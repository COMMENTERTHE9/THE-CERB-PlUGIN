package cerberus.world.cerb.events;

import cerberus.world.cerb.events.PlayerDefenseUpdateEvent;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

public class PlayerDefenseUpdateEvent extends Event {
    private static final HandlerList HANDLERS = new HandlerList();

    private final Player player;
    private final double newDefense;

    public PlayerDefenseUpdateEvent(Player player, double newDefense) {
        this.player     = player;
        this.newDefense = newDefense;
    }

    /** The player whose defense changed */
    public Player getPlayer() {
        return player;
    }

    /** The new defense value */
    public double getNewDefense() {
        return newDefense;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
