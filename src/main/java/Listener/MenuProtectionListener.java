package Listener;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.plugin.java.JavaPlugin;
import cerberus.world.cerb.cerb;

public class MenuProtectionListener implements Listener {

    private final cerb plugin;

    public MenuProtectionListener(cerb plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (event.getClickedInventory() != null) {
            String inventoryTitle = event.getView().getTitle();

            // Prevent moving items in protected inventories
            if (inventoryTitle.equals("Main Player Menu") ||
                    inventoryTitle.equals("Skill Management") ||
                    inventoryTitle.equals("Combat Skills") ||
                    inventoryTitle.equals("Magic Skills") ||
                    inventoryTitle.equals("Utility Skills") ||
                    inventoryTitle.equals("Admin Control Panel") ||
                    inventoryTitle.equals("Player Management") ||
                    inventoryTitle.equals("Server Settings") ||
                    inventoryTitle.equals("Advanced Settings")) {
                event.setCancelled(true);
            }
        }
    }

    @EventHandler
    public void onInventoryDrag(InventoryDragEvent event) {
        String inventoryTitle = event.getView().getTitle();

        // Prevent dragging items in protected inventories
        if (inventoryTitle.equals("Main Player Menu") ||
                inventoryTitle.equals("Skill Management") ||
                inventoryTitle.equals("Combat Skills") ||
                inventoryTitle.equals("Magic Skills") ||
                inventoryTitle.equals("Utility Skills") ||
                inventoryTitle.equals("Admin Control Panel") ||
                inventoryTitle.equals("Player Management") ||
                inventoryTitle.equals("Server Settings") ||
                inventoryTitle.equals("Advanced Settings")) {
            event.setCancelled(true);
        }
    }
}
