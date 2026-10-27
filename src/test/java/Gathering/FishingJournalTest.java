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

    // ── docs/design/FISHING_EQUIPMENT.md: tracking, the summary, notable catches, rewards ──

    @Test
    void aJournalFromBeforeTheUpdateReadsWithEverythingNewEmpty() {
        String old = "1|s=silver_dace:0.31|c=river_lurker|l=river_lurker:2|a=|f=stirs";
        FishingJournal j = FishingJournal.decode(old);
        assertNull(j.tracked());
        assertEquals(0, j.bestsSet());
        assertTrue(j.summary().isEmpty());
        assertTrue(j.notable().isEmpty());
        assertTrue(j.grantedRewards().isEmpty());
        assertEquals(2, j.lures("river_lurker"));
        assertEquals(old, j.encode(), "nothing new is written for a journal that has nothing new");
    }

    @Test
    void newPartsSurviveSavingAndAPartALaterVersionAddedIsKept() {
        FishingJournal j = new FishingJournal();
        j.record("goldfin_trout", 1.0);
        assertEquals(FishingJournal.Record.PERSONAL_BEST, j.record("goldfin_trout", 2.0));
        assertTrue(j.track("goldfin_trout"));
        assertTrue(j.grant("wayfarers_bait"));
        assertFalse(j.grant("wayfarers_bait"), "granted once");
        j.countCatch(120);
        j.countDiscovery("goldfin_trout", 1.0, 1000L);
        j.countPersonalBest("goldfin_trout", 2.0, 2000L);
        j.countEncounter();
        String text = j.encode() + "|z=from a later version";
        FishingJournal back = FishingJournal.decode(text);
        assertEquals("goldfin_trout", back.tracked());
        assertEquals(1, back.bestsSet());
        assertTrue(back.granted("wayfarers_bait"));
        assertEquals(new FishingJournal.Summary(1, 120, 1, 1, 1), back.summary());
        assertEquals(2, back.notable().size());
        assertEquals(text, back.encode(), "an unknown part is written back as it was");
    }

    @Test
    void trackingTakesOnlyPlainIdsAndNeverAnythingElse() {
        FishingJournal j = new FishingJournal();
        assertFalse(j.track("Not|An Id"));
        assertNull(j.tracked());
        assertTrue(j.track("silver_dace"));
        assertTrue(j.track(null));
        assertNull(j.tracked());
    }

    @Test
    void acknowledgingTakesOffExactlyWhatWasShown() {
        FishingJournal j = new FishingJournal();
        j.countCatch(100);
        j.countCatch(50);
        j.countDiscovery("silver_dace", 0.2, 10L);
        FishingJournal.Summary shown = j.summary();
        java.util.Set<Long> shownAt = java.util.Set.of(10L);
        // a catch while the screen is open
        j.countCatch(30);
        j.countPersonalBest("silver_dace", 0.3, 20L);
        j.acknowledge(shown, shownAt);
        assertEquals(new FishingJournal.Summary(1, 30, 0, 1, 0), j.summary(), "what came in after the screen was drawn stays");
        assertEquals(1, j.unreadNotable());
        assertFalse(j.notable().get(0).unread(), "the one shown is read");
        assertTrue(j.notable().get(1).unread(), "the one after is not");
        // acknowledging the same thing twice never goes below zero
        j.acknowledge(shown, shownAt);
        assertEquals(0, j.summary().catches());
        assertEquals(0, j.summary().xp());
    }

    @Test
    void readingTheSummaryNeverChangesIt() {
        FishingJournal j = new FishingJournal();
        j.countCatch(10);
        String before = j.encode();
        j.summary();
        j.notable();
        j.unreadNotable();
        assertEquals(before, j.encode());
    }

    @Test
    void notableCatchesAreBoundedAndTheOldestReadGoFirst() {
        FishingJournal j = new FishingJournal();
        for (int i = 0; i < FishingJournal.MAX_NOTABLE; i++) j.countPersonalBest("silver_dace", i, i);
        j.acknowledge(null, java.util.Set.of(5L)); // one read in the middle
        j.countDiscovery("goldfin_trout", 3.0, 100L);
        assertEquals(FishingJournal.MAX_NOTABLE, j.notable().size());
        assertTrue(j.notable().stream().noneMatch(n -> n.at() == 5L), "the read one went first");
        assertEquals(0L, j.notable().get(0).at(), "unread ones are kept");
        j.countDiscovery("pond_bream", 0.5, 101L);
        assertEquals(FishingJournal.MAX_NOTABLE, j.notable().size());
        assertTrue(j.notable().stream().noneMatch(n -> n.at() == 0L), "with none read, the oldest goes");
        assertEquals(101L, j.notable().get(FishingJournal.MAX_NOTABLE - 1).at());
    }

    @Test
    void aNotableCatchBecomesATrophyOnce() {
        FishingJournal j = new FishingJournal();
        j.countPersonalBest("goldfin_trout", 3.21, 77L);
        assertFalse(j.markTrophy("goldfin_trout", 78L), "no such catch");
        assertTrue(j.markTrophy("goldfin_trout", 77L));
        assertFalse(j.markTrophy("goldfin_trout", 77L), "one trophy per catch");
        assertTrue(FishingJournal.decode(j.encode()).notable().get(0).trophy());
    }

    @Test
    void countersSaturateInsteadOfWrapping() {
        FishingJournal j = FishingJournal.decode("1|s=|c=|l=|a=|f=|v=" + Long.MAX_VALUE + "," + Long.MAX_VALUE + ",2147483647,2147483647,2147483647");
        j.countCatch(Long.MAX_VALUE);
        j.countDiscovery("silver_dace", 0.1, 1L);
        j.countEncounter();
        FishingJournal.Summary s = j.summary();
        assertEquals(Long.MAX_VALUE, s.catches());
        assertEquals(Long.MAX_VALUE, s.xp());
        assertEquals(Integer.MAX_VALUE, s.species());
        assertEquals(Integer.MAX_VALUE, s.encounters());
    }
}
