package GUIs.player;

import org.bukkit.ChatColor;

/**
 * Honest state of a menu destination. Never mixes up "not built yet" with "not unlocked yet":
 * <ul>
 *   <li>{@link State#AVAILABLE}: the gameplay system exists and the screen works.</li>
 *   <li>{@link State#LOCKED}: the system exists, but this player hasn't met its requirement.</li>
 *   <li>{@link State#IN_DEVELOPMENT}: the gameplay system is not implemented yet.</li>
 * </ul>
 * Each state has a word and a symbol, so it never depends on colour alone.
 */
public record Availability(State state, String note) {

    public enum State {
        AVAILABLE(ChatColor.GREEN + "✔ Available"),
        LOCKED(ChatColor.GOLD + "✖ Locked"),
        IN_DEVELOPMENT(ChatColor.GRAY + "○ In development");

        public final String label;

        State(String label) { this.label = label; }
    }

    public static Availability available() { return new Availability(State.AVAILABLE, null); }

    /** Available, with one line on what inside it is not built yet. */
    public static Availability available(String note) { return new Availability(State.AVAILABLE, note); }

    /** @param requirement what the player must do, e.g. "Reach the Adept realm" */
    public static Availability locked(String requirement) { return new Availability(State.LOCKED, requirement); }

    /** @param note one line on what is coming, e.g. "Track every creature you defeat" */
    public static Availability inDevelopment(String note) { return new Availability(State.IN_DEVELOPMENT, note); }

    public boolean isAvailable() { return state == State.AVAILABLE; }
}
