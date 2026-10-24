package GUIs;

import cerberus.world.cerb.CerberusPlugin;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/**
 * The Admin Control Panel hotbar item. Thin facade over {@link GUIs.admin.AdminPanel}, which
 * owns the item, the permission check and the menus. Kept so existing callers stay unchanged.
 */
public class AdminChestGUI {
    private final CerberusPlugin plugin;

    public AdminChestGUI(CerberusPlugin plugin) {
        this.plugin = plugin;
    }

    /** True for the panel item (PDC-tagged, or an older one recognised by name). Grants nothing by itself. */
    public boolean isAdminChest(ItemStack item) {
        return plugin.getAdminPanel() != null && plugin.getAdminPanel().isPanelItem(item);
    }

    public ItemStack createAdminChest() {
        return plugin.getAdminPanel().createPanelItem();
    }

    /** Opens the Control Center - only for players with the admin permission. */
    public void open(Player player) {
        plugin.getAdminPanel().open(player);
    }

    /** @deprecated use {@link #open(Player)}. */
    @Deprecated
    public void openAdminGUI(Player player) {
        open(player);
    }

    public void givePlayerMenu(Player player) {
        plugin.getPlayerMenuGUI().placeMenuHead(player);
        player.sendMessage(ChatColor.GREEN + "You have been given the Player Menu item in the rightmost slot of your hotbar.");
    }
}
