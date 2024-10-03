package Listener;

import Manager.PlayerDefenseManager;
import Manager.PlayerHUDManager;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

public class CustomArmorListener implements Listener {

    private final PlayerDefenseManager defenseManager;
    private final PlayerHUDManager hudManager;

    public CustomArmorListener(PlayerDefenseManager defenseManager, PlayerHUDManager hudManager) {
        this.defenseManager = defenseManager;
        this.hudManager = hudManager;
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) return;

        Player player = (Player) event.getWhoClicked();
        updateDefense(player);
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player)) return;

        Player player = (Player) event.getPlayer();
        updateDefense(player);
    }

    private void updateDefense(Player player) {
        // Recalculate defense based on currently worn armor
        defenseManager.updateDefense(player);

        // Update HUD to reflect the defense change
        hudManager.updateHUD(player);
    }
}
