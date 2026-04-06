package Listener;

import GUIs.SkillGUI;
import Skills.SkillManager;
import Skills.UtilitySkill;
import CustomTags.ElementalTag;
import Manager.MobContext;
import Manager.MobRegistry;
import cerberus.world.cerb.CustomPlayer;
import cerberus.world.cerb.CerberusPlugin;
import cerberus.world.cerb.ConfigSkillMapper;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;


public class MobKillListener implements Listener {
    private final CerberusPlugin plugin;
    private final ConfigSkillMapper skillMapper;
    private final SkillManager skillManager;
    private final SkillGUI skillGUI;
    private final MobRegistry mobRegistry;
    private final NamespacedKey elementalTagKey;

    public MobKillListener(CerberusPlugin plugin,
                           SkillManager skillManager,
                           SkillGUI skillGUI,
                           ConfigSkillMapper skillMapper,
                           MobRegistry mobRegistry) {
        this.plugin        = plugin;
        this.skillManager  = skillManager;
        this.skillGUI      = skillGUI;
        this.elementalTagKey = new NamespacedKey(plugin, "elemental_tag");
        this.skillMapper   = skillMapper;
        this.mobRegistry   = mobRegistry;
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

        // Resolve mob classification from the registry (config-driven)
        MobContext ctx = mobRegistry.resolve(mob);

        ItemStack weapon = player.getInventory().getItemInMainHand();

        // Config-driven skill routing (skills.yml)
        String skillName = resolveSkill(weapon, player);
        if (skillName.isEmpty()) return;

        // Spell focus kill XP toggle
        if (skillMapper.isMagicSkill(skillName) && !skillMapper.shouldAwardKillXpForSpellFocus()) {
            return; // magic kill XP suppressed — cast XP from SpellManager still fires
        }

        // Debug logging
        if (plugin.isCombatDebugEnabled(player.getUniqueId())) {
            String materialName = weapon == null ? "AIR" : weapon.getType().name();
            String debugInfo = skillMapper.resolveDebug(materialName);
            if (debugInfo.isEmpty()) debugInfo = skillName + " (fallback)";
            player.sendMessage(ChatColor.DARK_GRAY + "[SkillTrigger] Held=" + materialName + " → " + debugInfo);
        }

        double xpGained = skillManager.calculateDynamicXp(health, damage, ctx);
        skillManager.addXpAndCheckLevelUp(skillName, xpGained, player);
        player.sendMessage(ChatColor.GREEN + "XP gained: " + xpGained + " in " + skillName);
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


    // ── Skill resolution chain ────────────────────────────────────
    //
    // 1. Config lookup (skills.yml — covers all weapons + spell items)
    // 2. Elemental PDC tag (elemental-tagged weapons → elemental skills)
    // 3. Heavy armor fallback (melee only, heavy armor only)
    // 4. Empty (no XP)

    private String resolveSkill(ItemStack weapon, Player player) {
        String materialName = (weapon == null || weapon.getType() == Material.AIR)
                ? "AIR" : weapon.getType().name();

        // Step 1: Config-driven lookup (skills.yml)
        String fromConfig = skillMapper.resolve(materialName);
        if (!fromConfig.isEmpty()) return fromConfig;

        // Step 2: Elemental PDC tag on weapon
        ElementalTag tag = getElementalTagFromWeapon(weapon);
        if (tag != null) {
            String elemSkill = getElementalSkillName(tag);
            if (!elemSkill.isEmpty()) return elemSkill;
        }

        // Step 3: Heavy armor fallback — only for melee with heavy armor
        if (hasHeavyArmor(player)) {
            return "Heavy Armor Training";
        }

        // Step 4: No match
        return "";
    }

    /** Check if player is wearing iron, diamond, or netherite armor (heavy armor only). */
    private boolean hasHeavyArmor(Player player) {
        for (ItemStack piece : player.getInventory().getArmorContents()) {
            if (piece == null) continue;
            String name = piece.getType().name();
            if (name.startsWith("IRON_") || name.startsWith("DIAMOND_") || name.startsWith("NETHERITE_")) {
                return true;
            }
        }
        return false;
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
