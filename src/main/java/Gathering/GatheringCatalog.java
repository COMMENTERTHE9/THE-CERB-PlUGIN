package Gathering;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Everything gathering and forging read, from {@code resources.yml} (docs/design/GATHERING.md):
 * resources by block, custom tools and items, smelting and forging XP, quality odds and effects,
 * and the Fortune rule. Built once per load and immutable; lookups are enum maps. A bad entry is
 * refused on its own with a reason in {@link #problems()}, never the whole file.
 */
public final class GatheringCatalog {

    /** A resource: what gathers it, what it is worth, and (for a custom one) what it drops. */
    public record ResourceDef(String id, String skill, Set<Material> blocks, String tool, int tier, int level,
                              double xp, boolean fortune, List<Drop> drops) {
        /** True when its drops replace the block's vanilla drops (a custom resource). */
        public boolean replacesDrops() { return !drops.isEmpty(); }
    }

    /** A drop: a Cerberus item id or a material, {@code min} to {@code max} of it. */
    public record Drop(String item, int min, int max) {}

    /** A custom tool: an item carrying this id, gathering what its kinds allow up to its tier. */
    public record ToolDef(String id, Material material, String name, Set<String> kinds, int tier) {}

    /** A custom item (a custom resource's drop). */
    public record ItemDef(String id, Material material, String name, List<String> lore) {}

    /** What forged gear's quality adds, by quality (index = ordinal; Common adds nothing). */
    public record QualityRules(String skill, double[] oddsAt1, double[] oddsAt100, double[] weaponPower,
                               double[] armorFortitude, double[] toolFortune) {}

    /** The kinds of gear forging knows, from the item's material name. */
    public enum Gear { WEAPON, TOOL, WEAPON_AND_TOOL, ARMOR }

    private static final Map<String, Integer> UNITS = Map.of("SWORD", 2, "AXE", 3, "PICKAXE", 3, "SHOVEL", 1, "HOE", 2,
            "HELMET", 5, "CHESTPLATE", 8, "LEGGINGS", 7, "BOOTS", 4);

    private final Map<Material, ResourceDef> byBlock;
    private final Map<String, ResourceDef> resources;
    private final Map<String, ToolDef> tools;
    private final Map<String, ItemDef> items;
    private final String smeltingSkill;
    private final Map<Material, Double> smelting;
    private final String forgingSkill;
    private final Map<String, Double> perUnit;
    private final Map<Material, Double> forgeOverrides;
    private final double netheriteUpgrade;
    private final QualityRules quality;
    private final double fortunePerPoint, fortuneMax;
    private final int weakGap;
    private final double weakFactor;
    private final List<String> problems;

    private GatheringCatalog(Map<Material, ResourceDef> byBlock, Map<String, ResourceDef> resources, Map<String, ToolDef> tools,
                             Map<String, ItemDef> items, String smeltingSkill, Map<Material, Double> smelting, String forgingSkill,
                             Map<String, Double> perUnit, Map<Material, Double> forgeOverrides, double netheriteUpgrade,
                             QualityRules quality, double fortunePerPoint, double fortuneMax, int weakGap, double weakFactor,
                             List<String> problems) {
        this.byBlock = byBlock;
        this.resources = resources;
        this.tools = tools;
        this.items = items;
        this.smeltingSkill = smeltingSkill;
        this.smelting = smelting;
        this.forgingSkill = forgingSkill;
        this.perUnit = perUnit;
        this.forgeOverrides = forgeOverrides;
        this.netheriteUpgrade = netheriteUpgrade;
        this.quality = quality;
        this.fortunePerPoint = fortunePerPoint;
        this.fortuneMax = fortuneMax;
        this.weakGap = weakGap;
        this.weakFactor = weakFactor;
        this.problems = List.copyOf(problems);
    }

    public static GatheringCatalog empty() { return from(null); }

    /** The resource a block of {@code m} is, or null. */
    public ResourceDef resource(Material m) { return m == null ? null : byBlock.get(m); }
    public Map<String, ResourceDef> resources() { return resources; }
    public ToolDef tool(String id) { return id == null ? null : tools.get(id); }
    public Map<String, ToolDef> tools() { return tools; }
    public ItemDef item(String id) { return id == null ? null : items.get(id); }
    public Map<String, ItemDef> items() { return items; }
    /** True when placing a block of {@code m} must be remembered (it is some resource's block). */
    public boolean tracked(Material m) { return m != null && byBlock.containsKey(m); }

    public String smeltingSkill() { return smeltingSkill; }
    /** XP for taking one {@code m} out of a furnace (0 when none). */
    public double smeltXp(Material m) { return m == null ? 0 : smelting.getOrDefault(m, 0.0); }

    public String forgingSkill() { return forgingSkill; }
    public double netheriteUpgradeXp() { return netheriteUpgrade; }
    public QualityRules quality() { return quality; }
    public double fortunePerPoint() { return fortunePerPoint; }
    public double fortuneMax() { return fortuneMax; }
    public int weakGap() { return weakGap; }
    public double weakFactor() { return weakFactor; }
    public List<String> problems() { return problems; }

    /** XP for forging one item of {@code m} (0 when it isn't forged gear). */
    public double forgeXp(Material m) {
        if (m == null) return 0;
        Double o = forgeOverrides.get(m);
        if (o != null) return o;
        return forgeXp(m.name(), perUnit);
    }

    /** Forging XP from a material name: per-unit XP of its metal × how many units its shape takes. */
    static double forgeXp(String material, Map<String, Double> perUnit) {
        int cut = material.indexOf('_');
        if (cut < 0) return 0;
        String metal = material.substring(0, cut), shape = material.substring(cut + 1);
        if (metal.equals("GOLDEN")) metal = "GOLD";
        Integer units = UNITS.get(shape);
        Double each = perUnit.get(metal.toLowerCase(Locale.ROOT));
        return units == null || each == null ? 0 : units * each;
    }

    /** What kind of gear an item of {@code material} is, or null when it is none. */
    public static Gear gear(String material) {
        if (material == null) return null;
        String m = material.toUpperCase(Locale.ROOT);
        if (m.endsWith("_SWORD") || m.endsWith("_SPEAR")) return Gear.WEAPON;
        if (m.endsWith("_PICKAXE") || m.endsWith("_SHOVEL") || m.endsWith("_HOE")) return Gear.TOOL;
        if (m.endsWith("_AXE")) return Gear.WEAPON_AND_TOOL;
        if (m.endsWith("_HELMET") || m.endsWith("_CHESTPLATE") || m.endsWith("_LEGGINGS") || m.endsWith("_BOOTS")) return Gear.ARMOR;
        return null;
    }

    // ── loading ───────────────────────────────────────────────────

    public static GatheringCatalog from(ConfigurationSection root) {
        List<String> problems = new ArrayList<>();
        Map<String, ItemDef> items = new LinkedHashMap<>();
        ConfigurationSection is = root == null ? null : root.getConfigurationSection("items");
        if (is != null) for (String id : is.getKeys(false)) {
            ConfigurationSection s = is.getConfigurationSection(id);
            Material m = s == null ? null : material(s.getString("material"));
            if (m == null) { problems.add("item " + id + ": unknown material " + (s == null ? null : s.getString("material"))); continue; }
            items.put(id, new ItemDef(id, m, s.getString("name", id), List.copyOf(s.getStringList("lore"))));
        }

        Map<String, ToolDef> tools = new LinkedHashMap<>();
        ConfigurationSection ts = root == null ? null : root.getConfigurationSection("tools");
        if (ts != null) for (String id : ts.getKeys(false)) {
            ConfigurationSection s = ts.getConfigurationSection(id);
            Material m = s == null ? null : material(s.getString("material"));
            List<String> why = new ArrayList<>();
            if (m == null) why.add("unknown material " + (s == null ? null : s.getString("material")));
            Set<String> kinds = new LinkedHashSet<>();
            if (s != null) for (String k : s.getStringList("kinds")) kinds.add(k.toLowerCase(Locale.ROOT));
            if (kinds.isEmpty()) why.add("no kinds");
            int tier = s == null ? 0 : s.getInt("tier", 0);
            if (tier < 0 || tier > 100) why.add("tier " + tier + " outside 0-100");
            if (!why.isEmpty()) { problems.add("tool " + id + ": " + String.join("; ", why)); continue; }
            tools.put(id, new ToolDef(id, m, s.getString("name", id), Collections.unmodifiableSet(kinds), tier));
        }

        Map<Material, ResourceDef> byBlock = new EnumMap<>(Material.class);
        Map<String, ResourceDef> resources = new LinkedHashMap<>();
        ConfigurationSection rs = root == null ? null : root.getConfigurationSection("resources");
        if (rs != null) for (String id : rs.getKeys(false)) {
            ConfigurationSection s = rs.getConfigurationSection(id);
            if (s == null) { problems.add("resource " + id + ": not a section"); continue; }
            List<String> why = new ArrayList<>();
            String skill = s.getString("skill");
            if (skill == null || skill.isBlank()) why.add("no skill");
            Set<Material> blocks = java.util.EnumSet.noneOf(Material.class);
            for (String b : s.getStringList("blocks")) {
                Material m = material(b);
                if (m == null) why.add("unknown block " + b);
                else if (byBlock.containsKey(m)) why.add(b + " is already resource " + byBlock.get(m).id());
                else blocks.add(m);
            }
            if (blocks.isEmpty() && why.isEmpty()) why.add("no blocks");
            double xp = s.getDouble("xp", 0);
            if (!(xp >= 0 && xp <= 100_000)) why.add("xp " + xp + " outside 0-100000");
            int level = s.getInt("level", 1);
            if (level < 1 || level > 1000) why.add("level " + level + " outside 1-1000");
            String tool = s.getString("tool");
            int tier = s.getInt("tier", 0);
            if (tier < 0 || tier > 100) why.add("tier " + tier + " outside 0-100");
            List<Drop> drops = new ArrayList<>();
            for (Map<?, ?> d : s.getMapList("drops")) {
                Object item = d.get("item");
                int min = num(d.get("min"), num(d.get("amount"), 1)), max = num(d.get("max"), min);
                String it = item == null ? null : String.valueOf(item);
                if (it == null || (items.get(it) == null && material(it) == null)) why.add("unknown drop " + it);
                else if (min < 0 || max < min || max > 64) why.add("drop " + it + " amounts " + min + "-" + max + " outside 0-64");
                else drops.add(new Drop(it, min, max));
            }
            if (!why.isEmpty()) { problems.add("resource " + id + ": " + String.join("; ", why)); continue; }
            ResourceDef def = new ResourceDef(id, skill, Collections.unmodifiableSet(blocks), tool == null ? null : tool.toLowerCase(Locale.ROOT),
                    tier, level, xp, s.getBoolean("fortune", false), List.copyOf(drops));
            resources.put(id, def);
            for (Material m : blocks) byBlock.put(m, def);
        }

        ConfigurationSection sm = root == null ? null : root.getConfigurationSection("smelting");
        String smeltingSkill = sm == null ? "Smithing" : sm.getString("skill", "Smithing");
        Map<Material, Double> smelting = new EnumMap<>(Material.class);
        ConfigurationSection sx = sm == null ? null : sm.getConfigurationSection("xp");
        if (sx != null) for (String k : sx.getKeys(false)) {
            Material m = material(k);
            double v = sx.getDouble(k, -1);
            if (m == null || !(v >= 0 && v <= 10_000)) problems.add("smelting " + k + ": unknown item or xp outside 0-10000");
            else smelting.put(m, v);
        }

        ConfigurationSection fg = root == null ? null : root.getConfigurationSection("forging");
        String forgingSkill = fg == null ? "Smithing" : fg.getString("skill", "Smithing");
        Map<String, Double> perUnit = new LinkedHashMap<>();
        ConfigurationSection pu = fg == null ? null : fg.getConfigurationSection("per-unit");
        if (pu != null) for (String k : pu.getKeys(false)) {
            double v = pu.getDouble(k, -1);
            if (!(v >= 0 && v <= 10_000)) problems.add("forging per-unit " + k + ": outside 0-10000");
            else perUnit.put(k.toLowerCase(Locale.ROOT), v);
        }
        Map<Material, Double> overrides = new EnumMap<>(Material.class);
        ConfigurationSection ov = fg == null ? null : fg.getConfigurationSection("items");
        if (ov != null) for (String k : ov.getKeys(false)) {
            Material m = material(k);
            double v = ov.getDouble(k, -1);
            if (m == null || !(v >= 0 && v <= 10_000)) problems.add("forging " + k + ": unknown item or xp outside 0-10000");
            else overrides.put(m, v);
        }
        double netherite = fg == null ? 0 : Math.max(0, Math.min(10_000, fg.getDouble("netherite-upgrade", 0)));

        ConfigurationSection q = root == null ? null : root.getConfigurationSection("quality");
        QualityRules quality = new QualityRules(q == null ? "Smithing" : q.getString("skill", "Smithing"),
                weights(q, "odds.at-level-1", new double[] {100, 0, 0, 0}, problems),
                weights(q, "odds.at-level-100", new double[] {100, 0, 0, 0}, problems),
                weights(q, "weapon-power", new double[4], problems),
                weights(q, "armor-fortitude", new double[4], problems),
                weights(q, "tool-fortune", new double[4], problems));

        ConfigurationSection fo = root == null ? null : root.getConfigurationSection("fortune");
        double perPoint = fo == null ? 0 : Math.max(0, Math.min(1, fo.getDouble("chance-per-point", 0)));
        double max = fo == null ? 0 : Math.max(0, Math.min(1, fo.getDouble("max-chance", 0)));
        ConfigurationSection wk = root == null ? null : root.getConfigurationSection("weak-resources");
        int gap = wk == null ? 20 : Math.max(0, wk.getInt("level-gap", 20));
        double factor = wk == null ? 0.25 : Math.max(0, Math.min(1, wk.getDouble("factor", 0.25)));

        return new GatheringCatalog(Collections.unmodifiableMap(byBlock), Collections.unmodifiableMap(resources),
                Collections.unmodifiableMap(tools), Collections.unmodifiableMap(items), smeltingSkill,
                Collections.unmodifiableMap(smelting), forgingSkill, Collections.unmodifiableMap(perUnit),
                Collections.unmodifiableMap(overrides), netherite, quality, perPoint, max, gap, factor, problems);
    }

    /** A material by name, without the server's registry (unit tests read the shipped file too). */
    static Material material(String name) {
        if (name == null || name.isBlank()) return null;
        try {
            return Material.valueOf(name.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static int num(Object v, int dflt) {
        if (v instanceof Number n) return n.intValue();
        if (v == null) return dflt;
        try { return Integer.parseInt(String.valueOf(v).trim()); } catch (NumberFormatException e) { return Integer.MIN_VALUE; }
    }

    /** {@code key}'s common/fine/superior/masterwork numbers (each 0-10000), or {@code dflt}. */
    private static double[] weights(ConfigurationSection q, String key, double[] dflt, List<String> problems) {
        ConfigurationSection s = q == null ? null : q.getConfigurationSection(key);
        if (s == null) return dflt.clone();
        double[] out = new double[Quality.values().length];
        for (Quality ql : Quality.values()) {
            double v = s.getDouble(ql.id(), 0);
            if (!(v >= 0 && v <= 10_000)) { problems.add("quality " + key + "." + ql.id() + ": " + v + " outside 0-10000"); v = 0; }
            out[ql.ordinal()] = v;
        }
        return out;
    }
}
