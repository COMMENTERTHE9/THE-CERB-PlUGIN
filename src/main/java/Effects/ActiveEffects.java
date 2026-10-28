package Effects;

import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.logging.Logger;

/**
 * Every effect on a player right now, from every registered {@link EffectProvider} (docs/design/ACTIVE_EFFECTS.md).
 * Holds no effect state of its own: each call asks the systems that run them. Player's region thread.
 */
public final class ActiveEffects {

    /** One provider by name, so a failure in the log says which. */
    private record Named(String name, EffectProvider provider) {}

    private final List<Named> providers = new CopyOnWriteArrayList<>();
    private final Set<String> failed = ConcurrentHashMap.newKeySet();
    private final Logger log;

    public ActiveEffects(Logger log) {
        this.log = log;
    }

    /** Offer {@code provider}'s effects to the menus. {@code name}: for the log. */
    public void register(String name, EffectProvider provider) {
        providers.add(new Named(name, provider));
    }

    /** {@code p}'s effects at {@code now}, in display order (a meal first); always-on ones last. Player's thread. */
    public List<ActiveEffect> of(Player p, long now) {
        List<ActiveEffect> all = new ArrayList<>();
        for (Named n : providers) {
            try {
                n.provider().collect(p, now, all);
            } catch (RuntimeException e) {
                // A broken source must not take the menu down with it; said once per source in the log.
                if (failed.add(n.name())) log.warning("[Effects] " + n.name() + " could not list its effects: " + e);
            }
        }
        return order(all, now);
    }

    /** Pure: drop what has ended by {@code now}; order by kind (a meal first), then the soonest to end. */
    public static List<ActiveEffect> order(List<ActiveEffect> effects, long now) {
        List<ActiveEffect> out = new ArrayList<>(effects.size());
        for (ActiveEffect e : effects) if (e != null && e.active(now)) out.add(e);
        out.sort(Comparator.comparingInt((ActiveEffect e) -> e.category().ordinal())
                .thenComparingLong(ActiveEffect::endsAt)
                .thenComparing(ActiveEffect::name));
        return out;
    }

    /** The timed and untimed effects players can lose (everything but {@link ActiveEffect.Category#ALWAYS_ON}). */
    public static List<ActiveEffect> temporary(List<ActiveEffect> effects) {
        List<ActiveEffect> out = new ArrayList<>(effects.size());
        for (ActiveEffect e : effects) if (e.category() != ActiveEffect.Category.ALWAYS_ON) out.add(e);
        return out;
    }

    /** The equipped passives and keystone: always on, not timed. */
    public static List<ActiveEffect> alwaysOn(List<ActiveEffect> effects) {
        List<ActiveEffect> out = new ArrayList<>();
        for (ActiveEffect e : effects) if (e.category() == ActiveEffect.Category.ALWAYS_ON) out.add(e);
        return out;
    }

    /** True when any of {@code effects} counts down (a screen showing them updates once a second). */
    public static boolean anyTimed(List<ActiveEffect> effects) {
        for (ActiveEffect e : effects) {
            if (e.timed() || (e.phase() != null && e.phase().endsAt() > 0)) return true;
        }
        return false;
    }

    /** The keys of {@code effects} in order: a screen redraws when they change (one ended, one started). */
    public static List<String> keys(List<ActiveEffect> effects) {
        List<String> out = new ArrayList<>(effects.size());
        for (ActiveEffect e : effects) out.add(e.key());
        return out;
    }
}
