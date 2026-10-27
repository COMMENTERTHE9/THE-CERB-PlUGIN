package Gathering;

import org.bukkit.Bukkit;
import org.bukkit.Keyed;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.block.CrafterCraftEvent;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.inventory.FurnaceBurnEvent;
import org.bukkit.event.inventory.FurnaceSmeltEvent;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.inventory.CraftingInventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.Recipe;
import org.bukkit.inventory.RecipeChoice;
import org.bukkit.inventory.ShapelessRecipe;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * Upgrades made in a crafting grid (docs/design/WOODCUTTING.md §8): a tool or weapon and one custom item (heartwood) make
 * the same tool with the upgrade, keeping its enchantments, damage, quality and name. The result is worked out for the
 * grid in front of the player and checked again when it is taken; a crafter block never makes one. It also keeps every
 * Cerberus item out of vanilla's own recipes, crafters, furnaces and the ground (heartwood looks like stripped wood, but
 * it is never planks, charcoal, fuel or a block).
 */
public final class Upgrades implements Listener {

    private final Plugin plugin;
    private final Supplier<GatheringCatalog> catalog;
    private final Set<NamespacedKey> keys = ConcurrentHashMap.newKeySet();
    /** Materials custom items are made of: a grid without one can't hold a Cerberus item or make an upgrade. */
    private volatile Set<Material> customMaterials = EnumSet.noneOf(Material.class);
    /** What is registered now, so a reload that changes no upgrade adds and removes no recipe. */
    private volatile String registered = "";

    public Upgrades(Plugin plugin, Supplier<GatheringCatalog> catalog) {
        this.plugin = plugin;
        this.catalog = catalog;
    }

    /**
     * (Re)register one shapeless recipe per upgrade: an item of its kinds and its custom item's material. Startup or
     * reload (the global region thread); a reload that changes no upgrade leaves the recipes alone.
     */
    public void register(GatheringCatalog c) {
        Set<Material> mats = EnumSet.noneOf(Material.class);
        for (GatheringCatalog.ItemDef d : c.items().values()) mats.add(d.material());
        customMaterials = mats;
        StringBuilder sig = new StringBuilder();
        for (GatheringCatalog.UpgradeDef u : c.upgrades().values()) {
            GatheringCatalog.ItemDef item = c.item(u.item());
            sig.append(u.id()).append(u.on()).append(item == null ? "-" : item.material().name()).append(';');
        }
        if (sig.toString().equals(registered)) return;
        for (NamespacedKey k : keys) Bukkit.removeRecipe(k);
        keys.clear();
        for (GatheringCatalog.UpgradeDef u : c.upgrades().values()) {
            GatheringCatalog.ItemDef item = c.item(u.item());
            List<Material> on = materials(u.on());
            if (item == null || on.isEmpty()) continue;
            NamespacedKey key = new NamespacedKey(plugin, "upgrade_" + u.id());
            // The real result is worked out per grid. The recipe's own is the custom item's plain material, so a path that
            // ever skipped that (a crafter, another plugin) would only turn a tool and the item into the item: nothing gained.
            ShapelessRecipe r = new ShapelessRecipe(key, new ItemStack(item.material()));
            r.addIngredient(new RecipeChoice.MaterialChoice(on));
            r.addIngredient(new RecipeChoice.MaterialChoice(item.material()));
            if (Bukkit.addRecipe(r)) keys.add(key);
        }
        registered = sig.toString();
    }

    /** True when a grid holds something of a custom item's material: the only grids this listener has to look at. */
    private boolean mayHoldCustom(ItemStack[] grid) {
        Set<Material> mats = customMaterials;
        if (mats.isEmpty()) return false;
        for (ItemStack it : grid) if (it != null && mats.contains(it.getType())) return true;
        return false;
    }

    /** Every item material of these kinds (axe → all axes). */
    static List<Material> materials(Set<String> kinds) {
        List<Material> out = new ArrayList<>();
        for (Material m : Material.values()) {
            if (m.isLegacy() || !m.isItem()) continue;
            if (kinds.contains(GearItems.kind(m))) out.add(m);
        }
        return out;
    }

    /**
     * A Cerberus item that must stay out of vanilla's uses: every custom item except those marked {@code vanilla} (a fish
     * species is eaten and cooked like any fish).
     */
    boolean guarded(ItemStack it) {
        String id = GearItems.itemId(it);
        if (id == null) return false;
        GatheringCatalog.ItemDef d = catalog.get().item(id);
        return d == null || !d.vanilla();
    }

    /** True for one of this plugin's upgrade recipes. */
    public boolean isUpgrade(Recipe r) {
        return r instanceof Keyed k && keys.contains(k.getKey());
    }

    /**
     * What a grid holding exactly one tool or weapon and one custom item makes: the tool with the upgrade that item
     * makes to that kind, or null (anything else in the grid, no such upgrade, or the tool has it already).
     */
    ItemStack upgraded(ItemStack[] matrix) {
        GatheringCatalog c = catalog.get();
        ItemStack tool = null;
        String item = null;
        for (ItemStack it : matrix) {
            if (it == null || it.getType().isAir()) continue;
            String id = GearItems.itemId(it);
            if (id != null) {
                if (item != null) return null;
                item = id;
            } else {
                if (tool != null) return null;
                tool = it;
            }
        }
        if (tool == null || item == null) return null;
        String kind = GearItems.kind(tool.getType());
        for (GatheringCatalog.UpgradeDef u : c.upgrades().values()) {
            if (u.item().equals(item) && u.on().contains(kind)) return GearItems.withUpgrade(tool, u);
        }
        return null;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPrepare(PrepareItemCraftEvent e) {
        CraftingInventory inv = e.getInventory();
        // Every grid change in every crafting grid lands here, and getRecipe() builds a whole recipe (docs/PERFORMANCE.md):
        // look at the materials first. An upgrade or a Cerberus item both need a custom item's material in the grid.
        if (!mayHoldCustom(inv.getMatrix())) return;
        if (isUpgrade(e.getRecipe())) {
            inv.setResult(upgraded(inv.getMatrix()));
            return;
        }
        for (ItemStack it : inv.getMatrix()) {
            if (guarded(it)) { inv.setResult(null); return; } // never planks, sticks or a vanilla recipe's part
        }
    }

    /** Taking the result: the grid must still make exactly it (a stale or edited result never passes). */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onCraft(CraftItemEvent e) {
        CraftingInventory inv = e.getInventory();
        if (!mayHoldCustom(inv.getMatrix())) return;
        if (isUpgrade(e.getRecipe())) {
            ItemStack expected = upgraded(inv.getMatrix());
            ItemStack shown = inv.getResult();
            if (expected == null || shown == null || !expected.isSimilar(shown)) e.setCancelled(true);
            return;
        }
        for (ItemStack it : inv.getMatrix()) if (guarded(it)) { e.setCancelled(true); return; }
    }

    /** A Cerberus item is never placed as a block (it would lose what it is). */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent e) {
        if (guarded(e.getItemInHand())) e.setCancelled(true);
    }

    /** ...nor burnt as fuel... */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBurn(FurnaceBurnEvent e) {
        if (guarded(e.getFuel())) e.setCancelled(true);
    }

    /** ...nor smelted (heartwood would make charcoal): it stays in the furnace. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onSmelt(FurnaceSmeltEvent e) {
        if (guarded(e.getSource())) e.setCancelled(true);
    }

    /**
     * A crafter block fires neither of the crafting-grid events above, and would hand out a recipe's own result: it never
     * makes an upgrade (a player does, at a crafting table), and never crafts with a Cerberus item. The crafter's block
     * region thread.
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onCrafter(CrafterCraftEvent e) {
        if (isUpgrade(e.getRecipe())) { e.setCancelled(true); return; }
        if (customMaterials.isEmpty() || !(e.getBlock().getState(false) instanceof org.bukkit.block.Crafter crafter)) return;
        ItemStack[] grid = crafter.getInventory().getContents();
        if (!mayHoldCustom(grid)) return;
        for (ItemStack it : grid) if (guarded(it)) { e.setCancelled(true); return; }
    }
}
