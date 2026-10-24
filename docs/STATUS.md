# Cerberus — Project Status

*Last updated 2026-10-24 (branch `claude/quirky-mccarthy-nhaz58`). Update this file when a milestone lands.*

**Evidence key.** **Played** = exercised on a real Folia 1.21.11 server with a scripted test client (cloud sandbox, not your PC). **Tested** = unit tests. **Read** = code inspection only. A green build alone proves nothing about gameplay.

## Summary

The foundation runs. The plugin loads cleanly on Folia 1.21.11 under Java 21 and 25, saves and reloads player progress, and survives restarts. The playable loop today is: fight mobs → earn skill XP → level up → see it in the Skills menu. **The loop breaks at "get stronger":** skill levels do not change how you fight mobs (Blade Mastery 1 and 100 kill a zombie in the same 10 swings — Played). Most of the designed game (realms, weapons, gear, talismans, mounts, families, crafting) exists only in `docs/design/`.

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
| Test content | `/givetestitem kit`; Fireball Wand (20 mana, regenerates); `/cerb spawn <mobId>`; `/spawncustomzombie`; `/testregen`; `/cerb healthstate`, `/cerb combatdebug`. |

## Incomplete (Read unless noted)

| Area | State |
|---|---|
| **Skill effects** | Leveling barely changes play. Combat-skill bonuses (Blade/Weapon Mastery, crits, lifesteal) apply only in PvP; against mobs the raw hit is used (Played). Most `applyEffect` code reads one level shared by the whole server, not yours. |
| **XP sources** | Only combat, spells, damage taken, and new-chunk discovery grant XP. Mining, Woodcutting, Farming, Fishing, Crafting, Smithing, Alchemy, Enchanting, Herbalism, Cooking and 9 more have **no XP source**. |
| **Kill XP size** | Now bounded (40 × mob multiplier), but flat: a level-80 mob pays the same as a level-1 mob. The design's HP-based formula no longer fits billions-scale HP pools (decision below). |
| Attributes | No stat bus. Strength / Luck / Magic Find / Yield managers exist but nothing reachable calls them. Mana is fixed at 1000 / 50 per second. |
| Crafting & traps | ~600 lines of quality-crafting and trap code, but no recipes are registered and the trap listener is never registered: unreachable. |
| Spells | Fireball only. It damages other players caught in the blast (PvP rules are not designed yet); it now spares the caster's own pets. |
| Elements / knockback | Elemental bonuses are computed but never applied; the `knockback:` config is never read. |
| Defensive Magic | Disabled (needed ProtocolLib, which Folia can't use). |
| Admin commands | `/cerb skill`, `mana`, `resetdata`, `worldgen`, `repairchunk`, `quest`, `event`, `timings`, `profiler`, `listentities`, `showqueues` are stubs. `config.yml` is never loaded. |
| Realm names | Players see tier names (Common, Rare…). The Western realm ladder (Mortal…Primordial, `NAMING.md`) is not in the UI yet. |

## Known defects still open

| Defect | Impact |
|---|---|
| Labels on *every* mob (`apply-to: ALL`): each fish, bat and villager gets an extra entity | Performance on busy servers; mob pools are also kept for a whole session after chunk unload |
| Void kills of high-tier mobs take minutes (void damage drains the pool slowly) | Cosmetic / odd |
| Custom-ID race when a brand-new player's first saves overlap (suspected) | Progress could save under an orphaned ID |
| SQLite opens a connection per call, no WAL (suspected) | "database is locked" under load |
| Same-day session logs overwrite each other | Debugging only |
| `/cerb speed` and `/cerb spawn` from the console throw (they assume a player) | Admin annoyance |

## Fixed and verified on 2026-10-24

Fresh-clone build (dead DTR/Volor deps) and any JDK ≥ 21 · launcher scripts with prerequisite checks and backups · **kill XP of ~500,000,000 per kill** (wrapped negative after ~5 kills) plus the level cap and load-time repair of corrupted values · **hit-particle burst that would hang clients** on `/kill` or big hits · **item in hotbar slot 9 deleted on every join** · labels blocking horse/pig/strider riding and taming · registry mobs (`/cerb spawn`, High Health Zombie, Sea Monster) spawning as common mobs · menu buttons that did nothing · free-XP Upgrade button (now admin-only) · spell XP on failed casts · per-tick XP and "BLOCKED" text in fire/lava · per-hit log spam · dropped saves · empty-data overwrite after a failed load · several Folia thread-safety issues. Details: `git log 031a6b4..HEAD`. Regression check: `tools/playtest/smoke.js` (9/9; the pre-fix build fails 3).

## Recommended next milestone — "Progression that matters" (Attributes v1)

**Player-facing result.** Leveling combat skills makes you visibly stronger against mobs: more damage and crit, more HP, better defense. Kill XP grows with mob level and tier, so walking away from spawn becomes the way to progress. A *Stats* page (the current "General" button) shows your attributes.

**Why this is next.** It repairs the broken step of the only playable loop, and it is the locked next step of the plan (`SYSTEMS_BASE_LAYER.md` §4, Step 1–2). Every later system — gear, talismans, realms, mounts — is designed to feed this layer, so building those first would repeat today's problem: content that can't affect combat.

**Changes, as small reviewable steps:**
1. Constants block in `combat.yml`: attribute caps and the kill-XP formula by mob level × tier (pure function + tests).
2. `PlayerAttributes`: per-player Vitality / Essence / Power / Fortitude / Agility / Fortune derived from skill levels (+ realm base), clamped at the caps (pure function + tests).
3. Mob damage path: apply the attacker's Power/crit **before** the hit drains the mob's pool (today the bonus is computed and thrown away).
4. Vitality → max HP, Fortitude → defense, Essence → mana max/regen; recompute on level-up, join, and gear change.
5. Replace shared-level `applyEffect` paths (starting with Navigation speed) with per-player attributes.
6. Stats page; a working `/cerb skill <player> <skill> <level>` admin command for testing.
7. Smoke-test checks for each.

**How we verify it in-game.** `/cerb skill <you> "Blade Mastery" 50` → a level-1 zombie dies in clearly fewer swings than at level 1; the Stats page shows the new numbers; a level-10 mob far from spawn pays more XP than a level-1 mob; HP above 100 after Vitality grows; everything survives a restart.

## Roadmap after that

1. **Progression that matters** — Attributes v1 + XP treaty (above).
2. **Gathering & crafting loop** — block-break XP for the 19 dead skills, capped yields, register recipes and wire the existing quality-crafting code.
3. **Bestiary & zones as data** — mob families in `mobs.yml`, location → band/realm (`ZoneManager`), realm suppression; themed areas instead of plain distance.
4. **First weapons & gear slice** — ~20 registry items feeding attributes; the 20-slot equip layout.
5. **Ascension (realms)** — realm display names, breakthroughs and stages over the existing tiers.

Each milestone ends with in-game checks and new smoke-test checks.

## Decisions that are yours

1. **Attributes: derived-only or manual points?** Recommend derived from skills + realm for v1; a point-allocation layer can sit on top later without rework.
2. **Kill-XP formula.** Recommend XP by mob level × tier (keeping the design's ~300–600 XP for a level-50 Rare), instead of by HP, which is now in the billions.
3. **Merging.** Today's work is on `claude/quirky-mccarthy-nhaz58`. Open a pull request into `master`, or keep working on the branch?
4. **Folia version.** 1.21.11 left PaperMC support on 2026-10-14 (current: 26.x). Recommend staying until Attributes v1 lands, then planning the upgrade.
5. **Labels on all mobs.** Keep (current choice from June), or label hostiles only to save entities?
6. **`lib/DTR-1.0.0.jar` and `libs/Volor.jar`.** Now unused by the build. Delete them from this repo, or keep them as references?
