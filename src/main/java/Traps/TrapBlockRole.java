// TrapBlockRole.java
package Traps;

public enum TrapBlockRole {
    TRIGGER("Block that activates the trap"),
    MECHANISM("Redstone/mechanical components"),
    STRUCTURAL("Support blocks"),
    EFFECT("Blocks that create the trap effect"),
    DECORATION("Pure aesthetic blocks");

    private final String description;

    TrapBlockRole(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }
}