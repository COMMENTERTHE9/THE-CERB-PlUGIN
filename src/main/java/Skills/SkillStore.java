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
import java.util.concurrent.Executors;
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
        /** One snapshot in one transaction; a row a newer snapshot wrote is kept. Throws on a database error. */
        void write(String customId, Map<String, Integer> levels, Map<String, Integer> xp, long seq) throws SQLException;

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
    private final Map<UUID, Long> reported = new ConcurrentHashMap<>();
    private volatile Consumer<String> alert = m -> { };
    private volatile long lastAlert;
    private volatile long lastFailure = -REPORT_EVERY_MS;
    private volatile boolean shuttingDown;
    private final AtomicLong writes = new AtomicLong(), skipped = new AtomicLong(), failures = new AtomicLong(),
            recovered = new AtomicLong();

    public SkillStore(Db db, Logger log, File recoveryDir) {
        this(db, log, recoveryDir, Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "Cerb-DB-Skills");
            t.setDaemon(true);
            return t;
        }), System::currentTimeMillis, SkillStore::pause);
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

    /** Continue the save order after {@code storedMax}, the newest snapshot in the database (startup). */
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
        int attempts = shuttingDown || failing ? 1 : ATTEMPTS;
        SQLException last = null;
        for (int a = 1; a <= attempts; a++) {
            try {
                db.write(customId(s), s.levels(), s.xp(), s.seq());
                stored.merge(player, s.seq(), Math::max);
                newest.remove(player, s); // a newer one handed in meanwhile stays, its own task writes it
                Snapshot kept = unsaved.get(player);
                if (kept != null && kept.seq() <= s.seq()) {
                    unsaved.remove(player, kept);
                    log.info("[Skills] " + s.name() + "'s skills are saved again (the progress kept in memory is stored).");
                }
                writes.incrementAndGet();
                return;
            } catch (SQLException e) {
                last = e;
                if (a < attempts && !shuttingDown) sleeper.accept(BACKOFF_MS[Math.min(a - 1, BACKOFF_MS.length - 1)]);
                else break; // shutting down: the final snapshots waiting behind this one need the time
            }
        }
        failures.incrementAndGet();
        lastFailure = clock.getAsLong();
        unsaved.merge(player, s, (a, b) -> b.seq() > a.seq() ? b : a);
        newest.remove(player, s);
        report(s, last);
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
        Map<UUID, Snapshot> left = new HashMap<>();
        for (Snapshot s : unsaved.values()) left.merge(s.player(), s, (a, b) -> b.seq() > a.seq() ? b : a);
        for (Snapshot s : newest.values()) left.merge(s.player(), s, (a, b) -> b.seq() > a.seq() ? b : a);
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
                + ", kept in memory " + unsaved.size() + ", recovered at startup " + recovered.get();
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
            try {
                y.save(new File(recoveryDir, s.player() + ".yml"));
                ok++;
            } catch (java.io.IOException e) {
                log.severe("[Skills] Could not write the recovery file for " + s.name() + ": " + e.getMessage());
            }
        }
        return ok;
    }

    /**
     * At startup, before anyone joins: write each recovery file to the database (kept only where it is newer than what
     * is stored) and delete the files written. A file that can't be written stays for the next start. Returns the
     * newest sequence number seen in them (the save order continues after it).
     */
    public long applyRecovery() {
        File[] files = recoveryDir.listFiles((d, n) -> n.endsWith(".yml"));
        if (files == null || files.length == 0) return 0;
        long newestSeen = 0;
        for (File f : files) {
            YamlConfiguration y = YamlConfiguration.loadConfiguration(f);
            try {
                UUID player = UUID.fromString(y.getString("player", ""));
                long s = y.getLong("seq");
                newestSeen = Math.max(newestSeen, s);
                Map<String, Integer> levels = ints(y.getConfigurationSection("levels"));
                Map<String, Integer> xp = ints(y.getConfigurationSection("xp"));
                String id = y.getString("custom-id");
                if (id == null) id = db.customId(player);
                db.write(id, levels, xp, s);
                recovered.incrementAndGet();
                log.warning("[Skills] Wrote " + y.getString("name", player.toString()) + "'s skills from " + f.getName()
                        + " (progress that could not be saved at the last shutdown; kept only where newer).");
                if (!f.delete()) log.warning("[Skills] Could not delete " + f.getPath() + "; it is harmless to write again.");
            } catch (IllegalArgumentException | SQLException e) {
                log.severe("[Skills] Could not apply " + f.getPath() + ": " + e.getMessage() + " (kept for the next start).");
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
