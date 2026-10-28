package GUIs.player;

import Commands.FarmingCommand;
import GUIs.menu.MenuView;
import GUIs.menu.Paging;
import GUIs.menu.Ui;
import Gathering.Farming;
import Gathering.FarmingRules;
import Gathering.GatheringCatalog;
import cerberus.world.cerb.CerberusPlugin;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Player Menu → Skills → Farming → Crops, and Collections → Crops (docs/design/FARMING_COOKING.md §3.5): one tile per crop
 * in resources.yml, discovered or not, with when it is ripe, how it is harvested, its level and XP, and which techniques
 * work on it; the header lists the techniques at the viewer's level, from the same rules the game uses. Read from the
 * player's own discoveries, kept in memory while they are online: no database.
 */
public final class CropsMenu extends PlayerMenu {

    private static final int[] SLOTS = {19, 20, 21, 22, 23, 24, 25, 28, 29, 30, 31, 32, 33, 34, 37, 38, 39, 40, 41, 42, 43};
    private int page;

    public CropsMenu(CerberusPlugin plugin) {
        super(plugin);
    }

    @Override public String title() { return PlayerTheme.title("Crops"); }

    @Override protected Material headerGlass() { return Material.LIME_STAINED_GLASS_PANE; }

    @Override
    protected void content(MenuView v) {
        Player p = v.viewer();
        Farming f = plugin.getFarming();
        FarmingRules r = GatheringCatalog.current().farming();
        if (f == null || !r.enabled()) {
            v.set(22, Ui.item(Material.BARRIER, ChatColor.GRAY + "Farming is off on this server", List.of()));
            return;
        }
        if (!loaded(p)) { loading(v); return; }
        List<FarmingRules.Crop> crops = new ArrayList<>(r.crops());
        Set<String> found = f.discovered(p);
        long have = crops.stream().filter(c -> found.contains(c.id())).count();
        int level = plugin.getSkillManager().getSkillLevel(p, r.skill());
        Ui.Lore head = Ui.lore().stat("Discovered", have + " of " + crops.size())
                .raw(PlayerTheme.bar(have, Math.max(1, crops.size()), 20, ChatColor.GREEN)).gap();
        for (String line : FarmingCommand.techniques(r, level, f.replanting(p))) head.raw(line);
        head.gap();
        for (String w : PlayerTheme.wrap("The first ripe harvest of each crop pays a discovery bonus, once. Crops you plant pay in full; "
                + "melons, pumpkins, cane and cactus placed by hand pay nothing.", SkillView.WIDTH)) head.text(w);
        v.set(MenuView.HEADER, Ui.item(Material.WHEAT, ChatColor.GREEN + "" + ChatColor.BOLD + "Crops", head.build()));

        page = Paging.clampPage(page, crops.size(), SLOTS.length);
        int pages = Paging.pageCount(crops.size(), SLOTS.length);
        List<FarmingRules.Crop> shown = Paging.slice(crops, page, SLOTS.length);
        for (int i = 0; i < shown.size(); i++) {
            FarmingRules.Crop c = shown.get(i);
            boolean got = found.contains(c.id());
            Ui.Lore l = Ui.lore();
            l.raw(got ? ChatColor.GREEN + "✔ Discovered" : ChatColor.DARK_GRAY + "✘ Not discovered yet");
            l.raw((level >= c.level() ? ChatColor.GREEN + "✓ " : ChatColor.RED + "✕ ") + ChatColor.WHITE + "Farming " + c.level()
                    + ChatColor.DARK_GRAY + " (you: " + level + ")");
            l.stat("Pays when", c.ripe().describe(c.age()));
            l.stat("Harvest", c.pick() ? "pick (right-click) or break" : c.column() ? "break (the segments above count too)" : "break");
            l.stat("XP", trim(c.xp()) + (c.column() ? " per grown segment" : " per harvest"));
            if (c.discoveryXp() > 0) l.stat("Discovery", "+" + trim(c.discoveryXp()) + " XP, once");
            List<String> works = new ArrayList<>();
            if (c.replants()) works.add("Replant");
            if (c.wide()) works.add("Wide Harvest");
            if (c.quality()) works.add("quality");
            works.add("Fortune");
            l.raw(ChatColor.DARK_GRAY + String.join(" · ", works));
            Material icon = got ? (c.produce().isItem() ? c.produce() : Material.WHEAT) : Material.GRAY_DYE;
            v.set(SLOTS[i], Ui.item(icon, (got ? ChatColor.GREEN : ChatColor.GRAY) + c.name(), l.build()));
        }
        if (page > 0) v.button(MenuView.PREV, Ui.item(Material.SPECTRAL_ARROW, ChatColor.YELLOW + "Previous page",
                Ui.lore().stat("Page", page + " / " + pages).build()), click -> { page--; menus.refresh(click.player()); });
        if (page < pages - 1) v.button(MenuView.NEXT, Ui.item(Material.SPECTRAL_ARROW, ChatColor.YELLOW + "Next page",
                Ui.lore().stat("Page", (page + 2) + " / " + pages).build()), click -> { page++; menus.refresh(click.player()); });
        v.refreshButton();
    }

    private static String trim(double v) {
        return v == Math.rint(v) ? String.valueOf((long) v) : String.valueOf(v);
    }
}
