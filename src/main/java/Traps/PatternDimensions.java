package Traps;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.util.Vector;
import cerberus.world.cerb.CerberusWorldProtection;

public class PatternDimensions {
    private final int width;
    private final int height;
    private final int length;
    private final boolean isSymmetrical;
    private final BlockFace[] validOrientations;

    public PatternDimensions(int width, int height, int length, boolean isSymmetrical, BlockFace[] validOrientations) {
        this.width = width;
        this.height = height;
        this.length = length;
        this.isSymmetrical = isSymmetrical;
        this.validOrientations = validOrientations;
    }

    // Get dimensions as vector
    public Vector getDimensions() {
        return new Vector(width, height, length);
    }

    // Check if pattern can be rotated to this orientation
    public boolean canRotateTo(BlockFace orientation) {
        if (isSymmetrical) return true;

        for (BlockFace validFace : validOrientations) {
            if (validFace == orientation) return true;
        }
        return false;
    }

    // Check if there's enough space at location
    public boolean hasRequiredSpace(Location location, BlockFace facing) {
        // Get rotated dimensions based on facing
        Vector rotatedDims = getRotatedDimensions(facing);

        // Check each block in the space
        for (int x = 0; x < rotatedDims.getX(); x++) {
            for (int y = 0; y < rotatedDims.getY(); y++) {
                for (int z = 0; z < rotatedDims.getZ(); z++) {
                    Location checkLoc = location.clone().add(x, y, z);
                    if (CerberusWorldProtection.isInProtectedRegion(checkLoc)) {
                        return false;
                    }
                }
            }
        }
        return true;
    }

    // Get dimensions after rotation
    public Vector getRotatedDimensions(BlockFace facing) {
        if (isSymmetrical) return getDimensions();

        switch (facing) {
            case NORTH:
            case SOUTH:
                return new Vector(width, height, length);
            case EAST:
            case WEST:
                return new Vector(length, height, width);
            default:
                return getDimensions();
        }
    }

    // Check if the pattern fits in slopes
    public boolean fitsInSlope(Location location, BlockFace facing) {
        Vector rotatedDims = getRotatedDimensions(facing);
        Location baseLocation = location.clone();

        // Check ground level variation
        int maxHeightDiff = 1; // Maximum allowed height difference

        for (int x = 0; x < rotatedDims.getX(); x++) {
            for (int z = 0; z < rotatedDims.getZ(); z++) {
                Location checkLoc = baseLocation.clone().add(x, 0, z);
                int heightDiff = getGroundHeightDifference(checkLoc);

                if (heightDiff > maxHeightDiff) {
                    return false;
                }
            }
        }
        return true;
    }

    // Get height difference at a location
    private int getGroundHeightDifference(Location location) {
        int highest = 0;
        int lowest = 0;
        boolean foundGround = false;

        for (int y = 0; y < height; y++) {
            Block block = location.clone().add(0, y, 0).getBlock();
            if (block.getType() != Material.AIR) {
                if (!foundGround) {
                    highest = lowest = y;
                    foundGround = true;
                } else {
                    highest = y;
                }
            }
        }

        return highest - lowest;
    }

    // Validate the pattern can be placed here
    public boolean validatePlacement(Location location, BlockFace facing) {
        // First check if any part would be in protected region
        if (!hasRequiredSpace(location, facing)) return false;

        // Then check slope compatibility if needed
        if (!fitsInSlope(location, facing)) return false;

        // Check for required supporting blocks if needed
        if (!hasRequiredSupport(location, facing)) return false;

        return true;
    }

    // Check if pattern has required supporting blocks
    private boolean hasRequiredSupport(Location location, BlockFace facing) {
        Vector rotatedDims = getRotatedDimensions(facing);

        // Check bottom layer has support
        for (int x = 0; x < rotatedDims.getX(); x++) {
            for (int z = 0; z < rotatedDims.getZ(); z++) {
                Location bottomLoc = location.clone().add(x, -1, z);
                if (bottomLoc.getBlock().getType() == Material.AIR) {
                    return false;
                }
            }
        }

        return true;
    }

    // Getters
    public int getWidth() { return width; }
    public int getHeight() { return height; }
    public int getLength() { return length; }
    public boolean isSymmetrical() { return isSymmetrical; }
    public BlockFace[] getValidOrientations() { return validOrientations; }
}