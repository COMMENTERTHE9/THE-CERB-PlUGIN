package Gathering;

import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockFormEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.world.ChunkUnloadEvent;
import org.bukkit.event.world.WorldUnloadEvent;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.LongAdder;
import java.util.function.Supplier;

/**
 * Resource blocks that did not grow in the world (docs/design/GATHERING.md, "What can't be farmed"):
 * placed by a player, formed by a generator (lava and water make stone and basalt), or moved by a
 * piston. Breaking one pays nothing. Kept in each chunk's own data, so it is saved with the world
 * (no database), and cached while the chunk is loaded; only blocks that are some resource are
 * remembered. Every call runs on the thread that owns the block's chunk (Folia: its region thread).
 */
public final class PlacedBlocks implements Listener {

    /** Chunk data key: the chunk's placed resource blocks, packed by {@link PlacedIndex}. */
    public static final NamespacedKey KEY = new NamespacedKey("cerberus", "placed_resources");
    private static final int[] NONE = new int[0];

    private final Supplier<GatheringCatalog> catalog;
    /** Per world, per loaded chunk: its sorted set (NONE when it has none). Dropped when the chunk unloads. */
    private final Map<UUID, Map<Long, int[]>> cache = new ConcurrentHashMap<>();
    private final LongAdder chunkReads = new LongAdder(), writes = new LongAdder();

    public PlacedBlocks(Supplier<GatheringCatalog> catalog) {
        this.catalog = catalog;
    }

    /** True when the block at {@code b} was placed, formed or moved there (not natural). */
    public boolean isPlaced(Block b) {
        return PlacedIndex.contains(set(b), pack(b));
    }

    /** Remember {@code b} as placed. */
    public void mark(Block b) {
        change(b, true);
    }

    /** Forget {@code b} (it was broken or blown up). */
    public void unmark(Block b) {
        change(b, false);
    }

    private void change(Block b, boolean placed) {
        int[] set = set(b);
        int v = pack(b);
        int[] next = placed ? PlacedIndex.add(set, v) : PlacedIndex.remove(set, v);
        if (next == set) return;
        chunks(b.getWorld()).put(chunkKey(b.getX() >> 4, b.getZ() >> 4), next);
        PersistentDataContainer pdc = b.getChunk().getPersistentDataContainer(); // saved with the chunk
        if (next.length == 0) pdc.remove(KEY);
        else pdc.set(KEY, PersistentDataType.INTEGER_ARRAY, next);
        writes.increment();
    }

    private int[] set(Block b) {
        Map<Long, int[]> chunks = chunks(b.getWorld());
        long key = chunkKey(b.getX() >> 4, b.getZ() >> 4);
        int[] set = chunks.get(key);
        if (set == null) {
            Chunk c = b.getChunk();
            int[] saved = c.getPersistentDataContainer().get(KEY, PersistentDataType.INTEGER_ARRAY);
            World w = b.getWorld();
            set = saved == null ? NONE : PlacedIndex.clean(saved, w.getMaxHeight() - w.getMinHeight());
            chunks.put(key, set);
            chunkReads.increment();
        }
        return set;
    }

    private Map<Long, int[]> chunks(World w) {
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

    // ── Diagnostics ──────────────────────────────────────────────

    /** Chunks whose set is cached now. */
    public int cachedChunks() {
        int n = 0;
        for (Map<Long, int[]> m : cache.values()) n += m.size();
        return n;
    }

    /** Chunk data reads (cache misses) and writes since start. */
    public long chunkReads() { return chunkReads.sum(); }
    public long writes() { return writes.sum(); }

    // ── Events ───────────────────────────────────────────────────

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent e) {
        if (tracked(e.getBlockPlaced())) mark(e.getBlockPlaced());
    }

    /** Generators: lava and water forming stone, basalt from lava over soul soil next to blue ice. */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onForm(BlockFormEvent e) {
        if (catalog.get().tracked(e.getNewState().getType())) mark(e.getBlock());
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
     * spot is the next one's old spot).
     */
    private void moved(List<Block> blocks, BlockFace dir) {
        List<Block> resources = new ArrayList<>();
        for (Block b : blocks) if (tracked(b)) resources.add(b);
        if (resources.isEmpty()) return;
        for (Block b : resources) unmark(b);
        for (Block b : resources) {
            Block to = b.getRelative(dir);
            if (Bukkit.isOwnedByCurrentRegion(to)) mark(to);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent e) {
        for (Block b : e.blockList()) if (tracked(b)) unmark(b);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent e) {
        for (Block b : e.blockList()) if (tracked(b)) unmark(b);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onChunkUnload(ChunkUnloadEvent e) {
        Map<Long, int[]> chunks = cache.get(e.getWorld().getUID());
        if (chunks != null) chunks.remove(chunkKey(e.getChunk().getX(), e.getChunk().getZ()));
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onWorldUnload(WorldUnloadEvent e) {
        cache.remove(e.getWorld().getUID());
    }
}
