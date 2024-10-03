package Listener;

import Manager.PlayerDefenseUpdateEvent;
import Manager.PlayerHUDManager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.entity.Player;

public class DefenseListener implements Listener {

    private final PlayerHUDManager hudManager;

    public DefenseListener(PlayerHUDManager hudManager) {
        this.hudManager = hudManager;
    }

    @EventHandler
    public void onDefenseUpdate(PlayerDefenseUpdateEvent event) {
        Player player = event.getPlayer();
        double newDefense = event.getNewDefense();

        // Update the player's HUD with the new defense value
        hudManager.updateHUD(player);

        // Optionally, you can add more logic here, such as applying buffs, debuffs, or triggering other events
        // based on the new defense value.
    }
}
