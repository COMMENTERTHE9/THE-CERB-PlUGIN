package DefensiveMagic;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.Particle;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;

public class Ward extends DefensiveStructure {
    private final double duration;
    private ScheduledTask particleTask;
    private ScheduledTask durationTask;

    public Ward(Player player, double duration) {
        super(player);
        this.duration = duration;
    }

    @Override
    public void activate() {
        isActive = true;
        player.sendMessage("§dA magical ward has been placed, providing protection for " + duration + " seconds.");
        playParticleEffect();
        startDurationTimer();
    }

    @Override
    public void deactivate() {
        isActive = false;
        if (particleTask != null) {
            particleTask.cancel();
            particleTask = null;
        }
        if (durationTask != null) {
            durationTask.cancel();
            durationTask = null;
        }

        // Final ward dissipation effect
        for (double y = 0; y <= 2; y += 0.1) {
            for (double angle = 0; angle < 360; angle += 45) {
                double x = 0.5 * Math.cos(Math.toRadians(angle));
                double z = 0.5 * Math.sin(Math.toRadians(angle));
                player.getWorld().spawnParticle(
                        Particle.ELECTRIC_SPARK,
                        player.getLocation().add(x, y, z),
                        5,
                        0.1, 0.1, 0.1,
                        0.05
                );
            }
        }

        player.sendMessage("§dYour magical ward has faded away.");
    }

    private void playParticleEffect() {
        // Player-bound for Folia
        particleTask = player.getScheduler().runAtFixedRate(plugin, task -> {
            if (!isActive || !player.isOnline()) {
                deactivate();
                return;
            }

            for (double y = 0; y <= 2; y += 0.2) {
                player.getWorld().spawnParticle(
                        Particle.ELECTRIC_SPARK,
                        player.getLocation().add(0.5, y, 0.5),
                        1, 0, 0, 0, 0
                );
                player.getWorld().spawnParticle(
                        Particle.ELECTRIC_SPARK,
                        player.getLocation().add(-0.5, y, 0.5),
                        1, 0, 0, 0, 0
                );
                player.getWorld().spawnParticle(
                        Particle.ELECTRIC_SPARK,
                        player.getLocation().add(0.5, y, -0.5),
                        1, 0, 0, 0, 0
                );
                player.getWorld().spawnParticle(
                        Particle.ELECTRIC_SPARK,
                        player.getLocation().add(-0.5, y, -0.5),
                        1, 0, 0, 0, 0
                );
            }
        }, null, 1L, 20L);
    }

    private void startDurationTimer() {
        // Player-bound for Folia
        durationTask = player.getScheduler().runDelayed(plugin, task -> {
            if (isActive) {
                deactivate();
            }
        }, null, (long)(duration * 20)); // Convert seconds to ticks
    }

    @Override
    public double getManaCost() {
        return 40.0;
    }
}