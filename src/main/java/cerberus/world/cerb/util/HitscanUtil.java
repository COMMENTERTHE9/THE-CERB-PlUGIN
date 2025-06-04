package cerberus.world.cerb.util;

import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

import java.util.function.Predicate;

public class HitscanUtil {
    /**
     * Ray–traces from the player's eye in their look direction up to maxRange,
     * returning the first entity matching filter, or null if none.
     */
    public static RayTraceResult rayTraceEntity(Player player, double maxRange, Predicate<Entity> filter) {
        Location eye = player.getEyeLocation();
        Vector dir   = eye.getDirection().normalize();
        return player.getWorld().rayTraceEntities(
                eye, dir, maxRange,
                filter
        );
    }
}
