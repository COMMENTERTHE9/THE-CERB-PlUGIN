package Manager;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Which skill a kill trains: the one behind the player's last hit on the mob, not the item in hand when it died
 * (docs/STATUS.md, "XP integrity"). An arrow that lands after the archer switched to a sword trains Ranged
 * Precision; a thrown trident trains Weapon Mastery, not the empty hand; a burn from a fireball trains the spell.
 *
 * <p>In memory, by mob: the last hit's player, skill and time. A hit by one player doesn't credit another's kill,
 * and a credit older than {@link #FRESH_MS} gives way to the item in hand. Entries are dropped at death and swept
 * when idle for {@link #IDLE_MS}. Thread-safe; time is passed in.
 */
public final class KillCredit {

    /** A last hit this recent decides the kill. */
    static final long FRESH_MS = 15_000L;
    static final long IDLE_MS = 10 * 60_000L;
    static final long PRUNE_EVERY_MS = 60_000L;

    private record Hit(UUID player, String skill, long at) {}

    private final Map<UUID, Hit> last = new ConcurrentHashMap<>();
    private volatile long lastPrune;

    /** {@code player} hit {@code mob} with something that trains {@code skill} ("" or null: nothing). */
    public void hit(UUID mob, UUID player, String skill, long now) {
        if (mob == null || player == null || skill == null || skill.isEmpty()) return;
        last.put(mob, new Hit(player, skill, now));
        if (now - lastPrune >= PRUNE_EVERY_MS) {
            lastPrune = now;
            last.values().removeIf(h -> now - h.at() > IDLE_MS);
        }
    }

    /** The skill {@code killer}'s kill of {@code mob} trains: their fresh last hit's, else {@code fallback}. */
    public String skillFor(UUID mob, UUID killer, String fallback, long now) {
        Hit h = mob == null ? null : last.get(mob);
        return h != null && h.player().equals(killer) && now - h.at() <= FRESH_MS ? h.skill() : fallback;
    }

    /** The mob is gone. */
    public void forget(UUID mob) {
        if (mob != null) last.remove(mob);
    }

    public int size() { return last.size(); }
}
