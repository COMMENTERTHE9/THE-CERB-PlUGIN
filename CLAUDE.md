# Claude Code Memory — Cerberus Plugin

## Project
Minecraft RPG plugin — cultivation / xianxia-flavored but **Western-named** — with a deep skill / combat / progression system. Currently mid-build: the full design is complete (`docs/design/`) and Step-0 foundation code is underway.

## Tech
- **Main class:** `cerberus.world.cerb.CerberusPlugin`
- **Platform:** **Folia 1.21.11** (region-threaded — entity/player ops must run on the owning region thread; no main thread; use `teleportAsync` for cross-region; **NO ProtocolLib**)
- **Java:** 21 with `--enable-preview`
- **Build:** Maven wrapper. `JAVA_HOME` MUST point to **JDK 21** (not 25), or `--enable-preview` fails:
  `$env:JAVA_HOME = "C:\Program Files\Eclipse Adoptium\jdk-21.0.9.10-hotspot"; .\mvnw.cmd test`
- **DB:** SQLite, async via `AsyncSaveManager` (5 tables auto-create on startup)

## Design (read this first)
The full game design lives in **`docs/design/`** — start at **`docs/design/README.md`** (index + build-readiness status). Doctrine: **controlled hyperscaling** (cultivation-scale numbers into the billions/trillions, realm suppression) + **Western-forward naming** (`NAMING.md`: Mortal…Primordial realms layered over the 10-tier `Tier` enum). Everything new is a **data-driven registry** (mirrors `MobRegistry`): config, not code.

## Build status — Step 0 in progress (master realm-base LOCKED at ×8)
- ✅ XP-curve overflow fixed (`SkillManager.xpForLevel`, polynomial, unit-tested)
- ✅ Mob virtual-HP pool + ×8 realm health curve (`MobVirtualHealthManager`; mobs hold billions+; vanilla MAX_HEALTH is cosmetic only)
- ✅ First Aid runaway capped
- ✅ Repo hygiene (removed the `Creb/` nested duplicate; `target/` untracked)
- ⬜ Next: Step 1–2 — the **Attribute stat-bus** (Vitality/Essence/Power/Fortitude/Agility/Fortune) + the constants treaty

## Key files
- `cerberus/world/cerb/CerberusPlugin.java` — init / lifecycle
- `Skills/SkillManager.java` — per-player skills + XP curve (`xpForLevel`)
- `Manager/Tier.java` — the unified 10-tier ladder (= realms)
- `Manager/MobHealthScaling.java` · `Listener/MobScalingListener.java` · `Manager/MobVirtualHealthManager.java` — mob level/HP scaling
- `Manager/CombatConfig.java` + `src/main/resources/combat.yml` — combat tuning (mob-health-scaling, mob-damage-scaling, tiers)
- `Listener/VirtualHealthListener.java` — damage pipeline (player + mob virtual HP)
- `cerberus/world/cerb/DatabaseManager.java` — SQLite

## Conventions
- **Folia threading:** snapshot on the entity/region thread → async I/O → reschedule back. Never touch Bukkit entity/player APIs off-thread.
- New content ships as **config** (mobs.yml / combat.yml / *.yml + a registry), not code.
- Pure-function math (curves, formulas) must have **unit tests**; run `mvnw.cmd test` (JDK 21).
- Commit working increments; keep the tree clean.

## History / audits
- `audit/AUDIT_2026-06-08.md` — latest codebase audit.
- Removed for Folia: the region/protection system, DecentHolograms, ProtocolLib. (Old docs referencing Paper 1.21.1 / those systems are obsolete.)
