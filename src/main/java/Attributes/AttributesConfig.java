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
    private volatile AttributeRules rules = AttributeRules.DEFAULTS;

    /** @param curves crit / lifesteal curves (from combat.yml), read on every reload */
    public AttributesConfig(JavaPlugin plugin, Supplier<AttributeRules.Curves> curves) {
        this.plugin = plugin;
        this.curves = curves;
        reload();
    }

    public KillXp.Params killXp() { return killXp; }

    public AttributeRules rules() { return rules; }

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
            rules = parseRules(section(cfg, "attributes"), curves.get(), problems::add);
            problems.forEach(p -> plugin.getLogger().warning("[Attributes] attributes.yml: " + p));
            plugin.getLogger().info("[Attributes] Loaded attributes.yml (kill XP v2 " + (killXp.enabled() ? "on" : "off")
                    + ", " + rules.skillWeights().size() + " skills feed attributes, PvE offense "
                    + (rules.switches().pveOffense() ? "on" : "off") + ")");
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "[Attributes] Could not load attributes.yml - keeping previous values", e);
        }
    }

    /**
     * Build rules from the {@code attributes:} section. Anything missing or invalid falls back to
     * {@link AttributeRules#DEFAULTS} and is reported through {@code problems}.
     */
    public static AttributeRules parseRules(ConfigurationSection s, AttributeRules.Curves curves, Consumer<String> problems) {
        AttributeRules def = AttributeRules.DEFAULTS;
        if (s == null) return new AttributeRules(def.skillWeights(), def.caps(), def.derived(), def.limits(), curves, def.switches());

        Map<String, List<AttributeRules.SkillWeight>> weights = new LinkedHashMap<>();
        ConfigurationSection sk = section(s, "skills");
        if (sk == null) {
            weights.putAll(def.skillWeights());
        } else {
            for (String skill : sk.getKeys(false)) {
                List<AttributeRules.SkillWeight> list = new ArrayList<>();
                for (Map<?, ?> m : sk.getMapList(skill)) {
                    Attribute a = Attribute.parse(String.valueOf(m.get("attribute")));
                    Object per = m.get("per-level");
                    Object weapon = m.get("weapon");
                    WeaponClass w = weapon == null ? null : WeaponClass.parse(String.valueOf(weapon));
                    if (a == null || !(per instanceof Number n) || !Double.isFinite(n.doubleValue())) {
                        problems.accept("skills." + skill + ": entry " + m + " needs a valid attribute and per-level - skipped");
                        continue;
                    }
                    if (weapon != null && w == null) {
                        problems.accept("skills." + skill + ": unknown weapon '" + weapon + "' - skipped");
                        continue;
                    }
                    list.add(new AttributeRules.SkillWeight(a, n.doubleValue(), w));
                }
                if (!list.isEmpty()) weights.put(skill, List.copyOf(list));
            }
        }

        Map<Attribute, Double> caps = new EnumMap<>(Attribute.class);
        for (Attribute a : Attribute.values()) {
            double v = s.getDouble("caps." + a.name().toLowerCase(), def.cap(a));
            caps.put(a, positive(v, def.cap(a), "caps." + a.name().toLowerCase(), problems));
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
                s.getBoolean("integration.essence", ds.essence()));

        return new AttributeRules(weights, caps, derived, limits, curves, switches);
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
