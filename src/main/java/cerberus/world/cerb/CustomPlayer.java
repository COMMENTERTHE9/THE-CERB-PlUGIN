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

    public double    getMagicMultiplier()          { return magicMultiplier; }
    public void      setMagicMultiplier(double d)  { this.magicMultiplier = d; }

    public ItemStack getCurrentCraftItem()         { return currentCraftItem; }
    public void      setCurrentCraftItem(ItemStack i){ this.currentCraftItem = i; }
}
