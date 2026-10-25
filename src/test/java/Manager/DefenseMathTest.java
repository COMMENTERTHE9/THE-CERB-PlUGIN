package Manager;

import Attributes.AttributeRules;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * How a hit on a player is reduced: resistance and armor are damage reductions (they stack and
 * share the 75% cap); the defense bar is a draining pool after them and is not part of the cap.
 * {@link DefenseMath#resolve} is exactly what the damage pipeline applies.
 */
class DefenseMathTest {

    private static final double EPS = 1e-9;
    private static final double CAP = DefenseMath.DEFAULT_REDUCTION_CAP;

    @Test
    void reductionsStackByMultiplyingWhatGetsThrough() {
        assertEquals(0.0, DefenseMath.combined(), EPS);
        assertEquals(0.40, DefenseMath.combined(0.40), EPS);
        assertEquals(0.70, DefenseMath.combined(0.50, 0.40), EPS);   // 1 - 0.5 x 0.6, not 0.5 + 0.4
        assertEquals(0.85, DefenseMath.combined(0.75, 0.40), EPS);
        assertEquals(0.40, DefenseMath.combined(-0.2, 0.40), EPS);   // a negative layer counts as none
        assertEquals(1.0, DefenseMath.combined(1.7), EPS);           // a layer above 100% counts as 100%
    }

    @Test
    void theCapLimitsTheCombinedReductionOnly() {
        assertEquals(0.70, DefenseMath.capped(0.70, CAP), EPS);      // under the cap: untouched
        assertEquals(0.75, DefenseMath.capped(0.85, CAP), EPS);
        assertEquals(0.75, DefenseMath.capped(0.85, Double.NaN), EPS); // a broken cap falls back to 75%
        assertEquals(0.60, DefenseMath.capped(0.85, 0.60), EPS);
    }

    @Test
    void todaysMaximumsStayUnderTheCap_soTheCapChangesNoCurrentHit() throws Exception {
        YamlConfiguration combat;
        try (InputStream in = getClass().getClassLoader().getResourceAsStream("combat.yml")) {
            combat = YamlConfiguration.loadConfiguration(new InputStreamReader(in, StandardCharsets.UTF_8));
        }
        AttributeRules.Limits lim = AttributeRules.DEFAULTS.limits();
        double maxArmor = Math.min(combat.getDouble("armor.max-reduction"), lim.armor()); // shipped: 0.40
        double maxResist = lim.resist();                             // fire / cold / drowning, 0.50
        assertEquals(0.75, lim.totalMitigation(), EPS);
        double strongest = DefenseMath.combined(maxResist, maxArmor);
        assertEquals(0.70, strongest, EPS);
        assertTrue(strongest < lim.totalMitigation());
        assertEquals(strongest, DefenseMath.capped(strongest, lim.totalMitigation()), EPS);
    }

    @Test
    void beforeAndAfter_reductionsPastTheCap() {
        // A future 75% fire resistance (talisman) with 40% armor: 85% uncapped.
        double uncappedToBar = 100 * (1 - 0.75) * (1 - 0.40);       // before: 15 got through
        DefenseMath.Hit h = DefenseMath.hit(100, 0.75, 0.40, CAP, 0, 300, 0.70);
        assertEquals(15.0, uncappedToBar, EPS);
        assertEquals(0.85, h.uncappedReduction(), EPS);
        assertEquals(0.75, h.reduction(), EPS);
        assertEquals(25.0, h.afterReductions(), EPS);                // after: 25 gets through (75% removed)
        assertEquals(25.0, h.toHealth(), EPS);                       // empty bar: all of it to health
    }

    @Test
    void theBarIsAPoolAfterTheReductions_notPartOfTheCap() {
        // Strongest reductions today (70%) and a full 300-point bar.
        DefenseMath.Hit h = DefenseMath.hit(100, 0.50, 0.40, CAP, 300, 300, 0.70);
        assertEquals(30.0, h.afterReductions(), EPS);                // reductions: 70% (under the cap)
        assertEquals(21.0, h.absorbed(), EPS);                       // the bar takes 70% of what is left
        assertEquals(9.0, h.toHealth(), EPS);
        assertEquals(279.0, h.barAfter(), EPS);                      // ... and pays for it from its points
        // Only 9% reached health, but that is not a 91% reduction: the bar drained, and repeated
        // hits take less from it as it empties.
        DefenseMath.Hit second = DefenseMath.hit(100, 0.50, 0.40, CAP, h.barAfter(), 300, 0.70);
        assertEquals(30.0 * 0.70 * 279.0 / 300.0, second.absorbed(), EPS);
        assertTrue(second.toHealth() > h.toHealth());
    }

    @Test
    void resolveGoesResistanceThenArmorThenCapThenBarThenHealthShare() {
        // 100 fire damage, 50% resistance, 40% armor, a full 300-point bar, PvP scales x2 bar, x3 health.
        DefenseMath.Hit h = DefenseMath.resolve(100, 0.50, 0.40, CAP, 300, 300, 0.70, 2.0, 3.0);
        assertEquals(50.0, h.afterResist(), EPS);
        assertEquals(30.0, h.afterArmor(), EPS);
        assertEquals(30.0, h.afterReductions(), EPS);   // under the cap: the same
        assertEquals(0.70, h.barRate(), EPS);
        assertEquals(42.0, h.absorbed(), EPS);          // the bar takes 21 of the hit and loses 2 x 21 points
        assertEquals(9.0, h.afterBar(), EPS);           // what is left, in the hit's own terms
        assertEquals(27.0, h.toHealth(), EPS);          // health share applies after the bar, never before
        assertEquals(258.0, h.barAfter(), EPS);
        // Past the cap the step after armor raises the damage back to (1 - cap).
        DefenseMath.Hit c = DefenseMath.resolve(100, 0.75, 0.40, CAP, 0, 300, 0.70, 1.0, 1.0);
        assertEquals(15.0, c.afterArmor(), EPS);
        assertEquals(25.0, c.afterReductions(), EPS);
        // Broken input never produces negative or NaN damage.
        DefenseMath.Hit bad = DefenseMath.resolve(Double.NaN, 0.5, 0.4, CAP, 300, 300, 0.7, 1, 1);
        assertEquals(0.0, bad.toHealth(), EPS);
        assertEquals(300.0, bad.barAfter(), EPS);
    }

    @Test
    void theBarTakesItsShareByHowFullItIs() {
        assertEquals(0.70, DefenseMath.barRate(300, 300, 0.70), EPS);
        assertEquals(0.35, DefenseMath.barRate(150, 300, 0.70), EPS);
        assertEquals(0.0, DefenseMath.barRate(0, 300, 0.70), EPS);
        assertEquals(0.0, DefenseMath.barRate(10, 0, 0.70), EPS);
        DefenseMath.Hit empty = DefenseMath.hit(100, 0, 0.40, CAP, 0, 300, 0.70);
        assertEquals(60.0, empty.toHealth(), EPS);
    }

    @Test
    void pvpAsBefore_takesTheSameShareAsADefenderWithoutAttributes() {
        // Before attributes: 100 health and a 300-point bar. Now Vitality tripled health and
        // Fortitude doubled the bar; a PvP hit still takes the same share of each.
        DefenseMath.Hit plain = DefenseMath.pvpAsBefore(20, 0, 0.24, CAP, 300, 300, 300, 0.70, 100, 100);
        DefenseMath.Hit grown = DefenseMath.pvpAsBefore(20, 0, 0.24, CAP, 600, 600, 300, 0.70, 300, 100);
        assertEquals(plain.toHealth() / 100, grown.toHealth() / 300, EPS);
        assertEquals(plain.absorbed() / 300, grown.absorbed() / 600, EPS);
        assertEquals(plain.barAfter() / 300, grown.barAfter() / 600, EPS);
        // A defender without attributes gets exactly the normal hit.
        DefenseMath.Hit normal = DefenseMath.hit(20, 0, 0.24, CAP, 300, 300, 0.70);
        assertEquals(normal.toHealth(), plain.toHealth(), EPS);
        assertEquals(normal.absorbed(), plain.absorbed(), EPS);
        // A half-empty bar takes the same share it did before too.
        DefenseMath.Hit halfPlain = DefenseMath.pvpAsBefore(20, 0, 0.24, CAP, 150, 300, 300, 0.70, 100, 100);
        DefenseMath.Hit halfGrown = DefenseMath.pvpAsBefore(20, 0, 0.24, CAP, 300, 600, 300, 0.70, 300, 100);
        assertEquals(halfPlain.toHealth() / 100, halfGrown.toHealth() / 300, EPS);
    }

    @Test
    void pvpAsBefore_usesTheOldArmorAndResistance() {
        // Fortitude raised armor 24% -> 40% and fire resistance 30% -> 50%; PvP still sees 24% and 30%.
        DefenseMath.Hit h = DefenseMath.pvpAsBefore(100, 0.30, 0.24, CAP, 0, 300, 300, 0.70, 100, 100);
        assertEquals(100 * 0.70 * 0.76, h.toHealth(), EPS);
        assertEquals(1.0, DefenseMath.shareScale(300, 0), EPS);   // unknown old size: no scaling
        assertEquals(3.0, DefenseMath.shareScale(300, 100), EPS);
    }

    @Test
    void currentBehaviour_aHitBiggerThanTheBarHoldsIsStillTakenAtTheFullRate() {
        // Pinned on purpose (owner decision pending, docs/ATTRIBUTES_ENGINE.md): one hit of 1,000
        // after reductions on a full 300-point bar - the bar takes 700, more than its 300 points,
        // then sits at zero. Any hit above max / max-absorption (~429 here) gets this extra.
        DefenseMath.Hit h = DefenseMath.hit(1000, 0, 0, CAP, 300, 300, 0.70);
        assertEquals(700.0, h.absorbed(), EPS);
        assertEquals(300.0, h.toHealth(), EPS);
        assertEquals(0.0, h.barAfter(), EPS);
    }
}
