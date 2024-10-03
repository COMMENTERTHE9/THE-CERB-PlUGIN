package Skills;

import Manager.CraftingManager;
import Listener.CraftingListener; // Import the crafting listener
import cerberus.world.cerb.CustomPlayer;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

public class CraftingSkill extends UtilitySkill {

    private final CraftingManager craftingManager;
    private final CraftingListener craftingListener;  // Add a reference to the crafting listener

    public CraftingSkill(String name, CraftingManager craftingManager, CraftingListener craftingListener) {
        super(name);
        this.craftingManager = craftingManager;
        this.craftingListener = craftingListener;  // Initialize the crafting listener
    }

    @Override
    public void applyEffect(CustomPlayer customPlayer) {
        if (customPlayer == null) return;
        Player player = customPlayer.getPlayer();

        // Handle experience gain and level up logic here
        int level = this.getLevel();

        // Apply crafting efficiency bonuses
        craftingManager.applyCraftingEfficiency(player, level);  // Adjusted to use player's skill level

        // Retrieve the crafted item using the crafting listener
        ItemStack craftedItem = getCraftedItem(player);
        if (craftedItem != null) {
            craftingManager.applyCraftingBonuses(player, craftedItem, "Crafting");
        }

        // Unlock special crafting recipes or perks at higher levels
        if (level >= 50) {
            craftingManager.unlockAdvancedRecipe(customPlayer, null);  // Adjusted to match method signature
        }

        // Increase player's max health slightly as they level up crafting
        double healthBonus = level * 5.0; // Example: +5 health per level
        customPlayer.getHealthManager().increaseMaxHealth(player, healthBonus);
    }

    @Override
    public void applyEffect(Player player) {
        // Apply crafting effect directly to the player if needed, but currently unused
    }

    /**
     * Retrieve the last crafted item for the player.
     */
    private ItemStack getCraftedItem(Player player) {
        // Use the crafting listener to get the last crafted item
        ItemStack craftedItem = craftingListener.getLastCraftedItem(player);

        // Once retrieved, clear the entry
        craftingListener.clearLastCraftedItem(player);

        return craftedItem;
    }
}
