package GUIs;

import Attributes.AttributeRules;
import GUIs.SkillDefinitions.EffectStatus;
import GUIs.SkillDefinitions.SkillDef;
import GUIs.SkillDefinitions.SkillEffect;
import Skills.SkillNames;
import cerberus.world.cerb.DatabaseManager;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/** The skill catalog stays consistent with the database, the attribute rules and its own honesty rules. */
class SkillCatalogTest {

    private final List<SkillDef> all = SkillDefinitions.defaults();

    @Test
    void sameThirtyFiveSkillsAsTheDatabase() {
        Set<String> names = all.stream().map(SkillDef::name).collect(Collectors.toSet());
        assertEquals(35, all.size());
        assertEquals(35, names.size(), "internal names are unique");
        assertEquals(new HashSet<>(DatabaseManager.allSkillNames()), names);
    }

    @Test
    void playerFacingNamesAreUniqueToo() {
        Set<String> shown = new HashSet<>();
        for (SkillDef d : all) assertTrue(shown.add(SkillNames.normalize(d.displayName())), d.displayName());
    }

    @Test
    void everyRenameNamesARealSkill() {
        Set<String> names = all.stream().map(SkillDef::name).collect(Collectors.toSet());
        assertTrue(names.containsAll(SkillNames.DEFAULT_RENAMES.keySet()));
    }

    @Test
    void attributeRulesOnlyNameCatalogSkills() {
        Set<String> names = all.stream().map(SkillDef::name).collect(Collectors.toSet());
        for (String skill : AttributeRules.DEFAULT_SKILL_WEIGHTS.keySet()) assertTrue(names.contains(skill), skill);
        assertTrue(names.contains(AttributeRules.DEFAULT_CURVES.lifestealSkill()));
    }

    @Test
    void workingEffectsHaveNumbersAndUnbuiltOnesDoNot() {
        for (SkillDef d : all) {
            for (SkillEffect e : d.effects()) {
                if (e.status() == EffectStatus.NOT_ACTIVE) {
                    assertNull(e.value(), d.name() + ": " + e.label());
                } else {
                    assertNotNull(e.value(), d.name() + ": " + e.label());
                    assertNotNull(e.unit(), d.name() + ": " + e.label());
                }
            }
        }
    }

    @Test
    void descriptionsAreShortAndPresent() {
        for (SkillDef d : all) {
            assertFalse(d.description().isBlank(), d.name());
            assertTrue(d.description().length() <= 70, d.name() + " description too long");
        }
    }

    @Test
    void lookupWorksByEitherName() {
        assertEquals("Navigation", SkillDefinitions.byName("Pathfinding").name());
        assertEquals("Navigation", SkillDefinitions.byName("navigation").name());
        assertEquals("First Aid", SkillDefinitions.byName("field_medicine").name());
        assertNull(SkillDefinitions.byName("Not A Skill"));
    }
}
