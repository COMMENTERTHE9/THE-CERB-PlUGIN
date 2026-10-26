package Skills;

import java.util.ArrayList;
import java.util.List;

/**
 * One thing worth celebrating (docs/design/LEVEL_UP.md): a skill level, a skill milestone, a class rank, an ability that
 * became available, and later a realm breakthrough, a mastery trial or an Awakening. Pure data: {@link Celebrations}
 * decides how it looks and sounds from its kind and tier. Texts carry legacy colour codes (section signs).
 *
 * @param hud      the line for the middle of the HUD bar when it is shown alone ("▲ Blade Mastery 12 · +0.9 Power")
 * @param brief    its part of a combined HUD line ("▲ Blade Mastery 12"), or null for {@code hud}
 * @param title    the big text for a MAJOR or GRAND one, or null
 * @param subtitle the line under it, or null
 * @param chat     the lines kept in chat
 * @param rgb      the colour of its particles (a class's colour), or -1 for the tier's own
 */
public record Celebration(Kind kind, Tier tier, String hud, String brief, String title, String subtitle, List<String> chat, int rgb) {

    public enum Kind { SKILL_LEVEL, SKILL_MILESTONE, CLASS_RANK, ABILITY, TECHNIQUE, DISCOVERY, REALM_BREAKTHROUGH, MASTERY_TRIAL, AWAKENING }

    /** How strong the presentation is. GRAND is for realm breakthroughs, mastery trials and Awakenings (nothing uses it yet). */
    public enum Tier { ORDINARY, MAJOR, GRAND }

    public Celebration {
        chat = chat == null ? List.of() : List.copyOf(chat);
        if (brief == null) brief = hud;
    }

    /**
     * Everything a player earned in the same moment, shown once: the strongest tier's sound, particles and title, one HUD
     * line (the full one when alone, else each one's brief part, at most {@link #HUD_PARTS}, then "+N more"), and every
     * chat line.
     */
    public record Batch(Tier tier, Kind kind, String hud, String title, String subtitle, List<String> chat, int rgb) {}

    static final int HUD_PARTS = 3;

    /** Combine {@code all} (in the order earned) into one presentation; null for none. Pure. */
    public static Batch combine(List<Celebration> all) {
        if (all == null || all.isEmpty()) return null;
        Celebration top = all.get(0);
        for (Celebration c : all) if (c.tier().ordinal() > top.tier().ordinal()) top = c;
        Celebration titled = null;
        for (Celebration c : all) {
            if (c.title() != null && c.tier() == top.tier()) { titled = c; break; }
        }
        List<Celebration> shown = new ArrayList<>();
        for (Celebration c : all) if (c.hud() != null && !c.hud().isEmpty()) shown.add(c);
        StringBuilder hud = new StringBuilder();
        if (shown.size() == 1) hud.append(shown.get(0).hud());
        else {
            for (int i = 0; i < Math.min(HUD_PARTS, shown.size()); i++) {
                if (i > 0) hud.append("§7 · ");
                hud.append(shown.get(i).brief());
            }
            if (shown.size() > HUD_PARTS) hud.append("§7 · +").append(shown.size() - HUD_PARTS).append(" more");
        }
        List<String> chat = new ArrayList<>();
        for (Celebration c : all) chat.addAll(c.chat());
        return new Batch(top.tier(), top.kind(), hud.toString(), titled == null ? null : titled.title(),
                titled == null ? null : titled.subtitle(), List.copyOf(chat), top.rgb());
    }
}
