# Cerberus — Weapons System

*Generated 2026-06-08 (cultivation/xianxia design pass). Companion to the [master design plan](CERBERUS_DESIGN_PLAN.md). Reflects the **controlled-hyperscaling** doctrine — see [CULTIVATION_INTEGRATION.md](CULTIVATION_INTEGRATION.md) for what this overturns from the original plan.*

---

## Weapons System

> **Scope & doctrine.** This section designs a weapon system that reaches **~250 distinct, meaningfully-differentiated weapons** without hand-authoring 250 uniques. It is built **on the live engine** — the 10-tier ladder (`Tier.craftStatMultiplier` 0.5→4.0, `armorPenetration` 0.0→0.8), the 7 Combat skills, the 3-layer defense pipeline (raw → elemental resist → armor ≤40% → defense bar → Virtual Health), `ElementalTag`, and the config-driven registry pattern — and it embraces the **new cultivation hyperscale**: numbers grow from hundreds to **trillions+**, weapons gain power tied to *realm*, spirit weapons grow with their wielder, and soulbound divine artifacts at the top enable peak-cultivation-protagonist dominance. The only things kept caps for are **literal bugs** (overflow, accidental `×level` runaway). All scaling here is **intentional, tunable, and representable** (long → `BigInteger`, formatted `1.2B`/`3.4T` / xianxia units).

---

### 1. The Realm Magnitude Ladder (the number the whole system hangs on)

Cultivation power is not a flat curve — it is a **staircase of magnitudes**. The existing 10 tiers are re-cast as **Realms**, and each Realm is assigned an **order-of-magnitude Weapon Power (WP) anchor**. Every weapon's damage is expressed as `WP × per-hit-coefficient`, and WP is what explodes by realm. This keeps the *engine math* simple (multiply two doubles/longs) while the *displayed numbers* become absurd.

| Realm (Tier) | Cultivation name | `craftStat` (live) | **Realm WP anchor** | Magnitude of a hit | Display band |
|---|---|---|---|---|---|
| WEAK | Mortal | 0.5 | **5** | single digits | 5 |
| COMMON | Body Tempering | 1.0 | **10** | tens | 10 |
| UNCOMMON | Qi Condensation | 1.25 | **45** | tens–hundreds | 45 |
| RARE | Foundation Establishment | 1.5 | **200** | hundreds | 200 |
| EPIC | Core Formation | 1.75 | **1,200** | thousands | 1.2K |
| LEGENDARY | Nascent Soul | 2.0 | **9,000** | tens of thousands | 9K |
| PERFECT | Soul Transformation | 2.5 | **80,000** | hundred-thousands | 80K |
| MYTHIC | Spirit Severing | 3.0 | **1,200,000** | millions | 1.2M |
| GODLY | Dao Seeking | 3.5 | **35,000,000** | tens of millions | 35M |
| PRIMORDIAL | Immortal Ascension | 4.0 | **1,500,000,000** | billions | 1.5B |

**Inter-realm ratio is intentionally non-uniform and *accelerating*** (≈2× → 4.5× → 6× early, ballooning to ~22×/43× at the top): this is the cultivation "realm gap." `WP(realm)` lives in **one** config block (`weapons.yml: realm-power`) so the entire economy is tunable from a single curve.

```yaml
# weapons.yml — the single source of truth for the power curve
realm-power:           # Weapon Power anchor per realm (the "magnitude")
  WEAK: 5
  COMMON: 10
  UNCOMMON: 45
  RARE: 200
  EPIC: 1200
  LEGENDARY: 9000
  PERFECT: 80000
  MYTHIC: 1200000
  GODLY: 35000000
  PRIMORDIAL: 1500000000
overflow-policy: BIGINT_ABOVE: 1.0e15   # promote to BigInteger past quadrillion (refined/awakened)
```

**Realm Suppression** (the signature cultivation rule, see §7) is what makes this ladder *felt*: a weapon two+ realms above its target doesn't just hit harder — it **partially ignores the victim's defense layers entirely**.

---

### 2. Base Weapon Archetypes (mapped to the 7 Combat skills)

Weapons are **not** a flat list — every weapon has an **archetype** (its identity/moveset) and a **class** (which Combat skill it levels and is multiplied by). Archetypes are the generative *rows*; realms × variants are the *columns* (§3).

| # | Archetype | Class skill | Hands | Speed | WP coeff (per-hit ×) | Defense-layer bias | Signature moveset trait |
|---|---|---|---|---|---|---|---|
| 1 | **Jian** (straight-sword) | Blade Mastery | 1H | Fast | 1.00 | balanced | Combo-extender: each combo step +4% next hit |
| 2 | **Dao** (saber) | Blade Mastery | 1H | Med | 1.25 | armor-favoring | Cleave arc: 60% splash to 1 extra target |
| 3 | **Greatsword / Zhanmadao** | Weapon Mastery | 2H | Slow | 2.10 | defense-bar-shred | Charged heavy: 2.5× on held swing |
| 4 | **Qiang / Spear & Polearm** | Weapon Mastery | 2H | Med | 1.55 | armor-pierce | Reach 1.5 blocks; lunge gap-closer |
| 5 | **Staff / Gun (cudgel)** | Martial Expertise | 2H | Fast | 1.10 | defense-bar-shred | Flurry: 3-hit micro-combo, knockback finisher |
| 6 | **Hammer / Maul (blunt)** | Heavy Armor Training\* | 2H | V.Slow | 2.60 | defense-bar-breaker | Ignores 25% of defense-bar absorption; stun on crit |
| 7 | **Axe / Battleaxe** | Weapon Mastery | 1H/2H | Slow | 1.80 | armor-shred | Applies "Sundered" (−10% target armor, 4s) |
| 8 | **Bow** | Ranged Precision | 2H | Med | 1.15 | falloff/crit | Draw-charge; Ranged Precision falloff & crit |
| 9 | **Crossbow** | Ranged Precision | 2H | Slow | 1.60 | armor-pierce | High flat pierce, slow reload, no falloff |
| 10 | **Thrown (chakram/dart/javelin)** | Ranged Precision | 1H | Fast | 0.90 | balanced | Returns/recovers; cheap to spam, low per-hit |
| 11 | **War-Fan** | Martial Expertise | 1H | Fast | 0.95 | elemental-favoring | On-hit elemental gust; deflect (parry window) |
| 12 | **Whip / Soft Weapon (chain, rope-dart)** | Martial Expertise | 1H | Fast | 1.05 | armor-bypass | Long reach, multi-target lash, disarm chance |
| 13 | **Fists / Gauntlets (martial)** | Martial Expertise | unarmed/1H | V.Fast | 0.75 | defense-bar-shred | Highest combo scaling; per-stack +6% (cap 10) |
| 14 | **Dual-Wield set (paired blades/claws)** | Dual Wielding | 2×1H | V.Fast | 0.70 ea | balanced | Two independent swings; offhand procs |
| 15 | **Flying Sword (yujian)** ✦exotic | Blade Mastery + Magic | summon | Auto | 1.40 | armor-bypass | Detaches, orbits & auto-strikes; scales with mana |
| 16 | **Spirit Whip / Soul Lash** ✦exotic | Martial Expertise + Magic | 1H | Fast | 1.10 | defense-bar-bypass | Hits Virtual Health directly on charged lash |
| 17 | **Talisman-Blade / Sabre-of-Cinders** ✦exotic | Blade Mastery + Magic | 1H | Med | 1.20 | elemental-burst | Stores 3 elemental "talisman charges," releases on combo |
| 18 | **Cauldron-Hammer / Dao-Furnace** ✦exotic | Heavy Armor Training\* | 2H | V.Slow | 2.80 | defense-bar-breaker | Builds "Heat"; overheated swing = AoE detonation |

> \* **Heavy Armor Training** currently has no weapon class. Blunt weapons (Hammer/Cauldron) are the natural home: a bruiser identity where defensive mastery doubles as the weapon-class skill. (Alternative: introduce a thin **"Blunt Mastery"** sub-key — a config decision, not a rewrite.)

> ✦ **Exotic cultivation weapons** (15–18) gate behind realm + a Magic skill and are the bridge into spirit weapons (§5). A Flying Sword's auto-strike damage = `WP × 1.40 × (1 + manaScaling)`, so it rewards hybrid Combat/Magic builds without breaking the melee budget.

---

### 3. The Generative Model (how 18 archetypes become 250+)

The catalog is **archetype × realm × variant**, resolved at item-generation time from data tables — never hand-stamped. Three orthogonal variant axes generate breadth:

**Axis A — Material/Realm body** (`material-tier`): the physical substrate. Realm-gated. Bog Iron (WEAK) → Spirit Steel (RARE) → Star-Iron (PERFECT) → Dao-Forged Empyrean (PRIMORDIAL). The material *sets the base realm WP* and a small intrinsic flavor (e.g. Star-Iron: +crit; Abyssal Bronze: +defense-bar-shred).

**Axis B — Elemental Infusion** (reuses `ElementalTag`: Fire/Ice/Lightning/Earth/Water/Void): turns "Jian" into "Flamewreath Jian," "Frostbite Jian," etc. Infusion picks the on-hit status (burn/chill/shock/quake/drench/erode) and ties into the element counter-wheel vs the 5 Echo Families.

**Axis C — Affix roll** (1–4 affixes from a weighted pool, count scales with realm): the Diablo/Borderlands layer. Affixes change *behavior and numbers*, not just stats — this is what keeps two same-realm Jians distinct.

A generated weapon's identity = `<Affix prefix> <Element> <Material> <Archetype> <Affix suffix>` → e.g. **"Keening Frostbite Star-Iron Jian of the Severing."** A `weaponId` is the deterministic hash of `(archetype, materialTier, element, affixSet)` so it resolves O(1) from the registry exactly like `MobRegistry`.

#### 3a. Concrete breakdown that sums to ~250

The trick: **most archetypes are not authored at every realm** — they appear at the realms where they're thematically/mechanically interesting, and a curated divine tier caps the top. Counting **named, catalog-distinct weapon entries** (an entry = archetype@realm with a *flavor pre-build*; the affix/element rolls then explode each into many runtime instances but aren't counted as separate catalog entries):

| Source | Math | Count |
|---|---|---|
| **Core melee archetypes** (Jian, Dao, Greatsword, Qiang, Staff, Hammer, Axe, Fists, Whip, War-Fan = 10) × **9 realms** each (WEAK→GODLY; PRIMORDIAL handled by divine tier) | 10 × 9 | **90** |
| **Ranged archetypes** (Bow, Crossbow, Thrown = 3) × **8 realms** (UNCOMMON→PRIMORDIAL) | 3 × 8 | **24** |
| **Dual-Wield paired sets** (claw, twin-jian, twin-dao, hook-swords, butterfly) × **5 realms** (RARE→GODLY) | 5 × 5 | **25** |
| **Exotic cultivation weapons** (Flying Sword, Spirit Whip, Talisman-Blade, Cauldron-Hammer = 4) × **6 realms** (EPIC→PRIMORDIAL) | 4 × 6 | **24** |
| **Elemental signature pre-builds** (one curated element-locked showcase per element × a spread of archetypes — e.g. "Voidfang Dao," "Tempest Qiang") | 6 elements × ~7 | **42** |
| **Curated hand-named Spirit Weapons** (growable, soulbound-on-awaken; §5) | authored | **18** |
| **Curated Divine Artifacts** (PRIMORDIAL+ apex, soulbound; §6) | authored | **12** |
| **Boss-keyed family signature weapons** (one per Echo Family, MYTHIC/GODLY drop-only) | SAND/TIDE/ASH/FROST/MIRE +1 | **6** |
| **— TOTAL catalog entries —** | | **≈ 245** |

A handful of seasonal/event slots (+5–10) trivially crosses **250+**. Crucially, only **36 entries are hand-authored** (18 spirit + 12 divine + 6 family signatures); the other **~210 are generated** from the 18 archetypes × realm × variant tables. **A solo dev authors 36 weapons and a half-dozen data tables, and the engine produces 250+.**

> **Differentiation guarantee:** the generated 210 are not "the same sword with a bigger number." Each carries (a) an **archetype moveset trait** (§2), (b) one of **6 elemental on-hit grammars**, (c) a **material flavor**, and (d) **1–4 affixes** drawn from a ~40-entry pool (§4). Two RARE Jians can play completely differently — one a `Keen/Vampiric Flame` combo-extender, the other a `Heavy/Sundering Earth` armor-cracker.

---

### 4. Affix System (the cheap-but-deep differentiation engine)

Affixes are stored in the item's PDC (matching the existing affix pattern) and rolled from a weighted pool. **Affix count by realm**: WEAK–COMMON 0–1, UNCOMMON–RARE 1–2, EPIC–LEGENDARY 2–3, PERFECT–MYTHIC 3, GODLY–PRIMORDIAL 4 (+1 "prime affix" reserved for awakened/divine). Affixes are categorized so the roller guarantees variety (max 1 per category except via reforge).

| Category | Example affixes | Effect (anchored to budget, not flat ×level) |
|---|---|---|
| **Damage-flat** | Cruel, Savage, Annihilating | +X% of WP coefficient (Cruel +8%, Savage +15%, Annihilating +30%) |
| **Crit** | Keen, Murderous, Executioner | Keen +crit-chance (adds **before** the single Critical Strike roll, sum hard-capped 0.75 so 100% crit stays impossible); Executioner +damage vs targets <20% HP |
| **Defense-interaction** | Sundering, Bar-Render, Soulpiercing | Sundering: +armor-shred; Bar-Render: +% damage to defense bar; **Soulpiercing: ignores X% of all layers → straight to Virtual Health** (rare, the cultivation "true damage") |
| **Element-synergy** | Conflagrant, Stormcaller, Voidbiting | Amplifies the weapon's elemental status (longer DoT, +reaction damage on the Wet/Brittle/Sundered combos) |
| **Sustain** | Vampiric, Qi-Drinking | Lifesteal (feeds Virtual Health) / Mana-leech (feeds the caster economy) |
| **Tempo** | Swift, Frenzied, Relentless | Attack-speed / combo-decay-resist / cooldown-reduction |
| **Realm-suppression** | **Overawing, Heaven-Defying** | Boosts the realm-gap suppression coefficient (§7) — lets a weapon "punch above its realm" |
| **Spirit (prime)** | Awakening, Soul-Bonded, Dao-Resonant | Only on GODLY+/spirit/divine: unlocks growth, soulbinding, and the active "Domain" skill |

**Reforge / Temper / Awaken** (the player-facing power paths, all clamped to the budget so they can't exploit-stack past the realm's representable ceiling):
- **Reforge** — re-roll the affix set within the current realm (currency sink; keeps chase alive without raising realm).
- **Temper** — raise the *material body* up to **+3 realms** above craft realm (tier-shard cost per step), lifting the WP anchor itself. This is the primary "make my favorite weapon endgame-viable" path.
- **Awaken** — converts a GODLY+ weapon into a **Spirit Weapon** (§5): adds the prime affix slot, the growth track, and soulbinding.

---

### 5. Weapon "Cultivation": Refinement, Awakening & Spirit Weapons that grow with you

The cultivation fantasy demands weapons that *grow*, not just weapons you replace. Three growth mechanisms layer on top of the static catalog:

**(1) Refinement (every weapon).** A `+0 … +20` refinement track. Each `+` multiplies WP by `1 + 0.06·n` (so +20 ≈ 2.2×). Fed by **Whetstones/Ore-essence** (gathering sink). Pure linear-with-a-cap — cheap, universal, and safe (bounded at +20).

**(2) Spirit Weapon binding (GODLY+ Awakened).** A Spirit Weapon has its **own level (1–100)** and gains a tiny share of the XP its wielder earns in the weapon's class skill. Its WP is dynamic:

```
spiritWP = realmWP(weaponRealm)
         × (1 + 0.06·refine)
         × (1 + 0.012·spiritLevel)        # +1.2%/level → +120% at L100, INTENTIONAL
         × affixMultiplier
         × (1 + 0.5·resonance)            # bond strength with its specific wielder
```

A Spirit Weapon that travels the whole journey with a player ends **~2.2× (refine) × 2.2× (level) × 1.5 (bond) ≈ 7×** over its base realm anchor — enough to keep a beloved weapon relevant one full realm up, embodying "my sword grew with me." It **remembers** its wielder (resonance decays if abandoned), can be **named by the player**, and at high bond unlocks a **Domain skill** (an active: e.g. a flying sword's "Ten-Thousand Sword Rain," a spirit whip's "Soul-Severing Coil").

**(3) Soulbinding.** Spirit and Divine weapons bind on awaken (`cerb_bound` PDC). A bound weapon **cannot be dropped on death** (returns as a recoverable shard), can't be traded, and is the anchor of identity. This is also the EULA-safe gate: bound = strictly earned.

| Growth axis | Who has it | Bounded by | Power contribution |
|---|---|---|---|
| Refinement +0–20 | all weapons | +20 hard stop | up to 2.2× |
| Spirit level 1–100 | GODLY+ awakened | level 100 | up to 2.2× |
| Resonance/bond | spirit/divine | 1.0 cap | up to 1.5× |
| Awakening prime affix | spirit/divine | 1 slot | unlocks Domain active |

---

### 6. Divine Artifacts (the top-end "Prime Yujiro walks in" weapons)

The terminal **12 curated Divine Artifacts** sit at PRIMORDIAL+ and exist to deliver the peak-cultivation-protagonist power fantasy. They are **soulbound, un-craftable, single-instance-per-server-or-rare**, drop only from the apex content (Cerberus, the Echo-Eater; the Abyssal Ladder; Depth Spiral apex), and carry **multiplicative WP** that pushes into **trillions** via `BigInteger` once refined + awakened.

| Divine Artifact | Archetype | Base WP basis | Soul effect (the "aura") |
|---|---|---|---|
| **Tian'e — The Heaven-Severing Edge** | Jian | 3.0 × PRIMORDIAL WP | Each kill permanently +0.01% WP (tracked, displayed) — the blade that "remembers every life it ends" |
| **Wúliàng — Limitless Coil** | Spirit Whip | 2.5 × | Charged lash deals **pure Virtual-Health damage** ignoring all 3 layers |
| **The Ten-Thousand-Sword Sovereign** | Flying Sword (set of 10) | 2.2 × (×10 swords) | Summons 10 auto-orbiting blades; realm suppression applied per blade |
| **Bāhuāng — Furnace of Eight Wastes** | Cauldron-Hammer | 3.2 × | Overheat detonation scales with target's *max* HP (anti-sponge) |
| **Yuán — The First Spear** | Qiang | 2.6 × | Lunge ignores 100% armor layer; pierces through enemies in a line |
| **Mángmáng — The Endless Dao** | Dao | 2.4 × | Cleave splash uncapped target count; combo never decays |
| ... (6 more authored: Bow "Sunshatter," Gauntlets "Mountain-Splitting Fists," War-Fan "Nine Heavens Gale," Crossbow "Star-Piercer," Twin-Blades "Yin-Yang Severance," Staff "Pillar of the World Axis") | various | 2.0–3.0 × | each a distinct apex Domain ultimate |

**Realm Suppression Aura (divine signature):** wielding a Divine Artifact projects a passive aura — enemies **two+ realms below you take +X% and deal −Y%** in your presence (the "everyone is instantly outclassed" effect), capped per §7 so it can't trivialize *same-realm* content. This is the literal "Yujiro walks into the room" mechanic, implemented as a defense-pipeline modifier, not a one-shot.

---

### 7. Realm Suppression & how weapons interact with the 3-Layer Defense

This is the cultivation system's teeth — and it's implemented as **insertion points in the existing 3-layer pipeline** (raw → elemental resist → armor ≤40% → defense bar → Virtual Health), not a new engine.

**Realm-gap suppression coefficient** (`Δ = attackerRealm − targetRealm`, where realm index is the Tier ordinal):

| Δ (realms above target) | Layer-bypass granted to the attacker | Effect |
|---|---|---|
| ≤ 0 | none | normal 3-layer math applies fully |
| +1 | armor-pen += 25% | chips the armor layer |
| +2 | armor-pen += 50%, +20% to defense-bar damage | bar melts faster |
| +3 | armor layer ignored, defense bar ignored 50% | hits start reaching HP |
| +4 | armor + bar both ignored | **straight to Virtual Health** |
| ≥ +5 | + flat damage floor = % of target max HP | **realm-suppressed: effectively unkillable-by-them / one-shot-them** |

This reuses the existing `Tier.armorPenetration` lever (already 0.0→0.8 per tier) — suppression *adds* to it — and the defense-bar's `max-absorption`. **Tunable in one block** (`weapons.yml: realm-suppression`). It is symmetric: a low-realm player against a high-realm mob is *correctly* suppressed, which is exactly how the distance-is-difficulty world should feel ("you wandered too deep").

**How weapon classes engage each layer** (keeps 250 weapons differentiated *defensively*, not just by raw WP):

| Weapon class | Primary layer it beats | Why it matters vs cultivation defenses |
|---|---|---|
| Spears/Crossbows (armor-pierce) | **Armor** (Layer 2) | best vs heavily-armored cultivators/tanks |
| Hammers/Cauldrons/Staves (bar-breaker) | **Defense bar** (Layer 3) | best vs shield/bar-stacking defensive builds |
| Whips/Spirit Whips/Soulpiercing affix | **Virtual Health** (bypass) | "true damage" — best vs absurd-HP sponges, the anti-spike answer |
| Elemental weapons | **Elemental resist** (Layer 1) via the counter-wheel | exploit Fire↔Frost, Lightning↔Wet etc. for +30–50% |
| Dual-Wield/Fists (combo) | scales **over time** | best sustained DPS; weak burst — the "duelist" answer |
| Bows/Thrown (ranged crit) | **burst from range** | safest vs telegraph bosses; falloff is the cost |

No single weapon beats every layer — **the answer to a defense profile is a weapon class**, which is what makes choosing among 250 a *build* decision.

---

### 8. Damage Budget anchored to the cultivation curve

Worked example, anchoring "what a hit looks like" against the live mob-HP curve **once it's re-targeted upward** (the conservative 5,000 cap is replaced by a per-realm HP cap that scales into the billions — handled in the Mob/HP domain; weapons assume the upward re-target). Target **TTK ≈ 4–6s** vs a *same-realm* level-matched mob; **realm suppression** turns lower-realm targets into one-shots and higher-realm targets into walls.

**Per-hit damage** = `realmWP × archetypeCoeff × (1+refine) × spiritMult × affixMult × skillMult × critMult × elementMult × suppressionMult`, where `skillMult` is the existing **Combat skill stack** (Weapon Mastery damage-multiplier, Blade Mastery bonus, Critical Strike, etc., resolved through `CombatSkillProcessor`).

| Realm | RealmWP | Sample weapon (mid roll) | Per-hit (skilled, no crit) | Crit hit | Same-realm mob HP (re-targeted) | ~TTK |
|---|---|---|---|---|---|---|
| COMMON | 10 | Iron Jian | ~18 | ~40 | ~600 | 5–6s |
| RARE | 200 | Spirit-Steel Dao | ~520 | ~1.3K | ~12K | 4–5s |
| EPIC | 1.2K | Flame Qiang +5 | ~4.1K | ~10K | ~90K | 4–5s |
| LEGENDARY | 9K | Frostbite Greatsword | ~38K | ~95K | ~700K | 4–5s |
| MYTHIC | 1.2M | Voidfang Flying Sword | ~6.2M | ~16M | ~110M | 4–6s |
| GODLY | 35M | Awakened Spirit Dao (L60, +12) | ~410M | ~1.0B | ~9B | 4–6s |
| PRIMORDIAL | 1.5B | Tian'e (Divine, awakened) | ~28B | ~80B | ~600B | 4–6s |
| PRIMORDIAL+ refined apex | 1.5B× | Tian'e fully grown vs lower realm | **trillions** (one-shot via suppression) | — | lower-realm = instant | <1s |

Numbers are **displayed formatted** (`6.2M`, `1.0B`, `28B`) and via optional xianxia flavor units ("28 billion Sword-Qi"). Internally: `long` suffices to ~9.2×10¹⁸ (covers everything up to fully-grown divine vs same-realm); the **`overflow-policy` promotes to `BigInteger`** only for the rare refined-divine-vs-suppressed-target trillions+ path — which is the *only* place the old code would have overflowed. Virtual Health already holds gigantic doubles, and mob HP must move to a long/double virtual pool too (cross-ref Mob domain) so these hits land on a representable target.

**Anti-bug guards retained (and only these):**
- XP/refine/level terms are **bounded** (`+20` refine, `L100` spirit, capped resonance) — no naked `×level` runaway (the First-Aid class of bug).
- Crit affixes **add before** the single roll, sum **hard-capped 0.75** — 100% crit impossible.
- Reforge/temper/affix stacking **clamped to the realm's representable budget** at resolve time (a generous multiple of realm WP, not a balance throttle — purely overflow safety).
- `BigInteger` promotion above 10¹⁵ so the genuinely-trillions endgame never wraps to negative.

---

### 9. Implementation map (build ON the existing engine)

| Need | Reuse / minimal add |
|---|---|
| O(1) weapon resolution | **`WeaponRegistry`** mirroring `MobRegistry` — `weapons.yml` with archetype/material/element/affix tables; `weaponId` = deterministic hash |
| Open-ended weapon **types** | Data-driven **archetype registry** (like the gear-type registry the creator wants) — new archetypes are config rows, not code |
| Per-hit math | Hook `CombatConfig`/`CombatSkillProcessor`; weapon supplies `realmWP × coeff`, existing skill stack applies |
| Realm tiers | Existing `Tier` enum + `craftStatMultiplier`; add `realm-power` map (new `weapons.yml` block) |
| Realm suppression | Adds to existing `Tier.armorPenetration` + defense-bar absorption at pipeline insertion points |
| Elements & counter-wheel | Existing `ElementalTag` (Fire/Ice/Lightning/Earth/Water/Void) + the §7 family wheel |
| Affixes / bound / refine / spirit-level | Existing **PDC** tagging pattern; Spirit/Divine state persisted via `AsyncSaveManager` (new `player_spirit_weapon` table, auto-create like the existing five) |
| Huge numbers | `long` default, `BigInteger` above 10¹⁵, formatted display (`1.2B`/`3.4T`); Virtual Health & mob virtual-HP already double/long-based |
| Folia safety | All weapon math is synchronous per-hit on the region thread; flying-sword summons stay in the wielder's region (`entity.getScheduler()` only if they cross) |

---

### 10. Open decisions this section surfaces

- **Heavy Armor Training as a weapon class?** Confirm blunt weapons level HAT, or introduce a thin "Blunt Mastery" key. Affects skill-to-weapon mapping completeness.
- **Realm-power curve exact values** — ratify the `realm-power` map (the single most load-bearing number set here). The proposed accelerating curve (2×→43×) is tunable; pick the inter-realm gap that matches the desired "realm wall" feel.
- **Suppression cap vs same-realm content** — confirm suppression only bites at Δ≥+1 and never makes *same-realm* fights trivial (otherwise the whole TTK target collapses).
- **Spirit-weapon XP share %** (proposed weapon gains a fraction of class-skill XP) and whether multiple spirit weapons level in parallel or only the equipped one.
- **Divine Artifact scarcity** — single-instance-per-server (true cultivation flex, drives PvP-for-the-artifact) vs rare-but-multiple. EULA: all soulbound/earned, never purchasable.
- **`BigInteger` promotion threshold** — confirm 10¹⁵, and audit display formatting + HUD legibility for trillions+ (cross-ref the accessibility gap).
- **Temper realm cap (+3)** vs cultivation "no ceiling" doctrine — is +3 enough, or should temper be uncapped-but-exponentially-costly to let any weapon chase the top realm?

---

Source files referenced for grounding (all absolute):
- `C:\Users\Gabri\IdeaProjects\Cerb\docs\design\CERBERUS_DESIGN_PLAN.md` (tier multipliers, 3-layer pipeline, Echo Families, distance curve)
- `C:\Users\Gabri\IdeaProjects\Cerb\docs\design\OPEN_DECISIONS.md` (weapon base-damage anchor, quality-enum unification, crit-stacking rule, bind-band)
- `C:\Users\Gabri\IdeaProjects\Cerb\src\main\resources\combat.yml` (live `tiers`, `mob-health-scaling`, `mob-damage-scaling`, 7 combat-skill scaling blocks, defense-bar config)
- `C:\Users\Gabri\IdeaProjects\Cerb\src\main\java\Manager\Tier.java` (`armorPenetration`/`xpMultiplier`/`craftStatMultiplier` fields)
