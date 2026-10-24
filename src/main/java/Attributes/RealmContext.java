package Attributes;

import Manager.Realm;

/**
 * A player's realm and its magnitude (POWER_SCALING.md §2.2: ×8 per realm from combat.yml's
 * realm curve, Awakened = ×1). The realm multiplies absolute quantities — damage, max health,
 * defense bar, regeneration, spell damage — while attributes shape the player inside the realm.
 */
public record RealmContext(Realm realm, double multiplier) {

    /** Awakened at ×1: what every player is until Ascension (breakthroughs) exists. */
    public static final RealmContext NEUTRAL = new RealmContext(Realm.AWAKENED, 1.0);

    public RealmContext {
        if (realm == null) realm = Realm.AWAKENED;
        if (!Double.isFinite(multiplier) || multiplier <= 0) multiplier = 1.0;
    }
}
