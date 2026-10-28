package Gathering;

import Attributes.Attribute;
import Attributes.Contribution;
import Attributes.Stat;
import org.bukkit.Material;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/** Alchemy's rules (docs/design/ALCHEMY.md): brews, tiers and their lift, potency and time, the shipped file. */
class AlchemyRulesTest {

    private static GatheringCatalog shipped() throws Exception {
        YamlConfiguration y = new YamlConfiguration();
        y.loadFromString(java.nio.file.Files.readString(new File("src/main/resources/resources.yml").toPath()).replace("\r\n", "\n"));
        return GatheringCatalog.from(y);
    }

    private static AlchemyRules parse(String yaml, List<String> problems) throws Exception {
        YamlConfiguration y = new YamlConfiguration();
        y.loadFromString(yaml);
        return AlchemyRules.from(y.getConfigurationSection("alchemy"), s -> s.equals("Alchemy") ? s : null, Set.of("bloodroot", "sunpetal"),
                AlchemyRules.VANILLA_INGREDIENTS, problems);
    }

    @Test
    void theShippedBrewsLoadCleanly() throws Exception {
        GatheringCatalog c = shipped();
        assertEquals(List.of(), c.problems());
        AlchemyRules r = c.alchemy();
        assertTrue(r.enabled());
        assertEquals("Alchemy", r.skill());
        assertEquals(8, r.brews().size(), "the eight brews of docs/design/ALCHEMY.md §3");
        assertEquals(2, r.vanillaXp());
        AlchemyRules.Brew mending = r.brew("mending");
        assertEquals(AlchemyRules.Kind.DRAUGHT, mending.kind());
        assertEquals(10, mending.level());
        assertEquals(Material.POPPY, mending.reagent());
        assertEquals("vigor", r.byRare("sunpetal").id(), "Sunpetal lifts the Elixir of Vigor");
        assertEquals("bloodroot", r.byRare("bloodroot").id(), "Bloodroot is the Bloodroot Draught's own reagent");
        assertEquals("moonlit", r.byRare("moonpetal").id());
        assertEquals("might", r.byRare("emberbloom").id());
        for (AlchemyRules.Brew b : r.brews()) {
            assertTrue(b.xp() > 0 && b.level() >= 10, b.id());
            if (b.reagent() != null) {
                assertFalse(AlchemyRules.VANILLA_INGREDIENTS.contains(b.reagent()), b.id() + ": never a vanilla brewing ingredient");
                assertNotNull(c.herbalism().herb(b.reagent()), b.id() + ": its reagent is a herb Herbalism gathers");
            } else {
                assertNotNull(c.herbalism().rare(b.rare()), b.id() + ": its rare reagent is Herbalism's");
            }
        }
        assertEquals(10, r.brews().stream().mapToInt(AlchemyRules.Brew::level).min().orElse(0), "the first brew at Alchemy 10");
    }

    @Test
    void tiersFromLevelAndOneUpFromTheReagent() throws Exception {
        AlchemyRules r = shipped().alchemy();
        assertEquals(Quality.COMMON, r.tierAt(24));
        assertEquals(Quality.FINE, r.tierAt(25));
        assertEquals(Quality.SUPERIOR, r.tierAt(50));
        assertEquals(Quality.MASTERWORK, r.tierAt(75));
        assertEquals(Quality.COMMON, r.tier(20, null, false));
        assertEquals(Quality.FINE, r.tier(20, Quality.FINE, false), "a Fine reagent lifts one tier");
        assertEquals(Quality.FINE, r.tier(20, Quality.MASTERWORK, false), "at most one tier");
        assertEquals(Quality.COMMON, r.tier(20, Quality.COMMON, false), "a Common reagent lifts nothing");
        assertEquals(Quality.SUPERIOR, r.tier(30, null, true), "the lifting rare reagent: one tier");
        assertEquals(Quality.SUPERIOR, r.tier(30, Quality.FINE, true), "a quality reagent and the lift together: still one tier");
        assertEquals(Quality.MASTERWORK, r.tier(90, Quality.SUPERIOR, true), "Masterwork stays Masterwork");
    }

    @Test
    void potencyTimeAndHealingByTier() throws Exception {
        AlchemyRules r = shipped().alchemy();
        AlchemyRules.Brew vigor = r.brew("vigor"), mending = r.brew("mending");
        assertEquals(8 * 60_000L, r.durationMs(vigor, Quality.COMMON));
        assertEquals(16 * 60_000L, r.durationMs(vigor, Quality.MASTERWORK));
        assertEquals(0, r.durationMs(mending, Quality.MASTERWORK), "a draught has no time");
        assertEquals(0.25, r.restoreShare(mending, Quality.COMMON), 1e-12);
        assertEquals(0.40, r.restoreShare(mending, Quality.MASTERWORK), 1e-12);
        assertEquals(0.64, r.restoreShare(r.brew("bloodroot"), Quality.MASTERWORK), 1e-12);
        List<Contribution> c = Alchemy.contributions(vigor, Quality.SUPERIOR, r);
        assertEquals(1, c.size());
        assertEquals(Attribute.VITALITY, c.get(0).attribute());
        assertEquals(8 * 1.4, c.get(0).amount(), 1e-9);
        List<Contribution> moon = Alchemy.contributions(r.brew("moonlit"), Quality.COMMON, r);
        assertEquals(3, moon.size(), "Vitality, Fortitude and Essence");
        List<Contribution> swift = Alchemy.contributions(r.brew("swiftness"), Quality.FINE, r);
        assertEquals(Stat.MOVE_SPEED, swift.get(0).stat());
        assertEquals(0.08 * 1.2, swift.get(0).amount(), 1e-12);
        assertTrue(c.get(0).sourceId().startsWith(Alchemy.SOURCE_PREFIX), "an elixir's own source, apart from a meal's");
    }

    @Test
    void brewsAreAnnouncedAtTheirLevels() throws Exception {
        AlchemyRules r = shipped().alchemy();
        assertTrue(r.openedBetween(9, 10).get(0).startsWith("Draught of Mending"));
        assertTrue(r.openedBetween(24, 25).stream().anyMatch(s -> s.startsWith("Elixir of Stoneskin")));
        assertTrue(r.openedBetween(24, 25).stream().anyMatch(s -> s.startsWith("Fine brews")));
        assertEquals(List.of(), r.openedBetween(60, 74));
    }

    @Test
    void withHerbalismOffTheBrewsOfVanillaHerbsStayWithoutTheirLift() throws Exception {
        // Herbalism off: no rare reagents. The brews made from a rare reagent go; those a vanilla herb makes stay, and lose
        // only the lift a rare reagent gave them (the Elixirs of Vigor and Might, docs/design/ALCHEMY.md §3).
        YamlConfiguration y = new YamlConfiguration();
        y.loadFromString(java.nio.file.Files.readString(new File("src/main/resources/resources.yml").toPath()).replace("\r\n", "\n")
                .replaceFirst("(?m)^herbalism:\\n", "herbalism: off\nold-herbalism:\n"));
        GatheringCatalog c = GatheringCatalog.from(y);
        assertFalse(c.herbalism().enabled());
        AlchemyRules r = c.alchemy();
        assertNotNull(r.brew("vigor"), String.join("\n", c.problems()));
        assertNotNull(r.brew("might"));
        assertNull(r.brew("vigor").lift());
        assertNull(r.brew("bloodroot"), "made from a rare reagent: gone with Herbalism");
        assertTrue(c.problems().stream().anyMatch(p -> p.contains("brew vigor: unknown lift sunpetal")), String.join("\n", c.problems()));
    }

    @Test
    void onlyAClickThatPutsSomethingInArmsAStand() {
        // docs/design/ALCHEMY.md §2: the player whose click starts a brew is its alchemist; a click that puts nothing into
        // the stand (an empty slot, taking out, outside the window) claims nothing, so a stand hoppers feed brews for nobody.
        assertTrue(Alchemy.putsInto(InventoryAction.PLACE_ALL, true, false, false));
        assertTrue(Alchemy.putsInto(InventoryAction.PLACE_ONE, true, false, false));
        assertTrue(Alchemy.putsInto(InventoryAction.SWAP_WITH_CURSOR, true, false, false));
        assertTrue(Alchemy.putsInto(InventoryAction.HOTBAR_SWAP, true, true, false), "a number key bringing an item");
        assertFalse(Alchemy.putsInto(InventoryAction.HOTBAR_SWAP, true, false, false), "a number key taking one out");
        assertTrue(Alchemy.putsInto(InventoryAction.MOVE_TO_OTHER_INVENTORY, false, true, true), "a shift-click of a bottle from your inventory");
        assertFalse(Alchemy.putsInto(InventoryAction.MOVE_TO_OTHER_INVENTORY, false, true, false), "a shift-click of something the stand won't take");
        assertFalse(Alchemy.putsInto(InventoryAction.MOVE_TO_OTHER_INVENTORY, true, true, true), "a shift-click out of the stand");
        assertFalse(Alchemy.putsInto(InventoryAction.PLACE_ALL, false, false, false), "a click in your own inventory");
        for (InventoryAction a : List.of(InventoryAction.NOTHING, InventoryAction.PICKUP_ALL, InventoryAction.PICKUP_HALF,
                InventoryAction.COLLECT_TO_CURSOR, InventoryAction.DROP_ALL_CURSOR, InventoryAction.CLONE_STACK, InventoryAction.UNKNOWN)) {
            assertFalse(Alchemy.putsInto(a, true, true, true), a + " puts nothing in");
        }
    }

    @Test
    void badBrewsAreRefusedOnTheirOwn() throws Exception {
        List<String> problems = new ArrayList<>();
        AlchemyRules r = parse("""
                alchemy:
                  skill: Alchemy
                  brews:
                    good:     { kind: elixir, reagent: POPPY, minutes: 5, effects: [ { attribute: vitality, amount: 4 } ] }
                    twice:    { kind: elixir, reagent: POPPY, minutes: 5, effects: [ { attribute: vitality, amount: 4 } ] }
                    vanilla:  { kind: elixir, reagent: SUGAR, minutes: 5, effects: [ { attribute: vitality, amount: 4 } ] }
                    nothing:  { kind: elixir, reagent: NOT_AN_ITEM, minutes: 5, effects: [ { attribute: vitality, amount: 4 } ] }
                    healer:   { kind: draught, reagent: DANDELION, effects: [ { attribute: vitality, amount: 4 } ] }
                    rare:     { kind: draught, reagent: bloodroot, restore: { share: 0.3, seconds: 4 } }
                    lifted:   { kind: elixir, reagent: ALLIUM, lift: ghost, minutes: 5, effects: [ { attribute: power, amount: 2 } ] }
                    colour:   { kind: elixir, reagent: CORNFLOWER, color: blue, minutes: 5, effects: [ { attribute: power, amount: 2 } ] }
                    off: off
                """, problems);
        assertEquals(Set.of("good", "rare", "lifted"), Set.copyOf(r.brews().stream().map(AlchemyRules.Brew::id).toList()), String.join("\n", problems));
        String all = String.join("\n", problems);
        for (String bad : List.of("twice", "vanilla", "nothing", "healer", "lifted", "colour")) assertTrue(all.contains("brew " + bad), bad + " in " + all);
        assertTrue(all.contains("unknown lift ghost (a rare reagent) - the brew stays, without its lift"), all);
        assertNull(r.brew("lifted").lift(), "an unknown lift is left out; the brew is made from its own reagent");
        assertTrue(all.contains("SUGAR is one of vanilla brewing's own ingredients"));
        assertEquals("rare", r.byRare("bloodroot").id());
        List<String> none = new ArrayList<>();
        assertFalse(parse("alchemy:\n  skill: Nobody\n", none).enabled(), "an unknown skill: alchemy off");
        assertFalse(AlchemyRules.NONE.enabled());
        assertEquals(0xC0392B, AlchemyRules.parseColor("#C0392B"));
        assertEquals(-1, AlchemyRules.parseColor("blue"));
    }
}
