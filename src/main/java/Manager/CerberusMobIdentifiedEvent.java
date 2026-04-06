package Manager;

import org.bukkit.entity.LivingEntity;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

/**
 * Fired by MobRegistry.resolve() whenever a non-NORMAL mob is identified.
 * Downstream systems (loot tables, achievements, quests) can listen for
 * this event without coupling directly to MobRegistry.
 */
public class CerberusMobIdentifiedEvent extends Event {

    private static final HandlerList HANDLERS = new HandlerList();

    private final LivingEntity entity;
    private final MobContext context;

    public CerberusMobIdentifiedEvent(LivingEntity entity, MobContext context) {
        this.entity = entity;
        this.context = context;
    }

    public LivingEntity getEntity() { return entity; }
    public MobContext getContext() { return context; }

    @Override
    public HandlerList getHandlers() { return HANDLERS; }
    public static HandlerList getHandlerList() { return HANDLERS; }
}
