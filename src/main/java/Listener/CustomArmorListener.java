package Listener;

import Manager.PlayerDefenseManager;
import Manager.PlayerHUDManager;
import cerberus.world.cerb.CerberusPlugin;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.inventory.InventoryType.SlotType;

public class CustomArmorListener implements Listener {

    private final PlayerDefenseManager defenseManager;
    private final PlayerHUDManager hudManager;
    private final CerberusPlugin plugin;

    public CustomArmorListener(PlayerDefenseManager defenseManager,
                               PlayerHUDManager hudManager,
                               CerberusPlugin plugin) {
        this.defenseManager = defenseManager;
        this.hudManager     = hudManager;
        this.plugin         = plugin;
    }

    /** Recalculate defense & update HUD (delayed 1 tick so inventory state is current). */
    private void refreshDefenseNextTick(Player p) {
        p.getScheduler().run(plugin, task -> {
            if (p.isOnline()) {
                defenseManager.updateDefense(p);
                hudManager.updateHUD(p);
            }
        }, null);
    }

    // ------------------------------------------------------------
    // Recalc when an armor slot is clicked/shift‑clicked
    // Delayed 1 tick because the item swap hasn't happened yet
    // ------------------------------------------------------------
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player p)) return;

        // Armor slot click OR shift-click (which could move armor)
        if (e.getSlotType() == SlotType.ARMOR || e.isShiftClick()) {
            refreshDefenseNextTick(p);
        }
    }

    // ------------------------------------------------------------
    // Recalc on inventory close (CRAFTING = player inventory screen)
    // ------------------------------------------------------------
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onInventoryClose(InventoryCloseEvent e) {
        if (!(e.getPlayer() instanceof Player p)) return;

        // Player inventory screen is CRAFTING type, not PLAYER
        if (e.getView().getType() != InventoryType.CRAFTING) return;

        refreshDefenseNextTick(p);
    }
}
