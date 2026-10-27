# Cerberus — Automation Review

*The anti-farming rules, reviewed against your guiding philosophy, "respect the player's time" (2026-10-27). A proposal: **nothing changes until you approve.** Read from the code on branch `claude/quirky-mccarthy-nhaz58`; line numbers are from commit `8192b67`. Numbers marked "estimate" are worked out from the code and vanilla's timings, not measured in game. Written 2026-10-27.*

## The rule we measure against

Automation, AFK farming, macros for ordinary actions and clever farms are legitimate and keep their normal rewards. There are no inactivity timers and no anti-macro checks. Duplication, fabricated rewards, damage to the economy, crashes and uncontrolled entity spawning stay protected against. Full text: `docs/DECISIONS.md` → "The guiding philosophy" and "What this means for code".

A rule may withhold or reduce a reward only for one of four reasons. The verdicts below name them:
- **Nothing happened:** a block placed and broken again, creative mode, an admin tool, a fabricated or cancelled event.
- **Value from nothing:** duplication, a refund that pays twice, a reward re-triggered without the work.
- **Server protection:** entity limits, bounded work per tick.
- **Equal pacing:** pacing that applies equally to active and passive play, such as a mob's XP budget.

"The player wasn't pressing keys" is never a reason.

**Verdicts:** **Keep** (with the reason it serves); **Conflicts** (it withholds a reward because play is automated, passive or AFK, or it punishes efficiency); **Borderline** (both sides have a case, set out in its section).

## In short

- **37 rules** read in the code, plus the old fishing code. Most serve one of the four reasons and stay. `skills.yml` and the old per-skill classes gate nothing (their `applyEffect` code is unreachable, `docs/SKILL_AUDIT.md`).
- **Conflicts, 4:** the idle rule for Survival and Heavy Armor Mastery (§1); the quarter for spawner, golem-farm and portal mobs (§2); the bone-meal share for trees (§3); hopper smelting paying nobody (§4).
- **Borderline, 3:** forging only from metal you smelted (§5); generator stone and basalt (§6); explosions, machines and crafters, for later (§7).
- **Gaps in rules we keep** (§8): the per-mob XP budgets forget a living mob 30 minutes after it last paid, and at every restart; a mob converted from a bred one gets a fresh spawn reason.
- **Already settled, not asked again** (§9): instant-mining stone XP, grown trees at 80%, AFK fishing with full rewards.
- **Your decisions:** eight lines at the end.

## Every rule

### Combat XP (`Manager/XpIntegrity`, `attributes.yml` → `xp-integrity`)

| # | What it does | Where | Why it was added | Verdict |
|---|---|---|---|---|
| 1 | **Idle rule:** Survival and Heavy Armor Mastery pay nothing after 60 s without any input | `CombatSkillProcessor.java:284`; `idle-seconds: 60` (`attributes.yml:41`) | A player standing idle in a mob trap trained both (your decision of 2026-10-26) | **Conflicts** (§1) |
| 2 | **Per-mob budget:** each mob pays each skill's per-hit XP (Critical Strike, Survival, Heavy Armor Mastery, the magic skills) at most what its kill pays | `XpIntegrity.java:119-122`, `XpBudgetLedger`; `hit-budget: true` | 35 punches on one live zombie paid Critical Strike 94 | **Keep:** equal pacing (gap: §8) |
| 3 | **Farm shares:** mobs from monster spawners, villagers' iron golems and nether portals pay a quarter: the kill, every hit, the budgets and class XP | `by-origin`: `SPAWNER`, `VILLAGE_DEFENSE`, `NETHER_PORTAL` (`attributes.yml:24-26`) | Mob grinders, golem farms, gold farms (audit) | **Conflicts** (§2) |
| 4 | **Bred animals and thrown-egg chickens** pay nothing | `BREEDING: 0`, `EGG: 0` | A calf bred from two cows paid 40, like a zombie | **Keep:** value from nothing (the target is made on demand) and server protection (breeding has no entity cap). Wild animals still pay. Gap: §8 |
| 5 | **Spawn-egg mobs** pay nothing | `SPAWNER_EGG: 0`, `DISPENSE_EGG: 0` | They paid in full (audit); and again until 2026-10-27, because Paper 1.21.11 records a spawn-egg mob's spawn reason as DEFAULT (fixed: the spawn event's reason is kept) | **Keep:** nothing happened (a creative or admin item) |
| 6 | **Built golems** pay nothing | `BUILD_IRONGOLEM: 0`, `BUILD_SNOWMAN: 0` | Same pass: mobs a player makes to kill | **Keep:** value from nothing (a snow golem is snow and a pumpkin, both renewable) |
| 7 | **Creative and spectator earn nothing:** kills, hits, spells, class XP, gathering, smithing | `XpIntegrity.java:85-88`, `GatheringService.methodOf`, `ForgeListener.creative` | A creative kill paid 40 | **Keep:** nothing happened |
| 8 | **No skill XP from fighting players**, for now | `pvp-xp: false` (`attributes.yml:35`) | Killing a player paid 40; your PvP-off decision | **Keep:** your PvP decision (two accounts could farm each other) |
| 9 | **Kill XP needs the player's own hit** in the last 5 s (vanilla's "killer"); a mob the farm kills alone pays nobody | `MobKillListener.java:64-65` | Vanilla's rule | **Keep:** nothing happened (the player's weapon did nothing). An AFK player with an auto-clicker at the kill chamber earns in full |
| 10 | **Kill credit:** a kill trains the skill behind the last hit (within 15 s), not the item in hand at death | `Manager/KillCredit`, `MobKillListener.java:83` | An arrow kill after a switch to a sword trained Blade Mastery | **Keep:** nothing happened (it only routes the XP) |
| 11 | **Weak mobs pay a quarter:** 20+ levels below the skill, for the kill, per-hit XP and the budgets | `kill-xp.anti-grind` (`attributes.yml:14-16`) | Kill XP v2 | **Keep:** equal pacing |
| 12 | **Magic XP once per cast that reaches a mob;** nothing into the sky, a wall, an armor stand or a pet | `XpIntegrity.spellCast` (`:155-172`) | A Fireball into the sky paid every magic skill | **Keep:** nothing happened |
| 13 | **Defensive XP needs a mob behind the hit** (its arrow, fangs, a witch's cloud); TNT, falling blocks, fireworks, dispensers, falls and fire pay nothing. Critical Strike only on mobs, not players or armor stands | `XpIntegrity.attackerOf`, `CombatSkillProcessor.java:264, :281-282` | Defensive XP is for fights | **Keep:** nothing happened; value from nothing (a dispenser would pay forever) |
| 14 | **A name tag never makes a boss:** a mob a player named is never matched by name in `mobs.yml` | `XpIntegrity.java:208-211` | "Legendary Bob" paid ×4 | **Keep:** fabricated reward |

### Class XP (`Classes/*`)

| # | What it does | Where | Why it was added | Verdict |
|---|---|---|---|---|
| 15 | **Cast XP needs an enemy:** a cast pays only when it reaches a hostile mob, or is a self cast within 8 s of a fight with one | `AbilityCaster.java:295-296, :330-340` | Blink and War Cry paid after hitting a cow; Fireball into the sky paid | **Keep:** nothing happened |
| 16 | **Weak mobs:** a quarter of the cast XP and of the budget | `ClassRanks.castXp` (`:33-39`) | Casting at trivial mobs out-earned fights | **Keep:** equal pacing |
| 17 | **Per-mob cast budget:** a mob pays cast XP up to its kill XP; after that only its kill pays | `Classes/CastXpLedger` | A caged mob paid 6 a cast forever (your decision of 2026-10-26) | **Keep:** equal pacing (gap: §8) |
| 18 | **Kill share only from enemies;** unhurtable mobs are never targets | `MobKillListener.java:124`; `AbilityTargets.unhurtable` | Farm animals into class ranks; a heart-bound Creaking paid forever | **Keep:** value from nothing. Classes train in fights; animals still pay skill XP |

### Mining and gathering (`Gathering/*`, `resources.yml`)

| # | What it does | Where | Why it was added | Verdict |
|---|---|---|---|---|
| 19 | **Placed blocks pay nothing;** a Silk-Touched ore placed again pays once, never the bonus | `PlacedBlocks.onPlace` (`:174-177`), `GatherRules.decide` (`:54-62`) | Place and break | **Keep:** nothing happened; value from nothing |
| 20 | **Piston-moved blocks pay nothing** (marked placed at their new spot) | `PlacedBlocks.java:254-278` | A pushed placed ore would lose its mark | **Keep:** value from nothing. Side effect: a natural ore a piston moves pays nothing either. Rare; carrying the block's origin would fix it (low priority) |
| 21 | **Generator stone and basalt** (lava and water) pay nothing | `PlacedBlocks.onForm` (`:179-183`) | Generators make stone forever | **Borderline** (§6) |
| 22 | **Only an explosion that destroys blocks clears marks;** a wind charge doesn't | `PlacedBlocks.java:280-296` | A wind charge made a placed ore pay again, without end | **Keep:** value from nothing |
| 23 | **Explosions and machines pay no XP** (the drops only) | `GatherRules.java:20-23`; `GatheringService.extract` (`:161-183`) | Only a player's own break pays | **Borderline**, later (§7) |
| 24 | **Weak resources pay a quarter:** 20+ levels above the resource (stone from Mining 21) | `weak-resources` (`resources.yml:61`) | Progress means mining deeper | **Keep:** equal pacing. It looks only at your level and the resource's, never at how or by whom the block was broken: a macro and a hand earn the same |
| 25 | **Vanilla decides drops:** an ore your tool can't harvest drops nothing and pays nothing | `GatheringService.java:88` | Vanilla's rule | **Keep:** nothing happened |
| 26 | **Fortune bonus:** at most one extra item, only the ore's own drop, never on Silk Touch, at most a 50% chance | `GatheringListener.java:94-121`; `fortune` (`resources.yml:65`) | A torch on an ore took the bonus | **Keep:** value from nothing |

### Woodcutting

| # | What it does | Where | Why it was added | Verdict |
|---|---|---|---|---|
| 27 | **Logs need an axe** (by hand: the wood, no XP); a too-weak axe pays a quarter | `GatheringService.java:83-91`; `woodcutting.weak-axe: 0.25` | The skill trains with its tool | **Keep:** a progression requirement, the same for everyone |
| 28 | **Trees grown from saplings pay 80%** (heartwood 80%) | `woodcutting.grown` (`resources.yml:87`) | Your Woodcutting playtest | **Keep:** approved by you on 2026-10-26 |
| 29 | **Bone-meal trees pay 50% and no heartwood** | `woodcutting.bone-meal` (`resources.yml:88`) | Fast regrowth was treated as farming | **Conflicts** (§3) |
| 30 | **Felling's rest and sizes:** 0.3 s per log felled (halved at 100), 6 to 128 logs by level | `woodcutting.felling` (`resources.yml:73-77`) | The technique's pace | **Keep:** equal pacing |
| 31 | **Felling never takes a build, a claim's blocks or another region's;** 8 logs a tick | `Woodcutting`, `TreeScan` | Protecting the world (Folia) | **Keep:** server protection. No XP is withheld: those logs pay when cut by hand |
| 32 | **A grown tree of a wood with custom drops** pays nothing and gives vanilla's drops | `GatheringService.paysNothing` (`:102-105`) | A farm never makes custom items (no such wood ships) | **Keep:** value from nothing |

### Smelting and forging (`Gathering/ForgeListener`, `resources.yml` → `smelting`, `forging`)

| # | What it does | Where | Why it was added | Verdict |
|---|---|---|---|---|
| 33 | **Pays what was really made:** the game's "items crafted" statistic; only real furnace blocks; a click that makes nothing pays nothing; an upgrade pays no forging | `ForgeListener.java:148-153, :290-320` | Empty clicks, furnace-shaped plugin screens, the shift-click hole | **Keep:** nothing happened |
| 34 | **Hoppers pay nobody:** metal a hopper takes out of a furnace pays no smelting XP and doesn't count as smelted | `ForgeListener.java:48-51, :155-166`; `smelting` (`resources.yml:109-112`) | Furnace automation | **Conflicts** (§4) |
| 35 | **Forging needs metal you smelted:** iron, gold and copper gear pays and rolls a quality only up to the ingots you took out of a furnace | `forging.needs-smelted` (`resources.yml:123`); `ForgeListener.java:132-136, :207-211` | Golem and gold farms made Smithing renewable (your decision of 2026-10-26) | **Borderline** (§5) |
| 36 | **No loops:** storage blocks, nuggets, the repair recipe and trims pay nothing; one quality roll per click, shared by a batch | `ForgeListener.java:197-221` | Craft-and-uncraft loops; rerolling for free | **Keep:** value from nothing |
| 37 | **A crafter's gear is plain:** no XP, no quality | No handler (`Upgrades.onCrafter` only stops upgrades and heartwood) | No player at the table | **Borderline**, later (§7) |

### Fishing

| # | What it does | Where | Why it was added | Verdict |
|---|---|---|---|---|
| 38 | **Old fishing:** no Fishing XP at all; every catch has a 5% chance to spawn a Sea Monster on the player, uncapped | `CerberusPlugin.onPlayerFish` | Old code | Replaced by the Fishing loop (decisions 2 and 3, built 2026-10-27, `docs/design/FISHING.md`): manual and automated catches pay alike, and sea creatures come only when their owner calls them; nothing to decide here |

## Replacements for the conflicting and borderline rules

### §1. The idle rule (Survival, Heavy Armor Mastery): Conflicts

**Now.** Survival (1 XP a hit) and Heavy Armor Mastery (half the damage, at most 10 a hit) pay nothing once you have done nothing for 60 s, by Paper's idle time (`XpIntegrity.java:143-146`, checked at `CombatSkillProcessor.java:284`).

**Why it conflicts.** It is an inactivity timer. It doesn't stop farms either: any click, key or arm swing resets Paper's idle time (the sandbox check `xp.js idle` brings the XP back with one arm swing). A one-key macro keeps earning; only an honest AFK player loses out.

**Does the per-mob budget alone bound an idle damage farm? Yes, per mob, with one gap.** Checked in the code:
- **Each mob pays each skill at most its kill XP** (`XpIntegrity.java:119-122`): 40 for a natural level-1 zombie, 10 for a spawner's today, 154 for a level-20 mob. A mob 20+ levels below your skill pays a quarter, of its hits and of its budget.
- **At most 4 hits a second land on a player** (`combat.yml` → `damage-timing.invincibility-ms: 250`; vanilla's own hurt cooldown may allow fewer, not measured). At 1 Survival a hit, no farm passes 14,400 Survival an hour. Heavy Armor Mastery has the same per-mob budget and pays more per hit only for hard hits (a level-1 zombie's hit is worth about 0.85).
- **The gap:** the budget lives in memory and is forgotten 30 minutes after a mob last paid, and at every restart (`XpBudgetLedger.java:21, :66-69`). Mobs that keep hitting stay dry. But a cage behind a wall that opens every half hour pays its mobs' budgets again each time. This works today for anyone with a macro; the idle rule only hides it from honest AFK players.

**What an idle damage farm would earn without the idle rule** (estimates):

| Farm | Survival an hour |
|---|---|
| A closed cage, no new mobs | Each mob's budget once, then nothing (12 level-1 zombies: 480). With the gap left open, the same again about every half hour: under 1,000 an hour for those 12, about 4,700 for 24 level-20 zombies |
| A dungeon spawner feeding the trap (a few hundred mobs an hour) | 3,000–4,000 at today's quarter share; up to the ceiling if spawners pay in full (§2) |
| A natural-spawn mob farm, level-1 mobs | Up to the 14,400 ceiling while your Survival is under 21; then 3,600 at most (the weak-mob quarter) |
| Any farm | Never above 14,400. Mobs strong enough to keep paying in full also hit harder (`combat.yml` → `mob-damage-scaling`) |

For scale: fighting level-1 mobs actively earns about 14,000 weapon XP an hour (`docs/design/GATHERING.md`). Survival 1 → 21 needs about 152,000 XP, about 10.5 hours at the ceiling. An idle farm earns what an active player standing in the same mobs earns: equal pacing.

**Replace with:**
- **Remove the idle rule from the code,** not just `idle-seconds: 0`, so an edited `attributes.yml` that still says 60 is ignored (the log can say so).
- **Keep** the per-mob budget and the weak-mob quarter.
- **Close the gap** (§8): a mob's budget ends when it dies or despawns, not 30 minutes after it last paid.

**What changes:** `CombatSkillProcessor.java:284`; `XpIntegrity.idle`; `XpRules` (`idleSeconds`); `AttributesConfig.java:108`; `attributes.yml` (with the `ShippedConfig` bookkeeping); the skill pages' line "Not while you have been idle for a minute" (`GUIs/SkillDefinitions.java:268, :357`).

**Risk: low.** AFK mob farms train Survival and Heavy Armor Mastery at the rates above. One follow-up: a player's hits skip the per-mob budget (`CombatSkillProcessor.java:291-297`), so when the PvP pass turns `pvp-xp` on, PvP defensive XP needs its own bound. `docs/ROADMAP.md:397` says "the idle rule still applies" and would change.

**Test:** `xp.js idle` becomes "an idle defender still earns; one zombie still stops at 40; a fresh zombie pays again". `XpRulesTest` loses its idle case. The playtest checklist's §9 row 8 changes.

### §2. Spawner, golem-farm and portal mobs pay a quarter: Conflicts

**Now.** `by-origin` sets `SPAWNER`, `VILLAGE_DEFENSE` and `NETHER_PORTAL` to 0.25. The share scales the kill, every hit, the per-mob budgets and class XP (`XpIntegrity.originFactor`; `MobKillListener.java:113-114`; the class cast budget, `classService.setKillXpOf` in `CerberusPlugin`).

**Why it conflicts.** It cuts three particular farms, not farming. A natural-spawn mob farm (spawning platforms), a nether-roof gold farm and a raid farm pay in full today, and they are the more productive designs. Nothing in it concerns duplication, fabricated events or server load: vanilla's spawn rules bound these farms like any other.

**Replace with:** remove the three lines, so these mobs pay in full. Keep: kill XP needs your own hit; the weak-mob quarter; the per-mob budgets.

**What a spawner would earn** (estimate): a dungeon spawner makes a few hundred mobs an hour (up to 4 every 10–40 s, at most 6 nearby). With an auto-clicker at level-1 zombies, roughly 12,000–16,000 weapon XP an hour, an active fighter's pace. A quarter of that once the weapon skill is 21. Today: a quarter of all of it.

**What changes:** `attributes.yml` (three lines; `ShippedConfig` bookkeeping) and `XpRules.DEFAULTS`. If you edited your `attributes.yml`, delete the three lines there yourself: your file is never rewritten.

**Risk: low.** Spawners become a good early training spot for weapon skills, Critical Strike and class ranks, as natural mob farms already are.

**Test:** `xp.js origin` (a spawner's zombie pays like a wild one; the bred calf still pays nothing); the playtest checklist's §9 row 2.

### §3. Bone-meal trees pay 50% and no heartwood: Conflicts

**Now.** `woodcutting.bone-meal: { xp: 0.5, heartwood: 0 }`, while a tree left to grow pays 80% (approved). A dispenser's bone meal counts as bone meal. `docs/DECISIONS.md` already marks this share as under review.

**Why it conflicts.** The gap between 80% and 50% is only speed: bone meal is the efficient way to grow a tree, and dispensers are its automation. Bone meal costs a real resource (bones or compost), and nothing is duplicated.

**Replace with:** bone meal pays as a grown tree, `bone-meal: { xp: 0.8, heartwood: 0.8 }`. Keep: placed logs pay nothing; the axe; the weak-resource quarter (oak and birch a quarter from Woodcutting 21); Felling's rest.

**What a farm can earn:** Felling's rest caps every player, active or AFK, at about 3 logs a second by Felling (0.3 s a log; about 6 at level 100). The real limit is growing and replanting. Heartwood upgrades go once per item, so more heartwood mostly means players get their grip or limbs sooner.

**What changes:** `resources.yml` (`ShippedConfig` bookkeeping); `WoodRules.DEFAULTS` (`WoodRules.java:42-43, :51`); the HUD hint (`Woodcutting.java:201-208`), one line for planted trees. An edited `resources.yml` keeps 0.5 until you change it.

**Risk: low to medium.** Nether fungi (10 XP a stem) grown with bone meal become the quickest Woodcutting XP at levels 40 to 60, and heartwood gets common on such farms.

**Test:** `woodcutting.js sources` (bone meal, by hand and by dispenser, pays 3.2 a birch log and rolls heartwood); the playtest checklist's §12 row 3.

### §4. Hopper smelting pays nobody: Conflicts

**Now.** Smelting XP (iron 2, gold 3, copper 1, netherite scrap 25) and the "smelted" credit for forging are paid only when a player takes metal out of a furnace window (`ForgeListener.java:290-303`, by the game's statistic). Ingots a hopper takes out pay nobody and don't count as smelted, so gear forged from them is plain: no Smithing XP, no quality (§5).

**Why it conflicts.** The player mined that metal. A hopper-fed furnace is ordinary automation, and today it costs both the smelting XP and the forged quality.

**Replace with:** count each smelt when the furnace finishes it (`FurnaceSmeltEvent`, at MONITOR) and keep the count in the furnace's own block data. Pay it, XP and credit, to the next player who opens the furnace or breaks it. Vanilla's furnace XP works the same way: it waits in the furnace for a player. A hand take pays from the same count, so nothing pays twice.

**What changes:** the smelting half of `ForgeListener`; `docs/design/GATHERING.md` ("Furnace automation").

**Risk: medium.** The smelting payment is reworked. It must never pay twice (a hand take and the stored count) and never pay a creative player. In a shared base the first player to open a furnace collects, as with vanilla's furnace XP.

**Test:** `gathering.js smelt` rewritten: a hand take pays once; a hopper-drained furnace pays its opener once; breaking it pays; two kinds of input; creative earns nothing; iron smelted by hopper forges with a quality. The playtest checklist's §7 row 7.

### §5. Forging needs metal you smelted: Borderline

**Now.** `forging.needs-smelted: [iron, gold, copper]`: iron, gold and copper gear pays Smithing XP and rolls a quality only up to the ingots you took out of a furnace (`ForgeListener.java:132-136, :207-211, :311-316`). Golem, gold and drowned farms give ingots without mining, so their metal forges plain gear. Diamond gear always pays.

**For keeping it.** Forged-quality bonuses are flat for now (decision 9): a Masterwork golden sword gets the same +10 Power as a Masterwork diamond one. A gold farm's endless ingots would make cheap rerolls the normal way to Masterwork gear, and likely the fastest Smithing XP (5 a unit of gold). That is an economy question, the one reason the philosophy leaves to keep it.

**Against it.** A golem or gold farm is the classic clever farm. Forging with its metal really happens, costs the metal and duplicates nothing.

**Recommendation:** keep it until the equipment-balancing milestone makes quality depend on the metal (decision 9), then drop it (`needs-smelted: []`, supported today). With hopper smelting counted (§4), metal you mined always qualifies, however you smelted it.

**Risk of dropping it now:** gold farms become the main Smithing route, and Masterwork gear gets cheap for everyone. **Test when dropped:** `gathering.js forge` (farm ingots forge with XP and a quality); the playtest checklist's §7 row 8b.

### §6. Generator stone and basalt pay nothing: Borderline

**Now.** Stone and basalt that lava and water form are remembered as placed and pay nothing (`PlacedBlocks.java:179-183`). Cobblestone isn't a resource at all.

**For keeping it.** A generator makes the same block in the same spot forever, at no cost. Mining it takes nothing out of the world, like placing and breaking.

**Against it.** The forming and the break both really happen, nothing is duplicated, and decision 14 already says fast stone mining isn't nerfed. The weak-resource quarter limits it already: stone is level 1 (a quarter from Mining 21), basalt level 5 (from 25). A generator makes no ore, so Fortune, smelting and forging still need real mining.

**What it would earn** (estimate): an overworld generator re-forms about every 1.5 s (Nether lava is faster). A macro working several breaks a few blocks a second: roughly 10,000–25,000 Mining XP an hour below Mining 21, a quarter of that above. Mining 21 → 100 needs about 13 million XP, so a generator stays a beginner's source.

**Recommendation:** pay them like natural stone, behind a switch in `resources.yml` (on by default) so you can turn it back. Cobblestone stays out.

**Risk: low.** An AFK generator becomes the quickest way to about Mining 20. **Test:** `gathering.js farm` (a generator's stone pays 1, a quarter from Mining 21; placed stone and a pushed ore still pay nothing).

### §7. Explosions, machines and crafters: Borderline, later

**Now.** Only a player's own break or craft pays. TNT, creepers and beds give the drops only (`GatherRules.java:20-23`); a future machine's extraction gives drops and no XP (`GatheringService.extract`); a crafter's gear is plain.

**Why borderline.** Blast mining, machines and crafters are real, efficient ways to gather and make, but each needs someone to pay: the TNT's igniter, the machine's or crafter's owner. Redstone-lit TNT has no player behind it, and Paper blocks TNT duplication by default.

**Recommendation:** no change now. No machine exists yet, and crafters are rarely used for gear. Decide with the machine milestone, under one rule: pay the player behind the work for what was really extracted or made, and nothing when no player is behind it.

### §8. Gaps in rules we keep

- **Budgets re-arm** (read from the code). Both per-mob budgets (`XpBudgetLedger`, `CastXpLedger`) forget a living mob 30 minutes after it last paid, and at a restart. For class ranks it matters more: a cage of 24 level-20 zombies re-armed every half hour could pay about 5,000 class XP an hour without a kill (estimate; rank 1 → 50 is about 172,000).
  - **Fix:** a budget ends when its mob dies or despawns (`XpIntegrity.onRemove` already sees despawns, `:220-223`), with a long safety expiry, a day for example, for mobs that unload and never come back. A restart would still reset it, once per restart; keeping it on the mob itself would close that too.
  - **Tests:** `XpBudgetLedgerTest` and `ClassCoreTest`, which assert the 30-minute expiry today.
- **Converted mobs get a fresh spawn reason** (read from the code, not verified in game). A bred mooshroom, sheared, becomes a cow that pays in full (`SHEARED`). A bred villager a zombie converts becomes a zombie villager (`INFECTION`). A spawner's zombie that drowns becomes a drowned (`DROWNED`; moot if §2 is approved).
  - **Fix:** a converted mob keeps the share of the mob it came from (copied at `EntityTransformEvent`).
- **Allays duplicated with amethyst pay in full:** `DUPLICATION` isn't listed. It is the same case as breeding.
  - **Fix:** `DUPLICATION: 0`.

### §9. Already settled (not asked again)

- **Instant-mining stone XP is kept** (decision 14). The weak-resource quarter doesn't conflict with it: it applies to every miner alike.
- **Trees grown from saplings pay 80%** (your decision after the Woodcutting playtest, 2026-10-26). Only the bone-meal share was under review (§3).
- **AFK fishing gets full XP and catches, with no inactivity timers** (decision 3), and **sea creatures come as banked encounters** (decision 2). The old fishing code (row 38) is replaced by the Fishing loop (built 2026-10-27).
- **PvP pays no skill XP for now** (2026-10-25); the PvP pass decides.

## For the owner

Eight decisions. Every other rule above stays as it is. Nothing changes until you approve. Each approved change is then built with its in-game check and a positive control, measured before and after. Your edited config files are never rewritten; the report will list any lines to change by hand.

1. **Idle rule: remove it.** Survival and Heavy Armor Mastery keep training in AFK mob farms, bounded by each mob's budget: at most about 14,400 Survival an hour, a quarter of that from mobs 20+ levels below you.
2. **Per-mob budgets: keep them, and close their gaps.** A budget lasts the mob's life, not 30 minutes; a converted mob keeps its origin's share; duplicated allays pay nothing, like bred animals.
3. **Spawner, golem-farm and portal mobs: pay in full.** Natural-spawn and raid farms already do; the weak-mob quarter and the budgets keep the pace.
4. **Bone-meal trees: pay like trees left to grow,** 80% XP and 80% heartwood.
5. **Hopper smelting: counts.** Paid to the player who opens or breaks the furnace, like vanilla's furnace XP.
6. **Forging from farm metal: keep `needs-smelted` until equipment balancing,** then drop it (one config line).
7. **Generator stone and basalt: pay like natural stone,** behind a switch.
8. **Explosions, machines, crafters: no change now.** Decided with the machine milestone, paying the player behind the work.

With the code, these docs change too: `docs/STATUS.md`, `docs/DECISIONS.md` (the 2026-10-26 lines marked "under review"), `docs/ROADMAP.md:397`, `docs/design/SKILL_PROGRESSION.md` (the Survival and Heavy Armor Mastery rows still list idle checks), the "can't be farmed" tables in `docs/design/GATHERING.md` and `docs/design/WOODCUTTING.md`, and `docs/PLAYTEST_CHECKLIST.md` §7, §9 and §12.
