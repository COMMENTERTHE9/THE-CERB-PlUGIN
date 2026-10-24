package GUIs.admin;

import GUIs.menu.Fmt;
import GUIs.menu.MenuView;
import GUIs.menu.Ui;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.Comparator;
import java.util.List;

/** All online players, paginated; click one to inspect. */
final class PlayerListMenu extends AdminMenu {

    private int page;

    PlayerListMenu(AdminPanel panel) { super(panel); }

    @Override public String title() { return PREFIX + "Players"; }

    @Override
    protected void build(MenuView v) {
        frame(v);
        v.refreshButton();
        List<Player> players = Bukkit.getOnlinePlayers().stream()
                .map(p -> (Player) p)
                .sorted(Comparator.comparing(Player::getName, String.CASE_INSENSITIVE_ORDER))
                .toList();
        v.set(MenuView.HEADER, Ui.item(Material.PLAYER_HEAD, Ui.HEAD + "" + ChatColor.BOLD + "Online players",
                Ui.lore().stat("Online", players.size() + " / " + Bukkit.getMaxPlayers())
                        .text("Click a player to inspect and edit").build()));
        page = v.page(players, page, p -> {
            PlayerVitals pv = PlayerVitals.of(plugin, p);
            return Ui.head(p, ChatColor.AQUA + p.getName(), Ui.lore()
                    .stat("Health", Fmt.compact(pv.hp()) + " / " + Fmt.compact(pv.maxHp()))
                    .stat("Mana", Fmt.compact(pv.mana()) + " / " + Fmt.compact(pv.maxMana()))
                    .stat("Total skill level", String.valueOf(pv.totalLevel()))
                    .stat("Session", pv.session())
                    .stat("World", p.getWorld().getName())
                    .hint("Click to inspect").build());
        }, p -> c -> open(c.player(), new PlayerInspectMenu(panel, p.getUniqueId())), np -> page = np);
        if (players.isEmpty()) {
            v.set(22, Ui.item(Material.GRAY_DYE, ChatColor.GRAY + "Nobody is online"));
        }
    }
}
