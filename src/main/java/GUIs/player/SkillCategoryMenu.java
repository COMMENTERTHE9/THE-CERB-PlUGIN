package GUIs.player;

import GUIs.SkillDefinitions;
import GUIs.SkillDefinitions.SkillCategory;
import GUIs.SkillDefinitions.SkillDef;
import GUIs.menu.Fmt;
import GUIs.menu.MenuView;
import GUIs.menu.Ui;
import Skills.SkillProgression;
import cerberus.world.cerb.CerberusPlugin;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.UUID;

/** Player Menu → Skills → one category: every skill in it, paged if a server adds more than fit. */
public final class SkillCategoryMenu extends PlayerMenu {

    private final SkillCategory category;
    private int page;

    public SkillCategoryMenu(CerberusPlugin plugin, SkillCategory category) {
        super(plugin);
        this.category = category;
    }

    @Override public String title() { return PlayerTheme.title(category.displayTitle); }

    @Override protected Material headerGlass() { return category.accentGlass; }

    @Override
    protected void content(MenuView v) {
        Player p = v.viewer();
        UUID id = p.getUniqueId();
        if (!loaded(p)) {
            loading(v);
            return;
        }
        List<SkillDef> defs = SkillDefinitions.byCategory(category);
        int sum = 0, trainable = 0;
        for (SkillDef d : defs) {
            sum += plugin.getSkillManager().getSkillLevel(id, d.name());
            if (SkillView.trainable(plugin, d)) trainable++;
        }
        int cap = defs.size() * SkillProgression.MAX_LEVEL;
        memory().visitedCategory(id, category);
        // The category shown glows in the middle of the header; the other two sit beside it as tabs.
        v.set(MenuView.HEADER, Ui.glow(Ui.item(category.icon, category.color + "" + ChatColor.BOLD + category.displayTitle, Ui.lore()
                .stat("Levels", Fmt.grouped(sum) + ChatColor.DARK_GRAY + " / " + Fmt.grouped(cap))
                .raw(PlayerTheme.bar(sum, cap, 20, category.color))
                .stat("Can be trained today", trainable + " of " + defs.size())
                .gap().text("Click a skill for what trains it,")
                .text("what it does and what comes next.").build()), true));
        SkillCategory[] all = SkillCategory.values();
        int at = java.util.Arrays.asList(all).indexOf(category);
        if (all.length > 1) tab(v, 2, all[Math.floorMod(at - 1, all.length)], "◀ ");
        if (all.length > 2) tab(v, 6, all[Math.floorMod(at + 1, all.length)], "");

        page = v.page(defs, page, d -> SkillView.tile(plugin, id, d),
                d -> c -> menus.open(c.player(), new SkillDetailMenu(plugin, d.name())), np -> page = np);
        v.refreshButton();
    }

    /** Another category, one click away (sideways: Back still returns to Skills). */
    private void tab(MenuView v, int slot, SkillCategory other, String arrowLeft) {
        String arrowRight = arrowLeft.isEmpty() ? " ▶" : "";
        v.button(slot, Ui.item(other.icon, other.color + arrowLeft + other.displayTitle + arrowRight,
                Ui.lore().text("Switch to " + other.displayTitle.toLowerCase(java.util.Locale.ROOT)).build()),
                c -> menus.replace(c.player(), new SkillCategoryMenu(plugin, other)));
    }
}
