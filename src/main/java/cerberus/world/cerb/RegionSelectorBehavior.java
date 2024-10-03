package cerberus.world.cerb;

import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.UUID;

public class RegionSelectorBehavior {

    private final cerb plugin;
    private final ItemUtils itemUtils;
    private final HashMap<UUID, Location[]> playerSelections = new HashMap<>();

    public RegionSelectorBehavior(cerb plugin) {
        this.plugin = plugin;
        this.itemUtils = new ItemUtils(plugin);
    }

    public void handleLeftClick(Player player, Location loc) {
        if (!player.hasPermission("cerberus.use.selector")) {
            player.sendMessage(ChatColor.RED + "You don't have permission to use the Region Selector.");
            return;
        }

        UUID playerId = player.getUniqueId();
        Location[] selections = playerSelections.computeIfAbsent(playerId, k -> new Location[2]);
        selections[0] = loc;
        player.sendMessage(ChatColor.GREEN + "First point set to " + formatLocation(loc));
    }

    public void handleRightClick(Player player, Location loc, ItemStack item) {
        if (itemUtils.isRegionSelector(item)) {
            handleRegionSelectorRightClick(player, loc);
        } else if (itemUtils.isUnregionizer(item)) {
            handleUnregionizerRightClick(player, loc);
        }
    }

    private void handleRegionSelectorRightClick(Player player, Location loc) {
        if (!player.hasPermission("cerberus.use.selector")) {
            player.sendMessage(ChatColor.RED + "You don't have permission to use the Region Selector.");
            return;
        }

        UUID playerId = player.getUniqueId();
        Location[] selections = playerSelections.computeIfAbsent(playerId, k -> new Location[2]);
        selections[1] = loc;
        player.sendMessage(ChatColor.GREEN + "Second point set to " + formatLocation(loc));

        if (selections[0] != null && selections[1] != null) {
            finalizeRegion(player, selections);
            playerSelections.remove(playerId); // Clear selections after finalizing
        }
    }

    private void handleUnregionizerRightClick(Player player, Location loc) {
        if (!player.hasPermission("cerberus.remove.region")) {
            player.sendMessage(ChatColor.RED + "You don't have permission to remove regions.");
            return;
        }

        Region removedRegion = plugin.getCerberusWorldProtection().removeProtectedRegionAt(loc);
        if (removedRegion != null) {
            player.sendMessage(ChatColor.GREEN + "Removed protection from region at " + formatLocation(loc));
            plugin.getRegionManager().saveRegions();
        } else {
            player.sendMessage(ChatColor.RED + "No protected region found at " + formatLocation(loc));
        }
    }

    private void finalizeRegion(Player player, Location[] selections) {
        if (!player.hasPermission("cerberus.create.region")) {
            player.sendMessage(ChatColor.RED + "You don't have permission to create regions.");
            return;
        }

        Region region = new Region(selections[0], selections[1]);
        boolean added = plugin.getCerberusWorldProtection().addProtectedRegion(region);
        if (added) {
            player.sendMessage(ChatColor.GREEN + "Region protected between " +
                    formatLocation(selections[0]) + " and " +
                    formatLocation(selections[1]));
            plugin.getRegionManager().saveRegions();
        } else {
            player.sendMessage(ChatColor.RED + "Failed to protect region. It may overlap with an existing protected region.");
        }
    }

    private String formatLocation(Location loc) {
        return String.format("(%d, %d, %d)", loc.getBlockX(), loc.getBlockY(), loc.getBlockZ());
    }
}
