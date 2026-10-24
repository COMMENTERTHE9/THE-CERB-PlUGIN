package Listener;

import Manager.CerbDamageResultEvent;
import Manager.CombatConfig;
import Manager.CombatContext;
import Manager.CustomDamageType;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.entity.Display.Billboard;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.Bukkit;
import org.bukkit.Particle;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.event.EventPriority;

import java.util.Iterator;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;
import org.bukkit.event.entity.EntityDeathEvent;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;

import javax.annotation.Nullable;

/**
 * Displays floating damage numbers, combo counters, sounds, and particles.
 * Now driven by {@link CerbDamageResultEvent} — uses CombatContext for
 * accurate damage type, critical status, and final damage values.
 */
public class DamageDisplayListener implements Listener {
    private final JavaPlugin plugin;
    private final Random random = new Random();
    private final Map<UUID, CombatData> combatData = new ConcurrentHashMap<>();
    private static final Logger log = Logger.getLogger("Cerberus");
    private static final long STALE_ENTRY_MS = 30_000; // 30 seconds
    private ScheduledTask cleanupTask;
    private @Nullable CombatConfig combatConfig;

    private static class CombatData {
        double totalDamage;
        int hitCount;
        long firstHitTime;
        long lastHitTime;
        double dps;
    }

    public DamageDisplayListener(JavaPlugin plugin) {
        this.plugin = plugin;
        startCleanupTask();
    }

    /** Optional: inject CombatConfig for display tuning values. */
    public void setCombatConfig(CombatConfig config) {
        this.combatConfig = config;
    }

    // ── Cleanup infrastructure (Phase 1C) ────────────────────────

    private void startCleanupTask() {
        cleanupTask = Bukkit.getGlobalRegionScheduler().runAtFixedRate(plugin, task -> {
            long now = System.currentTimeMillis();
            int removed = 0;
            Iterator<Map.Entry<UUID, CombatData>> it = combatData.entrySet().iterator();
            while (it.hasNext()) {
                Map.Entry<UUID, CombatData> entry = it.next();
                if (now - entry.getValue().lastHitTime > STALE_ENTRY_MS) {
                    it.remove();
                    removed++;
                }
            }
            if (removed > 0) {
                log.fine("[DamageDisplay] Cleaned up " + removed + " stale combat data entries.");
            }
        }, 600L, 600L);
    }

    @EventHandler
    public void onEntityDeath(EntityDeathEvent event) {
        combatData.remove(event.getEntity().getUniqueId());
    }

    public void shutdown() {
        if (cleanupTask != null) {
            cleanupTask.cancel();
            cleanupTask = null;
        }
        combatData.clear();
    }

    // ── Main display handler — driven by CerbDamageResultEvent ───

    @EventHandler(priority = EventPriority.MONITOR)
    public void onDamageResult(CerbDamageResultEvent event) {
        CombatContext ctx = event.getContext();
        LivingEntity target = ctx.getTarget();

        // Read from CombatContext — always correct
        CustomDamageType damageType = ctx.getDamageType();
        boolean isCritical = ctx.attack().isCritical();
        double finalDamage = ctx.defense().getFinalDamage();

        // Update combo stats (using attacker UUID if available)
        if (ctx.getAttacker() != null) {
            UUID attackerId = ctx.getAttacker().getUniqueId();
            updateCombatStats(attackerId, finalDamage);
        }

        // Spawn location near target
        Location spawnLoc = getRandomizedLocation(target);

        // Show damage number — "BLOCKED" for fully absorbed, normal otherwise
        if (event.wasFullyAbsorbed()) {
            showBlockedIndicator(spawnLoc);
        } else {
            showDamageNumber(spawnLoc, finalDamage, damageType, isCritical);
        }

        // Combo counter
        if (ctx.getAttacker() != null) {
            showComboIfRelevant(ctx.getAttacker().getUniqueId(),
                    spawnLoc.clone().add(0, 0.5, 0));
        }

        // Particles only — sound is now gated in PlayerVirtualHealthManager.applyDamage()
        if (finalDamage > 0) {
            spawnDamageEffects(target.getLocation(), damageType, finalDamage);
        }
    }

    // ── Display rendering ────────────────────────────────────────

    private void showDamageNumber(Location location, double damage, CustomDamageType type, boolean isCritical) {
        TextDisplay display = location.getWorld().spawn(location, TextDisplay.class, textDisplay -> {
            Component text = formatDamageText(damage, type, isCritical);
            textDisplay.text(text);
            textDisplay.setBillboard(Billboard.CENTER);
            textDisplay.setDefaultBackground(false);
            textDisplay.setShadowed(true);
            textDisplay.setViewRange(16.0f);
            textDisplay.setPersistent(false);
        });

        int displayTicks = isCritical
                ? (combatConfig != null ? combatConfig.displayCritDamageNumberTicks : 30)
                : (combatConfig != null ? combatConfig.displayDamageNumberTicks : 20);
        animateDisplay(display, location.clone(), isCritical, displayTicks);
    }

    /** Show a grey "BLOCKED" indicator for fully absorbed hits. */
    private void showBlockedIndicator(Location location) {
        TextDisplay display = location.getWorld().spawn(location, TextDisplay.class, textDisplay -> {
            textDisplay.text(Component.text("BLOCKED", NamedTextColor.GRAY)
                    .decoration(TextDecoration.ITALIC, true));
            textDisplay.setBillboard(Billboard.CENTER);
            textDisplay.setDefaultBackground(false);
            textDisplay.setShadowed(true);
            textDisplay.setViewRange(16.0f);
            textDisplay.setPersistent(false);
        });
        animateDisplay(display, location.clone(), false, 15);
    }

    private Component formatDamageText(double damage, CustomDamageType type, boolean isCritical) {
        // Realm-scaled hits reach millions: show "12.3M" rather than a 10-digit number.
        String numberText = damage >= 10_000 ? GUIs.menu.Fmt.compact(damage) : String.format("%.1f", damage);
        if (isCritical) {
            numberText = "✯ " + numberText + " ✯";
        }
        return Component.text(type.getSymbol() + numberText)
                .color(type.getColor())
                .decoration(TextDecoration.BOLD, isCritical);
    }

    private void animateDisplay(TextDisplay display, Location startLoc, boolean isCritical, int displayTime) {
        final int[] ticks = {0};
        final double[] horizontalOffset = {0};
        Bukkit.getRegionScheduler().runAtFixedRate(plugin, startLoc, task -> {
            if (ticks[0] >= displayTime || !display.isValid()) {
                display.remove();
                task.cancel();
                return;
            }

            double progress = (double) ticks[0] / displayTime;
            double height = Math.sin(progress * Math.PI) * (isCritical ? 0.8 : 0.5);

            if (isCritical) {
                horizontalOffset[0] = Math.sin(progress * Math.PI * 4) * 0.1;
            }

            Location newLoc = startLoc.clone().add(horizontalOffset[0], height, 0);
            display.teleportAsync(newLoc);

            if (ticks[0] > displayTime * 0.7) {
                display.setVisibleByDefault(ticks[0] % 2 == 0);
            }

            ticks[0]++;
        }, 1L, 1L);
    }

    private Location getRandomizedLocation(LivingEntity entity) {
        return entity.getLocation().add(
                (random.nextDouble() - 0.5) * 0.5,
                entity.getHeight() + 0.5,
                (random.nextDouble() - 0.5) * 0.5
        );
    }

    // ── Combat stats / combo ─────────────────────────────────────

    private void updateCombatStats(UUID attackerId, double damage) {
        CombatData data = combatData.computeIfAbsent(attackerId, k -> new CombatData());
        long currentTime = System.currentTimeMillis();

        long comboTimeout = combatConfig != null ? combatConfig.displayComboTimeoutMs : 5000;
        if (currentTime - data.lastHitTime > comboTimeout) {
            data.totalDamage = 0;
            data.hitCount = 0;
            data.dps = 0;
            data.firstHitTime = currentTime;
        }

        data.totalDamage += damage;
        data.hitCount++;
        data.lastHitTime = currentTime;
        double elapsedSeconds = (currentTime - data.firstHitTime) / 1000.0;
        data.dps = (elapsedSeconds > 0) ? data.totalDamage / elapsedSeconds : damage;
    }

    private void showComboIfRelevant(UUID attackerId, Location location) {
        CombatData data = combatData.get(attackerId);
        int threshold = combatConfig != null ? combatConfig.displayComboThreshold : 2;
        if (data != null && data.hitCount > threshold) {
            String comboText = String.format("%d Hit Combo! (%.1f DPS)",
                    data.hitCount, data.dps);

            TextDisplay comboDisplay = location.getWorld().spawn(location, TextDisplay.class, display -> {
                display.text(Component.text(comboText, NamedTextColor.GOLD));
                display.setBillboard(Billboard.CENTER);
                display.setDefaultBackground(false);
                // Not saved with the chunk: if the chunk unloads or the server stops before the
                // removal task runs, the text must not stay floating in the world forever.
                display.setPersistent(false);
            });

            Bukkit.getRegionScheduler().runDelayed(plugin, location, task -> {
                comboDisplay.remove();
            }, 20L);
        }
    }

    // ── Sound & particles ────────────────────────────────────────

    private void playDamageSound(Location location, CustomDamageType type, boolean isCritical) {
        Sound sound = switch (type) {
            case MANA_DRAIN, MANA_BURN -> Sound.BLOCK_BEACON_DEACTIVATE;
            case FIRE, BURN_DOT -> Sound.ENTITY_GENERIC_BURN;
            case FREEZE, FREEZE_DOT -> Sound.BLOCK_GLASS_BREAK;
            default -> isCritical ? Sound.ENTITY_PLAYER_ATTACK_CRIT : Sound.ENTITY_PLAYER_ATTACK_STRONG;
        };

        location.getWorld().playSound(location, sound, 1.0f, isCritical ? 1.2f : 1.0f);
    }

    private void spawnDamageEffects(Location location, CustomDamageType type, double damage) {
        Particle particle = switch (type) {
            case MANA_DRAIN, MANA_BURN -> Particle.DRAGON_BREATH;
            case FIRE, BURN_DOT -> Particle.FLAME;
            case FREEZE, FREEZE_DOT -> Particle.SNOWFLAKE;
            case MAGIC, CUSTOM_MAGIC -> Particle.WITCH;
            default -> Particle.CRIT;
        };

        location.getWorld().spawnParticle(particle, location.add(0, 1, 0),
                particleCount(damage), 0.3, 0.3, 0.3, 0.1);
    }

    /** Upper bound on hit particles. Clients spawn every particle in a packet one by one. */
    static final int MAX_HIT_PARTICLES = 40;

    /**
     * Particle count for a hit: two per point of damage, at least 1, at most
     * {@link #MAX_HIT_PARTICLES}. Uncapped, /kill (~3.4e38 damage) produced a packet asking
     * for Integer.MAX_VALUE particles, which would hang the client of every nearby player;
     * at cultivation-scale damage every big hit did the same.
     */
    static int particleCount(double damage) {
        if (!(damage > 0)) return 1; // also catches NaN
        return (int) Math.max(1, Math.min(MAX_HIT_PARTICLES, damage * 2));
    }
}
