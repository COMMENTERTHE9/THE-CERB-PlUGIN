package Gathering;

import Attributes.AttributeCalculator;
import Attributes.AttributeRules;
import Attributes.Contribution;
import Attributes.Stat;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/** Fishing equipment's pure rules (docs/design/FISHING_EQUIPMENT.md): the shipped tackle, what counts, bait, the cap. */
class TackleRulesTest {

    private static GatheringCatalog shipped() throws Exception {
        YamlConfiguration y = new YamlConfiguration();
        y.loadFromString(java.nio.file.Files.readString(new File("src/main/resources/resources.yml").toPath()).replace("\r\n", "\n"));
        return GatheringCatalog.from(y);
    }

    @Test
    void theShippedEquipmentLoadsWhole() throws Exception {
        GatheringCatalog c = shipped();
        assertEquals(List.of(), c.problems());
        TackleRules t = c.tackle();
        assertTrue(t.enabled());
        assertEquals(List.of(1, 10, 25, 50), t.categories().values().stream().map(TackleRules.Category::unlock).toList());
        // The 11 rod upgrades, each a part of the category it belongs to.
        Map<String, String> want = Map.ofEntries(Map.entry("scaled_grip", "rod"), Map.entry("heartwood_handle", "rod"),
                Map.entry("pearl_inlay", "rod"), Map.entry("monarchs_blessing", "rod"), Map.entry("resin_line", "line"),
                Map.entry("leviathan_line", "line"), Map.entry("rime_hook", "lure"), Map.entry("pearl_float", "lure"),
                Map.entry("sea_glass_float", "lure"), Map.entry("lantern_bob", "lure"), Map.entry("reef_eye_lens", "lure"));
        for (Map.Entry<String, String> e : want.entrySet()) {
            TackleRules.Part p = t.part(t.partOfUpgrade(e.getKey()));
            assertNotNull(p, e.getKey() + " is a part");
            assertEquals(e.getValue(), p.category(), e.getKey());
            assertEquals(c.upgrade(e.getKey()).item(), p.item(), e.getKey() + " is fitted with its upgrade's item");
        }
        long rodUpgrades = c.upgrades().values().stream().filter(u -> u.on().contains("rod")).count();
        assertEquals(rodUpgrades, want.size(), "every rod upgrade is mapped");
        assertEquals(TackleRules.Special.AUTO_REEL, t.part("auto_reel").special());
        assertEquals("reel", t.part("auto_reel").category());
        assertEquals("auto_hook_reel", t.part("auto_reel").item());
        assertEquals(TackleRules.Special.DISCOVERY, t.part("surveyors_float").special());
        assertEquals(1.5, t.part("surveyors_float").value(), 1e-9);
        assertEquals(TackleRules.Special.SPECIMEN, t.part("specimen_hook").special());
        assertNull(t.part("surveyors_float").item(), "earned, not fitted");
        assertEquals(Set.of("grub_bait", "chum", "glowworm_bait", "wayfarers_bait"), t.bait().keySet());
        for (TackleRules.Bait b : t.bait().values()) assertTrue(c.item(b.item()) != null && !c.item(b.item()).vanilla(), b.id() + "'s item is protected");
        assertEquals(Set.of("grub_bait", "chum", "glowworm_bait", "wayfarers_bait", "auto_hook_reel"), t.recipes().keySet());
        assertEquals("waters_3", t.recipes().get("wayfarers_bait").unlockedBy());
        assertNull(t.recipes().get("grub_bait").unlockedBy());
        // What the update added is listed, so an edited older resources.yml still gets it.
        for (String item : List.of("grub_bait", "chum", "glowworm_bait", "wayfarers_bait", "auto_hook_reel")) {
            assertTrue(GatheringCatalog.ADDED_BY_UPDATES.get("items").contains(item), item);
        }
    }

    @Test
    void theBagOpensWithFishing() throws Exception {
        TackleRules t = shipped().tackle();
        assertEquals(1, t.bagSlots(1));
        assertEquals(1, t.bagSlots(19));
        assertEquals(2, t.bagSlots(20));
        assertEquals(2, t.bagSlots(39));
        assertEquals(3, t.bagSlots(40));
        assertEquals(3, t.bagSlots(100));
    }

    @Test
    void onlyOwnedConfiguredUnlockedInstalledPartsCount() throws Exception {
        TackleRules t = shipped().tackle();
        Set<String> owned = Set.of("scaled_grip", "resin_line", "pearl_float", "auto_reel", "gone_part");
        Set<String> installed = new LinkedHashSet<>(List.of("scaled_grip", "resin_line", "pearl_float", "auto_reel", "gone_part", "rime_hook"));
        assertEquals(Set.of("scaled_grip"), t.active(installed, owned, 9), "the line waits for 10, the lure for 25, the reel for 50");
        assertEquals(Set.of("scaled_grip", "resin_line"), t.active(installed, owned, 10));
        assertEquals(Set.of("scaled_grip", "resin_line", "pearl_float", "auto_reel"), t.active(installed, owned, 50),
                "a part not on this server and a part not owned never count");
        assertTrue(t.hasAutoReel(t.active(installed, owned, 50)));
        assertFalse(t.hasAutoReel(t.active(installed, owned, 49)));
    }

    @Test
    void anOldRodAndTheSetupCountEachUpgradeOnce() throws Exception {
        TackleRules t = shipped().tackle();
        Set<String> active = Set.of("scaled_grip", "resin_line");
        Set<String> counted = TackleRules.countedUpgrades(active, t.parts(), List.of("scaled_grip", "pearl_float"));
        assertEquals(Set.of("scaled_grip", "resin_line", "pearl_float"), counted);
        GatheringCatalog c = shipped();
        List<Contribution> cs = Tackle.contributions(active, List.of("scaled_grip", "pearl_float"), c, "Discoveries");
        assertEquals(3, cs.size(), "the grip once, not twice");
        double lure = cs.stream().filter(x -> x.stat() == Stat.LURE_SPEED).mapToDouble(Contribution::amount).sum();
        assertEquals(0.06, lure, 1e-9);
        assertTrue(cs.stream().anyMatch(x -> x.label().startsWith("Rod in hand · Pearl float")));
        assertTrue(cs.stream().anyMatch(x -> x.label().startsWith("Fishing setup (Discoveries) · Scaled grip")));
        assertTrue(Tackle.contributions(Set.of(), List.of("heartwood_grip"), c, "").isEmpty(), "an axe's upgrade on a rod adds nothing");
    }

    @Test
    void newPartsGoWhereTheirGoalIs() throws Exception {
        GatheringCatalog c = shipped();
        TackleRules t = c.tackle();
        assertEquals(List.of(0, 1, 2), t.defaultPresets("scaled_grip", c.upgrades()), "bite speed: everywhere");
        assertEquals(List.of(0, 1, 2), t.defaultPresets("resin_line", c.upgrades()), "encounters: everywhere");
        assertEquals(List.of(1, 2), t.defaultPresets("pearl_float", c.upgrades()), "treasure replaces a fish: not in Discoveries");
        assertEquals(List.of(0, 1, 2), t.defaultPresets("auto_reel", c.upgrades()));
        assertEquals(List.of(0), t.defaultPresets("surveyors_float", c.upgrades()));
        assertEquals(List.of(), t.defaultPresets("specimen_hook", c.upgrades()));
        assertEquals(List.of(), t.defaultPresets("nothing", c.upgrades()));
    }

    @Test
    void weightFactorsFavourWithoutHidingAndStopAtTheCap() throws Exception {
        FishingRules r = shipped().fishing();
        TackleRules t = shipped().tackle();
        FishingRules.Species dace = r.speciesById("silver_dace"), brook = r.speciesById("brook_char");
        assertEquals(1.0, t.weightFactor(dace, true, 1.5, 1), 1e-9, "a caught species is not favoured");
        assertEquals(1.5, t.weightFactor(dace, false, 1.5, 1), 1e-9);
        assertEquals(1.0, t.weightFactor(dace, false, 1, 2), 1e-9, "night bait: only night-only species");
        assertEquals(2.0, t.weightFactor(brook, true, 1.5, 2), 1e-9);
        assertEquals(t.maxWeightFactor(), t.weightFactor(brook, false, 1.5, 2), 1e-9, "1.5 x 2 stops at the cap");
        assertEquals(1.0, t.weightFactor(dace, false, 0.5, 0.5), 1e-9, "never below 1");
        assertEquals(2.0, TackleRules.specimenExponent(0.25), 1e-9);
        assertEquals(2.5, TackleRules.specimenExponent(0), 1e-9);
    }

    @Test
    void baitAppliesOnlyWhereItCan() throws Exception {
        GatheringCatalog c = shipped();
        FishingRules r = c.fishing();
        TackleRules t = c.tackle();
        FishingRules.Moment riverDay = new FishingRules.Moment("river", "river", false, false, false);
        FishingRules.Moment riverNight = new FishingRules.Moment("river", "river", true, false, false);
        FishingRules.Moment fresh = new FishingRules.Moment("fresh", "plains", false, false, false);
        assertTrue(Tackle.appliedTo(t.baitById("grub_bait"), r, riverDay, 1, Set.of()), "speed: always");
        assertFalse(Tackle.appliedTo(t.baitById("chum"), r, riverDay, 4, Set.of()), "encounters: not before Something Stirs");
        assertTrue(Tackle.appliedTo(t.baitById("chum"), r, riverDay, 10, Set.of()), "the River Lurker lives in rivers from 5");
        assertFalse(Tackle.appliedTo(t.baitById("glowworm_bait"), r, riverDay, 30, Set.of()), "night bait by day: unused");
        assertTrue(Tackle.appliedTo(t.baitById("glowworm_bait"), r, riverNight, 30, Set.of()), "a river at night: Brook Char");
        assertFalse(Tackle.appliedTo(t.baitById("glowworm_bait"), r, riverNight, 10, Set.of()), "...but not below its level");
        Set<String> allFresh = new LinkedHashSet<>();
        for (FishingRules.Species s : r.species("fresh")) allFresh.add(s.id());
        assertTrue(Tackle.appliedTo(t.baitById("wayfarers_bait"), r, fresh, 60, Set.of("pond_bream")));
        assertFalse(Tackle.appliedTo(t.baitById("wayfarers_bait"), r, fresh, 60, allFresh), "everything here caught: unused");
        assertFalse(Tackle.appliedTo(null, r, fresh, 60, Set.of()));
    }

    @Test
    void theEngineCapsWhatASetupAdds() throws Exception {
        AttributeRules rules = AttributeRules.DEFAULTS;
        // Lure speed from the skill at 100 (0.2) plus the four rod-category parts (0.22): 0.42 raw under a 0.30 cap.
        List<Contribution> skill = List.of(Contribution.stat("skill:Fishing", "Fishing 100", Stat.LURE_SPEED, 0.2, null));
        List<Contribution> parts = new ArrayList<>(skill);
        for (double a : new double[] {0.06, 0.04, 0.04, 0.08}) parts.add(Contribution.stat("tackle:setup", "part", Stat.LURE_SPEED, a, null));
        double before = AttributeCalculator.compute(skill, rules).secondary(Stat.LURE_SPEED);
        double after = AttributeCalculator.compute(parts, rules).secondary(Stat.LURE_SPEED);
        double cap = rules.secondary().get(Stat.LURE_SPEED).cap();
        assertTrue(after > before && after < cap, before + " -> " + after);
        assertTrue(after - before < 0.22, "diminishing returns: less than the raw 22%");
        Tackle.StatChange small = new Tackle.StatChange(Stat.LURE_SPEED, 0.2999, 0.29995, 0.04, true);
        assertTrue(small.capped(), "a raw change the cap eats is said to be capped");
        Tackle.StatChange real = new Tackle.StatChange(Stat.LURE_SPEED, 0.09, 0.127, 0.06, true);
        assertFalse(real.capped());
    }

    @Test
    void brokenEntriesAreRefusedWithAReason() {
        YamlConfiguration y = new YamlConfiguration();
        y.set("categories.rod.unlock", 1);
        y.set("parts.a.category", "nope");
        y.set("parts.b.category", "rod");
        y.set("parts.b.upgrade", "missing_upgrade");
        y.set("parts.c.category", "rod");
        y.set("parts.c.special", "teleport");
        y.set("parts.d.category", "rod");
        y.set("parts.d.special", "discovery");
        y.set("parts.d.factor", 9);
        y.set("bait.e.item", "nothing");
        y.set("recipes.f.ingredients", List.of("NOT_A_MATERIAL"));
        y.set("rewards.g.species", -3);
        y.set("reel.delay-ticks", 99);
        List<String> problems = new ArrayList<>();
        TackleRules t = TackleRules.from(y, Map.of(), Map.of(), problems);
        String all = String.join(" | ", problems);
        assertTrue(all.contains("parts.a: unknown category"), all);
        assertTrue(all.contains("parts.b: unknown upgrade"), all);
        assertTrue(all.contains("parts.c: unknown special"), all);
        assertTrue(all.contains("parts.d.factor"), all);
        assertTrue(all.contains("bait.e: unknown item"), all);
        assertTrue(all.contains("recipes.f: unknown result item") || all.contains("recipes.f: unknown material"), all);
        assertTrue(all.contains("rewards.g"), all);
        assertTrue(all.contains("reel.delay-ticks"), all);
        assertEquals(8, t.reelDelayTicks(), "a bad delay falls back");
        assertEquals(1.5, t.part("d").value(), 1e-9, "a bad factor falls back");
    }
}
