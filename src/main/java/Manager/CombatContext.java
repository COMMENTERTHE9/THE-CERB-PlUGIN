package Manager;

import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Disciplined mutable data carrier flowing through the damage pipeline.
 * Structured with sections (AttackState, DefenseState, PostDamageState)
 * and controlled mutation — not a bag of public fields.
 * <p>
 * Constructor sets identity fields only. Each section is mutated by exactly
 * one phase of the pipeline.
 */
public class CombatContext {

    // ─── Identity (final, set in constructor) ───────────────────
    private final @Nullable Player attacker;
    private final LivingEntity target;
    private final CustomDamageType damageType;
    private final double rawDamage;

    // ─── Sections ───────────────────────────────────────────────
    private final AttackState attack = new AttackState();
    private final DefenseState defense = new DefenseState();
    private final PostDamageState postDamage = new PostDamageState();

    // ─── Debug Trace ────────────────────────────────────────────
    private final List<TraceEntry> trace = new ArrayList<>();

    // ─────────────────────────────────────────────────────────────
    // Constructor
    // ─────────────────────────────────────────────────────────────

    public CombatContext(@Nullable Player attacker, LivingEntity target,
                         CustomDamageType damageType, double rawDamage) {
        this.attacker = attacker;
        this.target = target;
        this.damageType = damageType;
        this.rawDamage = rawDamage;
        // Initialize modifiedDamage to rawDamage — if no skills modify it, it passes through unchanged
        this.attack.modifiedDamage = rawDamage;
    }

    // ─────────────────────────────────────────────────────────────
    // Identity getters
    // ─────────────────────────────────────────────────────────────

    public @Nullable Player getAttacker() { return attacker; }
    public LivingEntity getTarget() { return target; }
    public CustomDamageType getDamageType() { return damageType; }
    public double getRawDamage() { return rawDamage; }

    // ─────────────────────────────────────────────────────────────
    // Section accessors
    // ─────────────────────────────────────────────────────────────

    public AttackState attack() { return attack; }
    public DefenseState defense() { return defense; }
    public PostDamageState postDamage() { return postDamage; }

    // ─────────────────────────────────────────────────────────────
    // Trace / Debug
    // ─────────────────────────────────────────────────────────────

    /** Append a structured trace entry for this pipeline step. */
    public void appendTrace(String step, double before, double after, @Nullable String note) {
        trace.add(new TraceEntry(step, before, after, note));
    }

    /** Unmodifiable view of all trace entries. */
    public List<TraceEntry> getTrace() {
        return Collections.unmodifiableList(trace);
    }

    /** Render the full trace as a single formatted string for chat/debug output. */
    public String getTraceAsString() {
        if (trace.isEmpty()) return "[Combat] No trace data";

        StringBuilder sb = new StringBuilder("[Combat] ");
        for (int i = 0; i < trace.size(); i++) {
            if (i > 0) sb.append(" → ");
            sb.append(trace.get(i).format());
        }
        return sb.toString();
    }

    // ═════════════════════════════════════════════════════════════
    // Inner: AttackState
    // Set by CombatSkillProcessor.processAttackerSkills()
    // ═════════════════════════════════════════════════════════════

    public static class AttackState {
        private double modifiedDamage;
        private boolean isCritical;
        private double critMultiplier = 1.0;

        // Getters
        public double getModifiedDamage() { return modifiedDamage; }
        public boolean isCritical() { return isCritical; }
        public double getCritMultiplier() { return critMultiplier; }

        // Setters (package-private for pipeline phases)
        void setModifiedDamage(double v) { this.modifiedDamage = v; }
        void setCritical(boolean v) { this.isCritical = v; }
        void setCritMultiplier(double v) { this.critMultiplier = v; }
    }

    // ═════════════════════════════════════════════════════════════
    // Inner: DefenseState
    // Set by PlayerVirtualHealthManager.applyDamage() pipeline
    // 3-layer system: elemental resist → armor → defense bar → health
    // ═════════════════════════════════════════════════════════════

    public static class DefenseState {
        private double afterElementalResist;
        private double afterArmor;
        private double afterDefenseAbsorption;
        private double defenseAbsorptionRate;
        private double finalDamage;

        // Getters
        public double getAfterElementalResist() { return afterElementalResist; }
        public double getAfterArmor() { return afterArmor; }
        public double getAfterDefenseAbsorption() { return afterDefenseAbsorption; }
        public double getDefenseAbsorptionRate() { return defenseAbsorptionRate; }
        public double getFinalDamage() { return finalDamage; }

        // Setters (package-private for pipeline phases)
        void setAfterElementalResist(double v) { this.afterElementalResist = v; }
        void setAfterArmor(double v) { this.afterArmor = v; }
        void setAfterDefenseAbsorption(double v) { this.afterDefenseAbsorption = v; }
        void setDefenseAbsorptionRate(double v) { this.defenseAbsorptionRate = v; }
        void setFinalDamage(double v) { this.finalDamage = v; }
    }

    // ═════════════════════════════════════════════════════════════
    // Inner: PostDamageState
    // Set by CombatSkillProcessor.applyPostDamageEffects()
    // ═════════════════════════════════════════════════════════════

    public static class PostDamageState {
        private double lifestealAmount;
        private double bonusElementalDamage;
        private @Nullable CustomDamageType elementalType;
        private @Nullable Vector knockbackDirection;
        private boolean disarmTriggered;

        // Getters
        public double getLifestealAmount() { return lifestealAmount; }
        public double getBonusElementalDamage() { return bonusElementalDamage; }
        public @Nullable CustomDamageType getElementalType() { return elementalType; }
        public @Nullable Vector getKnockbackDirection() { return knockbackDirection; }
        public boolean isDisarmTriggered() { return disarmTriggered; }

        // Setters (package-private for pipeline phases)
        void setLifestealAmount(double v) { this.lifestealAmount = v; }
        void setBonusElementalDamage(double v) { this.bonusElementalDamage = v; }
        void setElementalType(@Nullable CustomDamageType v) { this.elementalType = v; }
        void setKnockbackDirection(@Nullable Vector v) { this.knockbackDirection = v; }
        void setDisarmTriggered(boolean v) { this.disarmTriggered = v; }
    }

    // ═════════════════════════════════════════════════════════════
    // Inner: TraceEntry
    // Structured trace for pipeline inspection and debug display.
    // ═════════════════════════════════════════════════════════════

    /** Structured trace entry for pipeline inspection. */
    public record TraceEntry(String step, double before, double after, @Nullable String note) {
        /** Format for chat/debug output. */
        public String format() {
            if (note != null) {
                return String.format("%s(%s): %.1f", step, note, after);
            }
            return String.format("%s: %.1f", step, after);
        }
    }
}
