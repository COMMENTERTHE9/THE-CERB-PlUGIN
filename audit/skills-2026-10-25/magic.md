# Skill audit: the magic group (7 skills)

Read-only audit of `/home/user/THE-CERB-PlUGIN` at HEAD `17097b1` (2026-10-25). The class wrap-up commits that landed during the audit were the uncommitted files already read; every line cited below was re-checked on HEAD.

**Path conventions.** Java paths are relative to `src/main/java/`. `*.yml` files are in `src/main/resources/`. `docs/…` and `tools/…` are from the repo root.

**Evidence.** **Read** = code inspection (this audit). **Played** = taken from `docs/STATUS.md` or the playtest scripts, not re-run here.

## Group-wide findings

These apply to several skills. The per-skill blocks refer back to them.

**G1. No normal (non-op) player can cast a spell, so the whole Essence tree is inert for them.**
- The only spell item, the Fireball Wand, comes from `/givetestitem`. That command needs `cerberus.give.testitem`, which defaults to op (`Commands/GiveTestItemCommand.java:65-88`, `plugin.yml:23-26,44-46`).
- Nothing else creates a `cerb_spell` item: no recipe, loot or mob drop. A grep finds only the command and the listeners that read the key.
- The only other casts are Mage abilities. Mage needs Intelligence 5:
  - `classes.yml:32-37`;
  - `Classes/ClassDef.canLearn:18-23`;
  - there is no starter grant (`Classes/ClassService.learnEligible:275-290`); only the admin `setRank` (`:303`) bypasses it.
- Intelligence XP comes only from spell casts (`Spells/SpellManager.java:85-95`).

The loop is circular: without an admin, Intelligence stays at 1 and the Mage class stays closed. The playtest checklist sets Intelligence 5 by command (`docs/PLAYTEST_CHECKLIST.md:73`). Warrior and Duelist abilities cost no Essence (`abilities.yml`), so a non-op never spends mana. Every Essence output (max mana, regeneration, spell damage, spell cooldown) is a number on the Stats page with nothing to act on.

The menus point players into this dead end:
- the Classes screen lists "Intelligence 5 (you: 1)" (`Classes/ClassService.java:651-663`);
- the Intelligence tile says "● Trains: Cast a spell successfully" (`GUIs/player/SkillView.java:84-89`, `GUIs/SkillDefinitions.java:239-241`).

**G2. Skill cast XP is paid at launch, with no target.**
- The Mage path, `SpellManager.castForAbility:78-83`, pays Spell Weaving 15, Elemental Mastery 10 and Intelligence 5 whenever `FireballSpell.castFromAbility` returns. That method always returns true (`Spells/FireballSpell.java:91-97`).
- The wand path, `SpellManager.castSpell:55-66`, pays on any cast whose 20 mana was paid (`FireballSpell.java:67-70`).
- Class XP got the fix "Fireball paid class XP fired into the sky" (`docs/ABILITY_AUDIT.md:132`); it now pays on burst (`Classes/AbilityCaster.java:282,315-318`). Skill XP did not.
- Mana regeneration outpaces the fastest spend: at least 51.7 per second at base, against 20 per 0.67 s or more (at most about 30 per second). An autoclicker aimed at the sky therefore farms forever.
  - Wand (Played interval: 1,037 ms at Spell Weaving 1, `docs/STATUS.md:110`): about 3,470 casts per hour → Spell Weaving ≈52k, Elemental Mastery ≈35k, Intelligence ≈17k XP per hour. Spell Weaving reaches level 20 (132.5k cumulative) in about 2.5 h.
  - Mage Fireball (2 s, at least 1.33 s after cooldown recovery; `abilities.yml:108-119`, `AbilityCaster.java:149-151`): 1,800–2,700 casts per hour.
- Cast XP has none of the protections class XP has:
  - no grey-mob ×0.25;
  - no per-mob `CastXpLedger`;
  - no enemy requirement;
  - no idle check.
- It is also flat: 5/10/15 XP whatever the foe. Kill XP v2 scales 40 → 334 from level 1 to level 50 (`attributes.yml:10-17`), so the cast-only skills (Intelligence, Elemental Mastery) fall far behind at higher tiers.

**G3. Each skill's own effect code is unreachable or a no-op. The only live effects are the attribute weights.**
- `EffectManager.applyMagicSkillEffect` (`Manager/EffectManager.java:82-104`) is called only from `FireballSpell.java:65,92`, always with "Elemental Mastery". That case lands on a body that only declares two locals (`:163-167`).
- No caller uses the other names. `DefensiveMagicSkill` does not even override `applyEffect`.
- `PlayerManaManager` has five magic hooks, none with a caller (grep):
  - `applyDynamicSkillInteraction:368`;
  - `applyCrossSkillBenefits:382`;
  - `checkForMagicSurge:394`;
  - `checkForElementalSynergy:414`;
  - `activateManaShield:463`.
- The `magicDamageMultiplier` field (`:325-333`) and the `spellDamage`, `spellRange` and `currentSpellManaCost` metadata are written but never read. Fireball uses constants: 20 mana, 10 damage (`FireballSpell.java:34-35`).
- The live effects are the weights in `attributes.yml:41-46` and the cooldown weights at `:110,120`.

**G4. Don't wire the dormant `applyEffect` bodies as they are.** They hold "Example" numbers that stack on every call and break the caps:

| Skill | What the dormant code does | Where |
|---|---|---|
| Intelligence | adds 500·L^1.2 max mana and 20·L^1.1 regen per call | `Skills/IntelligenceSkill.java:23,28` |
| Arcane Knowledge | −10% mana cost per level (over 100% at level 11); synchronous JDBC on the region thread | `Skills/ArcaneKnowledgeSkill.java:31`; `:42-44` → `cerberus/world/cerb/DatabaseManager.saveSkillEffect:424` |
| Elemental Mastery | +5% crit and +5% lifesteal per level; about 10 chat lines per call through the stubs | `Skills/ElementalMasterySkill.java:51,79`; `SpellManager.java:109-177` |
| Mana Regeneration | regen × (5 + 0.5·L) | `Skills/ManaRegenerationSkill.java:22` |
| Spell Weaving | compounding multipliers | `Skills/SpellWeavingSkill.java:55-72` |

Arcane Knowledge, Elemental Mastery, Summoning and Defensive Magic also read `this.getLevel()` on the shared skill object, which is always 1 (no `setLevel` caller). The attribute engine supersedes all of this.

---

### Intelligence

**Status: partial.** The XP path exists, but a normal player can't reach it (G1). The level's Essence and the Mage unlock work. Its own effect code is disconnected.

1. **XP source.**
   - +5 per successful spell cast (`SpellManager.java:18,94`): the Fireball Wand (`Listener/CustomItemListener.java:44-76` → `castSpell`) or the Mage Fireball ability (rank 3, `abilities.yml:108-114`; `AbilityCaster.java:282` → `castForAbility`).
   - It is not in the kill routing (`skills.yml:22-76`).
   - Cost of levels: level 5 = 2,292 XP = 459 casts; level 20 = 26.5k casts; level 100 ≈ 2.64M casts (computed from `Skills/SkillManager.xpForLevel:417-421`).
2. **Balance and protection.**
   - A cast with no mana pays nothing.
   - Cooldowns: the wand 1 s (`CustomItemListener.java:36,67-68`); the ability 2 s and 20 Essence.
   - Weak points: air-cast farming (G2), flat XP, no grey-mob rule, no ledger.
3. **Reachable effects.** Essence +1.0 per level (`attributes.yml:42`), which raises:
   - max mana ×(1+0.01·E) and regeneration ×(1+0.01·E) (`Manager/AttributeEffects.applyMana:67-78`);
   - spell damage against mobs ×(1+0.02·E) (`Attributes/AttributeCalculator.java:77-79`, `Manager/PveOffense.spellDamage:46-50`, `Listener/VirtualHealthListener.java:258`);
   - spell cooldown recovery, 0.002 per Essence point (`attributes.yml:110`, `Manager/SecondaryEffects.cooldownMs:93-96`).

   At level 100 alone: 2,025 mana, 101 per second, Fireball ×3.05, wand cooldown 855 ms. Played with admin-set levels: Intelligence and Essence Regeneration at 100 give about 3,000 mana, about 150 per second and Fireball ×5 (`docs/STATUS.md:84`, `progression.js magic`). It also opens Mage at level 5 (`classes.yml:37`).
4. **Disconnected or placeholder.**
   - `IntelligenceSkill.applyEffect:16-34`.
   - EffectManager's "Intelligence" case (+100 max mana, `EffectManager.java:84-85,144-151`), never dispatched.
   - `PlayerManaManager.checkForMagicSurge:394-412`, which needs Intelligence 5; no caller.
5. **Attributes.**
   - Essence 1.0 per level, the largest Essence weight (tied with Essence Regeneration).
   - Spell damage is PvE only: the player-target branch of `VirtualHealthListener` (`:161-243`) has no `spellDamage` call, so wand Fireballs hit players for a raw 10.
   - The cooldown bonus pauses while PvP-tagged (`SecondaryEffects.paused:61-63`, `attributes.yml:104`).
6. **Progression rewards.** One real milestone: Mage at 5, with new abilities and loadouts. Otherwise continuous percentages.
7. **Dependencies.** All built:
   - SpellManager and FireballSpell;
   - CustomItemListener (op-only wand);
   - ClassService and AbilityCaster;
   - AttributeService, AttributeEffects and PlayerManaManager;
   - SecondaryEffects and PvpTag;
   - PveOffense.

   Missing: any spell or use of Essence before Mage.
8. **Missing gameplay.**
   - A training route for normal players.
   - XP from Arcane Bolt, Frost Nova and Blink, which pay class XP only (`AbilityCaster.java:257-286`).
   - XP that scales with the foe.
9. **Future interactions.**
   - Classes: Mage (design entry Intelligence 10 + Spell Weaving 5, `docs/design/CLASSES.md:101`); Arcanist (Intelligence 20, `:104`); Cleric (Intelligence 10, `:106`).
   - Reservoir Inversion: +0.04 s duration per level and gated at Intelligence 20 (`docs/design/CERBERUS_DESIGN_PLAN.md:1519,1528`).
   - Gear: the Focus/Orb is governed by Intelligence + Essence Regeneration (`:813`).
   - Realms: realm magnitude already multiplies spell damage (`PveOffense.java:48`; everyone is Awakened ×1).
10. **Now vs later.** Now: a bootstrap route and on-hit cast XP under the class-XP rules (see the ranked loops below). Later: Inversion, Arcanist and Cleric, Focus gear, realms.

### Arcane Knowledge

**Status: partial.** It trains from real play, by an odd route. Its level adds only generic Essence; its own identity is disconnected.

1. **XP source.**
   - Kill XP v2 when the mob dies while the main hand holds an `ENCHANTED_BOOK` (`skills.yml:63-67`, `MobKillListener.java:57-89`): 40 × (1 + 0.15·(L−1)) × tier (`Attributes/KillXp.compute:31-40`).
   - The second route, an ARCANE `elemental_tag` (`MobKillListener.java:128-133,159,173`), is dead: nothing writes `elemental_tag`; it is only read (`:37,151-153`).
   - There is no cast XP: Arcane Bolt carries the arcane element but pays no skill XP.
2. **Balance and protection.**
   - Grey mobs pay ×0.25 at 20+ levels below the skill (`MobKillListener.java:81-85`, `attributes.yml:14-16`); there is a cap per kill.
   - Routing follows the item in hand at the moment of death (`:57-60`). A player can damage with a sword and land the final hit, or let a burn finish, with a book in hand (Read, not Played).
   - A book hits as `WeaponClass.OTHER` (fist-strength; `Attributes/WeaponClass.java:27-38`).
   - There is no inflation (one kill, one award), but the loop is unthematic.
3. **Reachable effects.** Essence +0.5 per level (`attributes.yml:46`): the Intelligence outputs at half rate. At level 100 alone: 1,530 mana, 76.5 per second, spells ×2.06, wand cooldown 911 ms. Useful only to casters (G1).
4. **Disconnected or placeholder.**
   - `ArcaneKnowledgeSkill.applyEffect:24-45` and `loadAndApplyEffects:47-59` have no callers.
   - The EffectManager case is commented-out code (`EffectManager.java:154-160`).
   - The page says "Enchanting and magic item bonuses — Not active yet" (`SkillDefinitions.java:242-244`), which is honest.
5. **Attributes.** Essence 0.5 per level; the same PvE/PvP split as Intelligence.
6. **Progression rewards.** Percentages only. No milestone, no unlock.
7. **Dependencies.**
   - Built: MobKillListener and ConfigSkillMapper.
   - Not built: the Enchanting system (`docs/design/SYSTEMS_BASE_LAYER.md:9-22`) and anything that writes `elemental_tag`.
8. **Missing gameplay.**
   - A thematic XP source: enchanting, arcane spell hits.
   - An effect of its own, for example an Essence cost reduction. `AbilityCaster.java:146` reads `a.essenceCost()` raw.
9. **Future interactions.**
   - Classes: Arcanist (Arcane Knowledge 20), Warlock (15), Necromancer (+10) (`CLASSES.md:104,111,112`).
   - Reservoir Inversion: −0.3 s cooldown per level (`CERBERUS_DESIGN_PLAN.md:1520`).
   - Gear: Wand/Staff governed by Arcane Knowledge + Spell Weaving + Elemental Mastery (`:812`).
   - Enchanting costs Essence (`SYSTEMS_BASE_LAYER.md:18`).
10. **Now vs later.** Now: XP from vanilla enchanting (`EnchantItemEvent`, by levels spent) and from arcane spell hits under the class-XP rules. Later: an effect of its own with the enchanting and gear systems; Arcanist and Warlock; Inversion.

### Elemental Mastery

**Status: partial.** XP comes only from Fireball casts (the op wand or Mage rank 3), and the level adds only generic Essence. Everything elemental is disconnected.

1. **XP source.**
   - +10 per successful Fireball cast (`SpellManager.java:17,90-92`) and nothing else.
   - Frost Nova (ice) and Arcane Bolt (arcane) pay no Elemental Mastery XP.
   - The elemental-tag kill route never returns "Elemental Mastery": FIRE maps to "Pyromancy", ICE to "Cryomancy" and so on, skills that don't exist (`MobKillListener.java:168-179`). The route is dead anyway.
   - Normal players can't reach it (G1). Level 20 = 13,250 casts.
2. **Balance and protection.** As Intelligence: paid at launch, farmable into the air (≈35k XP per hour with the wand), flat, no grey-mob rule (G2).
3. **Reachable effects.**
   - Essence +0.5 per level (`attributes.yml:45`). It boosts all spells, Arcane Bolt included, not elemental damage specifically.
   - Burn and slow do not scale with it: Fireball's burn is a flat 60 ticks (`FireballSpell.java:174`) and Frost Nova's slow is fixed (`abilities.yml:132`).
4. **Disconnected.**
   - `ElementalMasterySkill.applyEffect:18-36` has no caller.
   - The one reachable call (`FireballSpell.java:65,92` → `EffectManager.java:90-91,163-167`) does nothing.
   - The `SpellManager.apply*Multiplier` methods (`:109-177`) only send chat.
   - `ElementalInfusionManager.handleElementalInfusionEffect:89-109`, the only infusion code that reads the Elemental Mastery level, has no caller.
   - `CombatSkillProcessor.applyElementalInfusion:204-220` sets `bonusElementalDamage`, which nothing reads (the getter is only declared, `Manager/CombatContext.java:171`).
   - Nothing writes the `elemental_infusion` key: `ElementalInfusionManager.applyElementalInfusion:24-48` has no caller, and `CraftingManager.applySmithingBonuses:310-315` only renames the item.
   - `PlayerManaManager.applyDynamicSkillInteraction:368-379` (Elemental Mastery + Spell Weaving synergy) has no caller.
   - The page says "Extra elemental spell damage — Not active yet", which is honest. The training line "Cast Fireball successfully: 10 XP" is true, but no normal player can do it (`SkillDefinitions.java:245-248`).
5. **Attributes.** Essence 0.5 per level, PvE.
6. **Progression rewards.** None beyond percentages.
7. **Dependencies.**
   - Built: SpellManager, the Mage Fireball, and the element on `SpellDamageContext` (Fireball "FIRE", the abilities' `element:`).
   - Design only: reactions and an element status manager; Echo Families.
   - Unreachable: infusion via crafting.
8. **Missing gameplay.** An element-specific effect (burn, chill or shock strength or duration by level), XP from every elemental hit, and reactions.
9. **Future interactions.**
   - Classes: Elementalist (Elemental Mastery 15, `CLASSES.md:102`; a Step 6 class).
   - The reaction grammar (`CERBERUS_DESIGN_PLAN.md:1650-1690`), blocked by the TIDE/FROST ICE collision (`:1689`, `docs/design/OPEN_DECISIONS.md:38`).
   - Boss element gates (`CERBERUS_DESIGN_PLAN.md:698`), Echo Families, infused gear.
10. **Now vs later.** Now: XP from fire and ice spell hits under the class-XP rules; burn ticks and slow seconds that scale with level; remove the no-op call. Later: reactions, infusions, Elementalist, families.

### Summoning

**Status: placeholder**, and disconnected. It has no XP and no attribute weight, and its zombie-spawning stub is never called.

1. **XP source.** None:
   - no `addXp*` call uses the name;
   - it is not in the kill routing;
   - the page says "Nothing in the game trains this skill yet" (`SkillView.java:126`).
2. **Balance and protection.** Not applicable.
3. **Reachable effects.** None. It is not in `attributes.yml`, so its page shows no attribute rows.
4. **Placeholder and disconnected.**
   - `SummoningSkill.applyEffect:28-44` spends 30 "Summoning Energy". The mana type is ignored: `PlayerManaManager.spendMana:120-127` draws every type from the one pool.
   - It then spawns a plain hostile zombie at the player (`getSummonableCreature:77-81`, with a TODO), scales its attack and despawns it after 120 s or more through the region scheduler (`summonCreature:46-75`, `:66-72`).
   - The only path to it, `EffectManager.applySummoningEffect` (`:96-97,175-179`), is never triggered.
   - It reads `this.getLevel()`, which is always 1.
   - The page says "Summon creatures — Not active yet" (`SkillDefinitions.java:249-251`), which is honest.
5. **Attributes.** None.
6. **Progression rewards.** None.
7. **Dependencies.**
   - Not built: a `summon` ability verb (designed, `CLASSES.md:184`); ally AI and targeting; TTL, leash and ownership.
   - Design only: summon DPS counted against the offense cap (`docs/design/TALISMANS.md:139`); PvP rules.
8. **Missing gameplay.** All of it: summons, control, upkeep, and XP (for example, from damage your summons deal).
9. **Future interactions.**
   - Classes: Summoner (Summoning 15), Necromancer, Spiritbinder (`CLASSES.md:105,112,115`). Summoner is on the Step 6 list (`docs/ROADMAP.md:65`).
   - The MIRE Binding Pact ritual is designed to use `SummoningSkill` (`CERBERUS_DESIGN_PLAN.md:1568`).
   - The talisman Summon/Companion archetype (`TALISMANS.md:49`).
10. **Now vs later.** Now: keep the page honest and do not connect the zombie stub. Later: the Summoner step with a real summon verb.

### Spell Weaving

**Status: partial, the closest to functional.** It trains from real play (kills with a focus) and its cooldown effect works. But its outputs only matter to spellcasters (G1), and its cast XP is farmable (G2).

1. **XP source.**
   - (a) +15 per successful spell cast (`SpellManager.java:16,88`).
   - (b) Kill XP v2 when the mob dies with a focus in the main hand: `BLAZE_ROD`, `BREEZE_ROD`, `END_ROD`, `STICK`, `.*WAND.*` or `.*STAFF.*` (`skills.yml:32-44`). The toggle `award-kill-xp-for-spell-focus: true` is at `:83`.
   - Route (b) is the only magic-skill XP any normal player can earn. Mage ability kills with a focus in hand pay it too.
2. **Balance and protection.**
   - Kill XP: grey-mob ×0.25 and the per-kill cap.
   - Cast XP: farmable into the air, ≈52k XP per hour with the wand and 27–40k with the Mage Fireball (G2).
   - `END_ROD` trains it by kills but is not a focus for abilities (`Classes/WeaponFamily.java:27`).
   - Kill routing follows the item in hand at death, as for Arcane Knowledge; a stick hits with fist strength.
3. **Reachable effects.**
   - Essence +0.5 per level (`attributes.yml:44`).
   - Spell cooldown recovery +0.003 per level (`attributes.yml:120`, with diminishing returns to the 50% cap at `:110`). It applies to the wand (`CustomItemListener.java:67-68`) and every Essence ability (`AbilityCaster.java:149-151`, never below 1 s).
   - Played: Fireball every 1,037 ms at Spell Weaving 1, every 810 ms at 100 (`docs/STATUS.md:110`). At level 100 alone: wand cooldown 782 ms, spells ×2.06.
4. **Disconnected or placeholder.**
   - `SpellWeavingSkill.applyEffect:32-53` spends 100 "Spell Weaving Power" and compounds a `spellDamage` value that nothing reads. Its only path, `EffectManager.applySpellWeavingEffect` (`:93-94,169-173`), is never triggered.
   - The page says "Cheaper spells — Not active yet" (`SkillDefinitions.java:252-255`), which is honest.
5. **Attributes.**
   - Essence 0.5 per level plus `COOLDOWN_RECOVERY` 0.003 per level, the only skill in this group with a speed stat of its own.
   - PvE; the cooldown bonus pauses while PvP-tagged.
6. **Progression rewards.** Visibly faster casting, but continuous: no milestone and no unlock. The design uses it in class entries: Mage 5, Spellblade 15, Shadowblade 10.
7. **Dependencies.** All built: ConfigSkillMapper and MobKillListener, SpellManager, SecondaryEffects, AbilityCaster.
8. **Missing gameplay.**
   - Cast XP paid on hit.
   - The "weaving" of its name: there is no spell-combination mechanic.
   - Casting speed: spells are instant (`docs/design/ATTRIBUTE_EXPANSION.md:42`).
9. **Future interactions.**
   - Classes: Mage, Spellblade, Shadowblade entries (`CLASSES.md:101,103,113`).
   - Casting speed, once channelled spells exist; spell combinations through the reaction grammar.
   - Gear: Wand/Staff (`CERBERUS_DESIGN_PLAN.md:812`).
10. **Now vs later.** Now: on-hit cast XP under the class-XP rules; settle the `END_ROD` mismatch; make it a Mage entry route (ranked loop 1). Later: casting speed, combinations, gear.

### Mana Regeneration (shown as "Essence Regeneration")

**Status: disconnected.** Nothing awards it XP, so its live attribute weight is frozen at level 1. Its own effect code is unreachable.

1. **XP source.** None. No `addXp*` call uses the name. It is not in the kill routing; it appears only in the `magic-skills` toggle list (`skills.yml:94`). The page says nothing trains it (`SkillDefinitions.java:256-257`), which is honest.
2. **Balance and protection.** Not applicable. No XP for idle regeneration exists, so there is no idle exploit.
3. **Reachable effects** (only at an admin-set level).
   - Essence +1.0 per level (`attributes.yml:43`), identical to Intelligence (`Attributes/AttributeRules.java:119-120`). Mana, regeneration, spell damage and cooldown all rise together; nothing is regeneration-specific.
   - The description "Recovering Essence after spending it" overstates its identity. The numbers on the page are correct.
   - Played with admin-set levels (`docs/PLAYTEST_CHECKLIST.md:33`).
4. **Disconnected.**
   - `ManaRegenerationSkill.applyEffect:17-28` sets regeneration to 50 × (5 + 0.5·L), already ×5.5 at level 1. Its only path, `EffectManager.applyManaRegenerationEffect` (`:99-100,181-185`), is never triggered.
   - The Mage passive Essence Flow (+15% regeneration, `abilities.yml:145-153`) is more regeneration-specific than the skill named for it.
5. **Attributes.**
   - Essence 1.0 per level.
   - It can't feed the `MANA_REGEN` stat by configuration: `attributes.skills` accepts only attributes (`Attributes/AttributesConfig.java:104`), and `secondary.skills` only speed stats (`:195`).
   - The calculator already sums per-skill stat contributions (`Attributes/SkillAttributes.java:28-37`, `AttributeCalculator.java:78`), so this is a small change.
6. **Progression rewards.** None reachable.
7. **Dependencies.** Built: the regeneration task (`PlayerManaManager.startManaRegenTask`) and AttributeEffects. Missing: routing a skill to a stat (small) and an XP rule.
8. **Missing gameplay.** An XP source and a regeneration identity.
9. **Future interactions.**
   - Gear: the Focus/Orb (`CERBERUS_DESIGN_PLAN.md:813`).
   - Talismans: the Sustain archetype (`TALISMANS.md:46`).
   - Reservoir Inversion: the mana-regeneration cap during backlash (`OPEN_DECISIONS.md:37`).
10. **Now vs later.** Now: XP from Essence spent on casts that reached an enemy (bounded by the ledger), and weight it to `mana_regen` instead of generic Essence. Later: gear, talismans, Inversion.

### Defensive Magic

**Status: disconnected**, plus a placeholder input stub. It earns no XP, its Fortitude weight is live but frozen at level 1, and the skill class is an intentionally disabled shell.

1. **XP source.** None (grep). The page says nothing trains it.
2. **Balance and protection.** Not applicable.
3. **Reachable effects** (only at an admin-set level).
   - Fortitude +0.5 per level (`attributes.yml:41`). At level 100 with the others at 1: armor +5.2%, defense bar ×1.52, resistances +10.3%.
   - PvE only by default (`attributes.yml:92`, `pvp-defense: false`).
   - Played with admin-set levels in the defense checks (`tools/playtest/progression.js:567,647`).
4. **Disconnected or placeholder.**
   - `DefensiveMagicSkill` is marked "INTENTIONALLY DISABLED" (`:17-26`). `initializeKeyTracking:57-63` only logs. Every activation method is private and never invoked (`:65-157`). There is no `applyEffect` override, so it runs the no-op in `MagicSkill.java:11`.
   - EffectManager has no "Defensive Magic" case (`:82-104`).
   - `DefensiveMagic/{Shield,Ward,Barrier,ManaDrain}` (596 lines, marked RESERVED, `DefensiveMagic/Barrier.java:8-23`) are built only by those dead methods and by `PlayerManaManager.applyShield:143-148`, whose only caller has no caller of its own.
   - `applyCrossSkillBenefits` (the Heavy Armor → Defensive Magic discount, `:382-391`) has no caller.
   - The page says "Shields, wards and barriers — Not active yet", which is honest.
5. **Attributes.** Fortitude 0.5 per level (PvE).
6. **Progression rewards.** None reachable.
7. **Dependencies.**
   - Input: the class ability controls already solve the ProtocolLib blocker (quick keys and combos, `docs/design/ABILITY_CONTROLS.md`).
   - A `shield` or `barrier` verb, designed but not built (`CLASSES.md:184`).
   - An absorber ahead of the defense layers, from `CERBERUS_DESIGN_PLAN.md:1588-1599`.
   - PvP rules.
8. **Missing gameplay.** The magic itself: shields, wards, barriers, and XP (for example, from damage absorbed).
9. **Future interactions.**
   - Classes: Paladin (Heavy Armor 15 + Defensive Magic 10), Warden (Defensive Magic 15) (`CLASSES.md:107-108`).
   - Reservoir Inversion: −0.25% backlash per level (`CERBERUS_DESIGN_PLAN.md:1529`).
   - Talismans: the Ward archetype.
   - Owner decision P2 (`OPEN_DECISIONS.md:39`) still proposes "Catalyst + Sneak Cycle" input (`CERBERUS_DESIGN_PLAN.md:1580`), now superseded by the ability engine.
10. **Now vs later.** Now: nothing quick and honest beyond keeping the page as it is; restate decision P2 in terms of the ability engine. Later: a shield verb, the absorber and XP from absorbed damage, through a Knight, Paladin or Warden class step.

---

## Group table

| Skill | Status | XP source today | What its level does (reachable) |
|---|---|---|---|
| Intelligence | partial | 5 per Fireball cast (op wand or Mage Fireball); none for a normal player (circular Mage gate) | +1 Essence/level (mana, regen, spell damage vs mobs, spell cooldown); opens Mage at 5 |
| Arcane Knowledge | partial | Kill XP v2 holding an enchanted book (any player) | +0.5 Essence/level only |
| Elemental Mastery | partial (elemental side disconnected) | 10 per Fireball cast (op or Mage only) | +0.5 Essence/level; nothing elemental |
| Summoning | placeholder (disconnected) | none | nothing |
| Spell Weaving | partial (closest to functional) | 15 per cast; kill XP v2 holding a focus (any player) | +0.5 Essence/level; spell cooldown −0.3%/level (50% cap, paused in PvP) |
| Essence Regeneration (Mana Regeneration) | disconnected | none | frozen at 1; would be +1 Essence/level, a copy of Intelligence |
| Defensive Magic | disconnected (+ placeholder stub) | none | frozen at 1; would be +0.5 Fortitude/level (PvE) |

None of the seven is functional for a normal player. Every Essence output needs a spell, and no normal player can cast one.

## Highest-value missing loops, ranked by value to players vs effort

1. **Open magic to normal players.** Value: very high. Effort: very small.
   - Why: today zero of the seven magic skills is usable without an admin, and one of the three prototype classes is unreachable.
   - Quickest step, config only: `classes.yml` mage `entry-any: { Intelligence: 5, Spell Weaving: 5 }`. Spell Weaving already trains from focus kills, and the design table names Spell Weaving 5 for the Mage (`CLASSES.md:101`).
   - Then give Intelligence real XP. Either a small configurable splash of focus-kill XP (the routing that `OPEN_DECISIONS.md:31` recommends: "only to the skill governing the kill … with a small configurable splash set"), or Intelligence XP on spell hits (loop 2).
   - This must ship together with loop 2. Otherwise every new Mage gets the sky farm from G2.
2. **Every Mage spell trains its magic skills on hit, under the class-XP rules.** Value: high. Effort: small.
   - Move skill cast XP from launch to the moment the spell reaches a hostile mob. Fireball already reports its burst through `spellHit` (`AbilityCaster.java:315-318`); `tickFireball` takes an `onHit` callback the wand path can use too.
   - Reuse the grey-mob ×0.25 rule and the per-mob `CastXpLedger`.
   - Award by element:
     - Intelligence on any spell hit;
     - Elemental Mastery on fire and ice hits;
     - Arcane Knowledge on arcane hits;
     - Spell Weaving on any cast.
   - This closes the air-cast farm, lets Intelligence, Elemental Mastery and Arcane Knowledge grow from normal Mage play, and keeps the promise in `CLASSES.md:29`: "a Mage spell trains Spell Weaving".
3. **Give Essence Regeneration a job and a source.** Value: medium. Effort: small.
   - Why: it is a menu skill with no training that duplicates Intelligence.
   - Allow `stat:` entries in `attributes.skills` (pure, unit-testable).
   - Weight it to `mana_regen` instead of Essence.
   - Pay XP from Essence spent on casts that reached an enemy, bounded by the ledger.
4. **Elemental Mastery as an elemental modifier.** Value: medium. Effort: small to medium.
   - Scale burn ticks (`FireballSpell.java:174`) and the Frost Nova slow with its level, using the element `SpellDamageContext` already carries.
   - Drop the no-op `applyMagicSkillEffect` calls.
   - Reactions and infusions wait for the ICE collision decision and for gear.
5. **Arcane Knowledge from enchanting.** Value: low to medium. Effort: small. Use `EnchantItemEvent`, by levels spent. Best bundled with the gear and enchanting loop.
6. **Defensive Magic on the ability engine** (shield verb, absorber, XP from absorbed damage). Value: high in the long run. Effort: medium to large. Needs decision P2 restated now that input is solved.
7. **Summoning** (summon verb, ally AI, TTL and leash, offense cap). Value: high in the long run. Effort: large. Waits for the Summoner class step.

## Side findings (outside this group's scope; cheap to correct)

- `docs/STATUS.md:59` lists `/cerb mana` among the stubs. It is implemented (`cerberus/world/cerb/CerberusPlugin.java:713-735`).
- `docs/STATUS.md:27` says "magic skills triple mana and scale spells (Played)". That is true only at admin-set levels (G1).
- `Skills/SkillManager.java:414` comments "cumulative to 100 ≈ 19M". The formula gives ≈13.2M (computed: the sum of `xpForLevel(1..99)`).
- `Skills/Skills.java:14-20` describes effects nobody has built: cost reduction, summons, shields. Nothing reads that enum (grep), so it is dead metadata.

## Unconfirmed, and how I checked

- **Air-cast skill XP** (G2), **kill routing by the item held at death**, and **fire-tick kills crediting the caster**: Read only. The first two come from the code paths cited. The third relies on Paper's `getKiller()` semantics. None was exercised in game.
- **XP-per-hour figures**: computed from the code's constants, cooldowns and the Played wand interval (`docs/STATUS.md:110`). They are not measured.
- **"No normal player can cast"**: confirmed by grep. `cerb_spell` is written only by `GiveTestItemCommand.java:82`. Mage is opened only by skills or the admin `setRank`. No Warrior or Duelist ability has `essence:` (`abilities.yml`). I did not check a live server for third-party plugins or hand-made items carrying the tag.
- **Attribute magnitudes**: computed from `attributes.yml` and `AttributeCalculator`. Only the Intelligence + Essence Regeneration figures and the cooldown figures are Played.
