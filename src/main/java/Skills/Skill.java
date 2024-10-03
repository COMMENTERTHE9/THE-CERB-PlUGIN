package Skills;

import cerberus.world.cerb.CustomPlayer;
import org.bukkit.entity.Player;

public abstract class Skill {
    private String name;
    private double experience;
    private int level;

    public Skill(String name) {
        this.name = name;
        this.experience = 0;
        this.level = 1;
    }

    public void addExperience(double amount) {
        // Call the more detailed method with default values
        addExperience(amount, 0, 0, false, false);
    }

    public void addExperience(double amount, double health, double damage, boolean isRare, boolean isBoss) {
        this.experience += amount;
        checkLevelUp();
    }

    private void checkLevelUp() {
        double xpNeeded = calculateXpNeeded();
        while (this.experience >= xpNeeded) {
            this.experience -= xpNeeded;
            this.level++;
            xpNeeded = calculateXpNeeded();
        }
    }

    private double calculateXpNeeded() {
        return 100 * Math.pow(1.2, this.level - 1);
    }

    public double getEffectMultiplier() {
        return 1 + (this.level - 1) * 0.05;
    }

    public int getLevel() {
        return level;
    }

    public double getExperience() {
        return experience;
    }

    // Apply effect for Bukkit player
    public abstract void applyEffect(Player player);

    // Apply effect for CustomPlayer
    public abstract void applyEffect(CustomPlayer player);

    @Override
    public String toString() {
        return name + " (Level " + level + ", XP: " + experience + ")";
    }
}
