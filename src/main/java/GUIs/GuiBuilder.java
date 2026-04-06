package GUIs;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Static utility for building GUI inventories and items.
 * Null-safe, consistent styling, eliminates boilerplate across all GUIs.
 */
public final class GuiBuilder {

    private GuiBuilder() {} // static only

    // ── Inventory creation ───────────────────────────────────────

    /** Create a GUI inventory with proper GuiHolder identification. */
    public static Inventory createGui(GuiIdentity identity, int rows, String title) {
        return Bukkit.createInventory(
                new GuiIdentity.GuiHolder(identity),
                rows * 9,
                title
        );
    }

    // ── Item creation ────────────────────────────────────────────

    /** Create an item with display name and lore. Null-safe meta handling. */
    public static ItemStack createItem(Material material, String name, List<String> lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return item;

        meta.setDisplayName(name);
        if (lore != null && !lore.isEmpty()) {
            meta.setLore(lore);
        }
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ENCHANTS,
                ItemFlag.HIDE_UNBREAKABLE, ItemFlag.HIDE_ADDITIONAL_TOOLTIP);
        item.setItemMeta(meta);
        return item;
    }

    /** Create an item with lore as varargs. */
    public static ItemStack createItem(Material material, String name, String... lore) {
        return createItem(material, name, Arrays.asList(lore));
    }

    /**
     * Create a clickable action item with PDC tags for the unified listener.
     * Uses {@link GuiIdentity#CERB_ACTION} and {@link GuiIdentity#CERB_DATA}.
     */
    public static ItemStack createActionItem(Material material, String name, List<String> lore,
                                              String action, String data) {
        ItemStack item = createItem(material, name, lore);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return item;

        if (action != null) {
            meta.getPersistentDataContainer().set(
                    GuiIdentity.CERB_ACTION, PersistentDataType.STRING, action);
        }
        if (data != null) {
            meta.getPersistentDataContainer().set(
                    GuiIdentity.CERB_DATA, PersistentDataType.STRING, data);
        }
        item.setItemMeta(meta);
        return item;
    }

    /** Convenience: action item with varargs lore. */
    public static ItemStack createActionItem(Material material, String name, String action, String data,
                                              String... lore) {
        return createActionItem(material, name, Arrays.asList(lore), action, data);
    }

    /** Create a player skull item. */
    public static ItemStack createSkull(Player player, String name, List<String> lore) {
        ItemStack skull = new ItemStack(Material.PLAYER_HEAD);
        ItemMeta meta = skull.getItemMeta();
        if (meta instanceof SkullMeta skullMeta) {
            skullMeta.setOwningPlayer(player);
            skullMeta.setDisplayName(name);
            if (lore != null) skullMeta.setLore(lore);
            skull.setItemMeta(skullMeta);
        }
        return skull;
    }

    /** Create a glass pane filler with a blank name. */
    public static ItemStack createGlassPane(Material glass) {
        return createItem(glass, " ");
    }

    // ── Layout helpers ───────────────────────────────────────────

    /** Fill all border slots (top, bottom, left, right edges) with glass. */
    public static void fillBorders(Inventory inv, Material glass) {
        ItemStack pane = createGlassPane(glass);
        int size = inv.getSize();
        int cols = 9;
        int rows = size / cols;

        for (int i = 0; i < size; i++) {
            int row = i / cols;
            int col = i % cols;
            if (row == 0 || row == rows - 1 || col == 0 || col == 8) {
                inv.setItem(i, pane);
            }
        }
    }

    /** Fill all empty (null) slots with glass. */
    public static void fillEmpty(Inventory inv, Material glass) {
        ItemStack pane = createGlassPane(glass);
        for (int i = 0; i < inv.getSize(); i++) {
            if (inv.getItem(i) == null) {
                inv.setItem(i, pane);
            }
        }
    }

    // ── Lore formatting helpers ──────────────────────────────────

    /** Build an XP progress bar string: [||||||||------] 45/100 */
    public static String progressBar(int current, int max, int barLength) {
        if (max <= 0) max = 1;
        double ratio = Math.min(1.0, (double) current / max);
        int filled = (int) (ratio * barLength);
        int empty = barLength - filled;

        StringBuilder bar = new StringBuilder();
        bar.append(ChatColor.DARK_GREEN);
        bar.append("|".repeat(filled));
        bar.append(ChatColor.DARK_GRAY);
        bar.append("|".repeat(empty));
        bar.append(ChatColor.GRAY).append(" ").append(current).append("/").append(max);
        return bar.toString();
    }

    /** Format a stat line: "  Label: Value" in gray/white. */
    public static String statLine(String label, String value) {
        return ChatColor.GRAY + "  " + label + ": " + ChatColor.WHITE + value;
    }

    /** Format a delta line showing change: "  Label: old → new (+delta)" */
    public static String deltaLine(String label, double oldVal, double newVal, String format) {
        double delta = newVal - oldVal;
        String sign = delta >= 0 ? "+" : "";
        return ChatColor.GRAY + "  " + label + ": " + ChatColor.WHITE + String.format(format, oldVal)
                + ChatColor.GRAY + " → " + ChatColor.YELLOW + String.format(format, newVal)
                + ChatColor.GREEN + " (" + sign + String.format(format, delta) + ")";
    }

    /** Create a "Back" arrow item with the specified action target. */
    public static ItemStack createBackArrow(String backTarget) {
        return createActionItem(Material.ARROW,
                ChatColor.YELLOW + "Back",
                List.of(ChatColor.GRAY + "Return to previous menu"),
                "back", backTarget);
    }
}
