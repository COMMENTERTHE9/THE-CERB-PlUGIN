package Listener;

import cerberus.world.cerb.events.PlayerDefenseUpdateEvent;  // <<< IMPORT YOUR EVENT
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import Manager.PlayerHUDManager;  // or the correct package for your HUD manager

public class DefenseUpdateListener implements Listener {
    private final PlayerHUDManager hudManager;

    public DefenseUpdateListener(PlayerHUDManager hudManager) {
        this.hudManager = hudManager;
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onDefenseUpdate(PlayerDefenseUpdateEvent event) {
        if (hudManager == null) return;
        Player player = event.getPlayer();  // now resolves
        if (player == null) return;
        hudManager.updateHUD(player);
    }
}
