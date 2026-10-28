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

    private final Map<UUID, Active> active = new ConcurrentHashMap<>();
    private final Map<UUID, Pending> pending = new ConcurrentHashMap<>();
    /** A token per running restoration, so a new meal's ends the old one's. */
    private final Map<UUID, Object> restoring = new ConcurrentHashMap<>();
    private final Map<UUID, Set<String>> cooked = new ConcurrentHashMap<>();
    private final Set<NamespacedKey> recipeKeys = ConcurrentHashMap.newKeySet();
    private volatile Set<Material> mealBases = Set.of();
    private volatile String registered = "";
    private final LongAdder furnaceItems = new LongAdder(), campfireItems = new LongAdder(), craftedItems = new LongAdder(), mealsMade = new LongAdder(), mealsEaten = new LongAdder(),
            refused = new LongAdder(), restored = new LongAdder(), qualityCarried = new LongAdder();

    public Cooking(Plugin plugin, GatheringService gathering, SkillManager skills, AttributeService attributes, BiConsumer<Player, String> notice,
                   Consumer<Player> refresh) {
        this.plugin = plugin;
        this.gathering = gathering;
        this.skills = skills;
        this.attributes = attributes;
        this.notice = notice;
        this.refresh = refresh;
    }

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
     * A crop's quality carried through a furnace, smoker or campfire: a Fine potato bakes into a Fine baked potato. (A
     * furnace's smelt event is a cook event too: one handler sees both.)
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onCookQuality(BlockCookEvent e) {
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

    /** A campfire takes 30 s; a record this old belongs to food that never finished (the fire went out, it was broken). */
    static final long CAMPFIRE_RECORD_MS = 120_000;
    private final Map<CampfireSlot, Placed> campfire = new ConcurrentHashMap<>();

    private static boolean isCampfire(Material m) { return m == Material.CAMPFIRE || m == Material.SOUL_CAMPFIRE; }

    /**
     * A player putting food on a campfire: the slots it fills are theirs. Vanilla places the food after this event, so the
     * slots are compared a tick later on the campfire's region thread. A dispenser places food with no player: no record.
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onCampfireUse(org.bukkit.event.player.PlayerInteractEvent e) {
        if (e.getAction() != org.bukkit.event.block.Action.RIGHT_CLICK_BLOCK || e.getClickedBlock() == null
                || !isCampfire(e.getClickedBlock().getType()) || e.useItemInHand() == org.bukkit.event.Event.Result.DENY) return;
        ItemStack hand = e.getItem();
        if (hand == null || !rules().enabled() || creative(e.getPlayer())) return;
        org.bukkit.block.Block b = e.getClickedBlock();
        if (!(b.getState() instanceof org.bukkit.block.Campfire before)) return;
        boolean[] empty = new boolean[before.getSize()];
        for (int i = 0; i < empty.length; i++) empty[i] = before.getItem(i) == null || before.getItem(i).getType().isAir();
        UUID player = e.getPlayer().getUniqueId();
        Bukkit.getRegionScheduler().run(plugin, b.getLocation(), t -> {
            if (!(b.getState() instanceof org.bukkit.block.Campfire after)) return;
            long now = System.currentTimeMillis();
            for (int i = 0; i < empty.length && i < after.getSize(); i++) {
                ItemStack it = after.getItem(i);
                if (empty[i] && it != null && !it.getType().isAir()) {
                    campfire.put(new CampfireSlot(b.getWorld().getUID(), b.getX(), b.getY(), b.getZ(), i), new Placed(player, now));
                }
            }
            if (campfire.size() > 512) campfire.values().removeIf(p -> now - p.at() > CAMPFIRE_RECORD_MS);
        });
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
        restore(p, m, q);
    }

    /** Heal {@code m}'s share over its seconds, a second at a time: paused while PvP-tagged, ended by a new meal or death. */
    private void restore(Player p, CookingRules.Meal m, Quality q) {
        UUID id = p.getUniqueId();
        Object token = new Object();
        if (m.restore() == null) { restoring.remove(id); return; }
        restoring.put(id, token);
        double share = Math.min(1, m.restore().share() * rules().potency(q));
        int ticks = m.restore().seconds();
        int[] done = {0}, waited = {0};
        p.getScheduler().runAtFixedRate(plugin, task -> {
            if (restoring.get(id) != token || !p.isOnline() || p.isDead() || done[0] >= ticks || waited[0] > ticks * 3) {
                restoring.remove(id, token);
                task.cancel();
                return;
            }
            waited[0]++;
            if (pvpTagged.test(id)) return; // paused, not lost: the rest comes after the fight
            heal.accept(p, maxHealth.applyAsDouble(p) * share / ticks);
            done[0]++;
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

    /** End {@code p}'s meal now (admin). Player's thread. */
    public void clear(Player p) { end(p, false); }

    /** End {@code p}'s meal. {@code wornOff}: it ran its time (said on the HUD). Player's thread. */
    private void end(Player p, boolean wornOff) {
        UUID id = p.getUniqueId();
        Active a = active.remove(id);
        restoring.remove(id);
        p.getPersistentDataContainer().remove(MEAL);
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
            p.getScheduler().runDelayed(plugin, t -> { refresh.accept(p); scheduleEnd(p, run); }, null, 1L);
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
                + ", quality carried through a furnace " + qualityCarried.sum() + ", running now " + active.size();
    }
}
