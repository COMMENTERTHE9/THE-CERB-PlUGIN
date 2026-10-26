package Attributes;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.logging.Level;

/**
 * Loads attributes.yml (copied on first run; missing keys fall back to the jar's defaults).
 * Values are swapped in atomically, so readers on any region thread always see a whole config.
 */
public final class AttributesConfig {

    private final JavaPlugin plugin;
    private final Supplier<AttributeRules.Curves> curves;
    private volatile KillXp.Params killXp = KillXp.Params.DEFAULTS;
    private volatile XpRules xpRules = XpRules.DEFAULTS;
    private volatile AttributeRules rules = AttributeRules.DEFAULTS;
    private volatile Manager.Realm defaultRealm = Manager.Realm.AWAKENED;

    /** @param curves crit / lifesteal curves (from combat.yml), read on every reload */
    public AttributesConfig(JavaPlugin plugin, Supplier<AttributeRules.Curves> curves) {
        this.plugin = plugin;
        this.curves = curves;
        reload();
    }

    public KillXp.Params killXp() { return killXp; }

    /** What can't be farmed (attributes.yml {@code xp-integrity}). */
    public XpRules xpRules() { return xpRules; }

    public AttributeRules rules() { return rules; }

    /** Every player's realm until Ascension exists (attributes.realm.default). */
    public Manager.Realm defaultRealm() { return defaultRealm; }

    public void reload() {
        try {
            File file = new File(plugin.getDataFolder(), "attributes.yml");
            if (!file.exists()) plugin.saveResource("attributes.yml", false);
            YamlConfiguration cfg = YamlConfiguration.loadConfiguration(file);
            try (InputStream in = plugin.getResource("attributes.yml")) {
                if (in != null) cfg.setDefaults(YamlConfiguration.loadConfiguration(new InputStreamReader(in, StandardCharsets.UTF_8)));
            }
            KillXp.Params d = KillXp.Params.DEFAULTS;
            killXp = new KillXp.Params(
                    cfg.getBoolean("kill-xp.enabled", d.enabled()),
                    cfg.getDouble("kill-xp.base", d.base()),
                    cfg.getDouble("kill-xp.level-slope", d.levelSlope()),
                    cfg.getInt("kill-xp.anti-grind.level-gap", d.antiGrindGap()),
                    cfg.getDouble("kill-xp.anti-grind.factor", d.antiGrindFactor()),
                    cfg.getDouble("kill-xp.max-per-kill", d.maxPerKill()));
            List<String> problems = new ArrayList<>();
            xpRules = parseXpRules(cfg, problems::add);
            if (!cfg.isSet("attributes")) {
                plugin.getLogger().info("[Attributes] attributes.yml has no 'attributes:' section (older file) - using the built-in"
                        + " defaults. Delete attributes.yml and /cerb reload to get the full file with every setting.");
            }
            rules = parseRules(section(cfg, "attributes"), curves.get(), problems::add);
            String realmName = cfg.getString("attributes.realm.default", "AWAKENED");
            Manager.Realm realm = Manager.Realm.parse(realmName);
            if (realm == null) problems.add("realm.default '" + realmName + "' is not a realm - using Awakened");
            defaultRealm = realm != null ? realm : Manager.Realm.AWAKENED;
            problems.forEach(p -> plugin.getLogger().warning("[Attributes] attributes.yml: " + p));
            plugin.getLogger().info("[Attributes] Loaded attributes.yml (kill XP v2 " + (killXp.enabled() ? "on" : "off")
                    + ", " + rules.skillWeights().size() + " skills feed attributes, PvE offense "
                    + (rules.switches().pveOffense() ? "on" : "off") + ")");
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "[Attributes] Could not load attributes.yml - keeping previous values", e);
        }
    }

    /**
     * The {@code xp-integrity} section; an older file without it gets the jar's (through the defaults), and a missing
     * key the built-in value. A spawn reason that isn't one is reported and ignored.
     */
    static XpRules parseXpRules(org.bukkit.configuration.Configuration cfg, Consumer<String> problems) {
        XpRules d = XpRules.DEFAULTS;
        Map<String, Double> byOrigin = new LinkedHashMap<>();
        // section(): an older file without it reads the jar's (getConfigurationSection would make an empty one instead)
        ConfigurationSection o = section(cfg, "xp-integrity.by-origin");
        if (o == null) byOrigin.putAll(d.byOrigin());
        else for (String reason : o.getKeys(false)) {
            String key = reason.trim().toUpperCase(java.util.Locale.ROOT);
            boolean known = false;
            for (org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason r : org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason.values()) {
                if (r.name().equals(key)) { known = true; break; }
            }
            if (!known) { problems.accept("xp-integrity.by-origin: '" + reason + "' is not a spawn reason - ignored"); continue; }
            byOrigin.put(key, o.getDouble(reason, 1.0));
        }
        return new XpRules(byOrigin,
                cfg.getBoolean("xp-integrity.pvp-xp", d.pvpXp()),
                cfg.getBoolean("xp-integrity.hit-budget", d.hitBudget()),
                cfg.getInt("xp-integrity.idle-seconds", d.idleSeconds()));
    }

    /**
     * Build rules from the {@code attributes:} section. Anything missing or invalid falls back to
     * {@link AttributeRules#DEFAULTS} and is reported through {@code problems}.
     */
    public static AttributeRules parseRules(ConfigurationSection s, AttributeRules.Curves curves, Consumer<String> problems) {
        AttributeRules def = AttributeRules.DEFAULTS;
        if (s == null) return new AttributeRules(def.skillWeights(), def.caps(), def.derived(), def.limits(), curves, def.switches(), def.secondary());

        Map<String, List<AttributeRules.SkillWeight>> weights = new LinkedHashMap<>();
        ConfigurationSection sk = section(s, "skills");
        if (sk == null) {
            weights.putAll(def.skillWeights());
        } else {
            for (String skill : keysWithShipped(sk, problems)) {
                List<AttributeRules.SkillWeight> list = new ArrayList<>();
                for (Map<?, ?> m : sk.getMapList(skill)) {
                    Attribute a = Attribute.parse(String.valueOf(m.get("attribute")));
                    Object per = m.get("per-level");
                    Object weapon = m.get("weapon");
                    String weaponName = weapon == null ? "" : String.valueOf(weapon).trim();
                    boolean allWeapons = weaponName.isEmpty() || weaponName.equalsIgnoreCase("all");
                    WeaponClass w = allWeapons ? null : WeaponClass.parse(weaponName);
                    if (a == null || !(per instanceof Number n) || !Double.isFinite(n.doubleValue())) {
                        problems.accept("skills." + skill + ": entry " + m + " needs a valid attribute and per-level - skipped");
                        continue;
                    }
                    if (!allWeapons && w == null) {
                        problems.accept("skills." + skill + ": unknown weapon '" + weapon + "' - skipped");
                        continue;
                    }
                    Object yieldName = m.get("yield");
                    // No yield: the skill's own (Mining's Fortune on ore, as in a file from before scoping); "all": everything.
                    Yield y = yieldName == null ? (a == Attribute.FORTUNE ? Yield.defaultFor(skill) : null) : Yield.parse(String.valueOf(yieldName));
                    if (yieldName != null && y == null && !String.valueOf(yieldName).trim().equalsIgnoreCase("all")) {
                        problems.accept("skills." + skill + ": unknown yield '" + yieldName + "' (ore, wood, all) - skipped");
                        continue;
                    }
                    if (y != null && a != Attribute.FORTUNE) {
                        problems.accept("skills." + skill + ": a yield only limits fortune - '" + yieldName + "' ignored for " + a.displayName);
                        y = null;
                    }
                    list.add(new AttributeRules.SkillWeight(a, n.doubleValue(), w, y));
                }
                if (!list.isEmpty()) weights.put(skill, List.copyOf(list));
            }
        }

        Map<Attribute, Double> caps = new EnumMap<>(Attribute.class);
        for (Attribute a : Attribute.values()) {
            String key = "caps." + a.name().toLowerCase(java.util.Locale.ROOT); // ROOT: "vitality", never "vıtalıty"
            double v = s.getDouble(key, def.cap(a));
            caps.put(a, positive(v, def.cap(a), key, problems));
        }

        AttributeRules.Derived dd = def.derived();
        AttributeRules.Derived derived = new AttributeRules.Derived(
                num(s, "derived.power-damage", dd.powerDamage(), problems),
                num(s, "derived.vitality-hp", dd.vitalityHp(), problems),
                num(s, "derived.fortitude-armor", dd.fortitudeArmor(), problems),
                num(s, "derived.fortitude-defense-bar", dd.fortitudeDefenseBar(), problems),
                num(s, "derived.fortitude-resist", dd.fortitudeResist(), problems),
                num(s, "derived.essence-mana", dd.essenceMana(), problems),
                num(s, "derived.essence-regen", dd.essenceRegen(), problems),
                num(s, "derived.essence-spell", dd.essenceSpell(), problems));

        AttributeRules.Limits dl = def.limits();
        AttributeRules.Limits limits = new AttributeRules.Limits(
                Math.max(1.0, num(s, "limits.offense-budget", dl.offenseBudget(), problems)),
                fraction(s, "limits.crit-chance", dl.critChance(), problems),
                Math.max(1.0, num(s, "limits.crit-multiplier", dl.critMultiplier(), problems)),
                fraction(s, "limits.lifesteal", dl.lifesteal(), problems),
                fraction(s, "limits.armor", dl.armor(), problems),
                fraction(s, "limits.resist", dl.resist(), problems),
                fraction(s, "limits.total-mitigation", dl.totalMitigation(), problems));

        AttributeRules.Switches ds = def.switches();
        AttributeRules.Switches switches = new AttributeRules.Switches(
                s.getBoolean("integration.pve-offense", ds.pveOffense()),
                s.getBoolean("integration.vitality", ds.vitality()),
                s.getBoolean("integration.fortitude", ds.fortitude()),
                s.getBoolean("integration.essence", ds.essence()),
                s.getBoolean("integration.realm-magnitude", ds.realmMagnitude()),
                s.getBoolean("integration.pvp-defense", ds.pvpDefense()));

        return new AttributeRules(weights, caps, derived, limits, curves, switches, parseSecondary(section(s, "secondary"), problems));
    }

    /** The {@code secondary:} section (speed and handling stats); missing parts use the defaults. */
    static AttributeRules.Secondary parseSecondary(ConfigurationSection s, Consumer<String> problems) {
        AttributeRules.Secondary def = AttributeRules.DEFAULT_SECONDARY;
        if (s == null) return def;
        Map<Stat, AttributeRules.SecondaryStat> stats = new EnumMap<>(Stat.class);
        ConfigurationSection st = section(s, "stats");
        for (Stat stat : Stat.SECONDARY) {
            AttributeRules.SecondaryStat d = def.get(stat);
            String key = stat == Stat.MOVE_SPEED ? "movement-speed" : stat.name().toLowerCase(java.util.Locale.ROOT).replace('_', '-');
            ConfigurationSection one = st == null ? null : section(st, key);
            if (one == null) { stats.put(stat, d); continue; }
            Attribute driver = Attribute.parse(one.getString("driver", d.driver().name()));
            if (driver == null) {
                problems.accept("secondary.stats." + key + ".driver '" + one.getString("driver") + "' is not an attribute - using " + d.driver().displayName);
                driver = d.driver();
            }
            double cap = num(one, "cap", d.cap(), problems);
            if (cap <= 0 || cap > 2.0) {
                problems.accept("secondary.stats." + key + ".cap must be in (0, 2] - using " + d.cap());
                cap = d.cap();
            }
            stats.put(stat, new AttributeRules.SecondaryStat(one.getBoolean("enabled", d.enabled()), driver,
                    num(one, "per-point", d.perPoint(), problems), cap));
        }
        Map<String, List<AttributeRules.StatWeight>> weights = new LinkedHashMap<>();
        ConfigurationSection sk = section(s, "skills");
        if (sk == null) {
            weights.putAll(def.skillWeights());
        } else {
            for (String skill : keysWithShipped(sk, problems)) {
                List<AttributeRules.StatWeight> list = new ArrayList<>();
                for (Map<?, ?> m : sk.getMapList(skill)) {
                    Stat stat = Stat.parse(String.valueOf(m.get("stat")));
                    Object per = m.get("per-level");
                    Object weapon = m.get("weapon");
                    String weaponName = weapon == null ? "" : String.valueOf(weapon).trim();
                    boolean allWeapons = weaponName.isEmpty() || weaponName.equalsIgnoreCase("all");
                    WeaponClass w = allWeapons ? null : WeaponClass.parse(weaponName);
                    if (stat == null || !stat.isSecondary() || !(per instanceof Number n) || !Double.isFinite(n.doubleValue())) {
                        problems.accept("secondary.skills." + skill + ": entry " + m + " needs a speed stat and per-level - skipped");
                        continue;
                    }
                    if (!allWeapons && w == null) {
                        problems.accept("secondary.skills." + skill + ": unknown weapon '" + weapon + "' - skipped");
                        continue;
                    }
                    list.add(new AttributeRules.StatWeight(stat, n.doubleValue(), w));
                }
                if (!list.isEmpty()) weights.put(skill, List.copyOf(list));
            }
        }
        int tag = s.getInt("pvp-tag-seconds", def.pvpTagSeconds());
        if (tag < 1 || tag > 600) {
            problems.accept("secondary.pvp-tag-seconds must be 1-600 - using " + def.pvpTagSeconds());
            tag = def.pvpTagSeconds();
        }
        return new AttributeRules.Secondary(stats, weights, s.getBoolean("in-pvp", def.inPvp()), tag);
    }

    /**
     * Skills an update added to the shipped attributes.yml after its first version. Only these come
     * from the shipped file when a server's file doesn't mention them: a skill an owner deleted from
     * their own file before (older files said "skills not listed here add nothing") stays deleted.
     * When an update adds a skill's entry to the shipped file, add the skill here.
     */
    static final java.util.Set<String> ADDED_BY_UPDATES = java.util.Set.of(
            "Mining",       // 2026-10-25, the gathering loop
            "Woodcutting"); // 2026-10-26, the Woodcutting loop

    /**
     * The skills {@code sk} lists, plus each skill in {@link #ADDED_BY_UPDATES} that the shipped file
     * lists and this one doesn't mention at all (its list is then read through the defaults, and
     * {@code note} says so). {@code Skill: []} in the server's file turns one off.
     */
    static java.util.Set<String> keysWithShipped(ConfigurationSection sk, Consumer<String> note) {
        java.util.Set<String> keys = new java.util.LinkedHashSet<>(sk.getKeys(false));
        ConfigurationSection shipped = sk.getDefaultSection();
        if (shipped == null) return keys;
        for (String skill : shipped.getKeys(false)) {
            if (!ADDED_BY_UPDATES.contains(skill) || keys.contains(skill)) continue;
            keys.add(skill);
            note.accept(sk.getCurrentPath() + "." + skill + ": not in this file (an update added it), so the plugin's own entry is used; "
                    + "write \"" + skill + ": []\" to give it nothing");
        }
        return keys;
    }

    /**
     * The section at {@code path}, falling back to the jar defaults when the server's file
     * doesn't have it (an attributes.yml from an older version). Unlike
     * {@code getConfigurationSection}, this never substitutes a new empty section.
     */
    static ConfigurationSection section(ConfigurationSection parent, String path) {
        return parent.get(path) instanceof ConfigurationSection c ? c : null;
    }

    private static double num(ConfigurationSection s, String path, double def, Consumer<String> problems) {
        double v = s.getDouble(path, def);
        if (!Double.isFinite(v) || v < 0) {
            problems.accept(path + " must be a number >= 0 - using " + def);
            return def;
        }
        return v;
    }

    private static double positive(double v, double def, String path, Consumer<String> problems) {
        if (!Double.isFinite(v) || v <= 0) {
            problems.accept(path + " must be > 0 - using " + def);
            return def;
        }
        return v;
    }

    /** A limit that must be a fraction in [0, 1). */
    private static double fraction(ConfigurationSection s, String path, double def, Consumer<String> problems) {
        double v = num(s, path, def, problems);
        if (v >= 1.0) {
            problems.accept(path + " must be below 1.0 - using " + def);
            return def;
        }
        return v;
    }
}
