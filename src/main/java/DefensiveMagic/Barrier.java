package DefensiveMagic;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.Particle;
import org.bukkit.scheduler.BukkitRunnable;

public class Barrier extends DefensiveStructure {
    private final double strength;

    public Barrier(Player player, double strength) {
        super(player); // Call to the superclass constructor
        this.strength = strength;
    }

    @Override
    public void activate() {
        player.sendMessage("A barrier has been raised, enhancing your defenses.");
        playParticleEffect(); // Play wall particle effect
    }

    private void playParticleEffect() {
        new BukkitRunnable() {
            @Override
            public void run() {
                for (double x = -1; x <= 1; x += 0.2) {
                    for (double y = 0; y <= 2; y += 0.2) {
                        player.getWorld().spawnParticle(Particle.SMOKE, player.getLocation().add(x, y, -1), 1, 0, 0, 0, 0);
                    }
                }
            }
        }.runTaskTimer(Bukkit.getPluginManager().getPlugin("cerb"), 0L, 20L); // 1-second interval
    }

    @Override
    public double getManaCost() {
        return 60.0; // Example mana cost
    }
}
