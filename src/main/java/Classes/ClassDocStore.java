package Classes;

import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.function.LongConsumer;
import java.util.function.LongSupplier;
import java.util.function.Supplier;
import java.util.logging.Logger;

/**
 * How class documents reach the database (ClassService's writer thread; docs/STATUS.md → reliability audit). Order was
 * already safe (one writer, and a stamp the database compares). What this adds is what happens when a write fails:
 * <ul>
 *   <li>it is tried again (three attempts), then logged as an error, and the document is kept in memory;</li>
 *   <li>a rejoin loads the kept document in place of an older stored one;</li>
 *   <li>it is written as soon as the database answers ({@link #retryUnsaved}) or at shutdown;</li>
 *   <li>what shutdown still can't write goes to recovery files, written at the next start (only where newer).</li>
 * </ul>
 * Before, a failed quit or shutdown save was logged and the progress since the last good save was gone.
 */
final class ClassDocStore {

    interface Db {
        /** Write a document; the database keeps a newer stored one (returns 0 then, else 1). Throws on a database error. */
        int write(UUID player, int version, String data, long stamp) throws SQLException;
    }

    /** A document kept after a failed write. */
    record Pending(int version, String data, long stamp, String name) {}

    /** A document handed in and not yet written (its text is built on the writer thread, or at shutdown). */
    private record Handed(int version, Supplier<String> data, long stamp, String name) {}

    static final int ATTEMPTS = 3;
    static final long[] BACKOFF_MS = {500, 1500};
    static final long REPORT_EVERY_MS = 60_000;

    private final Db db;
    private final Logger log;
    private final File recoveryDir;
    private final ExecutorService writer;
    private final LongSupplier clock;
    private final LongConsumer sleeper;
    private final Map<UUID, Long> stored = new ConcurrentHashMap<>();
    private final Map<UUID, Pending> unsaved = new ConcurrentHashMap<>();
    /** The newest document handed in per player, until it is written or kept (shutdown keeps it if never run). */
    private final Map<UUID, Handed> handedIn = new ConcurrentHashMap<>();
    private final Map<UUID, Long> reported = new ConcurrentHashMap<>();
    /** A recovery file that couldn't be written at startup: deleted once its document (or a newer one) is stored. */
    private record PendingFile(File file, long stamp) {}
    private final Map<UUID, PendingFile> pendingFiles = new ConcurrentHashMap<>();
    /** Tests only: runs in {@link #finish} between reading the documents handed in and the ones kept after a failure. */
    Runnable betweenReads = () -> { };
    private volatile long lastFailure = -REPORT_EVERY_MS;
    private volatile boolean shuttingDown;

    ClassDocStore(Db db, Logger log, File recoveryDir, ExecutorService writer, LongSupplier clock, LongConsumer sleeper) {
        this.db = db;
        this.log = log;
        this.recoveryDir = recoveryDir;
        this.writer = writer;
        this.clock = clock;
        this.sleeper = sleeper;
    }

    /**
     * Write a document on the writer thread ({@code data} is built there), after everything handed in before it; during
     * shutdown, once the writer is closed, here on the caller's thread.
     */
    void save(UUID player, String name, int version, Supplier<String> data, long stamp) {
        handedIn.merge(player, new Handed(version, data, stamp, name), (a, b) -> b.stamp() > a.stamp() ? b : a);
        try {
            writer.execute(() -> writeHanded(player));
        } catch (RejectedExecutionException e) {
            writeHanded(player); // the writer is closed (shutdown): write here
        }
    }

    private void writeHanded(UUID player) {
        Handed h = handedIn.get(player);
        if (h == null) return; // written by an earlier task of this player's
        if (h.stamp() <= stored.getOrDefault(player, Long.MIN_VALUE)) { // a newer document is stored
            handedIn.remove(player, h);
            return;
        }
        String data = h.data().get();
        boolean failing = clock.getAsLong() - lastFailure < REPORT_EVERY_MS;
        // a recovery file's document the start couldn't write: one attempt, and its failures never make other players'
        // saves give up early (2026-11 review)
        PendingFile file = pendingFiles.get(player);
        boolean fromFile = file != null && file.stamp() == h.stamp();
        int attempts = shuttingDown || failing || fromFile ? 1 : ATTEMPTS;
        SQLException last = null;
        for (int a = 1; a <= attempts; a++) {
            try {
                int rows = db.write(player, h.version(), data, h.stamp());
                stored.merge(player, h.stamp(), Math::max);
                handedIn.remove(player, h); // a newer one handed in meanwhile stays, its own task writes it
                Pending kept = unsaved.get(player);
                if (kept != null && kept.stamp() <= h.stamp()) {
                    unsaved.remove(player, kept);
                    log.info("[Classes] " + h.name() + "'s classes are saved again (the document kept in memory is stored).");
                }
                PendingFile pf = pendingFiles.get(player);
                if (pf != null && pf.stamp() <= h.stamp() && pendingFiles.remove(player, pf) && !pf.file().delete() && pf.file().exists()) {
                    log.warning("[Classes] Could not delete " + pf.file().getPath() + "; it is harmless (older than what is stored now).");
                }
                if (rows == 0) {
                    log.warning("[Classes] " + h.name() + "'s classes (stamp " + h.stamp() + ") changed nothing: the database"
                            + " already holds a newer document. The stored one was kept.");
                }
                return;
            } catch (SQLException e) {
                last = e;
                if (a < attempts && !shuttingDown) sleeper.accept(BACKOFF_MS[Math.min(a - 1, BACKOFF_MS.length - 1)]);
                else break; // shutting down: the final documents waiting behind this one need the time
            }
        }
        if (!fromFile) lastFailure = clock.getAsLong();
        unsaved.merge(player, new Pending(h.version(), data, h.stamp(), h.name()), (a, b) -> b.stamp() > a.stamp() ? b : a);
        handedIn.remove(player, h);
        long now = clock.getAsLong();
        Long before = reported.get(player);
        if (before == null || now - before >= REPORT_EVERY_MS) {
            reported.put(player, now);
            log.severe("[Classes] Could not save " + h.name() + "'s classes: " + last.getMessage() + ". The document is kept"
                    + " in memory: it is used if they rejoin, and saved as soon as the database answers (at shutdown, into "
                    + recoveryDir.getName() + "/ if it still can't be).");
        }
    }

    /** In a load (on the writer thread): the kept document when it is newer than the stored one ({@code storedStamp}). */
    Pending newerUnsaved(UUID player, long storedStamp) {
        Pending p = unsaved.get(player);
        return p != null && p.stamp() > storedStamp ? p : null;
    }

    /** Hand every kept document to the writer again (a timer calls this). */
    void retryUnsaved() {
        for (Map.Entry<UUID, Pending> e : List.copyOf(unsaved.entrySet())) {
            Pending p = e.getValue();
            save(e.getKey(), p.name(), p.version(), p::data, p.stamp());
        }
    }

    /** Shutdown has begun: one attempt per write from now on. */
    void shuttingDown() {
        shuttingDown = true;
    }

    /**
     * After every loaded profile was handed in: try the kept documents once more, let the writer finish (at most
     * {@code timeoutMs}), then put what is still unsaved into recovery files. Returns how many files were written.
     */
    int finish(long timeoutMs) {
        shuttingDown = true;
        retryUnsaved();
        writer.shutdown();
        try {
            if (!writer.awaitTermination(timeoutMs, TimeUnit.MILLISECONDS)) writer.shutdownNow();
        } catch (InterruptedException e) {
            writer.shutdownNow();
            Thread.currentThread().interrupt();
        }
        // What is still not stored: handed in and never run (the writer was stopped), or kept after a failed write. Read in
        // this order: a write still running moves a failed document from the first to the second (kept, then removed), so it
        // is seen in at least one of them (2026-11 review).
        Map<UUID, Handed> handed = new java.util.HashMap<>(handedIn);
        betweenReads.run();
        Map<UUID, Pending> left = new java.util.HashMap<>(unsaved);
        for (Map.Entry<UUID, Handed> e : handed.entrySet()) {
            Handed h = e.getValue();
            Pending was = left.get(e.getKey());
            if (was != null && h.stamp() <= was.stamp()) continue;
            try {
                left.put(e.getKey(), new Pending(h.version(), h.data().get(), h.stamp(), h.name()));
            } catch (RuntimeException ex) { // its text can't be built: the others still get their files
                log.severe("[Classes] Could not build " + h.name() + "'s classes document at shutdown: " + ex);
            }
        }
        int files = 0;
        for (Map.Entry<UUID, Pending> e : left.entrySet()) {
            Pending p = e.getValue();
            if (p.stamp() <= stored.getOrDefault(e.getKey(), Long.MIN_VALUE)) continue;
            if (!recoveryDir.isDirectory() && !recoveryDir.mkdirs()) {
                log.severe("[Classes] Could not create " + recoveryDir.getPath() + ": the classes that could not be saved"
                        + " above are lost.");
                break;
            }
            YamlConfiguration y = new YamlConfiguration();
            y.set("player", e.getKey().toString());
            y.set("name", p.name());
            y.set("version", p.version());
            y.set("stamp", p.stamp());
            y.set("data", p.data());
            y.set("check", check(e.getKey(), p.version(), p.stamp(), p.data()));
            try {
                Skills.SkillStore.saveAtomically(y, new File(recoveryDir, e.getKey() + ".yml"));
                files++;
            } catch (java.io.IOException ex) {
                log.severe("[Classes] Could not write the recovery file for " + e.getKey() + ": " + ex.getMessage());
            }
        }
        if (files > 0) {
            log.severe("[Classes] " + files + " player(s)' classes could not be saved to the database at shutdown; kept in "
                    + recoveryDir.getPath() + " and written to the database at the next start.");
        }
        return files;
    }

    /**
     * At startup, before anyone joins: write each recovery file (the database keeps a newer stored document) and delete
     * the files written. A file that can't be written now (the database is still failing) is kept in memory like a failed
     * save: a join continues from it, it is written as soon as the database answers, and its file is deleted only then.
     * Returns how many were written.
     */
    int applyRecovery() {
        File[] files = recoveryDir.listFiles((d, n) -> n.endsWith(".yml"));
        if (files == null) return 0;
        int done = 0;
        for (File f : files) {
            YamlConfiguration y = YamlConfiguration.loadConfiguration(f);
            UUID player;
            String data = y.getString("data");
            try {
                player = UUID.fromString(y.getString("player", ""));
                if (data == null) throw new IllegalArgumentException("no data");
            } catch (IllegalArgumentException e) {
                log.severe("[Classes] Could not read " + f.getPath() + " (" + e.getMessage() + "); it is left where it is.");
                continue;
            }
            int version = y.getInt("version");
            long stamp = y.getLong("stamp");
            String name = y.getString("name", player.toString());
            if (!y.isLong("check") && !y.isInt("check") || y.getLong("check") != check(player, version, stamp, data)) {
                log.severe("[Classes] " + f.getPath() + " (" + name + ") is incomplete or was changed by hand (its check doesn't"
                        + " match): it is not used, and left where it is for a person to look at.");
                continue;
            }
            try {
                if (db.write(player, version, data, stamp) > 0) {
                    done++;
                    log.warning("[Classes] Wrote " + name + "'s classes from " + f.getName()
                            + " (a document that could not be saved at the last shutdown; kept only where newer).");
                } else {
                    log.warning("[Classes] " + f.getName() + " (" + name + ") was older than their stored classes: nothing to write.");
                }
                if (!f.delete()) log.warning("[Classes] Could not delete " + f.getPath() + "; it is harmless to write again.");
            } catch (SQLException e) {
                unsaved.merge(player, new Pending(version, data, stamp, name), (a, b) -> b.stamp() > a.stamp() ? b : a);
                pendingFiles.put(player, new PendingFile(f, stamp));
                log.severe("[Classes] Could not write " + f.getPath() + " (" + e.getMessage() + "). " + name + "'s classes in it"
                        + " are kept in memory: used if they join, and written as soon as the database answers; the file"
                        + " stays until then.");
            }
        }
        return done;
    }

    /** What a recovery file says it holds, as a number: a file whose content doesn't match it was cut short or edited. */
    static long check(UUID player, int version, long stamp, String data) {
        java.util.zip.CRC32 crc = new java.util.zip.CRC32();
        crc.update((player + "|" + version + "|" + stamp + "|" + data).getBytes(java.nio.charset.StandardCharsets.UTF_8));
        return crc.getValue();
    }

    /** For tests and diagnostics: how many documents are kept after failed writes. */
    int unsavedCount() {
        return unsaved.size();
    }
}
