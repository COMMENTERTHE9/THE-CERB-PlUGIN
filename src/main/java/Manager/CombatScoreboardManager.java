package Manager;

import org.bukkit.entity.Player;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Combat/Normal mode sidebar scoreboard.
 * Disabled on Folia — ScoreboardManager.getNewScoreboard() is unsupported
 * in regionalized threading. Sidebar display will be reimplemented using
 * the action bar HUD or Adventure API when needed.
 */
public class CombatScoreboardManager {

    public CombatScoreboardManager() {
    }

    public void setCombatMode(Player player) {
        // No-op on Folia
    }

    public void setNormalMode(Player player) {
        // No-op on Folia
    }

    public void clearScoreboard(Player player) {
        // No-op on Folia
    }

    public void removePlayerScoreboard(Player player) {
        // No-op on Folia
    }

    public void switchToCombatMode(Player player) {
        // No-op on Folia
    }

    public void switchToNormalMode(Player player) {
        // No-op on Folia
    }
}
