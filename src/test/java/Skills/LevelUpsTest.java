package Skills;

import Attributes.AttributeRules;
import Attributes.AttributeSnapshot;
import Attributes.SkillAttributes;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** Level-up presentation rules (docs/design/LEVEL_UP.md): milestones, text, real gains, combining. */
class LevelUpsTest {

    @Test
    void milestonesAreTheDesignsFive() {
        assertEquals("Apprentice", LevelUps.title(10));
        assertEquals("Journeyman", LevelUps.title(25));
        assertEquals("Expert", LevelUps.title(50));
        assertEquals("Master", LevelUps.title(75));
        assertEquals("Grandmaster", LevelUps.title(100));
        assertNull(LevelUps.title(11));
        assertEquals(10, LevelUps.milestoneCrossed(9, 10));
        assertEquals(0, LevelUps.milestoneCrossed(10, 11), "already past it");
        assertEquals(25, LevelUps.milestoneCrossed(8, 30), "a big reward crossing two shows the higher");
        assertEquals(0, LevelUps.milestoneCrossed(1, 9));
    }

    @Test
    void levelText() {
        assertEquals("12", LevelUps.levels(11, 12));
        assertEquals("10 → 13", LevelUps.levels(10, 13));
    }

    @Test
    void theGainIsWhatTheEngineComputesWithDiminishingReturns() {
        AttributeRules r = AttributeRules.DEFAULTS;
        AttributeSnapshot at11 = snapshot(r, Map.of("Blade Mastery", 11));
        AttributeSnapshot at12 = snapshot(r, Map.of("Blade Mastery", 12));
        List<LevelUps.Gain> g = LevelUps.gains("Blade Mastery", r, at11, at12);
        assertFalse(g.isEmpty(), "Blade Mastery feeds Power (swords)");
        LevelUps.Gain power = g.stream().filter(x -> x.name().startsWith("Power")).findFirst().orElseThrow();
        assertEquals("Power (swords)", power.name());
        assertTrue(power.delta() > 0 && power.delta() <= 1.0 + 1e-9, "one level, after diminishing returns: " + power.delta());
        assertTrue(power.text().startsWith("+") && power.text().endsWith("Power (swords)"), power.text());
        assertTrue(LevelUps.gains("Fishing", r, at11, at12).isEmpty(), "a skill that feeds nothing has no gain line");
    }

    @Test
    void woodcuttingsGainNamesWhereItsFortuneCounts() {
        AttributeRules r = AttributeRules.DEFAULTS;
        List<LevelUps.Gain> g = LevelUps.gains("Woodcutting", r, snapshot(r, Map.of("Woodcutting", 49, "Mining", 30)),
                snapshot(r, Map.of("Woodcutting", 50, "Mining", 30)));
        List<String> names = g.stream().map(LevelUps.Gain::name).toList();
        assertTrue(names.containsAll(List.of("Vitality", "Fortune (wood)", "Chopping speed")), names.toString());
        LevelUps.Gain f = g.stream().filter(x -> x.name().equals("Fortune (wood)")).findFirst().orElseThrow();
        assertEquals(24.5, f.before(), 1e-9, "Woodcutting's own Fortune, not Mining's ore Fortune");
        assertEquals(25, f.after(), 1e-9);
        List<LevelUps.Gain> m = LevelUps.gains("Mining", r, snapshot(r, Map.of("Mining", 9)), snapshot(r, Map.of("Mining", 10)));
        assertTrue(m.stream().anyMatch(x -> x.name().equals("Fortune (ore)")), m.toString());
    }

    @Test
    void aTechniqueLineNamesItAndWhatItDoes() {
        Celebration t = Celebrations.technique("Felling I: Sneak while cutting a natural tree to bring down up to 6 logs");
        assertEquals(Celebration.Kind.TECHNIQUE, t.kind());
        assertEquals(Celebration.Tier.MAJOR, t.tier());
        assertEquals(List.of("§a✦ New technique: §fFelling I§7: Sneak while cutting a natural tree to bring down up to 6 logs"), t.chat());
        assertNull(t.title(), "the milestone it comes with carries the title");
    }

    @Test
    void numbersReadCleanly() {
        assertEquals("+0.9", LevelUps.signed(0.9));
        assertEquals("+0.04", LevelUps.signed(0.04));
        assertEquals("+12", LevelUps.signed(12.0));
        assertEquals("+1.5", LevelUps.signed(1.5));
        assertEquals("1,234", LevelUps.num(1234.4));
    }

    @Test
    void simultaneousGainsAreShownOnce() {
        Celebration lvl = new Celebration(Celebration.Kind.SKILL_LEVEL, Celebration.Tier.ORDINARY, "▲ Blade Mastery 12 · +1 Power", "▲ Blade Mastery 12", null, null, List.of("a"), -1);
        Celebration rank = new Celebration(Celebration.Kind.CLASS_RANK, Celebration.Tier.MAJOR, "✦ Warrior rank 5 · New: Charge", "✦ Warrior rank 5", "Warrior · Rank 5", "New: Charge", List.of("b", "c"), 0xC0392B);
        Celebration lvl2 = new Celebration(Celebration.Kind.SKILL_LEVEL, Celebration.Tier.ORDINARY, "▲ Critical Strike 6 · +1 Agility", "▲ Critical Strike 6", null, null, List.of("d"), -1);
        Celebration lvl3 = new Celebration(Celebration.Kind.SKILL_LEVEL, Celebration.Tier.ORDINARY, "▲ Survival 3", null, null, null, List.of("e"), -1);
        Celebration.Batch b = Celebration.combine(List.of(lvl, rank, lvl2, lvl3));
        assertEquals(Celebration.Tier.MAJOR, b.tier(), "the strongest decides the effect");
        assertEquals(Celebration.Kind.CLASS_RANK, b.kind());
        assertEquals("Warrior · Rank 5", b.title());
        assertEquals(0xC0392B, b.rgb());
        assertEquals(List.of("a", "b", "c", "d", "e"), b.chat(), "every chat line, in order");
        assertEquals("▲ Blade Mastery 12§7 · ✦ Warrior rank 5§7 · ▲ Critical Strike 6§7 · +1 more", b.hud(), "brief parts, three at most");
        assertNull(Celebration.combine(List.of()));
        assertEquals("▲ Blade Mastery 12 · +1 Power", Celebration.combine(List.of(lvl)).hud(), "alone: the full line with its gain");
    }

    private static AttributeSnapshot snapshot(AttributeRules r, Map<String, Integer> levels) {
        return Attributes.AttributeCalculator.compute(SkillAttributes.contributions(levels, r), r);
    }
}
