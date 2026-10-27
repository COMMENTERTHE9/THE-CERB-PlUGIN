package Gathering;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Fishing equipment's rules (docs/design/FISHING_EQUIPMENT.md), read from resources.yml {@code tackle:}: the setup's
 * categories and the levels that open them, the parts (the rod upgrades of {@code upgrades:} and the special parts),
 * bait and the Bait Bag, the auto-hook reel, the crafting recipes for bait and the reel, and the collection rewards.
 * Pure: nothing here touches the world or a player.
 */
public final class TackleRules {

    /** A setup category: its name and the Fishing level that opens it. */
    public record Category(String id, String name, int unlock) {}

    /** What a special part does instead of adding a stat. */
    public enum Special { NONE, AUTO_REEL, DISCOVERY, SPECIMEN }

    /**
     * A part of the setup. An upgrade part adds its upgrade's stat ({@code upgrade}, the rod upgrade of the same id in
     * {@code upgrades:}); a special part does what {@link Special} says. {@code item} is what fitting it uses (an upgrade's
     * item, or the special part's own); null when it can't be fitted from an item (a reward). {@code value} is a special
     * part's strength (the discovery factor, the specimen strength).
     */
    public record Part(String id, String category, String upgrade, Special special, String item, String name, double value) {
        public boolean specialist() { return special == Special.DISCOVERY || special == Special.SPECIMEN; }
    }

    /** What a bait is for. */
    public enum Purpose { SPEED, ENCOUNTERS, NIGHT, DISCOVERY }

    /**
     * A bait: the item that carries it, its purpose, and either a stat it adds (lure speed, sea-creature chance) or a
     * weight factor (night species, undiscovered species). {@code night} limits it to the night; {@code unlockedBy} names
     * the collection reward that opens its recipe (null: open to everyone).
     */
    public record Bait(String id, String item, String name, Purpose purpose, Attributes.Stat stat, double amount, double factor,
                       boolean night, String unlockedBy) {}

    /** A crafting-table recipe (shapeless): each ingredient is a list of materials any of which will do. */
    public record Recipe(String id, String result, List<List<Material>> ingredients, int makes, String unlockedBy) {}

    /** A collection milestone: what it counts and what it unlocks ("part:<id>", "recipe:<id>", "title:<id>"). */
    public record Reward(String id, String name, Counts counts, int amount, List<String> unlocks) {}

    /** What a reward counts: waters with a discovery, species discovered (amount 0 = every species), personal bests set. */
    public enum Counts { WATERS, SPECIES, BESTS }

    public static final List<Category> DEFAULT_CATEGORIES = List.of(
            new Category("rod", "Rod", 1), new Category("line", "Line", 10), new Category("lure", "Lure", 25), new Category("reel", "Reel", 50));

    private final Map<String, Category> categories;
    private final Map<String, Part> parts;
    private final Map<String, String> partByUpgrade;
    private final Map<String, Bait> bait;
    private final Map<String, Recipe> recipes;
    private final Map<String, Reward> rewards;
    private final int[] bagUnlock; // the Fishing level each bag slot opens at
    private final int bagStack;
    private final int reelDelayTicks;
    private final double maxWeightFactor;

    TackleRules(Map<String, Category> categories, Map<String, Part> parts, Map<String, Bait> bait, Map<String, Recipe> recipes,
                Map<String, Reward> rewards, int[] bagUnlock, int bagStack, int reelDelayTicks, double maxWeightFactor) {
        this.categories = Collections.unmodifiableMap(new LinkedHashMap<>(categories));
        this.parts = Collections.unmodifiableMap(new LinkedHashMap<>(parts));
        Map<String, String> byUp = new LinkedHashMap<>();
        for (Part p : parts.values()) if (p.upgrade() != null) byUp.putIfAbsent(p.upgrade(), p.id());
        this.partByUpgrade = Collections.unmodifiableMap(byUp);
        this.bait = Collections.unmodifiableMap(new LinkedHashMap<>(bait));
        this.recipes = Collections.unmodifiableMap(new LinkedHashMap<>(recipes));
        this.rewards = Collections.unmodifiableMap(new LinkedHashMap<>(rewards));
        this.bagUnlock = bagUnlock.clone();
        this.bagStack = bagStack;
        this.reelDelayTicks = reelDelayTicks;
        this.maxWeightFactor = maxWeightFactor;
    }

    /** No {@code tackle:} section: no setup, no bait (rods work as before). */
    public static final TackleRules NONE = new TackleRules(Map.of(), Map.of(), Map.of(), Map.of(), Map.of(), new int[]{1, 20, 40}, 64, 8, 2.0);

    public boolean enabled() { return !categories.isEmpty(); }
    public Map<String, Category> categories() { return categories; }
    public Category category(String id) { return id == null ? null : categories.get(id); }
    public Map<String, Part> parts() { return parts; }
    public Part part(String id) { return id == null ? null : parts.get(id); }
    /** The part a rod upgrade became (its id), or null. */
    public String partOfUpgrade(String upgrade) { return upgrade == null ? null : partByUpgrade.get(upgrade); }
    public Map<String, Bait> bait() { return bait; }
    public Bait baitById(String id) { return id == null ? null : bait.get(id); }
    /** The bait an item id carries, or null. */
    public Bait baitOfItem(String item) {
        if (item == null) return null;
        for (Bait b : bait.values()) if (b.item().equals(item)) return b;
        return null;
    }
    public Map<String, Recipe> recipes() { return recipes; }
    public Map<String, Reward> rewards() { return rewards; }
    public int bagStack() { return bagStack; }
    public int reelDelayTicks() { return reelDelayTicks; }
    /** The most equipment and bait together multiply one species' weight by. */
    public double maxWeightFactor() { return maxWeightFactor; }

    /** How many Bait Bag slots a Fishing level opens (1 to 3). */
    public int bagSlots(int level) {
        int n = 0;
        for (int at : bagUnlock) if (level >= at) n++;
        return Math.max(1, Math.min(3, n));
    }

    /** The level the bag's slot {@code i} (0-2) opens at. */
    public int bagUnlock(int i) { return i >= 0 && i < bagUnlock.length ? bagUnlock[i] : Integer.MAX_VALUE; }

    /** True when {@code category} is open at {@code level} (an unknown category never is). */
    public boolean unlocked(String category, int level) {
        Category c = categories.get(category);
        return c != null && level >= c.unlock();
    }

    // ── the setup's effect (pure: the attribute engine does the rest) ──

    /**
     * The parts that count: those {@code installed} that are owned, configured here, and whose category is open at
     * {@code level}. Parts and ids unknown here are left out (kept owned, shown unavailable).
     */
    public Set<String> active(Set<String> installed, Set<String> owned, int level) {
        Set<String> out = new LinkedHashSet<>();
        for (String id : installed) {
            Part p = parts.get(id);
            if (p != null && owned.contains(id) && unlocked(p.category(), level)) out.add(id);
        }
        return out;
    }

    /**
     * The rod upgrades whose stats count with this setup and this rod in hand (docs/design/FISHING_EQUIPMENT.md §6): the
     * active parts' upgrades, plus the rod's own upgrades (at any level, as before), each once.
     */
    public static Set<String> countedUpgrades(Set<String> activeParts, Map<String, Part> parts, List<String> rodUpgrades) {
        Set<String> out = new LinkedHashSet<>();
        for (String id : activeParts) {
            Part p = parts.get(id);
            if (p != null && p.upgrade() != null) out.add(p.upgrade());
        }
        if (rodUpgrades != null) out.addAll(rodUpgrades);
        return out;
    }

    /** The specialist among the active parts (at most one counts: the first). */
    public Part specialist(Set<String> activeParts) {
        for (String id : activeParts) {
            Part p = parts.get(id);
            if (p != null && p.specialist()) return p;
        }
        return null;
    }

    /** True when the active parts hold the auto-hook reel. */
    public boolean hasAutoReel(Set<String> activeParts) {
        for (String id : activeParts) {
            Part p = parts.get(id);
            if (p != null && p.special() == Special.AUTO_REEL) return true;
        }
        return false;
    }

    /**
     * Which presets a newly owned part goes into (0-2): bite-speed, encounter and reel parts into all three; treasure parts
     * into Treasure and Sea Creatures (a treasure catch replaces a fish, so Discoveries leaves them out); Surveyor's Float
     * into Discoveries; the Specimen Hook into none (the player chooses). Unknown parts: none.
     */
    public List<Integer> defaultPresets(String partId, Map<String, GatheringCatalog.UpgradeDef> upgrades) {
        Part p = parts.get(partId);
        if (p == null) return List.of();
        return switch (p.special()) {
            case AUTO_REEL -> List.of(0, 1, 2);
            case DISCOVERY -> List.of(0);
            case SPECIMEN -> List.of();
            case NONE -> {
                GatheringCatalog.UpgradeDef u = upgrades.get(p.upgrade());
                yield u != null && u.stat() == Attributes.Stat.TREASURE_CHANCE ? List.of(1, 2) : List.of(0, 1, 2);
            }
        };
    }

    // ── weights (Surveyor's Float, discovery and night bait) ──────

    /**
     * How much a species' weight is multiplied by: the discovery factor (Surveyor's Float, a discovery bait) for one not
     * caught yet, the night factor (night bait) for one that bites only at night, all together at most
     * {@link #maxWeightFactor}. Never below 1: equipment favours, it never hides.
     */
    public double weightFactor(FishingRules.Species s, boolean caught, double discovery, double night) {
        double f = 1.0;
        if (!caught && discovery > 1) f *= discovery;
        if (night > 1 && s.when().time() == FishingRules.Time.NIGHT) f *= night;
        return Math.max(1.0, Math.min(maxWeightFactor, f));
    }

    /** The Specimen Hook's weight-roll exponent: vanilla's 2.5 flattened by {@code strength} (0.25 → 2.0). */
    public static double specimenExponent(double strength) {
        return 2.5 / (1.0 + Math.max(0, Math.min(4, strength)));
    }

    // ── reading resources.yml ─────────────────────────────────────

    public static TackleRules from(ConfigurationSection t, Map<String, GatheringCatalog.ItemDef> items,
                                   Map<String, GatheringCatalog.UpgradeDef> upgrades, List<String> problems) {
        if (t == null) return NONE;
        Map<String, Category> categories = new LinkedHashMap<>();
        ConfigurationSection cs = t.getConfigurationSection("categories");
        if (cs == null) for (Category c : DEFAULT_CATEGORIES) categories.put(c.id(), c);
        else for (String id : cs.getKeys(false)) {
            if (!FishingRules.ID.matcher(id).matches()) { problems.add("tackle.categories." + id + ": ids are a-z, 0-9 and _"); continue; }
            int unlock = cs.getInt(id + ".unlock", 1);
            if (unlock < 1 || unlock > 1000) { problems.add("tackle.categories." + id + ".unlock: 1-1000"); continue; }
            categories.put(id, new Category(id, cs.getString(id + ".name", id), unlock));
        }

        Map<String, Part> parts = new LinkedHashMap<>();
        ConfigurationSection ps = t.getConfigurationSection("parts");
        if (ps != null) for (String id : ps.getKeys(false)) {
            if (!FishingRules.ID.matcher(id).matches()) { problems.add("tackle.parts." + id + ": ids are a-z, 0-9 and _"); continue; }
            ConfigurationSection p = ps.getConfigurationSection(id);
            if (p == null) { problems.add("tackle.parts." + id + ": not a section"); continue; }
            String cat = p.getString("category", "");
            if (!categories.containsKey(cat)) { problems.add("tackle.parts." + id + ": unknown category " + cat); continue; }
            String up = p.getString("upgrade");
            Special special = special(p.getString("special"));
            if (special == null) { problems.add("tackle.parts." + id + ": unknown special " + p.getString("special") + " (auto-reel, discovery, specimen)"); continue; }
            if (up != null) {
                GatheringCatalog.UpgradeDef u = upgrades.get(up);
                if (u == null) { problems.add("tackle.parts." + id + ": unknown upgrade " + up + " (upgrades:)"); continue; }
                if (!u.on().contains("rod")) { problems.add("tackle.parts." + id + ": upgrade " + up + " is not a rod upgrade"); continue; }
                parts.put(id, new Part(id, cat, up, Special.NONE, u.item(), p.getString("name", u.name()), 0));
                continue;
            }
            if (special == Special.NONE) { problems.add("tackle.parts." + id + ": needs an upgrade or a special"); continue; }
            String item = p.getString("item");
            if (item != null && items.get(item) == null) { problems.add("tackle.parts." + id + ": unknown item " + item + " (items:)"); continue; }
            double value = p.getDouble(special == Special.DISCOVERY ? "factor" : "strength", special == Special.DISCOVERY ? 1.5 : 0.25);
            if (special == Special.DISCOVERY && !(value >= 1 && value <= 4)) { problems.add("tackle.parts." + id + ".factor: 1-4"); value = 1.5; }
            if (special == Special.SPECIMEN && !(value >= 0 && value <= 4)) { problems.add("tackle.parts." + id + ".strength: 0-4"); value = 0.25; }
            parts.put(id, new Part(id, cat, null, special, item, p.getString("name", id), value));
        }

        Map<String, Bait> bait = new LinkedHashMap<>();
        ConfigurationSection bs = t.getConfigurationSection("bait");
        if (bs != null) for (String id : bs.getKeys(false)) {
            ConfigurationSection b = bs.getConfigurationSection(id);
            if (b == null || !FishingRules.ID.matcher(id).matches()) { problems.add("tackle.bait." + id + ": not a section with a plain id"); continue; }
            String item = b.getString("item", id);
            if (items.get(item) == null) { problems.add("tackle.bait." + id + ": unknown item " + item + " (items:)"); continue; }
            Purpose purpose = purpose(b.getString("purpose"));
            if (purpose == null) { problems.add("tackle.bait." + id + ": purpose is speed, encounters, night or discovery"); continue; }
            Attributes.Stat stat = null;
            double amount = 0, factor = 1;
            if (purpose == Purpose.SPEED || purpose == Purpose.ENCOUNTERS) {
                stat = purpose == Purpose.SPEED ? Attributes.Stat.LURE_SPEED : Attributes.Stat.SEA_CREATURE_CHANCE;
                amount = b.getDouble("amount", 0);
                if (!(amount > 0 && amount <= 0.5)) { problems.add("tackle.bait." + id + ".amount: (0, 0.5]"); continue; }
            } else {
                factor = b.getDouble("factor", 1.5);
                if (!(factor > 1 && factor <= 4)) { problems.add("tackle.bait." + id + ".factor: (1, 4]"); continue; }
            }
            boolean night = purpose == Purpose.NIGHT || "night".equalsIgnoreCase(b.getString("time", ""));
            String unlocked = b.getString("unlocked-by");
            bait.put(id, new Bait(id, item, items.get(item).name(), purpose, stat, amount, factor, night,
                    unlocked == null || unlocked.isBlank() ? null : unlocked.trim()));
        }

        Map<String, Recipe> recipes = new LinkedHashMap<>();
        ConfigurationSection rs = t.getConfigurationSection("recipes");
        if (rs != null) for (String id : rs.getKeys(false)) {
            ConfigurationSection r = rs.getConfigurationSection(id);
            if (r == null || !FishingRules.ID.matcher(id).matches()) { problems.add("tackle.recipes." + id + ": not a section with a plain id"); continue; }
            String result = r.getString("result", id);
            if (items.get(result) == null) { problems.add("tackle.recipes." + id + ": unknown result item " + result + " (items:)"); continue; }
            List<List<Material>> ingredients = new ArrayList<>();
            boolean bad = false;
            for (Object o : r.getList("ingredients", List.of())) {
                List<Material> any = new ArrayList<>();
                for (Object m : o instanceof List<?> l ? l : List.of(o)) {
                    Material mat = GatheringCatalog.material(String.valueOf(m));
                    if (mat == null || mat.isLegacy() || mat == Material.AIR) { problems.add("tackle.recipes." + id + ": unknown material " + m); bad = true; }
                    else any.add(mat);
                }
                if (!any.isEmpty()) ingredients.add(List.copyOf(any));
            }
            if (bad) continue;
            if (ingredients.isEmpty() || ingredients.size() > 9) { problems.add("tackle.recipes." + id + ": 1-9 ingredients"); continue; }
            int makes = r.getInt("makes", 1);
            if (makes < 1 || makes > 64) { problems.add("tackle.recipes." + id + ".makes: 1-64"); continue; }
            String unlocked = r.getString("unlocked-by");
            recipes.put(id, new Recipe(id, result, List.copyOf(ingredients), makes, unlocked == null || unlocked.isBlank() ? null : unlocked.trim()));
        }

        Map<String, Reward> rewards = new LinkedHashMap<>();
        ConfigurationSection ws = t.getConfigurationSection("rewards");
        if (ws != null) for (String id : ws.getKeys(false)) {
            ConfigurationSection w = ws.getConfigurationSection(id);
            if (w == null || !FishingRules.ID.matcher(id).matches()) { problems.add("tackle.rewards." + id + ": not a section with a plain id"); continue; }
            Counts counts;
            int amount;
            if (w.contains("waters")) { counts = Counts.WATERS; amount = w.getInt("waters", 0); }
            else if (w.contains("bests")) { counts = Counts.BESTS; amount = w.getInt("bests", 0); }
            else if (w.contains("species")) {
                counts = Counts.SPECIES;
                amount = "all".equalsIgnoreCase(String.valueOf(w.get("species"))) ? 0 : w.getInt("species", -1);
            } else { problems.add("tackle.rewards." + id + ": counts waters, species or bests"); continue; }
            if (amount < 0 || (amount == 0 && counts != Counts.SPECIES)) { problems.add("tackle.rewards." + id + ": a positive number (species may be all)"); continue; }
            List<String> unlocks = new ArrayList<>();
            for (String u : w.getStringList("unlocks")) {
                String k = u.trim().toLowerCase(Locale.ROOT);
                if (k.startsWith("part:") && parts.containsKey(k.substring(5))) unlocks.add(k);
                else if (k.startsWith("recipe:") && recipes.containsKey(k.substring(7))) unlocks.add(k);
                else if (k.startsWith("title:") && FishingRules.ID.matcher(k.substring(6)).matches()) unlocks.add(k);
                else problems.add("tackle.rewards." + id + ": unknown unlock " + u + " (part:<id>, recipe:<id>, title:<id>)");
            }
            rewards.put(id, new Reward(id, w.getString("name", id), counts, amount, List.copyOf(unlocks)));
        }
        for (Bait b : bait.values()) if (b.unlockedBy() != null && !rewards.containsKey(b.unlockedBy())) {
            problems.add("tackle.bait." + b.id() + ".unlocked-by: unknown reward " + b.unlockedBy());
        }
        for (Recipe r : recipes.values()) if (r.unlockedBy() != null && !rewards.containsKey(r.unlockedBy())) {
            problems.add("tackle.recipes." + r.id() + ".unlocked-by: unknown reward " + r.unlockedBy());
        }

        int[] bag = {1, t.getInt("bait-bag.slot-2", 20), t.getInt("bait-bag.slot-3", 40)};
        if (!(bag[1] >= 1 && bag[1] <= bag[2] && bag[2] <= 1000)) { problems.add("tackle.bait-bag: slot-2 <= slot-3, 1-1000"); bag = new int[]{1, 20, 40}; }
        int stack = t.getInt("bait-bag.stack", 64);
        if (stack < 1 || stack > 1024) { problems.add("tackle.bait-bag.stack: 1-1024"); stack = 64; }
        int delay = t.getInt("reel.delay-ticks", 8);
        if (delay < 1 || delay > 15) { problems.add("tackle.reel.delay-ticks: 1-15 (a bite lasts at least 20 ticks)"); delay = 8; }
        double max = t.getDouble("max-weight-factor", 2.0);
        if (!(max >= 1 && max <= 8)) { problems.add("tackle.max-weight-factor: 1-8"); max = 2.0; }
        return new TackleRules(categories, parts, bait, recipes, rewards, bag, stack, delay, max);
    }

    private static Special special(String s) {
        if (s == null || s.isBlank()) return Special.NONE;
        return switch (s.trim().toLowerCase(Locale.ROOT).replace('_', '-')) {
            case "auto-reel" -> Special.AUTO_REEL;
            case "discovery" -> Special.DISCOVERY;
            case "specimen" -> Special.SPECIMEN;
            default -> null;
        };
    }

    private static Purpose purpose(String s) {
        if (s == null) return null;
        try { return Purpose.valueOf(s.trim().toUpperCase(Locale.ROOT)); } catch (IllegalArgumentException e) { return null; }
    }
}
