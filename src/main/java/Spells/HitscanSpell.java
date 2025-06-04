package Spells;

import cerberus.world.cerb.util.HitscanUtil;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.util.RayTraceResult;

import java.util.function.Predicate;

public abstract class HitscanSpell {
    private final double maxRange;
    private final Predicate<LivingEntity> targetFilter;

    protected HitscanSpell(double maxRange, Predicate<LivingEntity> targetFilter) {
        this.maxRange     = maxRange;
        this.targetFilter = targetFilter;
    }

    /**
     * Subclasses implement what happens on hit:
     */
    protected abstract void onHit(Player caster, LivingEntity target, Location hitPoint);

    /**
     * Call this to cast the spell.
     */
    public void cast(Player caster) {
        // 1) Ray trace for first matching living entity
        RayTraceResult result = HitscanUtil.rayTraceEntity(
                caster,
                maxRange,
                e -> e instanceof LivingEntity && e != caster && targetFilter.test((LivingEntity)e)
        );

        if (result != null && result.getHitEntity() instanceof LivingEntity live) {
            // 2) spawn an impact effect
            caster.getWorld().spawnParticle(
                    Particle.EXPLOSION,
                    result.getHitPosition().toLocation(caster.getWorld()),
                    1,0,0,0,0
            );
            // 3) delegate to subclass
            onHit(caster, live, result.getHitPosition().toLocation(caster.getWorld()));
        } else {
        }
        }
    }
