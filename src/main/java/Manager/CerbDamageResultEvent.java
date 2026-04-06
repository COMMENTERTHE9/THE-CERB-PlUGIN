package Manager;

import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

/**
 * Fired AFTER all damage processing completes — for display/logging only,
 * damage cannot be modified.
 * <p>
 * Fires for EVERY completed pipeline attempt that reaches result stage,
 * even if finalDamage is 0 (fully absorbed). Does NOT fire for
 * cancelled/blocked events that never entered the pipeline.
 * <p>
 * Named "Result" to leave room for a future pre-damage
 * {@code CerbDamageProcessEvent} (cancellable).
 */
public class CerbDamageResultEvent extends Event {
    private static final HandlerList HANDLERS = new HandlerList();
    private final CombatContext context;

    public CerbDamageResultEvent(CombatContext context) {
        this.context = context;
    }

    /** Full pipeline context — identity, attack state, defense state, post-damage, trace. */
    public CombatContext getContext() {
        return context;
    }

    /** Convenience: true when the defense pipeline fully absorbed all damage. */
    public boolean wasFullyAbsorbed() {
        return context.defense().getFinalDamage() <= 0;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
