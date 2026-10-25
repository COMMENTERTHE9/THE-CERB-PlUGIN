# Cerberus — Project Status

*Last updated 2026-10-25 (branch `claude/quirky-mccarthy-nhaz58`). Update this file when a milestone lands.*

**Current milestone:** the owner's decisions of 2026-10-25 are in: the 75% damage-reduction cap (resistance × armor; the defense bar is not counted), PvP playing as before Attributes 2.0, and the Weapon Mastery stacking analysis, all verified in game on the cloud sandbox. The owner has playtested Player Menu 2.0; the Admin Control Panel and Attributes checks are in `docs/PLAYTEST_CHECKLIST.md`. Step 1 of `docs/ROADMAP.md`, the performance assessment, is done. `docs/PERFORMANCE.md` has the details: Cerberus's server time dropped 66–69% for menu clicks, 17–37% in combat and 21–30% for hits taken, verified with the same reproducible loads. **Next: decisions A–E**, then menu animations, and the classes and abilities design.

**Evidence key.** **Played** = exercised on a real Folia 1.21.11 server with a scripted test client (cloud sandbox, not your PC). **Tested** = unit tests. **Read** = code inspection only. A green build alone proves nothing about gameplay.

## Summary

The foundation runs. The plugin loads cleanly on Folia 1.21.11 under Java 21 and 25, saves and reloads player progress, and survives restarts. The playable loop today is: fight mobs → earn skill XP (more from higher-level mobs) → level up → **get stronger**: skills feed six attributes that combat reads, so Blade Mastery 100 kills a level-50 zombie in about 5 swings instead of 20, defensive skills triple max health, magic skills triple mana and scale spells (Played). The Stats page shows all of it. Most of the designed game (realms, weapons, gear, talismans, mounts, families, crafting) exists only in `docs/design/`.

## What works (Played)

| Area | What you can see |
|---|---|
| Startup / shutdown | Loads in ~30 s; 5 SQLite tables; `mobs.yml` (7 entries), 35 skills, `combat.yml` v3; `stop` saves and exits cleanly. |
| Player health | 100-HP custom pool, 300-point defense bar, 1000 mana, action-bar HUD. Fall, lava, mob, and command damage all route through it. 250 ms hit invulnerability. |
| Death | Lethal hit → instant respawn at full HP (no death screen, by design). |
| Mob scaling | Every mob gets an HP pool (×8 per realm tier, +1 level per 150 blocks from spawn) and a live floating label (`Zombie 50❤ Lv1`). Kills are guaranteed when the pool empties. |
| Combat XP | Kill XP goes to the weapon's skill (sword → Blade Mastery, fist → Martial Expertise; `skills.yml`). Critical Strike XP per hit; Survival XP when hit. Level-up messages. |
| Persistence | Skill levels and XP survive restarts; saves coalesce within 1 s; a failed database load disconnects the player instead of overwriting data. |
| Player Menu | Head in slot 9 or `/menu`. Hub with the character at a glance and every destination marked *Available* or *In development*. Skills → category → skill pages (training sources, attribute and speed gains, effects with status, next milestones); Stats & Attributes; Ascension (realm ladder); Equipment; Bestiary; World & Exploration; Guide. Verified with `tools/playtest/menu.js` (nav 63 as op / 61 as non-op, safety 18 with controls, perf 2: no database access and no snapshot rebuilds while browsing). Four skills shown under new names (`NAMING.md`). |
| Admin Control Panel | `/cerb panel`: Players (inspect/edit), Combat Lab (spawn at level, mob inspector, cleanup of your own test mobs), Progression Lab, Attributes (inspector, simulator, dev buffs), My Test Character, Server & Plugin, Maintenance. Permission-checked on every click; verified with `tools/playtest/admin.js` (main 40, attrs 14, owners 5, cross 5, security 6). An independent review found no security or item-duplication hole; its correctness findings are fixed. |
| Attributes | Power (per weapon type), Agility (crits), Vitality (max health), Fortitude (armor, defense bar, resistances), Essence (mana, regen, spell damage), Fortune (shown only), plus speed stats: attack speed (per weapon type), bow draw speed, movement speed, spell cooldown recovery, with diminishing returns and caps; paused during PvP by default. Derived from skills, capped in one place, cached per player, `attributes.yml` with kill switches. Stats page (`/stats` or Player Menu → Stats & Attributes). Verified with `tools/playtest/progression.js` (incl. `speed`, 13 measurements) and the unit tests. |
| Defense | Resistance → armor → defense bar; resistance and armor together capped at 75%, the bar a separate pool; PvP hits use the pre-attribute defense as the same share of health (`progression.js defense`, `defense-cap`, `pvp`; `docs/ATTRIBUTES_ENGINE.md`). |
| Kill XP v2 | Based on mob level and tier: Common L1 40, L50 334, Rare L50 668; ×0.25 for mobs 20+ levels below your skill. |
| Test content | `/givetestitem kit`; Fireball Wand (20 mana, regenerates); `/cerb spawn <mobId>`; `/spawncustomzombie` (High Health Zombie, BOSS pool); `/cerb skill <player> <skill> level\|xp <n>`; `/testregen`; `/cerb healthstate`, `/cerb combatdebug`. |

## Incomplete (Read unless noted)

| Area | State |
|---|---|
| **Skill effects** | Through attributes (vs mobs): weapon skills, crit skills, defensive skills and magic skills now matter, and weapon/bow/movement/spell skills also make the action faster (Played). PvP still uses the old per-skill bonuses (a design decision for later). Fortune and the gathering/crafting skills feed nothing yet. Each skill page lists what is live, PvP-only or not active. Most old per-skill `applyEffect` code is still never called. |
| **XP sources** | Only combat, spells, damage taken, and new-chunk discovery grant XP. Mining, Woodcutting, Farming, Fishing, Crafting, Smithing, Alchemy, Enchanting, Herbalism, Cooking and 9 more have **no XP source**. |
| Realms | Every player is Awakened; realm advancement (and its attribute bonuses) is not built. |
| Old stat managers | Strength / Luck / Magic Find / Yield managers exist but nothing reachable calls them (Fortune will replace Luck / Magic Find). |
| Crafting & traps | ~600 lines of quality-crafting and trap code, but no recipes are registered and the trap listener is never registered: unreachable. |
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
| No click type in the menu moves an item; outside it the head can't be dropped, swapped, number-keyed, shift-clicked into a chest, placed, framed, put on an armor stand, picked out with middle-click or swapped by a powered shelf; one head after rejoin and respawn | menu.js safety 18/18, with three controls proving the bot's actions reach the server |
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

## Roadmap

`docs/ROADMAP.md`: performance first, then decisions A–E, Player Menu 2.1 animations, and the classes and abilities design and prototype. Earlier candidates still on the list: the gathering, crafting and Fortune loop; bestiary and zones as data; the first weapons and gear slice; Ascension (realms); the next speed stats (`ATTRIBUTE_EXPANSION.md`). Each milestone starts with a performance baseline and ends with in-game checks and new playtest checks.

## Decisions

**Made on 2026-10-25:**
- **Merging:** a reviewed pull request into `master` after your playtest; never merged automatically.
- **PvP:** unchanged for now (built); attributes in PvP get a dedicated pass later.
- **Damage-reduction cap:** 75% kept, applied to resistance × armor (built).
- **Weapon Mastery:** its all-weapon bonus stays (analysed).
- **A–E** (speed in PvP, shelves, Survival fire, projectile XP, spears): decided, scheduled as Step 2 of `docs/ROADMAP.md`.

**Still yours:**
1. **Big hits and the defense bar.** One hit larger than about 430 (for a 300-point bar) is still taken at the bar's full rate, so the bar absorbs more than it holds. Today only extreme hits reach that. Recommendation: limit the bar to its points when the first bosses with big telegraphed hits arrive (`ATTRIBUTES_ENGINE.md`).
2. **Weapon Mastery's double role.** Recommendation: split it at the weapons and classes design step (Step 4), so every weapon needs two skills for 125 Power (`ATTRIBUTES_ENGINE.md`).
3. **Where gathering and crafting go** in the new order (`ROADMAP.md`).
4. **Folia version.** 1.21.11 left PaperMC support on 2026-10-14 (current: 26.x). Recommend planning the upgrade now that Attributes v1 is in.
5. **Labels on all mobs.** Keep (current choice from June), or label hostiles only to save entities?
6. **`lib/DTR-1.0.0.jar` and `libs/Volor.jar`.** Now unused by the build. Delete them from this repo, or keep them as references?
