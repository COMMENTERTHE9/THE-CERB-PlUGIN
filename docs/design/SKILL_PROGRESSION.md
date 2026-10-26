# Skill progression: what every one of the 35 skills grows (design, 2026-10-26)

*Progression Depth & Woodcutting milestone, item 1 (`docs/ROADMAP.md`). Built on the code audit (`docs/SKILL_AUDIT.md`), the attribute engine (`docs/ATTRIBUTES_ENGINE.md`, `attributes.yml`) and the speed stats (`docs/design/ATTRIBUTE_EXPANSION.md`). This is the target design for all 35 skills. Each skill's part gets built together with that skill's own gameplay, never as a bonus without a way to earn it.*

## The rule (owner, 2026-10-26, permanent)

> Every one of the 35 skills improves at least one meaningful character attribute or relevant secondary stat. No skill exists only as an XP counter.

What follows from it:
- **One engine.** Everything a skill grows goes through the attribute engine:
  - its contributions (`attributes.yml`);
  - its caps and diminishing returns;
  - the PvP switch;
  - the Stats page.

  No skill gets its own stat manager.
- **Meaning, not padding.** A contribution must change play: what you can do, how fast, how much, how well. No numbers to fill a table.
- **Disciplines, not just damage.** Combat skills grow combat. A master fisherman, smith or farmer becomes extraordinarily capable *in that discipline*, through profession stats and capabilities, not weapon damage.
- **Balance holds:**
  - modest per-level rates;
  - caps with diminishing returns (`cap × (1 − e^(−raw/cap))`);
  - the 75% mitigation cap and the offense budget;
  - controlled scaling (`docs/design/POWER_SCALING.md`). Realm magnitude multiplies combat; professions grow in capability, efficiency, quality and access to higher resource tiers, not in runaway yield.

## The model: three layers on one engine

**1. Primary attributes: who your character is.** Every skill feeds one or two of the six, at a rate that matches how central the attribute is to the skill:

| Attribute | Drives (today) | Fed by, in this design |
|---|---|---|
| **Power** | Weapon damage, per weapon type | The weapon skills, each on its own weapons |
| **Agility** | Crit chance and damage; with skills, attack, draw and move speed | Precision and movement: Critical Strike, Dual Wielding, Pathfinding, Stealth, Riding, Crafting, Trap Mastery |
| **Vitality** | Max health | Endurance and care: Survival, Heavy Armor, Field Medicine, Woodcutting, Farming, Cooking, Animal Taming |
| **Fortitude** | Armor, defense bar, fire, cold and drowning resistance | Toughness: Heavy Armor, Survival, Defensive Magic, Smithing (heat and endurance), Repairing |
| **Essence** | Max mana, regeneration, spell damage, spell cooldown | Mind and spirit: the magic skills, Alchemy, Enchanting, Herbalism, Summoning |
| **Fortune** | A chance of one extra item from gathered resources (today: ore); later luck and loot | Luck: Scavenging, Trading, Lockpicking, and each gathering skill for its own resources (scoped, below) |

**Rates:**
- **1 point per level:** a combat skill's own attribute (Blade Mastery → Power on swords).
- **0.5 per level:** a skill's second attribute, and a gathering skill's Fortune.
- **0.25 per level:** the physical side of a profession (Woodcutting → Vitality).

A profession never becomes a damage source.

**2. Secondary stats: what you can do, and how well.** Each is owned by its discipline, capped, and shown on the Stats page:
- **Combat handling** (built): attack speed, draw speed, movement speed, spell cooldown recovery, plus crit chance and damage, lifesteal and resistances.
- **Profession stats:** each gathering and production discipline owns the stats that make its masters better at it. Some exist (gathering speed), some come with this milestone (Woodcutting's), the rest with their skills.

**3. Milestones: what you unlock.** Percentages alone don't make mastery. Every skill has milestone levels that unlock a capability, a technique, a recipe or an access, not just a bigger number:

| Level | Title | What it is |
|---|---|---|
| 10 | Apprentice | The discipline's first technique |
| 25 | Journeyman | A second technique, or the first upgrade of the first |
| 50 | Expert | The signature capability; harder resources open up |
| 75 | Master | The mastery version of the signature |
| 100 | Grandmaster | The capstone; later, the door to mastery beyond 100 (on the roadmap) |

Milestone levels get the stronger level-up celebration (item 3 of this milestone). Class entries at low levels (Warrior at Blade Mastery 5) stay as they are.

### Scopes: the same stat, aimed where it belongs

The engine already limits Power to one weapon type ("Power (swords)"). The same idea keeps professions distinct:
- **Fortune, scoped by resource.** Mining's Fortune counts on ore, Woodcutting's on wood, Farming's on crops, Fishing's on catches. Unscoped Fortune (luck skills, later talismans) counts on everything. A master miner gets more ore, a master woodcutter more wood, and neither gets the other's bonus.
  - **One rule for all of it:** 0.5% chance of one extra item per point, at most 50% (`resources.yml`), so Mining's numbers don't change.
  - **Forged tool quality** adds Fortune scoped to the tool's resource: a pickaxe for ore, an axe for wood.
- **Gathering speed, by the tool in hand.** One block-breaking stat, raised by the skill whose tool you hold: an axe gives Woodcutting's speed, any other item Mining's (today's rule). Farming (hoe) and later Excavation (shovel) take theirs when they exist. This settles decision 18 the way `ATTRIBUTE_EXPANSION.md` proposed ("set per tool type").
- **Later, the same way:** crafting quality scoped by material family (metal: Smithing; wood and leather: Crafting); potency scoped by consumable type (potions: Alchemy; meals: Cooking).

## The matrix

**Status key:**
- **Working**: earns XP in play and the effect is reachable (Played).
- **Partial**: some of it works.
- **Planned**: designed here, built with its system.

"Today" quotes `docs/SKILL_AUDIT.md` and later work.

### Combat (8)

| Skill | Primary | Secondary and profession stats | What changes in play | Milestones (capabilities) | Needs | Today |
|---|---|---|---|---|---|---|
| **Blade Mastery** | Power (swords) 1.0 | Attack speed (swords) | Swords hit harder and faster | 5: Warrior and Duelist entry. 25–100: sword techniques with the sword archetypes | Sword archetypes (weapons slice) | **Working**: Power, attack speed, class entry |
| **Weapon Mastery** | Power (axes, maces, tridents, spears) 1.0, all weapons 0.25 | Attack speed (its weapons); lifesteal on mob hits | Heavy weapons hit harder; the only in-fight healing | 5: Warrior entry. Later: per-weapon techniques | The Weapon Mastery split (your decision 2); weapons slice | **Working**; kill XP is credited to the killing attack from this milestone |
| **Ranged Precision** | Power (bows) 1.0 | Draw speed; planned: crossbow reload speed | Earlier full-power shots | 5: planned Ranger entry. 50: called shots (with bow archetypes) | Crossbow charge hook; bow archetypes | **Working**: Power, draw speed |
| **Martial Expertise** | Power (unarmed) 1.0 | Planned: knockback resistance; an unarmed damage term | A real unarmed build | 10: steady stance (knockback resistance). 50: Brawler techniques | Gauntlets, Brawler | **Partial**: weak fist Power only |
| **Critical Strike** | Agility 1.0 | Crit chance and damage (from Agility) | More and bigger crits | 5: Duelist entry. 50: planned crit techniques | — | **Partial**; XP becomes per target and capped (this milestone) |
| **Dual Wielding** | Agility 0.5 | Planned: off-hand attack share, twin-blade speed | Two weapons work together | 10: off-hand strikes | Off-hand weapons (Twin Blades) | **Planned**: no XP source |
| **Heavy Armor Mastery** | Fortitude 1.0, Vitality 0.5 | Armor, defense bar (from Fortitude); planned: knockback resistance in heavy armor | Tougher in heavy armor | 10: bonuses tied to heavy armor worn. 50: Knight entry | The idle rule (this milestone); XP by health share | **Partial**: XP rarely paid |
| **Survival** | Vitality 1.0, Fortitude 0.5 | Fire, cold and drowning resistance | Lasts longer in hostile places | 25: environmental resistances step up. 50: Ranger and Druid entries | XP by health share, the idle rule | **Partial**: XP paid; idle farms close this milestone |

### Magic (7)

| Skill | Primary | Secondary and profession stats | What changes in play | Milestones | Needs | Today |
|---|---|---|---|---|---|---|
| **Intelligence** | Essence 1.0 | Max mana, regeneration, spell damage (from Essence) | Bigger, stronger spells | 5: Mage entry | A normal player's way in (this milestone's Mage-route decision) | **Partial**: XP per cast only; on hit from this milestone |
| **Arcane Knowledge** | Essence 0.5 | Planned: spell efficiency (Essence cost −) | Cheaper spells | 25: arcane techniques | Enchanting and arcane spells | **Partial**: odd XP route |
| **Elemental Mastery** | Essence 0.5 | Planned: elemental potency (burn and chill strength) | Burns and chills bite harder | 25: element states. 50: reactions | Element states (design only) | **Partial**: XP per cast; on hit from this milestone |
| **Spell Weaving** | Essence 0.5 | Spell cooldown recovery | Faster casting | 10: planned spell combinations | — | **Partial (closest to working)**: on-hit XP from this milestone |
| **Essence Regeneration** | Essence 1.0 today (a copy of Intelligence); planned 0.5 | Planned: mana regeneration directly (its identity) | Mana comes back faster | 25: regeneration in combat | XP from Essence spent on hits | **Planned**: no XP source |
| **Defensive Magic** | Fortitude 0.5 | Planned: ward strength (absorb) | Shields, wards, barriers | 10: first ward verb | Shield verbs in the ability engine | **Planned** |
| **Summoning** | Essence 0.5 | Planned: summon strength and duration | Allies fight longer and harder | 10: first summon | Summon verb, ally AI | **Planned** |

### Gathering (5)

| Skill | Primary | Secondary and profession stats | What changes in play | Milestones | Needs | Today |
|---|---|---|---|---|---|---|
| **Mining** | Fortune (ore) 0.5 | Gathering speed (non-axe), +0.2% per level, cap 25% | More ore, faster mining | 50: tier-5 resources (custom ores need tier and level, `resources.yml`). Later: ore techniques | Custom ore tiers, distance-tiered nodes | **Working**: XP, speed, extra ore. Fortune scoped to ore since Woodcutting, numbers unchanged |
| **Woodcutting** | Vitality 0.25 | Fortune (wood) 0.5; chopping speed (axe in hand); Felling (logs one technique can take) | More wood, faster chopping, whole sections or trees at once | 10, 25, 50, 75, 100: techniques (`docs/design/WOODCUTTING.md`) | — | **Working** (2026-10-26): XP from natural logs, all three stats, Felling I–IV, Canopy, heartwood upgrades, discoveries |
| **Farming** | Vitality 0.25 | Fortune (crops) 0.5; harvesting speed (hoe in hand); planned: replanting | More and better crops | 10: replant on harvest. 25: wide harvest. 50: crop quality | Ripe-crop rules, crop tiers | **Planned** (next loop candidate) |
| **Fishing** | Fortune (fish) 0.5 (today: unscoped) | Planned: lure speed; treasure chance; sea-creature chance, capped | Faster bites, rarer catches, encounters worth having | 10: lure speed. 25: treasure. 50: sea creatures by level | XP per catch, idle check, encounter design | **Partial**: Fortune only; catches roll an uncapped Sea Monster (next loop candidate) |
| **Herbalism** | Essence 0.25 | Fortune (herbs) 0.5; planned: herb potency for Alchemy | Wild plants yield more and better reagents | 25: rare herbs. 50: reagent quality | The scope decision against Farming (wild plants vs crops) | **Planned** |

### Production (6)

| Skill | Primary | Secondary and profession stats | What changes in play | Milestones | Needs | Today |
|---|---|---|---|---|---|---|
| **Smithing** | Fortitude 0.25 (heat and endurance), planned | Craftsmanship, planned as a visible stat: the forged-quality odds it drives today | Better forged gear | 25: Superior odds. 50: reforging. 75: tempering | Reforging, realm materials | **Working**: XP, quality odds by level (not yet shown as a stat) |
| **Crafting** | Agility 0.25 | Planned: craftsmanship for wood and leather gear | Better bows, shields, tools | 10: woodworking recipes (with Woodcutting's materials) | Recipe rules (no XP for reversible recipes) | **Planned** |
| **Alchemy** | Essence 0.5 | Planned: potion potency and duration | Stronger, longer potions | 10: first Cerberus potion | Consumables that act on the health pool | **Planned** |
| **Enchanting** | Essence 0.5 | Planned: enchant power (budget) | Better enchantments within the offense budget | 25: enchant slots | The enchant budget design | **Planned** |
| **Cooking** | Vitality 0.25 | Planned: meal potency and duration | Meals as timed buffs | 10: first buff meal | Consumables | **Planned** |
| **Repairing** | Fortitude 0.25 | Planned: repair efficiency (durability restored, material saved) | Gear lasts longer for less | 25: field repair | Durability that matters (entropy) | **Planned** |

### Exploration and utility (9)

| Skill | Primary | Secondary and profession stats | What changes in play | Milestones | Needs | Today |
|---|---|---|---|---|---|---|
| **Pathfinding** | Agility 0.25 | Movement speed | Faster travel | 25: discovery rewards. 50: planned Scout entry | Danger-weighted discovery XP | **Working (thin)** |
| **Stealth** | Agility 0.25 | Planned: detection range − | Mobs notice you later | 10: sneaking techniques | An aggro design that avoids the Sneak ability keys | **Planned** |
| **Field Medicine** | Vitality 0.5 | Planned: healing effectiveness | Heals and consumables restore more | 10: first aid kits | Healing that reaches the health pool | **Planned** (Vitality only at admin levels) |
| **Scavenging** | Fortune 0.5 (unscoped) | Planned: loot luck in containers | Better loot | 25: rare finds | Loot-chest XP once per container | **Planned** |
| **Trading** | Fortune 0.5 (unscoped) | Planned: prices | Better deals | — | An economy | **Planned** |
| **Lockpicking** | Fortune 0.5 (unscoped) | Planned: pick speed and success | Locked content opens | — | Locked content | **Planned** |
| **Trap Mastery** | Agility 0.25 | Planned: trap potency | Traps that matter | — | A rebuild (never the old listener) | **Planned** |
| **Animal Taming** | Vitality 0.25 | Planned: companion bond (pet and mount stats) | Stronger companions | 10: starter mounts | Pets and mounts that matter | **Planned** |
| **Riding** | Agility 0.25 | Planned: mount speed and handling | Faster, steadier mounts | 10: mounted techniques | Starter mounts (`MOUNTS_AND_VEHICLES.md`) | **Planned** |

## What this milestone builds of it

1. **Woodcutting, complete** (`docs/design/WOODCUTTING.md`; built 2026-10-26):
   - Vitality 0.25;
   - Fortune scoped to wood;
   - chopping speed with an axe in hand;
   - Felling and its techniques at 10, 25, 50, 75 and 100.

   To make Fortune scoped, Mining's Fortune becomes Fortune (ore), with the same numbers.
2. **The XP-integrity rules** that feed Weapon Mastery, Ranged Precision, Martial Expertise, Critical Strike, Survival, Heavy Armor Mastery and the magic skills honestly (item 2 of the milestone).
3. **Milestone celebrations** for every skill at 10, 25, 50, 75 and 100 (item 3). Until a skill's own milestone capabilities exist, its milestone shows the stat it gained and the title.

Everything else above is built with its own gameplay: Farming or Fishing next, then the rest, one complete loop at a time.

## Rules for adding a skill's part later

- Add the contribution in `attributes.yml` (primary) or `attributes.yml → secondary` (stat), never in skill code. A new stat is one `Stat` value, its formula or application in one place (`AttributeRules` / `SecondaryEffects`), and a Stats-page line.
- Scope it if it belongs to one weapon, resource or tool.
- Give it a cap. Show it on the skill's page ("How it grows you") and in the level-up message.
- Pay its XP only for what really happened (the game's statistics, the final event state), with a negative and a positive control in a harness.
- Measure before and after when it adds work to a frequent event.
