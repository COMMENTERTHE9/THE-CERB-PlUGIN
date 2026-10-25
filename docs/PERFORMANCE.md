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

## Budgets for the next milestones

- **Step 3 (menu animations):** animation work must stay off the click path. The `menu` workload must not lose more than half of the Step 1 gain (Cerberus ≤ ~0.25 ms per tick). An idle open menu must cost nothing measurable.
