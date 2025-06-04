package cerberus.world.cerb;

import org.bukkit.Location;

public class Region {
    private final Location min;
    private final Location max;

    public Region(Location loc1, Location loc2) {
        this.min = new Location(loc1.getWorld(),
                Math.min(loc1.getX(), loc2.getX()),
                Math.min(loc1.getY(), loc2.getY()),
                Math.min(loc1.getZ(), loc2.getZ()));
        this.max = new Location(loc1.getWorld(),
                Math.max(loc1.getX(), loc2.getX()),
                Math.max(loc1.getY(), loc2.getY()),
                Math.max(loc1.getZ(), loc2.getZ()));
    }

    public boolean contains(Location location) {
        return location.getX() >= min.getX() && location.getX() <= max.getX() &&
                location.getY() >= min.getY() && location.getY() <= max.getY() &&
                location.getZ() >= min.getZ() && location.getZ() <= max.getZ();
    }

    public boolean overlaps(Region other) {
        return this.min.getX() <= other.max.getX() && this.max.getX() >= other.min.getX() &&
                this.min.getY() <= other.max.getY() && this.max.getY() >= other.min.getY() &&
                this.min.getZ() <= other.max.getZ() && this.max.getZ() >= other.min.getZ();
    }

    public Location getMin() {
        return min.clone();
    }

    public Location getMax() {
        return max.clone();
    }
}