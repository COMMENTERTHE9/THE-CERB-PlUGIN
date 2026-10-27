package GUIs.player;

import GUIs.menu.Menu;
import cerberus.world.cerb.CerberusPlugin;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiFunction;

/**
 * The section tiles under a skill's page (docs/design/FISHING_EQUIPMENT.md §2): Fishing's Equipment, Journal and
 * Encounters, Woodcutting's Woods. A skill gets a tile by registering one here, only when it has real functionality and
 * progress to show; the page lays out whatever is registered. Each tile is drawn from the viewer's own in-memory data
 * on every render (no database), and opens its screen on top of the skill page, so Back returns there.
 */
public final class SkillSections {

    /**
     * @param tile   the tile for this viewer now, or null to leave the section out (its system is off)
     * @param opener the screen it opens, or null when it only shows information
     */
    public record Section(String id, String skill, int order, BiFunction<CerberusPlugin, Player, ItemStack> tile,
                          BiFunction<CerberusPlugin, Player, Menu> opener) {}

    /**
     * A skill's summary tile in the page's bottom row (Fishing's "since your last visit"): the tile and what a click does
     * (null tile: none now). The click gets whether it was a right-click.
     */
    public record Summary(BiFunction<CerberusPlugin, Player, ItemStack> tile, SummaryClick click) {}

    @FunctionalInterface
    public interface SummaryClick {
        void clicked(CerberusPlugin plugin, Player player, boolean right, Menu from);
    }

    private final List<Section> sections = new ArrayList<>();
    private final Map<String, Summary> summaries = new ConcurrentHashMap<>();

    /** Add or replace (same id) a section. */
    public synchronized void register(Section s) {
        sections.removeIf(x -> x.id().equals(s.id()));
        sections.add(s);
    }

    /** {@code skill}'s sections, in order. */
    public synchronized List<Section> forSkill(String skill) {
        List<Section> out = new ArrayList<>();
        for (Section s : sections) if (s.skill().equals(skill)) out.add(s);
        out.sort(Comparator.comparingInt(Section::order));
        return out;
    }

    public void registerSummary(String skill, Summary s) {
        summaries.put(skill, s);
    }

    public Summary summary(String skill) {
        return summaries.get(skill);
    }

    /** The slots of {@code n} section tiles centred in the page's section row (row 3: slots 28-34). */
    public static int[] slots(int n) {
        return switch (Math.max(0, Math.min(4, n))) {
            case 0 -> new int[0];
            case 1 -> new int[]{31};
            case 2 -> new int[]{30, 32};
            case 3 -> new int[]{29, 31, 33};
            default -> new int[]{28, 30, 32, 34};
        };
    }
}
