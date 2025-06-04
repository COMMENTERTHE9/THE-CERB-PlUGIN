package Spells;

import org.bukkit.entity.Player;

public abstract class Spell {
    private final String name;

    public Spell(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }

    // Abstract method to be implemented by each specific spell
    public abstract void cast(Player player);
}
