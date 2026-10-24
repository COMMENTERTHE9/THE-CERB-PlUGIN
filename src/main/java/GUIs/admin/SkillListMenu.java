package GUIs.admin;

import GUIs.SkillDefinitions;
import GUIs.menu.Fmt;
import GUIs.menu.MenuView;
import GUIs.menu.Ui;
import Skills.SkillManager;
import Skills.SkillProgression;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/** Every registered skill for one player, by category; click to edit. */
final class SkillListMenu extends AdminMenu {

    private final UUID targetId;
    private int page;

    SkillListMenu(AdminPanel panel, UUID targetId) {
        super(panel);
        this.targetId = targetId;
    }

    @Override public String title() {
        Player t = Bukkit.getPlayer(targetId);
        return PREFIX + "Skills: " + (t != null ? t.getName() : "offline");
    }

    /** Registered skill names, ordered Combat / Magic / Utility, then by name. */
    static List<String> orderedSkills(SkillManager sm) {
        List<String> names = new ArrayList<>(sm.getSkills().keySet());
        names.sort(Comparator.comparingInt((String n) -> {
            SkillDefinitions.SkillDef d = SkillDefinitions.byName(n);
            return d == null ? 99 : d.category().ordinal();
        }).thenComparing(String.CASE_INSENSITIVE_ORDER));
        return names;
    }

    @Override
    protected void build(MenuView v) {
        frame(v);
        v.refreshButton();
        Player t = Bukkit.getPlayer(targetId);
        if (t == null) {
            v.set(22, Ui.item(Material.GRAY_DYE, ChatColor.GRAY + "This player is offline"));
            return;
        }
        SkillManager sm = plugin.getSkillManager();
        v.set(MenuView.HEADER, Ui.head(t, ChatColor.AQUA + "" + ChatColor.BOLD + t.getName() + "'s skills",
                Ui.lore().stat("Total level", String.valueOf(sm.getPlayerLevel(t)))
                        .stat("Level cap", String.valueOf(SkillProgression.MAX_LEVEL))
                        .text("Click a skill to change its level or XP").build()));

        page = v.page(orderedSkills(sm), page, name -> {
            SkillDefinitions.SkillDef d = SkillDefinitions.byName(name);
            int lvl = sm.getSkillLevel(t, name);
            int xp = sm.getSkillXP(t, name);
            int req = lvl >= SkillProgression.MAX_LEVEL ? 0 : SkillManager.xpForLevel(lvl);
            ChatColor color = d == null ? ChatColor.WHITE : d.category().color;
            Material icon = d == null || d.icon() == null ? Material.BOOK : d.icon();
            Ui.Lore lore = Ui.lore().stat("Level", lvl + " / " + SkillProgression.MAX_LEVEL);
            if (req > 0) lore.raw(ChatColor.GREEN + Fmt.bar(xp, req, 20)).stat("XP", Fmt.grouped(xp) + " / " + Fmt.grouped(req));
            else lore.raw(ChatColor.GOLD + "MAX LEVEL");
            if (d != null) lore.stat("Category", Fmt.title(d.category().name()));
            return Ui.amount(Ui.item(icon, color + name, lore.hint("Click to edit").build()), Math.max(1, lvl));
        }, name -> c -> open(c.player(), new SkillEditMenu(panel, targetId, name)), np -> page = np);
    }
}
