package Traps;

public enum TrapToolVariant {
    STANDARD("Standard", 1.0, 1.0, 1.0, false, false, 1),
    QUICK("Quick", 2.0, 1.0, 0.8, true, false, 1),      // Faster but noisy and less reliable
    STEALTH("Stealth", 0.5, 1.2, 1.2, false, true, 1),  // Slower but silent and more reliable
    MASS("Mass", 1.0, 0.8, 0.9, true, false, 3),        // Can affect multiple traps but less reliable
    REMOTE("Remote", 1.0, 0.9, 0.9, true, false, 1),    // Works at distance but less reliable
    SAFE("Safe", 0.5, 1.5, 1.5, false, true, 1);        // Very slow but very reliable and safe

    private final String displayName;
    private final double speedMultiplier;      // How fast it works
    private final double successMultiplier;    // Success rate modifier
    private final double safetyMultiplier;     // Resistance to accidents
    private final boolean isNoisy;             // Makes noise when used
    private final boolean isSilent;            // Completely silent
    private final int trapCount;               // How many traps it can affect at once

    TrapToolVariant(String displayName, double speedMult, double successMult,
                    double safetyMult, boolean noisy, boolean silent, int trapCount) {
        this.displayName = displayName;
        this.speedMultiplier = speedMult;
        this.successMultiplier = successMult;
        this.safetyMultiplier = safetyMult;
        this.isNoisy = noisy;
        this.isSilent = silent;
        this.trapCount = trapCount;
    }

    // Getters
    public String getDisplayName() { return displayName; }
    public double getSpeedMultiplier() { return speedMultiplier; }
    public double getSuccessMultiplier() { return successMultiplier; }
    public double getSafetyMultiplier() { return safetyMultiplier; }
    public boolean isNoisy() { return isNoisy; }
    public boolean isSilent() { return isSilent; }
    public int getTrapCount() { return trapCount; }
}