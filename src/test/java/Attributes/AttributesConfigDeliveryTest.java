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
        assertEquals(List.of(new AttributeRules.SkillWeight(Attribute.FORTITUDE, 0.25, null)), r.skillWeights().get("Smithing"));
        assertEquals(List.of(new AttributeRules.StatWeight(Stat.WEAR_RESISTANCE, 0.003, null)), r.secondary().skillWeights().get("Smithing"));
        assertEquals(AttributeRules.DEFAULT_SECONDARY.get(Stat.WEAR_RESISTANCE), r.secondary().get(Stat.WEAR_RESISTANCE));
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
        assertEquals(7, problems.size(), problems.toString());
        assertTrue(problems.stream().allMatch(p -> p.contains("Mining") && p.contains("Mining: []") || p.contains("Woodcutting") && p.contains("Woodcutting: []")
                        || p.contains("Smithing") && p.contains("Smithing: []") || p.contains("secondary.skills.Fishing") && p.contains("Fishing: []")),
                "the log says where Mining, Woodcutting, Smithing and Fishing's stats came from: " + problems);
        assertEquals(2.0, r.skillWeights().get("Blade Mastery").get(0).perLevel(), "the owner's own number stays");
        assertFalse(r.skillWeights().containsKey("Fishing"), "\"Fishing: []\" turns it off");
        assertEquals(0.5, r.skillWeights().get("Mining").get(0).perLevel(), "Mining arrives from the shipped file");
        assertFalse(r.skillWeights().containsKey("Survival"), "a skill the owner's older file left out stays out (it said: not listed = nothing)");
        assertEquals(0.01, r.secondary().skillWeights().get("Blade Mastery").get(0).perLevel());
        assertEquals(0.002, r.secondary().skillWeights().get("Mining").get(0).perLevel(), "speed weights arrive the same way");
        assertEquals(Yield.WOOD, r.skillWeights().get("Woodcutting").get(1).yield(), "Woodcutting arrives with its Fortune scoped to wood");
        assertEquals(0.002, r.secondary().skillWeights().get("Woodcutting").get(0).perLevel());
        assertTrue(r.secondary().get(Stat.GATHER_SPEED).enabled(), "a stat the file lacks uses the built-in setting");
        assertEquals(0.25, r.skillWeights().get("Smithing").get(0).perLevel(), "Smithing's Fortitude arrives on an older file");
        assertEquals(Stat.WEAR_RESISTANCE, r.secondary().skillWeights().get("Smithing").get(0).stat(), "...and its wear resistance");
        assertEquals(AttributeRules.DEFAULT_SECONDARY.get(Stat.WEAR_RESISTANCE), r.secondary().get(Stat.WEAR_RESISTANCE));
    }

    @Test
    void fishingDeletedFromAnOlderFileStaysDeletedButGetsItsNewStats() throws Exception {
        // Fishing's Fortune was in the first attributes.yml, which said "skills not listed here add nothing": an owner who
        // deleted it gave Fishing nothing, and an update must not bring it back. Its secondary stats are new (2026-10-27).
        YamlConfiguration server = new YamlConfiguration();
        server.loadFromString("""
                attributes:
                  skills:
                    Blade Mastery: [ { attribute: power, per-level: 2.0, weapon: sword } ]
                  secondary:
                    skills:
                      Blade Mastery: [ { stat: attack-speed, per-level: 0.01, weapon: sword } ]
                """);
        server.setDefaults(shipped());
        List<String> problems = new ArrayList<>();
        AttributeRules r = AttributesConfig.parseRules(AttributesConfig.section(server, "attributes"), AttributeRules.DEFAULT_CURVES, problems::add);
        assertFalse(r.skillWeights().containsKey("Fishing"), "the owner's deletion stands: " + problems);
        assertEquals(List.of(Stat.LURE_SPEED, Stat.TREASURE_CHANCE, Stat.SEA_CREATURE_CHANCE),
                r.secondary().skillWeights().get("Fishing").stream().map(AttributeRules.StatWeight::stat).toList(), "its new stats arrive");
        assertTrue(problems.stream().noneMatch(p -> p.startsWith("attributes.skills.Fishing") || p.startsWith("skills.Fishing")), problems.toString());
    }

    @Test
    void smithingGivesFortitudeAndWearResistanceWithDiminishingReturns() {
        AttributeSnapshot s = AttributeCalculator.compute(SkillAttributes.contributions(java.util.Map.of("Smithing", 100), AttributeRules.DEFAULTS),
                AttributeRules.DEFAULTS);
        assertEquals(25, s.get(Attribute.FORTITUDE), 1e-9, "+0.25 Fortitude per level");
        assertEquals(0.30, s.secondaryRaw(Stat.WEAR_RESISTANCE), 1e-9, "+0.3% wear resistance per level");
        assertEquals(0.2015, s.secondary(Stat.WEAR_RESISTANCE), 5e-4, "20% at 100, below the 35% cap");
        assertEquals(0.122, at("Smithing", 50).secondary(Stat.WEAR_RESISTANCE), 5e-4);
        assertEquals(0.068, at("Smithing", 25).secondary(Stat.WEAR_RESISTANCE), 5e-4);
        assertEquals(0.029, at("Smithing", 10).secondary(Stat.WEAR_RESISTANCE), 5e-4);
        assertTrue(at("Smithing", 150).secondary(Stat.WEAR_RESISTANCE) < 0.35, "mastery past 100 still never reaches the cap");
        assertEquals(0.025, s.armorBonus(), 1e-9, "Fortitude 25: +2.5% armor");
        assertEquals(1.25, s.defenseBarMultiplier(), 1e-9, "...a 25% larger defense bar");
        assertEquals(0.05, s.resist(), 1e-9, "...and +5% resistances");
        AttributeSnapshot tough = AttributeCalculator.compute(List.of(Contribution.attribute("t", "t", Attribute.FORTITUDE, 300, null)), AttributeRules.DEFAULTS);
        assertEquals(0.0, tough.secondary(Stat.WEAR_RESISTANCE), 1e-12, "Fortitude alone doesn't drive it: only Smithing does");
        AttributeSnapshot all = AttributeCalculator.compute(SkillAttributes.contributions(
                java.util.Map.of("Smithing", 100, "Heavy Armor Training", 100, "Survival", 100, "Defensive Magic", 100), AttributeRules.DEFAULTS),
                AttributeRules.DEFAULTS);
        assertEquals(225, all.get(Attribute.FORTITUDE), 1e-9, "every Fortitude skill at 100 stays under the 300 cap");
        assertTrue(all.resist() <= AttributeRules.DEFAULTS.limits().resist() + 1e-12 && all.armorBonus() <= AttributeRules.DEFAULTS.limits().armor() + 1e-12,
                "and inside the armour and resistance limits");
    }

    @Test
    void wearResistanceCanNeverMakeGearUnbreakable() throws Exception {
        YamlConfiguration server = new YamlConfiguration();
        server.loadFromString("""
                attributes:
                  secondary:
                    stats:
                      wear-resistance: { enabled: true, driver: fortitude, per-point: 0, cap: 1.0 }
                      attack-speed:    { enabled: true, driver: agility, per-point: 0.001, cap: 1.5 }
                """);
        List<String> problems = new ArrayList<>();
        AttributeRules r = AttributesConfig.parseRules(AttributesConfig.section(server, "attributes"), AttributeRules.DEFAULT_CURVES, problems::add);
        assertEquals(AttributeRules.DEFAULT_SECONDARY.get(Stat.WEAR_RESISTANCE).cap(), r.secondary().get(Stat.WEAR_RESISTANCE).cap(),
                "a cap of 1 (never wear) is refused: the default is used");
        assertTrue(problems.stream().anyMatch(x -> x.contains("wear-resistance.cap")), problems.toString());
        assertEquals(1.5, r.secondary().get(Stat.ATTACK_SPEED).cap(), 1e-12, "a speed may still go up to 2");
        server.loadFromString("attributes:\n  secondary:\n    stats:\n      wear-resistance: { cap: 0.9 }\n");
        assertEquals(0.9, AttributesConfig.parseRules(AttributesConfig.section(server, "attributes"), AttributeRules.DEFAULT_CURVES, x -> {})
                .secondary().get(Stat.WEAR_RESISTANCE).cap(), 1e-12, "0.9 is the most it may be");
    }

    private static AttributeSnapshot at(String skill, int level) {
        return AttributeCalculator.compute(SkillAttributes.contributions(java.util.Map.of(skill, level), AttributeRules.DEFAULTS), AttributeRules.DEFAULTS);
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
                    Scavenging: [ { attribute: fortune, per-level: 0.5 } ]
                    Trading: [ { attribute: fortune, per-level: 0.5, yield: all } ]
                """);
        List<String> problems = new ArrayList<>();
        AttributeRules r = AttributesConfig.parseRules(AttributesConfig.section(server, "attributes"), AttributeRules.DEFAULT_CURVES, problems::add);
        assertEquals(Yield.ORE, r.skillWeights().get("Mining").get(0).yield());
        assertEquals(Yield.WOOD, r.skillWeights().get("Woodcutting").get(0).yield());
        assertEquals(Yield.FISH, r.skillWeights().get("Fishing").get(0).yield(), "Fishing's counts on fish (2026-10-27), even in a file from before");
        assertNull(r.skillWeights().get("Scavenging").get(0).yield(), "any other skill's Fortune counts everywhere");
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
