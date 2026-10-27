package Gathering;

import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/** A player's fishing record (docs/design/FISHING.md): saved and read back, and lures that never duplicate or vanish. */
class FishingJournalTest {

    @Test
    void recordsAreDiscoveriesThenPersonalBests() {
        FishingJournal j = new FishingJournal();
        assertEquals(FishingJournal.Record.DISCOVERY, j.record("goldfin_trout", 1.2));
        assertEquals(FishingJournal.Record.NONE, j.record("goldfin_trout", 0.8));
        assertEquals(FishingJournal.Record.PERSONAL_BEST, j.record("goldfin_trout", 3.4));
        assertEquals(3.4, j.best("goldfin_trout"), 1e-9);
        assertEquals(1, j.speciesCount());
        assertTrue(j.caught("goldfin_trout") && !j.caught("silver_dace"));
    }

    @Test
    void aLureIsSpentOnceAndEndsOnce() {
        FishingJournal j = new FishingJournal();
        UUID a = UUID.randomUUID(), b = UUID.randomUUID();
        assertFalse(j.spend("river_lurker", a, 1), "no lure, nothing to spend");
        assertTrue(j.addLure("river_lurker", 0));
        assertTrue(j.addLure("river_lurker", 0));
        assertTrue(j.spend("river_lurker", a, 10));
        assertEquals(1, j.lures("river_lurker"));
        assertFalse(j.spend("river_lurker", b, 11), "one fight at a time: the second call spends nothing");
        assertEquals(1, j.lures("river_lurker"));
        assertFalse(j.refund(b), "another creature's refund does nothing");
        assertFalse(j.finish(b));
        assertTrue(j.refund(a), "it got away: the lure comes back");
        assertEquals(2, j.lures("river_lurker"));
        assertFalse(j.refund(a), "...once");
        assertFalse(j.finish(a), "and that fight can't be won afterwards");
        assertEquals(2, j.lures("river_lurker"));
        assertTrue(j.spend("river_lurker", b, 20));
        assertTrue(j.finish(b), "won: the lure stays spent");
        assertFalse(j.refund(b), "a won fight gives nothing back");
        assertEquals(1, j.lures("river_lurker"));
        assertNull(j.active());
    }

    @Test
    void theLastLureLeavesNoEmptyEntry() {
        FishingJournal j = new FishingJournal();
        j.addLure("leviathan", 0);
        UUID e = UUID.randomUUID();
        assertTrue(j.spend("leviathan", e, 1));
        assertEquals(Map.of(), j.lures());
        assertEquals(0, j.totalLures());
        assertTrue(j.refund(e));
        assertEquals(Map.of("leviathan", 1), j.lures());
    }

    @Test
    void aLimitStopsAddingAndNothingOverflows() {
        FishingJournal j = new FishingJournal();
        assertTrue(j.addLure("bog_stalker", 2));
        assertTrue(j.addLure("bog_stalker", 2));
        assertFalse(j.addLure("bog_stalker", 2), "full at 2");
        assertEquals(2, j.lures("bog_stalker"));
    }

    @Test
    void everythingSurvivesSavingAndReading() {
        FishingJournal j = new FishingJournal();
        j.record("silver_dace", 0.31);
        j.record("sunken_coins", 0);
        j.defeat("river_lurker");
        j.addLure("river_lurker", 0);
        j.addLure("river_lurker", 0);
        j.addLure("tidecaller", 0);
        UUID e = UUID.randomUUID();
        j.spend("tidecaller", e, 123456789L);
        j.setFlag("stirs");
        FishingJournal back = FishingJournal.decode(j.encode());
        assertEquals(j.encode(), back.encode());
        assertEquals(0.31, back.best("silver_dace"), 1e-9);
        assertTrue(back.caught("sunken_coins"));
        assertTrue(back.defeated("river_lurker"));
        assertEquals(2, back.lures("river_lurker"));
        assertEquals(0, back.lures("tidecaller"));
        assertEquals(new FishingJournal.Active("tidecaller", e, 123456789L), back.active());
        assertTrue(back.flag("stirs"));
        assertTrue(back.refund(e), "a fight still on the journal after a restart gives its lure back");
        assertEquals(1, back.lures("tidecaller"));
    }

    @Test
    void unreadableTextNeverThrows() {
        for (String bad : new String[] {null, "", "  ", "2|s=a:1", "garbage", "1|s=::,a:b,UPPER:1,ok:NaN,fine:1.5|l=x:-3,y:abc,z:2|a=x:notauuid:1|c=Bad Id,good|f=a b,ok|q=later"}) {
            FishingJournal j = FishingJournal.decode(bad);
            assertNotNull(j);
            if (bad != null && bad.startsWith("1|")) {
                assertEquals(1.5, j.best("fine"), 1e-9);
                assertFalse(j.caught("ok"), "NaN is skipped");
                assertEquals(2, j.lures("z"));
                assertEquals(0, j.lures("x"));
                assertNull(j.active());
                assertTrue(j.defeated("good") && !j.defeated("Bad Id"));
                assertTrue(j.flag("ok"));
            }
        }
    }
}
