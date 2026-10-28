package Effects;

import org.bukkit.entity.Player;

import java.util.List;

/**
 * A system that puts effects on players (a meal, an ability's buff, a potion; later Alchemy and magic) and tells the
 * menus about them. Registered once on {@link ActiveEffects}; the menus need no change for a new one.
 */
@FunctionalInterface
public interface EffectProvider {

    /**
     * Add the effects this system has on {@code p} at {@code now} to {@code out}: only ones still running, read from the
     * system's own state, nothing changed. Called on the player's region thread whenever a screen shows effects (and once
     * a second while one counts down), so it must be cheap.
     */
    void collect(Player p, long now, List<ActiveEffect> out);
}
