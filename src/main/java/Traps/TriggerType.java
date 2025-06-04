package Traps;

public enum TriggerType {
    PRESSURE("Activates when stepped on"),
    TRIPWIRE("Activates when line is broken"),
    PROXIMITY("Activates when entity is nearby"),
    REDSTONE("Activates with redstone power"),
    TIMER("Activates after time delay"),
    CHAIN("Activates from another trigger");

    private final String description;

    TriggerType(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }
}