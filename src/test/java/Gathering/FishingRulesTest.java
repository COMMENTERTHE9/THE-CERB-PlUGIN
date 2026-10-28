package Gathering;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/** Fishing's pure rules (docs/design/FISHING.md): waters, species, waits, treasure, creatures, techniques, parsing. */
class FishingRulesTest {

    private static String shippedText() throws Exception {
        return java.nio.file.Files.readString(new File("src/main/resources/resources.yml").toPath()).replace("\r\n", "\n");
    }

    private static GatheringCatalog shipped() throws Exception {
        YamlConfiguration y = new YamlConfiguration();
        y.loadFromString(shippedText());
        return GatheringCatalog.from(y);
    }

    private static FishingRules.Moment at(String water, String biome, boolean night, boolean rain) {
        return new FishingRules.Moment(water, biome, night, rain, false);
    }

    @Test
    void theShippedFishingLoadsWhole() throws Exception {
        GatheringCatalog c = shipped();
        assertEquals(List.of(), c.problems(), "the shipped resources.yml loads without refusals");
        FishingRules r = c.fishing();
        assertTrue(r.enabled());
        assertEquals("Fishing", r.skill());
        assertEquals(7, r.waters().size());
        assertEquals(31, r.allSpecies().size());
        assertEquals(4, r.treasure().size());
        assertEquals(8, r.creatures().size());
        for (FishingRules.Species s : r.allSpecies().values()) {
            GatheringCatalog.ItemDef d = c.item(s.item());
            assertNotNull(d, s.id());
            assertTrue(d.vanilla(), s.id() + " is a fish like any other");
        }
        for (FishingRules.Creature cr : r.creatures().values()) {
            for (FishingRules.Drop d : cr.drops()) assertTrue(c.item(d.item()) != null || GatheringCatalog.material(d.item()) != null, cr.id() + " " + d.item());
        }
        // Every rod upgrade's item exists and is not a fish.
        for (GatheringCatalog.UpgradeDef u : c.upgrades().values()) {
            if (!u.on().contains("rod")) continue;
            assertNotNull(c.item(u.item()), u.id());
            assertFalse(c.item(u.item()).vanilla(), u.id() + " is a protected item");
        }
        // The plugin's own list of what the update added names everything the shipped file has.
        List<String> added = GatheringCatalog.ADDED_BY_UPDATES.get("items");
        for (FishingRules.Species s : r.allSpecies().values()) assertTrue(added.contains(s.item()), s.item());
        for (FishingRules.Treasure t : r.treasure()) assertTrue(added.contains(t.item()), t.item());
        for (GatheringCatalog.UpgradeDef u : c.upgrades().values()) assertTrue(GatheringCatalog.ADDED_BY_UPDATES.get("upgrades").contains(u.id()), u.id());
    }

    @Test
    void theWaterIsUndergroundThenTheBiomeThenTheFallback() throws Exception {
        FishingRules r = shipped().fishing();
        assertEquals("cave", r.waterAt("river", true).id(), "underground first, whatever the biome");
        assertEquals("river", r.waterAt("river", false).id());
        assertEquals("river", r.waterAt("FROZEN_RIVER", false).id(), "biome keys are read in any case");
        assertEquals("warm", r.waterAt("lukewarm_ocean", false).id());
        assertEquals("cold", r.waterAt("deep_frozen_ocean", false).id());
        assertEquals("swamp", r.waterAt("mangrove_swamp", false).id());
        assertEquals("fresh", r.waterAt("plains", false).id(), "anywhere else: lakes and ponds");
        assertEquals("fresh", r.waterAt(null, false).id());
    }

    @Test
    void speciesFollowLevelTimeWeatherAndBiome() throws Exception {
        FishingRules r = shipped().fishing();
        List<String> day1 = r.eligible(at("river", "river", false, false), 1).stream().map(FishingRules.Species::id).toList();
        assertEquals(List.of("silver_dace"), day1, "a beginner on a river by day");
        List<String> night20 = r.eligible(at("river", "river", true, false), 20).stream().map(FishingRules.Species::id).toList();
        assertEquals(List.of("silver_dace", "river_perch", "brook_char"), night20, "Brook Char bites at night only");
        assertFalse(r.eligible(at("river", "river", false, false), 100).stream().anyMatch(s -> s.id().equals("riverking_sturgeon")), "rain only");
        assertTrue(r.eligible(at("river", "river", false, true), 100).stream().anyMatch(s -> s.id().equals("riverking_sturgeon")));
        assertFalse(r.eligible(at("ocean", "ocean", true, false), 100).stream().anyMatch(s -> s.id().equals("abyssal_angler")), "deep ocean only");
        assertTrue(r.eligible(at("ocean", "deep_ocean", true, false), 100).stream().anyMatch(s -> s.id().equals("abyssal_angler")));
        assertTrue(r.eligible(at("cave", "dripstone_caves", false, false), 4).isEmpty(), "cave fish from level 5");
    }

    @Test
    void theVanillaShareFallsAndTheRollIsByWeight() throws Exception {
        FishingRules r = shipped().fishing();
        assertEquals(0.7, r.vanillaShare(1), 1e-9);
        assertEquals(0.35, r.vanillaShare(100), 1e-9);
        assertEquals(0.35, r.vanillaShare(150), 1e-9, "never below the level-100 share");
        FishingRules.Moment river = at("river", "river", false, false);
        assertNull(r.rollSpecies(river, 1, 0.69, 0.5), "under the vanilla share: vanilla's own fish");
        assertEquals("silver_dace", r.rollSpecies(river, 1, 0.71, 0.99).id());
        // Level 25 by day: dace 60, perch 25, trout 10 (weights by rarity) = 95.
        assertEquals("silver_dace", r.rollSpecies(river, 25, 0.99, 59.0 / 95).id());
        assertEquals("river_perch", r.rollSpecies(river, 25, 0.99, 61.0 / 95).id());
        assertEquals("goldfin_trout", r.rollSpecies(river, 25, 0.99, 94.0 / 95).id());
        assertNull(r.rollSpecies(at(null, "plains", false, false), 50, 0.99, 0.5), "no water: nothing of ours");
    }

    @Test
    void weightsStayInTheirRangeAndMostAreSmall() throws Exception {
        FishingRules.Species trout = shipped().fishing().speciesById("goldfin_trout");
        assertEquals(trout.minKg(), FishingRules.kg(trout, 0), 1e-9);
        assertEquals(trout.maxKg(), FishingRules.kg(trout, 1), 1e-9);
        double median = FishingRules.kg(trout, 0.5);
        assertTrue(median < (trout.minKg() + trout.maxKg()) / 2, "the middle roll is in the lower half: " + median);
        assertEquals(trout.minKg(), FishingRules.kg(trout, -3), 1e-9, "a bad random number is clamped");
    }

    @Test
    void waitsShortenWithLureSpeedAndQuickBiteButNeverBelowTheFloor() throws Exception {
        FishingRules r = shipped().fishing();
        assertEquals(1.0, r.waitFactor(0, 1), 1e-9);
        assertEquals(0.9, r.waitFactor(0.1, 9), 1e-9);
        assertEquals(0.85, r.waitFactor(0, 10), 1e-9, "Quick Bite at 10");
        assertEquals(0.9 * 0.85, r.waitFactor(0.1, 10), 1e-9);
        assertEquals(0.4, r.waitFactor(0.9, 100), 1e-9, "the floor");
        assertEquals(0.4, r.waitFactor(5, 100), 1e-9, "a silly stat is clamped");
    }

    @Test
    void techniquesOpenAtTheirLevels() throws Exception {
        FishingRules r = shipped().fishing();
        assertFalse(r.treasureSense(24));
        assertTrue(r.treasureSense(25));
        assertTrue(r.elite(50) && !r.elite(49));
        assertTrue(r.twinCatch(75) && !r.twinCatch(74));
        assertTrue(r.apex(100) && !r.apex(99));
        assertEquals(1, r.openedBetween(4, 5).size());
        assertTrue(r.openedBetween(4, 5).get(0).startsWith("Something Stirs"));
        assertEquals(6, r.openedBetween(0, 100).size(), "six milestones");
        assertEquals(List.of(), r.openedBetween(10, 24));
        assertTrue(r.openedBetween(9, 10).get(0).contains("15% sooner"));
    }

    @Test
    void creaturesFollowTheirWaterAndLevel() throws Exception {
        FishingRules r = shipped().fishing();
        assertTrue(r.creaturesOf("river", 4).isEmpty(), "nothing before Something Stirs");
        assertEquals(List.of("river_lurker"), r.creaturesOf("river", 5).stream().map(FishingRules.Creature::id).toList());
        assertEquals(List.of("tidecaller", "reef_guardian"), r.creaturesOf("warm", 99).stream().map(FishingRules.Creature::id).toList());
        assertTrue(r.creaturesOf("cave", 29).isEmpty());
        assertEquals("hollow_angler", r.rollCreature("cave", 30, 0.5).id());
        assertNull(r.rollCreature("cave", 29, 0.5));
        assertNull(r.apexOf("ocean", 99));
        assertEquals("leviathan", r.apexOf("ocean", 100).id());
        assertEquals("drowned_monarch", r.apexOf("swamp", 100).id());
        assertFalse(r.creaturesOf("ocean", 100).stream().anyMatch(FishingRules.Creature::apex), "apexes are never regular rolls");
    }

    @Test
    void anUnchosenCallPrefersALureOfTheWaterItIsMadeIn() throws Exception {
        FishingRules r = shipped().fishing();
        List<String> banked = List.of("river_lurker", "tidecaller", "bog_stalker");
        assertEquals("tidecaller", r.lureFor(banked, "warm", "river_lurker"), "at sea, the sea's creature");
        assertEquals("bog_stalker", r.lureFor(banked, "swamp", "river_lurker"));
        assertEquals("river_lurker", r.lureFor(banked, "fresh", "river_lurker"), "a lake: the River Lurker lives there too");
        assertEquals("river_lurker", r.lureFor(banked, "cave", "river_lurker"), "no lure for this water: the first banked");
        assertEquals("river_lurker", r.lureFor(banked, null, "river_lurker"));
        assertEquals("river_lurker", r.lureFor(List.of("not_a_creature"), "warm", "river_lurker"), "unknown ids are skipped");
    }

    @Test
    void aCalledCreaturesLevelFollowsFishingWithinItsBounds() throws Exception {
        FishingRules r = shipped().fishing();
        FishingRules.Creature lurker = r.creature("river_lurker");
        assertEquals(30, r.creatureLevel(lurker, 30, 1, 0, 100), "its owner's Fishing level");
        assertEquals(33, r.creatureLevel(lurker, 30, 1, 1, 100), "spread +3");
        assertEquals(27, r.creatureLevel(lurker, 30, 1, -1, 100), "spread -3");
        assertEquals(5, r.creatureLevel(lurker, 5, 1, -1, 100), "never below its own level");
        assertEquals(60, r.creatureLevel(lurker, 30, 60, 0, 100), "never below the world's level there");
        assertEquals(100, r.creatureLevel(lurker, 150, 1, 1, 100), "at most the server's max level");
    }

    @Test
    void badEntriesAreRefusedOneByOne() throws Exception {
        String bad = shippedText()
                .replace("    silver_dace:         { water: river,", "    silver_dace:         { water: lake,")
                .replace("    river_perch:         { water: river, rarity: uncommon,", "    river_perch:         { water: river, rarity: mythic,")
                .replace("      entity: BOGGED", "      entity: ENDER_DRAGON")
                .replace("    quick-bite: { at: 10, wait: 0.85 }", "    quick-bite: { at: 10, wait: 5 }");
        YamlConfiguration y = new YamlConfiguration();
        y.loadFromString(bad);
        GatheringCatalog c = GatheringCatalog.from(y);
        List<String> p = c.problems();
        assertTrue(p.stream().anyMatch(s -> s.startsWith("fishing species silver_dace: unknown water lake")), p.toString());
        assertTrue(p.stream().anyMatch(s -> s.startsWith("fishing species river_perch: unknown rarity")), p.toString());
        assertTrue(p.stream().anyMatch(s -> s.startsWith("fishing creature bog_stalker: entity ENDER_DRAGON")), p.toString());
        assertTrue(p.stream().anyMatch(s -> s.startsWith("fishing quick-bite.wait")), p.toString());
        FishingRules r = c.fishing();
        assertNull(r.speciesById("silver_dace"));
        assertNotNull(r.speciesById("goldfin_trout"), "the rest still loads");
        assertNull(r.creature("bog_stalker"));
        assertEquals(0.85, r.techniques().quickBiteWait(), 1e-9, "a bad number keeps the default");
    }

    @Test
    void fishingOffOrMissingIsQuiet() throws Exception {
        YamlConfiguration y = new YamlConfiguration();
        y.loadFromString(shippedText().replaceFirst("(?m)^fishing:\\n", "fishing: off\nold_fishing:\n"));
        GatheringCatalog c = GatheringCatalog.from(y);
        assertFalse(c.fishing().enabled());
        assertEquals(List.of(), c.fishing().openedBetween(0, 100));
        assertEquals(List.of(), c.problems().stream().filter(s -> s.startsWith("fishing")).toList());
    }

    @Test
    void aSyntheticWaterSpeciesAndCreatureLoadAsData() throws Exception {
        // The extension point: a new water, a species and a creature are only entries (docs/design/FISHING.md §10).
        String yml = """
                items:
                  test_minnow: { material: COD, name: Test Minnow, vanilla: true }
                  test_scale: { material: TURTLE_SCUTE, name: Test Scale }
                fishing:
                  waters:
                    test_lake: { name: Test Lake, biomes: [cherry_grove] }
                    rest: { name: Elsewhere, fallback: true }
                  species:
                    test_minnow: { water: test_lake, rarity: rare, level: 3, weight: 7, xp: 99, kg: [1, 2] }
                  creatures:
                    test_beast: { name: Test Beast, entity: ZOMBIE, waters: [test_lake], level: 3, health: 1.5,
                                  drops: [ { item: test_scale, chance: 0.5 } ] }
                """;
        YamlConfiguration y = new YamlConfiguration();
        y.loadFromString(yml);
        GatheringCatalog c = GatheringCatalog.from(y);
        assertEquals(List.of(), c.problems());
        FishingRules r = c.fishing();
        assertEquals("test_lake", r.waterAt("cherry_grove", false).id());
        FishingRules.Species s = r.speciesById("test_minnow");
        assertEquals(99, s.xp(), 1e-9);
        assertEquals(7, s.weight(), 1e-9);
        assertEquals("test_minnow", r.rollSpecies(at("test_lake", "cherry_grove", false, false), 3, 0.99, 0.5).id());
        FishingRules.Creature b = r.creature("test_beast");
        assertEquals("ZOMBIE", b.entity());
        assertEquals("COMMON", b.tier());
        assertEquals("test_beast", r.rollCreature("test_lake", 5, 0.1).id());
        assertEquals(Set.of("test_lake"), b.waters());
    }

    @Test
    void creatureIdsAndSpeciesIdsArePlainSoTheJournalCanSaveThem() throws Exception {
        FishingRules r = shipped().fishing();
        Map<String, String> ids = new HashMap<>();
        List<String> all = new ArrayList<>();
        all.addAll(r.allSpecies().keySet());
        for (FishingRules.Treasure t : r.treasure()) all.add(t.id());
        all.addAll(r.creatures().keySet());
        for (String id : all) {
            assertTrue(FishingRules.ID.matcher(id).matches(), id);
            assertNull(ids.put(id, id), "one journal key per catch: " + id);
        }
    }

    // ── docs/design/FISHING_EQUIPMENT.md §3: the journal's checks are the bite's conditions ──

    @Test
    void theJournalsChecksAgreeWithWhatCanBiteEverywhere() throws Exception {
        FishingRules r = shipped().fishing();
        java.util.Set<String> biomes = new java.util.LinkedHashSet<>();
        for (FishingRules.Water w : r.waters()) biomes.addAll(w.biomes());
        biomes.add("plains");
        for (FishingRules.Species s : r.allSpecies().values()) biomes.addAll(s.when().biomes());
        int compared = 0;
        for (FishingRules.Water w : r.waters()) for (String biome : biomes) for (boolean night : new boolean[] {false, true})
            for (int weather = 0; weather < 3; weather++) for (int level : new int[] {1, 5, 10, 20, 25, 45, 50, 75, 85, 100}) {
                FishingRules.Moment m = new FishingRules.Moment(w.id(), biome, night, weather > 0, weather > 1);
                java.util.Set<String> eligible = new java.util.HashSet<>();
                for (FishingRules.Species s : r.eligible(m, level)) eligible.add(s.id());
                for (FishingRules.Species s : r.allSpecies().values()) {
                    boolean all = FishingRules.allMet(r.checks(s, m, level));
                    assertEquals(FishingRules.canBite(s, m, level), all, s.id() + " at " + m + " level " + level);
                    assertEquals(eligible.contains(s.id()), all, s.id() + " at " + m + " level " + level);
                    compared++;
                }
            }
        assertTrue(compared > 10_000, "compared " + compared);
    }

    @Test
    void anUnknownPlaceLeavesWaterAndBiomeUnknownButKnowsTheTime() throws Exception {
        FishingRules r = shipped().fishing();
        FishingRules.Species angler = r.speciesById("abyssal_angler"); // night, deep ocean, level 85
        List<FishingRules.Check> c = r.checks(angler, new FishingRules.Moment(null, null, false, false, false), 90);
        java.util.Map<FishingRules.Need, FishingRules.CheckState> by = new java.util.EnumMap<>(FishingRules.Need.class);
        for (FishingRules.Check x : c) by.put(x.kind(), x.state());
        assertEquals(FishingRules.CheckState.MET, by.get(FishingRules.Need.LEVEL));
        assertEquals(FishingRules.CheckState.UNKNOWN, by.get(FishingRules.Need.WATER));
        assertEquals(FishingRules.CheckState.MISSING, by.get(FishingRules.Need.TIME), "it is day: known anywhere in the world");
        assertEquals(FishingRules.CheckState.UNKNOWN, by.get(FishingRules.Need.BIOME));
        assertFalse(FishingRules.allMet(c));
        // each requirement on its own line, the missing ones named
        List<FishingRules.Check> wrong = r.checks(angler, at("river", "river", false, false), 30);
        assertEquals(List.of(FishingRules.CheckState.MISSING, FishingRules.CheckState.MISSING, FishingRules.CheckState.MISSING, FishingRules.CheckState.MISSING),
                wrong.stream().map(FishingRules.Check::state).toList(), "level, water, night and the deep ocean are all missing");
        assertEquals("Fishing 85", wrong.get(0).need());
        assertEquals("Night only", wrong.get(2).need());
    }

    @Test
    void trackingTheVerdictChangesOnlyWithWhatIsMissing() throws Exception {
        FishingRules r = shipped().fishing();
        FishingRules.Species trout = r.speciesById("brook_char"); // river, night, 15
        String day = Fishing.verdict(r.checks(trout, at("river", "river", false, false), 30));
        String night = Fishing.verdict(r.checks(trout, at("river", "river", true, false), 30));
        String nightAgain = Fishing.verdict(r.checks(trout, at("river", "frozen_river", true, true), 30));
        assertEquals("ok", night);
        assertEquals(night, nightAgain, "rain or another river biome changes nothing for this species");
        assertNotEquals(day, night);
        String at10 = Fishing.verdict(r.checks(trout, at("river", "river", true, false), 10));
        String at11 = Fishing.verdict(r.checks(trout, at("river", "river", true, false), 11));
        assertEquals(at10, at11, "a level-up that still falls short doesn't repeat the hint");
        assertEquals("ok", Fishing.verdict(r.checks(trout, at("river", "river", true, false), 15)));
    }

    @Test
    void theBiteCueGoesGreenThenYellowThenRedAsTheBiteRunsOut() {
        assertEquals(net.kyori.adventure.text.format.NamedTextColor.GREEN, Fishing.cueColor(0));
        assertEquals(net.kyori.adventure.text.format.NamedTextColor.GREEN, Fishing.cueColor(7));
        assertEquals(net.kyori.adventure.text.format.NamedTextColor.YELLOW, Fishing.cueColor(8));
        assertEquals(net.kyori.adventure.text.format.NamedTextColor.RED, Fishing.cueColor(16));
        assertTrue(Fishing.CUE_RED_TICKS < 20, "red before the shortest bite (20 ticks) can end");
    }

    @Test
    void theTrackingLineNeverSaysCanBiteWhereTheWaterIsUnknown() throws Exception {
        FishingRules r = shipped().fishing();
        FishingRules.Species trout = r.speciesById("brook_char");
        String known = Fishing.trackingLine(r, trout, r.checks(trout, at("river", "river", true, false), 30));
        assertTrue(known.contains("can bite here"), known);
        String unknown = Fishing.trackingLine(r, trout, r.checks(trout, at(null, "void", true, false), 30));
        assertFalse(unknown.contains("can bite"), unknown);
        assertTrue(unknown.contains("isn't one of the fishing waters"), unknown);
    }
}
