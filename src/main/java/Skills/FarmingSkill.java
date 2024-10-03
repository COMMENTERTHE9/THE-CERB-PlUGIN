package Skills;

import Manager.ResourceYieldManager;
import cerberus.world.cerb.CustomPlayer;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

public class FarmingSkill extends UtilitySkill {

    private final ResourceYieldManager yieldManager;

    public FarmingSkill(String name, ResourceYieldManager yieldManager) {
        super(name);
        this.yieldManager = yieldManager;
    }

    @Override
    public void applyEffect(CustomPlayer customPlayer) {
        if (customPlayer == null) return;
        Player player = customPlayer.getPlayer();

        // Handle experience gain and level up logic here
        int level = this.getLevel();

        // Enhance resource yield from farming based on skill level
        yieldManager.applyYieldBonus(player, "Farming");

        // Apply health and defense bonuses
        double healthBonus = level * 1.5; // Example: +1.5 health per level
        double defenseBonus = level * 1.0; // Example: +1.0 defense per level

        customPlayer.getHealthManager().increaseMaxHealth(player, healthBonus);
        customPlayer.getDefenseManager().increaseDefense(player, defenseBonus);

        // Optionally, you could also add a strength bonus
        double strengthBonus = level * 1.2; // Example: +1.2 strength per level
        customPlayer.getStrengthManager().increaseStrength(player, strengthBonus);

        // Apply crop-specific bonuses if applicable
        applyCropSpecificBonuses(player, level);

        // Apply farming tool effects if the player is using a specialized farming tool
        applyFarmingToolEffects(player, level);
    }

    @Override
    public void applyEffect(Player player) {
        // This method can be used for direct player effects if needed
    }

    // Apply bonuses for specific crops based on the player's farming skill level
    private void applyCropSpecificBonuses(Player player, int level) {
        Block targetBlock = player.getTargetBlockExact(5); // Assuming the player is targeting a crop block within 5 blocks range

        if (targetBlock == null || targetBlock.getType() == Material.AIR) return;

        double bonusMultiplier = 1.0 + (level * 0.1); // Example: 10% yield increase per level

        // Determine the crop type and apply the bonus
        switch (targetBlock.getType()) {
            case WHEAT:
                player.sendMessage("Your farming skill increases the yield for Wheat by " + (bonusMultiplier * 100) + "%.");
                yieldManager.applyYieldBonus(player, "Wheat");
                break;
            case CARROTS:
                player.sendMessage("Your farming skill increases the yield for Carrots by " + (bonusMultiplier * 100) + "%.");
                yieldManager.applyYieldBonus(player, "Carrots");
                break;
            case POTATOES:
                player.sendMessage("Your farming skill increases the yield for Potatoes by " + (bonusMultiplier * 100) + "%.");
                yieldManager.applyYieldBonus(player, "Potatoes");
                break;
            case BEETROOTS:
                player.sendMessage("Your farming skill increases the yield for Beetroots by " + (bonusMultiplier * 100) + "%.");
                yieldManager.applyYieldBonus(player, "Beetroots");
                break;
            case NETHER_WART:
                player.sendMessage("Your farming skill increases the yield for Nether Wart by " + (bonusMultiplier * 100) + "%.");
                yieldManager.applyYieldBonus(player, "Nether Wart");
                break;
            case SUGAR_CANE:
                player.sendMessage("Your farming skill increases the yield for Sugar Cane by " + (bonusMultiplier * 100) + "%.");
                yieldManager.applyYieldBonus(player, "Sugar Cane");
                break;
            case PUMPKIN:
                player.sendMessage("Your farming skill increases the yield for Pumpkins by " + (bonusMultiplier * 100) + "%.");
                yieldManager.applyYieldBonus(player, "Pumpkins");
                break;
            default:
                player.sendMessage("Your farming skill does not apply to this crop.");
                break;
        }
    }

    // Apply effects based on the farming tool the player is using
    private void applyFarmingToolEffects(Player player, int level) {
        ItemStack tool = player.getInventory().getItemInMainHand();
        Block targetBlock = player.getTargetBlockExact(5); // Assuming the player is targeting a crop block within 5 blocks range

        if (tool == null || targetBlock == null || targetBlock.getType() == Material.AIR) return;

        // Check if the tool is tagged for the specific crop type
        if (isToolEffectiveForCrop(tool, targetBlock.getType())) {
            double toolEffectivenessMultiplier = 1.0 + (level * 0.15); // Example: 15% bonus for effective tools
            player.sendMessage("Your " + tool.getItemMeta().getDisplayName() + " grants you an extra " + (toolEffectivenessMultiplier * 100) + "% yield.");
            yieldManager.applyYieldBonus(player, "Farming");
        }
    }

    // Check if the tool the player is using is effective for the specific crop type
    private boolean isToolEffectiveForCrop(ItemStack tool, Material cropType) {
        // Logic to check if the tool is effective for the given crop type
        // For simplicity, let's assume we use tool names or lore to determine effectiveness
        String toolName = tool.getItemMeta().getDisplayName().toLowerCase();

        switch (cropType) {
            case WHEAT:
                return toolName.contains("wheat");
            case CARROTS:
                return toolName.contains("carrot");
            case POTATOES:
                return toolName.contains("potato");
            case BEETROOTS:
                return toolName.contains("beetroot");
            case NETHER_WART:
                return toolName.contains("nether wart");
            case SUGAR_CANE:
                return toolName.contains("sugar cane");
            case PUMPKIN:
                return toolName.contains("pumpkin");
            default:
                return false;
        }
    }
}
