package Listener;

import Manager.PlayerManaManager;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.entity.PlayerDeathEvent;

public class ManaListener implements Listener {

    private final PlayerManaManager playerManaManager;

    public ManaListener(PlayerManaManager playerManaManager) {
        this.playerManaManager = playerManaManager;
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        playerManaManager.startManaRegenTask(player);
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        // Handle any necessary cleanup or save operations here
    }

    @EventHandler
    public void onPlayerRespawn(PlayerRespawnEvent event) {
        Player player = event.getPlayer();
        playerManaManager.resetMana(playerManaManager.getMaxMana(player)); // Reset mana to full on respawn
        playerManaManager.startManaRegenTask(player); // Restart mana regeneration
    }

    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();
        playerManaManager.resetMana( 0); // Reset mana to 0 on death
    }

    // Additional event handlers can be added as needed to respond to other mana-related events.
    // For example, you could handle custom events that drain or boost mana, or monitor specific actions that affect mana.
}
