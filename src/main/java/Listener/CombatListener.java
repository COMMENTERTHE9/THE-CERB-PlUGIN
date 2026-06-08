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
import GUIs.PlayerMenuGUI;
import GUIs.SkillGUI;
import Manager.MobContext;
import Manager.MobRegistry;

public class CombatListener implements Listener {
    private final CerberusPlugin      plugin;
    private final SkillManager         skillManager;
    private final SpellManager         spellManager;
    private final PlayerMenuGUI        playerMenuGUI;
    private final SkillGUI             skillGUI;
    private final MobRegistry          mobRegistry;

    // Combat tracking maps
    private final Map<UUID, Boolean> inCombat = new ConcurrentHashMap<>();
    private final Map<UUID, Long> lastCombatTime = new ConcurrentHashMap<>();

    // The amount of time after the last hit for the player to be considered out of combat
    private static final long COMBAT_TIMEOUT = 20000L; // 20 seconds

    public CombatListener(CerberusPlugin plugin,
                          SkillManager skillManager,
                          SpellManager spellManager,
                          PlayerMenuGUI playerMenuGUI,
                          SkillGUI skillGUI,
                          MobRegistry mobRegistry) {
        this.plugin            = plugin;
        this.skillManager      = skillManager;
        this.spellManager      = spellManager;
        this.playerMenuGUI     = playerMenuGUI;
        this.skillGUI          = skillGUI;
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

    // ——————————————————————————————
// Killing blow XP (melee or projectile)
// ——————————————————————————————
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onCombatDamage(EntityDamageByEntityEvent e) {
        Player killer = null;
        if (e.getDamager() instanceof Player p)           killer = p;
        else if (e.getDamager() instanceof Projectile pr &&
                pr.getShooter() instanceof Player p2)    killer = p2;
        if (killer == null) return;

        if (!(e.getEntity() instanceof LivingEntity victim)) return;
        double damage = e.getFinalDamage();
        double maxHp   = victim.getAttribute(Attribute.MAX_HEALTH).getValue();

        // only award if this *would* kill them
        if (victim.getHealth() - damage > 0) return;

        // Resolve mob classification from the registry (config-driven)
        MobContext ctx = mobRegistry.resolve(victim);

        // award XP and refresh the menu if it’s open
        skillManager.addXpForMobKill(killer, "Combat", maxHp, damage, ctx);
        killer.sendMessage(ChatColor.GREEN + "You gained combat XP!");
        // GUI refreshes on reopen — no live update needed
    }

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
