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

    /**
     * A hit nobody is allowed to land, cancelled before anything else looks at it: a called sea creature is its owner's fight
     * alone (Gathering.Fishing.guard). Set once at startup.
     */
    private volatile @Nullable java.util.function.Predicate<EntityDamageByEntityEvent> damageGuard;

    public void setDamageGuard(java.util.function.Predicate<EntityDamageByEntityEvent> guard) { this.damageGuard = guard; }

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
    /**
     * The last hit on a player this thread resolved into real damage: not skipped (invulnerability, dying, respawning), not
     * countered, not lethal. Set at LOWEST; a MONITOR listener that sees the same event knows nothing cancelled it since.
     * Held weakly: it never keeps an old event (and the entities it names) alive.
     */
    private static final ThreadLocal<java.lang.ref.WeakReference<EntityDamageEvent>> LANDED = new ThreadLocal<>();

    /** True when {@code event} is a hit on a player that really landed (see {@link #LANDED}). Same thread, same event. */
    public static boolean landed(EntityDamageEvent event) {
        java.lang.ref.WeakReference<EntityDamageEvent> r = LANDED.get();
        return event != null && r != null && r.get() == event;
    }

    /**
     * True when a raised shield takes the whole of {@code event}'s hit. The server decides a block by its own rules (the
     * shield up long enough, the blow from in front) and reports it as the event's blocking share, the hit's full size for a
     * shield. A hit this listener resolved no longer shows one: it sets the hit to 0.
     */
    @SuppressWarnings("deprecation") // the damage modifiers are deprecated, yet they are the server's only report of a block
    public static boolean shieldTookAll(EntityDamageEvent event) {
        return event.isApplicable(EntityDamageEvent.DamageModifier.BLOCKING)
                && wholeBlocked(event.getDamage(), event.getDamage(EntityDamageEvent.DamageModifier.BLOCKING));
    }

    /** The last player's hit on a player this thread saw a raised shield take whole: the pipeline still resolves it (PvP as before). */
    private static final ThreadLocal<java.lang.ref.WeakReference<EntityDamageEvent>> SHIELDED = new ThreadLocal<>();

    /**
     * True when a raised shield took the whole of {@code event}'s hit, for a listener after this one: a creature's hit still
     * shows its blocking share; a player's, which this listener resolves as before (and so sets to 0), is remembered.
     */
    public static boolean blockedByShield(EntityDamageEvent event) {
        if (shieldTookAll(event)) return true;
        java.lang.ref.WeakReference<EntityDamageEvent> r = SHIELDED.get();
        return event != null && r != null && r.get() == event;
    }

    /** Pure: a hit of {@code base} whose blocking share is {@code blocking} (0 or less) is blocked whole. */
    static boolean wholeBlocked(double base, double blocking) {
        return base > 0 && -blocking >= base - 1e-9;
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onEntityDamage(EntityDamageEvent event) {
        if (event.getDamage() <= 0) return;
        if (!(event.getEntity() instanceof LivingEntity)) return;
        var guard = damageGuard;
        if (guard != null && event instanceof EntityDamageByEntityEvent ede && guard.test(ede)) {
            event.setCancelled(true);
            return;
        }
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
            // A raised shield took the whole hit of a creature (its blow, arrow, fangs or blast): the pool takes nothing, and
            // the game's own blocking runs after this event (the block sound, the shield's wear).
            // A player's hit stays as it was: PvP plays as before (docs/ROADMAP.md → PvP).
            if (shieldTookAll(event)) {
                if (!causedByAnotherPlayer(event, event instanceof EntityDamageByEntityEvent by ? by.getDamager() : null, p)) return;
                SHIELDED.set(new java.lang.ref.WeakReference<>(event)); // a player's blow: resolved as before, answered by nothing
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

                // Resolve mob context once — used for both damage scaling and armor penetration. Evoker fangs are
                // their evoker's ranged attack (owner decision 10).
                MobContext mobCtx = null;
                LivingEntity fangsOwner = damager instanceof org.bukkit.entity.EvokerFangs fangs
                        && fangs.getOwner() != null && !(fangs.getOwner() instanceof Player) ? fangs.getOwner() : null;
                if ((damager instanceof LivingEntity && !(damager instanceof Player)) || fangsOwner != null) {
                    MobRegistry mobRegistry = plugin.getMobRegistry();
                    if (mobRegistry != null) {
                        mobCtx = mobRegistry.resolve(fangsOwner != null ? fangsOwner : (LivingEntity) damager);
                    }
                }

                // Mob damage scaling — tier + level based multiplier
                if (mobCtx != null && fangsOwner != null) {
                    modifiedDmg = scaleRanged(ctx, player, mobCtx, fangsOwner, modifiedDmg);
                } else if (mobCtx != null) {
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
                if (!resolution.lethal()) LANDED.set(new java.lang.ref.WeakReference<>(event)); // Iron Reprisal reads it at MONITOR

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
    /**
     * A mob's ranged hit (a shot, a thrown potion, evoker fangs) scaled like its melee hits, tier × level, within
     * the ranged caps (combat.yml mob-damage-scaling.ranged); traced as MobScaling. Player thread.
     */
    private double scaleRanged(CombatContext ctx, Player target, MobContext mobCtx, LivingEntity mob, double dmg) {
        CombatConfig cc = plugin.getCombatConfig();
        if (cc == null || !(dmg > 0)) return dmg;
        int level = resolveMobLevel(mob);
        double mult = cc.mobDamageScaling.calculateRanged(mobCtx.tier(), level);
        if (mult == 1.0) return dmg;
        double scaled = cc.mobDamageScaling.capRangedHit(dmg, dmg * mult, virtualHealthManager.getPlayerMaxVirtualHealth(target));
        ctx.attack().setModifiedDamage(scaled);
        ctx.appendTrace("MobScaling", dmg, scaled, mobCtx.tier().name() + " Lv" + level + " ranged x" + String.format("%.2f", mult)
                + (scaled < dmg * mult ? String.format(" (hit capped: %.1f, vanilla %.1f)", dmg * mult, dmg) : ""));
        return scaled;
    }

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

    /**
     * The weapon behind a shot, as skills.yml names it: a thrown trident, an arrow's bow or crossbow, a crossbow's
     * firework rocket (one shot at an angle; a rocket used on a block or for an elytra boost flies straight up). Null
     * for anything else (a snowball, a potion). Kill credit uses it, so a kill after a switch to a sword stays the shot's.
     */
    static String firedBy(Projectile projectile) {
        if (projectile instanceof org.bukkit.entity.Trident) return "TRIDENT";
        if (projectile instanceof org.bukkit.entity.AbstractArrow arrow) {
            org.bukkit.inventory.ItemStack w = arrow.getWeapon();
            return arrowWeapon(w == null ? null : w.getType());
        }
        if (projectile instanceof org.bukkit.entity.Firework rocket && rocket.isShotAtAngle()) return "CROSSBOW";
        return null;
    }

    /** An arrow's weapon: the crossbow that fired it, else a bow (an arrow no weapon fired, a command's, counts as one). */
    static String arrowWeapon(org.bukkit.Material weapon) {
        return weapon == org.bukkit.Material.CROSSBOW ? "CROSSBOW" : "BOW";
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

            // Resolve armor reduction with shooter's mob tier penetration; a mob's shot scales with its tier and
            // level like its melee hits (owner decision 10), within the ranged caps. A player's own llama is PvP.
            double armorReduction = virtualHealthManager.getArmorReduction(player);
            if (shooter instanceof LivingEntity livingShooter && !(shooter instanceof Player)) {
                MobRegistry mobRegistry = plugin.getMobRegistry();
                if (mobRegistry != null) {
                    MobContext mobCtx = mobRegistry.resolve(livingShooter);
                    double penetration = mobCtx.tier().armorPenetration;
                    armorReduction *= (1.0 - penetration);
                    if (Manager.PvpTag.playerBehind(livingShooter) == null) modifiedDmg = scaleRanged(ctx, player, mobCtx, livingShooter, modifiedDmg);
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
            sendCombatDebugTrace(ctx); // shots taken show their steps too (/cerb combatdebug), as melee hits do

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
            // The kill is the weapon's that fired the shot, whatever is in hand when it lands.
            Manager.XpIntegrity xi = xpIntegrity;
            if (xi != null && shooter instanceof Player p && target instanceof org.bukkit.entity.Mob mob && hit.dealt() > 0) {
                String firedBy = firedBy(projectile);
                if (firedBy != null) xi.creditHit(p, mob, xi.skillOf(firedBy));
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
