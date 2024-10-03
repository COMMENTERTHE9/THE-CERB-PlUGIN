package Skills;

import org.bukkit.entity.Player;

public abstract class UtilitySkill extends Skill {
    public UtilitySkill(String name) {
        super(name);
    }

    @Override
    public void applyEffect(cerberus.world.cerb.CustomPlayer player) {

    }

    @Override
    public void applyEffect(Player player) {
        // Apply utility skill effect to player
    }
}