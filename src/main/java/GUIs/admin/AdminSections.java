package GUIs.admin;

import GUIs.menu.Status;
import org.bukkit.Material;

import java.util.List;

/** The Control Center's built-in Home sections. Later systems call AdminPanel.registerSection. */
final class AdminSections {

    private AdminSections() {}

    static void registerDefaults(AdminPanel p) {
        p.registerSection(new AdminPanel.Section("players", 10, Material.PLAYER_HEAD, "Players",
                List.of("Inspect any online player's character:", "health, mana, defense, skills.",
                        "Set skill levels and XP, restore,", "teleport to them."),
                Status.LIVE, PlayerListMenu::new));
        p.registerSection(new AdminPanel.Section("combat", 20, Material.IRON_SWORD, "Combat Lab",
                List.of("Spawn registered mobs at an exact", "level, inspect nearby mobs' tier,",
                        "level and HP pool, combat stats,", "debug traces, test-mob cleanup."),
                Status.LIVE, CombatLabMenu::new));
        p.registerSection(new AdminPanel.Section("progression", 30, Material.EXPERIENCE_BOTTLE, "Progression Lab",
                List.of("XP curve, kill-XP rules, XP sources", "and which skills affect gameplay today."),
                Status.PARTIAL, ProgressionLabMenu::new));
        p.registerSection(new AdminPanel.Section("attributes", 35, Material.NETHER_STAR, "Attributes",
                List.of("Vitality, Essence, Power, Fortitude,", "Agility, Fortune - the next milestone.",
                        "Shows the plan; nothing here is live yet."),
                Status.IN_DEVELOPMENT, panel -> new AttributesDevMenu(panel, null)));
        p.registerSection(new AdminPanel.Section("self", 40, Material.ARMOR_STAND, "My Test Character",
                List.of("One-click tools for your own", "character: restore, test kit, skills,",
                        "debug traces, game mode, spawn."),
                Status.LIVE, SelfToolsMenu::new));
        p.registerSection(new AdminPanel.Section("server", 50, Material.COMPARATOR, "Server & Plugin",
                List.of("Read-only diagnostics: versions,", "build, performance, worlds, config", "and runtime state."),
                Status.LIVE, ServerInfoMenu::new));
        p.registerSection(new AdminPanel.Section("maintenance", 60, Material.ANVIL, "Maintenance",
                List.of("Reload configs, save all players.", "(No world tools or data resets here.)"),
                Status.LIVE, MaintenanceMenu::new));
    }
}
