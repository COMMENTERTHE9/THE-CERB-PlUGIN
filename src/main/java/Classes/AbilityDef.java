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
 * @param strikeCooldownMs a movement ability's strike and stun come back on this clock (0 = with every
 *                   cast); a cast in between only moves you
 * @param groups     shared cooldown groups it starts (e.g. "movement": every dash and blink)
 * @param exclusive  tags of which only one equipped ability may carry each ("stance", "aura")
 * @param effects    what an active does when cast, in order
 * @param passives   what a passive or keystone adds while equipped (through the attribute engine)
 */
public record AbilityDef(String id, String classId, String displayName, String description, Kind kind,
                         int unlockRank, Set<WeaponFamily> weapons, double essenceCost, long cooldownMs,
                         long strikeCooldownMs, Set<String> groups, Set<String> exclusive, List<EffectSpec> effects,
                         List<Passive> passives) {

    public enum Kind { ACTIVE, PASSIVE, KEYSTONE }

    /** A bonus while equipped: exactly one of {@code attribute} (points) / {@code stat} (a fraction, 0.05 = +5%). */
    public record Passive(Attribute attribute, Stat stat, double amount) {}

    public boolean isActive() { return kind == Kind.ACTIVE; }

    /** A movement ability: an active that dashes or blinks (owner rule: back in 1.2 s at most, {@link ClassCatalog#MOVEMENT_MAX_COOLDOWN_MS}). */
    public boolean isMovement() { return isActive() && effects.stream().anyMatch(AbilityDef::moves); }

    /** True for the effects that move you (a dash's stun aside, which is part of its strike). */
    static boolean moves(EffectSpec e) { return e instanceof EffectSpec.Dash || e instanceof EffectSpec.Blink; }

    /** True when a cast does more than move you: a strike, a status, a buff... or a dash's stun. */
    static boolean strikes(List<EffectSpec> effects) {
        return effects.stream().anyMatch(e -> !moves(e) || (e instanceof EffectSpec.Dash d && d.stunSeconds() > 0));
    }
}
