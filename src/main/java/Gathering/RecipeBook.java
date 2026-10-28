package Gathering;

import org.bukkit.Material;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Predicate;

/**
 * Every Cerberus crafting recipe as the game registers it, for Player Menu → Recipes: the items in {@code recipes:} (the
 * Sower's Charm), Fishing's bait and reel ({@code tackle.recipes:}) and the tool upgrades made in a crafting grid
 * ({@code upgrades:}; rod parts are fitted in Fishing → Equipment while it is on, {@link GatheringCatalog#madeInGrid}).
 * A recipe the server leaves out (an ingredient that is not an item, a result with no item) is left out here too, as
 * {@link ItemRecipes}, {@link Tackle#registerRecipes} and {@link Upgrades#register} leave it out. Meals and brews have
 * their own lists (Cookbook, Brews). Pure: the catalog in, the list out.
 */
public final class RecipeBook {

    public enum Kind { ITEM, TACKLE, UPGRADE }

    /**
     * One recipe, shapeless. {@code ingredients}: one entry per item put in, alternatives joined ("melon seeds or pumpkin
     * seeds"); {@code unlockedBy}: the collection milestone that unlocks it (null: everyone has it); {@code gives}: what an
     * upgrade adds to the tool (null for an item); {@code about}: the made item's own lore.
     */
    public record Entry(String id, Kind kind, Material icon, String name, List<String> ingredients, int makes,
                        String unlockedBy, String gives, List<String> about) {
        /** Up to four ingredients fit the 2×2 grid in a player's own inventory; more need a crafting table. */
        public boolean fitsInventoryGrid() { return ingredients.size() <= 4; }
    }

    private RecipeBook() {}

    /**
     * The crafting recipes of {@code c} in the file's order: items, then bait and reel, then tool upgrades.
     * {@code isItem} is {@link Material#isItem} on a server (it reads the server's registry).
     */
    public static List<Entry> crafting(GatheringCatalog c, Predicate<Material> isItem) {
        List<Entry> out = new ArrayList<>();
        for (TackleRules.Recipe r : c.recipes().values()) add(out, c, r, Kind.ITEM, isItem);
        for (TackleRules.Recipe r : c.tackle().recipes().values()) add(out, c, r, Kind.TACKLE, isItem);
        for (GatheringCatalog.UpgradeDef u : c.upgrades().values()) {
            GatheringCatalog.ItemDef item = c.item(u.item());
            if (!c.madeInGrid(u) || item == null || u.on().isEmpty()) continue;
            List<String> kinds = u.on().stream().sorted().toList();
            out.add(new Entry(u.id(), Kind.UPGRADE, toolIcon(kinds.get(0), item.material()), u.name(),
                    List.of(item.name(), "any " + String.join(" or ", kinds)), 1, null, GearItems.upgradeText(u, true), List.of()));
        }
        return out;
    }

    private static void add(List<Entry> out, GatheringCatalog c, TackleRules.Recipe r, Kind kind, Predicate<Material> isItem) {
        GatheringCatalog.ItemDef def = c.item(r.result());
        if (def == null || r.ingredients().stream().flatMap(List::stream).anyMatch(m -> !isItem.test(m))) return;
        List<String> ingredients = new ArrayList<>();
        for (List<Material> any : r.ingredients()) ingredients.add(String.join(" or ", any.stream().map(RecipeBook::name).toList()));
        out.add(new Entry(r.id(), kind, def.material(), def.name(), List.copyOf(ingredients), r.makes(), r.unlockedBy(), null,
                def.lore() == null ? List.of() : def.lore()));
    }

    /** WHEAT_SEEDS → "wheat seeds". */
    static String name(Material m) {
        return m.name().toLowerCase(Locale.ROOT).replace('_', ' ');
    }

    /** The tool an upgrade goes on, as an icon: an iron one where there is one (iron hoe), else the item itself (bow). */
    static Material toolIcon(String kind, Material fallback) {
        if ("rod".equals(kind)) return Material.FISHING_ROD;
        Material iron = Material.getMaterial("IRON_" + kind.toUpperCase(Locale.ROOT));
        if (iron != null) return iron;
        Material plain = Material.getMaterial(kind.toUpperCase(Locale.ROOT));
        return plain != null ? plain : fallback;
    }
}
