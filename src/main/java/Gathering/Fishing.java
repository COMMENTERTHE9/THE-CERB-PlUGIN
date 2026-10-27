package Gathering;

import Attributes.Stat;
import Attributes.Yield;
import Manager.MobRegistry;
import Manager.XpIntegrity;
import Skills.Celebration;
import Skills.SkillManager;
import Skills.SkillNames;
import cerberus.world.cerb.CerberusPlugin;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.AbstractSkeleton;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.FishHook;
import org.bukkit.entity.Item;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.Zombie;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityRemoveEvent;
import org.bukkit.event.entity.EntityTargetEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.LongAdder;

/**
 * Fishing (docs/design/FISHING.md): what a cast, a bite and a catch do, the species of each water, discoveries and
 * personal bests, banked encounters ("lures" in the code and the save: players see "encounters" since
 * docs/design/FISHING_EQUIPMENT.md), and the sea creatures their owners call and fight. Rules are {@link FishingRules}
 * (resources.yml); each player's record is a {@link FishingJournal} saved on the player, which also keeps the species
 * they track and the "since your last visit" summary.
 *
 * <p>Threads (Folia): a cast and a catch run on the player's region thread; a bite on the bobber's (almost always the same
 * region: the bobber is within 32 blocks); a creature's death, removal and leash checks on the creature's. A lure is spent
 * only where the player and the bobber are both owned by the current thread, and a fight ends once (its {@code over} flag):
 * a win keeps the lure spent, anything else gives it back on the player's thread (or at their next join).
 */
public final class Fishing implements Listener {

    /** The player's journal (FishingJournal.encode), saved with the player. */
    public static final NamespacedKey JOURNAL = new NamespacedKey("cerberus", "fishing");
    /** On a called creature: its owner's UUID. */
    public static final NamespacedKey OWNER = new NamespacedKey("cerberus", "sea_creature_owner");

    private final CerberusPlugin plugin;
    private final GatheringService service;
    private final SkillManager skills;
    /** Fishing equipment (docs/design/FISHING_EQUIPMENT.md): the setup, bait and the reel; null until the plugin sets it. */
    private volatile Tackle tackle;

    /** A cast in progress, by bobber: who cast it and their numbers then, a call, and what the current bite is. */
    private static final class Cast {
        final UUID player;
        final int level;
        final double treasure, creature, fortune;
        final String call;     // a creature id when this cast calls one
        final boolean chosen;  // the player chose that creature; otherwise one of the bobber's water is preferred
        final int minWait, maxWait; // the wait a plain cast fishes with (lure speed, Quick Bite)
        /** The equipment this cast fishes with, taken at the cast (docs/design/FISHING_EQUIPMENT.md §5): a later switch changes nothing here. */
        final Tackle.CastGear gear;
        volatile String water;
        volatile FishingRules.Moment moment; // at the last bite
        volatile FishingRules.Species pending;
        volatile boolean rolled, calledOut, callFailed;
        /** The bite in progress (BITE until the catch or FAILED_ATTEMPT) and its number, for the auto-hook reel. */
        volatile boolean biteLive;
        volatile int biteSeq;

        Cast(UUID player, int level, double treasure, double creature, double fortune, String call, boolean chosen, int minWait, int maxWait,
             Tackle.CastGear gear) {
            this.player = player;
            this.level = level;
            this.treasure = treasure;
            this.creature = creature;
            this.fortune = fortune;
            this.call = call;
            this.chosen = chosen;
            this.minWait = minWait;
            this.maxWait = maxWait;
            this.gear = gear == null ? Tackle.CastGear.NONE : gear;
        }
    }

    /** What a catch was decided to be (at HIGH), paid at MONITOR when the catch really happened. */
    private record Outcome(Kind kind, FishingRules.Species species, FishingRules.Treasure treasure, double kg, double xp,
                           String water, int level, Cast cast, Force force, int extra) {}

    private enum Kind { JUNK, FISH, VANILLA_TREASURE, SPECIES, TREASURE }

    /** A creature fight in progress. */
    private static final class Fight {
        final UUID owner, entity;
        final String creature;
        final boolean elite;
        final int level;
        final long started;
        final AtomicBoolean over = new AtomicBoolean();
        volatile LivingEntity mob;
        volatile ScheduledTask task;
        volatile long farSince;

        Fight(UUID owner, UUID entity, String creature, boolean elite, int level, long started) {
            this.owner = owner;
            this.entity = entity;
            this.creature = creature;
            this.elite = elite;
            this.level = level;
            this.started = started;
        }
    }

    /** An admin's "the next catch is this" (tests): species <id>, treasure [id], lure [creature], apex. */
    public record Force(String kind, String id) {}

    private final Map<UUID, Cast> casts = new ConcurrentHashMap<>();
    private final Map<UUID, UUID> castOf = new ConcurrentHashMap<>();    // player → their bobber (one at a time, as in vanilla)
    private final Map<UUID, Outcome> outcomes = new ConcurrentHashMap<>();
    private final Map<UUID, Fight> fights = new ConcurrentHashMap<>();   // by creature entity
    private final Map<UUID, UUID> fightOf = new ConcurrentHashMap<>();   // owner → creature entity
    private final Map<UUID, Fight> wins = new ConcurrentHashMap<>();     // creature → a win not yet on its owner's journal
    private final java.util.concurrent.atomic.AtomicInteger out = new java.util.concurrent.atomic.AtomicInteger(); // creatures out (reserved before a spawn)
    private final Map<UUID, FishingJournal> journals = new ConcurrentHashMap<>();
    private final Map<UUID, String> selected = new ConcurrentHashMap<>();
    private final Map<UUID, Force> forced = new ConcurrentHashMap<>();
    /** The last tracking hint each player saw (species + verdict) and when: shown again only when it changes or 10 minutes pass. */
    private final Map<UUID, String> lastHint = new ConcurrentHashMap<>();
    private final Map<UUID, Long> lastHintAt = new ConcurrentHashMap<>();
    private static final long HINT_AGAIN_MS = 10 * 60_000L;

    private final LongAdder castCount = new LongAdder(), bites = new LongAdder(), catches = new LongAdder(), speciesCaught = new LongAdder(),
            treasureCaught = new LongAdder(), luresEarned = new LongAdder(), callsMade = new LongAdder(), won = new LongAdder(),
            refunds = new LongAdder(), refused = new LongAdder(), discoveries = new LongAdder(), extras = new LongAdder(),
            autoReeled = new LongAdder();

    public Fishing(CerberusPlugin plugin, GatheringService service, SkillManager skills) {
        this.plugin = plugin;
        this.service = service;
        this.skills = skills;
    }

    private FishingRules rules() { return service.catalog().fishing(); }

    public void setTackle(Tackle tackle) { this.tackle = tackle; }

    // ── the journal ───────────────────────────────────────────────

    /** {@code p}'s journal, read from the player once and kept while they are online. Player's thread. */
    public FishingJournal journal(Player p) {
        return journals.computeIfAbsent(p.getUniqueId(),
                id -> FishingJournal.decode(p.getPersistentDataContainer().get(JOURNAL, PersistentDataType.STRING)));
    }

    private static void save(Player p, FishingJournal j) {
        p.getPersistentDataContainer().set(JOURNAL, PersistentDataType.STRING, j.encode());
    }

    /** Write {@code p}'s journal back onto the player after a menu changed it (tracking, the summary read). Player's thread. */
    public void saveJournal(Player p) {
        save(p, journal(p));
    }

    /** Where "here" is for the journal: the bobber, the water the player looked at, or not known (docs/design/FISHING_EQUIPMENT.md §3). */
    public enum PlaceSource { BOBBER, LOOKING, UNKNOWN }

    /** The moment "here" (time and weather are known anywhere in the world; the water only from a bobber or a look). */
    public record Place(FishingRules.Moment moment, PlaceSource source) {}

    /**
     * Where {@code p} fishes now: their bobber in the water when a cast is out, else the water they look at within 30 blocks
     * (a preview), else unknown with the world's time and weather. Never their standing position. Player's thread.
     */
    public Place place(Player p) {
        FishingRules r = rules();
        FishHook hook = p.getFishHook();
        if (hook != null && hook.isValid() && Bukkit.isOwnedByCurrentRegion(hook) && hook.getState() == FishHook.HookState.BOBBING) {
            FishingRules.Moment m = moment(r, hook.getLocation());
            if (m.water() != null) return new Place(m, PlaceSource.BOBBER);
        }
        Block b = p.getTargetBlockExact(30, org.bukkit.FluidCollisionMode.ALWAYS);
        if (b != null && b.getType() == Material.WATER && Bukkit.isOwnedByCurrentRegion(b.getLocation())) {
            FishingRules.Moment m = moment(r, b.getLocation().add(0.5, 0.9, 0.5));
            if (m.water() != null) return new Place(m, PlaceSource.LOOKING);
        }
        World w = p.getWorld();
        long t = w.getTime() % 24000;
        return new Place(new FishingRules.Moment(null, null, t >= 13000 && t < 23000, w.hasStorm(), w.isThundering()), PlaceSource.UNKNOWN);
    }

    /** The lure {@code p} chose to call next ({@code /fishing call}, the Fishing page), if they still have one and it is configured. */
    public String selected(Player p) {
        String s = selected.get(p.getUniqueId());
        return s != null && journal(p).lures(s) > 0 && rules().creature(s) != null ? s : null;
    }

    public void select(Player p, String creature) {
        if (creature == null) selected.remove(p.getUniqueId());
        else selected.put(p.getUniqueId(), creature);
    }

    /** What a Sneak-cast calls: the chosen lure, else the first banked. Null when none. */
    String callFor(Player p, FishingJournal j) {
        String s = selected(p);
        if (s != null) return s;
        FishingRules r = rules();
        for (String id : j.lures().keySet()) if (r.creature(id) != null) return id;
        return null;
    }

    public void force(UUID player, Force f) {
        if (f == null) forced.remove(player);
        else forced.put(player, f);
    }

    // ── casting ───────────────────────────────────────────────────

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onFish(PlayerFishEvent e) {
        FishingRules r = rules();
        if (!r.enabled()) return;
        switch (e.getState()) {
            case FISHING -> cast(e, r);
            case BITE -> bite(e, r);
            case CAUGHT_FISH -> decide(e, r);
            default -> { }
        }
    }

    /** A cast (player's thread): the numbers this cast fishes with, a shorter wait, and a call when Sneaking with a lure. */
    private void cast(PlayerFishEvent e, FishingRules r) {
        Player p = e.getPlayer();
        FishHook hook = e.getHook();
        UUID id = p.getUniqueId();
        if (!XpIntegrity.earns(p) || !skills.hasLoadedSkills(id)) return; // creative: vanilla fishing, nothing of ours
        // A bobber gone without an event (switched items, too far): that cast ended, and its bait goes back first.
        UUID prev = castOf.get(id);
        if (prev != null && !prev.equals(hook.getUniqueId())) {
            casts.remove(prev);
            outcomes.remove(prev);
            castOf.remove(id, prev);
            Tackle tk = tackle;
            if (tk != null) tk.castEnded(p, prev);
        }
        castCount.increment();
        int level = skills.getSkillLevel(p, r.skill());
        Manager.SecondaryEffects sec = plugin.getSecondaryEffects();
        double treasure = sec == null ? 0 : sec.active(id, Stat.TREASURE_CHANCE);
        double fortune = service.bonusChance(id, Yield.FISH);
        String call = null;
        boolean chosen = false;
        if (p.isSneaking()) {
            FishingJournal j = journal(p);
            String want = callFor(p, j);
            String blocked = want == null ? null : callBlocked(id, j, r);
            if (blocked != null) { // a plain cast at its usual pace: a call that can't be answered never brings a quicker bite
                refused.increment();
                hint(p, blocked, 2500);
            } else if (want != null) {
                call = want;
                chosen = selected(p) != null;
                hint(p, chosen ? "§b✦ Calling: " + r.creature(call).name() + " §7(" + j.lures(call) + " encounter" + (j.lures(call) == 1 ? "" : "s") + ") · wait for the bite"
                        : "§b✦ Calling a creature of these waters §7(" + j.totalLures() + " encounter" + (j.totalLures() == 1 ? "" : "s") + ") · wait for the bite", 2500);
            }
        }
        // The equipment this cast fishes with: the active preset's specialist and reel, and one bait held on the line
        // (a call uses none). A bait that adds a stat does so through the attribute engine, as part of everything else.
        Tackle tk = tackle;
        Tackle.CastGear gear = tk == null ? Tackle.CastGear.NONE : tk.onCast(p, hook.getUniqueId(), call != null, level, journal(p));
        double lure = gear.lureSpeed() != null ? gear.lureSpeed() : sec == null ? 0 : sec.active(id, Stat.LURE_SPEED);
        double creature = gear.creatureChance() != null ? gear.creatureChance() : sec == null ? 0 : sec.active(id, Stat.SEA_CREATURE_CHANCE);
        // The wait a plain cast fishes with: lure speed and Quick Bite, never below the floor.
        double factor = r.waitFactor(lure, level);
        int min = hook.getMinWaitTime(), max = hook.getMaxWaitTime();
        if (factor < 1.0) {
            min = Math.max(1, (int) Math.round(min * factor));
            max = Math.max(min, (int) Math.round(max * factor));
        }
        if (call != null) hook.setWaitTime(30, 60); // the call answers in two to three seconds
        else if (factor < 1.0) hook.setWaitTime(min, max);
        castOf.put(id, hook.getUniqueId());
        casts.put(hook.getUniqueId(), new Cast(id, level, treasure, creature, fortune, call, chosen, min, max, gear));
        if (call == null) trackingHint(p, hook, level);
    }

    /**
     * The tracked species' brief cast feedback (docs/design/FISHING_EQUIPMENT.md §3): once the bobber settles in water (a
     * check every half second, at most six, on the bobber's own scheduler), one HUD line saying whether it can bite there
     * or what is missing. Shown only when the verdict changed (or after 10 minutes), only on an idle bar, never in PvP
     * combat; off with the journal's switch. Informational: nothing here touches a roll.
     */
    private void trackingHint(Player p, FishHook hook, int level) {
        FishingJournal j = journal(p);
        String tracked = j.tracked();
        if (tracked == null || j.flag(QUIET_TRACKING)) return;
        UUID id = p.getUniqueId();
        int[] tries = {0};
        hook.getScheduler().runAtFixedRate(plugin, t -> {
            if (!hook.isValid() || ++tries[0] > 6) { t.cancel(); return; }
            if (hook.getState() != FishHook.HookState.BOBBING) return;
            t.cancel();
            FishingRules r = rules();
            FishingRules.Species s = r.speciesById(tracked);
            if (s == null) return;
            FishingRules.Moment m = moment(r, hook.getLocation());
            List<FishingRules.Check> checks = r.checks(s, m, level);
            String verdict = verdict(checks);
            Player owner = Bukkit.getPlayer(id);
            if (owner == null) return;
            owner.getScheduler().run(plugin, t2 -> showHint(owner, tracked, verdict, trackingLine(r, s, checks)), null);
        }, null, 10L, 10L);
    }

    /** A short key for what the checks say (what changed decides whether the hint shows again). */
    static String verdict(List<FishingRules.Check> checks) {
        StringBuilder sb = new StringBuilder();
        for (FishingRules.Check c : checks) if (c.state() != FishingRules.CheckState.MET) sb.append(c.kind()).append(':').append(c.have()).append(';');
        return sb.length() == 0 ? "ok" : sb.toString();
    }

    /** "★ Goldfin Trout can bite here", "★ Goldfin Trout needs Rivers (you're at Lakes and ponds)", "★ Brook Char bites only at night". */
    static String trackingLine(FishingRules r, FishingRules.Species s, List<FishingRules.Check> checks) {
        GatheringCatalog.ItemDef d = GatheringCatalog.current() == null ? null : GatheringCatalog.current().item(s.item());
        String name = s.rarity().color + (d == null ? s.id() : d.name());
        List<FishingRules.Check> missing = checks.stream().filter(c -> c.state() == FishingRules.CheckState.MISSING).toList();
        if (missing.isEmpty()) return "§a★ " + name + " §acan bite here";
        FishingRules.Check first = missing.get(0);
        String need = switch (first.kind()) {
            case WATER -> "needs §f" + first.need() + " §7(you're at " + first.have() + ")";
            case LEVEL -> "needs §f" + first.need() + " §7(" + first.have() + ")";
            case TIME -> "bites only §f" + (first.need().startsWith("Night") ? "at night" : "by day");
            default -> "needs §f" + first.need().toLowerCase(Locale.ROOT);
        };
        return "§7★ " + name + " §7" + need + (missing.size() > 1 ? " §7and " + (missing.size() - 1) + " more" : "");
    }

    private void showHint(Player p, String species, String verdict, String line) {
        if (!p.isOnline()) return;
        Manager.PvpTag pvp = plugin.getPvpTag();
        if (pvp != null && pvp.isTagged(p.getUniqueId())) return;
        String key = species + "|" + verdict;
        long now = System.currentTimeMillis();
        Long at = lastHintAt.get(p.getUniqueId());
        if (key.equals(lastHint.get(p.getUniqueId())) && at != null && now - at < HINT_AGAIN_MS) return;
        Manager.PlayerHUDManager hud = plugin.getPlayerHUDManager();
        if (hud == null || !hud.flashIfIdle(p, line, 3000)) return; // a busy bar: the next cast tries again
        lastHint.put(p.getUniqueId(), key);
        lastHintAt.put(p.getUniqueId(), now);
    }

    /** The journal flag that turns the tracked species' cast feedback off. */
    public static final String QUIET_TRACKING = "quiet_tracking";

    /** Why a call can't be answered now (one creature per player, the server's limit), or null. */
    private String callBlocked(UUID player, FishingJournal j, FishingRules r) {
        if (j.active() != null || fightOf.containsKey(player)) return "§cFinish your fight first: one creature at a time.";
        if (out.get() >= r.calls().server()) return "§cThe waters are crowded: too many creatures are out on the server. Try again soon.";
        return null;
    }

    /** A bite (the bobber's thread): a call surfaces its creature; otherwise the bite's species is rolled and a rare one shows. */
    private void bite(PlayerFishEvent e, FishingRules r) {
        FishHook hook = e.getHook();
        Cast c = casts.get(hook.getUniqueId());
        if (c == null) return;
        bites.increment();
        if (c.call != null && !c.calledOut && !c.callFailed) {
            String why;
            try {
                why = summon(hook, r, c);
            } catch (RuntimeException ex) {
                plugin.getLogger().log(java.util.logging.Level.WARNING, "A sea creature's call failed", ex);
                why = "§7Nothing answered the call.";
            }
            if (why == null) return;
            // Unanswered (a fight already out, the server's limit, a spawn refused): this quick bite brings nothing, the cast
            // fishes on at its usual pace, and nothing is spent.
            c.callFailed = true;
            refused.increment();
            e.setCancelled(true);
            hook.setWaitTime(c.minWait, c.maxWait);
            Player p = Bukkit.getPlayer(c.player);
            if (p != null && Bukkit.isOwnedByCurrentRegion(p)) hint(p, why + " §7You fish on as usual.", 2500);
            return;
        }
        Location at = hook.getLocation();
        FishingRules.Moment m = moment(r, at);
        c.water = m.water();
        c.moment = m;
        Force f = forced.get(c.player);
        ThreadLocalRandom rnd = ThreadLocalRandom.current();
        Tackle.CastGear g = c.gear;
        TackleRules t = service.catalog().tackle();
        // Surveyor's Float, discovery and night bait favour some eligible species (never past a gate, never a rarity's place
        // among them, at most max-weight-factor); the plain roll otherwise.
        FishingRules.Species s = f != null && "species".equals(f.kind()) ? r.speciesById(f.id())
                : g.weighs() ? r.rollSpecies(m, c.level, rnd.nextDouble(), rnd.nextDouble(),
                        x -> t.weightFactor(x, g.caught().contains(x.id()), g.discovery(), g.night()))
                : r.rollSpecies(m, c.level, rnd.nextDouble(), rnd.nextDouble());
        c.pending = s;
        c.rolled = true;
        c.biteSeq++;
        c.biteLive = true;
        if (s != null && s.rarity().ordinal() >= FishingRules.Rarity.RARE.ordinal()) signal(at, s.rarity());
        if (g.specialist() != null && g.specialist().special() == TackleRules.Special.DISCOVERY) surveyorHint(c, r, m);
        if (g.autoReel() && c.call == null) autoReel(hook, c, c.biteSeq);
    }

    /** The waters a player was told Surveyor's Float has nothing left to favour in (this session; once each). */
    private final Map<UUID, Set<String>> surveyed = new ConcurrentHashMap<>();

    /** Surveyor's Float where every species that can bite is caught already: normal odds, said once per water. */
    private void surveyorHint(Cast c, FishingRules r, FishingRules.Moment m) {
        if (m.water() == null) return;
        for (FishingRules.Species x : r.eligible(m, c.level)) if (!c.gear.caught().contains(x.id())) return;
        if (!surveyed.computeIfAbsent(c.player, k -> ConcurrentHashMap.newKeySet()).add(m.water())) return;
        Player p = Bukkit.getPlayer(c.player);
        if (p == null) return;
        p.getScheduler().run(plugin, t -> {
            FishingRules.Water w = rules().water(m.water());
            Manager.PlayerHUDManager hud = plugin.getPlayerHUDManager();
            if (hud != null) hud.flashIfIdle(p, "§dSurveyor's Float: §7everything that bites in " + (w == null ? m.water() : w.name())
                    + " now is in your journal · normal odds", 3500);
        }, null);
    }

    /**
     * The auto-hook reel (docs/design/FISHING_EQUIPMENT.md §9): {@code delay-ticks} after a bite, on the player's thread, it
     * reels in through the game's own retrieve when the same bobber still has that bite: the catch event, XP, discoveries,
     * Fortune, bait and encounters all follow the normal path, and the rod takes its normal wear. It never recasts. A
     * manual reel, an item switch, a broken rod, death, logout or a missed bite in between: it finds no bite and does nothing.
     */
    private void autoReel(FishHook hook, Cast c, int seq) {
        Player p = Bukkit.getPlayer(c.player);
        Tackle tk = tackle;
        if (p == null || tk == null) return;
        int delay = service.catalog().tackle().reelDelayTicks();
        p.getScheduler().runDelayed(plugin, t -> {
            if (!p.isOnline() || p.isDead() || !hook.isValid() || !Bukkit.isOwnedByCurrentRegion(hook)) return;
            UUID hid = hook.getUniqueId();
            if (!hid.equals(castOf.get(p.getUniqueId())) || casts.get(hid) != c || !c.biteLive || c.biteSeq != seq) return;
            org.bukkit.inventory.PlayerInventory inv = p.getInventory();
            org.bukkit.inventory.EquipmentSlot hand = inv.getItemInMainHand().getType() == Material.FISHING_ROD ? org.bukkit.inventory.EquipmentSlot.HAND
                    : inv.getItemInOffHand().getType() == Material.FISHING_ROD ? org.bukkit.inventory.EquipmentSlot.OFF_HAND : null;
            if (hand == null) return;
            int wear = hook.retrieve(hand); // the same catch a click makes (PlayerFishEvent CAUGHT_FISH: decide, pay)
            autoReeled.increment();
            ItemStack rod = inv.getItem(hand);
            if (wear > 0 && rod.getType() == Material.FISHING_ROD) inv.setItem(hand, rod.damage(wear, p)); // Unbreaking, wear resistance
            p.getWorld().playSound(p.getLocation(), Sound.ENTITY_FISHING_BOBBER_RETRIEVE, org.bukkit.SoundCategory.NEUTRAL, 1.0f,
                    0.4f / (ThreadLocalRandom.current().nextFloat() * 0.4f + 0.8f));
            p.swingHand(hand);
        }, null, delay);
    }

    /** A rare bite shows itself before the reel: a glint and a chime at the bobber (never the vanilla bite particles). */
    private static void signal(Location at, FishingRules.Rarity rarity) {
        World w = at.getWorld();
        int n = 6 + 6 * (rarity.ordinal() - FishingRules.Rarity.RARE.ordinal());
        w.spawnParticle(rarity == FishingRules.Rarity.RARE ? Particle.GLOW : rarity == FishingRules.Rarity.EPIC ? Particle.WITCH : Particle.END_ROD,
                at.clone().add(0, 0.3, 0), n, 0.25, 0.15, 0.25, 0.02);
        w.playSound(at, Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.7f, 0.6f + 0.25f * (rarity.ordinal() - 2));
    }

    /** Where the bobber floats: its water, its biome, the time and the weather. */
    public FishingRules.Moment moment(FishingRules r, Location at) {
        World w = at.getWorld();
        Block b = at.getBlock();
        boolean underground = b.getLightFromSky() == 0 && at.getBlockY() < w.getSeaLevel();
        String biome = b.getBiome().getKey().getKey();
        FishingRules.Water water = r.waterAt(biome, underground);
        long t = w.getTime() % 24000;
        return new FishingRules.Moment(water == null ? null : water.id(), biome, t >= 13000 && t < 23000, w.hasStorm(), w.isThundering());
    }

    // ── catching ──────────────────────────────────────────────────

    private static final Set<Material> FISH = EnumSet.of(Material.COD, Material.SALMON, Material.TROPICAL_FISH, Material.PUFFERFISH);
    private static final Set<Material> VANILLA_TREASURE = EnumSet.of(Material.ENCHANTED_BOOK, Material.NAME_TAG, Material.SADDLE,
            Material.NAUTILUS_SHELL, Material.BOW, Material.FISHING_ROD);

    /** What vanilla's catch is: its fish, its treasure (books, saddles, name tags, nautilus shells, enchanted bows and rods) or junk. */
    static Kind vanillaKind(ItemStack it) {
        if (it == null) return Kind.JUNK;
        if (FISH.contains(it.getType())) return Kind.FISH;
        if (VANILLA_TREASURE.contains(it.getType())) {
            // A plain bow or rod is junk; an enchanted one is treasure (vanilla's two tables).
            boolean gear = it.getType() == Material.BOW || it.getType() == Material.FISHING_ROD;
            return !gear || !it.getEnchantments().isEmpty() ? Kind.VANILLA_TREASURE : Kind.JUNK;
        }
        return Kind.JUNK;
    }

    /** A catch (player's thread, HIGH): decide what it is and put it on the hook; paid at MONITOR if it really happens. */
    private void decide(PlayerFishEvent e, FishingRules r) {
        Player p = e.getPlayer();
        FishHook hook = e.getHook();
        Cast c = casts.get(hook.getUniqueId());
        if (c != null && c.calledOut) { e.setCancelled(true); return; } // this bite became a creature
        if (!(e.getCaught() instanceof Item item) || !XpIntegrity.earns(p) || !skills.hasLoadedSkills(p.getUniqueId())) return;
        int level = c != null ? c.level : skills.getSkillLevel(p, r.skill());
        FishingRules.Moment m = moment(r, hook.getLocation());
        String water = c != null && c.water != null ? c.water : m.water();
        Force f = forced.remove(p.getUniqueId());
        ThreadLocalRandom rnd = ThreadLocalRandom.current();
        Kind kind = vanillaKind(item.getItemStack());
        FishingRules.Xp x = r.xp();
        Outcome o;
        double treasureChance = c != null ? c.treasure : 0;
        boolean forceTreasure = f != null && "treasure".equals(f.kind());
        boolean forceSpecies = f != null && "species".equals(f.kind());
        if (kind == Kind.VANILLA_TREASURE && !forceSpecies && !forceTreasure) {
            o = new Outcome(Kind.VANILLA_TREASURE, null, null, 0, x.treasure(), water, level, c, f, 0);
        } else if (forceTreasure || (r.treasureSense(level) && hook.isInOpenWater() && rnd.nextDouble() < treasureChance)) {
            FishingRules.Treasure t = forceTreasure && f.id() != null ? r.treasureById(f.id()) : r.rollTreasure(level, rnd.nextDouble());
            GatheringCatalog.ItemDef def = t == null ? null : service.catalog().item(t.item());
            if (def == null) o = new Outcome(kind, null, null, 0, kind == Kind.FISH ? x.fish() : x.junk(), water, level, c, f, 0);
            else {
                item.setItemStack(GearItems.makeItem(def, 1));
                o = new Outcome(Kind.TREASURE, null, t, 0, t.xp() >= 0 ? t.xp() : x.of(t.rarity()), water, level, c, f, 0);
            }
        } else {
            FishingRules.Species s = forceSpecies ? r.speciesById(f.id())
                    : c != null && c.rolled ? c.pending
                    : r.rollSpecies(new FishingRules.Moment(water, m.biome(), m.night(), m.rain(), m.thunder()), level, rnd.nextDouble(), rnd.nextDouble());
            GatheringCatalog.ItemDef def = s == null ? null : service.catalog().item(s.item());
            if (def == null) o = new Outcome(kind, null, null, 0, kind == Kind.FISH ? x.fish() : x.junk(), water, level, c, f, 0);
            else {
                double kg = FishingRules.kg(s, rnd.nextDouble(), c != null ? c.gear.kgExponent() : 2.5);
                item.setItemStack(GearItems.makeItem(def, 1));
                o = new Outcome(Kind.SPECIES, s, null, kg, s.xp() >= 0 ? s.xp() : x.of(s.rarity()), water, level, c, f, 0);
            }
        }
        // Fortune (fish): one more of the same fish, never of treasure or junk; from Twin Catch it can come twice. They go into
        // the caught stack: the catch is thrown to the player after this event, so separate items would stay at the bobber.
        if ((o.kind() == Kind.SPECIES || o.kind() == Kind.FISH) && c != null && c.fortune > 0 && rnd.nextDouble() < c.fortune) {
            int extra = 1 + (r.twinCatch(level) && rnd.nextDouble() < c.fortune ? 1 : 0);
            ItemStack stack = item.getItemStack();
            stack.setAmount(Math.min(stack.getMaxStackSize(), stack.getAmount() + extra));
            item.setItemStack(stack);
            o = new Outcome(o.kind(), o.species(), o.treasure(), o.kg(), o.xp(), o.water(), o.level(), o.cast(), o.force(), extra);
        }
        outcomes.put(hook.getUniqueId(), o);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onFished(PlayerFishEvent e) {
        UUID hook = e.getHook().getUniqueId();
        Player p = e.getPlayer();
        UUID who = p.getUniqueId();
        Tackle tk = tackle;
        switch (e.getState()) {
            case CAUGHT_FISH -> {
                Outcome o = outcomes.remove(hook);
                Cast was = casts.get(hook);
                // Cancelled by another plugin: no catch, the bobber stays in the water and the cast goes on (its bait too).
                // (A bite that became a creature cancels its own catch, and that cast is over.)
                if (e.isCancelled() && (was == null || !was.calledOut)) return;
                Cast c = casts.remove(hook);
                castOf.remove(who, hook);
                if (c != null) c.biteLive = false;
                if (o != null && e.getCaught() instanceof Item item) pay(p, o, item, c, e.getHook());
                else {
                    if (o == null && !e.isCancelled() && e.getCaught() instanceof Item && !XpIntegrity.earns(p) && rules().enabled()) {
                        skills.xpWithheld(p, rules().skill(), XpIntegrity.whyNot(p)); // a vanilla catch in creative
                    }
                    if (tk != null) tk.castEnded(p, hook); // no catch of ours: a bait held on this line goes back
                }
            }
            case FISHING -> {
                if (e.isCancelled()) {
                    casts.remove(hook);
                    castOf.remove(who, hook);
                    if (tk != null) tk.castEnded(p, hook); // cancelled after we took its bait: back it goes
                }
            }
            // A missed bite keeps the cast (the bobber stays and the next bite rolls again); only the bite is over. A bite
            // another plugin cancelled after ours never reached the bobber: nothing for the reel to take.
            case FAILED_ATTEMPT -> { Cast c = casts.get(hook); if (c != null) c.biteLive = false; }
            case BITE -> { if (e.isCancelled()) { Cast c = casts.get(hook); if (c != null) c.biteLive = false; } }
            case REEL_IN, IN_GROUND, CAUGHT_ENTITY -> {
                casts.remove(hook);
                outcomes.remove(hook);
                castOf.remove(who, hook);
                if (tk != null) tk.castEnded(p, hook); // nothing caught: its bait goes back
            }
            default -> { }
        }
    }

    /** A catch that really happened (player's thread): XP, the journal, Fortune's extra fish, maybe an encounter, the bait. */
    private void pay(Player p, Outcome o, Item item, Cast c, FishHook hook) {
        FishingRules r = rules();
        FishingJournal j = journal(p);
        double xp = o.xp();
        catches.increment();
        long now = System.currentTimeMillis();
        String line = null;
        switch (o.kind()) {
            case SPECIES -> {
                speciesCaught.increment();
                FishingRules.Species s = o.species();
                String name = itemName(s.item());
                FishingJournal.Record rec = j.record(s.id(), o.kg());
                line = s.rarity().color + "◆ " + name + " §7" + kg(o.kg());
                if (rec == FishingJournal.Record.DISCOVERY) {
                    double bonus = r.xp().discovery(s.rarity());
                    xp += bonus;
                    discoveries.increment();
                    j.countDiscovery(s.id(), o.kg(), now);
                    discovered(p, name, s.rarity(), bonus, j, r, "species");
                } else if (rec == FishingJournal.Record.PERSONAL_BEST) {
                    j.countPersonalBest(s.id(), o.kg(), now);
                    if (s.rarity().ordinal() >= FishingRules.Rarity.UNCOMMON.ordinal()) line += " §6★ personal best";
                }
            }
            case TREASURE -> {
                treasureCaught.increment();
                FishingRules.Treasure t = o.treasure();
                String name = itemName(t.item());
                line = t.rarity().color + "✦ Treasure: " + name;
                if (j.record(t.id(), 0) == FishingJournal.Record.DISCOVERY) {
                    double bonus = r.xp().discovery(t.rarity());
                    xp += bonus;
                    discoveries.increment();
                    discovered(p, name, t.rarity(), bonus, j, r, "treasure");
                }
            }
            default -> { }
        }
        if (o.extra() > 0) { // Fortune's extra fish, in the caught stack (decide)
            extras.add(o.extra());
            p.playSound(p.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.35f, 1.8f);
            line = (line == null ? "§f◆ Fish" : line) + " §a+" + o.extra();
        }
        if (line != null) hint(p, line, 1800);
        long got = xp > 0 ? skills.addXpCounted(r.skill(), xp, p) : 0;
        j.countCatch(got); // the summary: every catch that really happened, and the XP it really awarded
        lures(p, o, j, r, c);
        save(p, j);
        // The bait held on this line: used up when it applied to this catch, else back in the bag (one per catch, never
        // per Fortune fish). A catch without its cast record puts it back.
        Tackle tk = tackle;
        if (tk != null) {
            if (c != null) tk.onCatch(p, hook.getUniqueId(), c.gear, c.moment != null ? c.moment : moment(r, hook.getLocation()), o.level());
            else tk.castEnded(p, hook.getUniqueId());
        }
    }

    /** The encounter rolls after a catch: Something Stirs' first one, the sea-creature chance, the apex. True when one was banked. */
    private boolean lures(Player p, Outcome o, FishingJournal j, FishingRules r, Cast c) {
        if (o.water() == null) return false;
        int level = o.level();
        ThreadLocalRandom rnd = ThreadLocalRandom.current();
        boolean force = o.force() != null && "lure".equals(o.force().kind());
        boolean forceApex = o.force() != null && "apex".equals(o.force().kind());
        FishingRules.Creature got = null;
        boolean first = false;
        if (forceApex) got = r.creature(o.force().id()) != null ? r.creature(o.force().id()) : r.apexOf(o.water(), Math.max(level, r.techniques().apex()));
        else if (force && o.force().id() != null) got = r.creature(o.force().id());
        else if (r.apex(level) && rnd.nextDouble() < r.techniques().apexChance()) got = r.apexOf(o.water(), level);
        if (got == null && level >= r.techniques().creatures()) {
            if (!j.flag("stirs")) { // Something Stirs: the first catch from the milestone on brings a lure
                got = r.rollCreature(o.water(), level, rnd.nextDouble());
                first = got != null;
            } else if (force || (c != null && rnd.nextDouble() < c.creature)) {
                got = r.rollCreature(o.water(), level, rnd.nextDouble());
            }
        }
        if (got == null || !j.addLure(got.id(), 0)) return false;
        if (first) j.setFlag("stirs");
        j.countEncounter();
        luresEarned.increment();
        int have = j.lures(got.id());
        String rarity = got.rarity().color;
        if (first || got.apex()) {
            celebrate(p, new Celebration(Celebration.Kind.DISCOVERY, Celebration.Tier.MAJOR,
                    rarity + "✦ " + (got.apex() ? "An apex took your bait: " : "Something stirs: ") + got.name(), rarity + "✦ " + got.name(),
                    rarity + (got.apex() ? "Apex: " : "") + got.name(), "§7took your bait and slipped away · Sneak and cast to call it",
                    List.of(rarity + "✦ " + got.name() + " §7took your bait and slipped away. Encounter banked (" + have + "): "
                            + "§fSneak and cast§7 into water to call it and fight it."), -1));
        } else {
            hint(p, rarity + "✦ " + got.name() + " §7took your bait · encounter banked (" + have + ") · §fSneak + cast§7 to call it", 3500);
            p.sendMessage(rarity + "✦ " + got.name() + " §7took your bait and slipped away. Encounter banked (" + have + "). Sneak and cast to call it.");
            p.playSound(p.getLocation(), Sound.ENTITY_DOLPHIN_SPLASH, 0.8f, 0.7f);
        }
        return true;
    }

    private void discovered(Player p, String name, FishingRules.Rarity rarity, double bonus, FishingJournal j, FishingRules r, String what) {
        boolean species = what.equals("species");
        long found = species ? r.allSpecies().keySet().stream().filter(j::caught).count() : r.treasure().stream().filter(t -> j.caught(t.id())).count();
        int all = species ? r.allSpecies().size() : r.treasure().size();
        String gain = bonus > 0 ? " §7· §a+" + Math.round(bonus) + " XP" : "";
        celebrate(p, new Celebration(Celebration.Kind.DISCOVERY, rarity.ordinal() >= FishingRules.Rarity.EPIC.ordinal() ? Celebration.Tier.MAJOR : Celebration.Tier.ORDINARY,
                "§a✦ §fNew " + what + ": " + rarity.color + name + gain, "§a✦ " + rarity.color + name,
                rarity.ordinal() >= FishingRules.Rarity.EPIC.ordinal() ? rarity.color + name : null,
                rarity.ordinal() >= FishingRules.Rarity.EPIC.ordinal() ? "§7" + rarity.displayName + " " + what + " · a first catch" : null,
                List.of("§a✦ New " + what + ": " + rarity.color + name + " §7(" + rarity.displayName + "; " + found + " of " + all
                        + (species ? " species" : " treasures") + (bonus > 0 ? ", +" + Math.round(bonus) + " " + SkillNames.display(r.skill()) + " XP" : "") + ")"), -1));
    }

    private String itemName(String itemId) {
        GatheringCatalog.ItemDef d = service.catalog().item(itemId);
        return d == null ? itemId : d.name();
    }

    static String kg(double kg) {
        return kg <= 0 ? "" : String.format(Locale.ROOT, kg < 10 ? "%.2f kg" : "%.1f kg", kg);
    }

    // ── calling a creature ────────────────────────────────────────

    /**
     * The bite of a call (the bobber's thread): the creature surfaces at the bobber and the lure is spent, both here, on the
     * thread that owns the player and the bobber. Null when it surfaced; otherwise why not (nothing spent).
     */
    private String summon(FishHook hook, FishingRules r, Cast c) {
        Player p = Bukkit.getPlayer(c.player);
        if (p == null || !Bukkit.isOwnedByCurrentRegion(p) || !Bukkit.isOwnedByCurrentRegion(hook.getLocation())) return "§7Nothing answered the call.";
        FishingJournal j = journal(p);
        FishingRules.Creature cr = r.creature(c.chosen ? c.call : r.lureFor(j.lures().keySet(), moment(r, hook.getLocation()).water(), c.call));
        if (cr == null || j.lures(cr.id()) <= 0) return "§7Nothing answered the call: no encounter banked for it.";
        String blocked = callBlocked(p.getUniqueId(), j, r);
        if (blocked != null) return blocked;
        EntityType type;
        try { type = EntityType.valueOf(cr.entity()); } catch (IllegalArgumentException ex) { return "§7Nothing answered the call."; }
        Class<? extends Entity> cls = type.getEntityClass();
        if (cls == null || !LivingEntity.class.isAssignableFrom(cls)) return "§7Nothing answered the call.";
        // The server's limit, exactly: a place is reserved before the spawn and given up if the creature doesn't come.
        if (out.incrementAndGet() > r.calls().server()) {
            out.decrementAndGet();
            return "§cThe waters are crowded: too many creatures are out on the server. Try again soon.";
        }
        Fight f;
        try {
            f = surface(p, hook, r, c, j, cr, cls);
        } catch (RuntimeException ex) {
            out.decrementAndGet(); // registering the fight is surface's last step: nothing was registered
            throw ex;
        }
        if (f == null) { out.decrementAndGet(); return "§7Nothing answered the call."; }
        // The fight is registered: from here its place is given back by end(f), whatever happens below.
        announce(p, hook, f, cr);
        return null;
    }

    /**
     * The creature spawns at the bobber, the lure is spent and the fight is registered (its last step); null when the spawn
     * didn't happen (nothing spent).
     */
    private Fight surface(Player p, FishHook hook, FishingRules r, Cast c, FishingJournal j, FishingRules.Creature cr, Class<? extends Entity> cls) {
        Location at = hook.getLocation();
        int worldLevel = worldLevel(at);
        Manager.CombatConfig cc = plugin.getCombatConfig();
        int maxLevel = cc == null || cc.mobHealthScaling == null ? 100 : cc.mobHealthScaling.getMaxLevel();
        int level = r.creatureLevel(cr, c.level, worldLevel, ThreadLocalRandom.current().nextDouble() * 2 - 1, maxLevel);
        boolean elite = r.elite(c.level);
        double health = cr.health() * (elite ? r.techniques().eliteHealth() : 1.0);
        MobRegistry registry = plugin.getMobRegistry();
        UUID owner = p.getUniqueId();
        LivingEntity mob = (LivingEntity) at.getWorld().spawn(at.clone().add(0, -0.4, 0), cls, CreatureSpawnEvent.SpawnReason.CUSTOM, ent -> {
            LivingEntity le = (LivingEntity) ent;
            if (registry != null) {
                registry.tagEntity(le, cr.id());
                registry.requestSpawnLevel(le, level);
                registry.requestHealthFactor(le, health);
            }
            le.setCustomName((elite ? "Elite " : "") + cr.name());
            le.setCustomNameVisible(true);
            le.setPersistent(false);          // never saved into the world: a restart gives the lure back
            le.setRemoveWhenFarAway(false);   // the leash decides when it leaves
            le.setCanPickupItems(false);
            le.getPersistentDataContainer().set(OWNER, PersistentDataType.STRING, owner.toString());
            if (cr.scale() != 1.0 && le.getAttribute(org.bukkit.attribute.Attribute.SCALE) != null) {
                le.getAttribute(org.bukkit.attribute.Attribute.SCALE).setBaseValue(cr.scale());
            }
            if (le instanceof Zombie z) { z.setShouldBurnInDay(false); z.setAdult(); }
            if (le instanceof AbstractSkeleton s) s.setShouldBurnInDay(false);
            EntityEquipment eq = le.getEquipment();
            if (eq != null) {
                eq.clear();
                for (Map.Entry<String, String> e : cr.equipment().entrySet()) {
                    Material m = Material.matchMaterial(e.getValue());
                    if (m == null) continue;
                    ItemStack it = new ItemStack(m);
                    switch (e.getKey()) {
                        case "hand" -> { eq.setItemInMainHand(it); eq.setItemInMainHandDropChance(0f); }
                        case "off-hand" -> { eq.setItemInOffHand(it); eq.setItemInOffHandDropChance(0f); }
                        case "helmet" -> { eq.setHelmet(it); eq.setHelmetDropChance(0f); }
                        default -> { }
                    }
                }
            }
        });
        long now = System.currentTimeMillis();
        if (!mob.isValid() || !j.spend(cr.id(), mob.getUniqueId(), now)) { mob.remove(); return null; } // e.g. another plugin refused the spawn
        save(p, j);
        c.calledOut = true;
        Fight f = new Fight(owner, mob.getUniqueId(), cr.id(), elite, level, now);
        f.mob = mob;
        fights.put(mob.getUniqueId(), f);
        fightOf.put(owner, mob.getUniqueId());
        return f;
    }

    /** A registered fight begins: the creature targets its owner, its leash check runs, and the moment is shown. */
    private void announce(Player p, FishHook hook, Fight f, FishingRules.Creature cr) {
        LivingEntity mob = f.mob;
        Location at = hook.getLocation();
        if (mob instanceof Mob m) m.setTarget(p);
        f.task = mob.getScheduler().runAtFixedRate(plugin, t -> tick(mob, f), () -> { }, 20L, 20L);
        callsMade.increment();
        World w = at.getWorld();
        w.spawnParticle(Particle.SPLASH, at, 60, 0.8, 0.4, 0.8, 0.2);
        w.playSound(at, Sound.ENTITY_GENERIC_SPLASH, 1.2f, 0.6f);
        w.playSound(at, Sound.ENTITY_ELDER_GUARDIAN_AMBIENT_LAND, 0.6f, 1.4f);
        String color = cr.rarity().color;
        String name = (f.elite ? "Elite " : "") + cr.name();
        p.sendTitle(color + name, "§7Level " + f.level + " · it surfaces!", 5, 40, 10);
        p.sendMessage(color + "✦ " + name + " §7(level " + f.level + ") answers your call. Defeat it for its rewards; "
                + "if it gets away, its encounter comes back.");
        hook.remove(); // no fish on this line: the bite was the creature
    }

    /** The world's own mob level where a creature surfaces (distance from spawn, as any mob there). */
    private int worldLevel(Location at) {
        Manager.CombatConfig cc = plugin.getCombatConfig();
        if (cc == null || cc.mobHealthScaling == null) return 1;
        Location spawn = at.getWorld().getSpawnLocation();
        double dx = at.getX() - spawn.getX(), dz = at.getZ() - spawn.getZ();
        return cc.mobHealthScaling.levelForDistance(Math.sqrt(dx * dx + dz * dz));
    }

    /** Once a second on the creature's thread: keep it on its owner; it leaves when they can't fight it. */
    private void tick(LivingEntity mob, Fight f) {
        if (f.over.get() || !mob.isValid()) return;
        FishingRules.Calls calls = rules().calls();
        Player p = Bukkit.getPlayer(f.owner);
        long now = System.currentTimeMillis();
        boolean near = p != null && p.isOnline() && !p.isDead() && p.getWorld() == mob.getWorld() && Bukkit.isOwnedByCurrentRegion(p)
                && p.getLocation().distanceSquared(mob.getLocation()) <= calls.leashBlocks() * calls.leashBlocks();
        if (near) f.farSince = 0;
        else if (f.farSince == 0) f.farSince = now;
        String why = null;
        if (now - f.started > calls.fightSeconds() * 1000L) why = "the fight ran too long";
        else if (f.farSince != 0 && now - f.farSince > calls.leashSeconds() * 1000L) why = "you left the water";
        if (why != null) { retreat(mob, f, why); return; }
        if (near && mob instanceof Mob m && (m.getTarget() == null || !m.getTarget().getUniqueId().equals(f.owner))) m.setTarget(p);
    }

    /** The creature leaves without being defeated (its thread): it is removed, and its lure goes back to its owner. */
    private void retreat(LivingEntity mob, Fight f, String why) {
        if (!f.over.compareAndSet(false, true)) return;
        end(f);
        Location at = mob.getLocation();
        at.getWorld().spawnParticle(Particle.SPLASH, at, 40, 0.6, 0.3, 0.6, 0.15);
        at.getWorld().playSound(at, Sound.ENTITY_GENERIC_SPLASH, 1.0f, 1.2f);
        mob.remove();
        giveBack(f, why);
    }

    private void end(Fight f) {
        fights.remove(f.entity);
        fightOf.remove(f.owner, f.entity);
        out.decrementAndGet();
        ScheduledTask t = f.task;
        if (t != null) t.cancel();
    }

    /** The lure comes back on the owner's thread; if they are offline, their next join gives it back (the journal's fight). */
    private void giveBack(Fight f, String why) {
        Player p = Bukkit.getPlayer(f.owner);
        if (p == null) return;
        p.getScheduler().run(plugin, t -> refund(p, f.entity, why), null);
    }

    private void refund(Player p, UUID entity, String why) {
        FishingJournal j = journal(p);
        FishingJournal.Active a = j.active();
        if (a == null || !j.refund(entity)) return;
        save(p, j);
        refunds.increment();
        FishingRules.Creature cr = rules().creature(a.creature());
        String name = cr == null ? a.creature() : cr.name();
        hint(p, "§7The " + name + " slipped back into the water · its encounter is yours again", 3000);
        p.sendMessage("§7The " + name + " slipped back into the water (" + why + "). Its encounter is yours again.");
    }

    // ── fights: who may hit whom ─────────────────────────────────

    /**
     * A hit nobody is allowed to land (VirtualHealthListener.setDamageGuard): a called creature is hurt only by its owner (or
     * their pet, arrow or spell), and hurts no other player. Any thread.
     */
    public boolean guard(EntityDamageByEntityEvent e) {
        if (fights.isEmpty()) return false;
        Fight victim = fights.get(e.getEntity().getUniqueId());
        if (victim != null) {
            Player behind = Manager.PvpTag.playerBehind(e.getDamager());
            return behind == null || !behind.getUniqueId().equals(victim.owner);
        }
        if (e.getEntity() instanceof Player hurt) {
            Entity d = e.getDamager();
            Entity source = d instanceof Projectile pr && pr.getShooter() instanceof Entity s ? s : d;
            Fight attacker = fights.get(source.getUniqueId());
            return attacker != null && !attacker.owner.equals(hurt.getUniqueId());
        }
        return false;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onTarget(EntityTargetEvent e) {
        if (fights.isEmpty()) return;
        Fight f = fights.get(e.getEntity().getUniqueId());
        if (f != null && e.getTarget() != null && !e.getTarget().getUniqueId().equals(f.owner)) e.setCancelled(true);
    }

    /** A creature's own drops replace vanilla's; it gives the usual experience orbs. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDeathDrops(EntityDeathEvent e) {
        if (fights.isEmpty() || !fights.containsKey(e.getEntity().getUniqueId())) return;
        e.getDrops().clear();
    }

    /** A creature defeated (its thread): its drops for its owner alone; the rest on the owner's thread. */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onDeath(EntityDeathEvent e) {
        if (fights.isEmpty()) return;
        LivingEntity mob = e.getEntity();
        Fight f = fights.get(mob.getUniqueId());
        if (f == null || e.isCancelled() || !f.over.compareAndSet(false, true)) return;
        end(f);
        Player killer = mob.getKiller();
        if (killer == null || !killer.getUniqueId().equals(f.owner)) { giveBack(f, "it was not your blow"); return; }
        FishingRules r = rules();
        FishingRules.Creature cr = r.creature(f.creature);
        if (cr != null) {
            int rolls = 1 + (f.elite ? r.techniques().eliteDropRolls() : 0);
            ThreadLocalRandom rnd = ThreadLocalRandom.current();
            Location at = mob.getLocation();
            for (int i = 0; i < rolls; i++) for (FishingRules.Drop d : cr.drops()) {
                if (rnd.nextDouble() >= d.chance()) continue;
                int n = d.min() + (d.max() > d.min() ? rnd.nextInt(d.max() - d.min() + 1) : 0);
                ItemStack it = dropStack(d.item(), n);
                if (it == null) continue;
                Item drop = at.getWorld().dropItemNaturally(at, it);
                drop.setOwner(f.owner); // its owner's alone
            }
        }
        // The win is recorded on the owner's thread; if they leave before that runs, their next join records it (never a refund).
        UUID dead = mob.getUniqueId();
        wins.put(dead, f);
        killer.getScheduler().run(plugin, t -> defeated(killer, f, dead), null);
    }

    private ItemStack dropStack(String id, int n) {
        GatheringCatalog.ItemDef def = service.catalog().item(id);
        if (def != null) return GearItems.makeItem(def, n);
        Material m = GatheringCatalog.material(id);
        return m == null || !m.isItem() ? null : new ItemStack(m, Math.max(1, Math.min(m.getMaxStackSize(), n)));
    }

    /** The owner's side of a win: the fight ends (its lure stays spent), Fishing XP, a first defeat is a discovery. */
    private void defeated(Player p, Fight f, UUID entity) {
        if (wins.remove(entity) == null) return; // recorded once, here or at the owner's next join
        FishingRules r = rules();
        FishingJournal j = journal(p);
        if (!j.finish(entity)) return;
        won.increment();
        FishingRules.Creature cr = r.creature(f.creature);
        boolean pays = XpIntegrity.earns(p); // an owner who switched to creative mid-fight: the win counts, it pays nothing
        boolean first = pays && cr != null && j.defeat(cr.id());
        save(p, j);
        if (cr == null || !pays) return;
        var cfg = plugin.getAttributesConfig() != null ? plugin.getAttributesConfig().killXp() : null;
        int fishing = skills.getSkillLevel(p, r.skill());
        double xp = cfg == null ? 0 : Attributes.KillXp.compute(f.level, cr.xpMultiplier(), fishing, cfg) * cr.fishingShare()
                * (f.elite ? r.techniques().eliteXp() : 1.0);
        double disc = first ? (cr.discoveryXp() > 0 ? cr.discoveryXp() : r.xp().discovery(cr.rarity())) : 0;
        if (xp + disc > 0) {
            j.countXp(skills.addXpCounted(r.skill(), xp + disc, p));
            save(p, j);
        }
        String color = cr.rarity().color;
        if (first) {
            celebrate(p, new Celebration(Celebration.Kind.DISCOVERY, Celebration.Tier.MAJOR,
                    color + "✦ Defeated: " + cr.name() + " §7· §a+" + Math.round(xp + disc) + " " + SkillNames.display(r.skill()) + " XP",
                    color + "✦ " + cr.name(), color + cr.name(), "§7defeated for the first time",
                    List.of(color + "✦ First defeat: " + cr.name() + " §7(+" + Math.round(disc) + " discovery XP; "
                            + j.creaturesDefeated().size() + " of " + r.creatures().size() + " creatures)"), -1));
        } else {
            hint(p, color + "✦ " + (f.elite ? "Elite " : "") + cr.name() + " defeated §7· §a+" + Math.round(xp) + " " + SkillNames.display(r.skill()) + " XP", 3000);
        }
        p.playSound(p.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 0.6f, 1.2f);
    }

    /**
     * A bobber gone (its thread) with its cast still open: the player switched items, died, went too far, or its chunk
     * unloaded. That cast is over: its bait goes back, on the player's thread. (A catch or a reel-in closed it first.)
     */
    private void hookGone(FishHook h) {
        UUID id = h.getUniqueId();
        Cast c = casts.remove(id);
        if (c == null) return;
        outcomes.remove(id);
        castOf.remove(c.player, id);
        c.biteLive = false;
        Player p = Bukkit.getPlayer(c.player);
        Tackle tk = tackle;
        if (p != null && tk != null) p.getScheduler().run(plugin, t -> tk.castEnded(p, id), null);
    }

    /** Removed without dying (its thread): despawned, unloaded or removed by anything but us. Its encounter goes back. */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onRemove(EntityRemoveEvent e) {
        if (e.getEntity() instanceof FishHook h) { if (!casts.isEmpty()) hookGone(h); return; }
        if (fights.isEmpty() || e.getCause() == EntityRemoveEvent.Cause.DEATH) return;
        Fight f = fights.get(e.getEntity().getUniqueId());
        if (f == null || !f.over.compareAndSet(false, true)) return;
        end(f);
        giveBack(f, "it was lost");
    }

    // ── the owner comes and goes ─────────────────────────────────

    /** Leaving ends the fight with the lure back now (saved with the player as they leave); the creature goes too. */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent e) {
        Player p = e.getPlayer();
        UUID id = p.getUniqueId();
        endFightNow(p, "you left");
        UUID hook = castOf.remove(id);
        if (hook != null) { casts.remove(hook); outcomes.remove(hook); }
        journals.remove(id);
        selected.remove(id);
        forced.remove(id);
        lastHint.remove(id);
        lastHintAt.remove(id);
        surveyed.remove(id);
    }

    /** Dying ends the fight: the creature returns to the water and its lure to you (try again when ready). */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerDeath(PlayerDeathEvent e) {
        endFightNow(e.getEntity(), "you fell");
    }

    private void endFightNow(Player p, String why) {
        UUID ent = fightOf.get(p.getUniqueId());
        Fight f = ent == null ? null : fights.get(ent);
        if (f == null || !f.over.compareAndSet(false, true)) return;
        end(f);
        refund(p, f.entity, why);
        LivingEntity mob = f.mob;
        if (mob != null) mob.getScheduler().run(plugin, t -> mob.remove(), null);
    }

    /**
     * A fight still on the journal at join: won as its owner left (the win is recorded now), or ended with the server (a
     * restart or a crash: its lure comes back).
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        FishingJournal j = journal(p);
        FishingJournal.Active a = j.active();
        if (a == null || fights.containsKey(a.entity())) return;
        Fight won = wins.get(a.entity());
        if (won != null) defeated(p, won, a.entity());
        else refund(p, a.entity(), "the server restarted");
    }

    // ── presentation ─────────────────────────────────────────────

    private void hint(Player p, String msg, long ms) {
        Manager.PlayerHUDManager hud = plugin.getPlayerHUDManager();
        if (hud != null) hud.flash(p, msg, ms);
    }

    private void celebrate(Player p, Celebration c) {
        Skills.Celebrations cel = plugin.getCelebrations();
        if (cel != null) cel.celebrate(p, c);
        else p.sendMessage(c.chat().isEmpty() ? c.hud() : c.chat().get(0));
    }

    // ── admin ────────────────────────────────────────────────────

    /** Counters for /cerb fish stats. */
    public List<String> stats() {
        List<String> lines = new ArrayList<>();
        lines.add("casts " + castCount.sum() + ", bites " + bites.sum() + ", catches " + catches.sum() + " (species " + speciesCaught.sum()
                + ", treasure " + treasureCaught.sum() + ", extra fish " + extras.sum() + ", discoveries " + discoveries.sum() + ")");
        lines.add("encounters banked " + luresEarned.sum() + ", calls " + callsMade.sum() + ", won " + won.sum() + ", encounters given back " + refunds.sum()
                + ", calls refused " + refused.sum());
        lines.add("creatures out now " + fights.size() + " (most " + rules().calls().server() + "), casts tracked " + casts.size()
                + ", auto-reeled " + autoReeled.sum());
        Tackle tk = tackle;
        if (tk != null) lines.addAll(tk.stats());
        return lines;
    }

    /** The creature fight {@code owner} is in, as "creature id" (for the admin and the harness), or null. */
    public String fightOf(UUID owner) {
        UUID e = fightOf.get(owner);
        Fight f = e == null ? null : fights.get(e);
        return f == null ? null : f.creature + " level " + f.level + (f.elite ? " (elite)" : "") + " " + f.entity;
    }

    public int fightsOut() { return fights.size(); }

    /** Registers the sea creatures in the mob registry (by id; mobs.yml may override). Startup and reload. */
    public void registerCreatures(MobRegistry registry) {
        if (registry == null) return;
        Map<String, Manager.Tier> tiers = new java.util.LinkedHashMap<>();
        Map<String, Double> xp = new java.util.LinkedHashMap<>();
        Map<String, EntityType> types = new java.util.LinkedHashMap<>();
        for (FishingRules.Creature c : rules().creatures().values()) {
            tiers.put(c.id(), Manager.Tier.fromString(c.tier()));
            xp.put(c.id(), c.xpMultiplier());
            try { types.put(c.id(), EntityType.valueOf(c.entity())); } catch (IllegalArgumentException ignored) { /* refused at load */ }
        }
        registry.setCatalogEntries(tiers, xp, types);
    }
}
