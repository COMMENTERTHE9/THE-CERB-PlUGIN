package GUIs.menu;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Identity of one open menu inventory: which {@link Menu} rendered it, for which viewer, and
 * the action bound to each button slot. A new holder is created every time a menu is opened,
 * so a stale or copied inventory can never route clicks to another menu.
 *
 * <p>Only touched on the viewer's region thread (render and click both run there).
 */
public final class MenuHolder implements InventoryHolder {

    private final Menu menu;
    private final UUID viewerId;
    private final Map<Integer, MenuAction> actions = new HashMap<>();
    private Inventory inventory;

    MenuHolder(Menu menu, UUID viewerId) {
        this.menu = menu;
        this.viewerId = viewerId;
    }

    void attach(Inventory inventory) { this.inventory = inventory; }

    @Override
    public Inventory getInventory() { return inventory; }

    public Menu menu() { return menu; }

    public UUID viewerId() { return viewerId; }

    void bind(int slot, MenuAction action) { actions.put(slot, action); }

    MenuAction actionAt(int slot) { return actions.get(slot); }

    void clearActions() { actions.clear(); }
}
