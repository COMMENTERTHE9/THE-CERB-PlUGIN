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
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.Bisected;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Waterlogged;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockDropItemEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.LinkedHashMap;
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
 * Herbalism (docs/design/HERBALISM.md): wild plants gathered by players pay Herbalism XP, with discoveries, Fortune on herbs,
 * rare reagents (25), reagent quality (50) and Wildcrafting (Sneak + shears, 10). A gather pays when its drops hold the herb
 * itself (a fern broken without shears drops nothing of it: nothing was gathered). A plant placed by hand pays nothing
 * ({@link PlacedBlocks}); a break with no player in it (water, pistons) keeps vanilla's drops and pays nobody. Folia: a
 * gather is handled on the region thread that owns the block (the player's); Wildcrafting's steps run on the region
 * scheduler at the first plant, every plant and the player are checked at every step, and XP is awarded on the player's
 * own thread.
 */
public final class Herbalism implements Listener {

    /** The herbs a player has discovered (their ids), saved on the player (no database). */
    public static final NamespacedKey HERBS = new NamespacedKey("cerberus", "herbs_discovered");
    /** The rare reagents a player has found, with how many: "bloodroot:3,moonpetal:1". */
    public static final NamespacedKey RARES = new NamespacedKey("cerberus", "rare_reagents");
    /** Break effects per Wildcrafting sweep: a few, never one per plant. */
    static final int EFFECTS = 6;

    /**
     * One plant a Wildcrafting sweep is about to take, offered to protection plugins as the player's own break: cancel it and
     * the plant stays (and the sweep stops there), as with a player's break. It carries the player, so claims and regions
     * judge it like one.
     */
    public static final class GatherBreak extends BlockBreakEvent implements SweepBreak {
        public GatherBreak(Block block, Player player) { super(block, player); }
    }

    /** How a discovery is shown (chat and a sound, set by the plugin): a herb or a rare reagent, and the XP it gave. */
    @FunctionalInterface
    public interface Announcer {
        void discovered(Player p, String name, double xp, boolean rare);
    }

    /** A break waiting for its drops: the plant, its herb, the player's level when it broke. */
    private record Pending(Block block, HerbalismRules.Herb herb, int level) {}

    private final Plugin plugin;
    private final GatheringService gathering;
    private final SkillManager skills;
    private final BiConsumer<Player, String> notice;
    private volatile Announcer announcer;
    private final Map<UUID, Pending> pending = new ConcurrentHashMap<>();
    private final Map<UUID, Set<String>> discovered = new ConcurrentHashMap<>();
    private final Map<UUID, Map<String, Integer>> rareFound = new ConcurrentHashMap<>();
    private final Map<UUID, Long> sweeping = new ConcurrentHashMap<>();
    private final Map<String, Long> hints = new ConcurrentHashMap<>();
    private final LongAdder gathers = new LongAdder(), placedGathers = new LongAdder(), withoutHerb = new LongAdder(),
            bonuses = new LongAdder(), rareDrops = new LongAdder(), discoveries = new LongAdder(), sweeps = new LongAdder(),
            swept = new LongAdder(), refusals = new LongAdder();
    private final LongAdder[] qualities = new LongAdder[Quality.values().length];

    public Herbalism(Plugin plugin, GatheringService gathering, SkillManager skills, BiConsumer<Player, String> notice) {
        this.plugin = plugin;
        this.gathering = gathering;
        this.skills = skills;
        this.notice = notice;
        for (int i = 0; i < qualities.length; i++) qualities[i] = new LongAdder();
    }

    public void setAnnouncer(Announcer a) { this.announcer = a; }

    private HerbalismRules rules() { return gathering.catalog().herbalism(); }

    /** The block a plant is judged by: a tall flower's lower half (where a placed one is remembered). */
    static Block base(Block b) {
        return b.getBlockData() instanceof Bisected bi && bi.getHalf() == Bisected.Half.TOP ? b.getRelative(BlockFace.DOWN) : b;
    }

    private boolean placed(Block b) {
        PlacedBlocks p = gathering.placedBlocks();
        return p != null && p.isPlaced(base(b));
    }

    /** XP one gather of {@code h} pays a player at {@code level} (the weak-plant rule: weak-resources). */
    private double xp(HerbalismRules.Herb h, int level) {
        GatheringCatalog cat = gathering.catalog();
        return GatherRules.xp(h.xp(), h.level(), level, cat.weakGap(), cat.weakFactor());
    }

    // ── a player's own break ─────────────────────────────────────

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent e) {
        if (e instanceof SweepBreak) return; // sweeps pay their own
        Player p = e.getPlayer();
        pending.remove(p.getUniqueId()); // a new break: an earlier one's drops never get this one's changes
        HerbalismRules r = rules();
        if (!r.enabled()) return;
        Block b = e.getBlock();
        HerbalismRules.Herb h = r.herb(b.getType());
        if (h == null) return;
        boolean placedHere = placed(b);
        PlacedBlocks marks = gathering.placedBlocks();
        if (marks != null) marks.unmark(base(b)); // the plant is gone: a wild one that grows there later is wild
        if (GatheringService.methodOf(p) == GatherRules.Method.CREATIVE) {
            gathering.withheld(p, r.skill(), Manager.XpIntegrity.whyNot(p));
            return;
        }
        if (placedHere) {
            placedGathers.increment();
            hint(p, "placed", "§7" + h.name() + " placed by hand pays no Herbalism XP; wild plants do.", 120_000);
            return;
        }
        ItemStack hand = p.getInventory().getItemInMainHand();
        if (h.shears() && hand.getType() != Material.SHEARS) {
            hint(p, "shears:" + h.id(), "§7" + h.name() + " is gathered with §fshears§7.", 60_000);
        }
        int level = skills.getSkillLevel(p, r.skill());
        pending.put(p.getUniqueId(), new Pending(b, h, level));
        if (p.isSneaking()) startSweep(p, h, b, level);
    }

    /** A gather's drops: it pays when they hold the herb; Fortune's one more, the quality, the rare reagent. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDrop(BlockDropItemEvent e) {
        Player p = e.getPlayer();
        Pending g = pending.remove(p.getUniqueId());
        if (g == null || !g.block().equals(e.getBlock())) return;
        HerbalismRules r = rules();
        if (!r.enabled()) return;
        HerbalismRules.Herb h = g.herb();
        List<Item> items = e.getItems();
        Item first = null;
        for (Item it : items) if (h.produce().contains(it.getItemStack().getType())) { first = it; break; }
        if (first == null) { withoutHerb.increment(); return; } // nothing of the herb dropped: nothing was gathered
        gathers.increment();
        gathering.award(p, r.skill(), xp(h, g.level()));
        discover(p, h);
        if (rollBonus(p.getUniqueId())) {
            ItemStack s = first.getItemStack();
            if (s.getAmount() < s.getMaxStackSize()) {
                s.setAmount(s.getAmount() + 1);
                first.setItemStack(s);
                bonuses.increment();
                p.playSound(p.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.35f, 1.8f);
            }
        }
        Quality q = roll(r, g.level());
        if (q != Quality.COMMON) {
            for (Item it : items) {
                ItemStack s = it.getItemStack();
                if (!h.produce().contains(s.getType())) continue;
                GearItems.setCropQuality(s, q);
                it.setItemStack(s);
            }
            announceQuality(p, h.name(), q);
        }
        ItemStack rare = rollRare(p, r, h, g.level());
        if (rare != null) e.getBlock().getWorld().dropItemNaturally(e.getBlock().getLocation().add(0.5, 0.5, 0.5), rare);
    }

    /** Fortune on herbs: one more of the herb. */
    private boolean rollBonus(UUID player) {
        double chance = gathering.bonusChance(player, Attributes.Yield.HERB);
        return chance > 0 && ThreadLocalRandom.current().nextDouble() < chance;
    }

    private Quality roll(HerbalismRules r, int level) {
        Quality q = r.rollQuality(level, ThreadLocalRandom.current().nextDouble());
        qualities[q.ordinal()].increment();
        return q;
    }

    /** The rare reagent this gather gives, or null: the herb's own, at the level's chance, with its own quality roll. */
    private ItemStack rollRare(Player p, HerbalismRules r, HerbalismRules.Herb h, int level) {
        HerbalismRules.Rare rare = r.rare(h.rare());
        if (rare == null) return null;
        double chance = r.rareChance(level);
        if (!(chance > 0) || ThreadLocalRandom.current().nextDouble() >= chance) return null;
        GatheringCatalog.ItemDef def = gathering.catalog().item(rare.item());
        if (def == null) return null;
        ItemStack it = GearItems.makeItem(def, 1);
        Quality q = roll(r, level);
        if (q != Quality.COMMON) GearItems.setCropQuality(it, q);
        rareDrops.increment();
        foundRare(p, rare, def.name());
        return it;
    }

    private void announceQuality(Player p, String what, Quality q) {
        if (q.ordinal() < Quality.SUPERIOR.ordinal()) return; // Fine is common enough to go without a sound
        if (!hintDue(p, "quality", 1_500)) return;
        notice.accept(p, q.color + "✦ " + q.displayName + " " + what.toLowerCase(java.util.Locale.ROOT));
        p.playSound(p.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.7f, q == Quality.MASTERWORK ? 1.4f : 1.1f);
    }

    // ── Wildcrafting ─────────────────────────────────────────────

    /** One Wildcrafting sweep in progress. */
    private static final class Sweep {
        final Player p;
        final UUID id;
        final HerbalismRules.Herb herb;
        final List<Block> targets;
        final Location at;
        final World world;
        final int level;
        int next, taken, effects;

        Sweep(Player p, HerbalismRules.Herb herb, List<Block> targets, Location at, int level) {
            this.p = p;
            this.id = p.getUniqueId();
            this.herb = herb;
            this.targets = targets;
            this.at = at;
            this.world = at.getWorld();
            this.level = level;
        }
    }

    /** Start Wildcrafting around {@code b} (being gathered by {@code p}'s own hand), if one may. The block's region thread. */
    private void startSweep(Player p, HerbalismRules.Herb h, Block b, int level) {
        HerbalismRules r = rules();
        int side = r.reach(level);
        if (side <= 0 || p.getGameMode() != GameMode.SURVIVAL) return; // not open yet; adventure's own limits are never bypassed
        if (p.getInventory().getItemInMainHand().getType() != Material.SHEARS) {
            hint(p, "wild-shears", "§7Wildcrafting: Sneak and gather a wild plant with §fshears§7.", 60_000);
            return;
        }
        long now = System.currentTimeMillis();
        Long running = sweeping.get(p.getUniqueId());
        if (running != null && now - running < 10_000) return; // one at a time
        List<Block> targets = targets(b, h, side / 2);
        if (targets.isEmpty()) return;
        sweeping.put(p.getUniqueId(), now);
        sweeps.increment();
        Sweep s = new Sweep(p, h, targets, b.getLocation().add(0.5, 0.5, 0.5), level);
        b.getWorld().playSound(s.at, Sound.ENTITY_SHEEP_SHEAR, SoundCategory.BLOCKS, 0.8f, 1.2f);
        Bukkit.getRegionScheduler().runDelayed(plugin, s.at, task -> step(s), 1L);
    }

    /**
     * The wild plants of {@code h} within {@code r} blocks of {@code b} sideways and one up or down, nearest first, one block
     * per plant (a tall flower's lower half), in loaded chunks this region thread owns.
     */
    private List<Block> targets(Block b, HerbalismRules.Herb h, int r) {
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
                    if (!h.blocks().contains(t.getType()) || base(t) != t && base(t).getType() == t.getType()) continue; // one block per plant
                    if (!placed(t)) out.add(t);
                }
            }
        }
        return out;
    }

    /** One step of Wildcrafting: up to per-tick plants, their drops at the first plant and their XP to the player. */
    private void step(Sweep s) {
        HerbalismRules r = rules();
        boolean go = r.enabled() && s.p.isOnline() && !s.p.isDead() && Bukkit.isOwnedByCurrentRegion(s.p)
                && s.p.getGameMode() == GameMode.SURVIVAL && s.p.getInventory().getItemInMainHand().getType() == Material.SHEARS;
        List<ItemStack> drops = new ArrayList<>();
        int took = 0, paid = 0, bonus = 0;
        double xp = 0;
        Quality best = Quality.COMMON;
        while (go && took < r.perTick() && s.next < s.targets.size()) {
            Block b = s.targets.get(s.next++);
            if (!Bukkit.isOwnedByCurrentRegion(b)) { go = false; break; } // another region: the rest is for the hand
            if (!s.herb.blocks().contains(b.getType()) || placed(b)) continue; // gathered or changed meanwhile
            ItemStack shears = s.p.getInventory().getItemInMainHand();
            if (Woodcutting.wouldBreak(shears)) {
                hint(s.p, "wild-wear", "§7Wildcrafting stopped: your shears would break.", 10_000);
                go = false;
                break;
            }
            if (!GatheringService.vanillaAllows(s.p, b)) { refusals.increment(); go = false; break; } // spawn protection, the border
            GatherBreak e = new GatherBreak(b, s.p);
            Bukkit.getPluginManager().callEvent(e);
            if (e.isCancelled()) { refusals.increment(); go = false; break; } // a protection plugin said no: stop here
            List<ItemStack> mine = e.isDropItems() ? new ArrayList<>(b.getDrops(shears, s.p)) : new ArrayList<>();
            if (s.effects < EFFECTS && took % 4 == 0) { s.world.playEffect(b.getLocation(), Effect.STEP_SOUND, b.getType()); s.effects++; }
            remove(b);
            s.p.damageItemStack(EquipmentSlot.HAND, 1);
            took++;
            s.taken++;
            ItemStack herbStack = null;
            for (ItemStack it : mine) if (s.herb.produce().contains(it.getType())) { herbStack = it; break; }
            if (herbStack == null) continue; // nothing of the herb: nothing gathered
            paid++;
            xp += xp(s.herb, s.level);
            if (rollBonus(s.id) && herbStack.getAmount() < herbStack.getMaxStackSize()) { herbStack.setAmount(herbStack.getAmount() + 1); bonus++; }
            Quality q = roll(r, s.level);
            if (q != Quality.COMMON) {
                for (ItemStack it : mine) if (s.herb.produce().contains(it.getType())) GearItems.setCropQuality(it, q);
                if (q.ordinal() > best.ordinal()) best = q;
            }
            ItemStack rare = rollRare(s.p, r, s.herb, s.level);
            if (rare != null) mine.add(rare);
            drops.addAll(mine);
        }
        swept.add(took);
        gathers.add(paid);
        bonuses.add(bonus);
        for (ItemStack it : Woodcutting.merge(drops)) s.world.dropItemNaturally(s.at, it);
        double award = xp;
        Quality shown = best;
        String skill = r.skill();
        HerbalismRules.Herb herb = s.herb;
        if (s.p.isOnline() && (award > 0 || shown != Quality.COMMON)) s.p.getScheduler().run(plugin, t -> {
            if (award > 0) { gathering.award(s.p, skill, award); discover(s.p, herb); }
            if (shown != Quality.COMMON) announceQuality(s.p, herb.name(), shown);
        }, null);
        if (go && s.next < s.targets.size()) {
            Bukkit.getRegionScheduler().runDelayed(plugin, s.at, task -> step(s), 1L);
            return;
        }
        sweeping.remove(s.id);
    }

    /**
     * Take a plant away as a break does: a tall flower's both halves (without the second half dropping again), and water
     * back where a water plant stood (seagrass, kelp, a waterlogged pickle or dripleaf), never an air pocket.
     */
    static void remove(Block b) {
        BlockData d = b.getBlockData();
        if (d instanceof Bisected bi) {
            Block other = bi.getHalf() == Bisected.Half.BOTTOM ? b.getRelative(BlockFace.UP) : b.getRelative(BlockFace.DOWN);
            if (other.getType() == b.getType()) other.setType(wet(other.getBlockData()) ? Material.WATER : Material.AIR, false);
        }
        b.setType(wet(d) ? Material.WATER : Material.AIR, true);
    }

    /** True for a plant that stands in water: seagrass and kelp always, anything else when waterlogged. */
    static boolean wet(BlockData d) {
        Material t = d.getMaterial();
        return t == Material.SEAGRASS || t == Material.TALL_SEAGRASS || t == Material.KELP || t == Material.KELP_PLANT
                || (d instanceof Waterlogged w && w.isWaterlogged());
    }

    // ── discoveries and rare finds ───────────────────────────────

    /** The herbs {@code p} has discovered (their ids), read from the player once and kept while they are online. Player's thread. */
    public Set<String> discovered(Player p) {
        return discovered.computeIfAbsent(p.getUniqueId(), id -> {
            String saved = p.getPersistentDataContainer().get(HERBS, PersistentDataType.STRING);
            Set<String> out = ConcurrentHashMap.newKeySet();
            if (saved != null) for (String s : saved.split(",")) if (!s.isBlank()) out.add(s.trim());
            return out;
        });
    }

    private void discover(Player p, HerbalismRules.Herb h) {
        if (!skills.hasLoadedSkills(p.getUniqueId())) return; // its XP would be lost: the next gather discovers it instead
        Set<String> have = discovered(p);
        if (!have.add(h.id())) return;
        p.getPersistentDataContainer().set(HERBS, PersistentDataType.STRING, String.join(",", new LinkedHashSet<>(have)));
        discoveries.increment();
        if (h.discoveryXp() > 0) gathering.award(p, rules().skill(), h.discoveryXp());
        Announcer a = announcer;
        if (a != null) a.discovered(p, h.name(), h.discoveryXp(), false);
    }

    /** The rare reagents {@code p} has found, with how many. Player's thread. */
    public Map<String, Integer> rareFound(Player p) {
        return rareFound.computeIfAbsent(p.getUniqueId(), id -> {
            Map<String, Integer> out = new ConcurrentHashMap<>();
            String saved = p.getPersistentDataContainer().get(RARES, PersistentDataType.STRING);
            if (saved != null) for (String part : saved.split(",")) {
                int c = part.indexOf(':');
                if (c <= 0) continue;
                try { out.put(part.substring(0, c).trim(), Math.max(0, Integer.parseInt(part.substring(c + 1).trim()))); }
                catch (NumberFormatException ignored) { /* a damaged entry is skipped, the rest kept */ }
            }
            return out;
        });
    }

    private void foundRare(Player p, HerbalismRules.Rare rare, String name) {
        Map<String, Integer> have = rareFound(p);
        boolean first = !have.containsKey(rare.id());
        have.merge(rare.id(), 1, Integer::sum);
        Map<String, Integer> sorted = new LinkedHashMap<>(have);
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, Integer> e : sorted.entrySet()) { if (!sb.isEmpty()) sb.append(','); sb.append(e.getKey()).append(':').append(e.getValue()); }
        p.getPersistentDataContainer().set(RARES, PersistentDataType.STRING, sb.toString());
        if (first) {
            discoveries.increment();
            Announcer a = announcer;
            if (a != null) a.discovered(p, name, 0, true);
        } else {
            notice.accept(p, "§d✦ " + name);
        }
    }

    /** Forget {@code p}'s discoveries and finds (admin, for tests). Player's thread. */
    public void resetDiscoveries(Player p) {
        discovered.remove(p.getUniqueId());
        rareFound.remove(p.getUniqueId());
        p.getPersistentDataContainer().remove(HERBS);
        p.getPersistentDataContainer().remove(RARES);
    }

    /** How the rules see {@code b}, a block of {@code h}, for {@code p} (admin): placed, the XP it pays them now, the chances. */
    public String describe(Player p, Block b, HerbalismRules.Herb h) {
        HerbalismRules r = rules();
        int level = skills.getSkillLevel(p, r.skill());
        return (placed(b) ? "§cplaced by hand" : "§awild") + "§7, " + xp(h, level) + " XP at Herbalism " + level
                + (h.shears() ? ", shears" : "") + ", Fortune chance " + Math.round(gathering.bonusChance(p.getUniqueId(), Attributes.Yield.HERB) * 1000) / 10.0
                + "%, rare " + Math.round(r.rareChance(level) * 1000) / 10.0 + "%" + (h.rare() != null ? " (" + h.rare() + ")" : " (none)");
    }

    /** One line for /cerb herb stats. */
    public String stats() {
        return "gathered " + gathers.sum() + " (placed by hand " + placedGathers.sum() + ", nothing of the herb " + withoutHerb.sum()
                + "), Fortune extra " + bonuses.sum() + ", rare reagents " + rareDrops.sum() + ", discoveries " + discoveries.sum()
                + ", Wildcrafting " + sweeps.sum() + " sweeps / " + swept.sum() + " plants (refused " + refusals.sum() + "), quality fine "
                + qualities[Quality.FINE.ordinal()].sum() + " superior " + qualities[Quality.SUPERIOR.ordinal()].sum()
                + " masterwork " + qualities[Quality.MASTERWORK.ordinal()].sum();
    }

    // ── hints ────────────────────────────────────────────────────

    private boolean hint(Player p, String kind, String text, long everyMs) {
        if (!hintDue(p, kind, everyMs)) return false;
        notice.accept(p, text);
        return true;
    }

    private boolean hintDue(Player p, String kind, long everyMs) {
        String key = p.getUniqueId() + ":" + kind;
        long now = System.currentTimeMillis();
        Long last = hints.get(key);
        if (last != null && now - last < everyMs) return false;
        hints.put(key, now);
        return true;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent e) {
        UUID id = e.getPlayer().getUniqueId();
        pending.remove(id);
        discovered.remove(id);
        rareFound.remove(id);
        sweeping.remove(id);
        String prefix = id + ":";
        hints.keySet().removeIf(k -> k.startsWith(prefix));
    }
}
