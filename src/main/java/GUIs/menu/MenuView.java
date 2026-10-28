package GUIs.menu;

import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;
import java.util.function.Function;
import java.util.function.IntConsumer;

/**
 * What a {@link Menu} draws into during {@link Menu#build}. Buttons bind a server-side
 * {@link MenuAction} to a slot; everything else is decoration that does nothing when clicked.
 */
public final class MenuView {

    // Standard 6-row layout: row 0 header, rows 1-4 content, row 5 navigation.
    public static final int HEADER = 4;
    public static final int BACK = 45;
    public static final int PREV = 48;
    public static final int HOME = 49;
    public static final int NEXT = 50;
    public static final int REFRESH = 52;
    public static final int CLOSE = 53;

    private final MenuManager menus;
    private final Player viewer;
    private final Inventory inventory;
    private final MenuHolder holder;
    /** Animation steps asked for while building (null: none). */
    private List<Keyframe> keyframes;
    private BitSet animatedSlots;
    /** A live update asked for while building (null: none) and how often, in ticks. */
    private MenuTicker.Update live;
    private int livePeriod;

    /** One animation step: {@code tick} ticks after the screen appears, show {@code item} in {@code slot}, play {@code sound} and/or run {@code action}. */
    record Keyframe(int tick, int slot, ItemStack item, Sound sound, float volume, float pitch, Runnable action) {}

    MenuView(MenuManager menus, Player viewer, Inventory inventory, MenuHolder holder) {
        this.menus = menus;
        this.viewer = viewer;
        this.inventory = inventory;
        this.holder = holder;
    }

    public Player viewer() { return viewer; }

    public int size() { return inventory.getSize(); }

    /** Decorative item (clicking it does nothing). */
    public void set(int slot, ItemStack item) {
        if (slot < 0 || slot >= inventory.getSize()) return;
        inventory.setItem(slot, item);
    }

    /** Clickable button. */
    public void button(int slot, ItemStack item, MenuAction action) {
        if (slot < 0 || slot >= inventory.getSize()) return;
        inventory.setItem(slot, item);
        holder.bind(slot, action);
    }

    // ── Animation (Player Menu 2.1; played by MenuAnimator) ───────

    /**
     * Show {@code item} in {@code slot} {@code ticks} ticks after this screen appears (1 to
     * {@link MenuAnimator#MAX_TICKS}): draw the starting look with {@link #set}/{@link #button}, then
     * the steps here. A button keeps working throughout. Steps are dropped when the screen changes,
     * refreshes or closes first.
     */
    public void later(int ticks, int slot, ItemStack item) {
        if (item == null || slot < 0 || slot >= inventory.getSize()) return;
        add(new Keyframe(clampTick(ticks), slot, item, null, 0f, 0f, null));
        if (animatedSlots == null) animatedSlots = new BitSet(inventory.getSize());
        animatedSlots.set(slot);
    }

    /** Play {@code sound} to the viewer {@code ticks} ticks after this screen appears (dropped like {@link #later}). */
    public void soundLater(int ticks, Sound sound, float volume, float pitch) {
        if (sound != null) add(new Keyframe(clampTick(ticks), -1, null, sound, volume, pitch, null));
    }

    /**
     * Run {@code action} on the viewer's thread {@code ticks} ticks after this screen appears, only if
     * the animation gets that far: for marking a once-only effect as seen when it really played.
     */
    public void runLater(int ticks, Runnable action) {
        if (action != null) add(new Keyframe(clampTick(ticks), -1, null, null, 0f, 0f, action));
    }

    /**
     * Keep this screen live (docs/design/ACTIVE_EFFECTS.md §3): run {@code update} every {@code periodTicks} ticks (at
     * least {@link MenuTicker#MIN_PERIOD}) on the viewer's thread for as long as this exact render is shown, until it
     * returns false. For countdowns: an update rewrites only the slots that show a time ({@link #set}); a screen whose
     * content changed asks for a {@link MenuManager#refresh}. Ask only when something on the screen changes by itself.
     */
    public void every(int periodTicks, MenuTicker.Update update) {
        this.live = update;
        this.livePeriod = periodTicks;
    }

    MenuTicker.Update live() { return live; }

    int livePeriod() { return livePeriod; }

    /** The item now in {@code slot} (for a live update that changes only part of it). */
    public ItemStack get(int slot) {
        return slot < 0 || slot >= inventory.getSize() ? null : inventory.getItem(slot);
    }

    /** True if this screen animates {@code slot} itself (generic transitions leave it alone). */
    boolean animates(int slot) { return animatedSlots != null && animatedSlots.get(slot); }

    List<Keyframe> keyframes() { return keyframes == null ? List.of() : keyframes; }

    private void add(Keyframe k) {
        if (keyframes == null) keyframes = new ArrayList<>();
        keyframes.add(k);
    }

    private static int clampTick(int t) { return Math.max(1, Math.min(MenuAnimator.MAX_TICKS, t)); }

    /** Fill every slot that is still empty with a filler pane (call last). */
    public void fill(Material glass) {
        ItemStack pane = Ui.filler(glass);
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            if (inventory.getItem(slot) == null) inventory.setItem(slot, pane);
        }
    }

    /** Fill a whole row with a filler pane. */
    public void row(int row, Material glass) {
        ItemStack pane = Ui.filler(glass);
        for (int c = 0; c < 9; c++) {
            int slot = row * 9 + c;
            if (slot < inventory.getSize() && inventory.getItem(slot) == null) inventory.setItem(slot, pane);
        }
    }

    /** Standard frame: colored header row, black navigation row with Back / Home / Close. */
    public void frame(Material headerGlass, boolean showBack, boolean showHome) {
        int rows = inventory.getSize() / 9;
        if (showBack) {
            button(BACK, Ui.item(Material.ARROW, ChatColor.YELLOW + "Back",
                    Ui.lore().text("Previous screen").build()), c -> menus.back(c.player()));
        }
        if (showHome) {
            button(HOME, Ui.item(Material.NETHER_STAR, ChatColor.LIGHT_PURPLE + "Home",
                    Ui.lore().text("Back to the start screen").build()), c -> menus.home(c.player()));
        }
        button(CLOSE, Ui.item(Material.BARRIER, ChatColor.RED + "Close",
                Ui.lore().text("Close this menu").build()), c -> menus.close(c.player()));
        row(0, headerGlass);
        row(rows - 1, Material.BLACK_STAINED_GLASS_PANE);
    }

    /** A Refresh button that rebuilds this screen with current data. */
    public void refreshButton() {
        button(REFRESH, Ui.item(Material.CLOCK, ChatColor.AQUA + "Refresh",
                Ui.lore().text("Re-read the live values").build()), c -> menus.refresh(c.player()));
    }

    /**
     * Render one page of {@code items} into the standard 28-slot content area and add
     * Previous/Next buttons. Returns the page actually shown (clamped).
     */
    public <T> int page(List<T> items, int page, Function<T, ItemStack> render,
                        Function<T, MenuAction> action, IntConsumer setPage) {
        int per = Paging.CONTENT_SLOTS.length;
        int shown = Paging.clampPage(page, items.size(), per);
        int pages = Paging.pageCount(items.size(), per);
        List<T> slice = Paging.slice(items, shown, per);
        for (int i = 0; i < slice.size(); i++) {
            T t = slice.get(i);
            MenuAction a = action == null ? null : action.apply(t);
            if (a == null) set(Paging.CONTENT_SLOTS[i], render.apply(t));
            else button(Paging.CONTENT_SLOTS[i], render.apply(t), a);
        }
        if (shown > 0) {
            button(PREV, Ui.item(Material.SPECTRAL_ARROW, ChatColor.YELLOW + "Previous page",
                    Ui.lore().stat("Page", (shown) + " / " + pages).build()), c -> {
                setPage.accept(shown - 1);
                menus.refresh(c.player());
            });
        }
        if (shown < pages - 1) {
            button(NEXT, Ui.item(Material.SPECTRAL_ARROW, ChatColor.YELLOW + "Next page",
                    Ui.lore().stat("Page", (shown + 2) + " / " + pages).build()), c -> {
                setPage.accept(shown + 1);
                menus.refresh(c.player());
            });
        }
        return shown;
    }
}
