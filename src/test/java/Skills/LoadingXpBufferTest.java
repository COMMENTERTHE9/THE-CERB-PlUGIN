package Skills;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.LongAdder;

import static org.junit.jupiter.api.Assertions.*;

/**
 * What a session does while its skills load (docs/STATUS.md → item 10, exact XP while loading): kept for that session
 * only, in the order it happened, handed over once and run once, never run before the load completes, never lost to a race.
 */
class LoadingXpBufferTest {

    private final UUID ann = new UUID(0, 1);

    @Test
    void whatASessionDoesWhileLoadingIsHandedOverOnceInOrderToThatSession() {
        LoadingXpBuffer b = new LoadingXpBuffer();
        List<String> ran = new ArrayList<>();
        b.begin(ann, 7);
        assertTrue(b.isLoading(ann));
        for (String s : List.of("coal", "poppy", "wheat", "coal again")) assertTrue(b.keep(ann, () -> ran.add(s)));
        assertTrue(ran.isEmpty(), "kept, not run: nothing is paid before the load completes");
        LoadingXpBuffer.Kept got = b.drain(ann, 7);
        assertEquals(4, got.actions().size());
        assertEquals(0, got.dropped());
        got.actions().forEach(Runnable::run);
        assertEquals(List.of("coal", "poppy", "wheat", "coal again"), ran, "in the order they happened, each once");
        assertFalse(b.isLoading(ann));
        assertNull(b.drain(ann, 7), "handed over once");
        assertFalse(b.keep(ann, () -> ran.add("late")), "after the hand-over nothing is kept: the caller runs it the normal way");
        assertThrows(UnsupportedOperationException.class, () -> got.actions().add(() -> { }), "what was handed over can't change");
        assertEquals(4, ran.size());
    }

    @Test
    void anotherGenerationsLoadNeverReceivesIt() {
        LoadingXpBuffer b = new LoadingXpBuffer();
        List<String> ran = new ArrayList<>();
        b.begin(ann, 1);
        b.keep(ann, () -> ran.add("first session"));
        assertNull(b.drain(ann, 2), "a different session's load: not run for it");
        assertTrue(b.isLoading(ann), "and never dropped by it: the buffer waits for its own session's load");
        b.drain(ann, 1).actions().forEach(Runnable::run);
        assertEquals(List.of("first session"), ran);

        ran.clear();
        b.begin(ann, 3);
        b.keep(ann, () -> ran.add("session 3"));
        b.begin(ann, 4); // a rejoin started a new session before the old one's load came back
        assertTrue(b.keep(ann, () -> ran.add("session 4")));
        assertNull(b.drain(ann, 3), "the old session's load, coming back late, gets nothing");
        b.drain(ann, 4).actions().forEach(Runnable::run);
        assertEquals(List.of("session 4"), ran, "only the new session's own actions, once");
    }

    @Test
    void aFailedLoadOrALeavingPlayerDropsItAndNothingRuns() {
        LoadingXpBuffer b = new LoadingXpBuffer();
        AtomicInteger ran = new AtomicInteger();
        b.begin(ann, 1);
        b.keep(ann, ran::incrementAndGet);
        b.abandon(ann, 9); // another generation's failure: not this one
        assertTrue(b.isLoading(ann));
        b.abandon(ann, 1);
        assertFalse(b.isLoading(ann));
        assertFalse(b.keep(ann, ran::incrementAndGet), "nothing kept after the failure");
        assertNull(b.drain(ann, 1), "a failed load hands nothing over");
        b.begin(ann, 2);
        b.keep(ann, ran::incrementAndGet);
        b.forget(ann);
        assertNull(b.drain(ann, 2), "a player who left hands nothing over");
        assertEquals(0, ran.get(), "no action of a failed or abandoned load ever runs");
    }

    @Test
    void aLoadThatNeverEndsKeepsAtMostTheCapAndCountsTheRest() {
        LoadingXpBuffer b = new LoadingXpBuffer();
        b.begin(ann, 1);
        assertTrue(b.keep(ann, null), "nothing to keep");
        for (int i = 0; i < LoadingXpBuffer.MAX_ACTIONS + 25; i++) {
            assertTrue(b.keep(ann, () -> { }), "a loading session's action is never run early, even past the cap");
        }
        LoadingXpBuffer.Kept got = b.drain(ann, 1);
        assertEquals(LoadingXpBuffer.MAX_ACTIONS, got.actions().size());
        assertEquals(25, got.dropped());
    }

    @Test
    void anActionThatFindsTheBufferJustBeforeItIsHandedOverIsToldItWasNotKept() {
        // the exact interleaving: the action has found the buffer, then the load completes and takes it, then the action locks it
        LoadingXpBuffer b = new LoadingXpBuffer();
        List<String> ran = new ArrayList<>();
        b.begin(ann, 1);
        b.keep(ann, () -> ran.add("before"));
        LoadingXpBuffer.Kept[] handed = new LoadingXpBuffer.Kept[1];
        b.afterGet = () -> { if (handed[0] == null) handed[0] = b.drain(ann, 1); };
        assertFalse(b.keep(ann, () -> ran.add("racing")), "not kept: the caller runs it the normal way (the skills are loaded by then)");
        handed[0].actions().forEach(Runnable::run);
        assertEquals(List.of("before"), ran, "what was handed over doesn't change afterwards");
    }

    @Test
    void anActionRacingTheHandOverIsEitherHandedOverOrToldItWasNotNeverBoth() throws Exception {
        for (int round = 0; round < 200; round++) {
            LoadingXpBuffer b = new LoadingXpBuffer();
            b.begin(ann, 1);
            LongAdder keptTrue = new LongAdder(), refused = new LongAdder(), ran = new LongAdder();
            CountDownLatch go = new CountDownLatch(1);
            Thread[] ts = new Thread[4];
            for (int t = 0; t < ts.length; t++) {
                ts[t] = new Thread(() -> {
                    try { go.await(); } catch (InterruptedException e) { return; }
                    for (int k = 0; k < 500; k++) {
                        if (b.keep(ann, ran::increment)) keptTrue.increment(); else refused.increment();
                    }
                });
                ts[t].start();
            }
            go.countDown();
            Thread.onSpinWait();
            LoadingXpBuffer.Kept got = b.drain(ann, 1);
            for (Thread t : ts) t.join();
            if (got != null) got.actions().forEach(Runnable::run);
            assertEquals(keptTrue.sum(), ran.sum(), "every action said to be kept is in what was handed over, and runs once");
            assertEquals(2000, keptTrue.sum() + refused.sum(), "and every other one was told so (the caller runs it normally)");
        }
    }
}
