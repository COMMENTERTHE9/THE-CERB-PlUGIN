package Gathering;

/**
 * Where a resource block came from, as the placed-block memory knows it (docs/design/WOODCUTTING.md §5). A block
 * the memory has no mark for is {@link #NATURAL}: the world made it, or it was there before the memory was.
 */
public enum Origin {
    /** Generated with the world (or older than the memory): pays in full. */
    NATURAL,
    /** Placed by a player, formed by a generator, moved by a piston, set by a mob: pays nothing. */
    PLACED,
    /** Part of a tree that grew on its own from a sapling, fungus or propagule a player planted. */
    GROWN,
    /** Part of a tree that bone meal finished growing. */
    BONEMEAL
}
