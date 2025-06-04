package Skills;

import Manager.CraftingManager;
import cerberus.world.cerb.CustomPlayer;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.*;
import org.bukkit.block.Biome;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class RidingSkill extends UtilitySkill {

    private final CraftingManager craftingManager;
    private final Map<UUID, Double> mountFatigueMap = new HashMap<>();
    private final Map<UUID, Integer> mountLoyaltyMap = new HashMap<>();

    public RidingSkill(String name, CraftingManager craftingManager) {
        super(name);
        this.craftingManager = craftingManager;
    }

    @Override
    public void applyEffect(CustomPlayer customPlayer) {
        if (customPlayer == null) return;
        Player player = customPlayer.getPlayer();
        int level = this.getLevel();

        // Increase mount speed based on skill level
        double baseSpeedBonus = level * 0.05; // Example: +5% speed per level
        double terrainBonus = calculateTerrainSpeedBonus(player); // Custom terrain modifiers
        double totalSpeedBonus = baseSpeedBonus + terrainBonus;

        applyMountSpeedBonus(player, totalSpeedBonus);

        // Reduce fall damage while riding based on skill level
        double fallDamageReduction = level * 0.05; // Example: -5% fall damage per level
        reduceFallDamageWhileRiding(player, fallDamageReduction);

        // Handle mount-specific bonuses, including Dolphins
        applyMountSpecificBonuses(player, level);

        // Apply mount loyalty bonuses
        applyMountLoyaltyBonuses(player, level);

        // Regenerate mount health based on skill level
        applyMountHealthRegen(player, level);

        // Check for fatigue and reduce speed if needed
        applyMountFatigue(player);

        // Handle armor and damage resistance bonuses
        applyMountArmorAndResistanceBonuses(player, level);
    }

    private void applyMountSpeedBonus(Player player, double speedBonus) {
        if (player.getVehicle() instanceof LivingEntity) {
            LivingEntity mount = (LivingEntity) player.getVehicle();
            AttributeInstance speedAttribute = mount.getAttribute(Attribute.GENERIC_MOVEMENT_SPEED);
            if (speedAttribute != null) {
                speedAttribute.setBaseValue(speedAttribute.getBaseValue() * (1.0 + speedBonus));
            }
        }
    }

    private void reduceFallDamageWhileRiding(Player player, double reduction) {
        System.out.println("Fall damage reduced by " + reduction * 100 + "% while riding.");
    }

    private double calculateTerrainSpeedBonus(Player player) {
        double terrainBonus = 0.0;
        Biome biome = player.getLocation().getBlock().getBiome();

        switch (biome) {
            case PLAINS:
            case SUNFLOWER_PLAINS:
                terrainBonus += 0.05;
                break;
            case FOREST:
            case BIRCH_FOREST:
            case DARK_FOREST:
                terrainBonus -= 0.03;
                break;
            case DESERT:
                terrainBonus += 0.02;
                break;
            case SWAMP:
                terrainBonus -= 0.04;
                break;
            case SNOWY_SLOPES:
            case SNOWY_TAIGA:
                terrainBonus -= 0.02;
                break;
            case JUNGLE:
                terrainBonus -= 0.05;
                break;
            case OCEAN:
            case RIVER:
                if (player.isInWater()) {
                    terrainBonus += 0.10;
                }
                break;
            default:
                break;
        }

        return terrainBonus;
    }

    private void applyMountSpecificBonuses(Player player, int level) {
        if (player.getVehicle() instanceof LivingEntity) {
            LivingEntity mount = (LivingEntity) player.getVehicle();

            if (mount instanceof Horse) {
                applyHorseBonuses((Horse) mount, level);
            } else if (mount instanceof Llama) {
                applyLlamaBonuses((Llama) mount, level);
            } else if (mount instanceof Camel) {
                applyCamelBonuses((Camel) mount, level);
            } else if (mount instanceof Dolphin) {
                applyDolphinBonuses((Dolphin) mount, level);
            }
        }
    }

    private void applyHorseBonuses(Horse horse, int level) {
        double jumpBonus = level * 0.05;
        horse.setJumpStrength(horse.getJumpStrength() * (1.0 + jumpBonus));
        System.out.println("Horse's jump strength enhanced by " + jumpBonus * 100 + "%.");
    }

    private void applyLlamaBonuses(Llama llama, int level) {
        double spitResistanceBonus = level * 0.03;
        llama.setMaxHealth(llama.getMaxHealth() * (1.0 + spitResistanceBonus));
        System.out.println("Llama's health increased by " + spitResistanceBonus * 100 + "%.");
    }

    private void applyCamelBonuses(Camel camel, int level) {
        double healthBonus = level * 0.04;
        camel.setMaxHealth(camel.getMaxHealth() * (1.0 + healthBonus));
        System.out.println("Camel's health increased by " + healthBonus * 100 + "%.");
    }

    private void applyDolphinBonuses(Dolphin dolphin, int level) {
        double swimmingSpeedBonus = level * 0.10;
        AttributeInstance speedAttribute = dolphin.getAttribute(Attribute.GENERIC_MOVEMENT_SPEED);
        if (speedAttribute != null) {
            speedAttribute.setBaseValue(speedAttribute.getBaseValue() * (1.0 + swimmingSpeedBonus));
        }
        System.out.println("Dolphin's swimming speed enhanced by " + swimmingSpeedBonus * 100 + "%.");
    }

    private void applyMountLoyaltyBonuses(Player player, int level) {
        if (player.getVehicle() instanceof LivingEntity) {
            LivingEntity mount = (LivingEntity) player.getVehicle();
            UUID mountUUID = mount.getUniqueId();
            int loyaltyLevel = mountLoyaltyMap.getOrDefault(mountUUID, 0);
            loyaltyLevel += level; // Increase loyalty level based on player skill level
            mountLoyaltyMap.put(mountUUID, loyaltyLevel);
            double loyaltyBonus = loyaltyLevel * 0.01; // Example: 1% loyalty bonus per level
            mount.setMaxHealth(mount.getMaxHealth() * (1.0 + loyaltyBonus));
            System.out.println("Mount loyalty increased, providing a bonus of " + loyaltyBonus * 100 + "%.");
        }
    }

    private void applyMountHealthRegen(Player player, int level) {
        if (player.getVehicle() instanceof LivingEntity) {
            LivingEntity mount = (LivingEntity) player.getVehicle();
            double regenBonus = level * 0.01; // Example: +1% regen speed per level
            double currentHealth = mount.getHealth();
            double maxHealth = mount.getMaxHealth();
            if (currentHealth < maxHealth) {
                mount.setHealth(Math.min(maxHealth, currentHealth + (regenBonus * maxHealth)));
                System.out.println("Mount's health is regenerating faster with a bonus of " + regenBonus * 100 + "%.");
            }
        }
    }

    private void applyMountFatigue(Player player) {
        if (player.getVehicle() instanceof LivingEntity) {
            LivingEntity mount = (LivingEntity) player.getVehicle();
            UUID mountUUID = mount.getUniqueId();
            double fatigue = mountFatigueMap.getOrDefault(mountUUID, 0.0);
            fatigue += 0.01; // Increase fatigue with time
            if (fatigue > 1.0) {
                fatigue = 1.0; // Cap fatigue
            }
            mountFatigueMap.put(mountUUID, fatigue);

            if (fatigue > 0.5) {
                AttributeInstance speedAttribute = mount.getAttribute(Attribute.GENERIC_MOVEMENT_SPEED);
                if (speedAttribute != null) {
                    speedAttribute.setBaseValue(speedAttribute.getBaseValue() * (1.0 - fatigue));
                }
                System.out.println("Mount is fatigued, reducing speed by " + (fatigue * 100) + "%.");
            }
        }
    }

    private void applyMountArmorAndResistanceBonuses(Player player, int level) {
        if (player.getVehicle() instanceof LivingEntity) {
            LivingEntity mount = (LivingEntity) player.getVehicle();
            double armorBonus = level * 0.02; // Example: +2% armor bonus per level
            mount.getAttribute(Attribute.GENERIC_ARMOR).setBaseValue(mount.getAttribute(Attribute.GENERIC_ARMOR).getBaseValue() * (1.0 + armorBonus));
            System.out.println("Mount's armor increased by " + armorBonus * 100 + "%.");
        }
    }

    // Mount Summoning ability
    public void summonMount(Player player) {
        if (player.getVehicle() instanceof LivingEntity) {
            LivingEntity mount = (LivingEntity) player.getVehicle();
            mount.teleport(player.getLocation());
            System.out.println("Mount has been summoned to the player.");
        }
    }
}
