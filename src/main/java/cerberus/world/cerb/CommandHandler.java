package cerberus.world.cerb;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

public class CommandHandler implements CommandExecutor {
    private final RegionSelectorBehavior regionSelectorBehavior;
    private final ItemUtils itemUtils;

    public CommandHandler(cerb plugin, RegionSelectorBehavior regionSelectorBehavior) {
        this.regionSelectorBehavior = regionSelectorBehavior;
        this.itemUtils = plugin.getItemUtils();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("This command can only be used by players.");
            return true;
        }

        Player player = (Player) sender;

        if (command.getName().equalsIgnoreCase("regionselector")) {
            if (!player.hasPermission("cerberus.use.regionselector")) {
                player.sendMessage("You don't have permission to use this command.");
                return true;
            }

            ItemStack regionSelector = itemUtils.createRegionSelector();
            player.getInventory().addItem(regionSelector);
            player.sendMessage("You have received the Region Selector!");
        } else if (command.getName().equalsIgnoreCase("unregionizer")) {
            if (!player.hasPermission("cerberus.use.unregionizer")) {
                player.sendMessage("You don't have permission to use this command.");
                return true;
            }

            ItemStack unregionizer = itemUtils.createUnregionizer();
            player.getInventory().addItem(unregionizer);
            player.sendMessage("You have received the Unregionizer!");
        }

        return true;
    }
}
