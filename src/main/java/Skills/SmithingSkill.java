package Skills;

import Manager.CraftingManager;
import Manager.PlayerVirtualHealthManager;
import Manager.PlayerStrengthManager;
import cerberus.world.cerb.CustomPlayer;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

public class SmithingSkill extends UtilitySkill {
    private final CraftingManager craftingManager;
    private final PlayerVirtualHealthManager virtualHealthManager;
    private final PlayerStrengthManager strengthManager;

    public SmithingSkill(String name, CraftingManager craftingManager, PlayerVirtualHealthManager virtualHealthManager, PlayerStrengthManager strengthManager) {
        super(name);
        this.craftingManager = craftingManager;
        this.virtualHealthManager = virtualHealthManager;
        this.strengthManager = strengthManager;
    }

    @Override
    public void applyEffect(CustomPlayer customPlayer) {
        if (customPlayer == null) return;
        Player player = customPlayer.getPlayer();
        int skillLevel = this.getLevel();

        // Retrieve the item that is currently being crafted or modified
        ItemStack craftedItem = customPlayer.getCurrentCraftedItem(); // Assuming customPlayer holds the current item being crafted

        if (craftedItem == null) {
            player.sendMessage("No item is currently being crafted.");
            return;
        }

        // Apply smithing-specific bonuses to the crafted item
        craftingManager.applySmithingBonuses(player, craftedItem, skillLevel);

        // Apply general crafting bonuses to the crafted item
        craftingManager.applyCraftingBonuses(player, craftedItem, "Smithing");

        // Dynamically unlock recipes and perks based on milestones and chance
        dynamicallyUnlockRecipes(player, customPlayer, craftedItem, skillLevel);

        // Apply Virtual Health bonus based on the skill level
        double virtualHealthBonus = skillLevel * 1.5; // Example: +1.5 virtual health per level
        virtualHealthManager.increasePlayerVirtualHealth(player, virtualHealthBonus);

        // Apply strength bonus using the correct method
        double strengthBonus = skillLevel * 1.0; // Example: +1.0 strength per level
        strengthManager.increaseStrength(player, strengthBonus);

        // Notify the player about the bonuses applied
        player.sendMessage("Smithing bonuses applied: + " + virtualHealthBonus + " Virtual Health, + " + strengthBonus + " Strength.");
    }

    @Override
    public void applyEffect(Player player) {
        // This method can be used for direct player effects if needed
    }

    /**
     * Dynamically unlock recipes and perks at various skill levels.
     * @param player The player who is crafting.
     * @param customPlayer The custom player object.
     * @param craftedItem The item being crafted.
     * @param skillLevel The player's smithing skill level.
     */
    private void dynamicallyUnlockRecipes(Player player, CustomPlayer customPlayer, ItemStack craftedItem, int skillLevel) {
        if (skillLevel >= 10 && skillLevel < 30) {
            craftingManager.unlockAdvancedRecipe(customPlayer, craftedItem);
            player.sendMessage("You have unlocked basic smithing enhancements!");

        } else if (skillLevel >= 30 && skillLevel < 50) {
            craftingManager.unlockAdvancedRecipe(customPlayer, craftedItem);
            player.sendMessage("You have unlocked improved smithing techniques!");

        } else if (skillLevel >= 50) {
            // Add a chance-based system for rare recipe unlocks at higher levels
            double chance = Math.random();
            if (chance < 0.3) {  // 30% chance to unlock rare recipes
                craftingManager.unlockAdvancedRecipe(customPlayer, craftedItem);
                player.sendMessage("You have unlocked rare and powerful smithing recipes!");
            }

            player.sendMessage("Your smithing skill is at its peak, allowing rare recipe unlocks!");
        }
    }
}
