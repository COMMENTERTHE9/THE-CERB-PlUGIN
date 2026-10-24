package GUIs.menu;

import java.util.List;

/** Pure pagination math (unit-tested). Pages are 0-based. */
public final class Paging {

    private Paging() {}

    /** The standard content area of a 6-row menu: rows 1-4, columns 1-7 (28 slots). */
    public static final int[] CONTENT_SLOTS = {
            10, 11, 12, 13, 14, 15, 16,
            19, 20, 21, 22, 23, 24, 25,
            28, 29, 30, 31, 32, 33, 34,
            37, 38, 39, 40, 41, 42, 43
    };

    /** Number of pages for {@code total} items at {@code perPage} per page (at least 1). */
    public static int pageCount(int total, int perPage) {
        if (perPage <= 0) return 1;
        return Math.max(1, (total + perPage - 1) / perPage);
    }

    /** Clamp a requested page into range. */
    public static int clampPage(int page, int total, int perPage) {
        return Math.max(0, Math.min(page, pageCount(total, perPage) - 1));
    }

    /** The items shown on {@code page}. */
    public static <T> List<T> slice(List<T> items, int page, int perPage) {
        int p = clampPage(page, items.size(), perPage);
        int from = p * perPage;
        int to = Math.min(items.size(), from + perPage);
        return from >= to ? List.of() : items.subList(from, to);
    }
}
