package Manager;

import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages throttled, asynchronous saves for both YAML configs and DB tasks,
 * ensuring at most one save per key within the configured cooldown window.
 */
public class AsyncSaveManager {
    private final JavaPlugin plugin;
    private final long cooldownMs;
    private final Map<String, Long> lastRunTimestamps = new ConcurrentHashMap<>();

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
        if (!markSaveQueued(key)) return;

        Bukkit.getAsyncScheduler().runNow(plugin, task -> {
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
        if (!markSaveQueued(key)) return;

        Bukkit.getAsyncScheduler().runNow(plugin, task -> {
            try {
                dbTask.run();
            } catch (Exception ex) {
                plugin.getLogger().severe("Failed to run DB save '" + key + "': " + ex.getMessage());
                ex.printStackTrace();
            }
        });
    }

    private boolean markSaveQueued(String key) {
        long now = System.currentTimeMillis();
        boolean[] queued = {false};

        lastRunTimestamps.compute(key, (ignored, last) -> {
            if (last != null && now - last < cooldownMs) {
                return last;
            }
            queued[0] = true;
            return now;
        });

        return queued[0];
    }
}

