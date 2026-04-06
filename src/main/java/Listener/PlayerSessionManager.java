package Listener;

import Manager.*;
import Skills.SkillManager;
import cerberus.world.cerb.CerberusGameMode;
import cerberus.world.cerb.CerberusPlugin;
import cerberus.world.cerb.CustomPlayer;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.inventory.ItemStack;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Logger;

/**
 * Centralized player lifecycle orchestrator.
 * Replaces scattered join/quit/death/respawn handlers across 7+ listeners.
 *
 * <pre>
 * Session states: JOINING → LOADING_DATA → ACTIVE → DEAD → RESPAWNING → ACTIVE
 *                                                ↓
 *                                           CLEANING_UP → CLOSED
 * </pre>
 */
public class PlayerSessionManager implements Listener {

    private static final Logger log = Logger.getLogger("Cerberus");

    private final CerberusPlugin plugin;
    private final PlayerServices svc;
    private final CerberusGameMode gameMode;

    /** The ONE map — session per player. */
    private final Map<UUID, PlayerSession> sessions = new ConcurrentHashMap<>();

    /** Monotonic generation counter for stale-callback detection. */
    private final AtomicLong generationCounter = new AtomicLong(0);

    /** Plugin-owned DB executor — NOT the common pool. */
    private final ExecutorService dbExecutor;

    // Speed limit constant (matches old SpeedLimiterListener)
    private static final double MAX_MOVEMENT_SPEED = 0.2 * (1 + 0.5);

    /** Join phases for logging. */
    enum JoinPhase { SPEED_LIMIT, GAME_MODE, REGISTER_PLAYER, INVENTORY, LOAD_DATA, SYSTEMS_START, HUD_UPDATE }

    /** Async DB load result carrier. */
    private record SkillSnapshot(String customId, Map<String,Integer> levels, Map<String,Integer> xp) {}

    public PlayerSessionManager(CerberusPlugin plugin, PlayerServices services) {
        this.plugin = plugin;
        this.svc = services;
        this.gameMode = new CerberusGameMode(services.virtualHealth(), plugin);
        this.dbExecutor = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "Cerb-DB-Session");
            t.setDaemon(true);
            return t;
        });
    }

    // ──────────────────────────────────────────────────────────────
    // JOIN
    // ──────────────────────────────────────────────────────────────

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();
        long gen = generationCounter.incrementAndGet();

        // Create session (replaces any stale session from fast reconnect)
        PlayerSession session = new PlayerSession(uuid, gen);
        sessions.put(uuid, session);

        // ── Phase 1: Immediate (no DB dependency) ──

        // SPEED_LIMIT
        try {
            enforceSpeedLimit(player);
        } catch (Exception e) {
            log.warning("[PlayerSession] Phase SPEED_LIMIT failed for " + player.getName() + ": " + e.getMessage());
        }

        // GAME_MODE
        try {
            gameMode.enable(player, plugin.getPlayerDataConfig());
        } catch (Exception e) {
            log.warning("[PlayerSession] Phase GAME_MODE failed for " + player.getName() + ": " + e.getMessage());
        }

        // REGISTER_PLAYER
        try {
            new CustomPlayer(player, svc.skills(), svc.virtualHealth(), svc.defense(), svc.strength());
        } catch (Exception e) {
            log.warning("[PlayerSession] Phase REGISTER_PLAYER failed for " + player.getName() + ": " + e.getMessage());
        }

        // INVENTORY — menu head in slot 8, admin chest if admin
        try {
            ItemStack menuHead = svc.playerMenuGUI().getPlayerMenuItem(player);
            player.getInventory().setItem(8, menuHead);
            giveAdminChestIfNeeded(player);
        } catch (Exception e) {
            log.warning("[PlayerSession] Phase INVENTORY failed for " + player.getName() + ": " + e.getMessage());
        }

        // ── Phase 2: Async DB load ──
        session.setState(PlayerSession.State.LOADING_DATA);

        CompletableFuture.supplyAsync(() -> {
            String customId = svc.database().getOrCreateCustomId(uuid, player.getName());
            Map<String,Integer> levels = svc.database().loadPlayerSkillLevels(uuid);
            Map<String,Integer> xp = svc.database().loadPlayerSkillXP(uuid);
            return new SkillSnapshot(customId, levels, xp);
        }, dbExecutor).handle((data, ex) -> {
            // Callback to player's region thread (not global) — modifies player state
            player.getScheduler().run(plugin, task -> {
                // Session-safe: check generation matches
                PlayerSession current = sessions.get(uuid);
                if (current == null || current.getGeneration() != gen) {
                    log.fine("[PlayerSession] Stale callback discarded for " + player.getName() + " (gen " + gen + ")");
                    return; // stale
                }

                if (ex != null) {
                    log.severe("[PlayerSession] DB load failed for " + player.getName() + ": " + ex.getMessage());
                    session.setState(PlayerSession.State.ACTIVE);
                    completeJoinSetup(player, session);
                    return;
                }

                session.setCustomId(data.customId());
                svc.skills().setPlayerSkills(player, data.levels(), data.xp());
                session.setState(PlayerSession.State.ACTIVE);
                completeJoinSetup(player, session);

                log.info("[PlayerSession] Data loaded for " + player.getName() + ", session gen=" + gen);
            }, null);
            return null;
        });
    }

    /** Phase 2 completion: start stat-dependent systems (called on main thread). */
    private void completeJoinSetup(Player player, PlayerSession session) {
        // RESET HEALTH + MANA — ensure player always spawns alive
        try {
            svc.virtualHealth().resetHealth(player);
            svc.mana().resetMana(svc.mana().getMaxMana(player));
        } catch (Exception e) {
            log.warning("[PlayerSession] Phase SYSTEMS_START(resetHealth) failed for " + player.getName() + ": " + e.getMessage());
        }

        // SYSTEMS_START
        try {
            svc.defense().updateDefense(player);
        } catch (Exception e) {
            log.warning("[PlayerSession] Phase SYSTEMS_START(defense) failed for " + player.getName() + ": " + e.getMessage());
        }

        try {
            svc.virtualHealth().startHealthUpdateTask(player, svc.hud());
        } catch (Exception e) {
            log.warning("[PlayerSession] Phase SYSTEMS_START(healthTask) failed for " + player.getName() + ": " + e.getMessage());
        }

        try {
            svc.mana().startManaRegenTask(player);
        } catch (Exception e) {
            log.warning("[PlayerSession] Phase SYSTEMS_START(manaRegen) failed for " + player.getName() + ": " + e.getMessage());
        }

        try {
            svc.virtualHealth().startHealthRegenTask(player);
        } catch (Exception e) {
            log.warning("[PlayerSession] Phase SYSTEMS_START(healthRegen) failed for " + player.getName() + ": " + e.getMessage());
        }

        // HUD_UPDATE (last)
        try {
            svc.hud().updateHUD(player);
        } catch (Exception e) {
            log.warning("[PlayerSession] Phase HUD_UPDATE failed for " + player.getName() + ": " + e.getMessage());
        }
    }

    // ──────────────────────────────────────────────────────────────
    // QUIT
    // ──────────────────────────────────────────────────────────────

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onPlayerQuit(PlayerQuitEvent event) {
        cleanupPlayer(event.getPlayer());
    }

    /**
     * Public cleanup — called from quit AND onDisable.
     * Session-based guard: if session is null or CLOSED, skip (idempotent).
     */
    public void cleanupPlayer(Player player) {
        UUID uuid = player.getUniqueId();
        PlayerSession session = sessions.remove(uuid);
        if (session == null || session.isClosed()) return;
        session.setState(PlayerSession.State.CLEANING_UP);

        // 1. Remove effects FIRST — side effects captured in skill save
        try {
            svc.effects().removeAllEffects(player);
        } catch (Exception e) {
            log.warning("[PlayerSession] Cleanup(effects) failed for " + player.getName() + ": " + e.getMessage());
        }

        // 2. Snapshot skills, then async save
        Map<String,Integer> levels = svc.skills().getPlayerSkillLevels(player);
        Map<String,Integer> xp = svc.skills().getPlayerSkillXP(player);
        String customId = session.getCustomId();
        if (customId != null && levels != null && xp != null) {
            CompletableFuture.runAsync(() -> {
                try {
                    svc.database().saveSkillsByCustomId(customId, levels, xp);
                } catch (Exception e) {
                    log.severe("[PlayerSession] Async save failed for " + player.getName() + ": " + e.getMessage());
                }
            }, dbExecutor);
        }

        // 3. Clean up all managers (idempotent, safe if absent)
        try { svc.mana().removePlayer(uuid); } catch (Exception e) {
            log.warning("[PlayerSession] Cleanup(mana) failed: " + e.getMessage());
        }
        try { svc.virtualHealth().removePlayer(uuid); } catch (Exception e) {
            log.warning("[PlayerSession] Cleanup(virtualHealth) failed: " + e.getMessage());
        }
        try { svc.defenseBar().removePlayer(player); } catch (Exception e) {
            log.warning("[PlayerSession] Cleanup(defenseBar) failed: " + e.getMessage());
        }
        try { svc.defense().removePlayer(uuid); } catch (Exception e) {
            log.warning("[PlayerSession] Cleanup(defense) failed: " + e.getMessage());
        }
        try { svc.skills().removePlayer(uuid); } catch (Exception e) {
            log.warning("[PlayerSession] Cleanup(skills) failed: " + e.getMessage());
        }

        // 3b. Flush session log to disk
        try {
            if (plugin.getSessionLogger() != null) {
                plugin.getSessionLogger().flushAndRemove(uuid);
            }
        } catch (Exception e) {
            log.warning("[PlayerSession] Cleanup(sessionLogger) failed: " + e.getMessage());
        }

        // 4. Unregister CustomPlayer
        CustomPlayer.unregister(uuid);

        session.setState(PlayerSession.State.CLOSED);
        log.info("[PlayerSession] Cleaned up session for " + player.getName());
    }

    // ──────────────────────────────────────────────────────────────
    // DEATH
    // ──────────────────────────────────────────────────────────────

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();
        PlayerSession session = sessions.get(player.getUniqueId());
        if (session == null || !session.isActive()) return;

        session.setState(PlayerSession.State.DEAD);
        event.setKeepInventory(true);
        event.setKeepLevel(true);
        event.getDrops().clear();
        event.setDroppedExp(0);

        // Resolve killer mob via MobRegistry
        MobContext killerMob = null;
        Entity damager = null;
        EntityDamageEvent cause = player.getLastDamageCause();
        if (cause instanceof EntityDamageByEntityEvent edb) {
            damager = edb.getDamager();
            if (damager instanceof LivingEntity le) {
                killerMob = svc.mobRegistry().resolve(le);
            }
        }

        // Store death context for respawn handler
        session.setDeathContext(new DeathContext(
                svc.mana().getCurrentMana(player),
                svc.virtualHealth().getPlayerVirtualHealth(player),
                killerMob,
                System.currentTimeMillis()
        ));

        // Reset mana (virtual health is already 0 — checkPlayerDeath triggered this event)
        svc.mana().resetMana(0);
        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_PLAYER_DEATH, 1.0F, 1.0F);

        // Force respawn next tick
        player.getScheduler().run(plugin, task -> player.spigot().respawn(), null);
    }

    // ──────────────────────────────────────────────────────────────
    // RESPAWN
    // ──────────────────────────────────────────────────────────────

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = false)
    public void onPlayerRespawn(PlayerRespawnEvent event) {
        Player player = event.getPlayer();
        PlayerSession session = sessions.get(player.getUniqueId());
        if (session == null) return;

        session.setState(PlayerSession.State.RESPAWNING);
        DeathContext ctx = session.consumeDeathContext();

        // IMMEDIATELY set virtual health to max + apply invincibility.
        // These are just map writes — no Bukkit entity calls, safe during event.
        // This prevents the 0-health window where mobs can re-kill the player.
        double maxVH = svc.virtualHealth().getPlayerMaxVirtualHealth(player);
        svc.virtualHealth().setPlayerVirtualHealth(player, maxVH);
        svc.virtualHealth().applyRespawnInvincibility(player);

        session.setState(PlayerSession.State.ACTIVE);

        // Delayed setup for Bukkit entity calls (health bar, attributes, tasks)
        // that need the player entity fully re-initialized by the server.
        player.getScheduler().runDelayed(plugin, task -> {
            if (!player.isOnline()) return;

            // Set vanilla health to max (the Bukkit call that needs delay)
            try {
                double maxReal = player.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH).getValue();
                player.setHealth(maxReal);
            } catch (Exception e) {
                // Safety — player may have disconnected
            }

            svc.mana().resetMana(svc.mana().getMaxMana(player));
            svc.mana().startManaRegenTask(player);
            svc.virtualHealth().startHealthRegenTask(player);
            svc.virtualHealth().startHealthUpdateTask(player, svc.hud());
            svc.defense().updateDefense(player);
            svc.hud().updateHUD(player);
        }, null, 2L);
    }

    private void scheduleRegenAfterRespawnTick(Player player, PlayerSession session) {
        long gen = session.getGeneration();
        player.getScheduler().run(plugin, task -> {
            PlayerSession current = sessions.get(player.getUniqueId());
            if (current == null || current.getGeneration() != gen) return; // stale
            svc.virtualHealth().startHealthUpdateTask(player, svc.hud());
        }, null);
    }

    // ──────────────────────────────────────────────────────────────
    // SHUTDOWN — called from CerberusPlugin.onDisable()
    // ──────────────────────────────────────────────────────────────

    public void shutdown() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            cleanupPlayer(player);
        }
        dbExecutor.shutdown();
        try {
            if (!dbExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                log.warning("[PlayerSession] DB executor did not drain in 5s — forcing shutdown");
                dbExecutor.shutdownNow();
            }
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        }
    }

    // ──────────────────────────────────────────────────────────────
    // HELPERS
    // ──────────────────────────────────────────────────────────────

    /** Enforce speed limit on join — also clears all stacked modifiers from old bugs. */
    private void enforceSpeedLimit(Player player) {
        var speedAttr = player.getAttribute(Attribute.MOVEMENT_SPEED);
        if (speedAttr == null) return;

        // Wipe ALL modifiers on join — skills will re-apply their own cleanly
        var allMods = new java.util.ArrayList<>(speedAttr.getModifiers());
        for (var mod : allMods) {
            speedAttr.removeModifier(mod);
        }
        speedAttr.setBaseValue(0.1);
    }

    /** Give admin chest if player has permission (moved from CerberusPlugin). */
    private void giveAdminChestIfNeeded(Player player) {
        if (!player.hasPermission("cerberus.admin")) return;

        boolean hasAdminChest = false;
        int adminChestCount = 0;
        for (ItemStack item : player.getInventory().getContents()) {
            if (item != null && svc.adminChestGUI().isAdminChest(item)) {
                hasAdminChest = true;
                adminChestCount++;
            }
        }

        if (!hasAdminChest) {
            player.getInventory().addItem(svc.adminChestGUI().createAdminChest());
            player.sendMessage(ChatColor.GREEN + "You have received the Admin Control Panel chest!");
        } else if (adminChestCount > 1) {
            removeExcessAdminChests(player, adminChestCount);
        }
    }

    private void removeExcessAdminChests(Player player, int adminChestCount) {
        int removedCount = 0;
        for (int i = 0; i < player.getInventory().getSize(); i++) {
            ItemStack item = player.getInventory().getItem(i);
            if (item != null && svc.adminChestGUI().isAdminChest(item)) {
                if (removedCount < adminChestCount - 1) {
                    player.getInventory().setItem(i, null);
                    removedCount++;
                } else {
                    break;
                }
            }
        }
        if (removedCount > 0) {
            player.sendMessage(ChatColor.YELLOW + "Excess Admin Control Panel chests have been removed.");
        }
    }

    /** Get session for a player (used by other systems if needed). */
    public PlayerSession getSession(UUID uuid) {
        return sessions.get(uuid);
    }
}
