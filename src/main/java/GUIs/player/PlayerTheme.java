package GUIs.player;

import org.bukkit.ChatColor;
import org.bukkit.Material;

/**
 * The look of every player-facing screen: one title style, one frame, one bar style, so the
 * Player Menu reads as a single interface (and stays distinct from the admin Control Center).
 * Icons are plain Minecraft items today; a resource pack can later swap them per tile without
 * touching layouts.
 */
public final class PlayerTheme {

    private PlayerTheme() {}

    /** Background and navigation row. */
    public static final Material FRAME = Material.BLACK_STAINED_GLASS_PANE;
    /** Header row of the hub and general screens. */
    public static final Material HEADER = Material.GRAY_STAINED_GLASS_PANE;

    public static final ChatColor PRIMARY = ChatColor.GOLD;      // main destinations
    public static final ChatColor SECONDARY = ChatColor.YELLOW;  // secondary destinations
    public static final ChatColor HEALTH = ChatColor.RED;
    public static final ChatColor ESSENCE = ChatColor.AQUA;
    public static final ChatColor DEFENSE = ChatColor.GRAY;
    public static final ChatColor REALM = ChatColor.LIGHT_PURPLE;

    /**
     * The step between realms as configured (combat.yml tier-multiplier): "x8" when every step is
     * the same, else the range ("x6 to x10").
     */
    public static String realmStep(Manager.MobHealthScaling scaling) {
        double lo = Double.MAX_VALUE, hi = 0;
        Manager.Realm[] r = Manager.Realm.values();
        for (int i = 1; i < r.length; i++) {
            double step = scaling.realmMultiplier(r[i].tier()) / scaling.realmMultiplier(r[i - 1].tier());
            lo = Math.min(lo, step);
            hi = Math.max(hi, step);
        }
        if (!(hi > 0)) return "x1";
        return hi - lo < 0.01 * hi ? "x" + Manager.Numbers.compact(hi) : "x" + Manager.Numbers.compact(lo) + " to x" + Manager.Numbers.compact(hi);
    }

    /** "▲ +3 levels since you last looked": progress made while the menu was closed (Player Menu 2.1). */
    public static String newProgress(int levels) {
        return ChatColor.GREEN + "▲ +" + levels + (levels == 1 ? " level" : " levels") + " since you last looked";
    }

    /**
     * Mark a tile with new levels: a green "▲" before its name, and the count in its corner from 2 up
     * (a count of 1 isn't drawn). A glow alone can't say it: some icons, like the experience bottle
     * and the enchanted book, always glow.
     */
    public static org.bukkit.inventory.ItemStack badge(org.bukkit.inventory.ItemStack tile, int newLevels) {
        if (newLevels <= 0) return tile;
        org.bukkit.inventory.meta.ItemMeta meta = tile.getItemMeta();
        if (meta != null && meta.hasDisplayName()) {
            meta.setDisplayName(ChatColor.GREEN + "▲ " + meta.getDisplayName());
            tile.setItemMeta(meta);
        }
        return GUIs.menu.Ui.amount(tile, newLevels);
    }

    /** "✦ Skills" — every player screen title. */
    public static String title(String screen) {
        return ChatColor.DARK_GRAY + "✦ " + ChatColor.DARK_RED + "" + ChatColor.BOLD + screen;
    }

    /**
     * Indent a lore line by two spaces. The spaces go after a colour code: leading spaces before
     * the first colour code are dropped when the line becomes a text component.
     */
    public static String indent(String line) {
        return ChatColor.GRAY + "  " + line;
    }

    /**
     * Word-wrap {@code text} into lines of at most {@code width} characters (a word longer than
     * the width gets a line of its own). Tooltips stay narrow and readable.
     */
    public static java.util.List<String> wrap(String text, int width) {
        java.util.List<String> out = new java.util.ArrayList<>();
        if (text == null || text.isBlank()) return out;
        StringBuilder line = new StringBuilder();
        for (String word : text.trim().split("\\s+")) {
            if (line.length() > 0 && line.length() + 1 + word.length() > width) {
                out.add(line.toString());
                line.setLength(0);
            }
            if (line.length() > 0) line.append(' ');
            line.append(word);
        }
        if (line.length() > 0) out.add(line.toString());
        return out;
    }

    /**
     * A progress bar that does not rely on colour alone: filled/empty squares plus the
     * percentage as text. {@code fill} colours the filled part.
     */
    public static String bar(double current, double max, int width, ChatColor fill) {
        double f = max > 0 && Double.isFinite(current) && Double.isFinite(max) ? Math.max(0, Math.min(1, current / max)) : 0;
        int full = (int) Math.round(f * width);
        StringBuilder sb = new StringBuilder();
        sb.append(fill);
        for (int i = 0; i < full; i++) sb.append('■');
        sb.append(ChatColor.DARK_GRAY);
        for (int i = full; i < width; i++) sb.append('■');
        sb.append(ChatColor.GRAY).append(' ').append(String.format(java.util.Locale.ROOT, "%.0f%%", f * 100));
        return sb.toString();
    }
}
