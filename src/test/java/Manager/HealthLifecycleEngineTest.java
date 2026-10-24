package Manager;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HealthLifecycleEngineTest {
    private final HealthLifecycleEngine engine = new HealthLifecycleEngine();

    @Test
    void nonLethalDamageUpdatesState() {
        HealthLifecycleEngine.MutableState state = engine.newState(UUID.randomUUID());
        engine.initializeForJoin(state);

        DamageResolution resolution = engine.applyResolvedDamage(state, 25.0, 1_000L, 250L);

        assertEquals(25.0, resolution.finalDamage());
        assertFalse(resolution.lethal());
        assertEquals(75.0, resolution.remainingHp());
        assertEquals(75.0, state.currentHp());
        assertEquals(1_000L, state.lastDamageAt());
        assertEquals(1_250L, state.invulnerableUntil());
        assertEquals(HealthLifecycleState.ALIVE, state.lifecycleState());
    }

    @Test
    void invulnerabilityWindowBlocksFollowUpHit() {
        HealthLifecycleEngine.MutableState state = engine.newState(UUID.randomUUID());
        engine.initializeForJoin(state);

        engine.applyResolvedDamage(state, 20.0, 1_000L, 250L);
        DamageResolution blocked = engine.applyResolvedDamage(state, 20.0, 1_100L, 250L);

        assertEquals(0.0, blocked.finalDamage());
        assertFalse(blocked.lethal());
        assertEquals(80.0, blocked.remainingHp());
        assertEquals(80.0, state.currentHp());
        assertEquals(1_000L, state.lastDamageAt());
    }

    @Test
    void lethalDamageRestoresCleanlyAfterRespawn() {
        HealthLifecycleEngine.MutableState state = engine.newState(UUID.randomUUID());
        engine.initializeForJoin(state);

        DamageResolution lethal = engine.applyResolvedDamage(state, 150.0, 1_000L, 250L);
        assertTrue(lethal.lethal());
        assertEquals(0.0, state.currentHp());

        engine.markPendingDeath(state, 1_000L);
        engine.confirmDeath(state);
        engine.markRespawning(state);
        engine.restoreAfterRespawn(state, 2_000L, 3_000L);

        assertEquals(100.0, state.currentHp());
        assertEquals(100.0, state.maxHp());
        assertFalse(state.pendingCustomDeath());
        assertEquals(HealthLifecycleState.ALIVE, state.lifecycleState());
        assertEquals(0L, state.lastDamageAt());
        assertEquals(5_000L, state.invulnerableUntil());
    }

    @Test
    void invariantRepairRestoresAlivePlayersWithZeroHp() {
        HealthLifecycleEngine.MutableState state = engine.newState(UUID.randomUUID());
        engine.initializeForJoin(state);
        engine.setCurrentHp(state, 0.0);

        boolean repaired = engine.restoreIfInvariantBroken(state, 20.0);

        assertTrue(repaired);
        assertEquals(100.0, state.currentHp());
        assertFalse(state.pendingCustomDeath());
        assertEquals(HealthLifecycleState.ALIVE, state.lifecycleState());
    }

    @Test
    void invariantRepairDoesNotOverridePendingDeath() {
        HealthLifecycleEngine.MutableState state = engine.newState(UUID.randomUUID());
        engine.initializeForJoin(state);
        engine.markPendingDeath(state, 1_000L);

        boolean repaired = engine.restoreIfInvariantBroken(state, 20.0);

        assertFalse(repaired);
        assertEquals(0.0, state.currentHp());
        assertTrue(state.pendingCustomDeath());
    }

    @Test
    void hitsInsideInvulnerabilityWindowAreMarkedSkipped() {
        HealthLifecycleEngine.MutableState state = engine.newState(UUID.randomUUID());
        engine.initializeForJoin(state);

        DamageResolution first = engine.applyResolvedDamage(state, 20.0, 1_000L, 250L);
        DamageResolution inWindow = engine.applyResolvedDamage(state, 20.0, 1_100L, 250L);
        DamageResolution fullyAbsorbed = engine.applyResolvedDamage(state, 0.0, 2_000L, 250L);

        assertFalse(first.skipped());
        assertTrue(inWindow.skipped());      // ignored: no XP, no damage display
        assertFalse(fullyAbsorbed.skipped()); // resolved but nothing got through: shows BLOCKED
        assertEquals(0.0, fullyAbsorbed.finalDamage());
    }
}
