package Gathering;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * One player's fishing record (docs/design/FISHING.md): the species they caught with their heaviest catch, the creatures
 * they defeated, their banked lures, and the creature fight in progress. Saved on the player (with their inventory, so a
 * crash or restart keeps the two consistent), as one short text. Pure and not thread-safe: used on the player's thread.
 *
 * <p>The lures and the fight in progress are what must never duplicate or vanish: a lure is spent only by
 * {@link #spend} (which records the fight), and a fight ends exactly once, by {@link #finish} (won: the lure stays spent)
 * or {@link #refund} (it left: the lure comes back).
 */
public final class FishingJournal {

    /** The creature fight in progress: which creature, the entity that is it, when it was called. */
    public record Active(String creature, UUID entity, long started) {}

    private final Map<String, Double> species = new LinkedHashMap<>();
    private final Set<String> creatures = new LinkedHashSet<>();
    private final Map<String, Integer> lures = new LinkedHashMap<>();
    private final Set<String> flags = new LinkedHashSet<>();
    private Active active;

    // ── flags (one-time moments: the first lure Something Stirs gives) ──

    public boolean flag(String f) { return flags.contains(f); }
    /** True the first time. */
    public boolean setFlag(String f) { return FishingRules.ID.matcher(f).matches() && flags.add(f); }

    // ── species ───────────────────────────────────────────────────

    public boolean caught(String id) { return species.containsKey(id); }
    public int speciesCount() { return species.size(); }
    public Map<String, Double> species() { return Collections.unmodifiableMap(species); }
    public double best(String id) { return species.getOrDefault(id, 0.0); }

    /** What a catch of {@code id} weighing {@code kg} was: a first catch, a new personal best, or neither. */
    public enum Record { DISCOVERY, PERSONAL_BEST, NONE }

    public Record record(String id, double kg) {
        Double was = species.get(id);
        if (was == null) { species.put(id, kg); return Record.DISCOVERY; }
        if (kg > was) { species.put(id, kg); return Record.PERSONAL_BEST; }
        return Record.NONE;
    }

    // ── creatures defeated ────────────────────────────────────────

    public boolean defeated(String creature) { return creatures.contains(creature); }
    public Set<String> creaturesDefeated() { return Collections.unmodifiableSet(creatures); }
    /** True the first time. */
    public boolean defeat(String creature) { return creatures.add(creature); }

    // ── lures ─────────────────────────────────────────────────────

    public int lures(String creature) { return lures.getOrDefault(creature, 0); }
    public Map<String, Integer> lures() { return Collections.unmodifiableMap(lures); }
    public int totalLures() {
        int n = 0;
        for (int v : lures.values()) n += v;
        return n;
    }

    /** One more lure for {@code creature}, up to {@code max} of that creature (0: no limit). False when full. */
    public boolean addLure(String creature, int max) {
        int have = lures(creature);
        if (have == Integer.MAX_VALUE || (max > 0 && have >= max)) return false;
        lures.put(creature, have + 1);
        return true;
    }

    public Active active() { return active; }

    /**
     * Spend a lure on a creature that now exists as {@code entity}: only when one is banked and no fight is in progress.
     * Returns false (and changes nothing) otherwise.
     */
    public boolean spend(String creature, UUID entity, long now) {
        if (active != null || entity == null || lures(creature) <= 0) return false;
        int left = lures(creature) - 1;
        if (left == 0) lures.remove(creature);
        else lures.put(creature, left);
        active = new Active(creature, entity, now);
        return true;
    }

    /** The fight with {@code entity} was won: it ends, and its lure stays spent. False when it is not the fight in progress. */
    public boolean finish(UUID entity) {
        if (active == null || !active.entity().equals(entity)) return false;
        active = null;
        return true;
    }

    /** The creature {@code entity} left without being defeated: its lure comes back, once. False when it is not the fight in progress. */
    public boolean refund(UUID entity) {
        if (active == null || !active.entity().equals(entity)) return false;
        String c = active.creature();
        active = null;
        lures.merge(c, 1, (a, b) -> a == Integer.MAX_VALUE ? a : a + b);
        return true;
    }

    // ── saving ────────────────────────────────────────────────────

    /** {@code 1|s=dace:0.42,trout:2.31|c=river_lurker|l=river_lurker:3|a=river_lurker:<uuid>:<millis>|f=stirs}. Ids are plain. */
    public String encode() {
        StringBuilder sb = new StringBuilder("1|s=");
        join(sb, species, v -> String.valueOf(v));
        sb.append("|c=").append(String.join(",", creatures));
        sb.append("|l=");
        join(sb, lures, String::valueOf);
        sb.append("|a=");
        if (active != null) sb.append(active.creature()).append(':').append(active.entity()).append(':').append(active.started());
        sb.append("|f=").append(String.join(",", flags));
        return sb.toString();
    }

    private static <V> void join(StringBuilder sb, Map<String, V> m, java.util.function.Function<V, String> f) {
        boolean first = true;
        for (Map.Entry<String, V> e : m.entrySet()) {
            if (!first) sb.append(',');
            sb.append(e.getKey()).append(':').append(f.apply(e.getValue()));
            first = false;
        }
    }

    /** Read {@link #encode}'s text; anything unreadable is skipped (never an exception). Null or blank: an empty journal. */
    public static FishingJournal decode(String text) {
        FishingJournal j = new FishingJournal();
        if (text == null || text.isBlank()) return j;
        String[] parts = text.split("\\|", -1);
        if (parts.length == 0 || !parts[0].equals("1")) return j;
        for (int i = 1; i < parts.length; i++) {
            String p = parts[i];
            if (p.length() < 2 || p.charAt(1) != '=') continue;
            String body = p.substring(2);
            switch (p.charAt(0)) {
                case 's' -> {
                    for (String e : body.split(",")) {
                        String[] kv = e.split(":");
                        if (kv.length == 2 && FishingRules.ID.matcher(kv[0]).matches()) {
                            try {
                                double kg = Double.parseDouble(kv[1]);
                                if (Double.isFinite(kg) && kg >= 0) j.species.put(kv[0], kg);
                            } catch (NumberFormatException ignored) { /* skipped */ }
                        }
                    }
                }
                case 'c' -> {
                    for (String e : body.split(",")) if (FishingRules.ID.matcher(e).matches()) j.creatures.add(e);
                }
                case 'f' -> {
                    for (String e : body.split(",")) if (FishingRules.ID.matcher(e).matches()) j.flags.add(e);
                }
                case 'l' -> {
                    for (String e : body.split(",")) {
                        String[] kv = e.split(":");
                        if (kv.length == 2 && FishingRules.ID.matcher(kv[0]).matches()) {
                            try {
                                int n = Integer.parseInt(kv[1]);
                                if (n > 0) j.lures.put(kv[0], n);
                            } catch (NumberFormatException ignored) { /* skipped */ }
                        }
                    }
                }
                case 'a' -> {
                    String[] a = body.split(":");
                    if (a.length == 3 && FishingRules.ID.matcher(a[0]).matches()) {
                        try {
                            j.active = new Active(a[0], UUID.fromString(a[1]), Long.parseLong(a[2]));
                        } catch (IllegalArgumentException ignored) { /* skipped */ }
                    }
                }
                default -> { /* a later version's part: skipped */ }
            }
        }
        return j;
    }
}
