package Classes;

import java.util.HashMap;
import java.util.Map;

/**
 * One player's cooldowns (docs/design/CLASSES.md §5.4, §6). They belong to the player, not to a
 * loadout: switching loadouts never resets them, and the long ones are saved across relogs.
 * Keys: "ability:<id>", "group:<name>" (shared lockouts, e.g. every dash and blink: a cast locks
 * the group's other abilities; its own cooldown covers itself), "strike:<id>" (a movement
 * ability's strike, on its own clock) and "global" (a short gap between any two casts). Times are
 * epoch milliseconds. Only touched on the player's thread; not thread-safe on its own.
 */
public final class CooldownBook {

    /** Gap between any two casts. */
    public static final long GLOBAL_MS = 400;

    private final Map<String, Long> until = new HashMap<>();
    /** group -> the ability whose cast started its lockout (a restored lockout has none: it locks them all). */
    private final Map<String, String> groupStartedBy = new HashMap<>();

    /**
     * Milliseconds until {@code ability} can be cast (0 = ready): its own cooldown, the global gap,
     * and its groups' lockouts started by another ability of the group.
     */
    public long remaining(AbilityDef ability, long now) {
        long left = left("global", now);
        left = Math.max(left, left("ability:" + ability.id(), now));
        for (String g : ability.groups()) {
            if (!ability.id().equals(groupStartedBy.get(g))) left = Math.max(left, left("group:" + g, now));
        }
        return left;
    }

    /** A cast went off: start its cooldown, its groups' lockouts ({@code groupMs} each) and the global gap. */
    public void commit(AbilityDef ability, long cooldownMs, Map<String, Long> groupMs, long now) {
        until.put("global", now + GLOBAL_MS);
        until.put("ability:" + ability.id(), now + Math.max(0, cooldownMs));
        for (String g : ability.groups()) {
            Long ms = groupMs.get(g);
            if (ms != null && ms > 0) {
                until.merge("group:" + g, now + ms, Math::max);
                groupStartedBy.put(g, ability.id());
            }
        }
    }

    /** Milliseconds until a movement ability's strike is back (0 = this cast strikes). */
    public long strikeRemaining(AbilityDef ability, long now) { return left("strike:" + ability.id(), now); }

    /** The cast struck: its strike rests {@code ms}. */
    public void commitStrike(AbilityDef ability, long ms, long now) {
        if (ms > 0) until.put("strike:" + ability.id(), now + ms);
    }

    /** Shorten an ability's running cooldown to end at most {@code ms} from now (a successful counter). */
    public void shorten(AbilityDef ability, long ms, long now) {
        until.computeIfPresent("ability:" + ability.id(), (k, end) -> Math.min(end, now + Math.max(0, ms)));
    }

    /** Admin and test tool: every cooldown ends now. */
    public void clear() { until.clear(); groupStartedBy.clear(); }

    /** Running cooldowns with at least {@code minLeftMs} left, to save (short ones don't matter after a relog). */
    public Map<String, Long> saved(long now, long minLeftMs) {
        Map<String, Long> out = new HashMap<>();
        until.forEach((k, end) -> { if (end - now >= minLeftMs && !k.equals("global")) out.put(k, end); });
        return out;
    }

    /** The longest a restored cooldown can still run (a damaged or edited save can't lock an ability for good). */
    static final long MAX_RESTORED_MS = 60 * 60_000L;

    /** Restore saved cooldowns (expired or malformed entries are ignored, absurd ones capped). */
    public void restore(Map<String, Long> saved, long now) {
        if (saved == null) return;
        saved.forEach((k, end) -> {
            if (k != null && (k.startsWith("ability:") || k.startsWith("group:") || k.startsWith("strike:")) && end != null && end > now) {
                until.put(k, Math.min(end, now + MAX_RESTORED_MS));
            }
        });
    }

    private long left(String key, long now) {
        Long end = until.get(key);
        return end == null ? 0 : Math.max(0, end - now);
    }
}
