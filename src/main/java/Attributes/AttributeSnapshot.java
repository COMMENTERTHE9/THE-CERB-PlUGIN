package Attributes;

import java.util.Comparator;
import java.util.List;

/**
 * One player's attributes and derived stats at a point in time. Immutable and fully computed by
 * {@link AttributeCalculator} (all caps already applied), so reading it per hit is a field read
 * from any thread. Every value is finite.
 */
public final class AttributeSnapshot {

    private final AttributeRules rules;
    private final RealmContext realm;
    private final double[] totals;          // per Attribute, all-weapon part, capped
    private final double[] power;           // per WeaponClass: general + class Power, capped
    private final double[] damageBonus;     // per WeaponClass: DAMAGE stat (fraction)
    private final double critChance, critMultiplier, lifesteal;
    private final double maxHpMultiplier, armorBonus, defenseBarMultiplier, resist;
    private final double maxManaMultiplier, manaRegenMultiplier, spellDamageMultiplier;
    private final double[] secondary, secondaryRaw;        // per Stat (speed/handling stats only)
    private final double[] attackSpeed, attackSpeedRaw;    // per WeaponClass
    private final List<Contribution> contributions;
    private final int ignored;

    AttributeSnapshot(AttributeRules rules, RealmContext realm, double[] totals, double[] power, double[] damageBonus,
                      double critChance, double critMultiplier, double lifesteal,
                      double maxHpMultiplier, double armorBonus, double defenseBarMultiplier, double resist,
                      double maxManaMultiplier, double manaRegenMultiplier, double spellDamageMultiplier,
                      double[] secondary, double[] secondaryRaw, double[] attackSpeed, double[] attackSpeedRaw,
                      List<Contribution> contributions, int ignored) {
        this.rules = rules;
        this.realm = realm;
        this.totals = totals;
        this.power = power;
        this.damageBonus = damageBonus;
        this.critChance = critChance;
        this.critMultiplier = critMultiplier;
        this.lifesteal = lifesteal;
        this.maxHpMultiplier = maxHpMultiplier;
        this.armorBonus = armorBonus;
        this.defenseBarMultiplier = defenseBarMultiplier;
        this.resist = resist;
        this.maxManaMultiplier = maxManaMultiplier;
        this.manaRegenMultiplier = manaRegenMultiplier;
        this.spellDamageMultiplier = spellDamageMultiplier;
        this.secondary = secondary;
        this.secondaryRaw = secondaryRaw;
        this.attackSpeed = attackSpeed;
        this.attackSpeedRaw = attackSpeedRaw;
        this.contributions = List.copyOf(contributions);
        this.ignored = ignored;
    }

    /** No contributions: every multiplier is ×1, no crit, no lifesteal. */
    public static AttributeSnapshot empty(AttributeRules rules) {
        return AttributeCalculator.compute(List.of(), rules);
    }

    public AttributeRules rules() { return rules; }

    /** The realm this snapshot was built for, and its magnitude (×1 for Awakened). */
    public RealmContext realm() { return realm; }

    public double realmMultiplier() { return realm.multiplier(); }

    /**
     * A speed/handling stat after diminishing returns and its cap (0.12 = +12%); 0 when the stat
     * is switched off. For attack speed use {@link #attackSpeed(WeaponClass)}: it is per weapon.
     */
    public double secondary(Stat s) { return secondary[s.ordinal()]; }

    /** The same stat before diminishing returns (what the sources add up to). */
    public double secondaryRaw(Stat s) { return secondaryRaw[s.ordinal()]; }

    /** Attack speed bonus for hits with {@code c}: all-weapon part plus that weapon's skill. */
    public double attackSpeed(WeaponClass c) { return attackSpeed[c.ordinal()]; }

    public double attackSpeedRaw(WeaponClass c) { return attackSpeedRaw[c.ordinal()]; }

    /** Attribute total. For Power this is the all-weapon part; see {@link #power(WeaponClass)}. */
    public double get(Attribute a) { return totals[a.ordinal()]; }

    /** Power for hits made with {@code c}: all-weapon Power plus Power limited to that class. */
    public double power(WeaponClass c) { return power[c.ordinal()]; }

    /** The level baseline: ×(1 + per-point × Power) for this weapon class. */
    public double powerMultiplier(WeaponClass c) {
        return 1.0 + rules.derived().powerDamage() * power(c);
    }

    /** Direct damage bonus for this class (fraction), from DAMAGE stat contributions. */
    public double damageBonus(WeaponClass c) { return damageBonus[c.ordinal()]; }

    public double critChance() { return critChance; }
    public double critMultiplier() { return critMultiplier; }
    public double lifesteal() { return lifesteal; }
    public double maxHpMultiplier() { return maxHpMultiplier; }
    /** Armor reduction added on top of gear (fraction); see {@link #totalArmor(double)}. */
    public double armorBonus() { return armorBonus; }
    public double defenseBarMultiplier() { return defenseBarMultiplier; }
    /** Fire / cold / drowning resistance added by Fortitude (fraction). */
    public double resist() { return resist; }
    public double maxManaMultiplier() { return maxManaMultiplier; }
    public double manaRegenMultiplier() { return manaRegenMultiplier; }
    public double spellDamageMultiplier() { return spellDamageMultiplier; }

    /**
     * Damage multiplier for one hit. Crit and damage bonuses stack on top of the Power baseline,
     * but together never past the offense budget (×6 by default).
     */
    public double offenseMultiplier(WeaponClass c, boolean crit) {
        double extras = (1.0 + damageBonus(c)) * (crit ? critMultiplier : 1.0);
        return powerMultiplier(c) * Math.min(rules.limits().offenseBudget(), extras);
    }

    /** Everything that multiplies one hit: realm magnitude × the within-realm offense multiplier. */
    public double hitMultiplier(WeaponClass c, boolean crit) {
        return realm.multiplier() * offenseMultiplier(c, crit);
    }

    /** Average damage multiplier including crit odds (for display). */
    public double expectedOffenseMultiplier(WeaponClass c) {
        double extras = (1.0 + damageBonus(c)) * (1.0 + critChance * (critMultiplier - 1.0));
        return powerMultiplier(c) * Math.min(rules.limits().offenseBudget(), extras);
    }

    /** Gear armor reduction plus Fortitude's bonus, within the armor limit. */
    public double totalArmor(double gearReduction) {
        double g = Double.isFinite(gearReduction) ? Math.max(0, gearReduction) : 0;
        return Math.min(rules.limits().armor(), g + armorBonus);
    }

    /** Largest first; equal amounts by label, so the order never depends on how sources were stored. */
    private static final Comparator<Contribution> LARGEST_FIRST = Comparator.comparingDouble(Contribution::amount).reversed()
            .thenComparing(Contribution::label, Comparator.nullsLast(Comparator.naturalOrder()));

    /** Everything that fed {@code a}, largest first. */
    public List<Contribution> sources(Attribute a) {
        return contributions.stream().filter(x -> x.attribute() == a).sorted(LARGEST_FIRST).toList();
    }

    /** Direct contributions to {@code s}, largest first. */
    public List<Contribution> sources(Stat s) {
        return contributions.stream().filter(x -> x.stat() == s)
                .sorted(LARGEST_FIRST).toList();
    }

    public List<Contribution> contributions() { return contributions; }

    /** How many contributions were dropped for a non-finite amount (should be 0). */
    public int ignoredContributions() { return ignored; }
}
