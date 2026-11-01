package Skills;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * XP a session earns while its skills are still loading (docs/STATUS.md → reliability audit, P2): kept per session
 * generation and handed over once when the load completes, dropped when the load fails or the player leaves first.
 * Never applied to another session: a buffer belongs to one generation.
 *
 * <p>An award may come from another thread than the player's (a kill on a mob's region). A buffer is closed under its own
 * lock when it is handed over or dropped, so an award racing that moment is either in what is handed over or told that
 * it wasn't kept ({@link #keep} returns false), and the caller then awards it the normal way (the skills are loaded by
 * then) or drops it (the player left).
 */
final class LoadingXpBuffer {

    /** At most this much XP per skill is kept for one load (a guard, far above any real load's worth). */
    static final double CAP = 1e12;

    private static final class Entry {
        final long generation;
        /** skill → {XP from awards that celebrate a level-up, XP from quiet awards}. Changed only under the entry's lock. */
        final Map<String, double[]> bySkill = new HashMap<>();
        boolean closed;
        Entry(long generation) { this.generation = generation; }
    }

    private final Map<UUID, Entry> loading = new ConcurrentHashMap<>();

    /** A session's load has begun: XP it earns is kept for it. */
    void begin(UUID player, long generation) {
        Entry old = loading.put(player, new Entry(generation));
        if (old != null) close(old);
    }

    /** That session's load failed: what it earned while loading is dropped. Another generation's buffer is left alone. */
    void abandon(UUID player, long generation) {
        Entry e = loading.get(player);
        if (e != null && e.generation == generation && loading.remove(player, e)) close(e);
    }

    /** The player left: whatever is kept is dropped (there is nothing left to apply it to). */
    void forget(UUID player) {
        Entry e = loading.remove(player);
        if (e != null) close(e);
    }

    /** True between {@link #begin} and the hand-over, failure or leaving. */
    boolean isLoading(UUID player) {
        return loading.containsKey(player);
    }

    /**
     * Keep an award for the loading session. False when there is no open buffer (never began, handed over meanwhile,
     * dropped): the caller decides then. An amount that isn't a positive finite number is ignored (true: nothing to do).
     */
    boolean keep(UUID player, String skill, double amount, boolean celebrate) {
        Entry e = loading.get(player);
        if (e == null) return false;
        synchronized (e) {
            if (e.closed) return false;
            if (skill == null || !(amount > 0) || !Double.isFinite(amount)) return true;
            double[] a = e.bySkill.computeIfAbsent(skill, k -> new double[2]);
            int i = celebrate ? 0 : 1;
            a[i] = Math.min(CAP, a[i] + amount);
            return true;
        }
    }

    /**
     * The load of {@code generation} completed: closes the buffer and returns what it kept (skill → {celebrated, quiet}),
     * once. Null when there is none, or it belongs to another generation (that one is dropped: never applied to a session
     * it wasn't earned in).
     */
    Map<String, double[]> drain(UUID player, long generation) {
        Entry e = loading.remove(player);
        if (e == null) return null;
        close(e);
        return e.generation == generation ? e.bySkill : null; // closed: nothing changes it any more
    }

    private static void close(Entry e) {
        synchronized (e) {
            e.closed = true;
        }
    }
}
