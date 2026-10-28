package GUIs.menu;

import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Live screens (docs/design/ACTIVE_EFFECTS.md §3): a screen showing a countdown asks for one update every few ticks
 * ({@link MenuView#every}) while it is what the viewer sees.
 *
 * <ul>
 *   <li>On the viewer's own entity scheduler (Folia: the only thread that may touch their open inventory).</li>
 *   <li>At most one per viewer: a new screen, a refresh, a close or leaving the server stops it, and an update only ever
 *       writes into the exact screen render it was made for.</li>
 *   <li>It stops by itself when its update says nothing is left to count down. A screen without a countdown asks for
 *       none, so an open menu with nothing timed on it still runs nothing.</li>
 * </ul>
 */
public final class MenuTicker {

    /** One update of a live screen: false when there is nothing left to update (the ticker stops). The viewer's thread. */
    @FunctionalInterface
    public interface Update {
        boolean run();
    }

    /** Fastest allowed: twice a second. */
    public static final int MIN_PERIOD = 10;

    private final JavaPlugin plugin;
    private final Map<UUID, ScheduledTask> running = new ConcurrentHashMap<>();
    private final AtomicLong updates = new AtomicLong();

    MenuTicker(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Run {@code update} every {@code period} ticks for the screen just rendered into {@code inv} (render number of
     * {@code holder} now), replacing any ticker this viewer had. A null update just stops the old one. Viewer's thread.
     */
    void start(Player player, MenuHolder holder, Inventory inv, int period, Update update) {
        UUID id = player.getUniqueId();
        cancel(id);
        if (update == null) return;
        final int seq = holder.renderSeq();
        long every = Math.max(MIN_PERIOD, period);
        ScheduledTask task = player.getScheduler().runAtFixedRate(plugin, t -> {
            // The viewer moved on (another screen, a refresh, closed): never write into what they see now.
            if (player.getOpenInventory().getTopInventory() != inv || holder.renderSeq() != seq) {
                stop(id, t);
                return;
            }
            boolean more;
            try {
                more = update.run();
            } catch (RuntimeException e) {
                plugin.getLogger().warning("[Menu] A live screen's update failed (" + holder.menu().getClass().getSimpleName() + "): " + e);
                more = false;
            }
            updates.incrementAndGet();
            if (!more) stop(id, t);
        }, () -> running.remove(id), every, every);
        if (task != null) running.put(id, task);
    }

    private void stop(UUID id, ScheduledTask t) {
        t.cancel();
        running.remove(id, t);
    }

    /** Stop this viewer's ticker, if any (screen change, close, quit). */
    void cancel(UUID id) {
        ScheduledTask t = running.remove(id);
        if (t != null) t.cancel();
    }

    /** Stop everything (plugin disable). */
    void cancelAll() {
        for (ScheduledTask t : running.values()) t.cancel();
        running.clear();
    }

    /** Live screens counting down right now (0 when nobody has one open). */
    public int runningCount() { return running.size(); }

    /** Updates run since the plugin started. */
    public long updateCount() { return updates.get(); }
}
