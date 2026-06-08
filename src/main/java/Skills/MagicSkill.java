package Skills;

import org.bukkit.entity.Player;

public abstract class MagicSkill extends Skill {
    public MagicSkill(String name) {
        super(name);
    }

    @Override
    public void applyEffect(Player player) {
        // Default no-op — subclasses override
    }
}
