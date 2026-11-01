package Skills;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import java.util.function.LongSupplier;
import java.util.logging.Logger;

/**
 * Where players' skill levels and XP are stored (docs/STATUS.md → "Done: reliability audit"). The only way skills reach
 * the database:
 * <ul>
 *   <li><b>One writer, in order.</b> Every save (an XP gain's, a quit's, a backup's, shutdown's) is a snapshot taken on the
 *   player's own thread with a sequence number that only grows, and one thread writes them. A player's newest snapshot is
 *   written; one older than what is already stored is skipped. The database keeps a row only from a snapshot at least as
 *   new as the one that wrote it ({@code saved_seq}, cerberus.world.cerb.SkillRows), so even a write that somehow
 *   commits late can't replace newer progress.</li>
 *   <li><b>Loads on the same thread.</b> A rejoin is read after the quit's save, never before it.</li>
 *   <li><b>A failed write is said and kept.</b> It is tried again (three attempts in all), then logged as an error (and told
 *   to admins online); the snapshot stays in memory, a rejoin loads it in place of the older stored rows, and it is written
 *   as soon as the database answers ({@link #retryUnsaved}, every 30 s) or at shutdown. What shutdown still can't write
 *   goes to recovery files, written to the database at the next start (only where newer).</li>
 * </ul>
 * It does not protect against a crash or power loss between an XP gain and its save (at most about a second of play,
 * the save throttle's window), or against losing the disk.
 */
public final class SkillStore {

    /** The database side (DatabaseManager), for tests to stand in for. */
    public interface Db {
        /**
         * One snapshot in one transaction; a row a newer snapshot wrote is kept. Returns the rows written (0: the database
         * already held a newer save of every skill). Throws on a database error.
         */
        int write(String customId, Map<String, Integer> levels, Map<String, Integer> xp, long seq) throws SQLException;

        /** The player's stored custom id; throws when it can't be read or doesn't exist (never makes a new one). */
        String customId(UUID player) throws SQLException;
    }

    /** One player's skills at one moment. {@code seq} orders the snapshots: it only ever grows. */
    public record Snapshot(UUID player, String name, Map<String, Integer> levels, Map<String, Integer> xp, long seq) {}

    static final int ATTEMPTS = 3;
    static final long[] BACKOFF_MS = {500, 1500};
    static final long REPORT_EVERY_MS = 60_000;

    private final Db db;
    private final Logger log;
    private final File recoveryDir;
    private final ExecutorService writer;
    private final LongSupplier clock;
    private final java.util.function.LongConsumer sleeper;
    private final AtomicLong seq = new AtomicLong();
    /** The newest seq known to be stored, per player. */
    private final Map<UUID, Long> stored = new ConcurrentHashMap<>();
    /** The newest snapshot handed in and not yet stored. */
    private final Map<UUID, Snapshot> newest = new ConcurrentHashMap<>();
    /** The newest snapshot whose write failed (kept until a write of it, or of something newer, succeeds). */
    private final Map<UUID, Snapshot> unsaved = new ConcurrentHashMap<>();
    private final Map<UUID, String> customIds = new ConcurrentHashMap<>();
    /** A recovery file that couldn't be written at startup: deleted once its progress (or newer) is stored. */
    private record PendingFile(File file, long seq) {}
    private final Map<UUID, PendingFile> pendingFiles = new ConcurrentHashMap<>();
    /** When each player's failure, and separately each player's "changed nothing", was last logged (once a minute each). */
    private final Map<UUID, Long> reported = new ConcurrentHashMap<>(), reportedNoop = new ConcurrentHashMap<>();
    /** Tests only: runs in {@link #shutdown} between reading the waiting snapshots and the failed ones. */
    Runnable betweenReads = () -> { };
    private volatile Consumer<String> alert = m -> { };
    private volatile long lastAlert;
    private volatile long lastFailure = -REPORT_EVERY_MS;
    private volatile boolean shuttingDown;
    private final AtomicLong writes = new AtomicLong(), skipped = new AtomicLong(), failures = new AtomicLong(),
            recovered = new AtomicLong(), olderThanStored = new AtomicLong();

    public SkillStore(Db db, Logger log, File recoveryDir) {
        this(db, log, recoveryDir, new java.util.concurrent.ThreadPoolExecutor(1, 1, 0, TimeUnit.MILLISECONDS,
                new java.util.concurrent.LinkedBlockingQueue<>(), r -> {
            Thread t = new Thread(r, "Cerb-DB-Skills");
            t.setDaemon(true);
            return t;
        }), System::currentTimeMillis, SkillStore::pause);
    }

    /** Diagnostics: tasks waiting for the writer (saves and loads), or -1 when the writer can't say. */
    public int queued() {
        return writer instanceof java.util.concurrent.ThreadPoolExecutor tpe ? tpe.getQueue().size() : -1;
    }

    /** For tests: a writer, a clock and a way to wait of their own. */
    SkillStore(Db db, Logger log, File recoveryDir, ExecutorService writer, LongSupplier clock,
               java.util.function.LongConsumer sleeper) {
        this.db = db;
        this.log = log;
        this.recoveryDir = recoveryDir;
        this.writer = writer;
        this.clock = clock;
        this.sleeper = sleeper;
    }

    /** The writer thread. Loads run on it too, so a load comes after every save handed in before it. */
    public Executor executor() {
        return writer;
    }

    /** Where failures are told besides the log (admins online); called on the writer thread. */
    public void onAlert(Consumer<String> alert) {
        this.alert = alert == null ? m -> { } : alert;
    }

    /**
     * Continue the save order after {@code storedMax}: at startup the newest snapshot in the database, and at each load
     * that player's own newest stored row, so their next save is always newer than what is stored (even when the startup
     * read failed and the order had to start from the clock).
     */
    public void continueAfter(long storedMax) {
        seq.accumulateAndGet(storedMax, Math::max);
    }

    /** The player's custom id, known from their load: saves then never look it up. */
    public void knowCustomId(UUID player, String customId) {
        if (customId != null) customIds.put(player, customId);
    }

    /**
     * A copy of a player's skills with the next sequence number. Take it on the player's own thread (where their skills
     * change), so a later change always gets a later number.
     */
    public Snapshot snapshot(UUID player, String name, Map<String, Integer> levels, Map<String, Integer> xp) {
        return new Snapshot(player, name, Map.copyOf(levels), Map.copyOf(xp), seq.incrementAndGet());
    }

    /** Hand a snapshot to the writer. It is written after everything handed in before it, unless something newer is. */
    public void save(Snapshot s) {
        newest.merge(s.player(), s, (a, b) -> b.seq() > a.seq() ? b : a);
        try {
            writer.execute(() -> writeNewest(s.player()));
        } catch (RejectedExecutionException e) {
            // after shutdown: shutdown took every player's final snapshot (and wrote or kept what it could)
            log.fine("[Skills] A save of " + s.name() + " arrived after shutdown (seq " + s.seq() + ").");
        }
    }

    private void writeNewest(UUID player) {
        Snapshot s = newest.get(player);
        if (s == null) return; // written by an earlier task of this player's
        if (s.seq() <= stored.getOrDefault(player, 0L)) { // something newer is already stored
            newest.remove(player, s);
            skipped.incrementAndGet();
            return;
        }
        boolean failing = clock.getAsLong() - lastFailure < REPORT_EVERY_MS;
        // a recovery file's progress the start couldn't write: one attempt each time, and its failures never make other
        // players' saves give up early (it may fail for a reason of its own; 2026-11 review)
        PendingFile file = pendingFiles.get(player);
        boolean fromFile = file != null && file.seq() == s.seq();
        int attempts = shuttingDown || failing || fromFile ? 1 : ATTEMPTS;
        SQLException last = null;
        for (int a = 1; a <= attempts; a++) {
            try {
                int rows = db.write(customId(s), s.levels(), s.xp(), s.seq());
                stored.merge(player, s.seq(), Math::max);
                newest.remove(player, s); // a newer one handed in meanwhile stays, its own task writes it
                Snapshot kept = unsaved.get(player);
                if (kept != null && kept.seq() <= s.seq()) {
                    unsaved.remove(player, kept);
                    log.info("[Skills] " + s.name() + "'s skills are saved again (the progress kept in memory is stored).");
                }
                PendingFile pf = pendingFiles.get(player);
                if (pf != null && pf.seq() <= s.seq() && pendingFiles.remove(player, pf) && !pf.file().delete() && pf.file().exists()) {
                    log.warning("[Skills] Could not delete " + pf.file().getPath() + "; it is harmless (older than what is stored now).");
                }
                if (rows == 0 && !s.levels().isEmpty()) noteOlderThanStored(s); else writes.incrementAndGet();
                return;
            } catch (SQLException e) {
                last = e;
                if (a < attempts && !shuttingDown) sleeper.accept(BACKOFF_MS[Math.min(a - 1, BACKOFF_MS.length - 1)]);
                else break; // shutting down: the final snapshots waiting behind this one need the time
            }
        }
        failures.incrementAndGet();
        if (!fromFile) lastFailure = clock.getAsLong();
        unsaved.merge(player, s, (a, b) -> b.seq() > a.seq() ? b : a);
        newest.remove(player, s);
        report(s, last);
    }

    /**
     * The database held a newer save of every skill than this snapshot (its saved_seq is higher), so nothing was written.
     * The save order continues after each player's stored rows ({@link #continueAfter} at their load), so this means
     * something else wrote them: said, never silent.
     */
    private void noteOlderThanStored(Snapshot s) {
        olderThanStored.incrementAndGet();
        long now = clock.getAsLong();
        Long before = reportedNoop.get(s.player());
        if (before == null || now - before >= REPORT_EVERY_MS) {
            reportedNoop.put(s.player(), now);
            log.warning("[Skills] " + s.name() + "'s save #" + s.seq() + " changed nothing: the database already holds a newer"
                    + " save of their skills (written by something else than this server's save order). The stored rows were kept.");
        }
    }

    private String customId(Snapshot s) throws SQLException {
        String id = customIds.get(s.player());
        if (id == null) {
            id = db.customId(s.player());
            customIds.put(s.player(), id);
        }
        return id;
    }

    private void report(Snapshot s, SQLException e) {
        long now = clock.getAsLong();
        Long before = reported.get(s.player());
        if (before == null || now - before >= REPORT_EVERY_MS) {
            reported.put(s.player(), now);
            log.severe("[Skills] Could not save " + s.name() + "'s skills: " + e.getMessage() + ". Their progress is kept in"
                    + " memory: it is used if they rejoin, and saved as soon as the database answers (at shutdown, into "
                    + recoveryDir.getName() + "/ if it still can't be).");
        }
        if (now - lastAlert >= REPORT_EVERY_MS) {
            lastAlert = now;
            alert.accept("Saving player skills is failing (" + e.getMessage() + "). Progress is kept in memory and saved"
                    + " when the database answers. See the server log.");
        }
    }

    /**
     * In a load (on the writer thread): the snapshot kept in memory after a failed write, when it is newer than the
     * stored rows ({@code storedSeq}); the player continues from it. Null otherwise.
     */
    public Snapshot newerUnsaved(UUID player, long storedSeq) {
        Snapshot u = unsaved.get(player);
        return u != null && u.seq() > storedSeq ? u : null;
    }

    /** Hand every snapshot kept after a failed write to the writer again (a timer calls this). */
    public void retryUnsaved() {
        for (Snapshot u : List.copyOf(unsaved.values())) save(u);
    }

    /**
     * At shutdown, after every player's final snapshot was handed in: write what can be written within
     * {@code timeoutMs} (one attempt each), then put what is still not stored into recovery files. Returns how many
     * players' progress went to recovery files.
     */
    public int shutdown(long timeoutMs) {
        shuttingDown = true;
        retryUnsaved();
        writer.shutdown();
        try {
            if (!writer.awaitTermination(timeoutMs, TimeUnit.MILLISECONDS)) writer.shutdownNow();
        } catch (InterruptedException e) {
            writer.shutdownNow();
            Thread.currentThread().interrupt();
        }
        // newest before unsaved: a write still running moves a failed snapshot from the first to the second (unsaved, then
        // newest removed), so read in this order it is seen in at least one of them
        Map<UUID, Snapshot> left = new HashMap<>();
        for (Snapshot s : newest.values()) left.merge(s.player(), s, (a, b) -> b.seq() > a.seq() ? b : a);
        betweenReads.run();
        for (Snapshot s : unsaved.values()) left.merge(s.player(), s, (a, b) -> b.seq() > a.seq() ? b : a);
        left.values().removeIf(s -> s.seq() <= stored.getOrDefault(s.player(), 0L));
        log.info("[Skills] Saves this run: " + stats());
        if (left.isEmpty()) return 0;
        int kept = writeRecovery(left.values());
        log.severe("[Skills] " + left.size() + " player(s)' skills could not be saved to the database at shutdown; " + kept
                + " kept in " + recoveryDir.getPath() + " and written to the database at the next start.");
        return kept;
    }

    /** Diagnostics: snapshots written, skipped as older than what was stored, failed writes, kept in memory now. */
    public String stats() {
        return "written " + writes.get() + ", skipped (older than stored) " + skipped.get() + ", failed " + failures.get()
                + ", kept in memory " + unsaved.size() + ", recovered at startup " + recovered.get()
                + (pendingFiles.isEmpty() ? "" : ", recovery files waiting " + pendingFiles.size())
                + (olderThanStored.get() == 0 ? "" : ", changed nothing (database newer) " + olderThanStored.get());
    }

    // ── recovery files ───────────────────────────────────────────

    private int writeRecovery(Collection<Snapshot> snaps) {
        if (!recoveryDir.isDirectory() && !recoveryDir.mkdirs()) {
            log.severe("[Skills] Could not create " + recoveryDir.getPath() + ": the unsaved progress above is lost.");
            return 0;
        }
        int ok = 0;
        for (Snapshot s : snaps) {
            YamlConfiguration y = new YamlConfiguration();
            y.set("player", s.player().toString());
            y.set("name", s.name());
            y.set("custom-id", customIds.get(s.player()));
            y.set("seq", s.seq());
            for (Map.Entry<String, Integer> e : s.levels().entrySet()) y.set("levels." + e.getKey(), e.getValue());
            for (Map.Entry<String, Integer> e : s.xp().entrySet()) y.set("xp." + e.getKey(), e.getValue());
            y.set("check", check(s.player(), s.seq(), s.levels(), s.xp()));
            try {
                saveAtomically(y, new File(recoveryDir, s.player() + ".yml"));
                ok++;
            } catch (java.io.IOException e) {
                log.severe("[Skills] Could not write the recovery file for " + s.name() + ": " + e.getMessage());
            }
        }
        return ok;
    }

    /**
     * A file is written whole or not at all: to a temporary file beside it, then moved over it (a stop cut short can't
     * leave half a file where the last good one was).
     */
    public static void saveAtomically(YamlConfiguration y, File target) throws java.io.IOException {
        File tmp = new File(target.getParentFile(), target.getName() + ".tmp");
        y.save(tmp);
        try {
            java.nio.file.Files.move(tmp.toPath(), target.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING,
                    java.nio.file.StandardCopyOption.ATOMIC_MOVE);
        } catch (java.nio.file.AtomicMoveNotSupportedException e) {
            java.nio.file.Files.move(tmp.toPath(), target.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        }
    }

    /** What a recovery file says it holds, as a number: a file whose content doesn't match it was cut short or edited. */
    static long check(UUID player, long seq, Map<String, Integer> levels, Map<String, Integer> xp) {
        StringBuilder b = new StringBuilder().append(player).append('|').append(seq);
        for (String k : new java.util.TreeSet<>(levels.keySet())) b.append('|').append(k).append('=').append(levels.get(k));
        b.append("|xp");
        for (String k : new java.util.TreeSet<>(xp.keySet())) b.append('|').append(k).append('=').append(xp.get(k));
        java.util.zip.CRC32 crc = new java.util.zip.CRC32();
        crc.update(b.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
        return crc.getValue();
    }

    /**
     * The progress a join continues from: the stored rows with the kept snapshot laid over them (the kept snapshot is newer
     * for every skill it holds; a skill it doesn't hold keeps its stored value).
     */
    public static Map<String, Integer> overlay(Map<String, Integer> stored, Map<String, Integer> kept) {
        Map<String, Integer> out = new HashMap<>(stored);
        out.putAll(kept);
        return out;
    }

    /**
     * At startup, before anyone joins: write each recovery file to the database (kept only where it is newer than what
     * is stored) and delete the files written. A file that can't be written now (the database is still failing) is kept
     * in memory like a failed save: a join continues from it, it is written as soon as the database answers, and its file
     * is deleted only then (a shutdown before that writes it again). Returns the newest sequence number seen in them (the
     * save order continues after it).
     */
    public long applyRecovery() {
        File[] files = recoveryDir.listFiles((d, n) -> n.endsWith(".yml"));
        if (files == null || files.length == 0) return 0;
        long newestSeen = 0;
        for (File f : files) {
            YamlConfiguration y = YamlConfiguration.loadConfiguration(f);
            UUID player;
            try {
                player = UUID.fromString(y.getString("player", ""));
            } catch (IllegalArgumentException e) {
                log.severe("[Skills] Could not read " + f.getPath() + " (no player in it); it is left where it is.");
                continue;
            }
            long s = y.getLong("seq");
            String name = y.getString("name", player.toString());
            Map<String, Integer> levels = ints(y.getConfigurationSection("levels"));
            Map<String, Integer> xp = ints(y.getConfigurationSection("xp"));
            if (!y.isLong("check") && !y.isInt("check") || y.getLong("check") != check(player, s, levels, xp)) {
                log.severe("[Skills] " + f.getPath() + " (" + name + ") is incomplete or was changed by hand (its check doesn't"
                        + " match): it is not used, and left where it is for a person to look at.");
                continue;
            }
            newestSeen = Math.max(newestSeen, s);
            String id = y.getString("custom-id");
            try {
                if (id == null) id = db.customId(player);
                int rows = db.write(id, levels, xp, s);
                if (rows > 0) {
                    recovered.incrementAndGet();
                    log.warning("[Skills] Wrote " + name + "'s skills from " + f.getName()
                            + " (progress that could not be saved at the last shutdown; kept only where newer).");
                } else if (levels.isEmpty()) {
                    log.warning("[Skills] " + f.getName() + " (" + name + ") held no skills: nothing to write.");
                } else {
                    log.warning("[Skills] " + f.getName() + " (" + name + ") was older than their stored skills, which a later"
                            + " save replaced: nothing to write.");
                }
                if (!f.delete()) log.warning("[Skills] Could not delete " + f.getPath() + "; it is harmless to write again.");
            } catch (SQLException e) {
                if (id != null) customIds.putIfAbsent(player, id);
                unsaved.merge(player, new Snapshot(player, name, Map.copyOf(levels), Map.copyOf(xp), s),
                        (a, b) -> b.seq() > a.seq() ? b : a);
                pendingFiles.put(player, new PendingFile(f, s));
                log.severe("[Skills] Could not write " + f.getPath() + " (" + e.getMessage() + "). " + name + "'s progress in it"
                        + " is kept in memory: used if they join, and written as soon as the database answers; the file stays"
                        + " until then.");
            }
        }
        return newestSeen;
    }

    private static Map<String, Integer> ints(ConfigurationSection sec) {
        Map<String, Integer> out = new HashMap<>();
        if (sec != null) for (String k : sec.getKeys(false)) out.put(k, sec.getInt(k));
        return out;
    }

    private static void pause(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /** For tests: the snapshots kept after failed writes. */
    Collection<Snapshot> unsavedNow() {
        return new ArrayList<>(unsaved.values());
    }
}
