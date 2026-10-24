package GUIs.admin;

import Listener.PlayerSession;
import Manager.DefenseBarManager;
import Manager.HealthStateSnapshot;
import Skills.SkillManager;
import cerberus.world.cerb.CerberusPlugin;
import org.bukkit.entity.Player;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Read-only snapshot of a player's Cerberus state for display. Reads only the plugin's
 * concurrent per-player maps (safe from the admin's thread); never mutates anything.
 */
record PlayerVitals(String name, double hp, double maxHp, String lifecycle, double mana, double maxMana,
                    double manaRegen, double defense, double maxDefense, double armorReduction,
                    double fireRes, double coldRes, double drownRes, int totalLevel, String session,
                    Map<String, Integer> topSkills) {

    static PlayerVitals of(CerberusPlugin plugin, Player p) {
        var vh = plugin.getPlayerVirtualHealthManager();
        HealthStateSnapshot hs = vh.snapshot(p);
        var mana = plugin.getPlayerManaManager();
        DefenseBarManager.DefenseBar bar = plugin.getDefenseBarManager().getDefenseBar(p);
        SkillManager sm = plugin.getSkillManager();
        PlayerSession s = plugin.getPlayerSessionManager() == null ? null : plugin.getPlayerSessionManager().getSession(p.getUniqueId());

        Map<String, Integer> top = new LinkedHashMap<>();
        sm.getPlayerSkillLevels(p).entrySet().stream()
                .filter(e -> sm.getSkills().containsKey(e.getKey()))
                .sorted(Map.Entry.<String, Integer>comparingByValue(Comparator.reverseOrder()).thenComparing(Map.Entry.comparingByKey()))
                .limit(5)
                .forEach(e -> top.put(e.getKey(), e.getValue()));

        return new PlayerVitals(p.getName(), hs.currentHp(), hs.maxHp(), hs.lifecycleState().name(),
                mana.getCurrentMana(p), mana.getMaxMana(p), mana.getManaRegenRate(p),
                bar.getCurrent(), bar.getMaximum(), vh.getArmorReduction(p),
                vh.getPlayerFireResistance(p), vh.getPlayerColdResistance(p), vh.getPlayerDrowningResistance(p),
                sm.getPlayerLevel(p), s == null ? "NONE" : s.getState().name(), top);
    }
}
