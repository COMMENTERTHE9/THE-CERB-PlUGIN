package cerberus.world.cerb;

import Manager.PlayerVirtualHealthManager;
import Manager.PlayerDefenseManager;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.NamespacedKey;
import org.bukkit.Material;
import org.bukkit.attribute.Attribute;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.Vector;

public class QualityBuffs {
    private final Player player;
    private final PlayerVirtualHealthManager virtualHealthManager;
    private final PlayerDefenseManager defenseManager;
    private final JavaPlugin plugin;

    // Constructor to initialize the class
    public QualityBuffs(Player player, PlayerVirtualHealthManager virtualHealthManager, PlayerDefenseManager defenseManager, JavaPlugin plugin) {
        this.player = player;
        this.virtualHealthManager = virtualHealthManager;
        this.defenseManager = defenseManager;
        this.plugin = plugin;
    }

    // Method to apply buffs based on item quality
    public void applyQualityBuffs(Player player, ItemStack item) {
        if (item == null || item.getType() == Material.AIR) return;

        ItemMeta meta = item.getItemMeta();
        if (meta == null) return;

        // Retrieve the item's quality from the persistent data container
        String qualityName = meta.getPersistentDataContainer().get(new NamespacedKey(plugin, "item_quality"), PersistentDataType.STRING);
        if (qualityName == null) return;

        Quality quality = Quality.valueOf(qualityName.toUpperCase());

        // Apply buffs based on the item type (weapon, armor, tool, ranged weapon)
        if (isWeapon(item)) {
            applyWeaponBuffs(quality, item);
        } else if (isArmor(item)) {
            applyArmorBuffs(quality, item);
        } else if (isTool(item)) {
            applyToolBuffs(quality, item);
        } else if (isRangedWeapon(item)) {
            applyRangedWeaponBuffs(quality, item);
        }
    }

    // Method to remove buffs from the player when an item is unequipped or removed
    public void removeQualityBuffs(Player player) {
        // Reset player stats to default values when removing buffs
        virtualHealthManager.setPlayerMaxVirtualHealth(player, 100.0); // Reset virtual health
        defenseManager.setDefense(player, 10.0); // Reset defense

        // Reset vanilla Minecraft stats
        player.getAttribute(Attribute.GENERIC_MAX_HEALTH).setBaseValue(20.0);  // Default health
        if (player.getAttribute(Attribute.GENERIC_ATTACK_DAMAGE) != null) {
            player.getAttribute(Attribute.GENERIC_ATTACK_DAMAGE).setBaseValue(1.0);  // Default attack damage
        }
        if (player.getAttribute(Attribute.GENERIC_ARMOR) != null) {
            player.getAttribute(Attribute.GENERIC_ARMOR).setBaseValue(0.0);  // Default armor
        }
    }

    // Method to apply weapon-specific buffs
    private void applyWeaponBuffs(Quality quality, ItemStack item) {
        double multiplier = quality.getMultiplier();

        // Increase weapon damage based on quality
        item.addUnsafeEnchantment(Enchantment.SHARPNESS, (int) (multiplier * 3));

        // Apply knockback reduction for the player using the weapon
        virtualHealthManager.setKnockbackReductionFactor(player, 1 - (multiplier * 0.1));
    }

    // Method to apply armor-specific buffs
    private void applyArmorBuffs(Quality quality, ItemStack item) {
        double multiplier = quality.getMultiplier();

        // Buff defense and knockback reduction based on quality
        double defenseBuff = multiplier * 5;
        player.getAttribute(Attribute.GENERIC_ARMOR).setBaseValue(defenseBuff);

        // Reduce knockback for higher-quality armor
        double knockbackReduction = 1 - (multiplier * 0.2);
        virtualHealthManager.setKnockbackReductionFactor(player, knockbackReduction);
    }

    // Method to apply tool-specific buffs (farming, woodcutting, fishing, etc.)
    private void applyToolBuffs(Quality quality, ItemStack item) {
        double multiplier = quality.getMultiplier();

        // Buff efficiency and durability based on tool quality
        item.addUnsafeEnchantment(Enchantment.EFFICIENCY, (int) (multiplier * 2));
        item.addUnsafeEnchantment(Enchantment.UNBREAKING, (int) (multiplier * 1));

        // Send message indicating the tool's buffs
        player.sendMessage("Tool Buff Applied: Yield Bonus: " + (multiplier * 100) + "%, Effectiveness Bonus: " + (multiplier * 100) + "%");
    }

    // Method to apply ranged weapon-specific buffs (bows, crossbows, tridents, etc.)
    private void applyRangedWeaponBuffs(Quality quality, ItemStack item) {
        double multiplier = quality.getMultiplier();

        // Apply bonus to ranged attack damage
        if (player.getAttribute(Attribute.GENERIC_ATTACK_DAMAGE) != null) {
            double baseDamage = player.getAttribute(Attribute.GENERIC_ATTACK_DAMAGE).getBaseValue();
            player.getAttribute(Attribute.GENERIC_ATTACK_DAMAGE).setBaseValue(baseDamage * multiplier);
        }

        // Apply bonus to arrow or projectile velocity
        Vector velocity = player.getLocation().getDirection();
        velocity.multiply(1.0 + (multiplier * 0.1)); // Increase velocity by quality multiplier
        player.getLocation().setDirection(velocity);

        // Reduce bow/crossbow draw time based on quality
        int drawSpeedReductionTicks = Math.min(20, (int) (multiplier * 10));  // Example: reduce draw time by up to 10 ticks
    }

    // Helper method to check if the item is a weapon
    private boolean isWeapon(ItemStack item) {
        return item.getType().name().endsWith("_SWORD") || item.getType().name().endsWith("_AXE");
    }

    // Helper method to check if the item is armor
    private boolean isArmor(ItemStack item) {
        return item.getType().name().endsWith("_HELMET") || item.getType().name().endsWith("_CHESTPLATE") ||
                item.getType().name().endsWith("_LEGGINGS") || item.getType().name().endsWith("_BOOTS");
    }

    // Helper method to check if the item is a tool
    private boolean isTool(ItemStack item) {
        return item.getType().name().endsWith("_PICKAXE") || item.getType().name().endsWith("_SHOVEL") || item.getType().name().endsWith("_HOE") ||
                item.getType().name().endsWith("_FISHING_ROD");
    }

    // Helper method to check if the item is a ranged weapon (bows, crossbows, tridents)
    private boolean isRangedWeapon(ItemStack item) {
        return item.getType() == Material.BOW || item.getType() == Material.CROSSBOW || item.getType() == Material.TRIDENT;
    }

    // Enum for item quality with multipliers for buffs
    public enum Quality {
        WEAK("#7f7f7f", "Weak", 0.5),        // 50% buff
        COMMON("#ffffff", "Common", 1.0),    // 100% buff
        UNCOMMON("#00ff00", "Uncommon", 1.25),  // 125% buff
        RARE("#0000ff", "Rare", 1.5),        // 150% buff
        EPIC("#9400d3", "Epic", 1.75),       // 175% buff
        PERFECT("#ffd700", "Perfect", 2.0),  // 200% buff
        GODLY("#ff4500", "Godly", 2.75),     // 275% buff
        PRIMORDIAL("#8a2be2", "Primordial", 3.5); // 350% buff

        private final String hexColor;
        private final String name;
        private final double multiplier;

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
            return multiplier;
        }
    }
}
