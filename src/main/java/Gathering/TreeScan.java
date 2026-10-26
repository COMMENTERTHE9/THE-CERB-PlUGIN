package Gathering;

import org.bukkit.Material;

import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * The natural tree a cut log belongs to (docs/design/WOODCUTTING.md §5, §6): from the cut, the connected logs of its
 * wood (touching in any of the 26 directions) inside a box around the cut, never a placed log and never past one, in
 * breadth-first order; how many of the tree's natural leaves they touch (in the same 26 directions: a mushroom's cap
 * sits on its stem's top, an acacia's leaves beside its branches); whether a build touches it; how wide its base is;
 * whether its trunk is a giant's (2×2). Pure: the world is read through {@link View}, so unit tests build trees from a map.
 */
public final class TreeScan {

    private TreeScan() {}

    /** The world as a scan sees it. Implementations read only what the current thread may read (Folia: its region). */
    public interface View {
        /** The block at a position, or null when it can't be read now (an unloaded chunk, another region's). */
        Material type(int x, int y, int z);

        /** True for a leaf the world grew (vanilla's "persistent" off); blocks without that flag count as natural. */
        boolean naturalLeaf(int x, int y, int z);

        /** True when the placed-block memory says this log pays nothing (placed by hand, moved by a piston); a grown tree's logs are false. */
        boolean placed(int x, int y, int z);
    }

    /** How far from the cut a scan looks: sideways (x and z), up and down. */
    public record Reach(int sideways, int up, int down) {
        public Reach {
            sideways = Math.max(0, Math.min(16, sideways));
            up = Math.max(0, Math.min(64, up));
            down = Math.max(0, Math.min(8, down));
        }
    }

    /**
     * What a scan found.
     *
     * @param logs          the tree's logs as packed positions ({@link #pack}), breadth-first from the cut (the cut first)
     * @param naturalLeaves natural leaves of the tree's kind touching its logs (counted up to {@link #LEAF_COUNT_LIMIT})
     * @param giant         its trunk is 2×2 where it was cut (dark oak, pale oak, giant spruce and jungle trees)
     * @param capped        it has more logs than the scan was allowed to collect
     * @param edge          part of the box couldn't be read (another region's chunk, or one not loaded)
     * @param build         a block of a build (planks, glass, a door...) touches one of its logs: an old log house or a
     *                      village joined to a natural tree, whose logs no placed-block memory has
     * @param baseWidth     how wide its lowest layer of logs is (x or z, the larger): a trunk is 1 or 2 wide, a log wall
     *                      or a palisade built against it is wider
     */
    public record Tree(long[] logs, int naturalLeaves, boolean giant, boolean capped, boolean edge, boolean build, int baseWidth) {
        public static final Tree NONE = new Tree(new long[0], 0, false, false, false, false, 0);

        public int size() { return logs.length; }

        /** True when it is a tree the world grew: at least {@code minLeaves} natural leaves touch its logs. */
        public boolean natural(int minLeaves) { return logs.length > 0 && naturalLeaves >= Math.max(1, minLeaves); }

        @Override public boolean equals(Object o) {
            return o instanceof Tree t && Arrays.equals(logs, t.logs) && naturalLeaves == t.naturalLeaves && giant == t.giant
                    && capped == t.capped && edge == t.edge && build == t.build && baseWidth == t.baseWidth;
        }

        @Override public int hashCode() { return Arrays.hashCode(logs) * 31 + naturalLeaves; }

        @Override public String toString() {
            return "Tree[logs=" + logs.length + ", naturalLeaves=" + naturalLeaves + ", giant=" + giant + ", capped=" + capped + ", edge=" + edge
                    + ", build=" + build + ", baseWidth=" + baseWidth + "]";
        }
    }

    /** Natural leaves are counted up to this many: enough to tell a tree from a build, without walking a whole canopy. */
    public static final int LEAF_COUNT_LIMIT = 16;

    /**
     * Scan from the log at ({@code x0}, {@code y0}, {@code z0}).
     *
     * @param logs    the wood's log blocks (a tree is made of one wood: an oak never takes a birch's logs)
     * @param leaves  the wood's leaf blocks
     * @param builds  blocks that only builds have (planks, glass, doors...); one touching a log marks the tree {@code build}
     * @param maxLogs the most logs to collect; a bigger tree comes back {@code capped}, with this many
     * @return {@link Tree#NONE} when the cut isn't a log of the wood, or was placed
     */
    public static Tree scan(View v, int x0, int y0, int z0, Set<Material> logs, Set<Material> leaves, Set<Material> builds, Reach reach,
                            int maxLogs) {
        Material start = v.type(x0, y0, z0);
        if (start == null || !logs.contains(start) || v.placed(x0, y0, z0) || maxLogs <= 0) return Tree.NONE;
        long[] found = new long[Math.min(maxLogs, 1024)];
        int n = 0;
        Set<Long> seen = new HashSet<>();
        Set<Long> leafSeen = new HashSet<>();
        ArrayDeque<long[]> queue = new ArrayDeque<>();
        long first = pack(x0, y0, z0);
        seen.add(first);
        queue.add(new long[] {x0, y0, z0});
        int leavesFound = 0;
        boolean capped = false, edge = false, build = false;
        while (!queue.isEmpty()) {
            long[] c = queue.poll();
            int x = (int) c[0], y = (int) c[1], z = (int) c[2];
            if (n == found.length) { capped = true; break; }
            found[n++] = pack(x, y, z);
            if (leavesFound < LEAF_COUNT_LIMIT) {
                leaf:
                for (int dx = -1; dx <= 1; dx++) for (int dy = -1; dy <= 1; dy++) for (int dz = -1; dz <= 1; dz++) {
                    if (dx == 0 && dy == 0 && dz == 0) continue;
                    int lx = x + dx, ly = y + dy, lz = z + dz;
                    if (!leafSeen.add(pack(lx, ly, lz))) continue;
                    Material m = v.type(lx, ly, lz);
                    if (m == null) { edge = true; continue; }
                    if (leaves.contains(m) && v.naturalLeaf(lx, ly, lz) && ++leavesFound >= LEAF_COUNT_LIMIT) break leaf;
                }
            }
            for (int dx = -1; dx <= 1; dx++) for (int dy = -1; dy <= 1; dy++) for (int dz = -1; dz <= 1; dz++) {
                if (dx == 0 && dy == 0 && dz == 0) continue;
                int nx = x + dx, ny = y + dy, nz = z + dz;
                if (Math.abs(nx - x0) > reach.sideways() || Math.abs(nz - z0) > reach.sideways() || ny > y0 + reach.up() || ny < y0 - reach.down()) continue;
                long key = pack(nx, ny, nz);
                if (!seen.add(key)) continue;
                Material m = v.type(nx, ny, nz);
                if (m == null) { edge = true; continue; }
                if (builds.contains(m)) build = true;
                if (!logs.contains(m) || v.placed(nx, ny, nz)) continue; // a placed log is never part of a tree, nor a way through
                queue.add(new long[] {nx, ny, nz});
            }
        }
        long[] out = Arrays.copyOf(found, n);
        return new Tree(out, leavesFound, giant(out, y0), capped, edge, build, baseWidth(out));
    }

    /** How wide the lowest layer of these logs is: the larger of its x and z extents (0 for none). */
    static int baseWidth(long[] logs) {
        if (logs.length == 0) return 0;
        int low = Integer.MAX_VALUE;
        for (long l : logs) low = Math.min(low, y(l));
        int minX = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE, minZ = Integer.MAX_VALUE, maxZ = Integer.MIN_VALUE;
        for (long l : logs) {
            if (y(l) != low) continue;
            minX = Math.min(minX, x(l)); maxX = Math.max(maxX, x(l));
            minZ = Math.min(minZ, z(l)); maxZ = Math.max(maxZ, z(l));
        }
        return Math.max(maxX - minX, maxZ - minZ) + 1;
    }

    /** True when the logs hold a 2×2 square at the cut's height or one above (a giant's trunk). */
    static boolean giant(long[] logs, int y0) {
        Set<Long> at = new HashSet<>();
        for (long l : logs) at.add(l);
        for (long l : logs) {
            int y = y(l);
            if (y != y0 && y != y0 + 1) continue;
            int x = x(l), z = z(l);
            if (at.contains(pack(x + 1, y, z)) && at.contains(pack(x, y, z + 1)) && at.contains(pack(x + 1, y, z + 1))) return true;
        }
        return false;
    }

    // ── packed positions: x and z 26 bits each, y 12 bits (vanilla's own layout) ──

    public static long pack(int x, int y, int z) {
        return ((long) (x & 0x3FFFFFF) << 38) | ((long) (z & 0x3FFFFFF) << 12) | (y & 0xFFFL);
    }

    public static int x(long p) { return (int) (p >> 38); }

    public static int y(long p) { return (int) (p << 52 >> 52); }

    public static int z(long p) { return (int) (p << 26 >> 38); }
}
