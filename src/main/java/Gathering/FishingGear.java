package Gathering;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * One player's fishing equipment (docs/design/FISHING_EQUIPMENT.md §5, §8): the parts they own (each once, for good),
 * three presets naming which owned parts and bait slots they use, the active preset, the Bait Bag (up to three slots of
 * one bait each), the bait held on the line of the cast in progress, and two switches (the auto-hook reel, restocking).
 * Saved on the player next to the journal and the inventory, so a crash can't separate a part from the rod it came off
 * or a held bait from the bag. Pure and not thread-safe: used on the player's thread.
 *
 * <p>Presets name parts; they never make or copy one. Bait is held at a cast ({@link #hold}), and that hold ends exactly
 * once: {@link #consume} when a catch it applied to completes, {@link #release} otherwise (it goes back to the bag).
 */
public final class FishingGear {

    public static final int PRESETS = 3;
    public static final int SLOTS = 3;
    public static final List<String> DEFAULT_NAMES = List.of("Discoveries", "Treasure", "Sea Creatures");
    /** What a preset's name may hold: letters, digits, spaces and a few marks, 1-20 characters. */
    static final Pattern NAME = Pattern.compile("[A-Za-z0-9 '&.!-]{1,20}");

    /** A preset: its name, the parts it uses, and which bag slots it may take bait from (in slot order). */
    public static final class Preset {
        private String name;
        private final Set<String> parts = new LinkedHashSet<>();
        private final boolean[] slots = {true, true, true};

        Preset(String name) { this.name = name; }

        public String name() { return name; }
        public Set<String> parts() { return Collections.unmodifiableSet(parts); }
        public boolean usesSlot(int i) { return i >= 0 && i < SLOTS && slots[i]; }
    }

    /** One bag slot: which bait and how many (an empty slot has none). */
    public record Slot(String bait, int count) {
        public static final Slot EMPTY = new Slot(null, 0);

        public boolean empty() { return bait == null || count <= 0; }
    }

    /** The bait held on the line of the cast {@code hook}: which bait, from which slot. */
    public record Held(String bait, int slot, UUID hook) {}

    private final Set<String> owned = new LinkedHashSet<>();
    private final Preset[] presets = new Preset[PRESETS];
    private final Slot[] bag = {Slot.EMPTY, Slot.EMPTY, Slot.EMPTY};
    private final List<String> unknown = new ArrayList<>();
    private int active;
    private Held held;
    private boolean autoReel = true, restock;

    public FishingGear() {
        for (int i = 0; i < PRESETS; i++) presets[i] = new Preset(DEFAULT_NAMES.get(i));
    }

    // ── parts ─────────────────────────────────────────────────────

    public boolean owns(String part) { return owned.contains(part); }
    public Set<String> owned() { return Collections.unmodifiableSet(owned); }

    /** True the first time (a part is owned once). */
    public boolean own(String part) { return FishingRules.ID.matcher(part).matches() && owned.add(part); }

    /** An admin took a part away: it leaves every preset too. True when it was owned. */
    public boolean disown(String part) {
        if (!owned.remove(part)) return false;
        for (Preset p : presets) p.parts.remove(part);
        return true;
    }

    public int activeIndex() { return active; }
    public Preset preset(int i) { return presets[Math.max(0, Math.min(PRESETS - 1, i))]; }
    public Preset activePreset() { return presets[active]; }

    /** Switch to preset {@code i} (0-2): free, it counts from the next cast. False when out of range. */
    public boolean activate(int i) {
        if (i < 0 || i >= PRESETS) return false;
        active = i;
        return true;
    }

    /** Put an owned part into preset {@code i}. False when it isn't owned or already there. */
    public boolean install(int i, String part) {
        if (i < 0 || i >= PRESETS || !owned.contains(part)) return false;
        return presets[i].parts.add(part);
    }

    public boolean uninstall(int i, String part) {
        return i >= 0 && i < PRESETS && presets[i].parts.remove(part);
    }

    /** Rename preset {@code i}; colour codes and anything else outside {@link #NAME} are refused. */
    public boolean rename(int i, String name) {
        if (i < 0 || i >= PRESETS || name == null) return false;
        String n = name.trim();
        if (!NAME.matcher(n).matches()) return false;
        presets[i].name = n;
        return true;
    }

    public static boolean validName(String name) { return name != null && NAME.matcher(name.trim()).matches(); }

    /** Whether preset {@code i} takes bait from bag slot {@code slot}. */
    public void useSlot(int i, int slot, boolean use) {
        if (i >= 0 && i < PRESETS && slot >= 0 && slot < SLOTS) presets[i].slots[slot] = use;
    }

    // ── switches ──────────────────────────────────────────────────

    public boolean autoReel() { return autoReel; }
    public void autoReel(boolean on) { autoReel = on; }
    public boolean restock() { return restock; }
    public void restock(boolean on) { restock = on; }

    // ── the Bait Bag ──────────────────────────────────────────────

    public Slot slot(int i) { return i >= 0 && i < SLOTS ? bag[i] : Slot.EMPTY; }

    /**
     * Put up to {@code n} of {@code bait} into slot {@code i}, up to {@code max} in the slot; a slot holds one bait.
     * Returns how many went in (0 when the slot holds another bait).
     */
    public int load(int i, String bait, int n, int max) {
        if (i < 0 || i >= SLOTS || n <= 0 || bait == null || !FishingRules.ID.matcher(bait).matches()) return 0;
        Slot s = bag[i];
        if (!s.empty() && !s.bait().equals(bait)) return 0;
        int have = s.empty() ? 0 : s.count();
        int add = Math.max(0, Math.min(n, max - have));
        if (add > 0) bag[i] = new Slot(bait, have + add);
        return add;
    }

    /** Take up to {@code n} out of slot {@code i}; returns how many came out. */
    public int take(int i, int n) {
        if (i < 0 || i >= SLOTS || n <= 0) return 0;
        Slot s = bag[i];
        if (s.empty()) return 0;
        int out = Math.min(n, s.count());
        bag[i] = s.count() - out <= 0 ? Slot.EMPTY : new Slot(s.bait(), s.count() - out);
        return out;
    }

    /** Swap two slots (the order is the fallback order). */
    public void swap(int a, int b) {
        if (a < 0 || b < 0 || a >= SLOTS || b >= SLOTS || a == b) return;
        Slot t = bag[a];
        bag[a] = bag[b];
        bag[b] = t;
    }

    public Held held() { return held; }

    /**
     * Hold one bait from slot {@code i} on the line of cast {@code hook}: it leaves the bag now, so taking bait out or
     * switching presets during the cast can't keep its effect for free. False when the slot is empty or a hold is open.
     */
    public boolean hold(int i, UUID hook) {
        if (held != null || hook == null || i < 0 || i >= SLOTS || bag[i].empty()) return false;
        String bait = bag[i].bait();
        take(i, 1);
        held = new Held(bait, i, hook);
        return true;
    }

    /** The catch the held bait applied to completed: it is used up. True when {@code hook}'s hold ended here. */
    public boolean consume(UUID hook) {
        if (held == null || !held.hook().equals(hook)) return false;
        held = null;
        return true;
    }

    /** {@link #release(UUID, int)} with every slot open. */
    public String release(UUID hook) { return release(hook, SLOTS); }

    /**
     * The cast ended without using the held bait ({@code hook}, or any when null: a restart): it goes back to its slot if
     * that still holds the same bait, else to an open slot (the first {@code open}) with the same bait, else to an empty
     * open slot. Returns the bait when it could go nowhere in the bag (the caller hands it over as an item), "" when it
     * went back, null when nothing was held.
     */
    public String release(UUID hook, int open) {
        if (held == null || (hook != null && !held.hook().equals(hook))) return null;
        Held h = held;
        held = null;
        int n = Math.max(1, Math.min(SLOTS, open));
        if (back(h.slot(), h.bait())) return "";
        for (int i = 0; i < n; i++) if (back(i, h.bait())) return "";
        for (int i = 0; i < n; i++) if (bag[i].empty()) { bag[i] = new Slot(h.bait(), 1); return ""; }
        return h.bait();
    }

    private boolean back(int i, String bait) {
        Slot s = bag[i];
        if (s.empty() || !s.bait().equals(bait) || s.count() == Integer.MAX_VALUE) return false;
        bag[i] = new Slot(bait, s.count() + 1); // may go one over the stack: a refund is never refused
        return true;
    }

    // ── saving ────────────────────────────────────────────────────

    /**
     * {@code 1|o=part,part|a=0|p0=Name;part,part;111|p1=...|p2=...|b=bait:12,,bait:3|h=bait:0:<uuid>|s=rs}: owned parts,
     * the active preset, each preset (name, parts, the slots it uses), the bag, the held bait, the switches (r = reel on,
     * s = restock on). Parts a later version added come back unchanged.
     */
    public String encode() {
        StringBuilder sb = new StringBuilder("1|o=").append(String.join(",", owned));
        sb.append("|a=").append(active);
        for (int i = 0; i < PRESETS; i++) {
            Preset p = presets[i];
            sb.append("|p").append(i).append('=').append(p.name).append(';').append(String.join(",", p.parts)).append(';');
            for (int s = 0; s < SLOTS; s++) sb.append(p.slots[s] ? '1' : '0');
        }
        sb.append("|b=");
        for (int i = 0; i < SLOTS; i++) {
            if (i > 0) sb.append(',');
            if (!bag[i].empty()) sb.append(bag[i].bait()).append(':').append(bag[i].count());
        }
        if (held != null) sb.append("|h=").append(held.bait()).append(':').append(held.slot()).append(':').append(held.hook());
        sb.append("|s=").append(autoReel ? "r" : "").append(restock ? "s" : "");
        for (String u : unknown) sb.append('|').append(u);
        return sb.toString();
    }

    /** Read {@link #encode}'s text; anything unreadable is skipped. Null or blank: a new player's equipment. */
    public static FishingGear decode(String text) {
        FishingGear g = new FishingGear();
        if (text == null || text.isBlank()) return g;
        String[] parts = text.split("\\|", -1);
        if (parts.length == 0 || !parts[0].equals("1")) throw new IllegalArgumentException("not a version-1 equipment record");
        for (int i = 1; i < parts.length; i++) {
            String p = parts[i];
            int eq = p.indexOf('=');
            if (eq < 1) continue;
            String key = p.substring(0, eq), body = p.substring(eq + 1);
            switch (key) {
                case "o" -> { for (String id : body.split(",")) if (FishingRules.ID.matcher(id).matches()) g.owned.add(id); }
                case "a" -> { try { g.active = Math.max(0, Math.min(PRESETS - 1, Integer.parseInt(body))); } catch (NumberFormatException ignored) { } }
                case "p0", "p1", "p2" -> {
                    Preset pr = g.presets[key.charAt(1) - '0'];
                    String[] f = body.split(";", -1);
                    if (f.length >= 1 && NAME.matcher(f[0]).matches()) pr.name = f[0];
                    if (f.length >= 2) for (String id : f[1].split(",")) if (FishingRules.ID.matcher(id).matches()) pr.parts.add(id);
                    if (f.length >= 3) for (int s = 0; s < SLOTS && s < f[2].length(); s++) pr.slots[s] = f[2].charAt(s) != '0';
                }
                case "b" -> {
                    String[] slots = body.split(",", -1);
                    for (int s = 0; s < SLOTS && s < slots.length; s++) {
                        String[] kv = slots[s].split(":");
                        if (kv.length != 2 || !FishingRules.ID.matcher(kv[0]).matches()) continue;
                        try {
                            int n = Integer.parseInt(kv[1]);
                            if (n > 0) g.bag[s] = new Slot(kv[0], n);
                        } catch (NumberFormatException ignored) { }
                    }
                }
                case "h" -> {
                    String[] h = body.split(":");
                    if (h.length == 3 && FishingRules.ID.matcher(h[0]).matches()) {
                        try {
                            g.held = new Held(h[0], Math.max(0, Math.min(SLOTS - 1, Integer.parseInt(h[1]))), UUID.fromString(h[2]));
                        } catch (IllegalArgumentException ignored) { }
                    }
                }
                case "s" -> { g.autoReel = body.contains("r"); g.restock = body.contains("s"); }
                default -> { if (g.unknown.size() < 16 && p.length() <= 4096) g.unknown.add(p); }
            }
        }
        // A preset may only name parts the player owns (an edited save never makes a part appear).
        for (Preset pr : g.presets) pr.parts.retainAll(g.owned);
        return g;
    }
}
