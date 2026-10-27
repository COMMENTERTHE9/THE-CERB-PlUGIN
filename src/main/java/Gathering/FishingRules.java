package Gathering;

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
import java.util.TreeMap;

/**
 * Fishing's rules (docs/design/FISHING.md), read from resources.yml {@code fishing:}. Pure: which water a bobber floats in,
 * which species a bite can be, the XP a catch pays, the techniques a level opens, the treasure and the creatures a catch
 * can bring, and how strong a called creature is. Nothing here touches the world.
 */
public final class FishingRules {

    /** How rare a species, a treasure or a creature is: its XP, its discovery bonus, its colour and the bite it shows. */
    public enum Rarity {
        COMMON("Common", "§f"), UNCOMMON("Uncommon", "§a"), RARE("Rare", "§9"), EPIC("Epic", "§5"), LEGENDARY("Legendary", "§6");

        public final String displayName;
        /** Legacy colour code (section sign + digit) for chat and HUD texts. */
        public final String color;

        Rarity(String displayName, String color) { this.displayName = displayName; this.color = color; }

        static Rarity parse(String s) {
            if (s == null) return null;
            try { return valueOf(s.trim().toUpperCase(Locale.ROOT)); } catch (IllegalArgumentException e) { return null; }
        }
    }

    public enum Time { ANY, DAY, NIGHT }
    public enum Weather { ANY, CLEAR, RAIN, THUNDER }

    /** A kind of water: by biome (keys like "river"), or underground (no sky light, below sea level), or the fallback. */
    public record Water(String id, String name, Set<String> biomes, boolean underground, boolean fallback) {}

    /** The moment at the bobber: what a species' conditions ask about. Biome keys are lower case ("deep_ocean"). */
    public record Moment(String water, String biome, boolean night, boolean rain, boolean thunder) {}

    /** When a species bites: time of day, weather, and (optionally) only some of its water's biomes. */
    public record When(Time time, Weather weather, Set<String> biomes) {
        public static final When ALWAYS = new When(Time.ANY, Weather.ANY, Set.of());

        public boolean matches(Moment m) {
            return timeOk(m.night()) && weatherOk(m.rain(), m.thunder()) && biomeOk(m.biome());
        }

        // The three conditions one by one: matches() is exactly all of them, and the journal's checks (checks) use the same.
        public boolean timeOk(boolean night) {
            return !(time == Time.DAY && night) && !(time == Time.NIGHT && !night);
        }

        public boolean weatherOk(boolean rain, boolean thunder) {
            if (weather == Weather.CLEAR && rain) return false;
            if (weather == Weather.RAIN && !rain) return false;
            return !(weather == Weather.THUNDER && !thunder);
        }

        public boolean biomeOk(String biome) {
            return biomes.isEmpty() || biomes.contains(biome);
        }

        /** "at night, in rain" style words for menus; empty when always. */
        public String describe() {
            List<String> parts = new ArrayList<>();
            if (time == Time.NIGHT) parts.add("at night");
            if (time == Time.DAY) parts.add("by day");
            if (weather == Weather.RAIN) parts.add("in rain");
            if (weather == Weather.THUNDER) parts.add("in a thunderstorm");
            if (weather == Weather.CLEAR) parts.add("in clear weather");
            if (!biomes.isEmpty()) parts.add("in " + String.join(", ", biomes.stream().map(b -> b.replace('_', ' ')).toList()));
            return String.join(", ", parts);
        }
    }

    /** A species of one water: its custom item, its rarity, the level it needs, its weight among the water's species. */
    public record Species(String id, String water, String item, Rarity rarity, int level, double weight, When when,
                          double minKg, double maxKg, double xp) {}

    /** Cerberus treasure (from Treasure Sense, open water only). */
    public record Treasure(String id, String item, Rarity rarity, int level, double weight, double xp) {}

    /** One thing a creature drops: a custom item id or a vanilla material name, its chance and how many. */
    public record Drop(String item, double chance, int min, int max) {}

    /**
     * A sea creature a catch can bank as a lure and its owner can call (docs/design/FISHING.md §7). {@code tier} is a
     * {@code Manager.Tier} name; {@code health} multiplies a normal mob's pool at the same level and tier.
     */
    public record Creature(String id, String name, String entity, Set<String> waters, int level, String tier, double xpMultiplier,
                           double health, double scale, double weight, boolean apex, Map<String, String> equipment,
                           List<Drop> drops, double fishingShare, double discoveryXp, Rarity rarity) {}

    /** The milestones (levels and numbers). */
    public record Techniques(int creatures, int quickBite, double quickBiteWait, int treasureSense, int deepWaters,
                             double eliteHealth, double eliteXp, int eliteDropRolls, int twinCatch, int apex, double apexChance) {}

    /** Limits on called creatures. */
    public record Calls(int perPlayer, int server, double leashBlocks, int leashSeconds, int fightSeconds, int levelSpread) {}

    /** XP for what vanilla catches (junk, its fish, its treasure) and, by rarity, for species and Cerberus treasure. */
    public record Xp(double junk, double fish, double treasure, Map<Rarity, Double> rarity, Map<Rarity, Double> discovery) {
        public double of(Rarity r) { return rarity.getOrDefault(r, 0.0); }
        public double discovery(Rarity r) { return discovery.getOrDefault(r, 0.0); }
    }

    public static final Techniques DEFAULT_TECHNIQUES = new Techniques(5, 10, 0.85, 25, 50, 1.5, 1.5, 1, 75, 100, 0.0005);
    public static final Calls DEFAULT_CALLS = new Calls(1, 32, 48, 30, 300, 3);
    public static final Xp DEFAULT_XP;
    static {
        Map<Rarity, Double> r = new EnumMap<>(Rarity.class), d = new EnumMap<>(Rarity.class);
        double[] xp = {12, 25, 60, 150, 400}, disc = {50, 120, 300, 750, 2000};
        for (Rarity k : Rarity.values()) { r.put(k, xp[k.ordinal()]); d.put(k, disc[k.ordinal()]); }
        DEFAULT_XP = new Xp(2, 10, 30, Collections.unmodifiableMap(r), Collections.unmodifiableMap(d));
    }

    /** Ids written in resources.yml must be plain (they are saved on players, separated by these characters). */
    static final java.util.regex.Pattern ID = java.util.regex.Pattern.compile("[a-z0-9_]{1,40}");

    private final String skill;
    private final List<Water> waters;
    private final Map<String, List<Species>> species; // by water id
    private final Map<String, Species> speciesById;
    private final List<Treasure> treasure;
    private final Map<String, Creature> creatures;
    private final Techniques techniques;
    private final Calls calls;
    private final Xp xp;
    private final double vanillaAt1, vanillaAt100, waitFloor;

    FishingRules(String skill, List<Water> waters, Map<String, List<Species>> species, List<Treasure> treasure,
                 Map<String, Creature> creatures, Techniques techniques, Calls calls, Xp xp,
                 double vanillaAt1, double vanillaAt100, double waitFloor) {
        this.skill = skill;
        this.waters = List.copyOf(waters);
        Map<String, List<Species>> sp = new LinkedHashMap<>();
        Map<String, Species> byId = new LinkedHashMap<>();
        for (Map.Entry<String, List<Species>> e : species.entrySet()) {
            sp.put(e.getKey(), List.copyOf(e.getValue()));
            for (Species s : e.getValue()) byId.put(s.id(), s);
        }
        this.species = Collections.unmodifiableMap(sp);
        this.speciesById = Collections.unmodifiableMap(byId);
        this.treasure = List.copyOf(treasure);
        this.creatures = Collections.unmodifiableMap(new LinkedHashMap<>(creatures));
        this.techniques = techniques;
        this.calls = calls;
        this.xp = xp;
        this.vanillaAt1 = vanillaAt1;
        this.vanillaAt100 = vanillaAt100;
        this.waitFloor = waitFloor;
    }

    /** No fishing section (or none that loads): vanilla fishing with Fishing XP for its catches, nothing else. */
    public static final FishingRules NONE = new FishingRules(null, List.of(), Map.of(), List.of(), Map.of(), DEFAULT_TECHNIQUES,
            DEFAULT_CALLS, DEFAULT_XP, 1.0, 1.0, 0.4);

    public String skill() { return skill; }
    public boolean enabled() { return skill != null; }
    public List<Water> waters() { return waters; }
    public Water water(String id) { for (Water w : waters) if (w.id().equals(id)) return w; return null; }
    public List<Species> species(String water) { return species.getOrDefault(water, List.of()); }
    public Species speciesById(String id) { return id == null ? null : speciesById.get(id); }
    public Map<String, Species> allSpecies() { return speciesById; }
    public List<Treasure> treasure() { return treasure; }
    public Treasure treasureById(String id) { for (Treasure t : treasure) if (t.id().equals(id)) return t; return null; }
    public Map<String, Creature> creatures() { return creatures; }
    public Creature creature(String id) { return id == null ? null : creatures.get(id); }
    public Techniques techniques() { return techniques; }
    public Calls calls() { return calls; }
    public Xp xp() { return xp; }
    public double waitFloor() { return waitFloor; }

    // ── the water ─────────────────────────────────────────────────

    /**
     * The water a bobber floats in: an underground water when there is no sky above it and it is below sea level, else the
     * first water listing its biome, else the fallback. Null when nothing is configured.
     */
    public Water waterAt(String biomeKey, boolean underground) {
        if (underground) for (Water w : waters) if (w.underground()) return w;
        String b = biomeKey == null ? "" : biomeKey.toLowerCase(Locale.ROOT);
        for (Water w : waters) if (w.biomes().contains(b)) return w;
        for (Water w : waters) if (w.fallback()) return w;
        return null;
    }

    // ── species ───────────────────────────────────────────────────

    /** The share of fish and junk bites that stay vanilla at this level: falls from level 1 to level 100. */
    public double vanillaShare(int level) {
        double t = Math.max(0, Math.min(1, (level - 1) / 99.0));
        return vanillaAt1 + (vanillaAt100 - vanillaAt1) * t;
    }

    /** The species this water can give at this level and moment. */
    public List<Species> eligible(Moment m, int level) {
        List<Species> out = new ArrayList<>();
        if (m == null) return out;
        for (Species s : species(m.water())) if (canBite(s, m, level)) out.add(s);
        return out;
    }

    /** True when {@code s} can bite at this moment and level: what {@link #eligible} and every check below agree on. */
    public static boolean canBite(Species s, Moment m, int level) {
        return m != null && s.water().equals(m.water()) && s.level() <= level && s.when().matches(m);
    }

    /** One requirement a species has, as the journal shows it (docs/design/FISHING_EQUIPMENT.md §3). */
    public enum Need { LEVEL, WATER, TIME, WEATHER, BIOME }

    /** Met, missing, or not known yet (no bobber in the water and no water in sight). */
    public enum CheckState { MET, MISSING, UNKNOWN }

    /** A requirement and where it stands: {@code need} says what it asks, {@code have} what there is now (may be empty). */
    public record Check(Need kind, CheckState state, String need, String have) {}

    /**
     * {@code s}'s requirements one by one, at {@code m} for a player at {@code level}. {@code m} null or with no water: the
     * place isn't known, so the water and biome checks are UNKNOWN; time and weather are known anywhere in a world, so
     * {@code m} still carries them. Uses exactly the conditions {@link #canBite} uses: every check MET is canBite.
     */
    public List<Check> checks(Species s, Moment m, int level) {
        List<Check> out = new ArrayList<>(5);
        out.add(new Check(Need.LEVEL, s.level() <= level ? CheckState.MET : CheckState.MISSING, "Fishing " + s.level(), "you: " + level));
        Water w = water(s.water());
        String wName = w == null ? s.water() : w.name();
        boolean placeKnown = m != null && m.water() != null;
        if (!placeKnown) out.add(new Check(Need.WATER, CheckState.UNKNOWN, wName, ""));
        else {
            Water here = water(m.water());
            out.add(new Check(Need.WATER, s.water().equals(m.water()) ? CheckState.MET : CheckState.MISSING, wName,
                    here == null ? m.water() : here.name()));
        }
        When when = s.when();
        if (when.time() != Time.ANY) {
            String need = when.time() == Time.NIGHT ? "Night only" : "Day only";
            out.add(m == null ? new Check(Need.TIME, CheckState.UNKNOWN, need, "")
                    : new Check(Need.TIME, when.timeOk(m.night()) ? CheckState.MET : CheckState.MISSING, need, m.night() ? "it is night" : "it is day"));
        }
        if (when.weather() != Weather.ANY) {
            String need = switch (when.weather()) {
                case RAIN -> "Rain";
                case THUNDER -> "A thunderstorm";
                case CLEAR -> "Clear weather";
                default -> "";
            };
            out.add(m == null ? new Check(Need.WEATHER, CheckState.UNKNOWN, need, "")
                    : new Check(Need.WEATHER, when.weatherOk(m.rain(), m.thunder()) ? CheckState.MET : CheckState.MISSING, need,
                    m.thunder() ? "a thunderstorm" : m.rain() ? "rain" : "clear"));
        }
        if (!when.biomes().isEmpty()) {
            String need = "In " + String.join(" or ", when.biomes().stream().map(b -> b.replace('_', ' ')).toList());
            out.add(!placeKnown || m.biome() == null ? new Check(Need.BIOME, CheckState.UNKNOWN, need, "")
                    : new Check(Need.BIOME, when.biomeOk(m.biome()) ? CheckState.MET : CheckState.MISSING, need, m.biome().replace('_', ' ')));
        }
        return out;
    }

    /** True when every check is MET (the same answer as {@link #canBite} for a known place). */
    public static boolean allMet(List<Check> checks) {
        for (Check c : checks) if (c.state() != CheckState.MET) return false;
        return true;
    }

    /**
     * What a fish or junk bite becomes: null to keep vanilla's catch, else a species. {@code keep} and {@code pick} are
     * uniform random numbers in [0, 1): the first decides vanilla or not, the second picks by weight.
     */
    public Species rollSpecies(Moment m, int level, double keep, double pick) {
        return rollSpecies(m, level, keep, pick, s -> 1.0);
    }

    /**
     * The same roll with each eligible species' weight multiplied by {@code factor} (Surveyor's Float, discovery and night
     * bait; docs/design/FISHING_EQUIPMENT.md §7). Only the pick among eligible species changes: the vanilla share and every
     * gate are the plain roll's, and a factor is a multiplier, so the rarity order among species it favours stays.
     */
    public Species rollSpecies(Moment m, int level, double keep, double pick, java.util.function.ToDoubleFunction<Species> factor) {
        List<Species> can = eligible(m, level);
        if (can.isEmpty() || keep < vanillaShare(level)) return null;
        double total = 0;
        double[] w = new double[can.size()];
        for (int i = 0; i < can.size(); i++) {
            double f = factor.applyAsDouble(can.get(i));
            w[i] = can.get(i).weight() * (Double.isFinite(f) && f > 0 ? f : 1.0);
            total += w[i];
        }
        if (!(total > 0)) return null;
        double at = pick * total;
        for (int i = 0; i < can.size(); i++) {
            at -= w[i];
            if (at < 0) return can.get(i);
        }
        return can.get(can.size() - 1);
    }

    /** A catch's weight: most are near the small end, a few near the top. Two decimals. */
    public static double kg(Species s, double u) {
        return kg(s, u, 2.5);
    }

    /**
     * A catch's weight with the curve {@code u^exponent} (2.5 by default; the Specimen Hook flattens it to favour heavier
     * fish). Always within the species' own range. Two decimals.
     */
    public static double kg(Species s, double u, double exponent) {
        double e = Double.isFinite(exponent) && exponent > 0 ? exponent : 2.5;
        double w = s.minKg() + (s.maxKg() - s.minKg()) * Math.pow(Math.max(0, Math.min(1, u)), e);
        return Math.max(s.minKg(), Math.min(s.maxKg(), Math.round(w * 100) / 100.0));
    }

    // ── treasure ─────────────────────────────────────────────────

    /** Cerberus treasure for a catch in open water from Treasure Sense on, by weight among what the level allows. */
    public Treasure rollTreasure(int level, double pick) {
        List<Treasure> can = new ArrayList<>();
        double total = 0;
        for (Treasure t : treasure) if (t.level() <= level) { can.add(t); total += t.weight(); }
        if (can.isEmpty() || !(total > 0)) return null;
        double at = pick * total;
        for (Treasure t : can) {
            at -= t.weight();
            if (at < 0) return t;
        }
        return can.get(can.size() - 1);
    }

    // ── waits and techniques ──────────────────────────────────────

    /** How much of the vanilla wait for a bite is left: lure speed and Quick Bite cut it, never below the floor. */
    public double waitFactor(double lureSpeed, int level) {
        double f = 1.0 - Math.max(0, Math.min(1, lureSpeed));
        if (level >= techniques.quickBite()) f *= techniques.quickBiteWait();
        return Math.max(waitFloor, Math.min(1.0, f));
    }

    public boolean treasureSense(int level) { return level >= techniques.treasureSense(); }
    public boolean elite(int level) { return level >= techniques.deepWaters(); }
    public boolean twinCatch(int level) { return level >= techniques.twinCatch(); }
    public boolean apex(int level) { return level >= techniques.apex(); }

    /** The technique lines a level-up from {@code from} to {@code to} opens (for the level-up message). */
    public List<String> openedBetween(int from, int to) {
        List<String> out = new ArrayList<>();
        if (!enabled()) return out;
        TreeMap<Integer, String> lines = new TreeMap<>();
        Techniques t = techniques;
        lines.put(t.creatures(), "Something Stirs: sea creatures can take your bait (Sneak and cast to call one)");
        lines.merge(t.quickBite(), "Quick Bite: fish bite " + Math.round((1 - t.quickBiteWait()) * 100) + "% sooner", (a, b) -> a + "; " + b);
        lines.merge(t.treasureSense(), "Treasure Sense: treasure in open water", (a, b) -> a + "; " + b);
        lines.merge(t.deepWaters(), "Deep Waters: the creatures you call come as elites", (a, b) -> a + "; " + b);
        lines.merge(t.twinCatch(), "Twin Catch: a Fortune catch can bring two extra fish", (a, b) -> a + "; " + b);
        lines.merge(t.apex(), "Apex: the apex of each water can take your bait", (a, b) -> a + "; " + b);
        for (Map.Entry<Integer, String> e : lines.entrySet()) if (e.getKey() > from && e.getKey() <= to) out.add(e.getValue());
        return out;
    }

    // ── creatures ─────────────────────────────────────────────────

    /**
     * Which banked lure an unchosen call spends: the first (in {@code banked}'s order) whose creature lives in {@code water},
     * else {@code fallback}. A call the player chose never comes here.
     */
    public String lureFor(java.util.Collection<String> banked, String water, String fallback) {
        if (water != null) for (String id : banked) {
            Creature c = creatures.get(id);
            if (c != null && c.waters().contains(water)) return id;
        }
        return fallback;
    }

    /** The regular (non-apex) creatures of a water a level allows. */
    public List<Creature> creaturesOf(String water, int level) {
        List<Creature> out = new ArrayList<>();
        if (level < techniques.creatures()) return out;
        for (Creature c : creatures.values()) if (!c.apex() && c.level() <= level && c.waters().contains(water)) out.add(c);
        return out;
    }

    /** One of {@link #creaturesOf}, by weight; null when none. */
    public Creature rollCreature(String water, int level, double pick) {
        List<Creature> can = creaturesOf(water, level);
        double total = 0;
        for (Creature c : can) total += c.weight();
        if (can.isEmpty() || !(total > 0)) return null;
        double at = pick * total;
        for (Creature c : can) {
            at -= c.weight();
            if (at < 0) return c;
        }
        return can.get(can.size() - 1);
    }

    /** The apex of a water a level allows (from Apex on), or null. */
    public Creature apexOf(String water, int level) {
        if (!apex(level)) return null;
        for (Creature c : creatures.values()) if (c.apex() && c.level() <= level && c.waters().contains(water)) return c;
        return null;
    }

    /**
     * A called creature's level: its owner's Fishing level with a little spread ({@code spread} in [-1, 1]), never below the
     * creature's own level nor the world's level there, at most {@code maxLevel}.
     */
    public int creatureLevel(Creature c, int fishingLevel, int worldLevel, double spread, int maxLevel) {
        int s = (int) Math.round(Math.max(-1, Math.min(1, spread)) * calls.levelSpread());
        int level = Math.max(Math.max(c.level(), worldLevel), fishingLevel + s);
        return Math.max(1, Math.min(Math.max(1, maxLevel), level));
    }

    // ── parsing ───────────────────────────────────────────────────

    /**
     * The {@code fishing:} section. {@code items} are resources.yml's custom items (species, treasure and drops must name
     * one, or a vanilla material for drops); {@code skills} maps a written skill name to the real one (null when unknown).
     * A bad entry is refused on its own and reported; the rest loads.
     */
    static FishingRules from(ConfigurationSection f, Map<String, GatheringCatalog.ItemDef> items,
                             java.util.function.UnaryOperator<String> skills, List<String> problems) {
        if (f == null) return NONE;
        String written = f.getString("skill", "Fishing");
        String skill = skills.apply(written.trim());
        if (skill == null) { problems.add("fishing: unknown skill " + written + " - fishing pays nothing"); return NONE; }

        List<Water> waters = new ArrayList<>();
        Set<String> waterIds = new LinkedHashSet<>();
        ConfigurationSection ws = f.getConfigurationSection("waters");
        if (ws != null) for (String id : ws.getKeys(false)) {
            ConfigurationSection w = ws.getConfigurationSection(id);
            if (w == null || !ID.matcher(id).matches()) { problems.add("fishing water " + id + ": not a section with a plain id (a-z, 0-9, _)"); continue; }
            Set<String> biomes = new LinkedHashSet<>();
            for (String b : w.getStringList("biomes")) biomes.add(b.trim().toLowerCase(Locale.ROOT).replace("minecraft:", ""));
            boolean under = w.getBoolean("underground", false), fallback = w.getBoolean("fallback", false);
            if (biomes.isEmpty() && !under && !fallback) { problems.add("fishing water " + id + ": no biomes (or underground / fallback)"); continue; }
            waters.add(new Water(id, w.getString("name", id), Collections.unmodifiableSet(biomes), under, fallback));
            waterIds.add(id);
        }

        Map<String, List<Species>> species = new LinkedHashMap<>();
        Set<String> seen = new java.util.HashSet<>();
        ConfigurationSection ss = f.getConfigurationSection("species");
        if (ss != null) for (String id : ss.getKeys(false)) {
            ConfigurationSection s = ss.getConfigurationSection(id);
            List<String> why = new ArrayList<>();
            if (s == null) { problems.add("fishing species " + id + ": not a section"); continue; }
            if (!ID.matcher(id).matches()) why.add("id must be plain (a-z, 0-9, _)");
            String water = s.getString("water");
            if (water == null || !waterIds.contains(water)) why.add("unknown water " + water);
            String item = s.getString("item", id);
            if (items.get(item) == null) why.add("unknown item " + item + " (items:)");
            Rarity r = Rarity.parse(s.getString("rarity", "common"));
            if (r == null) why.add("unknown rarity " + s.getString("rarity"));
            int level = s.getInt("level", 1);
            if (level < 1 || level > 1000) why.add("level " + level + " outside 1-1000");
            double weight = s.getDouble("weight", -1);
            List<Double> kg = s.getDoubleList("kg");
            double minKg = kg.size() == 2 ? kg.get(0) : 0.1, maxKg = kg.size() == 2 ? kg.get(1) : 1.0;
            if (!(minKg > 0 && maxKg >= minKg && maxKg < 10_000)) why.add("kg " + kg + " must be [smallest, largest] above 0");
            When when = when(s, id, problems);
            if (!why.isEmpty() || when == null) { if (!why.isEmpty()) problems.add("fishing species " + id + ": " + String.join("; ", why)); continue; }
            if (!seen.add(id)) { problems.add("fishing species " + id + ": listed twice"); continue; }
            if (!(weight > 0)) weight = defaultWeight(r);
            double xp = s.getDouble("xp", -1);
            species.computeIfAbsent(water, k -> new ArrayList<>()).add(new Species(id, water, item, r, level, weight, when, minKg, maxKg,
                    xp >= 0 && xp <= 100_000 ? xp : -1));
        }

        List<Treasure> treasure = new ArrayList<>();
        ConfigurationSection ts = f.getConfigurationSection("treasure");
        if (ts != null) for (String id : ts.getKeys(false)) {
            ConfigurationSection t = ts.getConfigurationSection(id);
            if (t == null || !ID.matcher(id).matches()) { problems.add("fishing treasure " + id + ": not a section with a plain id"); continue; }
            String item = t.getString("item", id);
            Rarity r = Rarity.parse(t.getString("rarity", "common"));
            if (items.get(item) == null || r == null) { problems.add("fishing treasure " + id + ": unknown item " + item + " or rarity " + t.getString("rarity")); continue; }
            double weight = t.getDouble("weight", -1);
            double xp = t.getDouble("xp", -1);
            treasure.add(new Treasure(id, item, r, Math.max(1, t.getInt("level", 1)), weight > 0 ? weight : defaultWeight(r),
                    xp >= 0 && xp <= 100_000 ? xp : -1));
        }

        Map<String, Creature> creatures = new LinkedHashMap<>();
        ConfigurationSection cs = f.getConfigurationSection("creatures");
        if (cs != null) for (String id : cs.getKeys(false)) {
            ConfigurationSection c = cs.getConfigurationSection(id);
            List<String> why = new ArrayList<>();
            if (c == null) { problems.add("fishing creature " + id + ": not a section"); continue; }
            if (!ID.matcher(id).matches()) why.add("id must be plain (a-z, 0-9, _)");
            String entity = c.getString("entity", "").trim().toUpperCase(Locale.ROOT);
            if (!LIVING.contains(entity)) why.add("entity " + entity + " is not one of " + LIVING);
            Set<String> cw = new LinkedHashSet<>();
            for (String w : c.getStringList("waters")) {
                if (waterIds.contains(w.trim())) cw.add(w.trim());
                else why.add("unknown water " + w);
            }
            if (cw.isEmpty() && why.isEmpty()) why.add("no waters");
            String tier = c.getString("tier", "COMMON").trim().toUpperCase(Locale.ROOT);
            if (!TIERS.contains(tier)) why.add("unknown tier " + tier);
            int level = c.getInt("level", 1);
            if (level < 1 || level > 1000) why.add("level " + level + " outside 1-1000");
            double health = c.getDouble("health", 1.0), scale = c.getDouble("scale", 1.0), xpMult = c.getDouble("xp-multiplier", 1.0);
            if (!(health > 0 && health <= 1000)) why.add("health " + health + " outside (0, 1000]");
            if (!(scale >= 0.25 && scale <= 4)) why.add("scale " + scale + " outside 0.25-4");
            if (!(xpMult >= 0 && xpMult <= 1000)) why.add("xp-multiplier " + xpMult + " outside 0-1000");
            List<Drop> drops = new ArrayList<>();
            for (Map<?, ?> m : c.getMapList("drops")) {
                String item = String.valueOf(m.get("item"));
                boolean custom = items.containsKey(item);
                if (!custom && GatheringCatalog.material(item) == null) { why.add("drop " + item + " is neither an item nor a material"); continue; }
                double chance = m.get("chance") instanceof Number n ? n.doubleValue() : 1.0;
                int min = m.get("min") instanceof Number n ? n.intValue() : 1, max = m.get("max") instanceof Number n ? n.intValue() : min;
                if (!(chance > 0 && chance <= 1) || min < 1 || max < min || max > 64) { why.add("drop " + item + ": chance in (0, 1], 1 <= min <= max <= 64"); continue; }
                drops.add(new Drop(item, chance, min, max));
            }
            Map<String, String> equipment = new LinkedHashMap<>();
            ConfigurationSection eq = c.getConfigurationSection("equipment");
            if (eq != null) for (String slot : eq.getKeys(false)) {
                String mat = eq.getString(slot, "");
                if (!Set.of("hand", "off-hand", "helmet").contains(slot) || GatheringCatalog.material(mat) == null) why.add("equipment " + slot + ": " + mat);
                else equipment.put(slot, mat.trim().toUpperCase(Locale.ROOT));
            }
            Rarity rarity = Rarity.parse(c.getString("rarity", c.getBoolean("apex", false) ? "legendary" : "rare"));
            if (rarity == null) why.add("unknown rarity " + c.getString("rarity"));
            if (!why.isEmpty()) { problems.add("fishing creature " + id + ": " + String.join("; ", why)); continue; }
            double weight = c.getDouble("weight", 10);
            double share = c.getDouble("fishing-share", 1.0);
            double disc = c.getDouble("discovery-xp", 0);
            creatures.put(id, new Creature(id, c.getString("name", id), entity, Collections.unmodifiableSet(cw), level, tier, xpMult,
                    health, scale, weight > 0 ? weight : 10, c.getBoolean("apex", false), Collections.unmodifiableMap(equipment),
                    List.copyOf(drops), Math.max(0, Math.min(10, share)), Math.max(0, Math.min(100_000, disc)), rarity));
        }

        Techniques td = DEFAULT_TECHNIQUES;
        ConfigurationSection te = f.getConfigurationSection("techniques");
        Techniques tech = te == null ? td : new Techniques(
                lvl(te, "something-stirs", td.creatures()), lvl(te, "quick-bite.at", td.quickBite()),
                frac(te, "quick-bite.wait", td.quickBiteWait(), 0.1, 1.0, problems),
                lvl(te, "treasure-sense", td.treasureSense()), lvl(te, "deep-waters.at", td.deepWaters()),
                frac(te, "deep-waters.health", td.eliteHealth(), 1.0, 10.0, problems), frac(te, "deep-waters.xp", td.eliteXp(), 1.0, 10.0, problems),
                (int) frac(te, "deep-waters.drop-rolls", td.eliteDropRolls(), 0, 5, problems),
                lvl(te, "twin-catch", td.twinCatch()), lvl(te, "apex.at", td.apex()), frac(te, "apex.chance", td.apexChance(), 0, 0.1, problems));

        Calls cd = DEFAULT_CALLS;
        ConfigurationSection ca = f.getConfigurationSection("calls");
        Calls calls = ca == null ? cd : new Calls(
                (int) frac(ca, "per-player", cd.perPlayer(), 1, 5, problems), (int) frac(ca, "server", cd.server(), 1, 500, problems),
                frac(ca, "leash-blocks", cd.leashBlocks(), 8, 128, problems), (int) frac(ca, "leash-seconds", cd.leashSeconds(), 5, 600, problems),
                (int) frac(ca, "fight-seconds", cd.fightSeconds(), 30, 3600, problems), (int) frac(ca, "level-spread", cd.levelSpread(), 0, 20, problems));

        Xp xd = DEFAULT_XP;
        ConfigurationSection x = f.getConfigurationSection("xp");
        Map<Rarity, Double> byRarity = new EnumMap<>(Rarity.class), disc = new EnumMap<>(Rarity.class);
        for (Rarity r : Rarity.values()) {
            String k = r.name().toLowerCase(Locale.ROOT);
            byRarity.put(r, x == null ? xd.of(r) : frac(x, "rarity." + k, xd.of(r), 0, 100_000, problems));
            disc.put(r, x == null ? xd.discovery(r) : frac(x, "discovery." + k, xd.discovery(r), 0, 100_000, problems));
        }
        Xp xp = new Xp(x == null ? xd.junk() : frac(x, "junk", xd.junk(), 0, 100_000, problems),
                x == null ? xd.fish() : frac(x, "fish", xd.fish(), 0, 100_000, problems),
                x == null ? xd.treasure() : frac(x, "treasure", xd.treasure(), 0, 100_000, problems),
                Collections.unmodifiableMap(byRarity), Collections.unmodifiableMap(disc));

        double at1 = frac(f, "vanilla-share.at-level-1", 0.7, 0, 1, problems);
        double at100 = frac(f, "vanilla-share.at-level-100", 0.35, 0, 1, problems);
        double floor = frac(f, "wait-floor", 0.4, 0.05, 1, problems);
        return new FishingRules(skill, waters, species, treasure, creatures, tech, calls, xp, at1, at100, floor);
    }

    /** Mobs a creature may be: aquatic or water-friendly hostiles that fight on their own. */
    static final Set<String> LIVING = Set.of("DROWNED", "GUARDIAN", "ELDER_GUARDIAN", "BOGGED", "STRAY", "SKELETON", "ZOMBIE",
            "HUSK", "WITCH", "SLIME", "PUFFERFISH", "SPIDER", "CAVE_SPIDER", "PILLAGER", "VINDICATOR", "WITHER_SKELETON");
    static final Set<String> TIERS = Set.of("WEAK", "COMMON", "UNCOMMON", "RARE", "EPIC", "LEGENDARY", "PERFECT", "MYTHIC", "GODLY", "PRIMORDIAL");

    static double defaultWeight(Rarity r) {
        return switch (r) { case COMMON -> 60; case UNCOMMON -> 25; case RARE -> 10; case EPIC -> 4; case LEGENDARY -> 1; };
    }

    private static When when(ConfigurationSection s, String id, List<String> problems) {
        Time time;
        Weather weather;
        try {
            time = Time.valueOf(s.getString("time", "any").trim().toUpperCase(Locale.ROOT));
            weather = Weather.valueOf(s.getString("weather", "any").trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            problems.add("fishing species " + id + ": time is any, day or night; weather any, clear, rain or thunder");
            return null;
        }
        Set<String> biomes = new LinkedHashSet<>();
        for (String b : s.getStringList("biomes")) biomes.add(b.trim().toLowerCase(Locale.ROOT).replace("minecraft:", ""));
        return time == Time.ANY && weather == Weather.ANY && biomes.isEmpty() ? When.ALWAYS
                : new When(time, weather, Collections.unmodifiableSet(biomes));
    }

    private static int lvl(ConfigurationSection s, String key, int dflt) {
        int v = s.getInt(key, dflt);
        return v >= 1 && v <= 1000 ? v : dflt;
    }

    private static double frac(ConfigurationSection s, String key, double dflt, double min, double max, List<String> problems) {
        if (!s.contains(key)) return dflt;
        double v = s.getDouble(key, Double.NaN);
        if (!(v >= min && v <= max)) {
            problems.add("fishing " + key + ": " + s.get(key) + " outside " + min + "-" + max + " - using " + dflt);
            return dflt;
        }
        return v;
    }
}
