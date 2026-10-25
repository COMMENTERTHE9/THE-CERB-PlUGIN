package Classes;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/** The ability engine's pure core (docs/design/CLASSES.md): catalog and its bounds, ranks, loadout rules, cooldowns. */
class ClassCoreTest {

    private static final String SHIPPED_CLASSES = read("src/main/resources/classes.yml");
    private static final String SHIPPED_ABILITIES = read("src/main/resources/abilities.yml");

    /** The file with "\n" line ends, whatever the checkout uses (Git on Windows writes "\r\n"). */
    private static String read(String path) {
        try {
            return java.nio.file.Files.readString(new File(path).toPath()).replace("\r\n", "\n");
        } catch (java.io.IOException e) {
            throw new AssertionError(e);
        }
    }

    /** {@code text} with {@code regex} replaced; fails when nothing matched, so a test edit can't silently do nothing. */
    private static String edit(String text, String regex, String replacement) {
        String out = text.replaceAll(regex, replacement);
        assertNotEquals(text, out, "the test's edit found nothing to change: " + regex);
        return out;
    }

    /** The shipped files, read exactly as the server does (a YAML error fails the test with its message). */
    private static ClassCatalog shipped() {
        try {
            return of(SHIPPED_CLASSES, SHIPPED_ABILITIES);
        } catch (Exception e) {
            throw new AssertionError("shipped classes.yml / abilities.yml don't parse: " + e.getMessage(), e);
        }
    }

    private static ClassCatalog of(String classesYml, String abilitiesYml) throws Exception {
        YamlConfiguration c = new YamlConfiguration(), a = new YamlConfiguration();
        c.loadFromString(classesYml);
        a.loadFromString(abilitiesYml);
        return ClassCatalog.from(c, a);
    }

    private static final String ONE_CLASS = "classes:\n  warrior:\n    name: Warrior\ngroups:\n  movement: 2s\n";

    @Test
    void shippedPrototypeLoadsCleanly() {
        ClassCatalog cat = shipped();
        assertEquals(List.of(), cat.problems(), "the shipped files load without refusals");
        assertEquals(Set.of("warrior", "mage", "duelist"), cat.classes().keySet());
        for (ClassDef c : cat.classes().values()) {
            long actives = c.abilities().stream().filter(id -> cat.ability(id).kind() == AbilityDef.Kind.ACTIVE).count();
            long passives = c.abilities().stream().filter(id -> cat.ability(id).kind() == AbilityDef.Kind.PASSIVE).count();
            long keystones = c.abilities().stream().filter(id -> cat.ability(id).kind() == AbilityDef.Kind.KEYSTONE).count();
            assertEquals(4, actives, c.id() + " actives");
            assertEquals(2, passives, c.id() + " passives");
            assertEquals(1, keystones, c.id() + " keystone");
        }
        assertEquals(2000L, cat.groupMs().get("movement"));
        assertEquals(Set.of("movement"), cat.ability("blink").groups());
        assertEquals(Set.of(WeaponFamily.FOCUS), cat.ability("arcane_bolt").weapons());
        assertEquals(1, AbilityCaster.momentumNeeded(cat.ability("execute")), "Execute needs Momentum to spend");
        assertEquals(0, AbilityCaster.momentumNeeded(cat.ability("cleave")), "Cleave builds it");
    }

    @Test
    void aFinisherNeedsAStackUnlessTheFileSaysOtherwise() throws Exception {
        // A server's abilities.yml written before "needs" existed: the spend-all finisher still needs one stack.
        String old = edit(SHIPPED_ABILITIES, "spend-all: true, needs: 1 \\}", "spend-all: true }");
        assertEquals(1, AbilityCaster.momentumNeeded(of(SHIPPED_CLASSES, old).ability("execute")));
        String off = edit(SHIPPED_ABILITIES, "needs: 1 \\}", "needs: 0 }");
        assertEquals(0, AbilityCaster.momentumNeeded(of(SHIPPED_CLASSES, off).ability("execute")), "needs: 0 turns it off");
        String wild = edit(SHIPPED_ABILITIES, "needs: 1 \\}", "needs: 99 }");
        ClassCatalog w = of(SHIPPED_CLASSES, wild);
        assertTrue(w.ability("execute") == null || AbilityCaster.momentumNeeded(w.ability("execute")) <= ClassCatalog.MAX_STACKS,
                "an out-of-range value is refused or clamped, never above the stack cap");
    }

    @Test
    void classEntryNeedsSkillsNeverAnotherClass() {
        ClassCatalog cat = shipped();
        ClassDef warrior = cat.classDef("warrior"), duelist = cat.classDef("duelist");
        assertTrue(warrior.canLearn(s -> s.equals("Weapon Mastery") ? 5 : 1), "any one of Blade/Weapon Mastery 5");
        assertFalse(warrior.canLearn(s -> 4));
        assertFalse(duelist.canLearn(s -> s.equals("Blade Mastery") ? 10 : 1), "Duelist needs Critical Strike too");
        assertTrue(duelist.canLearn(s -> s.equals("Blade Mastery") ? 10 : 5));
    }

    @Test
    void boundsRefuseLimitBreakingAbilities() throws Exception {
        ClassCatalog cat = of(ONE_CLASS, """
                abilities:
                  ok:       { class: warrior, kind: active, cooldown: 4s, effects: [ { damage: { coefficient: 2.5 } } ] }
                  greedy:   { class: warrior, kind: active, cooldown: 4s, effects: [ { damage: { coefficient: 1.2, hits: 3 } } ] }
                  big:      { class: warrior, kind: active, cooldown: 25s, effects: [ { damage: { coefficient: 1.5, hits: 3 } } ] }
                  spammy:   { class: warrior, kind: active, cooldown: 200ms, effects: [ { damage: { coefficient: 1.0 } } ] }
                  typo:     { class: warrior, kind: active, cooldown: 4s, effects: [ { damgae: { coefficient: 1.0 } } ] }
                  badhand:  { class: warrior, kind: active, cooldown: 4s, weapons: [lightsaber], effects: [ { damage: {} } ] }
                  orphan:   { class: necromancer, kind: active, cooldown: 4s, effects: [ { damage: {} } ] }
                  farblink: { class: warrior, kind: active, cooldown: 4s, effects: [ { blink: { distance: 100 } } ] }
                  hollow:   { class: warrior, kind: passive }
                """);
        assertEquals(Set.of("ok", "big"), cat.abilities().keySet(), String.join("\n", cat.problems()));
        String all = String.join("\n", cat.problems());
        assertTrue(all.contains("greedy") && all.contains("over the 3.0x cap"), all);
        assertTrue(all.contains("spammy") && all.contains("floor"), all);
        assertTrue(all.contains("unknown verb damgae"), all);
        assertTrue(all.contains("unknown weapon family lightsaber"), all);
        assertTrue(all.contains("unknown class necromancer"), all);
        assertTrue(all.contains("blink distance"), all);
        assertTrue(all.contains("hollow") && all.contains("needs passives"), all);
    }

    @Test
    void wrongItemMessagesSayWhatToHold() {
        ClassCatalog cat = shipped();
        assertEquals("a focus (blaze rod, breeze rod or stick)", AbilityCaster.needs(cat.ability("arcane_bolt")));
        assertEquals("a blade, an axe, a polearm or a blunt weapon", AbilityCaster.needs(cat.ability("execute")));
        assertEquals("a blade", AbilityCaster.needs(cat.ability("flurry")));
    }

    @Test
    void weaponFamiliesFromVanillaItems() {
        assertEquals(WeaponFamily.BLADE, WeaponFamily.ofMaterial("NETHERITE_SWORD"));
        assertEquals(WeaponFamily.AXE, WeaponFamily.ofMaterial("IRON_AXE"));
        assertEquals(WeaponFamily.OTHER, WeaponFamily.ofMaterial("IRON_PICKAXE"));
        assertEquals(WeaponFamily.POLEARM, WeaponFamily.ofMaterial("IRON_SPEAR"));
        assertEquals(WeaponFamily.POLEARM, WeaponFamily.ofMaterial("TRIDENT"));
        assertEquals(WeaponFamily.BLUNT, WeaponFamily.ofMaterial("MACE"));
        assertEquals(WeaponFamily.FOCUS, WeaponFamily.ofMaterial("BLAZE_ROD"));
        assertEquals(WeaponFamily.UNARMED, WeaponFamily.ofMaterial("AIR"));
        assertTrue(WeaponFamily.fits(Set.of(), WeaponFamily.OTHER), "no requirement: anything");
        assertTrue(WeaponFamily.fits(Set.of(WeaponFamily.BLADE, WeaponFamily.AXE), WeaponFamily.AXE));
        assertFalse(WeaponFamily.fits(Set.of(WeaponFamily.FOCUS), WeaponFamily.BLADE));
        assertEquals("an axe", WeaponFamily.AXE.withArticle());
    }

    @Test
    void ranksAreBoundedAndOverflowSafe() {
        assertEquals(105, ClassRanks.xpForRank(1));
        assertEquals(3205, ClassRanks.xpForRank(25));
        long total = ClassRanks.totalTo(50);
        assertTrue(total > 150_000 && total < 200_000, "about 172k XP to master a class: " + total);
        ClassRanks.Result r = ClassRanks.award(1, 0, 105);
        assertEquals(2, r.rank());
        assertEquals(0, r.xp());
        assertEquals(new ClassRanks.Result(ClassRanks.MAX_RANK, 0, 49), ClassRanks.award(1, 0, Double.MAX_VALUE));
        assertEquals(new ClassRanks.Result(3, 10, 0), ClassRanks.award(3, 10, Double.NaN));
        assertEquals(new ClassRanks.Result(3, 10, 0), ClassRanks.award(3, 10, -500));
    }

    @Test
    void castsAtWeakMobsPayKillXpsShare() {
        Attributes.KillXp.Params k = Attributes.KillXp.Params.DEFAULTS; // gap 20, factor 0.25
        double base = ClassService.CAST_XP;
        assertEquals(base, ClassRanks.castXp(base, 30, 40, k), "a foe 10 levels below: full XP");
        assertEquals(base * 0.25, ClassRanks.castXp(base, 20, 40, k), "exactly the gap below: grey, a quarter");
        assertEquals(base * 0.25, ClassRanks.castXp(base, 15, 40, k));
        assertEquals(base, ClassRanks.castXp(base, 1, 10, k), "a new player is never short-changed");
        assertEquals(base, ClassRanks.castXp(base, 0, 99, k), "an unknown foe pays the base");
        Attributes.KillXp.Params off = new Attributes.KillXp.Params(false, 40, 0.15, 20, 0.25, 1e6);
        assertEquals(base, ClassRanks.castXp(base, 15, 40, off), "off with kill XP v2");
        Attributes.KillXp.Params wild = new Attributes.KillXp.Params(true, 40, 0.15, 20, 7.0, 1e6);
        assertEquals(base, ClassRanks.castXp(base, 15, 40, wild), "a factor above 1 never multiplies");
        assertEquals(base, ClassRanks.castXp(base, 15, 40, null));
        assertEquals(0.0, ClassRanks.castXp(Double.NaN, 30, 40, k));
        assertEquals(1, ClassRanks.award(1, 0, ClassRanks.castXp(base, 15, 40, k)).xp(), "1.5 XP banks as 1");
    }

    @Test
    void loadoutRulesAreFewAndCrossClassIsFine() {
        ClassCatalog cat = shipped();
        Map<String, AbilityDef> ab = cat.abilities();
        // A Warrior strike, a Mage blink and a Duelist counter together: allowed.
        Loadout mixed = Loadout.empty("Mixed").withActive(0, "cleave").withActive(1, "blink").withActive(2, "riposte")
                .withPassive(0, "precision").withPassive(1, "battle_hardened").withKeystone("glass_cannon");
        assertEquals(List.of(), mixed.problems(ab, a -> true));
        // Wrong kind, duplicates, locked; an id the files don't have (anymore) is inert, not a problem:
        Loadout bad = Loadout.empty("Bad").withActive(0, "precision").withActive(1, "cleave").withActive(2, "cleave")
                .withPassive(0, "nonsense").withKeystone("juggernaut");
        List<String> p = bad.problems(ab, a -> !a.id().equals("juggernaut"));
        assertTrue(p.contains("Precision can't go in an active slot"), p.toString());
        assertTrue(p.contains("Cleave is equipped twice"), p.toString());
        assertTrue(p.contains("Juggernaut is not unlocked yet"), p.toString());
        assertEquals(3, p.size(), p.toString());
        // Slots are fixed in size; blanks are empty.
        Loadout l = new Loadout(" ", List.of("cleave", ""), null, null);
        assertEquals(Loadout.ACTIVE_SLOTS, l.actives().size());
        assertNull(l.actives().get(1));
        assertEquals("Loadout", l.name());
    }

    @Test
    void badEntriesAreRefusedNeverThrown() throws Exception {
        ClassCatalog cat = of("classes:\n  a: {}\n", """
                abilities:
                  odd_element: { class: a, kind: active, cooldown: 4s, effects: [ { damage: { element: 5 } } ] }
                  odd_type: { class: a, kind: active, cooldown: [1, 2], effects: [ { empower: {} } ] }
                  ten_stacks: { class: a, kind: active, cooldown: 8s, effects: [ { damage: { per-momentum: 0.4 } }, { resource: { kind: momentum, delta: 1, max: 10 } } ] }
                  long_window: { class: a, kind: active, cooldown: 6s, effects: [ { counter: { window: 1h } } ] }
                  slow_success: { class: a, kind: active, cooldown: 6s, effects: [ { counter: { success-cooldown: 30s } } ] }
                  fine: { class: a, kind: active, cooldown: 4s, effects: [ { damage: { element: fire } } ] }
                """);
        assertEquals(java.util.Set.of("odd_element", "fine"), cat.abilities().keySet(), cat.problems().toString());
        String all = String.join(" | ", cat.problems());
        assertTrue(all.contains("odd_type"), all);
        assertTrue(all.contains("resource max"), all);
        assertTrue(all.contains("counter window"), all);
        assertTrue(all.contains("success-cooldown longer than the cooldown"), all);
        assertEquals("5", ((EffectSpec.Damage) cat.ability("odd_element").effects().get(0)).element(), "a number is read as text");
    }

    @Test
    void cooldownTextMatchesTheOldFormat() {
        for (long ms : new long[]{0, 49, 50, 999, 1234, 2050, 29_960, 59_999}) {
            assertEquals(String.format(java.util.Locale.ROOT, "%.1f s", Math.round(ms / 100.0) / 10.0), AbilityCaster.seconds(ms), "ms " + ms);
        }
    }

    @Test
    void aSnapshotSavesTheSameDocument() {
        ClassCatalog cat = shipped();
        ClassProfile p = new ClassProfile();
        p.learn("warrior");
        p.setRank("warrior", 7);
        p.setLoadout(0, Loadout.empty("A").withActive(0, "cleave"));
        p.cooldowns().commit(cat.ability("charge"), 10_000, cat.groupMs(), 1_000);
        ClassProfile.Snapshot snap = p.snapshot(2_000);
        p.setRank("warrior", 9); // later changes don't leak into a snapshot already taken
        String text = snap.toYaml();
        List<String> repairs = new java.util.ArrayList<>();
        ClassProfile back = ClassProfile.fromYaml(text, cat, 2_000, repairs);
        assertEquals(7, back.rank("warrior"), repairs.toString());
        assertEquals("cleave", back.loadouts().get(0).actives().get(0));
        assertEquals(9_000, back.cooldowns().remaining(cat.ability("charge"), 2_000));
    }

    @Test
    void exclusiveTagsAllowOnlyOnePerTag() throws Exception {
        ClassCatalog cat = of("classes:\n  a: {}\n  b: {}\n", """
                abilities:
                  stance1: { class: a, kind: active, cooldown: 2s, exclusive: [stance], effects: [ { empower: {} } ] }
                  stance2: { class: b, kind: active, cooldown: 2s, exclusive: [stance], effects: [ { empower: {} } ] }
                """);
        Loadout l = Loadout.empty("x").withActive(0, "stance1").withActive(1, "stance2");
        assertEquals(List.of("Only one stance at a time: stance1 and stance2"), l.problems(cat.abilities(), a -> true));
    }

    @Test
    void profilesRoundTripAndStoredDataIsNeverTrusted() throws Exception {
        ClassCatalog cat = shipped();
        long t = 5_000_000;
        ClassProfile p = new ClassProfile();
        p.learn("warrior"); p.learn("mage");
        p.setRank("warrior", 12); p.setRank("mage", 8);
        p.award("mage", 30);
        Loadout l = Loadout.empty("Mixed").withActive(0, "cleave").withActive(1, "blink").withPassive(0, "battle_hardened").withKeystone("juggernaut");
        p.setLoadout(1, l);
        p.setActive(1);
        p.cooldowns().commit(cat.ability("war_cry"), 30_000, cat.groupMs(), t);
        String saved = p.toYaml(t + 1000);

        List<String> repairs = new java.util.ArrayList<>();
        ClassProfile back = ClassProfile.fromYaml(saved, cat, t + 2000, repairs);
        assertEquals(List.of(), repairs);
        assertEquals(12, back.rank("warrior"));
        assertEquals(new ClassProfile.Progress(8, 30), back.classes().get("mage"));
        assertEquals(1, back.activeIndex());
        assertEquals(l, back.activeLoadout());
        assertEquals(28_000, back.cooldowns().remaining(cat.ability("war_cry"), t + 2000), "a long cooldown survives a relog");

        // A tampered or outdated document is repaired, never trusted.
        String tampered = saved.replace("rank: 12", "rank: 999").replace("cleave", "nuke").replace("battle_hardened", "precision");
        repairs.clear();
        tampered = tampered.replaceFirst("classes:\n", "classes:\n  necromancer:\n    rank: 50\n    xp: 0\n");
        ClassProfile fixed = ClassProfile.fromYaml(tampered, cat, t + 2000, repairs);
        assertEquals(ClassRanks.MAX_RANK, fixed.rank("warrior"), "rank clamped to 50");
        assertEquals("nuke", fixed.activeLoadout().actives().get(0), "an ability missing from the files stays in its slot, inert");
        assertNull(fixed.activeLoadout().passives().get(0), "an ability of an unlearned class (Duelist) can't stay equipped");
        assertFalse(fixed.knows("necromancer"), "a class missing from classes.yml is not usable...");
        assertTrue(fixed.toYaml(t + 2000).contains("necromancer"), "...but its saved progress is written back, never erased");
        assertTrue(repairs.size() >= 3, repairs.toString());

        // A reload that adds the class back restores that progress; one that drops a class keeps it dormant.
        ClassCatalog withNecro = of(edit(SHIPPED_CLASSES, "(?m)^classes:\n",
                "classes:\n  necromancer:\n    name: Necromancer\n    role: Test\n    signature: Test\n    entry: { Intelligence: 1 }\n"), SHIPPED_ABILITIES);
        repairs.clear();
        fixed.reconcile(withNecro, repairs);
        assertEquals(ClassRanks.MAX_RANK, fixed.rank("necromancer"), repairs.toString());
        ClassCatalog noMage = of(edit(SHIPPED_CLASSES, "(?s)  mage:.*?(?=  duelist:)", ""), SHIPPED_ABILITIES);
        fixed.learn("mage");
        repairs.clear();
        fixed.reconcile(noMage, repairs);
        assertFalse(fixed.knows("mage"));
        assertTrue(fixed.toYaml(t).contains("mage:"), "mage's progress stays in the saved document");

        // A document that doesn't parse, or comes from a newer version, is not read at all (null):
        // the caller leaves it as stored instead of starting empty and saving over it.
        repairs.clear();
        assertNull(ClassProfile.fromYaml("classes: [unclosed", cat, t, repairs));
        assertEquals(1, repairs.size(), repairs.toString());
        repairs.clear();
        assertNull(ClassProfile.fromYaml("version: 99\nclasses:\n  warrior:\n    rank: 3\n", cat, t, repairs));
        assertTrue(repairs.get(0).contains("newer version"), repairs.toString());
        // A new player (no document) starts with three empty loadouts.
        ClassProfile fresh = ClassProfile.fromYaml(null, cat, t, repairs);
        assertTrue(fresh.classes().isEmpty());
        assertEquals(ClassProfile.BASE_LOADOUTS, fresh.loadouts().size());
        // Junk ids (far too long to be a renamed ability) are dropped.
        String junk = "x".repeat(ClassProfile.MAX_ID_LENGTH + 1);
        repairs.clear();
        ClassProfile cleaned = ClassProfile.fromYaml(saved.replace("cleave", junk), cat, t + 2000, repairs);
        assertNull(cleaned.activeLoadout().actives().get(0), repairs.toString());
    }

    @Test
    void cooldownsBelongToThePlayerAndSurviveSwitchesAndRelogs() {
        ClassCatalog cat = shipped();
        AbilityDef blink = cat.ability("blink"), lunge = cat.ability("lunge"), bolt = cat.ability("arcane_bolt");
        CooldownBook book = new CooldownBook();
        long t = 1_000_000;
        book.commit(blink, blink.cooldownMs(), cat.groupMs(), t);
        assertEquals(CooldownBook.GLOBAL_MS, book.remaining(bolt, t), "a short global gap after any cast");
        assertEquals(0, book.remaining(bolt, t + CooldownBook.GLOBAL_MS));
        assertEquals(2000, book.remaining(lunge, t), "Lunge shares the movement lockout with Blink");
        assertEquals(6000, book.remaining(blink, t));
        // A "switch" does nothing to the book; a relog restores the long ones.
        Map<String, Long> saved = book.saved(t + 1000, 3000);
        assertEquals(Set.of("ability:blink"), saved.keySet(), "only cooldowns with 3 s or more left are saved");
        CooldownBook after = new CooldownBook();
        after.restore(saved, t + 1500);
        assertEquals(4500, after.remaining(blink, t + 1500));
        after.restore(Map.of("ability:x", t - 5, "junk", t + 99_999), t + 1500);
        assertEquals(0, after.remaining(bolt, t + 1500), "expired or unknown entries are ignored");
        after.restore(Map.of("ability:arcane_bolt", t + 1000L * 3600_000L), t);
        assertEquals(CooldownBook.MAX_RESTORED_MS, after.remaining(bolt, t), "a damaged save can't lock an ability for good");
        // A successful counter brings Riposte back sooner, never later.
        AbilityDef riposte = cat.ability("riposte");
        book.commit(riposte, riposte.cooldownMs(), cat.groupMs(), t);
        book.shorten(riposte, 1500, t + 100);
        assertEquals(1500, book.remaining(riposte, t + 100));
        book.shorten(riposte, 9000, t + 100);
        assertEquals(1500, book.remaining(riposte, t + 100));
    }
}
