package Attributes;

import Manager.ScalingFormula.ScalingParams;

import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Everything the attribute engine needs to know, loaded from attributes.yml (and the crit /
 * lifesteal curves from combat.yml). Immutable: a reload builds a new instance and swaps it in.
 *
 * @param skillWeights attribute points per skill level, by exact skill name
 * @param caps         ceiling for each attribute total (a safety rail, not the balance lever)
 * @param secondary    speed and handling stats (attack speed, draw speed, movement, cooldown recovery)
 */
public record AttributeRules(Map<String, List<SkillWeight>> skillWeights,
                             Map<Attribute, Double> caps,
                             Derived derived, Limits limits, Curves curves, Switches switches,
                             Secondary secondary) {

    /** Rules with the default speed and handling stats. */
    public AttributeRules(Map<String, List<SkillWeight>> skillWeights, Map<Attribute, Double> caps,
                          Derived derived, Limits limits, Curves curves, Switches switches) {
        this(skillWeights, caps, derived, limits, curves, switches, DEFAULT_SECONDARY);
    }

    /**
     * {@code perLevel} points of {@code attribute} per skill level; {@code weapon} null = all weapons; {@code yield}
     * (Fortune only) what the points count on, null = everything.
     */
    public record SkillWeight(Attribute attribute, double perLevel, WeaponClass weapon, Yield yield) {
        public SkillWeight(Attribute attribute, double perLevel, WeaponClass weapon) { this(attribute, perLevel, weapon, null); }
    }

    /** How attribute points turn into derived stats (per point). */
    public record Derived(double powerDamage, double vitalityHp, double fortitudeArmor,
                          double fortitudeDefenseBar, double fortitudeResist,
                          double essenceMana, double essenceRegen, double essenceSpell) {}

    /**
     * Hard limits, applied once in {@link AttributeCalculator} / {@link AttributeSnapshot}.
     * {@code offenseBudget}: crit, damage bonuses and later gear/buffs may multiply the level
     * baseline (the Power multiplier) by at most this much (POWER_SCALING.md §4.2).
     */
    public record Limits(double offenseBudget, double critChance, double critMultiplier, double lifesteal,
                         double armor, double resist, double totalMitigation) {}

    /** Curves shared with combat.yml, so PvP and PvE crits follow the same tuning. */
    public record Curves(ScalingParams critChance, ScalingParams critMultiplier,
                         String lifestealSkill, ScalingParams lifesteal) {}

    /**
     * Kill switches: each integration can be turned off without touching the others.
     * {@code pvpDefense}: false (default) keeps PvP hits as before Attributes 2.0 - the defender's
     * Vitality, Fortitude and realm don't change them; true is for the future PvP pass.
     */
    public record Switches(boolean pveOffense, boolean vitality, boolean fortitude, boolean essence,
                           boolean realmMagnitude, boolean pvpDefense) {}

    /**
     * One speed/handling stat: {@code driver} x {@code perPoint} plus skill contributions gives the
     * raw value; the effective value is {@code cap x (1 - e^(-raw/cap))} (diminishing returns).
     */
    public record SecondaryStat(boolean enabled, Attribute driver, double perPoint, double cap) {
        public static final SecondaryStat OFF = new SecondaryStat(false, Attribute.AGILITY, 0, 0.01);
    }

    /** Per skill level, for a speed/handling stat; {@code weapon} limits attack speed to one weapon type. */
    public record StatWeight(Stat stat, double perLevel, WeaponClass weapon) {}

    /**
     * Speed and handling stats (docs/design/ATTRIBUTE_EXPANSION.md).
     *
     * @param inPvp         false: the bonuses pause while a player is PvP-tagged (PvP stays vanilla)
     * @param pvpTagSeconds how long hitting or being hit by a player counts as PvP
     */
    public record Secondary(Map<Stat, SecondaryStat> stats, Map<String, List<StatWeight>> skillWeights,
                            boolean inPvp, int pvpTagSeconds) {
        public Secondary {
            stats = Map.copyOf(stats);
            skillWeights = Map.copyOf(skillWeights);
        }

        public SecondaryStat get(Stat s) { return stats.getOrDefault(s, SecondaryStat.OFF); }
    }

    public AttributeRules {
        skillWeights = Map.copyOf(skillWeights);
        EnumMap<Attribute, Double> c = new EnumMap<>(Attribute.class);
        for (Attribute a : Attribute.values()) c.put(a, caps.getOrDefault(a, DEFAULT_CAP));
        caps = java.util.Collections.unmodifiableMap(c);
    }

    public static final double DEFAULT_CAP = 300.0;

    public double cap(Attribute a) {
        return caps.get(a);
    }

    // ── Defaults (mirror the bundled attributes.yml and combat.yml) ──────────

    public static final Map<String, List<SkillWeight>> DEFAULT_SKILL_WEIGHTS;
    static {
        Map<String, List<SkillWeight>> m = new LinkedHashMap<>();
        Attribute P = Attribute.POWER, A = Attribute.AGILITY, V = Attribute.VITALITY,
                F = Attribute.FORTITUDE, E = Attribute.ESSENCE, L = Attribute.FORTUNE;
        m.put("Blade Mastery", List.of(new SkillWeight(P, 1.0, WeaponClass.SWORD)));
        m.put("Weapon Mastery", List.of(
                new SkillWeight(P, 1.0, WeaponClass.AXE), new SkillWeight(P, 1.0, WeaponClass.MACE),
                new SkillWeight(P, 1.0, WeaponClass.TRIDENT), new SkillWeight(P, 1.0, WeaponClass.SPEAR),
                new SkillWeight(P, 0.25, null)));
        m.put("Martial Expertise", List.of(new SkillWeight(P, 1.0, WeaponClass.UNARMED)));
        m.put("Ranged Precision", List.of(new SkillWeight(P, 1.0, WeaponClass.BOW)));
        m.put("Critical Strike", List.of(new SkillWeight(A, 1.0, null)));
        m.put("Dual Wielding", List.of(new SkillWeight(A, 0.5, null)));
        m.put("Navigation", List.of(new SkillWeight(A, 0.25, null)));
        m.put("Stealth", List.of(new SkillWeight(A, 0.25, null)));
        m.put("Survival", List.of(new SkillWeight(V, 1.0, null), new SkillWeight(F, 0.5, null)));
        m.put("Heavy Armor Training", List.of(new SkillWeight(F, 1.0, null), new SkillWeight(V, 0.5, null)));
        m.put("First Aid", List.of(new SkillWeight(V, 0.5, null)));
        m.put("Defensive Magic", List.of(new SkillWeight(F, 0.5, null)));
        m.put("Intelligence", List.of(new SkillWeight(E, 1.0, null)));
        m.put("Mana Regeneration", List.of(new SkillWeight(E, 1.0, null)));
        m.put("Spell Weaving", List.of(new SkillWeight(E, 0.5, null)));
        m.put("Elemental Mastery", List.of(new SkillWeight(E, 0.5, null)));
        m.put("Arcane Knowledge", List.of(new SkillWeight(E, 0.5, null)));
        m.put("Scavenging", List.of(new SkillWeight(L, 0.5, null)));
        m.put("Lockpicking", List.of(new SkillWeight(L, 0.5, null)));
        m.put("Trading", List.of(new SkillWeight(L, 0.5, null)));
        m.put("Fishing", List.of(new SkillWeight(L, 0.5, null)));
        m.put("Mining", List.of(new SkillWeight(L, 0.5, null, Yield.ORE)));
        m.put("Woodcutting", List.of(new SkillWeight(V, 0.25, null), new SkillWeight(L, 0.5, null, Yield.WOOD)));
        m.put("Smithing", List.of(new SkillWeight(F, 0.25, null)));
        DEFAULT_SKILL_WEIGHTS = java.util.Collections.unmodifiableMap(m);
    }

    /** Power tracks mob HP inside a realm: ×(1 + 0.05·Power) vs mob HP ×(1 + 0.05·(level − 1)). */
    public static final Derived DEFAULT_DERIVED = new Derived(0.05, 0.01, 0.001, 0.01, 0.002, 0.01, 0.01, 0.02);
    public static final Limits DEFAULT_LIMITS = new Limits(6.0, 0.75, 3.5, 0.40, 0.40, 0.50, 0.75);
    /** combat.yml skills.critical-strike / weapon-mastery.lifesteal defaults, resolved through their presets. */
    public static final Curves DEFAULT_CURVES = new Curves(
            new ScalingParams(0.10, 0.03, 0.08, 60, 0.50, 0.75),
            new ScalingParams(1.5, 0.04, 0.10, 60, 1.5, 3.5),
            "Weapon Mastery",
            new ScalingParams(0.0, 0.03, 0.08, 50, 0.30, 0.40));
    public static final Switches DEFAULT_SWITCHES = new Switches(true, true, true, true, true, false);

    public static final Secondary DEFAULT_SECONDARY;
    static {
        Map<Stat, SecondaryStat> stats = new java.util.EnumMap<>(Stat.class);
        stats.put(Stat.ATTACK_SPEED, new SecondaryStat(true, Attribute.AGILITY, 0.001, 0.25));
        stats.put(Stat.DRAW_SPEED, new SecondaryStat(true, Attribute.AGILITY, 0.001, 0.50));
        stats.put(Stat.MOVE_SPEED, new SecondaryStat(true, Attribute.AGILITY, 0.0005, 0.20));
        stats.put(Stat.COOLDOWN_RECOVERY, new SecondaryStat(true, Attribute.ESSENCE, 0.002, 0.50));
        stats.put(Stat.GATHER_SPEED, new SecondaryStat(true, Attribute.AGILITY, 0.0, 0.25)); // the gathering skills only
        stats.put(Stat.CHOP_SPEED, new SecondaryStat(true, Attribute.AGILITY, 0.0, 0.25));   // Woodcutting only, an axe in hand
        stats.put(Stat.WEAR_RESISTANCE, new SecondaryStat(true, Attribute.FORTITUDE, 0.0, 0.35)); // Smithing only
        Map<String, List<StatWeight>> w = new LinkedHashMap<>();
        w.put("Blade Mastery", List.of(new StatWeight(Stat.ATTACK_SPEED, 0.003, WeaponClass.SWORD)));
        w.put("Weapon Mastery", List.of(
                new StatWeight(Stat.ATTACK_SPEED, 0.003, WeaponClass.AXE), new StatWeight(Stat.ATTACK_SPEED, 0.003, WeaponClass.MACE),
                new StatWeight(Stat.ATTACK_SPEED, 0.003, WeaponClass.TRIDENT), new StatWeight(Stat.ATTACK_SPEED, 0.003, WeaponClass.SPEAR)));
        w.put("Ranged Precision", List.of(new StatWeight(Stat.DRAW_SPEED, 0.004, null)));
        w.put("Navigation", List.of(new StatWeight(Stat.MOVE_SPEED, 0.001, null)));
        w.put("Spell Weaving", List.of(new StatWeight(Stat.COOLDOWN_RECOVERY, 0.003, null)));
        w.put("Mining", List.of(new StatWeight(Stat.GATHER_SPEED, 0.002, null)));
        w.put("Woodcutting", List.of(new StatWeight(Stat.CHOP_SPEED, 0.002, null)));
        w.put("Smithing", List.of(new StatWeight(Stat.WEAR_RESISTANCE, 0.003, null)));
        DEFAULT_SECONDARY = new Secondary(stats, w, false, 15);
    }

    public static final AttributeRules DEFAULTS = new AttributeRules(DEFAULT_SKILL_WEIGHTS, Map.of(),
            DEFAULT_DERIVED, DEFAULT_LIMITS, DEFAULT_CURVES, DEFAULT_SWITCHES);
}
