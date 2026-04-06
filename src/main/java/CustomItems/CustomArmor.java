package CustomItems;

import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.EquipmentSlotGroup;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.LeatherArmorMeta;

public class CustomArmor {

    public static ItemStack createTestArmor() {
        ItemStack leatherChestplate = new ItemStack(Material.LEATHER_CHESTPLATE);
        LeatherArmorMeta meta = (LeatherArmorMeta) leatherChestplate.getItemMeta();

        if (meta != null) {
            // Set custom name and color
            meta.setDisplayName("§bTest Leather Chestplate");
            meta.setColor(Color.fromRGB(0x1E90FF)); // Dodger Blue color

            // Hide attributes to make it look more custom
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
            meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);

            // Add a custom attribute modifier for defense
            AttributeModifier defenseModifier = new AttributeModifier(
                    NamespacedKey.fromString("cerberus:generic_armor"),
                    100,
                    AttributeModifier.Operation.ADD_NUMBER,
                    EquipmentSlotGroup.CHEST
            );
            meta.addAttributeModifier(Attribute.ARMOR, defenseModifier);

            leatherChestplate.setItemMeta(meta);
        }

        return leatherChestplate;
    }
}
