package Manager;

import Attributes.AttributeCalculator;
import Attributes.AttributeRules;
import Attributes.AttributeSnapshot;
import Attributes.SkillAttributes;
import Attributes.WeaponClass;
import Manager.ScalingFormula.Preset;
import Manager.ScalingFormula.ScalingParams;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * How general Weapon Mastery (+0.25 Power per level on every weapon) stacks with the specialized
 * weapon skills, at every level, against the controlled-hyperscaling limits (docs/ATTRIBUTES_ENGINE.md,
 * "Weapon Mastery and the specialized weapon skills"). Reads the shipped attributes.yml defaults and
 * combat.yml, so a tuning change that breaks a bound fails here.
 */
class WeaponMasteryStackingTest {

    private static final AttributeRules R = AttributeRules.DEFAULTS;
    private static final double EPS = 1e-9;
    private static final int[] LEVELS = {1, 25, 50, 75, 100};
    /** Specialized skill per weapon class (Weapon Mastery itself for axes, maces, tridents, spears). */
    private static final Map<WeaponClass, String> SPECIALIZED = Map.of(
            WeaponClass.SWORD, "Blade Mastery", WeaponClass.BOW, "Ranged Precision",
            WeaponClass.UNARMED, "Martial Expertise", WeaponClass.AXE, "Weapon Mastery",
            WeaponClass.MACE, "Weapon Mastery", WeaponClass.TRIDENT, "Weapon Mastery",
            WeaponClass.SPEAR, "Weapon Mastery");

    private static YamlConfiguration combat;
    private static double mobSlope;

    @BeforeAll
    static void loadCombatYml() throws Exception {
        try (InputStream in = WeaponMasteryStackingTest.class.getClassLoader().getResourceAsStream("combat.yml")) {
            combat = YamlConfiguration.loadConfiguration(new InputStreamReader(in, StandardCharsets.UTF_8));
        }
        mobSlope = combat.getDouble("mob-health-scaling.level-slope");
    }

    private static AttributeSnapshot snap(Map<String, Integer> levels) {
        return AttributeCalculator.compute(SkillAttributes.contributions(levels, R), R);
    }

    /** A level-L mob's HP multiplier inside a realm (combat.yml mob-health-scaling.level-slope). */
    private static double mobHp(int level) {
        return 1.0 + mobSlope * (level - 1);
    }

    @Test
    void generalMasteryAddsAQuarterPowerPerLevelOnTopOfTheSpecializedSkill() {
        for (int lv : LEVELS) {
            for (WeaponClass c : new WeaponClass[]{WeaponClass.SWORD, WeaponClass.BOW, WeaponClass.UNARMED}) {
                String skill = SPECIALIZED.get(c);
                assertEquals(lv, snap(Map.of(skill, lv)).power(c), EPS, skill + " " + lv);
                assertEquals(0.25 * lv, snap(Map.of("Weapon Mastery", lv)).power(c), EPS, "Weapon Mastery alone, " + c);
                assertEquals(1.25 * lv, snap(Map.of(skill, lv, "Weapon Mastery", lv)).power(c), EPS, "both, " + c);
            }
            // Weapon Mastery is the specialized skill of these four, so it gives them both parts.
            for (WeaponClass c : new WeaponClass[]{WeaponClass.AXE, WeaponClass.MACE, WeaponClass.TRIDENT, WeaponClass.SPEAR}) {
                assertEquals(1.25 * lv, snap(Map.of("Weapon Mastery", lv)).power(c), EPS, "Weapon Mastery alone, " + c);
            }
            assertEquals(0.25 * lv, snap(Map.of("Weapon Mastery", lv)).power(WeaponClass.OTHER), EPS);
        }
    }

    @Test
    void noWeaponGetsMoreThan125PowerFromSkills() {
        // Every weapon skill at 100: the most Power any class can reach from skills today.
        AttributeSnapshot all = snap(Map.of("Blade Mastery", 100, "Weapon Mastery", 100, "Ranged Precision", 100,
                "Martial Expertise", 100));
        for (WeaponClass c : SPECIALIZED.keySet()) {
            assertEquals(125, all.power(c), EPS, c + ": specialized 100 + general 25, never stacked twice");
            assertEquals(7.25, all.powerMultiplier(c), EPS);
        }
        assertEquals(25, all.power(WeaponClass.OTHER), EPS);
        assertTrue(125 < R.cap(Attributes.Attribute.POWER), "far under the Power safety cap");
    }

    @Test
    void theStackStaysWithin25PercentOfAnEqualLevelMob() {
        // Power tracks mob HP inside a realm: specialized skill L vs a level-L mob stays even.
        // The general bonus puts a two-skill player ahead, but never by more than 25%.
        double worst = 0;
        for (int lv = 1; lv <= 100; lv++) {
            double one = snap(Map.of("Blade Mastery", lv)).powerMultiplier(WeaponClass.SWORD);
            double two = snap(Map.of("Blade Mastery", lv, "Weapon Mastery", lv)).powerMultiplier(WeaponClass.SWORD);
            assertTrue(one / mobHp(lv) <= 1.0 + mobSlope + EPS, "one skill keeps pace (one level's worth), level " + lv);
            worst = Math.max(worst, two / mobHp(lv));
        }
        assertTrue(worst <= 1.25, "two skills: at most 25% ahead, was " + worst);
        assertEquals(7.25 / 5.95, worst, 1e-6);   // the largest gap is at level 100: 21.8%
    }

    @Test
    void critsStayInsideTheOffenseBudgetOnTopOfTheStack() {
        AttributeSnapshot s = snap(Map.of("Blade Mastery", 100, "Weapon Mastery", 100, "Critical Strike", 100,
                "Dual Wielding", 100, "Navigation", 100, "Stealth", 100));
        double base = s.powerMultiplier(WeaponClass.SWORD);
        assertTrue(s.critChance() <= R.limits().critChance() + EPS);
        assertTrue(s.critMultiplier() <= R.limits().critMultiplier() + EPS);
        assertTrue(s.offenseMultiplier(WeaponClass.SWORD, true) <= base * R.limits().offenseBudget() + EPS);
        assertTrue(s.expectedOffenseMultiplier(WeaponClass.SWORD) <= base * R.limits().offenseBudget() + EPS);
    }

    /** PvP still uses the older additive skill bonuses (unchanged by decision 1); pinned so the PvP pass starts from facts. */
    @Test
    void pvpLegacyBonusesAddUpAndStayUnderTheirCaps() {
        ScalingParams bm = CombatConfig.scalingFrom(combat.getConfigurationSection("skills.blade-mastery.damage-bonus"),
                Preset.STEADY, "skills.blade-mastery.damage-bonus");
        ScalingParams wm = CombatConfig.scalingFrom(combat.getConfigurationSection("skills.weapon-mastery.damage-multiplier"),
                Preset.LATE_SURGE, "skills.weapon-mastery.damage-multiplier");
        double bm100 = ScalingFormula.compute(100, bm);
        double wm1 = ScalingFormula.compute(1, wm) - 1.0;
        double wm100 = ScalingFormula.compute(100, wm) - 1.0;
        assertEquals(0.18, wm1, 0.005);       // +18% on every PvP hit from Weapon Mastery level 1
        assertEquals(0.95, bm100, 0.01);      // swords only
        assertEquals(1.32, wm100, 0.01);      // any weapon, spells included
        double swordBoth = 1.0 + bm100 + wm100;
        assertEquals(3.27, swordBoth, 0.02);  // additive: x3.27 before crits at level 100
        assertTrue(swordBoth <= 1.0 + bm.hardCap() + (wm.hardCap() - 1.0));
    }
}
