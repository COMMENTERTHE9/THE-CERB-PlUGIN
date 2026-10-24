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

    /**
     * Cast the spell.
     *
     * @return true if the spell was actually cast; false if it fizzled before casting (e.g. not
     *         enough mana). Callers award cast XP only on true.
     */
    public abstract boolean cast(Player player);
}
