package GUIs;

import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;

/**
 * Simple placeholder GUI for features that are not yet implemented.
 * Shows a barrier icon with the feature name and a description,
 * plus a back arrow to return to the player menu.
 */
public class ComingSoonGUI {

    private final JavaPlugin plugin;

    public ComingSoonGUI(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Open a "Coming Soon" placeholder GUI for the given feature.
     *
     * @param player      the player to show the GUI to
     * @param featureName display name of the unimplemented feature
     * @param description short explanation shown in the lore
     */
    public void open(Player player, String featureName, String description) {
        Inventory inv = GuiBuilder.createGui(GuiIdentity.COMING_SOON, 3,
                ChatColor.GOLD + featureName);

        // Fill borders with black glass
        GuiBuilder.fillBorders(inv, Material.BLACK_STAINED_GLASS_PANE);

        // Center slot: barrier with feature info
        inv.setItem(13, GuiBuilder.createItem(Material.BARRIER,
                ChatColor.YELLOW + featureName + " \u2014 Coming Soon",
                List.of("",
                        ChatColor.GRAY + description,
                        "",
                        ChatColor.DARK_GRAY + "This feature is under development.")));

        // Back arrow at slot 18 (bottom-left interior)
        inv.setItem(18, GuiBuilder.createBackArrow("player_menu"));

        // Fill remaining empty slots with black glass
        GuiBuilder.fillEmpty(inv, Material.BLACK_STAINED_GLASS_PANE);

        // Folia-safe: open on the player's region thread
        player.getScheduler().run(plugin, (ScheduledTask t) -> player.openInventory(inv), null);
    }
}
