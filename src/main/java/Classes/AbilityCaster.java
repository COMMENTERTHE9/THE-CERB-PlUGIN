package Classes;

import Listener.VirtualHealthListener;
import Manager.CustomDamageType;
import Manager.ManaType;
import Manager.PlayerManaManager;
import Manager.SpellDamageContext;
import Spells.SpellManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Registry;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Tameable;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.function.Supplier;

/**
 * Casts abilities (docs/design/CLASSES.md §5.3): validates everything on the server, then performs
 * the ability's verbs through the existing systems. Weapon strikes go through the normal player hit
 * path (Power, crits, the mob HP pool, damage numbers); spells through the spell path (Essence);
 * costs come from the Essence pool. The client only sends a key press.
 *
 * <p>Folia: casts run on the caster's region thread; targets are only touched when that thread
 * owns them (nearby targets always are); blinks use {@code teleportAsync}; every delayed step runs
 * on the caster's entity scheduler and checks the caster is still there.
 *
 * <p>Abilities affect mobs, never players, for now: PvP is off until the PvP pass turns it on (not
 * permanent; docs/ROADMAP.md -> PvP lists every switch).
 */
public final class AbilityCaster {

    /** A basic spell hit before Essence scaling (an iron sword's 6). */
    static final double SPELL_BASE = 6.0;
    /** Momentum stacks fade this long after the last gain. */
    static final long MOMENTUM_DECAY_MS = 6_000;

    /** Set while an ability's own strike is dealt, so it is never read as a new key press. */
    static final ThreadLocal<Boolean> IN_ABILITY = ThreadLocal.withInitial(() -> Boolean.FALSE);

    private final JavaPlugin plugin;
    private final ClassService service;
    private final PlayerManaManager mana;
    private final Supplier<SpellManager> spells;
    private final Supplier<VirtualHealthListener> health;
    /** False while the player can't act (respawning, a death being finished...). */
    private volatile java.util.function.Predicate<Player> canAct = p -> true;
    /** An Essence ability's cooldown after the Spell cooldown stat (Spell Weaving), as for spell items. */
    private volatile java.util.function.ToLongBiFunction<java.util.UUID, Long> spellCooldown = (id, ms) -> ms;

    public AbilityCaster(JavaPlugin plugin, ClassService service, PlayerManaManager mana,
                         Supplier<SpellManager> spells, Supplier<VirtualHealthListener> health) {
        this.plugin = plugin;
        this.service = service;
        this.mana = mana;
        this.spells = spells;
        this.health = health;
    }

    public void setCanAct(java.util.function.Predicate<Player> canAct) { this.canAct = canAct; }

    public void setSpellCooldown(java.util.function.ToLongBiFunction<java.util.UUID, Long> f) { this.spellCooldown = f; }

    /** The ability in active slot {@code slot} of the player's loadout, or null (none, or not loaded). */
    public AbilityDef equippedActive(Player p, int slot) {
        ClassProfile prof = service.profile(p.getUniqueId());
        if (prof == null || slot < 0 || slot >= Loadout.ACTIVE_SLOTS) return null;
        AbilityDef a = service.catalog().ability(prof.activeLoadout().actives().get(slot));
        return a != null && a.isActive() ? a : null;
    }

    /** True while the current damage event comes from an ability's own strike (any thread: per-thread flag). */
    public static boolean inAbility() { return IN_ABILITY.get(); }

    /**
     * Cast active slot {@code slot} (0-4) of the player's loadout. Player's thread. Every check is
     * made here, then cost and cooldown are committed together and the verbs run. Returns true if
     * it went off.
     */
    public boolean cast(Player p, int slot) {
        return cast(p, slot, WeaponFamily.ofMaterial(p.getInventory().getItemInMainHand().getType().name()));
    }

    /**
     * Cast with {@code hand} as the family in hand (the drop key has already taken the item out of
     * the hand when the event fires, so the caller passes the dropped item's family).
     */
    public boolean cast(Player p, int slot, WeaponFamily hand) {
        ClassProfile prof = service.profile(p.getUniqueId());
        AbilityDef a = equippedActive(p, slot);
        if (prof == null || a == null) return false;
        long now = System.currentTimeMillis();
        if (!prof.unlocked(a)) return fail(p, a.displayName() + " is not unlocked yet");
        if (now - prof.lastSwitchAt < ClassService.CAST_LOCK_AFTER_SWITCH_MS) return fail(p, "Switching loadouts...");
        if (p.isDead() || !p.isValid() || !canAct.test(p)) return false;
        if (!WeaponFamily.fits(a.weapons(), hand)) return fail(p, a.displayName() + " needs " + needs(a));
        for (EffectSpec e : a.effects()) {
            if (e instanceof EffectSpec.Spell s && !spells.get().canCastForAbility(s.name())) {
                return fail(p, a.displayName() + " is not available (no spell " + s.name() + ")"); // a config error: nothing is spent
            }
        }
        long left = prof.cooldowns().remaining(a, now);
        if (left > 0) return fail(p, a.displayName() + ": " + seconds(left));
        if (a.essenceCost() > 0 && !mana.spendMana(p, ManaType.BASIC, a.essenceCost())) {
            return fail(p, a.displayName() + ": not enough Essence (" + Math.round(a.essenceCost()) + ")");
        }
        long cooldown = a.essenceCost() > 0
                ? Math.max(Math.min(a.cooldownMs(), ClassCatalog.MIN_COOLDOWN_MS), spellCooldown.applyAsLong(p.getUniqueId(), a.cooldownMs()))
                : a.cooldownMs();
        prof.cooldowns().commit(a, cooldown, service.catalog().groupMs(), now);
        prof.lastUse.put(a.classId(), now);
        casts.incrementAndGet();
        p.sendActionBar(Component.text("✦ " + a.displayName(), NamedTextColor.GOLD));
        sound(p, a);
        run(p, prof, a, 0, null, false);
        return true;
    }

    /** "2.1 s" for 2,050 ms (rounded to tenths). Pressing a key on cooldown is common: no Formatter for it. */
    static String seconds(long ms) {
        long tenths = (Math.max(0, ms) + 50) / 100;
        return tenths / 10 + "." + tenths % 10 + " s";
    }

    /** "a blade or an axe": what an ability needs in hand, for messages. */
    static String needs(AbilityDef a) {
        List<String> words = new ArrayList<>();
        for (WeaponFamily f : a.weapons()) words.add(f.withArticle());
        if (words.size() <= 1) return words.isEmpty() ? "nothing" : words.get(0);
        return String.join(", ", words.subList(0, words.size() - 1)) + " or " + words.get(words.size() - 1);
    }

    private final java.util.concurrent.atomic.AtomicLong casts = new java.util.concurrent.atomic.AtomicLong();
    private final java.util.concurrent.atomic.AtomicLong counters = new java.util.concurrent.atomic.AtomicLong();

    /** Casts that went off since start (diagnostics). */
    public long castCount() { return casts.get(); }

    /** Successful Ripostes since start (diagnostics). */
    public long counterCount() { return counters.get(); }

    /**
     * A mob's melee hit is about to land on {@code p}: if a Riposte window is open, the hit is
     * negated (the caller cancels it) and answered. Player's thread (the damaged entity's).
     */
    public boolean tryCounter(Player p, LivingEntity attacker) {
        ClassService.CounterWindow w = service.takeCounter(p.getUniqueId());
        if (w == null) return false;
        ClassProfile prof = service.profile(p.getUniqueId());
        AbilityDef a = service.catalog().ability(w.abilityId());
        if (prof == null || a == null || !prof.activeLoadout().contains(a.id())) return false;
        long now = System.currentTimeMillis();
        prof.cooldowns().shorten(a, w.successCooldownMs(), now);
        prof.lastUse.put(a.classId(), now);
        counters.incrementAndGet();
        counterStrike(p, attacker, w.coefficient());
        p.sendActionBar(Component.text("✦ " + a.displayName() + "!", NamedTextColor.GOLD));
        service.awardXp(p, a.classId(), ClassService.CAST_XP);
        return true;
    }

    /**
     * Perform the verbs from index {@code from}. A dash pauses the list until it lands, then the rest
     * runs (and the dash's stun lands on the first enemy in front). When the list is done, the cast
     * earns class XP if it reached a hostile mob, or if it was a self cast (buff, blink, Feint...)
     * made during a fight: casting into the air out of combat, or at farm animals, earns nothing
     * (CLASSES.md §2.2). A Riposte earns its XP when it actually counters.
     */
    private void run(Player p, ClassProfile prof, AbilityDef a, int from, EffectSpec.Dash landed, boolean reached) {
        if (landed != null && landed.stunSeconds() > 0) reached |= stunFirst(p, landed) instanceof org.bukkit.entity.Enemy;
        List<EffectSpec> fx = a.effects();
        boolean self = false;
        int hit = 0;
        for (int i = from; i < fx.size(); i++) {
            EffectSpec e = fx.get(i);
            switch (e) {
                case EffectSpec.Dash d -> {
                    dash(p, d);
                    int next = i + 1;
                    boolean soFar = reached;
                    p.getScheduler().runDelayed(plugin, t -> {
                        if (p.isValid() && !p.isDead()) run(p, prof, a, next, d, soFar);
                    }, null, 7L);
                    return;
                }
                case EffectSpec.Damage d -> { List<LivingEntity> hits = damage(p, prof, d); hit = hits.size(); reached |= anyHostile(hits); }
                case EffectSpec.Blink b -> self |= blink(p, b);
                case EffectSpec.Status s -> reached |= anyHostile(status(p, s));
                case EffectSpec.Buff b -> { service.addBuff(p, a, b); self = true; }
                case EffectSpec.Resource r -> resource(prof, r, hit);
                case EffectSpec.Counter c -> service.openCounter(p.getUniqueId(), a, c);
                case EffectSpec.Empower em -> { service.empower(p.getUniqueId(), em.seconds()); self = true; }
                case EffectSpec.Spell s -> self |= spells.get().castForAbility(s.name(), p);
            }
        }
        if (reached || (self && service.inCombat(p.getUniqueId()))) service.awardXp(p, a.classId(), ClassService.CAST_XP);
    }

    // ── verbs ───────────────────────────────────────────────────

    /** True when one of {@code hit} is a hostile mob (cast XP is not paid for hitting farm animals). */
    private static boolean anyHostile(List<LivingEntity> hit) {
        for (LivingEntity e : hit) if (e instanceof org.bukkit.entity.Enemy) return true;
        return false;
    }

    /** Deal the damage verb; returns the enemies it struck (strikes after the first land later, on the same targets). */
    private List<LivingEntity> damage(Player p, ClassProfile prof, EffectSpec.Damage d) {
        List<LivingEntity> targets = targets(p, d.shape(), d.range(), d.angle(), d.maxTargets());
        if (targets.isEmpty()) return targets;
        double base = d.scale() == EffectSpec.Scale.POWER ? weaponBase(p) : SPELL_BASE;
        double amount = base * (d.coefficient() + d.perMomentum() * momentum(prof));
        for (int h = 0; h < d.hits(); h++) {
            long delay = (long) h * d.intervalTicks();
            if (delay == 0) strike(p, targets, amount, d);
            else p.getScheduler().runDelayed(plugin, t -> { if (p.isValid() && !p.isDead()) strike(p, targets, amount, d); }, null, delay);
        }
        if (d.shape() == EffectSpec.Shape.CONE) {
            Location at = p.getEyeLocation().add(p.getEyeLocation().getDirection().multiply(1.5));
            p.getWorld().spawnParticle(Particle.SWEEP_ATTACK, at, 3, 0.6, 0.2, 0.6, 0);
        }
        return targets;
    }

    /** One strike on each target, through the normal hit (POWER) or spell (ESSENCE) path. */
    private void strike(Player p, List<LivingEntity> targets, double amount, EffectSpec.Damage d) {
        VirtualHealthListener vh = health.get();
        IN_ABILITY.set(Boolean.TRUE);
        try {
            for (LivingEntity t : targets) {
                if (!t.isValid() || t.isDead() || !Bukkit.isOwnedByCurrentRegion(t)) continue;
                t.setNoDamageTicks(0); // an ability strike is its own hit, not one inside the last hit's grace window
                if (d.scale() == EffectSpec.Scale.ESSENCE && vh != null) {
                    vh.registerSpellDamage(t.getEntityId(), new SpellDamageContext(elementType(d.element()), 0.0, d.element(), false));
                }
                t.damage(amount, p);
            }
        } finally {
            IN_ABILITY.set(Boolean.FALSE);
        }
    }

    /** A counter-strike for a successful Riposte, next tick on the defender's thread. */
    void counterStrike(Player p, LivingEntity attacker, double coefficient) {
        double amount = weaponBase(p) * coefficient;
        p.getScheduler().run(plugin, t -> {
            if (!attacker.isValid() || attacker.isDead() || !Bukkit.isOwnedByCurrentRegion(attacker)) return;
            IN_ABILITY.set(Boolean.TRUE);
            try {
                attacker.setNoDamageTicks(0);
                attacker.damage(amount, p);
            } finally {
                IN_ABILITY.set(Boolean.FALSE);
            }
        }, null);
        p.getWorld().playSound(p.getLocation(), Sound.ITEM_SHIELD_BLOCK, 0.9f, 1.4f);
        p.getWorld().spawnParticle(Particle.CRIT, attacker.getLocation().add(0, attacker.getHeight() * 0.6, 0), 12, 0.3, 0.3, 0.3, 0.2);
    }

    private void dash(Player p, EffectSpec.Dash d) {
        Vector dir = p.getLocation().getDirection().setY(0);
        if (dir.lengthSquared() < 1.0e-4) dir = new Vector(0, 0, 1);
        dir.normalize().multiply(Math.min(2.2, d.distance() * 0.17)).setY(0.25);
        p.setVelocity(dir);
        p.getWorld().spawnParticle(Particle.CLOUD, p.getLocation(), 8, 0.3, 0.1, 0.3, 0.02);
    }

    /** After a dash: the first enemy just in front can't move for the stun's length. Returns it, or null. */
    private LivingEntity stunFirst(Player p, EffectSpec.Dash d) {
        List<LivingEntity> near = targets(p, EffectSpec.Shape.CONE, 2.8, 120, 1);
        if (near.isEmpty()) return null;
        near.get(0).addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, (int) Math.round(d.stunSeconds() * 20), 9, false, true));
        return near.get(0);
    }

    private boolean blink(Player p, EffectSpec.Blink b) {
        Location start = p.getLocation();
        Vector dir = p.getEyeLocation().getDirection().normalize();
        Location best = null;
        for (double s = 0.5; s <= b.distance() + 1.0e-9; s += 0.5) {
            Location feet = start.clone().add(dir.clone().multiply(s));
            if (!Bukkit.isOwnedByCurrentRegion(feet)) break;
            Block f = feet.getBlock();
            if (!f.isPassable() || !f.getRelative(BlockFace.UP).isPassable()) break; // never into blocks
            best = feet;
        }
        if (best == null) return false;
        best.setYaw(start.getYaw());
        best.setPitch(start.getPitch());
        p.getWorld().spawnParticle(Particle.PORTAL, start.clone().add(0, 1, 0), 20, 0.3, 0.6, 0.3, 0.2);
        p.teleportAsync(best);
        return true;
    }

    private List<LivingEntity> status(Player p, EffectSpec.Status s) {
        PotionEffectType type = Registry.EFFECT.get(NamespacedKey.minecraft(s.type().toLowerCase(Locale.ROOT)));
        if (type == null) {
            plugin.getLogger().warning("[Classes] Unknown status effect " + s.type());
            return List.of();
        }
        List<LivingEntity> targets = targets(p, s.shape(), s.range(), 360, s.maxTargets());
        for (LivingEntity t : targets) t.addPotionEffect(new PotionEffect(type, (int) Math.round(s.seconds() * 20), s.amplifier(), false, true));
        if (s.shape() == EffectSpec.Shape.CIRCLE) {
            p.getWorld().spawnParticle(Particle.SNOWFLAKE, p.getLocation().add(0, 0.5, 0), 30, s.range() / 2, 0.2, s.range() / 2, 0.02);
        }
        return targets;
    }

    private void resource(ClassProfile prof, EffectSpec.Resource r, int targets) {
        if (!"momentum".equalsIgnoreCase(r.kind())) return;
        int cur = momentum(prof);
        if (r.spendAll()) cur = 0;
        else if (r.delta() > 0) cur = Math.min(r.max(), cur + r.delta() * (r.perTarget() ? Math.max(0, targets) : 1));
        else cur = Math.max(0, cur + r.delta());
        prof.momentum = cur;
        prof.momentumAt = System.currentTimeMillis();
    }

    /** Momentum stacks right now (they fade {@link #MOMENTUM_DECAY_MS} after the last gain). */
    static int momentum(ClassProfile prof) {
        return System.currentTimeMillis() - prof.momentumAt > MOMENTUM_DECAY_MS ? 0 : prof.momentum;
    }

    // ── targeting ───────────────────────────────────────────────

    /** Enemies for a shape: STRIKE = the first one you look at (walls block it); CONE / CIRCLE = nearest first. */
    private List<LivingEntity> targets(Player p, EffectSpec.Shape shape, double range, double angle, int max) {
        Location eye = p.getEyeLocation();
        Vector dir = eye.getDirection();
        if (shape == EffectSpec.Shape.STRIKE) {
            RayTraceResult r = p.getWorld().rayTrace(eye, dir, range, FluidCollisionMode.NEVER, true, 0.4, e -> valid(p, e));
            return r != null && r.getHitEntity() instanceof LivingEntity le ? List.of(le) : List.of();
        }
        List<LivingEntity> out = new ArrayList<>();
        for (Entity e : p.getNearbyEntities(range, range, range)) {
            if (!(e instanceof LivingEntity le) || !valid(p, e)) continue;
            Vector to = le.getLocation().add(0, le.getHeight() / 2, 0).toVector().subtract(eye.toVector());
            if (to.lengthSquared() > range * range) continue;
            if (shape == EffectSpec.Shape.CONE && to.lengthSquared() > 0.01 && Math.toDegrees(dir.angle(to)) > angle / 2) continue;
            out.add(le);
        }
        out.sort(Comparator.comparingDouble(le -> le.getLocation().distanceSquared(eye)));
        return out.size() > max ? new ArrayList<>(out.subList(0, max)) : out;
    }

    /** Mobs only (PROTOTYPE: no players), never your own pets, armor stands or the dead. */
    private static boolean valid(Player p, Entity e) {
        if (!(e instanceof LivingEntity) || e == p || e instanceof Player || e instanceof ArmorStand || e.isDead()) return false;
        if (e instanceof Tameable tm && tm.isTamed() && p.getUniqueId().equals(tm.getOwnerUniqueId())) return false;
        return Bukkit.isOwnedByCurrentRegion(e);
    }

    /** The held weapon's basic hit (its attack damage; 1 bare-handed). */
    static double weaponBase(Player p) {
        var attr = p.getAttribute(Attribute.ATTACK_DAMAGE);
        double v = attr == null ? 1.0 : attr.getValue();
        return Double.isFinite(v) && v > 0 ? v : 1.0;
    }

    private static CustomDamageType elementType(String element) {
        if (element == null) return CustomDamageType.ARCANE_BLAST;
        return switch (element.toLowerCase(Locale.ROOT)) {
            case "fire" -> CustomDamageType.PYROMANCY;
            case "ice", "frost" -> CustomDamageType.CRYOMANCY;
            case "lightning" -> CustomDamageType.ELECTROMANCY;
            case "earth" -> CustomDamageType.GEOMANCY;
            case "dark", "void" -> CustomDamageType.DARK_ARTS;
            default -> CustomDamageType.ARCANE_BLAST;
        };
    }

    private static void sound(Player p, AbilityDef a) {
        EffectSpec first = a.effects().isEmpty() ? null : a.effects().get(0);
        Sound s = switch (first) {
            case EffectSpec.Damage d when d.scale() == EffectSpec.Scale.ESSENCE -> Sound.ENTITY_ILLUSIONER_CAST_SPELL;
            case EffectSpec.Damage d -> d.shape() == EffectSpec.Shape.CONE ? Sound.ENTITY_PLAYER_ATTACK_SWEEP : Sound.ENTITY_PLAYER_ATTACK_STRONG;
            case EffectSpec.Blink b -> Sound.ENTITY_ENDERMAN_TELEPORT;
            case EffectSpec.Dash d -> Sound.ENTITY_BREEZE_JUMP;
            case EffectSpec.Buff b -> Sound.ENTITY_EVOKER_PREPARE_ATTACK;
            case EffectSpec.Counter c -> Sound.ITEM_ARMOR_EQUIP_IRON;
            case EffectSpec.Empower em -> Sound.ITEM_TRIDENT_RETURN;
            case null, default -> null;
        };
        if (s != null) p.getWorld().playSound(p.getLocation(), s, 0.7f, 1.1f);
    }

    private static boolean fail(Player p, String why) {
        p.sendActionBar(Component.text(why, NamedTextColor.RED));
        return false;
    }
}
