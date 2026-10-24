package Skills;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * What players read for each skill. The internal name (database key, config key, command
 * argument, XP routing) never changes: a rename only changes text, so no level or XP is touched.
 *
 * <p>Four names were polished in Player Menu 2.0; every other skill keeps its name. A server can
 * override any name in skills.yml ({@code skill-gui.<internal name>.display-name}).
 */
public final class SkillNames {

    /** Internal name -> player-facing name, for the skills that read better under a new name. */
    public static final Map<String, String> DEFAULT_RENAMES = Map.of(
            "Heavy Armor Training", "Heavy Armor Mastery",
            "First Aid", "Field Medicine",
            "Mana Regeneration", "Essence Regeneration",
            "Navigation", "Pathfinding");

    private static volatile Map<String, String> renames = DEFAULT_RENAMES;

    private SkillNames() {}

    /** The name players see ("Navigation" -> "Pathfinding"; unrenamed skills keep their name). */
    public static String display(String internalName) {
        if (internalName == null) return "";
        return renames.getOrDefault(internalName, internalName);
    }

    /** The old name when the skill is shown under a new one (for "formerly ..."), else null. */
    public static String formerName(String internalName) {
        return internalName != null && renames.containsKey(internalName) ? internalName : null;
    }

    /**
     * The internal name for a player-facing name ("pathfinding", "Field_Medicine"), ignoring case,
     * spaces and underscores; null when no renamed skill has that name.
     */
    public static String internalForDisplay(String input) {
        if (input == null) return null;
        String want = normalize(input);
        for (Map.Entry<String, String> e : renames.entrySet()) {
            if (normalize(e.getValue()).equals(want)) return e.getKey();
        }
        return null;
    }

    /** Replace the rename table (skills.yml load/reload). Blank names are ignored. */
    public static void setRenames(Map<String, String> table) {
        Map<String, String> clean = new HashMap<>();
        table.forEach((k, v) -> {
            if (k != null && v != null && !v.isBlank() && !v.equals(k)) clean.put(k, v.trim());
        });
        renames = Map.copyOf(clean);
    }

    public static String normalize(String s) {
        return s.replace("_", "").replace(" ", "").toLowerCase(Locale.ROOT);
    }
}
