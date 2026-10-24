package Manager;

import Skills.SkillManager;
import cerberus.world.cerb.CerberusPlugin;
import cerberus.world.cerb.DatabaseManager;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tracks per-player chunk discovery for Navigation skill XP.
 * Players gain XP when they enter a chunk they have never visited before.
 * Discovery state persists across sessions via the database.
 */
public class ChunkDiscoveryManager {

    private final CerberusPlugin plugin;
    private final DatabaseManager database;
    private final SkillManager skills;
    private final Map<UUID, Set<ChunkKey>> visitedChunks = new ConcurrentHashMap<>();

    /** XP awarded per newly discovered chunk. */
    /** Navigation XP for each chunk a player enters for the first time. */
    public static final double XP_PER_CHUNK = 5.0;

    public ChunkDiscoveryManager(CerberusPlugin plugin, DatabaseManager database, SkillManager skills) {
        this.plugin = plugin;
        this.database = database;
        this.skills = skills;
    }

    /**
     * Called from the callback after the async DB load in PlayerSessionManager.
     * The chunks parameter is the result of DatabaseManager.loadVisitedChunks().
     */
    public void acceptLoadedChunks(UUID uuid, Set<ChunkKey> chunks) {
        Set<ChunkKey> threadSafe = ConcurrentHashMap.newKeySet();
        threadSafe.addAll(chunks);
        visitedChunks.put(uuid, threadSafe);
    }

    /** Chunks this player has discovered (in memory, no database read); -1 while still loading. */
    public int discoveredCount(UUID uuid) {
        Set<ChunkKey> set = visitedChunks.get(uuid);
        return set == null ? -1 : set.size();
    }

    /** Called from PlayerSessionManager.cleanupPlayer on quit. */
    public void unloadPlayer(UUID uuid) {
        visitedChunks.remove(uuid);
    }

    /**
     * Called from a PlayerMoveEvent handler when the player crosses into a new chunk.
     * Returns true if the chunk was newly discovered (XP was awarded), false if already visited.
     */
    public boolean checkAndRecord(Player player, Chunk chunk) {
        UUID uuid = player.getUniqueId();
        Set<ChunkKey> playerSet = visitedChunks.get(uuid);
        if (playerSet == null) {
            // Player set not loaded yet (still loading async on join). Skip silently.
            return false;
        }

        ChunkKey key = new ChunkKey(chunk.getWorld().getName(), chunk.getX(), chunk.getZ());
        if (!playerSet.add(key)) {
            // Already visited
            return false;
        }

        // Newly discovered — persist to database async, award XP
        Bukkit.getAsyncScheduler().runNow(plugin, task -> {
            database.recordVisitedChunk(uuid, key.worldName(), key.chunkX(), key.chunkZ());
        });

        skills.addXpAndCheckLevelUp("Navigation", XP_PER_CHUNK, player);
        return true;
    }
}
