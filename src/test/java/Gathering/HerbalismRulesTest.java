package Gathering;

import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/** Herbalism's rules (docs/design/HERBALISM.md): herbs, produce, rare reagents, Wildcrafting's reach, reagent quality. */
class HerbalismRulesTest {

    private static final Map<String, GatheringCatalog.ItemDef> ITEMS = Map.of(
            "bloodroot", new GatheringCatalog.ItemDef("bloodroot", Material.NETHER_BRICK, "Bloodroot", List.of()),
            "moonpetal", new GatheringCatalog.ItemDef("moonpetal", Material.PRISMARINE_CRYSTALS, "Moonpetal", List.of()));

    private static HerbalismRules parse(String yaml, List<String> problems) throws Exception {
        YamlConfiguration y = new YamlConfiguration();
        y.loadFromString(yaml);
        return HerbalismRules.from(y.getConfigurationSection("herbalism"), ITEMS, s -> s.equals("Herbalism") ? s : null, problems);
    }

    private static final String SAMPLE = """
            herbalism:
              skill: Herbalism
              rare-reagents:
                bloodroot: { item: bloodroot }
                moonpetal: { item: moonpetal }
              rare: { at: 25, chance-at-start: 0.02, chance-at-100: 0.06 }
              wildcrafting:
                reach: { 10: 3, 40: 5, 70: 7 }
                per-tick: 16
              quality:
                at: 50
                odds:
                  at-level-50:  { fine: 10, superior: 2, masterwork: 0 }
                  at-level-100: { fine: 22, superior: 8, masterwork: 3 }
              herbs:
                poppy:     { blocks: [POPPY], level: 1, xp: 3, discovery-xp: 15, grows: "plains, forests", rare: bloodroot }
                fern:      { blocks: [FERN, LARGE_FERN], produce: [FERN, LARGE_FERN], level: 1, xp: 2, shears: true }
                kelp:      { blocks: [KELP, KELP_PLANT], produce: [KELP], level: 10, xp: 2 }
                rose_bush: { blocks: [ROSE_BUSH], level: 15, xp: 6, rare: bloodroot }
                bluet:     { name: Azure Bluet, blocks: [AZURE_BLUET], level: 1, xp: 3, rare: moonpetal }
            """;

    @Test
    void herbsAreDataWithTheirBlocksAndProduce() throws Exception {
        List<String> problems = new ArrayList<>();
        HerbalismRules r = parse(SAMPLE, problems);
        assertEquals(List.of(), problems);
        assertTrue(r.enabled());
        assertEquals("poppy", r.herb(Material.POPPY).id());
        assertEquals(Set.of(Material.POPPY), r.herb("poppy").produce(), "a flower's own item counts as gathering it");
        assertEquals(Set.of(Material.KELP), r.herb(Material.KELP_PLANT).produce(), "a kelp plant drops kelp");
        assertTrue(r.herb("fern").shears());
        assertEquals("Azure Bluet", r.herb("bluet").name());
        assertEquals("Rose bush", r.herb("rose_bush").name(), "a name from the id when none is written");
        assertTrue(r.tracked(Material.LARGE_FERN), "the placed-block memory keeps every herb block");
        assertFalse(r.tracked(Material.WHEAT));
        assertEquals(List.of("poppy", "rose_bush"), r.rare("bloodroot").from(), "a rare reagent knows the herbs that give it");
        assertEquals("moonpetal", r.rare("moonpetal").item());
    }

    @Test
    void rareReagentsFromTheirLevelRisingTo100() throws Exception {
        HerbalismRules r = parse(SAMPLE, new ArrayList<>());
        assertEquals(0, r.rareChance(24), 0.0, "none before 25");
        assertEquals(0.02, r.rareChance(25), 1e-12);
        assertEquals(0.04, r.rareChance(62) , 0.001, "halfway");
        assertEquals(0.06, r.rareChance(100), 1e-12);
        assertEquals(0.06, r.rareChance(150), 1e-12, "mastery past 100 keeps the top chance");
    }

    @Test
    void wildcraftingReachAndQualityByLevel() throws Exception {
        HerbalismRules r = parse(SAMPLE, new ArrayList<>());
        assertEquals(0, r.reach(9));
        assertEquals(3, r.reach(10));
        assertEquals(5, r.reach(40));
        assertEquals(7, r.reach(100));
        assertEquals(10, r.wildcraftAt());
        assertEquals(16, r.perTick());
        assertEquals(1.0, r.chance(Quality.COMMON, 49), 1e-12, "no quality before 50");
        assertEquals(0.10, r.chance(Quality.FINE, 50), 1e-12);
        assertEquals(0.22, r.chance(Quality.FINE, 100), 1e-12);
        assertEquals(0.03, r.chance(Quality.MASTERWORK, 100), 1e-12);
        // At 100: Common 67%, Fine 22%, Superior 8%, Masterwork 3%, rolled in that order.
        assertEquals(Quality.COMMON, r.rollQuality(100, 0.0));
        assertEquals(Quality.FINE, r.rollQuality(100, 0.70));
        assertEquals(Quality.SUPERIOR, r.rollQuality(100, 0.92));
        assertEquals(Quality.MASTERWORK, r.rollQuality(100, 0.99));
        assertEquals(Quality.COMMON, r.rollQuality(49, 0.99), "nothing but Common before 50");
    }

    @Test
    void techniquesAreAnnouncedAtTheirLevels() throws Exception {
        HerbalismRules r = parse(SAMPLE, new ArrayList<>());
        assertEquals(1, r.openedBetween(9, 10).size());
        assertTrue(r.openedBetween(9, 10).get(0).startsWith("Wildcrafting 3×3"));
        assertTrue(r.openedBetween(24, 25).get(0).startsWith("Rare reagents"));
        assertTrue(r.openedBetween(49, 50).get(0).startsWith("Reagent quality"));
        assertEquals(List.of(), r.openedBetween(10, 24));
        assertEquals(5, r.openedBetween(1, 100).size(), "three reaches, rare reagents and quality");
    }

    @Test
    void theShippedHerbsLoadCleanly() throws Exception {
        YamlConfiguration y = new YamlConfiguration();
        y.loadFromString(java.nio.file.Files.readString(new java.io.File("src/main/resources/resources.yml").toPath()).replace("\r\n", "\n"));
        GatheringCatalog c = GatheringCatalog.from(y);
        assertEquals(List.of(), c.problems());
        HerbalismRules r = c.herbalism();
        assertTrue(r.enabled());
        assertEquals("Herbalism", r.skill());
        assertEquals(35, r.herbs().size(), "the 35 herbs of docs/design/HERBALISM.md §3");
        assertEquals(List.of("bloodroot", "moonpetal", "sunpetal", "emberbloom"), r.rares().stream().map(HerbalismRules.Rare::id).toList());
        for (HerbalismRules.Rare rare : r.rares()) {
            assertFalse(rare.from().isEmpty(), rare.id() + " comes from some herb");
            GatheringCatalog.ItemDef def = c.item(rare.item());
            assertNotNull(def, rare.id());
            assertFalse(def.vanilla(), rare.id() + " is a guarded Cerberus item, never a vanilla ingredient");
        }
        for (HerbalismRules.Herb h : r.herbs()) {
            assertTrue(h.xp() > 0 && h.discoveryXp() > 0 && !h.grows().isBlank(), h.id());
            for (Material m : h.blocks()) {
                assertNull(c.farming().crop(m), h.id() + ": " + m + " is a crop");
                assertNull(c.resource(m), h.id() + ": " + m + " is a resource");
                assertFalse(c.tracked(m), h.id() + ": herbs are remembered only when placed, not when they spread or grow");
            }
        }
        // The shears herbs drop nothing of themselves to a hand, so the hint is right for every block of them.
        assertEquals(java.util.Set.of("fern", "seagrass", "glow_lichen", "hanging_roots", "small_dripleaf", "nether_sprouts"),
                r.herbs().stream().filter(HerbalismRules.Herb::shears).map(HerbalismRules.Herb::id).collect(java.util.stream.Collectors.toSet()));
        assertEquals(Set.of(Material.SEAGRASS), r.herb(Material.TALL_SEAGRASS).produce(), "tall seagrass has no item of its own");
        assertEquals(10, r.wildcraftAt());
        assertEquals(25, r.rareAt());
        assertEquals(50, r.qualityAt());
    }

    @Test
    void aHerbNeverClaimsACropOrAResource() throws Exception {
        List<String> problems = new ArrayList<>();
        YamlConfiguration y = new YamlConfiguration();
        y.loadFromString(SAMPLE);
        HerbalismRules r = HerbalismRules.from(y.getConfigurationSection("herbalism"), ITEMS, s -> s.equals("Herbalism") ? s : null,
                Map.of(Material.KELP_PLANT, "crop kelp"), problems);
        assertNull(r.herb("kelp"), "refused whole: one of its blocks has rules of its own");
        assertNotNull(r.herb("poppy"));
        assertTrue(String.join("\n", problems).contains("KELP_PLANT is already crop kelp"), String.join("\n", problems));
    }

    @Test
    void badEntriesAreRefusedOnTheirOwn() throws Exception {
        List<String> problems = new ArrayList<>();
        HerbalismRules r = parse("""
                herbalism:
                  skill: Herbalism
                  rare-reagents:
                    ghost: { item: nothing }
                  herbs:
                    good:    { blocks: [DANDELION], xp: 3 }
                    twice:   { blocks: [DANDELION], xp: 3 }
                    nothing: { blocks: [NOT_A_BLOCK], xp: 3 }
                    rarely:  { blocks: [POPPY], xp: 3, rare: ghost }
                    level:   { blocks: [ALLIUM], xp: 3, level: 0 }
                    off: off
                  wildcrafting:
                    reach: { 10: 4, 0: 3, 20: 5 }
                """, problems);
        assertEquals(Set.of("good"), Set.copyOf(r.herbs().stream().map(HerbalismRules.Herb::id).toList()), String.join("\n", problems));
        assertEquals(5, r.reach(20), "the valid step stays; an even side and level 0 are refused");
        assertEquals(0, r.reach(10));
        String all = String.join("\n", problems);
        for (String bad : List.of("ghost", "twice", "nothing", "rarely", "level", "reach 10", "reach 0")) assertTrue(all.contains(bad), bad + " in " + all);
        List<String> none = new ArrayList<>();
        assertFalse(parse("herbalism:\n  skill: Nobody\n", none).enabled(), "an unknown skill: herbalism off");
        assertTrue(none.get(0).contains("unknown skill"));
        assertFalse(HerbalismRules.NONE.enabled());
    }
}
