package Manager;

import java.util.UUID;

public record HealthStateSnapshot(
        UUID playerId,
        double currentHp,
        double maxHp,
        long invulnerableUntil,
        long lastDamageAt,
        boolean pendingCustomDeath,
        HealthLifecycleState lifecycleState
) {}
