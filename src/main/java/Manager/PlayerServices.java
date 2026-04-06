package Manager;

import GUIs.AdminChestGUI;
import GUIs.PlayerMenuGUI;
import GUIs.SkillGUI;
import Skills.SkillManager;
import cerberus.world.cerb.DatabaseManager;

/**
 * Bundles all manager references into a single object.
 * CerberusPlugin builds this once; adding a new manager means
 * one field here — no constructor changes downstream.
 */
public record PlayerServices(
        DatabaseManager database,
        SkillManager skills,
        PlayerVirtualHealthManager virtualHealth,
        PlayerDefenseManager defense,
        PlayerManaManager mana,
        PlayerHUDManager hud,
        PlayerStrengthManager strength,
        EffectManager effects,
        DefenseBarManager defenseBar,
        SkillGUI skillGUI,
        PlayerMenuGUI playerMenuGUI,
        AdminChestGUI adminChestGUI,
        MobRegistry mobRegistry
) {}
