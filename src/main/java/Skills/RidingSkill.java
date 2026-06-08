package Skills;

import Manager.CraftingManager;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.*;
import org.bukkit.block.Biome;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class RidingSkill extends UtilitySkill {

    private final CraftingManager craftingManager;
    private final Map<UUID, Double> mountFatigueMap = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> mountLoyaltyMap = new ConcurrentHashMap<>();

    public RidingSkill(String name, CraftingManager craftingManager) {
        super(name);
        this.craftingManager = craftingManager;
    }

    @Override
    public void applyEffect(Player player) {
        if (player == null) return;
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
            AttributeInstance speedAttribute = mount.getAttribute(Attribute.MOVEMENT_SPEED);
            if (speedAttribute != null) {
                speedAttribute.setBaseValue(speedAttribute.getBaseValue() * (1.0 + speedBonus));
            }
        }
    }

    private void reduceFallDamageWhileRiding(Player player, double reduction) {
    }

    private double calculateTerrainSpeedBonus(Player player) {
        double terrainBonus = 0.0;
        Biome biome = player.getLocation().getBlock().getBiome();

        // Biome is now a registry type (not an enum) — use identity comparison
        if (biome == Biome.PLAINS || biome == Biome.SUNFLOWER_PLAINS) {
            terrainBonus += 0.05;
        } else if (biome == Biome.FOREST || biome == Biome.BIRCH_FOREST || biome == Biome.DARK_FOREST) {
            terrainBonus -= 0.03;
        } else if (biome == Biome.DESERT) {
            terrainBonus += 0.02;
        } else if (biome == Biome.SWAMP) {
            terrainBonus -= 0.04;
        } else if (biome == Biome.SNOWY_SLOPES || biome == Biome.SNOWY_TAIGA) {
            terrainBonus -= 0.02;
        } else if (biome == Biome.JUNGLE) {
            terrainBonus -= 0.05;
        } else if (biome == Biome.OCEAN || biome == Biome.RIVER) {
            if (player.isInWater()) {
                terrainBonus += 0.10;
            }
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
    }

    private void applyLlamaBonuses(Llama llama, int level) {
        double spitResistanceBonus = level * 0.03;
        llama.setMaxHealth(llama.getMaxHealth() * (1.0 + spitResistanceBonus));
    }

    private void applyCamelBonuses(Camel camel, int level) {
        double healthBonus = level * 0.04;
        camel.setMaxHealth(camel.getMaxHealth() * (1.0 + healthBonus));
    }

    private void applyDolphinBonuses(Dolphin dolphin, int level) {
        double swimmingSpeedBonus = level * 0.10;
        AttributeInstance speedAttribute = dolphin.getAttribute(Attribute.MOVEMENT_SPEED);
        if (speedAttribute != null) {
            speedAttribute.setBaseValue(speedAttribute.getBaseValue() * (1.0 + swimmingSpeedBonus));
        }
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
                AttributeInstance speedAttribute = mount.getAttribute(Attribute.MOVEMENT_SPEED);
                if (speedAttribute != null) {
                    speedAttribute.setBaseValue(speedAttribute.getBaseValue() * (1.0 - fatigue));
                }
            }
        }
    }

    private void applyMountArmorAndResistanceBonuses(Player player, int level) {
        if (player.getVehicle() instanceof LivingEntity) {
            LivingEntity mount = (LivingEntity) player.getVehicle();
            double armorBonus = level * 0.02; // Example: +2% armor bonus per level
            mount.getAttribute(Attribute.ARMOR).setBaseValue(mount.getAttribute(Attribute.ARMOR).getBaseValue() * (1.0 + armorBonus));
        }
    }

    // Mount Summoning ability
    public void summonMount(Player player) {
        if (player.getVehicle() instanceof LivingEntity) {
            LivingEntity mount = (LivingEntity) player.getVehicle();
            mount.teleportAsync(player.getLocation());
        }
    }
}
