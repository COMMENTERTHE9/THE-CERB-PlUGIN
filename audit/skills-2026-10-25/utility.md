# Skill audit, utility group: First Aid, Stealth, Trap Mastery, Scavenging, Repairing, Trading, Navigation, Animal Taming, Riding, Lockpicking

Read-only code audit, 2026-10-25. I read the working tree on top of `1d2423f`; those changes were committed during the audit as `ffc7d4c`, and the citations were rechecked against `ffc7d4c`. Java paths are relative to `src/main/java/`; resources are under `src/main/resources/`. "Played" means verified on a server according to `docs/STATUS.md`. Everything else comes from reading the code. Nothing was run in game for this audit.

**How reachability was checked.**
- Every `pm.registerEvents` call. They all live in `cerberus/world/cerb/CerberusPlugin.java:512-577`.
- Every caller of `addXpAndCheckLevelUp`, `addExperience` and `addXpForMobKill`.
- Every caller of `.applyEffect(` and of the `EffectManager` dispatch methods.
- Every string reference to each skill name, in `src/main/java`, `src/main/resources` and `src/test/java`.

XP-curve numbers are computed from `SkillManager.xpForLevel` (`Skills/SkillManager.java:417-421`). Attribute numbers use the engine formulas (`Attributes/AttributeCalculator.java`) with `attributes.yml` and `combat.yml`.

One server behaviour was checked by disassembling `ServerGamePacketListenerImpl` with `javap`, from the sandbox's Folia 1.21.11 jar: does `PlayerMoveEvent` fire while riding? This was not tested in game.

**Status rule used.** "Partial" means one half of the loop is live: either XP from play, or the level changing play. First Aid and Stealth are partial only in that sense. Their level-to-attribute path is live, but nothing raises the level, so in normal play they do nothing.

## Cross-cutting findings (apply to all ten)

1. **No `applyEffect` of these skills ever runs.**
   - `.applyEffect(` is called only at `Manager/EffectManager.java:171,177,183`, for Spell Weaving, Summoning and Mana Regeneration.
   - `EffectManager.applyCombatSkillEffect` has no callers.
   - `applyMagicSkillEffect` is called only with "Elemental Mastery" (`Spells/FireballSpell.java:65,92`), and that branch contains only comments.
   - None of the ten skills has a case in the dispatcher.
2. **Even if dispatched, they would read the wrong level.**
   - There is one `SkillManager` (`CerberusPlugin.java:368`) holding one shared `Skill` object per skill (`Skills/SkillManager.java:170-223`).
   - `Skill.getLevel()` returns that object's own field (`Skills/Skill.java:44-46`). It defaults to 1, and `setLevel` (`:52`) has no caller.
   - Several bodies are also runaway-shaped or not Folia-safe (First Aid, Scavenging, Riding, Animal Taming below).
   - Do not wire them as they are. Rebuild them on per-player levels from `SkillManager`, with the attribute engine for stats.
3. **Levels start at 0 and stay there.**
   - New players get level 0 in every skill (`cerberus/world/cerb/DatabaseManager.java:203`), and level 0 gives no attribute points (`Attributes/SkillAttributes.java:21`).
   - The comment at `Attributes/SkillSource.java:26` says "unlearned skills count as level 1"; that is stale.
   - Nine of the ten have no XP source, so they stay at 0 in normal play. Only `/cerb skill` or the admin Skill editor can raise them (permission `cerb.admin`, op by default, `plugin.yml`).
4. **Fortune is inert.** `Attributes/AttributeService.java:88` returns `NOT_YET` for it. Scavenging, Lockpicking and Trading (and Fishing) feed only Fortune.
5. **All ten are registered skills** (`Skills/SkillManager.java:205-221`). The conditions on Trap Mastery (`:208`) and Scavenging (`:212`) hold, because the trap, luck and magic-find managers are created before the second `rebuildSkillRegistry()` (`CerberusPlugin.java:387-405`). So all ten appear in the Player Menu, in `/cerb skill` and in the admin tools.
6. **Two STATUS claims verified.**
   - "The trap listener is never registered": true. The trap system is broken in several more places (see Trap Mastery).
   - "The First Aid runaway was capped": true per call (commit `4e49557`). The method still adds on every call, but nothing calls it (see First Aid).

---

### First Aid (shown as Field Medicine)
**Status: partial.** Only the level-to-Vitality half is live (engine code, Played at admin-set levels). There is no XP source, so in normal play the skill sits at level 0 and does nothing. The skill's own effect code is disconnected, and its healing-item part is placeholder.

1. **XP source:** none. These are all the XP award sites, and none names First Aid:
   - `Spells/SpellManager.java:88-94` (spells);
   - `Listener/MobKillListener.java:89` (kills; `skills.yml` routes no utility skill);
   - `Manager/ChunkDiscoveryManager.java:81` (Navigation);
   - `Manager/CombatSkillProcessor.java:265-282` (combat);
   - admin: `CerberusPlugin.java:765` and `GUIs/admin/SkillEditMenu.java:175`.

   The skill page says it can't be trained (`GUIs/player/SkillView.java:88,126`), and `tools/playtest/menu.js:132` asserts that text.
2. **XP balance and exploits:** none apply (the level can only be set by an admin).
3. **Reachable effects:**
   - Vitality +0.5 per level (`attributes.yml:40`; `Attributes/AttributeRules.java:117`).
   - Max health = 100 × realm × (1 + 0.01 × Vitality) (`Manager/AttributeEffects.java:85,92-98`). First Aid 100 alone is about +50% max health. With Survival and Heavy Armor also at 100 it is 300 (Played, STATUS; `docs/PLAYTEST_CHECKLIST.md` #4).
   - PvE only: PvP hits are taken as a share of health (`attributes.yml:92`).
4. **Disconnected or placeholder:**
   - `Skills/FirstAidSkill.java:20-42` is never called. The registry instance has no dispatcher. A second instance is passed to `VirtualHealthListener` (`CerberusPlugin.java:559-560`) and stored in a field that is never read (`Listener/VirtualHealthListener.java:46,64`).
   - Runaway check: the per-call values are capped (`:30,33,39`). But every call adds max HP again (`Manager/PlayerVirtualHealthManager.java:272`) and starts one more 10-second regen task (`:530`). So it is bounded per call, not in total, and `AttributeEffects.applyMaxHealth` would overwrite the max on the next refresh anyway.
   - `getHealingItem` is "Example" code (`FirstAidSkill.java:44-46`).
   - `CraftingManager.enhanceHealingItems` ends in an empty `applyHealingBonus` (`Manager/CraftingManager.java:486-493`).
5. **Attribute integration:** Vitality only.
6. **Progression rewards:** none in normal play. At admin-set levels it is a percentage of max HP, with no unlocks. The description "recovering faster" is not true: regen is a flat 2 HP/s out of combat and 0 in combat, for everyone (`combat.yml:47-49`; `PlayerVirtualHealthManager.java:552-566`). It does not scale with First Aid or Vitality, so more max HP only makes a full refill take longer.
7. **Dependencies:**
   - The heal API exists (`PlayerVirtualHealthManager.heal`, `:284`), but the game has no healing item. `VirtualHealthListener.java:459-464` cancels every `EntityRegainHealthEvent` for players, so potions, golden apples and food regeneration never refill the real health pool (code reading, not checked in game).
   - Herbalism and Alchemy, which would supply healing items, have no XP or content.
   - The Cleric class needs Field Medicine 15 (`docs/design/CLASSES.md:106`, step 6a).
8. **Missing gameplay:**
   - an active heal (a bandage or salve, or vanilla consumables mapped into the virtual pool);
   - faster recovery;
   - stabilizing or reviving;
   - any XP source.
9. **Future interactions:**
   - Cleric and the `heal`/`cleanse` ability verbs (`CLASSES.md:106,184`);
   - sustain in boss fights (`docs/design/CERBERUS_DESIGN_PLAN.md:497,636`);
   - realm-scaled healing (regen already scales with realm, `PlayerVirtualHealthManager.java:564`);
   - the open "First Aid remediation" decision (`docs/design/OPEN_DECISIONS.md:187`) is effectively answered by the Vitality weight, a percentage of the realm health budget (`docs/design/CULTIVATION_INTEGRATION.md:51`).
10. **Now vs later:**
    - Now:
      - Heal the virtual pool from vanilla consumables, scaled by Field Medicine.
      - XP = HP actually restored by an active heal. Count only HP lost to non-self-inflicted damage in the last ~30 s, and cap it per minute so self-damage plus bread can't farm it.
      - Add a health-regen stat to the attribute engine, alongside the speed stats.
    - Later: healing others (Cleric), Herbalism bandages, PvP heal scaling (`pvpHealScale` already exists for lifesteal).

### Stealth
**Status: partial.** Only the level-to-Agility half is live: generic crit and speed points, nothing about stealth. There is no XP source, so it stays at level 0 in play. `StealthSkill` is placeholder code, and no stealth mechanic exists anywhere.

1. **XP source:** none (same evidence as First Aid).
2. **XP balance and exploits:** none apply.
3. **Reachable effects:**
   - Agility +0.25 per level (`attributes.yml:37`). Agility gives crit chance and crit damage against mobs (`Attributes/AttributeCalculator.java:61-70`) and a share of attack, draw and movement speed (`AttributeRules.java:145-147`).
   - Stealth 100 means +25 Agility. Computed with the `combat.yml:150-160` curves:
     - on a fresh character, crit chance goes 0% → 22.6% and crit damage ×1.50 → ×1.67, plus +1.2% movement and +2.4% attack speed;
     - on top of Critical Strike 50 it crosses the curve's inflection at 60: crit chance 37% → 61%, crit damage ×2.06 → ×2.90.
   - PvE only, and only at admin-set levels.
4. **Placeholder:**
   - `Skills/StealthSkill.java:22-43` is never called.
   - What it does: it looks for a `stealth_item` tag on the item in hand, then calls `CraftingManager.enhanceStealthItems`, which ends in an empty `applyStealthBonus` (`CraftingManager.java:496-503`). Then it sends a chat message ("Example: +5% stealth effectiveness per level", `:33,37`).
   - The code has no `EntityTargetEvent` or `EntityTargetLivingEntityEvent` handler, so nothing affects detection or aggro.
5. **Attribute integration:** Agility 0.25 per level. That means combat points, not concealment. The page lists only crit rows for Agility (`GUIs/player/SkillView.java:215-218`). Agility's share of speed is not shown there (an omission, not a false claim).
6. **Progression rewards:** none in play, and the attribute reward is off-theme: a stealth skill that makes you crit harder.
7. **Dependencies:**
   - A detection model, through mob targeting hooks (the Bukkit API exists, unused).
   - The `stealth` ability verb (`CLASSES.md:73,184`; not built).
   - Sneak is already an ability key: quick keys use Sneak + right-click and Sneak + F (`classes.yml:10`). A stealth mode based on sneaking must not switch on with every cast press.
8. **Missing gameplay:** lower mob detection while sneaking, an ambush bonus, detection feedback, and XP.
9. **Future interactions:**
   - The Assassin entry (Stealth 10 + Critical Strike 10, step 6a) and the Shadowblade entry (Stealth 10 + Spell Weaving 10, step 6c) (`CLASSES.md:99,113`).
   - Trap concealment (`CERBERUS_DESIGN_PLAN.md:1626`).
   - PvP scan radius (`:1798`).
   - Getting through points of interest (`:1411`).
   - The Mire-Stalker mount's Veil-Step (`docs/design/MOUNTS_AND_VEHICLES.md:169`).
10. **Now vs later:**
    - Now:
      - Use `EntityTargetLivingEntityEvent`: while a player sneaks, hostile mobs beyond a range set by Stealth level don't acquire them.
      - XP when a hostile within N blocks fails to notice the player while they move. No XP while idle (STATUS decision 8), and a cooldown per mob.
    - Later: the class verb, ambush damage, PvP.

### Trap Mastery
**Status: disconnected.** About 2,000 lines of trap code (`Traps/`, 1,957 lines) plus `Skills/TrapMasterySkill.java`. None of it is reachable, and it is broken inside even if wired.

1. **XP source:** none reachable. The only trap XP code is `Traps/TrapManager.java:183-196` (10 XP, ×2 against players, ×1.5 for the explosive trap: `:288-302`).
   - It has no caller: `triggerTrap` (`:247-259`) doesn't call it.
   - It looks up `"TrapMastery"` (`:190`), but the registry key is `"Trap Mastery"` (`Skills/SkillManager.java:209`), so it would get null.
   - It would add XP to the shared in-memory `Skill.addExperience` (`Skills/Skill.java:17-25`), never to the player or the database.
2. **XP balance and exploits:** nothing today. As written it would pay for any entity, including passive mobs and mob farms, and double against players.
3. **Reachable effects:** none, and no attribute weight. The only runtime touch is `trapManager.cleanup()` on disable, over empty maps (`CerberusPlugin.java:184`).
4. **Disconnected and broken inside:**
   - `TrapManager` is a Listener with four handlers (`:201`, `:226`, `:307`, `:356`) and is never passed to `registerEvents` (`CerberusPlugin.java:512-577`). This confirms STATUS.
   - `registerTrapRecipes()` (`:117`) is never called, so no trap item can exist.
   - A placed trap starts NEUTRAL (`Traps/Trap.java:30`). Only a `TrapTool` arms it (`Traps/TrapTool.java:164`), and no `TrapTool` is ever constructed. Every trigger path skips traps that aren't ARMED (`TrapManager.java:214,235,249`).
   - `Trap.baseDamage` is never assigned. `calculateBaseDamage()` (`Trap.java:41`) is private and unused, so every trap would deal 0 damage.
   - `TrapMasterySkill.applyEffect` (`:18-35`, "Example" numbers) is never called.
   - `CraftingManager.enhanceTraps` is empty (`:506-513`).

   **Landmines if someone only registers the listener:**
   - `onItemCraft` turns every crafted tripwire hook and TNT into a trap item (`:356-392`). `onBlockPlace` then cancels placing them and stamps a block pattern instead (`:307-351`). Vanilla TNT and tripwire hooks would stop working.
   - `isInTrapRange` calls `Location.distance` across worlds (`:244`), which throws IllegalArgumentException for a mob moving in a different dimension from a trap.
   - `onEntityMove` checks every trap on every mob step: O(mobs × traps), with cross-region reads on Folia.
   - Trap patterns overwrite world blocks, and there is no protection system (it was removed for Folia).
5. **Attribute integration:** none (no entry in `attributes.yml`).
6. **Progression rewards:** none.
7. **Dependencies:**
   - a working arm, trigger and re-arm cycle;
   - an item source (recipes or class devices);
   - damage through mob HP pools with realm scaling;
   - a region-local trap index;
   - rules against griefing.
8. **Missing gameplay:** the whole craft → place → arm → trigger → reward loop.
9. **Future interactions:**
   - The Artificer entry (Trap Mastery 15 + Smithing 10, step 6d) and Ranger snares (step 6a) (`CLASSES.md:96,114`).
   - Hunting gear (`docs/design/GEAR.md:191`).
   - Dungeon hazard rooms (`CERBERUS_DESIGN_PLAN.md:1411`).
   - Boss arenas (`:635`).
   - The traps-as-a-pillar spec (`:1607-1630`).
10. **Now vs later:**
    - Now: leave it unregistered.
    - Later: rebuild it as the Ranger/Artificer "devices" verb:
      - a per-player trap budget;
      - damage through the HP pools;
      - XP through `SkillManager` from trap kills, scaled by mob level, with none for passive mobs.

### Scavenging
**Status: placeholder.** The loot hook is an explicit placeholder, the luck managers it calls are real code but disconnected, and its Fortune weight is inert.

1. **XP source:** none.
2. **XP balance and exploits:** none apply.
3. **Reachable effects:** none. Fortune +0.5 per level (`attributes.yml:47`) is `NOT_YET`, and the page says "not used in the game yet" (`SkillView.java:238`).
4. **Placeholder or disconnected:**
   - `Skills/ScavengingSkill.java` implements Listener (`:21`) and is never registered.
   - Its `onChestOpen` (`:106`) uses a placeholder tier test on slot 0 (`:134-151`, "DEFERRED IMPLEMENTATION") and an empty `handleChestLoot` (`:154-157`).
   - `applyEffect` (`:55-68`) would add luck and magic find permanently on every call (`Manager/LuckManager.java:43-46`; `Manager/MagicFindManager.java:21-26`). Magic find is written to SQLite synchronously on the caller's thread (`DatabaseManager.java:468-476`). Wired as-is, it would accumulate without bound.
   - `LuckManager` and `MagicFindManager` have no other callers (`CerberusPlugin.java:1131-1132` are getters nobody uses).
   - `calculateSkillBasedLuck` reads skills that don't exist, "Magic Find" and "Exploration" (`LuckManager.java:93-96`), which default to level 1.
   - `CraftingManager.improveScavengedItems` is empty (`:516-523`).
5. **Attribute integration:** Fortune 0.5 per level (inert).
6. **Progression rewards:** none.
7. **Dependencies:**
   - a loot system (tiered containers, loot rules; not built);
   - a meaning for Fortune (the roadmap defines Fortune and rarity in the Mining → Smithing loop, `docs/ROADMAP.md:96-97,111`);
   - bags (`docs/design/SYSTEMS_BASE_LAYER.md:71`).
8. **Missing gameplay:** XP for looting, and better loot.
9. **Future interactions:**
   - gathering nodes (`CERBERUS_DESIGN_PLAN.md:111`);
   - dungeon caches (`:1405`);
   - wrecks (`MOUNTS_AND_VEHICLES.md:553`);
   - the Grav-Tether deployable (`GEAR.md:124-131`).
10. **Now vs later:**
    - Now: use vanilla structure loot.
      - `LootGenerateEvent` fires when a loot-table container is first opened or broken, once per container: dungeons, temples, shipwrecks, trial chambers.
      - XP per container, scaled by the area's creature level (the distance rule).
      - Fortune as a capped chance of one extra roll of the same table.
      - Skip `isPlugin()` events and creative players.
      - Mob drops the same way, through `EntityDeathEvent`.
      - Unverified: whether brushing suspicious sand fires the event too.
    - Later: custom caches, and magic find replacing `LuckManager`/`MagicFindManager` (as STATUS plans).

### Repairing
**Status: placeholder.**

1. **XP source:** none.
2. **XP balance and exploits:** none apply.
3. **Reachable effects:** none, and no attribute weight. Anvils, grindstones and Mending are vanilla; the code has no anvil or grindstone listener.
4. **Placeholder:** `Skills/RepairingSkill.java:26-47` is never called, and most of it could not run anyway:
   - The machine branch needs a block that is already in an in-memory map (`:33`). That map is only filled inside the same branch (`:54-56`, `:78`), so the branch can never be entered.
   - Over-repair needs level > 100 (`:42,65`), but the maximum level is 100 (`Skills/SkillProgression.java:17`).
   - `Math.max(0, …)` (`:88`) makes the `< 0` branch (`:90`) dead.
   - The normal branch calls an empty overload (`CraftingManager.java:416-418`).

   Unsafe if it ever ran:
   - `applyRepairEffectiveness` repairs the held item for free on every call (`CraftingManager.java:398-404`).
   - "Example" Sharpness III goes onto any item (`RepairingSkill.java:97`).
   - `CraftingManager.trackAndRewardRepairs` gives a diamond every 10 calls (`:442-452`).
5. **Attribute integration:** none.
6. **Progression rewards:** none.
7. **Dependencies:** durability that matters (entropy durability, `CERBERUS_DESIGN_PLAN.md:127,877`), custom gear (`GEAR.md`), and Smithing (no XP).
8. **Missing gameplay:** everything.
9. **Future interactions:** entropy as a money sink (`CERBERUS_DESIGN_PLAN.md:1866`), and repairing gear in the field during boss fights (`:631`).
10. **Now vs later:**
    - Now, but low value: anvil repairs give XP by durability restored, with a capped discount on the anvil's level cost (`PrepareAnvilEvent`). The vanilla level cost limits farming.
    - Better later, with entropy durability: items are kept on death today, so durability rarely matters.

### Trading
**Status: placeholder.** The class says so itself: `Skills/TradingSkill.java:7-19`, "Methods in this class do nothing by design".

1. **XP source:** none.
2. **XP balance and exploits:** none apply.
3. **Reachable effects:** none. Fortune +0.5 per level (`attributes.yml:49`) is inert. Villager trading is vanilla (`tools/playtest/abilities.js:389` checks that a right-click still opens it).
4. **Placeholder:**
   - `applyEffect` (`:30-49`) is never called.
   - If it ran, it would send three invented "Applied a N% trade bonus" messages (`:60,67,74`) and call an empty `enhanceTradeValue` (`CraftingManager.java:536-543`).
   - The page promises better villager prices (`GUIs/SkillDefinitions.java:293-294`), while the class targets markets, a bazaar and stocks.
5. **Attribute integration:** Fortune 0.5 per level (inert).
6. **Progression rewards:** none.
7. **Dependencies:** NPC vendors, a currency, a bazaar, contracts (`CERBERUS_DESIGN_PLAN.md:1196-1201,1853`). None of them are built.
8. **Missing gameplay:** everything.
9. **Future interactions:** player economy, escrow contracts, gold sinks, trading keyed gear.
10. **Now vs later:**
    - Now, but low value: Paper's `PlayerTradeEvent` gives XP by emerald value, with diminishing returns per villager per day (to stop trading halls and restock loops), plus a small capped discount.
    - Later: vendors and an economy.

### Navigation (shown as Pathfinding)
**Status: functional (thin).** The XP path and the level effect are both reachable. Protection is mostly sound, with gaps.

1. **XP source:**
   - 5 XP the first time a player ever enters a chunk, per world (`Manager/ChunkDiscoveryManager.java:29,62-83`).
   - It is called from `MobKillListener.onPlayerMove` when the chunk changes (`Listener/MobKillListener.java:97-110`), registered at `CerberusPlugin.java:531`.
   - The visited set loads on join (`Listener/PlayerSessionManager.java:109,134`), and each new chunk is stored (`DatabaseManager.java:567-580`).
   - What counts: walking, sprinting, swimming, elytra, and driving a horse or boat. Paper fires `PlayerMoveEvent` from the vehicle-move handler.
   - What doesn't count: minecart rides and passenger seats. The player-move handler skips the event for passengers.
   - Both points were checked in the Folia 1.21.11 server bytecode (`handleMoveVehicle`, `handleMovePlayer`), not in game.
   - Cost to level, from `xpForLevel` starting at level 0:

     | Level | Chunks needed |
     |---|---|
     | 10 | 3,594 |
     | 15 | 11,595 |
     | 25 | 50,309 |
     | 50 | 365k |
     | 100 | 2.64M |

   - Speed of earning: about 6,300 XP per hour sprinting in a straight line, roughly 37,000 with rocket elytra. So Pathfinding 15 (the Scout entry) takes about 9 hours on foot or about 1.6 hours flying.
   - One Common level-50 kill (334 XP) equals 67 new chunks.
   - The design plan proposed 25 XP per chunk (`CERBERUS_DESIGN_PLAN.md:998`).
2. **XP balance and exploits:**
   - Sound:
     - Each chunk pays once, ever (memory set plus database), so walking back and forth pays nothing.
     - AFK play needs fresh land. A minecart loop pays nothing.
     - A client auto-walk that keeps moving into new land looks the same as play, but each chunk still pays only once.
   - Gaps:
     - There is no game-mode check, so creative and spectator flight count. Only ops can change mode, but the admin self-tools toggle creative (`GUIs/admin/SelfToolsMenu.java:78`).
     - XP ignores danger: a chunk 15,000 blocks out pays the same as one at spawn.
     - If `loadVisitedChunks` fails, it logs and returns an empty set instead of failing the join (`DatabaseManager.java:558-560`). Every old chunk then pays again that session.
     - Each new chunk opens its own SQLite connection, in an async insert that is not batched (`DatabaseManager.java:138-141,567-580`). That adds write contention to the known "database is locked" risk.
     - The visited set is loaded whole on every join and never shrinks. Rough estimate, not measured: about 100 bytes per chunk in memory, so about 35 MB for the 365k chunks that Pathfinding 50 itself requires.
   - Not exercised in game: no playtest walks into new chunks. `tools/playtest/menu.js:131-136` checks only the page text.
3. **Reachable effects:**
   - Agility +0.25 per level (`attributes.yml:36`).
   - Movement speed +0.1% per level, raw (`:119`), with diminishing returns toward the 20% cap (`:109`; `AttributeCalculator.java:84-89,108-112`).
   - The movement bonus is applied as a temporary modifier whenever the level changes (`AttributeEffects.java:45` → `Manager/SecondaryEffects.java:71-77`), and paused during PvP.
   - Pathfinding 100 alone gives about +8.6% movement speed (Played: +9.0%, STATUS; `tools/playtest/progression.js:506-511`). At realistic levels: L15 +1.6%, L25 +2.6%, L50 +4.9%.
   - At level 100 its 25 Agility also raises mob crits (see Stealth for the numbers).
4. **Disconnected:**
   - `Skills/NavigationSkill.java:27-39` is never called. It holds a biome speed table, empty map and compass stubs (`:147-154`), and an old persistent speed modifier (`:66-71`) that `SecondaryEffects.onJoin` now strips (`SecondaryEffects.java:108-117`).
   - `/cerb speed` still prints the retired "1% per level" bonus (`Commands/CerbCommand.java:365-372`).
5. **Attribute integration:** Agility 0.25 per level, plus movement speed 0.1% per level.
6. **Progression rewards:** percentages only. No unlocks and no tools. A new chunk gives no feedback at all; the only message is the level-up (`SkillManager.java:269-271`).
7. **Dependencies:**
   - Chunk discovery and its table (built).
   - The attribute engine (built).
   - The HUD XP display (`docs/ROADMAP.md:100-105`, not built).
   - Regions and landmarks (not built; the World & Exploration screen says so, `GUIs/player/WorldMenu.java`).
8. **Missing gameplay:**
   - discovery weighted by danger;
   - first-visit bonuses for biomes and structures;
   - map and compass utility;
   - an endgame use for explored land (`CERBERUS_DESIGN_PLAN.md:1874`).
9. **Future interactions:**
   - The Scout entry: Pathfinding 15 (`CLASSES.md:98`).
   - Blink Gates built on the same table (`CERBERUS_DESIGN_PLAN.md:700,1364`).
   - Navigation as the flight and vehicle skill (`MOUNTS_AND_VEHICLES.md:315-324,614-624,1021`).
   - Boss arenas (`CERBERUS_DESIGN_PLAN.md:583,599,615`).
10. **Now vs later:**
    - Now:
      - Scale chunk XP by the chunk's creature level (the distance rule `WorldMenu` already computes).
      - Skip creative and spectator.
      - Batch chunk writes through `AsyncSaveManager`.
      - Fail or retry the join when the visited set can't load.
      - Show discoveries on the HUD (roadmap item 4).
      - First-visit bonuses for biomes and structures.
    - Later: Blink Gates, flight, vehicles.

### Animal Taming
**Status: placeholder.** Example code with empty stubs, never called.

1. **XP source:** none. `gainTamingExperience` is an empty stub (`Skills/AnimalTamingSkill.java:155-158`, "Example: CustomPlayer.addExperience(...)"). The code has no tame or breed listener.
2. **XP balance and exploits:** none apply.
3. **Reachable effects:** none, and no attribute weight.
   - Vanilla taming works, and a rideable mob's label steps aside for a rider (`Manager/MobLabelManager.java:151-185`; smoke.js horse check, STATUS).
   - Pet handling that has nothing to do with the skill: pets are excluded from ability targets and spared by Fireball (`Manager/AbilityTargets.java:20`; `Spells/FireballSpell.java:182-183`), and count as their owner in PvP (`Manager/PvpTag.java:58`).
4. **Placeholder:** `applyEffect` (`:26-40`) is never called. If it ran:
   - It would scan every tameable animal in the world (`:69`), which on Folia touches entities owned by other regions.
   - It would multiply pet health on every call (`:49,124,141`). `setHealth` above max health throws an exception, so a full-health pet would crash it.
   - It would compound max health on horses and camels (`:52,58`).

   `applyTamingSuccessBonus` and two of the enhance methods are empty (`:42-43,104-105,113-114`).
5. **Attribute integration:** none.
6. **Progression rewards:** none.
7. **Dependencies:**
   - A pet and mount registry with persistence, bonding and loyalty (`MOUNTS_AND_VEHICLES.md` §4; design only).
   - Mob levels and HP pools already apply to animals, because every mob is labelled (STATUS).
8. **Missing gameplay:** everything.
9. **Future interactions:**
   - Bonded mounts and beast ascension (`MOUNTS_AND_VEHICLES.md:238-264`).
   - The Beastmaster entry: Animal Taming 15 (`CLASSES.md:100`, step 6c).
   - Eggs and breeding (`:386-388`).
   - Beast-drawn vehicles (`:551`).
10. **Now vs later:**
    - Now:
      - XP on `EntityTameEvent`, scaled by the animal's level.
      - Little or no XP for breeding: feeding loops are cheap and pile up entities.
      - Pet bonuses as keyed attribute modifiers that never compound, applied on the pet's own scheduler.
    - Later: bonding, loyalty, eggs, and mounts defined as data.

### Riding
**Status: placeholder.** Example code, never called, and it would compound if wired.

1. **XP source:** none. Driving a mount into new chunks trains Pathfinding, not Riding.
2. **XP balance and exploits:** none apply.
3. **Reachable effects:** none, and no attribute weight. `SecondaryEffects` changes only the player's own speed, not the mount's. Vanilla riding works (after the label fix).
4. **Placeholder:** `Skills/RidingSkill.java:25-54` is never called. If wired, every call would:
   - permanently multiply the mount's base speed (`:61`), jump (`:113`), max health (`:118,123,142`) and armor (`:182`), saved on the entity;
   - add the level to "loyalty" again (`:139`);
   - once "fatigue" passes 0.5, multiply base speed by (1 − fatigue) (`:158-176`), until the mount barely moves.

   Fall-damage reduction is empty (`:66-67`), and `summonMount` teleports the mount you are already sitting on (`:187-192`).
5. **Attribute integration:** none.
6. **Progression rewards:** none.
7. **Dependencies:** a mount system and mounted-combat rules (`MOUNTS_AND_VEHICLES.md:249,284,335-344`; design only). Paper already fires `PlayerMoveEvent` while the player drives.
8. **Missing gameplay:** everything.
9. **Future interactions:**
   - The mounted-action ceiling (`MOUNTS_AND_VEHICLES.md:335-344`).
   - Pilot bonuses for mechs and airships (`:680`).
   - The Scout entry: Riding 5 (`CLASSES.md:98`).
10. **Now vs later:**
    - Now:
      - Riding XP for new chunks reached while driving. This reuses chunk discovery, so each chunk pays once and AFK loops in water currents can't farm it.
      - Riding XP for kills while mounted.
      - A keyed speed and jump modifier on the ridden mount, added when the player mounts and removed when they dismount.
    - Later: bonded mounts, mounted-combat tiers, flight.

### Lockpicking
**Status: placeholder.** There is nothing to pick.

1. **XP source:** none.
2. **XP balance and exploits:** none apply.
3. **Reachable effects:** none. Fortune +0.5 per level (`attributes.yml:48`) is inert.
4. **Placeholder:**
   - `Skills/LockpickingSkill.java:25-50` is never called.
   - It uses a hard-coded "reinforced_lock" (`:37`). Its success chance is 0.10 + 0.01 × level − 0.30 (`:33-43`), negative at the object's level 1, so every attempt would fail.
   - It only sends chat messages (`:60,66`), and the tool bonus and durability methods are stubs (`:71-81`).
   - `CraftingManager.enhanceLockpicks` is empty (`:586-593`).
5. **Attribute integration:** Fortune 0.5 per level (inert).
6. **Progression rewards:** none.
7. **Dependencies:**
   - Locked containers or doors: none exist, and Paper's container-lock API is unused.
   - Dungeons, points of interest and caches (`CERBERUS_DESIGN_PLAN.md:1405-1411`; design only).
   - Lockpick items.
   - A consequence for failing (traps, which are broken).
8. **Missing gameplay:** everything.
9. **Future interactions:** dungeon caches, getting through points of interest, disarming traps (`Traps/TrapToolType.java:5`), Fortune.
10. **Now vs later:** wait until locked content exists, and build it with the first dungeons or caches.

---

## Claims the code doesn't back

- **Guide:** `GUIs/player/GuideMenu.java:42` says "Every level adds attribute points, so you grow stronger as you train." Trap Mastery, Repairing, Animal Taming and Riding give no points; their own pages say "This skill gives no attribute points or speed" (`GUIs/player/SkillDetailMenu.java:170-171`). Scavenging, Lockpicking and Trading give only Fortune, which does nothing.
- **Admin speed diagnostic:** `/cerb speed` (`Commands/CerbCommand.java:369-372`) prints "Nav speed bonus: +N% (1% per level)". That is the retired formula. The live bonus is +0.1% per level, raw, with diminishing returns: about +8.6% at level 100.
- **Skill descriptions in `GUIs/SkillDefinitions.java`:** these one-line descriptions overstate. The effect rows under each one correctly say "Not active yet".
  - First Aid, "recovering faster" (`:283`): regen is flat for everyone.
  - Scavenging, "finding more in chests and on fallen foes" (`:289`).
  - Trading, "bargaining with villagers" (`:293`).
- **Agility:** `Attributes/Attribute.java:14` and `GuideMenu.java:46` describe Agility as crits only. It also drives attack, draw and movement speed (`AttributeRules.java:145-147`). An omission.
- **Code comments (not seen by players):**
  - `SkillManager.java:414` says reaching level 100 costs about 19M XP. The current curve gives about 13.2M from level 0. The design plan's table also comes from an older curve.
  - `Attributes/SkillSource.java:26` says unlearned skills count as level 1. They are stored as level 0.
- **Accurate, with one caveat:** the Pathfinding page's "Enter a chunk you have never visited: 5 XP" holds, except for passengers and minecart riders (Navigation point 1).

## Group table

| Skill | Status | XP source | What its level does |
|---|---|---|---|
| First Aid (Field Medicine) | partial (inert in play) | none | +0.5 Vitality per level → max health (PvE). The level can't be earned. |
| Stealth | partial (inert in play) | none | +0.25 Agility per level → mob crits and a little speed. No concealment. |
| Trap Mastery | disconnected | none (the trap XP code is dead) | nothing |
| Scavenging | placeholder | none | +0.5 Fortune per level (inert) |
| Repairing | placeholder | none | nothing |
| Trading | placeholder | none | +0.5 Fortune per level (inert) |
| Navigation (Pathfinding) | functional (thin) | 5 XP per never-visited chunk | +0.25 Agility per level, and movement speed (+1.6% at 15, +2.6% at 25, +8.6% at 100) |
| Animal Taming | placeholder | none | nothing |
| Riding | placeholder | none | nothing |
| Lockpicking | placeholder | none | +0.5 Fortune per level (inert) |

## Highest-value missing loops, by value to players against effort

1. **The Field Medicine healing loop.** High value, low-to-medium effort.
   - Nothing heals in a fight today: out-of-combat regen is a flat 2 HP/s, the only other heal is lifesteal, and every vanilla heal (potions, golden apples, food) is cancelled for players.
   - The heal API, the combat timer and realm scaling already exist.
   - XP from HP actually restored by active heals is naturally bounded by item cost and damage taken.
   - It also opens the Cleric entry (step 6a).
2. **The Scavenging loot loop, plus giving Fortune a job.** Medium-high value, low-to-medium effort.
   - `LootGenerateEvent` fires once per loot container, so protection against farming is built in.
   - It rewards exploration, which lines up with Pathfinding players.
   - It would make Fortune do something for Scavenging, Lockpicking, Trading and Fishing.
   - Define Fortune together with the roadmap's Mining Fortune work, so there is one rule ("Fortune duplicating rewards" is on the roadmap's exploit list).
3. **A starter mount loop: Riding plus Animal Taming.** High value, medium effort.
   - Mounts are a design pillar, and this loop opens the Scout and Beastmaster entries.
   - Riding XP from new chunks reached while driving: AFK-proof, and it reuses chunk discovery.
   - Riding XP from kills while mounted.
   - Taming XP from `EntityTameEvent`, scaled by the animal's level.
   - Mount and pet modifiers that never compound. The existing `RidingSkill` and `AnimalTamingSkill` bodies must not be reused, because they compound.

**A cheap fix to the one live loop.** Pathfinding XP scaled by danger, a creative/spectator guard, batched chunk writes, a strict load and feedback on the HUD. Low effort.

**Then, in order:**
1. The Stealth aggro loop. Medium effort; opens Assassin and Shadowblade. It needs a design that avoids the Sneak ability keys.
2. Repairing through anvil hooks. Its value waits for entropy durability.
3. Trading through villager trades. Its value waits for an economy.
4. The Trap Mastery rebuild. High effort; do it with Ranger and Artificer, and never by simply registering the old listener.
5. Lockpicking. Wait for locked content.
