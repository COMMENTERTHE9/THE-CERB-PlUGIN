package Attributes;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.LongAdder;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;

/**
 * Every online player's attributes. A snapshot is built once, cached, and rebuilt only after
 * something changed ({@link #markDirty}: level-up, load, reload). Reads are a map lookup plus a
 * rules identity check, so combat can call {@link #get} on every hit, from any region thread.
 *
 * <p>Only players whose data is loaded are tracked. Anyone else (not loaded yet, or already
 * quit) gets the neutral snapshot, and nothing is cached for them, so no state outlives a session.
 */
public final class AttributeService {

    private final Supplier<AttributeRules> rules;
    private final List<AttributeSource> sources = new CopyOnWriteArrayList<>();
    private final Set<UUID> tracked = ConcurrentHashMap.newKeySet();
    private final Map<UUID, AttributeSnapshot> cache = new ConcurrentHashMap<>();
    private final LongAdder rebuilds = new LongAdder();
    private volatile AttributeSnapshot empty;

    public AttributeService(Supplier<AttributeRules> rules) {
        this.rules = rules;
    }

    public void addSource(AttributeSource source) {
        sources.add(source);
        cache.clear();
    }

    public List<AttributeSource> sources() { return List.copyOf(sources); }

    public AttributeRules rules() { return rules.get(); }

    /** The player's data is loaded: start tracking (snapshot builds on first read). */
    public void track(UUID player) {
        tracked.add(player);
        cache.remove(player);
    }

    /** The player left: forget everything about them. */
    public void untrack(UUID player) {
        tracked.remove(player);
        cache.remove(player);
    }

    /** An input changed (call after the change is visible): rebuild on next read. */
    public void markDirty(UUID player) {
        cache.remove(player);
    }

    public boolean isTracked(UUID player) { return tracked.contains(player); }

    /** Whether an attribute changes gameplay right now. */
    public enum Effect { ACTIVE, SWITCHED_OFF, NOT_YET }

    // Which integrations exist in code. Each flips to true in the increment that wires it
    // (docs/ATTRIBUTES_ENGINE.md), so menus never claim an effect that isn't there.
    static final boolean OFFENSE_WIRED = true;    // Manager.PveOffense, increment 4
    static final boolean VITALITY_WIRED = false;
    static final boolean FORTITUDE_WIRED = false;
    static final boolean ESSENCE_WIRED = false;

    public Effect effect(Attribute a) {
        AttributeRules.Switches sw = rules.get().switches();
        return switch (a) {
            case POWER, AGILITY -> effect(OFFENSE_WIRED, sw.pveOffense());
            case VITALITY -> effect(VITALITY_WIRED, sw.vitality());
            case FORTITUDE -> effect(FORTITUDE_WIRED, sw.fortitude());
            case ESSENCE -> effect(ESSENCE_WIRED, sw.essence());
            case FORTUNE -> Effect.NOT_YET;
        };
    }

    private static Effect effect(boolean wired, boolean on) {
        return !wired ? Effect.NOT_YET : on ? Effect.ACTIVE : Effect.SWITCHED_OFF;
    }

    /** Current snapshot. Never null; neutral for players who aren't loaded. */
    public AttributeSnapshot get(UUID player) {
        AttributeRules r = rules.get();
        if (!tracked.contains(player)) return empty(r);
        AttributeSnapshot s = cache.get(player);
        if (s != null && s.rules() == r) return s;              // rules swap (reload) invalidates everything
        AttributeSnapshot built = cache.compute(player, (id, old) -> {
            if (old != null && old.rules() == r) return old;     // another thread just rebuilt it
            if (!tracked.contains(id)) return null;              // quit while we were here: cache nothing
            return build(id, r, UnaryOperator.identity());
        });
        return built != null ? built : empty(r);
    }

    /**
     * What-if snapshot (never cached): the player's real contributions passed through {@code edit}.
     * Used by previews such as "Blade Mastery 51 would give...".
     */
    public AttributeSnapshot preview(UUID player, UnaryOperator<List<Contribution>> edit) {
        return build(player, rules.get(), edit);
    }

    private AttributeSnapshot build(UUID player, AttributeRules r, UnaryOperator<List<Contribution>> edit) {
        List<Contribution> all = new ArrayList<>();
        for (AttributeSource src : sources) all.addAll(src.contributions(player, r));
        rebuilds.increment();
        return AttributeCalculator.compute(edit.apply(all), r);
    }

    private AttributeSnapshot empty(AttributeRules r) {
        AttributeSnapshot e = empty;
        if (e == null || e.rules() != r) empty = e = AttributeSnapshot.empty(r);
        return e;
    }

    // Diagnostics (admin Server Info)
    public int trackedCount() { return tracked.size(); }
    public int cachedCount() { return cache.size(); }
    public long rebuildCount() { return rebuilds.sum(); }
}
