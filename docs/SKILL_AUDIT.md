# Skill audit: all 35 skills, from the code (2026-10-25)

*The first task of the Gameplay Depth & Systems Completion milestone (`docs/ROADMAP.md`). Each skill was read from the source, not from its menu page: "Do not treat a skill as complete simply because its icon, description, or level exists."*

> **Update, later on 2026-10-25:** the first loop is built (`docs/design/GATHERING.md`, `docs/STATUS.md`). **Mining** and **Smithing** are now **functional** (their rows and blocks below are updated), and **Fortune** does something: a chance of one extra ore, which also gives Fishing, Scavenging, Trading and Lockpicking their first effect. The counts, therefore: 6 functional, 10 partial, 9 disconnected, 10 placeholder. The rest of this audit is as written.

## Method

- **Reachable only.** A behaviour counts when a runtime path reaches it: a registered listener, a command or a scheduled task actually calls it. Code that nothing calls is reported as disconnected, however complete it looks.
- **Every skill answers ten questions:**
  1. how it earns XP;
  2. how balanced and exploit-proof that is;
  3. its reachable effects;
  4. its incomplete, disconnected or placeholder effects;
  5. how it feeds the attribute engine;
  6. whether its levels change gameplay;
  7. what it depends on;
  8. what is missing;
  9. how it will meet future systems;
  10. what can be done now versus later.
- **Evidence.** Four independent readers covered the four groups. Their reports, with file and line references for every claim, are in `audit/skills-2026-10-25/` (combat, magic, gathering, utility). I spot-checked the key claims against the code before writing this summary:
  - no gathering, crafting, furnace, brewing or enchanting handler is registered;
  - magic skill XP is paid when a spell is fired;
  - the Mage class needs Intelligence 5, which only spells train;
  - vanilla healing is cancelled for players;
  - new players start every skill at level 0.

  Where two readers disagreed (the starting level), the code decided. Nothing in this audit was run in game except where a line says **Played** (taken from earlier in-game checks in `docs/STATUS.md`).

**Status, one per skill:**

| Status | Meaning |
|---|---|
| **Functional** | Earns XP from real play through a reachable path, and its level changes play in a reachable way, with sane exploit protection. |
| **Partial** | Some of that works. |
| **Disconnected** | Real code exists, but nothing reachable calls it. |
| **Placeholder** | Stub or example code. |
| **Design-only** | A name, a description or a menu page, perhaps a design document, but no meaningful code. |

## The result

| Status | Count | Skills |
|---|---|---|
| Functional | 6 (was 4) | Blade Mastery, Weapon Mastery, Ranged Precision; Pathfinding (thin); **Mining, Smithing** (built after the audit) |
| Partial | 10 | Martial Expertise, Heavy Armor Mastery, Critical Strike, Survival; Intelligence, Arcane Knowledge, Elemental Mastery, Spell Weaving; Field Medicine, Stealth |
| Disconnected | 9 (was 11) | Essence Regeneration, Defensive Magic; Crafting, Woodcutting, Farming, Fishing, Enchanting, Herbalism; Trap Mastery |
| Placeholder | 10 | Dual Wielding; Summoning; Alchemy, Cooking; Scavenging, Repairing, Trading, Animal Taming, Riding, Lockpicking |
| Design-only | 0 | Every skill has at least placeholder code behind its page. |

**23 of the 35 skills have no XP source at all.** Only fighting, spells, damage taken and new chunks pay XP; no block break, craft, furnace, brew, enchant, catch, tame or trade does.

### What holds across all 35

1. **Only two parts of the skill system are live:**
   - the XP sources for kills, hits, damage taken, spell casts and new chunks;
   - the attribute weights in `attributes.yml`, which turn levels into Power, Agility, Vitality, Fortitude and Essence, and into speed stats.

   Every skill's own effect code (`applyEffect` and the `EffectManager` dispatch) is unreachable.
2. **That old effect code must never be wired as it is.** There is one shared skill object per skill for the whole server, and its level reads 1 for every player. The bonuses add up again on every call and change saved vanilla attributes. It sends chat lines on every call. Several bodies duplicate items: the gathering skills drop the loot of the block you look at without breaking it. Retire it, and build each skill's gameplay on per-player levels and the attribute engine.
3. **Magic is closed to normal players.**
   - The only spell item is the op-only Fireball Wand.
   - The Mage class needs Intelligence 5, and only casting spells trains Intelligence. Without an admin the class never opens.
   - Every Essence bonus (mana, regeneration, spell damage and cooldown) is a number with nothing to spend it on.
4. **Live XP sources with exploits:**
   - **Magic skill XP is paid when a spell is fired, not when it hits.** The Mage's Fireball, aimed at the sky, pays Spell Weaving 15, Elemental Mastery 10 and Intelligence 5 per cast, and regeneration outpaces the cost. Class XP got this fix in Phase 3; skill XP did not.
   - **Kill XP has only the weak-mob rule.**
     - Spawner mobs, bred animals and creative-mode kills pay in full.
     - Mobs renamed with a name tag can pay ×4 (`mobs.yml` matches by name).
     - Player kills pay weapon-skill XP.
     - XP goes to whatever is in the hand at the moment of death, so a thrown-trident kill trains Martial Expertise.
   - **Critical Strike pays per hit with no limits**, so likely even on an armor stand (not checked in game).
   - **Defensive XP has no idle check** (your open decision 8): a player in a mob trap trains Heavy Armor and Survival.
5. **Vanilla healing is off for players.** Food, potions and golden apples restore no Cerberus health. Field Medicine, Cooking and Alchemy have no payoff until consumables act on the health pool or through timed buffs.
6. **Fortune does nothing yet.** It is display-only, and four skills feed nothing else: Fishing, Scavenging, Trading and Lockpicking.
7. **About 2,860 lines are unreachable:** quality crafting (`CraftingManager`), `QualityBuffs`, `PlayerItemListener` and the trap system. STATUS said 600. Wired as written, this code would:
   - duplicate items;
   - let yield multipliers grow without limit;
   - name every crafted tool "[Broken]";
   - push enchantments to level 255;
   - give a free diamond every 10 repairs.
8. **The skill pages are honest:** "Not active yet" and "No way to train it yet" match the code. Four claims overstate:
   - Heavy Armor's "half the damage taken": after armor and the bar, most hits pay 0.
   - Critical Strike's "8 for a critical hit" includes spell rolls that crit nothing.
   - Survival's "frost spells": nothing frost hits players.
   - A comment in `skills.yml` says gathering skills earn XP from block breaks; they don't.

### Highest-value missing loops

Ranked by value to players against effort.

1. **Mining → Smithing → Equipment** (the next build). It is the only loop that turns non-combat play into combat power, and its parts unlock the rest:
   - a resource registry;
   - placed-block tracking;
   - gathering-XP rules;
   - the real-time XP display;
   - an equipment source for the attribute engine.

   Its plan is in `docs/design/GATHERING.md`.
2. **Close the live XP exploits** (small):
   - pay magic skill XP when a spell hits an enemy, under the class-XP rules (a quarter from weak mobs, each mob only up to its kill XP);
   - credit kill XP to the weapon that landed the last hit;
   - no full kill XP from spawners, bred animals, creative mode, renamed mobs or player kills;
   - Critical Strike XP only for hits on real mobs, capped per mob.
3. **The defensive loop: Heavy Armor + Survival** (small).
   - **What's wrong today:** Heavy Armor pays 0 for most real hits, and Survival needs 17,857 hits for level 10.
   - **The fix:**
     - pay XP by the share of health a hit takes, keeping partial points;
     - Heavy Armor's bonuses only while heavy armor is worn;
     - one idle rule (your decision 8).
   - **What it opens:** the Knight, Paladin, Ranger, Berserker and Druid entries.
4. **Open magic to normal players** (tiny, but a design change, so yours to decide): let Spell Weaving, which kills with a focus already train, open the Mage class (or Intelligence via it), and ship it together with item 2's on-hit rule.
5. **Fishing XP** (low effort): the catch handler already runs. It needs:
   - XP per catch;
   - a Sea Monster chance that scales with level and has a cap;
   - an idle check.
6. **Woodcutting and Farming** on the gathering infrastructure: mostly configuration once item 1 exists.
7. **Healing with consumables** (Field Medicine, then Cooking and Alchemy): consumables restore the health pool, with XP from health actually restored.
8. **Scavenging and Fortune:** XP from loot chests (`LootGenerateEvent` fires once per container), and one Fortune rule shared with Mining.
9. **Starter mounts:** Riding XP from new chunks reached while riding, and Animal Taming XP from taming.

**Later**, each waiting on a system it needs:
- Stealth (an aggro design that avoids the ability keys);
- Repairing (entropy durability);
- Trading (an economy);
- Trap Mastery (a rebuild with Ranger and Artificer);
- Lockpicking (locked content);
- Summoning, Dual Wielding and Defensive Magic (their class steps and ability verbs).

### Smaller things found

- **New players start every skill at level 0.** The attribute code's comment says unlearned skills count as 1; that comment is stale.
- **Doc drift:**
  - `docs/design/CLASSES.md` says the Warrior needs Blade or Weapon Mastery 10; `classes.yml` says 5.
  - `SkillManager`'s comment says 19M XP to level 100; the formula gives 13.2M.
- **Weapon Mastery gives every player 2.7% lifesteal from level 1.** It is the only in-combat healing. Intended?
- **Pathfinding:**
  - each new chunk opens its own database connection (no batching);
  - creative and spectator flight earn XP;
  - a failed load of the visited set lets old chunks pay again for that session.

## Status matrix

| Skill (shown as) | Group | Status | XP today | What its level does today |
|---|---|---|---|---|
| Blade Mastery | Combat | Functional | Sword kills | Sword Power and attack speed (PvE); PvP sword damage; Warrior and Duelist entry |
| Weapon Mastery | Combat | Functional | Axe, mace, trident, spear kills | Power on those (+ a little on all), their attack speed, lifesteal on mob hits; PvP damage; Warrior entry |
| Ranged Precision | Combat | Functional | Bow and crossbow kills | Bow Power, bow draw speed (not crossbows) |
| Martial Expertise | Combat | Partial | Empty-hand kills (plus misrouted thrown-weapon kills) | Unarmed Power on a 1-damage fist; knockback resistance and disarm do nothing |
| Heavy Armor Training (Heavy Armor Mastery) | Combat | Partial | Hits taken in heavy armor (usually 0), kills holding a tool | Armor, defense-bar points, Fortitude, Vitality, with or without heavy armor |
| Dual Wielding | Combat | Placeholder | None | Nothing earnable |
| Critical Strike | Combat | Partial | 2 per hit, 8 per crit, no limits | Agility: crit chance and damage against mobs; Duelist entry |
| Survival | Combat | Partial | 1 per hit taken | Fire, cold and drowning resistance; Vitality; Fortitude (the level barely moves) |
| Intelligence | Magic | Partial | 5 per spell cast (op wand or Mage only) | Essence: mana, regeneration, spell damage, spell cooldown; opens Mage at 5 |
| Arcane Knowledge | Magic | Partial | Kills holding an enchanted book | Essence (half rate) |
| Elemental Mastery | Magic | Partial | 10 per Fireball cast (op wand or Mage only) | Essence (half rate); nothing elemental |
| Summoning | Magic | Placeholder | None | Nothing |
| Spell Weaving | Magic | Partial | 15 per cast; kills holding a focus | Essence (half rate), spell cooldown recovery |
| Mana Regeneration (Essence Regeneration) | Magic | Disconnected | None | Nothing earnable (would copy Intelligence) |
| Defensive Magic | Magic | Disconnected | None | Nothing earnable (Fortitude at admin levels) |
| Mining | Gathering | **Functional** (since the loop) | Natural stone and ore broken by hand (`resources.yml`) | Gathering speed, Fortune (extra ore) |
| Smithing | Production | **Functional** (since the loop) | Smelting metal, forging gear, the netherite upgrade | Forged gear quality odds |
| Crafting | Production | Disconnected | None | Nothing |
| Woodcutting | Gathering | Disconnected | None | Nothing |
| Farming | Gathering | Disconnected | None | Nothing |
| Fishing | Gathering | Disconnected | None (catches only roll a Sea Monster) | Fortune, which does nothing |
| Alchemy | Production | Placeholder | None | Nothing |
| Enchanting | Production | Disconnected | None | Nothing |
| Herbalism | Gathering | Disconnected | None | Nothing |
| Cooking | Production | Placeholder | None | Nothing |
| First Aid (Field Medicine) | Utility | Partial | None | Vitality (max health), only at admin-set levels |
| Stealth | Utility | Partial | None | Agility, only at admin-set levels; no stealth exists |
| Trap Mastery | Utility | Disconnected | None | Nothing (traps can't be crafted or armed) |
| Scavenging | Utility | Placeholder | None | Fortune (does nothing) |
| Repairing | Utility | Placeholder | None | Nothing |
| Trading | Utility | Placeholder | None | Fortune (does nothing) |
| Navigation (Pathfinding) | Utility | Functional (thin) | 5 per chunk entered for the first time | Agility and movement speed (+2.6% at 25, +8.6% at 100) |
| Animal Taming | Utility | Placeholder | None | Nothing |
| Riding | Utility | Placeholder | None | Nothing |
| Lockpicking | Utility | Placeholder | None | Fortune (does nothing) |

## Per skill

The ten questions, short. **XP** = 1–2, **Effects** = 3, **Not working** = 4, **Attributes** = 5, **Rewards** = 6, **Needs** = 7, **Missing** = 8, **Future** = 9, **Now / later** = 10. The evidence for each line is in the group reports.

### Combat

**Blade Mastery: functional (PvE).**
- **XP:** sword kills, kill XP by mob level and tier (40 for a level-1 Common mob, 334 at level 50); a quarter from mobs 20 levels below. About 7,800 equal-level kills to level 50. Open gaps: the kill-XP exploits above.
- **Effects:**
  - Sword Power +1 per level: ×3.5 at 50, ×6 at 100 (Played: a level-50 zombie from 19.5 to 5 swings).
  - Sword attack speed, up to +17.5% at 100 (Played).
  - A PvP damage bonus on the old path.
  - Warrior and Duelist entry.
- **Not working:** its own effect code (unreachable); a `combat.yml` speed curve that nothing reads.
- **Attributes:** Power (sword) and attack speed (sword), PvE.
- **Rewards:** numbers and two class gates; no technique or milestone.
- **Needs:** sword archetypes, realms, the PvP pass.
- **Missing:** sword identity beyond the multiplier.
- **Future:** Spellblade and Spiritbinder entries; crafted swords feed the same Power.
- **Now / later:** kill-XP integrity now; archetypes and mastery techniques later.

**Weapon Mastery: functional (PvE).**
- **XP:** axe, mace, trident and spear kills (a thrown-trident kill trains the empty hand instead).
- **Effects:**
  - Power on its four weapons (×7.25 at 100), +0.25 per level on every weapon;
  - attack speed;
  - lifesteal on every mob hit, 2.7% at level 1 rising to the 40% cap;
  - a PvP bonus;
  - Warrior entry.
- **Not working:** its own effect code.
- **Attributes:** Power, attack speed, lifesteal (the engine's only lifesteal), PvE.
- **Rewards:** numbers; lifesteal is the most felt.
- **Needs:** the open Weapon Mastery split (your decision 2).
- **Missing:** per-weapon identity; correct thrown-weapon routing.
- **Future:** Berserker; greatsword, spear and battleaxe archetypes.
- **Now / later:** route kills by the weapon that hit, and decide whether lifesteal starts at level 1, now; the split with the weapon archetypes later.

**Ranged Precision: functional (PvE).**
- **XP:** bow and crossbow kills, routed by the item held at death.
- **Effects:**
  - Bow Power on bow and crossbow arrows;
  - bow draw speed, +27.6% at 100 (Played: an early release reached full power). Crossbows get no draw speed.
- **Not working:** its own effect code (inert even if called); accuracy is a placeholder.
- **Attributes:** Power (bow) and draw speed, PvE.
- **Rewards:** draw speed is felt; no class gate yet.
- **Needs:** crossbow reload (no clean hook), bow archetypes.
- **Missing:** crossbow parity; accuracy or falloff; crit training for archers.
- **Future:** Ranger and Marksman entries.
- **Now / later:** projectile-aware routing and arrow crit XP now; archetypes later.

**Martial Expertise: partial.**
- **XP:** empty-hand kills, plus kills whose weapon left the hand (misrouted).
- **Effects:** unarmed Power on a 1-damage fist. At level 100 a fist roughly equals an iron sword at Blade Mastery 1.
- **Not working:** knockback resistance and disarm are computed and never applied.
- **Attributes:** Power (unarmed).
- **Rewards:** one weak multiplier.
- **Needs:** martial weapons, the Brawler class.
- **Missing:** a viable unarmed build.
- **Future:** Brawler; disarm with spears.
- **Now / later:** knockback resistance as a vanilla attribute modifier, an unarmed damage term, correct routing now; gauntlets and Brawler later.

**Heavy Armor Training, shown as Heavy Armor Mastery: partial.**
- **XP:** half the damage taken while wearing a heavy piece, in whole points. After armor and the bar most hits pay 0. Its practical source is killing mobs while holding a tool (a fallback rule). No idle check.
- **Effects:** armor, defense-bar points (near 0 below level 40), Fortitude, Vitality, with or without heavy armor worn.
- **Not working:** its own effect code.
- **Attributes:** Fortitude, Vitality.
- **Rewards:** weak (your playtest said so too).
- **Needs:** the idle rule.
- **Missing:** XP that matches play; bonuses tied to heavy armor.
- **Future:** Knight, Paladin; crafted armor quality.
- **Now / later:** the defensive loop (item 3 above) now.

**Dual Wielding: placeholder.**
- **XP:** none.
- **Effects:** nothing earnable; a small fixed bonus at level 1.
- **Not working:** example code; no off-hand weapon pipeline.
- **Attributes:** Agility.
- **Rewards:** none.
- **Needs:** off-hand weapons (Twin Blades).
- **Missing:** everything.
- **Future:** the weapons slice.
- **Now / later:** later.

**Critical Strike: partial.**
- **XP:** 2 per melee or spell hit, 8 per crit, with no per-mob limit and no check that the target is a real mob. Arrows don't count. It levels about 8× slower than weapon skills.
- **Effects:** Agility: crit chance and damage against mobs; Duelist entry.
- **Not working:** its own effect code.
- **Attributes:** Agility.
- **Rewards:** numbers.
- **Needs:** nothing new.
- **Missing:** protected, level-scaled hit XP.
- **Future:** Marksman, Assassin.
- **Now / later:** hit XP only for hits on real mobs, scaled by mob level, capped per mob, arrows included, now.

**Survival: partial.**
- **XP:** 1 per hit taken. Level 10 needs 17,857 hits. No idle or self check.
- **Effects:** fire, cold and drowning resistance; Vitality; Fortitude.
- **Not working:** its own effect code.
- **Attributes:** Vitality, Fortitude, resistances.
- **Rewards:** numbers.
- **Needs:** the idle rule.
- **Missing:** XP that matches play.
- **Future:** Ranger, Berserker, Druid.
- **Now / later:** the defensive loop now.

### Magic

**Intelligence: partial.**
- **XP:** 5 per spell cast, paid at launch (farmable into the sky); only the op wand or the Mage casts spells, and the Mage needs Intelligence 5.
- **Effects:** Essence +1 per level (mana, regeneration, spell damage against mobs, spell cooldown); opens the Mage at 5.
- **Not working:** its own effect code (it would add 500 mana per call).
- **Attributes:** Essence.
- **Rewards:** the Mage gate; otherwise numbers.
- **Needs:** a way in for normal players.
- **Missing:** a training route; XP from Arcane Bolt and Frost Nova; XP that scales with the foe.
- **Future:** Arcanist, Cleric; focus gear; realms.
- **Now / later:** on-hit XP and a way in now; Arcanist and gear later.

**Arcane Knowledge: partial.**
- **XP:** kills while holding an enchanted book (an odd route); the arcane-element route is dead.
- **Effects:** Essence at half rate.
- **Not working:** its own effect code and a database write it would make on the tick.
- **Attributes:** Essence.
- **Rewards:** numbers.
- **Needs:** the enchanting system.
- **Missing:** a thematic XP source (enchanting, arcane spell hits); an effect of its own (for example, cheaper spells).
- **Future:** Arcanist, Warlock.
- **Now / later:** XP from vanilla enchanting and arcane hits now; its own effect with enchanting later.

**Elemental Mastery: partial.**
- **XP:** 10 per Fireball cast, at launch. Frost Nova and Arcane Bolt pay none.
- **Effects:** Essence at half rate; nothing elemental.
- **Not working:** every elemental hook (infusions, synergies, multipliers) is disconnected or only sends chat.
- **Attributes:** Essence.
- **Rewards:** numbers.
- **Needs:** element states and reactions (design only).
- **Missing:** element-specific effects (burn or chill strength by level).
- **Future:** Elementalist.
- **Now / later:** on-hit XP from fire and ice spells, and burn and slow that scale with level, now; reactions later.

**Summoning: placeholder.**
- **XP:** none.
- **Effects:** none.
- **Not working:** a stub that would spawn a plain hostile zombie, never called.
- **Attributes:** none.
- **Rewards:** none.
- **Needs:** a summon ability verb, ally AI.
- **Missing:** everything.
- **Future:** Summoner, Necromancer.
- **Now / later:** later (the Summoner step); keep the stub unwired.

**Spell Weaving: partial (the closest to functional).**
- **XP:** 15 per cast (farmable at launch), and kills holding a focus: the only magic XP a normal player can earn.
- **Effects:** Essence at half rate; spell cooldown recovery (Played: Fireball every 1.04 s at 1, 0.81 s at 100).
- **Not working:** its own effect code.
- **Attributes:** Essence, cooldown recovery.
- **Rewards:** faster casting.
- **Needs:** nothing new.
- **Missing:** on-hit XP; spell combinations (its name).
- **Future:** Mage, Spellblade and Shadowblade entries; casting speed.
- **Now / later:** on-hit XP, and a Mage way in, now.

**Mana Regeneration, shown as Essence Regeneration: disconnected.**
- **XP:** none.
- **Effects:** none earnable; at admin levels it copies Intelligence exactly.
- **Not working:** its own effect code.
- **Attributes:** Essence (should be regeneration).
- **Rewards:** none.
- **Needs:** a skill-to-stat weight (small).
- **Missing:** an XP source and a regeneration identity.
- **Future:** focus gear, Sustain talismans.
- **Now / later:** XP from Essence spent on spells that hit, and a regeneration weight, now.

**Defensive Magic: disconnected.**
- **XP:** none.
- **Effects:** none earnable (Fortitude at admin levels).
- **Not working:** the disabled shield, ward and barrier code (about 600 lines).
- **Attributes:** Fortitude.
- **Rewards:** none.
- **Needs:** a shield verb and an absorber in the defense pipeline. The ProtocolLib blocker is gone: the ability keys replace it.
- **Missing:** the magic itself.
- **Future:** Paladin, Warden.
- **Now / later:** later, with a defensive class step.

### Gathering and production

**Mining: disconnected** (as audited; **functional since the Mining → Smithing → Equipment loop**: XP per natural block from `resources.yml`, gathering speed, Fortune, placed-block tracking, custom resources and tools; `docs/design/GATHERING.md`).
- **XP:** none. No block-break handler exists. A pickaxe kill trains nothing, or Heavy Armor through the fallback.
- **Effects:** none.
- **Not working:** `MiningSkill` and `ResourceYieldManager` are unreachable and unsafe:
  - they drop the looked-at block's loot without breaking it;
  - yield ×11 at level 100, on top of vanilla's;
  - a pre-1.17 ore list.
- **Attributes:** none.
- **Rewards:** none.
- **Needs:** a resource registry, placed-block tracking, tool data, a Fortune rule, the XP display.
- **Missing:** everything.
- **Future:** drills, custom ores, distance-tiered nodes.
- **Now / later:** the Mining → Smithing → Equipment loop now (`docs/design/GATHERING.md`).

**Smithing: disconnected** (as audited; **functional since the loop**: smelting, forging and netherite-upgrade XP, counted per item made; forged quality through the attribute engine).
- **XP:** none: no furnace, smithing-table or anvil handler.
- **Effects:** none.
- **Not working:** the smithing code in `CraftingManager` can never receive an item. If it could, it would apply unsafe Sharpness and a name-only "infusion".
- **Attributes:** none.
- **Rewards:** none.
- **Needs:** Mining inputs, recipes, an equipment attribute source.
- **Missing:** XP for smelting and forging; gear quality.
- **Future:** reforging, tempering, realm materials.
- **Now / later:** the loop now.

**Crafting: disconnected.**
- **XP:** none. One registered listener records crafts, and nothing reads the record.
- **Effects:** none.
- **Not working:** `CraftingManager`'s quality system: lost bonuses, "[Broken]" items, a level-160 ladder, a free diamond every 10 repairs.
- **Attributes:** none.
- **Rewards:** none.
- **Needs:** recipe rules (no XP for reversible recipes), gear grades.
- **Missing:** everything.
- **Future:** recipe unlocks, the Crafter block.
- **Now / later:** gear-recipe XP in the loop now.

**Woodcutting: disconnected.**
- **XP:** none (an axe kill trains Weapon Mastery).
- **Effects:** none.
- **Not working:** the same look-at duplication; six overworld logs only.
- **Attributes:** none.
- **Rewards:** none.
- **Needs:** the gathering infrastructure.
- **Missing:** XP per log.
- **Future:** chainsaw tools.
- **Now / later:** configuration on the Mining infrastructure next.

**Farming: disconnected.**
- **XP:** none.
- **Effects:** none.
- **Not working:** up to three look-at duplications per call; crop names passed as skill names.
- **Attributes:** none.
- **Rewards:** none.
- **Needs:** ripe-crop rules (every crop is player-planted).
- **Missing:** XP per ripe harvest.
- **Future:** scythes, food for Cooking.
- **Now / later:** ripe-crop XP on the gathering infrastructure next.

**Fishing: disconnected.**
- **XP:** none. Catches only roll a Sea Monster (5%, no cap, ignores the level), so an AFK fish farm keeps spawning Rare mobs.
- **Effects:** Fortune, which does nothing.
- **Not working:** its own effect code.
- **Attributes:** Fortune.
- **Rewards:** none.
- **Needs:** a Fortune rule, an idle check.
- **Missing:** XP per catch; level-scaled rare catches.
- **Future:** harpoons, Sea Monster apexes.
- **Now / later:** XP in the existing handler, a capped monster chance and an idle check, soon.

**Alchemy: placeholder.**
- **XP:** none.
- **Effects:** none.
- **Not working:** potion bonuses that only send chat.
- **Attributes:** none.
- **Rewards:** none.
- **Needs:** potions that act on the health pool or attributes (vanilla healing is off).
- **Missing:** everything.
- **Future:** pills for breakthroughs, the Alchemist.
- **Now / later:** after consumables work.

**Enchanting: disconnected.**
- **XP:** none.
- **Effects:** none; vanilla enchanting is unchanged.
- **Not working:** code that would raise enchantments to level 255.
- **Attributes:** none.
- **Rewards:** none.
- **Needs:** the enchant budget design. Vanilla enchantments already enter the hit before Power, outside the offense cap.
- **Missing:** everything.
- **Future:** enchant slots by realm.
- **Now / later:** XP per vanilla enchant (cheap) any time; Cerberus enchantments with gear.

**Herbalism: disconnected.**
- **XP:** none.
- **Effects:** none.
- **Not working:** a block handler, never called, that would multiply drops ×11 and hand out Strength III and golden apples.
- **Attributes:** none.
- **Rewards:** none.
- **Needs:** a scope decision against Farming (same crops).
- **Missing:** XP for wild plants.
- **Future:** pill reagents, Druid.
- **Now / later:** after the scope decision.

**Cooking: placeholder.**
- **XP:** none.
- **Effects:** none.
- **Not working:** an empty food-quality hook.
- **Attributes:** none.
- **Rewards:** none.
- **Needs:** food that restores health or gives timed buffs.
- **Missing:** everything.
- **Future:** meals as buffs, feasts.
- **Now / later:** after consumables work.

### Utility

**First Aid, shown as Field Medicine: partial.**
- **XP:** none.
- **Effects:** Vitality (max health), only at admin-set levels (Played). Recovery is a flat 2 HP/s out of combat for everyone.
- **Not working:** its own effect code; healing items (example code).
- **Attributes:** Vitality.
- **Rewards:** none in play.
- **Needs:** healing that reaches the health pool.
- **Missing:** an active heal and an XP source.
- **Future:** Cleric, heal verbs.
- **Now / later:** consumables heal the pool, XP from health restored (capped), soon.

**Stealth: partial.**
- **XP:** none.
- **Effects:** Agility, only at admin-set levels; no stealth mechanic exists.
- **Not working:** placeholder code.
- **Attributes:** Agility.
- **Rewards:** none.
- **Needs:** an aggro design that avoids the Sneak-based ability keys.
- **Missing:** everything.
- **Future:** Assassin, Shadowblade.
- **Now / later:** later.

**Trap Mastery: disconnected.**
- **XP:** none: its XP code is never reached.
- **Effects:** none.
- **Not working:** about 2,000 lines of trap code. The listener and recipes are never registered, traps can't be armed, and they would deal 0 damage.
- **Attributes:** none.
- **Rewards:** none.
- **Needs:** a rebuild.
- **Missing:** everything.
- **Future:** Ranger, Artificer.
- **Now / later:** later, never by registering the old listener.

**Scavenging: placeholder.**
- **XP:** none.
- **Effects:** Fortune (does nothing).
- **Not working:** a placeholder loot hook; luck managers that multiply without a cap.
- **Attributes:** Fortune.
- **Rewards:** none.
- **Needs:** a Fortune rule.
- **Missing:** everything.
- **Future:** exploration rewards.
- **Now / later:** loot-chest XP (once per container) with Mining's Fortune rule, soon.

**Repairing: placeholder.**
- **XP:** none.
- **Effects:** none.
- **Not working:** example code.
- **Attributes:** none.
- **Rewards:** none.
- **Needs:** durability that matters (entropy).
- **Missing:** everything.
- **Future:** anvil and forge repair.
- **Now / later:** later.

**Trading: placeholder.**
- **XP:** none.
- **Effects:** Fortune (does nothing).
- **Not working:** the class says itself that it does nothing.
- **Attributes:** Fortune.
- **Rewards:** none.
- **Needs:** an economy.
- **Missing:** everything.
- **Future:** villagers, markets.
- **Now / later:** later.

**Navigation, shown as Pathfinding: functional (thin).**
- **XP:** 5 the first time you enter a chunk (per world, ever), so walking back and forth pays nothing. Gaps:
  - creative and spectator flight count;
  - a chunk far out pays the same as one at spawn;
  - each new chunk is its own database write.
- **Effects:** Agility and movement speed: +2.6% at 25, +8.6% at 100 (Played).
- **Not working:** its own effect code (biome speed tables, stubs); `/cerb speed` prints a retired rule.
- **Attributes:** Agility, movement speed.
- **Rewards:** percentages; a new chunk gives no feedback.
- **Needs:** the XP display.
- **Missing:** danger-weighted discovery; first-visit bonuses.
- **Future:** Scout, Blink Gates, flight.
- **Now / later:**
  - Now: scale chunk XP by the area's creature level, skip creative and spectator, batch the writes, show discoveries on the HUD.
  - Later: the rest.

**Animal Taming: placeholder.**
- **XP:** none.
- **Effects:** none.
- **Not working:** example code with empty stubs.
- **Attributes:** none.
- **Rewards:** none.
- **Needs:** pets that matter.
- **Missing:** everything.
- **Future:** Beastmaster, mounts.
- **Now / later:** taming XP with starter mounts.

**Riding: placeholder.**
- **XP:** none.
- **Effects:** none.
- **Not working:** example code that would compound if wired.
- **Attributes:** none.
- **Rewards:** none.
- **Needs:** mounts that matter.
- **Missing:** everything.
- **Future:** mounts and vehicles (`MOUNTS_AND_VEHICLES.md`).
- **Now / later:** XP from new chunks reached while riding, and mounted kills, with starter mounts.

**Lockpicking: placeholder.**
- **XP:** none.
- **Effects:** Fortune (does nothing).
- **Not working:** nothing to pick.
- **Attributes:** Fortune.
- **Rewards:** none.
- **Needs:** locked content.
- **Missing:** everything.
- **Future:** dungeons.
- **Now / later:** later.
