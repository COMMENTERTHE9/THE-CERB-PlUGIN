package Attributes;

import java.util.Locale;
import java.util.Map;

/**
 * What can't be farmed (attributes.yml {@code xp-integrity}; docs/STATUS.md, "XP integrity"). Pure, unit-tested.
 *
 * <ul>
 *   <li>{@code byOrigin}: a mob's spawn reason scales its kill XP and every per-hit XP it pays. A reason that is not
 *       listed pays in full; a farm's mob (a monster spawner, a bred animal, a spawn egg) pays a share or nothing.</li>
 *   <li>{@code pvpXp}: whether fighting players pays skill XP: killing one, or the defensive skills' XP for a hit taken
 *       from one (off for now, not permanently: the PvP pass, docs/ROADMAP.md -> PvP).</li>
 *   <li>{@code hitBudget}: each mob pays each skill's per-hit XP (Critical Strike, the defensive skills, magic on hit)
 *       at most what its kill pays that skill, as class cast XP already does.</li>
 *   <li>{@code idleSeconds}: defensive XP needs a player who did something in this long (0: no rule).</li>
 * </ul>
 */
public record XpRules(Map<String, Double> byOrigin, boolean pvpXp, boolean hitBudget, int idleSeconds) {

    public static final XpRules DEFAULTS = new XpRules(Map.of(
            "SPAWNER", 0.25, "VILLAGE_DEFENSE", 0.25, "NETHER_PORTAL", 0.25,
            "BREEDING", 0.0, "EGG", 0.0, "SPAWNER_EGG", 0.0, "DISPENSE_EGG", 0.0,
            "BUILD_IRONGOLEM", 0.0, "BUILD_SNOWMAN", 0.0), false, true, 60);

    public XpRules {
        byOrigin = Map.copyOf(byOrigin);
        idleSeconds = Math.max(0, idleSeconds);
    }

    /** The share a mob that spawned for {@code reason} pays, 0..1 (1 for anything not listed, or no reason). */
    public double originFactor(String reason) {
        if (reason == null) return 1.0;
        Double f = byOrigin.get(reason.toUpperCase(Locale.ROOT));
        return f == null || !Double.isFinite(f) ? 1.0 : Math.max(0.0, Math.min(1.0, f));
    }

    /** True when a player idle for {@code idleMs} earns no defensive XP. */
    public boolean idle(long idleMs) {
        return idleSeconds > 0 && idleMs >= idleSeconds * 1000L;
    }
}
