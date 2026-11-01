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
    static class FakeDb implements SkillStore.Db {
        final Map<String, Map<String, long[]>> rows = new HashMap<>();
        final List<Long> writes = new ArrayList<>();
        /** Per custom id, the seq of every write in the order they reached the database. */
        final Map<String, List<Long>> writesBy = new HashMap<>();
        int failNext;
        boolean failAll;
        @Override public synchronized int write(String id, Map<String, Integer> levels, Map<String, Integer> xp, long seq)
                throws SQLException {
            if (failAll || failNext > 0) {
                if (failNext > 0) failNext--;
                throw new SQLException("[SQLITE_BUSY] database is locked");
            }
            writes.add(seq);
            writesBy.computeIfAbsent(id, k -> new ArrayList<>()).add(seq);
            Map<String, long[]> r = rows.computeIfAbsent(id, k -> new HashMap<>());
            int changed = 0;
            for (Map.Entry<String, Integer> e : levels.entrySet()) {
                long[] cur = r.get(e.getKey());
                if (cur == null || seq >= cur[2]) {
                    r.put(e.getKey(), new long[]{e.getValue(), xp.getOrDefault(e.getKey(), 0), seq});
                    changed++;
                }
            }
            return changed;
        }
        @Override public String customId(UUID p) { return "CUST-" + p; } // one per player
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
            @Override public int write(String id, Map<String, Integer> lv, Map<String, Integer> xp, long seq) throws SQLException {
                if (!id.equals("CUST-ANN")) return next.write(id, lv, xp, seq);
                for (var e : lv.entrySet()) annRows.put(e.getKey(), new long[]{e.getValue(), xp.get(e.getKey()), seq});
                return lv.size();
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
        for (Map.Entry<String, List<Long>> e : db.writesBy.entrySet()) {
            List<Long> seqs = e.getValue();
            for (int i = 1; i < seqs.size(); i++) assertTrue(seqs.get(i) > seqs.get(i - 1), "each player's writes only go forward: " + e.getKey());
        }
    }

    @Test
    void snapshotsOfOnePlayerHandedInOutOfOrderFromSeveralThreadsEndWithTheNewest() throws Exception {
        FakeDb db = new FakeDb();
        SkillStore s = new SkillStore(db, log, dir.resolve("r").toFile(), Executors.newSingleThreadExecutor(), System::currentTimeMillis, ms -> { });
        int n = 2000;
        List<SkillStore.Snapshot> snaps = new ArrayList<>();
        for (int k = 1; k <= n; k++) snaps.add(s.snapshot(ann, "Ann", mining(10), Map.of("Mining", k))); // taken in order
        java.util.Collections.shuffle(snaps, new java.util.Random(7));
        CountDownLatch go = new CountDownLatch(1);
        List<Thread> threads = new ArrayList<>();
        for (int t = 0; t < 4; t++) {
            List<SkillStore.Snapshot> part = snaps.subList(t * n / 4, (t + 1) * n / 4);
            Thread th = new Thread(() -> {
                try { go.await(); } catch (InterruptedException e) { return; }
                for (SkillStore.Snapshot x : part) s.save(x); // handed in out of order, from four threads at once
            });
            threads.add(th);
            th.start();
        }
        go.countDown();
        for (Thread t : threads) t.join();
        assertEquals(0, s.shutdown(5000));
        assertEquals(n, db.xpOf(ann, "Mining"), "the newest snapshot is what is stored");
        List<Long> seqs = db.writesBy.get(db.customId(ann));
        for (int i = 1; i < seqs.size(); i++) assertTrue(seqs.get(i) > seqs.get(i - 1), "no older snapshot was written after a newer one");
    }

    @Test
    void aRecoveryFileThatCannotBeWrittenAtStartupIsKeptInMemoryUsedByAJoinAndWrittenWhenTheDatabaseAnswers() {
        // Run 1: the shutdown can't write Ann's 80 XP; it goes to a recovery file.
        FakeDb db = new FakeDb();
        db.failAll = true;
        File recovery = dir.resolve("skills-recovery").toFile();
        SkillStore first = new SkillStore(db, log, recovery, new HandRun(), () -> 0, ms -> { });
        first.knowCustomId(ann, db.customId(ann));
        SkillStore.Snapshot lost = first.snapshot(ann, "Ann", mining(10), Map.of("Mining", 80));
        first.save(lost);
        assertEquals(1, first.shutdown(100));
        // Run 2: the database still fails at startup.
        HandRun w = new HandRun();
        SkillStore second = new SkillStore(db, log, recovery, w, () -> 0, ms -> { });
        long newest = second.applyRecovery();
        assertEquals(lost.seq(), newest, "the save order continues after the file's snapshot");
        assertEquals(1, recovery.listFiles().length, "the file stays while its progress isn't stored");
        SkillStore.Snapshot kept = second.newerUnsaved(ann, 0); // a join reading the older stored rows (seq 0)
        assertNotNull(kept, "a join continues from the file's progress, not the older stored rows");
        assertEquals(80, kept.xp().get("Mining"));
        second.continueAfter(newest);
        assertTrue(second.snapshot(ann, "Ann", mining(10), Map.of("Mining", 85)).seq() > lost.seq());
        // The database answers: the retry writes it, and only then is the file removed.
        db.failAll = false;
        second.retryUnsaved();
        w.runAll();
        assertEquals(80, db.xpOf(ann, "Mining"));
        assertEquals(0, recovery.listFiles().length);
        assertTrue(second.unsavedNow().isEmpty());
    }

    @Test
    void aRecoveryFileOlderThanTheStoredRowsIsRemovedAndNotCountedAsWritten() {
        FakeDb db = new FakeDb();
        db.failAll = true;
        File recovery = dir.resolve("skills-recovery").toFile();
        SkillStore first = new SkillStore(db, log, recovery, new HandRun(), () -> 0, ms -> { });
        first.knowCustomId(ann, db.customId(ann));
        SkillStore.Snapshot old = first.snapshot(ann, "Ann", mining(10), Map.of("Mining", 80));
        first.save(old);
        first.shutdown(100);
        db.failAll = false;
        db.rows.computeIfAbsent(db.customId(ann), k -> new HashMap<>()).put("Mining", new long[]{11, 5, old.seq() + 10}); // saved since
        SkillStore next = new SkillStore(db, log, recovery, new HandRun(), () -> 0, ms -> { });
        next.applyRecovery();
        assertEquals(5, db.xpOf(ann, "Mining"), "the newer stored rows stay");
        assertEquals(0, recovery.listFiles().length);
        assertTrue(next.stats().contains("recovered at startup 0"), next.stats());
        assertTrue(logged.records.stream().anyMatch(r -> r.getMessage().contains("was older than their stored skills")));
    }

    @Test
    void aSaveTheDatabaseKeepsNewerRowsAgainstIsSaidNotCountedAsWrittenAndContinuingAfterTheStoredRowsWritesIt() {
        FakeDb db = new FakeDb();
        db.rows.computeIfAbsent(db.customId(ann), k -> new HashMap<>()).put("Mining", new long[]{12, 9, 5_000}); // written by something else
        HandRun w = new HandRun();
        SkillStore s = store(db, w, new long[]{0});
        s.continueAfter(100); // e.g. the startup read failed and the order started from an earlier clock
        s.save(s.snapshot(ann, "Ann", mining(10), Map.of("Mining", 1)));
        w.runAll();
        assertEquals(9, db.xpOf(ann, "Mining"));
        assertTrue(s.stats().contains("written 0") && s.stats().contains("changed nothing (database newer) 1"), s.stats());
        assertEquals(1, logged.at(Level.WARNING));
        // A load moves the order past that player's stored rows: their next save is written.
        s.continueAfter(5_000);
        s.save(s.snapshot(ann, "Ann", mining(12), Map.of("Mining", 10)));
        w.runAll();
        assertEquals(10, db.xpOf(ann, "Mining"));
    }

    @Test
    void aWriteStillRunningWhenShutdownStopsWaitingStillGetsARecoveryFile() throws Exception {
        CountDownLatch inWrite = new CountDownLatch(1), release = new CountDownLatch(1);
        FakeDb db = new FakeDb() {
            @Override public synchronized int write(String id, Map<String, Integer> levels, Map<String, Integer> xp, long seq) throws SQLException {
                inWrite.countDown();
                try { release.await(); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
                throw new SQLException("[SQLITE_BUSY] database is locked");
            }
        };
        var writer = Executors.newSingleThreadExecutor();
        SkillStore s = new SkillStore(db, log, dir.resolve("skills-recovery").toFile(), writer, () -> 0, ms -> { });
        s.knowCustomId(ann, "CUST-ANN");
        s.save(s.snapshot(ann, "Ann", mining(10), Map.of("Mining", 70)));
        assertTrue(inWrite.await(5, TimeUnit.SECONDS));
        assertEquals(1, s.shutdown(50), "the snapshot being written is kept in a recovery file");
        release.countDown();
        assertTrue(writer.awaitTermination(5, TimeUnit.SECONDS));
    }

    @Test
    void aWriteThatFailsWhileShutdownReadsWhatIsLeftIsStillKeptInARecoveryFile() {
        // The exact interleaving the read order is for: shutdown has read the waiting snapshots when the write still running
        // fails and moves its snapshot to the failed ones (kept, then no longer waiting).
        FakeDb db = new FakeDb();
        db.failAll = true;
        HandRun w = new HandRun() {
            @Override public boolean awaitTermination(long t, TimeUnit u) { return false; } // shutdown stops waiting
            @Override public List<Runnable> shutdownNow() { shut = true; return List.of(); } // the write is still running
        };
        File recovery = dir.resolve("skills-recovery").toFile();
        SkillStore s = new SkillStore(db, log, recovery, w, () -> 0, ms -> { });
        s.knowCustomId(ann, db.customId(ann));
        s.save(s.snapshot(ann, "Ann", mining(10), Map.of("Mining", 70)));
        Runnable inFlight = w.queue.poll();
        s.betweenReads = inFlight; // it fails right between the two reads
        assertEquals(1, s.shutdown(10), "found in one of the two, whichever moment it moved");
        assertEquals(1, recovery.listFiles((d, n) -> n.endsWith(".yml")).length);
    }

    @Test
    void aRecoveryFileThatIsIncompleteOrChangedIsNotUsedAndIsLeftWhereItIs() throws Exception {
        FakeDb db = new FakeDb();
        db.failAll = true;
        File recovery = dir.resolve("skills-recovery").toFile();
        SkillStore first = new SkillStore(db, log, recovery, new HandRun(), () -> 0, ms -> { });
        first.knowCustomId(ann, db.customId(ann));
        first.save(first.snapshot(ann, "Ann", Map.of("Mining", 12, "Farming", 3), Map.of("Mining", 45, "Farming", 7)));
        first.shutdown(100);
        File f = recovery.listFiles((d, n) -> n.endsWith(".yml"))[0];
        String text = java.nio.file.Files.readString(f.toPath());
        assertTrue(text.contains("check:"), "the file carries its check");
        java.nio.file.Files.writeString(f.toPath(), text.replace("Mining: 45", "Mining: 4")); // cut short mid-number
        db.failAll = false;
        SkillStore next = new SkillStore(db, log, recovery, new HandRun(), () -> 0, ms -> { });
        assertEquals(0, next.applyRecovery(), "nothing taken from it");
        assertEquals(-1, db.xpOf(ann, "Mining"), "nothing written");
        assertNull(next.newerUnsaved(ann, 0), "nor kept for a join");
        assertTrue(f.exists(), "left for a person to look at");
        assertTrue(logged.records.stream().anyMatch(r -> r.getLevel() == Level.SEVERE && r.getMessage().contains("incomplete or was changed")));
        java.nio.file.Files.writeString(f.toPath(), text.replaceAll("(?m)^check:.*\\n", "")); // no check at all
        assertEquals(0, new SkillStore(db, log, recovery, new HandRun(), () -> 0, ms -> { }).applyRecovery());
        assertEquals(-1, db.xpOf(ann, "Mining"));
        assertEquals(0, recovery.listFiles((d, n) -> n.endsWith(".tmp")).length, "no temporary file left behind");
    }

    @Test
    void aRecoveryFilesProgressThatKeepsFailingNeverMakesAnotherPlayersSaveGiveUpEarly() {
        UUID bob = new UUID(0, 2);
        // Ann's progress from a recovery file can't be written (in this test: never); Bob's save fails once, then works.
        class Db2 extends FakeDb {
            int bobFails = 1;
            @Override public synchronized int write(String id, Map<String, Integer> levels, Map<String, Integer> xp, long seq) throws SQLException {
                if (id.equals(customId(ann))) throw new SQLException("[SQLITE_CONSTRAINT] cannot be written");
                if (id.equals(customId(bob)) && bobFails-- > 0) throw new SQLException("[SQLITE_BUSY] database is locked");
                return super.write(id, levels, xp, seq);
            }
        }
        FakeDb old = new FakeDb();
        old.failAll = true;
        File recovery = dir.resolve("skills-recovery").toFile();
        SkillStore first = new SkillStore(old, log, recovery, new HandRun(), () -> 0, ms -> { });
        first.knowCustomId(ann, old.customId(ann));
        first.save(first.snapshot(ann, "Ann", mining(10), Map.of("Mining", 50)));
        first.shutdown(100);
        Db2 db = new Db2();
        HandRun w = new HandRun();
        long[] now = {1_000_000};
        SkillStore s = new SkillStore(db, log, recovery, w, () -> now[0], ms -> now[0] += ms);
        s.continueAfter(s.applyRecovery());
        s.retryUnsaved();
        w.runAll(); // Ann's kept progress fails again: one attempt
        s.knowCustomId(bob, db.customId(bob));
        s.save(s.snapshot(bob, "Bob", mining(4), Map.of("Mining", 9)));
        w.runAll();
        assertEquals(9, db.xpOf(bob, "Mining"), "Bob's second attempt stores it");
        assertTrue(s.unsavedNow().stream().noneMatch(x -> x.player().equals(bob)));
        assertEquals(1, recovery.listFiles((d, n) -> n.endsWith(".yml")).length, "Ann's file stays until her progress is stored");
    }

    @Test
    void aFailureRightAfterAChangedNothingWarningIsStillLoggedAsAnError() {
        FakeDb db = new FakeDb();
        db.rows.computeIfAbsent(db.customId(ann), k -> new HashMap<>()).put("Mining", new long[]{12, 9, 5_000});
        HandRun w = new HandRun();
        long[] now = {0};
        SkillStore s = store(db, w, now);
        s.save(s.snapshot(ann, "Ann", mining(10), Map.of("Mining", 1))); // changes nothing: a warning
        w.runAll();
        db.failAll = true;
        s.save(s.snapshot(ann, "Ann", mining(10), Map.of("Mining", 2))); // a real failure seconds later
        w.runAll();
        assertEquals(1, logged.at(Level.WARNING));
        assertEquals(1, logged.at(Level.SEVERE), "the failure is not swallowed by the warning's once-a-minute limit");
    }

    @Test
    void aJoinLaysTheKeptProgressOverTheStoredRowsKeepingWhatTheKeptSnapshotDoesNotHold() {
        Map<String, Integer> stored = Map.of("Mining", 10, "Farming", 4, "Fishing", 7);
        Map<String, Integer> kept = Map.of("Mining", 12, "Farming", 4);
        assertEquals(Map.of("Mining", 12, "Farming", 4, "Fishing", 7), SkillStore.overlay(stored, kept));
    }

    @Test
    void aFullSnapshotComesBackFromItsRecoveryFileAndPassesItsCheck() {
        FakeDb db = new FakeDb();
        db.failAll = true;
        File recovery = dir.resolve("skills-recovery").toFile();
        SkillStore first = new SkillStore(db, log, recovery, new HandRun(), () -> 0, ms -> { });
        first.knowCustomId(ann, db.customId(ann));
        Map<String, Integer> levels = new HashMap<>(), xp = new HashMap<>();
        String[] names = {"Blade_Mastery", "Mining", "Navigation", "Spell_Weaving", "Heavy_Armor_Training", "Fishing"};
        for (int i = 0; i < names.length; i++) { levels.put(names[i], 1 + i * 17); xp.put(names[i], 123_456 * i); }
        first.save(first.snapshot(ann, "Ann", levels, xp));
        first.shutdown(100);
        db.failAll = false;
        new SkillStore(db, log, recovery, new HandRun(), () -> 0, ms -> { }).applyRecovery();
        for (int i = 0; i < names.length; i++) assertEquals(123_456L * i, db.xpOf(ann, names[i]), names[i]);
    }
}
