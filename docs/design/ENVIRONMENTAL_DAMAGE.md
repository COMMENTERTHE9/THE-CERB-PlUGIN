# Environmental damage: hazards that stay meaningful

*Owner decision 11 (`docs/DECISIONS.md`, 2026-10-27). Status: **a proposal; nothing is built or changed.** The numbers come from reading Cerberus's damage pipeline and the server's own code (Folia 1.21.11), cross-checked with what the existing harnesses measured. §2.4 says which number is which. The game changes only after the owner approves §6.*

## 1. What the owner asked for

> "Environmental damage: I want hazards to stay meaningful. I support proportional scaling with player health, but show a few representative damage calculations and a proposed safe implementation before changing balance. High-level players should be more resilient, not immune."

In short: hazards **meaningful** and **proportional** to health, high-level players **more resilient but never immune**, and the numbers and a safe plan before anything changes.

## 2. Today

### 2.1 How a hazard reaches health

A hazard takes the same road as a mob's blow (`Listener/VirtualHealthListener` → `PlayerVirtualHealthManager.applyResolvedDamage` → `DefenseMath.resolve`):

1. **The vanilla amount, unscaled.** The game sizes the hit for a 20-health player (a fall: blocks fallen − 3; lava: 4), and Cerberus takes it as it is. Mob hits get mob scaling; hazards get nothing.
2. **Resistance:** fire resistance on every kind of fire (lava at half), drowning resistance on drowning, cold resistance on freezing. At most 50% each (Survival + Fortitude). **Nothing resists a fall.**
3. **Armor**, at most 40%, on **every** hazard. (Vanilla armor skips falls, burning, drowning, suffocation, freezing and the void.)
4. **The 75% cap** on resistance and armor together.
5. **The defense bar** takes 70% of what is left while full, less as it drains. It refills 7 s after the last hit.
6. **Health.** Regeneration waits 8 s after any hit, so there is none during a hazard.

**Three findings from the code:**
- **Hazards that touch you every tick hit twice as often as in vanilla.** Lava, fire, magma, campfires, cactus, suffocation and the void try to hurt every tick; vanilla lets one hit through per 0.5 s. Cerberus sets each hit's vanilla damage to 0, so the game's cooldown never holds the next tick back ("fire/lava/cactus send one event per tick", the listener notes). Cerberus's own 0.25 s window then lets one tick in five through: **4 hits a second**.
- **Some vanilla protections do nothing.** Cerberus reads a hit before the game's armor and enchantment step and replaces it, so **Feather Falling, Protection, Fire Protection, the Resistance effect and golden-apple absorption have no effect today.** What the game checks *before* a hit still works: the Fire Resistance potion (immune to fire and lava), Water Breathing and Respiration, Slow Falling, soft landings (water, hay bales, beds).
- **Starvation never stops.** Vanilla stops it at 1 health on Normal, but the game always sees a Cerberus player at full vanilla health: a new player dies after about 15 minutes. Food belongs to Cooking; recorded here, not changed.

Hazards pay no Survival or Heavy Armor XP (defensive XP needs a living attacker). That stays.

### 2.2 Reference players

| | Health | Armor | Resistance: fire / lava / drowning | Defense bar |
|---|---|---|---|---|
| **Vanilla** (for comparison) | 20 | none | none | none |
| **Fresh:** a new character, no armor | 100 | 0% | 0 / 0 / 0 | 300 |
| **Mid:** Survival, Heavy Armor Training and First Aid at 50; iron armor | 200 | 34% | 45% / 23% / 35% | 558 |
| **Trained:** the same three at 100; diamond armor | 300 | 40% (the cap) | 50% / 25% / 50% | 849 |

A real new character has level-1 skills: 102 health, a 308 bar, about 2% armor and resistance. That is rounded away here; it moves the shares by about 2%.

### 2.3 The numbers today

Share of max health lost from full health, with a full defense bar (the usual case when exploring):

| Hazard (vanilla damage) | Vanilla | Fresh | Mid | Trained |
|---|---|---|---|---|
| Fall, 10 blocks (7) | 35% | 2.1% | 0.7% | 0.4% |
| Fall, 20 blocks (17) | 85% | 5.1% | 1.7% | 1.0% |
| Fall, 40 blocks (37) | dead | 11.1% | 3.7% | 2.2% |
| Lava, 3 s (4 a hit) | dead after 2 s | 16.1% | 3.8% | 2.2% |
| Lava, 10 s | dead after 2 s | 66% | 13.7% | 7.7% |
| Burning, 5 s (1 a second) | 25% | 1.5% | 0.3% | 0.2% |
| Drowning, the 10 s after the air runs out (2 a second) | dead at the 10th second | 6.3% | 1.3% | 0.6% |
| Lightning strike (5) | 25% | 1.5% | 0.5% | 0.3% |
| The void, first second (4 a hit) | 40% | 5.0% | 1.6% | 1.0% |
| **Lowest fall that kills** | 23 blocks | 337 blocks | none (the world is 384 tall) | none |
| **Time to die in lava** | 2 s | 14 s | 53 s | 91 s |
| **Time to die in the void** | 2 s | 14 s | 41 s | 68 s |

After a fight that emptied the bar, every Cerberus number is about 3.3 times bigger: a fresh player's 20-block fall costs 17%, and lava kills after 6 s. A trained player still loses only 3.4% to that fall.

**Worked examples** (the arithmetic behind every table):
- **Fresh, 20-block fall:** 17 → no armor → the full bar takes 70% (11.9) → **5.1** to health: 5.1% of 100.
- **Trained, 20-block fall:** 17 → armor 40% → 10.2 → the bar takes 70% (7.1) → **3.1**: 1.0% of 300.
- **Trained, lava:** 4 → resistance 25% and armor 40%: 4 × 0.75 × 0.6 = 1.8 → the bar takes 70% → **0.54** a hit. At 4 hits a second that is 0.7% of 300 a second, rising slowly as the bar drains: dead after 91 s.
- **Vanilla, lava:** 4 every 0.5 s against 20 health: 20% a hit, dead at the 5th hit (2 s).

**In short:** a trained player can't die from any fall the world allows, lasts a minute and a half in lava and over a minute in the void. A new player survives a 300-block fall. Hazards are not meaningful today.

### 2.4 Measured or computed

- **Measured in game** by existing harnesses: the defense steps and their shares (`progression.js defense` and `fire`: armor 40%, the bar's 70%, fire resistance 50%, lava 25%), and the trained player's 300 health and 30% armor without gear (`ranged.js levels`; diamond armor brings it to the 40% cap).
- **From the server's code** (the Folia 1.21.11 jar, read for this document): every vanilla amount and rhythm here, including the 15 s of burning lava leaves, the 15 s of air before drowning, burning never hurting while in lava, and the game's 0.5 s hurt cooldown, which the void respects too.
- **From Cerberus's code:** the 4 hits a second, armor on every hazard, the protections that do nothing, starvation.
- **Computed, not measured:** every share and time in this document, worked out hit by hit (a small simulation of `DefenseMath`, with the bar draining). The harness in §5.6 measures them before anything ships, the rhythm first.

## 3. Options

All three keep resistance, armor and the 75% cap. A and B change only how big a hazard is and keep the rest of today's pipeline, the defense bar included. C also changes how a hazard is applied. Every table has the same rows as §2.3.

### A. Fully proportional: × max health ÷ 20

A hazard takes its vanilla share of max health before defenses: ×5 for a fresh player, ×10 mid, ×15 trained.

| Hazard | Vanilla | Fresh | Mid | Trained |
|---|---|---|---|---|
| Fall, 10 blocks | 35% | 10.5% | 7.0% | 6.3% |
| Fall, 20 blocks | 85% | 26% | 16.9% | 15.3% |
| Fall, 40 blocks | dead | 56% | 37% | 33% |
| Lava, 3 s | dead after 2 s | dead after 2.75 s | 48% | 41% |
| Lava, 10 s | dead after 2 s | dead after 2.75 s | dead after 5.25 s | dead after 6 s |
| Burning, 5 s | 25% | 7.9% | 2.8% | 2.3% |
| Drowning, 10 s | dead | 37% | 14.3% | 9.7% |
| Lightning | 25% | 7.5% | 5.0% | 4.5% |
| The void, 1 s | 40% | 28% | 17.7% | 15.9% |
| **Lowest fall that kills** | 23 blocks | 70 | 104 | 115 |
| **Time to die in lava** | 2 s | 2.75 s | 5.25 s | 6 s |
| **Time to die in the void** | 2 s | 2.75 s | 4 s | 4.5 s |

- **For:** the simplest rule.
- **Against:** Vitality protects nothing; only armor and resistance make a trained player sturdier. The full bar still takes 70%, so a fresh player's 20-block fall costs 26%, but 85% right after a fight: the bar's state swings every hazard 3.3×. Lava still hits twice as often as in vanilla, and the void is slow.
- Scaling by max health ÷ 100 instead leaves a fresh player exactly where they are today.

### B. Proportional with resilience: × 5 × √(max health ÷ 100)

The same as A for a fresh player. Vitality now helps, by its square root: twice the health → 71% of the share, three times → 58%, four times (the Vitality ceiling) → half.

| Hazard | Vanilla | Fresh | Mid | Trained |
|---|---|---|---|---|
| Fall, 10 blocks | 35% | 10.5% | 4.9% | 3.6% |
| Fall, 20 blocks | 85% | 26% | 11.9% | 8.8% |
| Fall, 40 blocks | dead | 56% | 26% | 19.2% |
| Lava, 3 s | dead after 2 s | dead after 2.75 s | 32% | 22% |
| Lava, 10 s | dead after 2 s | dead after 2.75 s | dead after 7.5 s | 94% |
| Burning, 5 s | 25% | 7.9% | 2.0% | 1.3% |
| Drowning, 10 s | dead | 37% | 9.8% | 5.4% |
| Lightning | 25% | 7.5% | 3.5% | 2.6% |
| The void, 1 s | 40% | 28% | 12.1% | 8.8% |
| **Lowest fall that kills** | 23 blocks | 70 | 146 | 196 |
| **Time to die in lava** | 2 s | 2.75 s | 7.5 s | 10.5 s |
| **Time to die in the void** | 2 s | 2.75 s | 5.75 s | 7.75 s |

- **For:** high-level players are more resilient through their health as well as their armor, and never immune.
- **Against:** A's other problems stay: the bar's 3.3× swing, the doubled rhythm, the slow void.
- A straight line with a floor (−0.25% of the share per Vitality point, never below half) gives nearly the same numbers. The square root needs no floor.

### C. B's size, with four rules (recommended)

1. **Per cause:** burning and drowning at half of vanilla; every other hazard at vanilla's share.
2. **Vanilla's rhythm:** a hazard that tries every tick is scaled per hit by Cerberus's window ÷ vanilla's 0.5 s (×0.5 today), so its damage per second matches vanilla.
3. **The defense bar doesn't take hazards.** It guards against blows; falls, fire and water go to health after resistance and armor.
4. **The void kills in about 2 s,** whatever the player's stats, and no single hazard hit other than a fall takes more than half of max health.

Its table and worked examples are in §4.3.

## 4. Recommendation: C

### 4.1 The formula

```
v      = max health ÷ (100 × realm)       Vitality's multiplier, 1 + 0.01 × Vitality: 1 (fresh) to 4 (ceiling)
size   = 5 × realm × v^(1 − resilience)    with resilience 0.5: 5 × √v (realm is 1 until Ascension)
hit    = vanilla × cause factor × strength × rhythm × size
         rhythm: for hazards that try every tick, Cerberus's hit window (rounded up to whole ticks) ÷ 0.5 s;
                 0.25 ÷ 0.5 = 0.5 today. Every other hazard: 1
         then at most half of max health in one hit (falls and elytra crashes excepted), never below vanilla
then:    resistance → armor → 75% cap, as today; the defense bar is skipped
the void: hit = (vanilla ÷ 20) × rhythm × max health = 10% of max health a hit; nothing reduces it
```

As a share of max health before resistance and armor, that is **vanilla's share × cause factor × strength × v^(−resilience)**. A new player (v = 1) loses exactly vanilla's share. The 5 is Cerberus's 100 base health over vanilla's 20.

### 4.2 Causes

| Cause (the server's name) | Factor | Tries to hurt | Notes |
|---|---|---|---|
| Fall (FALL), elytra crash (FLY_INTO_WALL) | 1.0 | once | blocks fallen − 3, as vanilla; soft landings still count |
| Lava (LAVA) | 1.0 | every tick | 40% of a new player's health a second, as vanilla |
| Standing in fire (FIRE) | 1.0 | every tick | soul fire hits twice as hard, as vanilla |
| Magma block (HOT_FLOOR) | 1.0 | every tick | sneaking still avoids it |
| Campfire (CAMPFIRE) | 1.0 | every tick | |
| Cactus, sweet berry bush (CONTACT) | 1.0 | every tick | |
| Suffocation (SUFFOCATION) | 1.0 | every tick | |
| Freezing in powder snow (FREEZE) | 1.0 | every 2 s | cold resistance applies |
| Lightning (LIGHTNING) | 1.0 | once | the fire it starts counts as burning |
| **Burning** (FIRE_TICK) | **0.5** | every second | half of vanilla: you are already out of the fire |
| **Drowning** (DROWNING) | **0.5** | every second | half of vanilla: the air bar warns you |
| The void (VOID) | 1.0 | every tick | a share of max health; resilience, resistance and armor don't apply |

**Not hazards, so unchanged:** mob and player hits, projectiles, poison, wither and other effects, starvation, explosions (creepers, TNT, beds, anchors), falling blocks and anvils, cramming, the world border, `/kill`.

### 4.3 The numbers

**Ceiling** = Vitality and Fortitude at their 300 limit, with diamond armor: 400 health, armor 40%, resistances 50% (lava 25%). Today's skills and class passives can't reach it (Vitality tops out near 245, about 345 health); it is the most resilient player the caps allow.

| Hazard | Vanilla | Fresh | Mid | Trained | Ceiling |
|---|---|---|---|---|---|
| Fall, 10 blocks | 35% | 35% | 16.4% | 12.1% | 10.5% |
| Fall, 20 blocks | 85% | 85% | 40% | 29% | 26% |
| Fall, 40 blocks | dead | dead | 87% | 64% | 56% |
| Lava, 3 s | dead after 2 s | dead after 2.25 s | 44% | 31% | 27% |
| Lava, 10 s | dead after 2 s | dead after 2.25 s | dead after 6.75 s | dead after 9.5 s | 90% |
| Burning, 5 s | 25% | 12.5% | 3.2% | 2.2% | 1.9% |
| Drowning, 10 s | dead | 50% | 15.2% | 8.7% | 7.5% |
| Lightning | 25% | 25% | 11.7% | 8.7% | 7.5% |
| The void, 1 s | 40% | 40% | 40% | 40% | 40% |
| **Lowest fall that kills** | 23 | 23 | 46 | 61 | 70 |
| **Time to die in lava** | 2 s | 2.25 s | 6.75 s | 9.5 s | 11 s |
| **Time to die in the void** | 2 s | 2.25 s | 2.25 s | 2.25 s | 2.25 s |

**Worked examples:**
- **Fresh, 20-block fall:** 17 × 5 = **85**: 85% of 100, vanilla's share.
- **Trained, 20-block fall:** 17 × 5 × √3 = 147 → armor 40% → **88**: 29% of 300.
- **Trained, lava:** 4 × 5 × √3 × 0.5 = 17.3 a hit → resistance 25% and armor 40% (× 0.75 × 0.6) → **7.8**: 2.6% a hit, 10.4% a second, dead after 9.5 s.
- **Trained, drowning:** 2 × 0.5 × 5 × √3 = 8.7 a hit → resistance 50% and armor 40% (× 0.5 × 0.6) → **2.6**: 0.87% a second.
- **Anyone, the void:** (4 ÷ 20) × 0.5 = 10% of max health a hit, 4 hits a second: dead at the 10th, after 2.25 s.

### 4.4 Why it fits "meaningful, resilient, not immune"

- **Meaningful.** A new player meets Minecraft's own hazards: a 20-block fall takes 85%, 23 blocks kill, lava kills in about 2 s; burning and drowning are half as harsh. With a full bar, every hazard is 8 to 40 times stronger than today.
- **More resilient with training,** through Vitality (the square root), Fortitude (armor and resistance) and Survival (resistance). A trained player in diamond takes about a third of a new player's share from a fall and a quarter from lava: they survive a 60-block fall and 9 s in lava. **Lethal falls, decided:** vanilla's 23 blocks stay lethal for a new player, and training raises the height as Feather Falling and Protection do in vanilla (a fully enchanted vanilla player survives about 100 blocks).
- **Never immune.** Vitality halves a hazard at most and resistance with armor removes at most 75%, so every hazard keeps at least an eighth of what it costs a new player: lava 5% of max health a second, a 20-block fall 10.6%. Today's caps stay above that floor: the ceiling player dies from 70 blocks (163 if a future fall resistance reached the cap) and after 11 s in lava. The void kills everyone in about 2 s.
- **Predictable.** Without the bar, the same fall always costs the same share, so players can learn heights; with it, a fall right after a fight cost 3.3 times more.
- **Ready for realms.** The size follows the realm multiplier, so hazards keep their share when Ascension multiplies health by 8 a realm (`POWER_SCALING.md`: "magnitude inflates; the gameplay ratio is conserved"). Without it, hazards would vanish at the first breakthrough.

### 4.5 What it does not change, and one thing to watch

- **Unchanged:** mob and player hits and the bar against them, regeneration, death and respawn, defensive XP (none from hazards), every resistance and its decision-C shares, and the protections that work today (§2.1).
- **To watch in the playtest:** weak mobs stay gentle. A level-1 zombie costs a new player about 1% with a full bar, 3% with an empty one. So falls and lava now outweigh weak mobs by far more than in vanilla, where a 10-block fall is worth about two zombie hits; here it is closer to forty. That is what "meaningful" asks for. If it feels too sharp, `strength: 0.5` halves every hazard: a new player then loses 42.5% to a 20-block fall, dies from 43 blocks and lasts 4.75 s in lava.

## 5. Safe implementation plan (not built)

### 5.1 One decision point

- **`Listener/VirtualHealthListener.onEntityDamage`,** the player-target branch, right after the `MobScaling` step and before armor is read. The step applies when the cause has a factor, no living thing is behind the hit, the hit isn't PvP and the player isn't PvP-tagged. A lightning strike counts as a hazard; a mob's or player's blow never does. The damage then becomes `HazardScaling.hit(...)` and the context is marked as a hazard. For the void, armor is set to 0.
- **`PlayerVirtualHealthManager.applyResolvedDamage`** reads the mark: max absorption 0 and no bar drain (unless `defense-bar: true`). `DefenseMath` doesn't change.
- **New `Manager/HazardScaling`:** pure, no Bukkit types, like `DefenseMath`. It holds the rules from `combat.yml` and the arithmetic of §4.1. Each cause's factor × strength × rhythm is worked out once, when the config loads.
- **Small additions:** `CombatContext` gets `markHazard()`, a boolean like `markPvp()`; `CombatConfig` reads the section.
- **Folia:** the step runs inside the damage event on the player's own region thread, reads concurrent maps only and schedules nothing.

### 5.2 Config (`combat.yml`)

```yaml
# ── Environmental damage (docs/design/ENVIRONMENTAL_DAMAGE.md) ──────────
# Hazards take a share of max health instead of vanilla's flat damage. One hit:
#   vanilla x cause factor x strength x rhythm x 5 x realm x (1 + 0.01 Vitality)^(1 - resilience)
# A factor of 1.0 = vanilla's share; a new player loses exactly that. Hazards that try every tick
# are scaled to vanilla's damage per second (rhythm: 0.5 a hit with the 0.25 s window).
environmental-damage:
  enabled: true
  strength: 1.0          # every hazard at once (0.5 halves them)
  resilience: 0.5        # how much Vitality protects: 0 not at all (the same share for all), 1 fully
  max-hit-share: 0.5     # one hit takes at most this share of max health (falls excepted); never below vanilla
  defense-bar: false     # false: the defense bar takes blows, not hazards
  in-pvp: false          # false: while PvP-tagged, hazards stay as before (docs/ROADMAP.md -> PvP)
  causes:                # x vanilla; 0 = as today
    fall: 1.0            # and elytra crashes
    lava: 1.0
    standing-in-fire: 1.0
    magma-block: 1.0
    campfire: 1.0
    cactus: 1.0          # and sweet berry bushes
    suffocation: 1.0
    freezing: 1.0
    lightning: 1.0
    burning: 0.5         # half of vanilla
    drowning: 0.5        # half of vanilla
  void: 1.0              # vanilla's share of max health a second; nothing reduces it (0 = as today)
```

- **Code defaults equal these values,** so a file missing a key behaves like the shipped one. An invalid value warns and falls back, as `mob-damage-scaling.ranged` does.
- **Delivery:** the `combat.yml` loader copies a missing top-level section from the plugin's own file into the server's at startup (as `fire-resistance` arrived); it then stays in the owner's file.
- **Default: on.** Approving this document is the owner's switch, and the next playtest should feel the change. For a trial first, it ships `enabled: false` and one line turns it on. Decide now: the shipped value sticks in the owner's file.

### 5.3 How it meets the rest of combat

| Part | With the change |
|---|---|
| Fire, drowning and cold resistance (decision-C shares) | Unchanged; they reduce the bigger hit |
| Armor and the 75% cap | Unchanged; the void ignores both |
| The defense bar | Takes blows, not hazards (`defense-bar`) |
| The 0.25 s window and the 3 s respawn protection | Unchanged; the rhythm follows `damage-timing.invincibility-ms` |
| Regeneration | Unchanged: a hazard counts as a hit, so none for 8 s |
| XP | None from hazards, as now |
| Death messages, respawn | Unchanged; the game names the cause |
| `/cerb combatdebug` | A new `Hazard` step: the cause, its factor and the size |

### 5.4 PvP stays as before

- **While a player is PvP-tagged** (15 s after hitting or being hit by a player), hazards stay exactly as today: vanilla-sized and through the bar (`in-pvp: false`). A player knocked into lava in a fight takes what they would take now. The switch joins the list in `docs/ROADMAP.md` → PvP; the PvP pass decides.
- **PvP-marked hits** (another player's burn, poison or wither tick, their TNT or pet) keep the "as before" path. They are never scaled twice.
- **The void is the one exception:** it kills in about 2 s during PvP too. A player below the world dies either way; only the wait changes.

### 5.5 Unit tests (`HazardScalingTest`, pure)

- **Sizes:** a new player's 20-block fall is 85 of 100, a trained player's 17 × 5 × √3 = 147. Resilience 0 gives the same share at any health, 1 the same size, 0.5 half the share at four times the health. Realm ×8 makes the hit ×8 and keeps the share.
- **Rhythm:** every-tick causes × window ÷ 500 ms (250 → 0.5, 500 → 1, 0 → one tick); other causes × 1.
- **Limits:** one hit at most half of max health (falls excepted), never less than vanilla; the void 10% a hit. Disabled, a factor of 0, or a PvP-tagged player: unchanged.
- **Never immune:** at Vitality 300 and the 75% cap, every cause keeps at least an eighth of a new player's share.
- **Config:** the shipped `combat.yml` reads as documented; a file without the section reads the defaults; bad values fall back. `DefenseMath.resolve` with absorption 0 leaves the bar untouched.

### 5.6 In-game harness (`tools/playtest/hazards.js`)

A mineflayer bot like `ranged.js`: local sandbox only, op, survival, `/cerb combatdebug` on, a fresh and a trained character, health read with `/cerb healthstate`. Exact hits come from `/damage <bot> <n> minecraft:<type>`; real ones from drops, pools and blocks (magma and campfires count only from a real block).

| Phase | Checks |
|---|---|
| `rhythm` (first) | Lava lands 4 hits a second (counted in the trace) and takes vanilla's share a second |
| `falls` | Drops of 10, 20 and 40 blocks: health within a point of §4.3, a `Hazard` step and no `DefenseBar` step. Exact hits at the lethal height and one below (23/22 blocks fresh, 61/60 trained) |
| `lava` | 3 s in a pool: within 2% of the table; a fresh player dies within 2–2.5 s |
| `fire` | Fire, magma (not sneaking), campfire, burning: the decision-C resistances still in the trace |
| `water` | 10 s of drowning after the air runs out |
| `strike` | `/summon lightning_bolt` on the bot |
| `void` | Below the world: dead within 2–2.5 s, fresh and trained; a clean respawn |
| `pvp` | Two bots tag each other; a 20-block fall then costs today's 5.1% (fresh) and drains the bar |
| `off` | `enabled: false` in the sandbox's `combat.yml` (restored afterwards) and `/cerb reload`: today's numbers back. `defense-bar: true` alone: the bar takes hazards again |
| Controls | A zombie's hit is unchanged (no `Hazard` step, the bar drains); the Fire Resistance potion still blocks lava; on a build without the change, a 20-block fall costs 5.1% (fresh) |

### 5.7 Performance

- Lava, fire, magma and the void send a damage event every tick: 20 a second per player, of which 4 land. Per event the step adds an array read, two map reads (max health and realm), a PvP-tag lookup and one square root. **That is O(1), with no new objects.** The trace line is built only for players with `/cerb combatdebug` on.
- **Measured before and after,** as `docs/PERFORMANCE.md` describes: a new `load.js hazards` mode (9 trained bots standing on magma blocks, healed every few seconds: 20 events a second each) on fresh restarts, plus `tools/perf/profile.sh`. Expected: no difference.
- The work every damage event already does (the defense and HUD refresh, the combat context) is unchanged. If the profile shows it, skipping that work inside the 0.25 s window is a separate, measured follow-up.

### 5.8 Rollback

- **All of it:** `environmental-damage.enabled: false`, then `/cerb reload`. That restores today's behaviour exactly, with no restart. Nothing is stored, so nothing needs undoing.
- **One part at a time:** `defense-bar: true` (the bar takes hazards again), a cause at 0 (that hazard as today), `void: 0`, `strength`, `resilience`.

When it is built, these docs change too: `docs/ATTRIBUTES_ENGINE.md` (how a hit is reduced), `docs/STATUS.md`, `docs/PLAYTEST_CHECKLIST.md` (a hazards section), `docs/ROADMAP.md` → PvP, `docs/DECISIONS.md` (decision 11), and the Player Menu Guide's "Health and defense" line (the bar absorbs part of each blow; falls, fire and water hit health).

## 6. For the owner

Approve, change or reject each; the recommended answer comes first.

1. **The rule:** option C. (Or A or B, §3.)
2. **Strength 1.0:** a new player loses vanilla's share. (0.5 halves every hazard.)
3. **Resilience 0.5:** before armor, a trained player loses 58% of a new player's share, and nobody less than half. (0 = the same share for everyone; 1 = Vitality protects fully.)
4. **Burning and drowning at half of vanilla;** every other hazard at vanilla's share.
5. **The defense bar stops absorbing hazards.** It keeps absorbing every blow.
6. **The void kills everyone in about 2 s,** during PvP too.
7. **While PvP-tagged, hazards stay as today** until the PvP pass.
8. **Ship it switched on.** Rollback is one line and `/cerb reload`.
9. **Optional, separate: Feather Falling counts against falls again,** at vanilla's 12% a level (48% at IV), as a fall resistance inside the 75% cap and under the same PvP rule. Recommended yes. The other vanilla protections in §2.1 wait for the Enchanting system.
