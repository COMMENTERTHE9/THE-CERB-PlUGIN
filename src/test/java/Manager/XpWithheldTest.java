package Manager;

import org.bukkit.GameMode;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * What the HUD says when XP is withheld on purpose (the owner's report, 2026-10-27: a creative catch and a creative bow
 * kill showed no progress and no reason). The rules themselves are unchanged; these are the words.
 */
class XpWithheldTest {

    @Test
    void creativeAndSpectatorSayWhereSkillsTrain() {
        assertEquals("in creative mode §8· §7survival and adventure train skills", XpIntegrity.whyNot(GameMode.CREATIVE));
        assertTrue(XpIntegrity.whyNot(GameMode.SPECTATOR).startsWith("in spectator mode"));
    }

    @Test
    void eachZeroShareOriginHasItsReason() {
        assertEquals("from mobs out of spawn eggs", XpIntegrity.originWhy("SPAWNER_EGG"));
        assertEquals("from mobs out of spawn eggs", XpIntegrity.originWhy("DISPENSE_EGG"));
        assertEquals("from bred mobs", XpIntegrity.originWhy("BREEDING"), "villagers breed too");
        assertEquals("from mobs hatched from eggs", XpIntegrity.originWhy("EGG"), "thrown eggs, turtle and sniffer eggs, frogspawn");
        assertEquals("from mobs you built", XpIntegrity.originWhy("BUILD_IRONGOLEM"));
        // A server can give any spawn reason a share of 0 (attributes.yml xp-integrity.by-origin): it is named as it is.
        assertEquals("from mobs spawned this way (nether portal)", XpIntegrity.originWhy("NETHER_PORTAL"));
        assertEquals("from this mob", XpIntegrity.originWhy(null));
    }

    @Test
    void aSpawnEggsReasonIsKeptWherePapersRecordFellBack() {
        // A 1.21.11 spawn egg carries entity data: Paper's load records DEFAULT (an animal's NATURAL) before the spawn.
        assertEquals("SPAWNER_EGG", XpIntegrity.originToKeep("SPAWNER_EGG", "DEFAULT", null));
        assertEquals("SPAWNER_EGG", XpIntegrity.originToKeep("SPAWNER_EGG", "NATURAL", null), "a spawn-egg cow");
        assertEquals("DISPENSE_EGG", XpIntegrity.originToKeep("DISPENSE_EGG", "DEFAULT", null));
        assertEquals("SPAWNER_EGG", XpIntegrity.originToKeep("SPAWNER_EGG", null, null));
    }

    @Test
    void aSpawnThatAgreesWithItsRecordWritesNothing() {
        assertNull(XpIntegrity.originToKeep("NATURAL", "NATURAL", null));
        assertNull(XpIntegrity.originToKeep("BREEDING", "BREEDING", null));
        assertNull(XpIntegrity.originToKeep("SPAWNER", "SPAWNER", null));
        assertNull(XpIntegrity.originToKeep("COMMAND", "COMMAND", null));
        assertNull(XpIntegrity.originToKeep(null, "DEFAULT", null));
    }

    @Test
    void aCopyKeepsWhereTheOriginalCameFrom() {
        // A mob stacker's copy of a spawner's zombie is added as CUSTOM; Paper restored SPAWNER from its data.
        assertNull(XpIntegrity.originToKeep("CUSTOM", "SPAWNER", null), "a quarter, not full pay");
        // A copy of a spawn-egg zombie carries our record; so does a spawn-egg parrot coming off a shoulder.
        assertNull(XpIntegrity.originToKeep("CUSTOM", "DEFAULT", "SPAWNER_EGG"));
        assertNull(XpIntegrity.originToKeep("SHOULDER_ENTITY", "DEFAULT", "SPAWNER_EGG"));
    }
}
