package Classes;

import org.bukkit.Material;
import org.bukkit.inventory.EquipmentSlot;

import java.util.EnumSet;
import java.util.Set;

/**
 * Right-clicks that stay vanilla even with a weapon in hand (docs/design/ABILITY_CONTROLS.md §6):
 * the off-hand item's own use (a shield blocks, food is eaten, a torch is placed) and an axe's
 * use on a block (strip a log, scrape or unwax copper). The rules are pure and unit-tested; the
 * item's facts that need the server's registry (food, a block, where it is worn) come in as
 * arguments, filled in by {@link #offHand(Material)} on the server.
 */
final class VanillaUse {

    private VanillaUse() {}

    /** What a right-click does with an item in the off hand, in vanilla. */
    enum OffHand {
        /** Nothing (empty, a totem, arrows, a map...): right-click stays an ability key. */
        NONE,
        /** It acts on the block you click (blocks, torches, buckets, tools, spawn eggs...). */
        ON_BLOCK,
        /** It is used wherever you aim (a shield, food, potions, throwables, a bow, armor...). */
        ALWAYS
    }

    /** Used wherever you aim, besides food, potions and armor. */
    private static final Set<Material> USED_ANYWHERE = EnumSet.of(
            Material.SHIELD, Material.SNOWBALL, Material.EGG, Material.ENDER_PEARL, Material.ENDER_EYE,
            Material.EXPERIENCE_BOTTLE, Material.WIND_CHARGE, Material.TRIDENT, Material.BOW, Material.CROSSBOW,
            Material.FISHING_ROD, Material.SPYGLASS, Material.GOAT_HORN, Material.WRITABLE_BOOK, Material.WRITTEN_BOOK,
            Material.KNOWLEDGE_BOOK, Material.MAP, Material.FIREWORK_ROCKET, Material.CARROT_ON_A_STICK,
            Material.WARPED_FUNGUS_ON_A_STICK, Material.MILK_BUCKET, Material.POTION, Material.SPLASH_POTION,
            Material.LINGERING_POTION, Material.OMINOUS_BOTTLE, Material.ELYTRA);

    /** Used on the block you click, besides placeable blocks. */
    private static final Set<Material> USED_ON_BLOCKS = EnumSet.of(
            Material.FLINT_AND_STEEL, Material.FIRE_CHARGE, Material.BONE_MEAL, Material.BRUSH, Material.SHEARS,
            Material.HONEYCOMB, Material.END_CRYSTAL, Material.ITEM_FRAME, Material.GLOW_ITEM_FRAME,
            Material.PAINTING, Material.ARMOR_STAND, Material.LEAD, Material.INK_SAC, Material.GLOW_INK_SAC,
            Material.BUCKET, Material.MINECART);

    /** On the server: the off-hand item's use, with its registry facts looked up. */
    static OffHand offHand(Material m) {
        if (m == null || air(m)) return OffHand.NONE;
        return offHand(m, m.isEdible(), m.isBlock(), m.getEquipmentSlot());
    }

    /**
     * The off-hand item's use. {@code edible}, {@code block} and {@code worn} are the item's facts
     * from the registry (worn: the slot a right-click puts it on, HAND when none).
     */
    static OffHand offHand(Material m, boolean edible, boolean block, EquipmentSlot worn) {
        if (m == null || air(m) || m == Material.TOTEM_OF_UNDYING) return OffHand.NONE;
        if (edible || USED_ANYWHERE.contains(m) || wearable(worn)) return OffHand.ALWAYS;
        String n = m.name();
        if (n.endsWith("_BUNDLE") || m == Material.BUNDLE || (n.endsWith("_EGG") && !n.endsWith("_SPAWN_EGG") && !block)) {
            return OffHand.ALWAYS; // bundles empty out; blue and brown eggs are thrown
        }
        if (block || USED_ON_BLOCKS.contains(m)) return OffHand.ON_BLOCK;
        if (n.endsWith("_BUCKET") || n.endsWith("_SPAWN_EGG") || n.endsWith("_BOAT") || n.endsWith("_RAFT")
                || n.endsWith("_MINECART") || n.endsWith("_DYE") || n.endsWith("_HOE") || n.endsWith("_SHOVEL")
                || n.endsWith("_AXE")) {
            return OffHand.ON_BLOCK;
        }
        return OffHand.NONE;
    }

    /** Armor and anything else a right-click puts on (a carved pumpkin, a head). */
    private static boolean wearable(EquipmentSlot s) {
        return s == EquipmentSlot.HEAD || s == EquipmentSlot.CHEST || s == EquipmentSlot.LEGS || s == EquipmentSlot.FEET;
    }

    private static boolean air(Material m) {
        return m == Material.AIR || m == Material.CAVE_AIR || m == Material.VOID_AIR;
    }

    /** True when an axe right-clicked on {@code block} does something: strips it, scrapes or unwaxes copper. */
    static boolean axeWorks(Material block) {
        if (block == null) return false;
        String n = block.name();
        if (n.startsWith("STRIPPED_")) return false;
        if (n.endsWith("_LOG") || n.endsWith("_WOOD") || n.endsWith("_STEM") || n.endsWith("_HYPHAE") || block == Material.BAMBOO_BLOCK) {
            return true;
        }
        return n.contains("COPPER") && (n.startsWith("WAXED_") || n.startsWith("EXPOSED_") || n.startsWith("WEATHERED_") || n.startsWith("OXIDIZED_"));
    }
}
