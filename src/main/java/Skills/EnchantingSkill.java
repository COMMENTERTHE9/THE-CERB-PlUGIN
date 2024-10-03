package Skills;

import Manager.CraftingManager;
import Manager.PlayerManaManager;
import cerberus.world.cerb.CustomPlayer;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.enchantments.Enchantment;

public class EnchantingSkill extends UtilitySkill {

    private final CraftingManager craftingManager;
    private final PlayerManaManager manaManager;

    public EnchantingSkill(String name, CraftingManager craftingManager, PlayerManaManager manaManager) {
        super(name);
        this.craftingManager = craftingManager;
        this.manaManager = manaManager;
    }

    @Override
    public void applyEffect(CustomPlayer customPlayer) {
        if (customPlayer == null) return;
        Player player = customPlayer.getPlayer();
        int skillLevel = this.getLevel();

        // Retrieve the item to be enchanted
        ItemStack itemToEnchant = customPlayer.getCurrentCraftedItem();

        if (itemToEnchant == null) {
            player.sendMessage("You don't have an item ready for enchanting.");
            return;
        }

        // Enhance the item with enchantment bonuses based on skill
        enhanceEnchantments(player, itemToEnchant, skillLevel);

        // Apply general crafting bonuses
        craftingManager.applyCraftingBonuses(player, itemToEnchant, "Enchanting");

        // Boost mana regeneration and max mana as a side benefit of enchanting expertise
        double manaRegenBoost = skillLevel * 0.02;
        double maxManaIncrease = skillLevel * 1.5;

        manaManager.increaseManaRegenRate(player, manaRegenBoost);
        manaManager.increaseMaxMana(player, maxManaIncrease);

        // Unlock advanced enchanting techniques at higher levels
        unlockAdvancedEnchantingRecipes(player, customPlayer, itemToEnchant, skillLevel);

        // Notify the player about the bonuses applied
        player.sendMessage("Your enchanting has improved: +" + manaRegenBoost + "% Mana Regen, +" + maxManaIncrease + " Max Mana.");
    }

    @Override
    public void applyEffect(Player player) {
        // This method can be used for direct player effects if needed
    }

    /**
     * Apply bonuses to enchantments based on skill level.
     * @param player The player enchanting the item.
     * @param itemToEnchant The item to be enchanted.
     * @param skillLevel The player's enchanting skill level.
     */
    private void enhanceEnchantments(Player player, ItemStack itemToEnchant, int skillLevel) {
        double enchantmentBoost = skillLevel * 0.03; // Example: +3% enchantment power per level

        ItemMeta meta = itemToEnchant.getItemMeta();
        if (meta != null) {
            if (meta.hasEnchant(Enchantment.SHARPNESS)) { // Example: Boost Sharpness
                int currentLevel = meta.getEnchantLevel(Enchantment.SHARPNESS);
                meta.addEnchant(Enchantment.SHARPNESS, currentLevel + (int) enchantmentBoost, true);
            } else {
                // If item has no enchantments, apply Unbreaking as a base enchantment
                meta.addEnchant(Enchantment.UNBREAKING, (int) Math.min(skillLevel / 10, 3), true); // Cap Unbreaking at level 3
            }
            itemToEnchant.setItemMeta(meta);
        }

        player.sendMessage("Enchantments enhanced: +" + enchantmentBoost + "% effectiveness.");
    }

    /**
     * Unlock advanced enchanting recipes dynamically based on skill milestones.
     * @param player The player enchanting the item.
     * @param customPlayer The custom player object.
     * @param itemToEnchant The item to be enchanted.
     * @param skillLevel The player's enchanting skill level.
     */
    private void unlockAdvancedEnchantingRecipes(Player player, CustomPlayer customPlayer, ItemStack itemToEnchant, int skillLevel) {
        if (skillLevel >= 20 && skillLevel < 50) {
            craftingManager.unlockAdvancedRecipe(customPlayer, itemToEnchant);
            player.sendMessage("New enchanting techniques unlocked!");

        } else if (skillLevel >= 50 && skillLevel < 80) {
            craftingManager.unlockAdvancedRecipe(customPlayer, itemToEnchant);
            player.sendMessage("You can now enchant with greater power!");

        } else if (skillLevel >= 80) {
            if (Math.random() < 0.4) {  // 40% chance for rare recipes
                craftingManager.unlockAdvancedRecipe(customPlayer, itemToEnchant);
                player.sendMessage("Rare enchanting recipes unlocked!");
            }
        }
    }
}
