package GUIs.player;

import GUIs.menu.Ui;
import Gathering.Fishing;
import Gathering.FishingJournal;
import Gathering.FishingRules;
import Gathering.GatheringCatalog;
import Gathering.Woodcutting;
import cerberus.world.cerb.CerberusPlugin;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * The gathering skills' section tiles, collections and Fishing's summary (docs/design/FISHING_EQUIPMENT.md §2, §4).
 * Every tile reads the player's own records in memory and the totals from resources.yml; a skill whose system is off
 * on this server shows no tile.
 */
public final class GatheringSections {

    private GatheringSections() {}

    public static void register(SkillSections sections, CollectionIndex collections) {
        // ── Fishing ──
        sections.register(new SkillSections.Section("fishing.equipment", "Fishing", 10, GatheringSections::equipmentTile,
                (pl, p) -> new FishingEquipmentMenu(pl)));
        sections.register(new SkillSections.Section("fishing.journal", "Fishing", 20, GatheringSections::journalTile,
                (pl, p) -> new FishingJournalMenu(pl)));
        sections.register(new SkillSections.Section("fishing.encounters", "Fishing", 30, GatheringSections::encountersTile,
                (pl, p) -> new FishingEncountersMenu(pl)));
        sections.registerSummary("Fishing", new SkillSections.Summary(GatheringSections::summaryTile, GatheringSections::summaryClick));
        // ── Woodcutting ──
        sections.register(new SkillSections.Section("woodcutting.woods", "Woodcutting", 10, GatheringSections::woodsTile,
                (pl, p) -> new WoodsMenu(pl)));
        sections.register(new SkillSections.Section("farming.crops", "Farming", 10, GatheringSections::cropsTile,
                (pl, p) -> new CropsMenu(pl)));

        collections.register(new CollectionIndex.Entry("fish", "Fish species", Material.TROPICAL_FISH, 10, "Fishing",
                List.of("Every water's species, with your", "heaviest catch of each."),
                (pl, p) -> {
                    FishingRules r = rules(pl);
                    if (r == null) return CollectionIndex.Progress.OFF;
                    FishingJournal j = pl.getFishing().journal(p);
                    return new CollectionIndex.Progress(r.allSpecies().keySet().stream().filter(j::caught).count(), r.allSpecies().size(), true,
                            masterAngler(pl, r, j) ? "Master Angler" : null);
                }, (pl, p) -> new FishingJournalMenu(pl)));
        collections.registerExtra(new CollectionIndex.Extra("fishing.milestones", 10, GatheringSections::milestonesTile));
        collections.register(new CollectionIndex.Entry("fishing_treasure", "Fishing treasure", Material.CHEST, 20, "Fishing",
                List.of("What open water gives up from", "Treasure Sense on."),
                (pl, p) -> {
                    FishingRules r = rules(pl);
                    if (r == null || r.treasure().isEmpty()) return CollectionIndex.Progress.OFF;
                    FishingJournal j = pl.getFishing().journal(p);
                    return new CollectionIndex.Progress(r.treasure().stream().filter(t -> j.caught(t.id())).count(), r.treasure().size(), true);
                }, (pl, p) -> FishingJournalMenu.treasure(pl)));
        collections.register(new CollectionIndex.Entry("sea_creatures", "Sea creatures defeated", Material.HEART_OF_THE_SEA, 30, "Fishing",
                List.of("The creatures you called with", "an encounter and defeated."),
                (pl, p) -> {
                    FishingRules r = rules(pl);
                    if (r == null || r.creatures().isEmpty()) return CollectionIndex.Progress.OFF;
                    FishingJournal j = pl.getFishing().journal(p);
                    return new CollectionIndex.Progress(r.creatures().keySet().stream().filter(j::defeated).count(), r.creatures().size(), true);
                }, (pl, p) -> new FishingEncountersMenu(pl)));
        collections.register(new CollectionIndex.Entry("woods", "Woods", Material.OAK_SAPLING, 40, "Woodcutting",
                List.of("Every wood whose tree you cut", "a natural log from."),
                (pl, p) -> {
                    Woodcutting wc = pl.getWoodcutting();
                    GatheringCatalog c = GatheringCatalog.current();
                    if (wc == null || c == null) return CollectionIndex.Progress.OFF;
                    List<GatheringCatalog.ResourceDef> woods = WoodsMenu.woods(c, "Woodcutting");
                    if (woods.isEmpty()) return CollectionIndex.Progress.OFF;
                    Set<String> found = wc.discovered(p);
                    return new CollectionIndex.Progress(woods.stream().filter(r -> found.contains(r.id())).count(), woods.size(), true);
                }, (pl, p) -> new WoodsMenu(pl)));
        collections.register(new CollectionIndex.Entry("crops", "Crops", Material.WHEAT, 50, "Farming",
                List.of("Every crop you harvested ripe."),
                (pl, p) -> {
                    Gathering.Farming fa = pl.getFarming();
                    Gathering.FarmingRules r = GatheringCatalog.current().farming();
                    if (fa == null || !r.enabled() || r.crops().isEmpty()) return CollectionIndex.Progress.OFF;
                    Set<String> found = fa.discovered(p);
                    return new CollectionIndex.Progress(r.crops().stream().filter(c -> found.contains(c.id())).count(), r.crops().size(), true);
                }, (pl, p) -> new CropsMenu(pl)));
    }

    /**
     * The Master Angler emblem: the milestone that grants it, once (docs/design/FISHING_EQUIPMENT.md §10); on a server
     * without that milestone, every species in the journal.
     */
    public static boolean masterAngler(CerberusPlugin plugin, FishingRules r, FishingJournal j) {
        Gathering.Tackle tk = plugin.getTackle();
        Gathering.TackleRules t = GatheringCatalog.current().tackle();
        if (tk != null && t.enabled() && t.rewardForTitle("master_angler") != null) return tk.hasTitle(j, "master_angler");
        return !r.allSpecies().isEmpty() && r.allSpecies().keySet().stream().allMatch(j::caught);
    }

    /** The first milestone not reached yet and how far it is ("Surveyor · 5 of 8 species"), or null. */
    static String nextMilestone(FishingRules r, FishingJournal j) {
        Gathering.TackleRules t = GatheringCatalog.current().tackle();
        if (!t.enabled()) return null;
        for (Gathering.TackleRules.Reward w : t.rewards().values()) {
            if (j.granted(w.id())) continue;
            return w.name() + " · " + Gathering.TackleRules.progressText(w, Gathering.TackleRules.progress(w, r, j.species().keySet(), j.bestsSet()));
        }
        return null;
    }

    /** Collections → Fishing milestones: each milestone, how far it is and what it opens (docs/design/FISHING_EQUIPMENT.md §10). */
    static ItemStack milestonesTile(CerberusPlugin plugin, Player p) {
        FishingRules r = rules(plugin);
        Gathering.Tackle tk = plugin.getTackle();
        Gathering.TackleRules t = GatheringCatalog.current().tackle();
        if (r == null || tk == null || !t.enabled() || t.rewards().isEmpty()) return null;
        FishingJournal j = plugin.getFishing().journal(p);
        Ui.Lore l = Ui.lore();
        int done = 0;
        for (Gathering.TackleRules.Reward w : t.rewards().values()) {
            boolean got = j.granted(w.id());
            if (got) done++;
            Gathering.TackleRules.Progress pr = Gathering.TackleRules.progress(w, r, j.species().keySet(), j.bestsSet());
            l.raw((got ? ChatColor.GREEN + "✔ " : ChatColor.GRAY + "◇ ") + ChatColor.WHITE + w.name() + ChatColor.GRAY + ": "
                    + (got ? Gathering.TackleRules.asks(w) : Gathering.TackleRules.progressText(w, pr)));
            List<String> opens = new java.util.ArrayList<>();
            for (String u : w.unlocks()) { String x = tk.unlockText(u, t); if (x != null) opens.add(x); }
            if (!opens.isEmpty()) l.raw(ChatColor.DARK_GRAY + "   → " + String.join(", ", opens));
        }
        l.gap().text("Recorded as you fish: each is").text("granted once, nothing to hand in.");
        boolean all = done == t.rewards().size();
        return Ui.glow(Ui.item(Material.NAUTILUS_SHELL, (all ? ChatColor.GOLD : PlayerTheme.PRIMARY) + "Fishing milestones: " + done + " of "
                + t.rewards().size(), l.build()), all);
    }

    private static FishingRules rules(CerberusPlugin plugin) {
        GatheringCatalog c = GatheringCatalog.current();
        FishingRules r = c == null ? null : c.fishing();
        return plugin.getFishing() == null || r == null || !r.enabled() ? null : r;
    }

    // ── tiles ─────────────────────────────────────────────────────

    /** Fishing → Equipment: the active preset, what counts in it, the bait in use (docs/design/FISHING_EQUIPMENT.md §5). */
    static ItemStack equipmentTile(CerberusPlugin plugin, Player p) {
        Gathering.Tackle tk = plugin.getTackle();
        Gathering.TackleRules t = GatheringCatalog.current().tackle();
        if (rules(plugin) == null || tk == null || !t.enabled()) return null;
        Gathering.FishingGear g = tk.gear(p);
        Set<String> active = tk.active(p);
        Ui.Lore l = Ui.lore()
                .stat("Preset", g.activePreset().name())
                .stat("Parts counting", active.size() + " of " + g.owned().size() + " owned");
        Gathering.TackleRules.Part sp = t.specialist(active);
        if (sp != null) l.raw(ChatColor.LIGHT_PURPLE + "Specialist: " + sp.name());
        String note = tk.surveyorNote(p);
        if (note != null) l.raw(note);
        int baits = 0;
        for (int i = 0; i < Gathering.FishingGear.SLOTS; i++) if (!g.slot(i).empty()) baits += g.slot(i).count();
        l.stat("Bait in the bag", String.valueOf(baits));
        l.text("Any rod you fish with uses it.");
        l.hint("Click to open your equipment");
        return Ui.item(Material.FISHING_ROD, ChatColor.GOLD + "Equipment: " + g.activePreset().name(), l.build());
    }

    static ItemStack journalTile(CerberusPlugin plugin, Player p) {
        FishingRules r = rules(plugin);
        if (r == null) return null;
        FishingJournal j = plugin.getFishing().journal(p);
        long found = r.allSpecies().keySet().stream().filter(j::caught).count();
        Ui.Lore l = Ui.lore().raw(PlayerTheme.bar(found, Math.max(1, r.allSpecies().size()), 20, ChatColor.AQUA));
        FishingRules.Species t = r.speciesById(j.tracked());
        if (t != null) l.raw(ChatColor.LIGHT_PURPLE + "★ Tracking: " + t.rarity().color + FishingJournalMenu.name(t.item()));
        boolean master = masterAngler(plugin, r, j);
        if (master) l.raw(ChatColor.GOLD + "✦ Master Angler");
        String next = nextMilestone(r, j);
        if (next != null) l.raw(ChatColor.GRAY + "Next milestone: " + ChatColor.WHITE + next);
        l.text("Species by water: what each needs,").text("your best weights, what can bite here.");
        l.hint("Click to open the journal");
        return Ui.glow(Ui.item(Material.TROPICAL_FISH, ChatColor.GOLD + "Journal: " + found + " of " + r.allSpecies().size() + " species", l.build()), master);
    }

    static ItemStack encountersTile(CerberusPlugin plugin, Player p) {
        FishingRules r = rules(plugin);
        if (r == null) return null;
        Fishing f = plugin.getFishing();
        FishingJournal j = f.journal(p);
        Ui.Lore l = Ui.lore();
        String sel = f.selected(p);
        if (sel != null && r.creature(sel) != null) l.raw(ChatColor.AQUA + "▶ Next call: " + r.creature(sel).name());
        if (j.totalLures() == 0) l.text("None banked yet. From Fishing " + r.techniques().creatures() + ",").text("a catch can bank one.");
        else l.text("Sneak and cast into water to call").text("one and fight it.");
        l.raw(ChatColor.DARK_GRAY + "Defeated: " + r.creatures().keySet().stream().filter(j::defeated).count() + " of " + r.creatures().size());
        l.hint("Click to see them");
        return Ui.item(Material.HEART_OF_THE_SEA, ChatColor.AQUA + "Encounters: " + j.totalLures(), l.build());
    }

    static ItemStack cropsTile(CerberusPlugin plugin, Player p) {
        Gathering.Farming fa = plugin.getFarming();
        Gathering.FarmingRules r = GatheringCatalog.current().farming();
        if (fa == null || !r.enabled() || r.crops().isEmpty()) return null;
        Set<String> found = fa.discovered(p);
        long have = r.crops().stream().filter(c -> found.contains(c.id())).count();
        return Ui.item(Material.WHEAT, ChatColor.GOLD + "Crops: " + have + " of " + r.crops().size() + " discovered", Ui.lore()
                .raw(PlayerTheme.bar(have, Math.max(1, r.crops().size()), 20, ChatColor.GREEN))
                .text("Each crop, when it is ripe, its")
                .text("level and XP, and the techniques")
                .text("that work on it.")
                .hint("Click to see the crops").build());
    }

    static ItemStack woodsTile(CerberusPlugin plugin, Player p) {
        Woodcutting wc = plugin.getWoodcutting();
        GatheringCatalog c = GatheringCatalog.current();
        if (wc == null || c == null) return null;
        List<GatheringCatalog.ResourceDef> woods = WoodsMenu.woods(c, "Woodcutting");
        if (woods.isEmpty()) return null;
        Set<String> found = wc.discovered(p);
        long have = woods.stream().filter(r -> found.contains(r.id())).count();
        return Ui.item(Material.OAK_SAPLING, ChatColor.GOLD + "Woods: " + have + " of " + woods.size() + " discovered", Ui.lore()
                .raw(PlayerTheme.bar(have, Math.max(1, woods.size()), 20, ChatColor.GREEN))
                .text("Each wood, its level and the axe")
                .text("it needs; the first log of each")
                .text("pays a discovery bonus.")
                .hint("Click to see the woods").build());
    }

    /** Fishing's "since your last visit": the unread counters; left-click views the discoveries, right-click marks it read. */
    static ItemStack summaryTile(CerberusPlugin plugin, Player p) {
        FishingRules r = rules(plugin);
        if (r == null) return null;
        FishingJournal j = plugin.getFishing().journal(p);
        FishingJournal.Summary s = j.summary();
        int unread = j.unreadNotable();
        if (s.isEmpty() && unread == 0) {
            return Ui.item(Material.CLOCK, ChatColor.GRAY + "Since your last visit", Ui.lore()
                    .text("Nothing new yet: catches, XP,")
                    .text("discoveries and encounters you")
                    .text("bank show here until you mark")
                    .text("them read.")
                    .hint("Click: recent notable catches").build());
        }
        Ui.Lore l = Ui.lore();
        FishingDiscoveriesMenu.summaryLines(l, s, r);
        l.gap().raw(ChatColor.YELLOW + "▸ Click: view new discoveries →");
        l.raw(ChatColor.YELLOW + "▸ Right-click: mark as read");
        return Ui.glow(Ui.item(Material.CLOCK, ChatColor.YELLOW + "Since your last visit", l.build()), true);
    }

    /**
     * The summary tile clicked. The values it showed were read when the page was drawn; a right-click takes off exactly
     * those (Gathering.FishingJournal#acknowledge), never what came in since.
     */
    static void summaryClick(CerberusPlugin plugin, Player p, boolean right, GUIs.menu.Menu from) {
        Fishing f = plugin.getFishing();
        if (f == null) return;
        if (!right) { plugin.getMenuManager().open(p, new FishingDiscoveriesMenu(plugin)); return; }
        if (!(from instanceof SkillDetailMenu page) || page.redrawnJustNow(p)) return; // a double right-click clears only what was seen
        FishingJournal.Summary shown = page.shownSummary();
        Set<Long> at = page.shownUnreadAt();
        if (shown == null) return;
        f.journal(p).acknowledge(shown, at == null ? new HashSet<>() : at);
        f.saveJournal(p);
        plugin.getMenuManager().refresh(p);
    }
}
