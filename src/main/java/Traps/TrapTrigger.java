package Traps;

import org.bukkit.Location;
import org.bukkit.util.Vector;

public class TrapTrigger {
    private final TriggerType type;
    private final Vector[] triggerZone;  // Array of points making up the zone
    private final boolean chainable;      // Can this trigger activate other triggers
    private final double radius;          // For radius-based triggers
    private final int delay;              // Delay before activation (in ticks)
    private boolean isTriggered;

    public TrapTrigger(TriggerType type, Vector[] triggerZone, boolean chainable, double radius, int delay) {
        this.type = type;
        this.triggerZone = triggerZone;
        this.chainable = chainable;
        this.radius = radius;
        this.delay = delay;
        this.isTriggered = false;
    }

    // Simpler constructor for single-point triggers
    public TrapTrigger(TriggerType type, Vector point, int delay) {
        this(type, new Vector[]{point}, false, 0.5, delay);
    }

    // Getters
    public TriggerType getType() { return type; }
    public Vector[] getTriggerZone() { return triggerZone; }
    public boolean isChainable() { return chainable; }
    public double getRadius() { return radius; }
    public int getDelay() { return delay; }
    public boolean isTriggered() { return isTriggered; }

    // Setter for trigger state
    public void setTriggered(boolean triggered) {
        this.isTriggered = triggered;
    }

    // Utility method to check if a location is within trigger zone
    public boolean isInTriggerZone(Location location) {
        Vector locVector = location.toVector();
        if (type == TriggerType.PROXIMITY) {
            // For proximity triggers, check radius around each point
            for (Vector point : triggerZone) {
                if (locVector.distance(point) <= radius) {
                    return true;
                }
            }
        } else {
            // For other triggers, check exact points
            for (Vector point : triggerZone) {
                if (point.equals(locVector)) {
                    return true;
                }
            }
        }
        return false;
    }
}