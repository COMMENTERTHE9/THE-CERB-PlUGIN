package GUIs.player;

import GUIs.menu.MenuView;
import GUIs.menu.Paging;
import GUIs.menu.Ui;
import Gathering.AlchemyRules;
import Gathering.CookingRules;
import Gathering.GatheringCatalog;
import Gathering.RecipeBook;
import Gathering.TackleRules;
import cerberus.world.cerb.CerberusPlugin;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.List;

/**
 * Player Menu → Recipes: how to make every Cerberus item, read from the recipes the game registers ({@link RecipeBook}).
 * The crafting recipes are on this screen (the Sower's Charm, bait, the reel, tool upgrades such as the Replanting hoe)
 * with their ingredients, where they are made and whether this player has unlocked them; meals and brews open their own
 * lists (Cookbook, Brews), and rod parts Fishing → Equipment. Read-only, no database.
 */
public final class RecipesMenu extends PlayerMenu {

    private static final int[] SLOTS = {19, 20, 21, 22, 23, 24, 25, 28, 29, 30, 31, 32, 33, 34};
    private static final int MEALS = 38, BREWS = 40, PARTS = 42;
    private int page;

    public RecipesMenu(CerberusPlugin plugin) {
        super(plugin);
    }

    @Override public String title() { return PlayerTheme.title("Recipes"); }

    @Override
    protected void content(MenuView v) {
        Player p = v.viewer();
        if (!loaded(p)) { loading(v); return; }
        GatheringCatalog c = GatheringCatalog.current();
        List<RecipeBook.Entry> all = RecipeBook.crafting(c, Material::isItem);
        Ui.Lore head = Ui.lore().stat("Crafting recipes", String.valueOf(all.size())).gap();
        for (String w : PlayerTheme.wrap("Put the ingredients anywhere in a crafting grid: up to four fit your inventory's own, more "
                + "need a crafting table. Item recipes are in your recipe book too. Tool upgrades are not: the tool and its "
                + "item together make one.", SkillView.WIDTH)) head.text(w);
        v.set(MenuView.HEADER, Ui.item(Material.CRAFTING_TABLE, ChatColor.GOLD + "" + ChatColor.BOLD + "Recipes", head.build()));

        page = Paging.clampPage(page, all.size(), SLOTS.length);
        int pages = Paging.pageCount(all.size(), SLOTS.length);
        List<RecipeBook.Entry> shown = Paging.slice(all, page, SLOTS.length);
        for (int i = 0; i < shown.size(); i++) v.set(SLOTS[i], tile(shown.get(i), c, p));
        if (all.isEmpty()) v.set(22, Ui.item(Material.GRAY_DYE, ChatColor.GRAY + "No crafting recipes on this server", List.of()));
        links(v, c, p);
        if (page > 0) v.button(MenuView.PREV, Ui.item(Material.SPECTRAL_ARROW, ChatColor.YELLOW + "Previous page",
                Ui.lore().stat("Page", page + " / " + pages).build()), click -> { page--; menus.refresh(click.player()); });
        if (page < pages - 1) v.button(MenuView.NEXT, Ui.item(Material.SPECTRAL_ARROW, ChatColor.YELLOW + "Next page",
                Ui.lore().stat("Page", (page + 2) + " / " + pages).build()), click -> { page++; menus.refresh(click.player()); });
        v.refreshButton();
    }

    /** One recipe: what goes in, where, what it makes or gives, and (a milestone's recipe) whether it is unlocked. */
    private ItemStack tile(RecipeBook.Entry e, GatheringCatalog c, Player p) {
        boolean upgrade = e.kind() == RecipeBook.Kind.UPGRADE;
        boolean unlocked = e.unlockedBy() == null
                || (plugin.getTackle() != null && plugin.getTackle().mayCraft(p, c.tackle().recipes().get(e.id())));
        Ui.Lore l = Ui.lore();
        if (upgrade) l.raw(ChatColor.DARK_GRAY + "Tool upgrade");
        if (e.unlockedBy() != null) {
            TackleRules.Reward r = c.tackle().rewards().get(e.unlockedBy());
            String milestone = r == null ? e.unlockedBy() : r.name();
            if (unlocked) l.raw(ChatColor.GREEN + "✓ Unlocked " + ChatColor.DARK_GRAY + "(" + milestone + ")");
            else {
                l.raw(ChatColor.RED + "✕ Locked: " + ChatColor.WHITE + "the " + milestone + " milestone");
                if (r != null) l.raw(ChatColor.DARK_GRAY + "  " + TackleRules.asks(r) + " (Fishing)");
            }
        }
        l.raw(ChatColor.GRAY + "Ingredients:");
        for (String i : e.ingredients()) l.raw(ChatColor.WHITE + "  • " + i);
        if (e.makes() > 1) l.stat("Makes", String.valueOf(e.makes()));
        l.stat("Where", e.fitsInventoryGrid() ? "any crafting grid" : "a crafting table");
        if (upgrade) {
            l.gap();
            for (String w : PlayerTheme.wrap("Gives the tool: " + e.gives() + ". Once per item; its enchantments, damage and name "
                    + "stay.", SkillView.WIDTH)) l.text(w);
        }
        if (!e.about().isEmpty()) {
            l.gap();
            for (String a : e.about()) l.raw(ChatColor.GRAY + a);
        }
        l.gap().raw(ChatColor.DARK_GRAY + (upgrade ? "Not in the recipe book" : unlocked ? "Also in your recipe book" : "In your recipe book once unlocked"));
        ItemStack it = Ui.item(unlocked ? e.icon() : Material.GRAY_DYE, (unlocked ? PlayerTheme.PRIMARY : ChatColor.GRAY) + e.name(), l.build());
        return upgrade && unlocked ? Ui.glow(it, true) : it;
    }

    /** The lists other screens keep: meals (Cookbook), brews (Brews), rod parts (Fishing → Equipment). */
    private void links(MenuView v, GatheringCatalog c, Player p) {
        CookingRules cr = c.cooking();
        if (plugin.getCooking() != null && cr.enabled()) {
            int level = plugin.getSkillManager().getSkillLevel(p, cr.skill());
            long can = cr.meals().stream().filter(m -> level >= m.level()).count();
            v.button(MEALS, Ui.item(Material.CAKE, PlayerTheme.PRIMARY + "Meals", Ui.lore()
                    .text(cr.meals().size() + " meals from Cooking, made").text("in a crafting grid.")
                    .stat("You can cook", can + " of " + cr.meals().size())
                    .hint("Click to open the Cookbook").build()), click -> menus.open(click.player(), new CookbookMenu(plugin)));
        }
        AlchemyRules ar = c.alchemy();
        if (plugin.getAlchemy() != null && ar.enabled()) {
            int level = plugin.getSkillManager().getSkillLevel(p, ar.skill());
            long can = ar.brews().stream().filter(b -> level >= b.level()).count();
            v.button(BREWS, Ui.item(Material.BREWING_STAND, PlayerTheme.PRIMARY + "Brews", Ui.lore()
                    .text(ar.brews().size() + " brews from Alchemy, made").text("at a brewing stand.")
                    .stat("You can brew", can + " of " + ar.brews().size())
                    .hint("Click to open Brews").build()), click -> menus.open(click.player(), new BrewsMenu(plugin)));
        }
        if (plugin.getTackle() != null && c.tackle().enabled()) {
            v.button(PARTS, Ui.item(Material.FISHING_ROD, PlayerTheme.PRIMARY + "Rod parts", Ui.lore()
                    .text("Rod, line, lure and reel parts").text("are fitted with their item in").text("Fishing → Equipment, not crafted.")
                    .hint("Click to open Fishing Equipment").build()), click -> menus.open(click.player(), new FishingEquipmentMenu(plugin)));
        }
    }
}
