package Skills;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Locks the rewritten XP curve: the old 100 * 2^(level-1) overflowed int around
 * level 26 and bricked progression. The replacement must be positive, monotonic,
 * overflow-safe, and reach a sane cumulative total at level 100.
 */
class SkillXpCurveTest {

    @Test
    void alwaysPositiveAcrossTheRange() {
        for (int l = 1; l <= 100; l++) {
            assertTrue(SkillManager.xpForLevel(l) > 0,
                    "level " + l + " cost must be positive");
        }
    }

    @Test
    void strictlyMonotonic() {
        int prev = 0;
        for (int l = 1; l <= 100; l++) {
            int cost = SkillManager.xpForLevel(l);
            assertTrue(cost > prev, "cost must increase: level " + l + " = " + cost + " not > " + prev);
            prev = cost;
        }
    }

    @Test
    void theOldOverflowLevelIsNowSane() {
        // Old formula wrapped NEGATIVE at level 26; the new curve must be a sane positive.
        int cost26 = SkillManager.xpForLevel(26);
        assertTrue(cost26 > 0 && cost26 < 1_000_000, "L26 cost should be sane, was " + cost26);
    }

    @Test
    void anchorAndClampValues() {
        assertEquals(115, SkillManager.xpForLevel(1));   // 75*1 + 40
        assertEquals(115, SkillManager.xpForLevel(0));   // clamps to 1
        assertEquals(115, SkillManager.xpForLevel(-5));  // clamps to 1
    }

    @Test
    void cumulativeToHundredIsReasonable() {
        long total = 0;
        for (int l = 1; l < 100; l++) total += SkillManager.xpForLevel(l);
        assertTrue(total > 5_000_000 && total < 40_000_000, "cumulative to 100 was " + total);
    }
}
