package GUIs;

import GUIs.SkillDefinitions.SkillCategory;
import GUIs.SkillDefinitions.SkillDef;
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
 * Data-driven skill category menu. Uses {@link SkillDefinitions} for all
 * skill metadata -- no more hardcoded skill lists repeated six times.
 * <p>
 * The old {@link #update} and {@link #show} methods are kept for backward
 * compatibility with legacy callers (MobKillListener, MenuListener, etc.).
 */
public class SkillGUI {

    private final JavaPlugin plugin;
    private final SkillManager skillManager;

    public SkillGUI(JavaPlugin plugin, SkillManager skillManager) {
        this.plugin = plugin;
        this.skillManager = skillManager;
    }

    // ── Primary entry point ─────────────────────────────────────────

    /**
     * Open a category skill menu for the given player.
     *
     * @param player   target player
     * @param category which category to display
     */
    public void openCategoryMenu(Player player, SkillCategory category) {
        List<SkillDef> skills = SkillDefinitions.byCategory(category);

        GuiIdentity identity = switch (category) {
            case COMBAT  -> GuiIdentity.SKILL_COMBAT;
            case MAGIC   -> GuiIdentity.SKILL_MAGIC;
            case UTILITY -> GuiIdentity.SKILL_UTILITY;
        };

        // Enough rows to fit all skills inside the border
        int innerSlotCount = skills.size();
        int innerRows = Math.max(1, (int) Math.ceil(innerSlotCount / 7.0));
        int totalRows = innerRows + 2; // top + bottom borders
        if (totalRows < 3) totalRows = 3;
        if (totalRows > 6) totalRows = 6;

        Inventory inv = GuiBuilder.createGui(identity, totalRows,
                "" + category.color + ChatColor.BOLD + category.displayTitle);

        // Borders: black glass, corners with category accent
        GuiBuilder.fillBorders(inv, Material.BLACK_STAINED_GLASS_PANE);
        setCornerAccents(inv, totalRows, category.accentGlass);

        // Player skill data
        Map<String, Integer> levels = skillManager.getPlayerSkillLevels(player);
        Map<String, Integer> xpMap  = skillManager.getPlayerSkillXP(player);

        // Interior slots (skip borders)
        int[] interior = getInteriorSlots(totalRows);
        for (int i = 0; i < skills.size() && i < interior.length; i++) {
            SkillDef def = skills.get(i);
            int level = levels.getOrDefault(def.name(), 1);
            int xp    = xpMap.getOrDefault(def.name(), 0);
            int reqXp = skillManager.calculateRequiredXpForNextLevel(level);

            List<String> lore = new ArrayList<>();
            lore.add(ChatColor.GRAY + def.description());
            lore.add("");
            lore.add(ChatColor.GRAY + "XP: " + GuiBuilder.progressBar(xp, reqXp, 20));
            lore.add(ChatColor.YELLOW + "Click for details");

            inv.setItem(interior[i], GuiBuilder.createActionItem(
                    def.icon(),
                    "" + category.color + ChatColor.BOLD + def.name()
                            + ChatColor.GRAY + " (Lv " + level + ")",
                    lore,
                    "open_skill_detail", def.name()));
        }

        // Back arrow at bottom-left
        inv.setItem((totalRows - 1) * 9, GuiBuilder.createBackArrow("skill_management"));

        GuiBuilder.fillEmpty(inv, Material.BLACK_STAINED_GLASS_PANE);

        // Back arrow slot must be skipped by the animator
        int backSlot = (totalRows - 1) * 9;

        // Folia-safe open + start border animation
        player.getScheduler().run(plugin, (ScheduledTask t) -> {
            player.openInventory(inv);
            BorderAnimator.start(plugin, player, inv, category.accentGlass,
                    Set.of(backSlot), 3);
        }, null);
    }

    // ── Legacy compatibility ────────────────────────────────────────

    /**
     * Show a skill category by type string.
     * Called by MenuListener and other old code paths.
     */
    public void show(Player player, String skillType) {
        switch (skillType.toLowerCase()) {
            case "combat"  -> openCategoryMenu(player, SkillCategory.COMBAT);
            case "magic"   -> openCategoryMenu(player, SkillCategory.MAGIC);
            case "utility" -> openCategoryMenu(player, SkillCategory.UTILITY);
            default -> player.sendMessage(ChatColor.RED + "Invalid skill type.");
        }
    }

    /**
     * No-op kept for backward compatibility with MobKillListener and
     * PlayerJoinListener. Live GUI updates are no longer pushed; the
     * menu refreshes when the player reopens it.
     */
    public void update(Player player, String skillType) {
        // intentional no-op
    }

    /**
     * Legacy alias kept so old callers that passed both args still compile.
     */
    public void updatePlayerSkills(Player player, String skillType) {
        // intentional no-op
    }

    /**
     * No-op kept for backward compatibility with MobKillListener.
     * Real-time XP updates in the GUI are no longer pushed.
     */
    public void startRealTimeXPUpdate(Player player, String skillType) {
        // intentional no-op
    }

    // ── Helpers ─────────────────────────────────────────────────────

    /** Place category accent glass in the four corners of the inventory. */
    private void setCornerAccents(Inventory inv, int rows, Material accent) {
        int size = rows * 9;
        int lastRow = (rows - 1) * 9;
        for (int slot : new int[]{0, 8, lastRow, lastRow + 8}) {
            if (slot < size) {
                inv.setItem(slot, GuiBuilder.createGlassPane(accent));
            }
        }
    }

    /** Interior (non-border) slot indices for a given number of rows. */
    private int[] getInteriorSlots(int rows) {
        List<Integer> slots = new ArrayList<>();
        for (int row = 1; row < rows - 1; row++) {
            for (int col = 1; col <= 7; col++) {
                slots.add(row * 9 + col);
            }
        }
        return slots.stream().mapToInt(Integer::intValue).toArray();
    }
}
