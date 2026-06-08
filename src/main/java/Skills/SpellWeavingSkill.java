package Skills;

import Manager.ManaType;
import Manager.PlayerManaManager;
import cerberus.world.cerb.CerberusPlugin;
import org.bukkit.entity.Player;
import org.bukkit.metadata.FixedMetadataValue;

import java.util.*;

public class SpellWeavingSkill extends MagicSkill {

    private final PlayerManaManager manaManager;
    private final ManaType manaType = ManaType.SPELL_WEAVING;
    private final CerberusPlugin plugin; // Reference to your main plugin class
    private final double baseManaCost = 100;
    private final double baseEffectivenessMultiplier = 1.2;

    // Map to track recently cast spells for each player
    private final Map<UUID, List<SpellCast>> recentSpellCasts = new HashMap<>();

    // Time frame within which spells are considered for combination (in milliseconds)
    private static final long COMBINATION_TIME_FRAME = 5000L; // 5 seconds

    public SpellWeavingSkill(String name, PlayerManaManager manaManager, CerberusPlugin plugin) {
        super(name);
        this.manaManager = manaManager;
        this.plugin = plugin;
    }

    @Override
    public void applyEffect(Player player) {
        if (player == null) return;
        UUID playerUUID = player.getUniqueId();

        int level = manaManager.getSkillLevel(player, getName());
        double manaCost = baseManaCost * (1 - (level * 0.04));
        double effectivenessMultiplier = baseEffectivenessMultiplier * (1 + (level * 0.1));

        if (!manaManager.spendMana(player, manaType, manaCost)) {
            player.sendMessage("Not enough Spell Weaving Power to enhance your spells!");
            return;
        }

        // Apply the enhanced effectiveness to the player's active spells or combined spells
        applyEnhancedEffectiveness(player, effectivenessMultiplier);

        // Track this spell cast
        trackSpellCast(playerUUID, new SpellCast("SpellName", System.currentTimeMillis(), effectivenessMultiplier));

        // Clean up old spell casts
        cleanupOldSpells(playerUUID);
    }

    private void applyEnhancedEffectiveness(Player player, double multiplier) {
        UUID playerUUID = player.getUniqueId();
        List<SpellCast> recentCasts = recentSpellCasts.getOrDefault(playerUUID, new ArrayList<>());

        double totalMultiplier = multiplier;

        // Combine effects of recent spells
        for (SpellCast cast : recentCasts) {
            totalMultiplier += cast.getEffectivenessMultiplier();
        }

        // Apply the combined multiplier to the current spell
        double currentSpellDamage = getPlayerSpellDamage(player);
        double newSpellDamage = currentSpellDamage * totalMultiplier;
        setPlayerSpellDamage(player, newSpellDamage);

        player.sendMessage("Your spell effectiveness has been enhanced by " + (totalMultiplier - 1) * 100 + "%.");
    }

    private void trackSpellCast(UUID playerUUID, SpellCast spellCast) {
        List<SpellCast> spellCasts = recentSpellCasts.getOrDefault(playerUUID, new ArrayList<>());
        spellCasts.add(spellCast);
        recentSpellCasts.put(playerUUID, spellCasts);
    }

    private void cleanupOldSpells(UUID playerUUID) {
        List<SpellCast> spellCasts = recentSpellCasts.getOrDefault(playerUUID, new ArrayList<>());
        long currentTime = System.currentTimeMillis();

        // Remove spells cast outside the combination time frame
        spellCasts.removeIf(cast -> (currentTime - cast.getCastTime()) > COMBINATION_TIME_FRAME);

        recentSpellCasts.put(playerUUID, spellCasts);
    }

    // Placeholder methods to get and set spell attributes
    private double getPlayerSpellDamage(Player player) {
        List<org.bukkit.metadata.MetadataValue> values = player.getMetadata("spellDamage");
        return values.isEmpty() ? 10.0 : values.get(0).asDouble();
    }

    private void setPlayerSpellDamage(Player player, double newDamage) {
        player.setMetadata("spellDamage", new FixedMetadataValue(plugin, newDamage));
    }

    private double getPlayerSpellRange(Player player) {
        List<org.bukkit.metadata.MetadataValue> values = player.getMetadata("spellRange");
        return values.isEmpty() ? 5.0 : values.get(0).asDouble();
    }

    private void setPlayerSpellRange(Player player, double newRange) {
        player.setMetadata("spellRange", new FixedMetadataValue(plugin, newRange));
    }

    private static class SpellCast {
        private final String spellName;
        private final long castTime;
        private final double effectivenessMultiplier;

        public SpellCast(String spellName, long castTime, double effectivenessMultiplier) {
            this.spellName = spellName;
            this.castTime = castTime;
            this.effectivenessMultiplier = effectivenessMultiplier;
        }

        public long getCastTime() {
            return castTime;
        }

        public double getEffectivenessMultiplier() {
            return effectivenessMultiplier;
        }
    }
}
