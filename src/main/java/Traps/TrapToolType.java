package Traps;

public enum TrapToolType {
    ARMING("Arming Tool", "Used to arm traps", 100),  // 100 = base success rate
    DISARMING("Disarming Tool", "Used to disarm traps", 80),
    RESET("Reset Tool", "Used to reset triggered traps", 90);

    private final String displayName;
    private final String description;
    private final int baseSuccessRate;

    TrapToolType(String displayName, String description, int baseSuccessRate) {
        this.displayName = displayName;
        this.description = description;
        this.baseSuccessRate = baseSuccessRate;
    }

    public String getDisplayName() { return displayName; }
    public String getDescription() { return description; }
    public int getBaseSuccessRate() { return baseSuccessRate; }
}