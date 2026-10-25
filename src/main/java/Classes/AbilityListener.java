package Classes;

import io.papermc.paper.event.player.PrePlayerAttackEntityEvent;
import org.bukkit.GameMode;
import org.bukkit.block.Block;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The cast keys (docs/design/CLASSES.md §5.3): hold Sneak and press right-click (active 1),
 * left-click (2), F (3), Q (4), or scroll / a number key (5). Only with a weapon, a focus or an
 * empty hand, and only when that slot holds an ability; otherwise every key keeps its vanilla
 * meaning, so building and using items never change. A key that casts is consumed: nothing is
 * dropped or swapped and the held slot stays. Two exceptions keep vanilla playable: a bow or
 * crossbow still shoots with right-click, and a left-click whose cast doesn't go off (cooldown,
 * no Essence) is still a normal attack.
 *
 * <p>One key press can reach the server as several events (an attack and its swing, the main hand
 * and then the off hand); within {@link #SAME_PRESS_MS} they count as one press. Abilities never
 * hit players (PvP is an open owner decision), so a Sneak-attack on a player stays a normal attack.
 * Also: classes load on join and save on quit, temporary effects end on death, and every hit dealt
 * or taken marks the player as in combat (the loadout lock).
 *
 * <p>Folia: every handler runs on the acting player's region thread; per-player press state is
 * only touched there (the map itself is concurrent).
 */
public final class AbilityListener implements Listener {

    /** Events from one key press arriving this close together count once. */
    static final long SAME_PRESS_MS = 150;
    /** A scroll sends one event per notch: one cast per gesture. */
    static final long SCROLL_GAP_MS = 250;
    /** A drop this soon after an inventory click (or close) came from the inventory screen, not the Q key in hand. */
    static final long INVENTORY_GRACE_MS = 100;

    private final ClassService service;
    private final AbilityCaster caster;

    /** When each active slot was last pressed and whether that cast went off. */
    private static final class Presses {
        final long[] at = new long[Loadout.ACTIVE_SLOTS];
        final boolean[] ok = new boolean[Loadout.ACTIVE_SLOTS];
        long inventoryAt;
    }

    private final Map<UUID, Presses> presses = new ConcurrentHashMap<>();

    public AbilityListener(ClassService service, AbilityCaster caster) {
        this.service = service;
        this.caster = caster;
    }

    // ── the keys ────────────────────────────────────────────────

    /** Right-click (active 1) and left-click (active 2) on air or a block. */
    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = false) // air clicks always arrive "cancelled"
    public void onInteract(PlayerInteractEvent e) {
        Action act = e.getAction();
        if (act == Action.PHYSICAL || e.useItemInHand() == Event.Result.DENY) return; // used by another handler (spell item, menu head)
        boolean right = act == Action.RIGHT_CLICK_AIR || act == Action.RIGHT_CLICK_BLOCK;
        Player p = e.getPlayer();
        if (e.getHand() == EquipmentSlot.OFF_HAND) {
            // The client follows a main-hand right-click with an off-hand one: part of the same press.
            if (right && samePress(p, 0, true)) { consume(e); return; }
            if (right) usedItem(p); // an off-hand item is used (thrown, eaten...): its arm swing is no left-click
            return;
        }
        WeaponFamily hand = castHand(p, p.getInventory().getItemInMainHand());
        Boolean cast = right && keepsRightClick(hand, act, e.getClickedBlock()) ? null : press(p, right ? 0 : 1, hand, SAME_PRESS_MS);
        if (cast != null && (right || cast)) { consume(e); return; }
        if (right) usedItem(p); // vanilla uses the item (a bow, a block, a door...): its arm swing is no left-click
    }

    /** Right-clicks that stay vanilla with this hand: a bow or crossbow shoots, an empty hand opens doors and chests. */
    private static boolean keepsRightClick(WeaponFamily hand, Action act, Block clicked) {
        if (hand == WeaponFamily.BOW || hand == WeaponFamily.CROSSBOW) return true;
        return act == Action.RIGHT_CLICK_BLOCK && hand == WeaponFamily.UNARMED && clicked != null && clicked.getType().isInteractable();
    }

    /**
     * A vanilla client swings its arm after using or dropping an item, and the server reports any
     * swing that points at nothing as a main-hand left-click, whichever arm swung. So right after
     * such a use, a left-click is not a cast (it would fire active 2 by accident).
     */
    private void usedItem(Player p) {
        if (p.isSneaking()) seen(p, 1);
    }

    /** Right-click on a mob (active 1): with an empty hand no air click follows, so it is caught here. */
    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onInteractEntity(PlayerInteractEntityEvent e) {
        Player p = e.getPlayer();
        if (!(e.getRightClicked() instanceof LivingEntity) || e.getRightClicked() instanceof ArmorStand) return;
        if (e.getHand() == EquipmentSlot.OFF_HAND) {
            if (samePress(p, 0, true)) e.setCancelled(true);
            return;
        }
        WeaponFamily hand = castHand(p, p.getInventory().getItemInMainHand());
        if (hand == WeaponFamily.BOW || hand == WeaponFamily.CROSSBOW) return;
        if (press(p, 0, hand, SAME_PRESS_MS) != null) e.setCancelled(true);
    }

    /** Left-click on an entity (active 2). A cast replaces the attack; a cast that doesn't go off leaves it a normal hit. */
    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onAttack(PrePlayerAttackEntityEvent e) {
        Player p = e.getPlayer();
        if (!e.willAttack()) return;
        if (e.getAttacked() instanceof Player) {
            seen(p, 1); // PvP stays as it was: a normal hit, and the swing that follows casts nothing
            return;
        }
        if (Boolean.TRUE.equals(press(p, 1, castHand(p, p.getInventory().getItemInMainHand()), SAME_PRESS_MS))) e.setCancelled(true);
    }

    /**
     * Spears (1.21.11) attack with their own stab action, not the attack packet above, so their hit
     * is caught as damage. Must run before the damage pipeline (registered before it): a cast
     * cancels the stab before it touches the mob's HP pool.
     */
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onSpearHit(EntityDamageByEntityEvent e) {
        if (AbilityCaster.inAbility() || e.getCause() != EntityDamageEvent.DamageCause.ENTITY_ATTACK) return;
        if (!(e.getDamager() instanceof Player p) || e.getEntity() instanceof Player || !(e.getEntity() instanceof LivingEntity)) return;
        ItemStack held = p.getInventory().getItemInMainHand();
        if (!held.getType().name().endsWith("_SPEAR")) return;
        if (Boolean.TRUE.equals(press(p, 1, castHand(p, held), SAME_PRESS_MS))) e.setCancelled(true);
    }

    /** F (active 3). */
    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onSwapHands(PlayerSwapHandItemsEvent e) {
        Player p = e.getPlayer();
        if (press(p, 2, castHand(p, p.getInventory().getItemInMainHand()), SAME_PRESS_MS) != null) e.setCancelled(true);
    }

    /**
     * Q (active 4). The item has already left the hand when this fires, so the dropped item is what
     * was held; cancelling puts it back. Drops from inventory screens are never casts.
     */
    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onDrop(PlayerDropItemEvent e) {
        Player p = e.getPlayer();
        usedItem(p); // the client swings its arm after a drop: that swing is no left-click
        Presses s = presses.get(p.getUniqueId());
        if (s != null && System.currentTimeMillis() - s.inventoryAt < INVENTORY_GRACE_MS) return;
        if (!noContainerOpen(p)) return;
        if (press(p, 3, castHand(p, e.getItemDrop().getItemStack()), SAME_PRESS_MS) != null) e.setCancelled(true);
    }

    /** Scroll or a number key (active 5). The held slot stays; one cast per scroll gesture. */
    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onHeldSlot(PlayerItemHeldEvent e) {
        Player p = e.getPlayer();
        if (press(p, 4, castHand(p, p.getInventory().getItem(e.getPreviousSlot())), SCROLL_GAP_MS) != null) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onInventoryClick(InventoryClickEvent e) {
        if (e.getWhoClicked() instanceof Player p) presses.computeIfAbsent(p.getUniqueId(), k -> new Presses()).inventoryAt = System.currentTimeMillis();
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onInventoryClose(InventoryCloseEvent e) {
        if (e.getPlayer() instanceof Player p) presses.computeIfAbsent(p.getUniqueId(), k -> new Presses()).inventoryAt = System.currentTimeMillis();
    }

    // ── press logic ─────────────────────────────────────────────

    /**
     * True when the player has no container or menu open: the view is their own inventory, which
     * reads as CRAFTING (or CREATIVE in creative mode).
     */
    public static boolean noContainerOpen(Player p) {
        InventoryType t = p.getOpenInventory().getType();
        return t == InventoryType.CRAFTING || t == InventoryType.CREATIVE;
    }

    /**
     * The family in hand when Sneak is held and casting is possible with it, else null (the key
     * stays vanilla). Tools, blocks, food and every other item never cast.
     */
    private static WeaponFamily castHand(Player p, ItemStack hand) {
        if (!p.isSneaking() || p.getGameMode() == GameMode.SPECTATOR) return null;
        WeaponFamily f = WeaponFamily.ofMaterial(hand == null ? null : hand.getType().name());
        return f == WeaponFamily.OTHER ? null : f;
    }

    /**
     * Press active slot {@code slot}: null when the key isn't a cast (vanilla keeps it), else
     * whether the cast went off. A repeat within {@code window} of the last press of that slot is
     * the same press and returns the first result without casting again.
     */
    private Boolean press(Player p, int slot, WeaponFamily hand, long window) {
        if (hand == null || caster.equippedActive(p, slot) == null) return null;
        Presses s = presses.computeIfAbsent(p.getUniqueId(), k -> new Presses());
        long now = System.currentTimeMillis();
        if (now - s.at[slot] < window) return s.ok[slot];
        s.at[slot] = now;
        s.ok[slot] = false; // a cast that throws still counts as this press
        s.ok[slot] = caster.cast(p, slot, hand);
        return s.ok[slot];
    }

    /** True when this event belongs to a press of {@code slot} that just happened (and went off, if {@code onlyCast}). */
    private boolean samePress(Player p, int slot, boolean onlyCast) {
        Presses s = presses.get(p.getUniqueId());
        return s != null && System.currentTimeMillis() - s.at[slot] < SAME_PRESS_MS && (!onlyCast || s.ok[slot]);
    }

    /** Record a press of {@code slot} that casts nothing (its follow-up events then cast nothing either). */
    private void seen(Player p, int slot) {
        Presses s = presses.computeIfAbsent(p.getUniqueId(), k -> new Presses());
        s.at[slot] = System.currentTimeMillis();
        s.ok[slot] = false;
    }

    private static void consume(PlayerInteractEvent e) {
        e.setUseItemInHand(Event.Result.DENY);
        e.setUseInteractedBlock(Event.Result.DENY);
    }

    // ── lifecycle and combat state ──────────────────────────────

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent e) { service.load(e.getPlayer()); }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent e) {
        service.unload(e.getPlayer());
        presses.remove(e.getPlayer().getUniqueId());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onDeath(PlayerDeathEvent e) { service.onDeath(e.getPlayer()); }

    /** A hit between a player and anything living (dealt or taken, arrows included) puts them in combat. */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onCombat(EntityDamageByEntityEvent e) {
        Entity source = e.getDamager() instanceof Projectile pr && pr.getShooter() instanceof Entity shooter ? shooter : e.getDamager();
        if (source == e.getEntity() || !(e.getEntity() instanceof LivingEntity)) return;
        if (source instanceof Player p) service.markCombat(p.getUniqueId());
        if (e.getEntity() instanceof Player p && source instanceof LivingEntity) service.markCombat(p.getUniqueId());
    }
}
