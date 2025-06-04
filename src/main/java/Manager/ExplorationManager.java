package Manager;

import Skills.SkillManager;
import cerberus.world.cerb.CustomPlayer;
import org.bukkit.entity.Player;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.Random;

public class ExplorationManager {
    private final SkillManager skillManager;
    private final Random random;

    public ExplorationManager(SkillManager skillManager) {
        this.skillManager = skillManager;
        this.random = new Random();
    }

    // Enhance treasure discovery rates and the rewards from exploration.
    public void enhanceTreasureDiscovery(Player player, Location location) {
        int explorationLevel = skillManager.getSkillLevel("Exploration");

        // Example: Increase the chance of finding treasure based on exploration level
        double treasureChance = 0.1 + (explorationLevel * 0.05); // 10% base chance plus skill-based increase

        if (random.nextDouble() <= treasureChance) {
            // Logic to give the player treasure
            giveTreasure(player, explorationLevel);
            player.sendMessage("You have discovered a treasure!");
        }
    }

    private void giveTreasure(Player player, int explorationLevel) {
        // Example treasure based on exploration level
        ItemStack treasure = new ItemStack(Material.DIAMOND, 1 + (explorationLevel / 10)); // More diamonds with higher levels
        ItemMeta meta = treasure.getItemMeta();
        meta.setDisplayName("Treasure from Exploration");
        treasure.setItemMeta(meta);

        player.getInventory().addItem(treasure);
    }

    // Implement bonuses for discovering new biomes, structures, or dungeons.
    public void applyDiscoveryBonuses(Player player, Location location) {
        int explorationLevel = skillManager.getSkillLevel("Exploration");

        // Example: Increase XP or rewards for discovering new areas based on skill level
        int xpReward = 100 + (explorationLevel * 10); // Base XP plus additional based on level
        player.giveExp(xpReward);

        player.sendMessage("You gained " + xpReward + " XP for discovering a new area!");

        // Additional logic for giving rare items or bonus rewards could be added here
        if (random.nextDouble() < (0.01 * explorationLevel)) { // 1% chance per level for rare item
            giveRareDiscoveryReward(player);
        }
    }

    private void giveRareDiscoveryReward(Player player) {
        // Example of a rare reward
        ItemStack rareItem = new ItemStack(Material.EMERALD, 1);
        ItemMeta meta = rareItem.getItemMeta();
        meta.setDisplayName("Rare Discovery Emerald");
        rareItem.setItemMeta(meta);

        player.getInventory().addItem(rareItem);
        player.sendMessage("You found a rare emerald during your exploration!");
    }

    // Manage exploration-based achievements or rewards.
    public void manageExplorationAchievements(Player player, Location location) {
        int explorationLevel = skillManager.getSkillLevel("Exploration");

        // Example: Unlock achievements or titles based on exploration milestones
        if (explorationLevel >= 100) {
            unlockExplorationAchievement(player, "Master Explorer");
        }

        if (explorationLevel >= 200) {
            unlockExplorationAchievement(player, "Legendary Explorer");
        }
    }

    private void unlockExplorationAchievement(Player player, String achievementName) {
        // Logic to unlock achievement
        player.sendMessage("Congratulations! You have unlocked the achievement: " + achievementName);
        // Additional logic to store the achievement in a database or player profile could be added here
    }
}
