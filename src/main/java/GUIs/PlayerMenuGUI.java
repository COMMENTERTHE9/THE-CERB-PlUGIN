package GUIs;

import GUIs.menu.Ui;
import GUIs.player.AscensionMenu;
import GUIs.player.Availability;
import GUIs.player.BestiaryMenu;
import GUIs.player.EquipmentMenu;
import GUIs.player.GuideMenu;
import GUIs.player.PlayerHubMenu;
import GUIs.player.PlayerMenuSections;
import GUIs.player.PlayerMenuSections.Section;
import GUIs.player.SkillsMenu;
import GUIs.player.WorldMenu;
import GUIs.stats.StatsMenu;
import cerberus.world.cerb.CerberusPlugin;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.List;

/**
 * The Player Menu entry points: the hotbar head (slot 9), {@code /menu}, and the registry of hub
 * destinations. The screens themselves live in {@code GUIs.player} on the shared menu framework.
 *
 * <p>The head is recognised by a plugin tag in its item data. Heads given out before the tag
 * existed are still recognised by their name, so they get protected and replaced on the next
 * join instead of becoming loose copies. A head only ever opens its holder's own menu.
 */
public class PlayerMenuGUI {

    /** Hotbar slot 9 (index 8) — where the Player Menu head lives. */
    public static final int MENU_HEAD_SLOT = 8;

    private static final String HEAD_NAME = ChatColor.LIGHT_PURPLE + "Player Menu";

    private final CerberusPlugin plugin;
    private final NamespacedKey headKey;
    private final PlayerMenuSections sections = new PlayerMenuSections();
    private final GUIs.player.PlayerMenuMemory memory = new GUIs.player.PlayerMenuMemory();

    public PlayerMenuGUI(CerberusPlugin plugin) {
        this.plugin = plugin;
        this.headKey = new NamespacedKey(plugin, "player_menu_head");
        registerDefaultSections();
    }

    /** The hub's destinations. Register a section to make a new system reachable from the hub. */
    public PlayerMenuSections sections() { return sections; }

    /** What each player last saw in the menu (this session, in memory). */
    public GUIs.player.PlayerMenuMemory memory() { return memory; }

    // ── Opening ─────────────────────────────────────────────────

    /** Open the hub (fresh history). Safe from any thread. */
    public void open(Player player) {
        plugin.getMenuManager().openRoot(player, new PlayerHubMenu(plugin));
    }

    // ── Hotbar head ─────────────────────────────────────────────

    /** The head placed in hotbar slot 9. */
    public ItemStack getPlayerMenuItem(Player player) {
        ItemStack head = Ui.head(player, HEAD_NAME, Ui.lore()
                .text("Your character, skills and stats.")
                .hint("Right-click to open (or /menu)").build());
        ItemMeta meta = head.getItemMeta();
        if (meta != null) {
            meta.getPersistentDataContainer().set(headKey, PersistentDataType.BYTE, (byte) 1);
            head.setItemMeta(meta);
        }
        return head;
    }

    /**
     * Put a fresh Player Menu head in hotbar slot 9 without destroying anything.
     *
     * <p>If the slot holds a real item, that item is moved to a free inventory slot, or dropped
     * at the player's feet when the inventory is full. Stray menu heads elsewhere in the
     * inventory (e.g. moved there with a number-key swap) are removed so the player never ends
     * up with duplicates. Must run on the player's region thread (join, respawn, GUI click).
     */
    public void placeMenuHead(Player player) {
        org.bukkit.inventory.PlayerInventory inv = player.getInventory();
        ItemStack displaced = inv.getItem(MENU_HEAD_SLOT);
        inv.setItem(MENU_HEAD_SLOT, getPlayerMenuItem(player));

        for (int i = 0; i < inv.getSize(); i++) {
            if (i != MENU_HEAD_SLOT && isPlayerMenuHead(inv.getItem(i))) {
                inv.setItem(i, null);
            }
        }
        if (isPlayerMenuHead(player.getItemOnCursor())) player.setItemOnCursor(null);

        if (displaced == null || displaced.getType().isAir() || isPlayerMenuHead(displaced)) {
            return;
        }
        java.util.Map<Integer, ItemStack> leftover = inv.addItem(displaced);
        for (ItemStack drop : leftover.values()) {
            // The Admin Control Panel chest is plugin-issued and re-issued on join when there is
            // room; never leave it on the ground for someone else to pick up.
            if (plugin.getAdminChestGUI() != null && plugin.getAdminChestGUI().isAdminChest(drop)) {
                continue;
            }
            player.getWorld().dropItemNaturally(player.getLocation(), drop);
        }
        player.sendMessage(ChatColor.YELLOW + "Your item in hotbar slot 9 was moved "
                + (leftover.isEmpty() ? "into your inventory" : "to the ground (inventory full)")
                + " to make room for the Player Menu.");
    }

    /** True for the Player Menu head: tagged by the plugin, or (older heads) named like it. */
    public boolean isPlayerMenuHead(ItemStack item) {
        if (item == null || item.getType() != Material.PLAYER_HEAD || !item.hasItemMeta()) {
            return false;
        }
        ItemMeta meta = item.getItemMeta();
        if (meta.getPersistentDataContainer().has(headKey, PersistentDataType.BYTE)) return true;
        return meta.hasDisplayName() && meta.getDisplayName().equals(HEAD_NAME);
    }

    // ── Hub destinations ────────────────────────────────────────

    private void registerDefaultSections() {
        sections.register(new Section("skills", 19, Material.EXPERIENCE_BOTTLE, "Skills",
                List.of("Levels and XP, what trains each", "skill and what it does for you."),
                PlayerMenuGUI::skillsLive, p -> Availability.available(), SkillsMenu::new));
        sections.register(new Section("stats", 21, Material.BLAZE_POWDER, "Stats & Attributes",
                List.of("Your six attributes, damage,", "survivability and magic numbers."),
                null, p -> Availability.available(), StatsMenu::new));
        sections.register(new Section("ascension", 23, Material.END_CRYSTAL, "Ascension",
                List.of("The ten realms and where you", "stand among them."),
                (pl, p) -> List.of(ChatColor.GRAY + "Your realm: " + ChatColor.WHITE
                        + pl.getAttributeService().get(p.getUniqueId()).realm().realm().displayName),
                p -> Availability.available("Breakthroughs are not built yet."), AscensionMenu::new));
        sections.register(new Section("equipment", 25, Material.NETHERITE_CHESTPLATE, "Equipment",
                List.of("What your armor and weapon", "do for you today."),
                (pl, p) -> List.of(ChatColor.GRAY + "Damage reduction: " + ChatColor.WHITE
                        + GUIs.menu.Fmt.pct(pl.getPlayerVirtualHealthManager().getArmorReduction(p))),
                p -> Availability.available("Custom gear is not built yet."), EquipmentMenu::new));
        sections.register(new Section("bestiary", 28, Material.ZOMBIE_HEAD, "Bestiary",
                List.of("The creatures of this world:", "tier, realm, XP and health."),
                null, p -> Availability.available("Kill tracking is not built yet."), BestiaryMenu::new));
        sections.register(new Section("world", 30, Material.FILLED_MAP, "World & Exploration",
                List.of("How dangerous the land is here", "and how much you have explored."),
                PlayerMenuGUI::worldLive, p -> Availability.available("Regions and landmarks are not built yet."), WorldMenu::new));
        sections.register(new Section("collections", 32, Material.CHEST, "Collections",
                List.of("Items and materials you gather."),
                null, p -> Availability.inDevelopment("Not designed yet."), null));
        sections.register(new Section("recipes", 34, Material.CRAFTING_TABLE, "Recipes",
                List.of("Cerberus crafting recipes."),
                null, p -> Availability.inDevelopment("Custom recipes are not built yet."), null));
        sections.register(new Section("quests", 38, Material.WRITABLE_BOOK, "Quests",
                List.of("Tasks, stories and rewards."),
                null, p -> Availability.inDevelopment("Quests are not built yet."), null));
        sections.register(new Section("unlocks", 40, Material.TRIPWIRE_HOOK, "Unlocks",
                List.of("Abilities and features you", "unlock as you progress."),
                null, p -> Availability.inDevelopment("Nothing unlocks yet."), null));
        sections.register(new Section("guide", 42, Material.BOOK, "Guide",
                List.of("How Cerberus works, in short", "topics."),
                null, p -> Availability.available(), GuideMenu::new));
    }

    private static List<String> worldLive(CerberusPlugin plugin, Player p) {
        org.bukkit.Location loc = p.getLocation(), spawn = loc.getWorld().getSpawnLocation();
        double dx = loc.getX() - spawn.getX(), dz = loc.getZ() - spawn.getZ();
        int level = plugin.getCombatConfig().mobHealthScaling.levelForDistance(Math.sqrt(dx * dx + dz * dz));
        int chunks = plugin.getChunkDiscoveryManager() == null ? -1 : plugin.getChunkDiscoveryManager().discoveredCount(p.getUniqueId());
        return List.of(ChatColor.GRAY + "Creature level here: " + ChatColor.WHITE + level,
                ChatColor.GRAY + "Chunks discovered: " + ChatColor.WHITE + (chunks < 0 ? "loading..." : String.format(java.util.Locale.ROOT, "%,d", chunks)));
    }

    private static List<String> skillsLive(CerberusPlugin plugin, Player p) {
        if (!plugin.getSkillManager().hasLoadedSkills(p.getUniqueId())) return List.of();
        int total = 0, best = -1;
        SkillDefinitions.SkillDef top = null;
        for (SkillDefinitions.SkillDef d : SkillDefinitions.all()) {
            int lvl = plugin.getSkillManager().getSkillLevel(p.getUniqueId(), d.name());
            total += lvl;
            if (lvl > best) { best = lvl; top = d; }
        }
        if (top == null) return List.of();
        String cap = GUIs.menu.Fmt.grouped(SkillDefinitions.all().size() * Skills.SkillProgression.MAX_LEVEL);
        List<String> lines = new java.util.ArrayList<>(3);
        int gained = plugin.getPlayerMenuGUI().memory().gainedTotal(p.getUniqueId(), plugin.getSkillManager());
        if (gained > 0) lines.add(GUIs.player.PlayerTheme.newProgress(gained));
        lines.add(ChatColor.GRAY + "Total level: " + ChatColor.WHITE + GUIs.menu.Fmt.grouped(total) + ChatColor.DARK_GRAY + " / " + cap);
        lines.add(ChatColor.GRAY + "Highest: " + ChatColor.WHITE + top.displayName() + " " + best);
        return lines;
    }
}
