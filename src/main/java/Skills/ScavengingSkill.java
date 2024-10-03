package Skills;

import Manager.LuckManager;
import Manager.MagicFindManager;
import cerberus.world.cerb.CustomPlayer;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.Random;

public class ScavengingSkill extends UtilitySkill implements Listener {

    private final LuckManager luckManager;
    private final MagicFindManager magicFindManager;
    private final JavaPlugin plugin;

    // NamespacedKeys for different chest tiers
    private final NamespacedKey commonChestKey;
    private final NamespacedKey uncommonChestKey;
    private final NamespacedKey rareChestKey;
    private final NamespacedKey epicChestKey;
    private final NamespacedKey legendaryChestKey;

    // NamespacedKey for scavenging-related items
    private final NamespacedKey scavengingItemKey;

    public ScavengingSkill(String name, LuckManager luckManager, MagicFindManager magicFindManager, JavaPlugin plugin) {
        super(name);
        this.luckManager = luckManager;
        this.magicFindManager = magicFindManager;
        this.plugin = plugin;

        // Initialize the NamespacedKeys for the chest tiers
        this.commonChestKey = new NamespacedKey(plugin, "common_chest");
        this.uncommonChestKey = new NamespacedKey(plugin, "uncommon_chest");
        this.rareChestKey = new NamespacedKey(plugin, "rare_chest");
        this.epicChestKey = new NamespacedKey(plugin, "epic_chest");
        this.legendaryChestKey = new NamespacedKey(plugin, "legendary_chest");

        // Initialize the NamespacedKey for scavenging items
        this.scavengingItemKey = new NamespacedKey(plugin, "scavenging_item");
    }

    @Override
    public void applyEffect(CustomPlayer customPlayer) {
        if (customPlayer == null) return;
        Player player = customPlayer.getPlayer();

        // Handle experience gain and level-up logic here
        int level = this.getLevel();

        // Increase Luck based on skill level
        double luckBonus = level * 0.5; // Example: +0.5% luck per level
        luckManager.increaseLuck(player, luckBonus);

        // Increase Magic Find based on skill level
        double magicFindBonus = level * 0.2; // Example: +0.2% magic find per level
        magicFindManager.increaseMagicFind(player, magicFindBonus);
    }

    @Override
    public void applyEffect(Player player) {
        // This method can be used for direct player effects if needed
    }

    // Method to check if the player is holding a scavenging-related item
    public boolean isHoldingScavengingItem(Player player) {
        ItemStack itemInHand = player.getInventory().getItemInMainHand();
        if (itemInHand != null && itemInHand.getType() != Material.AIR) {
            ItemMeta meta = itemInHand.getItemMeta();
            if (meta != null && meta.getPersistentDataContainer().has(scavengingItemKey, PersistentDataType.INTEGER)) {
                return true;
            }
        }
        return false;
    }

    // Method to tag an item as a scavenging item
    public void tagItemAsScavenging(ItemStack item) {
        if (item != null && item.getType() != Material.AIR) {
            ItemMeta meta = item.getItemMeta();
            if (meta != null) {
                meta.getPersistentDataContainer().set(scavengingItemKey, PersistentDataType.INTEGER, 1);
                item.setItemMeta(meta);  // Update the item meta after adding the tag
            }
        }
    }

    // Example of applying scavenging effect when the player is holding a scavenging item
    public void applyScavengingEffect(Player player) {
        if (isHoldingScavengingItem(player)) {
            // Apply scavenging bonuses if the player is holding a tagged scavenging item
            double luckBonus = getLevel() * 0.5; // Adjust based on skill level
            luckManager.increaseLuck(player, luckBonus);
        }
    }

    // Event handler for opening chests
    @EventHandler
    public void onChestOpen(InventoryOpenEvent event) {
        Inventory inventory = event.getInventory();
        Player player = (Player) event.getPlayer();

        // Check if the chest has a tier tag
        if (isTieredChest(inventory, commonChestKey)) {
            // Randomize loot for common chest tier (handled in another class)
            handleChestLoot(inventory, "common", player);
        } else if (isTieredChest(inventory, uncommonChestKey)) {
            // Randomize loot for uncommon chest tier
            handleChestLoot(inventory, "uncommon", player);
        } else if (isTieredChest(inventory, rareChestKey)) {
            // Randomize loot for rare chest tier
            handleChestLoot(inventory, "rare", player);
        } else if (isTieredChest(inventory, epicChestKey)) {
            // Randomize loot for epic chest tier
            handleChestLoot(inventory, "epic", player);
        } else if (isTieredChest(inventory, legendaryChestKey)) {
            // Randomize loot for legendary chest tier
            handleChestLoot(inventory, "legendary", player);
        }
    }

    // Helper method to check if the chest is of a specific tier
    private boolean isTieredChest(Inventory inventory, NamespacedKey tierKey) {
        ItemStack chestItem = inventory.getItem(0);  // Example: Check the first slot for a chest item
        if (chestItem != null && chestItem.hasItemMeta()) {
            ItemMeta meta = chestItem.getItemMeta();
            return meta.getPersistentDataContainer().has(tierKey, PersistentDataType.INTEGER);
        }
        return false;
    }

    // Placeholder method to handle chest loot generation (to be implemented in another class)
    private void handleChestLoot(Inventory inventory, String tier, Player player) {
        // Logic to randomize loot based on tier should be implemented in a different class
        // This method acts as a placeholder for calling that logic
    }
}
