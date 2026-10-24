package GUIs.menu;

import org.bukkit.ChatColor;

/** Honest feature status shown on menu tiles. */
public enum Status {
    LIVE(ChatColor.GREEN + "● Live"),
    PARTIAL(ChatColor.YELLOW + "◐ Partly built"),
    IN_DEVELOPMENT(ChatColor.GRAY + "○ In development - not built yet");

    public final String label;

    Status(String label) { this.label = label; }
}
