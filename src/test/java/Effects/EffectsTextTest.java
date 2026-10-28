package Effects;

import org.bukkit.Material;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class EffectsTextTest {

    @Test
    void remainingTimesReadAsTheOwnerAskedFor() {
        assertEquals("8m 42s", EffectsText.remaining(8 * 60_000 + 42_000));
        assertEquals("8m 42s", EffectsText.remaining(8 * 60_000 + 42_999), "rounded down: a second is shown until it has passed");
        assertEquals("42s", EffectsText.remaining(42_000));
        assertEquals("1h 05m", EffectsText.remaining(65 * 60_000));
        assertEquals("0s", EffectsText.remaining(0));
        assertEquals("0s", EffectsText.remaining(-5));
        assertEquals("no time limit", EffectsText.remaining(ActiveEffect.NO_END));
    }

    @Test
    void summaryLineIsNameDashTimeRemaining() {
        ActiveEffect stew = new ActiveEffect("meal:hearty_stew:common", ActiveEffect.Category.MEAL, "Hearty Stew", "Meal · Common · Survival",
                Material.RABBIT_STEW, 1_000 + 8 * 60_000 + 42_000, null, List.of(), List.of(), null);
        assertEquals("Hearty Stew — 8m 42s remaining", EffectsText.summary(stew, 1_000));
        ActiveEffect forever = new ActiveEffect("potion:speed", ActiveEffect.Category.POTION, "Speed", "Potion effect (vanilla)",
                Material.POTION, ActiveEffect.NO_END, "until removed", List.of(), List.of(), null);
        assertEquals("Speed — until removed", EffectsText.summary(forever, 1_000));
    }

    @Test
    void healthEffectsSayTheyDoNothingUnderCerberus() {
        for (String k : List.of("regeneration", "instant_health", "absorption", "health_boost", "resistance")) {
            assertTrue(EffectsText.vanilla(k, 1).get(0).startsWith("No effect"), k + ": " + EffectsText.vanilla(k, 1));
        }
    }

    @Test
    void levelledVanillaEffectsScaleWithTheirLevel() {
        assertEquals("Walking speed +20%.", EffectsText.vanilla("speed", 1).get(0));
        assertEquals("Walking speed +40%.", EffectsText.vanilla("speed", 2).get(0));
        assertTrue(EffectsText.vanilla("strength", 2).get(0).contains("+6 base damage"));
        assertTrue(EffectsText.vanilla("weakness", 1).get(0).contains("-4 base damage"));
        assertEquals("Walking speed -100%.", EffectsText.vanilla("slowness", 7).get(0), "never past standing still");
        assertEquals(List.of("Vanilla effect."), EffectsText.vanilla("some_future_effect", 1));
    }

    @Test
    void namesAndLevels() {
        assertEquals("Fire Resistance", EffectsText.vanillaName("fire_resistance"));
        assertEquals("Dolphin's Grace", EffectsText.vanillaName("dolphins_grace"));
        assertEquals("Bad Luck", EffectsText.vanillaName("unluck"));
        assertEquals("II", EffectsText.roman(2));
        assertEquals("X", EffectsText.roman(10));
        assertEquals("11", EffectsText.roman(11));
    }
}
