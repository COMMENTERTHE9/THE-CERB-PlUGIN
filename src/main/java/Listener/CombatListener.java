package Listener;

import Manager.CombatScoreboardManager;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class CombatListener implements Listener {

    private final CombatScoreboardManager scoreboardManager;
    private final Plugin plugin;
    private final Map<UUID, Boolean> inCombat = new HashMap<>();
    private final Map<UUID, Long> lastCombatTime = new HashMap<>();

    // The amount of time after the last hit for the player to be considered out of combat
    private static final long COMBAT_TIMEOUT = 20000L; // 10 seconds

    public CombatListener(CombatScoreboardManager scoreboardManager, Plugin plugin) {
        this.scoreboardManager = scoreboardManager;
        this.plugin = plugin;
    }

    @EventHandler
    public void onPlayerHit(EntityDamageByEntityEvent event) {
        if (event.getEntity() instanceof Player) {
            Player player = (Player) event.getEntity();
            enterCombat(player);
        }

        if (event.getDamager() instanceof Player) {
            Player player = (Player) event.getDamager();
            enterCombat(player);
        }
    }

    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        if (inCombat.getOrDefault(player.getUniqueId(), false)) {
            checkExitCombat(player);
        }
    }

    private void enterCombat(Player player) {
        UUID playerUUID = player.getUniqueId();
        inCombat.put(playerUUID, true);
        lastCombatTime.put(playerUUID, System.currentTimeMillis());
        scoreboardManager.switchToCombatMode(player);

        // Reset the combat timeout task
        new BukkitRunnable() {
            @Override
            public void run() {
                checkExitCombat(player);
            }
        }.runTaskLater(plugin, COMBAT_TIMEOUT / 50); // Schedule a task to check combat status later
    }

    private void checkExitCombat(Player player) {
        UUID playerUUID = player.getUniqueId();
        if (!inCombat.containsKey(playerUUID)) return;

        long lastHitTime = lastCombatTime.getOrDefault(playerUUID, 0L);
        if (System.currentTimeMillis() - lastHitTime > COMBAT_TIMEOUT) {
            exitCombat(player);
        }
    }

    private void exitCombat(Player player) {
        UUID playerUUID = player.getUniqueId();
        inCombat.put(playerUUID, false);
        scoreboardManager.switchToNormalMode(player);
    }
}
