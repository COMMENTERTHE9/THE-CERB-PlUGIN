package GUIs.player;

import GUIs.SkillDefinitions;
import GUIs.SkillDefinitions.SkillCategory;
import GUIs.SkillDefinitions.SkillDef;
import GUIs.menu.Fmt;
import GUIs.menu.MenuView;
import GUIs.menu.Ui;
import Skills.SkillManager;
import Skills.SkillProgression;
import cerberus.world.cerb.CerberusPlugin;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * Player Menu → Skills: overall progress, the three categories, and shortcuts to the player's
 * strongest skills. Reads the in-memory skill maps only.
 */
public final class SkillsMenu extends PlayerMenu {

    private static final int[] CATEGORY_SLOTS = {20, 22, 24};
    private static final int[] TOP_SLOTS = {38, 39, 40, 41, 42};

    public SkillsMenu(CerberusPlugin plugin) {
        super(plugin);
    }

    @Override public String title() { return PlayerTheme.title("Skills"); }

    @Override
    protected void content(MenuView v) {
        Player p = v.viewer();
        UUID id = p.getUniqueId();
        if (!loaded(p)) {
            loading(v);
            return;
        }
        List<SkillDef> all = SkillDefinitions.all();
        int total = SkillView.totalLevel(plugin, id), cap = all.size() * SkillProgression.MAX_LEVEL;
        int maxed = 0, trainable = 0;
        for (SkillDef d : all) {
            if (plugin.getSkillManager().getSkillLevel(id, d.name()) >= SkillProgression.MAX_LEVEL) maxed++;
            if (SkillView.trainable(plugin, d)) trainable++;
        }
        SkillDef best = SkillView.best(plugin, id);

        Ui.Lore head = Ui.lore()
                .stat("Total skill level", Fmt.grouped(total) + ChatColor.DARK_GRAY + " / " + Fmt.grouped(cap))
                .raw(PlayerTheme.bar(total, cap, 20, ChatColor.GREEN));
        if (best != null) head.stat("Highest", best.displayName() + " " + plugin.getSkillManager().getSkillLevel(id, best.name()));
        head.stat("Skills at max level", maxed + " / " + all.size())
                .stat("Skills you can train today", trainable + " / " + all.size());
        v.set(MenuView.HEADER, Ui.item(Material.EXPERIENCE_BOTTLE, ChatColor.GOLD + "" + ChatColor.BOLD + "Your skills", head.build()));

        SkillCategory[] cats = SkillCategory.values();
        for (int i = 0; i < cats.length && i < CATEGORY_SLOTS.length; i++) {
            SkillCategory cat = cats[i];
            v.button(CATEGORY_SLOTS[i], categoryTile(id, cat), c -> menus.open(c.player(), new SkillCategoryMenu(plugin, cat)));
        }

        v.set(31, howItWorks());

        // Row 4: the strongest skills, one click away
        List<SkillDef> top = new ArrayList<>(all);
        SkillManager sm = plugin.getSkillManager();
        top.sort(Comparator.comparingInt((SkillDef d) -> sm.getSkillLevel(id, d.name())).reversed());
        int shown = 0;
        for (SkillDef d : top) {
            if (shown >= TOP_SLOTS.length || sm.getSkillLevel(id, d.name()) <= 0) break;
            v.button(TOP_SLOTS[shown++], SkillView.tile(plugin, id, d), c -> menus.open(c.player(), new SkillDetailMenu(plugin, d.name())));
        }
        if (shown == 0) {
            v.set(40, Ui.item(Material.GRAY_DYE, ChatColor.GRAY + "No skill levels yet",
                    Ui.lore().text("Fight, cast spells or explore:").text("your best skills will show here.").build()));
        } else {
            v.set(37, Ui.item(Material.GOLD_NUGGET, ChatColor.YELLOW + "Your best skills",
                    Ui.lore().text("Your highest-level skills.").text("Click one to open it.").build()));
        }
        v.refreshButton();
    }

    private org.bukkit.inventory.ItemStack categoryTile(UUID id, SkillCategory cat) {
        List<SkillDef> defs = SkillDefinitions.byCategory(cat);
        SkillManager sm = plugin.getSkillManager();
        int sum = 0, trainable = 0;
        for (SkillDef d : defs) {
            sum += sm.getSkillLevel(id, d.name());
            if (SkillView.trainable(plugin, d)) trainable++;
        }
        int cap = defs.size() * SkillProgression.MAX_LEVEL;
        List<SkillDef> sorted = new ArrayList<>(defs);
        sorted.sort(Comparator.comparingInt((SkillDef d) -> sm.getSkillLevel(id, d.name())).reversed());
        PlayerMenuMemory mem = memory();
        int gained = 0;
        for (SkillDef d : defs) gained += mem.gained(id, d.name(), sm.getSkillLevel(id, d.name()));
        boolean last = mem.lastCategory(id) == cat;

        Ui.Lore l = Ui.lore();
        if (gained > 0) l.raw(PlayerTheme.newProgress(gained));
        if (last) l.raw(ChatColor.DARK_GRAY + "◆ Last opened");
        l
                .stat("Skills", String.valueOf(defs.size()))
                .stat("Levels", Fmt.grouped(sum) + ChatColor.DARK_GRAY + " / " + Fmt.grouped(cap))
                .raw(PlayerTheme.bar(sum, cap, 20, cat.color))
                .stat("Can be trained today", trainable + " of " + defs.size())
                .gap().text("Highest:");
        for (int i = 0; i < Math.min(3, sorted.size()); i++) {
            SkillDef d = sorted.get(i);
            l.raw(ChatColor.WHITE + "  " + d.displayName() + ChatColor.GRAY + "  " + sm.getSkillLevel(id, d.name()));
        }
        l.hint("Click to open");
        return PlayerTheme.badge(Ui.glow(Ui.item(cat.icon, cat.color + "" + ChatColor.BOLD + cat.displayTitle, l.build()), gained > 0 || last), gained);
    }

    private static org.bukkit.inventory.ItemStack howItWorks() {
        Ui.Lore l = Ui.lore()
                .text("Skills grow by doing: each skill")
                .text("page lists exactly what trains it.")
                .gap().text("Every level adds attribute points")
                .text("(Power, Agility, Vitality...), which")
                .text("make you stronger. See Stats.")
                .gap().text("XP needed per level:")
                .raw(ChatColor.WHITE + "  Level 1: " + Fmt.grouped(SkillManager.xpForLevel(1))
                        + ChatColor.GRAY + "   Level 50: " + ChatColor.WHITE + Fmt.grouped(SkillManager.xpForLevel(50)))
                .raw(ChatColor.WHITE + "  Level 99: " + Fmt.grouped(SkillManager.xpForLevel(99))
                        + ChatColor.GRAY + "   Max level: " + ChatColor.WHITE + SkillProgression.MAX_LEVEL)
                .gap().text("Each effect shows whether it works:")
                .raw(PlayerTheme.indent(SkillDefinitions.EffectStatus.LIVE.label))
                .raw(PlayerTheme.indent(SkillDefinitions.EffectStatus.PVP_ONLY.label))
                .raw(PlayerTheme.indent(SkillDefinitions.EffectStatus.NOT_ACTIVE.label));
        return Ui.item(Material.KNOWLEDGE_BOOK, ChatColor.GOLD + "How skills work", l.build());
    }
}
