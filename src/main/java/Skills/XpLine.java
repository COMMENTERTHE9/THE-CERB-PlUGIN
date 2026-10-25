package Skills;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;

/**
 * One player's recent XP gains, merged into the short line the HUD shows (the owner's real-time XP
 * display, docs/design/GATHERING.md): gains of the same skill within {@code mergeMs} of each other
 * add up ("+86 Mining") instead of making a line each. Pure, unit-tested; one per player, used on
 * that player's thread.
 */
public final class XpLine {

    /** How much the line says (a per-player setting). */
    public enum Mode {
        /** "+86 Mining" */
        COMPACT,
        /** "+86 Mining XP · 1,240/2,000 · Lv 12" */
        DETAILED,
        /** Nothing (level-ups still show in chat). */
        OFF;

        public String id() { return name().toLowerCase(Locale.ROOT); }

        public static Mode parse(String s) {
            if (s == null) return null;
            try {
                return valueOf(s.trim().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException e) {
                return null;
            }
        }
    }

    private static final class Gain {
        final String skill;
        long total;
        long last;
        int level, xp, required;
        boolean max;

        Gain(String skill) { this.skill = skill; }
    }

    /** At most this many skills are named; the rest are counted ("+2 more"). */
    static final int NAMED = 2;

    private final long mergeNs;
    /** Most recent first. */
    private final List<Gain> gains = new ArrayList<>(4);

    public XpLine(long mergeMs) {
        this.mergeNs = Math.max(0, mergeMs) * 1_000_000L;
    }

    /**
     * Add {@code amount} XP of {@code skill} (the name players read) at {@code now} (nanoseconds),
     * with the skill's progress after it: {@code level}, {@code xp} into it of {@code required}.
     */
    public void add(String skill, long amount, int level, int xp, int required, boolean max, long now) {
        if (skill == null || amount <= 0) return;
        prune(now);
        Gain g = null;
        for (Iterator<Gain> it = gains.iterator(); it.hasNext(); ) {
            Gain x = it.next();
            if (x.skill.equals(skill)) { g = x; it.remove(); break; }
        }
        if (g == null) g = new Gain(skill);
        g.total = g.total + amount < 0 ? Long.MAX_VALUE : g.total + amount; // never wraps
        g.last = now;
        g.level = level;
        g.xp = xp;
        g.required = required;
        g.max = max;
        gains.add(0, g);
    }

    /** Forget gains whose skill has had none for longer than the merge window. */
    public void prune(long now) {
        gains.removeIf(g -> now - g.last > mergeNs);
    }

    /** The line in {@code mode} (section-sign colours), or "" when there is nothing to say. */
    public String render(Mode mode) {
        if (mode == Mode.OFF || gains.isEmpty()) return "";
        StringBuilder sb = new StringBuilder();
        if (mode == Mode.DETAILED) {
            Gain g = gains.get(0);
            sb.append("§a+").append(grouped(g.total)).append(" §f").append(g.skill).append(" XP");
            if (g.max) sb.append(" §8· §6max level");
            else sb.append(" §8· §7").append(grouped(g.xp)).append("/").append(grouped(g.required))
                    .append(" §8· §7Lv ").append(g.level);
            if (gains.size() > 1) sb.append(" §8(+").append(gains.size() - 1).append(" more)");
            return sb.toString();
        }
        for (int i = 0; i < gains.size() && i < NAMED; i++) {
            Gain g = gains.get(i);
            if (i > 0) sb.append("§8 · ");
            sb.append("§a+").append(grouped(g.total)).append(" §f").append(g.skill);
        }
        if (gains.size() > NAMED) sb.append(" §8+").append(gains.size() - NAMED).append(" more");
        return sb.toString();
    }

    /** Skills in the line now. */
    public int size() { return gains.size(); }

    static String grouped(long v) {
        return String.format(Locale.ROOT, "%,d", v);
    }
}
