package Listener;

import GUIs.AdminChestGUI;
import cerberus.world.cerb.cerb;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;

public class AdminGUIListener implements Listener {
    private final cerb plugin;
    private final AdminChestGUI adminChestGUI;

    public AdminGUIListener(cerb plugin, AdminChestGUI adminChestGUI) {
        this.plugin = plugin;
        this.adminChestGUI = adminChestGUI;
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) return;
        Player player = (Player) event.getWhoClicked();
        String title = event.getView().getTitle();

        if (title.equals(ChatColor.DARK_PURPLE + "Admin Control Panel") || title.equals(ChatColor.RED + "Server Commands")) {
            event.setCancelled(true);
            if (event.getCurrentItem() == null) return;

            String itemName = event.getCurrentItem().getItemMeta().getDisplayName();
            if (title.equals(ChatColor.DARK_PURPLE + "Admin Control Panel")) {
                if (itemName.equals(ChatColor.GOLD + "Manage Regions")) {
                    plugin.getRegionManagementGUI().openRegionManagementMenu(player);
                } else if (itemName.equals(ChatColor.RED + "Server Commands")) {
                    adminChestGUI.openServerCommandsGUI(player);
                } else if (itemName.equals(ChatColor.BLUE + "Manage Players")) {
                    // Handle Manage Players logic here
                } else if (itemName.equals(ChatColor.RED + "Back")) {
                    player.closeInventory();
                }
            } else if (title.equals(ChatColor.RED + "Server Commands")) {
                if (itemName.equals(ChatColor.DARK_PURPLE + "Give Player Menu")) {
                    adminChestGUI.givePlayerMenu(player);
                } else if (itemName.equals(ChatColor.RED + "Back")) {
                    adminChestGUI.openAdminGUI(player);
                }
            }
        }
    }
}