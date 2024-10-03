package Listener;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class CraftingListener implements Listener {

    // A map to store the last crafted item for each player
    private final Map<UUID, ItemStack> lastCraftedItem = new HashMap<>();

    @EventHandler
    public void onItemCraft(CraftItemEvent event) {
        if (event.getWhoClicked() instanceof Player) {
            Player player = (Player) event.getWhoClicked();
            ItemStack craftedItem = event.getCurrentItem();

            // Store the crafted item
            lastCraftedItem.put(player.getUniqueId(), craftedItem);
        }
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
