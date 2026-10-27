package GUIs.menu;

import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;

/** A validated click on a menu button (right viewer, permission re-checked). */
public record MenuClick(Player player, ClickType type, MenuHolder holder) {
    public boolean isShift() { return type.isShiftClick(); }
    public boolean isRight() { return type.isRightClick(); }
    /** A click of the mouse (left or right, with or without Shift): not a number key, drop, middle click or offhand swap. */
    public boolean isMouse() {
        return type == ClickType.LEFT || type == ClickType.RIGHT || type == ClickType.SHIFT_LEFT || type == ClickType.SHIFT_RIGHT;
    }
}
