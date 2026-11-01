package Skills;

import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.DoubleAdder;

import static org.junit.jupiter.api.Assertions.*;

/** XP earned while a session's skills load: kept for that session only, handed over once, never lost to a race. */
class LoadingXpBufferTest {

    private final UUID ann = new UUID(0, 1);

    @Test
    void whatASessionEarnsWhileLoadingIsHandedOverOnceToThatSession() {
        LoadingXpBuffer b = new LoadingXpBuffer();
        b.begin(ann, 7);
        assertTrue(b.isLoading(ann));
        assertTrue(b.keep(ann, "Mining", 5, true));
        assertTrue(b.keep(ann, "Mining", 3, true));
        assertTrue(b.keep(ann, "Mining", 2, false)); // an admin's quiet XP, kept apart (no celebration)
        assertTrue(b.keep(ann, "Herbalism", 4, true));
        Map<String, double[]> got = b.drain(ann, 7);
        assertArrayEquals(new double[]{8, 2}, got.get("Mining"));
        assertArrayEquals(new double[]{4, 0}, got.get("Herbalism"));
        assertFalse(b.isLoading(ann));
        assertNull(b.drain(ann, 7), "handed over once");
        assertFalse(b.keep(ann, "Mining", 1, true), "after the hand-over nothing is kept: the caller awards it normally");
    }

    @Test
    void anotherGenerationsLoadNeverReceivesIt() {
        LoadingXpBuffer b = new LoadingXpBuffer();
        b.begin(ann, 1);
        b.keep(ann, "Mining", 5, true);
        assertNull(b.drain(ann, 2), "a different session's load: not applied");
        assertTrue(b.isLoading(ann), "and never dropped by it: the buffer waits for its own session's load");
        assertArrayEquals(new double[]{5, 0}, b.drain(ann, 1).get("Mining"));
        b.begin(ann, 3);
        b.keep(ann, "Mining", 1, true);
        b.begin(ann, 4); // a rejoin started a new session before the old one's load came back
        assertTrue(b.keep(ann, "Mining", 2, true));
        assertArrayEquals(new double[]{2, 0}, b.drain(ann, 4).get("Mining"), "only the new session's own XP");
    }

    @Test
    void aFailedLoadOrALeavingPlayerDropsIt() {
        LoadingXpBuffer b = new LoadingXpBuffer();
        b.begin(ann, 1);
        b.keep(ann, "Mining", 5, true);
        b.abandon(ann, 9); // another generation's failure: not this one
        assertTrue(b.isLoading(ann));
        b.abandon(ann, 1);
        assertFalse(b.isLoading(ann));
        assertFalse(b.keep(ann, "Mining", 5, true));
        b.begin(ann, 2);
        b.keep(ann, "Mining", 5, true);
        b.forget(ann);
        assertNull(b.drain(ann, 2));
    }

    @Test
    void badAmountsAreIgnoredAndASkillIsCapped() {
        LoadingXpBuffer b = new LoadingXpBuffer();
        b.begin(ann, 1);
        assertTrue(b.keep(ann, "Mining", -3, true));
        assertTrue(b.keep(ann, "Mining", Double.NaN, true));
        assertTrue(b.keep(ann, "Mining", Double.POSITIVE_INFINITY, true));
        assertTrue(b.keep(ann, null, 5, true));
        b.keep(ann, "Mining", 9e11, true);
        b.keep(ann, "Mining", 9e11, true);
        assertEquals(LoadingXpBuffer.CAP, b.drain(ann, 1).get("Mining")[0]);
    }

    @Test
    void anAwardThatFindsTheBufferJustBeforeItIsHandedOverIsToldItWasNotKept() {
        // the exact interleaving: the award has found the buffer, then the load completes and takes it, then the award locks it
        LoadingXpBuffer b = new LoadingXpBuffer();
        b.begin(ann, 1);
        b.keep(ann, "Mining", 2, true);
        Map<String, double[]>[] handed = new Map[1];
        b.afterGet = () -> { if (handed[0] == null) handed[0] = b.drain(ann, 1); };
        assertFalse(b.keep(ann, "Mining", 7, true), "not kept: the caller awards it the normal way (the skills are loaded by then)");
        assertArrayEquals(new double[]{2, 0}, handed[0].get("Mining"), "what was handed over doesn't change afterwards");
    }

    @Test
    void anAwardRacingTheHandOverIsEitherHandedOverOrToldItWasNot() throws Exception {
        for (int round = 0; round < 200; round++) {
            LoadingXpBuffer b = new LoadingXpBuffer();
            b.begin(ann, 1);
            DoubleAdder keptTrue = new DoubleAdder(), refused = new DoubleAdder();
            CountDownLatch go = new CountDownLatch(1);
            Thread[] ts = new Thread[4];
            for (int t = 0; t < ts.length; t++) {
                ts[t] = new Thread(() -> {
                    try { go.await(); } catch (InterruptedException e) { return; }
                    for (int k = 0; k < 500; k++) {
                        if (b.keep(ann, "Mining", 1, true)) keptTrue.add(1); else refused.add(1);
                    }
                });
                ts[t].start();
            }
            go.countDown();
            Thread.onSpinWait();
            Map<String, double[]> got = b.drain(ann, 1);
            for (Thread t : ts) t.join();
            double handed = got == null || got.get("Mining") == null ? 0 : got.get("Mining")[0];
            assertEquals(keptTrue.sum(), handed, "every award said to be kept is in what was handed over");
            assertEquals(2000, keptTrue.sum() + refused.sum(), "and every other one was told so (the caller awards it normally)");
        }
    }
}
