package GUIs.player;

import GUIs.menu.ConfirmMenu;
import GUIs.menu.MenuView;
import GUIs.menu.Ui;
import Gathering.GatheringCatalog;
import Gathering.GearItems;
import Gathering.Tackle;
import cerberus.world.cerb.CerberusPlugin;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * Fishing → Equipment → Upgraded rod in your hand (docs/design/FISHING_EQUIPMENT.md §6): what converting the rod would
 * do with each upgrade (become your part, give its item back because you own the part, wait for its category, or stay on
 * the rod), and a Convert button that asks first. The conversion itself re-reads the rod and refuses if anything changed
 * since this preview ({@link Tackle#convert}).
 */
public final class LegacyRodMenu extends PlayerMenu {

    public LegacyRodMenu(CerberusPlugin plugin) {
        super(plugin);
    }

    @Override public String title() { return PlayerTheme.title("Convert a rod"); }

    @Override protected Material headerGlass() { return Material.ORANGE_STAINED_GLASS_PANE; }

    @Override
    protected void content(MenuView v) {
        Player p = v.viewer();
        Tackle tk = plugin.getTackle();
        if (tk == null || !GatheringCatalog.current().tackle().enabled()) {
            v.set(22, Ui.item(Material.BARRIER, ChatColor.GRAY + "Fishing equipment is off on this server", List.of()));
            return;
        }
        if (!loaded(p)) { loading(v); return; }
        ItemStack rod = p.getInventory().getItemInMainHand();
        if (rod.getType() != Material.FISHING_ROD || GearItems.upgrades(rod).isEmpty()) {
            v.set(22, Ui.item(Material.GRAY_DYE, ChatColor.GRAY + "No upgraded rod in your hand", Ui.lore()
                    .text("Hold a rod upgraded the old way").text("and open this screen again.").build()));
            return;
        }
        List<Tackle.Line> lines = tk.conversion(p, rod);
        Ui.Lore head = Ui.lore();
        for (String w : PlayerTheme.wrap("It keeps working as it is if you leave it. Converting moves its upgrades into your "
                + "setup, where they work with any rod. The rod keeps its enchantments, durability and name.", SkillView.WIDTH)) head.text(w);
        v.set(MenuView.HEADER, Ui.item(Material.FISHING_ROD, ChatColor.GOLD + "" + ChatColor.BOLD + "Convert this rod?", head.build()));

        Ui.Lore l = Ui.lore();
        int moving = 0;
        List<String> details = new ArrayList<>();
        for (Tackle.Line line : lines) {
            ChatColor c = switch (line.fate()) {
                case BECOMES_PART -> ChatColor.GREEN;
                case ITEM_BACK -> ChatColor.YELLOW;
                case STAYS -> ChatColor.GRAY;
            };
            l.raw(c + line.name() + ChatColor.DARK_GRAY + ": " + ChatColor.GRAY + line.note());
            details.add(line.name() + ": " + line.note());
            if (line.fate() != Tackle.Fate.STAYS) moving++;
        }
        v.set(22, Ui.item(Material.PAPER, ChatColor.GOLD + "What happens to each upgrade", l.build()));
        if (moving == 0) {
            v.set(31, Ui.item(Material.GRAY_DYE, ChatColor.GRAY + "Nothing to convert", Ui.lore().text("None of its upgrades is a setup part here.").build()));
            v.refreshButton();
            return;
        }
        ItemStack previewed = rod.clone();
        List<Tackle.Line> expected = List.copyOf(lines);
        v.button(31, Ui.item(Material.ANVIL, ChatColor.GREEN + "Convert", Ui.lore()
                .text(moving + " upgrade" + (moving == 1 ? "" : "s") + " move into your setup.")
                .hint("Click (asks first)").build()), c -> menus.open(c.player(), new ConfirmMenu(menus, null, "Convert this rod?", details, pl -> {
            Tackle tt = plugin.getTackle();
            if (tt == null) return;
            FishingEquipmentMenu.say(pl, tt.convert(pl, previewed, expected));
            menus.refresh(pl);
        })));
        v.refreshButton();
    }
}
