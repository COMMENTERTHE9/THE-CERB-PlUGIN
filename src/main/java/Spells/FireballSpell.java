package Spells;

import Manager.ManaType;
import Manager.PlayerManaManager;
import Manager.EffectManager;  // Import the EffectManager
import cerberus.world.cerb.CerberusPlugin;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;
import org.bukkit.Location;

import java.util.List;

public class FireballSpell extends Spell {

    private final PlayerManaManager manaManager;
    private final EffectManager effectManager;  // Declare the EffectManager
    private final double baseManaCost = 20; // Base mana cost for casting the Fireball spell
    private final double baseDamage = 10; // Base damage of the Fireball spell
    private final double explosionRadius = 3.0; // Radius for AoE damage

    public FireballSpell(PlayerManaManager manaManager, EffectManager effectManager) {  // Add EffectManager to the constructor
        super("Fireball");
        this.manaManager = manaManager;
        this.effectManager = effectManager;  // Initialize the EffectManager
    }

    @Override
    public void cast(Player player) {
        // Check if the player has enough mana to cast the spell
        double manaCost = baseManaCost;
        double damage = baseDamage;

        // Apply any skill-based mana cost reductions or damage bonuses
        effectManager.applyMagicSkillEffect(player, "Elemental Mastery");  // Use the EffectManager to apply effects

        // Updated spendMana call without passing Player object
        if (!manaManager.spendMana(player,ManaType.BASIC, manaCost)) {
            player.sendMessage("Not enough mana to cast Fireball!");
            return;
        }

        // Define the initial location and direction of the fireball
        Location startLocation = player.getEyeLocation();
        Vector direction = player.getLocation().getDirection().normalize().multiply(0.5); // Adjust the multiplier for speed

        // Play sound effect when casting the fireball
        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_BLAZE_SHOOT, 1.0f, 1.0f);

        // Create the particle fireball as a moving effect
        new BukkitRunnable() {
            Location location = startLocation.clone();
            int distanceTraveled = 0;
            final int maxDistance = 50; // Maximum distance the fireball can travel

            @Override
            public void run() {
                if (distanceTraveled >= maxDistance) {
                    cancel();
                    return;
                }

                // Move the particle forward by updating its location
                location.add(direction);

                // Check for collision with a block
                if (location.getBlock().getType().isSolid()) {
                    createExplosionEffect(location, player, damage);
                    cancel();
                    return;
                }

                // Check for collisions with entities
                List<Entity> nearbyEntities = (List<Entity>) location.getWorld().getNearbyEntities(location, 1, 1, 1);
                for (Entity entity : nearbyEntities) {
                    if (entity instanceof LivingEntity && !entity.equals(player)) {
                        // Apply AoE damage on impact
                        createExplosionEffect(location, player, damage);
                        cancel();
                        return;
                    }
                }

                // Spawn the fireball particles at the updated location
                player.getWorld().spawnParticle(Particle.FLAME, location, 15, 1, 1, 1, 0.1); // Small speed value for visual effect

                distanceTraveled++;
            }
        }.runTaskTimer(CerberusPlugin.getInstance(), 0, 1); // Runs every tick

        player.sendMessage("You have cast a Fireball spell!");
    }

    private void createExplosionEffect(Location location, Player caster, double damage) {
        // Play explosion sound and particle effect
        location.getWorld().playSound(location, Sound.ENTITY_GENERIC_EXPLODE, 1.0f, 1.0f);
        location.getWorld().spawnParticle(Particle.EXPLOSION, location, 10, 0.2, 0.2, 0.2, 0.2);

        // Apply AoE damage to all entities within the explosion radius
        List<Entity> nearbyEntities = (List<Entity>) location.getWorld().getNearbyEntities(location, explosionRadius, explosionRadius, explosionRadius);
        for (Entity entity : nearbyEntities) {
            if (entity instanceof LivingEntity && !entity.equals(caster)) {
                ((LivingEntity) entity).damage(damage, caster);
            }
        }
    }
}
