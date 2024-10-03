package Skills;

import Manager.CraftingManager;
import Manager.PlayerManaManager;
import cerberus.world.cerb.CustomPlayer;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

public class AlchemySkill extends UtilitySkill {

    private final CraftingManager craftingManager;
    private final PlayerManaManager manaManager;

    public AlchemySkill(String name, CraftingManager craftingManager, PlayerManaManager manaManager) {
        super(name);
        this.craftingManager = craftingManager;
        this.manaManager = manaManager;
    }

    @Override
    public void applyEffect(CustomPlayer customPlayer) {
        if (customPlayer == null) return;
        Player player = customPlayer.getPlayer();
        int skillLevel = this.getLevel();

        // Retrieve the potion item currently being brewed or crafted
        ItemStack potionItem = customPlayer.getCurrentCraftedItem();  // Assuming this method tracks the potion being brewed

        if (potionItem == null) {
            player.sendMessage("No potion is currently being brewed.");
            return;
        }

        // Apply alchemy-specific bonuses to the potion
        applyPotionEnhancements(player, potionItem, skillLevel);

        // Apply general crafting bonuses (e.g., mana cost reduction for crafting potions)
        craftingManager.applyCraftingBonuses(player, potionItem, "Alchemy");

        // Dynamically unlock advanced alchemy recipes
        dynamicallyUnlockAlchemyRecipes(player, customPlayer, potionItem, skillLevel);

        // Apply mana regeneration bonus based on skill level
        double manaRegenBonus = skillLevel * 1.5;  // Example: +1.5% mana regeneration per skill level
        manaManager.increaseManaRegenRate(player, manaRegenBonus);

        // Optionally, increase max mana as the player levels up alchemy
        double maxManaBonus = skillLevel * 2.0;  // Example: +2 max mana per level
        manaManager.increaseMaxMana(player, maxManaBonus);

        // Notify the player about the bonuses applied
        player.sendMessage("Alchemy bonuses applied: + " + manaRegenBonus + "% Mana Regeneration, + " + maxManaBonus + " Max Mana.");
    }

    @Override
    public void applyEffect(Player player) {
        // This method can be used for direct player effects if needed
    }

    /**
     * Apply potion-specific enhancements such as increased effectiveness, duration, or rarity.
     * @param player The player brewing the potion.
     * @param potionItem The potion item being brewed or crafted.
     * @param skillLevel The player's alchemy skill level.
     */
    private void applyPotionEnhancements(Player player, ItemStack potionItem, int skillLevel) {
        double potionEffectivenessBonus = skillLevel * 0.2;  // Example: +2% potion effectiveness per skill level
        double potionDurationBonus = skillLevel * 0.1;       // Example: +1% potion duration per skill level

        // Increase potion effectiveness (strength of effects)
        craftingManager.increasePotionEffectiveness(player, potionItem, potionEffectivenessBonus);

        // Increase potion duration (how long the effects last)
        craftingManager.increasePotionDuration(player, potionDurationBonus);

        // Chance to brew rare potions at higher levels
        if (skillLevel >= 50 && Math.random() < 0.3) {  // 30% chance to brew a rare potion
            craftingManager.brewRarePotion(player, potionItem);  // Assume this method exists in CraftingManager
            player.sendMessage("You have brewed a rare potion!");
        }

        // Notify the player about the potion enhancements
        player.sendMessage("Potion enhanced: +" + potionEffectivenessBonus + "% Effectiveness, +" + potionDurationBonus + "% Duration.");
    }

    /**
     * Dynamically unlock alchemy recipes and perks based on skill milestones.
     * @param player The player brewing the potion.
     * @param customPlayer The custom player object.
     * @param potionItem The potion item being brewed.
     * @param skillLevel The player's alchemy skill level.
     */
    private void dynamicallyUnlockAlchemyRecipes(Player player, CustomPlayer customPlayer, ItemStack potionItem, int skillLevel) {
        if (skillLevel >= 20 && skillLevel < 50) {
            craftingManager.unlockAdvancedRecipe(customPlayer, potionItem);
            player.sendMessage("You have unlocked improved alchemy techniques!");

        } else if (skillLevel >= 50 && skillLevel < 80) {
            craftingManager.unlockAdvancedRecipe(customPlayer, potionItem);
            player.sendMessage("You have unlocked advanced alchemy recipes!");

        } else if (skillLevel >= 80) {
            // At high levels, introduce a chance to unlock rare and powerful alchemy recipes
            if (Math.random() < 0.4) {  // 40% chance to unlock rare recipes
                craftingManager.unlockAdvancedRecipe(customPlayer, potionItem);
                player.sendMessage("You have unlocked rare and powerful alchemy recipes!");
            }
        }
    }
}
