# Gathering and production skills: code audit (10 skills)

*Mining, Smithing and Crafting in depth; Woodcutting, Farming, Fishing, Alchemy, Enchanting, Herbalism, Cooking. Read from the source at commit `17097b1` (2026-10-25). Evidence is code inspection only: nothing was built or run. Paths are under `src/main/java/` unless they start with `docs/`, `src/main/resources/` or `tools/`.*

## How I checked

- **Callers.** grep for every method that could deliver a behaviour (`applyEffect`, `handleBlockBreak`, `applyYieldBonus`, every `CraftingManager`, `LuckManager`, `MagicFindManager`, `PlayerStrengthManager` method, the manager getters on `CerberusPlugin`) and for every `addXp*` / `addExperience` call.
- **Listeners.** The registered set is `cerberus/world/cerb/CerberusPlugin.java:512-577`. grep for every block, craft, fish, brew, enchant, furnace, smithing, anvil and consume event handler in the code base, and for `new <Listener>(` constructions.
- **Pages.** `GUIs/SkillDefinitions.java` and `GUIs/player/SkillView.java` compared with the code.
- **Intent.** `docs/ROADMAP.md`, `docs/STATUS.md`, `docs/design/*`.

## Findings shared by all ten

- **S1. No XP source.** The only callers of the award path are kill XP (`Listener/MobKillListener.java:89`), spell casts (`Spells/SpellManager.java:88-94`), hit XP (`Manager/CombatSkillProcessor.java:265-282`), new chunks (`Manager/ChunkDiscoveryManager.java:81`) and admin tools (`CerberusPlugin.java:743-773` `/cerb skill`, `GUIs/admin/SkillEditMenu.java:140,175`). No registered handler exists for `BlockBreakEvent`, `BlockPlaceEvent` (`TrapManager` has one, never registered), `PlayerHarvestBlockEvent`, `FurnaceExtractEvent`, `BrewEvent`, `EnchantItemEvent`, `SmithItemEvent` or `PrepareAnvilEvent`; the one registered `CraftItemEvent` handler (`CraftingListener`) awards nothing (grep of every handler). The one config path: `skills.yml skill-triggers` can route *kill* XP to any skill name (`MobKillListener.java:120-126` → `addXpAndCheckLevelUp`), so an admin could make pickaxe kills train Mining; the shipped file does not. The shipped `skills.yml:18-19` comment says utility skills "get XP from block-break events": **false**.
- **S2. No reachable effect code.** Nothing calls `applyEffect` on any of the ten. `Manager/EffectManager.java:169-185` dispatches only Spell Weaving, Summoning and Mana Regeneration, and the only reachable dispatch into it is `Spells/FireballSpell.java:65,92` → `applyMagicSkillEffect(…, "Elemental Mastery")`, an empty method (`EffectManager.java:162-167`). No scheduled task calls skill effects (grep of `runAtFixedRate`/`runDelayed`).
- **S3. The old skill classes could not work even if wired.** The registry holds one shared object per skill for the whole server (`Skills/SkillManager.java:170-223`, one `SkillManager` built at `CerberusPlugin.java:368`). `Skill.level` starts at 1 and nothing calls `setLevel` (grep), so every `this.getLevel()` reads 1 for every player. Their stat bonuses are read-then-add and stack on every call: `PlayerVirtualHealthManager.increaseMaxHealth` (`:272-274`), `PlayerManaManager.increaseMaxMana/increaseManaRegenRate` (`:131-140`), and `PlayerStrengthManager.increaseStrength` (`Manager/PlayerStrengthManager.java:14-16`), which sets the vanilla ATTACK_DAMAGE *base* to the current *total* plus the bonus (compounding, and saved with the player). Most send chat on every call. Smithing, Alchemy and Enchanting read `CustomPlayer.getCurrentCraftedItem()`, whose setter has no caller (`cerberus/world/cerb/CustomPlayer.java:64,70`), so they would stop at "No item is currently being crafted." `Skill.addExperience/checkLevelUp` (`Skills/Skill.java:17-38`) is a second, dead XP curve (100·1.2^L).
- **S4. The player-facing pages are honest.** `SkillDefinitions.java:263-282` gives each of the ten no training line and one effect marked "○ Not active yet"; `SkillView.java:88,126` prints "○ No way to train it yet" / "Nothing in the game trains this skill yet". The admin Progression Lab lists them under "Not wired yet" (`GUIs/admin/ProgressionLabMenu.java:60-66`). No false in-game claim found. The `Skills/Skills.java` enum ("Increases efficiency and speed of mining operations", …) is never used (grep).
- **S5. Attributes.** Only Fishing is an attribute source (Fortune 0.5 per level, `src/main/resources/attributes.yml:50`, `Attributes/AttributeRules.java:127`), and Fortune is display-only (`Attributes/Attribute.java:15`, `Attributes/AttributeService.java:88` `NOT_YET`, `GUIs/player/GuideMenu.java:50`). New players' rows start at level 0 (`cerberus/world/cerb/DatabaseManager.java:80, 201-203`).
- **S6. No exploit protection exists for gathering or crafting.** No placed-block tracking (the block-tag methods are placeholders, `Manager/CustomTagManager.java:42-53`); no Silk Touch, Fortune, creative-mode or automation handling (grep for `SILK_TOUCH`, `GameMode`, pistons, hoppers, Crafter: none in this code; one commented-out creative check, `Traps/TrapManager.java:316`). Patterns worth copying exist elsewhere: dispenser shots excluded from defensive XP (`Listener/VirtualHealthListener.java:422`), the kill-XP grey-mob rule (`Attributes/KillXp.java:35-37`), per-mob XP budgets (`Classes/CastXpLedger.java`). The idle-farm rule is still an open owner decision (`docs/STATUS.md:409`, item 8).
- **S7. Vanilla healing is off for players.** `VirtualHealthListener.java:459-464` cancels every `EntityRegainHealthEvent` for players: food saturation, golden apples, healing and regeneration potions restore no Cerberus health. Cooking and Alchemy have no natural payoff until consumables go through the virtual pool or `Attributes/BuffSource`.
- **S8. `docs/STATUS.md` checked.** "No XP source" (`:52`) is true for all ten. "Strength / Luck / Magic Find / Yield managers … nothing reachable calls them" (`:54`) is true. "~600 lines of quality-crafting and trap code … unreachable" (`:55`) is true but understated: `Manager/CraftingManager.java` alone is 604 lines; with `cerberus/world/cerb/QualityBuffs.java` (177), `Listener/PlayerItemListener.java` (125, never constructed) and `Traps/` (1,957 lines, 14 files) about 2,860 lines are unreachable. `TrapManager` is never registered and `registerTrapRecipes()` (`TrapManager.java:117`) is never called; its two `addRecipe` calls (`:135,149`) are the only custom recipes in the code. The 2026-06-07 audit finding that Mining/Farming/Woodcutting/Fishing were missing from the registry (`audit/CODEBASE_AUDIT_2026-06-07.md:33-42`) is fixed: the registry is rebuilt after the setters (`CerberusPlugin.java:397-405`), so `/cerb skill <p> Mining …` works.

---

### Mining
**Status: disconnected.** Real code exists (`Skills/MiningSkill.java`, `Manager/ResourceYieldManager.java`) and nothing reachable calls it; parts are example code; drills and resource nodes are design-only.

1. **XP source.** None (S1). A kill with a pickaxe matches no `skills.yml` rule (`.*_AXE` needs the name to end in `_AXE`, `src/main/resources/skills.yml:46-54`), so it trains Heavy Armor Training if heavy armor is worn (`MobKillListener.java:135-138`), else nothing. Admin commands can set the level.
2. **Balance and exploits.** No XP, so nothing to balance. Context for the loop: the shared curve is 115 XP at L1 to about 373k at L99, about 19M to 100 (`SkillManager.java:408-421`); kill XP v2 pays 40 (Common L1) to 334 (Common L50) (`attributes.yml:10-17`); `tools/playtest/load.js:16-18` mines about 5 blocks/s per bot. So per-block XP must be small and weighted to ores, with stone near zero. No protection exists against placed-block re-mining, Silk-Touched ore re-placed, Fortune, creative, TNT or the gather load's `/setblock` refills (S6).
3. **Reachable effects.** None.
4. **Disconnected / placeholder.**
   - `MiningSkill.applyEffect` (`MiningSkill.java:17-37`): `applyYieldBonus` drops the loot of the block the player *looks at* without breaking it, a duplication (`ResourceYieldManager.java:135-152`); +2 max health and +1 vanilla attack damage per level, re-added every call (example values, S3); a "rare gem" chat message only, 2% + 1% per level (`:39-49`).
   - `ResourceYieldManager.handleBlockBreak` (`:57-74`), no caller: multiplier 1 + 0.1 × level (×11 at 100, `:115-120`); vanilla drops are not cancelled, so even level 1 roughly doubles drops; `Block#getDrops()` without the tool ignores Fortune and Silk Touch (Paper API semantics, unconfirmed in game); pre-1.17 ore list (no deepslate, copper, lapis, redstone, nether gold, ancient debris), while concrete and terracotta count as Mining (`:39-55, 76-113`); a 1% diamond-from-stone table is defined and never used (`:32-37, 122-132`).
5. **Attributes.** None: Mining has no weight in `attributes.yml:23-50`.
6. **Progression rewards.** None; a level can't be earned and changes nothing.
7. **Dependencies.** A resource registry, placed-block tracking, tool capability data, a Fortune reader and the HUD XP display (`docs/ROADMAP.md:100-105`): none exist.
8. **Missing gameplay.** XP per resource; tool tiers and level-gated ores; quality or rarity rolls (design: quantity capped at +50%, rarity as a separate luck roll, `docs/design/CERBERUS_DESIGN_PLAN.md:1136-1138`); gathering speed (proposed, `docs/design/ATTRIBUTE_EXPANSION.md:41`); unlocks rather than percentages (`docs/ROADMAP.md:66`).
9. **Future interactions.** Marrow-drill and other power tools gated by Mining (`docs/design/GEAR.md:66, 111-121, 190`); custom ores and drills plugging in (`docs/ROADMAP.md:97-98`); distance-tiered nodes (`CERBERUS_DESIGN_PLAN.md:106-128`); material pouch (`docs/design/SYSTEMS_BASE_LAYER.md:64`); Marrow economy.
10. **Now vs later.** Now: XP on `BlockBreakEvent` through a `resources.yml` registry of vanilla ores (deepslate and raw-ore aware), a placed-block tag in chunk PDC, creative and automation skipped, Silk Touch on ores paying no XP, the award through `SkillManager.addXpAndCheckLevelUp`, gains on the HUD, gathering speed through `SecondaryEffects`, a bounded Fortune rare-drop roll. Later: drills and machines, realm-tiered nodes, custom ores (resource pack), economy.

### Smithing
**Status: disconnected**, with a placeholder entry point. The smithing code in `CraftingManager` is real but unreachable, and `SmithingSkill` can never receive an item.

1. **XP source.** None. No furnace, smithing-table or anvil handler exists (S1).
2. **Balance and exploits.** Nothing to balance. Risks for the loop: hopper-fed furnaces (`FurnaceSmeltEvent` has no player; `FurnaceExtractEvent` does), nugget ↔ ingot ↔ block loops, re-smelting, anvil repair loops.
3. **Reachable effects.** None.
4. **Disconnected / placeholder.**
   - `SmithingSkill.applyEffect` (`Skills/SmithingSkill.java:22-55`) needs `getCurrentCraftedItem()`, always null (S3), so it stops at `:31-34`. Past that: the "+1.5 virtual health per level" is a heal, not max health (`PlayerVirtualHealthManager.java:263-265`); +1 vanilla attack damage per level per call; recipe "unlocks" are chat lines (`:64-83` → `CraftingManager.unlockAdvancedRecipe`, `CraftingManager.java:465-470`, which reads the *Crafting* level and only messages at 100).
   - `CraftingManager.applySmithingBonuses` (`:310-325`): at 20+, a 10% chance to append "[Elemental Infusion]" to the name (no effect, `:328-334`); at 40+, unsafe Sharpness of level/10 (Sharpness 10 at 100, `:337-340`); at 60+, armor repaired by 10 × level points (`:343-346`). It also runs through the general quality roll (see Crafting).
5. **Attributes.** None.
6. **Progression rewards.** None.
7. **Dependencies.** Mining (inputs), a recipe and gear registry, an equipment attribute source, item quality (today's is broken, see Crafting), Repairing (anvil overlap). The Artificer class needs Smithing 10 (`docs/design/CLASSES.md:114`, design-only).
8. **Missing gameplay.** XP for smelting, smithing-table and anvil work; ingots into gear tiers; reforge and temper (`CERBERUS_DESIGN_PLAN.md:886-887`); refinement fed by ore (`docs/design/WEAPONS.md:139,148`); repair and entropy (`CERBERUS_DESIGN_PLAN.md:871-878`).
9. **Future interactions.** Equipment quality feeding attributes, alloys and custom metals, the qi-forge deployable (`GEAR.md:192`), Marrow repair costs, realm-tiered materials.
10. **Now vs later.** Now: XP on `FurnaceExtractEvent` (player-only, extracted amount, ore-to-ingot values in config), on `SmithItemEvent` (netherite upgrades) and anvil repairs; a quality roll on smithed or crafted gear stored in PDC and read by an equipment attribute source. Later: reforge, temper, entropy, realm materials.

### Crafting
**Status: disconnected.** One reachable listener records crafts and nothing uses the record; the crafting skill and quality code are unreachable.

1. **XP source.** None. `Listener/CraftingListener.java:19-31` (registered, `CerberusPlugin.java:577`) stores a clone of each crafted result per player; the only reader is `CraftingSkill.getCraftedItem` (`Skills/CraftingSkill.java:53-61`), which nothing calls.
2. **Balance and exploits.** Nothing to balance. The listener as a base: it runs at LOWEST priority, so a later plugin's cancel is not seen; it records `getInventory().getResult()`, one result even for a shift-click that crafts many; its map is never cleared on quit (`:17`, a small leak). Risks for the loop: craft-and-uncraft loops (ingot ↔ block, nugget ↔ ingot, dyes and wool), the Crafter block (no player), cheap high-XP recipes.
3. **Reachable effects.** None (recording the result changes nothing).
4. **Disconnected / placeholder.**
   - `CraftingSkill.applyEffect` (`CraftingSkill.java:21-48`): `applyCraftingEfficiency` is an empty stub (`CraftingManager.java:407-414`); +5 max health per level per call; "advanced recipes" at 50 is a chat line.
   - `CraftingManager.applyCraftingBonuses` (`:64-95`), the quality system:
     - a private 8-tier `Quality` enum (`:36-61`) duplicating `Manager/Tier` and `QualityBuffs.Quality`; thresholds run to level 160 (`:98-128`) against the level cap of 100 (`Skills/SkillProgression.java:17`), so Perfect, Godly and Primordial can never roll;
     - `applyItemQuality` (`:131-141`) writes the crit, lifesteal and attack-speed bonuses through fresh metas, then writes back its own older meta, which discards them; an unnamed item is likely renamed " (Weak)";
     - `applyDurability` (`:199-215`) treats damage as remaining durability: a new item takes the "broken" branch, gets " [Broken]" in its name and is set to full damage; `applyCraftingEfficiency(item, level)` (`:162-175`) uses the level as a fraction (lore "+5000%"). Likely result if wired: every crafted tool or armor piece named "[Broken]" at zero durability (read, not run);
     - `addAttackSpeedBonus` +5 attack speed on Rare (`:275-282`); none of the PDC keys it writes (`critical_chance`, `lifesteal`, `durability_regen`, `luck_bonus`) is read anywhere (grep);
     - 17 methods with empty bodies (`:394-603`); `trackAndRewardRepairs` gives a free diamond every 10 repairs (`:442-452`).
   - Only custom recipes: two trap recipes never registered (S8).
5. **Attributes.** None.
6. **Progression rewards.** None.
7. **Dependencies.** A recipe registry, an item grade system, an equipment attribute source; the Player Menu "Recipes" tile waits (`GUIs/PlayerMenuGUI.java:155-157`, in development).
8. **Missing gameplay.** XP per meaningful recipe; known recipes that craft deterministically (`docs/design/VISION_ALIGNMENT.md:27`, C5); level-gated recipes; a quality roll on a 1-100 scale (`CERBERUS_DESIGN_PLAN.md:1144-1161`); a net-value rule so reversible recipes pay nothing.
9. **Future interactions.** Gear crafting into attributes, the Crafter and machines, collections, economy, recipe unlocks per player.
10. **Now vs later.** Now: XP on `CraftItemEvent` at MONITOR, player-only, with the real crafted count, from a recipe allowlist with per-recipe XP and zero for compression recipes; a first handful of Cerberus gear recipes. Later: unlock UI, affixes, Crafter rules.

### Woodcutting
**Status: disconnected**, thin example code.

1. **XP source.** None. An axe kill trains Weapon Mastery (`skills.yml:52-54`), not Woodcutting.
2. **Balance and exploits.** Nothing to balance. Future risks: placed logs, bone-mealed tree farms, TNT.
3. **Reachable effects.** None.
4. **Disconnected / placeholder.** `Skills/WoodcuttingSkill.java:17-33`: the same look-at-block duplication as Mining and example health and strength bonuses. `ResourceYieldManager` knows six overworld logs only (`:101-107`): no mangrove, cherry, pale oak, crimson or warped stems, wood or stripped variants.
5. **Attributes.** None.
6. **Progression rewards.** None.
7. **Dependencies.** The same registry, placed-block tag and HUD as Mining.
8. **Missing gameplay.** XP per log, felling speed, sapling and apple rolls.
9. **Future interactions.** Chainsaw power tool (`GEAR.md:190`), custom trees, wood for crafting.
10. **Now vs later.** Now: config entries on the Mining infrastructure plus a placed-log check. Later: chainsaw, custom trees.

### Farming
**Status: disconnected.**

1. **XP source.** None.
2. **Balance and exploits.** Nothing to balance. Future rules: every crop is player-planted, so reward ripe crops (maximum age) rather than unplaced blocks; bone meal from dispensers, water, piston and villager farms involve no player; sugar cane, bamboo and cactus grow from placed stock.
3. **Reachable effects.** None.
4. **Disconnected / placeholder.** `Skills/FarmingSkill.java:21-45`: up to three look-at-block duplications per call (`:29, 59-83, 105`); crop names passed as skill names ("Wheat"), so their level lookups return the default (`ResourceYieldManager.java:118`); a chat line per crop; tool bonuses matched on display-name text (`:110-134`); example health and strength. Crop list in `ResourceYieldManager.java:93-100`.
5. **Attributes.** None.
6. **Progression rewards.** None.
7. **Dependencies.** The gathering registry; crop age; overlaps Herbalism, which claims the same crops (`Skills/HerbalismSkill.java:75-80`).
8. **Missing gameplay.** XP per ripe harvest, replanting, yield and seed rolls.
9. **Future interactions.** Reaper scythe (`GEAR.md:190`), food for Cooking, reagents for Alchemy.
10. **Now vs later.** Now: ripe-crop XP on `BlockBreakEvent` and `PlayerHarvestBlockEvent` (berries) through the same registry. Later: tools, custom crops.

### Fishing
**Status: disconnected.** No XP and no level effect. One reachable fishing event exists, independent of the skill, and the level's Fortune is display-only.

1. **XP source.** None. `CerberusPlugin.onPlayerFish` (`CerberusPlugin.java:934-943`, registered with the plugin at `:512`) only rolls the Sea Monster.
2. **Balance and exploits.** No XP. The reachable event: 5% per catch, no cooldown, no cap per player (`CustomEntities/SeaMonsterManager.java:20-24`, pruned only on death or removal, `Listener/MobScalingListener.java:167`), spawned at the player's feet (`CerberusPlugin.java:939`). An AFK fish farm keeps producing Rare ×2.5 XP mobs (`src/main/resources/mobs.yml:41-45`) whose kills pay weapon-skill XP and whose hits can pay defensive XP (the idle-farm decision, S6). Not tested.
3. **Reachable effects.** The Sea Monster spawn (a Guardian, attack 15, registry-tagged, `CustomEntities/SeaMonster.java:26-35`). It does not read the Fishing level.
4. **Disconnected / placeholder.** `Skills/FishingSkill.java:31-49`: the look-at-block duplication; chat on every call ("You feel lucky!", "fish faster"; the speed value is unused, `:64`); "Kraken" and "Leviathan" both spawn the same generic monster (`:51-56, 72-86`); example health and strength. `ResourceYieldManager.java:108-109` maps `FISHING_ROD` as a block (dead).
5. **Attributes.** Fortune +0.5 per level (S5); its skill page shows "Fortune: not used in the game yet" (`SkillView.java:230,238`).
6. **Progression rewards.** None that play reads.
7. **Dependencies.** `PlayerFishEvent` (handler exists), a Fortune reader, loot tables, an idle check.
8. **Missing gameplay.** XP per catch (fish, treasure, junk), rare catches scaled by level and Fortune, a level-scaled and capped Sea Monster chance.
9. **Future interactions.** Harpoon and deep-net (`GEAR.md:191`), Sea Monster apexes, fish for Cooking and Alchemy.
10. **Now vs later.** Now: XP in the existing handler, an idle check, a cap and level scaling for the monster. Later: harpoons, custom catches, Fortune-driven loot.

### Alchemy
**Status: placeholder.**

1. **XP source.** None (no `BrewEvent` handler).
2. **Balance and exploits.** Nothing to balance. Brewing stands run on hoppers; `BrewEvent` has no player.
3. **Reachable effects.** None.
4. **Disconnected / placeholder.** `Skills/AlchemySkill.java:21-53` stops on the null item (`:27-32`). Behind it, `CraftingManager.increasePotionEffectiveness`, `increasePotionDuration` and `brewRarePotion` only send chat or rename (`CraftingManager.java:359-383`); mana bonuses re-added each call (`:44-49`).
5. **Attributes.** None.
6. **Progression rewards.** None.
7. **Dependencies.** Healing and regeneration potions do nothing for players (S7); no Essence potions; pills and realm breakthroughs are design-only (`docs/design/CULTIVATION_REALMS.md:138-150`); the Alchemist class needs Alchemy 15 (`CLASSES.md:110`, design-only); Herbalism reagents.
8. **Missing gameplay.** XP for brewing; Cerberus potions that act on the health pool, Essence or attributes; level-gated recipes.
9. **Future interactions.** Pills for breakthroughs, Alchemist concoctions, Spirit Rounds ammo (`WEAPONS.md:84`).
10. **Now vs later.** Now: XP for brews a player takes out (credit the last player who used the stand); potions as timed `BuffSource` buffs or pool heals. Later: pills, realm reagents.

### Enchanting
**Status: disconnected**, with a placeholder entry point (the item-changing code is real but can never receive an item).

1. **XP source.** None (no enchanting-table or anvil-book handler). Holding an enchanted book routes kill XP to Arcane Knowledge (`skills.yml:63-67`), whose page claims enchanted books (`SkillDefinitions.java:242-244`): a concept overlap to settle.
2. **Balance and exploits.** Nothing to balance.
3. **Reachable effects.** None; vanilla enchanting works unchanged.
4. **Disconnected / placeholder.** `Skills/EnchantingSkill.java:23-54` stops on the null item (`:29-34`); behind it, Sharpness + level × 0.03 per call, stacking past vanilla limits, or Unbreaking up to 3 (`:62-78`). `CraftingManager.increaseEnchantmentEffectiveness` sets each enchantment to level + skill × level (`:349-356`): Efficiency V at skill 50 becomes 255. Mana bonuses re-added each call.
5. **Attributes.** None.
6. **Progression rewards.** None.
7. **Dependencies.** The enchanting design (`SYSTEMS_BASE_LAYER.md:7-26`: `enchants.yml`, slots by realm, inside the 6× / 40% / 75% budgets) and gear; vanilla enchantment levels enter the damage before Power and outside the offense budget (`Manager/PveOffense.java:71`).
8. **Missing gameplay.** XP for enchanting; Cerberus enchantments inside the caps.
9. **Future interactions.** Enchant slots by realm, tool enchantments (speed, yield), Essence costs.
10. **Now vs later.** Now: XP per vanilla enchantment (levels spent) is cheap and safe. Cerberus enchantments wait for equipment and the budget design.

### Herbalism
**Status: disconnected.** The most complete-looking handler of the group, never called.

1. **XP source.** None. `HerbalismSkill.handleBlockBreak` (`Skills/HerbalismSkill.java:48-70`) has no caller (grep).
2. **Balance and exploits.** If wired as written: drops × (1 + 0.1 × level), ×11 at 100 (`:54`); unripe crops count; bone-mealed flowers and grass are an endless supply; each break of nether wart, wheat or sugar cane gives Strength (up to III), Regeneration or Speed for 10 s + 1 s per level (`:110-136`), and vanilla Strength raises damage outside the attribute caps; 10% golden apple per sweet-berry break, bushes regrow (`:120-124`; overflow lost by `addItem`).
3. **Reachable effects.** None.
4. **Disconnected / placeholder.** `applyEffect` (`:27-45`) works on a dummy `new ItemStack(Material.POTION)` (`:35-38`) and re-adds mana bonuses each call.
5. **Attributes.** None.
6. **Progression rewards.** None.
7. **Dependencies.** A scope decision against Farming (same crops, `:75-80` vs `ResourceYieldManager.java:93-100`); Alchemy reagents; the Druid class needs Herbalism 15 (`CLASSES.md:109`, design-only).
8. **Missing gameplay.** XP for wild plants, flowers and mushrooms; herbs as reagents.
9. **Future interactions.** Pill reagents, Druid, collections.
10. **Now vs later.** Now, after the scope decision: entries on the gathering registry (wild plants, no bone-meal growth). Later: reagents.

### Cooking
**Status: placeholder.**

1. **XP source.** None (no furnace, smoker or campfire handler).
2. **Balance and exploits.** Nothing to balance. Future risks: auto-smokers, cook-and-eat loops.
3. **Reachable effects.** None.
4. **Disconnected / placeholder.** `Skills/CookingSkill.java:18-31`: `applyFoodQualityBonus` calls an empty `enhanceFoodItem` and sends chat (`CraftingManager.java:473-483`); +1 max health per level per call.
5. **Attributes.** None.
6. **Progression rewards.** None.
7. **Dependencies.** Food heals nothing (S7); `Attributes/BuffSource` ("later potions and consumables", `BuffSource.java:12-13`) is the natural carrier; Farming and Fishing inputs.
8. **Missing gameplay.** XP for cooking; meals that restore health or Essence or give timed attribute buffs.
9. **Future interactions.** Feasts, realm food, economy.
10. **Now vs later.** Now: XP when a player takes cooked food from a furnace, smoker or campfire; two or three meals as `BuffSource` buffs. Later: recipes, quality.

---

## Reusable for Mining → Smithing → Equipment

### Keep and reuse (reachable, sound)

| Component | Today | Use in the loop |
|---|---|---|
| `Skills/SkillManager.addXpAndCheckLevelUp` (`:343-355`) with `SkillProgression` | Reachable; overflow-safe, capped at 100, coalesced async saves, `LevelListener` refreshes attributes and class unlocks (`CerberusPlugin.java:440-444`); unit-tested | The only award path. Don't copy the chat per gain (`MobKillListener.java:90`) or per level (`SkillManager.java:269-271`) for blocks: use the HUD |
| `Manager/PlayerHUDManager.flash` (`:119`) | Reachable (cast feedback uses it) | The merged "+86 Mining XP" display (`docs/ROADMAP.md:100-105`) |
| `Attributes/KillXp` (`:31-45`) and `attributes.yml kill-xp` (`:10-17`) | Reachable, unit-tested | Template for a pure `GatherXp`: resource tier × level, grey-resource rule, per-event ceiling, config block |
| `Classes/CastXpLedger` | Reachable, unit-tested | Template for per-chunk or per-node XP budgets with eviction (anti-farm for regrowing resources) |
| `Manager/ChunkDiscoveryManager` (`:62-83`) | Reachable | "First time only" set, async DB write, skip while the player is loading |
| `Manager/MobRegistry` (YAML → entries, PDC id → O(1), reload keeps the old config on error) | Reachable | Template for `resources.yml`, recipes and a gear registry (`GEAR.md` §3 mirrors it) |
| `cerberus/world/cerb/ConfigSkillMapper` | Reachable for kills; unit-tested | Material and regex matching with priorities, reusable for block → resource and tool → capability |
| `cerberus/world/cerb/ShippedConfig` | Reachable, unit-tested | Ship `resources.yml` so later updates reach unedited installs |
| `Attributes/AttributeSource`, `Contribution` (weapon-scoped), `AttributeService.markDirty`, `BuffSource` as the model, `attributes.yml limits` (`:68-75`) | Reachable | The equipment source planned in `docs/ATTRIBUTES_ENGINE.md:28,35`: gear contributes Power, Fortitude or stats inside the 6× offense budget, 40% armor and 75% total caps; "no second item-stat engine" (`docs/ROADMAP.md:99`) |
| `Listener/CustomArmorListener` (`:30-64`) | Reachable | Moments to snapshot equipment on the player's thread (add held-item and armor-change events) |
| `Manager/SecondaryEffects` (`:71-90`, transient, never-saved modifiers) | Reachable | Gathering speed through the vanilla block-break-speed attribute (`ATTRIBUTE_EXPANSION.md:41`) |
| `Manager/ScalingFormula` (bounded presets, hard caps) | Reachable | Yield and quality curves with hard caps (`CERBERUS_DESIGN_PLAN.md:1138`: STEADY, at most +50% quantity) |
| `Manager/PlayerDefenseManager.updateDefense` + `combat.yml armor.reduction-per-piece` (`src/main/resources/combat.yml:20-28`, `Manager/CombatConfig.java:368-378`) | Reachable; reads the material only | Keep for vanilla pieces. Add crafted quality through Fortitude or the equipment source, not here, so the caps stay in one place |
| `GUIs/player/EquipmentMenu`, Player Menu "Recipes" and "Collections" tiles (`PlayerMenuGUI.java:152-157`), `SkillDefinitions` training lines and statuses (`SkillCatalogTest`) | Reachable | Light up as the loop lands; training lines must come from code constants |
| `tools/playtest/load.js` gather and craft modes (`:16-20, 112-160`) | Ready | The performance baseline; no numbers are recorded in `docs/PERFORMANCE.md` yet |
| `ClassProfile` versioned document per player (`player_classes`), `AsyncSaveManager`, `SaveThrottle` | Reachable | Per-player recipe unlocks or collections |

### Replace (unreachable, or wrong if wired)

| Component | Why |
|---|---|
| `Manager/ResourceYieldManager` | Duplication, unbounded multiplier, no tool, Fortune or Silk Touch awareness, stale material list (Mining point 4) |
| `MiningSkill`, `WoodcuttingSkill`, `FarmingSkill`, `FishingSkill`, `SmithingSkill`, `CraftingSkill` `applyEffect` | Never called, shared-object level always 1, stacking stat bumps, chat spam (S3) |
| `Manager/CraftingManager` (604 lines) | Unreachable; duplicate enum, level-160 ladder, lost bonuses, "[Broken]" items, unsafe enchantments, a diamond faucet. Keep at most the idea of level-banded quality rolls |
| `cerberus/world/cerb/QualityBuffs` + `Listener/PlayerItemListener` | Never constructed. Bound to one player from its constructor (`QualityBuffs.java:17,23-28`); resets vanilla max health, attack damage and armor bases on every inventory click (`:56-73`); unsafe Sharpness on held items (`:76-81`) |
| `Manager/LuckManager`, `Manager/MagicFindManager` | Unreachable. An uncapped product of per-skill luck factors (`LuckManager.java:72-80, 91-104`): about ×13 with Scavenging and Lockpicking at 100, because two of its four skills ("Magic Find", "Exploration") don't exist and read as level 1; plain `HashMap`s on Folia; Magic Find reads the database on the calling thread when not cached (`MagicFindManager.java:41-53`). Fortune replaces both (`docs/STATUS.md:54`) |
| `Manager/PlayerStrengthManager` | Only dead code calls it; compounding change to the saved vanilla attack-damage base. Power replaced it |
| `Listener/CraftingListener` | Registered but useless; replace or extend into the crafting-XP listener (MONITOR priority, real crafted count, recipe identity, cleared on quit) |
| `CustomPlayer.currentCraftItem` | No setter caller |
| `Manager/Tier.craftStatMultiplier`, `Tier.fromLegacyQuality`, `combat.yml tiers.*.craft-stat-multiplier` (`:106-116`) | No callers; the whole `tiers:` block is not read by any Java (grep "tiers"; `MobRegistry.java:40` wrongly says combat.yml overrides tier XP). Item grades vs realm names is an open decision (`docs/design/VISION_ALIGNMENT.md:30`, C8; one realm curve, C1 `:23`) |
| `skills.yml:18-19` comment | States a block-break XP source that doesn't exist |

### Dangerous if wired as written

1. **Duplication:** `ResourceYieldManager.applyYieldBonus` (`:135-152`) drops the looked-at block's loot without breaking it (called by Mining, Woodcutting, Farming and Fishing `applyEffect`); `handleBlockBreak` (`:57-74`) adds drops on top of vanilla's.
2. **Unbounded multipliers:** yield 1 + 0.1 × level (`ResourceYieldManager.java:119`, `HerbalismSkill.java:54`); the luck product; enchantment levels × skill (`CraftingManager.java:349-356`); Sharpness level/10 (`:337-340`); +5 attack speed (`:275-282`); bow damage base × quality on every click (`QualityBuffs.java:108-111`).
3. **Bypassing the attribute caps:** vanilla enchantments and item attack-damage modifiers are part of the raw hit that Power multiplies (`PveOffense.java:71`), outside the 6× offense budget; Herbalism's Strength potions do the same. Gear stats must enter through an attribute source, not item modifiers.
4. **Saved vanilla attribute drift:** `PlayerStrengthManager.increaseStrength`; `QualityBuffs.removeQualityBuffs` (`:66-72`).
5. **Free items:** a diamond every 10 repairs (`CraftingManager.java:442-452`); 10% golden apple per sweet-berry break; the unused 1% diamond-from-stone table.
6. **Folia:** unsynchronised maps and a database read on the region thread (`LuckManager`, `MagicFindManager`).
7. **Item corruption:** "[Broken]" names and full damage, lost bonuses, renamed items (`CraftingManager.java:131-215`).

---

## Group table

| Skill | Status | XP source | What its level does |
|---|---|---|---|
| Mining | disconnected (+ placeholder) | none | nothing |
| Smithing | disconnected (placeholder entry) | none | nothing |
| Crafting | disconnected | none (`CraftingListener` records crafts, unused) | nothing |
| Woodcutting | disconnected (+ placeholder) | none | nothing |
| Farming | disconnected | none | nothing |
| Fishing | disconnected | none (catches only roll a level-independent Sea Monster) | +0.5 Fortune per level, shown only |
| Alchemy | placeholder | none | nothing |
| Enchanting | disconnected (placeholder entry) | none | nothing |
| Herbalism | disconnected | none | nothing |
| Cooking | placeholder | none | nothing |

None is functional or partial. Admins can set levels (`/cerb skill`, Skill Edit menu); only Fishing's level shows anywhere (Fortune), and nothing reads it.

## Highest-value missing loops in this group (value to players vs effort)

1. **Mining → Smithing → Equipment** (highest value, highest effort; the owner's next build). It is the only loop here that turns non-combat play into combat power, and its infrastructure unlocks the rest: a resource registry, a placed-block tag, gathering-XP rules, the HUD XP display and an equipment attribute source. Order: Mining XP on block breaks; Smithing XP on furnace extraction and smithing-table and anvil work; Crafting XP on gear recipes (real crafted count, no XP for reversible recipes); crafted gear carries a grade in PDC that an attribute source turns into Power or Fortitude inside the existing caps. Reuses the award path, `KillXp`-style rules, `MobRegistry`-style config, `AttributeSource` and the gather and craft loads.
2. **Fishing XP** (medium value, lowest effort). `CerberusPlugin.onPlayerFish` already runs on every catch: add a per-catch award, make the monster chance read the level, cap monsters per player, and add the idle check the owner still has to decide (`docs/STATUS.md:409`). A first reader for Fortune (rare catches) fits here.
3. **Woodcutting and Farming on the Mining infrastructure** (medium-high value, low effort after loop 1). Mostly configuration: logs with the placed-block check, crops by ripeness, `PlayerHarvestBlockEvent` for berries.

Later, in this order: Cooking and Alchemy (need consumables that act on the health pool or `BuffSource`, since vanilla healing is off; Alchemy also waits on pills and realms); Herbalism (needs the scope decision against Farming); Enchanting (needs the enchant budget design and equipment first; vanilla enchant XP alone is cheap if wanted earlier).

## Not confirmed (how I checked)

- `CraftingManager`'s exact item results ("[Broken]" at full damage, lost bonuses, " (Weak)" names): read from the code and Bukkit's copy-on-read `getItemMeta` semantics; not run.
- `Block#getDrops()` without a tool ignoring Fortune and Silk Touch: Paper API behaviour, not run.
- The AFK fish-farm Sea Monster loop: inferred from the handler having no cooldown or cap; not tested.
- The ×13 luck figure: computed by hand from `LuckManager.java:98-101` (1.05 × 4 × 1.04 × 3), with the two missing skills at the default level 1; the code is unreachable anyway.
- Line numbers are from commit `17097b1`; the working tree changed (a commit landed) while I was reading, so I re-checked the `CerberusPlugin.java` lines after it.
