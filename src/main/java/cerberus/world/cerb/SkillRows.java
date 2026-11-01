package cerberus.world.cerb;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Map;

/**
 * The {@code player_skills} rows (pure JDBC, unit-tested on a real SQLite file). Every row carries {@code saved_seq}, the
 * sequence number of the snapshot that wrote it (Skills/SkillStore): a write keeps a row only when its snapshot is at
 * least as new, so an older snapshot that commits after a newer one changes nothing, whichever thread or path it came
 * from. A snapshot is written in one transaction: all of its rows or none.
 */
public final class SkillRows {

    /** The table as created on a new database (saved_seq included). */
    public static final String CREATE_TABLE = """
            CREATE TABLE IF NOT EXISTS player_skills (
                custom_id   TEXT,
                skill_name  TEXT,
                skill_level INTEGER DEFAULT 1,
                skill_xp    INTEGER DEFAULT 0,
                saved_seq   INTEGER NOT NULL DEFAULT 0,
                PRIMARY KEY (custom_id, skill_name)
            )""";

    private static final String UPSERT = "INSERT INTO player_skills(custom_id,skill_name,skill_level,skill_xp,saved_seq) "
            + "VALUES(?,?,?,?,?) ON CONFLICT(custom_id,skill_name) DO UPDATE SET skill_level=excluded.skill_level, "
            + "skill_xp=excluded.skill_xp, saved_seq=excluded.saved_seq WHERE excluded.saved_seq >= player_skills.saved_seq";

    private SkillRows() {}

    /**
     * Adds {@code saved_seq} to a database made before it (2026-11): existing rows get 0, so the first snapshot after the
     * update replaces them. Older plugin versions name their columns and never read this one, so the database still works
     * with them. True when the column was added now.
     */
    public static boolean ensureSeqColumn(Connection conn) throws SQLException {
        try (Statement st = conn.createStatement(); ResultSet rs = st.executeQuery("PRAGMA table_info(player_skills)")) {
            while (rs.next()) if ("saved_seq".equalsIgnoreCase(rs.getString("name"))) return false;
        }
        try (Statement st = conn.createStatement()) {
            st.executeUpdate("ALTER TABLE player_skills ADD COLUMN saved_seq INTEGER NOT NULL DEFAULT 0");
        }
        return true;
    }

    /**
     * Writes one snapshot of a player's skills in one transaction. A row whose stored {@code saved_seq} is newer than
     * {@code seq} is kept as it is. Returns how many rows the snapshot wrote (0: the database already held a newer save
     * of every skill). Throws on any database error (nothing of the snapshot is then written).
     */
    public static int write(Connection conn, String customId, Map<String, Integer> levels, Map<String, Integer> xp, long seq)
            throws SQLException {
        boolean auto = conn.getAutoCommit();
        conn.setAutoCommit(false);
        try (PreparedStatement ps = conn.prepareStatement(UPSERT)) {
            for (Map.Entry<String, Integer> e : levels.entrySet()) {
                ps.setString(1, customId);
                ps.setString(2, e.getKey());
                ps.setInt(3, e.getValue());
                ps.setInt(4, xp.getOrDefault(e.getKey(), 0));
                ps.setLong(5, seq);
                ps.addBatch();
            }
            int[] counts = ps.executeBatch();
            conn.commit();
            int changed = 0;
            for (int c : counts) changed += c > 0 ? c : c == Statement.SUCCESS_NO_INFO ? 1 : 0; // unknown: count it as written
            return changed;
        } catch (SQLException ex) {
            try { conn.rollback(); } catch (SQLException ignored) { /* the original error says more */ }
            throw ex;
        } finally {
            try { conn.setAutoCommit(auto); } catch (SQLException ignored) { /* the connection is closed next */ }
        }
    }

    /** The newest snapshot any row was written from (0 for an empty table or rows from before saved_seq). */
    public static long maxSeq(Connection conn) throws SQLException {
        try (Statement st = conn.createStatement(); ResultSet rs = st.executeQuery("SELECT COALESCE(MAX(saved_seq), 0) FROM player_skills")) {
            return rs.next() ? rs.getLong(1) : 0;
        }
    }

    /** The newest snapshot this player's rows were written from (0 when none). */
    public static long maxSeq(Connection conn, String customId) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement("SELECT COALESCE(MAX(saved_seq), 0) FROM player_skills WHERE custom_id = ?")) {
            ps.setString(1, customId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getLong(1) : 0;
            }
        }
    }
}
