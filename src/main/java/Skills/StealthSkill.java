package Skills;

import Manager.CraftingManager;
import cerberus.world.cerb.CustomPlayer;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

public class StealthSkill extends UtilitySkill {

    private final CraftingManager craftingManager;
    private final NamespacedKey stealthTagKey;

    public StealthSkill(String name, CraftingManager craftingManager, NamespacedKey stealthTagKey) {
        super(name);
        this.craftingManager = craftingManager;
        this.stealthTagKey = stealthTagKey;  // Key to identify stealth-related items
    }

    @Override
    public void applyEffect(CustomPlayer customPlayer) {
        if (customPlayer == null) return;
        Player player = customPlayer.getPlayer();

        // Handle experience gain and level up logic here
        int level = this.getLevel();

        // Retrieve the actual crafted or used item for stealth enhancement
        ItemStack craftedItem = getTaggedStealthItemFromInventory(player);

        if (craftedItem != null) {
            // Apply stealth enhancement bonuses based on skill level
            double stealthBonus = level * 0.05; // Example: +5% stealth effectiveness per level
            craftingManager.enhanceStealthItems(player, craftedItem, stealthBonus);

            // Notify the player about the bonus applied
            player.sendMessage("Stealth bonus applied: +" + (stealthBonus * 100) + "% stealth effectiveness.");
        } else {
            player.sendMessage("No tagged stealth item found to enhance.");
        }

        // Additional effects for the StealthSkill can be added here (e.g., sneak speed, invisibility bonuses, etc.)
    }

    @Override
    public void applyEffect(Player player) {
        // This method can be used for direct player effects if needed
    }

    // Method to retrieve the tagged stealth item from the player's inventory
    private ItemStack getTaggedStealthItemFromInventory(Player player) {
        // Retrieve the item in the player's main hand
        ItemStack mainHandItem = player.getInventory().getItemInMainHand();

        // Check if the item has the stealth tag
        if (hasStealthTag(mainHandItem)) {
            return mainHandItem;
        }

        // You can add additional checks for other inventory slots if needed

        return null; // Return null if no tagged stealth item is found
    }

    // Helper method to check if an item has the stealth tag
    private boolean hasStealthTag(ItemStack item) {
        if (item == null || item.getItemMeta() == null) return false;

        ItemMeta meta = item.getItemMeta();
        // Check for the custom "stealth_item" tag in the item's PersistentDataContainer
        return meta.getPersistentDataContainer().has(stealthTagKey, PersistentDataType.STRING);
    }
}
