package Gathering;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Woodcutting's techniques by level (docs/design/WOODCUTTING.md §6, §8). */
class WoodRulesTest {

    private final WoodRules r = WoodRules.DEFAULTS;

    @Test
    void fellingGrowsAtItsMilestones() {
        assertEquals(0, r.capacity(1));
        assertEquals(0, r.capacity(9));
        assertEquals(6, r.capacity(10));
        assertEquals(6, r.capacity(24));
        assertEquals(24, r.capacity(25));
        assertEquals(64, r.capacity(50));
        assertEquals(64, r.capacity(99));
        assertEquals(128, r.capacity(100));
        assertEquals(10, r.firstFellingLevel());
        assertNull(r.fellingName(9));
        assertEquals("Felling I", r.fellingName(10));
        assertEquals("Felling II", r.fellingName(49));
        assertEquals("Felling III", r.fellingName(50));
        assertEquals("Felling IV", r.fellingName(150));
    }

    @Test
    void giantsTheCanopyAndTheRest() {
        assertFalse(r.giantTrunks(49));
        assertTrue(r.giantTrunks(50));
        assertFalse(r.canopy(74));
        assertTrue(r.canopy(75));
        assertEquals(6_000, r.restMs(30, 20), "0.3 s per log");
        assertEquals(3_000, r.restMs(100, 20), "halved at 100");
        assertEquals(0, r.restMs(30, 0));
    }

    @Test
    void heartwoodRisesWithLevelAndNeverPassesCertain() {
        assertEquals(0.002, r.heartwoodChance(0.002, 0), 1e-12);
        assertEquals(0.004, r.heartwoodChance(0.002, 50), 1e-12);
        assertEquals(0.006, r.heartwoodChance(0.002, 100), 1e-12, "x3 at 100");
        assertEquals(1.0, r.heartwoodChance(0.9, 100));
        assertEquals(0, r.heartwoodChance(0, 100));
        assertEquals(0, r.heartwoodChance(Double.NaN, 100));
    }

    @Test
    void aLevelUpNamesWhatItOpened() {
        assertEquals(List.of("Felling I: Sneak while cutting a natural tree to bring down up to 6 logs"), r.openedBetween(9, 10));
        assertEquals(List.of("Felling II: Felling takes up to 24 logs"), r.openedBetween(24, 25));
        assertEquals(List.of("Felling III: Felling takes up to 64 logs, and giant 2×2 trunks"), r.openedBetween(49, 50));
        assertEquals(List.of("Canopy: a felled tree's natural leaves come down with it (their own drops)"), r.openedBetween(74, 75));
        assertEquals(List.of("Felling IV: Felling takes up to 128 logs; half the rest between fellings"), r.openedBetween(99, 100));
        assertEquals(List.of(), r.openedBetween(10, 24), "nothing new between milestones");
        assertEquals(2, r.openedBetween(8, 26).size(), "a big jump names each step");
        assertEquals(List.of(), r.openedBetween(30, 30));
    }
}
