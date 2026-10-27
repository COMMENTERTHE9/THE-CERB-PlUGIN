# XP efficiency: Fluency (design investigation, 2026-10-27)

*The owner's request of 2026-10-27 (`docs/DECISIONS.md`, "Design investigation"; `docs/ROADMAP.md` → Fishing, item 4): a bonus that grows with a skill's level and speeds XP in that same skill. It should be bounded (about +40% at level 100 as a first candidate) and studied against the XP curve, the time to each milestone and the weak-resource rule. **This is a proposal, not a change:** nothing is built until the owner approves it, Fishing comes first, and no skill is rebalanced. Every number below was computed level by level from the code's own formulas (`SkillManager.xpForLevel`, `Attributes/KillXp`, `resources.yml`). The hours are estimates at the paces stated.*

## 1. The idea, and its name

**In two lines:** the better you know a skill, the faster you learn more of it. Every XP point a skill earns in real play gets a bonus set by that skill's own level. The bonus rises in steps at the milestones and never passes a fixed cap.

**Name: Fluency** ("Mining Fluency +25%"). A skill you know well becomes fluent. The word works for a miner, a swordsman or a mage, and it is Cerberus's own.
- **Alternatives:** **Second Nature** ("Mining has become second nature: +25% Mining XP"), **Practiced Hand**.
- **Avoided:** Wisdom (SkyBlock's name for the idea), Insight (`NAMING.md` keeps it for dao insight), Mastery (skill names, and levels 100–150), Momentum (a class resource), Expertise (Martial Expertise), Proficiency (another game's level bonus).

## 2. What the XP curve asks today

**The curve.** One level costs `round(75 × L^1.85 + 40 × L)` XP (`SkillManager.xpForLevel`):

| Level | 1 | 10 | 50 | 99 |
|---|---|---|---|---|
| XP for that one level | 115 | 5,710 | 106,269 | 372,926 |

Level 99 → 100 alone costs about as much as the first 28 levels together.

**Three paces** (XP an hour):
- **Fighting at your level:** a Common mob of your skill's level killed every 10 seconds.
  - Kill XP v2 pays 40 × (1 + 0.15 × (level − 1)) a kill (`attributes.yml kill-xp`).
  - That is 14,400 XP an hour at level 1 (`GATHERING.md`: "a level-1 kill every 10 seconds is about 14,000") and 120,000 at level 50.
  - It assumes your Power keeps pace with mob levels, as the Stats page says it does.
  - The best case: content at every level.
- **A flat 8,000 an hour:** strip mining by hand at level 1 (`GATHERING.md`, `PLAYTEST_CHECKLIST.md` §7), as if every block kept paying what it pays a beginner. Woodcutting starts at 6,000 (`WOODCUTTING.md` §4), the same order.
- **Mining by hand with today's blocks:** the same miner under the weak-resource rule.
  - Stone and deepslate pay a quarter from level 21.
  - By level 60 every vanilla ore does too (ancient debris, the highest, is level 40).
  - That leaves about 2,300–2,700 XP an hour between 21 and 59, and 2,000 from 60.
  - A rough model, but the shape is right.

| Milestone | XP from level 1 | Share of the road to 100 | Fighting at your level | Flat 8,000/h | Mining by hand, today's blocks |
|---|---|---|---|---|---|
| 10 Apprentice | 17,857 | 0.1% | 0.7 h | 2.2 h | 2.2 h |
| 25 Journeyman | 251,432 | 1.9% | 5.2 h | 31 h | 56 h |
| 50 Expert | 1,826,471 | 13.8% | 22 h | 228 h | 694 h |
| 75 Master | 5,810,604 | 44.0% | 49 h | 726 h | 2,600 h |
| 100 Grandmaster | 13,199,772 | 100% | 85 h | 1,650 h | 6,300 h |

**What it shows:**
- **The road is back-loaded.**
  - 56% of the XP to 100 lies between 75 and 100; the first 25 levels are 2%.
  - In fighting time, the last 25 levels take 37 of the 85 hours.
  - If progression gets tedious anywhere, it is at the top.
- **What an action pays at the top levels sets their pace, more than any bonus can.**
  - **Combat** has mobs at every level (+1 level per 150 blocks from spawn, up to 100), so its top levels take an hour or two each.
  - **Gathering** runs out of full-XP resources. From level 60 every vanilla block and log pays a quarter, and level 99 → 100 by hand is about 190 hours of mining.
  - **Instant mining** (kept by decision 14: 36,000–72,000 XP an hour on stone, a quarter from 21) still needs 700–1,450 hours to 100.
- **Side note:** `xpForLevel`'s comment says the road to 100 is "≈ 19M". The sum is 13,199,772 (checked in Java). The unit test's loose bounds (5M–40M) still hold.

## 3. Candidate curves

Five shapes, all reaching +40% at 100. Level 100 is the cap today, so 99 is the last level that earns XP. The 99 column is what players actually get at the top until mastery exists.

| Curve | Rule | 10 | 25 | 50 | 75 | 99 | 100 |
|---|---|---|---|---|---|---|---|
| **Linear** (the owner's candidate) | +0.4% a level | 4% | 10% | 20% | 30% | 39.6% | 40% |
| **Front-loaded** | 40% × √(level / 100) | 12.6% | 20% | 28.3% | 34.6% | 39.8% | 40% |
| **Back-loaded** | 40% × (level / 100)² | 0.4% | 2.5% | 10% | 22.5% | 39.2% | 40% |
| **Stepped A** | the line's value at each milestone | 5% | 10% | 20% | 30% | 30% | 40% |
| **Stepped B** | +5% at 10, then the line's average over each later band | 5% | 15% | 25% | 35% | 35% | 40% |

The stepped curves pay nothing below 10 and hold each value until the next milestone. The line's average over 25–49, 50–74 and 75–99, weighted by XP, is 15.8%, 25.4% and 35.2%, which Stepped B rounds down.

**Hours to each milestone, fighting at your level** (time saved against today):

| Curve | to 10 | to 25 | to 50 | to 75 | to 100 |
|---|---|---|---|---|---|
| Today | 0.7 h | 5.2 h | 21.8 h | 48.7 h | 85.4 h |
| Linear | 0.7 h (−2.6%) | 4.9 h (−6.2%) | 19.3 h (−11.6%) | 40.8 h (−16.2%) | 68.0 h (−20.4%) |
| Front-loaded | 0.6 h (−9.2%) | 4.5 h (−13.8%) | 17.8 h (−18.3%) | 38.3 h (−21.5%) | 65.0 h (−23.9%) |
| Back-loaded | 0.7 h (−0.2%) | 5.1 h (−1.2%) | 20.8 h (−4.6%) | 44.0 h (−9.6%) | 72.1 h (−15.6%) |
| Stepped A | 0.7 h (0%) | 5.0 h (−4.1%) | 20.1 h (−7.9%) | 42.5 h (−12.8%) | 70.7 h (−17.2%) |
| Stepped B | 0.7 h (0%) | 5.0 h (−4.1%) | 19.4 h (−10.9%) | 41.0 h (−15.9%) | 68.1 h (−20.2%) |

**At the other paces the percentages barely move:**
- **Flat 8,000/h, to 100:** 1,650 h becomes 1,280 (linear, −22.4%), 1,234 (front, −25.2%), 1,349 (back, −18.2%), 1,335 (A, −19.1%) or 1,284 (B, −22.2%).
- **Mining by hand with today's blocks:** 6,300 h becomes about 4,870 with linear or B (−23%).

**What the numbers say:**
- **To 100, every shape saves 16–24%.** Most XP is earned late, where every curve sits between 25% and 40%. In the longest stretch, 75 → 100, they save 23–27%. The shape decides *when* the player feels the bonus, not how much they save in the end.
- **A bonus of b saves b / (1 + b) of the time.** +5% saves 4.8%, +15% saves 13%, +25% 20%, +35% 26%, +40% 29%. That is bounded by construction: +40% can never make anything more than 29% faster.
- **Front-loading spends the bonus where it isn't needed.** Level 10 already takes under an hour of fighting or about two hours of mining. Making it 9% faster only brings the first techniques sooner.
- **Back-loading is invisible for most of the road.** +2.5% at Journeyman is a rounding error.
- **The line is fair but silent.** +0.4% a level is too small to notice on any level-up, and announcing it 99 times would be noise.
- **Steps are felt, and there is no runaway.** With Stepped B, the first level after 25, 50 and 75 is 5–6% quicker than the level before it. Everywhere else each level takes longer than the last.
- **Stepped B saves what the line saves** (−20.2% against −20.4% to 100), paid at the milestones. Stepped A gives up about three points of that.
- **No curve fixes gathering's long tail.** Mining by hand to 100 stays thousands of hours. Fixing that needs higher-level resources (Mining's planned tier-5 resources from 50, `SKILL_PROGRESSION.md`), not a bigger bonus.

## 4. How it meets the other rules

**The weak-resource quarter.** The same applies to its relatives: kill XP's weak-mob quarter, the weak axe's quarter, the shares from grown trees and spawner mobs.
- *Before or after?* Both are multipliers, so the order changes nothing. The real question is whether quartered XP gets Fluency at all.
- **Recommended: yes. Fluency applies last, to whatever the rules paid.**
  - **The rule keeps its purpose:** a weak resource still pays a quarter of what it would pay at full, 4 : 1 at every Fluency.
  - **One place** for every XP source, instead of four: `GatherRules.xp`, `KillXp.compute`, `XpIntegrity.payHit` and the weak axe in `GatheringService`.
  - **One rule for players:** "+25% Mining XP" means all Mining XP.
  - **Weak content stays weak:** at +35% it pays 0.34 of its base XP, against 1.35 for level-matched content.
- **The alternative,** Fluency on full XP only, sharpens the push towards level-matched content (5.4 : 1 at +35%). It costs those four places and an exception players must learn.

| Fluency | Level-matched resource | Weak resource | Ratio |
|---|---|---|---|
| none | 1.00 | 0.25 | 4 : 1 |
| +25% (Expert) | 1.25 | 0.31 | 4 : 1 |
| +35% (Master) | 1.35 | 0.34 | 4 : 1 |
| +35%, quartered XP excluded | 1.35 | 0.25 | 5.4 : 1 |

**Per-mob XP budgets.** Budgets count XP before Fluency: `XpIntegrity`'s ledger works in base XP, and Fluency is added later, in the award path.
- A mob in a cage still runs dry after the same number of hits.
- Its per-hit XP still never exceeds its kill XP, because both get the same multiplier.
- A dry budget pays 0, and Fluency on 0 is 0.

**AFK and automation: equal.** Fluency reads only the skill's level and the XP the rules already paid, never input or movement. An AFK farm and an active player at the same level get the same Fluency. That matches the philosophy: progress comes from real activity in the game, not from whether the mouse keeps moving (`DECISIONS.md`). XP the idle rule (under review) withholds is 0 before Fluency and 0 after.

**No value from nothing.**
- Fluency only multiplies XP that a real action already earned. Placed blocks, creative mode and bred animals pay 0 before it and 0 after.
- It never touches drops, Fortune, quality, items or class XP.
- Skill XP can't be traded, so no loop can turn Fluency into anything else.

**Admin XP: none.** Admin grants and corrections stay exact: `SkillManager.addXpQuietly` (`/cerb skill … xp`, the admin panel's skill editor) and `setSkillLevel`. `applyAward` already tells real play from admin corrections (its celebrate flag), and Fluency follows the same flag.

**Class XP: none.** A kill's class share is computed from the kill XP before the award path (`MobKillListener` → `ClassService.onKill`), and casts have their own ledger, so class ranks pace as today. A class version would be a separate design.

**The XP display.**
- **Compact** keeps the total: "+46 Mining", what you got.
- **Detailed** names the Fluency part: "+46 Mining XP (+6 Fluency) · 12,400 / 70,604 · Lv 40".
- **Every number a page quotes per action** (the skill page's XP per kill, the Stats page's Kill XP tile, `/cerb gather info`) includes Fluency, so the HUD and the pages always agree.

**Level-up messages.**
- **Ordinary level-ups are unchanged.** With steps, Fluency doesn't change between milestones.
- **A milestone adds one chat line** under its title, as a technique does: "✦ Fluency: +15% → +25% Mining XP".
- **The HUD notice keeps the skill's real stat** as its headline. Fluency is not in the attribute engine, so it never competes with real gains in `LevelUps.gains`.

**Mastery to 150 (phase 6).** The curve goes on past 100: levels 100 → 150 cost 28,736,853 XP, more than twice the whole road to 100.
- Mob levels stop at 100 (`combat.yml mob-health-scaling.max-level`).
- At a level-100 Common mob every 10 seconds (634 XP a kill, 228,000 an hour), 100 → 150 takes 126 hours. With the +40% step it takes 90 (−29%).

Fluency stays bounded:
- **The +40% step at 100 holds through 150.** It is the first time anyone uses it.
- **A hard cap** (`max`, proposed +50%) that no step, and no later source (gear, talismans, events), can pass. At +50% nothing is ever more than a third faster.
- **Any further step** (say +45% at 125) comes only with phase 6's own design, as config.

**PvP.** Fighting players pays no skill XP for now (`xp-integrity.pvp-xp: false`), so there is nothing to multiply. When the PvP pass turns it on, Fluency applies as to any XP. It changes no fight, so it needs no PvP pause.

**Fishing: nothing to do.** If approved, Fishing XP gets Fluency through the same award path, with no Fishing code.

## 5. Recommendation

**Fluency, stepped at the milestones (Stepped B):**

| Level of the skill | 1–9 | 10 Apprentice | 25 Journeyman | 50 Expert | 75 Master | 100 Grandmaster |
|---|---|---|---|---|---|---|
| Fluency | none | +5% | +15% | +25% | +35% | +40% (counts from mastery on) |

**Cap:** +50%, for any level and any source. The function enforces it whatever the config says.

**Why this one:**
- **It is the owner's +0.4%-a-level line, paid at the milestones:** the same time saved to 100, within a fraction of a point.
- **Each milestone title brings a perk you feel** ("Expert: +25% Mining XP"), on the celebration that already exists.
- **No noise:** every level-up but the five milestones is unchanged.
- **Nothing changes below 10.** A new player's XP matches `resources.yml` exactly, and level-1 harness checks keep their numbers.
- **It changes no stored XP or level.** Switching it on or off never moves anyone's progress.

**Where it lives (one place):**
- **A pure function,** `Fluency.bonus(level, params)`: the steps, the cap, and 0 for anything malformed.
- **Its settings in `attributes.yml`,** beside `kill-xp` and `xp-integrity`, loaded by `AttributesConfig`. Older files get code defaults, and the `ShippedConfig` hash is updated as for any shipped file. A sketch:

  ```yaml
  # Fluency: a skill's own level speeds its XP (docs/design/XP_EFFICIENCY.md)
  xp-efficiency:
    enabled: true
    steps: { 10: 0.05, 25: 0.15, 50: 0.25, 75: 0.35, 100: 0.40 }   # from this level of the skill on
    max: 0.50                                                       # never above this
  ```
- **Applied in `SkillManager.applyAward`,** the single award path every XP source already uses (kills, hits, spells, chunks, blocks, logs, smelting, forging):
  - only on the real-play path;
  - using the skill's level before the award;
  - **with the bonus part's fractions carried per player and skill,** as `GatherRules.Carry` does. Awards are whole points, so without a carry a stone's 1 XP × 1.25 would round back to 1, and Fluency would never show on stone or logs.
  - With Fluency off, every award is exactly today's.
- **Display:** the XP listener gets the Fluency part as one more argument, for the Detailed line.

**Not in the attribute engine.** Fluency is shown like a stat, but it isn't one:
- **It is a rate, not a power.** It changes how fast you progress, not what you can do. The XP rules (kill XP, XP integrity) already sit beside the engine in `attributes.yml`, not in its snapshot.
- **The engine would need new machinery:** a per-skill scope (35 values in every player's snapshot), steps as a new kind of contribution, and the award path reading the combat snapshot.
- **The per-level form would crowd level-ups.** It would join every level-up's gains and even become Smithing's headline (+0.4% a level outranks its wear resistance).
- **It doesn't count toward the owner's rule** that every skill grows a meaningful attribute or secondary stat (`SKILL_PROGRESSION.md`). A skill whose only growth was Fluency would still be an XP counter.

**How players see it:**
- **The skill page's "How to train":** "Fluency +15% (Journeyman): all Mining XP you earn. Next: +25% at 50 (Expert)."
- **XP numbers with Fluency included:** the skill page, the Stats page's Kill XP tile.
- **The milestone chat line and the Detailed XP line** (§4).
- **A short paragraph in the Guide.**
- **Admin tools:** `/cerb gather info` and `/cerb combatdebug` show base, Fluency and total.

**Tests:**
- **Unit:**
  - the steps at each edge (9/10, 24/25, 49/50, 74/75, 99/100) and above 100;
  - never above the cap;
  - malformed input (negative levels, NaN, a step above the cap);
  - off gives 0.
- **Unit, the award:**
  - off gives exactly today's result for a sweep of amounts;
  - a thousand 1-XP awards at +25% pay exactly 1,250;
  - the quiet path pays no bonus;
  - huge awards stay overflow-safe;
  - a weak resource still pays a quarter of a full one.
- **Config:** an older `attributes.yml` without the block gets the defaults; `ShippedConfigTest`.
- **In game,** a new harness phase:
  - **positive control:** at Mining 40 (+15%), a natural diamond ore pays 46 (40 + 15%);
  - **negative controls:** the same ore placed by hand pays 0, and 0 in creative; an admin grant of 100 adds exactly 100; at Mining 9 the ore pays exactly 40.
- **Existing harnesses** that check exact XP above level 9 compute their expectations through the same steps: `gathering.js`'s weak-resource checks at Mining 40, `woodcutting.js`'s checks at levels 15 and 21.
- **Performance:** one multiplication and one map lookup per award. `load.js gather` before and after, as for any change to a frequent event.

**How to switch it off:** set `xp-efficiency.enabled: false` and run `/cerb reload`. XP is exactly today's again, at once, and nobody's level moves.

**When:** nothing is built until the owner approves. Fishing comes first. Fluency would be a small increment after it (one function, one call site, display lines and tests), and no Fishing work waits for it.

## 6. For the owner

**Decide:** approve Fluency as recommended, built after Fishing? That is +5 / +15 / +25 / +35% at levels 10 / 25 / 50 / 75, +40% from 100 for mastery, never above +50%, on all real skill XP, quartered XP included. The other options are your plain +0.4%-a-level line or nothing. **My recommendation: approve it.** It saves about a fifth of the road to 100, mostly at the top. Note that for gathering's long tail, higher-level resources matter more than any bonus.
