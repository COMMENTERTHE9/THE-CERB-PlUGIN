# Claude Code Memory — Cerberus Plugin

## Project
Minecraft RPG plugin — cultivation / xianxia-flavored but **Western-named** — with a deep skill / combat / progression system. Currently mid-build: the full design is complete (`docs/design/`) and Step-0 foundation code is underway.

## Tech
- **Main class:** `cerberus.world.cerb.CerberusPlugin`
- **Platform:** **Folia 1.21.11** (region-threaded — entity/player ops must run on the owning region thread; no main thread; use `teleportAsync` for cross-region; **NO ProtocolLib**)
- **Java:** 21 (`maven.compiler.release=21`; no preview features). Any JDK **>= 21** builds it (21 and 25 verified) and Folia 1.21.11 runs on Java >= 21.
- **Build/run:** see `RESTART_GUIDE.md`. `run.bat` = build + back up plugin data + deploy + start; `status.bat` reports Java/server/worlds/running state. Logic lives in `tools/cerb.ps1` (Windows) and `tools/cerb.sh` (Linux/macOS). Tests: `.\mvnw.cmd test`.
- **DB:** SQLite, async via `AsyncSaveManager` (6 tables auto-create on startup; `player_classes` holds one versioned classes document per player)

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
- ✅ Step 1 performance pass (`docs/PERFORMANCE.md`): menus redraw in the open window (vanilla's advancement re-check per new window was ~60% of a click), item cache, HUD/labels/damage numbers; loads `tools/playtest/load.js` + profiler `tools/perf/profile.sh`
- ✅ Step 2, decisions A–E (`docs/STATUS.md`): PvP speed pause kept; powered shelves work (menu items lifted out for the swap tick); fire resistance per kind of fire (`combat.yml fire-resistance`); projectile hits train Survival/Heavy Armor; spears train Weapon Mastery. Admin/confirm screens always get a fresh window (`Menu.reuseWindow()`), fixing a stale-click bug from Step 1
- ✅ Step 3, Player Menu 2.1 (`docs/STATUS.md`): opening reveal, header sweep, page-turn sounds, filling skill bar, "since you last looked" progress (session memory, no DB), category tabs; engine `GUIs/menu/MenuAnimator` + `MenuView.later/soundLater` (bounded, cancelled on any screen change; `menu.js motion`)
- ✅ Step 4, classes & abilities design (`docs/design/CLASSES.md`; 8 owner decisions in its §10)
- ✅ Step 5, class prototype (`docs/STATUS.md`, `docs/design/CLASSES.md` §11): Warrior / Mage / Duelist on a data-driven ability engine (`Classes/*`, `classes.yml`, `abilities.yml`); cast keys = Sneak + right-click / left-click / F / Q / scroll; loadouts (5 actives, 3 passives, keystone; 3 per player); Player Menu → Classes & Abilities, `/class`, `/cerb class`; PvE-only until the PvP decision (`tools/playtest/classes.js`, `load.js abilities`)
- ⬜ Next: the owner's playtest + the decisions in `docs/STATUS.md`; then Step 6 (more classes, `CLASSES.md` §9) or the gathering/crafting loop, as the owner orders them

## Key files
- `cerberus/world/cerb/CerberusPlugin.java` — init / lifecycle
- `Skills/SkillManager.java` — per-player skills + XP curve (`xpForLevel`)
- `Manager/Tier.java` — the unified 10-tier ladder (= realms)
- `Manager/MobHealthScaling.java` · `Listener/MobScalingListener.java` · `Manager/MobVirtualHealthManager.java` — mob level/HP scaling
- `Manager/CombatConfig.java` + `src/main/resources/combat.yml` — combat tuning (mob-health-scaling, mob-damage-scaling, tiers)
- `Listener/VirtualHealthListener.java` — damage pipeline (player + mob virtual HP); `Manager/PlayerVirtualHealthManager.applyResolvedDamage` + `Manager/DefenseMath` — resistance → armor → 75% cap → defense bar, PvP-as-before; `Manager/PveOffense.java` — attributes on player→mob hits; `Manager/SecondaryEffects.java` + `Manager/PvpTag.java` — speed stats
- `GUIs/PlayerMenuGUI.java` — hotbar head, `/menu`, hub sections (a new destination = one `PlayerMenuSections.Section`); screens extend `GUIs/player/PlayerMenu`
- `Attributes/*` — attribute engine (pure calculator, cached per-player `AttributeService`, sources); `Manager/AttributeEffects.java` pushes Vitality/Fortitude/Essence into health/defense/mana
- `Classes/*` — classes & abilities: `ClassCatalog` (YAML → definitions, power bounds), `ClassService` (profiles, ranks, loadouts, switch rules, attribute source, saves), `AbilityCaster` (validates and runs the effect verbs through the normal damage pipeline), `AbilityListener` (the cast keys), `ClassProfile` (saved document, repaired on load); menus `GUIs/player/Classes*`, `LoadoutMenu`, `AbilityPickerMenu`
- `cerberus/world/cerb/DatabaseManager.java` — SQLite

## Conventions
- **Performance first:** every significant milestone starts by measuring a reproducible baseline for the systems it touches (`tools/playtest/load.js` + `tools/perf/profile.sh`, method in `docs/PERFORMANCE.md`), fixing confirmed bottlenecks, and measuring again. No speculative rewrites. Menus: change screens with `MenuManager.open/replace` (same-size screens redraw in place); never open raw inventories per click. A screen whose buttons change state at once returns `reuseWindow() = false` (all admin screens do) so stale clicks can't reach it.
- **Folia threading:** snapshot on the entity/region thread → async I/O → reschedule back. Never touch Bukkit entity/player APIs off-thread.
- New content ships as **config** (mobs.yml / combat.yml / *.yml + a registry), not code.
- Pure-function math (curves, formulas) must have **unit tests**; run `mvnw.cmd test` (any JDK >= 21).
- Verify gameplay changes on a running server, not just the build. In a cloud/Linux session, `tools/cerb.sh` can download Folia and run a local test server (see RESTART_GUIDE.md).
- Commit working increments; keep the tree clean.

## History / audits
- `audit/AUDIT_2026-06-08.md` — latest codebase audit.
- Removed for Folia: the region/protection system, DecentHolograms, ProtocolLib. (Old docs referencing Paper 1.21.1 / those systems are obsolete.)
