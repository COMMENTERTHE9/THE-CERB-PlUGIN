package GUIs;

import cerberus.world.cerb.CerberusPlugin;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;

public class RegionManagementGUI {

    private final CerberusPlugin plugin;

    public RegionManagementGUI(CerberusPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Open the region management menu using GuiBuilder/GuiIdentity. Folia-safe.
     */
    public void openRegionManagementMenu(Player player) {
        Inventory inv = GuiBuilder.createGui(GuiIdentity.REGION_MANAGEMENT, 3,
                ChatColor.GOLD + "Region Management");

        inv.setItem(10, GuiBuilder.createActionItem(Material.MAP,
                ChatColor.YELLOW + "View Regions",
                "view_regions", null,
                ChatColor.GRAY + "View and manage all regions"));

        inv.setItem(12, GuiBuilder.createActionItem(Material.IRON_DOOR,
                ChatColor.RED + "Create Region",
                "create_region", null,
                ChatColor.GRAY + "Create a new region"));

        inv.setItem(14, GuiBuilder.createActionItem(Material.BARRIER,
                ChatColor.RED + "Delete Region",
                "delete_region", null,
                ChatColor.GRAY + "Delete an existing region"));

        inv.setItem(22, GuiBuilder.createBackArrow("admin_panel"));

        GuiBuilder.fillEmpty(inv, Material.GRAY_STAINED_GLASS_PANE);

        // Folia-safe open
        player.getScheduler().run(plugin, (ScheduledTask t) -> player.openInventory(inv), null);
    }
}