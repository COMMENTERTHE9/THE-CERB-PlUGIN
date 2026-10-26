package Attributes;

import java.util.Locale;

/**
 * What a gathering bonus counts on (docs/design/SKILL_PROGRESSION.md, "Fortune, scoped by resource"): Mining's Fortune
 * on ore, Woodcutting's on wood. Fortune with no yield (luck skills, later talismans) counts on everything.
 */
public enum Yield {
    ORE("ore"),
    WOOD("wood");

    /** As written in the config files and shown to players: "Fortune (wood)". */
    public final String id;

    Yield(String id) { this.id = id; }

    /**
     * What a skill's own Fortune counts on when a config line doesn't say: Mining's on ore, Woodcutting's on wood, any
     * other skill's on everything (null). So an attributes.yml or resources.yml from before scoping keeps Mining's Fortune
     * on ore; "all" (yield) or "general" (fortune) asks for everything outright.
     */
    public static Yield defaultFor(String skill) {
        if ("Mining".equals(skill)) return ORE;
        if ("Woodcutting".equals(skill)) return WOOD;
        return null;
    }

    /** "ore" / "WOOD" → the yield; null when blank, "all" or unknown. */
    public static Yield parse(String s) {
        if (s == null || s.isBlank()) return null;
        String k = s.trim().toLowerCase(Locale.ROOT);
        for (Yield y : values()) if (y.id.equals(k)) return y;
        return null;
    }
}
