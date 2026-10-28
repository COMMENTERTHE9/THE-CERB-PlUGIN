package Effects;

import Attributes.Attribute;
import Attributes.AttributeCalculator;
import Attributes.AttributeRules;
import Attributes.AttributeSnapshot;
import Attributes.Contribution;
import Attributes.Stat;
import Attributes.WeaponClass;
import Attributes.Yield;
import GUIs.menu.Fmt;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class EffectBenefitsTest {

    private static final AttributeRules R = AttributeRules.DEFAULTS;

    private static EffectBenefits.Context ctx(boolean on, boolean pvp) {
        return new EffectBenefits.Context(a -> on, s -> on, pvp, 0.005, 0.5, 100, WeaponClass.SWORD, WeaponClass.SWORD, null);
    }

    /** The lines for {@code effect} on a character that also has {@code base}: the engine with and without it. */
    private static EffectBenefits.Lines lines(List<Contribution> base, List<Contribution> effect, EffectBenefits.Context ctx) {
        List<Contribution> all = new ArrayList<>(base);
        all.addAll(effect);
        AttributeSnapshot with = AttributeCalculator.compute(all, R), without = AttributeCalculator.compute(base, R);
        return EffectBenefits.lines(effect, with, without, ctx);
    }

    @Test
    void vitalityShowsMaxHealthBeforeAndAfter() {
        Contribution stew = Contribution.attribute("meal:hearty_stew", "Meal", Attribute.VITALITY, 6, null);
        EffectBenefits.Lines l = lines(List.of(), List.of(stew), ctx(true, false));
        AttributeSnapshot with = AttributeCalculator.compute(List.of(stew), R), without = AttributeCalculator.compute(List.of(), R);
        assertEquals(List.of("Vitality +6"), l.added());
        String health = "max health " + Manager.Numbers.hud(100 * without.maxHpMultiplier()) + " → "
                + Manager.Numbers.hud(100 * with.maxHpMultiplier());
        assertTrue(l.changes().contains(health), l.changes().toString());
        assertTrue(with.maxHpMultiplier() > without.maxHpMultiplier(), "Vitality raises max health");
    }

    @Test
    void everyNumberAnAttributeMovesIsListed() {
        // Vitality moves more than max health (its other effects come from the same engine): each one that moves is said.
        Contribution stew = Contribution.attribute("meal:hearty_stew", "Meal", Attribute.VITALITY, 6, null);
        AttributeSnapshot with = AttributeCalculator.compute(List.of(stew), R), without = AttributeCalculator.compute(List.of(), R);
        List<String> changes = lines(List.of(), List.of(stew), ctx(true, false)).changes();
        if (Math.abs(with.resist() - without.resist()) > 1e-9) {
            assertTrue(changes.stream().anyMatch(c -> c.startsWith("fire, cold and drowning resistance ")), changes.toString());
        }
        for (Stat s : Stat.SECONDARY) {
            double a = s == Stat.ATTACK_SPEED ? without.attackSpeed(WeaponClass.SWORD) : without.secondary(s);
            double b = s == Stat.ATTACK_SPEED ? with.attackSpeed(WeaponClass.SWORD) : with.secondary(s);
            if (Math.abs(a - b) > 1e-9) {
                String name = s == Stat.ATTACK_SPEED ? "attack speed (swords)" : s.displayName.toLowerCase(java.util.Locale.ROOT);
                assertTrue(changes.stream().anyMatch(c -> c.startsWith(name + " ")), name + " missing: " + changes);
            }
        }
    }

    @Test
    void aSpeedStatShowsItsRawAmountAndTheTotalAfterTheCurve() {
        Contribution ration = Contribution.stat("meal:trail_ration", "Meal", Stat.MOVE_SPEED, 0.08, null);
        EffectBenefits.Lines l = lines(List.of(), List.of(ration), ctx(true, false));
        assertEquals(List.of("Movement speed +8%"), l.added());
        AttributeSnapshot with = AttributeCalculator.compute(List.of(ration), R);
        assertTrue(with.secondary(Stat.MOVE_SPEED) < 0.08, "diminishing returns: less than the raw 8% counts");
        assertEquals(List.of("movement speed +0.0% → " + Fmt.signedPct(with.secondary(Stat.MOVE_SPEED))), l.changes());
    }

    @Test
    void pausedAndSwitchedOffAreSaid() {
        Contribution ration = Contribution.stat("meal:trail_ration", "Meal", Stat.MOVE_SPEED, 0.08, null);
        EffectBenefits.Lines paused = lines(List.of(), List.of(ration), ctx(true, true));
        assertTrue(paused.changes().get(0).endsWith("(paused: PvP)"), paused.changes().toString());
        EffectBenefits.Lines off = lines(List.of(), List.of(ration), ctx(false, false));
        assertEquals(List.of("Movement speed +8% · switched off on this server"), off.added());
        assertTrue(off.changes().get(0).endsWith("(switched off)"), off.changes().toString());
        Contribution stew = Contribution.attribute("meal:hearty_stew", "Meal", Attribute.VITALITY, 6, null);
        EffectBenefits.Lines vit = lines(List.of(), List.of(stew), ctx(false, false));
        assertEquals(List.of("Vitality +6 · switched off on this server"), vit.added());
        assertFalse(vit.changes().stream().anyMatch(c -> c.startsWith("max health")), "an attribute switched off moves no health");
    }

    @Test
    void fortuneOnCropsShowsTheExtraCropChance() {
        Contribution breakfast = Contribution.fortune("meal:farmers_breakfast", "Meal", 10, Yield.CROP);
        Contribution farming = Contribution.fortune("skill:Farming", "Farming", 2, Yield.CROP);
        EffectBenefits.Lines l = lines(List.of(farming), List.of(breakfast), ctx(true, false));
        assertEquals(List.of("Fortune (crops) +10"), l.added());
        assertEquals(List.of("extra crop chance 1.0% → 6.0%"), l.changes());
    }

    @Test
    void anAbilitysDamageBuffShowsTheWeaponDamage() {
        Contribution warCry = Contribution.stat("class:war_cry", "War Cry", Stat.DAMAGE, 0.15, null);
        EffectBenefits.Lines l = lines(List.of(), List.of(warCry), ctx(true, false));
        assertEquals(List.of("Damage +15%"), l.added());
        assertTrue(l.changes().contains("sword damage x1.00 → x1.15"), l.changes().toString());
    }

    @Test
    void armorGoesThroughTheGameRule() {
        // The menu passes the real rule (gear and caps): the line shows what the player gets, not the raw bonus.
        Contribution fort = Contribution.attribute("buff:test", "Test", Attribute.FORTITUDE, 40, null);
        AttributeSnapshot with = AttributeCalculator.compute(List.of(fort), R), without = AttributeCalculator.compute(List.of(), R);
        assertTrue(with.armorBonus() > without.armorBonus(), "Fortitude adds armor");
        EffectBenefits.Context capped = new EffectBenefits.Context(a -> true, s -> true, false, 0.005, 0.5, 100,
                WeaponClass.SWORD, WeaponClass.SWORD, bonus -> Math.min(0.40, 0.38 + bonus));
        List<String> changes = EffectBenefits.lines(List.of(fort), with, without, capped).changes();
        assertTrue(changes.contains("armor 38.0% → 40.0% (gear and caps included)"), changes.toString());
        // At the cap already, the armor line goes away: nothing moves.
        EffectBenefits.Context full = new EffectBenefits.Context(a -> true, s -> true, false, 0.005, 0.5, 100,
                WeaponClass.SWORD, WeaponClass.SWORD, bonus -> 0.40);
        assertFalse(EffectBenefits.lines(List.of(fort), with, without, full).changes().stream().anyMatch(c -> c.startsWith("armor ")));
    }

    @Test
    void noContributionsNoLines() {
        assertTrue(lines(List.of(), List.of(), ctx(true, false)).isEmpty());
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
