package GUIs.player;

import Attributes.Attribute;
import Attributes.AttributeSnapshot;
import Attributes.WeaponClass;
import Attributes.AttributeRules;
import GUIs.SkillDefinitions;
import GUIs.menu.MenuView;
import GUIs.menu.Ui;
import Manager.Numbers;
import Manager.Realm;
import Skills.SkillProgression;
import cerberus.world.cerb.CerberusPlugin;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.UUID;

/**
 * The Player Menu hub: the character at a glance (realm, total skill level, health, Essence,
 * defense, attributes) and every destination, each with an honest availability state. Reads only
 * in-memory state (cached attribute snapshot, skill maps, health/mana pools) — no database work.
 */
public final class PlayerHubMenu extends PlayerMenu {

    public PlayerHubMenu(CerberusPlugin plugin) {
        super(plugin);
    }

    @Override public String title() { return PlayerTheme.title("Player Menu"); }

    @Override protected boolean isHub() { return true; }

    @Override
    protected void content(MenuView v) {
        Player p = v.viewer();
        UUID id = p.getUniqueId();
        if (!loaded(p)) {
            v.set(MenuView.HEADER, Ui.head(p, ChatColor.GOLD + "" + ChatColor.BOLD + p.getName(), List.of()));
            loading(v);
            return;
        }
        AttributeSnapshot s = plugin.getAttributeService().get(id);
        Realm realm = s.realm().realm();
        boolean testRealm = plugin.getPlayerRealms().hasTestRealm(id);

        // ── Header: the character, and the effects on it (docs/design/ACTIVE_EFFECTS.md) ──
        Effects.ActiveEffects effects = plugin.getActiveEffects();
        long now = System.currentTimeMillis();
        List<Effects.ActiveEffect> temp = effects == null ? List.of() : Effects.ActiveEffects.temporary(effects.of(p, now));
        v.button(MenuView.HEADER, Ui.head(p, ChatColor.GOLD + "" + ChatColor.BOLD + p.getName(), headLore(p, temp, now)),
                c -> menus.open(c.player(), new ActiveEffectsMenu(plugin)));
        if (!temp.isEmpty()) {
            // Its times count down while the hub is open: the head's lines only, once a second, until nothing is left.
            v.every(20, () -> {
                if (!p.isOnline() || !loaded(p)) return false;
                long t = System.currentTimeMillis();
                List<Effects.ActiveEffect> left = Effects.ActiveEffects.temporary(effects.of(p, t));
                org.bukkit.inventory.ItemStack head = v.get(MenuView.HEADER);
                if (head != null) v.set(MenuView.HEADER, ActiveEffectsMenu.withLore(head, headLore(p, left, t)));
                return !left.isEmpty();
            });
        }

        // ── Vitals strip ───────────────────────────────────────
        var hs = plugin.getPlayerVirtualHealthManager().peekSnapshot(id);
        var bar = plugin.getDefenseBarManager().peekDefenseBar(id);
        double[] mana = plugin.getPlayerManaManager().peekMana(id);
        double hp = hs == null ? 0 : hs.currentHp(), maxHp = hs == null ? 0 : hs.maxHp();
        v.button(11, Ui.item(Material.GLISTERING_MELON_SLICE, PlayerTheme.HEALTH + "♥ Health", Ui.lore()
                .raw(ChatColor.WHITE + Numbers.hud(hp) + ChatColor.DARK_GRAY + " / " + ChatColor.WHITE + Numbers.hud(maxHp))
                .raw(PlayerTheme.bar(hp, maxHp, 20, ChatColor.RED))
                .gap()
                .raw(PlayerTheme.DEFENSE + "⛨ Defense bar " + ChatColor.WHITE + (bar == null ? "?" : Numbers.hud(bar.getCurrent())
                        + ChatColor.DARK_GRAY + " / " + ChatColor.WHITE + Numbers.hud(bar.getMaximum())))
                .text("Absorbs part of each hit, then refills")
                .text("after a few seconds out of combat.")
                .gap().text("Max health grows with Vitality, from")
                .text(feeders(s.rules(), Attribute.VITALITY) + ".")
                .hint("Click for survivability details").build()), c -> openSection(c.player(), "stats"));

        v.button(13, Ui.glow(Ui.item(Material.NETHER_STAR, PlayerTheme.REALM + "✦ Realm: " + realm.displayName, Ui.lore()
                .stat("Realm", "R" + realm.number + " of " + Realm.values().length + ChatColor.DARK_GRAY + "  (" + realm.epithet + ")")
                .stat("Realm magnitude", "x" + Numbers.compact(s.realmMultiplier()) + (testRealm ? "  (test realm from an admin)" : ""))
                .gap().text("Each realm is a step of " + PlayerTheme.realmStep(plugin.getCombatConfig().mobHealthScaling) + ":")
                .text("damage against creatures, health")
                .text("and defense all scale.")
                .gap().raw(Availability.State.IN_DEVELOPMENT.label + ChatColor.GRAY + ": breakthroughs to the next realm")
                .text("are not built yet. Everyone is " + plugin.getPlayerRealms().defaultRealm().displayName + " for now.")
                .hint("Click for the realm ladder").build()), true), c -> openSection(c.player(), "ascension"));

        double manaCur = mana == null ? 0 : mana[0], manaMax = mana == null ? 0 : mana[1], regen = mana == null ? 0 : mana[2];
        v.button(15, Ui.item(Material.HEART_OF_THE_SEA, PlayerTheme.ESSENCE + "✎ Essence (mana)", Ui.lore()
                .raw(ChatColor.WHITE + Numbers.hud(manaCur) + ChatColor.DARK_GRAY + " / " + ChatColor.WHITE + Numbers.hud(manaMax))
                .raw(PlayerTheme.bar(manaCur, manaMax, 20, ChatColor.AQUA))
                .stat("Regeneration", Numbers.hud(regen) + " / s")
                .gap().text("Spent on spells such as Fireball.")
                .text("Pool and regeneration grow with the")
                .text("Essence attribute, from")
                .text(feeders(s.rules(), Attribute.ESSENCE) + ".")
                .hint("Click for magic details").build()), c -> openSection(c.player(), "stats"));

        // ── Destinations ───────────────────────────────────────
        for (PlayerMenuSections.Section sec : plugin.getPlayerMenuGUI().sections().all()) {
            Availability a = safeAvailability(sec, p);
            Ui.Lore l = Ui.lore();
            for (String line : sec.summary()) l.text(line);
            if (sec.live() != null) {
                List<String> live = safeLive(sec, p);
                if (!live.isEmpty()) { l.gap(); live.forEach(l::raw); }
            }
            l.gap().raw(a.state().label);
            if (a.note() != null) l.raw(ChatColor.DARK_GRAY + a.note());
            if (a.isAvailable() && sec.opener() != null) l.hint("Click to open");
            // New skill levels since the menu was last closed: the Skills tile counts them, with one soft chime.
            int news = "skills".equals(sec.id()) ? memory().gainedTotal(id, plugin.getSkillManager()) : 0;
            if (news > 0 && memory().wouldAnnounce(id)) {
                v.soundLater(4, org.bukkit.Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.7f, 1.3f);
                v.runLater(4, () -> memory().markAnnounced(id));
            }
            v.button(sec.slot(), PlayerTheme.badge(Ui.item(sec.icon(), (a.isAvailable() ? PlayerTheme.PRIMARY : ChatColor.GRAY) + sec.name(),
                    l.build()), news), c -> openSection(c.player(), sec.id()));
        }
    }

    /** The head's lines: realm, total level, attributes, then the effects on the player now (meal first). */
    private List<String> headLore(Player p, List<Effects.ActiveEffect> temp, long now) {
        AttributeSnapshot s = plugin.getAttributeService().get(p.getUniqueId());
        Realm realm = s.realm().realm();
        int total = SkillView.totalLevel(plugin, p.getUniqueId()), cap = SkillDefinitions.all().size() * SkillProgression.MAX_LEVEL;
        WeaponClass best = bestPowerClass(s);
        Ui.Lore l = Ui.lore()
                .raw(realm.color() + "R" + realm.number + " " + realm.displayName + ChatColor.DARK_GRAY + "  " + realm.epithet)
                .gap()
                .stat("Total skill level", Numbers.hud(total) + ChatColor.DARK_GRAY + " / " + Numbers.hud(cap))
                .raw(PlayerTheme.bar(total, cap, 20, ChatColor.GREEN))
                .gap()
                .raw(Attribute.POWER.color + "Power " + ChatColor.WHITE + one(s.power(best))
                        + ChatColor.DARK_GRAY + " (" + best.displayName.toLowerCase() + ")"
                        + "   " + Attribute.AGILITY.color + "Agility " + ChatColor.WHITE + one(s.get(Attribute.AGILITY)))
                .raw(Attribute.VITALITY.color + "Vitality " + ChatColor.WHITE + one(s.get(Attribute.VITALITY))
                        + "   " + Attribute.FORTITUDE.color + "Fortitude " + ChatColor.WHITE + one(s.get(Attribute.FORTITUDE)))
                .raw(Attribute.ESSENCE.color + "Essence " + ChatColor.WHITE + one(s.get(Attribute.ESSENCE))
                        + "   " + Attribute.FORTUNE.color + "Fortune " + ChatColor.WHITE + GUIs.stats.StatsMenu.fortuneText(s))
                .gap()
                .raw(ChatColor.YELLOW + "Active effects:");
        for (String line : ActiveEffectsMenu.summary(temp, now, 4)) l.raw(" " + line);
        return l.hint("Click for your active effects").build();
    }

    /** Open a hub section by id, honouring its availability (re-evaluated at click time). */
    private void openSection(Player p, String id) {
        PlayerMenuSections.Section sec = plugin.getPlayerMenuGUI().sections().byId(id);
        if (sec == null) return;
        Availability a = safeAvailability(sec, p);
        if (a.isAvailable() && sec.opener() != null) {
            menus.open(p, sec.opener().apply(plugin));
        } else if (a.state() == Availability.State.LOCKED) {
            Ui.fail(p, sec.name() + " is locked" + (a.note() != null ? ": " + a.note() : "."));
        } else {
            Ui.info(p, sec.name() + " is in development" + (a.note() != null ? ": " + a.note() : "."));
        }
    }

    private static Availability safeAvailability(PlayerMenuSections.Section sec, Player p) {
        try {
            Availability a = sec.availability().apply(p);
            return a != null ? a : Availability.inDevelopment(null);
        } catch (RuntimeException e) {
            return Availability.inDevelopment("Unavailable right now.");
        }
    }

    private List<String> safeLive(PlayerMenuSections.Section sec, Player p) {
        try {
            List<String> l = sec.live().apply(plugin, p);
            return l != null ? l : List.of();
        } catch (RuntimeException e) {
            return List.of();
        }
    }

    /** "Survival, Heavy Armor Mastery and Field Medicine": the skills feeding {@code a}, biggest first (max 3). */
    private static String feeders(AttributeRules rules, Attribute a) {
        java.util.Map<String, Double> weight = new java.util.HashMap<>();
        rules.skillWeights().forEach((skill, ws) -> {
            for (AttributeRules.SkillWeight w : ws) if (w.attribute() == a) weight.merge(skill, w.perLevel(), Math::max);
        });
        List<String> names = weight.entrySet().stream()
                .sorted(java.util.Map.Entry.<String, Double>comparingByValue().reversed().thenComparing(java.util.Map.Entry.comparingByKey()))
                .limit(3).map(e -> Skills.SkillNames.display(e.getKey())).toList();
        if (names.isEmpty()) return "no skill yet";
        if (names.size() == 1) return names.get(0);
        return String.join(", ", names.subList(0, names.size() - 1)) + " and " + names.get(names.size() - 1);
    }

    /** The weapon type with the most Power (for the summary line). */
    public static WeaponClass bestPowerClass(AttributeSnapshot s) {
        WeaponClass best = WeaponClass.SWORD;
        for (WeaponClass w : WeaponClass.values()) {
            if (w != WeaponClass.OTHER && s.power(w) > s.power(best)) best = w;
        }
        return best;
    }

    private static String one(double v) {
        return v == Math.rint(v) ? String.valueOf((long) v) : String.format(java.util.Locale.ROOT, "%.1f", v);
    }
}
