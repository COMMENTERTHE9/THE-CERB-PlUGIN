package Traps;

import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;

public class TrapTool {
    private final TrapToolType type;
    private final TrapToolVariant variant;
    private final int uses;
    private final int baseSuccessRate;
    private final JavaPlugin plugin;

    // Constructor for basic tool
    public TrapTool(TrapToolType type, int uses, int successRate, JavaPlugin plugin) {
        this.type = type;
        this.variant = TrapToolVariant.STANDARD; // Default to standard variant
        this.uses = uses;
        this.usesRemaining = uses;  // Initialize usesRemaining
        this.baseSuccessRate = successRate;
        this.plugin = plugin;
    }

    // Constructor with variant
    public TrapTool(TrapToolType type, TrapToolVariant variant, int uses, JavaPlugin plugin) {
        this.type = type;
        this.variant = variant;
        this.uses = uses;
        this.usesRemaining = uses;  // Initialize usesRemaining
        this.baseSuccessRate = type.getBaseSuccessRate();
        this.plugin = plugin;
    }

    public ItemStack createItemStack() {
        ItemStack item = new ItemStack(getToolMaterial());
        ItemMeta meta = item.getItemMeta();

        // Set name (includes variant if not standard)
        String displayName = variant == TrapToolVariant.STANDARD ?
                type.getDisplayName() :
                variant.getDisplayName() + " " + type.getDisplayName();
        meta.setDisplayName(ChatColor.YELLOW + displayName);

        // Add lore
        List<String> lore = new ArrayList<>();
        lore.add(ChatColor.GRAY + type.getDescription());
        lore.add(ChatColor.GREEN + "Uses Remaining: " + uses);
        lore.add(ChatColor.BLUE + "Success Rate: " + getModifiedSuccessRate() + "%");

        // Add variant-specific lore if not standard
        if (variant != TrapToolVariant.STANDARD) {
            if (variant.getTrapCount() > 1) {
                lore.add(ChatColor.YELLOW + "Can affect " + variant.getTrapCount() + " traps at once");
            }
            if (variant.isNoisy()) {
                lore.add(ChatColor.RED + "Makes noise when used");
            }
            if (variant.isSilent()) {
                lore.add(ChatColor.AQUA + "Silent operation");
            }
        }

        meta.setLore(lore);

        // Add tool data
        PersistentDataContainer container = meta.getPersistentDataContainer();
        container.set(
                new NamespacedKey(plugin, "trap_tool_type"),
                PersistentDataType.STRING,
                type.name()
        );
        container.set(
                new NamespacedKey(plugin, "trap_tool_variant"),
                PersistentDataType.STRING,
                variant.name()
        );
        container.set(
                new NamespacedKey(plugin, "uses_remaining"),
                PersistentDataType.INTEGER,
                uses
        );

        item.setItemMeta(meta);
        return item;
    }

    private Material getToolMaterial() {
        switch(type) {
            case ARMING:
                return Material.BLAZE_ROD;
            case DISARMING:
                return Material.SHEARS;
            case RESET:
                return Material.CLOCK;
            default:
                return Material.STICK;
        }
    }

    public int getModifiedSuccessRate() {
        return (int)(baseSuccessRate * variant.getSuccessMultiplier());
    }

    public boolean attemptUse() {
        return Math.random() * 100 <= getModifiedSuccessRate();
    }

    public boolean hasUsesRemaining() {
        return uses > 0;
    }

    public void onUse(Location location, Player player) {
        // Handle variant effects
        if (variant.isNoisy()) {
            location.getWorld().playSound(location, Sound.BLOCK_LEVER_CLICK, 1.0f, 1.0f);
        }

        // Handle multiple traps for mass variant
        if (variant.getTrapCount() > 1) {
            List<Trap> nearbyTraps = getNearbyTraps(location, 3);
            for (Trap trap : nearbyTraps) {
                applyToolEffect(trap);
            }
        } else {
            // Apply to single trap
            Trap trap = getTrapAtLocation(location);
            if (trap != null) {
                applyToolEffect(trap);
            }
        }
    }

    private List<Trap> getNearbyTraps(Location location, int range) {
        List<Trap> nearbyTraps = new ArrayList<>();
        // Implementation to find nearby traps
        // This would need to be implemented based on how traps are stored
        return nearbyTraps;
    }

    private Trap getTrapAtLocation(Location location) {
        // Implementation to get trap at location
        // This would need to be implemented based on how traps are stored
        return null;
    }

    private void applyToolEffect(Trap trap) {
        if (!attemptUse()) {
            return; // Tool use failed
        }

        switch(type) {
            case ARMING:
                trap.setState(TrapState.ARMED);
                break;
            case DISARMING:
                trap.setState(TrapState.NEUTRAL);
                break;
            case RESET:
                if (trap.getCurrentState() == TrapState.TRIGGERED) {
                    trap.setState(TrapState.NEUTRAL);
                }
                break;
        }
    }

    private int usesRemaining;  // Add this field

    public boolean useItem(Player player, Trap trap) {
        // First check if any uses remain
        if (!hasUsesRemaining()) {
            player.sendMessage(ChatColor.RED + "This tool has no uses remaining!");
            return false;
        }

        // Validate ownership
        if (!isOwnerOrHasPermission(player, trap)) {
            player.sendMessage(ChatColor.RED + "You don't have permission to modify this trap!");
            return false;
        }

        // Attempt to use the tool
        if (attemptUse()) {
            decrementUses(player);
            return true;
        } else {
            player.sendMessage(ChatColor.RED + "Tool use failed!");
            decrementUses(player);
            return false;
        }
    }

    private boolean isOwnerOrHasPermission(Player player, Trap trap) {
        // Check if player is trap owner
        if (trap.getOwner().equals(player)) {
            return true;
        }

        // Check for admin permission
        if (player.hasPermission("traps.admin")) {
            return true;
        }

        // Check for team/party permissions if you have a team system
        // return checkTeamPermissions(player, trap);

        return false;
    }

    private void decrementUses(Player player) {
        usesRemaining--;
        updateUsesLore(player);

        if (usesRemaining <= 0) {
            player.sendMessage(ChatColor.RED + "Your tool has broken!");
            destroyTool(player);
        } else if (usesRemaining <= 3) {
            player.sendMessage(ChatColor.YELLOW + "Tool is getting worn out! " + usesRemaining + " uses remaining.");
        }
    }

    private void updateUsesLore(Player player) {
        ItemStack item = player.getInventory().getItemInMainHand();
        if (item == null || !isTrapTool(item)) return;

        ItemMeta meta = item.getItemMeta();
        List<String> lore = meta.getLore();

        // Update uses remaining in lore
        for (int i = 0; i < lore.size(); i++) {
            if (lore.get(i).contains("Uses Remaining:")) {
                lore.set(i, ChatColor.GREEN + "Uses Remaining: " + usesRemaining);
                break;
            }
        }

        meta.setLore(lore);

        // Update persistent data
        meta.getPersistentDataContainer().set(
                new NamespacedKey(plugin, "uses_remaining"),
                PersistentDataType.INTEGER,
                usesRemaining
        );

        item.setItemMeta(meta);
    }

    private void destroyTool(Player player) {
        player.getInventory().setItemInMainHand(null);
        player.playSound(player.getLocation(), Sound.ENTITY_ITEM_BREAK, 1.0f, 1.0f);
    }

    private boolean isTrapTool(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return false;

        return item.getItemMeta().getPersistentDataContainer().has(
                new NamespacedKey(plugin, "trap_tool_type"),
                PersistentDataType.STRING
        );
    }

    // Getters
    public TrapToolType getType() { return type; }
    public TrapToolVariant getVariant() { return variant; }
    public int getUses() { return uses; }
    public int getBaseSuccessRate() { return baseSuccessRate; }
}
