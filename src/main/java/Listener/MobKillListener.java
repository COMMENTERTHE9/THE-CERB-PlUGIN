package Listener;

import GUIs.SkillGUI;
import Skills.SkillManager;
import Skills.UtilitySkill;
import CustomTags.ElementalTag;
import cerberus.world.cerb.CustomPlayer;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

public class MobKillListener implements Listener {
    private final SkillManager skillManager;
    private final SkillGUI skillGUI;

    public MobKillListener(SkillManager skillManager, SkillGUI skillGUI) {
        this.skillManager = skillManager;
        this.skillGUI = skillGUI;
    }



    @EventHandler
    public void onEntityDeath(EntityDeathEvent event) {
        // Check if the killer is a player
        if (event.getEntity().getKiller() instanceof Player) {
            Player player = event.getEntity().getKiller();

            // Get some information about the killed entity
            double health = event.getEntity().getMaxHealth();
            double damage = event.getEntity().getLastDamage();
            boolean isRare = false; // You can customize this to check if the mob is rare
            boolean isBoss = determineIfBoss(event); // Determine if the entity is a boss

            // Get the weapon used by the player and determine the skill
            ItemStack weapon = player.getInventory().getItemInMainHand();
            String skillName = determineSkill(weapon, player);

            if (!skillName.isEmpty()) {
                // Calculate the XP gained based on the mob's health and damage done
                double xpGained = skillManager.calculateDynamicXp(health, damage, isRare, isBoss);

                // Add XP and check if the player levels up
                boolean leveledUp = skillManager.addXpAndCheckLevelUp(skillName, xpGained, player);

                // Notify the player of the XP gained
                player.sendMessage("XP gained: " + xpGained + " in " + skillName);

                // If the player leveled up, send a level-up message and update their skill GUI
                if (leveledUp) {
                    int level = skillManager.getSkillLevel(skillName);

                    // Create a level-up message and send it to the player
                    String levelUpMessage = skillManager.createLevelUpMessage(skillName, level);
                    player.sendMessage(levelUpMessage);
                }

                // Refresh the skill menu if the player is currently viewing it
                skillGUI.startRealTimeXPUpdate(player, determineSkillType(skillName));
            }
        }
    }

    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        // Check if the player actually moved a significant distance
        if (event.getFrom().distanceSquared(event.getTo()) < 0.01) {
            return; // Movement is too small, ignore
        }

        Player player = event.getPlayer();
        CustomPlayer customPlayer = getCustomPlayer(player);

        if (customPlayer != null) {
            String skillName = "Navigation";
            UtilitySkill skill = (UtilitySkill) skillManager.getSkill(skillName);

            if (skill != null) {
                skill.applyEffect(customPlayer);
            }
        }
    }


    private boolean determineIfBoss(EntityDeathEvent event) {
        // Logic to determine if the entity is a boss
        return false; // Default to false, needs proper implementation
    }

    private String determineSkill(ItemStack weapon, Player player) {
        if (weapon == null || weapon.getType() == Material.AIR) {
            // Unarmed combat
            return "Martial Expertise";
        } else if (weapon.getType().name().endsWith("_SWORD")) {
            // Bladed weapons
            return "Blade Mastery";
        } else if (weapon.getType().name().endsWith("_AXE") || weapon.getType().name().endsWith("_HAMMER")) {
            // Axes or hammers
            return "Weapon Mastery";
        } else if (weapon.getType() == Material.BOW || weapon.getType() == Material.CROSSBOW) {
            // Ranged weapons
            return "Ranged Precision";
        } else if (weapon.getType() == Material.ENCHANTED_BOOK) {
            // Assume books are used as a representation of magical skills
            return "Arcane Knowledge";
        } else if (weapon.getType() == Material.STICK) {
            // Assume a stick could represent a magic staff
            return "Elemental Mastery";
        } else if (weapon.getType().name().contains("WAND")) {
            // Wands can be used to represent spell weaving or summoning
            return "Spell Weaving";
        }

        // Check for elemental tag on weapon
        ElementalTag elementalTag = getElementalTagFromWeapon(weapon);
        if (elementalTag != null) {
            return getElementalSkillName(elementalTag);
        }

        // Check for dual-wielding
        if (player.getInventory().getItemInOffHand() != null && !player.getInventory().getItemInOffHand().getType().equals(Material.AIR)) {
            return "Dual Wielding";
        }

        // Handle Heavy Armor Training
        if (player.getInventory().getHelmet() != null || player.getInventory().getChestplate() != null ||
                player.getInventory().getLeggings() != null || player.getInventory().getBoots() != null) {
            return "Heavy Armor Training";
        }

        // Check for spell casting scenarios
        if (player.hasPermission("cerberus.spellweaving")) {
            return "Spell Weaving";
        } else if (player.hasPermission("cerberus.summoning")) {
            return "Summoning";
        }

        // Handle Mana Regeneration on all kills
        if (player.hasPermission("cerberus.manaregeneration")) {
            return "Mana Regeneration";
        }

        return ""; // No skill matched
    }

    private String determineUtilitySkill(Player player) {
        ItemStack itemInHand = player.getInventory().getItemInMainHand();

        if (itemInHand == null || itemInHand.getType() == Material.AIR) {
            return ""; // No item in hand, no utility skill to apply
        }

        Material itemType = itemInHand.getType();

        // Determine utility skill based on the item type
        if (itemType == Material.WOODEN_PICKAXE || itemType == Material.STONE_PICKAXE ||
                itemType == Material.IRON_PICKAXE || itemType == Material.GOLDEN_PICKAXE ||
                itemType == Material.DIAMOND_PICKAXE || itemType == Material.NETHERITE_PICKAXE) {
            return "Mining";
        } else if (itemType == Material.WOODEN_AXE || itemType == Material.STONE_AXE ||
                itemType == Material.IRON_AXE || itemType == Material.GOLDEN_AXE ||
                itemType == Material.DIAMOND_AXE || itemType == Material.NETHERITE_AXE) {
            return "Woodcutting";
        } else if (itemType == Material.WOODEN_HOE || itemType == Material.STONE_HOE ||
                itemType == Material.IRON_HOE || itemType == Material.GOLDEN_HOE ||
                itemType == Material.DIAMOND_HOE || itemType == Material.NETHERITE_HOE) {
            return "Farming";
        } else if (itemType == Material.FISHING_ROD) {
            return "Fishing";
        } else if (itemType == Material.SHEARS) {
            return "Herbalism";
        } else if (itemType == Material.ANVIL || itemType == Material.GRINDSTONE) {
            return "Smithing";
        } else if (itemType == Material.BREWING_STAND) {
            return "Alchemy";
        } else if (itemType == Material.ENCHANTING_TABLE) {
            return "Enchanting";
        } else if (itemType == Material.CRAFTING_TABLE) {
            return "Crafting";
        } else if (itemType == Material.CAULDRON) {
            return "Cooking";
        } else if (itemType == Material.CHEST) {
            return "Scavenging";
        } else if (itemType == Material.TRIPWIRE_HOOK || itemType == Material.LEVER) {
            return "Trap Mastery";
        } else if (itemType == Material.ELYTRA) {
            return "Navigation"; // We'll trigger Navigation on any form of movement
        } else if (itemType == Material.LEAD) {
            return "Animal Taming";
        } else if (itemType == Material.SADDLE) {
            return "Riding";
        } else if (itemType == Material.FLINT_AND_STEEL) {
            return "Survival";
        } else if (itemType == Material.GOLD_NUGGET || itemType == Material.EMERALD ||
                itemType == Material.DIAMOND) {
            return "Trading";
        } else if (itemType == Material.NAME_TAG) {
            return "First Aid"; // Example, could represent tagging and saving players
        } else if (itemType == Material.TRIPWIRE_HOOK) {
            return "Lockpicking";
        } else if (itemType == Material.LEATHER_BOOTS || itemType == Material.LEATHER_HELMET) {
            return "Stealth";
        } else {
            return ""; // No utility skill matched
        }
    }

    private CustomPlayer getCustomPlayer(Player player) {
        return skillManager.getCustomPlayer(player.getUniqueId());
    }

    private ElementalTag getElementalTagFromWeapon(ItemStack weapon) {
        if (weapon == null || !weapon.hasItemMeta()) {
            return null; // No weapon or no metadata
        }

        // Check if the weapon has custom NBT data for elemental tags
        if (weapon.getItemMeta().getPersistentDataContainer().has(new NamespacedKey("cerberus", "elemental_tag"), PersistentDataType.STRING)) {
            String elementType = weapon.getItemMeta().getPersistentDataContainer().get(new NamespacedKey("cerberus", "elemental_tag"), PersistentDataType.STRING);
            if (elementType != null) {
                // Retrieve the elemental tag based on the stored data
                switch (elementType.toUpperCase()) {
                    case "FIRE":
                        return new ElementalTag(ElementalTag.ElementType.FIRE, 1.2, 0.1, 0.5);
                    case "ICE":
                        return new ElementalTag(ElementalTag.ElementType.ICE, 1.1, 0.2, 0.4);
                    case "LIGHTNING":
                        return new ElementalTag(ElementalTag.ElementType.LIGHTNING, 1.3, 0.0, 0.6);
                    case "ARCANE":
                        return new ElementalTag(ElementalTag.ElementType.ARCANE, 1.4, 0.0, 0.7);
                    case "DARK":
                        return new ElementalTag(ElementalTag.ElementType.DARK, 1.5, 0.0, 0.8);
                    case "EARTH":
                        return new ElementalTag(ElementalTag.ElementType.EARTH, 1.1, 0.3, 0.4);
                    case "WIND":
                        return new ElementalTag(ElementalTag.ElementType.WIND, 1.2, 0.0, 0.5);
                    case "BINDING":
                        return new ElementalTag(ElementalTag.ElementType.BINDING, 1.0, 0.0, 0.3);
                    default:
                        return null; // Unknown element type
                }
            }
        }

        return null; // No elemental tag found
    }

    private String getElementalSkillName(ElementalTag elementalTag) {
        switch (elementalTag.getElementType()) {
            case FIRE:
                return "Pyromancy";
            case ICE:
                return "Cryomancy";
            case LIGHTNING:
                return "Electromancy";
            case ARCANE:
                return "Arcane Knowledge";
            case DARK:
                return "Dark Arts";
            case EARTH:
                return "Geomancy"; // Assuming a skill for earth element
            case WIND:
                return "Aeromancy"; // Assuming a skill for wind element
            default:
                return "";
        }
    }

    private String determineSkillType(String skillName) {
        if (isCombatSkill(skillName)) {
            return "combat";
        } else if (isMagicSkill(skillName)) {
            return "magic";
        } else {
            return "utility";
        }
    }

    private boolean isCombatSkill(String skillName) {
        return skillName.equalsIgnoreCase("Blade Mastery") || skillName.equalsIgnoreCase("Martial Expertise")
                || skillName.equalsIgnoreCase("Weapon Mastery") || skillName.equalsIgnoreCase("Ranged Precision")
                || skillName.equalsIgnoreCase("Heavy Armor Training") || skillName.equalsIgnoreCase("Dual Wielding")
                || skillName.equalsIgnoreCase("Critical Strike");
    }

    private boolean isMagicSkill(String skillName) {
        return skillName.equalsIgnoreCase("Intelligence") || skillName.equalsIgnoreCase("Arcane Knowledge")
                || skillName.equalsIgnoreCase("Elemental Mastery") || skillName.equalsIgnoreCase("Summoning")
                || skillName.equalsIgnoreCase("Spell Weaving") || skillName.equalsIgnoreCase("Mana Regeneration")
                || skillName.equalsIgnoreCase("Defensive Magic");
    }


    // Method to create the level-up message
    private String createLevelUpMessage(String skillName, int level) {
        StringBuilder message = new StringBuilder();
        message.append("§6§m--------------------------------------------------\n")  // Gold color with strikethrough line
                .append("§e§l| LEVEL UP! §b").append(skillName).append(" §e|\n")  // Yellow bold "LEVEL UP!" and skill name in aqua
                .append("§6§m--------------------------------------------------\n")
                .append("§7| You have reached §a").append(level).append(" §7in §b").append(skillName).append("! §7|\n")  // Level and skill name with colors
                .append("§6§m--------------------------------------------------\n");

        switch (skillName) {
            case "Blade Mastery":
                message.append("§c➜ Damage Increase: §a+").append(level * 10.0).append("% §7|\n")
                        .append("§c➜ Attack Speed Increase: §a+").append(level * 5.0).append("% §7|\n")
                        .append("§c➜ Movement Speed Increase: §a+").append(level * 1.0).append("% §7|\n");
                break;
            case "Martial Expertise":
                message.append("§c➜ Unarmed Damage Increase: §a+").append(level * 15.0).append("% §7|\n")
                        .append("§c➜ Knockback Resistance: §a+").append(level * 1.0).append("% §7|\n");
                break;
            case "Weapon Mastery":
                message.append("§c➜ Weapon Damage Increase: §a+").append(level * 12.0).append("% §7|\n");
                break;
            case "Ranged Precision":
                message.append("§c➜ Ranged Damage Increase: §a+").append(level * 10.0).append("% §7|\n")
                        .append("§c➜ Critical Hit Chance: §a+").append(level * 1.0).append("% §7|\n")
                        .append("§c➜ Draw Speed Increase: §a+").append(level * 0.5).append("% §7|\n")
                        .append("§c➜ Arrow Velocity Increase: §a+").append(level * 1.0).append("% §7|\n");
                break;
            case "Heavy Armor Training":
                message.append("§c➜ Damage Reduction: §a+").append(level * 8.0).append("% §7|\n")
                        .append("§c➜ Endurance Increase: §a+").append(level * 5.0).append("% §7|\n")
                        .append("§c➜ Knockback Resistance: §a+").append(level * 1.0).append("% §7|\n");
                break;
            case "Dual Wielding":
                message.append("§c➜ Offhand Damage Increase: §a+").append(level * 20.0).append("% §7|\n")
                        .append("§c➜ Attack Speed Increase: §a+").append(level * 10.0).append("% §7|\n")
                        .append("§c➜ Chance for Additional Strike: §a+").append(level * 5.0).append("% §7|\n");
                break;
            case "Critical Strike":
                message.append("§c➜ Critical Hit Damage: §a+").append(level * 20.0).append("% §7|\n")
                        .append("§c➜ Critical Hit Chance: §a+").append(level * 1.0).append("% §7|\n");
                break;
            case "Intelligence":
                message.append("§c➜ Mana Increase: §a+").append(level * 10.0).append(" §7|\n")
                        .append("§c➜ Spell Power Increase: §a+").append(level * 5.0).append("% §7|\n");
                break;
            case "Arcane Knowledge":
                message.append("§c➜ Spell Range Increase: §a+").append(level * 10.0).append("% §7|\n")
                        .append("§c➜ Mana Cost Reduction: §a-").append(level * 5.0).append("% §7|\n");
                break;
            case "Elemental Mastery":
                message.append("§c➜ Elemental Damage Increase: §a+").append(level * 15.0).append("% §7|\n")
                        .append("§c➜ Control over Elemental Magic: §a+").append(level * 5.0).append("% §7|\n");
                break;
            case "Spell Weaving":
                message.append("§c➜ Spell Combination Effectiveness: §a+").append(level * 10.0).append("% §7|\n")
                        .append("§c➜ Reduced Mana Cost for Weaving: §a-").append(level * 4.0).append("% §7|\n");
                break;
            case "Summoning":
                message.append("§c➜ Summon Duration: §a+").append(level * 10.0).append("% §7|\n")
                        .append("§c➜ Summon Strength: §a+").append(level * 10.0).append("% §7|\n");
                break;
            case "Mana Regeneration":
                message.append("§c➜ Mana Regeneration Rate: §a+").append(level * 5.0).append("% §7|\n");
                break;
            case "Herbalism":
                message.append("§c➜ Herb Yield Increase: §a+").append(level * 10.0).append("% §7|\n")
                        .append("§c➜ Potion Duration Increase: §a+").append(level * 5.0).append("% §7|\n");
                break;
            case "Alchemy":
                message.append("§c➜ Potion Effectiveness Increase: §a+").append(level * 10.0).append("% §7|\n");
                break;
            case "Cooking":
                message.append("§c➜ Food Quality Increase: §a+").append(level * 7.0).append("% §7|\n")
                        .append("§c➜ Hunger Satisfaction Increase: §a+").append(level * 5.0).append("% §7|\n");
                break;
            case "First Aid":
                message.append("§c➜ Healing Item Effectiveness: §a+").append(level * 12.0).append("% §7|\n");
                break;
            case "Smithing":
                message.append("§c➜ Weapon Durability Increase: §a+").append(level * 10.0).append("% §7|\n")
                        .append("§c➜ Armor Durability Increase: §a+").append(level * 8.0).append("% §7|\n")
                        .append("§c➜ Enchantment Efficiency: §a+").append(level * 6.0).append("% §7|\n");
                break;
            case "Crafting":
                message.append("§c➜ Crafting Efficiency: §a+").append(level * 8.0).append("% §7|\n")
                        .append("§c➜ Unlock Special Recipes: §a+").append(level * 3.0).append("% §7|\n");
                break;
            case "Survival":
                message.append("§c➜ Armor Toughness: §a+").append(level * 10.0).append("% §7|\n")
                        .append("§c➜ Damage Resistance: §a+").append(level * 5.0).append("% §7|\n");
                break;
            case "Lockpicking":
                message.append("§c➜ Lockpicking Success Chance: §a+").append(level * 10.0).append("% §7|\n");
                break;
            case "Riding":
                message.append("§c➜ Mount Speed Increase: §a+").append(level * 5.0).append("% §7|\n")
                        .append("§c➜ Reduced Fall Damage: §a+").append(level * 5.0).append("% §7|\n");
                break;
            case "Animal Taming":
                message.append("§c➜ Taming Success Chance: §a+").append(level * 5.0).append("% §7|\n")
                        .append("§c➜ Enhanced Tamed Animal Attributes: §a+").append(level * 3.0).append("% §7|\n");
                break;
            case "Navigation":
                message.append("§c➜ Movement Speed: §a+").append(level * 2.0).append("% §7|\n");
                break;
            case "Trading":
                message.append("§c➜ Trade Value Increase: §a+").append(level * 3.0).append("% §7|\n");
                break;
            case "Repairing":
                message.append("§c➜ Repair Cost Reduction: §a+").append(level * 1.0).append("% §7|\n")
                        .append("§c➜ Durability Restored: §a+").append(level * 2.0).append("% §7|\n");
                break;
            case "Scavenging":
                message.append("§c➜ Luck Increase: §a+").append(level * 0.5).append("% §7|\n")
                        .append("§c➜ Magic Find Increase: §a+").append(level * 0.2).append("% §7|\n");
                break;
            case "Trap Mastery":
                message.append("§c➜ Trap Potency Increase: §a+").append(level * 10.0).append("% §7|\n")
                        .append("§c➜ Trap Trigger Sensitivity: §a+").append(level * 5.0).append("% §7|\n");
                break;
            case "Stealth":
                message.append("§c➜ Stealth Effectiveness: §a+").append(level * 5.0).append("% §7|\n");
                break;
            case "Fishing":
                message.append("§c➜ Fishing Yield Increase: §a+").append(level * 5.0).append("% §7|\n")
                        .append("§c➜ Fishing Speed Increase: §a+").append(level * 1.0).append("% §7|\n");
                break;
            case "Woodcutting":
                message.append("§c➜ Wood Yield Increase: §a+").append(level * 5.0).append("% §7|\n")
                        .append("§c➜ Axe Durability Increase: §a+").append(level * 1.0).append("% §7|\n");
                break;
            case "Farming":
                message.append("§c➜ Crop Yield Increase: §a+").append(level * 5.0).append("% §7|\n")
                        .append("§c➜ Farming Tool Durability: §a+").append(level * 1.0).append("% §7|\n");
                break;
            case "Mining":
                message.append("§c➜ Ore Yield Increase: §a+").append(level * 5.0).append("% §7|\n")
                        .append("§c➜ Pickaxe Durability: §a+").append(level * 2.0).append("% §7|\n");
                break;
            default:
                message.append("§c➜ Stat Increase: §a+").append(level * 0.5).append("% §7|\n");
        }

        message.append("§6§m--------------------------------------------------\n")
                .append("§e| §bKeep training to unlock more power! §e|\n")
                .append("§6§m--------------------------------------------------");

        return message.toString();
    }
}
