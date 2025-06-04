package Traps;

import org.bukkit.Material;
import org.bukkit.block.BlockFace;
import org.bukkit.util.Vector;

public class TrapSetup {
    private final Material[][][] pattern; // Now 3D array: x, y, z
    private final TrapType trapType;
    private final BlockFace facing;
    private final Vector centerOffset;
    private final int width;
    private final int height;
    private final int length;

    public TrapSetup(TrapType trapType, Material[][][] pattern, BlockFace facing) {
        this.trapType = trapType;
        this.pattern = pattern;
        this.facing = facing;

        // Get dimensions from the 3D pattern
        this.width = pattern.length;
        this.height = pattern[0].length;
        this.length = pattern[0][0].length;

        // Calculate center including height
        this.centerOffset = new Vector(width/2, height/2, length/2);
    }

    // Flexible pattern getters
    public Material[][][] getPattern() {
        return pattern;
    }

    public Material getBlockAt(int x, int y, int z) {
        if (isWithinBounds(x, y, z)) {
            return pattern[x][y][z];
        }
        return null;
    }

    private boolean isWithinBounds(int x, int y, int z) {
        return x >= 0 && x < width &&
                y >= 0 && y < height &&
                z >= 0 && z < length;
    }

    // Dimension getters
    public int getWidth() { return width; }
    public int getHeight() { return height; }
    public int getLength() { return length; }

    // Pattern info
    public TrapType getTrapType() { return trapType; }
    public BlockFace getFacing() { return facing; }
    public Vector getCenterOffset() { return centerOffset.clone(); }

    // Get dimensions as a vector
    public Vector getDimensions() {
        return new Vector(width, height, length);
    }

    // Rotate the entire 3D pattern based on facing
    public Material[][][] getRotatedPattern() {
        if (facing == BlockFace.NORTH) return pattern;

        Material[][][] rotated = new Material[length][height][width];

        switch(facing) {
            case EAST:
                for(int x = 0; x < width; x++) {
                    for(int y = 0; y < height; y++) {
                        for(int z = 0; z < length; z++) {
                            rotated[z][y][width-1-x] = pattern[x][y][z];
                        }
                    }
                }
                break;
            case SOUTH:
                for(int x = 0; x < width; x++) {
                    for(int y = 0; y < height; y++) {
                        for(int z = 0; z < length; z++) {
                            rotated[width-1-x][y][length-1-z] = pattern[x][y][z];
                        }
                    }
                }
                break;
            case WEST:
                for(int x = 0; x < width; x++) {
                    for(int y = 0; y < height; y++) {
                        for(int z = 0; z < length; z++) {
                            rotated[length-1-z][y][x] = pattern[x][y][z];
                        }
                    }
                }
                break;
        }
        return rotated;
    }

    // Validation methods
    public boolean isValidPattern() {
        if (pattern == null || pattern.length == 0) return false;

        // Check for consistent dimensions
        int expectedHeight = pattern[0].length;
        int expectedLength = pattern[0][0].length;

        for (Material[][] slice : pattern) {
            if (slice.length != expectedHeight) return false;
            for (Material[] row : slice) {
                if (row.length != expectedLength) return false;
            }
        }

        return true;
    }

    // Get relative block positions (useful for placing and checking space)
    public Vector[] getRelativeBlockPositions() {
        Vector[] positions = new Vector[width * height * length];
        int index = 0;

        for(int x = 0; x < width; x++) {
            for(int y = 0; y < height; y++) {
                for(int z = 0; z < length; z++) {
                    if(pattern[x][y][z] != null && pattern[x][y][z] != Material.AIR) {
                        positions[index++] = new Vector(x, y, z);
                    }
                }
            }
        }

        return positions;
    }
}