package Skills;

import cerberus.world.cerb.CustomPlayer;
import org.bukkit.entity.Player;

public abstract class CombatSkill extends Skill {

    public CombatSkill(String name) {
        super(name);
    }

    // Abstract method to apply effects specific to CustomPlayer
    public abstract void applyEffect(CustomPlayer player);

    // Abstract method to apply effects specific to Bukkit Player
    @Override
    public abstract void applyEffect(Player player);
}
