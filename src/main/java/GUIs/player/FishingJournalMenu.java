package GUIs.player;

import GUIs.menu.MenuView;
import GUIs.menu.Paging;
import GUIs.menu.Ui;
import Gathering.Fishing;
import Gathering.FishingJournal;
import Gathering.FishingRules;
import Gathering.GatheringCatalog;
import cerberus.world.cerb.CerberusPlugin;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Player Menu → Skills → Fishing → Journal, and Collections → Fish species / Fishing treasure: one screen for both
 * (docs/design/FISHING_EQUIPMENT.md §3). Tabs by water (and treasure), the All / Undiscovered / Available here filters,
 * each species with its rarity, best weight and requirements, and the tracked species with each requirement checked
 * where the player fishes. "Available here" uses {@link FishingRules#checks}, the same conditions a bite uses.
 *
 * <p>Reads the journal and the rules from memory on every draw; clicking a species tracks it (saved on the player, no
 * database). The tab, filter and page stay on this screen's instance, so Back from a deeper screen returns to them.
 */
public final class FishingJournalMenu extends PlayerMenu {

    private static final int[] ENTRY_SLOTS = {19, 20, 21, 22, 23, 24, 25, 28, 29, 30, 31, 32, 33, 34, 37, 38, 39, 40, 41, 42, 43};
    private static final int TAB_ROW = 9, FILTER = 47, HINTS = 51, PLACE = 2, TRACKED = 6;

    /** The three filters. */
    enum Filter {
        ALL("All species"), UNDISCOVERED("Undiscovered"), HERE("Available here");

        final String label;

        Filter(String label) { this.label = label; }

        Filter next() { return values()[(ordinal() + 1) % values().length]; }
    }

    /** "" = all waters, a water id, or {@link #TREASURE}. */
    private String tab;
    private Filter filter = Filter.ALL;
    private int page;
    private int tabOffset;
    static final String TREASURE = "#treasure";

    public FishingJournalMenu(CerberusPlugin plugin) {
        this(plugin, "");
    }

    /** Opened on one tab: a water id, "" for all waters, or {@link #TREASURE}. */
    public FishingJournalMenu(CerberusPlugin plugin, String tab) {
        super(plugin);
        this.tab = tab == null ? "" : tab;
    }

    public static FishingJournalMenu treasure(CerberusPlugin plugin) {
        return new FishingJournalMenu(plugin, TREASURE);
    }

    @Override public String title() { return PlayerTheme.title("Fishing Journal"); }

    @Override protected Material headerGlass() { return Material.LIGHT_BLUE_STAINED_GLASS_PANE; }

    @Override
    protected void content(MenuView v) {
        Player p = v.viewer();
        Fishing f = plugin.getFishing();
        FishingRules r = GatheringCatalog.current().fishing();
        if (f == null || !r.enabled()) {
            v.set(22, Ui.item(Material.BARRIER, ChatColor.GRAY + "Fishing's rules are off on this server", List.of()));
            return;
        }
        if (!loaded(p)) { loading(v); return; }
        FishingJournal j = f.journal(p);
        int level = plugin.getSkillManager().getSkillLevel(p, r.skill());
        Fishing.Place place = f.place(p);

        v.set(MenuView.HEADER, header(r, j));
        v.set(PLACE, placeTile(r, place));
        v.button(TRACKED, trackedTile(r, j, level, place), c -> {
            FishingJournal jj = f.journal(c.player());
            if (jj.tracked() == null) { Ui.info(c.player(), "Click a species below to track it."); return; }
            jj.track(null);
            f.saveJournal(c.player());
            menus.refresh(c.player());
        });
        tabs(v, r);

        List<ItemStack> items = new ArrayList<>();
        List<String> ids = new ArrayList<>();
        if (TREASURE.equals(tab)) {
            for (FishingRules.Treasure t : r.treasure()) {
                boolean found = j.caught(t.id());
                if (filter == Filter.UNDISCOVERED && found) continue;
                if (filter == Filter.HERE && !(level >= t.level() && r.treasureSense(level))) continue;
                items.add(treasureTile(r, t, found, level));
                ids.add(null);
            }
        } else {
            for (FishingRules.Water w : r.waters()) {
                if (!tab.isEmpty() && !tab.equals(w.id())) continue;
                for (FishingRules.Species s : r.species(w.id())) {
                    boolean found = j.caught(s.id());
                    List<FishingRules.Check> checks = r.checks(s, place.moment(), level);
                    if (filter == Filter.UNDISCOVERED && found) continue;
                    if (filter == Filter.HERE && (place.source() == Fishing.PlaceSource.UNKNOWN || !FishingRules.allMet(checks))) continue;
                    items.add(speciesTile(r, s, j, checks, place, s.id().equals(j.tracked())));
                    ids.add(s.id());
                }
            }
        }
        int per = ENTRY_SLOTS.length;
        page = Paging.clampPage(page, items.size(), per);
        int pages = Paging.pageCount(items.size(), per);
        for (int i = 0, k = page * per; i < per && k < items.size(); i++, k++) {
            String id = ids.get(k);
            if (id == null) { v.set(ENTRY_SLOTS[i], items.get(k)); continue; }
            v.button(ENTRY_SLOTS[i], items.get(k), c -> {
                FishingJournal jj = f.journal(c.player());
                jj.track(id.equals(jj.tracked()) ? null : id);
                f.saveJournal(c.player());
                menus.refresh(c.player());
            });
        }
        if (items.isEmpty()) v.set(31, emptyTile(place));
        if (page > 0) v.button(MenuView.PREV, Ui.item(Material.SPECTRAL_ARROW, ChatColor.YELLOW + "Previous page",
                Ui.lore().stat("Page", page + " / " + pages).build()), c -> { page--; menus.refresh(c.player()); });
        if (page < pages - 1) v.button(MenuView.NEXT, Ui.item(Material.SPECTRAL_ARROW, ChatColor.YELLOW + "Next page",
                Ui.lore().stat("Page", (page + 2) + " / " + pages).build()), c -> { page++; menus.refresh(c.player()); });

        Ui.Lore fl = Ui.lore();
        for (Filter x : Filter.values()) fl.raw((x == filter ? ChatColor.AQUA + "▶ " : ChatColor.DARK_GRAY + "  ") + x.label);
        fl.gap().text("Available here: the species whose").text("every requirement is met where you")
                .text("fish now. A bite still picks among").text("them by rarity.");
        fl.hint("Click for the next filter");
        v.button(FILTER, Ui.item(Material.HOPPER, ChatColor.GOLD + "Filter: " + filter.label, fl.build()), c -> {
            filter = filter.next();
            page = 0;
            menus.refresh(c.player());
        });

        boolean quiet = j.flag(Fishing.QUIET_TRACKING);
        v.button(HINTS, Ui.item(quiet ? Material.GRAY_DYE : Material.LIME_DYE,
                (quiet ? ChatColor.GRAY : ChatColor.GREEN) + "Cast hints: " + (quiet ? "off" : "on"), Ui.lore()
                        .text("When your bobber settles, one short")
                        .text("line says whether your tracked")
                        .text("species can bite there, and only")
                        .text("when that changes.")
                        .hint("Click to turn " + (quiet ? "on" : "off")).build()), c -> {
            FishingJournal jj = f.journal(c.player());
            if (!jj.clearFlag(Fishing.QUIET_TRACKING)) jj.setFlag(Fishing.QUIET_TRACKING);
            f.saveJournal(c.player());
            menus.refresh(c.player());
        });
        v.refreshButton();
    }

    // ── tiles ─────────────────────────────────────────────────────

    private ItemStack header(FishingRules r, FishingJournal j) {
        long species = r.allSpecies().keySet().stream().filter(j::caught).count();
        long treasure = r.treasure().stream().filter(t -> j.caught(t.id())).count();
        Ui.Lore l = Ui.lore()
                .stat("Species", species + " of " + r.allSpecies().size())
                .raw(PlayerTheme.bar(species, Math.max(1, r.allSpecies().size()), 20, ChatColor.AQUA))
                .stat("Treasure", treasure + " of " + r.treasure().size())
                .stat("Personal bests set", String.valueOf(j.bestsSet()));
        l.gap();
        for (String w : PlayerTheme.wrap("Each water has its own species; time, weather and your level decide what can bite. "
                + "Track a species to see what it still needs.", SkillView.WIDTH)) l.text(w);
        boolean master = !r.allSpecies().isEmpty() && species >= r.allSpecies().size();
        if (master) l.gap().raw(ChatColor.GOLD + "✦ Master Angler: every species found");
        return Ui.glow(Ui.item(Material.TROPICAL_FISH, ChatColor.AQUA + "" + ChatColor.BOLD + "Fishing Journal", l.build()), master);
    }

    /** Where "here" is, said plainly (never the player's standing position). */
    static ItemStack placeTile(FishingRules r, Fishing.Place place) {
        Ui.Lore l = Ui.lore();
        FishingRules.Moment m = place.moment();
        String water = m.water() == null ? null : r.water(m.water()) == null ? m.water() : r.water(m.water()).name();
        switch (place.source()) {
            case BOBBER -> l.stat("Water", water).text("where your bobber floats.");
            case LOOKING -> l.stat("Water", water + ChatColor.DARK_GRAY + " (preview)").text("the water you were looking at").text("when this screen opened.");
            default -> l.text("Not known yet: cast your line, or").text("look at water before opening the").text("journal, to check where you fish.");
        }
        if (m.biome() != null) l.stat("Biome", m.biome().replace('_', ' '));
        l.stat("Time", m.night() ? "night" : "day").stat("Weather", m.thunder() ? "thunderstorm" : m.rain() ? "rain" : "clear");
        Material icon = place.source() == Fishing.PlaceSource.BOBBER ? Material.FISHING_ROD
                : place.source() == Fishing.PlaceSource.LOOKING ? Material.SPYGLASS : Material.COMPASS;
        String name = place.source() == Fishing.PlaceSource.UNKNOWN ? ChatColor.GRAY + "Where you fish: not known yet"
                : ChatColor.GOLD + "Where you fish: " + water;
        return Ui.item(icon, name, l.build());
    }

    private ItemStack trackedTile(FishingRules r, FishingJournal j, int level, Fishing.Place place) {
        FishingRules.Species s = r.speciesById(j.tracked());
        if (s == null) {
            return Ui.item(Material.ENDER_EYE, ChatColor.GRAY + "★ No species tracked", Ui.lore()
                    .text("Click a species below to track it:")
                    .text("this tile then checks each of its")
                    .text("requirements where you fish.")
                    .gap().raw(ChatColor.DARK_GRAY + "Tracking never changes the odds.").build());
        }
        List<FishingRules.Check> checks = r.checks(s, place.moment(), level);
        Ui.Lore l = Ui.lore();
        for (FishingRules.Check c : checks) l.raw(checkLine(c, place));
        l.gap();
        boolean all = FishingRules.allMet(checks);
        boolean unknown = checks.stream().anyMatch(c -> c.state() == FishingRules.CheckState.UNKNOWN);
        if (all) l.raw(ChatColor.GREEN + "It can bite here now.");
        else if (unknown && checks.stream().noneMatch(c -> c.state() == FishingRules.CheckState.MISSING)) l.raw(ChatColor.YELLOW + "Cast your line to check the water.");
        else l.raw(ChatColor.RED + "It can't bite here now.");
        l.raw(ChatColor.DARK_GRAY + "Tracking never changes the odds.");
        l.hint("Click to stop tracking");
        return Ui.glow(Ui.item(Material.ENDER_EYE, ChatColor.LIGHT_PURPLE + "★ Tracked: " + s.rarity().color + name(s.item()), l.build()), true);
    }

    /** "✓ Fishing 25 (you: 31)", "✕ Night only (it is day)", "? Rivers (cast to check)". */
    static String checkLine(FishingRules.Check c, Fishing.Place place) {
        String have = c.have().isEmpty() ? "" : " " + ChatColor.DARK_GRAY + "(" + c.have() + ")";
        if (c.kind() == FishingRules.Need.WATER && c.state() != FishingRules.CheckState.UNKNOWN) {
            have = " " + ChatColor.DARK_GRAY + "(" + (place.source() == Fishing.PlaceSource.BOBBER ? "your bobber: " : "preview: ") + c.have() + ")";
        }
        return switch (c.state()) {
            case MET -> ChatColor.GREEN + "✓ " + ChatColor.WHITE + c.need() + have;
            case MISSING -> ChatColor.RED + "✕ " + ChatColor.WHITE + c.need() + have;
            case UNKNOWN -> ChatColor.YELLOW + "? " + ChatColor.WHITE + c.need() + ChatColor.DARK_GRAY + " (cast to check)";
        };
    }

    private ItemStack speciesTile(FishingRules r, FishingRules.Species s, FishingJournal j, List<FishingRules.Check> checks,
                                  Fishing.Place place, boolean tracked) {
        boolean found = j.caught(s.id());
        GatheringCatalog.ItemDef def = GatheringCatalog.current().item(s.item());
        Material icon = found && def != null ? def.material() : Material.GRAY_DYE;
        FishingRules.Water w = r.water(s.water());
        Ui.Lore l = Ui.lore()
                .raw(s.rarity().color + s.rarity().displayName + ChatColor.DARK_GRAY + " · " + ChatColor.GRAY + (w == null ? s.water() : w.name()));
        if (found) {
            double kg = j.best(s.id());
            l.raw(ChatColor.GREEN + "✔ Caught" + (kg > 0 ? ChatColor.GRAY + " · best " + ChatColor.WHITE + String.format(Locale.ROOT, kg < 10 ? "%.2f kg" : "%.1f kg", kg) : ""));
        } else {
            l.raw(ChatColor.DARK_GRAY + "✘ Not caught yet");
        }
        l.raw(ChatColor.DARK_GRAY + "Range " + String.format(Locale.ROOT, "%s–%s kg", trim(s.minKg()), trim(s.maxKg())));
        l.gap();
        for (FishingRules.Check c : checks) l.raw(checkLine(c, place));
        if (place.source() != Fishing.PlaceSource.UNKNOWN) {
            l.raw(FishingRules.allMet(checks) ? ChatColor.GREEN + "Can bite here now" : ChatColor.GRAY + "Can't bite here now");
        }
        if (tracked) l.gap().raw(ChatColor.LIGHT_PURPLE + "★ Tracked");
        l.hint(tracked ? "Click to stop tracking" : "Click to track");
        String name = found ? s.rarity().color + name(s.item()) : ChatColor.GRAY + name(s.item());
        return Ui.glow(Ui.item(icon, name, l.build()), tracked);
    }

    private static ItemStack treasureTile(FishingRules r, FishingRules.Treasure t, boolean found, int level) {
        GatheringCatalog.ItemDef def = GatheringCatalog.current().item(t.item());
        Ui.Lore l = Ui.lore().raw(t.rarity().color + t.rarity().displayName + ChatColor.DARK_GRAY + " · " + ChatColor.GRAY + "treasure");
        l.raw(found ? ChatColor.GREEN + "✔ Found" : ChatColor.DARK_GRAY + "✘ Not found yet");
        l.gap();
        int from = Math.max(t.level(), r.techniques().treasureSense());
        l.raw((level >= from ? ChatColor.GREEN + "✓ " : ChatColor.RED + "✕ ") + ChatColor.WHITE + "Fishing " + from
                + ChatColor.DARK_GRAY + " (you: " + level + ")");
        l.raw(ChatColor.WHITE + "  In open water" + ChatColor.DARK_GRAY + " (as vanilla treasure)");
        l.text("The treasure-chance stat decides how");
        l.text("often a catch is treasure.");
        String name = def == null ? t.id() : def.name();
        return Ui.item(found && def != null ? def.material() : Material.GRAY_DYE, (found ? t.rarity().color : ChatColor.GRAY) + name, l.build());
    }

    private ItemStack emptyTile(Fishing.Place place) {
        Ui.Lore l = Ui.lore();
        if (filter == Filter.HERE && place.source() == Fishing.PlaceSource.UNKNOWN) {
            l.text("Where you fish isn't known yet:").text("cast your line, or look at water").text("before opening the journal.");
        } else if (filter == Filter.UNDISCOVERED) {
            l.text("Everything here is in your journal.");
        } else {
            l.text("Nothing matches this filter here.");
        }
        return Ui.item(Material.GLASS_BOTTLE, ChatColor.GRAY + "Nothing to show", l.build());
    }

    private void tabs(MenuView v, FishingRules r) {
        List<String> ids = new ArrayList<>();
        List<String> names = new ArrayList<>();
        ids.add("");
        names.add("All waters");
        for (FishingRules.Water w : r.waters()) {
            if (r.species(w.id()).isEmpty()) continue;
            ids.add(w.id());
            names.add(w.name());
        }
        ids.add(TREASURE);
        names.add("Treasure");
        if (!ids.contains(tab)) tab = "";
        int width = 9;
        boolean scroll = ids.size() > width;
        int shown = scroll ? width - 1 : width;
        tabOffset = Math.max(0, Math.min(tabOffset, ids.size() - shown));
        for (int i = 0; i < shown && tabOffset + i < ids.size(); i++) {
            String id = ids.get(tabOffset + i);
            boolean on = id.equals(tab);
            Material m = id.isEmpty() ? Material.WATER_BUCKET : id.equals(TREASURE) ? Material.CHEST : waterIcon(id);
            v.button(TAB_ROW + i, Ui.glow(Ui.item(m, (on ? ChatColor.AQUA + "▶ " : ChatColor.YELLOW + "") + names.get(tabOffset + i),
                    Ui.lore().hint(on ? "Showing" : "Click to show").build()), on), c -> {
                tab = id;
                page = 0;
                menus.refresh(c.player());
            });
        }
        if (scroll) {
            v.button(TAB_ROW + width - 1, Ui.item(Material.ARROW, ChatColor.YELLOW + "More tabs ▶", List.of()), c -> {
                tabOffset = tabOffset + shown >= ids.size() ? 0 : tabOffset + 1;
                menus.refresh(c.player());
            });
        }
    }

    private static Material waterIcon(String water) {
        return switch (water) {
            case "river" -> Material.KELP;
            case "fresh" -> Material.LILY_PAD;
            case "swamp" -> Material.MANGROVE_PROPAGULE;
            case "ocean" -> Material.PRISMARINE_SHARD;
            case "warm" -> Material.BRAIN_CORAL;
            case "cold" -> Material.BLUE_ICE;
            case "cave" -> Material.POINTED_DRIPSTONE;
            default -> Material.WATER_BUCKET;
        };
    }

    static String name(String itemId) {
        GatheringCatalog.ItemDef d = GatheringCatalog.current().item(itemId);
        return d == null ? itemId : d.name();
    }

    private static String trim(double v) {
        return v == Math.rint(v) ? String.valueOf((long) v) : String.valueOf(v);
    }
}
