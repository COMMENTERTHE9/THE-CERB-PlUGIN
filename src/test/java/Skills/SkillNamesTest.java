package Skills;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class SkillNamesTest {

    @AfterEach
    void restore() {
        SkillNames.setRenames(SkillNames.DEFAULT_RENAMES);
    }

    @Test
    void theFourPolishedNames() {
        assertEquals("Heavy Armor Mastery", SkillNames.display("Heavy Armor Training"));
        assertEquals("Field Medicine", SkillNames.display("First Aid"));
        assertEquals("Essence Regeneration", SkillNames.display("Mana Regeneration"));
        assertEquals("Pathfinding", SkillNames.display("Navigation"));
    }

    @Test
    void everyOtherSkillKeepsItsName() {
        assertEquals("Blade Mastery", SkillNames.display("Blade Mastery"));
        assertEquals("Critical Strike", SkillNames.display("Critical Strike"));
        assertEquals("Survival", SkillNames.display("Survival"));
        assertNull(SkillNames.formerName("Blade Mastery"));
        assertEquals("", SkillNames.display(null));
    }

    @Test
    void formerNameIsTheInternalName() {
        assertEquals("Navigation", SkillNames.formerName("Navigation"));
    }

    @Test
    void newNamesResolveToTheInternalNameLoosely() {
        assertEquals("Navigation", SkillNames.internalForDisplay("pathfinding"));
        assertEquals("First Aid", SkillNames.internalForDisplay("Field_Medicine"));
        assertEquals("Heavy Armor Training", SkillNames.internalForDisplay("heavy armor mastery"));
        assertNull(SkillNames.internalForDisplay("Blade Mastery")); // not renamed: callers match it directly
        assertNull(SkillNames.internalForDisplay(null));
    }

    @Test
    void serverOverridesReplaceTheTableAndIgnoreBlanks() {
        SkillNames.setRenames(Map.of("Survival", "Wilderness Lore", "Mining", " ", "Fishing", "Fishing"));
        assertEquals("Wilderness Lore", SkillNames.display("Survival"));
        assertEquals("Mining", SkillNames.display("Mining"));
        assertEquals("Fishing", SkillNames.display("Fishing"));
        assertNull(SkillNames.formerName("Fishing"));
        assertEquals("Navigation", SkillNames.display("Navigation")); // not in the new table
    }
}
