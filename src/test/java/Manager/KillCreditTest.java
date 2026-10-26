package Manager;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Which skill a kill trains (docs/STATUS.md, "XP integrity"). The exploit: a kill trained the item in hand at the
 * moment of death, so an arrow landing after a switch to a sword trained Blade Mastery, a thrown trident trained
 * the empty hand, and a held item chose the skill for a kill it didn't make.
 */
class KillCreditTest {

    private final UUID mob = UUID.randomUUID(), archer = UUID.randomUUID(), friend = UUID.randomUUID();

    @Test
    void theLastHitDecidesNotTheItemInHand() {
        KillCredit credit = new KillCredit();
        credit.hit(mob, archer, "Ranged Precision", 0);
        assertEquals("Ranged Precision", credit.skillFor(mob, archer, "Blade Mastery", 1_000), "the arrow's kill, sword in hand");
        credit.hit(mob, archer, "Blade Mastery", 2_000);
        assertEquals("Blade Mastery", credit.skillFor(mob, archer, "Ranged Precision", 2_500), "a later sword hit takes over");
    }

    @Test
    void noCreditMeansTheItemInHand() {
        KillCredit credit = new KillCredit();
        assertEquals("Blade Mastery", credit.skillFor(mob, archer, "Blade Mastery", 0), "nothing recorded: as before");
        credit.hit(mob, friend, "Ranged Precision", 0);
        assertEquals("Blade Mastery", credit.skillFor(mob, archer, "Blade Mastery", 1), "another player's hit doesn't choose my skill");
        credit.hit(mob, archer, "Ranged Precision", 0);
        assertEquals("Blade Mastery", credit.skillFor(mob, archer, "Blade Mastery", KillCredit.FRESH_MS + 1), "an old hit gives way");
        assertEquals("Ranged Precision", credit.skillFor(mob, archer, "Blade Mastery", KillCredit.FRESH_MS));
    }

    @Test
    void aHitWithNothingThatTrainsASkillDoesntOverwrite() {
        KillCredit credit = new KillCredit();
        credit.hit(mob, archer, "Ranged Precision", 0);
        credit.hit(mob, archer, "", 1);
        credit.hit(mob, archer, null, 2);
        assertEquals("Ranged Precision", credit.skillFor(mob, archer, "", 3));
    }

    @Test
    void entriesGoAtDeathAndWhenIdle() {
        KillCredit credit = new KillCredit();
        credit.hit(mob, archer, "Ranged Precision", 0);
        credit.forget(mob);
        assertEquals(0, credit.size());
        credit.hit(mob, archer, "Ranged Precision", KillCredit.PRUNE_EVERY_MS);
        credit.hit(UUID.randomUUID(), archer, "Blade Mastery", KillCredit.PRUNE_EVERY_MS + KillCredit.IDLE_MS + 1);
        assertEquals(1, credit.size(), "the idle entry went with the next sweep");
        credit.hit(null, archer, "Blade Mastery", 0);
        credit.forget(null);
        assertEquals(1, credit.size());
    }
}
