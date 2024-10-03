package Manager;

import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;
import org.bukkit.event.player.PlayerEvent;

public class PlayerDefenseUpdateEvent extends PlayerEvent {
    private static final HandlerList handlers = new HandlerList();
    private final double newDefense;

    public PlayerDefenseUpdateEvent(Player who, double newDefense) {
        super(who);
        this.newDefense = newDefense;
    }

    public double getNewDefense() {
        return newDefense;
    }

    public static HandlerList getHandlerList() {
        return handlers;
    }

    @Override
    public HandlerList getHandlers() {
        return handlers;
    }
}
