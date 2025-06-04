package Spells;

import Manager.EffectManager;
import Manager.PlayerManaManager;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Map;

public class SpellManager {
    private final Map<String, Spell> spells;
    private final PlayerManaManager playerManaManager;
    private final EffectManager effectManager;

    public SpellManager(EffectManager effectManager, PlayerManaManager playerManaManager) {
        this.spells = new HashMap<>();
        this.playerManaManager = playerManaManager;
        this.effectManager = effectManager;
        initializeSpells();
    }

    private void initializeSpells() {
        // Register your spells here, passing in the required managers
        spells.put("Fireball", new FireballSpell(playerManaManager, effectManager));
        // Add more spells as you create them
    }

    public Spell getSpell(String name) {
        return spells.get(name);
    }

    public void castSpell(String name, Player player) {
        Spell spell = getSpell(name);
        if (spell != null) {
            spell.cast(player);
        } else {
            player.sendMessage("Spell not found!");
        }
    }

    public void addSpell(String name, Spell spell) {
        spells.put(name, spell);
    }

    public void removeSpell(String name) {
        spells.remove(name);
    }

    public boolean hasSpell(String name) {
        return spells.containsKey(name);
    }

    // Elemental Damage Multiplier
    public void applyElementalDamageMultiplier(Player player, double multiplier) {
        // Logic to apply elemental damage multiplier to spells
        player.sendMessage("Elemental damage increased by " + (multiplier * 100) + "%.");
        // Implement the multiplier application logic
    }

    // Elemental Control Multiplier (e.g., accuracy, range)
    public void applyElementalControlMultiplier(Player player, double multiplier) {
        // Logic to apply control multiplier to spells
        player.sendMessage("Elemental control improved by " + (multiplier * 100) + "%.");
        // Implement the control multiplier application logic
    }

    // Arcane Blast Multiplier
    public void applyArcaneBlastMultiplier(Player player, double multiplier, double critChance) {
        // Logic to apply Arcane Blast multiplier and critical hit chance
        player.sendMessage("Arcane Blast empowered! Damage: +" + (multiplier * 100) + "%, Crit Chance: " + (critChance * 100) + "%.");
        // Implement the Arcane Blast logic with critical hit handling
    }

    // Fire Damage Multiplier with Burn Effect
    public void applyFireDamageMultiplier(Player player, double multiplier, int burnDuration) {
        // Logic to apply fire damage multiplier and burn duration
        player.sendMessage("Fire damage increased by " + (multiplier * 100) + "%. Burn duration: " + burnDuration + " seconds.");
        // Implement the burn damage over time logic
    }

    // Ice Damage Multiplier with Slow Effect
    public void applyIceDamageMultiplier(Player player, double multiplier, int slowDuration) {
        // Logic to apply ice damage multiplier and slow duration
        player.sendMessage("Ice damage increased by " + (multiplier * 100) + "%. Slow duration: " + slowDuration + " seconds.");
        // Implement the slow effect logic
    }

    // Lightning Damage Multiplier with Stun Effect
    public void applyLightningDamageMultiplier(Player player, double multiplier, int stunChance) {
        // Logic to apply lightning damage multiplier and stun chance
        player.sendMessage("Lightning damage increased by " + (multiplier * 100) + "%. Stun chance: " + stunChance + "%.");
        // Implement the stun effect logic
    }

    // Dark Damage Multiplier with Life Steal
    public void applyDarkDamageMultiplier(Player player, double multiplier, double lifeStealPercent) {
        // Logic to apply dark damage multiplier and life steal effect
        player.sendMessage("Dark damage increased by " + (multiplier * 100) + "%. Life steal: " + (lifeStealPercent * 100) + "% of damage.");
        // Implement the life steal logic
    }

    // Binding Effectiveness Multiplier
    public void applyBindingEffectiveness(Player player, double effectivenessMultiplier, int trapDuration) {
        // Logic to apply binding effectiveness multiplier and trap duration
        player.sendMessage("Binding effectiveness increased by " + (effectivenessMultiplier * 100) + "%. Trap duration: " + trapDuration + " seconds.");
        // Implement the binding/trap logic
    }

    // Earth Damage Multiplier with Defense Boost
    public void applyEarthDamageMultiplier(Player player, double multiplier, double defenseBoost) {
        // Logic to apply earth damage multiplier and defense boost
        player.sendMessage("Earth damage increased by " + (multiplier * 100) + "%. Defense increased by " + (defenseBoost * 100) + "%.");
        // Implement the defense boost logic
    }

    // Wind Damage Multiplier with Evasion Boost
    public void applyWindDamageMultiplier(Player player, double multiplier, double evasionBoost) {
        // Logic to apply wind damage multiplier and evasion boost
        player.sendMessage("Wind damage increased by " + (multiplier * 100) + "%. Evasion increased by " + (evasionBoost * 100) + "%.");
        // Implement the evasion boost logic
    }
}
