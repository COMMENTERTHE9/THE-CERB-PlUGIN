package GUIs.admin;

import GUIs.menu.MenuView;
import GUIs.menu.Status;
import GUIs.menu.Ui;
import org.bukkit.ChatColor;
import org.bukkit.Material;

import java.util.UUID;

/**
 * Placeholder for the Attributes milestone, clearly marked as not built. When the attribute
 * engine lands, the "attributes" Home section is re-registered with the real inspector.
 */
final class AttributesDevMenu extends AdminMenu {

    private final UUID targetId; // nullable: opened from Home

    AttributesDevMenu(AdminPanel panel, UUID targetId) {
        super(panel);
        this.targetId = targetId;
    }

    @Override public String title() { return PREFIX + "Attributes (in development)"; }

    private record Attr(Material icon, ChatColor color, String name, String does, String from) {}

    private static final Attr[] ATTRS = {
            new Attr(Material.GOLDEN_APPLE, ChatColor.RED, "Vitality", "Max health", "Survival, Heavy Armor Training, realm"),
            new Attr(Material.LAPIS_LAZULI, ChatColor.BLUE, "Essence", "Mana pool, mana regen, magic power", "Intelligence, Mana Regeneration, Spell Weaving, realm"),
            new Attr(Material.NETHERITE_SWORD, ChatColor.DARK_RED, "Power", "Damage you deal", "Blade / Weapon Mastery, Martial Expertise, Ranged Precision, realm"),
            new Attr(Material.SHIELD, ChatColor.GRAY, "Fortitude", "Armor, resistances, defense bar", "Heavy Armor Training, Survival, Defensive Magic, realm"),
            new Attr(Material.FEATHER, ChatColor.GREEN, "Agility", "Speed, attack speed, crit, dodge", "Critical Strike, Dual Wielding, Navigation, Stealth"),
            new Attr(Material.EMERALD, ChatColor.GOLD, "Fortune", "Luck, magic find, drop rate", "Scavenging, Lockpicking, Trading, Fishing"),
    };

    @Override
    protected void build(MenuView v) {
        frame(v);
        v.set(MenuView.HEADER, Ui.item(Material.NETHER_STAR, ChatColor.GRAY + "" + ChatColor.BOLD + "Attributes",
                Ui.lore().status(Status.IN_DEVELOPMENT)
                        .text("One stat engine every system feeds and")
                        .text("combat reads; balance caps applied once.")
                        .text("Skill/realm sources below are the plan,")
                        .text("not live values.").build()));
        int[] slots = {19, 20, 21, 23, 24, 25};
        for (int i = 0; i < ATTRS.length; i++) {
            Attr a = ATTRS[i];
            v.set(slots[i], Ui.item(a.icon(), a.color() + "" + ChatColor.BOLD + a.name(),
                    Ui.lore().stat("Will drive", a.does()).text("Planned sources:").text("  " + a.from())
                            .gap().status(Status.IN_DEVELOPMENT).build()));
        }
        v.set(31, Ui.item(Material.WRITABLE_BOOK, ChatColor.WHITE + "Admin tools coming with it",
                Ui.lore().text("Inspect any player's attributes with a")
                        .text("per-source breakdown, compare before/after,")
                        .text("and set skill levels to test the effect.")
                        .gap().status(Status.IN_DEVELOPMENT).build()));
        if (targetId != null) {
            v.button(32, Ui.item(Material.ENCHANTED_BOOK, ChatColor.GOLD + "Skills for this player",
                    Ui.lore().text("Skill levels are live today.").hint("Click to open").build()),
                    c -> open(c.player(), new SkillListMenu(panel, targetId)));
        }
    }
}
