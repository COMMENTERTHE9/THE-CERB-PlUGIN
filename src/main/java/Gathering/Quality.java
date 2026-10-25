package Gathering;

import java.util.Locale;

/**
 * How well a piece of gear was forged (docs/design/GATHERING.md): rolled when it is crafted, by
 * the Smithing level, and stored in the item by id. What each quality adds comes from
 * {@code resources.yml} through the attribute engine, never from item modifiers.
 */
public enum Quality {
    COMMON("Common", "§7"),
    FINE("Fine", "§a"),
    SUPERIOR("Superior", "§9"),
    MASTERWORK("Masterwork", "§6");

    public final String displayName;
    /** Legacy colour code for lore. */
    public final String color;

    Quality(String displayName, String color) {
        this.displayName = displayName;
        this.color = color;
    }

    public String id() { return name().toLowerCase(Locale.ROOT); }

    /** "fine" / "FINE" → FINE; null when unknown. */
    public static Quality parse(String id) {
        if (id == null) return null;
        try {
            return valueOf(id.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /**
     * The odds at {@code level}, as weights per quality (index = ordinal), between the odds at
     * level 1 and at level 100 (linear; clamped outside).
     */
    public static double[] odds(int level, double[] atLevel1, double[] atLevel100) {
        double t = Math.max(0, Math.min(1, (level - 1) / 99.0));
        double[] out = new double[values().length];
        for (int i = 0; i < out.length; i++) {
            double a = i < atLevel1.length ? Math.max(0, atLevel1[i]) : 0;
            double b = i < atLevel100.length ? Math.max(0, atLevel100[i]) : 0;
            out[i] = a + (b - a) * t;
        }
        return out;
    }

    /** The chance of rolling {@code q} or better at {@code level} (0 when the odds are all zero). */
    public static double chanceAtLeast(Quality q, int level, double[] atLevel1, double[] atLevel100) {
        double[] w = odds(level, atLevel1, atLevel100);
        double total = 0, good = 0;
        for (int i = 0; i < w.length; i++) {
            total += w[i];
            if (i >= q.ordinal()) good += w[i];
        }
        return total > 0 ? good / total : 0;
    }

    /** Roll a quality from {@code weights} with {@code r} in [0, 1). All-zero weights roll Common. */
    public static Quality roll(double[] weights, double r) {
        double total = 0;
        for (double w : weights) total += Math.max(0, w);
        if (!(total > 0)) return COMMON;
        double x = (r >= 0 ? Math.min(0.999999999, r) : 0) * total; // NaN or below 0 rolls as 0: the lowest it can roll
        for (int i = 0; i < weights.length && i < values().length; i++) {
            x -= Math.max(0, weights[i]);
            if (x < 0) return values()[i];
        }
        return values()[Math.min(weights.length, values().length) - 1];
    }
}
