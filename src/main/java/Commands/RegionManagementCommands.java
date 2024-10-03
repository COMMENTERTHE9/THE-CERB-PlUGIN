package Commands;

import cerberus.world.cerb.CerberusWorldProtection;
import cerberus.world.cerb.Region;
import cerberus.world.cerb.cerb;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Map;
import java.util.HashMap;

public class RegionManagementCommands implements CommandExecutor {

    private final cerb plugin;
    private final Map<String, Region> namedRegions = new HashMap<>();

    public RegionManagementCommands(cerb plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("This command can only be used by players.");
            return true;
        }

        Player player = (Player) sender;

        if (args.length == 0) {
            sendHelpMessage(player);
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "list":
                listRegions(player);
                break;
            case "name":
                if (args.length < 2) {
                    player.sendMessage(ChatColor.RED + "Usage: /region name <regionName>");
                    return true;
                }
                nameRegion(player, args[1]);
                break;
            case "tp":
            case "teleport":
                if (args.length < 2) {
                    player.sendMessage(ChatColor.RED + "Usage: /region tp <regionName>");
                    return true;
                }
                teleportToRegion(player, args[1]);
                break;
            default:
                sendHelpMessage(player);
                break;
        }

        return true;
    }

    private void sendHelpMessage(Player player) {
        player.sendMessage(ChatColor.GOLD + "Region Management Commands:");
        player.sendMessage(ChatColor.YELLOW + "/region list - List all protected regions");
        player.sendMessage(ChatColor.YELLOW + "/region name <regionName> - Name the last selected region");
        player.sendMessage(ChatColor.YELLOW + "/region tp <regionName> - Teleport to a named region");
    }

    private void listRegions(Player player) {
        List<Region> regions = CerberusWorldProtection.getProtectedRegions();
        player.sendMessage(ChatColor.GOLD + "Protected Regions:");
        for (int i = 0; i < regions.size(); i++) {
            Region region = regions.get(i);
            String name = getRegionName(region);
            player.sendMessage(ChatColor.YELLOW + String.format("%d. %s: %s to %s",
                    i + 1, name, formatLocation(region.getMin()), formatLocation(region.getMax())));
        }
    }

    private void nameRegion(Player player, String name) {
        List<Region> regions = CerberusWorldProtection.getProtectedRegions();
        if (regions.isEmpty()) {
            player.sendMessage(ChatColor.RED + "No regions available to name.");
            return;
        }
        Region lastRegion = regions.get(regions.size() - 1);
        namedRegions.put(name, lastRegion);
        player.sendMessage(ChatColor.GREEN + "Last selected region named: " + name);
    }

    private void teleportToRegion(Player player, String name) {
        Region region = namedRegions.get(name);
        if (region == null) {
            player.sendMessage(ChatColor.RED + "No region found with the name: " + name);
            return;
        }
        Location teleportLocation = region.getMin().clone().add(0, 1, 0); // Teleport just above the minimum point
        player.teleport(teleportLocation);
        player.sendMessage(ChatColor.GREEN + "Teleported to region: " + name);
    }

    private String getRegionName(Region region) {
        for (Map.Entry<String, Region> entry : namedRegions.entrySet()) {
            if (entry.getValue().equals(region)) {
                return entry.getKey();
            }
        }
        return "Unnamed";
    }

    private String formatLocation(Location loc) {
        return String.format("(%d, %d, %d)", loc.getBlockX(), loc.getBlockY(), loc.getBlockZ());
    }
}