package Effects;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;

import java.util.List;

/**
 * The player's vanilla potion effects (a potion, a beacon, a golden apple, a command), with their level and time left as
 * the server keeps them, described as they work under Cerberus ({@link EffectsText#vanilla}). Player's thread.
 */
public final class VanillaEffects implements EffectProvider {

    @Override
    public void collect(Player p, long now, List<ActiveEffect> out) {
        for (PotionEffect pe : p.getActivePotionEffects()) {
            String key = pe.getType().getKey().getKey();
            int level = pe.getAmplifier() + 1;
            boolean infinite = pe.isInfinite();
            // Remaining ticks as the server counts them now; at 20 ticks a second (a lagging region counts slower).
            long ends = infinite ? ActiveEffect.NO_END : now + pe.getDuration() * 50L;
            if (!infinite && pe.getDuration() <= 0) continue;
            String name = EffectsText.vanillaName(key) + (level > 1 ? " " + EffectsText.roman(level) : "");
            String source = pe.isAmbient() ? "Beacon or conduit (vanilla)" : "Potion effect (vanilla)";
            out.add(new ActiveEffect("potion:" + key, ActiveEffect.Category.POTION, name, source, Material.POTION, ends,
                    infinite ? "until removed" : null, List.of(), EffectsText.vanilla(key, level), null));
        }
    }
}
