package GUIs.player;

import GUIs.menu.Menu;
import cerberus.world.cerb.CerberusPlugin;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.BiFunction;
import java.util.function.Function;

/**
 * The destinations shown on the Player Menu hub. A system becomes reachable by registering (or
 * re-registering) its section here: the hub reads this list, so a new gameplay system never needs
 * a hub rewrite. Sections are drawn in their slot; their availability is evaluated per player,
 * per render.
 */
public final class PlayerMenuSections {

    /**
     * @param slot         hub slot (rows 2-4 of the 6-row hub)
     * @param summary      what the section is, always shown
     * @param live         extra lines with the player's own data (nullable)
     * @param availability evaluated per player when the hub renders
     * @param opener       the screen to open; null when there is nothing to open yet
     */
    public record Section(String id, int slot, Material icon, String name, List<String> summary,
                          BiFunction<CerberusPlugin, Player, List<String>> live,
                          Function<Player, Availability> availability,
                          Function<CerberusPlugin, Menu> opener) {}

    private final List<Section> sections = new ArrayList<>();

    /** Add or replace (same id) a section. */
    public synchronized void register(Section s) {
        sections.removeIf(x -> x.id().equals(s.id()));
        sections.add(s);
    }

    public synchronized List<Section> all() {
        List<Section> copy = new ArrayList<>(sections);
        copy.sort(Comparator.comparingInt(Section::slot));
        return copy;
    }

    public synchronized Section byId(String id) {
        for (Section s : sections) if (s.id().equals(id)) return s;
        return null;
    }
}
