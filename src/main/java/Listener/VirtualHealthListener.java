package Listener;

import cerberus.world.cerb.CerberusPlugin;
import cerberus.world.cerb.CustomPlayer;
import Manager.PlayerVirtualHealthManager;
import Manager.DamageTypeMapper;
import Manager.CustomDamageType;
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

    // ------------------------------------------------------------
    // Main damage handler
    // ------------------------------------------------------------
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onEntityDamage(EntityDamageEvent event) {
        if (event.getDamage() <= 0) return;
        if (!(event.getEntity() instanceof LivingEntity)) return;
        if (event.getEntity() instanceof Player p && (p.isDead() || p.getHealth() <= 0)) {
            event.setCancelled(true);
            return;
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

                // Resolve armor reduction with mob armor penetration
                double armorReduction = virtualHealthManager.getArmorReduction(player);
                if (damager instanceof LivingEntity livingDamager && !(damager instanceof Player)) {
                    MobRegistry mobRegistry = plugin.getMobRegistry();
                    if (mobRegistry != null) {
                        MobContext mobCtx = mobRegistry.resolve(livingDamager);
                        double penetration = mobCtx.tier().armorPenetration;
                        armorReduction *= (1.0 - penetration);
                    }
                }

                virtualHealthManager.applyDamage(player, modifiedDmg, armorReduction,
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

                if (virtualHealthManager.isVirtualHealthDepleted(player) || player.isDead()) {
                    event.setCancelled(true);
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

    private double applyDamageMultiplierToEntity(Entity entity, double damage) {
        return damage;
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

            virtualHealthManager.applyDamage(player, modifiedDmg, armorReduction,
                    CustomDamageType.PROJECTILE, shooter, ctx);

            if (combatSkillProcessor != null && attackerPlayer != null) {
                combatSkillProcessor.applyPostDamageEffects(ctx);
            }

            Bukkit.getPluginManager().callEvent(new CerbDamageResultEvent(ctx));

            // Set damage to 0 (NOT cancelled) so vanilla projectile knockback fires
            event.setDamage(0);
            if (!virtualHealthManager.isVirtualHealthDepleted(player) && !player.isDead()) {
                player.setHealth(player.getAttribute(Attribute.MAX_HEALTH).getValue());
            } else {
                event.setCancelled(true);
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
