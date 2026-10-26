# Cerberus — Project Status

*Last updated 2026-10-26 (branch `claude/quirky-mccarthy-nhaz58`). Update this file when a milestone lands.*

**Current milestone:** Steps 0–2 of `docs/ROADMAP.md` are done and verified in game on the cloud sandbox:
- **Step 0**, the owner's decisions: the 75% damage-reduction cap (resistance × armor; the defense bar is not counted), PvP playing as before Attributes 2.0, and the Weapon Mastery stacking analysis.
- **Step 1**, the performance assessment (`docs/PERFORMANCE.md`): Cerberus's server time dropped 66–69% for menu clicks, 17–37% in combat and 21–30% for hits taken. An independent review of that work found one real problem, a fast double-click in an admin screen could press a button on the next screen; it is fixed.
- **Step 2**, decisions A–E: speed bonuses stay off in PvP, powered shelves work again, Survival's fire resistance covers every kind of fire, projectiles train Survival and Heavy Armor Mastery, and spears train Weapon Mastery.
- **Step 3**, Player Menu 2.1: a short opening animation, a light sweeping the header between screens, page-turn sounds, the skill bar filling up, and "since you last looked" progress with one chime and one level-up flash. It stays within the performance budget, and an idle open menu costs nothing.

- **Step 4**, the classes and abilities design: `docs/design/CLASSES.md`.
- **Step 5**, the Warrior / Mage / Duelist prototype: learn several classes from your skills, unlock abilities by rank, mix them in three loadouts, cast with Sneak + a key. The six proofs were checked in game (91 checks), and an independent review's nine findings are fixed. Abilities are PvE-only for now; PvP gets turned on later (`docs/ROADMAP.md` → PvP).

The owner has playtested Player Menu 2.0 and, on 2026-10-25, the class prototype. That playtest found control problems the automated checks missed: Sneak + left-click was unclear, Sneak + F did the off-hand swap, and the Mage's focus abilities were unreliable.
- **Phases 1–3 are built and checked on the sandbox** (section "Done: ability controls…" below):
  - new controls (quick keys by default, click combos as an option) with feedback for every press;
  - every one of the 21 abilities audited on its own;
  - class XP only from real fights; Execute as a real finisher.
- **The owner has tried the new controls** and set the next milestone, **Gameplay Depth & Systems Completion** (`docs/ROADMAP.md`). The class milestone's loose ends are closed first (section "Done: class milestone wrap-up" below): both control schemes stay, a mob kept alive can't pay cast XP forever, and movement abilities come back in 1.2 s at most.
- **Done since:** the 35-skill audit, then the first gathering-to-equipment loop, **Mining → Smithing → Equipment**, with the real-time XP display (section "Done: Mining → Smithing → Equipment" below).
- **You playtested mining and forging (2026-10-26)** and set the next milestone, **Progression Depth & Woodcutting** (`docs/ROADMAP.md`):
  - a skill-to-attribute design for all 35 skills;
  - closing the live XP exploits;
  - level-up and rank-up feedback;
  - a deep Woodcutting loop.
- **Done first:** the **ghost mob labels** you found (section "Done: ghost mob labels" below).

**Evidence key.** **Played** = exercised on a real Folia 1.21.11 server with a scripted test client (cloud sandbox, not your PC). **Tested** = unit tests. **Read** = code inspection only. A green build alone proves nothing about gameplay.

## Summary

The foundation runs. The plugin loads cleanly on Folia 1.21.11 under Java 21 and 25, saves and reloads player progress, and survives restarts. The playable loop today is: fight mobs → earn skill XP (more from higher-level mobs) → level up → **get stronger**: skills feed six attributes that combat reads, so Blade Mastery 100 kills a level-50 zombie in about 5 swings instead of 20, defensive skills triple max health, magic skills triple mana and scale spells (Played). The Stats page shows all of it. Most of the designed game (realms, weapons, gear, talismans, mounts, families, crafting) exists only in `docs/design/`.

## What works (Played)

| Area | What you can see |
|---|---|
| Startup / shutdown | Loads in ~30 s; 6 SQLite tables; `mobs.yml` (7 entries), 35 skills, `combat.yml` v3, `classes.yml` + `abilities.yml` (3 classes, 21 abilities); `stop` saves and exits cleanly. |
| Player health | 100-HP custom pool, 300-point defense bar, 1000 mana, action-bar HUD. Fall, lava, mob, and command damage all route through it. 250 ms hit invulnerability. |
| Death | Lethal hit → instant respawn at full HP (no death screen, by design). |
| Mob scaling | Every mob gets an HP pool (×8 per realm tier, +1 level per 150 blocks from spawn) and a live floating label (`Zombie 50❤ Lv1`). Kills are guaranteed when the pool empties. |
| Combat XP | Kill XP goes to the weapon's skill (sword → Blade Mastery, axe or spear → Weapon Mastery, fist → Martial Expertise; `skills.yml`). Critical Strike XP per hit; Survival and Heavy Armor Mastery XP when hit, by melee or by a mob's or player's projectile. Level-up messages. |
| Persistence | Skill levels and XP survive restarts; saves coalesce within 1 s; a failed database load disconnects the player instead of overwriting data. |
| Player Menu | Head in slot 9 or `/menu`. Hub with the character at a glance and every destination marked *Available* or *In development*. Skills → category → skill pages (training sources, attribute and speed gains, effects with status, next milestones); Stats & Attributes; Ascension (realm ladder); Equipment; Bestiary; World & Exploration; Guide. Verified with `tools/playtest/menu.js` (nav 63 as op / 61 as non-op, safety 18 with controls, perf 2: no database access and no snapshot rebuilds while browsing). Four skills shown under new names (`NAMING.md`). |
| Admin Control Panel | `/cerb panel`: Players (inspect/edit), Combat Lab (spawn at level, mob inspector, cleanup of your own test mobs), Progression Lab, Attributes (inspector, simulator, dev buffs), My Test Character, Server & Plugin, Maintenance. Permission-checked on every click; verified with `tools/playtest/admin.js` (main 40, attrs 14, owners 5, cross 5, security 6). An independent review found no security or item-duplication hole; its correctness findings are fixed. |
| Attributes | Power (per weapon type), Agility (crits), Vitality (max health), Fortitude (armor, defense bar, resistances), Essence (mana, regen, spell damage), Fortune (the extra-ore chance), plus speed stats: attack speed (per weapon type), bow draw speed, movement speed, spell cooldown recovery, gathering speed, with diminishing returns and caps; paused during PvP by default. Derived from skills, capped in one place, cached per player, `attributes.yml` with kill switches. Stats page (`/stats` or Player Menu → Stats & Attributes). Verified with `tools/playtest/progression.js` (incl. `speed`, 13 measurements) and the unit tests. |
| Defense | Resistance → armor → defense bar; resistance and armor together capped at 75%, the bar a separate pool; PvP hits use the pre-attribute defense as the same share of health. Fire resistance covers flames, burning, campfires, magma and fire spells fully and lava at half (`combat.yml fire-resistance`). Checked by `progression.js defense`, `defense-cap`, `pvp` and `fire`; details in `docs/ATTRIBUTES_ENGINE.md`. |
| Kill XP v2 | Based on mob level and tier: Common L1 40, L50 334, Rare L50 668; ×0.25 for mobs 20+ levels below your skill. |
| Gathering | Mining XP for natural stone and ore broken by hand (stone 1 … ancient debris 100; a quarter 20+ levels above a resource). Mining raises gathering speed and Fortune, a chance of one extra ore. Placed, generator-made and piston-moved blocks, creative mode, explosions and machines pay nothing. Everything is data in `resources.yml`, custom ores, tools and drops included. Verified with `tools/playtest/gathering.js`. |
| Smithing | XP for taking metal out of a furnace, forging tools, weapons and armor, and the netherite upgrade; paid once per item really made. Forged gear rolls a quality (Common, Fine, Superior, Masterwork; better with Smithing) that adds Power, Fortitude or Fortune through the attribute engine. |
| XP display | Every XP gain shows briefly on the HUD, merged per skill ("+86 Mining"), below ability messages. `/xpdisplay compact|detailed|off`, saved per player. Kill XP no longer goes to chat; level-ups still do. |
| Classes (prototype) | Warrior, Mage and Duelist: opened by skills, ranks 1–50, 7 abilities each unlocking by rank, 3 loadouts (5 actives, 3 passives, keystone) mixing any classes, cast with quick keys (right-click / F / Q, Sneak + right-click / Sneak + F) or click combos, chosen per player, with a HUD message and sound for every press and an ability bar. Player Menu → Classes & Abilities, `/class`, `/cerb class <player> <class> rank <n>`. Abilities affect mobs only. Verified with `tools/playtest/classes.js` and `abilities.js` (every ability on its own, `docs/ABILITY_AUDIT.md`). |
| Test content | `/givetestitem kit`; Fireball Wand (20 mana, regenerates); `/cerb spawn <mobId>`; `/spawncustomzombie` (High Health Zombie, BOSS pool); `/cerb skill <player> <skill> level\|xp <n>`; `/testregen`; `/cerb healthstate`, `/cerb combatdebug`. |

## Incomplete (Read unless noted)

| Area | State |
|---|---|
| **Skill effects** | Through attributes (vs mobs): weapon skills, crit skills, defensive skills and magic skills now matter, and weapon/bow/movement/spell skills also make the action faster (Played). Mining (gathering speed, Fortune) and Smithing (forged quality) matter too. PvP still uses the old per-skill bonuses (a design decision for later). The other gathering and crafting skills feed nothing yet. Each skill page lists what is live, PvP-only or not active. Most old per-skill `applyEffect` code is still never called. |
| **XP sources** | Combat, spells, damage taken, new-chunk discovery, mining, smelting and forging grant XP. Woodcutting, Farming, Fishing, Crafting, Alchemy, Enchanting, Herbalism, Cooking and 13 more have **no XP source**. |
| Realms | Every player is Awakened; realm advancement (and its attribute bonuses) is not built. |
| Old stat managers | Strength / Luck / Magic Find / Yield managers exist but nothing reachable calls them (Fortune will replace Luck / Magic Find). |
| Crafting & traps | About 2,860 lines of quality-crafting, item-buff and trap code, unreachable and unsafe if wired (duplication, unbounded multipliers, "[Broken]" items): `docs/SKILL_AUDIT.md`. Replaced, not wired, by the gathering loop; still in the code, for a later clean-up. |
| Spells | Fireball only. It damages other players caught in the blast (PvP rules are not designed yet); it now spares the caster's own pets. |
| Elements / knockback | Elemental bonuses are computed but never applied; the `knockback:` config is never read. |
| Defensive Magic | Disabled (needed ProtocolLib, which Folia can't use). |
| Admin commands | `/cerb mana`, `resetdata`, `worldgen`, `repairchunk`, `quest`, `event`, `timings`, `profiler`, `listentities` are stubs (`showqueues` now prints runtime counters). `config.yml` is never loaded. |
| Realm names | The Player Menu shows the realm ladder (Mortal…Primordial); mob labels still show tier names (Common, Rare…). |

## Known defects still open

| Defect | Impact |
|---|---|
| Labels on *every* mob (`apply-to: ALL`): each fish, bat and villager gets an extra entity | Performance on busy servers; mob pools are also kept for a whole session after chunk unload |
| Void kills of high-tier mobs take minutes (void damage drains the pool slowly) | Cosmetic / odd |
| SQLite opens a connection per call, no WAL (suspected) | "database is locked" if several players save at once for > 3 s |
| Same-day session logs overwrite each other | Debugging only |

## Fixed and verified on 2026-10-24

Fresh-clone build (dead DTR/Volor deps) and any JDK ≥ 21 · launcher scripts with prerequisite checks and backups · **kill XP of ~500,000,000 per kill** (wrapped negative after ~5 kills) plus the level cap and load-time repair of corrupted values · **hit-particle burst that would hang clients** on `/kill` or big hits · **item in hotbar slot 9 deleted on every join** · labels blocking horse/pig/strider riding and taming · registry mobs (`/cerb spawn`, High Health Zombie, Sea Monster) spawning as common mobs · menu buttons that did nothing · free-XP Upgrade button (now admin-only) · spell XP on failed casts · XP saved before a player's data loaded · per-tick XP and "BLOCKED" text in fire/lava · per-hit log spam · dropped saves · empty-data overwrite after a failed load · several Folia thread-safety issues. Details: `git log 031a6b4..HEAD`. An independent review of the fixes found no regressions; its follow-ups are in. Regression check: `tools/playtest/smoke.js` (11/11; the pre-fix build fails the kill-XP, elite-spawn and horse checks).

## Done: "Progression that matters" (Attributes v1)

Built as `docs/ATTRIBUTES_ENGINE.md` increments 1–7 and verified on the sandbox server (Played):

| Check | Before | Now |
|---|---|---|
| Swings to kill a level-50 zombie (netherite sword) | ~20 at any Blade Mastery | 19.5 at BM 1, 7.5 at BM 50, 5 at BM 100 |
| Equal-level fight (BM 1 vs L1, BM 50 vs L50) | — | 7 vs 7.5 swings: progression keeps fights fair |
| Max health with Survival, Heavy Armor Training, First Aid at 100 | 100 | 300 (plus armor 30%, defense bar 846, resist 50%) |
| Max mana / regen with Intelligence, Mana Regeneration at 100 | 1000 / 50 per s | 3,020 / 150.8 per s; Fireball x5 vs mobs |
| Kill XP, Common L50 | 40 | 334 |

Still to do in this milestone (increment 8): the restart-persistence check on your real server, and a performance look under load.

## Done: Player Menu 2.0 (2026-10-25)

Built in three increments plus a reliability pass and an independent review (all Played):

| Check | Result |
|---|---|
| Head in slot 9 and `/menu` open the hub; every tile's state is honest | menu.js nav 63/63 (op), 61/61 (non-op) |
| No click type in the menu moves an item; outside it the head can't be dropped, swapped, number-keyed, shift-clicked into a chest, placed, framed, put on an armor stand or picked out with middle-click; powered shelves swap the rest of the hotbar but never the head (decision B, below); one head after rejoin and respawn | menu.js safety 18/18, with three controls proving the bot's actions reach the server; menu.js shelves 7/7 |
| Browsing every screen three times | 0 database connections, 0 attribute snapshot rebuilds |
| Old menus | Retired (SkillGUI, SkillDetailGUI, ComingSoonGUI, GuiBuilder, GuiIdentity, BorderAnimator, UnifiedGUIListener) |

## Done: speed stats, attribute expansion increment 1 (2026-10-25)

`docs/design/ATTRIBUTE_EXPANSION.md` (proposal + measurements). Measured on the sandbox server:

| Check | Level 1 | Level 100 |
|---|---|---|
| Netherite sword attacks per second (Blade Mastery) | 1.61 (0.62 s per full swing) | 1.88 (0.53 s) |
| Swinging every 0.55 s: vanilla damage per hit (of 8) | 7.1 | 8.0 |
| Bow released after 0.85 s (Ranged Precision) | power 0.81, no crit | full power, critical arrow |
| Movement speed (Pathfinding) | 0.100 | 0.109 (+9%) |
| Fireball cooldown while clicking constantly (Spell Weaving) | 1,037 ms | 810 ms |
| Hit by a player | bonuses pause, return 15 s later | |

## Done: owner decisions of 2026-10-25

Details and measurements in `docs/ATTRIBUTES_ENGINE.md`. All Played on the sandbox, comparing the previous build with the new one:

| Decision | Before | Now |
|---|---|---|
| **75% total damage reduction**, applied to resistance × armor only (the defense bar is a draining pool, not a reduction) | not enforced; a raised resist limit let 82% through the reduction layers | capped at 75% (`ReductionCap` trace step). Default play unchanged: today's maximum is 70% (fire hit 100 → 30 → bar 21 → 9 on both builds) |
| **PvP unchanged for now** | the defender's Vitality, Fortitude and realm counted in PvP: defensive skills 100 lost 1.5% of health per player hit | PvP hits use the pre-attribute defense as the same share: 6.0% per hit, exactly what a 100-health defender loses. This also covers a player's pets, potion clouds and TNT, burn and poison ticks during a PvP fight, and PvP lifesteal (same share). PvE unchanged. `attributes.integration.pvp-defense: false` |
| **Weapon Mastery kept** | — | Bounded: at most +25 Power on any weapon (125 total), at most 21.8% ahead of an equal-level mob, crits inside the ×6 budget (`WeaponMasteryStackingTest`). Level-100 zombie: sword BM 100 7.0–7.7 swings, + WM 100 6.7–7.0; axe WM 100 alone 4.3. Proposal for the imbalance (axes etc. reach 125 with one skill) is recorded, not built. |

## Done: performance assessment (Step 1, 2026-10-25)

`docs/PERFORMANCE.md`. Reproducible loads (`tools/playtest/load.js`, 9 bots in one region) profiled with Java Flight Recorder (`tools/perf/profile.sh`):

| Workload | Cerberus, ms per tick, before → after (two runs) |
|---|---|
| Menu browsing (~27 clicks/s) | 0.51 → 0.16–0.17 |
| Combat | 0.15 → 0.09–0.12 |
| Being hit | 0.19 → 0.14–0.15 |
| Idle | 0.04 → 0.02–0.03 |

Regression on the optimized build, all passed:
- menu nav 63, safety 18, perf 2;
- admin main 40, attrs 14;
- progression defense 7, PvP 12, survive 4, stats 14, speed 13, mastery 3, kill XP 4, magic 2;
- smoke 11.

Several harness checks were made robust to sandbox drift: a raised world spawn, a hotbar full of leftovers, and mobs left near spawn.

The biggest find: every menu click opened a new window, and vanilla then re-ran its advancement checks over the whole inventory (about 60% of a click). Screens now redraw in the open window. Also: cached menu items, a cheaper HUD, smarter mob labels, and damage numbers animated by the client. The server spends 6–7 ms of its 50 ms tick budget under these loads, of which Cerberus is a small part.

## Done: decisions A–E (Step 2, 2026-10-25)

All Played on the sandbox. For C, D and E the same checks ran on the previous build and on the new one:

| Decision | Before | Now | Check |
|---|---|---|---|
| **A. Speed bonuses stay off in PvP** | paused for 15 s after a player hit | unchanged (no code change): attack, movement, bow draw and spell cooldown bonuses all read zero while tagged | `progression.js speed` 13/13 |
| **B. Powered shelves work** | every powered-shelf swap refused while the hotbar held the head | the swap works; the head and the admin chest are lifted out for that tick and put back; the shelf items meant for their slots stay on the shelf. Three chained shelves: 7 slots swapped, nothing lost or duplicated (server view after rejoin; shelf contents counted after breaking them) | `menu.js shelves` 7/7, `safety` |
| **C. Fire resistance covers environmental fire** | standing in fire 50%; burning, campfire, magma, lava 0% | 50% for standing in fire, burning, a campfire and a magma block, 25% for lava (Survival etc. at 100; shares in `combat.yml fire-resistance`); a non-fire hit gets none; still under the 75% cap | `progression.js fire` 7/7 (3/7 on the old build) |
| **D. Projectiles train defense** | a skeleton's arrows gave no XP | +1 Survival per hit, Heavy Armor Mastery XP from the damage taken. Nothing from a dispenser, your own harming potion, a second arrow inside the invulnerability window, or a hit that does no damage | `progression.js projectile` 7/7 (3/7 on the old build) |
| **E. Spears train Weapon Mastery** | a spear kill paid Heavy Armor Mastery XP (fallback), while spear hits already used Weapon Mastery's Power | a spear kill pays Weapon Mastery XP, once; the same classification gives spears ×7.25 at Weapon Mastery 100, as axes. Old `skills.yml` files get the spear rule in memory (the file is not changed) | `progression.js spear` 5/5 (3/5 on the old build) |

**Found while testing:**
- Vanilla 1.21.11 spears don't use the normal attack. The client sends a "stab" that hits targets 2 to 4.5 blocks away; a normal attack with a spear does nothing. Kills and hits are credited the same way either way.
- The server names campfire and magma damage only when a real block caused it. `/damage … minecraft:campfire` arrives as generic damage, so the test uses real blocks.

**Performance:** within the Step 1 budget. Cerberus, ms per tick, two warm runs each:
- combat: 0.11–0.14 (Step 1: 0.09–0.12);
- being hit: 0.14–0.16 (Step 1: 0.14–0.15);
- menu browsing: 0.15 (Step 1: 0.16–0.17).

None of the new code appears among the costly frames; the spread is the documented run-to-run noise.

**Review of the performance pass, fixed:**
- **A fast double-click in an admin screen could press a button on the next screen** (for example "+10 levels"). Reproduced with a lagging client: 3 of 4 stale clicks applied +10 on the old build, 0 of 4 now (`admin.js staleclick`). Admin and confirm screens now always open in a fresh window; the server drops clicks aimed at a closed window. Read-only Player Menu screens keep the fast in-place redraw, with a wider guard of about one round trip.
- Damage numbers survive a 0 or negative `display.damage-number-ticks`.
- HUD and menu timers no longer depend on the wall clock.

## Done: Player Menu 2.1 (Step 3, 2026-10-25)

Polish on Player Menu 2.0, vanilla only (no resource pack), all Played (`menu.js motion`, 21 checks):

| What | How it looks | Bounds |
|---|---|---|
| Opening | Header and navigation at once; the content comes in from the centre column outwards over 4 ticks (0.2 s) | Only when the menu opens from nothing. Buttons work from the first tick. |
| Moving between screens | A band of light sweeps out across the header row and settles (5 ticks); the new content is already there | Only between different screens, not on refresh or paging. |
| Sounds | A page turn when opening or moving deeper, a lower page turn going back or home, the usual click for buttons that stay on the screen | Admin screens keep their click. Closing is silent. |
| Skill page bar | The 7-pane bar fills up as the page appears (two panes a tick), starting from where you last saw it | ≤ 4 ticks |
| Progress since you last looked | The Skills tile, the category and the skill count the new levels (the number in the corner) with a "▲ +2 levels since you last looked" line; a skill page says "▲ Level 3 → 5" or "▲ +415 XP". One soft chime on the hub; the first visit to a levelled skill flashes its bar gold with a quiet level-up sound | Remembered in memory for the session only (no database writes). Chime and flash once per menu visit. |
| Selected category | The category screen shows the current category glowing in the middle of the header, with the other two as tabs beside it (switching keeps the Back history); Skills marks the category you opened last | Static: no animation |

**Engine:** `GUIs/menu/MenuAnimator` plays a screen's keyframes (`MenuView.later`, `soundLater`) on the viewer's own entity scheduler (Folia-safe). Rules:
- at most one animation per player;
- each runs at most 1 s and ends by itself;
- a new screen, a refresh, a close or a quit cancels it, and a step is only ever written into the exact render it was made for.

Checked in game: an animation interrupted by a fast click writes nothing into the next screen, and an open menu nobody touches has no animation running (`/cerb showqueues`).

**Performance** (`docs/PERFORMANCE.md`):
- **Pre-work** first: two allocation hot spots in menu text are gone. Cerberus's allocations while browsing fell 15%; CPU time was unchanged.
- **With animations:** menu browsing costs 0.158–0.172 ms per tick (0.144 before, budget 0.25). An idle open menu costs 0.036 ms, the same as an idle player; the profile shows no menu work.

**Independent review:** no high or medium issues (wrong-screen writes, item safety, Folia races, leaks all checked). Its seven low findings are fixed, among them:
- a single new level now also gets a green "▲" before the tile's name, because a count of 1 isn't drawn and some icons always glow;
- "since you last looked" compares with what the last screen actually showed;
- the chime and the flash are used up only when they really play.

**Not built:** special presentation for cultivation breakthroughs, which waits for realm advancement. The engine supports it: keyframes on any screen.

## Done: classes prototype (Step 5, 2026-10-25)

Warrior, Mage and Duelist on a data-driven ability engine (`docs/design/CLASSES.md`; status and deviations in its §11). Everything below was Played on the sandbox with `tools/playtest/classes.js` (91 checks) and a second test player for PvP. The other 22 classes are design only.

**The six proofs:**

| Proof | What was shown in game | Check |
|---|---|---|
| 1. Progress in more than one tree | Skills open classes by themselves (Blade Mastery 5 → Warrior, Intelligence 5 → Mage, Blade Mastery 10 + Critical Strike 5 → Duelist; never taken back). Cleave raised Warrior XP, Arcane Bolt raised Mage XP, in one session; three level-30 kills right after a Cleave gave Warrior 3 × 107 XP (half of each kill's skill XP) | `learn` 7/7, `trees` 8/8 |
| 2. Progress kept when switching | Switching loadouts back and forth left every rank, XP and slot as it was | `trees` |
| 3. Abilities from different trees together | One loadout: Riposte + Flurry + Precision (Duelist), Charge + War Cry + Battle-Hardened + Juggernaut (Warrior), Blink + Essence Flow (Mage). All cast; max health 100 → 120 from the Warrior passives; Blink waits for the movement lockout Charge started (shared across classes) | `mixed` 8/8 |
| 4. Loadouts saved and switched | Three loadouts, switched from `/class` and from the menu; ranks, loadouts, the active one, its name and a running 30 s cooldown survive a relog and a server restart (also a change made 0.2 s before a stop, while online) | `exploits`, `persist` 5/5, `menu` 15/15 |
| 5. Combat that feels different | Measured from the combat trace (table below) | `combat` 10/10 |
| 6. No exploits | See "Exploit checks" | `exploits` 21/21, `keys` 17/17, unit tests |

**How the three classes play (measured, netherite sword or blaze rod, against level-60 zombies):**

| Class | Ability | Measured |
|---|---|---|
| Warrior | Cleave | one swing hit all 3 zombies in front, each at 1.6× a basic hit (12.8 before Power) |
| Warrior | Execute | after a 3-zombie Cleave: 3 Momentum → 2.2× (17.7 before Power) |
| Mage | Frost Nova | damaged and slowed all 3 zombies around |
| Mage | Arcane Bolt | hit a zombie 7 blocks away: 8.4 → 9.7 with Essence (15 Essence) |
| Duelist | Riposte | the zombie's hit negated (no damage taken), answered at 2.0× |
| Duelist | Flurry | three strikes at 0.6× |
| Duelist | Feint | the next normal hit: a guaranteed crit (shown in the trace), used up by that hit |

**Exploit checks (all Played):**
- three left-clicks within 100 ms cast once; pressing again shows the seconds left;
- a cooldown follows the player into another loadout and across a relog (War Cry 13.7 s before, 6.9 s after);
- War Cry can't be recast to stack (the bonus stays +15%) and ends the moment you switch to a loadout without it;
- no switch during a fight (8 s after the last hit), 10 s between switches, no casting in the first second after a switch;
- an ability can't be in two slots (equipping it again moves it); locked abilities and wrong slots are refused; a rank drop empties the slots it no longer allows, in every loadout;
- a cast key never drops, swaps or moves the item: Sneak + Q keeps the sword, Sneak + F swaps nothing, Sneak + scroll keeps the slot;
- one key casts one ability: the arm swing a client sends after Q, or after throwing an off-hand snowball, casts nothing (both used to fire active 2);
- keys stay vanilla where they should: without Sneak, with a block (Q drops it), a bow (right-click draws), an empty slot (F swaps), the Fireball Wand (its own spell);
- abilities never hit players: Cleave with a player in front hits only the zombie; the Fireball ability flies through a player (on the build before the review fix it hit the test player once and set it on fire); a Sneak-attack on a player is a normal PvP hit;
- stored data is never trusted: a tampered document is repaired (ranks clamped, illegal slots emptied, a damaged cooldown capped at 1 hour); an unreadable one is left untouched; an ability or class missing from the files keeps its slot or progress, inert (unit tests).

**Found and fixed while testing:**
- In creative mode Paper reports the player's own inventory as CREATIVE, not CRAFTING, so the "no menu open" check refused every cast in creative.
- After a rank drop, a stale slot in an inactive loadout blocked unrelated edits ("War Cry is not unlocked yet" when equipping something else). Rank changes now repair every loadout, and an edit repairs its loadout first.
- Paper sends no left-click event when a mob is under the crosshair (the client sends an attack instead), so casting at a mob goes through the attack.

**Performance** (`docs/PERFORMANCE.md`; 9 bots, warm server, two runs each):
- **Players who don't cast pay nothing.** The `combat` load costs 0.133–0.159 ms per tick on the build before classes and 0.134–0.148 with classes: noise.
- **Casting.** In the new `abilities` load (about 5 casts per second), a cast costs about what the hits it deals cost: they use the same pipeline. Casting never rebuilds attribute snapshots.
- **Found and fixed.**
  - The save document was the largest class-specific cost on the tick. It is now built on the class writer thread, and class work on the region threads fell by about 40% (from 35–47 to 21–29 samples per run).
  - The cooldown message no longer builds a formatter per press.
- **Class logic** (keys, checks, targeting, cooldowns, XP) costs about 0.02–0.04 ms per key press, within the 0.05 ms budget.

**Independent review** (of the code, not the plan; it read the server's own code to check event behaviour). One high, four medium and four low findings, all fixed. The two input problems were reproduced in game first:
- **High:** the Fireball ability used the old spell, whose blast damages and ignites anyone, players included. It now casts a mobs-only Fireball that flies through players, and its Essence is checked and paid before anything is spent. A failed cast used to still earn class XP; that is fixed by the same change.
- **Medium:**
  - The arm swing that a vanilla client sends after a drop or an item use counted as a left-click. Sneak + Q also tried active 2, and throwing an off-hand snowball while sneaking cast Cleave. Both were reproduced in game; now neither casts.
  - A quick rejoin (for example "logged in from another location") could read the database before the quit save landed, then overwrite it. Loads and saves now share one ordered writer.
  - A stored document that couldn't be read was replaced by an empty one on join. It is now left untouched and classes stay off for that player.
- **Low:**
  - A number where text was expected in `abilities.yml` stopped the plugin from starting properly. A bad value now refuses only its own entry.
  - The power-cap check missed Momentum above 5 and unbounded Riposte windows.
  - A config typo emptied loadout slots for good. Those slots are now kept, inert.
  - The save document was rebuilt for every XP gain (about 0.07 ms each). Now there is at most one per player per second.

The review also found that cast XP paid for hitting farm animals; it now needs a hostile mob. Grey (far lower-level) mobs still pay full cast XP, the same gap as skill hit XP today; it belongs with the anti-farm decision 8.

## Done: ability controls, ability audit, class polish (Phases 1–3, 2026-10-25)

After the owner's class playtest: Sneak + left-click was unclear, Sneak + F swapped hands, and the Mage's focus abilities were unreliable. Everything below was Played on the sandbox unless noted. **Usability is not verified until the owner has played it** (`docs/PLAYTEST_CHECKLIST.md` §6).

**Reproduced first** (`docs/design/ABILITY_CONTROLS.md` §1). The input problems were real, but most of the "doesn't work" feeling came from two things:
- **Invisible feedback.** The HUD erased cast messages within 1–950 ms.
- **Silent misses that still cost.** An Arcane Bolt that missed by a block still spent Essence and its cooldown.

On top of that, the test Fireball Wand caught every right-click before the abilities saw it.

**Built (Phase 1, `docs/design/ABILITY_CONTROLS.md`):**
- **Two control schemes**, chosen per player and saved:
  - **Quick keys** (default): right-click, F and Q for actives 1–3; Sneak + right-click and Sneak + F for 4–5.
  - **Click combos** (option, Wynncraft-like): R R R / R L R / R L L / R R L, and F for active 5.

  Switched with the Controls tile or `/class controls`. The scheme lives in one class; the abilities don't know about it.
- **Keys are ability keys only when armed:** a weapon, a focus or an empty hand, with at least one active equipped. Doors, chests, friendly mobs, bows, spears and spell items keep their right-click. F and Q never swap or drop a weapon. Sneak pressed in the same tick as a key still counts: the decision waits one tick.
- **Feedback for every press**, on the HUD (it now has a message slot) with a sound per reason. It covers casts, cooldowns, Essence, the wrong item, no target or no room, no Momentum, not unlocked, an empty key and switching. Nothing goes to chat. An **ability bar** (boss bar, `/class bar auto|on|off`) shows keys, readiness and Momentum.
- **Checks before anything is spent:** a refused cast costs no Essence, cooldown or Momentum. Single-target abilities get aim assist (0.75 blocks) and a visible bolt.

**Audited (Phase 2, `docs/ABILITY_AUDIT.md`):** all 21 abilities one by one, with real key presses and effects measured in the world. 137 checks in eight phases, all passing on the final build: controls 26, combos 13, warrior 20, mage 23, duelist 14, mixed 17, the review's fixes 20, persistence 4. Every "nothing happens" check has a positive control. The harness (`tools/playtest/abilities.js`) handles the test client's limits instead of hiding them:
- the 1.21.9+ motion packet;
- item-use rotation in degrees;
- no Q-drop prediction;
- mob effects read from the server.

**Polished and fixed (Phase 3):**
- **Class XP only from real fights** (`ClassRanks.castXp`, unit-tested):
  - Farm animals pay no kill share.
  - Weak mobs pay a quarter of the cast XP, kill XP's own rule (20+ levels below the class's skills).
  - A self cast pays only in a fight with an enemy. Hitting a cow or an armor stand used to make Blink or War Cry pay.
- **Execute is a finisher:** with no Momentum it is refused (`Execute needs 1 Momentum`) instead of spending its cooldown on a plain hit. The default holds for any spend-all finisher, so existing `abilities.yml` files get it too; `needs: 0` turns it off.
- **Keystones checked by their values:** Glass Cannon adds +0.25 to the spell multiplier, and Blade Dancer adds +0.15 to the crit multiplier.
- **The Fireball Wand** casts the right-click abilities that fit a focus, and its own Fireball otherwise. Before, a Warrior's Cleave on right-click made the wand do nothing.
- **Signed bonuses:** a keystone's slower attacks showed as "+-4.6%" on the Skill, Stats and admin pages. They now read "-4.6%".

**Regression.** Every earlier suite was run on the Phase 3 build: 360 checks.

| Suite | Checks |
|---|---|
| smoke | 11 |
| Player Menu | 116 |
| Admin Control Panel | 65 |
| progression | 94 |
| classes | 74 |

After the review's fixes, every suite they touch passed again on the final build:
- smoke and the menu's navigation;
- progression magic and mastery;
- all of classes.js;
- all of abilities.js.

**Nine checks failed on the Phase 3 build, all on the test side, and all are fixed in the scripts:**
- Six still pressed or read the retired Sneak keys (classes.js combat, exploits, menu).
- Two came from the shared test character: hundreds of test Fireballs had raised the Essence skills that "magic" measures from level 1. An earlier run had also shown class passives raising its health. Every abilities.js phase now ends by emptying the loadout.
- One counted swings over only three kills. It now uses five.

The suite also found two real bugs, both fixed:
- the Fireball Wand doing nothing with Cleave on right-click;
- the "+-4.6%" display.

**The owner's first look at the new controls** (screenshots, 2026-10-25). Two things fixed the same day:
- **Blink said "no room" looking down at open ground.** It moved the feet along the look direction, which points into the floor. It now follows your sight line and stands you on the ground where you look.
- **"Arcane Bolt needs a focus" didn't say what a focus is.** It now reads "needs a focus (blaze rod, breeze rod or stick)".

The usability playtest continues.

**Performance** (`docs/PERFORMANCE.md`; 9 bots, two runs each, the same method as before):
- **Ability load:** Cerberus at 0.053 / 0.038 ms per tick after, against 0.053 / 0.024 before. Within the run-to-run spread.
- **Class logic:** about 0.016 ms per key press, against a 0.05 ms budget.
- **New parts:** the feedback, HUD message and ability bar don't show in the profile.
- **Players who don't cast:** class code in 5 and 0 samples per run.

**Independent review** (of the code, without my conclusions): 3 medium and 5 low findings. Each was checked against the code before fixing, and each fix is checked in game (`abilities.js fixes`); the list is in `docs/ABILITY_AUDIT.md`.
- **Medium:**
  - A damage ability on Q struck bare-handed, because the server takes the item out of the hand during the drop event. Q now casts a tick later.
  - Unhurtable mobs (a heart-bound Creaking, an invulnerable mob) paid class XP forever. They are no longer targets.
  - Right-click took the off hand's own use: a shield never blocked, and an axe couldn't strip a log. The off hand's use and the axe's block uses now stay vanilla.
- **Low:**
  - Sneak let go with the key cast the plain slot.
  - An unquoted `ability-bar: off` was ignored.
  - Abilities could hit another player's pet or mount.
  - Q right after closing an inventory dropped the weapon.
  - Fireball paid class XP fired into the sky.

## Done: class milestone wrap-up (2026-10-25)

After the owner tried the new controls. Played on the sandbox unless noted.
- **Both control schemes stay** (decision below). Nothing to build: both were already saved per player and tested.
- **A mob kept alive pays cast XP only up to its kill XP** (`Classes/CastXpLedger`, unit-tested). Before, a strong mob kept in a cage paid 6 XP per cast forever (item 13 below). Now each mob has a budget equal to what its kill pays. Casts at it, and self casts in a fight with it, draw from that budget; once it is spent, only the kill pays. In game, nine War Cries in a fight with one level-1 zombie paid 6 6 6 6 6 6 4 0 0 (40, its kill XP), and its kill still paid the usual share. A real fight ends long before the budget runs out, so it is unchanged. Found while testing: the cast that killed a mob paid again, because the death cleared the mob's record first. A dead mob's record now stays for 10 seconds.
- **Movement comes back in 1.2 s at most** (owner rule, below; `ClassCatalog.MOVEMENT_MAX_COOLDOWN_MS`, unit-tested):
  - **Blink:** 0.4 s, the gap between any two casts. Essence is its limit (20 per Blink). Two Blinks half a second apart both go off.
  - **Charge and Lunge:** 1.2 s. Their stun and strike keep their old pace, every 10 s and 7 s (`strike-cooldown` in `abilities.yml`). A Charge in between only moves you, and the HUD says when the strike is back ("✦ Charge · strike in 6.9 s"). Without this, Charge would stun the same mob 80% of the time and add a free hit every 1.2 s. This is a balance guard, not your rule, and easy to drop (below).
  - **The shared movement lockout:** 1.2 s. It now holds the group's other abilities only, so Blink after Blink waits 0.4 s, and Blink after Charge waits 1.2 s.
  - **Your server's files get the rule too.** A longer movement cooldown in a server's `abilities.yml` is lowered when it loads, and the log says so.
- **Shipped config reaches older installs** (`cerberus/world/cerb/ShippedConfig`, unit-tested). `abilities.yml` and `classes.yml` are written once, at install, and an update never replaces them, so earlier tuning never reached your server. Now a copy nobody edited is replaced by the new version at startup, and the old one is kept as `abilities.yml.old` / `classes.yml.old`. A file with your own edits is never touched; the log says it was kept. On the sandbox both files were unedited Step 5 copies and were updated, with the old copies kept byte for byte. Your `run.bat` also backs up the plugin data before every start.
- **Horses, pigs and other rideable mobs show their health bar** (your playtest report). They had none on purpose: the label rides on the mob as a passenger, these mobs have a single seat, and on 2026-10-24 a labelled horse couldn't be mounted or tamed, so rideable mobs were left unlabelled. Now they get the label like every mob. It steps out of the seat the moment a player right-clicks the mob (before the game seats them) and comes back as soon as nobody rides it, or at once if they only fed, saddled or leashed it. Cows, sheep and chickens were never affected.
- **Test harness:** the arena now stops time and phantoms. The trapped-mob check puts the test bot back in place after each hit: nine knockbacks had pushed it off the platform. The item check now looks for a dropped sword, not any mob loot.
- **Independent review** (of the code, without my conclusions): no high-severity problem; 2 medium and 4 low, all fixed:
  - **Medium:**
    - A server file could make a movement ability strike every 0.4 s. A strike or stun now comes back no faster than 1 s, like any other ability; only the movement may be quicker.
    - A config refresh could overwrite a backup. A backup is never overwritten now: the next one is `.old.2`, `.old.3`… On the sandbox the second refresh wrote `abilities.yml.old.2` and left `.old` untouched. The refresh also runs only at startup now, not on `/cerb reload`.
  - **Low:**
    - A cast that reached a dry boss and fresh adds paid nothing. It is charged to a mob that still has XP to give.
    - A mob killed before any cast paid kept its record for 30 minutes; now 10 seconds.
    - A cancelled death cleared a living mob's record.
    - A rideable mob's label flickered on every right-click. It now steps aside only for a click that can seat you: not Sneak, not a spectator, not a baby, and a pig or strider only with a saddle.
  - **Two design notes, built as recommended:** a mob's cast-XP budget follows the weak-mob rule like its kill (a weak mob's budget is a quarter); and Blink's 20 Essence matches the base Essence regeneration exactly, so it can chain indefinitely at about 15 blocks a second (decision 16 below).

Checks on the final build (after the review's fixes):

| Suite | Checks |
|---|---|
| unit tests | 170 |
| abilities.js: warrior, mage, duelist, mixed | 21, 24, 15, 20 |
| abilities.js: controls, combos, review fixes, persistence across a restart | 26, 13, 20, 4 |
| classes.js: learn, trees, mixed, combat, exploits, menu | 7, 8, 8, 10, 22, 15 |
| smoke | 14 |

Every miss along the way was the test, not the plugin, and each test is fixed:
- **Two checks pressed Blink too late.** They were timed for the old 2 s lockout and pressed at 1.2 s, exactly when the new one ends.
- **The bot never got off the horse.** This mineflayer's "dismount" sends Jump (since 1.21.2 you get off with Sneak), and it never clears its own riding flag. The label's return is the evidence now: it only comes back when nobody rides.
- **One check depended on test order.** It counted snapshot rebuilds while filling a loadout that an earlier phase had left in use.

Nothing from the class milestone is left open.

## Done: the 35-skill audit (2026-10-25)

`docs/SKILL_AUDIT.md`: all 35 skills read from the code, with ten questions each and a status. The detailed evidence is in `audit/skills-2026-10-25/`.

| Status | Count |
|---|---|
| Functional | 4: Blade Mastery, Weapon Mastery, Ranged Precision, Pathfinding (thin) |
| Partial | 10 |
| Disconnected | 11 |
| Placeholder | 10 |

The headlines:
- **23 skills have no XP source.** Nothing pays for gathering, crafting, smelting, brewing, enchanting, fishing, taming or trading.
- **Every skill's own effect code is unreachable and unsafe to wire.** It holds one shared level for everyone, duplicates items and stacks bonuses on every call. Skills act only through the attribute engine.
- **Normal players can't use magic.** The only spell item is the op-only wand, and the Mage needs Intelligence 5, which only spells train.
- **Live XP exploits:**
  - magic skill XP is paid when a spell is fired, even into the sky;
  - kill XP pays in full for spawner mobs, bred animals, creative mode, renamed mobs and player kills, and goes to whatever is in the hand at death;
  - Critical Strike pays per hit without limits.
- **Food and potions heal nothing:** vanilla healing is off for players.

The highest-value loops, in order:
1. Mining → Smithing → Equipment (`docs/design/GATHERING.md`, being built).
2. Closing the live XP exploits.
3. The defensive skills' XP.
4. A way into magic.
5. Fishing.
6. Woodcutting and Farming.

## Done: Mining → Smithing → Equipment (2026-10-25)

The first complete gathering loop: find stone and ore → mine it (**Mining XP**) → smelt it and forge gear (**Smithing XP**) → the gear's quality feeds the attribute engine → a better pickaxe finds more ore, a better sword hits harder. Design and the counting rules: `docs/design/GATHERING.md`. Everything is data in `resources.yml` (a new file, refreshed at startup while unedited, like `abilities.yml`).

**What a level does now:**

| Skill | Before | Now |
|---|---|---|
| Mining | nothing | XP per natural block; **gathering speed** +0.2% per level toward a 25% cap (Mining 100: +13.8%); **Fortune** +0.5 per level |
| Smithing | nothing | XP for smelting and forging; better **quality** odds (level 1: 2% Superior or better; level 100: 40%, 12% Masterwork) |
| Fortune (attribute) | shown only | a chance of one extra ore, 0.5% per point, at most 50% |

**Forged quality**, by gear kind, through the attribute engine and its caps (PvE only, like every attribute):

| Quality | Weapon in hand: Power for that weapon | Each armor piece worn: Fortitude | Tool in hand: Fortune |
|---|---|---|---|
| Fine | +3 | +1 | +3 |
| Superior | +6 | +2 | +6 |
| Masterwork | +10 | +4 | +10 |

**What can't be farmed** (each checked in game with a positive control next to it):

| Way to farm | Result |
|---|---|
| Place and break (stone or ore) | no XP |
| Silk Touch an ore, place it, break it again | XP the first time only; Silk Touch never gets the bonus |
| A lava-and-water generator | the stone it makes pays nothing |
| A piston pushing an ore | the pushed ore pays nothing |
| TNT, creative mode, a machine | no XP |
| Fortune on top of Fortune | at most one extra item per block (40 ores at the 50% cap: 56 raw iron; 1 or 2 from each); with Fortune III, never above vanilla's 4 plus one |
| A furnace that smelted two kinds of input | one take pays once (the game fires its extract event once per kind) |
| A hopper under a furnace | pays nobody |
| An iron golem, gold or copper farm | gear forged from metal you didn't smelt yourself is plain vanilla gear: no Smithing XP, no quality (each ingot you take out of a furnace covers one unit of forging; switchable: `forging.needs-smelted` in `resources.yml`) |
| A torch, bell or banner standing on an ore | the bonus only ever goes to the ore's own drop |
| A click on a crafting result that makes nothing (full cursor; a number key onto an occupied hotbar slot) | pays nothing |
| Storage blocks both ways, nuggets, the repair recipe, armor trims | pay nothing |
| Re-crafting to fish for a quality | a shift-click batch shares one roll; each roll costs the materials |

**Equipment:** Power counts only with that weapon in the main hand (not in the off hand), Fortitude per armor piece worn (not held), Fortune with the tool in hand. Scrolling between plain items rebuilds nothing; to and from a forged item rebuilds once per change.

**The real-time XP display** (your request): every XP gain shows for 2.5 s in the middle of the HUD bar, gains of a skill within 4 s merged ("+86 Mining"); an ability or refusal message covers it, and it comes back afterwards with the time it had left. `/xpdisplay compact` (default), `detailed` ("+8 Mining XP · 13/115 · Lv 1") or `off`, stored on the player. Kill XP left chat; level-ups stay. The "low-level mob" hint now shows at most once a minute.

**Proving the extension points:** a test-only `resources.yml` adds a custom ore (an amethyst block "test vein" that needs a drill of tier 5, level 50, 75 XP), its own drop (a "Test Crystal") and the drill. With no code change, in game: a netherite pickaxe is refused with "test vein needs a drill (tier 5)" and the block stays; the drill gathers it for 75 XP and Test Crystals; a machine's extraction (`/cerb gather extract`) gives the crystals and no XP; with a pickaxe the machine can't; a placed one gives its own block back and nothing else.

**Your older `attributes.yml`** gets Mining's entries either way: an unedited copy is refreshed at startup (the old one kept as `attributes.yml.old`; on the sandbox it was the Kill XP v2 copy), and a skill your file doesn't mention takes the shipped entry. `Skill: []` turns one off.

**Checks:**

| Suite | Checks |
|---|---|
| unit tests | 191 (13 new: the rule table, the XP line, gear contributions, config delivery) |
| gathering.js: mine, farm, fortune, smelt, forge, upgrade | 13, 11, 7, 6, 14, 5 |
| gathering.js: gear, custom, xpdisplay, persistence across a restart | 8, 9, 3, 1 + 4 |

Every miss along the way was in the test, not the plugin: the arena was filled before its chunks loaded, the bot picked up drops before they were counted, `/item replace` doesn't run on this Folia build, and this mineflayer maps the 1.21 smithing table as the old 3-slot one.

**Regression** (the existing in-game suites on the gathering build):

| Suite | Checks |
|---|---|
| smoke | 14 |
| progression.js: killxp, stats, speed, spear | 5, 14, 13, 5 |
| menu.js: nav, safety, perf | 63, 18, 2 |
| admin.js: main, attrs | 40, 14 |
| abilities.js: controls, combos, warrior, mage, duelist, mixed, review fixes | 26, 13, 21, 24, 15, 20, 20 |
| classes.js: learn, exploits, combat, menu | 7, 22, 10, 15 |

On the first pass five checks failed, none of them in the plugin:
- **Two checks still said Fortune does nothing.** Now true the other way round; they look for the extra-ore chance.
- **The attack-speed and Fireball checks** ran with a class loadout an earlier session had left on the test character (a passive that slows attacks; the wand casting the loadout's ability). The ability phases end by clearing it; after them both pass. The speed check now also pins the skills behind Agility, which other phases raise.
- **`classes.js learn`** needs a character that has never learned a class (as its notes say); with a new one, 7 of 7.
- **The spear checks** first landed no hit on a fresh mob (timing), then, on the re-run, found no spear: other phases had filled the test character's hotbar, and the check only looked there for the spear it was given. It now finds a given item anywhere and moves it to the hotbar; 5 of 5.

**Independent review** (of the code, without my conclusions; it also read the server's own bytecode where vanilla behaviour mattered): no high-severity problem; 3 medium and 12 low, fixed or answered:
- **Medium:**
  - **A torch on an ore could take the bonus**, a small item duplication: the drop event also carries what blocks attached to the ore drop. The bonus now only goes to an item type the ore itself drops. In game: 20 ores with a torch on each, always 1 torch back.
  - **A shift-click netherite upgrade paid nothing:** vanilla moves the result first and counts the emptied stack. A shift-click now pays from that very signal (see the second review below). In game: 50 XP, once.
  - **Farms made Smithing renewable**, and gold farms by far: forging paid for any metal, and every craft rolled a quality. Built the protection (above): iron, gold and copper gear pays and gets a quality only from metal you smelted. This was decision 19; it is switchable.
- **Low, fixed:**
  - **Smelting:** a right-click split paid only the first half, and a number key or F onto an empty slot paid nothing. Smelting is now counted as metal leaves a furnace window, and that one swap from the click. In game: both halves pay, and the number key pays once.
  - **attributes.yml:** a skill an owner deleted from their own file came back with the new rule. Only skills an update adds (Mining) fill in now, and the log says so.
  - **A stale bonus** could follow a player to a later block. Each break now starts clean.
  - **Placed blocks:** covered nylium fading into netherrack, spreads and mob-set blocks are remembered too.
  - **Spears** couldn't be forged (no unit count); a spear is one unit.
  - **resources.yml:** a misspelled tool kind is refused with a log line; it used to load and make the ore unmineable.
  - **The XP line** waited out the full length of a message that ended early.
  - **Forging:** a payment armed by one craft could be spent by another in the same tick; it is now tied to its own window and disarmed by any other craft.
  - **Gear:** a hit in the same tick as a hotbar scroll or a hand swap used the previous item's quality; scrolls and swaps are read at once.
  - **Equipment after a quit:** a last equipment tick could bring a leaving player's entry back.
- **Low, answered:** one progression check depends on the reduced-XP hint's once-a-minute timer, which a rejoin resets, so each run starts clean; and the cost of a paying block is measured below.

**Second review** (of the fixes, again without my conclusions): one high, one low and a nit, all fixed:
- **High: my first shift-click fix could be farmed.** It paid when one more netherite sword was in the inventory a tick later. A shift-click that fails for want of room, with a netherite sword swapped in from the cursor in the same tick, paid 50 XP every time. A shift-click now pays only from vanilla's own signal that its result moved: the statistic raised for no item, by 0, in that window. In game, a new check sends both clicks in one go: on the old build it fails with +50 (so it catches the hole), on the fixed build it passes with +0, and the ordinary shift-click still pays 50 once.
- **Low: any furnace-shaped screen counted as a furnace.** A plugin's menu in the furnace layout could pay smelting XP. Only a furnace, blast furnace or smoker block's window counts now.
- **Nit:** a stale Fortune bonus is also cleared when another plugin cancels and then allows a break.
- **Playtest note:** ingots smelted before this update don't count as smelted (only ingots taken out of a furnace from now on do), so an old stockpile forges plain gear (`docs/PLAYTEST_CHECKLIST.md` §7, 8b).

**Third review** (of the second review's fixes, against the server's bytecode): no high or medium findings. It confirmed that the shift-click signal can't be faked or paid twice. Two low findings and four nits, fixed or noted:
- **A click in a furnace window** looked the block up before the cheap checks; it now does that last.
- **The empty take has no time limit now,** so a garbage-collection pause between the click and the take can't lose a real upgrade. The next tick or the next click still disarms it.
- **A click in creative mode** also disarms an earlier click's payment. An admin switching game mode within the same tick could otherwise have collected it.
- **Comments,** and a note in the negative control: it can't prove that both clicks landed in one tick, but the payment no longer depends on that.

In game after these: upgrade 5, smelt 6, forge 14.

**Performance** (`docs/PERFORMANCE.md`: 9 bots, plugin time in ms per tick, two runs each, TPS 20.00 everywhere):

| Load | Before | After |
|---|---|---|
| `gather` (9 players mining) | 0.037 / 0.035 | 0.059 / 0.043 |
| `craft` (9 players crafting) | 0.047 / 0.055 | 0.070 / 0.058 |
| `abilities` | 0.122 / 0.113 | 0.092 / 0.097 |
| `combat` | 0.122 / 0.122 | 0.109 / 0.105 |

- **The first "after" pass found two real costs, both fixed:**
  - the crafting-grid handler built a whole recipe on every change to a grid (`craft` was 0.108 / 0.099);
  - the XP display formatted its numbers with `String.format`.
- **What's left is small:** a block broken or a craft click costs about 10–20 µs, nothing runs per tick, and nothing touches the database per block.
- **The `abilities` numbers come from a warm server.** The first `abilities` run after a restart includes the ability code's warm-up (0.172 and 0.177). The baseline's server had already run 20 minutes of class tests.

## Done: ghost mob labels (2026-10-26)

**What you saw:** "Lv5 Horse" labels floating where horses had died.

**The cause was vanilla horse behavior, not death.** A label rides its mob as a passenger. An untamed horse, donkey, mule or llama treats any passenger as a rider being tamed:
- it runs around and bucks the label off within seconds;
- the label is left floating where it fell, and the horse walks on without one;
- when the horse died, the cleanup looked for the label on the horse, found nothing, and the stray stayed until its chunk unloaded.

Reproduced on the sandbox: 6 of 7 untamed horses and llamas threw their labels within 2.5 s, all 7 within 20 s. The same passenger also kept tamed horses from breeding, because vanilla won't breed a horse that carries anything.

**The fix** (`Manager/MobLabelManager`):
- **Bucking paused while a label rides.** The horse's "run around like crazy" behavior is paused through Paper's goal API.
- **Taming works as before.** The paused behavior comes back whenever the label steps aside for a player, so riding and taming are vanilla.
- **Breeding works.** A horse in love keeps its label off until love ends.
- **No strays, whatever the cause.** A label taken off its mob for any reason is removed at once, and the mob gets a new one a few seconds later.
- **Full cleanup on death.** Every label riding the mob is removed.

There is no per-tick work. `/cerb labels [radius] [clean]` (admin) counts labels nearby, shows the ones riding no mob, and removes them with `clean`.

**Checks:** `tools/playtest/labels.js`, 11 checks:
- untamed horses and llamas in an open field keep their labels for 20 s;
- horses, llamas, pigs, cows and zombies killed by `/kill`, by lethal hits, and right after a right-click leave no label, by both the server's count and the client's view;
- an untamed horse can still be mounted, and its bucking is back while it is ridden;
- its label and the pause return when the rider gets off;
- two tamed horses breed, and all three wear labels afterwards;
- labels survive chunks unloading and reloading.

On a control build with the fix switched off, the idle check fails (0 labels on a live mob, 7 orphans) and so does breeding. Smoke 14/14.

Strays already in your world disappear at the next restart (labels are never saved).

## Roadmap

`docs/ROADMAP.md`: performance first, then decisions A–E, Player Menu 2.1 animations, and the classes and abilities design and prototype. Earlier candidates still on the list: the gathering, crafting and Fortune loop; bestiary and zones as data; the first weapons and gear slice; Ascension (realms); the next speed stats (`ATTRIBUTE_EXPANSION.md`). Each milestone starts with a performance baseline and ends with in-game checks and new playtest checks.

## Decisions

**Made on 2026-10-25:**
- **Merging:** a reviewed pull request into `master` after your playtest; never merged automatically.
- **PvP: off for now, not permanent.** Every PvP limit (attributes, speed bonuses, abilities, class XP) is temporary: not turned on yet, not decided against. A later PvP pass turns them all on. What is off and where each switch is: `docs/ROADMAP.md` → PvP.
- **Damage-reduction cap:** 75% kept, applied to resistance × armor (built).
- **Weapon Mastery:** its all-weapon bonus stays (analysed).
- **A–E** (speed in PvP, shelves, Survival fire, projectile XP, spears): decided and built (Step 2).

**Made after the controls playtest (2026-10-25):**
- **Both control schemes stay, permanently:** quick keys and click combos, each player's own choice. The choice is saved, switching never touches progress or equipped abilities, and both stay supported and tested.
- **The owner prefers click combos** (R R R). The server default stays quick keys until broader player feedback (`classes.yml controls.default-scheme`).
- **The next milestone is Gameplay Depth & Systems Completion** (`docs/ROADMAP.md`): the 35-skill audit, then Mining → Smithing → Equipment. The six next classes wait, not cancelled.
- **Movement: 1.2 s at most, from now on.** Every movement ability (a dash or a blink), and the lockout they share, comes back in 1.2 s or less; Blink well under half a second. Built (above). I added one guard to it: Charge's stun and strike, and Lunge's strike, keep their old 10 s and 7 s. To have them strike every 1.2 s, delete the two `strike-cooldown:` lines in `abilities.yml`.
- **Real-time XP display** (requested): XP gains shown briefly on the HUD, repeated gains merged, combat and ability messages first, with Compact / Detailed / Off settings. A small task in this milestone (`docs/ROADMAP.md`).

**Still yours:**
1. **Big hits and the defense bar.** One hit larger than about 430 (for a 300-point bar) is still taken at the bar's full rate, so the bar absorbs more than it holds. Today only extreme hits reach that. Recommendation: limit the bar to its points when the first bosses with big telegraphed hits arrive (`ATTRIBUTES_ENGINE.md`).
2. **Weapon Mastery's double role.** Recommendation: split it at the weapons and classes design step (Step 4), so every weapon needs two skills for 125 Power (`ATTRIBUTES_ENGINE.md`).
3. ~~Where gathering and crafting go~~: decided (Gameplay Depth first) and the first loop is built.
4. **Folia version.** 1.21.11 left PaperMC support on 2026-10-14 (current: 26.x). Recommend planning the upgrade now that Attributes v1 is in.
5. **Labels on all mobs.** Keep (current choice from June), or label hostiles only to save entities?
6. **`lib/DTR-1.0.0.jar` and `libs/Volor.jar`.** Now unused by the build. Delete them from this repo, or keep them as references?
7. **Ranged mobs ignore mob level.** Melee mob damage scales with the mob's level and tier (`combat.yml mob-damage-scaling`); arrows, fireballs and other mob projectiles do vanilla damage at any level. A level-100 skeleton shoots like a level-1 one. Recommendation: scale mob projectiles the same way, as a separate small change, because it makes ranged mobs much more dangerous.
8. **Idle damage farms.** Defensive XP (melee and now projectiles) has no idle check: a player standing in a mob trap still trains Survival and Heavy Armor Mastery, as before Step 2. Recommendation: one rule for all defensive XP, for example none after a minute without player input (the server tracks this), when gathering or anti-grind work comes up.
9. **Environmental damage is vanilla-sized.** Falls, fire and lava deal vanilla amounts against a 100–300 health pool: lava costs 1–4% of health per hit where vanilla's 20 hearts lose 20%. Fire resistance now lowers that further. If lava and falls should stay dangerous, scale environmental damage with max health; that is a separate design decision.
10. **Classes and abilities:** eight decisions in `docs/design/CLASSES.md` §10, each with a recommendation:
    - abilities = the designed Techniques;
    - how to cast;
    - unlock by rank;
    - slot counts;
    - abilities in PvP;
    - skill-based class entry;
    - the roster;
    - weapon display names.

    The prototype uses the recommendations until you decide, with one exception: abilities in PvP (item 5) are off for now. That is not permanent: PvP gets turned on later (`docs/ROADMAP.md` → PvP). How to cast (item 2) is now built as two schemes, see item 11.
11. ~~The default control scheme~~: decided (above).
12. **Execute needs Momentum** (built, reversible). At 0 Momentum it was a plain hit that spent its 8 s cooldown, so it is now refused and spends nothing. To keep the old behaviour, set `needs: 0` on its resource effect.
13. ~~Cast XP from a mob that never dies~~: built as recommended (each mob pays cast XP only up to its kill XP; above).
14. **A way into magic for normal players** (`docs/SKILL_AUDIT.md`). Today only an admin can open the Mage. Recommendation: let Spell Weaving 5 open it (kills with a stick or blaze rod already train Spell Weaving), together with magic skill XP paid on hits instead of casts.
15. **Lifesteal for everyone from level 1.** Weapon Mastery gives every player 2.7% lifesteal on mob hits from the start, the only healing in a fight. Keep it, or start it at 0 and let it grow with the skill?
16. **Blink chaining.** At 0.4 s and 20 Essence, Blink costs exactly the base Essence regeneration, so it can chain indefinitely at about 15 blocks a second (like a teleport item limited only by mana). Keep it, or raise its Essence cost so a chain runs dry after a few seconds?
17. **Gathering numbers** (`resources.yml`, all yours to tune): Mining XP per block, the weak-resource quarter, Fortune's 0.5% per point and 50% cap, the quality odds and bonuses.
    - **Pace:** strip mining by hand (about 2 blocks a second) pays about 8,000 Mining XP an hour at level 1, so level 10 (17,900 XP) takes about 2 hours, the same order as fighting.
    - **The fast case:** an Efficiency V pickaxe with a Haste II beacon breaks stone instantly, 10 to 20 blocks a second. That is 36,000 to 72,000 Mining XP an hour until level 21 (152,000 XP, 2 to 4 hours), then a quarter of it.
    - **Recommendation:** keep it (it needs an end-game beacon), or pay stone and deepslate 0.5 XP if you want it slower.
18. **Gathering speed applies to every block,** dirt and wood included, as one block-breaking stat. Woodcutting and Excavation could get their own later; for now Mining drives it alone.
19. ~~Iron and gold farms and Smithing~~: built as a protection after the review (above). Gear forged from iron, gold or copper you didn't smelt yourself is plain. To pay for any metal instead, empty the list: `forging.needs-smelted: []`.
20. **Quality bonuses don't depend on the metal.** A Masterwork golden chestplate gives the same +4 Fortitude as a Masterwork netherite one (the armor itself is much weaker). Keep the flat bonuses, or scale them by material?
