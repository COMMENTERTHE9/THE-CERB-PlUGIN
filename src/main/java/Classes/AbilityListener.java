package Classes;

import io.papermc.paper.event.player.PrePlayerAttackEntityEvent;
import org.bukkit.GameMode;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.block.Block;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Enemy;
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
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The ability keys (docs/design/ABILITY_CONTROLS.md): raw key events become presses of a loadout's
 * active slots through the player's control scheme ({@link Controls}).
 * <ul>
 *   <li><b>Quick keys:</b> right-click = 1, F = 2, Q = 3, Sneak + right-click = 4, Sneak + F = 5.</li>
 *   <li><b>Click combos:</b> R R R / R L R / R L L / R R L = 1-4, F = 5; Q stays a drop.</li>
 * </ul>
 * With a bow or crossbow in hand, right and left swap: it shoots with right-click.
 *
 * <p><b>When keys are ability keys.</b> Only with a weapon, a focus or an empty hand, and only when
 * the loadout in use has an active ability. Then every press shows what happened (it went off, or
 * why not: {@link CastFeedback}), and F and Q never swap or drop (a weapon can still be dropped
 * from the inventory screen). Right-click stays vanilla when no right-click slot holds anything,
 * on a door, chest or other usable block, on a tame or friendly mob, with an axe on a block it
 * strips or scrapes, with a spear, trident or spell item whose abilities don't fit it (its charge,
 * throw or spell), and whenever the off hand's item has a use of its own there (a shield, food, a
 * torch on a block: {@link VanillaUse}). With a tool, a block, food or any other item in the main
 * hand every key is vanilla, so building and using items never change.
 *
 * <p><b>Sneak + key.</b> A vanilla client sends a click or F before the Sneak key's new state when
 * both change in the same tick, so a player pressing them together would read as "no Sneak" and
 * cast the wrong ability. Quick keys therefore decide one tick later, when the Sneak state has
 * arrived. Click combos use no Sneak.
 *
 * <p>One key press can reach the server as several events (an attack and its arm swing, the main
 * hand and then the off hand, a mob click and then an air click); events of one key that close
 * together are one press. An arm swing that follows a drop or an item use is not a left-click.
 * Abilities never hit players for now (PvP is off until the PvP pass, docs/ROADMAP.md): a
 * left-click on a player is always a plain hit and never part of a combo. Also: classes load on
 * join and save on quit, temporary effects end on death, and every hit dealt or taken marks the
 * player as in combat (the loadout lock).
 *
 * <p>Folia: every handler runs on the acting player's region thread; per-player state is only
 * touched there (the map itself is concurrent); the deferred decision runs on the player's
 * scheduler.
 */
public final class AbilityListener implements Listener {

    /** Events of one key this close together are one press. */
    static final long SAME_PRESS_MS = 150;
    /** For combo clicks: duplicate events of one click arrive together; real clicks are further apart. */
    static final long SAME_CLICK_MS = 40;
    /** A drop this soon after an inventory click (or close) came from the inventory screen, not the Q key in hand. */
    static final long INVENTORY_GRACE_MS = 100;
    /** After a drop or an item use, the arm swing the client sends within this time is not a left-click. */
    static final long SWING_AFTER_USE_MS = 150;

    private final Plugin plugin;
    private final ClassService service;
    private final AbilityCaster caster;
    private final CastFeedback feedback;
    private final AbilityBar bar;

    /** Per player, on their region thread. */
    private static final class State {
        long rightAt, leftAt, fAt, qAt;
        boolean rightUsed, leftUsed;
        long swingIgnoreUntil;
        long inventoryAt;
        /** When an inventory screen last closed, and the main hand then (the cursor item drops as it closes). */
        long closeAt;
        org.bukkit.Material handAtClose;
        int handAmountAtClose;
        final List<Controls.Click> combo = new ArrayList<>(Controls.COMBO_LENGTH);
        long comboAt;
        boolean comboBow;
    }

    private final Map<UUID, State> states = new ConcurrentHashMap<>();

    public AbilityListener(Plugin plugin, ClassService service, AbilityCaster caster, CastFeedback feedback, AbilityBar bar) {
        this.plugin = plugin;
        this.service = service;
        this.caster = caster;
        this.feedback = feedback;
        this.bar = bar;
        this.spellKey = new org.bukkit.NamespacedKey(plugin, "cerb_spell");
    }

    /** The tag on spell items (Listener.CustomItemListener reads the same one). */
    private final org.bukkit.NamespacedKey spellKey;

    private State state(Player p) { return states.computeIfAbsent(p.getUniqueId(), k -> new State()); }

    // ── the raw keys ────────────────────────────────────────────

    /** Right- and left-clicks on air or a block. */
    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = false) // air clicks always arrive "cancelled"
    public void onInteract(PlayerInteractEvent e) {
        Action act = e.getAction();
        if (act == Action.PHYSICAL || e.useItemInHand() == Event.Result.DENY) return; // used by another handler (the menu head)
        boolean right = act == Action.RIGHT_CLICK_AIR || act == Action.RIGHT_CLICK_BLOCK;
        Player p = e.getPlayer();
        State s = state(p);
        long now = System.currentTimeMillis();
        if (e.getHand() == EquipmentSlot.OFF_HAND) {
            // The client follows a main-hand right-click with an off-hand one: part of the same press.
            if (right && s.rightUsed && now - s.rightAt < SAME_PRESS_MS) { consume(e); return; }
            if (right) s.swingIgnoreUntil = now + SWING_AFTER_USE_MS; // an off-hand item is used: its swing is no left-click
            return;
        }
        boolean used = right ? right(p, e.getClickedBlock(), null) : left(p);
        if (used) consume(e);
        else if (right) s.swingIgnoreUntil = now + SWING_AFTER_USE_MS; // vanilla uses the item (a bow, a block, a door...)
    }

    /** Right-click on a mob (with an empty hand no air click follows, so it is caught here). */
    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onInteractEntity(PlayerInteractEntityEvent e) {
        if (!(e.getRightClicked() instanceof LivingEntity) || e.getRightClicked() instanceof ArmorStand) return;
        Player p = e.getPlayer();
        State s = state(p);
        if (e.getHand() == EquipmentSlot.OFF_HAND) {
            if (s.rightUsed && System.currentTimeMillis() - s.rightAt < SAME_PRESS_MS) e.setCancelled(true);
            return;
        }
        if (right(p, null, e.getRightClicked())) e.setCancelled(true);
    }

    /** Left-click on a mob: a key only in a combo (or with a bow); otherwise the normal attack. */
    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onAttack(PrePlayerAttackEntityEvent e) {
        if (!e.willAttack()) return;
        Player p = e.getPlayer();
        if (e.getAttacked() instanceof Player) {
            dropCombo(p, state(p)); // PvP stays as it was: a plain hit, never part of a combo
            return;
        }
        if (left(p)) e.setCancelled(true);
    }

    /**
     * Spears (1.21.11) attack with their own stab action, not the attack packet above, so their hit
     * is caught as damage. Must run before the damage pipeline (registered before it): a stab that
     * is a combo click is cancelled before it touches the mob's HP pool.
     */
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onSpearHit(EntityDamageByEntityEvent e) {
        if (AbilityCaster.inAbility() || e.getCause() != EntityDamageEvent.DamageCause.ENTITY_ATTACK) return;
        if (!(e.getDamager() instanceof Player p) || e.getEntity() instanceof Player || !(e.getEntity() instanceof LivingEntity)) return;
        if (!p.getInventory().getItemInMainHand().getType().name().endsWith("_SPEAR")) return;
        if (left(p)) e.setCancelled(true);
    }

    /** F. */
    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onSwapHands(PlayerSwapHandItemsEvent e) {
        if (keyF(e.getPlayer())) e.setCancelled(true);
    }

    /**
     * Q. The item has already left the hand when this fires, so the dropped item is what was held;
     * cancelling puts it back. Drops from inventory screens are never keys.
     */
    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onDrop(PlayerDropItemEvent e) {
        Player p = e.getPlayer();
        State s = state(p);
        long now = System.currentTimeMillis();
        s.swingIgnoreUntil = now + SWING_AFTER_USE_MS; // the client swings its arm after a drop: no left-click
        if (now - s.inventoryAt < INVENTORY_GRACE_MS || !noContainerOpen(p)) return; // dropped from an inventory screen
        // Just after a screen closed: the cursor item dropping leaves the hand as it was; a Q press has emptied it.
        if (now - s.closeAt < INVENTORY_GRACE_MS && handUnchangedSinceClose(p, s)) return;
        if (keyQ(p, family(e.getItemDrop().getItemStack()))) e.setCancelled(true);
    }

    /** Taking a weapon or focus in hand shows the ability bar for a moment (scroll and number keys are not ability keys). */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onHeldSlot(PlayerItemHeldEvent e) {
        Player p = e.getPlayer();
        dropCombo(p, state(p));
        p.getScheduler().run(plugin, t -> bar.touch(p), null); // after the slot has changed
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onInventoryClick(InventoryClickEvent e) {
        if (e.getWhoClicked() instanceof Player p) state(p).inventoryAt = System.currentTimeMillis();
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onInventoryClose(InventoryCloseEvent e) {
        if (!(e.getPlayer() instanceof Player p)) return;
        State s = state(p);
        ItemStack hand = p.getInventory().getItemInMainHand();
        s.closeAt = System.currentTimeMillis();
        s.handAtClose = hand.getType();
        s.handAmountAtClose = hand.getAmount();
    }

    private static boolean handUnchangedSinceClose(Player p, State s) {
        ItemStack hand = p.getInventory().getItemInMainHand();
        return hand.getType() == s.handAtClose && hand.getAmount() == s.handAmountAtClose;
    }

    // ── what a key means ────────────────────────────────────────

    /** A main-hand right-click (on air, {@code block} or {@code entity}). Returns true when it is an ability key (consume it). */
    private boolean right(Player p, Block block, Entity entity) {
        WeaponFamily hand = family(p.getInventory().getItemInMainHand());
        ClassProfile prof = armed(p, hand);
        if (prof == null) return false;
        State s = state(p);
        long now = System.currentTimeMillis();
        boolean bow = bow(hand);
        if (service.scheme(p.getUniqueId()) == Controls.Scheme.COMBO) {
            boolean live = comboLive(s, now);
            if (!live && (bow || keepsVanillaRight(p, prof, hand, block, entity, false))) return false;
            if (now - s.rightAt < SAME_CLICK_MS) return s.rightUsed;
            s.rightAt = now;
            s.rightUsed = true;
            comboClick(p, s, Controls.Click.RIGHT, bow, hand, now);
            return true;
        }
        if (bow || keepsVanillaRight(p, prof, hand, block, entity, true)) return false; // a bow shoots with right-click
        if (now - s.rightAt < SAME_PRESS_MS) return s.rightUsed;
        s.rightAt = now;
        s.rightUsed = true;
        decideNextTick(p, hand, true);
        return true;
    }

    /** A main-hand left-click (swing or attack on a mob). True when it is an ability key. */
    private boolean left(Player p) {
        WeaponFamily hand = family(p.getInventory().getItemInMainHand());
        ClassProfile prof = armed(p, hand);
        if (prof == null) return false;
        State s = state(p);
        long now = System.currentTimeMillis();
        if (now < s.swingIgnoreUntil) return false; // the arm swing after a drop or an item use
        boolean bow = bow(hand);
        if (service.scheme(p.getUniqueId()) == Controls.Scheme.COMBO) {
            boolean live = comboLive(s, now);
            if (!live && !(bow && hasAny(prof, 0, 1, 2, 3))) return false; // a normal attack
            if (now - s.leftAt < SAME_CLICK_MS) return s.leftUsed;
            s.leftAt = now;
            s.leftUsed = true;
            comboClick(p, s, Controls.Click.LEFT, bow, hand, now);
            return true;
        }
        if (!bow || !hasAny(prof, 0, 3)) return false; // only a bow's left-click is a quick key
        if (now - s.leftAt < SAME_PRESS_MS) return s.leftUsed;
        s.leftAt = now;
        s.leftUsed = true;
        decideNextTick(p, hand, true);
        return true;
    }

    /** F: always an ability key when armed (it never swaps a weapon away), except to take an off-hand item into an empty hand. */
    private boolean keyF(Player p) {
        WeaponFamily hand = family(p.getInventory().getItemInMainHand());
        ClassProfile prof = armed(p, hand);
        if (prof == null) return false;
        if (hand == WeaponFamily.UNARMED && !p.getInventory().getItemInOffHand().getType().isAir()) return false;
        State s = state(p);
        long now = System.currentTimeMillis();
        if (now - s.fAt < SAME_PRESS_MS) return true;
        s.fAt = now;
        if (service.scheme(p.getUniqueId()) == Controls.Scheme.COMBO) {
            dropCombo(p, s);
            castNow(p, Controls.COMBO_F, hand, Controls.Scheme.COMBO);
        } else {
            decideNextTick(p, hand, false);
        }
        return true;
    }

    /** Q (quick keys only): never drops a weapon or focus while armed; casts active 3. */
    private boolean keyQ(Player p, WeaponFamily dropped) {
        ClassProfile prof = armed(p, dropped);
        if (prof == null || service.scheme(p.getUniqueId()) != Controls.Scheme.QUICK) return false;
        State s = state(p);
        long now = System.currentTimeMillis();
        if (now - s.qAt < SAME_PRESS_MS) return true;
        s.qAt = now;
        // While the drop event runs the item is out of the hand (the server puts it back after the event).
        // Cast next tick, with the weapon back in hand, so its strikes and kills count for that weapon.
        p.getScheduler().run(plugin, t -> {
            if (p.isOnline()) castNow(p, Controls.QUICK_Q, family(p.getInventory().getItemInMainHand()), Controls.Scheme.QUICK);
        }, null);
        return true;
    }

    /**
     * Quick keys: the right-click (left with a bow) or F happened now; which slot it means depends
     * on Sneak, whose state may still be on its way (same tick), so decide next tick.
     */
    private void decideNextTick(Player p, WeaponFamily hand, boolean primary) {
        boolean sneakAtPress = p.isSneaking(); // a quick tap may let go of Sneak before the decision
        p.getScheduler().runDelayed(plugin, t -> {
            if (!p.isOnline()) return;
            boolean sneak = sneakAtPress || p.isSneaking();
            int slot = primary ? Controls.quickPrimary(sneak) : Controls.quickF(sneak);
            castNow(p, slot, hand, Controls.Scheme.QUICK);
        }, null, 1L);
    }

    private void castNow(Player p, int slot, WeaponFamily hand, Controls.Scheme scheme) {
        CastResult r = caster.cast(p, slot, hand);
        feedback.show(p, r, Controls.keyLabel(scheme, slot, bow(hand)));
        bar.touch(p);
    }

    // ── click combos ────────────────────────────────────────────

    private boolean comboLive(State s, long now) {
        return !s.combo.isEmpty() && now - s.comboAt <= service.catalog().controls().comboGapMs();
    }

    private void comboClick(Player p, State s, Controls.Click click, boolean bow, WeaponFamily hand, long now) {
        if (!comboLive(s, now) || s.comboBow != bow) s.combo.clear();
        if (s.combo.isEmpty()) {
            if (click != Controls.comboStart(bow)) return;
            s.comboBow = bow;
        }
        s.combo.add(click);
        s.comboAt = now;
        if (s.combo.size() < Controls.COMBO_LENGTH) {
            long gap = service.catalog().controls().comboGapMs();
            feedback.line(p, progress(p, s.combo, bow), gap + 100);
            p.playSound(p.getLocation(), Sound.UI_BUTTON_CLICK, SoundCategory.PLAYERS, 0.25f, 1.3f + 0.25f * s.combo.size());
            long at = now;
            p.getScheduler().runDelayed(plugin, t -> {
                if (s.comboAt == at && !s.combo.isEmpty()) { s.combo.clear(); feedback.clear(p); } // timed out
            }, null, gap / 50 + 2);
            bar.touch(p);
            return;
        }
        int slot = Controls.comboSlot(s.combo, bow);
        s.combo.clear();
        if (slot >= 0) castNow(p, slot, hand, Controls.Scheme.COMBO);
    }

    private void dropCombo(Player p, State s) {
        if (s.combo.isEmpty()) return;
        s.combo.clear();
        feedback.clear(p);
    }

    /** "R ▸ L ▸ ?   R Charge · L Execute": the clicks so far and what the next click casts. */
    private String progress(Player p, List<Controls.Click> clicks, boolean bow) {
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < Controls.COMBO_LENGTH; i++) {
            if (i > 0) b.append(" §8▸ ");
            b.append(i < clicks.size() ? "§e" + letter(clicks.get(i)) : "§7?");
        }
        if (clicks.size() == Controls.COMBO_LENGTH - 1) {
            b.append("  ");
            for (Controls.Click next : new Controls.Click[]{Controls.comboStart(bow), other(Controls.comboStart(bow))}) {
                List<Controls.Click> full = new ArrayList<>(clicks);
                full.add(next);
                AbilityDef a = caster.equippedActive(p, Controls.comboSlot(full, bow));
                b.append(" §7").append(letter(next)).append(" §f").append(a == null ? "-" : a.displayName());
            }
        }
        return b.toString();
    }

    private static String letter(Controls.Click c) { return c == Controls.Click.RIGHT ? "R" : "L"; }

    private static Controls.Click other(Controls.Click c) { return c == Controls.Click.RIGHT ? Controls.Click.LEFT : Controls.Click.RIGHT; }

    // ── helpers ─────────────────────────────────────────────────

    /**
     * The player's profile when keys with this hand are ability keys: a weapon, a focus or an empty
     * hand, and at least one active in the loadout in use. Else null (the key stays vanilla).
     */
    private ClassProfile armed(Player p, WeaponFamily hand) {
        if (hand == null || hand == WeaponFamily.OTHER || p.getGameMode() == GameMode.SPECTATOR) return null;
        ClassProfile prof = service.profile(p.getUniqueId());
        return prof != null && hasAny(prof, 0, 1, 2, 3, 4) ? prof : null;
    }

    /** True when one of these active slots holds an active ability. */
    private boolean hasAny(ClassProfile prof, int... slots) {
        List<String> actives = prof.activeLoadout().actives();
        for (int s : slots) {
            AbilityDef a = service.catalog().ability(actives.get(s));
            if (a != null && a.isActive()) return true;
        }
        return false;
    }

    /**
     * Right-clicks that stay vanilla: on a usable block (door, chest, lever...) or a mob that isn't
     * an enemy (mount, trade, feed), unless Sneaking (vanilla doesn't use them then either); with a
     * spear or trident when none of the right-click slots' abilities fit it (it charges or throws);
     * and when nothing is on right-click at all (quick keys: actives 1 and 4; combos: 1-4), so a
     * spell item with no ability on its key still casts its own spell.
     */
    private boolean keepsVanillaRight(Player p, ClassProfile prof, WeaponFamily hand, Block block, Entity entity, boolean quick) {
        boolean sneaking = p.isSneaking();
        // The off hand's own use wins (a shield blocks, food is eaten, a torch is placed), with or without Sneak.
        VanillaUse.OffHand off = VanillaUse.offHand(p.getInventory().getItemInOffHand().getType());
        if (off == VanillaUse.OffHand.ALWAYS || (off == VanillaUse.OffHand.ON_BLOCK && block != null)) return true;
        if (!sneaking && block != null && block.getType().isInteractable()) return true;
        if (!sneaking && block != null && hand == WeaponFamily.AXE && VanillaUse.axeWorks(block.getType())) return true; // strip, scrape, unwax
        if (!sneaking && entity != null && !(entity instanceof Enemy)) return true;
        int[] slots = quick ? new int[]{0, 3} : new int[]{0, 1, 2, 3};
        if (!hasAny(prof, slots)) return true;
        // An item with a right-click of its own (a spear's charge or throw, a spell item's spell) keeps it
        // unless an ability on the key fits that item.
        if (hand == WeaponFamily.POLEARM || spellItem(p.getInventory().getItemInMainHand())) {
            List<String> actives = prof.activeLoadout().actives();
            for (int s : slots) {
                AbilityDef a = service.catalog().ability(actives.get(s));
                if (a != null && a.isActive() && WeaponFamily.fits(a.weapons(), hand)) return false;
            }
            return true;
        }
        return false;
    }

    private static boolean bow(WeaponFamily f) { return f == WeaponFamily.BOW || f == WeaponFamily.CROSSBOW; }

    /** A spell item (the Fireball Wand), tagged with the spell it casts. */
    private boolean spellItem(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return false;
        return item.getItemMeta().getPersistentDataContainer().has(spellKey, org.bukkit.persistence.PersistentDataType.STRING);
    }

    private static WeaponFamily family(ItemStack item) {
        return WeaponFamily.ofMaterial(item == null ? null : item.getType().name());
    }

    /**
     * True when the player has no container or menu open: the view is their own inventory, which
     * reads as CRAFTING (or CREATIVE in creative mode).
     */
    public static boolean noContainerOpen(Player p) {
        InventoryType t = p.getOpenInventory().getType();
        return t == InventoryType.CRAFTING || t == InventoryType.CREATIVE;
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
        Player p = e.getPlayer();
        service.unload(p);
        states.remove(p.getUniqueId());
        feedback.forget(p.getUniqueId());
        bar.forget(p);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onDeath(PlayerDeathEvent e) {
        Player p = e.getPlayer();
        service.onDeath(p);
        State s = states.get(p.getUniqueId());
        if (s != null) s.combo.clear();
        bar.refresh(p);
    }

    /**
     * A hit between a player and anything living (dealt or taken, arrows included) puts them in
     * combat, and remembers the enemy's level (a self cast in this fight earns class XP for it).
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onCombat(EntityDamageByEntityEvent e) {
        Entity source = e.getDamager() instanceof Projectile pr && pr.getShooter() instanceof Entity shooter ? shooter : e.getDamager();
        if (source == e.getEntity() || !(e.getEntity() instanceof LivingEntity target)) return;
        if (source instanceof Player p) { service.markCombat(p.getUniqueId(), foe(target)); bar.touchIfShown(p); }
        if (target instanceof Player p && source instanceof LivingEntity src) { service.markCombat(p.getUniqueId(), foe(src)); bar.touchIfShown(p); }
    }

    /** An enemy as a foe for class XP (its level and cast-XP budget), null for anything else (or a mob this region doesn't own). */
    private ClassService.Foe foe(LivingEntity e) {
        return e instanceof Enemy && !(e instanceof Player) && org.bukkit.Bukkit.isOwnedByCurrentRegion(e) ? service.foe(e) : null;
    }

    /** A mob died: its cast-XP entry ends. */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onMobDeath(org.bukkit.event.entity.EntityDeathEvent e) {
        if (!(e.getEntity() instanceof Player)) service.forgetMob(e.getEntity().getUniqueId());
    }
}
