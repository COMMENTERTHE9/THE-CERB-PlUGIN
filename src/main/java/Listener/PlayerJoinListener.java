package Listener;

import Skills.SkillManager;
import cerberus.world.cerb.CerberusPlugin;
import GUIs.SkillGUI;
import GUIs.PlayerMenuGUI;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.EventPriority;
import org.bukkit.event.block.Action;
import org.bukkit.inventory.ItemStack;

/**
 * Handles player GUI interactions (menu head right-click).
 * Join/quit lifecycle moved to PlayerSessionManager.
 */
public class PlayerJoinListener implements Listener {
    private final SkillManager skillManager;
    private final CerberusPlugin plugin;
    private final SkillGUI skillGUI;
    private final PlayerMenuGUI playerMenuGUI;

    public PlayerJoinListener(SkillManager skillManager,
                              CerberusPlugin plugin,
                              SkillGUI skillGUI,
                              PlayerMenuGUI playerMenuGUI) {
        this.skillManager = skillManager;
        this.plugin = plugin;
        this.skillGUI = skillGUI;
        this.playerMenuGUI = playerMenuGUI;
    }

    // ---------------------------
    // Player interact → open skill GUI
    // ---------------------------
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = false)
    public void onPlayerInteract(PlayerInteractEvent event) {
        // Only respond to right-clicks
        if (event.getAction() != Action.RIGHT_CLICK_AIR
                && event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }

        Player player = event.getPlayer();
        ItemStack inHand = player.getInventory().getItemInMainHand();
        ItemStack menuHead = playerMenuGUI.getPlayerMenuItem(player);

        // EARLY EXIT if they're not holding their menu head
        if (inHand == null || !inHand.isSimilar(menuHead)) return;

        // Open the "combat" tab as an example
        skillGUI.update(player, "combat");
    }
}
