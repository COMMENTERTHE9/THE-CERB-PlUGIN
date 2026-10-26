package Gathering;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** One chunk's marks by origin (docs/design/WOODCUTTING.md §5): a position is in at most one set. */
class ChunkMarksTest {

    @Test
    void aPositionHasOneOriginAtATime() {
        int a = PlacedIndex.pack(3, 70, 5, -64), b = PlacedIndex.pack(4, 70, 5, -64);
        ChunkMarks m = ChunkMarks.NONE;
        assertEquals(Origin.NATURAL, m.origin(a), "nothing marked: natural");
        m = m.with(a, Origin.GROWN);
        assertEquals(Origin.GROWN, m.origin(a));
        assertEquals(Origin.NATURAL, m.origin(b), "a neighbour is untouched");
        m = m.with(a, Origin.PLACED);
        assertEquals(Origin.PLACED, m.origin(a), "a grown log moved by a piston, or broken and placed again: placed");
        assertEquals(0, m.grown().length, "and no longer grown");
        m = m.with(b, Origin.BONEMEAL).with(a, Origin.NATURAL);
        assertEquals(Origin.NATURAL, m.origin(a), "broken: forgotten");
        assertEquals(Origin.BONEMEAL, m.origin(b));
        assertArrayEquals(new int[0], m.placed());
        assertArrayEquals(new int[] {b}, m.of(Origin.BONEMEAL));
        assertArrayEquals(new int[0], m.of(Origin.NATURAL));
    }

    @Test
    void anUnchangedMarkIsTheSameValueSoNothingIsWritten() {
        int a = PlacedIndex.pack(0, 0, 0, -64);
        ChunkMarks m = ChunkMarks.NONE.with(a, Origin.PLACED);
        assertSame(m, m.with(a, Origin.PLACED));
        assertSame(ChunkMarks.NONE, ChunkMarks.NONE.with(a, Origin.NATURAL));
        ChunkMarks g = m.with(a, Origin.GROWN);
        assertNotSame(m, g);
        assertSame(m.boneMeal(), g.boneMeal(), "a set that didn't change is the same array (only changed sets are written)");
    }

    @Test
    void onlyAnExplosionThatDestroysBlocksForgetsThem() {
        assertTrue(PlacedBlocks.destroys(org.bukkit.ExplosionResult.DESTROY), "TNT, a creeper");
        assertTrue(PlacedBlocks.destroys(org.bukkit.ExplosionResult.DESTROY_WITH_DECAY));
        assertFalse(PlacedBlocks.destroys(org.bukkit.ExplosionResult.TRIGGER_BLOCK), "a wind charge leaves every block where it was");
        assertFalse(PlacedBlocks.destroys(org.bukkit.ExplosionResult.KEEP));
        assertFalse(PlacedBlocks.destroys(null));
    }

    @Test
    void savedSetsStaySortedAndEmptyIsEmpty() {
        ChunkMarks m = ChunkMarks.NONE;
        for (int v : new int[] {900, 5, 300, 5, 70}) m = m.with(v, Origin.GROWN);
        assertArrayEquals(new int[] {5, 70, 300, 900}, m.grown());
        assertFalse(m.isEmpty());
        assertTrue(new ChunkMarks(null, null, null).isEmpty(), "nothing saved: no marks");
        assertTrue(ChunkMarks.NONE.isEmpty());
    }
}
