package Gathering;

import java.util.Arrays;

/**
 * Block positions inside one chunk, packed into ints and kept as a sorted array: the form placed
 * resource blocks are saved in (chunk data) and looked up from. Pure, unit-tested.
 * Packing: {@code (y - minY) << 8 | (x & 15) << 4 | (z & 15)}, 17 bits for a 384-high world
 * (more for a taller one: the world's height decides what a saved value may be).
 */
public final class PlacedIndex {

    private PlacedIndex() {}

    public static int pack(int x, int y, int z, int minY) {
        return ((y - minY) << 8) | ((x & 15) << 4) | (z & 15);
    }

    /** The block's chunk-relative x (0-15). */
    public static int x(int packed) { return (packed >> 4) & 15; }

    /** The block's chunk-relative z (0-15). */
    public static int z(int packed) { return packed & 15; }

    /** The block's world y. */
    public static int y(int packed, int minY) { return (packed >>> 8) + minY; }

    public static boolean contains(int[] sorted, int v) {
        return sorted != null && Arrays.binarySearch(sorted, v) >= 0;
    }

    /** {@code sorted} with {@code v} added (the same array when it was there already). */
    public static int[] add(int[] sorted, int v) {
        if (sorted == null) return new int[] {v};
        int i = Arrays.binarySearch(sorted, v);
        if (i >= 0) return sorted;
        int at = -i - 1;
        int[] out = new int[sorted.length + 1];
        System.arraycopy(sorted, 0, out, 0, at);
        out[at] = v;
        System.arraycopy(sorted, at, out, at + 1, sorted.length - at);
        return out;
    }

    /** {@code sorted} without {@code v} (the same array when it wasn't there). */
    public static int[] remove(int[] sorted, int v) {
        if (sorted == null) return new int[0];
        int i = Arrays.binarySearch(sorted, v);
        if (i < 0) return sorted;
        int[] out = new int[sorted.length - 1];
        System.arraycopy(sorted, 0, out, 0, i);
        System.arraycopy(sorted, i + 1, out, i, sorted.length - i - 1);
        return out;
    }

    /** A saved array made safe: sorted, without duplicates or values outside a world {@code height} blocks high. */
    public static int[] clean(int[] saved, int height) {
        if (saved == null) return new int[0];
        long limit = (long) Math.max(0, height) << 8;
        int[] out = Arrays.stream(saved).filter(v -> v >= 0 && v < limit).sorted().distinct().toArray();
        return out.length == saved.length && Arrays.equals(out, saved) ? saved : out;
    }
}
