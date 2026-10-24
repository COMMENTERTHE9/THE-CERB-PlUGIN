package cerberus.world.cerb;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.*;
import java.util.logging.Logger;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * Config-driven skill trigger routing.
 * <p>
 * Loads {@code skill-triggers} from {@code skills.yml} with structured matching:
 * <ul>
 *   <li>{@code materials:} — exact material name list (O(1) HashSet lookup)</li>
 *   <li>{@code material-regex:} — regex patterns against Material.name()</li>
 *   <li>{@code priority:} — lower number matched first, deterministic ordering</li>
 * </ul>
 * At same priority, exact materials are checked before regex patterns.
 * First match wins.
 */
public class ConfigSkillMapper {

    private static final Logger log = Logger.getLogger("Cerberus");

    private final JavaPlugin plugin;

    /** Sorted trigger list — rebuilt atomically on reload. */
    private volatile List<SkillTrigger> triggers = List.of();

    /** Magic config */
    private volatile boolean awardKillXpForSpellFocus = true;
    private volatile Set<String> magicSkillNames = Set.of();

    // ── Internal record ──────────────────────────────────────────

    record SkillTrigger(String skillName, int priority,
                        Set<String> exactMaterials, List<Pattern> regexPatterns) {

        /** Check if a material name matches this trigger. Exact first, then regex. */
        boolean matches(String materialName) {
            if (exactMaterials.contains(materialName)) return true;
            for (Pattern p : regexPatterns) {
                if (p.matcher(materialName).matches()) return true;
            }
            return false;
        }

        String matchType(String materialName) {
            if (exactMaterials.contains(materialName)) return "materials[" + materialName + "]";
            for (Pattern p : regexPatterns) {
                if (p.matcher(materialName).matches()) return "regex " + p.pattern();
            }
            return "?";
        }
    }

    // ── Constructor + reload ─────────────────────────────────────

    public ConfigSkillMapper(JavaPlugin plugin) {
        this.plugin = plugin;
        reload();
    }

    /**
     * (Re)load all trigger rules from skills.yml.
     * Atomic swap — if parsing fails, old config stays active.
     */
    public void reload() {
        File file = new File(plugin.getDataFolder(), "skills.yml");
        if (!file.exists()) {
            plugin.saveResource("skills.yml", false);
        }

        YamlConfiguration cfg;
        try {
            cfg = YamlConfiguration.loadConfiguration(file);
        } catch (Exception e) {
            log.severe("[SkillMapper] Failed to parse skills.yml — keeping old config: " + e.getMessage());
            return;
        }

        List<SkillTrigger> newTriggers = new ArrayList<>();
        int exactOnlyCount = 0;
        int regexCount = 0;
        Set<Integer> seenPriorities = new HashSet<>();

        ConfigurationSection triggersSection = cfg.getConfigurationSection("skill-triggers");
        if (triggersSection != null) {
            for (String skillName : triggersSection.getKeys(false)) {
                ConfigurationSection entry = triggersSection.getConfigurationSection(skillName);
                if (entry == null) continue;

                int priority = entry.getInt("priority", 50);

                if (seenPriorities.contains(priority)) {
                    log.warning("[SkillMapper] Duplicate priority " + priority
                            + " for '" + skillName + "' — may produce ambiguous results");
                }
                seenPriorities.add(priority);

                // Parse match section
                ConfigurationSection matchSection = entry.getConfigurationSection("match");
                Set<String> exactMaterials = new HashSet<>();
                List<Pattern> regexPatterns = new ArrayList<>();

                if (matchSection != null) {
                    // Exact materials
                    List<String> mats = matchSection.getStringList("materials");
                    for (String mat : mats) {
                        exactMaterials.add(mat.toUpperCase());
                        // Validate (warning only — future materials are OK)
                        try {
                            Material.valueOf(mat.toUpperCase());
                        } catch (IllegalArgumentException e) {
                            log.warning("[SkillMapper] Unknown material '" + mat
                                    + "' in " + skillName + " — may be a future material");
                        }
                    }

                    // Regex patterns
                    List<String> regexes = matchSection.getStringList("material-regex");
                    for (String regex : regexes) {
                        try {
                            regexPatterns.add(Pattern.compile(regex, Pattern.CASE_INSENSITIVE));
                        } catch (PatternSyntaxException e) {
                            log.severe("[SkillMapper] Invalid regex '" + regex
                                    + "' in " + skillName + " — skipped: " + e.getMessage());
                        }
                    }
                }

                if (exactMaterials.isEmpty() && regexPatterns.isEmpty()) {
                    log.warning("[SkillMapper] Skill '" + skillName + "' has no match rules — skipped");
                    continue;
                }

                newTriggers.add(new SkillTrigger(skillName, priority, exactMaterials, regexPatterns));

                if (regexPatterns.isEmpty()) exactOnlyCount++;
                else regexCount++;
            }
        }

        // Sort by priority (lower first)
        newTriggers.sort(Comparator.comparingInt(SkillTrigger::priority));

        // Atomic swap
        this.triggers = List.copyOf(newTriggers);

        // Load magic config
        this.awardKillXpForSpellFocus = cfg.getBoolean("magic.award-kill-xp-for-spell-focus", true);
        List<String> magicList = cfg.getStringList("magic.magic-skills");
        this.magicSkillNames = new HashSet<>(magicList);

        log.info("[SkillMapper] Loaded " + triggers.size() + " trigger rules ("
                + exactOnlyCount + " exact-only, " + regexCount + " with regex)");
    }

    // ── Lookup ───────────────────────────────────────────────────

    /**
     * Resolve which skill a material name maps to.
     * Returns empty string if no match.
     */
    public String resolve(String materialName) {
        for (SkillTrigger trigger : triggers) {
            if (trigger.matches(materialName)) {
                return trigger.skillName;
            }
        }
        return "";
    }

    /**
     * Resolve with debug info — returns "SkillName (matchType, priority=N)" or empty.
     */
    public String resolveDebug(String materialName) {
        for (SkillTrigger trigger : triggers) {
            if (trigger.matches(materialName)) {
                return trigger.skillName + " (" + trigger.matchType(materialName)
                        + ", priority=" + trigger.priority + ")";
            }
        }
        return "";
    }

    /** Whether kill XP should be awarded for spell focus items. */
    public boolean shouldAwardKillXpForSpellFocus() {
        return awardKillXpForSpellFocus;
    }

    /** Whether a skill name is considered a magic skill. */
    public boolean isMagicSkill(String skillName) {
        return magicSkillNames.contains(skillName);
    }

    /**
     * What a player must hold for a kill to train {@code skillName}, in words ("any sword",
     * "mace, trident, any axe"), or "" when kills never train it (no rule, or kill XP for spell
     * focus items turned off). Read from the same rules {@link #resolve} uses.
     */
    public String describeKillItems(String skillName) {
        if (isMagicSkill(skillName) && !awardKillXpForSpellFocus) return "";
        for (SkillTrigger t : triggers) {
            if (t.skillName().equals(skillName)) {
                List<String> regex = new ArrayList<>();
                for (Pattern p : t.regexPatterns()) regex.add(p.pattern());
                return describeItems(new TreeSet<>(t.exactMaterials()), regex);
            }
        }
        return "";
    }

    /** Pure: material names and name patterns as words. Exact names first, then patterns. */
    static String describeItems(Collection<String> materials, List<String> regexes) {
        List<String> parts = new ArrayList<>();
        for (String m : materials) {
            parts.add(m.equals("AIR") ? "an empty hand" : m.toLowerCase(Locale.ROOT).replace('_', ' '));
        }
        for (String r : regexes) {
            java.util.regex.Matcher suffix = Pattern.compile("^\\.\\*_([A-Za-z]+)$").matcher(r);
            java.util.regex.Matcher contains = Pattern.compile("^\\.\\*([A-Za-z]+)\\.\\*$").matcher(r);
            if (suffix.matches()) parts.add("any " + suffix.group(1).toLowerCase(Locale.ROOT));
            else if (contains.matches()) parts.add("any " + contains.group(1).toLowerCase(Locale.ROOT));
            else parts.add("items matching " + r);
        }
        return String.join(", ", parts);
    }
}
