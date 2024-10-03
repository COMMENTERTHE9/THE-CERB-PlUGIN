package cerberus.world.cerb;

import org.apache.commons.lang3.RandomStringUtils;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
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

    // List of all skill names
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

    public DatabaseManager(String pluginFolder) {
        // SQLite connection string
        this.url = "jdbc:sqlite:" + pluginFolder + "/cerberus.db";  // Adjust the path as needed
        initializeDatabase();
    }

    private void initializeDatabase() {
        // SQL queries to create the necessary tables if they don't exist
        String createSkillsTable = "CREATE TABLE IF NOT EXISTS player_skills (" +
                "custom_id TEXT," +
                "skill_name TEXT," +
                "skill_level INTEGER DEFAULT 0," +
                "skill_xp INTEGER DEFAULT 0," +
                "PRIMARY KEY (custom_id, skill_name))"; // Use custom_id as part of the primary key

        String createEffectsTable = "CREATE TABLE IF NOT EXISTS skill_effects (" +
                "player_uuid TEXT," +
                "skill_name TEXT," +
                "effect_name TEXT," +
                "effect_value REAL," +
                "PRIMARY KEY (player_uuid, skill_name, effect_name))";

        String createMagicFindTable = "CREATE TABLE IF NOT EXISTS player_magic_find (" +
                "player_uuid TEXT PRIMARY KEY," +
                "magic_find REAL DEFAULT 0)";

        // Table to store the unique custom_id for each player
        String createCustomIdsTable = "CREATE TABLE IF NOT EXISTS player_custom_ids (" +
                "player_uuid TEXT PRIMARY KEY," + // Ensure each player has one unique entry
                "custom_id TEXT UNIQUE," +        // Ensure the custom_id is unique and doesn't change
                "player_name TEXT)";              // Store the player's name as well

        try (Connection conn = this.getConnection();
             PreparedStatement pstmt1 = conn.prepareStatement(createSkillsTable);
             PreparedStatement pstmt2 = conn.prepareStatement(createEffectsTable);
             PreparedStatement pstmt3 = conn.prepareStatement(createMagicFindTable);
             PreparedStatement pstmt4 = conn.prepareStatement(createCustomIdsTable)) {

            pstmt1.execute();
            pstmt2.execute();
            pstmt3.execute();
            pstmt4.execute();

        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public static Connection getConnection() throws SQLException {
        return DriverManager.getConnection(url);
    }

    // Generate or retrieve custom identifier
    public String getOrCreateCustomId(UUID playerUUID, String playerName) {
        String customId = loadCustomId(playerUUID); // Try to load an existing custom_id

        if (customId == null) {
            // Generate a new custom identifier if it does not exist
            customId = generateCustomId();
            saveCustomId(playerUUID, customId, playerName); // Save the new custom_id only once
            initializePlayerSkills(customId); // Initialize skills for the new player
        }

        return customId;
    }

    private String generateCustomId() {
        // This generates a random 6-character alphanumeric string
        return "CUST-" + RandomStringUtils.randomAlphanumeric(6).toUpperCase();
    }

    private void saveCustomId(UUID playerUUID, String customId, String playerName) {
        // Insert only if the player does not already have an entry
        String query = "INSERT INTO player_custom_ids (player_uuid, custom_id, player_name) " +
                "VALUES (?, ?, ?) " +
                "ON CONFLICT(player_uuid) DO NOTHING"; // Do nothing if there's already a custom_id for this player

        try (Connection conn = this.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(query)) {
            pstmt.setString(1, playerUUID.toString());
            pstmt.setString(2, customId);
            pstmt.setString(3, playerName);
            pstmt.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    // Load custom identifier from the database
    private String loadCustomId(UUID playerUUID) {
        String query = "SELECT custom_id FROM player_custom_ids WHERE player_uuid = ?";
        try (Connection conn = this.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(query)) {
            pstmt.setString(1, playerUUID.toString());
            ResultSet rs = pstmt.executeQuery();
            if (rs.next()) {
                return rs.getString("custom_id");
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return null; // Return null if not found
    }

    // Initialize all skills for a new player
    private void initializePlayerSkills(String customId) {
        String insertSkill = "INSERT INTO player_skills (custom_id, skill_name, skill_level, skill_xp) " +
                "VALUES (?, ?, 0, 0) " +
                "ON CONFLICT(custom_id, skill_name) DO NOTHING";

        try (Connection conn = this.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(insertSkill)) {

            for (String skillName : ALL_SKILL_NAMES) {
                pstmt.setString(1, customId);
                pstmt.setString(2, skillName);
                pstmt.addBatch();
            }
            pstmt.executeBatch();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    // Load skill levels using custom_id
    public Map<String, Integer> loadSkillLevelsByCustomId(String customId) {
        Map<String, Integer> skillLevels = new HashMap<>();
        String query = "SELECT skill_name, skill_level FROM player_skills WHERE custom_id = ?";

        try (Connection conn = this.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(query)) {
            pstmt.setString(1, customId);
            ResultSet rs = pstmt.executeQuery();

            while (rs.next()) {
                String skillName = rs.getString("skill_name");
                int level = rs.getInt("skill_level");
                skillLevels.put(skillName, level);
                System.out.println("[DEBUG] Loaded skill level by custom_id: " + skillName + " - Level: " + level);
            }
        } catch (SQLException e) {
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
                System.out.println("[DEBUG] Loaded skill XP by custom_id: " + skillName + " - XP: " + xp);
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }

        return skillXP;
    }

    // Save skills using custom_id
    public void saveSkillsByCustomId(String customId, Map<String, Integer> skillLevels, Map<String, Integer> skillXP) {
        if (customId == null) {
            System.err.println("[ERROR] customId is null. Cannot save skills.");
            return;
        }

        String insertOrUpdate = "INSERT INTO player_skills (custom_id, skill_name, skill_level, skill_xp) " +
                "VALUES (?, ?, ?, ?) " +
                "ON CONFLICT(custom_id, skill_name) " + // Make sure this matches the primary key
                "DO UPDATE SET skill_level = excluded.skill_level, skill_xp = excluded.skill_xp";

        try (Connection conn = this.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(insertOrUpdate)) {

            for (Map.Entry<String, Integer> entry : skillLevels.entrySet()) {
                String skillName = entry.getKey();
                int level = entry.getValue();
                int xp = skillXP.getOrDefault(skillName, 0);

                pstmt.setString(1, customId);
                pstmt.setString(2, skillName);
                pstmt.setInt(3, level);
                pstmt.setInt(4, xp);
                pstmt.addBatch();
            }

            pstmt.executeBatch();
        } catch (SQLException e) {
            e.printStackTrace();
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
            System.err.println("[ERROR] No customId found for playerUUID: " + playerUUID);
            return new HashMap<>();
        }
        return loadSkillLevelsByCustomId(customId);
    }

    // Load player skill XP (wrapper method)
    public Map<String, Integer> loadPlayerSkillXP(UUID playerUUID) {
        String customId = loadCustomId(playerUUID);
        if (customId == null) {
            System.err.println("[ERROR] No customId found for playerUUID: " + playerUUID);
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
                    System.err.println("[ERROR] Cannot fix null custom_id for rowid: " + rowId);
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
                        System.err.println("[ERROR] No player_uuid found for custom_id: " + customId);
                    }
                } else {
                    System.err.println("[ERROR] No player_custom_ids entry found for custom_id: " + customId);
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    // Save skill effects to the database
    public void saveSkillEffect(UUID playerUUID, String skillName, String effectName, double value) {
        String insertOrUpdate = "INSERT INTO skill_effects (player_uuid, skill_name, effect_name, effect_value) " +
                "VALUES (?, ?, ?, ?) " +
                "ON CONFLICT(player_uuid, skill_name, effect_name) " +
                "DO UPDATE SET effect_value = excluded.effect_value";

        try (Connection conn = this.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(insertOrUpdate)) {

            pstmt.setString(1, playerUUID.toString());
            pstmt.setString(2, skillName);
            pstmt.setString(3, effectName);
            pstmt.setDouble(4, value);
            pstmt.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
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
        String query = "REPLACE INTO player_magic_find (player_uuid, magic_find) VALUES (?, ?)";
        try (Connection conn = this.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(query)) {
            pstmt.setString(1, playerUUID.toString());
            pstmt.setDouble(2, magicFind);
            pstmt.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
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
}
