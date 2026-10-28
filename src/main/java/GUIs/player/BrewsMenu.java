package GUIs.player;

import GUIs.menu.MenuView;
import GUIs.menu.Paging;
import GUIs.menu.Ui;
import Gathering.Alchemy;
import Gathering.AlchemyRules;
import Gathering.CookingRules;
import Gathering.GatheringCatalog;
import Gathering.HerbalismRules;
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
 * Player Menu → Skills → Alchemy → Brews, and Collections → Brews (docs/design/ALCHEMY.md §5): one tile per brew in
 * resources.yml with its level, reagent, kind, and its effect and time at the tier the viewer brews at, from the same rules
 * the game uses; whether they have brewed it. No database.
 */
public final class BrewsMenu extends PlayerMenu {

    private static final int[] SLOTS = {19, 20, 21, 22, 23, 24, 25, 28, 29, 30, 31, 32, 33, 34, 37, 38, 39, 40, 41, 42, 43};
    private int page;

    public BrewsMenu(CerberusPlugin plugin) {
        super(plugin);
    }

    @Override public String title() { return PlayerTheme.title("Brews"); }

    @Override protected Material headerGlass() { return Material.PURPLE_STAINED_GLASS_PANE; }

    @Override
    protected void content(MenuView v) {
        Player p = v.viewer();
        Alchemy a = plugin.getAlchemy();
        AlchemyRules r = GatheringCatalog.current().alchemy();
        if (a == null || !r.enabled()) {
            v.set(22, Ui.item(Material.BARRIER, ChatColor.GRAY + "Alchemy is off on this server", List.of()));
            return;
        }
        if (!loaded(p)) { loading(v); return; }
        List<AlchemyRules.Brew> brews = new ArrayList<>(r.brews());
        Set<String> made = a.made(p);
        long have = brews.stream().filter(b -> made.contains(b.id())).count();
        int level = plugin.getSkillManager().getSkillLevel(p, r.skill());
        Quality tier = r.tierAt(level);
        Ui.Lore head = Ui.lore().stat("Brewed", have + " of " + brews.size())
                .raw(PlayerTheme.bar(have, Math.max(1, brews.size()), 20, ChatColor.LIGHT_PURPLE)).gap()
                .raw(ChatColor.GRAY + "You brew " + tier.color + tier.displayName + ChatColor.GRAY + ": every number ×"
                        + CookingRules.trim(r.potency(tier)) + ", elixirs last ×" + CookingRules.trim(r.durationFactor(tier))).gap();
        for (String w : PlayerTheme.wrap("At a brewing stand: water bottles, nether wart into Awkward Potions, then the reagent. A reagent "
                + "with a quality (Herbalism 50), or a brew's lifting rare reagent, makes it one tier better. One elixir at a time; one "
                + "draught at a time; an elixir and a meal work together.", SkillView.WIDTH)) head.text(w);
        v.set(MenuView.HEADER, Ui.item(Material.BREWING_STAND, ChatColor.LIGHT_PURPLE + "" + ChatColor.BOLD + "Brews", head.build()));

        HerbalismRules h = GatheringCatalog.current().herbalism();
        page = Paging.clampPage(page, brews.size(), SLOTS.length);
        int pages = Paging.pageCount(brews.size(), SLOTS.length);
        List<AlchemyRules.Brew> shown = Paging.slice(brews, page, SLOTS.length);
        for (int i = 0; i < shown.size(); i++) {
            AlchemyRules.Brew b = shown.get(i);
            boolean open = level >= b.level();
            Ui.Lore l = Ui.lore();
            l.raw(made.contains(b.id()) ? ChatColor.GREEN + "✔ Brewed" : ChatColor.DARK_GRAY + "✘ Not brewed yet");
            l.raw((open ? ChatColor.GREEN + "✓ " : ChatColor.RED + "✕ ") + ChatColor.WHITE + "Alchemy " + b.level()
                    + ChatColor.DARK_GRAY + " (you: " + level + ")");
            l.stat("Kind", b.kind().label());
            l.stat("Reagent", reagent(b, h) + (b.lift() != null ? " (" + rareName(b.lift(), h) + ": a tier up)" : ""));
            for (String e : Alchemy.effectLines(b, tier, r)) l.raw(ChatColor.WHITE + "  " + e);
            l.stat("XP", CookingRules.trim(b.xp()) + " a bottle");
            v.set(SLOTS[i], Ui.item(open ? Material.POTION : Material.GRAY_DYE, (open ? ChatColor.LIGHT_PURPLE : ChatColor.GRAY) + b.name(), l.build()));
        }
        if (page > 0) v.button(MenuView.PREV, Ui.item(Material.SPECTRAL_ARROW, ChatColor.YELLOW + "Previous page",
                Ui.lore().stat("Page", page + " / " + pages).build()), click -> { page--; menus.refresh(click.player()); });
        if (page < pages - 1) v.button(MenuView.NEXT, Ui.item(Material.SPECTRAL_ARROW, ChatColor.YELLOW + "Next page",
                Ui.lore().stat("Page", (page + 2) + " / " + pages).build()), click -> { page++; menus.refresh(click.player()); });
        v.refreshButton();
    }

    /** "an Awkward Potion and a poppy", or the rare reagent by its name. */
    static String reagent(AlchemyRules.Brew b, HerbalismRules h) {
        if (b.reagent() != null) return b.reagent().name().toLowerCase(Locale.ROOT).replace('_', ' ');
        return rareName(b.rare(), h) + " (rare)";
    }

    static String rareName(String id, HerbalismRules h) {
        HerbalismRules.Rare rare = h.rare(id);
        return rare == null ? id : rare.name();
    }
}
