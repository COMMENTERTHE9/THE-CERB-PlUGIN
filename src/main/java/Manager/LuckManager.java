package Manager;

import Skills.SkillManager;
import cerberus.world.cerb.CustomPlayer;
import Manager.PlayerVirtualHealthManager;
import Manager.PlayerDefenseManager;
import Manager.PlayerStrengthManager;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

public class LuckManager {
    private final SkillManager skillManager;
    private final Random random;
    private final Plugin plugin; // Plugin instance for managing metadata
    private final Map<UUID, Double> temporaryLuckMap; // Tracks temporary luck points per player
    private final PlayerVirtualHealthManager virtualHealthManager;
    private final PlayerDefenseManager defenseManager;
    private final PlayerStrengthManager strengthManager;
    private final Map<UUID, Double> playerLuckMap; // Keeps track of player's permanent luck points

    public LuckManager(SkillManager skillManager, Plugin plugin, PlayerVirtualHealthManager virtualHealthManager,
                       PlayerDefenseManager defenseManager, PlayerStrengthManager strengthManager) {
        this.skillManager = skillManager;
        this.random = new Random();
        this.plugin = plugin;
        this.temporaryLuckMap = new HashMap<>();
        this.virtualHealthManager = virtualHealthManager;
        this.defenseManager = defenseManager;
        this.strengthManager = strengthManager;
        this.playerLuckMap = new HashMap<>();  // Initialize the map for permanent luck tracking
    }

    /**
     * Adds a permanent luck bonus to the player.
     *
     * @param player The player to increase luck for.
     * @param luckBonus The amount of luck to increase.
     */
    public void increaseLuck(Player player, double luckBonus) {
        UUID playerUUID = player.getUniqueId();
        playerLuckMap.put(playerUUID, getLuck(player) + luckBonus);  // Adds permanent luck bonus
    }

    /**
     * Retrieves the current permanent luck points of a player.
     *
     * @param player The player whose luck is being queried.
     * @return The player's permanent luck points.
     */
    public double getLuck(Player player) {
        return playerLuckMap.getOrDefault(player.getUniqueId(), 0.0);  // Default to 0 if no luck is tracked
    }

    /**
     * Calculates the player's luck based on their relevant skills and other factors.
     * Includes both skill-based and temporary luck points.
     *
     * @param customPlayer The player whose luck is being calculated.
     * @return A luck factor, where 1.0 represents normal luck, >1.0 represents good luck, and <1.0 represents bad luck.
     */
    public double calculateLuckFactor(CustomPlayer customPlayer) {
        if (customPlayer == null) return 1.0;

        Player player = customPlayer.getPlayer();
        double luckFactor = 1.0;

        // Incorporate skill-based luck
        luckFactor *= calculateSkillBasedLuck(customPlayer);

        // Add temporary and permanent luck points if any
        luckFactor *= (1.0 + getTemporaryLuckPoints(player) / 100);
        luckFactor *= (1.0 + getLuck(player) / 100);  // Include permanent luck points in the calculation

        // Apply any additional bonuses from items, status effects, or other sources
        luckFactor *= applyItemBonuses(player);
        luckFactor *= applyEnvironmentalFactors(player);

        return luckFactor;
    }

    /**
     * Applies skill-based luck calculations, considering various skills that affect luck.
     *
     * @param customPlayer The player whose luck is being calculated.
     * @return A multiplier based on the player's skill levels.
     */
    private double calculateSkillBasedLuck(CustomPlayer customPlayer) {
        double luckMultiplier = 1.0;
        int magicFindLevel = skillManager.getSkillLevel("Magic Find");
        int scavengingLevel = skillManager.getSkillLevel("Scavenging");
        int explorationLevel = skillManager.getSkillLevel("Exploration");
        int lockpickingLevel = skillManager.getSkillLevel("Lockpicking");

        luckMultiplier *= (1.0 + (magicFindLevel * 0.05)); // 5% luck increase per Magic Find level
        luckMultiplier *= (1.0 + (scavengingLevel * 0.03)); // 3% luck increase per Scavenging level
        luckMultiplier *= (1.0 + (explorationLevel * 0.04)); // 4% luck increase per Exploration level
        luckMultiplier *= (1.0 + (lockpickingLevel * 0.02)); // 2% luck increase per Lockpicking level

        return luckMultiplier;
    }

    /**
     * Adds temporary luck points to a player for a specific duration.
     *
     * @param player      The player to add luck points to.
     * @param luckPoints  The amount of luck points to add.
     * @param durationSec Duration in seconds for the temporary luck points.
     */
    public void addTemporaryLuckPoints(Player player, double luckPoints, int durationSec) {
        UUID playerUUID = player.getUniqueId();
        temporaryLuckMap.put(playerUUID, getTemporaryLuckPoints(player) + luckPoints);

        // Set a delayed task to remove the luck points after the duration
        plugin.getServer().getScheduler().scheduleSyncDelayedTask(plugin, () -> {
            removeTemporaryLuckPoints(player, luckPoints);
        }, durationSec * 20L); // Convert seconds to ticks
    }

    /**
     * Removes temporary luck points from a player.
     *
     * @param player     The player whose luck points will be removed.
     * @param luckPoints The amount of luck points to remove.
     */
    private void removeTemporaryLuckPoints(Player player, double luckPoints) {
        UUID playerUUID = player.getUniqueId();
        double currentLuck = getTemporaryLuckPoints(player);
        double newLuck = Math.max(0, currentLuck - luckPoints); // Ensure it doesn't go below zero
        temporaryLuckMap.put(playerUUID, newLuck);
    }

    /**
     * Retrieves the current temporary luck points of a player.
     *
     * @param player The player whose luck points are being queried.
     * @return The amount of temporary luck points the player has.
     */
    public double getTemporaryLuckPoints(Player player) {
        return temporaryLuckMap.getOrDefault(player.getUniqueId(), 0.0);
    }

    /**
     * Applies bonuses to luck based on items or equipment the player is wearing.
     */
    private double applyItemBonuses(Player player) {
        double itemBonus = 1.0;
        // Add item-based luck bonuses here
        return itemBonus;
    }

    /**
     * Applies environmental factors to luck, such as time of day, weather, or location.
     */
    private double applyEnvironmentalFactors(Player player) {
        double envFactor = 1.0;
        // Add environmental luck modifiers here
        return envFactor;
    }

    /**
     * Integrates luck into finding rare loot, chest generation, fishing results, etc.
     */
    public boolean isRareLootObtained(Player player, double baseChance) {
        // Get customId from skillManager - this is the safer approach
        String customId = skillManager.getCustomId(player);
        CustomPlayer customPlayer = new CustomPlayer(
                player,
                skillManager,
                virtualHealthManager,
                defenseManager,
                strengthManager,
                customId  // Add the required customId parameter
        );
        double luckFactor = calculateLuckFactor(customPlayer);
        double adjustedChance = baseChance * luckFactor;
        return random.nextDouble() <= adjustedChance;
    }

    /**
     * Determines the success of an action (like lockpicking) based on luck.
     *
     * @param customPlayer The player attempting the action.
     * @param successChance The base success chance.
     * @return True if the action was successful, false otherwise.
     */
    public boolean determineSuccess(CustomPlayer customPlayer, double successChance) {
        double luckFactor = calculateLuckFactor(customPlayer);
        double adjustedChance = successChance * luckFactor;
        return random.nextDouble() <= adjustedChance;
    }
}
