package cerberus.world.cerb;

import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import java.io.File;
import java.util.*;
import java.util.regex.Pattern;

/**
 * Loads skill-trigger patterns from skills.yml and provides lookup.
 */
public class ConfigSkillMapper {
    private final Map<Pattern, String> patternToSkill = new LinkedHashMap<>();

    public ConfigSkillMapper(JavaPlugin plugin) {
        File file = new File(plugin.getDataFolder(), "skills.yml");
        if (!file.exists()) {
            plugin.saveResource("skills.yml", false);
        }
        YamlConfiguration cfg = YamlConfiguration.loadConfiguration(file);

        // Read each skill and its associated patterns
        if (cfg.isConfigurationSection("skill-triggers")) {
            for (String skillName : cfg.getConfigurationSection("skill-triggers").getKeys(false)) {
                List<String> patterns = cfg.getStringList("skill-triggers." + skillName + ".patterns");
                for (String pat : patterns) {
                    // compile each pattern, case-insensitive
                    Pattern regex = Pattern.compile(pat, Pattern.CASE_INSENSITIVE);
                    patternToSkill.put(regex, skillName);
                }
            }
        }
    }

    /**
     * Lookup the skill name for a given material name.
     * Returns empty string if no match.
     */
    public String getSkillForMaterial(String materialName) {
        for (Map.Entry<Pattern, String> entry : patternToSkill.entrySet()) {
            if (entry.getKey().matcher(materialName).matches()) {
                return entry.getValue();
            }
        }
        return "";
    }
}