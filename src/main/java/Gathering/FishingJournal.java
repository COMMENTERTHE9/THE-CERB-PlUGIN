package Gathering;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * One player's fishing record (docs/design/FISHING.md): the species they caught with their heaviest catch, the creatures
 * they defeated, their banked encounters ("lures" inside the code and the save), and the creature fight in progress. Since
 * docs/design/FISHING_EQUIPMENT.md also: the species they track, the "since your last visit" summary with the last notable
 * catches, their personal-best count and the collection rewards granted. Saved on the player (with their inventory, so a
 * crash or restart keeps the two consistent), as one short text. Pure and not thread-safe: used on the player's thread.
 *
 * <p>The encounters and the fight in progress are what must never duplicate or vanish: one is spent only by
 * {@link #spend} (which records the fight), and a fight ends exactly once, by {@link #finish} (won: it stays spent)
 * or {@link #refund} (it left: it comes back).
 */
public final class FishingJournal {

    /** The creature fight in progress: which creature, the entity that is it, when it was called. */
    public record Active(String creature, UUID entity, long started) {}

    /** The unread "since your last visit" counters (docs/design/FISHING_EQUIPMENT.md §4). */
    public record Summary(long catches, long xp, int species, int bests, int encounters) {
        public static final Summary NONE = new Summary(0, 0, 0, 0, 0);

        public boolean isEmpty() { return catches == 0 && xp == 0 && species == 0 && bests == 0 && encounters == 0; }
    }

    /** A notable catch: a discovery or a personal best, with its real weight and when; unread until acknowledged; a trophy once. */
    public record Notable(boolean discovery, String species, double kg, long at, boolean unread, boolean trophy) {}

    /** The last notable catches kept (the oldest read ones go first). */
    public static final int MAX_NOTABLE = 12;

    private final Map<String, Double> species = new LinkedHashMap<>();
    private final Set<String> creatures = new LinkedHashSet<>();
    private final Map<String, Integer> lures = new LinkedHashMap<>();
    private final Set<String> flags = new LinkedHashSet<>();
    private final Set<String> granted = new LinkedHashSet<>();
    private final List<Notable> notable = new ArrayList<>();
    /** Parts of the save this version doesn't know (a later version's): written back as they were. */
    private final List<String> unknown = new ArrayList<>();
    /** Saved by a later version this one can't read: kept as it is, never written over (see {@link #readOnly}). */
    private boolean readOnly;
    private Active active;
    private String tracked;
    private long bestsSet;
    private long sumCatches, sumXp;
    private int sumSpecies, sumBests, sumEncounters;

    // ── flags (one-time moments: the first encounter Something Stirs gives; settings) ──

    public boolean flag(String f) { return flags.contains(f); }
    /** True the first time. */
    public boolean setFlag(String f) { return FishingRules.ID.matcher(f).matches() && flags.add(f); }
    /** True when it was set. */
    public boolean clearFlag(String f) { return flags.remove(f); }

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
        if (kg > was) {
            species.put(id, kg);
            if (bestsSet < Long.MAX_VALUE) bestsSet++;
            return Record.PERSONAL_BEST;
        }
        return Record.NONE;
    }

    /** Personal bests set since this was counted (a collection milestone counts them). */
    public long bestsSet() { return bestsSet; }

    // ── tracking (informational: never changes a roll) ────────────

    public String tracked() { return tracked; }

    /** Track {@code id} (null: none). False when it isn't a valid id. */
    public boolean track(String id) {
        if (id != null && !FishingRules.ID.matcher(id).matches()) return false;
        tracked = id;
        return true;
    }

    // ── creatures defeated ────────────────────────────────────────

    public boolean defeated(String creature) { return creatures.contains(creature); }
    public Set<String> creaturesDefeated() { return Collections.unmodifiableSet(creatures); }
    /** True the first time. */
    public boolean defeat(String creature) { return creatures.add(creature); }

    // ── encounters (banked sea-creature summons; "lures" in the save) ──

    public int lures(String creature) { return lures.getOrDefault(creature, 0); }
    public Map<String, Integer> lures() { return Collections.unmodifiableMap(lures); }
    public int totalLures() {
        int n = 0;
        for (int v : lures.values()) n = n + v < 0 ? Integer.MAX_VALUE : n + v;
        return n;
    }

    /** One more encounter for {@code creature}, up to {@code max} of that creature (0: no limit). False when full. */
    public boolean addLure(String creature, int max) {
        int have = lures(creature);
        if (have == Integer.MAX_VALUE || (max > 0 && have >= max)) return false;
        lures.put(creature, have + 1);
        return true;
    }

    public Active active() { return active; }

    /**
     * Spend an encounter on a creature that now exists as {@code entity}: only when one is banked and no fight is in
     * progress. Returns false (and changes nothing) otherwise.
     */
    public boolean spend(String creature, UUID entity, long now) {
        if (active != null || entity == null || lures(creature) <= 0) return false;
        int left = lures(creature) - 1;
        if (left == 0) lures.remove(creature);
        else lures.put(creature, left);
        active = new Active(creature, entity, now);
        return true;
    }

    /** The fight with {@code entity} was won: it ends, and its encounter stays spent. False when it is not the fight in progress. */
    public boolean finish(UUID entity) {
        if (active == null || !active.entity().equals(entity)) return false;
        active = null;
        return true;
    }

    /** The creature {@code entity} left without being defeated: its encounter comes back, once. False when it is not the fight in progress. */
    public boolean refund(UUID entity) {
        if (active == null || !active.entity().equals(entity)) return false;
        String c = active.creature();
        active = null;
        lures.merge(c, 1, (a, b) -> a == Integer.MAX_VALUE ? a : a + b);
        return true;
    }

    // ── "since your last visit" (bounded: five counters and the last notable catches) ──

    public Summary summary() { return new Summary(sumCatches, sumXp, sumSpecies, sumBests, sumEncounters); }

    /** A catch that really happened, and the XP it really awarded (after the game mode and the level cap). */
    public void countCatch(long xpAwarded) {
        sumCatches = add(sumCatches, 1);
        countXp(xpAwarded);
    }

    /** XP really awarded by fishing outside a catch (a creature defeated). */
    public void countXp(long xpAwarded) {
        if (xpAwarded > 0) sumXp = add(sumXp, xpAwarded);
    }

    public void countEncounter() { sumEncounters = addInt(sumEncounters); }

    /** A first catch of {@code id}: counted and kept among the notable catches. */
    public void countDiscovery(String id, double kg, long at) {
        sumSpecies = addInt(sumSpecies);
        note(new Notable(true, id, kg, at, true, false));
    }

    /** A new personal best of {@code id}: counted and kept among the notable catches. */
    public void countPersonalBest(String id, double kg, long at) {
        sumBests = addInt(sumBests);
        note(new Notable(false, id, kg, at, true, false));
    }

    private void note(Notable n) {
        if (!FishingRules.ID.matcher(n.species()).matches()) return;
        notable.add(n);
        while (notable.size() > MAX_NOTABLE) {
            int drop = 0; // the oldest read one, else the oldest
            for (int i = 0; i < notable.size(); i++) if (!notable.get(i).unread()) { drop = i; break; }
            notable.remove(drop);
        }
    }

    /** The notable catches, oldest first. */
    public List<Notable> notable() { return Collections.unmodifiableList(notable); }

    public int unreadNotable() {
        int n = 0;
        for (Notable x : notable) if (x.unread()) n++;
        return n;
    }

    /**
     * The player acknowledged the summary as it was shown: exactly {@code shown} is taken off the counters (never below
     * zero), and the notable catches that were unread then ({@code shownAt}, their times) are marked read. Anything that
     * came in after the screen was drawn stays unread.
     */
    public void acknowledge(Summary shown, Set<Long> shownAt) {
        if (shown != null) {
            sumCatches = Math.max(0, sumCatches - Math.max(0, shown.catches()));
            sumXp = Math.max(0, sumXp - Math.max(0, shown.xp()));
            sumSpecies = Math.max(0, sumSpecies - Math.max(0, shown.species()));
            sumBests = Math.max(0, sumBests - Math.max(0, shown.bests()));
            sumEncounters = Math.max(0, sumEncounters - Math.max(0, shown.encounters()));
        }
        if (shownAt == null || shownAt.isEmpty()) return;
        for (int i = 0; i < notable.size(); i++) {
            Notable n = notable.get(i);
            if (n.unread() && shownAt.contains(n.at())) notable.set(i, new Notable(n.discovery(), n.species(), n.kg(), n.at(), false, n.trophy()));
        }
    }

    /** The notable catch at {@code at} of {@code id} became a trophy: true the first time (one trophy per catch). */
    public boolean markTrophy(String id, long at) {
        for (int i = 0; i < notable.size(); i++) {
            Notable n = notable.get(i);
            if (n.at() == at && n.species().equals(id)) {
                if (n.trophy()) return false;
                notable.set(i, new Notable(n.discovery(), n.species(), n.kg(), n.at(), n.unread(), true));
                return true;
            }
        }
        return false;
    }

    private static long add(long a, long b) { return a > Long.MAX_VALUE - b ? Long.MAX_VALUE : a + b; }
    private static int addInt(int a) { return a == Integer.MAX_VALUE ? a : a + 1; }

    // ── collection rewards (granted once) ─────────────────────────

    public boolean granted(String reward) { return granted.contains(reward); }
    public Set<String> grantedRewards() { return Collections.unmodifiableSet(granted); }
    /** True the first time. */
    public boolean grant(String reward) { return FishingRules.ID.matcher(reward).matches() && granted.add(reward); }

    // ── saving ────────────────────────────────────────────────────

    /**
     * {@code 1|s=dace:0.42,trout:2.31|c=river_lurker|l=river_lurker:3|a=river_lurker:<uuid>:<millis>|f=stirs|t=trout|b=4
     * |r=wayfarer|v=12,340,1,2,0|n=D:trout:2.31:<millis>:u,P:dace:0.42:<millis>:t}}. Ids are plain. Parts a later version
     * added come back unchanged.
     */
    public String encode() {
        StringBuilder sb = new StringBuilder("1|s=");
        join(sb, species, v -> String.valueOf(v));
        sb.append("|c=").append(String.join(",", creatures));
        sb.append("|l=");
        join(sb, lures, String::valueOf);
        sb.append("|a=");
        if (active != null) sb.append(active.creature()).append(':').append(active.entity()).append(':').append(active.started());
        sb.append("|f=").append(String.join(",", flags));
        if (tracked != null) sb.append("|t=").append(tracked);
        if (bestsSet > 0) sb.append("|b=").append(bestsSet);
        if (!granted.isEmpty()) sb.append("|r=").append(String.join(",", granted));
        if (!summary().isEmpty()) sb.append("|v=").append(sumCatches).append(',').append(sumXp).append(',').append(sumSpecies)
                .append(',').append(sumBests).append(',').append(sumEncounters);
        if (!notable.isEmpty()) {
            sb.append("|n=");
            for (int i = 0; i < notable.size(); i++) {
                Notable n = notable.get(i);
                if (i > 0) sb.append(',');
                sb.append(n.discovery() ? 'D' : 'P').append(':').append(n.species()).append(':').append(n.kg()).append(':').append(n.at())
                        .append(':').append(n.unread() ? "u" : "").append(n.trophy() ? "t" : "");
            }
        }
        for (String u : unknown) sb.append('|').append(u);
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

    /**
     * True when the saved text came from a version this one can't read (another version marker): the journal is empty
     * here and must never be written back over that text (Fishing skips its saves and discovery bonuses).
     */
    public boolean readOnly() { return readOnly; }

    /**
     * Read {@link #encode}'s text; anything unreadable is skipped (never an exception). Null or blank: an empty journal.
     * Another version marker: an empty, {@link #readOnly} journal. Parts this version doesn't know are kept as they are;
     * a known part may carry more fields than this version reads (a later version must change the marker to change one).
     */
    public static FishingJournal decode(String text) {
        FishingJournal j = new FishingJournal();
        if (text == null || text.isBlank()) return j;
        String[] parts = text.split("\\|", -1);
        if (parts.length == 0 || !parts[0].equals("1")) { j.readOnly = true; return j; }
        List<Notable> notes = new ArrayList<>();
        for (int i = 1; i < parts.length; i++) {
            String p = parts[i];
            int eq = p.indexOf('=');
            if (eq < 1) continue;
            String body = p.substring(eq + 1);
            if (eq != 1 || "scflatbrvn".indexOf(p.charAt(0)) < 0) { // a later version's part: kept
                if (j.unknown.size() < 16 && p.length() <= 4096) j.unknown.add(p);
                continue;
            }
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
                case 't' -> { if (FishingRules.ID.matcher(body).matches()) j.tracked = body; }
                case 'b' -> j.bestsSet = Math.max(0, parseLong(body));
                case 'r' -> {
                    for (String e : body.split(",")) if (FishingRules.ID.matcher(e).matches()) j.granted.add(e);
                }
                case 'v' -> {
                    String[] v = body.split(",");
                    if (v.length >= 5) {
                        j.sumCatches = Math.max(0, parseLong(v[0]));
                        j.sumXp = Math.max(0, parseLong(v[1]));
                        j.sumSpecies = (int) Math.max(0, Math.min(Integer.MAX_VALUE, parseLong(v[2])));
                        j.sumBests = (int) Math.max(0, Math.min(Integer.MAX_VALUE, parseLong(v[3])));
                        j.sumEncounters = (int) Math.max(0, Math.min(Integer.MAX_VALUE, parseLong(v[4])));
                    }
                }
                case 'n' -> {
                    for (String e : body.split(",")) {
                        String[] f = e.split(":", -1);
                        if (f.length < 5 || !(f[0].equals("D") || f[0].equals("P")) || !FishingRules.ID.matcher(f[1]).matches()) continue;
                        try {
                            double kg = Double.parseDouble(f[2]);
                            long at = Long.parseLong(f[3]);
                            if (!Double.isFinite(kg) || kg < 0) continue;
                            notes.add(new Notable(f[0].equals("D"), f[1], kg, at, f[4].contains("u"), f[4].contains("t")));
                        } catch (NumberFormatException ignored) { /* skipped */ }
                    }
                }
                default -> { }
            }
        }
        // The newest MAX_NOTABLE (the list is oldest first).
        j.notable.addAll(notes.subList(Math.max(0, notes.size() - MAX_NOTABLE), notes.size()));
        return j;
    }

    private static long parseLong(String s) {
        try {
            return Long.parseLong(s.trim());
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
