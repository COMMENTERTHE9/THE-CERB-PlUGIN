package GUIs.menu;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class FmtTest {

    @Test
    void compactHandlesCultivationScaleNumbers() {
        assertEquals("950", Fmt.compact(950));
        assertEquals("47.5", Fmt.compact(47.5));
        assertEquals("3.2K", Fmt.compact(3200));
        assertEquals("25.6K", Fmt.compact(25_600));
        assertEquals("1.0M", Fmt.compact(999_999)); // rounds up across the unit boundary
        assertEquals("1.4B", Fmt.compact(1.4e9));
        assertEquals("7.0T", Fmt.compact(7e12));
        assertEquals("-3.2K", Fmt.compact(-3200));
        assertEquals("?", Fmt.compact(Double.NaN));
        assertEquals("?", Fmt.compact(Double.POSITIVE_INFINITY));
    }

    @Test
    void groupedPctMult() {
        assertEquals("1,234,567", Fmt.grouped(1_234_567));
        assertEquals("45.7%", Fmt.pct(0.4567));
        assertEquals("x2.35", Fmt.mult(2.345));
    }

    @Test
    void signedPctShowsOneSign() {
        assertEquals("+4.6%", Fmt.signedPct(0.046));
        assertEquals("-4.6%", Fmt.signedPct(-0.046)); // a keystone's slower attacks: never "+-4.6%"
        assertEquals("+0.0%", Fmt.signedPct(0));
        assertEquals("+0.0%", Fmt.signedPct(-0.0001));
        assertEquals("?", Fmt.signedPct(Double.NaN));
    }

    @Test
    void duration() {
        assertEquals("0s", Fmt.duration(-5));
        assertEquals("1m 1s", Fmt.duration(61_000));
        assertEquals("2h 5m", Fmt.duration(2 * 3_600_000L + 5 * 60_000L));
        assertEquals("1d 2h 3m", Fmt.duration(93_784_000L));
    }

    @Test
    void bytes() {
        assertEquals("512 B", Fmt.bytes(512));
        assertEquals("1.5 KB", Fmt.bytes(1536));
        assertEquals("10.0 MB", Fmt.bytes(10_485_760));
    }

    @Test
    void barAndTitle() {
        assertEquals("|||||-----", Fmt.bar(50, 100, 10));
        assertEquals("----------", Fmt.bar(0, 0, 10));
        assertEquals("||||||||||", Fmt.bar(500, 100, 10));
        assertEquals("Blade Mastery", Fmt.title("BLADE_MASTERY"));
        assertEquals("Wither Skeleton", Fmt.title("wither_skeleton"));
    }
}
