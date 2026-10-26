package Classes;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * One player's classes as saved (docs/design/CLASSES.md §5.9): learned classes with rank and XP,
 * loadouts, which one is active, and long cooldowns. Stored as a small YAML document in
 * {@code player_classes.data}. Every load re-validates it against the current catalog (stored
 * data is never trusted): unknown classes and abilities are dropped, ranks and XP clamped, illegal
 * slots emptied. Each repair is reported so it can be logged.
 *
 * <p>Owned by the player's region thread. Runtime-only fields (Momentum, counter window...) are
 * never saved.
 */
public final class ClassProfile {

    public static final int VERSION = 1;
    public static final int BASE_LOADOUTS = 3;

    /** A learned class: rank 1-50 and the XP into it. */
    public record Progress(int rank, long xp) {}

    private final Map<String, Progress> classes = new LinkedHashMap<>();
    /**
     * Saved progress in classes the current catalog doesn't have (renamed or removed in
     * classes.yml): never used, but written back unchanged, so a config change can't erase it.
     */
    private final Map<String, Progress> dormant = new LinkedHashMap<>();
    private final List<Loadout> loadouts = new ArrayList<>();
    private int active;
    private final CooldownBook cooldowns = new CooldownBook();
    /** The player's own control scheme and ability-bar mode; null = the server's default. Saved. */
    private Controls.Scheme controls;
    private Controls.BarMode bar;

    // ── runtime only ────────────────────────────────────────────
    int momentum;
    long momentumAt;
    long counterUntil;
    double counterCoefficient;
    String counterAbility;
    long counterSuccessMs;
    long empowerUntil;
    long lastSwitchAt;
    /** class id -> when its abilities were last used (kill XP goes to the classes you fought with). */
    final Map<String, Long> lastUse = new HashMap<>();
    /** Stamp of the last snapshot taken for saving; only grows (see {@link #nextStamp}). */
    private long saveStamp;

    public ClassProfile() {
        for (int i = 0; i < BASE_LOADOUTS; i++) loadouts.add(Loadout.empty("Loadout " + (i + 1)));
    }

    // ── progress ────────────────────────────────────────────────

    public Map<String, Progress> classes() { return Collections.unmodifiableMap(classes); }

    public boolean knows(String classId) { return classes.containsKey(classId); }

    public int rank(String classId) {
        Progress p = classes.get(classId);
        return p == null ? 0 : p.rank();
    }

    /** Learn a class at rank 1 (no-op if already learned). Returns true if it was new. */
    public boolean learn(String classId) {
        return classes.putIfAbsent(classId, new Progress(1, 0)) == null;
    }

    /** Add class XP; returns the result (ranks gained) or null when the class isn't learned. */
    public ClassRanks.Result award(String classId, double xp) {
        Progress p = classes.get(classId);
        if (p == null) return null;
        ClassRanks.Result r = ClassRanks.award(p.rank(), p.xp(), xp);
        classes.put(classId, new Progress(r.rank(), r.xp()));
        return r;
    }

    /** Admin/test helper: an exact rank with 0 XP into it. */
    public void setRank(String classId, int rank) {
        classes.put(classId, new Progress(Math.max(1, Math.min(ClassRanks.MAX_RANK, rank)), 0));
    }

    /**
     * True when the ability can be used: its class is learned at its unlock rank or above, and every other requirement
     * ({@link AbilityDef#requires}) is met. A rank alone never opens an ability that needs more.
     */
    public boolean unlocked(AbilityDef a) {
        if (!rankReached(a)) return false;
        if (a.requires().isEmpty()) return true;
        java.util.function.ToIntFunction<String> level = skillLevels;
        if (level == null) return false;
        for (Map.Entry<String, Integer> r : a.requires().entrySet()) if (level.applyAsInt(r.getKey()) < r.getValue()) return false;
        return true;
    }

    /** True when the ability's class is learned at its unlock rank or above (its other requirements aside). */
    public boolean rankReached(AbilityDef a) {
        return a != null && rank(a.classId()) >= a.unlockRank();
    }

    /** The requirements besides its rank that are not met yet, as skill (internal name) to level; all of them while levels are unknown. */
    public Map<String, Integer> missing(AbilityDef a) {
        if (a == null || a.requires().isEmpty()) return Map.of();
        java.util.function.ToIntFunction<String> level = skillLevels;
        Map<String, Integer> out = new LinkedHashMap<>();
        for (Map.Entry<String, Integer> r : a.requires().entrySet()) {
            if (level == null || level.applyAsInt(r.getKey()) < r.getValue()) out.put(r.getKey(), r.getValue());
        }
        return out;
    }

    /** The player's skill levels, by internal name (set when the profile loads; never saved). */
    private volatile java.util.function.ToIntFunction<String> skillLevels;

    public void useSkillLevels(java.util.function.ToIntFunction<String> skillLevels) { this.skillLevels = skillLevels; }

    // ── loadouts ────────────────────────────────────────────────

    public List<Loadout> loadouts() { return Collections.unmodifiableList(loadouts); }

    public int activeIndex() { return active; }

    public Loadout activeLoadout() { return loadouts.get(active); }

    void setLoadout(int index, Loadout l) { loadouts.set(index, l); }

    void setActive(int index) { active = index; }

    public CooldownBook cooldowns() { return cooldowns; }

    // ── preferences ─────────────────────────────────────────────

    /** The player's chosen control scheme, or null for the server default. */
    public Controls.Scheme controls() { return controls; }

    /** The player's ability-bar mode, or null for the server default. */
    public Controls.BarMode bar() { return bar; }

    void setControls(Controls.Scheme scheme) { controls = scheme; }

    void setBar(Controls.BarMode mode) { bar = mode; }

    // ── saving ──────────────────────────────────────────────────

    /**
     * The stamp for a new snapshot: the clock, but always above the last one and above what was
     * stored when the profile loaded ({@link #stampAfter}), so later snapshots always win in the
     * database even if the clock steps back.
     */
    public long nextStamp(long now) {
        saveStamp = Math.max(now, saveStamp + 1);
        return saveStamp;
    }

    /** The stored document carried {@code stored}: every later snapshot must be stamped above it. */
    public void stampAfter(long stored) { saveStamp = Math.max(saveStamp, stored); }

    /**
     * Everything that gets saved, copied at one moment (cheap: a few small maps; loadouts are
     * immutable). Taken on the player's thread; turning it into text ({@link Snapshot#toYaml}) can
     * then happen anywhere, off the tick.
     */
    public record Snapshot(Map<String, Progress> classes, List<Loadout> loadouts, int active, Map<String, Long> cooldowns,
                           Controls.Scheme controls, Controls.BarMode bar) {

        /** The saved form (YAML). Pure: safe on any thread. */
        public String toYaml() {
            YamlConfiguration y = new YamlConfiguration();
            y.set("version", VERSION);
            for (Map.Entry<String, Progress> e : classes.entrySet()) {
                y.set("classes." + e.getKey() + ".rank", e.getValue().rank());
                y.set("classes." + e.getKey() + ".xp", e.getValue().xp());
            }
            for (int i = 0; i < loadouts.size(); i++) {
                Loadout l = loadouts.get(i);
                String k = "loadouts." + i + ".";
                y.set(k + "name", l.name());
                y.set(k + "actives", blanks(l.actives()));
                y.set(k + "passives", blanks(l.passives()));
                y.set(k + "keystone", l.keystone() == null ? "" : l.keystone());
            }
            y.set("active", active);
            cooldowns.forEach((key, end) -> y.set("cooldowns." + key.replace(':', '~'), end));
            if (controls != null) y.set("controls", controls.id());
            if (bar != null) y.set("bar", bar.id());
            return y.saveToString();
        }
    }

    /** What would be saved now: progress (dormant classes included), loadouts, the active one, cooldowns with 3 s or more left. */
    public Snapshot snapshot(long now) {
        Map<String, Progress> all = new LinkedHashMap<>(dormant);
        all.putAll(classes);
        return new Snapshot(Collections.unmodifiableMap(all), List.copyOf(loadouts), active,
                Collections.unmodifiableMap(cooldowns.saved(now, 3_000)), controls, bar);
    }

    /** The saved form (YAML). {@code now} decides which cooldowns are long enough to keep. */
    public String toYaml(long now) {
        return snapshot(now).toYaml();
    }

    /**
     * Read a saved profile and repair it against {@code catalog} (each repair is added to
     * {@code repairs}). A null or blank document is a new player. Returns null, with the reason in
     * {@code repairs}, for a document that doesn't parse or comes from a newer version: the caller
     * must then leave it untouched (never save over it), because starting empty would erase it.
     */
    public static ClassProfile fromYaml(String data, ClassCatalog catalog, long now, List<String> repairs) {
        ClassProfile p = new ClassProfile();
        if (data == null || data.isBlank()) return p;
        YamlConfiguration y = new YamlConfiguration();
        try {
            y.loadFromString(data);
        } catch (InvalidConfigurationException | RuntimeException e) {
            repairs.add("saved class data did not parse (" + e.getMessage() + ")");
            return null;
        }
        if (y.getInt("version", VERSION) > VERSION) {
            repairs.add("saved class data is from a newer version (" + y.getInt("version") + ")");
            return null;
        }
        ConfigurationSection cs = y.getConfigurationSection("classes");
        if (cs != null) {
            for (String id : cs.getKeys(false)) {
                int rank = cs.getInt(id + ".rank", 1);
                long xp = cs.getLong(id + ".xp", 0);
                if (catalog.classDef(id) == null) {
                    // Not in classes.yml now: kept as stored (not used), never erased by a config change.
                    p.dormant.put(id, new Progress(rank, xp));
                    repairs.add("kept progress in unknown class " + id + " (not in classes.yml; unused until it returns)");
                    continue;
                }
                int r = Math.max(1, Math.min(ClassRanks.MAX_RANK, rank));
                long x = r >= ClassRanks.MAX_RANK ? 0 : Math.max(0, Math.min(xp, ClassRanks.xpForRank(r) - 1));
                if (r != rank || x != xp) repairs.add("clamped " + id + " rank " + rank + " xp " + xp);
                p.classes.put(id, new Progress(r, x));
            }
        }
        ConfigurationSection ls = y.getConfigurationSection("loadouts");
        if (ls != null) {
            for (String key : ls.getKeys(false)) {
                int i;
                try { i = Integer.parseInt(key); } catch (NumberFormatException e) { repairs.add("dropped loadout " + key); continue; }
                if (i < 0 || i >= p.loadouts.size()) { repairs.add("dropped loadout " + key + " (no such slot)"); continue; }
                ConfigurationSection l = ls.getConfigurationSection(key);
                if (l == null) continue;
                Loadout raw = new Loadout(l.getString("name"), l.getStringList("actives"), l.getStringList("passives"),
                        blankToNull(l.getString("keystone")));
                p.loadouts.set(i, p.repair(raw, catalog, repairs));
            }
        }
        int act = y.getInt("active", 0);
        p.active = act >= 0 && act < p.loadouts.size() ? act : 0;
        // Preferences: an unknown value (a newer build's scheme) falls back to the server default.
        p.controls = Controls.Scheme.parse(y.getString("controls"));
        p.bar = Controls.BarMode.parse(y.getString("bar"));
        ConfigurationSection cd = y.getConfigurationSection("cooldowns");
        if (cd != null) {
            Map<String, Long> saved = new HashMap<>();
            for (String k : cd.getKeys(false)) saved.put(k.replace('~', ':'), cd.getLong(k));
            p.cooldowns.restore(saved, now);
        }
        return p;
    }

    /**
     * After the catalog changed (reload): classes it no longer has go dormant (kept, unused), and
     * dormant ones it has again come back. Then every loadout is re-checked.
     */
    void reconcile(ClassCatalog catalog, List<String> repairs) {
        for (var it = classes.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<String, Progress> e = it.next();
            if (catalog.classDef(e.getKey()) != null) continue;
            dormant.put(e.getKey(), e.getValue());
            it.remove();
            repairs.add("class " + e.getKey() + " is no longer in classes.yml; its progress is kept, unused");
        }
        for (var it = dormant.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<String, Progress> e = it.next();
            if (catalog.classDef(e.getKey()) == null) continue;
            Progress d = e.getValue();
            int r = Math.max(1, Math.min(ClassRanks.MAX_RANK, d.rank()));
            long x = r >= ClassRanks.MAX_RANK ? 0 : Math.max(0, Math.min(d.xp(), ClassRanks.xpForRank(r) - 1));
            classes.put(e.getKey(), new Progress(r, x));
            it.remove();
            repairs.add("class " + e.getKey() + " is back; its progress was kept");
        }
        for (int i = 0; i < loadouts.size(); i++) loadouts.set(i, repair(loadouts.get(i), catalog, repairs));
    }

    /**
     * {@code l} with every slot that breaks the rules emptied (one repair note per emptied slot). An
     * ability the catalog doesn't know stays in its slot, inert: a typo in abilities.yml must not
     * cost players their loadouts.
     */
    Loadout repair(Loadout l, ClassCatalog catalog, List<String> repairs) {
        Loadout out = l;
        java.util.Set<String> seen = new java.util.HashSet<>();
        java.util.Set<String> tags = new java.util.HashSet<>();
        for (int s = 0; s < Loadout.ACTIVE_SLOTS; s++) {
            String id = out.actives().get(s);
            if (id != null && !fits(id, AbilityDef.Kind.ACTIVE, catalog, seen, tags)) { repairs.add("emptied active slot " + (s + 1) + " (" + id + ")"); out = out.withActive(s, null); }
        }
        for (int s = 0; s < Loadout.PASSIVE_SLOTS; s++) {
            String id = out.passives().get(s);
            if (id != null && !fits(id, AbilityDef.Kind.PASSIVE, catalog, seen, tags)) { repairs.add("emptied passive slot " + (s + 1) + " (" + id + ")"); out = out.withPassive(s, null); }
        }
        if (out.keystone() != null && !fits(out.keystone(), AbilityDef.Kind.KEYSTONE, catalog, seen, tags)) {
            repairs.add("emptied keystone (" + out.keystone() + ")");
            out = out.withKeystone(null);
        }
        return out;
    }

    /** Longest id kept for an ability the catalog doesn't know (anything longer is junk, not a renamed ability). */
    static final int MAX_ID_LENGTH = 64;

    private boolean fits(String id, AbilityDef.Kind kind, ClassCatalog catalog, java.util.Set<String> seen, java.util.Set<String> tags) {
        AbilityDef a = catalog.ability(id);
        if (a == null) return id.length() <= MAX_ID_LENGTH && seen.add(id); // not in abilities.yml now: kept, inert (fixing the file brings it back)
        // The rank, not the other requirements: skill levels may not be loaded yet, and an ability whose skill dropped
        // stays in its slot, inert (refused at cast and on equip), until the skill is back.
        if (a.kind() != kind || !rankReached(a) || !seen.add(id)) return false;
        for (String t : a.exclusive()) if (!tags.add(t)) return false;
        return true;
    }

    private static List<String> blanks(List<String> ids) {
        List<String> out = new ArrayList<>(ids.size());
        for (String s : ids) out.add(s == null ? "" : s);
        return out;
    }

    private static String blankToNull(String s) { return s == null || s.isBlank() ? null : s; }
}
