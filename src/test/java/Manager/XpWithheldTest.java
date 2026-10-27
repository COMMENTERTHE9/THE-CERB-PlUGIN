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
        assertEquals("from animals you bred", XpIntegrity.originWhy("BREEDING"));
        assertEquals("from chickens out of thrown eggs", XpIntegrity.originWhy("EGG"));
        assertEquals("from mobs you built", XpIntegrity.originWhy("BUILD_IRONGOLEM"));
        // A server can give any spawn reason a share of 0 (attributes.yml xp-integrity.by-origin): it is named as it is.
        assertEquals("from mobs spawned this way (nether portal)", XpIntegrity.originWhy("NETHER_PORTAL"));
        assertEquals("from this mob", XpIntegrity.originWhy(null));
    }
}
