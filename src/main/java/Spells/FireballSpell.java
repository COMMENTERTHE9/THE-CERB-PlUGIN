package Spells;

import Listener.VirtualHealthListener;
import Manager.CustomDamageType;
import Manager.ManaType;
import Manager.PlayerManaManager;
import Manager.EffectManager;
import Manager.SpellDamageContext;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.Bukkit;
import org.bukkit.util.Vector;
import org.bukkit.Location;

import java.util.List;

/**
 * Fireball spell — launches a projectile that explodes on contact.
 * <p>
 * Now integrates with the custom damage pipeline via {@link SpellDamageContext}.
 * Before dealing damage, registers context so the {@link VirtualHealthListener}
 * routes it through CombatContext with the correct damage type, knockback, and
 * elemental tag.
 */
public class FireballSpell extends Spell {

    private final PlayerManaManager manaManager;
    private final EffectManager effectManager;
    private final org.bukkit.plugin.java.JavaPlugin plugin;
    private VirtualHealthListener virtualHealthListener;
    private final double baseManaCost = 20;
    private final double baseDamage = 10;
    private final double explosionRadius = 3.0;

    /** Spell damage context — PYROMANCY type, 1.5x knockback, fire element, no armor bypass. */
    private static final SpellDamageContext FIREBALL_CONTEXT = new SpellDamageContext(
            CustomDamageType.PYROMANCY,
            1.5,    // extra knockback for explosive feel
            "FIRE",
            false   // does not bypass armor
    );

    public FireballSpell(PlayerManaManager manaManager, EffectManager effectManager,
                         org.bukkit.plugin.java.JavaPlugin plugin, VirtualHealthListener virtualHealthListener) {
        super("Fireball");
        this.manaManager = manaManager;
        this.effectManager = effectManager;
        this.plugin = plugin;
        this.virtualHealthListener = virtualHealthListener;
    }

    public void setVirtualHealthListener(VirtualHealthListener listener) {
        this.virtualHealthListener = listener;
    }

    @Override
    public boolean cast(Player player) {
        double manaCost = baseManaCost;
        double damage = baseDamage;

        // Apply any skill-based mana cost reductions or damage bonuses
        effectManager.applyMagicSkillEffect(player, "Elemental Mastery");

        if (!manaManager.spendMana(player, ManaType.BASIC, manaCost)) {
            player.sendMessage("Not enough mana to cast Fireball!");
            return false;
        }

        Location startLocation = player.getEyeLocation();
        Vector direction = player.getLocation().getDirection().normalize().multiply(0.5);

        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_BLAZE_SHOOT, 1.0f, 1.0f);

        // Self-rescheduling fireball — each tick runs on the region owning the current position
        tickFireball(startLocation.clone(), direction, player, damage, 0, 50);

        player.sendMessage(net.kyori.adventure.text.Component.text("You cast Fireball!")
                .color(net.kyori.adventure.text.format.NamedTextColor.RED));
        return true;
    }

    /**
     * One tick of fireball travel. Runs on the region thread owning {@code loc}.
     * After processing, schedules the next tick on the (potentially different) region
     * that owns the new position. This lets the fireball cross region boundaries safely.
     */
    private void tickFireball(Location loc, Vector direction, Player caster,
                              double damage, int distance, int maxDistance) {
        // Schedule this tick on the region owning the fireball's current location
        Bukkit.getRegionScheduler().run(plugin, loc, task -> {
            if (distance >= maxDistance) return; // fizzle out

            loc.add(direction);

            // Block collision
            if (loc.getBlock().getType().isSolid()) {
                createExplosionEffect(loc, caster, damage);
                return;
            }

            // Entity collision
            for (Entity entity : loc.getWorld().getNearbyEntities(loc, 1, 1, 1)) {
                if (entity instanceof LivingEntity && !entity.equals(caster)) {
                    createExplosionEffect(loc, caster, damage);
                    return;
                }
            }

            // Trail particles — tight cluster for a visible fireball
            loc.getWorld().spawnParticle(Particle.FLAME, loc, 6, 0.1, 0.1, 0.1, 0.02);
            loc.getWorld().spawnParticle(Particle.SMOKE, loc, 2, 0.05, 0.05, 0.05, 0.01);

            // Schedule next tick (1 tick later) on whatever region owns the new position
            Bukkit.getRegionScheduler().runDelayed(plugin, loc, nextTask ->
                    tickFireball(loc.clone(), direction, caster, damage, distance + 1, maxDistance), 1L);
        });
    }

    /**
     * AoE explosion: particles + sound + damage to all nearby entities.
     * Registers {@link SpellDamageContext} for each target BEFORE dealing damage,
     * so the damage event routes through the full custom pipeline.
     */
    private void createExplosionEffect(Location location, Player caster, double damage) {
        location.getWorld().playSound(location, Sound.ENTITY_GENERIC_EXPLODE, 1.0f, 1.0f);
        location.getWorld().spawnParticle(Particle.EXPLOSION, location, 10, 0.2, 0.2, 0.2, 0.2);

        VirtualHealthListener listener = this.virtualHealthListener;

        List<Entity> nearbyEntities = (List<Entity>) location.getWorld()
                .getNearbyEntities(location, explosionRadius, explosionRadius, explosionRadius);
        for (Entity entity : nearbyEntities) {
            if (entity instanceof LivingEntity target && !entity.equals(caster)
                    && !isCastersPet(target, caster)) {
                // Register spell context BEFORE dealing damage — the listener picks it up
                if (listener != null) {
                    listener.registerSpellDamage(target.getEntityId(), FIREBALL_CONTEXT);
                }
                target.damage(damage, caster);

                // Set fire ticks for burn DOT flavor (3 seconds)
                target.setFireTicks(60);
            }
        }
    }

    /** Never burn the caster's own tamed animals (wolves, cats, parrots, horses...). */
    private static boolean isCastersPet(LivingEntity target, Player caster) {
        return target instanceof org.bukkit.entity.Tameable pet
                && pet.isTamed()
                && pet.getOwnerUniqueId() != null
                && pet.getOwnerUniqueId().equals(caster.getUniqueId());
    }
}
