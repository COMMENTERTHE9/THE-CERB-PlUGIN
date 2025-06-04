package cerberus.world.cerb;

import org.bukkit.Location;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public class CerberusWorldProtection {
    private static final List<Region> protectedRegions = new CopyOnWriteArrayList<>();

    public static boolean addProtectedRegion(Region newRegion) {
        // Check for overlaps
        for (Region existingRegion : protectedRegions) {
            if (newRegion.overlaps(existingRegion)) {
                return false; // Overlap detected, don't add the new region
            }
        }
        protectedRegions.add(newRegion);
        return true;
    }

    public static boolean isInProtectedRegion(Location location) {
        for (Region region : protectedRegions) {
            if (region.contains(location)) {
                return true;
            }
        }
        return false;
    }

    public static void clearRegions() {
        protectedRegions.clear();
    }

    public static List<Region> getProtectedRegions() {
        return new ArrayList<>(protectedRegions);
    }

    public static Region removeProtectedRegionAt(Location location) {
        for (Region region : protectedRegions) {
            if (region.contains(location)) {
                if (protectedRegions.remove(region)) {
                    return region;
                }
            }
        }
        return null;
    }
}