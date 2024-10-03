package Listener;

import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

public class SpeedLimiterListener implements Listener {
    private static final double MAX_MOVEMENT_SPEED = 0.2 * (1 + 0.5); // 50% increase over default

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        enforceSpeedLimit(player);
    }

    // Additional events to cover other cases (e.g., equipment changes, potion effects)

    private void enforceSpeedLimit(Player player) {
        player.getAttribute(Attribute.GENERIC_MOVEMENT_SPEED).setBaseValue(0.1); // Reset to default base value

        // Remove any movement speed modifiers that exceed the maximum allowed speed
        double totalMovementSpeed = player.getAttribute(Attribute.GENERIC_MOVEMENT_SPEED).getValue();
        if (totalMovementSpeed > MAX_MOVEMENT_SPEED) {
            player.getAttribute(Attribute.GENERIC_MOVEMENT_SPEED).getModifiers().forEach(modifier -> {
                player.getAttribute(Attribute.GENERIC_MOVEMENT_SPEED).removeModifier(modifier);
            });
            // Optionally reapply allowed modifiers here
        }
    }
}
