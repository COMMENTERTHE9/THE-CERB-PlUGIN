package Manager;

import cerberus.world.cerb.CerberusPlugin;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Virtual health system — replaces vanilla health with a custom HP pool.
 * <p>
 * The damage pipeline (3-layer defense system):
 * <ol>
 *   <li>Invincibility frames (250ms)</li>
 *   <li>Elemental resistance (type-specific % reduction)</li>
 *   <li>Armor reduction (flat % from gear + skills, pre-computed with penetration)</li>
 *   <li>Defense bar partial absorption (scales with bar fullness, 0%–70%)</li>
 *   <li>Subtract remainder from virtual health + play gated sound</li>
 * </ol>
 */
public class PlayerVirtualHealthManager {
    private final Map<UUID, Double> playerVirtualHealth = new ConcurrentHashMap<>();
    private final Map<UUID, Double> playerMaxVirtualHealth = new ConcurrentHashMap<>();
    private final Map<UUID, Double> playerArmorReduction = new ConcurrentHashMap<>();
    private final Map<UUID, Double> playerFireResistance = new ConcurrentHashMap<>();
    private final Map<UUID, Double> playerColdResistance = new ConcurrentHashMap<>();
    private final Map<UUID, Double> playerDrowningResistance = new ConcurrentHashMap<>();
    private final Map<UUID, Long> playerLastDamageTime = new ConcurrentHashMap<>();
    private final Map<UUID, Long> lastHitSoundTime = new ConcurrentHashMap<>();

    private final long invincibilityTimeMillis = 250;
    private final long respawnInvincibilityTimeMillis = 3000;

    private final Map<UUID, ScheduledTask> healthUpdateTasks = new ConcurrentHashMap<>();
    private final Map<UUID, ScheduledTask> healthRegenTasks = new ConcurrentHashMap<>();

    private boolean isHandlingEvent = false;
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

    // ── Virtual Health CRUD ──────────────────────────────────────

    public void setPlayerVirtualHealth(Player player, double health) {
        UUID playerId = player.getUniqueId();
        double maxHealth = getPlayerMaxVirtualHealth(player);
        health = Math.max(0, Math.min(health, maxHealth));
        playerVirtualHealth.put(playerId, health);
        checkPlayerDeath(player, health);
    }

    public void resetHealth(Player player) {
        double maxVH = getPlayerMaxVirtualHealth(player);
        setPlayerVirtualHealth(player, maxVH);
        double maxReal = player.getAttribute(Attribute.MAX_HEALTH).getValue();
        player.setHealth(maxReal);
    }

    public double getPlayerVirtualHealth(Player player) {
        return playerVirtualHealth.getOrDefault(player.getUniqueId(), 100.0);
    }

    public boolean isVirtualHealthDepleted(Player player) {
        return getPlayerVirtualHealth(player) <= 0.0;
    }

    public void setPlayerMaxVirtualHealth(Player player, double maxHealth) {
        playerMaxVirtualHealth.put(player.getUniqueId(), maxHealth);
        if (getPlayerVirtualHealth(player) > maxHealth) {
            setPlayerVirtualHealth(player, maxHealth);
        }
    }

    public double getPlayerMaxVirtualHealth(Player player) {
        return playerMaxVirtualHealth.getOrDefault(player.getUniqueId(), 100.0);
    }

    public void increasePlayerVirtualHealth(Player player, double amount) {
        setPlayerVirtualHealth(player, getPlayerVirtualHealth(player) + amount);
    }

    public void decreasePlayerVirtualHealth(Player player, double amount) {
        setPlayerVirtualHealth(player, getPlayerVirtualHealth(player) - amount);
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

    private void checkPlayerDeath(Player player, double health) {
        if (health <= 0 && !player.isDead()) {
            // Set vanilla health to 0 — triggers PlayerDeathEvent.
            // PlayerSessionManager handles the respawn; do NOT call spigot().respawn() here.
            player.setHealth(0);
        }
    }

    // ── Stat Setters/Getters ─────────────────────────────────────

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
        playerLastDamageTime.put(player.getUniqueId(),
                System.currentTimeMillis() - invincibilityTimeMillis + respawnInvincibilityTimeMillis);
        player.sendMessage("You are invincible for a few seconds after respawning!");
    }

    // ── Damage Pipeline ──────────────────────────────────────────

    /**
     * Legacy overload for callers that don't use the full pipeline yet.
     */
    public void applyDamage(Entity entity, double damage, CustomDamageType damageType, Entity damager) {
        if (!(entity instanceof Player player)) return;
        double effectiveArmor = getArmorReduction(player);
        Player attackerPlayer = (damager instanceof Player p) ? p : null;
        CombatContext ctx = new CombatContext(attackerPlayer, player, damageType, damage);
        applyDamage(player, damage, effectiveArmor, damageType, damager, ctx);
    }

    /**
     * Canonical 3-layer damage pipeline.
     * <p>
     * Steps:
     * <ol>
     *   <li>Invincibility frame check</li>
     *   <li>Elemental resistance (type-specific)</li>
     *   <li>Armor reduction (pre-computed with penetration by caller)</li>
     *   <li>Defense bar partial absorption</li>
     *   <li>Subtract from virtual health + gated hit sound</li>
     * </ol>
     *
     * @param effectiveArmorReduction armor % with penetration already applied by caller
     */
    public void applyDamage(Player player, double damage, double effectiveArmorReduction,
                            CustomDamageType damageType, Entity damager, CombatContext ctx) {
        if (isHandlingEvent) return;
        if (player.isDead() || player.getHealth() <= 0) return;

        long currentTime = System.currentTimeMillis();

        // Step 1: Invincibility frames
        Long lastDamageTime = playerLastDamageTime.get(player.getUniqueId());
        if (lastDamageTime != null && currentTime - lastDamageTime < invincibilityTimeMillis) {
            return;
        }
        playerLastDamageTime.put(player.getUniqueId(), currentTime);

        double current = damage;
        ctx.appendTrace("Incoming", 0, current, null);

        // Capture pre-pipeline state for logging
        double healthBefore = getPlayerVirtualHealth(player);
        double healthMax = getPlayerMaxVirtualHealth(player);
        double defBarBefore = 0;
        double defBarMax = 0;
        if (defenseBarManager != null) {
            DefenseBarManager.DefenseBar bar = defenseBarManager.getDefenseBar(player);
            defBarBefore = bar.getCurrent();
            defBarMax = bar.getMaximum();
        }

        // Step 2: Elemental resistance
        double typeResist = getTypeSpecificResistance(player, damageType);
        if (typeResist > 0) {
            double before = current;
            current *= (1.0 - typeResist);
            ctx.appendTrace("ElemResist", before, current,
                    damageType.name() + " " + String.format("%.0f%%", typeResist * 100));
        }
        ctx.defense().setAfterElementalResist(current);

        // Step 3: Armor reduction (penetration already applied by caller)
        if (effectiveArmorReduction > 0) {
            double before = current;
            current *= (1.0 - effectiveArmorReduction);
            ctx.appendTrace("Armor", before, current,
                    String.format("%.0f%% reduction", effectiveArmorReduction * 100));
        }
        ctx.defense().setAfterArmor(current);

        // Step 4: Defense bar partial absorption
        double absorbed = 0;
        double absorptionRate = 0;
        if (defenseBarManager != null && current > 0) {
            double beforeBar = current;
            current = defenseBarManager.applyDamageToBar(player, current);
            absorbed = beforeBar - current;
            DefenseBarManager.DefenseBar bar = defenseBarManager.getDefenseBar(player);
            absorptionRate = bar.getLastAbsorptionRate();
            ctx.appendTrace("DefenseBar", beforeBar, current,
                    String.format("%.0f%% absorb, -%.1f", absorptionRate * 100, absorbed));
        }
        ctx.defense().setAfterDefenseAbsorption(current);
        ctx.defense().setDefenseAbsorptionRate(absorptionRate);

        // Clamp to minimum 0
        current = Math.max(0, current);

        // Step 5: Assign final damage
        ctx.defense().setFinalDamage(current);
        ctx.appendTrace("Final", damage, current, null);

        // Step 6: Subtract from virtual health + gated hit sound
        if (current > 0) {
            decreasePlayerVirtualHealth(player, current);
            playGatedHitSound(player, damageType);
        }

        // Session logging
        double healthAfter = getPlayerVirtualHealth(player);
        if (sessionLogger != null) {
            sessionLogger.logCombat(player.getUniqueId(), ctx, healthBefore, healthAfter,
                    healthMax, defBarBefore, defBarMax, effectiveArmorReduction,
                    typeResist, absorptionRate, absorbed);
        }

        // Knockback: handled by vanilla (event.setDamage(0) in listener)
    }

    /** Get type-specific resistance for the given damage type. */
    private double getTypeSpecificResistance(Player player, CustomDamageType damageType) {
        return switch (damageType) {
            case FIRE, BURN_DOT, PYROMANCY -> getPlayerFireResistance(player);
            case FREEZE, FREEZE_DOT, CRYOMANCY -> getPlayerColdResistance(player);
            case DROWNING -> getPlayerDrowningResistance(player);
            default -> 0.0;
        };
    }

    /**
     * Play a hit sound, gated by per-player per-category cooldown.
     */
    private void playGatedHitSound(Player player, CustomDamageType damageType) {
        long now = System.currentTimeMillis();
        UUID uuid = player.getUniqueId();
        int cooldownMs = combatConfig != null
                ? combatConfig.getSoundCooldownMs(damageType) : 400;

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

    // ── Projectile handler ───────────────────────────────────────

    public void handleProjectileHit(Projectile projectile, Entity target) {
        if (target instanceof Player || target instanceof Entity) {
            double damage = projectile.getVelocity().length();
            Entity shooter = projectile.getShooter() instanceof Entity ? (Entity) projectile.getShooter() : null;
            applyDamage(target, damage, CustomDamageType.PROJECTILE, shooter);
        }
    }

    // ── Health Regen ─────────────────────────────────────────────

    public void applyHealthRegen(Player player, double normalRegenRate, double reducedRegenRate,
                                 long duration, long damageTimeout) {
        final long[] timeElapsed = {0};
        final long[] lastDmgTime = {System.currentTimeMillis()};

        player.getScheduler().runAtFixedRate(plugin, task -> {
            if (timeElapsed[0] >= duration || player.isDead()) {
                task.cancel();
                return;
            }
            double currentHealth = getPlayerVirtualHealth(player);
            double maxHealth = getPlayerMaxVirtualHealth(player);
            long timeSinceLastDamage = System.currentTimeMillis() - lastDmgTime[0];
            double regenRate = timeSinceLastDamage > damageTimeout ? normalRegenRate : reducedRegenRate;
            if (currentHealth < maxHealth) {
                increasePlayerVirtualHealth(player, regenRate);
            }
            timeElapsed[0] += 20;
        }, null, 1L, 20L);
    }

    public void applyHealthRegen(Player player, double regenRate, long duration) {
        applyHealthRegen(player, regenRate, regenRate / 2, duration, 5000L);
    }

    // ── Persistent Health Regen ──────────────────────────────────

    /**
     * Start a persistent health regen task (like mana regen).
     * Runs every tick. Uses combat timeout from CombatConfig to switch
     * between out-of-combat and in-combat regen rates.
     * Idempotent — cancels any existing task first.
     */
    public void startHealthRegenTask(Player player) {
        stopHealthRegenTask(player);

        UUID uuid = player.getUniqueId();
        ScheduledTask task = player.getScheduler().runAtFixedRate(plugin, scheduledTask -> {
            if (!player.isOnline() || player.isDead()) {
                scheduledTask.cancel();
                healthRegenTasks.remove(uuid);
                return;
            }

            double current = getPlayerVirtualHealth(player);
            double max = getPlayerMaxVirtualHealth(player);
            if (current >= max) return; // already full

            // Determine regen rate based on combat state
            double baseRate = 2.0;
            double combatRate = 0.0;
            long combatTimeout = 8000;
            if (combatConfig != null) {
                baseRate = combatConfig.healthRegenBaseRate;
                combatRate = combatConfig.healthRegenCombatRate;
                combatTimeout = combatConfig.healthRegenCombatTimeoutMs;
            }

            boolean inCombat = false;
            Long lastDmg = playerLastDamageTime.get(uuid);
            if (lastDmg != null) {
                inCombat = (System.currentTimeMillis() - lastDmg) < combatTimeout;
            }

            double regenPerTick = (inCombat ? combatRate : baseRate) / 20.0;
            if (regenPerTick > 0) {
                double newHealth = Math.min(max, current + regenPerTick);
                playerVirtualHealth.put(uuid, newHealth);
            }

            // Fallback: tick defense bar regen if its own task died
            if (!inCombat && defenseBarManager != null) {
                DefenseBarManager.DefenseBar bar = defenseBarManager.getDefenseBar(player);
                if (bar.getCurrent() < bar.getMaximum()) {
                    double barRegen = combatConfig != null ? combatConfig.defBarRegenRate / 20.0 : 0.75;
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

    // ── HUD Update Task ──────────────────────────────────────────

    public void startHealthUpdateTask(Player player, PlayerHUDManager hudManager) {
        UUID uuid = player.getUniqueId();
        ScheduledTask existing = healthUpdateTasks.remove(uuid);
        if (existing != null && !existing.isCancelled()) {
            existing.cancel();
        }

        ScheduledTask task = player.getScheduler().runAtFixedRate(plugin, scheduledTask -> {
            if (!player.isOnline()) {
                scheduledTask.cancel();
                healthUpdateTasks.remove(uuid);
                return;
            }
            hudManager.updateHUD(player);
        }, null, 1L, 20L);
        healthUpdateTasks.put(uuid, task);
    }

    // ── Cleanup ──────────────────────────────────────────────────

    public void removePlayer(UUID uuid) {
        ScheduledTask task = healthUpdateTasks.remove(uuid);
        if (task != null && !task.isCancelled()) {
            task.cancel();
        }
        ScheduledTask regenTask = healthRegenTasks.remove(uuid);
        if (regenTask != null && !regenTask.isCancelled()) {
            regenTask.cancel();
        }
        playerVirtualHealth.remove(uuid);
        playerMaxVirtualHealth.remove(uuid);
        playerArmorReduction.remove(uuid);
        playerFireResistance.remove(uuid);
        playerColdResistance.remove(uuid);
        playerDrowningResistance.remove(uuid);
        playerLastDamageTime.remove(uuid);
        lastHitSoundTime.remove(uuid);
    }
}
