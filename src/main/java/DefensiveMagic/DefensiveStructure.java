package DefensiveMagic;

import org.bukkit.entity.Player;

public abstract class DefensiveStructure {
    protected final Player player;

    public DefensiveStructure(Player player) {
        this.player = player;
    }

    public abstract void activate();

    public abstract double getManaCost();
}
