package Listener;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.inventory.ItemStack;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;

import cerberus.world.cerb.CerberusPlugin;
import Skills.SkillManager;
import Spells.SpellManager;
import Manager.MobContext;
import Manager.MobRegistry;

public class CombatListener implements Listener {
    private final CerberusPlugin      plugin;
    private final SkillManager         skillManager;
    private final SpellManager         spellManager;
    private final MobRegistry          mobRegistry;

    // Combat tracking maps
    private final Map<UUID, Boolean> inCombat = new ConcurrentHashMap<>();
    private final Map<UUID, Long> lastCombatTime = new ConcurrentHashMap<>();

    // The amount of time after the last hit for the player to be considered out of combat
    private static final long COMBAT_TIMEOUT = 20000L; // 20 seconds

    public CombatListener(CerberusPlugin plugin,
                          SkillManager skillManager,
                          SpellManager spellManager,
                          MobRegistry mobRegistry) {
        this.plugin            = plugin;
        this.skillManager      = skillManager;
        this.spellManager      = spellManager;
        this.mobRegistry       = mobRegistry;
    }

    // -------------------------
    // Enter combat on hit
    // -------------------------
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)  // <<< ADDED
    public void onPlayerHit(EntityDamageByEntityEvent event) {
        double damage = event.getDamage();
        if (damage <= 0) return;                                           // <<< EARLY EXIT

        // If a player is damaged, they enter combat
        if (event.getEntity() instanceof Player victim) {
            enterCombat(victim);                                           // your existing method
        }

        // If a player deals damage, they also enter combat
        if (event.getDamager() instanceof Player attacker) {
            enterCombat(attacker);                                         // your existing method
        }
    }

    // -------------------------
    // Check for exit combat on move
    // -------------------------
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)  // <<< ADDED
    public void onPlayerMove(PlayerMoveEvent event) {
        // ignore tiny movements (head‐turns)
        if (event.getFrom().distanceSquared(event.getTo()) < 0.01) return;  // <<< EARLY EXIT

        Player player = event.getPlayer();
        // only check exit for those actually in combat
        if (!inCombat.getOrDefault(player.getUniqueId(), false)) return;   // <<< EARLY EXIT

        checkExitCombat(player);                                           // your existing method
    }

    // Kill XP is awarded once, by MobKillListener, to the skill of the weapon used (design
    // plan: "mob kill -> the combat skill used, weapon-type routed"). This listener used to
    // also award a second share to a "Combat" key that is not a registered skill: invisible
    // in the skill menus, but it inflated the menu-head Level and doubled kill XP.

    private void enterCombat(Player player) {
        UUID playerUUID = player.getUniqueId();
        inCombat.put(playerUUID, true);
        lastCombatTime.put(playerUUID, System.currentTimeMillis());

        // Reset the combat timeout task
        player.getScheduler().runDelayed(plugin, task -> {
            checkExitCombat(player);
        }, null, COMBAT_TIMEOUT / 50); // Schedule a task to check combat status later
    }

    private void checkExitCombat(Player player) {
        UUID playerUUID = player.getUniqueId();
        Boolean combat = inCombat.get(playerUUID);
        if (combat == null || !combat) return;

        long lastHitTime = lastCombatTime.getOrDefault(playerUUID, 0L);
        if (System.currentTimeMillis() - lastHitTime > COMBAT_TIMEOUT) {
            exitCombat(player);
        }
    }

    private void exitCombat(Player player) {
        UUID playerUUID = player.getUniqueId();
        inCombat.put(playerUUID, false);
    }
}
