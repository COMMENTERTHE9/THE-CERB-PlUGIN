package Manager;

import org.bukkit.ChatColor;

/**
 * The ten Realms of Ascension: the player-facing display layer over {@link Tier}
 * (docs/design/NAMING.md, canonical; Realm ≡ Tier, 1:1). Magnitudes come from the
 * realm-base curve in combat.yml (×8 per realm), not from this enum.
 */
public enum Realm {
    MORTAL(Tier.WEAK, 1, "Mortal", "Stirring"),
    AWAKENED(Tier.COMMON, 2, "Awakened", "Vesselborn"),
    ADEPT(Tier.UNCOMMON, 3, "Adept", "Wellspring"),
    CHAMPION(Tier.RARE, 4, "Champion", "Tidecaller"),
    ASCENDANT(Tier.EPIC, 5, "Ascendant", "Embersoul"),
    PARAGON(Tier.LEGENDARY, 6, "Paragon", "Rimeheart"),
    TRANSCENDENT(Tier.PERFECT, 7, "Transcendent", "Mirebound"),
    DEMIGOD(Tier.MYTHIC, 8, "Demigod", "Ashen Lord"),
    EMPYREAN(Tier.GODLY, 9, "Empyrean", "Maw-Eater"),
    PRIMORDIAL(Tier.PRIMORDIAL, 10, "Primordial", "Primordial Echo");

    private final Tier tier;
    /** R1..R10. */
    public final int number;
    public final String displayName;
    /** Optional flavor subtitle. */
    public final String epithet;

    Realm(Tier tier, int number, String displayName, String epithet) {
        this.tier = tier;
        this.number = number;
        this.displayName = displayName;
        this.epithet = epithet;
    }

    public Tier tier() { return tier; }

    public ChatColor color() { return tier.displayColor; }

    /** Steps from Awakened: Mortal −1, Awakened 0 … Primordial 8 (POWER_SCALING §3.1 realmIndex). */
    public int index() { return number - 2; }

    public static Realm of(Tier tier) {
        for (Realm r : values()) if (r.tier == tier) return r;
        return AWAKENED;
    }

    /** Parse "champion", "RARE", "R4" or "4"; null when unknown. */
    public static Realm parse(String s) {
        if (s == null) return null;
        String t = s.trim().toUpperCase(java.util.Locale.ROOT);
        if (t.startsWith("R")) t = t.substring(1);
        for (Realm r : values()) {
            if (r.name().equals(t) || r.tier.name().equals(t) || String.valueOf(r.number).equals(t)) return r;
        }
        return null;
    }
}
