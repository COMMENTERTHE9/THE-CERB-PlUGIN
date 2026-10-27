package GUIs.player;

import GUIs.menu.Fmt;
import GUIs.menu.MenuView;
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
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Fishing → "Since your last visit" → View new discoveries (docs/design/FISHING_EQUIPMENT.md §4, §11): the summary's
 * counters and the last notable catches (discoveries and personal bests with their real weights), newest first. Viewing
 * changes nothing; "Mark as read" takes off exactly what this screen showed, so catches that came in while it was open
 * stay unread. A notable catch can be kept as a trophy once, from a fish of its species you carry.
 */
public final class FishingDiscoveriesMenu extends PlayerMenu {

    private static final int[] SLOTS = {19, 20, 21, 22, 23, 24, 25, 28, 29, 30, 31, 32};
    private static final int READ = 51;

    public FishingDiscoveriesMenu(CerberusPlugin plugin) {
        super(plugin);
    }

    @Override public String title() { return PlayerTheme.title("New discoveries"); }

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
        FishingJournal.Summary shown = j.summary();
        Set<Long> unreadAt = new HashSet<>();
        for (FishingJournal.Notable n : j.notable()) if (n.unread()) unreadAt.add(n.at());

        Ui.Lore head = Ui.lore();
        summaryLines(head, shown, r);
        if (shown.isEmpty()) head.text("Nothing new since you last marked").text("the summary read.");
        v.set(MenuView.HEADER, Ui.item(Material.CLOCK, ChatColor.YELLOW + "" + ChatColor.BOLD + "Since your last visit", head.build()));

        List<FishingJournal.Notable> list = new ArrayList<>(j.notable());
        java.util.Collections.reverse(list); // newest first
        long now = System.currentTimeMillis();
        for (int i = 0; i < list.size() && i < SLOTS.length; i++) {
            FishingJournal.Notable n = list.get(i);
            int carried = n.trophy() ? 0 : f.carried(p, n.species());
            ItemStack tile = notableTile(n, now, carried);
            if (n.trophy() || carried <= 0) { v.set(SLOTS[i], tile); continue; }
            v.button(SLOTS[i], tile, c -> askTrophy(c.player(), n));
        }
        if (list.isEmpty()) {
            v.set(22, Ui.item(Material.GLASS_BOTTLE, ChatColor.GRAY + "No notable catches yet", Ui.lore()
                    .text("New species and personal bests").text("show here with their weights.").build()));
        }

        boolean any = !shown.isEmpty() || !unreadAt.isEmpty();
        if (any) {
            v.button(READ, Ui.item(Material.LIME_DYE, ChatColor.GREEN + "✔ Mark as read", Ui.lore()
                    .text("Clears what this screen shows.")
                    .text("Catches made since it opened")
                    .text("stay unread.").build()), c -> {
                Fishing ff = plugin.getFishing();
                if (ff == null) return;
                ff.journal(c.player()).acknowledge(shown, unreadAt);
                ff.saveJournal(c.player());
                menus.refresh(c.player());
            });
        }
        v.refreshButton();
    }

    /** "42 catches · +1,260 Fishing XP", "3 new species · 1 personal best", "2 encounters banked": only real, awarded numbers. */
    static void summaryLines(Ui.Lore l, FishingJournal.Summary s, FishingRules r) {
        if (s.isEmpty()) return;
        l.raw(ChatColor.WHITE + Fmt.grouped(s.catches()) + ChatColor.GRAY + (s.catches() == 1 ? " catch" : " catches")
                + ChatColor.DARK_GRAY + " · " + ChatColor.GREEN + "+" + Fmt.grouped(s.xp()) + " " + Skills.SkillNames.display(r.skill()) + " XP");
        List<String> parts = new ArrayList<>();
        if (s.species() > 0) parts.add(ChatColor.AQUA + "" + s.species() + ChatColor.GRAY + (s.species() == 1 ? " new species" : " new species"));
        if (s.bests() > 0) parts.add(ChatColor.GOLD + "" + s.bests() + ChatColor.GRAY + (s.bests() == 1 ? " personal best" : " personal bests"));
        if (!parts.isEmpty()) l.raw(String.join(ChatColor.DARK_GRAY + " · ", parts));
        if (s.encounters() > 0) l.raw(ChatColor.LIGHT_PURPLE + "" + s.encounters() + ChatColor.GRAY + (s.encounters() == 1 ? " encounter banked" : " encounters banked"));
    }

    /** Keep as trophy: asks first (it uses one fish), then {@link Fishing#keepTrophy} checks everything again. */
    private void askTrophy(Player p, FishingJournal.Notable n) {
        FishingRules.Species s = GatheringCatalog.current().fishing().speciesById(n.species());
        GatheringCatalog.ItemDef def = s == null ? null : GatheringCatalog.current().item(s.item());
        String name = def == null ? n.species() : def.name();
        String weight = String.format(Locale.ROOT, n.kg() < 10 ? "%.2f kg" : "%.1f kg", n.kg());
        menus.open(p, new GUIs.menu.ConfirmMenu(menus, null, "Keep " + name + " as a trophy?", List.of(
                "Uses 1 " + name + " from your inventory.",
                "You get a trophy: " + name + " · " + weight + ",",
                "with who caught it, when and where.",
                "Each notable catch makes one trophy."), pl -> {
            Fishing ff = plugin.getFishing();
            if (ff == null) return;
            Gathering.Tackle.Result r = ff.keepTrophy(pl, n.species(), n.at());
            if (r.ok()) Ui.ok(pl, r.message());
            else Ui.fail(pl, r.message());
            menus.refresh(pl);
        }));
    }

    private static ItemStack notableTile(FishingJournal.Notable n, long now, int carried) {
        FishingRules r = GatheringCatalog.current().fishing();
        FishingRules.Species s = r.speciesById(n.species());
        GatheringCatalog.ItemDef def = s == null ? null : GatheringCatalog.current().item(s.item());
        String name = def == null ? n.species() : def.name();
        String color = s == null ? ChatColor.GRAY.toString() : s.rarity().color;
        Ui.Lore l = Ui.lore()
                .raw(n.discovery() ? ChatColor.AQUA + "New species" : ChatColor.GOLD + "★ Personal best")
                .stat("Weight", String.format(Locale.ROOT, n.kg() < 10 ? "%.2f kg" : "%.1f kg", n.kg()))
                .stat("When", ago(now - n.at()));
        if (s != null) {
            FishingRules.Water w = r.water(s.water());
            l.stat("Water", w == null ? s.water() : w.name());
        }
        if (n.trophy()) l.raw(ChatColor.GOLD + "✦ Kept as a trophy");
        else if (carried > 0) l.hint("Click: keep it as a trophy (uses 1 of the " + carried + " you carry)");
        else l.raw(ChatColor.DARK_GRAY + "Carry one to keep it as a trophy");
        ItemStack it = Ui.item(def == null ? Material.COD : def.material(), color + name + (n.unread() ? ChatColor.YELLOW + " · NEW" : ""), l.build());
        return Ui.glow(it, n.unread());
    }

    static String ago(long ms) {
        long m = Math.max(0, ms) / 60_000;
        if (m < 1) return "just now";
        if (m < 60) return m + " min ago";
        long h = m / 60;
        if (h < 48) return h + " h ago";
        return (h / 24) + " days ago";
    }
}
