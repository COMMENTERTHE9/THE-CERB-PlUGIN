package Attributes;

import java.util.Locale;

/** What a hit was made with. Power can be general or limited to one class (Blade Mastery: swords). */
public enum WeaponClass {
    SWORD("Swords"),
    AXE("Axes"),
    MACE("Maces"),
    TRIDENT("Tridents"),
    SPEAR("Spears"),
    BOW("Bows and crossbows"),
    UNARMED("Unarmed"),
    /** Anything else in hand (tools, blocks): only general Power applies. */
    OTHER("Other items");

    public final String displayName;

    WeaponClass(String displayName) {
        this.displayName = displayName;
    }

    /** Classify by material name (e.g. "NETHERITE_SWORD", "AIR"). Never null. */
    public static WeaponClass ofMaterial(String material) {
        if (material == null) return UNARMED;
        String m = material.toUpperCase(Locale.ROOT);
        if (m.equals("AIR") || m.equals("CAVE_AIR") || m.equals("VOID_AIR")) return UNARMED;
        if (m.endsWith("_SWORD")) return SWORD;
        if (m.endsWith("_AXE")) return AXE;           // not PICKAXE: that ends in "KAXE"
        if (m.endsWith("_SPEAR")) return SPEAR;
        if (m.equals("MACE")) return MACE;
        if (m.equals("TRIDENT")) return TRIDENT;
        if (m.equals("BOW") || m.equals("CROSSBOW")) return BOW;
        return OTHER;
    }

    /** Parse a config name ("sword", "bow"); null when unknown or blank (= all weapons). */
    public static WeaponClass parse(String s) {
        if (s == null || s.isBlank() || s.equalsIgnoreCase("all")) return null;
        try {
            return valueOf(s.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
