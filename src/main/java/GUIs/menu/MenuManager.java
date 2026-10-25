package GUIs.menu;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/**
 * Opens, navigates, refreshes, and routes clicks for {@link Menu} screens.
 *
 * <p>Security model:
 * <ul>
 *   <li>Every click in a menu is cancelled before anything else, so items never move in or out
 *       (no duplication, no theft) - including shift-clicks, number keys and drags.</li>
 *   <li>Actions are bound server-side to the slots of the screen currently drawn; nothing is read
 *       from item data, so copied or forged items do nothing.</li>
 *   <li>A click aimed at the previous screen never reaches a button that changes something:
 *       such screens ({@link Menu#reuseWindow()} false: admin and confirm screens) always get a
 *       fresh window, and the server drops clicks for a window that is no longer open. Read-only
 *       screens of the same size share one window (fast); there a click arriving within the
 *       player's round trip after a switch is ignored, as it was aimed at the screen before.
 *       (A screen's own {@code refresh} never had this protection: it redraws the same buttons.)</li>
 *   <li>The clicker must be the viewer the menu was rendered for.</li>
 *   <li>{@link Menu#permission()} is checked on open AND on every click; losing the permission
 *       mid-session closes the menu.</li>
 * </ul>
 *
 * <p>Folia: clicks arrive on the viewer's region thread; opening is always done there.
 */
public final class MenuManager implements Listener {

    private static final long CLICK_DEBOUNCE_MS = 120;
    /** After an in-place switch, clicks are ignored for about one round trip: ping + this, within the bounds below. */
    private static final long SWITCH_GUARD_EXTRA_MS = 150, SWITCH_GUARD_MIN_MS = 250, SWITCH_GUARD_MAX_MS = 1000;

    private final JavaPlugin plugin;
    private final Map<UUID, Deque<Menu>> stacks = new ConcurrentHashMap<>();
    private final Map<UUID, Long> lastClick = new ConcurrentHashMap<>();
    private final MenuAnimator animator;
    /** Set when a click's action moved to another screen (that screen played its own sound). Per region thread. */
    private final ThreadLocal<Boolean> navigated = ThreadLocal.withInitial(() -> Boolean.FALSE);

    /** How a screen was reached: opened from nothing, deeper, or back/home. Decides the sound. */
    private enum Nav { OPEN, FORWARD, BACK }

    public MenuManager(JavaPlugin plugin) {
        this.plugin = plugin;
        this.animator = new MenuAnimator(plugin);
    }

    /** The animation player (diagnostics: how many run now, how many started). */
    public MenuAnimator animator() { return animator; }

    public JavaPlugin plugin() { return plugin; }

    // ── Navigation ───────────────────────────────────────────────

    /** Open {@code menu} as a fresh root (clears the Back history). */
    public void openRoot(Player player, Menu menu) {
        onPlayerThread(player, () -> {
            Deque<Menu> stack = new ArrayDeque<>();
            stack.push(menu);
            stacks.put(player.getUniqueId(), stack);
            show(player, menu, Nav.BACK); // from inside a menu this is "home"; from nothing it is an opening
        });
    }

    /** Open {@code menu} on top of the current one (Back returns here). */
    public void open(Player player, Menu menu) {
        onPlayerThread(player, () -> {
            stacks.computeIfAbsent(player.getUniqueId(), k -> new ArrayDeque<>()).push(menu);
            show(player, menu, Nav.FORWARD);
        });
    }

    /**
     * Swap the current screen for {@code menu} without growing the history (Back still returns to
     * the screen before). For browsing sideways, e.g. next/previous skill; the window title changes too.
     */
    public void replace(Player player, Menu menu) {
        onPlayerThread(player, () -> {
            Deque<Menu> stack = stacks.computeIfAbsent(player.getUniqueId(), k -> new ArrayDeque<>());
            if (!stack.isEmpty()) stack.pop();
            stack.push(menu);
            show(player, menu, Nav.FORWARD);
        });
    }

    /** Go back one screen (closes when there is nothing to go back to). */
    public void back(Player player) {
        backFrom(player, null);
    }

    /**
     * Go back one screen, but only if the player is still looking at {@code from} (null: any
     * menu). For callers that finish later (after a task on another thread): if the admin has
     * closed the panel or moved on meanwhile, this does nothing instead of closing whatever
     * they have open now or popping the wrong screen.
     */
    public void backFrom(Player player, Menu from) {
        onPlayerThread(player, () -> {
            Menu current = viewing(player);
            if (current == null || (from != null && current != from)) return;
            Deque<Menu> stack = stacks.get(player.getUniqueId());
            if (stack == null || stack.size() <= 1) { close(player); return; }
            stack.pop();
            show(player, stack.peek(), Nav.BACK);
        });
    }

    /** Return to the bottom (root) screen. */
    public void home(Player player) {
        onPlayerThread(player, () -> {
            if (viewing(player) == null) return; // panel already closed
            Deque<Menu> stack = stacks.get(player.getUniqueId());
            if (stack == null || stack.isEmpty()) { close(player); return; }
            while (stack.size() > 1) stack.pop();
            show(player, stack.peek(), Nav.BACK);
        });
    }

    /** Re-render the screen the player is looking at, in place (no flicker, cursor kept). */
    public void refresh(Player player) {
        onPlayerThread(player, () -> {
            Inventory top = player.getOpenInventory().getTopInventory();
            if (top.getHolder() instanceof MenuHolder holder && holder.viewerId().equals(player.getUniqueId())) {
                if (!allowed(player, holder.menu())) { deny(player); return; }
                MenuView v = render(player, holder.menu(), top, holder);
                animator.play(player, holder, top, v.keyframes());
            }
        });
    }

    public void close(Player player) {
        onPlayerThread(player, () -> {
            stacks.remove(player.getUniqueId());
            player.closeInventory();
        });
    }

    /** The menu this player is looking at right now, or null. Call on the player's thread. */
    private static Menu viewing(Player player) {
        Inventory top = player.getOpenInventory().getTopInventory();
        return top.getHolder() instanceof MenuHolder h && h.viewerId().equals(player.getUniqueId()) ? h.menu() : null;
    }

    /**
     * Plugin shutdown: empty every open menu so nothing in it can be taken once our click
     * listener is gone, then drop all history.
     */
    public void closeAll() {
        for (UUID id : stacks.keySet()) {
            Player p = Bukkit.getPlayer(id);
            if (p == null) continue;
            Inventory top = p.getOpenInventory().getTopInventory();
            if (top.getHolder() instanceof MenuHolder) top.clear();
        }
        animator.cancelAll();
        stacks.clear();
        lastClick.clear();
    }

    /** Run {@code task} on the player's region thread (immediately if already there). */
    public void onPlayerThread(Player player, Runnable task) {
        if (Bukkit.isOwnedByCurrentRegion(player)) {
            task.run();
        } else {
            player.getScheduler().run(plugin, t -> task.run(), null);
        }
    }

    // ── Rendering ────────────────────────────────────────────────

    private void show(Player player, Menu menu, Nav nav) {
        if (!allowed(player, menu)) { deny(player); return; }
        int size = Math.max(1, Math.min(6, menu.rows())) * 9;
        // Already looking at one of our windows of the same size: draw the new screen into it
        // and retitle it. Opening a new window per click made vanilla re-check every
        // inventory-change advancement against the whole player inventory: about 60% of a
        // menu click's server time (docs/PERFORMANCE.md).
        InventoryView view = player.getOpenInventory();
        Inventory top = view.getTopInventory();
        if (menu.reuseWindow() && top.getHolder() instanceof MenuHolder open
                && open.viewerId().equals(player.getUniqueId()) && top.getSize() == size) {
            boolean another = open.menu().getClass() != menu.getClass();
            boolean retitle = open.switchTo(menu);
            MenuView v = render(player, menu, top, open);
            if (retitle) view.setTitle(menu.title());
            if (another && menu.animationBackground() != null) sweep(v, top, open);
            animator.play(player, open, top, v.keyframes());
            sound(player, menu, nav);
            return;
        }
        boolean fromNothing = !(top.getHolder() instanceof MenuHolder);
        MenuHolder holder = new MenuHolder(menu, player.getUniqueId());
        Inventory inv = Bukkit.createInventory(holder, size, menu.title());
        holder.attach(inv);
        MenuView v = render(player, menu, inv, holder);
        if (fromNothing && menu.animationBackground() != null) reveal(v, inv, menu.animationBackground());
        player.openInventory(inv);
        if (player.getOpenInventory().getTopInventory() != inv) return; // another plugin cancelled the open
        animator.play(player, holder, inv, v.keyframes());
        sound(player, menu, fromNothing ? Nav.OPEN : nav);
    }

    private MenuView render(Player player, Menu menu, Inventory inv, MenuHolder holder) {
        holder.nextRender();
        inv.clear();
        holder.clearActions();
        MenuView v = new MenuView(this, player, inv, holder);
        try {
            menu.build(v);
        } catch (Throwable t) {
            plugin.getLogger().log(Level.SEVERE, "[Menu] Failed to build " + menu.getClass().getSimpleName(), t);
            Ui.fail(player, "This screen failed to load (" + t.getClass().getSimpleName() + "). Details are in the server log.");
        }
        return v;
    }

    /**
     * Opening from nothing: the content (between the header and navigation rows) appears from the
     * centre outwards over 4 ticks instead of all at once. The header and navigation are there at
     * once, and every button works from the first tick.
     */
    private static void reveal(MenuView v, Inventory inv, Material background) {
        int rows = inv.getSize() / 9;
        if (rows < 3) return;
        ItemStack bg = Ui.filler(background);
        for (int r = 1; r < rows - 1; r++) {
            for (int c = 0; c < 9; c++) {
                int slot = r * 9 + c;
                ItemStack it = inv.getItem(slot);
                if (it == null || v.animates(slot) || it.isSimilar(bg)) continue;
                ItemStack shown = it.clone();
                inv.setItem(slot, bg);
                v.later(revealTick(c), slot, shown);
            }
        }
    }

    /** The tick column {@code c} (0-8) appears on when a menu opens: the centre first, the edges last (1-4). */
    static int revealTick(int c) {
        return 1 + Math.min(3, Math.abs(c - 4));
    }

    /**
     * Moving to another screen in place: a band of light sweeps out from the centre of the header
     * row and settles back (5 ticks). The new content is already there.
     */
    private static void sweep(MenuView v, Inventory inv, MenuHolder h) {
        ItemStack light = Ui.filler(Material.WHITE_STAINED_GLASS_PANE);
        for (int k = 1; k <= 4; k++) {
            for (int c : new int[]{4 - k, 4 + k}) {
                ItemStack it = inv.getItem(c);
                if (it == null || h.actionAt(c) != null || v.animates(c)
                        || !it.getType().name().endsWith("_STAINED_GLASS_PANE")) continue;
                v.later(k, c, light);
                v.later(k + 1, c, it.clone());
            }
        }
    }

    /** The screen's sound for how it was reached; screens without navigation sounds leave it to the click. */
    private void sound(Player player, Menu menu, Nav nav) {
        if (!menu.navigationSounds()) return;
        navigated.set(Boolean.TRUE);
        switch (nav) {
            case OPEN -> player.playSound(player.getLocation(), Sound.ITEM_BOOK_PAGE_TURN, 0.8f, 1.0f);
            case FORWARD -> player.playSound(player.getLocation(), Sound.ITEM_BOOK_PAGE_TURN, 0.6f, 1.2f);
            case BACK -> player.playSound(player.getLocation(), Sound.ITEM_BOOK_PAGE_TURN, 0.6f, 0.85f);
        }
    }

    private boolean allowed(Player player, Menu menu) {
        String perm = menu.permission();
        return perm == null || player.hasPermission(perm);
    }

    private void deny(Player player) {
        stacks.remove(player.getUniqueId());
        player.closeInventory();
        player.sendMessage(ChatColor.RED + "You don't have permission to use this menu.");
    }

    // ── Events ───────────────────────────────────────────────────

    @EventHandler(priority = EventPriority.LOWEST)
    public void onClick(InventoryClickEvent event) {
        Inventory top = event.getView().getTopInventory();
        if (!(top.getHolder() instanceof MenuHolder holder)) return;
        event.setCancelled(true); // first, unconditionally: nothing ever moves while a menu is open

        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (!holder.viewerId().equals(player.getUniqueId())) return;
        if (event.getClickedInventory() != top) return;           // clicks in the player's own inventory
        if (event.getClick() == ClickType.DOUBLE_CLICK) return;   // the single click already ran

        MenuAction action = holder.actionAt(event.getRawSlot());
        if (action == null) return;

        if (!allowed(player, holder.menu())) {                     // re-validated on every click
            plugin.getLogger().warning("[Menu] " + player.getName() + " clicked "
                    + holder.menu().getClass().getSimpleName() + " without permission - closed.");
            deny(player);
            return;
        }

        // Just switched screens in this window: a click arriving within the player's round trip
        // (plus up to a tick each way and a client frame) was aimed at the previous screen.
        long guard = Math.min(SWITCH_GUARD_MAX_MS, Math.max(SWITCH_GUARD_MIN_MS, player.getPing() + SWITCH_GUARD_EXTRA_MS));
        if (holder.msSinceSwitch() < guard) return;
        long now = System.nanoTime();
        Long prev = lastClick.put(player.getUniqueId(), now);
        if (prev != null && now - prev < CLICK_DEBOUNCE_MS * 1_000_000L) return; // accidental double-fire

        navigated.set(Boolean.FALSE);
        try {
            action.onClick(new MenuClick(player, event.getClick(), holder));
        } catch (Throwable t) {
            plugin.getLogger().log(Level.SEVERE, "[Menu] Action failed in "
                    + holder.menu().getClass().getSimpleName() + " for " + player.getName(), t);
            Ui.fail(player, "That action failed: " + t.getClass().getSimpleName()
                    + (t.getMessage() != null ? " - " + t.getMessage() : "") + ". Nothing else was changed.");
            return;
        }
        if (!navigated.get()) Ui.click(player); // a move to another screen already played its page sound
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof MenuHolder) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        if (!(event.getInventory().getHolder() instanceof MenuHolder)) return;
        // Switching screens closes the old inventory with reason OPEN_NEW - keep the history then.
        UUID id = event.getPlayer().getUniqueId();
        animator.cancel(id);
        if (event.getReason() != InventoryCloseEvent.Reason.OPEN_NEW) {
            stacks.remove(id);
            if (event.getInventory().getHolder() instanceof MenuHolder h && event.getPlayer() instanceof Player p) {
                try {
                    h.menu().closed(p);
                } catch (Throwable t) {
                    plugin.getLogger().log(Level.WARNING, "[Menu] close hook failed in " + h.menu().getClass().getSimpleName(), t);
                }
            }
        } else if (event.getPlayer() instanceof Player p) {
            // ...unless what opened next was not one of our menus (another plugin's inventory, or
            // a cancelled open): then the history is stale and the menu counts as closed. Checked
            // once the open has settled.
            Menu was = event.getInventory().getHolder() instanceof MenuHolder h ? h.menu() : null;
            p.getScheduler().run(plugin, t -> {
                if (viewing(p) != null) return;
                stacks.remove(id);
                if (was != null) {
                    try {
                        was.closed(p);
                    } catch (Throwable ex) {
                        plugin.getLogger().log(Level.WARNING, "[Menu] close hook failed in " + was.getClass().getSimpleName(), ex);
                    }
                }
            }, null);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        stacks.remove(event.getPlayer().getUniqueId());
        lastClick.remove(event.getPlayer().getUniqueId());
        animator.cancel(event.getPlayer().getUniqueId());
    }

    /** How many screens deep the player is (1 = a root screen, 0 = no menu open). */
    public int depth(Player player) {
        Deque<Menu> stack = stacks.get(player.getUniqueId());
        return stack == null ? 0 : stack.size();
    }

    /** For diagnostics: how many players currently have a menu history. */
    public int activeViewers() { return stacks.size(); }
}
