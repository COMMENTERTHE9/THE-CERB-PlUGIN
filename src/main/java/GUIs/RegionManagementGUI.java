package GUIs;

import cerberus.world.cerb.cerb;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.List;

public class RegionManagementGUI {

    private final cerb plugin;

    public RegionManagementGUI(cerb plugin) {
        this.plugin = plugin;
    }

    public void openRegionManagementMenu(Player player) {
        Inventory regionManagementMenu = Bukkit.createInventory(null, 54, ChatColor.GOLD + "Region Management");

        // Add items for managing regions
        regionManagementMenu.setItem(10, createGuiItem(Material.MAP, ChatColor.YELLOW + "View Regions", "View and manage all regions"));
        regionManagementMenu.setItem(12, createGuiItem(Material.IRON_DOOR, ChatColor.RED + "Create Region", "Create a new region"));
        regionManagementMenu.setItem(14, createGuiItem(Material.BARRIER, ChatColor.RED + "Delete Region", "Delete an existing region"));

        fillEmptySlotsWithPane(regionManagementMenu);

        player.openInventory(regionManagementMenu);
    }

    public void openMainMenu(Player player) {
        Inventory mainMenu = Bukkit.createInventory(null, 36, ChatColor.DARK_PURPLE + "Main Menu");

        mainMenu.setItem(10, createGuiItem(Material.DIAMOND_SWORD, ChatColor.RED + "Skills", "Manage your skills"));
        mainMenu.setItem(12, createGuiItem(Material.CRAFTING_TABLE, ChatColor.GREEN + "Recipes", "View crafting recipes"));
        mainMenu.setItem(14, createGuiItem(Material.CHEST, ChatColor.GOLD + "Collections", "View your collections"));
        mainMenu.setItem(16, createGuiItem(Material.DRAGON_EGG, ChatColor.LIGHT_PURPLE + "Mobs", "View mobs information"));

        fillEmptySlotsWithPane(mainMenu);

        player.openInventory(mainMenu);
    }

    private void fillEmptySlotsWithPane(Inventory inventory) {
        ItemStack pane = new ItemStack(Material.WHITE_STAINED_GLASS_PANE);
        ItemMeta meta = pane.getItemMeta();
        meta.setDisplayName(" ");
        pane.setItemMeta(meta);

        for (int i = 0; i < inventory.getSize(); i++) {
            if (inventory.getItem(i) == null || inventory.getItem(i).getType() == Material.AIR) {
                inventory.setItem(i, pane);
            }
        }
    }

    private ItemStack createGuiItem(Material material, String name, String... lore) {
        ItemStack item = new ItemStack(material, 1);
        ItemMeta meta = item.getItemMeta();

        meta.setDisplayName(name);
        meta.setLore(List.of(lore));

        item.setItemMeta(meta);

        return item;
    }
}