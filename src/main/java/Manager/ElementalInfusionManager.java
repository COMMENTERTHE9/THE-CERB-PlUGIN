package Manager;

import Skills.ElementalMasterySkill;
import CustomTags.ElementalTag;
import CustomTags.ElementalTag.ElementType;
import cerberus.world.cerb.CustomPlayer;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

public class ElementalInfusionManager {
    private final JavaPlugin plugin;

    public ElementalInfusionManager(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    // Apply an elemental infusion to an item
    public boolean applyElementalInfusion(ItemStack item, ElementalTag.ElementType elementalType, double damageMultiplier, double specialEffect) {
        if (item == null || item.getType().isAir()) {
            return false;
        }

        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            // Check if the item already has an infusion
            if (hasElementalInfusion(item)) {
                return false;
            }

            // Apply the infusion as a tag
            ElementalTag elementalTag = new ElementalTag(elementalType, damageMultiplier, 0.0, specialEffect);
            meta.getPersistentDataContainer().set(new NamespacedKey(plugin, "elemental_infusion"), PersistentDataType.STRING, elementalTag.toString());

            // Modify the display name to show the infusion
            meta.setDisplayName(meta.getDisplayName() + " " + elementalType.getColor() + "[" + elementalType.name() + " Infusion]");
            item.setItemMeta(meta);

            return true;
        }

        return false;
    }

    // Check if an item has an elemental infusion
    public boolean hasElementalInfusion(ItemStack item) {
        if (item == null || item.getType().isAir()) {
            return false;
        }

        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            return meta.getPersistentDataContainer().has(new NamespacedKey(plugin, "elemental_infusion"), PersistentDataType.STRING);
        }
        return false;
    }

    // Get the type of elemental infusion on an item
    public ElementalTag getElementalInfusion(ItemStack item) {
        if (hasElementalInfusion(item)) {
            ItemMeta meta = item.getItemMeta();
            if (meta != null) {
                String infusionData = meta.getPersistentDataContainer().get(new NamespacedKey(plugin, "elemental_infusion"), PersistentDataType.STRING);
                // Parse the infusionData back into an ElementalTag (simple implementation for now)
                return parseElementalTag(infusionData);
            }
        }
        return null;
    }

    // Parse elemental infusion data
    private ElementalTag parseElementalTag(String data) {
        // Extract the elemental type and values (parsing logic based on how you've saved the data)
        // Placeholder parsing implementation
        for (ElementType type : ElementType.values()) {
            if (data.contains(type.name())) {
                return new ElementalTag(type, 1.0, 0.0, 1.0); // Replace with actual parsed values
            }
        }
        return null;
    }

    // Behavior when an infused item is used in combat, factoring in ElementalMasterySkill
    public void handleElementalInfusionEffect(EntityDamageByEntityEvent event, CustomPlayer customPlayer) {
        if (event.getDamager() instanceof Player) {
            Player player = (Player) event.getDamager();
            ItemStack weapon = player.getInventory().getItemInMainHand();

            if (hasElementalInfusion(weapon)) {
                ElementalTag elementalTag = getElementalInfusion(weapon);

                if (elementalTag != null) {
                    // Get the player's elemental mastery level using the skill name
                    ElementalMasterySkill masterySkill = (ElementalMasterySkill) customPlayer.getSkill("Elemental Mastery");
                    double skillLevelMultiplier = masterySkill != null ? 1.0 + 0.07 * masterySkill.getLevel() : 1.0;

                    // Apply enhanced infusion effect
                    applyInfusionEffect(event, elementalTag, skillLevelMultiplier);
                }
            }
        }
    }

    // Apply the effects based on the infusion type and player skill level
    private void applyInfusionEffect(EntityDamageByEntityEvent event, ElementalTag elementalTag, double skillMultiplier) {
        Entity target = event.getEntity();
        ElementType elementalType = elementalTag.getElementType();

        switch (elementalType) {
            case FIRE:
                target.setFireTicks((int) (100 * skillMultiplier)); // Scale fire ticks with skill
                break;
            case ICE:
                // Apply ice/slowness effect (e.g., using PotionEffects)
                break;
            case LIGHTNING:
                target.getWorld().strikeLightning(target.getLocation());
                break;
            case DARK:
                // Apply dark damage or life steal, scaled with mastery level
                break;
            case ARCANE:
            case EARTH:
            case WIND:
            case BINDING:
                // Apply effects based on the element type
                break;
        }
    }

    // Remove elemental infusion from an item
    public boolean removeElementalInfusion(ItemStack item) {
        if (hasElementalInfusion(item)) {
            ItemMeta meta = item.getItemMeta();
            if (meta != null) {
                meta.getPersistentDataContainer().remove(new NamespacedKey(plugin, "elemental_infusion"));

                // Clean up the display name
                String displayName = meta.getDisplayName();
                if (displayName.contains(" Infusion]")) {
                    displayName = displayName.substring(0, displayName.lastIndexOf(" ["));
                }
                meta.setDisplayName(displayName);
                item.setItemMeta(meta);
                return true;
            }
        }
        return false;
    }
}
