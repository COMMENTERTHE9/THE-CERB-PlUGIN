package Manager;

import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class PlayerHUDManager {
    /**
     * The action bar fades after about three seconds; the HUD task resends it every second, so an
     * unchanged bar sent less than this long ago is not sent again (hits refresh it many times a second).
     */
    private static final long RESEND_MS = 900;

    /** Per player: the last bar sent and when, and the last luck figure with its text. */
    private static final class Sent {
        String text = "";
        long at;
        double effectiveness = Double.NaN;
        String effectivenessText = "";
    }

    private final Map<UUID, Sent> sent = new ConcurrentHashMap<>();

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
        if (player == null || !player.isOnline() || player.isDead()) return;
        if (healthManager == null || manaManager == null || defenseManager == null) return;

        HealthStateSnapshot healthState = healthManager.snapshot(player);
        double health = healthState.currentHp();
        double maxHealth = healthState.maxHp();
        double mana = getMana(player);
        double maxMana = getMaxMana(player);
        double effectiveness = getDefenseEffectiveness(player);

        health = Math.max(0, Math.min(health, Math.max(maxHealth, 0)));
        mana = Math.max(0, Math.min(mana, Math.max(maxMana, 0)));

        // Determine color based on percentage thresholds
        double healthPct = (maxHealth > 0) ? health / maxHealth : 0;
        double manaPct = (maxMana > 0) ? mana / maxMana : 0;
        ChatColor healthColor = getHealthColor(healthPct);
        ChatColor manaColor = getManaColor(manaPct);
        ChatColor effectivenessColor = getEffectivenessColor(effectiveness);

        Sent last = sent.computeIfAbsent(player.getUniqueId(), id -> new Sent());
        if (effectiveness != last.effectiveness) {               // rarely changes: format it once
            last.effectiveness = effectiveness;
            last.effectivenessText = String.format("%.2f%%★   ", effectiveness * 100);
        }

        // Build HUD string
        StringBuilder hudBuilder = new StringBuilder();
        
        // Add defense bar if available
        if (defenseBarManager != null) {
            DefenseBarManager.DefenseBar defenseBar = defenseBarManager.getDefenseBar(player);
            double defensePercentage = defenseBar.getPercentage();
            ChatColor defenseColor = getDefenseBarColor(defensePercentage);
            hudBuilder.append(defenseColor).append(Numbers.hud(defenseBar.getCurrent())).append('/')
                    .append(Numbers.hud(defenseBar.getMaximum())).append("⛨  ");
        }
        
        // Add health, effectiveness, and mana
        // Realm magnitudes reach billions: exact below 10,000, compact above (Numbers.hud).
        hudBuilder.append(healthColor).append(Numbers.hud(health)).append('/').append(Numbers.hud(maxHealth)).append("♥   ")
                .append(effectivenessColor).append(last.effectivenessText)
                .append(manaColor).append(Numbers.hud(mana)).append('/').append(Numbers.hud(maxMana)).append('✎');

        String text = hudBuilder.toString();
        long now = System.currentTimeMillis();
        if (text.equals(last.text) && now - last.at < RESEND_MS) return;
        last.text = text;
        last.at = now;
        // Adventure straight to the client (the old Spigot path round-tripped the text through JSON).
        player.sendActionBar(LegacyComponentSerializer.legacySection().deserialize(text));
    }

    /** Forget a player's HUD state (on quit). */
    public void removePlayer(UUID playerId) {
        sent.remove(playerId);
    }

    private ChatColor getHealthColor(double healthPct) {
        if (healthPct > 0.90) return ChatColor.GREEN;
        if (healthPct > 0.75) return ChatColor.DARK_GREEN;
        if (healthPct > 0.50) return ChatColor.YELLOW;
        if (healthPct > 0.25) return ChatColor.GOLD;
        if (healthPct > 0.10) return ChatColor.RED;
        return ChatColor.DARK_RED;
    }

    private ChatColor getManaColor(double manaPct) {
        if (manaPct > 0.90) return ChatColor.AQUA;
        if (manaPct > 0.75) return ChatColor.DARK_AQUA;
        if (manaPct > 0.50) return ChatColor.BLUE;
        if (manaPct > 0.25) return ChatColor.DARK_BLUE;
        if (manaPct > 0.10) return ChatColor.DARK_PURPLE;
        return ChatColor.GRAY;
    }

    private ChatColor getEffectivenessColor(double effectiveness) {
        if (effectiveness > 0.9) return ChatColor.DARK_GREEN;   // Platinum
        if (effectiveness > 0.75) return ChatColor.GREEN;   // Gold
        if (effectiveness > 0.5) return ChatColor.GRAY;    // Silver
        if (effectiveness > 0.25) return ChatColor.DARK_RED;  // Bronze
        return ChatColor.BLACK;                              // Low effectiveness, Red
    }

    private double getDefenseEffectiveness(Player player) {
        // Armor reduction (0.0–0.40) scaled to 0.0–1.0 for HUD color display
        return defenseManager.getArmorReduction(player) / 0.40;
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
