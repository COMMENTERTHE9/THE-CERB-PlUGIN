package Attributes;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** What can't be farmed (attributes.yml {@code xp-integrity}; docs/STATUS.md, "XP integrity"). */
class XpRulesTest {

    private static YamlConfiguration shipped() throws Exception {
        YamlConfiguration y = new YamlConfiguration();
        y.loadFromString(Files.readString(Path.of("src/main/resources/attributes.yml")).replace("\r\n", "\n"));
        return y;
    }

    @Test
    void farmMobsPayAShareAndNaturalOnesInFull() {
        XpRules r = XpRules.DEFAULTS;
        assertEquals(0.25, r.originFactor("SPAWNER"), "a mob grinder's mob");
        assertEquals(0.0, r.originFactor("BREEDING"), "a bred animal");
        assertEquals(0.0, r.originFactor("SPAWNER_EGG"));
        assertEquals(1.0, r.originFactor("NATURAL"), "not listed: in full");
        assertEquals(1.0, r.originFactor("TRIAL_SPAWNER"), "trial spawners are a real fight");
        assertEquals(1.0, r.originFactor("COMMAND"), "admin spawns (the harnesses) pay");
        assertEquals(1.0, r.originFactor(null), "no reason known: in full");
        assertEquals(0.25, r.originFactor("spawner"), "case doesn't matter");
    }

    @Test
    void sharesStayBetweenNothingAndFull() {
        XpRules r = new XpRules(Map.of("SPAWNER", 3.0, "BREEDING", -1.0, "EGG", Double.NaN), false, true, 60);
        assertEquals(1.0, r.originFactor("SPAWNER"));
        assertEquals(0.0, r.originFactor("BREEDING"));
        assertEquals(1.0, r.originFactor("EGG"), "a broken number pays in full rather than surprising anyone");
    }

    @Test
    void idleMeansNothingDoneForTheConfiguredTime() {
        XpRules r = XpRules.DEFAULTS;
        assertFalse(r.idle(0));
        assertFalse(r.idle(59_999));
        assertTrue(r.idle(60_000));
        XpRules off = new XpRules(Map.of(), false, true, 0);
        assertFalse(off.idle(Long.MAX_VALUE), "0 turns the rule off");
        assertEquals(0, new XpRules(Map.of(), false, true, -5).idleSeconds(), "a negative time is no rule");
    }

    @Test
    void theShippedFileMatchesTheBuiltInDefaults() throws Exception {
        List<String> problems = new ArrayList<>();
        XpRules r = AttributesConfig.parseXpRules(shipped(), problems::add);
        assertEquals(List.of(), problems);
        assertEquals(XpRules.DEFAULTS, r, "file and built-in defaults agree");
    }

    @Test
    void anOlderFileGetsTheShippedRulesAndAnOwnersChoicesStay() throws Exception {
        YamlConfiguration older = new YamlConfiguration();
        older.loadFromString("kill-xp:\n  base: 40\n");
        older.setDefaults(shipped()); // as AttributesConfig.reload does with the jar's copy
        List<String> problems = new ArrayList<>();
        assertEquals(XpRules.DEFAULTS, AttributesConfig.parseXpRules(older, problems::add), "no section: the jar's");
        assertEquals(List.of(), problems);

        YamlConfiguration own = new YamlConfiguration();
        own.loadFromString("""
                xp-integrity:
                  by-origin:
                    spawner: 0.5
                    MOB_FARM: 0
                  pvp-xp: true
                  idle-seconds: 0
                """);
        own.setDefaults(shipped());
        XpRules r = AttributesConfig.parseXpRules(own, problems::add);
        assertEquals(0.5, r.originFactor("SPAWNER"), "the owner's number");
        assertEquals(1.0, r.originFactor("BREEDING"), "a reason the owner's list leaves out pays in full, as the file says");
        assertEquals(1, problems.size(), problems.toString());
        assertTrue(problems.get(0).contains("MOB_FARM"), "a reason that isn't one is reported: " + problems);
        assertTrue(r.pvpXp());
        assertTrue(r.hitBudget(), "a missing key: the shipped value");
        assertEquals(0, r.idleSeconds());
    }
}
