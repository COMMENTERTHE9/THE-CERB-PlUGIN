package Manager;

import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.function.IntSupplier;

/**
 * Who is in PvP combat: hitting or being hit by another player (melee or projectile) tags both
 * for a few seconds. Speed and handling bonuses pause while tagged (unless attributes.yml says
 * otherwise), so PvP stays vanilla until the owner decides. Folia: the damage event runs on the
 * victim's region; each player's own change is scheduled on that player's thread.
 */
public final class PvpTag implements Listener {

    private final JavaPlugin plugin;
    private final IntSupplier seconds;
    /** Called on the player's thread when their tag starts or ends. */
    private final Consumer<Player> onChange;
    private final Map<UUID, Long> until = new ConcurrentHashMap<>();

    public PvpTag(JavaPlugin plugin, IntSupplier seconds, Consumer<Player> onChange) {
        this.plugin = plugin;
        this.seconds = seconds;
        this.onChange = onChange;
    }

    public boolean isTagged(UUID player) {
        Long t = until.get(player);
        return t != null && t > System.currentTimeMillis();
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent e) {
        if (!(e.getEntity() instanceof Player victim)) return;
        Player attacker = e.getDamager() instanceof Player p ? p
                : e.getDamager() instanceof Projectile pr && pr.getShooter() instanceof Player p2 ? p2 : null;
        if (attacker == null || attacker.equals(victim)) return;
        tag(victim);
        tag(attacker);
    }

    private void tag(Player p) {
        long ms = seconds.getAsInt() * 1000L;
        long now = System.currentTimeMillis();
        Long prev = until.put(p.getUniqueId(), now + ms);
        if (prev != null && prev > now) return; // already tagged: just extended, the pending check will see it
        p.getScheduler().run(plugin, t -> onChange.accept(p), null);
        scheduleEnd(p, ms);
    }

    private void scheduleEnd(Player p, long delayMs) {
        long ticks = Math.max(1, (delayMs + 49) / 50);
        p.getScheduler().runDelayed(plugin, t -> {
            Long end = until.get(p.getUniqueId());
            if (end == null) return;
            long left = end - System.currentTimeMillis();
            if (left > 0) { scheduleEnd(p, left); return; } // extended meanwhile
            if (until.remove(p.getUniqueId(), end)) onChange.accept(p); // unless a new tag just replaced it
        }, null, ticks);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        until.remove(e.getPlayer().getUniqueId());
    }
}
