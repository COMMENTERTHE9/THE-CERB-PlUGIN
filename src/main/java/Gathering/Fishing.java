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
 * personal bests, banked lures, and the sea creatures their owners call and fight. Rules are {@link FishingRules}
 * (resources.yml); each player's record is a {@link FishingJournal} saved on the player.
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

    /** A cast in progress, by bobber: who cast it and their numbers then, a call, and what the current bite is. */
    private static final class Cast {
        final UUID player;
        final int level;
        final double treasure, creature, fortune;
        final String call;     // a creature id when this cast calls one
        final boolean chosen;  // the player chose that creature; otherwise one of the bobber's water is preferred
        volatile String water;
        volatile FishingRules.Species pending;
        volatile boolean rolled, calledOut;

        Cast(UUID player, int level, double treasure, double creature, double fortune, String call, boolean chosen) {
            this.player = player;
            this.level = level;
            this.treasure = treasure;
            this.creature = creature;
            this.fortune = fortune;
            this.call = call;
            this.chosen = chosen;
        }
    }

    /** What a catch was decided to be (at HIGH), paid at MONITOR when the catch really happened. */
    private record Outcome(Kind kind, FishingRules.Species species, FishingRules.Treasure treasure, double kg, double xp,
                           String water, int level, Cast cast, Force force) {}

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
    private final Map<UUID, FishingJournal> journals = new ConcurrentHashMap<>();
    private final Map<UUID, String> selected = new ConcurrentHashMap<>();
    private final Map<UUID, Force> forced = new ConcurrentHashMap<>();

    private final LongAdder castCount = new LongAdder(), bites = new LongAdder(), catches = new LongAdder(), speciesCaught = new LongAdder(),
            treasureCaught = new LongAdder(), luresEarned = new LongAdder(), callsMade = new LongAdder(), won = new LongAdder(),
            refunds = new LongAdder(), refused = new LongAdder(), discoveries = new LongAdder(), extras = new LongAdder();

    public Fishing(CerberusPlugin plugin, GatheringService service, SkillManager skills) {
        this.plugin = plugin;
        this.service = service;
        this.skills = skills;
    }

    private FishingRules rules() { return service.catalog().fishing(); }

    // ── the journal ───────────────────────────────────────────────

    /** {@code p}'s journal, read from the player once and kept while they are online. Player's thread. */
    public FishingJournal journal(Player p) {
        return journals.computeIfAbsent(p.getUniqueId(),
                id -> FishingJournal.decode(p.getPersistentDataContainer().get(JOURNAL, PersistentDataType.STRING)));
    }

    private static void save(Player p, FishingJournal j) {
        p.getPersistentDataContainer().set(JOURNAL, PersistentDataType.STRING, j.encode());
    }

    /** The lure {@code p} chose to call next ({@code /fishing call}, the Fishing page), if they still have one. */
    public String selected(Player p) {
        String s = selected.get(p.getUniqueId());
        return s != null && journal(p).lures(s) > 0 ? s : null;
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
        castCount.increment();
        int level = skills.getSkillLevel(p, r.skill());
        Manager.SecondaryEffects sec = plugin.getSecondaryEffects();
        double lure = sec == null ? 0 : sec.active(id, Stat.LURE_SPEED);
        double treasure = sec == null ? 0 : sec.active(id, Stat.TREASURE_CHANCE);
        double creature = sec == null ? 0 : sec.active(id, Stat.SEA_CREATURE_CHANCE);
        double fortune = service.bonusChance(id, Yield.FISH);
        String call = null;
        boolean chosen = false;
        if (p.isSneaking()) {
            FishingJournal j = journal(p);
            call = callFor(p, j);
            if (call != null) {
                chosen = selected(p) != null;
                FishingRules.Creature c = r.creature(call);
                hint(p, chosen ? "§b✦ Calling: " + c.name() + " §7(" + j.lures(call) + " lure" + (j.lures(call) == 1 ? "" : "s") + ") · wait for the bite"
                        : "§b✦ Calling a creature of these waters §7(" + j.totalLures() + " lure" + (j.totalLures() == 1 ? "" : "s") + ") · wait for the bite", 2500);
                hook.setWaitTime(30, 60); // the call answers in two to three seconds
            }
        }
        if (call == null) {
            double f = r.waitFactor(lure, level);
            if (f < 1.0) {
                int min = Math.max(1, (int) Math.round(hook.getMinWaitTime() * f));
                int max = Math.max(min, (int) Math.round(hook.getMaxWaitTime() * f));
                hook.setWaitTime(min, max);
            }
        }
        UUID old = castOf.put(id, hook.getUniqueId());
        if (old != null && !old.equals(hook.getUniqueId())) { casts.remove(old); outcomes.remove(old); } // a bobber gone without an event
        casts.put(hook.getUniqueId(), new Cast(id, level, treasure, creature, fortune, call, chosen));
    }

    /** A bite (the bobber's thread): a call surfaces its creature; otherwise the bite's species is rolled and a rare one shows. */
    private void bite(PlayerFishEvent e, FishingRules r) {
        FishHook hook = e.getHook();
        Cast c = casts.get(hook.getUniqueId());
        if (c == null) return;
        bites.increment();
        if (c.call != null && !c.calledOut && summon(hook, r, c)) return;
        Location at = hook.getLocation();
        FishingRules.Moment m = moment(r, at);
        c.water = m.water();
        Force f = forced.get(c.player);
        ThreadLocalRandom rnd = ThreadLocalRandom.current();
        FishingRules.Species s = f != null && "species".equals(f.kind()) ? r.speciesById(f.id())
                : r.rollSpecies(m, c.level, rnd.nextDouble(), rnd.nextDouble());
        c.pending = s;
        c.rolled = true;
        if (s != null && s.rarity().ordinal() >= FishingRules.Rarity.RARE.ordinal()) signal(at, s.rarity());
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
            o = new Outcome(Kind.VANILLA_TREASURE, null, null, 0, x.treasure(), water, level, c, f);
        } else if (forceTreasure || (r.treasureSense(level) && hook.isInOpenWater() && rnd.nextDouble() < treasureChance)) {
            FishingRules.Treasure t = forceTreasure && f.id() != null ? r.treasureById(f.id()) : r.rollTreasure(level, rnd.nextDouble());
            GatheringCatalog.ItemDef def = t == null ? null : service.catalog().item(t.item());
            if (def == null) o = new Outcome(kind, null, null, 0, kind == Kind.FISH ? x.fish() : x.junk(), water, level, c, f);
            else {
                item.setItemStack(GearItems.makeItem(def, 1));
                o = new Outcome(Kind.TREASURE, null, t, 0, t.xp() >= 0 ? t.xp() : x.of(t.rarity()), water, level, c, f);
            }
        } else {
            FishingRules.Species s = forceSpecies ? r.speciesById(f.id())
                    : c != null && c.rolled ? c.pending
                    : r.rollSpecies(new FishingRules.Moment(water, m.biome(), m.night(), m.rain(), m.thunder()), level, rnd.nextDouble(), rnd.nextDouble());
            GatheringCatalog.ItemDef def = s == null ? null : service.catalog().item(s.item());
            if (def == null) o = new Outcome(kind, null, null, 0, kind == Kind.FISH ? x.fish() : x.junk(), water, level, c, f);
            else {
                double kg = FishingRules.kg(s, rnd.nextDouble());
                item.setItemStack(GearItems.makeItem(def, 1));
                o = new Outcome(Kind.SPECIES, s, null, kg, s.xp() >= 0 ? s.xp() : x.of(s.rarity()), water, level, c, f);
            }
        }
        outcomes.put(hook.getUniqueId(), o);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onFished(PlayerFishEvent e) {
        UUID hook = e.getHook().getUniqueId();
        UUID who = e.getPlayer().getUniqueId();
        switch (e.getState()) {
            case CAUGHT_FISH -> {
                Outcome o = outcomes.remove(hook);
                Cast c = casts.remove(hook);
                castOf.remove(who, hook);
                if (o != null && !e.isCancelled() && e.getCaught() instanceof Item item) pay(e.getPlayer(), o, item, c);
            }
            case FISHING -> { if (e.isCancelled()) { casts.remove(hook); castOf.remove(who, hook); } }
            case REEL_IN, FAILED_ATTEMPT, IN_GROUND, CAUGHT_ENTITY -> { casts.remove(hook); outcomes.remove(hook); castOf.remove(who, hook); }
            default -> { }
        }
    }

    /** A catch that really happened (player's thread): XP, the journal, Fortune's extra fish, and maybe a lure. */
    private void pay(Player p, Outcome o, Item item, Cast c) {
        FishingRules r = rules();
        FishingJournal j = journal(p);
        boolean changed = false;
        double xp = o.xp();
        catches.increment();
        String line = null;
        switch (o.kind()) {
            case SPECIES -> {
                speciesCaught.increment();
                FishingRules.Species s = o.species();
                String name = itemName(s.item());
                FishingJournal.Record rec = j.record(s.id(), o.kg());
                changed |= rec != FishingJournal.Record.NONE;
                line = s.rarity().color + "◆ " + name + " §7" + kg(o.kg());
                if (rec == FishingJournal.Record.DISCOVERY) {
                    double bonus = r.xp().discovery(s.rarity());
                    xp += bonus;
                    discoveries.increment();
                    discovered(p, name, s.rarity(), bonus, j, r, "species");
                } else if (rec == FishingJournal.Record.PERSONAL_BEST && s.rarity().ordinal() >= FishingRules.Rarity.UNCOMMON.ordinal()) {
                    line += " §6★ personal best";
                }
            }
            case TREASURE -> {
                treasureCaught.increment();
                FishingRules.Treasure t = o.treasure();
                String name = itemName(t.item());
                line = t.rarity().color + "✦ Treasure: " + name;
                if (j.record(t.id(), 0) == FishingJournal.Record.DISCOVERY) {
                    changed = true;
                    double bonus = r.xp().discovery(t.rarity());
                    xp += bonus;
                    discoveries.increment();
                    discovered(p, name, t.rarity(), bonus, j, r, "treasure");
                }
            }
            default -> { }
        }
        // Fortune (fish): one more of the same fish, never of treasure or junk; from Twin Catch it can come twice.
        if ((o.kind() == Kind.SPECIES || o.kind() == Kind.FISH) && c != null && c.fortune > 0) {
            int extra = 0;
            ThreadLocalRandom rnd = ThreadLocalRandom.current();
            if (rnd.nextDouble() < c.fortune) {
                extra++;
                if (r.twinCatch(o.level()) && rnd.nextDouble() < c.fortune) extra++;
            }
            for (int i = 0; i < extra; i++) {
                ItemStack one = item.getItemStack().clone();
                one.setAmount(1);
                Item more = item.getWorld().dropItem(item.getLocation(), one);
                more.setVelocity(item.getVelocity());
                more.setPickupDelay(0); // as the catch itself: dropItem's half-second delay let it fly past the player
                if (item.getOwner() != null) more.setOwner(item.getOwner());
                extras.increment();
            }
            if (extra > 0) {
                p.playSound(p.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.35f, 1.8f);
                line = (line == null ? "§f◆ Fish" : line) + " §a+" + extra;
            }
        }
        if (line != null) hint(p, line, 1800);
        if (xp > 0) skills.addXpAndCheckLevelUp(r.skill(), xp, p);
        changed |= lures(p, o, j, r, c);
        if (changed) save(p, j);
    }

    /** The lure rolls after a catch: Something Stirs' first lure, the sea-creature chance, the apex. True when one was banked. */
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
        luresEarned.increment();
        int have = j.lures(got.id());
        String rarity = got.rarity().color;
        if (first || got.apex()) {
            celebrate(p, new Celebration(Celebration.Kind.DISCOVERY, Celebration.Tier.MAJOR,
                    rarity + "✦ " + (got.apex() ? "An apex took your bait: " : "Something stirs: ") + got.name(), rarity + "✦ " + got.name(),
                    rarity + (got.apex() ? "Apex: " : "") + got.name(), "§7took your bait and slipped away · Sneak and cast to call it",
                    List.of(rarity + "✦ " + got.name() + " §7took your bait and slipped away. Its lure is yours (" + have + "): "
                            + "§fSneak and cast§7 into water to call it and fight it."), -1));
        } else {
            hint(p, rarity + "✦ " + got.name() + " §7took your bait · lure kept (" + have + ") · §fSneak + cast§7 to call it", 3500);
            p.sendMessage(rarity + "✦ " + got.name() + " §7took your bait and slipped away. Lure kept (" + have + "). Sneak and cast to call it.");
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
     * thread that owns the player and the bobber. False (nothing spent) when it can't happen now.
     */
    private boolean summon(FishHook hook, FishingRules r, Cast c) {
        Player p = Bukkit.getPlayer(c.player);
        if (p == null || !Bukkit.isOwnedByCurrentRegion(p) || !Bukkit.isOwnedByCurrentRegion(hook.getLocation())) return false;
        FishingJournal j = journal(p);
        FishingRules.Creature cr = r.creature(c.chosen ? c.call : r.lureFor(j.lures().keySet(), moment(r, hook.getLocation()).water(), c.call));
        if (cr == null || j.lures(cr.id()) <= 0) return false;
        boolean busy = j.active() != null || fightOf.containsKey(p.getUniqueId()); // one creature at a time (the journal holds one fight)
        if (busy || fightOf.size() >= r.calls().server()) {
            refused.increment();
            hint(p, busy ? "§cFinish your fight first: one creature at a time."
                    : "§cThe waters are crowded: too many creatures are out on the server. Try again soon.", 2500);
            return false;
        }
        EntityType type;
        try { type = EntityType.valueOf(cr.entity()); } catch (IllegalArgumentException ex) { return false; }
        Class<? extends Entity> cls = type.getEntityClass();
        if (cls == null || !LivingEntity.class.isAssignableFrom(cls)) return false;
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
        if (!mob.isValid() || !j.spend(cr.id(), mob.getUniqueId(), now)) { mob.remove(); return false; }
        save(p, j);
        c.calledOut = true;
        Fight f = new Fight(owner, mob.getUniqueId(), cr.id(), elite, level, now);
        f.mob = mob;
        fights.put(mob.getUniqueId(), f);
        fightOf.put(owner, mob.getUniqueId());
        if (mob instanceof Mob m) m.setTarget(p);
        f.task = mob.getScheduler().runAtFixedRate(plugin, t -> tick(mob, f), () -> { }, 20L, 20L);
        callsMade.increment();
        World w = at.getWorld();
        w.spawnParticle(Particle.SPLASH, at, 60, 0.8, 0.4, 0.8, 0.2);
        w.playSound(at, Sound.ENTITY_GENERIC_SPLASH, 1.2f, 0.6f);
        w.playSound(at, Sound.ENTITY_ELDER_GUARDIAN_AMBIENT_LAND, 0.6f, 1.4f);
        String color = cr.rarity().color;
        p.sendTitle(color + (elite ? "Elite " : "") + cr.name(), "§7Level " + level + " · it surfaces!", 5, 40, 10);
        p.sendMessage(color + "✦ " + (elite ? "Elite " : "") + cr.name() + " §7(level " + level + ") answers your call. Defeat it for its rewards; "
                + "if it gets away, its lure comes back.");
        hook.remove(); // no fish on this line: the bite was the creature
        return true;
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
        hint(p, "§7The " + name + " slipped back into the water · its lure is yours again", 3000);
        p.sendMessage("§7The " + name + " slipped back into the water (" + why + "). Its lure is yours again.");
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
        boolean first = cr != null && j.defeat(cr.id());
        save(p, j);
        if (cr == null) return;
        var cfg = plugin.getAttributesConfig() != null ? plugin.getAttributesConfig().killXp() : null;
        int fishing = skills.getSkillLevel(p, r.skill());
        double xp = cfg == null ? 0 : Attributes.KillXp.compute(f.level, cr.xpMultiplier(), fishing, cfg) * cr.fishingShare()
                * (f.elite ? r.techniques().eliteXp() : 1.0);
        double disc = first ? (cr.discoveryXp() > 0 ? cr.discoveryXp() : r.xp().discovery(cr.rarity())) : 0;
        if (xp + disc > 0) skills.addXpAndCheckLevelUp(r.skill(), xp + disc, p);
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

    /** Removed without dying (its thread): despawned, unloaded or removed by anything but us. Its lure goes back. */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onRemove(EntityRemoveEvent e) {
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
        List<String> out = new ArrayList<>();
        out.add("casts " + castCount.sum() + ", bites " + bites.sum() + ", catches " + catches.sum() + " (species " + speciesCaught.sum()
                + ", treasure " + treasureCaught.sum() + ", extra fish " + extras.sum() + ", discoveries " + discoveries.sum() + ")");
        out.add("lures earned " + luresEarned.sum() + ", calls " + callsMade.sum() + ", won " + won.sum() + ", lures given back " + refunds.sum()
                + ", calls refused " + refused.sum());
        out.add("creatures out now " + fights.size() + " (most " + rules().calls().server() + "), casts tracked " + casts.size());
        return out;
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
