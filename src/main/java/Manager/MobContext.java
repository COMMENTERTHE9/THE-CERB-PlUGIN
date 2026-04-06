package Manager;

/**
 * Immutable result of a MobRegistry lookup.
 * Uses the unified {@link Tier} system for classification.
 */
public record MobContext(
        String mobId,
        Tier tier,
        double xpMultiplier,
        MatchReason matchReason
) {
    /** Default context for mobs not found in the registry. */
    public static final MobContext NORMAL =
            new MobContext("unknown", Tier.COMMON, 1.0, MatchReason.DEFAULT);

    /** Convenience — true if tier is EPIC or higher. */
    public boolean isBoss() { return tier.isAtLeast(Tier.EPIC); }

    /** Convenience — true if tier is RARE or higher (but below EPIC). */
    public boolean isRare() { return tier.isAtLeast(Tier.RARE); }
}
