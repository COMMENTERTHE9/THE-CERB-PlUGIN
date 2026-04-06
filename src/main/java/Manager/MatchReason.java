package Manager;

/**
 * Structured match reason returned by MobRegistry.resolve().
 * Programmatically checkable — no string parsing needed.
 */
public record MatchReason(Type type, String value) {

    public enum Type {
        PDC_TAG,
        CUSTOM_NAME_EXACT,
        CUSTOM_NAME_PATTERN,
        ENTITY_TYPE,
        DEFAULT
    }

    /** Sentinel for unregistered / normal mobs. */
    public static final MatchReason DEFAULT = new MatchReason(Type.DEFAULT, "");

    @Override
    public String toString() {
        return type + (value.isEmpty() ? "" : ": " + value);
    }
}
