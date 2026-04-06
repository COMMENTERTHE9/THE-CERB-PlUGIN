package Manager;

/**
 * Carries state from the death handler to the respawn handler.
 * Stored in PlayerSession, consumed on respawn.
 * Enables future death penalties, death-recap, and partial effect persistence.
 */
public record DeathContext(
        double manaAtDeath,
        double healthAtDeath,
        MobContext killerMob,       // null if not killed by a registered mob
        long deathTimestamp
) {}
