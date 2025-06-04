package Listener;

import cerberus.world.cerb.CerberusPlugin;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryMoveItemEvent;
import org.bukkit.event.EventPriority;
import org.bukkit.inventory.ItemStack;


public class RegionManagementGUIListener implements Listener {

    private final CerberusPlugin plugin;

    public RegionManagementGUIListener(CerberusPlugin plugin) {
        this.plugin = plugin;
    }

    // ------------------------------------------------------------
// Block clicks inside the “Region Management” GUI
// ------------------------------------------------------------
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent event) {

        if (!(event.getWhoClicked() instanceof Player player)) return;   // EARLY EXIT 1
        if (!event.getView().getTitle().equals(ChatColor.DARK_PURPLE + "Region Management"))
            return;                                                      // EARLY EXIT 2

        event.setCancelled(true);                                        // lock GUI

        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || !clicked.hasItemMeta()) return;           // EARLY EXIT 3

        String itemName = clicked.getItemMeta().getDisplayName();
        if (itemName.equals(ChatColor.RED + "Back")) {
            plugin.getRegionManagementGUI().openMainMenu(player);
        }
    }


    // ------------------------------------------------------------
// Prevent drag events in the GUI
// ------------------------------------------------------------
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onInventoryDrag(InventoryDragEvent event) {

        if (event.getView().getTitle()
                .equals(ChatColor.DARK_PURPLE + "Region Management")) {
            event.setCancelled(true);
        }
    }


    // ------------------------------------------------------------
// Stop hoppers or other inventories from pulling items out
// ------------------------------------------------------------
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onInventoryMoveItem(InventoryMoveItemEvent event) {

        if (!(event.getInitiator().getHolder() instanceof Player player)) return;  // EARLY EXIT

        if (player.getOpenInventory().getTitle()
                .equals(ChatColor.DARK_PURPLE + "Region Management")) {
            event.setCancelled(true);
        }
    }
}