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

    /** "iron_pickaxe" → "Iron pickaxe" (for labels). */
    static String label(String material) {
        String s = material.toLowerCase(Locale.ROOT).replace('_', ' ');
        return s.isEmpty() ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }
}
