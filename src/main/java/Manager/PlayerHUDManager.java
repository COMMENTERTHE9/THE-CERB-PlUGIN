package Manager;

import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;

public class PlayerHUDManager {
    private PlayerManaManager manaManager;
    private PlayerDefenseManager defenseManager;
    private PlayerVirtualHealthManager healthManager;
    private DefenseBarManager defenseBarManager;

    public PlayerHUDManager(PlayerManaManager manaManager, PlayerDefenseManager defenseManager, PlayerVirtualHealthManager healthManager) {
        this.manaManager = manaManager;
        this.defenseManager = defenseManager;
        this.healthManager = healthManager;
    }
    
    public void setDefenseBarManager(DefenseBarManager defenseBarManager) {
        this.defenseBarManager = defenseBarManager;
    }

    public void setDefenseManager(PlayerDefenseManager defenseManager) {
        this.defenseManager = defenseManager;
    }

    public void setVirtualHealthManager(PlayerVirtualHealthManager healthManager) {
        this.healthManager = healthManager;
    }

    public void setManaManager(PlayerManaManager manaManager) {
        this.manaManager = manaManager;
    }

    public void updateHUD(Player player) {
        // Get player's current stats
        double health = healthManager.getPlayerVirtualHealth(player); // Get virtual health
        double maxHealth = healthManager.getPlayerMaxVirtualHealth(player);  // Get max virtual health
        double mana = getMana(player);
        double maxMana = getMaxMana(player);
        double effectiveness = getDefenseEffectiveness(player);

        // Determine color based on health thresholds
        ChatColor healthColor = getHealthColor(health);
        ChatColor manaColor = getManaColor(mana);
        ChatColor effectivenessColor = getEffectivenessColor(effectiveness);

        // Build HUD string
        StringBuilder hudBuilder = new StringBuilder();
        
        // Add defense bar if available
        if (defenseBarManager != null) {
            DefenseBarManager.DefenseBar defenseBar = defenseBarManager.getDefenseBar(player);
            double defensePercentage = defenseBar.getPercentage();
            ChatColor defenseColor = getDefenseBarColor(defensePercentage);
            hudBuilder.append(String.format("%s%.0f/%.0f⛨  ", 
                defenseColor, defenseBar.getCurrent(), defenseBar.getMaximum()));
        }
        
        // Add health, effectiveness, and mana
        hudBuilder.append(String.format("%s%.0f/%.0f♥   %s%.2f%%★   %s%.2f/%.2f✎",
                healthColor, health, maxHealth,
                effectivenessColor, effectiveness * 100,
                manaColor, mana, maxMana));

        // Send the action bar message to the player
        player.spigot().sendMessage(ChatMessageType.ACTION_BAR, new TextComponent(hudBuilder.toString()));
    }

    private ChatColor getHealthColor(double health) {
        if (health > 260000) return ChatColor.WHITE; // Platinum
        if (health > 230000) return ChatColor.GOLD;  // Gold
        if (health > 200000) return ChatColor.GRAY;  // Silver
        if (health > 150000) return ChatColor.BLACK; // Bronze
        if (health > 100000) return ChatColor.YELLOW; // Yellow
        if (health > 50000) return ChatColor.BLUE;   // Blue
        return ChatColor.RED;                        // Red
    }

    private ChatColor getManaColor(double mana) {
        if (mana > 260000) return ChatColor.DARK_BLUE; // Platinum
        if (mana > 230000) return ChatColor.GOLD;      // Gold
        if (mana > 200000) return ChatColor.GRAY;      // Silver
        if (mana > 150000) return ChatColor.DARK_AQUA; // Teal
        if (mana > 100000) return ChatColor.GREEN;     // Light Green
        if (mana > 50000) return ChatColor.AQUA;       // Cyan
        return ChatColor.BLUE;                         // Light Blue
    }

    private ChatColor getEffectivenessColor(double effectiveness) {
        if (effectiveness > 0.9) return ChatColor.DARK_GREEN;   // Platinum
        if (effectiveness > 0.75) return ChatColor.GREEN;   // Gold
        if (effectiveness > 0.5) return ChatColor.GRAY;    // Silver
        if (effectiveness > 0.25) return ChatColor.DARK_RED;  // Bronze
        return ChatColor.BLACK;                              // Low effectiveness, Red
    }

    private double getDefenseEffectiveness(Player player) {
        // Retrieve the defense effectiveness from PlayerDefenseManager
        return defenseManager.getCurrentEffectiveness();
    }

    private double getMana(Player player) {
        // Retrieve actual mana value from PlayerManaManager
        return manaManager.getCurrentMana(player);
    }

    private double getMaxMana(Player player) {
        // Retrieve actual max mana value from PlayerManaManager
        return manaManager.getMaxMana(player);  // Pass the player object here
    }
    
    private ChatColor getDefenseBarColor(double percentage) {
        if (percentage > 0.75) return ChatColor.GREEN;
        if (percentage > 0.5) return ChatColor.YELLOW;
        if (percentage > 0.25) return ChatColor.GOLD;
        return ChatColor.RED;
    }
}
