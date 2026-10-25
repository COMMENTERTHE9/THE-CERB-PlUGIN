package Gathering;

import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class GatheringTest {

    private static GatheringCatalog of(String yaml) throws Exception {
        YamlConfiguration y = new YamlConfiguration();
        y.loadFromString(yaml);
        return GatheringCatalog.from(y);
    }

    private static GatheringCatalog shipped() throws Exception {
        return of(java.nio.file.Files.readString(new File("src/main/resources/resources.yml").toPath()).replace("\r\n", "\n"));
    }

    @Test
    void theShippedFileLoadsCleanly() throws Exception {
        GatheringCatalog c = shipped();
        assertEquals(List.of(), c.problems());
        assertEquals("iron_ore", c.resource(Material.DEEPSLATE_IRON_ORE).id());
        assertEquals(8, c.resource(Material.IRON_ORE).xp());
        assertTrue(c.resource(Material.DIAMOND_ORE).fortune());
        assertFalse(c.resource(Material.STONE).fortune(), "no bonus cobblestone");
        assertNull(c.resource(Material.COBBLESTONE), "generators make cobblestone: it pays nothing");
        assertNull(c.resource(Material.DIRT));
        assertTrue(c.tracked(Material.STONE) && !c.tracked(Material.OAK_PLANKS), "only resource blocks are remembered when placed");
        assertEquals(2, c.smeltXp(Material.IRON_INGOT));
        assertEquals(0, c.smeltXp(Material.IRON_NUGGET), "melting gear down pays nothing");
        assertEquals(10, c.forgeXp(Material.IRON_SWORD), "2 ingots x 5");
        assertEquals(40, c.forgeXp(Material.IRON_CHESTPLATE), "8 ingots x 5");
        assertEquals(160, c.forgeXp(Material.DIAMOND_CHESTPLATE));
        assertEquals(15, c.forgeXp(Material.GOLDEN_PICKAXE));
        assertEquals(6, c.forgeXp(Material.COPPER_SWORD));
        assertEquals(0, c.forgeXp(Material.IRON_BLOCK), "storage blocks are reversible: nothing");
        assertEquals(0, c.forgeXp(Material.NETHERITE_SWORD), "netherite comes from the upgrade, paid on its own");
        assertEquals(50, c.netheriteUpgradeXp());
        assertTrue(c.fortuneMax() <= 0.5);
    }

    @Test
    void xpRules() {
        assertEquals(8, GatherRules.xp(8, 10, 1, 20, 0.25), "a new miner gets the full XP");
        assertEquals(8, GatherRules.xp(8, 10, 29, 20, 0.25), "19 levels above: still full");
        assertEquals(2, GatherRules.xp(8, 10, 30, 20, 0.25), "20 levels above: a quarter");
        assertEquals(0, GatherRules.xp(Double.NaN, 1, 1, 20, 0.25));
        assertEquals(8, GatherRules.xp(8, 10, 99, 0, 0.25), "no gap: never reduced");
        assertEquals(8, GatherRules.xp(8, 10, 99, 20, 7.0) , "a factor above 1 never multiplies");
        // Fractions carry over per skill and are never lost.
        GatherRules.Carry carry = new GatherRules.Carry();
        long total = 0;
        for (int i = 0; i < 8; i++) total += carry.add("Mining", 0.25);
        assertEquals(2, total, "eight quarter-XP blocks pay 2");
        assertEquals(0, carry.add("Mining", 0.5));
        assertEquals(0.5, carry.pending("Mining"), 1e-9);
        assertEquals(1, carry.add("Smithing", 1.0), "skills carry separately");
        assertEquals(0, carry.add("Mining", -1));
        assertEquals(0, carry.add(null, 5));
    }

    @Test
    void toolsAndMethods() {
        GatherRules.Tool iron = GatherRules.vanillaTool("IRON_PICKAXE");
        assertEquals(Set.of("pickaxe"), iron.kinds());
        assertEquals(2, iron.tier());
        assertEquals(0, GatherRules.vanillaTool("GOLDEN_PICKAXE").tier());
        assertEquals(1, GatherRules.vanillaTool("COPPER_PICKAXE").tier());
        assertEquals(4, GatherRules.vanillaTool("NETHERITE_PICKAXE").tier());
        assertEquals(Set.of("axe"), GatherRules.vanillaTool("DIAMOND_AXE").kinds(), "an axe, not a pickaxe");
        assertNull(GatherRules.vanillaTool("IRON_SWORD"));
        assertNull(GatherRules.vanillaTool(null));
        assertTrue(GatherRules.toolFits("pickaxe", 0, iron.kinds(), iron.tier()));
        assertFalse(GatherRules.toolFits("pickaxe", 3, iron.kinds(), iron.tier()), "tier too low");
        assertFalse(GatherRules.toolFits("drill", 0, iron.kinds(), iron.tier()), "wrong kind");
        assertTrue(GatherRules.toolFits(null, 0, null, 0), "no tool needed");
        assertTrue(GatherRules.Method.MANUAL.xp && GatherRules.Method.MANUAL.bonus);
        for (GatherRules.Method m : List.of(GatherRules.Method.CREATIVE, GatherRules.Method.EXPLOSION, GatherRules.Method.AUTOMATED)) {
            assertFalse(m.xp || m.bonus, m + " pays no XP and no bonus");
        }
    }

    @Test
    void fortuneIsBoundedAtOneExtraDropAndHalfTheTime() {
        assertEquals(0, GatherRules.bonusChance(0, 0.005, 0.5));
        assertEquals(0.25, GatherRules.bonusChance(50, 0.005, 0.5), 1e-9, "Mining 100 alone: Fortune 50");
        assertEquals(0.5, GatherRules.bonusChance(300, 0.005, 0.5), 1e-9, "capped");
        assertEquals(1.0, GatherRules.bonusChance(1e9, 1, 7), 1e-9, "never above 1, whatever the file says");
        assertEquals(0, GatherRules.bonusChance(Double.NaN, 0.005, 0.5));
        assertEquals(0, GatherRules.bonusChance(-5, 0.005, 0.5));
    }

    @Test
    void qualityOddsMoveWithSmithingAndRollFairly() {
        double[] at1 = {85, 13, 2, 0}, at100 = {20, 40, 28, 12};
        assertArrayEquals(at1, Quality.odds(1, at1, at100), 1e-9);
        assertArrayEquals(at100, Quality.odds(100, at1, at100), 1e-9);
        assertArrayEquals(at100, Quality.odds(150, at1, at100), 1e-9, "clamped above 100");
        double[] mid = Quality.odds(50, at1, at100);
        assertTrue(mid[3] > 0 && mid[3] < 12, "Masterwork possible but rare at 50");
        assertEquals(Quality.COMMON, Quality.roll(at1, 0.0));
        assertEquals(Quality.FINE, Quality.roll(at1, 0.86));
        assertEquals(Quality.SUPERIOR, Quality.roll(at1, 0.99));
        assertEquals(Quality.MASTERWORK, Quality.roll(at100, 0.995));
        assertEquals(Quality.COMMON, Quality.roll(new double[4], 0.7), "no odds: Common");
        assertEquals(Quality.COMMON, Quality.roll(at1, Double.NaN));
        // A level-1 smith never rolls Masterwork, whatever the roll.
        for (int i = 0; i < 1000; i++) assertNotEquals(Quality.MASTERWORK, Quality.roll(at1, i / 1000.0));
        assertEquals(Quality.FINE, Quality.parse("fine"));
        assertNull(Quality.parse("legendary"));
    }

    @Test
    void placedIndexPacksAndKeepsASortedSet() {
        int v = PlacedIndex.pack(-17, -64, 31, -64);
        assertEquals(15, PlacedIndex.x(v));
        assertEquals(15, PlacedIndex.z(v));
        assertEquals(-64, PlacedIndex.y(v, -64));
        int top = PlacedIndex.pack(5, 319, 2, -64);
        assertEquals(319, PlacedIndex.y(top, -64));
        assertTrue(top < (1 << 17));
        int[] set = PlacedIndex.add(null, 7);
        set = PlacedIndex.add(set, 3);
        set = PlacedIndex.add(set, 9);
        assertArrayEquals(new int[] {3, 7, 9}, set);
        assertSame(set, PlacedIndex.add(set, 7), "already there");
        assertTrue(PlacedIndex.contains(set, 9) && !PlacedIndex.contains(set, 8));
        set = PlacedIndex.remove(set, 7);
        assertArrayEquals(new int[] {3, 9}, set);
        assertSame(set, PlacedIndex.remove(set, 100));
        assertArrayEquals(new int[] {1, 2, 5}, PlacedIndex.clean(new int[] {5, -1, 2, 1, 2, 1 << 20}, 384), "a damaged save is cleaned");
        int[] fine = {1, 2, 5};
        assertSame(fine, PlacedIndex.clean(fine, 384), "a clean save is used as it is");
        int high = PlacedIndex.pack(0, 2000, 0, -64);
        assertArrayEquals(new int[] {high}, PlacedIndex.clean(new int[] {high}, 4064), "a taller world keeps its high blocks");
        assertFalse(PlacedIndex.contains(null, 1));
    }

    @Test
    void customResourcesToolsAndItemsAreJustData() throws Exception {
        // The synthetic extension test: a custom ore, a custom drop and a drill, with no code change.
        GatheringCatalog c = of("""
                items:
                  test_crystal: { material: AMETHYST_SHARD, name: "Test Crystal" }
                tools:
                  test_drill: { material: DIAMOND_PICKAXE, name: "Test Drill", kinds: [drill, pickaxe], tier: 5 }
                resources:
                  test_vein:
                    skill: Mining
                    blocks: [AMETHYST_BLOCK]
                    tool: drill
                    tier: 5
                    level: 50
                    xp: 75
                    drops: [ { item: test_crystal, min: 1, max: 2 } ]
                """);
        assertEquals(List.of(), c.problems());
        GatheringCatalog.ResourceDef r = c.resource(Material.AMETHYST_BLOCK);
        assertEquals("test_vein", r.id());
        assertTrue(r.replacesDrops());
        GatheringCatalog.ToolDef drill = c.tool("test_drill");
        assertTrue(GatherRules.toolFits(r.tool(), r.tier(), drill.kinds(), drill.tier()), "the drill gathers it");
        GatherRules.Tool pick = GatherRules.vanillaTool("NETHERITE_PICKAXE");
        assertFalse(GatherRules.toolFits(r.tool(), r.tier(), pick.kinds(), pick.tier()), "a vanilla pickaxe can't");
        assertEquals("Test Crystal", c.item("test_crystal").name());

        // Bad entries are refused one by one; the rest loads.
        GatheringCatalog bad = of("""
                resources:
                  ok:      { skill: Mining, blocks: [STONE], xp: 1 }
                  typo:    { skill: Mining, blocks: [STONEE], xp: 1 }
                  greedy:  { skill: Mining, blocks: [DIRT], xp: 1000000 }
                  twice:   { skill: Mining, blocks: [STONE], xp: 1 }
                  noskill: { blocks: [SAND], xp: 1 }
                  baddrop: { skill: Mining, blocks: [GRAVEL], drops: [ { item: nothing_here } ] }
                tools:
                  t: { material: NOT_A_THING, kinds: [x] }
                smelting:
                  xp: { IRON_INGOT: -4 }
                """);
        assertEquals(Set.of("ok"), bad.resources().keySet(), String.join("\n", bad.problems()));
        String all = String.join("\n", bad.problems());
        for (String s : List.of("typo", "greedy", "twice", "noskill", "baddrop", "tool t", "smelting IRON_INGOT")) {
            assertTrue(all.contains(s), s + " in: " + all);
        }
        assertEquals(0, bad.smeltXp(Material.IRON_INGOT));
        assertTrue(GatheringCatalog.empty().resources().isEmpty());
    }

    @Test
    void theRuleTable() {
        GatherRules.Method M = GatherRules.Method.MANUAL, C = GatherRules.Method.CREATIVE, A = GatherRules.Method.AUTOMATED;
        // decide(method, placed, custom, toolFits, vanillaDrops, silk, fortune)
        assertEquals(new GatherRules.Decision(true, true, false, false), GatherRules.decide(M, false, false, true, true, false, true), "a natural ore by hand: XP and the bonus");
        assertEquals(new GatherRules.Decision(true, false, false, false), GatherRules.decide(M, false, false, true, true, true, true), "Silk Touch: XP, no bonus");
        assertEquals(new GatherRules.Decision(true, false, false, false), GatherRules.decide(M, false, false, true, true, false, false), "stone: XP, never a bonus");
        assertEquals(GatherRules.Decision.NOTHING, GatherRules.decide(M, true, false, true, true, false, true), "placed: nothing");
        assertEquals(GatherRules.Decision.NOTHING, GatherRules.decide(C, false, false, true, true, false, true), "creative: nothing");
        assertEquals(GatherRules.Decision.NOTHING, GatherRules.decide(A, false, false, true, true, false, true), "a machine: drops only");
        assertEquals(GatherRules.Decision.NOTHING, GatherRules.decide(M, false, false, true, false, false, true), "vanilla drops nothing (iron ore, wooden pickaxe): nothing");
        assertEquals(GatherRules.Decision.NOTHING, GatherRules.decide(M, false, false, false, true, false, true), "the wrong tool for a vanilla resource: nothing, the break goes on");
        assertEquals(new GatherRules.Decision(false, false, false, true), GatherRules.decide(M, false, true, false, true, false, true), "a natural custom ore and the wrong tool: refused");
        assertEquals(new GatherRules.Decision(true, true, true, false), GatherRules.decide(M, false, true, true, false, false, true), "the right tool: XP, bonus, custom drops");
        assertEquals(GatherRules.Decision.NOTHING, GatherRules.decide(M, true, true, false, true, false, true), "a placed custom block: its vanilla drops, nothing else");
        assertEquals(new GatherRules.Decision(false, false, true, false), GatherRules.decide(A, false, true, true, true, false, true), "a machine with the right tool: custom drops, no XP");
        assertEquals(GatherRules.Decision.NOTHING, GatherRules.decide(C, false, true, false, true, false, true), "creative breaks anything, for nothing");
        assertEquals(GatherRules.Decision.NOTHING, GatherRules.decide(null, false, false, true, true, false, true));
    }

    @Test
    void skillNamesAreCheckedAndTrainingLinesComeFromTheFile() throws Exception {
        YamlConfiguration y = new YamlConfiguration();
        y.loadFromString("""
                resources:
                  ok:   { skill: mining, blocks: [STONE], xp: 1 }
                  typo: { skill: Minning, blocks: [DIRT], xp: 1 }
                smelting: { skill: Smithing, xp: { IRON_INGOT: 2 } }
                """);
        GatheringCatalog c = GatheringCatalog.from(y, s -> s.equalsIgnoreCase("mining") ? "Mining" : s.equals("Smithing") ? "Smithing" : null);
        assertEquals("Mining", c.resource(Material.STONE).skill(), "the skill's real name, whatever the case");
        assertNull(c.resource(Material.DIRT));
        assertTrue(String.join("\n", c.problems()).contains("unknown skill Minning"), c.problems().toString());

        java.util.Map<String, List<String>> lines = shipped().trainingLines();
        assertTrue(lines.get("Mining").get(0).contains("iron ore 8"), lines.get("Mining").toString());
        assertTrue(lines.get("Mining").get(1).contains("25%"), "the weak-resource rule");
        assertTrue(lines.get("Smithing").get(0).contains("iron ingot 2"), lines.get("Smithing").toString());
        assertTrue(lines.get("Smithing").stream().anyMatch(l -> l.contains("netherite") && l.contains("50 XP")));
    }

    @Test
    void qualityChancesForTheSkillPage() {
        double[] at1 = {85, 13, 2, 0}, at100 = {20, 40, 28, 12};
        assertEquals(1.0, Quality.chanceAtLeast(Quality.COMMON, 1, at1, at100), 1e-9);
        assertEquals(0.02, Quality.chanceAtLeast(Quality.SUPERIOR, 1, at1, at100), 1e-9);
        assertEquals(0.0, Quality.chanceAtLeast(Quality.MASTERWORK, 1, at1, at100), 1e-9);
        assertEquals(0.40, Quality.chanceAtLeast(Quality.SUPERIOR, 100, at1, at100), 1e-9);
        assertEquals(0.12, Quality.chanceAtLeast(Quality.MASTERWORK, 100, at1, at100), 1e-9);
        assertEquals(0.0, Quality.chanceAtLeast(Quality.FINE, 50, new double[4], new double[4]), "no odds: nothing");
    }

    @Test
    void forgedGearFeedsTheEngineOnlyWhereItCounts() {
        GatheringCatalog.QualityRules q = GatheringCatalog.empty().quality();
        q = new GatheringCatalog.QualityRules("Smithing", q.oddsAt1(), q.oddsAt100(), new double[] {0, 3, 6, 10},
                new double[] {0, 1, 2, 4}, new double[] {0, 3, 6, 10});
        EquipmentSource.Piece sword = new EquipmentSource.Piece(GatheringCatalog.Gear.WEAPON, Attributes.WeaponClass.SWORD, Quality.MASTERWORK, "Masterwork iron sword");
        EquipmentSource.Piece axe = new EquipmentSource.Piece(GatheringCatalog.Gear.WEAPON_AND_TOOL, Attributes.WeaponClass.AXE, Quality.FINE, "Fine iron axe");
        EquipmentSource.Piece pick = new EquipmentSource.Piece(GatheringCatalog.Gear.TOOL, Attributes.WeaponClass.OTHER, Quality.SUPERIOR, "Superior iron pickaxe");
        EquipmentSource.Piece helm = new EquipmentSource.Piece(GatheringCatalog.Gear.ARMOR, Attributes.WeaponClass.OTHER, Quality.FINE, "Fine iron helmet");
        EquipmentSource.Piece boots = new EquipmentSource.Piece(GatheringCatalog.Gear.ARMOR, Attributes.WeaponClass.OTHER, Quality.MASTERWORK, "Masterwork iron boots");

        List<Attributes.Contribution> s = EquipmentSource.contributions(new EquipmentSource.Worn(sword, List.of(helm, boots)), q);
        assertEquals(3, s.size(), s.toString());
        Attributes.Contribution power = s.get(0);
        assertEquals(Attributes.Attribute.POWER, power.attribute());
        assertEquals(10, power.amount());
        assertEquals(Attributes.WeaponClass.SWORD, power.weapon(), "Power for swords only");
        assertEquals(5, s.stream().filter(c -> c.attribute() == Attributes.Attribute.FORTITUDE).mapToDouble(Attributes.Contribution::amount).sum(), 1e-9, "1 + 4 Fortitude");

        List<Attributes.Contribution> a = EquipmentSource.contributions(new EquipmentSource.Worn(axe, List.of()), q);
        assertEquals(2, a.size(), "an axe is a weapon and a tool");
        assertEquals(3, a.stream().filter(c -> c.attribute() == Attributes.Attribute.FORTUNE).mapToDouble(Attributes.Contribution::amount).sum(), 1e-9);

        List<Attributes.Contribution> t = EquipmentSource.contributions(new EquipmentSource.Worn(pick, List.of()), q);
        assertEquals(List.of(Attributes.Attribute.FORTUNE), t.stream().map(Attributes.Contribution::attribute).toList(), "a pickaxe: Fortune only");
        assertEquals(6, t.get(0).amount());

        assertEquals(List.of(), EquipmentSource.contributions(EquipmentSource.Worn.NOTHING, q));
        // A held armor piece adds nothing; worn armor never counts as the weapon.
        assertEquals(List.of(), EquipmentSource.contributions(new EquipmentSource.Worn(helm, List.of()), q));
        // Through the engine: the caps hold whatever gear adds.
        Attributes.AttributeSnapshot snap = Attributes.AttributeCalculator.compute(
                EquipmentSource.contributions(new EquipmentSource.Worn(sword, List.of(boots, boots, boots, boots)), q), Attributes.AttributeRules.DEFAULTS);
        assertEquals(10, snap.power(Attributes.WeaponClass.SWORD), 1e-9);
        assertEquals(0, snap.power(Attributes.WeaponClass.AXE), 1e-9);
        assertEquals(16, snap.get(Attributes.Attribute.FORTITUDE), 1e-9);
    }

    @Test
    void metalUnitsAndTheSmeltedRule() throws Exception {
        assertEquals("iron", GatheringCatalog.metalOf("IRON_SWORD"));
        assertEquals("gold", GatheringCatalog.metalOf("GOLDEN_CHESTPLATE"));
        assertEquals("diamond", GatheringCatalog.metalOf("DIAMOND_PICKAXE"));
        assertEquals("copper", GatheringCatalog.metalOf("COPPER_SPEAR"));
        assertNull(GatheringCatalog.metalOf("IRON_BLOCK"));
        assertNull(GatheringCatalog.metalOf("STICK"));
        assertEquals(8, GatheringCatalog.unitsOf("IRON_CHESTPLATE"));
        assertEquals(1, GatheringCatalog.unitsOf("IRON_SPEAR"), "a spear is one unit and two sticks");
        assertEquals(0, GatheringCatalog.unitsOf("IRON_INGOT"));
        assertEquals("iron", GatheringCatalog.smeltedMetal("IRON_INGOT"));
        assertEquals("gold", GatheringCatalog.smeltedMetal("GOLD_INGOT"));
        assertNull(GatheringCatalog.smeltedMetal("IRON_NUGGET"), "melting gear down covers nothing");
        assertNull(GatheringCatalog.smeltedMetal("NETHERITE_SCRAP"));
        GatheringCatalog c = shipped();
        assertEquals(Set.of("iron", "gold", "copper"), c.needsSmelted());
        assertTrue(c.needsSmelted(Material.GOLDEN_HELMET));
        assertFalse(c.needsSmelted(Material.DIAMOND_SWORD), "no farm makes diamonds");
        assertEquals(5, c.forgeXp(Material.IRON_SPEAR), "spears forge too");
        assertTrue(of("forging: { needs-smelted: [] }").needsSmelted().isEmpty(), "an empty list pays for any metal");
        assertEquals(Set.of("iron", "gold", "copper"), of("forging: { per-unit: { iron: 5 } }").needsSmelted(), "an older file keeps the protection");
    }

    @Test
    void aResourceMustNameAToolKindThatExists() throws Exception {
        GatheringCatalog c = of("""
                tools:
                  drill: { material: DIAMOND_PICKAXE, kinds: [drill] }
                resources:
                  typo:   { skill: Mining, blocks: [STONE], tool: pickaxes, xp: 1 }
                  vanilla: { skill: Mining, blocks: [DIRT], tool: shovel, xp: 1 }
                  custom: { skill: Mining, blocks: [AMETHYST_BLOCK], tool: drill, xp: 1 }
                  any:    { skill: Mining, blocks: [SAND], xp: 1 }
                """);
        assertEquals(Set.of("vanilla", "custom", "any"), c.resources().keySet(), c.problems().toString());
        assertTrue(c.problems().get(0).contains("unknown tool kind pickaxes"), c.problems().toString());
    }

    @Test
    void gearKinds() {
        assertEquals(GatheringCatalog.Gear.WEAPON, GatheringCatalog.gear("IRON_SWORD"));
        assertEquals(GatheringCatalog.Gear.WEAPON_AND_TOOL, GatheringCatalog.gear("DIAMOND_AXE"));
        assertEquals(GatheringCatalog.Gear.TOOL, GatheringCatalog.gear("GOLDEN_PICKAXE"));
        assertEquals(GatheringCatalog.Gear.ARMOR, GatheringCatalog.gear("COPPER_BOOTS"));
        assertNull(GatheringCatalog.gear("IRON_BLOCK"));
    }
}
