## Core Gameplay Loops, Player Fantasy & Balance

### 1. Player Fantasy / Identity

In Cerberus you are a **Threshold Walker** — a survivor who began at the safe heart of the world (spawn) and earns the right to push outward into territory that wants you dead. Where vanilla Minecraft says "the world is uniformly dangerous at night and trivial by day," Cerberus says **"danger is a coordinate."** Every block away from spawn is a deliberate dial-up of mob level, loot tier, and reward. Your character is not a generic Steve with a diamond sword; you are a build — a self-authored intersection of 37 skills, an elemental affinity, a defense profile, and a gear loadout that you assembled, not looted by accident.

The power fantasy has three pillars:

- **Mastery, not just gear.** Skills (cap 100 each) mean a naked expert is dangerous and a geared novice is fragile. Your hands got better, not just your inventory.
- **Specialization with teeth.** You cannot max everything meaningfully within a normal play arc. A Blade/Fire/Heavy-Armor bruiser plays nothing like an Arcane/Ice/Stealth glass-cannon caster. Identity is a choice with opportunity cost.
- **Earned frontier.** The map is a difficulty gradient you literally walk across. Reaching PRIMORDIAL country and surviving is the flex — and Blink Gates let you brag by anchoring a foothold out there.

**What makes it feel different:**

| Axis | Vanilla MC | Typical RPG plugin | Cerberus |
|---|---|---|---|
| HP model | 20 hardcoded | +max-health buffs (creep magnet) | Virtual Health pool, decoupled from vanilla, hard-capped |
| Difficulty source | Time of day / biome | Mob-level regions or grind walls | **Continuous distance-from-spawn curve** |
| Defense | Armor points | Flat % reduction | **3-layer: elemental resist → armor (≤40%) → regenerating defense bar** |
| Damage clarity | Hearts only | Some show numbers | Floaty numbers + combo counter + elemental color coding |
| Progression | Tools tier | Levels + gear | **37 skills × unified 10-tier economy + Echo rituals + badges** |

---

### 2. The Core Combat Loop

Moment-to-moment, every fight is a **3-resource conversation**: their damage vs. your Defense Bar, your Mana, and your Virtual Health — in that order of consumption.

```
ENGAGE
  │  (you choose: melee weave, spell cast, ranged poke, trap bait)
  ▼
ELEMENTAL RESIST  ── their hit type checked vs your affinity profile (Fire/Ice/Lightning/Earth/Water/Void)
  ▼
ARMOR (≤40% cap)  ── flat-ish mitigation, can NEVER trivialize a hit
  ▼
DEFENSE BAR       ── absorbs the remainder; DRAINS on hit, audible/visual crack
  │   bar empty?
  ├─ no  → you took zero HP damage; back off 7s to regen the bar
  └─ yes → overflow spills into VIRTUAL HEALTH (the danger state)
  ▼
YOUR OFFENSE
  ├─ Melee: Blade/Weapon/Dual-Wield/Martial procs → Critical Strike rolls → combo counter climbs
  ├─ Spells: spend Mana → Elemental Infusion applies status (burn/chill/shock/etc.)
  └─ Ranged: Ranged Precision falloff + crit
  ▼
KILL → floaty damage crescendo, combo bonus banked, XP to the skills used, loot rolled vs Luck/Magic-Find at mob's tier
  ▼
DECOMPRESS (7s no-combat) → defense bar regenerates, mana ticks back (50/s) → re-engage
```

**The feel we are protecting:**
- **The Defense Bar is the heartbeat.** It is your skill-expressive buffer. A good player rotates engagements so the bar eats the hits and HP is rarely touched; HP loss = a *mistake*, not a *cost of doing business*. This makes the 7-second out-of-combat regen window a real tactical decision (push vs. peel).
- **Mana is tempo.** Casters live and die by the 1000/50ps economy and shields; running dry mid-fight is the caster's "out of position."
- **Procs are punctuation.** Skill procs and crits should land as discrete, readable events (sound + number color), not a beige stat soup. The combo counter rewards aggression without forcing it.
- **Combat has a rhythm of compression and decompression** — burst danger, then a deliberate breather. This is what keeps farther-out mobs *tense* rather than just spongy.

---

### 3. The Gather / Craft Loop (How Gathering Feeds Combat Power)

Gathering is the **economy that fuels the build**, and it is gated by the same distance curve — the best mats live where the worst mobs do.

```
EXPLORE OUTWARD → harvest tier-scaled nodes (Mining/Woodcutting/Herbalism/Farming/Fishing/Scavenging)
        │  (yield & rarity scale with distance + Luck/Magic-Find + the relevant gather skill)
        ▼
REFINE (Smithing / Alchemy / Cooking) → tier-quality intermediate goods
        ▼
CRAFT (Crafting) → gear at a tier ≤ your input mats + your skill tier
        ▼
AUGMENT (Enchanting + Elemental Infusion + future -Nomicons/keyed armor)
        ▼
EQUIP → raises your effective combat tier → unlocks safely farming the NEXT distance band
        ▼  (loop closes: better gear → go farther → better mats)
```

Design rules that keep this loop honest:
- **Inputs gate outputs.** You cannot craft above the tier of your materials *or* your skill — both must be present. This prevents a high-skill player face-rolling endgame gear from spawn-tier scraps, and vice versa.
- **Gathering is a parallel progression spine, not a chore tax.** Cooking (HP/regen food), Alchemy (combat potions), Smithing (armor/weapons) each feed combat *directly*, so a player who loves gathering is building power, not just grinding currency.
- **Repairing + entropy durability (roadmap)** turns gear into something you maintain, giving Repairing/Smithing a permanent role and preventing "craft once, win forever."

---

### 4. The Exploration Loop (Distance = Danger = Reward)

Exploration is the **master loop** that contains the other two. The core covenant: **the game pays you exactly as much as it threatens you.**

```
                 spawn (safe, lvl ~1 mobs, WEAK/COMMON loot)
                   ●───────────────────────────────────────▶ frontier (lvl 100, GODLY/PRIMORDIAL)
   distance band:  HOME      NEAR        MID        FAR        DEEP        PRIMORDIAL
   mob level:      1–15      16–35       36–55      56–75      76–92       93–100
   loot ceiling:   COMMON    UNCOMMON    RARE/EPIC  LEGENDARY  PERFECT/MYTHIC  GODLY/PRIMORDIAL
   what you do:    learn     gear up     specialize push limits siege/farm   flex/endgame
```

- **Chunk discovery** rewards first-time exploration (small one-shot bonus + map fill), nudging players to keep walking rather than camping one node.
- **Blink Gates (roadmap)** are the pressure-release valve and the *aspirational* mechanic: establishing a gate in DEEP/PRIMORDIAL country is a mid-to-late goal that converts a dangerous expedition into a repeatable commute. Gates should be **expensive to place far out** and **never trivialize the walk for under-geared players** (e.g., gate cost scales with destination distance band).
- **The frontier is a treadmill that moves the player, not the numbers.** We never re-scale spawn to stay relevant; instead the player's reason to go out is always "the next band has loot I can't get here." This is how distance stays meaningful for the life of a character.

---

### 5. The 30-Minute Session (Early / Mid / Endgame)

**Early player (skills <30, HOME→NEAR band):**
1. (0–5m) Top up: cook food, repair gear, check defense-bar tier.
2. (5–20m) Push one band out, clear mobs in the NEAR band, gather the best nodes they can safely reach. Defense bar gets tested; they learn the 7s peel rhythm.
3. (20–28m) Craft/enchant one upgrade from the haul. Tangible power bump.
4. (28–30m) Bank loot, set a Blink Gate goal. **Session win = one gear tier or ~5 skill levels.**

**Mid player (skills 30–70, MID→FAR band):**
1. (0–3m) Restock potions (Alchemy), prep an elemental loadout for the target zone.
2. (3–22m) Expedition to FAR band: fight level-60+ mobs where defense-bar management is mandatory, farm RARE/EPIC mats, chase a specific LEGENDARY drop via Magic-Find.
3. (22–28m) Refine + craft toward a build keystone (e.g., infusing a weapon to match their affinity).
4. (28–30m) Drop or upgrade a Blink Gate to shorten the next run. **Session win = a build piece, not just stuff.**

**Endgame player (skills 70–100, DEEP→PRIMORDIAL):**
1. (0–2m) Quick buff stack; teleport via Gate network to the frontier.
2. (2–20m) Farm GODLY/PRIMORDIAL content, run an Echo Family ritual (SAND/TIDE/ASH/FROST/MIRE) for tokens, or contest a PvP outpost (roadmap).
3. (20–28m) Spend tokens / push an -Nomicon / chase a MYTHIC→GODLY craft. Marginal-but-meaningful power.
4. (28–30m) Maintain entropy durability, reinforce frontier holdings. **Session win = prestige & optimization, the long tail.**

---

### 6. Long-Term Goals & Macro Progression Spine

The spine has **five rails that never all finish at once**, guaranteeing there's always a "next thing":

| Rail | What it is | Caps the player against |
|---|---|---|
| **Skill rail** | Level the 37 skills toward 100 | Their own time & specialization choices |
| **Gear rail** | Climb the 10-tier ladder WEAK→PRIMORDIAL | Material/distance access |
| **Distance rail** | Physically reach & survive farther bands | Their current effective tier |
| **Ritual rail** | Echo Families tokens, Reservoir Inversion, Spaceman Armor | Endgame mat/token sinks |
| **Identity rail** | Badge (20-tier), keyed armor, PvP tiers, contracts | Account-level prestige & social standing |

The **macro arc**: *Learn the systems at home → gear into the mid-game → commit to a build identity → establish frontier infrastructure (Gates/outposts) → chase the ritual & prestige long tail.* No single rail is a hard wall; players who hate one (e.g., PvP) can advance others, but the deepest power requires touching several.

---

### 7. Balance Philosophy (Anti-Power-Creep Doctrine)

The guiding law, born from the **First Aid landmine** (`level × 1500` = 150,000 HP at level 100): **no stat may scale unbounded with level, and no single source may dominate a stat's budget.** Concretely:

**Rule 1 — Everything has a hard ceiling.**
- Virtual Health, defense-bar size, elemental resist %, armor (already ≤40%), crit chance, and movement speed all have **hard caps** enforced at the manager layer, independent of how a buff is computed. Even a bugged formula cannot exceed the cap. (First Aid's HP grant must be re-expressed as a *percentage of a capped HP budget*, not a flat per-level number.)

**Rule 2 — Skills give curved, soft-capped contributions, not linear ones.**
- A skill's contribution follows **diminishing returns** (e.g., `contribution = cap × (1 − e^(−level/k))` or a tiered step table), so level 100 is meaningfully better than 50 but **not 2× better**, and the marginal value of each level shrinks. Linear `stat = level × C` formulas are **banned by policy** — they are the exact shape of the First Aid bug.

**Rule 3 — Stat budgets are shared and capped per category.**
- Each defensive stat (e.g., total damage reduction) has a **global budget** that all sources (armor + skills + infusion + gear + buffs) draw from and **clamp to**. Stacking five 10% sources cannot yield 50% if the budget caps at, say, 75% total mitigation. This kills multiplicative stacking exploits.

**Rule 4 — Player power must TRACK, not OUTRUN, the mob curve.**
- Both player effective power and mob power scale to the same 1–100 cap over the same distance curve. The **target invariant**: at distance band *N*, a player who legitimately geared/leveled through bands 1..N has a **TTK on band-N mobs of ~3–6 seconds** and a **survival margin where the defense bar absorbs a sustained fight but HP is at risk on mistakes.** If players start one-shotting a band, that band's mob curve (not the player) is under-tuned, or a stat broke its cap.

**Rule 5 — The Defense Bar owns TTK pacing.**
- TTK and "time-to-die" are tuned primarily through **defense-bar size and regen**, not raw HP inflation. We want HP pools to stay readable (think hundreds, not hundreds-of-thousands). Making fights longer = bigger/faster bar; making them more lethal = smaller bar or faster drain. This keeps the numbers legible and the HUD honest, and it means we **never need to inflate HP to add difficulty** — the precise failure mode First Aid represented.

**Rule 6 — Soft caps before hard caps.**
- Approaching a cap should feel like wading into mud (steep diminishing returns) *before* hitting the wall, so players sense the ceiling rather than slamming it. Hard caps are the safety net; soft caps are the design.

**Rule 7 — Tiers are multiplicative steps with a fixed ratio, audited centrally.**
- Each tier step (WEAK→...→PRIMORDIAL, 9 steps) applies a **fixed, configurable power ratio** (e.g., ~1.35× per step) defined in ONE place. No system may invent its own tier multipliers. This makes the whole economy tunable from a single curve and prevents one subsystem from quietly out-scaling the rest.

**Rule 8 — New systems pass a "creep budget" review.**
- Any new power source (Echo tokens, -Nomicons, Spaceman Armor) must declare which capped budget it draws from. If it grants something *new*, it gets its own cap. Nothing ships with an uncapped `× level` term. This is the institutional defense against the next First Aid.

---

### 8. Onboarding / New-Player Experience (First 10 Minutes)

The custom systems are invisible to a vanilla brain, so we **teach by doing, near spawn, where mistakes are cheap.**

| Time | Beat | Teaches |
|---|---|---|
| 0–1m | Spawn in HOME band; HUD lights up (HP / Mana / Defense Bar) with a one-line tooltip each. | The three resources exist and are distinct from hearts. |
| 1–3m | First easy mob. Floaty numbers + combo counter fire on every hit. Defense bar visibly drains then **regens after 7s**. | Combat is numeric; the bar is your buffer; backing off restores it. |
| 3–5m | Gather a starter node; yield popup shows tier + a "this feeds crafting" hint. | Gathering → power, distance = better mats. |
| 5–7m | Guided first craft/cook; the new item shows its **tier color**. | The unified tier ladder; inputs gate output tier. |
| 7–9m | A signposted "the world gets harder that way →" prompt points outward; a slightly tougher mob proves the defense bar matters. | Distance = danger = reward; defense management. |
| 9–10m | Pick a starting skill focus / affinity hint; set first goal (reach NEAR band). | Specialization is a choice; here's your next session's aim. |

Principles: **never wall a new player behind a system they haven't seen**, surface one mechanic per beat, and make the **first defense-bar break a teaching moment, not a death.** Tooltips and HUD coachmarks do the talking; no wiki required.

---

### 9. Feedback & Juice (How the Game Communicates State)

Custom systems are only as good as their legibility. Everything that changes a resource must **announce itself** through a redundant channel (visual + audio).

- **HUD (persistent):** three stacked bars — Virtual Health, Mana, Defense Bar — each color-keyed; defense bar shows a distinct "cracking" state as it drains and a "recharging" shimmer during the 7s regen. Combo counter and current combat tier sit near the reticle.
- **Floaty numbers:** every hit spawns a number colored by damage type (Fire=orange, Ice=cyan, Lightning=yellow, Earth=brown, Water=blue, Void=purple, physical=white); **crits are bigger and punchier**, procs get a tagged label.
- **Combo counter:** climbs with sustained offense, resets on a long gap; feeds a visible bonus so aggression is *seen* to pay off.
- **Audio layer:** distinct cues for crit, proc, **defense-bar break** (the single most important non-HUD signal — "you are now spending HP"), mana-empty, level-up, and tier-up. The defense-bar break sound is sacred: it's the audible line between "fine" and "in danger."
- **State transitions are loud:** entering combat, the 7s decompression starting, a tier-up on gear/skill, a chunk discovered — each gets a brief, non-spammy flourish so progression is *felt*.
- **Anti-spam discipline:** batch/throttle floaty numbers in AoE, cap simultaneous sounds, and never let juice obscure the HUD. Legibility beats spectacle.

The throughline: a player should be able to **close their eyes and still know** whether their defense bar just broke, whether a crit landed, and whether they leveled — and with eyes open, know their exact standing in all three resources at a glance.

---

### Open Decisions (Gameplay & Balance)

- **Tier power ratio:** pick the exact per-step multiplier (e.g., 1.30× vs 1.40×) for the 9 tier steps — sets the entire economy's slope.
- **Skill diminishing-returns curve:** choose the formula/`k` (exponential vs tiered step table) and each skill's hard-cap contribution to its stat budget.
- **Defensive budget cap:** set the global max total damage reduction (e.g., 70% vs 80%) that armor + resist + bar + skills clamp to.
- **TTK targets per band:** lock the intended kill time and survival margin per distance band, and the mob-level → distance mapping resolution.
- **Virtual Health budget:** decide the capped HP range across levels (hundreds vs low-thousands) now that flat-per-level grants are banned.
- **Defense-bar tuning levers:** finalize base size, drain rate, regen rate, and the 7s out-of-combat threshold per tier — the primary TTK dials.
- **Mana economy for casters:** confirm 1000/50ps fits build diversity, or whether casters need affinity-scaled pools/regen.
- **Blink Gate cost curve:** how gate placement/use cost scales with destination band, and whether under-geared players are soft-blocked from far gates.
- **Specialization pressure:** is there an explicit cap on simultaneously "maxed" skills, or is time the only limiter? (Affects build identity strength.)
- **Reservoir Inversion balance:** how swapping HP/MP interacts with the capped budgets and the defense bar (does inverted HP still feed bar overflow?).
- **PvP power normalization:** in risk zones/sieges, are stats normalized toward a band, or is it gear-and-skill-uncapped (frontier "might makes right")?
- **First Aid remediation spec:** the exact replacement formula (percentage-of-capped-budget) and migration for existing saves carrying the legacy grant.
- **Chunk-discovery reward size:** keep it a small one-shot nudge vs. a meaningful resource, without it becoming a farmable exploit.
- **Echo Family identity:** whether the five families (SAND/TIDE/ASH/FROST/MIRE) map onto the six elemental affinities or form an orthogonal progression axis.
