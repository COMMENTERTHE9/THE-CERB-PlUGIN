package Listener;

import eu.decentsoftware.holograms.api.DHAPI;
import eu.decentsoftware.holograms.api.holograms.Hologram;
import org.bukkit.Location;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.LivingEntity;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.concurrent.atomic.AtomicInteger;

public class DamageDisplayListener implements Listener {

    private final JavaPlugin plugin;
    private static final AtomicInteger hologramCounter = new AtomicInteger();

    public DamageDisplayListener(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onEntityDamage(EntityDamageByEntityEvent event) {
        if (event.getEntity() instanceof LivingEntity) {
            LivingEntity entity = (LivingEntity) event.getEntity();
            double damage = event.getDamage();

            // Show damage number with unique hologram ID
            showDamageNumber(entity.getLocation(), damage);

            // Show health above the entity with unique hologram ID
            updateHealthHologram(entity);
        }
    }

    private void showDamageNumber(Location location, double damage) {
        int id = hologramCounter.incrementAndGet();
        String hologramId = "damage-" + id;
        Hologram hologram = DHAPI.createHologram(hologramId, location.add(0, 2.5, 0), true);
        DHAPI.addHologramLine(hologram, String.format("§c-%.2f", damage)); // Red color and damage number

        // Move the hologram upwards and remove it after the movement is complete
        new BukkitRunnable() {
            int ticks = 0;

            @Override
            public void run() {
                if (ticks >= 20) { // Runs for 20 ticks (1 second)
                    DHAPI.removeHologram(hologramId);
                    cancel();
                    return;
                }
                Location currentLocation = hologram.getLocation();
                DHAPI.moveHologram(hologram, currentLocation.add(0, 0.05, 0));
                ticks++;
            }
        }.runTaskTimer(plugin, 0L, 1L); // Move up every tick
    }

    private void updateHealthHologram(LivingEntity entity) {
        int id = hologramCounter.incrementAndGet();
        String hologramId = "health-" + id;
        Location location = entity.getLocation().add(0, 2.5, 0);
        Hologram healthHologram = DHAPI.createHologram(hologramId, location, true);

        String healthText = String.format("§aHealth: %.2f/%.2f", entity.getHealth(), entity.getAttribute(Attribute.GENERIC_MAX_HEALTH).getValue());
        DHAPI.addHologramLine(healthHologram, healthText);

        // Keep updating the hologram position and text
        new BukkitRunnable() {
            @Override
            public void run() {
                if (!entity.isValid()) {
                    DHAPI.removeHologram(hologramId);
                    cancel();
                    return;
                }
                DHAPI.moveHologram(healthHologram, entity.getLocation().add(0, 2.5, 0));
                DHAPI.setHologramLine(healthHologram, 0, String.format("§aHealth: %.2f/%.2f", entity.getHealth(), entity.getAttribute(Attribute.GENERIC_MAX_HEALTH).getValue()));
            }
        }.runTaskTimer(plugin, 0L, 20L); // Update every second
    }
}
