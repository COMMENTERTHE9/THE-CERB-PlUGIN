package Classes;

import Attributes.Attribute;
import Attributes.Stat;

import java.util.List;
import java.util.Set;

/**
 * One ability of a class, as data (abilities.yml; docs/design/CLASSES.md §5.1). Unlocked at
 * {@code unlockRank} of its class and kept forever; it only does something while equipped in the
 * active loadout.
 *
 * @param weapons    families that can use it (empty = any); can still be equipped without one
 * @param groups     shared cooldown groups it starts (e.g. "movement": every dash and blink)
 * @param exclusive  tags of which only one equipped ability may carry each ("stance", "aura")
 * @param effects    what an active does when cast, in order
 * @param passives   what a passive or keystone adds while equipped (through the attribute engine)
 */
public record AbilityDef(String id, String classId, String displayName, String description, Kind kind,
                         int unlockRank, Set<WeaponFamily> weapons, double essenceCost, long cooldownMs,
                         Set<String> groups, Set<String> exclusive, List<EffectSpec> effects,
                         List<Passive> passives) {

    public enum Kind { ACTIVE, PASSIVE, KEYSTONE }

    /** A bonus while equipped: exactly one of {@code attribute} (points) / {@code stat} (a fraction, 0.05 = +5%). */
    public record Passive(Attribute attribute, Stat stat, double amount) {}

    public boolean isActive() { return kind == Kind.ACTIVE; }
}
