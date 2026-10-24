package GUIs.admin;

import GUIs.menu.Fmt;
import GUIs.menu.MenuView;
import GUIs.menu.Status;
import GUIs.menu.Ui;
import Manager.MobVirtualHealthManager;
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

        // Kill XP (current rule)
        double flat = SkillProgression.killXp(MobVirtualHealthManager.COSMETIC_MAX_HEALTH, MobVirtualHealthManager.COSMETIC_MAX_HEALTH, 1.0);
        v.set(20, Ui.item(Material.ROTTEN_FLESH, ChatColor.YELLOW + "Kill XP (current rule)",
                Ui.lore().text("Goes to the skill of the weapon used").text("(skills.yml: sword -> Blade Mastery...).")
                        .stat("Every scaled mob", Fmt.one(flat) + " x its registry multiplier")
                        .text("Bounded and safe, but the same for a").text("level-1 and a level-100 mob.")
                        .gap().status(Status.PARTIAL)
                        .text("Next: Kill XP v2 by mob level x tier").text("(approved; part of Attributes 2.0).").build()));

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
