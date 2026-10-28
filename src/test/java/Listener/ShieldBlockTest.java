package Listener;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * A raised shield keeps a creature's blow off the Cerberus health pool (found while checking Iron Reprisal, 2026-10-28:
 * the pool lost the full hit behind a raised shield). The server reports a block as the event's blocking share.
 */
class ShieldBlockTest {

    @Test
    void aShieldTakesTheWholeHitWhenItsShareIsTheWholeHit() {
        assertTrue(VirtualHealthListener.wholeBlocked(3.0, -3.0), "a vanilla shield blocks a blow from in front whole");
        assertTrue(VirtualHealthListener.wholeBlocked(3.0000000001, -3.0), "rounding in the server's float math still counts");
        assertTrue(VirtualHealthListener.wholeBlocked(12.5, -20.0), "more blocked than dealt is still the whole hit");
    }

    @Test
    void anythingLessReachesThePool() {
        assertFalse(VirtualHealthListener.wholeBlocked(3.0, 0.0), "no shield, or a blow from behind it");
        assertFalse(VirtualHealthListener.wholeBlocked(3.0, -0.0), "the server writes -0 when nothing is blocked");
        assertFalse(VirtualHealthListener.wholeBlocked(3.0, -1.5), "a part blocked (an item with its own blocking rules): the hit goes on");
        assertFalse(VirtualHealthListener.wholeBlocked(0.0, -3.0), "a hit already resolved to 0 (the pool took it) is not read as a block");
    }
}
