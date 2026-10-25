package Manager;

import org.bukkit.entity.Creaking;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Tameable;

/**
 * What class abilities may affect while PvP is off (docs/ROADMAP.md → PvP): never a player, nor
 * what belongs to one, and nothing that can't be hurt (it would pay class XP for hits that never
 * land). Shared by the ability engine and the Fireball spell's ability form. Call on the region
 * thread that owns the entity.
 */
public final class AbilityTargets {

    private AbilityTargets() {}

    /** A tamed animal with an owner (anyone's pet), or a mount a player is riding. */
    public static boolean belongsToAPlayer(Entity e) {
        if (e instanceof Tameable t && t.isTamed() && t.getOwnerUniqueId() != null) return true;
        for (Entity rider : e.getPassengers()) {
            if (rider instanceof Player) return true;
        }
        return false;
    }

    /** Damage can't land: an invulnerable entity, or a Creaking bound to its heart (it ignores every hit). */
    public static boolean unhurtable(Entity e) {
        return e.isInvulnerable() || (e instanceof Creaking c && c.getHome() != null);
    }
}
