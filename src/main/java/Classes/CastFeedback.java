package Classes;

import Manager.PlayerHUDManager;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Shows what an ability key did (docs/design/ABILITY_CONTROLS.md, feedback): a short message in the
 * middle of the HUD bar, where the HUD's own refreshes can't erase it (health and Essence stay
 * around it), and for a refused cast a quiet sound only the caster hears, a different one per
 * reason, so a player can tell "on cooldown" from "no Essence" from "wrong item" without reading.
 * Nothing goes to chat. A cast that went off already plays its own sound for everyone nearby.
 * Player's thread.
 */
public final class CastFeedback {

    static final long CAST_MS = 1_600;
    static final long REFUSED_MS = 1_400;
    /** Refusal sounds at most this often: a held right-click repeats five times a second. */
    static final long SOUND_GAP_MS = 350;

    private final PlayerHUDManager hud;
    private final Map<UUID, Long> lastSound = new ConcurrentHashMap<>();

    public CastFeedback(PlayerHUDManager hud) {
        this.hud = hud;
    }

    /** Show {@code r}; {@code key} names the key pressed ("F", "Sneak + right-click") for an empty slot. */
    public void show(Player p, CastResult r, String key) {
        String text = text(r, key);
        if (text == null) return;
        hud.flash(p, text, r.cast() ? CAST_MS : REFUSED_MS);
        if (!r.cast()) refusalSound(p, r.outcome());
    }

    /** Something an ability did on its own a moment later (a Riposte's counter). */
    public void event(Player p, String legacyText) {
        hud.flash(p, legacyText, CAST_MS);
    }

    /** A free-form line (combo progress, hints), shown for {@code ms}. */
    public void line(Player p, String legacyText, long ms) {
        hud.flash(p, legacyText, ms);
    }

    public void clear(Player p) {
        hud.clearFlash(p);
    }

    public void forget(UUID id) {
        lastSound.remove(id);
    }

    /** The bar text for a result, or null when nothing is shown. Section-sign colours. */
    static String text(CastResult r, String key) {
        String name = r.ability() == null ? "" : r.ability().displayName();
        String d = r.detail() == null ? "" : r.detail();
        return switch (r.outcome()) {
            case CAST -> "§6✦ " + name + (d.isEmpty() ? "" : " §7· §f" + d);
            case COOLDOWN -> "§7" + name + " §f" + AbilityCaster.seconds(r.remainingMs());
            case NO_ESSENCE -> "§b" + name + " §cneeds " + d + " Essence";
            case NO_RESOURCE -> "§6" + name + " §cneeds " + d;
            case WRONG_ITEM -> "§c" + name + " needs " + d;
            case NO_TARGET -> "§c" + name + ": no target within " + d;
            case BLOCKED -> "§c" + name + ": no room";
            case NOT_UNLOCKED -> "§c" + name + " unlocks at " + d;
            case EMPTY -> "§7Nothing on " + key + " §8· set it in Classes & Abilities";
            case SWITCHING -> "§7Switching loadouts...";
            case UNAVAILABLE -> "§c" + name + " is unavailable (" + d + ")";
            case CANNOT_ACT -> null;
        };
    }

    private void refusalSound(Player p, CastResult.Outcome o) {
        long now = System.currentTimeMillis();
        Long last = lastSound.get(p.getUniqueId());
        if (last != null && now - last < SOUND_GAP_MS) return;
        lastSound.put(p.getUniqueId(), now);
        switch (o) {
            case COOLDOWN -> p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_HAT, SoundCategory.PLAYERS, 0.45f, 0.7f);
            case NO_ESSENCE -> {
                p.playSound(p.getLocation(), Sound.BLOCK_FIRE_EXTINGUISH, SoundCategory.PLAYERS, 0.35f, 1.8f);
                p.spawnParticle(Particle.SMOKE, p.getEyeLocation().add(p.getEyeLocation().getDirection().multiply(0.7)), 5, 0.1, 0.1, 0.1, 0.01);
            }
            case WRONG_ITEM, NO_RESOURCE -> p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, SoundCategory.PLAYERS, 0.6f, 0.6f);
            case NO_TARGET, BLOCKED -> p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_ATTACK_NODAMAGE, SoundCategory.PLAYERS, 0.7f, 1.0f);
            case NOT_UNLOCKED, EMPTY, SWITCHING, UNAVAILABLE -> p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, SoundCategory.PLAYERS, 0.4f, 0.5f);
            case CAST, CANNOT_ACT -> { }
        }
    }
}
