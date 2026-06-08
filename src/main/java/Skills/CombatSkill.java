package Skills;

import org.bukkit.entity.Player;

public abstract class CombatSkill extends Skill {

    public CombatSkill(String name) {
        super(name);
    }

    @Override
    public abstract void applyEffect(Player player);
}
