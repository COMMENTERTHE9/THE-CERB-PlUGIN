package Attributes;

/**
 * Pure bow math (unit-tested). Vanilla bow power after drawing {@code t} seconds:
 * {@code p = (t^2 + 2t) / 3}, full (1.0) at one second. Draw speed makes each second of drawing
 * count for more: a release after {@code t} seconds shoots with the power of {@code t x (1 + bonus)}.
 */
public final class BowDraw {

    private BowDraw() {}

    /** Vanilla power for a draw of {@code seconds} (0..1). */
    public static double powerFor(double seconds) {
        double t = Math.max(0, Math.min(1, seconds));
        return Math.min(1.0, (t * t + 2 * t) / 3.0);
    }

    /** Draw time (seconds) that gives {@code power}: the inverse of {@link #powerFor}. */
    public static double secondsFor(double power) {
        double p = Math.max(0, Math.min(1, power));
        return Math.sqrt(1 + 3 * p) - 1;
    }

    /** The power a release at {@code power} has with draw speed {@code bonus} (0.25 = 25% faster). */
    public static double boosted(double power, double bonus) {
        if (!(bonus > 0) || !(power > 0) || power >= 1.0) return Math.max(0, Math.min(1, power));
        return powerFor(secondsFor(power) * (1 + bonus));
    }

    /** Seconds of drawing to full power with draw speed {@code bonus}. */
    public static double fullDrawSeconds(double bonus) {
        return 1.0 / (1 + Math.max(0, bonus));
    }
}
