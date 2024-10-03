package DefensiveMagic;

import org.bukkit.entity.Player;
import org.bukkit.Particle;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.Bukkit;

public class Shield extends DefensiveStructure {
    private double strength;
    private boolean active;

    public Shield(Player player, double strength) {
        super(player);
        this.strength = strength;
        this.active = true; // Initialize the shield as active
    }

    @Override
    public void activate() {
        player.sendMessage("A magical shield surrounds you, absorbing damage.");
        playParticleEffect(); // Play spherical particle effect
    }

    private void playParticleEffect() {
        new BukkitRunnable() {
            @Override
            public void run() {
                for (double phi = 0; phi <= Math.PI; phi += Math.PI / 10) {
                    double y = Math.cos(phi);
                    for (double theta = 0; theta <= 2 * Math.PI; theta += Math.PI / 10) {
                        double x = Math.sin(phi) * Math.cos(theta);
                        double z = Math.sin(phi) * Math.sin(theta);
                        player.getWorld().spawnParticle(Particle.WITCH, player.getLocation().add(x, y, z), 1, 0, 0, 0, 0);
                    }
                }
            }
        }.runTaskTimer(Bukkit.getPluginManager().getPlugin("cerb"), 0L, 20L); // 1-second interval
    }

    @Override
    public double getManaCost() {
        return 50.0; // Example mana cost
    }

    // Method to check if the shield is still active
    public boolean isActive() {
        return active && strength > 0;
    }

    // Method to absorb damage using the shield
    public void absorbDamage(double damage) {
        if (strength > 0) {
            strength -= damage;
            if (strength <= 0) {
                strength = 0;
                active = false;
                player.sendMessage("Your shield has been broken!");
            }
        }
    }
}
