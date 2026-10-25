package Manager;

import Attributes.AttributeService;
import Attributes.AttributeSnapshot;
import Attributes.BowDraw;
import Attributes.Stat;
import Attributes.WeaponClass;
import io.papermc.paper.event.entity.EntityEquipmentChangedEvent;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.AbstractArrow;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityShootBowEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.EquipmentSlotGroup;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.UUID;

/**
 * Puts the speed and handling stats into the game (docs/design/ATTRIBUTE_EXPANSION.md):
 * attack speed and movement speed as temporary attribute modifiers (never saved with the
 * player), bow draw speed when an arrow is released, and the spell cooldown through
 * {@link #cooldownMs}. Everything runs on the player's own region thread, only when something
 * changes (snapshot rebuilt, main hand changed, PvP tag started or ended): no per-tick work.
 */
public final class SecondaryEffects implements Listener {

    public static final NamespacedKey ATTACK_SPEED_KEY = new NamespacedKey("cerberus", "attack_speed");
    public static final NamespacedKey MOVE_SPEED_KEY = new NamespacedKey("cerberus", "movement_speed");
    /** On arrows shot with extra draw speed: how much faster they flew (to undo it against players). */
    private final NamespacedKey drawScaleKey;
    /** The old Navigation skill's speed modifier, saved into player data by earlier versions. */
    private static final UUID LEGACY_NAVIGATION = UUID.fromString("a7e3c2d1-5f00-4b8a-9c11-0a1b2c3d4e5f");

    private final AttributeService attributes;
    private PvpTag pvp;
    /** Players with /cerb combatdebug on get a line per arrow (draw time, power before/after). */
    private java.util.function.Predicate<UUID> debug = id -> false;

    public SecondaryEffects(JavaPlugin plugin, AttributeService attributes) {
        this.attributes = attributes;
        this.drawScaleKey = new NamespacedKey(plugin, "draw_scale");
    }

    public void setPvpTag(PvpTag pvp) { this.pvp = pvp; }

    public void setDebug(java.util.function.Predicate<UUID> debug) { this.debug = debug; }

    public NamespacedKey drawScaleKey() { return drawScaleKey; }

    /** True when this player's bonuses are paused for PvP (tagged, and attributes.yml keeps PvP vanilla). */
    public boolean paused(UUID player) {
        return pvp != null && !attributes.rules().secondary().inPvp() && pvp.isTagged(player);
    }

    /** The bonus actually in effect for {@code stat} right now (0 while paused for PvP). */
    public double active(UUID player, Stat stat) {
        return paused(player) ? 0.0 : attributes.get(player).secondary(stat);
    }

    /** Set attack and movement speed for the player's snapshot and main hand. Player's thread only. */
    public void apply(Player p) {
        AttributeSnapshot s = attributes.get(p.getUniqueId());
        boolean off = !attributes.isTracked(p.getUniqueId()) || paused(p.getUniqueId());
        WeaponClass held = WeaponClass.ofMaterial(p.getInventory().getItemInMainHand().getType().name());
        set(p, Attribute.ATTACK_SPEED, ATTACK_SPEED_KEY, off ? 0.0 : s.attackSpeed(held));
        set(p, Attribute.MOVEMENT_SPEED, MOVE_SPEED_KEY, off ? 0.0 : s.secondary(Stat.MOVE_SPEED));
    }

    /** Replace our modifier only when the amount changed (each change sends the client an update). */
    private static void set(Player p, Attribute attribute, NamespacedKey key, double amount) {
        AttributeInstance inst = p.getAttribute(attribute);
        if (inst == null) return;
        AttributeModifier current = inst.getModifier(key);
        if (current != null && Math.abs(current.getAmount() - amount) < 1e-9) return;
        if (current != null) inst.removeModifier(key);
        if (amount != 0 && Double.isFinite(amount)) {
            // ×(1 + amount) on the final value, so it scales whatever the weapon's own speed is
            inst.addTransientModifier(new AttributeModifier(key, amount, AttributeModifier.Operation.MULTIPLY_SCALAR_1, EquipmentSlotGroup.ANY));
        }
    }

    /** Spell cooldown after recovery: base / (1 + recovery). */
    public long cooldownMs(UUID player, long baseMs) {
        double r = active(player, Stat.COOLDOWN_RECOVERY);
        return Math.max(50, Math.round(baseMs / (1.0 + Math.max(-0.5, r))));
    }

    // ── Events ───────────────────────────────────────────────────

    /** Attack speed depends on the weapon type in hand. */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onEquipmentChange(EntityEquipmentChangedEvent e) {
        if (e.getEntity() instanceof Player p && e.getEquipmentChanges().containsKey(EquipmentSlot.HAND)) apply(p);
    }

    /** Earlier versions saved a Navigation speed modifier into player data: remove it once. */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent e) {
        AttributeInstance move = e.getPlayer().getAttribute(Attribute.MOVEMENT_SPEED);
        if (move == null) return;
        for (AttributeModifier m : java.util.List.copyOf(move.getModifiers())) {
            if (m.getKey().getKey().equals(LEGACY_NAVIGATION.toString()) || "NavigationSkillSpeedBonus".equals(m.getName())) {
                move.removeModifier(m);
            }
        }
    }

    /**
     * Draw speed: a bow released early shoots as if drawn longer (speed, damage, a critical arrow at
     * full power). The pull animation is client-side and stays vanilla.
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onShoot(EntityShootBowEvent e) {
        if (!(e.getEntity() instanceof Player p) || !(e.getProjectile() instanceof AbstractArrow arrow)) return;
        if (e.getBow() == null || e.getBow().getType() != Material.BOW) return; // crossbows: not yet
        double bonus = active(p.getUniqueId(), Stat.DRAW_SPEED);
        double force = e.getForce() > 1.0001 ? e.getForce() / 3.0 : e.getForce(); // velocity or 0..1 power
        double boosted = BowDraw.boosted(force, bonus);
        if (debug.test(p.getUniqueId())) {
            p.sendMessage(String.format(java.util.Locale.ROOT, "[Draw] drew %.2f s: power %.2f -> %.2f (draw speed +%.1f%%%s)%s",
                    BowDraw.secondsFor(force), force, boosted, Math.max(0, bonus) * 100,
                    paused(p.getUniqueId()) ? ", paused: PvP" : "", boosted >= 1.0 ? " critical" : ""));
        }
        if (bonus <= 0 || !(force > 0) || boosted <= force + 1e-9) return;
        double scale = boosted / force;
        arrow.setVelocity(arrow.getVelocity().multiply(scale));
        if (boosted >= 1.0) arrow.setCritical(true);
        arrow.getPersistentDataContainer().set(drawScaleKey, PersistentDataType.DOUBLE, scale);
    }
}
