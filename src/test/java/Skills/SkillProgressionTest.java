package Skills;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SkillProgressionTest {

    @Test
    void smallAwardAccumulatesWithoutLevelUp() {
        SkillProgression.Result r = SkillProgression.award(1, 10, 20);
        assertEquals(1, r.level());
        assertEquals(30, r.xp());
        assertEquals(0, r.levelsGained());
    }

    @Test
    void reachingRequirementLevelsUpAndCarriesExcess() {
        int req = SkillManager.xpForLevel(1);
        SkillProgression.Result r = SkillProgression.award(1, req - 5, 15);
        assertEquals(2, r.level());
        assertEquals(10, r.xp());
        assertEquals(1, r.levelsGained());
    }

    @Test
    void largeAwardGainsSeveralLevels() {
        long needed = (long) SkillManager.xpForLevel(1) + SkillManager.xpForLevel(2);
        SkillProgression.Result r = SkillProgression.award(1, 0, needed);
        assertEquals(3, r.level());
        assertEquals(0, r.xp());
        assertEquals(2, r.levelsGained());
    }

    @Test
    void hugeAwardNeverWrapsNegativeAndStopsAtCap() {
        // The old bug: 5e8 XP per kill wrapped int XP negative after ~5 kills.
        SkillProgression.Result r = SkillProgression.award(1, Integer.MAX_VALUE - 10, 1.0e30);
        assertTrue(r.xp() >= 0);
        assertTrue(r.level() <= SkillProgression.MAX_LEVEL);
    }

    @Test
    void levelCapIsReachableAndNeverExceeded() {
        int req = SkillManager.xpForLevel(99);
        SkillProgression.Result atCap = SkillProgression.award(99, req - 1, 1);
        assertEquals(SkillProgression.MAX_LEVEL, atCap.level());
        assertEquals(0, atCap.xp());

        SkillProgression.Result past = SkillProgression.award(SkillProgression.MAX_LEVEL, 0, 1_000_000);
        assertEquals(SkillProgression.MAX_LEVEL, past.level());
        assertEquals(0, past.xp());
        assertEquals(0, past.levelsGained());

        SkillProgression.Result corrupt = SkillProgression.award(250, 7, 1);
        assertEquals(SkillProgression.MAX_LEVEL, corrupt.level());
    }

    @Test
    void garbageGainsAddNothing() {
        for (double bad : new double[]{Double.NaN, -50, Double.NEGATIVE_INFINITY, 0}) {
            SkillProgression.Result r = SkillProgression.award(3, 40, bad);
            assertEquals(3, r.level());
            assertEquals(40, r.xp());
        }
    }

    @Test
    void negativeStoredXpFromOldOverflowIsRepaired() {
        SkillProgression.Result r = SkillProgression.award(4, -2_000_000_000, 5);
        assertEquals(4, r.level());
        assertEquals(5, r.xp());
        assertEquals(0, SkillProgression.clampXp(-1));
        assertEquals(SkillProgression.MIN_LEVEL, SkillProgression.clampLevel(-3));
        assertEquals(SkillProgression.MAX_LEVEL, SkillProgression.clampLevel(9999));
    }

    @Test
    void everySkillStartsAtLevelOneAndAnOldLevelZeroKeepsItsXp() {
        // Versions before 2026-10-27 wrote a new character's skills at level 0 (the owner's Fishing "level 0, 0 / 115").
        assertEquals(1, SkillProgression.MIN_LEVEL);
        assertEquals(1, SkillProgression.clampLevel(0));
        assertEquals(1, SkillProgression.clampLevel(1));
        assertEquals(100, SkillProgression.sanitizeStoredXp(0, 100), "XP earned at level 0 counts at level 1: both need 115");
        assertEquals(SkillManager.xpForLevel(0), SkillManager.xpForLevel(1));

        SkillProgression.Result kept = SkillProgression.award(0, 100, 0);
        assertEquals(1, kept.level());
        assertEquals(100, kept.xp());
        assertEquals(0, kept.levelsGained(), "reading an old level 0 is no level-up");

        SkillProgression.Result next = SkillProgression.award(0, 100, 20); // 120 of the 115 level 1 needs
        assertEquals(2, next.level());
        assertEquals(5, next.xp());
        assertEquals(1, next.levelsGained());
    }

    @Test
    void killXpIgnoresOverkillSentinel() {
        // A scaled mob has a cosmetic 40-HP bar; the guaranteed-kill hit is 1e9.
        double sentinel = SkillProgression.killXp(40, 1.0e9, 1.0);
        double exact = SkillProgression.killXp(40, 40, 1.0);
        assertEquals(exact, sentinel, 1e-9);
        assertEquals(40.0, sentinel, 1e-9);
    }

    @Test
    void killXpMatchesDesignFormulaForNormalHits() {
        // (maxHp * 0.5 + damage * 0.5) * multiplier
        assertEquals((20 * 0.5 + 6 * 0.5) * 2.0, SkillProgression.killXp(20, 6, 2.0), 1e-9);
    }

    @Test
    void killXpSanitizesNonFiniteInputs() {
        assertEquals(0.0, SkillProgression.killXp(Double.NaN, 5, 1), 1e-9);
        assertEquals(0.0, SkillProgression.killXp(20, 5, Double.NaN), 1e-9);
        assertTrue(Double.isFinite(SkillProgression.killXp(20, Double.POSITIVE_INFINITY, 1)));
        assertEquals(10.0, SkillProgression.killXp(20, -5, 1), 1e-9);
    }

    @Test
    void storedOverflowXpCanGrantAtMostOneLevel() {
        int req = SkillManager.xpForLevel(1);
        assertEquals(req - 1, SkillProgression.sanitizeStoredXp(1, 500_000_002));
        assertEquals(0, SkillProgression.sanitizeStoredXp(1, -7));
        assertEquals(50, SkillProgression.sanitizeStoredXp(1, 50));
        assertEquals(0, SkillProgression.sanitizeStoredXp(SkillProgression.MAX_LEVEL, 999));
        SkillProgression.Result next = SkillProgression.award(1, SkillProgression.sanitizeStoredXp(1, 500_000_002), 40);
        assertEquals(2, next.level());
    }
}
