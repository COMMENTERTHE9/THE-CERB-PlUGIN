package Skills;

import Manager.LuckManager;
import cerberus.world.cerb.CustomPlayer;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Map;

public class LockpickingSkill extends UtilitySkill {

    private final LuckManager luckManager;
    private final Map<String, Double> lockComplexityMap = new HashMap<>(); // Custom lock complexity system

    public LockpickingSkill(String name, LuckManager luckManager) {
        super(name);
        this.luckManager = luckManager;

        // Initialize some example lock complexities (you can expand this)
        lockComplexityMap.put("basic_lock", 0.1);   // Basic locks have 10% complexity
        lockComplexityMap.put("reinforced_lock", 0.3); // Reinforced locks have 30% complexity
        lockComplexityMap.put("magical_lock", 0.5);   // Magical locks have 50% complexity
    }

    @Override
    public void applyEffect(CustomPlayer customPlayer) {
        if (customPlayer == null) return;
        Player player = customPlayer.getPlayer();
        int level = this.getLevel();

        // Calculate base success chance and luck factor
        double baseSuccessChance = 0.1 + (level * 0.01); // Base: 10% + 1% per level
        double luckFactor = luckManager.calculateLuckFactor(customPlayer);

        // Get the complexity of the lock being picked (example: "reinforced_lock")
        double lockComplexity = getLockComplexity("reinforced_lock"); // You can replace this with the actual lock being picked

        // Final success chance is influenced by luck and lock complexity
        double finalSuccessChance = (baseSuccessChance - lockComplexity) * luckFactor;

        // Check for success or failure
        if (luckManager.determineSuccess(customPlayer, finalSuccessChance)) {
            // Successful lockpicking
            handleSuccessfulLockpick(player);
        } else {
            // Failed lockpicking attempt, could trigger traps or penalties
            handleFailedLockpick(player);
        }
    }

    @Override
    public void applyEffect(Player player) {
        // This method can be used for direct player effects if needed
    }

    // Custom method to calculate lock complexity based on the lock type
    private double getLockComplexity(String lockType) {
        // Use the lockComplexityMap to retrieve the complexity of the lock
        return lockComplexityMap.getOrDefault(lockType, 0.0); // Default complexity is 0 if lock type is not found
    }

    // Handle a successful lockpick
    private void handleSuccessfulLockpick(Player player) {
        player.sendMessage("You successfully picked the lock!");
        // Additional logic to open the chest, door, etc.
    }

    // Handle a failed lockpick
    private void handleFailedLockpick(Player player) {
        player.sendMessage("Lockpicking failed! You may have triggered a trap.");
        // Additional logic to penalize or trigger events like traps
    }

    // Calculate additional bonus from lockpicking tools (e.g., enchanted lockpicks)
    private double calculateToolBonus(Player player) {
        // Placeholder logic to check for special tools in the player's hand
        // You can expand this by checking for specific item names or metadata
        return 0.05; // Example: 5% tool bonus for special lockpicking tools
    }

    // Handle the durability of the lockpicking tool based on success or failure
    private void applyToolDurabilityEffect(Player player, boolean success) {
        // Placeholder logic for reducing tool durability after lockpicking
        // You can implement a durability system here if needed
        System.out.println(success ? "Tool durability reduced slightly." : "Tool durability reduced significantly due to failure.");
    }
}
