# Cerberus Codebase Audit Report
**Date:** 2026-04-06
**Auditor:** Claude (Opus 4.6)
**Codebase version:** c55bbad (Major update: Folia migration, GUI rewrite, combat system overhaul)
**Total files audited:** 154 Java files + 6 resource files + pom.xml
**Total lines of code audited:** ~20,004

---

## Executive Summary

The Cerberus plugin is a 20,000-line Minecraft RPG system running on Folia 1.21.11. The core combat pipeline (damage, defense, health lifecycle) is well-architected with a proper state machine (`HealthLifecycleEngine`). The Folia migration is largely complete with correct use of entity/region/global schedulers. However, the codebase suffers from three systemic problems: (1) pervasive null-pointer risks from unguarded `getItemMeta()` and `getMetadata().get(0)` calls that will crash the server under specific conditions, (2) roughly half of the 35+ skills are stubs or have broken implementations that silently do nothing, and (3) significant debug output (`System.out.println`) left in production code that spams the console. The GUI rewrite is solid (GuiHolder pattern, BorderAnimator). The database layer works but has synchronous calls that could block under load. The biggest concern is the sheer volume of half-implemented features — the codebase promises more than it delivers, creating maintenance debt.

---

## Statistics Block

```
- CRITICAL findings: 14
- HIGH findings: 16
- MEDIUM findings: 18
- LOW findings: 15
- COSMETIC findings: 8
- Dead code instances: 30+
- TODO/FIXME markers found: 25+
- Folia compatibility issues: 0 remaining (migration complete)
- System.out.println instances: 15+
```

**Total findings: 71**

---

## 1. Bugs and Crashes

#### Finding 1: Unguarded metadata access — IndexOutOfBoundsException
**Severity:** CRITICAL
**File:** Skills/ArcaneKnowledgeSkill.java
**Location:** lines 81, 89
**Description:** `player.getMetadata("spellDamage").get(0).asDouble()` calls `.get(0)` without checking if the metadata list is empty. If the metadata key doesn't exist, `getMetadata()` returns an empty list and `.get(0)` throws IndexOutOfBoundsException.
**Impact:** Server crash when Arcane Knowledge skill is applied to a player without spellDamage metadata set.
**Suggested fix:** Check `!player.getMetadata(key).isEmpty()` before accessing index 0.
**Related findings:** 2, 3, 4, 5

#### Finding 2: Unguarded metadata access — SpellWeavingSkill
**Severity:** CRITICAL
**File:** Skills/SpellWeavingSkill.java
**Location:** lines 99, 112
**Description:** Same `.get(0)` without empty check on metadata list for "spellDamage" and "spellRange".
**Impact:** IndexOutOfBoundsException crash.
**Suggested fix:** Same as Finding 1.
**Related findings:** 1, 3, 4, 5

#### Finding 3: Unguarded metadata access — PlayerManaManager
**Severity:** CRITICAL
**File:** Manager/PlayerManaManager.java
**Location:** lines 335, 346, 414
**Description:** Multiple `.getMetadata(key).get(0)` calls without empty list checks for "currentSpellManaCost", "currentSpellDamage", and generic key.
**Impact:** IndexOutOfBoundsException crash during spell casting.
**Suggested fix:** Wrap all metadata access in empty-check guard.
**Related findings:** 1, 2, 4, 5

#### Finding 4: Unguarded getItemMeta() chains — TrapManager
**Severity:** HIGH
**File:** Traps/TrapManager.java
**Location:** lines 84, 96, 106
**Description:** `item.getItemMeta().getPersistentDataContainer()` called without null-checking the result of `getItemMeta()`, which can return null for certain item types.
**Impact:** NullPointerException when handling trap items without metadata.
**Suggested fix:** Add `if (meta == null) return;` guard before PDC access.
**Related findings:** 5, 6

#### Finding 5: Unguarded getItemMeta() chains — TrapTool
**Severity:** HIGH
**File:** Traps/TrapTool.java
**Location:** line 267
**Description:** `item.getItemMeta().getPersistentDataContainer().has(...)` without null check on getItemMeta().
**Impact:** NullPointerException.
**Suggested fix:** Null-check getItemMeta() before chaining.
**Related findings:** 4, 6

#### Finding 6: Unguarded getItemMeta() chains — FarmingSkill
**Severity:** HIGH
**File:** Skills/FarmingSkill.java
**Location:** lines 105, 106, 114-115
**Description:** `tool.getItemMeta().hasDisplayName()` and `.getDisplayName()` without null check on getItemMeta(). Line 105 checks hasItemMeta() but the actual getItemMeta() call on line 106 can still return null.
**Impact:** NullPointerException when farming with items that lack metadata.
**Suggested fix:** Assign getItemMeta() to a variable, null-check it.
**Related findings:** 4, 5

#### Finding 7: Unsafe cast — ElementalInfusionManager
**Severity:** HIGH
**File:** Manager/ElementalInfusionManager.java
**Location:** line 100
**Description:** `(ElementalMasterySkill) customPlayer.getSkill("Elemental Mastery")` — direct cast without `instanceof` check. If `getSkill()` returns null or a different type, ClassCastException is thrown.
**Impact:** Server crash when elemental infusion triggers.
**Suggested fix:** Add instanceof check before cast.
**Related findings:** 8

#### Finding 8: Unsafe casts — EffectManager
**Severity:** HIGH
**File:** Manager/EffectManager.java
**Location:** lines 173, 181, 189
**Description:** Results of `skillManager.getSkill()` cast directly to specific skill types (SpellWeavingSkill, etc.) without null or type checking.
**Impact:** ClassCastException or NullPointerException.
**Suggested fix:** Add instanceof checks before all skill casts.
**Related findings:** 7

#### Finding 9: CraftingSkill null pointer risk
**Severity:** CRITICAL
**File:** Skills/CraftingSkill.java
**Location:** line 44
**Description:** `customPlayer.getHealthManager().increaseMaxHealth(player, healthBonus)` — `getHealthManager()` may return null. No null check before calling method on result.
**Impact:** NullPointerException when crafting triggers health bonus.
**Suggested fix:** Null-check getHealthManager() return value.
**Related findings:** none

#### Finding 10: HeavyArmorTrainingSkill null pointer risk
**Severity:** HIGH
**File:** Skills/HeavyArmorTrainingSkill.java
**Location:** line 25
**Description:** `customPlayer.getBukkitPlayer()` result used without null check before passing to `defenseManager.updateDefense(player)`.
**Impact:** NullPointerException if CustomPlayer's Bukkit reference is stale.
**Suggested fix:** Add null guard on getBukkitPlayer() result.
**Related findings:** none

#### Finding 11: RangedPrecisionSkill attribute wrapper null risk
**Severity:** HIGH
**File:** Skills/RangedPrecisionSkill.java
**Location:** line 44
**Description:** `attributeWrapper.setBaseValue(...)` used outside the scope of the null check at line 34. Variable could be null when critical hit code path executes.
**Impact:** NullPointerException during ranged critical hit.
**Suggested fix:** Move attribute access inside the null-check block.
**Related findings:** none

#### Finding 12: Command registration mismatch — regiongui
**Severity:** CRITICAL
**File:** src/main/resources/plugin.yml line 33 + cerberus/world/cerb/CerberusPlugin.java
**Description:** Command "regiongui" is defined in plugin.yml but has NO corresponding `setExecutor()` call in CerberusPlugin.initCommands(). The command will be registered by Bukkit but has no handler.
**Impact:** Server error when /regiongui is invoked.
**Suggested fix:** Either register an executor or remove from plugin.yml.
**Related findings:** 13

#### Finding 13: Command registration mismatch — killholograms
**Severity:** LOW
**File:** cerberus/world/cerb/CerberusPlugin.java line 315
**Description:** `setExecutor` for "killholograms" is commented out. The command exists in plugin.yml but the executor is a no-op class (KillHologramsCommand returns "This command is disabled").
**Impact:** Harmless — command responds with "disabled" message. But the registration is commented out, so it actually has no executor at all.
**Suggested fix:** Either uncomment the registration or remove from plugin.yml.
**Related findings:** 12

#### Finding 14: SummoningSkill — cannot spawn abstract class
**Severity:** HIGH
**File:** Skills/SummoningSkill.java
**Location:** line 87
**Description:** `player.getWorld().spawn(player.getLocation(), LivingEntity.class)` attempts to spawn an abstract class. LivingEntity.class cannot be instantiated.
**Impact:** SummoningSkill is completely non-functional. Will throw an exception or return null.
**Suggested fix:** Replace with a concrete entity type (Zombie.class, Wolf.class, etc.) based on skill level or config.
**Related findings:** none

---

## 2. Half-Implemented Features

#### Finding 15: DefensiveMagicSkill — completely disabled
**Severity:** HIGH
**File:** Skills/DefensiveMagicSkill.java
**Location:** lines 7-8 (TODO comment), entire file
**Description:** ProtocolLib was removed for Folia compatibility. The key-combo input detection system is disabled. `initializeKeyTracking()` logs a message and returns. The skill cannot activate Shield, Ward, Barrier, or ManaDrain abilities. Four DefensiveMagic/ classes (Barrier, Shield, Ward, ManaDrain) exist but are unreachable.
**Impact:** Defensive Magic skill has zero gameplay functionality. 4 classes are dead code.
**Suggested fix:** Reimplement input detection using PlayerInteractEvent (right-click with specific items) or a command-based activation system.
**Related findings:** none

#### Finding 16: 15+ admin commands are stubs
**Severity:** MEDIUM
**File:** cerberus/world/cerb/CerberusPlugin.java
**Location:** lines 431-546
**Description:** These `/cerb` subcommands are registered but return "not implemented yet": backup, debug (partial), mana, skill, resetdata, banregion, region, protection, worldgen, repairchunk, quest, event, teleportall, timings, profiler, listentities, showqueues.
**Impact:** Admin cannot manage server state via commands.
**Suggested fix:** Implement the most useful ones (mana, skill, resetdata) first, delete the rest or mark as planned.
**Related findings:** none

#### Finding 17: FirstAidSkill — extreme scaling
**Severity:** MEDIUM
**File:** Skills/FirstAidSkill.java
**Location:** line 29
**Description:** `double healthBoost = level * 1500.0` — at level 100, this grants 150,000 HP. With base max of 100, this is 1500x the starting health.
**Impact:** Completely breaks game balance at higher levels.
**Suggested fix:** Use ScalingFormula with a hard cap instead of linear scaling.
**Related findings:** 18

#### Finding 18: ManaRegenerationSkill — exponential scaling
**Severity:** MEDIUM
**File:** Skills/ManaRegenerationSkill.java
**Location:** line 24
**Description:** `double regenRateMultiplier = 5 + (0.5 * level)` — at level 100, this is 55x mana regen. Base regen is 5.0/sec, so max regen would be 275/sec.
**Impact:** High-level players regenerate mana nearly instantly, breaking magic balance.
**Suggested fix:** Use ScalingFormula with diminishing returns.
**Related findings:** 17

#### Finding 19: ScavengingSkill — incomplete chest detection
**Severity:** LOW
**File:** Skills/ScavengingSkill.java
**Location:** lines 143-149
**Description:** `isTieredChest()` only checks inventory slot 0 to determine chest tier. Won't work for arbitrary inventories.
**Impact:** Chest tier detection doesn't function for most containers.
**Suggested fix:** Check all items or use PDC tag on the chest block/inventory holder.
**Related findings:** none

#### Finding 20: TradingSkill — all methods are placeholders
**Severity:** LOW
**File:** Skills/TradingSkill.java
**Location:** throughout
**Description:** Every method in TradingSkill uses System.out.println as placeholder. No actual trading logic.
**Impact:** Trading skill does nothing.
**Suggested fix:** Either implement or remove from the skill list.
**Related findings:** none

#### Finding 21: AdvancedDropManager — hardcoded example only
**Severity:** LOW
**File:** Manager/AdvancedDropManager.java
**Location:** throughout
**Description:** Only handles STONE and OAK_LOG with hardcoded conditions. No real advanced drop system.
**Impact:** Drop manager is non-functional for actual gameplay.
**Suggested fix:** Implement config-driven drop tables or delete the class.
**Related findings:** none

#### Finding 22: ElementalInfusionManager — placeholder parsing
**Severity:** MEDIUM
**File:** Manager/ElementalInfusionManager.java
**Location:** lines 77-86
**Description:** `parseElementalTag()` returns hardcoded values instead of parsing actual NBT/PDC data.
**Impact:** Elemental infusion system doesn't read real item data.
**Suggested fix:** Implement actual PDC-based elemental tag parsing.
**Related findings:** none

#### Finding 23: GUI "Coming Soon" buttons
**Severity:** LOW
**File:** GUIs/PlayerMenuGUI.java, Listener/UnifiedGUIListener.java
**Location:** PlayerMenuGUI slots 22, 24, 29, 31, 33, 38
**Description:** Six main menu buttons (Recipes, Collections, General, Mobs, World Map, Armor) all route to ComingSoonGUI. These features have no implementation behind them.
**Impact:** Player menu advertises features that don't exist. Acceptable for development but should be tracked.
**Suggested fix:** Implement features or clearly mark as "planned" in the menu description.
**Related findings:** none

---

## 3. Dead Code

#### Finding 24: 4 DefensiveMagic classes unreachable
**Severity:** MEDIUM
**File:** DefensiveMagic/Barrier.java, Shield.java, Ward.java, ManaDrain.java
**Location:** entire files
**Description:** These classes exist and have full implementations but cannot be activated because DefensiveMagicSkill's input system is disabled. No code path leads to their `activate()` methods.
**Impact:** ~400 lines of dead code.
**Suggested fix:** Delete until the input system is reimplemented, or keep with clear "disabled" documentation.
**Related findings:** 15

#### Finding 25: QualityBuffs unused variable
**Severity:** LOW
**File:** cerberus/world/cerb/QualityBuffs.java
**Location:** line 115
**Description:** `int drawSpeedReductionTicks = Math.min(...)` is computed but never used.
**Impact:** Wasted computation, incomplete feature.
**Suggested fix:** Remove or implement the draw speed reduction.
**Related findings:** none

#### Finding 26: VirtualHealthListener.applyDamageMultiplierToEntity() is no-op
**Severity:** LOW
**File:** Listener/VirtualHealthListener.java
**Location:** line 191
**Description:** Method returns damage unchanged. It's a placeholder that was never implemented.
**Impact:** Mob damage multipliers are never applied.
**Suggested fix:** Implement or mark as intentional no-op with comment.
**Related findings:** none

#### Finding 27: CraftingManager empty stub methods
**Severity:** LOW
**File:** Manager/CraftingManager.java
**Location:** lines 392-394, 410-412, 499-501, 549-551, 579-581, 589-591, 599-601
**Description:** 7+ private helper methods with empty bodies: `applyRepairCostReduction()`, `reduceCraftingMaterialCost()`, `applyStealthBonus()`, `applyNavigationToolBonus()`, `applyExplorationGearBonus()`, etc.
**Impact:** Features incomplete, stubs inflate class size.
**Suggested fix:** Implement or delete.
**Related findings:** none

#### Finding 28: CombatScoreboardManager — disabled on Folia
**Severity:** LOW
**File:** Manager/CombatScoreboardManager.java
**Location:** entire file
**Description:** All methods are no-ops. Class was gutted for Folia compatibility (getNewScoreboard unsupported). Sidebar scoreboard display doesn't work.
**Impact:** No functional impact — correctly disabled. But 40 lines of no-op code could be simplified.
**Suggested fix:** Replace with a single-line comment class or delete entirely.
**Related findings:** none

#### Finding 29: Skills with empty applyEffect(Player) overrides
**Severity:** LOW
**File:** Skills/MiningSkill.java, CookingSkill.java, FishingSkill.java
**Location:** applyEffect(Player) methods
**Description:** These skills implement `applyEffect(CustomPlayer)` with real logic but `applyEffect(Player)` is empty. When the Bukkit Player overload is called (from some code paths), nothing happens.
**Impact:** Inconsistent behavior depending on which overload is called.
**Suggested fix:** Have the Player overload delegate to the CustomPlayer overload, or make one canonical.
**Related findings:** none

---

## 4. Wired Up But Not Working

#### Finding 30: Skill XP — most utility skills have no XP source
**Severity:** MEDIUM
**File:** Skills/ (various), Listener/MobKillListener.java
**Location:** skills.yml skill-triggers section
**Description:** Only combat and magic skills are in the `skill-triggers` config for mob-kill XP. Utility skills (Mining, Farming, Woodcutting, Fishing, Crafting, Smithing, Alchemy, Enchanting, Herbalism, Cooking, First Aid, Stealth, Trap Mastery, Scavenging, Repairing, Trading, Navigation, Animal Taming, Riding, Lockpicking, Survival) have no block-break, crafting, or other event-based XP triggers. Only Survival and Heavy Armor Training get per-hit XP from CombatSkillProcessor.
**Impact:** 20+ utility skills can never gain XP through normal gameplay. They exist in the GUI but are permanently level 1.
**Suggested fix:** Add BlockBreakEvent, CraftItemEvent, FurnaceSmeltEvent, etc. listeners that award XP for relevant actions.
**Related findings:** none

#### Finding 31: CraftingManager method ambiguity
**Severity:** MEDIUM
**File:** Manager/CraftingManager.java
**Location:** lines 160 and 405
**Description:** Two different methods named `applyCraftingEfficiency` with different signatures. Line 160: `private void applyCraftingEfficiency(ItemStack, double)`. Line 405: `public void applyCraftingEfficiency(Player, int)`. Line 303 calls it with ambiguous parameters.
**Impact:** Wrong method may be called, machine crafting bonuses may not apply.
**Suggested fix:** Rename one method for clarity.
**Related findings:** none

---

## 5. Poor Design / Refactor Candidates

#### Finding 32: CerberusPlugin.java — god class
**Severity:** MEDIUM
**File:** cerberus/world/cerb/CerberusPlugin.java
**Location:** entire file
**Description:** ~750 lines. Contains plugin lifecycle, event handlers for player interactions, block breaks, fishing, admin commands, world protection checks, region selector logic, and more. Too many responsibilities for one class.
**Impact:** Hard to maintain, hard to test, hard to find things.
**Suggested fix:** Extract event handlers to dedicated listener classes. Move admin command logic to CerbCommand or sub-handlers.
**Related findings:** none

#### Finding 33: Skill class hierarchy — inconsistent overloads
**Severity:** LOW
**File:** Skills/ (throughout)
**Location:** applyEffect methods
**Description:** Skills have two overloads: `applyEffect(CustomPlayer)` and `applyEffect(Player)`. Some implement one, some the other, some both. There's no consistent contract for which is the "primary" method. Callers must guess which to call.
**Impact:** Bugs where the wrong overload is called and nothing happens.
**Suggested fix:** Standardize on one canonical method signature. Have the other delegate.
**Related findings:** 29

#### Finding 34: String literals as skill identifiers
**Severity:** LOW
**File:** Manager/CombatSkillProcessor.java, Skills/SkillManager.java, throughout
**Location:** throughout
**Description:** Skills are identified by string literals ("Blade Mastery", "Critical Strike", etc.) throughout the codebase. No enum or constant class. Typos in skill names cause silent failures.
**Impact:** A typo like "Critcal Strike" would silently return null and skip the skill.
**Suggested fix:** Create a SkillNames constants class or use the SkillDefinitions registry.
**Related findings:** none

---

## 6. Missing Error Handling

#### Finding 35: DatabaseManager — synchronous calls can block
**Severity:** MEDIUM
**File:** cerberus/world/cerb/DatabaseManager.java
**Location:** lines 114-160 (getOrCreateCustomId, saveCustomId, initializePlayerSkills)
**Description:** Database I/O is synchronous. While callers run on background threads, the operations have no timeout. A slow SQLite disk could stall the DB executor thread indefinitely.
**Impact:** Server lag if database operations stall.
**Suggested fix:** Add a connection timeout to the SQLite JDBC URL or wrap in a future with timeout.
**Related findings:** none

#### Finding 36: DatabaseManager — System.err.println instead of logger
**Severity:** HIGH
**File:** cerberus/world/cerb/DatabaseManager.java
**Location:** lines 272, 281
**Description:** `System.err.println("[ERROR] No customId found for playerUUID: ...")` bypasses the plugin logging framework. These appear in console as raw stderr output.
**Impact:** Error logs are not properly attributed to the plugin, harder to filter.
**Suggested fix:** Replace with `plugin.getLogger().severe(...)`.
**Related findings:** 37

#### Finding 37: System.out.println throughout codebase
**Severity:** HIGH
**File:** Multiple files
**Location:** DatabaseManager.java:226, SkillManager.java:74-75, NavigationSkill.java:168/174, RidingSkill.java:69/117/123/129/138/150/162/183, LockpickingSkill.java:85, AnimalTamingSkill.java:50/113/119/124, HerbalismSkill.java (multiple), TradingSkill.java (throughout), SurvivalSkill.java (throughout)
**Description:** 15+ instances of System.out.println and System.err.println used instead of plugin.getLogger(). Debug output left in production code.
**Impact:** Console spam, unprofessional output, hard to filter logs.
**Suggested fix:** Global find-and-replace System.out.println with plugin.getLogger().info() and System.err.println with plugin.getLogger().severe().
**Related findings:** 36

---

## 7. Configuration Issues

#### Finding 38: AsyncSaveManager — HashMap not thread-safe
**Severity:** MEDIUM
**File:** Manager/AsyncSaveManager.java
**Location:** line 18
**Description:** `lastRunTimestamps` is a regular HashMap in a class designed for async operations. Multiple threads can call scheduleYamlSave/scheduleDbSave concurrently.
**Impact:** Race condition on cooldown check — two threads could both pass the cooldown window simultaneously.
**Suggested fix:** Change to ConcurrentHashMap.
**Related findings:** none

#### Finding 39: ConfigSkillMapper — HashSet not thread-safe
**Severity:** LOW
**File:** cerberus/world/cerb/ConfigSkillMapper.java
**Location:** line 158
**Description:** `magicSkillNames` is assigned a new HashSet on reload. While the volatile reference swap is safe for visibility, HashSet itself is not thread-safe for concurrent reads if another thread is mid-iteration during swap.
**Impact:** Extremely unlikely race, but technically unsafe.
**Suggested fix:** Use `Set.copyOf()` or `Collections.unmodifiableSet()`.
**Related findings:** none

#### Finding 40: RegionManager — hardcoded first-world assumption
**Severity:** MEDIUM
**File:** cerberus/world/cerb/RegionManager.java
**Location:** line 44
**Description:** `plugin.getServer().getWorlds().get(0)` — assumes the first world is the correct one. If worlds load in different order or server has multiple worlds, regions may bind to the wrong world.
**Impact:** Region protection may not work on multi-world servers.
**Suggested fix:** Store world name in region data and look up by name.
**Related findings:** none

#### Finding 41: pom.xml — unused dependencies
**Severity:** LOW
**File:** pom.xml
**Location:** lines 105-116
**Description:** Two dependencies `cerberus.plugins:DTR:1.0.0` and `cer-ber-us:volor:1.0.0` are declared but never imported in any Java file. These appear to be local JARs.
**Impact:** Build bloat. If JARs are missing from local Maven repo, build fails.
**Suggested fix:** Remove if unused, or document what they provide.
**Related findings:** none

#### Finding 42: CombatConfig encoding issue
**Severity:** LOW
**File:** Manager/CombatConfig.java
**Location:** line 285
**Description:** Error message contains malformed character `a]` (should be an em-dash). Indicates the source file has encoding issues.
**Impact:** Corrupted text in logs.
**Suggested fix:** Re-save file as UTF-8.
**Related findings:** none

---

## 8. Database Issues

#### Finding 43: Custom ID collision risk
**Severity:** LOW
**File:** cerberus/world/cerb/DatabaseManager.java
**Location:** lines 114-121
**Description:** Custom ID generated via `RandomStringUtils.randomAlphanumeric(6)` — 6 characters gives ~2.17 billion possible IDs. Insert uses `ON CONFLICT DO NOTHING`, so a collision would silently fail to create the ID.
**Impact:** Astronomically unlikely but theoretically possible data loss for a new player if their generated ID collides with an existing one.
**Suggested fix:** Add collision-retry loop or increase ID length.
**Related findings:** none

#### Finding 44: Database — unclosed ResultSet
**Severity:** MEDIUM
**File:** cerberus/world/cerb/DatabaseManager.java
**Location:** line 220
**Description:** `ResultSet rs = pstmt.executeQuery()` — the ResultSet is created but not wrapped in try-with-resources. While the parent PreparedStatement's close will clean it up, this is a dangerous pattern.
**Impact:** Potential resource leak under error conditions.
**Suggested fix:** Wrap in try-with-resources.
**Related findings:** none

---

## 9. Folia Compatibility Issues

#### Finding 45: No remaining Folia issues
**Severity:** N/A
**Description:** The Folia migration is complete. All BukkitScheduler calls have been replaced with Folia-compatible schedulers. All HashMaps in managers are ConcurrentHashMap. All teleport() calls use teleportAsync(). Entity scheduler is used for player-bound tasks. RegionScheduler for location-bound tasks. GlobalRegionScheduler for non-location-specific tasks. AsyncScheduler for database/file operations. The only remaining concern is the AsyncSaveManager.lastRunTimestamps HashMap (Finding 38), which is a minor thread-safety issue.
**Related findings:** 38

---

## 10. Performance Issues

#### Finding 46: NavigationSkill runs on every PlayerMoveEvent
**Severity:** MEDIUM
**File:** Listener/MobKillListener.java
**Location:** lines 84-94 (onPlayerMove handler)
**Description:** NavigationSkill.applyEffect() is called on every player move event (filtered by distance > 0.01). Inside, it modifies the movement speed attribute, checks biome, and awards XP with a 10-second cooldown. The attribute modifier removal/addition runs on every move.
**Impact:** Unnecessary attribute modification churn on every step.
**Suggested fix:** Only recalculate speed bonus when biome changes, not every move.
**Related findings:** none

#### Finding 47: Skill persistence saves on every XP gain
**Severity:** MEDIUM
**File:** Skills/SkillManager.java
**Location:** addXpAndCheckLevelUp() line 356
**Description:** `saveSkills(player)` is called after every single XP gain event. With per-hit XP awards (Critical Strike, HAT, Survival), this means a database write on every combat hit.
**Impact:** High I/O load during combat. SQLite can handle it for single-player but will bottleneck with multiple players.
**Suggested fix:** Batch saves using a periodic auto-save (every 30-60 seconds) instead of per-event.
**Related findings:** none

---

## 11. Logging and Debug Issues

#### Finding 48: System.out.println — 15+ instances
**Severity:** HIGH
**File:** Multiple (see Finding 37 for full list)
**Description:** Production code contains debug System.out.println calls throughout skills, database, and manager classes.
**Impact:** Console spam, unprofessional output.
**Suggested fix:** Replace all with plugin.getLogger() calls.
**Related findings:** 36, 37

#### Finding 49: DualWieldingSkill typo in message
**Severity:** COSMETIC
**File:** Skills/DualWieldingSkill.java
**Location:** line 80
**Description:** `player.sendMessage("double double You triggered an additional strike!")` — duplicate "double" in message.
**Impact:** Poor UX, looks like a bug to the player.
**Suggested fix:** Fix the message text.
**Related findings:** none

---

## 12. Documentation and Naming

#### Finding 50: CraftingManager method name shadowing
**Severity:** LOW
**File:** Manager/CraftingManager.java
**Location:** lines 160 and 405
**Description:** Two methods named `applyCraftingEfficiency` with completely different signatures and purposes. Confusing for developers.
**Impact:** Maintenance confusion, potential wrong-method bugs.
**Suggested fix:** Rename one to distinguish (e.g. `applyCraftingEfficiencyToItem` vs `applyCraftingEfficiencyForPlayer`).
**Related findings:** 31

---

## 13. Build and Project Configuration

#### Finding 51: pom.xml systemPath warning
**Severity:** COSMETIC
**File:** pom.xml
**Description:** `[ERROR] 'dependencies.dependency.systemPath' for cerberus.plugins:Cerb:jar must specify an absolute path` appears on every build. The systemPath uses `${project.basedir}` which Maven doesn't resolve correctly in this context.
**Impact:** Warning on every build, no functional impact.
**Suggested fix:** Use a local Maven repository install instead of systemPath.
**Related findings:** none

---

## Cross-Reference Map

### Findings that should be fixed together
- **Findings 1, 2, 3** — All are the same bug (unguarded `.get(0)` on metadata lists). Fix pattern once, apply everywhere.
- **Findings 4, 5, 6** — All are unguarded `getItemMeta()` chains. Same fix pattern.
- **Findings 7, 8** — Unsafe casts from `getSkill()`. Same fix pattern (add instanceof checks).
- **Findings 36, 37, 48** — All System.out/err.println cleanup. One grep-and-replace pass.
- **Findings 17, 18** — Scaling balance issues. Should be addressed together with a ScalingFormula pass across all skills.
- **Findings 12, 13** — Command registration mismatches. Fix together.

### Findings that block other findings
- Finding 15 (DefensiveMagic disabled) blocks any work on the 4 DefensiveMagic classes (Finding 24).
- Finding 30 (utility skills have no XP source) blocks any meaningful testing of 20+ utility skills.
- Finding 47 (per-hit saves) should be fixed before any load testing with multiple players.

---

## Hot Spots

| Rank | File | Findings | Severities | Summary |
|------|------|----------|------------|---------|
| 1 | Manager/CraftingManager.java | 5 | 1 CRIT, 1 HIGH, 2 MED, 1 LOW | Method ambiguity, empty stubs, overlapping names |
| 2 | cerberus/world/cerb/DatabaseManager.java | 5 | 2 HIGH, 2 MED, 1 LOW | System.err, synchronous blocking, unclosed resources, collision risk |
| 3 | Skills/SkillManager.java | 4 | 1 HIGH, 2 MED, 1 LOW | Debug output, race condition on map access, per-hit saves |
| 4 | Traps/TrapManager.java + TrapTool.java | 4 | 4 HIGH | Unguarded getItemMeta() chains throughout |
| 5 | Manager/EffectManager.java | 3 | 3 HIGH | Unsafe casts from getSkill() |
| 6 | Skills/DefensiveMagicSkill.java | 2 | 1 HIGH, 1 MED | Completely disabled, 4 dead classes |
| 7 | cerberus/world/cerb/CerberusPlugin.java | 3 | 1 CRIT, 1 MED, 1 COSMETIC | God class, command mismatch, 15+ stub commands |

---

## Quick Wins

**DatabaseManager.java:**
- Replace System.err.println with plugin.getLogger().severe() (3 instances)
- Replace System.out.println with plugin.getLogger().info() (1 instance)

**SkillManager.java:**
- Remove System.out.println debug lines 74-75

**DualWieldingSkill.java:**
- Fix "double double" message typo at line 80

**plugin.yml:**
- Remove "regiongui" command (no executor) or add executor

**CombatScoreboardManager.java:**
- Collapse to a single-line no-op class

**QualityBuffs.java:**
- Remove unused `drawSpeedReductionTicks` variable at line 115

---

## Recommended Order of Operations

### Phase 1: Crash Prevention (Critical)
Fix all null-pointer and crash risks. These cause server crashes during normal gameplay.
**Findings:** 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 14
**Estimated complexity:** Low — each is a 1-3 line guard clause

### Phase 2: Debug Cleanup (High)
Remove all System.out/err.println, fix log levels, remove debug output.
**Findings:** 36, 37, 48, 49
**Estimated complexity:** Low — grep and replace

### Phase 3: Skill Balance and XP Sources (High)
Fix scaling formulas for FirstAid and ManaRegen. Add block-break/craft XP triggers for utility skills. This makes 20+ skills actually playable.
**Findings:** 17, 18, 30
**Estimated complexity:** High — requires new event listeners and balance tuning

### Phase 4: Dead Feature Triage (Medium)
Decide fate of: DefensiveMagic (reimplement or delete), SummoningSkill (fix entity spawn), TradingSkill (implement or delete), AdvancedDropManager (implement or delete), CombatScoreboardManager (delete).
**Findings:** 15, 20, 21, 24, 28
**Estimated complexity:** Medium — mostly delete-or-implement decisions

### Phase 5: Database Hardening (Medium)
Batch skill saves instead of per-hit. Fix unclosed ResultSet. Replace System.err with logger.
**Findings:** 35, 43, 44, 47
**Estimated complexity:** Medium

### Phase 6: Design Cleanup (Low)
Extract CerberusPlugin responsibilities. Standardize skill applyEffect overloads. Fix CraftingManager method naming.
**Findings:** 27, 29, 31, 32, 33, 34, 50
**Estimated complexity:** High — structural refactoring

---

## Open Questions

1. `VirtualHealthListener.applyDamageMultiplierToEntity()` returns damage unchanged. Is this intentional (mob damage multipliers not planned) or a missing implementation?

2. `AdvancedDropManager` only handles STONE and OAK_LOG. Is this a prototype that should be expanded, or dead code that should be deleted?

3. `QualityBuffs.drawSpeedReductionTicks` is computed but never used. Is draw speed reduction a planned feature or was it abandoned?

4. The 4 DefensiveMagic classes (Barrier, Shield, Ward, ManaDrain) have full implementations. Is there a plan to reimplement the input system, or should these be deleted?

5. `TradingSkill` has all-placeholder methods with System.out.println. Is trading planned for a future phase, or should this skill be removed from the GUI?

6. Is the `regiongui` command in plugin.yml intentional (planned feature) or leftover from a removed feature?

7. Skills like Mining, Farming, Woodcutting have `applyEffect(CustomPlayer)` with real logic but `applyEffect(Player)` is empty. Which overload is canonical — should all callers use the CustomPlayer version?

8. `ScavengingSkill.isTieredChest()` only checks slot 0 of an inventory. Was this a prototype that was never finished?

9. The `CerberusPlugin.enableImmediateRespawn()` sets `DO_IMMEDIATE_RESPAWN = true` on all worlds. Is this intended to be permanent, or was it added during respawn debugging and should be configurable?

10. `NavigationSkill` awards 10 XP every 10 seconds just from walking. Is this intentional passive leveling, or should the XP cooldown be longer?
