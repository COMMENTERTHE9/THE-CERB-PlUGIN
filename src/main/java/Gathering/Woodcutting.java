package Gathering;

import Skills.SkillManager;
import org.bukkit.Bukkit;
import org.bukkit.Effect;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.Tag;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.type.Leaves;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.LongAdder;
import java.util.function.BiConsumer;

/**
 * Woodcutting beyond one log (docs/design/WOODCUTTING.md): Felling a tree in steps, the canopy, heartwood,
 * discoveries and the rest between fellings, for trees the world made and trees grown from planted saplings (their share,
 * {@link WoodRules#xpShare}). A cut log's own XP and Fortune bonus are the gathering rules'
 * ({@link GatheringListener}); this adds what comes after the cut. Folia: a felling's steps run on the region thread that
 * owns the tree (the region scheduler at the stump), every block and the player are checked for ownership at every step,
 * and XP is awarded on the player's own thread.
 */
public final class Woodcutting implements Listener {

    /** Chunk-free per-player memory of the woods a player discovered (saved on the player, no database). */
    public static final NamespacedKey WOODS = new NamespacedKey("cerberus", "woods_discovered");
    /** Logs a felling takes per tick (other plugins' handlers then never run for a whole tree at once). */
    static final int LOGS_PER_TICK = 8;
    /** Break effects per felling: a few, never one per log. */
    static final int EFFECTS = 8;
    /** Leaves a canopy sweep takes in all, the blocks it may look at to find them, and the leaves it takes per tick. */
    static final int CANOPY_MAX = 400, CANOPY_VISITS = 2_000, CANOPY_PER_TICK = 50;
    /**
     * Blocks only builds have (docs/design/WOODCUTTING.md §10). A tree whose logs touch one is never felled: an old log
     * house or a village built against a natural tree has logs no placed-block memory knows, and must not come down
     * with it. A tree the world grew touches none of these; a refusal only means the tree is cut log by log.
     */
    static final Set<Material> BUILD_BLOCKS = buildBlocks();

    private static Set<Material> buildBlocks() {
        Set<Material> out = EnumSet.noneOf(Material.class);
        for (Tag<Material> t : List.of(Tag.PLANKS, Tag.STAIRS, Tag.SLABS, Tag.FENCES, Tag.FENCE_GATES, Tag.WALLS, Tag.DOORS, Tag.TRAPDOORS,
                Tag.BEDS, Tag.WOOL, Tag.WOOL_CARPETS, Tag.ALL_SIGNS, Tag.BANNERS)) out.addAll(t.getValues());
        for (Material m : Material.values()) {
            if (m.isLegacy() || !m.isBlock()) continue;
            String n = m.name();
            if (n.endsWith("GLASS") || n.endsWith("GLASS_PANE") || n.endsWith("TORCH") || n.endsWith("LANTERN") || n.endsWith("BRICKS")
                    || n.endsWith("CONCRETE")) out.add(m);
        }
        out.addAll(List.of(Material.LADDER, Material.CRAFTING_TABLE, Material.CHEST, Material.TRAPPED_CHEST, Material.BARREL,
                Material.FURNACE, Material.BOOKSHELF));
        return out;
    }

    /** A break this class makes for a felling or the canopy: protection plugins see it as the player's; the gathering listener ignores it. */
    public static final class FellingBreak extends BlockBreakEvent implements SweepBreak {
        public FellingBreak(Block block, Player player) { super(block, player); }
    }

    /** What a discovery or a felling has to say on the HUD and in chat (wired to the level-up presentation). */
    public interface Announcer {
        /** The first log of {@code wood} cut from a tree (never a placed one): {@code xp} paid once. */
        void discovered(Player p, GatheringCatalog.ResourceDef wood, double xp);

        /** Heartwood dropped. */
        void heartwood(Player p, GatheringCatalog.ResourceDef wood);
    }

    private final Plugin plugin;
    private final GatheringService gathering;
    private final SkillManager skills;
    private final BiConsumer<Player, String> notice;
    private volatile Announcer announcer;
    private final Map<UUID, Long> restUntil = new ConcurrentHashMap<>();
    /** Players with a felling running, and when it started (an entry older than a few seconds is stale). */
    private final Map<UUID, Long> felling = new ConcurrentHashMap<>();
    private final Map<UUID, Set<String>> discovered = new ConcurrentHashMap<>();
    /** When a player last saw each kind of hint ("axe", "weak", "felling", "placed", "grown"...), so one kind never silences another. */
    private final Map<String, Long> lastHint = new ConcurrentHashMap<>();
    private final LongAdder fellings = new LongAdder(), felledLogs = new LongAdder(), refusals = new LongAdder(),
            heartwoods = new LongAdder(), discoveries = new LongAdder(), canopyLeaves = new LongAdder(), scans = new LongAdder();

    public Woodcutting(Plugin plugin, GatheringService gathering, SkillManager skills, BiConsumer<Player, String> notice) {
        this.plugin = plugin;
        this.gathering = gathering;
        this.skills = skills;
        this.notice = notice;
    }

    public void setAnnouncer(Announcer a) { this.announcer = a; }

    // ── after a real cut (GatheringListener, MONITOR, the player's region thread) ──────────────

    /**
     * A player's own break of a wood's log, after the XP was paid: hints, heartwood, a discovery, and Felling when they
     * Sneak. {@code e} is still before the block is removed.
     */
    public void afterCut(Player p, GatheringService.Evaluation ev) {
        GatheringCatalog.ResourceDef r = ev.resource();
        if (!r.isTree() || ev.method() != GatherRules.Method.MANUAL) return;
        boolean hasAxe = ev.tool() != null && ev.tool().kinds().contains("axe");
        if (ev.placed()) {
            if (hasAxe && hintDue(p, "placed", 120_000)) notice.accept(p, placedHint(ev, r)); // the scan only when due
            return;
        }
        if (!ev.decision().xp()) {
            if (!hasAxe) hint(p, "axe", "§7Logs pay Woodcutting XP when cut with an axe.", 60_000);
            return;
        }
        GatheringCatalog c = gathering.catalog();
        discover(p, r); // any axe that pays at all finds a wood, on any tree that wasn't placed
        if (ev.weakTool()) { // this hint first: a grown tree's would replace it on the HUD at once
            String axe = GatheringCatalog.tierName(r.tier());
            hint(p, "weak", "§e" + r.displayName() + " §7pays in full with " + ("aeiou".indexOf(axe.charAt(0)) >= 0 ? "an" : "a") + " §f" + axe
                    + " axe §7or better.", 10_000);
            return;
        }
        int level = skills.getSkillLevel(p, r.skill());
        Block b = ev.block();
        boolean said = false; // a HUD line this cut already showed (each replaces the one before)
        if (rollHeartwood(c, r, level, ev.origin())) {
            ItemStack h = heartwoodItem(c);
            if (h != null) {
                b.getWorld().dropItemNaturally(b.getLocation().add(0.5, 0.5, 0.5), h);
                heartwoods.increment();
                Announcer a = announcer;
                if (a != null) { a.heartwood(p, r); said = true; }
            }
        }
        if (p.isSneaking()) said |= fell(p, ev, level);
        if (!said) grownHint(p, c.wood(), ev.origin()); // last, and only when nothing else is on the HUD: never wiped unseen
    }

    private boolean rollHeartwood(GatheringCatalog c, GatheringCatalog.ResourceDef r, int level, Origin origin) {
        double chance = c.wood().heartwoodChance(r.tree().heartwood(), level, origin);
        return chance > 0 && ThreadLocalRandom.current().nextDouble() < chance;
    }

    /**
     * Why a log that pays nothing does: a grown tree of a wood with its own drops; a tree grown before grown trees paid
     * (remembered as placed: {@link #grewBefore}); or a log placed by hand.
     */
    private String placedHint(GatheringService.Evaluation ev, GatheringCatalog.ResourceDef r) {
        if (ev.origin() == Origin.GROWN || ev.origin() == Origin.BONEMEAL) return "§7Trees of this wood pay only where the world grew them.";
        if (grewBefore(ev.block(), r)) {
            return "§7This tree grew before grown trees paid, so it is remembered as placed: no XP. Trees you plant now pay.";
        }
        return "§7Logs placed by hand pay no Woodcutting XP; trees grown from saplings do.";
    }

    /**
     * True when the placed log {@code b} looks like a tree grown before grown trees paid: with the memory's marks left
     * aside, its connected logs of that wood make a tree with its own (decaying) leaves and no build touching it, and
     * every one of those logs is remembered as placed (a log placed against a wild tree is not: the wild tree's logs
     * aren't). For the hint only (every 2 minutes at most); nothing pays by it. The block's region thread.
     */
    private boolean grewBefore(Block b, GatheringCatalog.ResourceDef r) {
        PlacedBlocks placed = gathering.placedBlocks();
        if (placed == null) return false;
        World w = b.getWorld();
        TreeScan.View all = new TreeScan.View() {
            private final TreeScan.View base = view(w, r);
            @Override public Material type(int x, int y, int z) { return base.type(x, y, z); }
            @Override public boolean naturalLeaf(int x, int y, int z) { return base.naturalLeaf(x, y, z); }
            @Override public boolean placed(int x, int y, int z) { return false; }
        };
        WoodRules rules = gathering.catalog().wood();
        TreeScan.Tree t = TreeScan.scan(all, b.getX(), b.getY(), b.getZ(), r.blocks(), r.tree().leaves(), BUILD_BLOCKS, rules.reach(), 64);
        if (t.size() < 2 || t.capped() || t.build() || !t.natural(rules.minLeaves())) return false;
        for (long pos : t.logs()) {
            if (placed.origin(w.getBlockAt(TreeScan.x(pos), TreeScan.y(pos), TreeScan.z(pos))) != Origin.PLACED) return false;
        }
        return true;
    }

    /** Now and then, what a tree grown from a planted sapling pays (so a smaller number is never a mystery). */
    private void grownHint(Player p, WoodRules w, Origin origin) {
        if (origin == Origin.GROWN) {
            hint(p, "grown", "§7Trees grown from saplings pay §f" + percent(w.grown().xp()) + "%§7 of a wild tree's Woodcutting XP.", 300_000);
        } else if (origin == Origin.BONEMEAL) {
            hint(p, "bonemeal", "§7Trees grown with bone meal pay §f" + percent(w.boneMeal().xp()) + "%§7 of a wild tree's XP"
                    + (w.boneMeal().heartwood() > 0 ? "" : " and no heartwood") + "; left to grow, §f" + percent(w.grown().xp()) + "%§7.", 300_000);
        }
    }

    private static long percent(double share) { return Math.round(share * 100); }

    private static ItemStack heartwoodItem(GatheringCatalog c) {
        GatheringCatalog.ItemDef d = c.item(c.wood().heartwoodItem());
        return d == null ? null : GearItems.makeItem(d, 1);
    }

    /** Show {@code text} unless this kind was shown in the last {@code everyMs}; true when shown. */
    private boolean hint(Player p, String kind, String text, long everyMs) {
        if (!hintDue(p, kind, everyMs)) return false;
        notice.accept(p, text);
        return true;
    }

    /** True (and noted) when {@code p} hasn't seen a hint of this kind in {@code everyMs}. */
    private boolean hintDue(Player p, String kind, long everyMs) {
        long now = System.currentTimeMillis();
        String key = p.getUniqueId() + ":" + kind;
        Long last = lastHint.get(key);
        if (last != null && now - last < everyMs) return false;
        lastHint.put(key, now);
        return true;
    }

    // ── discoveries ──────────────────────────────────────────────

    /** The woods {@code p} has discovered (their ids), read from the player once and kept while they are online. Player's thread. */
    public Set<String> discovered(Player p) {
        return discovered.computeIfAbsent(p.getUniqueId(), id -> {
            String saved = p.getPersistentDataContainer().get(WOODS, PersistentDataType.STRING);
            Set<String> out = ConcurrentHashMap.newKeySet();
            if (saved != null) for (String s : saved.split(",")) if (!s.isBlank()) out.add(s.trim());
            return out;
        });
    }

    private void discover(Player p, GatheringCatalog.ResourceDef r) {
        if (!skills.hasLoadedSkills(p.getUniqueId())) return; // its XP would be lost: the next log discovers it instead
        Set<String> have = discovered(p);
        if (!have.add(r.id())) return;
        p.getPersistentDataContainer().set(WOODS, PersistentDataType.STRING, String.join(",", new LinkedHashSet<>(have)));
        discoveries.increment();
        double xp = r.tree().discoveryXp();
        if (xp > 0) gathering.award(p, r.skill(), xp);
        Announcer a = announcer;
        if (a != null) a.discovered(p, r, xp);
    }

    // ── Felling ──────────────────────────────────────────────────

    /** A felling in progress: the logs still to take (breadth-first from the cut), and what it has gathered so far. */
    private final class Felling {
        final Player p;
        final UUID id;
        final GatheringCatalog.ResourceDef r;
        final Material axe;
        final long[] logs;
        final World world;
        final Location stump;
        final int level;
        final boolean canopy;
        /** The Fortune bonus chance per log (0 with Silk Touch or a wood without the bonus), fixed when the felling starts. */
        final double bonusChance;
        int next = 1; // the cut itself (index 0) is the player's own break
        int effects;
        int taken;
        long started;

        Felling(Player p, GatheringCatalog.ResourceDef r, Material axe, long[] logs, World world, Location stump, int level, boolean canopy,
                double bonusChance) {
            this.p = p;
            this.id = p.getUniqueId();
            this.r = r;
            this.axe = axe;
            this.logs = logs;
            this.world = world;
            this.stump = stump;
            this.level = level;
            this.canopy = canopy;
            this.bonusChance = bonusChance;
        }
    }

    /** Fell the tree {@code ev}'s cut belongs to, if Felling may; true when the HUD said why it didn't. */
    private boolean fell(Player p, GatheringService.Evaluation ev, int level) {
        GatheringCatalog c = gathering.catalog();
        WoodRules w = c.wood();
        GatheringCatalog.ResourceDef r = ev.resource();
        int capacity = w.capacity(level);
        if (capacity <= 0) return false; // Felling isn't open yet: a sneaking cut is just a cut
        if (p.getGameMode() != org.bukkit.GameMode.SURVIVAL) return false; // adventure's own limits on breaking are never bypassed
        UUID id = p.getUniqueId();
        long now = System.currentTimeMillis();
        Long running = felling.get(id);
        if (running != null && now - running < 10_000) return false; // one felling at a time
        Long until = restUntil.get(id);
        if (until != null && until > now) {
            return refuse(p, "§7Felling is ready again in §f" + (until - now + 999) / 1000 + " s§7.");
        }
        Block b = ev.block();
        scans.increment();
        TreeScan.Tree t = TreeScan.scan(view(b.getWorld(), r), b.getX(), b.getY(), b.getZ(), r.blocks(), r.tree().leaves(), BUILD_BLOCKS,
                w.reach(), capacity + 1);
        if (t.capped()) { // first: a capped scan may not have reached the crown's leaves
            return refuse(p, "§7This tree has more than §f" + capacity + " §7logs: " + w.fellingName(level) + " takes up to §f" + capacity + "§7.");
        }
        if (!t.natural(w.minLeaves())) {
            return refuse(p, "§7Felling brings down only trees with their own leaves: this one has none. Cut it log by log.");
        }
        if (t.build()) {
            return refuse(p, "§7This tree touches a build: Felling leaves it standing. Cut it log by log.");
        }
        if (t.baseWidth() > r.tree().trunk()) {
            return refuse(p, "§7This tree's base is wider than a trunk (logs built against it, or trees grown together): cut it log by log.");
        }
        if (t.giant() && !w.giantTrunks(level)) {
            return refuse(p, "§7Giant trunks fall from " + Skills.SkillNames.display(r.skill()) + " §f" + w.giantAt() + "§7.");
        }
        if (t.size() <= 1) return false; // nothing above the cut
        double bonus = ev.decision().bonus() ? gathering.bonusChance(id, r.fortuneYield()) : 0;
        Felling f = new Felling(p, r, p.getInventory().getItemInMainHand().getType(), t.logs(), b.getWorld(),
                b.getLocation().add(0.5, 0.5, 0.5), level, w.canopy(level), bonus);
        felling.put(id, now);
        f.started = now;
        restUntil.put(id, now + w.restMs(level, t.size())); // set again when it ends, by what it really took
        fellings.increment();
        b.getWorld().playSound(f.stump, Sound.ENTITY_ZOMBIE_BREAK_WOODEN_DOOR, SoundCategory.BLOCKS, 0.45f, 0.6f);
        Bukkit.getRegionScheduler().runDelayed(plugin, f.stump, task -> step(f), 1L);
        return false;
    }

    /** A felling refused: counted, and said on the HUD (true when shown). */
    private boolean refuse(Player p, String text) {
        refusals.increment();
        return hint(p, "felling", text, 1_500);
    }

    /**
     * One step of a felling: up to {@link #LOGS_PER_TICK} logs, their drops at the stump and their XP to the player, then
     * the next step or the end. The tree's region thread.
     */
    private void step(Felling f) {
        boolean go = f.p.isOnline() && Bukkit.isOwnedByCurrentRegion(f.p);
        ItemStack hand = go ? f.p.getInventory().getItemInMainHand() : null;
        if (hand == null || hand.getType() != f.axe) go = false; // the axe left the hand, or the player left
        List<ItemStack> drops = new ArrayList<>();
        GatheringCatalog c = gathering.catalog();
        PlacedBlocks placed = gathering.placedBlocks();
        int took = 0;
        double xp = 0;
        while (go && took < LOGS_PER_TICK && f.next < f.logs.length) {
            long pos = f.logs[f.next++];
            Block b = f.world.getBlockAt(TreeScan.x(pos), TreeScan.y(pos), TreeScan.z(pos));
            if (!Bukkit.isOwnedByCurrentRegion(b)) { go = false; break; } // past the region: the rest is cut by hand
            Material log = b.getType();
            if (!f.r.blocks().contains(log)) continue;                    // someone cut it meanwhile
            Origin origin = placed == null ? Origin.NATURAL : placed.origin(b);
            if (GatheringService.paysNothing(f.r, origin)) continue;     // placed meanwhile
            ItemStack axe = f.p.getInventory().getItemInMainHand();
            if (wouldBreak(axe)) { go = false; break; }
            if (!GatheringService.vanillaAllows(f.p, b)) { go = false; break; } // spawn protection, the world border
            FellingBreak e = new FellingBreak(b, f.p);
            Bukkit.getPluginManager().callEvent(e);
            if (e.isCancelled()) { go = false; break; }                   // a protection plugin said no: stop here
            if (e.isDropItems()) drops.addAll(b.getDrops(axe, f.p));
            if (f.effects < EFFECTS && took % 4 == 0) { f.world.playEffect(b.getLocation(), Effect.STEP_SOUND, log); f.effects++; }
            b.setType(Material.AIR, true);
            if (placed != null) placed.unmark(b);
            f.p.damageItemStack(EquipmentSlot.HAND, 1);
            took++;
            f.taken++;
            xp += GatherRules.xp(f.r.xp(), f.r.level(), f.level, c.weakGap(), c.weakFactor()) * c.wood().xpShare(origin);
            if (e.isDropItems() && f.bonusChance > 0 && ThreadLocalRandom.current().nextDouble() < f.bonusChance) {
                drops.add(new ItemStack(log)); // Fortune for wood: one more of the log, as a cut by hand gets
                gathering.countBonus();
            }
            if (rollHeartwood(c, f.r, f.level, origin)) {
                ItemStack h = heartwoodItem(c);
                if (h != null) {
                    drops.add(h);
                    heartwoods.increment();
                    Announcer a = announcer;
                    if (a != null) a.heartwood(f.p, f.r);
                }
            }
        }
        felledLogs.add(took);
        for (ItemStack it : merge(drops)) f.world.dropItemNaturally(f.stump, it);
        double paid = xp;
        String skill = f.r.skill();
        if (paid > 0 && f.p.isOnline()) f.p.getScheduler().run(plugin, t -> gathering.award(f.p, skill, paid), null);
        if (go && f.next < f.logs.length) {
            Bukkit.getRegionScheduler().runDelayed(plugin, f.stump, task -> step(f), 1L);
            return;
        }
        felling.remove(f.id);
        // The rest counts what came down (and the cut), not the tree it saw: a felling a claim or the axe stopped early
        // rests for what it took.
        restUntil.put(f.id, f.started + gathering.catalog().wood().restMs(f.level, f.taken + 1));
        if (f.canopy && f.taken > 0) {
            Bukkit.getRegionScheduler().runDelayed(plugin, f.stump, task -> canopy(f), 10L); // leaves settle a step a tick
        }
    }

    /** True when one more point of damage would break this item. */
    static boolean wouldBreak(ItemStack it) {
        if (it == null) return true;
        int max = it.getType().getMaxDurability();
        if (max <= 0 || !(it.getItemMeta() instanceof Damageable d)) return false;
        if (it.getItemMeta().isUnbreakable()) return false;
        return max - d.getDamage() <= 1;
    }

    /** Equal items merged into stacks (a felling's 40 logs land as one stack, not 40 entities). */
    static List<ItemStack> merge(List<ItemStack> in) {
        List<ItemStack> out = new ArrayList<>();
        for (ItemStack it : in) {
            if (it == null || it.getType().isAir() || it.getAmount() <= 0) continue;
            int left = it.getAmount();
            for (ItemStack o : out) {
                if (left <= 0) break;
                if (!o.isSimilar(it)) continue;
                int room = o.getMaxStackSize() - o.getAmount();
                int add = Math.min(room, left);
                if (add > 0) { o.setAmount(o.getAmount() + add); left -= add; }
            }
            while (left > 0) {
                ItemStack copy = it.clone();
                copy.setAmount(Math.min(left, it.getMaxStackSize()));
                left -= copy.getAmount();
                out.add(copy);
            }
        }
        return out;
    }

    // ── the canopy (level 75): the felled tree's leaves that would decay come down at once ──

    private void canopy(Felling f) {
        Set<Material> leaves = f.r.tree().leaves();
        TreeScan.Reach reach = gathering.catalog().wood().reach();
        int x0 = f.stump.getBlockX(), y0 = f.stump.getBlockY(), z0 = f.stump.getBlockZ();
        List<Block> sweep = new ArrayList<>();
        Set<Long> seen = new java.util.HashSet<>();
        java.util.ArrayDeque<Long> queue = new java.util.ArrayDeque<>();
        // Start from leaves next to where the logs were, then walk through leaves inside the box.
        for (long pos : f.logs) {
            int x = TreeScan.x(pos), y = TreeScan.y(pos), z = TreeScan.z(pos);
            for (int[] d : new int[][] {{1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}, {0, 0, 1}, {0, 0, -1}}) {
                long k = TreeScan.pack(x + d[0], y + d[1], z + d[2]);
                if (seen.add(k)) queue.add(k);
            }
        }
        int visits = 0;
        while (!queue.isEmpty() && sweep.size() < CANOPY_MAX && visits < CANOPY_VISITS) {
            long k = queue.poll();
            int x = TreeScan.x(k), y = TreeScan.y(k), z = TreeScan.z(k);
            if (Math.abs(x - x0) > reach.sideways() + 3 || Math.abs(z - z0) > reach.sideways() + 3 || y > y0 + reach.up() + 3 || y < y0 - reach.down()) continue;
            visits++;
            Block b = f.world.getBlockAt(x, y, z);
            if (!Bukkit.isOwnedByCurrentRegion(b) || !leaves.contains(b.getType())) continue;
            // Only leaves with no log within reach any more (vanilla would let them decay), and the walk goes only through
            // those: a neighbouring tree's crown, still held by its own logs, is never walked or taken.
            if (!(b.getBlockData() instanceof Leaves l) || l.isPersistent() || l.getDistance() < 7) continue;
            sweep.add(b);
            for (int[] d : new int[][] {{1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}, {0, 0, 1}, {0, 0, -1}}) {
                long n = TreeScan.pack(x + d[0], y + d[1], z + d[2]);
                if (seen.add(n)) queue.add(n);
            }
        }
        sweepStep(f, sweep, 0);
    }

    private void sweepStep(Felling f, List<Block> sweep, int from) {
        if (!f.p.isOnline() || !Bukkit.isOwnedByCurrentRegion(f.p)) return;
        List<ItemStack> drops = new ArrayList<>();
        int i = from;
        for (; i < sweep.size() && i < from + CANOPY_PER_TICK; i++) {
            Block b = sweep.get(i);
            if (!Bukkit.isOwnedByCurrentRegion(b) || !(b.getBlockData() instanceof Leaves l) || l.isPersistent()) continue;
            if (!GatheringService.vanillaAllows(f.p, b)) continue;
            FellingBreak e = new FellingBreak(b, f.p);
            Bukkit.getPluginManager().callEvent(e);
            if (e.isCancelled()) continue;
            if (e.isDropItems()) drops.addAll(b.getDrops()); // as if it decayed: saplings, sticks, apples by vanilla's odds
            b.setType(Material.AIR, true);
            canopyLeaves.increment();
        }
        for (ItemStack it : merge(drops)) f.world.dropItemNaturally(f.stump, it);
        if (i < sweep.size()) {
            int at = i;
            Bukkit.getRegionScheduler().runDelayed(plugin, f.stump, task -> sweepStep(f, sweep, at), 1L);
        }
    }

    // ── the world, as a tree scan reads it ───────────────────────

    /**
     * Reads only blocks this region thread owns in loaded chunks; anything else is "can't read" (an edge). A log of
     * {@code r} that pays nothing (placed by hand, moved) is no part of a tree; a grown tree's logs are.
     */
    private TreeScan.View view(World w, GatheringCatalog.ResourceDef r) {
        PlacedBlocks placed = gathering.placedBlocks();
        return new TreeScan.View() {
            @Override public Material type(int x, int y, int z) {
                if (y < w.getMinHeight() || y >= w.getMaxHeight()) return Material.AIR;
                if (!w.isChunkLoaded(x >> 4, z >> 4) || !Bukkit.isOwnedByCurrentRegion(w, x >> 4, z >> 4)) return null;
                return w.getBlockAt(x, y, z).getType();
            }

            @Override public boolean naturalLeaf(int x, int y, int z) {
                return !(w.getBlockAt(x, y, z).getBlockData() instanceof Leaves l) || !l.isPersistent();
            }

            @Override public boolean placed(int x, int y, int z) {
                return placed != null && GatheringService.paysNothing(r, placed.origin(w.getBlockAt(x, y, z)));
            }
        };
    }

    /** The tree the block {@code b} is, as Felling would see it (admin tool). The block's region thread. */
    public TreeScan.Tree inspect(Block b, GatheringCatalog.ResourceDef r, int maxLogs) {
        scans.increment();
        return TreeScan.scan(view(b.getWorld(), r), b.getX(), b.getY(), b.getZ(), r.blocks(), r.tree().leaves(), BUILD_BLOCKS,
                gathering.catalog().wood().reach(), maxLogs);
    }

    /** Milliseconds until {@code player} can fell again (0 when ready). */
    public long restLeft(UUID player) {
        Long until = restUntil.get(player);
        return until == null ? 0 : Math.max(0, until - System.currentTimeMillis());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent e) {
        UUID id = e.getPlayer().getUniqueId();
        discovered.remove(id);
        lastHint.keySet().removeIf(k -> k.startsWith(id + ":"));
        long now = System.currentTimeMillis();
        restUntil.values().removeIf(until -> until <= now); // a rest still running outlasts a relog; spent ones go
    }

    // Diagnostics (/cerb gather stats)
    public long fellings() { return fellings.sum(); }
    public long felledLogs() { return felledLogs.sum(); }
    public long refusals() { return refusals.sum(); }
    public long heartwoods() { return heartwoods.sum(); }
    public long discoveries() { return discoveries.sum(); }
    public long canopyLeaves() { return canopyLeaves.sum(); }
    public long scans() { return scans.sum(); }
}
