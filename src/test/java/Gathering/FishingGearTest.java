package Gathering;

import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/** One player's fishing equipment (docs/design/FISHING_EQUIPMENT.md §5, §8): parts owned once, presets, the Bait Bag. */
class FishingGearTest {

    @Test
    void aNewPlayerHasThreeNamedPresetsAndNothingElse() {
        FishingGear g = new FishingGear();
        assertEquals("Discoveries", g.preset(0).name());
        assertEquals("Treasure", g.preset(1).name());
        assertEquals("Sea Creatures", g.preset(2).name());
        assertEquals(0, g.activeIndex());
        assertTrue(g.owned().isEmpty());
        assertTrue(g.autoReel(), "the reel works once fitted");
        assertFalse(g.restock(), "restocking is opt-in");
        for (int i = 0; i < FishingGear.SLOTS; i++) assertTrue(g.slot(i).empty());
        assertNull(g.held());
    }

    @Test
    void aPartIsOwnedOnceAndPresetsOnlyNameOwnedParts() {
        FishingGear g = new FishingGear();
        assertFalse(g.install(0, "scaled_grip"), "not owned: a preset can't make it");
        assertTrue(g.own("scaled_grip"));
        assertFalse(g.own("scaled_grip"), "owned once");
        assertTrue(g.install(0, "scaled_grip"));
        assertTrue(g.install(1, "scaled_grip"), "two presets name the same part: still one part");
        assertFalse(g.install(1, "scaled_grip"));
        assertEquals(Set.of("scaled_grip"), g.owned());
        assertTrue(g.uninstall(1, "scaled_grip"));
        assertTrue(g.disown("scaled_grip"));
        assertFalse(g.preset(0).parts().contains("scaled_grip"), "a part taken away leaves every preset");
        assertFalse(g.own("Bad|Id"));
    }

    @Test
    void presetNamesAreChecked() {
        FishingGear g = new FishingGear();
        assertTrue(g.rename(0, "  Night Hunt  "));
        assertEquals("Night Hunt", g.preset(0).name());
        assertFalse(g.rename(0, "§cRed"), "no colour codes");
        assertFalse(g.rename(0, ""));
        assertFalse(g.rename(0, "x".repeat(21)));
        assertFalse(g.rename(0, "a|b"), "never the save's separators");
        assertFalse(g.rename(3, "Fine"));
        assertEquals("Night Hunt", g.preset(0).name());
    }

    @Test
    void theBagHoldsOneBaitPerSlotUpToAStack() {
        FishingGear g = new FishingGear();
        assertEquals(64, g.load(0, "grub_bait", 100, 64), "up to a full slot");
        assertEquals(0, g.load(0, "grub_bait", 5, 64), "full");
        assertEquals(0, g.load(0, "chum", 5, 64), "a slot holds one bait");
        assertEquals(10, g.load(1, "chum", 10, 64));
        assertEquals(4, g.take(1, 4));
        assertEquals(new FishingGear.Slot("chum", 6), g.slot(1));
        assertEquals(6, g.take(1, 100));
        assertTrue(g.slot(1).empty());
        g.load(2, "chum", 3, 64);
        g.swap(0, 2);
        assertEquals("chum", g.slot(0).bait());
        assertEquals("grub_bait", g.slot(2).bait());
    }

    @Test
    void heldBaitEndsExactlyOnce() {
        FishingGear g = new FishingGear();
        g.load(0, "grub_bait", 3, 64);
        UUID hook = UUID.randomUUID(), other = UUID.randomUUID();
        assertTrue(g.hold(0, hook));
        assertEquals(2, g.slot(0).count(), "it leaves the bag at the cast");
        assertFalse(g.hold(0, other), "one hold at a time");
        assertFalse(g.consume(other), "another cast's catch uses nothing");
        assertNull(g.release(other));
        assertTrue(g.consume(hook), "the catch it applied to uses it");
        assertFalse(g.consume(hook), "...once");
        assertNull(g.release(hook), "a used bait never comes back");
        assertEquals(2, g.slot(0).count());

        assertTrue(g.hold(0, hook));
        assertEquals("", g.release(hook), "a cast without a catch puts it back");
        assertEquals(2, g.slot(0).count());
        assertNull(g.release(hook), "...once");
    }

    @Test
    void releasedBaitFindsItsWayBackOrIsHandedOver() {
        UUID h = UUID.randomUUID();
        // Its own slot still holds that bait: back there.
        FishingGear a = new FishingGear();
        a.load(0, "grub_bait", 3, 64);
        a.hold(0, h);
        assertEquals("", a.release(h));
        assertEquals(new FishingGear.Slot("grub_bait", 3), a.slot(0));
        // Its slot now holds another bait, another slot holds the same: there.
        FishingGear b = new FishingGear();
        b.load(0, "grub_bait", 1, 64);
        b.hold(0, h);
        b.load(0, "chum", 2, 64);
        b.load(2, "grub_bait", 5, 64);
        assertEquals("", b.release(h));
        assertEquals(new FishingGear.Slot("grub_bait", 6), b.slot(2));
        // Neither, but an empty slot: there.
        FishingGear c = new FishingGear();
        c.load(0, "grub_bait", 1, 64);
        c.hold(0, h);
        c.load(0, "chum", 2, 64);
        assertEquals("", c.release(h));
        assertEquals(new FishingGear.Slot("grub_bait", 1), c.slot(1));
        // No room anywhere: handed over (the caller gives it as an item).
        FishingGear d = new FishingGear();
        d.load(0, "grub_bait", 1, 64);
        d.hold(0, h);
        d.load(0, "chum", 5, 64);
        d.load(1, "chum", 5, 64);
        d.load(2, "chum", 5, 64);
        assertEquals("grub_bait", d.release(h));
        assertNull(d.held());
    }

    @Test
    void aReleasedBaitGoesOnlyIntoOpenSlots() {
        UUID h = UUID.randomUUID();
        FishingGear g = new FishingGear();
        g.load(0, "grub_bait", 1, 64);
        g.hold(0, h);
        g.load(0, "chum", 2, 64);
        assertEquals("grub_bait", g.release(h, 1), "slots 2 and 3 are locked at this level: handed over, never hidden there");
        assertTrue(g.slot(1).empty() && g.slot(2).empty());
        FishingGear k = new FishingGear();
        k.load(0, "grub_bait", 1, 64);
        k.hold(0, h);
        k.load(0, "chum", 2, 64);
        assertEquals("", k.release(h, 2), "slot 2 is open: there");
        assertEquals(new FishingGear.Slot("grub_bait", 1), k.slot(1));
    }

    @Test
    void aRefundMayGoOneOverTheStack() {
        FishingGear g = new FishingGear();
        g.load(0, "grub_bait", 64, 64);
        UUID hook = UUID.randomUUID();
        assertTrue(g.hold(0, hook));
        g.load(0, "grub_bait", 1, 64); // restocked meanwhile
        assertEquals("", g.release(hook));
        assertEquals(65, g.slot(0).count(), "a refund is never refused");
    }

    @Test
    void everythingSurvivesSavingAndReading() {
        FishingGear g = new FishingGear();
        g.own("scaled_grip");
        g.own("resin_line");
        g.install(0, "scaled_grip");
        g.install(2, "resin_line");
        g.rename(1, "Coins & Pearls");
        g.useSlot(2, 1, false);
        g.activate(2);
        g.load(0, "grub_bait", 12, 64);
        g.load(2, "chum", 3, 64);
        UUID hook = UUID.randomUUID();
        g.hold(2, hook);
        g.autoReel(false);
        g.restock(true);
        String text = g.encode() + "|zz=a later version's part";
        FishingGear back = FishingGear.decode(text);
        assertEquals(text, back.encode(), "the same text, the unknown part kept");
        assertEquals(Set.of("scaled_grip", "resin_line"), back.owned());
        assertEquals(2, back.activeIndex());
        assertEquals("Coins & Pearls", back.preset(1).name());
        assertFalse(back.preset(2).usesSlot(1));
        assertEquals(new FishingGear.Slot("grub_bait", 12), back.slot(0));
        assertEquals(new FishingGear.Slot("chum", 2), back.slot(2));
        assertEquals(new FishingGear.Held("chum", 2, hook), back.held());
        assertFalse(back.autoReel());
        assertTrue(back.restock());
    }

    @Test
    void anEditedSaveNeverMakesAPartAppear() {
        FishingGear g = FishingGear.decode("1|o=scaled_grip|a=0|p0=Discoveries;scaled_grip,monarchs_blessing;111|p1=Treasure;;111|p2=Sea Creatures;;111|b=,,|s=r");
        assertEquals(Set.of("scaled_grip"), g.preset(0).parts(), "a preset naming a part the player doesn't own loses it");
    }

    @Test
    void anUnreadableVersionIsRefusedNotGuessed() {
        assertThrows(IllegalArgumentException.class, () -> FishingGear.decode("2|o=something new"));
        assertNotNull(FishingGear.decode(null));
        assertNotNull(FishingGear.decode(""));
        FishingGear g = FishingGear.decode("1|o=ok,Bad Id|a=9|b=x:abc,y:-2,grub_bait:4|h=bad");
        assertEquals(Set.of("ok"), g.owned());
        assertEquals(2, g.activeIndex(), "an out-of-range preset is clamped");
        assertEquals(new FishingGear.Slot("grub_bait", 4), g.slot(2));
        assertNull(g.held());
    }
}
