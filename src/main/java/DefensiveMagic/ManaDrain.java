package DefensiveMagic;

import Manager.CustomDamageType;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;
import cerberus.world.cerb.CerberusPlugin;

public class ManaDrain extends DefensiveStructure {
    private final double effectiveness;
    private final Entity target;
    private final Plugin plugin;
    private BukkitRunnable particleTask;
    private static final double DRAIN_INTERVAL = 1.0; // Seconds between drain ticks
    private static final double BASE_MANA_DRAIN = 10.0;
    private static final double BASE_DAMAGE = 5.0;

    public ManaDrain(Player player, double effectiveness, Entity target) {
        super(player);
        this.effectiveness = effectiveness;
        this.target = target;
        this.plugin = Bukkit.getPluginManager().getPlugin("CerberusPlugin");
    }

    @Override
    public void activate() {
        player.sendMessage("§9A mana drain has been activated, draining mana from your target.");
        startDrainEffect();
        playParticleEffect();
    }

    private void startDrainEffect() {
        new BukkitRunnable() {
            private int ticks = 0;
            private static final int MAX_DURATION = 100; // 5 seconds (20 ticks per second)

            @Override
            public void run() {
                if (ticks >= MAX_DURATION || !isValidDrain()) {
                    this.cancel();
                    deactivate();
                    return;
                }

                applyDrainEffect();
                ticks++;
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    private void applyDrainEffect() {
        if (target instanceof Player targetPlayer) {
            double drainAmount = calculateDrainAmount();
            double damage = calculateDrainDamage();

            // Apply mana drain
            if (drainMana(targetPlayer, drainAmount)) {
                // Apply damage if mana was successfully drained
                CerberusPlugin.getInstance().getPlayerVirtualHealthManager().applyDamage(
                        targetPlayer,
                        damage,
                        CustomDamageType.MANA_DRAIN,
                        player
                );

                // Restore some mana to the caster
                restoreMana(player, drainAmount * 0.5);

                // Check for mana depletion effects
                if (isManaDepleted(targetPlayer)) {
                    applyManaDepletion(targetPlayer);
                }
            }
        }
    }

    private void playParticleEffect() {
        particleTask = new BukkitRunnable() {
            @Override
            public void run() {
                if (!isValidDrain()) {
                    this.cancel();
                    return;
                }

                drawDrainBeam();
                spawnDrainParticles();
            }
        };
        particleTask.runTaskTimer(plugin, 0L, 2L);
    }

    private void drawDrainBeam() {
        Location start = player.getLocation().add(0, 1, 0);
        Location end = target.getLocation().add(0, 1, 0);
        Vector direction = end.toVector().subtract(start.toVector());
        double distance = direction.length();
        direction.normalize();

        for (double i = 0; i < distance; i += 0.5) {
            Location point = start.clone().add(direction.clone().multiply(i));

            // Main beam
            player.getWorld().spawnParticle(
                    Particle.DRAGON_BREATH,
                    point,
                    1,
                    0, 0, 0,
                    0,
                    new Particle.DustOptions(Color.BLUE, 1)
            );

            // Spiral effect
            double radius = 0.3;
            double pitch = (i / distance) * Math.PI * 8;
            Location spiralPoint = point.clone().add(
                    Math.cos(pitch) * radius,
                    Math.sin(pitch) * radius,
                    Math.cos(pitch) * radius
            );

            player.getWorld().spawnParticle(
                    Particle.DRAGON_BREATH,
                    spiralPoint,
                    1,
                    0, 0, 0,
                    0,
                    new Particle.DustOptions(Color.fromRGB(100, 100, 255), 0.7f)
            );
        }
    }

    private void spawnDrainParticles() {
        if (target instanceof Player) {
            Location targetLoc = target.getLocation().add(0, 1, 0);

            // Mana drain particles around target
            for (int i = 0; i < 8; i++) {
                double angle = (i / 8.0) * Math.PI * 2;
                Location particleLoc = targetLoc.clone().add(
                        Math.cos(angle) * 0.5,
                        0,
                        Math.sin(angle) * 0.5
                );

                player.getWorld().spawnParticle(
                        Particle.CRIT,
                        particleLoc,
                        1,
                        0, 0, 0,
                        0,
                        new Particle.DustOptions(Color.fromRGB(0, 0, 255), 1)
                );
            }
        }
    }

    private double calculateDrainAmount() {
        return BASE_MANA_DRAIN * effectiveness;
    }

    private double calculateDrainDamage() {
        return BASE_DAMAGE * effectiveness;
    }

    private boolean drainMana(Player target, double amount) {
        // Get mana manager from your plugin instance
        return CerberusPlugin.getInstance().getPlayerManaManager().consumeMana(target, amount);
    }

    private void restoreMana(Player player, double amount) {
        CerberusPlugin.getInstance().getPlayerManaManager().addMana(player, amount);
    }

    private boolean isManaDepleted(Player target) {
        return CerberusPlugin.getInstance().getPlayerManaManager().getMana(target) <= 0;
    }

    private void applyManaDepletion(Player target) {
        // Apply additional effects when mana is fully drained
        CerberusPlugin.getInstance().getPlayerVirtualHealthManager().applyDamage(
                target,
                BASE_DAMAGE * 2,
                CustomDamageType.MANA_VOID,
                player
        );

        // Visual effect for mana depletion
        target.getWorld().spawnParticle(
                Particle.SOUL,
                target.getLocation().add(0, 1, 0),
                20,
                0.5, 0.5, 0.5,
                0.1
        );
    }

    private boolean isValidDrain() {
        if (!player.isOnline() || !target.isValid()) return false;
        if (player.getWorld() != target.getWorld()) return false;
        if (player.getLocation().distance(target.getLocation()) > 10) return false;
        return true;
    }

    @Override
    public void deactivate() {
        if (particleTask != null) {
            particleTask.cancel();
        }
        player.sendMessage("§9Mana drain has ended.");
    }

    @Override
    public double getManaCost() {
        return 30.0;
    }
}