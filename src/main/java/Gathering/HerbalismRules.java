package Gathering;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Pattern;

/**
 * Herbalism's rules (docs/design/HERBALISM.md): the herbs (wild plants as reagents, {@code resources.yml herbalism.herbs}),
 * the rare reagents from their level, reagent quality, and Wildcrafting's reach. Pure: no server calls, so every rule is
 * unit-tested; the runtime ({@link Herbalism}) asks these rules.
 */
public final class HerbalismRules {

    static final Pattern ID = Pattern.compile("[a-z0-9_]+");

    /**
     * One herb. {@code blocks}: the blocks that are it; {@code produce}: the items that count as gathering it (a break that
     * drops one of them pays; a fern broken without shears drops none); {@code shears}: whether it needs shears to drop
     * itself (for the hint); {@code grows}: where it grows, for the journal; {@code rare}: the rare reagent it can give (or null).
     */
    public record Herb(String id, String name, Set<Material> blocks, Set<Material> produce, int level, double xp,
                       double discoveryXp, String grows, boolean shears, String rare) {}

    /** A rare reagent: its id, the Cerberus item it is ({@code items:}) and that item's name, and the herbs that give it. */
    public record Rare(String id, String item, String name, List<String> from) {}

    private final String skill;
    private final Map<String, Herb> herbs;
    private final Map<Material, Herb> byBlock;
    private final Map<String, Rare> rares;
    private final int rareAt;
    private final double rareAtStart, rareAt100;
    /** Wildcrafting's side length (3 = 3x3) from each level on. */
    private final NavigableMap<Integer, Integer> reach;
    private final int perTick;
    private final int qualityAt;
    private final double[] qualityAtStart, qualityAt100;

    public static final HerbalismRules NONE = new HerbalismRules(null, Map.of(), Map.of(), Map.of(), 0, 0, 0, new TreeMap<>(), 16, 0,
            new double[] {100, 0, 0, 0}, new double[] {100, 0, 0, 0});

    HerbalismRules(String skill, Map<String, Herb> herbs, Map<Material, Herb> byBlock, Map<String, Rare> rares, int rareAt,
                   double rareAtStart, double rareAt100, NavigableMap<Integer, Integer> reach, int perTick, int qualityAt,
                   double[] qualityAtStart, double[] qualityAt100) {
        this.skill = skill;
        this.herbs = herbs;
        this.byBlock = byBlock;
        this.rares = rares;
        this.rareAt = rareAt;
        this.rareAtStart = rareAtStart;
        this.rareAt100 = rareAt100;
        this.reach = reach;
        this.perTick = perTick;
        this.qualityAt = qualityAt;
        this.qualityAtStart = qualityAtStart;
        this.qualityAt100 = qualityAt100;
    }

    /** False when herbalism: is off or unusable: gathering then pays nothing and nothing changes the drops. */
    public boolean enabled() { return skill != null; }

    public String skill() { return skill; }

    public Herb herb(Material m) { return m == null ? null : byBlock.get(m); }

    public Herb herb(String id) { return id == null ? null : herbs.get(id); }

    public Collection<Herb> herbs() { return herbs.values(); }

    /** The rare reagents, in the order the file lists them. */
    public Collection<Rare> rares() { return rares.values(); }

    public Rare rare(String id) { return id == null ? null : rares.get(id); }

    /** True for a herb's block: the placed-block memory keeps it, so a plant placed by hand pays nothing. */
    public boolean tracked(Material m) { return m != null && byBlock.containsKey(m); }

    // ── techniques and milestones ────────────────────────────────

    /** The level rare reagents start dropping at (0: never). */
    public int rareAt() { return rareAt; }

    /** The chance (0-1) a gathered herb that has a rare reagent gives one at {@code level}: rising evenly to its value at 100. */
    public double rareChance(int level) {
        if (rareAt <= 0 || level < rareAt) return 0;
        double t = rareAt >= 100 ? 1 : Math.max(0, Math.min(1, (level - rareAt) / (double) (100 - rareAt)));
        return rareAtStart + (rareAt100 - rareAtStart) * t;
    }

    /** Wildcrafting's side length at {@code level} (3 = 3x3), 0 before it opens. */
    public int reach(int level) {
        Map.Entry<Integer, Integer> e = reach.floorEntry(level);
        return e == null ? 0 : e.getValue();
    }

    /** The level Wildcrafting opens at (0: never). */
    public int wildcraftAt() { return reach.isEmpty() ? 0 : reach.firstKey(); }

    /** The levels the reach grows at, with the side length from each. */
    public NavigableMap<Integer, Integer> reachSteps() { return Collections.unmodifiableNavigableMap(reach); }

    /** Plants a Wildcrafting sweep takes per tick. */
    public int perTick() { return perTick; }

    /** The level reagent quality opens at (0: never). */
    public int qualityAt() { return qualityAt; }

    /** The quality odds at {@code level} as weights per quality (index = ordinal), as crops have them. */
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

    /** The quality one gathered herb rolls at {@code level} with {@code r} in [0, 1). */
    public Quality rollQuality(int level, double r) {
        return Quality.roll(qualityOdds(level), r);
    }

    /** The chance (0-1) of {@code q} exactly at {@code level}. */
    public double chance(Quality q, int level) {
        double[] w = qualityOdds(level);
        double total = 0;
        for (double v : w) total += Math.max(0, v);
        return total > 0 ? Math.max(0, w[q.ordinal()]) / total : 0;
    }

    /** The techniques {@code from} → {@code to} opened, "Name: what it does", as level-ups show them. */
    public List<String> openedBetween(int from, int to) {
        List<String> out = new ArrayList<>();
        for (Map.Entry<Integer, Integer> e : reach.entrySet()) {
            if (e.getKey() > from && e.getKey() <= to) {
                out.add("Wildcrafting " + e.getValue() + "×" + e.getValue() + ": Sneak and gather a wild plant with shears to gather "
                        + "every one of its kind around it");
            }
        }
        if (rareAt > from && rareAt <= to && !rares.isEmpty()) {
            List<String> names = rares.values().stream().map(Rare::name).toList();
            String list = names.size() == 1 ? names.get(0)
                    : String.join(", ", names.subList(0, names.size() - 1)) + " or " + names.get(names.size() - 1);
            out.add("Rare reagents: gathered herbs sometimes give " + list);
        }
        if (qualityAt > from && qualityAt <= to) out.add("Reagent quality: gathered herbs can be Fine, Superior or Masterwork");
        return out;
    }

    // ── loading ──────────────────────────────────────────────────

    static HerbalismRules from(ConfigurationSection h, Map<String, GatheringCatalog.ItemDef> items,
                               java.util.function.UnaryOperator<String> skills, List<String> problems) {
        return from(h, items, skills, Map.of(), problems);
    }

    /**
     * {@code taken}: blocks that already have rules of their own (a crop, a resource), with what they are: a herb never
     * claims one, so one break never pays twice.
     */
    static HerbalismRules from(ConfigurationSection h, Map<String, GatheringCatalog.ItemDef> items,
                               java.util.function.UnaryOperator<String> skills, Map<Material, String> taken, List<String> problems) {
        if (h == null) return NONE;
        String written = h.getString("skill", "Herbalism");
        String skill = skills.apply(written.trim());
        if (skill == null) { problems.add("herbalism: unknown skill " + written + " - herbalism pays nothing"); return NONE; }

        Map<String, Rare> rares = new LinkedHashMap<>();
        Map<String, List<String>> from = new LinkedHashMap<>();
        ConfigurationSection rs = h.getConfigurationSection("rare-reagents");
        if (rs != null) for (String id : rs.getKeys(false)) {
            Object raw = rs.get(id);
            if (Boolean.FALSE.equals(raw) || "off".equalsIgnoreCase(String.valueOf(raw))) continue;
            ConfigurationSection s = rs.getConfigurationSection(id);
            String item = s == null ? null : s.getString("item", id);
            if (s == null || !ID.matcher(id).matches()) { problems.add("herbalism rare reagent " + id + ": not a section with a plain id"); continue; }
            if (items.get(item) == null) { problems.add("herbalism rare reagent " + id + ": unknown item " + item + " (items:)"); continue; }
            from.put(id, new ArrayList<>());
            rares.put(id, new Rare(id, item, items.get(item).name(), List.of()));
        }

        Map<String, Herb> herbs = new LinkedHashMap<>();
        Map<Material, Herb> byBlock = new EnumMap<>(Material.class);
        ConfigurationSection hs = h.getConfigurationSection("herbs");
        if (hs != null) for (String id : hs.getKeys(false)) {
            Object raw = hs.get(id);
            if (Boolean.FALSE.equals(raw) || "off".equalsIgnoreCase(String.valueOf(raw))) continue;
            ConfigurationSection s = hs.getConfigurationSection(id);
            if (s == null) { problems.add("herbalism herb " + id + ": not a section"); continue; }
            List<String> why = new ArrayList<>();
            if (!ID.matcher(id).matches()) why.add("id must be plain (a-z, 0-9, _)");
            Set<Material> blocks = EnumSet.noneOf(Material.class);
            for (String b : s.getStringList("blocks")) {
                Material m = GatheringCatalog.material(b);
                if (m == null || m.isLegacy() || m == Material.AIR) why.add("unknown block " + b);
                else if (byBlock.containsKey(m)) why.add(b + " is already herb " + byBlock.get(m).id());
                else if (taken.containsKey(m)) why.add(b + " is already " + taken.get(m));
                else blocks.add(m);
            }
            if (blocks.isEmpty() && why.isEmpty()) why.add("no blocks");
            // What counts as gathering it: written, or its blocks (a dandelion drops a dandelion). A block with no item of its
            // own (tall seagrass, a kelp or vine stem) writes its produce. Pure: no server registry is asked here.
            Set<Material> produce = EnumSet.noneOf(Material.class);
            if (s.isList("produce")) {
                for (String p : s.getStringList("produce")) {
                    Material m = GatheringCatalog.material(p);
                    if (m == null || m.isLegacy() || m == Material.AIR) why.add("unknown produce " + p);
                    else produce.add(m);
                }
            } else {
                produce.addAll(blocks);
            }
            if (produce.isEmpty() && why.isEmpty()) why.add("no produce (write produce: [ITEM])");
            int level = s.getInt("level", 1);
            if (level < 1 || level > 1000) why.add("level " + level + " outside 1-1000");
            double xp = s.getDouble("xp", 0), disc = s.getDouble("discovery-xp", 0);
            if (!(xp >= 0 && xp <= 100_000)) why.add("xp " + xp + " outside 0-100000");
            if (!(disc >= 0 && disc <= 100_000)) why.add("discovery-xp " + disc + " outside 0-100000");
            String rare = s.getString("rare");
            if (rare != null && !rares.containsKey(rare)) why.add("unknown rare reagent " + rare + " (rare-reagents:)");
            if (!why.isEmpty()) { problems.add("herbalism herb " + id + ": " + String.join("; ", why)); continue; }
            Herb herb = new Herb(id, s.getString("name", label(id)), Collections.unmodifiableSet(blocks), Collections.unmodifiableSet(produce),
                    level, xp, disc, s.getString("grows", ""), s.getBoolean("shears", false), rare);
            herbs.put(id, herb);
            for (Material m : blocks) byBlock.put(m, herb);
            if (rare != null) from.get(rare).add(id);
        }
        for (Map.Entry<String, Rare> e : new ArrayList<>(rares.entrySet())) {
            rares.put(e.getKey(), new Rare(e.getKey(), e.getValue().item(), e.getValue().name(), List.copyOf(from.get(e.getKey()))));
        }

        int rareAt = Math.max(0, h.getInt("rare.at", 25));
        double rareStart = h.getDouble("rare.chance-at-start", 0.02), rareTop = h.getDouble("rare.chance-at-100", 0.06);
        if (!(rareStart >= 0 && rareStart <= 1) || !(rareTop >= 0 && rareTop <= 1)) {
            problems.add("herbalism rare: chances must be 0-1 - no rare reagents");
            rareAt = 0;
        }

        NavigableMap<Integer, Integer> reach = new TreeMap<>();
        ConfigurationSection ws = h.getConfigurationSection("wildcrafting.reach");
        if (ws != null) for (String k : ws.getKeys(false)) {
            int at, side = ws.getInt(k, -1);
            try { at = Integer.parseInt(k.trim()); } catch (NumberFormatException e) { at = -1; }
            if (at < 1 || at > 1000 || side < 3 || side > 15 || side % 2 == 0) {
                problems.add("herbalism wildcrafting.reach " + k + ": " + ws.get(k) + " (a level 1-1000: an odd side 3-15)");
                continue;
            }
            reach.put(at, side);
        }
        int perTick = Math.max(1, Math.min(64, h.getInt("wildcrafting.per-tick", 16)));
        int qualityAt = Math.max(0, h.getInt("quality.at", 50));
        double[] start = odds(h.getConfigurationSection("quality.odds.at-level-" + qualityAt), "quality.odds.at-level-" + qualityAt, problems);
        double[] top = odds(h.getConfigurationSection("quality.odds.at-level-100"), "quality.odds.at-level-100", problems);
        return new HerbalismRules(skill, Collections.unmodifiableMap(herbs), Collections.unmodifiableMap(byBlock),
                Collections.unmodifiableMap(rares), rareAt, rareStart, rareTop, reach, perTick, qualityAt, start, top);
    }

    /** Fine, superior and masterwork chances in percent (each 0-100); Common is what is left of 100. */
    private static double[] odds(ConfigurationSection s, String where, List<String> problems) {
        double[] out = {100, 0, 0, 0};
        if (s == null) return out;
        double sum = 0;
        for (Quality q : List.of(Quality.FINE, Quality.SUPERIOR, Quality.MASTERWORK)) {
            double v = s.getDouble(q.id(), 0);
            if (!(v >= 0 && v <= 100)) { problems.add("herbalism " + where + "." + q.id() + ": " + v + " outside 0-100"); v = 0; }
            out[q.ordinal()] = v;
            sum += v;
        }
        if (sum > 100) {
            problems.add("herbalism " + where + ": the chances add up to " + sum + "%, more than 100 - no quality there");
            return new double[] {100, 0, 0, 0};
        }
        out[Quality.COMMON.ordinal()] = 100 - sum;
        return out;
    }

    /** "lily_of_the_valley" → "Lily of the valley". */
    static String label(String id) {
        String s = id.replace('_', ' ');
        return s.isEmpty() ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }
}
