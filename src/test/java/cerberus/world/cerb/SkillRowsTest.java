package cerberus.world.cerb;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** The skill rows on a real SQLite file: a write keeps a row only from a snapshot at least as new; all or nothing. */
class SkillRowsTest {

    @TempDir Path dir;

    private Connection open() throws SQLException {
        return DriverManager.getConnection("jdbc:sqlite:" + dir.resolve("t.db"));
    }

    private static int[] row(Connection c, String id, String skill) throws SQLException {
        try (Statement st = c.createStatement();
             ResultSet rs = st.executeQuery("SELECT skill_level, skill_xp, saved_seq FROM player_skills WHERE custom_id = '"
                     + id + "' AND skill_name = '" + skill + "'")) {
            return rs.next() ? new int[]{rs.getInt(1), rs.getInt(2), rs.getInt(3)} : null;
        }
    }

    @Test
    void anOlderSnapshotThatCommitsLaterLeavesTheNewerRowsAsTheyAre() throws Exception {
        try (Connection c = open()) {
            try (Statement st = c.createStatement()) { st.execute(SkillRows.CREATE_TABLE); }
            SkillRows.write(c, "CUST-A", Map.of("Mining", 10, "Farming", 3), Map.of("Mining", 80, "Farming", 5), 5);
            SkillRows.write(c, "CUST-A", Map.of("Mining", 10, "Farming", 2), Map.of("Mining", 40, "Farming", 9), 3); // late, older
            assertArrayEquals(new int[]{10, 80, 5}, row(c, "CUST-A", "Mining"));
            assertArrayEquals(new int[]{3, 5, 5}, row(c, "CUST-A", "Farming"));
            SkillRows.write(c, "CUST-A", Map.of("Mining", 10), Map.of("Mining", 80), 5); // the same snapshot again (a retry)
            assertArrayEquals(new int[]{10, 80, 5}, row(c, "CUST-A", "Mining"));
            SkillRows.write(c, "CUST-A", Map.of("Mining", 11), Map.of("Mining", 2), 6);  // newer
            assertArrayEquals(new int[]{11, 2, 6}, row(c, "CUST-A", "Mining"));
            SkillRows.write(c, "CUST-B", Map.of("Mining", 4), Map.of("Mining", 1), 2); // another player keeps their own order
            assertArrayEquals(new int[]{4, 1, 2}, row(c, "CUST-B", "Mining"));
            assertEquals(6, SkillRows.maxSeq(c));
            assertEquals(6, SkillRows.maxSeq(c, "CUST-A"));
            assertEquals(2, SkillRows.maxSeq(c, "CUST-B"));
            assertEquals(0, SkillRows.maxSeq(c, "CUST-NONE"));
        }
    }

    @Test
    void aSnapshotIsWrittenWhollyOrNotAtAll() throws Exception {
        try (Connection c = open()) {
            try (Statement st = c.createStatement()) {
                st.execute(SkillRows.CREATE_TABLE);
                st.execute("CREATE TRIGGER boom BEFORE INSERT ON player_skills WHEN NEW.skill_name = 'Boom' "
                        + "BEGIN SELECT RAISE(ABORT, 'boom'); END");
            }
            SkillRows.write(c, "CUST-A", Map.of("Mining", 10), Map.of("Mining", 80), 5);
            Map<String, Integer> levels = new LinkedHashMap<>();
            levels.put("Mining", 12);
            levels.put("Boom", 1);
            assertThrows(SQLException.class, () -> SkillRows.write(c, "CUST-A", levels, Map.of("Mining", 3), 6));
            assertArrayEquals(new int[]{10, 80, 5}, row(c, "CUST-A", "Mining"), "the failed snapshot wrote nothing");
            assertTrue(c.getAutoCommit(), "the connection is left as it was found");
        }
    }

    @Test
    void aDatabaseFromBeforeGetsTheColumnWithEveryRowKeptAndOlderVersionsStillWriteIt() throws Exception {
        try (Connection c = open()) {
            try (Statement st = c.createStatement()) { // the table as versions before 2026-11 made it
                st.execute("CREATE TABLE player_skills (custom_id TEXT, skill_name TEXT, skill_level INTEGER DEFAULT 1, "
                        + "skill_xp INTEGER DEFAULT 0, PRIMARY KEY (custom_id, skill_name))");
                st.execute("INSERT INTO player_skills VALUES ('CUST-A','Mining',37,1234), ('CUST-A','Farming',5,6)");
            }
            assertTrue(SkillRows.ensureSeqColumn(c));
            assertFalse(SkillRows.ensureSeqColumn(c), "added once");
            assertArrayEquals(new int[]{37, 1234, 0}, row(c, "CUST-A", "Mining"));
            assertArrayEquals(new int[]{5, 6, 0}, row(c, "CUST-A", "Farming"));
            assertEquals(0, SkillRows.maxSeq(c));
            SkillRows.write(c, "CUST-A", Map.of("Mining", 37), Map.of("Mining", 1300), 1); // the first snapshot after the update
            assertArrayEquals(new int[]{37, 1300, 1}, row(c, "CUST-A", "Mining"));
            try (Statement st = c.createStatement()) { // a rollback to an older plugin version: its own upsert still works
                st.execute("INSERT INTO player_skills(custom_id,skill_name,skill_level,skill_xp) VALUES('CUST-A','Mining',38,5) "
                        + "ON CONFLICT(custom_id,skill_name) DO UPDATE SET skill_level=excluded.skill_level,skill_xp=excluded.skill_xp");
                st.execute("INSERT INTO player_skills(custom_id,skill_name,skill_level,skill_xp) VALUES('CUST-A','Woodcutting',2,0) "
                        + "ON CONFLICT(custom_id,skill_name) DO NOTHING");
            }
            assertArrayEquals(new int[]{38, 5, 1}, row(c, "CUST-A", "Mining"));
            assertArrayEquals(new int[]{2, 0, 0}, row(c, "CUST-A", "Woodcutting"));
        }
    }
}
