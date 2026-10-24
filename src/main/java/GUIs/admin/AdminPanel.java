package GUIs.admin;

import GUIs.menu.Menu;
import GUIs.menu.MenuManager;
import GUIs.menu.Status;
import Manager.MobTestService;
import cerberus.world.cerb.CerberusPlugin;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Function;

/**
 * Cerberus Control Center: entry point, permission, panel item, audit log, and the registry of
 * Home-screen sections. New systems (e.g. Attributes) add a section with
 * {@link #registerSection} instead of editing the Home screen.
 *
 * <p>Security: the panel item is only a shortcut. Opening checks {@link #PERMISSION}, and every
 * click re-checks it ({@link MenuManager}). Holding the item grants nothing.
 */
public final class AdminPanel {

    /** The one permission for the whole panel (declared in plugin.yml, default: op). */
    public static final String PERMISSION = "cerb.admin";

    /** A tile on the Home screen. */
    public record Section(String id, int order, Material icon, String name, List<String> description,
                          Status status, Function<AdminPanel, Menu> opener) {}

    private final CerberusPlugin plugin;
    private final MenuManager menus;
    private final MobTestService mobTools;
    private final NamespacedKey itemKey;
    private final List<Section> sections = new ArrayList<>();

    public AdminPanel(CerberusPlugin plugin, MenuManager menus, MobTestService mobTools) {
        this.plugin = plugin;
        this.menus = menus;
        this.mobTools = mobTools;
        this.itemKey = new NamespacedKey(plugin, "admin_panel_item");
        AdminSections.registerDefaults(this);
    }

    public CerberusPlugin plugin() { return plugin; }
    public MenuManager menus() { return menus; }
    public MobTestService mobTools() { return mobTools; }

    // ── Opening ──────────────────────────────────────────────────

    /** Open the Home screen if (and only if) the player has the admin permission. */
    public void open(Player player) {
        if (!player.hasPermission(PERMISSION)) {
            player.sendMessage(ChatColor.RED + "The Cerberus Control Center is for server admins only.");
            plugin.getLogger().warning("[Admin] " + player.getName() + " tried to open the admin panel without " + PERMISSION);
            return;
        }
        menus.openRoot(player, new AdminHomeMenu(this));
    }

    // ── Sections ─────────────────────────────────────────────────

    public synchronized void registerSection(Section section) {
        sections.removeIf(s -> s.id().equals(section.id()));
        sections.add(section);
        sections.sort(Comparator.comparingInt(Section::order));
    }

    public synchronized List<Section> sections() {
        return List.copyOf(sections);
    }

    // ── Panel item ───────────────────────────────────────────────

    /** The hotbar shortcut item. Tagged in PDC; the tag alone grants nothing. */
    public ItemStack createPanelItem() {
        ItemStack item = new ItemStack(Material.ENDER_CHEST);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(ChatColor.DARK_PURPLE + "Admin Control Panel");
        meta.setLore(List.of(ChatColor.GRAY + "Right-click to open the Cerberus Control Center.",
                ChatColor.DARK_GRAY + "Admins only."));
        meta.getPersistentDataContainer().set(itemKey, PersistentDataType.BYTE, (byte) 1);
        item.setItemMeta(meta);
        return item;
    }

    /** True for the panel item (new PDC-tagged ones, and older ones recognised by name). */
    public boolean isPanelItem(ItemStack item) {
        if (item == null || item.getType() != Material.ENDER_CHEST || !item.hasItemMeta()) return false;
        ItemMeta meta = item.getItemMeta();
        if (meta.getPersistentDataContainer().has(itemKey, PersistentDataType.BYTE)) return true;
        return meta.hasDisplayName() && meta.getDisplayName().equals(ChatColor.DARK_PURPLE + "Admin Control Panel");
    }

    // ── Audit ────────────────────────────────────────────────────

    /** Every state-changing admin action is written to the server log. */
    public void audit(Player admin, String what) {
        plugin.getLogger().info("[Admin] " + admin.getName() + ": " + what);
    }
}
