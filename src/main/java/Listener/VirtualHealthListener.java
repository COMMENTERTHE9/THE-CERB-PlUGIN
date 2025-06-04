package Listener;

import cerberus.world.cerb.CerberusPlugin;
import cerberus.world.cerb.CustomPlayer;
import Manager.PlayerVirtualHealthManager;
import Manager.DamageTypeMapper;
import Manager.CustomDamageType;
import Skills.FirstAidSkill;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityRegainHealthEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

/**
 * Manages virtual health: intercepts damage, prevents vanilla regen,
 * handles custom death/respawn logic, and schedules regeneration.
 */
public class VirtualHealthListener implements Listener {
    private final PlayerVirtualHealthManager virtualHealthManager;
    private final FirstAidSkill firstAidSkill;
    private final CerberusPlugin plugin;
    private boolean isHandlingEvent = false;

    public VirtualHealthListener(PlayerVirtualHealthManager virtualHealthManager,
                                 FirstAidSkill firstAidSkill,
                                 CerberusPlugin plugin) {
        this.virtualHealthManager = virtualHealthManager;
        this.firstAidSkill = firstAidSkill;
        this.plugin = plugin;
    }

    // ------------------------------------------------------------
    // Main damage handler: early exits, priority, and custom flow
    // ------------------------------------------------------------
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onEntityDamage(EntityDamageEvent event) {
        if (event.getDamage() <= 0) return;                       // no damage
        if (!(event.getEntity() instanceof LivingEntity)) return;
        if (isHandlingEvent) return;                              // re‑entrant guard

        isHandlingEvent = true;
        try {
            LivingEntity entity = (LivingEntity) event.getEntity();
            CustomDamageType damageType = DamageTypeMapper.mapToCustomDamageType(event.getCause());
            Entity damager = null;

            if (event instanceof EntityDamageByEntityEvent ede) {
                damager = ede.getDamager();
                if (damager instanceof Projectile proj) {
                    handleProjectileHit(proj, entity, event.getDamage());
                    return;
                } else if (damageType.shouldApplyKnockback()) {
                    applyKnockback(entity, damager, event.getDamage());
                }
            }

            double dmg = event.getDamage();
            if (entity instanceof Player player) {
                dmg *= virtualHealthManager.getDamageMultiplier(player);
                virtualHealthManager.applyDamage(player, dmg, damageType, damager);
                event.setCancelled(true);
                player.setHealth(player.getAttribute(Attribute.GENERIC_MAX_HEALTH).getValue());
            } else {
                dmg = applyDamageMultiplierToEntity(entity, dmg);
                event.setDamage(dmg);
            }
            playDamageAnimation(entity);
        } finally {
            isHandlingEvent = false;
        }
    }

    private double applyDamageMultiplierToEntity(Entity entity, double damage) {
        return damage; // customize if needed
    }

    private void handleProjectileHit(Projectile projectile, Entity target, double originalDamage) {
        double dmg = originalDamage;
        if (target instanceof Player player) {
            dmg *= virtualHealthManager.getDamageMultiplier(player);
            virtualHealthManager.applyDamage(player, dmg, CustomDamageType.PROJECTILE, projectile.getShooter() instanceof Entity e ? e : null);
            player.setHealth(player.getAttribute(Attribute.GENERIC_MAX_HEALTH).getValue());
            playDamageAnimation(player);
        } else if (target instanceof LivingEntity leb) {
            dmg = applyDamageMultiplierToEntity(leb, dmg);
            leb.damage(dmg);
        }
        Vector kb = projectile.getVelocity().normalize().multiply(0.3 * dmg);
        kb.setY(0);
        target.setVelocity(kb);
        playDamageAnimation(target);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEntityRegainHealth(EntityRegainHealthEvent event) {
        if (event.getEntity() instanceof Player) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerDeath(PlayerDeathEvent event) {
        if (isHandlingEvent) return;
        isHandlingEvent = true;
        try {
            Player player = event.getEntity();
            Entity damager = (player.getLastDamageCause() instanceof EntityDamageByEntityEvent ede)
                    ? ede.getDamager() : null;

            if (virtualHealthManager.getPlayerVirtualHealth(player) <= 0) {
                event.setKeepInventory(true);
            }
            virtualHealthManager.applyDamage(
                    player,
                    player.getHealth(),
                    CustomDamageType.ENTITY_ATTACK,
                    damager
            );
            player.setHealth(0);
            player.getWorld().playSound(player.getLocation(), Sound.ENTITY_PLAYER_DEATH, 1.0F, 1.0F);
        } finally {
            isHandlingEvent = false;
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onPlayerRespawn(PlayerRespawnEvent event) {
        scheduleRegen(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onPlayerJoin(PlayerJoinEvent event) {
        scheduleRegen(event.getPlayer());
    }

    // ------------------------------------------------------------
    // Schedule health regen safely on main thread
    // ------------------------------------------------------------
    private void scheduleRegen(Player player) {
        new BukkitRunnable() {
            @Override
            public void run() {
                if (player.isOnline()) {
                    // Use the real API that exists:
                    virtualHealthManager.startHealthUpdateTask(player, plugin.getPlayerHUDManager());
                }
            }
        }.runTask(plugin);
    }

    // ------------------------------------------------------------
    // Knockback utility
    // ------------------------------------------------------------
    private void applyKnockback(Entity entity, Entity damager, double damage) {
        if (!(entity instanceof Player) || damager == null) return;
        Location locE = entity.getLocation();
        Location locD = damager.getLocation();
        Vector dir = locE.toVector().subtract(locD.toVector()).normalize();
        double strength = 0.5 * damage * virtualHealthManager.getKnockbackReductionFactor((Player)entity);
        dir.setY(0);
        entity.setVelocity(dir.multiply(strength));
    }

    private void playDamageAnimation(Entity entity) {
        if (entity instanceof LivingEntity leb) {
            leb.damage(0.1);
            double newHp = Math.min(leb.getHealth() + 0.1, leb.getAttribute(Attribute.GENERIC_MAX_HEALTH).getValue());
            leb.setHealth(newHp);
        }
    }
}
