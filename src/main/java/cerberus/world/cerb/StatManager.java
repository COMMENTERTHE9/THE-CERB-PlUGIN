package cerberus.world.cerb;

import Manager.PlayerDefenseManager;
import Manager.PlayerManaManager;
import Manager.PlayerVirtualHealthManager;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.attribute.Attribute;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.NamespacedKey;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

public class StatManager {

    private final PlayerVirtualHealthManager virtualHealthManager;
    private final PlayerManaManager manaManager;
    private final PlayerDefenseManager defenseManager;
    private final JavaPlugin plugin; // Store the plugin instance

    // Constructor to initialize the plugin and managers
    public StatManager(PlayerVirtualHealthManager virtualHealthManager, PlayerManaManager manaManager, PlayerDefenseManager defenseManager, JavaPlugin plugin) {
        this.virtualHealthManager = virtualHealthManager;
        this.manaManager = manaManager;
        this.defenseManager = defenseManager;
        this.plugin = plugin;  // Initialize the plugin
    }

    // Method to apply stat adjustments while the player holds a high-quality item
    public void applyTemporaryQualityBuffs(Player player, ItemStack item) {
        // Check the item quality and apply relevant buffs
        Quality quality = getItemQuality(item);

        if (quality == null) return;  // If no quality tag is found, exit early

        double qualityMultiplier = quality.getMultiplier(); // Get the multiplier from the item quality

        // 1. Apply virtual health buffs
        double currentMaxVirtualHealth = virtualHealthManager.getPlayerMaxVirtualHealth(player);
        virtualHealthManager.setPlayerMaxVirtualHealth(player, currentMaxVirtualHealth * qualityMultiplier);

        // 2. Apply mana buffs
        double currentMaxMana = manaManager.getMaxMana(player);
        manaManager.setMaxMana(currentMaxMana * qualityMultiplier);

        // 3. Apply defense buffs
        double currentDefense = defenseManager.getDefense(player);
        defenseManager.setDefense(player, currentDefense * qualityMultiplier);

        // 4. Apply vanilla Minecraft attributes like health, armor, and attack damage
        double baseHealth = player.getAttribute(Attribute.GENERIC_MAX_HEALTH).getBaseValue();
        player.getAttribute(Attribute.GENERIC_MAX_HEALTH).setBaseValue(baseHealth * qualityMultiplier);

        if (player.getAttribute(Attribute.GENERIC_ATTACK_DAMAGE) != null) {
            double baseAttackDamage = player.getAttribute(Attribute.GENERIC_ATTACK_DAMAGE).getBaseValue();
            player.getAttribute(Attribute.GENERIC_ATTACK_DAMAGE).setBaseValue(baseAttackDamage * qualityMultiplier);
        }

        if (player.getAttribute(Attribute.GENERIC_ARMOR) != null) {
            double baseArmor = player.getAttribute(Attribute.GENERIC_ARMOR).getBaseValue();
            player.getAttribute(Attribute.GENERIC_ARMOR).setBaseValue(baseArmor * qualityMultiplier);
        }
    }

    // Method to remove the stat modifications when the player unequips the item
    public void removeTemporaryQualityBuffs(Player player) {
        // Reset custom and vanilla stats to their original values
        virtualHealthManager.setPlayerMaxVirtualHealth(player, 100.0);  // Reset to base max virtual health
        manaManager.setMaxMana(1000.0);  // Reset mana to base
        defenseManager.setDefense(player, 10.0);  // Reset defense

        // Reset vanilla Minecraft stats
        player.getAttribute(Attribute.GENERIC_MAX_HEALTH).setBaseValue(20.0);  // Default health
        if (player.getAttribute(Attribute.GENERIC_ATTACK_DAMAGE) != null) {
            player.getAttribute(Attribute.GENERIC_ATTACK_DAMAGE).setBaseValue(1.0);  // Default attack damage
        }
        if (player.getAttribute(Attribute.GENERIC_ARMOR) != null) {
            player.getAttribute(Attribute.GENERIC_ARMOR).setBaseValue(0.0);  // Default armor
        }
    }

    // Helper method to get the quality of an item based on custom tags or lore
    private Quality getItemQuality(ItemStack item) {
        if (item == null) return null;

        ItemMeta meta = item.getItemMeta();
        if (meta == null) return null;

        // Check for custom quality tag
        if (meta.getPersistentDataContainer().has(new NamespacedKey(plugin, "item_quality"), PersistentDataType.STRING)) {
            String qualityName = meta.getPersistentDataContainer().get(new NamespacedKey(plugin, "item_quality"), PersistentDataType.STRING);
            return Quality.valueOf(qualityName.toUpperCase());
        }

        // If no custom tag, return null
        return null;
    }

    // Define the Quality enum with multipliers for each quality tier
    public enum Quality {
        WEAK("#7f7f7f", "Weak", 0.5),
        COMMON("#ffffff", "Common", 1.0),
        UNCOMMON("#00ff00", "Uncommon", 1.2),
        RARE("#0000ff", "Rare", 1.5),
        EPIC("#9400d3", "Epic", 1.8),
        PERFECT("#ffd700", "Perfect", 2.0),
        GODLY("#ff4500", "Godly", 2.5),
        PRIMORDIAL("#8a2be2", "Primordial", 3.0);

        private final String hexColor;
        private final String name;
        private final double multiplier;  // Quality multiplier for stats

        Quality(String hexColor, String name, double multiplier) {
            this.hexColor = hexColor;
            this.name = name;
            this.multiplier = multiplier;
        }

        public String getHexColor() {
            return hexColor;
        }

        public String getName() {
            return name;
        }

        public double getMultiplier() {
            return multiplier;  // Return the stat multiplier for this quality
        }
    }
}
