package Skills;

import DefensiveMagic.Barrier;
import DefensiveMagic.ManaDrain;
import DefensiveMagic.Shield;
import DefensiveMagic.Ward;
// ProtocolLib removed — Folia incompatible. Key-combo input detection disabled.
// TODO: Reimplement using PlayerInteractEvent or custom keybind system.
import cerberus.world.cerb.CustomPlayer;
import Manager.PlayerManaManager;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class DefensiveMagicSkill extends MagicSkill {

    private final PlayerManaManager manaManager;
    private final Plugin plugin;
    private Manager.PlayerVirtualHealthManager virtualHealthManager;

    // Track key states
    private final Map<UUID, Boolean> isKeySPressed = new ConcurrentHashMap<>();
    private final Map<UUID, Boolean> isKeyEPressed = new ConcurrentHashMap<>();
    private final Map<UUID, Boolean> isKeyWPressed = new ConcurrentHashMap<>();
    private final Map<UUID, Boolean> isKeyFPressed = new ConcurrentHashMap<>();
    private final Map<UUID, Boolean> isKeyBPressed = new ConcurrentHashMap<>();
    private final Map<UUID, Boolean> isKeyVPressed = new ConcurrentHashMap<>();
    private final Map<UUID, Boolean> isKeyRPressed = new ConcurrentHashMap<>();

    // Multipliers for different abilities
    private final double shieldStrengthMultiplier = 0.05; // 5% increase per level
    private final double wardDurationMultiplier = 0.05;   // 5% increase per level
    private final double barrierStrengthMultiplier = 0.07; // 7% increase per level
    private final double manaDrainEffectiveness = 0.03;    // 3% more effective mana drain per level

    public DefensiveMagicSkill(String name, PlayerManaManager manaManager, Plugin plugin,
                              Manager.PlayerVirtualHealthManager virtualHealthManager) {
        super(name);
        this.manaManager = manaManager;
        this.plugin = plugin;
        this.virtualHealthManager = virtualHealthManager;
        initializeKeyTracking();
    }

    private void initializeKeyTracking() {
        // ProtocolLib removed for Folia compatibility.
        // Key-combo detection disabled. Defensive magic structures (Shield, Ward,
        // Barrier, ManaDrain) need a new input system to be usable.
        java.util.logging.Logger.getLogger("Cerberus")
                .info("[DefensiveMagic] Key tracking disabled — ProtocolLib not available on Folia.");
    }

    private boolean isKeyCombinationPressed(UUID playerUUID, String combination) {
        switch (combination) {
            case "S+E":
                return isKeySPressed.getOrDefault(playerUUID, false) && isKeyEPressed.getOrDefault(playerUUID, false);
            case "W+F":
                return isKeyWPressed.getOrDefault(playerUUID, false) && isKeyFPressed.getOrDefault(playerUUID, false);
            case "B+F":
                return isKeyBPressed.getOrDefault(playerUUID, false) && isKeyFPressed.getOrDefault(playerUUID, false);
            case "V+R":
                return isKeyVPressed.getOrDefault(playerUUID, false) && isKeyRPressed.getOrDefault(playerUUID, false);
            default:
                return false;
        }
    }

    private void updateKeyState(UUID playerUUID, String key, boolean isPressed) {
        switch (key) {
            case "S":
                isKeySPressed.put(playerUUID, isPressed);
                break;
            case "E":
                isKeyEPressed.put(playerUUID, isPressed);
                break;
            case "W":
                isKeyWPressed.put(playerUUID, isPressed);
                break;
            case "F":
                isKeyFPressed.put(playerUUID, isPressed);
                break;
            case "B":
                isKeyBPressed.put(playerUUID, isPressed);
                break;
            case "V":
                isKeyVPressed.put(playerUUID, isPressed);
                break;
            case "R":
                isKeyRPressed.put(playerUUID, isPressed);
                break;
        }
    }

    private void selectDefensiveStructure(Player player, String structureType) {
        switch (structureType) {
            case "shield":
                activateShield(player);
                break;
            case "ward":
                activateWard(player);
                break;
            case "barrier":
                activateBarrier(player);
                break;
            case "manadrain":
                activateManaDrain(player);
                break;
            default:
                player.sendMessage("No valid defensive ability selected.");
                break;
        }
    }

    private void activateShield(Player player) {
        int level = this.getLevel();
        double strength = calculateShieldStrength(level);
        Shield shield = new Shield(player, strength);
        shield.activate();
        player.sendMessage("🛡️ Your magical shield is now active with strength: " + strength);
    }

    private void activateWard(Player player) {
        int level = this.getLevel();
        double duration = calculateWardDuration(level);
        Ward ward = new Ward(player, duration);
        ward.activate();
        player.sendMessage("🌟 Your ward is now active with duration: " + duration + " seconds.");
    }

    private void activateBarrier(Player player) {
        int level = this.getLevel();
        double strength = calculateBarrierStrength(level);
        Barrier barrier = new Barrier(player, strength);
        barrier.activate();
        player.sendMessage("🛡️ Your barrier is now active with strength: " + strength);
    }

    private void activateManaDrain(Player player) {
        int level = this.getLevel();
        double effectiveness = calculateManaDrainEffectiveness(level);
        ManaDrain manaDrain = new ManaDrain(player, effectiveness, player.getTargetEntity(10),
                plugin, virtualHealthManager, manaManager);
        manaDrain.activate();
        player.sendMessage("🔋 Your mana drain is now active with effectiveness: " + (effectiveness * 100) + "%.");
    }

    private double calculateShieldStrength(int level) {
        double baseStrength = 100;
        return baseStrength + (shieldStrengthMultiplier * level * baseStrength);
    }

    private double calculateWardDuration(int level) {
        double baseDuration = 30; // Base duration in seconds
        return baseDuration + (wardDurationMultiplier * level * baseDuration);
    }

    private double calculateBarrierStrength(int level) {
        double baseStrength = 150;
        return baseStrength + (barrierStrengthMultiplier * level * baseStrength);
    }

    private double calculateManaDrainEffectiveness(int level) {
        double baseEffectiveness = 0.1; // Base mana drain effectiveness
        return baseEffectiveness + (manaDrainEffectiveness * level);
    }
}
