package GUIs.menu;

/**
 * One screen of a Cerberus menu. Subclasses describe what to show in {@link #build(MenuView)};
 * {@link MenuManager} owns opening, navigation, refresh, permissions, and click routing.
 *
 * <p>A Menu instance is kept on the viewer's navigation stack, so it may hold view state
 * (current page, selected level). Data is re-read on every build, never cached across opens.
 */
public abstract class Menu {

    protected final MenuManager menus;

    protected Menu(MenuManager menus) {
        this.menus = menus;
    }

    /** Inventory title (legacy color codes allowed). */
    public abstract String title();

    /** Rows (1-6). Menus using the standard navigation bar must use 6. */
    public int rows() { return 6; }

    /**
     * Permission needed to open this menu and to click anything in it, checked every time.
     * {@code null} = no permission needed.
     */
    public String permission() { return null; }

    /**
     * Whether moving here from another screen may redraw the open window in place (fast). A screen
     * whose buttons change something at once returns false: it always gets a fresh window, so a
     * click the player aimed at the previous screen can never reach one of its buttons (the
     * server drops clicks for a window that is no longer open).
     */
    public boolean reuseWindow() { return true; }

    /** Render. Runs on the viewer's region thread; may be called again to refresh in place. */
    protected abstract void build(MenuView view);
}
