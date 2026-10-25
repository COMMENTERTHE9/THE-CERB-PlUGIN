package Classes;

import Attributes.Attribute;
import Attributes.Stat;
import org.bukkit.configuration.ConfigurationSection;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Every class and ability, read from classes.yml and abilities.yml (docs/design/CLASSES.md §5.1).
 * Validation enforces the balance bounds of CLASSES.md §8: an ability that breaks them is refused
 * with a reason (logged by the caller), so a typo can't create an infinite or limit-breaking
 * ability. Immutable once built; a reload builds a new catalog.
 */
public final class ClassCatalog {

    /** Most damage a cast may do, in basic hits (all damage effects x hits together): short cooldowns. */
    public static final double MAX_COEFFICIENT_SHORT = 3.0;
    /** ...and for cooldowns of {@link #LONG_COOLDOWN_MS} or more. */
    public static final double MAX_COEFFICIENT_LONG = 6.0;
    public static final long LONG_COOLDOWN_MS = 20_000;
    /** No active may come back faster than this. */
    public static final long MIN_COOLDOWN_MS = 1_000;
    /** Most stacks a class resource (Momentum) holds; the power check counts every damage bonus per stack at this many. */
    public static final int MAX_STACKS = 5;

    private final Map<String, ClassDef> classes;
    private final Map<String, AbilityDef> abilities;
    private final Map<String, Long> groupMs;
    private final List<String> problems;
    /** class or ability id -> the item its menu tile shows (the optional "icon:" key; a material name). */
    private final Map<String, String> icons;
    /** The ability keys' server settings ({@code controls:} in classes.yml). */
    private final Controls.Settings controls;

    private ClassCatalog(Map<String, ClassDef> classes, Map<String, AbilityDef> abilities, Map<String, Long> groupMs,
                         List<String> problems, Map<String, String> icons, Controls.Settings controls) {
        this.classes = Collections.unmodifiableMap(classes);
        this.abilities = Collections.unmodifiableMap(abilities);
        this.groupMs = Collections.unmodifiableMap(groupMs);
        this.problems = List.copyOf(problems);
        this.icons = Collections.unmodifiableMap(icons);
        this.controls = controls;
    }

    public static ClassCatalog empty() { return new ClassCatalog(Map.of(), Map.of(), Map.of(), List.of(), Map.of(), Controls.Settings.DEFAULTS); }

    /** The ability keys' server settings: default scheme, combo timing, ability bar. */
    public Controls.Settings controls() { return controls; }

    /** The configured menu icon (a material name) of a class or ability, or null for the default. */
    public String icon(String id) { return id == null ? null : icons.get(id); }

    public Map<String, ClassDef> classes() { return classes; }
    public Map<String, AbilityDef> abilities() { return abilities; }
    /** Shared cooldown groups and how long each locks (e.g. "movement" -> 2000). */
    public Map<String, Long> groupMs() { return groupMs; }
    /** Why entries were refused while loading (empty when everything loaded). */
    public List<String> problems() { return problems; }

    public ClassDef classDef(String id) { return id == null ? null : classes.get(id); }
    public AbilityDef ability(String id) { return id == null ? null : abilities.get(id); }

    /**
     * Build from the two files' root sections ({@code classes:} / {@code groups:} in classes.yml,
     * {@code abilities:} in abilities.yml). Never throws for bad content: refused entries are listed
     * in {@link #problems()}.
     */
    public static ClassCatalog from(ConfigurationSection classesYml, ConfigurationSection abilitiesYml) {
        List<String> problems = new ArrayList<>();
        Map<String, Long> groups = new LinkedHashMap<>();
        ConfigurationSection g = classesYml == null ? null : classesYml.getConfigurationSection("groups");
        if (g != null) {
            for (String k : g.getKeys(false)) {
                try {
                    groups.put(k, Math.max(0, millis(g.get(k), 0)));
                } catch (IllegalArgumentException ex) {
                    problems.add("group " + k + ": " + ex.getMessage());
                }
            }
        }

        Map<String, AbilityDef> abilities = new LinkedHashMap<>();
        Map<String, String> icons = new java.util.HashMap<>();
        ConfigurationSection as = abilitiesYml == null ? null : abilitiesYml.getConfigurationSection("abilities");
        if (as != null) {
            for (String id : as.getKeys(false)) {
                ConfigurationSection a = as.getConfigurationSection(id);
                if (a == null) continue;
                if (a.getString("icon") != null) icons.put(id, a.getString("icon"));
                List<String> why = new ArrayList<>();
                AbilityDef def;
                try {
                    def = parseAbility(id, a, why);
                } catch (RuntimeException ex) { // a wrong type anywhere in the entry: refuse it, never fail the whole load
                    def = null;
                    why.add("unreadable (" + ex.getMessage() + ")");
                }
                if (def != null && why.isEmpty()) abilities.put(id, def);
                else problems.add("ability " + id + ": " + String.join("; ", why));
            }
        }

        Map<String, ClassDef> classes = new LinkedHashMap<>();
        ConfigurationSection cs = classesYml == null ? null : classesYml.getConfigurationSection("classes");
        if (cs != null) {
            for (String id : cs.getKeys(false)) {
                ConfigurationSection c = cs.getConfigurationSection(id);
                if (c == null) continue;
                if (c.getString("icon") != null) icons.put(id, c.getString("icon"));
                List<String> own = new ArrayList<>();
                for (AbilityDef a : abilities.values()) if (a.classId().equals(id)) own.add(a.id());
                classes.put(id, new ClassDef(id, c.getString("name", id), c.getString("role", ""),
                        c.getString("signature", ""), levels(c.getConfigurationSection("entry")),
                        levels(c.getConfigurationSection("entry-any")), List.copyOf(own)));
            }
        }
        // An ability must belong to a class that exists.
        abilities.values().removeIf(a -> {
            if (classes.containsKey(a.classId())) return false;
            problems.add("ability " + a.id() + ": unknown class " + a.classId());
            return true;
        });
        Controls.Settings controls = Controls.Settings.from(classesYml == null ? null : classesYml.getConfigurationSection("controls"));
        return new ClassCatalog(classes, abilities, groups, problems, icons, controls);
    }

    private static AbilityDef parseAbility(String id, ConfigurationSection a, List<String> why) {
        String classId = a.getString("class");
        if (classId == null || classId.isBlank()) why.add("no class");
        AbilityDef.Kind kind;
        try {
            kind = AbilityDef.Kind.valueOf(a.getString("kind", "active").toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            why.add("unknown kind " + a.getString("kind"));
            return null;
        }
        int rank = a.getInt("unlock-rank", 1);
        if (rank < 1 || rank > ClassRanks.MAX_RANK) why.add("unlock-rank " + rank + " outside 1-" + ClassRanks.MAX_RANK);
        Set<WeaponFamily> weapons = EnumSet.noneOf(WeaponFamily.class);
        for (String w : a.getStringList("weapons")) {
            WeaponFamily f = WeaponFamily.parse(w);
            if (f == null) why.add("unknown weapon family " + w); else weapons.add(f);
        }
        double essence = a.getDouble("essence", 0);
        if (!(essence >= 0 && essence <= 10_000)) why.add("essence cost " + essence + " outside 0-10000");
        long cooldown = millis(a.get("cooldown"), 0);
        List<EffectSpec> effects = new ArrayList<>();
        for (Map<?, ?> m : a.getMapList("effects")) {
            EffectSpec e = parseEffect(m, why);
            if (e != null) effects.add(e);
        }
        List<AbilityDef.Passive> passives = new ArrayList<>();
        for (Map<?, ?> m : a.getMapList("passives")) {
            AbilityDef.Passive p = parsePassive(m, why);
            if (p != null) passives.add(p);
        }
        if (kind == AbilityDef.Kind.ACTIVE) {
            if (effects.isEmpty()) why.add("an active needs effects");
            if (cooldown < MIN_COOLDOWN_MS) why.add("cooldown under the " + MIN_COOLDOWN_MS + " ms floor");
            double total = 0;
            for (EffectSpec e : effects) if (e instanceof EffectSpec.Damage d) total += (d.coefficient() + d.perMomentum() * MAX_STACKS) * d.hits();
            for (EffectSpec e : effects) if (e instanceof EffectSpec.Counter c) {
                total += c.coefficient();
                if (c.successCooldownMs() > cooldown) why.add("counter success-cooldown longer than the cooldown");
            }
            double cap = cooldown >= LONG_COOLDOWN_MS ? MAX_COEFFICIENT_LONG : MAX_COEFFICIENT_SHORT;
            if (total > cap + 1e-9) why.add(String.format(Locale.ROOT, "damage %.2fx a hit over the %.1fx cap for its cooldown", total, cap));
        } else if (passives.isEmpty()) {
            why.add("a passive or keystone needs passives");
        }
        return new AbilityDef(id, classId == null ? "" : classId, a.getString("name", id), a.getString("description", ""),
                kind, rank, Collections.unmodifiableSet(weapons), essence, cooldown,
                Collections.unmodifiableSet(new LinkedHashSet<>(a.getStringList("groups"))),
                Collections.unmodifiableSet(new LinkedHashSet<>(a.getStringList("exclusive"))),
                List.copyOf(effects), List.copyOf(passives));
    }

    private static EffectSpec parseEffect(Map<?, ?> m, List<String> why) {
        if (m.size() != 1) { why.add("an effect has exactly one verb"); return null; }
        Map.Entry<?, ?> en = m.entrySet().iterator().next();
        String verb = String.valueOf(en.getKey()).toLowerCase(Locale.ROOT);
        Map<?, ?> p = en.getValue() instanceof Map<?, ?> mm ? mm : Map.of();
        try {
            return switch (verb) {
                case "damage" -> {
                    EffectSpec.Shape shape = EffectSpec.Shape.valueOf(str(p, "shape", "strike").toUpperCase(Locale.ROOT));
                    double range = bounded(num(p, "range", 3.5), 0.5, 8, "damage range", why);
                    double angle = bounded(num(p, "angle", 90), 10, 360, "damage angle", why);
                    double coef = bounded(num(p, "coefficient", 1.0), 0, MAX_COEFFICIENT_LONG, "damage coefficient", why);
                    EffectSpec.Scale scale = EffectSpec.Scale.valueOf(str(p, "scale", "power").toUpperCase(Locale.ROOT));
                    int targets = (int) bounded(num(p, "max-targets", 1), 1, 12, "max-targets", why);
                    int hits = (int) bounded(num(p, "hits", 1), 1, 5, "hits", why);
                    int interval = (int) bounded(num(p, "interval-ticks", 0), 0, 10, "interval-ticks", why);
                    double perMomentum = bounded(num(p, "per-momentum", 0), 0, 0.5, "per-momentum", why);
                    yield new EffectSpec.Damage(shape, range, angle, coef, scale, targets, hits, interval, str(p, "element", null), perMomentum);
                }
                case "dash" -> new EffectSpec.Dash(bounded(num(p, "distance", 6), 1, 16, "dash distance", why),
                        bounded(num(p, "stun-seconds", 0), 0, 3, "stun-seconds", why));
                case "blink" -> new EffectSpec.Blink(bounded(num(p, "distance", 6), 1, 16, "blink distance", why));
                case "status" -> new EffectSpec.Status(str(p, "type", "slowness"),
                        bounded(num(p, "seconds", 2), 0.1, 10, "status seconds", why),
                        (int) bounded(num(p, "amplifier", 0), 0, 4, "status amplifier", why),
                        EffectSpec.Shape.valueOf(str(p, "shape", "circle").toUpperCase(Locale.ROOT)),
                        bounded(num(p, "range", 4), 0.5, 8, "status range", why),
                        (int) bounded(num(p, "max-targets", 8), 1, 12, "status max-targets", why));
                case "buff" -> {
                    Attribute attr = attribute(str(p, "attribute", null));
                    Stat stat = Stat.parse(str(p, "stat", null));
                    if ((attr == null) == (stat == null)) { why.add("a buff names exactly one attribute or stat"); yield null; }
                    double amount = attr != null ? bounded(num(p, "amount", 0), -50, 50, "buff points", why)
                            : bounded(num(p, "amount", 0), -0.5, 0.5, "buff fraction", why);
                    yield new EffectSpec.Buff(attr, stat, amount, bounded(num(p, "seconds", 8), 1, 30, "buff seconds", why));
                }
                case "resource" -> new EffectSpec.Resource(str(p, "kind", "momentum"), (int) bounded(num(p, "delta", 1), -10, 10, "resource delta", why),
                        (int) bounded(num(p, "max", MAX_STACKS), 1, MAX_STACKS, "resource max", why), bool(p, "per-target"), bool(p, "spend-all"));
                case "counter" -> new EffectSpec.Counter((long) bounded(millis(p.get("window"), 600), 100, 2_000, "counter window (ms)", why),
                        bounded(num(p, "coefficient", 2.0), 0, MAX_COEFFICIENT_SHORT, "counter coefficient", why),
                        (long) bounded(millis(p.get("success-cooldown"), 1_500), 0, 60_000, "counter success-cooldown (ms)", why));
                case "empower" -> new EffectSpec.Empower(bounded(num(p, "seconds", 3), 0.5, 10, "empower seconds", why));
                case "spell" -> new EffectSpec.Spell(str(p, "name", "Fireball"));
                default -> { why.add("unknown verb " + verb); yield null; }
            };
        } catch (RuntimeException ex) { // a wrong type or value in the file: refuse this effect, never fail the whole load
            why.add("bad " + verb + ": " + ex.getMessage());
            return null;
        }
    }

    private static AbilityDef.Passive parsePassive(Map<?, ?> m, List<String> why) {
        Attribute attr = attribute(m.get("attribute") == null ? null : String.valueOf(m.get("attribute")));
        Stat stat = Stat.parse(m.get("stat") == null ? null : String.valueOf(m.get("stat")));
        if ((attr == null) == (stat == null)) { why.add("a passive names exactly one attribute or stat"); return null; }
        double amount = num(m, "amount", 0);
        amount = attr != null ? bounded(amount, -50, 50, "passive points", why) : bounded(amount, -0.5, 0.5, "passive fraction", why);
        return new AbilityDef.Passive(attr, stat, amount);
    }

    // ── small parsers ───────────────────────────────────────────

    private static Map<String, Integer> levels(ConfigurationSection s) {
        Map<String, Integer> out = new LinkedHashMap<>();
        if (s != null) for (String skill : s.getKeys(false)) out.put(skill, Math.max(0, s.getInt(skill)));
        return Collections.unmodifiableMap(out);
    }

    private static Attribute attribute(String s) {
        if (s == null || s.isBlank()) return null;
        try { return Attribute.valueOf(s.trim().toUpperCase(Locale.ROOT)); } catch (IllegalArgumentException e) { return null; }
    }

    /**
     * "4s", "1500ms", "1.5s", "2m", "1h" or a number of milliseconds ({@code dflt} when absent).
     * Anything else is refused with an IllegalArgumentException (reported as a problem), never
     * silently replaced by the default.
     */
    static long millis(Object v, long dflt) {
        if (v == null) return dflt;
        if (v instanceof Number n) return n.longValue();
        String s = String.valueOf(v).trim().toLowerCase(Locale.ROOT);
        try {
            if (s.endsWith("ms")) return Math.round(Double.parseDouble(s.substring(0, s.length() - 2)));
            if (s.endsWith("s")) return Math.round(Double.parseDouble(s.substring(0, s.length() - 1)) * 1000);
            if (s.endsWith("m")) return Math.round(Double.parseDouble(s.substring(0, s.length() - 1)) * 60_000);
            if (s.endsWith("h")) return Math.round(Double.parseDouble(s.substring(0, s.length() - 1)) * 3_600_000);
            return Math.round(Double.parseDouble(s));
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("not a duration: " + v);
        }
    }

    private static double num(Map<?, ?> m, String key, double dflt) {
        Object v = m.get(key);
        if (v instanceof Number n) return n.doubleValue();
        if (v == null) return dflt;
        try { return Double.parseDouble(String.valueOf(v)); } catch (NumberFormatException e) { return Double.NaN; }
    }

    private static String str(Map<?, ?> m, String key, String dflt) {
        Object v = m.get(key);
        return v == null ? dflt : String.valueOf(v);
    }

    private static boolean bool(Map<?, ?> m, String key) {
        return Boolean.parseBoolean(String.valueOf(m.get(key)));
    }

    private static double bounded(double v, double lo, double hi, String what, List<String> why) {
        if (!Double.isFinite(v) || v < lo || v > hi) {
            why.add(what + " " + v + " outside " + lo + "-" + hi);
            return Double.isFinite(v) ? Math.max(lo, Math.min(hi, v)) : lo;
        }
        return v;
    }
}
