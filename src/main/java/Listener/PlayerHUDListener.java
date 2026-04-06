package Listener;

import Manager.PlayerHUDManager;
import Manager.PlayerDefenseManager;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityRegainHealthEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;

/**
 * Keeps HUD and defense stats in sync on damage/heal/consume events.
 * Join/quit/death lifecycle moved to PlayerSessionManager.
 */
public class PlayerHUDListener implements Listener {

    private final PlayerHUDManager hudManager;
    private final PlayerDefenseManager defenseManager;

    public PlayerHUDListener(PlayerHUDManager hudManager,
                             PlayerDefenseManager defenseManager) {
        this.hudManager = hudManager;
        this.defenseManager = defenseManager;
    }

    private void refresh(Player player) {
        defenseManager.updateDefense(player);
        hudManager.updateHUD(player);
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onEntityDamage(EntityDamageEvent e) {
        if (!(e.getEntity() instanceof Player p)) return;
        refresh(p);
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onEntityRegain(EntityRegainHealthEvent e) {
        if (!(e.getEntity() instanceof Player p)) return;
        refresh(p);
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onConsume(PlayerItemConsumeEvent e) {
        if (!(e.getPlayer() instanceof Player p)) return;
        refresh(p);
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onDamageDealt(EntityDamageByEntityEvent e) {
        if (!(e.getDamager() instanceof Player p)) return;
        refresh(p);
    }
}
