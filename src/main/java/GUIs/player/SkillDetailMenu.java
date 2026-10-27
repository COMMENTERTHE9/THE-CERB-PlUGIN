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
 * Below them, the skill's section tiles (docs/design/FISHING_EQUIPMENT.md §2: Fishing's Equipment,
 * Journal and Encounters, Woodcutting's Woods), each opening its own screen, and for Fishing the
 * "since your last visit" summary. Arrows browse the category sideways (Back still returns to the
 * category).
 */
public final class SkillDetailMenu extends PlayerMenu {

    private static final int STRIP_FIRST = 10, STRIP_LENGTH = 7;

    private final String skill;
    /** What the summary tile showed at the last draw: a right-click marks exactly this read (never what came in since). */
    private Gathering.FishingJournal.Summary shownSummary;
    private java.util.Set<Long> shownUnreadAt;

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

        // ── Row 2: train / attributes / effects / next levels ──
        Ui.Lore train = Ui.lore();
        SkillView.trainingLines(train, plugin, id, d, pr.level());
        boolean trainable = SkillView.trainable(plugin, d);
        v.set(19, Ui.item(trainable ? Material.BOOK : Material.GRAY_DYE,
                (trainable ? ChatColor.GOLD : ChatColor.GRAY) + "How to train", train.build()));

        v.set(21, attributeTile(svc, rules, id, d, pr));

        Ui.Lore eff = Ui.lore();
        SkillView.effectLines(eff, plugin, d, pr.level(), realm, pr.max());
        v.set(23, Ui.item(d.effects().stream().anyMatch(e -> e.status() != SkillDefinitions.EffectStatus.NOT_ACTIVE)
                ? Material.BLAZE_POWDER : Material.GUNPOWDER, ChatColor.GOLD + "What it does", eff.build()));

        v.set(25, upcomingTile(svc, rules, id, d, pr, realm));

        // ── Row 3: the skill's sections (only skills with real functionality have any) ──
        sections(v, p, d);

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
     * The skill's section tiles, centred in row 3, each opening its screen on top of this page (Back returns here), and
     * its summary tile between the arrows (Fishing's "since your last visit"). Tiles read the player's own data in
     * memory: no database.
     */
    private void sections(MenuView v, Player p, SkillDef d) {
        SkillSections reg = plugin.getPlayerMenuGUI().skillSections();
        java.util.List<SkillSections.Section> live = new java.util.ArrayList<>();
        java.util.List<ItemStack> tiles = new java.util.ArrayList<>();
        for (SkillSections.Section s : reg.forSkill(d.name())) {
            ItemStack tile;
            try { tile = s.tile().apply(plugin, p); } catch (RuntimeException e) { tile = null; }
            if (tile == null) continue;
            live.add(s);
            tiles.add(tile);
        }
        int[] slots = SkillSections.slots(live.size());
        for (int i = 0; i < slots.length; i++) {
            SkillSections.Section s = live.get(i);
            if (s.opener() == null) { v.set(slots[i], tiles.get(i)); continue; }
            v.button(slots[i], tiles.get(i), c -> {
                SkillSections.Section now = plugin.getPlayerMenuGUI().skillSections().forSkill(d.name()).stream()
                        .filter(x -> x.id().equals(s.id())).findFirst().orElse(null);
                if (now != null && now.opener() != null) menus.open(c.player(), now.opener().apply(plugin, c.player()));
            });
        }
        SkillSections.Summary sum = reg.summary(d.name());
        shownSummary = null;
        shownUnreadAt = null;
        if (sum != null) {
            ItemStack tile;
            try { tile = sum.tile().apply(plugin, p); } catch (RuntimeException e) { tile = null; }
            if (tile != null) {
                Gathering.Fishing f = plugin.getFishing();
                if (f != null && d.name().equals(Gathering.GatheringCatalog.current().fishing().skill())) {
                    Gathering.FishingJournal j = f.journal(p);
                    shownSummary = j.summary();
                    shownUnreadAt = new java.util.HashSet<>();
                    for (Gathering.FishingJournal.Notable n : j.notable()) if (n.unread()) shownUnreadAt.add(n.at());
                }
                v.button(40, tile, c -> sum.click().clicked(plugin, c.player(), c.isRight(), this));
            }
        }
    }

    /** What the summary tile showed when this page was last drawn (a right-click marks exactly this read). */
    Gathering.FishingJournal.Summary shownSummary() { return shownSummary; }

    java.util.Set<Long> shownUnreadAt() { return shownUnreadAt; }

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
