package GUIs.player;

import Attributes.AttributeService;
import Attributes.AttributeSnapshot;
import Attributes.Contribution;
import Attributes.WeaponClass;
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
 * its source, what it adds and what that changes (worked out by the attribute engine with and without it), and its time
 * left, counting down while the screen is open. The equipped passives and keystone apart, as always on. Reads only
 * in-memory state.
 */
public final class ActiveEffectsMenu extends PlayerMenu {

    /** At most this many effects get a tile (the content area); more are counted on the last one. */
    private static final int MAX_TILES = Paging.CONTENT_SLOTS.length;
    private static final int ALWAYS_ON_SLOT = 47, STATS_SLOT = 51;

    /**
     * What a PvP fight changes right now: while tagged, combat bonuses count against creatures only (PvP plays as before
     * Attributes 2.0) and a meal's healing waits; {@code speedPaused}: speed and handling bonuses are paused too.
     */
    private record Pvp(boolean tagged, boolean speedPaused) {}

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
        Pvp pvp = pvp(p);

        // What each shown effect does: worked out once per drawing (the countdown only rewrites the times).
        Map<String, EffectBenefits.Lines> benefits = new HashMap<>();
        for (int i = 0; i < temp.size() && i < MAX_TILES; i++) {
            ActiveEffect e = temp.get(i);
            benefits.put(e.key(), benefits(p, e.contributions(), pvp));
        }

        v.set(MenuView.HEADER, Ui.head(p, ChatColor.LIGHT_PURPLE + "" + ChatColor.BOLD + "Active Effects", headerLore(temp, now, pvp)));
        if (temp.isEmpty()) {
            v.set(22, Ui.item(Material.GLASS_BOTTLE, ChatColor.GRAY + "No active effects", Ui.lore()
                    .text("Meals, ability buffs, potions and")
                    .text("admin test buffs show here, with")
                    .text("what they do and their time left.")
                    .gap().text("Your permanent skill attributes are")
                    .text("on Stats & Attributes.").build()));
        }
        for (int i = 0; i < temp.size() && i < MAX_TILES; i++) {
            ActiveEffect e = temp.get(i);
            v.set(Paging.CONTENT_SLOTS[i], tile(e, benefits.get(e.key()), now, more(temp, i), pvp));
        }
        if (!always.isEmpty()) v.set(ALWAYS_ON_SLOT, alwaysOnTile(p, always, pvp));

        // Count down while open (docs/design/ACTIVE_EFFECTS.md §3): a second at a time, only while something is timed, and
        // only the times; the screen is drawn again when an effect ends or another starts, or a PvP fight starts or ends,
        // so nothing ended ever shows as running.
        if (registry == null || !ActiveEffects.anyTimed(temp)) return;
        List<String> shown = shownKeys(temp);
        int count = temp.size();
        v.every(20, () -> {
            if (!p.isOnline()) return false;
            long t = System.currentTimeMillis();
            List<ActiveEffect> nowTemp = ActiveEffects.temporary(registry.of(p, t));
            if (nowTemp.size() != count || !shownKeys(nowTemp).equals(shown) || !pvp(p).equals(pvp)) {
                menus.refresh(p);
                return false; // the new drawing keeps its own countdown
            }
            ItemStack head = v.get(MenuView.HEADER);
            if (head != null) v.set(MenuView.HEADER, withLore(head, headerLore(nowTemp, t, pvp)));
            for (int i = 0; i < nowTemp.size() && i < MAX_TILES; i++) {
                ActiveEffect e = nowTemp.get(i);
                v.set(Paging.CONTENT_SLOTS[i], tile(e, benefits.get(e.key()), t, more(nowTemp, i), pvp));
            }
            return ActiveEffects.anyTimed(nowTemp);
        });
    }

    /** The keys of the effects that get a tile (the first {@link #MAX_TILES}). */
    private static List<String> shownKeys(List<ActiveEffect> temp) {
        return ActiveEffects.keys(temp.size() > MAX_TILES ? temp.subList(0, MAX_TILES) : temp);
    }

    /** How many effects the last tile counts as not shown (0 on every other tile). */
    private static int more(List<ActiveEffect> temp, int i) {
        return i == MAX_TILES - 1 ? temp.size() - MAX_TILES : 0;
    }

    /** The header's lines: how many effects, the summary (as on the hub's head), and what a PvP fight changes now. */
    private static List<String> headerLore(List<ActiveEffect> temp, long now, Pvp pvp) {
        Ui.Lore l = Ui.lore().stat("Active effects", String.valueOf(temp.size())).gap();
        for (String line : summary(temp, now, 6)) l.raw(line);
        if (pvp.tagged()) {
            l.gap().warn("In a PvP fight: combat bonuses")
                    .raw(ChatColor.RED + "  count against creatures only for")
                    .raw(ChatColor.RED + "  now; a meal's healing waits for")
                    .raw(ChatColor.RED + "  the fight to end.");
            if (pvp.speedPaused()) l.raw(ChatColor.RED + "  Speed and handling bonuses are paused.");
        }
        if (ActiveEffects.anyTimed(temp)) l.gap().text("Times count down while this is open.");
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

    /**
     * One effect's tile: name and time left, source, what it adds and what that changes now, its notes, its inner part (a
     * meal's restoration). Built fresh each time: a countdown's lore never goes into the shared item cache.
     */
    private static ItemStack tile(ActiveEffect e, EffectBenefits.Lines b, long now, int more, Pvp pvp) {
        String time = e.timed() ? ChatColor.WHITE + EffectsText.remaining(e.remaining(now)) + " remaining"
                : ChatColor.GRAY + (e.endsWhen() != null ? e.endsWhen() : "no time limit");
        Ui.Lore l = Ui.lore().raw(ChatColor.GRAY + e.source());
        boolean lines = b != null && !b.isEmpty();
        if (lines || !e.notes().isEmpty()) {
            l.gap().raw(ChatColor.YELLOW + "What it does:");
            if (b != null) for (String a : b.added()) l.raw(ChatColor.WHITE + "• " + a);
            for (String n : e.notes()) l.raw(ChatColor.GRAY + n);
            if (b != null && !b.changes().isEmpty()) {
                l.raw(ChatColor.YELLOW + "Now:");
                for (String c : b.changes()) l.raw(ChatColor.GRAY + "  " + c);
                if (pvp.tagged()) {
                    l.raw(ChatColor.RED + "  In PvP, the combat numbers count")
                            .raw(ChatColor.RED + "  against creatures only for now.");
                }
            }
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
        return Ui.fresh(e.icon() != null ? e.icon() : Material.POTION, color(e) + e.name() + ChatColor.DARK_GRAY + " — " + time, l.build());
    }

    /** The equipped passives and keystone: always on, not timed, with what they add and what that changes. */
    private ItemStack alwaysOnTile(Player p, List<ActiveEffect> always, Pvp pvp) {
        Ui.Lore l = Ui.lore().text("Not timed: they count whenever").text("they are equipped in the loadout")
                .text("you use (Classes & Abilities).");
        for (ActiveEffect e : always) {
            l.gap().raw(ChatColor.GOLD + e.name() + ChatColor.DARK_GRAY + "  " + e.source());
            EffectBenefits.Lines b = benefits(p, e.contributions(), pvp);
            for (String a : b.added()) l.raw(ChatColor.WHITE + "• " + a);
            for (String c : b.changes()) l.raw(ChatColor.GRAY + "   " + c);
        }
        return Ui.fresh(Material.NETHER_STAR, ChatColor.AQUA + "Always on (" + always.size() + ")", l.build());
    }

    /** What {@code cs} add and change for {@code p} now: the engine with them against without them. */
    private EffectBenefits.Lines benefits(Player p, List<Contribution> cs, Pvp pvp) {
        if (cs.isEmpty()) return new EffectBenefits.Lines(List.of(), List.of());
        AttributeService svc = plugin.getAttributeService();
        UUID id = p.getUniqueId();
        AttributeSnapshot with = svc.get(id);
        AttributeSnapshot without = svc.preview(id, list -> {
            List<Contribution> rest = new ArrayList<>(list);
            for (Contribution c : cs) rest.remove(c);
            return rest;
        });
        Gathering.GatheringCatalog gc = Gathering.GatheringCatalog.current();
        WeaponClass held = WeaponClass.ofMaterial(p.getInventory().getItemInMainHand().getType().name());
        Manager.PlayerDefenseManager defense = plugin.getPlayerDefenseManager();
        EffectBenefits.Context ctx = new EffectBenefits.Context(
                a -> svc.effect(a) == AttributeService.Effect.ACTIVE,
                s -> svc.secondaryEffect(s) == AttributeService.Effect.ACTIVE,
                pvp.speedPaused(), gc.fortunePerPoint(), gc.fortuneMax(), Manager.AttributeEffects.BASE_MAX_HEALTH,
                PlayerHubMenu.bestPowerClass(with), held,
                defense == null ? null : bonus -> defense.armorWith(p, bonus));
        return EffectBenefits.lines(cs, with, without, ctx);
    }

    /** Whether {@code p} is in a PvP fight now, and whether that pauses their speed and handling bonuses. */
    private Pvp pvp(Player p) {
        UUID id = p.getUniqueId();
        boolean tagged = plugin.getPvpTag() != null && plugin.getPvpTag().isTagged(id);
        boolean paused = plugin.getSecondaryEffects() != null && plugin.getSecondaryEffects().paused(id);
        return new Pvp(tagged || paused, paused);
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
