package Skills;

/**
 * Pure skill-progression math: XP awards, level-ups, the level cap, and kill-XP.
 *
 * <p>Everything here is a pure function of its arguments so it can be unit-tested without a
 * server. {@link SkillManager} delegates to it for every XP award, so all award paths share
 * one cap and one overflow policy.
 *
 * <p>Invariants guaranteed by {@link #award}: level stays in {@code [MIN_LEVEL, MAX_LEVEL]}, XP stays in
 * {@code [0, Integer.MAX_VALUE]}, and garbage input (NaN, infinity, negative gains) never wraps
 * or corrupts stored values.
 */
public final class SkillProgression {

    /** Highest level any skill can reach. */
    public static final int MAX_LEVEL = 100;
    /**
     * The level every skill starts at, a new character's and an untrained one's. Versions before 2026-10-27 wrote new
     * characters' skills at level 0; {@link #clampLevel} reads those as level 1, with their XP (both need 115 to go on).
     */
    public static final int MIN_LEVEL = 1;

    private SkillProgression() {}

    /** Result of an XP award: the new level and in-level XP, plus how many levels were gained. */
    public record Result(int level, int xp, int levelsGained) {}

    /**
     * Apply an XP award to a skill.
     *
     * @param level  current level (values outside {@code [MIN_LEVEL, MAX_LEVEL]} are clamped)
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

    /** A skill's whole XP from the start: every level below {@code level} plus {@code xp} into it. */
    public static long totalXp(int level, int xp) {
        long t = 0;
        for (int l = MIN_LEVEL, top = clampLevel(level); l < top; l++) t += SkillManager.xpForLevel(l);
        return t + Math.max(0, xp);
    }

    /**
     * The XP an award really added: the skill's total after minus before, so the level cap (and an award that never
     * happened) counts as nothing. Never negative.
     */
    public static long gainedXp(int levelBefore, int xpBefore, Result after) {
        if (after == null) return 0;
        return Math.max(0, totalXp(after.level(), after.xp()) - totalXp(levelBefore, xpBefore));
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

    /** Clamp a stored level into the valid range (an old level 0 is level 1). */
    public static int clampLevel(int level) {
        return Math.max(MIN_LEVEL, Math.min(MAX_LEVEL, level));
    }

    /** What reading a player's stored rows found: rows at an older version's level 0, and rows with values out of range. */
    public record Loaded(int started, int repaired) {}

    /**
     * Stored skill rows as read for play, into {@code levels} and {@code xp}: each level clamped (an older version's level
     * 0 is level 1 with its XP kept, as both need 115 to go on) and each XP kept inside its level (the old kill-XP
     * overflow, levels above the cap). Pure but for the two output maps.
     */
    public static Loaded readStored(java.util.Map<String, Integer> storedLevels, java.util.Map<String, Integer> storedXp,
                                    java.util.Map<String, Integer> levels, java.util.Map<String, Integer> xp) {
        int started = 0, repaired = 0;
        for (java.util.Map.Entry<String, Integer> e : storedLevels.entrySet()) {
            int raw = e.getValue() == null ? MIN_LEVEL : e.getValue();
            int fixed = clampLevel(raw);
            if (raw == 0) started++;
            else if (fixed != raw) repaired++;
            levels.put(e.getKey(), fixed);
        }
        for (java.util.Map.Entry<String, Integer> e : storedXp.entrySet()) {
            int raw = e.getValue() == null ? 0 : e.getValue();
            int fixed = sanitizeStoredXp(levels.getOrDefault(e.getKey(), MIN_LEVEL), raw);
            if (fixed != raw) repaired++;
            xp.put(e.getKey(), fixed);
        }
        return new Loaded(started, repaired);
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
