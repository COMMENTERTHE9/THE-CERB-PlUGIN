package GUIs.admin;

import Attributes.Attribute;
import Attributes.AttributeSnapshot;
import Attributes.WeaponClass;
import GUIs.menu.Fmt;
import GUIs.menu.MenuView;
import GUIs.menu.Ui;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.Comparator;
import java.util.List;

/** Attributes section: pick a player (yourself first) to inspect, simulate and buff. */
final class AttributesLabMenu extends AdminMenu {

    private int page;

    AttributesLabMenu(AdminPanel panel) {
        super(panel);
    }

    @Override public String title() { return PREFIX + "Attributes"; }

    @Override
    protected void build(MenuView v) {
        frame(v);
        v.refreshButton();
        Player me = v.viewer();
        List<Player> players = Bukkit.getOnlinePlayers().stream().map(p -> (Player) p)
                .sorted(Comparator.comparing((Player p) -> !p.getUniqueId().equals(me.getUniqueId()))
                        .thenComparing(Player::getName, String.CASE_INSENSITIVE_ORDER))
                .toList();
        v.set(MenuView.HEADER, Ui.item(Material.NETHER_STAR, ChatColor.LIGHT_PURPLE + "" + ChatColor.BOLD + "Attributes",
                Ui.lore().text("Derived from skills; combat reads them.")
                        .text("Pick a player to see sources, simulate")
                        .text("skill levels, or give test buffs.").build()));
        page = v.page(players, page, p -> {
            AttributeSnapshot s = plugin.getAttributeService().get(p.getUniqueId());
            boolean self = p.getUniqueId().equals(me.getUniqueId());
            return Ui.head(p, ChatColor.AQUA + p.getName() + (self ? ChatColor.GRAY + " (you)" : ""), Ui.lore()
                    .stat("Power (swords)", Fmt.one(s.power(WeaponClass.SWORD)) + "  " + Fmt.mult(s.powerMultiplier(WeaponClass.SWORD)))
                    .stat("Agility", Fmt.one(s.get(Attribute.AGILITY)) + "  crit " + Fmt.pct(s.critChance()))
                    .stat("Vitality", Fmt.one(s.get(Attribute.VITALITY)) + "  health " + Fmt.mult(s.maxHpMultiplier()))
                    .stat("Fortitude", Fmt.one(s.get(Attribute.FORTITUDE)))
                    .stat("Essence", Fmt.one(s.get(Attribute.ESSENCE)))
                    .hint("Click to inspect").build());
        }, p -> c -> open(c.player(), new AttributesInspectMenu(panel, p.getUniqueId())), np -> page = np);
    }
}
