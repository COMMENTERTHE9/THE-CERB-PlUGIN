package Skills;

import cerberus.world.cerb.CustomPlayer;
import org.bukkit.Bukkit;          // <<< NEW
import org.bukkit.entity.Player;
import java.util.UUID;

public abstract class UtilitySkill extends Skill {
    private final SkillManager skillManager;

    public UtilitySkill(String name, SkillManager skillManager) {
        super(name);
        this.skillManager = skillManager;
    }


    /** If you ever need to call it with a Bukkit Player object */
    public void applyEffect(Player player) {
        applyEffect(player.getUniqueId());
    }

    /** New: lookup by UUID and delegate to your CustomPlayer logic */
    public void applyEffect(UUID playerId) {
        Player player = Bukkit.getPlayer(playerId);
        if (player != null) applyEffect(player);
    }
}
