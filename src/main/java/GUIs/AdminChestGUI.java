package GUIs;

import cerberus.world.cerb.CerberusPlugin;
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

    public void openAdminGUI(Player player) {
        Inventory adminGUI = plugin.getServer().createInventory(null, 27, ChatColor.DARK_PURPLE + "Admin Control Panel");

        // Add items to the admin GUI
        adminGUI.setItem(10, createGuiItem(Material.BOOK, ChatColor.GOLD + "Manage Regions", "View and manage regions"));
        adminGUI.setItem(12, createGuiItem(Material.COMMAND_BLOCK, ChatColor.RED + "Server Commands", "Execute server commands"));
        adminGUI.setItem(14, createGuiItem(Material.PLAYER_HEAD, ChatColor.BLUE + "Manage Players", "Manage player settings"));
        adminGUI.setItem(16, createGuiItem(Material.BARRIER, ChatColor.RED + "Back", "Close the Admin Control Panel"));

        player.openInventory(adminGUI);
    }

    public void openServerCommandsGUI(Player player) {
        Inventory serverCommandsGUI = plugin.getServer().createInventory(null, 27, ChatColor.RED + "Server Commands");

        // Add item to give the player the Player Menu item in the rightmost slot of their hotbar
        serverCommandsGUI.setItem(10, createGuiItem(Material.PLAYER_HEAD, ChatColor.DARK_PURPLE + "Give Player Menu", "Gives the player the Player Menu item in the rightmost slot of their hotbar"));
        serverCommandsGUI.setItem(16, createGuiItem(Material.BARRIER, ChatColor.RED + "Back", "Return to Admin Control Panel"));

        player.openInventory(serverCommandsGUI);
    }

    public void givePlayerMenu(Player player) {
        ItemStack playerMenu = new ItemStack(Material.PLAYER_HEAD, 1);
        ItemMeta meta = playerMenu.getItemMeta();
        meta.setDisplayName(ChatColor.DARK_PURPLE + "Player Menu");
        playerMenu.setItemMeta(meta);

        player.getInventory().setItem(8, playerMenu); // Set the item in the rightmost slot of the hotbar
        player.sendMessage(ChatColor.GREEN + "You have been given the Player Menu item in the rightmost slot of your hotbar.");
    }

    private ItemStack createGuiItem(Material material, String name, String... lore) {
        ItemStack item = new ItemStack(material, 1);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(name);
        meta.setLore(Arrays.asList(lore));
        item.setItemMeta(meta);
        return item;
    }
}