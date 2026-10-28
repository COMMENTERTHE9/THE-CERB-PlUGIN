# Performance: baseline, findings, changes

*Step 1 of `docs/ROADMAP.md` (2026-10-25). Every later milestone starts by re-running the same measurements for the systems it touches.*

## How to measure (reproducible)

| Tool | What it does |
|---|---|
| `tools/playtest/load.js <bots> <seconds> <mode>` | Bots load a local sandbox server, all next to world spawn so they share one Folia region (the worst case: one region thread does everything). Modes: `combat` (each bot hits its own no-AI level-50 zombie), `hurt` (an AI zombie hits each bot in survival), `menu` (fast Player Menu browsing: hub → Skills → Combat → Blade Mastery → next → back → home → Stats → back → close), `mixed`, `idle`. |
| `tools/perf/profile.sh <seconds>` | Java Flight Recorder on the running server (1 ms CPU sampling), summarised by `jfr_summary.py`: Cerberus's share of region-thread time, the Cerberus frames that cost the most, and allocations. Runs locally; nothing is uploaded. |
| Folia `tps` (server console) | Region MSPT (milliseconds per tick of the 50 ms budget) and utilisation. |

**How to read the numbers.**
- JFR samples only Java code, so GC and native time are missing and its "ms per tick" understates the real cost. It is used to compare runs and rank Cerberus's own costs.
- Folia's MSPT is the whole server (vanilla included). It varies by about ±0.5 ms between runs and is higher for a few minutes after a restart (JIT warm-up). Compare warm runs only.

**Setup:** sandbox Folia 1.21.11, 4 CPUs, `-Xmx3G`, 9 bots plus 1 controller, 60 s recordings, the same world and the same spawn area for every run.

## Baseline (before)

| Workload (9 bots, 60 s) | Server MSPT | Cerberus, ms per tick | Cerberus's biggest costs |
|---|---|---|---|
| Idle (standing) | 4.9–5.2 | 0.04 | HUD task, mob labels |
| Combat (~16 hits/s) | 6.0 | 0.15 | mob labels, damage numbers, HUD |
| Being hit (~9 hits/s) | 6.1 | 0.19 | HUD (formatting + sending), damage numbers |
| Menu browsing (~27 clicks/s) | 6.7–7.1 | **0.51** | opening windows, building menu items |

The server is far from its limit (6–7 ms of the 50 ms budget) and Cerberus is a small part of that. Menu browsing stands out, and Step 3 (menu animations) will add more menu work.

## What was found

1. **Menus: one click ≈ one new window, and vanilla pays for it.**
   - Each screen change opened a new inventory window. Vanilla then re-runs its `inventory_changed` advancement checks (every recipe advancement's item test) over the whole player inventory. That was **~60% of a menu click's server time**.
   - Building the items took most of the rest. Bukkit converts each legacy lore line with a regular expression (it also looks for links).
2. **HUD.**
   - `String.format("%,d")` for six numbers per update built a new `DecimalFormat` every call.
   - The action bar went through Spigot's chat API, which round-trips the text through JSON.
   - It was rebuilt on every hit.
3. **Mob labels.** Each hit re-scans the mob's passengers and rebuilds the whole label. Most of what's left is Paper's own text conversion (~0.045 ms per label update): inherent.
4. **Floating damage numbers.** Each number ran its own per-tick task for 20–30 ticks, teleported every tick, and blinked (hide/show for everyone) near the end.
5. **Persistence.** Every save opens a new SQLite connection. It runs off the tick thread and is ~1–2% of the server's allocations: not a bottleneck today. The known "database is locked" risk under simultaneous saves remains.
6. **Fine already.**
   - Attribute snapshots are cached: browsing and hits rebuild none (`menu.js perf`).
   - Per-player background work is ~0.004 ms per tick per player.

## What changed

| Change | Where |
|---|---|
| Moving to another screen of the same size redraws the open window and retitles it (`InventoryView.setTitle`), instead of opening a new window. A click that arrives within the player's ping after a switch was aimed at the previous screen and is ignored. A new window used to drop such clicks by window id. | `GUIs/menu/MenuManager`, `MenuHolder` |
| Finished menu items are cached by (material, name, lore). Only copies are handed out, and the cache is bounded at 2,048 entries. | `GUIs/menu/Ui.item` |
| HUD numbers are grouped without a formatter (same text, unit-tested against `String.format`). The bar is sent with Paper's action-bar API. An unchanged bar is not resent within 0.9 s; the HUD task still refreshes it every second. | `Manager/Numbers.grouped`, `PlayerHUDManager` |
| Labels keep their entity and the fixed "Lv50 Zombie" part. A hit that doesn't change the shown HP text ("3.2K") sends nothing. | `Manager/MobLabelManager` |
| Damage numbers are animated by the client (display interpolation): two updates per number. They rise, then settle and shrink away; this replaces the per-tick teleports and the blinking. Crits still rise higher and sway. | `Listener/DamageDisplayListener` |
| The defense update that runs on every hit reads each skill level once. | `Manager/PlayerDefenseManager` |

## After (same workloads, warm server, two runs)

| Workload | Cerberus, ms per tick: before → after | Server MSPT after |
|---|---|---|
| Menu browsing | 0.513 → **0.159–0.172 (−66 to −69%)** | 5.5–7.0 |
| Combat | 0.145 → **0.092–0.121 (−17 to −37%)** | 5.6–6.2 |
| Being hit | 0.193 → **0.136–0.152 (−21 to −30%)** | 5.8–6.3 |
| Idle | 0.039 → 0.021–0.033 | 4.7–5.4 |

- **Same work in every run:** 225 clicks per menu bot, ~110 hits per combat bot. The second being-hit run took twice as many hits and still cost less.
- **Noise:** the menu gain is far beyond it. The combat and being-hit gains are real but modest and vary between runs (the JFR figure moves by ±25% for the same load).
- **Gameplay:** every regression check passed on the optimized build (`docs/STATUS.md`).

## Measured and deliberately left alone (for now)

- **SQLite connection per save:** off the tick and small; a pooled connection becomes worthwhile with more players or more frequent saves.
- **Label text conversion:** inherent per update. If many players hit one boss, coalesce updates per mob (at most one per tick).
- **The per-hit defense refresh** could become event-driven (armor change, level-up), but it costs little.
- **The combo popup** spawns a text entity per hit after the third hit. Small; throttle it if combat entities grow.
- **Player-head menu items** look up the skin profile; Paper caches it, but watch it on new players.

## Review follow-up: admin screens get their own window

An independent review of this work found that redrawing screens in place had weakened one protection. A new window used to drop every click aimed at the previous screen, exactly. In place, a click sent just before the new screen arrived could land on it: a lagging admin double-clicking a skill could press "+10 levels" on the editor (reproduced: 3 of 4 such clicks applied it). Now:
- admin and confirm screens, whose buttons change something at once, always open in a fresh window (`Menu.reuseWindow()`), so that exact protection is back (0 of 4, `admin.js staleclick`);
- read-only Player Menu screens keep the fast redraw, and ignore clicks for about one round trip after a switch (ping + 150 ms, 250 ms to 1 s; it was ping + 50 ms).

The `menu` workload is unchanged (Player Menu only; 225 clicks per bot, none dropped).

## Step 2 check (decisions A–E, 2026-10-25)

Same loads, warm server, two runs each. Cerberus, ms per tick:

| Workload | Step 1 after | Step 2 |
|---|---|---|
| Combat | 0.092–0.121 | 0.111–0.141 |
| Being hit | 0.136–0.152 | 0.142–0.158 |
| Menu browsing | 0.159–0.172 | 0.152 |

Within the budget. None of the Step 2 code appears among the costly frames: the fire-coverage lookup, the projectile XP rule, the shelf swap and the spear rule run only on their own events. The same functions as before lead (mob labels, damage numbers, HUD), and the spread matches the run-to-run noise above.

## Step 3: Player Menu 2.1 (2026-10-25)

New load mode `menuidle`: each bot opens the Player Menu once and leaves it open.

**1. Baseline (Step 2 build).**
- Menu browsing: 0.144 ms per tick.
- Idle open menu: 0.028 ms per tick, the same as idle players.

**2. Found.** The two biggest Cerberus allocators while browsing were avoidable:
- `Fmt.grouped` built a `String.format` formatter per number: 26–38 MB per 60 s run;
- `ConfigSkillMapper.describeItems` compiled two regexes for every skill tile: 13–17 MB.

**3. Fixed** (commit 4e880cd). The number formatter now matches `String.format` without building one (unit-tested). The regexes are compiled once, and each skill's text is cached until a reload.

**4. Measured again.** CPU was unchanged (0.146 ms per tick, within noise). Cerberus's allocations fell from 2.70 to 2.30 MB/s (−15%), and both hot spots are gone from the list. A small gain, but it is free.

**5. Built the animations** (`MenuAnimator`). Each runs on the viewer's entity scheduler, is cancelled on any screen change, close or quit, and ends within 1 s.

| Workload | Before (Step 2 build) | With Player Menu 2.1 | Budget |
|---|---|---|---|
| Menu browsing (9 bots, 225 clicks each) | 0.144 | 0.158–0.172 | ≤ ~0.25 |
| Idle open menu (9 bots) | 0.028 | 0.036 (the profile shows no menu work: HUD, labels and regeneration only) | nothing measurable |

The animations cost about 0.02 ms per tick at 27 clicks per second, mostly the header sweep's slot updates. They stay within the budget, and the click itself only schedules them.

## Step 5: classes and abilities (2026-10-25)

New load mode `abilities`: the `combat` bots, as Warrior/Duelist with a loadout, press their cast keys on a rhythm while fighting (Sneak + attack = Cleave, Sneak + right-click = Execute, Sneak + F = Flurry, Sneak + Q = Feint, plain hits in between). Many presses land on a cooldown, as in play. Same setup as before: 9 bots, 60 s, warm server, a warm-up run first.

**1. Players who don't cast pay nothing.** `combat` on the build before classes and on the classes build, same day, same work (106–110 hits per bot):

| Workload | Before classes | With classes |
|---|---|---|
| Combat, Cerberus ms per tick | 0.159 / 0.133 | 0.148 / 0.134 |
| Server MSPT | 7.1–7.3 | 7.0–7.3 |

The difference is noise. Class code appears in 1 of ~4,200 samples per run: the cast keys, the combat marking and the counter and Feint checks cost nothing measurable.

**2. Casting.** In the `abilities` load, 647 casts went off over two runs, about 5 per second server-wide. Class code was on the stack in 37–50 samples per run (each sample is about 1 ms of one thread):

| Part | Samples per run | Note |
|---|---|---|
| Hit pipeline | 11–18 | The ability's strikes go through the same damage pipeline as a weapon hit: the mob's HP pool, its label, damage numbers. Same cost per hit. |
| Building the save document | 14–20 | The largest class-specific cost, already limited to one save per player per second (review finding). |
| Class logic | 12 | Keys, checks, targeting, cooldowns, class XP: about 0.04 ms per press, within the 0.05 ms budget. |

Casting doesn't rebuild attribute snapshots: 132 rebuilds since the restart, from joins and passive equips, against 647 casts.

**3. Fixed.**
- The save document is now built on the class writer thread. The player's thread only copies the profile, a few small maps; this follows the project's snapshot, then asynchronous I/O rule.
- The cooldown message ("Cleave: 2.1 s") no longer builds a `String.format` formatter. Pressing a key on cooldown is the most common failed press.

**4. Measured again** (same `abilities` load, two runs, class samples split by thread):

| | Class samples on region threads | ...building the save document there | Save document on the writer thread |
|---|---|---|---|
| Before the change | 47 / 35 | 20 / 14 | 0 |
| After | 29 / 21 | 0 | 17 / 13 |

Class work on the region threads fell by about 40%, because the document moved entirely off the tick. Class logic alone (keys, checks, targeting, cooldowns, XP) is about 0.02–0.04 ms of region time per key press. Cerberus's total for the load stays in the run-to-run noise (0.09–0.13 ms per tick; MSPT 6.5–7.6), dominated as before by mob labels and damage numbers.

Within the budget: plain combat is unchanged, and casting costs about what the hits it deals cost.

## Phases 1–3: ability controls, audit, class polish (2026-10-25)

**Method:** as for Step 5. The `abilities` load (9 bots, 60 s, warm server, a warm-up run first, two measured runs with a 50 s JFR recording each) now presses the quick keys:
- F: Cleave;
- right-click: Execute;
- Q: Flurry;
- Sneak + right-click: Feint.

Plain hits fall in between. The `combat` load shows what players who don't cast pay. Class code is counted per run from the recorded stacks (`Classes.*` on a region thread).

| | Before (Step 5 controls) | After (quick keys, feedback, bar, review fixes) |
|---|---|---|
| `abilities`: Cerberus on the region threads | 53 / 24 samples (0.053 / 0.024 ms per tick) | 53 / 38 samples (0.053 / 0.038 ms per tick) |
| ...of which class code | 8 / 1 | 5 / 5 |
| Key presses / casts per run | about 306 / not counted | about 322 / 171 and 166 |
| `combat` (nobody casts): Cerberus | | 42 / 41 samples (0.041–0.042 ms per tick) |
| ...of which class code | | 5 / 0 |

- **Within the run-to-run spread.** The baseline's two runs differed by more than before differs from after.
- **Class logic is about 0.016 ms per key press:** 5 samples over about 322 presses, well inside the 0.05 ms budget. The extra checks before a cast (target, room, Momentum) don't show.
- **The new parts cost nothing measurable.** The cast feedback, the HUD message slot and the ability bar appear in 0 samples under the `abilities` load, and the bar in 3 under `combat`. While shown, the bar redraws twice a second per player; hidden, it runs no task.
- **A refused cast is cheaper than a cast.** The checks run before anything is spent, so a refusal costs the checks and one message.

## Gameplay Depth: gathering, crafting and the XP display (2026-10-25)

**Method, corrected.** 9 bots, an 80 s load, a warm-up run first, then two measured runs per load. Each measured run records 60 s of JFR **starting 10 s after the load phase begins** ("LOAD START": the bots have all joined by then), and Folia's `tps` is read halfway through (`$SP/perf-gd.sh` in the session; the same steps by hand: start `load.js`, wait for "LOAD START", wait 10 s, `tools/perf/profile.sh 60`). Two new loads in `load.js`:
- `gather`: each bot strip-mines its own block of stone with coal, iron, copper and gold ore in survival (a netherite pickaxe and Haste II: about 1.7 blocks a second per bot, picking up its drops), refilled when mined out;
- `craft`: each bot crafts iron pickaxes at its own crafting table as fast as a hurried player (about 1.4 a second).

**Two corrections to earlier sections:**
- **The Phase 1–3 recordings above mostly measured bots joining.** Their JFR started 8 s after `load.js` was launched, but the bots take about 40 s to join, so most of each 50 s recording was joins and only the last seconds were load. Their "0.024–0.053 ms per tick" for `abilities` and "0.041–0.042" for `combat` are therefore too low, and their before/after comparison says nothing. Re-measured correctly on 2026-10-25 (the same build, before the gathering code), `abilities` costs 0.113–0.122 ms per tick and `combat` 0.122, in line with Step 5's 0.09–0.13 and 0.13–0.16. Nothing was over budget: class code on the region threads is 23–25 samples per run, of which 8–12 are ability strikes going through the hit pipeline (a hit costs the same as a weapon hit) and 5–6 the ability bar; class logic itself comes to about 0.04 ms per key press, inside the 0.05 ms budget. (Phase 1–3's "0.016 ms per press" was taken from the flawed recordings.)
- **`tools/perf/jfr_summary.py` didn't count class code.** Its package list lacked `Classes.`, so a cast key handled directly by `Classes.AbilityListener` wasn't counted as Cerberus time. It now includes `Classes.` and `Gathering.`, prints a per-package line for the region threads, and every number in this section comes from the corrected script (the saved baseline recordings were summarised again).

**Before** (the build before the gathering code, a47ca6e; TPS 20.00 on every region in every run):

| Load | Cerberus, ms per tick (two runs) | Work per bot |
|---|---|---|
| `gather` | 0.037 / 0.035 | 137 blocks in 80 s (127 stone, 10 ore) |
| `craft` | 0.047 / 0.055 | 104–118 iron pickaxes in 80 s |
| `abilities` | 0.122 / 0.113 | 50 key presses, 49 hits |
| `combat` | 0.122 / 0.122 | 107–110 hits |

**After** (TPS 20.00 on every region in every run, and the same work per bot as before):

| Load | Before | After, first pass (b7be382) | After the fixes (7907fbc) |
|---|---|---|---|
| `gather` | 0.037 / 0.035 | 0.084 / 0.039 | 0.059 / 0.043 |
| `craft` | 0.047 / 0.055 | 0.108 / 0.099 | 0.070 / 0.058 |
| `abilities` | 0.122 / 0.113 | 0.172 / 0.131 | 0.177 / 0.107; two more on the warm server: 0.092 / 0.097 |
| `combat` | 0.122 / 0.122 | 0.122 / 0.095 | 0.109 / 0.105 |

**What the first pass found, and what was fixed** (`tools/perf/jfr_summary.py`, then a breakdown of what ran under each handler):
- **Crafting cost twice the baseline.** The forge listener's grid handler spent 34 of its 36 samples in `PrepareItemCraftEvent.getRecipe`, which builds a whole Bukkit recipe on every change to any crafting grid. The armed batch is now checked first, so the lookup only runs inside a shift-click batch. Forge traces are built only for a player with the debug on, and the smelted-metal keys are cached. Craft: 0.108 / 0.099 → 0.070 / 0.058.
- **The XP display's cost was `String.format`.** Nearly all of its samples (19 of 20 under a skill award in `gather`, all 5 in `abilities`) were formatter and locale setup in `XpLine.grouped`, once per gain. It now uses `Numbers.grouped`, which `NumbersTest` pins to the same output.

**What stays, and why that is fine:**
- **A block broken costs about 10–20 µs** of plugin time: 9 bots broke about 925 blocks in each 60 s recording, with 9–14 samples under the gathering listener and 5–6 under the XP display.
- **A craft click costs about 15–20 µs:** 13–25 samples for about 760 crafts. Most of it is the quality roll and the smelted-metal check on the item made.
- **Nothing runs per tick, and nothing touches the database per block.** Skill saves stay coalesced: at most one asynchronous save per player per second, as before.
- **The rest of the gap is in code this milestone didn't touch.** Mob labels for natural mobs near the bots take 20–32 samples in a `gather` run, and damage numbers and ability code vary by run. In `abilities` the new code is barely on the stack: gathering 0 samples, the XP display 2–6. The high first `abilities` runs are warm-up, not new cost. Both "after" sets were recorded soon after a server restart, with no ability cast in between, so their first `abilities` run was the first time the ability code ran since the start. That run measured 0.172 and 0.177. The baseline's server had run about 20 minutes of class and ability tests before its recording (the server log: `/class equip` 153 times, `/cerb class` 85). Two more runs on the warm server measured 0.092 and 0.097, below the baseline. **Method note for next time:** add each load to the warm-up, not only `gather`.

## Progression Depth: XP integrity and level-ups (2026-10-26)

**Method:** the corrected one above (9 bots, 80 s load, a warm-up run of every load, two measured runs of 60 s JFR from 10 s after "LOAD START", `tps` halfway). Two loads are new in `load.js`:
- `levels`: each bot mines diamond ore with its Mining set back to level 1 every 12 s, so it levels up again and again from real XP. That is many level-ups at once across the server, the presentation's workload.
- `wood`: each bot chops a small grove. It is the baseline for Woodcutting, which comes next.

**Before:** the build before the XP-integrity work (1f1ed75). **After:** XP integrity and the level-up presentation (the build committed with this section). TPS 20.00 on every region in every run, except one 19.67 reading in the first `abilities` run (the warm-up effect described above).

| Load | Before, ms per tick (two runs) | After | Work per bot (both) |
|---|---|---|---|
| `levels` | 0.025 / 0.024 | 0.024 / 0.037 | 93 diamond ores, 6 resets |
| `gather` | 0.022 / 0.020 | 0.018 / 0.022 | 127 stone, 10 ore |
| `combat` | 0.113 / 0.114 | 0.095 / 0.087 | 110 hits |
| `abilities` | 0.120 / 0.083 | 0.120 / 0.090 | 50 presses, 49 hits |

- **The presentation costs nothing measurable.** Across the `levels` runs, its frames (`Celebrations.present`, `flush`, `sound`, `LevelUps`) were 2–5 samples per 60 s run, while 9 players levelled Mining about every 2 s each. The second run's higher figure comes from the HUD redraw and the mana and health managers, which vary from run to run.
- **XP integrity costs nothing measurable either.** `combat` is slightly lower than before, `abilities` the same.
- **A first attempt was confounded:** about 400 mobs had built up near spawn over earlier test sessions, with mob spawning on. That run measured mob labels and damage numbers, which the baseline never saw (`levels` 0.082 / 0.057). The runs above were taken after clearing them, with mob spawning off, as in the baseline. **Method note:** clear mobs near spawn and turn mob spawning off before every recording.

## Progression Depth: Woodcutting (2026-10-26)

**Method:** as above (9 bots, 80 s load, a warm-up run of every load, two 60 s JFR runs from 10 s after "LOAD START", `tps` halfway), with mobs near spawn cleared and mob spawning off before each set. One load is new in `load.js`:
- `felling`: each bot at Woodcutting 25, with Sneak held, chops three 6-log oaks. A cut fells its trunk when Felling is ready: one tree scan, one step of 5 logs, their drops merged, a protection event per log. Single logs fall during the rest, and the grove is refilled when it is down.

**Before:** the build before the Woodcutting code (d6213c3), measured in the same session. There, `felling` is the same work with no Felling: each sneaking cut takes one log, and logs pay nothing. **After:** the build committed with this section.

TPS was 19.93–20.00 in every run.

| Load | Before, ms per tick (two runs) | After | Work per bot in 80 s |
|---|---|---|---|
| `wood` | 0.019 / 0.021 | 0.054 / 0.020; two more runs: 0.019 / 0.023 | 142 logs, both builds |
| `felling` | 0.013 / 0.017 | 0.079 / 0.051 | before: 121 logs, one per cut; after: 48 fellings and 98 cuts, 338 logs |
| `gather` | 0.019 / 0.022 | 0.019 / 0.019 | 127 stone, 10 ore |
| `craft` (each set right after a restart) | 0.055 / 0.036 | 0.066 / 0.050 | 116–121 iron pickaxes |

- **Cutting wood by hand costs what it did,** although logs now pay XP. Three of the four `wood` runs match the old build; the first measured run (0.054) didn't repeat. What is new per log: the XP, the XP display's HUD line, and a coalesced skill save on an async thread.
- **Felling: 0.05–0.08 ms per tick with 9 players felling non-stop,** about 5 trees a second across the server and 2.8 times the logs per minute of the old build. Per log that is about what cutting by hand costs. In the profile:
  - the felling's own steps take 8–13 samples a minute, the tree scans 3–4;
  - the XP display's HUD line takes 12–16;
  - there is no database work on region threads (one sample).
- **Mining is unchanged** (`gather`).
- **Crafting:** the upgrade listener now looks at a grid's materials before anything else, so ordinary crafting never pays for a recipe lookup. Its own cost is 3–5 samples a minute. The rest of the gap is in code this loop didn't touch (the HUD redraw, the forge listener) and is about the spread between two runs of one build (0.055 vs 0.036 before).
- **A first `craft` set taken after an hour of tests** read 0.076 / 0.053, with twice the region-thread samples of a fresh server. **Method note:** restart before each set, as for mobs and mob spawning.

## Grown trees and Smithing's wear resistance (2026-10-26)

**What it adds to frequent events:** every log or ore broken now reads its origin from the chunk's marks (up to three binary searches in small sorted arrays, from the same per-chunk cache); a tree scan does the same per log; every point of wear on a tool, weapon or armour piece goes through `SecondaryEffects.onItemDamage` (one cached-snapshot lookup, one roll). Growth marks are written when a sapling grows (one region task a tick later for random-tick growth).

**Method:** as above (9 bots, 80 s load, a warm-up per load, two 60 s JFR runs from 10 s after "LOAD START"), mobs cleared and spawning off, each build right after a restart. **Before:** the Woodcutting build (d116bb0) with its own shipped files. **After:** the build committed with this section. TPS 19.9–20.0 throughout.

| Load | Before, ms per tick (two runs) | After | Work per bot in 80 s |
|---|---|---|---|
| `gather` | 0.039 / 0.015 | 0.030 / 0.017 | 127 stone, 10 ore (a pickaxe's wear per block) |
| `wood`, first comparison | 0.029 / 0.018 | 0.052 / 0.024; two more: 0.056 / 0.051 | 142 logs |
| `wood`, the builds alternated (before, after, before, after), bots from Woodcutting 1 every run | 0.060 / 0.036, 0.078 / 0.033 | 0.048 / 0.042, 0.051 / 0.038 | 141 logs |
| `felling` | 0.049 / 0.052 | 0.056 / 0.048 | 46–48 fellings, about 97 cuts |
| `combat` | 0.102 / 0.083 | 0.105 / 0.082 | 110 hits (a sword's wear per hit) |

- **The new code barely shows in the profile:** `onItemDamage` 0–1 samples per 60 s run, the origin lookups (`PlacedBlocks.marks`, `pack`) 0–1, while 9 bots mined, chopped or fought non-stop. Tree scans are 6–7 samples a run against 5–6 before, and the felling steps the same (7–12 on both builds).
- **`wood` first looked slower after the change** (0.05 against 0.02–0.03). The extra samples were in the skill saves, the XP display's HUD line and the level-up path, none of which this change touches. The cause was the test, not the build: `load.js` didn't set the bots' Woodcutting level, so the two sessions ran at different levels, with different numbers of level-ups and XP lines. With every run starting at Woodcutting 1 (`load.js` now does this) and the builds alternated on fresh restarts, the change reads the same or lower (0.038–0.051 against 0.033–0.078). **Method note:** a load that pays XP must set the skill level it starts from.

## Fishing (2026-10-27)

**What it adds to frequent events:**
- **A cast** reads three secondary stats and the Fortune chance from the cached attribute snapshot and keeps one small record (one per player).
- **A bite** reads the bobber's block (biome, sky light) and rolls a species from a short list.
- **A catch** pays its XP (the XP display's HUD line and a coalesced skill save, as every gain does) and writes the player's journal to their data when it changed (a few hundred characters). Fortune's extra fish go into the caught stack.
- **A called creature** runs one check a second on its own region thread.

Nothing else runs per tick, and nothing touches the database per catch.

**Method:** as above (9 bots, 80 s load, a warm-up, two 60 s JFR runs from 10 s after "LOAD START"), mobs cleared and spawning off, each set right after a restart, the builds alternated (before, after, before, after). The new `fishing` load: each bot fishes its own 7×7 pond with a Lure III rod at Fishing 25, in survival, as an AFK farm would. **Before:** the build before Fishing (8192b67, with the old Sea Monster). **After:** the build committed with this section. TPS 20.0 in every run (1.9–2.6 MSPT for the region).

| Load | Before, ms per tick (two runs each set) | After | Catches in 80 s (9 bots) |
|---|---|---|---|
| `fishing`, first sets | 0.016 / 0.011 | 0.017 / 0.015 | before 58 / 56, after 55 / 71 |
| `fishing`, second sets | 0.033 / 0.018 | 0.018 / 0.015 | before 45 / 53, after 45 / 60 |

- **Fishing costs what the old build did.** Every run is within the spread between two runs of one build (0.011–0.033 before). The after build pays XP, rolls species and writes journals on every catch, and its bots catch a little more (lure speed and Quick Bite).
- **Fishing's own code** (`Gathering.Fishing`, `FishingRules`) is 3–4 of about 1,250 region-thread samples per 60 s run: the cast, the bite's roll, the catch, `moment`. The HUD redraw, which every load shares, is still the largest Cerberus frame. Database saves stay on async threads.
- **Method note:** in this load four of the nine bots miss some casts ("Fishing cancelled") on both builds. It comes from the lanes' layout in `load.js`, not from either build, so compare a set's per-tick time and its catches together.

## Fishing equipment, goals and collections (2026-10-27)

**What it adds to frequent events:**
- **A cast:**
  - the setup's snapshot: the active parts from the player's cached view;
  - one engine preview, only when a bait with a stat is held (its effect after caps);
  - while tracking hints are on, one check a half-second on the bobber's own scheduler until it settles (at most six).
- **A bite with the Auto-hook Reel:** one delayed task, 8 ticks later, on the bobber's region.
- **A catch:**
  - a few counters on the journal (the summary, notable catches);
  - the bait's use;
  - a milestone check, but only after a new species or a personal best.
- **Crafting grids:** a recipe is looked up only when the grid shows a locked recipe's result (Wayfarer's Bait); every other grid change is one material check.
- **Menus:**
  - The Equipment and category screens compute their previews through the same engine (one preview per owned part's tile).
  - Nothing is written to the database for browsing.
  - Nothing runs while a screen stays open.

**Method:** as above (9 bots, 80 s load, a warm-up per load, two 60 s JFR runs from 10 s after "LOAD START"), mobs cleared and spawning off, each set right after a restart. The before set was taken before building; the after set on the final build. A second before/after pair was cut to deliver sooner, so each build has one set of two runs. Loads:
- `menu`: the hub and skill pages.
- `fishmenu`: down to the Fishing page and two redraws, the same clicks on both builds.
- `gearmenu` (new, after only): the new screens. Equipment, Rod, Lure, the Bait Bag, then the journal with two filters and the Treasure tab, at Fishing 50 with four parts owned so every tile computes its preview.
- `menuidle`: one open menu per bot.
- `fishing`: nine bots fishing by hand at Fishing 25 with a Lure III rod.
- `autofish` (after only): nine bots at Fishing 50 with the Auto-hook Reel, only casting.
- `craft`: iron pickaxes at a crafting table, for the grid listener.

**Before:** the build before this milestone (4aaaf3b). **After:** the build committed with this section.

| Load | Before, ms per tick (two runs) | After | Work per bot in 80 s |
|---|---|---|---|
| `menu` | 0.153 / 0.121 | 0.203 / 0.132 | 25 menus, 225 clicks |
| `fishmenu` | 0.145 / 0.162 | 0.174 / 0.165 | 27 visits, 216 clicks |
| `gearmenu` (new screens) | none (no such screens) | 0.389 / 0.315 | 14 rounds, 238 clicks |
| `menuidle` | 0.007 / 0.005 | 0.008 / 0.007 | one open menu |
| `fishing` (by hand) | 0.029 / 0.025 | 0.012 / 0.013 | 9–13 catches |
| `autofish` (the reel) | none (no reel) | 0.022 / 0.021 | 8–10 reeled |
| `craft` | not taken | 0.077 / 0.080 | 122 pickaxes |

TPS was 20.0 in every run.

- **Fishing costs what it did,** by hand and with the reel: 0.012–0.013 ms per tick against 0.025–0.029 before, with the setup, bait and tracking all in the path. The Auto-hook Reel's 0.021–0.022 is the same order: one delayed task per bite, nothing scanning players.
- **An open menu costs nothing extra** (0.007–0.008 against 0.005–0.007).
- **The skill pages are the same or slightly higher.** `fishmenu` 0.165–0.174 against 0.145–0.162, and `menu` 0.132–0.203 against 0.121–0.153; the first after-run of `menu` was the high one. Both are within or near the spread between two runs of one build. A second alternated pair would settle it; it was cut for time.
- **The new screens cost about twice a skill-page click:**
  - 0.315–0.389 ms per tick with nine players clicking through them three times a second without a pause, about 0.26 ms per click against about 0.13 for the skill pages.
  - The profile says why: `MenuManager.show`, which opens a fresh window (vanilla's own per-window work, the cost Step 1 removed for ordinary pages), is 314 of 467 Cerberus samples.
  - The Equipment, category, Bait Bag and new-discoveries screens open a fresh window on purpose: their buttons change things, and the project's rule (and the review's finding) is that stale clicks must not reach them.
  - The engine previews on those screens are a few samples (`Tackle.contributions` 5, `TackleRules.countedUpgrades` 2).
  - A real player opens these screens now and then, so this is a ceiling, not a running cost.
- **Crafting:** 0.077–0.080 ms per tick for 122 pickaxes per bot. There is no before sample in this session. The grid listener looks a recipe up only for a locked recipe's result, so it does less than before the review's fix.
- **Not measured:** many players fishing with Surveyor's Float, bait and tracking all at once beyond the `fishing` load's nine; the journal's `n=` growth is bounded at 12.

## Farming and Cooking (2026-10-28)

**What it adds to frequent events:**
- **A harvest** (a crop broken or picked): a crop lookup by material, a ripeness read, the placed-block memory only for melons, pumpkins, sugar cane and cactus, one roll each for Fortune and quality from the cached attribute snapshot, and the XP payment with its fraction carried. Replant sets one block in the same call.
- **A wide harvest:** at most 16 crops a tick on the field's region thread, each offered to protection plugins and checked against the world border and spawn protection; one sweep per player; nothing runs while no sweep does.
- **Cooking:**
  - the crafted-items statistic path that already existed (furnace and smoker takes, crafting);
  - in the crafting grid, a recipe lookup only when its shown result is a meal's base item;
  - one record per campfire slot a player filled.
- **A meal:** one snapshot rebuild when eaten and one when it ends, one task at its end, and a restoration task of up to its seconds (one heal a second).
- **Nothing runs per tick** while nobody farms or cooks, and nothing is written to the database for any of it beyond the skill saves every XP gain already makes.

**Method:** as above (9 bots, 80 s load, a warm-up per load, two 60 s JFR runs from 10 s after "LOAD START"), mobs cleared and spawning off, each set right after a restart. **Before:** 69e772d, the build before this milestone, measured before any code. **After:** the final build (a918e75's code; this commit changes only tests and docs). Loads:
- `farm` (new): nine bots harvesting ripe wheat by hand in survival at Farming 1, the field refilled as they go;
- `widefarm` (new, after only): the same at Farming 50 with Sneak held, so every cut is a 5×5 wide harvest;
- `cook` (new): bread at a crafting table, about two a second each;
- `menu`, `menuidle`, `idle`.

| Load | Before, ms per tick (two runs) | After | Region ms per tick, before → after | Work per bot in 80 s |
|---|---|---|---|---|
| `farm` | 0.013 / 0.017 | 0.032 / 0.028 | 6.4 / 6.1 → 7.1 / 5.5 | 267–268 harvests |
| `widefarm` | none (no Wide Harvest) | 0.028 / 0.038 | 5.3 / 5.2 | 111–112 sweeps over 14 fresh fields |
| `cook` | 0.072 / 0.048 | 0.074 / 0.084 | 5.5 / 4.8 → 4.4 / 5.0 | 130–132 bread |
| `menu` | 0.222 / 0.191 | 0.182 / 0.179 | 3.0 / 3.1 → 3.2 / 2.7 | 25 menus, 225 clicks |
| `menuidle` | 0.012 / 0.013 | 0.006 / 0.007 | 1.8 / 1.8 → 1.7 / 1.8 | one open menu |
| `idle` | 0.013 / 0.008 | 0.008 / 0.008 | 1.7 / 1.7 → 1.6 / 1.8 | nothing |

TPS was 20.0 in every run. In `farm` and `widefarm`, two of the nine bots refill their field after every cut on both builds (the lanes' layout in `load.js`, as in the `fishing` load), so their counts are lower.

- **Harvesting by hand pays now, for about 10 µs a harvest.** 0.028–0.032 ms per tick against 0.013–0.017 when wheat paid nothing, for about 2,400 harvests in 80 s. `Farming.onBreak` is 19 of about 2,080 region-thread samples a minute. Most of it is the XP path every skill shares (`SkillManager.applyAward`, `saveSkills`, the XP display's HUD line); the crop's own rules (`Farming.ripe`, the Fortune roll) are a sample or two. The region's own time per tick did not change.
- **Wide Harvest stays in small steps.**
  - Nine players sweeping 5×5 fields as fast as the load can: 0.028–0.038 ms per tick, the region at 5.2–5.3 ms, lower than nine players harvesting by hand.
  - The sweep's steps (`Farming`'s step lambdas) are about 11 samples a minute.
  - A sweep breaks at most 16 crops a tick, each paid through the same path, so even a 9×9 (81 crops) is six steps over six ticks, never one long tick.
- **Cooking costs the XP it pays.**
  - Bread now pays Cooking XP: 0.074–0.084 ms per tick against 0.048–0.072 when it paid nothing.
  - `Cooking.onCrafted` is 18–20 samples a minute, about 20 µs per loaf. All of it is under `GatheringService.award`: the XP display's line (12) and the skill save (5).
  - The grid listener's recipe lookup didn't fire once: there is no `Cooking.onPrepare` sample.
- **Menus are the same or lower:** `menu` 0.179–0.182 against 0.191–0.222, an open menu 0.006–0.007 against 0.012–0.013, idle 0.008.
- **Not measured:**
  - many players eating meals at once: a meal is one snapshot rebuild per eat, as an equipment change is;
  - campfires in bulk: one record per slot a player filled, dropped when the food leaves;
  - a sweep across a region edge: it stops there by design.

## Active effects (2026-10-28)

**What it adds:**
- **A countdown while a screen shows a timed effect** (the hub's head, the Active Effects screen): one update a second for that viewer, on their own thread, rewriting only the tiles with a time (`GUIs/menu/MenuTicker`). It stops when the screen closes or nothing on it is timed.
- **Clicking the head** opens Active Effects instead of Stats: the screen works out what each shown effect moves, with the attribute engine with and without it.
- **A meal's heads-up:** one delayed task per meal.
- **Nothing else runs:** no server-wide effect scanner, nothing per tick while no such screen is open.

**Method:** as above (9 bots, 80 s load, a warm-up per load, two 60 s JFR runs from 10 s after "LOAD START"), each set right after a restart. **Before:** cc82ef9, the build before Active Effects, measured before any code. **After:** b7a186e, the build with the review's fixes. Loads:
- `mealidle` (new): each bot eats a Hearty Stew and has Speed I, then leaves the hub open (the head counts down on the new build);
- `effectsmenu` (new): with the same meal and potion, open the menu, click the head, Back, close, as fast as a hurried player (Stats before, Active Effects after);
- `effectsidle` (new): the same, leaving the head's screen open (Active Effects counts down on the new build);
- `menu`, `menuidle`, `idle`.

| Load | Before, ms per tick (two runs) | After | Region ms per tick, before → after | Work per bot in 80 s |
|---|---|---|---|---|
| `mealidle` | 0.013 / 0.014 | 0.032 / 0.033 | 1.9 / 1.7 → 1.5 / 1.8 | an open hub, a meal and a potion running |
| `effectsidle` | 0.008 / 0.013 | 0.047 / 0.037 | 1.7 / 1.6 → 1.7 / 1.7 | the head's screen left open |
| `effectsmenu` | 0.294 / 0.302 | 0.417 / 0.384 | 2.7 / 2.6 → 3.1 / 2.7 | 51 menus, 153 clicks |
| `menu` | 0.186 / 0.162 | 0.313 / 0.209 | 3.8 / 2.8 → 4.1 / 2.7 | 25 menus, 225 clicks |
| `menuidle` | 0.009 / 0.010 | 0.013 / 0.005 | 1.6 / 1.5 → 1.7 / 1.5 | one open menu |
| `idle` | 0.013 / 0.012 | 0.013 / 0.015 | 1.6 / 1.4 → 1.6 / 1.6 | nothing |

TPS was 20.0 in every run.

- **A countdown costs about 40–70 µs a second per open screen.** Nine open hubs with a meal running: +0.019 ms per tick (about 42 µs per viewer per second); nine Active Effects screens open: +0.03 ms per tick (about 67 µs per viewer per second, every timed tile and the header). Under the ticker, `ActiveEffectsMenu.withLore` (copying a tile and setting its lore) is 21 of its 30 samples a minute. It runs only while a screen with a running countdown is open; an open menu with nothing timed (`menuidle`) costs what it did.
- **The head's click costs about 0.09 ms more:** about 0.35 ms per click against 0.26 ms when it opened Stats, for working out every number each effect moves with the engine (`ActiveEffectsMenu.content`, `summary`, `benefits`: a few samples each).
- **`menu`:** 0.209–0.313 against 0.162–0.186. The hub's own drawing didn't grow (`PlayerHubMenu.content`: 4–7 samples a minute on both builds); the difference is in opening the window (`MenuManager.show`, which is the server's own work: 132–169 samples against 78–101), which also varies most between runs on the same build. To be measured again at the end of the milestone (the integration pass).
- **Allocations:** JFR's sampled allocations jump between runs of the same load (one run reports 150 MB/s under `withLore`, the next 0.5 MB/s), so they are not compared.
- **Not measured:** many players with many effects at once; a screen shows at most 28 tiles, so the work per second is bounded.

## Combat improvements: Martial Expertise's Agility and Iron Reprisal (2026-10-28)

**What it adds:**
- **Martial Expertise's Agility** is one more weight in the attribute engine's per-player snapshot, rebuilt only when a level or equipment changes: nothing per hit.
- **Iron Reprisal:** on each melee hit that lands on a player, a few comparisons and an armor count at MONITOR; when it throws, one delayed task on the attacker's own thread a tick later. The cooldown map is swept only when it reaches 512 entries.
- **The shield check** (found while checking Iron Reprisal): two reads of the damage event's modifiers per hit on a player.

**Method:** as above (9 bots, 80 s load, a warm-up per load, two 60 s JFR runs), each set right after a restart. **Before:** 2044c68, the committed build this increment started from. **After:** the build with Iron Reprisal (before the shield check, which adds two field reads per hit). Loads:
- `hurtheavy` (new): like `hurt` (each bot is attacked by zombies it summons, healed when low), with each bot in full iron at Heavy Armor Mastery 100, so on the new build every landed blow throws its zombie;
- `hurt`: the same without armor (nobody thrown on either build).

| Load | Before, ms per tick (two runs) | After | Region ms per tick, before → after | Blows taken by the nine bots in 80 s, before → after |
|---|---|---|---|---|
| `hurtheavy` | 0.092 / 0.084 | 0.068 / 0.059 | 1.6 / 1.6 → 1.5 / 1.6 | 416 / 403 → 121 / 172 |
| `hurt` | 0.095 / 0.090 | 0.085 / 0.114 | 1.7 / 1.7 → 1.8 / 1.9 | 415 / 454 → 504 / 589 |

TPS was 20.0 in every run.

- **Iron Reprisal costs nothing measurable.** With every landed blow throwing its zombie, Cerberus's share went down: a thrown zombie has to walk back before it strikes again, so the armored bots took about a third of the blows (121–172 against 403–416). `IronReprisal.onHit` shows in one sample a minute in each run, `push` in none. One bot of the nine (the one that gives the others their orders) wore no armor in either run.
- **`hurt`** (no armor, nobody thrown) stays within the two builds' run-to-run spread (0.085–0.114 against 0.090–0.095, with 10–30% more blows taken on the new build's runs).
- **The shield check** was added after these runs: two reads of the event per hit on a player, too small to separate from this spread; it is part of the final measurement at the end of the milestone.

## The milestone's before/after: combat, Replant, Herbalism and Alchemy (2026-10-28)

**What the milestone adds to frequent events:**
- **A melee blow on a player:** Iron Reprisal's checks (a few comparisons and an armor count) and, when it throws, one delayed task on the attacker's thread; the shield check (two reads of the event's modifiers).
- **A crop harvest:** the Replanting check on the hoe (an item id), the Fortune bonus added before the replant's seed is taken.
- **A herb gathered:** a lookup by material, the placed-block memory for that block, one roll each for Fortune, quality and the rare reagent, and the XP payment once the drops are final.
- **Wildcrafting:** at most 16 plants a tick per sweep, on the region thread that owns them.
- **A brew:** a click that puts something into a stand is remembered for 1.5 s; the start writes the starter on the stand; the end sets the results and pays.
- **Every tick, each loaded brewing stand that holds an ingredient:** the server asks every brewing mix whether that stand could start a brew, for each bottle (custom mixes first).
- **A drink:** an elixir is one snapshot rebuild, a write to the player's data and two delayed tasks (its end and its heads-up; the tasks of an elixir that was replaced run once and do nothing); a draught is one task a second for its seconds.
- **Nothing per tick** otherwise, and nothing written to the database beyond the skill saves every XP gain already makes.

**Method:** 9 bots and one 80 s run per load, with a 60 s JFR recording from 10 s after "LOAD START" and Folia's `/tps` 30 s into it. Every load was warmed up first (a 30 s run each, all before the first recording). Each build ran right after a restart with mobs cleared, and **the other two sandboxes were stopped** so nothing else used the CPU. **Before:** 2044c68, the build this milestone started from. **After:** the final build (7c3037f). The plugin's own counters were read before and after each load, outside the recording (`load.js` prints them: the work done and the tasks it ran). Loads:
- `combat`, `hurt`: melee and defensive combat, unchanged loads;
- `hurtheavy`: each bot in full iron at Heavy Armor Mastery 100, so every landed blow throws its zombie on the new build;
- `farm`, `widefarm`: harvesting wheat by hand at Farming 1, and Wide Harvest at Farming 50 with Sneak (a Replanting hoe on the new build, Farming's own Replant before);
- `herb` (new), `wildcraft` (new): gathering a meadow of poppies and dandelions by hand at Herbalism 1, and Wildcrafting at 40 with shears and Sneak;
- `brew` (new): water bottles and nether wart brewed at each bot's own stand, a brew every 21 s;
- `drink` (new): a potion every 3 s: an Elixir of Vigor, a Tonic of Swiftness that replaces it and a Draught of Mending after 5 damage; before Alchemy, Mundane Potions at the same pace and with the same damage;
- `standidle` (new): twelve brewing stands per bot, each left with a finished potion and nether wart and no fuel;
- `menu`, `effectsidle`, `idle`: browsing, the Active Effects countdown left open, and players doing nothing.

Not in this pass: `gather` and `felling` (mining and Felling share the break listeners, which only gained the sweep marker, a lookup per break) and `effectsmenu`.

**Results** (Cerberus's time on the region threads from the JFR samples; Folia's region MSPT mid-run; the first bot's own work, which every bot repeats). TPS was 20.0 in every run.

| Load | Cerberus ms per tick, before → after | Region MSPT, before → after | Work (bot 1), before → after | The milestone's own code, after (samples of about 1,200–2,000) |
|---|---|---|---|---|
| `combat` | 0.083 → 0.085 | 3.35 → 3.65 | 110 → 109 hits | none |
| `hurt` | 0.093 → 0.077 | 3.18 → 3.11 | hit 71 → 80 times | none |
| `hurtheavy` | 0.081 → **0.043** | 2.84 → 2.88 | hit 73 → **48** times | Iron Reprisal 1 (125 creatures thrown in 80 s) |
| `farm` | 0.045 → 0.043 | 6.20 → 5.81 | 265 → 265 harvests | Farming as before |
| `widefarm` | 0.048 → 0.037 | 5.82 → 5.23 | 109 → 110 | Farming as before |
| `herb` | 0.017 → 0.025 | 3.81 → 3.74 | 268 → 268 gathered | Herbalism 14 (about 2,370 gathers in 80 s) |
| `wildcraft` | 0.015 → 0.035 | 2.72 → 3.26 | 121 → 99 gathered, 5 → 24 refills | Herbalism 32 (888 sweeps, 4,656 plants in 80 s) |
| `brew` | 0.018 → 0.036 | 2.75 → 2.45 | 4 → 4 brews | Alchemy 2 |
| `drink` | 0.029 → 0.041 | 2.70 → 2.23 | 25 → 25 drinks | Alchemy 6 (153 elixirs, 72 draughts in 80 s) |
| `standidle` | 0.022 → **0.007** | 2.34 → 1.89 | 12 stands | none |
| `menu` | 0.183 → 0.177 | 3.22 → 2.75 | 225 clicks | none |
| `effectsidle` | 0.058 → 0.050 | 2.28 → 1.50 | | none |
| `idle` | 0.008 → 0.011 | 1.88 → 1.61 | | none |

- **What the milestone's own code costs:**
  - **A herb gathered:** about 8 µs, paid at the drop event.
  - **Wildcrafting:** about 9 µs a plant, or 50 µs a sweep. In the `wildcraft` load the sweeps empty the meadow faster than one bot can gather by hand, so the bots spend more of the run refilling it.
  - **Alchemy:** 2–6 samples a minute with nine players brewing or drinking non-stop.
  - **Iron Reprisal:** 1 sample a minute with every landed blow throwing.
- **Fewer blows taken in `hurtheavy`** (48 against 73): a thrown zombie walks back before it strikes again, so the armored bots took a third fewer blows, and Cerberus's share fell with them.
- **`brew` and `drink` rose by 0.012–0.018 ms per tick,** but not in Alchemy's code. The difference is the plugin's usual work for players who take damage. In the after run's `brew` load the bots were being hit (`VirtualHealthListener.onEntityDamage` 16 samples, none before), and `drink` deals 5 damage before each draught.
- **The review's brewing-stand fix (F3), measured on three builds** (the same `standidle` load: 108 stands, each left with a potion and nether wart, no fuel):

  | Build | Cerberus ms per tick | Allocated | Notes |
  |---|---|---|---|
  | Before (no Alchemy) | 0.022 | 0.00 MB/s | |
  | Without the fix | **0.363** (28% of the samples) | **46.7 MB/s** | `Alchemy.plainAwkward` 415 samples, 2.8 GB a minute, reached from every brewing mix's predicate test |
  | Final | 0.007 | 0.07 MB/s | nothing of Alchemy's |

  The server tests every brewing mix for every bottle of every loaded stand that holds an ingredient, every tick; a predicate that copies an item costs that much. Native choices (an exact base potion, herbs by material) cost Cerberus nothing. The fix stays.
- **The `idle` load on the final build** was measured on a freshly restarted server, after the other 12 loads. A container restart had stopped the server in the middle of the first attempt.

## Budgets for the next milestones

- **Combat Improvements, Replant, Herbalism and Alchemy:** met. A herb gathered costs about 8 µs and a Wildcrafting sweep about 50 µs (bounded at 16 plants a tick). Alchemy's handlers show in a few samples a minute with nine players brewing or drinking non-stop, and idle brewing stands cost nothing (0.36 ms a tick before the review's fix). Iron Reprisal shows in about one sample a minute with every landed blow throwing. For the next milestone: keep anything the server runs every tick (brewing stands, hoppers, block ticks) on native matching rather than plugin predicates, give each such path an idle load, and put `gather` and `felling` back in the pass if the break listeners change.

- **Combat improvements (Martial Expertise, Iron Reprisal):** met. Nothing per hit beyond a few comparisons; a throw is one delayed task on the attacker's thread.
- **Active effects:** met. A countdown costs about 40–70 µs a second per open screen that shows a timed effect, and nothing otherwise; the head's click about 0.35 ms, about 0.09 ms more than Stats. If many players keep the screen open, the next step is to rewrite only the time line of each tile (not done: no measured problem).
- **Farming and Cooking:** met. A paid harvest costs about 10 µs and a paid craft about 20 µs, mostly the XP path every skill shares. Wide Harvest is bounded at 16 crops a tick per sweep, and nine players sweeping cost what nine harvesting by hand do. Nothing runs per tick and nothing touches the database per harvest or meal. The XP display's HUD line is now the largest per-gain cost in every gathering loop; coalescing it per tick would be the next step if a loop ever needs it (not done: no measured problem).
- **Step 4–5 (classes and abilities):** met. Plain combat is unchanged with classes in the plugin, a cast costs about what its hits cost, and snapshots rebuild only on joins, loadout changes and passive equips, never per cast.
- **Phases 1–3 (controls, audit, polish):** met. Class logic per key press stays near 0.02 ms, and the feedback and bar don't show in the profile.
- **Progression Depth, XP integrity and level-ups:** met; neither shows in the profile.
- **Grown trees and wear resistance:** met; the new handlers don't show in the profile.
- **Fishing equipment:** met for play: fishing by hand and with the reel cost what fishing did, and an open menu nothing. The new screens cost about twice a skill-page click because they open a fresh window for stale-click safety; if many players browse them at once, the next step is to keep the window and rely on the click guard alone.
- **Fishing:** met. Fishing costs what the old build did per tick, and its handlers are a few samples a minute with nine players fishing non-stop. Creatures are bounded (one per player, 32 on the server) and run one check a second each. For Farming: add a `farming` load, measure on alternating builds, and keep crop handlers as light as the catch.
- **Woodcutting:** met. Cutting wood costs what it did; Felling costs about what cutting the same logs by hand would. It runs 8 logs a tick, does nothing per tick when idle, and writes nothing to the database per block. For Farming or Fishing: add a load for the loop (`load.js`), measure before and after on freshly restarted servers, and look at the XP display's HUD line, which is now the largest per-gain cost.
- **Gameplay Depth (gathering and crafting):** met after the fixes. A block broken costs about 10–20 µs and a craft click about 15–20 µs; nothing runs per tick and nothing touches the database per block. For each next skill loop (Woodcutting, Farming, Fishing), measure `gather` and `craft` (or a new load for that loop) before and after, and look at what runs under each new handler: an API call that looks cheap can build a whole object every time (`getRecipe`).
- **Step 6 (more classes):** measure `combat` and `abilities` before and after each batch. New verbs must stay off the tick where they can (summons, auras and barriers are bounded entity tasks), and class logic must stay at or under 0.05 ms per press. Watch the save document as loadouts grow; it is already built off the tick.
