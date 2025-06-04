package Listener;

import Manager.PlayerDefenseManager;
import Manager.PlayerHUDManager;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;             // <<< NEW
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryType;    // <<< NEW
import org.bukkit.event.inventory.InventoryType.SlotType;  // <<< NEW

public class CustomArmorListener implements Listener {

    private final PlayerDefenseManager defenseManager;
    private final PlayerHUDManager hudManager;

    public CustomArmorListener(PlayerDefenseManager defenseManager,
                               PlayerHUDManager hudManager) {
        this.defenseManager = defenseManager;
        this.hudManager     = hudManager;
    }

    /** Recalculate defense & update HUD */
    private void refreshDefense(Player p) {
        defenseManager.updateDefense(p);
        hudManager.updateHUD(p);
    }

    // ------------------------------------------------------------
    // Only recalc when an armor slot is clicked/shift‑clicked
    // ------------------------------------------------------------
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)  // <<< NEW flags
    public void onInventoryClick(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player p)) return;         // EARLY‑EXIT

        // Only care about armor‐slot interactions
        if (e.getSlotType() != SlotType.ARMOR) return;                // EARLY‑EXIT

        refreshDefense(p);
    }

    // ------------------------------------------------------------
    // Also recalc on inventory close for PLAYER inventory
    // ------------------------------------------------------------
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)  // <<< NEW flags
    public void onInventoryClose(InventoryCloseEvent e) {
        if (!(e.getPlayer() instanceof Player p)) return;             // EARLY‑EXIT

        // Only for the player's own inventory, not chests or other UIs
        if (e.getView().getType() != InventoryType.PLAYER) return;    // EARLY‑EXIT

        refreshDefense(p);
    }
}
