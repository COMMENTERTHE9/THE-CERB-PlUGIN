package Attributes;

import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.logging.Level;

/**
 * Loads attributes.yml (copied on first run; missing keys fall back to the jar's defaults).
 * Values are swapped in atomically, so readers on any region thread always see a whole config.
 */
public final class AttributesConfig {

    private final JavaPlugin plugin;
    private volatile KillXp.Params killXp = KillXp.Params.DEFAULTS;

    public AttributesConfig(JavaPlugin plugin) {
        this.plugin = plugin;
        reload();
    }

    public KillXp.Params killXp() { return killXp; }

    public void reload() {
        try {
            File file = new File(plugin.getDataFolder(), "attributes.yml");
            if (!file.exists()) plugin.saveResource("attributes.yml", false);
            YamlConfiguration cfg = YamlConfiguration.loadConfiguration(file);
            try (InputStream in = plugin.getResource("attributes.yml")) {
                if (in != null) cfg.setDefaults(YamlConfiguration.loadConfiguration(new InputStreamReader(in, StandardCharsets.UTF_8)));
            }
            KillXp.Params d = KillXp.Params.DEFAULTS;
            killXp = new KillXp.Params(
                    cfg.getBoolean("kill-xp.enabled", d.enabled()),
                    cfg.getDouble("kill-xp.base", d.base()),
                    cfg.getDouble("kill-xp.level-slope", d.levelSlope()),
                    cfg.getInt("kill-xp.anti-grind.level-gap", d.antiGrindGap()),
                    cfg.getDouble("kill-xp.anti-grind.factor", d.antiGrindFactor()),
                    cfg.getDouble("kill-xp.max-per-kill", d.maxPerKill()));
            plugin.getLogger().info("[Attributes] Loaded attributes.yml (kill XP v2 " + (killXp.enabled() ? "on" : "off")
                    + ", base " + killXp.base() + ", slope " + killXp.levelSlope() + ")");
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "[Attributes] Could not load attributes.yml - keeping previous values", e);
        }
    }
}
