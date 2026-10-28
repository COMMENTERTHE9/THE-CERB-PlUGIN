package Gathering;

import org.bukkit.Bukkit;
import org.bukkit.ExplosionResult;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockFadeEvent;
import org.bukkit.event.block.BlockFertilizeEvent;
import org.bukkit.event.block.BlockFormEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.block.BlockSpreadEvent;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.world.ChunkUnloadEvent;
import org.bukkit.event.world.StructureGrowEvent;
import org.bukkit.event.world.WorldUnloadEvent;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.LongAdder;
import java.util.function.Supplier;

/**
 * Resource blocks that did not come with the world (docs/design/GATHERING.md, "What can't be farmed"; docs/design/WOODCUTTING.md
 * §5): placed by a player, formed by a generator (lava and water make stone and basalt), moved by a piston, faded or
 * spread into a resource, or set by a mob ({@link Origin#PLACED}, pays nothing); and the logs of trees grown from
 * something a player planted, on their own ({@link Origin#GROWN}) or finished by bone meal ({@link Origin#BONEMEAL}),
 * which pay a share. Blocks placed before this was installed, and blocks of a material added to resources.yml later,
 * count as natural. Kept in each chunk's own data, so it is saved with the world (no database), and cached while the
 * chunk is loaded; only blocks that are some resource are remembered. Every call runs on the thread that owns the
 * block's chunk (Folia: its region thread).
 */
public final class PlacedBlocks implements Listener {

    /** Chunk data key: the chunk's placed resource blocks, packed by {@link PlacedIndex}. */
    public static final NamespacedKey KEY = new NamespacedKey("cerberus", "placed_resources");
    /** Chunk data keys: logs of trees that grew on their own, and of trees bone meal finished (the same packing). */
    public static final NamespacedKey GROWN_KEY = new NamespacedKey("cerberus", "grown_resources"),
            BONEMEAL_KEY = new NamespacedKey("cerberus", "bonemeal_resources");

    private final Plugin plugin;
    private final Supplier<GatheringCatalog> catalog;
    /** Per world, per loaded chunk: its marks (ChunkMarks.NONE when it has none). Dropped when the chunk unloads. */
    private final Map<UUID, Map<Long, ChunkMarks>> cache = new ConcurrentHashMap<>();
    private final LongAdder chunkReads = new LongAdder(), writes = new LongAdder();
    /**
     * Growth seen without bone meal and not settled yet, by the position of what grew (a sapling, fungus, propagule). A
     * dispenser's bone meal reports its growth that way too and then fires a fertilize event in the same call, which
     * settles it as bone meal; growth by random ticks has none and settles as grown one tick later.
     */
    private final Map<GrowthAt, List<org.bukkit.block.BlockState>> unsettled = new ConcurrentHashMap<>();

    private record GrowthAt(UUID world, int x, int y, int z) {
        static GrowthAt of(Location l) { return new GrowthAt(l.getWorld().getUID(), l.getBlockX(), l.getBlockY(), l.getBlockZ()); }
    }

    public PlacedBlocks(Plugin plugin, Supplier<GatheringCatalog> catalog) {
        this.plugin = plugin;
        this.catalog = catalog;
    }

    /** Where the block at {@code b} came from. */
    public Origin origin(Block b) {
        return marks(b).origin(pack(b));
    }

    /** True when the block at {@code b} was placed, formed or moved there (pays nothing). */
    public boolean isPlaced(Block b) {
        return origin(b) == Origin.PLACED;
    }

    /** Remember {@code b} as placed. */
    public void mark(Block b) {
        change(b, Origin.PLACED);
    }

    /** Remember {@code b} as part of a tree that grew: on its own, or finished by bone meal. */
    public void markGrown(Block b, boolean boneMeal) {
        change(b, boneMeal ? Origin.BONEMEAL : Origin.GROWN);
    }

    /** Forget {@code b} (it was broken or blown up). */
    public void unmark(Block b) {
        change(b, Origin.NATURAL);
    }

    private void change(Block b, Origin o) {
        ChunkMarks marks = marks(b);
        ChunkMarks next = marks.with(pack(b), o);
        if (next == marks) return;
        chunks(b.getWorld()).put(chunkKey(b.getX() >> 4, b.getZ() >> 4), next);
        PersistentDataContainer pdc = b.getChunk().getPersistentDataContainer(); // saved with the chunk
        for (Origin kind : List.of(Origin.PLACED, Origin.GROWN, Origin.BONEMEAL)) {
            int[] now = next.of(kind);
            if (now == marks.of(kind)) continue; // only the sets that changed are written
            NamespacedKey key = key(kind);
            if (now.length == 0) pdc.remove(key);
            else pdc.set(key, PersistentDataType.INTEGER_ARRAY, now);
        }
        writes.increment();
    }

    private static NamespacedKey key(Origin o) {
        return o == Origin.GROWN ? GROWN_KEY : o == Origin.BONEMEAL ? BONEMEAL_KEY : KEY;
    }

    private ChunkMarks marks(Block b) {
        Map<Long, ChunkMarks> chunks = chunks(b.getWorld());
        long key = chunkKey(b.getX() >> 4, b.getZ() >> 4);
        ChunkMarks marks = chunks.get(key);
        if (marks == null) {
            PersistentDataContainer pdc = b.getChunk().getPersistentDataContainer();
            World w = b.getWorld();
            int height = w.getMaxHeight() - w.getMinHeight();
            marks = new ChunkMarks(read(pdc, KEY, height), read(pdc, GROWN_KEY, height), read(pdc, BONEMEAL_KEY, height));
            if (marks.isEmpty()) marks = ChunkMarks.NONE;
            chunks.put(key, marks);
            chunkReads.increment();
        }
        return marks;
    }

    private static int[] read(PersistentDataContainer pdc, NamespacedKey key, int height) {
        int[] saved = pdc.get(key, PersistentDataType.INTEGER_ARRAY);
        return saved == null ? null : PlacedIndex.clean(saved, height);
    }

    private Map<Long, ChunkMarks> chunks(World w) {
        return cache.computeIfAbsent(w.getUID(), id -> new ConcurrentHashMap<>());
    }

    private static int pack(Block b) {
        return PlacedIndex.pack(b.getX(), b.getY(), b.getZ(), b.getWorld().getMinHeight());
    }

    private static long chunkKey(int cx, int cz) {
        return ((long) cx << 32) | (cz & 0xffffffffL);
    }

    private boolean tracked(Block b) {
        return catalog.get().tracked(b.getType());
    }

    /** A herb's block (docs/design/HERBALISM.md §2): remembered when a player places it, forgotten when one grows there. */
    private boolean herb(Material m) {
        return catalog.get().herbalism().tracked(m);
    }

    /** A herb grew at {@code b} (spread, bone meal, a death's wither rose): a mark left from one placed there before is forgotten. */
    private void grewHerb(Block b, Material now) {
        if (herb(now) && isPlaced(b)) unmark(b);
    }

    // ── Diagnostics ──────────────────────────────────────────────

    /** Chunks whose set is cached now. */
    public int cachedChunks() {
        int n = 0;
        for (Map<Long, ChunkMarks> m : cache.values()) n += m.size();
        return n;
    }

    /** Chunk data reads (cache misses) and writes since start. */
    public long chunkReads() { return chunkReads.sum(); }
    public long writes() { return writes.sum(); }

    // ── Events ───────────────────────────────────────────────────

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent e) {
        Block b = e.getBlockPlaced();
        if (tracked(b) || herb(b.getType())) mark(b); // a tall flower is remembered by its lower half, where it is placed
    }

    /**
     * A melon or pumpkin from its stem, a cane or cactus segment growing up (docs/design/FARMING_COOKING.md §3.2): it grew
     * there, so a mark left from a block placed and lost there before (a cactus a neighbour knocked off) is forgotten. Only
     * those crops come here: a wheat stage is no block a player can place.
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onCropGrow(org.bukkit.event.block.BlockGrowEvent e) {
        if (catalog.get().farming().placeable(e.getNewState().getType()) && isPlaced(e.getBlock())) unmark(e.getBlock());
    }

    /** Generators: lava and water forming stone, basalt from lava over soul soil next to blue ice. */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onForm(BlockFormEvent e) {
        if (catalog.get().tracked(e.getNewState().getType())) mark(e.getBlock());
    }

    /** A spread that makes a resource block (its own event, not a form). */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onSpread(BlockSpreadEvent e) {
        if (catalog.get().tracked(e.getNewState().getType())) mark(e.getBlock());
        else grewHerb(e.getBlock(), e.getNewState().getType()); // a mushroom spreading, kelp or vines growing
    }

    /** A block that fades into a resource block (covered nylium becomes netherrack): not grown there either. */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onFade(BlockFadeEvent e) {
        if (catalog.get().tracked(e.getNewState().getType())) mark(e.getBlock());
    }

    /** A mob or falling block changing a block: a resource it makes counts as placed; one it takes away is forgotten. */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityChange(EntityChangeBlockEvent e) {
        if (catalog.get().tracked(e.getTo())) mark(e.getBlock());
        else if (tracked(e.getBlock())) unmark(e.getBlock());
        else grewHerb(e.getBlock(), e.getTo()); // the wither rose a wither's victim leaves
    }

    /**
     * A tree, huge fungus or huge mushroom grown from something a player planted (docs/design/WOODCUTTING.md §5): its logs
     * and stems are remembered as grown, or as bone-meal grown when bone meal finished it, and pay a share of a wild tree's
     * XP (resources.yml woodcutting.grown / bone-meal). Any other resource it makes counts as placed. The world's own trees
     * are generated, never grown, so they never come through here.
     *
     * <p>Marks are written once the growth is final (CLAUDE.md: the final event state). A player's bone meal reports
     * {@code isFromBonemeal} and is followed by a fertilize event, which decides ({@link #onFertilize}). A dispenser's
     * bone meal reports false here and is followed by a fertilize event too; random ticks report false and are not. So
     * growth without the flag waits one tick, and what no fertilize event claimed by then grew on its own.
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onGrow(StructureGrowEvent e) {
        if (e.isFromBonemeal()) return;
        List<org.bukkit.block.BlockState> states = List.copyOf(e.getBlocks());
        GatheringCatalog c = catalog.get();
        if (states.stream().noneMatch(st -> c.tracked(st.getType()))) return;
        GrowthAt at = GrowthAt.of(e.getLocation());
        unsettled.put(at, states);
        Bukkit.getRegionScheduler().run(plugin, e.getLocation(), t -> {
            List<org.bukkit.block.BlockState> grown = unsettled.remove(at);
            if (grown != null) record(grown, Origin.GROWN, true);
        });
    }

    /** Bone meal, from a player or a dispenser: the growth goes ahead unless this is cancelled, so it settles here. */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onFertilize(BlockFertilizeEvent e) {
        unsettled.remove(GrowthAt.of(e.getBlock().getLocation()));
        if (e.isCancelled()) return;
        record(e.getBlocks(), Origin.BONEMEAL, false);
        // Flowers bone meal grows on grass are grown, not placed. A herb placed by hand stays placed when bone meal grows it
        // where it stands (a fern into a large fern, one more petal), and so does what it spreads to: nothing wild came of it.
        Block fertilized = e.getBlock();
        boolean fromPlaced = herb(fertilized.getType()) && isPlaced(fertilized);
        for (org.bukkit.block.BlockState st : e.getBlocks()) {
            if (!herb(st.getType())) continue;
            Block b = st.getBlock();
            if (!b.getWorld().isChunkLoaded(b.getX() >> 4, b.getZ() >> 4) || !Bukkit.isOwnedByCurrentRegion(b)) continue;
            if (fromPlaced) mark(b);
            else grewHerb(b, st.getType());
        }
    }

    /**
     * Remember what a growth made: a tree's logs as {@code treeOrigin}, any other resource as placed. {@code placedNow}:
     * the blocks are in the world already (a tick later), so only those still there are marked; otherwise they are about
     * to be set. Blocks another region owns, or in an unloaded chunk, are left alone.
     */
    private void record(List<org.bukkit.block.BlockState> states, Origin treeOrigin, boolean placedNow) {
        GatheringCatalog c = catalog.get();
        for (org.bukkit.block.BlockState st : states) {
            GatheringCatalog.ResourceDef r = c.resource(st.getType());
            if (r == null) continue;
            Block b = st.getBlock();
            if (!b.getWorld().isChunkLoaded(b.getX() >> 4, b.getZ() >> 4) || !Bukkit.isOwnedByCurrentRegion(b)) continue;
            if (placedNow && b.getType() != st.getType()) continue;
            if (r.isTree()) change(b, treeOrigin);
            else mark(b);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPistonExtend(BlockPistonExtendEvent e) {
        moved(e.getBlocks(), e.getDirection());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPistonRetract(BlockPistonRetractEvent e) {
        moved(e.getBlocks(), e.getDirection());
    }

    /**
     * Blocks a piston moves one step {@code dir}: every resource block among them counts as placed
     * at its new spot, and its old spot is forgotten (all old spots first: in a row, one block's new
     * spot is the next one's old spot). A block the piston breaks instead (a melon, cane) goes nowhere.
     */
    private void moved(List<Block> blocks, BlockFace dir) {
        List<Block> resources = new ArrayList<>();
        for (Block b : blocks) if (tracked(b)) resources.add(b);
        if (resources.isEmpty()) return;
        List<Block> moving = new ArrayList<>();
        for (Block b : resources) if (b.getPistonMoveReaction() != org.bukkit.block.PistonMoveReaction.BREAK) moving.add(b);
        for (Block b : resources) unmark(b);
        for (Block b : moving) {
            Block to = b.getRelative(dir);
            if (Bukkit.isOwnedByCurrentRegion(to)) mark(to);
        }
    }

    /**
     * An explosion that destroys blocks forgets them. A wind charge, a breeze or a mace's Wind Burst only triggers blocks
     * (doors, buttons) and reports every block it reached: those stay, and so do their marks.
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent e) {
        if (destroys(e.getExplosionResult())) for (Block b : e.blockList()) if (tracked(b)) unmark(b);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent e) {
        if (destroys(e.getExplosionResult())) for (Block b : e.blockList()) if (tracked(b)) unmark(b);
    }

    static boolean destroys(ExplosionResult r) {
        return r == ExplosionResult.DESTROY || r == ExplosionResult.DESTROY_WITH_DECAY;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onChunkUnload(ChunkUnloadEvent e) {
        Map<Long, ChunkMarks> chunks = cache.get(e.getWorld().getUID());
        if (chunks != null) chunks.remove(chunkKey(e.getChunk().getX(), e.getChunk().getZ()));
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onWorldUnload(WorldUnloadEvent e) {
        cache.remove(e.getWorld().getUID());
    }
}
