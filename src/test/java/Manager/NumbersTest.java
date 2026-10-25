package Manager;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class NumbersTest {

    @Test
    void compactCoversTheWholeRealmRange() {
        assertEquals("950", Numbers.compact(950));
        assertEquals("47.5", Numbers.compact(47.5));
        assertEquals("3.2K", Numbers.compact(3200));
        assertEquals("1.0M", Numbers.compact(999_999));
        assertEquals("1.7B", Numbers.compact(1.68e9));      // Primordial player health at x8^8
        assertEquals("7.0T", Numbers.compact(7e12));
        assertEquals("9.0Qa", Numbers.compact(9.0e15));     // the double-exact ceiling
        assertEquals("1.0Dc", Numbers.compact(1e33));
        assertEquals("2.0e36", Numbers.compact(2e36));      // past the ladder: scientific
        assertEquals("-3.2K", Numbers.compact(-3200));
        assertEquals("?", Numbers.compact(Double.NaN));
        assertEquals("?", Numbers.compact(Double.NEGATIVE_INFINITY));
    }

    @Test
    void groupedMatchesStringFormatExactly() {
        for (long n = -20_000; n <= 20_000; n++) {
            assertEquals(String.format(java.util.Locale.ROOT, "%,d", n), Numbers.grouped(n), "n=" + n);
        }
        for (long n : new long[]{999_999, 1_000_000, -1_234_567_890L, Long.MAX_VALUE, Long.MIN_VALUE}) {
            assertEquals(String.format(java.util.Locale.ROOT, "%,d", n), Numbers.grouped(n), "n=" + n);
        }
    }

    @Test
    void hudShowsExactSmallValuesAndCompactLargeOnes() {
        assertEquals("102", Numbers.hud(102.4));
        assertEquals("9,850", Numbers.hud(9850));
        assertEquals("12.3K", Numbers.hud(12_345));
        assertEquals("1.7B", Numbers.hud(1.68e9));
        assertEquals("?", Numbers.hud(Double.NaN));
    }

    @Test
    void lengthStaysBounded() {
        for (double v = 1; v < 1e40; v *= 7.3) {
            assertTrue(Numbers.compact(v).length() <= 7, v + " -> " + Numbers.compact(v));
            assertTrue(Numbers.hud(v).length() <= 7, v + " -> " + Numbers.hud(v));
        }
    }
}
