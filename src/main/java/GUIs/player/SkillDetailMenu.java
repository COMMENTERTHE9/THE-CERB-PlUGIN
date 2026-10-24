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

        // ── Header: identity and level ─────────────────────────
        Ui.Lore head = Ui.lore();
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
        for (int i = 0; i < STRIP_LENGTH; i++) {
            Material pane = pr.max() ? Material.YELLOW_STAINED_GLASS_PANE
                    : i < filled ? Material.LIME_STAINED_GLASS_PANE : Material.GRAY_STAINED_GLASS_PANE;
            v.set(STRIP_FIRST + i, Ui.item(pane, stripName, stripLore));
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

        // ── Row 3: what the next levels bring ──────────────────
        v.set(31, upcomingTile(svc, rules, id, d, pr, realm));

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

    private ItemStack attributeTile(AttributeService svc, AttributeRules rules, UUID id, SkillDef d, SkillView.Progress pr) {
        List<AttributeRules.SkillWeight> weights = SkillView.weights(rules, d.name());
        Attribute main = SkillView.mainAttribute(rules, d.name());
        Ui.Lore l = Ui.lore();
        if (weights.isEmpty()) {
            l.text("This skill gives no attribute").text("points.");
            return Ui.item(Material.GRAY_DYE, ChatColor.GRAY + "Attributes", l.build());
        }
        l.text("Each level gives:");
        for (AttributeRules.SkillWeight w : weights) l.raw(PlayerTheme.indent(SkillView.weightLine(w, w.perLevel(), false)));
        l.gap().text("From this skill now (level " + pr.level() + "):");
        for (AttributeRules.SkillWeight w : weights) l.raw(PlayerTheme.indent(SkillView.weightLine(w, w.perLevel() * pr.level(), false)));
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
        return Ui.item(AttributeView.icon(main), ChatColor.GOLD + "Attributes", l.build());
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
            for (SkillEffect e : d.effects()) {
                String val = SkillView.effectValue(plugin, e, target, realm);
                if (val != null) l.raw(ChatColor.GRAY + "  " + e.label() + ": " + ChatColor.WHITE + val
                        + (e.status() == SkillDefinitions.EffectStatus.PVP_ONLY ? ChatColor.DARK_GRAY + " (players)" : ""));
            }
            l.gap();
        }
        if (weights.isEmpty() && d.effects().stream().allMatch(e -> e.value() == null)) {
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
