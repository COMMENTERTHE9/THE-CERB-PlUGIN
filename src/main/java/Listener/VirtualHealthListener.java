package Listener;

import Manager.PlayerVirtualHealthManager;
import Manager.DamageTypeMapper;
import Manager.CustomDamageType;
import Skills.FirstAidSkill;
import cerberus.world.cerb.CustomPlayer;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityRegainHealthEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

public class VirtualHealthListener implements Listener {

    private final PlayerVirtualHealthManager virtualHealthManager;
    private final FirstAidSkill firstAidSkill;
    private boolean isHandlingEvent = false;

    public VirtualHealthListener(PlayerVirtualHealthManager virtualHealthManager, FirstAidSkill firstAidSkill) {
        this.virtualHealthManager = virtualHealthManager;
        this.firstAidSkill = firstAidSkill;
    }

    @EventHandler
    public void onEntityDamage(EntityDamageEvent event) {
        if (isHandlingEvent) return;
        isHandlingEvent = true;

        try {
            Entity entity = event.getEntity();
            CustomDamageType damageType = DamageTypeMapper.mapToCustomDamageType(event.getCause());
            Entity damagerEntity = null;

            if (event instanceof EntityDamageByEntityEvent) {
                EntityDamageByEntityEvent entityEvent = (EntityDamageByEntityEvent) event;
                damagerEntity = entityEvent.getDamager();

                if (damagerEntity instanceof Projectile) {
                    handleProjectileHit((Projectile) damagerEntity, entity, event.getDamage());
                    return;
                } else if (shouldApplyKnockback(damageType)) {
                    applyKnockback(entity, damagerEntity, event.getDamage());
                }
            }



            double damage = event.getDamage();

            if (entity instanceof Player) {
                Player player = (Player) entity;

                // Apply the damage multiplier based on the player's Virtual Health
                double damageMultiplier = virtualHealthManager.getDamageMultiplier(player);
                damage *= damageMultiplier;

                // Apply the damage to Virtual Health
                virtualHealthManager.applyDamage(player, damage, damageType, damagerEntity);
                // Cancel event damage to prevent affecting real health
                event.setCancelled(true);
                // Reset player's health to max (real health should not decrease)
                player.setHealth(player.getAttribute(Attribute.GENERIC_MAX_HEALTH).getValue());
            } else if (entity instanceof LivingEntity) {
                // Apply damage multiplier for non-player entities as well
                damage = applyDamageMultiplierToEntity(entity, damage);
                event.setDamage(damage);
            }

            playDamageAnimation(entity);
        } finally {
            isHandlingEvent = false;
        }
    }

    private double applyDamageMultiplierToEntity(Entity entity, double damage) {
        // Implement any additional logic to modify damage for non-player entities if needed
        return damage; // Default: return unmodified damage
    }

    private void handleProjectileHit(Projectile projectile, Entity target, double originalDamage) {
        double damage = projectile.getVelocity().length(); // Or use originalDamage if preferred
        Entity damager = projectile.getShooter() instanceof Entity ? (Entity) projectile.getShooter() : null;

        if (target instanceof Player) {
            Player player = (Player) target;

            // Apply the damage multiplier based on the player's Virtual Health
            double damageMultiplier = virtualHealthManager.getDamageMultiplier(player);
            damage *= damageMultiplier;

            // Apply the damage to the virtual health instead of normal health
            virtualHealthManager.applyDamage(player, damage, CustomDamageType.PROJECTILE, damager);

            // Set the event damage to zero to prevent normal health from being affected
            player.setHealth(player.getAttribute(Attribute.GENERIC_MAX_HEALTH).getValue());

            // Apply horizontal knockback based on the projectile's velocity
            Vector knockback = projectile.getVelocity().normalize().multiply(0.3 * damage);
            knockback.setY(0); // Keep the knockback horizontal
            player.setVelocity(knockback);

            // Trigger the damage animation (red flash effect)
            playDamageAnimation(player);
        } else if (target instanceof LivingEntity) {
            LivingEntity livingEntity = (LivingEntity) target;
            // Apply damage directly to the mob's real health with multiplier
            damage = applyDamageMultiplierToEntity(livingEntity, damage);
            livingEntity.damage(damage);
        }

        // Apply horizontal knockback based on the projectile's velocity
        Vector knockback = projectile.getVelocity().normalize().multiply(0.3 * damage);
        knockback.setY(0); // Keep the knockback horizontal
        target.setVelocity(knockback);

        // Trigger the damage animation (red flash effect)
        playDamageAnimation(target);
    }

    @EventHandler
    public void onEntityRegainHealth(EntityRegainHealthEvent event) {
        event.setCancelled(true); // Cancel any natural regeneration
    }

    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {
        if (isHandlingEvent) return;
        isHandlingEvent = true;

        try {
            Player player = event.getEntity();
            EntityDamageEvent lastDamageCause = player.getLastDamageCause();
            Entity damagerEntity = null;

            if (lastDamageCause instanceof EntityDamageByEntityEvent) {
                damagerEntity = ((EntityDamageByEntityEvent) lastDamageCause).getDamager();
            }

            double currentVirtualHealth = virtualHealthManager.getPlayerVirtualHealth(player);
            if (currentVirtualHealth <= 0) {
                event.setKeepInventory(true);
            }

            virtualHealthManager.applyDamage(player, player.getHealth(), CustomDamageType.ENTITY_ATTACK, damagerEntity);

            player.setHealth(0);
            player.getWorld().playSound(player.getLocation(), "entity.player.death", 1.0F, 1.0F);
        } finally {
            isHandlingEvent = false;
        }
    }

    @EventHandler
    public void onPlayerRespawn(PlayerRespawnEvent event) {
        startHealthRegenTask(event.getPlayer());
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        startHealthRegenTask(event.getPlayer());
    }

    private void applyKnockback(Entity entity, Entity damager, double damage) {
        if (damager == null || entity == null) return;

        Location entityLocation = entity.getLocation();
        Location damagerLocation = damager.getLocation();

        Vector direction = entityLocation.toVector().subtract(damagerLocation.toVector()).normalize();

        double knockbackStrength = 0.5 * damage;

        // Apply knockback reduction if the entity is a player
        if (entity instanceof Player) {
            Player player = (Player) entity;
            double knockbackReductionFactor = virtualHealthManager.getKnockbackReductionFactor(player);
            knockbackStrength *= knockbackReductionFactor;
        }

        Vector knockback = direction.multiply(knockbackStrength);
        knockback.setY(0);

        entity.setVelocity(knockback);
    }

    private boolean shouldApplyKnockback(CustomDamageType damageType) {
        switch (damageType) {
            // Cases where knockback should be applied:
            case ENTITY_ATTACK:
            case ENTITY_SWEEP_ATTACK:
            case PROJECTILE:
            case ENTITY_EXPLOSION:
            case BLOCK_EXPLOSION:
            case THORNS:
            case SONIC_BOOM:
            case CUSTOM_EXPLOSION:
                return true;

            // Cases where knockback should not be applied:
            case FALL:
            case FIRE:
            case FIRE_TICK:
            case LAVA:
            case DROWNING:
            case SUFFOCATION:
            case STARVATION:
            case POISON:
            case MAGIC:
            case WITHER:
            case FALLING_BLOCK:
            case LIGHTNING:
            case HOT_FLOOR:
            case CRAMMING:
            case DRAGON_BREATH:
            case DRYOUT:
            case FREEZE:
            case VOID:
            case FLY_INTO_WALL:
            case WORLD_BORDER:
            case CONTACT:
            case MELTING:
            case CAMPFIRE:
            case SUICIDE:
            case CUSTOM_MAGIC:
            case CUSTOM_ENVIRONMENTAL:
                return false;

            default:
                return false;
        }
    }

    private void playDamageAnimation(Entity entity) {
        if (entity instanceof LivingEntity) {
            LivingEntity livingEntity = (LivingEntity) entity;
            livingEntity.damage(0.1);
            livingEntity.setHealth(Math.min(livingEntity.getHealth() + 0.1, livingEntity.getAttribute(Attribute.GENERIC_MAX_HEALTH).getValue()));
        }
    }

    private void startHealthRegenTask(Player player) {
        new BukkitRunnable() {
            long lastDamageTime = System.currentTimeMillis();

            @Override
            public void run() {
                if (!player.isOnline()) {
                    this.cancel();
                    return;
                }

                double currentHealth = virtualHealthManager.getPlayerVirtualHealth(player);
                double maxHealth = virtualHealthManager.getPlayerMaxVirtualHealth(player);

                if (currentHealth >= maxHealth) {
                    return;
                }

                long timeSinceLastDamage = System.currentTimeMillis() - lastDamageTime;
                double regenRate = timeSinceLastDamage > 10000 ? 1.0 : 0.2;

                // Integrate FirstAidSkill regeneration boost
                CustomPlayer customPlayer = CustomPlayer.getCustomPlayer(player);
                if (customPlayer != null) {
                    firstAidSkill.applyEffect(customPlayer);
                }

                virtualHealthManager.increasePlayerVirtualHealth(player, regenRate);

                if (timeSinceLastDamage <= 10000) {
                    lastDamageTime = System.currentTimeMillis();
                }
            }
        }.runTaskTimerAsynchronously(Bukkit.getPluginManager().getPlugin("CerberusPlugin"), 0L, 20L);
    }
}
