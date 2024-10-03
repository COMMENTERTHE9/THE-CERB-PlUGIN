package Skills;

import Manager.ManaType;
import Manager.PlayerManaManager;
import cerberus.world.cerb.CustomPlayer;
import org.bukkit.entity.Player;
import org.bukkit.entity.LivingEntity;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;

public class SummoningSkill extends MagicSkill {

    private final PlayerManaManager manaManager;
    private final ManaType manaType = ManaType.SUMMONING; // Using Summoning Energy as the mana type
    private final double baseManaCost = 30; // Base mana cost for summoning
    private final double baseDuration = 120; // Base duration of summoned creatures in seconds
    private final double baseStrengthMultiplier = 1.0; // Base strength multiplier for summoned creatures
    private final Plugin plugin; // Reference to the main plugin

    public SummoningSkill(String name, PlayerManaManager manaManager, Plugin plugin) {
        super(name);
        this.manaManager = manaManager;
        this.plugin = plugin;
    }

    @Override
    public void applyEffect(CustomPlayer customPlayer) {
        if (customPlayer == null) return;
        Player player = customPlayer.getPlayer();

        // Apply the summoning skill effect based on the player's skill level
        int level = this.getLevel();
        double manaCost = calculateManaCost(level);
        double duration = calculateDuration(level);
        double strengthMultiplier = baseStrengthMultiplier * (1 + (level * 0.1)); // Increase strength by 10% per level

        if (!manaManager.spendMana(player, manaType, manaCost)) {
            player.sendMessage("Not enough Summoning Energy to summon a creature!");
            return;
        }

        // Logic to summon a creature with enhanced strength and duration
        summonCreature(player, duration, strengthMultiplier);
    }

    @Override
    public void applyEffect(Player player) {
        // This method could be used for direct player effects if needed
    }

    private void summonCreature(Player player, double duration, double strengthMultiplier) {
        // Example logic to summon a creature with enhanced properties
        LivingEntity summonedCreature = getSummonableCreature(player);
        if (summonedCreature == null) {
            player.sendMessage("No suitable creatures available for summoning.");
            return;
        }

        // Apply custom name to the summoned creature
        summonedCreature.setCustomName(player.getName() + "'s Summoned Creature");

        // Apply strength enhancement
        summonedCreature.getAttribute(Attribute.GENERIC_ATTACK_DAMAGE).setBaseValue(
                summonedCreature.getAttribute(Attribute.GENERIC_ATTACK_DAMAGE).getBaseValue() * strengthMultiplier
        );

        // Tag the entity as a summoned creature
        tagSummonedCreature(summonedCreature);

        // Schedule a task to remove the creature after the duration expires
        new org.bukkit.scheduler.BukkitRunnable() {
            @Override
            public void run() {
                if (summonedCreature.isValid()) {
                    summonedCreature.remove();
                    player.sendMessage("Your summoned creature has returned to the void.");
                }
            }
        }.runTaskLater(plugin, (long) (duration * 20)); // Convert seconds to ticks (20 ticks = 1 second)

        player.sendMessage("You have summoned a creature for " + (int) duration + " seconds with enhanced strength!");
    }

    private LivingEntity getSummonableCreature(Player player) {
        // Placeholder logic to get a summonable creature type
        // Replace this with actual summoning logic once specific creature types are decided
        return player.getWorld().spawn(player.getLocation(), LivingEntity.class); // Replace with actual entity class
    }

    private void tagSummonedCreature(LivingEntity entity) {
        NamespacedKey key = new NamespacedKey(plugin, "summoned_creature");
        entity.getPersistentDataContainer().set(key, PersistentDataType.BYTE, (byte) 1);
    }

    private double calculateManaCost(int level) {
        // Reduces mana cost by 5% per level, with a minimum mana cost of 10
        return Math.max(baseManaCost * (1 - (level * 0.05)), 10);
    }

    private double calculateDuration(int level) {
        // Increases duration by 10% per level
        return baseDuration * (1 + (level * 0.1));
    }
}
