package GUIs.player;

import Attributes.Attribute;
import Attributes.AttributeRules;
import Attributes.AttributeService;
import Attributes.AttributeSnapshot;
import Attributes.KillXp;
import Attributes.WeaponClass;
import GUIs.SkillDefinitions;
import GUIs.SkillDefinitions.SkillDef;
import GUIs.SkillDefinitions.SkillEffect;
import GUIs.menu.Fmt;
import GUIs.menu.Ui;
import GUIs.stats.AttributeView;
import Manager.Numbers;
import Manager.Tier;
import Skills.SkillManager;
import Skills.SkillNames;
import Skills.SkillProgression;
import cerberus.world.cerb.CerberusPlugin;
import org.bukkit.ChatColor;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * How skills are shown on the player screens. Every number comes from the gameplay code: levels
 * and XP from {@link SkillManager}, attribute outcomes from the attribute engine (previews at
 * other levels), effect sizes from the skill formulas the combat code calls, kill XP from
 * {@link KillXp} and the skills.yml routing. Nothing here has a formula of its own.
 */
public final class SkillView {

    private SkillView() {}

    /** Tooltip text width. */
    static final int WIDTH = 34;

    // ── Levels and XP ────────────────────────────────────────────

    record Progress(int level, int xp, int required, boolean max) {
        double fraction() { return max ? 1.0 : required <= 0 ? 0.0 : Math.min(1.0, (double) xp / required); }
    }

    static Progress progress(CerberusPlugin plugin, UUID player, String skill) {
        SkillManager sm = plugin.getSkillManager();
        int level = sm.getSkillLevel(player, skill);
        boolean max = level >= SkillProgression.MAX_LEVEL;
        return new Progress(level, max ? 0 : sm.getSkillXP(player, skill), max ? 0 : SkillManager.xpForLevel(level), max);
    }

    /** "Level 23 / 100", the bar and the XP numbers. */
    static void levelLines(Ui.Lore l, Progress pr) {
        l.raw(ChatColor.GRAY + "Level " + ChatColor.WHITE + pr.level() + ChatColor.DARK_GRAY + " / " + SkillProgression.MAX_LEVEL);
        if (pr.max()) {
            l.raw(ChatColor.GOLD + "★ Max level");
        } else {
            l.raw(PlayerTheme.bar(pr.xp(), pr.required(), 20, ChatColor.GREEN));
            l.raw(ChatColor.GRAY + "XP " + ChatColor.WHITE + Fmt.grouped(pr.xp()) + ChatColor.DARK_GRAY + " / "
                    + ChatColor.WHITE + Fmt.grouped(pr.required()) + ChatColor.GRAY + "  ("
                    + Fmt.grouped(pr.required() - pr.xp()) + " to level " + (pr.level() + 1) + ")");
        }
    }

    // ── Training ─────────────────────────────────────────────────

    /** What a held item must be for a kill to train this skill, in words; "" if kills don't. */
    static String killItems(CerberusPlugin plugin, SkillDef d) {
        var mapper = plugin.getSkillMapper();
        return mapper == null ? "" : mapper.describeKillItems(d.name());
    }

    /** True when the game has at least one way to earn XP for this skill. */
    static boolean trainable(CerberusPlugin plugin, SkillDef d) {
        return !d.training().isEmpty() || !killItems(plugin, d).isEmpty();
    }

    /** One short line for list tiles: how it trains, or that it can't yet. */
    static String trainingSummary(CerberusPlugin plugin, SkillDef d) {
        String kill = killItems(plugin, d);
        if (!kill.isEmpty()) return ChatColor.GREEN + "● Trains by kills: " + ChatColor.WHITE + shorten(kill, 26);
        if (!d.training().isEmpty()) return ChatColor.GREEN + "● Trains: " + ChatColor.WHITE + shorten(firstClause(d.training().get(0)), 30);
        return ChatColor.GRAY + "○ No way to train it yet";
    }

    /** The full "How to train" text. */
    static void trainingLines(Ui.Lore l, CerberusPlugin plugin, UUID player, SkillDef d, int level) {
        boolean any = false;
        for (String t : d.training()) {
            wrapped(l, "• " + t, ChatColor.GRAY);
            any = true;
        }
        String kill = killItems(plugin, d);
        if (!kill.isEmpty()) {
            if (any) l.gap();
            wrapped(l, "• Kill mobs while holding: " + kill + ".", ChatColor.GRAY);
            KillXp.Params kx = plugin.getAttributesConfig() != null ? plugin.getAttributesConfig().killXp() : null;
            if (kx != null && kx.enabled()) {
                l.raw(ChatColor.GRAY + "  XP per kill (common mob):");
                StringBuilder sb = new StringBuilder(ChatColor.WHITE + "  ");
                int[] levels = {1, 25, 50, 100};
                for (int i = 0; i < levels.length; i++) {
                    long xp = (long) KillXp.compute(levels[i], Tier.COMMON.xpMultiplier, level, kx);
                    boolean grey = KillXp.isGreyKill(levels[i], level, kx);
                    sb.append(grey ? ChatColor.DARK_GRAY : ChatColor.WHITE).append("Lv").append(levels[i]).append(' ')
                            .append(Fmt.grouped(xp)).append(i < levels.length - 1 ? ChatColor.DARK_GRAY + " · " : "");
                }
                l.raw(sb.toString());
                l.raw(ChatColor.DARK_GRAY + "  Rarer and stronger mobs give more.");
                if (kx.antiGrindGap() > 0) {
                    l.raw(ChatColor.DARK_GRAY + "  Mobs " + kx.antiGrindGap() + "+ levels below your skill give "
                            + Fmt.pct(kx.antiGrindFactor()).replace(".0%", "%") + ".");
                }
            } else {
                l.raw(ChatColor.DARK_GRAY + "  XP depends on the mob's health.");
            }
            any = true;
        }
        if (!any) {
            l.raw(Availability.State.IN_DEVELOPMENT.label);
            wrapped(l, "Nothing in the game trains this skill yet. It is listed so you can see what is planned.", ChatColor.GRAY);
        }
    }

    // ── Attributes ───────────────────────────────────────────────

    /** Attribute points per level from this skill, merged by attribute + weapon ("+1 Power (sword)"). */
    static List<AttributeRules.SkillWeight> weights(AttributeRules rules, String skill) {
        return rules.skillWeights().getOrDefault(skill, List.of());
    }

    /** One speed stat a skill raises, with the weapon types it applies to (Weapon Mastery: four). */
    record SpeedRow(Attributes.Stat stat, List<WeaponClass> weapons) {
        String label() {
            if (stat != Attributes.Stat.ATTACK_SPEED) return stat.displayName;
            if (weapons.isEmpty()) return "Attack speed";
            List<String> names = new ArrayList<>();
            for (WeaponClass w : weapons) names.add(names.isEmpty() ? w.singular : w.singular.toLowerCase(Locale.ROOT));
            return String.join("/", names) + " attack speed";
        }

        /** Effective value in {@code s} (the first weapon stands for the group: same weights). */
        double value(AttributeSnapshot s) {
            return stat == Attributes.Stat.ATTACK_SPEED ? s.attackSpeed(weapons.isEmpty() ? WeaponClass.OTHER : weapons.get(0)) : s.secondary(stat);
        }
    }

    /** Group a skill's speed weights per stat. */
    static List<SpeedRow> speedRows(List<AttributeRules.StatWeight> ws) {
        Map<Attributes.Stat, List<WeaponClass>> by = new LinkedHashMap<>();
        for (AttributeRules.StatWeight w : ws) {
            List<WeaponClass> list = by.computeIfAbsent(w.stat(), k -> new ArrayList<>());
            if (w.weapon() != null && !list.contains(w.weapon())) list.add(w.weapon());
        }
        List<SpeedRow> out = new ArrayList<>();
        by.forEach((st, list) -> out.add(new SpeedRow(st, list)));
        return out;
    }

    /** Speed bonuses per level from this skill (attack, draw, movement speed, cooldown recovery). */
    static List<AttributeRules.StatWeight> statWeights(AttributeRules rules, String skill) {
        return rules.secondary().skillWeights().getOrDefault(skill, List.of());
    }

    /**
     * Speed lines for a skill, one per stat and amount: Weapon Mastery's four attack-speed
     * weights read "+0.3% axe/mace/trident/spear attack speed". {@code levels} multiplies the
     * per-level amount (1 = per level, the skill level = from this skill now).
     */
    static List<String> speedLines(List<AttributeRules.StatWeight> ws, double levels, boolean perLevel) {
        Map<String, List<AttributeRules.StatWeight>> by = new LinkedHashMap<>();
        for (AttributeRules.StatWeight w : ws) by.computeIfAbsent(w.stat() + "/" + w.perLevel(), k -> new ArrayList<>()).add(w);
        List<String> out = new ArrayList<>();
        for (List<AttributeRules.StatWeight> group : by.values()) {
            AttributeRules.StatWeight first = group.get(0);
            List<String> weapons = new ArrayList<>();
            for (AttributeRules.StatWeight w : group) if (w.weapon() != null) weapons.add(w.weapon().singular.toLowerCase(Locale.ROOT));
            out.add(ChatColor.AQUA + "+" + Fmt.pct(first.perLevel() * levels).replace(".0%", "%") + " "
                    + (weapons.isEmpty() ? "" : String.join("/", weapons) + " ")
                    + first.stat().displayName.toLowerCase(Locale.ROOT)
                    + (perLevel ? ChatColor.DARK_GRAY + " per level" : ""));
        }
        return out;
    }

    static String weightLine(AttributeRules.SkillWeight w, double amount, boolean perLevel) {
        return w.attribute().color + "+" + one(amount) + " " + w.attribute().displayName
                + ChatColor.GRAY + (w.weapon() != null ? " (" + w.weapon().displayName.toLowerCase(Locale.ROOT) + ")" : "")
                + (perLevel ? ChatColor.DARK_GRAY + " per level" : "");
    }

    /**
     * What this skill's attributes do for the player, now and at {@code nextLevel}: one line per
     * outcome, both values from the attribute engine.
     */
    static List<String> outcomes(AttributeService svc, UUID player, String skill, int nextLevel) {
        AttributeSnapshot now = svc.get(player);
        AttributeSnapshot next = AttributeView.withSkillLevel(svc, player, skill, nextLevel);
        Map<String, String[]> rows = new LinkedHashMap<>();
        for (AttributeRules.SkillWeight w : weights(svc.rules(), skill)) {
            switch (w.attribute()) {
                case POWER -> {
                    if (w.weapon() != null) {
                        WeaponClass wc = w.weapon();
                        rows.put(wc.singular + " damage", new String[]{Fmt.mult(now.powerMultiplier(wc)), Fmt.mult(next.powerMultiplier(wc))});
                    } else { // all-weapon Power: OTHER carries no weapon-only Power, so this is the shared part
                        rows.put("Power for every weapon", new String[]{one(now.power(WeaponClass.OTHER)), one(next.power(WeaponClass.OTHER))});
                    }
                }
                case AGILITY -> {
                    rows.put("Crit chance (mobs)", new String[]{Fmt.pct(now.critChance()), Fmt.pct(next.critChance())});
                    rows.put("Crit damage (mobs)", new String[]{Fmt.mult(now.critMultiplier()), Fmt.mult(next.critMultiplier())});
                }
                case VITALITY -> rows.put("Max health", new String[]{Fmt.mult(now.maxHpMultiplier()), Fmt.mult(next.maxHpMultiplier())});
                case FORTITUDE -> {
                    rows.put("Armor", new String[]{"+" + Fmt.pct(now.armorBonus()), "+" + Fmt.pct(next.armorBonus())});
                    rows.put("Defense bar", new String[]{Fmt.mult(now.defenseBarMultiplier()), Fmt.mult(next.defenseBarMultiplier())});
                    rows.put("Resistances", new String[]{"+" + Fmt.pct(now.resist()), "+" + Fmt.pct(next.resist())});
                }
                case ESSENCE -> {
                    rows.put("Max Essence", new String[]{Fmt.mult(now.maxManaMultiplier()), Fmt.mult(next.maxManaMultiplier())});
                    rows.put("Essence regeneration", new String[]{Fmt.mult(now.manaRegenMultiplier()), Fmt.mult(next.manaRegenMultiplier())});
                    rows.put("Spell damage (mobs)", new String[]{Fmt.mult(now.spellDamageMultiplier()), Fmt.mult(next.spellDamageMultiplier())});
                }
                case FORTUNE -> rows.put("Fortune", null);
            }
        }
        for (SpeedRow r : speedRows(statWeights(svc.rules(), skill))) {
            rows.put(r.label(), new String[]{"+" + Fmt.pct(r.value(now)), "+" + Fmt.pct(r.value(next))});
        }
        List<String> out = new ArrayList<>();
        rows.forEach((label, v) -> {
            if (v == null) out.add(ChatColor.GRAY + label + ": " + ChatColor.DARK_GRAY + "not used in the game yet");
            else if (v[0].equals(v[1])) out.add(ChatColor.GRAY + label + ": " + ChatColor.WHITE + v[0]);
            else out.add(ChatColor.GRAY + label + ": " + ChatColor.WHITE + v[0] + ChatColor.DARK_GRAY + " → " + ChatColor.GREEN + v[1]);
        });
        return out;
    }

    // ── Effects ──────────────────────────────────────────────────

    /** An effect's value at a level, formatted ("12.0%", "x1.85", "+340 points"). */
    static String effectValue(CerberusPlugin plugin, SkillEffect e, int level, double realm) {
        if (e.value() == null || plugin.getCombatConfig() == null) return null;
        double v = e.value().at(plugin.getCombatConfig(), level);
        return switch (e.unit()) {
            case PERCENT -> Fmt.pct(v);
            case MULTIPLIER -> Fmt.mult(v);
            case REALM_POINTS -> "+" + Numbers.hud(v * realm) + " points";
        };
    }

    static void effectLines(Ui.Lore l, CerberusPlugin plugin, SkillDef d, int level, double realm, boolean max) {
        if (d.effects().isEmpty()) {
            l.text("No effects of its own: it works").text("through its attribute points.");
            return;
        }
        boolean first = true;
        for (SkillEffect e : d.effects()) {
            if (!first) l.gap();
            first = false;
            l.raw(ChatColor.WHITE + e.label());
            l.raw(PlayerTheme.indent(e.status().label));
            String now = effectValue(plugin, e, level, realm);
            if (now != null) {
                String next = max ? null : effectValue(plugin, e, level + 1, realm);
                l.raw(ChatColor.GRAY + "  Now: " + ChatColor.WHITE + now
                        + (next != null && !next.equals(now) ? ChatColor.DARK_GRAY + "  next level " + ChatColor.GREEN + next : ""));
            }
            if (e.note() != null) for (String w : PlayerTheme.wrap(e.note(), WIDTH - 2)) l.raw(ChatColor.DARK_GRAY + "  " + w);
        }
    }

    /** "2 live · 1 against players · 1 not active yet" for list tiles. */
    static String effectSummary(SkillDef d) {
        int live = 0, pvp = 0, planned = 0;
        for (SkillEffect e : d.effects()) {
            switch (e.status()) {
                case LIVE -> live++;
                case PVP_ONLY -> pvp++;
                case NOT_ACTIVE -> planned++;
            }
        }
        List<String> parts = new ArrayList<>();
        if (live > 0) parts.add(ChatColor.GREEN + "● " + live + " live");
        if (pvp > 0) parts.add(ChatColor.YELLOW + "◐ " + pvp + " vs players");
        if (planned > 0) parts.add(ChatColor.GRAY + "○ " + planned + " not active yet");
        return parts.isEmpty() ? ChatColor.GRAY + "Attribute points only" : String.join(ChatColor.DARK_GRAY + " · ", parts);
    }

    // ── Tiles ────────────────────────────────────────────────────

    /** The list tile for a skill (category screen, top skills). */
    static ItemStack tile(CerberusPlugin plugin, UUID player, SkillDef d) {
        Progress pr = progress(plugin, player, d.name());
        Ui.Lore l = Ui.lore();
        if (SkillNames.formerName(d.name()) != null) l.raw(ChatColor.DARK_GRAY + "Formerly " + d.name());
        for (String w : PlayerTheme.wrap(d.description(), WIDTH)) l.text(w);
        l.gap();
        levelLines(l, pr);
        l.gap();
        AttributeRules rules = plugin.getAttributeService().rules();
        for (AttributeRules.SkillWeight w : weights(rules, d.name())) l.raw(weightLine(w, w.perLevel(), true));
        speedLines(statWeights(rules, d.name()), 1, true).forEach(l::raw);
        l.raw(effectSummary(d));
        l.raw(trainingSummary(plugin, d));
        l.hint("Click for details");
        String name = d.category().color + d.displayName() + ChatColor.DARK_GRAY + "  Lv " + pr.level();
        return Ui.glow(Ui.item(d.icon(), name, l.build()), pr.max());
    }

    // ── Text helpers ─────────────────────────────────────────────

    static void wrapped(Ui.Lore l, String text, ChatColor color) {
        List<String> lines = PlayerTheme.wrap(text, WIDTH);
        for (int i = 0; i < lines.size(); i++) l.raw(color + (i == 0 ? "" : "  ") + lines.get(i));
    }

    static String one(double v) {
        return v == Math.rint(v) ? String.valueOf((long) v) : String.format(Locale.ROOT, "%.2f", v).replaceAll("0+$", "");
    }

    private static String shorten(String s, int max) {
        return s.length() <= max ? s : s.substring(0, Math.max(0, max - 1)).trim() + "…";
    }

    private static String firstClause(String s) {
        int i = s.indexOf(':');
        return i > 0 ? s.substring(0, i) : s;
    }

    /** Sum of every catalogued skill's level. */
    public static int totalLevel(CerberusPlugin plugin, UUID player) {
        int sum = 0;
        for (SkillDef d : SkillDefinitions.all()) sum += plugin.getSkillManager().getSkillLevel(player, d.name());
        return sum;
    }

    /** The highest-level skill (first in catalog order on ties), or null when there are none. */
    static SkillDef best(CerberusPlugin plugin, UUID player) {
        SkillDef best = null;
        int bestLevel = -1;
        for (SkillDef d : SkillDefinitions.all()) {
            int lvl = plugin.getSkillManager().getSkillLevel(player, d.name());
            if (lvl > bestLevel) { best = d; bestLevel = lvl; }
        }
        return best;
    }

    static Attribute mainAttribute(AttributeRules rules, String skill) {
        AttributeRules.SkillWeight best = null;
        for (AttributeRules.SkillWeight w : weights(rules, skill)) if (best == null || w.perLevel() > best.perLevel()) best = w;
        return best == null ? null : best.attribute();
    }
}
