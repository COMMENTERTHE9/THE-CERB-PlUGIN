package Listener;

import cerberus.world.cerb.QualityBuffs;
import Manager.PlayerVirtualHealthManager;
import Manager.PlayerManaManager;
import Manager.PlayerDefenseManager;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.event.player.PlayerArmorStandManipulateEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;

public class PlayerItemListener implements Listener {

    private final QualityBuffs qualityBuffs;
    private final PlayerVirtualHealthManager virtualHealthManager;
    private final PlayerManaManager manaManager;
    private final PlayerDefenseManager defenseManager;

    public PlayerItemListener(QualityBuffs qualityBuffs, PlayerVirtualHealthManager virtualHealthManager, PlayerManaManager manaManager, PlayerDefenseManager defenseManager) {
        this.qualityBuffs = qualityBuffs;
        this.virtualHealthManager = virtualHealthManager;
        this.manaManager = manaManager;
        this.defenseManager = defenseManager;
    }

    // When player switches the item in their main hand
    @EventHandler
    public void onPlayerItemHeld(PlayerItemHeldEvent event) {
        Player player = event.getPlayer();
        ItemStack newItem = player.getInventory().getItem(event.getNewSlot());
        ItemStack oldItem = player.getInventory().getItem(event.getPreviousSlot());

        // Remove buffs from the previously held item
        if (oldItem != null) {
            qualityBuffs.removeQualityBuffs(player);
        }

        // Apply buffs to the newly held item in the main hand
        if (newItem != null) {
            qualityBuffs.applyQualityBuffs(player, newItem);
        }

        // Also check if the off-hand item has any buffs to apply
        ItemStack offHandItem = player.getInventory().getItemInOffHand();
        if (offHandItem != null) {
            qualityBuffs.applyQualityBuffs(player, offHandItem);
        }
    }

    // When player equips or unequips armor
    @EventHandler
    public void onPlayerArmorEquip(PlayerArmorStandManipulateEvent event) {
        Player player = (Player) event.getPlayer();
        ItemStack armorPiece = event.getArmorStandItem();

        // Apply armor buffs when equipped
        if (armorPiece != null) {
            qualityBuffs.applyQualityBuffs(player, armorPiece);
        } else {
            // Remove armor buffs when unequipped
            qualityBuffs.removeQualityBuffs(player);
        }
    }

    // When player interacts with items (such as right-clicking with a tool or weapon)
    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        ItemStack mainHandItem = player.getInventory().getItemInMainHand();
        ItemStack offHandItem = player.getInventory().getItemInOffHand();

        // Apply buffs when interacting with a tool or weapon in the main hand
        if (mainHandItem != null) {
            qualityBuffs.applyQualityBuffs(player, mainHandItem);
        }

        // Apply buffs when interacting with an item in the off-hand if applicable
        if (offHandItem != null) {
            qualityBuffs.applyQualityBuffs(player, offHandItem);
        }
    }

    // When player consumes an item (like food or potion)
    @EventHandler
    public void onPlayerConsume(PlayerItemConsumeEvent event) {
        Player player = event.getPlayer();
        ItemStack consumedItem = event.getItem();

        // Apply buffs if the consumed item has relevant buffs (e.g., custom potion or food)
        if (consumedItem != null) {
            qualityBuffs.applyQualityBuffs(player, consumedItem);
        }
    }

    // When player swaps items between main hand and offhand
    @EventHandler
    public void onPlayerSwapItems(PlayerSwapHandItemsEvent event) {
        Player player = event.getPlayer();
        ItemStack newMainHandItem = event.getOffHandItem();
        ItemStack newOffHandItem = event.getMainHandItem();

        // Apply buffs to the new main hand item
        if (newMainHandItem != null) {
            qualityBuffs.applyQualityBuffs(player, newMainHandItem);
        }

        // Apply buffs to the new off-hand item
        if (newOffHandItem != null) {
            qualityBuffs.applyQualityBuffs(player, newOffHandItem);
        }
    }

    // When player clicks in the inventory (for equipping items or switching slots)
    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) return;

        Player player = (Player) event.getWhoClicked();
        ItemStack clickedItem = event.getCurrentItem();

        // Apply buffs if the clicked item is relevant
        if (clickedItem != null) {
            qualityBuffs.applyQualityBuffs(player, clickedItem);
        } else {
            // Remove buffs if the player is unequipping or moving the item
            qualityBuffs.removeQualityBuffs(player);
        }
    }
}
