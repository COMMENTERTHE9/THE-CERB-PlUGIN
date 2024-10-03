package Traps;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

public class TrapManager implements Listener {

    private final JavaPlugin plugin;

    public TrapManager(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    // Method to enhance the potency of traps
    public void enhanceTrapPotency(Player player, double potencyBonus) {
        // Logic to increase the potency of traps set by the player
    }

    // Method to enhance the trigger sensitivity of traps
    public void enhanceTrapTrigger(Player player, double triggerSensitivityBonus) {
        // Logic to make traps trigger more easily or quickly
    }

    // Method to enhance the duration of traps
    public void enhanceTrapDuration(Player player, double durationBonus) {
        // Logic to extend trap's active duration
    }

    // Automatically apply the trap tag to block items
    public void applyTrapTagAutomatically(ItemStack item) {
        if (item != null && item.getType().isBlock()) {  // Ensure it's a block item
            item.getItemMeta().getPersistentDataContainer().set(
                    new NamespacedKey(plugin, "trap_item"), PersistentDataType.STRING, "true");
        }
    }

    // Method to check if an item is tagged as a trap
    public boolean isTrapItem(ItemStack item) {
        if (item != null && item.getItemMeta() != null) {
            return item.getItemMeta().getPersistentDataContainer().has(
                    new NamespacedKey(plugin, "trap_item"), PersistentDataType.STRING);
        }
        return false;
    }

    // Event handler to detect when a custom trap item is crafted
    @EventHandler
    public void onItemCraft(CraftItemEvent event) {
        ItemStack resultItem = event.getRecipe().getResult(); // Get the result of the crafting recipe
        if (isTrapRecipe(resultItem)) {
            applyTrapTagAutomatically(resultItem); // Automatically tag it as a trap
        }
    }

    // Check if the crafted item matches the trap recipe
    private boolean isTrapRecipe(ItemStack item) {
        // Define the criteria for an item to be considered a "trap item"
        // Customize this to match specific items or recipes
        return item.getType() == Material.TRIPWIRE_HOOK || item.getType() == Material.TNT;  // Example of trap items
    }
}
