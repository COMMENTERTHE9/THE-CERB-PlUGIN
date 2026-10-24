package Manager;

import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * Manages throttled, asynchronous saves for both YAML configs and DB tasks: at most one save
 * per key per cooldown window, and the newest request inside a window still runs at its end.
 */
public class AsyncSaveManager {
    private final JavaPlugin plugin;
    private final SaveThrottle throttle;
    /** Newest not-yet-run task per key; a scheduled flush runs whatever is here. */
    private final Map<String, Runnable> pending = new ConcurrentHashMap<>();

    /**
     * @param plugin     your main plugin instance
     * @param cooldownMs minimum milliseconds between saves for the same key
     */
    public AsyncSaveManager(JavaPlugin plugin, long cooldownMs) {
        this.plugin   = plugin;
        this.throttle = new SaveThrottle(cooldownMs);
    }

    /**
     * Schedule a throttled YAML save.
     *
     * @param key  logical identifier for this config (e.g. "playerData", "regions")
     * @param cfg  the FileConfiguration to save
     * @param file the destination File
     */
    public void scheduleYamlSave(String key, FileConfiguration cfg, File file) {
        submit(key, () -> {
            try {
                synchronized (cfg) { cfg.save(file); }
            } catch (Exception ex) {
                plugin.getLogger().severe("Failed to save '" + key + "': " + ex.getMessage());
                ex.printStackTrace();
            }
        });
    }

    /**
     * Schedule a throttled database-related task. Inside the cooldown the newest task for the
     * key is kept and runs when the cooldown ends (older ones are superseded, never lost).
     *
     * @param key    logical identifier (e.g. "dbBackup", "skillSave")
     * @param dbTask the Runnable that performs the DB work (should capture a snapshot)
     */
    public void scheduleDbSave(String key, Runnable dbTask) {
        submit(key, () -> {
            try {
                dbTask.run();
            } catch (Exception ex) {
                plugin.getLogger().severe("Failed to run DB save '" + key + "': " + ex.getMessage());
                ex.printStackTrace();
            }
        });
    }

    private void submit(String key, Runnable work) {
        SaveThrottle.Decision d;
        // Decide and record the pending task atomically for this key, so a flush cannot run
        // between "a flush is scheduled" and "here is the newest task" and leave it behind.
        synchronized (SaveThrottle.lockFor(key)) {
            d = throttle.request(key, System.currentTimeMillis());
            if (d.action() != SaveThrottle.Action.RUN_NOW) pending.put(key, work);
        }
        switch (d.action()) {
            case RUN_NOW -> Bukkit.getAsyncScheduler().runNow(plugin, task -> work.run());
            case ALREADY_SCHEDULED -> { } // the scheduled flush will run the newest pending task
            case SCHEDULE_FLUSH -> Bukkit.getAsyncScheduler().runDelayed(plugin, task -> {
                Runnable latest;
                synchronized (SaveThrottle.lockFor(key)) {
                    throttle.flushed(key, System.currentTimeMillis());
                    latest = pending.remove(key);
                }
                if (latest != null) latest.run();
            }, d.delayMs(), TimeUnit.MILLISECONDS);
        }
    }
}
