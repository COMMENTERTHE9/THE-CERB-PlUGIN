package Skills;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * What a session does that earns XP while its skills are still loading (docs/STATUS.md → reliability audit, and the
 * owner's decision on item 10: exact XP while loading). Each action is kept as it happened, in order, per session
 * generation, and run once when that load completes, so the XP it pays is worked out from the player's restored levels
 * and sees what the actions before it changed, never as if the player were level 1. Dropped when the load fails or the
 * player leaves first. Never run for another session: a buffer belongs to one generation.
 *
 * <p>An action may be kept from another thread than the player's (a kill on a mob's region). A buffer is closed under its
 * own lock when it is handed over or dropped, so an action racing that moment is either in what is handed over or told
 * that it wasn't kept ({@link #keep} returns false), and the caller then runs it the normal way (the skills are loaded by
 * then) or drops it (the player left).
 */
final class LoadingXpBuffer {

    /**
     * At most this many actions are kept for one load: a guard against a load that never ends (a real one keeps a few,
     * a minute of heavy fighting a few thousand). Past it, actions are dropped and counted ({@link Kept#dropped}).
     */
    static final int MAX_ACTIONS = 10_000;

    /** What a completed load hands over: its actions in the order they happened, and how many the cap dropped. */
    record Kept(List<Runnable> actions, int dropped) {}

    private static final class Entry {
        final long generation;
        /** In the order they were kept. Changed only under the entry's lock, and never once closed. */
        final List<Runnable> actions = new ArrayList<>();
        int dropped;
        boolean closed;
        Entry(long generation) { this.generation = generation; }
    }

    private final Map<UUID, Entry> loading = new ConcurrentHashMap<>();
    /** Tests only: runs in {@link #keep} between finding the buffer and locking it (to put a hand-over exactly there). */
    Runnable afterGet = () -> { };

    /** A session's load has begun: what it does that earns XP is kept for it. An older buffer of the player's is dropped. */
    void begin(UUID player, long generation) {
        Entry old = loading.put(player, new Entry(generation));
        if (old != null) close(old);
    }

    /** That session's load failed: what it did while loading is dropped. Another generation's buffer is left alone. */
    void abandon(UUID player, long generation) {
        Entry e = loading.get(player);
        if (e != null && e.generation == generation && loading.remove(player, e)) close(e);
    }

    /** The player left: whatever is kept is dropped (there is nothing left to pay it to). */
    void forget(UUID player) {
        Entry e = loading.remove(player);
        if (e != null) close(e);
    }

    /** True between {@link #begin} and the hand-over, failure or leaving. */
    boolean isLoading(UUID player) {
        return loading.containsKey(player);
    }

    /**
     * Keep {@code action} for the loading session, after everything kept before it. False when there is no open buffer
     * (never began, handed over meanwhile, dropped): the caller decides then. True also when the buffer is full and the
     * action is dropped (counted): an action of a loading session never runs before its load completes.
     */
    boolean keep(UUID player, Runnable action) {
        if (action == null) return true; // nothing to do
        Entry e = loading.get(player);
        if (e == null) return false;
        afterGet.run();
        synchronized (e) {
            if (e.closed) return false;
            if (e.actions.size() >= MAX_ACTIONS) e.dropped++;
            else e.actions.add(action);
            return true;
        }
    }

    /**
     * The load of {@code generation} completed: closes the buffer and returns what it kept, once. Null when there is
     * none, or it belongs to another generation: that one is left for its own session's load (never run for a session it
     * wasn't kept in, never dropped by another's).
     */
    Kept drain(UUID player, long generation) {
        Entry e = loading.get(player);
        if (e == null || e.generation != generation || !loading.remove(player, e)) return null;
        synchronized (e) {
            e.closed = true;
            return new Kept(List.copyOf(e.actions), e.dropped); // closed: nothing changes it any more
        }
    }

    private static void close(Entry e) {
        synchronized (e) {
            e.closed = true;
        }
    }
}
