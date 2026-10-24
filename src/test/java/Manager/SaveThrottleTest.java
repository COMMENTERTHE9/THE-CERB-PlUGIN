package Manager;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SaveThrottleTest {

    @Test
    void firstRequestRunsNow() {
        SaveThrottle t = new SaveThrottle(1000);
        assertEquals(SaveThrottle.Action.RUN_NOW, t.request("k", 10_000).action());
    }

    @Test
    void requestInsideCooldownSchedulesOneTrailingFlush() {
        SaveThrottle t = new SaveThrottle(1000);
        t.request("k", 10_000);
        SaveThrottle.Decision d = t.request("k", 10_300);
        assertEquals(SaveThrottle.Action.SCHEDULE_FLUSH, d.action());
        assertEquals(700, d.delayMs());
        // further requests before the flush just replace the pending task
        assertEquals(SaveThrottle.Action.ALREADY_SCHEDULED, t.request("k", 10_600).action());
        assertEquals(SaveThrottle.Action.ALREADY_SCHEDULED, t.request("k", 12_000).action());
    }

    @Test
    void afterFlushTheCooldownRestarts() {
        SaveThrottle t = new SaveThrottle(1000);
        t.request("k", 10_000);
        t.request("k", 10_300);           // schedules flush
        t.flushed("k", 11_000);
        assertEquals(SaveThrottle.Action.SCHEDULE_FLUSH, t.request("k", 11_200).action());
        t.flushed("k", 12_000);
        assertEquals(SaveThrottle.Action.RUN_NOW, t.request("k", 13_500).action());
    }

    @Test
    void keysAreIndependent() {
        SaveThrottle t = new SaveThrottle(1000);
        t.request("a", 10_000);
        assertEquals(SaveThrottle.Action.RUN_NOW, t.request("b", 10_100).action());
    }
}
