package Effects;

import org.bukkit.Material;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.logging.Handler;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;

class ActiveEffectsTest {

    private static ActiveEffect fx(String key, ActiveEffect.Category c, long endsAt) {
        return new ActiveEffect(key, c, key, "test", Material.POTION, endsAt, endsAt == ActiveEffect.NO_END ? "until removed" : null,
                List.of(), List.of(), null);
    }

    @Test
    void mealFirstThenByKindThenSoonestToEnd() {
        long now = 1_000;
        List<ActiveEffect> in = List.of(
                fx("potion:speed", ActiveEffect.Category.POTION, now + 5_000),
                fx("buff:a", ActiveEffect.Category.BUFF, now + 90_000),
                fx("class:war_cry", ActiveEffect.Category.ABILITY, now + 8_000),
                fx("potion:luck", ActiveEffect.Category.POTION, ActiveEffect.NO_END),
                fx("meal:hearty_stew:common", ActiveEffect.Category.MEAL, now + 600_000),
                fx("potion:haste", ActiveEffect.Category.POTION, now + 1_000));
        assertEquals(List.of("meal:hearty_stew:common", "class:war_cry", "buff:a", "potion:haste", "potion:speed", "potion:luck"),
                ActiveEffects.keys(ActiveEffects.order(in, now)));
    }

    @Test
    void endedEffectsNeverShow() {
        long now = 10_000;
        List<ActiveEffect> in = List.of(fx("a", ActiveEffect.Category.BUFF, now), fx("b", ActiveEffect.Category.BUFF, now - 1),
                fx("c", ActiveEffect.Category.BUFF, now + 1));
        assertEquals(List.of("c"), ActiveEffects.keys(ActiveEffects.order(in, now)), "an effect ending at this very moment is over");
    }

    @Test
    void alwaysOnIsKeptApartFromTemporaryOnes() {
        List<ActiveEffect> all = ActiveEffects.order(List.of(fx("always:battle_hardened", ActiveEffect.Category.ALWAYS_ON, ActiveEffect.NO_END),
                fx("meal:x:common", ActiveEffect.Category.MEAL, 50_000)), 0);
        assertEquals(List.of("meal:x:common"), ActiveEffects.keys(ActiveEffects.temporary(all)));
        assertEquals(List.of("always:battle_hardened"), ActiveEffects.keys(ActiveEffects.alwaysOn(all)));
        assertFalse(ActiveEffects.anyTimed(ActiveEffects.alwaysOn(all)));
        assertTrue(ActiveEffects.anyTimed(ActiveEffects.temporary(all)));
    }

    @Test
    void aBrokenSourceIsSkippedAndLoggedOnce() {
        Logger log = Logger.getAnonymousLogger();
        log.setUseParentHandlers(false);
        int[] warnings = {0};
        log.addHandler(new Handler() {
            @Override public void publish(LogRecord r) { warnings[0]++; }
            @Override public void flush() {}
            @Override public void close() {}
        });
        ActiveEffects reg = new ActiveEffects(log);
        reg.register("broken", (p, now, out) -> { throw new IllegalStateException("boom"); });
        reg.register("fine", (p, now, out) -> out.add(fx("buff:ok", ActiveEffect.Category.BUFF, now + 1_000)));
        assertEquals(List.of("buff:ok"), ActiveEffects.keys(reg.of(null, 0)));
        assertEquals(List.of("buff:ok"), ActiveEffects.keys(reg.of(null, 0)));
        assertEquals(1, warnings[0], "the same broken source is reported once, not every second");
    }

    @Test
    void remainingAndPhases() {
        ActiveEffect.Phase running = new ActiveEffect.Phase("Restoration", "25% over 20 s", 5_000, false);
        assertTrue(running.running(4_999));
        assertFalse(running.running(5_000));
        assertFalse(new ActiveEffect.Phase("Restoration", "done", 0, false).running(1));
        ActiveEffect e = fx("m", ActiveEffect.Category.MEAL, 10_000);
        assertEquals(4_000, e.remaining(6_000));
        assertEquals(0, e.remaining(12_000));
        assertTrue(e.active(9_999));
        assertFalse(e.active(10_000));
    }
}
