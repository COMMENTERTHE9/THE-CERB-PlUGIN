package Classes;

import java.util.Locale;
import java.util.Set;

/**
 * What an ability needs in hand (docs/design/CLASSES.md §4): a family of weapons, never a class.
 * Read from the vanilla item until custom weapon archetypes exist; each archetype later maps onto
 * one of these, so abilities never change.
 */
public enum WeaponFamily {
    ANY, BLADE, AXE, POLEARM, BLUNT, UNARMED, BOW, CROSSBOW, FOCUS,
    /** Anything else in hand (tools, blocks): only abilities that take ANY. */
    OTHER;

    /** The family of the item in hand, by material name ("NETHERITE_SWORD", "AIR"). Never null. */
    public static WeaponFamily ofMaterial(String material) {
        if (material == null) return UNARMED;
        String m = material.toUpperCase(Locale.ROOT);
        if (m.equals("AIR") || m.equals("CAVE_AIR") || m.equals("VOID_AIR")) return UNARMED;
        if (m.endsWith("_SWORD")) return BLADE;
        if (m.endsWith("_AXE")) return AXE;              // not PICKAXE: that ends in "KAXE"
        if (m.endsWith("_SPEAR") || m.equals("TRIDENT")) return POLEARM;
        if (m.equals("MACE")) return BLUNT;
        if (m.equals("BOW")) return BOW;
        if (m.equals("CROSSBOW")) return CROSSBOW;
        if (m.equals("BLAZE_ROD") || m.equals("BREEZE_ROD") || m.equals("STICK")) return FOCUS; // wands until custom foci
        return OTHER;
    }

    /** True when an ability needing one of {@code needed} (empty = anything) can be used with {@code inHand}. */
    public static boolean fits(Set<WeaponFamily> needed, WeaponFamily inHand) {
        return needed.isEmpty() || needed.contains(ANY) || needed.contains(inHand);
    }

    /** "blade" / "BLADE" -> BLADE; null when unknown. */
    public static WeaponFamily parse(String s) {
        if (s == null || s.isBlank()) return null;
        try {
            return valueOf(s.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /**
     * "a blade", "an axe": for "needs a blade" messages. A focus names the items that are one, so a
     * player told "needs a focus" knows what to hold.
     */
    public String withArticle() {
        return switch (this) {
            case BLUNT -> "a blunt weapon";
            case UNARMED -> "an empty hand";
            case FOCUS -> "a focus (blaze rod, breeze rod or stick)";
            default -> {
                String w = name().toLowerCase(Locale.ROOT);
                yield (w.matches("^[aeiou].*") ? "an " : "a ") + w;
            }
        };
    }
}
