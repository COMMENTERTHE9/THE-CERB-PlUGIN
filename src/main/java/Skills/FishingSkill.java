package Skills;

import CustomEntities.SeaMonsterManager;
import Manager.ResourceYieldManager;
import cerberus.world.cerb.CustomPlayer;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.plugin.Plugin;

import java.util.HashMap;
import java.util.Map;

public class FishingSkill extends UtilitySkill {

    private final ResourceYieldManager yieldManager;
    private final SeaMonsterManager seaMonsterManager;
    private final Plugin plugin; // Reference to the main plugin instance

    // Map to store sea monster tags with associated conditions
    private final Map<String, SeaMonsterTag> seaMonsterTags = new HashMap<>();

    public FishingSkill(String name, ResourceYieldManager yieldManager, SeaMonsterManager seaMonsterManager, Plugin plugin) {
        super(name);
        this.yieldManager = yieldManager;
        this.seaMonsterManager = seaMonsterManager;
        this.plugin = plugin;
        initializeSeaMonsterTags();
    }

    @Override
    public void applyEffect(CustomPlayer customPlayer) {
        if (customPlayer == null) return;
        Player player = customPlayer.getPlayer();

        int level = this.getLevel();

        // Enhance resource yield from fishing based on skill level
        yieldManager.applyYieldBonus(player, "Fishing");

        // Apply bonuses to player (health, defense, strength, etc.)
        double healthBonus = level * 1.0;
        double defenseBonus = level * 0.5;
        double strengthBonus = level * 0.3;

        customPlayer.getHealthManager().increaseMaxHealth(player, healthBonus);
        customPlayer.getDefenseManager().increaseDefense(player, defenseBonus);
        customPlayer.getStrengthManager().increaseStrength(player, strengthBonus);

        // Custom Enhancements including Sea Monsters
        applyFishingEnhancements(player, level);
    }

    private void initializeSeaMonsterTags() {
        // Example: Initialize sea monster tags with their unique conditions
        seaMonsterTags.put("Kraken", new SeaMonsterTag("Kraken", 0.01, 50));
        seaMonsterTags.put("Leviathan", new SeaMonsterTag("Leviathan", 0.005, 100));
        // Add more sea monsters with their tags and conditions as needed
    }

    private void applyFishingEnhancements(Player player, int level) {
        double rareCatchChance = 0.05 + (level * 0.01);
        if (Math.random() <= rareCatchChance) {
            player.sendMessage("You feel lucky! Increased chance for a rare catch!");
        }

        double fishingSpeedMultiplier = 1.0 - (level * 0.01);
        player.sendMessage("Your fishing skill allows you to fish faster!");

        if (level >= 50) {
            player.sendMessage("Your expert fishing skill grants you a temporary boost!");
        }

        // Apply advanced sea monster spawning logic
        for (SeaMonsterTag tag : seaMonsterTags.values()) {
            if (Math.random() <= tag.getSpawnChance() + (level * 0.001)) {
                player.sendMessage("A " + tag.getName() + " has appeared!");
                spawnSeaMonster(player.getLocation(), player, tag);
                break; // Only spawn one monster at a time
            }
        }
    }

    private void spawnSeaMonster(Location location, Player player, SeaMonsterTag tag) {
        // Spawning sea monster using the SeaMonsterManager
        seaMonsterManager.spawnSeaMonster(location);

        // Here, you would add metadata or any specific logic you need for the SeaMonster
        player.sendMessage("You are being attacked by a " + tag.getName() + "!");
    }

    // Class to define sea monster tags with conditions
    private static class SeaMonsterTag {
        private final String name;
        private final double spawnChance;
        private final double health;

        public SeaMonsterTag(String name, double spawnChance, double health) {
            this.name = name;
            this.spawnChance = spawnChance;
            this.health = health;
        }

        public String getName() {
            return name;
        }

        public double getSpawnChance() {
            return spawnChance;
        }

        public double getHealth() {
            return health;
        }
    }
}
