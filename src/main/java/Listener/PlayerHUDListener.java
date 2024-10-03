package Listener;

import Manager.PlayerHUDManager;
import Manager.PlayerDefenseManager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.entity.EntityRegainHealthEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.entity.Player;

public class PlayerHUDListener implements Listener {

    private final PlayerHUDManager hudManager;
    private final PlayerDefenseManager defenseManager;

    public PlayerHUDListener(PlayerHUDManager hudManager, PlayerDefenseManager defenseManager) {
        this.hudManager = hudManager;
        this.defenseManager = defenseManager;
    }

    // Event handler to update HUD when player takes damage
    @EventHandler
    public void onPlayerDamage(EntityDamageEvent event) {
        if (event.getEntity() instanceof Player) {
            Player player = (Player) event.getEntity();

            // Update defense when player takes damage
            defenseManager.updateDefense(player);

            // Update the HUD
            hudManager.updateHUD(player);
        }
    }

    // Event handler to update HUD when player regains health
    @EventHandler
    public void onPlayerRegainHealth(EntityRegainHealthEvent event) {
        if (event.getEntity() instanceof Player) {
            Player player = (Player) event.getEntity();

            // Update defense when player regains health
            defenseManager.updateDefense(player);

            // Update the HUD
            hudManager.updateHUD(player);
        }
    }

    // Event handler to update HUD when player consumes items (e.g., potions)
    @EventHandler
    public void onPlayerItemConsume(PlayerItemConsumeEvent event) {
        Player player = event.getPlayer();

        // Update defense when player consumes items
        defenseManager.updateDefense(player);

        // Update the HUD
        hudManager.updateHUD(player);
    }

    // Event handler to update HUD when player joins the game
    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();

        // Update defense when player joins
        defenseManager.updateDefense(player);

        // Update the HUD
        hudManager.updateHUD(player);
    }

    // Event handler to update HUD when player leaves the game (optional, for cleanup)
    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        // Optional: Clear or reset any HUD elements related to the player
    }

    // Event handler to update HUD when a player deals damage to another entity
    @EventHandler
    public void onPlayerDamageEntity(EntityDamageByEntityEvent event) {
        if (event.getDamager() instanceof Player) {
            Player player = (Player) event.getDamager();

            // Update defense when player deals damage
            defenseManager.updateDefense(player);

            // Update the HUD
            hudManager.updateHUD(player);
        }
    }

    // Event handler to update HUD when player dies
    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();

        // Update the HUD upon player death
        hudManager.updateHUD(player);
    }
}
