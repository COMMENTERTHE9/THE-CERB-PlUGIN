package Manager;

import Skills.SkillManager;
import cerberus.world.cerb.CustomPlayer;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.AnimalTamer;
import org.bukkit.entity.Player;
import org.bukkit.entity.Tameable;
import org.bukkit.entity.Wolf;
import org.bukkit.entity.Horse;

public class TamingManager {
    private final SkillManager skillManager;

    public TamingManager(SkillManager skillManager) {
        this.skillManager = skillManager;
    }

    // Enhance taming success rates based on skill levels.
    public boolean enhanceTamingSuccess(Player player, Tameable animal) {
        int tamingLevel = skillManager.getSkillLevel("Animal Taming");

        // Example: Increase taming success rate by 5% per level
        double tamingChance = 0.5 + (tamingLevel * 0.05); // 50% base chance plus skill-based increase

        // Ensure taming chance does not exceed 100%
        tamingChance = Math.min(tamingChance, 1.0);

        return Math.random() <= tamingChance;
    }

    // Apply combat or resource-gathering bonuses to tamed animals.
    public void applyTamedAnimalBonuses(Player player, Tameable animal) {
        int tamingLevel = skillManager.getSkillLevel("Animal Taming");

        // Example: Increase combat effectiveness of tamed animals based on skill level
        double bonusMultiplier = 1.0 + (tamingLevel * 0.1);

        if (animal instanceof Wolf) {
            Wolf wolf = (Wolf) animal;
            // Increase the attack damage of the wolf
            wolf.getAttribute(Attribute.GENERIC_ATTACK_DAMAGE).setBaseValue(
                    wolf.getAttribute(Attribute.GENERIC_ATTACK_DAMAGE).getBaseValue() * bonusMultiplier
            );
            // Increase the wolf's health
            wolf.getAttribute(Attribute.GENERIC_MAX_HEALTH).setBaseValue(
                    wolf.getAttribute(Attribute.GENERIC_MAX_HEALTH).getBaseValue() * bonusMultiplier
            );
            wolf.setHealth(wolf.getAttribute(Attribute.GENERIC_MAX_HEALTH).getBaseValue());
        } else if (animal instanceof Horse) {
            Horse horse = (Horse) animal;
            // Increase the horse's speed
            horse.getAttribute(Attribute.GENERIC_MOVEMENT_SPEED).setBaseValue(
                    horse.getAttribute(Attribute.GENERIC_MOVEMENT_SPEED).getBaseValue() * bonusMultiplier
            );
            // Increase the horse's jump strength (this is specific to horses)
            horse.setJumpStrength(horse.getJumpStrength() * bonusMultiplier);
            // Increase the horse's health
            horse.getAttribute(Attribute.GENERIC_MAX_HEALTH).setBaseValue(
                    horse.getAttribute(Attribute.GENERIC_MAX_HEALTH).getBaseValue() * bonusMultiplier
            );
            horse.setHealth(horse.getAttribute(Attribute.GENERIC_MAX_HEALTH).getBaseValue());
        }
        // Additional logic for other types of tamed animals can be added here
    }

    // Manage custom taming mechanics, like special abilities or behaviors.
    public void manageCustomTaming(Player player, Tameable animal) {
        int tamingLevel = skillManager.getSkillLevel("Animal Taming");

        // Example: Unlock special abilities for tamed animals at higher levels
        if (tamingLevel >= 50) {
            if (animal instanceof Wolf) {
                Wolf wolf = (Wolf) animal;
                // Grant the wolf a special ability, such as increased damage at night
                wolf.setCustomName("Night Stalker");
                // Custom logic to boost damage at night can be added here
            } else if (animal instanceof Horse) {
                Horse horse = (Horse) animal;
                // Grant the horse the ability to automatically regenerate health over time
                horse.setCustomName("Healing Stallion");
                // Custom logic for health regeneration can be added here
            }
            // Additional special abilities for other animals can be implemented here
        }
    }
}
