package Attributes;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** Speed and handling stats: the diminishing-returns rule, the engine wiring and the config. */
class SecondaryStatsTest {

    private static final AttributeRules R = AttributeRules.DEFAULTS;

    private static AttributeSnapshot skills(Map<String, Integer> levels) {
        return AttributeCalculator.compute(SkillAttributes.contributions(levels, R), R);
    }

    @Test
    void diminishingReturnsAreLinearEarlyAndNeverPassTheCap() {
        assertEquals(0.0, AttributeCalculator.diminish(0, 0.25), 1e-12);
        assertEquals(0.001, AttributeCalculator.diminish(0.001, 0.25), 1e-5);          // small bonuses count in full
        assertEquals(0.25 * (1 - Math.exp(-1)), AttributeCalculator.diminish(0.25, 0.25), 1e-12); // raw = cap -> 63%
        assertTrue(AttributeCalculator.diminish(100, 0.25) <= 0.25);
        assertEquals(-0.5, AttributeCalculator.diminish(-3, 0.25), 1e-12);             // slows pass through, limited
        assertEquals(0.0, AttributeCalculator.diminish(Double.NaN, 0.25), 1e-12);
    }

    @Test
    void bladeMasteryRaisesSwordAttackSpeedOnly() {
        AttributeSnapshot s = skills(Map.of("Blade Mastery", 100));
        assertEquals(0.30, s.attackSpeedRaw(WeaponClass.SWORD), 1e-9);
        assertEquals(AttributeCalculator.diminish(0.30, 0.25), s.attackSpeed(WeaponClass.SWORD), 1e-12);
        assertEquals(0.0, s.attackSpeed(WeaponClass.AXE), 1e-12);
    }

    @Test
    void attackIntervalsMatchTheProposal() {
        // A sword's own speed is 1.6/s (12.5 ticks); the doc says 12.5 -> 11.2 -> 10.6 ticks at Blade Mastery 1/50/100.
        double[] ticks = new double[3];
        int[] levels = {1, 50, 100};
        for (int i = 0; i < 3; i++) {
            double bonus = skills(Map.of("Blade Mastery", levels[i])).attackSpeed(WeaponClass.SWORD);
            ticks[i] = 20 / (1.6 * (1 + bonus));
        }
        assertEquals(12.5, ticks[0], 0.05);
        assertEquals(11.2, ticks[1], 0.05);
        assertEquals(10.6, ticks[2], 0.05);
        // Even maxed out, a sword never swings faster than the 10-tick hit-immunity window.
        AttributeSnapshot max = AttributeCalculator.compute(List.of(
                Contribution.attribute("t", "t", Attribute.AGILITY, 300, null),
                Contribution.stat("t", "t", Stat.ATTACK_SPEED, 5.0, WeaponClass.SWORD)), R);
        assertTrue(20 / (1.6 * (1 + max.attackSpeed(WeaponClass.SWORD))) >= 10.0);
    }

    @Test
    void agilityRaisesEverySpeedALittle() {
        AttributeSnapshot s = AttributeCalculator.compute(List.of(Contribution.attribute("t", "t", Attribute.AGILITY, 100, null)), R);
        assertEquals(0.10, s.attackSpeedRaw(WeaponClass.AXE), 1e-9);
        assertEquals(0.10, s.secondaryRaw(Stat.DRAW_SPEED), 1e-9);
        assertEquals(0.05, s.secondaryRaw(Stat.MOVE_SPEED), 1e-9);
        assertEquals(0.0, s.secondaryRaw(Stat.COOLDOWN_RECOVERY), 1e-9); // driven by Essence
    }

    @Test
    void ownerSkillsRaiseTheirStat() {
        AttributeSnapshot s = skills(Map.of("Ranged Precision", 50, "Navigation", 100, "Spell Weaving", 100));
        // Ranged Precision 50: +0.2; Pathfinding's 25 Agility adds 25 x 0.001 to draw speed as well
        assertEquals(0.225, s.secondaryRaw(Stat.DRAW_SPEED), 1e-9);
        assertEquals(AttributeCalculator.diminish(0.225, 0.50), s.secondary(Stat.DRAW_SPEED), 1e-12);
        // Pathfinding (Navigation) 100: +0.1 from the skill, +25 Agility x 0.0005 from its Agility weight
        assertEquals(0.1125, s.secondaryRaw(Stat.MOVE_SPEED), 1e-9);
        // Spell Weaving 100: +0.3 directly, +50 Essence x 0.002
        assertEquals(0.40, s.secondaryRaw(Stat.COOLDOWN_RECOVERY), 1e-9);
    }

    @Test
    void aSwitchedOffStatIsZeroButStillShowsItsSources() {
        AttributeRules.Secondary d = AttributeRules.DEFAULT_SECONDARY;
        Map<Stat, AttributeRules.SecondaryStat> stats = new java.util.EnumMap<>(Stat.class);
        stats.putAll(d.stats());
        stats.put(Stat.DRAW_SPEED, new AttributeRules.SecondaryStat(false, Attribute.AGILITY, 0.001, 0.5));
        AttributeRules off = new AttributeRules(R.skillWeights(), R.caps(), R.derived(), R.limits(), R.curves(), R.switches(),
                new AttributeRules.Secondary(stats, d.skillWeights(), d.inPvp(), d.pvpTagSeconds()));
        AttributeSnapshot s = AttributeCalculator.compute(SkillAttributes.contributions(Map.of("Ranged Precision", 50), off), off);
        assertEquals(0.0, s.secondary(Stat.DRAW_SPEED), 1e-12);
        assertEquals(0.20, s.secondaryRaw(Stat.DRAW_SPEED), 1e-9);
    }

    @Test
    void configOverridesAndRejectsBadValues() {
        YamlConfiguration y = new YamlConfiguration();
        y.set("in-pvp", true);
        y.set("pvp-tag-seconds", 9999);
        y.set("stats.attack-speed.cap", 0.10);
        y.set("stats.draw-speed.driver", "nonsense");
        y.set("skills.Blade Mastery", List.of(Map.of("stat", "attack-speed", "per-level", 0.01, "weapon", "sword")));
        y.set("skills.Mining", List.of(Map.of("stat", "power", "per-level", 1.0)));
        List<String> problems = new ArrayList<>();
        AttributeRules.Secondary s = AttributesConfig.parseSecondary(y, problems::add);
        assertTrue(s.inPvp());
        assertEquals(15, s.pvpTagSeconds());                                  // out of range -> default
        assertEquals(0.10, s.get(Stat.ATTACK_SPEED).cap(), 1e-12);
        assertEquals(Attribute.AGILITY, s.get(Stat.DRAW_SPEED).driver());      // bad driver -> default
        assertEquals(0.01, s.skillWeights().get("Blade Mastery").get(0).perLevel(), 1e-12);
        assertFalse(s.skillWeights().containsKey("Mining"));                  // power is not a speed stat
        assertEquals(3, problems.size(), problems.toString());
    }

    @Test
    void bowPowerFollowsVanillaAndDrawSpeedShortensTheDraw() {
        assertEquals(1.0, BowDraw.powerFor(1.0), 1e-12);
        assertEquals((0.25 + 1.0) / 3, BowDraw.powerFor(0.5), 1e-12);
        assertEquals(0.5, BowDraw.secondsFor(BowDraw.powerFor(0.5)), 1e-12);
        assertEquals(0.4, BowDraw.boosted(0.4, 0), 1e-12);                     // no bonus, no change
        assertEquals(1.0, BowDraw.boosted(BowDraw.powerFor(0.5), 1.0), 1e-12); // twice as fast: half a second is full
        assertEquals(BowDraw.powerFor(0.6), BowDraw.boosted(BowDraw.powerFor(0.5), 0.2), 1e-12);
        assertEquals(1.0, BowDraw.boosted(1.0, 0.5), 1e-12);
        assertEquals(0.8, BowDraw.fullDrawSeconds(0.25), 1e-12);
    }
}
