package Commands;

import cerberus.world.cerb.CerberusPlugin;
import cerberus.world.cerb.CerberusWorldProtection;
import cerberus.world.cerb.Region;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class RegionManagementCommands implements CommandExecutor {

    private final CerberusPlugin plugin;
    private final Map<String, Region> namedRegions = new HashMap<>();

    // <<< NEW: centralize usage/help text
    private static final String USAGE =
            ChatColor.GOLD + "Region Management Commands:\n" +
                    ChatColor.YELLOW + "/region list\n" +
                    ChatColor.YELLOW + "/region name <regionName>\n" +
                    ChatColor.YELLOW + "/region tp <regionName>";

    public RegionManagementCommands(CerberusPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender,
                             Command command,
                             String label,
                             String[] args) {

        // <<< NEW: only players
        if (!(sender instanceof Player player)) {
            sender.sendMessage(ChatColor.RED + "Only players may use this command.");
            return true;
        }

        // <<< NEW: permission check
        if (!player.hasPermission("cerberus.region")) {
            player.sendMessage(ChatColor.RED + "You lack permission to manage regions.");
            return true;
        }

        // <<< NEW: args length check
        if (args.length == 0) {
            player.sendMessage(USAGE);
            return true;
        }

        String sub = args[0].toLowerCase();

        try {
            switch (sub) {
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
                    player.sendMessage(USAGE);
                    break;
            }
        } catch (Exception ex) {
            // <<< NEW: catch unexpected errors
            Bukkit.getLogger().severe("Error in /region command: " + ex.getMessage());
            ex.printStackTrace();
            player.sendMessage(ChatColor.RED + "An internal error occurred; check the console.");
        }

        return true;
    }

    private void listRegions(Player player) {
        List<Region> regions = CerberusWorldProtection.getProtectedRegions();
        player.sendMessage(ChatColor.GOLD + "Protected Regions:");
        for (int i = 0; i < regions.size(); i++) {
            Region r = regions.get(i);
            String name = getRegionName(r);
            player.sendMessage(ChatColor.YELLOW + String.format(
                    "%d. %s: %s to %s",
                    i + 1,
                    name,
                    formatLocation(r.getMin()),
                    formatLocation(r.getMax())
            ));
        }
    }

    private void nameRegion(Player player, String name) {
        List<Region> regions = CerberusWorldProtection.getProtectedRegions();
        if (regions.isEmpty()) {
            player.sendMessage(ChatColor.RED + "No regions available to name.");
            return;
        }
        Region last = regions.get(regions.size() - 1);
        namedRegions.put(name, last);
        player.sendMessage(ChatColor.GREEN + "Last selected region named “" + name + "”.");
    }

    private void teleportToRegion(Player player, String name) {
        Region region = namedRegions.get(name);
        if (region == null) {
            player.sendMessage(ChatColor.RED + "No region found named “" + name + "”.");
            return;
        }
        Location loc = region.getMin().clone().add(0, 1, 0);
        player.teleport(loc);
        player.sendMessage(ChatColor.GREEN + "Teleported to region “" + name + "”.");
    }

    private String getRegionName(Region region) {
        return namedRegions.entrySet().stream()
                .filter(e -> e.getValue().equals(region))
                .map(Map.Entry::getKey)
                .findFirst()
                .orElse("Unnamed");
    }

    private String formatLocation(Location loc) {
        return String.format("(%d, %d, %d)",
                loc.getBlockX(), loc.getBlockY(), loc.getBlockZ()
        );
    }
}
