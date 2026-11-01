package Classes;

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
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;

/** Class documents whose write fails: tried again, kept, used by a rejoin, written later or kept in recovery files. */
class ClassDocStoreTest {

    @TempDir Path dir;

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

    /** The database's rule for class documents: a newer stored stamp is kept. */
    static final class FakeDb implements ClassDocStore.Db {
        final Map<UUID, Object[]> rows = new HashMap<>();
        boolean failAll;
        int attempts;
        @Override public int write(UUID p, int version, String data, long stamp) throws SQLException {
            attempts++;
            if (failAll) throw new SQLException("[SQLITE_BUSY] database is locked");
            Object[] cur = rows.get(p);
            if (cur != null && stamp < (long) cur[1]) return 0;
            rows.put(p, new Object[]{data, stamp});
            return 1;
        }
        String data(UUID p) { Object[] r = rows.get(p); return r == null ? null : (String) r[0]; }
    }

    private final List<LogRecord> records = new ArrayList<>();
    private final Logger log = Logger.getAnonymousLogger();
    {
        log.setUseParentHandlers(false);
        log.addHandler(new Handler() {
            @Override public void publish(LogRecord r) { records.add(r); }
            @Override public void flush() { }
            @Override public void close() { }
        });
    }
    private final UUID ann = new UUID(0, 1);

    @Test
    void aFailedWriteIsTriedAgainKeptUsedByARejoinAndWrittenWhenTheDatabaseAnswers() {
        FakeDb db = new FakeDb();
        db.failAll = true;
        HandRun w = new HandRun();
        long[] now = {1_000_000};
        ClassDocStore s = new ClassDocStore(db, log, dir.resolve("cr").toFile(), w, () -> now[0], ms -> now[0] += ms);
        s.save(ann, "Ann", 3, () -> "warrior: rank 7", 100);
        w.runAll();
        assertEquals(3, db.attempts);
        assertEquals(1, s.unsavedCount());
        assertEquals(1, records.stream().filter(r -> r.getLevel() == Level.SEVERE).count());
        assertEquals("warrior: rank 7", s.newerUnsaved(ann, 90).data(), "a rejoin continues from the kept document");
        assertNull(s.newerUnsaved(ann, 100), "not when the stored one is as new");
        db.failAll = false;
        s.retryUnsaved();
        w.runAll();
        assertEquals("warrior: rank 7", db.data(ann));
        assertEquals(0, s.unsavedCount());
    }

    @Test
    void aDocumentOlderThanTheStoredOneIsNotWrittenAgain() {
        FakeDb db = new FakeDb();
        HandRun w = new HandRun();
        ClassDocStore s = new ClassDocStore(db, log, dir.resolve("cr").toFile(), w, () -> 0, ms -> { });
        s.save(ann, "Ann", 3, () -> "new", 200);
        s.save(ann, "Ann", 3, () -> "old", 150); // a delayed save handed in later
        w.runAll();
        assertEquals("new", db.data(ann));
        assertEquals(1, db.attempts, "skipped without touching the database");
    }

    @Test
    void aDocumentHandedInButNeverRunBeforeTheWriterStopsGoesToARecoveryFile() {
        FakeDb db = new FakeDb();
        HandRun w = new HandRun() {
            @Override public boolean awaitTermination(long t, TimeUnit u) { return false; } // the writer is stuck
        };
        File recovery = dir.resolve("classes-recovery").toFile();
        ClassDocStore s = new ClassDocStore(db, log, recovery, w, () -> 0, ms -> { });
        s.shuttingDown();
        s.save(ann, "Ann", 3, () -> "ann: warrior 4", 400); // queued behind a write that never ends
        assertEquals(1, s.finish(10));
        assertEquals(0, db.attempts, "never written");
        FakeDb next = new FakeDb();
        new ClassDocStore(next, log, recovery, new HandRun(), () -> 0, ms -> { }).applyRecovery();
        assertEquals("ann: warrior 4", next.data(ann));
    }

    @Test
    void shutdownKeepsWhatItCannotWriteInRecoveryFilesAndTheNextStartWritesThemOnlyWhereNewer() {
        FakeDb db = new FakeDb();
        db.failAll = true;
        HandRun w = new HandRun();
        File recovery = dir.resolve("classes-recovery").toFile();
        ClassDocStore s = new ClassDocStore(db, log, recovery, w, () -> 0, ms -> { });
        UUID bob = new UUID(0, 2);
        s.shuttingDown();
        s.save(ann, "Ann", 3, () -> "ann: mage 4", 300);
        s.save(bob, "Bob", 3, () -> "bob: duelist 2", 50);
        assertEquals(2, s.finish(1000));
        assertEquals(2, recovery.listFiles().length);

        FakeDb next = new FakeDb();
        next.rows.put(bob, new Object[]{"bob: duelist 5", 80L}); // Bob's stored document is newer than his file
        ClassDocStore later = new ClassDocStore(next, log, recovery, new HandRun(), () -> 0, ms -> { });
        assertEquals(1, later.applyRecovery(), "Ann's written; Bob's file was older than his stored document: nothing written");
        assertEquals("ann: mage 4", next.data(ann));
        assertEquals("bob: duelist 5", next.data(bob));
        assertEquals(0, recovery.listFiles().length);
    }

    @Test
    void aRecoveryFileThatCannotBeWrittenAtStartupIsKeptUsedByAJoinAndRemovedOnceWritten() {
        FakeDb db = new FakeDb();
        db.failAll = true;
        File recovery = dir.resolve("classes-recovery").toFile();
        ClassDocStore first = new ClassDocStore(db, log, recovery, new HandRun(), () -> 0, ms -> { });
        first.shuttingDown();
        first.save(ann, "Ann", 3, () -> "ann: warrior 9", 900);
        assertEquals(1, first.finish(100));
        HandRun w = new HandRun();
        ClassDocStore second = new ClassDocStore(db, log, recovery, w, () -> 0, ms -> { });
        assertEquals(0, second.applyRecovery(), "the database still fails at startup");
        assertEquals(1, recovery.listFiles().length, "the file stays");
        assertEquals("ann: warrior 9", second.newerUnsaved(ann, 100).data(), "a join continues from it, not the older stored one");
        db.failAll = false;
        second.retryUnsaved();
        w.runAll();
        assertEquals("ann: warrior 9", db.data(ann));
        assertEquals(0, recovery.listFiles().length, "removed once written");
    }

    @Test
    void aWriteThatFailsWhileShutdownReadsWhatIsLeftIsStillKeptInARecoveryFile() {
        // shutdown has read the documents handed in when the write still running fails and moves its document to the kept ones
        FakeDb db = new FakeDb();
        db.failAll = true;
        HandRun w = new HandRun() {
            @Override public boolean awaitTermination(long t, TimeUnit u) { return false; }
            @Override public List<Runnable> shutdownNow() { shut = true; return List.of(); }
        };
        File recovery = dir.resolve("classes-recovery").toFile();
        ClassDocStore s = new ClassDocStore(db, log, recovery, w, () -> 0, ms -> { });
        s.save(ann, "Ann", 3, () -> "ann: warrior 6", 600);
        Runnable inFlight = w.queue.poll();
        s.shuttingDown();
        s.betweenReads = inFlight;
        assertEquals(1, s.finish(10), "found in one of the two, whichever moment it moved");
    }

    @Test
    void aRecoveryFileThatIsIncompleteOrChangedIsNotUsedAndIsLeftWhereItIs() throws Exception {
        FakeDb db = new FakeDb();
        db.failAll = true;
        File recovery = dir.resolve("classes-recovery").toFile();
        ClassDocStore first = new ClassDocStore(db, log, recovery, new HandRun(), () -> 0, ms -> { });
        first.shuttingDown();
        first.save(ann, "Ann", 3, () -> "warrior: rank 7", 700);
        first.finish(100);
        File f = recovery.listFiles((d, n) -> n.endsWith(".yml"))[0];
        String text = java.nio.file.Files.readString(f.toPath());
        java.nio.file.Files.writeString(f.toPath(), text.replace("rank 7", "rank 9"));
        db.failAll = false;
        assertEquals(0, new ClassDocStore(db, log, recovery, new HandRun(), () -> 0, ms -> { }).applyRecovery());
        assertNull(db.data(ann), "nothing written");
        assertTrue(f.exists(), "left for a person to look at");
    }

    @Test
    void aRealMultiLineDocumentComesBackFromItsRecoveryFileExactlyAndPassesItsCheck() {
        String doc = "version: 1\nclasses:\n  warrior:\n    rank: 8\n    xp: 0\nloadouts:\n  '0':\n    name: Loadout 1\n"
                + "    actives:\n    - ''\n    - execute\n    passives:\n    - ''\n    keystone: ''\nactive: 0\n"
                + "note: \"tabs\\tand trailing spaces  \"\n";
        FakeDb db = new FakeDb();
        db.failAll = true;
        File recovery = dir.resolve("classes-recovery").toFile();
        ClassDocStore first = new ClassDocStore(db, log, recovery, new HandRun(), () -> 0, ms -> { });
        first.shuttingDown();
        first.save(ann, "Ann", 1, () -> doc, 800);
        assertEquals(1, first.finish(100));
        db.failAll = false;
        assertEquals(1, new ClassDocStore(db, log, recovery, new HandRun(), () -> 0, ms -> { }).applyRecovery(), "its check matches");
        assertEquals(doc, db.data(ann), "byte for byte");
    }
}
