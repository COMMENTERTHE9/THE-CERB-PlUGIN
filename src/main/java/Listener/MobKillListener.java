package Listener;

import GUIs.SkillGUI;
import Skills.SkillManager;
import Skills.UtilitySkill;
import CustomTags.ElementalTag;
import cerberus.world.cerb.CustomPlayer;
import cerberus.world.cerb.CerberusPlugin;
import cerberus.world.cerb.ConfigSkillMapper;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.LivingEntity;
import cerberus.world.cerb.ConfigSkillMapper;  // new
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.inventory.ItemStack;
import cerberus.world.cerb.CerberusPlugin;
import org.bukkit.NamespacedKey;


public class MobKillListener implements Listener {
    private final CerberusPlugin plugin;
    private final ConfigSkillMapper skillMapper;
    private final SkillManager skillManager;
    private final SkillGUI skillGUI;
    private final NamespacedKey elementalTagKey;

    public MobKillListener(CerberusPlugin plugin,
                           SkillManager skillManager,
                           SkillGUI skillGUI,
                           ConfigSkillMapper skillMapper) {
        this.plugin        = plugin;
        this.skillManager  = skillManager;
        this.skillGUI      = skillGUI;
        this.elementalTagKey = new NamespacedKey(plugin, "elemental_tag");
        this.skillMapper   = skillMapper;             // ← assignment, not declaration
    }

    // -----------------------------------------
    // Award dynamic XP on mob kill
    // -----------------------------------------
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onEntityDeath(EntityDeathEvent event) {
        LivingEntity mob = event.getEntity();
        Player player = mob.getKiller();
        if (player == null) return;

        double health = mob.getMaxHealth();
        double damage = mob.getLastDamage();
        boolean isRare = mob.getCustomName() != null;
        boolean isBoss = mob.getType() == EntityType.ENDER_DRAGON
                || mob.getType() == EntityType.WITHER;

        ItemStack weapon = player.getInventory().getItemInMainHand();

        // <<< UPDATED: use determineCombatSkill instead of the old generic determineSkill
        String skillName = determineCombatSkill(weapon, player);
        if (skillName.isEmpty()) return;

        double xpGained = skillManager.calculateDynamicXp(health, damage, isRare, isBoss);
        boolean leveledUp = skillManager.addXpAndCheckLevelUp(skillName, xpGained, player);

        player.sendMessage(ChatColor.GREEN + "XP gained: " + xpGained + " in " + skillName);

        if (leveledUp) {
            int level = skillManager.getSkillLevel(skillName);

            // <<< UPDATED: call our private createLevelUpMessage, not the SkillManager’s
            player.sendMessage(createLevelUpMessage(skillName, level));
        }

        // <<< NEW: refresh the GUI tab based on the skill type
        skillGUI.startRealTimeXPUpdate(player, determineSkillType(skillName));
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onPlayerMove(PlayerMoveEvent event) {
        if (event.getFrom().distanceSquared(event.getTo()) < 0.01) return;

        Player player = event.getPlayer();
        CustomPlayer custom = skillManager.getCustomPlayer(player.getUniqueId());
        if (custom == null) return;

        UtilitySkill nav = (UtilitySkill) skillManager.getSkill("Navigation");
        if (nav != null) nav.applyEffect(custom);
    }


    // ---- Helper methods ----

    private boolean determineIfBossMob(LivingEntity e) {
        switch (e.getType()) {
            case ENDER_DRAGON:
            case WITHER:
                return true;
            default:
                return false;
        }
    }

    // ----------------------------------------
    // Determine which combat/magic/utility skill
    // ----------------------------------------
    private String determineSkill(ItemStack weapon, Player player) {
        // Unarmed
        if (weapon == null || weapon.getType() == Material.AIR) {
            return "Martial Expertise";
        }
        String name = weapon.getType().name();
        // Bladed
        if (name.endsWith("_SWORD"))            return "Blade Mastery";
        // Axes/Hammers
        if (name.endsWith("_AXE") || name.endsWith("_HAMMER")) return "Weapon Mastery";
        // Ranged
        if (weapon.getType() == Material.BOW
                || weapon.getType() == Material.CROSSBOW) return "Ranged Precision";
        // Books = magic
        if (weapon.getType() == Material.ENCHANTED_BOOK)       return "Arcane Knowledge";
        // Wands/Staffs
        if (name.contains("WAND") || weapon.getType() == Material.STICK) return "Spell Weaving";
        // Dual-wield
        ItemStack off = player.getInventory().getItemInOffHand();
        if (off != null && off.getType() != Material.AIR)     return "Dual Wielding";
        // Armor presence
        if (player.getInventory().getHelmet() != null
                || player.getInventory().getChestplate() != null
                || player.getInventory().getLeggings() != null
                || player.getInventory().getBoots() != null)           return "Heavy Armor Training";
        // Permissions-based
        if (player.hasPermission("cerberus.spellweaving"))      return "Spell Weaving";
        if (player.hasPermission("cerberus.summoning"))         return "Summoning";
        if (player.hasPermission("cerberus.manaregeneration"))  return "Mana Regeneration";

        // Elemental tag
        String element = weapon.getItemMeta()
                .getPersistentDataContainer()
                .get(elementalTagKey, PersistentDataType.STRING);
        if (element != null) {
            ElementalTag tag = ElementalTag.fromString(element);
            if (tag != null) return tag.getSkillName();
        }

        return "";
    }

    private String determineCombatSkill(ItemStack weapon, Player player) {
        // <<< NEW: data‑driven lookup from skills.yml
        String materialName       = (weapon == null ? "AIR" : weapon.getType().name());
        String skillFromConfig    = skillMapper.getSkillForMaterial(materialName);
        if (!skillFromConfig.isEmpty()) {
            return skillFromConfig;
        }

        String type = weapon.getType().name();
        if (type.endsWith("_SWORD"))           return "Blade Mastery";
        if (type.endsWith("_AXE") || type.endsWith("_HAMMER")) return "Weapon Mastery";
        if (weapon.getType() == Material.BOW || weapon.getType() == Material.CROSSBOW) return "Ranged Precision";
        if (weapon.getType() == Material.ENCHANTED_BOOK)       return "Arcane Knowledge";
        if (type.contains("WAND") || weapon.getType() == Material.STICK) return "Spell Weaving";
        if (player.getInventory().getItemInOffHand() != null
                && player.getInventory().getItemInOffHand().getType() != Material.AIR) return "Dual Wielding";
        if (hasArmor(player))                return "Heavy Armor Training";
        if (player.hasPermission("cerberus.spellweaving"))     return "Spell Weaving";
        if (player.hasPermission("cerberus.summoning"))       return "Summoning";
        if (player.hasPermission("cerberus.manaregeneration")) return "Mana Regeneration";

        ElementalTag tag = getElementalTagFromWeapon(weapon);
        if (tag != null) return getElementalSkillName(tag);

        return "";
    }

    private boolean hasArmor(Player player) {
        return player.getInventory().getHelmet() != null
                || player.getInventory().getChestplate() != null
                || player.getInventory().getLeggings() != null
                || player.getInventory().getBoots() != null;
    }

    private ElementalTag getElementalTagFromWeapon(ItemStack weapon) {
        if (weapon == null || !weapon.hasItemMeta()) return null;
        String value = weapon.getItemMeta()
                .getPersistentDataContainer()
                .get(elementalTagKey, PersistentDataType.STRING);
        if (value == null) return null;
        switch (value.toUpperCase()) {
            case "FIRE":      return new ElementalTag(ElementalTag.ElementType.FIRE, 1.2, 0.1, 0.5);
            case "ICE":       return new ElementalTag(ElementalTag.ElementType.ICE, 1.1, 0.2, 0.4);
            case "LIGHTNING": return new ElementalTag(ElementalTag.ElementType.LIGHTNING, 1.3, 0.0, 0.6);
            case "ARCANE":    return new ElementalTag(ElementalTag.ElementType.ARCANE, 1.4, 0.0, 0.7);
            case "DARK":      return new ElementalTag(ElementalTag.ElementType.DARK, 1.5, 0.0, 0.8);
            case "EARTH":     return new ElementalTag(ElementalTag.ElementType.EARTH, 1.1, 0.3, 0.4);
            case "WIND":      return new ElementalTag(ElementalTag.ElementType.WIND, 1.2, 0.0, 0.5);
            case "BINDING":   return new ElementalTag(ElementalTag.ElementType.BINDING, 1.0, 0.0, 0.3);
            default:          return null;
        }
    }

    private String getElementalSkillName(ElementalTag tag) {
        switch (tag.getElementType()) {
            case FIRE:      return "Pyromancy";
            case ICE:       return "Cryomancy";
            case LIGHTNING: return "Electromancy";
            case ARCANE:    return "Arcane Knowledge";
            case DARK:      return "Dark Arts";
            case EARTH:     return "Geomancy";
            case WIND:      return "Aeromancy";
            default:        return "";
        }
    }

    private String determineSkillType(String skillName) {
        if (isCombatSkill(skillName)) return "combat";
        if (isMagicSkill(skillName))  return "magic";
        return "utility";
    }

    private boolean isCombatSkill(String name) {
        switch (name.toLowerCase()) {
            case "blade mastery": case "martial expertise":
            case "weapon mastery": case "ranged precision":
            case "heavy armor training": case "dual wielding":
            case "critical strike":
                return true;
            default:
                return false;
        }
    }

    private boolean isMagicSkill(String name) {
        switch (name.toLowerCase()) {
            case "arcane knowledge": case "elemental mastery":
            case "spell weaving": case "summoning":
            case "mana regeneration": case "dark arts":
                return true;
            default:
                return false;
        }
    }

    private String createLevelUpMessage(String skillName, int level) {
        StringBuilder msg = new StringBuilder();
        msg.append("§6§m----------------------------------------------\n")
                .append("§e§l| LEVEL UP! §b").append(skillName).append(" §e|\n")
                .append("§6§m----------------------------------------------\n")
                .append("§7| You have reached §a").append(level)
                .append(" §7in §b").append(skillName).append("! §7|\n")
                .append("§6§m----------------------------------------------\n")
                .append("§e| Keep training to unlock more power! |");
        return msg.toString();
    }
    }
