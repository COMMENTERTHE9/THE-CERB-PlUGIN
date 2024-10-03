package Skills;

import org.bukkit.entity.Player;

public abstract class MagicSkill extends Skill {
    public MagicSkill(String name) {
        super(name);
    }

    @Override
    public void applyEffect(cerberus.world.cerb.CustomPlayer player) {

    }

    @Override
    public void applyEffect(Player player) {
        // Apply magic skill effect to player
    }
}