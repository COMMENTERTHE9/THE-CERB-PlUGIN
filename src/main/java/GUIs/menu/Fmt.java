package GUIs.menu;

import java.util.Locale;

/**
 * Pure number/text formatting for menus (unit-tested). Cerberus numbers reach billions and
 * beyond, so everything big is shown compactly: 3.2K, 25.6M, 1.4B, 7.0T.
 */
public final class Fmt {

    private Fmt() {}

    private static final String[] UNITS = {"K", "M", "B", "T", "Qa", "Qi"};

    /** 950 -> "950", 3200 -> "3.2K", 25_600_000 -> "25.6M". NaN/inf -> "?". */
    public static String compact(double v) {
        if (Double.isNaN(v) || Double.isInfinite(v)) return "?";
        String sign = v < 0 ? "-" : "";
        double x = Math.abs(v);
        if (x < 1000) {
            return sign + (x == Math.rint(x) ? String.valueOf((long) x) : String.format(Locale.ROOT, "%.1f", x));
        }
        int i = -1;
        while (x >= 999.95 && i < UNITS.length - 1) { x /= 1000.0; i++; }
        return sign + String.format(Locale.ROOT, "%.1f", x) + UNITS[i];
    }

    /** 1234567 -> "1,234,567" (exact integers, for XP and levels). */
    public static String grouped(long v) {
        return String.format(Locale.ROOT, "%,d", v);
    }

    /** 0.4567 -> "45.7%". */
    public static String pct(double fraction) {
        if (Double.isNaN(fraction)) return "?";
        return String.format(Locale.ROOT, "%.1f%%", fraction * 100.0);
    }

    /** Multiplier: 2.345 -> "x2.35". */
    public static String mult(double m) {
        return String.format(Locale.ROOT, "x%.2f", m);
    }

    /** One decimal: 85.25 -> "85.3". */
    public static String one(double v) {
        return String.format(Locale.ROOT, "%.1f", v);
    }

    /** 93_784_000 ms -> "1d 2h 3m", 61_000 -> "1m 1s". */
    public static String duration(long ms) {
        if (ms < 0) ms = 0;
        long s = ms / 1000, d = s / 86400, h = (s % 86400) / 3600, m = (s % 3600) / 60, sec = s % 60;
        if (d > 0) return d + "d " + h + "h " + m + "m";
        if (h > 0) return h + "h " + m + "m";
        if (m > 0) return m + "m " + sec + "s";
        return sec + "s";
    }

    /** 1536 -> "1.5 KB", 10_485_760 -> "10.0 MB". */
    public static String bytes(long b) {
        if (b < 1024) return b + " B";
        String[] u = {"KB", "MB", "GB", "TB"};
        double x = b;
        int i = -1;
        while (x >= 1024 && i < u.length - 1) { x /= 1024; i++; }
        return String.format(Locale.ROOT, "%.1f %s", x, u[i]);
    }

    /** Text progress bar of {@code width} cells: "||||||||||||--------". */
    public static String bar(double current, double max, int width) {
        int filled = 0;
        if (max > 0 && current > 0 && !Double.isNaN(current)) {
            filled = (int) Math.round(Math.min(1.0, current / max) * width);
        }
        return "|".repeat(filled) + "-".repeat(Math.max(0, width - filled));
    }

    /** "BLADE_MASTERY" / "blade_mastery" -> "Blade Mastery". */
    public static String title(String raw) {
        if (raw == null || raw.isEmpty()) return "";
        StringBuilder sb = new StringBuilder();
        for (String p : raw.toLowerCase(Locale.ROOT).split("[_ ]+")) {
            if (p.isEmpty()) continue;
            if (sb.length() > 0) sb.append(' ');
            sb.append(Character.toUpperCase(p.charAt(0))).append(p.substring(1));
        }
        return sb.toString();
    }
}
