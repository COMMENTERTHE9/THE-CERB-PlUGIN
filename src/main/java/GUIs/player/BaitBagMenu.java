package GUIs.player;

import GUIs.menu.MenuView;
import GUIs.menu.Ui;
import Gathering.FishingGear;
import Gathering.GatheringCatalog;
import Gathering.GearItems;
import Gathering.Tackle;
import Gathering.TackleRules;
import cerberus.world.cerb.CerberusPlugin;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Fishing → Equipment → Bait Bag (docs/design/FISHING_EQUIPMENT.md §8): up to three slots of one bait each (the second
 * and third open with Fishing), which of them the active preset uses and in what order, the bait you carry to put in, and
 * how each bait is made. Clicks move items between the inventory and the bag only through {@link Tackle}, which checks
 * room and counts on every click (nothing here trusts the item shown).
 */
public final class BaitBagMenu extends PlayerMenu {

    private static final int[] SLOT_TILES = {20, 22, 24};
    private static final int[] LOAD_TILES = {29, 30, 31, 32, 33};
    private static final int[] RECIPE_TILES = {38, 39, 40, 41, 42};

    public BaitBagMenu(CerberusPlugin plugin) {
        super(plugin);
    }

    @Override public String title() { return PlayerTheme.title("Bait Bag"); }

    /** Its buttons act at once: always its own window, so a click aimed at the screen before never lands here. */
    @Override public boolean reuseWindow() { return false; }

    @Override protected Material headerGlass() { return Material.BROWN_STAINED_GLASS_PANE; }

    @Override
    protected void content(MenuView v) {
        Player p = v.viewer();
        Tackle tk = plugin.getTackle();
        TackleRules t = GatheringCatalog.current().tackle();
        if (tk == null || !t.enabled()) {
            v.set(22, Ui.item(Material.BARRIER, ChatColor.GRAY + "Bait is off on this server", List.of()));
            return;
        }
        if (!loaded(p)) { loading(v); return; }
        FishingGear g = tk.gear(p);
        int level = tk.level(p);
        int open = t.bagSlots(level);
        int preset = g.activeIndex();

        Ui.Lore head = Ui.lore();
        for (String w : PlayerTheme.wrap("One bait works at a time: the first slot " + g.activePreset().name() + " uses whose bait can apply "
                + "now. It is used up only when a catch completes and it applied; anything else puts it back. An empty bag is "
                + "normal fishing.", SkillView.WIDTH)) head.text(w);
        if (g.held() != null) {
            TackleRules.Bait hb = t.baitById(g.held().bait());
            head.gap().raw(ChatColor.AQUA + "On your line now: 1 " + (hb == null ? g.held().bait() : hb.name()));
        }
        v.set(MenuView.HEADER, Ui.item(Material.BUNDLE, ChatColor.GOLD + "" + ChatColor.BOLD + "Bait Bag", head.build()));

        for (int i = 0; i < FishingGear.SLOTS; i++) {
            int slot = i;
            FishingGear.Slot s = g.slot(i);
            if (i >= open) {
                if (s.empty()) {
                    v.set(SLOT_TILES[i], Ui.item(Material.GRAY_STAINED_GLASS_PANE, ChatColor.GRAY + "Slot " + (i + 1) + ": locked", Ui.lore()
                            .text("Opens at Fishing " + t.bagUnlock(i) + ".").build()));
                } else { // filled while it was open (a lower level now): never hidden, always takeable
                    TackleRules.Bait lb = t.baitById(s.bait());
                    v.button(SLOT_TILES[i], Ui.item(Material.GRAY_STAINED_GLASS_PANE, ChatColor.GRAY + "Slot " + (i + 1) + ": locked, holds "
                            + (lb == null ? s.bait() : lb.name()) + " ×" + s.count(), Ui.lore()
                            .text("Opens at Fishing " + t.bagUnlock(i) + "; not used").text("until then.")
                            .hint("Click: take it out (what fits)").build()), c -> {
                        Tackle tt = plugin.getTackle();
                        if (tt == null || !c.isMouse() || justDrawn(c.player())) return; // a click only, never a number key, a drop or a double-click's second half
                        FishingEquipmentMenu.say(c.player(), tt.unload(c.player(), slot));
                        menus.refresh(c.player());
                    });
                }
                continue;
            }
            boolean uses = g.preset(preset).usesSlot(i);
            Ui.Lore l = Ui.lore();
            TackleRules.Bait b = s.empty() ? null : t.baitById(s.bait());
            if (s.empty()) l.text("Empty.");
            else if (b == null) l.raw(ChatColor.RED + "Unavailable on this server: kept, not used.");
            else {
                l.raw(purpose(b));
                l.raw(applies(tk, b, p, level));
            }
            l.gap().raw(uses ? ChatColor.GREEN + "✔ " + g.activePreset().name() + " uses this slot" : ChatColor.GRAY + "✘ " + g.activePreset().name() + " skips this slot");
            if (!s.empty()) l.raw(ChatColor.YELLOW + "▸ Click: take it out (what fits)");
            l.raw(ChatColor.YELLOW + "▸ Right-click: " + (uses ? "skip it in " : "use it in ") + g.activePreset().name());
            if (i > 0) l.raw(ChatColor.YELLOW + "▸ Shift-click: move up (tried earlier)");
            String name = s.empty() ? ChatColor.GRAY + "Slot " + (i + 1) + ": empty"
                    : ChatColor.WHITE + "Slot " + (i + 1) + ": " + (b == null ? s.bait() : b.name()) + ChatColor.GRAY + " ×" + s.count();
            Material icon = s.empty() || b == null ? Material.LIGHT_GRAY_STAINED_GLASS_PANE : iconOf(b);
            ItemStack tile = Ui.item(icon, name, l.build());
            if (!s.empty()) Ui.amount(tile, Math.min(64, s.count()));
            v.button(SLOT_TILES[i], Ui.glow(tile, uses && !s.empty()), c -> {
                Tackle tt = plugin.getTackle();
                if (tt == null || !c.isMouse() || justDrawn(c.player())) return; // a click only, never a number key, a drop or a double-click's second half
                FishingGear gg = tt.gear(c.player());
                if (c.isShift()) { if (slot > 0) FishingEquipmentMenu.say(c.player(), tt.swapSlots(c.player(), slot, slot - 1)); }
                else if (c.isRight()) FishingEquipmentMenu.say(c.player(), tt.useSlot(c.player(), gg.activeIndex(), slot, !gg.preset(gg.activeIndex()).usesSlot(slot)));
                else if (!gg.slot(slot).empty()) FishingEquipmentMenu.say(c.player(), tt.unload(c.player(), slot));
                menus.refresh(c.player());
            });
        }

        // The bait you carry, to put in: the slot holding that bait, else the first empty open slot.
        Map<String, Integer> carried = new LinkedHashMap<>();
        for (TackleRules.Bait b : t.bait().values()) {
            int n = 0;
            for (ItemStack it : p.getInventory().getStorageContents()) if (it != null && b.item().equals(GearItems.itemId(it))) n += it.getAmount();
            if (n > 0) carried.put(b.id(), n);
        }
        int k = 0;
        for (Map.Entry<String, Integer> e : carried.entrySet()) {
            if (k >= LOAD_TILES.length) break;
            TackleRules.Bait b = t.baitById(e.getKey());
            int target = targetSlot(g, b.id(), open);
            Ui.Lore l = Ui.lore().raw(purpose(b)).stat("You carry", String.valueOf(e.getValue()));
            if (target < 0) l.raw(ChatColor.RED + "No slot free for it: empty one first");
            else l.hint("Click: put it into slot " + (target + 1));
            v.button(LOAD_TILES[k++], Ui.item(iconOf(b), ChatColor.YELLOW + "Put in: " + b.name(), l.build()), c -> {
                Tackle tt = plugin.getTackle();
                if (tt == null || !c.isMouse() || justDrawn(c.player())) return;
                int to = targetSlot(tt.gear(c.player()), b.id(), t.bagSlots(tt.level(c.player())));
                if (to < 0) Ui.fail(c.player(), "No free slot for " + b.name() + ": take another bait out first.");
                else FishingEquipmentMenu.say(c.player(), tt.load(c.player(), to, b.id()));
                menus.refresh(c.player());
            });
        }
        if (carried.isEmpty()) {
            v.set(31, Ui.item(Material.GRAY_DYE, ChatColor.GRAY + "No bait in your inventory", Ui.lore()
                    .text("Craft some at a crafting table (the").text("recipes are below and in your").text("recipe book).").build()));
        }

        // How each bait is made.
        int r = 0;
        for (TackleRules.Recipe rec : t.recipes().values()) {
            if (r >= RECIPE_TILES.length) break;
            TackleRules.Bait b = t.baitOfItem(rec.result());
            GatheringCatalog.ItemDef def = GatheringCatalog.current().item(rec.result());
            if (def == null) continue;
            Ui.Lore l = Ui.lore();
            if (b != null) l.raw(purpose(b));
            l.text("Crafting table, any shape:");
            for (List<Material> any : rec.ingredients()) l.raw(ChatColor.WHITE + "  " + String.join(" or ", any.stream().map(m -> pretty(m.name())).toList()));
            l.stat("Makes", String.valueOf(rec.makes()));
            boolean can = tk.mayCraft(p, rec);
            if (!can) l.raw(ChatColor.RED + "Locked: a collection milestone opens it");
            v.set(RECIPE_TILES[r++], Ui.item(can ? def.material() : Material.GRAY_DYE, (can ? ChatColor.GOLD : ChatColor.GRAY) + "Recipe: " + def.name(), l.build()));
        }
        v.refreshButton();
    }

    /** The slot {@code bait} goes into: the open slot holding it, else the first empty open slot; -1 when none. */
    static int targetSlot(FishingGear g, String bait, int open) {
        for (int i = 0; i < open; i++) if (!g.slot(i).empty() && bait.equals(g.slot(i).bait())) return i;
        for (int i = 0; i < open; i++) if (g.slot(i).empty()) return i;
        return -1;
    }

    static String purpose(TackleRules.Bait b) {
        return switch (b.purpose()) {
            case SPEED -> ChatColor.AQUA + "Bites come sooner: lure speed +" + GUIs.menu.Fmt.pct(b.amount()) + " (raw)";
            case ENCOUNTERS -> ChatColor.AQUA + "Encounters: sea-creature chance +" + GUIs.menu.Fmt.pct(b.amount()) + " (raw)";
            case NIGHT -> ChatColor.AQUA + "At night: night-only species ×" + trim(b.factor());
            case DISCOVERY -> ChatColor.AQUA + "Species you haven't caught ×" + trim(b.factor());
        };
    }

    private static String applies(Tackle tk, TackleRules.Bait b, Player p, int level) {
        return switch (b.purpose()) {
            case NIGHT -> isNight(p) ? ChatColor.GREEN + "Applies now (night)" : ChatColor.GRAY + "Waits for the night (stays in the bag)";
            case ENCOUNTERS -> level >= GatheringCatalog.current().fishing().techniques().creatures() ? ChatColor.GREEN + "Applies where a creature lives"
                    : ChatColor.GRAY + "From Fishing " + GatheringCatalog.current().fishing().techniques().creatures() + " (stays in the bag)";
            case DISCOVERY -> ChatColor.GREEN + "Applies where an undiscovered species bites";
            case SPEED -> ChatColor.GREEN + "Applies to every cast";
        };
    }

    private static boolean isNight(Player p) {
        long t = p.getWorld().getTime() % 24000;
        return t >= 13000 && t < 23000;
    }

    private static Material iconOf(TackleRules.Bait b) {
        GatheringCatalog.ItemDef def = GatheringCatalog.current().item(b.item());
        return def == null ? Material.WHEAT_SEEDS : def.material();
    }

    private static String pretty(String material) {
        String s = material.toLowerCase(java.util.Locale.ROOT).replace('_', ' ');
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    private static String trim(double v) {
        return v == Math.rint(v) ? String.valueOf((long) v) : String.valueOf(v);
    }
}
