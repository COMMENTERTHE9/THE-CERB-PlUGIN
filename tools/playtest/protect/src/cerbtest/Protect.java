package cerbtest;

import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * A stand-in for a protection plugin, for the sandbox only (tools/playtest/woodcutting.js protect): it refuses every
 * block break inside one box, the way claim plugins refuse breaks in a claim. Never shipped with Cerberus.
 * {@code /testprotect <x1> <y1> <z1> <x2> <y2> <z2>} sets the box, {@code /testprotect off} clears it.
 */
public final class Protect extends JavaPlugin implements Listener {

    private volatile int[] box;
    private volatile int refused;

    @Override
    public void onEnable() {
        getServer().getPluginManager().registerEvents(this, this);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
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
}
