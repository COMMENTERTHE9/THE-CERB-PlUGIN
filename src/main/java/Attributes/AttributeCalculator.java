package Attributes;

import Manager.ScalingFormula;

import java.util.ArrayList;
import java.util.List;

/**
 * Pure: contributions + rules → {@link AttributeSnapshot}. This is the only place attribute
 * caps and derived-stat limits are applied. No Bukkit objects, no state, safe on any thread.
 */
public final class AttributeCalculator {

    private AttributeCalculator() {}

    public static AttributeSnapshot compute(List<Contribution> input, AttributeRules rules) {
        return compute(input, rules, RealmContext.NEUTRAL);
    }

    public static AttributeSnapshot compute(List<Contribution> input, AttributeRules rules, RealmContext realm) {
        int nAttr = Attribute.values().length, nClass = WeaponClass.values().length, nStat = Stat.values().length;
        double[] general = new double[nAttr];
        double[] classPower = new double[nClass];
        double[] stat = new double[nStat];
        double[] classDamage = new double[nClass];
        double[] classAttackSpeed = new double[nClass];
        double[] yieldFortune = new double[Yield.values().length];
        List<Contribution> kept = new ArrayList<>(input.size());
        int ignored = 0;

        for (Contribution c : input) {
            if (c == null || !Double.isFinite(c.amount())) { ignored++; continue; }
            kept.add(c);
            if (c.attribute() != null) {
                // Weapon limits only mean something for Power; elsewhere they count everywhere.
                if (c.attribute() == Attribute.POWER && c.weapon() != null) classPower[c.weapon().ordinal()] += c.amount();
                else if (c.attribute() == Attribute.FORTUNE && c.yield() != null) yieldFortune[c.yield().ordinal()] += c.amount();
                else general[c.attribute().ordinal()] += c.amount();
            } else if (c.stat() == Stat.DAMAGE && c.weapon() != null) {
                classDamage[c.weapon().ordinal()] += c.amount();
            } else if (c.stat() == Stat.ATTACK_SPEED && c.weapon() != null) {
                classAttackSpeed[c.weapon().ordinal()] += c.amount();
            } else {
                stat[c.stat().ordinal()] += c.amount();
            }
        }

        double[] totals = new double[nAttr];
        for (Attribute a : Attribute.values()) totals[a.ordinal()] = clamp(general[a.ordinal()], 0, rules.cap(a));

        double powerCap = rules.cap(Attribute.POWER);
        double[] power = new double[nClass];
        double[] damageBonus = new double[nClass];
        for (WeaponClass w : WeaponClass.values()) {
            power[w.ordinal()] = clamp(general[Attribute.POWER.ordinal()] + classPower[w.ordinal()], 0, powerCap);
            // A damage penalty can at most cancel the bonus side, never make hits heal.
            damageBonus[w.ordinal()] = Math.max(-0.9, stat[Stat.DAMAGE.ordinal()] + classDamage[w.ordinal()]);
        }

        // Fortune counting on one yield (Mining's on ore, Woodcutting's on wood): the general part plus that yield's.
        double[] fortune = new double[Yield.values().length];
        for (Yield y : Yield.values()) {
            fortune[y.ordinal()] = clamp(general[Attribute.FORTUNE.ordinal()] + yieldFortune[y.ordinal()], 0, rules.cap(Attribute.FORTUNE));
        }

        AttributeRules.Derived d = rules.derived();
        AttributeRules.Limits lim = rules.limits();
        AttributeRules.Curves cv = rules.curves();
        double agility = totals[Attribute.AGILITY.ordinal()];
        double fort = totals[Attribute.FORTITUDE.ordinal()];
        double ess = totals[Attribute.ESSENCE.ordinal()];
        double vit = totals[Attribute.VITALITY.ordinal()];
        int agilityLevel = (int) Math.floor(agility);

        double critChance = clamp((agility > 0 ? ScalingFormula.compute(agilityLevel, cv.critChance()) : 0)
                + stat[Stat.CRIT_CHANCE.ordinal()], 0, lim.critChance());
        double critMult = clamp(ScalingFormula.compute(agilityLevel, cv.critMultiplier())
                + stat[Stat.CRIT_MULTIPLIER.ordinal()], 1.0, lim.critMultiplier());
        double lifesteal = clamp(stat[Stat.LIFESTEAL.ordinal()], 0, lim.lifesteal());

        double maxHp = floor(1.0 + d.vitalityHp() * vit + stat[Stat.MAX_HP.ordinal()]);
        double armor = clamp(d.fortitudeArmor() * fort + stat[Stat.ARMOR.ordinal()], 0, lim.armor());
        double bar = floor(1.0 + d.fortitudeDefenseBar() * fort + stat[Stat.DEFENSE_BAR.ordinal()]);
        double resist = clamp(d.fortitudeResist() * fort + stat[Stat.RESIST.ordinal()], 0, lim.resist());
        double mana = floor(1.0 + d.essenceMana() * ess + stat[Stat.MAX_MANA.ordinal()]);
        double regen = floor(1.0 + d.essenceRegen() * ess + stat[Stat.MANA_REGEN.ordinal()]);
        double spell = floor(1.0 + d.essenceSpell() * ess + stat[Stat.SPELL_DAMAGE.ordinal()]);

        // Speed and handling: driver attribute + skill contributions, then diminishing returns to the cap.
        AttributeRules.Secondary sec = rules.secondary();
        double[] secondaryRaw = new double[nStat], secondary = new double[nStat];
        for (Stat st : Stat.SECONDARY) {
            AttributeRules.SecondaryStat def = sec.get(st);
            double raw = totals[def.driver().ordinal()] * def.perPoint() + stat[st.ordinal()];
            secondaryRaw[st.ordinal()] = raw;
            secondary[st.ordinal()] = def.enabled() ? diminish(raw, def.cap()) : 0.0;
        }
        AttributeRules.SecondaryStat as = sec.get(Stat.ATTACK_SPEED);
        double[] attackSpeedRaw = new double[nClass], attackSpeed = new double[nClass];
        for (WeaponClass w : WeaponClass.values()) {
            double raw = secondaryRaw[Stat.ATTACK_SPEED.ordinal()] + classAttackSpeed[w.ordinal()];
            attackSpeedRaw[w.ordinal()] = raw;
            attackSpeed[w.ordinal()] = as.enabled() ? diminish(raw, as.cap()) : 0.0;
        }

        return new AttributeSnapshot(rules, realm, totals, power, damageBonus, critChance, critMult, lifesteal,
                maxHp, armor, bar, resist, mana, regen, spell, secondary, secondaryRaw, attackSpeed, attackSpeedRaw,
                fortune, kept, ignored);
    }

    /**
     * Diminishing returns toward {@code cap}: {@code cap x (1 - e^(-raw/cap))}. Slope 1 at zero, so
     * small bonuses count in full; raw = cap gives 63% of the cap, 2 x cap 86%; never above the cap.
     * A negative raw value (a slow) passes through, limited to -50%.
     */
    public static double diminish(double raw, double cap) {
        if (!Double.isFinite(raw) || !(cap > 0)) return 0.0;
        if (raw <= 0) return Math.max(-0.5, raw);
        return cap * (1.0 - Math.exp(-raw / cap));
    }

    private static double clamp(double v, double lo, double hi) {
        if (!Double.isFinite(v)) return v > 0 ? hi : lo;
        return Math.max(lo, Math.min(hi, v));
    }

    /** Multipliers never drop below ×0.1 (debuffs weaken, they never zero a stat out). */
    private static double floor(double v) {
        return Double.isFinite(v) ? Math.max(0.1, v) : 1.0;
    }
}
