package Classes;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static Classes.Controls.Click.LEFT;
import static Classes.Controls.Click.RIGHT;
import static org.junit.jupiter.api.Assertions.*;

/** The ability keys' pure rules (docs/design/ABILITY_CONTROLS.md): schemes, combos, labels, settings, feedback text. */
class ControlsTest {

    @Test
    void everyComboMeansOneSlotAndABowMirrorsTheButtons() {
        assertEquals(0, Controls.comboSlot(List.of(RIGHT, RIGHT, RIGHT), false), "R R R (hold right-click) = active 1");
        assertEquals(1, Controls.comboSlot(List.of(RIGHT, LEFT, RIGHT), false));
        assertEquals(2, Controls.comboSlot(List.of(RIGHT, LEFT, LEFT), false));
        assertEquals(3, Controls.comboSlot(List.of(RIGHT, RIGHT, LEFT), false));
        // A bow shoots with right-click, so its combos start with left: same shapes, mirrored.
        assertEquals(0, Controls.comboSlot(List.of(LEFT, LEFT, LEFT), true));
        assertEquals(1, Controls.comboSlot(List.of(LEFT, RIGHT, LEFT), true));
        assertEquals(2, Controls.comboSlot(List.of(LEFT, RIGHT, RIGHT), true));
        assertEquals(3, Controls.comboSlot(List.of(LEFT, LEFT, RIGHT), true));
        // Not a combo: the wrong start, too short, too long.
        assertEquals(-1, Controls.comboSlot(List.of(LEFT, RIGHT, RIGHT), false));
        assertEquals(-1, Controls.comboSlot(List.of(RIGHT, RIGHT), false));
        assertEquals(-1, Controls.comboSlot(List.of(RIGHT, RIGHT, RIGHT, RIGHT), false));
        // The four combos and F reach five different slots, and the printed text matches each.
        Set<Integer> slots = new HashSet<>();
        for (Controls.Click b : Controls.Click.values()) for (Controls.Click c : Controls.Click.values()) {
            int s = Controls.comboSlot(List.of(RIGHT, b, c), false);
            assertTrue(slots.add(s), "two combos on one slot");
            assertEquals(Controls.comboText(s, false), "R " + letter(b) + " " + letter(c));
        }
        slots.add(Controls.COMBO_F);
        assertEquals(Set.of(0, 1, 2, 3, 4), slots);
    }

    private static String letter(Controls.Click c) { return c == RIGHT ? "R" : "L"; }

    @Test
    void quickKeysCoverFiveSlotsAndSneakOnlyTheLastTwo() {
        Set<Integer> slots = new HashSet<>(List.of(Controls.quickPrimary(false), Controls.quickF(false), Controls.QUICK_Q,
                Controls.quickPrimary(true), Controls.quickF(true)));
        assertEquals(Set.of(0, 1, 2, 3, 4), slots);
        assertEquals(0, Controls.quickPrimary(false));
        assertEquals(1, Controls.quickF(false));
        assertEquals(2, Controls.QUICK_Q);
        assertEquals(3, Controls.quickPrimary(true));
        assertEquals(4, Controls.quickF(true));
    }

    @Test
    void labelsNameTheKeysAndFollowTheBowSwap() {
        List<String> quick = new ArrayList<>(), combo = new ArrayList<>();
        for (int s = 0; s < Loadout.ACTIVE_SLOTS; s++) {
            quick.add(Controls.keyLabel(Controls.Scheme.QUICK, s, false));
            combo.add(Controls.keyLabel(Controls.Scheme.COMBO, s, false));
        }
        assertEquals(List.of("Right-click", "F", "Q", "Sneak + right-click", "Sneak + F"), quick);
        assertEquals(List.of("R R R", "R L R", "R L L", "R R L", "F"), combo);
        assertEquals("Left-click", Controls.keyLabel(Controls.Scheme.QUICK, 0, true));
        assertEquals("Sneak + left-click", Controls.keyLabel(Controls.Scheme.QUICK, 3, true));
        assertEquals("L R L", Controls.keyLabel(Controls.Scheme.COMBO, 1, true));
        assertEquals("⇧R", Controls.keyShort(Controls.Scheme.QUICK, 3, false));
        assertEquals("RLL", Controls.keyShort(Controls.Scheme.COMBO, 2, false));
    }

    @Test
    void settingsHaveSafeDefaultsAndBounds() {
        assertEquals(Controls.Settings.DEFAULTS, Controls.Settings.from(null));
        YamlConfiguration y = new YamlConfiguration();
        y.set("default-scheme", "combo");
        y.set("combo-gap-ms", 5);           // too small: clamped
        y.set("ability-bar", "off");
        y.set("ability-bar-linger-ms", 999_999);
        Controls.Settings s = Controls.Settings.from(y);
        assertEquals(Controls.Scheme.COMBO, s.defaultScheme());
        assertEquals(250, s.comboGapMs());
        assertEquals(Controls.BarMode.OFF, s.defaultBar());
        assertEquals(30_000, s.barLingerMs());
        y.set("default-scheme", "telepathy"); // unknown: the default, never an error
        y.set("ability-bar", "sometimes");
        assertEquals(Controls.Scheme.QUICK, Controls.Settings.from(y).defaultScheme());
        assertEquals(Controls.BarMode.AUTO, Controls.Settings.from(y).defaultBar());
        assertEquals(Controls.Scheme.QUICK, Controls.Scheme.parse("Quick-Keys"));
        assertEquals(Controls.Scheme.COMBO, Controls.Scheme.parse(" combos "));
        assertNull(Controls.Scheme.parse(null));
    }

    @Test
    void theShippedClassesFileSetsQuickKeysAndTheAutoBar() throws Exception {
        YamlConfiguration y = new YamlConfiguration();
        y.loadFromString(java.nio.file.Files.readString(new File("src/main/resources/classes.yml").toPath()).replace("\r\n", "\n"));
        Controls.Settings s = Controls.Settings.from(y.getConfigurationSection("controls"));
        assertEquals(Controls.Scheme.QUICK, s.defaultScheme());
        assertEquals(700, s.comboGapMs());
        assertEquals(Controls.BarMode.AUTO, s.defaultBar());
    }

    @Test
    void everyOutcomeSaysWhatHappenedOrStaysSilent() {
        AbilityDef bolt = new AbilityDef("arcane_bolt", "mage", "Arcane Bolt", "", AbilityDef.Kind.ACTIVE, 1,
                Set.of(WeaponFamily.FOCUS), 15, 1500, Set.of(), Set.of(), List.of(), List.of());
        assertEquals("§6✦ Arcane Bolt", CastFeedback.text(CastResult.cast(bolt, ""), "Right-click"));
        assertEquals("§6✦ Arcane Bolt §7· §f3 hit", CastFeedback.text(CastResult.cast(bolt, "3 hit"), "Right-click"));
        assertEquals("§7Arcane Bolt §f1.2 s", CastFeedback.text(CastResult.cooldown(bolt, 1_200), "Right-click"));
        assertEquals("§bArcane Bolt §cneeds 15 Essence", CastFeedback.text(CastResult.refuse(CastResult.Outcome.NO_ESSENCE, bolt, "15"), "x"));
        assertEquals("§cArcane Bolt needs a focus", CastFeedback.text(CastResult.refuse(CastResult.Outcome.WRONG_ITEM, bolt, "a focus"), "x"));
        assertEquals("§cArcane Bolt: no target within 8 blocks", CastFeedback.text(CastResult.refuse(CastResult.Outcome.NO_TARGET, bolt, "8 blocks"), "x"));
        assertEquals("§cArcane Bolt unlocks at Mage rank 3", CastFeedback.text(CastResult.refuse(CastResult.Outcome.NOT_UNLOCKED, bolt, "Mage rank 3"), "x"));
        assertTrue(CastFeedback.text(CastResult.refuse(CastResult.Outcome.EMPTY, null, ""), "Sneak + F").startsWith("§7Nothing on Sneak + F"));
        assertNull(CastFeedback.text(CastResult.silent(), "F"), "dead or in a menu: nothing shown");
        for (CastResult.Outcome o : CastResult.Outcome.values()) {
            if (o == CastResult.Outcome.CANNOT_ACT) continue;
            assertNotNull(CastFeedback.text(new CastResult(o, bolt, "x", 1000), "F"), o + " shows nothing");
        }
    }

    @Test
    void castMessagesFormatNumbersShortly() {
        assertEquals("8 blocks", AbilityCaster.rangeText(8));
        assertEquals("3.5 blocks", AbilityCaster.rangeText(3.5));
        assertEquals("2.2", AbilityCaster.oneDecimal(2.2));
        assertEquals("3", AbilityCaster.oneDecimal(3.0));
        assertEquals("+15% damage · 8 s", AbilityCaster.buffText(new EffectSpec.Buff(null, Attributes.Stat.DAMAGE, 0.15, 8)));
    }

    @Test
    void thePlayersChoiceIsSavedAndAnUnknownOneFallsBack() throws Exception {
        ClassCatalog cat = ClassCatalog.from(load("src/main/resources/classes.yml"), load("src/main/resources/abilities.yml"));
        ClassProfile p = new ClassProfile();
        p.learn("mage");
        p.setControls(Controls.Scheme.COMBO);
        p.setBar(Controls.BarMode.OFF);
        List<String> repairs = new ArrayList<>();
        ClassProfile back = ClassProfile.fromYaml(p.toYaml(1_000), cat, 1_000, repairs);
        assertEquals(Controls.Scheme.COMBO, back.controls());
        assertEquals(Controls.BarMode.OFF, back.bar());
        assertTrue(repairs.isEmpty(), repairs.toString());
        // No choice made: nothing is written, and the server default applies.
        String plain = new ClassProfile().toYaml(1_000);
        assertFalse(plain.contains("controls") || plain.contains("bar:"), plain);
        assertNull(ClassProfile.fromYaml(plain, cat, 1_000, repairs).controls());
        // A scheme this build doesn't know (written by a newer one) is the default, and progress still loads.
        ClassProfile odd = ClassProfile.fromYaml("version: 1\ncontrols: telepathy\nclasses:\n  mage:\n    rank: 4\n    xp: 0\n", cat, 1_000, repairs);
        assertNull(odd.controls());
        assertEquals(4, odd.rank("mage"));
    }

    private static YamlConfiguration load(String path) throws Exception {
        YamlConfiguration y = new YamlConfiguration();
        y.loadFromString(java.nio.file.Files.readString(new File(path).toPath()).replace("\r\n", "\n"));
        return y;
    }
}
