package GUIs.player;

import Skills.SkillManager;
import org.bukkit.ChatColor;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class PlayerMenuPureTest {

    @Test
    void wrapKeepsWordsWhole() {
        assertEquals(List.of("Take damage while", "wearing heavy", "armor."), PlayerTheme.wrap("Take damage while wearing heavy armor.", 17));
        assertEquals(List.of("a", "bbbbbbbbbbbbbbbbbbbb", "c"), PlayerTheme.wrap("a bbbbbbbbbbbbbbbbbbbb c", 5));
        assertTrue(PlayerTheme.wrap("  ", 10).isEmpty());
        assertTrue(PlayerTheme.wrap(null, 10).isEmpty());
    }

    @Test
    void barShowsThePercentageAsText() {
        String bar = ChatColor.stripColor(PlayerTheme.bar(1, 4, 8, ChatColor.GREEN));
        assertEquals("■■■■■■■■ 25%", bar);
        assertTrue(ChatColor.stripColor(PlayerTheme.bar(5, 0, 4, ChatColor.GREEN)).endsWith(" 0%"));
        assertTrue(ChatColor.stripColor(PlayerTheme.bar(Double.NaN, 10, 4, ChatColor.GREEN)).endsWith(" 0%"));
        assertTrue(ChatColor.stripColor(PlayerTheme.bar(50, 10, 4, ChatColor.GREEN)).endsWith(" 100%"));
    }

    @Test
    void milestonesAreNextLevelNextTenAndMax() {
        assertArrayEquals(new int[]{1, 10, 100}, SkillDetailMenu.milestones(0));
        assertArrayEquals(new int[]{10, 20, 100}, SkillDetailMenu.milestones(9));
        assertArrayEquals(new int[]{24, 30, 100}, SkillDetailMenu.milestones(23));
        assertArrayEquals(new int[]{96, 100}, SkillDetailMenu.milestones(95));
        assertArrayEquals(new int[]{100}, SkillDetailMenu.milestones(99));
    }

    @Test
    void xpBetweenSumsTheCurve() {
        assertEquals(0, SkillDetailMenu.xpBetween(5, 5));
        assertEquals(SkillManager.xpForLevel(5), SkillDetailMenu.xpBetween(5, 6));
        assertEquals((long) SkillManager.xpForLevel(1) + SkillManager.xpForLevel(2), SkillDetailMenu.xpBetween(1, 3));
    }

    @Test
    void realmMagnitudesReadCorrectlyBelowOne() {
        assertEquals("0.125", AscensionMenu.magnitude(0.125));
        assertEquals("0.5", AscensionMenu.magnitude(0.5));
        assertEquals("8", AscensionMenu.magnitude(8));
        assertEquals("2.1M", AscensionMenu.magnitude(2_097_152));
    }

    @Test
    void availabilityStatesAreWordsNotJustColours() {
        for (Availability.State s : Availability.State.values()) {
            assertFalse(ChatColor.stripColor(s.label).isBlank());
        }
        assertTrue(Availability.available().isAvailable());
        assertFalse(Availability.locked("Reach Adept").isAvailable());
        assertEquals("Reach Adept", Availability.locked("Reach Adept").note());
    }
}
