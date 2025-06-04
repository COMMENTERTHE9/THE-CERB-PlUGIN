package Listener;

import Manager.PlayerManaManager;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.EventPriority;

public class ManaListener implements Listener {

    private final PlayerManaManager playerManaManager;

    public ManaListener(PlayerManaManager playerManaManager) {
        this.playerManaManager = playerManaManager;
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        if (player == null) return;          // early‑exit guard
        playerManaManager.startManaRegenTask(player);
    }


    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        if (player == null) return;

        // Example cleanup: stop regen task, save mana to DB, etc.
        // playerManaManager.stopManaRegenTask(player);
    }


    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onPlayerRespawn(PlayerRespawnEvent event) {
        Player player = event.getPlayer();
        if (player == null) return;

        playerManaManager.resetMana(playerManaManager.getMaxMana(player));
        playerManaManager.startManaRegenTask(player);
    }


    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();
        if (player == null) return;

        playerManaManager.resetMana(0);
    }


    // Additional event handlers can be added as needed to respond to other mana-related events.
    // For example, you could handle custom events that drain or boost mana, or monitor specific actions that affect mana.
}
