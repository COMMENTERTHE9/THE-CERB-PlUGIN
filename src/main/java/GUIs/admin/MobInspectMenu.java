package GUIs.admin;

import GUIs.menu.MenuView;
import GUIs.menu.Ui;
import Manager.MobTestService;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.LivingEntity;

import java.util.List;

/** One mob: full stats and safe test actions. Changes run on the mob's own region thread. */
final class MobInspectMenu extends AdminMenu {

    private final LivingEntity mob;

    MobInspectMenu(AdminPanel panel, LivingEntity mob) {
        super(panel);
        this.mob = mob;
    }

    @Override public String title() { return PREFIX + "Mob"; }

    @Override
    protected void build(MenuView v) {
        frame(v);
        v.refreshButton();
        if (!mob.isValid()) {
            v.set(22, Ui.item(Material.GRAY_DYE, ChatColor.GRAY + "This mob is gone",
                    Ui.lore().text("It died, despawned or was removed.").build()));
            return;
        }
        if (!Bukkit.isOwnedByCurrentRegion(mob)) {
            v.set(22, Ui.item(Material.GRAY_DYE, ChatColor.GRAY + "This mob is too far away",
                    Ui.lore().text("Walk closer to it and press Refresh.").build()));
            return;
        }
        MobTestService tools = panel.mobTools();
        MobTestService.MobInfo info = tools.describe(mob, v.viewer().getLocation());
        v.set(MenuView.HEADER, NearbyMobsMenu.render(info));

        v.button(19, Ui.item(Material.GOLDEN_APPLE, ChatColor.GREEN + "Heal to full",
                Ui.lore().text("Pool, label and health bar.").hint("Click").build()),
                c -> changeMob(c.player(), mob, m -> tools.setHealthFraction(m, 1.0)));
        v.button(20, Ui.item(Material.APPLE, ChatColor.YELLOW + "Set HP to 50%",
                Ui.lore().hint("Click").build()),
                c -> changeMob(c.player(), mob, m -> tools.setHealthFraction(m, 0.5)));
        v.button(21, Ui.item(Material.POISONOUS_POTATO, ChatColor.RED + "Set HP to 10%",
                Ui.lore().text("One good hit should finish it.").hint("Click").build()),
                c -> changeMob(c.player(), mob, m -> tools.setHealthFraction(m, 0.1)));

        v.button(23, Ui.glow(Ui.item(info.ai() ? Material.REDSTONE_TORCH : Material.LEVER,
                ChatColor.WHITE + "AI: " + (info.ai() ? ChatColor.RED + "on" : ChatColor.AQUA + "off"),
                Ui.lore().text("Off = punching bag (no attacks, no movement).").hint("Click to switch").build()), info.ai()),
                c -> changeMob(c.player(), mob, tools::toggleAi));
        v.button(24, Ui.glow(Ui.item(Material.GLOWSTONE_DUST, ChatColor.WHITE + "Highlight: " + (mob.isGlowing() ? "on" : "off"),
                Ui.lore().text("Outline visible through walls.").hint("Click to switch").build()), mob.isGlowing()),
                c -> changeMob(c.player(), mob, m -> m.setGlowing(!m.isGlowing())));

        boolean test = info.testSpawn();
        v.button(25, Ui.item(Material.BARRIER, ChatColor.RED + "Remove this mob",
                Ui.lore().text("Deletes it: no death, drops or XP.")
                        .raw(test ? ChatColor.DARK_AQUA + "Test spawn" : ChatColor.RED + "Not a test spawn - asks to confirm")
                        .hint("Click").build()),
                c -> {
                    Runnable remove = () -> changeMob(c.player(), mob, m -> {
                        panel.audit(c.player(), "removed " + m.getType() + " (" + info.mobId() + ", level " + info.level() + ")");
                        tools.remove(m);
                        menus.onPlayerThread(c.player(), () -> { Ui.ok(c.player(), "Removed."); menus.back(c.player()); });
                    });
                    if (test) remove.run();
                    else confirm(c.player(), "Remove this mob?", List.of(
                            "It was not spawned for testing.", "It is deleted with no death, drops or XP."), p -> remove.run());
                });
    }
}
