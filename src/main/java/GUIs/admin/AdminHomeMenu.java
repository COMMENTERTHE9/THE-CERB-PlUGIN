package GUIs.admin;

import GUIs.menu.Fmt;
import GUIs.menu.MenuView;
import GUIs.menu.Status;
import GUIs.menu.Ui;
import cerberus.world.cerb.BuildInfo;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.lang.management.ManagementFactory;
import java.util.List;

/** Control Center start screen: live summary + one tile per registered section. */
final class AdminHomeMenu extends AdminMenu {

    /** Tile positions: 4 / 3 / 4 per row, centered. */
    private static final int[] TILE_SLOTS = {19, 21, 23, 25, 29, 31, 33, 37, 39, 41, 43};

    AdminHomeMenu(AdminPanel panel) { super(panel); }

    @Override public String title() { return PREFIX + "Control Center"; }

    @Override
    protected void build(MenuView v) {
        Player me = v.viewer();
        v.frame(HEADER_GLASS, false, false);
        v.refreshButton();

        long uptime = ManagementFactory.getRuntimeMXBean().getUptime();
        int online = Bukkit.getOnlinePlayers().size();
        v.set(MenuView.HEADER, Ui.item(Material.ENDER_EYE, ChatColor.LIGHT_PURPLE + "" + ChatColor.BOLD + "Cerberus Control Center",
                Ui.lore()
                        .stat("Plugin", "v" + plugin.getPluginMeta().getVersion() + "  build " + BuildInfo.buildTime())
                        .stat("Server", Bukkit.getName() + " " + Bukkit.getMinecraftVersion())
                        .stat("Players online", online + " / " + Bukkit.getMaxPlayers())
                        .stat("Uptime", Fmt.duration(uptime))
                        .stat("Mob HP pools tracked", Fmt.grouped(plugin.getMobVirtualHealthManager().size()))
                        .gap()
                        .text("Signed in as " + me.getName() + " (" + AdminPanel.PERMISSION + ")")
                        .build()));

        List<AdminPanel.Section> sections = panel.sections();
        for (int i = 0; i < sections.size() && i < TILE_SLOTS.length; i++) {
            AdminPanel.Section s = sections.get(i);
            Ui.Lore lore = Ui.lore();
            for (String d : s.description()) lore.text(d);
            lore.gap().status(s.status());
            lore.hint(s.status() == Status.IN_DEVELOPMENT ? "Click to see the plan" : "Click to open");
            ChatColor color = s.status() == Status.IN_DEVELOPMENT ? ChatColor.GRAY : Ui.HEAD;
            v.button(TILE_SLOTS[i], Ui.item(s.icon(), color + "" + ChatColor.BOLD + s.name(), lore.build()),
                    c -> open(c.player(), s.opener().apply(panel)));
        }
    }
}
