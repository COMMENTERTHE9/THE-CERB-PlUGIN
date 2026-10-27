package Listener;


import Manager.CombatConfig;
import Manager.MobHealthScaling;
import Manager.MobRegistry;
import Manager.MobVirtualHealthManager;
import Manager.Tier;
import cerberus.world.cerb.CerberusPlugin;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Enemy;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityRemoveEvent;
import org.bukkit.event.world.EntitiesLoadEvent;

/**
 * Assigns a level and realm-scaled virtual health to mobs, plus their floating label.
 *
 * <p>Two entry points share one scaling path:
 * <ul>
 *   <li>{@link CreatureSpawnEvent} — newly spawned mobs.</li>
 *   <li>{@link EntitiesLoadEvent} — mobs arriving via chunk load. Covers (a) pre-existing
 *       mobs that never scaled, (b) mobs reloading after a chunk unload (label must be
 *       re-attached; the in-memory pool survives, preserving damage), and (c) mobs loading
 *       after a server restart (the transient pool is gone and is re-initialized full).</li>
 * </ul>
 *
 * <p>Level derives from distance to world spawn; health from the {@code mob-health-scaling}
 * realm curve in combat.yml, stored in {@link MobVirtualHealthManager} (the vanilla
 * MAX_HEALTH attribute is cosmetic only — it is hard-capped at 1024 by the game).
 *
 * <p>Folia-safe: CreatureSpawnEvent and EntitiesLoadEvent both fire on the region thread
 * that owns the chunk, so entity mutation here needs no extra scheduling. (Label attach
 * internally defers one tick — entities are not world-committed during CreatureSpawnEvent.)
 */
public class MobScalingListener implements Listener {

    private final CerberusPlugin plugin;

    public MobScalingListener(CerberusPlugin plugin) {
        this.plugin = plugin;
    }

    // MONITOR: we only mutate the entity, never the event — and it means a spawn cancelled
    // by any other plugin (mob stackers/limiters at HIGHEST) never leaks a pool entry.
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onCreatureSpawn(CreatureSpawnEvent event) {
        scaleIfEligible(event.getEntity(), false);
    }

    /** Chunk load — scale/restore every mob the chunk brings in. */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onEntitiesLoad(EntitiesLoadEvent event) {
        for (Entity e : event.getEntities()) {
            if (e instanceof LivingEntity le) {
                scaleIfEligible(le, true);
            }
        }
    }

    /** Public so the plugin's startup sweep can scale mobs in chunks that loaded before enable. */
    public void scaleIfEligible(LivingEntity entity, boolean fromChunkLoad) {
        CombatConfig cc = plugin.getCombatConfig();
        if (cc == null) return;
        MobHealthScaling scaling = cc.mobHealthScaling;
        if (scaling == null || !scaling.isEnabled()) return;

        // Real AI mobs only — never players, armor stands, or display entities.
        if (!(entity instanceof Mob)) return;
        // MONSTERS = hostiles only (Enemy covers slimes/ghasts/phantoms, which Monster misses).
        if (scaling.getApplyTo().equals("MONSTERS") && !(entity instanceof Enemy)) return;

        MobRegistry registry = plugin.getMobRegistry();
        MobVirtualHealthManager mvh = plugin.getMobVirtualHealthManager();
        if (registry == null || mvh == null) return;

        boolean alreadyTagged = registry.hasLevel(entity);
        // Spawn path: skip already-scaled mobs ONLY if their pool is intact. A tagged mob can
        // re-fire CreatureSpawnEvent with its pool gone (e.g. a bee re-emerging from a hive —
        // ENTER_BLOCK removal deleted the pool); fall through to rebuild pool + label.
        if (alreadyTagged && !fromChunkLoad && mvh.has(entity)) return;

        AttributeInstance maxHealthAttr = entity.getAttribute(Attribute.MAX_HEALTH);
        if (maxHealthAttr == null) return;

        // Level: reuse the tag on reloaded mobs; else an explicit spawn request (admin/test
        // spawns, clamped to the configured range); else the normal distance-from-spawn level.
        Integer requested = alreadyTagged ? null : registry.takeRequestedSpawnLevel(entity);
        int level = alreadyTagged ? registry.getLevel(entity)
                  : requested != null ? Math.max(1, Math.min(scaling.getMaxLevel(), requested))
                  : scaling.levelForDistance(distanceToSpawn(entity));
        Tier tier = registry.resolve(entity).tier();
        double maxHealth = scaling.calculate(tier, level) * registry.healthFactor(entity); // a sea creature's ×2 etc.

        if (!alreadyTagged) {
            maxHealthAttr.setBaseValue(MobVirtualHealthManager.COSMETIC_MAX_HEALTH);
            // setHealth to getValue(), not the base constant: vanilla modifiers (e.g. leader-
            // zombie MULTIPLY_TOTAL bonuses) survive setBaseValue, and a health below the
            // final attribute value renders a permanently part-empty bar.
            entity.setHealth(maxHealthAttr.getValue());
            registry.tagLevel(entity, level);
        }

        // (Re-)create the pool only if absent: an in-session chunk reload keeps the damaged
        // pool (HP preserved); after a restart/hive-exit the pool is gone and re-initializes
        // full — sync the cosmetic bar back to full so bar, pool, and label agree.
        if (!mvh.has(entity)) {
            mvh.init(entity, maxHealth);
            entity.setHealth(maxHealthAttr.getValue());
        }

        // Floating label (only generic, un-named mobs — never clobber registry-named specials).
        // attach() is idempotent and defers one tick internally; pass the live fraction so a
        // reloaded damaged mob shows its real HP, not full.
        // Label: generic un-named mobs, plus registry mobs (/cerb spawn, High Health Zombie,
        // Sea Monster), which are named before this runs. Their name moves into the label so it
        // isn't shown twice. Mobs players named with a name tag are never relabelled.
        boolean unnamed = entity.getCustomName() == null || entity.getCustomName().isBlank();
        boolean registryMob = entity.getPersistentDataContainer().has(
                registry.getMobIdKey(), org.bukkit.persistence.PersistentDataType.STRING);
        if (scaling.isShowLevelInName() && (unnamed || registryMob)) {
            if (!unnamed) entity.setCustomNameVisible(false);
            plugin.getMobLabelManager().attach(entity, level, tier, maxHealth, mvh.fraction(entity));
        }
    }

    /** Horizontal distance from the entity to its world's spawn point. */
    private double distanceToSpawn(LivingEntity entity) {
        Location loc = entity.getLocation();
        World world = loc.getWorld();
        if (world == null) return 0.0;
        Location spawn = world.getSpawnLocation();
        double dx = loc.getX() - spawn.getX();
        double dz = loc.getZ() - spawn.getZ();
        return Math.sqrt(dx * dx + dz * dz);
    }

    /**
     * A totem of undying ignores damage magnitude, so a totem-holding mob would survive our
     * "guaranteed lethal" overkill and walk around alive at 0❤. If the pool is depleted, the
     * resurrection is denied — the pool is the source of truth for death.
     */
    @EventHandler(ignoreCancelled = true)
    public void onResurrect(org.bukkit.event.entity.EntityResurrectEvent event) {
        if (event.getEntity() instanceof org.bukkit.entity.Player) return;
        MobVirtualHealthManager mvh = plugin.getMobVirtualHealthManager();
        if (mvh != null && mvh.has(event.getEntity()) && mvh.getCurrent(event.getEntity()) <= 0) {
            event.setCancelled(true);
        }
    }

    /** Death-time cleanup (snappy — the label vanishes with the death, not the corpse). */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDeath(EntityDeathEvent event) {
        MobVirtualHealthManager mvh = plugin.getMobVirtualHealthManager();
        if (mvh != null) mvh.remove(event.getEntity().getUniqueId());
        if (plugin.getMobLabelManager() != null) plugin.getMobLabelManager().remove(event.getEntity());
    }

    /**
     * Pool cleanup for permanent removals (natural despawn, /kill, plugins) — without this
     * the pool map leaks an entry per despawned mob. UNLOAD is deliberately kept: the pool
     * survives a chunk unload so a reloading mob keeps its damaged HP (see onEntitiesLoad).
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onEntityRemove(EntityRemoveEvent event) {
        if (event.getCause() == EntityRemoveEvent.Cause.UNLOAD) return;
        if (!(event.getEntity() instanceof LivingEntity)) return;
        MobVirtualHealthManager mvh = plugin.getMobVirtualHealthManager();
        if (mvh != null) mvh.remove(event.getEntity().getUniqueId());
    }
}
