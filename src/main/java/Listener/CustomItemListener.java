package Listener;

import Spells.SpellManager;
import org.bukkit.Material;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.ChatColor;

public class CustomItemListener implements Listener {
    private final SpellManager spellManager;

    public CustomItemListener(SpellManager spellManager) {
        this.spellManager = spellManager;
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        ItemStack item = event.getItem();
        if (item != null && item.getType() == Material.BLAZE_ROD) {
            if (item.getItemMeta() != null && ChatColor.stripColor(item.getItemMeta().getDisplayName()).equalsIgnoreCase("Test Fireball Rod")) {
                spellManager.castSpell("Fireball", event.getPlayer());
                event.getPlayer().sendMessage(ChatColor.RED + "You cast Fireball!");
            }
        }
    }
}
