package Classes;

/**
 * Class rank 1-50 and its XP curve (docs/design/CLASSES.md §2.2). Shorter than a skill's: a
 * focused player masters a class in a few weeks (about 172,000 XP from rank 1 to 50). Pure and
 * overflow-safe, like {@code Skills.SkillProgression}.
 */
public final class ClassRanks {

    public static final int MAX_RANK = 50;

    private ClassRanks() {}

    /** XP to go from {@code rank} to the next: 80 + 25 x rank^1.5 (rank 1: 105, rank 25: 3,205, rank 49: 8,655). */
    public static long xpForRank(int rank) {
        int r = Math.max(1, Math.min(MAX_RANK, rank));
        return Math.round(80 + 25 * Math.pow(r, 1.5));
    }

    /** XP from rank 1 to the start of {@code rank}. */
    public static long totalTo(int rank) {
        long sum = 0;
        for (int r = 1; r < Math.min(MAX_RANK, rank); r++) sum += xpForRank(r);
        return sum;
    }

    /**
     * Class XP for one cast that reached an enemy of {@code foeLevel} (0 = unknown, paid in full).
     * Kill XP's weak-mob rule applies: a foe kill-xp.anti-grind-gap or more levels below the class's
     * own skills ({@code classSkillLevel}) pays anti-grind-factor of {@code base}, so casting at
     * trivial mobs can't out-earn real fights. Off with kill XP v2 itself ({@code enabled: false}).
     */
    public static double castXp(double base, int foeLevel, int classSkillLevel, Attributes.KillXp.Params k) {
        double xp = Double.isFinite(base) ? Math.max(0.0, base) : 0.0;
        if (k != null && k.enabled() && foeLevel > 0 && Attributes.KillXp.isGreyKill(foeLevel, classSkillLevel, k)) {
            xp *= Math.max(0.0, Math.min(1.0, k.antiGrindFactor()));
        }
        return xp;
    }

    /** Rank and XP into it after an award; nothing past the max rank. */
    public record Result(int rank, long xp, int ranksGained) {}

    /** Add {@code gained} XP (NaN, infinite or negative adds nothing; fractions round down). */
    public static Result award(int rank, long xp, double gained) {
        int r = Math.max(1, Math.min(MAX_RANK, rank));
        if (r >= MAX_RANK) return new Result(MAX_RANK, 0, 0);
        long add = Double.isFinite(gained) && gained > 0 ? (long) Math.min(gained, 1.0e12) : 0;
        long total = Math.max(0, xp) + add;
        int start = r;
        while (r < MAX_RANK && total >= xpForRank(r)) {
            total -= xpForRank(r);
            r++;
        }
        return r >= MAX_RANK ? new Result(MAX_RANK, 0, MAX_RANK - start) : new Result(r, total, r - start);
    }
}
