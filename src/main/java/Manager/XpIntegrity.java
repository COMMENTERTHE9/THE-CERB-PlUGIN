package Manager;

import Attributes.KillXp;
import Attributes.XpRules;
import Skills.SkillManager;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityRemoveEvent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

import java.util.function.Supplier;

/**
 * The rules that keep combat XP honest (docs/STATUS.md, "XP integrity"; attributes.yml {@code xp-integrity}).
 * Every combat XP source asks here:
 * <ul>
 *   <li><b>Where the mob came from:</b> a monster spawner's mob, a bred animal or a spawn egg's pays a share or
 *       nothing ({@link XpRules#originFactor}), for its kill and for every hit on it.</li>
 *   <li><b>Who earns:</b> nothing in creative or spectator mode; no skill XP from fighting players (off for now:
 *       {@link XpRules#pvpXp}).</li>
 *   <li><b>Which skill a kill trains:</b> the one behind the player's last hit on the mob ({@link KillCredit}),
 *       not the item in hand when it died.</li>
 *   <li><b>Per-hit XP</b> (Critical Strike, the defensive skills) and <b>magic XP</b> (once per spell cast that reached
 *       a mob, never into the sky): the weak-mob share, and each mob pays each skill at most what its kill pays
 *       ({@link XpBudgetLedger}), as class cast XP already does.</li>
 *   <li><b>Idle:</b> defensive XP needs a player who did something recently (Paper's idle time).</li>
 *   <li><b>Names:</b> a mob a player named with a name tag is never taken for a registry mob by its name.</li>
 * </ul>
 * Hit and kill handlers call it on the mob's region thread; an award to a player another region owns is
 * handed to the player's own thread.
 */
public final class XpIntegrity implements Listener {

    /** Magic skill XP per spell cast that reached a mob (the old per-cast amounts, paid even into the sky). */
    public static final double SPELL_WEAVING_CAST_XP = 15.0, INTELLIGENCE_CAST_XP = 5.0, SCHOOL_CAST_XP = 10.0;
    /** The skill a spell's kill trains, as a kill with a spell focus in hand does (skills.yml). */
    public static final String SPELL_KILL_SKILL = "Spell Weaving";

    private final Plugin plugin;
    private final SkillManager skills;
    private final MobRegistry registry;
    private final Supplier<KillXp.Params> killXp;
    private final Supplier<XpRules> rules;
    private final NamespacedKey playerNamedKey;
    /** A mob's spawn reason as its spawn event gave it, where Paper's own record differs ({@link #onSpawn}). */
    private final NamespacedKey originKey;
    private final KillCredit credit = new KillCredit();
    private final XpBudgetLedger ledger = new XpBudgetLedger();

    public XpIntegrity(Plugin plugin, SkillManager skills, MobRegistry registry, Supplier<KillXp.Params> killXp, Supplier<XpRules> rules) {
        this.plugin = plugin;
        this.skills = skills;
        this.registry = registry;
        this.killXp = killXp;
        this.rules = rules;
        this.playerNamedKey = registry.getPlayerNamedKey();
        this.originKey = new NamespacedKey(plugin, "spawn_origin");
    }

    public XpRules rules() { return rules.get(); }

    /** Which skill a kill with an item trains (skills.yml's triggers, by material name), "" for none. */
    private volatile java.util.function.Function<String, String> skillOfMaterial = m -> "";

    public void setSkillOfMaterial(java.util.function.Function<String, String> skillOfMaterial) { this.skillOfMaterial = skillOfMaterial; }

    /** The skill a kill with {@code material} ("BOW", "NETHERITE_SWORD", "AIR") trains, or "". */
    public String skillOf(String material) {
        String s = skillOfMaterial.apply(material);
        return s == null ? "" : s;
    }

    /** The skill a kill with what {@code p} holds trains, or "". */
    public String skillOfHeld(Player p) {
        org.bukkit.inventory.ItemStack it = p.getInventory().getItemInMainHand();
        return skillOf(it == null || it.getType().isAir() ? "AIR" : it.getType().name());
    }

    /** Survival and adventure earn; creative and spectator don't. */
    public static boolean earns(Player p) {
        GameMode m = p.getGameMode();
        return m == GameMode.SURVIVAL || m == GameMode.ADVENTURE;
    }

    /** Why {@code p} earns nothing now, for the withheld-XP notice (SkillManager.xpWithheld): "in creative mode ...". */
    public static String whyNot(Player p) {
        return whyNot(p.getGameMode());
    }

    static String whyNot(GameMode m) {
        return (m == GameMode.SPECTATOR ? "in spectator mode" : "in creative mode") + " §8· §7survival and adventure train skills";
    }

    /**
     * Why a kill paid nothing when its mob's origin ({@link #origin}) has a share of 0, for the withheld-XP notice: "from
     * mobs out of spawn eggs" (attributes.yml xp-integrity.by-origin).
     */
    public static String originWhy(String reason) {
        if (reason == null) return "from this mob";
        return switch (reason) {
            case "SPAWNER_EGG", "DISPENSE_EGG" -> "from mobs out of spawn eggs";
            case "BREEDING" -> "from bred mobs";
            case "EGG" -> "from mobs hatched from eggs"; // thrown eggs' chickens, turtle and sniffer eggs, frogspawn
            case "BUILD_IRONGOLEM", "BUILD_SNOWMAN", "BUILD_WITHER", "BUILD_COPPERGOLEM" -> "from mobs you built";
            default -> "from mobs spawned this way (" + reason.toLowerCase(java.util.Locale.ROOT).replace('_', ' ') + ")";
        };
    }

    /** The share {@code mob} pays for where it came from ({@link #origin}). */
    public double originFactor(LivingEntity mob) {
        return rules.get().originFactor(origin(mob));
    }

    /**
     * Where {@code mob} came from, as a spawn reason name: the one its spawn event gave, where Paper's record differs
     * ({@link #onSpawn}), else Paper's (kept through restarts). Null when unknown.
     */
    public String origin(LivingEntity mob) {
        String recorded = mob.getPersistentDataContainer().get(originKey, PersistentDataType.STRING);
        if (recorded != null) return recorded;
        var reason = mob.getEntitySpawnReason();
        return reason == null ? null : reason.name();
    }

    /** {@code p} hit {@code mob} with something that trains {@code skill}: a kill soon after is credited to it. */
    public void creditHit(Player p, LivingEntity mob, String skill) {
        if (p != null && mob != null && !(mob instanceof Player)) credit.hit(mob.getUniqueId(), p.getUniqueId(), skill, System.currentTimeMillis());
    }

    /** The skill {@code killer}'s kill of {@code mob} trains: their last hit's, else {@code fallback} (the item in hand). */
    public String killSkill(Player killer, LivingEntity mob, String fallback) {
        return credit.skillFor(mob.getUniqueId(), killer.getUniqueId(), fallback, System.currentTimeMillis());
    }

    /**
     * Per-hit XP: {@code xp} of {@code skill} for {@code p}'s hit on (or by) {@code mob}, after the origin share and the
     * weak-mob share, and never more than the mob still has for that skill. Awards it and returns what was paid.
     */
    public double payHit(Player p, LivingEntity mob, String skill, double xp) {
        if (p == null || mob == null || mob instanceof Player || !(xp > 0) || !earns(p)) return 0;
        KillXp.Params k = killXp.get();
        int mobLevel = registry.getLevel(mob);
        int own = skills.getSkillLevel(p, skill);
        double origin = originFactor(mob);
        double amount = xp * origin;
        if (KillXp.isGreyKill(mobLevel, own, k)) amount *= Math.max(0.0, Math.min(1.0, k.antiGrindFactor()));
        if (!(amount > 0)) return 0;
        if (rules.get().hitBudget()) {
            double budget = KillXp.compute(mobLevel, registry.xpMultiplierOf(mob), own, k) * origin;
            amount = ledger.pay(mob.getUniqueId(), skill, amount, budget, System.currentTimeMillis());
        }
        if (amount > 0) award(p, skill, amount);
        if (!mob.isValid() || mob.isDead()) ledger.died(mob.getUniqueId(), System.currentTimeMillis()); // killed by this hit
        return amount;
    }

    /**
     * Who is behind a hit on a player: a player behind it first ({@link PvpTag#playerBehind}: their arrow, pet, potion
     * cloud, TNT, fangs), else the mob itself, a mob's arrow, an evoker's fangs, a witch's cloud. Null when nothing
     * living is (a falling block, fireworks, a dispenser's arrow).
     */
    public static LivingEntity attackerOf(org.bukkit.entity.Entity damager) {
        Player behind = damager == null ? null : PvpTag.playerBehind(damager);
        if (behind != null) return behind;
        if (damager instanceof LivingEntity living) return living;
        if (damager instanceof org.bukkit.entity.Projectile pr) return pr.getShooter() instanceof LivingEntity s ? s : null;
        if (damager instanceof org.bukkit.entity.EvokerFangs fangs) return fangs.getOwner();
        if (damager instanceof org.bukkit.entity.AreaEffectCloud cloud) return cloud.getSource() instanceof LivingEntity s ? s : null;
        return null;
    }

    /** True when {@code p} has done nothing for longer than the idle rule allows (defensive XP stops). */
    public boolean idle(Player p) {
        return rules.get().idle(p.getIdleDuration().toMillis());
    }

    /**
     * A spell cast reached {@code reached} (a Fireball's blast, an Arcane Bolt's target, a Frost Nova's ring): the magic
     * skills' XP for the cast, once, when one of them is a mob that can be hurt and belongs to no player. Into the sky,
     * at a wall, an armor stand, a player or a pet: nothing. Spell Weaving and Intelligence, and the school's skill
     * ({@link #schoolOf}); each is charged to the first mob reached that still has that skill's budget, with its
     * origin and weak-mob shares. Call on the region thread that owns the mobs.
     */
    public void spellCast(Player p, java.util.Collection<? extends LivingEntity> reached, CustomDamageType type) {
        if (p == null || reached == null || reached.isEmpty()) return;
        java.util.List<LivingEntity> mobs = new java.util.ArrayList<>(reached.size());
        for (LivingEntity e : reached) {
            if (e instanceof org.bukkit.entity.Mob && Bukkit.isOwnedByCurrentRegion(e)
                    && !AbilityTargets.belongsToAPlayer(e) && !AbilityTargets.unhurtable(e)) mobs.add(e);
        }
        if (mobs.isEmpty()) return;
        if (!earns(p)) { skills.xpWithheld(p, "Spell Weaving", whyNot(p)); return; }
        payCast(p, mobs, "Spell Weaving", SPELL_WEAVING_CAST_XP);
        payCast(p, mobs, "Intelligence", INTELLIGENCE_CAST_XP);
        String school = schoolOf(type);
        if (school != null) payCast(p, mobs, school, SCHOOL_CAST_XP);
    }

    /** One skill's cast XP, from the first mob in {@code mobs} with that skill's budget left. */
    private void payCast(Player p, java.util.List<LivingEntity> mobs, String skill, double xp) {
        for (LivingEntity mob : mobs) if (payHit(p, mob, skill, xp) > 0) return;
    }

    /** The school a spell trains: Elemental Mastery for fire, frost, lightning and earth, Arcane Knowledge for arcane. */
    static String schoolOf(CustomDamageType type) {
        if (type == null) return null;
        return switch (type) {
            case PYROMANCY, CRYOMANCY, ELECTROMANCY, GEOMANCY -> "Elemental Mastery";
            case ARCANE_BLAST -> "Arcane Knowledge";
            default -> null;
        };
    }

    /**
     * XP fractions per player and skill. Skill XP is awarded in whole points, and per-hit XP is often less than one (a hit
     * the defense bar mostly absorbs, a weak mob's quarter): the fraction waits for the next one instead of being lost.
     * On the player's thread only.
     */
    private final java.util.Map<java.util.UUID, Gathering.GatherRules.Carry> carries = new java.util.concurrent.ConcurrentHashMap<>();

    private void award(Player p, String skill, double amount) {
        Runnable pay = () -> {
            long whole = carries.computeIfAbsent(p.getUniqueId(), id -> new Gathering.GatherRules.Carry()).add(skill, amount);
            if (whole > 0) skills.addXpAndCheckLevelUp(skill, whole, p);
        };
        if (Bukkit.isOwnedByCurrentRegion(p)) pay.run();
        else p.getScheduler().run(plugin, t -> pay.run(), null);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(org.bukkit.event.player.PlayerQuitEvent e) {
        carries.remove(e.getPlayer().getUniqueId());
    }

    // ── Events ──────────────────────────────────────────────────

    /**
     * A mob's spawn reason as its spawn event gives it, kept on the mob when Paper's record lost it ({@link #originToKeep}).
     * Paper's entity load sets the record to DEFAULT (an animal's to NATURAL) when an entity reads data that doesn't name
     * one, and a 1.21.11 spawn egg always carries entity data (its type), so a spawn-egg mob was recorded as DEFAULT before
     * the egg's spawn could say SPAWNER_EGG and paid in full, against xp-integrity's by-origin SPAWNER_EGG: 0. A natural
     * spawn agrees with its record, and a copy (a mob stacker's, a parrot off a shoulder) keeps what it carries: nothing
     * is written.
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onSpawn(org.bukkit.event.entity.CreatureSpawnEvent e) {
        LivingEntity mob = e.getEntity();
        var record = mob.getEntitySpawnReason();
        String keep = originToKeep(e.getSpawnReason() == null ? null : e.getSpawnReason().name(), record == null ? null : record.name(),
                mob.getPersistentDataContainer().get(originKey, PersistentDataType.STRING));
        if (keep != null) mob.getPersistentDataContainer().set(originKey, PersistentDataType.STRING, keep);
    }

    /**
     * The spawn reason to keep on a mob at its spawn event, or null to write nothing (pure): the event's reason, only when
     * the mob carries none of ours yet and Paper's record is one its load falls back to (DEFAULT, or NATURAL for an animal)
     * or missing. A record Paper restored or set itself (a spawner's mob, a bred animal) stands.
     */
    static String originToKeep(String event, String record, String stored) {
        if (event == null || stored != null || event.equals(record)) return null;
        return record == null || record.equals("DEFAULT") || record.equals("NATURAL") ? event : null;
    }

    /**
     * A mob that turns into another (a zombie that drowns, a skeleton that freezes, a slime that splits, a villager struck
     * by lightning) passes on where it came from: a spawner's zombie makes a spawner's drowned. Before the new mob's spawn.
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onTransform(org.bukkit.event.entity.EntityTransformEvent e) {
        if (!(e.getEntity() instanceof LivingEntity old)) return;
        String from = origin(old);
        if (from == null) return;
        for (org.bukkit.entity.Entity made : e.getTransformedEntities()) {
            if (made instanceof LivingEntity && !made.getPersistentDataContainer().has(originKey, PersistentDataType.STRING)) {
                made.getPersistentDataContainer().set(originKey, PersistentDataType.STRING, from);
            }
        }
    }

    /** A player named this mob with a name tag: it is never taken for a registry mob by its name. */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onName(io.papermc.paper.event.player.PlayerNameEntityEvent e) {
        e.getEntity().getPersistentDataContainer().set(playerNamedKey, PersistentDataType.BYTE, (byte) 1);
    }

    /** After the kill handlers (they run earlier): the mob's credit goes, its budgets a few seconds later. */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onDeath(EntityDeathEvent e) {
        ledger.died(e.getEntity().getUniqueId(), System.currentTimeMillis());
        credit.forget(e.getEntity().getUniqueId());
    }

    /** Gone for good (a mob that is never saved leaves for good at unload too): its credit goes. */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onRemove(EntityRemoveEvent e) {
        if (e.getCause() != EntityRemoveEvent.Cause.UNLOAD || !e.getEntity().isPersistent()) credit.forget(e.getEntity().getUniqueId());
    }
}
