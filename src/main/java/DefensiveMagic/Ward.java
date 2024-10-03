package DefensiveMagic;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.Particle;
import org.bukkit.scheduler.BukkitRunnable;

public class Ward extends DefensiveStructure {
    private final double duration;

    public Ward(Player player, double duration) {
        super(player); // Call to the superclass constructor
        this.duration = duration;
    }

    @Override
    public void activate() {
        player.sendMessage("A magical ward has been placed, providing protection for " + duration + " seconds.");
        playParticleEffect(); // Play outline particle effect
    }

    private void playParticleEffect() {
        new BukkitRunnable() {
            @Override
            public void run() {
                for (double y = 0; y <= 2; y += 0.2) {
                    player.getWorld().spawnParticle(Particle.ELECTRIC_SPARK, player.getLocation().add(0.5, y, 0.5), 1, 0, 0, 0, 0);
                    player.getWorld().spawnParticle(Particle.ELECTRIC_SPARK, player.getLocation().add(-0.5, y, 0.5), 1, 0, 0, 0, 0);
                    player.getWorld().spawnParticle(Particle.ELECTRIC_SPARK, player.getLocation().add(0.5, y, -0.5), 1, 0, 0, 0, 0);
                    player.getWorld().spawnParticle(Particle.ELECTRIC_SPARK, player.getLocation().add(-0.5, y, -0.5), 1, 0, 0, 0, 0);
                }
            }
        }.runTaskTimer(Bukkit.getPluginManager().getPlugin("cerb"), 0L, 20L); // 1-second interval
    }

    @Override
    public double getManaCost() {
        return 40.0; // Example mana cost
    }
}
