package Listener;

import Manager.CombatConfig;
import Manager.MobHealthScaling;
import Manager.MobRegistry;
import Manager.Tier;
import cerberus.world.cerb.CerberusPlugin;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Enemy;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityDeathEvent;

/**
 * Assigns a level and tier-scaled max health to mobs as they spawn.
 *
 * <p>Level is derived from the mob's distance to world spawn (farther = higher
 * level); health from the {@code mob-health-scaling} section of combat.yml. The
 * level is stored on the mob via PDC so the combat damage pipeline
 * ({@code VirtualHealthListener.resolveMobLevel}) can read it later.
 *
 * <p>Folia-safe: {@link CreatureSpawnEvent} fires on the region thread that owns
 * the spawning chunk, so mutating the entity here needs no extra scheduling.
 */
public class MobScalingListener implements Listener {

    private final CerberusPlugin plugin;

    public MobScalingListener(CerberusPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onCreatureSpawn(CreatureSpawnEvent event) {
        LivingEntity entity = event.getEntity();

        CombatConfig cc = plugin.getCombatConfig();
        if (cc == null) return;
        MobHealthScaling scaling = cc.mobHealthScaling;
        if (scaling == null || !scaling.isEnabled()) return;

        // apply-to filter (MONSTERS = any hostile mob incl. slimes/ghasts/phantoms via the
        // Enemy marker; ALL = every living mob). Bukkit's Monster excludes Slime/Ghast/etc.,
        // so we use Enemy to catch all hostiles.
        if (scaling.getApplyTo().equals("MONSTERS") && !(entity instanceof Enemy)) return;

        MobRegistry registry = plugin.getMobRegistry();
        if (registry == null) return;

        // Already scaled (defensive — CreatureSpawnEvent normally fires once per mob)
        if (registry.hasLevel(entity)) return;

        AttributeInstance maxHealthAttr = entity.getAttribute(Attribute.MAX_HEALTH);
        if (maxHealthAttr == null) return;

        // 1. Level from horizontal distance to world spawn
        Location loc = entity.getLocation();
        World world = loc.getWorld();
        double distance = 0.0;
        if (world != null) {
            Location spawn = world.getSpawnLocation();
            double dx = loc.getX() - spawn.getX();
            double dz = loc.getZ() - spawn.getZ();
            distance = Math.sqrt(dx * dx + dz * dz);
        }
        int level = scaling.levelForDistance(distance);

        // 2. Tier from the registry (named/tagged specials resolve higher than COMMON)
        Tier tier = registry.resolve(entity).tier();

        // 3. Apply scaled max health into the VIRTUAL pool. Vanilla MAX_HEALTH is hard-capped
        //    at 1024, so the real (possibly billions) HP lives in MobVirtualHealthManager; the
        //    vanilla attribute is set to a cosmetic value only so the health bar still renders.
        double maxHealth = scaling.calculate(tier, level);
        maxHealthAttr.setBaseValue(Manager.MobVirtualHealthManager.COSMETIC_MAX_HEALTH);
        entity.setHealth(Manager.MobVirtualHealthManager.COSMETIC_MAX_HEALTH);
        plugin.getMobVirtualHealthManager().init(entity, maxHealth);

        // 4. Persist the level so combat damage scaling can read it
        registry.tagLevel(entity, level);

        // 5. Floating always-on label (TextDisplay passenger) — replaces the cull-prone
        //    vanilla nameplate. Only generic (un-named) mobs, so we never clobber a name
        //    the registry uses to identify a special mob.
        if (scaling.isShowLevelInName()
                && (entity.getCustomName() == null || entity.getCustomName().isBlank())) {
            plugin.getMobLabelManager().attach(entity, level, tier, maxHealth);
        }
    }

    /** Free the mob's virtual-HP entry when it dies (prevents map growth). */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onDeath(EntityDeathEvent event) {
        Manager.MobVirtualHealthManager mvh = plugin.getMobVirtualHealthManager();
        if (mvh != null) mvh.remove(event.getEntity().getUniqueId());
        if (plugin.getMobLabelManager() != null) plugin.getMobLabelManager().remove(event.getEntity());
    }

    /** Compact number for the name tag: 50, 1.6K, 5.0B. */
    private static String compact(double v) {
        if (v < 1000) return String.valueOf((long) v);
        String[] u = {"K", "M", "B", "T", "Qa"};
        int i = -1;
        double x = v;
        while (x >= 1000 && i < u.length - 1) { x /= 1000; i++; }
        return String.format("%.1f%s", x, u[i]);
    }

    /** ZOMBIE -> "Zombie", CAVE_SPIDER -> "Cave Spider". */
    private String prettify(String typeName) {
        String[] parts = typeName.toLowerCase().split("_");
        StringBuilder sb = new StringBuilder();
        for (String p : parts) {
            if (p.isEmpty()) continue;
            sb.append(Character.toUpperCase(p.charAt(0))).append(p.substring(1)).append(' ');
        }
        return sb.toString().trim();
    }
}
