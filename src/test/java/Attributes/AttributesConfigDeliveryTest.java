package Attributes;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** A skill an update gives attribute weights reaches servers whose attributes.yml is older (Mining, 2026-10-25). */
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
        assertEquals(List.of(new AttributeRules.SkillWeight(Attribute.FORTUNE, 0.5, null)), r.skillWeights().get("Mining"));
        assertEquals(List.of(new AttributeRules.StatWeight(Stat.GATHER_SPEED, 0.002, null)), r.secondary().skillWeights().get("Mining"));
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
        assertEquals(2, problems.size(), problems.toString());
        assertTrue(problems.stream().allMatch(p -> p.contains("Mining") && p.contains("Mining: []")), "the log says where Mining came from: " + problems);
        assertEquals(2.0, r.skillWeights().get("Blade Mastery").get(0).perLevel(), "the owner's own number stays");
        assertFalse(r.skillWeights().containsKey("Fishing"), "\"Fishing: []\" turns it off");
        assertEquals(0.5, r.skillWeights().get("Mining").get(0).perLevel(), "Mining arrives from the shipped file");
        assertFalse(r.skillWeights().containsKey("Survival"), "a skill the owner's older file left out stays out (it said: not listed = nothing)");
        assertEquals(0.01, r.secondary().skillWeights().get("Blade Mastery").get(0).perLevel());
        assertEquals(0.002, r.secondary().skillWeights().get("Mining").get(0).perLevel(), "speed weights arrive the same way");
        assertTrue(r.secondary().get(Stat.GATHER_SPEED).enabled(), "a stat the file lacks uses the built-in setting");
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
        assertEquals(50, s.get(Attribute.FORTUNE), 1e-9, "Mining 100: Fortune 50");
        AttributeSnapshot agile = AttributeCalculator.compute(List.of(Contribution.attribute("t", "t", Attribute.AGILITY, 300, null)), AttributeRules.DEFAULTS);
        assertEquals(0.0, agile.secondary(Stat.GATHER_SPEED), 1e-12, "Agility doesn't drive it");
    }
}
