package GUIs.player;

import Attributes.Attribute;
import Attributes.AttributeRules;
import Attributes.AttributeService;
import GUIs.SkillDefinitions;
import GUIs.SkillDefinitions.SkillDef;
import GUIs.SkillDefinitions.SkillEffect;
import GUIs.menu.Fmt;
import GUIs.menu.MenuView;
import GUIs.menu.Ui;
import GUIs.stats.AttributeView;
import Skills.SkillManager;
import Skills.SkillNames;
import Skills.SkillProgression;
import cerberus.world.cerb.CerberusPlugin;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.UUID;

/**
 * Player Menu → Skills → category → one skill: what it is, how to train it, the attribute points
 * it gives and what they do, its own effects with their status, and what the next levels bring.
 * Arrows browse the category sideways (Back still returns to the category).
 */
public final class SkillDetailMenu extends PlayerMenu {

    private static final int STRIP_FIRST = 10, STRIP_LENGTH = 7;

    private final String skill;

    public SkillDetailMenu(CerberusPlugin plugin, String skill) {
        super(plugin);
        this.skill = skill;
    }

    @Override public String title() {
        SkillDef d = SkillDefinitions.byName(skill);
        return PlayerTheme.title(d != null ? d.displayName() : SkillNames.display(skill));
    }

    @Override protected Material headerGlass() {
        SkillDef d = SkillDefinitions.byName(skill);
        return d != null ? d.category().accentGlass : PlayerTheme.HEADER;
    }

    @Override
    protected void content(MenuView v) {
        Player p = v.viewer();
        UUID id = p.getUniqueId();
        SkillDef d = SkillDefinitions.byName(skill);
        if (d == null) {
            v.set(22, Ui.item(Material.BARRIER, ChatColor.RED + "Unknown skill", Ui.lore().text(skill).build()));
            return;
        }
        if (!loaded(p)) {
            loading(v);
            return;
        }
        SkillView.Progress pr = SkillView.progress(plugin, id, d.name());
        AttributeService svc = plugin.getAttributeService();
        AttributeRules rules = svc.rules();
        double realm = svc.get(id).realmMultiplier();

        PlayerMenuMemory.Seen seen = memory().seen(id, skill);
        boolean leveled = seen != null && pr.level() > seen.level();

        // ── Header: identity and level ─────────────────────────
        Ui.Lore head = Ui.lore();
        if (leveled) head.raw(ChatColor.GREEN + "▲ Level " + seen.level() + " → " + pr.level() + " since you last looked");
        else if (seen != null && seen.level() == pr.level() && pr.xp() > seen.xp()) head.raw(ChatColor.GREEN + "▲ +" + Fmt.grouped(pr.xp() - seen.xp()) + " XP since you last looked");
        if (SkillNames.formerName(d.name()) != null) head.raw(ChatColor.DARK_GRAY + "Formerly " + d.name());
        head.raw(d.category().color + d.category().displayTitle);
        for (String w : PlayerTheme.wrap(d.description(), SkillView.WIDTH)) head.text(w);
        head.gap();
        SkillView.levelLines(head, pr);
        v.set(MenuView.HEADER, Ui.glow(Ui.item(d.icon(), d.category().color + "" + ChatColor.BOLD + d.displayName(), head.build()), pr.max()));

        // ── Row 1: progress strip (labelled, so it never relies on colour) ──
        int filled = (int) Math.round(pr.fraction() * STRIP_LENGTH);
        String stripName = pr.max() ? ChatColor.GOLD + "★ Max level"
                : ChatColor.GREEN + "Level " + pr.level() + " → " + (pr.level() + 1) + ": "
                + String.format(java.util.Locale.ROOT, "%.0f%%", pr.fraction() * 100);
        List<String> stripLore = pr.max() ? List.of() : Ui.lore()
                .stat("XP", Fmt.grouped(pr.xp()) + " / " + Fmt.grouped(pr.required()))
                .stat("To go", Fmt.grouped(pr.required() - pr.xp())).build();
        if (pr.max()) {
            for (int i = 0; i < STRIP_LENGTH; i++) v.set(STRIP_FIRST + i, Ui.item(Material.YELLOW_STAINED_GLASS_PANE, stripName, stripLore));
        } else {
            boolean celebrate = leveled && memory().isFirstLook(id, skill);
            fillStrip(v, filled, seen, celebrate, pr, stripName, stripLore);
            if (celebrate) v.runLater(5, () -> memory().markLooked(id, skill)); // used up only if the flash really played
        }

        // ── Row 2: train / attributes / effects ────────────────
        Ui.Lore train = Ui.lore();
        SkillView.trainingLines(train, plugin, id, d, pr.level());
        boolean trainable = SkillView.trainable(plugin, d);
        v.set(20, Ui.item(trainable ? Material.BOOK : Material.GRAY_DYE,
                (trainable ? ChatColor.GOLD : ChatColor.GRAY) + "How to train", train.build()));

        v.set(22, attributeTile(svc, rules, id, d, pr));

        Ui.Lore eff = Ui.lore();
        SkillView.effectLines(eff, plugin, d, pr.level(), realm, pr.max());
        v.set(24, Ui.item(d.effects().stream().anyMatch(e -> e.status() != SkillDefinitions.EffectStatus.NOT_ACTIVE)
                ? Material.BLAZE_POWDER : Material.GUNPOWDER, ChatColor.GOLD + "What it does", eff.build()));

        // ── Row 3: what the next levels bring (and, for a skill with woods, the woods found) ──
        v.set(31, upcomingTile(svc, rules, id, d, pr, realm));
        ItemStack woods = woodsTile(p, d);
        if (woods != null) v.set(33, woods);
        fishingTiles(p, d, v);

        // ── Row 4: browse the category in place ────────────────
        List<SkillDef> siblings = SkillDefinitions.byCategory(d.category());
        int at = siblings.indexOf(d);
        if (siblings.size() > 1 && at >= 0) {
            SkillDef prev = siblings.get(Math.floorMod(at - 1, siblings.size()));
            SkillDef next = siblings.get(Math.floorMod(at + 1, siblings.size()));
            v.button(37, Ui.item(Material.SPECTRAL_ARROW, ChatColor.YELLOW + "◀ " + prev.displayName(),
                    Ui.lore().text("Previous " + d.category().name().toLowerCase(java.util.Locale.ROOT) + " skill").build()),
                    c -> menus.replace(c.player(), new SkillDetailMenu(plugin, prev.name())));
            v.button(43, Ui.item(Material.SPECTRAL_ARROW, ChatColor.YELLOW + next.displayName() + " ▶",
                    Ui.lore().text("Next " + d.category().name().toLowerCase(java.util.Locale.ROOT) + " skill").build()),
                    c -> menus.replace(c.player(), new SkillDetailMenu(plugin, next.name())));
        }
        v.refreshButton();
    }

    /**
     * Player Menu 2.1: the strip fills up as the page appears (two panes a tick), starting from where
     * the player last saw it this session. The first look at a level gained since the menu was last
     * closed fills the strip, flashes it gold with a level-up sound, then fills the new level.
     */
    private static void fillStrip(MenuView v, int filled, PlayerMenuMemory.Seen seen, boolean celebrate,
                                  SkillView.Progress pr, String name, List<String> lore) {
        ItemStack lime = Ui.item(Material.LIME_STAINED_GLASS_PANE, name, lore);
        ItemStack gray = Ui.item(Material.GRAY_STAINED_GLASS_PANE, name, lore);
        int from = stripFrom(seen, pr.level(), pr.required(), filled, STRIP_LENGTH);
        for (int i = 0; i < STRIP_LENGTH; i++) v.set(STRIP_FIRST + i, i < from ? lime : gray);
        int t = 1;
        if (celebrate) {
            ItemStack gold = Ui.item(Material.YELLOW_STAINED_GLASS_PANE, name, lore);
            for (int i = 0; i < STRIP_LENGTH; i++) v.later(1 + i / 2, STRIP_FIRST + i, lime);
            for (int i = 0; i < STRIP_LENGTH; i++) v.later(5, STRIP_FIRST + i, gold);
            v.soundLater(5, org.bukkit.Sound.ENTITY_PLAYER_LEVELUP, 0.4f, 1.4f);
            for (int i = 0; i < STRIP_LENGTH; i++) v.later(8, STRIP_FIRST + i, gray);
            t = 9;
        }
        for (int i = from; i < filled; i++) v.later(t + (i - from) / 2, STRIP_FIRST + i, lime);
    }

    /**
     * Panes already lit when the strip starts filling: where the player last saw this level (0 for a
     * new level, nothing seen yet, or a level with no XP requirement), never beyond today's fill.
     */
    static int stripFrom(PlayerMenuMemory.Seen seen, int level, int required, int filled, int length) {
        if (seen == null || seen.level() != level || required <= 0) return 0;
        int was = (int) Math.round((double) Math.max(0, seen.xp()) / required * length);
        return Math.max(0, Math.min(filled, was));
    }

    /**
     * The woods a skill cuts (resources.yml) and which ones the player has discovered (docs/design/WOODCUTTING.md §9):
     * found ones in green, the rest with the level and axe they ask for. Null for a skill without woods. Read from the
     * player's own data, kept in memory while they are online: no database.
     */
    private ItemStack woodsTile(Player p, SkillDef d) {
        Gathering.Woodcutting wc = plugin.getWoodcutting();
        Gathering.GatheringCatalog c = Gathering.GatheringCatalog.current();
        if (wc == null || c == null) return null;
        List<Gathering.GatheringCatalog.ResourceDef> woods = c.woods().stream().filter(r -> r.skill().equals(d.name())).toList();
        if (woods.isEmpty()) return null;
        java.util.Set<String> found = wc.discovered(p);
        long have = woods.stream().filter(r -> found.contains(r.id())).count();
        Ui.Lore l = Ui.lore();
        for (Gathering.GatheringCatalog.ResourceDef r : woods) {
            String axe = r.tier() <= 0 ? "any axe" : Gathering.GatheringCatalog.tierName(r.tier()) + " axe";
            l.raw(found.contains(r.id()) ? ChatColor.GREEN + "✔ " + ChatColor.WHITE + r.displayName()
                    : ChatColor.DARK_GRAY + "✘ " + ChatColor.GRAY + r.displayName() + ChatColor.DARK_GRAY + " · level " + r.level() + " · " + axe);
        }
        l.gap();
        for (String w : PlayerTheme.wrap("The first log of each wood you cut from a tree pays a discovery bonus, once.", SkillView.WIDTH)) l.text(w);
        return Ui.item(Material.OAK_SAPLING, ChatColor.GOLD + "Woods: " + have + " of " + woods.size() + " discovered", l.build());
    }

    /**
     * Fishing's page (docs/design/FISHING.md): the journal by water (species found, the rest with the level and moment they
     * ask for), and the banked lures, where a click chooses which one a Sneak-cast calls (kept for the session, no database).
     */
    private void fishingTiles(Player p, SkillDef d, MenuView v) {
        Gathering.Fishing f = plugin.getFishing();
        Gathering.FishingRules r = Gathering.GatheringCatalog.current().fishing();
        if (f == null || !r.enabled() || !d.name().equals(r.skill())) return;
        Gathering.FishingJournal j = f.journal(p);
        Ui.Lore l = Ui.lore();
        long found = 0;
        for (Gathering.FishingRules.Water w : r.waters()) {
            List<Gathering.FishingRules.Species> sp = r.species(w.id());
            if (sp.isEmpty()) continue;
            long have = sp.stream().filter(s -> j.caught(s.id())).count();
            found += have;
            l.raw((have == sp.size() ? ChatColor.GREEN + "✔ " : ChatColor.GRAY + "• ") + ChatColor.WHITE + w.name() + ChatColor.GRAY + ": " + have + " of " + sp.size());
        }
        l.gap();
        for (String w : PlayerTheme.wrap("Each water has its own species; time and weather change what bites. A first catch is a discovery, a heavier one a personal best. /fishing journal lists them.", SkillView.WIDTH)) l.text(w);
        v.set(33, Ui.item(Material.TROPICAL_FISH, ChatColor.GOLD + "Journal: " + found + " of " + r.allSpecies().size() + " species", l.build()));

        Ui.Lore lu = Ui.lore();
        String sel = f.selected(p);
        if (j.totalLures() == 0) {
            for (String w : PlayerTheme.wrap("None banked yet. From Fishing " + r.techniques().creatures() + ", a catch can bring a sea creature's lure: "
                    + "Sneak and cast into water to call it, and fight it when you are ready.", SkillView.WIDTH)) lu.text(w);
        } else {
            for (java.util.Map.Entry<String, Integer> e : j.lures().entrySet()) {
                Gathering.FishingRules.Creature c = r.creature(e.getKey());
                if (c == null) continue;
                lu.raw((e.getKey().equals(sel) ? ChatColor.AQUA + "▶ " : ChatColor.GRAY + "  ") + c.rarity().color + c.name() + ChatColor.GRAY + " ×" + e.getValue());
            }
            lu.gap();
            for (String w : PlayerTheme.wrap("Sneak and cast into water to call " + (sel != null ? "the chosen one" : "one") + ". If it gets away, its lure comes back.", SkillView.WIDTH)) lu.text(w);
            lu.hint("Click to choose which one");
        }
        lu.raw(ChatColor.DARK_GRAY + "Defeated: " + j.creaturesDefeated().size() + " of " + r.creatures().size() + " creatures");
        ItemStack lures = Ui.item(Material.PRISMARINE_SHARD, ChatColor.AQUA + "Lures: " + j.totalLures(), lu.build());
        if (j.totalLures() == 0) { v.set(29, lures); return; }
        v.button(29, lures, c -> {
            List<String> have = new java.util.ArrayList<>(j.lures().keySet());
            String now = f.selected(c.player());
            int at = now == null ? -1 : have.indexOf(now);
            f.select(c.player(), have.get((at + 1) % have.size()));
            menus.replace(c.player(), new SkillDetailMenu(plugin, d.name()));
        });
    }

    private ItemStack attributeTile(AttributeService svc, AttributeRules rules, UUID id, SkillDef d, SkillView.Progress pr) {
        List<AttributeRules.SkillWeight> weights = SkillView.weights(rules, d.name());
        List<AttributeRules.StatWeight> speed = SkillView.statWeights(rules, d.name());
        Attribute main = SkillView.mainAttribute(rules, d.name());
        Ui.Lore l = Ui.lore();
        if (weights.isEmpty() && speed.isEmpty()) {
            l.text("This skill gives no attribute").text("points or speed.");
            return Ui.item(Material.GRAY_DYE, ChatColor.GRAY + "Attributes & speed", l.build());
        }
        l.text("Each level gives:");
        for (AttributeRules.SkillWeight w : weights) l.raw(PlayerTheme.indent(SkillView.weightLine(w, w.perLevel(), false)));
        SkillView.speedLines(speed, 1, false).forEach(x -> l.raw(PlayerTheme.indent(x)));
        l.gap().text("From this skill now (level " + pr.level() + "):");
        for (AttributeRules.SkillWeight w : weights) l.raw(PlayerTheme.indent(SkillView.weightLine(w, w.perLevel() * pr.level(), false)));
        SkillView.speedLines(speed, pr.level(), false).forEach(x -> l.raw(PlayerTheme.indent(x)));
        int next = Math.min(SkillProgression.MAX_LEVEL, pr.level() + 1);
        l.gap().text(pr.max() ? "What they do for you:" : "What they do (now → next level):");
        for (String line : SkillView.outcomes(svc, id, d.name(), next)) l.raw(PlayerTheme.indent(line));
        l.gap();
        java.util.Set<Attribute> seen = java.util.EnumSet.noneOf(Attribute.class);
        for (AttributeRules.SkillWeight w : weights) {
            if (seen.add(w.attribute())) {
                l.raw(w.attribute().color + w.attribute().displayName + ": " + AttributeView.effectLabel(svc.effect(w.attribute()), w.attribute()));
            }
        }
        java.util.Set<Attributes.Stat> seenStats = java.util.EnumSet.noneOf(Attributes.Stat.class);
        for (AttributeRules.StatWeight w : speed) {
            if (seenStats.add(w.stat())) l.raw(ChatColor.AQUA + w.stat().displayName + ": " + AttributeView.secondaryLabel(svc.secondaryEffect(w.stat())));
        }
        if (!speed.isEmpty() && !rules.secondary().inPvp()) l.raw(ChatColor.DARK_GRAY + "Speed and handling bonuses pause during PvP combat.");
        return Ui.item(main != null ? AttributeView.icon(main) : Material.FEATHER, ChatColor.GOLD + "Attributes & speed", l.build());
    }

    private ItemStack upcomingTile(AttributeService svc, AttributeRules rules, UUID id, SkillDef d, SkillView.Progress pr, double realm) {
        Ui.Lore l = Ui.lore();
        if (pr.max()) {
            l.text("This skill is at the max level.");
            return Ui.glow(Ui.item(Material.NETHER_STAR, ChatColor.GOLD + "Mastered", l.build()), true);
        }
        int[] targets = milestones(pr.level());
        List<AttributeRules.SkillWeight> weights = SkillView.weights(rules, d.name());
        long xpToGo = (long) pr.required() - pr.xp();
        for (int target : targets) {
            l.raw(ChatColor.YELLOW + "At level " + target + ChatColor.DARK_GRAY + "  ("
                    + Fmt.grouped(xpToGo + xpBetween(pr.level() + 1, target)) + " XP away)");
            for (AttributeRules.SkillWeight w : weights) l.raw(PlayerTheme.indent(SkillView.weightLine(w, w.perLevel() * target, false)));
            List<AttributeRules.StatWeight> speed = SkillView.statWeights(rules, d.name());
            if (!speed.isEmpty()) { // effective values (after diminishing returns), from the engine
                Attributes.AttributeSnapshot at = AttributeView.withSkillLevel(svc, id, d.name(), target);
                for (SkillView.SpeedRow r : SkillView.speedRows(speed)) {
                    l.raw(PlayerTheme.indent(ChatColor.AQUA + r.label() + ": +" + GUIs.menu.Fmt.pct(r.value(at))));
                }
            }
            for (SkillEffect e : d.effects()) {
                String val = SkillView.effectValue(plugin, e, target, realm);
                if (val != null) l.raw(ChatColor.GRAY + "  " + e.label() + ": " + ChatColor.WHITE + val
                        + (e.status() == SkillDefinitions.EffectStatus.PVP_ONLY ? ChatColor.DARK_GRAY + " (players)" : ""));
            }
            l.gap();
        }
        if (weights.isEmpty() && SkillView.statWeights(rules, d.name()).isEmpty() && d.effects().stream().allMatch(e -> e.value() == null)) {
            l.text("Levels raise your total skill level;").text("this skill has no live bonus yet.");
        }
        return Ui.item(Material.EXPERIENCE_BOTTLE, ChatColor.GREEN + "Next levels", l.build());
    }

    /** The next level, the next multiple of 10 after it, and the max. */
    static int[] milestones(int level) {
        int next = level + 1;
        int ten = Math.min(SkillProgression.MAX_LEVEL, ((next / 10) + 1) * 10);
        if (next >= SkillProgression.MAX_LEVEL) return new int[]{SkillProgression.MAX_LEVEL};
        if (ten >= SkillProgression.MAX_LEVEL) return new int[]{next, SkillProgression.MAX_LEVEL};
        return new int[]{next, ten, SkillProgression.MAX_LEVEL};
    }

    /** XP to go from the start of level {@code from} to the start of level {@code to}. */
    static long xpBetween(int from, int to) {
        long sum = 0;
        for (int l = from; l < to; l++) sum += SkillManager.xpForLevel(l);
        return sum;
    }
}
