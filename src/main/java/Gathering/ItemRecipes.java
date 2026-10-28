package Gathering;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.RecipeChoice;
import org.bukkit.inventory.ShapelessRecipe;
import org.bukkit.plugin.Plugin;

import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Crafting-table recipes for Cerberus items outside a skill's own section ({@code resources.yml recipes:}; the Sower's
 * Charm, docs/design/FARMING_COOKING.md §9): shapeless, the result the item itself, in every player's recipe book.
 * Registered at startup and on reload (the global region thread); a reload that changes no recipe leaves them alone.
 */
public final class ItemRecipes implements Listener {

    private final Plugin plugin;
    private final Set<NamespacedKey> keys = ConcurrentHashMap.newKeySet();
    /** What is registered now, so a reload that changes no recipe adds and removes none. */
    private volatile String registered = "";

    public ItemRecipes(Plugin plugin) {
        this.plugin = plugin;
    }

    /** (Re)register {@code c}'s recipes; players online see the new ones in their recipe book. */
    public void register(GatheringCatalog c) {
        StringBuilder sig = new StringBuilder();
        for (TackleRules.Recipe r : c.recipes().values()) sig.append(r).append(c.item(r.result())).append(';'); // the item it makes too
        if (sig.toString().equals(registered)) return;
        for (NamespacedKey k : keys) Bukkit.removeRecipe(k);
        keys.clear();
        for (TackleRules.Recipe r : c.recipes().values()) {
            GatheringCatalog.ItemDef def = c.item(r.result());
            if (def == null) continue;
            if (r.ingredients().stream().flatMap(List::stream).anyMatch(m -> !m.isItem())) { // a block such as carrots or water
                plugin.getLogger().warning("[Gathering] resources.yml recipes." + r.id() + ": an ingredient is not an item - recipe left out");
                continue;
            }
            NamespacedKey key = new NamespacedKey(plugin, "item_" + r.id());
            try {
                ShapelessRecipe sr = new ShapelessRecipe(key, GearItems.makeItem(def, r.makes()));
                for (List<Material> any : r.ingredients()) sr.addIngredient(new RecipeChoice.MaterialChoice(any));
                if (Bukkit.addRecipe(sr)) keys.add(key);
                else plugin.getLogger().warning("[Gathering] resources.yml recipes." + r.id() + ": the server refused the recipe - left out");
            } catch (RuntimeException ex) { // never the rest of startup or the reload for one recipe
                plugin.getLogger().warning("[Gathering] resources.yml recipes." + r.id() + ": " + ex.getMessage() + " - recipe left out");
            }
        }
        registered = sig.toString();
        for (Player p : Bukkit.getOnlinePlayers()) p.getScheduler().run(plugin, t -> discover(p), null);
    }

    /** True for one of these recipes. */
    public boolean isItemRecipe(org.bukkit.inventory.Recipe r) {
        return r instanceof org.bukkit.Keyed k && keys.contains(k.getKey());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent e) {
        discover(e.getPlayer());
    }

    private void discover(Player p) {
        if (!keys.isEmpty() && p.isOnline()) p.discoverRecipes(keys);
    }
}
