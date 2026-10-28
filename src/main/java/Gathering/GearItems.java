package Gathering;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * What a Cerberus item is, stored in the item's own data by id (docs/design/GATHERING.md): a custom
 * tool, a custom item, a forged quality. Names and lore are only for reading; everything that
 * counts (a tool's kind and tier, a quality's bonus) is looked up from these ids.
 */
public final class GearItems {

    public static final NamespacedKey TOOL = new NamespacedKey("cerberus", "tool");
    public static final NamespacedKey ITEM = new NamespacedKey("cerberus", "item");
    public static final NamespacedKey QUALITY = new NamespacedKey("cerberus", "quality");
    /**
     * The quality of a crop's produce, or of what a furnace made from it (docs/design/FARMING_COOKING.md §3.4). A key of its
     * own: forged gear's {@link #QUALITY} counts as equipment, produce never does.
     */
    public static final NamespacedKey CROP_QUALITY = new NamespacedKey("cerberus", "crop_quality");
    /** Upgrades made to the item (docs/design/WOODCUTTING.md §8): their ids, comma-separated, in the order made. */
    public static final NamespacedKey UPGRADES = new NamespacedKey("cerberus", "upgrades");

    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacySection();
    private static final PlainTextComponentSerializer PLAIN = PlainTextComponentSerializer.plainText();

    /** Plain text of every lore line {@link #setQuality} writes, so a new quality replaces the old lines. */
    private static final Set<String> QUALITY_LINES = new HashSet<>();
    static {
        for (Quality q : Quality.values()) {
            QUALITY_LINES.add(PLAIN.serialize(LEGACY.deserialize(header(q))));
            for (GatheringCatalog.Gear g : GatheringCatalog.Gear.values()) QUALITY_LINES.add(PLAIN.serialize(LEGACY.deserialize(raises(g))));
        }
    }

    private GearItems() {}

    /** The custom tool id an item carries, or null. Reads the item's data without copying it. */
    public static String toolId(ItemStack it) {
        return string(it, TOOL);
    }

    /** The custom item id an item carries, or null. */
    public static String itemId(ItemStack it) {
        return string(it, ITEM);
    }

    /** The forged quality an item carries, or null (never forged, or forged before qualities existed). */
    public static Quality quality(ItemStack it) {
        return Quality.parse(string(it, QUALITY));
    }

    /** The quality on produce or food (Fine or better), or null: Common produce is a plain item. */
    public static Quality cropQuality(ItemStack it) {
        return Quality.parse(string(it, CROP_QUALITY));
    }

    /**
     * Mark produce or food as {@code q}: the id in its data and one lore line ("✦ Fine quality"). Common leaves the item as
     * it is, so it stacks with any plain one; an earlier quality line is replaced, other lore kept.
     */
    public static void setCropQuality(ItemStack it, Quality q) {
        if (it == null || q == null || q == Quality.COMMON || it.getType().isAir()) return;
        it.editMeta(m -> {
            m.getPersistentDataContainer().set(CROP_QUALITY, PersistentDataType.STRING, q.id());
            List<Component> lore = m.lore() == null ? new ArrayList<>() : new ArrayList<>(m.lore());
            lore.removeIf(line -> QUALITY_LINES.contains(PLAIN.serialize(line)));
            lore.add(0, text(header(q)));
            m.lore(lore);
        });
    }

    private static String string(ItemStack it, NamespacedKey key) {
        if (it == null || it.getType().isAir()) return null;
        return it.getPersistentDataContainer().get(key, PersistentDataType.STRING);
    }

    /**
     * The tool as the gathering rules see it: a custom tool by its id, else a vanilla tool by its
     * material (also when its custom id is no longer in resources.yml); null when it is neither.
     */
    public static GatherRules.Tool tool(ItemStack it, GatheringCatalog c) {
        if (it == null || it.getType().isAir()) return null;
        GatheringCatalog.ToolDef d = c.tool(toolId(it));
        if (d != null) return new GatherRules.Tool(d.id(), d.kinds(), d.tier());
        return GatherRules.vanillaTool(it.getType().name());
    }

    /** A custom tool, carrying its id. */
    public static ItemStack makeTool(GatheringCatalog.ToolDef d) {
        ItemStack it = new ItemStack(d.material());
        it.editMeta(m -> {
            m.displayName(text("§b" + d.name()));
            m.lore(List.of(text("§7" + String.join(", ", d.kinds()) + " · tier " + d.tier())));
            m.getPersistentDataContainer().set(TOOL, PersistentDataType.STRING, d.id());
        });
        return it;
    }

    /** {@code amount} of a custom item, carrying its id. */
    public static ItemStack makeItem(GatheringCatalog.ItemDef d, int amount) {
        ItemStack it = new ItemStack(d.material(), Math.max(1, Math.min(d.material().getMaxStackSize(), amount)));
        it.editMeta(m -> {
            m.displayName(text("§f" + d.name()));
            if (!d.lore().isEmpty()) {
                List<Component> lore = new ArrayList<>();
                for (String line : d.lore()) lore.add(text("§7" + line));
                m.lore(lore);
            }
            m.getPersistentDataContainer().set(ITEM, PersistentDataType.STRING, d.id());
        });
        return it;
    }

    /**
     * A trophy made from one fish ({@code fish}, a copy: its material and look): named "Goldfin Trout · 3.21 kg" from the
     * notable catch's real weight, with who caught it, the date and the water in its lore, and {@code mark} (its species
     * and catch time) under {@code key} in its data instead of the species' item id, so it never stacks with another
     * trophy or counts as a fish of that species (docs/design/FISHING_EQUIPMENT.md §11).
     */
    public static ItemStack trophy(ItemStack fish, String name, double kg, String by, long at, String water, String mark, NamespacedKey key) {
        ItemStack out = fish.clone();
        out.setAmount(1);
        String date = java.time.LocalDate.ofInstant(java.time.Instant.ofEpochMilli(at), java.time.ZoneId.systemDefault()).toString();
        out.editMeta(m -> {
            m.getPersistentDataContainer().remove(ITEM);
            m.getPersistentDataContainer().set(key, PersistentDataType.STRING, mark);
            m.displayName(text("§6" + name + " §7· §f" + String.format(Locale.ROOT, kg < 10 ? "%.2f kg" : "%.1f kg", kg)));
            m.lore(List.of(text("§6✦ Trophy"), text("§7Caught by §f" + by), text("§7" + date + " · " + water)));
        });
        return out;
    }

    /**
     * Mark {@code it} as forged at {@code q}: the id in its data, and two lore lines saying which
     * attribute it raises (the amounts are on the Stats page, so the lore never goes out of date
     * when resources.yml changes). Lines from an earlier quality are replaced, other lore kept.
     */
    public static void setQuality(ItemStack it, Quality q) {
        if (it == null || q == null || it.getType().isAir()) return;
        GatheringCatalog.Gear g = GatheringCatalog.gear(it.getType().name());
        it.editMeta(m -> {
            m.getPersistentDataContainer().set(QUALITY, PersistentDataType.STRING, q.id());
            List<Component> lore = m.lore() == null ? new ArrayList<>() : new ArrayList<>(m.lore());
            lore.removeIf(line -> QUALITY_LINES.contains(PLAIN.serialize(line)));
            List<Component> ours = new ArrayList<>();
            ours.add(text(header(q)));
            if (q != Quality.COMMON && g != null) ours.add(text(raises(g)));
            lore.addAll(0, ours);
            m.lore(lore);
        });
    }

    /** Remove a forged quality (a crafting result left over after a batch: the next take rolls its own). */
    public static void clearQuality(ItemStack it) {
        if (it == null || it.getType().isAir() || quality(it) == null) return;
        it.editMeta(m -> {
            m.getPersistentDataContainer().remove(QUALITY);
            List<Component> lore = m.lore();
            if (lore == null) return;
            List<Component> kept = new ArrayList<>(lore);
            kept.removeIf(line -> QUALITY_LINES.contains(PLAIN.serialize(line)));
            m.lore(kept.isEmpty() ? null : kept);
        });
    }

    private static String header(Quality q) {
        return q.color + "✦ " + q.displayName + " quality";
    }

    private static String raises(GatheringCatalog.Gear g) {
        return "§7" + switch (g) {
            case WEAPON -> "Power while held";
            case TOOL -> "Fortune while held";
            case WEAPON_AND_TOOL -> "Power and Fortune while held";
            case ARMOR -> "Fortitude while worn";
        };
    }

    private static Component text(String legacy) {
        return LEGACY.deserialize(legacy).decoration(TextDecoration.ITALIC, false);
    }

    /** The upgrades an item carries, by id (empty when none). */
    public static List<String> upgrades(ItemStack it) {
        String s = string(it, UPGRADES);
        if (s == null || s.isBlank()) return List.of();
        List<String> out = new ArrayList<>();
        for (String id : s.split(",")) if (!id.isBlank()) out.add(id.trim());
        return List.copyOf(out);
    }

    /**
     * A copy of {@code it} with {@code u} made to it: the id in its data and one lore line ("✦ Heartwood grip: chopping
     * speed while held"; the amount is on the Stats page). Everything else (enchantments, damage, quality, name) is kept.
     * Null when it already has that upgrade or isn't of a kind it goes on.
     */
    public static ItemStack withUpgrade(ItemStack it, GatheringCatalog.UpgradeDef u) {
        if (it == null || u == null || !u.on().contains(kind(it.getType()))) return null;
        List<String> have = upgrades(it);
        if (have.contains(u.id())) return null;
        ItemStack out = it.clone();
        out.setAmount(1);
        List<String> ids = new ArrayList<>(have);
        ids.add(u.id());
        out.editMeta(m -> {
            m.getPersistentDataContainer().set(UPGRADES, PersistentDataType.STRING, String.join(",", ids));
            List<Component> lore = m.lore() == null ? new ArrayList<>() : new ArrayList<>(m.lore());
            lore.add(upgradeLine(u));
            m.lore(lore);
        });
        return out;
    }

    /**
     * A copy of {@code it} without the upgrades {@code ids} (docs/design/FISHING_EQUIPMENT.md §6, converting an old rod):
     * their ids leave its data and their lore lines (as {@link #withUpgrade} wrote them) leave its lore. Enchantments,
     * damage, name, quality, other lore and other data are kept.
     */
    public static ItemStack withoutUpgrades(ItemStack it, java.util.Collection<String> ids, GatheringCatalog c) {
        ItemStack out = it.clone();
        List<String> keep = new ArrayList<>(upgrades(it));
        keep.removeAll(ids);
        Set<String> lines = new HashSet<>();
        for (String id : ids) {
            GatheringCatalog.UpgradeDef u = c.upgrade(id);
            if (u != null && u.stat() != null) lines.add(PLAIN.serialize(upgradeLine(u)));
        }
        out.editMeta(m -> {
            if (keep.isEmpty()) m.getPersistentDataContainer().remove(UPGRADES);
            else m.getPersistentDataContainer().set(UPGRADES, PersistentDataType.STRING, String.join(",", keep));
            List<Component> lore = m.lore();
            if (lore == null) return;
            List<Component> kept = new ArrayList<>(lore);
            kept.removeIf(line -> lines.contains(PLAIN.serialize(line)));
            m.lore(kept.isEmpty() ? null : kept);
        });
        return out;
    }

    private static Component upgradeLine(GatheringCatalog.UpgradeDef u) {
        return text("§6✦ " + u.name() + "§7: " + u.stat().displayName.toLowerCase(Locale.ROOT) + " while held");
    }

    /** What kind of item a material is, for upgrades: axe, pickaxe, shovel, hoe, sword, spear, bow, crossbow, trident, mace; or null. */
    public static String kind(org.bukkit.Material m) {
        if (m == null) return null;
        String n = m.name();
        if (n.endsWith("_PICKAXE")) return "pickaxe";
        if (n.endsWith("_AXE")) return "axe";
        if (n.endsWith("_SHOVEL")) return "shovel";
        if (n.endsWith("_HOE")) return "hoe";
        if (n.endsWith("_SWORD")) return "sword";
        if (n.endsWith("_SPEAR")) return "spear";
        return switch (n) {
            case "BOW" -> "bow";
            case "CROSSBOW" -> "crossbow";
            case "TRIDENT" -> "trident";
            case "MACE" -> "mace";
            case "FISHING_ROD" -> "rod";
            default -> null;
        };
    }

    /** "iron_pickaxe" → "Iron pickaxe" (for labels). */
    static String label(String material) {
        String s = material.toLowerCase(Locale.ROOT).replace('_', ' ');
        return s.isEmpty() ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }
}
