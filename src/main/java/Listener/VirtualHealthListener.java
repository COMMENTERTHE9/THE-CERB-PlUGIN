package Listener;

import cerberus.world.cerb.CerberusPlugin;
import cerberus.world.cerb.CustomPlayer;
import Manager.PlayerVirtualHealthManager;
import Manager.DamageResolution;
import Manager.DamageTypeMapper;
import Manager.CustomDamageType;
import Manager.CombatConfig;
import Manager.CombatContext;
import Manager.CombatSkillProcessor;
import Manager.CerbDamageResultEvent;
import Manager.SpellDamageContext;
import Manager.MobContext;
import Manager.MobRegistry;
import Skills.FirstAidSkill;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityRegainHealthEvent;

import javax.annotation.Nullable;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages virtual health: intercepts damage, prevents vanilla regen.
 * Integrates CombatSkillProcessor and fires CerbDamageResultEvent.
 * <p>
 * Knockback: we do NOT apply custom knockback. Instead, we let vanilla
 * handle all knockback by setting event damage to 0 (not cancelling)
 * for players, and leaving non-player events untouched.
 */
public class VirtualHealthListener implements Listener {
    private final PlayerVirtualHealthManager virtualHealthManager;
    private final FirstAidSkill firstAidSkill;
    private final CerberusPlugin plugin;
    private boolean isHandlingEvent = false;

    // Injected after construction (setter pattern)
    private @Nullable CombatSkillProcessor combatSkillProcessor;

    // Spell damage registration — spells register context before dealing damage
    private final Map<Integer, SpellDamageContext> pendingSpellDamage = new ConcurrentHashMap<>();

    public VirtualHealthListener(PlayerVirtualHealthManager virtualHealthManager,
                                 FirstAidSkill firstAidSkill,
                                 CerberusPlugin plugin) {
        this.virtualHealthManager = virtualHealthManager;
        this.firstAidSkill = firstAidSkill;
        this.plugin = plugin;
    }

    /** Setter for CombatSkillProcessor — called after all managers are initialized. */
    public void setCombatSkillProcessor(CombatSkillProcessor processor) {
        this.combatSkillProcessor = processor;
    }

    /**
     * Register a spell damage context before the spell deals damage.
     * The entry is consumed (removed) when the damage event fires.
     * Entity IDs get reused — always clean immediately after reading.
     */
    public void registerSpellDamage(int entityId, SpellDamageContext context) {
        pendingSpellDamage.put(entityId, context);
        // Safety cleanup: remove after 2 ticks if not consumed
        Bukkit.getGlobalRegionScheduler().runDelayed(plugin, task -> pendingSpellDamage.remove(entityId), 2L);
    }

    private boolean hasActiveSession(Player player) {
        PlayerSessionManager manager = plugin.getPlayerSessionManager();
        if (manager == null) {
            return false;
        }

        PlayerSession session = manager.getSession(player.getUniqueId());
        return session != null && session.isActive();
    }

    // ------------------------------------------------------------
    // Main damage handler
    // ------------------------------------------------------------
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onEntityDamage(EntityDamageEvent event) {
        if (event.getDamage() <= 0) return;
        if (!(event.getEntity() instanceof LivingEntity)) return;
        if (event.getEntity() instanceof Player p) {
            if (!hasActiveSession(p)) {
                event.setCancelled(true);
                return;
            }
            if (virtualHealthManager.isRespawning(p)) {
                event.setCancelled(true);
                return;
            }
            if (virtualHealthManager.hasPendingCustomDeath(p)) {
                event.setDamage(virtualHealthManager.getGuaranteedVanillaDeathDamage(p));
                virtualHealthManager.schedulePendingDeathFinalizer(p);
                return;
            }
            virtualHealthManager.enforceHealthInvariant(p, "PRE_DAMAGE");
            if (p.isDead() || p.getHealth() <= 0) {
                event.setCancelled(true);
                return;
            }
        }
        if (isHandlingEvent) return;

        isHandlingEvent = true;
        try {
            LivingEntity entity = (LivingEntity) event.getEntity();
            CustomDamageType damageType = DamageTypeMapper.mapToCustomDamageType(event.getCause());
            Entity damager = null;
            SpellDamageContext spellCtx = null;

            if (event instanceof EntityDamageByEntityEvent ede) {
                damager = ede.getDamager();
                if (damager instanceof Projectile proj) {
                    handleProjectileHit(proj, entity, event);
                    return; // event.setDamage() handled inside
                }
                // Check for pending spell damage context
                spellCtx = pendingSpellDamage.remove(entity.getEntityId());
                if (spellCtx != null) {
                    damageType = spellCtx.damageType();
                }
            }

            double dmg = event.getDamage();
            Player attackerPlayer = (damager instanceof Player p) ? p : null;

            if (entity instanceof Player player) {
                // ── Player is the TARGET (mob/player hitting the player) ──
                CombatContext ctx = new CombatContext(attackerPlayer, entity, damageType, dmg);

                if (combatSkillProcessor != null && attackerPlayer != null) {
                    combatSkillProcessor.processAttackerSkills(ctx);
                }
                if (combatSkillProcessor != null) {
                    combatSkillProcessor.processDefenderSkills(ctx);
                }

                double modifiedDmg = ctx.attack().getModifiedDamage();

                // Resolve mob context once — used for both damage scaling and armor penetration
                MobContext mobCtx = null;
                if (damager instanceof LivingEntity livingDamager && !(damager instanceof Player)) {
                    MobRegistry mobRegistry = plugin.getMobRegistry();
                    if (mobRegistry != null) {
                        mobCtx = mobRegistry.resolve(livingDamager);
                    }
                }

                // Mob damage scaling — tier + level based multiplier
                if (mobCtx != null) {
                    CombatConfig cc = plugin.getCombatConfig();
                    if (cc != null && cc.mobDamageScaling.isEnabled()) {
                        int mobLevel = resolveMobLevel(damager instanceof LivingEntity le ? le : null);
                        double scalingMultiplier = cc.mobDamageScaling.calculate(mobCtx.tier(), mobLevel);
                        modifiedDmg *= scalingMultiplier;
                        ctx.attack().setModifiedDamage(modifiedDmg);
                        ctx.appendTrace("MobScaling", modifiedDmg / scalingMultiplier, modifiedDmg,
                                mobCtx.tier().name() + " Lv" + mobLevel + " x" + String.format("%.2f", scalingMultiplier));
                    }
                }

                // Resolve armor reduction with mob armor penetration
                double armorReduction = virtualHealthManager.getArmorReduction(player);
                if (mobCtx != null) {
                    double penetration = mobCtx.tier().armorPenetration;
                    armorReduction *= (1.0 - penetration);
                }

                DamageResolution resolution = virtualHealthManager.applyResolvedDamage(player, modifiedDmg, armorReduction,
                        damageType, damager, ctx);

                if (combatSkillProcessor != null && attackerPlayer != null) {
                    combatSkillProcessor.applyPostDamageEffects(ctx);
                }

                // Defender XP: award for taking damage (HAT, Survival)
                double damageTaken = ctx.defense().getFinalDamage();
                if (damageTaken <= 0) damageTaken = modifiedDmg * 0.1; // minimum XP even if fully mitigated
                if (combatSkillProcessor != null) {
                    combatSkillProcessor.awardDefenderHitXp(player, damageTaken);
                }

                Bukkit.getPluginManager().callEvent(new CerbDamageResultEvent(ctx));
                sendCombatDebugTrace(ctx);

                if (resolution.lethal()) {
                    event.setDamage(virtualHealthManager.getGuaranteedVanillaDeathDamage(player));
                    virtualHealthManager.schedulePendingDeathFinalizer(player);
                } else {
                    event.setDamage(0);
                    player.setHealth(player.getAttribute(Attribute.MAX_HEALTH).getValue());
                }
            } else {
                // ── Non-player entity is the TARGET (player hitting a mob) ──
                dmg = applyDamageMultiplierToEntity(entity, dmg);
                event.setDamage(dmg);

                // Award attacker per-hit XP when a PLAYER hits a MOB
                if (attackerPlayer != null && combatSkillProcessor != null && dmg > 0) {
                    CombatContext ctx = new CombatContext(attackerPlayer, entity, damageType, dmg);
                    combatSkillProcessor.processAttackerSkills(ctx); // for crit detection
                    combatSkillProcessor.awardAttackerHitXp(ctx, attackerPlayer);
                }
            }
        } finally {
            isHandlingEvent = false;
        }
    }

    /**
     * Resolve the level of an attacking mob from its PDC tag, set by
     * {@link MobScalingListener} on spawn. Untagged mobs are level 1.
     *
     * @param mob the attacking mob, or null
     * @return the mob's level (>= 1)
     */
    private int resolveMobLevel(@javax.annotation.Nullable LivingEntity mob) {
        if (mob == null) return 1;
        MobRegistry registry = plugin.getMobRegistry();
        return registry != null ? registry.getLevel(mob) : 1;
    }

    /**
     * Route player→mob damage through the mob's virtual-HP pool (set on spawn by
     * MobScalingListener). The real (possibly billions) HP lives in the pool; the vanilla
     * health bar is driven cosmetically so it renders the remaining fraction. Returns the
     * vanilla damage to apply: enough to move the bar to the new fraction, or lethal when the
     * pool is depleted. Untracked mobs take their vanilla damage unchanged.
     */
    private double applyDamageMultiplierToEntity(Entity entity, double damage) {
        if (!(entity instanceof LivingEntity mob)) return damage;
        Manager.MobVirtualHealthManager mvh = plugin.getMobVirtualHealthManager();
        if (mvh == null || !mvh.has(mob)) return damage;

        boolean lethal = mvh.damage(mob, damage);
        var attr = mob.getAttribute(Attribute.MAX_HEALTH);
        double cosmeticMax = attr != null ? attr.getValue() : 20.0;
        double target = lethal ? 0.0 : Math.max(1.0, cosmeticMax * mvh.fraction(mob));
        // vanilla damage needed to drive the rendered health bar to `target`
        return Math.max(0.0, mob.getHealth() - target);
    }

    private void handleProjectileHit(Projectile projectile, Entity target, EntityDamageEvent event) {
        double dmg = event.getDamage();
        Entity shooter = projectile.getShooter() instanceof Entity e ? e : null;

        if (target instanceof Player player) {
            Player attackerPlayer = (shooter instanceof Player p) ? p : null;
            CombatContext ctx = new CombatContext(attackerPlayer, player, CustomDamageType.PROJECTILE, dmg);

            if (combatSkillProcessor != null && attackerPlayer != null) {
                combatSkillProcessor.processAttackerSkills(ctx);
            }
            if (combatSkillProcessor != null) {
                combatSkillProcessor.processDefenderSkills(ctx);
            }

            double modifiedDmg = ctx.attack().getModifiedDamage();

            // Resolve armor reduction with shooter's mob tier penetration
            double armorReduction = virtualHealthManager.getArmorReduction(player);
            if (shooter instanceof LivingEntity livingShooter && !(shooter instanceof Player)) {
                MobRegistry mobRegistry = plugin.getMobRegistry();
                if (mobRegistry != null) {
                    MobContext mobCtx = mobRegistry.resolve(livingShooter);
                    double penetration = mobCtx.tier().armorPenetration;
                    armorReduction *= (1.0 - penetration);
                }
            }

            DamageResolution resolution = virtualHealthManager.applyResolvedDamage(player, modifiedDmg, armorReduction,
                    CustomDamageType.PROJECTILE, shooter, ctx);

            if (combatSkillProcessor != null && attackerPlayer != null) {
                combatSkillProcessor.applyPostDamageEffects(ctx);
            }

            Bukkit.getPluginManager().callEvent(new CerbDamageResultEvent(ctx));

            if (resolution.lethal()) {
                event.setDamage(virtualHealthManager.getGuaranteedVanillaDeathDamage(player));
                virtualHealthManager.schedulePendingDeathFinalizer(player);
            } else {
                // Set damage to 0 (NOT cancelled) so vanilla projectile knockback fires
                event.setDamage(0);
                player.setHealth(player.getAttribute(Attribute.MAX_HEALTH).getValue());
            }
        } else {
            // Non-player entities: let vanilla handle damage + knockback entirely
            // Do NOT call leb.damage() — that causes recursive event loops
            double modified = applyDamageMultiplierToEntity(target, dmg);
            event.setDamage(modified);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEntityRegainHealth(EntityRegainHealthEvent event) {
        if (event.getEntity() instanceof Player) {
            event.setCancelled(true);
        }
    }

    /**
     * Send combat debug trace to any involved players that have debug enabled.
     */
    private void sendCombatDebugTrace(CombatContext ctx) {

        String trace = ctx.getTraceAsString();

        Player attacker = ctx.getAttacker();
        if (attacker != null && plugin.isCombatDebugEnabled(attacker.getUniqueId())) {
            attacker.sendMessage(trace);
        }

        if (ctx.getTarget() instanceof Player target) {
            if (plugin.isCombatDebugEnabled(target.getUniqueId())
                    && (attacker == null || !target.getUniqueId().equals(attacker.getUniqueId()))) {
                target.sendMessage(trace);
            }
        }
    }

}
