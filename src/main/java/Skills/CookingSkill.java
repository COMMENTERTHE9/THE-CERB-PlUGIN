package Skills;

import Manager.CraftingManager;
import Manager.PlayerVirtualHealthManager;
import org.bukkit.entity.Player;

public class CookingSkill extends UtilitySkill {

    private final CraftingManager craftingManager;
    private final PlayerVirtualHealthManager healthManager;

    public CookingSkill(String name, CraftingManager craftingManager, PlayerVirtualHealthManager healthManager) {
        super(name);
        this.craftingManager = craftingManager;
        this.healthManager = healthManager;
    }

    @Override
    public void applyEffect(Player player) {
        // Handle experience gain and level up logic here
        int level = this.getLevel();

        // Apply cooking bonuses to food items
        double foodQualityBonus = level * 0.05; // Example: +5% better food quality per level
        craftingManager.applyFoodQualityBonus(player, foodQualityBonus);

        // Optionally, you could apply a health increase related to cooking skill
        double healthBonus = level * 1.0; // Example: +1 health per level
        healthManager.increaseMaxHealth(player, healthBonus);
    }
}
