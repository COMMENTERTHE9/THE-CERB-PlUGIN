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

    /** Convenience constructor for subclasses that don't need SkillManager. */
    public UtilitySkill(String name) {
        super(name);
        this.skillManager = null;
    }


    /**
     * Default no-op for UtilitySkill — subclasses override to provide behavior.
     * Most utility skills are passive and don't need per-tick effects.
     */
    public void applyEffect(Player player) {
        // No-op by default; subclasses override as needed
    }

    /** Convenience: lookup by UUID and delegate */
    public void applyEffect(UUID playerId) {
        Player player = Bukkit.getPlayer(playerId);
        if (player != null) applyEffect(player);
    }
}
