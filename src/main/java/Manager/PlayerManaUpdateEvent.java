package Manager;

import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;
import org.bukkit.event.player.PlayerEvent;

public class PlayerManaUpdateEvent extends PlayerEvent {
    private static final HandlerList handlers = new HandlerList();
    private final double newMana;

    public PlayerManaUpdateEvent(Player who, double newMana) {
        super(who);
        this.newMana = newMana;
    }

    public double getNewMana() {
        return newMana;
    }

    public static HandlerList getHandlerList() {
        return handlers;
    }

    @Override
    public HandlerList getHandlers() {
        return handlers;
    }
}
