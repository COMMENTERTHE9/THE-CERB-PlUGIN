package GUIs;

import GUIs.SkillDefinitions.SkillCategory;
import GUIs.SkillDefinitions.SkillDef;
import Manager.CombatConfig;
import Manager.ScalingFormula;
import Manager.ScalingFormula.ScalingParams;
import Skills.SkillManager;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 5-row (45-slot) detail panel for a single skill.
 * Shows description, XP progress, current stats, next-level preview,
 * and an upgrade button.
 */
public class SkillDetailGUI {

    private final JavaPlugin plugin;
    private final SkillManager skillManager;
    private final CombatConfig combatConfig;

    public SkillDetailGUI(JavaPlugin plugin, SkillManager skillManager, CombatConfig combatConfig) {
        this.plugin = plugin;
        this.skillManager = skillManager;
        this.combatConfig = combatConfig;
    }

    /**
     * Open the skill detail panel for the given player and skill.
     */
    public void open(Player player, String skillName) {
        SkillDef def = SkillDefinitions.byName(skillName);
        if (def == null) {
            player.sendMessage(ChatColor.RED + "Unknown skill: " + skillName);
            return;
        }

        SkillCategory cat = def.category();
        Map<String, Integer> levels = skillManager.getPlayerSkillLevels(player);
        Map<String, Integer> xpMap  = skillManager.getPlayerSkillXP(player);

        int level = levels.getOrDefault(skillName, 1);
        int xp    = xpMap.getOrDefault(skillName, 0);
        int reqXp = skillManager.calculateRequiredXpForNextLevel(level);
        boolean maxLevel = skillManager.isMaxLevel(skillName, level);

        Inventory inv = GuiBuilder.createGui(GuiIdentity.SKILL_DETAIL, 5,
                "" + cat.color + ChatColor.BOLD + def.name() + " Details");

        // ── Row 1 (0-8): border ──────────────────────────────────────
        fillRowBorder(inv, 0, cat.accentGlass);

        // ── Row 2 (9-17): skill icon, description, XP info ──────────
        inv.setItem(9, GuiBuilder.createGlassPane(cat.accentGlass));
        inv.setItem(17, GuiBuilder.createGlassPane(cat.accentGlass));

        // Slot 10: skill icon
        inv.setItem(10, GuiBuilder.createItem(def.icon(),
                cat.color + "" + ChatColor.BOLD + def.name() + ChatColor.GRAY + " (Lv " + level + ")",
                ChatColor.GRAY + def.description()));

        // Slot 12: Guide book — how to level this skill
        List<String> guideLore = new ArrayList<>();
        guideLore.add("");
        // Wrap guide text into multiple lines at ~40 chars
        String guide = def.guideText();
        for (int i = 0; i < guide.length(); i += 40) {
            int end = Math.min(i + 40, guide.length());
            // Don't break mid-word
            if (end < guide.length() && guide.charAt(end) != ' ') {
                int lastSpace = guide.lastIndexOf(' ', end);
                if (lastSpace > i) end = lastSpace;
            }
            guideLore.add(ChatColor.GRAY + guide.substring(i, end).trim());
        }
        guideLore.add("");
        guideLore.add(ChatColor.DARK_GRAY + "Category: " + cat.color + cat.displayTitle);
        inv.setItem(12, GuiBuilder.createItem(Material.BOOK,
                ChatColor.GOLD + "" + ChatColor.BOLD + "How to Level Up", guideLore));

        // Slots 14-16: XP info
        List<String> xpLore = new ArrayList<>();
        xpLore.add(ChatColor.GRAY + "Current XP: " + ChatColor.WHITE + xp);
        xpLore.add(ChatColor.GRAY + "Needed: " + ChatColor.WHITE + reqXp);
        xpLore.add("");
        xpLore.add(GuiBuilder.progressBar(xp, reqXp, 20));
        if (maxLevel) {
            xpLore.add(ChatColor.GOLD + "MAX LEVEL REACHED");
        }
        inv.setItem(15, GuiBuilder.createItem(Material.EXPERIENCE_BOTTLE,
                ChatColor.AQUA + "XP Progress", xpLore));

        // ── Row 3 (18-26): current stats ─────────────────────────────
        inv.setItem(18, GuiBuilder.createGlassPane(cat.accentGlass));
        inv.setItem(26, GuiBuilder.createGlassPane(cat.accentGlass));

        inv.setItem(19, GuiBuilder.createItem(Material.GREEN_CONCRETE,
                ChatColor.GREEN + "" + ChatColor.BOLD + "Current Stats (Lv " + level + ")"));

        Map<String, ScalingParams> allParams = combatConfig.getAllScalingParams();
        String[] statKeys = def.scalingStatKeys();

        if (statKeys.length == 0) {
            inv.setItem(20, GuiBuilder.createItem(Material.PAPER,
                    ChatColor.GRAY + "General Effectiveness",
                    ChatColor.GRAY + "Level affects this skill's",
                    ChatColor.GRAY + "overall effectiveness."));
        } else {
            int slot = 20;
            for (String key : statKeys) {
                if (slot > 25) break;
                ScalingParams params = allParams.get(key);
                if (params == null) {
                    inv.setItem(slot++, GuiBuilder.createItem(Material.BARRIER,
                            ChatColor.RED + formatStatName(key),
                            ChatColor.GRAY + "No scaling data found"));
                    continue;
                }
                double value = ScalingFormula.compute(level, params);
                inv.setItem(slot++, GuiBuilder.createItem(Material.LIME_STAINED_GLASS_PANE,
                        ChatColor.GREEN + formatStatName(key),
                        GuiBuilder.statLine(formatStatName(key), formatValue(value))));
            }
        }

        // ── Row 4 (27-35): next level preview ────────────────────────
        inv.setItem(27, GuiBuilder.createGlassPane(cat.accentGlass));
        inv.setItem(35, GuiBuilder.createGlassPane(cat.accentGlass));

        int nextLevel = level + 1;
        inv.setItem(28, GuiBuilder.createItem(Material.YELLOW_CONCRETE,
                ChatColor.YELLOW + "" + ChatColor.BOLD + "Next Level Preview (Lv " + nextLevel + ")"));

        if (statKeys.length == 0) {
            inv.setItem(29, GuiBuilder.createItem(Material.PAPER,
                    ChatColor.GRAY + "General Improvement",
                    ChatColor.GRAY + "Leveling up increases",
                    ChatColor.GRAY + "skill effectiveness."));
        } else {
            int slot = 29;
            for (String key : statKeys) {
                if (slot > 34) break;
                ScalingParams params = allParams.get(key);
                if (params == null) {
                    inv.setItem(slot++, GuiBuilder.createItem(Material.BARRIER,
                            ChatColor.RED + formatStatName(key),
                            ChatColor.GRAY + "No scaling data found"));
                    continue;
                }
                double curVal  = ScalingFormula.compute(level, params);
                double nextVal = ScalingFormula.compute(nextLevel, params);

                List<String> lore = new ArrayList<>();
                lore.add(GuiBuilder.deltaLine(formatStatName(key), curVal, nextVal, "%.4f"));
                inv.setItem(slot++, GuiBuilder.createItem(Material.YELLOW_STAINED_GLASS_PANE,
                        ChatColor.YELLOW + formatStatName(key), lore));
            }
        }

        // ── Row 5 (36-44): border + back + upgrade ──────────────────
        fillRowBorder(inv, 4, Material.BLACK_STAINED_GLASS_PANE);
        inv.setItem(36, GuiBuilder.createBackArrow(cat.name()));

        if (!maxLevel && player.hasPermission("cerb.admin")) {
            inv.setItem(44, GuiBuilder.createActionItem(Material.LIME_CONCRETE,
                    ChatColor.GREEN + "" + ChatColor.BOLD + "Admin: +10 XP",
                    List.of(ChatColor.GRAY + "Test tool (admins only)",
                            ChatColor.GRAY + "XP: " + xp + " / " + reqXp),
                    "upgrade", skillName));
        } else if (!maxLevel) {
            inv.setItem(44, GuiBuilder.createItem(Material.EXPERIENCE_BOTTLE,
                    ChatColor.GREEN + "Level up by using this skill",
                    ChatColor.GRAY + "XP: " + xp + " / " + reqXp));
        } else {
            inv.setItem(44, GuiBuilder.createItem(Material.RED_CONCRETE,
                    ChatColor.RED + "Cannot Upgrade",
                    ChatColor.GRAY + "Maximum level reached."));
        }

        GuiBuilder.fillEmpty(inv, Material.BLACK_STAINED_GLASS_PANE);

        // Folia-safe open + start border animation
        player.getScheduler().run(plugin, (ScheduledTask t) -> {
            player.openInventory(inv);
            BorderAnimator.start(plugin, player, inv, cat.accentGlass,
                    Set.of(36, 44), 3);
        }, null);
    }

    // ── Helpers ─────────────────────────────────────────────────────

    /** Fill an entire row with border glass. Row is 0-indexed. */
    private void fillRowBorder(Inventory inv, int row, Material glass) {
        int start = row * 9;
        for (int i = start; i < start + 9 && i < inv.getSize(); i++) {
            inv.setItem(i, GuiBuilder.createGlassPane(glass));
        }
    }

    /**
     * Parse a scaling key like "critical-strike / crit-chance" into
     * a display name like "Crit Chance".
     * If the key contains " / ", take the second part and title-case it.
     */
    private String formatStatName(String key) {
        String raw = key;
        int sep = raw.indexOf(" / ");
        if (sep >= 0) {
            raw = raw.substring(sep + 3);
        }
        // "crit-chance" -> "Crit Chance"
        String[] parts = raw.split("-");
        StringBuilder sb = new StringBuilder();
        for (String part : parts) {
            if (!part.isEmpty()) {
                if (sb.length() > 0) sb.append(' ');
                sb.append(Character.toUpperCase(part.charAt(0)));
                if (part.length() > 1) sb.append(part.substring(1));
            }
        }
        return sb.toString();
    }

    /** Format a double value for display (percentage-like if < 2, otherwise raw). */
    private String formatValue(double value) {
        if (value < 2.0) {
            return String.format("%.1f%%", value * 100);
        }
        return String.format("%.2f", value);
    }
}
