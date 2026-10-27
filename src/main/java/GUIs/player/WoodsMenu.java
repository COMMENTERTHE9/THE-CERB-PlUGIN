package GUIs.player;

import GUIs.menu.MenuView;
import GUIs.menu.Paging;
import GUIs.menu.Ui;
import Gathering.GatheringCatalog;
import Gathering.Woodcutting;
import cerberus.world.cerb.CerberusPlugin;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Set;

/**
 * Player Menu → Skills → Woodcutting → Woods, and Collections → Woods (docs/design/FISHING_EQUIPMENT.md §2): one tile per
 * wood in resources.yml, discovered or not, with the level and axe it asks for. Read from the player's own discoveries,
 * kept in memory while they are online: no database.
 */
public final class WoodsMenu extends PlayerMenu {

    private static final int[] SLOTS = {19, 20, 21, 22, 23, 24, 25, 28, 29, 30, 31, 32, 33, 34, 37, 38, 39, 40, 41, 42, 43};
    private final String skill;
    private int page;

    public WoodsMenu(CerberusPlugin plugin) {
        this(plugin, "Woodcutting");
    }

    public WoodsMenu(CerberusPlugin plugin, String skill) {
        super(plugin);
        this.skill = skill;
    }

    @Override public String title() { return PlayerTheme.title("Woods"); }

    @Override protected Material headerGlass() { return Material.GREEN_STAINED_GLASS_PANE; }

    @Override
    protected void content(MenuView v) {
        Player p = v.viewer();
        Woodcutting wc = plugin.getWoodcutting();
        GatheringCatalog c = GatheringCatalog.current();
        if (wc == null || c == null) {
            v.set(22, Ui.item(Material.BARRIER, ChatColor.GRAY + "Woodcutting is off on this server", List.of()));
            return;
        }
        if (!loaded(p)) { loading(v); return; }
        List<GatheringCatalog.ResourceDef> woods = woods(c, skill);
        Set<String> found = wc.discovered(p);
        long have = woods.stream().filter(r -> found.contains(r.id())).count();
        int level = plugin.getSkillManager().getSkillLevel(p, skill);
        Ui.Lore head = Ui.lore().stat("Discovered", have + " of " + woods.size())
                .raw(PlayerTheme.bar(have, Math.max(1, woods.size()), 20, ChatColor.GREEN)).gap();
        for (String w : PlayerTheme.wrap("The first log of each wood you cut from a tree pays a discovery bonus, once.", SkillView.WIDTH)) head.text(w);
        v.set(MenuView.HEADER, Ui.item(Material.OAK_SAPLING, ChatColor.GREEN + "" + ChatColor.BOLD + "Woods", head.build()));

        page = Paging.clampPage(page, woods.size(), SLOTS.length);
        int pages = Paging.pageCount(woods.size(), SLOTS.length);
        List<GatheringCatalog.ResourceDef> shown = Paging.slice(woods, page, SLOTS.length);
        for (int i = 0; i < shown.size(); i++) {
            GatheringCatalog.ResourceDef r = shown.get(i);
            boolean got = found.contains(r.id());
            String axe = r.tier() <= 0 ? "any axe" : GatheringCatalog.tierName(r.tier()) + " axe";
            Ui.Lore l = Ui.lore();
            l.raw(got ? ChatColor.GREEN + "✔ Discovered" : ChatColor.DARK_GRAY + "✘ Not discovered yet");
            l.raw((level >= r.level() ? ChatColor.GREEN + "✓ " : ChatColor.RED + "✕ ") + ChatColor.WHITE + "Woodcutting " + r.level()
                    + ChatColor.DARK_GRAY + " (you: " + level + ")");
            l.raw(ChatColor.WHITE + "  " + Character.toUpperCase(axe.charAt(0)) + axe.substring(1));
            l.stat("XP per log", trim(r.xp()));
            if (r.tree() != null && r.tree().discoveryXp() > 0) l.stat("Discovery", "+" + trim(r.tree().discoveryXp()) + " XP, once");
            Material icon = got ? r.blocks().stream().filter(Material::isItem).findFirst().orElse(Material.OAK_LOG) : Material.GRAY_DYE;
            v.set(SLOTS[i], Ui.item(icon, (got ? ChatColor.GREEN : ChatColor.GRAY) + r.displayName(), l.build()));
        }
        if (page > 0) v.button(MenuView.PREV, Ui.item(Material.SPECTRAL_ARROW, ChatColor.YELLOW + "Previous page",
                Ui.lore().stat("Page", page + " / " + pages).build()), click -> { page--; menus.refresh(click.player()); });
        if (page < pages - 1) v.button(MenuView.NEXT, Ui.item(Material.SPECTRAL_ARROW, ChatColor.YELLOW + "Next page",
                Ui.lore().stat("Page", (page + 2) + " / " + pages).build()), click -> { page++; menus.refresh(click.player()); });
        v.refreshButton();
    }

    /** The woods {@code skill} cuts (resources.yml), in the file's order. */
    static List<GatheringCatalog.ResourceDef> woods(GatheringCatalog c, String skill) {
        return c.woods().stream().filter(r -> r.skill().equals(skill)).toList();
    }

    private static String trim(double v) {
        return v == Math.rint(v) ? String.valueOf((long) v) : String.valueOf(v);
    }
}
