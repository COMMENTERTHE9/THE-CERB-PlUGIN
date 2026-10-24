package Manager;

import org.bukkit.entity.LivingEntity;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Per-mob virtual health pool — a {@code double}-backed HP store that can hold
 * cultivation-scale values (billions+), which the vanilla {@code MAX_HEALTH}
 * attribute (hard-capped at 1024) cannot. Mirrors the player Virtual Health system.
 *
 * <p>The vanilla health bar is driven cosmetically (see {@link #COSMETIC_MAX_HEALTH});
 * the real HP lives here. Folia-safe: all access happens on the mob's owning region
 * thread (spawn + damage events fire there), and the backing map is concurrent.
 */
public final class MobVirtualHealthManager {

    /** The vanilla MAX_HEALTH set on scaled mobs purely so the health bar renders. */
    public static final double COSMETIC_MAX_HEALTH = 40.0;

    private static final class State {
        double current;
        final double max;
        State(double max) { this.max = max; this.current = max; }
    }

    private final Map<UUID, State> states = new ConcurrentHashMap<>();

    /** Register a mob's real (possibly huge) max HP. */
    public void init(LivingEntity mob, double maxHealth) {
        if (mob != null) states.put(mob.getUniqueId(), new State(Math.max(1.0, maxHealth)));
    }

    public boolean has(LivingEntity mob) {
        return mob != null && states.containsKey(mob.getUniqueId());
    }

    public double getMax(LivingEntity mob) {
        State s = mob == null ? null : states.get(mob.getUniqueId());
        return s != null ? s.max : 0.0;
    }

    public double getCurrent(LivingEntity mob) {
        State s = mob == null ? null : states.get(mob.getUniqueId());
        return s != null ? s.current : 0.0;
    }

    /** Remaining fraction in [0,1] (1.0 if untracked). */
    public double fraction(LivingEntity mob) {
        State s = mob == null ? null : states.get(mob.getUniqueId());
        if (s == null || s.max <= 0) return 1.0;
        return Math.max(0.0, Math.min(1.0, s.current / s.max));
    }

    /**
     * Subtract {@code amount} from the pool.
     * @return true if this hit depletes the pool (the mob should now die).
     */
    public boolean damage(LivingEntity mob, double amount) {
        State s = mob == null ? null : states.get(mob.getUniqueId());
        if (s == null) return false;
        s.current = Math.max(0.0, s.current - Math.max(0.0, amount));
        return s.current <= 0.0;
    }

    /** Set the pool to {@code fraction} of its max (clamped to [0,1]). No-op if untracked. */
    public void setFraction(LivingEntity mob, double fraction) {
        State s = mob == null ? null : states.get(mob.getUniqueId());
        if (s == null) return;
        double f = Double.isNaN(fraction) ? 1.0 : Math.max(0.0, Math.min(1.0, fraction));
        s.current = s.max * f;
    }

    public void remove(UUID uuid) { states.remove(uuid); }
    public void clear() { states.clear(); }
    public int size() { return states.size(); }
}
