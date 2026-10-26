package Gathering;

import org.bukkit.Material;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/** The tree a cut log belongs to (docs/design/WOODCUTTING.md §5, §6), on hand-built worlds. */
class TreeScanTest {

    private static final Set<Material> OAK = Set.of(Material.OAK_LOG);
    private static final Set<Material> OAK_LEAVES = Set.of(Material.OAK_LEAVES);
    private static final Set<Material> BUILDS = Set.of(Material.OAK_PLANKS, Material.GLASS);
    private static final TreeScan.Reach REACH = new TreeScan.Reach(7, 32, 1);

    /** A world in a map: blocks, leaves a player placed, positions the placed-block memory has, unreadable spots. */
    private static final class World implements TreeScan.View {
        final Map<Long, Material> blocks = new HashMap<>();
        final Set<Long> persistent = new HashSet<>(), placed = new HashSet<>(), unreadable = new HashSet<>();

        World set(int x, int y, int z, Material m) { blocks.put(TreeScan.pack(x, y, z), m); return this; }

        @Override public Material type(int x, int y, int z) {
            long k = TreeScan.pack(x, y, z);
            return unreadable.contains(k) ? null : blocks.getOrDefault(k, Material.AIR);
        }
        @Override public boolean naturalLeaf(int x, int y, int z) { return !persistent.contains(TreeScan.pack(x, y, z)); }
        @Override public boolean placed(int x, int y, int z) { return placed.contains(TreeScan.pack(x, y, z)); }

        /** An oak: a trunk of {@code height} logs on (x, 0, z) and a ring of leaves around its top two logs. */
        World oak(int x, int z, int height) {
            for (int y = 0; y < height; y++) set(x, y, z, Material.OAK_LOG);
            for (int y = height - 2; y <= height; y++) {
                for (int dx = -2; dx <= 2; dx++) for (int dz = -2; dz <= 2; dz++) {
                    if (blocks.get(TreeScan.pack(x + dx, y, z + dz)) == null) set(x + dx, y, z + dz, Material.OAK_LEAVES);
                }
            }
            return this;
        }
    }

    private static TreeScan.Tree scan(World w, int x, int y, int z, int max) {
        return TreeScan.scan(w, x, y, z, OAK, OAK_LEAVES, BUILDS, REACH, max);
    }

    @Test
    void aNaturalOakIsItsTrunkFromTheCutWithItsLeaves() {
        World w = new World().oak(0, 0, 5);
        TreeScan.Tree t = scan(w, 0, 0, 0, 64);
        assertEquals(5, t.size());
        assertEquals(TreeScan.pack(0, 0, 0), t.logs()[0], "the cut comes first");
        assertEquals(TreeScan.pack(0, 1, 0), t.logs()[1], "then the nearest");
        assertTrue(t.natural(4), t.toString());
        assertFalse(t.giant());
        assertFalse(t.capped());
        assertFalse(t.edge());
    }

    @Test
    void aLogBuildIsNoTree() {
        World w = new World();
        for (int x = 0; x < 6; x++) w.set(x, 0, 0, Material.OAK_LOG); // a log wall, no leaves
        TreeScan.Tree t = scan(w, 0, 0, 0, 64);
        assertEquals(6, t.size());
        assertFalse(t.natural(1), "no natural leaves: a build");
    }

    @Test
    void leavesAPlayerPlacedDontMakeABuildATree() {
        World w = new World();
        for (int y = 0; y < 4; y++) w.set(0, y, 0, Material.OAK_LOG);
        w.set(1, 3, 0, Material.OAK_LEAVES).set(-1, 3, 0, Material.OAK_LEAVES).set(0, 4, 0, Material.OAK_LEAVES);
        w.persistent.add(TreeScan.pack(1, 3, 0));
        w.persistent.add(TreeScan.pack(-1, 3, 0));
        w.persistent.add(TreeScan.pack(0, 4, 0));
        assertFalse(scan(w, 0, 0, 0, 64).natural(1));
        w.persistent.remove(TreeScan.pack(0, 4, 0)); // one the world grew
        assertTrue(scan(w, 0, 0, 0, 64).natural(1));
    }

    @Test
    void leavesCountAlongEdgesAndCornersAsLogsConnect() {
        World w = new World();
        for (int y = 0; y < 4; y++) w.set(0, y, 0, Material.OAK_LOG); // a stem under a cap, like a huge mushroom's
        for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) w.set(dx, 4, dz, Material.OAK_LEAVES);
        assertTrue(scan(w, 0, 0, 0, 64).natural(9), "the 3x3 cap on the top log: one above it, eight along its edges and corners");
    }

    @Test
    void aTreeABuildTouchesSaysSo() {
        World w = new World().oak(0, 0, 5);
        assertFalse(scan(w, 0, 0, 0, 64).build(), "a tree alone");
        w.set(2, 1, 0, Material.OAK_PLANKS); // a wall two blocks from the trunk: not touching
        assertFalse(scan(w, 0, 0, 0, 64).build());
        w.set(1, 1, 1, Material.OAK_PLANKS); // an old house's corner against it (diagonally)
        TreeScan.Tree t = scan(w, 0, 0, 0, 64);
        assertTrue(t.build(), t.toString());
        assertTrue(t.natural(4), "still a tree the world grew: Felling refuses it for the build, not for the leaves");
    }

    @Test
    void aTrunkIsNarrowAndLogsBuiltAgainstItWidenItsBase() {
        World w = new World().oak(0, 0, 6);
        assertEquals(1, scan(w, 0, 0, 0, 64).baseWidth(), "a single trunk");
        // An old palisade of unmarked logs, three high and five long, against the trunk: no planks, no torches.
        for (int x = 1; x <= 5; x++) for (int y = 0; y < 3; y++) w.set(x, y, 0, Material.OAK_LOG);
        TreeScan.Tree t = scan(w, 0, 0, 0, 64);
        assertTrue(t.natural(4) && !t.build(), "the tree's own leaves are there and no build block touches it");
        assertEquals(6, t.baseWidth(), "but its base is six logs wide");
        assertEquals(6, scan(w, 0, 3, 0, 64).baseWidth(), "cut just above the palisade: its top row is the lowest layer reached");
        assertEquals(1, scan(w, 0, 4, 0, 64).baseWidth(), "cut higher: the palisade is out of reach (one below the cut)");

        World giant = new World();
        for (int y = 0; y < 6; y++) for (int dx = 0; dx <= 1; dx++) for (int dz = 0; dz <= 1; dz++) giant.set(dx, y, dz, Material.OAK_LOG);
        giant.set(0, 6, 0, Material.OAK_LEAVES).set(1, 6, 1, Material.OAK_LEAVES);
        assertEquals(2, scan(giant, 0, 0, 0, 64).baseWidth(), "a 2x2 giant");
        assertEquals(0, TreeScan.baseWidth(new long[0]));
    }

    @Test
    void aPlacedLogIsNeitherTakenNorAWayThrough() {
        World w = new World().oak(0, 0, 6);
        w.placed.add(TreeScan.pack(0, 2, 0)); // a player's log in the middle of the trunk
        TreeScan.Tree t = scan(w, 0, 0, 0, 64);
        assertEquals(2, t.size(), "only the two logs below it");
        assertFalse(t.natural(1), "the leaves are past the placed log");
        assertSame(TreeScan.Tree.NONE, scan(w, 0, 2, 0, 64), "a placed log is no tree to start from");
    }

    @Test
    void aBuildTouchingATreeIsNotTakenWhenItsLogsWerePlaced() {
        World w = new World().oak(0, 0, 5);
        for (int x = 1; x <= 4; x++) { w.set(x, 0, 0, Material.OAK_LOG); w.placed.add(TreeScan.pack(x, 0, 0)); } // a fence of placed logs
        assertEquals(5, scan(w, 0, 0, 0, 64).size());
    }

    @Test
    void anotherWoodIsAnotherTree() {
        World w = new World().oak(0, 0, 4);
        w.set(1, 0, 0, Material.BIRCH_LOG).set(1, 1, 0, Material.BIRCH_LOG);
        assertEquals(4, scan(w, 0, 0, 0, 64).size());
    }

    @Test
    void aTooBigTreeComesBackCapped() {
        World w = new World().oak(0, 0, 30);
        TreeScan.Tree t = scan(w, 0, 0, 0, 10);
        assertEquals(10, t.size());
        assertTrue(t.capped());
        assertFalse(scan(w, 0, 0, 0, 30).capped(), "exactly its size is not capped");
    }

    @Test
    void theReachBoundsTheWalk() {
        World w = new World();
        for (int x = 0; x <= 10; x++) w.set(x, 0, 0, Material.OAK_LOG);
        assertEquals(8, scan(w, 0, 0, 0, 64).size(), "the cut and 7 sideways");
        World tall = new World();
        for (int y = -5; y <= 40; y++) tall.set(0, y, 0, Material.OAK_LOG);
        assertEquals(1 + 32 + 1, scan(tall, 0, 0, 0, 64).size(), "32 up and 1 down");
    }

    @Test
    void aGiantTrunkIsTwoByTwo() {
        World w = new World();
        for (int y = 0; y < 6; y++) for (int dx = 0; dx <= 1; dx++) for (int dz = 0; dz <= 1; dz++) w.set(dx, y, dz, Material.OAK_LOG);
        w.set(0, 6, 0, Material.OAK_LEAVES);
        TreeScan.Tree t = scan(w, 1, 0, 1, 64);
        assertEquals(24, t.size());
        assertTrue(t.giant());
        assertTrue(t.natural(1));
    }

    @Test
    void anUnreadableSpotIsAnEdgeAndNotWalkedInto() {
        World w = new World().oak(0, 0, 5);
        w.unreadable.add(TreeScan.pack(0, 3, 0)); // another region's block
        TreeScan.Tree t = scan(w, 0, 0, 0, 64);
        assertTrue(t.edge());
        assertTrue(t.size() < 5, t.toString());
    }

    @Test
    void notALogIsNoTree() {
        World w = new World().set(0, 0, 0, Material.STONE);
        assertSame(TreeScan.Tree.NONE, scan(w, 0, 0, 0, 64));
        assertSame(TreeScan.Tree.NONE, scan(new World().oak(0, 0, 4), 0, 0, 0, 0), "nothing allowed");
    }

    @Test
    void positionsPackBothWays() {
        int[][] cases = {{0, 0, 0}, {-1, -64, -1}, {30_000_000, 319, -30_000_000}, {-123_456, 2047, 654_321}, {5, -2048, -7}};
        for (int[] c : cases) {
            long p = TreeScan.pack(c[0], c[1], c[2]);
            assertEquals(c[0], TreeScan.x(p));
            assertEquals(c[1], TreeScan.y(p));
            assertEquals(c[2], TreeScan.z(p));
        }
    }
}
