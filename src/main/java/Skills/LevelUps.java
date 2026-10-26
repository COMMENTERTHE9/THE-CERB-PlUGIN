package Skills;

import Attributes.Attribute;
import Attributes.AttributeRules;
import Attributes.AttributeSnapshot;
import Attributes.Stat;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * What a skill level-up says (docs/design/LEVEL_UP.md): the milestone titles (docs/design/SKILL_PROGRESSION.md), the
 * level text, and the real attribute and stat gain the levels made, read from two attribute snapshots (before and
 * after, diminishing returns included). Pure, unit-tested.
 */
public final class LevelUps {

    private LevelUps() {}

    /** Milestone levels, lowest first: 10 Apprentice, 25 Journeyman, 50 Expert, 75 Master, 100 Grandmaster. */
    static final int[] MILESTONES = {10, 25, 50, 75, 100};
    private static final String[] TITLES = {"Apprentice", "Journeyman", "Expert", "Master", "Grandmaster"};

    /** The milestone title at exactly {@code level}, or null. */
    public static String title(int level) {
        for (int i = 0; i < MILESTONES.length; i++) if (MILESTONES[i] == level) return TITLES[i];
        return null;
    }

    /** The highest milestone level in ({@code from}, {@code to}], or 0 when the levels crossed none. */
    public static int milestoneCrossed(int from, int to) {
        int best = 0;
        for (int m : MILESTONES) if (m > from && m <= to) best = m;
        return best;
    }

    /** "12", or "10 → 13" when several levels came at once. */
    public static String levels(int from, int to) {
        return to - from > 1 ? from + " → " + to : String.valueOf(to);
    }

    /** One attribute or stat that changed: its name ("Power (swords)") and the values before and after. */
    public record Gain(String name, double before, double after, boolean percent) {
        public double delta() { return after - before; }

        /** "+0.9 Power (swords)", "+0.40% Gathering speed". */
        public String text() {
            return (percent ? signed(delta() * 100) + "%" : signed(delta())) + " " + name;
        }

        /** "Power (swords) 10.4 → 11.3", "Gathering speed 2.00% → 2.40%". */
        public String fromTo() {
            return name + " " + (percent ? num(before * 100) + "% → " + num(after * 100) + "%" : num(before) + " → " + num(after));
        }
    }

    /** What {@code skill}'s levels changed between {@code before} and {@code after}, biggest first (the skill's own weights only). */
    public static List<Gain> gains(String skill, AttributeRules rules, AttributeSnapshot before, AttributeSnapshot after) {
        List<Gain> out = new ArrayList<>();
        for (AttributeRules.SkillWeight w : rules.skillWeights().getOrDefault(skill, List.of())) {
            boolean scoped = w.attribute() == Attribute.POWER && w.weapon() != null;
            boolean yielded = w.attribute() == Attribute.FORTUNE && w.yield() != null; // Mining's on ore, Woodcutting's on wood
            double b = scoped ? before.power(w.weapon()) : yielded ? before.fortune(w.yield()) : before.get(w.attribute());
            double a = scoped ? after.power(w.weapon()) : yielded ? after.fortune(w.yield()) : after.get(w.attribute());
            String name = w.attribute().displayName + (scoped ? " (" + w.weapon().displayName.toLowerCase(Locale.ROOT) + ")"
                    : yielded ? " (" + w.yield().id + ")" : "");
            add(out, new Gain(name, b, a, false));
        }
        for (AttributeRules.StatWeight w : rules.secondary().skillWeights().getOrDefault(skill, List.of())) {
            boolean scoped = w.stat() == Stat.ATTACK_SPEED && w.weapon() != null;
            double b = scoped ? before.attackSpeed(w.weapon()) : before.secondary(w.stat());
            double a = scoped ? after.attackSpeed(w.weapon()) : after.secondary(w.stat());
            String name = w.stat().displayName + (scoped ? " (" + w.weapon().displayName.toLowerCase(Locale.ROOT) + ")" : "");
            add(out, new Gain(name, b, a, true));
        }
        out.sort((x, y) -> Double.compare(Math.abs(y.delta()) / scale(y), Math.abs(x.delta()) / scale(x)));
        return out;
    }

    /** Same-name gains (a skill feeding one attribute twice) are merged; nothing that didn't move is kept. */
    private static void add(List<Gain> out, Gain g) {
        if (!(Math.abs(g.delta()) > 1e-9)) return;
        for (int i = 0; i < out.size(); i++) {
            if (out.get(i).name().equals(g.name())) return; // one snapshot value: already counted
        }
        out.add(g);
    }

    /** Percent stats are fractions: compare them on the same footing as points (1% ~ 1 point). */
    private static double scale(Gain g) { return g.percent() ? 0.01 : 1.0; }

    static String signed(double v) { return (v >= 0 ? "+" : "-") + num(Math.abs(v)); }

    /** 12, 0.9, 0.04 (two decimals below 1, one below 100, none above). */
    static String num(double v) {
        double a = Math.abs(v);
        if (a >= 100) return String.format(Locale.ROOT, "%,.0f", v);
        if (a >= 1) return trim(String.format(Locale.ROOT, "%.1f", v));
        return trim(String.format(Locale.ROOT, "%.2f", v));
    }

    private static String trim(String s) {
        if (!s.contains(".")) return s;
        s = s.replaceAll("0+$", "");
        return s.endsWith(".") ? s.substring(0, s.length() - 1) : s;
    }
}
