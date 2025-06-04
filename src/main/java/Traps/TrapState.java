package Traps;

public enum TrapState {
    NEUTRAL("Initial placement state"),
    ARMED("Ready to trigger"),
    ACTIVATED("Currently activating"),
    TRIGGERED("Effect occurring"),
    RESET("Returning to neutral");

    private final String stateDescription;

    TrapState(String stateDescription) {
        this.stateDescription = stateDescription;
    }

    public String getStateDescription() {
        return stateDescription;
    }
}