package GUIs.menu;

import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Short menu animations (Player Menu 2.1): a screen's {@link MenuView#later keyframes}, played one
 * tick at a time on the viewer's own entity scheduler (Folia: the viewer's region thread, the only
 * place their open inventory may be touched).
 *
 * <ul>
 *   <li>Bounded: nothing runs longer than {@link #MAX_TICKS}; the task cancels itself after its
 *       last keyframe, so an open menu that nobody touches runs nothing.</li>
 *   <li>At most one per viewer: a new screen, a refresh or a close cancels the running one, and a
 *       keyframe is only ever written into the exact screen render it was made for.</li>
 *   <li>Off the click path: a click only schedules; the slot updates happen in later ticks, and
 *       buttons work from the first tick (a keyframe changes how a slot looks, never what it does).</li>
 * </ul>
 */
public final class MenuAnimator {

    /** No animation runs longer than this (1 s). */
    public static final int MAX_TICKS = 20;

    private final JavaPlugin plugin;
    private final Map<UUID, ScheduledTask> running = new ConcurrentHashMap<>();
    private final AtomicLong started = new AtomicLong();

    MenuAnimator(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Play {@code frames} for the screen just rendered into {@code inv} (render number {@code seq}),
     * replacing any animation already running for this viewer. Call on the viewer's thread.
     */
    void play(Player player, MenuHolder holder, Inventory inv, List<MenuView.Keyframe> frames) {
        UUID id = player.getUniqueId();
        cancel(id);
        if (frames.isEmpty()) return;
        frames = new ArrayList<>(frames);
        frames.sort(java.util.Comparator.comparingInt(MenuView.Keyframe::tick));
        int last = 0;
        for (MenuView.Keyframe k : frames) last = Math.max(last, k.tick());
        @SuppressWarnings("unchecked")
        List<MenuView.Keyframe>[] byTick = new List[last + 1];
        for (MenuView.Keyframe k : frames) {
            if (byTick[k.tick()] == null) byTick[k.tick()] = new ArrayList<>(4);
            byTick[k.tick()].add(k);
        }
        final int end = last;
        final int seq = holder.renderSeq();
        final int[] tick = {0};
        ScheduledTask task = player.getScheduler().runAtFixedRate(plugin, t -> {
            tick[0]++;
            if (tick[0] > end) { stop(id, t); return; } // never runs past its last frame, whatever happened
            // The viewer moved on (another screen, a refresh, closed): never draw into what they see now.
            if (player.getOpenInventory().getTopInventory() != inv || holder.renderSeq() != seq) {
                stop(id, t);
                return;
            }
            try {
                List<MenuView.Keyframe> now = byTick[tick[0]];
                if (now != null) {
                    for (MenuView.Keyframe k : now) {
                        if (k.item() != null) inv.setItem(k.slot(), k.item());
                        if (k.sound() != null) player.playSound(player.getLocation(), k.sound(), k.volume(), k.pitch());
                        if (k.action() != null) k.action().run();
                    }
                }
            } finally {
                if (tick[0] >= end) stop(id, t);
            }
        }, () -> running.remove(id), 1L, 1L);
        if (task != null) {
            running.put(id, task);
            started.incrementAndGet();
        } else {
            // The player is leaving (no scheduler): show each slot's final look at once, skip sounds and actions.
            for (MenuView.Keyframe k : frames) if (k.item() != null) inv.setItem(k.slot(), k.item());
        }
    }

    private void stop(UUID id, ScheduledTask t) {
        t.cancel();
        running.remove(id, t);
    }

    /** Stop this viewer's animation, if any (screen change, close, quit). */
    void cancel(UUID id) {
        ScheduledTask t = running.remove(id);
        if (t != null) t.cancel();
    }

    /** Stop everything (plugin disable). */
    void cancelAll() {
        for (ScheduledTask t : running.values()) t.cancel();
        running.clear();
    }

    /** Animations running right now (0 when no menu is mid-animation). */
    public int runningCount() { return running.size(); }

    /** Animations started since the plugin started. */
    public long startedCount() { return started.get(); }
}
