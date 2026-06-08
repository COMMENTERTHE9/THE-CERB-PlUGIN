# Cerberus — Game Design Plan

*Generated 2026-06-08 from an 8-domain multi-agent design pass, grounded in the live codebase (real 10-tier system, the 35+ skills, and the systems already built). Companion checklist: [OPEN_DECISIONS.md](OPEN_DECISIONS.md).*

## Executive Summary

## Cerberus: One Game, Synthesized

**The pitch.** Cerberus is a radial-difficulty RPG survival server where *distance from spawn IS the difficulty curve*. Every meter you walk outward raises mob level (1 per 150 blocks, capped at 100 = ~14,850 blocks to the edge). On top of that raw curve sit five **Echo Families** (SAND, TIDE, ASH, FROST, MIRE) that theme the world into concentric **Bands**, each mapped 1:1 onto the existing unified tier ladder (WEAK→COMMON→UNCOMMON→RARE→EPIC→LEGENDARY→PERFECT→MYTHIC→GODLY→PRIMORDIAL). You read the map with your feet: the geography teaches progression, the families give you a faction to commit to, and the tier of everything you fight, loot, and craft rises together as you go deeper. At cap, the **Depth Spiral** raids and **Abyssal Ladder** boss-rush convert the finite distance curve into infinitely-scaling instanced content via a separate Threat tier, while a four-mode PvP layer and seasonal Prestige give the grind a destination.

**How it interlocks into one game.** Cerberus already has a remarkably complete engine: a unified `Tier` enum carrying armorPenetration/xpMultiplier/craftStatMultiplier, 35 use-leveled skills, a 3-layer defense pipeline (raw → elemental resist → armor → defense bar → health), virtual-health and mana pools, config-driven mob resolution (MobRegistry), and live HP/damage scaling keyed to spawn distance. The eight domains are NOT eight features — they are one loop seen from eight angles:

1. **The world is the difficulty dial.** A new `ZoneManager`/`BandResolver` reads `MobHealthScaling.levelForDistance()` + biome + config to resolve any location → Band + Family + risk profile, with NO region-editor (that was deleted). The same distance metric drives mob tier, loot tier, PvP risk class, and Blink Gate gating.
2. **Families are the spine.** The five mob families ARE the faction system. Same-family kills drop family tokens; tokens feed attunement; attunement unlocks keyed armor sets and family rituals; rituals trigger targeted boss spawns; bosses are the sole source of Nomicons and keyed gear.
3. **Gear is the central balance lever.** Player HP is hard-capped (~1,000 from skills, ~1,500 with gear) and offense is clamped (~6.0x stacked combat multiplier) against a 100-HP base, so the *only* way mobs stay threatening deep out is gear+tier. Every weapon/armor number is calibrated against the real mob HP curve, with gear tier gated to the Band you earned it in.
4. **Signature verbs make it Cerberus, not generic.** Reservoir Inversion (swap HP%/MP% as a panic button), elemental reactions (8 ElementTypes as a combo grammar), reactivated Defensive Magic (Folia-friendly, no ProtocolLib), traps as a hunting pillar, and roaming Sea Monster apexes are the texture layered on the loop.
5. **Endgame closes the loop.** Skill cap + the 5,000-HP natural ceiling are the *start* of endgame, not the end: Depth Spiral/Abyssal Ladder bypass the cap via Threat scaling, seasonal Reservoirs + Echelon prestige + the 20-tier Inventory Badge are the terminal sinks for Marrow (soft currency), Echo Shards (family-premium), and Entropy (durability fuel).

**The one blocking decision everything waits on.** The 5,000-HP cap collapses the entire top half of the tier ladder in the midgame — with the live numbers (base 20 HP, 6/level, PRIMORDIAL ×60) a PRIMORDIAL mob flatlines at ~level 3, MYTHIC at ~33, PERFECT at ~51. Four of ten tiers are indistinguishable by HP across most of the world. This must be fixed *first* (recommendation below: a per-tier health-cap map), because it silently breaks mob authoring, gear calibration, zone design, and endgame Threat scaling simultaneously. It is a config + tiny code change, not a rewrite — the engine is sound; only the cap is wrong.

## Design Pillars

1. Distance is destiny — geography is the only difficulty UI. One radial level curve (150 blocks/level → L1..L100) drives mob power, loot tier, zone risk, and travel gating. No minimap difficulty markers, no region editor: where you stand resolves deterministically to a Band + Family + risk profile from distance + biome + config.
2. Families are the commitment axis. Five Echo Families (SAND/TIDE/ASH/FROST/MIRE) theme the world into Bands, gate keyed armor, supply tokens, and host rituals/bosses. Choosing a family is the meta-progression decision; switching is deliberately expensive (token sink + cooldown).
3. Gear is the balance lever, not stats-from-leveling. Player HP and damage are hard-capped against a 100-HP base (~1,000–1,500 HP, ~6.0x offense). Mobs stay lethal at depth only through tier+gear, so itemization carries the whole power budget and stays the designer's single tuning knob.
4. Tiers mean the same thing everywhere. WEAK→PRIMORDIAL is ONE ladder for mobs, gear, loot, crafting, and zones — calibrated against the real MobHealthScaling/MobDamageScaling curves. A per-tier health-cap replaces the flat 5,000 cap so all ten tiers actually differentiate at endgame.
5. Signature verbs over feature count. Reservoir Inversion (HP%/MP% swap panic button), 8-element reaction grammar, Folia-friendly Defensive Magic (no ProtocolLib), traps-as-hunting, and roaming apex Sea Monsters are what make Cerberus feel distinct — built on existing managers, not net-new engines.
6. Config-driven and Folia-safe by default. New content ships through mobs.yml / combat.yml / zone config and existing managers (MobRegistry, AsyncSaveManager, PlayerSessionManager). All persistence follows the snapshot-on-entity-thread → async I/O → reschedule pattern. New tables auto-create like the existing five.
7. Anti-power-creep is a first-class constraint. Kill the runaway formulas (skill XP 100·2^99, First-Aid +1500 HP/level, gathering +1100%) at the spine: a sane hybrid XP curve, capped defensive/offensive ceilings, and capped gathering quantity with rarity moved to a separate luck roll.
8. Endgame is infinite by design, finite by power. Depth Spiral / Abyssal Ladder Threat scaling bypasses the HP cap for scaling challenge, but power permanently caps at PRIMORDIAL — past T20 awards only cosmetics + leaderboard score. Prestige (Echelon) and the 20-tier Inventory Badge are sinks, not power inflators.

## Recommended Build Order

1. **0. Hygiene + the HP-cap fix (UNBLOCK EVERYTHING)** — Commit the 99 modified/18 new files already staged (P0 fixes + region removal), delete the accidental Creb/ nested git repo, git rm --cached target/ and dependency-reduced-pom.xml, and update stale CLAUDE.md/AI_HANDOFF.md (Paper 1.21.1 → Folia 1.21.11, DecentHolograms/region refs gone). THEN fix the HP-cap collapse: replace the flat 5000 cap in MobHealthScaling with a per-tier health-cap map (recommended: WEAK 200 → COMMON 600 → ... → MYTHIC ~12k → GODLY ~20k → PRIMORDIAL ~35k). This is the single decision every other domain depends on — gear calibration, zone authoring, boss HP models, and Threat scaling all assume tiers differentiate at endgame. It is a config+small-code change against an already-correct engine. Also decide the 10x damage cap question (PRIMORDIAL L100 only reaches ~8.5x — either lower the cap to ~9 or raise PRIMORDIAL's tier-base so the ceiling is meaningful).
2. **1. Number lock-down (the shared-constants treaty)** — Before authoring content, ratify the cross-domain numbers in ONE place (combat.yml + a design constants doc): player HP ceiling (~1,000 skill / ~1,500 gear), offense clamp (6.0x), the new per-tier HP cap map, the COMMON sword reference base damage (proposed 7.0, the anchor for the entire weapon-power budget), the pvp-damage-multiplier (proposed 0.6, applied post-defense-bar), and Reservoir Inversion's swap model (SWAP-BY-FILL-PERCENTAGE, not raw values). Every later phase reads these; locking them now prevents the domains from drifting into contradictory tunings.
3. **2. Progression spine rewrite (the grind must be sane first)** — Rewrite the broken skill XP curve (100·2^99 is unplayable) to the hybrid polynomial (~19M XP/skill to 100), and audit all 35 skills to keyed, idempotent, ScalingFormula-based stat sources. Kill First-Aid's +1500 HP/level and cap gathering quantity at +50% (rarity → separate luck roll). Unify LuckManager + MagicFindManager and reconcile the ghost 'Magic Find'/'Exploration' skill lookups. This is foundational because gear, loot tables, and zone tuning all balance against the player-power-per-level budget — which is meaningless until the XP curve and skill effects are bounded.
4. **3. Bestiary + Families as data (mobs.yml, no new code)** — Author the mob ecosystem as mobs.yml registry entries: five families × six archetypes (tank/skirmisher/caster/swarm/support/elite) + a Wild pool, each with an ElementalTag affinity. This requires NO new resolution code (MobRegistry already does O(1) tag/name/pattern resolution and MobScalingListener already applies tier+level). Resolve the open scaling questions here: keep apply-to:MONSTERS with a curated entity-type allowlist for non-Monster scaled mobs; family tokens drop only from same-family kills (feeds Echo progression); mob XP feeds a configurable skill set. Families-as-data must exist before zones (which theme around them) and gear (keyed to them).
5. **4. ZoneManager / BandResolver (geography layer)** — Build the read-only ZoneManager that resolves location → Band + Family + risk from distance + biome + config, reusing MobHealthScaling.levelForDistance() (extract distanceToSpawn() to share). Inject the Band floor-tier rule into the spawn pipeline (generic mobs force-promoted to the Band's floor tier; named mobs.yml specials never downgraded). Add Tier.max() helper. This depends on Families (phase 3) for theming and unblocks Blink Gates, family rituals, and the PvE/PvP risk gradient. Defer Outpost sieges (the only piece needing new persistent world-state).
6. **5. Weapons & Gear itemization (the balance lever, now calibrated)** — With the HP curve, per-tier caps, and reference base damage locked, build the single stat-budget model off Tier.craftStatMultiplier, named weapon classes mapped to the real combat skills, armor sets feeding the armor/defense-bar layers, the PDC affix system, and reforge/temper/Nomicon paths. Migrate the duplicate Quality enums (CraftingManager + QualityBuffs) onto the unified Tier. Wire the Dual-Wielding offhand path that doesn't exist today. Fix the broken parseElementalTag round-trip. Gear comes after mobs+zones because its numbers are meaningless without the curve it's tuned against and the Bands that gate its drops.
7. **6. Signature systems (make it Cerberus)** — Now that the loop is playable, layer the identity verbs: Reservoir Inversion (swap-by-fill-% through PlayerManaManager + PlayerVirtualHealthManager, gated behind Intelligence/ArcaneKnowledge), the 8-element reaction grammar with deterministic resolution order, Folia-friendly Defensive Magic reactivation (offhand catalyst + sneak-combo input, reusing the dormant Shield/Ward/Barrier classes), traps-as-hunting, and Sea Monster apexes. Add the player_echo table (auto-create + AsyncSaveManager). Resolve the TIDE/FROST ICE collision (split into specialEffect variants). These hook the existing combat pipeline at defined layer-insertion points without breaking the 3-layer math.
8. **7. Blink Gates + economy sinks** — Build the Blink Gate fast-travel network on ChunkDiscoveryManager (new discovered_gates table mirroring visited_chunks), with outward-blink costing mana+tokens, inward-blink cheap, combat-tagged players blocked in HIGH/LETHAL Bands, and USE gated by boss-kill/Inventory-Badge-tier/Nomicon (kills the 'ferried to Primordium at level 5' exploit). Wire the three-currency economy (Marrow/Echo Shards/Entropy) with structural sinks and the 20-tier Inventory Badge (gated on Mastery Level = sum of skills /10). Depends on zones (gate prerequisites) and gear (Entropy durability).
9. **8. Endgame, PvP & seasons (close the loop)** — Fork ThreatScaling from MobHealthScaling/MobDamageScaling for Depth Spiral instanced raids + Abyssal Ladder (bypass the per-tier HP cap via Threat tier; power caps at PRIMORDIAL, past-T20 = cosmetics/leaderboard only). Build the four-mode PvP layer (Tiers, Outpost sieges, Risk Zones, Gate contestation) with PvP-aware DamageResolution routing using the locked pvp-damage-multiplier. Add seasonal Reservoirs + Echelon prestige + the new persistence tables (player_pvp_rating, warbands, descent_depth, leaderboards). Last because it consumes every prior system (Families, gear, zones, signature verbs, economy) as its content and reward substrate.


---

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

---

## Bestiary &amp; Mob Types

This domain defines every hostile creature in Cerberus: how mobs map to the ten tiers, how they scale with the distance-based level engine, which Echo Family they belong to, what role they play in a fight, and how they telegraph danger. Everything here is expressed through systems that **already exist in code** — `MobRegistry` (mobs.yml), `MobHealthScaling`, `MobDamageScaling`, `Tier`, and `ElementalTag` — so v1 ships as config plus a small spawn-control layer, not a rewrite.

### 1. Grounding in the real scaling engine

The numbers below are computed from the **actual** formulas in the repo, not invented.

- **Level from distance** (`MobHealthScaling.levelForDistance`): `level = clamp(1 + floor(distance/150), 1, 100)`. So level 1 at spawn, level 11 at 1500 blocks, level 51 at 7500, level 100 at ~15000 blocks out.
- **Health** (`MobHealthScaling.calculate`): `maxHealth = clamp((20 + (level-1)*6) * tierMult, 1, 5000)`.
- **Melee damage multiplier** (`MobDamageScaling.calculate`): `mult = min(tierBase * (1 + 0.55*log1p(level/25)), 10.0)`, applied on top of the vanilla/base mob attack.

Real tier multipliers from `Tier.java` / `MobHealthScaling` / `MobDamageScaling`:

| Tier | HP mult | dmg tierBase | armor-pen | XP mult | Display color |
|------|---------|--------------|-----------|---------|---------------|
| WEAK | 0.5x | 0.85 | 0% | 0.5x | Gray |
| COMMON | 1.0x | 1.00 | 0% | 1.0x | White |
| UNCOMMON | 1.5x | 1.15 | 10% | 1.5x | Green |
| RARE | 3.0x | 1.35 | 20% | 2.0x | Blue |
| EPIC | 6.0x | 1.75 | 30% | 3.0x | Dark Purple |
| LEGENDARY | 10.0x | 2.25 | 40% | 4.0x | Gold |
| PERFECT | 16.0x | 2.50 | 50% | 5.0x | Yellow |
| MYTHIC | 25.0x | 3.00 | 60% | 7.0x | Red |
| GODLY | 40.0x | 3.75 | 70% | 10.0x | Dark Red |
| PRIMORDIAL | 60.0x | 4.50 | 80% | 15.0x | Light Purple |

> Note: `armorPenetration` on `Tier` is currently consumed by the *player* combat path. For mobs it is the natural lever to make high-tier mobs bypass player armor — fold it into the mob→player damage path so a PRIMORDIAL mob ignores 80% of the player's armor layer (Layer 2 of the 3-Layer Defense). This is the cleanest way to keep elite mobs threatening without raising raw damage past the 10x cap.

#### 1a. THE CAP LANDMINE (read this first)

Computed from the live formula, here is the level at which each tier **slams into the 5000 HP cap**:

| Tier | Hits 5000 cap at level | Uncapped HP @ L100 |
|------|------------------------|--------------------|
| WEAK | never (307 @ L100) | 307 |
| COMMON | never (614) | 614 |
| UNCOMMON | never (921) | 921 |
| RARE | never (1842) | 1842 |
| EPIC | never (3684) | 3684 |
| LEGENDARY | L81 | 6140 |
| PERFECT | **L50** | 9824 |
| MYTHIC | **L31** | 15350 |
| GODLY | **L19** | 24560 |
| PRIMORDIAL | **L12** | 36840 |

**Consequence:** past the midgame, a PRIMORDIAL, GODLY, MYTHIC and (above L50) PERFECT mob are *all 5000 HP* — four tiers become identical in durability. Tier stops meaning anything for tankiness exactly where the endgame begins. **This is the single most important balance decision in this domain.** Recommended fix (pick one):

1. **Raise the cap to ~40,000** and let the formula breathe (PRIMORDIAL L100 = 36,840 sits just under). Simple, but a single mob with 40k HP is a slog for a solo player.
2. **Per-tier caps** (new config): WEAK/COMMON 600, …, EPIC 6k, LEGENDARY 10k, PERFECT 16k, MYTHIC 25k, GODLY 40k, PRIMORDIAL 60k. Preserves tier identity; needs a `health-cap-per-tier` map added to `MobHealthScaling`.
3. **Keep 5000 as a soft cap for HP but differentiate elites by armor-pen, abilities, and add-spawns instead of raw HP.** Cheapest; leans on the design below.

The bestiary below assumes **per-tier caps (option 2)** because it makes the ten tiers legible at a glance and matches the roadmap's tier-driven gear/loot intent.

### 2. Echo Families &amp; the neutral Wild pool

The roadmap's **Echo Families** (SAND, TIDE, ASH, FROST, MIRE) become the mob ecosystem's factional spine. Each family owns an elemental identity (mapped to the real `ElementalTag.ElementType`), a biome/zone bias, a signature mechanic, and a token currency that feeds the Echo Families progression system.

| Family | Element (ElementalTag) | Biome / zone bias | Signature mechanic | Token | Telegraph color |
|--------|------------------------|-------------------|--------------------|-------|-----------------|
| **SAND** | EARTH (+WIND on elites) | Deserts, badlands, savanna, dry mesas | Burrow/ambush, sandstorm slow, armor-shred | Glass Shard | Tan/gold dust particles |
| **TIDE** | ICE-as-water + LIGHTNING | Oceans, rivers, beaches, swamp edges | Pull/displace, wet status (slows + amplifies LIGHTNING), drowning pressure | Brine Pearl | Aqua bubbles |
| **ASH** | FIRE (+ DARK on elites) | Nether-adjacent, volcanic, scorched, deep caves | Ignite DoT, explode-on-death, heat aura that drains player health-regen | Cinder Mote | Red/orange embers |
| **FROST** | ICE | Snowy peaks, frozen ocean, ice spikes, tundra | Freeze/slow stacking, brittle (next hit +dmg), shatter-on-frozen | Rime Crystal | Aqua/white snow |
| **MIRE** | DARK (+ BINDING on elites) | Swamps, dark forest, deep dark, mushroom | Poison/wither DoT, root/bind (uses BINDING), summon spores, life-leech | Spore Sac | Dark-gray/green murk |
| **Wild** (neutral) | none / mixed | Everywhere; vanilla biomes | Baseline filler; promotable into a family by rituals | (none) | Plain gray name |

**Family assignment is purely a `mobs.yml` field plus a name prefix today** — no resolution code changes. Use `custom-name-pattern` to recognize family mobs (e.g. `^\\[ASH\\] .*`) and the existing PDC tagging for plugin-spawned ones. The family identity drives spawn rules (Section 5), loot (cross-ref Loot domain), and the Echo Families ritual triggers (Section 8).

### 3. Archetype roles

Six combat roles, each defined by how it uses the existing systems (virtual health pool, defense bar, mana, elemental infusion, status DoTs from `CustomDamageType`):

| Role | Fantasy | HP bias | Damage bias | Key behavior | Tier sweet-spot |
|------|---------|---------|-------------|--------------|------------------|
| **Tank** | Slow wall | High (use full tier HP mult) | Low–med | Soaks hits, blocks line, knockback-resistant; bleeds player's defense bar | RARE–GODLY |
| **Skirmisher** | Fast harasser | Low–med | Med, fast | High move speed, leaps/dashes, flanks, low HP "delete me first" | COMMON–EPIC |
| **Caster** | Ranged threat | Low | High burst (elemental) | Casts elemental projectiles/AoE using family element + DoT; must be approached or LoS-broken | UNCOMMON–MYTHIC |
| **Swarm** | Death by a thousand | Very low (WEAK/COMMON HP mult, even at high tier-name) | Very low individually | Spawns in packs of 4–12; overwhelms via numbers; trivial XP each | WEAK–UNCOMMON |
| **Support** | Force multiplier | Med | Near-zero | Buffs allies (haste, shields via the disabled Defensive Magic classes once re-enabled), heals, raises shields; priority kill | RARE–LEGENDARY |
| **Elite / Boss** | Set-piece | Tier HP cap | Tier dmg + abilities | Phases, ability rotation, telegraphed specials, add-spawns; fires `CerberusMobIdentifiedEvent` | EPIC–PRIMORDIAL |

A mob's **role decouples from its tier**: a swarm mob can be named MYTHIC for loot/XP purposes while keeping a WEAK-equivalent HP override so the pack stays fair. Implement role-specific HP via a per-mob `health-mult-override` (new optional field — see Section 9).

### 4. Sample bestiary (named mobs across all tiers)

HP/dmg shown at a representative spawn level using the **real** formula (with per-tier caps from Section 1a). "dmgMult" is the `MobDamageScaling` output at that level.

| # | Name | Family | Tier | Role | Element | Lvl(dist) | HP | dmgMult | Signature ability | Telegraph |
|---|------|--------|------|------|---------|-----------|----|---------|-------------------|-----------|
| 1 | Dust Mite | SAND | WEAK | Swarm | EARTH | L1 (spawn) | 10 | 0.87 | Pack of 8, burrow-emerge | Sand puff on spawn |
| 2 | Husk Drifter | SAND | COMMON | Skirmisher | EARTH | L3 (~300b) | 32 | 1.07 | Sprint + sand-slow on hit | Kicks up dust trail |
| 3 | Tide Nipper | TIDE | WEAK | Swarm | ICE | L2 (~150b) | 13 | 0.92 | Schools of 6, applies Wet | Bubble ring |
| 4 | Brine Lurker | TIDE | UNCOMMON | Skirmisher | LIGHTNING | L10 | 111 | 1.36 | Hook-pull, +dmg vs Wet targets | Aqua tether beam |
| 5 | Cinder Hound | ASH | COMMON | Skirmisher | FIRE | L8 | 62 | 1.16 | Leap + ignite (BURN_DOT) | Ember trail, growl |
| 6 | Ashen Brute | ASH | RARE | Tank | FIRE | L20 | 402 | 1.79 | Heat aura (suppresses player regen), knockback slam | Red ground-crack ring |
| 7 | Frost Wisp | FROST | UNCOMMON | Caster | ICE | L12 | 130 | 1.41 | Frost bolt, stacks Slow→Freeze | Floating ice shards |
| 8 | Rime Sentinel | FROST | RARE | Tank | ICE | L25 | 492 | 1.87 | Brittle aura (frozen players take +25%) | Crystalline armor sheen |
| 9 | Mire Spitter | MIRE | UNCOMMON | Caster | DARK | L15 | 150 | 1.52 | Poison glob (POISON_DOT), AoE puddle | Dripping green mouth |
| 10 | Spore Warden | MIRE | RARE | Support | DARK | L30 | 582 | 1.94 | Spore cloud heals/cleanses allies | Pulsing green motes |
| 11 | Glasswing Reaver | SAND | EPIC | Skirmisher | WIND | L40 | 1524 | 2.67 | Dash-flurry, armor-shred (−1 player armor layer/hit) | Whirling glass blades |
| 12 | Maelstrom Caller | TIDE | EPIC | Caster | LIGHTNING | L45 | 2104 | 2.92 | Chain-lightning vs grouped/Wet players | Storm clouds overhead |
| 13 | Pyre Colossus | ASH | LEGENDARY | Tank | FIRE | L50 | 3140 (cap 10k) | 3.61 | Explode-on-death (CUSTOM_EXPLOSION), magma trail | Glowing seams, rumble |
| 14 | Glacial Warden | FROST | LEGENDARY | Tank | ICE | L55 | 3680→cap | 3.86 | Shatter (frozen player → burst dmg), reflect-chill | Frozen breath fog |
| 15 | Bog Tyrant | MIRE | LEGENDARY | Elite | DARK | L60 | 6140→cap10k | 4.18 | Root (BINDING), wither aura, summons 4 Mire Spitters | Black tendrils erupt |
| 16 | Dune Leviathan | SAND | PERFECT | Boss | EARTH+WIND | L60 | 16000 (cap) | 4.18 | Burrow phases, sandstorm blind, surface-slam shockwave | Screen-wide tremor |
| 17 | Abyssal Sovereign | TIDE | MYTHIC | Boss | LIGHTNING+ICE | L75 | 25000 (cap) | 5.29 | Tidal-pull → drown zone, lightning rain phase 2 | Water pillars rise |
| 18 | Infernal Archon | ASH | MYTHIC | Boss | FIRE+DARK | L80 | 25000 (cap) | 5.49 | Meteor telegraphs, ash-storm regen-lock, adds | Sky darkens, fire rain markers |
| 19 | Hoarfrost Monarch | FROST | GODLY | Boss | ICE | L90 | 40000 (cap) | 6.90 | Total-freeze ultimate (telegraphed 3s), ice-clone adds | Arena frosts over |
| 20 | Rotcrown Hierophant | MIRE | GODLY | Boss | DARK+BINDING | L95 | 40000 (cap) | 7.13 | Bind-all + wither nova, spore-swarm summon, life-drain | Green ritual circle |
| 21 | Cerberus, the Echo-Eater | (all) | PRIMORDIAL | Boss | rotates all 5 | L100 (~15k blocks) | 60000 (cap) | 8.48 | 5-phase, one element/family per phase; consumes adds to heal | Per-phase color shift |
| 22 | Sand Wraith (rare roamer) | SAND | RARE | Skirmisher | EARTH | scales w/ dist | per dist | — | Rare-spawn ambusher, drops Glass Shards | Shimmering mirage |
| 23 | Drowned Echo (rare roamer) | TIDE | RARE | Support | LIGHTNING | scales w/ dist | per dist | — | Buffs nearby TIDE mobs' speed | Ghostly aqua glow |
| 24 | Emberling Swarmcaller | ASH | EPIC | Support | FIRE | L42 | 1632 | 2.74 | Summons Cinder Hounds, hastes ASH allies | Drumbeat, ember halo |
| 25 | Voidtouched Stalker | MIRE | PERFECT | Elite | DARK | L55 | 14080→cap16k | 3.86 | Blink-teleport, invisible until 4 blocks, execute on low HP | Brief shadow flicker |

These map cleanly to existing `mobs.yml` patterns: family roamers use `custom-name-pattern` (e.g. `^Sand Wraith.*`), bosses use `custom-name-exact` (e.g. "Cerberus, the Echo-Eater") or PDC tags for plugin-spawned ones.

### 5. Spawn rules — zone × biome × distance × family

Because level is already a pure function of distance from spawn, the **tier ceiling should also rise with distance** so you don't see PRIMORDIAL mobs at the spawn town. Proposed **tier-by-distance band** (the *maximum* tier that can roll; lower tiers stay common throughout):

| Distance band | Level range | Allowed tiers (weighted) | Family elites? | Boss spawns |
|---------------|-------------|--------------------------|----------------|-------------|
| 0–1000 (Hearth) | L1–7 | WEAK 50%, COMMON 45%, UNCOMMON 5% | no | none |
| 1000–3000 (Frontier) | L7–21 | COMMON 45%, UNCOMMON 35%, RARE 18%, EPIC 2% | rare RARE | rare family mini-boss |
| 3000–6000 (Marches) | L21–41 | UNCOMMON 30%, RARE 40%, EPIC 25%, LEGENDARY 5% | yes (EPIC) | family elites |
| 6000–10000 (Wastes) | L41–67 | RARE 25%, EPIC 40%, LEGENDARY 28%, PERFECT 7% | yes | LEGENDARY bosses |
| 10000–14000 (Echofields) | L67–94 | EPIC 25%, LEGENDARY 40%, PERFECT 25%, MYTHIC 9%, GODLY 1% | yes | MYTHIC/GODLY |
| 14000+ (The Maw) | L94–100 | LEGENDARY 20%, PERFECT 30%, MYTHIC 30%, GODLY 15%, PRIMORDIAL 5% | yes | PRIMORDIAL (Cerberus) |

**Biome → family** mapping decides *which* family's mob set rolls inside the tier the band allows (e.g. at 8000 blocks in a desert you get a SAND mob of EPIC/LEGENDARY; in adjacent ocean you get TIDE). Wild pool mobs roll anywhere as filler. **Implementation:** a new `MobSpawnController` listening on `CreatureSpawnEvent` *before* `MobScalingListener` (lower priority so it runs first) that: (a) reads distance + biome, (b) rolls a family + tier from the band table, (c) replaces or tags the spawning entity with the chosen `mobId`. This is the **only meaningful new code** the domain needs; everything downstream (HP/damage/level/XP) already works.

**Density &amp; pack rules:**
- Swarm mobs spawn in groups: roll one "lead" swarm mob, then spawn `4 + floor(level/15)` siblings within 6 blocks (cap 12).
- Tank+Caster pairs: a Tank has a 25% chance to spawn with a Caster of one tier lower (a wall + a poke threat is the core "interesting" encounter).
- Support mobs never spawn alone — only attached to a 2+ mob group, max one Support per group.
- Elites/bosses are gated by a per-chunk and per-player cooldown to avoid stacking (cross-ref Async/Session — reuse `PlayerSessionManager` for per-player boss cooldown).

### 6. Elemental affinities, resistances &amp; the rock-paper element wheel

Mobs carry their family element (`ElementalTag.ElementType`). Use it two ways:

1. **Offense:** the mob's hits apply that element's DoT/effect via the existing `CustomDamageType` family (BURN_DOT for ASH, FREEZE_DOT for FROST, POISON_DOT for MIRE, MANA_DRAIN for arcane elites, etc.).
2. **Defense:** mobs resist their own element and are vulnerable to a counter — implemented as a per-mob resistance map that the player damage path already supports via custom-damage-type resistances.

Proposed **element counter wheel** (offense element → strong vs / weak vs target family):

| Player infusion (ElementalTag) | Strong vs | Weak vs | Rationale |
|-------------------------------|-----------|---------|-----------|
| FIRE | FROST, MIRE | TIDE, ASH | melts ice, burns rot; doused by water, ASH is fireproof |
| ICE | ASH, SAND | TIDE, FROST | freezes flame & loose sand; tide stays fluid, frost immune |
| LIGHTNING | TIDE | SAND (grounded) | water conducts; sand insulates |
| EARTH | LIGHTNING-casters, TIDE | ASH | earths out shocks, dams water; cracks in heat |
| DARK | FROST, TIDE | MIRE | corrupts the pure; mire is already corrupt |
| WIND | ASH (scatters embers) | SAND (feeds storms) | — |

Wet status (TIDE) is a cross-element amplifier: a Wet target takes **+30% LIGHTNING** and **−25% FIRE** — this lets TIDE mobs set up combos and gives players a reason to read elemental telegraphs. Resistance magnitudes can reuse the existing `ElementalTag.fromString` resistance values (FIRE 0.1, ICE 0.2, EARTH 0.3, etc.) scaled by tier.

### 7. Special abilities catalogue (by role, all expressible with existing systems)

| Ability | Used by | Mechanic / system hook |
|---------|---------|------------------------|
| **Burrow-Ambush** | SAND skirmishers/elites | despawn-render, re-emerge behind player; spawn invuln 0.5s |
| **Hook-Pull / Tidal Pull** | TIDE | velocity vector toward mob; sets Wet |
| **Ignite Aura** | ASH tanks | suppresses player `healthRegenCombatRate` while in radius (already a config lever) |
| **Explode-on-Death** | ASH | fire CUSTOM_EXPLOSION on death event |
| **Freeze Stacking** | FROST | applies FREEZE_DOT; at 5 stacks → rooted 2s ("Frozen") |
| **Shatter** | FROST tanks | bonus burst dmg if target is Frozen |
| **Root / Bind** | MIRE elites | BINDING element; immobilize 1.5s, telegraphed |
| **Spore Heal** | MIRE support | heals allies % of their max HP/sec in cloud |
| **Armor-Shred** | SAND/WIND elites | temporarily nullifies one of the player's armor pieces (Layer 2) |
| **Summon Adds** | all bosses | spawn N lower-tier same-family mobs; consumes them to heal (Cerberus) |
| **Blink** | MIRE PERFECT | short teleport toward/away; breaks player LoS for casters |
| **Phase Shift** | PRIMORDIAL | rotate element + resistance each phase; forces player to swap infusion |
| **Mana Burn** | arcane elites | MANA_DRAIN type — punishes mage builds; ties into Mana System |

### 8. Roadmap integration

- **Echo Families:** The five families ARE this bestiary's factions. Family-specific **rituals** (roadmap) trigger targeted spawns: performing a SAND ritual spawns a Dune Leviathan event regardless of distance band (with level/tier set by the ritual altar's distance). **Family tokens** drop only from same-family mobs (Section 2 token column), feeding the progression loop. Killing a family elite advances that family's standing.
- **Gear Progression / Nomicons / keyed armor:** Bosses (EPIC+) are the only source of **Nomicon** drops; **keyed armor** drops only from the matching family's GODLY/PRIMORDIAL bosses (FROST armor key from Hoarfrost Monarch, etc.). The **entropy durability** system means farming high-tier mobs degrades gear — pacing the grind. (Cross-ref Gear/Loot domains.)
- **PvP Tiers &amp; Zones:** The distance bands in Section 5 double as PvE risk zones; the deepest band (The Maw) is the natural high-risk PvP overlap.
- **Blink Gates:** Boss arenas become discoverable fast-travel anchors once cleared (cross-ref Chunk Discovery — `ChunkDiscoveryManager` already tracks visited chunks).
- **Reservoir Inversion:** Mana-Burn elites and the PRIMORDIAL boss's mana phases create the "critical moment" the Reservoir Inversion mechanic is designed for — swap HP/MP to survive a mana-locked phase.
- **Inventory Badge:** Token/loot volume from swarms gives early Badge-tier fodder; elite drops gate higher Badge tiers.

### 9. Config &amp; code touch-points (minimal-change plan)

- **Ships today with zero code changes:** add the 25 mobs to `mobs.yml` using existing `classification` (now tier names), `xp-multiplier`, and `match` rules. Use `custom-name-pattern` for families, `custom-name-exact`/`pdc-tag` for bosses. `MobScalingListener` already scales their HP and shows the level in the name (note: it only relabels *un-named* mobs, so named family mobs keep their family name — correct behavior).
- **Small additions for the full vision:**
  1. `health-cap-per-tier` map in `MobHealthScaling` (fixes the cap landmine — Section 1a).
  2. `health-mult-override` / `damage-mult-override` optional per-mob fields so swarm/tank roles decouple HP from tier-name.
  3. A `MobSpawnController` listener (priority below `MobScalingListener`) implementing the distance×biome×family band table (Section 5).
  4. Wire `Tier.armorPenetration` into the **mob→player** damage path so elite mobs bypass the player's armor layer.
  5. A `family` field on `MobEntry` (or derive from name prefix) so loot/rituals can query it without re-parsing names.
- **Folia note:** `CreatureSpawnEvent` fires on the owning region thread (the code comments confirm this), so the spawn controller mutates entities inline with no scheduling. Add-spawns from bosses must use `entity.getScheduler()` if they cross regions — keep summons local to the boss's chunk.

### What you're probably missing

- **The 5000 HP cap silently erases your four top tiers in the endgame.** Until you fix it (Section 1a), PERFECT/MYTHIC/GODLY/PRIMORDIAL feel identical to fight past the midgame. This is the highest-leverage decision in the whole bestiary.
- **`MobDamageScaling` caps at 10x but tier-bases only reach 4.5x** — even PRIMORDIAL L100 only hits ~8.48x, never the 10x ceiling. The ceiling is dead config. Either lower it to ~9 (truthful) or raise PRIMORDIAL's base, otherwise the cap is a lie.
- **`apply-to: MONSTERS` means only `Monster` instances scale.** Several thematic mobs (Slime, Magma Cube, Hoglin, Phantom, Ghast, Shulker) are **not** `Monster` in the Bukkit hierarchy and will spawn at vanilla HP/level 1 — so a "Ghast" caster wouldn't scale at all. You need either `apply-to: ALL` (which then scales passive animals too — bad) or a curated allowlist of entity types. This will bite you the first time you build a non-Monster boss.
- **Level is *purely* horizontal distance from world spawn.** There is no Y-axis or biome-difficulty input. A player who tunnels straight down at spawn fights level-1 mobs in the deep dark; a player who walks 15k blocks in any safe direction fights level-100 mobs with no warning. Consider a biome/depth modifier or at least signposted zone borders.
- **Multiple spawn worlds / the Nether & End share one spawn point per world.** Distance-from-spawn is per-world; the Nether's 8:1 compression means Nether mobs level up 8x faster in overworld-equivalent distance. Decide per-world `blocks-per-level` or you'll get level-100 piglins 1875 Nether-blocks out.
- **Boss HP at the cap is a solo-player slog.** A 40k–60k HP boss against one player with capped weapon scaling can take many minutes. You likely want boss HP to scale with *nearby player count*, or phases that gate-check DPS, rather than a single giant sponge.
- **No "elite/affix" layer between named bosses and generic mobs.** Borderlands/Diablo-style random affixes (e.g. a normal mob rolls "Vampiric" or "Volatile") give huge replayability cheaply and slot perfectly into the existing PDC-tag + `MatchReason` system without authoring 200 named mobs.
- **Telegraphing on Folia without ProtocolLib is constrained.** No packet-level boss bars or fancy client effects via ProtocolLib (it was removed). You're limited to particles, sounds, `BossBar` API, custom-name updates, and entity glow. Plan telegraphs around those primitives now, not around a future packet system.
- **Despawn / persistence of named elites.** Named mobs (`setCustomName`) resist despawn in vanilla, so EPIC+ mobs you spawn will accumulate and leak if players disengage. Add a leash-radius despawn or TTL for plugin-spawned elites, or your far-out chunks fill with abandoned bosses.
- **XP economy blowout:** PRIMORDIAL gives 15x XP and you intend players to farm them. With 30+ skills at level cap 100, decide whether mob XP feeds *all* skills or only combat skills, or far-out grinding trivializes the whole skill tree.

---

## Bosses & Encounter Design

### 1. Encounter Design Philosophy

A Cerberus boss is not a vanilla mob with a fat HP bar. It is a **stateful, multi-system puzzle** that interrogates whether a player has actually engaged with Cerberus's mechanics: the 3-Layer Defense, Mana economy, Elemental Infusion, and the 44 combat/magic/utility skills. The core design rule:

> **No single stat solves a Cerberus boss.** A pure-tank with 5000 HP, a pure-mage with a 1000 mana pool, and a glass-cannon crit build should each *fail differently* against the same boss, and each must adapt rather than out-stat it.

**How a Cerberus boss exercises each existing system:**

| System | How bosses use it |
|---|---|
| **3-Layer Defense** (elem resist → armor ≤40% → defense bar) | Bosses deal **mixed damage packets** in one attack (e.g. 60% Fire + 40% physical) so a single resistance never zeroes a hit. "Bar-breaker" attacks deal flat **defense-bar-only** damage that ignores armor cap, punishing players who never refill the bar. "Pierce" attacks bypass the bar entirely and hit Virtual Health, punishing players who hide behind the bar and never dodge. |
| **Mana** | Some bosses apply **Mana Burn** (drain MP on hit) and **Silence fields** (no spellcasting in zone), forcing martial players to carry the fight. Reservoir Inversion bosses (see ASH) literally swap your HP and MP mid-fight, weaponizing your build against you. Mana shields are explicitly counterable by **shield-shatter** attacks. |
| **Elemental Infusion** | Every boss has an **affinity** (resist its own element ~75%) and a **vulnerability** (take +50% from the opposing element per the Infusion wheel: Fire↔Water, Ice↔Fire, Lightning↔Earth, Earth↔Lightning, Water↔Void, Void↔Water). Players who infuse the *wrong* element waste a phase; the fight teaches the wheel. |
| **Combat skills** | Telegraph windows are tuned so **Ranged Precision** can punish from safe range, **Critical Strike**/**Blade Mastery** reward burst inside short openings, **Heavy Armor Training** gates whether bar-breakers one-shot you, and **Martial Expertise** dodge/parry timing maps to telegraph cadence. **Dual Wielding** vs two-handed is a real choice on tight DPS checks. |
| **Utility skills** | **First Aid** out-of-combat ticks gate sustain on long fights; **Alchemy** potions counter status (Antidote vs Mire poison, Warmth vs Frost slow); **Survival** reduces environmental arena damage; **Navigation** unlocks Blink-Gate fast access to remote arenas; **Trap Mastery** lets players pre-place Cerberus Traps in the arena (see Summoning). |

**Distinctiveness pillars (every boss must hit at least 3):**

1. **Telegraph-and-window** — every lethal attack is readable (particle/sound/block cue) with a fixed wind-up; reaction, not RNG, decides outcome.
2. **Resource conversation** — the fight forces an MP/bar/HP trade-off the player must actively manage, not autopilot.
3. **Phase identity** — each phase changes the *question being asked* (DPS check → survival check → add-management → elemental swap), not just the number.
4. **Counterplay over gear-check** — there is always a *skill* answer, not only a *stat* answer.
5. **Arena as a character** — the floor, hazards, and adds are part of the boss, not set dressing.

---

### 2. Tier & Difficulty Ladder

Bosses occupy **EPIC and above** on the unified tier system. Each tier band defines the boss's base HP multiplier (feeding the existing `(base + level*hpPerLevel)*tierMult` formula, but **bosses ignore the 5000 cap** — see Folia tuning), its level band, and the *intended* gear tier of the player.

| Boss Tier | Mob Level Band | Boss HP (solo baseline) | Enrage Timer | Player Gear Expected | Zone Band |
|---|---|---|---|---|---|
| **EPIC** | 30–50 | 25,000 | 6:00 | RARE–EPIC | Inner zones (mini-bosses, family entry) |
| **LEGENDARY** | 50–70 | 70,000 | 5:30 | EPIC–LEGENDARY | Family heartlands |
| **PERFECT** | 65–80 | 150,000 | 5:00 | LEGENDARY–PERFECT | Family apex |
| **MYTHIC** | 80–90 | 320,000 | 4:30 | PERFECT–MYTHIC | World-tier raids |
| **GODLY** | 90–100 | 650,000 | 4:00 | MYTHIC–GODLY | Convergence raids |
| **PRIMORDIAL** | 100 (capped) | 1,200,000 | hard 3:30 | GODLY–PRIMORDIAL | Endgame singletons |

**Intended progression order** (gates explained in §6):

```
SAND (EPIC) → TIDE (LEGENDARY) → ASH (PERFECT) → FROST (MYTHIC) → MIRE (MYTHIC)
        ↓ (all 5 family-keyed armor sets collected)
   CONVERGENCE (GODLY) → THE WARDEN OF CERBERUS (PRIMORDIAL)
```

Each family has a **3-rung internal ladder**: Herald (EPIC entry mini-boss) → Matriarch/Patriarch (the family's main LEGENDARY+ boss) → the family contributes one component to the GODLY Convergence raid. The Spaceman Armor meme reward drops *only* from the PRIMORDIAL Warden.

---

### 3. Boss Roster Overview

| Boss | Family | Tier | Zone | Element (affinity / vuln) | Signature Mechanic |
|---|---|---|---|---|---|
| **Grit-Herald Khamûn** | SAND | EPIC | Sunken Dunes | Earth / Lightning | Sand-shroud (sight-blocking sandstorm + burrow ambush) |
| **Dune-Matriarch Sa'irah** | SAND | LEGENDARY | Glasspike Wastes | Earth / Lightning | Glassing — vitrifies the floor into shatter-tiles |
| **Tide-Caller Nessuvar** | TIDE | LEGENDARY | Drowned Trench | Water / Void | Whirlpool arena + summons Sea Monsters |
| **Cinder-Patriarch Vol'Khar** | ASH | PERFECT | Emberfall Caldera | Fire / Ice | Reservoir Inversion — swaps your HP/MP mid-fight |
| **Frost-Empress Lysithea** | FROST | MYTHIC | The Rime Vault | Ice / Fire | Absolute Zero — freezes the defense bar solid |
| **Bog-Mother ULGRA** | MIRE | MYTHIC | The Sump | Void / Water | Entropy plague — corrodes gear durability live |
| **The Convergence** | All 5 | GODLY | Echo Nexus | Cycles all 6 | Rotating family phases; demands all 5 keyed sets |
| **The Warden of Cerberus** | — | PRIMORDIAL | The Last Gate | None (adaptive) | Learns/locks the last skill you used |

---

### 4. Detailed Boss Concepts

#### 4.1 Grit-Herald Khamûn — SAND, EPIC (Tutorial-Boss of the boss system)

- **Zone:** Sunken Dunes. **Level:** 40. **HP (solo):** 25,000. **Element:** Earth affinity (75% Earth resist), Lightning vulnerability (+50%). **Enrage:** 6:00 → permanent +100% damage and stacking Sand-Lung (1% max-HP DoT/sec).
- **Arena:** Circular 30-block sand bowl ringed by glass pillars (line-of-sight breakers / LOS lightning rods — Lightning infusions arc off them for bonus AoE, teaching the vuln).
- **Role:** Teaches telegraph-reading, the elemental wheel, and bar management at low stakes.

**Phases:**
- **P1 (100–60% HP) — The Reading.** Khamûn does telegraphed **Sand Lances** (1.0s wind-up, line AoE, mixed 70% physical / 30% Earth, hits defense bar). Every 15s, **Burrow** (vanishes 2s, re-emerges under the nearest player — a 0.6s shadow-on-ground telegraph). Soft check: out-DPS the Sand-Lung stacks.
- **P2 (60–25%) — The Storm.** Spawns a sandstorm: vision cut to ~6 blocks, **Survival** skill reduces the storm's 0.5%/s chip. Summons 4 **Grit-Wraiths** (level 30 adds). Players must kill adds *or* LOS-break behind pillars; ranged builds shine.
- **P3 (25–0%) — Collapse.** Floor sinks into a funnel (Whirlpool-of-sand pulling players center). Khamûn channels **Sandblast** (3.0s, full-arena, **bar-breaker** — drains the entire defense bar then overflows to HP). Counter: stand behind a pillar (LOS) or pop a Mana shield. This is the player's first "the bar alone won't save you" lesson.

- **Loot:** SAND Herald Token x1 (ritual currency), Earthbreaker Mace (EPIC, Earth infusion), 30% Sand-Keyed Helm (entry piece of SAND armor set), Grit -Nomicon page (1 of 5).

---

#### 4.2 Dune-Matriarch Sa'irah — SAND, LEGENDARY (family apex)

- **Zone:** Glasspike Wastes. **Level:** 60. **HP (solo):** 70,000. **Element:** Earth / Lightning vuln. **Enrage:** 5:30.
- **Arena:** 40-block glass plateau. The defining gimmick: Sa'irah **vitrifies floor tiles** — stepped-on tiles after 1.5s become **shatter-glass** that breaks on the next dash, dropping players into a damaging sand pit (escape via climb, costs a phase of DPS).

**Phases:**
- **P1 — Glassing.** Telegraphed cone breath vitrifies a 90° arc; arena slowly becomes a minefield. Heavy Armor Training reduces shatter fall damage.
- **P2 (66%) — Mirror Dunes.** Spawns 2 **Glass-Doubles** (illusory clones at 1 HP that mirror her attacks; killing the wrong target wastes the DPS window — a *target-priority* check). Lightning infusion reveals the real one (it doesn't reflect light).
- **P3 (33%) — Hourglass.** Hard soft-enrage: the arena fills with rising sand from the edges inward (shrinking safe zone). Sa'irah gains **Sandstorm Veil** (60% damage reduction unless hit by Lightning — forces correct infusion). DPS race against the shrinking floor.

- **Loot:** Guaranteed **3 Sand-Keyed armor pieces**, Glasspike Bow (LEGENDARY, Lightning), Sa'irah's Hourglass (consumable: brief time-slow trinket), 2 SAND Matriarch Tokens, completes Grit -Nomicon. **Gate:** full Sand-Keyed set required to survive Convergence's SAND phase environmental DoT.

---

#### 4.3 Tide-Caller Nessuvar — TIDE, LEGENDARY

- **Zone:** Drowned Trench (underwater/flooded arena). **Level:** 65. **HP (solo):** 80,000. **Element:** Water affinity, **Void** vulnerability. **Enrage:** 5:30.
- **Arena:** Submerged amphitheater with a central **maelstrom** that pulls players inward (Navigation/Riding mounts resist pull). Ties directly into the existing **Sea Monster spawning** system.

**Phases:**
- **P1 — Undertow.** Periodic **Tidal Surge** telegraph (water rises 3s warning) — players must reach raised coral platforms or take a bar-breaker wave. Mana shields hold one surge.
- **P2 (70%) — The Drowning.** Arena fully floods; oxygen becomes a resource (Survival/Alchemy water-breathing). Nessuvar summons **3 Sea Monsters** (reuses existing spawner) that must be tanked off the raid.
- **P3 (40%) — Maelstrom.** Central whirlpool intensifies; Nessuvar becomes **untargetable except when struck by Void infusion** (which "stills" the water for a 4s window). **Mana Burn** aura drains 20 MP/s — martial builds carry. Soft-enrage: maelstrom radius grows.
- **P4 (15%) — Last Tide.** Single hard DPS check: **Abyssal Geyser** channel (5s) one-shots via pierce damage unless interrupted; interrupt requires a Void-infused heavy hit (Weapon Mastery/Blade Mastery burst).

- **Loot:** Guaranteed **Tide-Keyed armor pieces**, Maelstrom Trident (LEGENDARY, Water+Void hybrid), Drowned -Nomicon, TIDE Tokens, rare **Leviathan Scale** (Smithing mat for keyed-armor upgrades).

---

#### 4.4 Cinder-Patriarch Vol'Khar — ASH, PERFECT (the build-breaker)

- **Zone:** Emberfall Caldera (lava arena). **Level:** 75. **HP (solo):** 150,000. **Element:** Fire affinity, **Ice** vulnerability. **Enrage:** 5:00.
- **Signature:** First boss to weaponize the **Reservoir Inversion** roadmap mechanic — mid-fight he **swaps each player's HP and MP pools**, so a 5000-HP/1000-MP tank becomes 1000-HP/5000-MP and must survive on a glass-cannon body.
- **Arena:** Caldera with rising/falling lava floor; safe basalt platforms shift each phase (Navigation reads the pattern).

**Phases:**
- **P1 — Forge-Heat.** Stacking arena heat: every 10s, +5% Fire damage taken (Survival + Ice infusion of self counters). Telegraphed **Magma Lance** lines.
- **P2 (75%) — Inversion.** Vol'Khar channels **Reservoir Inversion** (4s telegraph, unavoidable). HP↔MP swap for 45s. Now your mana pool is your life total — Mana Regen skill literally heals you; Mana Burn attacks become *lethal*. The fight inverts every assumption. Reapplies each phase.
- **P3 (45%) — Cinderstorm.** Summons **Ash-Echoes** (adds that explode on death for Fire AoE — corpse positioning matters). Vol'Khar gains a **Mana shield** that must be shield-shattered (heavy burst) before he's damageable.
- **P4 (20%) — Pyre Collapse.** Lava floor rises to near-total; tiny safe platforms. Hard DPS check under Inversion. **Ice infusion doubles damage** this phase — wrong element = enrage wipe.

- **Loot:** Guaranteed **Ash-Keyed armor**, Cinderheart Greatsword (PERFECT, Fire), **Vol'Khar's Reservoir** (trinket: voluntary HP/MP swap on demand — a build-defining item), Ember -Nomicon, ASH Tokens. **Gate:** Ash-Keyed set's Inversion-resist stat is near-mandatory for Convergence's ASH phase.

---

#### 4.5 Frost-Empress Lysithea — FROST, MYTHIC

- **Zone:** The Rime Vault. **Level:** 85. **HP (solo):** 320,000. **Element:** Ice affinity, **Fire** vulnerability. **Enrage:** 4:30.
- **Signature:** **Absolute Zero** — periodically *freezes the defense bar solid*, so it can neither absorb nor regenerate for 8s; players must avoid all damage or eat it raw on Virtual Health.
- **Arena:** Mirror-ice floor (low friction — momentum carries dashes; Navigation/Riding control it) ringed by braziers (Fire-infused hits relight braziers, which thaw a safe zone).

**Phases:**
- **P1 — Frostbite.** Stacking slow (Alchemy Warmth potion / Survival counter). Telegraphed **Ice Spear** volleys (Ranged Precision punishes from range).
- **P2 (75%) — Absolute Zero.** Bar-freeze cycle begins (8s frozen / 12s thawed). During freeze, only dodging (Martial Expertise) or Mana shields save you. Spawns **Rime-Wraiths** that must be melted (Fire).
- **P3 (50%) — Blizzard.** Whiteout vision + arena-wide DoT; players cluster in brazier-thawed zones. Lysithea **shatters frozen players** (if you took a hit while a Freeze debuff is active, you take massive bonus damage — a hard "don't get hit while frozen" check).
- **P4 (25%) — Heart of Winter.** She encases herself in an ice shell (damage-immune) that *only Fire infusion cracks*; a non-Fire raid simply cannot kill her — hard elemental gate. Soft-enrage stacks frostbite to a 1%/s max-HP DoT.

- **Loot:** Guaranteed **Frost-Keyed armor**, Rimepiercer (MYTHIC, Ice), **Heart of Winter** (trinket: on-demand defense-bar freeze immunity, 1/min), Rime -Nomicon, FROST Tokens, **Permafrost Core** (Enchanting reagent).

---

#### 4.6 Bog-Mother ULGRA — MIRE, MYTHIC

- **Zone:** The Sump. **Level:** 88. **HP (solo):** 340,000. **Element:** Void affinity, **Water** vulnerability. **Enrage:** 4:30.
- **Signature:** **Entropy Plague** — live, accelerating **gear durability corrosion** (ties to roadmap Entropy Durability). The longer the fight, the worse your gear performs; broken pieces drop your defense layers. This makes ULGRA a *speed/efficiency* boss, not an attrition one.
- **Arena:** Sinking bog of poison pools; **Repairing** skill mid-fight (field-repair anvil mechanic) becomes relevant; Antidote (Alchemy) counters poison stacks.

**Phases:**
- **P1 — Rot.** Poison pools spread; Entropy aura ticks 1 durability/3s on all worn gear. Telegraphed **Spore Burst** (poison cloud, Antidote/Survival mitigates).
- **P2 (70%) — Swarm.** Summons **Mire-Spawn** waves (uses Mob Registry); each spawn that reaches ULGRA heals her 2% — an add-management/positioning phase. **Trap Mastery** shines: pre-placed Spike/Flame traps thin the swarm.
- **P3 (40%) — Decay Acceleration.** Entropy ticks double (1 dura/1.5s). Players who over-relied on gear now feel naked; **First Aid**/skill-based sustain carries. ULGRA gains **Void-Lash** pierce attacks (bypass bar).
- **P4 (15%) — Dissolution.** Arena floor dissolves to islands; ULGRA **regurgitates** corroded gear stats as projectiles. Water infusion strips her Void shell for the kill window.

- **Loot:** Guaranteed **Mire-Keyed armor**, Entropy Scythe (MYTHIC, Void), **Bog-Mother's Carapace** (trinket: entropy-corrosion immunity aura), Sump -Nomicon, MIRE Tokens, **Incorruptible Ingot** (Smithing — crafts entropy-proof gear).

---

#### 4.7 The Convergence — All Families, GODLY (raid)

- **Zone:** Echo Nexus (where all 5 family echoes bleed together). **Level:** 95. **HP (solo, soloing discouraged):** 650,000; tuned for **5–10 players**. **Element:** cycles all 6. **Enrage:** 4:00.
- **Access gate:** Requires **all 5 family-keyed armor sets** present in the raid (the boss's per-phase environmental DoT can only be survived by wearing the matching keyed set during that family's phase — so a raid must field at least one player per family, or rotate).

**Phases (rotating family wheel):**
- **P1 SAND** — Glassing floor + sandstorm; Sand-Keyed set negates DoT.
- **P2 TIDE** — Flood + Sea Monsters; Tide-Keyed set grants water-breathing.
- **P3 ASH** — Mass Reservoir Inversion (whole raid swaps HP/MP); Ash-Keyed set softens it.
- **P4 FROST** — Raid-wide Absolute Zero; Frost-Keyed set keeps the bar thawed.
- **P5 MIRE** — Entropy storm; Mire-Keyed set halts corrosion.
- **P6 CONVERGENCE (sub-25%)** — all five mechanics fire simultaneously at low intensity; pure coordination check. Hard soft-enrage.

- **Loot:** **Convergence -Nomicon** (capstone, combines all 5 family powers), one guaranteed **GODLY armor token** (upgrades any keyed set to GODLY), Echo Crown trinket, large family-token payout. **Gate:** drops the **Warden Key** needed to open The Last Gate.

---

#### 4.8 The Warden of Cerberus — PRIMORDIAL (endgame singleton)

- **Zone:** The Last Gate. **Level:** 100. **HP (solo):** 1,200,000 (tuned 8–10). **Element:** none innate — **adaptive** (mirrors the element you use most, becoming resistant to it). **Enrage:** hard 3:30 (full wipe — no soft-enrage; the Warden is a *check*, not a war of attrition).
- **Signature — Skill-Lock:** every 20s the Warden **locks the last combat/magic skill you used** (disables it for 30s), forcing players to rotate their entire kit. A one-trick build is physically incapable of clearing.

**Phases:**
- **P1 — Mirror.** Warden adapts to the raid's dominant element; raid must diversify infusions (no mono-element zerg).
- **P2 (66%) — The Lock.** Skill-Lock begins. Mana Burn + intermittent Silence fields force martial/magic hybridization.
- **P3 (33%) — Gauntlet.** Sequentially recreates a signature mechanic from each family boss (a Glassing tile, a Maelstrom pull, an Inversion, an Absolute Zero, an Entropy tick) — a "final exam" of every prior boss. Hard 3:30 enrage looms.
- **P4 (10%) — The Gate Opens.** Warden removes all its own resistances but gains a **pierce-only** rapid combo (pure dodge check, Martial Expertise / no defense layer helps) — the player's reflexes decide it.

- **Loot:** **Spaceman Armor** (the meme endgame reward — full PRIMORDIAL set, jetpack hop + space-cosmetic), Warden's Gavel (PRIMORDIAL, adaptive-element weapon), the only source of **PRIMORDIAL upgrade cores**, capstone -Nomicon, Blink-Gate master node unlock.

---

### 5. Summoning, Rituals & Access

Bosses are **not** wandering spawns — they are **summoned**, tying boss access to the Echo Families ritual system, Traps, and the Sea Monster spawner.

| Access method | Mechanic |
|---|---|
| **Family Ritual** | Combine **family Tokens** (from heralds) + a **family reagent** at the family's ritual altar to summon that family's main boss. SAND needs Grit Tokens + glass; ASH needs Ember Tokens + magma core, etc. (ties to roadmap Echo Families rituals). |
| **Mob-kill triggers** | Killing N family-aligned mobs (Mob Registry tag) in a zone builds a hidden "echo charge"; at threshold, the family Herald auto-spawns (ambient gateway into the boss ladder). |
| **Trap-baited summons** | Placing a **Cerberus Trap** (existing system) at a ritual site and letting a family mob trigger it "feeds" the altar — a Trap Mastery shortcut to charge rituals faster. |
| **Sea Monster escalation** | Killing enough Sea Monsters (existing spawner) in the Drowned Trench raises the **Tide-Caller** without tokens (alt access for fishing/sailing players). |
| **Convergence** | Requires one **completed -Nomicon from each of the 5 families** consumed at the Echo Nexus. |
| **Warden** | Requires the **Warden Key** (Convergence drop) + a synchronized 8-player presence at The Last Gate. |

**Ritual cadence / lockouts:** main-boss rituals have a per-player **20-hour lockout** on guaranteed keyed-armor drops (tokens still drop on repeat kills, so farming is allowed but loot-protected). Heralds have **no lockout** (farmable for tokens). Convergence and Warden are **weekly** lockouts.

---

### 6. Progression Gating

Bosses are the spine of mid-to-endgame progression. Each gate is a **mechanic wall**, not just a stat wall:

- **Keyed armor chain:** Each family boss drops its **keyed armor set** (per-family, roadmap Gear Progression). You *need* the matching keyed set to survive that family's Convergence phase DoT → so you must clear all 5 families before the GODLY raid.
- **-Nomicons:** Each family yields one **-Nomicon** (unique item, roadmap). All 5 → Convergence access. Convergence -Nomicon + Warden → capstone. -Nomicons are the explicit "you have mastered this family" key.
- **Element-gate phases:** Lysithea's ice shell (Fire-only), ULGRA's Void shell (Water-only), Vol'Khar's Inversion phase (Ice-only DPS) all **hard-require correct elemental infusion** — gating Elemental Mastery skill investment.
- **Inventory Badge tie-in:** boss clears award **Badge tier progress** (roadmap 20-tier badge), giving cosmetic/prestige proof of clears.
- **Blink Gates:** boss-zone Blink nodes unlock as you clear each family (Navigation), making remote arenas reachable — progression doubles as map unlock.

---

### 7. Folia Tuning — Solo vs Group Scaling & Threading

**Threading model (hard constraint compliance):**

- A boss is **owned by the region thread** of its arena. **All** boss entity ops (movement, target switch, ability casts, summon spawns, damage application) are scheduled on the **owning entity/region scheduler** (`entity.getScheduler()` / region scheduler), never the global/main thread.
- Ability **timers** use `RegionScheduler.runAtFixedRate` anchored to the arena's region, not Bukkit's global scheduler. Phase-transition state machines live in a per-boss object whose mutations only occur inside region-thread tasks.
- **Cross-region reads** (e.g. a player who dashed into an adjacent region) are resolved by re-scheduling onto the *player's* region for that player's damage/effect application, then handing results back — never blocking-read across threads.
- Add summons (Sea Monsters, wraiths, spawn waves) are spawned via the **region scheduler at the spawn location's region**; the boss holds only UUID references, re-resolving entities on its own thread to avoid cross-thread entity access.
- Arena is **anchored within a single Folia region** wherever possible (one-region arenas avoid cross-thread handoff entirely) — a stated arena-design rule.

**Player-count scaling (computed at the encounter's *start*, re-checked at phase transitions):**

| Players | HP multiplier | Boss damage multiplier | Add count | Notes |
|---|---|---|---|---|
| 1 (solo) | ×1.00 (baseline above) | ×1.00 | ×1 | Solo allowed up to MYTHIC; GODLY/PRIMORDIAL strongly group-tuned |
| 2 | ×1.8 | ×1.05 | ×1 | Sub-linear HP so duos aren't punished |
| 3 | ×2.5 | ×1.10 | ×2 | |
| 4 | ×3.2 | ×1.12 | ×2 | |
| 5 | ×4.0 | ×1.15 | ×3 | Convergence design point |
| 6–8 | ×4.0 + 0.7/player | ×1.15 | ×3–4 | |
| 9–10 | ×4.0 + 0.7/player | ×1.18 (cap) | ×4 | Warden design point |

- **HP scales sub-linearly** with players (so each added player still speeds the kill — no "more players = slower" anti-social math), while **damage scales gently** (so adding bodies doesn't trivialize survival but also never one-shots the squishiest). Damage multiplier is **capped at ×1.18** to keep telegraph dodging the real defense.
- **Bosses bypass the 5000 HP cap** of the standard Mob Health Scaling (a `bossUncapped` flag in the boss definition) — boss HP comes from the tier table × player-count multiplier, computed once and stored, not from the per-mob distance formula.
- **Damage packets still flow through the 3-Layer Defense pipeline** unchanged (elem resist → armor ≤40% → defense bar → Virtual Health), so bosses reuse the existing combat math; only the *magnitudes* and *mixed-packet composition* are boss-specific.
- **Enrage/soft-enrage timers** are region-scheduler countdowns started on first damage; on enrage they flip a boss-state flag read by every ability tick.

---

### Open Decisions (Bosses)

- **HP cap bypass scope:** Confirm `bossUncapped` is the right approach vs. a separate boss HP field — and decide the absolute ceiling for PRIMORDIAL (1.2M solo → up to ~6M at 10 players: is that DPS-feasible with GODLY gear, or does the enrage timer need lengthening?).
- **Solo accessibility cutoff:** Is MYTHIC the right solo ceiling, or should GODLY/PRIMORDIAL be *hard* group-locked (refuse to start with <N players)?
- **Reservoir Inversion as a boss mechanic** before it ships as a player system — do we build Inversion for Vol'Khar first (boss-driven) or wait for the roadmap feature? (Dependency call.)
- **Defense-bar freeze (Absolute Zero):** does freezing the bar require new state in the 3-Layer Defense engine, and is an 8s freeze too punishing at MYTHIC?
- **Entropy durability dependency:** ULGRA's live corrosion needs the roadmap Entropy Durability system — gate ULGRA behind that feature, or ship a boss-local stand-in?
- **Keyed-armor hard gate vs. soft gate:** should Convergence *refuse entry* without all 5 keyed sets, or just make those phases brutally hard (soft gate)?
- **Skill-Lock fairness:** does locking the *last-used* skill feel punishing-but-fair, or should it lock a *random* skill / a *category*? Needs playtest.
- **Lockout lengths:** 20h main-boss keyed-drop lockout and weekly Convergence/Warden — too grindy or too generous for the server's population?
- **Sea Monster reuse:** can the existing Sea Monster spawner be parameterized for boss-add duty (level/tier override), or does it need a boss-add variant?
- **Element vulnerability values:** is +50% from the opposing element and 75% self-resist the right spread, or too swingy (mono-element wipe risk on Lysithea/ULGRA)?
- **Add-heal mechanic (ULGRA/Convergence):** does "adds heal the boss if they reach it" create unfun leash/pathing dependence on Folia's region threading?
- **Telegraph delivery without ProtocolLib:** confirm all telegraphs (particles, block displays, sounds, BossBar) are achievable with vanilla Paper/Folia APIs only — no packet lib.

---

## Weapons & Gear

This domain governs every equippable item in Cerberus: melee/ranged/magic weapons, armor pieces and sets, and the affix/upgrade/acquisition machinery around them. It is the primary **gameplay balance lever** the hard constraints flag — player skills and damage scale hard, but mobs currently lean on vanilla/scaled HP, so gear is what keeps the distance-from-spawn power curve honest.

It builds directly on what already exists in code:
- `Tier` enum (`WEAK..PRIMORDIAL`) with per-tier `craftStatMultiplier` (0.5 → 4.0) and `armorPenetration` (0.0 → 0.80).
- `CombatSkillProcessor` attack pipeline: `rawDamage → baseMod (Blade/Weapon Mastery) → crit (Critical Strike) → elemental (Infusion) → lifesteal`.
- 3-Layer Defense: elemental resist → `PlayerDefenseManager` armor reduction (cap 40%) → `DefenseBarManager` (base 300 pts, 70% max absorption).
- `ElementalInfusionManager` + `ElementalTag` (FIRE, ICE, LIGHTNING, ARCANE, DARK, EARTH, WIND, BINDING).
- `MobHealthScaling` (player 100 HP baseline, mob cap 5000) and `MobDamageScaling` (mob dmg ×1.0 → ×10 cap).

### Design pillars
1. **One stat budget, scaled by tier.** Every item's power is a *budget* derived from `Tier.craftStatMultiplier`. No hand-tuned per-item stats — affixes spend budget. This keeps the unified tier promise and means a Primordial sword is always ~8x a Weak one (4.0/0.5).
2. **Gear is the curve-keeper.** A player at distance must field tier-appropriate gear to dent tier-appropriate mobs. Mob HP scales to 5000; weapon DPS must scale alongside via tier, not just skills.
3. **Weapon class ↔ combat skill is 1:1 where possible.** Each class routes XP and bonuses through an existing skill so itemization reinforces the skill tree rather than competing with it.
4. **Everything lives in PDC, never display-name parsing.** Follow the `cerb_spell` precedent in `CustomItemListener` — all gear metadata is PDC-keyed (`cerb_tier`, `cerb_weapon_class`, `cerb_base_damage`, `cerb_affixes`, `cerb_entropy`, `cerb_keyed_family`).

---

### 1. The per-tier stat budget

We anchor on a single number — **Weapon Power (WP)** and **Armor Power (AP)** — that scales with `craftStatMultiplier`. WEAK=0.5 is the floor, COMMON=1.0 the reference, PRIMORDIAL=4.0 the ceiling.

**Reference (COMMON, 1.0x) base values:**
- Sword class base damage: **7.0** (vs vanilla iron sword 6, diamond 7) — Cerberus raw damage feeds `CombatContext.getRawDamage()`.
- Chestplate base armor reduction contribution: **0.05** (matches current `armorDiamond`).
- Defense-bar contribution from a full armor set: **+100 pts** on top of the 300 base.

**Weapon Power by tier** (base sword damage = `7.0 × craftStatMultiplier`, then class multiplier applies):

| Tier | craftStatMult | Sword base dmg | Affix budget pts | Max affix count | Color |
|------|---------------|----------------|------------------|-----------------|-------|
| WEAK | 0.5 | 3.5 | 1 | 0 | Gray |
| COMMON | 1.0 | 7.0 | 2 | 1 | White |
| UNCOMMON | 1.25 | 8.75 | 4 | 1 | Green |
| RARE | 1.5 | 10.5 | 6 | 2 | Blue |
| EPIC | 1.75 | 12.25 | 9 | 2 | Purple |
| LEGENDARY | 2.0 | 14.0 | 12 | 3 | Gold |
| PERFECT | 2.5 | 17.5 | 16 | 3 | Yellow |
| MYTHIC | 3.0 | 21.0 | 21 | 4 | Red |
| GODLY | 3.5 | 24.5 | 27 | 4 | Dark Red |
| PRIMORDIAL | 4.0 | 28.0 | 35 | 5 | Violet |

> Implementation note: store `cerb_base_damage` as DOUBLE in PDC at craft/drop time so `CombatContext` reads it directly rather than recomputing. `CraftingManager.determineItemQuality()` already rolls tiers from skill level — extend it to write the budget instead of the legacy `Quality` enum (which is a stale 8-tier duplicate; see Open Questions).

**Why these numbers:** at distance 0 a COMMON sword (7 dmg) with mid skills clears a level-1 COMMON mob (~20 HP) in ~3 hits. At max distance a level-100 PRIMORDIAL mob hits the 5000 HP cap; a PRIMORDIAL sword (28 base) × Weapon Mastery LATE_SURGE (~3.0x) × a Critical Strike (~3.0x mult) ≈ 250/hit before elemental — ~20 hits, intentionally a gear+skill+consumable check, not a solo facetank.

---

### 2. Weapon classes & archetypes

Each class is identified by `cerb_weapon_class` PDC and maps to a governing skill. Class multiplier is applied to the tier base damage.

| Class | Base material family | Dmg mult | Speed | Governing skill(s) | Signature mechanic |
|-------|---------------------|----------|-------|--------------------|--------------------|
| **Blade** (sword) | swords | 1.0x | Fast | Blade Mastery + Weapon Mastery | Sweep cleave; Blade Mastery only fires when `isSword()` (already gated in `CombatSkillProcessor.getBladeMasteryBonus`) |
| **Cleaver** (axe) | axes | 1.35x | Slow | Weapon Mastery | High burst, +armor-pen vs shields; consumes more entropy |
| **Spear/Halberd** | tridents/custom | 1.15x | Medium | Martial Expertise + Weapon Mastery | Reach (extra block), disarm synergy with Martial Expertise |
| **Duelist blades** (paired) | swords (offhand) | 0.7x each | Fast | Dual Wielding | Two hits per swing; Dual Wielding governs offhand proc rate |
| **Maul** (heavy 2H) | custom (netherite base) | 1.6x | Very slow | Heavy Armor Training + Weapon Mastery | Stagger (mini-knockback ignoring `meKbResist`), big entropy cost |
| **Bow** | bow | 1.1x ranged | Draw | Ranged Precision | Crit on full-draw headshot; Ranged Precision drives projectile dmg |
| **Crossbow** | crossbow | 1.25x ranged | Reload | Ranged Precision | Pre-loaded burst, armor-pen affix-friendly |
| **Wand/Staff** (caster) | blaze rod / stick base | n/a (spell) | n/a | Arcane Knowledge + Spell Weaving + Elemental Mastery | Holds `cerb_spell` (FireballSpell precedent); WP budget becomes spell-power scalar |
| **Focus/Orb** (off-hand caster) | custom | n/a | n/a | Intelligence + Mana Regeneration | +mana pool / +regen instead of damage; pairs with wand |

**Class → mob-curve interaction:** Maul and Cleaver intentionally trade speed for burst so they remain viable against the 5000-HP cap mobs where fast weapons get out-regened. Bow/Crossbow scale with Ranged Precision so ranged stays competitive at high tiers — critical because high-tier mobs (`MobDamageScaling` up to ×10) punish melee uptime.

---

### 3. Affixes & modifiers

Affixes are the budget-spend layer. Stored as a serialized list in PDC key `cerb_affixes` (one STRING, `;`-delimited `affix:tier` pairs). Each affix costs budget points (column in §1). Prefixes are offensive, suffixes defensive/utility.

**Weapon affixes (prefix):**

| Affix | Effect at +1 | Per-point scaling | Cost/pt | Pipeline hook |
|-------|-------------|-------------------|---------|---------------|
| **Sharp** | +2 flat raw dmg | linear | 1 | adds to `cerb_base_damage` read by `CombatContext` |
| **Brutal** | +4% dmg multiplier | linear, cap +40% | 2 | additive into `baseMod` in `processAttackerSkills` Phase 1 |
| **Keen** | +2% crit chance | adds to Critical Strike roll | 2 | additive before `applyCriticalStrike` roll |
| **Savage** | +0.15x crit mult | linear | 3 | additive into crit multiplier |
| **Vampiric** | +1.5% lifesteal | linear, cap 15% | 2 | adds to `getLifestealPercent` result |
| **Piercing** | +5% armor-pen | stacks w/ `Tier.armorPenetration` | 2 | reduces target `effectiveArmorReduction` in VHM |
| **Elemental (X)** | enables infusion slot of type X | — | 3 (slot) | unlocks `ElementalInfusionManager` infusion |

**Armor affixes (suffix):**

| Affix | Effect at +1 | Cost/pt | Pipeline hook |
|-------|-------------|---------|---------------|
| **of Warding** | +1% armor reduction (still capped 40%) | 2 | `PlayerDefenseManager.updateDefense` per-piece add |
| **of the Bulwark** | +25 defense-bar max pts | 1 | `DefenseBarManager.setBonusMaxPoints` |
| **of Embers/Frost/Storm** | +3% resist to FIRE/ICE/LIGHTNING | 2 | VHM `setFireResistance` etc. (extend beyond Survival) |
| **of Recovery** | +2 defense-bar regen rate | 2 | `defBarRegenRate` per-player override |
| **of Vigor** | +8 max virtual HP | 1 | `setPlayerMaxVirtualHealth` delta |
| **of Fortune** | +luck points | 1 | `LuckManager` temp/perm points |

**Implementation:** a single `GearStatResolver` service recomputes a player's effective stats on equip-change (hook into existing `CustomArmorListener.refreshDefenseNextTick`). Weapon affixes are read lazily inside `CombatSkillProcessor` from the main-hand item's PDC — no per-player armor-style cache needed for weapons since they swap freely.

---

### 4. Armor sets & set bonuses

Armor stays grounded in vanilla material families (so `PlayerDefenseManager.calculateArmorFromGear` and `CombatConfig.getArmorReductionForPiece` keep working) but gains a **set identity** PDC key `cerb_armor_set`. Wearing 2/4 matching pieces grants tiered bonuses. Per-piece base armor reduction = `materialBase × (0.5 + 0.5 × craftStatMultiplier/4.0)` so tier amplifies but the 40% global cap still binds.

**Named armor sets (each themed to play patterns + Echo Families, see Cross-refs):**

| Set | Family theme | 2-piece bonus | 4-piece bonus | Best for |
|-----|-------------|---------------|---------------|----------|
| **Duskplate** (ASH) | heavy | +50 defense-bar pts | +5% all-dmg reduction when below 30% HP | Maul/tank, Heavy Armor Training |
| **Tideweave** (TIDE) | medium | +2 def-bar regen | -1.5s combat regen lockout | sustain bruiser |
| **Dunestride** (SAND) | light | +6% move speed | dodge: 8% chance to negate a hit fully | Duelist/Bow, Stealth |
| **Rimeguard** (FROST) | heavy | +15% ICE resist | reflect 10% of melee as CRYOMANCY (thorns-style) | anti-melee |
| **Mireshell** (MIRE) | medium | +10% POISON/BLEED resist | DOT immunity stacks decay 50% faster | survival/PvE attrition |
| **Spaceman Armor** (meme endgame) | full set only | knockback immunity | low-gravity jump + 20% all resist + cosmetic trail; **no tier scaling — fixed GODLY-equivalent** | endgame flex reward |

Set bonuses route through the same managers: dmg reduction → `PlayerDefenseManager`, def-bar → `DefenseBarManager`, resists → VHM resist maps, speed → attribute modifier. Spaceman Armor is a fixed-stat keyed set (not craftable, not reforgeable) acquired only via its quest chain.

**Keyed armor (roadmap):** `cerb_keyed_family` binds a piece to one Echo Family. Keyed pieces get a +20% stat budget but **only activate their set bonus for players attuned to that family**. This is the per-family progression hook from the Gear Progression roadmap.

---

### 5. Entropy durability (roadmap)

Replace vanilla durability entirely with **Entropy** — a PDC DOUBLE `cerb_entropy` from 0.0 (pristine) to 1.0 (collapsed). Vanilla durability is hidden (`ItemFlag.HIDE_ATTRIBUTES` precedent in `CustomArmor`).

- Each hit dealt/taken adds entropy: weapon `+0.0008 × classWeight`, armor `+0.0005 per hit absorbed`. Maul/Cleaver have higher classWeight (1.5).
- **Entropy degrades stats, doesn't break the item.** Effective stat = `baseStat × (1 - entropy × 0.5)`. At entropy 1.0 the item is at 50% power but never vanishes (avoids the harsh vanilla break; aligns with current `applyDurability` "make useless not broken" intent).
- **Repairing skill / Smithing** reduce entropy. `CraftingManager.applyRepairBonuses` already scaffolds repair effectiveness — repoint it at entropy. Higher Repairing level → more entropy cleared per repair and lower material cost.
- High-tier items (LEGENDARY+) gain `durability_regen` (already a PDC key written by `CraftingManager.addDurabilityRegen`) → passive entropy decay out of combat.

---

### 6. Upgrade, reforge & Nomicon paths

Three distinct progression verbs, each tied to a utility skill:

1. **Reforge (Smithing):** reroll affixes within the item's tier budget. Costs tier-scaled materials. Smithing level raises the floor of rerolled affix tiers (mirrors `determineItemQuality`'s level→quality bias). Cannot change tier.
2. **Temper / Upgrade (Smithing + Enchanting):** spend tier-shards to push an item up one tier (e.g. RARE→EPIC), raising its budget and unlocking an extra affix slot. Capped per item by a `cerb_temper_count` to prevent infinite climbing; soft cap at +3 tiers above craft tier.
3. **Nomicons (roadmap unique items):** named, fixed-affix legendaries that **cannot be reforged or tempered** but have a unique mechanic affix unavailable elsewhere. Each Nomicon is GODLY/PRIMORDIAL budget with one signature effect.

**Sample Nomicons:**

| Nomicon | Class | Tier | Signature effect |
|---------|-------|------|------------------|
| **Cinderfang** | Blade | GODLY | Kills reset Critical Strike cooldown-equivalent; ignites in a 3-block arc (PYROMANCY) |
| **Tideturner** | Spear | GODLY | On disarm proc (Martial Expertise), heals 15% of dmg as virtual HP |
| **The Hollow Aught** | Wand | PRIMORDIAL | Reservoir Inversion trigger item — swaps HP/MP on full-mana cast (ties roadmap Reservoir Inversion) |
| **Graveline** | Crossbow | PRIMORDIAL | Bolts pierce all targets in a line; armor-pen 100% vs mobs below 25% HP |
| **Mantle of the Nine** | Chest (keyed, all families) | PRIMORDIAL | Counts as 4-piece of whatever family set you wear the rest of |

---

### 7. Acquisition sources

Gear enters the economy through multiple, tier-gated channels — distance-from-spawn is the master gate (it already drives mob level/tier via `MobHealthScaling.levelForDistance`).

| Source | Tier range | Driver | Notes |
|--------|-----------|--------|-------|
| **Crafting/Smithing** | WEAK → PRIMORDIAL (skill-rolled) | Crafting/Smithing level via `determineItemQuality` | The reliable backbone; high variance at low level |
| **Mob drops** | matches mob `Tier` (±1) | `MobKillListener` + `LuckManager` | Distance gates tier since far mobs are higher tier |
| **Echo Family rituals** | family-keyed UNCOMMON+ | Echo Families tokens | Only path to keyed armor |
| **Boss drops** | EPIC → GODLY guaranteed slot | tagged boss mobs in `MobRegistry` | Nomicon drop pool |
| **Scavenging/Fishing/Mining** | WEAK → RARE components | gathering skills + `ResourceYieldManager` | Feeds crafting materials, not finished gear |
| **Nomicon quests** | fixed GODLY/PRIMORDIAL | scripted | One-time, account-bound |
| **Spaceman Armor quest** | fixed (meme endgame) | scripted | Cosmetic-priority reward |

`LuckManager` (already tracks luck factor for loot rolls) biases the *tier* of dropped/crafted gear upward — fold luck affixes (`of Fortune`) and `magic_find` (the `player_magic_find` DB table) into this roll.

---

### 8. Worked damage examples (sanity check vs mob curve)

- **Early (dist 0, COMMON sword, lvl 10 skills):** raw 7 × Weapon Mastery (~1.1) × no crit = ~7.7/hit vs a level-1 COMMON mob (20 HP) → 3 hits. Healthy.
- **Mid (dist ~7500 = ~lvl 50, EPIC blade w/ Brutal+2, lvl 50 skills):** raw 12.25 +Sharp → base mod (Blade+Weapon ~1.4) ≈ 17 × occasional crit (~2x) ≈ 34. Target: level-50 RARE mob ≈ (20+49×6)×3.0 ≈ 942 HP → ~28 hits, with crits/elemental ~18. Tense but doable — gear matters.
- **Endgame (dist 15000 = lvl 100, PRIMORDIAL Maul w/ 5 affixes, lvl 100 skills):** raw 28×1.6 class +Sharp ≈ 50 → baseMod (~3.0) ≈ 150 × crit (~3.0) ≈ 450 + elemental ≈ 550. Target: level-100 PRIMORDIAL mob = 5000 HP cap → ~9-12 hits. A genuine duel, exactly the "main balance lever" the constraints want.

These confirm gear (not skills alone) must keep pace — a fully-skilled player in COMMON gear at max distance does ~30/hit into 5000 HP (167 hits) and gets deleted by ×10 mob damage. **Gear tier ≈ mob tier is mandatory**, which is the intended pressure.

---

### What you're probably missing

1. **Two stale duplicate quality enums.** `CraftingManager.Quality` and `QualityBuffs.Quality` are pre-unification 8-tier enums (missing LEGENDARY/MYTHIC, with different multipliers — QualityBuffs goes to 3.5x, the real `Tier` to 4.0x). They contradict the unified `Tier`. Itemization will silently fork unless these are deleted and both classes switched to `Tier`. This is a landmine, not a nice-to-have.
2. **`QualityBuffs` mutates vanilla attributes you've otherwise abandoned.** It calls `setBaseValue` on `MAX_HEALTH`/`ATTACK_DAMAGE`/`ARMOR` and adds raw `SHARPNESS` enchants — but your whole combat path is virtual-health + `cerb_base_damage`. Vanilla attack damage doesn't even feed `CombatContext`. This system is dead weight that will double-count or no-op; itemization should ignore vanilla attributes entirely except `ARMOR` for cosmetic HUD (already done in `PlayerDefenseManager`).
3. **The 40% armor cap makes flat armor affixes worthless at high tier.** Once a Primordial set hits 40%, "+1% armor" affixes do nothing. You need the budget to flow into uncapped layers (defense-bar pts, resists, regen) at high tier — the affix table above deliberately weights that way, but you must *enforce* it or players waste budget.
4. **Weapon stats can't be cached per-player like armor.** Armor recalcs on equip-change; weapons swap every tick in combat. `CombatSkillProcessor` must read weapon PDC live each hit. Cheap, but don't accidentally build a stale weapon-stat cache.
5. **Crit can double-dip with affixes.** `Keen`/`Savage` feed the same `Critical Strike` roll/multiplier the skill already scales via `ScalingFormula`. Decide additive-before-roll vs multiplicative-after, or crit chance trivially hits 100% with stacked Keen + high skill. Recommend: affixes add to chance *before* the single roll, cap total chance at the skill's `HARD_CAPPED` ceiling (0.75).
6. **Entropy needs an async-safe write path.** Entropy ticks on every hit, which happens on the entity thread — fine — but persistence must go through `AsyncSaveManager` snapshots (the threading constraint forbids Bukkit item calls off-thread). Don't write PDC from an async task.
7. **Dual Wielding and offhand have no pipeline today.** `CombatSkillProcessor` only reads `getItemInMainHand()`. Duelist class + Dual Wielding skill require a new offhand-hit path; this is net-new code, not config.
8. **Elemental infusion currently overwrites and double-modifies display names.** `ElementalInfusionManager` and `CraftingManager.applyElementalInfusion` both append to the display name and the parse-back (`parseElementalTag`) returns hardcoded placeholder multipliers (1.0) — so infusion damage is effectively lost on reload. Itemization that relies on infusion budget must fix the round-trip serialization first.
9. **No item-level cap on total power.** With reforge + temper + affixes + infusion + Nomicon-adjacent stacking, you can exceed the intended Primordial budget. Define a hard per-item WP/AP ceiling and clamp at resolve time, independent of how stats were acquired.
10. **PvP scaling divergence.** Gear tuned against 5000-HP mobs will one-shot 100-HP players. Weapons need a `vs-player` damage scalar (likely ~0.25x) or PvP becomes instant — relevant to the roadmap PvP Tiers domain.

---

## Progression, Economy & Itemization Loops

This domain is the load-bearing spine of Cerberus. Every other system (combat, mobs, gear, Echo Families) plugs power *into* curves defined here. The single most important discovery during design review: **two existing curves are broken and will sabotage everything built on top of them**, so they are addressed first as non-negotiable foundations.

---

### 0. Two foundational bugs that gate this whole domain

| Bug | Location | Current behavior | Why it breaks progression |
|-----|----------|------------------|---------------------------|
| **Exponential XP curve** | `SkillManager.calculateRequiredXpForNextLevel`: `100 * 2^(level-1)` | Level 2 = 100 XP, Level 30 = 53 billion XP, Level 100 = `100 * 2^99` (~6.3e31) | Level cap 100 is **mathematically unreachable**. Past ~level 25 no human grinds it. The whole "skills cap at 100" design is fiction until this is replaced. |
| **First-Aid HP runaway** | `FirstAidSkill.applyEffect`: `level * 1500.0` max HP, plus `(level-100)*3000` above 100 | At level 100: +150,000 max HP on a 100-base-HP player. Mob damage cap is 10x so a PRIMORDIAL lvl-100 hit ~caps near 50 damage. Player becomes literally unkillable. | This is the canonical power-creep landmine. It must be rewritten to `ScalingFormula` (see §2.4). |

Both are flagged in the baseline hard constraints. **No progression tuning is meaningful until the XP curve is replaced.** Proposed replacement is §1.

---

### 1. Skill XP curve & the level-100 cap

#### 1.1 Replacement curve — hybrid polynomial (per-skill, cap 100)

Replace `100 * 2^(level-1)` with a **per-level cost** that is smooth, hand-tunable, and reachable:

```
reqXp(L→L+1) = round( BASE * L^EXP + LINEAR * L )
   BASE   = 75
   EXP    = 1.85
   LINEAR = 40
```

| Level → next | Per-level XP | Cumulative XP to reach |
|--------------|--------------|------------------------|
| 1 → 2   | 115     | 0 |
| 5 → 6   | ~1,750  | ~4.4k |
| 10 → 11 | ~5,700  | ~25k |
| 25 → 26 | ~31k    | ~330k |
| 50 → 51 | ~117k   | ~2.3M |
| 75 → 76 | ~255k   | ~7.7M |
| 99 → 100| ~437k   | ~19M |

A single skill to 100 ≈ 19M XP. With the kill-XP model below (a lvl-50 RARE mob giving ~300–600 XP), level 100 in *one* combat skill is roughly 30k–60k kills — a long-but-finite grind, while levels 1–30 fly by for new-player onboarding. Tune `EXP` per category band if utility skills should cap faster than combat skills.

**Implementation:** the loop in `calculateRequiredXpForNextLevel` stays the same shape (it already iterates per level), just swap the body. Keep it `int`-safe by switching the XP fields to `long` (19M fits `int`, but Echo/global multipliers can overflow — use `long` defensively).

#### 1.2 XP sources & the dynamic-XP formula

`SkillManager.calculateDynamicXp(health, damage, ctx)` already does `(health*0.5 + damage*0.5) * ctx.xpMultiplier()`, and `Tier.xpMultiplier` ranges 0.5 (WEAK) → 15.0 (PRIMORDIAL). This is a **good** base — high-tier mobs already pay out 15x. Formalize the full source table:

| Source | XP formula | Skill credited |
|--------|-----------|----------------|
| Mob kill | `(maxHp*0.5 + dmgDealt*0.5) * tier.xpMultiplier` | the combat skill used (weapon-type routed) |
| Gathering (ore/log/crop/fish) | flat per-block: WEAK node 5 → PRIMORDIAL node 200, × luck minor bonus | Mining/Woodcutting/Farming/Fishing/Herbalism |
| Crafting an item | `outputTier.craftStatMultiplier * 50` (so a PRIMORDIAL craft = 200 XP) | Crafting/Smithing/Alchemy/Enchanting |
| Discovering a new chunk | flat 25 (via `ChunkDiscoveryManager`) | Navigation |
| First successful trap trigger | `trapTier.xpMultiplier * 20` | Trap Mastery |

**Anti-grind rule:** apply a *diminishing-returns multiplier on kills of mobs whose level is far below the player's effective combat band*. Define `effLevel = avg(combat skill levels)`; if `mobLevel < effLevel - 20`, XP × `0.25`. This stops players from farming spawn-adjacent lvl-1 mobs to 100. (Mob level already derives from distance-to-spawn via `MobHealthScaling.levelForDistance`, so this is cheap to compute.)

#### 1.3 The "Mastery" total-level meta-track

`SkillManager.getPlayerLevel` already sums all skill levels. Surface this as **Mastery Level** = `floor(sum(skillLevels) / 10)`. With 34 skills × 100 = 3,400 max → Mastery cap 340. Mastery gates the Inventory Badge tiers (§6) and unlocks account-wide perks, decoupling "I maxed one skill" from "I am an endgame account."

---

### 2. The power curve: player vs. distance/level mob curve

#### 2.1 The opposing curves (the real numbers)

**Mob HP** — `MobHealthScaling`: `(20 + (level-1)*6) * tierMult`, cap 5000.

| Mob | COMMON (×1.0) | RARE (×3.0) | EPIC (×6.0) | LEGENDARY (×10) | PRIMORDIAL (×60, capped) |
|-----|--------------|-------------|-------------|-----------------|--------------------------|
| Lvl 1   | 20   | 60    | 120   | 200    | 1,200 |
| Lvl 25  | 164  | 492   | 984   | 1,640  | 5,000 (cap) |
| Lvl 50  | 314  | 942   | 1,884 | 3,140  | 5,000 (cap) |
| Lvl 100 | 614  | 1,842 | 3,684 | 5,000  | 5,000 (cap) |

**Mob damage multiplier** — `MobDamageScaling`: `tierBase * (1 + 0.55*log1p(level/25))`, cap 10x.

| Mob | COMMON (base 1.0) | RARE (1.35) | EPIC (1.75) | PRIMORDIAL (4.5) |
|-----|-------------------|-------------|-------------|------------------|
| Lvl 1   | 1.02 | 1.38 | 1.79 | 4.59 |
| Lvl 25  | 1.38 | 1.86 | 2.42 | 6.22 |
| Lvl 50  | 1.60 | 2.16 | 2.80 | 7.20 |
| Lvl 100 | 1.83 | 2.47 | 3.20 | 8.24 |

**Player HP** baseline = **100** (`HealthLifecycleEngine.DEFAULT_MAX_HP`). This is the anchor everything balances against.

#### 2.2 The Player Power Budget (the central anti-power-creep contract)

The core rule: **player effective HP (eHP) and player DPS must grow on a curve that stays inside a defined band relative to the mob curve at the matching distance/level.** Define the target as "a well-geared player at effective combat level L should beat a same-level COMMON/UNCOMMON mob in ~3–5 hits and survive ~8–12 hits; a same-level EPIC+ should be a real threat."

**Player max-HP budget (replaces uncapped First-Aid):**

```
playerMaxHp(L) = 100 + ScalingFormula(L, HP_PARAMS)  // additive bonus, NOT multiplicative
HP_PARAMS: base=0, logScale=120, steepness=0.10, inflection=60, lateBonus=600, hardCap=900
```

| Effective level | Player max HP | Same-level EPIC mob hit (base ~5 dmg × mult) | Hits to kill player |
|-----------------|--------------|-----------------------------------------------|---------------------|
| 1   | ~100  | ~9   | ~11 |
| 25  | ~490  | ~12  | ~40 (but defense bar absorbs) |
| 50  | ~700  | ~14  | ~50 |
| 100 | ~1,000 (100 base + ~900 cap) | ~16 | ~60 |

Hard cap: **player max HP never exceeds ~1,000** from skills alone. Gear (§5) can push to ~1,500. This keeps the 10x mob-damage cap meaningful forever — a PRIMORDIAL lvl-100 mob hitting for `base × 8.24` with a high base weapon stays lethal in 5–10 hits even to a maxed player. **Contrast: the current First-Aid gives 150,000 HP — 150× over budget.**

#### 2.3 Player DPS budget vs. mob HP

Player damage flows through `CombatSkillProcessor` (skill multipliers) → `DamageResolution`. Combat skills use `ScalingFormula` presets (`LATE_SURGE` cap 3.0x, `STEADY` cap 1.5x). Cap the **total stacked combat multiplier** at a defined ceiling so DPS scales *with* mob HP, not past it:

| Effective combat level | Stacked weapon dmg mult (cap) | Approx DPS vs lvl-matched mob | Time-to-kill same-level RARE |
|------------------------|-------------------------------|-------------------------------|------------------------------|
| 1   | ~1.1x | low | ~6s |
| 25  | ~2.2x | mid | ~4s |
| 50  | ~3.5x | high | ~4s |
| 100 | **≤6.0x total** (hard ceiling across all multiplicative skill sources) | endgame | ~4–5s |

The **6.0x total combat-multiplier ceiling** is the DPS-side anti-creep rule, mirroring the 1,000-HP defensive ceiling. Both are enforced in `CombatSkillProcessor` as a final `Math.min` clamp, and surfaced in `combat.yml`.

#### 2.4 Five hard anti-power-creep rules (the doctrine)

1. **All level-scaled stats route through `ScalingFormula` with a `hardCap`.** No raw `level * X` expressions anywhere. (Rewrite First-Aid, audit every skill's `applyEffect`.)
2. **HP bonuses are additive flat values, never multiplicative on base HP.** (First-Aid `level*1500` was multiplicative-in-effect; ban it.)
3. **Defensive ceiling = 1,000 HP from skills, ~1,500 with gear.** Offensive ceiling = 6.0x total combat multiplier.
4. **Mob damage cap (10x) and HP cap (5,000) are sacred** — they define the "ceiling of threat." Player power must always sit *below* the band where these caps trivialize content.
5. **Resistances/absorption are percentage-based and capped** (armor already caps at 40%, defense bar at 70% absorb). No new flat damage-immunity sources. Stacking elemental resist + armor + defense bar already approaches ~85% mitigation at max — that *is* the eHP budget; do not add a fourth layer.

#### 2.5 First-Aid rewrite (concrete)

```java
// FirstAidSkill.applyEffect — REPLACE level*1500 with:
double hpBonus = ScalingFormula.compute(level, new ScalingParams(
        0, 120, 0.10, 60, 600, 900));   // 0 → ~900 across levels 1–100
healthManager.setMaxHealthBonus(player, "first_aid", hpBonus); // keyed, idempotent
```
The keyed setter (replacing the additive `increaseMaxHealth`) is critical — the current code *adds* every time `applyEffect` fires, so repeated calls stack infinitely regardless of formula. Make max-HP bonuses a `Map<String,Double>` of named sources summed once.

---

### 3. Currencies & sinks

There is **no currency system in the codebase today** — clean slate. Propose three currencies with distinct loops, plus a hard sink doctrine.

| Currency | Symbol | Earned from | Tier-flavored? | Primary use |
|----------|--------|-------------|----------------|-------------|
| **Marrow** (soft) | bone-themed | mob kills, gathering, selling junk | scales with `tier.xpMultiplier` | repairs, basic crafting, NPC vendor buys, Blink Gate tolls |
| **Echo Shards** (premium/progression) | per-family colored | Echo Family rituals, EPIC+ mob drops, boss kills | yes — family-keyed (SAND/TIDE/ASH/FROST/MIRE) | keyed armor (§5), Nomicons, family upgrades |
| **Entropy** (durability currency) | — | consumed by gear use; refilled via crafting/repair | n/a | not a wallet — a *gear fuel* that creates the main Marrow sink |

**Marrow drop formula:** `floor((mobMaxHp * 0.05) * tier.xpMultiplier * luckFactor)`. A lvl-50 RARE mob (942 HP, ×2.0) → ~94 Marrow × luck.

**The sink doctrine (this is what prevents inflation):**

| Sink | Cost basis | Why it works |
|------|-----------|--------------|
| **Entropy durability** (roadmap gear feature) | Every gear piece loses Entropy on use; refill = Marrow + materials | Continuous, scales with playtime, hits everyone |
| **Repair tax** | `CraftingManager` repair routed through a Marrow cost = `(maxDurability - current) * tierFactor` | Already half-built (`applyRepairCostReduction` is a stub — wire it to Marrow) |
| **Tier-up gambling** | Re-rolling a craft's tier costs escalating Marrow | Endgame Marrow drain |
| **Blink Gate tolls** (roadmap) | Marrow per long-distance teleport | Travel convenience tax |
| **Inventory Badge upgrades** (§6) | Marrow + Echo Shards per tier | Big mid/late sink |
| **Respec** (§7) | Marrow + Echo Shards, escalating | Vanity/optimization sink |

**Rule:** every currency *faucet* (drop) must have a named *sink* of comparable magnitude at the same progression band. Track faucet/sink ratio in tuning; target net-neutral for an "average session" at endgame.

---

### 4. Magic Find / Luck & drop-rate design

#### 4.1 What exists

- `LuckManager.calculateLuckFactor` multiplies skill-luck × temp-luck × permanent-luck × item-bonus × env-factor. **`applyItemBonuses` and `applyEnvironmentalFactors` are empty stubs returning 1.0** — wire these up.
- Skill-luck reads "Magic Find", "Scavenging", "Exploration", "Lockpicking" (note: "Magic Find" and "Exploration" aren't in the `Skills` enum — these lookups silently return level 1; reconcile to real skills: Scavenging + Navigation, or add the missing skills).
- `MagicFindManager` is a separate persisted per-player double (table `player_magic_find`). **Two parallel luck systems exist** — unify: `LuckManager` should read `MagicFindManager.getMagicFind()` as the item/permanent component instead of its own empty stub.

#### 4.2 Loot tier roll table (the core drop design)

Define a per-source **base tier-weight table**, then shift weights up by `luckFactor`. Use the unified Tier enum directly.

| Source | WEAK | COMMON | UNCOMMON | RARE | EPIC | LEG | PERF | MYTH | GODLY | PRIM |
|--------|------|--------|----------|------|------|-----|------|------|-------|------|
| Trash mob (COMMON tier) | 40% | 45% | 12% | 2.5% | 0.5% | — | — | — | — | — |
| Elite (EPIC tier) | — | 20% | 35% | 25% | 15% | 4% | 1% | — | — | — |
| Boss (LEGENDARY+) | — | — | 10% | 25% | 30% | 20% | 10% | 4% | 0.9% | 0.1% |
| Family ritual (Echo) | — | — | — | 15% | 30% | 30% | 15% | 7% | 2.5% | 0.5% |

**Luck application:** `luckFactor` (e.g. 1.5 at decent investment, ~3.0 maxed) shifts the distribution via a *weight power*: `adjustedWeight[tier] = baseWeight[tier] * luckFactor^(tier.ordinal())`. Higher tiers get exponentially boosted by luck, then renormalize. This makes luck *feel* like it pulls toward rarity without ever guaranteeing it. Cap effective luck at `3.0` so even a maxed luck build can't trivialize PRIMORDIAL drops (keep them <1% from anything but bosses/rituals).

**Pity timer (what prevents rage-quitting):** track kills-since-last-EPIC+ per player; after N kills (band-scaled, e.g. 500 at endgame) force-floor the next elite/boss roll to EPIC minimum. Store in the existing skill-effects or a new `player_pity` column.

#### 4.3 Gathering quality rolls

`ResourceYieldManager.calculateYieldMultiplier` is currently `1.0 + level*0.1` — at level 100 that's **11x drops**, which floods the economy. Cap it: route through `ScalingFormula` (`STEADY`, hardCap ~1.5 → max +50% quantity), and move *rarity* to a separate luck-gated roll using the table above (a Mining node can drop a tiered gem). Quantity stays modest; *quality* is the exciting axis.

---

### 5. Crafting / gathering economy & itemization

#### 5.1 Fix the crafting tier system (it contradicts the baseline)

`CraftingManager.determineItemQuality` uses an **old private 8-tier `Quality` enum** (missing UNCOMMON gaps, no LEGENDARY/MYTHIC) with skill thresholds up to **160+** — directly contradicting the unified 10-tier `Tier` enum and the level-100 cap. **Rewrite to use `Tier` and a 1–100 skill scale.**

Proposed tier-chance table by crafting skill level (replaces the `< 10 / < 30 / ... / < 160` ladder):

| Skill lvl band | Best achievable tier | Roll distribution (top 3) |
|----------------|----------------------|---------------------------|
| 1–15   | COMMON   | 70% WEAK / 30% COMMON |
| 16–30  | UNCOMMON | 50% COMMON / 40% UNCOMMON / 10% WEAK |
| 31–50  | RARE     | 45% UNCOMMON / 40% RARE / 15% COMMON |
| 51–70  | EPIC     | 40% RARE / 35% EPIC / 25% UNCOMMON |
| 71–85  | LEGENDARY| 40% EPIC / 30% LEGENDARY / 30% RARE |
| 86–95  | PERFECT  | 35% LEGENDARY / 30% PERFECT / 35% EPIC |
| 96–99  | MYTHIC   | 30% PERFECT / 25% MYTHIC / 45% LEGENDARY |
| 100    | GODLY (PRIMORDIAL rare) | 5% PRIMORDIAL / 30% GODLY / 35% MYTHIC / 30% PERFECT |

Luck (`luckFactor`) applies the same `weight^ordinal` shift. PRIMORDIAL gear is intentionally craft-rare — a luck-maxed level-100 smith averaging maybe 1-in-15 crafts. This is the **player-driven endgame faucet** for top gear and the main Echo Shard / Marrow sink.

#### 5.2 Item stat application uses `Tier.craftStatMultiplier`

The enum already carries `craftStatMultiplier` (WEAK 0.5x → PRIMORDIAL 4.0x). All crafted-item stat rolls multiply base stats by this. Combined with the §2.3 6.0x combat ceiling: a PRIMORDIAL weapon's raw stats are strong, but the *player's* total multiplier is still clamped — gear gives you *reach* (hit the ceiling sooner) rather than *runaway*.

#### 5.3 Gathering → crafting → combat loop (the macro loop)

```
Gather (skill XP + materials, quantity capped +50%)
   → Craft/Smith (consume materials + Marrow, roll Tier via skill+luck)
      → Equip (Tier.craftStatMultiplier feeds combat, capped at ceiling)
         → Kill higher-tier mobs farther from spawn (more Marrow, Echo Shards, rarer drops)
            → afford better gathering tools / Inventory Badge / repairs → loop widens
```
Entropy durability (§3) is the leak that keeps the loop *running* instead of saturating.

---

### 6. Inventory Badge — 20-tier progression (roadmap)

A 20-tier account-wide upgrade track gating carrying capacity + passive bonuses. Gate it on **Mastery Level** (§1.3) + currency, so it pulls *all* skills forward, not just one.

| Badge tier | Mastery req | Cost (Marrow / Echo Shards) | Unlock |
|-----------|-------------|------------------------------|--------|
| 1–4   | 0,5,10,18   | low | +1 hotbar-equiv slot each; +5% Marrow gain |
| 5–8   | 28,40,55,72 | mid | +2 slots each; +luck (perm, fed into LuckManager); auto-pickup radius |
| 9–12  | 90,110,135,160 | high | shulker-in-inventory; +gathering quantity cap; Blink Gate discount |
| 13–16 | 190,220,255,290 | high + GODLY mats | +Entropy efficiency (gear lasts longer); +Echo Shard gain |
| 17–20 | 320,335,345,340-max | PRIMORDIAL mats + Echo Shards | endgame: keep-inventory-on-death toggle in safe zones, vault tab, cosmetic badge |

Each tier flips a persisted flag (new `player_inventory_badge` int column, mirror `player_magic_find` pattern). Bonuses that feed combat/luck/economy are themselves **inside the §2.4 ceilings** — the badge accelerates reaching caps, never exceeds them.

---

### 7. Trading, player economy & Contracts (roadmap)

| Layer | Status | Design |
|-------|--------|--------|
| **NPC vendors** | foundation | Marrow faucet/sink anchor. Buy basics, sell junk at `tier.craftStatMultiplier * basePrice * 0.4`. Sets the *floor* price of everything — prevents player-economy deflation. |
| **Trading skill** | exists (stub bonuses) | `enhanceTradeValue` is a stub. Wire it: each level reduces vendor spread and raises sell value via `ScalingFormula` (cap +30%). |
| **Direct P2P trade** | not started | Simple secure two-party trade window first (no escrow). |
| **Contracts / escrow** | roadmap, deferred | Build *on* P2P + NPC foundation. Player A posts an item + asking Marrow/Echo Shards; B fulfills; plugin holds both sides until both confirm; dispute → admin/timeout auto-refund. Persist in a new `contracts` table (id, poster, item-blob, price, state, expiry). Folia-safe: all DB I/O via `AsyncSaveManager`. |

**Bind-on-pickup for top tiers:** GODLY/PRIMORDIAL gear and Nomicons should be account-bound (untradeable) to protect the player economy — otherwise one lucky craft floods the server and collapses the rarity curve. Mid-tier (RARE–PERFECT) is the *tradeable* sweet spot that gives the economy liquidity.

---

### 8. Prestige & Respec

- **Respec (skills):** redistribute is meaningless (skills level by *use*, not points), but **stat investment from skill perks** can be respec'd if perks become choice-based. For now, offer **per-skill "decay reset"** — pay Marrow + Echo Shards to *bank* a maxed skill (lock it at 100) and gain a temporary global XP boost on another skill. Escalating cost prevents abuse.
- **Prestige (Mastery):** at Mastery cap (340), allow a **Primordial Ascension** — reset chosen skills to 1 in exchange for a permanent account-wide **Echo Resonance** stat (+2% to a chosen axis: luck / gathering / Marrow / Echo Shard gain), stackable but **hard-capped at +20% per axis**. This is the infinite-but-bounded endgame chase that respects §2.4. Ties directly into Echo Families (the resonance axis is family-flavored).

---

### What you're probably missing

1. **The XP curve is the silent project-killer.** `100 * 2^(level-1)` means the entire "100-level skill" design and every tier/multiplier balanced against "a level-100 player" is currently fantasy — nobody can reach those levels. Fix this *first* or all other tuning is built on sand.
2. **First-Aid isn't the only `level * X` landmine.** Audit *every* skill `applyEffect` for raw linear scaling — `CraftingManager` alone has `skillLevel * 0.1`, `* 0.05`, `* 0.02`, `level*10`, `level*1000` regen. Several quietly explode at level 100. The fix is doctrinal (§2.4 rule 1), not one-off.
3. **`applyEffect` re-applies additively with no idempotency.** Bonuses use `increaseMaxHealth`/`increaseStrength` (read-then-add). Every trigger stacks forever. You need *keyed, recomputed* stat sources (`Map<source,value>` summed once), or even a correct formula will runaway.
4. **Two parallel luck systems and two ghost skills.** `LuckManager` queries "Magic Find" and "Exploration" which aren't in the `Skills` enum (silently return level 1), while `MagicFindManager` persists a separate value the LuckManager ignores. Unify before designing drop rates, or your luck numbers are lies.
5. **No item-value anchor = guaranteed economy collapse.** Without NPC vendor floor prices, a player economy with luck-driven drops *will* hyperinflate. Vendors must exist before Contracts, purely as the price anchor.
6. **Gathering yield at +1100% floods materials.** `1.0 + level*0.1` at level 100 is 11x output. Crafting becomes free, the Marrow sink collapses. Cap quantity, move excitement to *quality* rolls.
7. **The economy needs a continuous sink, not just one-time ones.** Inventory Badge and respec are one-time drains. Only **Entropy durability** (the roadmap gear feature) is a *continuous* sink scaling with playtime — it is therefore load-bearing for economic stability, not a side feature. Prioritize it.
8. **Bind-on-pickup is a balance lever, not a restriction.** Without it, the first lucky PRIMORDIAL craft is sold to the whole server and the rarity curve dies overnight. Decide the tradeable band (recommend RARE–PERFECT) deliberately.
9. **Folia threading constraint on every economy write.** All currency/contract/badge persistence must snapshot on the entity thread and I/O via `AsyncSaveManager` (the baseline forbids Bukkit calls on async threads). The Contracts escrow especially must not touch player inventories off-thread.
10. **Crafting still references a defunct 8-tier `Quality` enum with level-160 thresholds.** It pre-dates both the unified `Tier` enum and the level-100 cap — it's a direct contradiction of the baseline hiding in working code.

---

## World, Zones & Content Structure

### 0. Design premise: geography IS the difficulty curve

Cerberus has no zone layer today. `MobScalingListener` computes a mob's level purely from horizontal distance to `world.getSpawnLocation()` (`level = 1 + floor(distance / 150)`, clamped 1–100), then `MobHealthScaling.calculate()` and `MobDamageScaling.calculate()` apply tier + level. The deleted region-editor system (now only in the orphaned `Creb/` nested repo) is **not** coming back — and it should not. Instead we make distance-from-spawn *legible*: carve the radial gradient into ten named **Bands**, paint each Band with an **Echo Family** theme + biome affinity, and overlay a **risk gradient**. Everything is derived (distance + biome + a thin config), so there is no per-block region database to maintain and the design stays Folia-safe.

The world thus teaches progression with zero tutorial text: the farther you walk, the higher the mob level the engine already assigns, the deadlier the family, the better the loot tier, and the riskier the PvP rules. A player reads their progress by how far the spawn beacon is over the horizon.

---

### 1. The Band system (concentric difficulty rings)

Ten Bands map 1:1 onto the unified tier ladder and onto the engine's existing 1–100 level range. Band boundaries are expressed in **mob-level terms** (the value `levelForDistance` already produces) so they auto-track any future change to `blocks-per-level`. At the default `blocks-per-level: 150`, level 100 begins at ~14,850 blocks; the table shows both.

| Band | Name | Tier theme | Mob-level range | Approx radius (blocks @150/lvl) | Dominant Family | Risk class |
|---|---|---|---|---|---|---|
| 0 | **The Hearth** | WEAK | 1–5 | 0 – 750 | none (neutral) | SAFE (no-PvP, no hostile spawns near spawn beacon) |
| 1 | **The Verge** | COMMON | 6–15 | 750 – 2,250 | SAND | LOW |
| 2 | **Dunesea** | UNCOMMON | 16–25 | 2,250 – 3,750 | SAND | LOW |
| 3 | **The Tidewrack** | RARE | 26–38 | 3,750 – 5,700 | TIDE | MEDIUM |
| 4 | **Cinderreach** | EPIC | 39–52 | 5,700 – 7,800 | ASH | MEDIUM |
| 5 | **The Rimefells** | LEGENDARY | 53–65 | 7,800 – 9,750 | FROST | HIGH |
| 6 | **The Sunkmire** | PERFECT | 66–76 | 9,750 – 11,400 | MIRE | HIGH |
| 7 | **Ashen Wastes** | MYTHIC | 77–86 | 11,400 – 12,900 | ASH + FROST blend | HIGH (PvP-on) |
| 8 | **The Maw** | GODLY | 87–96 | 12,900 – 14,400 | MIRE + TIDE blend | LETHAL (full-loot risk option) |
| 9 | **The Primordium** | PRIMORDIAL | 97–100 | 14,400+ | all five (convergence) | LETHAL (full-loot, siege-enabled) |

**How the engine already supports this:** the Band is a pure function of the mob level the spawn scaler already produces. We add a `BandResolver`:

```
Band bandForLevel(int level)  // bucket 1-100 into Bands 0-9 by the table above
Band bandForLocation(Location)  // = bandForLevel(mobHealthScaling.levelForDistance(dist))
```

No new persistence, no per-chunk data. `bandForLocation` reuses the *exact* distance math in `MobScalingListener` (extract that distance calc into `MobHealthScaling.distanceToSpawn(loc)` so the listener and resolver can't drift).

#### 1.1 Band → tier coupling is already half-built

`MobScalingListener` resolves a mob's **tier** from the `MobRegistry` (named/PDC mobs only); generic mobs default to `Tier.COMMON`. Today a level-90 vanilla zombie in The Maw is still COMMON tier → only `tierMultiplier 1.0`. That is the single biggest balance hole. **Fix: the Band sets a *floor tier* for generic spawns.** Add to the spawn pipeline:

```
Tier resolved = registry.resolve(entity).tier();
Tier bandFloor = band.floorTier();        // Hearth→WEAK ... Primordium→PRIMORDIAL
Tier effective = Tier.max(resolved, bandFloor);  // never downgrade a named special
```

This makes a generic zombie in Cinderreach an EPIC-tier mob (HP ×6, damage ×1.75 base) without authoring a `mobs.yml` entry per biome — closing the "custom mobs mostly use vanilla HP" hard-constraint gap. Named specials in `mobs.yml` still override upward.

| Band | floorTier | Generic zombie max-HP @ band's top level | Generic zombie damage mult @ top level |
|---|---|---|---|
| Hearth | WEAK | (20+4·6)·0.5 = 22 | 0.85·(1+.55·log1p(5/25)) = 0.93 |
| Verge | COMMON | (20+14·6)·1.0 = 104 | 1.0·1.24 = 1.24 |
| Dunesea | UNCOMMON | (20+24·6)·1.5 = 246 | 1.15·1.36 = 1.56 |
| Tidewrack | RARE | (20+37·6)·3.0 = 726 | 1.35·1.49 = 2.01 |
| Cinderreach | EPIC | (20+51·6)·6.0 = 1,956 | 1.75·1.58 = 2.77 |
| Rimefells | LEGENDARY | (20+64·6)·10.0 = 4,040 | 2.25·1.66 = 3.74 |
| Sunkmire | PERFECT | (20+75·6)·16.0 → cap **5,000** | 2.5·1.71 = 4.28 |
| Ashen Wastes | MYTHIC | cap **5,000** | 3.0·1.76 = 5.28 |
| Maw | GODLY | cap **5,000** | 3.75·1.81 = 6.79 |
| Primordium | PRIMORDIAL | cap **5,000** | 4.5·1.84 = 8.28 (→ cap 10 near elites) |

Note the **health cap (5000) flattens Bands 6–9 into a wall**: a Sunkmire generic and a Primordium generic have identical HP. That is a real problem — see §9.

---

### 2. Echo Families as the world's biome-thematic spine

The roadmap's **Echo Families** (SAND, TIDE, ASH, FROST, MIRE) become the *cultural and ecological identity* of the Bands, not just a token-grind sidebar. Each family owns a biome cluster, a mob archetype set, an elemental affinity (reusing the existing **Elemental Infusion** types: Fire, Ice, Lightning, Earth, Water, Void), a ritual altar POI, and a keyed-armor line (roadmap **Gear Progression**).

| Family | Element affinity | Biome cluster (vanilla) | Mob archetype flavor | Ritual token | Keyed-armor theme |
|---|---|---|---|---|---|
| **SAND** | Earth | desert, badlands, savanna, windswept hills | husks, dune-stalkers, glass-shard golems | *Glass Tear* | Duneplate (kb-resist, sandstorm immunity) |
| **TIDE** | Water | ocean, beach, river, mangrove, lush caves | drowned, reef-wraiths, tide callers | *Brine Pearl* | Reefmail (water-breathing, wet-step speed) |
| **ASH** | Fire | nether-adjacent surface scars, basalt deltas surface analogs, scorched forest | magma husks, ember archers, ash revenants | *Cinder Mote* | Emberweave (fire resist, ignite-on-hit) |
| **FROST** | Ice | snowy taiga, frozen peaks, ice spikes, grove | frost wights, glacier maulers, rime archers | *Frost Shard* | Rimeguard (slow-immunity, freeze aura) |
| **MIRE** | Void | swamp, dark forest, deep dark, dripstone | bog-things, void-touched, lantern wraiths | *Mire Husk* | Hollowhide (void resist, blink cost cut) |

**Biome-driven family resolution (no region DB):** family is resolved by *biome first, Band second*. The `BandResolver` reads `location.getBlock().getBiome()`, maps it through a `family-biomes` config map, and falls back to the Band's "dominant family" when a biome isn't mapped (e.g., a desert in the Rimefells radius still spawns SAND-flavored mobs but at LEGENDARY floor tier). This gives organic, non-circular zones: a frozen ocean at distance 9,000 is a TIDE/FROST contested pocket inside the Rimefells.

```
Family familyFor(Location loc):
  Family b = familyBiomes.get(loc.biome)         // explicit biome map
  return (b != null) ? b : band.dominantFamily   // else band default
```

This means the **world generation is just vanilla** — we don't generate terrain, we *interpret* it. That keeps the design Folia-safe and removes any custom-worldgen dependency.

#### 2.1 Family mob authoring via `mobs.yml` (already supported)

Each family's named mobs are authored as `mobs.yml` entries using the existing match system. Example new entries (the registry already parses `classification`, `xp-multiplier`, `match-mode`, regex):

```yaml
  sand_dune_stalker:
    classification: RARE
    xp-multiplier: 2.2
    match-mode: ALL
    match:
      entity-type: HUSK
      custom-name-pattern: "^Dune Stalker.*"
  ash_revenant:
    classification: EPIC
    match:
      custom-name-pattern: "^Ash Revenant.*"
  frost_glacier_mauler:     # mini-boss
    classification: LEGENDARY
    match:
      custom-name-exact: "Glacier Mauler"
```

A new `FamilySpawnDirector` (CreatureSpawnEvent, LOW priority — runs *before* MobScalingListener's HIGH) does occasional substitution: with probability `p(band, family)` it converts a vanilla spawn into the family's named archetype (sets custom name + PDC tag via `registry.tagEntity`), and `MobScalingListener` then scales it. This is the spawn-side counterpart to the floor-tier rule.

---

### 3. Risk zones, safe zones & PvP tiers

Reusing the roadmap **PvP Tiers / risk zones / outpost sieges** concept, risk is a property of the Band, refined by POI type. PvP is **off by default in Bands 0–6** and progressively enabled outward. This is enforced in a `PvPRuleListener` keyed on `bandForLocation` — no per-block region flags.

| Risk class | Bands | PvP | Death penalty | Mob aggression | Special rules |
|---|---|---|---|---|---|
| **SAFE** | 0 (Hearth) | disabled | none (keep all) | no hostile spawns within 96 blocks of spawn beacon | Blink hub, vendors, repair, bank |
| **LOW** | 1–2 | disabled | drop 0% (vanilla keep-inventory style) | normal | family ritual altars appear |
| **MEDIUM** | 3–4 | duel-opt-in only | drop 10% of carried non-equipped | elevated | contested POIs flag PvP locally |
| **HIGH** | 5–7 | always on | drop 25% carried + entropy durability tick | high; family elites roam | outposts capturable |
| **LETHAL** | 8–9 | always on, **full-loot option** | full-loot in flagged sub-zones | maximal; convergence events | siege windows, Nomicon drops |

**Directional scanning** (roadmap): a craftable **Echo Compass** item, when held, shows in the HUD (reuse `PlayerHUDManager`) the bearing + Band name of the nearest higher-tier Band edge and the nearest Blink Gate — "Rimefells ▲ NE • Gate 1.2k SW". This is the diegetic map; no minimap mod, Folia-safe HUD text only.

**Outpost sieges** (Bands 7–9): an **Outpost** is a fixed POI (see §5) a guild/player can "hold" by standing a banner. While held, it suppresses the Band's full-loot rule in a radius (a moving safe bubble) and grants a passive token trickle. A **siege window** opens on a server-config schedule; attackers can contest. Capture state is the *only* new world-state we persist (a tiny `outposts` table: `outpost_id, world, holder_uuid, captured_at`) — deliberately minimal, unlike the deleted region DB.

---

### 4. Blink Gates: the fast-travel network

The roadmap **Blink Gates** teleport network is built directly on the existing **Chunk Discovery System** (`ChunkDiscoveryManager` / `visited_chunks` table) — a gate is "discovered" the first time a player enters its chunk, exactly like Navigation XP discovery.

**Topology — a hub-and-spoke + ring:**

```
              [Hearth Hub]  (Band 0, central)
             /  |   |   |   \
       SAND   TIDE  ASH FROST MIRE   ← five Family Spoke gates (Band 1-2 edge)
         |     |    |     |     |
   ...outer family gates at each Band's family altar (Bands 3-7)...
                  \  |  /
            [The Maw Gate] (Band 8)
                   |
           [Primordium Gate] (Band 9, one-way-in by default)
```

| Gate type | Where | Unlock condition | Cost to use |
|---|---|---|---|
| **Hearth Hub** | spawn (Band 0) | default unlocked | free |
| **Family Spoke** | each family's first ritual altar (Band 1–2) | discover chunk + light the altar once | small mana drain (uses Mana System) |
| **Altar Gates** | each family altar Bands 3–7 | discover chunk + offer N family tokens | mana + 1 token, scaling by Band |
| **Maw Gate** | Band 8 | clear the Band-7 family boss | mana + Inventory-Badge tier ≥ 12 gate-check |
| **Primordium Gate** | Band 9 | hold a Nomicon key | one-way in unless return-anchor crafted |

**Mechanics that fit the existing engine:**
- A gate is a multiblock **pattern** — reuse the **Traps System** pattern/block-role framework (`Trigger`/`Effect`/`Container` block roles) to define a `GateStructure` instead of hand-rolling structure detection.
- Teleport is a Folia region-aware `entity.teleportAsync(target)` — never sync teleport across regions.
- **Travel cost scales with risk**: blinking *outward* costs more (mana + token), blinking *inward toward Hearth* is cheap (encourages retreat-to-bank loops, supports MEDIUM+ death-penalty tension). MIRE family's keyed-armor cuts blink cost (thematic Void affinity).
- **No blinking while combat-tagged** (reuse `health-regen.combat-timeout-ms` combat state) — prevents teleport-escape in HIGH/LETHAL Bands.

**Discovery integration:** add a `gates` set to `ChunkDiscoveryManager` semantics — when `checkAndRecord` fires for a chunk that contains a registered gate, also flag the gate discovered (persist in a `discovered_gates` table mirroring `visited_chunks`). The GUI rewrite's `GuiBuilder` renders the **Blink Map GUI**: an inventory grid where each discovered gate is an item; clicking it triggers the teleport. Undiscovered gates show as obsidian "???".

---

### 5. POIs, dungeons & landmarks

Content density rises with Band. POIs are **vanilla-structure-anchored + plugin-flavored** where possible (no custom worldgen): we listen for players entering known structure types and overlay family content, plus hand-place a handful of fixed landmark coordinates per server.

| POI type | Bands | Frequency | Content | Reward focus |
|---|---|---|---|---|
| **Ritual Altar** | 1–9 (one per family per Band) | fixed | light/offer tokens → Family Spoke gate + ritual buffs | gate unlock, Echo Family progression |
| **Cache** (small) | 1–4 | common | 1–3 mob pack + locked chest (Lockpicking skill) | tier-appropriate gear, crafting mats |
| **Warren** (mini-dungeon) | 3–7 | uncommon | linear 3–5 room delve, family-themed, 1 mini-boss | keyed-armor pieces, Nomicon fragments |
| **Outpost** | 7–9 | fixed (siege) | capturable banner point | token trickle, safe bubble |
| **Deepwork** (full dungeon) | 5–9 | rare | multi-wing, puzzle (Trap rooms), gated boss | Nomicon (unique item), Blink anchor |
| **Convergence Rift** | 9 only | event | all-family wave event, server-wide ping | PRIMORDIAL mats, Spaceman Armor pieces |

**Dungeons reuse the Traps System** for hazard rooms (Spike/Blast/Flame/Freeze trap types already exist with Movement/Pressure/Contact triggers and Armed/Triggered/Cooldown states). A Deepwork is authored as a set of trap patterns + a boss spawn anchor — no new hazard engine needed. **Lockpicking, Stealth, Survival, Navigation** skills gate or ease POI traversal (locked caches, trap-disarm, no-spawn campfires, map-reveal).

---

### 6. Gating gear & bosses by zone

Zones gate progression through three coupled levers, all hooking existing systems:

1. **Mob tier floor** (§1.1) → loot tier. The **Resource Yield / Luck systems** already scale drop quality; a mob's effective Tier (now Band-floored) feeds the loot roll, so Cinderreach mobs *naturally* drop EPIC-band mats. No separate loot-table-per-zone needed beyond a tier→drop map.
2. **Family bosses** as Band capstones. Each HIGH+ Band has a named family boss that **gates the next Band's Blink Gate** and drops the family's keyed-armor set token. Bosses are `mobs.yml` entries at `LEGENDARY`+ classification.
3. **Inventory Badge & Nomicon checks** as soft walls. The roadmap **Inventory Badge** (20-tier) and **Nomicon** keys become *gate-use prerequisites* (see §4 table) rather than invisible walls — you can walk into the Maw underleveled and die, but you can't *blink* there until you've earned the badge tier, so the geography stays honest.

| Band boss (capstone) | Family | Classification | Gates | Signature drop |
|---|---|---|---|---|
| **Sandfather Ozun** | SAND | LEGENDARY | — (early, optional) | Duneplate chest |
| **The Drowned Choir** (TIDE) | TIDE | LEGENDARY | Tidewrack altar gate | Reefmail set token |
| **Pyre-Mother Cael** (ASH) | ASH | MYTHIC | Cinderreach → Rimefells | Emberweave + Cinder Nomicon frag |
| **Rimewyrm Vael** (FROST) | FROST | MYTHIC | Rimefells → Sunkmire | Rimeguard set token |
| **The Hollow King** (MIRE) | MIRE | GODLY | Sunkmire → Maw Gate unlock | Hollowhide + Blink anchor recipe |
| **The Maw Itself** | blend | GODLY | Maw → Primordium Gate | Nomicon key |
| **The First Echo** | all | PRIMORDIAL | endgame | Spaceman Armor schematic |

---

### 7. Zone progression map (the player's journey)

```
[HEARTH]──learn combat, no risk, light first family altar
   │  (walk out 750 blocks → engine starts leveling mobs)
   ▼
[VERGE/DUNESEA · SAND]  Lv1-25  ── caches, first keyed-armor, unlock SAND spoke gate
   │  (cross water → biome flips family)
   ▼
[TIDEWRACK · TIDE]  Lv26-38 ── PvP duel-opt-in begins, first Warren mini-dungeon
   │  (boss: Drowned Choir → opens Tidewrack altar gate)
   ▼
[CINDERREACH · ASH]  Lv39-52 ── EPIC floor, Pyre-Mother gates the cold north
   │
   ▼
[RIMEFELLS · FROST]  Lv53-65 ── HIGH risk: full PvP on, outposts appear, Deepwork dungeons
   │  (Rimewyrm Vael → Sunkmire)
   ▼
[SUNKMIRE · MIRE]  Lv66-76 ── PERFECT mobs, Hollow King, Blink anchor crafting
   │  (badge tier ≥12 to blink to Maw)
   ▼
[ASHEN WASTES]  Lv77-86 ── MYTHIC, contested family blend, siege windows
   │
   ▼
[THE MAW]  Lv87-96 ── GODLY, full-loot zones, Nomicon hunting
   │  (Nomicon key → Primordium)
   ▼
[PRIMORDIUM]  Lv97-100 ── PRIMORDIAL convergence, all families, Spaceman Armor, one-way risk
```

The player never reads a level-requirement tooltip; they feel the wall (mobs hit harder, drop better, PvP turns on) and the Blink Gate prerequisites tell them what they still need.

---

### 8. Reservoir Inversion & world tie-in

The roadmap **Reservoir Inversion** (swap HP/MP pools) gets a *world hook*: **Mire/Void POIs in the Sunkmire and Maw can force-trigger a brief Inversion** as an environmental hazard (a "void bleed" room), turning the Mana System into your effective health bar mid-fight. This makes the FROST/MIRE deep zones mechanically distinct rather than just numerically harder, and gives the disabled **Defensive Magic** shields a clear future home (shield-on-inversion). Flag this as a stretch tie-in pending the input-system reimplementation hard constraint.

---

### 9. What you're probably missing

1. **The 5000 HP cap collapses your top four Bands.** Sunkmire→Primordium generic mobs all sit at the 5000 cap (§1.1 table), so Bands 6–9 have *identical generic HP* and differ only in damage multiplier. Either (a) raise `health-cap` to ~25,000 and let player gear scale to match, (b) make the cap **per-Band** (`health-cap` lookup by floor tier), or (c) accept it and differentiate top Bands purely by mechanics (adds, auras, Inversion) rather than HP. Pick one before authoring endgame.
2. **Distance is from world spawn only — multi-world and the Nether/End are undefined.** `levelForDistance` uses `world.getSpawnLocation()`. What is a mob's Band in `world_nether` (compressed 8:1 coords) or `world_the_end`? Decide whether the End is "Band 11" hardcoded, whether the Nether uses its own spawn anchor, and whether Blink Gates cross worlds.
3. **Radial bands punish builders and create a "ring rush" meta.** Because difficulty is a pure radius, the optimal play is to tunnel straight out along one bearing. Consider **biome gating** (must pass a FROST choke to reach MIRE) or **angular variation** (a small `±` jitter on the level-from-distance so the frontier isn't a perfect circle) to break the straight-line meta.
4. **No world border interaction.** With level 100 at ~14,850 blocks, where is the actual world border? If it's at 30,000, half the playable world is undifferentiated max-level. Set the border near the Primordium edge or define what "beyond level 100" means.
5. **Spawn-camping the difficulty gradient.** A high-level player can stand just inside a LETHAL Band and farm low-level players crossing the HIGH/LETHAL boundary. The PvP rule needs an **edge grace radius** and possibly level-bracketed PvP (can't full-loot someone 30+ Bands below you).
6. **Biome-family mismatch on real maps.** Many seeds have huge oceans or no badlands for thousands of blocks, so a Band's "dominant family" fallback may dominate while the intended biome family never appears. You need a **family-density floor** (FamilySpawnDirector guarantees a minimum mix per Band regardless of biome) or the SAND/TIDE balance breaks per-seed.
7. **Gate discovery exploit.** Since gates unlock on chunk discovery, a player ferried by a high-level friend can unlock the Primordium gate at level 5. The **gate-use prerequisites** (badge tier, boss kills) must gate *use*, not just *discovery* — confirm the §4 table is enforced on teleport, not on reveal.
8. **Performance: don't resolve Band/Family per entity per tick.** `bandForLocation` and `familyFor` (biome lookup) must run only at spawn (CreatureSpawnEvent) and on player chunk-cross, never in the damage loop. Cache the Band on the mob's PDC alongside the existing level tag.
9. **Outpost/siege is real persistent world-state — scope-creep risk.** It's the one feature here that needs new DB + scheduling + conflict resolution, and it brushes the deleted-region-system territory. Strongly consider shipping Bands + Families + Blink Gates first and deferring sieges (like the roadmap already defers Contracts).

---

## Signature & Meta Systems

This is the identity layer of Cerberus. Combat, skills, gear and mobs are the *nouns*; this domain defines the **verbs** — the handful of moments a player will describe to a friend ("I flipped my health and mana to survive a Primordial Tide boss, then drained its mana into my own pool"). Every system here is built directly on existing classes and the unified `Tier` ladder; nothing contradicts the Folia / no-ProtocolLib / level-cap-100 / distance-drives-level constraints.

The signature verbs, named:
- **INVERT** — Reservoir Inversion: swap HP↔MP pools for a dramatic window.
- **ATTUNE / RITUAL** — Echo Families: bind to one of 5 families, perform rituals, spend tokens.
- **WARD** — Defensive Magic: raise Shield / Ward / Barrier / ManaDrain via a Folia input system.
- **SNARE** — Traps: pre-place lethal terrain control, now a hunting pillar.
- **REACT** — Elemental infusion + reactions: chain the 8 `ElementType`s into named combos.
- **HUNT THE DEEP** — Sea Monsters: roaming tier-scaled apex water encounters.

---

### 1. Reservoir Inversion (the flagship mechanic)

**Concept.** For a short window the player's **current HP and current MP swap roles**. While inverted: incoming damage drains *mana* (via `PlayerManaManager`) instead of virtual health (via `PlayerVirtualHealthManager`), and spending mana costs *health*. This is the "magical counterplay" panic button — a mage who is out of HP but full of mana becomes briefly unkillable-by-damage but pays in their casting resource, and vice-versa.

**Why it's signature.** No other system swaps the two pools. It makes the dual Virtual Health / Mana architecture (which already exists and is otherwise just "two bars") into a *strategic resource* rather than two parallel meters.

**State machine.** Add a `ReservoirState` per UUID in a new `ReservoirInversionManager` (sits beside `PlayerManaManager` / `PlayerVirtualHealthManager`, never touches Bukkit on async threads).

| State | Meaning | Damage routes to | Mana spend costs |
|---|---|---|---|
| `STABLE` (default) | Normal | Virtual Health | Mana |
| `INVERTING` | 10-tick handshake animation, locks input | (still HP) | (locked) |
| `INVERTED` | Active window | **Mana pool** | **Virtual Health** |
| `BACKLASH` | Window ended | HP, +vulnerability | Mana, +cost |

**Numbers (config: `signature.yml`):**
- **Duration:** base `4.0s`, +`0.04s` per `Intelligence` level (cap 100 → 8.0s).
- **Cooldown:** `90s`, reduced `0.3s`/level of `ArcaneKnowledge` (cap → 60s).
- **Activation cost:** `15%` of *max* mana up front (prevents free-cast spam) — refunded `0` on backlash.
- **Conversion ratio:** pools do **not** swap raw values 1:1 (a 5000 HP pool flooding into a 1000 mana pool is broken). Instead swap *fill percentages*. On INVERT: `manaFill = hpFill`, `hpFill = oldManaFill`, each applied to its own max. So a player at 80% HP / 20% MP becomes 20% HP / 80% MP. Damage now eats the (large) mana-as-shield; HP is small but only spending mana (now: HP) hurts it.
- **Backlash:** for `3s` after the window, the player takes `+25%` damage and `+25%` mana costs (the pools "resettle"). Telegraphs risk; stops players living permanently in inversion.

**Pipeline hook.** In `DamageResolution` / the damage pipeline, before Layer-3 defense bar absorption, check `ReservoirInversionManager.isInverted(uuid)`. If inverted, the *defense bar still applies first* (3-layer defense is sacred), then the residual is subtracted from **mana**, and lethality is computed against mana hitting 0 — running mana dry while inverted is a death (and a dramatic one).

**Skill / tier interplay.**
- Gated behind `Intelligence` level ≥ 20 to unlock at all (it's an arcane verb).
- `DefensiveMagicSkill` (re-enabled, below) reduces backlash penalty by `0.25%`/level.
- Tier of equipped **Nomicon** (roadmap Gear Progression) can grant "Stable Inversion" — removes backlash entirely at `PRIMORDIAL`.

**What it should feel like.** Screen vignette flip, a deep inhale SFX, HUD bars literally trade colors. The `PlayerHUDManager` already renders HP / mana / defense — add an `inverted` flag that swaps bar glyphs and tints. `DamageDisplayListener` shows mana-damage in blue floaty numbers during the window.

---

### 2. Echo Families (the meta-progression spine)

**Concept.** The roadmap's 5 families — **SAND, TIDE, ASH, FROST, MIRE** — become the long-term identity choice. Each player **attunes** to one family (re-attunable at a cost), unlocking family rituals, a family resource (**Echo Tokens**), keyed gear access (roadmap Gear Progression), and triggering family-specific mob spawns (roadmap mob progression). This is the system that gives a level-100 player a reason to keep playing.

**Mapping families to the existing 8 `ElementType`s** (do NOT invent a parallel element system — reuse `ElementalTag.ElementType`):

| Family | Theme | Primary `ElementType` | Secondary | Signature damage type | Biome anchor |
|---|---|---|---|---|---|
| **SAND** | Erosion, entropy, time | EARTH | WIND | `GEOMANCY` | Desert / Badlands |
| **TIDE** | Pressure, drowning, depth | ICE | (water) | `CRYOMANCY` + drown | Ocean / Beach |
| **ASH** | Cremation, fury, ruin | FIRE | LIGHTNING | `PYROMANCY` | Nether / scorched |
| **FROST** | Stasis, silence, preservation | ICE | ARCANE | `CRYOMANCY` | Snowy / mountain |
| **MIRE** | Decay, binding, corruption | DARK | BINDING | `DARK_ARTS` + `MYSTIC_BINDING` | Swamp / dark forest |

**Echo Tokens.** A new tier-keyed currency, one variant per family (`Sand Echo`, `Tide Echo`, …), dropped by family mobs and ritual completion. Token tier follows the unified ladder. Drop rate scales with `LuckManager` luck factor and `MagicFindManager`.

| Source | Token tier | Base qty |
|---|---|---|
| Family mob kill (mob tier = T) | T (one step below for trash) | 1–3 × `Tier.xpMultiplier`-adjusted |
| Family ritual completion | family-defined | 5–25 |
| Sea Monster (family-aligned) | mob tier | 10–40 |

Persist in a new DB table `player_echo` (`uuid`, `family`, `attune_level`, `sand_tokens`, `tide_tokens`, …, `ritual_flags`) — auto-created like the existing 5 tables, saved via `AsyncSaveManager` snapshot pattern.

**Rituals.** A ritual is a player-built multiblock (reuse the **Traps `TrapPattern` / block-role engine** — patterns with TRIGGER/EFFECT/CONTAINER roles are exactly the right abstraction) that, when activated with the right token offering, fires a family effect. Per-family ritual examples:

| Family | Ritual name | Pattern centerpiece | Token cost | Effect |
|---|---|---|---|---|
| SAND | Hourglass Rite | Sandstone ring + soul sand core | 10 Sand (UNCOMMON+) | 10-min buff: gear **entropy durability** drains 50% slower |
| TIDE | Drowning Call | Prismarine + conduit core | 15 Tide (RARE+) | Spawns a **Sea Monster** of your attune tier as a boss summon (loot guaranteed) |
| ASH | Pyre of Renewal | Magma + netherrack ring | 12 Ash (RARE+) | Converts lower-tier gear into Ash-keyed gear one tier up (gambling) |
| FROST | Stasis Lock | Packed ice + amethyst core | 20 Frost (EPIC+) | Freezes a chunk's mob level for 30 min (farm a fixed-difficulty zone) |
| MIRE | Binding Pact | Mud + wither rose core | 8 Mire (UNCOMMON+) | Summons a controllable bound mob ally for 5 min (uses `SummoningSkill`) |

**Attunement progression.** `attune_level` 0–10 maps 1:1 onto the **unified tier ladder** (level 0=WEAK access, 10=PRIMORDIAL access). Raising it costs escalating tokens and completing a tier-gated ritual. Higher attunement = better family-keyed gear tier ceiling, stronger ritual outputs, and bonus to that family's element via `ElementalMasterySkill` synergy.

**Family mob spawning.** Extend `MobRegistry` / `mobs.yml` with a `family:` field. Family mobs spawn preferentially in the biome anchor AND scale with the existing **distance-from-spawn level (150 blocks/level, cap 100)** plus tier multiplier. A SAND family member roaming the desert at distance 60 (level 40) meets level-40 Sand mobs that drop Sand Echoes; their own family mobs are slightly weaker to them (-10% incoming) and rival-family mobs hit harder (+10%) — soft factional PvE.

---

### 3. Re-enabling Defensive Magic — Folia-friendly input (no ProtocolLib)

`DefensiveMagicSkill` is intentionally disabled; `Shield`, `Ward`, `Barrier`, `ManaDrain` classes exist and are Folia-clean (`player.getScheduler()`, no async Bukkit calls). The blocker is **input** — the old system used ProtocolLib key-combo packets (S+E, W+F, etc.). We replace that with a 100% server-side input grammar that needs no client mod and no ProtocolLib.

**Input system: "Catalyst + Sneak Cycle".** A new `DefensiveMagicInputListener` (Bukkit events only):
1. Player holds a **Catalyst** item in the **offhand** (a craftable focus, e.g. `Warding Focus`, tier-keyed). Detected via PDC tag like the existing `elemental_infusion` tag pattern.
2. **Right-click offhand catalyst** (`PlayerInteractEvent`, OFF_HAND) = "open ward menu" → cycles the *selected* structure (Shield→Ward→Barrier→ManaDrain) shown in action bar. No GUI needed; reuses the action-bar HUD.
3. **Sneak + right-click** = **cast the selected structure**. `toggleSneak` + interact disambiguates from normal block interaction.
4. **Double-sneak (two `PlayerToggleSneakEvent` within 400ms)** = emergency drop all active wards.

This maps the four old combos onto a single discoverable, cross-client verb. No keybind client mod, no ProtocolLib, fully Folia-safe (all on the player's region scheduler).

**Wiring the four structures into the real 3-layer defense pipeline.** Defensive Magic becomes a **pre-Layer-1** absorber (it sits *above* elemental resist → armor → defense bar). Order on hit:

`raw → [Defensive Magic structures] → elemental resist → armor reduction → defense bar → virtual health`

| Structure | Verb | Folia behavior | Mana | Scaling (uses real `DefensiveMagicSkill` level) |
|---|---|---|---|---|
| **Shield** | Personal bubble, flat HP-like buffer | `Shield.absorbDamage()` already exists; subtract from `strength` before Layer 1 | 50 + tierCost | `strength = 100 × (1 + 0.05·level)` (existing formula; cap at level 100 → 600) |
| **Ward** | Timed % damage reduction aura | Apply a multiplier in pipeline for `duration` | 40 | `30s × (1 + 0.05·level)`; reduction `10%→30%` by level |
| **Barrier** | Placed wall / zone denial (multiblock-lite) | Spawns a barrier zone on a block face; blocks projectiles & reduces melee through it | 75 | `strength = 150 × (1 + 0.07·level)` (existing) |
| **ManaDrain** | Offensive: siphon target mob/player mana → self | `ManaDrain` already targets `getTargetEntity(10)` | scales w/ effectiveness | `effectiveness = 0.1 + 0.03·level` (existing) |

**Re-enable steps (concrete):** (a) delete the "INTENTIONALLY DISABLED" guard in `DefensiveMagicSkill`, (b) replace `initializeKeyTracking()` body with `DefensiveMagicInputListener` registration, (c) add a `DefensiveMagicManager` holding `Map<UUID, ActiveStructure>` so the damage pipeline can query active wards in O(1), (d) hook the pipeline pre-Layer-1 as above. The `applyShield` / `hasActiveShield` / `absorbShieldDamage` plumbing in `PlayerManaManager` (lines 137–161) is already the integration seam — reuse it.

**Synergy:** `PlayerManaManager.applyCrossSkillBenefits` already grants Heavy Armor Training → Defensive Magic mana discounts. That dead code becomes live the moment the skill is re-enabled.

---

### 4. Traps as a gameplay pillar

The trap framework is rich (`TrapType`, `TriggerType`, `TrapState`, `TrapPattern` with block roles, `TrapTool` variants) but `TrapType` only has 2 entries (`BASIC_SNARE`, `EXPLOSIVE_TRAP`). Elevate it into the **terrain-control / hunting** pillar — the "stealth-archer-engineer" playstyle Cerberus currently lacks.

**Expanded `TrapType` roster** (each carries a `CustomDamageType` — several already exist: `PHYSICAL_TRAP`, `MAGICAL_TRAP`, `ELEMENTAL_TRAP`, `BLEED_DOT`, `FREEZE_DOT`):

| TrapType | Damage type | Base dmg | Effect | TriggerType | Tier-gated craft |
|---|---|---|---|---|---|
| BASIC_SNARE | PHYSICAL_TRAP | 5 | Root 1.5s | PRESSURE | WEAK |
| EXPLOSIVE_TRAP | PHYSICAL_TRAP | 15 | AoE knockback | CONTACT | COMMON |
| SPIKE_PIT | BLEED_DOT | 10 + bleed | DoT 8 over 4s | MOVEMENT | UNCOMMON |
| FLAME_GLYPH | ELEMENTAL_TRAP (FIRE) | 12 | Ignite, ASH synergy | PRESSURE | RARE |
| FROST_GLYPH | FREEZE_DOT | 8 | Slow 70%, FROST synergy | PRESSURE | RARE |
| ARC_COIL | ELEMENTAL_TRAP (LIGHTNING) | 18 | Chain to 3 nearby | MOVEMENT | EPIC |
| VOID_SNARE | MAGICAL_TRAP | 20 + mana burn | Drains target mana (MIRE) | CONTACT | LEGENDARY |
| GRAVE_MAW | DARK_ARTS | 35 | Pulls + binds, boss-tier | PRESSURE | MYTHIC |

**Trap power scales with `TrapMasterySkill` and tier.** Damage = `baseDamage × Tier.craftStatMultiplier(trapTier) × (1 + 0.02·trapMasteryLevel)`. A PRIMORDIAL Grave Maw at TrapMastery 100 deals `35 × 4.0 × 3.0 = 420` — meaningful against scaled mobs but bounded.

**Pillar mechanics to add:**
- **Trap budget:** a player may have `5 + floor(level/20)` active traps (anti-spam, server perf). Tracked in `TrapManager`.
- **Concealment:** `StealthSkill` raises the level a victim needs to spot the trap; high Stealth = invisible to lower-level mobs/players.
- **Rearm:** traps enter `COOLDOWN` (existing state) then re-arm; cooldown scales down with TrapMastery.
- **PvP zones (roadmap):** in PvP tiers, traps become a legal siege/defense tool around outposts; in safe zones they're disabled.
- **Family flavor:** ASH players get +25% trap fire damage, MIRE +25% trap mana-burn, SAND traps cost less entropy durability.

---

### 5. Sea Monsters (roaming apex encounters)

`SeaMonster` is a hardcoded 100-HP Guardian stub and `SeaMonsterManager` exists. Promote it to a **tier-scaled roaming apex** that hooks the real scaling systems — this is the "main gameplay balance lever" the hard constraints flag (mobs currently use vanilla HP).

**Design.** Sea Monsters are not bound to distance-level alone; they're **anchored to deep ocean + ritual summons (TIDE family)** and spawn at a tier rolled from depth + distance-from-spawn level. Route them through `MobRegistry` (add `sea_monster` entries to `mobs.yml`) so `MobHealthScaling` and `MobDamageScaling` apply automatically.

| Named Sea Monster | Tier band | Base HP (pre-scale) | Signature | Family token drop |
|---|---|---|---|---|
| Brine Lurker | COMMON–RARE | 120 | Pull + drown DoT | Tide (low) |
| Tideclaw Behemoth | EPIC–LEGENDARY | 800 | Whirlpool AoE, sinks boats | Tide (mid) |
| The Drowned Choir | MYTHIC | 2500 | Summons add-guardians, silences spells (`MANA_VOID`) | Tide (high) |
| Leviathan, Pressure of the Deep | GODLY–PRIMORDIAL | 5000 (HP cap) | Multi-phase, inverts player reservoir as an attack | Tide (max) + Nomicon chance |

**Hooks:** HP via `MobHealthScaling` (`(base + level·hpPerLevel)·tierMult`, capped 5000); melee via `MobDamageScaling` (`tierBase·(1+levelScale·log1p(level/pivot))`, capped 10×). Leviathan's "forces inversion on the player" attack is the cross-link to System 1 — turning your panic button against you is the apex-fight identity beat.

---

### 6. Elemental Infusion & Reaction Depth

`ElementalInfusionManager` works but `applyInfusionEffect` is mostly empty stubs (only FIRE setFireTicks, LIGHTNING strike are real; ICE/DARK/ARCANE/EARTH/WIND/BINDING are TODO). The 8 `ElementType`s already carry `damageMultiplier`, `resistance`, `specialEffect`, and a `getSkillName()` (Pyromancy, Cryomancy, …). Turn this into a **reaction grammar** — the second signature verb.

**Fill the per-element on-hit effects** (all Folia-safe, scale with `ElementalMasterySkill` `1 + 0.07·level`):

| ElementType | On-hit effect | Reaction key (status it applies) |
|---|---|---|
| FIRE | Ignite (`setFireTicks`), `BURN_DOT` | `BURNING` |
| ICE | Slow + brief root, `FREEZE_DOT` | `CHILLED` |
| LIGHTNING | Chain to 1 nearby (+jump per mastery), strike | `SHOCKED` |
| ARCANE | Mana burn on target, ignores 20% resist | `UNSTABLE` |
| DARK | Lifesteal `specialEffect%` of dmg → caster HP | `CURSED` |
| EARTH | Armor shred (lowers Layer-2 reduction 10%) | `SUNDERED` |
| WIND | Knockback + brief levitate | `AIRBORNE` |
| BINDING | Slow + disables target dash/escape | `BOUND` |

**Reactions (the combo layer).** When a target already carrying status X is hit by element Y, fire a named reaction (bonus burst + visual). Track statuses in a lightweight `ElementalStatusManager` (UUID→status+expiry, like mana drains).

| Status + Incoming | Reaction | Effect |
|---|---|---|
| CHILLED + FIRE | **Thermal Shock** | Burst = 1.5× weapon dmg, clears both |
| BURNING + WIND | **Firestorm** | Spreads BURNING to mobs in 3-block radius |
| SHOCKED + ICE | **Superconduct** | Big SUNDERED (armor shred 30%) |
| CURSED + ARCANE | **Void Collapse** | `MANA_EXPLOSION`, heavy mana burn AoE |
| BOUND + EARTH | **Entomb** | Stun + 2× trap-style damage |
| BURNING + ICE | **Steam Veil** | Blinds, light damage, MIRE/TIDE flavor |

**Family + reaction synergy:** an ASH player's FIRE reactions deal +20%; FROST player's CHILLED lasts longer; MIRE's CURSED tick harder. Ties infusion → families → skills into one loop. `PlayerManaManager.checkForElementalSynergy` already tracks recent fire/ice/lightning casts — that dormant code becomes the spellcasting analog of weapon reactions.

---

### What you're probably missing

1. **Reservoir Inversion + Defense Bar interaction is a balance landmine.** While inverted, the defense bar absorbs first and THEN mana eats the rest — but the defense bar regenerates out of combat and mana regenerates at 50/s. A player could chain inversions to make the defense bar a near-infinite shield. **You must lock defense-bar regen and cap mana regen during INVERTED + BACKLASH**, or inversion becomes the only viable defensive strategy.
2. **Inversion as a one-shot avoidance exploit.** Players will pop INVERT the instant before a Leviathan's big hit to dump it into the huge mana pool. Counter: the **15% activation cost + 10-tick INVERTING handshake** means you must pre-commit ~0.5s early — telegraph it so skilled bosses (Leviathan) can bait it.
3. **ManaDrain vs. players is griefing fuel.** Draining a player's mana to zero in PvP (then they can't cast/invert) is oppressive. Decide: drain-cap per hit, or mana-drain only works on mobs in non-PvP zones.
4. **Echo Family re-attunement churn.** If switching families is cheap, no one commits; if it's punishingly expensive, new players who picked wrong rage-quit. A token-sink with a **cooldown (e.g. 7 real days) + partial token refund** is the lever to tune.
5. **Trap performance under Folia.** Each active trap is a region task scanning for entities. With `5 + level/20` traps per player and many players, this is a tick-budget risk. Use **event-driven triggers (block step / interact) not polling scans** wherever possible, and a hard global active-trap ceiling.
6. **The 8 ElementTypes do not map cleanly to 5 families** — TIDE and FROST both want ICE. Either (a) split ICE into a TIDE "pressure/drown" variant and a FROST "stasis" variant under the same `ElementType` with different `specialEffect`, or (b) give TIDE a water/`CRYOMANCY` blend. Decide before wiring family→element, because gear keying and reactions depend on it.
7. **Status stacking / cleanse rules.** Reactions consume statuses — but what if a target has BURNING and CHILLED simultaneously and you hit with LIGHTNING? Define a **priority order** for reaction resolution, or reactions become non-deterministic.
8. **No "off switch" for inverted death.** Dying *because your mana ran out while inverted* will feel unfair to players who don't understand the system. Needs a loud HUD warning at low inverted-mana and a tutorial/lore intro before the verb is ever usable (gate behind Intelligence 20 helps).
9. **Sea Monster despawn / leashing on Folia.** A roaming Leviathan crossing region boundaries is exactly where Folia region-threading bites. Anchor it to its spawn region or use a Folia-safe teleport leash; never let it path unbounded.
10. **Ritual griefing & land use.** Multiblock rituals built in shared/PvP space can be sabotaged or block-griefed. Decide ownership/protection of ritual blocks and whether rivals can interrupt an in-progress ritual (which could be great PvP content if intentional).

---

## Endgame, PvP & Replayability

### 0. Design problem statement

The current world has a hard ceiling. The MobHealthScaling cap is `5000` HP and the strongest natural spawn (PRIMORDIAL, level 100, 15,000 blocks out) computes `(20 + 99*6) * 60 = 36,840` HP but is clamped to **5,000**. MobDamageScaling for the same mob is `4.5 * (1 + 0.55*log1p(100/25)) = 8.48x`. Meanwhile players cap *every* skill at level 100 and gear caps at PRIMORDIAL (`craftStatMultiplier 4.0`, `armorPenetration 0.80`). So a max player will eventually delete any 5,000-HP mob in seconds and exploration runs out of new chunks to discover (Navigation XP dries up).

**Endgame must therefore (a) break the 5,000 ceiling in controlled instances, (b) give players reasons to keep playing past skill cap, and (c) create human-vs-human stakes.** All three below are designed to ride the existing levers rather than invent parallel ones.

---

### 1. The Threat axis — a vertical scaling channel that bypasses the world cap

The natural world stops at level 100 / 5,000 HP. Endgame content lives on a second, multiplicative axis called **Threat (T0..T20)**, applied only inside endgame instances/events so the open world stays sane.

| Concept | Open world | Endgame (Threat) |
|---|---|---|
| Source of difficulty | distance from spawn → level 1..100 | Threat tier T0..T20 chosen on entry |
| HP formula | `(20 + (lvl-1)*6) * tierMult`, cap 5,000 | `instanceBaseHP * tierMult * threatHP(T)` — **no 5,000 cap; separate cap 50,000,000** |
| Damage formula | `tierBase * (1 + 0.55*log1p(lvl/25))`, cap 8.48x | open-world value **×** `threatDmg(T)` |
| Reward tier ceiling | up to PRIMORDIAL drops gated by tier | guaranteed tier floor rises with Threat |

`threatHP(T) = pow(1.6, T)` and `threatDmg(T) = 1 + 0.18*T`. Concrete:

| Threat | HP mult | Dmg mult | A PRIMORDIAL "anchor" mob HP (base 5,000) | Guaranteed loot floor |
|---|---|---|---|---|
| T0 | 1.0 | 1.00 | 5,000 | COMMON |
| T2 | 2.56 | 1.36 | 12,800 | UNCOMMON |
| T5 | 10.5 | 1.90 | 52,400 | RARE |
| T8 | 43 | 2.44 | 214,700 | EPIC |
| T11 | 176 | 2.98 | 879,600 | LEGENDARY |
| T14 | 721 | 3.52 | 3,604,000 | PERFECT |
| T17 | 2,954 | 4.06 | 14,770,000 | MYTHIC |
| T20 | 12,089 | 4.60 | 50,000,000 (cap) | GODLY floor, PRIMORDIAL roll |

**Implementation:** add a `ThreatScaling` manager mirroring `MobHealthScaling`/`MobDamageScaling`, reading a `threat-scaling` section of combat.yml. Endgame mobs are spawned with a persistent data tag `cerb:threat` (int) and `cerb:instance` (UUID); `MobScalingListener` checks for the tag and, if present, routes through `ThreatScaling` instead of the distance-based path. This is a clean fork that does not touch the open-world balance and is fully reloadable.

---

### 2. Endgame activity catalog

#### 2.1 Depth Spiral (instanced procedural raid) — the flagship PvE loop

A 3–5 player instanced descent. Each "floor" is a sealed arena keyed to an **Echo Family** (SAND / TIDE / ASH / FROST / MIRE — roadmap). The party picks a Family and a starting Threat; every floor cleared raises effective Threat by +1 and rotates Family modifiers.

| Floor | Content | Threat | Family modifier example (FROST) |
|---|---|---|---|
| 1–2 | trash waves (RARE/EPIC mobs) | chosen T | mobs apply Slow on hit; standing still > 3s freezes you |
| 3 | mini-boss (Family Warden) | T+1 | floor periodically ices over, removing footing |
| 4–5 | trash + environmental hazard | T+2 | falling icicles deal ElementalIce |
| 6 | **Echo Boss** (Family Primarch) | T+3 | enrage at 25% HP doubles attack speed |

- Reuses `MobRegistry` (new `mobs.yml` entries `depth_warden_frost`, `depth_primarch_frost`, ... with `classification: BOSS`), `CustomDamageType` (ElementalIce etc.), `TrapManager` for arena hazards, and `MobContext` tier resolution.
- **Family tokens** drop on boss kill (roadmap "token mechanics"): 1 token per Echo Boss, +1 per Threat above entry. Tokens are the currency for keyed armor and Nomicons (roadmap Gear Progression).
- **Lockout / scaling reward:** loot quality floor = the Threat-tier floor table above. Higher Threat = strictly better expected tier, so the party self-selects difficulty. No daily lockout — it is the time/risk cost that gates farming, not a timer.
- **Folia note:** each Depth Spiral instance is a region-bound arena. Spawn arenas in a dedicated `cerb_depths` world far from spawn so the open-world distance scaling never bleeds in; entity scheduling stays region-local (Folia-safe).

#### 2.2 Abyssal Ladder (boss rush / score attack)

Solo or duo. A fixed sequence of the Family Primarchs at *escalating* Threat, back to back, on a shared HP/MP/cooldown budget (no full heals between bosses — only **Reservoir Inversion**, roadmap, lets you convert your remaining MP pool into emergency HP once per run). Score = highest Threat boss downed + time bonus. Feeds the **seasonal leaderboard** (§4). This is the purest expression of "infinite-scaling zone leveraging the level-cap mechanics": there is no level 101, but there is always a higher Threat.

#### 2.3 Rift Anomalies (open-world repeatable, no instance)

Periodic world events: a **Rift** opens at a random discovered chunk (ties into `ChunkDiscoveryManager` — only spawns in chunks at least one player has visited, so it rewards explorers). It spawns a wave of mobs whose Threat = `floor(distanceLevel / 5)` capped at T8, giving the *open world* a controlled taste of Threat scaling without instances. First player to clear gets a temporary luck buff via `LuckManager.addTemporaryLuckPoints` (e.g. +50 for 600s) and a Family token. This keeps non-instanced, low-coordination players engaged.

#### 2.4 Infinite Descent Well (true infinite scaling)

A single global structure at world spawn. Each "dive" picks up where you left your **Descent Depth** (persisted per player, new DB column). Depth D maps to Threat `min(20, floor(D/3))` plus a flat post-T20 HP multiplier `1 + 0.25*(D - 60)` for D>60 — this is where the *truly* infinite tail lives, for the handful of players chasing the global Descent leaderboard. Pure score chase; cosmetic-only rewards past T20 to avoid power creep.

---

### 3. PvP layer

Four interlocking modes, all gated by a hidden **Combat Rating (CR)** so a fresh-100 player is not thrown to a PRIMORDIAL-geared veteran.

#### 3.1 PvP Tiers (roadmap) — the ranking ladder

A per-season CR using the same `Tier` enum names as **rank badges** (so the unified tier vocabulary carries into PvP):

| Rank badge | CR range | Matchmaking pool |
|---|---|---|
| WEAK | 0–199 | newcomer protected bracket |
| COMMON | 200–499 | open |
| RARE | 800–1199 | open |
| LEGENDARY | 1600–2199 | open, queue priority |
| MYTHIC | 2600–3199 | flagged on scoreboard |
| GODLY | 3600–4199 | leaderboard eligible |
| PRIMORDIAL | 4600+ | top-100 only, season title |

CR is Elo-style: `delta = K * (result - expected)`, `K=32` below LEGENDARY, `K=16` above. Win/loss recorded in a new `player_pvp_rating` table via `AsyncSaveManager`. Decay of 25 CR/week above MYTHIC to keep the top ladder active.

#### 3.2 Risk Zones (roadmap "Risk-based zones" + "directional scanning")

Concentric with the *existing* distance-from-spawn axis — the same metric that drives mob level now drives PvP risk, so the world reads consistently:

| Zone | Distance from spawn | PvP | Loot-on-death | Mechanic |
|---|---|---|---|---|
| Sanctuary | 0–600 (lvl 1–4) | off | none | safe trade hub |
| Frontier | 600–4,500 (lvl 5–30) | opt-in flag | flagged inventory only | duels, no penalty if unflagged |
| Contested | 4,500–10,500 (lvl 31–70) | on | dropped tier-keyed gear | **directional scanning**: compass-style HUD pings nearest enemy player every 30s within 200 blocks |
| Abyssal | 10,500–15,000 (lvl 71–100) | always on, FFA | full keyed-armor durability loss (roadmap "entropy durability") | best Rift/Threat spawns; outposts live here |

**Directional scanning** reuses the HUD pipeline (`PlayerHUDManager` / `DefenseUpdateListener`) to render a bearing arrow. StealthSkill reduces your scan radius (you ping later); NavigationSkill widens *your* scan range — folding two existing skills into PvP without new stats.

#### 3.3 Outpost Sieges (roadmap)

Capturable structures in Contested/Abyssal owned by a **Warband** (§5). A siege is a scheduled 20-minute window:
- Attacker breaches an **Aegis Node** (an EPIC-tier MobRegistry entity with `instanceBaseHP` scaled to defender count: `25,000 * defenders`, routed through ThreatScaling at T = current season tier).
- Defenders deploy **Traps** (full reuse of `TrapManager`: Spike/Blast/Flame/Freeze nodes as defensive emplacements) and Family-keyed wards.
- Holding an outpost grants the Warband a **Reservoir tap**: passive Family-token trickle + a 5% craft-stat bonus to members' crafting (hooks `CraftingManager` tier multiplier). This is the territorial endgame carrot.

#### 3.4 Blink Gate contestation (roadmap "Blink Gates")

Fast-travel nodes unlocked via `ChunkDiscoveryManager` (you can only link gates between chunks you've personally discovered). In Contested+ zones, a gate must be **charged** (channel for 8s) to use — channeling is interruptible by PvP damage, turning gates into ambush flashpoints. Owning the outpost adjacent to a gate makes your Warband's channel instant. Ties exploration → travel → PvP into one loop.

---

### 4. Seasons, leaderboards & prestige

#### 4.1 Seasons ("Reservoir Cycles")

A season is an 8–10 week container. At rollover: PvP CR soft-resets toward COMMON (`newCR = 400 + (oldCR-400)*0.4`), leaderboards snapshot into a permanent Hall of Records, and a new **Reservoir theme** rotates one Echo Family into focus (its Depth Spiral drops are +1 tier floor that season, its Outposts grant double tokens). This guarantees the meta shifts and every Family eventually gets a spotlight.

#### 4.2 Leaderboards

Stored in dedicated tables, written async, surfaced in a GUI (new `LeaderboardGUI` reusing `GuiBuilder`/`GuiIdentity`):

| Board | Metric | Reset |
|---|---|---|
| Abyssal Ladder | highest Threat boss + time | per season |
| Infinite Descent | max Descent Depth | all-time (never resets) |
| PvP CR | rank | per season |
| Outpost Dominion | Warband outpost-hours held | per season |
| Family Devotion | Family tokens earned | per season |

#### 4.3 Prestige — the "Echelon" soft-reset (the core retention loop)

When a player has all combat-relevant skills at 100 *and* a full PRIMORDIAL kit, they may **Echelon**: reset chosen skills to level 1 in exchange for a permanent **Echelon Mark** (+1 each). Each Mark grants a small permanent multiplier applied through existing scaling:

- `+1.5%` skill-XP gain per Mark (re-leveling is faster each cycle),
- `+1` to the effective Threat floor a player solos comfortably (cosmetic gating),
- unlocks the next **Inventory Badge** tier requirement faster.

Marks are *account-permanent*, capped at 20 to mirror the tier count. This is the proven "renown/paragon" loop that gives the level-100 player a reason to keep earning XP forever.

#### 4.4 Inventory Badge (roadmap, 20 tiers) as a prestige sink

The 20-tier Inventory Badge is the natural prestige progression bar. Each tier (Badge I..XX, named after the 10 tiers doubled, e.g. "Common Badge", "Greater Common Badge", ...) unlocks +1 row of carrying capacity and a small passive (LuckManager permanent luck, mana cap, or defense-bar base-max bump). Tier-up cost = Family tokens + an Echelon Mark gate at Badge XI+, so the Inventory Badge becomes the visible spine that ties tokens, Echelon, and Families together.

---

### 5. Group & social systems

| System | What it is | Reuses |
|---|---|---|
| **Warbands** | 5–25 player guild. Shared outpost ownership, shared Family allegiance, shared token bank. | new `Warband` manager + DB table; AsyncSaveManager |
| **Family Wars** | Seasonal Family-vs-Family meta-conflict. Each Family's aggregate Outpost Dominion + Devotion determines the season-winning Family; members of the winning Family get a cosmetic banner + next-season token discount. | Outpost + leaderboard data |
| **Contracts (escrow)** [roadmap, deferred] | Player-to-player escrow trades with dispute resolution. Endgame justifies it: keyed armor / Nomicons / tokens are tradeable high-value goods worth escrowing. | TradingSkill, new escrow table |
| **Warband Reservoir** | Guild-level shared progression bar fed by all members' endgame activity; unlocks Warband-wide perks (siege buffs, gate channel speed). | mirrors player Reservoir |
| **Mentor bounties** | A capped player can flag as Mentor; partying with a sub-50 player grants both a token trickle. Combats the "veterans ignore newbies" problem. | SkillManager level reads |

---

### 6. Reward & power-budget guardrails

To avoid the "implement first, balance later" trap turning into infinite power creep:

- **Threat past T20 yields only cosmetics + leaderboard score** — no stat items. Caps the gear power ceiling at PRIMORDIAL forever.
- **Echelon Marks cap at 20** and only touch XP-rate and gating, never raw damage, so PvP stays skill-expressive.
- **PvP damage uses a separate `pvp-damage-multiplier` global** (default 0.6) in combat.yml so a build tuned to one-shot 5,000-HP mobs does not one-shot a 4,000-HP player. Without this, the open-world power curve makes PvP a coin-flip of who clicks first.
- **Entropy durability (roadmap)** on keyed armor is the money sink: dying in Abyssal/sieges burns durability that only Family tokens (or RepairingSkill at high level) restore — closing the token-faucet loop.

---

### What you're probably missing

1. **The 5,000-HP cap is invisible today but becomes the single biggest endgame blocker.** A level-100 PRIMORDIAL mob *wants* to be 36,840 HP but is clamped to 5,000 — meaning the entire top half of your tier×level matrix is wasted in the open world. The Threat axis is mandatory, not optional, or endgame mobs literally cannot be made harder than a mid-game mob.

2. **Navigation XP and chunk discovery dry up permanently.** `ChunkDiscoveryManager` awards XP once per chunk forever; a max explorer has zero reason to roam. Rift Anomalies + Blink Gate discovery re-monetize already-visited chunks. Without this, your most-developed exploration system is dead content at endgame.

3. **PvP balance is unsolvable without decoupling PvP damage from PvE damage.** Your skill scaling (e.g. `weapon-mastery damage-multiplier` cap 2.5x, `critical-strike crit-multiplier` 3.5x, lifesteal 0.40) is tuned to chew through 5,000-HP mobs. Applied raw to a player it is a one-shot meta. The `pvp-damage-multiplier` global is the cheapest fix and must be designed in from day one.

4. **Folia region threading constrains "raids" hard.** A 5-player Depth Spiral must keep all entities and players in one region thread, or you hit cross-region scheduling pain. Design instances as single-region sealed arenas in a dedicated world — do NOT try to make a sprawling multi-region raid dungeon.

5. **Reservoir Inversion is your best endgame mechanic and it's marked "not started."** Swapping the HP/MP pools is a perfect boss-rush survival button (the Abyssal Ladder no-heal constraint). It single-handedly makes the mana system (currently mostly a spell-cost meter) matter at endgame. Prioritize it over flashier roadmap items.

6. **Soft-reset prestige (Echelon) needs a partial-reset model, not full wipe.** Players will not re-grind 30+ utility skills. Let them choose *which* skills to Echelon (combat-only by default) so prestige feels like a build choice, not punishment. A full-wipe prestige will bleed your most committed players.

7. **No anti-blob / queue health design.** A small server cannot fill a 25-player siege or a 5-player T20 raid. Every endgame activity needs a *solo-viable* floor (Abyssal Ladder solo, Infinite Descent solo, Risk Zone duels) so content isn't gated behind a population you may not have.

8. **Loot-on-death in Risk Zones interacts violently with the keyed-armor + entropy systems.** Dropping per-Family keyed armor on death must be reconciled with durability/entropy so a single gank doesn't permanently destroy weeks of token grind. Decide whether keyed gear drops as a *soulbound shard* (recoverable) vs. a real item (lootable) before building Risk Zones.

9. **Leaderboard integrity on a custom-health server.** Your virtual-health, defense-bar, and damage-display systems make "fastest kill" and "highest Threat" scores trivially forgeable via config edits or admin spawns. Score-bearing instances must validate that mobs were ThreatScaling-spawned (not `/spawncustomzombie`) before recording a leaderboard entry.

10. **You need a sink for *temporary* LuckManager points.** Endgame produces huge luck stacks (Rift buffs, Family events) but `LuckManager` only multiplies loot chance. Without a high-tier loot table that *responds* to luck above a threshold, stacking luck feels inert — design a Family-token / Nomicon roll that visibly scales with luck factor so the system has endgame teeth.

---

## Cross-Domain Consistency Notes

- TIER LADDER IS LAW: every domain must use the exact unified Tier enum (WEAK→COMMON→UNCOMMON→RARE→EPIC→LEGENDARY→PERFECT→MYTHIC→GODLY→PRIMORDIAL) — 10 tiers, in order. Delete the duplicate Quality enums in CraftingManager (3.5x) and QualityBuffs and migrate both to Tier.craftStatMultiplier (0.5x..4.0x). Bands, loot, drops, and crafting quality all reference the SAME ladder via ordinal.
- THE HP-CAP NUMBER must be agreed once and consumed everywhere: Bestiary (mob authoring), Gear (calibration target), World/Zones (endgame Band authoring), and Endgame (Threat baseline) ALL assume the same per-tier cap. The current flat 5,000 contradicts all four. Recommendation: per-tier health-cap map. Do NOT let Gear tune against 5,000 while Zones assume 25,000 — pick one map, put it in combat.yml, reference it from one place.
- PLAYER POWER CEILING is the anti-power-creep anchor shared by Progression (sets it: ~1,000 HP skills / ~1,500 gear, 6.0x offense) and every domain that spawns threats (mob damage curve, boss DPS gates, PvP scalar all balance against it). If anyone changes the ceiling, the whole mob curve must be retuned. Treat it as a frozen constant.
- ELEMENT TYPES must agree on a single enum across Bestiary (mob family affinity), Signature (reaction grammar), and Gear (infusion affixes). There are 8 ElementTypes but TIDE and FROST both currently map to ICE — this collision MUST be resolved (split into specialEffect variants is recommended) before family keying, mob affinities, OR reaction tables are authored, or the three domains will diverge.
- FAMILY TOKEN NAMING + DROP RULES: Bestiary, Loot, Signature, and Endgame all reference family tokens (Glass Shard/Brine Pearl/Cinder Mote/Rime Crystal/Spore Sac). Agree the canonical names AND the drop rule (recommended: only same-family mobs drop their token) in one place, since tokens feed attunement (Signature), keyed armor (Gear), Inventory Badge tier-up (Progression), and Family Wars (Endgame).
- DISTANCE→LEVEL is the single source of truth for difficulty: MobHealthScaling.levelForDistance() (150 blocks/level, L1..100) must be the ONLY level derivation. World/Zones must reuse it (extract distanceToSpawn() to share with BandResolver), not reimplement. Multi-world rules (Nether 8:1 compression, End) must be resolved centrally — decide per-world blocks-per-level once.
- PVP DAMAGE SCALAR: Gear, Endgame, and Combat must agree on ONE pvp-damage-multiplier (proposed 0.6) and WHERE it applies (pre- vs post-defense-bar). Gear is tuned against 5,000-HP mobs but players have ~100 HP; without a single agreed scalar in combat.yml and PvP-aware routing in DamageResolution, mob-tuned gear one-shots players. This number is referenced by both the Gear vs-player scalar and the Endgame PvP layer — they must be the same value.
- RESERVOIR INVERSION CONVERSION MODEL must be locked as SWAP-BY-FILL-PERCENTAGE (not raw values), shared by Signature (defines it) and Endgame (uses it as the Abyssal Ladder survival mechanic). Because HP max (up to the cap, thousands) dwarfs the 1000 base mana, a raw-value swap would be nonsensical. Both domains must reference the percentage model.
- CURRENCY MODEL: Progression and Endgame both touch currencies. Agree the three-currency model (Marrow soft / Echo Shards family-premium / Entropy durability-fuel) once, OR fold Echo Shards into the Echo Families domain for a two-currency economy — but do not let Progression assume three while Endgame assumes two. Entropy durability is the load-bearing continuous sink referenced by Gear AND Progression.
- BIND-ON-PICKUP BAND must be agreed by Progression, Gear, and Endgame (Risk Zone death penalties): proposed GODLY/PRIMORDIAL + Nomicons bound, RARE–PERFECT tradeable. This single decision defines whether a player economy and the deferred Contracts escrow can exist; all three domains' loot/trade rules depend on it.
- NAMED-SPECIAL DOWNGRADE RULE: Bestiary and World/Zones must agree that the Band floor-tier rule force-PROMOTES generic mobs to the Band floor tier but NEVER downgrades named mobs.yml specials (a PRIMORDIAL boss authored in mobs.yml stays PRIMORDIAL even in a low Band). MobScalingListener already resolves tier from the registry first — the floor rule must only raise, never lower.
- TERMINOLOGY: 'Band' (World/Zones) and 'tier' (everything else) are distinct but 1:1 mapped — a Band IS the geographic expression of a tier. Keep the words separate in code/docs to avoid confusion: Bands are places, Tiers are quality grades. Endgame's 'Threat tier' is a THIRD axis (instanced difficulty multiplier) that must not be conflated with either.
