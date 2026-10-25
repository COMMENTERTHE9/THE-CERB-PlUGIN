package Classes;

import org.bukkit.configuration.ConfigurationSection;

import java.util.List;
import java.util.Locale;

/**
 * How ability keys map to loadout slots (docs/design/ABILITY_CONTROLS.md). Pure rules, no server
 * state: the listener turns raw key events into {@link Click}s and asks here which slot they mean.
 * Each player picks a {@link Scheme}; the server sets the default ({@code classes.yml controls}).
 *
 * <ul>
 *   <li><b>Quick keys</b> (default): right-click = active 1, F = 2, Q = 3, Sneak + right-click = 4,
 *       Sneak + F = 5. One press per cast; the three most used need no Sneak.</li>
 *   <li><b>Click combos</b>: three clicks starting with right-click (R R R = 1, R L R = 2, R L L = 3,
 *       R R L = 4), F = 5. Mouse only, no Sneak; Q stays a normal drop.</li>
 * </ul>
 * A bow or crossbow shoots with right-click, so with one in hand right and left swap places in
 * both schemes (its "right-click" abilities are on left-click).
 */
public final class Controls {

    private Controls() {}

    public enum Scheme {
        QUICK("Quick keys"), COMBO("Click combos");

        public final String displayName;

        Scheme(String displayName) { this.displayName = displayName; }

        /** "quick" / "combo" (and a few spellings); null when unknown. */
        public static Scheme parse(String s) {
            if (s == null) return null;
            return switch (s.trim().toLowerCase(Locale.ROOT)) {
                case "quick", "keys", "quick-keys", "quickkeys" -> QUICK;
                case "combo", "combos", "click-combos", "clicks" -> COMBO;
                default -> null;
            };
        }

        public String id() { return this == QUICK ? "quick" : "combo"; }
    }

    /** When the ability bar (the boss bar listing the loadout's keys and cooldowns) is shown. */
    public enum BarMode {
        /** In a fight, and for a few seconds after a cast or after taking a weapon or focus in hand. */
        AUTO,
        /** Whenever a weapon or focus is in hand. */
        ON,
        OFF;

        public static BarMode parse(String s) {
            if (s == null) return null;
            String v = s.trim().toLowerCase(Locale.ROOT);
            // YAML reads an unquoted on/off (or yes/no) as true/false, so a hand-edited file says those.
            if (v.equals("true")) return ON;
            if (v.equals("false")) return OFF;
            try { return valueOf(v.toUpperCase(Locale.ROOT)); } catch (IllegalArgumentException e) { return null; }
        }

        public String id() { return name().toLowerCase(Locale.ROOT); }
    }

    /** A mouse button as the player pressed it (before the bow swap). */
    public enum Click { RIGHT, LEFT }

    /** Server settings ({@code classes.yml controls}); missing or unknown values keep the defaults. */
    public record Settings(Scheme defaultScheme, long comboGapMs, BarMode defaultBar, long barLingerMs) {
        public static final Settings DEFAULTS = new Settings(Scheme.QUICK, 700, BarMode.AUTO, 5_000);

        public static Settings from(ConfigurationSection s) {
            if (s == null) return DEFAULTS;
            Scheme scheme = Scheme.parse(s.getString("default-scheme"));
            BarMode bar = BarMode.parse(s.getString("ability-bar"));
            long gap = s.getLong("combo-gap-ms", DEFAULTS.comboGapMs);
            long linger = s.getLong("ability-bar-linger-ms", DEFAULTS.barLingerMs);
            return new Settings(scheme == null ? DEFAULTS.defaultScheme : scheme,
                    Math.max(250, Math.min(2_000, gap)),
                    bar == null ? DEFAULTS.defaultBar : bar,
                    Math.max(1_000, Math.min(30_000, linger)));
        }
    }

    // ── Quick keys ──────────────────────────────────────────────

    /** Quick keys: the slot (0-4) for a right-click (left-click with a bow), with or without Sneak. */
    public static int quickPrimary(boolean sneaking) { return sneaking ? 3 : 0; }

    /** Quick keys: the slot for F, with or without Sneak. */
    public static int quickF(boolean sneaking) { return sneaking ? 4 : 1; }

    /** Quick keys: the slot for Q. */
    public static final int QUICK_Q = 2;

    // ── Click combos ────────────────────────────────────────────

    /** Clicks in a combo (the first one starts it). */
    public static final int COMBO_LENGTH = 3;

    /** Combos: F casts this slot. */
    public static final int COMBO_F = 4;

    /**
     * Combos: the slot (0-3) for a full combo, or -1 when it isn't one. With a bow the buttons are
     * mirrored, so the same pattern of "start button / other button" gives the same slot.
     */
    public static int comboSlot(List<Click> clicks, boolean bow) {
        if (clicks.size() != COMBO_LENGTH) return -1;
        Click start = bow ? Click.LEFT : Click.RIGHT;
        if (clicks.get(0) != start) return -1;
        boolean second = clicks.get(1) == start, third = clicks.get(2) == start;
        if (second && third) return 0;   // R R R (hold right-click)
        if (!second && third) return 1;  // R L R
        if (!second) return 2;           // R L L
        return 3;                        // R R L
    }

    /** The button that starts a combo with this hand. */
    public static Click comboStart(boolean bow) { return bow ? Click.LEFT : Click.RIGHT; }

    /** "R L R" for slot 1 (index) of a combo, as printed in menus and the ability bar. */
    public static String comboText(int slot, boolean bow) {
        String s = bow ? "L" : "R", o = bow ? "R" : "L";
        return switch (slot) {
            case 0 -> s + " " + s + " " + s;
            case 1 -> s + " " + o + " " + s;
            case 2 -> s + " " + o + " " + o;
            case 3 -> s + " " + s + " " + o;
            default -> "F";
        };
    }

    // ── labels ──────────────────────────────────────────────────

    /** The key for active slot {@code slot} in words: "Right-click", "Sneak + F", "R L R". */
    public static String keyLabel(Scheme scheme, int slot, boolean bow) {
        if (scheme == Scheme.COMBO) return slot == COMBO_F ? "F" : comboText(slot, bow);
        String click = bow ? "left-click" : "right-click";
        return switch (slot) {
            case 0 -> Character.toUpperCase(click.charAt(0)) + click.substring(1);
            case 1 -> "F";
            case 2 -> "Q";
            case 3 -> "Sneak + " + click;
            case 4 -> "Sneak + F";
            default -> "?";
        };
    }

    /** The same, as short as the ability bar needs: "R", "F", "Q", "⇧R", "⇧F", "RLR". */
    public static String keyShort(Scheme scheme, int slot, boolean bow) {
        if (scheme == Scheme.COMBO) return slot == COMBO_F ? "F" : comboText(slot, bow).replace(" ", "");
        String click = bow ? "L" : "R";
        return switch (slot) {
            case 0 -> click;
            case 1 -> "F";
            case 2 -> "Q";
            case 3 -> "⇧" + click;
            case 4 -> "⇧F";
            default -> "?";
        };
    }
}
