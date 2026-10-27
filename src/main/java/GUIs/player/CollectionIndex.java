package GUIs.player;

import GUIs.menu.Menu;
import cerberus.world.cerb.CerberusPlugin;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.BiFunction;

/**
 * Every collection the Player Menu's Collections index lists (docs/design/FISHING_EQUIPMENT.md §2): fish species, fishing
 * treasure, sea creatures defeated, woods. Each reads its progress from the player's existing records and its totals from
 * the definitions (resources.yml), and opens the same screen its skill page opens. A later system adds a collection by
 * registering one here.
 */
public final class CollectionIndex {

    /** How far a player is: {@code have} of {@code total}; {@code available} false when its system is off on this server. */
    public record Progress(long have, long total, boolean available) {
        public static final Progress OFF = new Progress(0, 0, false);

        public boolean complete() { return available && total > 0 && have >= total; }
    }

    /**
     * @param skill    the skill it belongs to (shown on the tile)
     * @param progress the viewer's progress, read on their thread from memory
     * @param opener   the collection's screen (the same one its skill page opens)
     */
    public record Entry(String id, String name, Material icon, int order, String skill, List<String> summary,
                        BiFunction<CerberusPlugin, Player, Progress> progress, BiFunction<CerberusPlugin, Player, Menu> opener) {}

    private final List<Entry> entries = new ArrayList<>();

    /** Add or replace (same id) a collection. */
    public synchronized void register(Entry e) {
        entries.removeIf(x -> x.id().equals(e.id()));
        entries.add(e);
    }

    public synchronized List<Entry> all() {
        List<Entry> copy = new ArrayList<>(entries);
        copy.sort(Comparator.comparingInt(Entry::order));
        return copy;
    }

    public synchronized Entry byId(String id) {
        for (Entry e : entries) if (e.id().equals(id)) return e;
        return null;
    }

    /** The progress of {@code e} for {@code p}, never failing the screen (a broken system reads as off). */
    public static Progress safe(Entry e, CerberusPlugin plugin, Player p) {
        try {
            Progress pr = e.progress().apply(plugin, p);
            return pr == null ? Progress.OFF : pr;
        } catch (RuntimeException ex) {
            return Progress.OFF;
        }
    }
}
