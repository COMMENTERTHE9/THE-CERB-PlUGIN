package Manager;

import org.bukkit.ChatColor;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;

public class BrokenItemManager {
    private final JavaPlugin plugin;

    public BrokenItemManager(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    // Mark an item as broken, and possibly make it unusable until repaired
    public void markItemAsBroken(ItemStack item) {
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            // Add a "Broken" tag or modify the item display name
            meta.setDisplayName(meta.getDisplayName() + ChatColor.RED + " [Broken]");
            // Optionally add lore or any custom tag to signify it's broken
            item.setItemMeta(meta);
        }
    }

    // Example: Repair an item marked as broken
    public void repairItem(ItemStack item) {
        ItemMeta meta = item.getItemMeta();
        if (meta != null && meta.getDisplayName().contains("[Broken]")) {
            // Remove the broken tag from the display name
            meta.setDisplayName(meta.getDisplayName().replace(" [Broken]", ""));
            item.setItemMeta(meta);
        }
    }
}
