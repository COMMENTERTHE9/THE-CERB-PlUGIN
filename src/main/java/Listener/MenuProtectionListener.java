package Listener;

import cerberus.world.cerb.CerberusPlugin;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;                         // <<< NEW
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.InventoryView;

import java.util.Set;                                         // <<< NEW

public class MenuProtectionListener implements Listener {
    private final CerberusPlugin plugin;

    // <<< NEW: define protected inventory titles in one place
    private static final Set<String> PROTECTED_TITLES = Set.of(
            "Main Player Menu",
            "Skill Management",
            "Combat Skills",
            "Magic Skills",
            "Utility Skills",
            "Admin Control Panel",
            "Player Management",
            "Server Settings",
            "Advanced Settings"
    );

    public MenuProtectionListener(CerberusPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)  // <<< UPDATED
    public void onInventoryClick(InventoryClickEvent event) {
        InventoryView view = event.getView();                               // <<< NEW
        if (view == null) return;                                           // <<< EARLY EXIT

        String title = view.getTitle();
        if (!PROTECTED_TITLES.contains(title)) return;                      // <<< EARLY EXIT

        // Prevent moving items in protected inventories
        event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)  // <<< UPDATED
    public void onInventoryDrag(InventoryDragEvent event) {
        String title = event.getView().getTitle();
        if (!PROTECTED_TITLES.contains(title)) return;                      // <<< EARLY EXIT

        // Prevent dragging items in protected inventories
        event.setCancelled(true);
    }
}
