package Manager;

import org.junit.jupiter.api.Test;

import java.util.PrimitiveIterator;
import java.util.Random;
import java.util.stream.DoubleStream;

import static org.junit.jupiter.api.Assertions.*;

/** Smithing's wear resistance: each point of wear avoided with the stat's chance (docs/design/SKILL_PROGRESSION.md). */
class WearResistanceTest {

    private static java.util.function.DoubleSupplier rolls(double... values) {
        PrimitiveIterator.OfDouble it = DoubleStream.of(values).iterator();
        return it::nextDouble;
    }

    @Test
    void eachPointIsRolledOnItsOwn() {
        assertEquals(1, SecondaryEffects.wearKept(1, 0.2, rolls(0.5)), "a roll above the chance: the wear lands");
        assertEquals(0, SecondaryEffects.wearKept(1, 0.2, rolls(0.1)), "below it: avoided");
        assertEquals(2, SecondaryEffects.wearKept(3, 0.2, rolls(0.9, 0.05, 0.3)), "three points, one avoided");
    }

    @Test
    void nothingToAvoidOrNoChanceKeepsTheWear() {
        assertEquals(5, SecondaryEffects.wearKept(5, 0, rolls()));
        assertEquals(5, SecondaryEffects.wearKept(5, Double.NaN, rolls()));
        assertEquals(5, SecondaryEffects.wearKept(5, -1, rolls()));
        assertEquals(0, SecondaryEffects.wearKept(0, 0.3, rolls()));
        assertEquals(0, SecondaryEffects.wearKept(4, 1.5, rolls(0.99, 0.99, 0.99, 0.99)), "a chance above 1 is certain");
    }

    @Test
    void aBigAmountKeepsItsExpectedShare() {
        assertEquals(80, SecondaryEffects.wearKept(100, 0.2, () -> { throw new AssertionError("no rolls past " + SecondaryEffects.WEAR_ROLLS); }));
    }

    @Test
    void overManyUsesTheShareAvoidedMatchesTheChance() {
        Random r = new Random(7);
        int kept = 0;
        for (int i = 0; i < 20_000; i++) kept += SecondaryEffects.wearKept(1, 0.2015, r::nextDouble);
        assertEquals(0.2015, 1 - kept / 20_000.0, 0.01, "Smithing 100: about a fifth of all wear avoided");
    }
}
