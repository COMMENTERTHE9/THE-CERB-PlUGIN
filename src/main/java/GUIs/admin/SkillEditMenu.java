package GUIs.admin;

import GUIs.SkillDefinitions;
import GUIs.menu.Fmt;
import GUIs.menu.MenuClick;
import GUIs.menu.MenuView;
import GUIs.menu.Ui;
import Skills.SkillManager;
import Skills.SkillProgression;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.UUID;

/**
 * Set one skill's level or grant XP for one player. Uses SkillManager's own methods (same save
 * path, cap and level-up messages as real play). Lowering a level asks for confirmation.
 */
final class SkillEditMenu extends AdminMenu {

    private static final int[] PRESETS = {1, 10, 25, 50, 75, 99, 100};
    private static final long[] XP_GRANTS = {10, 100, 1_000, 10_000, 100_000};

    private final UUID targetId;
    private final String skill;

    SkillEditMenu(AdminPanel panel, UUID targetId, String skill) {
        super(panel);
        this.targetId = targetId;
        this.skill = skill;
    }

    @Override public String title() { return PREFIX + skill; }

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
        SkillDefinitions.SkillDef d = SkillDefinitions.byName(skill);
        int lvl = sm.getSkillLevel(t, skill);
        int xp = sm.getSkillXP(t, skill);
        boolean max = lvl >= SkillProgression.MAX_LEVEL;
        int req = max ? 0 : SkillManager.xpForLevel(lvl);

        Ui.Lore head = Ui.lore().stat("Player", t.getName()).stat("Level", lvl + " / " + SkillProgression.MAX_LEVEL);
        if (!max) head.raw(ChatColor.GREEN + Fmt.bar(xp, req, 20)).stat("XP", Fmt.grouped(xp) + " / " + Fmt.grouped(req))
                .stat("XP to next level", Fmt.grouped(req - xp));
        else head.raw(ChatColor.GOLD + "MAX LEVEL");
        if (d != null && d.description() != null) head.gap().text(d.description());
        v.set(MenuView.HEADER, Ui.item(d == null || d.icon() == null ? Material.BOOK : d.icon(),
                (d == null ? ChatColor.WHITE : d.category().color) + "" + ChatColor.BOLD + skill, head.build()));

        // Row 2: step the level
        stepButton(v, 19, Material.RED_CONCRETE, -10, lvl);
        stepButton(v, 20, Material.PINK_CONCRETE, -1, lvl);
        v.set(22, Ui.amount(Ui.item(Material.EXPERIENCE_BOTTLE, ChatColor.YELLOW + "Level " + lvl,
                Ui.lore().text("Use the buttons to change it.").build()), Math.max(1, lvl)));
        stepButton(v, 24, Material.LIME_CONCRETE, +1, lvl);
        stepButton(v, 25, Material.GREEN_CONCRETE, +10, lvl);

        // Row 3: presets
        for (int i = 0; i < PRESETS.length; i++) {
            int target = PRESETS[i];
            boolean current = target == lvl;
            v.button(28 + i, Ui.glow(Ui.amount(Ui.item(current ? Material.GOLD_BLOCK : Material.IRON_BLOCK,
                    ChatColor.WHITE + "Set level " + target,
                    Ui.lore().text(current ? "Current level" : "XP into the level resets to 0")
                            .hint(target < lvl ? "Click (asks to confirm - lowers the level)" : "Click to set").build()),
                    target), current), c -> setLevel(c, target));
        }

        // Row 4: grant XP through the normal award path
        for (int i = 0; i < XP_GRANTS.length; i++) {
            long amount = XP_GRANTS[i];
            v.button(37 + i, Ui.item(Material.EXPERIENCE_BOTTLE, ChatColor.AQUA + "+" + Fmt.grouped(amount) + " XP",
                    Ui.lore().text("Awarded like real XP: level-ups,").text("chat messages and the cap apply.")
                            .hint(max ? "Already at max level" : "Click to grant").build()),
                    c -> grantXp(c, amount));
        }
        if (!max) {
            long toNext = req - xp;
            v.button(43, Ui.item(Material.DRAGON_BREATH, ChatColor.LIGHT_PURPLE + "Fill to next level",
                    Ui.lore().stat("Grants", Fmt.grouped(toNext) + " XP").hint("Click to grant").build()),
                    c -> grantXp(c, toNext));
        }
    }

    private void stepButton(MenuView v, int slot, Material m, int delta, int lvl) {
        int target = Math.max(0, Math.min(SkillProgression.MAX_LEVEL, lvl + delta));
        String label = (delta > 0 ? "+" : "") + delta + " level" + (Math.abs(delta) == 1 ? "" : "s");
        v.button(slot, Ui.item(m, (delta > 0 ? ChatColor.GREEN : ChatColor.RED) + label,
                Ui.lore().stat("Result", lvl + " -> " + target)
                        .hint(delta < 0 ? "Click (asks to confirm)" : "Click to apply").build()),
                c -> setLevel(c, target));
    }

    private void setLevel(MenuClick c, int newLevel) {
        Player admin = c.player();
        Player t = Bukkit.getPlayer(targetId);
        if (t == null) { Ui.fail(admin, "That player is no longer online."); return; }
        int current = plugin.getSkillManager().getSkillLevel(t, skill);
        if (newLevel == current) { Ui.info(admin, skill + " is already level " + current + "."); return; }
        Runnable apply = () -> changePlayer(admin, targetId, target -> {
            int before = plugin.getSkillManager().getSkillLevel(target, skill);
            plugin.getSkillManager().setSkillLevel(target, skill, newLevel);
            panel.audit(admin, "set " + target.getName() + "'s " + skill + " " + before + " -> " + newLevel);
            notifyTarget(admin, target, "set your " + skill + " to level " + newLevel + ".");
            menus.onPlayerThread(admin, () -> Ui.ok(admin, target.getName() + ": " + skill + " " + before + " -> " + newLevel));
        });
        if (newLevel < current) {
            confirm(admin, "Lower " + skill + "?", List.of(
                    t.getName() + ": level " + current + " -> " + newLevel,
                    "XP into the level resets to 0.",
                    "Levels lost this way are not restored automatically."), p -> apply.run());
        } else {
            apply.run();
        }
    }

    private void grantXp(MenuClick c, long amount) {
        Player admin = c.player();
        changePlayer(admin, targetId, target -> {
            SkillManager sm = plugin.getSkillManager();
            int before = sm.getSkillLevel(target, skill);
            if (before >= SkillProgression.MAX_LEVEL) {
                menus.onPlayerThread(admin, () -> Ui.info(admin, skill + " is already at max level."));
                return;
            }
            sm.addXpAndCheckLevelUp(skill, amount, target);
            int after = sm.getSkillLevel(target, skill);
            panel.audit(admin, "granted " + amount + " " + skill + " XP to " + target.getName() + " (level " + before + " -> " + after + ")");
            notifyTarget(admin, target, "granted you " + Fmt.grouped(amount) + " " + skill + " XP.");
            menus.onPlayerThread(admin, () -> Ui.ok(admin, "+" + Fmt.grouped(amount) + " " + skill + " XP to " + target.getName()
                    + (after != before ? " (level " + before + " -> " + after + ")" : "")));
        });
    }
}
