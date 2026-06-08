package Manager;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Locks in the level-from-distance and tier/level-to-health math.
 * Uses the hardcoded defaults (no combat.yml needed):
 * base=20, hp-per-level=6, blocks-per-level=150, max-level=100, cap=5000,
 * tier mult COMMON=1.0, RARE=3.0, PRIMORDIAL=60.0.
 */
class MobHealthScalingTest {

    private final MobHealthScaling scaling = new MobHealthScaling();
    private static final double EPS = 1e-9;

    // ── levelForDistance ────────────────────────────────────────

    @Test
    void levelStartsAtOneNearSpawn() {
        assertEquals(1, scaling.levelForDistance(0));
        assertEquals(1, scaling.levelForDistance(149));   // floor(149/150)=0 -> level 1
    }

    @Test
    void levelIncrementsEveryBlocksPerLevel() {
        assertEquals(2, scaling.levelForDistance(150));   // exactly one band out
        assertEquals(4, scaling.levelForDistance(450));   // floor(450/150)=3 -> level 4
    }

    @Test
    void levelIsCappedAtMaxLevel() {
        assertEquals(100, scaling.levelForDistance(1_000_000));
        assertEquals(100, scaling.getMaxLevel());
    }

    // ── calculate ───────────────────────────────────────────────

    @Test
    void levelOneCommonEqualsBaseHealth() {
        assertEquals(20.0, scaling.calculate(Tier.COMMON, 1), EPS);
    }

    @Test
    void healthScalesLinearlyWithLevel() {
        // 20 + (11-1)*6 = 80
        assertEquals(80.0, scaling.calculate(Tier.COMMON, 11), EPS);
    }

    @Test
    void tierMultipliesHealth() {
        // COMMON level 11 = 80, RARE is 3x
        assertEquals(240.0, scaling.calculate(Tier.RARE, 11), EPS);
    }

    @Test
    void nullTierTreatedAsCommon() {
        assertEquals(20.0, scaling.calculate(null, 1), EPS);
    }

    @Test
    void levelIsClampedInsideCalculate() {
        // Level below 1 clamps to 1; absurd level clamps to max-level (100)
        assertEquals(20.0, scaling.calculate(Tier.COMMON, -5), EPS);
        // 20 + (100-1)*6 = 614
        assertEquals(614.0, scaling.calculate(Tier.COMMON, 999), EPS);
    }

    @Test
    void healthCapIsEnforced() {
        // PRIMORDIAL lvl 100 = 614 * 60 = 36840, capped to 5000
        assertEquals(5000.0, scaling.calculate(Tier.PRIMORDIAL, 100), EPS);
    }

    @Test
    void enabledByDefault() {
        assertTrue(scaling.isEnabled());
    }
}
