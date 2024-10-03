package Skills;

import Manager.PlayerManaManager;
import cerberus.world.cerb.CustomPlayer;
import cerberus.world.cerb.DatabaseManager;
import org.bukkit.entity.Player;
import org.bukkit.metadata.FixedMetadataValue;

import java.util.UUID;

public class ArcaneKnowledgeSkill extends MagicSkill {
    private final PlayerManaManager manaManager;
    private final DatabaseManager databaseManager;
    private final cerberus.world.cerb.cerb plugin; // Reference to the main plugin class

    public ArcaneKnowledgeSkill(String name, PlayerManaManager manaManager, DatabaseManager databaseManager, cerberus.world.cerb.cerb plugin) {
        super(name);
        this.manaManager = manaManager;
        this.databaseManager = databaseManager;
        this.plugin = plugin; // Initialize the plugin reference
    }

    @Override
    public void applyEffect(CustomPlayer customPlayer) {
        if (customPlayer == null) return;

        int level = this.getLevel(); // Get the current skill level
        Player player = customPlayer.getBukkitPlayer();
        UUID playerUUID = player.getUniqueId();

        // Reduce mana cost based on skill level
        double manaCostReduction = 0.1 * level; // Example: 10% reduction per level
        manaManager.applyManaCostReduction(player, manaCostReduction);

        // Increase spell potency and range based on skill level
        double potencyMultiplier = 1.0 + 0.05 * level; // Example: 5% potency increase per level
        double rangeMultiplier = 1.0 + 0.03 * level;   // Example: 3% range increase per level

        applySpellPotencyMultiplier(player, potencyMultiplier);
        applySpellRangeMultiplier(player, rangeMultiplier);

        // Save these effects to the database
        databaseManager.saveSkillEffect(playerUUID, "ArcaneKnowledge", "manaCostReduction", manaCostReduction);
        databaseManager.saveSkillEffect(playerUUID, "ArcaneKnowledge", "potencyMultiplier", potencyMultiplier);
        databaseManager.saveSkillEffect(playerUUID, "ArcaneKnowledge", "rangeMultiplier", rangeMultiplier);
    }

    public void loadAndApplyEffects(Player player) {
        UUID playerUUID = player.getUniqueId();

        // Load effects from the database
        double manaCostReduction = databaseManager.loadSkillEffect(playerUUID, "ArcaneKnowledge", "manaCostReduction", 0.0);
        double potencyMultiplier = databaseManager.loadSkillEffect(playerUUID, "ArcaneKnowledge", "potencyMultiplier", 1.0);
        double rangeMultiplier = databaseManager.loadSkillEffect(playerUUID, "ArcaneKnowledge", "rangeMultiplier", 1.0);

        // Apply the loaded effects
        manaManager.applyManaCostReduction(player, manaCostReduction);
        applySpellPotencyMultiplier(player, potencyMultiplier);
        applySpellRangeMultiplier(player, rangeMultiplier);
    }

    // Method to apply spell potency multiplier for Bukkit player
    private void applySpellPotencyMultiplier(Player player, double multiplier) {
        double currentSpellDamage = getPlayerSpellDamage(player);
        double newSpellDamage = currentSpellDamage * multiplier;
        setPlayerSpellDamage(player, newSpellDamage);
        player.sendMessage("🔮 Your spell potency has been permanently increased by " + (multiplier - 1) * 100 + "%.");
    }

    // Method to apply spell range multiplier for Bukkit player
    private void applySpellRangeMultiplier(Player player, double multiplier) {
        double currentSpellRange = getPlayerSpellRange(player);
        double newSpellRange = currentSpellRange * multiplier;
        setPlayerSpellRange(player, newSpellRange);
        player.sendMessage("🌠 Your spell range has been permanently increased by " + (multiplier - 1) * 100 + "%.");
    }

    // Placeholder methods to get and set spell attributes
    private double getPlayerSpellDamage(Player player) {
        return player.getMetadata("spellDamage").get(0).asDouble();
    }

    private void setPlayerSpellDamage(Player player, double newDamage) {
        player.setMetadata("spellDamage", new FixedMetadataValue(plugin, newDamage));
    }

    private double getPlayerSpellRange(Player player) {
        return player.getMetadata("spellRange").get(0).asDouble();
    }

    private void setPlayerSpellRange(Player player, double newRange) {
        player.setMetadata("spellRange", new FixedMetadataValue(plugin, newRange));
    }
}
