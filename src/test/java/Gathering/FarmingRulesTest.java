package Gathering;

import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Farming's pure rules (docs/design/FARMING_COOKING.md §3): crops, ripeness, techniques by level, quality odds, parsing. */
class FarmingRulesTest {

    private static GatheringCatalog shipped() throws Exception {
        YamlConfiguration y = new YamlConfiguration();
        y.loadFromString(java.nio.file.Files.readString(new File("src/main/resources/resources.yml").toPath()).replace("\r\n", "\n"));
        return GatheringCatalog.from(y);
    }

    private static FarmingRules parse(String yaml, List<String> problems) throws Exception {
        YamlConfiguration y = new YamlConfiguration();
        y.loadFromString(yaml);
        GatheringCatalog c = GatheringCatalog.from(y);
        problems.addAll(c.problems());
        return c.farming();
    }

    @Test
    void theShippedCropsLoadCleanly() throws Exception {
        GatheringCatalog c = shipped();
        assertEquals(List.of(), c.problems());
        FarmingRules f = c.farming();
        assertTrue(f.enabled());
        assertEquals("Farming", f.skill());
        assertEquals(12, f.crops().size());
        assertSame(f.crop("wheat"), f.crop(Material.WHEAT));
        assertSame(f.crop("glow_berries"), f.crop(Material.CAVE_VINES_PLANT), "both cave vine blocks are glow berries");
        assertEquals(Material.WHEAT_SEEDS, f.crop("wheat").seed());
        assertNull(f.crop("melon").seed(), "the stem stays: nothing to replant");
        assertTrue(f.crop("sugar_cane").column() && f.crop("cactus").column());
        assertTrue(f.crop("sweet_berries").pick() && f.crop("glow_berries").pick());
        for (FarmingRules.Crop crop : f.crops()) {
            assertTrue(crop.xp() > 0 && crop.discoveryXp() > 0, crop.id());
            if (crop.replants()) assertNotNull(Material.matchMaterial(crop.seed().name()), crop.id());
        }
        assertTrue(f.crops().stream().filter(FarmingRules.Crop::quality).allMatch(cr -> !cr.column()), "cane and cactus roll no quality");
    }

    @Test
    void ripenessByKind() throws Exception {
        FarmingRules f = shipped().farming();
        FarmingRules.Crop wheat = f.crop("wheat"), berries = f.crop("sweet_berries"), glow = f.crop("glow_berries"), melon = f.crop("melon");
        assertFalse(FarmingRules.ripe(wheat, 6, 7, false, false));
        assertTrue(FarmingRules.ripe(wheat, 7, 7, false, false));
        assertTrue(FarmingRules.ripe(wheat, 7, 7, false, true), "a planted crop pays: the placed memory never applies to it");
        assertFalse(FarmingRules.ripe(berries, 1, 3, false, false));
        assertTrue(FarmingRules.ripe(berries, 2, 3, false, false), "a bush with berries from stage 2");
        assertFalse(FarmingRules.ripe(glow, 0, 25, false, false));
        assertTrue(FarmingRules.ripe(glow, 0, 25, true, false));
        assertTrue(FarmingRules.ripe(melon, 0, 0, false, false), "a melon that grew");
        assertFalse(FarmingRules.ripe(melon, 0, 0, false, true), "a melon placed by hand");
        assertTrue(f.placeable(Material.MELON) && f.placeable(Material.SUGAR_CANE) && f.placeable(Material.CACTUS) && f.placeable(Material.PUMPKIN));
        assertFalse(f.placeable(Material.WHEAT), "a wheat stage is no block a player places");
        assertFalse(f.placeable(Material.STONE));
    }

    @Test
    void techniquesOpenAtTheirLevels() throws Exception {
        FarmingRules f = shipped().farming();
        assertFalse(f.replants(9));
        assertTrue(f.replants(10));
        assertEquals(0, f.reach(24));
        assertEquals(3, f.reach(25));
        assertEquals(3, f.reach(49));
        assertEquals(5, f.reach(50));
        assertEquals(7, f.reach(75));
        assertEquals(9, f.reach(100));
        assertEquals(9, f.reach(150));
        assertEquals(25, f.wideAt());
        assertEquals(16, f.perTick());
    }

    @Test
    void qualityOpensAtFiftyAndRisesToAHundred() throws Exception {
        FarmingRules f = shipped().farming();
        FarmingRules.Crop wheat = f.crop("wheat");
        assertEquals(0, f.chance(Quality.FINE, 49));
        assertEquals(Quality.COMMON, f.rollQuality(wheat, 49, 0.999));
        assertEquals(0.10, f.chance(Quality.FINE, 50), 1e-9);
        assertEquals(0.02, f.chance(Quality.SUPERIOR, 50), 1e-9);
        assertEquals(0.0, f.chance(Quality.MASTERWORK, 50), 1e-9);
        assertEquals(0.16, f.chance(Quality.FINE, 75), 1e-9, "halfway");
        assertEquals(0.22, f.chance(Quality.FINE, 100), 1e-9);
        assertEquals(0.08, f.chance(Quality.SUPERIOR, 100), 1e-9);
        assertEquals(0.03, f.chance(Quality.MASTERWORK, 100), 1e-9);
        assertEquals(0.67, f.chance(Quality.COMMON, 100), 1e-9);
        assertEquals(0.03, f.chance(Quality.MASTERWORK, 150), 1e-9, "clamped past 100");
        assertEquals(Quality.MASTERWORK, f.rollQuality(wheat, 100, 0.999));
        assertEquals(Quality.COMMON, f.rollQuality(wheat, 100, 0.0));
        assertEquals(Quality.COMMON, f.rollQuality(f.crop("sugar_cane"), 100, 0.999), "no quality on cane");
        // Over many rolls the shares match the odds.
        java.util.Random rnd = new java.util.Random(7);
        int[] n = new int[4];
        for (int i = 0; i < 200_000; i++) n[f.rollQuality(wheat, 100, rnd.nextDouble()).ordinal()]++;
        assertEquals(0.22, n[1] / 200_000.0, 0.005);
        assertEquals(0.03, n[3] / 200_000.0, 0.002);
    }

    @Test
    void theWeakCropRuleUsesTheCropsLevel() throws Exception {
        GatheringCatalog c = shipped();
        FarmingRules.Crop wheat = c.farming().crop("wheat"), wart = c.farming().crop("nether_wart");
        assertEquals(3, GatherRules.xp(wheat.xp(), wheat.level(), 20, c.weakGap(), c.weakFactor()));
        assertEquals(0.75, GatherRules.xp(wheat.xp(), wheat.level(), 21, c.weakGap(), c.weakFactor()), 1e-9, "a quarter 20 levels above it");
        assertEquals(4, GatherRules.xp(wart.xp(), wart.level(), 59, c.weakGap(), c.weakFactor()));
    }

    @Test
    void badEntriesAreRefusedOneByOne() throws Exception {
        List<String> problems = new java.util.ArrayList<>();
        FarmingRules f = parse("""
                farming:
                  skill: Farming
                  wide-harvest: { reach: { 25: 4, 50: 5 } }
                  quality: { at: 50, odds: { at-level-50: { fine: 80, superior: 30 } } }
                  crops:
                    good:   { blocks: [WHEAT], ripe: max-age, produce: WHEAT, seed: WHEAT_SEEDS, level: 1, xp: 3 }
                    twice:  { blocks: [WHEAT], ripe: max-age, produce: WHEAT, level: 1, xp: 3 }
                    noripe: { blocks: [CARROTS], ripe: soon, produce: CARROT, level: 1, xp: 3 }
                    badage: { blocks: [SWEET_BERRY_BUSH], ripe: { age: 0 }, produce: SWEET_BERRIES, level: 1, xp: 3 }
                    column: { blocks: [BEETROOTS], ripe: max-age, column: true, produce: BEETROOT, level: 1, xp: 3 }
                    gone:   off
                """, problems);
        assertEquals(1, f.crops().size(), problems.toString());
        assertNotNull(f.crop("good"));
        assertTrue(problems.stream().anyMatch(p -> p.contains("twice") && p.contains("already crop good")), problems.toString());
        assertTrue(problems.stream().anyMatch(p -> p.contains("noripe")), problems.toString());
        assertTrue(problems.stream().anyMatch(p -> p.contains("badage")), problems.toString());
        assertTrue(problems.stream().anyMatch(p -> p.contains("column needs ripe: grown")), problems.toString());
        assertTrue(problems.stream().anyMatch(p -> p.contains("wide-harvest.reach 25")), "an even side is refused: " + problems);
        assertEquals(0, f.reach(25));
        assertEquals(5, f.reach(50));
        assertTrue(problems.stream().anyMatch(p -> p.contains("more than 100")), problems.toString());
        assertEquals(0, f.chance(Quality.FINE, 50), "odds over 100% give no quality there");
        assertFalse(problems.stream().anyMatch(p -> p.contains("gone")), "off is quiet");
    }

    @Test
    void offOrAnUnknownSkillTurnsFarmingOff() throws Exception {
        List<String> problems = new java.util.ArrayList<>();
        assertFalse(parse("farming: off\n", problems).enabled());
        assertFalse(parse("resources: {}\n", problems).enabled(), "no section: no rules");
        assertEquals(List.of(), problems);
        YamlConfiguration y = new YamlConfiguration();
        y.loadFromString("farming:\n  skill: Gardening\n  crops: {}\n");
        GatheringCatalog c = GatheringCatalog.from(y, s -> s.equals("Farming") ? s : null);
        assertFalse(c.farming().enabled());
        assertTrue(c.problems().stream().anyMatch(p -> p.contains("unknown skill Gardening")));
    }

    @Test
    void aCropWinsOverAResourceForTheSameBlock() throws Exception {
        YamlConfiguration y = new YamlConfiguration();
        y.loadFromString("""
                resources:
                  melons: { skill: Mining, blocks: [MELON], level: 1, xp: 1 }
                farming:
                  crops:
                    melon: { blocks: [MELON], ripe: grown, produce: MELON_SLICE, level: 1, xp: 4 }
                """);
        GatheringCatalog c = GatheringCatalog.from(y);
        assertNull(c.resource(Material.MELON), "one block, one set of rules: a harvest never pays twice");
        assertNotNull(c.farming().crop(Material.MELON));
        assertTrue(c.tracked(Material.MELON));
        assertTrue(c.problems().stream().anyMatch(p -> p.contains("the crop's rules win")));
    }
}
