package Manager;

import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Player;

public class PlayerStrengthManager {

    // Method to get the player's current strength
    public double getCurrentStrength(Player player) {
        return player.getAttribute(Attribute.GENERIC_ATTACK_DAMAGE).getValue(); // Returns the current strength (damage) of the player
    }

    // Method to increase the player's strength
    public void increaseStrength(Player player, double amount) {
        double currentStrength = getCurrentStrength(player);
        player.getAttribute(Attribute.GENERIC_ATTACK_DAMAGE).setBaseValue(currentStrength + amount); // Increase strength (damage)
    }

    // Method to decrease the player's strength
    public void decreaseStrength(Player player, double amount) {
        double currentStrength = getCurrentStrength(player);
        player.getAttribute(Attribute.GENERIC_ATTACK_DAMAGE).setBaseValue(Math.max(0, currentStrength - amount)); // Decrease strength (damage), ensure it doesn't go below zero
    }
}
