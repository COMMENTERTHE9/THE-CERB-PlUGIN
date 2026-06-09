All API surfaces are verified. The spec is accurate: `MobScalingListener` lines 91-97 hold the nameplate block, `VirtualHealthListener.applyDamageMultiplierToEntity` (244-255) is the refresh hook, `CerberusPlugin` construction is at line 223 / getter at 236 / registration at 344 / disable at 175-184. One important correction surfaced: `resolve()` fires a `CerberusMobIdentifiedEvent` for non-COMMON tiers, so calling it on every refresh is wasteful/risky — the plan must cache the Tier at spawn instead. Here is the merged plan.

---

# Robust Mob Label — Implementation Plan

## 1. Root cause of the flicker

The current label is a **vanilla entity custom name** (a "nameplate"), set in `MobScalingListener.onCreatureSpawn` lines 91-97 via `entity.setCustomName(...)` + `entity.setCustomNameVisible(true)`. A nameplate is not a real object in the world — it is a special client-side render layer, and the client alone decides when to draw it. Three vanilla gates cause the reported "it's there, but if I look a little up or down it goes away": (a) a **look-direction / aim cull** — the client only draws a mob's tag when the camera's look vector is roughly aimed at the entity's bounding box, so pitching up or down drops it even while the mob body stays visible; (b) a **~64-block hard render cut** past which tags fade regardless of aim; (c) **block-occlusion / transparency culling** (Mojang bug MC-202197). None of this is server-tunable — `setCustomNameVisible(true)` only means "eligible to render," after which the client applies all the gates above. The server cannot override it without packets (ProtocolLib, excluded here). The fix is to stop using a nameplate entirely.

## 2. Chosen approach — and why

Replace the nameplate with a **`TextDisplay` entity** (added 1.19.4, mature in 1.21, present in the Folia 1.21 Bukkit API), **mounted as a passenger of the mob** (`mob.addPassenger(textDisplay)`), configured with **`Display.Billboard.CENTER`**.

- **Why TextDisplay, not ArmorStand/scoreboard:** a `TextDisplay` renders as real in-world text geometry, not a nameplate overlay, so it is **immune to the aim cull, the 64-block cap, and the occlusion bug**. An armor stand's custom name and `below-name`/team scoreboard text both render in the *same nameplate layer* and inherit the identical bugs — they are not fixes. TextDisplay is also fully server-controllable (background, opacity, brightness, alignment, view range) with no ProtocolLib.
- **Why `Billboard.CENTER`:** the display plane re-orients toward the camera every frame on **both** axes, so it always squarely faces the player no matter where they look. This is the property that directly kills the look-up/look-down flicker.
- **Why passenger-mount, not a teleport-follow task:** as a passenger the display rides the mob automatically with **zero per-tick code** — it moves, unloads, and (with the cleanup below) dies with its vehicle. This is the cleanest fit for Folia: no scheduled task per mob, no region-vs-entity scheduler hazard. A teleport-follow task would cost one entity-scheduler task per mob and is strictly worse.

Supporting settings: `setSeeThrough(true)` (no block occlusion), `setBrightness(15,15)` (legible at night/in caves), `setViewRange(1.0f)` (render-distance multiplier, no nameplate cap), `setBackgroundColor(...)` (subtle/transparent bg), and a `Transformation` Y-translate to float the text above the head (passengers otherwise mount near the eyes).

The level/HP math is **untouched**: `MobScalingListener` lines 63-87 (`levelForDistance`, `registry.resolve(...).tier()`, `scaling.calculate`, `mvh.init`, `registry.tagLevel`) and `MobVirtualHealthManager` stay byte-for-byte identical. Only the presentation moves.

## 3. Files to change

| File | Change |
|---|---|
| `Manager/MobLabelManager.java` | **New.** TextDisplay lifecycle + cleanup. |
| `Listener/MobScalingListener.java` | Replace nameplate block (91-97) with `attach(...)`; add `remove(...)` in `onDeath`; capture Tier/HP for the manager; drop `ChatColor` import; move `compact`/`prettify` into the manager. |
| `Listener/VirtualHealthListener.java` | One line in `applyDamageMultiplierToEntity` (after line 249) to refresh the label on HP change. |
| `cerberus/world/cerb/CerberusPlugin.java` | Construct `MobLabelManager` (line 223) + getter (near 236); register its listener (line 344 block); clean up in `onDisable` (175-184). |

### 3a. `Manager/MobLabelManager.java` (new)

A registry-style manager mirroring `MobVirtualHealthManager`. **Key correction vs. the draft spec:** `MobRegistry.resolve()` fires a `CerberusMobIdentifiedEvent` for every non-COMMON tier (MobRegistry.java:119-121), so it must **not** be called on the hot damage-refresh path. Instead, cache the `Tier` and `level` at spawn in a small per-mob `LabelState`, so `refresh(...)` only re-reads the live HP fraction.

```java
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
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Owns one always-visible floating TextDisplay label per scaled mob.
 *
 * <p>Replaces the cull-prone vanilla nameplate. The display is mounted as a
 * passenger of the mob (auto-follow, zero per-tick cost) and uses
 * Billboard.CENTER so it always faces the player regardless of look direction.
 *
 * <p>Folia-safe: attach/refresh/remove are only ever called from handlers that
 * fire on the mob's owning region thread (CreatureSpawnEvent, EntityDamageEvent,
 * EntityDeathEvent, EntityRemoveFromWorldEvent). The map is concurrent.
 */
public final class MobLabelManager implements Listener {

    private final CerberusPlugin plugin;
    private final NamespacedKey labelKey;  // on the MOB: TextDisplay UUID (string)
    private final NamespacedKey ownerKey;  // on the DISPLAY: marks it ours (cleanup + combat-ignore)

    /** Cached at spawn so refresh never calls MobRegistry.resolve (which fires events). */
    private static final class LabelState {
        final UUID labelId;
        final int level;
        final Tier tier;
        final String typeName;   // raw EntityType name, prettified at render
        final double maxHp;
        LabelState(UUID labelId, int level, Tier tier, String typeName, double maxHp) {
            this.labelId = labelId; this.level = level; this.tier = tier;
            this.typeName = typeName; this.maxHp = maxHp;
        }
    }

    private final Map<UUID, LabelState> states = new ConcurrentHashMap<>(); // mob UUID -> state

    public MobLabelManager(CerberusPlugin plugin) {
        this.plugin = plugin;
        this.labelKey = new NamespacedKey(plugin, "cerb_label_uuid");
        this.ownerKey = new NamespacedKey(plugin, "cerb_label_owner");
    }

    /** PDC key marking a TextDisplay as ours — lets combat/targeting code skip it. */
    public NamespacedKey ownerKey() { return ownerKey; }

    // ── Spawn: create + mount. Called on the mob's region thread. ──
    public void attach(LivingEntity mob, int level, Tier tier, double maxHp) {
        if (mob == null || !mob.isValid()) return;
        // idempotent: never double-label the same mob
        if (mob.getPersistentDataContainer().has(labelKey, PersistentDataType.STRING)) return;

        World world = mob.getWorld();
        final String typeName = mob.getType().name();
        TextDisplay label = world.spawn(mob.getLocation(), TextDisplay.class, td -> {
            td.text(buildText(level, tier, typeName, maxHp, 1.0));
            td.setBillboard(Display.Billboard.CENTER);          // ALWAYS faces the camera — kills look-cull
            td.setSeeThrough(true);                             // no block-occlusion hide
            td.setShadowed(false);
            td.setDefaultBackground(false);
            td.setBackgroundColor(Color.fromARGB(0x40000000));  // subtle translucent bg
            td.setTextOpacity((byte) 255);
            td.setAlignment(TextDisplay.TextAlignment.CENTER);
            td.setBrightness(new Display.Brightness(15, 15));   // full-bright at night/in caves
            td.setViewRange(1.0f);                              // render-distance multiplier (no 64-block cap)
            // float above head: passengers mount near the eyes, so translate up
            td.setTransformation(new Transformation(
                    new Vector3f(0f, 0.7f, 0f),                 // Y offset — tune per mob height
                    new AxisAngle4f(0f, 0f, 0f, 1f),
                    new Vector3f(1f, 1f, 1f),
                    new AxisAngle4f(0f, 0f, 0f, 1f)));
            td.setPersistent(false);                            // never written to chunk — anti-orphan/anti-dup
            td.setInvulnerable(true);
            td.getPersistentDataContainer().set(ownerKey, PersistentDataType.BYTE, (byte) 1);
        });

        mob.addPassenger(label);   // auto-follow, no per-tick task
        mob.getPersistentDataContainer().set(labelKey, PersistentDataType.STRING,
                label.getUniqueId().toString());
        states.put(mob.getUniqueId(),
                new LabelState(label.getUniqueId(), level, tier, typeName, maxHp));
    }

    // ── Refresh text on HP change. Called from the damage handler (mob's region). ──
    public void refresh(LivingEntity mob, double fraction) {
        if (mob == null) return;
        LabelState st = states.get(mob.getUniqueId());
        if (st == null) return;
        Entity e = Bukkit.getEntity(st.labelId);     // same region as the mob — safe here
        if (e instanceof TextDisplay td && td.isValid()) {
            td.text(buildText(st.level, st.tier, st.typeName, st.maxHp, fraction));
        }
    }

    // ── Remove. Called from death / removal handlers (mob's region). ──
    public void remove(LivingEntity mob) {
        if (mob == null) return;
        UUID mobId = mob.getUniqueId();
        LabelState st = states.remove(mobId);
        UUID labelId = (st != null) ? st.labelId : null;
        if (labelId == null) {
            String s = mob.getPersistentDataContainer().get(labelKey, PersistentDataType.STRING);
            if (s != null) try { labelId = UUID.fromString(s); } catch (IllegalArgumentException ignored) {}
        }
        mob.getPersistentDataContainer().remove(labelKey);
        if (labelId != null) {
            Entity e = Bukkit.getEntity(labelId);
            if (e != null) e.remove();               // region-thread safe — same region as the mob
        }
    }

    /** Drop the in-memory map (called on disable; displays are non-persistent and vanish on unload). */
    public void clear() { states.clear(); }

    // ── Chunk-unload / despawn / kill: drop the map entry + remove the display. ──
    @EventHandler(priority = EventPriority.MONITOR)
    public void onRemove(io.papermc.paper.event.entity.EntityRemoveFromWorldEvent e) {
        if (e.getEntity() instanceof LivingEntity le && states.containsKey(le.getUniqueId())) {
            remove(le);
        }
    }

    // ── Text builder (Adventure Component; keeps the old "Lv3 Zombie 50❤" format) ──
    private Component buildText(int level, Tier tier, String typeName, double maxHp, double fraction) {
        long shownHp = (long) Math.ceil(maxHp * Math.max(0.0, Math.min(1.0, fraction)));
        return Component.text("Lv" + level + " ", NamedTextColor.GRAY)
                .append(Component.text(prettify(typeName), tierColor(tier)))
                .append(Component.text("  " + compact(shownHp) + "\u2764", NamedTextColor.RED));
    }

    /** Map a Tier to an Adventure colour. Replace with tier.displayColor mapping if you expose one. */
    private TextColor tierColor(Tier tier) {
        // tier.displayColor is a legacy ChatColor; convert once. Falls back to white.
        try {
            java.awt.Color c = tier.displayColor.asBungee().getColor();
            return TextColor.color(c.getRed(), c.getGreen(), c.getBlue());
        } catch (Throwable ignored) {
            return NamedTextColor.WHITE;
        }
    }

    private static String compact(double v) {
        if (v < 1000) return String.valueOf((long) v);
        String[] u = {"K", "M", "B", "T", "Qa"};
        int i = -1; double x = v;
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
```

> **Note on `tierColor`:** verify how `Tier.displayColor` is typed in `Manager/Tier.java`. The draft used `tier.displayColor` as a legacy `ChatColor` string-prefix; the conversion above turns it into an Adventure `TextColor`. If `Tier` already exposes an RGB or a `TextColor`, use that directly and delete the try/catch. (This is the one spot to confirm against `Tier.java` before building.)

### 3b. `Listener/MobScalingListener.java`

Replace lines 91-97 with the manager call (the math at 63-87 is unchanged):

```java
// 5. Floating always-on label (TextDisplay passenger) — replaces the vanilla nameplate.
//    Only generic (un-named) mobs, so we never clobber a name the registry matches on.
if (scaling.isShowLevelInName()
        && (entity.getCustomName() == null || entity.getCustomName().isBlank())) {
    plugin.getMobLabelManager().attach(entity, level, tier, maxHealth);
}
```

In `onDeath` (lines 101-105), add label teardown next to the existing virtual-HP removal:

```java
@EventHandler(priority = EventPriority.MONITOR)
public void onDeath(EntityDeathEvent event) {
    Manager.MobVirtualHealthManager mvh = plugin.getMobVirtualHealthManager();
    if (mvh != null) mvh.remove(event.getEntity().getUniqueId());
    if (plugin.getMobLabelManager() != null) plugin.getMobLabelManager().remove(event.getEntity());
}
```

Remove `import org.bukkit.ChatColor;` (line 8) and delete the now-unused `compact`/`prettify` helpers (108-126) since they move into `MobLabelManager`. (Or leave them — harmless — but cleaner to move.)

### 3c. `Listener/VirtualHealthListener.java`

In `applyDamageMultiplierToEntity`, after the pool is decremented (after line 249), refresh the label with the freshly-computed fraction:

```java
boolean lethal = mvh.damage(mob, damage);
if (plugin.getMobLabelManager() != null) {
    plugin.getMobLabelManager().refresh(mob, mvh.fraction(mob));   // <-- new
}
var attr = mob.getAttribute(Attribute.MAX_HEALTH);
// ... unchanged ...
```

This handler already runs on the mob's region thread and already calls `mvh.fraction(mob)` for the cosmetic bar, so this is the correct, free hook for a live HP label. (If only a static spawn-time label is desired, omit this edit.)

### 3d. `cerberus/world/cerb/CerberusPlugin.java`

- **Field + getter** (near line 234-237, beside `mobVirtualHealthManager`):
  ```java
  private Manager.MobLabelManager mobLabelManager;
  public Manager.MobLabelManager getMobLabelManager() { return mobLabelManager; }
  ```
- **Construct** in `initCoreManagers` (after line 223):
  ```java
  mobLabelManager = new Manager.MobLabelManager(this);
  ```
- **Register its listener** (in the `pm.registerEvents(...)` block, near line 344, so the `EntityRemoveFromWorldEvent` handler is live):
  ```java
  pm.registerEvents(mobLabelManager, this);
  ```
- **Disable cleanup** (`onDisable`, 175-184): displays are `setPersistent(false)`, so they are not written to chunks; just drop the map:
  ```java
  if (mobLabelManager != null) mobLabelManager.clear();
  ```

## 4. Pitfalls and the guard for each

1. **Orphaned displays (biggest risk).** A TextDisplay is an independent entity that can outlive its mob on a crash. Guards (all three): `setPersistent(false)` (never saved to chunk); explicit `mob.remove()`-of-display in `onDeath` **and** in the `EntityRemoveFromWorldEvent` handler (covers despawn, `/kill`, chunk-unload); plus the `ownerKey` PDC tag so a defensive startup sweep can find and purge any stray `TextDisplay` (iterate loaded worlds on enable, remove any display with `ownerKey`).
2. **Restart persistence / duplicates.** `setPersistent(false)` is the fix — the display is never serialized, so it cannot reload as a duplicate while the transient virtual-HP state is gone. (This deliberately departs from the draft's `setPersistent(true)` option; non-persistent is safer here because the mob's HP pool is itself transient — a persisted label would survive into a state with no matching pool. On next spawn/reload the label is simply re-created.)
3. **Passenger vertical offset (text sits in the head).** Passengers mount near the eyes, so the text floats too low. Fix with the `Transformation` Y-translate (`Vector3f(0, 0.7f, 0)` above). Tune the constant per mob height if tall/short mobs look off; consider a small per-EntityType table or scaling by `mob.getHeight()`. If the mount offset is ever unworkable, the fallback is an unmounted display repositioned on the **mob's** `getScheduler()` task — but the mount avoids that ticking cost.
4. **Label targetable/hittable.** A TextDisplay has no hitbox/collision by default, so it won't intercept melee or projectiles, and players can't ride it. Belt-and-suspenders: `setInvulnerable(true)` and the `ownerKey` PDC tag so any AoE/entity-iteration combat code can skip it. Add a one-line guard in entity-loop combat code: `if (e.getPersistentDataContainer().has(mobLabelManager.ownerKey(), PersistentDataType.BYTE)) continue;`.
5. **`MobRegistry.resolve()` side effect (correctness).** `resolve()` fires `CerberusMobIdentifiedEvent` for non-COMMON tiers, so it must **not** be called on the per-hit refresh path — the plan caches `Tier`/`level`/`maxHp` in `LabelState` at spawn and `refresh()` reads only the live fraction. (This corrects the draft, which called `resolve()` inside `refresh`.)
6. **Folia threading.** Every entity touch happens on the mob's owning region thread: `attach` from `CreatureSpawnEvent`, `refresh` from `EntityDamageEvent`, `remove` from `EntityDeathEvent`/`EntityRemoveFromWorldEvent`. `Bukkit.getEntity(labelId)` in `refresh`/`remove` resolves the passenger in that same region. `onDisable` runs off-region but only calls `clear()` (no entity touch). The `states` map is a `ConcurrentHashMap`. No `teleportAsync` needed — the display rides the mob.
7. **View range / brightness.** `setViewRange(1.0f)` is a render-distance multiplier (raise for longer range, but it can't exceed the player's chunk view-distance — the chunk must be loaded). `setBrightness(15,15)` keeps the label legible at night/in caves. No jitter to worry about: as a passenger the display inherits the mob's movement interpolation, so leave `teleportDuration` at default.

## 5. In-game test checklist

1. **Build:** `$env:JAVA_HOME = "C:\Program Files\Eclipse Adoptium\jdk-21.0.9.10-hotspot"; .\mvnw.cmd test` (JDK 21, `--enable-preview`).
2. **Look-direction (the bug):** spawn a scaled mob, stand close, pan the camera straight up and straight down. The label stays fully visible and rotates to face you the whole time (no flicker/disappear). This is the primary acceptance test.
3. **Occlusion:** put a glass pane / partial block between you and the mob — label still visible (`seeThrough` true).
4. **Distance:** back away past ~64 blocks (within view-distance) — label still renders (no nameplate cap).
5. **Night/cave:** verify the label is full-bright and legible in darkness.
6. **Live HP:** hit the mob repeatedly — the `❤` number ticks down each hit (refresh hook firing) and the format/tier color is correct.
7. **Death cleanup:** kill the mob — the floating label disappears immediately, no orphan left behind. Run `/kill @e[type=text_display]`-style audit or check entity count.
8. **Chunk unload:** walk far away so the chunk unloads, then return — no stray/duplicate labels; the mob (if still loaded) shows exactly one label.
9. **Restart:** stop and restart the server — no orphaned/duplicate TextDisplays persist (confirms `setPersistent(false)`).
10. **Specials untouched:** spawn a registry-named special mob — it keeps its identifying name and gets no generic Lv-label (the `getCustomName() == null` guard holds).
11. **Combat integrity:** confirm melee/projectile hits land on the mob, never on the label (no "missed" hits, no errors), and AoE skills ignore the display.
12. **Folia sanity:** watch the console for any "Thread … cannot access entities in region …" errors during spawn/combat/death — there should be none.