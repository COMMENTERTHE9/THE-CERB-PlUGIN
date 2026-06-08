package Manager;

public record DamageResolution(
        double finalDamage,
        boolean lethal,
        double absorbedByDefense,
        double remainingHp
) {
    public static DamageResolution noDamage(double remainingHp) {
        return new DamageResolution(0.0, false, 0.0, remainingHp);
    }
}
