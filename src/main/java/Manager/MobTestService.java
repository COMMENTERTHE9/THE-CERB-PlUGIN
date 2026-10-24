package Manager;

import cerberus.world.cerb.CerberusPlugin;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.CreatureSpawnEvent;

import java.util.ArrayList;
import java.util.List;

/**
 * Server-side mob tools shared by the Admin Control Panel and {@code /cerb spawn}: spawn a mob
 * at an exact level through the normal registry + scaling path, inspect its pool, set its HP,
 * and clean up test spawns. Nothing here defines mobs or scales them itself:
 * identity comes from {@link MobRegistry}, HP/level from {@code MobScalingListener}.
 *
 * <p>Folia: every method that touches an entity must run on that entity's region thread
 * (spawn: the location's region). Callers in menus run on the admin's thread and act on mobs
 * near the admin, which share that region.
 */
public final class MobTestService {

    /** Mob types whose vanilla behaviour destroys terrain - never spawned from the panel. */
    public static final java.util.Set<EntityType> TERRAIN_DESTROYING =
            java.util.EnumSet.of(EntityType.WITHER, EntityType.ENDER_DRAGON);

    private final CerberusPlugin plugin;

    public MobTestService(CerberusPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * What to spawn. {@code mobId}/{@code customName} may be null for a plain vanilla mob;
     * {@code owner} is the admin a test spawn belongs to (their cleanup removes it).
     */
    public record SpawnRequest(EntityType type, String mobId, String customName, int level,
                               boolean ai, boolean testSpawn, java.util.UUID owner) {}

    /** Read-only view of a mob for display. */
    public record MobInfo(String typeName, String displayName, String mobId, Tier tier, int level,
                          double poolCurrent, double poolMax, boolean pooled, double damageMultiplier,
                          double xpMultiplier, boolean testSpawn, boolean ai, double distance) {
        public double fraction() { return poolMax > 0 ? poolCurrent / poolMax : 1.0; }
    }

    /**
     * Spawn per the request at {@code loc}. Must run on the region thread owning {@code loc}.
     * Name, registry tag, requested level, and test marker are applied before the spawn event,
     * so scaling sees them exactly as it does for any other spawn.
     */
    public LivingEntity spawn(Location loc, SpawnRequest r) {
        Class<? extends Entity> cls = r.type().getEntityClass();
        if (cls == null || !LivingEntity.class.isAssignableFrom(cls)) {
            throw new IllegalArgumentException(r.type() + " is not a living mob");
        }
        MobRegistry registry = plugin.getMobRegistry();
        Entity e = loc.getWorld().spawn(loc, cls, CreatureSpawnEvent.SpawnReason.COMMAND, ent -> {
            LivingEntity le = (LivingEntity) ent;
            if (r.mobId() != null) registry.tagEntity(le, r.mobId());
            if (r.customName() != null) {
                le.setCustomName(r.customName());
                le.setCustomNameVisible(true);
            }
            if (r.level() > 0) registry.requestSpawnLevel(le, r.level());
            if (r.testSpawn()) {
                registry.markTestSpawn(le, r.owner());
                le.setPersistent(false);         // test mobs never get saved into the world
                le.setRemoveWhenFarAway(true);
            }
            if (!r.ai()) le.setAI(false); // "punching bag": vanilla NoAI - no movement, attacks or knockback drift
        });
        return (LivingEntity) e;
    }

    /** Describe a mob for display (call on its region thread). */
    public MobInfo describe(LivingEntity mob, Location viewerLoc) {
        MobRegistry registry = plugin.getMobRegistry();
        MobVirtualHealthManager pools = plugin.getMobVirtualHealthManager();
        MobContext ctx = registry.resolve(mob);
        int level = registry.getLevel(mob);
        CombatConfig cc = plugin.getCombatConfig();
        double dmg = cc != null && cc.mobDamageScaling.isEnabled() ? cc.mobDamageScaling.calculate(ctx.tier(), level) : 1.0;
        boolean pooled = pools.has(mob);
        double max = pooled ? pools.getMax(mob) : attr(mob);
        double cur = pooled ? pools.getCurrent(mob) : mob.getHealth();
        double dist = viewerLoc != null && viewerLoc.getWorld() == mob.getWorld() ? viewerLoc.distance(mob.getLocation()) : -1;
        boolean ai = mob.hasAI();
        String name = mob.getCustomName() != null && !mob.getCustomName().isBlank() ? mob.getCustomName() : null;
        return new MobInfo(mob.getType().name(), name, ctx.mobId(), ctx.tier(), level, cur, max, pooled, dmg,
                ctx.xpMultiplier(), registry.isTestSpawn(mob), ai, dist);
    }

    /** Set the mob's HP to a fraction of max: pool, floating label and health bar together. */
    public void setHealthFraction(LivingEntity mob, double fraction) {
        MobVirtualHealthManager pools = plugin.getMobVirtualHealthManager();
        double f = Math.max(0.01, Math.min(1.0, fraction)); // never 0: dying goes through combat
        if (pools.has(mob)) {
            pools.setFraction(mob, f);
            if (plugin.getMobLabelManager() != null) plugin.getMobLabelManager().refresh(mob, f);
        }
        mob.setHealth(Math.max(1.0, attr(mob) * f));
    }

    /** Toggle "punching bag" mode. Returns the new AI state. */
    public boolean toggleAi(LivingEntity mob) {
        mob.setAI(!mob.hasAI());
        return mob.hasAI();
    }

    /** Remove a mob and its pool/label (no death, no drops, no XP). */
    public void remove(LivingEntity mob) {
        if (plugin.getMobLabelManager() != null) plugin.getMobLabelManager().remove(mob);
        plugin.getMobVirtualHealthManager().remove(mob.getUniqueId());
        mob.remove();
    }

    /**
     * Living mobs (not players, not our label displays) within {@code radius} of {@code player},
     * nearest first, limited to entities owned by the current region thread. Call on the
     * player's region thread.
     */
    public List<LivingEntity> nearbyMobs(Player player, double radius) {
        List<LivingEntity> out = new ArrayList<>();
        for (Entity e : nearbyEntities(player, radius)) {
            // Only entities this thread owns (Folia): safe to read and modify right here.
            if (e instanceof LivingEntity le && !(e instanceof Player) && !(e instanceof org.bukkit.entity.ArmorStand)
                    && Bukkit.isOwnedByCurrentRegion(le) && le.isValid()) {
                out.add(le);
            }
        }
        Location here = player.getLocation();
        out.sort(java.util.Comparator.comparingDouble(le -> le.getLocation().distanceSquared(here)));
        return out;
    }

    /**
     * Folia only lets a region search a box it fully owns. Near a region edge the full box can
     * throw; shrink it until it fits rather than failing the whole lookup.
     */
    private static java.util.Collection<Entity> nearbyEntities(Player player, double radius) {
        for (double r = radius; ; r /= 2) {
            try {
                return player.getNearbyEntities(r, r, r);
            } catch (IllegalStateException e) {
                if (r <= 8) return List.of();
            }
        }
    }

    /** True for a test spawn owned by {@code admin}. */
    public boolean isOwnTestSpawn(Entity e, java.util.UUID admin) {
        return admin.equals(plugin.getMobRegistry().getTestSpawnOwner(e));
    }

    /**
     * Remove this player's own test spawns (only those - not other admins', not natural mobs)
     * within {@code radius}. Returns the count.
     */
    public int cleanupTestSpawns(Player player, double radius) {
        int n = 0;
        for (LivingEntity le : nearbyMobs(player, radius)) {
            if (isOwnTestSpawn(le, player.getUniqueId())) { remove(le); n++; }
        }
        return n;
    }

    /** True when the current thread owns {@code loc} (Folia), so a spawn can run inline. */
    public static boolean ownsLocation(Location loc) {
        return Bukkit.isOwnedByCurrentRegion(loc);
    }

    private static double attr(LivingEntity mob) {
        var a = mob.getAttribute(Attribute.MAX_HEALTH);
        return a != null ? a.getValue() : 20.0;
    }
}
