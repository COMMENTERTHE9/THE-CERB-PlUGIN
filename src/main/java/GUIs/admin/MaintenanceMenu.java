package GUIs.admin;

import GUIs.menu.MenuView;
import GUIs.menu.Ui;
import org.bukkit.ChatColor;
import org.bukkit.Material;

import java.util.List;

/** Safe maintenance actions that already exist in the plugin, behind confirmation. */
final class MaintenanceMenu extends AdminMenu {

    MaintenanceMenu(AdminPanel panel) { super(panel); }

    @Override public String title() { return PREFIX + "Maintenance"; }

    @Override
    protected void build(MenuView v) {
        frame(v);
        v.set(MenuView.HEADER, Ui.item(Material.ANVIL, ChatColor.WHITE + "" + ChatColor.BOLD + "Maintenance",
                Ui.lore().text("Only actions the plugin already supports.").build()));

        v.button(20, Ui.item(Material.WRITABLE_BOOK, ChatColor.GOLD + "Reload configuration",
                Ui.lore().text("Re-reads combat.yml, mobs.yml, skills.yml.").text("Same as /cerb reload. Player data is untouched.")
                        .hint("Click (asks to confirm)").build()),
                c -> confirm(c.player(), "Reload configuration?",
                        List.of("Re-reads combat.yml, mobs.yml and skills.yml.", "Mobs already spawned keep their HP pools."),
                        p -> {
                            plugin.reloadAll();
                            panel.audit(p, "reloaded configuration");
                            Ui.ok(p, "Configuration reloaded.");
                        }));

        v.button(22, Ui.item(Material.CHEST, ChatColor.GREEN + "Save all players now",
                Ui.lore().text("Writes every online player's skills to").text("the database (normally automatic).")
                        .text("Same as /cerb backup.").hint("Click").build()),
                c -> {
                    plugin.saveAllPlayers();
                    panel.audit(c.player(), "saved all players");
                    Ui.ok(c.player(), "Saving all online players (see the server log).");
                });

        v.set(24, Ui.item(Material.BOOKSHELF, ChatColor.AQUA + "Backups",
                Ui.lore().text("run.bat / start.bat back up plugin data").text("before every start (last 10 kept).")
                        .text("backup.bat copies worlds + data while")
                        .text("the server is stopped.").text("Folder: test-server/backups/").build()));

        v.set(31, Ui.item(Material.STRUCTURE_VOID, ChatColor.DARK_GRAY + "Not available here (by design)",
                Ui.lore().text("No console commands, world editing,").text("world resets or player-data wipes.")
                        .text("Those need an explicit decision first.").build()));
    }
}
