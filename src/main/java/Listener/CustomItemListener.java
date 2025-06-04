package Listener;

import Spells.SpellManager;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;                // <<< NEW
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;                // <<< NEW
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

public class CustomItemListener implements Listener {
    private static final String TEST_ROD_NAME = "Test Fireball Rod";   // <<< NEW

    private final SpellManager spellManager;

    public CustomItemListener(SpellManager spellManager) {
        this.spellManager = spellManager;
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)  // <<< NEW flags
    public void onPlayerInteract(PlayerInteractEvent event) {

        // EARLY‑EXIT: only care about right‑clicks
        Action action = event.getAction();
        if (action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK) return;

        Player     player = event.getPlayer();
        ItemStack  item   = event.getItem();
        if (item == null || item.getType() != Material.BLAZE_ROD) return;

        ItemMeta meta = item.getItemMeta();
        if (meta == null) return;

        String name = ChatColor.stripColor(meta.getDisplayName());
        if (!TEST_ROD_NAME.equalsIgnoreCase(name)) return;  // <<< use constant

        // ---- At this point we know it's our Test Fireball Rod ----
        spellManager.castSpell("Fireball", player);
        player.sendMessage(ChatColor.RED + "You cast Fireball!");
    }
}
