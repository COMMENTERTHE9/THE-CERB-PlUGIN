package Classes;

import Listener.VirtualHealthListener;
import Manager.CustomDamageType;
import Manager.ManaType;
import Manager.PlayerManaManager;
import Manager.SpellDamageContext;
import Spells.SpellManager;
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
    /**
     * Aim allowance of a single-target ability, in blocks past the target's hitbox: a near miss
     * still finds the enemy you were clearly aiming at (it must be in range and in sight).
     */
    static final double AIM_ASSIST_BLOCKS = 0.75;

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
     * Cast active slot {@code slot} (0-4) of the player's loadout with what is in the main hand.
     * Player's thread.
     */
    public CastResult cast(Player p, int slot) {
        return cast(p, slot, WeaponFamily.ofMaterial(p.getInventory().getItemInMainHand().getType().name()));
    }

    /**
     * Cast active slot {@code slot} with {@code hand} as the family in hand (the drop key has
     * already taken the item out of the hand when its event fires, so that caller passes the
     * dropped item's family). Player's thread. Every check is made first, including that a
     * single-target ability has a target and a Blink has room: a refused cast spends nothing
     * (no Essence, no cooldown, no Momentum). Then cost and cooldown are committed together and
     * the verbs run. The caller shows the result ({@link CastFeedback}).
     */
    public CastResult cast(Player p, int slot, WeaponFamily hand) {
        ClassProfile prof = service.profile(p.getUniqueId());
        if (prof == null || p.isDead() || !p.isValid() || !canAct.test(p)) return CastResult.silent();
        AbilityDef a = equippedActive(p, slot);
        if (a == null) return CastResult.refuse(CastResult.Outcome.EMPTY, null, "");
        long now = System.currentTimeMillis();
        if (!prof.unlocked(a)) {
            ClassDef c = service.catalog().classes().get(a.classId());
            return CastResult.refuse(CastResult.Outcome.NOT_UNLOCKED, a, (c == null ? a.classId() : c.displayName()) + " rank " + a.unlockRank());
        }
        if (now - prof.lastSwitchAt < ClassService.CAST_LOCK_AFTER_SWITCH_MS) return CastResult.refuse(CastResult.Outcome.SWITCHING, a, "");
        if (!WeaponFamily.fits(a.weapons(), hand)) return CastResult.refuse(CastResult.Outcome.WRONG_ITEM, a, needs(a));
        for (EffectSpec e : a.effects()) {
            if (e instanceof EffectSpec.Spell s && !spells.get().canCastForAbility(s.name())) {
                return CastResult.refuse(CastResult.Outcome.UNAVAILABLE, a, "no spell " + s.name()); // a config error: nothing is spent
            }
        }
        long left = prof.cooldowns().remaining(a, now);
        if (left > 0) return CastResult.cooldown(a, left);
        int needs = momentumNeeded(a);
        if (needs > 0 && momentum(prof) < needs) return CastResult.refuse(CastResult.Outcome.NO_RESOURCE, a, needs + " Momentum");
        // What the ability needs in the world, checked before anything is spent.
        LivingEntity aimed = null;
        EffectSpec first = a.effects().isEmpty() ? null : a.effects().get(0);
        if (first instanceof EffectSpec.Damage d && d.shape() == EffectSpec.Shape.STRIKE) {
            aimed = aimed(p, d.range());
            if (aimed == null) return CastResult.refuse(CastResult.Outcome.NO_TARGET, a, rangeText(d.range()));
        }
        Location blinkTo = null;
        if (first instanceof EffectSpec.Blink b) {
            blinkTo = blinkDestination(p, b);
            if (blinkTo == null) return CastResult.refuse(CastResult.Outcome.BLOCKED, a, "");
        }
        if (a.essenceCost() > 0 && !mana.spendMana(p, ManaType.BASIC, a.essenceCost())) {
            return CastResult.refuse(CastResult.Outcome.NO_ESSENCE, a, String.valueOf(Math.round(a.essenceCost())));
        }
        long cooldown = a.essenceCost() > 0
                ? Math.max(Math.min(a.cooldownMs(), ClassCatalog.MIN_COOLDOWN_MS), spellCooldown.applyAsLong(p.getUniqueId(), a.cooldownMs()))
                : a.cooldownMs();
        prof.cooldowns().commit(a, cooldown, service.catalog().groupMs(), now);
        prof.lastUse.put(a.classId(), now);
        casts.incrementAndGet();
        sound(p, a);
        StringBuilder detail = new StringBuilder();
        run(p, prof, a, 0, null, 0, aimed, blinkTo, detail);
        return CastResult.cast(a, detail.toString());
    }

    /** "8 blocks", "3.5 blocks". */
    static String rangeText(double range) {
        return (range == Math.rint(range) ? String.valueOf((long) range) : String.valueOf(range)) + " blocks";
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
        // A player's pet or mount is part of a PvP fight: abilities stay out of it (the window stays open).
        if (Bukkit.isOwnedByCurrentRegion(attacker) && Manager.AbilityTargets.belongsToAPlayer(attacker)) return false;
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
        CastFeedback fb = feedback;
        if (fb != null) fb.event(p, "§6✦ " + a.displayName() + "! §7· §fcountered");
        // Paid like a cast that reached it: enemies only, by their level (a weak one pays the kill-XP share).
        if (attacker instanceof org.bukkit.entity.Enemy && Bukkit.isOwnedByCurrentRegion(attacker)) {
            service.awardCastXp(p, a.classId(), service.levelOf(attacker));
        }
        return true;
    }

    private volatile CastFeedback feedback;

    /** Where an ability that goes off later (a Riposte's counter) reports itself. */
    public void setFeedback(CastFeedback feedback) { this.feedback = feedback; }

    /**
     * Perform the verbs from index {@code from}. A dash pauses the list until it lands, then the rest
     * runs (and the dash's stun lands on the first enemy in front). When the list is done, the cast
     * earns class XP if it reached a hostile mob ({@code foe}: the strongest one's level), or if it
     * was a self cast (buff, blink, Feint...) made during a fight with an enemy (paid for that
     * enemy): casting into the air, at farm animals, or in a fight with no enemy in it earns nothing
     * (CLASSES.md §2.2), and trivial mobs pay the kill-XP anti-grind share
     * ({@link ClassService#awardCastXp}). A Riposte earns its XP when it actually counters.
     *
     * <p>{@code aimed} is the target the leading strike was checked against (it is the one hit),
     * {@code blinkTo} the leading Blink's checked landing spot; {@code detail} collects what the
     * first part did ("3 hit", "x2.2 · 3 Momentum") for the cast message (null after a dash).
     */
    private void run(Player p, ClassProfile prof, AbilityDef a, int from, EffectSpec.Dash landed, int foe,
                     LivingEntity aimed, Location blinkTo, StringBuilder detail) {
        if (landed != null && landed.stunSeconds() > 0) foe = Math.max(foe, strongest(stunFirst(p, landed)));
        List<EffectSpec> fx = a.effects();
        boolean self = false;
        int hit = 0;
        for (int i = from; i < fx.size(); i++) {
            EffectSpec e = fx.get(i);
            boolean leading = i == 0;
            switch (e) {
                case EffectSpec.Dash d -> {
                    dash(p, d);
                    int next = i + 1;
                    int soFar = foe;
                    p.getScheduler().runDelayed(plugin, t -> {
                        if (p.isValid() && !p.isDead()) run(p, prof, a, next, d, soFar, null, null, null);
                    }, null, 7L);
                    return;
                }
                case EffectSpec.Damage d -> {
                    List<LivingEntity> hits = damage(p, prof, d, leading ? aimed : null, detail);
                    hit = hits.size();
                    foe = Math.max(foe, strongest(hits));
                }
                case EffectSpec.Blink b -> self |= blink(p, b, leading ? blinkTo : null);
                case EffectSpec.Status s -> foe = Math.max(foe, strongest(status(p, s)));
                case EffectSpec.Buff b -> {
                    service.addBuff(p, a, b);
                    self = true;
                    note(detail, buffText(b));
                    ring(p, Particle.CRIT, 1.2);
                }
                case EffectSpec.Resource r -> resource(prof, r, hit);
                case EffectSpec.Counter c -> {
                    service.openCounter(p.getUniqueId(), a, c);
                    note(detail, "guard " + seconds(c.windowMs()));
                    p.getWorld().spawnParticle(Particle.ENCHANTED_HIT, p.getLocation().add(0, 1.1, 0), 14, 0.45, 0.5, 0.45, 0.05);
                }
                case EffectSpec.Empower em -> {
                    service.empower(p.getUniqueId(), em.seconds());
                    self = true;
                    note(detail, "next hit crits");
                    p.getWorld().spawnParticle(Particle.WITCH, p.getLocation().add(0, 1.2, 0), 10, 0.35, 0.4, 0.35, 0.02);
                }
                case EffectSpec.Spell s -> spells.get().castForAbility(s.name(), p, burst -> spellHit(p, a, burst)); // XP on its hit
            }
        }
        if (foe == 0 && self) foe = service.fightFoeLevel(p.getUniqueId());
        if (foe > 0) service.awardCastXp(p, a.classId(), foe);
    }

    /** Add a part to the cast message ("3 hit"); a null builder (after a dash) takes nothing. */
    private static void note(StringBuilder detail, String part) {
        if (detail == null || part == null || part.isEmpty()) return;
        if (!detail.isEmpty()) detail.append(" · ");
        detail.append(part);
    }

    /** "+15% damage · 8 s". */
    static String buffText(EffectSpec.Buff b) {
        String what = b.attribute() != null ? b.attribute().displayName : b.stat().displayName.toLowerCase(Locale.ROOT);
        String amount = b.attribute() != null ? "+" + oneDecimal(b.amount()) : "+" + Math.round(b.amount() * 100) + "%";
        return amount + " " + what + " · " + oneDecimal(b.seconds()) + " s";
    }

    /** 2.2 / 3 (no trailing ".0"). */
    static String oneDecimal(double v) {
        long tenths = Math.round(v * 10);
        return tenths % 10 == 0 ? String.valueOf(tenths / 10) : tenths / 10 + "." + Math.abs(tenths % 10);
    }

    // ── verbs ───────────────────────────────────────────────────

    /**
     * A spell cast as an ability (Fireball) burst on {@code hit}, on the region thread that owns
     * them: class XP as for a cast that reached the strongest enemy among them, on the caster's thread.
     */
    private void spellHit(Player p, AbilityDef a, List<LivingEntity> hit) {
        int foe = strongest(hit);
        if (foe > 0) p.getScheduler().run(plugin, t -> service.awardCastXp(p, a.classId(), foe), null);
    }

    /** The level of the strongest hostile mob in {@code hit}, or 0 when none is hostile (no cast XP for farm animals). */
    private int strongest(List<LivingEntity> hit) {
        int best = 0;
        for (LivingEntity e : hit) best = Math.max(best, strongest(e));
        return best;
    }

    private int strongest(LivingEntity e) {
        return e instanceof org.bukkit.entity.Enemy && Bukkit.isOwnedByCurrentRegion(e) && !Manager.AbilityTargets.unhurtable(e)
                ? service.levelOf(e) : 0;
    }

    /**
     * Deal the damage verb; returns the enemies it struck (strikes after the first land later, on
     * the same targets). A strike hits {@code aimed} when the cast already checked one.
     */
    private List<LivingEntity> damage(Player p, ClassProfile prof, EffectSpec.Damage d, LivingEntity aimed, StringBuilder detail) {
        List<LivingEntity> targets;
        if (d.shape() == EffectSpec.Shape.STRIKE) {
            LivingEntity t = aimed != null && aimed.isValid() && !aimed.isDead() ? aimed : aimed(p, d.range());
            targets = t == null ? List.of() : List.of(t);
        } else {
            targets = targets(p, d.shape(), d.range(), d.angle(), d.maxTargets());
        }
        int stacks = d.perMomentum() > 0 ? momentum(prof) : 0;
        double multiplier = d.coefficient() + d.perMomentum() * stacks;
        if (d.perMomentum() > 0) note(detail, stacks > 0 ? "x" + oneDecimal(multiplier) + " · " + stacks + " Momentum" : "no Momentum");
        else if (d.shape() != EffectSpec.Shape.STRIKE) note(detail, targets.isEmpty() ? "no one hit" : targets.size() + " hit");
        if (d.shape() == EffectSpec.Shape.CONE) {
            Location at = p.getEyeLocation().add(p.getEyeLocation().getDirection().multiply(1.5));
            p.getWorld().spawnParticle(Particle.SWEEP_ATTACK, at, 3, 0.6, 0.2, 0.6, 0);
        }
        if (targets.isEmpty()) return targets;
        if (d.shape() == EffectSpec.Shape.STRIKE && d.scale() == EffectSpec.Scale.ESSENCE) beam(p, targets.get(0), d.element());
        double base = d.scale() == EffectSpec.Scale.POWER ? weaponBase(p) : SPELL_BASE;
        double amount = base * multiplier;
        for (int h = 0; h < d.hits(); h++) {
            long delay = (long) h * d.intervalTicks();
            int n = h;
            if (delay == 0) strike(p, targets, amount, d, n, stacks);
            else p.getScheduler().runDelayed(plugin, t -> { if (p.isValid() && !p.isDead()) strike(p, targets, amount, d, n, stacks); }, null, delay);
        }
        return targets;
    }

    /** One strike on each target, through the normal hit (POWER) or spell (ESSENCE) path; {@code n} counts a multi-hit's strikes. */
    private void strike(Player p, List<LivingEntity> targets, double amount, EffectSpec.Damage d, int n, int momentumSpent) {
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
                if (d.shape() == EffectSpec.Shape.STRIKE && d.scale() == EffectSpec.Scale.POWER) impact(p, t, d, n, momentumSpent);
            }
        } finally {
            IN_ABILITY.set(Boolean.FALSE);
        }
    }

    // ── what a cast looks like (one-off particles and sounds; nothing keeps running) ──

    /** A single-target spell's bolt: a line of coloured dust from the caster's hand to the target, and a burst on it. */
    private void beam(Player p, LivingEntity target, String element) {
        Location from = p.getEyeLocation().add(p.getEyeLocation().getDirection().multiply(0.6)).add(0, -0.25, 0);
        Location to = target.getLocation().add(0, target.getHeight() * 0.55, 0);
        Vector step = to.toVector().subtract(from.toVector());
        double length = step.length();
        if (length < 1.0e-3) return;
        step.multiply(0.35 / length);
        Particle.DustOptions dust = new Particle.DustOptions(elementColor(element), 0.9f);
        Location at = from.clone();
        for (double s = 0; s <= length; s += 0.35) {
            p.getWorld().spawnParticle(Particle.DUST, at, 1, 0, 0, 0, 0, dust);
            at.add(step);
        }
        p.getWorld().spawnParticle(Particle.WITCH, to, 8, 0.25, 0.3, 0.25, 0.02);
        p.getWorld().playSound(to, Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.9f, 1.4f);
    }

    /** A weapon strike landing: crits, a heavier hit for Momentum spent, a rising sweep for each strike of a flurry. */
    private void impact(Player p, LivingEntity t, EffectSpec.Damage d, int n, int momentumSpent) {
        Location at = t.getLocation().add(0, t.getHeight() * 0.6, 0);
        p.getWorld().spawnParticle(Particle.CRIT, at, 10, 0.3, 0.3, 0.3, 0.25);
        if (d.hits() > 1) {
            p.getWorld().spawnParticle(Particle.SWEEP_ATTACK, at, 1, 0, 0, 0, 0);
            p.getWorld().playSound(at, Sound.ENTITY_PLAYER_ATTACK_SWEEP, 0.7f, 1.15f + 0.15f * n);
        } else if (momentumSpent > 0) {
            p.getWorld().spawnParticle(Particle.ENCHANTED_HIT, at, 8 + 4 * momentumSpent, 0.35, 0.35, 0.35, 0.3);
            p.getWorld().playSound(at, momentumSpent >= 3 ? Sound.ITEM_MACE_SMASH_GROUND : Sound.ENTITY_PLAYER_ATTACK_CRIT, 0.8f, 1.0f);
        }
    }

    /** A ring of {@code particle} around the caster's feet (buffs). */
    private static void ring(Player p, Particle particle, double radius) {
        Location c = p.getLocation().add(0, 0.2, 0);
        for (int k = 0; k < 16; k++) {
            double ang = Math.PI * 2 * k / 16;
            p.getWorld().spawnParticle(particle, c.clone().add(Math.cos(ang) * radius, 0, Math.sin(ang) * radius), 1, 0, 0.05, 0, 0);
        }
    }

    private static org.bukkit.Color elementColor(String element) {
        if (element == null) return org.bukkit.Color.fromRGB(170, 90, 255);
        return switch (element.toLowerCase(Locale.ROOT)) {
            case "fire" -> org.bukkit.Color.fromRGB(255, 120, 40);
            case "ice", "frost" -> org.bukkit.Color.fromRGB(140, 220, 255);
            case "lightning" -> org.bukkit.Color.fromRGB(255, 240, 120);
            case "earth" -> org.bukkit.Color.fromRGB(150, 110, 60);
            case "dark", "void" -> org.bukkit.Color.fromRGB(70, 20, 90);
            default -> org.bukkit.Color.fromRGB(170, 90, 255); // arcane
        };
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

    /**
     * Where a Blink lands: as far as its distance toward where you look, stopping before the first
     * block (never into one) and inside the caster's region. Null when there is no room at all.
     */
    /**
     * Where a Blink lands: up to its distance along where you look, never into or through blocks.
     * The sight line walks forward from the eye until it meets a block. Under each point of it, the
     * ground within two blocks is where you would stand; with no ground that close, you are in the
     * air there. So looking at the floor lands on the floor where you look, looking ahead keeps your
     * height and hops a one-block wall, and looking up (or off a ledge) leaves you to fall. Null when
     * no spot is at least a block away (a wall in your face, looking at your own feet).
     */
    private static Location blinkDestination(Player p, EffectSpec.Blink b) {
        Location start = p.getLocation();
        Location eye = p.getEyeLocation();
        double eyeHeight = eye.getY() - start.getY();
        Vector dir = eye.getDirection().normalize();
        Location best = null;
        for (double s = 0.25; s <= b.distance() + 1.0e-9; s += 0.25) {
            Location sight = eye.clone().add(dir.clone().multiply(s));
            if (!Bukkit.isOwnedByCurrentRegion(sight) || !sight.getBlock().isPassable()) break; // the sight line meets a block
            Location feet = null;
            for (int down = 1; down <= 2; down++) { // the ground under this point: stand on it
                Block g = sight.getBlock().getRelative(BlockFace.DOWN, down);
                if (!g.isPassable()) {
                    feet = sight.clone();
                    feet.setY(g.getY() + 1.0);
                    break;
                }
            }
            if (feet == null) feet = sight.clone().subtract(0, eyeHeight, 0); // nothing under it: in the air
            if (standsFree(feet)) best = feet; // else no room to stand here: look further
        }
        if (best == null || best.distance(start) < 1.0) return null;
        best.setYaw(start.getYaw());
        best.setPitch(start.getPitch());
        return best;
    }

    /** True when a player's body (0.6 wide, 1.8 tall) standing at {@code feet} touches no solid block. */
    private static boolean standsFree(Location feet) {
        for (double dx : new double[]{-0.3, 0.3}) {
            for (double dz : new double[]{-0.3, 0.3}) {
                for (double dy : new double[]{0.0, 0.9, 1.79}) {
                    Location corner = feet.clone().add(dx, dy, dz);
                    if (!Bukkit.isOwnedByCurrentRegion(corner) || !corner.getBlock().isPassable()) return false;
                }
            }
        }
        return true;
    }

    private boolean blink(Player p, EffectSpec.Blink b, Location checked) {
        Location to = checked != null ? checked : blinkDestination(p, b);
        if (to == null) return false;
        p.getWorld().spawnParticle(Particle.PORTAL, p.getLocation().add(0, 1, 0), 20, 0.3, 0.6, 0.3, 0.2);
        p.getWorld().spawnParticle(Particle.REVERSE_PORTAL, to.clone().add(0, 1, 0), 16, 0.3, 0.6, 0.3, 0.02); // owned: checked above
        p.teleportAsync(to);
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

    /** The Momentum an ability needs before it can be cast (a finisher: 1), 0 for none. */
    static int momentumNeeded(AbilityDef a) {
        int n = 0;
        for (EffectSpec e : a.effects()) {
            if (e instanceof EffectSpec.Resource r && "momentum".equalsIgnoreCase(r.kind())) n = Math.max(n, r.needs());
        }
        return n;
    }

    /** Momentum stacks right now (they fade {@link #MOMENTUM_DECAY_MS} after the last gain). */
    static int momentum(ClassProfile prof) {
        return System.currentTimeMillis() - prof.momentumAt > MOMENTUM_DECAY_MS ? 0 : prof.momentum;
    }

    // ── targeting ───────────────────────────────────────────────

    /**
     * The enemy a single-target ability aims at: the one under the crosshair (walls block it),
     * else the one closest to the aim line within {@link #AIM_ASSIST_BLOCKS} of its hitbox, in
     * range and in sight. Null when there is none.
     */
    LivingEntity aimed(Player p, double range) {
        Location eye = p.getEyeLocation();
        Vector dir = eye.getDirection().normalize();
        RayTraceResult r = p.getWorld().rayTrace(eye, dir, range, FluidCollisionMode.NEVER, true, 0.2, e -> valid(p, e));
        if (r != null && r.getHitEntity() instanceof LivingEntity le) return le;
        double reach = r != null && r.getHitBlock() != null ? Math.min(range, r.getHitPosition().distance(eye.toVector())) : range;
        Vector start = eye.toVector();
        LivingEntity best = null;
        double bestScore = Double.MAX_VALUE;
        for (Entity e : p.getNearbyEntities(range + 1, range + 1, range + 1)) {
            if (!(e instanceof LivingEntity le) || !valid(p, e)) continue;
            org.bukkit.util.BoundingBox padded = le.getBoundingBox().expand(AIM_ASSIST_BLOCKS);
            // The aim line passes within the allowance of its hitbox, before the wall you aim at.
            if (!padded.contains(start) && padded.rayTrace(start, dir, reach) == null) continue;
            Vector to = le.getBoundingBox().getCenter().subtract(start);
            double along = to.dot(dir);
            if (along <= 0 || !p.hasLineOfSight(le)) continue;
            double score = Math.sqrt(Math.max(0, to.lengthSquared() - along * along)) / along; // the smallest angle off the aim wins
            if (score < bestScore) { bestScore = score; best = le; }
        }
        return best;
    }

    /** Enemies for a shape: STRIKE = the one you aim at ({@link #aimed}); CONE / CIRCLE = nearest first. */
    private List<LivingEntity> targets(Player p, EffectSpec.Shape shape, double range, double angle, int max) {
        Location eye = p.getEyeLocation();
        Vector dir = eye.getDirection();
        if (shape == EffectSpec.Shape.STRIKE) {
            LivingEntity t = aimed(p, range);
            return t == null ? List.of() : List.of(t);
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

    /**
     * Mobs only, while PvP is off: never a player, anyone's pet or a mount a player rides, armor
     * stands, the dead, or a mob that can't be hurt (an invulnerable one, a heart-bound Creaking:
     * hits that never land must not pay class XP).
     */
    private static boolean valid(Player p, Entity e) {
        if (!(e instanceof LivingEntity) || e == p || e instanceof Player || e instanceof ArmorStand) return false;
        if (!Bukkit.isOwnedByCurrentRegion(e) || e.isDead()) return false;
        return !Manager.AbilityTargets.belongsToAPlayer(e) && !Manager.AbilityTargets.unhurtable(e);
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
}
