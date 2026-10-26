package Gathering;

import Skills.SkillManager;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Statistic;
import org.bukkit.block.Furnace;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.event.inventory.SmithItemEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerStatisticIncrementEvent;
import org.bukkit.inventory.CraftingInventory;
import org.bukkit.inventory.FurnaceInventory;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.Recipe;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.inventory.ShapelessRecipe;
import org.bukkit.inventory.SmithingTransformRecipe;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.LongAdder;
import java.util.function.Supplier;

/**
 * Smithing XP and forged quality (docs/design/GATHERING.md, "Smithing and crafting"). What pays is
 * always what was really made, counted by the game's own "items crafted" statistic, never the click:
 * a click that makes nothing pays nothing, and a shift-click or Ctrl+Q batch pays every item.
 *
 * <ul>
 *   <li><b>Smelting</b> pays as metal is taken out of a furnace, blast furnace or smoker block by a player
 *       (the statistic rises while that window is open; a number key or F onto an empty slot is
 *       counted from the click, the one take the statistic misses). Hoppers pay nobody. Each ingot
 *       taken also covers one unit of forging of that metal.</li>
 *   <li><b>Forging</b> (crafting gear) and the <b>netherite upgrade</b>: the click arms the payment
 *       for its own window, and the statistic pays it. A shift-click at the smithing table moves the
 *       result first and then counts the emptied stack: the statistic is raised for no item by 0, and
 *       only when the result really moved. That take pays the armed upgrade once.</li>
 *   <li><b>Metal a farm can make</b> ({@code forging.needs-smelted}: iron, gold, copper) pays and
 *       rolls a quality only as far as the player smelted it: an iron golem farm makes ingots without
 *       mining. The units are kept on the player (no database).</li>
 *   <li><b>Quality</b> is rolled once per click by the Smithing level, so a shift-click's whole batch
 *       shares it and re-crafting can't fish for one.</li>
 * </ul>
 * Creative mode earns nothing. Everything runs on the player's region thread.
 */
public final class ForgeListener implements Listener {

    /** How long a click's payment stays armed: the click is handled within the tick. */
    private static final long WINDOW_NS = 100_000_000L;
    /** The "pays only from smelted metal" hint, at most this often per player. */
    private static final long HINT_MS = 5 * 60_000L;

    /**
     * An armed payment: items of {@code material} made in {@code view} now pay {@code xpEach}; a batch's items get
     * {@code batchQuality}. {@code emptyTake}: a shift-click at the smithing table, paid by its empty take.
     */
    private record Pending(Material material, String skill, double xpEach, Quality batchQuality, InventoryView view, long until,
                           boolean emptyTake) {
        boolean live(long now) { return now - until < 0; }
    }

    private final Plugin plugin;
    private final GatheringService service;
    private final SkillManager skills;
    private final Map<UUID, Pending> pending = new ConcurrentHashMap<>();
    private final Map<UUID, Long> lastHint = new ConcurrentHashMap<>();
    private final LongAdder smelted = new LongAdder(), forged = new LongAdder(), upgraded = new LongAdder();
    /** Players with /cerb combatdebug on get a line per forging step. */
    private java.util.function.Predicate<UUID> debug = id -> false;
    /** HUD messages (player, text). */
    private java.util.function.BiConsumer<Player, String> notice = (p, s) -> {};

    public ForgeListener(Plugin plugin, GatheringService service, SkillManager skills) {
        this.plugin = plugin;
        this.service = service;
        this.skills = skills;
    }

    public void setDebug(java.util.function.Predicate<UUID> debug) { this.debug = debug; }

    public void setNotice(java.util.function.BiConsumer<Player, String> notice) { this.notice = notice; }

    /** The line is only built for a player with the debug on (it can be costly: a recipe lookup, a credit read). */
    private void trace(Player p, Supplier<String> line) {
        if (debug.test(p.getUniqueId())) p.sendMessage("§8[Forge] " + line.get());
    }

    private static boolean creative(Player p) {
        return p.getGameMode() == GameMode.CREATIVE || p.getGameMode() == GameMode.SPECTATOR;
    }

    // ── Smelted metal: units of forging covered, per player and metal ──

    private static final Map<String, NamespacedKey> CREDIT_KEYS = new ConcurrentHashMap<>();

    static NamespacedKey creditKey(String metal) {
        return CREDIT_KEYS.computeIfAbsent(metal, m -> new NamespacedKey("cerberus", "smelted_" + m));
    }

    /** Units of {@code metal} the player smelted and hasn't forged yet. Player's thread. */
    public static int credit(Player p, String metal) {
        Integer v = p.getPersistentDataContainer().get(creditKey(metal), PersistentDataType.INTEGER);
        return v == null ? 0 : Math.max(0, v);
    }

    /** Add (or, negative, spend) units of {@code metal}; never below 0, never past the int range. Player's thread. */
    public static void addCredit(Player p, String metal, long units) {
        PersistentDataContainer pdc = p.getPersistentDataContainer();
        long next = Math.max(0, Math.min(Integer.MAX_VALUE, (long) credit(p, metal) + units));
        if (next == 0) pdc.remove(creditKey(metal));
        else pdc.set(creditKey(metal), PersistentDataType.INTEGER, (int) next);
    }

    /** True when the player has the smelted metal forging {@code gear} once needs (always, for a metal no farm makes). */
    private boolean covered(Player p, GatheringCatalog c, Material gear) {
        if (!c.needsSmelted(gear)) return true;
        return credit(p, GatheringCatalog.metalOf(gear.name())) >= GatheringCatalog.unitsOf(gear.name());
    }

    private void hint(Player p, Material gear) {
        long now = System.currentTimeMillis();
        Long last = lastHint.get(p.getUniqueId());
        if (last != null && now - last < HINT_MS) return;
        lastHint.put(p.getUniqueId(), now);
        notice.accept(p, "§7Smithing pays for " + GatheringCatalog.metalOf(gear.name()) + " you smelted yourself");
    }

    // ── Smelting ─────────────────────────────────────────────────

    /** A furnace, blast furnace or smoker block's window (a plugin's furnace-shaped screen is none of them). */
    private static boolean furnace(Inventory top) {
        InventoryType t = top.getType();
        return (t == InventoryType.FURNACE || t == InventoryType.BLAST_FURNACE || t == InventoryType.SMOKER)
                && top.getHolder(false) instanceof Furnace;
    }

    /** Pay for {@code count} of {@code item} taken out of a furnace, and cover that much forging. */
    private void paySmelt(Player p, Material item, int count) {
        GatheringCatalog c = service.catalog();
        double each = c.smeltXp(item);
        if (count <= 0 || creative(p)) return;
        String metal = GatheringCatalog.smeltedMetal(item.name());
        if (metal != null && c.needsSmelted().contains(metal)) addCredit(p, metal, count);
        if (each <= 0) return;
        smelted.add(count);
        trace(p, () -> "smelted " + count + " " + item + (metal != null ? ", " + metal + " for forging " + credit(p, metal) : ""));
        service.award(p, c.smeltingSkill(), each * count);
    }

    /**
     * A number key or F on a furnace's output moves the whole stack to that slot when it is empty, and
     * the statistic doesn't count it (vanilla's furnace slot has no swap count), so it is paid here.
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onFurnaceSwap(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player p) || !(e.getClickedInventory() instanceof FurnaceInventory f)) return;
        if (e.getSlotType() != InventoryType.SlotType.RESULT) return;
        if (e.getClick() != ClickType.NUMBER_KEY && e.getClick() != ClickType.SWAP_OFFHAND) return;
        if (!furnace(f)) return; // last: it looks the block up
        ItemStack out = e.getCurrentItem();
        if (out == null || out.getType().isAir()) return;
        ItemStack target = e.getClick() == ClickType.NUMBER_KEY ? p.getInventory().getItem(e.getHotbarButton()) : p.getInventory().getItemInOffHand();
        if (target != null && !target.getType().isAir()) return; // an occupied slot: the swap does nothing
        paySmelt(p, out.getType(), out.getAmount());
    }

    // ── Forging ──────────────────────────────────────────────────

    /** Recipes that make gear without forging it (an upgrade: docs/design/WOODCUTTING.md §8): no XP, no new quality. */
    private volatile java.util.function.Predicate<Recipe> notForging = r -> false;

    public void setNotForging(java.util.function.Predicate<Recipe> notForging) { this.notForging = notForging; }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onCraft(CraftItemEvent e) {
        if (!(e.getWhoClicked() instanceof Player p)) return;
        pending.remove(p.getUniqueId()); // any other craft disarms an earlier click's payment, in creative too
        if (creative(p)) return;
        Recipe recipe = e.getRecipe();
        // Gear recipes only: the repair recipe (two worn tools into one) and other special recipes pay nothing.
        if (!(recipe instanceof ShapedRecipe || recipe instanceof ShapelessRecipe)) return;
        if (notForging.test(recipe)) return; // an upgrade keeps the item's own quality and pays no forging
        ItemStack result = e.getCurrentItem();
        if (result == null || result.getType().isAir()) return;
        Material m = result.getType();
        GatheringCatalog c = service.catalog();
        double each = c.forgeXp(m);
        if (each <= 0 || GatheringCatalog.gear(m.name()) == null) return;
        if (!covered(p, c, m)) { // made from metal the player didn't smelt: a plain item, no XP
            hint(p, m);
            trace(p, () -> "forging " + m + ": not enough smelted " + GatheringCatalog.metalOf(m.name()));
            return;
        }

        Quality q = roll(p, c.quality());
        ItemStack tagged = result.clone();
        GearItems.setQuality(tagged, q);
        e.setCurrentItem(tagged); // the item this click takes
        boolean batch = e.getClick().isShiftClick() || e.getClick() == ClickType.CONTROL_DROP;
        Pending pd = new Pending(m, c.forgingSkill(), each, batch ? q : null, e.getView(), System.nanoTime() + WINDOW_NS, false);
        pending.put(p.getUniqueId(), pd);
        CraftingInventory grid = e.getInventory();
        p.getScheduler().runDelayed(plugin, t -> settle(p, pd, grid), null, 1L);
    }

    /** Smithing's quality odds at the player's level. */
    private Quality roll(Player p, GatheringCatalog.QualityRules rules) {
        int level = skills.getSkillLevel(p, rules.skill());
        return Quality.roll(Quality.odds(level, rules.oddsAt1(), rules.oddsAt100()), ThreadLocalRandom.current().nextDouble());
    }

    /** In a batch, each next result gets the batch's quality as the grid makes it, while the player's smelted metal lasts. */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onPrepare(PrepareItemCraftEvent e) {
        // Every change to any crafting grid comes here: the armed batch is checked first, because the recipe
        // lookup builds a whole recipe object each call (it was this listener's main cost under load).
        HumanEntity who = e.getView().getPlayer();
        Pending pd = pending.get(who.getUniqueId());
        if (pd == null || pd.batchQuality() == null || pd.view() != e.getView() || !pd.live(System.nanoTime())) return;
        Recipe recipe = e.getRecipe();
        if (e.isRepair() || !(recipe instanceof ShapedRecipe || recipe instanceof ShapelessRecipe) || notForging.test(recipe)) return;
        ItemStack result = e.getInventory().getResult();
        if (result == null || result.getType() != pd.material()) return;
        if (!(who instanceof Player p) || !covered(p, service.catalog(), pd.material())) return;
        ItemStack tagged = result.clone();
        GearItems.setQuality(tagged, pd.batchQuality());
        e.getInventory().setResult(tagged);
    }

    /** After the click: the payment is disarmed, and a result left in the grid shows no quality (its own take rolls one). */
    private void settle(Player p, Pending pd, CraftingInventory grid) {
        pending.remove(p.getUniqueId(), pd);
        ItemStack left = grid.getResult();
        if (left != null && GearItems.quality(left) != null) {
            ItemStack plain = left.clone();
            GearItems.clearQuality(plain);
            grid.setResult(plain);
        }
    }

    // ── The netherite upgrade ────────────────────────────────────

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onSmith(SmithItemEvent e) {
        if (!(e.getWhoClicked() instanceof Player p)) return;
        pending.remove(p.getUniqueId()); // as in onCraft: disarm first, in creative too
        if (creative(p)) return;
        trace(p, () -> "smithing click (" + e.getClick() + "): recipe " + (e.getInventory().getRecipe() == null ? "none"
                : e.getInventory().getRecipe().getClass().getSimpleName()) + ", result " + (e.getCurrentItem() == null ? "none" : e.getCurrentItem().getType()));
        if (!(e.getInventory().getRecipe() instanceof SmithingTransformRecipe)) return; // trims pay nothing
        ItemStack result = e.getCurrentItem();
        if (result == null || !result.getType().name().startsWith("NETHERITE_") || GatheringCatalog.gear(result.getType().name()) == null) return;
        GatheringCatalog c = service.catalog();
        if (c.netheriteUpgradeXp() <= 0) return;
        Material m = result.getType();
        // A shift-click is paid by its empty take (onCrafted), never by what turns up in the inventory: a shift-click
        // that fails for want of room makes nothing, and an item swapped in from the cursor is not one made.
        Pending pd = new Pending(m, c.forgingSkill(), c.netheriteUpgradeXp(), null, e.getView(), System.nanoTime() + WINDOW_NS,
                e.getClick().isShiftClick());
        pending.put(p.getUniqueId(), pd);
        p.getScheduler().runDelayed(plugin, t -> pending.remove(p.getUniqueId(), pd), null, 1L);
    }

    private void payUpgrade(Player p, Material m, int made) {
        upgraded.add(made);
        trace(p, () -> "upgraded " + made + " " + m);
        service.award(p, service.catalog().forgingSkill(), service.catalog().netheriteUpgradeXp() * made);
    }

    // ── Payment: once per item really made ──────────────────────

    @EventHandler(priority = EventPriority.MONITOR)
    public void onCrafted(PlayerStatisticIncrementEvent e) {
        if (e.getStatistic() != Statistic.CRAFT_ITEM) return;
        Player p = e.getPlayer();
        int made = e.getNewValue() - e.getPreviousValue();
        if (e.getMaterial() == null || e.getMaterial().isAir()) { // no item: an emptied stack's take (see emptyTake)
            if (made == 0) emptyTake(p);
            return;
        }
        if (made <= 0) return;
        if (furnace(p.getOpenInventory().getTopInventory())) { // taken out of a furnace: smelting
            paySmelt(p, e.getMaterial(), made);
            return;
        }
        Pending pd = pending.get(p.getUniqueId());
        trace(p, () -> "made " + made + " " + e.getMaterial() + (pd == null ? ", nothing armed" : ", armed for " + pd.material()
                + (pd.live(System.nanoTime()) ? "" : " (expired)")));
        if (pd == null || pd.emptyTake() || e.getMaterial() != pd.material() || !pd.live(System.nanoTime()) || p.getOpenInventory() != pd.view()) return;
        if (pd.material().name().startsWith("NETHERITE_")) { payUpgrade(p, pd.material(), made); return; }
        GatheringCatalog c = service.catalog();
        int paid = made;
        if (c.needsSmelted(pd.material())) { // spend the smelted metal; only what it covers pays
            String metal = GatheringCatalog.metalOf(pd.material().name());
            int units = Math.max(1, GatheringCatalog.unitsOf(pd.material().name()));
            paid = Math.min(made, credit(p, metal) / units);
            addCredit(p, metal, -(long) paid * units);
        }
        if (paid <= 0) return;
        forged.add(paid);
        service.award(p, pd.skill(), pd.xpEach() * paid);
    }

    /**
     * A shift-click at the smithing table moved its result into the inventory: vanilla then counts the emptied
     * stack, which raises the statistic for no item by 0 (and only after a real move). Pays the armed upgrade once:
     * a netherite result never stacks and the base slot holds one item, so one click makes one.
     *
     * <p>Furnace, stonecutter and cartography shift-clicks raise the same signal in their own windows; the armed
     * window rules them out. No time limit here (unlike the other payments): the signal comes in the same call as
     * the click that armed it, and the next tick or the next click disarms it, so a slow listener or a GC pause in
     * between can't lose a real upgrade.
     */
    private void emptyTake(Player p) {
        Pending pd = pending.get(p.getUniqueId());
        if (pd == null || !pd.emptyTake() || p.getOpenInventory() != pd.view()) return;
        if (!pending.remove(p.getUniqueId(), pd)) return;
        payUpgrade(p, pd.material(), 1);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent e) {
        pending.remove(e.getPlayer().getUniqueId());
        lastHint.remove(e.getPlayer().getUniqueId());
    }

    // Diagnostics
    public long smelted() { return smelted.sum(); }
    public long forged() { return forged.sum(); }
    public long upgraded() { return upgraded.sum(); }
}
