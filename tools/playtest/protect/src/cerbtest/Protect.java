package cerbtest;

import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A stand-in for a protection plugin, for the sandbox only (tools/playtest/woodcutting.js protect): it refuses every
 * block break inside one box, the way claim plugins refuse breaks in a claim. Never shipped with Cerberus.
 * {@code /testprotect <x1> <y1> <z1> <x2> <y2> <z2>} sets the box, {@code /testprotect off} clears it.
 * {@code /testsave <player>} writes a player's data to disk now (Folia has no save-all; fishing.js crash-set uses it).
 * {@code /testnocatch <player>|off} refuses that player's catches (PlayerFishEvent CAUGHT_FISH, cancelled after Cerberus
 * decides), the way a minigame or claim plugin may (fishgear.js bait: a refused catch uses no bait).
 */
public final class Protect extends JavaPlugin implements Listener {

    private volatile int[] box;
    private volatile int refused;
    private final Set<UUID> noCatch = ConcurrentHashMap.newKeySet();

    @Override
    public void onEnable() {
        getServer().getPluginManager().registerEvents(this, this);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (command.getName().equalsIgnoreCase("testnocatch")) {
            if (args.length != 1) return false;
            if (args[0].equalsIgnoreCase("off")) { noCatch.clear(); sender.sendMessage("testnocatch: off"); return true; }
            org.bukkit.entity.Player p = getServer().getPlayerExact(args[0]);
            if (p == null) return false;
            noCatch.add(p.getUniqueId());
            sender.sendMessage("testnocatch: refusing " + p.getName() + "'s catches");
            return true;
        }
        if (command.getName().equalsIgnoreCase("testsave")) {
            org.bukkit.entity.Player p = args.length == 1 ? getServer().getPlayerExact(args[0]) : null;
            if (p == null) return false;
            p.getScheduler().run(this, t -> { p.saveData(); sender.sendMessage("testsave: saved " + p.getName()); }, null);
            return true;
        }
        if (args.length == 1 && args[0].equalsIgnoreCase("off")) {
            box = null;
            sender.sendMessage("testprotect: off (" + refused + " breaks refused)");
            return true;
        }
        if (args.length != 6) return false;
        int[] b = new int[6];
        try {
            for (int i = 0; i < 6; i++) b[i] = Integer.parseInt(args[i]);
        } catch (NumberFormatException e) {
            return false;
        }
        box = new int[] {Math.min(b[0], b[3]), Math.min(b[1], b[4]), Math.min(b[2], b[5]), Math.max(b[0], b[3]), Math.max(b[1], b[4]), Math.max(b[2], b[5])};
        refused = 0;
        sender.sendMessage("testprotect: protecting " + String.join(" ", args));
        return true;
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent e) {
        int[] b = box;
        if (b == null) return;
        int x = e.getBlock().getX(), y = e.getBlock().getY(), z = e.getBlock().getZ();
        if (x >= b[0] && y >= b[1] && z >= b[2] && x <= b[3] && y <= b[4] && z <= b[5]) {
            e.setCancelled(true);
            refused++;
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onFish(PlayerFishEvent e) {
        if (e.getState() == PlayerFishEvent.State.CAUGHT_FISH && noCatch.contains(e.getPlayer().getUniqueId())) e.setCancelled(true);
    }
}
