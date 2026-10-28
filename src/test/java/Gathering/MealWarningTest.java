package Gathering;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** The one-minute heads-up before a meal ends (docs/design/ACTIVE_EFFECTS.md §4): when it is set, and when there is none. */
class MealWarningTest {

    private static final long NOW = 1_000_000;

    @Test
    void aMealEatenWarnsAMinuteBeforeItsEnd() {
        assertEquals(9 * 60_000L, Cooking.warnDelayMs(NOW, NOW + 10 * 60_000L, true), "a 10-minute meal: after 9 minutes");
        assertEquals(5_000L, Cooking.warnDelayMs(NOW, NOW + 65_000L, true));
    }

    @Test
    void aMealWithAboutAMinuteOrLessGetsNoneWhenEaten() {
        assertEquals(-1, Cooking.warnDelayMs(NOW, NOW + 64_999L, true), "the eating message said its time a moment ago");
        assertEquals(-1, Cooking.warnDelayMs(NOW, NOW + 60_000L, true));
        assertEquals(-1, Cooking.warnDelayMs(NOW, NOW + 30_000L, true));
    }

    @Test
    void afterAJoinUnderAMinuteLeftItComesAMomentLater() {
        assertEquals(10 * 60_000L - 60_000L, Cooking.warnDelayMs(NOW, NOW + 10 * 60_000L, false), "plenty left: at the usual time");
        assertEquals(Cooking.WARN_AFTER_JOIN_MS, Cooking.warnDelayMs(NOW, NOW + 50_000L, false));
        assertEquals(Cooking.WARN_AFTER_JOIN_MS, Cooking.warnDelayMs(NOW, NOW + 15_001L, false));
        assertEquals(-1, Cooking.warnDelayMs(NOW, NOW + 15_000L, false), "too little left to be worth saying");
        assertEquals(-1, Cooking.warnDelayMs(NOW, NOW - 1, false), "already over");
    }

    @Test
    void justOverAMinuteLeftAtAJoinWarnsAtTheMinute() {
        assertEquals(1_000L, Cooking.warnDelayMs(NOW, NOW + 61_000L, false));
    }
}
