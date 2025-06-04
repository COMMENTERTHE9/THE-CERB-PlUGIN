package Manager;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

/**
 * Manages throttled, asynchronous saves for both YAML configs and DB tasks,
 * ensuring at most one save per key within the configured cooldown window.
 */
public class AsyncSaveManager {
    private final JavaPlugin plugin;
    private final long cooldownMs;
    private final Map<String, Long> lastRunTimestamps = new HashMap<>();

    /**
     * @param plugin     your main plugin instance
     * @param cooldownMs minimum milliseconds between saves for the same key
     */
    public AsyncSaveManager(JavaPlugin plugin, long cooldownMs) {
        this.plugin     = plugin;
        this.cooldownMs = cooldownMs;
    }

    /**
     * Schedule a throttled YAML save.
     *
     * @param key  logical identifier for this config (e.g. "playerData", "regions")
     * @param cfg  the FileConfiguration to save
     * @param file the destination File
     */
    public void scheduleYamlSave(String key, FileConfiguration cfg, File file) {
        long now = System.currentTimeMillis();
        long last = lastRunTimestamps.getOrDefault(key, 0L);
        if (now - last < cooldownMs) return;

        lastRunTimestamps.put(key, now);
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                cfg.save(file);
            } catch (Exception ex) {
                plugin.getLogger().severe("Failed to save '" + key + "': " + ex.getMessage());
                ex.printStackTrace();
            }
        });
    }

    /**
     * Schedule a throttled database‐related task.
     *
     * @param key    logical identifier (e.g. "dbBackup", "skillSave")
     * @param dbTask the Runnable that performs the DB work
     */
    public void scheduleDbSave(String key, Runnable dbTask) {
        long now = System.currentTimeMillis();
        long last = lastRunTimestamps.getOrDefault(key, 0L);
        if (now - last < cooldownMs) return;

        lastRunTimestamps.put(key, now);
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, dbTask);
    }
}

