package Manager;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.ScoreboardManager;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class CombatScoreboardManager {

    private final Map<UUID, Scoreboard> playerScoreboards = new HashMap<>();
    private final ScoreboardManager scoreboardManager;

    public CombatScoreboardManager() {
        this.scoreboardManager = Bukkit.getScoreboardManager();
    }

    public void setCombatMode(Player player) {
        Scoreboard scoreboard = getOrCreateScoreboard(player);
        Objective objective = scoreboard.getObjective("combatMode");

        if (objective == null) {
            objective = scoreboard.registerNewObjective("combatMode", "dummy", ChatColor.DARK_RED + "Combat Mode");
            objective.setDisplaySlot(DisplaySlot.SIDEBAR);
        }

        // Placeholder for combat log
        objective.getScore(ChatColor.GREEN + "Combat Log:").setScore(10);  // Placeholder value

        // Placeholder for skill precognition
        objective.getScore(ChatColor.DARK_GREEN + "Skill Precognition:").setScore(5);  // Placeholder value

        // Placeholder for additional combat features
        objective.getScore(ChatColor.RED + "Future Attack:").setScore(3);  // Placeholder value

        player.setScoreboard(scoreboard);
    }

    public void setNormalMode(Player player) {
        Scoreboard scoreboard = getOrCreateScoreboard(player);
        Objective objective = scoreboard.getObjective("normalMode");

        if (objective == null) {
            objective = scoreboard.registerNewObjective("normalMode", "dummy", ChatColor.BLUE + "Normal Mode");
            objective.setDisplaySlot(DisplaySlot.SIDEBAR);
        }

        // Player's wallet (placeholder)
        objective.getScore(ChatColor.GOLD + "Wallet:").setScore(getPlayerWallet(player));  // Replace with actual wallet value

        // Player's level and XP
        objective.getScore(ChatColor.GREEN + "Level:").setScore(player.getLevel());
        objective.getScore(ChatColor.GREEN + "XP:").setScore((int) player.getExp());

        // Placeholder for additional normal mode features
        objective.getScore(ChatColor.GREEN + "Placeholder Feature:").setScore(1);

        player.setScoreboard(scoreboard);
    }

    private Scoreboard getOrCreateScoreboard(Player player) {
        UUID playerUUID = player.getUniqueId();
        if (!playerScoreboards.containsKey(playerUUID)) {
            Scoreboard scoreboard = scoreboardManager.getNewScoreboard();
            playerScoreboards.put(playerUUID, scoreboard);
        }
        return playerScoreboards.get(playerUUID);
    }

    public void clearScoreboard(Player player) {
        player.setScoreboard(scoreboardManager.getNewScoreboard());
    }

    public void removePlayerScoreboard(Player player) {
        playerScoreboards.remove(player.getUniqueId());
    }

    // Placeholder method for player's wallet
    private int getPlayerWallet(Player player) {
        // Replace with actual wallet retrieval logic
        return 100;  // Placeholder value
    }

    public void switchToCombatMode(Player player) {
        setCombatMode(player);
    }

    public void switchToNormalMode(Player player) {
        setNormalMode(player);
    }
}
