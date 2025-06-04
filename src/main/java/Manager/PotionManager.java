package Manager;

import Skills.SkillManager;
import cerberus.world.cerb.CustomPlayer;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;

public class PotionManager {
    private final SkillManager skillManager;

    public PotionManager(SkillManager skillManager) {
        this.skillManager = skillManager;
    }

    // Apply skill-based bonuses to potion brewing (e.g., increased potency, reduced brewing time).
    public PotionEffect applyPotionBonuses(Player player, PotionEffect effect) {
        int alchemyLevel = skillManager.getSkillLevel("Alchemy");

        // Example: Increase potion potency based on alchemy level
        int newDuration = (int) (effect.getDuration() * (1.0 + (alchemyLevel * 0.05)));
        return new PotionEffect(effect.getType(), newDuration, effect.getAmplifier());
    }

    // Manage unlocking of special potions based on skill levels.
    public boolean unlockSpecialPotion(CustomPlayer customPlayer) {
        int alchemyLevel = skillManager.getSkillLevel("Alchemy");

        // Example: Unlock special potions at higher levels
        if (alchemyLevel >= 75) {
            // Logic to unlock special potion
            return true;
        }

        return false;
    }

    // Enhance potion effects dynamically based on player skills.
    public PotionEffect enhancePotionEffect(CustomPlayer customPlayer, PotionEffect effect) {
        int alchemyLevel = skillManager.getSkillLevel("Alchemy");

        // Example: Add an extra level to the potion effect based on skill level
        return new PotionEffect(effect.getType(), effect.getDuration(), effect.getAmplifier() + 1);
    }
}
