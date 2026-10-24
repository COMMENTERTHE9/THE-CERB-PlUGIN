package Attributes;

import java.util.Objects;

/**
 * One source's share of an attribute or stat, e.g. "Blade Mastery 50 → Power +50 (swords)".
 * Exactly one of {@code attribute} / {@code stat} is set. {@code weapon} null = every weapon
 * (only meaningful for Power and the DAMAGE stat). The label is what players and admins see in
 * breakdowns.
 *
 * @param sourceId stable id of the source that produced it ("skill:Blade Mastery", "buff:dev")
 */
public record Contribution(String sourceId, String label, Attribute attribute, Stat stat,
                           double amount, WeaponClass weapon) {

    public Contribution {
        Objects.requireNonNull(sourceId, "sourceId");
        Objects.requireNonNull(label, "label");
        if ((attribute == null) == (stat == null)) {
            throw new IllegalArgumentException("A contribution targets exactly one attribute or stat");
        }
    }

    public static Contribution attribute(String sourceId, String label, Attribute a, double amount, WeaponClass weapon) {
        return new Contribution(sourceId, label, a, null, amount, weapon);
    }

    public static Contribution stat(String sourceId, String label, Stat s, double amount, WeaponClass weapon) {
        return new Contribution(sourceId, label, null, s, amount, weapon);
    }

    /** True when this applies to a hit made with {@code c}. */
    public boolean appliesTo(WeaponClass c) {
        return weapon == null || weapon == c;
    }
}
