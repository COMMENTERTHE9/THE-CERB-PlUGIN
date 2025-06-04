package Traps;

import Manager.CustomDamageType;
import cerberus.world.cerb.CerberusPlugin;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import java.util.Random;
import org.bukkit.util.Vector;


public class Trap {
    private final Random random = new Random();
    private final Player owner;
    private final Location location;  // Final field must be initialized
    private final TrapPattern pattern;
    private final BlockFace facing;
    private final CerberusPlugin plugin;

    // Modifiable properties
    private double baseDamage;
    private double potencyMultiplier = 1.0;
    private double triggerSensitivity = 1.0;
    private double durationMultiplier = 1.0;
    private TrapState currentState = TrapState.NEUTRAL;

    public Trap(Player owner, Location location, TrapPattern pattern, BlockFace facing, CerberusPlugin plugin) {
        this.owner = owner;
        this.location = location;  // Initialize here
        this.pattern = pattern;
        this.facing = facing;
        this.plugin = plugin;
    }


    private double calculateBaseDamage() {
        return switch(pattern.getTrapType()) {
            case BASIC_SNARE -> 5.0;
            case EXPLOSIVE_TRAP -> 10.0;
            default -> 3.0;
        };
    }

    // Property modifiers
    public void setPotencyMultiplier(double multiplier) {
        this.potencyMultiplier = multiplier;
    }

    public void setTriggerSensitivity(double sensitivity) {
        this.triggerSensitivity = sensitivity;
    }

    public void setDurationMultiplier(double multiplier) {
        this.durationMultiplier = multiplier;
    }

    // State management
    public void setState(TrapState newState) {
        this.currentState = newState;
    }

    private void playStateChangeEffects(TrapState oldState, TrapState newState) {
        Location effectLoc = location.clone().add(0.5, 0.5, 0.5);
        World world = location.getWorld();

        switch(newState) {
            case ARMED -> {
                // Start a repeating task for arming animation
                new BukkitRunnable() {
                    double angle = 0;
                    int ticks = 0;

                    @Override
                    public void run() {
                        if (ticks >= 40) { // 2 second animation
                            this.cancel();
                            return;
                        }

                        // Double helix effect
                        for (int i = 0; i < 2; i++) {
                            double offsetAngle = angle + (i * Math.PI);
                            double y = ticks * 0.1;
                            double radius = Math.max(0.5, 2.0 - (y * 0.2));

                            double x = Math.cos(offsetAngle) * radius;
                            double z = Math.sin(offsetAngle) * radius;

                            Location particleLoc = effectLoc.clone().add(x, y, z);
                            world.spawnParticle(Particle.WITCH, particleLoc, 1, 0, 0, 0, 0);
                            world.spawnParticle(Particle.END_ROD, particleLoc, 1, 0, 0, 0, 0);
                        }

                        // Rune circle at base
                        for (double a = 0; a < Math.PI * 2; a += Math.PI / 8) {
                            double x = Math.cos(a + angle) * 1.5;
                            double z = Math.sin(a + angle) * 1.5;
                            Location runeLoc = effectLoc.clone().add(x, 0, z);
                            world.spawnParticle(Particle.ENCHANTED_HIT, runeLoc, 1, 0, 0, 0, 0);
                        }

                        angle += Math.PI / 16;
                        ticks++;
                    }
                }.runTaskTimer(plugin, 0L, 1L);
            }

            case ACTIVATED -> {
                // Activation sequence
                new BukkitRunnable() {
                    int stage = 0;

                    @Override
                    public void run() {
                        if (stage >= 20) {
                            this.cancel();
                            return;
                        }

                        // Expanding ring
                        double radius = stage * 0.2;
                        for (double angle = 0; angle < Math.PI * 2; angle += Math.PI / 16) {
                            double x = Math.cos(angle) * radius;
                            double z = Math.sin(angle) * radius;
                            Location ringLoc = effectLoc.clone().add(x, 0, z);
                            world.spawnParticle(Particle.SMALL_GUST, ringLoc, 1, 0, 0, 0, 0);
                        }

                        // Rising particles
                        if (stage % 2 == 0) {
                            for (int i = 0; i < 4; i++) {
                                double angle = (i * Math.PI / 2) + (stage * 0.2);
                                double x = Math.cos(angle) * 0.5;
                                double z = Math.sin(angle) * 0.5;
                                Location particleLoc = effectLoc.clone().add(x, stage * 0.1, z);
                                world.spawnParticle(Particle.CRIT, particleLoc, 1, 0, 0, 0, 0);
                            }
                        }

                        stage++;
                    }
                }.runTaskTimer(plugin, 0L, 1L);
            }

            case TRIGGERED -> {
                // Explosion sequence
                new BukkitRunnable() {
                    int stage = 0;
                    double baseRadius = 0.5;

                    @Override
                    public void run() {
                        if (stage >= 15) {
                            this.cancel();
                            return;
                        }

                        // Imploding particles
                        for (int i = 0; i < 8; i++) {
                            double angle = (i * Math.PI / 4) + (stage * 0.2);
                            double radius = baseRadius + (stage * 0.3);
                            double x = Math.cos(angle) * radius;
                            double y = Math.sin(stage * 0.5) * 1.5;
                            double z = Math.sin(angle) * radius;

                            Location particleLoc = effectLoc.clone().add(x, y, z);
                            world.spawnParticle(Particle.SPLASH, particleLoc, 0, 1, 0, 0, 1);
                            world.spawnParticle(Particle.CRIT, particleLoc, 1, 0, 0, 0, 0.1);
                        }

                        if (stage == 14) {
                            // Final explosion
                            world.spawnParticle(Particle.EXPLOSION, effectLoc, 1, 0, 0, 0, 0);
                            world.spawnParticle(Particle.FLASH, effectLoc, 2, 0.3, 0.3, 0.3, 0);
                            for (int i = 0; i < 50; i++) {
                                Vector direction = new Vector(
                                        random.nextDouble() - 0.5,ok
                                        random.nextDouble() - 0.5,
                                        random.nextDouble() - 0.5
                                ).normalize().multiply(0.2);
                                Location particleLoc = effectLoc.clone();
                                world.spawnParticle(Particle.WITCH, particleLoc, 0,
                                        direction.getX(), direction.getY(), direction.getZ(), 1);
                            }
                        }

                        stage++;
                    }
                }.runTaskTimer(plugin, 0L, 1L);
            }
        }
    }
    // Damage handling
    public void applyTrapDamage(Entity target) {
        if (!(target instanceof LivingEntity livingEntity)) return;

        CustomDamageType damageType = getDamageType();
        double modifiedDamage = getModifiedDamage(baseDamage);

        plugin.getPlayerVirtualHealthManager().applyDamage(
                livingEntity,
                modifiedDamage,
                damageType,
                owner
        );
    }

    private CustomDamageType getDamageType() {
        return switch(pattern.getTrapType()) {
            case BASIC_SNARE -> CustomDamageType.PHYSICAL_TRAP;
            case EXPLOSIVE_TRAP -> CustomDamageType.ELEMENTAL_TRAP;
            default -> CustomDamageType.PHYSICAL_TRAP;
        };
    }

    // Getters
    public Player getOwner() { return owner; }
    public Location getLocation() { return location; }
    public TrapPattern getPattern() { return pattern; }
    public BlockFace getFacing() { return facing; }
    public double getBaseDamage() { return baseDamage; }
    public double getPotencyMultiplier() { return potencyMultiplier; }
    public double getTriggerSensitivity() { return triggerSensitivity; }
    public double getDurationMultiplier() { return durationMultiplier; }
    public TrapState getCurrentState() { return currentState; }

    // Utility methods
    public boolean isActive() {
        return currentState == TrapState.ARMED || currentState == TrapState.ACTIVATED;
    }

    public double getModifiedDamage(double damage) {
        return damage * potencyMultiplier;
    }

    public double getModifiedTriggerRadius(double baseRadius) {
        return baseRadius * triggerSensitivity;
    }

    public long getModifiedDuration(long baseDuration) {
        return (long)(baseDuration * durationMultiplier);
    }
}