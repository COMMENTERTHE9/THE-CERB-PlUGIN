package cerberus.world.cerb;

import Manager.*;
import Skills.SkillManager;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class CustomPlayer {

    /* ── static registry ───────────────────────── */
    private static final Map<UUID, CustomPlayer> REGISTRY = new ConcurrentHashMap<>();
    public static CustomPlayer get(UUID id)               { return REGISTRY.get(id); }
    public static void        register(CustomPlayer cp)   { REGISTRY.put(cp.uuid, cp); }
    public static void        unregister(UUID id)         { REGISTRY.remove(id); }

    /* ── core references ───────────────────────── */
    private final UUID   uuid;
    private final Player bukkit;
    private final SkillManager skills;
    private final PlayerVirtualHealthManager vh;
    private final PlayerDefenseManager       def;
    private final PlayerStrengthManager      str;

    /* ── useful session fields ─────────────────── */
    private ItemStack currentCraftItem;           // for crafting GUI
    private double    magicMultiplier = 1.0;      // cached combat stat

    /* ── ctor ──────────────────────────────────── */
    public CustomPlayer(Player p,
                        SkillManager skills,
                        PlayerVirtualHealthManager vh,
                        PlayerDefenseManager def,
                        PlayerStrengthManager str)
    {
        this.uuid   = p.getUniqueId();
        this.bukkit = p;
        this.skills = skills;
        this.vh     = vh;
        this.def    = def;
        this.str    = str;

        register(this);          // add to static map
    }

    /* ── getters many other classes still use ──── */
    public UUID   getUuid()              { return uuid; }
    public Player getBukkit()            { return bukkit; }
    public SkillManager getSkillManager(){ return skills; }

    public PlayerVirtualHealthManager getVHealth() { return vh; }
    public PlayerDefenseManager       getDefense() { return def; }
    public PlayerStrengthManager      getStrength(){ return str; }

    /* ── aliases used by 24+ skill classes ──── */
    public Player                     getPlayer()             { return bukkit; }
    public Player                     getBukkitPlayer()       { return bukkit; }
    public PlayerVirtualHealthManager getHealthManager()      { return vh; }
    public PlayerDefenseManager       getDefenseManager()     { return def; }
    public PlayerStrengthManager      getStrengthManager()    { return str; }
    public ItemStack                  getCurrentCraftedItem() { return currentCraftItem; }

    public double    getMagicMultiplier()          { return magicMultiplier; }
    public void      setMagicMultiplier(double d)  { this.magicMultiplier = d; }

    public ItemStack getCurrentCraftItem()         { return currentCraftItem; }
    public void      setCurrentCraftItem(ItemStack i){ this.currentCraftItem = i; }

    /* ── static lookup alias (used by TrapManager, etc.) ── */
    public static CustomPlayer getCustomPlayer(Player p) {
        return REGISTRY.get(p.getUniqueId());
    }

    /* ── skill delegation aliases (used by SkillGUI, CraftingManager, ElementalInfusionManager, etc.) ── */

    /** Returns the skill-levels map for this player from the SkillManager. */
    public Map<String, Integer> getSkillLevels() {
        return skills.getPlayerSkillLevels(bukkit);
    }

    /** Returns the skill-XP map for this player from the SkillManager. */
    public Map<String, Integer> getSkillXP() {
        return skills.getPlayerSkillXP(bukkit);
    }

    /** Delegates to this player's skill-level map. */
    public int getSkillLevel(String skillName) {
        return skills.getSkillLevel(bukkit, skillName);
    }

    /** Delegates to SkillManager.getSkill(skillName). */
    public Skills.Skill getSkill(String skillName) {
        return skills.getSkill(skillName);
    }

}
