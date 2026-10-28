package Gathering;

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
import java.util.NavigableMap;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Pattern;

/**
 * Farming's rules (docs/design/FARMING_COOKING.md §3), parsed from {@code resources.yml farming:}: the crops, when each is
 * ripe, what it pays, and the techniques by level (Replant, Wide Harvest's reach, crop quality). Pure: no server calls,
 * so the numbers the game uses, the menus show and the tests check are the same.
 */
public final class FarmingRules {

    /** When a crop pays. */
    public enum Ripe {
        /** An ageable crop at its last age (wheat 7, beetroots 3, cocoa 2, nether wart 3). */
        MAX_AGE,
        /** An ageable crop at {@link Crop#age} or more (a sweet berry bush with berries: 2). */
        AGE,
        /** Cave vines with glow berries on them. */
        BERRIES,
        /** A block that grew there (a melon, a pumpkin, a cane or cactus segment): any one the placed-block memory doesn't hold. */
        GROWN;

        static Ripe parse(Object v) {
            if (v instanceof ConfigurationSection || v instanceof Map<?, ?>) return AGE;
            String s = v == null ? "" : String.valueOf(v).trim().toLowerCase(Locale.ROOT).replace('_', '-');
            return switch (s) {
                case "max-age" -> MAX_AGE;
                case "berries" -> BERRIES;
                case "grown" -> GROWN;
                default -> null;
            };
        }

        /** "fully grown", "with berries"... for the crop list. */
        public String describe(int age) {
            return switch (this) {
                case MAX_AGE -> "fully grown";
                case AGE -> "from growth stage " + age;
                case BERRIES -> "with berries";
                case GROWN -> "grown (not placed)";
            };
        }
    }

    /**
     * One crop. {@code pick}: also harvested by right-clicking (sweet berries, glow berries). {@code seed}: what a replant
     * takes (null: the crop isn't replanted, its stem or plant stays). {@code wide}: Wide Harvest takes it. {@code quality}:
     * its produce rolls a quality from {@link #qualityAt()}. {@code column}: segments above the broken one fall with it
     * (sugar cane, cactus) and are counted at the break.
     */
    public record Crop(String id, String name, Set<Material> blocks, Ripe ripe, int age, boolean pick, Material produce, Material seed,
                       int level, double xp, double discoveryXp, boolean wide, boolean quality, boolean column) {
        public boolean replants() { return seed != null; }
    }

    static final Pattern ID = Pattern.compile("[a-z0-9_]+");

    private final String skill;
    private final Map<String, Crop> crops;
    private final Map<Material, Crop> byBlock;
    private final int replantAt;
    /** Wide Harvest's side length (3 = 3x3) from each level on. */
    private final NavigableMap<Integer, Integer> reach;
    private final int perTick;
    private final int qualityAt;
    private final double[] qualityAtStart, qualityAt100;

    public static final FarmingRules NONE = new FarmingRules(null, Map.of(), Map.of(), 0, new TreeMap<>(), 16, 0,
            new double[] {100, 0, 0, 0}, new double[] {100, 0, 0, 0});

    FarmingRules(String skill, Map<String, Crop> crops, Map<Material, Crop> byBlock, int replantAt, NavigableMap<Integer, Integer> reach,
                 int perTick, int qualityAt, double[] qualityAtStart, double[] qualityAt100) {
        this.skill = skill;
        this.crops = crops;
        this.byBlock = byBlock;
        this.replantAt = replantAt;
        this.reach = reach;
        this.perTick = perTick;
        this.qualityAt = qualityAt;
        this.qualityAtStart = qualityAtStart;
        this.qualityAt100 = qualityAt100;
    }

    /** False when farming: is off or unusable: harvests then pay nothing and nothing changes them. */
    public boolean enabled() { return skill != null; }

    public String skill() { return skill; }

    public Crop crop(Material m) { return m == null ? null : byBlock.get(m); }

    public Crop crop(String id) { return id == null ? null : crops.get(id); }

    public Collection<Crop> crops() { return crops.values(); }

    /** True for a crop block a player can also place by hand (a melon, a pumpkin, cane, cactus): the placed-block memory keeps those. */
    public boolean placeable(Material m) {
        Crop c = crop(m);
        return c != null && c.ripe() == Ripe.GROWN;
    }

    /** Pure: whether a crop's block in this state pays. {@code placed}: the placed-block memory holds it (a GROWN crop then doesn't). */
    public static boolean ripe(Crop c, int age, int maxAge, boolean berries, boolean placed) {
        return switch (c.ripe()) {
            case MAX_AGE -> maxAge > 0 && age >= maxAge;
            case AGE -> age >= c.age();
            case BERRIES -> berries;
            case GROWN -> !placed;
        };
    }

    // ── techniques ───────────────────────────────────────────────

    public int replantAt() { return replantAt; }

    public boolean replants(int level) { return replantAt > 0 && level >= replantAt; }

    /** Wide Harvest's side length at {@code level} (3 = 3x3), 0 before it opens. */
    public int reach(int level) {
        Map.Entry<Integer, Integer> e = reach.floorEntry(level);
        return e == null ? 0 : e.getValue();
    }

    /** The level Wide Harvest opens at (0: never). */
    public int wideAt() { return reach.isEmpty() ? 0 : reach.firstKey(); }

    /** The levels the reach grows at, with the side length from each. */
    public NavigableMap<Integer, Integer> reachSteps() { return Collections.unmodifiableNavigableMap(reach); }

    /** Crops a Wide Harvest takes per tick (other plugins' break handlers then never run for a whole field at once). */
    public int perTick() { return perTick; }

    /** The level crop quality opens at (0: never). */
    public int qualityAt() { return qualityAt; }

    /**
     * The quality odds at {@code level}, as weights per quality (index = ordinal): nothing but Common before
     * {@link #qualityAt()}, then from the odds at that level to the odds at 100 (linear, clamped).
     */
    public double[] qualityOdds(int level) {
        if (qualityAt <= 0 || level < qualityAt) return new double[] {100, 0, 0, 0};
        double t = qualityAt >= 100 ? 1 : Math.max(0, Math.min(1, (level - qualityAt) / (double) (100 - qualityAt)));
        double[] out = new double[Quality.values().length];
        for (int i = 0; i < out.length; i++) {
            double a = i < qualityAtStart.length ? qualityAtStart[i] : 0, b = i < qualityAt100.length ? qualityAt100[i] : 0;
            out[i] = a + (b - a) * t;
        }
        return out;
    }

    /** The quality one harvest of {@code c} rolls at {@code level} with {@code r} in [0, 1); Common for a crop without quality. */
    public Quality rollQuality(Crop c, int level, double r) {
        if (c == null || !c.quality()) return Quality.COMMON;
        return Quality.roll(qualityOdds(level), r);
    }

    /** The chance (0-1) of {@code q} exactly at {@code level}. */
    public double chance(Quality q, int level) {
        double[] w = qualityOdds(level);
        double total = 0;
        for (double v : w) total += Math.max(0, v);
        return total > 0 ? Math.max(0, w[q.ordinal()]) / total : 0;
    }

    // ── loading ──────────────────────────────────────────────────

    static FarmingRules from(ConfigurationSection f, java.util.function.UnaryOperator<String> skills, List<String> problems) {
        if (f == null) return NONE;
        String written = f.getString("skill", "Farming");
        String skill = skills.apply(written.trim());
        if (skill == null) { problems.add("farming: unknown skill " + written + " - farming pays nothing"); return NONE; }

        Map<String, Crop> crops = new LinkedHashMap<>();
        Map<Material, Crop> byBlock = new EnumMap<>(Material.class);
        ConfigurationSection cs = f.getConfigurationSection("crops");
        if (cs != null) for (String id : cs.getKeys(false)) {
            Object raw = cs.get(id);
            if (Boolean.FALSE.equals(raw) || "off".equalsIgnoreCase(String.valueOf(raw))) continue;
            ConfigurationSection s = cs.getConfigurationSection(id);
            if (s == null) { problems.add("farming crop " + id + ": not a section"); continue; }
            List<String> why = new ArrayList<>();
            if (!ID.matcher(id).matches()) why.add("id must be plain (a-z, 0-9, _)");
            Set<Material> blocks = java.util.EnumSet.noneOf(Material.class);
            for (String b : s.getStringList("blocks")) {
                Material m = GatheringCatalog.material(b);
                if (m == null) why.add("unknown block " + b);
                else if (byBlock.containsKey(m)) why.add(b + " is already crop " + byBlock.get(m).id());
                else blocks.add(m);
            }
            if (blocks.isEmpty() && why.isEmpty()) why.add("no blocks");
            Object ripeRaw = s.get("ripe");
            Ripe ripe = Ripe.parse(ripeRaw);
            int age = 0;
            if (ripe == null) why.add("ripe " + ripeRaw + " is not max-age, { age: N }, berries or grown");
            else if (ripe == Ripe.AGE) {
                age = s.getInt("ripe.age", -1);
                if (age < 1 || age > 25) why.add("ripe age " + age + " outside 1-25");
            }
            Material produce = GatheringCatalog.material(s.getString("produce"));
            if (produce == null) why.add("unknown produce " + s.getString("produce"));
            String seedName = s.getString("seed");
            Material seed = seedName == null || seedName.isBlank() ? null : GatheringCatalog.material(seedName);
            if (seedName != null && !seedName.isBlank() && seed == null) why.add("unknown seed " + seedName);
            int level = s.getInt("level", 1);
            if (level < 1 || level > 1000) why.add("level " + level + " outside 1-1000");
            double xp = s.getDouble("xp", 0), disc = s.getDouble("discovery-xp", 0);
            if (!(xp >= 0 && xp <= 100_000)) why.add("xp " + xp + " outside 0-100000");
            if (!(disc >= 0 && disc <= 100_000)) why.add("discovery-xp " + disc + " outside 0-100000");
            boolean column = s.getBoolean("column", false);
            if (column && ripe != Ripe.GROWN) why.add("column needs ripe: grown");
            if (!why.isEmpty()) { problems.add("farming crop " + id + ": " + String.join("; ", why)); continue; }
            Crop c = new Crop(id, s.getString("name", label(id)), Collections.unmodifiableSet(blocks), ripe, age, s.getBoolean("pick", false),
                    produce, seed, level, xp, disc, s.getBoolean("wide", false), s.getBoolean("quality", false), column);
            crops.put(id, c);
            for (Material m : blocks) byBlock.put(m, c);
        }

        int replantAt = Math.max(0, f.getInt("replant-at", 10));
        NavigableMap<Integer, Integer> reach = new TreeMap<>();
        ConfigurationSection rs = f.getConfigurationSection("wide-harvest.reach");
        if (rs != null) for (String k : rs.getKeys(false)) {
            int at, side = rs.getInt(k, -1);
            try { at = Integer.parseInt(k.trim()); } catch (NumberFormatException e) { at = -1; }
            if (at < 1 || at > 1000 || side < 3 || side > 15 || side % 2 == 0) {
                problems.add("farming wide-harvest.reach " + k + ": " + rs.get(k) + " (a level 1-1000: an odd side 3-15)");
                continue;
            }
            reach.put(at, side);
        }
        int perTick = Math.max(1, Math.min(64, f.getInt("wide-harvest.per-tick", 16)));
        int qualityAt = Math.max(0, f.getInt("quality.at", 50));
        double[] start = odds(f.getConfigurationSection("quality.odds.at-level-" + qualityAt), "quality.odds.at-level-" + qualityAt, problems);
        double[] top = odds(f.getConfigurationSection("quality.odds.at-level-100"), "quality.odds.at-level-100", problems);
        return new FarmingRules(skill, Collections.unmodifiableMap(crops), Collections.unmodifiableMap(byBlock), replantAt, reach, perTick,
                qualityAt, start, top);
    }

    /** Fine, superior and masterwork chances in percent (each 0-100); Common is what is left of 100. */
    private static double[] odds(ConfigurationSection s, String where, List<String> problems) {
        double[] out = {100, 0, 0, 0};
        if (s == null) return out;
        double sum = 0;
        for (Quality q : List.of(Quality.FINE, Quality.SUPERIOR, Quality.MASTERWORK)) {
            double v = s.getDouble(q.id(), 0);
            if (!(v >= 0 && v <= 100)) { problems.add("farming " + where + "." + q.id() + ": " + v + " outside 0-100"); v = 0; }
            out[q.ordinal()] = v;
            sum += v;
        }
        if (sum > 100) {
            problems.add("farming " + where + ": the chances add up to " + sum + "%, more than 100 - no quality there");
            return new double[] {100, 0, 0, 0};
        }
        out[Quality.COMMON.ordinal()] = 100 - sum;
        return out;
    }

    /** "sweet_berries" → "Sweet berries". */
    static String label(String id) {
        String s = id.replace('_', ' ');
        return s.isEmpty() ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }
}
