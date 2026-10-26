package Manager;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * How much per-hit XP each mob has paid each skill (docs/STATUS.md, "XP integrity"): a mob pays a skill at most its
 * budget, what its kill pays that skill. A strong mob kept alive in a cage, or one hitting a player in a trap, runs
 * dry after a while instead of paying forever; a real fight, which ends in a kill, is unchanged. The same rule as
 * class cast XP ({@code Classes.CastXpLedger}), per skill.
 *
 * <p>In memory, by mob. A mob's death marks its entry, dropped a few seconds later (a spell still in flight sees
 * it); entries idle for half an hour (the mob unloaded or wandered off) are dropped too. A restart forgets them,
 * which costs nothing real. Thread-safe: hits run on different region threads (each mob's entry is updated
 * atomically). Time is passed in.
 */
public final class XpBudgetLedger {

    static final long IDLE_MS = 30 * 60_000L;
    static final long DEAD_GRACE_MS = 10_000L;
    static final long PRUNE_EVERY_MS = 60_000L;

    /** One mob: what it paid each skill; {@code at} is the last payment, or the death for a dead mob. Immutable. */
    private record Entry(Map<String, Double> spent, long at, boolean dead) {}

    private final Map<UUID, Entry> mobs = new ConcurrentHashMap<>();
    private volatile long lastPrune;

    /** Pay up to {@code xp} of {@code skill} from {@code mob}, whose budget for it is {@code budget}; returns what is paid. */
    public double pay(UUID mob, String skill, double xp, double budget, long now) {
        if (mob == null || skill == null || !(xp > 0) || !(budget > 0) || !Double.isFinite(xp) || !Double.isFinite(budget)) return 0;
        double[] paid = new double[1];
        mobs.compute(mob, (id, e) -> {
            double before = e == null ? 0 : e.spent().getOrDefault(skill, 0.0);
            paid[0] = Math.max(0, Math.min(xp, budget - before));
            Map<String, Double> next = e == null ? new HashMap<>(4) : new HashMap<>(e.spent());
            next.put(skill, before + paid[0]);
            return e != null && e.dead() ? new Entry(next, e.at(), true) : new Entry(next, now, false);
        });
        sweep(now);
        return paid[0];
    }

    /** What {@code mob} has paid {@code skill} so far. */
    public double spent(UUID mob, String skill) {
        Entry e = mob == null ? null : mobs.get(mob);
        return e == null || skill == null ? 0 : e.spent().getOrDefault(skill, 0.0);
    }

    /** The mob died: its entry is dropped after {@link #DEAD_GRACE_MS}. */
    public void died(UUID mob, long now) {
        if (mob == null) return;
        mobs.computeIfPresent(mob, (id, e) -> new Entry(e.spent(), now, true));
        sweep(now);
    }

    private void sweep(long now) {
        if (now - lastPrune >= PRUNE_EVERY_MS) {
            lastPrune = now;
            prune(now);
        }
    }

    /** Drop dead mobs' entries after the grace and idle ones after {@link #IDLE_MS}. */
    public void prune(long now) {
        mobs.values().removeIf(e -> now - e.at() > (e.dead() ? DEAD_GRACE_MS : IDLE_MS));
    }

    public int size() { return mobs.size(); }
}
