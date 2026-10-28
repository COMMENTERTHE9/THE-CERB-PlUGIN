package GUIs.player;

import Commands.HerbalismCommand;
import GUIs.menu.MenuView;
import GUIs.menu.Paging;
import GUIs.menu.Ui;
import Gathering.GatheringCatalog;
import Gathering.Herbalism;
import Gathering.HerbalismRules;
import cerberus.world.cerb.CerberusPlugin;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Player Menu → Skills → Herbalism → Herbarium (or Rare reagents), and Collections → Herbs / Rare reagents
 * (docs/design/HERBALISM.md §5): one tile per herb in resources.yml, found or not (an unfound herb shows its name and where it
 * grows, so it is a goal, not a secret), with its level, XP, whether it needs shears and the rare reagent it can give; or one
 * tile per rare reagent with the herbs that give it and how many you found. The header lists the techniques at the viewer's
 * level, from the same rules the game uses. Read from the player's own records, kept in memory while they are online.
 */
public final class HerbariumMenu extends PlayerMenu {

    private static final int[] SLOTS = {19, 20, 21, 22, 23, 24, 25, 28, 29, 30, 31, 32, 33, 34, 37, 38, 39, 40, 41, 42, 43};
    private static final int[] RARE_SLOTS = {20, 22, 24, 30, 32, 34, 38, 40, 42};
    private final boolean rare;
    private int page;

    public HerbariumMenu(CerberusPlugin plugin) {
        this(plugin, false);
    }

    private HerbariumMenu(CerberusPlugin plugin, boolean rare) {
        super(plugin);
        this.rare = rare;
    }

    /** The rare reagents' screen. */
    public static HerbariumMenu rare(CerberusPlugin plugin) {
        return new HerbariumMenu(plugin, true);
    }

    @Override public String title() { return PlayerTheme.title(rare ? "Rare Reagents" : "Herbarium"); }

    @Override protected Material headerGlass() { return rare ? Material.PURPLE_STAINED_GLASS_PANE : Material.LIME_STAINED_GLASS_PANE; }

    @Override
    protected void content(MenuView v) {
        Player p = v.viewer();
        Herbalism h = plugin.getHerbalism();
        HerbalismRules r = GatheringCatalog.current().herbalism();
        if (h == null || !r.enabled()) {
            v.set(22, Ui.item(Material.BARRIER, ChatColor.GRAY + "Herbalism is off on this server", List.of()));
            return;
        }
        if (!loaded(p)) { loading(v); return; }
        int level = plugin.getSkillManager().getSkillLevel(p, r.skill());
        if (rare) rareReagents(v, p, h, r, level);
        else herbs(v, p, h, r, level);
        v.refreshButton();
    }

    private void herbs(MenuView v, Player p, Herbalism h, HerbalismRules r, int level) {
        List<HerbalismRules.Herb> herbs = new ArrayList<>(r.herbs());
        Set<String> found = h.discovered(p);
        long have = herbs.stream().filter(x -> found.contains(x.id())).count();
        Ui.Lore head = Ui.lore().stat("Discovered", have + " of " + herbs.size())
                .raw(PlayerTheme.bar(have, Math.max(1, herbs.size()), 20, ChatColor.GREEN)).gap();
        for (String line : HerbalismCommand.techniques(r, level)) head.raw(line);
        head.gap();
        for (String w : PlayerTheme.wrap("The first gather of each herb pays a discovery bonus, once. Wild plants and those bone meal "
                + "grew pay in full; one placed by hand pays nothing.", SkillView.WIDTH)) head.text(w);
        v.set(MenuView.HEADER, Ui.item(Material.POPPY, ChatColor.GREEN + "" + ChatColor.BOLD + "Herbarium", head.build()));

        page = Paging.clampPage(page, herbs.size(), SLOTS.length);
        int pages = Paging.pageCount(herbs.size(), SLOTS.length);
        List<HerbalismRules.Herb> shown = Paging.slice(herbs, page, SLOTS.length);
        boolean rareOpen = r.rareAt() > 0 && level >= r.rareAt();
        for (int i = 0; i < shown.size(); i++) {
            HerbalismRules.Herb x = shown.get(i);
            boolean got = found.contains(x.id());
            Ui.Lore l = Ui.lore();
            l.raw(got ? ChatColor.GREEN + "✔ Discovered" : ChatColor.DARK_GRAY + "✘ Not discovered yet");
            l.raw((level >= x.level() ? ChatColor.GREEN + "✓ " : ChatColor.RED + "✕ ") + ChatColor.WHITE + "Herbalism " + x.level()
                    + ChatColor.DARK_GRAY + " (you: " + level + ")");
            if (!x.grows().isBlank()) l.stat("Grows", x.grows());
            l.stat("Gather", x.shears() ? "with shears (by hand it drops nothing of itself)" : "by hand or with shears");
            l.stat("XP", trim(x.xp()) + " per gather");
            if (x.discoveryXp() > 0) l.stat("Discovery", "+" + trim(x.discoveryXp()) + " XP, once");
            HerbalismRules.Rare rr = r.rare(x.rare());
            if (rr != null) {
                l.stat("Rare reagent", rareOpen ? rr.name() + " (" + pct(r.rareChance(level)) + ")" : rr.name() + " (from Herbalism " + r.rareAt() + ")");
            }
            l.raw(ChatColor.DARK_GRAY + "Fortune · quality · Wildcrafting");
            Material icon = got ? icon(x) : Material.GRAY_DYE;
            v.set(SLOTS[i], Ui.item(icon, (got ? ChatColor.GREEN : ChatColor.GRAY) + x.name(), l.build()));
        }
        if (page > 0) v.button(MenuView.PREV, Ui.item(Material.SPECTRAL_ARROW, ChatColor.YELLOW + "Previous page",
                Ui.lore().stat("Page", page + " / " + pages).build()), click -> { page--; menus.refresh(click.player()); });
        if (page < pages - 1) v.button(MenuView.NEXT, Ui.item(Material.SPECTRAL_ARROW, ChatColor.YELLOW + "Next page",
                Ui.lore().stat("Page", (page + 2) + " / " + pages).build()), click -> { page++; menus.refresh(click.player()); });
    }

    private void rareReagents(MenuView v, Player p, Herbalism h, HerbalismRules r, int level) {
        List<HerbalismRules.Rare> rares = new ArrayList<>(r.rares());
        Map<String, Integer> found = h.rareFound(p);
        long have = rares.stream().filter(x -> found.containsKey(x.id())).count();
        boolean open = r.rareAt() > 0 && level >= r.rareAt();
        Ui.Lore head = Ui.lore().stat("Found", have + " of " + rares.size())
                .raw(PlayerTheme.bar(have, Math.max(1, rares.size()), 20, ChatColor.LIGHT_PURPLE)).gap();
        head.raw(open ? "§a✔ §fRare reagents §7" + pct(r.rareChance(level)) + " from a herb that has one"
                : "§8✖ Rare reagents §7from Herbalism " + r.rareAt());
        head.gap();
        for (String w : PlayerTheme.wrap("A gathered herb that has a rare reagent sometimes gives one beside it. Alchemy's strongest "
                + "brews need them. Fortune never doubles one.", SkillView.WIDTH)) head.text(w);
        v.set(MenuView.HEADER, Ui.item(Material.AMETHYST_SHARD, ChatColor.LIGHT_PURPLE + "" + ChatColor.BOLD + "Rare Reagents", head.build()));
        for (int i = 0; i < rares.size() && i < RARE_SLOTS.length; i++) {
            HerbalismRules.Rare x = rares.get(i);
            GatheringCatalog.ItemDef def = GatheringCatalog.current().item(x.item());
            String name = x.name();
            int count = found.getOrDefault(x.id(), 0);
            Ui.Lore l = Ui.lore();
            l.raw(count > 0 ? ChatColor.GREEN + "✔ Found " + count + (count == 1 ? " time" : " times") : ChatColor.DARK_GRAY + "✘ Not found yet");
            List<String> from = new ArrayList<>();
            for (String id : x.from()) { HerbalismRules.Herb hb = r.herb(id); if (hb != null) from.add(hb.name()); }
            for (String w : PlayerTheme.wrap("From: " + (from.isEmpty() ? "no herb on this server" : String.join(", ", from)), SkillView.WIDTH)) l.text(w);
            l.stat("Chance", open ? pct(r.rareChance(level)) + " a gather" : "from Herbalism " + r.rareAt());
            Material icon = count > 0 && def != null ? def.material() : Material.GRAY_DYE;
            v.set(RARE_SLOTS[i], Ui.item(icon, (count > 0 ? ChatColor.LIGHT_PURPLE : ChatColor.GRAY) + name, l.build()));
        }
    }

    /** A found herb's tile: its own item where it has one (a tall seagrass shows seagrass). */
    private static Material icon(HerbalismRules.Herb x) {
        for (Material m : x.produce()) if (m.isItem()) return m;
        return Material.POPPY;
    }

    private static String pct(double chance) {
        double v = chance * 100;
        return (v == Math.rint(v) ? String.valueOf((long) v) : String.format(java.util.Locale.ROOT, "%.1f", v)) + "%";
    }

    private static String trim(double v) {
        return v == Math.rint(v) ? String.valueOf((long) v) : String.valueOf(v);
    }
}
