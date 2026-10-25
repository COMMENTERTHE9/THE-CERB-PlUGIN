package Gathering;

import Skills.SkillManager;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.Statistic;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.inventory.FurnaceExtractEvent;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.event.inventory.SmithItemEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerStatisticIncrementEvent;
import org.bukkit.inventory.CraftingInventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.Recipe;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.inventory.ShapelessRecipe;
import org.bukkit.inventory.SmithingTransformRecipe;
import org.bukkit.plugin.Plugin;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.LongAdder;

/**
 * Smithing XP and forged quality (docs/design/GATHERING.md, "Smithing and crafting").
 *
 * <ul>
 *   <li><b>Smelting:</b> paid when a player takes metal out of a furnace or blast furnace (hoppers
 *       pay nobody), for the count the game's "items crafted" statistic adds for that take: a furnace
 *       that smelted two kinds of input (raw iron and iron ore) fires its extract event once per kind
 *       for one take, so the event alone would pay twice.</li>
 *   <li><b>Forging and the netherite upgrade:</b> a click on the result slot only says a craft
 *       <i>may</i> happen (a full cursor or hotbar slot makes it do nothing; a shift-click or
 *       Ctrl+Q makes several). So the click arms the payment, and what pays is the game's own
 *       "items crafted" statistic as it goes up, once per item really made.</li>
 *   <li><b>Quality:</b> rolled once per click by the Smithing level, so a shift-click's whole batch
 *       shares it and re-crafting can't fish for one; written into each item as it is made.</li>
 * </ul>
 * Creative mode earns nothing. Everything runs on the player's region thread.
 */
public final class ForgeListener implements Listener {

    /** How long a click's payment stays armed: the click is handled within the tick. */
    private static final long WINDOW_NS = 100_000_000L;

    /** An armed payment: items of {@code material} made now pay {@code xpEach}; a batch's items get {@code batchQuality}. */
    private record Pending(Material material, String skill, double xpEach, Quality batchQuality, long until) {
        boolean live(long now) { return now - until < 0; }
    }

    private final Plugin plugin;
    private final GatheringService service;
    private final SkillManager skills;
    private final Map<UUID, Pending> pending = new ConcurrentHashMap<>();

    /** The last "items crafted" statistic rise per player: a furnace take raises it just before its extract event. */
    private record Made(Material material, int count, long at) {}

    private final Map<UUID, Made> lastMade = new ConcurrentHashMap<>();
    private final LongAdder smelted = new LongAdder(), forged = new LongAdder(), upgraded = new LongAdder();

    /** Players with /cerb combatdebug on get a line per forging step. */
    private java.util.function.Predicate<UUID> debug = id -> false;

    public ForgeListener(Plugin plugin, GatheringService service, SkillManager skills) {
        this.plugin = plugin;
        this.service = service;
        this.skills = skills;
    }

    public void setDebug(java.util.function.Predicate<UUID> debug) { this.debug = debug; }

    private void trace(Player p, String line) {
        if (debug.test(p.getUniqueId())) p.sendMessage("§8[Forge] " + line);
    }

    private static boolean creative(Player p) {
        return p.getGameMode() == GameMode.CREATIVE || p.getGameMode() == GameMode.SPECTATOR;
    }

    // ── Smelting ─────────────────────────────────────────────────

    @EventHandler(priority = EventPriority.MONITOR)
    public void onSmelt(FurnaceExtractEvent e) {
        Player p = e.getPlayer();
        Made made = lastMade.remove(p.getUniqueId()); // the take's own count; a second event for the same take finds none
        trace(p, "furnace take: " + e.getItemAmount() + " " + e.getItemType() + (made == null ? ", already paid or no count" : ", counted " + made.count()));
        if (made == null || made.material() != e.getItemType() || System.nanoTime() - made.at() > WINDOW_NS) return;
        GatheringCatalog c = service.catalog();
        double each = c.smeltXp(e.getItemType());
        if (each <= 0 || creative(p)) return;
        smelted.add(made.count());
        service.award(p, c.smeltingSkill(), each * made.count());
    }

    // ── Forging ──────────────────────────────────────────────────

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onCraft(CraftItemEvent e) {
        if (!(e.getWhoClicked() instanceof Player p) || creative(p)) return;
        Recipe recipe = e.getRecipe();
        // Gear recipes only: the repair recipe (two worn tools into one) and other special recipes pay nothing.
        if (!(recipe instanceof ShapedRecipe || recipe instanceof ShapelessRecipe)) return;
        ItemStack result = e.getCurrentItem();
        if (result == null || result.getType().isAir()) return;
        Material m = result.getType();
        GatheringCatalog c = service.catalog();
        double each = c.forgeXp(m);
        if (each <= 0 || GatheringCatalog.gear(m.name()) == null) return;

        Quality q = roll(p, c.quality());
        ItemStack tagged = result.clone();
        GearItems.setQuality(tagged, q);
        e.setCurrentItem(tagged); // the item this click takes
        boolean batch = e.getClick().isShiftClick() || e.getClick() == ClickType.CONTROL_DROP;
        Pending pd = new Pending(m, c.forgingSkill(), each, batch ? q : null, System.nanoTime() + WINDOW_NS);
        pending.put(p.getUniqueId(), pd);
        CraftingInventory grid = e.getInventory();
        p.getScheduler().runDelayed(plugin, t -> settle(p, pd, grid), null, 1L);
    }

    /** Smithing's quality odds at the player's level. */
    private Quality roll(Player p, GatheringCatalog.QualityRules rules) {
        int level = skills.getSkillLevel(p, rules.skill());
        return Quality.roll(Quality.odds(level, rules.oddsAt1(), rules.oddsAt100()), ThreadLocalRandom.current().nextDouble());
    }

    /** In a batch, each next result gets the batch's quality as the grid makes it. */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onPrepare(PrepareItemCraftEvent e) {
        HumanEntity who = e.getView().getPlayer();
        Pending pd = pending.get(who.getUniqueId());
        if (pd == null || pd.batchQuality() == null || !pd.live(System.nanoTime())) return;
        ItemStack result = e.getInventory().getResult();
        if (result == null || result.getType() != pd.material()) return;
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
        if (!(e.getWhoClicked() instanceof Player p) || creative(p)) return;
        trace(p, "smithing click: recipe " + (e.getInventory().getRecipe() == null ? "none" : e.getInventory().getRecipe().getClass().getSimpleName())
                + ", result " + (e.getCurrentItem() == null ? "none" : e.getCurrentItem().getType()));
        if (!(e.getInventory().getRecipe() instanceof SmithingTransformRecipe)) return; // trims pay nothing
        ItemStack result = e.getCurrentItem();
        if (result == null || !result.getType().name().startsWith("NETHERITE_") || GatheringCatalog.gear(result.getType().name()) == null) return;
        GatheringCatalog c = service.catalog();
        if (c.netheriteUpgradeXp() <= 0) return;
        Pending pd = new Pending(result.getType(), c.forgingSkill(), c.netheriteUpgradeXp(), null, System.nanoTime() + WINDOW_NS);
        pending.put(p.getUniqueId(), pd);
        p.getScheduler().runDelayed(plugin, t -> pending.remove(p.getUniqueId(), pd), null, 1L);
    }

    // ── Payment: once per item really made ──────────────────────

    @EventHandler(priority = EventPriority.MONITOR)
    public void onCrafted(PlayerStatisticIncrementEvent e) {
        if (e.getStatistic() != Statistic.CRAFT_ITEM) return;
        Player p = e.getPlayer();
        int made = e.getNewValue() - e.getPreviousValue();
        if (made <= 0) return;
        lastMade.put(p.getUniqueId(), new Made(e.getMaterial(), made, System.nanoTime()));
        Pending pd = pending.get(p.getUniqueId());
        trace(p, "made " + made + " " + e.getMaterial() + (pd == null ? ", nothing armed" : ", armed for " + pd.material()
                + (pd.live(System.nanoTime()) ? "" : " (expired)")));
        if (pd == null || e.getMaterial() != pd.material() || !pd.live(System.nanoTime())) return;
        if (pd.material().name().startsWith("NETHERITE_")) upgraded.add(made);
        else forged.add(made);
        service.award(p, pd.skill(), pd.xpEach() * made);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent e) {
        pending.remove(e.getPlayer().getUniqueId());
        lastMade.remove(e.getPlayer().getUniqueId());
    }

    // Diagnostics
    public long smelted() { return smelted.sum(); }
    public long forged() { return forged.sum(); }
    public long upgraded() { return upgraded.sum(); }
}
