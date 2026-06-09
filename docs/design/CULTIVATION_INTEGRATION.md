# Cerberus — Cultivation Integration & Doctrine Change

*Generated 2026-06-08 (cultivation/xianxia design pass). Companion to the [master design plan](CERBERUS_DESIGN_PLAN.md). Reflects the **controlled-hyperscaling** doctrine — see [CULTIVATION_INTEGRATION.md](CULTIVATION_INTEGRATION.md) for what this overturns from the original plan.*

---

## Cultivation Integration & Doctrine Change

> *Integration brief reconciling the four new design sections (Weapons, Gear, Power-Scaling Math, Cultivation Realms) with the existing `CERBERUS_DESIGN_PLAN.md` and `OPEN_DECISIONS.md`. This brief is authoritative where it conflicts with those documents; it explicitly enumerates every prior decision it overturns, what survives untouched, and how the new pillars slot into the existing 9-step build order. It is grounded in the live engine: `Tier` (`craftStatMultiplier` 0.5→4.0, `armorPenetration` 0→0.8, `xpMultiplier` 0.5→15), `MobHealthScaling`, `MobScalingListener` (the `Attribute.MAX_HEALTH` 1024 landmine), `PlayerVirtualHealthManager` (double pool), `SkillManager` (the `100·2^(L-1)` int overflow), and `CombatSkillProcessor` (the 6.0× clamp).*

---

### 1. What Changes — The New "Controlled Hyperscaling" Doctrine

The prior doctrine (`CERBERUS_DESIGN_PLAN.md` §7, Pillar 3, Pillar 7) was **anti-power-creep**: HP capped ~1,500, offense clamped 6.0×, mob HP capped ~35k, numbers deliberately kept "readable in the hundreds" (Rule 5), gear as the *only* power lever, and tiers as a gentle ~1.35×/step ratio. Its purpose was to keep every number small and the defense bar as the sole TTK dial.

**That doctrine is retired and replaced by Controlled Hyperscaling.** The new law, in one sentence:

> **Power is *meant* to become astronomically large — hundreds → millions → billions → trillions+ — and a higher realm hard-dominates a lower one (realm suppression). The only limits that survive are anti-*bug* rails: nothing may overflow, nothing may scale *by accident*. Every huge number must be intentional, tunable from one central curve, and representable in its data type.**

The doctrine has two halves that must be held simultaneously:

| Half | Statement |
|---|---|
| **Embrace (new)** | Numbers explode by realm. A PRIMORDIAL cultivator has HP/damage in the billions–trillions, one-shots lower-realm content via realm suppression ("Prime Yujiro walks in and everyone is instantly outclassed"), and weapons/gear/spirit-weapons grow without a low ceiling. Power-creep across realms is now a *feature*. |
| **Protect (kept)** | Silent corruption is still a defect. The XP `int` overflow (~L25), the mob `MAX_HEALTH` 1024 attribute clamp, the First-Aid `level×1500` accidental runaway, and any naked `×level` term are bugs and are fixed/banned. Within a single realm, fights stay *fair and readable in ratio* (gap=0 ⇒ suppression is inert; TTK ~3–6 s preserved). Magnitude inflates; the gameplay ratio is conserved. |

**The mechanical pivot:** keep the engine, delete the *caps-as-ceilings*, re-anchor every multiplier onto a single **Realm curve** (`power-curve` in `combat.yml`), and replace the two attribute-bound HP paths (player vanilla-health sync, mob `MAX_HEALTH`) with **double/`long`/`BigInteger`-backed virtual pools** that can legitimately hold trillions. Caps do not disappear — they are **re-targeted upward dramatically** and **re-purposed as overflow/sanity rails** (a per-realm ceiling set ~3× above intended, far below `double`'s 2⁵³ exact-integer limit), not as balance throttles.

---

### 2. Specific Overrides — Prior Decisions That Must Be Overturned

Every row below is a decision or number from `CERBERUS_DESIGN_PLAN.md` or `OPEN_DECISIONS.md` that the cultivation direction **overturns or revises**, with its replacement.

| # | Prior decision (source) | Status | Cultivation-scale replacement |
|---|---|---|---|
| O1 | **Player HP ceiling ~1,000 skills / ~1,500 gear** (Plan Pillar 3; OPEN P0 player-ceilings) | **OVERTURNED** | Per-realm HP budget `playerHP = HP_ANCHOR · realmMult(realm) · levelMult(L) · PLAYER_HP_FACTOR`. R1 ~200 → R10 (PRIMORDIAL) **~27 billion**, fully-grown/geared apex into the **trillions**. Hard ceiling becomes the per-realm sanity rail (PRIMORDIAL `1.0e14`), not a balance cap. |
| O2 | **6.0× absolute stacked-offense clamp** in `CombatSkillProcessor` (Plan Pillar 3; OPEN P0) | **REVISED** | The 6.0× becomes a **realm-relative budget**: `maxPlayerDamage = DMG_ANCHOR · realmMult(realm) · levelMult(L) · OFFENSE_BUDGET_CAP(6.0)`. Intra-realm stacking is still clamped to 6× the *realm baseline* (so same-realm balance survives), but the absolute number that represents scales from ~120 at COMMON to ~6 billion at PRIMORDIAL. |
| O3 | **Mob HP per-tier cap map (WEAK 200 … GODLY 20k … PRIMORDIAL ~35k)** (Plan Build Step 0; OPEN P0 mob HP-cap) | **OVERTURNED** | Replaced by the multiplicative realm curve `mobMaxHP = HP_ANCHOR · realmMult · levelMult` and a per-realm **virtual-pool ceiling** (PRIMORDIAL `1.0e14`). Rep. mob HP: RARE ~19K, GODLY ~840M, PRIMORDIAL ~6.7B; apex bosses ~330B–840B via `boss-hp-factor`. The old 35k is now a *mid-LEGENDARY* number. |
| O4 | **Flat 5,000 mob HP cap / "raise to ~40k"** (Plan Exec Summary, §1a; OPEN P0) | **OVERTURNED + REFRAMED AS A BUG** | The 5,000 cap was *always a fiction*: `Attribute.MAX_HEALTH` hard-caps at **1024** in MC, so `MobScalingListener` never produced >1024-HP mobs. Fix is mandatory regardless of direction: mobs get a **`MobVirtualHealthManager` double pool** (mirroring players); vanilla `MAX_HEALTH` set to a cosmetic 40 for the rendered bar; real HP in the pool + PDC `DOUBLE`. The empty `applyDamageMultiplierToEntity` hook (`VirtualHealthListener` line ~237) is implemented to drive the pool. |
| O5 | **Rule 5: "HP pools stay readable — hundreds, not hundreds-of-thousands"** (Plan §7) | **OVERTURNED** | Numbers are *meant* to reach billions/trillions. Legibility is delivered by a **`CultivationNumberFormat`** util (`1.2B`/`3.4T`/`9.1Qa`, or xianxia units *Qi/Marrow/Meridian/Pillar/Dao/Heaven/Primordium*), cached per-state and re-rendered only on bucket-crossing — not by keeping the underlying number small. |
| O6 | **Rule 7: tiers are a fixed ~1.35×/step ratio** (Plan §7; OPEN "Tier power ratio" 1.30 vs 1.40) | **OVERTURNED** | Tiers become **Realms** with a steep geometric step. Two candidate curves to ratify (one open decision, §4): Power-Scaling's `realm-base ≈ 8.0` (×8/realm), or Cultivation Realms' `6^(R-1)` with ×4/×8/×16 kinks at R8/R9/R10. Either way the per-realm jump is the "instantly outclassed" wall, not a 1.35× nudge. |
| O7 | **Rule 4: player power TRACKS (never outruns) the mob curve; TTK 3–6 s everywhere** (Plan §7) | **NARROWED, NOT KILLED** | TTK 3–6 s is preserved **only as a within-realm invariant** (gap=0). *Across* realms, tracking is deliberately broken: a higher-realm player one-shots lower-realm mobs (suppression), and a lower-realm player is a wall they can't dent. "Tracking" now means *same-realm* parity only. |
| O8 | **Rules 1, 2, 6: hard caps on everything / soft-cap curves / `stat=level×C` banned by policy** (Plan §7) | **PARTIALLY OVERTURNED** | Hard *ceilings-as-balance* are gone; hard *rails-as-overflow-safety* stay (per-realm pool ceiling, crit-chance ≤0.75, refine +20, spirit-level L100, resonance ≤1.0). The `stat=level×C` *ban* is **kept for accidental runaways** (First Aid) but **intentional, bounded** level terms are now allowed (spirit weapon `+1.2%/level`, gear temper, dao). The test is *intentional + bounded + representable*, not *linear-is-forbidden*. |
| O9 | **Endgame: "5,000-HP natural ceiling is the start of endgame"; power permanently caps at PRIMORDIAL** (Plan Exec §5, Pillar 8; OPEN Threat) | **REVISED** | Open-world power caps at **R10 / PRIMORDIAL** as the terminal *identity*, but that cap is now in the **trillions**, not 5,000. The infinite Threat tail (Depth Spiral / Abyssal Ladder) keeps "cosmetics/leaderboard only past T20" but its HP math moves to **`BigInteger`** (instanced-only, the sole place that exceeds `double`'s 2⁵³). `threatHP = 1.5^T` survives as the *shape*, re-anchored onto the realm curve. |
| O10 | **Mob damage cap 10×; "raise PRIMORDIAL tier-base to ~5.5 so the cap is approached"** (Plan §1a; OPEN P2) | **OVERTURNED** | The `max-damage-multiplier: 10.0` clamp is **deleted entirely** — mob damage is now `DMG_ANCHOR · realmMult · levelMult` + suppression, intentionally astronomical. The "raise tier-base" debate is moot. |
| O11 | **Weapon base-damage anchor: COMMON sword = 7.0, per-tier = 7.0×craftStatMultiplier** (OPEN P0 weapon-base) | **REVISED / SUPERSEDED** | Replaced by **Weapon Power (WP) realm anchors** (`weapons.yml: realm-power`): WEAK 5 → COMMON 10 → RARE 200 → MYTHIC 1.2M → PRIMORDIAL 1.5B. Per-hit = `realmWP · archetypeCoeff · refine · spirit · affix · skill · crit · element · suppression`. The 7.0 number is retired as the anchor. |
| O12 | **Per-item ceiling = tier-budget × 2.0; temper cap +3** (OPEN P1 item-ceiling) | **REVISED** | Item ceiling is re-expressed as a generous multiple of **realm** WP (overflow safety, not a throttle), with `BigInteger` promotion above `1.0e15` for refined-divine-vs-suppressed paths. Temper +3 **retained for v1** but flagged as an open decision vs. cultivation's "no ceiling" doctrine (uncapped-but-exponentially-costly alternative — §4). |
| O13 | **Reservoir Inversion: HP max "up to 5,000" dwarfs 1,000 mana** (OPEN P1; Plan §7 inversion) | **REVISED (assumption only)** | The "5,000 HP" framing is dead. SWAP-BY-FILL-PERCENTAGE is **kept and reinforced** (it is the *only* model that survives when HP is in the billions and mana is thousands — a raw-value swap is now catastrophically degenerate). The percentage model needs no number change; only its justification updates. |
| O14 | **First Aid: rewrite to ScalingFormula contributing "a few hundred HP"** (OPEN P0; Plan §7) | **REVISED** | First Aid's `level×1500` is still killed (accidental runaway). But its replacement contributes a **percentage of the per-realm HP budget**, not a flat "few hundred" — at PRIMORDIAL a few hundred HP is noise. Cap as `%-of-capped-budget`, where the budget is now realm-scaled (billions). |
| O15 | **"Hundreds not thousands" Virtual Health budget open decision** (Plan Open Decisions; OPEN Progression) | **OVERTURNED** | Resolved in favor of realm-scaled budget (O1). The open question is no longer "hundreds vs low-thousands" but "ratify `realm-base` 6.0 vs 8.0" (§4). |
| O16 | **Tier display = quality colors only; accessibility via tier-symbol** (OPEN "Accessibility" gap) | **EXTENDED** | Realms get **flavor names** layered over the unchanged `Tier` enum (Mortal/Body Tempering/…/Primordial, or Stirring/…/Primordial Echo). Accessibility gap is *widened* by trillions-magnitude HUD numbers — the `CultivationNumberFormat` + bucket-cache is now a hard requirement, and HUD legibility for `T`/`Qa` must be audited. |

---

### 3. What Still Holds — Prior Decisions That Survive Unchanged

These survive the doctrine change intact and remain authoritative:

| Area | Surviving decision | Source |
|---|---|---|
| **XP overflow fix** | Rewrite `100·2^(L-1)` `int` → hybrid polynomial `BASE·L^EXP + LINEAR·L` (BASE=75, EXP=1.85, LINEAR=40), `long` accumulator, ~19M cumulative to 100, precomputed `long[101]` table, unit-tested for monotonicity/no-overflow. **XP stays deliberately human-scaled** — skill level is an *exponent input*, not a magnitude source; the astronomical numbers come from realms/gear, not XP totals. | OPEN P0 XP; Power-Scaling §1.4 |
| **Distance-as-difficulty** | One radial level curve (150 blocks/level, L1–100), geography is the only difficulty UI, no region editor, `BandResolver` is a pure function of distance+biome+config. Cultivation *amplifies* this (realm suppression makes "you wandered too deep" a hard mechanical wall) rather than replacing it. | Plan Pillars 1–2 |
| **Families-as-data** | Five Echo Families (SAND/TIDE/ASH/FROST/MIRE) as `mobs.yml` config, O(1) `MobRegistry` resolution, tokens from same-family kills, family→biome theming. Realms map 1:1 onto the families' Bands (a Band *is* a realm's country). | Plan Pillars 1–2, Bestiary §2 |
| **Config-driven + Folia-safe** | New content ships through `mobs.yml`/`combat.yml`/`weapons.yml`/`gear*.yml` + existing managers; snapshot-on-region-thread → async I/O → reschedule; new tables auto-create; `entity.getScheduler()` / `teleportAsync` for cross-region. **No ProtocolLib.** | Plan Pillar 6 |
| **Registry-pattern reuse** | `WeaponRegistry` and `GearRegistry` mirror `MobRegistry` exactly (PDC-tag → name → pattern → fallback resolution, atomic reload-with-fallback, secondary indexes). | Weapons §9, Gear §3 |
| **Quality-enum unification** | Collapse `CraftingManager` + `QualityBuffs` duplicate Quality enums onto the single `Tier` ladder (`fromLegacyQuality` for migration only). The unified ladder is *more* load-bearing now (every realm multiplier derives from it). | OPEN P0 itemization |
| **3-layer defense pipeline** | raw → elemental resist → armor (≤40%, →55% only via set bonus) → defense bar → Virtual Health. Suppression and weapon/gear effects are **insertion points** in this pipeline, never a new layer. | Plan Pillar 5, Combat Loop §2 |
| **Repo hygiene / docs** | Commit staged work, delete the Creb/ nested repo, `git rm --cached target/`, update stale CLAUDE.md/AI_HANDOFF.md (Folia 1.21.11, region gone), add `schema_version` table + idempotent migrations, plan a clean v1 wipe. | OPEN "Build order", "Doc hygiene", "Save-data scope" |
| **EULA / monetization** | Cosmetic-only monetization; all soulbound/divine/spirit weapons and keyed gear are **strictly earned, never purchasable**. Soulbinding (`cerb_bound` PDC) is the EULA-safe earned gate. | OPEN "Monetization gap" |
| **Testing discipline** | Unit-test the riskiest pure-function math: rewritten XP curve, the realm `power-curve`, the `CultivationNumberFormat` formatter, and the defense-pipeline order — before wiring systems on top. | OPEN "Testing gap" |
| **Talisman boundary** | Talismans are a **separate system**. Gear reserves an unread `talisman-sockets` field but never reads it; no talisman slots/charges/crafting in Gear. | Gear §1, §8 |
| **Build/runtime + accessibility flags** | The `--enable-preview` JDK-21 pin audit and the colorblind/HUD-density concerns remain open and are *more* urgent given trillions-magnitude HUD output. | OPEN "Build fragility", "Accessibility" |

---

### 4. New Open Decisions Introduced by Weapons / Gear / Cultivation

These are **net-new** decisions the four sections surface (beyond the existing OPEN_DECISIONS checklist), needing a creator call. Defaults are recommended where the sections proposed one.

#### 4.1 Scaling-curve ratification (highest leverage — everything hangs on these)

| ID | Decision | Options / default |
|---|---|---|
| N1 | **The single `realm-base` value** — the slope of the entire fantasy. | Power-Scaling proposes **8.0** (×8/realm, uniform); Cultivation Realms proposes **6.0 with ×4/×8/×16 kinks at R8/R9/R10**; Weapons proposes a *non-uniform accelerating* WP curve (2×→43×). **These three are not yet mutually consistent — pick ONE master curve and derive WP/HP/gear from it.** Default: ratify Power-Scaling's `realm-base=8.0` as the spine and re-express the others as functions of it. |
| N2 | **Within-realm `level-slope`** (×5.95 over 100 levels @ 0.05). | Higher = more grind value per realm; lower = flatter plateaus. Default 0.05. |
| N3 | **`BigInteger` promotion threshold** (default `1.0e15`). | Confirm 10¹⁵, and that `BigInteger` is **forbidden** outside `cerb_depths` instances (never on a `CreatureSpawnEvent`/`EntityDamageEvent`/HUD tick). |
| N4 | **Per-realm sanity-ceiling map** (overflow rails). | Ratify `realm-health-ceiling` (PRIMORDIAL `1.0e14`, well under 2⁵³). Set ~3× intended max. |

#### 4.2 Suppression bounds

| ID | Decision | Default |
|---|---|---|
| N5 | **Suppression saturation gap** (`SUPP_MAX_GAP`). | Power-Scaling caps at +4; Cultivation Realms uses Δ≥+3 "Overwhelm"; Weapons goes to Δ≥+5 "one-shot floor". **Reconcile to one curve.** Default: saturate at +4, with a Δ≥+5 flat-%-of-max-HP floor for the divine/apex flavor. |
| N6 | **Suppression DR cap** (a lower realm is never *literally* immune). | `suppression-dr-cap = 0.99` so chip/"thousand cuts" group play survives. |
| N7 | **Suppression in PvP & same-faction dueling.** | Does Δ≥+3 Overwhelm apply intra-sect? Realm-bracketed full-loot (±1 realm only); ganking 3+ realms down = no loot + flags aggressor. Realm-Boost Elixir +1-realm-offense PvE-only vs PvP-allowed (pay-to-burst risk). |
| N8 | **Aura scope/perf.** | Only elites/bosses/players above a realm threshold project the aura; 1 Hz region-thread radius scan, downward-only (high→low). Confirm radius (12 blocks). |

#### 4.3 Counts, registries & extensibility

| ID | Decision | Default |
|---|---|---|
| N9 | **Weapon count = ~250**, of which **~36 hand-authored** (18 spirit + 12 divine + 6 family signatures) and ~210 generated (18 archetypes × realm × variant). Ratify the count split. | Accept ~245 catalog entries + seasonal slots. |
| N10 | **Gear count = ~250** = ~150 Wearables + ~100 Equipment & Tools, ~20 curated uniques. Ratify the branch split and the **12-slot** default equip layout. | Accept; slots are config (`gear-slots.yml`) so additions are free. |
| N11 | **Gear is open-ended via a data-driven Gear-Type Registry** (`gear-slots.yml` / `gear-types.yml` / `gear-items.yml`), `GearBehavior` handlers keyed by `function.type`. Confirm the schema (branch / slot / activation / governing-skill / stat-schema / affix-slots) and that **adding a slot or type is config-only**, new *behavior* = one handler class. | Accept the registry schema; ratify the `function.type` behavior set (HARVEST, ITEM_MAGNET, EXPLOSIVE, BUFF_AURA, DEPLOYABLE…). |
| N12 | **Heavy Armor Training as a weapon class** for blunt weapons, or introduce a thin "Blunt Mastery" sub-key? | Default: blunt weapons level HAT (config decision, not a rewrite). |
| N13 | **Realm count = 10**, 1:1 with the tier ladder. Confirm flavor-name set (Mortal…Primordial vs Stirring…Primordial Echo — two competing name tables across the sections; pick one). | **RESOLVED (2026-06-08): Western-forward names canonical — Mortal · Awakened · Adept · Champion · Ascendant · Paragon · Transcendent · Demigod · Empyrean · Primordial (see [NAMING.md](NAMING.md)). Cultivation jargon retired from player-facing names.** |

#### 4.4 Cultivation growth & gates

| ID | Decision | Default |
|---|---|---|
| N14 | **Spirit-weapon growth bounds** (refine +20 ≈2.2×, spirit-level L100 ≈2.2×, resonance ≤1.0 ≈1.5×, total ≈7× over realm anchor) and **XP-share %** to the weapon; do multiple spirit weapons level in parallel or only the equipped one? | Default: equipped-only, small fraction of class-skill XP. |
| N15 | **Divine Artifact scarcity** — single-instance-per-server (true flex, drives PvP-for-the-artifact) vs rare-but-multiple. | All soulbound/earned; lean single-instance for the apex 12. |
| N16 | **Temper realm cap** — keep +3 (overturns nothing but contradicts "no ceiling") vs uncapped-but-exponentially-costly. | Open; default +3 for v1, revisit. |
| N17 | **Cultivation stage/bottleneck/tribulation model** — 4 minor stages + Lesser/Greater bottlenecks, tribulations reuse the boss system, heavenly strikes ignore armor/bar (softened by dao/pills), failure stakes (drop a stage + entropy spike). Confirm failure severity (per-server difficulty toggle?). | Accept defaults; add difficulty toggle for small servers. |
| N18 | **Cultivation persistence** — new `player_cultivation` table (realm, minor_stage, qi as BigInteger-TEXT, dao_points, physique_id, sect_id, attunement_json) and `player_spirit_weapon`, `player_gear` tables, auto-created + versioned. | Accept; first features to ship a versioned migration; clean v1 wipe is fine. |
| N19 | **Can you cultivate past R10 / PRIMORDIAL?** | Recommend **no realm past Primordial** — route infinite scaling into the Threat-tier Depth Spiral (cosmetics/leaderboard). R10 = terminal identity, not terminal challenge. |
| N20 | **Merge Dao Comprehension with the old "Primordial Ascension / Echo Resonance" prestige** into one bounded-infinite axis? | Recommend yes — one prestige system, not two overlapping. |

---

### 5. Updated Build-Order Note

The existing **9-step build order (Steps 0–8)** survives as the skeleton; the cultivation work threads into it rather than replacing it. Net changes:

**Step 0 (Hygiene + HP-cap fix) — RE-SCOPED.** The "per-tier health-cap map (WEAK 200 … PRIMORDIAL 35k)" deliverable is **replaced** by: (a) the mandatory `Attribute.MAX_HEALTH` 1024 fix → stand up **`MobVirtualHealthManager`** + implement the empty `applyDamageMultiplierToEntity` hook; (b) author the **`power-curve`** config block (realm-base, anchors, level-slope, per-realm ceilings) and rewrite `MobHealthScaling.calculate` to the multiplicative realm curve; (c) delete the dead `health-cap: 5000` and `max-damage-multiplier: 10` keys. Repo hygiene (commit staged work, kill Creb/ nested repo, `git rm --cached target/`, fix stale docs, add `schema_version`) is unchanged and still first. This remains *the* unblocking step — every other system reads `power-curve`.

**Step 1 (Number lock-down) — RE-TARGETED.** The "shared-constants treaty" still happens in one place, but the constants are the cultivation set, not the conservative set: ratify N1–N7 (realm-base, level-slope, suppression bounds, BigInteger threshold, per-realm ceilings) and the `realm-power` WP anchors. The old constants (1,500 HP, 6.0× absolute, 7.0 sword base) are entered as their *overturned* values per §2.

**Step 2 (Progression spine) — KEPT, with one addition.** XP-curve rewrite + skill audit + First-Aid kill proceed exactly as planned (these are the *surviving* anti-bug fixes). **Add:** the `CultivationNumberFormat` utility (pure, unit-tested) lands here, since the HUD/lore/floaty-numbers all depend on it once numbers go astronomical.

**Step 3 (Bestiary/Families) — KEPT.** Mobs.yml authoring is unchanged; mobs now read `power-curve` for their realm-scaled virtual HP and carry a realm = their Band's realm (drives their suppression aura).

**Step 4 (ZoneManager/BandResolver) — KEPT, extended.** Bands now double as **realm country**; the floor-tier rule becomes a floor-*realm* rule, and the resolver feeds the suppression `realmIndex(attacker) − realmIndex(defender)` calculation.

**Step 5 (Weapons & Gear) — EXPANDED into the largest authoring surface.** Now delivers: `WeaponRegistry` (18 archetypes × realm × variant → ~250), the affix/reforge/temper/awaken paths, **Spirit Weapons + Divine Artifacts** (growth + soulbind), the **`GearRegistry`** (data-driven slots/types/items, two branches, ~250), set bonuses, and **realm suppression** wired into the 3-layer pipeline via `Tier.armorPenetration` + defense-bar insertion points. This is where the bulk of cultivation content lives.

**Step 6 (Signature systems) — KEPT, with Reservoir Inversion reinforced.** SWAP-BY-FILL-PERCENTAGE is now mandatory (raw-value swap is degenerate at billions HP). Elemental reactions, Defensive Magic, traps, Sea Monsters unchanged.

**Step 6.5 (NEW) — Cultivation Realm & Ascension layer.** Inserted between signature systems and economy: minor stages + bottlenecks, Breakthroughs/Tribulations (reusing the boss system), Pills/Alchemy advancement economy, Techniques/Manuals, Physiques, sects-as-families, and the `player_cultivation`/`player_spirit_weapon`/`player_gear` tables. This is the identity layer that sits *above* tiers/Bands/skills and depends on all of Steps 3–6.

**Step 7 (Blink Gates + economy) — KEPT, re-gated.** Gate USE-gating becomes **realm-gating** (Maw Gate requires R9-eligibility, Primordium Gate requires a Nomicon/R10-track) — the same anti-"ferried to Primordium at level 5" rail, now thematically grounded. Spirit Stones alias/replace Marrow.

**Step 8 (Endgame/PvP/seasons) — KEPT, with `BigInteger` Threat.** Depth Spiral / Abyssal Ladder Threat HP moves to instanced-only `BigInteger`; power still caps at PRIMORDIAL (R10) but at a trillions magnitude; realm-bracketed full-loot PvP per N7.

**One-line dependency summary:** *Hygiene + virtual-mob-HP + `power-curve` (0) → ratify cultivation constants (1) → sane XP + number formatter (2) → mobs/families read the curve (3) → zones feed realm-gap (4) → weapons/gear/suppression, the big build (5) → signature verbs (6) → cultivation realms/ascension (6.5) → realm-gated gates/economy (7) → BigInteger Threat endgame (8).*
