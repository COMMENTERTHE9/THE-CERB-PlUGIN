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

## Budgets for the next milestones

- **Step 4–5 (classes and abilities):** met. Plain combat is unchanged with classes in the plugin, a cast costs about what its hits cost, and snapshots rebuild only on joins, loadout changes and passive equips, never per cast.
- **Phases 1–3 (controls, audit, polish):** met. Class logic per key press stays near 0.02 ms, and the feedback and bar don't show in the profile.
- **Step 6 (more classes):** measure `combat` and `abilities` before and after each batch. New verbs must stay off the tick where they can (summons, auras and barriers are bounded entity tasks), and class logic must stay at or under 0.05 ms per press. Watch the save document as loadouts grow; it is already built off the tick.
