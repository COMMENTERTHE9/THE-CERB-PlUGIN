package Skills;

import Manager.CraftingManager;
import Manager.PlayerDefenseManager;
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
    public void applyEffect(Player player) {
        if (player == null) return;
        int level = this.getLevel();

        // Toughness and resistance bonuses are now handled automatically in
        // PlayerDefenseManager.applyElementalResistances(). Trigger a recalc.
        defenseManager.updateDefense(player);

        // Apply environmental resistances based on skill level
        applyEnvironmentalResistances(player, level);
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
    }

    // Optionally connect the skill with crafting-related food bonuses
    public void enhanceSurvivalConsumables(Player player, int level) {
        double foodBonus = level * 0.05; // Example: +5% bonus to food effectiveness
        craftingManager.applyFoodQualityBonus(player, foodBonus); // Using CraftingManager for consumables
    }
}
