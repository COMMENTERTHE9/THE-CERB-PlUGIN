package GUIs;

import Skills.SkillManager;
import cerberus.world.cerb.CerberusPlugin;
import cerberus.world.cerb.CustomPlayer;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.Arrays;
import java.util.Map;

public class SkillGUI {
    private final CerberusPlugin plugin;
    private final SkillManager skillManager;

    public SkillGUI(CerberusPlugin plugin, SkillManager skillManager) {
        this.plugin = plugin;
        this.skillManager = skillManager;
    }

    // Show the appropriate skill menu
    public void show(Player player, String skillType) {
        switch (skillType.toLowerCase()) {
            case "combat":
                showCombatSkillsMenu(player);
                break;
            case "magic":
                showMagicSkillsMenu(player);
                break;
            case "utility":
                showUtilitySkillsMenu(player);
                break;
            default:
                player.sendMessage(ChatColor.RED + "Invalid skill type.");
                break;
        }
    }

    // Show Combat Skills Menu
    public void showCombatSkillsMenu(Player player) {
        Inventory combatSkillsMenu = createCombatSkillsMenu(player);
        player.openInventory(combatSkillsMenu);
    }

    // Show Magic Skills Menu
    public void showMagicSkillsMenu(Player player) {
        Inventory magicSkillsMenu = createMagicSkillsMenu(player);
        player.openInventory(magicSkillsMenu);
    }

    // Show Utility Skills Menu
    public void showUtilitySkillsMenu(Player player) {
        Inventory utilitySkillsMenu = createUtilitySkillsMenu(player);
        player.openInventory(utilitySkillsMenu);
    }

    // Create Combat Skills Menu
    private Inventory createCombatSkillsMenu(Player player) {
        Inventory combatSkillsMenu = plugin.getServer().createInventory(null, 27, ChatColor.RED + "Combat Skills");

        CustomPlayer customPlayer = getCustomPlayer(player);
        if (customPlayer == null) return combatSkillsMenu;

        Map<String, Integer> skillLevels = customPlayer.getSkillLevels();
        Map<String, Integer> skillXP = customPlayer.getSkillXP();

        int[] combatCenteredSlots = {10, 11, 12, 13, 14, 15, 16};
        int slotIndex = 0;

        addSkillItem(combatSkillsMenu, Material.IRON_SWORD, "Blade Mastery", "Increases proficiency and damage with bladed weapons.",
                skillLevels.getOrDefault("Blade Mastery", 0),
                skillXP.getOrDefault("Blade Mastery", 0),
                skillManager.calculateRequiredXpForNextLevel(skillLevels.getOrDefault("Blade Mastery", 0)),
                combatCenteredSlots[slotIndex++]);

        addSkillItem(combatSkillsMenu, Material.LEATHER_CHESTPLATE, "Martial Expertise", "Boosts skill in hand-to-hand combat.",
                skillLevels.getOrDefault("Martial Expertise", 0),
                skillXP.getOrDefault("Martial Expertise", 0),
                skillManager.calculateRequiredXpForNextLevel(skillLevels.getOrDefault("Martial Expertise", 0)),
                combatCenteredSlots[slotIndex++]);

        addSkillItem(combatSkillsMenu, Material.DIAMOND_AXE, "Weapon Mastery", "Enhances damage with hammers, axes, and polearms.",
                skillLevels.getOrDefault("Weapon Mastery", 0),
                skillXP.getOrDefault("Weapon Mastery", 0),
                skillManager.calculateRequiredXpForNextLevel(skillLevels.getOrDefault("Weapon Mastery", 0)),
                combatCenteredSlots[slotIndex++]);

        addSkillItem(combatSkillsMenu, Material.BOW, "Ranged Precision", "Increases accuracy and damage with ranged weapons.",
                skillLevels.getOrDefault("Ranged Precision", 0),
                skillXP.getOrDefault("Ranged Precision", 0),
                skillManager.calculateRequiredXpForNextLevel(skillLevels.getOrDefault("Ranged Precision", 0)),
                combatCenteredSlots[slotIndex++]);

        addSkillItem(combatSkillsMenu, Material.IRON_CHESTPLATE, "Heavy Armor Training", "Increases defense with heavy armor.",
                skillLevels.getOrDefault("Heavy Armor Training", 0),
                skillXP.getOrDefault("Heavy Armor Training", 0),
                skillManager.calculateRequiredXpForNextLevel(skillLevels.getOrDefault("Heavy Armor Training", 0)),
                combatCenteredSlots[slotIndex++]);

        addSkillItem(combatSkillsMenu, Material.GOLDEN_SWORD, "Dual Wielding", "Enhances attack speed when dual wielding weapons.",
                skillLevels.getOrDefault("Dual Wielding", 0),
                skillXP.getOrDefault("Dual Wielding", 0),
                skillManager.calculateRequiredXpForNextLevel(skillLevels.getOrDefault("Dual Wielding", 0)),
                combatCenteredSlots[slotIndex++]);

        addSkillItem(combatSkillsMenu, Material.NETHERITE_SWORD, "Critical Strike", "Increases the chance of landing critical hits.",
                skillLevels.getOrDefault("Critical Strike", 0),
                skillXP.getOrDefault("Critical Strike", 0),
                skillManager.calculateRequiredXpForNextLevel(skillLevels.getOrDefault("Critical Strike", 0)),
                combatCenteredSlots[slotIndex++]);

        combatSkillsMenu.setItem(18, createBackButton());
        return combatSkillsMenu;
    }

    // Create Magic Skills Menu
    private Inventory createMagicSkillsMenu(Player player) {
        Inventory magicSkillsMenu = plugin.getServer().createInventory(null, 27, ChatColor.BLUE + "Magic Skills");

        CustomPlayer customPlayer = getCustomPlayer(player);
        if (customPlayer == null) return magicSkillsMenu;

        Map<String, Integer> skillLevels = customPlayer.getSkillLevels();
        Map<String, Integer> skillXP = customPlayer.getSkillXP();

        int[] magicCenteredSlots = {10, 11, 12, 13, 14, 15, 16};
        int slotIndex = 0;

        addSkillItem(magicSkillsMenu, Material.BOOK, "Intelligence", "Increases mana pool and spellcasting speed.",
                skillLevels.getOrDefault("Intelligence", 0),
                skillXP.getOrDefault("Intelligence", 0),
                skillManager.calculateRequiredXpForNextLevel(skillLevels.getOrDefault("Intelligence", 0)),
                magicCenteredSlots[slotIndex++]);

        addSkillItem(magicSkillsMenu, Material.ENCHANTED_BOOK, "Arcane Knowledge", "Boosts spell potency and range.",
                skillLevels.getOrDefault("Arcane Knowledge", 0),
                skillXP.getOrDefault("Arcane Knowledge", 0),
                skillManager.calculateRequiredXpForNextLevel(skillLevels.getOrDefault("Arcane Knowledge", 0)),
                magicCenteredSlots[slotIndex++]);

        addSkillItem(magicSkillsMenu, Material.BLAZE_POWDER, "Elemental Mastery", "Increases control over elemental magic.",
                skillLevels.getOrDefault("Elemental Mastery", 0),
                skillXP.getOrDefault("Elemental Mastery", 0),
                skillManager.calculateRequiredXpForNextLevel(skillLevels.getOrDefault("Elemental Mastery", 0)),
                magicCenteredSlots[slotIndex++]);

        addSkillItem(magicSkillsMenu, Material.SPAWNER, "Summoning", "Enhances the strength and duration of summoned creatures.",
                skillLevels.getOrDefault("Summoning", 0),
                skillXP.getOrDefault("Summoning", 0),
                skillManager.calculateRequiredXpForNextLevel(skillLevels.getOrDefault("Summoning", 0)),
                magicCenteredSlots[slotIndex++]);

        addSkillItem(magicSkillsMenu, Material.PAPER, "Spell Weaving", "Increases effectiveness when combining spells.",
                skillLevels.getOrDefault("Spell Weaving", 0),
                skillXP.getOrDefault("Spell Weaving", 0),
                skillManager.calculateRequiredXpForNextLevel(skillLevels.getOrDefault("Spell Weaving", 0)),
                magicCenteredSlots[slotIndex++]);

        addSkillItem(magicSkillsMenu, Material.POTION, "Mana Regeneration", "Increases the rate of mana regeneration.",
                skillLevels.getOrDefault("Mana Regeneration", 0),
                skillXP.getOrDefault("Mana Regeneration", 0),
                skillManager.calculateRequiredXpForNextLevel(skillLevels.getOrDefault("Mana Regeneration", 0)),
                magicCenteredSlots[slotIndex++]);

        addSkillItem(magicSkillsMenu, Material.SHIELD, "Defensive Magic", "Increases the strength of magical shields.",
                skillLevels.getOrDefault("Defensive Magic", 0),
                skillXP.getOrDefault("Defensive Magic", 0),
                skillManager.calculateRequiredXpForNextLevel(skillLevels.getOrDefault("Defensive Magic", 0)),
                magicCenteredSlots[slotIndex++]);

        magicSkillsMenu.setItem(18, createBackButton());
        return magicSkillsMenu;
    }

    // Create Utility Skills Menu
    private Inventory createUtilitySkillsMenu(Player player) {
        Inventory utilitySkillsMenu = plugin.getServer().createInventory(null, 54, ChatColor.GREEN + "Utility Skills");

        CustomPlayer customPlayer = getCustomPlayer(player);
        if (customPlayer == null) return utilitySkillsMenu;

        Map<String, Integer> skillLevels = customPlayer.getSkillLevels();
        Map<String, Integer> skillXP = customPlayer.getSkillXP();

        int[] utilityCenteredSlots = {
                10, 11, 12, 13, 14, 15, 16,
                19, 20, 21, 22, 23, 24, 25,
                28, 29, 30, 31, 32, 33, 34,
                37, 38, 39, 40, 41, 42
        };
        int slotIndex = 0;

        addSkillItem(utilitySkillsMenu, Material.IRON_PICKAXE, "Mining", "Increases mining efficiency.",
                skillLevels.getOrDefault("Mining", 0),
                skillXP.getOrDefault("Mining", 0),
                skillManager.calculateRequiredXpForNextLevel(skillLevels.getOrDefault("Mining", 0)),
                utilityCenteredSlots[slotIndex++]);

        addSkillItem(utilitySkillsMenu, Material.WHEAT, "Farming", "Enhances crop yield.",
                skillLevels.getOrDefault("Farming", 0),
                skillXP.getOrDefault("Farming", 0),
                skillManager.calculateRequiredXpForNextLevel(skillLevels.getOrDefault("Farming", 0)),
                utilityCenteredSlots[slotIndex++]);

        addSkillItem(utilitySkillsMenu, Material.IRON_AXE, "Woodcutting", "Increases woodcutting efficiency.",
                skillLevels.getOrDefault("Woodcutting", 0),
                skillXP.getOrDefault("Woodcutting", 0),
                skillManager.calculateRequiredXpForNextLevel(skillLevels.getOrDefault("Woodcutting", 0)),
                utilityCenteredSlots[slotIndex++]);

        addSkillItem(utilitySkillsMenu, Material.FISHING_ROD, "Fishing", "Increases chances of catching rare fish.",
                skillLevels.getOrDefault("Fishing", 0),
                skillXP.getOrDefault("Fishing", 0),
                skillManager.calculateRequiredXpForNextLevel(skillLevels.getOrDefault("Fishing", 0)),
                utilityCenteredSlots[slotIndex++]);

        addSkillItem(utilitySkillsMenu, Material.CRAFTING_TABLE, "Crafting", "Improves crafting efficiency.",
                skillLevels.getOrDefault("Crafting", 0),
                skillXP.getOrDefault("Crafting", 0),
                skillManager.calculateRequiredXpForNextLevel(skillLevels.getOrDefault("Crafting", 0)),
                utilityCenteredSlots[slotIndex++]);

        addSkillItem(utilitySkillsMenu, Material.ANVIL, "Smithing", "Enhances forging abilities.",
                skillLevels.getOrDefault("Smithing", 0),
                skillXP.getOrDefault("Smithing", 0),
                skillManager.calculateRequiredXpForNextLevel(skillLevels.getOrDefault("Smithing", 0)),
                utilityCenteredSlots[slotIndex++]);

        addSkillItem(utilitySkillsMenu, Material.BREWING_STAND, "Alchemy", "Increases potion-making speed and potency.",
                skillLevels.getOrDefault("Alchemy", 0),
                skillXP.getOrDefault("Alchemy", 0),
                skillManager.calculateRequiredXpForNextLevel(skillLevels.getOrDefault("Alchemy", 0)),
                utilityCenteredSlots[slotIndex++]);

        addSkillItem(utilitySkillsMenu, Material.ENCHANTING_TABLE, "Enchanting", "Enhances enchantment abilities.",
                skillLevels.getOrDefault("Enchanting", 0),
                skillXP.getOrDefault("Enchanting", 0),
                skillManager.calculateRequiredXpForNextLevel(skillLevels.getOrDefault("Enchanting", 0)),
                utilityCenteredSlots[slotIndex++]);

        addSkillItem(utilitySkillsMenu, Material.FLOWER_POT, "Herbalism", "Increases yield when gathering herbs.",
                skillLevels.getOrDefault("Herbalism", 0),
                skillXP.getOrDefault("Herbalism", 0),
                skillManager.calculateRequiredXpForNextLevel(skillLevels.getOrDefault("Herbalism", 0)),
                utilityCenteredSlots[slotIndex++]);

        addSkillItem(utilitySkillsMenu, Material.COOKED_BEEF, "Cooking", "Improves cooking efficiency.",
                skillLevels.getOrDefault("Cooking", 0),
                skillXP.getOrDefault("Cooking", 0),
                skillManager.calculateRequiredXpForNextLevel(skillLevels.getOrDefault("Cooking", 0)),
                utilityCenteredSlots[slotIndex++]);

        addSkillItem(utilitySkillsMenu, Material.GOLDEN_APPLE, "First Aid", "Increases healing effectiveness.",
                skillLevels.getOrDefault("First Aid", 0),
                skillXP.getOrDefault("First Aid", 0),
                skillManager.calculateRequiredXpForNextLevel(skillLevels.getOrDefault("First Aid", 0)),
                utilityCenteredSlots[slotIndex++]);

        addSkillItem(utilitySkillsMenu, Material.ENDER_PEARL, "Stealth", "Enhances sneaking abilities.",
                skillLevels.getOrDefault("Stealth", 0),
                skillXP.getOrDefault("Stealth", 0),
                skillManager.calculateRequiredXpForNextLevel(skillLevels.getOrDefault("Stealth", 0)),
                utilityCenteredSlots[slotIndex++]);

        addSkillItem(utilitySkillsMenu, Material.TRIPWIRE_HOOK, "Trap Mastery", "Improves trap placement and detection.",
                skillLevels.getOrDefault("Trap Mastery", 0),
                skillXP.getOrDefault("Trap Mastery", 0),
                skillManager.calculateRequiredXpForNextLevel(skillLevels.getOrDefault("Trap Mastery", 0)),
                utilityCenteredSlots[slotIndex++]);

        addSkillItem(utilitySkillsMenu, Material.CHEST, "Scavenging", "Increases the chance of finding rare items.",
                skillLevels.getOrDefault("Scavenging", 0),
                skillXP.getOrDefault("Scavenging", 0),
                skillManager.calculateRequiredXpForNextLevel(skillLevels.getOrDefault("Scavenging", 0)),
                utilityCenteredSlots[slotIndex++]);

        addSkillItem(utilitySkillsMenu, Material.ANVIL, "Repairing", "Improves weapon and armor repair abilities.",
                skillLevels.getOrDefault("Repairing", 0),
                skillXP.getOrDefault("Repairing", 0),
                skillManager.calculateRequiredXpForNextLevel(skillLevels.getOrDefault("Repairing", 0)),
                utilityCenteredSlots[slotIndex++]);

        addSkillItem(utilitySkillsMenu, Material.EMERALD, "Trading", "Improves trading skills.",
                skillLevels.getOrDefault("Trading", 0),
                skillXP.getOrDefault("Trading", 0),
                skillManager.calculateRequiredXpForNextLevel(skillLevels.getOrDefault("Trading", 0)),
                utilityCenteredSlots[slotIndex++]);

        addSkillItem(utilitySkillsMenu, Material.MAP, "Navigation", "Enhances travel efficiency.",
                skillLevels.getOrDefault("Navigation", 0),
                skillXP.getOrDefault("Navigation", 0),
                skillManager.calculateRequiredXpForNextLevel(skillLevels.getOrDefault("Navigation", 0)),
                utilityCenteredSlots[slotIndex++]);

        addSkillItem(utilitySkillsMenu, Material.LEAD, "Animal Taming", "Increases animal taming success rate.",
                skillLevels.getOrDefault("Animal Taming", 0),
                skillXP.getOrDefault("Animal Taming", 0),
                skillManager.calculateRequiredXpForNextLevel(skillLevels.getOrDefault("Animal Taming", 0)),
                utilityCenteredSlots[slotIndex++]);

        addSkillItem(utilitySkillsMenu, Material.SADDLE, "Riding", "Improves mount control and endurance.",
                skillLevels.getOrDefault("Riding", 0),
                skillXP.getOrDefault("Riding", 0),
                skillManager.calculateRequiredXpForNextLevel(skillLevels.getOrDefault("Riding", 0)),
                utilityCenteredSlots[slotIndex++]);

        addSkillItem(utilitySkillsMenu, Material.TRIPWIRE_HOOK, "Lockpicking", "Improves lockpicking success.",
                skillLevels.getOrDefault("Lockpicking", 0),
                skillXP.getOrDefault("Lockpicking", 0),
                skillManager.calculateRequiredXpForNextLevel(skillLevels.getOrDefault("Lockpicking", 0)),
                utilityCenteredSlots[slotIndex++]);

        addSkillItem(utilitySkillsMenu, Material.CAMPFIRE, "Survival", "Increases survival in harsh conditions.",
                skillLevels.getOrDefault("Survival", 0),
                skillXP.getOrDefault("Survival", 0),
                skillManager.calculateRequiredXpForNextLevel(skillLevels.getOrDefault("Survival", 0)),
                utilityCenteredSlots[slotIndex++]);

        // After adding all utility skill items
        utilitySkillsMenu.setItem(49, createBackButton());

        return utilitySkillsMenu;
    }
    // Add skill item with progress
    private void addSkillItem(Inventory menu, Material material, String skillName, String description, int level, int currentXP, int xpForNextLevel, int slot) {
        if (slot < 0 || slot >= menu.getSize()) return;

        double progress = (xpForNextLevel > 0) ? (double) currentXP / xpForNextLevel : 1.0;
        int progressBars = (int) (progress * 20);

        StringBuilder progressBar = new StringBuilder();
        progressBar.append(ChatColor.DARK_GREEN);
        for (int i = 0; i < progressBars; i++) {
            progressBar.append("|");
        }
        progressBar.append(ChatColor.DARK_GRAY);
        for (int i = progressBars; i < 20; i++) {
            progressBar.append("|");
        }

        String name = ChatColor.RED + skillName + " (Level " + level + ")";
        String[] lore = {
                description,
                "Current Level: " + level,
                "XP: " + currentXP + " / " + xpForNextLevel,
                progressBar.toString(),
                "Next Level: " + xpForNextLevel + " XP"
        };

        ItemStack item = createGuiItem(material, name, lore);
        menu.setItem(slot, item);
    }

    // Utility to create a GUI item
    private ItemStack createGuiItem(Material material, String name, String... lore) {
        ItemStack item = new ItemStack(material, 1);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(name);
        meta.setLore(Arrays.asList(lore));
        item.setItemMeta(meta);
        return item;
    }

    // Start real-time XP updates
    public void startRealTimeXPUpdate(Player player, String skillType) {
        new BukkitRunnable() {
            @Override
            public void run() {
                if (!player.isOnline()) {
                    this.cancel();
                    return;
                }
                updatePlayerSkills(player, skillType);
            }
        }.runTaskTimer(plugin, 0, 20);
    }

    public void updatePlayerSkills(Player player, String skillType) {
        if (!player.isOnline()) return;
        Inventory openInv = player.getOpenInventory().getTopInventory();
        if (openInv == null) return;

        String expectedTitle;
        switch (skillType.toLowerCase()) {
            case "combat":
                expectedTitle = ChatColor.RED + "Combat Skills";
                break;
            case "magic":
                expectedTitle = ChatColor.BLUE + "Magic Skills";
                break;
            case "utility":
                expectedTitle = ChatColor.GREEN + "Utility Skills";
                break;
            default:
                return; // Invalid skill type
        }

        // Check if the player is still looking at the correct menu
        if (!player.getOpenInventory().getTitle().equals(expectedTitle)) {
            return;
        }

        // Get updated skill data
        CustomPlayer customPlayer = getCustomPlayer(player);
        if (customPlayer == null) return;

        Map<String, Integer> skillLevels = customPlayer.getSkillLevels();
        Map<String, Integer> skillXP = customPlayer.getSkillXP();

        if (skillType.equalsIgnoreCase("combat")) {
            int[] combatCenteredSlots = {10, 11, 12, 13, 14, 15, 16};
            int slotIndex = 0;

            addSkillItem(openInv, Material.IRON_SWORD, "Blade Mastery", "Increases proficiency and damage with bladed weapons.",
                    skillLevels.getOrDefault("Blade Mastery", 0),
                    skillXP.getOrDefault("Blade Mastery", 0),
                    skillManager.calculateRequiredXpForNextLevel(skillLevels.getOrDefault("Blade Mastery", 0)),
                    combatCenteredSlots[slotIndex++]);

            addSkillItem(openInv, Material.LEATHER_CHESTPLATE, "Martial Expertise", "Boosts skill in hand-to-hand combat.",
                    skillLevels.getOrDefault("Martial Expertise", 0),
                    skillXP.getOrDefault("Martial Expertise", 0),
                    skillManager.calculateRequiredXpForNextLevel(skillLevels.getOrDefault("Martial Expertise", 0)),
                    combatCenteredSlots[slotIndex++]);

            addSkillItem(openInv, Material.DIAMOND_AXE, "Weapon Mastery", "Enhances damage with hammers, axes, and polearms.",
                    skillLevels.getOrDefault("Weapon Mastery", 0),
                    skillXP.getOrDefault("Weapon Mastery", 0),
                    skillManager.calculateRequiredXpForNextLevel(skillLevels.getOrDefault("Weapon Mastery", 0)),
                    combatCenteredSlots[slotIndex++]);

            addSkillItem(openInv, Material.BOW, "Ranged Precision", "Increases accuracy and damage with ranged weapons.",
                    skillLevels.getOrDefault("Ranged Precision", 0),
                    skillXP.getOrDefault("Ranged Precision", 0),
                    skillManager.calculateRequiredXpForNextLevel(skillLevels.getOrDefault("Ranged Precision", 0)),
                    combatCenteredSlots[slotIndex++]);

            addSkillItem(openInv, Material.IRON_CHESTPLATE, "Heavy Armor Training", "Increases defense with heavy armor.",
                    skillLevels.getOrDefault("Heavy Armor Training", 0),
                    skillXP.getOrDefault("Heavy Armor Training", 0),
                    skillManager.calculateRequiredXpForNextLevel(skillLevels.getOrDefault("Heavy Armor Training", 0)),
                    combatCenteredSlots[slotIndex++]);

            addSkillItem(openInv, Material.GOLDEN_SWORD, "Dual Wielding", "Enhances attack speed when dual wielding weapons.",
                    skillLevels.getOrDefault("Dual Wielding", 0),
                    skillXP.getOrDefault("Dual Wielding", 0),
                    skillManager.calculateRequiredXpForNextLevel(skillLevels.getOrDefault("Dual Wielding", 0)),
                    combatCenteredSlots[slotIndex++]);

            addSkillItem(openInv, Material.NETHERITE_SWORD, "Critical Strike", "Increases the chance of landing critical hits.",
                    skillLevels.getOrDefault("Critical Strike", 0),
                    skillXP.getOrDefault("Critical Strike", 0),
                    skillManager.calculateRequiredXpForNextLevel(skillLevels.getOrDefault("Critical Strike", 0)),
                    combatCenteredSlots[slotIndex++]);

        } else if (skillType.equalsIgnoreCase("magic")) {
            int[] magicCenteredSlots = {10, 11, 12, 13, 14, 15, 16};
            int slotIndex = 0;

            addSkillItem(openInv, Material.BOOK, "Intelligence", "Increases mana pool and spellcasting speed.",
                    skillLevels.getOrDefault("Intelligence", 0),
                    skillXP.getOrDefault("Intelligence", 0),
                    skillManager.calculateRequiredXpForNextLevel(skillLevels.getOrDefault("Intelligence", 0)),
                    magicCenteredSlots[slotIndex++]);

            addSkillItem(openInv, Material.ENCHANTED_BOOK, "Arcane Knowledge", "Boosts spell potency and range.",
                    skillLevels.getOrDefault("Arcane Knowledge", 0),
                    skillXP.getOrDefault("Arcane Knowledge", 0),
                    skillManager.calculateRequiredXpForNextLevel(skillLevels.getOrDefault("Arcane Knowledge", 0)),
                    magicCenteredSlots[slotIndex++]);

            addSkillItem(openInv, Material.BLAZE_POWDER, "Elemental Mastery", "Increases control over elemental magic.",
                    skillLevels.getOrDefault("Elemental Mastery", 0),
                    skillXP.getOrDefault("Elemental Mastery", 0),
                    skillManager.calculateRequiredXpForNextLevel(skillLevels.getOrDefault("Elemental Mastery", 0)),
                    magicCenteredSlots[slotIndex++]);

            addSkillItem(openInv, Material.SPAWNER, "Summoning", "Enhances the strength and duration of summoned creatures.",
                    skillLevels.getOrDefault("Summoning", 0),
                    skillXP.getOrDefault("Summoning", 0),
                    skillManager.calculateRequiredXpForNextLevel(skillLevels.getOrDefault("Summoning", 0)),
                    magicCenteredSlots[slotIndex++]);

            addSkillItem(openInv, Material.PAPER, "Spell Weaving", "Increases effectiveness when combining spells.",
                    skillLevels.getOrDefault("Spell Weaving", 0),
                    skillXP.getOrDefault("Spell Weaving", 0),
                    skillManager.calculateRequiredXpForNextLevel(skillLevels.getOrDefault("Spell Weaving", 0)),
                    magicCenteredSlots[slotIndex++]);

            addSkillItem(openInv, Material.POTION, "Mana Regeneration", "Increases the rate of mana regeneration.",
                    skillLevels.getOrDefault("Mana Regeneration", 0),
                    skillXP.getOrDefault("Mana Regeneration", 0),
                    skillManager.calculateRequiredXpForNextLevel(skillLevels.getOrDefault("Mana Regeneration", 0)),
                    magicCenteredSlots[slotIndex++]);

            addSkillItem(openInv, Material.SHIELD, "Defensive Magic", "Increases the strength of magical shields.",
                    skillLevels.getOrDefault("Defensive Magic", 0),
                    skillXP.getOrDefault("Defensive Magic", 0),
                    skillManager.calculateRequiredXpForNextLevel(skillLevels.getOrDefault("Defensive Magic", 0)),
                    magicCenteredSlots[slotIndex++]);

        } else if (skillType.equalsIgnoreCase("utility")) {
            int[] utilityCenteredSlots = {
                    10, 11, 12, 13, 14, 15, 16,
                    19, 20, 21, 22, 23, 24, 25,
                    28, 29, 30, 31, 32, 33, 34,
                    37, 38, 39, 40, 41, 42
            };
            int slotIndex = 0;

            // Update all utility skills items just like in createUtilitySkillsMenu
            addSkillItem(openInv, Material.IRON_PICKAXE, "Mining", "Increases mining efficiency.",
                    skillLevels.getOrDefault("Mining", 0),
                    skillXP.getOrDefault("Mining", 0),
                    skillManager.calculateRequiredXpForNextLevel(skillLevels.getOrDefault("Mining", 0)),
                    utilityCenteredSlots[slotIndex++]);

            addSkillItem(openInv, Material.WHEAT, "Farming", "Enhances crop yield.",
                    skillLevels.getOrDefault("Farming", 0),
                    skillXP.getOrDefault("Farming", 0),
                    skillManager.calculateRequiredXpForNextLevel(skillLevels.getOrDefault("Farming", 0)),
                    utilityCenteredSlots[slotIndex++]);

            addSkillItem(openInv, Material.IRON_AXE, "Woodcutting", "Increases woodcutting efficiency.",
                    skillLevels.getOrDefault("Woodcutting", 0),
                    skillXP.getOrDefault("Woodcutting", 0),
                    skillManager.calculateRequiredXpForNextLevel(skillLevels.getOrDefault("Woodcutting", 0)),
                    utilityCenteredSlots[slotIndex++]);

            addSkillItem(openInv, Material.FISHING_ROD, "Fishing", "Increases chances of catching rare fish.",
                    skillLevels.getOrDefault("Fishing", 0),
                    skillXP.getOrDefault("Fishing", 0),
                    skillManager.calculateRequiredXpForNextLevel(skillLevels.getOrDefault("Fishing", 0)),
                    utilityCenteredSlots[slotIndex++]);

            addSkillItem(openInv, Material.CRAFTING_TABLE, "Crafting", "Improves crafting efficiency.",
                    skillLevels.getOrDefault("Crafting", 0),
                    skillXP.getOrDefault("Crafting", 0),
                    skillManager.calculateRequiredXpForNextLevel(skillLevels.getOrDefault("Crafting", 0)),
                    utilityCenteredSlots[slotIndex++]);

            addSkillItem(openInv, Material.ANVIL, "Smithing", "Enhances forging abilities.",
                    skillLevels.getOrDefault("Smithing", 0),
                    skillXP.getOrDefault("Smithing", 0),
                    skillManager.calculateRequiredXpForNextLevel(skillLevels.getOrDefault("Smithing", 0)),
                    utilityCenteredSlots[slotIndex++]);

            addSkillItem(openInv, Material.BREWING_STAND, "Alchemy", "Increases potion-making speed and potency.",
                    skillLevels.getOrDefault("Alchemy", 0),
                    skillXP.getOrDefault("Alchemy", 0),
                    skillManager.calculateRequiredXpForNextLevel(skillLevels.getOrDefault("Alchemy", 0)),
                    utilityCenteredSlots[slotIndex++]);

            addSkillItem(openInv, Material.ENCHANTING_TABLE, "Enchanting", "Enhances enchantment abilities.",
                    skillLevels.getOrDefault("Enchanting", 0),
                    skillXP.getOrDefault("Enchanting", 0),
                    skillManager.calculateRequiredXpForNextLevel(skillLevels.getOrDefault("Enchanting", 0)),
                    utilityCenteredSlots[slotIndex++]);

            addSkillItem(openInv, Material.FLOWER_POT, "Herbalism", "Increases yield when gathering herbs.",
                    skillLevels.getOrDefault("Herbalism", 0),
                    skillXP.getOrDefault("Herbalism", 0),
                    skillManager.calculateRequiredXpForNextLevel(skillLevels.getOrDefault("Herbalism", 0)),
                    utilityCenteredSlots[slotIndex++]);

            addSkillItem(openInv, Material.COOKED_BEEF, "Cooking", "Improves cooking efficiency.",
                    skillLevels.getOrDefault("Cooking", 0),
                    skillXP.getOrDefault("Cooking", 0),
                    skillManager.calculateRequiredXpForNextLevel(skillLevels.getOrDefault("Cooking", 0)),
                    utilityCenteredSlots[slotIndex++]);

            addSkillItem(openInv, Material.GOLDEN_APPLE, "First Aid", "Increases healing effectiveness.",
                    skillLevels.getOrDefault("First Aid", 0),
                    skillXP.getOrDefault("First Aid", 0),
                    skillManager.calculateRequiredXpForNextLevel(skillLevels.getOrDefault("First Aid", 0)),
                    utilityCenteredSlots[slotIndex++]);

            addSkillItem(openInv, Material.ENDER_PEARL, "Stealth", "Enhances sneaking abilities.",
                    skillLevels.getOrDefault("Stealth", 0),
                    skillXP.getOrDefault("Stealth", 0),
                    skillManager.calculateRequiredXpForNextLevel(skillLevels.getOrDefault("Stealth", 0)),
                    utilityCenteredSlots[slotIndex++]);

            addSkillItem(openInv, Material.TRIPWIRE_HOOK, "Trap Mastery", "Improves trap placement and detection.",
                    skillLevels.getOrDefault("Trap Mastery", 0),
                    skillXP.getOrDefault("Trap Mastery", 0),
                    skillManager.calculateRequiredXpForNextLevel(skillLevels.getOrDefault("Trap Mastery", 0)),
                    utilityCenteredSlots[slotIndex++]);

            addSkillItem(openInv, Material.CHEST, "Scavenging", "Increases the chance of finding rare items.",
                    skillLevels.getOrDefault("Scavenging", 0),
                    skillXP.getOrDefault("Scavenging", 0),
                    skillManager.calculateRequiredXpForNextLevel(skillLevels.getOrDefault("Scavenging", 0)),
                    utilityCenteredSlots[slotIndex++]);

            addSkillItem(openInv, Material.ANVIL, "Repairing", "Improves weapon and armor repair abilities.",
                    skillLevels.getOrDefault("Repairing", 0),
                    skillXP.getOrDefault("Repairing", 0),
                    skillManager.calculateRequiredXpForNextLevel(skillLevels.getOrDefault("Repairing", 0)),
                    utilityCenteredSlots[slotIndex++]);

            addSkillItem(openInv, Material.EMERALD, "Trading", "Improves trading skills.",
                    skillLevels.getOrDefault("Trading", 0),
                    skillXP.getOrDefault("Trading", 0),
                    skillManager.calculateRequiredXpForNextLevel(skillLevels.getOrDefault("Trading", 0)),
                    utilityCenteredSlots[slotIndex++]);

            addSkillItem(openInv, Material.MAP, "Navigation", "Enhances travel efficiency.",
                    skillLevels.getOrDefault("Navigation", 0),
                    skillXP.getOrDefault("Navigation", 0),
                    skillManager.calculateRequiredXpForNextLevel(skillLevels.getOrDefault("Navigation", 0)),
                    utilityCenteredSlots[slotIndex++]);

            addSkillItem(openInv, Material.LEAD, "Animal Taming", "Increases animal taming success rate.",
                    skillLevels.getOrDefault("Animal Taming", 0),
                    skillXP.getOrDefault("Animal Taming", 0),
                    skillManager.calculateRequiredXpForNextLevel(skillLevels.getOrDefault("Animal Taming", 0)),
                    utilityCenteredSlots[slotIndex++]);

            addSkillItem(openInv, Material.SADDLE, "Riding", "Improves mount control and endurance.",
                    skillLevels.getOrDefault("Riding", 0),
                    skillXP.getOrDefault("Riding", 0),
                    skillManager.calculateRequiredXpForNextLevel(skillLevels.getOrDefault("Riding", 0)),
                    utilityCenteredSlots[slotIndex++]);

            addSkillItem(openInv, Material.TRIPWIRE_HOOK, "Lockpicking", "Improves lockpicking success.",
                    skillLevels.getOrDefault("Lockpicking", 0),
                    skillXP.getOrDefault("Lockpicking", 0),
                    skillManager.calculateRequiredXpForNextLevel(skillLevels.getOrDefault("Lockpicking", 0)),
                    utilityCenteredSlots[slotIndex++]);

            addSkillItem(openInv, Material.CAMPFIRE, "Survival", "Increases survival in harsh conditions.",
                    skillLevels.getOrDefault("Survival", 0),
                    skillXP.getOrDefault("Survival", 0),
                    skillManager.calculateRequiredXpForNextLevel(skillLevels.getOrDefault("Survival", 0)),
                    utilityCenteredSlots[slotIndex++]);
        }
    }

    // Go back button
    private ItemStack createBackButton() {
        ItemStack backButton = new ItemStack(Material.ARROW);
        ItemMeta meta = backButton.getItemMeta();
        meta.setDisplayName(ChatColor.YELLOW + "Back");
        meta.setLore(Arrays.asList(ChatColor.RED + "Click to return to the main menu"));
        backButton.setItemMeta(meta);
        return backButton;
    }

    public void update(Player player, String skillType) {
        // Update the current menu without recreating it
        updatePlayerSkills(player, skillType);
    }

    private CustomPlayer getCustomPlayer(Player player) {
        String customId = skillManager.getCustomId(player);
        CustomPlayer customPlayer = skillManager.getCustomPlayerByCustomId(customId);
        if (customPlayer == null) {
            player.sendMessage(ChatColor.RED + "Error: Could not retrieve your skill data.");
            System.out.println("[ERROR] Failed to retrieve CustomPlayer for customId: " + customId);
        }
        return customPlayer;
    }
}
