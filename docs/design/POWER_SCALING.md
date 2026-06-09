# Cerberus — Cultivation Power-Scaling Math

*Generated 2026-06-08 (cultivation/xianxia design pass). Companion to the [master design plan](CERBERUS_DESIGN_PLAN.md). Reflects the **controlled-hyperscaling** doctrine — see [CULTIVATION_INTEGRATION.md](CULTIVATION_INTEGRATION.md) for what this overturns from the original plan.*

---

# Cultivation Power-Scaling Math

> *Supersedes the anti-power-creep doctrine of `CERBERUS_DESIGN_PLAN.md` §7 (Rules 1–8) and the conservative ceilings in `OPEN_DECISIONS.md` (player ~1,500 HP, 6.0× offense clamp, 35k/per-tier mob HP). Those rules were written to keep numbers "readable in the hundreds." The new creator direction is the opposite: numbers are **meant** to become astronomically large. What survives from the old doctrine is **only the anti-*bug* core** — nothing may overflow, nothing may scale *accidentally*, every huge number must be intentional, tunable from one curve, and representable in its data type. Power-creep is now a feature; silent corruption is still a defect.*

The reconciliation in one sentence: **keep the engine, delete the caps, re-anchor every multiplier onto a single Realm curve, and replace the two attribute-bound HP paths (player vanilla-health sync and mob `MAX_HEALTH`) with double/`long`/`BigInteger`-backed virtual pools that can legitimately hold trillions.**

---

## 1. Representation & Numerical Safety

### 1.1 The three magnitude regimes and which type owns them

Cultivation scaling spans roughly **10¹ → 10¹⁵+** (tens of HP at spawn to trillions+ at the apex realm, with the infinite Threat/Depth tail going further). No single primitive is correct across that whole span, so Cerberus uses a **tiered representation** keyed to where a value lives:

| Regime | Magnitude | Type | Where it lives | Rationale |
|---|---|---|---|---|
| **Live combat values** (a single hit, a tick of regen, current/max HP, defense-bar, mana) | up to ~10¹⁵ (≈ 9.0 PB of integer precision in a `double`) | **`double`** | `PlayerVirtualHealthManager` (already `double`), new `MobVirtualHealthManager` | The existing virtual-HP pool is already a `double`. A `double` holds every integer exactly up to 2⁵³ ≈ 9.007×10¹⁵ ("nine quadrillion"). That is *past trillions* — enough for the entire normal game (realm cap ~10¹³, see §2). Arithmetic is one CPU instruction; this is the hot path running every hit on a region thread. |
| **Accumulators that only grow** (cumulative skill XP to 100, lifetime damage dealt, account-wide currency wallets) | up to ~9.2×10¹⁸ | **`long`** | `SkillManager` XP table, currency ledgers | A monotonically increasing counter must never wrap. `long` (signed 64-bit) caps at 9,223,372,036,854,775,807. The XP-to-100 total is ~10⁷ (§1.4) — `long` is 12 orders of magnitude of headroom, future-proof against any curve re-tune. |
| **The infinite tail** (Depth Spiral / Abyssal Ladder Threat HP past the realm cap, "beyond-PRIMORDIAL" leaderboard scoring) | unbounded (10¹⁶ → ∞) | **`BigInteger`** (lazy, instanced-only) | new `ThreatScaling`, only inside `cerb_depths` instances | This is the *only* place that can exceed 2⁵³. It is also the only place that is **not** on the hot per-hit overworld path — Depth floors are instanced, low-population, and tick a handful of boss entities. `BigInteger` cost is acceptable there and nowhere else. |

**Rule:** the overworld combat loop is **`double`-only**. `BigInteger` never touches a `CreatureSpawnEvent`, an `EntityDamageEvent`, or a HUD tick. If a value would exceed ~10¹⁵ in normal play, that is a tuning bug, not a reason to promote the type.

### 1.2 The mob-HP attribute landmine (the real reason mobs need a pool)

`MobScalingListener` currently does (lines 77–78):

```java
maxHealthAttr.setBaseValue(maxHealth);   // Attribute.MAX_HEALTH
entity.setHealth(maxHealth);
```

**Minecraft hard-caps `Attribute.MAX_HEALTH` at 1024.** Paper/Folia silently clamp `setBaseValue` to that ceiling. So the `health-cap: 5000` in `combat.yml` is **already a fiction** — no mob in the live game has ever actually had 5000 HP; it has had at most 1024, and any "5000-HP mob" was a 1024-HP mob wearing a 5000 label. This is why the cap discussion in the old docs is moot for the new direction: you cannot reach millions through the attribute at all.

**Fix: mobs get a virtual-HP pool, exactly mirroring players.** Introduce `MobVirtualHealthManager` and `MobHealthState`:

- On spawn (`MobScalingListener`), compute the *true* virtual max (a `double`, can be billions) and store it in a `ConcurrentHashMap<UUID, MobHealthState>` (or PDC for persistence across chunk unload — see §1.3). Set the **vanilla** `MAX_HEALTH` attribute to a fixed cosmetic value (e.g. 40.0) only so the vanilla health bar renders; the real pool is the source of truth.
- Player→mob damage routes through the currently-empty hook `VirtualHealthListener.applyDamageMultiplierToEntity(entity, damage)` (line 237, today a no-op pass-through). Rewrite it to: (a) resolve the mob's virtual pool, (b) subtract the *real* (possibly billions) damage, (c) set the event's vanilla damage to 0 and instead drive a proportional vanilla-health drain for the rendered bar (`vanillaHealth = 40 * (virtualCurrent / virtualMax)`), (d) call `entity.setHealth(0)` to trigger the real death + loot only when the virtual pool hits 0.
- This is the **same pattern already proven on players** (`event.setDamage(0)` + virtual bookkeeping + guaranteed-lethal finalizer). Folia-safe: `CreatureSpawnEvent` and `EntityDamageEvent` both fire on the owning region thread, so pool mutation is inline, no scheduling.

**`health-cap` semantics change:** it is no longer "the number we clamp the attribute to" (impossible) but "a per-realm sanity ceiling on the `double` pool" — present only to catch a runaway tuning bug, set far above any intended value (see §2 table). Delete the global `5000.0` default; replace with the per-realm map in §2.4.

### 1.3 Folia / Minecraft performance pitfalls

| Pitfall | Consequence | Mitigation |
|---|---|---|
| **`double` precision loss above 2⁵³** | At 10¹⁶+, integer additions lose the ones digit; a "−5" hit might not register, making a boss unkillable. | Keep the **overworld realm cap below 10¹⁴** (§2 tops out at ~3.3×10¹³). Only the instanced `BigInteger` Threat tail goes higher, and there subtraction is exact. |
| **`BigInteger` on a region thread** | Allocation + GC churn per hit; can stall a region tick under load. | `BigInteger` is **forbidden** outside `cerb_depths` instances. Even there, batch damage per tick rather than per hit. |
| **PDC storage of huge numbers** | PDC has no native `double`-or-bigger "money" type; storing as `String` and re-parsing every spawn is slow and lossy. | Store mob virtual-max as a **`PersistentDataType.DOUBLE`** (PDC supports it) keyed once at spawn; store level as the existing `INTEGER` tag. Recompute (cheap, §2.4 formula) on chunk reload rather than persisting current HP for transient mobs; persist current HP only for named/boss elites via the boss state table. |
| **Number formatting allocating per HUD tick** | The HUD updates every 20 ticks per player; naive `String.format` + `BigDecimal` churns garbage. | Cache the formatted string on the `MobHealthState` / player state; re-format only when the underlying value crosses a display bucket (§1.5), not every tick. |
| **`(int) amount` truncation in XP add** | `SkillManager.addXp` does `currentXP + (int) amount` (line 240) — fine for XP, but the same casting pattern must **never** be copied into the HP/damage path where values exceed `int`. | Audit: all HP/damage/currency math is `double`/`long` end-to-end; `int` only for level (1–100) and tier ordinal. |

### 1.4 Fixing the XP-curve integer overflow (the one mandatory bug fix)

`SkillManager.calculateRequiredXpForNextLevel` (lines 377–383):

```java
int currentLevelXP = 100;
for (int i = 1; i < currentLevel; i++) currentLevelXP = currentLevelXP * 2;   // 100 * 2^(level-1)
return currentLevelXP;   // int → overflows ~level 25 (2^31), returns negative garbage
```

At level 25 this is `100 * 2²⁴ ≈ 1.67×10⁹`, still inside `int`; at level 28 it passes 2³¹ and wraps **negative**, so `newXP >= reqXP` becomes true on the first kill and the player rockets to 100 — *or* the comparison flips and they can never level. Either way progression is bricked in the high 20s. This is a literal bug and must die regardless of the cultivation direction.

**Replacement — hybrid polynomial, `long` accumulator, precomputed table:**

```
costToReach(L)  =  round( BASE * L^EXP  +  LINEAR * L )      // per-level cost, L in [2..100]
   BASE = 75, EXP = 1.85, LINEAR = 40                         // one unified curve for v1
cumulative[L]   =  sum over k=2..L of costToReach(k)          // long[101], built once at load
```

- **No overflow:** cumulative-to-100 ≈ **1.9×10⁷ XP** (`long` headroom: 12 orders of magnitude).
- **Monotonic, reachable, smooth** — unit-testable: assert strictly increasing, assert `cumulative[100]` within ±1% of target, assert no negative.
- Built **once** into a `long[101]` lookup at load (do not recompute the loop per XP gain).
- A single per-category `EXP` knob in config reserved for later (utility skills could cap faster) but v1 ships one curve.

> Note this XP curve is **deliberately modest** (~19M, reachable) even though combat numbers are astronomical. Skill *level* (1–100) is an exponent input, not a magnitude — the astronomical numbers come from realms and gear (§2), not from XP totals. Keeping XP human-scaled is what keeps the *grind* sane while *power* explodes.

### 1.5 HUD number formatting (the "1.2B / 3.4T / cultivation units" layer)

A new `NumberFormat` utility, used by `PlayerHUDManager`, floaty damage numbers, and mob name labels. Two display modes (config / per-player toggle), both reading the same `double`:

**Mode A — Western short-scale (default, legible to everyone):**

| Range | Format | Example input | Output |
|---|---|---|---|
| < 10,000 | integer | 8,420 | `8420` |
| 10⁴–10⁶ | `#.#K` | 142,500 | `142.5K` |
| 10⁶–10⁹ | `#.##M` | 3,400,000 | `3.4M` |
| 10⁹–10¹² | `#.##B` | 1,200,000,000 | `1.2B` |
| 10¹²–10¹⁵ | `#.##T` | 3,400,000,000,000 | `3.4T` |
| 10¹⁵+ | `#.##Qa`, then scientific | 5.1×10¹⁷ | `510Qa` → `5.1e17` |

Suffix ladder: `K, M, B, T, Qa, Qi, Sx, Sp, Oc, No, Dc` (then fall back to `#.##e##`). Three significant figures max; never render the full digit string in the HUD.

**Mode B — Cultivation units (flavor, ties numbers to the realm fiction):** the same `double` is bucketed into named **cultivation magnitudes** that line up with realm steps, so the player reads *meaning* not just digits:

| Magnitude band | Cultivation unit (display) | Roughly |
|---|---|---|
| 10⁰–10² | *Qi* | tens |
| 10²–10⁴ | *Marrow* | hundreds–thousands |
| 10⁴–10⁶ | *Meridian* | tens of thousands |
| 10⁶–10⁹ | *Pillar* | millions |
| 10⁹–10¹² | *Dao* | billions |
| 10¹²–10¹⁵ | *Heaven* | trillions |
| 10¹⁵+ | *Primordium* | beyond |

e.g. a 3,400,000-HP mob shows `3.4 Pillar` instead of `3.4M`. Mode B is cosmetic over the same number; combat math never touches it.

**Implementation guard:** formatting caches on state and only re-renders on bucket-crossing (§1.3), so the HUD's 1 Hz refresh does not allocate a `BigDecimal`/`String` every tick for a value that barely moved.

---

## 2. The Realm-Step Magnitude Curve

### 2.1 Realms = the existing Tier ladder, reinterpreted as orders of magnitude

The 10-tier ladder (`WEAK → … → PRIMORDIAL`) is **kept exactly** — but each tier is now a **cultivation Realm**, and a Realm is a *step-change in magnitude*, not a 1.35× nudge. The old design's "fixed ~1.35× per tier step" (Rule 7) is **replaced** by a steep geometric per-realm multiplier so that crossing a realm boundary is the "everyone in the room is instantly outclassed" moment.

**Realm names** (Western-forward, canonical per [NAMING.md](NAMING.md); display layer over the existing enum; the enum constants are unchanged so all code keeps working):

| Tier enum (unchanged) | Realm name (display) | Echo-Band feel |
|---|---|---|
| WEAK | Mortal | the safe heart |
| COMMON | Awakened | Hearth |
| UNCOMMON | Adept | Frontier |
| RARE | Champion | Marches |
| EPIC | Ascendant | Marches/Wastes |
| LEGENDARY | Paragon | Wastes |
| PERFECT | Transcendent | Wastes/Echofields |
| MYTHIC | Demigod | Echofields |
| GODLY | Empyrean | Echofields/Maw |
| PRIMORDIAL | Primordial | The Maw |

### 2.2 The single central curve (everything derives from this)

One config block (`power-curve` in `combat.yml`) is the **sole source of truth**. Two compounding axes:

1. **Realm step** `R(tier)` — the order-of-magnitude jump per realm. Geometric with ratio **≈ ×8 per realm** (tunable), so 9 steps from COMMON to PRIMORDIAL ≈ ×8⁹ ≈ ×1.34×10⁸ (a hundred-million-fold span across realms).
2. **Within-realm level curve** `L(level)` — a *gentle* climb across the 100 levels inside a realm's band, so fights *within* a realm stay readable (a ~×5–8 span across the level range, not orders of magnitude). This is what §4 protects.

```
realmMult(tier)   = REALM_BASE ^ realmIndex(tier)         # REALM_BASE = 8.0 ; realmIndex: WEAK=−1..PRIMORDIAL=8, COMMON=0
levelMult(level)  = 1 + LEVEL_SLOPE * (level - 1)         # LEVEL_SLOPE = 0.05  →  ×1.0 at L1 .. ×5.95 at L100
mobMaxHP(tier,L)  = HP_ANCHOR  * realmMult(tier) * levelMult(L)      # HP_ANCHOR = 50  (a COMMON L1 mob = 50 HP)
mobDamage(tier,L) = DMG_ANCHOR * realmMult(tier) * levelMult(L)      # DMG_ANCHOR = 5
playerHP(realm,L) = HP_ANCHOR  * realmMult(realm) * levelMult(L) * PLAYER_HP_FACTOR   # PLAYER_HP_FACTOR ≈ 4
playerDmg(realm)  = DMG_ANCHOR * realmMult(realm) * gearBudget(realm)                 # gear is the realm-offense lever, §4
```

Key properties:
- A **player and the mobs of their own realm sit at the same order of magnitude** → fights are fair *within* a realm (the §4 invariant).
- Crossing into the **next realm multiplies your whole power band by ~×8** → the cultivation "step-change" feeling.
- All other systems (`Tier.craftStatMultiplier`, `Tier.xpMultiplier`, gear budgets) are re-expressed as functions of `realmMult` so nothing invents its own slope.

### 2.3 Expected power per realm (concrete numbers, monstrous endgame)

Computed from §2.2 with `REALM_BASE=8, HP_ANCHOR=50, DMG_ANCHOR=5, LEVEL_SLOPE=0.05, PLAYER_HP_FACTOR=4`. "Representative level" = the mid level a player typically is when they're *in* that realm's band.

| Realm (Tier) | realmMult | Rep. mob HP (HUD) | Rep. mob dmg/hit | Player HP (HUD) | Player dmg/hit | Felt magnitude |
|---|---|---|---|---|---|---|
| Mortal (WEAK) | 0.125 | ~6 | ~0.6 | ~25 | ~2 | tens |
| Awakened (COMMON) | 1 | ~50–300 | ~5–30 | ~200–1.2K | ~20–100 | hundreds |
| Adept (UNCOMMON) | 8 | ~2.4K (`2.4K`) | ~240 | ~10K (`10K`) | ~1.2K | thousands |
| Champion (RARE) | 64 | ~19K (`19K`) | ~1.9K | ~77K (`77K`) | ~12K | tens of thousands |
| Ascendant (EPIC) | 512 | ~205K (`205K`) | ~20K | ~820K (`820K`) | ~120K | hundreds of thousands |
| Paragon (LEGENDARY) | 4,096 | ~1.6M (`1.6M`) | ~160K | ~6.5M (`6.5M`) | ~1.1M | millions |
| Transcendent (PERFECT) | 32,768 | ~13M (`13M`) | ~1.3M | ~52M (`52M`) | ~9M | tens of millions |
| Demigod (MYTHIC) | 262,144 | ~105M (`105M`) | ~10M | ~420M (`420M`) | ~80M | hundreds of millions |
| Empyrean (GODLY) | 2,097,152 | ~840M (`840M`) | ~84M | ~3.4B (`3.4B`) | ~700M | billions |
| Primordial (PRIMORDIAL) | 16,777,216 | ~6.7B → 33B (boss) (`6.7B`/`33B`) | ~670M | ~27B (`27B`) | ~6B | billions → tens of billions |

**Bosses** apply a `bossHpFactor` (×5 for elites, ×20–×50 for apex bosses) on top of the table — a PRIMORDIAL apex boss (Cerberus, the Echo-Eater) sits at **~330B–840B HP** (`330B`/`840B` or `0.33 Heaven`/`0.84 Heaven`), comfortably inside `double` precision (10¹¹ « 2⁵³). The infinite Threat tail (§5) takes the *instanced* numbers from there to trillions+ via `BigInteger`.

This is the power fantasy made literal: a Primordial cultivator dealing **6 billion per swing** walking into a Foundation-realm zone whose mobs have 19K HP one-shots literally everything on screen — and §3 makes that *automatic and overwhelming* via realm suppression.

### 2.4 What this changes in `MobHealthScaling` / `combat.yml`

`MobHealthScaling.calculate` is rewritten from the additive `(base + level*hpPerLevel) * tierMult` clamped at 5000, to the **multiplicative realm curve**:

```java
double realmMult = REALM_BASE ^ realmIndex(tier);
double levelMult = 1 + LEVEL_SLOPE * (level - 1);
double raw = HP_ANCHOR * realmMult * levelMult;
return Math.min(raw, realmHealthCeiling(tier));   // ceiling is a *bug guard*, far above intended
```

`combat.yml` `mob-health-scaling` becomes (replacing the old `base/hp-per-level/health-cap/tier-multiplier`):

```yaml
power-curve:
  realm-base: 8.0          # ×8 magnitude per realm step
  hp-anchor: 50.0          # COMMON level-1 mob HP
  dmg-anchor: 5.0          # COMMON level-1 mob damage
  level-slope: 0.05        # within-realm climb (×5.95 over 100 levels)
  player-hp-factor: 4.0
  boss-hp-factor: 20.0
  # per-realm sanity ceiling on the virtual pool (NOT the old 1024-attribute cap).
  # Set ~3x the intended max so a tuning bug can't reach 2^53.
  realm-health-ceiling:
    WEAK: 1.0e3
    COMMON: 1.0e5
    UNCOMMON: 1.0e6
    RARE: 1.0e7
    EPIC: 1.0e8
    LEGENDARY: 1.0e9
    PERFECT: 1.0e10
    MYTHIC: 1.0e11
    GODLY: 1.0e12
    PRIMORDIAL: 1.0e14     # well under 9.0e15 (double-exact ceiling)
```

The `tier-multiplier` map in `mob-damage-scaling` is **removed** and that section also reads `realmMult` from `power-curve`, so the two halves of mob scaling tune from **one** place (resolving the doc's long-standing complaint that the damage cap and the HP cap drifted apart). The old `max-damage-multiplier: 10.0` clamp is deleted entirely — damage is now *meant* to be astronomical; the only ceiling is the per-realm pool ceiling and the §3 suppression bounds.

---

## 3. Realm Suppression (the signature "Yujiro walks in" mechanic)

Realm suppression is what converts a large *number* gap into a *narrative* gap: a higher-realm entity doesn't just out-stat a lower one, it **dominates** it — hits harder, takes almost nothing, and projects an aura that visibly weakens the lower-realm foe before a blow is even struck. This is the peak-cultivation-protagonist feeling and the single most important new mechanic in this section.

### 3.1 Realm gap

```
gap = realmIndex(attacker) - realmIndex(defender)     # +ve = attacker is higher realm
```

`realmIndex`: WEAK=−1, COMMON=0, UNCOMMON=1, … PRIMORDIAL=8. So a PRIMORDIAL hitting a RARE mob has `gap = 8 - 3 = +5`.

### 3.2 The three suppression effects

All three are driven by **one** smoothstep-style curve so they're tunable together. Let `g = clamp(gap, -SUPP_MAX_GAP, +SUPP_MAX_GAP)` with `SUPP_MAX_GAP = 4` (gaps beyond 4 realms are already near-absolute from raw magnitude, so suppression saturates — this prevents double-counting an already 8⁵-fold number gap).

**(a) Outgoing damage amplification (you hit a lower realm harder):**
```
suppressionDamageMult = (1 + SUPP_DMG_PER_GAP) ^ g          # SUPP_DMG_PER_GAP = 0.60
```
| gap | mult | feel |
|---|---|---|
| +1 | ×1.60 | meaningful edge over a same-band-but-one-realm-up foe |
| +2 | ×2.56 | clearly dominant |
| +3 | ×4.10 | crushing |
| +4 (capped) | ×6.55 | near-absolute *on top of* the already-×8/realm raw number gap |

**(b) Incoming damage reduction (a lower realm can barely scratch you):**
```
suppressionDR = 1 - (1 - SUPP_DR_PER_GAP) ^ g              # SUPP_DR_PER_GAP = 0.50, for g>0
```
| gap | DR | a lower-realm hit lands at |
|---|---|---|
| +1 | 50% | half |
| +2 | 75% | a quarter |
| +3 | 87.5% | an eighth |
| +4 (capped) | 93.75% | ~6% |

Bounded at **≤ 99% via a config `suppression-dr-cap: 0.99`** so a lower-realm entity is never *literally* immune to (e.g., a player can still chip a boss for a sliver, preserving "death by a thousand cuts" group play and avoiding a hard wall).

**(c) The Suppression Aura (the "walks into the room" effect):** a passive radius effect a higher-realm *entity* projects onto lower-realm foes in range (e.g. 12 blocks), applied as a debuff *before* combat — this is the pre-fight intimidation that makes the gap *felt*:
```
auraWeaken = min(AURA_MAX, AURA_PER_GAP * g)               # AURA_PER_GAP = 0.15, AURA_MAX = 0.60
```
Lower-realm foes in the aura suffer `auraWeaken` as a flat reduction to **their** outgoing damage and movement/attack speed (a GODLY player walking through a RARE zone = every mob there at 60% damage, sluggish, visibly cowed via a particle/`slow` overlay). For PvE pacing this only flows **downward** (high→low); same-realm entities are immune to each other's aura.

### 3.3 Combined effect & where it slots into the pipeline

For a single hit, suppression multiplies onto the §2 raw damage **before** the 3-layer defense math, and the DR applies as an extra resist layer:

```
effectiveOutgoing = rawDamage(§2) * suppressionDamageMult(gap)              # attacker side
... then 3-layer defense ...
effectiveTaken    = afterDefense * (1 - suppressionDR(gap))                 # defender side (gap from defender's POV)
```

Insertion points (no new pipeline, reuse existing hooks):
- **Player→mob:** in the rewritten `applyDamageMultiplierToEntity` (`VirtualHealthListener` line 237) — multiply by `suppressionDamageMult(playerRealm − mobRealm)` and reduce by the mob's `suppressionDR(mobRealm − playerRealm)` (negative gap → no reduction).
- **Mob→player:** in `applyResolvedDamage` (`PlayerVirtualHealthManager`) — same, with realms swapped. This **reuses `Tier.armorPenetration`** the doc already wanted folded in: suppression DR and armor-pen stack (a higher-realm mob both ignores your armor *and* takes little from you).
- **Aura:** a low-frequency (every 1 s) region-thread radius scan on named/elite entities only (perf-bounded; not every mob projects an aura — only elites/bosses/players above a realm threshold).

### 3.4 Why this doesn't break within-realm pacing

`gap = 0` → all three effects are identity (×1.0 damage, 0% DR, no aura). **Inside a realm, suppression is completely inert.** It only activates across realm boundaries, which is exactly where the design *wants* domination. Combined with §4's gentle within-realm level curve, a fight between two same-realm entities is governed entirely by the readable level/gear math — suppression is the *cross-realm* layer only. This is the explicit reconciliation: **huge across realms (raw ×8/realm × suppression ×6.5), fair within a realm (gap=0 ⇒ identity).**

---

## 4. Tunability & Within-Realm Balance

### 4.1 The within-realm invariant (what stays readable)

The old TTK doctrine (3–6 s kills, defense bar eats the fight) is **preserved, but only as a *within-realm* invariant**:

> At realm *N*, a player who legitimately progressed through realms 1..N has a **TTK of ~3–6 s** on same-realm mobs and a survival margin where the defense bar absorbs sustained fights but HP is at risk on mistakes — **regardless of whether the absolute numbers are 200 or 27 billion.**

This holds because *both* sides of a same-realm fight scale by the identical `realmMult × levelMult` (§2.2), so their **ratio** is constant across realms. Player offense (gear budget) and mob HP both climb ×8/realm, so the *number of hits to kill* is invariant even as the per-hit numbers go from 30 to 6,000,000,000. The HUD shows `6.5M HP` instead of `650 HP`, but the fight *feels* the same. That is the whole trick: **magnitude inflates; the gameplay ratio is conserved.**

### 4.2 The offense lever moves from a hard clamp to a realm-relative budget

The old **6.0× stacked-combat clamp** in `CombatSkillProcessor` was an *absolute* ceiling — it must go, because absolute clamps are meaningless when realm magnitudes span 10⁸. Replace it with a **realm-relative budget clamp**:

```
maxPlayerDamage(realm, level) = DMG_ANCHOR * realmMult(realm) * levelMult(level) * OFFENSE_BUDGET_CAP
   OFFENSE_BUDGET_CAP = 6.0      # gear + skills + crit + infusion may stack up to 6x the realm-anchored baseline
```

- Within a realm, **skill stacking is still clamped to 6×** the realm baseline — so the *intra-realm* balance the old doctrine protected (no one-shotting same-realm mobs from a stat stack) is fully preserved.
- But the *absolute* number that 6× represents scales with realm — at PRIMORDIAL the cap is ~6 billion, at COMMON it's ~120. Same gameplay shape, astronomically different magnitude.
- Crit-affix stacking still hard-caps crit *chance* at 0.75 (no 100%-crit one-shot meta), and crit *multiplier* at 3.5× — these are dimensionless ratios, so they need no realm adjustment.

`Tier.craftStatMultiplier` (0.5→4.0) is **reinterpreted as the gear realm budget** — gear of realm *N* contributes to the realm-*N* offense baseline; it is re-derived from `realmMult` so crafted-gear power tracks the realm curve instead of the old linear 0.5–4.0 ramp. Concretely: `gearBudget(realm) = realmMult(realm) ^ GEAR_EXP` with `GEAR_EXP = 1.0`, i.e. gear of your realm fully participates in your realm's power band; gear from a lower realm is dead weight (a Foundation sword does Foundation numbers, invisible against an Ascendant mob — itemization stays the realm-gated balance lever the design always intended).

### 4.3 One curve, central config, full reconciliation table

Everything above reads from the single `power-curve` block (§2.4). The reconciliation with the *already-built* systems:

| Existing system | Was | Becomes | Code touch |
|---|---|---|---|
| `MobHealthScaling.calculate` | additive `(20+level*6)*tierMult`, clamp 5000 | multiplicative `HP_ANCHOR*realmMult*levelMult`, per-realm ceiling | rewrite formula; read `power-curve` |
| `MobScalingListener` | sets `MAX_HEALTH` attribute (clamped to 1024!) | sets cosmetic vanilla max, stores true `double` max in `MobVirtualHealthManager` + PDC `DOUBLE` | new manager + pool write |
| `VirtualHealthListener.applyDamageMultiplierToEntity` | no-op pass-through (line 237) | drives mob virtual pool, applies §3 suppression, proportional vanilla-bar drain, lethal-at-0 | implement the hook |
| `MobDamageScaling` | `tierBase*(1+0.55*log1p(level/25))`, clamp 10× | `DMG_ANCHOR*realmMult*levelMult` + §3 suppression | remove tier-base map + 10× clamp; read curve |
| `Tier.craftStatMultiplier` (0.5–4.0) | linear gear stat ramp | gear realm budget `realmMult^GEAR_EXP` | derive from curve; keep enum constants |
| `Tier.xpMultiplier` (0.5–15) | kept | kept (XP stays human-scaled, §1.4) | none |
| `Tier.armorPenetration` (0–0.8) | player combat only | also feeds mob→player path, stacks with §3 suppression DR | wire into mob path |
| `CombatSkillProcessor` 6× clamp | absolute 6× over 100-HP base | realm-relative 6× over realm baseline (§4.2) | swap constant for `maxPlayerDamage()` |
| `SkillManager` XP curve | `int`, `100*2^(L-1)`, overflows L25 | `long` hybrid polynomial table, ~19M to 100 | rewrite (§1.4) |
| `PlayerVirtualHealthManager` HP | `double` pool, ~1,500 target | `double` pool, `playerHP(realm,level)` up to ~27B; existing 1,000,000 lethal floor in `getGuaranteedVanillaDeathDamage` raised to scale with realm | retune max-HP source; bump lethal-damage floor |
| `combat.yml` `health-cap: 5000`, `max-damage-multiplier: 10` | absolute caps | deleted; replaced by `power-curve` + per-realm ceilings | config migration adds `power-curve`, removes dead keys |

### 4.4 Tuning knobs, ranked by leverage

When the creator wants to dial the whole game, these are the handles (all in `power-curve`), most-impactful first:

1. **`realm-base` (8.0)** — *the* slope of the cultivation power fantasy. 6.0 = gentler steps; 10.0 = more violent realm jumps. Changing this rescales every number in §2.3 simultaneously and consistently.
2. **`level-slope` (0.05)** — within-realm climb. Higher = bigger gap between a fresh-in-realm and a maxed-in-realm cultivator (more grind value); lower = realms feel more "flat plateaus."
3. **`hp-anchor` / `dmg-anchor`** — the absolute floor; shifts the whole curve up/down without changing its shape.
4. **`SUPP_DMG_PER_GAP` / `SUPP_DR_PER_GAP` / `SUPP_MAX_GAP`** — how brutal realm suppression feels. Lower these for PvP balance (so a 1-realm PvP edge is winnable), keep high for PvE domination flavor.
5. **`OFFENSE_BUDGET_CAP` (6.0)** — within-realm stacking ceiling; the intra-realm TTK/balance dial.
6. **`boss-hp-factor`** — solo-vs-group boss sponginess, independent of the main curve.

Every one is a `double` in one config section; none requires touching another subsystem, satisfying "huge across realms, fair within, all from one central curve." A `/cerb power preview <realm> <level>` debug command (mirroring the existing scaling-params preview) prints the full derived table so the creator can re-tune and verify (including overflow-safety assertions: no value > 9.0×10¹⁵ outside instanced Threat content) without a server restart.
