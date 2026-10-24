# Cerberus — Project Status

*Last updated 2026-10-24 (branch `claude/quirky-mccarthy-nhaz58`). Update this file when a milestone lands.*

**Current milestone:** Admin Control Panel 2.0 and Attributes & Progression 2.0 (increments 1–7 of `docs/ATTRIBUTES_ENGINE.md`) are built and verified in-game on the cloud sandbox; waiting for the owner's playtest on the real server.

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
| Menus | Player Menu head (slot 9) → Skills → Combat / Magic / Utility → per-skill detail page. Other menu buttons open *Coming Soon*. |
| Admin Control Panel | `/cerb panel`: Players (inspect/edit), Combat Lab (spawn at level, mob inspector, cleanup of your own test mobs), Progression Lab, Attributes (inspector, simulator, dev buffs), My Test Character, Server & Plugin, Maintenance. Permission-checked on every click; verified with `tools/playtest/admin.js` (main 40, attrs 14, owners 5, cross 5, security 6). An independent review found no security or item-duplication hole; its correctness findings are fixed. |
| Attributes | Power (per weapon type), Agility (crits), Vitality (max health), Fortitude (armor, defense bar, resistances), Essence (mana, regen, spell damage), Fortune (shown only). Derived from skills, capped in one place, cached per player, `attributes.yml` with kill switches. Stats page (`/stats` or Player Menu → Stats). Verified with `tools/playtest/progression.js` and 81 unit tests. |
| Kill XP v2 | Based on mob level and tier: Common L1 40, L50 334, Rare L50 668; ×0.25 for mobs 20+ levels below your skill. |
| Test content | `/givetestitem kit`; Fireball Wand (20 mana, regenerates); `/cerb spawn <mobId>`; `/spawncustomzombie` (High Health Zombie, BOSS pool); `/cerb skill <player> <skill> level\|xp <n>`; `/testregen`; `/cerb healthstate`, `/cerb combatdebug`. |

## Incomplete (Read unless noted)

| Area | State |
|---|---|
| **Skill effects** | Through attributes (vs mobs): weapon skills, crit skills, defensive skills and magic skills now matter (Played). PvP still uses the old per-skill bonuses (a design decision for later). Fortune and the gathering/crafting skills feed nothing yet. Most old per-skill `applyEffect` code is still never called. |
| **XP sources** | Only combat, spells, damage taken, and new-chunk discovery grant XP. Mining, Woodcutting, Farming, Fishing, Crafting, Smithing, Alchemy, Enchanting, Herbalism, Cooking and 9 more have **no XP source**. |
| Realms | Every player is Awakened; realm advancement (and its attribute bonuses) is not built. |
| Old stat managers | Strength / Luck / Magic Find / Yield managers exist but nothing reachable calls them (Fortune will replace Luck / Magic Find). |
| Crafting & traps | ~600 lines of quality-crafting and trap code, but no recipes are registered and the trap listener is never registered: unreachable. |
| Spells | Fireball only. It damages other players caught in the blast (PvP rules are not designed yet); it now spares the caster's own pets. |
| Elements / knockback | Elemental bonuses are computed but never applied; the `knockback:` config is never read. |
| Defensive Magic | Disabled (needed ProtocolLib, which Folia can't use). |
| Admin commands | `/cerb mana`, `resetdata`, `worldgen`, `repairchunk`, `quest`, `event`, `timings`, `profiler`, `listentities`, `showqueues` are stubs. `config.yml` is never loaded. |
| Realm names | Players see tier names (Common, Rare…). The Western realm ladder (Mortal…Primordial, `NAMING.md`) is not in the UI yet. |

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

## Roadmap after that

1. **Gathering & crafting loop** — block-break XP for the 19 dead skills, capped yields, register recipes and wire the existing quality-crafting code. Fortune starts paying off here.
2. **Bestiary & zones as data** — mob families in `mobs.yml`, location → band/realm (`ZoneManager`), realm suppression; themed areas instead of plain distance.
3. **First weapons & gear slice** — ~20 registry items feeding attributes through a GearSource; the 20-slot equip layout.
4. **Ascension (realms)** — realm display names, breakthroughs and stages over the existing tiers; a RealmSource feeding attributes.
5. **PvP rules** — whether PvP should use attributes too (today it uses the old per-skill bonuses).

Each milestone ends with in-game checks and new playtest checks.

## Decisions that are yours

1. **Merging.** All work is on `claude/quirky-mccarthy-nhaz58`. Open a pull request into `master` once you've playtested, or keep working on the branch?
2. **PvP and attributes.** PvP is unchanged on purpose. Should PvP read attributes too (a balance decision)?
3. **Total mitigation cap.** The design caps resist + armor + defense bar at 75% combined. The existing defense pipeline already allows ~82% with full armor and a full bar, so enforcing 75% would make well-armored players take more damage. Enforce it, or keep today's behaviour? (`attributes.yml limits.total-mitigation` is reserved and not enforced yet.)
4. **Weapon Mastery stacking.** Weapon Mastery gives +1 Power per level to axes/maces/tridents/spears *and* +0.25 to every weapon, so at level 100 an axe hits x7.25 vs a level-100 mob's x5.95 HP. Keep (rewards the generalist skill) or drop the +0.25?
5. **Folia version.** 1.21.11 left PaperMC support on 2026-10-14 (current: 26.x). Recommend planning the upgrade now that Attributes v1 is in.
6. **Labels on all mobs.** Keep (current choice from June), or label hostiles only to save entities?
7. **`lib/DTR-1.0.0.jar` and `libs/Volor.jar`.** Now unused by the build. Delete them from this repo, or keep them as references?
