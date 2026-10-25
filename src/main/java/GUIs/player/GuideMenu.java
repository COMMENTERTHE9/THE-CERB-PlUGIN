package GUIs.player;

import Attributes.Attribute;
import GUIs.SkillDefinitions;
import GUIs.menu.Fmt;
import GUIs.menu.MenuView;
import GUIs.menu.Ui;
import Manager.Realm;
import Skills.SkillProgression;
import cerberus.world.cerb.CerberusPlugin;
import org.bukkit.ChatColor;
import org.bukkit.Material;

/**
 * Player Menu → Guide: how Cerberus works today, in short topics. Numbers that come from config
 * (blocks per level, skill count, max level) are read live, so the guide never drifts.
 */
public final class GuideMenu extends PlayerMenu {

    public GuideMenu(CerberusPlugin plugin) {
        super(plugin);
    }

    @Override public String title() { return PlayerTheme.title("Guide"); }

    @Override
    protected void content(MenuView v) {
        long perLevel = Math.round(plugin.getCombatConfig().mobHealthScaling.getBlocksPerLevel());
        int skills = SkillDefinitions.all().size();

        v.set(MenuView.HEADER, Ui.item(Material.BOOK, ChatColor.GOLD + "" + ChatColor.BOLD + "Guide",
                Ui.lore().text("How Cerberus works today.").text("Hover a topic to read it.").build()));

        v.set(19, topic(Material.OAK_SAPLING, "Getting started",
                "Right-click the head in your last hotbar slot, or type /menu, to open the Player Menu.",
                "Creatures grow stronger the farther you go from world spawn: every " + Fmt.grouped(perLevel)
                        + " blocks adds a level.",
                "Skills improve by doing. Each skill's page says exactly what trains it."));
        v.set(21, topic(Material.EXPERIENCE_BOTTLE, "Skills",
                skills + " skills in three groups: combat, magic and utility. Each goes from level 0 to "
                        + SkillProgression.MAX_LEVEL + ".",
                "Every level adds attribute points, so you grow stronger as you train.",
                "Some skills cannot be trained yet; their pages say so."));
        v.set(23, topic(Material.BLAZE_POWDER, "Attributes",
                Attribute.POWER.displayName + ": weapon damage; it keeps pace with creature levels.",
                Attribute.AGILITY.displayName + ": critical hits against creatures.",
                Attribute.VITALITY.displayName + ": max health.",
                Attribute.FORTITUDE.displayName + ": armor, defense bar and resistances.",
                Attribute.ESSENCE.displayName + ": Essence pool, regeneration and spell damage.",
                Attribute.FORTUNE.displayName + ": not used in the game yet."));
        v.set(25, topic(Material.GOLDEN_APPLE, "Health and defense",
                "Your real health and Essence are shown above your hotbar; the hearts stay full.",
                "The defense bar absorbs part of each hit and refills a few seconds after combat.",
                "When you die you respawn at once and keep your items."));
        v.set(29, topic(Material.NETHER_STAR, "Realms",
                Realm.values().length + " realms, from " + Realm.values()[0].displayName + " to "
                        + Realm.values()[Realm.values().length - 1].displayName + ". Each realm is "
                        + PlayerTheme.realmStep(plugin.getCombatConfig().mobHealthScaling) + " the one before.",
                "Everyone is " + plugin.getPlayerRealms().defaultRealm().displayName
                        + " for now: rising to the next realm is not built yet."));
        v.set(31, topic(Material.HEART_OF_THE_SEA, "Essence and spells",
                "Essence is your magic pool. Spells such as Fireball spend it, and it refills over time.",
                "The Essence attribute raises the pool, its regeneration and spell damage."));
        v.set(33, topic(Material.PAPER, "Commands",
                "/menu opens this menu.",
                "/stats opens your Stats & Attributes page."));
    }

    private static org.bukkit.inventory.ItemStack topic(Material icon, String name, String... paragraphs) {
        Ui.Lore l = Ui.lore();
        for (String para : paragraphs) {
            l.gap();
            for (String line : PlayerTheme.wrap(para, SkillView.WIDTH)) l.text(line);
        }
        return Ui.item(icon, ChatColor.YELLOW + name, l.build());
    }
}
