package Skills;

import Manager.ResourceYieldManager;
import cerberus.world.cerb.CustomPlayer;
import org.bukkit.entity.Player;

public class MiningSkill extends UtilitySkill {

    private final ResourceYieldManager yieldManager;

    public MiningSkill(String name, ResourceYieldManager yieldManager) {
        super(name);
        this.yieldManager = yieldManager;
    }

    @Override
    public void applyEffect(Player player) {
        CustomPlayer cp = CustomPlayer.get(player.getUniqueId());
        if (cp == null) return;

        // Handle experience gain and level up logic
        int level = this.getLevel();
        // Experience gain and level-up logic assumed to be handled by the system

        // Enhance resource yield from mining based on skill level
        yieldManager.applyYieldBonus(player, "Mining");

        // Apply health and strength bonuses (defense is now gear-based, no longer granted by gathering skills)
        double healthBonus = level * 2.0; // Example: +2 health per level
        double strengthBonus = level * 1.0; // Example: +1 strength per level

        cp.getHealthManager().increaseMaxHealth(player, healthBonus);
        cp.getStrengthManager().increaseStrength(player, strengthBonus);

        // Trigger special effects for mining specific materials (placeholder logic)
        triggerSpecialEffects(player, level);
    }

    private void triggerSpecialEffects(Player player, int level) {
        // Placeholder logic for special effects when mining certain materials
        // This could include finding rare gems, triggering events, etc.
        double specialEffectChance = 0.02 + (level * 0.01); // Example: Base 2% chance, increasing by 1% per level

        if (Math.random() <= specialEffectChance) {
            // Example effect: Finding a rare gem
            player.sendMessage("You have discovered a rare gem while mining!");
            // In the full system, this might spawn an item or trigger an event
        }
    }
}
