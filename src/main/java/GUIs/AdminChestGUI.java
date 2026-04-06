package GUIs;

import cerberus.world.cerb.CerberusPlugin;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.Arrays;

public class AdminChestGUI {
    private final CerberusPlugin plugin;

    public AdminChestGUI(CerberusPlugin plugin) {
        this.plugin = plugin;
    }

    public ItemStack createAdminChest() {
        ItemStack adminChest = new ItemStack(Material.ENDER_CHEST, 1);
        ItemMeta meta = adminChest.getItemMeta();
        meta.setDisplayName(ChatColor.DARK_PURPLE + "Admin Control Panel");
        meta.setLore(Arrays.asList(ChatColor.GRAY + "Right-click to open the Admin Control Panel"));
        adminChest.setItemMeta(meta);
        return adminChest;
    }

    public boolean isAdminChest(ItemStack item) {
        if (item == null || item.getType() != Material.ENDER_CHEST) {
            return false;
        }
        ItemMeta meta = item.getItemMeta();
        return meta != null && meta.getDisplayName().equals(ChatColor.DARK_PURPLE + "Admin Control Panel");
    }

    /**
     * Open the admin control panel using GuiBuilder/GuiIdentity. Folia-safe.
     */
    public void open(Player player) {
        Inventory inv = GuiBuilder.createGui(GuiIdentity.ADMIN_PANEL, 3,
                ChatColor.DARK_PURPLE + "Admin Control Panel");

        inv.setItem(10, GuiBuilder.createActionItem(Material.BOOK,
                ChatColor.GOLD + "Manage Regions",
                "open_regions", null,
                ChatColor.GRAY + "View and manage regions"));

        inv.setItem(12, GuiBuilder.createActionItem(Material.COMMAND_BLOCK,
                ChatColor.RED + "Server Commands",
                "open_commands", null,
                ChatColor.GRAY + "Execute server commands"));

        inv.setItem(14, GuiBuilder.createActionItem(Material.PLAYER_HEAD,
                ChatColor.BLUE + "Manage Players",
                "open_players", null,
                ChatColor.GRAY + "Manage player settings"));

        inv.setItem(16, GuiBuilder.createActionItem(Material.BARRIER,
                ChatColor.RED + "Close",
                "back", null,
                ChatColor.GRAY + "Close the Admin Control Panel"));

        GuiBuilder.fillEmpty(inv, Material.GRAY_STAINED_GLASS_PANE);

        // Folia-safe open
        player.getScheduler().run(plugin, (ScheduledTask t) -> player.openInventory(inv), null);
    }

    /**
     * @deprecated Use {@link #open(Player)} instead. Kept for backward compatibility.
     */
    @Deprecated
    public void openAdminGUI(Player player) {
        open(player);
    }

    public void givePlayerMenu(Player player) {
        ItemStack playerMenu = new ItemStack(Material.PLAYER_HEAD, 1);
        ItemMeta meta = playerMenu.getItemMeta();
        meta.setDisplayName(ChatColor.DARK_PURPLE + "Player Menu");
        playerMenu.setItemMeta(meta);

        player.getInventory().setItem(8, playerMenu);
        player.sendMessage(ChatColor.GREEN + "You have been given the Player Menu item in the rightmost slot of your hotbar.");
    }
}