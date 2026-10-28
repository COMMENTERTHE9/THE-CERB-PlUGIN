package GUIs.player;

import GUIs.menu.MenuView;
import GUIs.menu.Ui;
import cerberus.world.cerb.CerberusPlugin;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.List;

/**
 * Player Menu → Collections (docs/design/FISHING_EQUIPMENT.md §2): every registered collection ({@link CollectionIndex})
 * with the viewer's progress read from their own records and totals from the definitions. Each opens the same screen its
 * skill page opens; Back returns here.
 */
public final class CollectionsMenu extends PlayerMenu {

    /** Fishing's three on the first row, then the gathering skills' in order (Woods, Crops, Meals, Herbs...). */
    private static final int[] SLOTS = {20, 22, 24, 28, 30, 32, 34, 38, 42};
    /** Milestones and other extra tiles (CollectionIndex.Extra). */
    private static final int[] EXTRA_SLOTS = {40, 39, 41};

    public CollectionsMenu(CerberusPlugin plugin) {
        super(plugin);
    }

    @Override public String title() { return PlayerTheme.title("Collections"); }

    @Override
    protected void content(MenuView v) {
        Player p = v.viewer();
        if (!loaded(p)) { loading(v); return; }
        List<CollectionIndex.Entry> all = plugin.getPlayerMenuGUI().collections().all();
        long have = 0, total = 0;
        int shown = 0;
        for (CollectionIndex.Entry e : all) {
            CollectionIndex.Progress pr = CollectionIndex.safe(e, plugin, p);
            if (!pr.available()) continue;
            if (shown >= SLOTS.length) break;
            have += Math.min(pr.have(), pr.total());
            total += pr.total();
            Ui.Lore l = Ui.lore();
            for (String line : e.summary()) l.text(line);
            l.gap().stat("Collected", pr.have() + " of " + pr.total())
                    .raw(PlayerTheme.bar(pr.have(), Math.max(1, pr.total()), 20, pr.complete() ? ChatColor.GOLD : ChatColor.GREEN));
            l.raw(ChatColor.DARK_GRAY + Skills.SkillNames.display(e.skill()));
            if (pr.complete()) l.raw(ChatColor.GOLD + "✦ Complete");
            if (pr.badge() != null) l.raw(ChatColor.GOLD + "✦ " + pr.badge());
            l.hint("Click to open");
            v.button(SLOTS[shown++], Ui.glow(Ui.item(e.icon(), (pr.complete() ? ChatColor.GOLD : PlayerTheme.PRIMARY) + e.name(), l.build()), pr.complete()),
                    c -> {
                        CollectionIndex.Entry now = plugin.getPlayerMenuGUI().collections().byId(e.id());
                        if (now != null && CollectionIndex.safe(now, plugin, c.player()).available()) menus.open(c.player(), now.opener().apply(plugin, c.player()));
                    });
        }
        int extra = 0;
        for (CollectionIndex.Extra x : plugin.getPlayerMenuGUI().collections().extras()) {
            if (extra >= EXTRA_SLOTS.length) break;
            org.bukkit.inventory.ItemStack tile;
            try { tile = x.tile().apply(plugin, p); } catch (RuntimeException ex) { tile = null; }
            if (tile != null) v.set(EXTRA_SLOTS[extra++], tile);
        }
        if (shown == 0) {
            v.set(22, Ui.item(Material.GRAY_DYE, ChatColor.GRAY + "No collections yet", Ui.lore()
                    .text("Collections arrive with the skills").text("that have them (Fishing, Woodcutting).").build()));
        }
        v.set(MenuView.HEADER, Ui.item(Material.CHEST, ChatColor.GOLD + "" + ChatColor.BOLD + "Collections", Ui.lore()
                .stat("Collected", have + " of " + total)
                .raw(PlayerTheme.bar(have, Math.max(1, total), 20, ChatColor.GREEN))
                .gap().text("Everything you discover is recorded").text("as you play: nothing to hand in.").build()));
        v.refreshButton();
    }
}
