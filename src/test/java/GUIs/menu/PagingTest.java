package GUIs.menu;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PagingTest {

    private static final List<Integer> SKILLS = IntStream.range(0, 35).boxed().toList();

    @Test
    void pageCounts() {
        assertEquals(1, Paging.pageCount(0, 28));
        assertEquals(1, Paging.pageCount(28, 28));
        assertEquals(2, Paging.pageCount(29, 28));
        assertEquals(2, Paging.pageCount(35, 28));
    }

    @Test
    void slicesAndClamping() {
        assertEquals(28, Paging.slice(SKILLS, 0, 28).size());
        assertEquals(List.of(28, 29, 30, 31, 32, 33, 34), Paging.slice(SKILLS, 1, 28));
        assertEquals(Paging.slice(SKILLS, 1, 28), Paging.slice(SKILLS, 99, 28)); // past the end -> last page
        assertEquals(Paging.slice(SKILLS, 0, 28), Paging.slice(SKILLS, -3, 28));
        assertEquals(List.of(), Paging.slice(List.of(), 0, 28));
        assertEquals(28, Paging.CONTENT_SLOTS.length);
    }
}
