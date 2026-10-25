# Cerberus — Development Roadmap

*Owner direction of 2026-10-25, recorded as given. "Approved" = the owner's decision. "Proposal" = a starting point the owner wants reviewed and refined. Status of finished work: `docs/STATUS.md`.*

## The three priorities

- **Freedom:** players can train several classes and build their own combination of abilities.
- **Depth:** different disciplines, weapons and abilities play genuinely differently.
- **Performance:** as Cerberus grows, unnecessary processing keeps being removed so play stays smooth.

> *Cultivation determines how far you've progressed. Skills show what you've trained. Attributes represent your capabilities. Classes provide specialization trees. Equipped abilities define your current fighting style.*

Cerberus stays an original cultivation RPG, inspired by Chinese cultivation fantasy, Hypixel SkyBlock's depth, Wynncraft's adventure and some Black Desert Online combat ideas, without copying their classes, mechanics, names, visuals or progression.

## Principle: performance comes first (approved)

Before any significant new system or major feature, examine the code it touches and make it faster and leaner where that is measurably worth it. The target is richer gameplay with less work per player, entity and tick. Areas of particular interest: attribute snapshots, combat calculations, GUI rendering, entity management, particles, persistence, Folia region scheduling.

**Workflow at the start of every major milestone:**
1. **Measure** a reproducible baseline for the systems the milestone affects.
2. **Find the actual bottlenecks**: CPU, needless calculations, redundant work, allocations, repeated database access, costly entity updates, wasteful scheduled tasks.
3. **Fix the confirmed bottlenecks and low-risk inefficiencies first.**
4. **Keep correctness:** gameplay, persistence, combat math, Folia safety and player data must not break.
5. **Measure again** under a comparable workload and report the difference.
6. **Then build the feature.** No speculative rewrites, no micro-optimizations without measurable benefit, and no endless optimization that blocks content.

## Development order

| Step | What | State |
|---|---|---|
| 0 | Finish the current milestone (owner decisions on the 75% cap, PvP, Weapon Mastery; playtest checklist) | Done 2026-10-25 |
| 1 | Performance assessment and targeted optimization (then repeated at the start of each later milestone) | Done 2026-10-25: `docs/PERFORMANCE.md` (Cerberus time: menu clicks −66 to −69%, combat −17 to −37%, hits taken −21 to −30%) |
| 2 | Decisions A–E (below), as small testable changes | Done 2026-10-25: `docs/STATUS.md` (each verified in game, before and after; within the performance budget) |
| 3 | Player Menu 2.1: animations and feedback | Done 2026-10-25: `docs/STATUS.md` (21 in-game checks; within the performance budget; an idle open menu costs nothing) |
| 4 | Classes and abilities **design**: review the roster and the weapon archetypes; architecture and phased plan | Done 2026-10-25: `docs/design/CLASSES.md` (eight owner decisions in its §10, each with a recommendation) |
| 5 | **Prototype** the ability system with 2–3 distinct trees (suggested: Warrior, Mage, Duelist), verified in game | Done 2026-10-25: `docs/STATUS.md` (the six proofs in game, 91 checks; independent review fixed; within the performance budget: non-casting players pay nothing, class logic ~0.03 ms per key press). Built on the §10 recommendations; PvE-only for now (PvP is turned on later, see PvP below) |
| 6 | Scale up classes and abilities incrementally, keeping quality, identity, fair progression and performance | Becomes Phase 4 below: after the ability controls and the three classes are reliable (owner, 2026-10-25) |

Rules for every step: routine engineering decisions are made by the lead developer; material design conflicts, scope changes and destructive operations go to the owner with a recommendation. Work stays on the development branch; nothing merges into `master` automatically. The owner's world, characters, inventories and databases are never touched by tests; risky changes are tried on the separate development server.

**Earlier items, still on the list:**
- **Gathering, crafting, meaningful resource progression, making Fortune useful:** now Phase 5 below.
- **Architectural goals to preserve, not to build yet:** procedural world generation, modular structure building, a custom resource pack, custom mobs and living-world systems. They should eventually support generated settlements, temples, dungeons, ecosystems, resources and dynamic world events. They are never built all at once, and the existing world is never modified without permission.

## After the class playtest: quality before quantity (owner, 2026-10-25, approved)

The owner played the Warrior / Mage / Duelist prototype. **Feedback:** Sneak + left-click was unclear, with no clear sign whether anything activated; Sneak + F did Minecraft's off-hand swap; Arcane Bolt and other Mage abilities did not work reliably with the focus. The automated checks had passed, so the tests must improve too.

**Principle:** three classes with reliable, enjoyable, polished abilities beat nine with awkward controls. Build a strong ability-control foundation now, so every future class uses it. A feature is not good enough just because it technically works: it must be understandable, responsive, rewarding and fun.

| Phase | What | State |
|---|---|---|
| 1 | **Ability controls.** Reproduce the playtest problems before fixing them. Research how Wynncraft and Hypixel SkyBlock activate abilities (their real controls; learn, don't copy). Design an original control scheme a vanilla 1.21.11 client supports (no client mods; a resource pack may only improve presentation). Clear feedback for success, cooldown, too little Essence, missing weapon or focus, invalid target or range, not unlocked or not equipped, and disabled here: action bar, sounds, light effects, no chat spam. The activation layer stays separate from the ability definitions; progress, ranks, loadouts and the PvP limits are kept. | In progress |
| 2 | **Audit all 21 abilities individually** (Warrior 7, Mage 7, Duelist 7): real behaviour verified in the world (damage, movement and landing, mitigation, buffs and durations, attribute contributions, keystones) in every relevant situation (still, moving, sneaking, jumping, switching items, right and wrong weapon, focus, empty hand, no Essence, cooldown, valid and invalid targets, one and many enemies, edge of range, loadout changes, relog, restart). Combinations across classes without cooldown resets, duplicate buffs, damage multiplication, bad refunds, XP farming, item loss or PvP bypass. A better harness: real input sequences, positive and negative controls, a clean predictable arena, bot limits told apart from plugin behaviour, manual checks where automation can't judge feel. | Next |
| 3 | **Polish Warrior, Mage and Duelist** into the quality standard: recognisable identities, clear visual and audio feedback, satisfying execution, distinct strengths and weaknesses, good cross-class combinations, predictable costs. Fix weak-mob class-XP farming (eligibility or reward scaling). Upgrades and advanced tiers stay on the roadmap, not rushed. | |
| 4 | **Step 6: the next six classes**, one at a time: Knight (defense, counters, protection), Ranger (ranged mobility, precision, wilderness), Cleric (healing, support), Assassin (stealth, ambush, burst), Elementalist (elemental interactions), Summoner (allies and constructs). Each with real abilities, individual tests and a complete gameplay loop, on the shared engine, attributes, Essence and progression. The ~25-class roster stays the plan; nothing is deleted or finalised before its design review. | After 1–3 |
| 5 | **Gameplay depth and systems completion.** A code-grounded audit of all 35 skills (how XP is earned, what works, what is disconnected, what to add, beginner-to-mastery path, links to other systems). Then real gathering and crafting loops, Mining, Woodcutting, Farming, Fishing, Smithing, Crafting and Alchemy first: resources, progression, rewards, recipes, equipment interactions, new capabilities as skills grow, not just percentages. **Future-proof:** registries, resource definitions, tool capabilities, reward rules and extension points, so a later custom ore, tree, plant, tool, drill or machine plugs in without rewriting the progression. No giant speculative frameworks: today's gameplay done properly, proven by small synthetic extension tests. | |
| 6 | **Advanced mastery, level 100 → 150** for all 35 skills: a skill-specific mastery challenge at 100 unlocks 101–150, permanently per player and skill; real progression and milestone rewards, not 50 empty levels. **Heavy Armor Mastery first:** its high-level defense bar feels weak; look at capacity, regeneration, defensive techniques and breakthroughs, with the scaling math checked against realm magnitude and enemy damage before any per-level doubling. Caps migrate safely; no progress is lost. | |
| 7 | **Review the skeletal systems:** equipment and weapon archetypes, techniques, cultivation, crafting and processing, mobs and Bestiary, exploration and regions, quests and collections, structures and procedural generation, living world, mounts. For each: functional, prototype, placeholder or plan. Complete loops that connect systems first. | |
| 8 | **Playtest backlog** (below), placed into the phases above. | |

Skill depth (5) and mastery (6) may share infrastructure and be coordinated; class expansion must not postpone the noncombat gameplay indefinitely. Profiling, correctness tests and regression tests recur in every phase.

**Phase 2 is complete only when:** every ability has an individually documented result; activation works reliably through the intended scheme; effects match their descriptions and config; mixed-class abilities work together; persistence and exploit protections pass; the earlier regression suite is green; an independent reviewer has checked the important changes; and the owner has clear instructions to try the new controls. **Usability is not called verified until the owner has played the revised controls.**

**Playtest backlog** (to investigate, not assumed to be code defects):
- **Passive mob health bars:** some passive animals (pigs, horses) showed no Cerberus health bar. Check the intended display rules and whether passive mobs are registered, tracked and updated.
- **Weapon names:** the owner prefers **Furnace Hammer** (Cauldron-Hammer) and **Runeblade** (Talisman-Blade) as Western-fantasy display names; mechanics and internal ids stay. Settled when the weapons are built (see `docs/design/CLASSES.md` §4).
- **Skill progression:** the level-100 cap and the weak-feeling Heavy Armor rewards: Phase 6.

## Step 2: decisions A–E (approved)

| | Decision | What it means |
|---|---|---|
| A | **PvP speed bonuses stay off in PvP** | For now, not permanently (see PvP below). Keep the 15-second PvP suppression until the full PvP integration is designed and verified. No extreme attack-speed combinations, cooldown bypasses or unrestricted ability stacking in PvP. |
| B | **Powered shelves must work** | Replace today's protection, which refuses every powered-shelf swap for everyone carrying the menu head. Find a Folia-safe mechanism that keeps normal shelf interactions working while the head still can't be duplicated, deleted, swapped into an invalid state or otherwise exploited. Positive and negative tests. |
| C | **Survival's fire resistance covers environmental fire** | Extend it past standing in fire to burning, lava, magma blocks, campfires and related fire damage, using the existing defensive calculations. Keep the categories clear. Bounded: no immunity. |
| D | **Projectile hits train Survival and Heavy Armor Mastery** | Only real defensive encounters count. Guard against harmless repeated hits, invulnerability windows, self-inflicted damage and artificial farming. |
| E | **Spears train Weapon Mastery** | Spear attacks and kills give Weapon Mastery XP. XP and combat bonuses use the same weapon classification, with no duplicate rewards, and current skill records stay compatible. |

## Step 3: Player Menu 2.1 animations (approved goals)

The owner tested Player Menu 2.0 in game and is very happy with it. The next step is tasteful, lightweight life, using vanilla capabilities first; resource-pack visuals come later. Candidates (not all required):
- a brief, elegant opening animation;
- subtle highlighting of the selected category;
- smooth-looking XP and progression updates;
- small transitions between skill and character screens;
- distinct feedback for unlocks and important milestones;
- sounds for navigation, selection and achievements;
- later, special presentation for cultivation breakthroughs.

**Constraints:** no excessive particles, constant inventory rebuilding, flashing, long delays or visual noise. The menu stays fast. Every animation task is scheduled properly, cancelled when no longer needed, and Folia-safe. The goal is polish, not spectacle.

## Steps 4–6: flexible multiclass and ability trees

### The vision (approved)

There is no permanent class lock. About **25 classes** to start (more later where a new playstyle justifies it), working as **interconnected specialization and ability trees**:
- **A. Learn multiple classes.** Unlock and train several trees over time without losing earlier progress.
- **B. Combine abilities across classes.** This is the heart of the system: personal builds from compatible actives and passives of every mastered tree. For example, a Warrior defense, a Duelist rapid strike, a Mage mobility spell and a Ranger precision passive.
- **C. Unlocked is not the same as equipped.** Earned abilities stay earned. A loadout has meaningful limits (active slots, passive slots, resource costs, cooldowns, compatibility rules), but no arbitrary bans just because a combination crosses class boundaries.
- **D. Flexible build switching.** Several saved loadouts with convenient switching. No exploits: switching must not reset cooldowns, stack temporary bonuses from inactive classes, or change builds mid-combat to bypass limits. Switching never destroys progress.
- **E. Integrate, don't duplicate.** Use the realms, skills, primary and secondary attributes, combat calculations, Essence and other resources, equipment and weapon archetypes, the unified stat aggregation and character persistence. **No competing attribute system and no per-class combat engine.**
- **F. Distinct identities.** Not 25 classes that differ by passive percentages. For example, a Duelist has precision counters and chained strikes; a Warden has barriers and protects allies.

**Balance:** freedom with understandable rules. Every passive flows through the attribute engine and the controlled-hyperscaling limits. Interesting combinations (stacked mobility, for example) are welcome; limit-bypassing (cycling temporary buffs through loadouts) is not. Trade-offs, not forced identical builds, and no restriction added only because it is convenient to implement.

### Proposed roster (proposal, to be reviewed)

| # | Class | Identity | # | Class | Identity |
|---|---|---|---|---|---|
| 1 | Warrior | Balanced melee | 14 | Arcanist | Advanced magic, Essence control |
| 2 | Knight | Heavy armor, defense | 15 | Summoner | Summoned beings and constructs |
| 3 | Berserker | Aggressive high-risk offense | 16 | Cleric | Healing and support |
| 4 | Duelist | Precision, counters, fast melee | 17 | Paladin | Empowered melee, protective magic |
| 5 | Brawler | Unarmed combat and combos | 18 | Warden | Barriers, defensive control |
| 6 | Ranger | Mobile archery, wilderness | 19 | Druid | Nature magic, transformations |
| 7 | Marksman | Long-range precision | 20 | Alchemist | Potions and concoctions |
| 8 | Scout | Mobility and tracking | 21 | Warlock | Curses, risky power |
| 9 | Assassin | Stealth and ambushes | 22 | Necromancer | Undead summons, life drain |
| 10 | Beastmaster | Animal companions | 23 | Shadowblade | Stealth, movement and magic |
| 11 | Mage | General offensive magic | 24 | Artificer | Devices, traps, constructs |
| 12 | Elementalist | Elemental control and reactions | 25 | Spiritbinder | Spirits, spirit-weapon techniques |
| 13 | Spellblade | Melee weapons with magic | | | |

At Step 4, review overlaps, roles, progression dependencies and the Western-forward naming doctrine (`docs/design/NAMING.md`). Refinements are welcome. Removing major class choices or locking the roster is discussed with the owner first. Quality comes before hitting a class count.

### The 21 weapon archetypes (designed content, not implemented)

The reference is `docs/design/WEAPONS.md` (mechanics, speed, attack properties, governing skills); keep it coherent unless a change is approved. At Step 4, propose Western-forward display names where appropriate, keeping the mechanical archetypes. Weapons are not locked to one class: a Mage can use a sword, and a Warrior can learn some magic. Some abilities may require a weapon type where that is mechanically justified.

Jian / Straight Sword · Dao / Saber · Greatsword / Zhanmadao · Qiang / Spear and Polearm · Staff / Cudgel · Hammer / Maul · Axe / Battleaxe · Recurve Bow · Crossbow · Thrown Weapons · War-Fan · Whip / Chain Weapons · Fists / Gauntlets · Dual-Wielded Weapons · Flying Sword · Spirit Whip / Soul Lash · Talisman-Blade · Cauldron-Hammer · Shortbow · Longbow / War-Bow · Spirit-Gun / Qi-Repeater

### The ability engine (requirements)

The engine must handle:
- active and passive abilities;
- class and specialization trees;
- prerequisites and progression requirements;
- cooldowns and shared cooldown groups;
- Essence and other resource costs;
- interactions and combinations;
- weapon compatibility;
- saved loadouts;
- server-authoritative activation and validation;
- attribute contributions through the unified engine;
- data-driven class and ability definitions;
- persistence, versioning and growth.

Large amounts of content should not need new Java per ability, but dedicated code is fine for genuinely new mechanics. It must stay fast and Folia-correct throughout.

**Start small (Step 5).** Prototype 2–3 substantially different trees and prove:
1. progress in more than one tree;
2. progress is kept when switching;
3. compatible abilities from different trees can be equipped together;
4. loadouts can be saved and switched;
5. combat feels meaningfully different;
6. no cooldown, duplication, buff-stacking or persistence exploits.

Only then grow toward the full roster. A class is never called implemented just because it has a menu entry or a config definition.

**Player Menu → Classes & Abilities** (when the system exists): every class or discipline, unlocked progression, actives and passives, prerequisites, equipped abilities, saved loadouts, combinations and their attribute contributions. It keeps Player Menu 2.0's style, with subtle unlock effects and clear progression indicators. Nothing is labelled functional before the gameplay exists.

## PvP: off for now, not permanent (owner, 2026-10-25)

Every PvP limit so far is **temporary**. None of it is a decision against PvP; these parts are simply not turned on yet. A later **PvP pass** turns them all on, with PvP-specific numbers where needed, and is verified in game like any milestone. Until then each limit stays switchable and listed here, so nothing is forgotten.

| Off for now | Where it is switched | To turn it on |
|---|---|---|
| Attributes on defense in PvP (Vitality, Fortitude, realm): PvP hits use the pre-Attributes-2.0 defense | `attributes.yml` → `integration.pvp-defense: false` | Set `true`, then tune |
| Speed bonuses in PvP (attack, movement, bow draw, spell cooldown) and the faster-drawn arrow's extra damage (decision A) | `attributes.yml` → `secondary.in-pvp: false` (tag length `pvp-tag-seconds: 15`) | Set `true` |
| Attributes on offense in PvP (Power, crits, lifesteal): PvP hits still use the old per-skill bonuses | Code: `Manager/PveOffense` runs only on player → mob hits; PvP goes through `Manager/CombatSkillProcessor` | A PvP offense path with PvP coefficients |
| Abilities hitting players: every ability targets mobs only (Cleave, Fireball...); Riposte only counters mobs; Feint's crit only on mob hits; a Sneak-attack on a player is a plain hit | Code: `Classes/AbilityCaster.valid`, the counter hook in `Listener/VirtualHealthListener`, `Manager/PveOffense.meleeHit`, `Spells/FireballSpell.castFromAbility`, `Classes/AbilityListener.onAttack` | `docs/design/CLASSES.md` §10 decision 5 (PvP coefficients), then one switch |
| Class XP from fights with players | Code: `Listener/MobKillListener` gives no class share for player kills; casts never reach players | Together with abilities in PvP, with rules against farming each other |

## Reporting, per milestone

Report each milestone with (owner's format, 2026-10-25):
1. what was completed;
2. what problems were found and fixed;
3. which tests passed;
4. what the performance measurements show;
5. what the owner needs to test personally;
6. what remains unfinished;
7. what comes next.

Performance claims need benchmarks and gameplay claims need in-game checks. Small, reliable, reusable, well-tested components, not huge disconnected changes. A feature is not finished because it compiles, has a menu entry or a config definition: it is verified in gameplay, and the docs say honestly what works, what is incomplete and what is still a proposal. Significant code or architecture changes get a focused independent review; findings are verified before acting on them.
