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

            stmt.execute("""
                CREATE TABLE IF NOT EXISTS player_skills (
                    custom_id   TEXT,
                    skill_name  TEXT,
                    skill_level INTEGER DEFAULT 0,
                    skill_xp    INTEGER DEFAULT 0,
                    PRIMARY KEY (custom_id, skill_name)
                )""");

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

            // Verify tables exist
            int tableCount = 0;
            try (ResultSet rs = stmt.executeQuery(
                    "SELECT name FROM sqlite_master WHERE type='table' AND name IN " +
                    "('player_custom_ids','player_skills','skill_effects','player_magic_find','visited_chunks')")) {
                while (rs.next()) tableCount++;
            }
            plugin.getLogger().info("[Database] Initialized — " + tableCount + "/5 tables verified at " + url);

        } catch (SQLException ex) {
            plugin.getLogger().severe("[Database] Failed to initialize tables! " + ex.getMessage());
            ex.printStackTrace();
        }
    }

    public Connection getConnection() throws SQLException {
        return DriverManager.getConnection(url);
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
    private void initializePlayerSkills(String customId) {
        String sql = "INSERT INTO player_skills(custom_id,skill_name,skill_level,skill_xp) "
                + "VALUES(?,?,0,0) ON CONFLICT(custom_id,skill_name) DO NOTHING";
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


    /** A player's skill rows, loaded strictly (see {@link #loadPlayerSkillsStrict}). */
    public record StrictSkills(String customId, Map<String, Integer> levels, Map<String, Integer> xp) {}

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
            return new StrictSkills(customId, levels, xp);
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
     * Save skills synchronously — callers already run on background threads.
     * Must NOT use asyncSaver: throttle cooldown can silently drop writes.
     */
    public void saveSkillsByCustomId(String customId,
                                     Map<String,Integer> skillLevels,
                                     Map<String,Integer> skillXP) {
        String sql = "INSERT INTO player_skills(custom_id,skill_name,skill_level,skill_xp) "
                + "VALUES(?,?,?,?) "
                + "ON CONFLICT(custom_id,skill_name) DO UPDATE "
                + "SET skill_level=excluded.skill_level,skill_xp=excluded.skill_xp";
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            for (Map.Entry<String,Integer> e : skillLevels.entrySet()) {
                ps.setString(1, customId);
                ps.setString(2, e.getKey());
                ps.setInt(3, e.getValue());
                ps.setInt(4, skillXP.getOrDefault(e.getKey(), 0));
                ps.addBatch();
            }
            ps.executeBatch();
        } catch (SQLException ex) {
            plugin.getLogger().severe("Failed to save skills for " + customId + ": " + ex.getMessage());
            ex.printStackTrace();
        }
    }

    // Save player skills (wrapper method)
    public void savePlayerSkills(UUID playerUUID, String playerName, Map<String, Integer> skillLevels, Map<String, Integer> skillXP) {
        String customId = getOrCreateCustomId(playerUUID, playerName); // Retrieve or create custom_id
        saveSkillsByCustomId(customId, skillLevels, skillXP); // Save skills using custom_id
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

    // Update a single skill for a player
    public void updatePlayerSkill(UUID playerUUID, String playerName, String skillName, int level, int xp) {
        String customId = getOrCreateCustomId(playerUUID, playerName);
        Map<String, Integer> skillLevels = new HashMap<>();
        Map<String, Integer> skillXP = new HashMap<>();
        skillLevels.put(skillName, level);
        skillXP.put(skillName, xp);
        saveSkillsByCustomId(customId, skillLevels, skillXP);
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
