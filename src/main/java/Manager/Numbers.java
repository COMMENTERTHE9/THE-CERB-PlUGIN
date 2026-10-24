package Manager;

import java.util.Locale;

/**
 * The one number formatter for HUD, labels, damage numbers and menus (POWER_SCALING.md §1.5,
 * short-scale default per NAMING.md). Realm magnitudes run from tens to trillions, so anything
 * big is compacted; nothing ever prints a 13-digit number or a NaN. Pure and unit-tested.
 */
public final class Numbers {

    private Numbers() {}

    private static final String[] UNITS = {"K", "M", "B", "T", "Qa", "Qi", "Sx", "Sp", "Oc", "No", "Dc"};

    /** 950 → "950", 47.5 → "47.5", 3200 → "3.2K", 1.4e9 → "1.4B", 2e36 → "2.0e36". NaN/inf → "?". */
    public static String compact(double v) {
        if (Double.isNaN(v) || Double.isInfinite(v)) return "?";
        String sign = v < 0 ? "-" : "";
        double x = Math.abs(v);
        if (x < 1000) {
            return sign + (x == Math.rint(x) ? String.valueOf((long) x) : String.format(Locale.ROOT, "%.1f", x));
        }
        int i = -1;
        while (x >= 999.95 && i < UNITS.length - 1) { x /= 1000.0; i++; }
        if (x >= 999.95) return sign + String.format(Locale.ROOT, "%.1e", Math.abs(v)).replace("e+", "e"); // past Dc
        return sign + String.format(Locale.ROOT, "%.1f", x) + UNITS[i];
    }

    /** HUD values: exact whole numbers below 10,000 ("102", "9,850"), compact above ("12.3K", "1.7B"). */
    public static String hud(double v) {
        if (Double.isNaN(v) || Double.isInfinite(v)) return "?";
        if (Math.abs(v) < 10_000) return String.format(Locale.ROOT, "%,d", Math.round(v));
        return compact(v);
    }
}
