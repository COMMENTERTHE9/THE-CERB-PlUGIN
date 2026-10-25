package Classes;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

/**
 * What a player has equipped (docs/design/CLASSES.md §2.3, §5.5): {@link #ACTIVE_SLOTS} actives
 * (cast with Sneak + right-click, left-click, F, Q or scroll), {@link #PASSIVE_SLOTS} passives and
 * one keystone, from any learned classes. Empty slots are null. Immutable; changes make a new loadout.
 */
public record Loadout(String name, List<String> actives, List<String> passives, String keystone) {

    public static final int ACTIVE_SLOTS = 5, PASSIVE_SLOTS = 3;

    public Loadout {
        actives = fixed(actives, ACTIVE_SLOTS);
        passives = fixed(passives, PASSIVE_SLOTS);
        name = name == null || name.isBlank() ? "Loadout" : name.strip();
    }

    public static Loadout empty(String name) { return new Loadout(name, List.of(), List.of(), null); }

    /** Every equipped ability id, actives then passives then the keystone. */
    public List<String> equipped() {
        List<String> out = new ArrayList<>();
        for (String a : actives) if (a != null) out.add(a);
        for (String p : passives) if (p != null) out.add(p);
        if (keystone != null) out.add(keystone);
        return out;
    }

    public boolean contains(String abilityId) { return equipped().contains(abilityId); }

    public Loadout withActive(int slot, String id) {
        List<String> a = new ArrayList<>(actives);
        a.set(slot, id);
        return new Loadout(name, a, passives, keystone);
    }

    public Loadout withPassive(int slot, String id) {
        List<String> p = new ArrayList<>(passives);
        p.set(slot, id);
        return new Loadout(name, actives, p, keystone);
    }

    public Loadout withKeystone(String id) { return new Loadout(name, actives, passives, id); }

    /**
     * Why this loadout can't be in use, in plain words (empty = fine). The whole rule list: each
     * slot holds the right kind, everything is unlocked, nothing twice, and at most one equipped
     * ability per exclusive tag. Crossing class boundaries is never a rule. An id that isn't in
     * {@code abilities} (gone from abilities.yml for now) is inert, not a problem: it does nothing
     * and is kept, so fixing the file brings it back.
     */
    public List<String> problems(Map<String, AbilityDef> abilities, Predicate<AbilityDef> unlocked) {
        List<String> out = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        Map<String, String> tagOwner = new java.util.HashMap<>();
        check(actives, AbilityDef.Kind.ACTIVE, "an active slot", abilities, unlocked, seen, tagOwner, out);
        check(passives, AbilityDef.Kind.PASSIVE, "a passive slot", abilities, unlocked, seen, tagOwner, out);
        check(Collections.singletonList(keystone), AbilityDef.Kind.KEYSTONE, "the keystone slot", abilities, unlocked, seen, tagOwner, out);
        return out;
    }

    private static void check(List<String> ids, AbilityDef.Kind kind, String where, Map<String, AbilityDef> abilities,
                              Predicate<AbilityDef> unlocked, Set<String> seen, Map<String, String> tagOwner, List<String> out) {
        for (String id : ids) {
            if (id == null) continue;
            AbilityDef a = abilities.get(id);
            if (a == null) continue; // inert (see above)
            if (a.kind() != kind) out.add(a.displayName() + " can't go in " + where);
            if (!unlocked.test(a)) out.add(a.displayName() + " is not unlocked yet");
            if (!seen.add(id)) out.add(a.displayName() + " is equipped twice");
            for (String tag : a.exclusive()) {
                String other = tagOwner.putIfAbsent(tag, a.displayName());
                if (other != null && !other.equals(a.displayName())) out.add("Only one " + tag + " at a time: " + other + " and " + a.displayName());
            }
        }
    }

    private static List<String> fixed(List<String> in, int size) {
        List<String> out = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            String v = in != null && i < in.size() ? in.get(i) : null;
            out.add(v == null || v.isBlank() ? null : v);
        }
        return Collections.unmodifiableList(out);
    }
}
