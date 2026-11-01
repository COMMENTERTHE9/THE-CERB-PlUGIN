package cerberus.world.cerb;

import Manager.AsyncSaveManager;
import org.apache.commons.lang3.RandomStringUtils;
import org.bukkit.plugin.java.JavaPlugin;
import java.io.File;

import java.sql.Connection;
import java.sql.*;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import cerberus.world.cerb.CerberusPlugin;
// Import HashMap, Map, UUID, SecureRandom, and other necessary classes
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.security.SecureRandom;

public class DatabaseManager {

    private static String url;

    private static final String CUSTOM_ID_PREFIX = "CUST"; // Prefix for the custom ID
    private static final int RANDOM_PART_LENGTH = 6; // Length of the random alphanumeric part
    private static final String ALPHANUMERIC_CHARACTERS = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
    private static final SecureRandom random = new SecureRandom();
    private final AsyncSaveManager asyncSaver;
    private final CerberusPlugin plugin;


    private static final String[] ALL_SKILL_NAMES = {
            "Blade Mastery", "Martial Expertise", "Weapon Mastery", "Ranged Precision",
            "Heavy Armor Training", "Dual Wielding", "Critical Strike",
            "Intelligence", "Arcane Knowledge", "Elemental Mastery", "Summoning",
            "Spell Weaving", "Mana Regeneration", "Defensive Magic",
            "Mining", "Farming", "Woodcutting", "Fishing",
            "Crafting", "Smithing", "Alchemy", "Enchanting",
            "Herbalism", "Cooking", "First Aid", "Stealth",
            "Trap Mastery", "Scavenging", "Repairing", "Trading",
            "Navigation", "Animal Taming", "Riding", "Lockpicking",
            "Survival"
    };

    /** Every skill row a new player gets: the internal skill names (never renamed). */
    public static List<String> allSkillNames() {
        return List.of(ALL_SKILL_NAMES);
    }

    public DatabaseManager(CerberusPlugin plugin) {
        this.plugin    = plugin;
        this.asyncSaver= plugin.getAsyncSaver();
        url            = "jdbc:sqlite:"+plugin.getDataFolder().getAbsolutePath()+"/cerberus.db";
        initializeDatabase();
    }

    private void initializeDatabase() {
        // Ensure data folder exists (safety net — initFiles should have done this)
        File dataFolder = plugin.getDataFolder();
        if (!dataFolder.exists()) {
            dataFolder.mkdirs();
        }

        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement()) {

            stmt.execute("""
                CREATE TABLE IF NOT EXISTS player_custom_ids (
                    player_uuid TEXT PRIMARY KEY,
                    custom_id   TEXT UNIQUE,
                    player_name TEXT
                )""");

            stmt.execute(SkillRows.CREATE_TABLE);
            // 2026-11 (reliability audit): every row says which snapshot wrote it, so an older save can't replace newer
            // progress (SkillRows, Skills/SkillStore). A database from before gets the column with 0 in every row. If the
            // database is busy now (another program holds it), the column is added before the first load or save instead.
            try {
                ensureSeq(conn);
            } catch (SQLException e) {
                plugin.getLogger().severe("[Database] Could not add saved_seq to player_skills now (" + e.getMessage()
                        + "); it is tried again before each skill load or save until it works. Until then those fail"
                        + " safely (a join is refused with nothing changed; a save is kept in memory).");
            }

            stmt.execute("""
                CREATE TABLE IF NOT EXISTS skill_effects (
                    player_uuid  TEXT,
                    skill_name   TEXT,
                    effect_name  TEXT,
                    effect_value REAL,
                    PRIMARY KEY (player_uuid, skill_name, effect_name)
                )""");

            stmt.execute("""
                CREATE TABLE IF NOT EXISTS player_magic_find (
                    player_uuid TEXT PRIMARY KEY,
                    magic_find  REAL DEFAULT 0
                )""");

            stmt.execute("""
                CREATE TABLE IF NOT EXISTS visited_chunks (
                    player_uuid TEXT NOT NULL,
                    world_name  TEXT NOT NULL,
                    chunk_x     INTEGER NOT NULL,
                    chunk_z     INTEGER NOT NULL,
                    PRIMARY KEY (player_uuid, world_name, chunk_x, chunk_z)
                )""");
            stmt.execute(
                "CREATE INDEX IF NOT EXISTS idx_visited_chunks_player ON visited_chunks(player_uuid)");

            // Classes and abilities (docs/design/CLASSES.md §5.9): one versioned document per player.
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS player_classes (
                    player_uuid TEXT PRIMARY KEY,
                    version     INTEGER NOT NULL,
                    data        TEXT NOT NULL,
                    updated_at  INTEGER NOT NULL
                )""");

            // Verify tables exist
            int tableCount = 0;
            try (ResultSet rs = stmt.executeQuery(
                    "SELECT name FROM sqlite_master WHERE type='table' AND name IN " +
                    "('player_custom_ids','player_skills','skill_effects','player_magic_find','visited_chunks','player_classes')")) {
                while (rs.next()) tableCount++;
            }
            plugin.getLogger().info("[Database] Initialized — " + tableCount + "/6 tables verified at " + url);

        } catch (SQLException ex) {
            plugin.getLogger().severe("[Database] Failed to initialize tables! " + ex.getMessage());
            ex.printStackTrace();
        }
    }

    /** saved_seq is in player_skills (added at startup, or before the first skill load or save that could add it). */
    private volatile boolean seqReady;

    /** Adds saved_seq if it isn't there yet (once per run); throws while the database can't be changed. */
    private void ensureSeq(Connection conn) throws SQLException {
        if (seqReady) return;
        synchronized (this) {
            if (seqReady) return;
            if (SkillRows.ensureSeqColumn(conn)) {
                plugin.getLogger().info("[Database] player_skills: added saved_seq (save order); existing rows kept as they were.");
            }
            seqReady = true;
        }
    }

    /** Every query and save opens one connection, so this counts database work (diagnostics). */
    private final java.util.concurrent.atomic.AtomicLong connectionsOpened = new java.util.concurrent.atomic.AtomicLong();

    public Connection getConnection() throws SQLException {
        connectionsOpened.incrementAndGet();
        return DriverManager.getConnection(url);
    }

    /** Connections opened since startup (one per query or save batch). */
    public long connectionsOpened() {
        return connectionsOpened.get();
    }

    // Generate or retrieve custom identifier
    public String getOrCreateCustomId(UUID playerUUID, String playerName) {
        String existing = loadCustomId(playerUUID);
        if (existing != null) return existing;

        String newId = "CUST-" + RandomStringUtils.randomAlphanumeric(6).toUpperCase();
        saveCustomId(playerUUID, newId, playerName);
        initializePlayerSkills(newId);
        return newId;
    }

    private String generateCustomId() {
        // This generates a random 6-character alphanumeric string
        return "CUST-" + RandomStringUtils.randomAlphanumeric(6).toUpperCase();
    }

    /**
     * Save custom ID synchronously — called from background DB thread,
     * must complete before loadPlayerSkillLevels reads it.
     */
    private void saveCustomId(UUID playerUUID, String customId, String playerName) {
        String sql = "INSERT INTO player_custom_ids(player_uuid,custom_id,player_name) VALUES(?,?,?) "
                + "ON CONFLICT(player_uuid) DO NOTHING";
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, playerUUID.toString());
            ps.setString(2, customId);
            ps.setString(3, playerName);
            ps.executeUpdate();
        } catch (SQLException ex) {
            plugin.getLogger().severe("Failed to save customId for " + playerName + ": " + ex.getMessage());
            ex.printStackTrace();
        }
    }

    /**
     * At join: the player's custom id, made (with every skill at level 1) when there is none. Throws when the database
     * can't be read or written, so a failed load never looks like a new player: {@link #getOrCreateCustomId} logs a failed
     * read and makes a new random id with 35 rows of its own (2026-11 audit: orphan rows from a busy database).
     */
    public String getOrCreateCustomIdStrict(UUID playerUUID, String playerName) throws SQLException {
        try {
            return customIdStrict(playerUUID);
        } catch (SQLException e) {
            if (!String.valueOf(e.getMessage()).startsWith("no custom_id row")) throw e; // a read that failed
        }
        String newId = "CUST-" + RandomStringUtils.randomAlphanumeric(6).toUpperCase();
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement("INSERT INTO player_custom_ids(player_uuid,custom_id,player_name) "
                     + "VALUES(?,?,?) ON CONFLICT(player_uuid) DO NOTHING")) {
            ps.setString(1, playerUUID.toString());
            ps.setString(2, newId);
            ps.setString(3, playerName);
            ps.executeUpdate();
        }
        String id = customIdStrict(playerUUID); // ours, or one another join wrote first
        String sql = "INSERT INTO player_skills(custom_id,skill_name,skill_level,skill_xp) "
                + "VALUES(?,?," + Skills.SkillProgression.MIN_LEVEL + ",0) ON CONFLICT(custom_id,skill_name) DO NOTHING";
        try (Connection conn = getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            for (String skill : ALL_SKILL_NAMES) {
                ps.setString(1, id);
                ps.setString(2, skill);
                ps.addBatch();
            }
            ps.executeBatch();
        }
        return id;
    }

    /**
     * The player's stored custom id, for saving their skills (Skills/SkillStore): throws when it can't be read or doesn't
     * exist. Never makes a new one ({@link #getOrCreateCustomId} would, when a read fails, and the save would go to it).
     */
    public String customIdStrict(UUID playerUUID) throws SQLException {
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement("SELECT custom_id FROM player_custom_ids WHERE player_uuid = ?")) {
            ps.setString(1, playerUUID.toString());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next() && rs.getString(1) != null) return rs.getString(1);
            }
        }
        throw new SQLException("no custom_id row for " + playerUUID);
    }

    private String loadCustomId(UUID playerUUID) {
        String query = "SELECT custom_id FROM player_custom_ids WHERE player_uuid = ?";
        try (Connection     conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(query)) {
            ps.setString(1, playerUUID.toString());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getString("custom_id");
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return null;
    }

    /**
     * Initialize skill rows synchronously — called from background DB thread,
     * must complete before loadSkillLevels reads them.
     */
    /** A new character's skill rows: every skill at level 1 with no XP (versions before 2026-10-27 wrote level 0). */
    private void initializePlayerSkills(String customId) {
        String sql = "INSERT INTO player_skills(custom_id,skill_name,skill_level,skill_xp) "
                + "VALUES(?,?," + Skills.SkillProgression.MIN_LEVEL + ",0) ON CONFLICT(custom_id,skill_name) DO NOTHING";
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            for (String skill : ALL_SKILL_NAMES) {
                ps.setString(1, customId);
                ps.setString(2, skill);
                ps.addBatch();
            }
            ps.executeBatch();
        } catch (SQLException ex) {
            plugin.getLogger().severe("Failed to initialize skills for " + customId + ": " + ex.getMessage());
            ex.printStackTrace();
        }
    }


    /**
     * A player's skill rows, loaded strictly (see {@link #loadPlayerSkillsStrict}), and the newest snapshot they were
     * written from ({@code saved_seq}).
     */
    public record StrictSkills(String customId, Map<String, Integer> levels, Map<String, Integer> xp, long savedSeq) {}

    /**
     * Load a player's custom ID and skill rows, throwing on any database error.
     *
     * <p>The other load methods log SQL errors and return empty maps, which the join path could
     * not tell apart from a brand-new player: the session went ACTIVE with default levels and
     * the next XP award saved level 1 over the real stored level. The join path uses this
     * instead and refuses to activate the session when it throws.
     */
    public StrictSkills loadPlayerSkillsStrict(UUID playerUUID) throws SQLException {
        try (Connection conn = getConnection()) {
            ensureSeq(conn);
            String customId;
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT custom_id FROM player_custom_ids WHERE player_uuid = ?")) {
                ps.setString(1, playerUUID.toString());
                try (ResultSet rs = ps.executeQuery()) {
                    customId = rs.next() ? rs.getString("custom_id") : null;
                }
            }
            if (customId == null) {
                throw new SQLException("no custom_id row for " + playerUUID + " (creating it failed)");
            }
            Map<String, Integer> levels = new HashMap<>();
            Map<String, Integer> xp = new HashMap<>();
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT skill_name, skill_level, skill_xp FROM player_skills WHERE custom_id = ?")) {
                ps.setString(1, customId);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        levels.put(rs.getString("skill_name"), rs.getInt("skill_level"));
                        xp.put(rs.getString("skill_name"), rs.getInt("skill_xp"));
                    }
                }
            }
            return new StrictSkills(customId, levels, xp, SkillRows.maxSeq(conn, customId));
        }
    }

    /**
     * Load all skill levels for a given custom_id.
     */
    public Map<String,Integer> loadSkillLevelsByCustomId(String customId) {
        Map<String,Integer> skillLevels = new HashMap<>();
        String sql = "SELECT skill_name, skill_level FROM player_skills WHERE custom_id = ?";

        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, customId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    skillLevels.put(
                            rs.getString("skill_name"),
                            rs.getInt   ("skill_level")
                    );
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("Error loading skill levels for customId " + customId);
            e.printStackTrace();
        }

        return skillLevels;
    }


    // Load skill XP using custom_id
    public Map<String, Integer> loadSkillXPByCustomId(String customId) {
        Map<String, Integer> skillXP = new HashMap<>();
        String query = "SELECT skill_name, skill_xp FROM player_skills WHERE custom_id = ?";

        try (Connection conn = this.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(query)) {
            pstmt.setString(1, customId);
            ResultSet rs = pstmt.executeQuery();

            while (rs.next()) {
                String skillName = rs.getString("skill_name");
                int xp = rs.getInt("skill_xp");
                skillXP.put(skillName, xp);
                plugin.getLogger().fine("[DB] Loaded skill XP: " + skillName + " - XP: " + xp);
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }

        return skillXP;
    }

    /**
     * Writes one snapshot of a player's skills (Skills/SkillStore, on its writer thread): one transaction, kept only where
     * no newer snapshot already wrote the row. Throws on a database error, so the caller can retry and report it.
     */
    public int writeSkills(String customId, Map<String, Integer> skillLevels, Map<String, Integer> skillXP, long seq)
            throws SQLException {
        try (Connection conn = getConnection()) {
            ensureSeq(conn);
            return SkillRows.write(conn, customId, skillLevels, skillXP, seq);
        }
    }

    /** The newest snapshot any skill row was written from (the save order continues from it after a restart). */
    public long maxSkillSeq() throws SQLException {
        try (Connection conn = getConnection()) {
            ensureSeq(conn);
            return SkillRows.maxSeq(conn);
        }
    }

    // Load player skill levels (wrapper method)
    public Map<String, Integer> loadPlayerSkillLevels(UUID playerUUID) {
       String customId = loadCustomId(playerUUID);
        if (customId == null) {
            plugin.getLogger().warning("No customId found for playerUUID: " + playerUUID);
           return new HashMap<>();
       }
        return loadSkillLevelsByCustomId(customId);
    }
    // Load player skill XP (wrapper method)
    public Map<String, Integer> loadPlayerSkillXP(UUID playerUUID) {
        String customId = loadCustomId(playerUUID);
        if (customId == null) {
            plugin.getLogger().warning("No customId found for playerUUID: " + playerUUID);
            return new HashMap<>();
        }
        return loadSkillXPByCustomId(customId);
    }

    // Fix null custom_ids in the player_skills table
    public void fixNullCustomIds() {
        String querySelect = "SELECT rowid, custom_id FROM player_skills WHERE custom_id IS NULL";
        String queryUpdate = "UPDATE player_skills SET custom_id = ? WHERE rowid = ?";
        String querySelectPlayerUUID = "SELECT player_uuid FROM player_custom_ids WHERE custom_id = ?";

        try (Connection conn = this.getConnection();
             PreparedStatement pstmtSelect = conn.prepareStatement(querySelect);
             PreparedStatement pstmtUpdate = conn.prepareStatement(queryUpdate);
             PreparedStatement pstmtSelectPlayerUUID = conn.prepareStatement(querySelectPlayerUUID)) {

            ResultSet rs = pstmtSelect.executeQuery();

            while (rs.next()) {
                int rowId = rs.getInt("rowid");
                String customId = rs.getString("custom_id");

                if (customId == null) {
                    // Attempt to retrieve player_uuid associated with this skill entry
                    // This requires that you have some way to map skill entries to player_uuid
                    // Since custom_id is null, this may not be possible without additional data
                    // You might need to handle this case based on your application logic
                    plugin.getLogger().warning("Cannot fix null custom_id for rowid: " + rowId);
                    continue;
                }

                // Retrieve player_uuid associated with custom_id
                pstmtSelectPlayerUUID.setString(1, customId);
                ResultSet rsPlayerUUID = pstmtSelectPlayerUUID.executeQuery();

                if (rsPlayerUUID.next()) {
                    String playerUUID = rsPlayerUUID.getString("player_uuid");

                    if (playerUUID != null) {
                        // Update the custom_id in player_skills
                        pstmtUpdate.setString(1, customId);
                        pstmtUpdate.setInt(2, rowId);
                        pstmtUpdate.executeUpdate();
                    } else {
                        plugin.getLogger().warning("No player_uuid found for custom_id: " + customId);
                    }
                } else {
                    plugin.getLogger().warning("No player_custom_ids entry found for custom_id: " + customId);
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    // Save skill effects to the database

    public void saveSkillEffect(UUID playerUUID,
                                String skillName,
                                String effectName,
                                double value) {
        String sql = "INSERT INTO skill_effects(player_uuid,skill_name,effect_name,effect_value) "
                + "VALUES(?,?,?,?) "
                + "ON CONFLICT(player_uuid,skill_name,effect_name) DO UPDATE "
                + "SET effect_value=excluded.effect_value";
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, playerUUID.toString());
            ps.setString(2, skillName);
            ps.setString(3, effectName);
            ps.setDouble(4, value);
            ps.executeUpdate();
        } catch (SQLException ex) {
            plugin.getLogger().severe("Failed to save skill effect: " + ex.getMessage());
            ex.printStackTrace();
        }
    }


    // Load skill effects from the database
    public double loadSkillEffect(UUID playerUUID, String skillName, String effectName, double defaultValue) {
        String query = "SELECT effect_value FROM skill_effects WHERE player_uuid = ? AND skill_name = ? AND effect_name = ?";

        try (Connection conn = this.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(query)) {

            pstmt.setString(1, playerUUID.toString());
            pstmt.setString(2, skillName);
            pstmt.setString(3, effectName);
            ResultSet rs = pstmt.executeQuery();

            if (rs.next()) {
                return rs.getDouble("effect_value");
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return defaultValue;
    }

    // Save magic find to the database (for MagicFindManager)
    public void saveMagicFind(UUID playerUUID, double magicFind) {
        String sql = "REPLACE INTO player_magic_find(player_uuid,magic_find) VALUES(?,?)";
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, playerUUID.toString());
            ps.setDouble(2, magicFind);
            ps.executeUpdate();
        } catch (SQLException ex) {
            plugin.getLogger().severe("Failed to save magic find: " + ex.getMessage());
            ex.printStackTrace();
        }
    }

    // Load magic find from the database (for MagicFindManager)
    public double loadMagicFind(UUID playerUUID) {
        String query = "SELECT magic_find FROM player_magic_find WHERE player_uuid = ?";
        try (Connection conn = this.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(query)) {
            pstmt.setString(1, playerUUID.toString());
            ResultSet rs = pstmt.executeQuery();
            if (rs.next()) {
                return rs.getDouble("magic_find");
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return 0.0; // Default to 0 if no record found
    }

    // ── Classes ──────────────────────────────────────────────────

    /** A stored classes document and the stamp of the snapshot it came from. */
    public record ClassData(String data, long stamp) {}

    /**
     * One player's classes document (docs/design/CLASSES.md §5.9), or null when none is stored.
     * Throws when the read fails, so a failed load never looks like a new player (whose empty
     * document would then overwrite the stored one).
     */
    public ClassData loadClassData(UUID playerUUID) throws SQLException {
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement("SELECT data, updated_at FROM player_classes WHERE player_uuid = ?")) {
            ps.setString(1, playerUUID.toString());
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? new ClassData(rs.getString("data"), rs.getLong("updated_at")) : null;
            }
        }
    }

    /**
     * Store a player's classes document. {@code stamp} orders the snapshots (it only ever grows
     * per player): a write carrying an older stamp than the stored one changes nothing, so a
     * delayed save can never overwrite newer progress, whichever thread finishes first.
     */
    public void saveClassData(UUID playerUUID, int version, String data, long stamp) {
        try {
            writeClassData(playerUUID, version, data, stamp);
        } catch (SQLException ex) {
            plugin.getLogger().severe("Failed to save classes for " + playerUUID + ": " + ex.getMessage());
        }
    }

    /**
     * {@link #saveClassData}, throwing on a database error (Classes/ClassDocStore tries again and keeps it). Returns 1 when
     * the document was written, 0 when the stored one is newer (kept as it is).
     */
    public int writeClassData(UUID playerUUID, int version, String data, long stamp) throws SQLException {
        String sql = "INSERT INTO player_classes(player_uuid, version, data, updated_at) VALUES(?,?,?,?) "
                + "ON CONFLICT(player_uuid) DO UPDATE SET version = excluded.version, data = excluded.data, "
                + "updated_at = excluded.updated_at WHERE excluded.updated_at >= player_classes.updated_at";
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, playerUUID.toString());
            ps.setInt(2, version);
            ps.setString(3, data);
            ps.setLong(4, stamp);
            return ps.executeUpdate();
        }
    }

    // ── Chunk Discovery ──────────────────────────────────────────

    /**
     * Load all visited chunks for a player. Called from background thread during join.
     */
    public java.util.Set<Manager.ChunkKey> loadVisitedChunks(UUID playerUUID) {
        java.util.Set<Manager.ChunkKey> chunks = new java.util.HashSet<>();
        String sql = "SELECT world_name, chunk_x, chunk_z FROM visited_chunks WHERE player_uuid = ?";
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, playerUUID.toString());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    chunks.add(new Manager.ChunkKey(
                            rs.getString("world_name"),
                            rs.getInt("chunk_x"),
                            rs.getInt("chunk_z")));
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("Failed to load visited chunks for " + playerUUID + ": " + e.getMessage());
        }
        return chunks;
    }

    /**
     * Record a single visited chunk. Uses INSERT OR IGNORE for idempotency.
     * Called from async scheduler — never on a tick thread.
     */
    public void recordVisitedChunk(UUID playerUUID, String worldName, int chunkX, int chunkZ) {
        String sql = "INSERT OR IGNORE INTO visited_chunks(player_uuid, world_name, chunk_x, chunk_z) VALUES(?,?,?,?)";
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, playerUUID.toString());
            ps.setString(2, worldName);
            ps.setInt(3, chunkX);
            ps.setInt(4, chunkZ);
            ps.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().severe("Failed to record visited chunk: " + e.getMessage());
        }
    }
}
