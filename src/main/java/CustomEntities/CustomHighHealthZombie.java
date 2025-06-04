package CustomEntities;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Zombie;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

public class CustomHighHealthZombie {

    public static void spawn(Location location, int extraHealth) {
        Zombie zombie = (Zombie) location.getWorld().spawnEntity(location, EntityType.ZOMBIE, CreatureSpawnEvent.SpawnReason.CUSTOM);
        zombie.getAttribute(Attribute.GENERIC_MAX_HEALTH).setBaseValue(1024.0); // Base health
        zombie.setHealth(1024.0);

        // Apply Health Boost to extend beyond normal limits
        int healthBoostLevels = extraHealth / 2;
        zombie.addPotionEffect(new PotionEffect(PotionEffectType.HEALTH_BOOST, Integer.MAX_VALUE, healthBoostLevels - 1, false, false));

        // Set the health to the new max value
        zombie.setHealth(zombie.getAttribute(Attribute.GENERIC_MAX_HEALTH).getValue());

        // Optionally, set a custom name
        zombie.setCustomName("High Health Zombie");
        zombie.setCustomNameVisible(true);
    }
}
