package Manager;

import cerberus.world.cerb.cerb;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class PlayerVirtualHealthManager {
    private final Map<UUID, Double> playerVirtualHealth = new HashMap<>();
    private final Map<UUID, Double> playerMaxVirtualHealth = new HashMap<>();
    private final Map<UUID, Double> playerDamageReduction = new HashMap<>();
    private final Map<UUID, Double> playerKnockbackReductionFactor = new HashMap<>();
    private final Map<UUID, Double> playerToughness = new HashMap<>();
    private final Map<UUID, Double> playerResistance = new HashMap<>();
    private final Map<UUID, Double> playerFireResistance = new HashMap<>();
    private final Map<UUID, Double> playerColdResistance = new HashMap<>();
    private final Map<UUID, Double> playerDrowningResistance = new HashMap<>();
    private final Map<UUID, Long> playerLastDamageTime = new HashMap<>();
    private final long invincibilityTimeMillis = 250;  // Invincibility frames after taking damage
    private final long respawnInvincibilityTimeMillis = 3000;  // Respawn invincibility duration

    private boolean isHandlingEvent = false;
    private final cerb plugin;

    public PlayerVirtualHealthManager(cerb plugin) {
        this.plugin = plugin;
    }

    // Player health management
    public void setPlayerVirtualHealth(Player player, double health) {
        UUID playerId = player.getUniqueId();
        double maxHealth = playerMaxVirtualHealth.getOrDefault(playerId, 100.0);
        if (health > maxHealth) {
            health = maxHealth;
        } else if (health < 0) {
            health = 0;
        }
        playerVirtualHealth.put(playerId, health);
        checkPlayerDeath(player, health);
    }

    public double getPlayerVirtualHealth(Player player) {
        return playerVirtualHealth.getOrDefault(player.getUniqueId(), 100.0);
    }

    public void setPlayerMaxVirtualHealth(Player player, double maxHealth) {
        UUID playerId = player.getUniqueId();
        playerMaxVirtualHealth.put(playerId, maxHealth);
        if (getPlayerVirtualHealth(player) > maxHealth) {
            setPlayerVirtualHealth(player, maxHealth);
        }
    }

    public double getPlayerMaxVirtualHealth(Player player) {
        return playerMaxVirtualHealth.getOrDefault(player.getUniqueId(), 100.0);
    }

    public void increasePlayerVirtualHealth(Player player, double amount) {
        setPlayerVirtualHealth(player, getPlayerVirtualHealth(player) + amount);
    }

    public void decreasePlayerVirtualHealth(Player player, double amount) {
        setPlayerVirtualHealth(player, getPlayerVirtualHealth(player) - amount);
    }

    public void increaseMaxHealth(Player player, double amount) {
        UUID playerId = player.getUniqueId();
        double currentMaxHealth = getPlayerMaxVirtualHealth(player);
        setPlayerMaxVirtualHealth(player, currentMaxHealth + amount);
    }

    public void setMaxHealth(Player player, double maxHealth) {
        setPlayerMaxVirtualHealth(player, maxHealth);
    }

    private void checkPlayerDeath(Player player, double health) {
        if (health <= 0) {
            player.setHealth(0);  // Kill the player
            Bukkit.getScheduler().runTask(plugin, () -> player.spigot().respawn());
        }
    }

    // Set and get player's toughness (damage buffer)
    public void setPlayerToughness(Player player, double toughness) {
        playerToughness.put(player.getUniqueId(), toughness);
    }

    public double getPlayerToughness(Player player) {
        return playerToughness.getOrDefault(player.getUniqueId(), 0.0);
    }

    // Set and get player's resistance (damage reduction)
    public void setPlayerResistance(Player player, double resistance) {
        playerResistance.put(player.getUniqueId(), resistance);
    }

    public double getPlayerResistance(Player player) {
        return playerResistance.getOrDefault(player.getUniqueId(), 0.0);
    }

    // Fire, Cold, and Drowning resistances
    public double getPlayerFireResistance(Player player) {
        return playerFireResistance.getOrDefault(player.getUniqueId(), 0.0);
    }

    public double getPlayerColdResistance(Player player) {
        return playerColdResistance.getOrDefault(player.getUniqueId(), 0.0);
    }

    public double getPlayerDrowningResistance(Player player) {
        return playerDrowningResistance.getOrDefault(player.getUniqueId(), 0.0);
    }

    // Invincibility frames and respawn invincibility
    public void applyRespawnInvincibility(Player player) {
        playerLastDamageTime.put(player.getUniqueId(), System.currentTimeMillis() - invincibilityTimeMillis + respawnInvincibilityTimeMillis);
        player.sendMessage("You are invincible for a few seconds after respawning!");
    }

    // Method to apply damage with toughness, resistances, and invincibility frames
    public void applyDamage(Entity entity, double damage, CustomDamageType damageType, Entity damager) {
        if (isHandlingEvent) {
            return; // Prevent recursion
        }
        if (entity instanceof Player) {
            Player player = (Player) entity;

            long currentTime = System.currentTimeMillis();
            if (playerLastDamageTime.containsKey(player.getUniqueId())) {
                long lastDamageTime = playerLastDamageTime.get(player.getUniqueId());
                if (currentTime - lastDamageTime < invincibilityTimeMillis) {
                    return; // Apply invincibility frames
                }
            }

            playerLastDamageTime.put(player.getUniqueId(), currentTime);

            // Apply toughness (damage buffer)
            double toughness = getPlayerToughness(player);
            if (toughness > 0) {
                double absorbed = Math.min(toughness, damage);
                damage -= absorbed;
                setPlayerToughness(player, toughness - absorbed);
            }

            // Apply resistance (percentage-based damage reduction)
            double resistance = getPlayerResistance(player);
            damage *= (1.0 - resistance);

            // Apply environmental resistances
            if (damageType == CustomDamageType.FIRE) {
                double fireResistance = getPlayerFireResistance(player);
                damage *= (1.0 - fireResistance);
            } else if (damageType == CustomDamageType.FREEZE) {
                double coldResistance = getPlayerColdResistance(player);
                damage *= (1.0 - coldResistance);
            } else if (damageType == CustomDamageType.DROWNING) {
                double drowningResistance = getPlayerDrowningResistance(player);
                damage *= (1.0 - drowningResistance);
            }

            // Apply virtual health damage reduction
            double damageReduction = playerDamageReduction.getOrDefault(player.getUniqueId(), 0.0);
            damage *= (1.0 - damageReduction);

            // Decrease player's virtual health
            decreasePlayerVirtualHealth(player, damage);

            // Apply velocity after damage
            applyVelocity(entity, damager, damage, damageType);
        }
    }

    // Helper method to apply velocity after damage
    private void applyVelocity(Entity entity, Entity damager, double damage, CustomDamageType damageType) {
        if (damager == null || entity == null) return;

        double velocityStrength = 0.5 * damage;
        int velocityModifier = 0;

        if (damager instanceof Player) {
            Player playerDamager = (Player) damager;
            if (playerDamager.isSprinting()) {
                velocityModifier++;
            }
        }

        velocityStrength += velocityModifier * 0.5;
        velocityStrength = Math.min(velocityStrength, 2.0);  // Cap velocity strength

        Location entityLocation = entity.getLocation();
        Location damagerLocation = damager.getLocation();
        Vector velocityDirection = entityLocation.toVector().subtract(damagerLocation.toVector()).normalize();

        if (entity instanceof Player) {
            Player player = (Player) entity;
            double knockbackReduction = playerKnockbackReductionFactor.getOrDefault(player.getUniqueId(), 1.0);
            velocityStrength *= knockbackReduction;
        }

        Vector appliedVelocity = velocityDirection.multiply(velocityStrength);
        appliedVelocity.setY(0.1);  // Add small vertical lift

        if (!damageType.shouldApplyKnockback()) {
            appliedVelocity.multiply(0.1);  // Dampen velocity
        }

        entity.setVelocity(appliedVelocity);
    }

    // Method to handle projectiles and their effect
    public void handleProjectileHit(Projectile projectile, Entity target) {
        if (target instanceof Player || target instanceof Entity) {
            double damage = projectile.getVelocity().length();  // Use projectile speed as damage

            // Apply damage and velocity
            applyDamage(target, damage, CustomDamageType.PROJECTILE, projectile.getShooter() instanceof Entity ? (Entity) projectile.getShooter() : null);
        }
    }

    // Method to handle periodic health regeneration with reduced regen while taking damage
    public void applyHealthRegen(Player player, double normalRegenRate, double reducedRegenRate, long duration, long damageTimeout) {
        new BukkitRunnable() {
            long timeElapsed = 0;
            long lastDamageTime = System.currentTimeMillis();

            @Override
            public void run() {
                if (timeElapsed >= duration || player.isDead()) {
                    this.cancel();
                    return;
                }

                double currentHealth = getPlayerVirtualHealth(player);
                double maxHealth = getPlayerMaxVirtualHealth(player);

                long timeSinceLastDamage = System.currentTimeMillis() - lastDamageTime;
                double regenRate = timeSinceLastDamage > damageTimeout ? normalRegenRate : reducedRegenRate;

                if (currentHealth < maxHealth) {
                    increasePlayerVirtualHealth(player, regenRate);
                }

                timeElapsed += 20;  // 1 second (20 ticks)
            }

            @Override
            public void cancel() {
                super.cancel();
            }

        }.runTaskTimerAsynchronously(plugin, 0L, 20L);
    }

    // Overloaded method to handle regeneration with fewer parameters
    public void applyHealthRegen(Player player, double regenRate, long duration) {
        applyHealthRegen(player, regenRate, regenRate / 2, duration, 5000L);  // Default damageTimeout is 5 seconds
    }

    // Method to start the health update task for a player
    public void startHealthUpdateTask(Player player, PlayerHUDManager hudManager) {
        new BukkitRunnable() {
            @Override
            public void run() {
                if (!player.isOnline()) {
                    this.cancel();
                    return;
                }
                hudManager.updateHUD(player);
            }
        }.runTaskTimer(plugin, 0, 20);  // Runs every 20 ticks (1 second)
    }

    // Method to check if velocity should be applied based on CustomDamageType
    private boolean shouldApplyVelocity(CustomDamageType damageType) {
        return damageType.shouldApplyKnockback();
    }

    // Method to set the damage reduction for a player
    public void reduceVirtualHealthDamage(Player player, double reduction) {
        playerDamageReduction.put(player.getUniqueId(), reduction);
    }

    // Method to get the damage reduction for a player
    public double getDamageReduction(Player player) {
        return playerDamageReduction.getOrDefault(player.getUniqueId(), 0.0);
    }

    // Method to set the knockback reduction for a player
    public void setKnockbackReductionFactor(Player player, double reduction) {
        playerKnockbackReductionFactor.put(player.getUniqueId(), reduction);
    }

    // Method to get the knockback reduction for a player
    public double getKnockbackReductionFactor(Player player) {
        return playerKnockbackReductionFactor.getOrDefault(player.getUniqueId(), 1.0);
    }

    // Method to get the max health of a player
    public double getMaxHealth(Player player) {
        // Check if the player is in the map
        UUID playerUUID = player.getUniqueId();
        return playerMaxVirtualHealth.getOrDefault(playerUUID, 20.0); // Default to 20.0 if not found (default Minecraft health)
    }

    // Method to get the damage multiplier for a player
    public double getDamageMultiplier(Player player) {
        // Assume some default base multiplier (e.g., 1.0 means no change)
        double baseMultiplier = 1.0;

        UUID playerUUID = player.getUniqueId();

        // Apply virtual health damage reduction multiplier (if any)
        double damageReduction = getDamageReduction(player); // Use the existing method for damage reduction
        double damageMultiplier = baseMultiplier * (1.0 - damageReduction); // Apply reduction

        // Apply any other custom multipliers here (e.g., based on toughness, buffs, etc.)
        // For now, let's assume we only use the damage reduction multiplier
        return damageMultiplier;
    }
}
