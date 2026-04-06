package Commands;

import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlotGroup;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;

/**
 * /givetestitem <type> — gives test items for gameplay testing.
 * Available: fireball, sword, axe, bow, armor, kit
 */
public class GiveTestItemCommand implements CommandExecutor {

    private final JavaPlugin plugin;

    public GiveTestItemCommand(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Players only.");
            return true;
        }

        String type = args.length > 0 ? args[0].toLowerCase() : "list";

        switch (type) {
            case "fireball" -> giveFireballWand(player);
            case "sword" -> giveCerbSword(player);
            case "axe" -> giveCerbAxe(player);
            case "bow" -> giveCerbBow(player);
            case "armor" -> giveCerbArmor(player);
            case "kit" -> {
                giveCerbSword(player);
                giveCerbAxe(player);
                giveCerbBow(player);
                giveFireballWand(player);
                giveCerbArmor(player);
                player.sendMessage(ChatColor.GOLD + "" + ChatColor.BOLD + "Full test kit received!");
            }
            default -> player.sendMessage(ChatColor.YELLOW + "Usage: /givetestitem <fireball|sword|axe|bow|armor|kit>");
        }
        return true;
    }

    // ── Fireball Wand ────────────────────────────────────────────

    private void giveFireballWand(Player player) {
        ItemStack wand = new ItemStack(Material.BLAZE_ROD);
        ItemMeta meta = wand.getItemMeta();
        if (meta == null) return;

        meta.setDisplayName(ChatColor.RED + "" + ChatColor.BOLD + "Fireball Wand");
        meta.setLore(List.of(
                "",
                ChatColor.GRAY + "Right-click to cast " + ChatColor.RED + "Fireball",
                "",
                ChatColor.DARK_GRAY + "Mana Cost: " + ChatColor.AQUA + "20",
                ChatColor.DARK_GRAY + "Damage: " + ChatColor.RED + "10",
                ChatColor.DARK_GRAY + "Cooldown: " + ChatColor.YELLOW + "1s",
                "",
                ChatColor.DARK_PURPLE + "Pyromancy Spell"
        ));
        meta.getPersistentDataContainer().set(
                new NamespacedKey(plugin, "cerb_spell"), PersistentDataType.STRING, "Fireball");
        meta.addEnchant(Enchantment.UNBREAKING, 1, true);
        meta.addItemFlags(ItemFlag.HIDE_ENCHANTS, ItemFlag.HIDE_ATTRIBUTES);
        wand.setItemMeta(meta);
        player.getInventory().addItem(wand);
        player.sendMessage(ChatColor.GREEN + "You received a " + ChatColor.RED + "Fireball Wand" + ChatColor.GREEN + "!");
    }

    // ── Cerberus Sword ───────────────────────────────────────────

    private void giveCerbSword(Player player) {
        ItemStack sword = new ItemStack(Material.NETHERITE_SWORD);
        ItemMeta meta = sword.getItemMeta();
        if (meta == null) return;

        meta.setDisplayName(ChatColor.DARK_RED + "" + ChatColor.BOLD + "Cerberus Blade");
        meta.setLore(List.of(
                "",
                ChatColor.GRAY + "A blade forged in the underworld.",
                "",
                ChatColor.DARK_GRAY + "Damage: " + ChatColor.RED + "+10",
                ChatColor.DARK_GRAY + "Speed: " + ChatColor.YELLOW + "1.6",
                "",
                ChatColor.GOLD + "Legendary Weapon"
        ));
        meta.addEnchant(Enchantment.SHARPNESS, 3, true);
        meta.addEnchant(Enchantment.FIRE_ASPECT, 1, true);
        meta.setUnbreakable(true);
        meta.addItemFlags(ItemFlag.HIDE_ENCHANTS, ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_UNBREAKABLE);

        // Custom attack damage
        meta.addAttributeModifier(Attribute.ATTACK_DAMAGE,
                new AttributeModifier(new NamespacedKey(plugin, "cerb_sword_dmg"),
                        10.0, AttributeModifier.Operation.ADD_NUMBER, EquipmentSlotGroup.MAINHAND));
        meta.addAttributeModifier(Attribute.ATTACK_SPEED,
                new AttributeModifier(new NamespacedKey(plugin, "cerb_sword_spd"),
                        -2.4, AttributeModifier.Operation.ADD_NUMBER, EquipmentSlotGroup.MAINHAND));

        sword.setItemMeta(meta);
        player.getInventory().addItem(sword);
        player.sendMessage(ChatColor.GREEN + "You received the " + ChatColor.DARK_RED + "Cerberus Blade" + ChatColor.GREEN + "!");
    }

    // ── Cerberus Axe ─────────────────────────────────────────────

    private void giveCerbAxe(Player player) {
        ItemStack axe = new ItemStack(Material.NETHERITE_AXE);
        ItemMeta meta = axe.getItemMeta();
        if (meta == null) return;

        meta.setDisplayName(ChatColor.DARK_AQUA + "" + ChatColor.BOLD + "Styx Cleaver");
        meta.setLore(List.of(
                "",
                ChatColor.GRAY + "Heavy axe from the river Styx.",
                "",
                ChatColor.DARK_GRAY + "Damage: " + ChatColor.RED + "+14",
                ChatColor.DARK_GRAY + "Speed: " + ChatColor.YELLOW + "0.9 (Slow)",
                "",
                ChatColor.GOLD + "Legendary Weapon"
        ));
        meta.addEnchant(Enchantment.SHARPNESS, 4, true);
        meta.setUnbreakable(true);
        meta.addItemFlags(ItemFlag.HIDE_ENCHANTS, ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_UNBREAKABLE);

        meta.addAttributeModifier(Attribute.ATTACK_DAMAGE,
                new AttributeModifier(new NamespacedKey(plugin, "cerb_axe_dmg"),
                        14.0, AttributeModifier.Operation.ADD_NUMBER, EquipmentSlotGroup.MAINHAND));
        meta.addAttributeModifier(Attribute.ATTACK_SPEED,
                new AttributeModifier(new NamespacedKey(plugin, "cerb_axe_spd"),
                        -3.1, AttributeModifier.Operation.ADD_NUMBER, EquipmentSlotGroup.MAINHAND));

        axe.setItemMeta(meta);
        player.getInventory().addItem(axe);
        player.sendMessage(ChatColor.GREEN + "You received the " + ChatColor.DARK_AQUA + "Styx Cleaver" + ChatColor.GREEN + "!");
    }

    // ── Cerberus Bow ─────────────────────────────────────────────

    private void giveCerbBow(Player player) {
        ItemStack bow = new ItemStack(Material.BOW);
        ItemMeta meta = bow.getItemMeta();
        if (meta == null) return;

        meta.setDisplayName(ChatColor.DARK_GREEN + "" + ChatColor.BOLD + "Hydra's Reach");
        meta.setLore(List.of(
                "",
                ChatColor.GRAY + "A bow made from hydra sinew.",
                "",
                ChatColor.DARK_GRAY + "Power: " + ChatColor.RED + "V",
                ChatColor.DARK_GRAY + "Infinity: " + ChatColor.YELLOW + "Yes",
                "",
                ChatColor.GOLD + "Legendary Weapon"
        ));
        meta.addEnchant(Enchantment.POWER, 5, true);
        meta.addEnchant(Enchantment.INFINITY, 1, true);
        meta.addEnchant(Enchantment.FLAME, 1, true);
        meta.setUnbreakable(true);
        meta.addItemFlags(ItemFlag.HIDE_ENCHANTS, ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_UNBREAKABLE);

        bow.setItemMeta(meta);
        player.getInventory().addItem(bow);

        // Give one arrow for infinity to work
        player.getInventory().addItem(new ItemStack(Material.ARROW, 1));
        player.sendMessage(ChatColor.GREEN + "You received " + ChatColor.DARK_GREEN + "Hydra's Reach" + ChatColor.GREEN + "!");
    }

    // ── Cerberus Armor Set ───────────────────────────────────────

    private void giveCerbArmor(Player player) {
        giveArmorPiece(player, Material.NETHERITE_HELMET, "Cerberus Helm", Enchantment.PROTECTION, 4);
        giveArmorPiece(player, Material.NETHERITE_CHESTPLATE, "Cerberus Chestguard", Enchantment.PROTECTION, 4);
        giveArmorPiece(player, Material.NETHERITE_LEGGINGS, "Cerberus Greaves", Enchantment.PROTECTION, 4);
        giveArmorPiece(player, Material.NETHERITE_BOOTS, "Cerberus Treads", Enchantment.PROTECTION, 4);
        player.sendMessage(ChatColor.GREEN + "You received the full " + ChatColor.DARK_RED + "Cerberus Armor" + ChatColor.GREEN + " set!");
    }

    private void giveArmorPiece(Player player, Material material, String name, Enchantment enchant, int level) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return;

        meta.setDisplayName(ChatColor.DARK_RED + "" + ChatColor.BOLD + name);
        meta.setLore(List.of(
                "",
                ChatColor.GRAY + "Armor of the three-headed guardian.",
                "",
                ChatColor.GOLD + "Legendary Armor"
        ));
        meta.addEnchant(enchant, level, true);
        meta.setUnbreakable(true);
        meta.addItemFlags(ItemFlag.HIDE_ENCHANTS, ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_UNBREAKABLE);

        item.setItemMeta(meta);
        player.getInventory().addItem(item);
    }
}
