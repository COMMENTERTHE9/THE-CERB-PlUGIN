package Attributes;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class KillXpTest {

    private static final KillXp.Params P = KillXp.Params.DEFAULTS;

    @Test
    void defaultsMatchTheDesignNumbers() {
        assertEquals(40.0, KillXp.compute(1, 1.0, 1, P), 1e-9);        // Common L1: unchanged early game
        assertEquals(334.0, KillXp.compute(50, 1.0, 50, P), 1e-9);     // Common L50
        assertEquals(668.0, KillXp.compute(50, 2.0, 50, P), 1e-9);     // Rare L50 (design ~300-600)
        assertEquals(9510.0, KillXp.compute(100, 15.0, 100, P), 1e-9); // Primordial L100
    }

    @Test
    void strongerEnemiesAlwaysPayMore() {
        double prev = 0;
        for (int lvl = 1; lvl <= 100; lvl++) {
            double xp = KillXp.compute(lvl, 1.0, 1, P);
            assertTrue(xp > prev, "level " + lvl);
            prev = xp;
        }
        assertTrue(KillXp.compute(10, 3.0, 1, P) > KillXp.compute(10, 2.0, 1, P));
    }

    @Test
    void antiGrindOnlyForMobsFarBelowTheSkill() {
        assertEquals(40.0, KillXp.compute(1, 1.0, 20, P), 1e-9);   // gap 19: full XP
        assertEquals(10.0, KillXp.compute(1, 1.0, 21, P), 1e-9);   // gap 20: x0.25
        assertTrue(KillXp.isGreyKill(5, 30, P));
        assertFalse(KillXp.isGreyKill(50, 30, P));
    }

    @Test
    void boundedAndSafeForGarbageInputs() {
        assertEquals(40.0, KillXp.compute(-5, 1.0, 0, P), 1e-9);              // level clamps to 1
        assertEquals(40.0, KillXp.compute(1, Double.NaN, 1, P), 1e-9);        // NaN multiplier -> 1
        assertEquals(0.0, KillXp.compute(1, -3.0, 1, P), 1e-9);               // negative -> 0
        assertEquals(P.maxPerKill(), KillXp.compute(Integer.MAX_VALUE, 1e300, 1, P), 1e-9); // ceiling
        // an infinite multiplier is invalid config, treated like NaN: neutral x1 (L10 Common = 94)
        assertEquals(94.0, KillXp.compute(10, Double.POSITIVE_INFINITY, 1, P), 1e-9);
    }
}
