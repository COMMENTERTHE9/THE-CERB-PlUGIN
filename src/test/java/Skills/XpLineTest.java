package Skills;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class XpLineTest {

    private static final long S = 1_000_000_000L; // a second in nanoseconds

    @Test
    void gainsOfASkillMergeIntoOneLine() {
        XpLine l = new XpLine(4_000);
        for (int i = 0; i < 20; i++) l.add("Mining", 4, 12, 1240 + i, 2000, false, i * S / 2); // a block every half second
        assertEquals(1, l.size());
        assertEquals("§a+80 §fMining", l.render(XpLine.Mode.COMPACT), "twenty gains, one line");
        assertEquals("§a+80 §fMining XP §8· §71,259/2,000 §8· §7Lv 12", l.render(XpLine.Mode.DETAILED), "the latest progress");
        assertEquals("", l.render(XpLine.Mode.OFF));
    }

    @Test
    void aPauseStartsANewCountAndOtherSkillsShareTheLine() {
        XpLine l = new XpLine(4_000);
        l.add("Mining", 10, 1, 10, 115, false, 0);
        l.add("Mining", 5, 1, 15, 115, false, 5 * S); // more than 4 s later: a new count
        assertEquals("§a+5 §fMining", l.render(XpLine.Mode.COMPACT));
        l.add("Smithing", 10, 1, 10, 115, false, 6 * S);
        assertEquals("§a+10 §fSmithing§8 · §a+5 §fMining", l.render(XpLine.Mode.COMPACT), "most recent first");
        l.add("Critical Strike", 1, 1, 1, 115, false, 6 * S);
        l.add("Survival", 2, 1, 2, 115, false, 6 * S);
        assertEquals("§a+2 §fSurvival§8 · §a+1 §fCritical Strike §8+2 more", l.render(XpLine.Mode.COMPACT), "two named, the rest counted");
        assertTrue(l.render(XpLine.Mode.DETAILED).endsWith("§8(+3 more)"));
        l.prune(20 * S);
        assertEquals(0, l.size(), "everything ages out");
        assertEquals("", l.render(XpLine.Mode.COMPACT));
    }

    @Test
    void edgeCases() {
        XpLine l = new XpLine(4_000);
        l.add(null, 5, 1, 0, 1, false, 0);
        l.add("Mining", 0, 1, 0, 1, false, 0);
        l.add("Mining", -3, 1, 0, 1, false, 0);
        assertEquals(0, l.size(), "nothing to show");
        l.add("Mining", Long.MAX_VALUE, 100, 0, 0, true, 0);
        l.add("Mining", 5, 100, 0, 0, true, 0);
        assertTrue(l.render(XpLine.Mode.COMPACT).contains(XpLine.grouped(Long.MAX_VALUE)), "never wraps");
        assertTrue(l.render(XpLine.Mode.DETAILED).contains("max level"));
        assertEquals(XpLine.Mode.DETAILED, XpLine.Mode.parse(" Detailed "));
        assertNull(XpLine.Mode.parse("loud"));
    }

    @Test
    void aWithheldNoticeNamesTheSkillAndWhy() {
        assertEquals("§7No §fFishing§7 XP in creative mode", XpDisplay.withheldLine("Fishing", "in creative mode"));
        assertEquals("§7No §fRanged Precision§7 XP from mobs out of spawn eggs", XpDisplay.withheldLine("Ranged Precision", "from mobs out of spawn eggs"));
    }

    @Test
    void aWithheldNoticeShowsOncePerSkillAndReasonEveryFewMinutes() {
        java.util.Map<String, Long> shown = new java.util.HashMap<>();
        long every = XpDisplay.WITHHELD_EVERY_MS;
        assertTrue(XpDisplay.due(shown, "Fishing|creative", 1_000, every), "the first time");
        assertFalse(XpDisplay.due(shown, "Fishing|creative", 1_000 + every - 1, every), "not again inside the window");
        assertTrue(XpDisplay.due(shown, "Ranged Precision|creative", 2_000, every), "another skill has its own");
        assertTrue(XpDisplay.due(shown, "Fishing|creative", 1_000 + every, every), "again once the window has passed");
    }
}
