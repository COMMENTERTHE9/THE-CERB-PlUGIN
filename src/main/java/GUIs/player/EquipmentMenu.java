package GUIs.player;

import Attributes.AttributeSnapshot;
import Attributes.WeaponClass;
import GUIs.menu.Fmt;
import GUIs.menu.MenuView;
import GUIs.menu.Ui;
import Manager.CombatConfig;
import Manager.PlayerDefenseManager;
import Skills.SkillNames;
import cerberus.world.cerb.CerberusPlugin;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

/**
 * Player Menu → Equipment: what the worn armor and held items do today (armor reduction, heavy
 * armor, weapon type, which skill a kill trains). Shows pictures of the gear, never the items
 * themselves, so nothing here can be taken. Custom gear is not built; the screen says so.
 */
public final class EquipmentMenu extends PlayerMenu {

    public EquipmentMenu(CerberusPlugin plugin) {
        super(plugin);
    }

    @Override public String title() { return PlayerTheme.title("Equipment"); }

    @Override
    protected void content(MenuView v) {
        Player p = v.viewer();
        PlayerInventory inv = p.getInventory();
        CombatConfig cc = plugin.getCombatConfig();
        AttributeSnapshot s = plugin.getAttributeService().get(p.getUniqueId());

        ItemStack[] armor = {inv.getHelmet(), inv.getChestplate(), inv.getLeggings(), inv.getBoots()};
        String[] slotNames = {"Helmet", "Chestplate", "Leggings", "Boots"};
        int[] slots = {11, 20, 29, 38};
        double gear = 0;
        for (int i = 0; i < armor.length; i++) {
            ItemStack piece = armor[i];
            if (piece == null || piece.getType().isAir()) {
                v.set(slots[i], Ui.item(Material.LIGHT_GRAY_STAINED_GLASS_PANE, ChatColor.GRAY + slotNames[i] + ": empty",
                        Ui.lore().text("Nothing worn here.").build()));
                continue;
            }
            double r = cc.getArmorReductionForPiece(piece.getType());
            gear += r;
            Ui.Lore l = Ui.lore().stat("Damage reduction", "+" + Fmt.pct(r));
            if (PlayerDefenseManager.isHeavyArmor(piece.getType())) {
                l.raw(ChatColor.GREEN + "● Heavy armor: trains " + SkillNames.display("Heavy Armor Training"));
            }
            v.set(slots[i], Ui.item(piece.getType(), ChatColor.WHITE + slotNames[i] + ": " + Fmt.title(piece.getType().name()), l.build()));
        }

        // Totals, using the same parts PlayerDefenseManager adds up
        double skill = PlayerDefenseManager.heavyArmorBonus(cc, plugin.getSkillManager().getSkillLevel(p.getUniqueId(), "Heavy Armor Training"));
        boolean fortOn = s.rules().switches().fortitude();    // same switch PlayerDefenseManager reads
        double fort = fortOn ? s.armorBonus() : 0.0;
        double cap = fortOn ? Math.min(cc.armorMaxReduction, s.rules().limits().armor()) : cc.armorMaxReduction;
        double total = plugin.getPlayerVirtualHealthManager().getArmorReduction(p);
        boolean heavy = PlayerDefenseManager.wearsHeavyArmor(p);
        v.set(MenuView.HEADER, Ui.item(Material.ARMOR_STAND, ChatColor.GOLD + "" + ChatColor.BOLD + "Your equipment", Ui.lore()
                .stat("Damage reduction", Fmt.pct(total) + ChatColor.DARK_GRAY + "  (max " + Fmt.pct(cap) + ")")
                .stat("Heavy armor", heavy ? "yes" : "no").build()));

        Ui.Lore t = Ui.lore()
                .stat("From armor pieces", "+" + Fmt.pct(gear))
                .stat("From " + SkillNames.display("Heavy Armor Training"), "+" + Fmt.pct(skill))
                .stat("From Fortitude", "+" + Fmt.pct(fort))
                .raw(ChatColor.GRAY + "Total: " + ChatColor.WHITE + Fmt.pct(total)
                        + (gear + skill + fort > cap + 1e-9 ? ChatColor.YELLOW + "  (capped at " + Fmt.pct(cap) + ")" : ""))
                .gap().text("Each hit you take is reduced by this.")
                .text("Stronger creatures pierce part of it:")
                .text("the higher their tier, the more.");
        v.set(22, Ui.item(Material.SHIELD, ChatColor.GOLD + "Damage reduction", t.build()));

        v.set(15, handTile("Main hand", inv.getItemInMainHand(), s));
        v.set(24, handTile("Off hand", inv.getItemInOffHand(), s));

        v.set(42, Ui.item(Material.GRAY_DYE, ChatColor.GRAY + "Custom gear", Ui.lore()
                .raw(Availability.State.IN_DEVELOPMENT.label)
                .text("Gear with its own stats, upgrades and")
                .text("set bonuses is not built yet. Today")
                .text("armor reduces damage, and your weapon")
                .text("decides which Power applies and which")
                .text("skill a kill trains.").build()));
        v.refreshButton();
    }

    private ItemStack handTile(String hand, ItemStack item, AttributeSnapshot s) {
        if (item == null || item.getType().isAir()) {
            return Ui.item(Material.LIGHT_GRAY_STAINED_GLASS_PANE, ChatColor.GRAY + hand + ": empty",
                    Ui.lore().text("Fighting bare-handed uses unarmed").text("Power (" + SkillNames.display("Martial Expertise") + ").").build());
        }
        if (plugin.getPlayerMenuGUI().isPlayerMenuHead(item)) {
            return Ui.item(Material.PLAYER_HEAD, ChatColor.LIGHT_PURPLE + hand + ": Player Menu",
                    Ui.lore().text("Hold a weapon to see what it does.").build());
        }
        WeaponClass wc = WeaponClass.ofMaterial(item.getType().name());
        Ui.Lore l = Ui.lore().stat("Weapon type", wc.displayName);
        if (!hand.equals("Main hand")) {
            l.text("Only the main hand counts for hits").text("and kill XP.");
            return Ui.item(item.getType(), ChatColor.WHITE + hand + ": " + Fmt.title(item.getType().name()), l.build());
        }
        if (wc != WeaponClass.OTHER) {
            l.stat("Power", Fmt.one(s.power(wc)) + "  ->  " + Fmt.mult(s.powerMultiplier(wc)) + " damage vs creatures");
        } else {
            l.text("Not a weapon: only all-weapon Power").text("applies to hits with it.");
        }
        var mapper = plugin.getSkillMapper();
        String trains = mapper == null ? "" : mapper.resolveKillSkill(item.getType().name());
        if (!trains.isEmpty()) l.stat("Kills train", SkillNames.display(trains));
        else l.text("Kills with it train no skill, except")
                .text(SkillNames.display("Heavy Armor Training") + " in heavy armor.");
        return Ui.item(item.getType(), ChatColor.WHITE + hand + ": " + Fmt.title(item.getType().name()), l.build());
    }
}
