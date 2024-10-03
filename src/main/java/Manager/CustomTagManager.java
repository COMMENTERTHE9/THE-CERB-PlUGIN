package Manager;

import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.NamespacedKey;
import org.bukkit.plugin.Plugin;

public class CustomTagManager {
    private final Plugin plugin;

    public CustomTagManager(Plugin plugin) {
        this.plugin = plugin;
    }

    // Add a custom tag to an item
    public void addCustomTag(ItemStack item, String key, String value) {
        if (item == null || item.getType() == Material.AIR) return;

        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            NamespacedKey namespacedKey = new NamespacedKey(plugin, key);
            meta.getPersistentDataContainer().set(namespacedKey, PersistentDataType.STRING, value);
            item.setItemMeta(meta);
        }
    }

    // Retrieve a custom tag from an item
    public String getCustomTag(ItemStack item, String key) {
        if (item == null || item.getType() == Material.AIR) return null;

        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            NamespacedKey namespacedKey = new NamespacedKey(plugin, key);
            return meta.getPersistentDataContainer().get(namespacedKey, PersistentDataType.STRING);
        }
        return null;
    }

    // Add a custom tag to a block (Placeholder, to be expanded)
    public void addCustomTagToBlock(Block block, String key, String value) {
        // Implement logic for blocks if needed using block state metadata or custom storage
        // This is a placeholder for future expansion
    }

    // Retrieve a custom tag from a block (Placeholder, to be expanded)
    public String getCustomTagFromBlock(Block block, String key) {
        // Implement logic for blocks if needed using block state metadata or custom storage
        // This is a placeholder for future expansion
        return null;
    }
}