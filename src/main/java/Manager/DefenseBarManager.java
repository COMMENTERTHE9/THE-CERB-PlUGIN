package Manager;

import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Defense Bar — Layer 3 of the 3-layer defense system.
 * <p>
 * Partial absorption: the bar absorbs a percentage of incoming damage
 * that scales linearly with how full the bar is.
 * <pre>
 *   absorptionRate = maxAbsorption * (current / max)
 *   At 100% bar: absorbs 70% of post-armor damage
 *   At  50% bar: absorbs 35%
 *   At   0% bar: absorbs  0% (player fully exposed)
 * </pre>
 * Bar drains by the absorbed amount each hit and regenerates out of combat.
 */
public class DefenseBarManager {
    private final JavaPlugin plugin;
    private final Map<UUID, DefenseBar> playerDefenseBars = new ConcurrentHashMap<>();

    // Config values — set from CombatConfig
    private double baseMaxPoints = 300.0;
    private double maxAbsorption = 0.70;
    private double regenRate = 15.0;       // points per second
    private long regenDelayMs = 7000;      // ms after last hit before regen starts

    public DefenseBarManager(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    /** Inject config values. Call after CombatConfig is loaded and on reload. */
    public void applyCombatConfig(CombatConfig config) {
        this.baseMaxPoints = config.defBarBaseMaxPoints;
        this.maxAbsorption = config.defBarMaxAbsorption;
        this.regenRate = config.defBarRegenRate;
        this.regenDelayMs = config.defBarRegenDelayMs;
    }

    /** Get or create a defense bar for a player. */
    public DefenseBar getDefenseBar(Player player) {
        return playerDefenseBars.computeIfAbsent(player.getUniqueId(),
                uuid -> new DefenseBar(player, baseMaxPoints));
    }

    /** Regeneration scale per player (the realm magnitude): a bigger realm's bar refills at the same pace. */
    private volatile java.util.function.ToDoubleFunction<UUID> regenScale = id -> 1.0;

    public void setRegenScale(java.util.function.ToDoubleFunction<UUID> scale) {
        this.regenScale = scale == null ? id -> 1.0 : scale;
    }

    double regenScale(UUID id) {
        double s = regenScale.applyAsDouble(id);
        return Double.isFinite(s) && s > 0 ? s : 1.0;
    }

    /** Bar size before any skill or attribute bonus (combat.yml defense-bar.base-max-points). */
    public double getBaseMaxPoints() { return baseMaxPoints; }

    /** Read-only: the bar if this player has one, else null. Never creates one. */
    public DefenseBar peekDefenseBar(UUID playerId) {
        return playerDefenseBars.get(playerId);
    }

    /** {@code combat.yml defense-bar.max-absorption}: the share a full bar takes of a hit. */
    public double getMaxAbsorption() { return maxAbsorption; }

    /**
     * The bar took part of a hit (worked out by {@link DefenseMath#resolve}): it loses
     * {@code points} (never below zero), remembers the share it took, and refills later.
     */
    public void drain(Player player, double points, double rate) {
        DefenseBar bar = getDefenseBar(player);
        if (Double.isFinite(points) && points > 0) bar.current = Math.max(0, bar.current - points);
        bar.lastDamageTime = System.currentTimeMillis();
        bar.lastAbsorptionRate = rate;
        bar.scheduleRegeneration();
    }

    /** Set additional max points (e.g. from Heavy Armor Training skill). */
    public void setBonusMaxPoints(Player player, double bonus) {
        DefenseBar bar = getDefenseBar(player);
        double oldMax = bar.maximum;
        double newMax = baseMaxPoints + bonus;
        if (!Double.isFinite(newMax) || newMax <= 0 || newMax == oldMax) return;
        // Keep the same fill fraction when the bar grows or shrinks (a realm step can change
        // its size by x8 or more; a full bar should stay full, a half bar half).
        bar.current = oldMax > 0 ? Math.min(newMax, bar.current / oldMax * newMax) : newMax;
        bar.maximum = newMax;
    }

    /** Fully restore a player's defense bar and clear any regen timing. */
    public void resetDefenseBar(Player player) {
        DefenseBar bar = getDefenseBar(player);
        bar.cancelRegeneration();
        bar.current = bar.maximum;
        bar.lastDamageTime = 0;
        bar.lastAbsorptionRate = 0;
    }

    /** Clean up when player leaves. */
    public void removePlayer(Player player) {
        DefenseBar bar = playerDefenseBars.remove(player.getUniqueId());
        if (bar != null) {
            bar.cancelRegeneration();
        }
    }

    public void removePlayer(UUID uuid) {
        DefenseBar bar = playerDefenseBars.remove(uuid);
        if (bar != null) {
            bar.cancelRegeneration();
        }
    }

    // ── Inner class ──────────────────────────────────────────────

    public class DefenseBar {
        private final Player player;
        double current;
        double maximum;
        long lastDamageTime = 0;
        double lastAbsorptionRate = 0;
        private ScheduledTask regenTask;

        DefenseBar(Player player, double maxPoints) {
            this.player = player;
            this.maximum = maxPoints;
            this.current = maxPoints;
        }

        public double getCurrent() { return current; }
        public double getMaximum() { return maximum; }
        public double getPercentage() { return maximum > 0 ? current / maximum : 0; }
        public double getLastAbsorptionRate() { return lastAbsorptionRate; }

        void scheduleRegeneration() {
            cancelRegeneration();

            // Schedule regen to start after 1 tick, check delay each tick
            // (simpler than using delayTicks — avoids off-by-one timing issues)
            regenTask = player.getScheduler().runAtFixedRate(plugin, scheduledTask -> {
                if (!player.isOnline()) {
                    scheduledTask.cancel();
                    return;
                }

                // Wait for combat timeout before regenerating
                if (System.currentTimeMillis() - lastDamageTime < regenDelayMs) {
                    return;
                }

                // Regenerate: regenRate points per second, this runs every tick
                double before = current;
                current = Math.min(maximum, current + regenRate * regenScale(player.getUniqueId()) / 20.0);

                if (current >= maximum) {
                    scheduledTask.cancel();
                    regenTask = null;
                }
            }, null, 1L, 1L);
        }

        void cancelRegeneration() {
            if (regenTask != null) {
                regenTask.cancel();
                regenTask = null;
            }
        }
    }
}
