package Manager;

import org.bukkit.ChatColor;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Implements the Defense Bar system from the Cerb roadmap.
 * Formula: flat / (toughness × effectiveness) gives a starting pool.
 * Bar drains on each hit and regenerates over time.
 */
public class DefenseBarManager {
    private final JavaPlugin plugin;
    private final PlayerDefenseManager defenseManager;
    private final Map<UUID, DefenseBar> playerDefenseBars = new HashMap<>();
    
    // Configuration values (can be loaded from config)
    private double baseDefensePool = 1000.0; // Base defense pool
    private double drainMultiplier = 1.0; // How much damage drains from bar
    private double regenRate = 10.0; // Defense regeneration per second
    private double regenDelay = 3.0; // Seconds before regen starts after damage
    private boolean showBarInActionBar = true;
    
    public DefenseBarManager(JavaPlugin plugin, PlayerDefenseManager defenseManager) {
        this.plugin = plugin;
        this.defenseManager = defenseManager;
        loadConfig();
    }
    
    private void loadConfig() {
        FileConfiguration config = plugin.getConfig();
        baseDefensePool = config.getDouble("defense-bar.base-pool", 1000.0);
        drainMultiplier = config.getDouble("defense-bar.drain-multiplier", 1.0);
        regenRate = config.getDouble("defense-bar.regen-rate", 10.0);
        regenDelay = config.getDouble("defense-bar.regen-delay", 3.0);
        showBarInActionBar = config.getBoolean("defense-bar.show-in-action-bar", true);
    }
    
    /**
     * Get or create a defense bar for a player
     */
    public DefenseBar getDefenseBar(Player player) {
        return playerDefenseBars.computeIfAbsent(player.getUniqueId(), 
            uuid -> new DefenseBar(player));
    }
    
    /**
     * Apply damage to the defense bar. Returns remaining damage after absorption.
     */
    public double applyDamageToBar(Player player, double damage) {
        DefenseBar bar = getDefenseBar(player);
        double absorbed = bar.drain(damage * drainMultiplier);
        
        // Schedule regeneration after delay
        bar.scheduleRegeneration();
        
        // Update display if enabled
        if (showBarInActionBar) {
            updateActionBar(player, bar);
        }
        
        // Return unabsorbed damage
        return Math.max(0, damage - absorbed);
    }
    
    /**
     * Update the action bar display for defense
     */
    private void updateActionBar(Player player, DefenseBar bar) {
        String display = formatDefenseBar(bar);
        // This will be handled by PlayerHUDManager integration
        player.sendActionBar(display);
    }
    
    /**
     * Format the defense bar for display
     */
    private String formatDefenseBar(DefenseBar bar) {
        double percentage = bar.getPercentage();
        int barLength = 20;
        int filled = (int) (barLength * percentage);
        
        StringBuilder display = new StringBuilder();
        display.append(ChatColor.GRAY).append("Defense [");
        
        // Color based on percentage
        ChatColor color;
        if (percentage > 0.75) color = ChatColor.GREEN;
        else if (percentage > 0.5) color = ChatColor.YELLOW;
        else if (percentage > 0.25) color = ChatColor.GOLD;
        else color = ChatColor.RED;
        
        display.append(color);
        for (int i = 0; i < filled; i++) {
            display.append("█");
        }
        display.append(ChatColor.DARK_GRAY);
        for (int i = filled; i < barLength; i++) {
            display.append("█");
        }
        
        display.append(ChatColor.GRAY).append("] ");
        display.append(color).append(String.format("%.0f/%.0f", bar.current, bar.maximum));
        
        return display.toString();
    }
    
    /**
     * Clean up when player leaves
     */
    public void removePlayer(Player player) {
        DefenseBar bar = playerDefenseBars.remove(player.getUniqueId());
        if (bar != null) {
            bar.cancelRegeneration();
        }
    }
    
    /**
     * Inner class representing a player's defense bar
     */
    public class DefenseBar {
        private final Player player;
        private double current;
        private double maximum;
        private double effectiveness = 1.0;
        private double toughness = 1.0;
        private BukkitRunnable regenTask;
        private long lastDamageTime = 0;
        
        public DefenseBar(Player player) {
            this.player = player;
            recalculateMaximum();
            this.current = this.maximum;
        }
        
        /**
         * Recalculate maximum defense based on formula: flat / (toughness × effectiveness)
         */
        public void recalculateMaximum() {
            // Get values from PlayerDefenseManager
            double baseDefense = defenseManager.getBaseDefense(player);
            this.effectiveness = defenseManager.getCurrentEffectiveness();
            this.toughness = Math.max(0.1, baseDefense / 10.0); // Prevent division by zero
            
            // Apply formula from roadmap
            this.maximum = baseDefensePool / (toughness * effectiveness);
            
            // Ensure current doesn't exceed new maximum
            if (current > maximum) {
                current = maximum;
            }
        }
        
        /**
         * Drain defense bar by damage amount. Returns amount actually drained.
         */
        public double drain(double amount) {
            lastDamageTime = System.currentTimeMillis();
            double drained = Math.min(current, amount);
            current -= drained;
            
            // Reduce effectiveness when bar is low
            if (getPercentage() < 0.25) {
                defenseManager.setDefense(player, defenseManager.getDefense(player) * 0.9);
            }
            
            return drained;
        }
        
        /**
         * Start regeneration after delay
         */
        public void scheduleRegeneration() {
            cancelRegeneration();
            
            regenTask = new BukkitRunnable() {
                @Override
                public void run() {
                    // Check if enough time has passed since last damage
                    if (System.currentTimeMillis() - lastDamageTime < regenDelay * 1000) {
                        return;
                    }
                    
                    // Regenerate defense
                    current = Math.min(maximum, current + regenRate / 20.0); // Divided by 20 for per-tick
                    
                    // Update display
                    if (showBarInActionBar && player.isOnline()) {
                        updateActionBar(player, DefenseBar.this);
                    }
                    
                    // Stop regeneration when full
                    if (current >= maximum) {
                        this.cancel();
                    }
                }
            };
            regenTask.runTaskTimer(plugin, (long)(regenDelay * 20), 1L);
        }
        
        /**
         * Cancel ongoing regeneration
         */
        public void cancelRegeneration() {
            if (regenTask != null && !regenTask.isCancelled()) {
                regenTask.cancel();
                regenTask = null;
            }
        }
        
        /**
         * Get current percentage of defense bar
         */
        public double getPercentage() {
            return maximum > 0 ? current / maximum : 0;
        }
        
        public double getCurrent() { return current; }
        public double getMaximum() { return maximum; }
        public void setCurrent(double value) { current = Math.max(0, Math.min(maximum, value)); }
    }
}