package Attributes;

import java.util.Locale;

/** What a hit was made with. Power can be general or limited to one class (Blade Mastery: swords). */
public enum WeaponClass {
    SWORD("Swords", "Sword"),
    AXE("Axes", "Axe"),
    MACE("Maces", "Mace"),
    TRIDENT("Tridents", "Trident"),
    SPEAR("Spears", "Spear"),
    BOW("Bows and crossbows", "Bow"),
    UNARMED("Unarmed", "Unarmed"),
    /** Anything else in hand (tools, blocks): only general Power applies. */
    OTHER("Other items", "Other item");

    public final String displayName;
    /** For "Sword damage", "Axe damage"... */
    public final String singular;

    WeaponClass(String displayName, String singular) {
        this.displayName = displayName;
        this.singular = singular;
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
