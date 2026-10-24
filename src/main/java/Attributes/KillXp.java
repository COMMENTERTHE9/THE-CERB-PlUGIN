package Attributes;

/**
 * Kill XP v2 (pure, unit-tested): XP for killing a mob is based on the mob's LEVEL and TIER,
 * not its HP. HP reaches billions under the realm curve, so it can't drive a reward; level and
 * tier are the designed difficulty signals.
 *
 * <pre>
 * xp = base * (1 + levelSlope * (mobLevel - 1)) * tierMultiplier
 *      * (antiGrindFactor if mobLevel <= skillLevel - antiGrindGap)
 * clamped to [0, maxPerKill]
 * </pre>
 * Defaults (attributes.yml): base 40, slope 0.15 -> Common L1 = 40 (same as before),
 * Common L50 = 334, Rare L50 = 668 (design: ~300-600), Primordial L100 = 9,510.
 */
public final class KillXp {

    private KillXp() {}

    /** Tunables, loaded from attributes.yml "kill-xp". */
    public record Params(boolean enabled, double base, double levelSlope, int antiGrindGap,
                         double antiGrindFactor, double maxPerKill) {
        public static final Params DEFAULTS = new Params(true, 40.0, 0.15, 20, 0.25, 1_000_000.0);
    }

    /**
     * @param mobLevel       the mob's level (1..100; values outside are clamped to >= 1)
     * @param tierMultiplier the mob's XP multiplier (mobs.yml entry, else its tier default)
     * @param skillLevel     the killer's level in the skill that receives the XP
     */
    public static double compute(int mobLevel, double tierMultiplier, int skillLevel, Params p) {
        int level = Math.max(1, mobLevel);
        double mult = finite(tierMultiplier, 1.0);
        double xp = Math.max(0.0, p.base()) * (1.0 + Math.max(0.0, p.levelSlope()) * (level - 1)) * Math.max(0.0, mult);
        if (p.antiGrindGap() > 0 && level <= skillLevel - p.antiGrindGap()) {
            xp *= Math.max(0.0, Math.min(1.0, p.antiGrindFactor()));
        }
        if (!Double.isFinite(xp)) xp = p.maxPerKill();
        return Math.max(0.0, Math.min(p.maxPerKill(), xp));
    }

    /** True when the anti-grind reduction applies (for UI hints). */
    public static boolean isGreyKill(int mobLevel, int skillLevel, Params p) {
        return p.antiGrindGap() > 0 && Math.max(1, mobLevel) <= skillLevel - p.antiGrindGap();
    }

    private static double finite(double v, double fallback) {
        return Double.isFinite(v) ? v : fallback;
    }
}
