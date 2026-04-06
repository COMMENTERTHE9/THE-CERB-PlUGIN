package GUIs;

import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Central GUI identification system — replaces fragile title-string matching.
 * <p>
 * Every Cerberus GUI inventory is created with a {@link GuiHolder} that carries
 * a {@link GuiIdentity} enum. The unified listener checks this instead of parsing
 * colored title strings.
 */
public enum GuiIdentity {
    PLAYER_MENU,
    SKILL_MANAGEMENT,
    SKILL_COMBAT,
    SKILL_MAGIC,
    SKILL_UTILITY,
    SKILL_DETAIL,
    ADMIN_PANEL,
    ADMIN_COMMANDS,
    REGION_MANAGEMENT,
    COMING_SOON;

    // ── Shared PDC keys — used by ALL GUIs for action routing ────────

    /** The action to perform: "open_combat", "open_skill_detail", "upgrade", "back", etc. */
    public static NamespacedKey CERB_ACTION;

    /** Context data: skill name, submenu target, feature name, etc. */
    public static NamespacedKey CERB_DATA;

    /** Call once during plugin enable to initialize the NamespacedKeys. */
    public static void init(JavaPlugin plugin) {
        CERB_ACTION = new NamespacedKey(plugin, "gui_action");
        CERB_DATA = new NamespacedKey(plugin, "gui_data");
    }

    // ── GuiHolder — InventoryHolder that carries identity ────────────

    /**
     * Inventory holder that tags an inventory with a {@link GuiIdentity}.
     * Use {@code inv.getHolder() instanceof GuiHolder h} to identify our GUIs.
     */
    public record GuiHolder(GuiIdentity identity) implements InventoryHolder {
        @Override
        public Inventory getInventory() {
            return null; // not used — Bukkit requires this but we never call it
        }
    }
}
