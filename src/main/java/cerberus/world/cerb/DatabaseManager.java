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
        // build a single “profile” table: one row per player, two columns per skill
        StringBuilder ddl = new StringBuilder("""
        CREATE TABLE IF NOT EXISTS player_profiles (
          player_uuid TEXT PRIMARY KEY,
          player_name TEXT
        """);
        for (String skill : ALL_SKILL_NAMES) {
            // turn “Blade Mastery” → “blade_mastery”
            String col = skill.toLowerCase().replaceAll("[^a-z0-9]+","_");
            ddl.append(",\n  ").append(col).append("_level INTEGER DEFAULT 0");
            ddl.append(",\n  ").append(col).append("_xp    INTEGER DEFAULT 0");
        }
        ddl.append("\n);");

        try (Connection conn = getConnection();
             Statement  stmt = conn.createStatement()) {
            stmt.execute(ddl.toString());
        } catch (SQLException ex) {
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

    private void saveCustomId(UUID playerUUID, String customId, String playerName) {
        String sql = "INSERT INTO player_custom_ids(player_uuid,custom_id,player_name) VALUES(?,?,?) "
                + "ON CONFLICT(player_uuid) DO NOTHING";
        asyncSaver.scheduleDbSave(
                "customId:" + playerUUID,
                () -> {
                    try (Connection conn = getConnection();
                         PreparedStatement ps = conn.prepareStatement(sql)) {
                        ps.setString(1, playerUUID.toString());
                        ps.setString(2, customId);
                        ps.setString(3, playerName);
                        ps.executeUpdate();
                    } catch (SQLException ex) {
                        ex.printStackTrace();
                    }
                }
        );
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

    private void initializePlayerSkills(String customId) {
        String sql = "INSERT INTO player_skills(custom_id,skill_name,skill_level,skill_xp) "
                + "VALUES(?,?,0,0) ON CONFLICT(custom_id,skill_name) DO NOTHING";
        asyncSaver.scheduleDbSave(
                "initSkills:" + customId,
                () -> {
                    try (Connection conn = getConnection();
                         PreparedStatement ps = conn.prepareStatement(sql)) {
                        for (String skill : ALL_SKILL_NAMES) {
                            ps.setString(1, customId);
                            ps.setString(2, skill);
                            ps.addBatch();
                        }
                        ps.executeBatch();
                    } catch (SQLException ex) {
                        ex.printStackTrace();
                    }
                }
        );
    }


    // Load skill levels using custom_id
    /**
     * Load all skill levels for a given player UUID.
     */
    public Map<String,Integer> loadSkillLevels(UUID playerUUID) {
        Map<String,Integer> skillLevels = new HashMap<>();
        String sql = """
        SELECT skill_name, skill_level
          FROM player_skills
         WHERE player_uuid = ?
        """;

        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, playerUUID.toString());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    skillLevels.put(
                            rs.getString("skill_name"),
                            rs.getInt   ("skill_level")
                    );
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("Error loading skill levels for " + playerUUID);
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
    public void saveSkillsByCustomId(String customId,
                                     Map<String,Integer> skillLevels,
                                     Map<String,Integer> skillXP) {
        String sql = "INSERT INTO player_skills(custom_id,skill_name,skill_level,skill_xp) "
                + "VALUES(?,?,?,?) "
                + "ON CONFLICT(custom_id,skill_name) DO UPDATE "
                + "SET skill_level=excluded.skill_level,skill_xp=excluded.skill_xp";
        asyncSaver.scheduleDbSave(
                "saveSkills:" + customId,
                () -> {
                    try (Connection conn = getConnection();
                         PreparedStatement ps = conn.prepareStatement(sql)) {
                        for (Map.Entry<String,Integer> e : skillLevels.entrySet()) {
                            ps.setString(1, customId);
                            ps.setString(2, e.getKey());
                            ps.setInt(3, e.getValue());
                            ps.setInt(4, skillXP.getOrDefault(e.getKey(),0));
                            ps.addBatch();
                        }
                        ps.executeBatch();
                    } catch (SQLException ex) {
                        ex.printStackTrace();
                    }
                }
        );
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
        return loadSkillLevels(playerUUID);
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

    public void saveSkillEffect(UUID playerUUID,
                                String skillName,
                                String effectName,
                                double value) {
        String sql = "INSERT INTO skill_effects(player_uuid,skill_name,effect_name,effect_value) "
                + "VALUES(?,?,?,?) "
                + "ON CONFLICT(player_uuid,skill_name,effect_name) DO UPDATE "
                + "SET effect_value=excluded.effect_value";
        asyncSaver.scheduleDbSave(
                "skillEffect:" + playerUUID + ":" + skillName + ":" + effectName,
                () -> {
                    try (Connection conn = getConnection();
                         PreparedStatement ps = conn.prepareStatement(sql)) {
                        ps.setString(1, playerUUID.toString());
                        ps.setString(2, skillName);
                        ps.setString(3, effectName);
                        ps.setDouble(4, value);
                        ps.executeUpdate();
                    } catch (SQLException ex) {
                        ex.printStackTrace();
                    }
                }
        );
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
        asyncSaver.scheduleDbSave(
                "magicFind:" + playerUUID,
                () -> {
                    try (Connection conn = getConnection();
                         PreparedStatement ps = conn.prepareStatement(sql)) {
                        ps.setString(1, playerUUID.toString());
                        ps.setDouble(2, magicFind);
                        ps.executeUpdate();
                    } catch (SQLException ex) {
                        ex.printStackTrace();
                    }
                }
        );
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
