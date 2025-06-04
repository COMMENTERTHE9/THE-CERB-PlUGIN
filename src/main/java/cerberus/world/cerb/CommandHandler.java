package cerberus.world.cerb;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/**
 * Handles both /regionselector and /unregionizer commands.
 * Usage:
 *   /regionselector [player]
 *   /unregionizer [player]
 */
public class CommandHandler implements CommandExecutor {

    // <<< NEW: command names
    private static final String CMD_SELECTOR   = "regionselector";
    private static final String CMD_UNREGION   = "unregionizer";

    // <<< NEW: permission nodes
    private static final String PERM_SELF_SELECTOR   = "cerberus.use.regionselector";
    private static final String PERM_OTHER_SELECTOR  = "cerberus.admin.regionselector";
    private static final String PERM_SELF_UNREGION   = "cerberus.use.unregionizer";
    private static final String PERM_OTHER_UNREGION  = "cerberus.admin.unregionizer";

    // <<< NEW: usage messages
    private static final String USAGE_SELECTOR =
            ChatColor.YELLOW + "Usage: /regionselector [player]";
    private static final String USAGE_UNREGION =
            ChatColor.YELLOW + "Usage: /unregionizer [player]";

    private final RegionSelectorBehavior regionSelectorBehavior;
    private final ItemUtils itemUtils;

    public CommandHandler(CerberusPlugin plugin,
                          RegionSelectorBehavior regionSelectorBehavior) {
        this.regionSelectorBehavior = regionSelectorBehavior;
        this.itemUtils = plugin.getItemUtils();
    }

    @Override
    public boolean onCommand(CommandSender sender,
                             Command command,
                             String label,
                             String[] args) {
        String cmd = command.getName().toLowerCase();

        try {
            switch (cmd) {
                case CMD_SELECTOR:
                    return handleSelector(sender, args);
                case CMD_UNREGION:
                    return handleUnregionizer(sender, args);
                default:
                    return false;
            }
        } catch (Exception ex) {
            sender.sendMessage(ChatColor.RED + "An error occurred; check console.");
            Bukkit.getLogger().severe("Error in CommandHandler for " + cmd + ": " + ex);
            ex.printStackTrace();
            return true;
        }
    }

    // <<< NEW: Handles /regionselector
    private boolean handleSelector(CommandSender sender, String[] args) {
        // /regionselector [player]
        if (args.length > 1) {
            sender.sendMessage(USAGE_SELECTOR);
            return true;
        }

        // Self
        if (args.length == 0) {
            if (!sender.hasPermission(PERM_SELF_SELECTOR)) {
                sender.sendMessage(ChatColor.RED + "You lack permission to receive a Region Selector.");
                return true;
            }
            if (!(sender instanceof Player player)) {
                sender.sendMessage(ChatColor.RED + "Only players may receive selectors.");
                return true;
            }
            giveSelectorTo(player);
            return true;
        }

        // Other
        if (!sender.hasPermission(PERM_OTHER_SELECTOR)) {
            sender.sendMessage(ChatColor.RED + "You lack permission to give selectors to others.");
            return true;
        }
        Player target = Bukkit.getPlayerExact(args[0]);
        if (target == null) {
            sender.sendMessage(ChatColor.RED + "Player not found: " + args[0]);
            return true;
        }
        giveSelectorTo(target);
        sender.sendMessage(ChatColor.GREEN + "Gave a Region Selector to " + target.getName());
        return true;
    }

    // <<< NEW: Handles /unregionizer
    private boolean handleUnregionizer(CommandSender sender, String[] args) {
        // /unregionizer [player]
        if (args.length > 1) {
            sender.sendMessage(USAGE_UNREGION);
            return true;
        }

        // Self
        if (args.length == 0) {
            if (!sender.hasPermission(PERM_SELF_UNREGION)) {
                sender.sendMessage(ChatColor.RED + "You lack permission to receive an Unregionizer.");
                return true;
            }
            if (!(sender instanceof Player player)) {
                sender.sendMessage(ChatColor.RED + "Only players may receive unregionizers.");
                return true;
            }
            giveUnregionizerTo(player);
            return true;
        }

        // Other
        if (!sender.hasPermission(PERM_OTHER_UNREGION)) {
            sender.sendMessage(ChatColor.RED + "You lack permission to give unregionizers to others.");
            return true;
        }
        Player target = Bukkit.getPlayerExact(args[0]);
        if (target == null) {
            sender.sendMessage(ChatColor.RED + "Player not found: " + args[0]);
            return true;
        }
        giveUnregionizerTo(target);
        sender.sendMessage(ChatColor.GREEN + "Gave an Unregionizer to " + target.getName());
        return true;
    }

    // <<< NEW helper to give region selector
    private void giveSelectorTo(Player player) {
        ItemStack item = itemUtils.createRegionSelector();
        player.getInventory().addItem(item);
        player.sendMessage(ChatColor.GREEN + "You have received the Region Selector!");
    }

    // <<< NEW helper to give unregionizer
    private void giveUnregionizerTo(Player player) {
        ItemStack item = itemUtils.createUnregionizer();
        player.getInventory().addItem(item);
        player.sendMessage(ChatColor.GREEN + "You have received the Unregionizer!");
    }
}
