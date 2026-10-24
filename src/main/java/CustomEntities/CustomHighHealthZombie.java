package CustomEntities;

import cerberus.world.cerb.CerberusPlugin;
import org.bukkit.Location;
import org.bukkit.entity.Zombie;
import org.bukkit.event.entity.CreatureSpawnEvent;

public class CustomHighHealthZombie {

    /**
     * Spawn with PDC tagging so MobRegistry identifies it even if name changes.
     *
     * <p>Name and registry tag are applied in the pre-spawn callback, i.e. BEFORE
     * CreatureSpawnEvent fires. MobScalingListener scales on that event; tagging afterwards made
     * it resolve the mob as a plain COMMON zombie (~50 HP instead of its BOSS pool).
     *
     * <p>{@code extraHealth} is kept for command compatibility but no longer changes HP: real
     * mob HP is the realm-scaled virtual pool, and vanilla MAX_HEALTH is cosmetic only.
     */
    public static Zombie spawn(Location location, int extraHealth, CerberusPlugin plugin) {
        return location.getWorld().spawn(location, Zombie.class, CreatureSpawnEvent.SpawnReason.CUSTOM, zombie -> {
            zombie.setCustomName("High Health Zombie");
            zombie.setCustomNameVisible(true);
            if (plugin != null && plugin.getMobRegistry() != null) {
                plugin.getMobRegistry().tagEntity(zombie, "high_health_zombie");
            }
        });
    }

    /** Legacy overload — no PDC tagging. */
    public static Zombie spawn(Location location, int extraHealth) {
        return spawn(location, extraHealth, null);
    }
}
