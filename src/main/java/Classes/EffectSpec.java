package Classes;

import Attributes.Attribute;
import Attributes.Stat;

/**
 * One verb of an ability (docs/design/CLASSES.md §5.2), as data. The engine knows how to perform
 * each verb once; abilities are combinations of verbs with numbers, so new content is config.
 * Numbers are validated when the catalog loads ({@link ClassCatalog}).
 */
public sealed interface EffectSpec {

    /** Where a damage or status effect lands. */
    enum Shape { STRIKE, CONE, CIRCLE }

    /** Which attribute scales a damage effect: weapon hits use Power, spells use Essence. */
    enum Scale { POWER, ESSENCE }

    /**
     * Damage in {@code shape} (STRIKE: the target in front, CONE: in front within {@code angle}
     * degrees, CIRCLE: all around), {@code coefficient} x a basic hit, {@code hits} times
     * {@code intervalTicks} apart, at most {@code maxTargets} targets each time. {@code element}
     * null = physical. {@code perMomentum} adds that share per Momentum stack spent.
     */
    record Damage(Shape shape, double range, double angle, double coefficient, Scale scale, int maxTargets,
                  int hits, int intervalTicks, String element, double perMomentum) implements EffectSpec {}

    /** Dash forward {@code distance} blocks; the first enemy met is stunned {@code stunSeconds}. */
    record Dash(double distance, double stunSeconds) implements EffectSpec {}

    /** Teleport up to {@code distance} blocks where you look (never into blocks). */
    record Blink(double distance) implements EffectSpec {}

    /** A status on enemies in {@code shape} (slow, weakness...): {@code type} is a vanilla potion effect. */
    record Status(String type, double seconds, int amplifier, Shape shape, double range, int maxTargets) implements EffectSpec {}

    /** A timed bonus on yourself: exactly one of {@code attribute} / {@code stat} (fractions for stats). */
    record Buff(Attribute attribute, Stat stat, double amount, double seconds) implements EffectSpec {}

    /** Gain ({@code delta} > 0, per target hit when {@code perTarget}) or spend ({@code delta} < 0, or all when {@code spendAll}) a class resource. */
    record Resource(String kind, int delta, int max, boolean perTarget, boolean spendAll) implements EffectSpec {}

    /**
     * A counter window: for {@code windowMs} the next melee hit you take is negated and answered at
     * {@code coefficient} x a basic hit. A success shortens the cooldown to {@code successCooldownMs}.
     */
    record Counter(long windowMs, double coefficient, long successCooldownMs) implements EffectSpec {}

    /** Your next weapon hit within {@code seconds} is a critical hit. */
    record Empower(double seconds) implements EffectSpec {}

    /** Cast an existing spell by name ("Fireball"): its own damage, cost and rules apply. */
    record Spell(String name) implements EffectSpec {}
}
