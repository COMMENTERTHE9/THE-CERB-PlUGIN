package Skills;

import Manager.ResourceYieldManager;
import cerberus.world.cerb.CustomPlayer;
import org.bukkit.entity.Player;

public class WoodcuttingSkill extends UtilitySkill {

    private final ResourceYieldManager yieldManager;

    public WoodcuttingSkill(String name, ResourceYieldManager yieldManager) {
        super(name);
        this.yieldManager = yieldManager;
    }

    @Override
    public void applyEffect(CustomPlayer customPlayer) {
        if (customPlayer == null) return;
        Player player = customPlayer.getPlayer();

        // Handle experience gain and level up logic here
        int level = this.getLevel();

        // Enhance resource yield from woodcutting based on skill level
        yieldManager.applyYieldBonus(player, "Woodcutting");

        // Apply health, defense, and strength bonuses
        double healthBonus = level * 1.5; // Example: +1.5 health per level
        double defenseBonus = level * 1.0; // Example: +1.0 defense per level
        double strengthBonus = level * 0.5; // Example: +0.5 strength per level

        customPlayer.getHealthManager().increaseMaxHealth(player, healthBonus);
        customPlayer.getDefenseManager().increaseDefense(player, defenseBonus);
        customPlayer.getStrengthManager().increaseStrength(player, strengthBonus);
    }

    @Override
    public void applyEffect(Player player) {
        // This method can be used for direct player effects if needed
    }
}
