package Manager;

/**
 * Immutable key identifying a specific chunk in a specific world.
 * Used by ChunkDiscoveryManager for per-player visited-chunk tracking.
 */
public record ChunkKey(String worldName, int chunkX, int chunkZ) {}
