# Cerberus — Classes & Abilities (design)

*Step 4 of `docs/ROADMAP.md`, 2026-10-25. **A proposal for the owner's review: nothing here is built.** It turns the owner's direction (ROADMAP "Steps 4–6") into an architecture, a roster review, weapon display names, exploit-proof loadout rules and a phased plan. Decisions that are the owner's are collected in §10, each with a recommendation.*

## 0. In one screen

- **A class is a specialization tree, not a lock.** A player can learn every class over time. Earned progress is never lost.
- **Unlocked is not equipped.** Each class unlocks actives and passives as its rank rises. A **loadout** decides what is in use. It mixes abilities from any learned classes in **5 active slots + 3 passive slots + 1 keystone**, with a few understandable rules (§5.5). There are no bans just because two abilities come from different classes.
- **One engine, data-driven.** Classes and abilities are config (`classes.yml`, `abilities.yml`), built from about 15 reusable effect verbs (§5.2). Dedicated Java is written only for a genuinely new mechanic, never per ability.
- **Abilities are the designed "Techniques"** (CULTIVATION_REALMS §6.1): the same engine. Manuals stay as rare drops and become one way to unlock special abilities.
- **Everything numeric flows through the existing systems:**
  - passives and buffs are contributions in the Attributes engine (same caps);
  - damage goes through the existing damage pipeline;
  - costs come from Essence;
  - cultivation gates the higher tiers.
- **Exploit-proof switching:**
  - cooldowns belong to the player, not the loadout;
  - switching ends every temporary effect from abilities that leave the loadout;
  - no switching in combat.
- **Prototype (Step 5):** Warrior, Mage and Duelist, with 4 actives and 3 passives each, proving the owner's six points with in-game tests and a performance check.

> *Cultivation determines how far you've progressed. Skills show what you've trained. Attributes represent your capabilities. Classes provide specialization trees. Equipped abilities define your current fighting style.* (owner, ROADMAP)

## 1. How the layers fit

| Layer | Decides | Source of truth today | How classes use it |
|---|---|---|---|
| **Realm** (cultivation) | how far you have come | `Tier`/`Realm`, `PlayerRealms` | Gates class tiers (Adept nodes from Champion, Master nodes from Paragon) and extra passive slots and loadouts. Breakthroughs become class milestones. |
| **Skills** (35) | what you have trained | `SkillManager`, `skills.yml` | Class entry requirements and node requirements ("Shield Wall needs Heavy Armor Mastery 25"). Using an ability still trains the matching skills (a Mage spell trains Spell Weaving, as Fireball does today). |
| **Attributes** (6 + speed stats) | your capabilities | `AttributeService`, `attributes.yml` | Ability numbers read attributes: physical abilities scale with Power, spells with Essence, crits with Agility. Passives and buffs are new contribution sources, inside the same caps. |
| **Classes** (≈25) | your specialization trees | **new:** `ClassRegistry`, `classes.yml` | Ranks, trees, unlocks. |
| **Loadout** | your current fighting style | **new:** `LoadoutService` | Equipped actives, passives and keystone; saved presets. |

Nothing here is a second stat system or a per-class combat engine. A class can only add contributions to the attribute engine and requests to the damage pipeline.

## 2. The class model

### 2.1 Learning a class
- Every class has an **entry requirement made of skills**, never another class: for example, Duelist needs Blade Mastery 10 and Critical Strike 5. Meeting it opens the class at rank 1. Freedom stays intact: training is the gate, not an earlier choice.
- There is no limit on how many classes a player learns. Learning one never costs progress in another.
- Later, world content can add class **mentors or trials** as an alternative way in (Wynncraft-like adventure, original content). The rule stays the same.

### 2.2 Rank and class XP
- **Rank 1–50 per class**, on its own XP curve. It is shorter than a skill's: a class is a specialization, so a focused player masters one in a few weeks.
- **Class XP comes from playing that class in real encounters:**
  - each cast of its abilities that does something (damage dealt, damage prevented, healing done);
  - a share of every kill's XP split between the classes whose abilities were used in that fight.
- **The same anti-farm rules as skills:**
  - grey mobs (20+ levels below) pay ×0.25;
  - dummies, your own summons and players with no stake pay nothing;
  - the idle rule, if the owner adopts it for skills (STATUS open decision 8), applies here too.

### 2.3 Trees: unlock by rank, choose in the loadout
- Each class has **12–16 nodes in three tiers:**
  - **Novice:** ranks 1–15;
  - **Adept:** ranks 15–35, and realm Champion;
  - **Master:** ranks 35–50, and realm Paragon.
- **Node kinds:**
  - **Active** (a castable ability);
  - **Passive** (always on while equipped);
  - **Upgrade** (an alternative version of one of the class's actives, picked per loadout: *Cleave: Wide* or *Cleave: Heavy*);
  - **Keystone** (one per class: a big identity passive with a real trade-off, such as the Berserker's *Bloodrage*: more damage as health falls, no healing above 50%).
- **No points to spend and no respec.** A node unlocks when its rank and requirements are met, and stays unlocked. The meaningful choices are made in the loadout: which abilities, which upgrade of each, which keystone. They can change at any time out of combat. This follows the owner's "no permanent restrictions just because they are convenient": build variety comes from combination, not from locking players out.

### 2.4 Signature mechanics: distinct identities without 25 engines
Each class is built around **one signature mechanic**: a small rule its abilities create and spend. The mechanics are engine primitives written once and shared, about 12 in total, so classes differ in how they play, not in passive percentages.

| Primitive | What it is | Classes built on it |
|---|---|---|
| Momentum stacks | hits build stacks; finishers spend them | Warrior, Brawler (Combo) |
| Fury | stronger as health falls; spends health | Berserker, Warlock (Blood Price) |
| Counter window | a short timed window that negates the next hit and answers it | Duelist (Riposte), Knight (Guard) |
| Stealth state | unseen until you act; the first hit from stealth is an ambush | Assassin, Shadowblade |
| Mark | tag a target; your hits on it gain an effect | Marksman, Scout, Ranger |
| Summon | creatures bound to you, with upkeep and a limit | Summoner, Necromancer, Beastmaster |
| Element states & reactions | the 6-element grammar (burn, chill, shock…): two states combine into a reaction | Elementalist, Mage, Spellblade |
| Essence flow | spend, borrow and amplify Essence; cooldown control | Arcanist, Mage |
| Barrier / shield | absorb pools on self or others, and placed barriers | Warden, Knight, Paladin, Cleric |
| Heal & cleanse | restore health, remove statuses | Cleric, Druid, Paladin |
| Form | a transformation: an attribute set, a model change and form abilities | Druid |
| Devices | placed objects that act on their own (traps, turrets) | Artificer, Ranger |
| Concoctions | thrown or drunk mixtures built from your potions | Alchemist |
| Spirit bond | a spirit bound to your weapon, empowering Spirit Weapons and the Flying Sword | Spiritbinder |

## 3. The roster: a review of the 25 proposed classes

All 25 stay. Every name is already Western-forward (NAMING.md), so no renames are needed. The review only sharpens identities where two classes would otherwise overlap.

| # | Class | Role | Signature | Entry (skills) | Natural weapons |
|---|---|---|---|---|---|
| 1 | Warrior | balanced melee | Momentum | Blade Mastery or Weapon Mastery 10 | any melee |
| 2 | Knight | tank: guard, taunt | Counter window (Guard), barrier | Heavy Armor Mastery 15 | sword or spear + shield |
| 3 | Berserker | high-risk offense | Fury | Weapon Mastery 10, Survival 10 | axe, greatsword |
| 4 | Duelist | precision counters, chained strikes | Counter window (Riposte), chain | Blade Mastery 10, Critical Strike 5 | straight sword, saber, twin blades |
| 5 | Brawler | unarmed combos | Momentum (Combo), stagger | Martial Expertise 10 | gauntlets, fists, quarterstaff |
| 6 | Ranger | mobile archery, wilderness | Mark, devices (snares) | Ranged Precision 10, Survival 10 | shortbow, recurve |
| 7 | Marksman | long-range precision | Mark (weak points), charged shots | Ranged Precision 15, Critical Strike 10 | longbow, crossbow, Essence Repeater |
| 8 | Scout | mobility, tracking, support | Mark (reveal), movement | Pathfinding 15, Riding 5 | any (weapon-agnostic) |
| 9 | Assassin | stealth, ambush | Stealth (Ambush), poisons | Stealth 10, Critical Strike 10 | saber, twin blades, throwing arms |
| 10 | Beastmaster | animal companions | Summon (tamed animals) | Animal Taming 15 | any |
| 11 | Mage | general offensive magic | Essence flow, element states | Intelligence 10, Spell Weaving 5 | focus (wand, staff) |
| 12 | Elementalist | elemental control and reactions | Reactions | Elemental Mastery 15 | focus |
| 13 | Spellblade | melee weapons with magic | Element states on weapon hits | Blade Mastery 15, Spell Weaving 15 | sword, Sigil Blade |
| 14 | Arcanist | advanced magic, Essence control | Essence flow (amplify, borrow) | Arcane Knowledge 20, Intelligence 20 | focus |
| 15 | Summoner | summoned beings and constructs | Summon (constructs, elementals) | Summoning 15 | focus |
| 16 | Cleric | healing and support | Heal & cleanse, barrier | Field Medicine 15, Intelligence 10 | focus, mace |
| 17 | Paladin | empowered melee, protective magic | Auras, barrier | Heavy Armor Mastery 15, Defensive Magic 10 | warhammer, sword + shield |
| 18 | Warden | barriers, defensive control | Placed barriers, zones | Defensive Magic 15 | any |
| 19 | Druid | nature magic, transformations | Form | Herbalism 15, Survival 10 | quarterstaff, focus |
| 20 | Alchemist | potions and concoctions | Concoctions | Alchemy 15 | throwing arms |
| 21 | Warlock | curses, risky power | Fury (Blood Price), curses | Arcane Knowledge 15 | focus, Soul Lash |
| 22 | Necromancer | undead summons, life drain | Summon (from fallen foes), drain | Summoning 15, Arcane Knowledge 10 | focus |
| 23 | Shadowblade | stealth, movement and magic | Stealth (Shadow Step: blink, afterimages) | Stealth 10, Spell Weaving 10 | straight sword, twin blades |
| 24 | Artificer | devices, traps, constructs | Devices | Trap Mastery 15, Smithing 10 | crossbow, Furnace Hammer |
| 25 | Spiritbinder | spirits, spirit-weapon techniques | Spirit bond | Summoning 10, Blade Mastery 10 | Flying Sword, Spirit Weapons |

**Overlaps, and how each pair stays distinct (owner's call; nothing removed):**
- **Assassin and Shadowblade.** The Assassin waits unseen for one decisive ambush, with poisons and executes. The Shadowblade fights in motion: blinks, afterimages, shadow magic on a blade.
- **Ranger, Marksman and Scout.** The Ranger shoots on the move with snares and the wild. The Marksman stands still for charged, weak-point shots at long range. The Scout becomes a mobility and reconnaissance support class: marks, reveals, speed for the group, any weapon. That avoids a third archer.
- **Knight, Paladin and Warden.** The Knight is a personal tank: guard, block, taunt. The Paladin is an offensive hybrid: holy melee with auras for the group. The Warden protects others: placed barriers and zone control.
- **Summoner, Necromancer and Spiritbinder.** The Summoner keeps constructs and elementals on Essence upkeep. The Necromancer raises from the fallen and drains life. The Spiritbinder binds spirits to weapons and is the class bridge to Spirit Weapons and the Flying Sword (WEAPONS.md §5).
- **Mage, Elementalist and Arcanist.** The Mage is direct spell damage, the clearest first magic class. The Elementalist sets up element states and triggers reactions. The Arcanist bends the rules: Essence borrowing, cooldown control, amplifying spells. The Arcanist's higher entry makes it a natural second magic class.
- **Cleric and Druid.** The Cleric is a dedicated healer and protector. The Druid heals through nature and changes form; its strength is versatility, not raw healing.
- **Warrior and Berserker.** Momentum rewards steady pressure; Fury rewards risk.
- **Brawler and Duelist.** The Brawler staggers and juggles unarmed; the Duelist reads and counters with a blade.

Possible later additions, where a playstyle is missing: a dedicated **Bard / Battle Herald** (group buffs through rhythm) and a **Lancer** (mounted combat with MOUNTS_AND_VEHICLES). Proposals only.

## 4. The 21 weapon archetypes: display names

The mechanics stay exactly as WEAPONS.md §2 defines them. Only the player-facing names change, Western-forward, following NAMING.md (qi → Essence, dao → Mastery, talisman → sigil):

| # | WEAPONS.md name | Proposed display name | Class skill | Vanilla stand-in until custom weapons exist |
|---|---|---|---|---|
| 1 | Jian | **Straight Sword** | Blade Mastery | swords |
| 2 | Dao | **Saber** | Blade Mastery | — |
| 3 | Greatsword / Zhanmadao | **Greatsword** | Weapon Mastery | — |
| 4 | Qiang / Spear & Polearm | **Spear** (polearms in the same family) | Weapon Mastery | spears, trident (melee) |
| 5 | Staff / Gun (cudgel) | **Quarterstaff** | Martial Expertise | — |
| 6 | Hammer / Maul | **Warhammer** | Heavy Armor Mastery | mace |
| 7 | Axe / Battleaxe | **Battleaxe** | Weapon Mastery | axes |
| 8 | Recurve Bow | **Recurve Bow** | Ranged Precision | bow |
| 9 | Crossbow | **Crossbow** | Ranged Precision | crossbow |
| 10 | Thrown (chakram, dart, javelin) | **Throwing Arms** | Ranged Precision | trident (thrown) |
| 11 | War-Fan | **War Fan** | Martial Expertise | — |
| 12 | Whip / Soft Weapon | **Chain Whip** | Martial Expertise | — |
| 13 | Fists / Gauntlets | **Gauntlets** (bare **Fists**) | Martial Expertise | empty hand |
| 14 | Dual-Wield set | **Twin Blades** | Dual Wielding | two weapons |
| 15 | Flying Sword (yujian) | **Flying Sword** (kept: readable) | Blade Mastery + magic | — |
| 16 | Spirit Whip / Soul Lash | **Soul Lash** | Martial Expertise + magic | — |
| 17 | Talisman-Blade / Sabre-of-Cinders | **Sigil Blade** | Blade Mastery + magic | — |
| 18 | Cauldron-Hammer / Dao-Furnace | **Furnace Hammer** | Heavy Armor Mastery | — |
| 19 | Shortbow | **Shortbow** | Ranged Precision | — |
| 20 | Longbow / War-Bow | **Longbow** | Ranged Precision | — |
| 21 | Spirit-Gun / Qi-Repeater | **Essence Repeater** (pistol, rifle, hand-cannon) | Ranged Precision + magic | — |

- **Weapons are not class property.** A Mage can swing a sword, and a Warrior can learn some magic.
- **An ability states what it needs,** as a family rather than a class: `blade`, `polearm`, `blunt`, `axe`, `unarmed`, `bow`, `crossbow`, `thrown`, `focus` (wand or staff), `shield`, or `any`. The family is read from the same weapon classification that Power and kill XP already use (`Attributes.WeaponClass`, decision E). Custom archetypes extend it later without changing abilities.

## 5. Abilities and the engine

### 5.1 An ability is data
```yaml
# abilities.yml (illustrative, not final numbers)
cleave:
  class: warrior
  kind: active
  tier: novice
  unlock: { rank: 1 }
  weapon: [blade, axe, polearm]
  cost: { momentum: 0 }            # builds Momentum instead
  cooldown: 4s
  groups: [melee-burst]
  effects:
    - damage: { shape: cone, range: 3.5, angle: 100, scale: power, coefficient: 1.6, max-targets: 6 }
    - momentum: { gain: 1, per-target: true, max: 5 }
  upgrades:
    wide:  { effects[0].angle: 160, effects[0].coefficient: 1.3 }
    heavy: { effects[0].angle: 70,  effects[0].coefficient: 2.2 }
```
Validation at load refuses an ability whose numbers break the bounds (§8): a coefficient over the cap, a cooldown under the floor, an unknown verb or weapon family. Server owners can tune, but cannot make an infinite or limit-breaking ability by accident.

### 5.2 Effect verbs (the engine's vocabulary)
`damage` (melee strike, cone, circle, line, projectile, hitscan) · `dash` · `blink` (teleport, Folia `teleportAsync`) · `knockback` / `pull` · `status` (slow, stun, burn, poison, weakness: vanilla effects where they exist) · `buff` (a timed attribute contribution) · `shield` (an absorb pool, read by the defense bar layer) · `heal` · `cleanse` · `mark` · `stealth` · `summon` · `barrier` (placed, temporary, no world changes kept) · `stance` (a toggle) · `form` · `resource` (gain or spend Momentum, Fury or Essence) · `reaction` (element state + element state → effect).

About 15 verbs cover the 25 classes' identities. A new verb is real Java, reviewed and tested once, then reused by any number of abilities.

### 5.3 Casting: inputs and validation
**Proposed input** (vanilla client, no mods or resource pack): hold Sneak, then press one of five keys.

| Key (while holding Sneak) | Casts |
|---|---|
| right-click | active 1 |
| left-click (swing; at a mob too) | active 2 |
| swap-hands key (F) | active 3 |
| drop key (Q) | active 4 |
| scroll or any number key | active 5 |

- **Casting only works with a weapon, a focus or an empty hand.** With a block or a tool in hand, every key does what vanilla does, so building and using items are never affected.
- Each key is consumed, so nothing is dropped or swapped and the held slot doesn't change.
- **Why not "Sneak + 1–5":** re-selecting the slot you already hold sends nothing to the server, so one number key would always be dead. A scroll step and a number key also look the same to the server.

Alternatives are in §10. All inputs are plain events the server already receives.

**Server-authoritative chain, for every cast:**
1. the input belongs to the player;
2. the ability is in the active loadout;
3. it is unlocked (re-checked, never trusted from storage);
4. the held weapon fits;
5. the cooldown and its groups are ready, with a global 0.4 s between casts;
6. the cost can be paid;
7. the player can act (alive, not in a menu, not stunned or respawning).

Only then are the effects applied and the cost and cooldown committed, in one step on the caster's thread. The client never decides anything.

### 5.4 Costs and cooldowns
- **Essence** (the existing mana pool) for magic.
- **Class resources** (Momentum, Fury, Focus): small per-player counters with decay.
- **Cooldowns per ability, plus shared groups.** All blinks and dashes share a short `movement` lockout, so mobility can stack but not chain without limit.
- Cooldowns are **stored with the player**, not the loadout. The long ones survive relog.
- Speed stats already reduce spell cooldowns (Spell Weaving). The same rule extends to abilities, with the existing caps and the PvP pause (decision A).

### 5.5 Compatibility rules (the whole list)
1. One **stance**, one **aura**, one **form** and one **summon family** active at a time (exclusive tags).
2. One **keystone** equipped.
3. An ability works only with its **weapon family** in hand. It can be equipped anyway, and its slot shows "needs a blade".
4. No ability in two slots.

That is all. Crossing class boundaries is never a rule.

### 5.6 Attributes
- **Passives** are contributions in `AttributeService`, from a new source (`Class: Duelist — Precision`), and **only while equipped**.
- **Buffs** are timed contributions, ended by the engine (bounded, like menu animations).
- Both pass the existing limits: offense ×6, armor 40%, total reduction 75%, the speed caps and diminishing returns. A buff never stacks with itself (re-casting refreshes it); different buffs add up inside the caps.
- A loadout change recomputes the snapshot **once**, counted by the existing rebuild counter (`/cerb showqueues`).

### 5.7 Damage
- **Ability damage is built as a `CombatContext`,** tagged physical or with its element's existing damage type (PYROMANCY, CRYOMANCY…). It runs through the same pipeline as weapon hits and spells: the mob HP pool, the player's defense layers, the combat trace, damage numbers.
- **Coefficients scale with the attribute that fits:** Power for weapons, Essence for spells. Abilities therefore grow with skills, realms and gear for free, and inside the same doctrine.
- **PvP:** an open decision (§10). Until then, PvP keeps the owner's "as before" rule.

### 5.8 Folia
- **Inputs and casts run on the caster's region thread.**
- **Effects on other entities** run on that thread when it owns them, else on their own entity scheduler. This is the pattern already used for lifesteal.
- **Blinks** use `teleportAsync`.
- **Every lasting effect** (buff, shield, summon lifetime, barrier) is a bounded task on an entity scheduler with a maximum duration. It is cancelled on death, quit, loadout switch or disable. There are no global timers.

### 5.9 Persistence and versioning
- **One versioned document per player,** in a new table `player_classes(uuid, version, data, updated_at)`, holding:
  - learned classes with rank and XP;
  - loadouts: name, actives, passives, keystone, the chosen upgrades;
  - the active loadout;
  - long cooldown expiry times.
- **Saved through `AsyncSaveManager`:** snapshot on the player's thread, asynchronous write, coalesced like skills.
- **On load, everything is re-validated against current rules.** Unknown ids are dropped with a log line, illegal slots are emptied, and over-cap values are clamped. Stored data is never trusted. A `version` number lets later format changes migrate idempotently.
- **The table is created automatically, like the existing five.** No existing table or player record changes.

### 5.10 Performance budget
- **A cast** is a few map lookups plus one damage-pipeline call. Target: under 0.05 ms of server time per cast.
- **No per-tick work for idle players.** Active effects are bounded in duration and in targets (cones and circles cap their targets).
- **Attribute rebuilds** happen only on a loadout change and at the start and end of a buff.
- **Step 5 acceptance:** the `combat` workload stays within the Step 2 numbers plus noise. A new `abilities` load mode (bots casting on a cooldown rhythm) is measured before and after.

## 6. Loadouts and switching (the exploit rules)

- **Three loadouts to start;** one more at Ascendant and one at Transcendent.
- **Switching or editing:**
  - only out of combat: no damage dealt or taken for 8 s, and not PvP-tagged;
  - a 10 s cooldown between switches;
  - no casting for 1 s after a switch.
- **Cooldowns carry over:** a Blink on cooldown is still on cooldown in every loadout, and after relog.
- **Temporary effects end with their ability.** Leaving the loadout immediately ends every buff, stance, aura, form, summon and barrier from abilities not in the new one; passives recompute. Inactive classes can contribute nothing.
- **Changes are server-validated.** The menu only sends requests; the server checks slots, unlocks and exclusivity, then saves the whole document.
- **Switching never touches progress:** ranks, XP and unlocks live outside loadouts.

## 7. Player Menu → Classes & Abilities

The hub tile stays **In development** until the prototype works in game, so nothing is labelled functional early. Then, in Player Menu 2.0's style with the 2.1 animations:
- **Classes:** every class as a tile, either learned (rank bar), learnable (requirements met) or locked (missing requirements listed).
- **A class tree:** nodes by tier, as unlocked, next to unlock (with what's missing) or locked. A new unlock flashes once, like the skill bar's level-up.
- **An ability:** cost, cooldown, weapon family, effects with live numbers from the attribute engine, upgrades, and which slots it fits.
- **Loadouts:** 5 active, 3 passive and the keystone. Click a slot, then pick from what's unlocked. Shows the combined attribute contributions and any rule conflict in plain words, plus presets and the switch button (refused in combat, with the reason).

## 8. Balance: freedom with understandable rules

- **One power budget.**
  - Ability coefficients are bounded per kind; config validation enforces it. Proposal: at most 3.0× a weapon hit for a 4–8 s cooldown, 6.0× for 20 s or more.
  - Over a minute, any loadout's sustained damage stays within about ±25% of an equal-level weapon build. Burst, control, survival and utility are what differ.
- **The attribute engine's caps** bind everything (offense ×6, armor 40%, reductions 75%, speed caps). There are no class multipliers outside it.
- **Interesting combinations are welcome:** a Duelist parry, a Mage blink and a Ranger mark together. Stacking limits are shared groups, not class bans.
- **Cycling buffs through loadouts is impossible by construction** (§6).
- **PvP** gets its own coefficients in the PvP pass, as the owner asked for attributes.

## 9. Phased plan

| Step | Content | Proves |
|---|---|---|
| **5. Prototype** | Engine core: registry, loadout service, caster, cooldowns, attribute source, persistence v1, inputs. **Warrior, Mage, Duelist** with 4 actives and 3 passives each (below). A basic Classes & Abilities screen. In-game tests for the six points; an `abilities` load and profile. | Learning in several trees; progress kept when switching; cross-class loadouts; saved and switched loadouts; combat that feels different; no cooldown, duplication, buff-stacking or persistence exploit. |
| **6a** | Knight, Ranger, Cleric, Assassin, Elementalist, Summoner | New verbs: shield, heal, stealth, summon, reaction, mark |
| **6b** | Berserker, Brawler, Marksman, Spellblade, Warden, Arcanist | Reuse plus barrier and Essence-flow verbs |
| **6c** | Paladin, Druid (form), Necromancer, Beastmaster, Scout, Shadowblade | Form verb; summons from the fallen |
| **6d** | Alchemist, Artificer (devices), Warlock, Spiritbinder (spirit bond, with Spirit Weapons) | The last verbs |
| later | Manuals (rare drops unlocking special nodes), Echo Family abilities, Master-tier nodes tied to Breakthroughs | Content on the same engine |

Each 6x step follows the same rule: a performance baseline first, at most two new verbs, then content with in-game tests and a performance check. A class counts as implemented only when its abilities work in game.

**Prototype abilities (first numbers, for tuning in game):**

| Class | Actives | Passives |
|---|---|---|
| **Warrior** (Momentum) | *Cleave* (cone, 1.6× Power, builds Momentum per target); *Charge* (dash 8 blocks, stuns the first enemy hit for 1 s); *Execute* (finisher: spends Momentum, +40% per stack); *War Cry* (8 s: +15% damage, self only in the prototype) | *Battle-Hardened* (+Vitality); *Weapon Discipline* (+attack speed, inside the speed caps); *Juggernaut* (keystone: +Vitality and Fortitude, slower attacks) |
| **Mage** (Essence, element states) | *Arcane Bolt* (projectile, 1.4× Essence, cheap); *Fireball* (the existing spell, moved onto the engine); *Frost Nova* (circle, chills: slow, and sets up a reaction later); *Blink* (6 blocks forward; `movement` group) | *Essence Flow* (+Essence regeneration); *Spell Focus* (+Essence); *Glass Cannon* (keystone: +25% spell damage, −15% max health) |
| **Duelist** (Riposte, chains) | *Riposte* (0.6 s window: the next melee hit taken is negated and answered at 2.0× Power; a short cooldown on success, a longer one on a miss); *Lunge* (gap closer, 1.5× Power); *Flurry* (three quick strikes, each 0.6×); *Feint* (the next hit within 3 s crits) | *Precision* (+crit chance); *Footwork* (+movement speed); *Blade Dancer* (keystone: +critical damage, less Fortitude) |

## 10. Decisions for the owner

| # | Decision | Recommendation |
|---|---|---|
| 1 | **Abilities = the designed Techniques** (one engine), with Manuals as an unlock source | Yes. One system instead of two, and it keeps CULTIVATION_REALMS §6.1 meaningful. |
| 2 | **How to cast** | *Hold Sneak + right-click, left-click, F, Q or scroll* (§5.3), only with a weapon, a focus or an empty hand. Five distinct keys, no dead key, and building is untouched. Alternatives: Sneak + number keys (one key is always dead, and scrolling looks the same); click sequences (slower, and the signature of another game); a hotbar "ability mode" (risky for item safety). |
| 3 | **How trees progress** | Unlock by rank, with no points and no respec; choices made in the loadout and upgrades. Freedom and depth without permanent locks. |
| 4 | **Slots** | 5 active, 3 passive and 1 keystone; +1 passive at Champion and at Paragon; 3 loadouts, +1 at Ascendant and at Transcendent. |
| 5 | **Abilities in PvP** | Allowed, with PvP-specific coefficients and PvP's "as before" defense rule, until the PvP pass. The alternative is PvE-only for now. |
| 6 | **Class entry** | Skill requirements, never another class: open and readable. |
| 7 | **Roster** | Keep all 25 with the §3 refinements (Scout as mobility support; Arcanist as the advanced magic class). Confirm Warrior, Mage and Duelist for the prototype. |
| 8 | **Weapon display names** | The §4 table (Straight Sword, Saber, Quarterstaff, Warhammer, Sigil Blade, Furnace Hammer, Essence Repeater…). |

## 11. Prototype status (Step 5, 2026-10-25)

**Built and verified in game** (`tools/playtest/classes.js`, 85 checks on a Folia 1.21.11 sandbox; `docs/STATUS.md`):
- Warrior, Mage and Duelist with the abilities in §9 (4 actives, 2 passives and a keystone each; `classes.yml`, `abilities.yml`);
- the engine core: catalog with its power bounds (§8), loadouts and their rules, the caster, cooldowns with shared groups, the attribute source, persistence v1;
- the five cast keys;
- Player Menu → Classes & Abilities (classes, a class's abilities, loadouts, an ability picker), `/class`, and the admin `/cerb class <player> <class> rank <n> | info`.

The other 22 classes of the roster are design only.

**Settled while building.** These deviate from the text above; each takes the cautious side:
- **PvP.** Abilities never hit players, and a Sneak-attack on a player stays a normal hit. Decision 5 recommends allowing abilities in PvP with PvP coefficients. That would change how PvP plays, which the owner keeps as a separate decision, so the prototype is PvE-only until then.
- **Inputs.**
  - A bow or crossbow keeps Sneak + right-click, because it shoots with it.
  - A left-click whose cast doesn't go off (cooldown, Essence) is still a normal attack.
  - With a mob under the crosshair the client sends an attack, not an air click, so the cast comes from the attack.
  - Spears stab with their own action: their hit is caught as damage and replaced by the cast.
  - Spell items (the Fireball Wand) keep their own right-click.
  - The arm swing a client sends after dropping or using an item (a snowball from the off hand, a block) is not a left-click, so it never casts active 2.
  - An empty hand can't cast with Q, and its right-click reaches the server only on a block or a mob. In both cases the vanilla client sends nothing the server can act on. Scroll, F and left-click work empty-handed.
- **Arcane Bolt** is an instant bolt: a ray to the first enemy within 8 blocks, and walls block it. It is not a flying projectile.
- **Fireball** casts a mobs-only version of the existing spell. It flies through players, and its blast neither damages nor ignites them. Its 20 Essence is checked and paid by the ability engine before anything is spent.
- **Class XP.**
  - A cast earns 6 XP when it reaches a hostile mob (farm animals don't count), or when it is a self cast (buff, Blink, Feint) made during a fight.
  - Riposte earns it when it counters.
  - Casting into the air out of combat earns nothing.
  - A kill gives 50% of its skill XP, split between the classes whose abilities were used in the last 10 s; grey mobs are already reduced.
- **Essence abilities** have their cooldowns shortened by Spell cooldown recovery (capped, paused in PvP), never below 1 s.
- **Config changes never cost progress.**
  - A class removed from `classes.yml` keeps its saved rank and XP, unused, and gets them back if it returns.
  - An ability missing from `abilities.yml` (a typo, a rename) stays in its slot, inert, and works again when it returns.
  - A file that doesn't parse changes nothing. At startup classes stay off, and nothing is loaded or saved over. A wrong value refuses only its own entry, never the whole file.
  - A rank change empties only the slots it no longer allows, in every loadout.
- **Saves.**
  - Loads and saves go through one writer thread, in order, so a quick rejoin always reads the quit save.
  - Every snapshot carries an increasing stamp, and the database ignores an older one, so a late save can't overwrite newer progress.
  - In play, at most one snapshot per player per second; quitting and shutting down write at once.
  - A failed load, or a stored document that can't be read or comes from a newer version, turns classes off for that player's session. The stored data is left exactly as it was, never replaced by an empty profile.
