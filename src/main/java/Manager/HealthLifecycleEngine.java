package Manager;

import java.util.UUID;

final class HealthLifecycleEngine {
    static final double DEFAULT_MAX_HP = 100.0;

    MutableState newState(UUID playerId) {
        return new MutableState(playerId);
    }

    void initializeForJoin(MutableState state) {
        state.maxHp = sanitizeMaxHp(state.maxHp);
        state.currentHp = state.maxHp;
        state.invulnerableUntil = 0L;
        state.lastDamageAt = 0L;
        state.pendingCustomDeath = false;
        state.lifecycleState = HealthLifecycleState.ALIVE;
    }

    void setCurrentHp(MutableState state, double health) {
        state.maxHp = sanitizeMaxHp(state.maxHp);
        state.currentHp = clamp(health, state.maxHp);
        if (state.currentHp > 0 && !state.pendingCustomDeath) {
            state.lifecycleState = HealthLifecycleState.ALIVE;
        }
    }

    void setMaxHp(MutableState state, double maxHp) {
        state.maxHp = sanitizeMaxHp(maxHp);
        state.currentHp = clamp(state.currentHp, state.maxHp);
    }

    DamageResolution applyResolvedDamage(MutableState state, double finalDamage, long now, long damageInvulnerabilityMs) {
        if (finalDamage <= 0) {
            return DamageResolution.noDamage(state.currentHp);
        }
        if (state.pendingCustomDeath
                || state.lifecycleState == HealthLifecycleState.DEAD
                || state.lifecycleState == HealthLifecycleState.RESPAWNING) {
            return DamageResolution.noDamage(state.currentHp);
        }
        if (now < state.invulnerableUntil) {
            return DamageResolution.noDamage(state.currentHp);
        }

        state.lastDamageAt = now;
        state.invulnerableUntil = now + Math.max(0L, damageInvulnerabilityMs);

        double damageToApply = Math.max(0.0, finalDamage);
        double remainingHp = Math.max(0.0, state.currentHp - damageToApply);
        boolean lethal = remainingHp <= 0.0;

        state.currentHp = remainingHp;
        if (!lethal) {
            state.lifecycleState = HealthLifecycleState.ALIVE;
        }

        return new DamageResolution(damageToApply, lethal, 0.0, remainingHp);
    }

    void heal(MutableState state, double amount) {
        if (amount <= 0) {
            return;
        }
        state.currentHp = clamp(state.currentHp + amount, state.maxHp);
        if (state.currentHp > 0.0 && !state.pendingCustomDeath) {
            state.lifecycleState = HealthLifecycleState.ALIVE;
        }
    }

    void clearRecentDamageState(MutableState state) {
        state.lastDamageAt = 0L;
        state.invulnerableUntil = 0L;
    }

    void markPendingDeath(MutableState state, long now) {
        state.currentHp = 0.0;
        state.lastDamageAt = now;
        state.invulnerableUntil = 0L;
        state.pendingCustomDeath = true;
        state.lifecycleState = HealthLifecycleState.DYING;
    }

    void confirmDeath(MutableState state) {
        state.currentHp = 0.0;
        state.invulnerableUntil = 0L;
        state.pendingCustomDeath = true;
        state.lifecycleState = HealthLifecycleState.DEAD;
    }

    void markRespawning(MutableState state) {
        state.lifecycleState = HealthLifecycleState.RESPAWNING;
        state.pendingCustomDeath = false;
    }

    void restoreAfterRespawn(MutableState state, long now, long respawnInvulnerabilityMs) {
        state.maxHp = sanitizeMaxHp(state.maxHp);
        state.currentHp = state.maxHp;
        state.lastDamageAt = 0L;
        state.invulnerableUntil = now + Math.max(0L, respawnInvulnerabilityMs);
        state.pendingCustomDeath = false;
        state.lifecycleState = HealthLifecycleState.ALIVE;
    }

    void applyRespawnInvincibility(MutableState state, long now, long respawnInvulnerabilityMs) {
        state.invulnerableUntil = now + Math.max(0L, respawnInvulnerabilityMs);
    }

    boolean restoreIfInvariantBroken(MutableState state, double vanillaHealth) {
        if (vanillaHealth <= 0.0 || state.pendingCustomDeath || state.currentHp > 0.0) {
            return false;
        }

        state.maxHp = sanitizeMaxHp(state.maxHp);
        state.currentHp = state.maxHp;
        state.lastDamageAt = 0L;
        state.invulnerableUntil = 0L;
        state.lifecycleState = HealthLifecycleState.ALIVE;
        return true;
    }

    private double sanitizeMaxHp(double maxHp) {
        return maxHp > 0.0 ? maxHp : DEFAULT_MAX_HP;
    }

    private double clamp(double health, double maxHp) {
        return Math.max(0.0, Math.min(health, maxHp));
    }

    static final class MutableState {
        private final UUID playerId;
        private double currentHp = DEFAULT_MAX_HP;
        private double maxHp = DEFAULT_MAX_HP;
        private long invulnerableUntil = 0L;
        private long lastDamageAt = 0L;
        private boolean pendingCustomDeath = false;
        private HealthLifecycleState lifecycleState = HealthLifecycleState.ALIVE;

        MutableState(UUID playerId) {
            this.playerId = playerId;
        }

        UUID playerId() {
            return playerId;
        }

        double currentHp() {
            return currentHp;
        }

        double maxHp() {
            return maxHp;
        }

        long invulnerableUntil() {
            return invulnerableUntil;
        }

        long lastDamageAt() {
            return lastDamageAt;
        }

        boolean pendingCustomDeath() {
            return pendingCustomDeath;
        }

        HealthLifecycleState lifecycleState() {
            return lifecycleState;
        }

        HealthStateSnapshot snapshot() {
            return new HealthStateSnapshot(
                    playerId,
                    currentHp,
                    maxHp,
                    invulnerableUntil,
                    lastDamageAt,
                    pendingCustomDeath,
                    lifecycleState
            );
        }
    }
}
