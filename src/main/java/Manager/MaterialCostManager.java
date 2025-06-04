package Manager;

import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.persistence.PersistentDataType;

public class MaterialCostManager {
    private final JavaPlugin plugin;

    public MaterialCostManager(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    // Apply material cost reduction logic based on crafting efficiency
    public void applyMaterialCostReduction(ItemStack item, double efficiencyBonus) {
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            // For example, you could store the crafting efficiency in persistent data
            NamespacedKey efficiencyKey = new NamespacedKey(plugin, "crafting_efficiency");
            meta.getPersistentDataContainer().set(efficiencyKey, PersistentDataType.DOUBLE, efficiencyBonus);
            item.setItemMeta(meta);
        }
    }

    // Enhance item attributes based on crafting efficiency
    public void enhanceItemAttributes(ItemStack item, double efficiencyBonus) {
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            // You could modify item attributes here, such as increasing damage or durability
            NamespacedKey attributeKey = new NamespacedKey(plugin, "enhanced_attributes");
            double currentAttribute = meta.getPersistentDataContainer().getOrDefault(attributeKey, PersistentDataType.DOUBLE, 1.0);
            double newAttribute = currentAttribute + efficiencyBonus;
            meta.getPersistentDataContainer().set(attributeKey, PersistentDataType.DOUBLE, newAttribute);
            item.setItemMeta(meta);
        }
    }
}
