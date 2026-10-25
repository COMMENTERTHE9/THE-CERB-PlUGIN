package GUIs.menu;

import org.bukkit.ChatColor;
import org.bukkit.Material;

import java.util.List;
import java.util.function.Consumer;

import org.bukkit.entity.Player;

/**
 * Yes/No dialog for actions that lose data or remove things. Inherits the permission of the
 * screen that opened it, and the confirm action re-checks it like any other click.
 */
public final class ConfirmMenu extends Menu {

    private final String permission;
    private final String question;
    private final List<String> details;
    private final Consumer<Player> onConfirm;

    public ConfirmMenu(MenuManager menus, String permission, String question, List<String> details,
                       Consumer<Player> onConfirm) {
        super(menus);
        this.permission = permission;
        this.question = question;
        this.details = details;
        this.onConfirm = onConfirm;
    }

    @Override public String title() { return ChatColor.DARK_RED + "Confirm: " + ChatColor.stripColor(question); }
    @Override public int rows() { return 3; }
    @Override public String permission() { return permission; }

    /** Confirm acts at once: always its own window, never a redraw of the previous one. */
    @Override public boolean reuseWindow() { return false; }

    @Override
    protected void build(MenuView v) {
        Ui.Lore lore = Ui.lore();
        for (String d : details) lore.text(d);
        v.set(4, Ui.item(Material.PAPER, ChatColor.GOLD + question, lore.build()));
        v.button(11, Ui.item(Material.LIME_CONCRETE, ChatColor.GREEN + "" + ChatColor.BOLD + "Yes, do it",
                Ui.lore().text("Apply this change").build()), c -> {
            menus.back(c.player());       // return to the screen that asked
            onConfirm.accept(c.player());  // then apply (it refreshes that screen itself)
        });
        v.button(15, Ui.item(Material.RED_CONCRETE, ChatColor.RED + "" + ChatColor.BOLD + "Cancel",
                Ui.lore().text("Go back without changing anything").build()), c -> {
            Ui.info(c.player(), "Cancelled - nothing was changed.");
            menus.back(c.player());
        });
        for (int i = 0; i < 27; i++) if (i != 4 && i != 11 && i != 15) v.set(i, Ui.filler(Material.GRAY_STAINED_GLASS_PANE));
    }
}
