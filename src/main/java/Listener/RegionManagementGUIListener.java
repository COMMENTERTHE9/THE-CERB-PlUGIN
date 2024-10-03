package Listener;

import cerberus.world.cerb.cerb;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryMoveItemEvent;

public class RegionManagementGUIListener implements Listener {

    private final cerb plugin;

    public RegionManagementGUIListener(cerb plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) {
            return;
        }

        Player player = (Player) event.getWhoClicked();
        String inventoryTitle = event.getView().getTitle();

        if (inventoryTitle.equals(ChatColor.DARK_PURPLE + "Region Management")) {
            event.setCancelled(true);

            if (event.getCurrentItem() == null || !event.getCurrentItem().hasItemMeta()) {
                return;
            }

            String itemName = event.getCurrentItem().getItemMeta().getDisplayName();

            if (itemName.equals(ChatColor.RED + "Back")) {
                plugin.getRegionManagementGUI().openMainMenu(player);
            }
        }
    }

    @EventHandler
    public void onInventoryDrag(InventoryDragEvent event) {
        String inventoryTitle = event.getView().getTitle();
        if (inventoryTitle.equals(ChatColor.DARK_PURPLE + "Region Management")) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onInventoryMoveItem(InventoryMoveItemEvent event) {
        if (event.getInitiator().getHolder() instanceof Player) {
            Player player = (Player) event.getInitiator().getHolder();
            String inventoryTitle = player.getOpenInventory().getTitle();
            if (inventoryTitle.equals(ChatColor.DARK_PURPLE + "Region Management")) {
                event.setCancelled(true);
            }
        }
    }
}