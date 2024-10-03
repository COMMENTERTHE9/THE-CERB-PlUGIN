package Skills;

import Manager.CraftingManager;
import Manager.PlayerVirtualHealthManager;
import cerberus.world.cerb.CustomPlayer;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

public class FirstAidSkill extends UtilitySkill {

    private final PlayerVirtualHealthManager healthManager;
    private final CraftingManager craftingManager;

    public FirstAidSkill(String name, PlayerVirtualHealthManager healthManager, CraftingManager craftingManager) {
        super(name);
        this.healthManager = healthManager;
        this.craftingManager = craftingManager;
    }

    @Override
    public void applyEffect(CustomPlayer customPlayer) {
        if (customPlayer == null) return;
        Player player = customPlayer.getPlayer();

        // Handle experience gain and level up logic here
        int level = this.getLevel();

        // Health boost based on skill level
        double healthBoost = level * 1500.0; // Example: +1500 health per level
        healthManager.increaseMaxHealth(player, healthBoost);

        // Regeneration enhancement based on skill level
        double regenBoost = level * 1000.0; // Example: +1000 health regeneration per level
        healthManager.applyHealthRegen(player, regenBoost, 20L * 10); // Regenerate over 10 seconds (20 ticks = 1 second)

        // Additional health boost scaling more steeply at higher levels
        if (level > 100) {
            double highLevelBoost = (level - 100) * 3000.0; // Example: +3000 health per level above 100
            healthManager.increaseMaxHealth(player, highLevelBoost);
        }

        // Enhance healing items based on skill level
        ItemStack healingItem = getHealingItem(player); // Assuming you get the healing item from the player's inventory or event
        if (healingItem != null) {
            double healingBonus = level * 0.10; // Example: +10% healing effectiveness per level
            craftingManager.enhanceHealingItems(player, healingItem, healingBonus); // Apply the enhancement to the healing item
        }
    }

    @Override
    public void applyEffect(Player player) {
        // This method can be used for direct player effects if needed
    }

    // Example method to retrieve the healing item from the player's inventory
    private ItemStack getHealingItem(Player player) {
        return player.getInventory().getItemInMainHand(); // Example: Get the item in the player's main hand
    }
}
