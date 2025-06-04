package DefensiveMagic;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.Particle;
import org.bukkit.scheduler.BukkitRunnable;

public class Barrier extends DefensiveStructure {
    private final double strength;
    private BukkitRunnable particleTask;

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
        particleTask = new BukkitRunnable() {
            @Override
            public void run() {
                if (!isActive || !player.isOnline()) {
                    cancel();
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
            }
        };
        particleTask.runTaskTimer(plugin, 0L, 20L);
    }

    @Override
    public double getManaCost() {
        return 60.0;
    }
}