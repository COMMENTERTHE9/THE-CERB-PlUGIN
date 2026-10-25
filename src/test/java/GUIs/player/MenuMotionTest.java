package GUIs.player;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/** Player Menu 2.1: the strip's starting fill and the session memory's once-only rules. */
class MenuMotionTest {

    private static PlayerMenuMemory.Seen seen(int level, int xp) { return new PlayerMenuMemory.Seen(level, xp); }

    @Test
    void stripStartsWhereThePlayerLastSawIt() {
        // Nothing seen yet this session, or a new level: fill from empty.
        assertEquals(0, SkillDetailMenu.stripFrom(null, 5, 1000, 6, 7));
        assertEquals(0, SkillDetailMenu.stripFrom(seen(4, 900), 5, 1000, 6, 7));
        // Same level: from the share seen (500/1000 of 7 panes rounds to 4), never past today's fill.
        assertEquals(4, SkillDetailMenu.stripFrom(seen(5, 500), 5, 1000, 6, 7));
        assertEquals(2, SkillDetailMenu.stripFrom(seen(5, 500), 5, 1000, 2, 7));
        assertEquals(0, SkillDetailMenu.stripFrom(seen(5, 0), 5, 1000, 6, 7));
        // Broken input never lights panes that aren't earned.
        assertEquals(0, SkillDetailMenu.stripFrom(seen(5, 500), 5, 0, 6, 7));
        assertEquals(0, SkillDetailMenu.stripFrom(seen(5, -40), 5, 1000, 6, 7));
    }

    @Test
    void levelUpsAndChimesAreShownOnceUntilTheNextClose() {
        PlayerMenuMemory m = new PlayerMenuMemory();
        UUID p = UUID.randomUUID();
        assertNull(m.seen(p, "Blade Mastery"), "nothing counts as new before the first close");
        assertEquals(0, m.gained(p, "Blade Mastery", 12));
        assertTrue(m.isFirstLook(p, "Blade Mastery"));
        assertTrue(m.isFirstLook(p, "Blade Mastery"), "asking doesn't use it up: only a flash that really played does");
        m.markLooked(p, "Blade Mastery");
        assertFalse(m.isFirstLook(p, "Blade Mastery"), "the level-up flash plays once per skill");
        assertTrue(m.isFirstLook(p, "Survival"));
        assertTrue(m.wouldAnnounce(p));
        m.markAnnounced(p);
        assertFalse(m.wouldAnnounce(p), "the hub chime plays once");
        m.forget(p);
        assertTrue(m.isFirstLook(p, "Blade Mastery"), "a new session starts fresh");
        assertTrue(m.wouldAnnounce(p));
    }
}
