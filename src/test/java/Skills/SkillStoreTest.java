package Skills;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.file.Path;
import java.sql.SQLException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.AbstractExecutorService;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The skill store's guarantees, each interleaving made exact with a hand-run writer: an older snapshot never replaces a
 * newer one; a failed write is tried again, kept, said, used by the next load and written when the database answers;
 * shutdown keeps what it can't write in recovery files the next start writes (only where newer).
 */
class SkillStoreTest {

    @TempDir Path dir;

    /** Runs tasks only when told to, in the order they came. */
    static class HandRun extends AbstractExecutorService {
        final Deque<Runnable> queue = new ArrayDeque<>();
        boolean shut;
        @Override public void execute(Runnable r) { if (shut) throw new RejectedExecutionException(); queue.add(r); }
        void runAll() { while (!queue.isEmpty()) queue.poll().run(); }
        @Override public void shutdown() { shut = true; }
        @Override public List<Runnable> shutdownNow() { shut = true; List<Runnable> l = new ArrayList<>(queue); queue.clear(); return l; }
        @Override public boolean isShutdown() { return shut; }
        @Override public boolean isTerminated() { return shut && queue.isEmpty(); }
        @Override public boolean awaitTermination(long t, TimeUnit u) { runAll(); return true; }
    }

    /** The database's rule (SkillRows): a row is kept unless the write's snapshot is at least as new. */
    static final class FakeDb implements SkillStore.Db {
        final Map<String, Map<String, long[]>> rows = new HashMap<>();
        final List<Long> writes = new ArrayList<>();
        int failNext;
        boolean failAll;
        @Override public synchronized void write(String id, Map<String, Integer> levels, Map<String, Integer> xp, long seq)
                throws SQLException {
            if (failAll || failNext > 0) {
                if (failNext > 0) failNext--;
                throw new SQLException("[SQLITE_BUSY] database is locked");
            }
            writes.add(seq);
            Map<String, long[]> r = rows.computeIfAbsent(id, k -> new HashMap<>());
            for (Map.Entry<String, Integer> e : levels.entrySet()) {
                long[] cur = r.get(e.getKey());
                if (cur == null || seq >= cur[2]) r.put(e.getKey(), new long[]{e.getValue(), xp.getOrDefault(e.getKey(), 0), seq});
            }
        }
        @Override public String customId(UUID p) { return "CUST-" + p.toString().substring(0, 6); }
        synchronized long xpOf(UUID p, String skill) {
            long[] r = rows.getOrDefault(customId(p), Map.of()).get(skill);
            return r == null ? -1 : r[1];
        }
    }

    static final class Logged extends Handler {
        final List<LogRecord> records = new ArrayList<>();
        @Override public synchronized void publish(LogRecord r) { records.add(r); }
        @Override public void flush() { }
        @Override public void close() { }
        synchronized long at(Level l) { return records.stream().filter(r -> r.getLevel() == l).count(); }
    }

    private final Logged logged = new Logged();
    private final Logger log = logger(logged);
    private final UUID ann = UUID.fromString("00000000-0000-0000-0000-00000000a001");

    private static Logger logger(Handler h) {
        Logger l = Logger.getAnonymousLogger();
        l.setUseParentHandlers(false);
        l.addHandler(h);
        return l;
    }

    private SkillStore store(FakeDb db, HandRun w, long[] now) {
        return new SkillStore(db, log, dir.resolve("skills-recovery").toFile(), w, () -> now[0], ms -> now[0] += ms);
    }

    private static Map<String, Integer> mining(int level) { return Map.of("Mining", level); }

    @Test
    void anOlderSnapshotArrivingAfterANewerOneIsNeverWritten() {
        FakeDb db = new FakeDb();
        HandRun w = new HandRun();
        SkillStore s = store(db, w, new long[]{0});
        SkillStore.Snapshot first = s.snapshot(ann, "Ann", mining(10), Map.of("Mining", 40));
        SkillStore.Snapshot second = s.snapshot(ann, "Ann", mining(10), Map.of("Mining", 80));
        assertTrue(second.seq() > first.seq());
        s.save(second); // the quit's save
        s.save(first);  // a throttled save of the earlier gain, handed in late
        w.runAll();
        assertEquals(80, db.xpOf(ann, "Mining"));
        assertEquals(List.of(second.seq()), db.writes, "the older snapshot is never written");
    }

    @Test
    void aSnapshotOlderThanWhatIsStoredIsSkippedEvenWhenItIsTheOnlyOneWaiting() {
        FakeDb db = new FakeDb();
        HandRun w = new HandRun();
        SkillStore s = store(db, w, new long[]{0});
        SkillStore.Snapshot older = s.snapshot(ann, "Ann", mining(10), Map.of("Mining", 40));
        SkillStore.Snapshot newer = s.snapshot(ann, "Ann", mining(10), Map.of("Mining", 80));
        s.save(newer);
        w.runAll();
        s.save(older);
        w.runAll();
        assertEquals(80, db.xpOf(ann, "Mining"));
        assertEquals(1, db.writes.size());
        assertTrue(s.stats().contains("skipped (older than stored) 1"), s.stats());
    }

    @Test
    void aFailedWriteIsTriedAgainThenKeptSaidUsedByTheNextLoadAndWrittenWhenTheDatabaseAnswers() {
        FakeDb db = new FakeDb();
        db.failAll = true;
        HandRun w = new HandRun();
        long[] now = {1_000_000};
        SkillStore s = store(db, w, now);
        List<String> alerts = new ArrayList<>();
        s.onAlert(alerts::add);
        SkillStore.Snapshot snap = s.snapshot(ann, "Ann", mining(10), Map.of("Mining", 80));
        s.save(snap);
        w.runAll();
        assertEquals(1_000_000 + 500 + 1500, now[0], "three attempts, waiting 0.5 s then 1.5 s between them");
        assertEquals(List.of(snap), List.copyOf(s.unsavedNow()));
        assertEquals(1, logged.at(Level.SEVERE), "the failure is said in the log");
        assertEquals(1, alerts.size(), "and to admins online");
        assertSame(snap, s.newerUnsaved(ann, 0), "a rejoin continues from the kept progress (newer than the stored rows)");
        assertNull(s.newerUnsaved(ann, snap.seq()), "not when the stored rows are as new");
        db.failAll = false;
        s.retryUnsaved();
        w.runAll();
        assertEquals(80, db.xpOf(ann, "Mining"));
        assertTrue(s.unsavedNow().isEmpty());
        assertNull(s.newerUnsaved(ann, 0));
    }

    @Test
    void aWriteThatSucceedsOnASecondAttemptIsStoredAndNothingIsKept() {
        FakeDb db = new FakeDb();
        db.failNext = 1;
        HandRun w = new HandRun();
        SkillStore s = store(db, w, new long[]{0});
        s.save(s.snapshot(ann, "Ann", mining(10), Map.of("Mining", 80)));
        w.runAll();
        assertEquals(80, db.xpOf(ann, "Mining"));
        assertTrue(s.unsavedNow().isEmpty());
        assertEquals(0, logged.at(Level.SEVERE));
    }

    @Test
    void whileTheDatabaseFailsANewerSaveReplacesTheKeptOneAndItsSuccessClearsIt() {
        FakeDb db = new FakeDb();
        db.failAll = true;
        HandRun w = new HandRun();
        long[] now = {0};
        SkillStore s = store(db, w, now);
        SkillStore.Snapshot a = s.snapshot(ann, "Ann", mining(10), Map.of("Mining", 40));
        s.save(a);
        w.runAll();
        SkillStore.Snapshot b = s.snapshot(ann, "Ann", mining(10), Map.of("Mining", 80));
        s.save(b);
        w.runAll();
        assertEquals(List.of(b), List.copyOf(s.unsavedNow()), "the newest is kept");
        assertEquals(1, logged.at(Level.SEVERE), "said once a minute per player, not per write");
        db.failAll = false;
        SkillStore.Snapshot c = s.snapshot(ann, "Ann", mining(10), Map.of("Mining", 90));
        s.save(c);
        w.runAll();
        assertEquals(90, db.xpOf(ann, "Mining"));
        assertTrue(s.unsavedNow().isEmpty());
    }

    @Test
    void shutdownKeepsWhatItCannotWriteInRecoveryFilesAndTheNextStartWritesThemOnlyWhereNewer() {
        FakeDb db = new FakeDb();
        db.failAll = true;
        HandRun w = new HandRun();
        SkillStore s = store(db, w, new long[]{0});
        UUID bob = UUID.fromString("00000000-0000-0000-0000-00000000b002");
        s.knowCustomId(ann, "CUST-ANN");
        s.save(s.snapshot(ann, "Ann", Map.of("Mining", 12, "Farming", 3), Map.of("Mining", 80, "Farming", 7)));
        SkillStore.Snapshot bobs = s.snapshot(bob, "Bob", mining(5), Map.of("Mining", 1));
        s.save(bobs);
        assertEquals(2, s.shutdown(1000));
        File recovery = dir.resolve("skills-recovery").toFile();
        assertEquals(2, recovery.listFiles().length);
        assertThrows(RejectedExecutionException.class, () -> w.execute(() -> { }), "the writer is closed");

        FakeDb next = new FakeDb(); // the next start; Bob's stored rows are newer than his recovery file
        next.rows.computeIfAbsent(next.customId(bob), k -> new HashMap<>()).put("Mining", new long[]{6, 0, bobs.seq() + 5});
        Map<String, long[]> annRows = new HashMap<>();
        SkillStore later = new SkillStore(new SkillStore.Db() {
            @Override public void write(String id, Map<String, Integer> lv, Map<String, Integer> xp, long seq) throws SQLException {
                if (id.equals("CUST-ANN")) for (var e : lv.entrySet()) annRows.put(e.getKey(), new long[]{e.getValue(), xp.get(e.getKey()), seq});
                else next.write(id, lv, xp, seq);
            }
            @Override public String customId(UUID p) { return next.customId(p); }
        }, log, recovery, new HandRun(), () -> 0, ms -> { });
        long newest = later.applyRecovery();
        assertTrue(newest >= bobs.seq());
        assertArrayEquals(new long[]{12, 80}, new long[]{annRows.get("Mining")[0], annRows.get("Mining")[1]}, "Ann's progress is back");
        assertEquals(7, annRows.get("Farming")[1]);
        assertArrayEquals(new long[]{6, 0}, new long[]{next.rows.get(next.customId(bob)).get("Mining")[0], next.rows.get(next.customId(bob)).get("Mining")[1]},
                "Bob's newer stored rows stay");
        assertEquals(0, recovery.listFiles().length, "files written are removed");
    }

    @Test
    void aSnapshotHandedInButNeverRunBeforeTheWriterStopsGoesToARecoveryFile() {
        FakeDb db = new FakeDb();
        HandRun w = new HandRun() {
            @Override public boolean awaitTermination(long t, TimeUnit u) { return false; } // the writer is stuck
        };
        SkillStore s = store(db, w, new long[]{0});
        s.knowCustomId(ann, "CUST-ANN");
        s.save(s.snapshot(ann, "Ann", mining(10), Map.of("Mining", 60))); // queued behind a write that never ends
        assertEquals(1, s.shutdown(10));
        assertTrue(db.writes.isEmpty());
        assertEquals(1, dir.resolve("skills-recovery").toFile().listFiles().length);
    }

    @Test
    void theSaveOrderContinuesAfterTheNewestStoredSnapshot() {
        SkillStore s = store(new FakeDb(), new HandRun(), new long[]{0});
        s.continueAfter(100);
        assertEquals(101, s.snapshot(ann, "Ann", mining(1), Map.of()).seq());
        s.continueAfter(50);
        assertEquals(102, s.snapshot(ann, "Ann", mining(1), Map.of()).seq(), "never goes back");
    }

    @Test
    void manyPlayersSavingFromManyThreadsEachEndWithTheirNewestProgress() throws Exception {
        FakeDb db = new FakeDb();
        SkillStore s = new SkillStore(db, log, dir.resolve("r").toFile(), Executors.newSingleThreadExecutor(), System::currentTimeMillis, ms -> { });
        int players = 8, gains = 300;
        CountDownLatch go = new CountDownLatch(1);
        List<Thread> threads = new ArrayList<>();
        List<UUID> ids = new ArrayList<>();
        for (int p = 0; p < players; p++) {
            UUID id = new UUID(0, p + 1);
            ids.add(id);
            Thread t = new Thread(() -> {
                try { go.await(); } catch (InterruptedException e) { return; }
                for (int k = 1; k <= gains; k++) s.save(s.snapshot(id, "P", mining(10), Map.of("Mining", k)));
            });
            threads.add(t);
            t.start();
        }
        go.countDown();
        for (Thread t : threads) t.join();
        assertEquals(0, s.shutdown(5000));
        for (UUID id : ids) assertEquals(gains, db.xpOf(id, "Mining"), "player " + id);
        assertTrue(db.writes.size() <= players * gains);
    }
}
