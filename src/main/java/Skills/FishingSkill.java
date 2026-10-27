package Skills;

import org.bukkit.entity.Player;

/**
 * The Fishing skill's entry in the skill registry. Fishing itself (catches, species, lures, sea creatures) is
 * Gathering.Fishing, with its rules in resources.yml (docs/design/FISHING.md); its attributes come from the attribute
 * engine (attributes.yml). The old effect code here spawned an uncapped Sea Monster and was never reached.
 */
public class FishingSkill extends UtilitySkill {

    public FishingSkill(String name) {
        super(name);
    }

    @Override
    public void applyEffect(Player player) {
        // Nothing: see the class comment.
    }
}
