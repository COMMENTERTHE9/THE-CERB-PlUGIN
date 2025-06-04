package Listener;

import cerberus.world.cerb.QualityBuffs;
import Manager.PlayerDefenseManager;
import Manager.PlayerManaManager;
import Manager.PlayerVirtualHealthManager;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;          // <<< NEW
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.*;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

/**
 * Applies / removes Quality‑Buffs whenever the player equips, swaps,
 * consumes or otherwise touches an item that carries a quality tag.
 */
public class PlayerItemListener implements Listener {

    private final QualityBuffs qualityBuffs;
    private final PlayerVirtualHealthManager virtualHealthManager;  // (unused for now)
    private final PlayerManaManager manaManager;                    // (unused for now)
    private final PlayerDefenseManager defenseManager;              // (unused for now)

    public PlayerItemListener(QualityBuffs buffs,
                              PlayerVirtualHealthManager vh,
                              PlayerManaManager mana,
                              PlayerDefenseManager def) {
        this.qualityBuffs       = buffs;
        this.virtualHealthManager = vh;
        this.manaManager        = mana;
        this.defenseManager     = def;
    }

    /* -----------------------------------------------------------
       Helper: true if stack isn’t null/air and has any ItemMeta
       ----------------------------------------------------------- */
    private boolean isValid(ItemStack stack) {                      // <<< NEW
        return stack != null && stack.getType().isItem() && stack.hasItemMeta();
    }

    // ----------------------------------------------------------------
    // MAIN‑HAND SCROLL (number‑keys or wheel) → apply / remove buffs
    // ----------------------------------------------------------------
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onItemHeld(PlayerItemHeldEvent e) {

        Player    p   = e.getPlayer();
        ItemStack old = p.getInventory().getItem(e.getPreviousSlot());
        ItemStack neu = p.getInventory().getItem(e.getNewSlot());

        if (isValid(old)) qualityBuffs.removeQualityBuffs(p);       // remove old
        if (isValid(neu)) qualityBuffs.applyQualityBuffs(p, neu);   // apply new

        // off‑hand might still give buffs:
        ItemStack off = p.getInventory().getItemInOffHand();
        if (isValid(off)) qualityBuffs.applyQualityBuffs(p, off);
    }

    // ----------------------------------------------------------------
    // ARMOR swap via right‑click / hot‑swap
    // ----------------------------------------------------------------
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onArmorSwap(PlayerArmorStandManipulateEvent e) {

        if (!(e.getPlayer() instanceof Player p)) return;           // safety
        ItemStack item = e.getArmorStandItem();

        if (isValid(item)) qualityBuffs.applyQualityBuffs(p, item);
        else               qualityBuffs.removeQualityBuffs(p);
    }

    // ----------------------------------------------------------------
    // Any right‑click interaction with held items
    // ----------------------------------------------------------------
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent e) {
        Player p = e.getPlayer();

        ItemStack main = p.getInventory().getItemInMainHand();
        if (isValid(main)) qualityBuffs.applyQualityBuffs(p, main);

        ItemStack off  = p.getInventory().getItemInOffHand();
        if (isValid(off))  qualityBuffs.applyQualityBuffs(p, off);
    }

    // ----------------------------------------------------------------
    // Consuming food / potions → buffs from consumable
    // ----------------------------------------------------------------
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onConsume(PlayerItemConsumeEvent e) {
        ItemStack item = e.getItem();
        if (isValid(item))
            qualityBuffs.applyQualityBuffs(e.getPlayer(), item);
    }

    // ----------------------------------------------------------------
    // Q‑swap main/off‑hand
    // ----------------------------------------------------------------
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onSwap(PlayerSwapHandItemsEvent e) {
        Player p = e.getPlayer();

        if (isValid(e.getOffHandItem()))
            qualityBuffs.applyQualityBuffs(p, e.getOffHandItem());
        if (isValid(e.getMainHandItem()))
            qualityBuffs.applyQualityBuffs(p, e.getMainHandItem());
    }

    // ----------------------------------------------------------------
    // Inventory clicks (equip, move, hot‑bar swap)
    // ----------------------------------------------------------------
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onInvClick(InventoryClickEvent e) {

        if (!(e.getWhoClicked() instanceof Player p)) return;
        ItemStack clicked = e.getCurrentItem();

        if (isValid(clicked)) qualityBuffs.applyQualityBuffs(p, clicked);
        else                  qualityBuffs.removeQualityBuffs(p);
    }
}
