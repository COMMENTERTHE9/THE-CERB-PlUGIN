package Gathering;

import Attributes.AttributeRules;
import Attributes.AttributeService;
import Attributes.AttributeSource;
import Attributes.Contribution;
import Skills.SkillManager;
import io.papermc.paper.datacomponent.DataComponentTypes;
import io.papermc.paper.datacomponent.item.FoodProperties;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Keyed;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.Statistic;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockCookEvent;
import org.bukkit.event.block.CrafterCraftEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.inventory.FurnaceSmeltEvent;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerStatisticIncrementEvent;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.Recipe;
import org.bukkit.inventory.RecipeChoice;
import org.bukkit.inventory.ShapelessRecipe;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.LongAdder;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * Cooking (docs/design/FARMING_COOKING.md §4): Cooking XP for food really made (taken out of a furnace or smoker, crafted
 * at a grid; the game's crafted-items statistic counts it, never the click), crop quality carried through a furnace, and
 * meals: locked recipes opened by level, a tier fixed when made, and one timed effect at a time through the attribute
 * engine (this class is that source). A meal's restoration heals the Cerberus health pool, never vanilla health. The
 * running meal is saved on the player with its real end time; death ends it. Everything runs on the player's thread.
 */
public final class Cooking implements Listener, AttributeSource {

    /** A meal item: "id|tier" (tier as {@link Quality#id()}). */
    public static final NamespacedKey MEAL_ITEM = new NamespacedKey("cerberus", "meal_item");
    /** The running meal on the player: "1|id|tier|endMillis". */
    public static final NamespacedKey MEAL = new NamespacedKey("cerberus", "meal");
    /** The meals a player has cooked (their ids). */
    public static final NamespacedKey COOKED = new NamespacedKey("cerberus", "meals_cooked");
    /** The end time of the meal whose one-minute heads-up was shown: a relog doesn't say it again. */
    public static final NamespacedKey MEAL_WARNED = new NamespacedKey("cerberus", "meal_warned");
    static final String SOURCE_PREFIX = "meal:";
    /** How long a click's payment stays armed: the click and its batch are handled within the tick. */
    private static final long WINDOW_NS = 100_000_000L;

    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacySection();

    /** The meal running on a player: until {@code end} (epoch milliseconds). */
    public record Active(String id, Quality tier, long end) {
        String encode() { return "1|" + id + "|" + tier.id() + "|" + end; }

        static Active decode(String s) {
            if (s == null) return null;
            String[] p = s.split("\\|");
            if (p.length < 4 || !p[0].equals("1")) return null;
            Quality q = Quality.parse(p[2]);
            try { return q == null ? null : new Active(p[1], q, Long.parseLong(p[3])); } catch (NumberFormatException e) { return null; }
        }
    }

    /**
     * A meal's restoration while it runs: {@code heals} heals a second apart, {@code done} so far, {@code paused} while
     * PvP holds it. Read by the menus (docs/design/ACTIVE_EFFECTS.md); written only by its own task.
     */
    private static final class Restoration {
        final String meal;
        final int heals;
        volatile int done;
        volatile boolean paused;

        Restoration(String meal, int heals) {
            this.meal = meal;
            this.heals = heals;
        }
    }

    /** An armed payment: items of {@code material} made in {@code view} now pay {@code xpEach}; {@code meal} when it is one. */
    private record Pending(Material material, double xpEach, String meal, InventoryView view, long until) {
        boolean live(long now) { return now - until < 0; }
    }

    private final Plugin plugin;
    private final GatheringService gathering;
    private final SkillManager skills;
    private final AttributeService attributes;
    private final BiConsumer<Player, String> notice;
    /** Max health, defense and speed stats pushed again after the engine's snapshot changed (AttributeEffects.refresh). */
    private final Consumer<Player> refresh;
    /** Heals the Cerberus health pool: player, amount; and reads max health. */
    private volatile BiConsumer<Player, Double> heal = (p, a) -> {};
    private volatile java.util.function.ToDoubleFunction<Player> maxHealth = p -> 0;
    private volatile java.util.function.Predicate<UUID> pvpTagged = id -> false;

    /** Shows the one-minute heads-up on the HUD: {@code force} once it has waited long enough for a free moment. */
    @FunctionalInterface
    public interface HeadsUp {
        /** True when shown. {@code force}: show it now, behind any message on the bar. */
        boolean show(Player p, String text, boolean force);
    }

    private volatile HeadsUp headsUp = (p, text, force) -> true;

    /** About a minute before a meal ends, the HUD says so once (docs/design/ACTIVE_EFFECTS.md §4). */
    static final long WARN_BEFORE_MS = 60_000L;
    /** A meal eaten with less than this past the warning time gets no heads-up (it is said when eaten). */
    static final long WARN_MIN_AFTER_EATING_MS = 5_000L;
    /** After a join with under a minute left: a heads-up a moment later, if at least this much remains. */
    static final long WARN_MIN_LEFT_MS = 15_000L, WARN_AFTER_JOIN_MS = 2_000L;
    /** While the HUD shows a message or a level-up, the heads-up tries again this often, this many times, then waits behind it. */
    private static final long WARN_RETRY_TICKS = 40L;
    private static final int WARN_TRIES = 8;

    private final Map<UUID, Active> active = new ConcurrentHashMap<>();
    private final Map<UUID, Pending> pending = new ConcurrentHashMap<>();
    /** The running restoration per player (its own token: a new meal's replaces the old one's). */
    private final Map<UUID, Restoration> restoring = new ConcurrentHashMap<>();
    private final Map<UUID, Set<String>> cooked = new ConcurrentHashMap<>();
    private final Set<NamespacedKey> recipeKeys = ConcurrentHashMap.newKeySet();
    private volatile Set<Material> mealBases = Set.of();
    private volatile String registered = "";
    private final LongAdder furnaceItems = new LongAdder(), campfireItems = new LongAdder(), craftedItems = new LongAdder(), mealsMade = new LongAdder(), mealsEaten = new LongAdder(),
            refused = new LongAdder(), restored = new LongAdder(), qualityCarried = new LongAdder(), warnings = new LongAdder();

    public Cooking(Plugin plugin, GatheringService gathering, SkillManager skills, AttributeService attributes, BiConsumer<Player, String> notice,
                   Consumer<Player> refresh) {
        this.plugin = plugin;
        this.gathering = gathering;
        this.skills = skills;
        this.attributes = attributes;
        this.notice = notice;
        this.refresh = refresh;
    }

    /** How the one-minute heads-up reaches the HUD (Manager.PlayerHUDManager.noticeIfIdle / notice). */
    public void setHeadsUp(HeadsUp headsUp) { this.headsUp = headsUp; }

    public void setHealth(BiConsumer<Player, Double> heal, java.util.function.ToDoubleFunction<Player> maxHealth) {
        this.heal = heal;
        this.maxHealth = maxHealth;
    }

    public void setPvpTagged(java.util.function.Predicate<UUID> tagged) { this.pvpTagged = tagged; }

    private CookingRules rules() { return gathering.catalog().cooking(); }

    private static boolean creative(Player p) {
        return p.getGameMode() == GameMode.CREATIVE || p.getGameMode() == GameMode.SPECTATOR;
    }

    // ── Cooking XP: food out of a furnace or smoker (ForgeListener's statistic path calls this) ──

    /** {@code count} of {@code item} taken out of a furnace, smoker or blast furnace by {@code p}. Player's thread. */
    public void taken(Player p, Material item, int count) {
        CookingRules r = rules();
        double each = r.furnaceXp(item);
        if (!r.enabled() || count <= 0 || !(each > 0)) return;
        if (creative(p)) { gathering.withheld(p, r.skill(), Manager.XpIntegrity.whyNot(p)); return; }
        furnaceItems.add(count);
        gathering.award(p, r.skill(), each * count);
    }

    /**
     * A crop's quality carried through a campfire: a Fine potato roasts into a Fine baked potato. A campfire drops what it
     * cooks. A furnace or smoker makes plain food: its output slot takes only items like the recipe's plain result, so a
     * quality result would stop it until emptied, or burn fuel for nothing beside plain food. (A furnace's smelt event is a
     * cook event too, hence the block check.)
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onCookQuality(BlockCookEvent e) {
        if (e instanceof FurnaceSmeltEvent || !isCampfire(e.getBlock().getType())) return;
        Quality q = GearItems.cropQuality(e.getSource());
        if (q == null) return;
        ItemStack out = e.getResult();
        if (out == null || out.getType().isAir() || !out.getType().isEdible()) return;
        ItemStack tagged = out.clone();
        GearItems.setCropQuality(tagged, q);
        e.setResult(tagged);
        qualityCarried.increment();
    }

    // ── Cooking XP: food a player put on a campfire, paid when the campfire finishes it ──

    /** One slot of one campfire. */
    private record CampfireSlot(UUID world, int x, int y, int z, int slot) {}

    /** Who put the food in a campfire slot, and when (a record older than {@link #CAMPFIRE_RECORD_MS} is stale). */
    private record Placed(UUID player, long at) {}

    /**
     * A campfire takes 30 s, longer unlit; a slot's record is replaced by the next food put in that slot, so this only
     * bounds memory for campfires that are gone.
     */
    static final long CAMPFIRE_RECORD_MS = 30 * 60_000L;
    private final Map<CampfireSlot, Placed> campfire = new ConcurrentHashMap<>();

    private static boolean isCampfire(Material m) { return m == Material.CAMPFIRE || m == Material.SOUL_CAMPFIRE; }

    /** A player using a campfire with an item in hand, just now: the food a campfire takes in that same call is theirs. */
    private final Map<CampfireSlot, Placed> using = new ConcurrentHashMap<>();

    /**
     * A player putting food on a campfire. Vanilla places it right after this event, in the same call, and the campfire's
     * start event says when it does; this only notes who is using which campfire (creative players aren't noted: their food
     * pays nothing).
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onCampfireUse(org.bukkit.event.player.PlayerInteractEvent e) {
        if (e.getAction() != org.bukkit.event.block.Action.RIGHT_CLICK_BLOCK || e.getClickedBlock() == null
                || !isCampfire(e.getClickedBlock().getType()) || e.useItemInHand() == org.bukkit.event.Event.Result.DENY) return;
        if (e.getItem() == null || !rules().enabled() || creative(e.getPlayer())) return;
        org.bukkit.block.Block b = e.getClickedBlock();
        using.put(new CampfireSlot(b.getWorld().getUID(), b.getX(), b.getY(), b.getZ(), -1), new Placed(e.getPlayer().getUniqueId(), System.nanoTime()));
    }

    /**
     * Food goes into a campfire: the first empty slot, as vanilla fills them. The player who used the campfire in this same
     * call owns that slot's food; food put there any other way (another plugin) owns nobody, and replaces whatever record
     * the slot had (food that left without cooking: a campfire broken or put out).
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onCampfireStart(org.bukkit.event.block.CampfireStartEvent e) {
        org.bukkit.block.Block b = e.getBlock();
        if (!(b.getState() instanceof org.bukkit.block.Campfire state)) return;
        int slot = -1;
        for (int i = 0; i < state.getSize(); i++) {
            ItemStack it = state.getItem(i);
            if (it == null || it.getType().isAir()) { slot = i; break; }
        }
        if (slot < 0) return;
        UUID w = b.getWorld().getUID();
        Placed who = using.remove(new CampfireSlot(w, b.getX(), b.getY(), b.getZ(), -1));
        CampfireSlot key = new CampfireSlot(w, b.getX(), b.getY(), b.getZ(), slot);
        if (who != null && System.nanoTime() - who.at() < 50_000_000L) campfire.put(key, new Placed(who.player(), System.currentTimeMillis()));
        else campfire.remove(key);
        if (campfire.size() > 512) {
            long now = System.currentTimeMillis();
            campfire.values().removeIf(p -> now - p.at() > CAMPFIRE_RECORD_MS);
        }
        if (using.size() > 64) using.clear(); // notes are used in the same call; any left over are stale
    }

    /** A campfire finished a slot's food: the player who put it there earns its Cooking XP (on their own thread). */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onCampfireCooked(BlockCookEvent e) {
        if (e instanceof FurnaceSmeltEvent || !isCampfire(e.getBlock().getType())) return;
        CookingRules r = rules();
        double each = r.furnaceXp(e.getResult() == null ? null : e.getResult().getType());
        if (!r.enabled() || !(each > 0) || campfire.isEmpty()) return;
        org.bukkit.block.Block b = e.getBlock();
        if (!(b.getState() instanceof org.bukkit.block.Campfire state)) return;
        // The slot finishing now: the first one still holding its food whose time is up (an earlier one that finished in
        // this tick is already empty).
        int slot = -1;
        for (int i = 0; i < state.getSize(); i++) {
            ItemStack it = state.getItem(i);
            if (it != null && it.getType() == e.getSource().getType() && state.getCookTime(i) >= state.getCookTimeTotal(i)) { slot = i; break; }
        }
        if (slot < 0) return;
        Placed who = campfire.remove(new CampfireSlot(b.getWorld().getUID(), b.getX(), b.getY(), b.getZ(), slot));
        if (who == null || System.currentTimeMillis() - who.at() > CAMPFIRE_RECORD_MS) return;
        Player p = Bukkit.getPlayer(who.player());
        if (p == null) return; // left meanwhile: nobody to pay
        String skill = r.skill();
        p.getScheduler().run(plugin, t -> {
            if (creative(p)) { gathering.withheld(p, skill, Manager.XpIntegrity.whyNot(p)); return; }
            campfireItems.increment();
            gathering.award(p, skill, each);
        }, null);
    }

    // ── meal recipes ─────────────────────────────────────────────

    /** Register the meals' recipes (load, /cerb reload). Global region thread. */
    public void registerRecipes(GatheringCatalog c) {
        CookingRules r = c.cooking();
        StringBuilder sig = new StringBuilder();
        for (CookingRules.Meal m : r.meals()) sig.append(m.id()).append(m.item()).append(m.recipe()).append(';');
        if (sig.toString().equals(registered)) return;
        for (NamespacedKey k : recipeKeys) Bukkit.removeRecipe(k);
        recipeKeys.clear();
        Set<Material> bases = EnumSet.noneOf(Material.class);
        for (CookingRules.Meal m : r.meals()) {
            if (!m.item().isItem() || !m.item().isEdible()) {
                plugin.getLogger().warning("[Cooking] resources.yml cooking.meals." + m.id() + ": " + m.item() + " is not food - meal left out");
                continue;
            }
            if (m.recipe().stream().flatMap(List::stream).anyMatch(x -> !x.isItem())) {
                plugin.getLogger().warning("[Cooking] resources.yml cooking.meals." + m.id() + ": an ingredient is not an item - meal left out");
                continue;
            }
            NamespacedKey key = new NamespacedKey(plugin, "meal_" + m.id());
            ShapelessRecipe sr = new ShapelessRecipe(key, new ItemStack(m.item()));
            for (List<Material> any : m.recipe()) sr.addIngredient(new RecipeChoice.MaterialChoice(any));
            if (Bukkit.addRecipe(sr)) { recipeKeys.add(key); bases.add(m.item()); }
        }
        mealBases = bases;
        registered = sig.toString();
    }

    /** The meal a recipe is, when it is one of ours. */
    private CookingRules.Meal mealOf(Recipe recipe) {
        if (!(recipe instanceof Keyed k) || !recipeKeys.contains(k.getKey())) return null;
        return rules().meal(k.getKey().getKey().substring("meal_".length()));
    }

    /** True when {@code p} may cook {@code m}: Cooking at its level. */
    public boolean mayCook(Player p, CookingRules.Meal m) {
        CookingRules r = rules();
        return r.enabled() && m != null && skills.getSkillLevel(p, r.skill()) >= m.level();
    }

    /** The meal recipes {@code p} has opened go into their recipe book. Player's thread. */
    public void discoverRecipes(Player p) {
        List<NamespacedKey> keys = new ArrayList<>();
        for (CookingRules.Meal m : rules().meals()) {
            NamespacedKey key = new NamespacedKey(plugin, "meal_" + m.id());
            if (recipeKeys.contains(key) && mayCook(p, m)) keys.add(key);
        }
        if (!keys.isEmpty()) p.discoverRecipes(keys);
    }

    /** A Cooking level-up: the meals it opens (a chat line each, and the recipe book). Player's thread. */
    public void leveledUp(Player p, String skill, int from, int to) {
        CookingRules r = rules();
        if (!r.enabled() || !r.skill().equals(skill)) return;
        for (CookingRules.Meal m : r.meals()) {
            if (m.level() > from && m.level() <= to) {
                p.sendMessage("§6✦ New recipe: §f" + m.name() + " §7(" + m.role() + ") · Player Menu → Skills → Cooking → Cookbook");
            }
        }
        if (r.tierAt(to) != r.tierAt(from)) {
            Quality q = r.tierAt(to);
            p.sendMessage("§6✦ " + q.color + q.displayName + " §7meals from now on: effects ×" + CookingRules.trim(r.potency(q))
                    + ", lasting ×" + CookingRules.trim(r.durationFactor(q)));
        }
        discoverRecipes(p);
    }

    /**
     * The grid's result for a meal recipe: nothing before its level, else the meal at the tier this cook and these
     * ingredients make. Every grid change in every crafting grid lands here; getRecipe() builds a whole recipe
     * (docs/PERFORMANCE.md), so it is asked only when the result vanilla worked out is a meal's base item.
     */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPrepare(PrepareItemCraftEvent e) {
        ItemStack shown = e.getInventory().getResult();
        if (shown == null || !mealBases.contains(shown.getType())) return;
        CookingRules.Meal m = mealOf(e.getRecipe());
        if (m == null) return;
        if (!(e.getView().getPlayer() instanceof Player p) || !mayCook(p, m)) { e.getInventory().setResult(null); return; }
        e.getInventory().setResult(makeMeal(m, tierFor(p, e.getInventory().getMatrix())));
    }

    /** The tier {@code p} cooks a meal at from these ingredients. */
    private Quality tierFor(Player p, ItemStack[] grid) {
        CookingRules r = rules();
        FarmingRules f = gathering.catalog().farming();
        int crops = 0, fine = 0;
        for (ItemStack it : grid) {
            if (it == null || it.getType().isAir() || !CookingRules.qualityIngredient(it.getType(), f)) continue;
            crops++;
            Quality q = GearItems.cropQuality(it);
            if (q != null && q.ordinal() >= Quality.FINE.ordinal()) fine++;
        }
        return r.tier(skills.getSkillLevel(p, r.skill()), crops, fine);
    }

    /** One meal item at tier {@code q}: its name, what it does, and the id and tier in its data (always edible). */
    public ItemStack makeMeal(CookingRules.Meal m, Quality q) {
        CookingRules r = rules();
        ItemStack it = new ItemStack(m.item());
        FoodProperties food = it.getData(DataComponentTypes.FOOD);
        if (food != null) it.setData(DataComponentTypes.FOOD, FoodProperties.food().nutrition(food.nutrition()).saturation(food.saturation()).canAlwaysEat(true));
        it.editMeta(meta -> {
            meta.displayName(text("§6" + m.name()));
            List<Component> lore = new ArrayList<>();
            lore.add(text(q.color + "✦ " + q.displayName + " §7· " + m.role()));
            for (String line : effectLines(m, q, r)) lore.add(text("§f" + line));
            lore.add(text("§7Lasts " + duration(r.durationMs(m, q))));
            lore.add(text("§8One meal at a time; a new one replaces it."));
            meta.lore(lore);
            meta.getPersistentDataContainer().set(MEAL_ITEM, PersistentDataType.STRING, m.id() + "|" + q.id());
        });
        return it;
    }

    /** What a meal does at tier {@code q}, one line per part. */
    public static List<String> effectLines(CookingRules.Meal m, Quality q, CookingRules r) {
        List<String> out = new ArrayList<>();
        double pot = r.potency(q);
        for (CookingRules.Effect e : m.effects()) out.add(e.describe(pot));
        if (m.restore() != null) out.add("Restores " + CookingRules.trim(m.restore().share() * pot * 100) + "% health over " + m.restore().seconds() + " s");
        return out;
    }

    static String duration(long ms) {
        long s = Math.round(ms / 1000.0), min = s / 60, sec = s % 60;
        return min > 0 ? min + " min" + (sec > 0 ? " " + sec + " s" : "") : sec + " s";
    }

    private static Component text(String legacy) {
        return LEGACY.deserialize(legacy).decoration(TextDecoration.ITALIC, false);
    }

    /** A meal item's id and tier, or null when the item is none. */
    public static String[] mealItem(ItemStack it) {
        if (it == null || it.getType().isAir() || !it.hasItemMeta()) return null;
        String s = it.getPersistentDataContainer().get(MEAL_ITEM, PersistentDataType.STRING);
        if (s == null) return null;
        String[] p = s.split("\\|");
        return p.length == 2 ? p : null;
    }

    /** Taking a meal's result: the grid must still make exactly it, for a cook who has its level. */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onCraftCheck(CraftItemEvent e) {
        ItemStack shown = e.getCurrentItem();
        if (shown == null || !mealBases.contains(shown.getType())) return; // a recipe is built only for a meal's base item
        CookingRules.Meal m = mealOf(e.getRecipe());
        if (m == null) return;
        String[] made = mealItem(shown);
        if (!(e.getWhoClicked() instanceof Player p) || !mayCook(p, m) || made == null || !made[0].equals(m.id())) {
            e.setCancelled(true);
            refused.increment();
        }
    }

    /** A crafter has no cook: it never makes a meal (its recipe's plain base item would be a free conversion). */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onCrafter(CrafterCraftEvent e) {
        if (mealOf(e.getRecipe()) != null) e.setCancelled(true);
    }

    // ── Cooking XP: crafted food, paid once per item really made ──

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onCraft(CraftItemEvent e) {
        if (!(e.getWhoClicked() instanceof Player p)) return;
        pending.remove(p.getUniqueId()); // any other craft disarms an earlier click's payment
        CookingRules r = rules();
        if (!r.enabled()) return;
        ItemStack result = e.getCurrentItem();
        if (result == null || result.getType().isAir()) return;
        double vanilla = r.craftXp(result.getType());
        if (!(vanilla > 0) && !mealBases.contains(result.getType())) return; // not food: no recipe is built
        Recipe recipe = e.getRecipe();
        CookingRules.Meal m = mealOf(recipe);
        double each = m != null ? m.xp() : vanilla;
        if (!(each > 0)) return;
        if (m == null && !(recipe instanceof Keyed k && k.getKey().getNamespace().equals(NamespacedKey.MINECRAFT))) return; // another plugin's recipe
        if (creative(p)) { gathering.withheld(p, r.skill(), Manager.XpIntegrity.whyNot(p)); return; }
        pending.put(p.getUniqueId(), new Pending(result.getType(), each, m == null ? null : m.id(), e.getView(), System.nanoTime() + WINDOW_NS));
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onCrafted(PlayerStatisticIncrementEvent e) {
        if (e.getStatistic() != Statistic.CRAFT_ITEM || e.getMaterial() == null) return;
        Player p = e.getPlayer();
        Pending pd = pending.get(p.getUniqueId());
        if (pd == null || e.getMaterial() != pd.material() || !pd.live(System.nanoTime()) || p.getOpenInventory() != pd.view()) return;
        int made = e.getNewValue() - e.getPreviousValue();
        if (made <= 0) return;
        CookingRules r = rules();
        if (pd.meal() != null) {
            mealsMade.add(made);
            Set<String> have = cooked(p);
            if (have.add(pd.meal())) p.getPersistentDataContainer().set(COOKED, PersistentDataType.STRING, String.join(",", new LinkedHashSet<>(have)));
        } else craftedItems.add(made);
        gathering.award(p, r.skill(), pd.xpEach() * made);
    }

    /** The meals {@code p} has cooked (ids), read once and kept while online. Player's thread. */
    public Set<String> cooked(Player p) {
        return cooked.computeIfAbsent(p.getUniqueId(), id -> {
            String saved = p.getPersistentDataContainer().get(COOKED, PersistentDataType.STRING);
            Set<String> out = ConcurrentHashMap.newKeySet();
            if (saved != null) for (String s : saved.split(",")) if (!s.isBlank()) out.add(s.trim());
            return out;
        });
    }

    // ── eating ───────────────────────────────────────────────────

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEat(PlayerItemConsumeEvent e) {
        String[] meal = mealItem(e.getItem());
        if (meal == null) return;
        CookingRules r = rules();
        CookingRules.Meal m = r.meal(meal[0]);
        Quality q = Quality.parse(meal[1]);
        Player p = e.getPlayer();
        if (!r.enabled() || m == null || q == null) {
            p.sendMessage("§7This meal is no longer on this server's menu: plain food.");
            return;
        }
        UUID id = p.getUniqueId();
        Active before = active.get(id);
        long now = System.currentTimeMillis();
        Active next = new Active(m.id(), q, now + r.durationMs(m, q));
        active.put(id, next);
        p.getPersistentDataContainer().set(MEAL, PersistentDataType.STRING, next.encode());
        mealsEaten.increment();
        attributes.markDirty(id);
        refresh.accept(p);
        String what = q.color + q.displayName + " §6" + m.name(), change;
        if (before != null && before.end() > now && !before.id().equals(m.id())) {
            CookingRules.Meal old = r.meal(before.id());
            change = "replaces " + (old == null ? "your last meal" : old.name());
        } else if (before != null && before.end() > now) {
            change = "time refreshed: " + duration(next.end() - now);
        } else {
            change = duration(next.end() - now);
        }
        notice.accept(p, what + " §7· " + change);
        p.sendMessage("§6✦ " + m.name() + " §7(" + q.color + q.displayName + "§7): " + String.join(", ", effectLines(m, q, r)) + " §8· "
                + duration(next.end() - now) + (change.startsWith("replaces") ? " §7(" + change + ")" : ""));
        p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_BURP, 0.6f, 1.2f);
        scheduleEnd(p, next);
        scheduleWarning(p, next, true);
        restore(p, m, q);
    }

    /** A fight can hold a heal over seconds this long in all (ten minutes); after that the heals still due are dropped. */
    static final int MAX_RESTORE_PAUSE_SECONDS = 600;

    enum RestoreStep { HEAL, WAIT, STOP }

    /**
     * What a heal over seconds does this second (a meal's restoration, Alchemy's draught): {@code done} of {@code heals}
     * so far, {@code paused} seconds already waited. While PvP-tagged it waits, paused, not lost: the rest comes after the
     * fight, however long the tag lasts, up to {@link #MAX_RESTORE_PAUSE_SECONDS} in all, so the task always ends. Pure.
     */
    static RestoreStep restoreStep(int done, int heals, int paused, boolean tagged) {
        if (done >= heals || paused >= MAX_RESTORE_PAUSE_SECONDS) return RestoreStep.STOP;
        return tagged ? RestoreStep.WAIT : RestoreStep.HEAL;
    }

    /** Heal {@code m}'s share over its seconds, a second at a time: paused while PvP-tagged, ended by a new meal or death. */
    private void restore(Player p, CookingRules.Meal m, Quality q) {
        UUID id = p.getUniqueId();
        if (m.restore() == null) { restoring.remove(id); return; }
        double share = Math.min(1, m.restore().share() * rules().potency(q));
        int ticks = m.restore().seconds();
        Restoration token = new Restoration(m.id(), ticks);
        restoring.put(id, token);
        int[] paused = {0};
        p.getScheduler().runAtFixedRate(plugin, task -> {
            RestoreStep step = restoring.get(id) != token || !p.isOnline() || p.isDead() ? RestoreStep.STOP
                    : restoreStep(token.done, ticks, paused[0], pvpTagged.test(id));
            if (step == RestoreStep.STOP) {
                restoring.remove(id, token);
                task.cancel();
                return;
            }
            if (step == RestoreStep.WAIT) { token.paused = true; paused[0]++; return; } // paused, not lost: the rest comes after the fight
            token.paused = false;
            heal.accept(p, maxHealth.applyAsDouble(p) * share / ticks);
            token.done++;
            restored.increment();
        }, null, 20L, 20L);
    }

    /** When the meal ends: its effect leaves the engine (at most a second late). Player's thread. */
    private void scheduleEnd(Player p, Active a) {
        long ticks = Math.max(1, (a.end() - System.currentTimeMillis() + 49) / 50);
        p.getScheduler().runDelayed(plugin, t -> {
            if (active.get(p.getUniqueId()) != a) return; // replaced, refreshed or ended meanwhile
            end(p, true);
        }, null, ticks);
    }

    /**
     * When to say {@code end} is near, in milliseconds from {@code now}, or -1 for no heads-up. {@code eatenNow}: set when
     * the meal is eaten (a meal with about a minute or less to run gets none: the eating message said its time); else set
     * at a join (under a minute left: a moment after the join, if at least {@link #WARN_MIN_LEFT_MS} remain). Pure.
     */
    static long warnDelayMs(long now, long end, boolean eatenNow) {
        long left = end - now;
        if (left > WARN_BEFORE_MS) {
            long delay = left - WARN_BEFORE_MS;
            return eatenNow && delay < WARN_MIN_AFTER_EATING_MS ? -1 : delay;
        }
        return !eatenNow && left > WARN_MIN_LEFT_MS ? WARN_AFTER_JOIN_MS : -1;
    }

    /** The one-minute heads-up for meal {@code a}: one delayed task; a replaced, refreshed or ended meal never warns. */
    private void scheduleWarning(Player p, Active a, boolean eatenNow) {
        var pdc = p.getPersistentDataContainer();
        if (eatenNow) pdc.remove(MEAL_WARNED);
        else if (Long.valueOf(a.end()).equals(pdc.get(MEAL_WARNED, PersistentDataType.LONG))) return; // said before the relog
        long delay = warnDelayMs(System.currentTimeMillis(), a.end(), eatenNow);
        if (delay < 0) return;
        p.getScheduler().runDelayed(plugin, t -> warn(p, a, WARN_TRIES), null, Math.max(1, delay / 50));
    }

    private void warn(Player p, Active a, int tries) {
        if (active.get(p.getUniqueId()) != a || !p.isOnline()) return; // replaced, refreshed or ended meanwhile
        long left = a.end() - System.currentTimeMillis();
        if (left <= 0) return;
        CookingRules.Meal m = rules().meal(a.id());
        String text = "§6" + (m == null ? "Your meal" : m.name()) + " §7wears off in " + duration(left);
        if (headsUp.show(p, text, tries <= 1)) {
            p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_CHIME, 0.45f, 1.4f);
            p.getPersistentDataContainer().set(MEAL_WARNED, PersistentDataType.LONG, a.end());
            warnings.increment();
            return;
        }
        // The bar is busy (a fight's messages, a level-up): try again shortly, at most WARN_TRIES times.
        p.getScheduler().runDelayed(plugin, t -> warn(p, a, tries - 1), null, WARN_RETRY_TICKS);
    }

    /** End {@code p}'s meal now (admin). Player's thread. */
    public void clear(Player p) { end(p, false); }

    /** End {@code p}'s meal. {@code wornOff}: it ran its time (said on the HUD). Player's thread. */
    private void end(Player p, boolean wornOff) {
        UUID id = p.getUniqueId();
        Active a = active.remove(id);
        restoring.remove(id);
        p.getPersistentDataContainer().remove(MEAL);
        p.getPersistentDataContainer().remove(MEAL_WARNED);
        if (a == null) return;
        attributes.markDirty(id);
        refresh.accept(p);
        if (wornOff) {
            CookingRules.Meal m = rules().meal(a.id());
            notice.accept(p, "§7" + (m == null ? "Your meal" : m.name()) + " wore off.");
        }
    }

    /** {@code p}'s running meal, or null. */
    public Active active(UUID player) {
        Active a = active.get(player);
        return a == null || a.end() <= System.currentTimeMillis() ? null : a;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onDeath(PlayerDeathEvent e) {
        if (active.containsKey(e.getPlayer().getUniqueId())) end(e.getPlayer(), false);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        Active a = Active.decode(p.getPersistentDataContainer().get(MEAL, PersistentDataType.STRING));
        if (a != null && (a.end() <= System.currentTimeMillis() || rules().meal(a.id()) == null)) {
            p.getPersistentDataContainer().remove(MEAL);
            a = null;
        }
        if (a != null) {
            active.put(p.getUniqueId(), a);
            attributes.markDirty(p.getUniqueId());
            Active run = a;
            p.getScheduler().runDelayed(plugin, t -> { refresh.accept(p); scheduleEnd(p, run); scheduleWarning(p, run, false); }, null, 1L);
        }
        discoverRecipes(p);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent e) {
        UUID id = e.getPlayer().getUniqueId();
        active.remove(id); // saved on the player with its end time; read again at the next join
        restoring.remove(id); // restoration doesn't survive a relog
        pending.remove(id);
        cooked.remove(id);
    }

    // ── the meal in the menus (docs/design/ACTIVE_EFFECTS.md) ────

    /**
     * {@code p}'s running meal as the menus show it: its timed effect until its end, and, apart, its restoration, which
     * heals only in the first seconds after eating. Read from the same state the engine uses. Player's thread.
     */
    public void effects(Player p, long now, List<Effects.ActiveEffect> out) {
        Active a = active.get(p.getUniqueId());
        if (a == null || a.end() <= now) return;
        CookingRules r = rules();
        CookingRules.Meal m = r.meal(a.id());
        if (m == null) return;
        Effects.ActiveEffect.Phase phase = null;
        if (m.restore() != null) {
            String what = CookingRules.trim(Math.min(1, m.restore().share() * r.potency(a.tier())) * 100) + "% of max health over "
                    + m.restore().seconds() + " s";
            Restoration rs = restoring.get(p.getUniqueId());
            if (rs != null && rs.meal.equals(a.id()) && rs.done < rs.heals) {
                phase = new Effects.ActiveEffect.Phase("Restoration", what + ", a heal a second", now + (rs.heals - rs.done) * 1000L, rs.paused);
            } else {
                phase = new Effects.ActiveEffect.Phase("Restoration", what + " after eating; not running now", 0L, false);
            }
        }
        out.add(new Effects.ActiveEffect("meal:" + a.id() + ":" + a.tier().id(), Effects.ActiveEffect.Category.MEAL, m.name(),
                "Meal · " + a.tier().displayName + " · " + m.role(), m.item(), a.end(), null, contributions(m, a.tier(), r), List.of(), phase));
    }

    // ── the meal in the attribute engine ─────────────────────────

    @Override public String id() { return "meal"; }

    @Override
    public List<Contribution> contributions(UUID player, AttributeRules ruleSet) {
        Active a = active(player);
        if (a == null) return List.of();
        CookingRules r = rules();
        CookingRules.Meal m = r.meal(a.id());
        return m == null ? List.of() : contributions(m, a.tier(), r);
    }

    /** Pure: what a meal at tier {@code q} adds, every number times the tier's potency. */
    public static List<Contribution> contributions(CookingRules.Meal m, Quality q, CookingRules r) {
        double pot = r.potency(q);
        String label = "Meal · " + m.name() + " (" + q.displayName + ")";
        List<Contribution> out = new ArrayList<>();
        for (CookingRules.Effect e : m.effects()) {
            double v = e.amount() * pot;
            if (e.stat() != null) out.add(Contribution.stat(SOURCE_PREFIX + m.id(), label, e.stat(), v, null));
            else if (e.yield() != null) out.add(Contribution.fortune(SOURCE_PREFIX + m.id(), label, v, e.yield()));
            else out.add(Contribution.attribute(SOURCE_PREFIX + m.id(), label, e.attribute(), v, null));
        }
        return out;
    }

    /** Diagnostics for /cerb cook stats. */
    public String stats() {
        return "furnace food " + furnaceItems.sum() + ", campfire food " + campfireItems.sum() + ", crafted food " + craftedItems.sum() + ", meals made " + mealsMade.sum()
                + " (refused " + refused.sum() + "), eaten " + mealsEaten.sum() + ", restoration ticks " + restored.sum()
                + ", quality carried through a furnace " + qualityCarried.sum() + ", running now " + active.size() + ", one-minute warnings " + warnings.sum();
    }
}
