package GUIs.menu;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Identity of one open menu window: which {@link Menu} is drawn in it right now, for which
 * viewer, its title, and the action bound to each button slot. A window belongs to one viewer
 * for its whole life. Moving to another screen of the same size redraws the same window
 * ({@link MenuManager}); every redraw clears and rebinds all actions, so a click can only ever
 * reach a button of the screen currently shown.
 *
 * <p>Only touched on the viewer's region thread (render and click both run there).
 */
public final class MenuHolder implements InventoryHolder {

    private Menu menu;
    private String title;
    /** When this window last switched to another screen in place (ms), 0 if never. */
    private long switchedAt;
    private final UUID viewerId;
    private final Map<Integer, MenuAction> actions = new HashMap<>();
    private Inventory inventory;

    MenuHolder(Menu menu, UUID viewerId) {
        this.menu = menu;
        this.title = menu.title();
        this.viewerId = viewerId;
    }

    /** The window now shows {@code next} (same size); returns true when its title must change. */
    boolean switchTo(Menu next) {
        this.menu = next;
        this.switchedAt = System.currentTimeMillis();
        String t = next.title();
        if (t.equals(title)) return false;
        title = t;
        return true;
    }

    void attach(Inventory inventory) { this.inventory = inventory; }

    @Override
    public Inventory getInventory() { return inventory; }

    public Menu menu() { return menu; }

    long switchedAt() { return switchedAt; }

    public UUID viewerId() { return viewerId; }

    void bind(int slot, MenuAction action) { actions.put(slot, action); }

    MenuAction actionAt(int slot) { return actions.get(slot); }

    void clearActions() { actions.clear(); }
}
