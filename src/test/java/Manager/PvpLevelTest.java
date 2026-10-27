package Manager;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The pre-Attributes-2.0 PvP formulas count a skill from its first level gained, as they did when an untrained skill was
 * stored at 0 (the independent review of the level-1 start, 2026-10-27: without this, every player's PvP hits grew by
 * about 30%: Weapon Mastery x1.18, a 12.5% crit chance, Blade Mastery +3.5%).
 */
class PvpLevelTest {

    @Test
    void levelOneIsUntrainedInThePvpFormulas() {
        assertEquals(0, CombatSkillProcessor.pvpLevel(1));
        assertEquals(0, CombatSkillProcessor.pvpLevel(0), "an old level 0, before it is read as 1");
    }

    @Test
    void everyLevelGainedCountsAsItDid() {
        assertEquals(2, CombatSkillProcessor.pvpLevel(2));
        assertEquals(50, CombatSkillProcessor.pvpLevel(50));
        assertEquals(100, CombatSkillProcessor.pvpLevel(100));
    }
}
