package Gathering;

/**
 * One chunk's marked resource blocks by {@link Origin}: three sorted sets of positions packed by {@link PlacedIndex},
 * one per kind of mark. A position is in at most one of them; one in none is natural. Immutable: a change returns a new
 * value (the same one when nothing changed, so the caller knows not to write). Pure, unit-tested.
 */
public record ChunkMarks(int[] placed, int[] grown, int[] boneMeal) {

    private static final int[] EMPTY = new int[0];
    public static final ChunkMarks NONE = new ChunkMarks(EMPTY, EMPTY, EMPTY);

    public ChunkMarks {
        placed = placed == null ? EMPTY : placed;
        grown = grown == null ? EMPTY : grown;
        boneMeal = boneMeal == null ? EMPTY : boneMeal;
    }

    /** Where the block at packed position {@code v} came from. */
    public Origin origin(int v) {
        if (PlacedIndex.contains(placed, v)) return Origin.PLACED;
        if (PlacedIndex.contains(grown, v)) return Origin.GROWN;
        if (PlacedIndex.contains(boneMeal, v)) return Origin.BONEMEAL;
        return Origin.NATURAL;
    }

    /** These marks with {@code v} marked {@code o} and in no other set ({@link Origin#NATURAL}: in none). */
    public ChunkMarks with(int v, Origin o) {
        int[] p = o == Origin.PLACED ? PlacedIndex.add(placed, v) : PlacedIndex.remove(placed, v);
        int[] g = o == Origin.GROWN ? PlacedIndex.add(grown, v) : PlacedIndex.remove(grown, v);
        int[] b = o == Origin.BONEMEAL ? PlacedIndex.add(boneMeal, v) : PlacedIndex.remove(boneMeal, v);
        return p == placed && g == grown && b == boneMeal ? this : new ChunkMarks(p, g, b);
    }

    /** The set kept for {@code o} ({@link Origin#NATURAL} has none). */
    public int[] of(Origin o) {
        return switch (o) {
            case PLACED -> placed;
            case GROWN -> grown;
            case BONEMEAL -> boneMeal;
            case NATURAL -> EMPTY;
        };
    }

    public boolean isEmpty() { return placed.length == 0 && grown.length == 0 && boneMeal.length == 0; }
}
