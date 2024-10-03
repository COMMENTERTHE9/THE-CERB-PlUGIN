package Manager;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.Map;

public class FoodAttributeManager {

    private final Map<Material, FoodAttributes> foodAttributesMap;

    public FoodAttributeManager() {
        this.foodAttributesMap = new HashMap<>();
        initializeFoodAttributes();
    }

    // Initialize default food attributes for each food item
    private void initializeFoodAttributes() {
        // Example food attributes
        foodAttributesMap.put(Material.APPLE, new FoodAttributes(4, 2.4f)); // 4 hunger points, 2.4 saturation
        foodAttributesMap.put(Material.COOKED_BEEF, new FoodAttributes(8, 12.8f)); // 8 hunger points, 12.8 saturation
        foodAttributesMap.put(Material.BREAD, new FoodAttributes(5, 6.0f)); // 5 hunger points, 6.0 saturation
        // Add more food items as needed
    }

    // Retrieve food attributes based on the item
    public FoodAttributes getFoodAttributes(ItemStack item) {
        return foodAttributesMap.get(item.getType());
    }

    // Apply the custom food attributes to a player
    public void applyFoodAttributes(Player player, ItemStack foodItem, double saturationMultiplier, double hungerBonus) {
        FoodAttributes attributes = getFoodAttributes(foodItem);
        if (attributes != null) {
            double newHungerPoints = attributes.getHungerPoints() + hungerBonus;
            double newSaturation = attributes.getSaturation() * saturationMultiplier;

            // Logic to apply these values to the player when they consume the food
            player.setFoodLevel(Math.min(player.getFoodLevel() + (int) newHungerPoints, 20)); // Cap food level at 20
            player.setSaturation(Math.min(player.getSaturation() + (float) newSaturation, player.getFoodLevel())); // Cap saturation at food level
        }
    }

    // Inner class to hold food attributes
    public static class FoodAttributes {
        private final int hungerPoints;
        private final float saturation;

        public FoodAttributes(int hungerPoints, float saturation) {
            this.hungerPoints = hungerPoints;
            this.saturation = saturation;
        }

        public int getHungerPoints() {
            return hungerPoints;
        }

        public float getSaturation() {
            return saturation;
        }
    }
}
