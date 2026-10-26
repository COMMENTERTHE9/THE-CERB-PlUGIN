package Manager;

import cerberus.world.cerb.CerberusPlugin;
import com.destroystokyo.paper.entity.ai.Goal;
import com.destroystokyo.paper.entity.ai.VanillaGoal;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.AbstractHorse;
import org.bukkit.entity.Animals;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDismountEvent;
import org.bukkit.event.entity.EntityEnterLoveModeEvent;
import org.bukkit.event.entity.EntityRemoveEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
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
 * <p>A mob a player can ride has one seat, which the label would take: its label steps aside
 * when a player right-clicks the mob and comes back once nobody rides it.
 *
 * <p>A label is only ever a passenger, so it never outlives its mob: one taken off a mob for any
 * reason is removed at once ({@link #onLabelDismount}), and the mob gets a new one a little later.
 * Untamed horses, donkeys, mules and llamas treat any passenger as a rider being tamed: they run
 * around and buck it off, which used to leave the label floating where it fell. While a label
 * rides one, that goal is paused; it is back whenever a player could mount (the label steps aside),
 * so taming works as in vanilla. A horse in love keeps its label off, because vanilla won't breed
 * a horse that carries anything.
 *
 * <p>Folia-safe: attach/refresh/remove run only from handlers that fire on the mob's
 * owning region thread (spawn / damage / death / remove / interact / dismount). The map is
 * concurrent.
 */
public final class MobLabelManager implements Listener {

    private final CerberusPlugin plugin;
    private final NamespacedKey ownerKey; // marks a TextDisplay as ours (combat-ignore + sweeps)
    private final NamespacedKey mobKey;   // the mob a label was made for (diagnostics)

    /** Cached at spawn so refresh never re-resolves the mob (resolve() fires events). */
    private static final class LabelState {
        final int level;
        final Tier tier;
        final String typeName;
        final double maxHp;
        /** "Lv50 Zombie": never changes, built once. */
        final Component prefix;
        /**
         * The label entity (found again from the mob's passengers if this one is gone); null while
         * it steps aside for a rider. Mob's thread only.
         */
        TextDisplay label;
        /** The HP part last shown, so a hit that doesn't change the text sends nothing. */
        String shownHp;
        /** An untamed horse's "run around like crazy" goal, paused while the label rides it. */
        Goal<AbstractHorse> crazy;
        /** A restore is already scheduled (after a label was taken off). */
        boolean restoring;
        LabelState(int level, Tier tier, String typeName, double maxHp, Component prefix) {
            this.level = level; this.tier = tier;
            this.typeName = typeName; this.maxHp = maxHp;
            this.prefix = prefix;
        }
    }

    private final Map<UUID, LabelState> states = new ConcurrentHashMap<>(); // mob UUID -> state

    public MobLabelManager(CerberusPlugin plugin) {
        this.plugin = plugin;
        this.ownerKey = new NamespacedKey(plugin, "cerb_label_owner");
        this.mobKey = new NamespacedKey(plugin, "cerb_label_mob");
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
        if (states.containsKey(mob.getUniqueId())) return; // idempotent — never double-label

        // DEFER one tick onto the mob's region thread. During CreatureSpawnEvent the mob is NOT
        // yet committed to the world, so spawning the display + addPassenger there silently fail
        // (the mount never registers). By next tick the mob is live and tracked. (Folia EntityScheduler.)
        mob.getScheduler().run(plugin, task -> {
            if (!mob.isValid()) return;                         // died/despawned before next tick
            if (states.containsKey(mob.getUniqueId())) return;  // re-check after the tick gap

            // Display name: a registry mob's custom name ("High Health Zombie") as-is, else the
            // prettified entity type ("Zombie"). Stored ready-to-show for refresh().
            final String customName = mob.getCustomName();
            final String typeName = (customName != null && !customName.isBlank())
                    ? customName : prettify(mob.getType().name());
            LabelState st = new LabelState(level, tier, typeName, maxHp, prefix(level, tier, typeName));
            if (hasRider(mob)) { // a player is on it: the label comes back when they get off
                states.put(mob.getUniqueId(), st);
                return;
            }
            if (mount(mob, st, fraction)) states.put(mob.getUniqueId(), st);
        }, null); // retired=null: if the mob is gone before next tick, just skip (nothing spawned yet)
    }

    /** Spawn {@code st}'s label and mount it on {@code mob}; false when the mount failed. Mob's thread. */
    private boolean mount(LivingEntity mob, LabelState st, double fraction) {
        final String hp = hpText(st.maxHp, fraction);
        TextDisplay label = mob.getWorld().spawn(mob.getLocation(), TextDisplay.class, td -> {
            td.text(st.prefix.append(Component.text(hp, NamedTextColor.RED)));
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
            td.getPersistentDataContainer().set(mobKey, PersistentDataType.STRING, mob.getUniqueId().toString());
        });

        if (!mob.addPassenger(label)) {  // mount failed (mob moved region / died this tick)
            label.remove();
            return false;
        }
        st.label = label;
        st.shownHp = hp;
        calm(mob, st);
        return true;
    }

    /**
     * An untamed horse-like mob treats the label as a rider: its "run around like crazy" goal makes it
     * gallop about and buck the label off. Pause that goal while the label rides. Mob's thread.
     */
    private static void calm(LivingEntity mob, LabelState st) {
        if (!(mob instanceof AbstractHorse h) || h.isTamed()) return;
        var goals = Bukkit.getMobGoals();
        Goal<AbstractHorse> g = goals.getGoal(h, VanillaGoal.HORSE_RUN_AROUND_LIKE_CRAZY);
        if (g == null) return;
        st.crazy = g;
        goals.removeGoal(h, VanillaGoal.HORSE_RUN_AROUND_LIKE_CRAZY);
    }

    /** Put the paused goal back (a player may mount and must be able to tame it). Mob's thread. */
    private static void uncalm(LivingEntity mob, LabelState st) {
        if (st.crazy == null || !(mob instanceof AbstractHorse h)) return;
        var goals = Bukkit.getMobGoals();
        if (!goals.hasGoal(h, VanillaGoal.HORSE_RUN_AROUND_LIKE_CRAZY)) goals.addGoal(h, 1, st.crazy); // vanilla's priority
        st.crazy = null;
    }

    /** Take the label off for now (a rider, love mode); {@link #restore} brings it back. Mob's thread. */
    private void stepAside(LivingEntity mob, LabelState st, long restoreTicks) {
        TextDisplay td = findLabel(mob);
        st.label = null; // first: the dismount below is ours (see onLabelDismount)
        if (td != null) {
            td.leaveVehicle(); // the seat is free now, not when the removal is processed
            td.remove();
        }
        uncalm(mob, st);
        scheduleRestore(mob, st, restoreTicks);
    }

    private void scheduleRestore(LivingEntity mob, LabelState st, long ticks) {
        if (st.restoring) return;
        st.restoring = true;
        mob.getScheduler().runDelayed(plugin, t -> { st.restoring = false; restore(mob); }, () -> st.restoring = false, Math.max(1, ticks));
    }

    /**
     * Mobs a player can sit on. The label is mounted as a passenger, and these mobs have one seat
     * (a camel two), so the label steps aside for a rider ({@link #onRiderInteract}).
     */
    public static boolean isRideable(org.bukkit.entity.Entity e) {
        return e instanceof org.bukkit.entity.AbstractHorse   // horse, donkey, mule, llama, camel
                || e instanceof org.bukkit.entity.Steerable    // pig, strider
                || e instanceof org.bukkit.entity.HappyGhast
                || e instanceof org.bukkit.entity.AbstractNautilus
                || e instanceof org.bukkit.entity.CamelHusk;
    }

    /**
     * A rideable mob's label takes its seat, so it steps aside when a player right-clicks the mob,
     * before vanilla mounts them (it checks the seat first). It comes back at once if nobody got
     * on (they fed or leashed it), else when the rider gets off ({@link #onDismount}). Clicks that
     * can't seat anyone leave it alone: Sneak (a horse's inventory), a spectator, a baby, an
     * unsaddled pig or strider.
     */
    @EventHandler(priority = EventPriority.LOWEST)
    public void onRiderInteract(PlayerInteractEntityEvent event) {
        if (!(event.getRightClicked() instanceof LivingEntity mob) || !isRideable(mob)) return;
        LabelState st = states.get(mob.getUniqueId());
        if (st == null || !canSeat(event.getPlayer(), mob)) return;
        if (findLabel(mob) == null) return; // already aside (the off-hand half of the same click): its return is on the way
        stepAside(mob, st, 2L);
    }

    /** Vanilla won't breed a horse that carries anything: a horse in love keeps its label off until love ends. */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onLove(EntityEnterLoveModeEvent event) {
        if (!(event.getEntity() instanceof AbstractHorse mob)) return;
        LabelState st = states.get(mob.getUniqueId());
        if (st != null && findLabel(mob) != null) stepAside(mob, st, 40L);
    }

    /**
     * A label taken off its mob by anything but our own removal (vanilla AI, a conversion, another
     * plugin) is removed at once, so it can never be left floating; the mob gets a new one later.
     * Runs on the mob's thread (the dismount happens there).
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onLabelDismount(EntityDismountEvent event) {
        if (!(event.getEntity() instanceof TextDisplay td) || !td.getPersistentDataContainer().has(ownerKey, PersistentDataType.BYTE)) return;
        if (!td.isValid()) return; // being removed by us
        td.remove();
        if (!(event.getDismounted() instanceof LivingEntity mob) || !mob.isValid()) return;
        LabelState st = states.get(mob.getUniqueId());
        if (st == null || st.label != td) return; // a label stepping aside: its own restore is on the way
        st.label = null;
        uncalm(mob, st);
        scheduleRestore(mob, st, 100L); // a little later, so something that keeps ejecting can't churn labels
    }

    /** True when this right-click can seat {@code p} on {@code mob}, as far as the label needs to know. */
    private static boolean canSeat(Player p, LivingEntity mob) {
        if (p.isSneaking() || p.getGameMode() == org.bukkit.GameMode.SPECTATOR) return false;
        if (mob instanceof org.bukkit.entity.Ageable a && !a.isAdult()) return false;
        return !(mob instanceof org.bukkit.entity.Steerable s) || s.hasSaddle();
    }

    /** A player got off a labelled mob: its label comes back. */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onDismount(EntityDismountEvent event) {
        if (!(event.getEntity() instanceof Player) || !(event.getDismounted() instanceof LivingEntity mob)) return;
        if (!states.containsKey(mob.getUniqueId())) return;
        mob.getScheduler().runDelayed(plugin, t -> restore(mob), null, 1L);
    }

    /** Put a stepped-aside label back, unless a player rides the mob or it has one. Mob's thread. */
    private void restore(LivingEntity mob) {
        LabelState st = states.get(mob.getUniqueId());
        if (st == null || !mob.isValid() || hasRider(mob) || findLabel(mob) != null) return;
        if (mob instanceof AbstractHorse && mob instanceof Animals a && a.isLoveMode()) { // breeding needs it bare
            scheduleRestore(mob, st, 40L);
            return;
        }
        MobVirtualHealthManager pools = plugin.getMobVirtualHealthManager();
        mount(mob, st, pools != null && pools.has(mob) ? pools.fraction(mob) : 1.0);
    }

    /** True when a player rides {@code mob} (mobs riding it, a skeleton on its horse, share the label fine). */
    private static boolean hasRider(Entity mob) {
        for (Entity p : mob.getPassengers()) if (p instanceof Player) return true;
        return false;
    }

    /** Update the HP shown on the label. {@code fraction} = remaining/max in [0,1]. */
    public void refresh(LivingEntity mob, double fraction) {
        if (mob == null) return;
        LabelState st = states.get(mob.getUniqueId());
        if (st == null) return;
        String hp = hpText(st.maxHp, fraction);
        if (hp.equals(st.shownHp)) return;                 // e.g. "3.2K" before and after the hit
        TextDisplay td = st.label;
        if (td == null || !td.isValid() || td.getVehicle() != mob) {
            td = findLabel(mob); // read off the mob's passengers — Folia-safe, no Bukkit.getEntity
            st.label = td;
        }
        if (td != null && td.isValid()) {
            td.text(st.prefix.append(Component.text(hp, NamedTextColor.RED)));
            st.shownHp = hp;
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

    /** Remove the label (every one riding the mob, and the one it had if that is still about). Mob's thread. */
    public void remove(LivingEntity mob) {
        if (mob == null) return;
        LabelState st = states.remove(mob.getUniqueId());
        for (Entity p : mob.getPassengers()) { // passenger lookup — Folia-safe
            if (p instanceof TextDisplay td && td.getPersistentDataContainer().has(ownerKey, PersistentDataType.BYTE)) td.remove();
        }
        TextDisplay kept = st == null ? null : st.label;
        if (kept != null && kept.isValid() && kept.getVehicle() != mob) {
            if (Bukkit.isOwnedByCurrentRegion(kept)) kept.remove();
            else kept.getScheduler().run(plugin, t -> kept.remove(), null);
        }
    }

    public void clear() { states.clear(); }

    /**
     * Diagnostics: our labels within {@code r} blocks of {@code at} that this region owns, as
     * {total, on a live mob, orphans}, and a line per orphan (at most {@code max}). At's thread.
     */
    public java.util.List<String> census(Player at, double r, int max) {
        return census(at, r, max, false);
    }

    /** As {@link #census(Player, double, int)}; {@code clean} also removes the orphans found. */
    public java.util.List<String> census(Player at, double r, int max, boolean clean) {
        int total = 0, live = 0, removed = 0;
        java.util.List<String> orphans = new java.util.ArrayList<>();
        for (Entity e : at.getNearbyEntities(r, r, r)) {
            if (!(e instanceof TextDisplay td) || !Bukkit.isOwnedByCurrentRegion(e)
                    || !td.getPersistentDataContainer().has(ownerKey, PersistentDataType.BYTE)) continue;
            total++;
            Entity v = td.getVehicle();
            if (v instanceof LivingEntity le && le.isValid()) { live++; continue; }
            if (clean) { td.remove(); removed++; }
            if (orphans.size() < max) {
                String mob = td.getPersistentDataContainer().get(mobKey, PersistentDataType.STRING);
                orphans.add(String.format(java.util.Locale.ROOT, "orphan at %d %d %d, %d ticks old, vehicle %s, mob %s (%s)",
                        td.getLocation().getBlockX(), td.getLocation().getBlockY(), td.getLocation().getBlockZ(), td.getTicksLived(),
                        v == null ? "none" : v.getType().name().toLowerCase(java.util.Locale.ROOT) + (v.isValid() ? "" : " (gone)"),
                        mob == null ? "?" : mob.substring(0, 8), mob != null && states.containsKey(UUID.fromString(mob)) ? "tracked" : "not tracked"));
            }
        }
        int horses = 0, untamed = 0, paused = 0;
        for (Entity e : at.getNearbyEntities(r, r, r)) {
            if (!(e instanceof AbstractHorse h) || !Bukkit.isOwnedByCurrentRegion(e)) continue;
            horses++;
            if (h.isTamed()) continue;
            untamed++;
            if (!Bukkit.getMobGoals().hasGoal(h, VanillaGoal.HORSE_RUN_AROUND_LIKE_CRAZY)) paused++;
        }
        java.util.List<String> out = new java.util.ArrayList<>();
        out.add("labels " + total + ", on a live mob " + live + ", orphans " + (total - live) + (clean ? ", removed " + removed : "")
                + "; horse-likes " + horses + ", untamed " + untamed + ", bucking paused " + paused);
        out.addAll(orphans);
        return out;
    }

    /** Covers death, despawn, /kill, and chunk-unload — guarantees no orphaned displays. */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onEntityRemove(EntityRemoveEvent event) {
        if (event.getEntity() instanceof LivingEntity le && states.containsKey(le.getUniqueId())) {
            remove(le);
        }
    }

    // ── Text ──────────────────────────────────────────────────────

    /** "Lv50 Zombie" in gray and the tier's color: the part of a label that never changes. */
    private Component prefix(int level, Tier tier, String typeName) {
        return Component.text("Lv" + level + " ", NamedTextColor.GRAY)
                .append(Component.text(typeName, tierColor(tier)));
    }

    /** "  3.2K❤": the live HP part. */
    private static String hpText(double maxHp, double fraction) {
        long shownHp = (long) Math.ceil(maxHp * Math.max(0.0, Math.min(1.0, fraction)));
        return "  " + compact(shownHp) + "❤";
    }

    private TextColor tierColor(Tier tier) {
        if (tier != null && tier.hexColor != null) {
            TextColor c = TextColor.fromHexString(tier.hexColor);
            if (c != null) return c;
        }
        return NamedTextColor.WHITE;
    }

    private static String compact(double v) {
        return Numbers.compact(Math.floor(v)); // whole HP on labels: "50", "3.2K", "1.7B"
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
