package Listener;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.EventPriority;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class CraftingListener implements Listener {

    // A map to store the last crafted item for each player
    private final Map<UUID, ItemStack> lastCraftedItem = new HashMap<>();

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onItemCraft(CraftItemEvent event) {

        // EARLY EXIT: only care about players
        if (!(event.getWhoClicked() instanceof Player player)) return;

        // EARLY EXIT: no meaningful result
        ItemStack result = event.getInventory().getResult();
        if (result == null || result.getType().isAir()) return;

        // Store a clone of the crafted item
        lastCraftedItem.put(player.getUniqueId(), result.clone());
    }

    // Method to retrieve the last crafted item for a player
    public ItemStack getLastCraftedItem(Player player) {
        return lastCraftedItem.getOrDefault(player.getUniqueId(), null);
    }

    // Method to clear the last crafted item once it's retrieved
    public void clearLastCraftedItem(Player player) {
        lastCraftedItem.remove(player.getUniqueId());
    }
}
