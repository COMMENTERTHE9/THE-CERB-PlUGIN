# Cerberus Codebase Audit

Date: 2026-06-07
Repo: C:\Users\Gabri\IdeaProjects\Cerb
Scope: Maven build, plugin lifecycle, player/session lifecycle, combat, skills, data persistence, local scripts, and repository hygiene.

## Executive Summary

Cerberus is a Java 21 Maven Minecraft plugin targeting Folia/Paper 1.21.x. The current branch is a large in-progress refactor: region/protection systems were removed, health lifecycle was pulled into a testable engine, combat config is now data-driven, and player session handling was centralized.

The project is worth continuing, but the next work should not be new features. The highest risk is that core runtime state is not truly per-player yet. `SkillManager` stores per-player maps, but many combat and defense calculations still read shared `Skill` objects or legacy global skill levels. `PlayerManaManager` also stores mana as single manager fields, so multiplayer mana state is shared. There is also a manager initialization ordering bug that leaves many skills missing or constructed with null dependencies.

Build status is mostly good once Java is pointed correctly: `mvnw.cmd test` fails with the current `JAVA_HOME` because it points to JDK 25, but passes when `JAVA_HOME` is set to JDK 21. Packaging also succeeds under JDK 21.

## Verified Checks

- `.\mvnw.cmd test` with current environment: fails.
  - Cause: `JAVA_HOME` is `C:\Program Files\Eclipse Adoptium\jdk-25.0.1.8-hotspot`.
  - Error: `invalid source release 21 with --enable-preview`.
- `.\mvnw.cmd test` with `JAVA_HOME=C:\Program Files\Eclipse Adoptium\jdk-21.0.9.10-hotspot`: passes.
  - 145 main sources compiled.
  - 5 JUnit tests passed.
- `.\mvnw.cmd package -DskipTests` with JDK 21: passes.
  - Built `target\Cerberus.jar`.
  - Jar contains the correct root `plugin.yml`.
- Existing `test-server\logs\latest.log` shows the plugin enabled on Folia 1.21.11 and handled combat, but also shows a post-quit damage event with `session=NONE`.

## P0 Findings

### 1. SkillManager is initialized before its required managers exist

Evidence:
- `CerberusPlugin.initSkillManager()` constructs `SkillManager` before `playerManaManager`, `resourceYieldManager`, `trapManager`, `luckManager`, `magicFindManager`, `craftingManager`, and `seaMonsterManager` are created.
- File: `src/main/java/cerberus/world/cerb/CerberusPlugin.java`, around lines 223-250.
- `SkillManager.initializeSkills()` only adds magic skills if `playerManaManager != null`, resource skills if `resourceYieldManager != null`, trap/scavenging skills if their managers exist, and passes `craftingManager` into many utility skills.
- File: `src/main/java/Skills/SkillManager.java`, around lines 143-196.

Impact:
- Magic skills are not added during initial construction.
- Mining/Farming/Woodcutting/Fishing are not added.
- Trap Mastery and Scavenging are not added.
- Several utility skills are created with a null `CraftingManager`.
- Later setter calls update manager fields but do not rebuild the skill registry.

Recommended fix:
- Build manager dependencies first, then create `SkillManager`, or add a single explicit `initializeSkills()` pass after all dependencies are set.
- Avoid constructors that need half-created dependencies. Prefer a `PlayerServices`-style bundle once all managers exist.

### 2. Skill levels are not safely per-player at runtime

Evidence:
- `SkillManager.setPlayerSkills()` stores per-player maps, then syncs shared `Skill` objects to the loaded player's levels.
- File: `src/main/java/Skills/SkillManager.java`, around lines 438-449.
- Combat reads shared `Skill` objects and `skill.getLevel()`.
- File: `src/main/java/Manager/CombatSkillProcessor.java`, around lines 119-145 and 173-190.
- Defense and other managers call legacy `getSkillLevel(String)`, which reads the global flat map, not the player's map.
- File: `src/main/java/Manager/PlayerDefenseManager.java`, around lines 91-135.
- `CustomPlayer.getSkillLevel(String)` delegates to the same global API.
- File: `src/main/java/cerberus/world/cerb/CustomPlayer.java`, around lines 89-92.

Impact:
- The last player loaded or leveled can change the shared `Skill` object level used by other players.
- Multiplayer combat, defense, and crafting bonuses can be wrong.
- Some systems may always see default level 1 even when player data has a higher level.

Recommended fix:
- Make player-specific level lookups the only path for runtime effects: `getSkillLevel(Player, String)`.
- Stop mutating shared `Skill` objects with per-player level state.
- Treat `Skill` definitions as stateless definitions, not player state holders.

### 3. PlayerManaManager has a shared mana pool

Evidence:
- `baseMana`, `totalMana`, `currentMana`, `manaRegenRate`, and `magicDamageMultiplier` are single fields on `PlayerManaManager`.
- File: `src/main/java/Manager/PlayerManaManager.java`, around lines 17-23.
- Getters/setters accept a `Player`, but read/write the shared fields.
- File: `src/main/java/Manager/PlayerManaManager.java`, around lines 54-83.

Impact:
- All players share current mana, max mana, regen rate, and magic damage multiplier.
- One player's spell casting or mana reset can change another player's mana state.

Recommended fix:
- Replace shared fields with per-player state, for example `ConcurrentHashMap<UUID, ManaState>`.
- Remove player parameters from methods that do not actually use them until the storage is fixed.

### 4. `/cerb reload` duplicates listeners and managers

Evidence:
- `reloadAll()` re-runs `initDatabase()`, `initCoreManagers()`, `initSkillManager()`, `initGUIs()`, `initCommands()`, and `initListeners()`.
- File: `src/main/java/cerberus/world/cerb/CerberusPlugin.java`, around lines 354-375.
- `initListeners()` registers new listener instances every time and does not unregister old ones.
- File: `src/main/java/cerberus/world/cerb/CerberusPlugin.java`, around lines 299-343.

Impact:
- Repeated `/cerb reload` can duplicate event handling, damage processing, HUD updates, and DB session executors.
- Old `PlayerSessionManager` executors may remain alive.

Recommended fix:
- Disable or narrow `/cerb reload` until a proper lifecycle reload exists.
- For now, support config reloads only: combat config, mobs, skill definitions, and mapper.
- Keep full plugin reload as clean server restart only.

### 5. Damage can recreate virtual health state after session cleanup

Evidence:
- Existing `test-server/logs/latest.log` shows a `DAMAGE_APPLIED` transition after the player disconnected, with `session=NONE`.
- `VirtualHealthListener` checks respawning and pending death, but not whether the player has an active `PlayerSession`.
- File: `src/main/java/Listener/VirtualHealthListener.java`, around lines 82-101 and 169-190.
- `PlayerVirtualHealthManager.applyResolvedDamage()` calls `getOrCreateState(player)`, which recreates state if cleanup removed it.
- File: `src/main/java/Manager/PlayerVirtualHealthManager.java`, around lines 274-340 and 531-533.

Impact:
- A damage event after quit can recreate cleaned-up state.
- This weakens session lifecycle guarantees and can leak stale state.

Recommended fix:
- Guard player damage with active session state before applying virtual damage.
- Consider `getStateIfPresent()` for damage paths rather than `getOrCreateState()`.

### 6. Spell metadata access can still crash

Evidence:
- `PlayerManaManager` checks `hasMetadata()` and then calls `getMetadata(key).get(0)`.
- File: `src/main/java/Manager/PlayerManaManager.java`, around lines 331-346 and 409-414.
- `SpellWeavingSkill` does the same for `spellDamage` and `spellRange`.
- File: `src/main/java/Skills/SpellWeavingSkill.java`, around lines 90-108.
- `PlayerManaManager` stores metadata using `Bukkit.getPluginManager().getPlugin("cerb")`, but the plugin name is `CerberusPlugin`.
- File: `src/main/java/Manager/PlayerManaManager.java`, around lines 338-350.

Impact:
- Empty metadata lists can throw `IndexOutOfBoundsException`.
- Wrong plugin lookup can return null and break metadata writes.

Recommended fix:
- Use the injected `plugin` field in `PlayerManaManager`.
- Read metadata through a helper that checks the list is non-empty.

## High Findings

### 7. Skill saves happen on every XP gain

Evidence:
- `addExperience()` and `addXpAndCheckLevelUp()` call `saveSkills(player)` immediately.
- File: `src/main/java/Skills/SkillManager.java`, around lines 221-255 and 325-354.
- Per-hit XP from combat calls this path.
- File: `src/main/java/Manager/CombatSkillProcessor.java`, around lines 209-232.

Impact:
- Combat can trigger SQLite writes per hit.
- This will bottleneck under multiplayer load.

Recommended fix:
- Mark players dirty on XP gain.
- Flush periodically and on quit/shutdown.

### 8. `saveAllPlayers()` uses Bukkit player APIs from an async scheduler

Evidence:
- `saveAllPlayers()` schedules an async task and then iterates `getServer().getOnlinePlayers()`.
- File: `src/main/java/cerberus/world/cerb/CerberusPlugin.java`, around lines 147-157.

Impact:
- Folia/Paper thread-safety risk.

Recommended fix:
- Snapshot online player IDs and skill maps on the correct thread, then write DB data asynchronously.

### 9. AsyncSaveManager uses a non-thread-safe HashMap

Evidence:
- `lastRunTimestamps` is a plain `HashMap`.
- File: `src/main/java/Manager/AsyncSaveManager.java`, around lines 15-18 and 36-65.

Impact:
- Concurrent async saves can race.

Recommended fix:
- Use `ConcurrentHashMap` and atomic compute/update.

### 10. First Aid scaling is still extreme

Evidence:
- `FirstAidSkill` adds `level * 1500.0` max health and `level * 1000.0` regen.
- File: `src/main/java/Skills/FirstAidSkill.java`, around lines 23-38.

Impact:
- Level 10 gives +15,000 HP.
- Level 100 gives +150,000 HP before other bonuses.
- Combat tuning becomes impossible.

Recommended fix:
- Move First Aid to `ScalingFormula` with hard caps.
- Suggested endgame HP contribution should be hundreds, not hundreds of thousands, unless the whole combat model is deliberately million-scale.

### 11. Mob tier damage exists, but mob HP/level scaling does not

Evidence:
- `/cerb spawn` tags mobs and sets custom names, but does not set max health.
- File: `src/main/java/Commands/CerbCommand.java`, around lines 399-424.
- `VirtualHealthListener.resolveMobLevel()` always returns 1.
- File: `src/main/java/Listener/VirtualHealthListener.java`, around lines 210-222.
- `mobs.yml` has classification and match rules but no HP, base damage, or level fields.

Impact:
- A rare or legendary zombie can still have vanilla zombie HP.
- Player damage progression outpaces encounter durability quickly.

Recommended fix:
- Add tier/level-based HP and explicit base damage fields to `mobs.yml`.
- Apply those values when spawning tagged mobs and when resolving existing configured mobs.

### 12. Admin command surface advertises many stubs

Evidence:
- `/cerb mana`, `skill`, `resetdata`, `worldgen`, `repairchunk`, `quest`, `event`, `timings`, `profiler`, `listentities`, and `showqueues` mostly send "not implemented" messages.
- File: `src/main/java/cerberus/world/cerb/CerberusPlugin.java`, around lines 426-521.

Impact:
- The admin command suggests operational tooling that does not exist.

Recommended fix:
- Keep only working commands in help/tab completion.
- Implement `skill`, `mana`, `resetdata`, and `status` first; hide the rest.

### 13. `/cerb` permission is not declared in the permission tree

Evidence:
- Command requires `cerb.admin`.
- File: `src/main/resources/plugin.yml`, line 11.
- Permissions section declares `cerberus.*` children, but not `cerb.admin`.
- File: `src/main/resources/plugin.yml`, around lines 30-49.

Impact:
- Permission behavior is inconsistent.

Recommended fix:
- Declare `cerb.admin` with `default: op`.
- Add it as a child of `cerberus.*` or standardize all command permissions under one namespace.

## Medium Findings

### 14. Build environment and scripts disagree

Evidence:
- Current `JAVA_HOME` points to JDK 25, but the project compiles with Java 21 preview args.
- `run.bat` handles Java 21 build and Java 25 runtime split.
- `deploy.bat`, `deploy.sh`, and `run.sh` do not set the Java 21 build JDK.
- `setup.sh`/`setup.bat` download Paper 1.21.1, while `run.bat` expects Folia 1.21.11.

Impact:
- A direct Maven command fails.
- Windows and shell workflows behave differently.

Recommended fix:
- Add a documented `.env.example` or update scripts consistently.
- Decide one local runtime target: Folia 1.21.11 or Paper 1.21.1.

### 15. Maven warnings should be cleaned up

Evidence:
- `maven-compiler-plugin` warns `testCompilerArgs` is unknown.
- DTR dependency POM is invalid.
- Shade warns dependencies also contain `plugin.yml`; current output keeps the right descriptor, but this should be explicitly configured.

Impact:
- Build output is noisy and can hide real warnings.

Recommended fix:
- Fix compiler plugin config.
- Clean or reinstall local DTR/Volor artifacts with valid POMs.
- Configure shade resource handling explicitly.

### 16. Repo hygiene needs a cleanup pass

Evidence:
- Parent repo tracks `Creb` as a gitlink (`160000`) but has no `.gitmodules`.
- `git submodule status` fails: no submodule mapping found for path `Creb`.
- `target/` and `dependency-reduced-pom.xml` are ignored now, but generated target files and dependency-reduced-pom are still tracked from previous commits.

Impact:
- Git status is noisy and confusing.
- Future commits can accidentally include build artifacts or subrepo pointer churn.

Recommended fix:
- Decide whether `Creb` is a real submodule, vendored legacy copy, or should be removed.
- Stop tracking generated Maven outputs.

### 17. GUI and feature surface is ahead of implementation

Evidence:
- Several menu actions route to `ComingSoonGUI`.
- Admin region management says it has been removed.
- Defensive Magic is still disabled pending a new input system.

Impact:
- The user-facing plugin looks broader than the playable feature set.

Recommended fix:
- For the next playable milestone, hide unfinished menu entries or mark them clearly as locked/planned.

## What Is Working

- The project compiles under JDK 21.
- The health lifecycle engine has unit tests and passes.
- The plugin enabled successfully in the last Folia test-server log.
- Database schema creation now covers the key tables from older audits.
- Native Folia scheduler usage is mostly in place; old Bukkit scheduler calls were not found.
- The built shaded jar contains the correct `plugin.yml`.
- Region/protection removals are mostly reflected in plugin registration.

## Recommended Starting Plan

1. Fix skill initialization order.
   - Goal: all 35 skills registered with valid manager dependencies.
   - Add a startup assertion/log that prints actual registered skill count.

2. Make runtime skill reads per-player.
   - Replace shared `Skill.getLevel()` reads in combat/defense/effects.
   - Replace legacy `getSkillLevel(String)` usages with `getSkillLevel(Player, String)`.

3. Make mana per-player.
   - Introduce a `ManaState` record/class keyed by UUID.
   - Fix metadata helper methods and use the injected plugin instance.

4. Disable unsafe reload and reduce command surface.
   - Convert `/cerb reload` to config-only reload.
   - Hide or remove stub commands from help/tab completion.

5. Batch persistence.
   - Dirty-player save queue.
   - Periodic flush.
   - Save on quit and shutdown.

6. Balance and mob progression.
   - Cap First Aid.
   - Add tier HP/level/base-damage to mobs.
   - Make `/cerb spawn` apply those stats.

7. Clean repo/tooling.
   - Fix `JAVA_HOME` docs/scripts.
   - Resolve `Creb` gitlink/submodule state.
   - Stop tracking generated Maven artifacts.

## Best Next Task

Start with skill initialization and per-player skill reads. That unlocks most of the rest of the project, because combat, defense, GUI skill displays, XP, and utility features all depend on skill state being correct.
