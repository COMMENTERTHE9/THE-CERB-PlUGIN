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
import Manager.Tier;
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
    // Re-entrancy guard. MUST be per-thread: on Folia, damage events fire concurrently on
    // many region threads against this one listener instance — a plain shared boolean made
    // one region's event silently bypass the whole damage pipeline while another was inside it.
    private final ThreadLocal<Boolean> isHandlingEvent = ThreadLocal.withInitial(() -> Boolean.FALSE);

    // Injected after construction (setter pattern)
    private @Nullable CombatSkillProcessor combatSkillProcessor;
    private @Nullable Manager.PveOffense pveOffense;

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

    /** Attribute-driven player -> mob offense (Power, crit, lifesteal). */
    public void setPveOffense(Manager.PveOffense pveOffense) {
        this.pveOffense = pveOffense;
    }

    /**
     * Asked when a mob's melee hit is about to land on a player: true = the hit is negated (a class
     * counter such as Riposte answered it), and the event is cancelled before anything else happens.
     */
    private volatile @Nullable java.util.function.BiPredicate<Player, LivingEntity> counterHook;

    public void setCounterHook(java.util.function.BiPredicate<Player, LivingEntity> counterHook) {
        this.counterHook = counterHook;
    }

    /**
     * Register a spell damage context before the spell deals damage.
     * The entry is consumed (removed) when the damage event fires.
     * Entity IDs get reused — always clean immediately after reading.
     */
    private volatile Manager.XpIntegrity xpIntegrity;

    /** The XP rules hits feed: kill credit by the attack, magic XP on a spell hit (Manager.XpIntegrity). */
    public void setXpIntegrity(Manager.XpIntegrity xpIntegrity) { this.xpIntegrity = xpIntegrity; }

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
        if (isHandlingEvent.get()) return;

        isHandlingEvent.set(Boolean.TRUE);
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
                var counter = counterHook;
                if (counter != null && spellCtx == null && damager instanceof LivingEntity mobAttacker && !(damager instanceof Player)
                        && event.getCause() == EntityDamageEvent.DamageCause.ENTITY_ATTACK && counter.test(player, mobAttacker)) {
                    event.setCancelled(true); // countered: no damage, no knockback, no defensive XP
                    return;
                }
                CombatContext ctx = new CombatContext(attackerPlayer, entity, damageType, dmg);
                if (!ctx.isPvp() && causedByAnotherPlayer(event, damager, player)) ctx.markPvp();

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

                if (resolution.skipped()) {
                    // Ignored hit (invulnerability window / dying / respawning): no XP, no
                    // damage display, no debug trace. Fire/lava/cactus send one event per tick.
                    event.setDamage(0);
                    player.setHealth(player.getAttribute(Attribute.MAX_HEALTH).getValue());
                    return;
                }

                if (combatSkillProcessor != null && attackerPlayer != null) {
                    combatSkillProcessor.applyPostDamageEffects(ctx);
                }

                // Defender XP: award for taking damage (HAT, Survival). PvP as before attributes
                // scales the hit to the defender's health; XP counts it in the old terms.
                double damageTaken = ctx.defense().getFinalDamage() / ctx.defense().getHealthScale();
                if (damageTaken <= 0) damageTaken = modifiedDmg * 0.1; // minimum XP even if fully mitigated
                if (combatSkillProcessor != null) {
                    combatSkillProcessor.awardDefenderHitXp(player, damageTaken, damager);
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
                boolean weaponHit = attackerPlayer != null && spellCtx == null && entity instanceof org.bukkit.entity.Mob
                        && (event.getCause() == EntityDamageEvent.DamageCause.ENTITY_ATTACK
                            || event.getCause() == EntityDamageEvent.DamageCause.ENTITY_SWEEP_ATTACK);
                if (weaponHit && pveOffense != null && pveOffense.enabled()) {
                    // Attributes scale the hit before it drains the pool (the Blade Mastery fix).
                    CombatContext ctx = pveOffense.meleeHit(attackerPlayer, entity, damageType, dmg,
                            Manager.PveOffense.meleeWeapon(attackerPlayer), plugin.isCombatDebugEnabled(attackerPlayer.getUniqueId()));
                    PoolHit hit = hitPool(entity, ctx.attack().getModifiedDamage());
                    event.setDamage(hit.vanillaDamage());
                    afterPveHit(ctx, attackerPlayer, hit.dealt(), true);
                    Manager.XpIntegrity xi = xpIntegrity;
                    if (xi != null) xi.creditHit(attackerPlayer, entity, xi.skillOfHeld(attackerPlayer)); // a kill soon after trains this weapon's skill
                } else {
                    if (spellCtx != null && attackerPlayer != null && pveOffense != null) {
                        double before = dmg;
                        dmg = pveOffense.spellDamage(attackerPlayer, dmg); // Essence scales spells against mobs
                        if (plugin.isCombatDebugEnabled(attackerPlayer.getUniqueId())) {
                            attackerPlayer.sendMessage(String.format(java.util.Locale.ROOT,
                                    "[Combat] Spell(%s, realm and Essence x%.2f): %.1f -> %.1f", damageType.name(), dmg / before, before, dmg));
                        }
                    }
                    PoolHit hit = hitPool(entity, dmg);
                    event.setDamage(hit.vanillaDamage());

                    // A kill soon after a spell's hit is the spell's (its cast paid the magic skills: XpIntegrity.spellCast);
                    // any other hit by a player is credited to what they hold.
                    Manager.XpIntegrity xi = xpIntegrity;
                    if (xi != null && attackerPlayer != null && hit.dealt() > 0) {
                        xi.creditHit(attackerPlayer, entity, spellCtx != null ? Manager.XpIntegrity.SPELL_KILL_SKILL : xi.skillOfHeld(attackerPlayer));
                    }

                    // Award attacker per-hit XP when a PLAYER hits a MOB
                    if (attackerPlayer != null && combatSkillProcessor != null && hit.vanillaDamage() > 0) {
                        CombatContext ctx = new CombatContext(attackerPlayer, entity, damageType, dmg);
                        combatSkillProcessor.processAttackerSkills(ctx); // for crit detection
                        combatSkillProcessor.awardAttackerHitXp(ctx, attackerPlayer);
                    }
                }
            }
        } finally {
            isHandlingEvent.set(Boolean.FALSE);
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
    /** What a hit did: the vanilla damage to put on the event, and the HP actually taken from the pool. */
    private record PoolHit(double vanillaDamage, double dealt) {}

    /** A mob without a pool takes vanilla damage; it can't lose more than the health it has (caps lifesteal). */
    private static PoolHit unpooled(LivingEntity mob, double damage) {
        return new PoolHit(damage, Math.max(0.0, Math.min(damage, mob.getHealth() + mob.getAbsorptionAmount())));
    }

    private PoolHit hitPool(Entity entity, double damage) {
        if (!(entity instanceof LivingEntity mob)) return new PoolHit(damage, damage);
        Manager.MobVirtualHealthManager mvh = plugin.getMobVirtualHealthManager();
        if (mvh == null) return unpooled(mob, damage);

        // Defense-in-depth: a mob that carries a level tag but lost its transient pool
        // (restart, hive exit, any missed lifecycle path) gets the pool lazily rebuilt
        // here instead of silently taking raw vanilla damage on the cosmetic bar.
        if (!mvh.has(mob)) {
            MobRegistry registry = plugin.getMobRegistry();
            CombatConfig cc = plugin.getCombatConfig();
            if (registry == null || cc == null || !registry.hasLevel(mob)) return unpooled(mob, damage);
            int level = registry.getLevel(mob);
            Tier tier = registry.resolve(mob).tier();
            mvh.init(mob, cc.mobHealthScaling.calculate(tier, level));
        }

        double dealt = Math.min(Math.max(0.0, damage), mvh.getCurrent(mob));
        boolean lethal = mvh.damage(mob, damage);
        var labels = plugin.getMobLabelManager();
        if (labels != null) labels.refresh(mob, mvh.fraction(mob)); // live HP on the floating label

        if (lethal) {
            // Guarantee the kill. Vanilla armor/enchant/potion reductions apply to event damage
            // AFTER this handler, so returning exactly current-health can leave an armored mob
            // alive with the pool at 0. Massive overkill survives any percentage reduction, and
            // killing through the event preserves killer attribution (loot, XP, death message).
            return new PoolHit(Math.max(1.0e9, mob.getHealth() * 100.0), dealt);
        }

        var attr = mob.getAttribute(Attribute.MAX_HEALTH);
        double cosmeticMax = attr != null ? attr.getValue() : 20.0;
        double target = Math.max(1.0, cosmeticMax * mvh.fraction(mob));
        // vanilla damage needed to drive the rendered health bar to `target`
        return new PoolHit(Math.max(0.0, mob.getHealth() - target), dealt);
    }

    /**
     * After an attribute-scaled hit on a mob: lifesteal, per-hit XP, the damage number, and the
     * debug trace. Lifesteal heals on the attacker's own thread (an arrow's shooter may be far away).
     */
    private void afterPveHit(CombatContext ctx, Player attacker, double dealt, boolean hitXp) {
        pveOffense.dealt(ctx, dealt, plugin.isCombatDebugEnabled(attacker.getUniqueId()));
        double heal = ctx.postDamage().getLifestealAmount();
        if (heal > 0) {
            Runnable lifesteal = () -> {
                // A late arrow must not heal (and so revive) a shooter who died meanwhile.
                if (attacker.isDead() || virtualHealthManager.isRespawning(attacker)
                        || virtualHealthManager.hasPendingCustomDeath(attacker)) return;
                virtualHealthManager.heal(attacker, heal);
            };
            if (Bukkit.isOwnedByCurrentRegion(attacker)) lifesteal.run();
            else attacker.getScheduler().run(plugin, t -> lifesteal.run(), null);
        }
        if (hitXp && combatSkillProcessor != null && dealt > 0) combatSkillProcessor.awardAttackerHitXp(ctx, attacker);
        CombatConfig cc = plugin.getCombatConfig();
        if (cc == null || cc.displayMobDamageNumbers) Bukkit.getPluginManager().callEvent(new CerbDamageResultEvent(ctx));
        sendCombatDebugTrace(ctx);
    }

    /**
     * An arrow shot with extra draw speed flew faster and hits harder; against a player that bonus
     * is taken back (unless attributes.yml secondary.in-pvp is true).
     */
    private double vanillaDrawDamage(Projectile projectile, double dmg) {
        var sec = plugin.getSecondaryEffects();
        if (sec == null || plugin.getAttributeService().rules().secondary().inPvp()) return dmg;
        Double scale = projectile.getPersistentDataContainer().get(sec.drawScaleKey(), org.bukkit.persistence.PersistentDataType.DOUBLE);
        return scale != null && scale > 1.0 && Double.isFinite(scale) ? dmg / scale : dmg;
    }

    private void handleProjectileHit(Projectile projectile, Entity target, EntityDamageEvent event) {
        double dmg = event.getDamage();
        Entity shooter = projectile.getShooter() instanceof Entity e ? e : null;

        if (target instanceof Player player) {
            Player attackerPlayer = (shooter instanceof Player p) ? p : null;
            if (attackerPlayer != null) dmg = vanillaDrawDamage(projectile, dmg); // PvP stays vanilla unless configured
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

            if (resolution.skipped()) {
                event.setDamage(0); // ignored hit: no display, no post-damage effects
                player.setHealth(player.getAttribute(Attribute.MAX_HEALTH).getValue());
                return;
            }

            if (combatSkillProcessor != null && attackerPlayer != null) {
                combatSkillProcessor.applyPostDamageEffects(ctx);
            }

            // Defender XP from projectiles (owner decision D), like melee hits, but only for a real
            // defensive encounter: shot by a mob or another player (not yourself, not a dispenser),
            // a hit that landed (an ignored hit returned above) and did damage.
            if (combatSkillProcessor != null && shooter instanceof LivingEntity && !shooter.getUniqueId().equals(player.getUniqueId())
                    && modifiedDmg > 0) {
                double damageTaken = ctx.defense().getFinalDamage() / ctx.defense().getHealthScale();
                if (damageTaken <= 0) damageTaken = modifiedDmg * 0.1; // minimum XP even if fully mitigated (as melee)
                combatSkillProcessor.awardDefenderHitXp(player, damageTaken, shooter);
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
            Attributes.WeaponClass weapon = projectile instanceof org.bukkit.entity.Trident ? Attributes.WeaponClass.TRIDENT
                    : projectile instanceof org.bukkit.entity.AbstractArrow ? Attributes.WeaponClass.BOW : null;
            PoolHit hit;
            if (shooter instanceof Player p && weapon != null && target instanceof org.bukkit.entity.Mob mob
                    && pveOffense != null && pveOffense.enabled()) {
                CombatContext ctx = pveOffense.hit(p, mob, CustomDamageType.PROJECTILE, dmg, weapon,
                        plugin.isCombatDebugEnabled(p.getUniqueId()));
                hit = hitPool(target, ctx.attack().getModifiedDamage());
                event.setDamage(hit.vanillaDamage());
                afterPveHit(ctx, p, hit.dealt(), false); // no per-hit Critical Strike XP for arrows (unchanged)
            } else {
                hit = hitPool(target, dmg);
                event.setDamage(hit.vanillaDamage());
            }
            // The kill is the bow's or the trident's, whatever is in hand when it lands.
            Manager.XpIntegrity xi = xpIntegrity;
            if (xi != null && shooter instanceof Player p && weapon != null && target instanceof org.bukkit.entity.Mob mob && hit.dealt() > 0) {
                xi.creditHit(p, mob, xi.skillOf(weapon == Attributes.WeaponClass.TRIDENT ? "TRIDENT" : "BOW"));
            }
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
    /** Burning, poison and wither ticks carry no attacker; during a PvP fight they count as PvP. */
    private static final java.util.Set<EntityDamageEvent.DamageCause> DAMAGE_OVER_TIME = java.util.EnumSet.of(
            EntityDamageEvent.DamageCause.FIRE_TICK, EntityDamageEvent.DamageCause.POISON, EntityDamageEvent.DamageCause.WITHER);

    /**
     * Damage another player caused without hitting directly: their pet, potion cloud, TNT or
     * evoker fangs, or a burn / poison / wither tick while the victim is PvP-tagged (a Fire
     * Aspect or Fireball burn, a poison arrow). PvP plays as before attributes for these too;
     * the attacker's own skill bonuses still don't apply (as before).
     */
    private boolean causedByAnotherPlayer(EntityDamageEvent event, @Nullable Entity damager, Player victim) {
        Player behind = damager != null ? Manager.PvpTag.playerBehind(damager) : null;
        if (behind != null) return !behind.getUniqueId().equals(victim.getUniqueId());
        Manager.PvpTag tag = plugin.getPvpTag();
        return tag != null && DAMAGE_OVER_TIME.contains(event.getCause()) && tag.isTagged(victim.getUniqueId());
    }

    private void sendCombatDebugTrace(CombatContext ctx) {
        Player attacker = ctx.getAttacker();
        boolean toAttacker = attacker != null && plugin.isCombatDebugEnabled(attacker.getUniqueId());
        boolean toTarget = ctx.getTarget() instanceof Player target
                && plugin.isCombatDebugEnabled(target.getUniqueId())
                && (attacker == null || !target.getUniqueId().equals(attacker.getUniqueId()));
        if (!toAttacker && !toTarget) return; // the common case: build no string at all

        String trace = ctx.getTraceAsString();
        if (toAttacker) attacker.sendMessage(trace);
        if (toTarget) ctx.getTarget().sendMessage(trace);
    }

}
