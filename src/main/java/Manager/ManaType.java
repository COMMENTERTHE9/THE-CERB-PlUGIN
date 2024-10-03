package Manager;

public enum ManaType {
    BASIC("Basic Mana"),
    SUMMONING("Summoning Energy"),
    SPELL_WEAVING("Spell Weaving Power"),
    DEFENSIVE("Defensive Magic Power");

    private final String displayName;

    ManaType(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}
