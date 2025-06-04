package Listener;

import GUIs.AdminChestGUI;
import cerberus.world.cerb.CerberusPlugin;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.EventPriority;
import org.bukkit.inventory.ItemStack;

public class AdminGUIListener implements Listener {
    private final CerberusPlugin plugin;
    private final AdminChestGUI adminChestGUI;

    public AdminGUIListener(CerberusPlugin plugin, AdminChestGUI adminChestGUI) {
        this.plugin = plugin;
        this.adminChestGUI = adminChestGUI;
    }

    // ------------------------------------------------------------
// Admin Chest & Server‑Command GUIs
// ------------------------------------------------------------
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)   // <<< NEW flags
    public void onInventoryClick(InventoryClickEvent e) {

        /* ---------- EARLY‑EXIT FILTERS ---------- */
        if (!(e.getWhoClicked() instanceof Player player)) return;          // pattern‑var (Java 16+)
        String title = e.getView().getTitle();
        if (!title.equals(ChatColor.DARK_PURPLE + "Admin Control Panel")
                && !title.equals(ChatColor.RED + "Server Commands")) return; // not our GUI

        e.setCancelled(true);                                               // lock inventory

        ItemStack clicked = e.getCurrentItem();
        if (clicked == null || !clicked.hasItemMeta()) return;              // nothing useful
        String itemName = clicked.getItemMeta().getDisplayName();

        /* ---------- ADMIN CONTROL PANEL ---------- */
        if (title.equals(ChatColor.DARK_PURPLE + "Admin Control Panel")) {

            if (itemName.equals(ChatColor.GOLD + "Manage Regions")) {
                plugin.getRegionManagementGUI().openRegionManagementMenu(player);
                return;
            }
            if (itemName.equals(ChatColor.RED + "Server Commands")) {
                adminChestGUI.openServerCommandsGUI(player);
                return;
            }
            if (itemName.equals(ChatColor.BLUE + "Manage Players")) {
                // TODO: implement manage‑players GUI
                return;
            }
            if (itemName.equals(ChatColor.RED + "Back")) {
                player.closeInventory();
                return;
            }
        }

        /* ------------- SERVER COMMANDS GUI ------------- */
        if (title.equals(ChatColor.RED + "Server Commands")) {

            if (itemName.equals(ChatColor.DARK_PURPLE + "Give Player Menu")) {
                adminChestGUI.givePlayerMenu(player);
                return;
            }
            if (itemName.equals(ChatColor.RED + "Back")) {
                adminChestGUI.openAdminGUI(player);
            }
        }
    }
}