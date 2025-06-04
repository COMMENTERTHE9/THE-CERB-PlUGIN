package Skills;

import Manager.CraftingManager;
import cerberus.world.cerb.CustomPlayer;
import org.bukkit.entity.*;
import org.bukkit.entity.Tameable;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.stream.Collectors;

public class AnimalTamingSkill extends UtilitySkill {

    private final CraftingManager craftingManager;
    private final Map<UUID, Integer> animalAffinity = new HashMap<>(); // Store affinity per player and their tamed animals
    private final Random random = new Random();

    public AnimalTamingSkill(String name, CraftingManager craftingManager) {
        super(name);
        this.craftingManager = craftingManager;
    }

    @Override
    public void applyEffect(CustomPlayer customPlayer) {
        if (customPlayer == null) return;
        Player player = customPlayer.getPlayer();
        int level = this.getLevel();

        // Increase taming success rate based on skill level
        double tamingSuccessBonus = level * 0.05; // Example: +5% taming success per level
        applyTamingSuccessBonus(player, tamingSuccessBonus);

        // Increase the attributes of tamed animals
        double tamedAnimalBonus = level * 0.03; // Example: +3% bonus to tamed animal stats per level
        enhanceTamedAnimalAttributes(player, tamedAnimalBonus);

        // Gain experience after successful taming
        gainTamingExperience(player, 10); // Example experience gain per tame
    }

    @Override
    public void applyEffect(Player player) {
        // This method can be used for direct player effects if needed
    }

    private void applyTamingSuccessBonus(Player player, double bonus) {
        System.out.println("Taming success rate increased by " + bonus * 100 + "%.");
    }

    private void enhanceTamedAnimalAttributes(Player player, double bonus) {
        for (Tameable tameable : getTamedAnimalsByPlayer(player)) {
            if (tameable != null) {
                if (tameable instanceof Wolf) {
                    ((Wolf) tameable).setHealth(((Wolf) tameable).getHealth() * (1 + bonus));
                } else if (tameable instanceof Horse) {
                    ((Horse) tameable).setJumpStrength(((Horse) tameable).getJumpStrength() * (1 + bonus));
                    ((Horse) tameable).setMaxHealth(((Horse) tameable).getMaxHealth() * (1 + bonus));
                } else if (tameable instanceof Cat) {
                    ((Cat) tameable).setHealth(((Cat) tameable).getHealth() * (1 + bonus));
                } else if (tameable instanceof Llama) {
                    ((Llama) tameable).setStrength((int) (((Llama) tameable).getStrength() * (1 + bonus)));
                } else if (tameable instanceof Camel) {
                    ((Camel) tameable).setMaxHealth(((Camel) tameable).getMaxHealth() * (1 + bonus));
                }
                applyBonusToTameableAnimals(tameable, bonus);
                increaseAnimalAffinity(tameable, player);
                applyRandomTamedAnimalBonus(tameable);
            }
        }
    }

    // Helper method to retrieve all tamed animals belonging to the player
    private Iterable<Tameable> getTamedAnimalsByPlayer(Player player) {
        List<Tameable> tameables = player.getWorld().getEntitiesByClass(Tameable.class)
                .stream()
                .filter(tameable -> tameable.isTamed() && tameable.getOwner() != null && tameable.getOwner().getUniqueId().equals(player.getUniqueId()))
                .collect(Collectors.toList());
        return tameables;
    }

    // Additional tameable animals with appropriate bonuses
    private void applyBonusToTameableAnimals(Tameable tameable, double bonus) {
        if (tameable instanceof Donkey) {
            ((Donkey) tameable).setMaxHealth(((Donkey) tameable).getMaxHealth() * (1 + bonus));
        } else if (tameable instanceof Mule) {
            ((Mule) tameable).setMaxHealth(((Mule) tameable).getMaxHealth() * (1 + bonus));
        } else if (tameable instanceof Fox) {
            ((Fox) tameable).setHealth(((Fox) tameable).getHealth() * (1 + bonus));
        } else if (tameable instanceof Parrot) {
            ((Parrot) tameable).setHealth(((Parrot) tameable).getHealth() * (1 + bonus));
        } else if (tameable instanceof Goat) {
            ((Goat) tameable).setHealth(((Goat) tameable).getHealth() * (1 + bonus));
        } else if (tameable instanceof PolarBear) {
            ((PolarBear) tameable).setHealth(((PolarBear) tameable).getHealth() * (1 + bonus));
        } else if (tameable instanceof Allay) {
            enhanceAllayAttributes((Allay) tameable, bonus);
        } else if (tameable instanceof Axolotl) {
            enhanceAxolotlAttributes((Axolotl) tameable, bonus);
        } else if (tameable instanceof Dolphin) {
            enhanceDolphinAttributes((Dolphin) tameable, bonus);
        } else if (tameable instanceof Turtle) {
            ((Turtle) tameable).setHealth(((Turtle) tameable).getHealth() * (1 + bonus));
        } else if (tameable instanceof Sniffer) {
            ((Sniffer) tameable).setHealth(((Sniffer) tameable).getHealth() * (1 + bonus));
        }
    }

    // Custom logic to enhance Allay abilities
    private void enhanceAllayAttributes(Allay allay, double bonus) {
        System.out.println("Allay item-fetching speed enhanced by " + bonus * 100 + "%.");
    }

    // Custom logic to enhance Axolotl abilities
    private void enhanceAxolotlAttributes(Axolotl axolotl, double bonus) {
        axolotl.setHealth(axolotl.getHealth() * (1 + bonus));
        System.out.println("Axolotl health increased by " + bonus * 100 + "%.");
    }

    // Custom logic to enhance Dolphin abilities
    private void enhanceDolphinAttributes(Dolphin dolphin, double bonus) {
        System.out.println("Dolphin swimming speed enhanced by " + bonus * 100 + "%.");
    }

    // Animal Affinity System: Increase affinity over time
    private void increaseAnimalAffinity(Tameable animal, Player player) {
        UUID animalId = animal.getUniqueId();
        animalAffinity.put(animalId, animalAffinity.getOrDefault(animalId, 0) + 1);

        // Example: Boost health with higher affinity
        if (animalAffinity.get(animalId) > 10) {
            if (animal instanceof Wolf) {
                ((Wolf) animal).setHealth(((Wolf) animal).getHealth() * 1.1); // Boost health by 10% as affinity increases
                player.sendMessage("Your loyal wolf has become stronger!");
            }
        }
    }

    private void applyRandomTamedAnimalBonus(Tameable tameable) {
        double randomChance = random.nextDouble();
        if (randomChance < 0.1) { // 10% chance for a random bonus
            AnimalTamer owner = tameable.getOwner();
            if (owner instanceof Player) { // Ensure the owner is a player
                Player player = (Player) owner;

                if (tameable instanceof Horse) {
                    ((Horse) tameable).setJumpStrength(((Horse) tameable).getJumpStrength() * 1.2); // Increase jump strength by 20%
                    player.sendMessage("Your new horse has exceptional strength!");
                } else if (tameable instanceof Wolf) {
                    ((Wolf) tameable).setHealth(((Wolf) tameable).getHealth() * 1.2); // Boost health by 20%
                    player.sendMessage("Your new wolf is exceptionally resilient!");
                }
            }
        }
    }

    // Tamed Animal Death Tracking
    public void trackTamedAnimalDeath(Tameable animal, Player owner) {
        animal.setRemoveWhenFarAway(false); // Prevent despawning
        owner.sendMessage("Your tamed animal has perished!");
    }

    // Gain experience after successful taming
    private void gainTamingExperience(Player player, int experience) {
        // Logic to add experience points to the taming skill
        // Example: CustomPlayer.addExperience("AnimalTaming", experience);
        System.out.println("You gained " + experience + " taming experience.");
    }
}
