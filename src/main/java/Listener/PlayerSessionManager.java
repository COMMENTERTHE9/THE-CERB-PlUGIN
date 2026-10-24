package Listener;

import GUIs.AdminChestGUI;
import Manager.*;
import com.destroystokyo.paper.event.player.PlayerPostRespawnEvent;
import cerberus.world.cerb.CerberusGameMode;
import cerberus.world.cerb.CerberusPlugin;
import cerberus.world.cerb.CustomPlayer;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.GameRule;
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
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Logger;

/**
 * Centralized player lifecycle orchestrator.
 */
public class PlayerSessionManager implements Listener {
    private static final Logger log = Logger.getLogger("Cerberus");

    private final CerberusPlugin plugin;
    private final PlayerServices svc;
    private final CerberusGameMode gameMode;
    private final Map<UUID, PlayerSession> sessions = new ConcurrentHashMap<>();
    private final AtomicLong generationCounter = new AtomicLong(0);
    private final ExecutorService dbExecutor;

    private record SkillSnapshot(String customId, Map<String, Integer> levels, Map<String, Integer> xp,
                                     java.util.Set<Manager.ChunkKey> visitedChunks) {}

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

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();
        long generation = generationCounter.incrementAndGet();

        PlayerSession session = new PlayerSession(uuid, generation);
        sessions.put(uuid, session);

        try {
            enforceSpeedLimit(player);
        } catch (Exception e) {
            log.warning("[PlayerSession] Phase SPEED_LIMIT failed for " + player.getName() + ": " + e.getMessage());
        }

        try {
            gameMode.enable(player, plugin.getPlayerDataConfig());
        } catch (Exception e) {
            log.warning("[PlayerSession] Phase GAME_MODE failed for " + player.getName() + ": " + e.getMessage());
        }

        try {
            new CustomPlayer(player, svc.skills(), svc.virtualHealth(), svc.defense(), svc.strength());
        } catch (Exception e) {
            log.warning("[PlayerSession] Phase REGISTER_PLAYER failed for " + player.getName() + ": " + e.getMessage());
        }

        try {
            svc.playerMenuGUI().placeMenuHead(player); // never destroys what was in slot 9
            giveAdminChestIfNeeded(player);
        } catch (Exception e) {
            log.warning("[PlayerSession] Phase INVENTORY failed for " + player.getName() + ": " + e.getMessage());
        }

        session.setState(PlayerSession.State.LOADING_DATA);

        CompletableFuture.supplyAsync(() -> {
            svc.database().getOrCreateCustomId(uuid, player.getName());
            cerberus.world.cerb.DatabaseManager.StrictSkills loaded;
            try {
                loaded = svc.database().loadPlayerSkillsStrict(uuid);
            } catch (java.sql.SQLException e) {
                throw new java.util.concurrent.CompletionException(e);
            }
            java.util.Set<Manager.ChunkKey> chunks = svc.database().loadVisitedChunks(uuid);
            return new SkillSnapshot(loaded.customId(), loaded.levels(), loaded.xp(), chunks);
        }, dbExecutor).handle((data, ex) -> {
            player.getScheduler().run(plugin, task -> {
                PlayerSession current = sessions.get(uuid);
                if (current == null || current.getGeneration() != generation) {
                    log.fine("[PlayerSession] Stale callback discarded for " + player.getName() + " (gen " + generation + ")");
                    return;
                }

                if (ex != null) {
                    // Never play on empty defaults after a failed load: the first XP save would
                    // overwrite the player's real stored levels. Disconnect instead; the stored
                    // data is untouched and a rejoin retries the load.
                    log.severe("[PlayerSession] DB load failed for " + player.getName()
                            + " - disconnecting to protect saved progress: " + ex.getMessage());
                    player.kick(net.kyori.adventure.text.Component.text(
                            "Cerberus could not load your saved data (database error).\n"
                            + "Your progress was not changed. Please rejoin; if this repeats, "
                            + "check the server log."));
                    return;
                }

                session.setCustomId(data.customId());
                svc.skills().setPlayerSkills(player, data.levels(), data.xp());
                svc.chunkDiscovery().acceptLoadedChunks(uuid, data.visitedChunks());
                session.setState(PlayerSession.State.ACTIVE);
                completeJoinSetup(player);
                log.info("[PlayerSession] Data loaded for " + player.getName() + ", session gen=" + generation);
            }, null);
            return null;
        });
    }

    private void completeJoinSetup(Player player) {
        try {
            svc.defense().updateDefense(player);
        } catch (Exception e) {
            log.warning("[PlayerSession] Phase SYSTEMS_START(defense) failed for " + player.getName() + ": " + e.getMessage());
        }

        try {
            svc.virtualHealth().initializeForJoin(player);
            svc.mana().resetMana(player, svc.mana().getMaxMana(player));
        } catch (Exception e) {
            log.warning("[PlayerSession] Phase SYSTEMS_START(resetHealth) failed for " + player.getName() + ": " + e.getMessage());
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

        try {
            svc.virtualHealth().enforceHealthInvariant(player, "JOIN_COMPLETE");
            svc.hud().updateHUD(player);
        } catch (Exception e) {
            log.warning("[PlayerSession] Phase HUD_UPDATE failed for " + player.getName() + ": " + e.getMessage());
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onPlayerQuit(PlayerQuitEvent event) {
        cleanupPlayer(event.getPlayer());
    }

    public void cleanupPlayer(Player player) {
        UUID uuid = player.getUniqueId();
        PlayerSession session = sessions.remove(uuid);
        if (session == null || session.isClosed()) {
            return;
        }

        session.setState(PlayerSession.State.CLEANING_UP);

        try {
            svc.effects().removeAllEffects(player);
        } catch (Exception e) {
            log.warning("[PlayerSession] Cleanup(effects) failed for " + player.getName() + ": " + e.getMessage());
        }

        Map<String, Integer> levels = svc.skills().getPlayerSkillLevels(player);
        Map<String, Integer> xp = svc.skills().getPlayerSkillXP(player);
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

        try {
            svc.chunkDiscovery().unloadPlayer(uuid);
        } catch (Exception e) {
            log.warning("[PlayerSession] Cleanup(chunkDiscovery) failed: " + e.getMessage());
        }
        try {
            svc.mana().removePlayer(uuid);
        } catch (Exception e) {
            log.warning("[PlayerSession] Cleanup(mana) failed: " + e.getMessage());
        }
        try {
            svc.virtualHealth().removePlayer(uuid);
        } catch (Exception e) {
            log.warning("[PlayerSession] Cleanup(virtualHealth) failed: " + e.getMessage());
        }
        try {
            svc.defenseBar().removePlayer(player);
        } catch (Exception e) {
            log.warning("[PlayerSession] Cleanup(defenseBar) failed: " + e.getMessage());
        }
        try {
            svc.defense().removePlayer(uuid);
        } catch (Exception e) {
            log.warning("[PlayerSession] Cleanup(defense) failed: " + e.getMessage());
        }
        try {
            svc.skills().removePlayer(uuid);
        } catch (Exception e) {
            log.warning("[PlayerSession] Cleanup(skills) failed: " + e.getMessage());
        }

        try {
            if (plugin.getSessionLogger() != null) {
                plugin.getSessionLogger().flushAndRemove(uuid);
            }
        } catch (Exception e) {
            log.warning("[PlayerSession] Cleanup(sessionLogger) failed: " + e.getMessage());
        }

        CustomPlayer.unregister(uuid);
        session.setState(PlayerSession.State.CLOSED);
        log.info("[PlayerSession] Cleaned up session for " + player.getName());
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();
        PlayerSession session = sessions.get(player.getUniqueId());
        if (session == null || !session.isActive()) {
            return;
        }

        session.setState(PlayerSession.State.DEAD);
        event.setKeepInventory(true);
        event.setKeepLevel(true);
        event.getDrops().clear();
        event.setDroppedExp(0);

        MobContext killerMob = null;
        EntityDamageEvent cause = player.getLastDamageCause();
        if (cause instanceof EntityDamageByEntityEvent byEntity) {
            Entity damager = byEntity.getDamager();
            if (damager instanceof LivingEntity livingDamager) {
                killerMob = svc.mobRegistry().resolve(livingDamager);
            }
        }

        if (!svc.virtualHealth().hasPendingCustomDeath(player)) {
            svc.virtualHealth().markPendingDeath(player);
        }
        svc.virtualHealth().confirmVanillaDeath(player);

        session.setDeathContext(new DeathContext(
                svc.mana().getCurrentMana(player),
                svc.virtualHealth().snapshot(player).currentHp(),
                killerMob,
                System.currentTimeMillis()
        ));

        svc.mana().resetMana(player, 0);
        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_PLAYER_DEATH, 1.0F, 1.0F);

        // Folia does not fire PlayerRespawnEvent reliably, even when calling
        // player.spigot().respawn() directly. Instead, we let the server handle
        // the visual respawn via DO_IMMEDIATE_RESPAWN gamerule, then schedule
        // restoration ourselves on a delay using the global region scheduler.
        Bukkit.getGlobalRegionScheduler().runDelayed(plugin, scheduledTask -> {
            if (!player.isOnline()) return;

            log.info("[Respawn] Direct restore triggered for " + player.getName());

            // Run the actual restoration on the player's entity scheduler
            player.getScheduler().run(plugin, entityTask -> {
                if (!player.isOnline()) return;

                // Skip if already restored (e.g. event fired before our delayed task)
                if (session.getState() == PlayerSession.State.ACTIVE) {
                    log.info("[Respawn] Skipping direct restore for " + player.getName() + " — already restored");
                    return;
                }

                session.consumeDeathContext();
                svc.virtualHealth().markRespawnSeen(player);
                svc.defense().updateDefense(player);
                svc.virtualHealth().restoreAfterRespawn(player, svc.hud());
                svc.mana().resetMana(player, svc.mana().getMaxMana(player));
                svc.mana().startManaRegenTask(player);
                session.setState(PlayerSession.State.ACTIVE);
            }, null);
        }, 10L);
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = false)
    public void onPlayerRespawn(PlayerRespawnEvent event) {
        Player player = event.getPlayer();
        PlayerSession session = sessions.get(player.getUniqueId());
        if (session == null) {
            return;
        }

        session.setState(PlayerSession.State.RESPAWNING);
        svc.virtualHealth().markRespawnSeen(player);

        // Delay restoration by 1 tick — the player entity is not fully
        // re-initialized during PlayerRespawnEvent. Scheduled on the
        // player's entity scheduler for Folia compatibility.
        player.getScheduler().run(plugin, scheduledTask -> {
            if (!player.isOnline()) return;

            session.consumeDeathContext();
            svc.defense().updateDefense(player);
            svc.virtualHealth().restoreAfterRespawn(player, svc.hud());
            svc.mana().resetMana(player, svc.mana().getMaxMana(player));
            svc.mana().startManaRegenTask(player);

            session.setState(PlayerSession.State.ACTIVE);
        }, null);
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = false)
    public void onPlayerPostRespawn(PlayerPostRespawnEvent event) {
        // No-op. Restoration handled in onPlayerRespawn with a tick delay.
        // Kept as a stub in case Paper/Folia starts firing this event in the future.
    }

    public void shutdown() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            cleanupPlayer(player);
        }
        dbExecutor.shutdown();
        try {
            if (!dbExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                log.warning("[PlayerSession] DB executor did not drain in 5s - forcing shutdown");
                dbExecutor.shutdownNow();
            }
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        }
    }

    private void enforceSpeedLimit(Player player) {
        var speedAttr = player.getAttribute(Attribute.MOVEMENT_SPEED);
        if (speedAttr == null) {
            return;
        }

        var allMods = new java.util.ArrayList<>(speedAttr.getModifiers());
        for (var mod : allMods) {
            speedAttr.removeModifier(mod);
        }
        speedAttr.setBaseValue(0.1);
    }


    private void giveAdminChestIfNeeded(Player player) {
        if (!player.hasPermission("cerberus.admin")) {
            return;
        }

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

    public PlayerSession getSession(UUID uuid) {
        return sessions.get(uuid);
    }
}
