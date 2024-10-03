package Spells;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.List;

public abstract class Ritual {
    private final String name;
    private final List<Location> requiredLocations; // Locations where items must be placed
    private final List<Material> requiredItems; // Items required to complete the ritual

    public Ritual(String name, List<Location> requiredLocations, List<Material> requiredItems) {
        this.name = name;
        this.requiredLocations = requiredLocations;
        this.requiredItems = requiredItems;
    }

    public String getName() {
        return name;
    }

    // Automatically place the required items at the correct locations
    public void placeRequiredItems(Player player) {
        for (int i = 0; i < requiredLocations.size(); i++) {
            Location loc = requiredLocations.get(i);
            Material item = requiredItems.get(i);
            loc.getBlock().setType(item); // Place the item (as a block) at the location
        }
    }

    // Check if the player has the required items and removes them from the inventory
    public boolean checkConditions(Player player) {
        for (Material item : requiredItems) {
            if (!player.getInventory().contains(item)) {
                player.sendMessage("You do not have the required items to start the ritual!");
                return false;
            }
        }

        for (Material item : requiredItems) {
            player.getInventory().removeItem(new ItemStack(item, 1));
        }

        return true;
    }

    // Abstract method to be implemented by specific rituals
    public abstract void performRitual(Player player);

    // Attempt to perform the ritual
    public void attemptRitual(Player player) {
        if (checkConditions(player)) {
            placeRequiredItems(player);
            performRitual(player);
        }
    }
}
