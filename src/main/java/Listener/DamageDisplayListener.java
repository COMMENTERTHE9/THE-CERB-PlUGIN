package Listener;

import Manager.CustomDamageType;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.entity.Display.Billboard;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.Particle;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.event.EventPriority;


import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

public class DamageDisplayListener implements Listener {
    private final JavaPlugin plugin;
    private final Random random = new Random();
    private final Map<UUID, CombatData> combatData = new HashMap<>();

    private static class CombatData {
        double totalDamage;
        int hitCount;
        long lastHitTime;
        double dps;
    }

    public DamageDisplayListener(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    // ------------------------------------------------------------
// Damage‑number handler (pattern‑aligned)
// ------------------------------------------------------------
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)   // flag + priority
    public void onEntityDamage(EntityDamageByEntityEvent event) {

        /* ---------- EARLY‑EXIT FILTERS ---------- */
        if (!(event.getEntity() instanceof LivingEntity)) return;   // not a living mob/player
        if (event.getDamage() <= 0) return;                         // no real damage

        // ---------- Original logic ----------
        LivingEntity      entity      = (LivingEntity) event.getEntity();
        double            damage      = event.getDamage();
        CustomDamageType  damageType  = determineDamageType(event);
        boolean           isCritical  = isCriticalHit(event);

        updateCombatStats(event.getDamager().getUniqueId(), damage);

        Location spawnLoc = getRandomizedLocation(entity);
        showDamageNumber(spawnLoc, damage, damageType, isCritical);

        showComboIfRelevant(event.getDamager().getUniqueId(),
                spawnLoc.clone().add(0, 0.5, 0));

        playDamageSound(entity.getLocation(), damageType, isCritical);

        spawnDamageEffects(entity.getLocation(), damageType, damage);
    }


    private void showDamageNumber(Location location, double damage, CustomDamageType type, boolean isCritical) {
        TextDisplay display = location.getWorld().spawn(location, TextDisplay.class, textDisplay -> {
            Component text = formatDamageText(damage, type, isCritical);
            textDisplay.text(text);
            textDisplay.setBillboard(Billboard.CENTER);
            textDisplay.setDefaultBackground(false);
            textDisplay.setShadowed(true);
            textDisplay.setViewRange(16.0f);
            textDisplay.setPersistent(false);
        });

        animateDisplay(display, location.clone(), isCritical);
    }

    private Component formatDamageText(double damage, CustomDamageType type, boolean isCritical) {
        String numberText = String.format("%.1f", damage);
        if (isCritical) {
            numberText = "✯ " + numberText + " ✯";
        }
        return Component.text(type.getSymbol() + numberText)
                .color(type.getColor())
                .decoration(TextDecoration.BOLD, isCritical);
    }

    private void animateDisplay(TextDisplay display, Location startLoc, boolean isCritical) {
        new BukkitRunnable() {
            private int ticks = 0;
            private final int displayTime = isCritical ? 30 : 20;  // Changed to non-static final
            private double horizontalOffset = 0;

            @Override
            public void run() {
                if (ticks >= displayTime || !display.isValid()) {
                    display.remove();
                    cancel();
                    return;
                }

                double progress = (double) ticks / displayTime;  // Use displayTime instead
                double height = Math.sin(progress * Math.PI) * (isCritical ? 0.8 : 0.5);

                if (isCritical) {
                    horizontalOffset = Math.sin(progress * Math.PI * 4) * 0.1;
                }

                Location newLoc = startLoc.clone().add(horizontalOffset, height, 0);
                display.teleport(newLoc);

                if (ticks > displayTime * 0.7) {
                    display.setVisibleByDefault(ticks % 2 == 0);
                }

                ticks++;
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    private Location getRandomizedLocation(LivingEntity entity) {
        return entity.getLocation().add(
                (random.nextDouble() - 0.5) * 0.5,
                entity.getHeight() + 0.5,
                (random.nextDouble() - 0.5) * 0.5
        );
    }

    private void updateCombatStats(UUID attackerId, double damage) {
        CombatData data = combatData.computeIfAbsent(attackerId, k -> new CombatData());
        long currentTime = System.currentTimeMillis();

        // Reset if more than 5 seconds since last hit
        if (currentTime - data.lastHitTime > 5000) {
            data.totalDamage = 0;
            data.hitCount = 0;
            data.dps = 0;
        }

        data.totalDamage += damage;
        data.hitCount++;
        data.lastHitTime = currentTime;
        data.dps = data.totalDamage / ((currentTime - data.lastHitTime) / 1000.0);
    }

    private void showComboIfRelevant(UUID attackerId, Location location) {
        CombatData data = combatData.get(attackerId);
        if (data != null && data.hitCount > 2) {
            String comboText = String.format("%d Hit Combo! (%.1f DPS)",
                    data.hitCount, data.dps);

            TextDisplay comboDisplay = location.getWorld().spawn(location, TextDisplay.class, display -> {
                display.text(Component.text(comboText, NamedTextColor.GOLD));
                display.setBillboard(Billboard.CENTER);
                display.setDefaultBackground(false);
            });

            // Remove after 1 second
            new BukkitRunnable() {
                @Override
                public void run() {
                    comboDisplay.remove();
                }
            }.runTaskLater(plugin, 20L);
        }
    }

    private void playDamageSound(Location location, CustomDamageType type, boolean isCritical) {
        Sound sound = switch (type) {
            case MANA_DRAIN, MANA_BURN -> Sound.BLOCK_BEACON_DEACTIVATE;
            case FIRE, BURN_DOT -> Sound.ENTITY_GENERIC_BURN;
            case FREEZE, FREEZE_DOT -> Sound.BLOCK_GLASS_BREAK;
            default -> isCritical ? Sound.ENTITY_PLAYER_ATTACK_CRIT : Sound.ENTITY_PLAYER_ATTACK_STRONG;
        };

        location.getWorld().playSound(location, sound, 1.0f, isCritical ? 1.2f : 1.0f);
    }

    private void spawnDamageEffects(Location location, CustomDamageType type, double damage) {
        Particle particle = switch (type) {
            case MANA_DRAIN, MANA_BURN -> Particle.DRAGON_BREATH;
            case FIRE, BURN_DOT -> Particle.FLAME;
            case FREEZE, FREEZE_DOT -> Particle.SNOWFLAKE;
            case MAGIC, CUSTOM_MAGIC -> Particle.WITCH;
            default -> Particle.CRIT;
        };

        location.getWorld().spawnParticle(particle, location.add(0, 1, 0),
                (int)(damage * 2), 0.3, 0.3, 0.3, 0.1);
    }

    private boolean isCriticalHit(EntityDamageByEntityEvent event) {
        // Add your critical hit logic here
        return event.getDamage() > 10.0; // Example threshold
    }

    private CustomDamageType determineDamageType(EntityDamageByEntityEvent event) {
        // Add your damage type determination logic here
        return CustomDamageType.ENTITY_ATTACK; // Default type
    }
}