package GUIs.menu;

import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;

/** A validated click on a menu button (right viewer, permission re-checked). */
public record MenuClick(Player player, ClickType type, MenuHolder holder) {
    public boolean isShift() { return type.isShiftClick(); }
    public boolean isRight() { return type.isRightClick(); }
}
