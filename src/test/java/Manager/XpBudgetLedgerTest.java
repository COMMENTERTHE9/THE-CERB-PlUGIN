package Manager;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Per-hit XP budgets (docs/STATUS.md, "XP integrity"): a mob pays each skill at most what its kill pays. The exploit:
 * one zombie hit forever in a cage (Critical Strike) or hitting an idle player in a trap (Survival) paid forever.
 */
class XpBudgetLedgerTest {

    private final UUID caged = UUID.randomUUID(), other = UUID.randomUUID();
    private static final double BUDGET = 40; // a level-1 Common mob's kill XP

    @Test
    void aCagedMobRunsDryAndARealFightIsUnchanged() {
        XpBudgetLedger ledger = new XpBudgetLedger();
        double total = 0;
        for (int hit = 0; hit < 1000; hit++) total += ledger.pay(caged, "Critical Strike", 5, BUDGET, hit * 500L);
        assertEquals(BUDGET, total, 1e-9, "a thousand hits on one caged mob pay its kill XP, not 5000");
        // A fight that ends in a kill: 6 hits of 5 are all paid.
        double fight = 0;
        for (int hit = 0; hit < 6; hit++) fight += ledger.pay(other, "Critical Strike", 5, BUDGET, hit * 500L);
        assertEquals(30, fight, 1e-9, "a normal fight pays every hit");
    }

    @Test
    void theLastPaymentIsWhatIsLeftAndSkillsHaveTheirOwnBudgets() {
        XpBudgetLedger ledger = new XpBudgetLedger();
        ledger.pay(caged, "Survival", 36, BUDGET, 0);
        assertEquals(4, ledger.pay(caged, "Survival", 6, BUDGET, 1), "what is left");
        assertEquals(0, ledger.pay(caged, "Survival", 6, BUDGET, 2));
        assertEquals(6, ledger.pay(caged, "Heavy Armor Training", 6, BUDGET, 3), "another skill has its own budget");
        assertEquals(40, ledger.spent(caged, "Survival"));
        assertEquals(6, ledger.spent(caged, "Heavy Armor Training"));
        assertEquals(0, ledger.spent(other, "Survival"));
    }

    @Test
    void aBiggerBudgetLaterPaysTheDifference() {
        // The budget is the mob's kill XP for the player's skill level, which only falls (weak-mob rule) as the
        // player levels; a raised budget (a config change) pays only the difference.
        XpBudgetLedger ledger = new XpBudgetLedger();
        ledger.pay(caged, "Survival", 40, 40, 0);
        assertEquals(5, ledger.pay(caged, "Survival", 5, 60, 1));
        assertEquals(0, ledger.pay(caged, "Survival", 5, 10, 2), "a smaller budget than spent pays nothing (never negative)");
    }

    @Test
    void deadMobsGoAfterTheGraceAndIdleOnesAfterHalfAnHour() {
        XpBudgetLedger ledger = new XpBudgetLedger();
        ledger.pay(caged, "Survival", 40, BUDGET, 0);
        ledger.pay(other, "Survival", 6, BUDGET, 0);
        ledger.died(caged, 1_000);
        assertEquals(0, ledger.pay(caged, "Survival", 5, BUDGET, 2_000), "a spell landing after the death sees the spent budget");
        ledger.prune(1_000 + XpBudgetLedger.DEAD_GRACE_MS);
        assertEquals(40, ledger.spent(caged, "Survival"), "kept for the grace");
        ledger.prune(1_000 + XpBudgetLedger.DEAD_GRACE_MS + 1);
        assertEquals(0, ledger.spent(caged, "Survival"), "then dropped");
        assertEquals(1, ledger.size(), "the live mob's entry stays");
        ledger.prune(XpBudgetLedger.IDLE_MS + 1);
        assertEquals(0, ledger.size(), "an entry idle for half an hour goes (the mob unloaded or wandered off)");
        ledger.died(UUID.randomUUID(), 0);
        assertEquals(0, ledger.size(), "a death without payments leaves nothing behind");
    }

    @Test
    void paymentsSweepOldEntriesByThemselves() {
        XpBudgetLedger ledger = new XpBudgetLedger();
        ledger.pay(caged, "Survival", 6, BUDGET, XpBudgetLedger.PRUNE_EVERY_MS);
        ledger.died(caged, XpBudgetLedger.PRUNE_EVERY_MS);
        ledger.pay(other, "Survival", 6, BUDGET, 2 * XpBudgetLedger.PRUNE_EVERY_MS + XpBudgetLedger.DEAD_GRACE_MS);
        assertEquals(1, ledger.size(), "the dead mob's entry went with the next sweep");
    }

    @Test
    void garbageInPaysNothing() {
        XpBudgetLedger ledger = new XpBudgetLedger();
        assertEquals(0, ledger.pay(null, "Survival", 5, BUDGET, 0));
        assertEquals(0, ledger.pay(caged, null, 5, BUDGET, 0));
        assertEquals(0, ledger.pay(caged, "Survival", -5, BUDGET, 0));
        assertEquals(0, ledger.pay(caged, "Survival", Double.NaN, BUDGET, 0));
        assertEquals(0, ledger.pay(caged, "Survival", 5, 0, 0), "a mob whose kill pays nothing pays no hits");
        assertEquals(0, ledger.pay(caged, "Survival", 5, Double.POSITIVE_INFINITY, 0));
        assertEquals(0, ledger.size());
    }
}
