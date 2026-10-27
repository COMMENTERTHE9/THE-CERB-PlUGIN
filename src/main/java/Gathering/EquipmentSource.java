package Gathering;

import Attributes.Attribute;
import Attributes.AttributeRules;
import Attributes.AttributeService;
import Attributes.AttributeSource;
import Attributes.Contribution;
import Attributes.WeaponClass;
import Attributes.Yield;
import io.papermc.paper.event.entity.EntityEquipmentChangedEvent;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Forged gear in the attribute engine (docs/design/GATHERING.md, "Equipment in the attribute
 * engine"): the quality of the item in hand and of each armor piece worn becomes Power, Fortune
 * and Fortitude contributions, so it goes through the engine's caps and the PvP rule like any
 * other source. A tool's Fortune counts on what it gathers (an axe's on wood, a pickaxe's on ore).
 * Upgrades on the item in hand (heartwood: docs/design/WOODCUTTING.md §8) add their speed stat; a rod's upgrades
 * are counted by {@link Tackle} with the player's fishing setup, each once (docs/design/FISHING_EQUIPMENT.md §6). It
 * never adds item modifiers (those would enter a hit before Power, outside the caps).
 *
 * <p>What is worn is read on the player's thread when their equipment changes, reduced to a
 * signature (the qualities, nothing else), and the snapshot is rebuilt only when the signature
 * changes: scrolling between plain items, or a pickaxe losing durability, rebuilds nothing.
 */
public final class EquipmentSource implements AttributeSource, Listener {

    public static final String SOURCE_PREFIX = "gear:";

    /**
     * One item that adds something: what kind of gear, the weapon type it hits as, its quality (null: none), what its
     * Fortune counts on (null: everything), its item kind for upgrades (GearItems.kind: axe, bow...), its upgrades, a label.
     */
    public record Piece(GatheringCatalog.Gear gear, WeaponClass weapon, Quality quality, Yield yield, String kind, List<String> upgrades,
                        String label) {
        public Piece {
            upgrades = upgrades == null ? List.of() : List.copyOf(upgrades);
        }

        public Piece(GatheringCatalog.Gear gear, WeaponClass weapon, Quality quality, String label) {
            this(gear, weapon, quality, null, null, List.of(), label);
        }
    }

    /** What a player has on, as far as qualities go. {@code hand} is null when the item in hand adds nothing. */
    public record Worn(Piece hand, List<Piece> armor) {
        public static final Worn NOTHING = new Worn(null, List.of());

        public Worn {
            armor = List.copyOf(armor);
        }
    }

    private final AttributeService attributes;
    private final Supplier<GatheringCatalog> catalog;
    /** Called after a change (player's thread): re-apply health, defense and speed. */
    private final Consumer<Player> changed;
    private final Map<UUID, Worn> worn = new ConcurrentHashMap<>();

    public EquipmentSource(AttributeService attributes, Supplier<GatheringCatalog> catalog, Consumer<Player> changed) {
        this.attributes = attributes;
        this.catalog = catalog;
        this.changed = changed;
    }

    @Override public String id() { return "gear"; }

    @Override
    public List<Contribution> contributions(UUID player, AttributeRules rules) {
        Worn w = worn.get(player);
        GatheringCatalog c = catalog.get();
        return w == null ? List.of() : contributions(w, c.quality(), c.upgrades());
    }

    /** Pure: what {@code w} adds under {@code q}'s numbers (Common adds nothing), without upgrades. */
    public static List<Contribution> contributions(Worn w, GatheringCatalog.QualityRules q) {
        return contributions(w, q, Map.of());
    }

    /** Pure: what {@code w} adds under {@code q}'s numbers and these upgrades (one the item no longer qualifies for adds nothing). */
    public static List<Contribution> contributions(Worn w, GatheringCatalog.QualityRules q, Map<String, GatheringCatalog.UpgradeDef> upgrades) {
        List<Contribution> out = new ArrayList<>();
        Piece h = w.hand();
        if (h != null && h.quality() != null) {
            int i = h.quality().ordinal();
            if (h.gear() == GatheringCatalog.Gear.WEAPON || h.gear() == GatheringCatalog.Gear.WEAPON_AND_TOOL) {
                add(out, "hand", h.label(), Attribute.POWER, at(q.weaponPower(), i), h.weapon());
            }
            if (h.gear() == GatheringCatalog.Gear.TOOL || h.gear() == GatheringCatalog.Gear.WEAPON_AND_TOOL) {
                double amount = at(q.toolFortune(), i);
                if (amount > 0 && Double.isFinite(amount)) out.add(Contribution.fortune(SOURCE_PREFIX + "hand", h.label(), amount, h.yield()));
            }
        }
        if (h != null && !"rod".equals(h.kind())) for (String id : h.upgrades()) { // a rod's: Tackle counts them, once with the setup
            GatheringCatalog.UpgradeDef u = upgrades.get(id);
            if (u == null || u.stat() == null || !(u.amount() > 0) || !u.on().contains(h.kind())) continue;
            out.add(Contribution.stat(SOURCE_PREFIX + "hand", h.label() + " · " + u.name(), u.stat(), u.amount(), null));
        }
        for (Piece a : w.armor()) {
            if (a.quality() != null && a.gear() == GatheringCatalog.Gear.ARMOR) {
                add(out, "armor", a.label(), Attribute.FORTITUDE, at(q.armorFortitude(), a.quality().ordinal()), null);
            }
        }
        return out;
    }

    private static void add(List<Contribution> out, String slot, String label, Attribute a, double amount, WeaponClass weapon) {
        if (amount > 0 && Double.isFinite(amount)) out.add(Contribution.attribute(SOURCE_PREFIX + slot, label, a, amount, weapon));
    }

    private static double at(double[] values, int i) {
        return i >= 0 && i < values.length ? values[i] : 0;
    }

    /** Read what {@code p} has on and rebuild their attributes if a quality changed. Player's thread. */
    public void capture(Player p) {
        capture(p, p.getInventory().getItemInMainHand());
    }

    /**
     * The same, with {@code mainHand} as the item in hand: a hotbar scroll or a hand swap is read when
     * it happens, so a hit or a break in that same tick already uses the new item's quality.
     */
    public void capture(Player p, ItemStack mainHand) {
        if (!p.isConnected()) return; // leaving: a last equipment tick must not bring the entry back
        PlayerInventory inv = p.getInventory();
        Piece hand = piece(mainHand, false);
        List<Piece> armor = new ArrayList<>(4);
        for (ItemStack it : inv.getArmorContents()) {
            Piece a = piece(it, true);
            if (a != null) armor.add(a);
        }
        Worn now = hand == null && armor.isEmpty() ? Worn.NOTHING : new Worn(hand, armor);
        Worn before = worn.put(p.getUniqueId(), now);
        if (before == null ? now != Worn.NOTHING : !before.equals(now)) {
            attributes.markDirty(p.getUniqueId());
            changed.accept(p);
        }
    }

    /**
     * The piece {@code it} is, or null when it adds nothing: no quality above Common and no upgrade, not gear, or armor
     * held in hand.
     */
    static Piece piece(ItemStack it, boolean worn) {
        if (it == null || it.getType().isAir()) return null;
        String material = it.getType().name();
        GatheringCatalog.Gear g = GatheringCatalog.gear(material);
        Quality q = GearItems.quality(it);
        if (q == Quality.COMMON || g == null) q = null;
        List<String> ups = worn ? List.of() : GearItems.upgrades(it);
        if (q == null && ups.isEmpty()) return null;
        if ((g == GatheringCatalog.Gear.ARMOR) != worn) return null;
        String kind = GearItems.kind(it.getType());
        Yield y = "axe".equals(kind) ? Yield.WOOD : "pickaxe".equals(kind) ? Yield.ORE : null;
        String label = GearItems.label(material);
        return new Piece(g, WeaponClass.ofMaterial(material), q, y, kind, ups, q == null ? label : q.displayName + " " + label.toLowerCase(java.util.Locale.ROOT));
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onEquipment(EntityEquipmentChangedEvent e) {
        if (e.getEntity() instanceof Player p) capture(p);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent e) {
        capture(e.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onHeld(PlayerItemHeldEvent e) {
        capture(e.getPlayer(), e.getPlayer().getInventory().getItem(e.getNewSlot()));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onSwapHands(PlayerSwapHandItemsEvent e) {
        capture(e.getPlayer(), e.getMainHandItem());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent e) {
        worn.remove(e.getPlayer().getUniqueId());
    }

    /** What {@code player} has on now (for the inspector and tests). */
    public Worn worn(UUID player) {
        return worn.getOrDefault(player, Worn.NOTHING);
    }
}
