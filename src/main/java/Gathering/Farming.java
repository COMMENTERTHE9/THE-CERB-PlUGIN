package Gathering;

import Skills.SkillManager;
import org.bukkit.Bukkit;
import org.bukkit.Effect;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.Ageable;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.type.CaveVinesPlant;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockDropItemEvent;
import org.bukkit.event.player.PlayerHarvestBlockEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.Iterator;
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
 * Farming (docs/design/FARMING_COOKING.md §3): ripe harvests pay Farming XP, by breaking or by picking, with Fortune on
 * crops, discoveries, Replant (10), Wide Harvest (25) and crop quality (50). Crops planted by players are farming: only a
 * melon, pumpkin, cane or cactus placed by hand pays nothing ({@link PlacedBlocks}). A harvest with no player in it (water,
 * pistons, villagers, machines) keeps vanilla's drops and pays nobody. Folia: a harvest is handled on the region thread
 * that owns the block (the player's); a Wide Harvest's steps run on the region scheduler at the first crop, every crop and
 * the player are checked for ownership at every step, and XP is awarded on the player's own thread.
 */
public final class Farming implements Listener {

    /** The crops a player has discovered (their ids), saved on the player (no database). */
    public static final NamespacedKey CROPS = new NamespacedKey("cerberus", "crops_discovered");
    /** Farming's switches on the player: "noreplant" when Replant is off. */
    public static final NamespacedKey FLAGS = new NamespacedKey("cerberus", "farming_flags");
    /** Break effects per Wide Harvest: a few, never one per crop. */
    static final int EFFECTS = 6;
    /** Sugar cane or cactus segments above a broken one that are counted (a column is never taller in practice). */
    static final int COLUMN_MAX = 64;

    /** A break this class makes for a Wide Harvest: protection plugins see it as the player's own; the harvest listeners ignore it. */
    public static final class HarvestBreak extends BlockBreakEvent {
        public HarvestBreak(Block block, Player player) { super(block, player); }
    }

    /** What a discovery has to say (wired to the level-up presentation). */
    public interface Announcer {
        /** The first ripe harvest of {@code crop}: {@code xp} paid once. */
        void discovered(Player p, FarmingRules.Crop crop, double xp);
    }

    /**
     * A break harvest paid at MONITOR, waiting for its drops: {@code bonuses} extra produce (Fortune), the produce's quality,
     * and whether to replant.
     */
    private record Pending(Block block, FarmingRules.Crop crop, int bonuses, Quality quality, boolean replant, BlockData planted) {}

    private final Plugin plugin;
    private final GatheringService gathering;
    private final SkillManager skills;
    private final BiConsumer<Player, String> notice;
    private volatile Announcer announcer;
    private final Map<UUID, Pending> pending = new ConcurrentHashMap<>();
    /** Players with a Wide Harvest running, and when it started (an entry older than a few seconds is stale). */
    private final Map<UUID, Long> sweeping = new ConcurrentHashMap<>();
    private final Map<UUID, Set<String>> discovered = new ConcurrentHashMap<>();
    private final Map<UUID, Boolean> replantOff = new ConcurrentHashMap<>();
    /** When a player last saw each kind of hint, so one kind never silences another. */
    private final Map<String, Long> lastHint = new ConcurrentHashMap<>();
    private final LongAdder harvests = new LongAdder(), unripe = new LongAdder(), placedHarvests = new LongAdder(), bonuses = new LongAdder(),
            replants = new LongAdder(), noSeed = new LongAdder(), sweeps = new LongAdder(), swept = new LongAdder(), refusals = new LongAdder(),
            discoveries = new LongAdder(), picks = new LongAdder();
    private final LongAdder[] qualities = {new LongAdder(), new LongAdder(), new LongAdder(), new LongAdder()};

    public Farming(Plugin plugin, GatheringService gathering, SkillManager skills, BiConsumer<Player, String> notice) {
        this.plugin = plugin;
        this.gathering = gathering;
        this.skills = skills;
        this.notice = notice;
    }

    public void setAnnouncer(Announcer a) { this.announcer = a; }

    private FarmingRules rules() { return gathering.catalog().farming(); }

    // ── what a crop's block is now ───────────────────────────────

    /** True when {@code d}, a block of {@code c}, pays now. {@code placed}: the placed-block memory holds it. */
    static boolean ripe(FarmingRules.Crop c, BlockData d, boolean placed) {
        int age = 0, max = 0;
        boolean berries = false;
        if (d instanceof Ageable a) { age = a.getAge(); max = a.getMaximumAge(); }
        if (d instanceof CaveVinesPlant v) berries = v.isBerries();
        return FarmingRules.ripe(c, age, max, berries, placed);
    }

    private boolean placed(FarmingRules.Crop c, Block b) {
        PlacedBlocks p = gathering.placedBlocks();
        return c.ripe() == FarmingRules.Ripe.GROWN && p != null && p.isPlaced(b);
    }

    /** XP one harvest of {@code c} pays a player at {@code level} (the weak-crop rule: weak-resources). */
    private double xp(FarmingRules.Crop c, int level) {
        GatheringCatalog cat = gathering.catalog();
        return GatherRules.xp(c.xp(), c.level(), level, cat.weakGap(), cat.weakFactor());
    }

    // ── a player's own break ─────────────────────────────────────

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent e) {
        if (e instanceof HarvestBreak || e instanceof Woodcutting.FellingBreak) return; // a sweep or a felling pays its own
        Player p = e.getPlayer();
        pending.remove(p.getUniqueId()); // a new break: an earlier one's drops never get this one's changes
        FarmingRules f = rules();
        if (!f.enabled()) return;
        Block b = e.getBlock();
        FarmingRules.Crop c = f.crop(b.getType());
        if (c == null) return;
        boolean placedHere = placed(c, b);
        // A segment a break below already paid for, still standing until its fall next tick (a fast macro can break it
        // in the same tick): it and the segments above it were counted then, so they pay nothing again.
        boolean counted = c.column() && falling.remove(Seg.of(b)) instanceof Long until && until - System.currentTimeMillis() > 0;
        int above = c.column() && !counted ? column(b, c) : 0; // grown segments above that fall with it, each counted once
        if (c.ripe() == FarmingRules.Ripe.GROWN && gathering.placedBlocks() != null) gathering.placedBlocks().unmark(b);
        boolean ripe = !counted && ripe(c, b.getBlockData(), placedHere);
        int count = (ripe ? 1 : 0) + above;
        boolean creative = GatheringService.methodOf(p) == GatherRules.Method.CREATIVE;
        if (count == 0) {
            if (creative || counted) return;
            if (placedHere) {
                placedHarvests.increment();
                hint(p, "placed", "§7" + c.name() + " placed by hand pays no Farming XP; " + c.name().toLowerCase(java.util.Locale.ROOT)
                        + " that grows does.", 120_000);
            } else {
                unripe.increment();
                hint(p, "unripe:" + c.id(), "§7" + c.name() + " pays Farming XP when " + c.ripe().describe(c.age()) + ".", 60_000);
            }
            return;
        }
        if (creative) {
            gathering.withheld(p, f.skill(), Manager.XpIntegrity.whyNot(p));
            return;
        }
        int level = skills.getSkillLevel(p, f.skill());
        harvests.add(count);
        gathering.award(p, f.skill(), xp(c, level) * count);
        discover(p, c);
        int extra = rollBonuses(p.getUniqueId(), count);
        Quality q = ripe ? roll(f, c, level) : Quality.COMMON;
        boolean replant = ripe && c.replants() && f.replants(level) && replanting(p);
        BlockData planted = replant ? seedling(b.getBlockData()) : null;
        if (extra > 0 || q != Quality.COMMON || planted != null) pending.put(p.getUniqueId(), new Pending(b, c, extra, q, planted != null, planted));
        if (ripe && c.wide() && p.isSneaking()) startSweep(p, c, b, level);
    }

    /** A cane or cactus segment's place, for the ones counted before they fall. */
    private record Seg(UUID world, int x, int y, int z) {
        static Seg of(Block b) { return new Seg(b.getWorld().getUID(), b.getX(), b.getY(), b.getZ()); }
    }

    /** Segments a break counted above it, until when (they fall a tick later; a second break of one meanwhile pays nothing). */
    private final Map<Seg, Long> falling = new ConcurrentHashMap<>();
    static final long FALL_MS = 1_000;

    /**
     * Sugar cane or cactus above {@code b}: the segments that will fall once it is broken. Returns how many of them grew (a
     * placed one pays nothing; its mark stays until it is gone, and a segment that grows there later clears it) and
     * remembers each counted one until it has fallen. The block's region thread.
     */
    private int column(Block b, FarmingRules.Crop c) {
        PlacedBlocks marks = gathering.placedBlocks();
        int grown = 0;
        World w = b.getWorld();
        long until = System.currentTimeMillis() + FALL_MS;
        for (int y = b.getY() + 1, n = 0; y < w.getMaxHeight() && n < COLUMN_MAX; y++, n++) {
            Block up = w.getBlockAt(b.getX(), y, b.getZ());
            if (!c.blocks().contains(up.getType())) break;
            if (marks != null && marks.isPlaced(up)) continue;
            Long before = falling.put(Seg.of(up), until);
            if (before == null || before - System.currentTimeMillis() <= 0) grown++; // never counted twice
        }
        if (falling.size() > 256) {
            long now = System.currentTimeMillis();
            falling.values().removeIf(t -> t - now <= 0);
        }
        return grown;
    }

    /** Fortune on crops: of {@code count} harvests, how many drop one more of their produce. */
    private int rollBonuses(UUID player, int count) {
        double chance = gathering.bonusChance(player, Attributes.Yield.CROP);
        if (!(chance > 0)) return 0;
        int n = 0;
        for (int i = 0; i < count; i++) if (ThreadLocalRandom.current().nextDouble() < chance) n++;
        return n;
    }

    private Quality roll(FarmingRules f, FarmingRules.Crop c, int level) {
        Quality q = f.rollQuality(c, level, ThreadLocalRandom.current().nextDouble());
        qualities[q.ordinal()].increment();
        return q;
    }

    /** What a replant sets: the same block (a cocoa pod on the same side of the same log) at its first growth stage. */
    static BlockData seedling(BlockData ripe) {
        if (!(ripe instanceof Ageable)) return null;
        BlockData d = ripe.clone();
        ((Ageable) d).setAge(0);
        return d;
    }

    /** A break's drops: the replant's seed taken out, the Fortune bonus added, the produce marked with its quality. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDrop(BlockDropItemEvent e) {
        Player p = e.getPlayer();
        Pending h = pending.remove(p.getUniqueId());
        if (h == null || !h.block().equals(e.getBlock())) return;
        FarmingRules.Crop c = h.crop();
        List<Item> items = e.getItems();
        if (h.replant()) {
            boolean seed = takeSeed(items, c.seed()) || takeSeed(p.getInventory(), c.seed());
            if (seed && e.getBlock().getType().isAir() && h.planted().isSupported(e.getBlock())) {
                e.getBlock().setBlockData(h.planted(), true);
                replants.increment();
            } else if (!seed) {
                noSeed.increment();
                hint(p, "seed", "§7No " + GearItems.label(c.seed().name()).toLowerCase(java.util.Locale.ROOT) + " to replant with.", 60_000);
            }
        }
        if (h.bonuses() > 0) {
            for (Item item : items) {
                ItemStack s = item.getItemStack();
                if (s.getType() != c.produce() || s.getAmount() >= s.getMaxStackSize()) continue;
                s.setAmount(Math.min(s.getMaxStackSize(), s.getAmount() + h.bonuses()));
                item.setItemStack(s);
                bonuses.add(h.bonuses());
                p.playSound(p.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.35f, 1.8f);
                break;
            }
        }
        if (h.quality() != Quality.COMMON) {
            for (Item item : items) {
                ItemStack s = item.getItemStack();
                if (s.getType() != c.produce()) continue;
                GearItems.setCropQuality(s, h.quality());
                item.setItemStack(s);
            }
            announceQuality(p, c, h.quality());
        }
    }

    /** One seed out of a harvest's own drops; true when there was one. */
    private static boolean takeSeed(List<Item> items, Material seed) {
        for (Iterator<Item> it = items.iterator(); it.hasNext(); ) {
            Item item = it.next();
            ItemStack s = item.getItemStack();
            if (s.getType() != seed) continue;
            if (s.getAmount() <= 1) it.remove(); // never spawned
            else { s.setAmount(s.getAmount() - 1); item.setItemStack(s); }
            return true;
        }
        return false;
    }

    /** One seed out of a list of drops (a sweep's); true when there was one. */
    private static boolean takeSeedFrom(List<ItemStack> drops, Material seed) {
        for (Iterator<ItemStack> it = drops.iterator(); it.hasNext(); ) {
            ItemStack s = it.next();
            if (s == null || s.getType() != seed) continue;
            if (s.getAmount() <= 1) it.remove();
            else s.setAmount(s.getAmount() - 1);
            return true;
        }
        return false;
    }

    /** One seed out of the player's inventory (a plain one first, then one with a quality); true when there was one. */
    static boolean takeSeed(PlayerInventory inv, Material seed) {
        ItemStack[] contents = inv.getStorageContents();
        int slot = -1;
        for (int i = 0; i < contents.length; i++) {
            ItemStack s = contents[i];
            if (s == null || s.getType() != seed) continue;
            if (slot < 0 || GearItems.cropQuality(s) == null) slot = i;
            if (GearItems.cropQuality(s) == null) break;
        }
        if (slot < 0) return false;
        ItemStack s = contents[slot];
        if (s.getAmount() <= 1) inv.setItem(slot, null);
        else { s.setAmount(s.getAmount() - 1); inv.setItem(slot, s); }
        return true;
    }

    private void announceQuality(Player p, FarmingRules.Crop c, Quality q) {
        if (q.ordinal() < Quality.SUPERIOR.ordinal()) return; // Fine is common enough to go without a sound
        if (!hintDue(p, "quality", 1_500)) return;
        notice.accept(p, q.color + "✦ " + q.displayName + " " + GearItems.label(c.produce().name()).toLowerCase(java.util.Locale.ROOT));
        p.playSound(p.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.7f, q == Quality.MASTERWORK ? 1.4f : 1.1f);
    }

    // ── picking (sweet berries, glow berries) ────────────────────

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPick(PlayerHarvestBlockEvent e) {
        FarmingRules f = rules();
        if (!f.enabled()) return;
        Player p = e.getPlayer();
        Block b = e.getHarvestedBlock();
        FarmingRules.Crop c = f.crop(b.getType());
        if (c == null || !c.pick()) return;
        if (!ripe(c, b.getBlockData(), false)) return; // vanilla gives nothing for an unripe pick either
        if (GatheringService.methodOf(p) == GatherRules.Method.CREATIVE) {
            gathering.withheld(p, f.skill(), Manager.XpIntegrity.whyNot(p));
            return;
        }
        int level = skills.getSkillLevel(p, f.skill());
        harvests.increment();
        picks.increment();
        gathering.award(p, f.skill(), xp(c, level));
        discover(p, c);
        List<ItemStack> items = e.getItemsHarvested();
        if (rollBonuses(p.getUniqueId(), 1) > 0) {
            for (ItemStack s : items) {
                if (s == null || s.getType() != c.produce() || s.getAmount() >= s.getMaxStackSize()) continue;
                s.setAmount(s.getAmount() + 1);
                bonuses.increment();
                break;
            }
        }
        Quality q = roll(f, c, level);
        if (q != Quality.COMMON) {
            for (ItemStack s : items) if (s != null && s.getType() == c.produce()) GearItems.setCropQuality(s, q);
            announceQuality(p, c, q);
        }
    }

    // ── discoveries ──────────────────────────────────────────────

    /** The crops {@code p} has discovered (their ids), read from the player once and kept while they are online. Player's thread. */
    public Set<String> discovered(Player p) {
        return discovered.computeIfAbsent(p.getUniqueId(), id -> {
            String saved = p.getPersistentDataContainer().get(CROPS, PersistentDataType.STRING);
            Set<String> out = ConcurrentHashMap.newKeySet();
            if (saved != null) for (String s : saved.split(",")) if (!s.isBlank()) out.add(s.trim());
            return out;
        });
    }

    private void discover(Player p, FarmingRules.Crop c) {
        if (!skills.hasLoadedSkills(p.getUniqueId())) return; // its XP would be lost: the next harvest discovers it instead
        Set<String> have = discovered(p);
        if (!have.add(c.id())) return;
        p.getPersistentDataContainer().set(CROPS, PersistentDataType.STRING, String.join(",", new LinkedHashSet<>(have)));
        discoveries.increment();
        if (c.discoveryXp() > 0) gathering.award(p, rules().skill(), c.discoveryXp());
        Announcer a = announcer;
        if (a != null) a.discovered(p, c, c.discoveryXp());
    }

    /** Forget {@code p}'s discoveries (admin, for tests). Player's thread. */
    public void resetDiscoveries(Player p) {
        discovered.remove(p.getUniqueId());
        p.getPersistentDataContainer().remove(CROPS);
    }

    /** How the rules see {@code b}, a block of {@code c}, for {@code p} (admin): ripe, placed, the XP it pays them now. */
    public String describe(Player p, Block b, FarmingRules.Crop c) {
        boolean placedHere = placed(c, b);
        int level = skills.getSkillLevel(p, rules().skill());
        return (ripe(c, b.getBlockData(), placedHere) ? "§aripe" : "§cnot ripe") + "§7" + (placedHere ? ", placed by hand" : "")
                + ", " + xp(c, level) + " XP at Farming " + level + (c.column() ? " per segment" : "")
                + ", Fortune chance " + Math.round(gathering.bonusChance(p.getUniqueId(), Attributes.Yield.CROP) * 1000) / 10.0 + "%";
    }

    // ── Replant's switch ─────────────────────────────────────────

    /** True unless {@code p} turned Replant off. Player's thread. */
    public boolean replanting(Player p) {
        return !replantOff.computeIfAbsent(p.getUniqueId(), id -> {
            String flags = p.getPersistentDataContainer().get(FLAGS, PersistentDataType.STRING);
            return flags != null && List.of(flags.split(",")).contains("noreplant");
        });
    }

    /** Turn Replant on or off for {@code p} (saved on the player). Player's thread. */
    public void setReplanting(Player p, boolean on) {
        replantOff.put(p.getUniqueId(), !on);
        if (on) p.getPersistentDataContainer().remove(FLAGS);
        else p.getPersistentDataContainer().set(FLAGS, PersistentDataType.STRING, "noreplant");
    }

    // ── Wide Harvest ─────────────────────────────────────────────

    /** A Wide Harvest in progress: the crops still to take (nearest first), and what it has taken so far. */
    private final class Sweep {
        final Player p;
        final UUID id;
        final FarmingRules.Crop crop;
        final Material hoe;
        final List<Block> targets;
        final World world;
        final Location at;
        final int level;
        final boolean replant;
        int next, effects, taken;

        Sweep(Player p, FarmingRules.Crop crop, Material hoe, List<Block> targets, Location at, int level, boolean replant) {
            this.p = p;
            this.id = p.getUniqueId();
            this.crop = crop;
            this.hoe = hoe;
            this.targets = targets;
            this.world = at.getWorld();
            this.at = at;
            this.level = level;
            this.replant = replant;
        }
    }

    /** Start a Wide Harvest around {@code b} (already broken by {@code p}'s own hand), if one may. The block's region thread. */
    private void startSweep(Player p, FarmingRules.Crop c, Block b, int level) {
        FarmingRules f = rules();
        int side = f.reach(level);
        if (side <= 0 || p.getGameMode() != GameMode.SURVIVAL) return; // not open yet; adventure's own limits are never bypassed
        ItemStack hand = p.getInventory().getItemInMainHand();
        if (!"hoe".equals(GearItems.kind(hand.getType()))) {
            hint(p, "hoe", "§7Wide Harvest: Sneak and break a ripe crop with a §fhoe§7.", 60_000);
            return;
        }
        long now = System.currentTimeMillis();
        Long running = sweeping.get(p.getUniqueId());
        if (running != null && now - running < 10_000) return; // one at a time
        List<Block> targets = targets(b, c, side / 2);
        if (targets.isEmpty()) return;
        sweeping.put(p.getUniqueId(), now);
        sweeps.increment();
        Sweep s = new Sweep(p, c, hand.getType(), targets, b.getLocation().add(0.5, 0.5, 0.5), level, c.replants() && f.replants(level) && replanting(p));
        b.getWorld().playSound(s.at, Sound.ITEM_HOE_TILL, SoundCategory.BLOCKS, 0.8f, 0.8f);
        Bukkit.getRegionScheduler().runDelayed(plugin, s.at, task -> step(s), 1L);
    }

    /**
     * The ripe crops of {@code c} within {@code r} blocks of {@code b} sideways and one up or down, nearest first, in loaded
     * chunks this region thread owns (the rest is left for the hand).
     */
    private List<Block> targets(Block b, FarmingRules.Crop c, int r) {
        World w = b.getWorld();
        List<Block> out = new ArrayList<>();
        for (int ring = 1; ring <= r; ring++) {
            for (int dx = -ring; dx <= ring; dx++) for (int dz = -ring; dz <= ring; dz++) {
                if (Math.max(Math.abs(dx), Math.abs(dz)) != ring) continue;
                int x = b.getX() + dx, z = b.getZ() + dz;
                if (!w.isChunkLoaded(x >> 4, z >> 4) || !Bukkit.isOwnedByCurrentRegion(w, x >> 4, z >> 4)) continue;
                for (int dy = -1; dy <= 1; dy++) {
                    int y = b.getY() + dy;
                    if (y < w.getMinHeight() || y >= w.getMaxHeight()) continue;
                    Block t = w.getBlockAt(x, y, z);
                    if (c.blocks().contains(t.getType()) && ripe(c, t.getBlockData(), false)) out.add(t);
                }
            }
        }
        return out;
    }

    /** One step of a Wide Harvest: up to per-tick crops, their drops at the first crop and their XP to the player. */
    private void step(Sweep s) {
        FarmingRules f = rules();
        boolean go = f.enabled() && s.p.isOnline() && !s.p.isDead() && Bukkit.isOwnedByCurrentRegion(s.p)
                && s.p.getGameMode() == GameMode.SURVIVAL;
        if (go && s.p.getInventory().getItemInMainHand().getType() != s.hoe) go = false; // the hoe left the hand
        List<ItemStack> drops = new ArrayList<>();
        int took = 0, bonus = 0;
        double xp = 0;
        Quality best = Quality.COMMON;
        while (go && took < f.perTick() && s.next < s.targets.size()) {
            Block b = s.targets.get(s.next++);
            if (!Bukkit.isOwnedByCurrentRegion(b)) { go = false; break; } // another region: the rest is for the hand
            BlockData data = b.getBlockData();
            if (!s.crop.blocks().contains(b.getType()) || !ripe(s.crop, data, false)) continue; // harvested or changed meanwhile
            ItemStack hoe = s.p.getInventory().getItemInMainHand();
            if (Woodcutting.wouldBreak(hoe)) {
                hint(s.p, "wide-hoe", "§7Wide Harvest stopped: your hoe would break.", 10_000);
                go = false;
                break;
            }
            if (!GatheringService.vanillaAllows(s.p, b)) { refusals.increment(); go = false; break; } // spawn protection, the border
            HarvestBreak e = new HarvestBreak(b, s.p);
            Bukkit.getPluginManager().callEvent(e);
            if (e.isCancelled()) { refusals.increment(); go = false; break; } // a protection plugin said no: stop here
            List<ItemStack> mine = e.isDropItems() ? new ArrayList<>(b.getDrops(hoe, s.p)) : new ArrayList<>();
            BlockData planted = s.replant ? seedling(data) : null;
            boolean seed = planted != null && (takeSeedFrom(mine, s.crop.seed()) || takeSeed(s.p.getInventory(), s.crop.seed()));
            if (s.effects < EFFECTS && took % 4 == 0) { s.world.playEffect(b.getLocation(), Effect.STEP_SOUND, b.getType()); s.effects++; }
            b.setType(Material.AIR, true);
            if (seed && planted.isSupported(b)) { b.setBlockData(planted, true); replants.increment(); }
            else if (planted != null && !seed) noSeed.increment();
            s.p.damageItemStack(EquipmentSlot.HAND, 1);
            took++;
            s.taken++;
            xp += xp(s.crop, s.level);
            if (e.isDropItems()) {
                int extra = rollBonuses(s.id, 1);
                if (extra > 0) { mine.add(new ItemStack(s.crop.produce(), extra)); bonus += extra; }
                Quality q = roll(f, s.crop, s.level);
                if (q != Quality.COMMON) {
                    for (ItemStack it : mine) if (it.getType() == s.crop.produce()) GearItems.setCropQuality(it, q);
                    if (q.ordinal() > best.ordinal()) best = q;
                }
            }
            drops.addAll(mine);
        }
        swept.add(took);
        harvests.add(took);
        bonuses.add(bonus);
        for (ItemStack it : Woodcutting.merge(drops)) s.world.dropItemNaturally(s.at, it);
        double paid = xp;
        Quality shown = best;
        String skill = f.skill();
        if (s.p.isOnline() && (paid > 0 || shown != Quality.COMMON)) s.p.getScheduler().run(plugin, t -> {
            if (paid > 0) gathering.award(s.p, skill, paid);
            if (shown != Quality.COMMON) announceQuality(s.p, s.crop, shown);
        }, null);
        if (go && s.next < s.targets.size()) {
            Bukkit.getRegionScheduler().runDelayed(plugin, s.at, task -> step(s), 1L);
            return;
        }
        sweeping.remove(s.id);
    }

    // ── hints ────────────────────────────────────────────────────

    private boolean hint(Player p, String kind, String text, long everyMs) {
        if (!hintDue(p, kind, everyMs)) return false;
        notice.accept(p, text);
        return true;
    }

    private boolean hintDue(Player p, String kind, long everyMs) {
        long now = System.currentTimeMillis();
        String key = p.getUniqueId() + ":" + kind;
        Long last = lastHint.get(key);
        if (last != null && now - last < everyMs) return false;
        lastHint.put(key, now);
        return true;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent e) {
        UUID id = e.getPlayer().getUniqueId();
        pending.remove(id);
        discovered.remove(id);
        replantOff.remove(id);
        lastHint.keySet().removeIf(k -> k.startsWith(id + ":"));
    }

    /** Diagnostics for /cerb farm stats. */
    public String stats() {
        return "harvests " + harvests.sum() + " (picked " + picks.sum() + ", swept " + swept.sum() + " in " + sweeps.sum() + " wide harvests, "
                + refusals.sum() + " stopped by protection), unripe " + unripe.sum() + ", placed " + placedHarvests.sum() + ", Fortune +"
                + bonuses.sum() + ", replanted " + replants.sum() + " (no seed " + noSeed.sum() + "), discoveries " + discoveries.sum()
                + ", quality fine/superior/masterwork " + qualities[1].sum() + "/" + qualities[2].sum() + "/" + qualities[3].sum();
    }
}
