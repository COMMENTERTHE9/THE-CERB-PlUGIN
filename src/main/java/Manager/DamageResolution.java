package Manager;

/**
 * Outcome of resolving one hit against a player's custom HP.
 *
 * @param skipped true when the hit was ignored entirely (inside the invulnerability window,
 *                already dying/dead, or respawning). Skipped hits must not award XP or spawn
 *                damage displays: fire, lava and cactus fire a damage event every tick, and
 *                most of those land inside the window.
 */
public record DamageResolution(
        double finalDamage,
        boolean lethal,
        double absorbedByDefense,
        double remainingHp,
        boolean skipped
) {
    /** A resolved (not skipped) hit. */
    public DamageResolution(double finalDamage, boolean lethal, double absorbedByDefense, double remainingHp) {
        this(finalDamage, lethal, absorbedByDefense, remainingHp, false);
    }

    /** The hit was ignored entirely (invulnerable, dying/dead, or respawning). */
    public static DamageResolution noDamage(double remainingHp) {
        return new DamageResolution(0.0, false, 0.0, remainingHp, true);
    }

    /** The hit was resolved but nothing got through (e.g. fully absorbed by defenses). */
    public static DamageResolution zero(double remainingHp) {
        return new DamageResolution(0.0, false, 0.0, remainingHp, false);
    }
}
