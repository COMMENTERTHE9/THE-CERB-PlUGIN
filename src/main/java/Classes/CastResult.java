package Classes;

/**
 * What happened when a player pressed an ability key (docs/design/ABILITY_CONTROLS.md, feedback):
 * the caster decides, {@link CastFeedback} shows it. A refused cast costs nothing.
 *
 * @param ability     the ability on that key (null when the key is empty or the player isn't loaded)
 * @param detail      a short extra: "3 hit", "a focus", "8 blocks", "Warrior rank 6", "15"
 * @param remainingMs for {@link Outcome#COOLDOWN}: how long until it is ready
 */
public record CastResult(Outcome outcome, AbilityDef ability, String detail, long remainingMs) {

    public enum Outcome {
        /** It went off. */
        CAST,
        /** Still recovering. */
        COOLDOWN,
        /** Not enough Essence (detail: the cost). */
        NO_ESSENCE,
        /** Not enough of a class resource, a finisher with no Momentum (detail: "1 Momentum"). */
        NO_RESOURCE,
        /** The item in hand doesn't fit (detail: what it needs, "a focus"). */
        WRONG_ITEM,
        /** Nothing to hit within range (detail: the range). */
        NO_TARGET,
        /** No room (a Blink straight into a wall). */
        BLOCKED,
        /** The class rank is too low (detail: "Warrior rank 6"). */
        NOT_UNLOCKED,
        /** Nothing is equipped on this key. */
        EMPTY,
        /** A loadout switch just happened. */
        SWITCHING,
        /** Dead, respawning or a menu open: nothing is shown. */
        CANNOT_ACT,
        /** A configuration problem (the spell it uses is missing). */
        UNAVAILABLE
    }

    public boolean cast() { return outcome == Outcome.CAST; }

    static CastResult cast(AbilityDef a, String detail) { return new CastResult(Outcome.CAST, a, detail, 0); }

    static CastResult refuse(Outcome o, AbilityDef a, String detail) { return new CastResult(o, a, detail, 0); }

    static CastResult cooldown(AbilityDef a, long remainingMs) { return new CastResult(Outcome.COOLDOWN, a, "", remainingMs); }

    static CastResult silent() { return new CastResult(Outcome.CANNOT_ACT, null, "", 0); }
}
