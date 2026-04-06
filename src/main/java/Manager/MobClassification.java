package Manager;

/**
 * Top-level classification for registered mobs.
 * Standalone enum so any system (loot, scoreboards, spawn commands)
 * can reference it without importing the full MobContext.
 */
public enum MobClassification {
    BOSS,
    RARE,
    NORMAL
}
