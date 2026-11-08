package Classes;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * A kill's class share goes to the classes used in the fight it ended (ClassService.usedInFight): the
 * {@link ClassService#KILL_WINDOW_MS} before the kill, measured from the kill. A kill made while the killer's skills loaded
 * is paid once they have (exact XP while loading, item 10), up to seconds later: measured from that moment instead, the
 * fight could be over and the share lost (the independent review's finding 1).
 */
class ClassKillShareTest {

    private static Map<String, Long> uses(Object... classAndTime) {
        Map<String, Long> m = new LinkedHashMap<>();
        for (int i = 0; i < classAndTime.length; i += 2) m.put((String) classAndTime[i], ((Number) classAndTime[i + 1]).longValue());
        return m;
    }

    @Test
    void aKillPaidLaterStillFindsTheFightItEnded() {
        Map<String, Long> lastUse = uses("warrior", 500);
        // a cast at 0.5 s, the kill at 1 s, the skills load at 12 s
        assertEquals(List.of("warrior"), ClassService.usedInFight(lastUse, 1_000, 12_000), "measured from the kill");
        assertEquals(List.of(), ClassService.usedInFight(lastUse, -1, 12_000),
                "measured from the moment it is paid, the 10 s window has passed: the share would be lost");
    }

    @Test
    void aKillPaidAtOnceIsMeasuredFromNowAsBefore() {
        Map<String, Long> lastUse = uses("warrior", 1_000, "mage", 15_000, "duelist", 20_000);
        assertEquals(List.of("mage", "duelist"), ClassService.usedInFight(lastUse, -1, 25_000), "the 10 s before now, edge included");
        assertEquals(ClassService.usedInFight(lastUse, 25_000, 25_000), ClassService.usedInFight(lastUse, -1, 25_000),
                "a kill paid in the moment it happened: the same classes either way");
    }

    @Test
    void aClassUsedAgainSinceTheKillStillCounts() {
        // the fight went on: the Warrior cast before the kill (not recorded any more) and again after it
        Map<String, Long> lastUse = uses("warrior", 3_000);
        assertEquals(List.of("warrior"), ClassService.usedInFight(lastUse, 1_000, 4_000));
        assertEquals(List.of(), ClassService.usedInFight(uses("warrior", 0), 10_001, 10_001), "1 ms before the window: not counted");
        assertEquals(List.of("warrior"), ClassService.usedInFight(uses("warrior", 1), 10_001, 10_001), "exactly 10 s before the kill: counted");
    }
}
