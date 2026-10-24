package GUIs.admin;

import Manager.MobRegistry;
import Manager.MobTestService;
import Manager.Tier;
import org.bukkit.Material;
import org.bukkit.entity.EntityType;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Something the Combat Lab can spawn: a mobs.yml entry or a plain vanilla mob. Built from the
 * live registry every time, so it never drifts from mobs.yml; no mob is defined here.
 */
record MobChoice(String label, EntityType type, String mobId, String customName, Tier tier,
                 double xpMultiplier, String source, String blockedReason) {

    /** Vanilla hostiles offered as generic test enemies (no block-griefers: no creeper/enderman/ghast). */
    private static final List<EntityType> VANILLA = List.of(
            EntityType.ZOMBIE, EntityType.HUSK, EntityType.SKELETON, EntityType.STRAY, EntityType.SPIDER,
            EntityType.CAVE_SPIDER, EntityType.VINDICATOR, EntityType.PILLAGER, EntityType.WITCH,
            EntityType.IRON_GOLEM);

    /**
     * Entries that mobs.yml matches by exact name only. Their entity type lives in the code that
     * spawns them (CustomEntities), so the panel uses the same type.
     */
    private static EntityType typeForNamedEntry(String customName) {
        if (customName == null) return null;
        return switch (customName) {
            case "High Health Zombie" -> EntityType.ZOMBIE;   // CustomHighHealthZombie
            case "Sea Monster" -> EntityType.GUARDIAN;        // SeaMonster
            default -> null;
        };
    }

    boolean spawnable() { return blockedReason == null; }

    Material icon() {
        Material egg = type == null ? null : Material.matchMaterial(type.name() + "_SPAWN_EGG");
        if (!spawnable()) return Material.GRAY_DYE;
        return egg != null ? egg : Material.SPAWNER;
    }

    static List<MobChoice> all(MobRegistry registry) {
        List<MobChoice> out = new ArrayList<>();
        List<MobRegistry.MobEntry> entries = new ArrayList<>(registry.getEntries());
        entries.sort(Comparator.comparing((MobRegistry.MobEntry e) -> e.getTier().ordinal()).reversed()
                .thenComparing(MobRegistry.MobEntry::getMobId));
        for (MobRegistry.MobEntry e : entries) {
            EntityType type = e.getEntityType() != null ? e.getEntityType() : typeForNamedEntry(e.getCustomNameExact());
            String blocked = null;
            if (type == null) blocked = "mobs.yml gives no entity type (it is matched by a name pattern only)";
            else if (MobTestService.TERRAIN_DESTROYING.contains(type)) blocked = "Its vanilla behaviour destroys terrain, so the panel won't spawn it";
            out.add(new MobChoice(e.getMobId(), type, e.getMobId(), e.getCustomNameExact(), e.getTier(),
                    e.getXpMultiplier(), "mobs.yml", blocked));
        }
        for (EntityType t : VANILLA) {
            if (registry.getEntryByType(t) != null) continue; // already listed via its registry entry
            out.add(new MobChoice(t.name().toLowerCase(), t, null, null, Tier.COMMON, 1.0, "vanilla", null));
        }
        return out;
    }
}
