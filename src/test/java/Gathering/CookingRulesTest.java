package Gathering;

import Attributes.Attribute;
import Attributes.Contribution;
import Attributes.Stat;
import Attributes.Yield;
import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Cooking's pure rules (docs/design/FARMING_COOKING.md §4): XP tables, meal tiers, effects, the meal codec, parsing. */
class CookingRulesTest {

    private static GatheringCatalog shipped() throws Exception {
        YamlConfiguration y = new YamlConfiguration();
        y.loadFromString(java.nio.file.Files.readString(new File("src/main/resources/resources.yml").toPath()).replace("\r\n", "\n"));
        return GatheringCatalog.from(y);
    }

    @Test
    void theShippedMealsLoadCleanly() throws Exception {
        GatheringCatalog c = shipped();
        assertEquals(List.of(), c.problems());
        CookingRules r = c.cooking();
        assertTrue(r.enabled());
        assertEquals("Cooking", r.skill());
        assertEquals(6, r.meals().size());
        CookingRules.Meal stew = r.meal("hearty_stew");
        assertEquals(10, stew.level(), "the first meal at Cooking 10");
        assertEquals(Material.RABBIT_STEW, stew.item());
        assertEquals(5, stew.recipe().size());
        assertTrue(stew.recipe().get(1).containsAll(List.of(Material.COOKED_BEEF, Material.COOKED_CHICKEN)), "#cooked_meat");
        assertNotNull(stew.restore());
        assertEquals(10, r.meals().stream().mapToInt(CookingRules.Meal::level).min().orElse(0));
        // Meals are made on items vanilla makes in a furnace or rarely crafts, so the grid looks a recipe up rarely.
        for (CookingRules.Meal m : r.meals()) {
            assertFalse(List.of(Material.BREAD, Material.COOKIE, Material.CAKE).contains(m.item()), m.id() + " on a common craft");
            assertTrue(m.xp() > 0 && m.minutes() > 0 && (!m.effects().isEmpty() || m.restore() != null), m.id());
            assertTrue(m.recipe().stream().flatMap(List::stream).anyMatch(x -> CookingRules.qualityIngredient(x, c.farming())),
                    m.id() + " has a crop ingredient a quality can lift");
        }
        assertEquals(1, r.meals().stream().map(CookingRules.Meal::item).distinct().count() == r.meals().size() ? 1 : 0, "one base item per meal");
    }

    @Test
    void xpTablesPayFoodAndNeverReversibleStorage() throws Exception {
        CookingRules r = shipped().cooking();
        assertEquals(3, r.furnaceXp(Material.COOKED_BEEF));
        assertEquals(4, r.furnaceXp(Material.COOKED_SALMON));
        assertEquals(0.25, r.furnaceXp(Material.DRIED_KELP));
        assertEquals(0, r.furnaceXp(Material.IRON_INGOT), "metal is Smithing's");
        assertEquals(4, r.craftXp(Material.BREAD));
        assertEquals(1, r.craftXp(Material.COOKIE));
        assertEquals(0, r.craftXp(Material.DRIED_KELP), "dried kelp from a dried kelp block is storage, not cooking");
        assertEquals(0, r.craftXp(Material.WHEAT), "nor wheat from a hay bale");
        assertEquals(0, r.craftXp(Material.HONEY_BOTTLE));
    }

    @Test
    void tiersFromLevelAndQualityIngredients() throws Exception {
        CookingRules r = shipped().cooking();
        assertEquals(Quality.COMMON, r.tierAt(24));
        assertEquals(Quality.FINE, r.tierAt(25));
        assertEquals(Quality.SUPERIOR, r.tierAt(50));
        assertEquals(Quality.MASTERWORK, r.tierAt(75));
        assertEquals(Quality.COMMON, r.tier(10, 3, 1), "1 of 3 crops with a quality: under half");
        assertEquals(Quality.FINE, r.tier(10, 3, 2), "2 of 3: half or more lifts a tier");
        assertEquals(Quality.FINE, r.tier(10, 2, 1), "exactly half");
        assertEquals(Quality.SUPERIOR, r.tier(25, 1, 1));
        assertEquals(Quality.MASTERWORK, r.tier(75, 3, 3), "never past Masterwork");
        assertEquals(Quality.COMMON, r.tier(10, 0, 0), "no crop ingredients: no lift");
    }

    @Test
    void potencyAndDurationScaleEveryNumber() throws Exception {
        CookingRules r = shipped().cooking();
        CookingRules.Meal stew = r.meal("hearty_stew"), feast = r.meal("warriors_feast"), ration = r.meal("trail_ration");
        assertEquals(600_000, r.durationMs(stew, Quality.COMMON));
        assertEquals(1_200_000, r.durationMs(stew, Quality.MASTERWORK));
        List<Contribution> common = Cooking.contributions(stew, Quality.COMMON, r);
        assertEquals(1, common.size());
        assertEquals(Attribute.VITALITY, common.get(0).attribute());
        assertEquals(6, common.get(0).amount(), 1e-9);
        assertEquals(9.6, Cooking.contributions(stew, Quality.MASTERWORK, r).get(0).amount(), 1e-9);
        List<Contribution> f = Cooking.contributions(feast, Quality.FINE, r);
        assertEquals(3.6, f.stream().filter(x -> x.attribute() == Attribute.POWER).findFirst().orElseThrow().amount(), 1e-9);
        assertNull(f.get(0).weapon(), "Power for every weapon");
        Contribution speed = Cooking.contributions(ration, Quality.SUPERIOR, r).get(0);
        assertEquals(Stat.MOVE_SPEED, speed.stat());
        assertEquals(0.112, speed.amount(), 1e-9);
        Contribution fortune = Cooking.contributions(r.meal("farmers_breakfast"), Quality.COMMON, r).get(0);
        assertEquals(Attribute.FORTUNE, fortune.attribute());
        assertEquals(Yield.CROP, fortune.yield(), "Fortune on crops only");
        assertTrue(Cooking.effectLines(stew, Quality.FINE, r).contains("Restores 30% health over 20 s"), Cooking.effectLines(stew, Quality.FINE, r).toString());
    }

    @Test
    void theRunningMealSurvivesItsOwnCodec() {
        Cooking.Active a = new Cooking.Active("hearty_stew", Quality.SUPERIOR, 1_700_000_000_000L);
        assertEquals(a, Cooking.Active.decode(a.encode()));
        assertNull(Cooking.Active.decode(null));
        assertNull(Cooking.Active.decode("2|hearty_stew|fine|5"), "a later version's format is not read as this one");
        assertNull(Cooking.Active.decode("1|hearty_stew|shiny|5"));
        assertNull(Cooking.Active.decode("1|hearty_stew|fine|soon"));
    }

    @Test
    void badMealsAreRefusedOneByOne() throws Exception {
        YamlConfiguration y = new YamlConfiguration();
        y.loadFromString("""
                cooking:
                  skill: Cooking
                  tiers: { fine: 50, superior: 25, masterwork: 75 }
                  potency: { common: 1.0, fine: 0.5 }
                  meals:
                    good:    { item: COOKED_BEEF, level: 1, xp: 5, recipe: [CARROT], effects: [ { attribute: vitality, amount: 2 } ] }
                    nogroup: { item: COOKED_BEEF, level: 1, xp: 5, recipe: ["#cooked_bread"], effects: [ { attribute: vitality, amount: 2 } ] }
                    noeffect: { item: COOKED_BEEF, level: 1, xp: 5, recipe: [CARROT] }
                    wrongstat: { item: COOKED_BEEF, level: 1, xp: 5, recipe: [CARROT], effects: [ { stat: damage, amount: 0.1 } ] }
                    badyield: { item: COOKED_BEEF, level: 1, xp: 5, recipe: [CARROT], effects: [ { attribute: power, amount: 2, yield: crops } ] }
                    toolong: { item: COOKED_BEEF, level: 1, xp: 5, recipe: [CARROT, CARROT, CARROT, CARROT, CARROT, CARROT, CARROT, CARROT, CARROT, CARROT], effects: [ { attribute: vitality, amount: 2 } ] }
                    gone:    off
                """);
        GatheringCatalog c = GatheringCatalog.from(y);
        CookingRules r = c.cooking();
        assertEquals(1, r.meals().size(), c.problems().toString());
        for (String bad : List.of("nogroup", "noeffect", "wrongstat", "badyield", "toolong")) {
            assertTrue(c.problems().stream().anyMatch(p -> p.contains(bad)), bad + ": " + c.problems());
        }
        assertTrue(c.problems().stream().anyMatch(p -> p.contains("cooking tiers")), "falling tiers are refused");
        assertEquals(Quality.FINE, r.tierAt(25), "the defaults stand in");
        assertTrue(c.problems().stream().anyMatch(p -> p.contains("potency")), "a lower potency for a higher tier is refused");
        assertEquals(1.2, r.potency(Quality.FINE), 1e-9);
        assertFalse(c.problems().stream().anyMatch(p -> p.contains("gone")), "off is quiet");
    }

    @Test
    void offOrMissingTurnsCookingOff() throws Exception {
        YamlConfiguration y = new YamlConfiguration();
        y.loadFromString("cooking: off\n");
        assertFalse(GatheringCatalog.from(y).cooking().enabled());
        assertFalse(GatheringCatalog.from(new YamlConfiguration()).cooking().enabled());
    }
}
