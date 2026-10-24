package Manager;

import Listener.PlayerSession;
import cerberus.world.cerb.CerberusPlugin;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Authoritative health lifecycle service for Cerberus custom HP.
 * Owns per-player health state, regen, invariant repair, and lifecycle logging.
 */
public class PlayerVirtualHealthManager {
    private static final long DEFAULT_INVINCIBILITY_TIME_MILLIS = 250L;
    private static final long DEFAULT_RESPAWN_INVINCIBILITY_TIME_MILLIS = 3000L;

    private final Map<UUID, HealthLifecycleEngine.MutableState> healthStates = new ConcurrentHashMap<>();
    private final Map<UUID, Double> playerArmorReduction = new ConcurrentHashMap<>();
    private final Map<UUID, Double> playerFireResistance = new ConcurrentHashMap<>();
    private final Map<UUID, Double> playerColdResistance = new ConcurrentHashMap<>();
    private final Map<UUID, Double> playerDrowningResistance = new ConcurrentHashMap<>();
    private final Map<UUID, Long> lastHitSoundTime = new ConcurrentHashMap<>();
    private final Map<UUID, ScheduledTask> healthUpdateTasks = new ConcurrentHashMap<>();
    private final Map<UUID, ScheduledTask> healthRegenTasks = new ConcurrentHashMap<>();

    private final HealthLifecycleEngine lifecycleEngine = new HealthLifecycleEngine();
    private final CerberusPlugin plugin;
    private DefenseBarManager defenseBarManager;
    private CombatConfig combatConfig;
    private SessionLogger sessionLogger;

    public PlayerVirtualHealthManager(CerberusPlugin plugin) {
        this.plugin = plugin;
    }

    public void setDefenseBarManager(DefenseBarManager defenseBarManager) {
        this.defenseBarManager = defenseBarManager;
    }

    public void setCombatConfig(CombatConfig combatConfig) {
        this.combatConfig = combatConfig;
    }

    public void setSessionLogger(SessionLogger sessionLogger) {
        this.sessionLogger = sessionLogger;
    }

    public void initializeForJoin(Player player) {
        HealthLifecycleEngine.MutableState state = getOrCreateState(player);
        lifecycleEngine.initializeForJoin(state);
        clearRecentDamageState(player);
        if (defenseBarManager != null) {
            defenseBarManager.resetDefenseBar(player);
        }
        syncVanillaHealthToMax(player);
        enforceHealthInvariant(player, "JOIN_INIT");
        logLifecycle("JOIN_INIT", player, null);
    }

    public void markRespawnSeen(Player player) {
        lifecycleEngine.markRespawning(getOrCreateState(player));
        logLifecycle("RESPAWN_EVENT_SEEN", player, null);
    }

    public void restoreAfterRespawn(Player player, PlayerHUDManager hudManager) {
        HealthLifecycleEngine.MutableState state = getOrCreateState(player);
        lifecycleEngine.restoreAfterRespawn(state, System.currentTimeMillis(), getRespawnInvincibilityMillis());
        lastHitSoundTime.remove(player.getUniqueId());

        if (defenseBarManager != null) {
            defenseBarManager.resetDefenseBar(player);
        }

        syncVanillaHealthToMax(player);
        startHealthRegenTask(player);
        if (hudManager != null) {
            startHealthUpdateTask(player, hudManager);
            hudManager.updateHUD(player);
        }

        enforceHealthInvariant(player, "RESPAWN_RESTORED");
        logLifecycle("RESPAWN_RESTORED", player, null);
    }

    public void confirmVanillaDeath(Player player) {
        lifecycleEngine.confirmDeath(getOrCreateState(player));
        stopHealthUpdateTask(player);
        stopHealthRegenTask(player);
        logLifecycle("DEATH_EVENT_CONFIRMED", player, null);
    }

    public void markPendingDeath(Player player) {
        lifecycleEngine.markPendingDeath(getOrCreateState(player), System.currentTimeMillis());
        logLifecycle("CUSTOM_LETHAL", player, null);
    }

    public boolean hasPendingCustomDeath(Player player) {
        return getOrCreateState(player).pendingCustomDeath();
    }

    public boolean isRespawning(Player player) {
        return getOrCreateState(player).lifecycleState() == HealthLifecycleState.RESPAWNING;
    }

    public double getGuaranteedVanillaDeathDamage(Player player) {
        double vanillaHealth = safeVanillaHealth(player);
        double absorption = Math.max(0.0, player.getAbsorptionAmount());
        return Math.max(1_000_000.0, vanillaHealth + absorption + 1.0);
    }

    public void schedulePendingDeathFinalizer(Player player) {
        player.getScheduler().runDelayed(plugin, task -> {
            if (!player.isOnline() || player.isDead()) {
                return;
            }
            if (!hasPendingCustomDeath(player)) {
                return;
            }
            // Skip if death is already confirmed — a direct restore is scheduled
            // and will handle the transition. Firing the fallback here would just
            // be noise in the logs.
            HealthLifecycleState state = getOrCreateState(player).lifecycleState();
            if (state == HealthLifecycleState.DEAD || state == HealthLifecycleState.RESPAWNING) {
                return;
            }

            logLifecycle("CUSTOM_LETHAL_FALLBACK", player, "forcing_vanilla_death");
            player.setHealth(0.0);
        }, null, 1L);
    }

    public HealthStateSnapshot snapshot(Player player) {
        return getOrCreateState(player).snapshot();
    }

    /** Read-only: the snapshot if this player is tracked, else null. Never creates state. */
    public HealthStateSnapshot peekSnapshot(java.util.UUID playerId) {
        HealthLifecycleEngine.MutableState s = healthStates.get(playerId);
        return s == null ? null : s.snapshot();
    }

    public boolean enforceHealthInvariant(Player player, String detail) {
        if (player == null || !player.isOnline()) {
            return false;
        }

        HealthLifecycleEngine.MutableState state = getOrCreateState(player);
        boolean repaired = lifecycleEngine.restoreIfInvariantBroken(state, safeVanillaHealth(player));
        if (repaired) {
            syncVanillaHealthToMax(player);
            if (defenseBarManager != null) {
                defenseBarManager.resetDefenseBar(player);
            }
            logLifecycle("HEALTH_INVARIANT_BROKEN", player, detail);
        }
        return repaired;
    }

    /** Regeneration scale per player (the realm magnitude): health and bar regen keep pace with realm-sized pools. */
    private volatile java.util.function.ToDoubleFunction<UUID> regenScale = id -> 1.0;

    public void setRegenScale(java.util.function.ToDoubleFunction<UUID> scale) {
        this.regenScale = scale == null ? id -> 1.0 : scale;
    }

    private double regenScale(Player player) {
        double s = regenScale.applyAsDouble(player.getUniqueId());
        return Double.isFinite(s) && s > 0 ? s : 1.0;
    }

    public void setPlayerVirtualHealth(Player player, double health) {
        lifecycleEngine.setCurrentHp(getOrCreateState(player), health);
    }

    public void resetHealth(Player player) {
        resetForAdminHeal(player);
    }

    public void resetForAdminHeal(Player player) {
        HealthLifecycleEngine.MutableState state = getOrCreateState(player);
        lifecycleEngine.restoreAfterRespawn(state, System.currentTimeMillis(), 0L);
        clearRecentDamageState(player);
        if (defenseBarManager != null) {
            defenseBarManager.resetDefenseBar(player);
        }
        syncVanillaHealthToMax(player);
    }

    public double getPlayerVirtualHealth(Player player) {
        return getOrCreateState(player).currentHp();
    }

    public boolean isVirtualHealthDepleted(Player player) {
        return getPlayerVirtualHealth(player) <= 0.0;
    }

    public void clearRecentDamageState(Player player) {
        lifecycleEngine.clearRecentDamageState(getOrCreateState(player));
        lastHitSoundTime.remove(player.getUniqueId());
    }

    public void setPlayerMaxVirtualHealth(Player player, double maxHealth) {
        lifecycleEngine.setMaxHp(getOrCreateState(player), maxHealth);
    }

    public double getPlayerMaxVirtualHealth(Player player) {
        return getOrCreateState(player).maxHp();
    }

    public void increasePlayerVirtualHealth(Player player, double amount) {
        heal(player, amount);
    }

    public void decreasePlayerVirtualHealth(Player player, double amount) {
        HealthLifecycleEngine.MutableState state = getOrCreateState(player);
        lifecycleEngine.setCurrentHp(state, state.currentHp() - amount);
    }

    public void increaseMaxHealth(Player player, double amount) {
        setPlayerMaxVirtualHealth(player, getPlayerMaxVirtualHealth(player) + amount);
    }

    public void setMaxHealth(Player player, double maxHealth) {
        setPlayerMaxVirtualHealth(player, maxHealth);
    }

    public double getMaxHealth(Player player) {
        return getPlayerMaxVirtualHealth(player);
    }

    public void heal(Player player, double amount) {
        lifecycleEngine.heal(getOrCreateState(player), amount);
    }

    public void setArmorReduction(Player player, double reduction) {
        playerArmorReduction.put(player.getUniqueId(), reduction);
    }

    public double getArmorReduction(Player player) {
        return playerArmorReduction.getOrDefault(player.getUniqueId(), 0.0);
    }

    public void setFireResistance(Player player, double value) {
        playerFireResistance.put(player.getUniqueId(), value);
    }

    public void setColdResistance(Player player, double value) {
        playerColdResistance.put(player.getUniqueId(), value);
    }

    public void setDrowningResistance(Player player, double value) {
        playerDrowningResistance.put(player.getUniqueId(), value);
    }

    public double getPlayerFireResistance(Player player) {
        return playerFireResistance.getOrDefault(player.getUniqueId(), 0.0);
    }

    public double getPlayerColdResistance(Player player) {
        return playerColdResistance.getOrDefault(player.getUniqueId(), 0.0);
    }

    public double getPlayerDrowningResistance(Player player) {
        return playerDrowningResistance.getOrDefault(player.getUniqueId(), 0.0);
    }

    public void applyRespawnInvincibility(Player player) {
        lifecycleEngine.applyRespawnInvincibility(
                getOrCreateState(player),
                System.currentTimeMillis(),
                getRespawnInvincibilityMillis()
        );
    }

    public DamageResolution applyDamage(Entity entity, double damage, CustomDamageType damageType, Entity damager) {
        if (!(entity instanceof Player player)) {
            return DamageResolution.noDamage(0.0);
        }

        double effectiveArmor = getArmorReduction(player);
        Player attackerPlayer = (damager instanceof Player damagerPlayer) ? damagerPlayer : null;
        CombatContext ctx = new CombatContext(attackerPlayer, player, damageType, damage);
        return applyResolvedDamage(player, damage, effectiveArmor, damageType, damager, ctx);
    }

    public DamageResolution applyResolvedDamage(Player player, double damage, double effectiveArmorReduction,
                                                CustomDamageType damageType, Entity damager, CombatContext ctx) {
        HealthLifecycleEngine.MutableState state = getOrCreateState(player);
        if (player.isDead() || player.getHealth() <= 0.0) {
            return DamageResolution.noDamage(state.currentHp());
        }
        if (state.pendingCustomDeath()
                || state.lifecycleState() == HealthLifecycleState.DEAD
                || state.lifecycleState() == HealthLifecycleState.RESPAWNING) {
            return DamageResolution.noDamage(state.currentHp());
        }

        long currentTime = System.currentTimeMillis();
        if (currentTime < state.invulnerableUntil()) {
            return DamageResolution.noDamage(state.currentHp());
        }

        double current = damage;
        ctx.appendTrace("Incoming", 0, current, null);

        double healthBefore = state.currentHp();
        double healthMax = state.maxHp();
        double defBarBefore = 0.0;
        double defBarMax = 0.0;
        if (defenseBarManager != null) {
            DefenseBarManager.DefenseBar bar = defenseBarManager.getDefenseBar(player);
            defBarBefore = bar.getCurrent();
            defBarMax = bar.getMaximum();
        }

        double typeResist = getTypeSpecificResistance(player, damageType);
        if (typeResist > 0.0) {
            double before = current;
            current *= (1.0 - typeResist);
            ctx.appendTrace("ElemResist", before, current,
                    damageType.name() + " " + String.format("%.0f%%", typeResist * 100.0));
        }
        ctx.defense().setAfterElementalResist(current);

        if (effectiveArmorReduction > 0.0) {
            double before = current;
            current *= (1.0 - effectiveArmorReduction);
            ctx.appendTrace("Armor", before, current,
                    String.format("%.0f%% reduction", effectiveArmorReduction * 100.0));
        }
        ctx.defense().setAfterArmor(current);

        double absorbed = 0.0;
        double absorptionRate = 0.0;
        if (defenseBarManager != null && current > 0.0) {
            double beforeBar = current;
            current = defenseBarManager.applyDamageToBar(player, current);
            absorbed = beforeBar - current;
            DefenseBarManager.DefenseBar bar = defenseBarManager.getDefenseBar(player);
            absorptionRate = bar.getLastAbsorptionRate();
            ctx.appendTrace("DefenseBar", beforeBar, current,
                    String.format("%.0f%% absorb, -%.1f", absorptionRate * 100.0, absorbed));
        }
        ctx.defense().setAfterDefenseAbsorption(current);
        ctx.defense().setDefenseAbsorptionRate(absorptionRate);

        current = Math.max(0.0, current);
        ctx.defense().setFinalDamage(current);
        ctx.appendTrace("Final", damage, current, null);

        DamageResolution lifecycleResolution =
                lifecycleEngine.applyResolvedDamage(state, current, currentTime, getDamageInvincibilityMillis());
        DamageResolution resolution = new DamageResolution(
                lifecycleResolution.finalDamage(),
                lifecycleResolution.lethal(),
                absorbed,
                lifecycleResolution.remainingHp(),
                lifecycleResolution.skipped()
        );

        if (resolution.finalDamage() > 0.0) {
            playGatedHitSound(player, damageType);
        }

        if (sessionLogger != null) {
            sessionLogger.logCombat(player.getUniqueId(), ctx, healthBefore, resolution.remainingHp(),
                    healthMax, defBarBefore, defBarMax, effectiveArmorReduction,
                    typeResist, absorptionRate, absorbed);
        }

        // Per-hit line only for players with /cerb combatdebug on: at INFO for every hit it buried
        // real problems in latest.log. Full per-hit detail is still in the session log above.
        if (plugin != null && plugin.isCombatDebugEnabled(player.getUniqueId())) {
            logLifecycle("DAMAGE_APPLIED", player,
                    "type=" + damageType.name() + " final=" + String.format("%.2f", resolution.finalDamage()));
        }

        if (resolution.lethal()) {
            markPendingDeath(player);
        }

        return resolution;
    }

    private double getTypeSpecificResistance(Player player, CustomDamageType damageType) {
        return switch (damageType) {
            case FIRE, BURN_DOT, PYROMANCY -> getPlayerFireResistance(player);
            case FREEZE, FREEZE_DOT, CRYOMANCY -> getPlayerColdResistance(player);
            case DROWNING -> getPlayerDrowningResistance(player);
            default -> 0.0;
        };
    }

    private long getDamageInvincibilityMillis() {
        return combatConfig != null ? combatConfig.damageInvincibilityMs : DEFAULT_INVINCIBILITY_TIME_MILLIS;
    }

    private long getRespawnInvincibilityMillis() {
        return combatConfig != null ? combatConfig.respawnInvincibilityMs : DEFAULT_RESPAWN_INVINCIBILITY_TIME_MILLIS;
    }

    private void playGatedHitSound(Player player, CustomDamageType damageType) {
        long now = System.currentTimeMillis();
        UUID uuid = player.getUniqueId();
        int cooldownMs = combatConfig != null ? combatConfig.getSoundCooldownMs(damageType) : 400;

        Long lastSound = lastHitSoundTime.get(uuid);
        if (lastSound != null && (now - lastSound) < cooldownMs) {
            return;
        }
        lastHitSoundTime.put(uuid, now);

        Sound sound = switch (damageType) {
            case MANA_DRAIN, MANA_BURN -> Sound.BLOCK_BEACON_DEACTIVATE;
            case FIRE, BURN_DOT, PYROMANCY, LAVA, HOT_FLOOR, CAMPFIRE -> Sound.ENTITY_GENERIC_BURN;
            case FREEZE, FREEZE_DOT, CRYOMANCY -> Sound.BLOCK_GLASS_BREAK;
            case DROWNING -> Sound.ENTITY_PLAYER_SPLASH;
            default -> Sound.ENTITY_PLAYER_HURT;
        };
        player.getWorld().playSound(player.getLocation(), sound, 1.0f, 1.0f);
    }

    public void applyHealthRegen(Player player, double normalRegenRate, double reducedRegenRate,
                                 long duration, long damageTimeout) {
        final long[] ticksElapsed = {0L};

        player.getScheduler().runAtFixedRate(plugin, task -> {
            if (ticksElapsed[0] >= duration || !player.isOnline() || player.isDead()) {
                task.cancel();
                return;
            }

            HealthStateSnapshot snapshot = snapshot(player);
            long timeSinceLastDamage = snapshot.lastDamageAt() == 0L
                    ? Long.MAX_VALUE
                    : System.currentTimeMillis() - snapshot.lastDamageAt();
            double regenRate = timeSinceLastDamage > damageTimeout ? normalRegenRate : reducedRegenRate;
            if (snapshot.currentHp() < snapshot.maxHp()) {
                heal(player, regenRate);
            }

            ticksElapsed[0] += 20L;
        }, null, 1L, 20L);
    }

    public void applyHealthRegen(Player player, double regenRate, long duration) {
        applyHealthRegen(player, regenRate, regenRate / 2.0, duration, 5000L);
    }

    public void startHealthRegenTask(Player player) {
        stopHealthRegenTask(player);

        UUID uuid = player.getUniqueId();
        ScheduledTask task = player.getScheduler().runAtFixedRate(plugin, scheduledTask -> {
            if (!player.isOnline() || player.isDead()) {
                scheduledTask.cancel();
                healthRegenTasks.remove(uuid);
                return;
            }

            HealthStateSnapshot snapshot = snapshot(player);
            if (snapshot.pendingCustomDeath()
                    || snapshot.lifecycleState() == HealthLifecycleState.DEAD
                    || snapshot.currentHp() >= snapshot.maxHp()) {
                return;
            }

            double baseRate = 2.0;
            double combatRate = 0.0;
            long combatTimeout = 8000L;
            if (combatConfig != null) {
                baseRate = combatConfig.healthRegenBaseRate;
                combatRate = combatConfig.healthRegenCombatRate;
                combatTimeout = combatConfig.healthRegenCombatTimeoutMs;
            }

            boolean inCombat = snapshot.lastDamageAt() != 0L
                    && (System.currentTimeMillis() - snapshot.lastDamageAt()) < combatTimeout;

            double scale = regenScale(player);
            double regenPerTick = (inCombat ? combatRate : baseRate) * scale / 20.0;
            if (regenPerTick > 0.0) {
                heal(player, regenPerTick);
            }

            if (!inCombat && defenseBarManager != null) {
                DefenseBarManager.DefenseBar bar = defenseBarManager.getDefenseBar(player);
                if (bar.getCurrent() < bar.getMaximum()) {
                    double barRegen = (combatConfig != null ? combatConfig.defBarRegenRate / 20.0 : 0.75) * scale;
                    bar.current = Math.min(bar.maximum, bar.current + barRegen);
                }
            }
        }, null, 20L, 1L);

        healthRegenTasks.put(uuid, task);
    }

    public void stopHealthRegenTask(Player player) {
        ScheduledTask existing = healthRegenTasks.remove(player.getUniqueId());
        if (existing != null && !existing.isCancelled()) {
            existing.cancel();
        }
    }

    public void startHealthUpdateTask(Player player, PlayerHUDManager hudManager) {
        UUID uuid = player.getUniqueId();
        stopHealthUpdateTask(player);

        ScheduledTask task = player.getScheduler().runAtFixedRate(plugin, scheduledTask -> {
            if (!player.isOnline()) {
                scheduledTask.cancel();
                healthUpdateTasks.remove(uuid);
                return;
            }

            enforceHealthInvariant(player, "HUD_TICK");
            if (!player.isDead()) {
                hudManager.updateHUD(player);
            }
        }, null, 1L, 20L);

        healthUpdateTasks.put(uuid, task);
    }

    public void stopHealthUpdateTask(Player player) {
        ScheduledTask existing = healthUpdateTasks.remove(player.getUniqueId());
        if (existing != null && !existing.isCancelled()) {
            existing.cancel();
        }
    }

    public void removePlayer(UUID uuid) {
        stopTask(healthUpdateTasks.remove(uuid));
        stopTask(healthRegenTasks.remove(uuid));
        healthStates.remove(uuid);
        playerArmorReduction.remove(uuid);
        playerFireResistance.remove(uuid);
        playerColdResistance.remove(uuid);
        playerDrowningResistance.remove(uuid);
        lastHitSoundTime.remove(uuid);
    }

    private void stopTask(ScheduledTask task) {
        if (task != null && !task.isCancelled()) {
            task.cancel();
        }
    }

    private HealthLifecycleEngine.MutableState getOrCreateState(Player player) {
        return healthStates.computeIfAbsent(player.getUniqueId(), lifecycleEngine::newState);
    }

    private double safeVanillaHealth(Player player) {
        if (player == null || player.isDead()) {
            return 0.0;
        }
        try {
            return Math.max(0.0, player.getHealth());
        } catch (Exception ignored) {
            return 0.0;
        }
    }

    private void syncVanillaHealthToMax(Player player) {
        if (player == null || !player.isOnline() || player.isDead()) {
            return;
        }

        double vanillaMax = 20.0;
        if (player.getAttribute(Attribute.MAX_HEALTH) != null) {
            vanillaMax = player.getAttribute(Attribute.MAX_HEALTH).getValue();
        }

        if (vanillaMax > 0.0) {
            player.setHealth(vanillaMax);
        }
    }

    private void logLifecycle(String transition, Player player, String detail) {
        if (plugin == null || player == null) {
            return;
        }

        HealthStateSnapshot snapshot = snapshot(player);
        String sessionState = "NONE";
        if (plugin.getPlayerSessionManager() != null) {
            PlayerSession session = plugin.getPlayerSessionManager().getSession(player.getUniqueId());
            if (session != null) {
                sessionState = session.getState().name();
            }
        }

        String detailFragment = detail == null || detail.isBlank() ? "" : " detail=" + detail;
        plugin.getLogger().info(String.format(
                "[HealthLifecycle] transition=%s player=%s uuid=%s hp=%.2f/%.2f vanilla=%.2f pending=%s lifecycle=%s session=%s invulnUntil=%d lastDamageAt=%d%s",
                transition,
                player.getName(),
                player.getUniqueId(),
                snapshot.currentHp(),
                snapshot.maxHp(),
                safeVanillaHealth(player),
                snapshot.pendingCustomDeath(),
                snapshot.lifecycleState(),
                sessionState,
                snapshot.invulnerableUntil(),
                snapshot.lastDamageAt(),
                detailFragment
        ));
    }
}
