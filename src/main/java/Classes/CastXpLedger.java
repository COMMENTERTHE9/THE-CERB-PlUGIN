package Classes;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * How much class cast XP each mob has paid (docs/design/CLASSES.md §11). A mob pays at most its
 * budget, what its kill pays; after that only its kill does. So a strong mob kept alive in a cage
 * runs dry after a few dozen casts instead of paying forever, while a real fight, which ends in a
 * kill, is unchanged. Kept in memory by mob id. A mob's death marks its entry, which is dropped a
 * few seconds later: the cast that killed it, and a spell that bursts on it a moment later, still
 * see what it has paid. Entries idle for half an hour (the mob unloaded or wandered off) are
 * dropped too. A restart forgets it, which costs nothing real. Thread-safe: casts, counters and
 * spell hits run on different region threads.
 */
final class CastXpLedger {

    /** Entries idle this long are dropped (the mob unloaded or wandered off). */
    static final long IDLE_MS = 30 * 60_000L;
    /** A dead mob's entry is kept this long, for the cast that killed it and spells still in flight. */
    static final long DEAD_GRACE_MS = 10_000L;
    /** Old entries are swept at most this often (on a payment). */
    static final long PRUNE_EVERY_MS = 60_000L;

    /** {@code at}: the last payment, or the death for a dead mob. */
    private record Entry(double spent, long at, boolean dead) {}

    private final Map<UUID, Entry> spent = new ConcurrentHashMap<>();
    private volatile long lastPrune;

    /**
     * Pay up to {@code xp} from the mob {@code mob}, whose budget is {@code budget}. Returns what is
     * paid: all of it, the rest of the budget, or 0 when the mob has run dry.
     */
    double pay(UUID mob, double xp, double budget, long now) {
        if (mob == null || !(xp > 0) || !(budget > 0) || !Double.isFinite(xp) || !Double.isFinite(budget)) return 0;
        double[] paid = new double[1];
        spent.compute(mob, (id, e) -> {
            double before = e == null ? 0 : e.spent();
            paid[0] = Math.max(0, Math.min(xp, budget - before));
            return e != null && e.dead() ? new Entry(before + paid[0], e.at(), true) : new Entry(before + paid[0], now, false);
        });
        if (now - lastPrune >= PRUNE_EVERY_MS) {
            lastPrune = now;
            prune(now);
        }
        return paid[0];
    }

    /** What the mob has paid so far. */
    double spent(UUID mob) {
        Entry e = mob == null ? null : spent.get(mob);
        return e == null ? 0 : e.spent();
    }

    /** The mob died: its entry is dropped after {@link #DEAD_GRACE_MS}, its budget still spent until then. */
    void died(UUID mob, long now) {
        if (mob != null) spent.computeIfPresent(mob, (id, e) -> new Entry(e.spent(), now, true));
    }

    /** Drop dead mobs' entries after the grace and idle ones after {@link #IDLE_MS}. */
    void prune(long now) {
        spent.values().removeIf(e -> now - e.at() > (e.dead() ? DEAD_GRACE_MS : IDLE_MS));
    }

    int size() { return spent.size(); }
}
