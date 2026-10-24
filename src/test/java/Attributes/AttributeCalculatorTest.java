package Attributes;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class AttributeCalculatorTest {

    private static final AttributeRules R = AttributeRules.DEFAULTS;

    private static AttributeSnapshot of(Contribution... cs) {
        return AttributeCalculator.compute(List.of(cs), R);
    }

    private static Contribution attr(Attribute a, double amount, WeaponClass w) {
        return Contribution.attribute("test", "test", a, amount, w);
    }

    private static Contribution stat(Stat s, double amount) {
        return Contribution.stat("test", "test", s, amount, null);
    }

    @Test
    void emptyIsIdentity() {
        AttributeSnapshot s = AttributeSnapshot.empty(R);
        for (Attribute a : Attribute.values()) assertEquals(0, s.get(a));
        for (WeaponClass w : WeaponClass.values()) {
            assertEquals(1.0, s.powerMultiplier(w));
            assertEquals(1.0, s.offenseMultiplier(w, false));
        }
        assertEquals(0, s.critChance());
        assertEquals(0, s.lifesteal());
        assertEquals(1.0, s.maxHpMultiplier());
        assertEquals(1.0, s.defenseBarMultiplier());
        assertEquals(0, s.armorBonus());
        assertEquals(1.0, s.maxManaMultiplier());
        assertEquals(1.0, s.spellDamageMultiplier());
    }

    @Test
    void weaponLimitedPowerOnlyAppliesToThatWeapon() {
        AttributeSnapshot s = of(attr(Attribute.POWER, 50, WeaponClass.SWORD), attr(Attribute.POWER, 10, null));
        assertEquals(60, s.power(WeaponClass.SWORD));
        assertEquals(10, s.power(WeaponClass.AXE));
        assertEquals(10, s.get(Attribute.POWER), "the attribute total is the all-weapon part");
        assertEquals(1 + 0.05 * 60, s.powerMultiplier(WeaponClass.SWORD), 1e-9);
    }

    /** The design's within-realm invariant: a weapon skill at level L keeps pace with a level-L mob's HP. */
    @Test
    void equalLevelFightsKeepTheSameHitCount() {
        for (int level = 1; level <= 100; level++) {
            Map<String, Integer> skills = Map.of("Blade Mastery", level);
            AttributeSnapshot s = AttributeCalculator.compute(SkillAttributes.contributions(skills, R), R);
            double damage = s.powerMultiplier(WeaponClass.SWORD);
            double mobHp = 1 + 0.05 * (level - 1); // combat.yml mob-health-scaling level-slope
            double ratio = damage / mobHp;
            assertTrue(ratio >= 1.0 && ratio <= 1.06, "level " + level + ": damage/HP ratio " + ratio);
        }
    }

    @Test
    void attributeCapsHold() {
        AttributeSnapshot s = of(attr(Attribute.POWER, 1e9, null), attr(Attribute.VITALITY, 1e9, null),
                attr(Attribute.AGILITY, 1e9, null), attr(Attribute.POWER, 1e9, WeaponClass.SWORD));
        assertEquals(R.cap(Attribute.POWER), s.get(Attribute.POWER));
        assertEquals(R.cap(Attribute.POWER), s.power(WeaponClass.SWORD));
        assertEquals(R.cap(Attribute.VITALITY), s.get(Attribute.VITALITY));
        assertEquals(R.cap(Attribute.AGILITY), s.get(Attribute.AGILITY));
    }

    @Test
    void derivedLimitsHoldNoMatterWhatIsStacked() {
        List<Contribution> cs = new ArrayList<>();
        for (Stat st : Stat.values()) cs.add(stat(st, 1e6));
        for (Attribute a : Attribute.values()) cs.add(attr(a, 1e6, null));
        AttributeSnapshot s = AttributeCalculator.compute(cs, R);
        AttributeRules.Limits lim = R.limits();
        assertEquals(lim.critChance(), s.critChance());
        assertEquals(lim.critMultiplier(), s.critMultiplier());
        assertEquals(lim.lifesteal(), s.lifesteal());
        assertEquals(lim.armor(), s.armorBonus());
        assertEquals(lim.resist(), s.resist());
        assertEquals(lim.armor(), s.totalArmor(0.35), "gear + Fortitude armor stays under the armor limit");
        for (WeaponClass w : WeaponClass.values()) {
            double baseline = s.powerMultiplier(w);
            assertEquals(baseline * lim.offenseBudget(), s.offenseMultiplier(w, true), 1e-6,
                    "crit + damage bonuses never exceed the offense budget over the Power baseline");
            assertTrue(s.expectedOffenseMultiplier(w) <= baseline * lim.offenseBudget() + 1e-9);
        }
    }

    @Test
    void critComesFromAgilityThroughTheCombatCurves() {
        AttributeSnapshot none = of();
        AttributeSnapshot low = of(attr(Attribute.AGILITY, 1, null));
        AttributeSnapshot high = of(attr(Attribute.AGILITY, 100, null));
        AttributeSnapshot max = of(attr(Attribute.AGILITY, 300, null));
        assertEquals(0, none.critChance(), "no Agility, no crits");
        assertTrue(low.critChance() > 0.10 && low.critChance() < 0.15, "Agility 1 ~ Critical Strike 1: " + low.critChance());
        assertTrue(high.critChance() > 0.70 && high.critChance() < 0.75, "Agility 100 ~ Critical Strike 100: " + high.critChance());
        assertEquals(0.75, max.critChance(), 1e-9, "stacking Agility past 100 stops at the crit-chance cap");
        assertTrue(high.critMultiplier() > low.critMultiplier());
        assertTrue(high.critMultiplier() <= 3.5);
        double crit = high.offenseMultiplier(WeaponClass.SWORD, true);
        assertEquals(high.critMultiplier(), crit, 1e-9, "with no Power, a crit is exactly the crit multiplier");
    }

    @Test
    void garbageInputsAreDroppedAndOutputsStayFinite() {
        AttributeSnapshot s = of(attr(Attribute.POWER, Double.NaN, null), attr(Attribute.POWER, Double.POSITIVE_INFINITY, WeaponClass.SWORD),
                stat(Stat.CRIT_CHANCE, Double.NEGATIVE_INFINITY), attr(Attribute.VITALITY, 20, null));
        assertEquals(3, s.ignoredContributions());
        assertEquals(1, s.contributions().size());
        assertEquals(0, s.power(WeaponClass.SWORD));
        assertEquals(1.2, s.maxHpMultiplier(), 1e-9);
        assertTrue(Double.isFinite(s.totalArmor(Double.NaN)));
    }

    @Test
    void negativeContributionsNeverGoBelowZeroOrZeroAStatOut() {
        AttributeSnapshot s = of(attr(Attribute.POWER, -500, null), attr(Attribute.VITALITY, -500, null),
                stat(Stat.MAX_HP, -50), stat(Stat.DAMAGE, -50));
        assertEquals(0, s.get(Attribute.POWER));
        assertEquals(1.0, s.powerMultiplier(WeaponClass.SWORD));
        assertEquals(0.1, s.maxHpMultiplier(), 1e-9, "max HP never drops below x0.1");
        assertTrue(s.offenseMultiplier(WeaponClass.SWORD, false) > 0, "a hit always does some damage");
    }

    @Test
    void sourcesAreListedLargestFirst() {
        AttributeSnapshot s = of(Contribution.attribute("a", "Small", Attribute.VITALITY, 5, null),
                Contribution.attribute("b", "Big", Attribute.VITALITY, 50, null),
                Contribution.attribute("c", "Other", Attribute.POWER, 99, null));
        assertEquals(List.of("Big", "Small"), s.sources(Attribute.VITALITY).stream().map(Contribution::label).toList());
    }

    @Test
    void contributionNeedsExactlyOneTarget() {
        assertThrows(IllegalArgumentException.class, () -> new Contribution("x", "x", null, null, 1, null));
        assertThrows(IllegalArgumentException.class, () -> new Contribution("x", "x", Attribute.POWER, Stat.DAMAGE, 1, null));
    }
}
