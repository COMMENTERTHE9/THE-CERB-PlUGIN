# Handoff verification evidence — 2026-10-28

These are automated bot checks on a new local Folia sandbox, not the owner's personal Minecraft playtest. No live server or player data was used. No final performance measurements are claimed.

## Environment

- Milestone test build: `7c3037fa881660183d207ae63446bb2c73784266`; jar SHA-256 `f3391a3b71ebd562e2fdcae6957b4a6f952aa950818bb80ff5cddd18ff0f5568`.
- Latest Claude build, fetched later during the handoff: `e9ba92543deef447071e560428b4f2de6f2082a1` (Recipes); jar SHA-256 `9f035913f2d5ca8c3cb4fe6f178ac29ecca71013fff7045a3c1ea8934ef08406`. Codex made no plugin source/config edits; this newer Claude commit was preserved and verified separately.
- Pre-integration control: `ceed424`, archived separately and built with tests skipped; jar SHA-256 `c01325be7c5b1fd7b4182370f0b5a85bb6abc7bf76c15ea2c63b8d95c2738fc2`.
- Folia 1.21.11 build 14, SHA-256 `f52c408490a0225611e67907a3ca19f7e6da2c6bc899e715d5f46844e7103c39`.
- Windows 11; Temurin Java 21.0.9 server; `-Xms512M -Xmx2G -XX:ActiveProcessorCount=4`; loopback `127.0.0.1:25585`, offline mode; new flat sandbox and fresh test accounts.
- Node 24.14.0, mineflayer 4.39.0, minecraft-protocol 1.68.0, minecraft-data 3.117.0.
- Alchemy harness Git blob: `809814d0c5d8d2cb7273f571d7de690e7b0c740b`; the identical revised harness was used on both plugin builds, with separate victim accounts and the same `CerbTarget` attacker. Default combat/healing config was kept; only the sandbox attacker's vanilla base damage was reduced temporarily by the harness and restored afterwards.

## Results

Set `MC_PORT=25585`; wait for Folia and Cerberus to finish startup; op the named accounts and `CerbTarget` before running commands from `tools/playtest`.

| Command | Plugin build | Output | Result |
|---|---|---|---|
| `mvnw.cmd -B package` | final | [unit-tests.json](unit-tests.json) | 411 tests pass; build succeeds; Java 21 bytecode |
| `node alchemy.js pvp CerbBrew` | final | [alchemy-pvp-final.txt](alchemy-pvp-final.txt) | 4/4; zero healing ticks during a 29 s fight, all five after, no deaths |
| `node alchemy.js pvp CerbBrewControl` | `ceed424` | [alchemy-pvp-before.txt](alchemy-pvp-before.txt) | Expected 3/4: no draught and zero healing ticks after the tag; demonstrates the old bug |
| `node herbalism.js bonemealtop CerbHerb` | final | [herbalism-top-final.txt](herbalism-top-final.txt) | 5/5; growth succeeds, wild +45 XP (discovery included), hand-placed +0 |
| `node herbalism.js bonemealtop CerbHerbControl` | `ceed424` | [herbalism-top-before-inconclusive.txt](herbalism-top-before-inconclusive.txt) | Inconclusive setup: growth left stems without a leaf; XP comparisons did not execute |
| `node woodcutting.js sources CerbWoodControl` | final | [woodcutting-sources-final.txt](woodcutting-sources-final.txt) | 8/8; the wind burst opened the trapdoor, the placed log still paid zero; natural/grown/bone-meal controls pass |
| `node xpbugs.js creative CerbXpFresh` | milestone `7c3037f` | [xpbugs-creative-final.txt](xpbugs-creative-final.txt) | 9/9; creative +0 Pathfinding XP with a HUD reason, fresh survival terrain +10; fishing and bow controls pass |
| `mvnw.cmd -B package` | latest `e9ba925` | [unit-tests-latest.json](unit-tests-latest.json) | 415 tests pass; build succeeds; all 726 non-multirelease classes are at most Java 21 |
| `node menu.js recipes CerbRecipes` | latest `e9ba925` | [recipes-latest.txt](recipes-latest.txt) | 16/16; recipe ingredients, locks, tool descriptions, linked screens, navigation and read-only tiles |

In this table, "final" denotes the final integration build `7c3037f`, before the late Recipes commit. The latest runtime session logged no errors and was stopped cleanly. The isolated sandbox is left stopped with the latest tested jar.

The healing runs start their final measurement at the same **59.56/102.75 HP**. The old build ends at **75.66**, the final at **101.34**: **25.68 HP extra**, matching 25% of 102.75 within rounding, with five recorded healing ticks instead of zero. Neither run died or reached full health in that measurement.

The pre-fix dripleaf setup failed in two attempts, including a larger scan. A separate reconnect read the same stem-only columns. That diagnostic scan change was reverted; the final-build passing test uses the original dripleaf harness. It does not supply a new independent reproduction of the old XP exploit.

Setup attempts without operators, the original healing harness's lethal extra damage, and a safe preparation-limit stop are excluded from acceptance results. One XP slice was stopped early to avoid interference with the tree-growth slice's world clock; it supplies no result. Startup also logged vanilla flat-generator configuration warnings and early console grants failed before Folia had a world; operators were granted again after startup. See [the assessment](../../docs/HANDOFF_CODEX.md) for missing evidence and limitations.
