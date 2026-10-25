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

    /** Per player: the last bar sent and when, the last luck figure with its text, and a message shown in the bar. */
    private static final class Sent {
        String text = "";
        long at; // System.nanoTime()
        double effectiveness = Double.NaN;
        String effectivenessText = "";
        String flash;       // legacy-coloured text shown in the middle of the bar, or null
        long flashUntil;    // System.nanoTime()
        String xp;          // the XP line: the same place, lower priority (it waits while a message shows)
        long xpUntil;       // System.nanoTime()
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
        
        // Add health, effectiveness (or a message in its place), and mana
        // Realm magnitudes reach billions: exact below 10,000, compact above (Numbers.hud).
        long now = System.nanoTime(); // monotonic: a clock step back must not silence the bar
        if (last.flash != null && now - last.flashUntil >= 0) last.flash = null;
        if (last.xp != null && now - last.xpUntil >= 0) last.xp = null;
        hudBuilder.append(healthColor).append(Numbers.hud(health)).append('/').append(Numbers.hud(maxHealth)).append("♥   ");
        if (last.flash != null) hudBuilder.append(last.flash).append("   ");
        else if (last.xp != null) hudBuilder.append(last.xp).append("   ");
        else hudBuilder.append(effectivenessColor).append(last.effectivenessText);
        hudBuilder.append(manaColor).append(Numbers.hud(mana)).append('/').append(Numbers.hud(maxMana)).append('✎');

        String text = hudBuilder.toString();
        if (text.equals(last.text) && now - last.at < RESEND_MS * 1_000_000L) return;
        last.text = text;
        last.at = now;
        // Adventure straight to the client (the old Spigot path round-tripped the text through JSON).
        player.sendActionBar(LegacyComponentSerializer.legacySection().deserialize(text));
    }

    /**
     * Show {@code legacyText} (section-sign colours) in the middle of the HUD bar for {@code ms}: an
     * ability's result or why a cast was refused. Health and Essence stay around it, and the HUD's
     * own refreshes keep it until it ends, so it is never overwritten a moment after it appears
     * (the action bar only shows one text at a time). Player's thread.
     */
    public void flash(Player player, String legacyText, long ms) {
        Sent s = sent.computeIfAbsent(player.getUniqueId(), id -> new Sent());
        long now = System.nanoTime();
        long shownUntil = s.flash != null && s.flashUntil - now > 0 ? s.flashUntil : now;
        s.flash = legacyText;
        s.flashUntil = now + Math.max(0, ms) * 1_000_000L;
        // An XP line waits while the message shows: it keeps the time it had left for after it.
        if (s.xp != null && s.xpUntil - shownUntil > 0) s.xpUntil = s.flashUntil + (s.xpUntil - shownUntil);
        updateHUD(player);
    }

    /**
     * Show the XP line (the real-time XP display) in the same place as {@link #flash} messages, for
     * {@code ms}: below them in priority, so an ability's result or a refusal is never covered, and
     * an XP line that arrives during one shows once it ends. Empty text clears it. Player's thread.
     */
    public void xp(Player player, String legacyText, long ms) {
        Sent s = sent.computeIfAbsent(player.getUniqueId(), id -> new Sent());
        if (legacyText == null || legacyText.isEmpty()) {
            if (s.xp == null) return;
            s.xp = null;
        } else {
            long now = System.nanoTime();
            long start = s.flash != null && s.flashUntil - now > 0 ? s.flashUntil : now;
            s.xp = legacyText;
            s.xpUntil = start + Math.max(0, ms) * 1_000_000L;
        }
        updateHUD(player);
    }

    /** End a message early (a combo that timed out). Player's thread. */
    public void clearFlash(Player player) {
        Sent s = sent.get(player.getUniqueId());
        if (s == null || s.flash == null) return;
        long now = System.nanoTime();
        // An XP line waiting behind the message moves up by the time the message no longer takes.
        if (s.xp != null && s.flashUntil - now > 0 && s.xpUntil - s.flashUntil > 0) s.xpUntil -= s.flashUntil - now;
        s.flash = null;
        updateHUD(player);
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
