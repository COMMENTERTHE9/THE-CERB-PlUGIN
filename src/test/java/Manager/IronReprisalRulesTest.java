package Manager;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/** Iron Reprisal's push (docs/design/COMBAT_IMPROVEMENTS.md §2): level and heavy pieces, no chance, its settings. */
class IronReprisalRulesTest {

    private static final IronReprisalRules D = IronReprisalRules.DEFAULTS;

    @Test
    void opensAtItsLevelAndGrowsEvenlyToItsMaximum() {
        assertEquals(0.0, D.strength(1, 4), 0.0, "level 1: not yet");
        assertEquals(0.0, D.strength(9, 4), 0.0);
        assertEquals(0.4, D.strength(10, 4), 1e-12, "at the unlock level: a plain hit's push in full heavy armor");
        assertEquals(0.7, D.strength(55, 4), 1e-12, "halfway");
        assertEquals(1.0, D.strength(100, 4), 1e-12);
        assertEquals(1.0, D.strength(150, 4), 1e-12, "mastery past 100 keeps the maximum");
        double previous = 0;
        for (int level = 10; level <= 100; level++) {
            double s = D.strength(level, 4);
            assertTrue(s >= previous, "never weaker at a higher level: " + level);
            previous = s;
        }
    }

    @Test
    void eachHeavyPieceIsAQuarter() {
        assertEquals(0.0, D.strength(100, 0), 0.0, "no heavy armor, no push");
        assertEquals(0.25, D.strength(100, 1), 1e-12);
        assertEquals(0.5, D.strength(100, 2), 1e-12);
        assertEquals(0.75, D.strength(100, 3), 1e-12);
        assertEquals(1.0, D.strength(100, 4), 1e-12);
        assertEquals(1.0, D.strength(100, 9), 1e-12, "four pieces at most");
        assertEquals(0.1, D.strength(10, 1), 1e-12);
    }

    @Test
    void theSameHitAlwaysPushesTheSame() {
        // No chance roll: the push is a function of level and pieces only.
        for (int i = 0; i < 1000; i++) assertEquals(0.7, D.strength(55, 4), 0.0);
    }

    @Test
    void offMeansNothing() {
        IronReprisalRules off = new IronReprisalRules(false, 10, 0.4, 100, 1.0, 500, false, true, Set.of());
        assertEquals(0.0, off.strength(100, 4), 0.0);
        assertEquals(List.of(), off.openedBetween(9, 10));
    }

    @Test
    void theTechniqueIsAnnouncedOnceAtItsLevel() {
        assertEquals(1, D.openedBetween(9, 10).size());
        assertTrue(D.openedBetween(9, 10).get(0).startsWith("Iron Reprisal: "));
        assertEquals(1, D.openedBetween(1, 40).size(), "a jump past it still announces it");
        assertEquals(List.of(), D.openedBetween(10, 11), "not again");
        assertEquals(List.of(), D.openedBetween(5, 9));
    }

    @Test
    void bossesAreExempt() {
        assertFalse(D.throwable("ENDER_DRAGON"));
        assertFalse(D.throwable("wither"));
        assertTrue(D.throwable("ZOMBIE"));
        assertFalse(D.throwable(null));
    }

    @Test
    void theShippedFileSaysWhatTheCodeDefaultsSay() throws Exception {
        YamlConfiguration y = YamlConfiguration.loadConfiguration(Files.newBufferedReader(Path.of("src/main/resources/combat.yml")));
        List<String> problems = new ArrayList<>();
        IronReprisalRules r = IronReprisalRules.parse(y.getConfigurationSection("skills.heavy-armor-training.iron-reprisal"), problems::add);
        assertEquals(List.of(), problems);
        assertEquals(D, r, "an older combat.yml without the section plays exactly like the shipped one");
        assertEquals(D, IronReprisalRules.parse(null, problems::add));
    }

    @Test
    void badValuesKeepTheDefaults() throws Exception {
        YamlConfiguration y = new YamlConfiguration();
        y.loadFromString("""
                r:
                  unlock-level: 0
                  strength-at-unlock: -1
                  max-strength: 99
                  cooldown-ms: -5
                  pvp: true
                  exempt: [ warden ]
                """);
        List<String> problems = new ArrayList<>();
        IronReprisalRules r = IronReprisalRules.parse(y.getConfigurationSection("r"), problems::add);
        assertEquals(4, problems.size(), problems.toString());
        assertEquals(D.unlockLevel(), r.unlockLevel());
        assertEquals(D.strengthAtUnlock(), r.strengthAtUnlock());
        assertEquals(D.strengthAtMax(), r.strengthAtMax());
        assertEquals(D.cooldownMs(), r.cooldownMs());
        assertTrue(r.pvp(), "a good value is taken");
        assertEquals(Set.of("WARDEN"), r.exempt());
        assertTrue(r.throwable("ENDER_DRAGON"), "the owner's own list replaces the default one");
    }
}
