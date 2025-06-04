package Listener;

import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.EventPriority;


public class SpeedLimiterListener implements Listener {
    private static final double MAX_MOVEMENT_SPEED = 0.2 * (1 + 0.5); // 50% increase over default

    // ------------------------------------------------------------
// Speed‑limit on player join (pattern‑aligned version)
// ------------------------------------------------------------
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)    // keep flags consistent
    public void onPlayerJoin(PlayerJoinEvent event) {

        Player player = event.getPlayer();

        // EARLY EXIT – should never be null, but guard just in case
        if (player.getAttribute(Attribute.GENERIC_MOVEMENT_SPEED) == null) return;

        enforceSpeedLimit(player);
    }

    /**
     * Resets the player’s movement speed and removes over‑limit modifiers.
     */
    private void enforceSpeedLimit(Player player) {
        // reset base value to vanilla default
        player.getAttribute(Attribute.GENERIC_MOVEMENT_SPEED)
                .setBaseValue(0.1);

        // strip modifiers that push the total speed above your cap
        double totalSpeed = player.getAttribute(Attribute.GENERIC_MOVEMENT_SPEED).getValue();
        if (totalSpeed > MAX_MOVEMENT_SPEED) {
            player.getAttribute(Attribute.GENERIC_MOVEMENT_SPEED)
                    .getModifiers()
                    .forEach(mod -> player.getAttribute(Attribute.GENERIC_MOVEMENT_SPEED)
                            .removeModifier(mod));
            // (Re‑apply allowed modifiers here if needed)
        }
    }
}
