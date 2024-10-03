package Skills;

import Manager.CraftingManager;
import cerberus.world.cerb.CustomPlayer;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.block.Biome;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class NavigationSkill extends UtilitySkill {
    private final CraftingManager craftingManager;
    private final Map<UUID, Long> lastXpGainTime = new HashMap<>();
    private final SkillManager skillManager;  // Assuming you have access to SkillManager
    private static final float MAX_WALK_SPEED = 0.3f; // Maximum allowed walk speed

    public NavigationSkill(String name, CraftingManager craftingManager, SkillManager skillManager) {
        super(name);
        this.craftingManager = craftingManager;
        this.skillManager = skillManager;
    }

    @Override
    public void applyEffect(CustomPlayer customPlayer) {
        if (customPlayer == null) return;

        Player player = customPlayer.getPlayer();
        UUID playerId = player.getUniqueId();
        int level = this.getLevel();

        // Apply speed bonuses to the player based on skill level and environment
        applySpeedBonus(player, level);

        // Apply navigation tool enhancements
        applyNavigationToolEnhancements(player, level);

        // Implement cooldown for XP gain
        long currentTime = System.currentTimeMillis();
        long lastGainTime = lastXpGainTime.getOrDefault(playerId, 0L);
        long cooldown = 10000; // 10 seconds in milliseconds

        if (currentTime - lastGainTime >= cooldown) {
            // Award XP
            int xpAmount = 10; // Adjust as needed
            skillManager.addXpAndCheckLevelUp("Navigation", xpAmount, player);

            // Update last gain time
            lastXpGainTime.put(playerId, currentTime);

        }
    }

    private void applySpeedBonus(Player player, int level) {
        double baseSpeedBonus = level * 0.01; // Example: +1% speed per level
        double environmentSpeedBonus = calculateEnvironmentSpeedBonus(player); // Custom environmental modifiers

        // Total speed bonus with environmental factors
        double totalSpeedBonus = baseSpeedBonus + environmentSpeedBonus;

        AttributeInstance movementSpeedAttribute = player.getAttribute(Attribute.GENERIC_MOVEMENT_SPEED);
        if (movementSpeedAttribute == null) return; // Safety check

        // Remove existing modifiers specific to NavigationSkill
        movementSpeedAttribute.getModifiers().stream()
                .filter(modifier -> modifier.getName().equals("NavigationSkillSpeedBonus"))
                .forEach(movementSpeedAttribute::removeModifier);

        // Calculate the new movement speed with bonuses
        double baseMovementSpeed = movementSpeedAttribute.getBaseValue(); // Should be around 0.1
        double newMovementSpeed = baseMovementSpeed * (1 + totalSpeedBonus);

        // Enforce the maximum speed limit
        double maxMovementSpeed = baseMovementSpeed * (MAX_WALK_SPEED / 0.2f);
        if (newMovementSpeed > maxMovementSpeed) {
            newMovementSpeed = maxMovementSpeed;
        }

        // Apply the new movement speed modifier
        AttributeModifier speedModifier = new AttributeModifier(
                UUID.randomUUID(),
                "NavigationSkillSpeedBonus",
                newMovementSpeed - baseMovementSpeed,
                AttributeModifier.Operation.ADD_NUMBER
        );
        movementSpeedAttribute.addModifier(speedModifier);

    }

    // Calculate environmental speed bonus based on selected vanilla Minecraft environments
    private double calculateEnvironmentSpeedBonus(Player player) {
        double environmentBonus = 0.0;
        Biome biome = player.getLocation().getBlock().getBiome();

        // Grouped and selected relevant environments
        switch (biome) {
            // Cold biomes (slow movement)
            case SNOWY_TAIGA:
            case SNOWY_PLAINS:
            case ICE_SPIKES:
            case FROZEN_PEAKS:
            case SNOWY_SLOPES:
                environmentBonus -= 0.02; // Slower movement in cold, snowy biomes
                break;

            // Desert and warm biomes (faster movement)
            case DESERT:
            case SAVANNA:
            case BADLANDS:
            case ERODED_BADLANDS:
                environmentBonus += 0.02; // Faster movement in hot, dry biomes
                break;

            // Swamp and mangrove (slower movement)
            case SWAMP:
            case MANGROVE_SWAMP:
                environmentBonus -= 0.01; // Slower movement in swampy areas
                break;

            // Forests and plains (neutral movement)
            case FOREST:
            case PLAINS:
            case MEADOW:
            case CHERRY_GROVE:
                environmentBonus += 0.01; // Slight speed boost in neutral biomes
                break;

            // Ocean and rivers (slow swimming)
            case OCEAN:
            case RIVER:
            case FROZEN_RIVER:
            case WARM_OCEAN:
            case COLD_OCEAN:
                if (player.isInWater()) {
                    environmentBonus -= 0.03; // Slower movement while swimming
                }
                break;

            // Nether and the End (unique environments)
            case NETHER_WASTES:
            case CRIMSON_FOREST:
            case WARPED_FOREST:
            case BASALT_DELTAS:
            case SOUL_SAND_VALLEY:
                environmentBonus -= 0.05; // Slower movement in nether environments
                break;
            case THE_END:
            case END_HIGHLANDS:
            case END_MIDLANDS:
                environmentBonus += 0.02; // Slightly faster movement in The End
                break;

            // Mountainous regions (slow movement)
            case WINDSWEPT_HILLS:
            case JAGGED_PEAKS:
            case STONY_PEAKS:
                environmentBonus -= 0.02; // Slower movement in mountainous terrain
                break;

            default:
                break;
        }

        return environmentBonus;
    }

    // Placeholder method to enhance navigation tools
    private void applyNavigationToolEnhancements(Player player, int level) {
        ItemStack navigationTool = getNavigationToolFromPlayer(player);

        if (navigationTool != null && isNavigationTool(navigationTool)) {
            // Example: Enhance map accuracy or compass effectiveness based on skill level
            enhanceMapAccuracy(navigationTool, level);
            enhanceCompassEffectiveness(navigationTool, level);
        }
    }

    // Helper method to check if the item is a navigation tool
    private boolean isNavigationTool(ItemStack item) {
        // Placeholder logic for identifying navigation tools like maps or compasses
        return item.getType().toString().contains("MAP") || item.getType().toString().contains("COMPASS");
    }

    // Placeholder logic for enhancing map accuracy
    private void enhanceMapAccuracy(ItemStack map, int level) {
        // Example: Increase map detail or coverage radius based on skill level
        System.out.println("Map accuracy enhanced by " + level * 5 + "%.");
    }

    // Placeholder logic for enhancing compass effectiveness
    private void enhanceCompassEffectiveness(ItemStack compass, int level) {
        // Example: Improve compass precision or add custom tracking features based on skill level
        System.out.println("Compass effectiveness enhanced by " + level * 5 + "%.");
    }

    // Retrieve the navigation tool the player is using
    private ItemStack getNavigationToolFromPlayer(Player player) {
        // For now, we get the item in the player's main hand
        return player.getInventory().getItemInMainHand();
    }
}
