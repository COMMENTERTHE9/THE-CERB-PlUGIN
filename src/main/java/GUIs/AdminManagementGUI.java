package GUIs;

import cerberus.world.cerb.cerb;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.Arrays;

public class AdminManagementGUI {
    private final cerb plugin;

    public AdminManagementGUI(cerb plugin) {
        this.plugin = plugin;
    }

    public void openAdminManagementMenu(Player player) {
        Inventory adminManagementMenu = Bukkit.createInventory(null, 54, ChatColor.AQUA + "Admin Management");

        adminManagementMenu.setItem(20, createGuiItem(Material.GOLD_INGOT, ChatColor.YELLOW, "Manage Economy", "Adjust player economy settings."));
        adminManagementMenu.setItem(22, createGuiItem(Material.PAPER, ChatColor.GREEN, "Manage Rules", "Enforce server rules."));
        adminManagementMenu.setItem(24, createGuiItem(Material.BOOK, ChatColor.RED, "Manage Reports", "Review player reports."));
        adminManagementMenu.setItem(49, createBackButton());

        fillEmptySlotsWithPane(adminManagementMenu);

        player.openInventory(adminManagementMenu);
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

    private ItemStack createGuiItem(Material material, ChatColor color, String name, String... lore) {
        ItemStack item = new ItemStack(material, 1);
        ItemMeta meta = item.getItemMeta();

        meta.setDisplayName(color + name);
        meta.setLore(Arrays.asList(lore));

        item.setItemMeta(meta);

        return item;
    }

    private ItemStack createBackButton() {
        return createGuiItem(Material.BARRIER, ChatColor.RED, "Back", "Return to the previous menu");
    }
}
