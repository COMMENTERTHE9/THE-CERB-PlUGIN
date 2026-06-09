package Manager;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Locks the level-from-distance and the ×8 realm-curve health math.
 * Defaults: base=50, level-slope=0.05, blocks-per-level=150, max-level=100,
 * health-cap=1e14; tier mult COMMON=1, UNCOMMON=8, RARE=64, PRIMORDIAL=8^8.
 */
class MobHealthScalingTest {

    private final MobHealthScaling scaling = new MobHealthScaling();
    private static final double EPS = 1e-6;

    // ── levelForDistance ────────────────────────────────────────

    @Test
    void levelStartsAtOneNearSpawn() {
        assertEquals(1, scaling.levelForDistance(0));
        assertEquals(1, scaling.levelForDistance(149));
    }

    @Test
    void levelIncrementsEveryBlocksPerLevel() {
        assertEquals(2, scaling.levelForDistance(150));
        assertEquals(4, scaling.levelForDistance(450));
    }

    @Test
    void levelIsCappedAtMaxLevel() {
        assertEquals(100, scaling.levelForDistance(1_000_000));
        assertEquals(100, scaling.getMaxLevel());
    }

    // ── calculate (×8 realm curve) ──────────────────────────────

    @Test
    void levelOneCommonEqualsBaseAnchor() {
        assertEquals(50.0, scaling.calculate(Tier.COMMON, 1), EPS);
    }

    @Test
    void levelScalesByLevelSlope() {
        // 50 * 1 * (1 + 0.05*10) = 75
        assertEquals(75.0, scaling.calculate(Tier.COMMON, 11), EPS);
    }

    @Test
    void tierStepsByEightPerRealm() {
        double common = scaling.calculate(Tier.COMMON, 1);
        assertEquals(8.0, scaling.calculate(Tier.UNCOMMON, 1) / common, EPS);
        assertEquals(64.0, scaling.calculate(Tier.RARE, 1) / common, EPS);
    }

    @Test
    void nullTierTreatedAsCommon() {
        assertEquals(50.0, scaling.calculate(null, 1), EPS);
    }

    @Test
    void levelIsClampedInsideCalculate() {
        assertEquals(50.0, scaling.calculate(Tier.COMMON, -5), EPS);    // clamps to level 1
        assertEquals(297.5, scaling.calculate(Tier.COMMON, 999), EPS);  // clamps to 100: 50*5.95
    }

    @Test
    void primordialReachesBillionsNotTheOldFiveThousandCap() {
        double hp = scaling.calculate(Tier.PRIMORDIAL, 100);
        assertTrue(hp > 1_000_000_000.0, "PRIMORDIAL L100 should be billions, was " + hp);
        assertTrue(hp < 1.0e14, "stays under the sanity rail, was " + hp);
    }

    @Test
    void enabledByDefault() {
        assertTrue(scaling.isEnabled());
    }
}
