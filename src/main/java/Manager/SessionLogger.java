package Manager;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;
import java.util.stream.Stream;

/**
 * Per-player ring buffer that captures structured events in TOON format
 * and flushes to disk on quit or on demand via {@code /cerb log}.
 * <p>
 * TOON = Token-Oriented Object Notation. Flat, whitespace-delimited format
 * optimized for LLM consumption (~50% fewer tokens than JSON).
 */
public class SessionLogger {
    private static final Logger log = Logger.getLogger("Cerberus");
    private static final int BUFFER_SIZE = 500;
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private final JavaPlugin plugin;
    private final Map<UUID, RingBuffer> playerBuffers = new ConcurrentHashMap<>();

    public SessionLogger(JavaPlugin plugin) {
        this.plugin = plugin;
        cleanOldLogs();
    }

    // ═══════════════════════════════════════════════════════════════
    // TYPED LOGGING METHODS
    // ═══════════════════════════════════════════════════════════════

    /**
     * Log a full combat damage pipeline event (3-layer system).
     * Called from PlayerVirtualHealthManager.applyDamage() after the pipeline completes.
     */
    public void logCombat(UUID uuid, CombatContext ctx, double healthBefore, double healthAfter,
                          double maxHealth, double defBarBefore, double defBarMax,
                          double effectiveArmor, double elemResist,
                          double absorptionRate, double absorbed) {
        Player player = Bukkit.getPlayer(uuid);
        StringBuilder sb = new StringBuilder();
        long t = System.currentTimeMillis();

        sb.append("@event combat.damage t:").append(t).append('\n');

        // Player info
        if (player != null) {
            Location loc = player.getLocation();
            sb.append("  player:").append(shortUuid(uuid))
              .append(" pos:").append(loc.getBlockX()).append(',')
              .append(loc.getBlockY()).append(',').append(loc.getBlockZ())
              .append(" world:").append(player.getWorld().getName()).append('\n');
        } else {
            sb.append("  player:").append(shortUuid(uuid)).append(" pos:unknown\n");
        }

        // Source
        Player attacker = ctx.getAttacker();
        if (attacker != null) {
            sb.append("  source:player id:").append(shortUuid(attacker.getUniqueId())).append('\n');
        } else if (ctx.getRawDamage() > 0) {
            sb.append("  source:").append(ctx.getDamageType().name().toLowerCase()).append('\n');
        }

        // Damage type + raw
        sb.append("  type:").append(ctx.getDamageType().name().toLowerCase()).append('\n');
        sb.append("  raw_dmg:").append(fmt(ctx.getRawDamage())).append('\n');

        // 3-layer pipeline trace
        sb.append("  elem_resist:").append(fmt(elemResist))
          .append(" after_elem:").append(fmt(ctx.defense().getAfterElementalResist())).append('\n');
        sb.append("  armor_pct:").append(fmt(effectiveArmor))
          .append(" after_armor:").append(fmt(ctx.defense().getAfterArmor())).append('\n');
        sb.append("  def_bar:").append(fmt(defBarBefore)).append('/').append(fmt(defBarMax))
          .append(" absorb_rate:").append(fmt(absorptionRate))
          .append(" absorbed:").append(fmt(absorbed))
          .append(" to_health:").append(fmt(ctx.defense().getAfterDefenseAbsorption())).append('\n');
        sb.append("  final:").append(fmt(ctx.defense().getFinalDamage())).append('\n');

        // Health
        sb.append("  hp_before:").append(fmt(healthBefore))
          .append(" hp_after:").append(fmt(healthAfter))
          .append(" hp_max:").append(fmt(maxHealth)).append('\n');

        // Attack state
        if (ctx.attack().isCritical()) {
            sb.append("  crit:yes mult:").append(fmt(ctx.attack().getCritMultiplier())).append('\n');
        }
        double lifesteal = ctx.postDamage().getLifestealAmount();
        if (lifesteal > 0) {
            sb.append("  lifesteal:").append(fmt(lifesteal)).append('\n');
        }

        // ── Anomaly flags ──
        double finalDmg = ctx.defense().getFinalDamage();
        if (finalDmg > 0.001 && Math.abs(healthBefore - healthAfter) < 0.001) {
            sb.append("  NOTE:health_unchanged_after_nonzero_damage final_dmg:")
              .append(fmt(finalDmg)).append('\n');
        }
        if (healthAfter > maxHealth + 0.01) {
            sb.append("  NOTE:health_exceeds_max hp:").append(fmt(healthAfter))
              .append(" max:").append(fmt(maxHealth)).append('\n');
        }
        if (finalDmg < -0.001) {
            sb.append("  NOTE:negative_damage final:").append(fmt(finalDmg)).append('\n');
        }

        append(uuid, sb.toString());
    }

    /**
     * Log any health mutation (regen, potion, respawn, manual set).
     */
    public void logHealth(UUID uuid, String source, double before, double after, double max) {
        StringBuilder sb = new StringBuilder();
        sb.append("@event health.change t:").append(System.currentTimeMillis()).append('\n');
        sb.append("  player:").append(shortUuid(uuid))
          .append(" source:").append(source).append('\n');
        sb.append("  hp_before:").append(fmt(before))
          .append(" hp_after:").append(fmt(after))
          .append(" hp_max:").append(fmt(max)).append('\n');

        if (after > max + 0.01) {
            sb.append("  NOTE:health_exceeds_max hp:").append(fmt(after))
              .append(" max:").append(fmt(max)).append('\n');
        }
        append(uuid, sb.toString());
    }

    /**
     * Log session lifecycle: join, quit, death, respawn with state snapshot.
     */
    public void logSession(UUID uuid, String eventType, Map<String, String> snapshot) {
        StringBuilder sb = new StringBuilder();
        sb.append("@event session.").append(eventType).append(" t:").append(System.currentTimeMillis()).append('\n');
        sb.append("  player:").append(shortUuid(uuid)).append('\n');
        for (Map.Entry<String, String> e : snapshot.entrySet()) {
            sb.append("  ").append(e.getKey()).append(':').append(e.getValue()).append('\n');
        }
        append(uuid, sb.toString());
    }

    /**
     * Log skill events: XP gain, level up, proc triggers.
     */
    public void logSkill(UUID uuid, String skillName, String event, Map<String, String> data) {
        StringBuilder sb = new StringBuilder();
        sb.append("@event skill.").append(event).append(" t:").append(System.currentTimeMillis()).append('\n');
        sb.append("  player:").append(shortUuid(uuid))
          .append(" skill:").append(skillName.replace(' ', '_')).append('\n');
        for (Map.Entry<String, String> e : data.entrySet()) {
            sb.append("  ").append(e.getKey()).append(':').append(e.getValue()).append('\n');
        }
        append(uuid, sb.toString());
    }

    /**
     * Log errors: null lookups, missing map entries, unexpected state.
     */
    public void logError(UUID uuid, String context, String detail) {
        StringBuilder sb = new StringBuilder();
        sb.append("@event error t:").append(System.currentTimeMillis()).append('\n');
        sb.append("  player:").append(shortUuid(uuid))
          .append(" ctx:").append(context).append('\n');
        sb.append("  detail:`").append(detail).append("`\n");
        sb.append("  NOTE:player_error\n");
        append(uuid, sb.toString());
    }

    /**
     * Log when a UUID lookup returns null unexpectedly.
     */
    public void logMissingPlayer(UUID uuid, String mapName) {
        StringBuilder sb = new StringBuilder();
        sb.append("@event error t:").append(System.currentTimeMillis()).append('\n');
        sb.append("  player:").append(shortUuid(uuid))
          .append(" ctx:missing_map_entry map:").append(mapName).append('\n');
        sb.append("  NOTE:player_missing_from_map\n");
        append(uuid, sb.toString());
    }

    // ═══════════════════════════════════════════════════════════════
    // RING BUFFER + FLUSH
    // ═══════════════════════════════════════════════════════════════

    private void append(UUID uuid, String entry) {
        playerBuffers.computeIfAbsent(uuid, k -> new RingBuffer(BUFFER_SIZE)).add(entry);
    }

    /**
     * Flush a player's buffer to disk and remove from map.
     * Called on player quit.
     */
    public void flushAndRemove(UUID uuid) {
        RingBuffer buf = playerBuffers.remove(uuid);
        if (buf == null || buf.isEmpty()) return;
        flushToDisk(uuid, buf, false);
    }

    /**
     * Dump current buffer to a live file for /cerb log command.
     * Does NOT remove the buffer — player is still online.
     *
     * @return the path to the written file, or null on failure
     */
    public String dumpLive(UUID uuid) {
        RingBuffer buf = playerBuffers.get(uuid);
        if (buf == null || buf.isEmpty()) return null;
        return flushToDisk(uuid, buf, true);
    }

    private String flushToDisk(UUID uuid, RingBuffer buf, boolean live) {
        String date = LocalDate.now().format(DATE_FMT);
        String subDir = live ? "live" : date;
        File dir = new File(plugin.getDataFolder(), "logs/sessions/" + subDir);
        if (!dir.exists()) dir.mkdirs();

        File file = new File(dir, uuid.toString() + ".toon");
        try {
            List<String> entries = buf.getAll();
            StringBuilder sb = new StringBuilder();
            sb.append("# Session log for ").append(uuid).append('\n');
            sb.append("# Date: ").append(date).append(" Entries: ").append(entries.size()).append('\n');
            sb.append("---\n");
            for (String entry : entries) {
                sb.append(entry);
                sb.append("---\n");
            }
            Files.writeString(file.toPath(), sb.toString());
            return file.getAbsolutePath();
        } catch (IOException e) {
            log.warning("[SessionLogger] Failed to flush log for " + uuid + ": " + e.getMessage());
            return null;
        }
    }

    /**
     * Delete session log files older than 24 hours on startup.
     */
    private void cleanOldLogs() {
        File sessionsDir = new File(plugin.getDataFolder(), "logs/sessions");
        if (!sessionsDir.exists()) return;

        LocalDate cutoff = LocalDate.now().minusDays(1);
        File[] dateDirs = sessionsDir.listFiles(File::isDirectory);
        if (dateDirs == null) return;

        for (File dir : dateDirs) {
            String name = dir.getName();
            if (name.equals("live")) {
                // Clean live dir entirely
                deleteDir(dir);
                continue;
            }
            try {
                LocalDate dirDate = LocalDate.parse(name, DATE_FMT);
                if (dirDate.isBefore(cutoff)) {
                    deleteDir(dir);
                    log.info("[SessionLogger] Cleaned old session logs: " + name);
                }
            } catch (Exception ignored) {
                // Not a date dir, skip
            }
        }
    }

    private void deleteDir(File dir) {
        File[] files = dir.listFiles();
        if (files != null) {
            for (File f : files) f.delete();
        }
        dir.delete();
    }

    // ═══════════════════════════════════════════════════════════════
    // UTILITY
    // ═══════════════════════════════════════════════════════════════

    private static String shortUuid(UUID uuid) {
        return uuid.toString().substring(0, 8);
    }

    private static String fmt(double v) {
        if (v == 0) return "0";
        return String.format("%.2f", v);
    }

    /**
     * Simple ring buffer backed by an array.
     */
    static class RingBuffer {
        private final String[] data;
        private int head = 0;
        private int count = 0;

        RingBuffer(int capacity) {
            this.data = new String[capacity];
        }

        synchronized void add(String entry) {
            data[head] = entry;
            head = (head + 1) % data.length;
            if (count < data.length) count++;
        }

        synchronized boolean isEmpty() {
            return count == 0;
        }

        synchronized List<String> getAll() {
            List<String> result = new ArrayList<>(count);
            int start = (head - count + data.length) % data.length;
            for (int i = 0; i < count; i++) {
                result.add(data[(start + i) % data.length]);
            }
            return result;
        }
    }
}
