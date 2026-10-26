package GUIs.admin;

import Attributes.Attribute;
import Attributes.AttributeRules;
import Attributes.AttributeService;
import Attributes.AttributeSnapshot;
import Attributes.WeaponClass;
import GUIs.menu.Fmt;
import GUIs.menu.MenuView;
import GUIs.menu.Ui;
import GUIs.stats.AttributeView;
import Skills.SkillProgression;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * "What if this skill were level N?" for one player: before/after attributes and derived stats,
 * computed from their real contributions with one skill swapped. Read-only: changes nothing.
 */
final class AttributeSimulatorMenu extends AdminMenu {

    private static final int[] PRESETS = {1, 10, 25, 50, 75, 99, 100};
    private static final Attribute[] ORDER = {Attribute.POWER, Attribute.AGILITY, Attribute.VITALITY,
            Attribute.FORTITUDE, Attribute.ESSENCE, Attribute.FORTUNE};

    private final UUID targetId;
    private int skillIndex;
    private int level = -1; // -1 = start from the player's real level

    AttributeSimulatorMenu(AdminPanel panel, UUID targetId) {
        super(panel);
        this.targetId = targetId;
    }

    @Override public String title() { return PREFIX + "Attribute simulator"; }

    private List<String> skills(AttributeRules rules) {
        List<String> list = new ArrayList<>(rules.skillWeights().keySet());
        list.sort(String.CASE_INSENSITIVE_ORDER);
        return list;
    }

    @Override
    protected void build(MenuView v) {
        frame(v);
        Player t = Bukkit.getPlayer(targetId);
        if (t == null) {
            v.set(22, Ui.item(Material.GRAY_DYE, ChatColor.GRAY + "This player is offline"));
            return;
        }
        AttributeService svc = plugin.getAttributeService();
        AttributeRules rules = svc.rules();
        List<String> skills = skills(rules);
        if (skills.isEmpty()) {
            v.set(22, Ui.item(Material.BARRIER, ChatColor.RED + "No skills feed attributes", Ui.lore().text("Check attributes.yml.").build()));
            return;
        }
        skillIndex = Math.floorMod(skillIndex, skills.size());
        String skill = skills.get(skillIndex);
        int real = plugin.getSkillManager().getSkillLevel(targetId, skill);
        if (level < 0) level = real;
        level = Math.max(1, Math.min(SkillProgression.MAX_LEVEL, level));

        AttributeSnapshot now = svc.get(targetId);
        AttributeSnapshot sim = AttributeView.withSkillLevel(svc, targetId, skill, level);

        v.set(MenuView.HEADER, Ui.item(Material.COMPARATOR, ChatColor.GREEN + "" + ChatColor.BOLD + "Simulator - nothing is changed",
                Ui.lore().stat("Player", t.getName()).stat("Skill", skill)
                        .stat("Level", real + " (real)  ->  " + level + " (simulated)").build()));

        // Row 1: before -> after for each attribute
        for (int i = 0; i < ORDER.length; i++) {
            Attribute a = ORDER[i];
            Ui.Lore l = Ui.lore();
            if (a == Attribute.POWER) {
                for (WeaponClass w : WeaponClass.values()) {
                    if (w == WeaponClass.OTHER || (now.power(w) <= 0 && sim.power(w) <= 0)) continue;
                    l.raw(ChatColor.WHITE + "  " + w.displayName + ": " + change(now.powerMultiplier(w), sim.powerMultiplier(w), true));
                }
            } else if (a == Attribute.FORTUNE) { // scoped parts too: Mining's Fortune counts on ore, Woodcutting's on wood
                l.raw(ChatColor.WHITE + "  " + a.displayName + ": " + change(now.get(a), sim.get(a), false));
                for (Attributes.Yield y : Attributes.Yield.values()) {
                    if (now.fortune(y) != now.get(a) || sim.fortune(y) != sim.get(a)) {
                        l.raw(ChatColor.WHITE + "  " + a.displayName + " (" + y.id + "): " + change(now.fortune(y), sim.fortune(y), false));
                    }
                }
            } else {
                l.raw(ChatColor.WHITE + "  " + a.displayName + ": " + change(now.get(a), sim.get(a), false));
            }
            List<String> before = AttributeView.effects(a, now), after = AttributeView.effects(a, sim);
            for (int k = 0; k < after.size(); k++) {
                if (!after.get(k).equals(k < before.size() ? before.get(k) : "")) l.raw(ChatColor.DARK_GREEN + "  now: " + after.get(k));
            }
            boolean changed = a == Attribute.POWER ? differs(now, sim) : now.get(a) != sim.get(a)
                    || (a == Attribute.FORTUNE && java.util.Arrays.stream(Attributes.Yield.values()).anyMatch(y -> now.fortune(y) != sim.fortune(y)));
            v.set(10 + i + (i >= 3 ? 1 : 0), Ui.glow(Ui.item(AttributeView.icon(a), a.color + a.displayName
                    + (changed ? ChatColor.GREEN + " (changes)" : ChatColor.DARK_GRAY + " (no change)"), l.build()), changed));
        }

        // Row 2: choose the skill
        v.button(19, Ui.item(Material.ARROW, ChatColor.YELLOW + "Previous skill"), c -> { skillIndex--; level = -1; menus.refresh(c.player()); });
        v.button(22, Ui.item(Material.ENCHANTED_BOOK, ChatColor.GOLD + skill, Ui.lore()
                .stat("Real level", String.valueOf(real))
                .text("Feeds:").raw(feeds(skill, rules))
                .hint("Left/right click: next/previous skill").build()),
                c -> { skillIndex += c.type() == ClickType.RIGHT ? -1 : 1; level = -1; menus.refresh(c.player()); });
        v.button(25, Ui.item(Material.ARROW, ChatColor.YELLOW + "Next skill"), c -> { skillIndex++; level = -1; menus.refresh(c.player()); });

        // Row 3: step the level
        step(v, 28, Material.RED_CONCRETE, -10);
        step(v, 29, Material.PINK_CONCRETE, -1);
        v.set(31, Ui.amount(Ui.item(Material.EXPERIENCE_BOTTLE, ChatColor.YELLOW + "Simulated level " + level), Math.max(1, Math.min(64, level))));
        step(v, 33, Material.LIME_CONCRETE, +1);
        step(v, 34, Material.GREEN_CONCRETE, +10);

        // Row 4: presets
        for (int i = 0; i < PRESETS.length; i++) {
            int p = PRESETS[i];
            v.button(37 + i, Ui.glow(Ui.amount(Ui.item(p == level ? Material.GOLD_BLOCK : Material.IRON_BLOCK,
                    ChatColor.WHITE + "Level " + p), p), p == level), c -> { level = p; menus.refresh(c.player()); });
        }
    }

    private void step(MenuView v, int slot, Material m, int delta) {
        v.button(slot, Ui.item(m, (delta > 0 ? ChatColor.GREEN + "+" : ChatColor.RED + "") + delta),
                c -> { level = Math.max(1, Math.min(SkillProgression.MAX_LEVEL, level + delta)); menus.refresh(c.player()); });
    }

    private static boolean differs(AttributeSnapshot a, AttributeSnapshot b) {
        for (WeaponClass w : WeaponClass.values()) if (a.power(w) != b.power(w)) return true;
        return false;
    }

    private static String feeds(String skill, AttributeRules rules) {
        List<String> parts = new ArrayList<>();
        for (AttributeRules.SkillWeight sw : rules.skillWeights().getOrDefault(skill, List.of())) {
            parts.add(sw.attribute().displayName + " +" + sw.perLevel() + (sw.weapon() != null ? " (" + sw.weapon().displayName.toLowerCase() + ")" : ""));
        }
        return ChatColor.WHITE + "  " + (parts.isEmpty() ? "nothing" : String.join(", ", parts));
    }

    private static String change(double before, double after, boolean multiplier) {
        String b = multiplier ? Fmt.mult(before) : Fmt.one(before), a = multiplier ? Fmt.mult(after) : Fmt.one(after);
        ChatColor c = after > before ? ChatColor.GREEN : after < before ? ChatColor.RED : ChatColor.GRAY;
        return ChatColor.GRAY + b + " -> " + c + a;
    }
}
