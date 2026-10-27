# Cerberus — Owner Decisions

*The register of the owner's decisions, with the state of each. **Check it before asking the owner anything:** a question answered here is not asked again. New decisions are added when the owner makes them. Last updated 2026-10-27.*

**States:**
- **Approved, built:** decided and in the game.
- **Approved, building:** decided; built in the current milestone (`docs/ROADMAP.md`).
- **Approved, no change:** decided to keep what is there.
- **Deferred:** decided in principle; built in the milestone named.
- **Follow-up design:** the owner wants a proposal first; nothing changes until they approve it.

`docs/design/OPEN_DECISIONS.md` is the older design checklist from June 2026; where it disagrees with this file, this file wins.

## The guiding philosophy: respect the player's time (owner, 2026-10-27, permanent)

Cerberus is a deep, challenging and rewarding RPG. It does not copy Hypixel SkyBlock's strict approach to AFK farming, macros and repetitive progression.

- **Players are not punished for finding a more efficient way to progress.**
- **Automation is acceptable. Exploiting bugs, duplicating items, destroying the economy or harming server performance is not.**
- **Allowed:**
  - AFK farming;
  - macros that automate ordinary gameplay actions;
  - clever farms, rewarded for their engineering.
- **Normal rewards don't disappear** just because a player isn't pressing buttons. Progress comes from legitimate activity in the game, not from whether the mouse keeps moving.
- **Not built:** aggressive anti-AFK or anti-macro detection, inactivity timers, or movement and interaction challenges.
- **Automation still obeys:**
  - the game's real mechanics;
  - resource costs;
  - progression requirements;
  - the server's performance limits.
- **Still protected against:**
  - duplication bugs;
  - fabricated reward events;
  - crashes;
  - uncontrolled entity spawning.
- **Active play is rewarded with more, not by making passive play worthless:** interesting challenges, discoveries, special encounters and extra opportunities. *Make active play exciting, not mandatory for every repetitive action.*
- **Scope:** Fishing first, then every discipline: Mining, Farming, Woodcutting, combat training, crafting and the rest.
- **Existing rules:** the anti-farming rules already built are reviewed against this, not removed wholesale. The ones that conflict go to the owner with replacements (`docs/AUTOMATION_REVIEW.md`).

**What this means for code** (how the rule is applied, 2026-10-27):
- A reward pays for what really happened in the world, whether a player, a farm or a macro did it.
- A rule may withhold or reduce a reward only for one of these reasons:
  - **nothing really happened:** a block placed and broken again, a creative-mode action, an admin tool, a fabricated or cancelled event;
  - **a loop makes value from nothing:** duplication, refunds that pay twice, a reward that can be re-triggered without the work;
  - **server protection:** entity limits, bounded work per tick;
  - **pacing that applies equally to active and passive play:** for example a mob's XP budget.
- "The player wasn't pressing keys" is never a reason.

## Fishing Equipment, Guided Discovery and Collections (owner, 2026-10-27)

**Approved, building** on the development branch in three increments (design and every number: `docs/design/FISHING_EQUIPMENT.md`). The goal is useful goals, meaningful equipment choices and fewer chores, not more menus.

- **The loop:** find a missing species → track it → understand its requirements → choose a setup → fish → discover or beat a record → unlock another option. Treasure and sea-creature encounters are alternative goals in the same skill: no new professions or XP bars. Manual, AFK and macro fishing keep their full normal rewards.
- **Words:** *Lure* = reusable equipment; *Bait* = optional consumable; *Encounters* = banked sea-creature summons (the banked-lure tile becomes "Encounters: N"; saves and commands stay compatible).
- **A, navigation and guidance:**
  - skill-page section tiles (Fishing: Equipment, Journal, Encounters; Woodcutting: Woods; others only with real functionality);
  - an active Collections index sharing each collection's data and screen with its skill page;
  - the graphical journal with All / Undiscovered / Available here (the real eligibility rules);
  - one tracked species with each requirement checked (bobber location, or a labelled preview);
  - a bounded "Since your last visit" summary with real awarded XP, cleared only when acknowledged.
- **B, equipment and convenience:**
  - **Setup categories:** Rod 1, Line 10, Lure 25, Reel 50, each an assembly so the 11 old upgrades keep stacking;
  - **any normal rod uses the setup**, through the one attribute engine;
  - **effective previews** after caps and diminishing returns;
  - **three renameable presets** (Discoveries, Treasure, Sea Creatures), switching for the next cast, a snapshot per cast;
  - **old upgraded rods keep working**, convert voluntarily, nothing counted or returned twice, unknown parts kept;
  - **a Bait Bag** of 1 to 3 slots: one bait active, situational bait never wasted, one per completed catch, opt-in restocking from the inventory, "Bait depleted. Normal fishing continues.";
  - **the Auto-hook Reel at Fishing 50:** reels a real bite through the game's own catch, never recasts, never spends an encounter, on/off.
- **C, fishing goals:**
  - **Surveyor's Float** (undiscovered eligible species favoured, gates and rarity order kept) and **Specimen Hook** (heavier within each species' range), one specialist at a time;
  - a small collection-reward table, granted once;
  - a Master Angler emblem for full completion;
  - optional "Keep as trophy" for a recorded catch.
- **Not authorized here:** Farming, Cooking, cultivation, Fluency, PvP or class expansion.
- **Routine implementation choices and small tuning values are the developer's to make** and are documented in the design file, not asked again.

## Decisions of 2026-10-27 (the 23 open decisions, all settled)

| # | Decision | The owner's answer | State |
|---|---|---|---|
| 1 | Next gathering skill | **Fishing first.** Farming next, ideally together with Cooking. | Approved. Fishing built 2026-10-27 (`docs/design/FISHING.md`); Farming with Cooking next |
| 2 | Sea creatures | **Real fights with levelled sea creatures**, using mob levels, the combat engine, classes, abilities and rewards; encounters grow more exciting with Fishing. No AFK farm continuously spawning hostile mobs. The owner's preferred form, **banked encounters**: any legitimate catch can earn a stored encounter (a special lure, a summon token); the player starts it when ready; it is a real, level-scaled fight with rewards. Manual and automated catches earn them equally. Stored encounters persist safely, never duplicated or lost; spawned creatures have sensible entity limits. Refinements are welcome if automated fishing can still earn the same rare encounters. | Approved, built 2026-10-27: lures banked on the player (any legitimate catch, manual or automated), called with Sneak + cast, a levelled fight only the owner can hurt or loot; spent once, given back once; one per player, 32 on the server; never saved into the world |
| 3 | AFK fishing | **Allowed, with full normal XP and catch rewards.** Not zero, not a reduced fraction. No inactivity timers, no movement or interaction challenges. Compact fishing setups are not invalidated; the environment may change which catches come and how rare they are. Protect server resources and prevent broken reward loops without treating automation as cheating. | Approved, built 2026-10-27: every legitimate catch pays the same, no idle rule or timer for fishing; treasure needs open water as in vanilla |
| 4 | A way into the Mage | **A: Spell Weaving 5 opens the Mage**, for normal players without admin items. **C** (a craftable beginner focus) is liked for a later equipment milestone. | A: approved, built 2026-10-27 (`classes.yml`; an edited file's old default follows). C: deferred (magic equipment) |
| 5 | Merging into `master` | **Policy kept:** after the owner's playtest and an independent verification, a reviewed pull request is prepared. Never merged automatically. | Approved, standing |
| 6 | Lifesteal 2.7% from Weapon Mastery level 1 | **Keep for now.** Reconsider once healing exists (Cooking, Alchemy); early survival must not become frustrating. | Approved, no change (revisit with healing) |
| 7 | Blink can chain | **Keep Blink fast.** If endless chaining becomes a problem, look at Essence cost or regeneration separately; never slow movement. | Approved, no change |
| 8 | Execute needs Momentum | **Keep.** | Approved, built |
| 9 | Forged-quality bonuses are flat | **Keep flat for now**; revisit in the equipment-balancing milestone. | Deferred (equipment balancing) |
| 10 | Mob arrows and fireballs ignore mob level | **Scale ranged attacks with mob level and tier like melee**, with sensible caps, tested against players of different levels. High-level ranged mobs are dangerous, not unfair or impossible. | Approved, built 2026-10-27 (`combat.yml mob-damage-scaling.ranged`: melee's multiplier up to ×6, one shot at most 35% of max health, never below vanilla) |
| 11 | Environmental damage is vanilla-sized | **Hazards should stay meaningful**, and scaling them with the player's health is supported, but **first a few representative calculations and a safe implementation plan**. High-level players are more resilient, never immune. | Follow-up design (`docs/design/ENVIRONMENTAL_DAMAGE.md`) |
| 12 | One huge hit drains more than the defense bar holds | **Correct it when bosses with massive attacks arrive:** the bar never absorbs more than its capacity. No urgent change while ordinary hits are unaffected. | Deferred (first bosses) |
| 13 | Weapon Mastery's double role | **Keep it.** Investigate splitting it in the weapons milestone, without invalidating anyone's progress. | Deferred (weapons milestone) |
| 14 | Instant-mining stone XP | **Keep.** Legitimate efficiency is not nerfed. | Approved, no change |
| 15 | Abilities and Techniques | **One unified system**, with Manuals as an unlock source. | Approved |
| 16 | How abilities unlock | **Class ranks stay the main progression**, with no mandatory points or respec. Some advanced abilities may also need a class rank, a skill level, a boss or special enemy defeated, a quest or challenge, an NPC, item or training manual, or a location or mastery trial. Requirements are visible to the player. **A locked ability never stops the class from gaining ranks.** Not all these systems are built now; the architecture keeps room for them. | Approved. Built so far: rank and skill-level requirements (`abilities.yml requires:`). The rest arrive with their systems |
| 17 | Slots | 5 active, 3 passive, 1 keystone, 3 loadouts, with more at higher ranks as proposed. | Approved, built |
| 18 | Class entry | Classes open from skill progression; players may develop several classes. | Approved, built |
| 19 | Roster | Keep the full roster of about 25 classes; don't rush more classes. | Approved |
| 20 | Weapon names | The naming direction is approved; **Furnace Hammer** and **Runeblade** stay on the roster. | Approved (applied when weapons are built) |
| 21 | Folia version | **Plan the upgrade to a supported Folia** as its own compatibility milestone: backups, plugin testing, a migration plan. **The owner's world and server are never upgraded automatically.** | Approved (planning; a separate milestone) |
| 22 | Labels on all mobs | **Keep labels on all mobs.** If they cost too much, optimize them (visibility distance, lifecycle, efficient updates) rather than removing peaceful-mob labels. | Approved, no change |
| 23 | `lib/DTR-1.0.0.jar`, `libs/Volor.jar` | **Delete them from the development branch** after checking nothing depends on them; the build and tests must still work; keep anything an existing feature needs. | Approved, done 2026-10-27 (nothing referenced them; build and 255 tests pass) |

**Controls (confirmed again):** both schemes stay, Quick Keys and Click Combos; the owner plays the R-R-R combos.

**Feedback:** Woodcutting's Felling is "fantastic", the feeling every discipline should reach. That is about Felling's feel, not an approval of every item on its checklist.

**Design investigation (not an approval):** an XP-efficiency bonus, inspired by the idea behind SkyBlock's Skill Wisdom but adapted: a higher level in a skill earns more XP in that same skill, bounded (about +40% at level 100 as a first candidate), studied against the XP curve, the time to each milestone and the weak-resource rule. It must not delay Fishing and is not a rebalance of all 35 skills (`docs/design/XP_EFFICIENCY.md`).

**Still for the owner:** major new architectural choices, destructive changes and substantial new resource costs. Routine development and testing proceed under the decisions above.

## Earlier decisions (still in force)

**2026-10-25:**
- **Merging:** a reviewed pull request into `master` after the owner's playtest; never automatic.
- **PvP is off for now, not permanently.** Every PvP limit is temporary, switchable and listed in `docs/ROADMAP.md` → PvP, until a PvP pass turns them all on.
- **Damage reduction:** capped at 75%, applied to resistance × armor (built).
- **Weapon Mastery's all-weapon bonus** stays (analysed; see decision 13).
- **A–E** (speed in PvP, powered shelves, Survival's fire resistance, projectile XP, spears): built.
- **Both control schemes stay, permanently,** each player's choice; the server default stays Quick Keys until broader feedback.
- **Movement abilities come back in 1.2 s at most** (standing rule).
- **Real-time XP display:** built.
- **Gathering speed:** with an axe in hand, Woodcutting's chopping speed; otherwise Mining's gathering speed (built).

**2026-10-26:**
- **Every skill improves at least one meaningful attribute or secondary stat** through the one attribute engine (permanent; `docs/design/SKILL_PROGRESSION.md`).
- **Idle damage farms:** Survival and Heavy Armor Mastery stop after a minute without input (built as recommended then; **under review** against the 2026-10-27 philosophy, `docs/AUTOMATION_REVIEW.md`).
- **Cast XP from a mob that never dies:** each mob pays cast XP only up to its kill XP (built).
- **Iron and gold farms:** gear forged from metal the player didn't smelt is plain (built; **under review**, `docs/AUTOMATION_REVIEW.md`).
- **Trees grown from saplings pay Woodcutting XP:** 80%, with bone meal 50% and no heartwood; placed logs pay nothing (built; the bone-meal share is **under review**).
- **Settings to tune after playtesting, not decisions:** XP per block and per log, the weak-resource quarter, Fortune, quality odds, Felling sizes, heartwood rarity (`resources.yml`, `attributes.yml`).
