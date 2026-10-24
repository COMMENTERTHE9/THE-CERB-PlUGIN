package Manager;

import cerberus.world.cerb.CerberusPlugin;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.TextDisplay;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityRemoveEvent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * One always-visible floating {@link TextDisplay} label per scaled mob — replaces the
 * cull-prone vanilla nameplate (which hid when the player looked up/down, faded past
 * ~64 blocks, and was occluded by transparent blocks).
 *
 * <p>The display is mounted as a <b>passenger</b> of the mob (auto-follow, zero per-tick
 * cost) with {@link Display.Billboard#CENTER} so it always squarely faces the player.
 * The text updates live as the mob's virtual HP drains.
 *
 * <p>Folia-safe: attach/refresh/remove run only from handlers that fire on the mob's
 * owning region thread (spawn / damage / death / remove). The map is concurrent.
 */
public final class MobLabelManager implements Listener {

    private final CerberusPlugin plugin;
    private final NamespacedKey ownerKey; // marks a TextDisplay as ours (combat-ignore + sweeps)

    /** Cached at spawn so refresh never re-resolves the mob (resolve() fires events). */
    private static final class LabelState {
        final UUID labelId;
        final int level;
        final Tier tier;
        final String typeName;
        final double maxHp;
        LabelState(UUID labelId, int level, Tier tier, String typeName, double maxHp) {
            this.labelId = labelId; this.level = level; this.tier = tier;
            this.typeName = typeName; this.maxHp = maxHp;
        }
    }

    private final Map<UUID, LabelState> states = new ConcurrentHashMap<>(); // mob UUID -> state

    public MobLabelManager(CerberusPlugin plugin) {
        this.plugin = plugin;
        this.ownerKey = new NamespacedKey(plugin, "cerb_label_owner");
    }

    /** PDC key marking a TextDisplay as ours — lets combat/targeting code skip it. */
    public NamespacedKey ownerKey() { return ownerKey; }

    /** Spawn + mount the floating label. Call on the mob's region thread (CreatureSpawnEvent). */
    public void attach(LivingEntity mob, int level, Tier tier, double maxHp) {
        attach(mob, level, tier, maxHp, 1.0);
    }

    /** @param fraction the mob's current HP fraction [0,1] — lets a reloaded damaged mob show its real HP. */
    public void attach(LivingEntity mob, int level, Tier tier, double maxHp, double fraction) {
        if (mob == null) return;
        if (isRideable(mob)) return; // the label rides as a passenger and would take the seat
        if (states.containsKey(mob.getUniqueId())) return; // idempotent — never double-label

        // DEFER one tick onto the mob's region thread. During CreatureSpawnEvent the mob is NOT
        // yet committed to the world, so spawning the display + addPassenger there silently fail
        // (the mount never registers). By next tick the mob is live and tracked. (Folia EntityScheduler.)
        mob.getScheduler().run(plugin, task -> {
            if (!mob.isValid()) return;                         // died/despawned before next tick
            if (states.containsKey(mob.getUniqueId())) return;  // re-check after the tick gap

            World world = mob.getWorld();
            // Display name: a registry mob's custom name ("High Health Zombie") as-is, else the
            // prettified entity type ("Zombie"). Stored ready-to-show for refresh().
            final String customName = mob.getCustomName();
            final String typeName = (customName != null && !customName.isBlank())
                    ? customName : prettify(mob.getType().name());
            TextDisplay label = world.spawn(mob.getLocation(), TextDisplay.class, td -> {
                td.text(buildText(level, tier, typeName, maxHp, fraction));
                td.setBillboard(Display.Billboard.CENTER);          // ALWAYS faces the camera
                td.setSeeThrough(true);                             // no block-occlusion hide
                td.setShadowed(false);
                td.setDefaultBackground(false);
                td.setBackgroundColor(Color.fromARGB(64, 0, 0, 0)); // subtle translucent backdrop
                td.setTextOpacity((byte) 255);
                td.setAlignment(TextDisplay.TextAlignment.CENTER);
                td.setBrightness(new Display.Brightness(15, 15));   // full-bright at night/in caves
                td.setViewRange(1.0f);
                td.setTransformation(new Transformation(
                        new Vector3f(0f, 0.7f, 0f),                 // float above the head
                        new AxisAngle4f(0f, 0f, 0f, 1f),
                        new Vector3f(1f, 1f, 1f),
                        new AxisAngle4f(0f, 0f, 0f, 1f)));
                td.setPersistent(false);                            // never saved → anti-orphan/anti-dup
                td.setInvulnerable(true);
                td.getPersistentDataContainer().set(ownerKey, PersistentDataType.BYTE, (byte) 1);
            });

            if (!mob.addPassenger(label)) {  // mount failed (mob moved region / died this tick)
                label.remove();
                return;
            }
            states.put(mob.getUniqueId(), new LabelState(label.getUniqueId(), level, tier, typeName, maxHp));
        }, null); // retired=null: if the mob is gone before next tick, just skip (nothing spawned yet)
    }

    /**
     * Mobs a player can sit on. The label is mounted as a passenger, and these mobs accept only
     * one passenger, so a labelled horse/pig/strider/camel/happy ghast/nautilus could not be
     * ridden or tamed. They keep their scaled HP pool; they just get no floating label.
     */
    public static boolean isRideable(org.bukkit.entity.Entity e) {
        return e instanceof org.bukkit.entity.AbstractHorse   // horse, donkey, mule, llama, camel
                || e instanceof org.bukkit.entity.Steerable    // pig, strider
                || e instanceof org.bukkit.entity.HappyGhast
                || e instanceof org.bukkit.entity.AbstractNautilus
                || e instanceof org.bukkit.entity.CamelHusk;
    }

    /** Update the HP shown on the label. {@code fraction} = remaining/max in [0,1]. */
    public void refresh(LivingEntity mob, double fraction) {
        if (mob == null) return;
        LabelState st = states.get(mob.getUniqueId());
        if (st == null) return;
        TextDisplay td = findLabel(mob); // read off the mob's passengers — Folia-safe, no Bukkit.getEntity
        if (td != null && td.isValid()) {
            td.text(buildText(st.level, st.tier, st.typeName, st.maxHp, fraction));
        }
    }

    /** Find our floating-text passenger on the mob (Folia-safe; runs on the mob's region thread). */
    private TextDisplay findLabel(LivingEntity mob) {
        for (Entity p : mob.getPassengers()) {
            if (p instanceof TextDisplay td
                    && td.getPersistentDataContainer().has(ownerKey, PersistentDataType.BYTE)) {
                return td;
            }
        }
        return null;
    }

    /** Remove the label. Call on the mob's region thread. */
    public void remove(LivingEntity mob) {
        if (mob == null) return;
        states.remove(mob.getUniqueId());
        TextDisplay td = findLabel(mob); // passenger lookup — Folia-safe
        if (td != null) td.remove();
    }

    public void clear() { states.clear(); }

    /** Covers death, despawn, /kill, and chunk-unload — guarantees no orphaned displays. */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onEntityRemove(EntityRemoveEvent event) {
        if (event.getEntity() instanceof LivingEntity le && states.containsKey(le.getUniqueId())) {
            remove(le);
        }
    }

    // ── Text ──────────────────────────────────────────────────────

    private Component buildText(int level, Tier tier, String typeName, double maxHp, double fraction) {
        long shownHp = (long) Math.ceil(maxHp * Math.max(0.0, Math.min(1.0, fraction)));
        return Component.text("Lv" + level + " ", NamedTextColor.GRAY)
                .append(Component.text(typeName, tierColor(tier)))
                .append(Component.text("  " + compact(shownHp) + "❤", NamedTextColor.RED));
    }

    private TextColor tierColor(Tier tier) {
        if (tier != null && tier.hexColor != null) {
            TextColor c = TextColor.fromHexString(tier.hexColor);
            if (c != null) return c;
        }
        return NamedTextColor.WHITE;
    }

    private static String compact(double v) {
        if (v < 1000) return String.valueOf((long) v);
        String[] u = {"K", "M", "B", "T", "Qa"};
        int i = -1;
        double x = v;
        while (x >= 1000 && i < u.length - 1) { x /= 1000; i++; }
        return String.format("%.1f%s", x, u[i]);
    }

    private String prettify(String typeName) {
        String[] parts = typeName.toLowerCase().split("_");
        StringBuilder sb = new StringBuilder();
        for (String p : parts) {
            if (p.isEmpty()) continue;
            sb.append(Character.toUpperCase(p.charAt(0))).append(p.substring(1)).append(' ');
        }
        return sb.toString().trim();
    }
}
