package GUIs.admin;

import GUIs.SkillDefinitions;
import GUIs.menu.Fmt;
import GUIs.menu.MenuClick;
import GUIs.menu.MenuView;
import GUIs.menu.Ui;
import Skills.SkillManager;
import Skills.SkillNames;
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
    /** Sentinel for "grant what the next level needs, measured when the grant runs". */
    private static final long FILL_TO_NEXT = -1;

    private final UUID targetId;
    private final String skill;

    SkillEditMenu(AdminPanel panel, UUID targetId, String skill) {
        super(panel);
        this.targetId = targetId;
        this.skill = skill;
    }

    @Override public String title() { return PREFIX + SkillNames.display(skill); }

    /** For admins: the name players see, plus the internal name when they differ. */
    private String label() {
        String shown = SkillNames.display(skill);
        return shown.equals(skill) ? skill : shown + " (" + skill + ")";
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
        SkillDefinitions.SkillDef d = SkillDefinitions.byName(skill);
        int lvl = sm.getSkillLevel(t, skill);
        int xp = sm.getSkillXP(t, skill);
        boolean max = lvl >= SkillProgression.MAX_LEVEL;
        int req = max ? 0 : SkillManager.xpForLevel(lvl);

        Ui.Lore head = Ui.lore().stat("Player", t.getName()).stat("Level", lvl + " / " + SkillProgression.MAX_LEVEL);
        if (!max) head.raw(ChatColor.GREEN + Fmt.bar(xp, req, 20)).stat("XP", Fmt.grouped(xp) + " / " + Fmt.grouped(req))
                .stat("XP to next level", Fmt.grouped(req - xp));
        else head.raw(ChatColor.GOLD + "MAX LEVEL");
        if (SkillNames.formerName(skill) != null) head.stat("Internal name", skill + " (commands, database)");
        if (d != null && d.description() != null) head.gap().text(d.description());
        v.set(MenuView.HEADER, Ui.item(d == null || d.icon() == null ? Material.BOOK : d.icon(),
                (d == null ? ChatColor.WHITE : d.category().color) + "" + ChatColor.BOLD + SkillNames.display(skill), head.build()));

        // Row 2: step the level (applied to the level at the moment of the click, not when drawn)
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
            v.button(43, Ui.item(Material.DRAGON_BREATH, ChatColor.LIGHT_PURPLE + "Fill to next level",
                    Ui.lore().stat("Grants", Fmt.grouped(req - xp) + " XP (re-checked on click)").hint("Click to grant").build()),
                    c -> grantXp(c, FILL_TO_NEXT));
        }
    }

    private void stepButton(MenuView v, int slot, Material m, int delta, int lvl) {
        String label = (delta > 0 ? "+" : "") + delta + " level" + (Math.abs(delta) == 1 ? "" : "s");
        v.button(slot, Ui.item(m, (delta > 0 ? ChatColor.GREEN : ChatColor.RED) + label,
                Ui.lore().stat("Result", lvl + " -> " + clamp(lvl + delta))
                        .hint(delta < 0 ? "Click (asks to confirm)" : "Click to apply").build()),
                c -> {
                    Player t = Bukkit.getPlayer(targetId);
                    if (t == null) { Ui.fail(c.player(), "That player is no longer online."); return; }
                    setLevel(c, clamp(plugin.getSkillManager().getSkillLevel(t, skill) + delta));
                });
    }

    private static int clamp(int level) {
        return Math.max(1, Math.min(SkillProgression.MAX_LEVEL, level));
    }

    /**
     * Set an exact level. {@code expected} is the level the admin saw when deciding (and
     * confirming); if the player's level has moved since, nothing changes.
     */
    private void setLevel(MenuClick c, int newLevel) {
        Player admin = c.player();
        Player t = Bukkit.getPlayer(targetId);
        if (t == null) { Ui.fail(admin, "That player is no longer online."); return; }
        int expected = plugin.getSkillManager().getSkillLevel(t, skill);
        if (newLevel == expected) { Ui.info(admin, label() + " is already level " + expected + "."); return; }
        Runnable apply = () -> changePlayer(admin, targetId, target -> {
            SkillManager sm = plugin.getSkillManager();
            int before = sm.getSkillLevel(target, skill);
            if (before != expected) {
                menus.onPlayerThread(admin, () -> Ui.fail(admin, target.getName() + "'s " + label() + " changed to level "
                        + before + " meanwhile - nothing changed. Check and try again."));
                return;
            }
            if (!sm.setSkillLevel(target, skill, newLevel)) {
                menus.onPlayerThread(admin, () -> Ui.fail(admin, target.getName() + "'s skills are not loaded yet - nothing changed."));
                return;
            }
            panel.audit(admin, "set " + target.getName() + "'s " + label() + " " + before + " -> " + newLevel);
            notifyTarget(admin, target, "set your " + SkillNames.display(skill) + " to level " + newLevel + ".");
            menus.onPlayerThread(admin, () -> Ui.ok(admin, target.getName() + ": " + label() + " " + before + " -> " + newLevel));
        });
        if (newLevel < expected) {
            confirm(admin, "Lower " + SkillNames.display(skill) + "?", List.of(
                    t.getName() + ": level " + expected + " -> " + newLevel,
                    "XP into the level resets to 0.",
                    "Levels lost this way are not restored automatically."), p -> apply.run());
        } else {
            apply.run();
        }
    }

    /** Grant XP through the normal award path. {@link #FILL_TO_NEXT} = exactly what the next level needs, measured now. */
    private void grantXp(MenuClick c, long amount) {
        Player admin = c.player();
        changePlayer(admin, targetId, target -> {
            SkillManager sm = plugin.getSkillManager();
            if (!sm.hasLoadedSkills(target.getUniqueId())) {
                menus.onPlayerThread(admin, () -> Ui.fail(admin, target.getName() + "'s skills are not loaded yet - nothing changed."));
                return;
            }
            int before = sm.getSkillLevel(target, skill);
            if (before >= SkillProgression.MAX_LEVEL) {
                menus.onPlayerThread(admin, () -> Ui.info(admin, label() + " is already at max level."));
                return;
            }
            long grant = amount == FILL_TO_NEXT
                    ? Math.max(1, (long) SkillManager.xpForLevel(before) - sm.getSkillXP(target, skill))
                    : amount;
            sm.addXpQuietly(skill, grant, target); // an admin's grant: no level-up celebration
            int after = sm.getSkillLevel(target, skill);
            panel.audit(admin, "granted " + grant + " " + label() + " XP to " + target.getName() + " (level " + before + " -> " + after + ")");
            notifyTarget(admin, target, "granted you " + Fmt.grouped(grant) + " " + SkillNames.display(skill) + " XP.");
            menus.onPlayerThread(admin, () -> Ui.ok(admin, "+" + Fmt.grouped(grant) + " " + label() + " XP to " + target.getName()
                    + (after != before ? " (level " + before + " -> " + after + ")" : "")));
        });
    }
}
