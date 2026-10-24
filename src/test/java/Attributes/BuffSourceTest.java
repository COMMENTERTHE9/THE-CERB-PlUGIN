package Attributes;

import org.junit.jupiter.api.Test;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;

class BuffSourceTest {

    private final AtomicLong now = new AtomicLong(1_000);
    private final BuffSource buffs = new BuffSource(now::get);
    private final UUID p = UUID.randomUUID();

    @Test
    void buffCountsUntilItExpires() {
        buffs.add(p, new BuffSource.Buff("a", "Dev buff", Attribute.POWER, 50, 2_000));
        AttributeSnapshot s = AttributeCalculator.compute(buffs.contributions(p, AttributeRules.DEFAULTS), AttributeRules.DEFAULTS);
        assertEquals(50, s.power(WeaponClass.SWORD), "an all-weapon buff counts for every weapon");
        now.set(2_000);
        assertTrue(buffs.contributions(p, AttributeRules.DEFAULTS).isEmpty(), "gone exactly at expiry");
        assertTrue(buffs.active(p).isEmpty());
    }

    @Test
    void clearRemovesEverythingAndCountsOnlyActive() {
        buffs.add(p, new BuffSource.Buff("a", "A", Attribute.VITALITY, 10, 5_000));
        buffs.add(p, new BuffSource.Buff("b", "B", Attribute.VITALITY, 10, 1_500));
        now.set(2_000);
        assertEquals(1, buffs.clear(p));
        assertTrue(buffs.active(p).isEmpty());
        assertEquals(0, buffs.clear(p), "clearing twice is harmless");
    }

    @Test
    void activeListsSoonestFirst() {
        buffs.add(p, new BuffSource.Buff("late", "Late", Attribute.ESSENCE, 1, 9_000));
        buffs.add(p, new BuffSource.Buff("soon", "Soon", Attribute.ESSENCE, 1, 3_000));
        assertEquals("soon", buffs.active(p).get(0).id());
    }

    @Test
    void contributionsAreLabelledAndPrefixed() {
        buffs.add(p, new BuffSource.Buff("x", "Dev: +25 Agility", Attribute.AGILITY, 25, 9_000));
        Contribution c = buffs.contributions(p, AttributeRules.DEFAULTS).get(0);
        assertEquals("buff:x", c.sourceId());
        assertEquals("Dev: +25 Agility", c.label());
    }
}
