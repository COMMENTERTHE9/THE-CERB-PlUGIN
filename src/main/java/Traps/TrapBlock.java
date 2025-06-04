package Traps;

import org.bukkit.Material;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.BlockData;

public class TrapBlock {
    private final Material material;
    private final BlockData blockData;
    private final TrapBlockRole role;
    private final BlockFace orientation;
    private boolean isHidden;
    private boolean isPowered;

    public TrapBlock(Material material, BlockData blockData, TrapBlockRole role, BlockFace orientation) {
        this.material = material;
        this.blockData = blockData;
        this.role = role;
        this.orientation = orientation;
        this.isHidden = false;
        this.isPowered = false;
    }

    // Simple constructor for basic blocks
    public TrapBlock(Material material, TrapBlockRole role) {
        this(material, null, role, BlockFace.NORTH);
    }

    // Constructor with block data and role
    public TrapBlock(Material material, BlockData blockData, TrapBlockRole role) {
        this(material, blockData, role, BlockFace.NORTH);
    }

    // Getters
    public Material getMaterial() { return material; }
    public BlockData getBlockData() { return blockData; }
    public TrapBlockRole getRole() { return role; }
    public BlockFace getOrientation() { return orientation; }
    public boolean isHidden() { return isHidden; }
    public boolean isPowered() { return isPowered; }

    // Setters for state
    public void setHidden(boolean hidden) { this.isHidden = hidden; }
    public void setPowered(boolean powered) { this.isPowered = powered; }

    // Clone method for when we need copies
    public TrapBlock clone() {
        TrapBlock clone = new TrapBlock(material, blockData, role, orientation);
        clone.isHidden = this.isHidden;
        clone.isPowered = this.isPowered;
        return clone;
    }
}
