package GUIs.menu;

/** What a menu button does when clicked. Bound server-side to a slot; never read from item data. */
@FunctionalInterface
public interface MenuAction {
    void onClick(MenuClick click);
}
