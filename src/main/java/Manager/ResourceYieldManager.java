package Manager;

import Skills.SkillManager;
import cerberus.world.cerb.CustomPlayer;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;

public class ResourceYieldManager {
    private final SkillManager skillManager;
    private final Map<Material, Double> rareDropChances;
    private final Map<Material, String> customMaterialSkillMap;
    private final Random random;
    private final CustomTagManager customTagManager;
    private final AdvancedDropManager advancedDropManager;

    public ResourceYieldManager(SkillManager skillManager, Plugin plugin) {
        this.skillManager = skillManager;
        this.rareDropChances = new HashMap<>();
        this.customMaterialSkillMap = new HashMap<>();
        this.random = new Random();
        this.customTagManager = new CustomTagManager(plugin);
        this.advancedDropManager = new AdvancedDropManager();
        initializeRareDropChances();
        initializeCustomMaterialSkillMap();
    }

    private void initializeRareDropChances() {
        // Rare drop chances for specific materials, e.g., diamonds from stone
        rareDropChances.put(Material.STONE, 0.01); // 1% chance to drop a diamond from stone
        rareDropChances.put(Material.OAK_LOG, 0.05); // 5% chance to drop an apple from oak logs
        // Add more rare drop chances as needed
    }

    private void initializeCustomMaterialSkillMap() {
        // Assign custom materials to specific skills here
        customMaterialSkillMap.put(Material.IRON_BLOCK, "Mining");
        customMaterialSkillMap.put(Material.GOLD_BLOCK, "Mining");
        customMaterialSkillMap.put(Material.DIAMOND_BLOCK, "Mining");
        customMaterialSkillMap.put(Material.EMERALD_BLOCK, "Mining");

        // Concrete and Terracotta blocks added to the Mining skill
        customMaterialSkillMap.put(Material.WHITE_CONCRETE, "Mining");
        customMaterialSkillMap.put(Material.BLACK_CONCRETE, "Mining");
        customMaterialSkillMap.put(Material.RED_CONCRETE, "Mining");
        customMaterialSkillMap.put(Material.TERRACOTTA, "Mining");
        customMaterialSkillMap.put(Material.BLACK_TERRACOTTA, "Mining");
        customMaterialSkillMap.put(Material.RED_TERRACOTTA, "Mining");

        // Add more concrete and terracotta colors as needed
    }

    public void handleBlockBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        Block block = event.getBlock();
        Material material = block.getType();

        // Determine the skill associated with the material
        String skillName = getSkillForMaterial(material);
        if (skillName == null || skillName.isEmpty()) return;

        // Calculate the yield multiplier based on the player's skill level
        double yieldMultiplier = calculateYieldMultiplier((CustomPlayer) player, skillName);

        // Handle advanced drops
        advancedDropManager.handleDrops(player, block, skillManager.getSkillLevel(skillName));

        // Adjust drops based on yield multiplier
        block.getDrops().forEach(drop -> {
            drop.setAmount((int) (drop.getAmount() * yieldMultiplier));
            block.getWorld().dropItemNaturally(block.getLocation(), drop);
        });
    }

    private String getSkillForMaterial(Material material) {
        switch (material) {
            case IRON_ORE:
            case GOLD_ORE:
            case DIAMOND_ORE:
            case EMERALD_ORE:
            case STONE:
            case COBBLESTONE:
            case COAL_ORE:
            case NETHER_QUARTZ_ORE:
            case WHITE_CONCRETE:
            case BLACK_CONCRETE:
            case RED_CONCRETE:
            case TERRACOTTA:
            case BLACK_TERRACOTTA:
            case RED_TERRACOTTA:
                return "Mining";
            case WHEAT:
            case CARROTS:
            case POTATOES:
            case BEETROOTS:
            case NETHER_WART:
            case PUMPKIN:
            case SUGAR_CANE:
                return "Farming";
            case OAK_LOG:
            case SPRUCE_LOG:
            case BIRCH_LOG:
            case JUNGLE_LOG:
            case ACACIA_LOG:
            case DARK_OAK_LOG:
                return "Woodcutting";
            case FISHING_ROD:
                return "Fishing";
            default:
                return customMaterialSkillMap.get(material);
        }
    }

    private double calculateYieldMultiplier(CustomPlayer player, String skillName) {
        if (skillName == null || skillName.isEmpty()) return 1.0;

        int skillLevel = skillManager.getSkillLevel(skillName);
        return 1.0 + (skillLevel * 0.1); // 10% yield increase per skill level
    }

    private ItemStack getRareDropForMaterial(Material material) {
        // Define rare drops for specific materials
        switch (material) {
            case STONE:
                return new ItemStack(Material.DIAMOND); // Example rare drop
            case OAK_LOG:
                return new ItemStack(Material.APPLE); // Example rare drop
            default:
                return null;
        }
    }

    // New method to apply yield bonus based on the player's skill level
    public void applyYieldBonus(Player player, String skillName) {
        // Get the block the player is looking at or interacting with
        Block block = player.getTargetBlockExact(5); // Assuming a range of 5 blocks for interaction

        // Check if the block is valid and can be interacted with
        if (block == null || block.getType() == Material.AIR) {
            return; // If no valid block is found, exit the method
        }

        // Calculate the yield multiplier based on the player's skill level
        double yieldMultiplier = calculateYieldMultiplier((CustomPlayer) player, skillName);

        // Adjust drops based on yield multiplier
        block.getDrops().forEach(drop -> {
            drop.setAmount((int) (drop.getAmount() * yieldMultiplier));
            block.getWorld().dropItemNaturally(block.getLocation(), drop);
        });
    }

    // Add any additional methods for ResourceYieldManager as needed
}
