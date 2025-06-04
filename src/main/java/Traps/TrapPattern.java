package Traps;

import cerberus.world.cerb.CerberusPlugin;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Directional;
import org.bukkit.block.data.type.RedstoneWire;
import org.bukkit.block.data.type.TripwireHook;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.List;

public class TrapPattern {
    private final TrapBlock[][][] pattern;
    private final String name;
    private final Vector triggerPoint;
    private final TrapType trapType;
    private final List<Vector> redstonePoints;
    private final List<TrapTrigger> triggers;
    private final List<TriggerChain> triggerChains;
    private final CerberusPlugin plugin;


    public TrapPattern(String name, TrapBlock[][][] pattern, Vector triggerPoint, TrapType trapType,CerberusPlugin plugin) {
        this.name = name;
        this.pattern = pattern;
        this.triggerPoint = triggerPoint;
        this.trapType = trapType;
        this.redstonePoints = findRedstonePoints();
        this.triggers = new ArrayList<>();
        this.triggerChains = new ArrayList<>();
        this.plugin = plugin;
    }

    // Alternative constructor for when using triggers list
    public TrapPattern(String name, TrapBlock[][][] pattern, List<TrapTrigger> triggers, TrapType trapType) {
        this(name, pattern, triggers.isEmpty() ? new Vector(0, 0, 0) : triggers.get(0).getTriggerZone()[0], trapType, null);
        this.triggers.addAll(triggers);
    }

    // Inner class for trigger chains
    private class TriggerChain {
        private final TrapTrigger source;
        private final TrapTrigger target;
        private final int chainDelay;

        public TriggerChain(TrapTrigger source, TrapTrigger target, int chainDelay) {
            this.source = source;
            this.target = target;
            this.chainDelay = chainDelay;
        }
    }

    // Add chain reaction between triggers
    public void addTriggerChain(TrapTrigger source, TrapTrigger target, int delay) {
        if (source.isChainable() && triggers.contains(source) && triggers.contains(target)) {
            triggerChains.add(new TriggerChain(source, target, delay));
        }
    }

    // Pattern creation methods
    public static TrapPattern createBasicSnare() {
        TrapBlock[][][] pattern = new TrapBlock[1][3][3]; // 1 high, 3x3 base

        // Create tripwire hooks with proper facing directions
        TripwireHook eastHook = (TripwireHook) Material.TRIPWIRE_HOOK.createBlockData();
        eastHook.setFacing(BlockFace.EAST);

        TripwireHook westHook = (TripwireHook) Material.TRIPWIRE_HOOK.createBlockData();
        westHook.setFacing(BlockFace.WEST);

        TripwireHook northHook = (TripwireHook) Material.TRIPWIRE_HOOK.createBlockData();
        northHook.setFacing(BlockFace.NORTH);

        TripwireHook southHook = (TripwireHook) Material.TRIPWIRE_HOOK.createBlockData();
        southHook.setFacing(BlockFace.SOUTH);

        // Create redstone wire
        RedstoneWire redstone = (RedstoneWire) Material.REDSTONE_WIRE.createBlockData();

        // Layer 0 (ground level)
        pattern[0] = new TrapBlock[][] {
                {
                        new TrapBlock(Material.TRIPWIRE, TrapBlockRole.TRIGGER),
                        new TrapBlock(Material.TRIPWIRE_HOOK, eastHook, TrapBlockRole.MECHANISM),
                        new TrapBlock(Material.TRIPWIRE, TrapBlockRole.TRIGGER)
                },
                {
                        new TrapBlock(Material.TRIPWIRE_HOOK, southHook, TrapBlockRole.MECHANISM),
                        new TrapBlock(Material.REDSTONE_WIRE, redstone, TrapBlockRole.MECHANISM),
                        new TrapBlock(Material.TRIPWIRE_HOOK, northHook, TrapBlockRole.MECHANISM)
                },
                {
                        new TrapBlock(Material.TRIPWIRE, TrapBlockRole.TRIGGER),
                        new TrapBlock(Material.TRIPWIRE_HOOK, westHook, TrapBlockRole.MECHANISM),
                        new TrapBlock(Material.TRIPWIRE, TrapBlockRole.TRIGGER)
                }
        };

        List<TrapTrigger> triggers = new ArrayList<>();

        // Add primary tripwire trigger
        triggers.add(new TrapTrigger(
                TriggerType.TRIPWIRE,
                new Vector(1, 0, 1),
                0
        ));

        // Add backup pressure plate trigger
        triggers.add(new TrapTrigger(
                TriggerType.PRESSURE,
                new Vector(1, 0, 2),
                10
        ));

        return new TrapPattern("Basic Snare", pattern, triggers, TrapType.BASIC_SNARE);
    }

    public static TrapPattern createPitTrap() {
        TrapBlock[][][] pattern = new TrapBlock[2][3][3]; // 2 high, 3x3 base

        // Create directional pistons
        Directional pistonData = (Directional) Material.STICKY_PISTON.createBlockData();
        pistonData.setFacing(BlockFace.UP);

        // Layer 0 (bottom)
        pattern[0] = new TrapBlock[][] {
                {
                        new TrapBlock(Material.STICKY_PISTON, pistonData, TrapBlockRole.MECHANISM),
                        new TrapBlock(Material.AIR, TrapBlockRole.EFFECT),
                        new TrapBlock(Material.STICKY_PISTON, pistonData, TrapBlockRole.MECHANISM)
                },
                {
                        new TrapBlock(Material.AIR, TrapBlockRole.EFFECT),
                        new TrapBlock(Material.REDSTONE_BLOCK, TrapBlockRole.MECHANISM),
                        new TrapBlock(Material.AIR, TrapBlockRole.EFFECT)
                },
                {
                        new TrapBlock(Material.STICKY_PISTON, pistonData, TrapBlockRole.MECHANISM),
                        new TrapBlock(Material.AIR, TrapBlockRole.EFFECT),
                        new TrapBlock(Material.STICKY_PISTON, pistonData, TrapBlockRole.MECHANISM)
                }
        };

        // Layer 1 (top)
        pattern[1] = new TrapBlock[][] {
                {
                        new TrapBlock(Material.AIR, TrapBlockRole.EFFECT),
                        new TrapBlock(Material.STONE_PRESSURE_PLATE, TrapBlockRole.TRIGGER),
                        new TrapBlock(Material.AIR, TrapBlockRole.EFFECT)
                },
                {
                        new TrapBlock(Material.STONE_PRESSURE_PLATE, TrapBlockRole.TRIGGER),
                        new TrapBlock(Material.STONE_PRESSURE_PLATE, TrapBlockRole.TRIGGER),
                        new TrapBlock(Material.STONE_PRESSURE_PLATE, TrapBlockRole.TRIGGER)
                },
                {
                        new TrapBlock(Material.AIR, TrapBlockRole.EFFECT),
                        new TrapBlock(Material.STONE_PRESSURE_PLATE, TrapBlockRole.TRIGGER),
                        new TrapBlock(Material.AIR, TrapBlockRole.EFFECT)
                }
        };

        List<TrapTrigger> triggers = new ArrayList<>();

        // Add pressure plate triggers
        triggers.add(new TrapTrigger(
                TriggerType.PRESSURE,
                new Vector(1, 1, 1),
                0
        ));

        // Add proximity trigger zone
        Vector[] zone = {
                new Vector(0, 1, 0),
                new Vector(2, 1, 2)
        };
        triggers.add(new TrapTrigger(
                TriggerType.PROXIMITY,
                zone,
                true,
                1.5,
                5
        ));

        return new TrapPattern("Pit Trap", pattern, triggers, TrapType.EXPLOSIVE_TRAP);
    }

    // Getters
    public TrapBlock[][][] getPattern() { return pattern; }
    public String getName() { return name; }
    public Vector getTriggerPoint() { return triggerPoint; }
    public TrapType getTrapType() { return trapType; }
    public List<Vector> getRedstonePoints() { return redstonePoints; }
    public List<TrapTrigger> getTriggers() { return triggers; }
    public List<TriggerChain> getTriggerChains() { return triggerChains; }

    // Utility methods
    private List<Vector> findRedstonePoints() {
        List<Vector> points = new ArrayList<>();
        for (int y = 0; y < pattern.length; y++) {
            for (int x = 0; x < pattern[y].length; x++) {
                for (int z = 0; z < pattern[y][x].length; z++) {
                    TrapBlock block = pattern[y][x][z];
                    if (block != null && block.getRole() == TrapBlockRole.MECHANISM) {
                        points.add(new Vector(x, y, z));
                    }
                }
            }
        }
        return points;
    }

    public static TrapPattern createPattern(TrapType trapType) {
        switch (trapType) {
            case BASIC_SNARE:
                return createBasicSnare();
            case EXPLOSIVE_TRAP:
                return createPitTrap();
            default:
                throw new IllegalArgumentException("Unknown trap type: " + trapType);
        }
    }


    // Method to apply the pattern at a location with given orientation
    public void applyPattern(Location location, BlockFace facing) {
        // Get rotated pattern based on facing direction
        TrapBlock[][][] rotatedPattern = getRotatedPattern(facing);

        // Apply each block in the pattern
        for (int y = 0; y < pattern.length; y++) {
            for (int x = 0; x < pattern[y].length; x++) {
                for (int z = 0; z < pattern[y][x].length; z++) {
                    TrapBlock trapBlock = rotatedPattern[y][x][z];
                    if (trapBlock != null) {
                        Location blockLoc = location.clone().add(x, y, z);
                        Block block = blockLoc.getBlock();

                        // Set the block type
                        block.setType(trapBlock.getMaterial());

                        // If there's block data, apply it
                        if (trapBlock.getBlockData() != null) {
                            block.setBlockData(trapBlock.getBlockData());
                        }
                    }
                }
            }
        }
    }

    public void restoreBlocks(Location baseLocation, BlockFace facing) {
        // Get rotated pattern based on facing direction
        TrapBlock[][][] rotatedPattern = getRotatedPattern(facing);

        // Create restoration effect sequence
        new BukkitRunnable() {
            int y = pattern.length - 1;  // Start from top

            @Override
            public void run() {
                if (y < 0) {
                    this.cancel();
                    playCompletionEffect(baseLocation);
                    return;
                }

                // Restore layer with effects
                restoreLayer(baseLocation, y);
                y--;
            }
        }.runTaskTimer(plugin, 0L, 5L);  // Run every 5 ticks (0.25 seconds)
    }

    private void restoreLayer(Location baseLocation, int y) {
        World world = baseLocation.getWorld();

        for (int x = 0; x < pattern[y].length; x++) {
            for (int z = 0; z < pattern[y][x].length; z++) {
                Location blockLoc = baseLocation.clone().add(x, y, z);

                // Remove block
                blockLoc.getBlock().setType(Material.AIR);

                Location particleLoc = blockLoc.clone().add(0.5, 0.5, 0.5);

                // Multiple particle effects
                world.spawnParticle(Particle.CLOUD, particleLoc, 5, 0.2, 0.2, 0.2, 0.05);
                world.spawnParticle(Particle.WITCH, particleLoc, 3, 0.2, 0.2, 0.2, 0);
                world.spawnParticle(Particle.CRIT, particleLoc, 4, 0.2, 0.2, 0.2, 0.1);
                world.spawnParticle(Particle.ENCHANT, particleLoc, 10, 0.3, 0.3, 0.3, 1);

                // Sound effect
                world.playSound(blockLoc, Sound.BLOCK_STONE_BREAK, 0.3f, 1.2f);
            }
        }
    }

    private void playCompletionEffect(Location baseLocation) {
        World world = baseLocation.getWorld();
        Location center = baseLocation.clone().add(pattern[0].length/2.0, 0, pattern[0][0].length/2.0);

        // Create spiral effect
        for(double y = 0; y < 3; y += 0.1) {
            double radius = (3.0 - y) * 0.3;
            for(double angle = 0; angle < Math.PI * 2; angle += Math.PI/16) {
                double x = Math.cos(angle) * radius;
                double z = Math.sin(angle) * radius;
                Location particleLoc = center.clone().add(x, y, z);

                // Helix of particles
                world.spawnParticle(Particle.END_ROD, particleLoc, 1, 0, 0, 0, 0);
                world.spawnParticle(Particle.WHITE_SMOKE, particleLoc, 1, 0, 0, 0, 0);
            }
        }

        // Central burst
        world.spawnParticle(Particle.EXPLOSION_EMITTER, center, 1, 0, 0, 0, 0);
        world.spawnParticle(Particle.FLASH, center, 1, 0, 0, 0, 0);
        world.spawnParticle(Particle.WITCH, center, 50, 1, 1, 1, 0.1);

        // Sounds
        world.playSound(center, Sound.BLOCK_BEACON_DEACTIVATE, 1.0f, 1.5f);
        world.playSound(center, Sound.ENTITY_ILLUSIONER_DEATH, 0.5f, 1.2f);
    }

    // Helper method to rotate the pattern based on facing
    private TrapBlock[][][] getRotatedPattern(BlockFace facing) {
        if (facing == BlockFace.NORTH) return pattern;

        int width = pattern[0].length;
        int height = pattern.length;
        int length = pattern[0][0].length;
        TrapBlock[][][] rotated = new TrapBlock[height][width][length];

        switch (facing) {
            case EAST:
                for (int y = 0; y < height; y++) {
                    for (int x = 0; x < width; x++) {
                        for (int z = 0; z < length; z++) {
                            rotated[y][z][width-1-x] = rotateBlock(pattern[y][x][z], facing);
                        }
                    }
                }
                break;
            case SOUTH:
                for (int y = 0; y < height; y++) {
                    for (int x = 0; x < width; x++) {
                        for (int z = 0; z < length; z++) {
                            rotated[y][width-1-x][length-1-z] = rotateBlock(pattern[y][x][z], facing);
                        }
                    }
                }
                break;
            case WEST:
                for (int y = 0; y < height; y++) {
                    for (int x = 0; x < width; x++) {
                        for (int z = 0; z < length; z++) {
                            rotated[y][length-1-z][x] = rotateBlock(pattern[y][x][z], facing);
                        }
                    }
                }
                break;
        }
        return rotated;
    }

    // Helper method to rotate block data if needed
    private TrapBlock rotateBlock(TrapBlock block, BlockFace facing) {
        if (block == null || block.getBlockData() == null) return block;

        BlockData rotatedData = block.getBlockData().clone();
        if (rotatedData instanceof Directional) {
            BlockFace originalFace = ((Directional) rotatedData).getFacing();
            BlockFace newFace = rotateBlockFace(originalFace, facing);
            ((Directional) rotatedData).setFacing(newFace);
            return new TrapBlock(block.getMaterial(), rotatedData, block.getRole());
        }

        return block;
    }

    // Helper method to rotate block faces
    private BlockFace rotateBlockFace(BlockFace original, BlockFace rotation) {
        if (original == BlockFace.UP || original == BlockFace.DOWN) {
            return original;
        }

        int rotations = 0;
        switch (rotation) {
            case EAST:
                rotations = 1;
                break;
            case SOUTH:
                rotations = 2;
                break;
            case WEST:
                rotations = 3;
                break;
        }

        BlockFace[] faces = {BlockFace.NORTH, BlockFace.EAST, BlockFace.SOUTH, BlockFace.WEST};
        int currentIndex = 0;
        for (int i = 0; i < faces.length; i++) {
            if (faces[i] == original) {
                currentIndex = i;
                break;
            }
        }

        return faces[(currentIndex + rotations) % 4];
    }

    // Get dimensions
    public Vector getDimensions() {
        return new Vector(
                pattern[0].length,    // width
                pattern.length,       // height
                pattern[0][0].length  // length
        );
    }

    // Validate pattern
    public boolean isValid() {
        if (pattern == null || pattern.length == 0) return false;

        int width = pattern[0].length;
        int length = pattern[0][0].length;

        // Check consistency of dimensions
        for (TrapBlock[][] layer : pattern) {
            if (layer.length != width) return false;
            for (TrapBlock[] row : layer) {
                if (row.length != length) return false;
            }
        }

        // Validate triggers
        if (triggers.isEmpty()) return false;

        // Check if all trigger positions are within pattern bounds
        Vector dimensions = getDimensions();
        for (TrapTrigger trigger : triggers) {
            for (Vector point : trigger.getTriggerZone()) {
                if (point.getX() >= dimensions.getX() ||
                        point.getY() >= dimensions.getY() ||
                        point.getZ() >= dimensions.getZ() ||
                        point.getX() < 0 || point.getY() < 0 || point.getZ() < 0) {
                    return false;
                }
            }
        }



        return true;
    }
}