package GUIs.player;

import Attributes.Stat;
import GUIs.menu.Fmt;
import GUIs.menu.MenuView;
import GUIs.menu.Ui;
import Gathering.FishingGear;
import Gathering.FishingRules;
import Gathering.GatheringCatalog;
import Gathering.GearItems;
import Gathering.Tackle;
import Gathering.TackleRules;
import cerberus.world.cerb.CerberusPlugin;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Player Menu → Skills → Fishing → Equipment (docs/design/FISHING_EQUIPMENT.md §5): the three presets (click to use one
 * from the next cast, shift-click to rename), the four categories with what the active preset holds in each, the Bait
 * Bag, the setup's effect now, an old upgraded rod in hand to convert, and the reel and restock switches. Every number
 * comes from the attribute engine; every action is re-checked by {@link Tackle} when it runs.
 */
public final class FishingEquipmentMenu extends PlayerMenu {

    private static final int[] PRESET_SLOTS = {11, 13, 15};
    private static final int[] CATEGORY_SLOTS = {19, 21, 23, 25};

    public FishingEquipmentMenu(CerberusPlugin plugin) {
        super(plugin);
    }

    @Override public String title() { return PlayerTheme.title("Fishing Equipment"); }

    @Override protected Material headerGlass() { return Material.LIGHT_BLUE_STAINED_GLASS_PANE; }

    @Override
    protected void content(MenuView v) {
        Player p = v.viewer();
        Tackle tk = plugin.getTackle();
        TackleRules t = GatheringCatalog.current().tackle();
        if (tk == null || !t.enabled()) {
            v.set(22, Ui.item(Material.BARRIER, ChatColor.GRAY + "Fishing equipment is off on this server", List.of()));
            return;
        }
        if (!loaded(p)) { loading(v); return; }
        FishingGear g = tk.gear(p);
        int level = tk.level(p);
        Set<String> active = tk.active(p);

        // ── header: what the setup does now ──
        Ui.Lore head = Ui.lore();
        for (String w : PlayerTheme.wrap("Any normal fishing rod you fish with uses your active preset. Its enchantments and "
                + "durability stay the rod's own.", SkillView.WIDTH)) head.text(w);
        head.gap().stat("Active preset", g.activePreset().name());
        statLines(head, tk.preview(p, g.activePreset().parts()), false);
        if (!tk.writable(p)) head.gap().warn("Saved by a newer version: read-only");
        v.set(MenuView.HEADER, Ui.item(Material.FISHING_ROD, ChatColor.AQUA + "" + ChatColor.BOLD + "Fishing Equipment", head.build()));

        // ── presets ──
        for (int i = 0; i < FishingGear.PRESETS; i++) {
            int idx = i;
            FishingGear.Preset pr = g.preset(i);
            boolean on = i == g.activeIndex();
            Ui.Lore l = Ui.lore();
            l.raw(on ? ChatColor.AQUA + "▶ In use" : ChatColor.GRAY + "Not in use");
            List<String> names = new ArrayList<>();
            for (String id : pr.parts()) {
                TackleRules.Part part = t.part(id);
                names.add(part == null ? id + " (unavailable)" : part.name());
            }
            l.gap().text("Parts:");
            if (names.isEmpty()) l.raw(ChatColor.DARK_GRAY + "  none yet");
            else for (String n : names) l.raw(ChatColor.WHITE + "  " + n);
            StringBuilder slots = new StringBuilder();
            for (int s = 0; s < t.bagSlots(level); s++) slots.append(s > 0 ? ", " : "").append(s + 1).append(pr.usesSlot(s) ? "" : " (skipped)");
            l.stat("Bait slots", slots.toString());
            if (!on) {
                l.gap().text("Switching to it:");
                statLines(l, tk.preview(p, pr.parts()), true);
                l.hint("Click: use it from your next cast");
            }
            l.raw(ChatColor.YELLOW + "▸ Shift-click: rename");
            ItemStack tile = Ui.glow(Ui.item(on ? Material.FISHING_ROD : Material.STICK, (on ? ChatColor.AQUA : ChatColor.YELLOW) + pr.name(), l.build()), on);
            v.button(PRESET_SLOTS[i], tile, c -> {
                Tackle tt = plugin.getTackle();
                if (tt == null) return;
                if (c.isShift()) { menus.close(c.player()); tt.askRename(c.player(), idx); return; }
                say(c.player(), tt.activate(c.player(), idx));
                menus.refresh(c.player());
            });
        }

        // ── categories ──
        int k = 0;
        for (TackleRules.Category cat : t.categories().values()) {
            if (k >= CATEGORY_SLOTS.length) break;
            boolean open = level >= cat.unlock();
            List<String> inCat = new ArrayList<>();
            int owned = 0, all = 0;
            for (TackleRules.Part part : t.parts().values()) {
                if (!part.category().equals(cat.id())) continue;
                all++;
                if (g.owns(part.id())) owned++;
                if (g.activePreset().parts().contains(part.id())) inCat.add(part.name());
            }
            Ui.Lore l = Ui.lore();
            l.raw(open ? ChatColor.GREEN + "✓ Open" : ChatColor.RED + "✕ Opens at Fishing " + cat.unlock() + ChatColor.DARK_GRAY + " (you: " + level + ")");
            l.stat("Owned", owned + " of " + all);
            l.gap().text("In " + g.activePreset().name() + ":");
            if (inCat.isEmpty()) l.raw(ChatColor.DARK_GRAY + "  nothing");
            else for (String n : inCat) l.raw(ChatColor.WHITE + "  " + n + (open ? "" : ChatColor.DARK_GRAY + " (waits)"));
            l.hint("Click to see its parts");
            Material icon = switch (cat.id()) {
                case "rod" -> Material.FISHING_ROD;
                case "line" -> Material.STRING;
                case "lure" -> Material.TRIPWIRE_HOOK;
                case "reel" -> Material.CLOCK;
                default -> Material.CHEST;
            };
            v.button(CATEGORY_SLOTS[k++], Ui.item(open ? icon : Material.GRAY_DYE, (open ? ChatColor.GOLD : ChatColor.GRAY) + cat.name(), l.build()),
                    c -> menus.open(c.player(), new TackleCategoryMenu(plugin, cat.id())));
        }

        // ── bait, the setup now, an old rod ──
        v.button(29, baitTile(t, g, level), c -> menus.open(c.player(), new BaitBagMenu(plugin)));
        v.set(31, setupTile(t, active, tk, p));
        ItemStack hand = p.getInventory().getItemInMainHand();
        if (hand.getType() == Material.FISHING_ROD && !GearItems.upgrades(hand).isEmpty()) {
            v.button(33, Ui.item(Material.FISHING_ROD, ChatColor.GOLD + "Upgraded rod in your hand", Ui.lore()
                    .text("It keeps working as it is. You can")
                    .text("move its upgrades into your setup")
                    .text("(they then work with any rod).")
                    .hint("Click for a preview").build()), c -> menus.open(c.player(), new LegacyRodMenu(plugin)));
        } else {
            v.set(33, Ui.item(Material.GRAY_DYE, ChatColor.GRAY + "Old upgraded rods", Ui.lore()
                    .text("A rod upgraded the old way keeps")
                    .text("working. Hold one and open this")
                    .text("screen to convert it.").build()));
        }

        // ── switches ──
        if (t.hasAutoReel(g.owned())) { // shown once the reel is owned
            boolean on = g.autoReel();
            boolean works = t.hasAutoReel(active);
            v.button(39, Ui.item(on ? Material.LIME_DYE : Material.GRAY_DYE, (on ? ChatColor.GREEN : ChatColor.GRAY) + "Auto-hook reel: " + (on ? "on" : "off"), Ui.lore()
                    .text("Reels in a real bite for you; you")
                    .text("still cast. Never a Sneak-cast call.")
                    .raw(works ? ChatColor.GREEN + "In use in " + g.activePreset().name() : ChatColor.GRAY + "Not in use now (Reel, Fishing " + catUnlock(t, "reel") + ")")
                    .hint("Click to turn " + (on ? "off" : "on")).build()), c -> {
                say(c.player(), plugin.getTackle().autoReel(c.player(), !on));
                menus.refresh(c.player());
            });
        }
        boolean restock = g.restock();
        v.button(41, Ui.item(restock ? Material.LIME_DYE : Material.GRAY_DYE, (restock ? ChatColor.GREEN : ChatColor.GRAY) + "Restock bait: " + (restock ? "on" : "off"), Ui.lore()
                .text("When a bag slot runs out, the same")
                .text("bait comes from your inventory.")
                .text("Never another bait, never a chest.")
                .hint("Click to turn " + (restock ? "off" : "on")).build()), c -> {
            say(c.player(), plugin.getTackle().restock(c.player(), !restock));
            menus.refresh(c.player());
        });
        List<String> gone = new ArrayList<>();
        for (String id : g.owned()) if (t.part(id) == null) gone.add(id);
        if (!gone.isEmpty()) {
            Ui.Lore l = Ui.lore().text("Still yours, but this server no longer").text("has them. They do nothing until").text("they come back:");
            for (String id : gone) l.raw(ChatColor.GRAY + "  " + id);
            v.set(40, Ui.item(Material.STRUCTURE_VOID, ChatColor.GRAY + "Unavailable parts: " + gone.size(), l.build()));
        }
        v.refreshButton();
    }

    private static int catUnlock(TackleRules t, String cat) {
        TackleRules.Category c = t.category(cat);
        return c == null ? 0 : c.unlock();
    }

    private ItemStack baitTile(TackleRules t, FishingGear g, int level) {
        Ui.Lore l = Ui.lore();
        int slots = t.bagSlots(level);
        for (int i = 0; i < FishingGear.SLOTS; i++) {
            if (i >= slots) { l.raw(ChatColor.DARK_GRAY + "Slot " + (i + 1) + ": opens at Fishing " + t.bagUnlock(i)); continue; }
            FishingGear.Slot s = g.slot(i);
            TackleRules.Bait b = s.empty() ? null : t.baitById(s.bait());
            l.raw(ChatColor.GRAY + "Slot " + (i + 1) + ": " + (s.empty() ? ChatColor.DARK_GRAY + "empty"
                    : (b == null ? ChatColor.RED + s.bait() + " (unavailable)" : ChatColor.WHITE + b.name()) + ChatColor.GRAY + " ×" + s.count()));
        }
        if (g.held() != null) l.raw(ChatColor.AQUA + "On your line: 1 " + (t.baitById(g.held().bait()) == null ? g.held().bait() : t.baitById(g.held().bait()).name()));
        l.gap().text("One bait at a time, one per catch.").text("No bait: normal fishing.");
        l.hint("Click to open the bag");
        return Ui.item(Material.BUNDLE, ChatColor.GOLD + "Bait Bag", l.build());
    }

    private ItemStack setupTile(TackleRules t, Set<String> active, Tackle tk, Player p) {
        Ui.Lore l = Ui.lore();
        TackleRules.Part sp = t.specialist(active);
        l.text("Counting now (" + active.size() + " part" + (active.size() == 1 ? "" : "s") + "):");
        for (String id : active) l.raw(ChatColor.WHITE + "  " + t.part(id).name());
        if (active.isEmpty()) l.raw(ChatColor.DARK_GRAY + "  nothing yet");
        if (sp != null) l.gap().raw(ChatColor.LIGHT_PURPLE + "Specialist: " + sp.name());
        if (t.hasAutoReel(active)) l.raw(ChatColor.AQUA + "Auto-hook reel" + (tk.gear(p).autoReel() ? "" : ChatColor.GRAY + " (switched off)"));
        l.gap();
        for (String w : PlayerTheme.wrap("Parts come from sea creatures' drops, treasure and heartwood: open a category to fit one.", SkillView.WIDTH)) l.text(w);
        return Ui.item(Material.ENCHANTED_BOOK, ChatColor.GOLD + "Your setup now", l.build());
    }

    /**
     * The fishing stats as the engine gives them: "Lure speed: 9.0% → 12.7%" with the raw change, "no meaningful gain" when
     * a cap eats it, and when each applies. {@code diffOnly}: only the stats that change.
     */
    static void statLines(Ui.Lore l, List<Tackle.StatChange> changes, boolean diffOnly) {
        boolean any = false;
        for (Tackle.StatChange s : changes) {
            if (diffOnly && !s.changes()) continue;
            any = true;
            String name = s.stat().displayName;
            if (!s.enabled()) { l.raw(ChatColor.GRAY + name + ": switched off on this server"); continue; }
            if (!s.changes()) { l.raw(ChatColor.AQUA + name + ": " + ChatColor.WHITE + Fmt.pct(s.now())); continue; }
            String raw = ChatColor.DARK_GRAY + " (" + (s.raw() >= 0 ? "+" : "") + Fmt.pct(s.raw()) + " raw)";
            if (s.capped()) l.raw(ChatColor.AQUA + name + ": " + ChatColor.WHITE + Fmt.pct(s.now()) + ChatColor.GRAY + " no meaningful change (cap)" + raw);
            else l.raw(ChatColor.AQUA + name + ": " + ChatColor.WHITE + Fmt.pct(s.now()) + " → " + (s.then() >= s.now() ? ChatColor.GREEN : ChatColor.RED)
                    + Fmt.pct(s.then()) + raw);
            l.raw(ChatColor.DARK_GRAY + "  " + when(s.stat()));
        }
        if (diffOnly && !any) l.raw(ChatColor.GRAY + "No change to your stats");
    }

    /** When a stat applies (unconditional or an opportunity that depends on place and level). */
    static String when(Stat s) {
        FishingRules r = GatheringCatalog.current().fishing();
        return switch (s) {
            case LURE_SPEED -> "always, with any rod";
            case TREASURE_CHANCE -> "in open water, from Fishing " + r.techniques().treasureSense();
            case SEA_CREATURE_CHANCE -> "from Fishing " + r.techniques().creatures() + ", where a creature lives";
            default -> "";
        };
    }

    static void say(Player p, Tackle.Result r) {
        if (r == null) return;
        if (r.ok()) Ui.ok(p, r.message());
        else Ui.fail(p, r.message());
    }

    /** The parts of the active preset with {@code id} put in or taken out (a specialist replaces the other). */
    static Set<String> toggled(TackleRules t, Set<String> current, String id) {
        Set<String> out = new LinkedHashSet<>(current);
        if (!out.remove(id)) {
            TackleRules.Part part = t.part(id);
            if (part != null && part.specialist()) out.removeIf(o -> t.part(o) != null && t.part(o).specialist());
            out.add(id);
        }
        return out;
    }
}
