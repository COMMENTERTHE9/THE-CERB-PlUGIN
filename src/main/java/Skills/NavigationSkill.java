package Skills;

import Manager.CraftingManager;
import cerberus.world.cerb.CustomPlayer;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.block.Biome;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class NavigationSkill extends UtilitySkill {
    private final CraftingManager craftingManager;
    private final Map<UUID, Long> lastXpGainTime = new ConcurrentHashMap<>();
    private final SkillManager skillManager;
    private static final float MAX_WALK_SPEED = 0.3f;
    /** Fixed UUID so there's only ever ONE speed modifier per player. */
    private static final UUID SPEED_MODIFIER_UUID = UUID.fromString("a7e3c2d1-5f00-4b8a-9c11-0a1b2c3d4e5f");

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
        double baseSpeedBonus = level * 0.01; // +1% speed per level
        double environmentSpeedBonus = calculateEnvironmentSpeedBonus(player);
        double totalSpeedBonus = baseSpeedBonus + environmentSpeedBonus;

        AttributeInstance movementSpeedAttribute = player.getAttribute(Attribute.MOVEMENT_SPEED);
        if (movementSpeedAttribute == null) return;

        // Remove the old modifier by fixed UUID (safe, no-op if absent)
        movementSpeedAttribute.getModifiers().stream()
                .filter(m -> m.getUniqueId().equals(SPEED_MODIFIER_UUID))
                .findFirst()
                .ifPresent(movementSpeedAttribute::removeModifier);

        // Calculate the new speed bonus
        double baseMovementSpeed = movementSpeedAttribute.getBaseValue(); // ~0.1
        double bonusAmount = baseMovementSpeed * totalSpeedBonus;

        // Enforce the maximum speed limit
        double maxBonus = baseMovementSpeed * (MAX_WALK_SPEED / 0.2f) - baseMovementSpeed;
        bonusAmount = Math.min(bonusAmount, maxBonus);

        if (bonusAmount <= 0) return; // no bonus to apply

        // Apply single modifier with fixed UUID — can never stack
        AttributeModifier speedModifier = new AttributeModifier(
                SPEED_MODIFIER_UUID,
                "NavigationSkillSpeedBonus",
                bonusAmount,
                AttributeModifier.Operation.ADD_NUMBER
        );
        movementSpeedAttribute.addModifier(speedModifier);
    }

    // Calculate environmental speed bonus based on selected vanilla Minecraft environments
    private double calculateEnvironmentSpeedBonus(Player player) {
        double environmentBonus = 0.0;
        Biome biome = player.getLocation().getBlock().getBiome();

        // Biome is now a registry type (not an enum) — use identity comparison with static fields
        // Cold biomes (slow movement)
        if (biome == Biome.SNOWY_TAIGA || biome == Biome.SNOWY_PLAINS
                || biome == Biome.ICE_SPIKES || biome == Biome.FROZEN_PEAKS
                || biome == Biome.SNOWY_SLOPES) {
            environmentBonus -= 0.02;
        }
        // Desert and warm biomes (faster movement)
        else if (biome == Biome.DESERT || biome == Biome.SAVANNA
                || biome == Biome.BADLANDS || biome == Biome.ERODED_BADLANDS) {
            environmentBonus += 0.02;
        }
        // Swamp and mangrove (slower movement)
        else if (biome == Biome.SWAMP || biome == Biome.MANGROVE_SWAMP) {
            environmentBonus -= 0.01;
        }
        // Forests and plains (neutral movement)
        else if (biome == Biome.FOREST || biome == Biome.PLAINS
                || biome == Biome.MEADOW || biome == Biome.CHERRY_GROVE) {
            environmentBonus += 0.01;
        }
        // Ocean and rivers (slow swimming)
        else if (biome == Biome.OCEAN || biome == Biome.RIVER
                || biome == Biome.FROZEN_RIVER || biome == Biome.WARM_OCEAN
                || biome == Biome.COLD_OCEAN) {
            if (player.isInWater()) {
                environmentBonus -= 0.03;
            }
        }
        // Nether (unique environments)
        else if (biome == Biome.NETHER_WASTES || biome == Biome.CRIMSON_FOREST
                || biome == Biome.WARPED_FOREST || biome == Biome.BASALT_DELTAS
                || biome == Biome.SOUL_SAND_VALLEY) {
            environmentBonus -= 0.05;
        }
        // The End
        else if (biome == Biome.THE_END || biome == Biome.END_HIGHLANDS
                || biome == Biome.END_MIDLANDS) {
            environmentBonus += 0.02;
        }
        // Mountainous regions (slow movement)
        else if (biome == Biome.WINDSWEPT_HILLS || biome == Biome.JAGGED_PEAKS
                || biome == Biome.STONY_PEAKS) {
            environmentBonus -= 0.02;
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
