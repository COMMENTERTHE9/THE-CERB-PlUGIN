package GUIs.player;

import Attributes.AttributeService;
import Attributes.AttributeSnapshot;
import Attributes.Contribution;
import Effects.ActiveEffect;
import Effects.ActiveEffects;
import Effects.EffectBenefits;
import Effects.EffectsText;
import GUIs.menu.MenuView;
import GUIs.menu.Paging;
import GUIs.menu.Ui;
import cerberus.world.cerb.CerberusPlugin;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Player Menu → your head → Active Effects (docs/design/ACTIVE_EFFECTS.md): every effect on you now, meal first, each with
 * its source, what it does (worked out by the attribute engine with and without it), and its time left, counting down
 * while the screen is open. The equipped passives and keystone apart, as always on. Reads only in-memory state.
 */
public final class ActiveEffectsMenu extends PlayerMenu {

    /** At most this many effects get a tile (the content area); more are counted on the last one. */
    private static final int MAX_TILES = Paging.CONTENT_SLOTS.length;
    private static final int ALWAYS_ON_SLOT = 47, STATS_SLOT = 51;

    public ActiveEffectsMenu(CerberusPlugin plugin) {
        super(plugin);
    }

    @Override public String title() { return PlayerTheme.title("Active Effects"); }

    @Override protected Material headerGlass() { return Material.PURPLE_STAINED_GLASS_PANE; }

    @Override
    protected void content(MenuView v) {
        Player p = v.viewer();
        v.button(STATS_SLOT, Ui.item(Material.BLAZE_POWDER, PlayerTheme.PRIMARY + "Stats & Attributes", Ui.lore()
                .text("Your permanent numbers: attributes,")
                .text("damage, survivability and magic.").hint("Click to open").build()), c -> menus.open(c.player(), new GUIs.stats.StatsMenu(plugin)));
        if (!loaded(p)) { loading(v); return; }
        ActiveEffects registry = plugin.getActiveEffects();
        long now = System.currentTimeMillis();
        List<ActiveEffect> all = registry == null ? List.of() : registry.of(p, now);
        List<ActiveEffect> temp = ActiveEffects.temporary(all);
        List<ActiveEffect> always = ActiveEffects.alwaysOn(all);
        boolean pvp = paused(p);

        // What each effect does: worked out once per drawing (the countdown only rewrites the times).
        Map<String, List<String>> benefits = new HashMap<>();
        for (ActiveEffect e : temp) benefits.put(e.key(), benefits(p, e.contributions(), pvp));

        v.set(MenuView.HEADER, Ui.head(p, ChatColor.LIGHT_PURPLE + "" + ChatColor.BOLD + "Active Effects", headerLore(temp, now, pvp)));
        if (temp.isEmpty()) {
            v.set(22, Ui.item(Material.GLASS_BOTTLE, ChatColor.GRAY + "No active effects", Ui.lore()
                    .text("Meals, ability buffs, potions and")
                    .text("admin test buffs show here, with")
                    .text("what they do and their time left.")
                    .gap().text("Your permanent skill attributes are")
                    .text("on Stats & Attributes.").build()));
        }
        List<String> shown = new ArrayList<>();
        for (int i = 0; i < temp.size() && i < MAX_TILES; i++) {
            ActiveEffect e = temp.get(i);
            int more = i == MAX_TILES - 1 ? temp.size() - MAX_TILES : 0;
            v.set(Paging.CONTENT_SLOTS[i], tile(e, benefits.get(e.key()), now, more));
            shown.add(e.key());
        }
        if (!always.isEmpty()) v.set(ALWAYS_ON_SLOT, alwaysOnTile(p, always, pvp));

        // Count down while open (docs/design/ACTIVE_EFFECTS.md §3): a second at a time, only the times; the screen is drawn
        // again when an effect ends or another starts, so nothing ended ever shows as running.
        if (temp.isEmpty()) return;
        v.every(20, () -> {
            if (!p.isOnline()) return false;
            long t = System.currentTimeMillis();
            List<ActiveEffect> nowTemp = ActiveEffects.temporary(registry.of(p, t));
            if (!ActiveEffects.keys(nowTemp).equals(shown) || paused(p) != pvp) {
                menus.refresh(p);
                return false; // the new drawing keeps its own countdown
            }
            ItemStack head = v.get(MenuView.HEADER);
            if (head != null) v.set(MenuView.HEADER, withLore(head, headerLore(nowTemp, t, pvp)));
            for (int i = 0; i < nowTemp.size() && i < MAX_TILES; i++) {
                ActiveEffect e = nowTemp.get(i);
                int more = i == MAX_TILES - 1 ? nowTemp.size() - MAX_TILES : 0;
                v.set(Paging.CONTENT_SLOTS[i], tile(e, benefits.getOrDefault(e.key(), List.of()), t, more));
            }
            return true;
        });
    }

    /** The header's lines: how many effects, the summary (as on the hub's head), and what PvP pauses right now. */
    private static List<String> headerLore(List<ActiveEffect> temp, long now, boolean pvp) {
        Ui.Lore l = Ui.lore().stat("Active effects", String.valueOf(temp.size())).gap();
        for (String line : summary(temp, now, 6)) l.raw(line);
        if (pvp) {
            l.gap().warn("PvP combat: speed and handling bonuses")
                    .raw(ChatColor.RED + "  are paused, and a meal's healing waits.");
        }
        if (!temp.isEmpty()) l.gap().text("Times count down while this is open.");
        return l.build();
    }

    /**
     * The summary lines the hub's head and this screen's header show: "Hearty Stew — 8m 42s remaining", meal first, at
     * most {@code max} (then "+N more"); "No active effects" when there are none.
     */
    public static List<String> summary(List<ActiveEffect> temp, long now, int max) {
        List<String> out = new ArrayList<>();
        if (temp.isEmpty()) {
            out.add(ChatColor.DARK_GRAY + "No active effects");
            return out;
        }
        for (int i = 0; i < temp.size() && i < max; i++) {
            ActiveEffect e = temp.get(i);
            String time = e.timed() ? ChatColor.WHITE + EffectsText.remaining(e.remaining(now)) + " remaining"
                    : ChatColor.GRAY + (e.endsWhen() != null ? e.endsWhen() : "no time limit");
            out.add(color(e) + e.name() + ChatColor.DARK_GRAY + " — " + time);
        }
        if (temp.size() > max) out.add(ChatColor.DARK_GRAY + "+" + (temp.size() - max) + " more");
        return out;
    }

    /** One effect's tile: name and time left, source, what it does now, its inner part (a meal's restoration). */
    private static ItemStack tile(ActiveEffect e, List<String> benefits, long now, int more) {
        String time = e.timed() ? ChatColor.WHITE + EffectsText.remaining(e.remaining(now)) + " remaining"
                : ChatColor.GRAY + (e.endsWhen() != null ? e.endsWhen() : "no time limit");
        Ui.Lore l = Ui.lore().raw(ChatColor.GRAY + e.source());
        if (!benefits.isEmpty() || !e.notes().isEmpty()) {
            l.gap().raw(ChatColor.YELLOW + "What it does now:");
            for (String b : benefits) block(l, b);
            for (String n : e.notes()) l.raw(ChatColor.GRAY + n);
        }
        ActiveEffect.Phase ph = e.phase();
        if (ph != null) {
            l.gap();
            if (ph.running(now)) {
                l.raw(ChatColor.YELLOW + ph.label() + ": " + ChatColor.WHITE + EffectsText.remaining(ph.endsAt() - now) + " left"
                        + (ph.paused() ? ChatColor.RED + " (paused: PvP)" : ""));
                l.raw(ChatColor.GRAY + "  " + ph.text());
            } else {
                l.raw(ChatColor.DARK_GRAY + ph.label() + ": " + ph.text());
            }
        }
        if (e.timed()) l.gap().raw(ChatColor.DARK_GRAY + "Ends in " + EffectsText.remaining(e.remaining(now)) + ".");
        if (more > 0) l.gap().raw(ChatColor.DARK_GRAY + "+" + more + " more effect" + (more == 1 ? "" : "s") + " not shown");
        return Ui.item(e.icon() != null ? e.icon() : Material.POTION, color(e) + e.name() + ChatColor.DARK_GRAY + " — " + time, l.build());
    }

    /** "Vitality +6 · max health 103 → 109": the first part on its line, what it changes below it. */
    private static void block(Ui.Lore l, String line) {
        String[] parts = line.split(" · ");
        l.raw(ChatColor.WHITE + "• " + parts[0]);
        for (int i = 1; i < parts.length; i++) l.raw(ChatColor.GRAY + "   " + parts[i]);
    }

    /** The equipped passives and keystone: always on, not timed, with what they add. */
    private ItemStack alwaysOnTile(Player p, List<ActiveEffect> always, boolean pvp) {
        Ui.Lore l = Ui.lore().text("Not timed: they count whenever").text("they are equipped in the loadout")
                .text("you use (Classes & Abilities).");
        for (ActiveEffect e : always) {
            l.gap().raw(ChatColor.GOLD + e.name() + ChatColor.DARK_GRAY + "  " + e.source());
            for (String b : benefits(p, e.contributions(), pvp)) block(l, b);
        }
        return Ui.item(Material.NETHER_STAR, ChatColor.AQUA + "Always on (" + always.size() + ")", l.build());
    }

    /** What {@code cs} change for {@code p} now: the engine with them against without them. */
    private List<String> benefits(Player p, List<Contribution> cs, boolean pvp) {
        if (cs.isEmpty()) return List.of();
        AttributeService svc = plugin.getAttributeService();
        UUID id = p.getUniqueId();
        AttributeSnapshot with = svc.get(id);
        AttributeSnapshot without = svc.preview(id, list -> {
            List<Contribution> rest = new ArrayList<>(list);
            for (Contribution c : cs) rest.remove(c);
            return rest;
        });
        Gathering.GatheringCatalog gc = Gathering.GatheringCatalog.current();
        EffectBenefits.Context ctx = new EffectBenefits.Context(
                a -> svc.effect(a) == AttributeService.Effect.ACTIVE,
                s -> svc.secondaryEffect(s) == AttributeService.Effect.ACTIVE,
                pvp, gc.fortunePerPoint(), gc.fortuneMax(), Manager.AttributeEffects.BASE_MAX_HEALTH, PlayerHubMenu.bestPowerClass(with));
        return EffectBenefits.lines(cs, with, without, ctx);
    }

    /** True while PvP pauses this player's speed and handling bonuses (and a meal's healing waits). */
    private boolean paused(Player p) {
        return plugin.getSecondaryEffects() != null && plugin.getSecondaryEffects().paused(p.getUniqueId());
    }

    private static ChatColor color(ActiveEffect e) {
        return switch (e.category()) {
            case MEAL -> ChatColor.GOLD;
            case ABILITY -> ChatColor.YELLOW;
            case BUFF -> ChatColor.LIGHT_PURPLE;
            case POTION -> ChatColor.AQUA;
            case ALWAYS_ON -> ChatColor.WHITE;
        };
    }

    /** A copy of {@code item} with {@code lore} (the hub's head counting down: no new skull each second). */
    static ItemStack withLore(ItemStack item, List<String> lore) {
        ItemStack copy = item.clone();
        ItemMeta meta = copy.getItemMeta();
        if (meta == null) return copy;
        meta.setLore(lore);
        copy.setItemMeta(meta);
        return copy;
    }
}
