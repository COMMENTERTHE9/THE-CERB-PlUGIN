package Traps;

import Manager.CustomDamageType;
import org.bukkit.Material;

public enum TrapType {
    BASIC_SNARE(
            CustomDamageType.PHYSICAL_TRAP,
            Material.TRIPWIRE_HOOK,
            "Basic Snare",
            5.0,  // Base damage
            2.0   // Trigger radius
    ),

    EXPLOSIVE_TRAP(
            CustomDamageType.PHYSICAL_TRAP,
            Material.TNT,
            "Explosive Trap",
            15.0, // Base damage
            3.0   // Trigger radius
    );

    private final CustomDamageType damageType;
    private final Material material;
    private final String displayName;
    private final double baseDamage;
    private final double triggerRadius;

    TrapType(
            CustomDamageType damageType,
            Material material,
            String displayName,
            double baseDamage,
            double triggerRadius
    ) {
        this.damageType = damageType;
        this.material = material;
        this.displayName = displayName;
        this.baseDamage = baseDamage;
        this.triggerRadius = triggerRadius;
    }

    // Getters
    public CustomDamageType getDamageType() { return damageType; }
    public Material getMaterial() { return material; }
    public String getDisplayName() { return displayName; }
    public double getBaseDamage() { return baseDamage; }
    public double getTriggerRadius() { return triggerRadius; }

    // Helper method to get TrapType from Material
    public static TrapType fromMaterial(Material material) {
        for (TrapType type : values()) {
            if (type.getMaterial() == material) {
                return type;
            }
        }
        return null;
    }
}