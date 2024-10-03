package Skills;

import DefensiveMagic.Barrier;
import DefensiveMagic.ManaDrain;
import DefensiveMagic.Shield;
import DefensiveMagic.Ward;
import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.events.PacketEvent;
import com.comphenix.protocol.events.PacketListener;
import com.comphenix.protocol.events.PacketAdapter;
import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.ProtocolManager;
import com.comphenix.protocol.wrappers.EnumWrappers;
import cerberus.world.cerb.CustomPlayer;
import Manager.PlayerManaManager;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class DefensiveMagicSkill extends MagicSkill {

    private final PlayerManaManager manaManager;
    private final Plugin plugin;

    // Track key states
    private final Map<UUID, Boolean> isKeySPressed = new HashMap<>();
    private final Map<UUID, Boolean> isKeyEPressed = new HashMap<>();
    private final Map<UUID, Boolean> isKeyWPressed = new HashMap<>();
    private final Map<UUID, Boolean> isKeyFPressed = new HashMap<>();
    private final Map<UUID, Boolean> isKeyBPressed = new HashMap<>();
    private final Map<UUID, Boolean> isKeyVPressed = new HashMap<>();
    private final Map<UUID, Boolean> isKeyRPressed = new HashMap<>();

    // Multipliers for different abilities
    private final double shieldStrengthMultiplier = 0.05; // 5% increase per level
    private final double wardDurationMultiplier = 0.05;   // 5% increase per level
    private final double barrierStrengthMultiplier = 0.07; // 7% increase per level
    private final double manaDrainEffectiveness = 0.03;    // 3% more effective mana drain per level

    public DefensiveMagicSkill(String name, PlayerManaManager manaManager, Plugin plugin) {
        super(name);
        this.manaManager = manaManager;
        this.plugin = plugin;
        initializeKeyTracking();
    }

    private void initializeKeyTracking() {
        ProtocolManager protocolManager = ProtocolLibrary.getProtocolManager();

// Listen for the necessary packets
        PacketListener packetListener = new PacketAdapter(plugin, PacketType.Play.Client.STEER_VEHICLE, PacketType.Play.Client.USE_ENTITY, PacketType.Play.Client.BLOCK_DIG) {
            @Override
            public void onPacketReceiving(PacketEvent event) {
                Player player = event.getPlayer();
                UUID playerUUID = player.getUniqueId();

                if (event.getPacketType() == PacketType.Play.Client.STEER_VEHICLE) {
                    handleSteerVehiclePacket(event, playerUUID);
                } else if (event.getPacketType() == PacketType.Play.Client.USE_ENTITY) {
                    handleUseEntityPacket(event, playerUUID);
                } else if (event.getPacketType() == PacketType.Play.Client.BLOCK_DIG) {
                    handleBlockDigPacket(event, playerUUID);
                }

                // Check for key combinations
                if (isKeyCombinationPressed(playerUUID, "S+E")) {
                    selectDefensiveStructure(player, "shield");
                } else if (isKeyCombinationPressed(playerUUID, "W+F")) {
                    selectDefensiveStructure(player, "ward");
                } else if (isKeyCombinationPressed(playerUUID, "B+F")) {
                    selectDefensiveStructure(player, "barrier");
                } else if (isKeyCombinationPressed(playerUUID, "V+R")) {
                    selectDefensiveStructure(player, "manadrain");
                }
            }
        };

        protocolManager.addPacketListener(packetListener);

    }

    private void handleSteerVehiclePacket(PacketEvent event, UUID playerUUID) {
        // Use this packet to detect W and S key presses
        float forward = event.getPacket().getFloat().read(0);
        float side = event.getPacket().getFloat().read(1);

        updateKeyState(playerUUID, "W", forward > 0);
        updateKeyState(playerUUID, "S", forward < 0);
        // Handle other keys as necessary
    }

    private void handleUseEntityPacket(PacketEvent event, UUID playerUUID) {
        // Use this packet to detect interactions, which might map to certain key actions
        // Example: Detect right-click to bind to key E
        updateKeyState(playerUUID, "E", true);
    }

    private void handleBlockDigPacket(PacketEvent event, UUID playerUUID) {
        // Detect actions like left-click
        EnumWrappers.PlayerDigType digType = event.getPacket().getPlayerDigTypes().read(0);

        if (digType == EnumWrappers.PlayerDigType.START_DESTROY_BLOCK) {
            updateKeyState(playerUUID, "F", true);
        }
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
        ManaDrain manaDrain = new ManaDrain(player, effectiveness, player.getTargetEntity(10));
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
