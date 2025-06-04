package Manager;

import cerberus.world.cerb.CerberusPlugin;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.attribute.Attribute;
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

    // Customize these values if needed
    private final long invincibilityTimeMillis = 250;          // Invincibility frames after taking damage
    private final long respawnInvincibilityTimeMillis = 3000;  // Respawn invincibility duration

    private boolean isHandlingEvent = false;
    private final CerberusPlugin plugin;
    private DefenseBarManager defenseBarManager;

    // Updated constructor to use CerberusPlugin instead of cerb
    public PlayerVirtualHealthManager(CerberusPlugin plugin) {
        this.plugin = plugin;
    }
    
    // Setter for DefenseBarManager (called after initialization)
    public void setDefenseBarManager(DefenseBarManager defenseBarManager) {
        this.defenseBarManager = defenseBarManager;
    }

    public void setPlayerVirtualHealth(Player player, double health) {
        UUID playerId = player.getUniqueId();
        double maxHealth = getPlayerMaxVirtualHealth(player);
        if (health > maxHealth) {
            health = maxHealth;
        } else if (health < 0) {
            health = 0;
        }
        playerVirtualHealth.put(playerId, health);
        checkPlayerDeath(player, health);
    }

    // --------------------------------------------------
    // <<< NEW >>> Full restore of both real & virtual HP
    // --------------------------------------------------
    public void resetHealth(Player player) {
        // Virtual HP → max
        UUID id = player.getUniqueId();
        double maxVH = getPlayerMaxVirtualHealth(player);
        setPlayerVirtualHealth(player, maxVH);

        // Real HP → max
        double maxReal = player.getAttribute(Attribute.GENERIC_MAX_HEALTH).getValue();
        player.setHealth(maxReal);
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
        double currentMaxHealth = getPlayerMaxVirtualHealth(player);
        setPlayerMaxVirtualHealth(player, currentMaxHealth + amount);
    }

    public void setMaxHealth(Player player, double maxHealth) {
        setPlayerMaxVirtualHealth(player, maxHealth);
    }

    private void checkPlayerDeath(Player player, double health) {
        if (health <= 0) {
            player.setHealth(0);  // This kills the player in-game.
            Bukkit.getScheduler().runTask(plugin, () -> player.spigot().respawn());
        }
    }

    public void setPlayerToughness(Player player, double toughness) {
        playerToughness.put(player.getUniqueId(), toughness);
    }

    public double getPlayerToughness(Player player) {
        return playerToughness.getOrDefault(player.getUniqueId(), 0.0);
    }

    public void setPlayerResistance(Player player, double resistance) {
        playerResistance.put(player.getUniqueId(), resistance);
    }

    public double getPlayerResistance(Player player) {
        return playerResistance.getOrDefault(player.getUniqueId(), 0.0);
    }

    public double getPlayerFireResistance(Player player) {
        return playerFireResistance.getOrDefault(player.getUniqueId(), 0.0);
    }

    public double getPlayerColdResistance(Player player) {
        return playerColdResistance.getOrDefault(player.getUniqueId(), 0.0);
    }

    public double getPlayerDrowningResistance(Player player) {
        return playerDrowningResistance.getOrDefault(player.getUniqueId(), 0.0);
    }

    public void applyRespawnInvincibility(Player player) {
        playerLastDamageTime.put(player.getUniqueId(), System.currentTimeMillis() - invincibilityTimeMillis + respawnInvincibilityTimeMillis);
        player.sendMessage("You are invincible for a few seconds after respawning!");
    }

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
                    // Invincibility frames
                    return;
                }
            }

            playerLastDamageTime.put(player.getUniqueId(), currentTime);

            // Apply defense bar first (if available)
            if (defenseBarManager != null) {
                damage = defenseBarManager.applyDamageToBar(player, damage);
                // If defense bar absorbed all damage, we're done
                if (damage <= 0) {
                    return;
                }
            }
            
            // Apply toughness first
            double toughness = getPlayerToughness(player);
            if (toughness > 0) {
                double absorbed = Math.min(toughness, damage);
                damage -= absorbed;
                setPlayerToughness(player, toughness - absorbed);
            }

            // Apply resistances
            double resistance = getPlayerResistance(player);
            damage *= (1.0 - resistance);

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

            // Apply final damage to virtual health
            decreasePlayerVirtualHealth(player, damage);

            // Apply knockback/velocity
            applyVelocity(entity, damager, damage, damageType);
        }
    }

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
        velocityStrength = Math.min(velocityStrength, 2.0);  // Cap velocity

        Location entityLocation = entity.getLocation();
        Location damagerLocation = damager.getLocation();
        Vector velocityDirection = entityLocation.toVector().subtract(damagerLocation.toVector()).normalize();

        if (entity instanceof Player) {
            Player player = (Player) entity;
            double knockbackReduction = playerKnockbackReductionFactor.getOrDefault(player.getUniqueId(), 1.0);
            velocityStrength *= knockbackReduction;
        }

        Vector appliedVelocity = velocityDirection.multiply(velocityStrength);
        appliedVelocity.setY(0.1);  // Add a small vertical component

        if (!damageType.shouldApplyKnockback()) {
            appliedVelocity.multiply(0.1);  // Reduce knockback if damageType shouldn't apply full knockback
        }

        entity.setVelocity(appliedVelocity);
    }

    public void handleProjectileHit(Projectile projectile, Entity target) {
        if (target instanceof Player || target instanceof Entity) {
            double damage = projectile.getVelocity().length();  // Projectile speed as damage
            Entity shooter = projectile.getShooter() instanceof Entity ? (Entity) projectile.getShooter() : null;
            applyDamage(target, damage, CustomDamageType.PROJECTILE, shooter);
        }
    }

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

                timeElapsed += 20;  // Increase by 20 ticks (1 second)
            }
        }.runTaskTimerAsynchronously(plugin, 0L, 20L);
    }

    public void applyHealthRegen(Player player, double regenRate, long duration) {
        applyHealthRegen(player, regenRate, regenRate / 2, duration, 5000L);
    }

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
        }.runTaskTimer(plugin, 0, 20);
    }

    public void reduceVirtualHealthDamage(Player player, double reduction) {
        playerDamageReduction.put(player.getUniqueId(), reduction);
    }

    public double getDamageReduction(Player player) {
        return playerDamageReduction.getOrDefault(player.getUniqueId(), 0.0);
    }

    public void setKnockbackReductionFactor(Player player, double reduction) {
        playerKnockbackReductionFactor.put(player.getUniqueId(), reduction);
    }

    public double getKnockbackReductionFactor(Player player) {
        return playerKnockbackReductionFactor.getOrDefault(player.getUniqueId(), 1.0);
    }

    public double getMaxHealth(Player player) {
        return getPlayerMaxVirtualHealth(player); // Use the defined method to get max health
    }

    public double getDamageMultiplier(Player player) {
        double baseMultiplier = 1.0;
        double damageReduction = getDamageReduction(player);
        return baseMultiplier * (1.0 - damageReduction);
    }
}
