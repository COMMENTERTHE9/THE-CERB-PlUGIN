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
| 1 | **Ability controls.** Reproduce the playtest problems before fixing them. Research how Wynncraft and Hypixel SkyBlock activate abilities (their real controls; learn, don't copy). Design an original control scheme a vanilla 1.21.11 client supports (no client mods; a resource pack may only improve presentation). Clear feedback for success, cooldown, too little Essence, missing weapon or focus, invalid target or range, not unlocked or not equipped, and disabled here: action bar, sounds, light effects, no chat spam. The activation layer stays separate from the ability definitions; progress, ranks, loadouts and the PvP limits are kept. | Done 2026-10-25: quick keys and click combos, feedback for every press, aim assist, an ability bar (`docs/design/ABILITY_CONTROLS.md`). **Owner's playtest:**
- Both schemes stay, permanently, as each player's choice. Both are supported and tested.
- The owner prefers click combos (R R R).
- The server default stays quick keys until broader player feedback.
- Remaining control bugs are fixed as found; no further redesign. |
| 2 | **Audit all 21 abilities individually** (Warrior 7, Mage 7, Duelist 7): real behaviour verified in the world (damage, movement and landing, mitigation, buffs and durations, attribute contributions, keystones) in every relevant situation (still, moving, sneaking, jumping, switching items, right and wrong weapon, focus, empty hand, no Essence, cooldown, valid and invalid targets, one and many enemies, edge of range, loadout changes, relog, restart). Combinations across classes without cooldown resets, duplicate buffs, damage multiplication, bad refunds, XP farming, item loss or PvP bypass. A better harness: real input sequences, positive and negative controls, a clean predictable arena, bot limits told apart from plugin behaviour, manual checks where automation can't judge feel. | Done in the sandbox 2026-10-25: every ability verified on its own (`docs/ABILITY_AUDIT.md`, `tools/playtest/abilities.js`), with the regression suite and an independent review. Feel: owner's playtest |
| 3 | **Polish Warrior, Mage and Duelist** into the quality standard: recognisable identities, clear visual and audio feedback, satisfying execution, distinct strengths and weaknesses, good cross-class combinations, predictable costs. Fix weak-mob class-XP farming (eligibility or reward scaling). Upgrades and advanced tiers stay on the roadmap, not rushed. | Started 2026-10-25:
- **Class XP farming fixed:** only enemies pay, and grey mobs pay a quarter (eligibility and scaling).
- **Execute is a real finisher:** it needs Momentum.
- **Keystones verified:** Glass Cannon and Blade Dancer by their values.

More identity work follows the owner's playtest |
| 4 | **Step 6: the next six classes**, one at a time: Knight (defense, counters, protection), Ranger (ranged mobility, precision, wilderness), Cleric (healing, support), Assassin (stealth, ambush, burst), Elementalist (elemental interactions), Summoner (allies and constructs). Each with real abilities, individual tests and a complete gameplay loop, on the shared engine, attributes, Essence and progression. The ~25-class roster stays the plan; nothing is deleted or finalised before its design review. | After the Gameplay Depth milestone (phase 5), by the owner's reordering of 2026-10-25. Not cancelled |
| 5 | **Gameplay depth and systems completion** (the audit and the first loop, Mining → Smithing → Equipment, are done). A code-grounded audit of all 35 skills (how XP is earned, what works, what is disconnected, what to add, beginner-to-mastery path, links to other systems). Then real gathering and crafting loops, Mining, Woodcutting, Farming, Fishing, Smithing, Crafting and Alchemy first: resources, progression, rewards, recipes, equipment interactions, new capabilities as skills grow, not just percentages. **Future-proof:** registries, resource definitions, tool capabilities, reward rules and extension points, so a later custom ore, tree, plant, tool, drill or machine plugs in without rewriting the progression. No giant speculative frameworks: today's gameplay done properly, proven by small synthetic extension tests. | **Now** (moved ahead of phase 4 by the owner, 2026-10-25): see "Gameplay Depth & Systems Completion" below |
| 6 | **Advanced mastery, level 100 → 150** for all 35 skills: a skill-specific mastery challenge at 100 unlocks 101–150, permanently per player and skill; real progression and milestone rewards, not 50 empty levels. **Heavy Armor Mastery first:** its high-level defense bar feels weak; look at capacity, regeneration, defensive techniques and breakthroughs, with the scaling math checked against realm magnitude and enemy damage before any per-level doubling. Caps migrate safely; no progress is lost. | |
| 7 | **Review the skeletal systems:** equipment and weapon archetypes, techniques, cultivation, crafting and processing, mobs and Bestiary, exploration and regions, quests and collections, structures and procedural generation, living world, mounts. For each: functional, prototype, placeholder or plan. Complete loops that connect systems first. | |
| 8 | **Playtest backlog** (below), placed into the phases above. | |

Skill depth (5) and mastery (6) may share infrastructure and be coordinated; class expansion must not postpone the noncombat gameplay indefinitely. Profiling, correctness tests and regression tests recur in every phase.

**Phase 2 is complete only when:** every ability has an individually documented result; activation works reliably through the intended scheme; effects match their descriptions and config; mixed-class abilities work together; persistence and exploit protections pass; the earlier regression suite is green; an independent reviewer has checked the important changes; and the owner has clear instructions to try the new controls. **Usability is not called verified until the owner has played the revised controls.**

## Combat Improvements, Replant Enchantment, Herbalism & Alchemy (owner, 2026-10-28, approved): built, waiting for the owner's playtest

The owner's two-phase milestone, built in verified increments on the development branch (never merged into `master` without the owner's word; the owner's Windows server, world and database are never touched; experiments only on the sandbox):

| # | Increment | State |
|---|---|---|
| 0 | **Preflight:** Active Effects' regression, persistence, performance and documentation confirmed; the independent review's findings fixed | Done (`docs/STATUS.md` → "Done: Active effects") |
| 1 | **Martial Expertise +1.25 Agility per level** (Power on bare hands kept) and **Iron Reprisal** (Heavy Armor Mastery throws melee attackers back; no chance, no reflected damage, PvP switchable) | Done (`docs/STATUS.md` → "Done: Combat improvements"; `docs/design/COMBAT_IMPROVEMENTS.md`). Found on the way and fixed: shields didn't keep creatures' blows off the Cerberus health pool |
| 2 | **Replant as a tool:** a registered enchantment if practical, otherwise a Cerberus hoe upgrade; the tool is the source, no Farming 10; Wide Harvest recognises it; seed accounting kept; a survival way to get it; a migration message | Done (`docs/STATUS.md` → "Done: Replant on the hoe"): the Replanting hoe upgrade from a Sower's Charm (`docs/design/FARMING_COOKING.md` §9) |
| 3 | **Lifesteal review:** measured analysis and a recommendation, no change (decision 6 stays until the owner decides) | Done (`docs/design/LIFESTEAL_REVIEW.md`, `docs/STATUS.md` → "Done: lifesteal review"): nothing changed; the recommendation (a per-hit cap, then Weapon Mastery's own weapons) waits for the owner |
| 4 | **Herbalism:** herbs as data, XP, discoveries, +0.25 Essence and +0.5 Fortune on herbs per level, potency/quality, milestones (rare herbs at 25, reagent quality at 50), one bounded active technique, the journal through the skill page and Collections | Done (`docs/STATUS.md` → "Done: Herbalism", `docs/design/HERBALISM.md`): 35 herbs, four rare reagents, reagent quality, Wildcrafting, the Herbarium |
| 5 | **Alchemy:** XP only for completed brews at the brewing stand, +0.5 Essence per level, potency and duration progression, potions as data with Western names, healing on the Cerberus pool, stacking and replacement rules with meals, shown through Active Effects, saved | Done (`docs/STATUS.md` → "Done: Alchemy", `docs/design/ALCHEMY.md`): completed brews pay their starter, eight draughts and elixirs as data, tiers from the level, Essence; an independent review's six Alchemy findings fixed |
| 6 | **Integration:** full regression, independent reviews, performance (MSPT, CPU samples, allocations, task counts), every document, the final report | **Verification open.** Regression and the review's six fixes are recorded (`docs/STATUS.md` → "Done: integration"). Codex closed the two deferred gameplay checks on the final build; the final performance results remain missing. Evidence and remaining limitations: `docs/HANDOFF_CODEX.md`. |

Not in this milestone: reflected damage, any lifesteal change, the proposals in `docs/AUTOMATION_REVIEW.md`, the cultivation realm system, a Folia upgrade.

## Next: proposed order (2026-10-28, waiting for the owner)

After playing the Farming and Cooking build ("everything seems to work"), the owner asked for the list of what comes next. A proposal: the order is the owner's to set, and nothing here is approved except item 1, the owner's own request.

**Right away (small):**
1. **Active effects in the Player Menu** (the owner's request, 2026-10-28): done (`docs/STATUS.md` → "Done: Active effects").
2. **Replant as a tool enchantment** (the owner's note, 2026-10-28; `docs/DECISIONS.md`): done by the milestone above, increment 2 (a hoe upgrade).
3. **Decision 6 (lifesteal):** reviewed by the milestone above, increment 3 (`docs/design/LIFESTEAL_REVIEW.md`): nothing changed; the owner decides after a playtest (checklist §20).
4. **A reviewed pull request into `master`** when the owner is happy with the branch (never merged automatically).

**Proposals waiting for a yes or no** (written, nothing built):
5. `docs/AUTOMATION_REVIEW.md`: the older anti-farming rules against "respect the player's time".
6. `docs/design/ENVIRONMENTAL_DAMAGE.md`: hazards that stay meaningful at high levels.
7. `docs/design/XP_EFFICIENCY.md`: Fluency, faster XP in a skill you're already good at.
8. The Farm Ledger (`docs/design/FARMING_COOKING.md` §6): machines paying their owner.

**The next skill loops** (12 of the 35 skills have a real loop now: Blade Mastery, Weapon Mastery, Ranged Precision, Pathfinding, Mining, Smithing, Woodcutting, Fishing, Farming, Cooking, Herbalism, Alchemy):
9. **Herbalism and Alchemy:** taken up by the milestone above (increments 4 and 5).
10. **Field Medicine:** healing items on the Cerberus health pool.
11. **Crafting, Enchanting and Repairing:** the item loop around Smithing.
12. **Animal Taming and Riding:** pets and mounts.
13. **Scavenging, Lockpicking and Trading:** with loot rooms, structures and trades (the world systems).
14. **The partial combat and magic skills** (Heavy Armor, Survival, Critical Strike, Martial Expertise, the magic skills, Stealth, Dual Wielding, Summoning, Trap Mastery): finished with the classes that use them.

**The big phases after** (on the roadmap already):
15. **The next six classes, one at a time:** Knight, Ranger, Cleric, Assassin, Elementalist, Summoner (Phase 4 above).
16. **Mastery 100 → 150,** Heavy Armor first (Phase 6).
17. **The skeletal systems:** weapons and gear, realms (Ascension), Bestiary and zones, quests, structures, mounts, the living world (Phase 7).
18. **The PvP pass:** every PvP limit turned on together ("PvP" below).

## Farming, Cooking and meaningful food (owner, 2026-10-28, approved): built; the owner played it (2026-10-28)

**Why.** Farming and Cooking as disciplines worth mastering, at the standard of Felling and Fishing: Farming grows better ingredients, Cooking turns them into meals that make the character stronger, and each stands on its own. Design: `docs/design/FARMING_COOKING.md`; the owner's rulings: `docs/DECISIONS.md`.

1. **Design and baseline** (done): the design, the `farm`, `widefarm` and `cook` loads, and the baseline measured before any code.
2. **Increment A, Farming** (done): ripe harvests broken or picked, 12 crops as data, discoveries, Vitality and Fortune on crops, Replant (10; on the hoe since 2026-10-28), Wide Harvest (25, bounded and protection-aware), crop quality (50), the Crops screen and Collections.
3. **Increment B, Cooking** (done): Cooking XP for food really made (furnace, smoker, campfire, crafting), six meals with tiers from level and ingredient quality, one timed effect at a time through the attribute engine, restoration on the Cerberus health pool, the Cookbook and Collections.
4. **Verification** (done): `farming.js`, `cooking.js`, the regression slices, an independent review and its fixes, performance before and after (`docs/STATUS.md` → "Done: Farming and Cooking"; `docs/PERFORMANCE.md`).
5. **The owner's playtest (2026-10-28):** "everything seems to work". One request: show the effects running on you (a meal and its time left) in the Player Menu, item 1 of "Next: proposed order" above. Still open: decision 6 (lifesteal) now that meals heal. Proposed, not built: the Farm Ledger (machine attribution), scythes, irrigation, custom crops for Herbalism and Alchemy.
6. **The owner's note (2026-10-28):** Replant moves from a Farming level unlock to a tool enchantment (`docs/DECISIONS.md` → Farming, Cooking and meaningful food). Done the same day: the Replanting hoe upgrade (`docs/design/FARMING_COOKING.md` §9).

## Fishing Equipment, Guided Discovery and Collections (owner, 2026-10-27, approved): built, waiting for the owner's playtest

**Why.** Give fishing goals and choices, not more menus: find a missing species, track it, choose a setup, fish, discover or beat a record, unlock the next option; treasure and sea-creature encounters as the other goals. Approved design: `docs/design/FISHING_EQUIPMENT.md`; decisions: `docs/DECISIONS.md`.

1. **Increment A, navigation and guidance** (done): skill-page sections, the shared Collections index, "Encounters" wording, the journal's filters, one tracked species, "Since your last visit".
2. **Increment B, equipment and convenience** (done): the setup on the character (Rod 1, Line 10, Lure 25, Reel 50), previews from the attribute engine, three presets, old rods kept and convertible, the Bait Bag, the Auto-hook Reel.
3. **Increment C, fishing goals** (done): Surveyor's Float, the Specimen Hook, the collection milestones, Keep as trophy.
4. **Verification** (done): fresh characters and the upgrade from the build before, a clean stop and a crash mid-cast, the regression slices, three independent reviews and their fixes, performance before and after (`docs/STATUS.md` → "Done: Fishing equipment, goals and collections"; `docs/PERFORMANCE.md`).
5. **Next:** the owner's playtest (`docs/PLAYTEST_CHECKLIST.md` §15); not authorized here: Farming, Cooking, cultivation, Fluency, PvP or class expansion.

## Fishing, and respecting the player's time (owner, 2026-10-27, approved): built, waiting for the owner's playtest

**Why.** The owner settled all 23 open decisions (`docs/DECISIONS.md`) and found Woodcutting's Felling "fantastic", the feeling every discipline should reach. Fishing is next, built to the same standard, under a new guiding philosophy.

**Principle: respect the player's time (approved, permanent; full text in `docs/DECISIONS.md`).**
- Automation is acceptable. Exploiting bugs, duplicating items, destroying the economy or harming server performance is not.
- AFK farming, macros for ordinary actions and clever farms are legitimate, and their normal rewards don't disappear.
- No inactivity timers, no anti-macro detection, no movement or interaction challenges.
- Duplication, fabricated reward events, crashes and uncontrolled entity spawning stay protected against.
- Active play earns more through challenges, discoveries, special encounters and opportunities, never by making passive play worthless.
- It guides Fishing first, then Mining, Farming, Woodcutting, combat training, crafting and every other discipline. The existing anti-farming rules are reviewed against it; the conflicting ones go to the owner with replacements, none removed automatically.

**In order:**
1. **Record the decisions and the philosophy** (`docs/DECISIONS.md`, this section, `CLAUDE.md`). **Done 2026-10-27.**
2. **The small approved changes** (**done 2026-10-27**; `docs/STATUS.md` → "Done: Fishing", checklist §6 and §13):
   - Spell Weaving 5 opens the Mage (decision 4, option A);
   - mob arrows and fireballs scale with mob level and tier, with caps, tested against players of different levels (decision 10);
   - `lib/DTR-1.0.0.jar` and `libs/Volor.jar` deleted once nothing is found to depend on them (decision 23).
3. **Fishing, a complete loop** (`docs/design/FISHING.md`):
   - satisfying from the first cast;
   - meaningful XP and attributes;
   - increasingly valuable discoveries and rewards;
   - milestones that unlock new capabilities;
   - different fish, different waters, rare encounters;
   - rod upgrades and useful resources;
   - real sea-creature battles: earned by any legitimate catch as a banked encounter, started when the player is ready, level-scaled, through the mob levels, combat engine, classes and abilities;
   - the same normal catch rewards for manual and automated fishing;
   - no uncontrolled mob spawning, no serious performance cost.

   It starts as a focused, playable version on the existing world and the gathering and combat systems, measured before and after. **Tested:**
   - ordinary fishing;
   - automation;
   - storing and redeeming rare encounters;
   - inventory edge cases;
   - persistence;
   - combat rewards;
   - economy and duplication boundaries.

   An independent agent reviews the important changes.

   **Done 2026-10-27** (`docs/STATUS.md` → "Done: Fishing"; `tools/playtest/fishing.js`; checklist §13).
4. **Proposals for the owner, not changes** (they must not delay Fishing; **written 2026-10-27**, waiting for the owner):
   - the anti-farming review (`docs/AUTOMATION_REVIEW.md`);
   - environmental damage with representative calculations (`docs/design/ENVIRONMENTAL_DAMAGE.md`);
   - the own-skill XP bonus investigation (`docs/design/XP_EFFICIENCY.md`).
5. **Report:** full results, manual playtest instructions, and the exact Git commands to update and launch the owner's server.

**Rules:**
- Performance first: measure before and after.
- Protect the owner's world and database: development and automated tests stay in the sandbox.
- Routine development and testing go ahead under the recorded decisions. Major new architectural choices, destructive changes and substantial new resource costs go to the owner.
- No automatic merge into `master`.
- Not redesigning every skill at once.

**Afterwards (approved, not started):**
- Farming, ideally together with Cooking.
- A Folia compatibility milestone: upgrading to a supported version with backups, plugin testing and a migration plan (decision 21). The owner's server and world are never upgraded automatically.
- Waiting for their milestones:
  - flat quality bonuses (equipment balancing);
  - the defense bar's capacity limit (the first bosses);
  - splitting Weapon Mastery (weapons);
  - a craftable beginner focus (magic equipment).

## Progression Depth & Woodcutting (owner, 2026-10-26, approved): done, 2026-10-26

**Why.** The owner playtested Mining → Smithing → Equipment and the XP display ("taking us in the right direction"). Next: every skill becomes a discipline worth mastering, progression feels alive, and gathering becomes deep, starting with Woodcutting. The quality target is the gameplay depth of Hypixel SkyBlock's foraging updates (0.23, June 2025; 0.27, August 2026). They are studied for design principles only: Cerberus copies no names, assets, maps, menus, formulas or implementations. Comparable depth comes over time, not in one milestone.

**Permanent rule (owner, 2026-10-26): every skill improves at least one meaningful attribute or secondary stat,** through the one attribute engine. No skill is only an XP counter, and no bonus exists just to fill a table. Not every skill needs to add combat damage: a master fisherman, blacksmith or farmer becomes extraordinarily capable in that discipline. Balance, diminishing returns and controlled scaling hold. All 35 are designed now (`docs/design/SKILL_PROGRESSION.md`) and built alongside each skill's own gameplay.

**In order:**
1. **The skill-to-attribute design for all 35 skills** (done: `docs/design/SKILL_PROGRESSION.md`). For each skill:
   - its primary attributes and its secondary or profession stats;
   - what each one does in play;
   - its milestones and missing dependencies;
   - what works today and what is planned.
2. **Close the live XP exploits**, each with a reproduction and a positive test:
   - magic XP for casts into empty space;
   - full kill XP from spawners, bred animals, renamed mobs, creative mode and player kills;
   - weapon XP credited to the item held at death;
   - Critical Strike farmed on one target;
   - defensive XP in idle damage farms.

   Also review a normal player's way into the Mage; a material design choice goes to the owner with options and a recommendation.

   **Done 2026-10-26** (`docs/STATUS.md` → "Done: XP integrity"; `tools/playtest/xp.js` 35/35, every reproduction failing on a control build). The Mage route: Spell Weaving 5, decided 2026-10-27 (`docs/DECISIONS.md`, decision 4) and built.
3. **Level-up and rank-up feedback:**
   - a brief sound and a small particle effect;
   - which skill rose, its new level and its real stat gain;
   - something stronger at milestones;
   - a distinct class rank-up that names what it unlocks (abilities with other requirements stay locked);
   - simultaneous gains combined;
   - no replay on login, data loading or admin corrections;
   - a player setting.

   Vanilla sounds and particles only, local, bounded and Folia-safe. Built so realm breakthroughs, mastery trials and Awakenings can use it later; those systems aren't built now.

   **Done 2026-10-26** (`docs/design/LEVEL_UP.md`; `docs/STATUS.md` → "Done: level-up and rank-up presentation"; `tools/playtest/levelup.js` 19/19 and `requires` 6/6).
4. **Woodcutting, a complete and deep loop:**
   - **The loop:** find a natural tree, cut it with the right tool, earn XP, see the skill's effect, get better or specialized tools, unlock harvesting capabilities at milestones, use the resources elsewhere, move on to harder and more rewarding trees.
   - **Trees and techniques:** different trees matter. Basic trees stay easy, and at least one higher-level technique visibly changes how Woodcutting plays.
   - **Protection:** natural and placed wood are told apart, and placed logs or artificial regrowth can't be farmed. Large effects respect protected areas, builds, chunk and region boundaries and player intent.
   - **Data:** everything is data-driven, proven by a synthetic custom tree. No forest generator.

   **Done 2026-10-26** (`docs/design/WOODCUTTING.md`; `docs/STATUS.md` → "Done: Woodcutting"; `tools/playtest/woodcutting.js`): eleven woods, Felling I–IV and the Canopy, heartwood grip and limbs, discoveries, Fortune scoped to wood, chopping speed, and protection for builds, claims and Folia regions.

   **Owner's playtest feedback, done 2026-10-26** (`docs/STATUS.md` → "Done: grown trees and Smithing's growth"): growing and harvesting trees is a legitimate way to train Woodcutting (a tree left to grow pays 80%, bone meal 50% and no heartwood, placed logs nothing; the owner's decision replaces "artificial regrowth can't be farmed" above); and Smithing grows Fortitude and wear resistance through the attribute engine, its forged-quality odds unchanged.
5. **Independent review and finish.**
6. **Report, and propose the next loop:** Farming or Fishing, one complete loop at a time.

**Also in this milestone:** the **ghost mob labels** from the owner's playtest (labels left floating after horses died). Fixed first (`docs/STATUS.md`).

**Rules:**
- **Performance first:** measure the affected systems before and after, with realistic multi-player harvesting and simultaneous level-ups. Particles and sounds must not become a new cost.
- **Protect** the owner's world, characters and database.
- **Preserve:**
  - Mining → Smithing → Equipment;
  - the XP display's modes;
  - the six primary attributes and the secondary stats;
  - combat and the 75% cap;
  - Warrior, Mage and Duelist;
  - both control schemes (the owner plays R-R-R combos);
  - saved class progress and loadouts;
  - the config migration and backup rules.
- **No automatic merge** into `master`.
- **Stays on the roadmap, not built early:** classes, mastery, Awakening, world generation.

**Later (owner's direction):** Farming and Fishing next, then every remaining profession, each as deep as Mining and Woodcutting. The long-term aim is that depth across combat, mining, woodcutting, fishing, farming, hunting, crafting and the other disciplines: 35 disciplines worth mastering, not 35 XP bars.

### The next loop: proposal (2026-10-26; decided 2026-10-27: Fishing, with the owner's changes)

**Recommendation: Fishing first, then Farming together with Cooking.**
- **Fishing has a live problem today.** Every fish caught has a flat 5% chance to spawn a Rare Sea Monster on the player, with no level, no cap and no idle check (`CerberusPlugin.onPlayerFish`, `docs/SKILL_AUDIT.md`). An AFK fish farm keeps spawning them, and they pay full kill XP. Building Fishing properly fixes that.
- **Fishing's rewards connect to what exists:**
  - sea creatures are fights, through the mob registry, the level scaling and the classes;
  - catches fill a Bestiary-style journal;
  - Fortune already belongs to Fishing;
  - rods take upgrades through the data the heartwood grip uses.
- **Farming's rewards mostly feed Cooking and Alchemy, which aren't built.** Its "resources that feed other systems" step would be thin until they exist. Farming also needs a new integrity rule: every crop is planted by a player, and bone meal is cheap. So it fits best together with Cooking.

**Fishing, the loop** (the same shape as Woodcutting; principles studied, nothing copied):
1. **Cast in open water** (vanilla's own open-water test), and catch by water and place: river, ocean, warm and cold seas, swamps, later lava in the Nether. Time and weather shift the odds.
2. **XP per catch, by rarity.** A spot fished out pays less and recovers over time; AFK farms, creative mode and closed water pay nothing and call no creatures.
3. **What a level gives:** Fortune for fish (an extra catch), lure speed with a rod in hand, a treasure chance, and a capped sea-creature chance.
4. **Milestones:**
   - 10: faster bites;
   - 25: the treasure table;
   - 50: sea creatures by level, fought with your class abilities, rewarding drops;
   - 75: double catches;
   - 100: an apex creature per water.
5. **Discoveries:** the first catch of each kind, in a journal.
6. **Equipment:** rare materials from creatures upgrade rods (lure speed, treasure) through the same `upgrades:` data.
7. **Data:** catches, waters and creatures in config, proven by a synthetic catch and creature; a fishing load in `load.js`, measured before and after.

**The owner's answers (2026-10-27, `docs/DECISIONS.md` 1–3):**
1. Fishing first; Farming next, ideally together with Cooking.
2. Real, level-scaled sea-creature fights, earned as banked encounters that any legitimate catch can earn and the player starts when ready.
3. AFK fishing is allowed with full normal XP and catch rewards; no inactivity timers. This replaces items 2–3 of the loop above ("AFK farms … pay nothing and call no creatures").

## Gameplay Depth & Systems Completion (owner, 2026-10-25, approved): done, 2026-10-25

**Why.** The menus, combat engine, attributes and first three classes are in good shape, but much of the game is still skeletal. Many of the 35 skills have little or no gameplay: no XP source, unfinished effects, or effects that depend on systems not built yet. So depth and complete gameplay loops come before more classes. The six next classes and the 25-class vision stay on the roadmap; they wait.

**In order:**
1. **Class milestone wrap-up:**
   - Both control schemes stay (above). Done.
   - The trapped-mob class-XP loophole is closed: a mob pays cast XP only up to what its kill pays. Done.
   - Movement comes back in 1.2 s at most (the owner's rule, below). Done.
   - The passive-mob health-bar inconsistency is fixed as a focused issue. Done: rideable mobs were unlabelled on purpose (the label took their only seat); their label now steps aside for a rider.
2. **A code-grounded audit of all 35 skills** (`docs/SKILL_AUDIT.md`). Done: 4 functional, 10 partial, 11 disconnected, 10 placeholder. For each skill it records:
   - XP sources, balance and exploits;
   - effects, reachable or not;
   - the attribute system;
   - progression rewards;
   - dependencies;
   - what is missing;
   - future interactions;
   - what can be done now and what must wait.

   Each skill is classed as functional, partial, disconnected, placeholder or design-only, with the highest-value missing loops named.
3. **The first complete loop: Mining → Smithing → Equipment** (`docs/design/GATHERING.md`). Done, verified in game (`docs/STATUS.md`). Find resources, mine them, earn Mining XP, process them, earn Smithing XP, make useful equipment, and use it in combat. (Crafting XP stays for later: forging pays Smithing, and plain crafting has no loop yet.)
   - Vanilla resources first, on data-driven definitions with stable ids: resources, loot and XP, tool capabilities, extraction methods (manual or automated), and Fortune and rarity.
   - A future custom ore, tool, drill or machine plugs in without a rewrite, proven by a synthetic custom resource and tool.
   - Equipment feeds the existing attribute engine; there is no second item-stat engine.
4. **Real-time XP display** (owner request, small): players see their progression without a cluttered screen. Done (`/xpdisplay`; `docs/STATUS.md`).
   - XP gains show briefly on the existing HUD: combat, exploration and new chunks, gathering, crafting and the other skills.
   - Repeated gains merge into one clean notification ("+86 Mining XP" over a few seconds), never dozens of lines.
   - Combat information and ability feedback always come first.
   - A per-player setting: Compact, Detailed or Off.
   - Lightweight and Folia-safe, built on the existing progression and HUD. Tested with several XP sources and measured. A polish item, not a UI overhaul; it lands as the new gathering and crafting XP arrives.
5. **Close the live XP exploits the audit found** (small):
   - magic skill XP on hits, not casts;
   - kill XP credited to the weapon that landed the last hit, with no full XP from spawners, bred animals, creative mode, renamed mobs or player kills;
   - Critical Strike XP capped per mob;
   - the defensive skills' XP by the share of health taken, with an idle rule.
6. **The same pattern next** for Woodcutting, Farming, Fishing, Alchemy and the rest, in the order the audit recommends (`docs/SKILL_AUDIT.md`, "Highest-value missing loops").

**Rules for this milestone:**
- **Movement (owner rule, 2026-10-25, standing):** every movement ability, a dash or a blink, comes back in 1.2 s at most, and so does the lockout they share. Teleport-style movement may be as quick as the gap between casts (Blink 0.4 s). Enforced when `abilities.yml` loads (`ClassCatalog.MOVEMENT_MAX_COOLDOWN_MS`). A movement ability's strike or stun may keep a slower pace of its own (`strike-cooldown`). The rule holds for every class to come.
- **Performance first:** measure before and after, with realistic gathering and crafting loads.
- **Exploit protection:** placed blocks, craft-and-dismantle loops, Fortune duplicating rewards, creative mode, automation, invalid definitions and item duplication.
- **Balance:** proportional rewards that fit controlled hyperscaling, with no runaway multipliers.
- **Folia correctness and small commits.**
- **Verification:** in game in the sandbox, with positive and negative controls and an independent review. The owner's Windows world and database are never touched.

**Not in this milestone:**
- the next six classes, or all 25;
- class Awakening;
- level-150 mastery;
- all future ores and machines;
- a new world generator;
- every weapon archetype;
- a full economy, quests or NPC civilization.

The discussions about Awakening, ability requirements, long-lasting effects and upgrades are design input, not tasks.

**Playtest backlog** (to investigate, not assumed to be code defects):
- ~~**Passive mob health bars:** some passive animals (pigs, horses) showed no Cerberus health bar.~~ Fixed: rideable mobs were left unlabelled because the label took their seat; it now steps aside for a rider (`docs/STATUS.md`).
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
| Speed and handling bonuses in PvP (attack, movement, bow draw, spell cooldown, gathering and chopping speed, Smithing's wear resistance, Fishing's lure speed, treasure and sea-creature chances, including the fishing setup's parts and bait, and a meal's speed stats: Trail Ration, Angler's Platter, Miner's Pasty) and the faster-drawn arrow's extra damage (decision A); while paused, a stat bait (Grub Bait, Chum) stays in the bag instead of being spent for nothing (`Gathering/Tackle.onCast`) | `attributes.yml` → `secondary.in-pvp: false` (tag length `pvp-tag-seconds: 15`) | Set `true` |
| A meal's restoration while PvP-tagged: it pauses (the rest comes after the fight), so meals can't heal mid-duel | Code: `Gathering/Cooking.restore` (the PvP tag) | A PvP healing rule, with lifesteal (decision 6) |
| Attributes on offense in PvP (Power, crits, lifesteal): PvP hits still use the old per-skill bonuses, which count an untrained skill (level 1 since 2026-10-27, 0 before) as untrained, as before; so does the PvP defense baseline | Code: `Manager/PveOffense` runs only on player → mob hits; PvP goes through `Manager/CombatSkillProcessor` (`pvpLevel`; the baseline in `PlayerDefenseManager.updateDefense`) | A PvP offense path with PvP coefficients |
| Abilities hitting players: every ability targets mobs only (Cleave, Fireball...), never another player's pet or a mount a player rides; Riposte only counters mobs that belong to no player; Feint's crit only on mob hits; a left-click on a player is a plain hit | Code: `Classes/AbilityCaster.valid` and `Manager/AbilityTargets`, the counter hook in `Listener/VirtualHealthListener`, `Manager/PveOffense.meleeHit`, `Spells/FireballSpell.castFromAbility`, `Classes/AbilityListener.onAttack` | `docs/design/CLASSES.md` §10 decision 5 (PvP coefficients), then one switch |
| Class XP from fights with players | Code: `Listener/MobKillListener` gives no class share for player kills; casts never reach players; a self cast in a fight with no enemy in it (a PvP tag) earns nothing (`Classes/ClassService.fightFoeLevel`) | Together with abilities in PvP, with rules against farming each other |
| Sea creatures and players: a called creature is hurt only by its owner (their arrows, pets and spells) and hurts only its owner; its drops are its owner's | Code: `Gathering/Fishing.guard` (through `VirtualHealthListener.setDamageGuard`) and `onTarget` | A rule for shared or contested fights (parties, PvP zones) |
| Iron Reprisal on players: a player (or an animal a player owns) who hits you in melee is not thrown back (2026-10-28) | `combat.yml` → `skills.heavy-armor-training.iron-reprisal.pvp: false` | Set `true` (it already works: the knockback goes to the attacking player) |
| Shields against players: behind a raised shield a player's blow (or their arrow, pet, TNT) still comes off the Cerberus health pool, as it always has; creatures' blows stopped doing so on 2026-10-28 (`docs/design/COMBAT_IMPROVEMENTS.md` → Shields) | Code: `Listener/VirtualHealthListener` (the shield check leaves hits caused by players to the pipeline) | Drop the player exception, so a shield blocks a player's blow as it blocks a creature's |
| Skill XP from fights with players: a player kill's weapon-skill XP, and Survival and Heavy Armor Mastery for a hit taken from a player (or their arrow, pet, potion cloud, TNT) | `attributes.yml` → `xp-integrity.pvp-xp: false` (`Manager/XpIntegrity`) | Set `true` (the idle rule still applies), with rules against two players farming each other |

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
