package Gathering;

import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** Player Menu → Recipes lists what the game registers (RecipeBook): items, bait and reel, tool upgrades made in a grid. */
class RecipeBookTest {

    private static YamlConfiguration shippedYaml() throws Exception {
        YamlConfiguration y = new YamlConfiguration();
        y.loadFromString(java.nio.file.Files.readString(new File("src/main/resources/resources.yml").toPath()).replace("\r\n", "\n"));
        return y;
    }

    private static RecipeBook.Entry find(List<RecipeBook.Entry> all, String id) {
        return all.stream().filter(e -> e.id().equals(id)).findFirst().orElse(null);
    }

    @Test
    void theSowersCharmAndTheReplantingHoeAreListedWithTheirIngredients() throws Exception {
        List<RecipeBook.Entry> all = RecipeBook.crafting(GatheringCatalog.from(shippedYaml()), m -> true);

        RecipeBook.Entry charm = find(all, "sowers_charm");
        assertNotNull(charm);
        assertEquals(RecipeBook.Kind.ITEM, charm.kind());
        assertEquals("Sower's Charm", charm.name());
        assertEquals(List.of("wheat seeds", "beetroot seeds", "melon seeds or pumpkin seeds", "bone meal", "string", "gold nugget"),
                charm.ingredients());
        assertFalse(charm.fitsInventoryGrid(), "six ingredients need a crafting table");
        assertNull(charm.unlockedBy());
        assertFalse(charm.about().isEmpty(), "the charm's own lore says what it is for");

        RecipeBook.Entry replant = find(all, "replanting");
        assertNotNull(replant);
        assertEquals(RecipeBook.Kind.UPGRADE, replant.kind());
        assertEquals(List.of("Sower's Charm", "any hoe"), replant.ingredients());
        assertEquals(Material.IRON_HOE, replant.icon());
        assertEquals("ripe crops it harvests are planted again", replant.gives());
        assertTrue(replant.fitsInventoryGrid());

        RecipeBook.Entry grip = find(all, "heartwood_grip");
        assertNotNull(grip);
        assertEquals(Material.IRON_AXE, grip.icon());
        assertEquals("chopping speed +8% while held", grip.gives());
        assertEquals(Material.BOW, find(all, "heartwood_limbs").icon());
    }

    @Test
    void baitAndTheReelAreListedAndAMilestoneRecipeSaysWhatUnlocksIt() throws Exception {
        List<RecipeBook.Entry> all = RecipeBook.crafting(GatheringCatalog.from(shippedYaml()), m -> true);
        RecipeBook.Entry grub = find(all, "grub_bait");
        assertEquals(RecipeBook.Kind.TACKLE, grub.kind());
        assertEquals(4, grub.makes());
        assertNull(grub.unlockedBy());
        assertEquals("waters_3", find(all, "wayfarers_bait").unlockedBy());
        assertEquals(List.of("cod or salmon", "bone meal"), find(all, "chum").ingredients());
        assertFalse(find(all, "auto_hook_reel").fitsInventoryGrid());
        // The file's order: items, then bait and reel, then upgrades.
        List<RecipeBook.Kind> kinds = all.stream().map(RecipeBook.Entry::kind).toList();
        assertEquals(kinds.stream().sorted().toList(), kinds);
    }

    @Test
    void rodPartsAreFittedNotCraftedWhileFishingEquipmentIsOn() throws Exception {
        List<RecipeBook.Entry> on = RecipeBook.crafting(GatheringCatalog.from(shippedYaml()), m -> true);
        assertTrue(on.stream().noneMatch(e -> e.icon() == Material.FISHING_ROD), "no rod upgrade is a crafting recipe");
        assertNull(find(on, "scaled_grip"));

        YamlConfiguration off = shippedYaml();
        off.set("tackle", false); // "tackle: off": rods are upgraded in a crafting grid again, and there is no bait
        GatheringCatalog c = GatheringCatalog.from(off);
        List<RecipeBook.Entry> all = RecipeBook.crafting(c, m -> true);
        RecipeBook.Entry scaled = find(all, "scaled_grip");
        assertNotNull(scaled);
        assertEquals(Material.FISHING_ROD, scaled.icon());
        assertEquals(List.of("Lurker Scale", "any rod"), scaled.ingredients());
        assertTrue(all.stream().noneMatch(e -> e.kind() == RecipeBook.Kind.TACKLE));
        for (GatheringCatalog.UpgradeDef u : c.upgrades().values()) {
            assertEquals(c.madeInGrid(u), find(all, u.id()) != null, u.id());
        }
    }

    @Test
    void aRecipeTheServerLeavesOutIsLeftOutHereToo() throws Exception {
        YamlConfiguration y = shippedYaml();
        y.createSection("recipes.no_such_item", Map.of("ingredients", List.of("STICK"), "makes", 1));
        GatheringCatalog c = GatheringCatalog.from(y);
        assertNull(find(RecipeBook.crafting(c, m -> true), "no_such_item"), "a recipe with no item to make is never listed");

        // An ingredient the server's registry says is not an item (a block such as carrots or water).
        List<RecipeBook.Entry> all = RecipeBook.crafting(c, m -> m != Material.WHEAT_SEEDS);
        assertNull(find(all, "sowers_charm"));
        assertNull(find(all, "grub_bait"));
        assertNotNull(find(all, "chum"));
        assertNotNull(find(all, "replanting"), "an upgrade's ingredients are the tool and its item");
    }
}
