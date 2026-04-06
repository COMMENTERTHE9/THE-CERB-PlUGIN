package GUIs;

import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Four-point chase-light animation on GUI borders.
 * <p>
 * Four accent panes start simultaneously at each corner and travel clockwise
 * around the border in sync — creating a balanced quad-chase effect.
 * Functional slots (back arrows, upgrade buttons) are skipped without
 * overwriting their items.
 * <p>
 * Runs on the player's Folia entity scheduler. Cancelled on inventory close.
 */
public final class BorderAnimator {

    private BorderAnimator() {}

    private static final Map<UUID, ScheduledTask> activeAnimations = new ConcurrentHashMap<>();

    /**
     * Start a 4-point border chase animation.
     *
     * @param plugin       plugin instance for scheduling
     * @param player       the player viewing the inventory
     * @param inv          the open inventory to animate
     * @param accentGlass  the colored glass material for the "lit" panes
     * @param skipSlots    slots to never overwrite (back arrows, upgrade buttons)
     * @param ticksPerStep ticks between each step (3-4 recommended)
     */
    public static void start(JavaPlugin plugin, Player player, Inventory inv,
                             Material accentGlass, Set<Integer> skipSlots,
                             long ticksPerStep) {
        stop(player);

        int rows = inv.getSize() / 9;
        List<Integer> path = buildBorderPath(rows);
        if (path.isEmpty()) return;

        ItemStack litPane = GuiBuilder.createGlassPane(accentGlass);
        ItemStack darkPane = GuiBuilder.createGlassPane(Material.BLACK_STAINED_GLASS_PANE);

        final int pathSize = path.size();
        // 4 heads evenly spaced around the border
        final int spacing = pathSize / 4;
        final int[] step = {0};

        ScheduledTask task = player.getScheduler().runAtFixedRate(plugin, scheduledTask -> {
            if (!player.isOnline()) {
                scheduledTask.cancel();
                activeAnimations.remove(player.getUniqueId());
                return;
            }

            Inventory topInv = player.getOpenInventory().getTopInventory();
            if (topInv != inv) {
                scheduledTask.cancel();
                activeAnimations.remove(player.getUniqueId());
                return;
            }

            // Clear previous 4 positions
            for (int head = 0; head < 4; head++) {
                int prevIdx = ((step[0] - 1 + head * spacing) % pathSize + pathSize) % pathSize;
                int prevSlot = path.get(prevIdx);
                if (!skipSlots.contains(prevSlot)) {
                    inv.setItem(prevSlot, darkPane);
                }
            }

            // Draw new 4 positions
            for (int head = 0; head < 4; head++) {
                int idx = ((step[0] + head * spacing) % pathSize + pathSize) % pathSize;
                int slot = path.get(idx);
                if (!skipSlots.contains(slot)) {
                    inv.setItem(slot, litPane);
                }
            }

            step[0] = (step[0] + 1) % pathSize;
        }, null, 1L, ticksPerStep);

        activeAnimations.put(player.getUniqueId(), task);
    }

    /** Stop and remove the animation for a player. */
    public static void stop(Player player) {
        ScheduledTask task = activeAnimations.remove(player.getUniqueId());
        if (task != null && !task.isCancelled()) {
            task.cancel();
        }
    }

    /** Stop by UUID. */
    public static void stop(UUID uuid) {
        ScheduledTask task = activeAnimations.remove(uuid);
        if (task != null && !task.isCancelled()) {
            task.cancel();
        }
    }

    /**
     * Clockwise border path: top L→R, right T→B, bottom R→L, left B→T.
     * Corners appear once (no duplicates).
     */
    private static List<Integer> buildBorderPath(int rows) {
        List<Integer> path = new ArrayList<>();
        int cols = 9;

        // Top row: left to right
        for (int c = 0; c < cols; c++) {
            path.add(c);
        }
        // Right column: top+1 to bottom-1
        for (int r = 1; r < rows - 1; r++) {
            path.add(r * cols + (cols - 1));
        }
        // Bottom row: right to left
        for (int c = cols - 1; c >= 0; c--) {
            path.add((rows - 1) * cols + c);
        }
        // Left column: bottom-1 to top+1
        for (int r = rows - 2; r >= 1; r--) {
            path.add(r * cols);
        }

        return path;
    }
}
