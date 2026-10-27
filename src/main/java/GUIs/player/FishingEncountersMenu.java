package GUIs.player;

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
import java.util.List;
import java.util.Locale;

/**
 * Player Menu → Skills → Fishing → Encounters, and Collections → Sea creatures defeated (docs/design/FISHING_EQUIPMENT.md
 * §2): every sea creature, how many encounters with it are banked, whether it was defeated, where it lives and what it
 * drops (the drops make rod parts). Clicking a banked one chooses it for the next Sneak-cast (kept for the session, as
 * {@code /fishing call} does). Spending, refunds and limits are Fishing's, unchanged.
 */
public final class FishingEncountersMenu extends PlayerMenu {

    private static final int[] SLOTS = {19, 20, 21, 22, 23, 24, 25, 28, 29, 30, 31, 32, 33, 34, 37, 38, 39, 40, 41, 42, 43};
    private int page;

    public FishingEncountersMenu(CerberusPlugin plugin) {
        super(plugin);
    }

    @Override public String title() { return PlayerTheme.title("Encounters"); }

    @Override protected Material headerGlass() { return Material.CYAN_STAINED_GLASS_PANE; }

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
        String sel = f.selected(p);

        Ui.Lore head = Ui.lore()
                .stat("Banked", String.valueOf(j.totalLures()))
                .stat("Defeated", j.creaturesDefeated().stream().filter(id -> r.creature(id) != null).count() + " of " + r.creatures().size() + " creatures");
        head.gap();
        for (String w : PlayerTheme.wrap("From Fishing " + r.techniques().creatures() + ", a catch can bank an encounter: a sea creature "
                + "that took your bait. Sneak and cast into water to call one and fight it when you're ready. If it gets away, "
                + "the encounter comes back.", SkillView.WIDTH)) head.text(w);
        String fight = f.fightOf(p.getUniqueId());
        if (fight != null) head.gap().raw(ChatColor.YELLOW + "Fighting now: " + fight.substring(0, fight.lastIndexOf(' ')).replace('_', ' '));
        v.set(MenuView.HEADER, Ui.item(Material.HEART_OF_THE_SEA, ChatColor.AQUA + "" + ChatColor.BOLD + "Encounters: " + j.totalLures(), head.build()));

        List<FishingRules.Creature> all = new ArrayList<>(r.creatures().values());
        page = GUIs.menu.Paging.clampPage(page, all.size(), SLOTS.length);
        int pages = GUIs.menu.Paging.pageCount(all.size(), SLOTS.length);
        List<FishingRules.Creature> shown = GUIs.menu.Paging.slice(all, page, SLOTS.length);
        for (int i = 0; i < shown.size(); i++) {
            FishingRules.Creature c = shown.get(i);
            int banked = j.lures(c.id());
            boolean chosen = c.id().equals(sel);
            ItemStack tile = tile(r, c, banked, j.defeated(c.id()), chosen, level);
            if (banked <= 0) { v.set(SLOTS[i], tile); continue; }
            v.button(SLOTS[i], tile, click -> {
                Fishing ff = plugin.getFishing();
                if (ff == null) return;
                // Only one this player still has; the last one may have gone while the screen was open.
                if (ff.journal(click.player()).lures(c.id()) <= 0) { Ui.info(click.player(), "No " + c.name() + " encounter banked any more."); }
                else ff.select(click.player(), chosen ? null : c.id());
                menus.refresh(click.player());
            });
        }
        if (page > 0) v.button(MenuView.PREV, Ui.item(Material.SPECTRAL_ARROW, ChatColor.YELLOW + "Previous page",
                Ui.lore().stat("Page", page + " / " + pages).build()), c -> { page--; menus.refresh(c.player()); });
        if (page < pages - 1) v.button(MenuView.NEXT, Ui.item(Material.SPECTRAL_ARROW, ChatColor.YELLOW + "Next page",
                Ui.lore().stat("Page", (page + 2) + " / " + pages).build()), c -> { page++; menus.refresh(c.player()); });
        v.refreshButton();
    }

    private static ItemStack tile(FishingRules r, FishingRules.Creature c, int banked, boolean defeated, boolean chosen, int level) {
        Material egg = Material.matchMaterial(c.entity() + "_SPAWN_EGG");
        Material icon = banked > 0 || defeated ? (egg != null ? egg : Material.PRISMARINE_SHARD) : Material.GRAY_DYE;
        Ui.Lore l = Ui.lore().raw(c.rarity().color + c.rarity().displayName + (c.apex() ? ChatColor.GOLD + " · apex" : ""));
        l.stat("Banked", String.valueOf(banked));
        l.raw(defeated ? ChatColor.GREEN + "✔ Defeated" : ChatColor.DARK_GRAY + "✘ Not defeated yet");
        List<String> waters = new ArrayList<>();
        for (String w : c.waters()) waters.add(r.water(w) == null ? w : r.water(w).name());
        l.gap().stat("Waters", String.join(", ", waters));
        int from = c.apex() ? Math.max(c.level(), r.techniques().apex()) : Math.max(c.level(), r.techniques().creatures());
        l.raw((level >= from ? ChatColor.GREEN + "✓ " : ChatColor.RED + "✕ ") + ChatColor.WHITE + "Fishing " + from + ChatColor.DARK_GRAY + " (you: " + level + ")");
        if (!c.drops().isEmpty()) {
            l.gap().text("Drops:");
            for (FishingRules.Drop d : c.drops()) {
                GatheringCatalog.ItemDef def = GatheringCatalog.current().item(d.item());
                String name = def != null ? def.name() : d.item().toLowerCase(Locale.ROOT).replace('_', ' ');
                l.raw(ChatColor.GRAY + "  " + name + ChatColor.DARK_GRAY + " (" + Math.round(d.chance() * 100) + "%)");
            }
        }
        if (banked > 0) {
            l.gap().raw(chosen ? ChatColor.AQUA + "▶ Chosen: your next Sneak-cast calls it" : ChatColor.GRAY + "A Sneak-cast calls one of the water's");
            l.hint(chosen ? "Click to stop choosing it" : "Click to choose it for your next Sneak-cast");
        }
        ItemStack it = Ui.item(icon, (banked > 0 || defeated ? c.rarity().color : ChatColor.GRAY) + c.name()
                + (banked > 0 ? ChatColor.GRAY + " ×" + banked : ""), l.build());
        if (banked > 1) Ui.amount(it, banked);
        return Ui.glow(it, chosen);
    }
}
