package Commands;

import cerberus.world.cerb.CerberusPlugin;
import cerberus.world.cerb.CerberusWorldProtection;
import cerberus.world.cerb.Region;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.List;

public class RegionVisualizerCommand implements CommandExecutor {
    private final CerberusPlugin plugin;

    public RegionVisualizerCommand(CerberusPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("This command can only be used by players.");
            return true;
        }

        Player player = (Player) sender;
        if (!player.hasPermission("cerberus.visualize.regions")) {
            player.sendMessage(ChatColor.RED + "You don't have permission to use this command.");
            return true;
        }

        player.sendMessage(ChatColor.GREEN + "Visualizing protected regions for 30 seconds.");
        visualizeRegions(player);

        return true;
    }

    private void visualizeRegions(Player player) {
        new BukkitRunnable() {
            int ticks = 0;
            @Override
            public void run() {
                if (ticks >= 600) { // 30 seconds (20 ticks per second)
                    this.cancel();
                    return;
                }
                List<Region> regions = CerberusWorldProtection.getProtectedRegions();
                if (regions.isEmpty()) {
                    if (ticks == 0) {
                        player.sendMessage(ChatColor.YELLOW + "There are no protected regions to visualize.");
                    }
                    this.cancel(); // Stop the task if there are no regions to visualize.
                    return;
                }
                for (Region region : regions) {
                    Location min = region.getMin();
                    Location max = region.getMax();
                    for (double x = min.getX(); x <= max.getX(); x += 0.5) {
                        spawnParticle(player, new Location(min.getWorld(), x, min.getY(), min.getZ()));
                        spawnParticle(player, new Location(min.getWorld(), x, min.getY(), max.getZ()));
                        spawnParticle(player, new Location(min.getWorld(), x, max.getY(), min.getZ()));
                        spawnParticle(player, new Location(min.getWorld(), x, max.getY(), max.getZ()));
                    }
                    for (double y = min.getY(); y <= max.getY(); y += 0.5) {
                        spawnParticle(player, new Location(min.getWorld(), min.getX(), y, min.getZ()));
                        spawnParticle(player, new Location(min.getWorld(), min.getX(), y, max.getZ()));
                        spawnParticle(player, new Location(min.getWorld(), max.getX(), y, min.getZ()));
                        spawnParticle(player, new Location(min.getWorld(), max.getX(), y, max.getZ()));
                    }
                    for (double z = min.getZ(); z <= max.getZ(); z += 0.5) {
                        spawnParticle(player, new Location(min.getWorld(), min.getX(), min.getY(), z));
                        spawnParticle(player, new Location(min.getWorld(), min.getX(), max.getY(), z));
                        spawnParticle(player, new Location(min.getWorld(), max.getX(), min.getY(), z));
                        spawnParticle(player, new Location(min.getWorld(), max.getX(), max.getY(), z));
                    }
                }
                ticks += 2;
            }
        }.runTaskTimer(plugin, 0, 2);
    }

    private void spawnParticle(Player player, Location location) {
        player.spawnParticle(Particle.PORTAL, location, 10, 0.5, 0.5, 0.5, 0.03);
    }
}