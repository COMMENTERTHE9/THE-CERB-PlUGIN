package Manager;

import Skills.SkillManager;
import org.bukkit.enchantments.Enchantment; // Properly importing Enchantment class
import cerberus.world.cerb.CustomPlayer;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

public class CraftingManager {
    private final SkillManager skillManager;
    private final JavaPlugin plugin; // For plugin-related operations like NamespacedKeys
    private final MaterialCostManager materialCostManager; // Manager for modifying material costs
    private final BrokenItemManager brokenItemManager; // Manager for handling broken items
    private final Random random = new Random();  // Random instance for generating random values

    public CraftingManager(SkillManager skillManager, JavaPlugin plugin) {
        this.skillManager = skillManager;
        this.plugin = plugin;
        this.materialCostManager = new MaterialCostManager(plugin);
        this.brokenItemManager = new BrokenItemManager(plugin);
    }

    // New getPlugin method
    public JavaPlugin getPlugin() {
        return this.plugin;
    }

    // Quality levels using hex codes
    private enum Quality {
        WEAK("#7f7f7f", "Weak"),          // Gray
        COMMON("#ffffff", "Common"),      // White
        UNCOMMON("#00ff00", "Uncommon"),  // Bright Green
        RARE("#0000ff", "Rare"),          // Blue
        EPIC("#9400d3", "Epic"),          // Dark Purple
        PERFECT("#ffd700", "Perfect"),    // Gold
        GODLY("#ff4500", "Godly"),        // Orange-Red
        PRIMORDIAL("#8a2be2", "Primordial"); // Blue Violet

        private final String hexColor;
        private final String name;

        Quality(String hexColor, String name) {
            this.hexColor = hexColor;
            this.name = name;
        }

        public String getHexColor() {
            return hexColor;
        }

        public String getName() {
            return name;
        }
    }

    // Apply crafting bonuses including item quality
    public void applyCraftingBonuses(Player player, ItemStack craftedItem, String skillName) {
        int skillLevel = skillManager.getSkillLevel(skillName);

        // Determine item quality based on skill level
        Quality quality = determineItemQuality(skillLevel);

        // Apply the quality to the item using a custom tag
        applyItemQuality(craftedItem, quality);

        // Adjust item attributes based on the determined quality
        adjustItemAttributes(craftedItem, skillLevel);

        // Apply custom logic for specific skills
        switch (skillName) {
            case "Smithing":
                applySmithingBonuses(player, craftedItem, skillLevel);
                break;
            case "Enchanting":
                increaseEnchantmentEffectiveness(player, craftedItem, skillLevel);
                break;
            case "Alchemy":
            case "Brewing":
                increasePotionEffectiveness(player, craftedItem, skillLevel);
                break;
            case "CraftingBlock":  // New case for crafting blocks and machines
                applyMachineCraftingBonuses(player, craftedItem, skillLevel);
                break;
            // Add more custom logic cases as needed for other skills
        }
    }

    // Determine the item quality based on the skill level
    private Quality determineItemQuality(int skillLevel) {
        double chance = random.nextDouble();  // Generates a value between 0.0 and 1.0

        if (skillLevel < 10) {
            return Quality.WEAK;
        } else if (skillLevel < 30) {
            return chance < 0.8 ? Quality.COMMON : Quality.WEAK;  // 80% Common, 20% Weak
        } else if (skillLevel < 50) {
            return chance < 0.7 ? Quality.UNCOMMON : Quality.COMMON;  // 70% Uncommon, 30% Common
        } else if (skillLevel < 80) {
            if (chance < 0.5) return Quality.RARE;  // 50% Rare
            else if (chance < 0.85) return Quality.UNCOMMON;  // 35% Uncommon
            else return Quality.COMMON;  // 15% Common
        } else if (skillLevel < 110) {
            if (chance < 0.4) return Quality.EPIC;  // 40% Epic
            else if (chance < 0.8) return Quality.RARE;  // 40% Rare
            else return Quality.UNCOMMON;  // 20% Uncommon, no Common or Weak items at this level
        } else if (skillLevel < 140) {
            if (chance < 0.3) return Quality.PERFECT;  // 30% Perfect
            else if (chance < 0.75) return Quality.EPIC;  // 45% Epic
            else return Quality.RARE;  // 25% Rare, no Uncommon or lower items
        } else if (skillLevel < 160) {
            if (chance < 0.2) return Quality.GODLY;  // 20% Godly
            else if (chance < 0.7) return Quality.PERFECT;  // 50% Perfect
            else return Quality.EPIC;  // 30% Epic, no Rare or lower items
        } else {
            if (chance < 0.1) return Quality.PRIMORDIAL;  // 10% Primordial
            else if (chance < 0.6) return Quality.GODLY;  // 50% Godly
            else return Quality.PERFECT;  // 40% Perfect, no Epic or lower items
        }
    }

    // Apply the determined quality to the item using a custom tag
    private void applyItemQuality(ItemStack item, Quality quality) {
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            String coloredName = applyHexColor(quality.getHexColor()) + quality.getName();
            meta.setDisplayName(meta.getDisplayName() + " (" + coloredName + ")");
            // Add a custom tag to track the item's quality
            meta.getPersistentDataContainer().set(new NamespacedKey(plugin, "item_quality"), PersistentDataType.STRING, quality.getName());
            applyQualityBonuses(item, quality);
            item.setItemMeta(meta);
        }
    }

    // Helper method to convert hex to Minecraft format (e.g., #RRGGBB -> §x§R§R§G§G§B§B)
    private String applyHexColor(String hexColor) {
        StringBuilder hexFormat = new StringBuilder("§x");
        for (char c : hexColor.substring(1).toCharArray()) {
            hexFormat.append("§").append(c);
        }
        return hexFormat.toString();
    }

    // Adjust item attributes and apply crafting efficiency
    private void adjustItemAttributes(ItemStack item, int skillLevel) {
        double durabilityMultiplier = calculateDurabilityMultiplier(skillLevel);
        applyDurability(item, durabilityMultiplier);

        // Apply crafting efficiency for material cost reduction and attribute enhancement
        applyCraftingEfficiency(item, skillLevel);
    }

    // Define the applyCraftingEfficiency method
    private void applyCraftingEfficiency(ItemStack craftedItem, double efficiencyBonus) {
        ItemMeta meta = craftedItem.getItemMeta();
        if (meta != null) {
            short maxDurability = craftedItem.getType().getMaxDurability();
            short currentDurability = craftedItem.getDurability();
            short newDurability = (short) Math.max(0, currentDurability - (short) (maxDurability * efficiencyBonus));

            craftedItem.setDurability(newDurability);

            // Add lore to reflect efficiency bonus
            meta.setLore(java.util.Collections.singletonList(ChatColor.GREEN + "Crafting Efficiency: +" + (efficiencyBonus * 100) + "%"));
            craftedItem.setItemMeta(meta);
        }
    }

    // Calculate the durability multiplier based on skill level
    private double calculateDurabilityMultiplier(int skillLevel) {
        if (skillLevel < 10) {
            return 0.5; // Weak
        } else if (skillLevel < 30) {
            return 0.75; // Common
        } else if (skillLevel < 50) {
            return 1.0; // Uncommon
        } else if (skillLevel < 80) {
            return 1.25; // Rare
        } else if (skillLevel < 110) {
            return 1.5; // Epic
        } else if (skillLevel < 140) {
            return 1.75; // Perfect
        } else if (skillLevel < 160) {
            return 2.0; // Godly
        } else {
            return 2.5; // Primordial
        }
    }

    // Apply durability adjustments, making the item useless instead of breaking
    private void applyDurability(ItemStack item, double durabilityMultiplier) {
        short maxDurability = item.getType().getMaxDurability();
        short currentDurability = item.getDurability();
        short newDurability = (short) Math.max(0, currentDurability - (short) (maxDurability * durabilityMultiplier));

        if (newDurability <= 0) {
            item.setDurability(maxDurability); // Set to max durability to make it useless
            // Use BrokenItemManager to mark the item as broken
            brokenItemManager.markItemAsBroken(item);
        } else {
            item.setDurability(newDurability);
        }
    }

    // Apply quality-specific bonuses
    private void applyQualityBonuses(ItemStack item, Quality quality) {
        switch (quality) {
            case WEAK:
                // Apply penalties or leave neutral
                break;
            case COMMON:
                // Neutral stats
                break;
            case UNCOMMON:
                addCriticalHitChance(item, 2.0); // Example
                break;
            case RARE:
                addCriticalHitChance(item, 6.0);
                addAttackSpeedBonus(item, 5.0);
                break;
            case EPIC:
                addCriticalHitChance(item, 10.0);
                addLifesteal(item, 2.0);
                break;
            case PERFECT:
                addCriticalHitChance(item, 12.0);
                addLifesteal(item, 4.0);
                addDurabilityRegen(item, 1.0); // Regenerate 1% durability
                break;
            case GODLY:
                addCriticalHitChance(item, 15.0);
                addLifesteal(item, 5.0);
                addDurabilityRegen(item, 2.0);
                break;
            case PRIMORDIAL:
                addCriticalHitChance(item, 20.0);
                addLifesteal(item, 8.0);
                addDurabilityRegen(item, 3.0);
                addLuckBonus(item, 10.0); // Extra luck
                break;
        }
    }

    // Add critical hit chance bonus
    private void addCriticalHitChance(ItemStack item, double chance) {
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.getPersistentDataContainer().set(new NamespacedKey(plugin, "critical_chance"), PersistentDataType.DOUBLE, chance);
            item.setItemMeta(meta);
        }
    }

    // Add lifesteal bonus
    private void addLifesteal(ItemStack item, double lifestealPercentage) {
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.getPersistentDataContainer().set(new NamespacedKey(plugin, "lifesteal"), PersistentDataType.DOUBLE, lifestealPercentage);
            item.setItemMeta(meta);
        }
    }

    // Add attack speed bonus
    private void addAttackSpeedBonus(ItemStack item, double attackSpeed) {
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.addAttributeModifier(org.bukkit.attribute.Attribute.GENERIC_ATTACK_SPEED,
                    new org.bukkit.attribute.AttributeModifier(java.util.UUID.randomUUID(), "attack_speed", attackSpeed, org.bukkit.attribute.AttributeModifier.Operation.ADD_NUMBER));
            item.setItemMeta(meta);
        }
    }

    // Add durability regeneration bonus
    private void addDurabilityRegen(ItemStack item, double regenRate) {
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.getPersistentDataContainer().set(new NamespacedKey(plugin, "durability_regen"), PersistentDataType.DOUBLE, regenRate);
            item.setItemMeta(meta);
        }
    }

    // Add luck bonus
    private void addLuckBonus(ItemStack item, double luckBonus) {
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.getPersistentDataContainer().set(new NamespacedKey(plugin, "luck_bonus"), PersistentDataType.DOUBLE, luckBonus);
            item.setItemMeta(meta);
        }
    }

    // Custom logic for machine crafting bonuses
    private void applyMachineCraftingBonuses(Player player, ItemStack craftedItem, int skillLevel) {
        double machineBonus = skillLevel * 0.1;
        applyCraftingEfficiency(craftedItem, skillLevel + machineBonus);
        applyDurability(craftedItem, 1.2);
    }

    // Apply additional smithing logic based on skill level
    public void applySmithingBonuses(Player player, ItemStack item, int skillLevel) {
        if (item == null) return;

        if (skillLevel >= 20 && Math.random() < 0.1) { // 10% chance of elemental infusion
            applyElementalInfusion(item);
        }

        if (skillLevel >= 40 && (item.getType().name().endsWith("_SWORD") || item.getType().name().endsWith("_AXE"))) {
            increaseWeaponSharpness(item, skillLevel);
        }

        if (skillLevel >= 60 && (item.getType().name().endsWith("_HELMET") || item.getType().name().endsWith("_CHESTPLATE")
                || item.getType().name().endsWith("_LEGGINGS") || item.getType().name().endsWith("_BOOTS"))) {
            reinforceArmor(item, skillLevel);
        }
    }

    // Apply elemental infusion to the item
    private void applyElementalInfusion(ItemStack item) {
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(meta.getDisplayName() + ChatColor.BLUE + " [Elemental Infusion]");
            item.setItemMeta(meta);
        }
    }

    // Increase weapon sharpness based on skill level
    private void increaseWeaponSharpness(ItemStack item, int skillLevel) {
        int sharpnessLevel = (int) Math.floor(skillLevel / 10.0);
        item.addUnsafeEnchantment(Enchantment.SHARPNESS, sharpnessLevel);
    }

    // Reinforce armor for increased durability
    private void reinforceArmor(ItemStack item, int skillLevel) {
        int durabilityBonus = skillLevel * 10;
        item.setDurability((short) Math.max(0, item.getDurability() - durabilityBonus));
    }

    // Increase enchantment effectiveness based on skill level
    public void increaseEnchantmentEffectiveness(Player player, ItemStack item, double effectivenessBonus) {
        if (item != null && item.getType() != Material.AIR) {
            item.getEnchantments().forEach((enchantment, level) -> {
                int newLevel = level + (int) (effectivenessBonus * level);
                item.addUnsafeEnchantment(enchantment, newLevel);
            });
        }
    }

    // Potion Effectiveness Increase Logic
    public void increasePotionEffectiveness(Player player, ItemStack potionItem, double effectivenessBonus) {
        if (potionItem != null) {
            // Logic for increasing the effectiveness of the potion
            player.sendMessage(ChatColor.GREEN + "Potion effectiveness increased by " + effectivenessBonus + "% for item: " + potionItem.getType());
            // You can also modify the item meta or effects based on this logic
        } else {
            player.sendMessage(ChatColor.RED + "No potion item provided to apply effectiveness bonus.");
        }
    }

    // Potion Duration Increase Logic
    public void increasePotionDuration(Player player, double durationBonus) {
        player.sendMessage(ChatColor.GREEN + "Potion duration increased by " + durationBonus + "%.");
    }

    // Brew Rare Potion Logic
    public void brewRarePotion(Player player, ItemStack potionItem) {
        player.sendMessage(ChatColor.LIGHT_PURPLE + "You have successfully brewed a rare potion!");
        ItemMeta meta = potionItem.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(ChatColor.LIGHT_PURPLE + "Rare Potion");
            meta.setLore(java.util.Collections.singletonList(ChatColor.DARK_PURPLE + "This potion has rare effects."));
            potionItem.setItemMeta(meta);
        }
    }

    // Apply repair bonuses based on skill level
    public void applyRepairBonuses(Player player, ItemStack item, int skillLevel) {
        double repairCostReduction = skillLevel * 0.01; // 1% cost reduction per level
        double repairEffectivenessBonus = skillLevel * 0.02; // 2% more durability restored per level

        applyRepairCostReduction(item, repairCostReduction);
        applyRepairEffectiveness(item, repairEffectivenessBonus);
    }

    private void applyRepairCostReduction(ItemStack item, double reduction) {
        // Logic to reduce the material cost to repair the item
    }

    public void applyRepairEffectiveness(ItemStack item, double effectivenessBonus) {
        // Logic to increase how much durability is restored when the item is repaired
        short currentDurability = item.getDurability();
        short maxDurability = item.getType().getMaxDurability();
        short newDurability = (short) Math.max(0, currentDurability - (maxDurability * effectivenessBonus));
        item.setDurability(newDurability);
    }

    // Apply crafting efficiency based on skill level
    public void applyCraftingEfficiency(Player player, int skillLevel) {
        double efficiencyBonus = skillLevel * 0.01;  // 1% material cost reduction per level
        reduceCraftingMaterialCost(player, efficiencyBonus);
    }

    private void reduceCraftingMaterialCost(Player player, double efficiencyBonus) {
        // Logic to reduce the amount of materials required to craft items
    }

    public void applyRepairBonuses(Player player, double repairCostReduction, double repairEffectivenessBonus) {
        // Overloaded method if item is not passed
    }

    // Apply over-repair logic if skill level is above 100
    public void applyOverRepair(Player player, ItemStack item, double overRepairBonus) {
        short maxDurability = item.getType().getMaxDurability();
        short currentDurability = item.getDurability();
        short newDurability = (short) Math.max(0, currentDurability - (maxDurability * (overRepairBonus * 1.5)));

        if (newDurability > maxDurability) {
            item.setDurability(maxDurability);  // Cap durability at over-repair limit
            player.sendMessage(ChatColor.GREEN + "Your item has been over-repaired, granting temporary bonuses!");
            item.addUnsafeEnchantment(Enchantment.SHARPNESS, 1);  // Temporary bonus
        } else {
            item.setDurability(newDurability);
        }
    }

    public void applyOverRepair(Player player, double overRepairBonus) {
        // Overloaded method if item is not passed
    }

    // Track and reward repairs
    private Map<UUID, Integer> repairCountMap = new HashMap<>();

    public void trackAndRewardRepairs(Player player) {
        UUID playerUUID = player.getUniqueId();
        int repairCount = repairCountMap.getOrDefault(playerUUID, 0);
        repairCount++;
        repairCountMap.put(playerUUID, repairCount);

        if (repairCount % 10 == 0) {
            player.sendMessage(ChatColor.GOLD + "You've repaired 10 items! Here's a bonus!");
            player.getInventory().addItem(new ItemStack(Material.DIAMOND));
        }
    }

    // Apply crafting success bonuses
    public void applyCraftingSuccess(Player player, ItemStack item, int skillLevel) {
        double successChance = Math.min(0.05 + (skillLevel * 0.01), 1.0);  // Max success chance is 100%
        if (random.nextDouble() <= successChance) {
            // Critical success logic: Add special enchantments or extra item stats
            player.sendMessage(ChatColor.GOLD + "Critical Success! Your item is of superior quality!");
            item.addUnsafeEnchantment(Enchantment.UNBREAKING, 3);
        }
    }

    // Unlock special advanced recipes at higher levels
    public void unlockAdvancedRecipe(CustomPlayer customPlayer, Object recipe) {
        int skillLevel = customPlayer.getSkillLevel("Crafting");
        if (skillLevel >= 100) {
            customPlayer.getPlayer().sendMessage("You have unlocked advanced crafting recipes!");
        }
    }

    // Apply food quality bonuses (Cooking)
    public void applyFoodQualityBonus(Player player, double foodQualityBonus) {
        ItemStack foodItem = player.getInventory().getItemInMainHand();
        if (foodItem != null && foodItem.getType().isEdible()) {
            enhanceFoodItem(foodItem, foodQualityBonus);
            player.sendMessage("Food quality improved by " + (foodQualityBonus * 100) + "%!");
        }
    }

    private void enhanceFoodItem(ItemStack foodItem, double bonus) {
        // Logic to increase the food's saturation or other benefits
    }

    // Enhance healing items (First Aid)
    public void enhanceHealingItems(Player player, ItemStack item, double skillLevel) {
        double healingBonus = skillLevel * 0.05;
        applyHealingBonus(item, healingBonus);
    }

    private void applyHealingBonus(ItemStack item, double bonus) {
        // Logic to modify the healing effect of the item
    }

    // Enhance stealth-related items (Stealth)
    public void enhanceStealthItems(Player player, ItemStack item, double skillLevel) {
        double stealthBonus = skillLevel * 0.02;
        applyStealthBonus(item, stealthBonus);
    }

    private void applyStealthBonus(ItemStack item, double stealthBonus) {
        // Logic to increase the effectiveness of stealth items
    }

    // Enhance traps (Trap Mastery)
    public void enhanceTraps(Player player, ItemStack item, int skillLevel) {
        double trapPotencyBonus = skillLevel * 0.05;
        applyTrapPotencyBonus(item, trapPotencyBonus);
    }

    private void applyTrapPotencyBonus(ItemStack item, double bonus) {
        // Logic to increase trap effectiveness
    }

    // Improve scavenged items (Scavenging)
    public void improveScavengedItems(Player player, ItemStack item, int skillLevel) {
        double luckBonus = skillLevel * 0.02;
        increaseScavengedLootQuality(item, luckBonus);
    }

    private void increaseScavengedLootQuality(ItemStack item, double bonus) {
        // Logic to increase chances of higher-tier loot from scavenging
    }

    // Improve repair quality (Repairing)
    public void improveRepairingQuality(Player player, ItemStack item, int skillLevel) {
        double repairBonus = skillLevel * 0.03;
        applyImprovedRepairQuality(item, repairBonus);
    }

    private void applyImprovedRepairQuality(ItemStack item, double bonus) {
        // Logic to improve repair quality
    }

    // Enhance trade value (Trading)
    public void enhanceTradeValue(Player player, ItemStack item, int skillLevel) {
        double tradeBonus = skillLevel * 0.03;
        applyTradeValueBonus(item, tradeBonus);
    }

    private void applyTradeValueBonus(ItemStack item, double bonus) {
        // Logic to increase trade value
    }

    // Improve navigation tools (Navigation)
    public void improveNavigationTools(Player player, ItemStack item, int skillLevel) {
        double speedBonus = skillLevel * 0.01;
        applyNavigationToolBonus(item, speedBonus);
    }

    private void applyNavigationToolBonus(ItemStack item, double bonus) {
        // Logic to improve navigation tools
    }

    // Improve taming items (Animal Taming)
    public void improveTamingItems(Player player, ItemStack item, int skillLevel) {
        double tamingBonus = skillLevel * 0.03;
        applyTamingBonus(item, tamingBonus);
    }

    private void applyTamingBonus(ItemStack item, double bonus) {
        // Logic to increase taming effectiveness
    }

    // Improve riding equipment (Riding)
    public void improveRidingEquipment(Player player, ItemStack item, int skillLevel) {
        double speedBonus = skillLevel * 0.05;
        applyMountSpeedBonus(item, speedBonus);
    }

    private void applyMountSpeedBonus(ItemStack item, double bonus) {
        // Logic to improve riding equipment
    }

    // Improve exploration gear (Exploration)
    public void improveExplorationGear(Player player, ItemStack item, int skillLevel) {
        double bonus = skillLevel * 0.04;
        applyExplorationGearBonus(item, bonus);
    }

    private void applyExplorationGearBonus(ItemStack item, double bonus) {
        // Logic to improve exploration gear
    }

    // Enhance lockpicks (Lockpicking)
    public void enhanceLockpicks(Player player, ItemStack item, int skillLevel) {
        double lockpickingBonus = skillLevel * 0.02;
        applyLockpickingBonus(item, lockpickingBonus);
    }

    private void applyLockpickingBonus(ItemStack item, double bonus) {
        // Logic to enhance lockpicking success rate
    }

    // Improve survival tools (Survival)
    public void improveSurvivalTools(Player player, ItemStack item, int skillLevel) {
        double toughnessBonus = skillLevel * 0.03;
        applySurvivalToolBonus(item, toughnessBonus);
    }

    private void applySurvivalToolBonus(ItemStack item, double bonus) {
        // Logic to improve survival tools
    }
}