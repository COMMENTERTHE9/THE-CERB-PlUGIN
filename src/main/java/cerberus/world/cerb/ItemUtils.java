package cerberus.world.cerb;

import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.Arrays;
import java.util.UUID;

public class ItemUtils {

    private final CerberusPlugin plugin;
    private final NamespacedKey REGION_SELECTOR_KEY;
    private final NamespacedKey UNREGIONIZER_KEY;

    public ItemUtils(CerberusPlugin plugin) {
        this.plugin = plugin;
        this.REGION_SELECTOR_KEY = new NamespacedKey(plugin, "region_selector");
        this.UNREGIONIZER_KEY = new NamespacedKey(plugin, "unregionizer");
    }

    public ItemStack createRegionSelector() {
        ItemStack item = new ItemStack(Material.GOLDEN_AXE);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(ChatColor.GOLD + "Region Selector");
            meta.setLore(Arrays.asList(
                    ChatColor.GRAY + "Left-click to set the first point",
                    ChatColor.GRAY + "Right-click to set the second point"
            ));
            meta.getPersistentDataContainer().set(
                    REGION_SELECTOR_KEY,
                    PersistentDataType.STRING,
                    UUID.randomUUID().toString()
            );
            item.setItemMeta(meta);
        }
        return item;
    }

    public ItemStack createUnregionizer() {
        ItemStack item = new ItemStack(Material.BLAZE_ROD);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(ChatColor.RED + "Unregionizer");
            meta.setLore(Arrays.asList(
                    ChatColor.GRAY + "Right-click to remove protection from a region"
            ));
            meta.getPersistentDataContainer().set(
                    UNREGIONIZER_KEY,
                    PersistentDataType.STRING,
                    UUID.randomUUID().toString()
            );
            item.setItemMeta(meta);
        }
        return item;
    }

    public boolean isRegionSelector(ItemStack item) {
        return item != null &&
                item.getType() == Material.GOLDEN_AXE &&
                item.hasItemMeta() &&
                item.getItemMeta().getPersistentDataContainer().has(
                        REGION_SELECTOR_KEY, PersistentDataType.STRING
                );
    }

    public boolean isUnregionizer(ItemStack item) {
        return item != null &&
                item.getType() == Material.BLAZE_ROD &&
                item.hasItemMeta() &&
                item.getItemMeta().getPersistentDataContainer().has(
                        UNREGIONIZER_KEY, PersistentDataType.STRING
                );
    }
}
