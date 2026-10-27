package Attributes;

import java.util.List;
import java.util.UUID;

/**
 * Something that gives a player attribute points: skills today; realms, gear, talismans, buffs
 * and manual allocation later. Combat never changes when a new source is added.
 *
 * <p>{@link #contributions} may be called from any thread, so it must only read thread-safe
 * state. A source whose inputs live on the player (equipped armor, potion effects) captures
 * them on the player's region thread when they change, and calls
 * {@link AttributeService#markDirty} so the snapshot is rebuilt.
 */
public interface AttributeSource {

    /** Stable id, also the prefix of its contributions' {@link Contribution#sourceId()}. */
    String id();

    List<Contribution> contributions(UUID player, AttributeRules rules);

    /**
     * What this source gives with {@code skill} at {@code level} instead of the player's own (a level-up's real gain,
     * "next level gives..."). Only a source whose contributions depend on a skill level (equipment parts that open at a
     * level) answers differently.
     */
    default List<Contribution> contributionsAt(UUID player, AttributeRules rules, String skill, int level) {
        return contributions(player, rules);
    }
}
