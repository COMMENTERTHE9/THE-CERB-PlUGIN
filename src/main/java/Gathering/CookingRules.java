package Gathering;

import Attributes.Attribute;
import Attributes.Stat;
import Attributes.Yield;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Cooking's rules (docs/design/FARMING_COOKING.md §4), parsed from {@code resources.yml cooking:}: what food pays when a
 * player takes it out of a furnace or smoker or crafts it, and the meals: their recipes, the level that opens each, and
 * their effects at each tier. A meal's tier is the cook's level (Common, Fine, Superior, Masterwork: {@link Quality}),
 * one higher when at least half of its crop ingredients have a quality. Pure: no server calls.
 */
public final class CookingRules {

    /** One part of a meal's effect: an attribute, Fortune on a yield, or a speed stat; {@code amount} at the Common tier. */
    public record Effect(Attribute attribute, Stat stat, Yield yield, double amount) {
        public String describe(double potency) {
            double v = amount * potency;
            if (stat != null) return stat.displayName + " +" + trim(v * 100) + "%";
            String name = attribute.displayName + (yield != null ? " (" + yield.id + ")" : "");
            return name + " +" + trim(v);
        }
    }

    /** Health a meal restores: {@code share} of max health over {@code seconds}, from when it is eaten. */
    public record Restore(double share, int seconds) {}

    /**
     * One meal. {@code item}: the edible vanilla item it is made on (its food points and eating time stay vanilla).
     * {@code recipe}: one entry per ingredient, each the materials that may fill it (shapeless).
     */
    public record Meal(String id, String name, Material item, int level, double xp, String role, List<List<Material>> recipe,
                       int minutes, List<Effect> effects, Restore restore) {}

    /** Groups a recipe may name with '#'. */
    static final Map<String, List<Material>> GROUPS = Map.of(
            "cooked_meat", List.of(Material.COOKED_BEEF, Material.COOKED_PORKCHOP, Material.COOKED_MUTTON, Material.COOKED_CHICKEN, Material.COOKED_RABBIT),
            "cooked_fish", List.of(Material.COOKED_COD, Material.COOKED_SALMON));

    private final String skill;
    private final Map<Material, Double> furnace, crafting;
    /** The levels the Fine, Superior and Masterwork tiers start at. */
    private final int[] tierAt;
    private final double[] potency, duration;
    private final Map<String, Meal> meals;

    public static final CookingRules NONE = new CookingRules(null, Map.of(), Map.of(), new int[] {25, 50, 75},
            new double[] {1, 1.2, 1.4, 1.6}, new double[] {1, 1.25, 1.5, 2}, Map.of());

    CookingRules(String skill, Map<Material, Double> furnace, Map<Material, Double> crafting, int[] tierAt, double[] potency,
                 double[] duration, Map<String, Meal> meals) {
        this.skill = skill;
        this.furnace = furnace;
        this.crafting = crafting;
        this.tierAt = tierAt;
        this.potency = potency;
        this.duration = duration;
        this.meals = meals;
    }

    public boolean enabled() { return skill != null; }

    public String skill() { return skill; }

    /** XP each for food of {@code m} taken out of a furnace or smoker (0: none). */
    public double furnaceXp(Material m) { return m == null ? 0 : furnace.getOrDefault(m, 0.0); }

    /** XP each for food of {@code m} crafted by a vanilla recipe (0: not food, or a reversible recipe's). */
    public double craftXp(Material m) { return m == null ? 0 : crafting.getOrDefault(m, 0.0); }

    public Map<Material, Double> furnace() { return furnace; }

    public Map<Material, Double> crafting() { return crafting; }

    public Collection<Meal> meals() { return meals.values(); }

    public Meal meal(String id) { return id == null ? null : meals.get(id); }

    /** The tier a cook at {@code level} makes meals at, before ingredients. */
    public Quality tierAt(int level) {
        Quality q = Quality.COMMON;
        for (int i = 0; i < tierAt.length; i++) if (level >= tierAt[i]) q = Quality.values()[i + 1];
        return q;
    }

    /** The level {@code q} starts at (0 for Common). */
    public int levelFor(Quality q) {
        return q == Quality.COMMON ? 0 : tierAt[q.ordinal() - 1];
    }

    /**
     * A meal's tier: the cook's, one higher (Masterwork at most) when at least half of its {@code crops} crop ingredients
     * ({@code fine} of them Fine or better) have a quality.
     */
    public Quality tier(int level, int crops, int fine) {
        Quality q = tierAt(level);
        if (crops > 0 && fine * 2 >= crops && q != Quality.MASTERWORK) q = Quality.values()[q.ordinal() + 1];
        return q;
    }

    /** Every number of a meal's effect at tier {@code q} is multiplied by this. */
    public double potency(Quality q) { return potency[q.ordinal()]; }

    /** How long a meal at tier {@code q} lasts, in milliseconds. */
    public long durationMs(Meal m, Quality q) {
        return Math.round(m.minutes() * 60_000L * duration[q.ordinal()]);
    }

    public double durationFactor(Quality q) { return duration[q.ordinal()]; }

    /** The materials that can carry a crop quality into a meal: quality crops' produce and what a furnace makes of them. */
    public static boolean qualityIngredient(Material m, FarmingRules farming) {
        if (m == Material.BAKED_POTATO) return true;
        for (FarmingRules.Crop c : farming.crops()) if (c.quality() && c.produce() == m) return true;
        return false;
    }

    // ── loading ──────────────────────────────────────────────────

    static CookingRules from(ConfigurationSection s, java.util.function.UnaryOperator<String> skills, List<String> problems) {
        if (s == null) return NONE;
        String written = s.getString("skill", "Cooking");
        String skill = skills.apply(written.trim());
        if (skill == null) { problems.add("cooking: unknown skill " + written + " - cooking pays nothing"); return NONE; }
        Map<Material, Double> furnace = xpTable(s.getConfigurationSection("furnace"), "cooking furnace", problems);
        Map<Material, Double> crafting = xpTable(s.getConfigurationSection("crafting"), "cooking crafting", problems);

        int[] tierAt = {s.getInt("tiers.fine", 25), s.getInt("tiers.superior", 50), s.getInt("tiers.masterwork", 75)};
        if (!(tierAt[0] >= 1 && tierAt[0] <= tierAt[1] && tierAt[1] <= tierAt[2] && tierAt[2] <= 1000)) {
            problems.add("cooking tiers: " + tierAt[0] + "/" + tierAt[1] + "/" + tierAt[2] + " must rise (1-1000) - using 25/50/75");
            tierAt = new int[] {25, 50, 75};
        }
        double[] potency = factors(s.getConfigurationSection("potency"), new double[] {1, 1.2, 1.4, 1.6}, 0.1, 5, "cooking potency", problems);
        double[] duration = factors(s.getConfigurationSection("duration"), new double[] {1, 1.25, 1.5, 2}, 0.1, 10, "cooking duration", problems);

        Map<String, Meal> meals = new LinkedHashMap<>();
        ConfigurationSection ms = s.getConfigurationSection("meals");
        if (ms != null) for (String id : ms.getKeys(false)) {
            Object raw = ms.get(id);
            if (Boolean.FALSE.equals(raw) || "off".equalsIgnoreCase(String.valueOf(raw))) continue;
            ConfigurationSection m = ms.getConfigurationSection(id);
            if (m == null) { problems.add("cooking meal " + id + ": not a section"); continue; }
            List<String> why = new ArrayList<>();
            if (!FarmingRules.ID.matcher(id).matches()) why.add("id must be plain (a-z, 0-9, _)");
            Material item = GatheringCatalog.material(m.getString("item"));
            if (item == null) why.add("unknown item " + m.getString("item"));
            int level = m.getInt("level", 1);
            if (level < 1 || level > 1000) why.add("level " + level + " outside 1-1000");
            double xp = m.getDouble("xp", 0);
            if (!(xp >= 0 && xp <= 100_000)) why.add("xp " + xp + " outside 0-100000");
            List<List<Material>> recipe = new ArrayList<>();
            for (String r : m.getStringList("recipe")) {
                String k = r.trim();
                if (k.startsWith("#")) {
                    List<Material> g = GROUPS.get(k.substring(1).toLowerCase(Locale.ROOT));
                    if (g == null) why.add("unknown group " + k + " (" + String.join(", ", GROUPS.keySet().stream().map(x -> "#" + x).toList()) + ")");
                    else recipe.add(g);
                } else {
                    Material mm = GatheringCatalog.material(k);
                    if (mm == null) why.add("unknown ingredient " + k);
                    else recipe.add(List.of(mm));
                }
            }
            if (recipe.isEmpty() || recipe.size() > 9) why.add("a recipe takes 1-9 ingredients (has " + recipe.size() + ")");
            int minutes = m.getInt("minutes", 10);
            if (minutes < 1 || minutes > 240) why.add("minutes " + minutes + " outside 1-240");
            List<Effect> effects = effects(m.getMapList("effects"), why);
            Restore restore = null;
            if (m.isConfigurationSection("restore")) {
                double share = m.getDouble("restore.share", 0);
                int seconds = m.getInt("restore.seconds", 0);
                if (!(share > 0 && share <= 1) || seconds < 1 || seconds > 600) why.add("restore " + share + " over " + seconds + " s (share (0, 1], 1-600 s)");
                else restore = new Restore(share, seconds);
            }
            if (effects.isEmpty() && restore == null) why.add("no effects");
            if (!why.isEmpty()) { problems.add("cooking meal " + id + ": " + String.join("; ", why)); continue; }
            meals.put(id, new Meal(id, m.getString("name", FarmingRules.label(id)), item, level, xp, m.getString("role", ""),
                    Collections.unmodifiableList(recipe), minutes, List.copyOf(effects), restore));
        }
        return new CookingRules(skill, Collections.unmodifiableMap(furnace), Collections.unmodifiableMap(crafting), tierAt, potency, duration,
                Collections.unmodifiableMap(meals));
    }

    /** A meal's or a brew's effects: { attribute, amount } (fortune may add a yield) or { stat, amount }; what's wrong goes to {@code why}. */
    static List<Effect> effects(List<Map<?, ?>> written, List<String> why) {
        List<Effect> effects = new ArrayList<>();
        for (Map<?, ?> e : written) {
            Object a = e.get("attribute"), st = e.get("stat"), am = e.get("amount"), y = e.get("yield");
            double amount = am instanceof Number n ? n.doubleValue() : Double.NaN;
            if (a != null) {
                Attribute at = Attribute.parse(String.valueOf(a));
                Yield yy = y == null ? null : Yield.parse(String.valueOf(y));
                if (at == null) why.add("unknown attribute " + a);
                else if (y != null && (yy == null || at != Attribute.FORTUNE)) why.add("yield " + y + " (only fortune takes one: ore, wood, fish, crops, herbs)");
                else if (!(amount > 0 && amount <= 100)) why.add(a + " amount " + am + " outside (0, 100]");
                else effects.add(new Effect(at, null, yy, amount));
            } else if (st != null) {
                Stat stat = Stat.parse(String.valueOf(st));
                if (stat == null || !stat.isSecondary()) why.add("stat " + st + " is not a speed stat");
                else if (!(amount > 0 && amount <= 0.5)) why.add(st + " amount " + am + " outside (0, 0.5]");
                else effects.add(new Effect(null, stat, null, amount));
            } else why.add("an effect needs an attribute or a stat: " + e);
        }
        return effects;
    }

    private static Map<Material, Double> xpTable(ConfigurationSection s, String where, List<String> problems) {
        Map<Material, Double> out = new EnumMap<>(Material.class);
        if (s != null) for (String k : s.getKeys(false)) {
            Material m = GatheringCatalog.material(k);
            double v = s.getDouble(k, -1);
            if (m == null || !(v >= 0 && v <= 10_000)) problems.add(where + " " + k + ": unknown item or xp outside 0-10000");
            else out.put(m, v);
        }
        return out;
    }

    static double[] factors(ConfigurationSection s, double[] dflt, double min, double max, String where, List<String> problems) {
        double[] out = dflt.clone();
        if (s == null) return out;
        for (Quality q : Quality.values()) {
            double v = s.getDouble(q.id(), dflt[q.ordinal()]);
            if (!(v >= min && v <= max)) problems.add(where + "." + q.id() + ": " + v + " outside " + min + "-" + max);
            else out[q.ordinal()] = v;
        }
        for (int i = 1; i < out.length; i++) if (out[i] < out[i - 1]) {
            problems.add(where + ": each tier must be at least the one before - using the defaults");
            return dflt.clone();
        }
        return out;
    }

    /** A number for players: at most two decimals, none when whole. */
    public static String trim(double v) {
        double r = Math.round(v * 100) / 100.0;
        return r == Math.rint(r) ? String.valueOf((long) r) : String.valueOf(r);
    }

    /** Every material a meal's recipe can use (for the crafting grid's quick check). */
    public Set<Material> ingredients() {
        Set<Material> out = java.util.EnumSet.noneOf(Material.class);
        for (Meal m : meals.values()) for (List<Material> slot : m.recipe()) out.addAll(slot);
        return out;
    }
}
