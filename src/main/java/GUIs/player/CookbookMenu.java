package GUIs.player;

import Commands.CookingCommand;
import GUIs.menu.MenuView;
import GUIs.menu.Paging;
import GUIs.menu.Ui;
import Gathering.Cooking;
import Gathering.CookingRules;
import Gathering.GatheringCatalog;
import Gathering.Quality;
import cerberus.world.cerb.CerberusPlugin;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Player Menu → Skills → Cooking → Cookbook, and Collections → Meals (docs/design/FARMING_COOKING.md §4.3): one tile per
 * meal in resources.yml with its level, ingredients, and its effect and duration at the tier the viewer cooks at, from
 * the same rules the game uses; whether they have cooked it; the running meal in the header. No database.
 */
public final class CookbookMenu extends PlayerMenu {

    private static final int[] SLOTS = {19, 20, 21, 22, 23, 24, 25, 28, 29, 30, 31, 32, 33, 34, 37, 38, 39, 40, 41, 42, 43};
    private int page;

    public CookbookMenu(CerberusPlugin plugin) {
        super(plugin);
    }

    @Override public String title() { return PlayerTheme.title("Cookbook"); }

    @Override protected Material headerGlass() { return Material.ORANGE_STAINED_GLASS_PANE; }

    @Override
    protected void content(MenuView v) {
        Player p = v.viewer();
        Cooking c = plugin.getCooking();
        CookingRules r = GatheringCatalog.current().cooking();
        if (c == null || !r.enabled()) {
            v.set(22, Ui.item(Material.BARRIER, ChatColor.GRAY + "Cooking is off on this server", List.of()));
            return;
        }
        if (!loaded(p)) { loading(v); return; }
        List<CookingRules.Meal> meals = new ArrayList<>(r.meals());
        Set<String> cooked = c.cooked(p);
        long have = meals.stream().filter(m -> cooked.contains(m.id())).count();
        int level = plugin.getSkillManager().getSkillLevel(p, r.skill());
        Quality tier = r.tierAt(level);
        Ui.Lore head = Ui.lore().stat("Cooked", have + " of " + meals.size())
                .raw(PlayerTheme.bar(have, Math.max(1, meals.size()), 20, ChatColor.GOLD)).gap()
                .raw(ChatColor.GRAY + "You cook " + tier.color + tier.displayName + ChatColor.GRAY + " meals: effects ×"
                        + CookingRules.trim(r.potency(tier)) + ", lasting ×" + CookingRules.trim(r.durationFactor(tier)))
                .raw(CookingCommand.activeLine(c, r, p)).gap();
        for (String w : PlayerTheme.wrap("Craft meals in a crafting grid (four-ingredient meals fit your own). Half or more of a meal's crops with a quality "
                + "(Farming 50) make it one tier better. One meal at a time: a new one replaces the last.", SkillView.WIDTH)) head.text(w);
        v.set(MenuView.HEADER, Ui.item(Material.CAKE, ChatColor.GOLD + "" + ChatColor.BOLD + "Cookbook", head.build()));

        page = Paging.clampPage(page, meals.size(), SLOTS.length);
        int pages = Paging.pageCount(meals.size(), SLOTS.length);
        List<CookingRules.Meal> shown = Paging.slice(meals, page, SLOTS.length);
        for (int i = 0; i < shown.size(); i++) {
            CookingRules.Meal m = shown.get(i);
            boolean open = level >= m.level();
            Ui.Lore l = Ui.lore();
            l.raw(cooked.contains(m.id()) ? ChatColor.GREEN + "✔ Cooked" : ChatColor.DARK_GRAY + "✘ Not cooked yet");
            l.raw((open ? ChatColor.GREEN + "✓ " : ChatColor.RED + "✕ ") + ChatColor.WHITE + "Cooking " + m.level()
                    + ChatColor.DARK_GRAY + " (you: " + level + ")");
            l.stat("Role", m.role());
            l.stat("Ingredients", ingredients(m));
            for (String e : Cooking.effectLines(m, tier, r)) l.raw(ChatColor.WHITE + "  " + e);
            l.stat("Lasts", durationText(r.durationMs(m, tier)) + " (" + tier.displayName + ")");
            l.stat("XP", CookingRules.trim(m.xp()) + " each");
            Material icon = open ? m.item() : Material.GRAY_DYE;
            v.set(SLOTS[i], Ui.item(icon, (open ? ChatColor.GOLD : ChatColor.GRAY) + m.name(), l.build()));
        }
        if (page > 0) v.button(MenuView.PREV, Ui.item(Material.SPECTRAL_ARROW, ChatColor.YELLOW + "Previous page",
                Ui.lore().stat("Page", page + " / " + pages).build()), click -> { page--; menus.refresh(click.player()); });
        if (page < pages - 1) v.button(MenuView.NEXT, Ui.item(Material.SPECTRAL_ARROW, ChatColor.YELLOW + "Next page",
                Ui.lore().stat("Page", (page + 2) + " / " + pages).build()), click -> { page++; menus.refresh(click.player()); });
        v.refreshButton();
    }

    /** "bowl, any cooked meat, carrot, potato, beetroot". */
    static String ingredients(CookingRules.Meal m) {
        List<String> out = new ArrayList<>();
        for (List<Material> slot : m.recipe()) {
            if (slot.size() > 1 && slot.contains(Material.COOKED_BEEF)) out.add("any cooked meat");
            else if (slot.size() > 1) out.add("cooked cod or salmon");
            else out.add(slot.get(0).name().toLowerCase(Locale.ROOT).replace('_', ' '));
        }
        return String.join(", ", out);
    }

    static String durationText(long ms) {
        long s = Math.round(ms / 1000.0), min = s / 60, sec = s % 60;
        return min > 0 ? min + " min" + (sec > 0 ? " " + sec + " s" : "") : sec + " s";
    }
}
