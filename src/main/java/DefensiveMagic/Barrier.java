package DefensiveMagic;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.Particle;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;

/**
 * RESERVED FOR FUTURE DEFENSIVE MAGIC SYSTEM.
 *
 * This class is currently unreachable. It is preserved as a seed implementation
 * for the future Defensive Magic spell system, which will use a Shield + Book
 * combination architecture (manifestation x principle).
 *
 * Master types: Barrier, Ward, Shield, ManaDrain
 * Sub-principles per master type: Absorption, Negation, Reflection, Conversion
 *
 * DO NOT delete this class.
 * DO NOT attempt to "fix" it by reconnecting it to the old input detection system.
 * DO NOT add new functionality without consulting the design doc.
 *
 * The full architecture will be planned and implemented as a dedicated phase.
 */
public class Barrier extends DefensiveStructure {
    private final double strength;
    private ScheduledTask particleTask;

    public Barrier(Player player, double strength) {
        super(player);
        this.strength = strength;
    }

    @Override
    public void activate() {
        player.sendMessage("A barrier has been raised, enhancing your defenses.");
        playParticleEffect();
        isActive = true;  // Set the active status from parent class
    }

    @Override
    public void deactivate() {
        if (particleTask != null) {
            particleTask.cancel();
            particleTask = null;
        }
        isActive = false;  // Set inactive status
        player.sendMessage("Your barrier has fallen.");

        // Final particle burst effect
        for (double x = -1; x <= 1; x += 0.1) {
            for (double y = 0; y <= 2; y += 0.1) {
                player.getWorld().spawnParticle(
                        Particle.SMOKE,
                        player.getLocation().add(x, y, -1),
                        1,
                        0.2, 0.2, 0.2,
                        0.1
                );
            }
        }
    }

    private void playParticleEffect() {
        // Player-bound for Folia
        particleTask = player.getScheduler().runAtFixedRate(plugin, task -> {
            if (!isActive || !player.isOnline()) {
                task.cancel();
                deactivate();
                return;
            }

            for (double x = -1; x <= 1; x += 0.2) {
                for (double y = 0; y <= 2; y += 0.2) {
                    player.getWorld().spawnParticle(
                            Particle.SMOKE,
                            player.getLocation().add(x, y, -1),
                            1,
                            0, 0, 0,
                            0
                    );
                }
            }
        }, null, 1L, 20L);
    }

    @Override
    public double getManaCost() {
        return 60.0;
    }
}