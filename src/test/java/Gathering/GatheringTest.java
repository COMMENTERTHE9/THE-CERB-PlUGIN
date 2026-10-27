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
    void anEditedFileFromBeforeWoodcuttingGetsWhatTheUpdateAdded() throws Exception {
        YamlConfiguration shipped = new YamlConfiguration();
        shipped.loadFromString(java.nio.file.Files.readString(new File("src/main/resources/resources.yml").toPath()).replace("\r\n", "\n"));
        String older = """
                resources:
                  stone: { skill: Mining, blocks: [STONE], tool: pickaxe, level: 1, xp: 2 }
                  iron_ore: { skill: Mining, blocks: [IRON_ORE], tool: pickaxe, level: 10, xp: 8, fortune: true }
                weak-resources: { level-gap: 20, factor: 0.25 }
                fortune: { chance-per-point: 0.005, max-chance: 0.5 }
                tools: {}
                items: {}
                """;
        YamlConfiguration server = new YamlConfiguration();
        server.loadFromString(older);
        List<String> notes = GatheringCatalog.addUpdates(server, shipped);
        assertEquals(4, notes.size(), notes.toString());
        assertTrue(notes.get(0).startsWith("resources: oak, birch,") && notes.get(0).contains("write \"<id>: off\""), notes.get(0));
        assertTrue(notes.get(3).startsWith("fishing: not in this file"), "Fishing (2026-10-27) comes as a whole section: " + notes.get(3));
        GatheringCatalog c = GatheringCatalog.from(server);
        assertEquals(List.of(), c.problems());
        assertEquals(2, c.resource(Material.STONE).xp(), "the owner's own numbers stay");
        assertEquals(11, c.woods().size(), "every wood the update added");
        assertEquals(4, c.resource(Material.OAK_LOG).xp());
        assertNotNull(c.item("heartwood"));
        assertTrue(c.upgrades().keySet().containsAll(Set.of("heartwood_grip", "heartwood_limbs", "scaled_grip", "heartwood_handle", "leviathan_line")),
                "the woods' and the rods' upgrades: " + c.upgrades().keySet());
        assertTrue(c.fishing().enabled() && c.fishing().allSpecies().size() == 31, "Fishing came whole, its species with it");
        assertTrue(c.wood().capacity(10) > 0, "Felling from the code's defaults (no woodcutting: section in the file)");
        assertEquals(List.of(), GatheringCatalog.addUpdates(server, shipped), "nothing twice");

        // The owner leaves one out, or has an entry of their own: both stay as written.
        YamlConfiguration mine = new YamlConfiguration();
        mine.loadFromString(older.replace("items: {}", "items: {}\nupgrades:\n  heartwood_limbs: off\n")
                .replace("  iron_ore:", "  oak: off\n  birch: { skill: Woodcutting, blocks: [BIRCH_LOG], tool: axe, level: 1, xp: 9 }\n  iron_ore:"));
        GatheringCatalog.addUpdates(mine, shipped);
        GatheringCatalog m = GatheringCatalog.from(mine);
        assertEquals(List.of(), m.problems(), "off is quiet");
        assertNull(m.resource(Material.OAK_LOG));
        assertEquals(9, m.resource(Material.BIRCH_LOG).xp());
        assertTrue(m.upgrades().containsKey("heartwood_grip") && !m.upgrades().containsKey("heartwood_limbs"), "heartwood_limbs: off stays out");

        // An entry of the owner's own that already is a wood's logs, under another name: the update's wood stays out.
        YamlConfiguration logs = new YamlConfiguration();
        logs.loadFromString(older.replace("  iron_ore:", "  my_logs: { skill: Woodcutting, blocks: [SPRUCE_LOG, OAK_LOG], tool: axe, level: 1, xp: 3 }\n  iron_ore:"));
        List<String> said = GatheringCatalog.addUpdates(logs, shipped);
        GatheringCatalog l = GatheringCatalog.from(logs);
        assertEquals(List.of(), l.problems(), "no clash reported as a broken entry");
        assertEquals("my_logs", l.resource(Material.OAK_LOG).id());
        assertEquals("my_logs", l.resource(Material.SPRUCE_LOG).id());
        assertEquals("birch", l.resource(Material.BIRCH_LOG).id(), "the other woods still arrive");
        assertTrue(said.contains("resources.oak: not added, your file already has OAK_LOG"), said.toString());
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
    void woodsTechniquesHeartwoodAndUpgradesAreData() throws Exception {
        GatheringCatalog c = shipped();
        assertEquals(11, c.woods().size(), c.woods().stream().map(GatheringCatalog.ResourceDef::id).toList().toString());
        GatheringCatalog.ResourceDef oak = c.resource(Material.OAK_LOG), pale = c.resource(Material.PALE_OAK_LOG);
        assertEquals("oak", oak.id());
        assertTrue(oak.isTree() && oak.tree().leaves().contains(Material.AZALEA_LEAVES), "azalea trees are oak");
        assertEquals(Attributes.Yield.WOOD, oak.fortuneYield(), "fortune: true counts Woodcutting's own Fortune: wood");
        assertEquals(Attributes.Yield.ORE, c.resource(Material.IRON_ORE).fortuneYield(), "...and Mining's: ore");
        assertEquals(2, pale.tier(), "pale oak pays in full from an iron axe");
        assertEquals(10, pale.xp());
        assertTrue(c.resource(Material.CRIMSON_STEM).tree().leaves().contains(Material.NETHER_WART_BLOCK));
        assertEquals(3, c.resource(Material.CRIMSON_STEM).tree().trunk(), "some huge fungi grow 3x3 stems");
        assertEquals(2, oak.tree().trunk(), "a trunk is 1 or 2 wide unless the wood says otherwise");
        assertNull(c.resource(Material.STRIPPED_OAK_LOG), "stripped logs are made by players: no resource");
        assertNull(c.resource(Material.OAK_LEAVES), "leaves are part of a tree, not a resource");
        assertTrue(c.tracked(Material.OAK_LOG), "a placed log is remembered");
        assertEquals(0.25, c.weakToolFactor());
        WoodRules w = c.wood();
        assertEquals(WoodRules.DEFAULTS.fellingAt(), w.fellingAt(), "the shipped techniques are the built-in ones");
        assertEquals(300, w.restPerLogMs());
        assertEquals("heartwood", w.heartwoodItem());
        assertNotNull(c.item("heartwood"));
        GatheringCatalog.UpgradeDef grip = c.upgrade("heartwood_grip"), limbs = c.upgrade("heartwood_limbs");
        assertEquals(Set.of("axe"), grip.on());
        assertEquals(Attributes.Stat.CHOP_SPEED, grip.stat());
        assertEquals(Set.of("bow"), limbs.on());
        assertEquals(Attributes.Stat.DRAW_SPEED, limbs.stat());
        assertEquals(new WoodRules.Share(0.8, 0.8), w.grown(), "a tree left to grow from a sapling: 80% of the XP and heartwood");
        assertEquals(new WoodRules.Share(0.5, 0), w.boneMeal(), "one bone meal finished: half the XP, no heartwood");
        // The skill page's training lines speak of trees for a skill made of woods, and say what a grown tree pays.
        List<String> lines = c.trainingLines().get("Woodcutting");
        assertTrue(lines.get(0).startsWith("Cut the logs of trees with an axe"), lines.toString());
        assertTrue(lines.stream().anyMatch(l -> l.contains("pale oak: iron")), lines.toString());
        assertTrue(lines.stream().anyMatch(l -> l.startsWith("Trees grown from saplings pay 80% (with bone meal 50%); logs players place give nothing")),
                lines.toString());
    }

    @Test
    void grownTreesPayTheirShareAndAFileCanChangeIt() throws Exception {
        GatheringCatalog edited = of("""
                woodcutting:
                  grown: { xp: 1.0, heartwood: 0.5 }
                  bone-meal: { xp: 0.25 }
                """);
        assertTrue(edited.problems().stream().noneMatch(x -> x.startsWith("woodcutting.grown") || x.startsWith("woodcutting.bone-meal")),
                edited.problems().toString());
        assertEquals(new WoodRules.Share(1.0, 0.5), edited.wood().grown());
        assertEquals(new WoodRules.Share(0.25, 0), edited.wood().boneMeal(), "an unwritten heartwood keeps the default");
        GatheringCatalog older = of("""
                woodcutting:
                  felling: { at: { 10: 6 } }
                """);
        assertEquals(WoodRules.DEFAULTS.grown(), older.wood().grown(), "a file from before this update: the built-in shares");
        assertEquals(WoodRules.DEFAULTS.boneMeal(), older.wood().boneMeal());
        GatheringCatalog bad = of("""
                woodcutting:
                  grown: { xp: 1.5 }
                  bone-meal: { xp: -1, heartwood: 0 }
                """);
        assertEquals(WoodRules.DEFAULTS.grown(), bad.wood().grown(), "outside 0-1: the default, and a problem");
        assertEquals(WoodRules.DEFAULTS.boneMeal(), bad.wood().boneMeal());
        assertEquals(2, bad.problems().stream().filter(x -> x.startsWith("woodcutting.grown") || x.startsWith("woodcutting.bone-meal")).count(),
                bad.problems().toString());
    }

    @Test
    void whatPaysNothingByOrigin() throws Exception {
        GatheringCatalog c = shipped();
        GatheringCatalog.ResourceDef oak = c.resource(Material.OAK_LOG), iron = c.resource(Material.IRON_ORE);
        assertFalse(GatheringService.paysNothing(oak, Origin.NATURAL));
        assertTrue(GatheringService.paysNothing(oak, Origin.PLACED), "a log placed by hand");
        assertFalse(GatheringService.paysNothing(oak, Origin.GROWN), "a tree grown from a sapling pays its share");
        assertFalse(GatheringService.paysNothing(oak, Origin.BONEMEAL));
        assertFalse(GatheringService.paysNothing(iron, Origin.NATURAL));
        assertTrue(GatheringService.paysNothing(iron, Origin.PLACED));
        assertTrue(GatheringService.paysNothing(iron, Origin.GROWN), "only a tree's logs pay for growing");
        GatheringCatalog custom = of("""
                resources:
                  glowcap: { skill: Woodcutting, blocks: [MUSHROOM_STEM], tool: axe, xp: 5, drops: [ { item: STICK } ],
                             tree: { leaves: [RED_MUSHROOM_BLOCK] } }
                """);
        assertTrue(GatheringService.paysNothing(custom.resource(Material.MUSHROOM_STEM), Origin.GROWN),
                "a grown tree of a wood with custom drops: vanilla's drops only, as a placed one (no custom items from a farm)");
        assertFalse(GatheringService.paysNothing(custom.resource(Material.MUSHROOM_STEM), Origin.NATURAL));
        assertFalse(GatheringService.paysNothing(null, Origin.NATURAL));
        assertTrue(GatheringService.paysNothing(null, Origin.GROWN));
    }

    @Test
    void aBadWoodOrUpgradeIsRefusedOnItsOwn() throws Exception {
        GatheringCatalog c = of("""
                resources:
                  good: { skill: Woodcutting, blocks: [OAK_LOG], tool: axe, xp: 4, fortune: wood, tree: { leaves: [OAK_LEAVES] } }
                  leafless: { skill: Woodcutting, blocks: [BIRCH_LOG], tool: axe, xp: 4, tree: { leaves: [] } }
                  oddleaves: { skill: Woodcutting, blocks: [SPRUCE_LOG], tool: axe, xp: 4, tree: { leaves: [NOT_A_LEAF] } }
                  sure: { skill: Woodcutting, blocks: [JUNGLE_LOG], tool: axe, xp: 4, fortune: gems, tree: { leaves: [JUNGLE_LEAVES] } }
                  lucky: { skill: Mining, blocks: [COAL_ORE], tool: pickaxe, xp: 4, fortune: general }
                items:
                  heartwood: { material: STRIPPED_DARK_OAK_WOOD, name: Heartwood }
                upgrades:
                  grip: { item: heartwood, for: [axe], stat: chop-speed, amount: 0.08 }
                  nothing: { item: heartwood, for: [spoon], stat: chop-speed, amount: 0.08 }
                  toomuch: { item: heartwood, for: [bow], stat: draw-speed, amount: 5 }
                  power: { item: heartwood, for: [bow], stat: damage, amount: 0.1 }
                  ghost: { item: nothing, for: [bow], stat: draw-speed, amount: 0.1 }
                woodcutting:
                  felling: { at: { 5: 3, 0: 9, 20: 2000 } }
                  heartwood: { item: missing }
                """);
        assertEquals(Set.of("good", "lucky"), c.resources().keySet(), String.join("\n", c.problems()));
        assertNull(c.resource(Material.COAL_ORE).fortuneYield(), "fortune: general counts only Fortune that counts everywhere");
        assertEquals(Set.of("grip"), c.upgrades().keySet(), String.join("\n", c.problems()));
        assertEquals(3, c.wood().capacity(5), "the valid felling step stays; level 0 and 2000 logs are refused");
        assertEquals(0, c.wood().capacity(4));
        assertNull(c.wood().heartwoodItem(), "an unknown heartwood item: no heartwood drops");
        String all = String.join("\n", c.problems());
        for (String bad : List.of("leafless", "oddleaves", "sure", "nothing", "toomuch", "power", "ghost", "felling.at")) assertTrue(all.contains(bad), bad + " in " + all);
    }

    @Test
    void forgedToolsCountWhereTheyGatherAndUpgradesWhileHeld() {
        GatheringCatalog.QualityRules q = GatheringCatalog.empty().quality();
        q = new GatheringCatalog.QualityRules("Smithing", q.oddsAt1(), q.oddsAt100(), new double[] {0, 3, 6, 10},
                new double[] {0, 1, 2, 4}, new double[] {0, 3, 6, 10});
        java.util.Map<String, GatheringCatalog.UpgradeDef> ups = java.util.Map.of("grip",
                new GatheringCatalog.UpgradeDef("grip", "heartwood", Set.of("axe"), Attributes.Stat.CHOP_SPEED, 0.08, "Heartwood grip"));
        EquipmentSource.Piece axe = new EquipmentSource.Piece(GatheringCatalog.Gear.WEAPON_AND_TOOL, Attributes.WeaponClass.AXE, Quality.FINE,
                Attributes.Yield.WOOD, "axe", List.of("grip"), "Fine iron axe");
        EquipmentSource.Piece pick = new EquipmentSource.Piece(GatheringCatalog.Gear.TOOL, Attributes.WeaponClass.OTHER, Quality.SUPERIOR,
                Attributes.Yield.ORE, "pickaxe", List.of(), "Superior iron pickaxe");
        Attributes.AttributeSnapshot a = Attributes.AttributeCalculator.compute(EquipmentSource.contributions(new EquipmentSource.Worn(axe, List.of()), q, ups),
                Attributes.AttributeRules.DEFAULTS);
        assertEquals(3, a.fortune(Attributes.Yield.WOOD), 1e-9, "a forged axe's Fortune counts on wood");
        assertEquals(0, a.fortune(Attributes.Yield.ORE), 1e-9, "...not on ore");
        assertEquals(0.08, a.secondaryRaw(Attributes.Stat.CHOP_SPEED), 1e-9, "the grip adds chopping speed");
        Attributes.AttributeSnapshot p = Attributes.AttributeCalculator.compute(EquipmentSource.contributions(new EquipmentSource.Worn(pick, List.of()), q, ups),
                Attributes.AttributeRules.DEFAULTS);
        assertEquals(6, p.fortune(Attributes.Yield.ORE), 1e-9, "a forged pickaxe's on ore");
        assertEquals(0, p.fortune(Attributes.Yield.WOOD), 1e-9);
        // An upgrade the file no longer has (or a stat it no longer names) adds nothing.
        assertEquals(1, EquipmentSource.contributions(new EquipmentSource.Worn(axe, List.of()), q, java.util.Map.of()).size() - 1,
                "without its definition the grip adds nothing (Power and Fortune stay)");
        EquipmentSource.Piece plainAxe = new EquipmentSource.Piece(null, Attributes.WeaponClass.AXE, null, Attributes.Yield.WOOD, "axe", List.of("grip"), "Iron axe");
        assertEquals(1, EquipmentSource.contributions(new EquipmentSource.Worn(plainAxe, List.of()), q, ups).size(), "an upgrade adds its stat to an unforged item");
        EquipmentSource.Piece bow = new EquipmentSource.Piece(null, Attributes.WeaponClass.BOW, null, null, "bow", List.of("grip"), "Bow");
        assertEquals(0, EquipmentSource.contributions(new EquipmentSource.Worn(bow, List.of()), q, ups).size(), "a grip on a bow (the file says axes) adds nothing");
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
