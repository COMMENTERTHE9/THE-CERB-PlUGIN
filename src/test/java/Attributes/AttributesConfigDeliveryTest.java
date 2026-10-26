package Attributes;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** A skill an update gives attribute weights reaches servers whose attributes.yml is older (Mining 2026-10-25, Woodcutting 2026-10-26). */
class AttributesConfigDeliveryTest {

    private static YamlConfiguration shipped() throws Exception {
        YamlConfiguration y = new YamlConfiguration();
        y.loadFromString(Files.readString(Path.of("src/main/resources/attributes.yml")).replace("\r\n", "\n"));
        return y;
    }

    @Test
    void theShippedFileHasMiningAndMatchesTheBuiltInDefaults() throws Exception {
        List<String> problems = new ArrayList<>();
        AttributeRules r = AttributesConfig.parseRules(AttributesConfig.section(shipped(), "attributes"), AttributeRules.DEFAULT_CURVES, problems::add);
        assertEquals(List.of(), problems);
        assertEquals(AttributeRules.DEFAULT_SKILL_WEIGHTS, r.skillWeights(), "file and built-in defaults agree");
        assertEquals(AttributeRules.DEFAULT_SECONDARY.skillWeights(), r.secondary().skillWeights());
        assertEquals(AttributeRules.DEFAULT_SECONDARY.get(Stat.GATHER_SPEED), r.secondary().get(Stat.GATHER_SPEED));
        assertEquals(List.of(new AttributeRules.SkillWeight(Attribute.FORTUNE, 0.5, null, Yield.ORE)), r.skillWeights().get("Mining"));
        assertEquals(List.of(new AttributeRules.StatWeight(Stat.GATHER_SPEED, 0.002, null)), r.secondary().skillWeights().get("Mining"));
        assertEquals(List.of(new AttributeRules.SkillWeight(Attribute.VITALITY, 0.25, null),
                new AttributeRules.SkillWeight(Attribute.FORTUNE, 0.5, null, Yield.WOOD)), r.skillWeights().get("Woodcutting"));
        assertEquals(List.of(new AttributeRules.StatWeight(Stat.CHOP_SPEED, 0.002, null)), r.secondary().skillWeights().get("Woodcutting"));
        assertEquals(AttributeRules.DEFAULT_SECONDARY.get(Stat.CHOP_SPEED), r.secondary().get(Stat.CHOP_SPEED));
    }

    @Test
    void anOlderFileGetsTheNewSkillsAndKeepsItsOwnChoices() throws Exception {
        YamlConfiguration server = new YamlConfiguration();
        server.loadFromString("""
                attributes:
                  skills:
                    Blade Mastery: [ { attribute: power, per-level: 2.0, weapon: sword } ]
                    Fishing: []
                  secondary:
                    skills:
                      Blade Mastery: [ { stat: attack-speed, per-level: 0.01, weapon: sword } ]
                """);
        server.setDefaults(shipped()); // as AttributesConfig.reload does with the jar's copy
        List<String> problems = new ArrayList<>();
        AttributeRules r = AttributesConfig.parseRules(AttributesConfig.section(server, "attributes"), AttributeRules.DEFAULT_CURVES, problems::add);
        assertEquals(4, problems.size(), problems.toString());
        assertTrue(problems.stream().allMatch(p -> p.contains("Mining") && p.contains("Mining: []") || p.contains("Woodcutting") && p.contains("Woodcutting: []")),
                "the log says where Mining and Woodcutting came from: " + problems);
        assertEquals(2.0, r.skillWeights().get("Blade Mastery").get(0).perLevel(), "the owner's own number stays");
        assertFalse(r.skillWeights().containsKey("Fishing"), "\"Fishing: []\" turns it off");
        assertEquals(0.5, r.skillWeights().get("Mining").get(0).perLevel(), "Mining arrives from the shipped file");
        assertFalse(r.skillWeights().containsKey("Survival"), "a skill the owner's older file left out stays out (it said: not listed = nothing)");
        assertEquals(0.01, r.secondary().skillWeights().get("Blade Mastery").get(0).perLevel());
        assertEquals(0.002, r.secondary().skillWeights().get("Mining").get(0).perLevel(), "speed weights arrive the same way");
        assertEquals(Yield.WOOD, r.skillWeights().get("Woodcutting").get(1).yield(), "Woodcutting arrives with its Fortune scoped to wood");
        assertEquals(0.002, r.secondary().skillWeights().get("Woodcutting").get(0).perLevel());
        assertTrue(r.secondary().get(Stat.GATHER_SPEED).enabled(), "a stat the file lacks uses the built-in setting");
    }

    @Test
    void woodcuttingGivesVitalityFortuneOnWoodAndChoppingSpeed() {
        AttributeSnapshot s = AttributeCalculator.compute(SkillAttributes.contributions(java.util.Map.of("Woodcutting", 100, "Mining", 40), AttributeRules.DEFAULTS),
                AttributeRules.DEFAULTS);
        assertEquals(25, s.get(Attribute.VITALITY), 1e-9);
        assertEquals(50, s.fortune(Yield.WOOD), 1e-9, "Woodcutting's Fortune counts on wood");
        assertEquals(20, s.fortune(Yield.ORE), 1e-9, "Mining's on ore, and neither on the other");
        assertEquals(0.20, s.secondaryRaw(Stat.CHOP_SPEED), 1e-9);
        assertEquals(0.08, s.secondaryRaw(Stat.GATHER_SPEED), 1e-9);
        assertTrue(s.fortuneScoped());
        AttributeSnapshot lucky = AttributeCalculator.compute(List.of(Contribution.attribute("t", "t", Attribute.FORTUNE, 10, null),
                Contribution.fortune("w", "w", 5, Yield.WOOD)), AttributeRules.DEFAULTS);
        assertEquals(10, lucky.get(Attribute.FORTUNE), 1e-9, "general Fortune");
        assertEquals(15, lucky.fortune(Yield.WOOD), 1e-9, "counts everywhere, plus wood's own");
        assertEquals(10, lucky.fortune(Yield.ORE), 1e-9);
        AttributeSnapshot capped = AttributeCalculator.compute(List.of(Contribution.fortune("w", "w", 1e9, Yield.WOOD)), AttributeRules.DEFAULTS);
        assertEquals(AttributeRules.DEFAULTS.cap(Attribute.FORTUNE), capped.fortune(Yield.WOOD), 1e-9, "the Fortune cap holds per yield");
    }

    @Test
    void aYieldOnlyLimitsFortune() throws Exception {
        YamlConfiguration server = new YamlConfiguration();
        server.loadFromString("""
                attributes:
                  skills:
                    Mining: [ { attribute: fortune, per-level: 0.5, yield: gems } ]
                    Survival: [ { attribute: vitality, per-level: 1.0, yield: wood } ]
                """);
        List<String> problems = new ArrayList<>();
        AttributeRules r = AttributesConfig.parseRules(AttributesConfig.section(server, "attributes"), AttributeRules.DEFAULT_CURVES, problems::add);
        assertFalse(r.skillWeights().containsKey("Mining"), "an unknown yield skips the entry: " + problems);
        assertNull(r.skillWeights().get("Survival").get(0).yield(), "a yield on another attribute is ignored");
        assertEquals(2, problems.size(), problems.toString());
    }

    @Test
    void fortuneWithoutAYieldCountsOnTheSkillsOwnResource() throws Exception {
        // An attributes.yml edited before Fortune was scoped has Mining's line without a yield: it stays Fortune on ore,
        // so Mining never adds logs. "all" counts everywhere, and another skill's Fortune without a yield does too.
        YamlConfiguration server = new YamlConfiguration();
        server.loadFromString("""
                attributes:
                  skills:
                    Mining: [ { attribute: fortune, per-level: 0.5 } ]
                    Woodcutting: [ { attribute: fortune, per-level: 0.5 } ]
                    Fishing: [ { attribute: fortune, per-level: 0.5 } ]
                    Trading: [ { attribute: fortune, per-level: 0.5, yield: all } ]
                """);
        List<String> problems = new ArrayList<>();
        AttributeRules r = AttributesConfig.parseRules(AttributesConfig.section(server, "attributes"), AttributeRules.DEFAULT_CURVES, problems::add);
        assertEquals(Yield.ORE, r.skillWeights().get("Mining").get(0).yield());
        assertEquals(Yield.WOOD, r.skillWeights().get("Woodcutting").get(0).yield());
        assertNull(r.skillWeights().get("Fishing").get(0).yield(), "any other skill's Fortune counts everywhere");
        assertNull(r.skillWeights().get("Trading").get(0).yield(), "yield: all counts everywhere");
        assertEquals(List.of(), problems);
        AttributeSnapshot s = AttributeCalculator.compute(SkillAttributes.contributions(java.util.Map.of("Mining", 100), r), r);
        assertEquals(50, s.fortune(Yield.ORE), 1e-9);
        assertEquals(0, s.fortune(Yield.WOOD), 1e-9, "Mining 100 adds no logs");
    }

    @Test
    void withoutAShippedCopyOnlyTheFileCounts() throws Exception {
        YamlConfiguration server = new YamlConfiguration();
        server.loadFromString("attributes:\n  skills:\n    Blade Mastery: [ { attribute: power, per-level: 2.0, weapon: sword } ]\n");
        AttributeRules r = AttributesConfig.parseRules(AttributesConfig.section(server, "attributes"), AttributeRules.DEFAULT_CURVES, p -> {});
        assertEquals(java.util.Set.of("Blade Mastery"), r.skillWeights().keySet());
    }

    @Test
    void gatheringSpeedFollowsMiningWithDiminishingReturns() {
        AttributeSnapshot s = AttributeCalculator.compute(SkillAttributes.contributions(java.util.Map.of("Mining", 100), AttributeRules.DEFAULTS),
                AttributeRules.DEFAULTS);
        assertEquals(0.20, s.secondaryRaw(Stat.GATHER_SPEED), 1e-9, "+0.2% per level");
        assertEquals(AttributeCalculator.diminish(0.20, 0.25), s.secondary(Stat.GATHER_SPEED), 1e-12);
        assertTrue(s.secondary(Stat.GATHER_SPEED) < 0.25);
        assertEquals(50, s.fortune(Yield.ORE), 1e-9, "Mining 100: Fortune 50 on ore");
        assertEquals(0, s.fortune(Yield.WOOD), 1e-9, "...and none on wood");
        assertEquals(0, s.get(Attribute.FORTUNE), 1e-9, "...nor anywhere else");
        AttributeSnapshot agile = AttributeCalculator.compute(List.of(Contribution.attribute("t", "t", Attribute.AGILITY, 300, null)), AttributeRules.DEFAULTS);
        assertEquals(0.0, agile.secondary(Stat.GATHER_SPEED), 1e-12, "Agility doesn't drive it");
    }
}
