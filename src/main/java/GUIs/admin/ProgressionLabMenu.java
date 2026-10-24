package GUIs.admin;

import GUIs.menu.Fmt;
import GUIs.menu.MenuView;
import GUIs.menu.Status;
import GUIs.menu.Ui;
import Skills.SkillManager;
import Skills.SkillProgression;
import org.bukkit.ChatColor;
import org.bukkit.Material;

/** XP curve, kill-XP rules, XP sources and skill-effect status - with honest build status. */
final class ProgressionLabMenu extends AdminMenu {

    ProgressionLabMenu(AdminPanel panel) { super(panel); }

    @Override public String title() { return PREFIX + "Progression Lab"; }

    @Override
    protected void build(MenuView v) {
        frame(v);
        v.set(MenuView.HEADER, Ui.item(Material.EXPERIENCE_BOTTLE, ChatColor.GOLD + "" + ChatColor.BOLD + "Progression Lab",
                Ui.lore().text("How skills level today, and what is").text("still being built.").build()));

        // XP curve (live numbers from SkillManager.xpForLevel)
        Ui.Lore curve = Ui.lore().text("XP to go from level L to L+1:");
        long cumulative = 0;
        int[] marks = {1, 5, 10, 25, 50, 75, 99};
        int next = 0;
        for (int l = 1; l < SkillProgression.MAX_LEVEL; l++) {
            long cost = SkillManager.xpForLevel(l);
            if (next < marks.length && l == marks[next]) {
                curve.raw(ChatColor.GRAY + "  L" + l + " -> " + (l + 1) + ": " + ChatColor.WHITE + Fmt.grouped(cost)
                        + ChatColor.DARK_GRAY + "  (total so far " + Fmt.compact(cumulative) + ")");
                next++;
            }
            cumulative += cost;
        }
        curve.stat("Level 1 -> 100 in total", Fmt.grouped(cumulative) + " XP").gap().status(Status.LIVE);
        v.set(19, Ui.item(Material.EXPERIENCE_BOTTLE, ChatColor.GREEN + "XP curve", curve.build()));

        // Kill XP v2 (attributes.yml)
        var kx = plugin.getAttributesConfig().killXp();
        Ui.Lore kill = Ui.lore().text("Goes to the skill of the weapon used").text("(skills.yml: sword -> Blade Mastery...).");
        if (kx.enabled()) {
            kill.text("XP = base x (1 + slope x (level - 1)) x tier mult");
            kill.stat("base / slope", Fmt.one(kx.base()) + " / " + kx.levelSlope());
            for (Object[] row : new Object[][]{{"Common", 1, 1.0}, {"Common", 50, 1.0}, {"Rare", 50, 2.0}, {"Epic", 75, 3.0}, {"Primordial", 100, 15.0}}) {
                kill.raw(ChatColor.GRAY + "  " + row[0] + " L" + row[1] + ": " + ChatColor.WHITE
                        + Fmt.grouped(Math.round(Attributes.KillXp.compute((int) row[1], (double) row[2], 1, kx))) + " XP");
            }
            kill.text("Mobs " + kx.antiGrindGap() + "+ levels below your skill pay x" + kx.antiGrindFactor() + ".")
                    .stat("Ceiling per kill", Fmt.grouped((long) kx.maxPerKill())).gap().status(Status.LIVE);
        } else {
            kill.text("Kill XP v2 is switched off in attributes.yml;").text("using the old HP-based rule.").gap().status(Status.PARTIAL);
        }
        v.set(20, Ui.item(Material.ROTTEN_FLESH, ChatColor.YELLOW + "Kill XP", kill.build()));

        // XP sources
        v.set(21, Ui.item(Material.HOPPER, ChatColor.AQUA + "XP sources",
                Ui.lore().raw(ChatColor.GREEN + "Wired:")
                        .text("  kills (weapon skill), crit hits,").text("  damage taken (Survival, Heavy Armor),")
                        .text("  spell casts, new chunks (Navigation)")
                        .raw(ChatColor.RED + "Not wired yet:")
                        .text("  mining, woodcutting, farming, fishing,").text("  crafting, smithing, alchemy, cooking...")
                        .gap().status(Status.PARTIAL).build()));

        // Skill effects
        v.set(23, Ui.item(Material.KNOWLEDGE_BOOK, ChatColor.LIGHT_PURPLE + "Do skill levels change gameplay?",
                Ui.lore().raw(ChatColor.GREEN + "Yes:").text("  Heavy Armor Training (defense),").text("  Survival (resistances)")
                        .raw(ChatColor.YELLOW + "PvP only:").text("  Blade/Weapon Mastery, Critical Strike")
                        .raw(ChatColor.RED + "Not yet:").text("  most other skills; damage vs mobs")
                        .gap().status(Status.PARTIAL)
                        .text("Attributes 2.0 makes every skill feed").text("Vitality / Essence / Power / Fortitude /").text("Agility / Fortune, which combat reads.").build()));

        v.button(24, Ui.item(Material.ENCHANTED_BOOK, ChatColor.GOLD + "My skills",
                Ui.lore().text("Set your own levels and XP for testing.").hint("Click to open").build()),
                c -> open(c.player(), new SkillListMenu(panel, c.player().getUniqueId())));
        v.button(25, Ui.item(Material.NETHER_STAR, ChatColor.GRAY + "Attributes",
                Ui.lore().status(Status.IN_DEVELOPMENT).hint("Click to see the plan").build()),
                c -> open(c.player(), new AttributesDevMenu(panel, c.player().getUniqueId())));
    }
}
