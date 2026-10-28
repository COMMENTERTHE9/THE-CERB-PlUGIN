package Attributes;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** Martial Expertise: Power (unarmed) 1.0 and Agility 1.25 per level (docs/design/COMBAT_IMPROVEMENTS.md §1). */
class MartialExpertiseTest {

    private static final AttributeRules R = AttributeRules.DEFAULTS;
    private static final int[] LEVELS = {1, 10, 25, 50, 75, 100};

    /** A character with every skill at 1 (a new one) and Martial Expertise at {@code level}. */
    private static AttributeSnapshot character(int level) {
        Map<String, Integer> levels = new HashMap<>();
        for (String skill : R.skillWeights().keySet()) levels.put(skill, 1);
        levels.put("Martial Expertise", level);
        return AttributeCalculator.compute(SkillAttributes.contributions(levels, R), R);
    }

    private static AttributeSnapshot alone(int level) {
        return AttributeCalculator.compute(SkillAttributes.contributions(Map.of("Martial Expertise", level), R), R);
    }

    @Test
    void eachLevelAddsAgilityAndKeepsUnarmedPower() {
        for (int l : LEVELS) {
            AttributeSnapshot s = alone(l);
            assertEquals(1.25 * l, s.get(Attribute.AGILITY), 1e-9, "Agility at " + l);
            assertEquals(1.0 * l, s.power(WeaponClass.UNARMED), 1e-9, "Power (unarmed) at " + l);
            assertEquals(0.0, s.power(WeaponClass.SWORD), 1e-9, "no Power with a sword at " + l);
        }
    }

    @Test
    void agilityRaisesCritAndSpeedThroughTheSharedCapsAtEveryLevel() {
        AttributeSnapshot previous = null;
        for (int l : LEVELS) {
            AttributeSnapshot s = character(l);
            assertEquals(2.0 + 1.25 * l, s.get(Attribute.AGILITY), 1e-9, "the other Agility skills at 1 give 2.0; at " + l);
            assertTrue(s.critChance() <= R.limits().critChance() + 1e-12, "crit chance cap at " + l);
            assertTrue(s.critMultiplier() <= R.limits().critMultiplier() + 1e-12, "crit multiplier cap at " + l);
            for (Stat st : List.of(Stat.ATTACK_SPEED, Stat.DRAW_SPEED, Stat.MOVE_SPEED)) {
                assertTrue(s.secondary(st) < R.secondary().get(st).cap(), st + " under its cap at " + l);
            }
            double extras = s.offenseMultiplier(WeaponClass.UNARMED, true) / s.offenseMultiplier(WeaponClass.UNARMED, false);
            assertTrue(extras <= R.limits().offenseBudget() + 1e-9, "the offense budget holds at " + l);
            if (previous != null) {
                assertTrue(s.critChance() > previous.critChance() && s.secondary(Stat.MOVE_SPEED) > previous.secondary(Stat.MOVE_SPEED),
                        "more at " + l + " than below it");
            }
            previous = s;
        }
        // The numbers in the design doc's table (a new character, Martial Expertise at 1 / 50 / 100).
        assertEquals(0.147, character(1).critChance(), 5e-4);
        assertEquals(0.515, character(50).critChance(), 5e-4);
        assertEquals(0.743, character(100).critChance(), 5e-4);
        assertEquals(3.19, character(100).critMultiplier(), 5e-3);
        assertEquals(0.055, character(100).secondary(Stat.MOVE_SPEED), 5e-4, "Navigation 1's own +0.1% included");
        assertEquals(6.01, character(100).offenseMultiplier(WeaponClass.UNARMED, false), 5e-3, "Power (unarmed) 100 + Weapon Mastery's 0.25");
    }

    @Test
    void agilityIsGeneralItCountsWithEveryWeapon() {
        AttributeSnapshot s = character(50);
        assertEquals(s.critChance(), character(50).critChance(), 0.0);
        assertTrue(s.attackSpeed(WeaponClass.SWORD) > character(1).attackSpeed(WeaponClass.SWORD), "a sword's attack speed rises too");
    }

    private static YamlConfiguration shipped() throws Exception {
        YamlConfiguration y = new YamlConfiguration();
        y.loadFromString(Files.readString(Path.of("src/main/resources/attributes.yml")).replace("\r\n", "\n"));
        return y;
    }

    private static AttributeRules parse(String skills, List<String> problems) throws Exception {
        YamlConfiguration server = new YamlConfiguration();
        server.loadFromString("attributes:\n  skills:\n" + skills);
        server.setDefaults(shipped());
        return AttributesConfig.parseRules(AttributesConfig.section(server, "attributes"), AttributeRules.DEFAULT_CURVES, problems::add);
    }

    @Test
    void anEditedFileStillHoldingTheOldEntryGetsAgility() throws Exception {
        List<String> problems = new ArrayList<>();
        AttributeRules r = parse("    Martial Expertise:    [ { attribute: power, per-level: 1.0, weapon: unarmed } ]\n", problems);
        assertEquals(AttributeRules.DEFAULT_SKILL_WEIGHTS.get("Martial Expertise"), r.skillWeights().get("Martial Expertise"));
        assertTrue(problems.stream().anyMatch(p -> p.contains("Martial Expertise") && p.contains("agility 1.25") && p.contains("per-level: 0")),
                problems.toString());
    }

    @Test
    void anOwnersOwnEntryIsKept() throws Exception {
        List<String> problems = new ArrayList<>();
        AttributeRules r = parse("    Martial Expertise:    [ { attribute: power, per-level: 2.0, weapon: unarmed } ]\n", problems);
        assertEquals(List.of(new AttributeRules.SkillWeight(Attribute.POWER, 2.0, WeaponClass.UNARMED)), r.skillWeights().get("Martial Expertise"));
        assertTrue(problems.stream().anyMatch(p -> p.contains("your own entry is kept") && p.contains("agility 1.25")), problems.toString());

        problems.clear();
        r = parse("    Martial Expertise:\n      - { attribute: power, per-level: 1.0, weapon: unarmed }\n      - { attribute: agility, per-level: 0 }\n", problems);
        assertEquals(0.0, AttributeCalculator.compute(SkillAttributes.contributions(Map.of("Martial Expertise", 100), r), r).get(Attribute.AGILITY), 1e-12,
                "the way the log gives to keep it without Agility works");

        problems.clear();
        r = parse("    Martial Expertise: []\n", problems);
        assertFalse(r.skillWeights().containsKey("Martial Expertise"), "\"Martial Expertise: []\" still turns it off");

        problems.clear();
        r = parse("    Martial Expertise:\n      - { attribute: power, per-level: 1.0, weapon: unarmed }\n      - { attribute: agility, per-level: 1.25 }\n", problems);
        assertTrue(problems.stream().noneMatch(p -> p.contains("Martial Expertise")), "this version's entry: nothing to say " + problems);
    }
}
