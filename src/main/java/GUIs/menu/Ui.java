package GUIs.menu;

import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Shared look and feedback for Cerberus menus, so every screen reads the same way:
 * colored name, gray description, white values, then a yellow "click" hint.
 */
public final class Ui {

    private Ui() {}

    // ── Palette ──────────────────────────────────────────────────
    public static final ChatColor HEAD = ChatColor.GOLD;          // tile names
    public static final ChatColor TEXT = ChatColor.GRAY;          // descriptions
    public static final ChatColor VALUE = ChatColor.WHITE;        // numbers
    public static final ChatColor GOOD = ChatColor.GREEN;
    public static final ChatColor BAD = ChatColor.RED;
    public static final ChatColor WARN = ChatColor.YELLOW;

    // ── Items ────────────────────────────────────────────────────

    /**
     * Finished items by (material, name, lore). Menus redraw the same tiles over and over, and
     * turning legacy text into item text is most of an item's cost (Bukkit runs a regular
     * expression over every line; docs/PERFORMANCE.md). Only copies are handed out, so callers
     * may still change what they get (glow, amount, skull owner). Cleared when full.
     */
    private static final int ITEM_CACHE_MAX = 2048;
    private static final Map<ItemKey, ItemStack> ITEMS = new ConcurrentHashMap<>();

    private record ItemKey(Material material, String name, List<String> lore) {}

    /** Item with a name and lore; hides vanilla tooltips (attributes, enchants...). */
    public static ItemStack item(Material material, String name, List<String> lore) {
        List<String> lines = lore == null || lore.isEmpty() ? List.of() : copyWithoutNulls(lore);
        if (lines == null) return build(material, name, lore); // a null line: not cacheable
        ItemKey key = new ItemKey(material, name, lines);
        ItemStack cached = ITEMS.get(key);
        if (cached == null) {
            cached = build(material, name, lines);
            if (ITEMS.size() >= ITEM_CACHE_MAX) ITEMS.clear();
            ITEMS.put(key, cached);
        }
        return cached.clone();
    }

    /** An immutable copy, or null when a line is null. */
    private static List<String> copyWithoutNulls(List<String> lore) {
        for (String s : lore) if (s == null) return null;
        return List.copyOf(lore);
    }

    private static ItemStack build(Material material, String name, List<String> lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return item;
        meta.setDisplayName(name);
        if (lore != null && !lore.isEmpty()) meta.setLore(lore);
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ENCHANTS, ItemFlag.HIDE_UNBREAKABLE,
                ItemFlag.HIDE_ADDITIONAL_TOOLTIP, ItemFlag.HIDE_DYE, ItemFlag.HIDE_ARMOR_TRIM);
        item.setItemMeta(meta);
        return item;
    }

    public static ItemStack item(Material material, String name, String... lore) {
        return item(material, name, Arrays.asList(lore));
    }

    /** Same item with an enchantment glint (used for "selected" / "on"). */
    public static ItemStack glow(ItemStack item, boolean on) {
        if (!on) return item;
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setEnchantmentGlintOverride(true);
            item.setItemMeta(meta);
        }
        return item;
    }

    /** Stack size used as a visual counter (1-64). */
    public static ItemStack amount(ItemStack item, int amount) {
        item.setAmount(Math.max(1, Math.min(64, amount)));
        return item;
    }

    public static ItemStack head(OfflinePlayer owner, String name, List<String> lore) {
        ItemStack skull = item(Material.PLAYER_HEAD, name, lore);
        if (skull.getItemMeta() instanceof SkullMeta meta) {
            meta.setOwningPlayer(owner);
            skull.setItemMeta(meta);
        }
        return skull;
    }

    public static ItemStack filler(Material glass) {
        return item(glass, " ");
    }

    // ── Lore helpers ─────────────────────────────────────────────

    /** Builder for consistent lore blocks. */
    public static final class Lore {
        private final List<String> lines = new ArrayList<>();

        public Lore text(String s) { lines.add(TEXT + s); return this; }
        public Lore stat(String label, String value) { lines.add(TEXT + label + ": " + VALUE + value); return this; }
        public Lore raw(String s) { lines.add(s); return this; }
        public Lore status(Status s) { lines.add(s.label); return this; }
        public Lore gap() { if (!lines.isEmpty() && !lines.get(lines.size() - 1).isEmpty()) lines.add(""); return this; }
        public Lore hint(String s) { gap(); lines.add(WARN + "▸ " + s); return this; }
        public Lore warn(String s) { lines.add(BAD + "⚠ " + s); return this; }
        public List<String> build() { return lines; }
    }

    public static Lore lore() { return new Lore(); }

    // ── Feedback (chat + sound) ──────────────────────────────────

    public static void ok(Player p, String msg) {
        p.sendMessage(ChatColor.GREEN + "✔ " + ChatColor.GRAY + msg);
        p.playSound(p.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.6f, 1.4f);
    }

    public static void fail(Player p, String msg) {
        p.sendMessage(ChatColor.RED + "✘ " + ChatColor.GRAY + msg);
        p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 0.8f, 0.6f);
    }

    public static void info(Player p, String msg) {
        p.sendMessage(ChatColor.AQUA + "ℹ " + ChatColor.GRAY + msg);
    }

    public static void click(Player p) {
        p.playSound(p.getLocation(), Sound.UI_BUTTON_CLICK, 0.4f, 1.2f);
    }
}
