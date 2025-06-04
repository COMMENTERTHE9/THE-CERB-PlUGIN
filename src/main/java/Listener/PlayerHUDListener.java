package Listener;

import Manager.PlayerHUDManager;
import Manager.PlayerDefenseManager;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;                  // <<< NEW
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityRegainHealthEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.entity.PlayerDeathEvent;

/**
 * Central listener to keep the player's HUD and defense stat in sync.
 */
public class PlayerHUDListener implements Listener {

    private final PlayerHUDManager hudManager;
    private final PlayerDefenseManager defenseManager;

    public PlayerHUDListener(PlayerHUDManager hudManager,
                             PlayerDefenseManager defenseManager) {
        this.hudManager = hudManager;
        this.defenseManager = defenseManager;
    }

    /** Common update routine */
    private void refresh(Player player) {                         // <<< NEW helper
        defenseManager.updateDefense(player);
        hudManager.updateHUD(player);
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)   // <<< NEW flags
    public void onEntityDamage(EntityDamageEvent e) {
        if (!(e.getEntity() instanceof Player p)) return;         // EARLY‑EXIT
        refresh(p);
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)   // <<< NEW flags
    public void onEntityRegain(EntityRegainHealthEvent e) {
        if (!(e.getEntity() instanceof Player p)) return;
        refresh(p);
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)   // <<< NEW flags
    public void onConsume(PlayerItemConsumeEvent e) {
        if (!(e.getPlayer() instanceof Player p)) return;
        refresh(p);
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)   // <<< NEW flags
    public void onJoin(PlayerJoinEvent e) {
        refresh(e.getPlayer());
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)   // <<< NEW flags
    public void onQuit(PlayerQuitEvent e) {
        // Optionally clean up any per‑player tasks in hudManager
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)   // <<< NEW flags
    public void onDamageDealt(EntityDamageByEntityEvent e) {
        if (!(e.getDamager() instanceof Player p)) return;
        refresh(p);
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)   // <<< NEW flags
    public void onDeath(PlayerDeathEvent e) {
        Player p = e.getEntity();
        refresh(p);
    }
}
