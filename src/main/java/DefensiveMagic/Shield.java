package DefensiveMagic;

import org.bukkit.entity.Player;
import org.bukkit.Particle;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.Bukkit;

public class Shield extends DefensiveStructure {
    private double strength;
    private BukkitRunnable particleTask;

    public Shield(Player player, double strength) {
        super(player);
        this.strength = strength;
    }

    @Override
    public void activate() {
        isActive = true; // Using parent class isActive
        player.sendMessage("§bA magical shield surrounds you, absorbing damage.");
        playParticleEffect();
    }

    @Override
    public void deactivate() {
        isActive = false;
        if (particleTask != null) {
            particleTask.cancel();
            particleTask = null;
        }

        // Final shield break effect
        for (double phi = 0; phi <= Math.PI; phi += Math.PI / 15) {
            double y = Math.cos(phi);
            for (double theta = 0; theta <= 2 * Math.PI; theta += Math.PI / 15) {
                double x = Math.sin(phi) * Math.cos(theta);
                double z = Math.sin(phi) * Math.sin(theta);
                player.getWorld().spawnParticle(
                        Particle.WITCH,
                        player.getLocation().add(x, y + 1, z),
                        3,
                        0.1, 0.1, 0.1,
                        0.05
                );
            }
        }

        player.sendMessage("§cYour magical shield has dissipated!");
    }

    private void playParticleEffect() {
        particleTask = new BukkitRunnable() {
            @Override
            public void run() {
                if (!isActive || !player.isOnline()) {
                    deactivate();
                    return;
                }

                for (double phi = 0; phi <= Math.PI; phi += Math.PI / 10) {
                    double y = Math.cos(phi);
                    for (double theta = 0; theta <= 2 * Math.PI; theta += Math.PI / 10) {
                        double x = Math.sin(phi) * Math.cos(theta);
                        double z = Math.sin(phi) * Math.sin(theta);
                        player.getWorld().spawnParticle(
                                Particle.WITCH,
                                player.getLocation().add(x, y + 1, z),
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
        return 50.0;
    }

    // Method to absorb damage using the shield
    public void absorbDamage(double damage) {
        if (isActive && strength > 0) {
            strength -= damage;

            // Visual feedback for shield hit
            player.getWorld().spawnParticle(
                    Particle.FLASH,
                    player.getLocation(),
                    10,
                    0.5, 0.5, 0.5,
                    0.1
            );

            if (strength <= 0) {
                strength = 0;
                deactivate();
            } else {
                // Show remaining shield strength
                player.sendMessage(String.format("§bShield strength: §f%.1f", strength));
            }
        }
    }

    public double getStrength() {
        return strength;
    }
}