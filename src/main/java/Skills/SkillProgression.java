package Skills;

/**
 * Pure skill-progression math: XP awards, level-ups, the level cap, and kill-XP.
 *
 * <p>Everything here is a pure function of its arguments so it can be unit-tested without a
 * server. {@link SkillManager} delegates to it for every XP award, so all award paths share
 * one cap and one overflow policy.
 *
 * <p>Invariants guaranteed by {@link #award}: level stays in {@code [0, MAX_LEVEL]}, XP stays in
 * {@code [0, Integer.MAX_VALUE]}, and garbage input (NaN, infinity, negative gains) never wraps
 * or corrupts stored values.
 */
public final class SkillProgression {

    /** Highest level any skill can reach. */
    public static final int MAX_LEVEL = 100;

    private SkillProgression() {}

    /** Result of an XP award: the new level and in-level XP, plus how many levels were gained. */
    public record Result(int level, int xp, int levelsGained) {}

    /**
     * Apply an XP award to a skill.
     *
     * @param level  current level (values outside {@code [0, MAX_LEVEL]} are clamped)
     * @param xp     current in-level XP (negative values, e.g. from old overflow, become 0)
     * @param gained XP to add; NaN, infinite, and negative values add nothing
     */
    public static Result award(int level, int xp, double gained) {
        int lvl = clampLevel(level);
        if (lvl >= MAX_LEVEL) {
            return new Result(MAX_LEVEL, 0, 0);
        }
        long total = (long) Math.max(0, xp) + toWholeXp(gained);
        int startLevel = lvl;
        while (lvl < MAX_LEVEL) {
            long required = SkillManager.xpForLevel(lvl);
            if (total < required) break;
            total -= required;
            lvl++;
        }
        if (lvl >= MAX_LEVEL) {
            return new Result(MAX_LEVEL, 0, MAX_LEVEL - startLevel);
        }
        return new Result(lvl, (int) Math.min(total, Integer.MAX_VALUE), lvl - startLevel);
    }

    /**
     * XP for killing a mob: half its max HP plus half the damage of the killing blow, times the
     * mob's XP multiplier (design plan, "XP sources and the dynamic-XP formula").
     *
     * <p>Damage beyond the mob's max HP is overkill and does not count. This matters because the
     * mob virtual-HP pool finishes a mob with a huge sentinel hit (1e9) to guarantee the kill;
     * without the clamp that sentinel became ~5e8 XP per kill.
     */
    public static double killXp(double maxHp, double damage, double multiplier) {
        double hp = finiteNonNegative(maxHp);
        double dmg = Math.min(finiteNonNegative(damage), hp);
        double mult = finiteNonNegative(multiplier);
        return (hp * 0.5 + dmg * 0.5) * mult;
    }

    /** Clamp a stored level into the valid range. */
    public static int clampLevel(int level) {
        return Math.max(0, Math.min(MAX_LEVEL, level));
    }

    /** Clamp stored in-level XP: negative values (from the old int overflow) become 0. */
    public static int clampXp(int xp) {
        return Math.max(0, xp);
    }

    /**
     * Repair stored in-level XP for a skill at {@code level}. Valid in-level XP is always below
     * the requirement for the next level, so anything at or above it is leftover from the old
     * kill-XP overflow (~5e8 per kill). It is clamped to one XP short of the next level, so old
     * data can grant at most one level, never a jump straight to the cap. At the cap XP is 0.
     */
    public static int sanitizeStoredXp(int level, int xp) {
        int lvl = clampLevel(level);
        if (lvl >= MAX_LEVEL) return 0;
        int ceiling = SkillManager.xpForLevel(lvl) - 1;
        return Math.max(0, Math.min(xp, ceiling));
    }

    /** The whole XP an award of {@code gained} adds (fractions are dropped; garbage adds nothing). */
    public static long wholeXp(double gained) {
        return toWholeXp(gained);
    }

    private static long toWholeXp(double gained) {
        if (Double.isNaN(gained) || gained <= 0) return 0L;
        if (gained >= Integer.MAX_VALUE) return Integer.MAX_VALUE;
        return (long) gained;
    }

    private static double finiteNonNegative(double v) {
        if (Double.isNaN(v) || v <= 0) return 0.0;
        return Double.isInfinite(v) ? Double.MAX_VALUE : v;
    }
}
