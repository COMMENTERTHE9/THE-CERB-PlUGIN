package GUIs.admin;

import GUIs.menu.Fmt;
import GUIs.menu.MenuView;
import GUIs.menu.Ui;
import Manager.CombatConfig;
import Manager.Tier;
import org.bukkit.ChatColor;
import org.bukkit.Material;

import java.util.List;

/** The 10-tier ladder at a chosen level: HP pool, damage, armor penetration, XP. */
final class TierTableMenu extends AdminMenu {

    private static final int[] LEVELS = {1, 10, 25, 50, 75, 100};
    private static final Material[] ICONS = {
            Material.GRAY_WOOL, Material.WHITE_WOOL, Material.LIME_WOOL, Material.BLUE_WOOL, Material.PURPLE_WOOL,
            Material.ORANGE_WOOL, Material.YELLOW_WOOL, Material.RED_WOOL, Material.BROWN_WOOL, Material.MAGENTA_WOOL};

    private int level = 1;

    TierTableMenu(AdminPanel panel) { super(panel); }

    @Override public String title() { return PREFIX + "Tier reference"; }

    @Override
    protected void build(MenuView v) {
        frame(v);
        CombatConfig cc = plugin.getCombatConfig();
        v.set(MenuView.HEADER, Ui.item(Material.BOOK, ChatColor.WHITE + "" + ChatColor.BOLD + "Tiers at level " + level,
                Ui.lore().text("From combat.yml (mob-health-scaling,").text("mob-damage-scaling) and the Tier ladder.")
                        .text("Pick a level in the bottom row.").build()));
        List<Tier> tiers = List.of(Tier.values());
        v.page(tiers, 0, tier -> Ui.item(ICONS[tier.ordinal() % ICONS.length], tier.displayColor + "" + ChatColor.BOLD + tier.displayName,
                Ui.lore().stat("HP pool", Fmt.compact(cc.mobHealthScaling.calculate(tier, level)))
                        .stat("Damage multiplier", Fmt.mult(cc.mobDamageScaling.calculate(tier, level)))
                        .stat("Armor penetration", Fmt.pct(tier.armorPenetration))
                        .stat("Kill XP multiplier", Fmt.mult(tier.xpMultiplier)).build()), null, p -> {});
        for (int i = 0, slot = 37, prev = -1; i < LEVELS.length; i++) {
            int lv = Math.min(cc.mobHealthScaling.getMaxLevel(), LEVELS[i]);
            if (lv == prev) continue; // levels above max-level collapse into one button
            prev = lv;
            v.button(slot++, Ui.glow(Ui.amount(Ui.item(lv == level ? Material.GOLD_BLOCK : Material.IRON_BLOCK,
                    ChatColor.WHITE + "Level " + lv, Ui.lore().hint("Click to show this level").build()), Math.min(64, lv)), lv == level),
                    c -> { level = lv; menus.refresh(c.player()); });
        }
    }
}
