package DefensiveMagic;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.Particle;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.entity.Entity;

public class ManaDrain extends DefensiveStructure {
    private final double effectiveness;
    private final Entity target;

    public ManaDrain(Player player, double effectiveness, Entity target) {
        super(player); // Call to the superclass constructor
        this.effectiveness = effectiveness;
        this.target = target;
    }

    @Override
    public void activate() {
        player.sendMessage("A mana drain has been activated, draining mana from attackers.");
        playParticleEffect(); // Play line particle effect
    }

    private void playParticleEffect() {
        new BukkitRunnable() {
            @Override
            public void run() {
                double distance = player.getLocation().distance(target.getLocation());
                for (double i = 0; i <= distance; i += 0.2) {
                    player.getWorld().spawnParticle(Particle.DUST, player.getLocation().clone().add(player.getLocation().getDirection().multiply(i)), 1, 0, 0, 0, 0);
                }
            }
        }.runTaskTimer(Bukkit.getPluginManager().getPlugin("cerb"), 0L, 20L); // 1-second interval
    }

    @Override
    public double getManaCost() {
        return 30.0; // Example mana cost
    }
}
