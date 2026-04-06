package CustomEntities;

import cerberus.world.cerb.CerberusPlugin;
import org.bukkit.Location;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Zombie;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

public class CustomHighHealthZombie {

    /**
     * Spawn with PDC tagging so MobRegistry identifies it even if name changes.
     */
    public static void spawn(Location location, int extraHealth, CerberusPlugin plugin) {
        Zombie zombie = (Zombie) location.getWorld().spawnEntity(location, EntityType.ZOMBIE, CreatureSpawnEvent.SpawnReason.CUSTOM);
        zombie.getAttribute(Attribute.MAX_HEALTH).setBaseValue(1024.0);
        zombie.setHealth(1024.0);

        // Apply Health Boost to extend beyond normal limits
        int healthBoostLevels = extraHealth / 2;
        zombie.addPotionEffect(new PotionEffect(PotionEffectType.HEALTH_BOOST, Integer.MAX_VALUE, healthBoostLevels - 1, false, false));

        // Set the health to the new max value
        zombie.setHealth(zombie.getAttribute(Attribute.MAX_HEALTH).getValue());

        zombie.setCustomName("High Health Zombie");
        zombie.setCustomNameVisible(true);

        // Stamp PDC tag so MobRegistry can identify this mob by ID
        if (plugin != null && plugin.getMobRegistry() != null) {
            plugin.getMobRegistry().tagEntity(zombie, "high_health_zombie");
        }
    }

    /** Legacy overload — no PDC tagging. */
    public static void spawn(Location location, int extraHealth) {
        spawn(location, extraHealth, null);
    }
}
