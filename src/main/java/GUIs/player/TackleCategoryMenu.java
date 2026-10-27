package GUIs.player;

import GUIs.menu.ConfirmMenu;
import GUIs.menu.MenuView;
import GUIs.menu.Ui;
import Gathering.FishingGear;
import Gathering.GatheringCatalog;
import Gathering.Tackle;
import Gathering.TackleRules;
import cerberus.world.cerb.CerberusPlugin;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Fishing → Equipment → one category (docs/design/FISHING_EQUIPMENT.md §5): each part of the category, owned or not, in
 * the active preset or not, with the effective change installing or removing it would make (from the attribute engine,
 * after caps), the specialist it would replace, and what fitting an unowned one uses. Fitting asks first: it uses an item.
 */
public final class TackleCategoryMenu extends PlayerMenu {

    private static final int[] SLOTS = {19, 20, 21, 22, 23, 24, 25, 28, 29, 30, 31, 32, 33, 34};
    private final String category;

    public TackleCategoryMenu(CerberusPlugin plugin, String category) {
        super(plugin);
        this.category = category;
    }

    @Override public String title() {
        TackleRules.Category c = GatheringCatalog.current().tackle().category(category);
        return PlayerTheme.title("Equipment: " + (c == null ? category : c.name()));
    }

    @Override protected Material headerGlass() { return Material.LIGHT_BLUE_STAINED_GLASS_PANE; }

    /** Its buttons act at once (a part in or out): always its own window, so a click aimed at the screen before never lands here. */
    @Override public boolean reuseWindow() { return false; }

    @Override
    protected void content(MenuView v) {
        Player p = v.viewer();
        Tackle tk = plugin.getTackle();
        TackleRules t = GatheringCatalog.current().tackle();
        TackleRules.Category cat = t.category(category);
        if (tk == null || cat == null) {
            v.set(22, Ui.item(Material.BARRIER, ChatColor.GRAY + "This category isn't on this server", List.of()));
            return;
        }
        if (!loaded(p)) { loading(v); return; }
        FishingGear g = tk.gear(p);
        int level = tk.level(p);
        boolean open = level >= cat.unlock();
        String preset = g.activePreset().name();
        Ui.Lore head = Ui.lore()
                .raw(open ? ChatColor.GREEN + "✓ Open" : ChatColor.RED + "✕ Opens at Fishing " + cat.unlock() + ChatColor.DARK_GRAY + " (you: " + level + ")")
                .stat("Editing", preset);
        head.gap();
        for (String w : PlayerTheme.wrap("This category holds every part of its kind you put in: they work together. "
                + "Parts are yours for good and work with any rod.", SkillView.WIDTH)) head.text(w);
        if (category.equals("lure")) head.gap().raw(ChatColor.LIGHT_PURPLE + "One specialist at a time per preset.");
        v.set(MenuView.HEADER, Ui.item(Material.FISHING_ROD, ChatColor.GOLD + "" + ChatColor.BOLD + cat.name(), head.build()));

        List<TackleRules.Part> parts = new ArrayList<>();
        for (TackleRules.Part part : t.parts().values()) if (part.category().equals(category)) parts.add(part);
        for (int i = 0; i < parts.size() && i < SLOTS.length; i++) {
            TackleRules.Part part = parts.get(i);
            v.button(SLOTS[i], tile(tk, t, g, p, part, open, level), c -> { if (!justDrawn(c.player())) clicked(c.player(), part); });
        }
        if (parts.isEmpty()) v.set(31, Ui.item(Material.GRAY_DYE, ChatColor.GRAY + "No parts in this category yet", List.of()));
        v.refreshButton();
    }

    private ItemStack tile(Tackle tk, TackleRules t, FishingGear g, Player p, TackleRules.Part part, boolean open, int level) {
        GatheringCatalog c = GatheringCatalog.current();
        boolean owned = g.owns(part.id());
        boolean in = g.activePreset().parts().contains(part.id());
        Ui.Lore l = Ui.lore();
        l.raw(effect(part, c));
        if (owned) l.raw(in ? ChatColor.GREEN + "✔ In " + g.activePreset().name() : ChatColor.GRAY + "Owned, not in " + g.activePreset().name());
        else l.raw(ChatColor.DARK_GRAY + "✘ Not owned yet");
        if (in && open && part.specialist() && !tk.active(p).contains(part.id())) {
            l.raw(ChatColor.YELLOW + "Not counting: another specialist is in it (one at a time)");
        }
        if (!open) l.raw(ChatColor.RED + "Waits until Fishing " + t.category(part.category()).unlock());
        if (owned) {
            l.gap().text(in ? "Taking it out:" : "Putting it in:");
            FishingEquipmentMenu.statLines(l, tk.preview(p, FishingEquipmentMenu.toggled(t, g.activePreset().parts(), part.id())), true);
            if (!in && part.specialist()) {
                for (String other : g.activePreset().parts()) {
                    TackleRules.Part o = t.part(other);
                    if (o != null && o.specialist()) l.raw(ChatColor.RED + "Removes: " + o.name() + " (one specialist at a time)");
                }
            }
            l.hint(in ? "Click: take it out of " + g.activePreset().name() : "Click: put it into " + g.activePreset().name());
        } else if (part.item() != null) {
            GatheringCatalog.ItemDef def = c.item(part.item());
            int have = def == null ? 0 : countItem(p, part.item());
            l.gap().stat("Made with", "1 " + (def == null ? part.item() : def.name()) + ChatColor.DARK_GRAY + " (you carry " + have + ")");
            if (def != null) for (String w : PlayerTheme.wrap(source(part.item()), SkillView.WIDTH)) l.raw(ChatColor.DARK_GRAY + w);
            if (have > 0) l.hint("Click: fit it (asks first)");
        } else {
            l.gap().text("Earned from a collection milestone").text("(Player Menu → Collections).");
        }
        Material icon = owned ? iconOf(part, c) : Material.GRAY_DYE;
        ItemStack it = Ui.item(icon, (owned ? (in ? ChatColor.GREEN : ChatColor.YELLOW) : ChatColor.GRAY) + part.name(), l.build());
        return Ui.glow(it, in);
    }

    private static Material iconOf(TackleRules.Part part, GatheringCatalog c) {
        GatheringCatalog.ItemDef def = part.item() == null ? null : c.item(part.item());
        if (def != null) return def.material();
        return switch (part.special()) {
            case DISCOVERY -> Material.SPYGLASS;
            case SPECIMEN -> Material.TRIPWIRE_HOOK;
            case AUTO_REEL -> Material.CLOCK;
            default -> Material.STRING;
        };
    }

    /** What a part does, plainly: an upgrade's raw stat, a special part's effect. */
    static String effect(TackleRules.Part part, GatheringCatalog c) {
        return switch (part.special()) {
            case NONE -> {
                GatheringCatalog.UpgradeDef u = c.upgrade(part.upgrade());
                yield u == null || u.stat() == null ? ChatColor.GRAY + "Unavailable on this server"
                        : ChatColor.AQUA + u.stat().displayName + " +" + GUIs.menu.Fmt.pct(u.amount()) + ChatColor.DARK_GRAY + " (raw, before caps)";
            }
            case AUTO_REEL -> ChatColor.AQUA + "Reels in each real bite for you; never recasts";
            case DISCOVERY -> ChatColor.LIGHT_PURPLE + "Species you haven't caught bite ×" + trim(part.value()) + " as often";
            case SPECIMEN -> ChatColor.LIGHT_PURPLE + "Heavier fish within each species' range";
        };
    }

    /** Where a part's item comes from, read from the rules: a recipe, fishing treasure, a creature's drop, heartwood. */
    static String source(String item) {
        GatheringCatalog c = GatheringCatalog.current();
        for (TackleRules.Recipe r : c.tackle().recipes().values()) {
            if (!r.result().equals(item)) continue;
            Map<String, Integer> counts = new LinkedHashMap<>();
            for (List<Material> any : r.ingredients()) {
                counts.merge(String.join(" or ", any.stream().map(m -> pretty(m.name())).toList()), 1, Integer::sum);
            }
            List<String> parts = new ArrayList<>();
            counts.forEach((name, n) -> parts.add(n > 1 ? n + " " + name : name));
            return "Crafting table: " + String.join(", ", parts);
        }
        Gathering.FishingRules f = c.fishing();
        for (Gathering.FishingRules.Treasure t : f.treasure()) {
            if (t.item().equals(item)) return "Fishing treasure (open water, Fishing " + Math.max(f.techniques().treasureSense(), t.level()) + "+)";
        }
        List<String> by = new ArrayList<>();
        for (Gathering.FishingRules.Creature cr : f.creatures().values()) {
            for (Gathering.FishingRules.Drop d : cr.drops()) if (d.item().equals(item)) { by.add(cr.name()); break; }
        }
        if (!by.isEmpty()) return "Dropped by " + String.join(", ", by) + " (Encounters)";
        if (item.equals(c.wood().heartwoodItem())) return "Heartwood: from Woodcutting's natural logs";
        return "Found while fishing";
    }

    private static String pretty(String material) {
        String s = material.toLowerCase(Locale.ROOT).replace('_', ' ');
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    private static int countItem(Player p, String itemId) {
        int n = 0;
        for (ItemStack it : p.getInventory().getStorageContents()) if (it != null && itemId.equals(Gathering.GearItems.itemId(it))) n += it.getAmount();
        return n;
    }

    private void clicked(Player p, TackleRules.Part part) {
        Tackle tk = plugin.getTackle();
        if (tk == null) return;
        FishingGear g = tk.gear(p);
        if (g.owns(part.id())) {
            FishingEquipmentMenu.say(p, tk.toggle(p, g.activeIndex(), part.id()));
            menus.refresh(p);
            return;
        }
        if (part.item() == null) { Ui.info(p, "The " + part.name() + " comes from a collection milestone."); return; }
        GatheringCatalog.ItemDef def = GatheringCatalog.current().item(part.item());
        if (def == null || countItem(p, part.item()) <= 0) { Ui.fail(p, "You need 1 " + (def == null ? part.item() : def.name()) + " to fit it."); return; }
        List<String> details = new ArrayList<>();
        details.add("Uses 1 " + def.name() + " from your inventory.");
        details.add("The part stays on your character for good");
        details.add("and works with any rod; it doesn't");
        details.add("turn back into the item.");
        TackleRules t = GatheringCatalog.current().tackle();
        List<Integer> into = t.defaultPresets(part.id(), GatheringCatalog.current().upgrades());
        List<String> names = new ArrayList<>();
        for (int i : into) names.add(g.preset(i).name());
        details.add(names.isEmpty() ? "It goes into no preset yet: put it in yourself." : "It goes into: " + String.join(", ", names) + ".");
        String partId = part.id();
        menus.open(p, new ConfirmMenu(menus, null, "Fit the " + part.name() + "?", details, pl -> {
            Tackle tt = plugin.getTackle();
            if (tt == null) return;
            FishingEquipmentMenu.say(pl, tt.fit(pl, partId));
            menus.refresh(pl);
        }));
    }

    private static String trim(double v) {
        return v == Math.rint(v) ? String.valueOf((long) v) : String.format(Locale.ROOT, "%.2f", v).replaceAll("0+$", "");
    }

}
