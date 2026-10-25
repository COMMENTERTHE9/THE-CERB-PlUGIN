# Claude Code Memory — Cerberus Plugin

## Project
Minecraft RPG plugin — cultivation / xianxia-flavored but **Western-named** — with a deep skill / combat / progression system. Currently mid-build: the full design is complete (`docs/design/`) and Step-0 foundation code is underway.

## Tech
- **Main class:** `cerberus.world.cerb.CerberusPlugin`
- **Platform:** **Folia 1.21.11** (region-threaded — entity/player ops must run on the owning region thread; no main thread; use `teleportAsync` for cross-region; **NO ProtocolLib**)
- **Java:** 21 (`maven.compiler.release=21`; no preview features). Any JDK **>= 21** builds it (21 and 25 verified) and Folia 1.21.11 runs on Java >= 21.
- **Build/run:** see `RESTART_GUIDE.md`. `run.bat` = build + back up plugin data + deploy + start; `status.bat` reports Java/server/worlds/running state. Logic lives in `tools/cerb.ps1` (Windows) and `tools/cerb.sh` (Linux/macOS). Tests: `.\mvnw.cmd test`.
- **DB:** SQLite, async via `AsyncSaveManager` (5 tables auto-create on startup)

## Design (read this first)
The full game design lives in **`docs/design/`** — start at **`docs/design/README.md`** (index + build-readiness status). The owner's current priorities and build order: **`docs/ROADMAP.md`** (performance first; multiclass ability trees with cross-class loadouts are the next big design area). Doctrine: **controlled hyperscaling** (cultivation-scale numbers into the billions/trillions, realm suppression) + **Western-forward naming** (`NAMING.md`: Mortal…Primordial realms layered over the 10-tier `Tier` enum). Everything new is a **data-driven registry** (mirrors `MobRegistry`): config, not code.

## Build status (master realm-base LOCKED at ×8; current state and owner decisions in `docs/STATUS.md`)
- ✅ XP-curve overflow fixed (`SkillManager.xpForLevel`, polynomial, unit-tested)
- ✅ Mob virtual-HP pool + ×8 realm health curve (`MobVirtualHealthManager`; mobs hold billions+; vanilla MAX_HEALTH is cosmetic only)
- ✅ First Aid runaway capped
- ✅ Repo hygiene (removed the `Creb/` nested duplicate; `target/` untracked)
- ✅ Playtest-readiness pass (2026-10-24): fresh-clone build, launcher scripts, kill-XP overflow + level cap, hotbar slot-9 item loss, rideable-mob labels, registry-mob spawn tier, particle cap, save coalescing, failed-load protection — each verified on a real Folia 1.21.11 server
- ✅ Admin Control Panel 2.0 (`GUIs/menu` framework + `GUIs/admin`; permission re-checked per click; `tools/playtest/admin.js`)
- ✅ Attributes & Progression 2.0, increments 1–7 (`docs/ATTRIBUTES_ENGINE.md`): kill XP v2, attribute engine + `attributes.yml`, Stats page, PvE offense, Vitality/Fortitude/Essence, admin inspector/simulator/dev buffs (`tools/playtest/progression.js`)
- ✅ Player Menu 2.0 (2026-10-25): hub + Skills / Stats / Ascension / Equipment / Bestiary / World / Guide on the shared menu framework (`GUIs/player/`, `tools/playtest/menu.js`); honest skill catalog (`GUIs/SkillDefinitions.java`); 4 display-name renames (`Skills/SkillNames.java`; internal names = DB keys, unchanged)
- ✅ Speed stats, attribute expansion increment 1 (2026-10-25): attack / bow draw / movement speed + spell cooldown in the engine, paused during PvP (`docs/design/ATTRIBUTE_EXPANSION.md`, `progression.js speed`)
- ✅ Owner decisions 2026-10-25: 75% damage-reduction cap on resistance × armor (`Manager/DefenseMath`; the defense bar is a pool, not counted), PvP plays as before Attributes 2.0 (`integration.pvp-defense: false`), Weapon Mastery stacking analysed (`WeaponMasteryStackingTest`); playtest list `docs/PLAYTEST_CHECKLIST.md`
- ⬜ Next: **`docs/ROADMAP.md`** Step 1 (performance baseline), then decisions A–E, Player Menu 2.1 animations, classes & abilities design → prototype

## Key files
- `cerberus/world/cerb/CerberusPlugin.java` — init / lifecycle
- `Skills/SkillManager.java` — per-player skills + XP curve (`xpForLevel`)
- `Manager/Tier.java` — the unified 10-tier ladder (= realms)
- `Manager/MobHealthScaling.java` · `Listener/MobScalingListener.java` · `Manager/MobVirtualHealthManager.java` — mob level/HP scaling
- `Manager/CombatConfig.java` + `src/main/resources/combat.yml` — combat tuning (mob-health-scaling, mob-damage-scaling, tiers)
- `Listener/VirtualHealthListener.java` — damage pipeline (player + mob virtual HP); `Manager/PlayerVirtualHealthManager.applyResolvedDamage` + `Manager/DefenseMath` — resistance → armor → 75% cap → defense bar, PvP-as-before; `Manager/PveOffense.java` — attributes on player→mob hits; `Manager/SecondaryEffects.java` + `Manager/PvpTag.java` — speed stats
- `GUIs/PlayerMenuGUI.java` — hotbar head, `/menu`, hub sections (a new destination = one `PlayerMenuSections.Section`); screens extend `GUIs/player/PlayerMenu`
- `Attributes/*` — attribute engine (pure calculator, cached per-player `AttributeService`, sources); `Manager/AttributeEffects.java` pushes Vitality/Fortitude/Essence into health/defense/mana
- `cerberus/world/cerb/DatabaseManager.java` — SQLite

## Conventions
- **Performance first:** every significant milestone starts by measuring a reproducible baseline for the systems it touches, fixing confirmed bottlenecks, and measuring again (`docs/ROADMAP.md`). No speculative rewrites.
- **Folia threading:** snapshot on the entity/region thread → async I/O → reschedule back. Never touch Bukkit entity/player APIs off-thread.
- New content ships as **config** (mobs.yml / combat.yml / *.yml + a registry), not code.
- Pure-function math (curves, formulas) must have **unit tests**; run `mvnw.cmd test` (any JDK >= 21).
- Verify gameplay changes on a running server, not just the build. In a cloud/Linux session, `tools/cerb.sh` can download Folia and run a local test server (see RESTART_GUIDE.md).
- Commit working increments; keep the tree clean.

## History / audits
- `audit/AUDIT_2026-06-08.md` — latest codebase audit.
- Removed for Folia: the region/protection system, DecentHolograms, ProtocolLib. (Old docs referencing Paper 1.21.1 / those systems are obsolete.)
