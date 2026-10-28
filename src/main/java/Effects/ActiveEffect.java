package Effects;

import Attributes.Contribution;
import org.bukkit.Material;

import java.util.List;

/**
 * One thing affecting a player right now, as the menus show it (docs/design/ACTIVE_EFFECTS.md): read from the system
 * that runs it at the moment it is shown, never stored. Read-only.
 *
 * @param key           stable for this effect while it runs ("meal", "class:war_cry", "buff:1a2b3c4d", "potion:speed"):
 *                      a screen redraws when the set of keys it shows changes
 * @param category      what kind of source it is; the order effects are listed in (a meal first)
 * @param name          "Hearty Stew", "War Cry", "Speed II"
 * @param source        where it comes from, for the player: "Meal · Fine · Survival", "Warrior ability", "Potion effect"
 * @param icon          the tile's item
 * @param endsAt        when it ends (epoch milliseconds), or {@link #NO_END} for one without an end of its own
 * @param endsWhen      for {@link #NO_END}: when it does end ("until you leave"); null otherwise
 * @param contributions what it adds to the attribute engine (empty when nothing): the menu works out what they change
 * @param notes         what it does where the engine can't say (a vanilla effect, a Riposte guard)
 * @param phase         a shorter part with its own time (a meal's restoration), or null
 */
public record ActiveEffect(String key, Category category, String name, String source, Material icon, long endsAt, String endsWhen,
                           List<Contribution> contributions, List<String> notes, Phase phase) {

    /** An effect with no end time of its own (an infinite potion, an admin test realm). */
    public static final long NO_END = Long.MAX_VALUE;

    public ActiveEffect {
        contributions = contributions == null ? List.of() : List.copyOf(contributions);
        notes = notes == null ? List.of() : List.copyOf(notes);
    }

    /** Kinds of source, in the order they are listed. {@link #ALWAYS_ON} is never mixed with the timed ones. */
    public enum Category {
        MEAL("Meal"),
        /** Alchemy's elixirs and draughts (docs/design/ALCHEMY.md). */
        ELIXIR("Elixir"),
        ABILITY("Ability"),
        BUFF("Buff"),
        POTION("Potion"),
        /** Equipped passives and keystone: not timed, listed apart. */
        ALWAYS_ON("Always on");

        public final String label;

        Category(String label) { this.label = label; }
    }

    /**
     * A shorter part of an effect with its own time, such as a meal's restoration: {@code endsAt} 0 when it is not
     * running any more ({@code text} says why), {@code paused} while it waits (PvP).
     */
    public record Phase(String label, String text, long endsAt, boolean paused) {
        public boolean running(long now) { return endsAt > now; }
    }

    /** True when this effect ends by itself at a time (shown counting down). */
    public boolean timed() { return endsAt != NO_END; }

    /** Milliseconds left at {@code now} (0 when ended; {@link #NO_END} for one without an end). */
    public long remaining(long now) {
        return endsAt == NO_END ? NO_END : Math.max(0, endsAt - now);
    }

    /** True while it still runs at {@code now}. */
    public boolean active(long now) { return endsAt == NO_END || endsAt > now; }
}
