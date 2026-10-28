package Effects;

import Attributes.Attribute;
import Attributes.AttributeCalculator;
import Attributes.AttributeRules;
import Attributes.AttributeSnapshot;
import Attributes.Contribution;
import Attributes.Stat;
import Attributes.WeaponClass;
import Attributes.Yield;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class EffectBenefitsTest {

    private static final AttributeRules R = AttributeRules.DEFAULTS;

    private static EffectBenefits.Context ctx(boolean on, boolean pvp) {
        return new EffectBenefits.Context(a -> on, s -> on, pvp, 0.005, 0.5, 100, WeaponClass.SWORD);
    }

    /** The lines for {@code effect} on a character that also has {@code base}: the engine with and without it. */
    private static List<String> lines(List<Contribution> base, List<Contribution> effect, EffectBenefits.Context ctx) {
        List<Contribution> all = new ArrayList<>(base);
        all.addAll(effect);
        AttributeSnapshot with = AttributeCalculator.compute(all, R), without = AttributeCalculator.compute(base, R);
        return EffectBenefits.lines(effect, with, without, ctx);
    }

    @Test
    void vitalityShowsMaxHealthBeforeAndAfter() {
        Contribution stew = Contribution.attribute("meal:hearty_stew", "Meal", Attribute.VITALITY, 6, null);
        List<String> l = lines(List.of(), List.of(stew), ctx(true, false));
        AttributeSnapshot with = AttributeCalculator.compute(List.of(stew), R), without = AttributeCalculator.compute(List.of(), R);
        String expected = "Vitality +6 · max health " + Manager.Numbers.hud(100 * without.maxHpMultiplier()) + " → "
                + Manager.Numbers.hud(100 * with.maxHpMultiplier());
        assertEquals(List.of(expected), l);
        assertTrue(with.maxHpMultiplier() > without.maxHpMultiplier(), "Vitality raises max health");
    }

    @Test
    void aSpeedStatShowsItsRawAmountAndWhatCountsAfterTheCap() {
        Contribution ration = Contribution.stat("meal:trail_ration", "Meal", Stat.MOVE_SPEED, 0.08, null);
        String line = lines(List.of(), List.of(ration), ctx(true, false)).get(0);
        assertTrue(line.startsWith("Movement speed +8% raw · +"), line);
        AttributeSnapshot with = AttributeCalculator.compute(List.of(ration), R);
        assertTrue(with.secondary(Stat.MOVE_SPEED) < 0.08, "diminishing returns: less than the raw 8% counts");
        assertTrue(line.endsWith(GUIs.menu.Fmt.signedPct(with.secondary(Stat.MOVE_SPEED)) + " now"), line);
    }

    @Test
    void pausedAndSwitchedOffAreSaid() {
        Contribution ration = Contribution.stat("meal:trail_ration", "Meal", Stat.MOVE_SPEED, 0.08, null);
        assertTrue(lines(List.of(), List.of(ration), ctx(true, true)).get(0).endsWith("(paused: PvP)"));
        assertEquals("Movement speed +8% · switched off on this server", lines(List.of(), List.of(ration), ctx(false, false)).get(0));
        Contribution stew = Contribution.attribute("meal:hearty_stew", "Meal", Attribute.VITALITY, 6, null);
        assertEquals("Vitality +6 · switched off on this server", lines(List.of(), List.of(stew), ctx(false, false)).get(0));
    }

    @Test
    void fortuneOnCropsShowsTheExtraCropChance() {
        Contribution breakfast = Contribution.fortune("meal:farmers_breakfast", "Meal", 10, Yield.CROP);
        Contribution farming = Contribution.fortune("skill:Farming", "Farming", 2, Yield.CROP);
        String line = lines(List.of(farming), List.of(breakfast), ctx(true, false)).get(0);
        assertEquals("Fortune (crops) +10 · extra crop chance 1.0% → 6.0%", line);
    }

    @Test
    void anAbilitysDamageBuffShowsTheWeaponDamageBonus() {
        Contribution warCry = Contribution.stat("class:war_cry", "War Cry", Stat.DAMAGE, 0.15, null);
        String line = lines(List.of(), List.of(warCry), ctx(true, false)).get(0);
        assertEquals("Damage +15% · weapon damage bonus +0.0% → +15.0%", line);
    }

    @Test
    void numbersReadWithoutTrailingZeros() {
        assertEquals("+6", EffectBenefits.signed(6));
        assertEquals("+7.2", EffectBenefits.signed(7.2));
        assertEquals("-3", EffectBenefits.signed(-3));
        assertEquals("+8%", EffectBenefits.signedPctOf(0.08));
        assertEquals("+9.6%", EffectBenefits.signedPctOf(0.096));
    }
}
