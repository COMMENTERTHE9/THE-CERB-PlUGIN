package Attributes;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.LongSupplier;

/**
 * Temporary attribute bonuses (admin dev buffs for controlled tests; later potions and
 * consumables). In memory only: never saved, cleared when the player leaves. An expired buff
 * stops counting the moment it expires; whoever adds one schedules a
 * {@link AttributeService#markDirty} at its expiry so the cached snapshot drops it too.
 */
public final class BuffSource implements AttributeSource {

    public static final String SOURCE_PREFIX = "buff:";

    /** One buff. {@code expiresAt} is in the clock's milliseconds. */
    public record Buff(String id, String label, Attribute attribute, double amount, long expiresAt) {}

    private final Map<UUID, List<Buff>> buffs = new ConcurrentHashMap<>();
    private final LongSupplier clock;

    public BuffSource() {
        this(System::currentTimeMillis);
    }

    /** For tests: a controllable clock. */
    public BuffSource(LongSupplier clock) {
        this.clock = clock;
    }

    @Override public String id() { return "buff"; }

    public void add(UUID player, Buff buff) {
        buffs.computeIfAbsent(player, k -> new CopyOnWriteArrayList<>()).add(buff);
    }

    /** Buffs still running, soonest to expire first. Drops expired ones as it goes. */
    public List<Buff> active(UUID player) {
        List<Buff> list = buffs.get(player);
        if (list == null) return List.of();
        long now = clock.getAsLong();
        list.removeIf(b -> b.expiresAt() <= now);
        if (list.isEmpty()) buffs.remove(player, list);
        List<Buff> out = new ArrayList<>(list);
        out.sort(java.util.Comparator.comparingLong(Buff::expiresAt));
        return out;
    }

    /** Remove every buff from this player. Returns how many were active. */
    public int clear(UUID player) {
        List<Buff> removed = buffs.remove(player);
        if (removed == null) return 0;
        long now = clock.getAsLong();
        return (int) removed.stream().filter(b -> b.expiresAt() > now).count();
    }

    public long now() { return clock.getAsLong(); }

    @Override
    public List<Contribution> contributions(UUID player, AttributeRules rules) {
        List<Contribution> out = new ArrayList<>();
        for (Buff b : active(player)) {
            out.add(Contribution.attribute(SOURCE_PREFIX + b.id(), b.label(), b.attribute(), b.amount(), null));
        }
        return out;
    }
}
