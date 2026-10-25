package Classes;

import Manager.PlayerManaManager;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The ability bar (docs/design/ABILITY_CONTROLS.md): a boss bar listing the loadout's keys, names
 * and cooldowns, so the keys can be learned at a glance and readiness is visible mid-fight:
 * {@code [R] Cleave  [F] War Cry 12  [Q] Charge  [⇧R] Execute  ⚔ ●●●○○}. A name is green when
 * ready, grey with seconds left on cooldown, blue when there isn't enough Essence and dark when it
 * doesn't fit the item in hand.
 *
 * <p>Shown only while a weapon, focus or empty hand is held and the loadout has an active: in a
 * fight and for a few seconds after a press or after taking one in hand (AUTO, the default),
 * always (ON) or never (OFF); each player chooses. While shown it refreshes twice a second; hidden,
 * it costs nothing (its task stops). Player's thread.
 */
public final class AbilityBar {

    private static final long REFRESH_TICKS = 10;
    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacySection();

    private final Plugin plugin;
    private final ClassService service;
    private final PlayerManaManager mana;

    private static final class State {
        BossBar bar;
        volatile boolean shown;
        long until;
        ScheduledTask task;
        String text = "";
        volatile long combatPokeAt;
    }

    private final Map<UUID, State> states = new ConcurrentHashMap<>();

    public AbilityBar(Plugin plugin, ClassService service, PlayerManaManager mana) {
        this.plugin = plugin;
        this.service = service;
        this.mana = mana;
    }

    /** Show the bar for a few seconds (AUTO) after a press or after taking a weapon in hand, and refresh it now. */
    public void touch(Player p) {
        State s = states.computeIfAbsent(p.getUniqueId(), k -> new State());
        s.until = System.currentTimeMillis() + service.catalog().controls().barLingerMs();
        refresh(p);
    }

    /**
     * A hit dealt or taken (any thread): a hidden bar may need to appear (AUTO shows it in a fight).
     * At most one check per second, on the player's own thread.
     */
    public void touchIfShown(Player p) {
        State s = states.computeIfAbsent(p.getUniqueId(), k -> new State());
        if (s.shown) return;
        long now = System.currentTimeMillis();
        if (now - s.combatPokeAt < 1_000) return;
        s.combatPokeAt = now;
        p.getScheduler().run(plugin, t -> refresh(p), null);
    }

    /** Recompute: show and update, or hide and stop the refresh task. Player's thread. */
    public void refresh(Player p) {
        State s = states.computeIfAbsent(p.getUniqueId(), k -> new State());
        String text = p.isOnline() && !p.isDead() ? render(p, s) : null;
        if (text == null) {
            hide(p, s);
            return;
        }
        if (s.bar == null) {
            s.bar = BossBar.bossBar(LEGACY.deserialize(text), 1f, BossBar.Color.WHITE, BossBar.Overlay.PROGRESS);
            s.text = text;
        } else if (!text.equals(s.text)) {
            s.bar.name(LEGACY.deserialize(text));
            s.text = text;
        }
        if (!s.shown) {
            p.showBossBar(s.bar);
            s.shown = true;
        }
        if (s.task == null) {
            s.task = p.getScheduler().runAtFixedRate(plugin, t -> refresh(p), () -> { }, REFRESH_TICKS, REFRESH_TICKS);
        }
    }

    /** Remove a player's bar (quit). */
    public void forget(Player p) {
        State s = states.remove(p.getUniqueId());
        if (s != null) hide(p, s);
    }

    private static void hide(Player p, State s) {
        if (s.shown && s.bar != null) p.hideBossBar(s.bar);
        s.shown = false;
        if (s.task != null) {
            s.task.cancel();
            s.task = null;
        }
    }

    /** The bar's text, or null when it should not show. */
    private String render(Player p, State s) {
        UUID id = p.getUniqueId();
        Controls.BarMode mode = service.barMode(id);
        if (mode == Controls.BarMode.OFF) return null;
        ClassProfile prof = service.profile(id);
        if (prof == null) return null;
        WeaponFamily hand = WeaponFamily.ofMaterial(p.getInventory().getItemInMainHand().getType().name());
        if (hand == WeaponFamily.OTHER) return null;
        long now = System.currentTimeMillis();
        if (mode == Controls.BarMode.AUTO && now > s.until && !service.inCombat(id)) return null;
        Controls.Scheme scheme = service.scheme(id);
        boolean bow = hand == WeaponFamily.BOW || hand == WeaponFamily.CROSSBOW;
        double essence = mana == null ? Double.MAX_VALUE : mana.getCurrentMana(p);
        List<String> actives = prof.activeLoadout().actives();
        StringBuilder b = new StringBuilder();
        boolean any = false, momentum = false;
        for (int slot = 0; slot < Loadout.ACTIVE_SLOTS; slot++) {
            AbilityDef a = service.catalog().ability(actives.get(slot));
            if (a == null || !a.isActive()) continue;
            any = true;
            momentum |= usesMomentum(a);
            if (!b.isEmpty()) b.append("  ");
            b.append("§8[§e").append(Controls.keyShort(scheme, slot, bow)).append("§8] ");
            long left = prof.cooldowns().remaining(a, now);
            if (!prof.unlocked(a) || !WeaponFamily.fits(a.weapons(), hand)) b.append("§8").append(a.displayName());
            else if (left > 0) b.append("§7").append(a.displayName()).append(" §f").append((left + 999) / 1000);
            else if (a.essenceCost() > essence) b.append("§9").append(a.displayName());
            else if (AbilityCaster.momentum(prof) < AbilityCaster.momentumNeeded(a)) b.append("§6").append(a.displayName());
            else b.append("§a").append(a.displayName());
        }
        if (!any) return null;
        if (momentum) {
            int m = AbilityCaster.momentum(prof);
            b.append("   §6⚔ ").append("●".repeat(m)).append("§8").append("○".repeat(Math.max(0, ClassCatalog.MAX_STACKS - m)));
        }
        return b.toString();
    }

    private static boolean usesMomentum(AbilityDef a) {
        for (EffectSpec e : a.effects()) {
            if (e instanceof EffectSpec.Resource r && "momentum".equalsIgnoreCase(r.kind())) return true;
            if (e instanceof EffectSpec.Damage d && d.perMomentum() > 0) return true;
        }
        return false;
    }
}
