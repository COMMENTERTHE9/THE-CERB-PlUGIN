package Skills;

import Manager.CraftingManager;
import Manager.PlayerDefenseManager;
import cerberus.world.cerb.CustomPlayer;
import org.bukkit.entity.Player;

public class SurvivalSkill extends UtilitySkill {

    private final PlayerDefenseManager defenseManager;
    private final CraftingManager craftingManager; // Integrate CraftingManager for consumables

    public SurvivalSkill(String name, PlayerDefenseManager defenseManager, CraftingManager craftingManager) {
        super(name);
        this.defenseManager = defenseManager;
        this.craftingManager = craftingManager;
    }

    @Override
    public void applyEffect(CustomPlayer customPlayer) {
        if (customPlayer == null) return;
        Player player = customPlayer.getPlayer();
        int level = this.getLevel();

        // Apply toughness bonus based on skill level
        double toughnessBonus = level * 1000.0; // Example: +1000 toughness per level
        defenseManager.increaseArmorToughness(player, toughnessBonus);

        // Apply damage resistance bonus based on skill level
        double resistanceBonus = level * 0.05; // Example: +5% resistance per level
        defenseManager.increaseDamageResistance(player, resistanceBonus);

        // Additional bonuses for levels above 100
        if (level > 100) {
            double highLevelToughnessBonus = (level - 100) * 2000.0;
            defenseManager.increaseArmorToughness(player, highLevelToughnessBonus);

            double highLevelResistanceBonus = (level - 100) * 0.1;
            defenseManager.increaseDamageResistance(player, highLevelResistanceBonus);
        }

        // Apply environmental resistances based on skill level
        applyEnvironmentalResistances(player, level);

        // Update both normal and virtual health systems
        defenseManager.updateDefense(player);
    }

    @Override
    public void applyEffect(Player player) {
        // Optional direct player effects
    }

    // Add logic for environmental resistances based on survival skill level
    private void applyEnvironmentalResistances(Player player, int level) {
        // Fire resistance: Reduces fire damage taken
        double fireResistanceBonus = level * 0.03; // Example: +3% fire resistance per level

        // Cold resistance: Reduces damage from cold environments
        double coldResistanceBonus = level * 0.02; // Example: +2% cold resistance per level

        // Drowning resistance: Increases time before drowning damage is applied
        double drowningResistanceBonus = level * 0.01; // Example: +1% longer breath holding per level

        // Placeholder logic for applying these resistances
        System.out.println("Fire resistance increased by " + fireResistanceBonus * 100 + "%.");
        System.out.println("Cold resistance increased by " + coldResistanceBonus * 100 + "%.");
        System.out.println("Drowning resistance increased by " + drowningResistanceBonus * 100 + "%.");
    }

    // Optionally connect the skill with crafting-related food bonuses
    public void enhanceSurvivalConsumables(Player player, int level) {
        double foodBonus = level * 0.05; // Example: +5% bonus to food effectiveness
        craftingManager.applyFoodQualityBonus(player, foodBonus); // Using CraftingManager for consumables
    }
}
