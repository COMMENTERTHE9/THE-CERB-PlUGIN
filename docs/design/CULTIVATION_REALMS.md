# Cerberus — Cultivation Realm & Ascension System

*Generated 2026-06-08 (cultivation/xianxia design pass). Companion to the [master design plan](CERBERUS_DESIGN_PLAN.md). Reflects the **controlled-hyperscaling** doctrine — see [CULTIVATION_INTEGRATION.md](CULTIVATION_INTEGRATION.md) for what this overturns from the original plan.*

---

## Cultivation Realm & Ascension System

> **Scope & doctrine note.** This section operates under the **new creator direction**: Cerberus is now an over-the-top cultivation power fantasy where numbers climb from hundreds → millions → billions → trillions and a higher realm *hard-dominates* a lower one (realm suppression). The prior anti-power-creep caps (~1,500 player HP, 6× damage clamp, 35k mob HP) are **deliberately retired** as the live ceilings and replaced with the realm-driven curve below. The *only* hard limits that survive are anti-**bug** rails — integer-overflow protection (the XP curve `100·2^(L−1)` overflows ~L25 and is replaced), and a ban on **unintentional** `×level` runaways (First Aid's `level·1500`). Realm power is intentional, tunable, and representable: it rides the existing `double`-based **Virtual Health** pool (which can already hold gigantic numbers), promotes mob HP off the capped attribute basis onto a **mob virtual-HP pool**, and uses `long`/`BigInteger` + a formatter for anything that can exceed `double`'s exact-integer range (~9.0e15). This is the identity layer that sits *above* the tier ladder, the Bands, and the skills — it is the answer to "what am I, and why does the world bend around me."

### 1. The Realm Ladder — Cerberus's Cultivation Spine

Cerberus's cultivation ladder is named after the world's own myth: the **Echo** is the residue of a dead primordial sound, and a cultivator climbs by hearing more of it and eventually *becoming* it. The ladder has **ten Realms** so it maps 1:1 onto the existing ten-tier ladder (WEAK→PRIMORDIAL), the ten Bands (Hearth→Primordium), and the engine's 1–100 level range. This 1:1:1:1 alignment is the keystone: **Realm = Tier = Band theme = the slice of the level curve you can survive.**

| # | Realm name | Classic analog | Maps to Tier | Maps to Band | Echo Family flavor of the realm | Power feel |
|---|---|---|---|---|---|---|
| R1 | **Stirring** | Qi Condensation | WEAK | 0 The Hearth | neutral | "You can hear the Echo." Mortal-plus. |
| R2 | **Vesselborn** | Body Tempering | COMMON | 1 The Verge | SAND | A real cultivator's body. |
| R3 | **Wellspring** | Foundation Establishment | UNCOMMON | 2 Dunesea | SAND | A true qi reservoir forms (ties to Mana). |
| R4 | **Tidecaller** | Core Formation | RARE | 3 The Tidewrack | TIDE | A golden core (your "Echo Core") condenses. |
| R5 | **Embersoul** | Nascent Soul | EPIC | 4 Cinderreach | ASH | Your soul ignites; first taste of dominance. |
| R6 | **Rimeheart** | Soul Transformation | LEGENDARY | 5 The Rimefells | FROST | Your dao freezes into permanence. |
| R7 | **Mirebound** | Void/Dao Seeking | PERFECT | 6 The Sunkmire | MIRE | You touch the void between Echoes. |
| R8 | **Ashen Lord** | Tribulation Transcendence | MYTHIC | 7 Ashen Wastes | ASH+FROST | You rule a domain; lesser beings cower. |
| R9 | **Maw-Eater** | Immortal Ascension | GODLY | 8 The Maw | MIRE+TIDE | You devour realms. Godhood's threshold. |
| R10 | **Primordial Echo** | True Immortal / Dao Ancestor | PRIMORDIAL | 9 The Primordium | all five (convergence) | You *are* the Echo. Walking apocalypse. |

**Why ten and why aligned.** Every existing system already speaks "tier." By making Realm ≡ Tier we get realm gating, realm suppression, realm-colored UI, and realm-keyed loot **for free** off `Tier` — `Realm.forTier(Tier)` and `Realm.tier()` are a static bidirectional map, not new data. The Band table's "Tier theme" column becomes the realm a Band *belongs to*: walking into Cinderreach (Band 4, EPIC) is walking into **Embersoul country**, and a Stirring-realm mortal there is realm-suppressed into paste.

#### 1.1 The realm power curve (the "absurdly large" numbers, tuned)

Each realm applies a **realm power multiplier** `RP(R)` to the player's effective offense and to the Virtual-Health budget, *on top of* tier/skill/gear contributions. Unlike the old 1.35×/step tier ratio, the cultivation curve is **steep and super-exponential late** so endgame feels monstrous. Recommended geometric base with a late "ascendant kink":

```
RP(R) = baseStep ^ (R-1) · kink(R)
  baseStep = 6.0                 // each realm is ~6× the last through R7
  kink(R)  = 1.0 for R ≤ 7
           = 4.0 for R = 8  (Ashen Lord: the "now you're a monster" jump)
           = 8.0 for R = 9  (Maw-Eater: godhood)
           = 16.0 for R = 10 (Primordial Echo: apocalypse)
```

| Realm | RP multiplier | Player effective-HP budget (illustrative) | Player effective hit (illustrative) | Display |
|---|---|---|---|---|
| R1 Stirring | 1× | 200 | 8 | `200` |
| R2 Vesselborn | 6× | 1,200 | 48 | `1.2K` |
| R3 Wellspring | 36× | 7,200 | 290 | `7.2K` |
| R4 Tidecaller | 216× | 43,000 | 1,700 | `43K` |
| R5 Embersoul | 1,296× | 260,000 | 10,000 | `260K` |
| R6 Rimeheart | 7,776× | 1,550,000 | 62,000 | `1.55M` |
| R7 Mirebound | 46,656× | 9,300,000 | 370,000 | `9.3M` |
| R8 Ashen Lord | 1,119,744× (×4 kink) | 224,000,000 | 9,000,000 | `224M` |
| R9 Maw-Eater | 53,747,712× (×8 kink) | 10,700,000,000 | 430,000,000 | `10.7B` |
| R10 Primordial Echo | 5,159,780,352× (×16 kink) | 1,030,000,000,000 | 41,000,000,000 | `1.03T` |

These are the *floor* of each realm; minor stages (§2) and gear/skills push the in-realm range up another ~8–12× before the next realm. So a late-stage, fully-geared **Primordial Echo** legitimately sits in the **multi-trillion HP / tens-of-billions per hit** range — exactly the "Prime Yujiro walks in and everyone is outclassed" target. All of this fits `double` for HP/damage up to ~9e15 exactly; past that (deep Threat-tier endgame, §8 of the plan) values route through the **`BigNumber` formatter** described in §9.

#### 1.2 Numeric representation & the formatter (anti-bug rail, not anti-power rail)

Because numbers intentionally cross `double`'s exact-integer ceiling in the deepest content, all realm-scaled quantities are authored as **`double` for live combat math** (fast, region-thread-safe) with a **canonical string formatter** for display, and a **`BigInteger` ledger** only for *persistent accumulators* that must never lose precision (lifetime qi earned, spirit-stone bank). Display uses **xianxia-flavored units** with a Western fallback:

| Magnitude | Western | Cerberus xianxia unit (configurable) |
|---|---|---|
| 1e3 | 1.2K | 1.2 *Mote* |
| 1e6 | 3.4M | 3.4 *Spark* |
| 1e9 | 5.6B | 5.6 *Ember* |
| 1e12 | 7.8T | 7.8 *Star* |
| 1e15 | 9.0Qa | 9.0 *Heaven* |
| 1e18+ | 1.2Qi | 1.2 *Eternity* |

The formatter is a pure function (`String CultivationFormat.compact(double)` / `(BigInteger)`), unit-tested for monotonicity and overflow exactly like the rewritten XP curve. **No combat formula may ever round-trip a number through an `int`** — that is the institutional defense against the next overflow landmine.

---

### 2. Minor Stages & Bottlenecks (the texture inside a realm)

Each realm is divided into **four minor stages** plus **two bottleneck gates**, so a single realm is a multi-session arc, not a step. Stages are pure progression sugar layered on the existing **use-leveled skill XP**; they consume the *same grind* the plan already designs, re-skinned as cultivation.

| Stage | Name | In-realm power band | What it gates |
|---|---|---|---|
| Early | **Kindling** | RP(R) × 1.0 | freshly broke through; can survive your Band's *floor* |
| Mid | **Surging** | RP(R) × 2.5 | comfortable in your Band; can clear named family elites |
| Late | **Crowning** | RP(R) × 6.0 | can hunt your Band's *boss*; first peek into the next Band |
| Peak | **Consummate** | RP(R) × 10.0 | maxed for the realm; **bottleneck wall is now visible** |
| — | **Lesser Bottleneck** (between Surging→Crowning) | — | a *plateau*: skill XP gains halve until you cleanse it |
| — | **Greater Bottleneck** (at Peak) | — | the *wall*: cannot gain cultivation XP at all until Breakthrough (§3) |

**Cultivation XP ("Qi").** Realm/stage advancement is driven by a dedicated **Qi** pool, distinct from the 35 use-leveled skills but *fed by them*. Qi accrues from: mob kills (scaled by the mob's tier `xpMultiplier` — PRIMORDIAL mobs are 15× and intentionally lucrative), gathering/refining at-or-above your realm, completing Echo Family rituals, and **meditation** (a slow passive tick while sneaking at an attuned altar, Folia-safe via `entity.getScheduler()`). The **Mastery Level** the plan already defines (sum of all skills / 10) sets your *Qi ceiling per stage* — you cannot Surge if your skills are too thin, which keeps cultivation honest and prevents pure-grind-one-skill ascension.

**Bottlenecks as designed friction.** The **Lesser Bottleneck** is a soft DR plateau cleansed by consuming a **realm-appropriate pill** (Alchemy, §4) or a single **family token** — a small, predictable speed bump. The **Greater Bottleneck** is the *event horizon* of a realm: a hard stop that can only be broken by the **Breakthrough/Tribulation** ritual (§3). This is where the drama lives. A player can sit at **Peak Consummate** indefinitely, hoarding resources and gear, deciding *when* to risk the heavens — which is the classic xianxia tension and a perfect natural soft-gate against rushing ahead under-geared.

---

### 3. Breakthroughs & Tribulations (the dramatic realm-up)

A **Breakthrough** is the act of crossing a Greater Bottleneck into the next realm. Every Breakthrough is gated by a **Tribulation** — a scripted, escalating trial whose form depends on which realm you are entering. This reuses the **Boss/Encounter** system, the **Bands**, and the **3-Layer Defense** wholesale; a Tribulation is just a special boss encounter with realm-suppression inverted (the heavens suppress *you*).

#### 3.1 Tribulation types by realm threshold

| Entering realm | Tribulation name | Form | Where it triggers | Failure stake |
|---|---|---|---|---|
| R2 Vesselborn | **First Stirring** | tutorial trial: survive a 30s wave of WEAK adds | anywhere in Hearth/Verge | none (cannot fail; teaching beat) |
| R3 Wellspring | **Sand Trial** | mini-boss: a SAND elite + sandstorm hazard | a SAND altar, Band 1–2 | lose 25% banked Qi |
| R4 Tidecaller | **Heart's Tide** | solo boss: condense your Echo Core under tidal pressure | TIDE altar, Band 3 | lose 50% Qi, drop to Peak of R3 |
| R5 Embersoul | **Soul Ignition** | boss + Reservoir-Inversion phase (your HP/MP swap) | ASH altar, Band 4 | lose 50% Qi + 1 random skill knocked −5 levels |
| R6 Rimeheart | **Heavenly Frost Tribulation** | first true *heavenly tribulation*: 3 telegraphed "lightning of the heavens" strikes you must survive at <X HP%, plus a FROST boss | Rimefells, Band 5 | **soul wound**: −1 realm stage + entropy spike on all gear |
| R7 Mirebound | **Void Tribulation** | survive the void between Echoes: silence field (no casting) + Mire boss + Mana-Burn | Sunkmire, Band 6 | drop to R6 Peak; gear takes heavy entropy |
| R8 Ashen Lord | **Lordly Tribulation** | 5-wave tribulation lightning that *grows* each wave; must out-DPS an enrage; ASH+FROST dual boss | Ashen Wastes, Band 7 | drop a full minor stage; lose a Nomicon charge |
| R9 Maw-Eater | **Devouring Tribulation** | the Maw itself fights back: full-loot LETHAL arena, GODLY boss, add-consume-to-heal | The Maw, Band 8 | drop to R8; **keyed-armor shard recoverable** loss |
| R10 Primordial Echo | **Echo Convergence** | the capstone: 5-phase Cerberus-tier trial, one Echo Family per phase, all 5 keyed sets *recommended* | The Primordium, Band 9 | drop to R9 Consummate; massive Qi loss; cosmetic "Failed Ascendant" scar |

#### 3.2 Heavenly Tribulation mechanics (the spectacle)

From R6 onward the Breakthrough is a **Heavenly Tribulation** — an event that *the world performs at you*, expressible entirely with Folia-safe primitives (no ProtocolLib): darkened sky, `BossBar` countdown, lightning-strike `BlockDisplay`/particle telegraphs, sound crescendo, and a glowing tribulation circle. Structure:

- **Tribulation waves**: N waves (N = realm number − 4, so R6=2 waves up to R10=6 waves) of "heavenly strikes." Each strike is a **telegraphed flat % of your max Virtual Health** that *ignores armor and the defense bar* (the heavens don't care about your gear) but *can* be reduced by **dao comprehension** (§6) and **tribulation-resist pills**. You must survive each wave above a HP-% floor.
- **Enrage / DPS gate** (R8+): a boss spawns mid-tribulation; failing to kill it before the final wave = automatic failure.
- **Realm suppression inverted**: during the tribulation you are treated as if **one realm lower** for *defense* (the heavens suppress the ascendant), forcing you to over-prepare — the whole point of sitting at Peak Consummate hoarding pills.

#### 3.3 Resources that fuel advancement

Advancement is intentionally a **multi-system sink** so cultivation pulls every existing pillar forward:

| Resource | Source (existing system) | Spent on |
|---|---|---|
| **Qi** (cultivation XP) | mob kills (tier `xpMultiplier`), gathering, rituals, meditation | minor-stage progress; the per-stage Qi ceiling = Mastery-gated |
| **Skills (the 35)** | use-leveling | Mastery Level gates Qi ceiling + Breakthrough eligibility |
| **Pills** | **Alchemy** skill + tier-quality reagents | cleanse bottlenecks; tribulation-resist; temporary realm-boost |
| **Spirit Stones** | new soft currency (alias/replacement for **Marrow**), tiered by Band | Breakthrough "offering" cost; pill reagents; altar lighting |
| **Echo Family Tokens** (Glass Tear / Brine Pearl / Cinder Mote / Frost Shard / Mire Husk) | same-family kills + rituals | realm *attunement* (§5); altar gate use; keyed-armor; Lesser-Bottleneck cleanse |
| **Nomicons** | EPIC+ bosses / family GODLY-PRIMORDIAL bosses | unlock **Stable Breakthrough** (no failure-stake) and Techniques (§6) |
| **Tribulation Pills / Talismans** | high-tier Alchemy (talismans are a *separate* system — realm only *consumes* them, never owns them) | reduce heavenly-strike damage; one-shot revive-on-fail |

**Stable vs. Risky Breakthrough.** A player at Peak Consummate may attempt a **Risky Breakthrough** (cheaper — just Qi + a stone offering, full failure stakes) or pay the **Stable** premium (the realm's Nomicon + a stack of tribulation pills) to *guarantee* success with reduced stakes. This is the spend valve for the rich endgame player and the gamble for the bold one.

---

### 4. Pills, Alchemy & the Advancement Economy

Alchemy (an existing utility skill) becomes the **realm-support spine**. Pills are authored data-driven (mirroring the mob registry / open-ended gear registry philosophy) so new pills ship as config, not code.

| Pill class | Effect | Realm relevance |
|---|---|---|
| **Cleansing Pill** | removes a Lesser Bottleneck plateau | every realm; reagent tier must ≥ your realm |
| **Tribulation Pill** | −X% heavenly-strike damage for one tribulation | R6+ Breakthroughs; stacks to a capped floor |
| **Foundation Pill** | permanently +1 minor-stage Qi ceiling (rare) | smooths the grind; hard-capped per realm |
| **Ascendant's Folly** (forbidden pill) | instantly fills Qi to Peak but inflicts a "qi deviation" debuff (random skill −10, tribulation suppression worsened) | the greedy shortcut — high risk, very xianxia |
| **Realm-Boost Elixir** | temporary: act as +1 realm for offense for 60s (NOT defense) | a consumable "burst into the next band" for daring expeditions |

Reagent tiers ride the **unified tier ladder** (a PRIMORDIAL pill needs PRIMORDIAL-tier reagents), and the **inputs-gate-outputs** crafting rule the plan already enforces means you cannot brew above your material/skill tier — so the pill economy auto-tracks realm without new gating logic.

---

### 5. Realm as the Content Gate & the Realm-Suppression Aura

Realm is the **master key** that gates zones, weapons, gear, and social standing — and it is the carrier of the signature **realm-suppression** feeling.

#### 5.1 What realm gates

| Gated thing | Rule |
|---|---|
| **Zones / Bands** | A Band *expects* its matching realm. You may physically walk anywhere (distance = difficulty is sacred), but you are **suppressed** (§5.2) if you are below the Band's realm, making under-realm travel a deliberate, deadly flex. |
| **Weapons (~250 target)** | Each weapon has a **required realm to wield at full power**. Below it, the weapon is *sealed*: it functions at the wielder's realm cap, not the weapon's (no "ferry a god-sword to a mortal" exploit). At/above, full stats unlock. |
| **Gear (~250 target — Wearables + Equipment/Tools)** | Same seal rule. Wearables (armor/clothing/accessories) gate their *defensive* realm budget; Equipment & Tools (chainsaws, drills, hunting gear, utility) gate their *function tier* (a Primordial drill mines Primordium nodes; a Stirring drill cannot). The **gear-TYPE registry** (open-ended, data-driven like the mob registry) carries a `requiredRealm` field per type, so new gear types inherit realm gating with zero new code. |
| **Blink Gates** | The plan's Gate USE-gating becomes realm-gating: the Maw Gate requires R9 attempt-eligibility, the Primordium Gate requires holding a Nomicon — i.e., R10-track. This *is* the anti-"ferried to Primordium at level 5" rail, now thematically grounded. |
| **Techniques / Manuals (§6)** | A manual cannot be *comprehended* above your realm; you can hold it, not use it. |

#### 5.2 The Realm-Suppression Aura (cross-ref the power-scaling design)

Realm suppression is the mechanical heart of the power fantasy: **a higher realm hard-dominates a lower one, beyond what raw stats alone would give.** It is implemented as a multiplicative modifier in the existing **3-Layer Defense** pipeline and the offense path, keyed on `attackerRealm − defenderRealm = Δ`:

```
suppression(Δ):
  Δ ≥ +3 realms : "Overwhelm" — defender takes ×(1 + 0.5·Δ) damage AND deals ×(1/(1+Δ)) damage;
                  defender's defense bar regen frozen; crits against defender auto-succeed.
  Δ = +1..+2    : "Pressure"  — +25%/+60% damage taken, −20%/−40% damage dealt by the lower realm.
  Δ = 0         : fair fight (gear/skill/dao decide it).
  Δ ≤ −1        : you are the lower realm — the mirror of the above is applied to YOU.
```

- **PvE expression**: a Stirring-realm player wandering into Cinderreach (Embersoul Band, Δ = −3) is *Overwhelmed* — generic mobs there one-shot them regardless of gear, and their own hits tickle. This makes distance-as-difficulty *brutal and legible*, and makes "reaching a Band you can actually fight in" the core power milestone.
- **PvP expression**: realm suppression makes a higher-realm player a *walking apocalypse* to lower realms (the Yujiro fantasy), but the PvP scalar (`pvp-damage-multiplier`, the plan's tunable) still applies so same-realm PvP stays a skill fight, not a one-shot. **Realm brackets** for PvP (the plan's "level-bracketed PvP") become **realm-bracketed**: full-loot only bites within ±1 realm; ganking 3+ realms down yields no loot and flags the aggressor (anti-grief, ties to the moderation surface the Open Decisions flag).
- **Mob expression / mob virtual-HP**: because mobs need to reach billions+ at endgame, the plan's "promote mobs off the capped attribute" becomes a **MobVirtualHealth pool** mirroring player Virtual Health (a `double` pool on the entity's PDC + a manager), and the mob's **realm = its Band's realm** drives its suppression aura against players. The old per-tier HP-cap map is **re-targeted upward** to the realm curve (PRIMORDIAL/R10 mobs in the billions), with the literal-bug rails kept: HP is a bounded `double`, never an overflowing `int`.

#### 5.3 Realm-suppression "aura" as a visible verb

When a much-higher realm entity is near a much-lower one, the suppression is *felt before the fight*: screen-edge vignette + slow + a low sound (reuse HUD/particle layer). A Primordial Echo player literally radiates a pressure field (`PotionEffect` slow/weakness applied to far-lower-realm mobs in a radius, Folia region-local) — the "everyone in the room is instantly outclassed" beat, expressed with vanilla-safe APIs.

---

### 6. Optional Flavor Systems (grounded in the existing engine)

These deepen identity without new engines — each reuses a system already designed.

#### 6.1 Techniques & Manuals (built on the skill + Nomicon systems)
A **Manual** is a Nomicon variant (existing drop system) that, once *comprehended* (requires realm ≥ manual realm + a Qi cost), grants a **Technique** — an active or passive that hooks the combat pipeline at a defined insertion point, like Defensive Magic does. Examples: *Echo-Step* (blink that scales with realm), *Heaven-Severing Palm* (a charged hit dealing realm-scaled flat damage), *Nine-Tide Body* (passive: convert % of bar overflow back to HP). Manuals are realm-gated and **family-flavored** (a TIDE manual drops from TIDE bosses), tying techniques to Echo Families.

#### 6.2 Dao Comprehension (a second, slow axis)
A capped, slow-bleeding meta-stat earned by *using* a realm's theme (kills with the Band's element, surviving tribulations). **Dao points** buy small permanent edges — most importantly **tribulation-strike resistance** (the only way to soften heavenly strikes besides pills) and **suppression resistance** (lets you punch one realm up less suicidally). This is the bounded-infinite endgame chase that fits the plan's "Echo Resonance" prestige slot — reskin/merge **Primordial Ascension** prestige into **Dao Comprehension** so they're one system, not two.

#### 6.3 Bloodlines / Physiques (a character-creation/rare-unlock axis)
A **Physique** is a rare, account-level passive (rolled at first Breakthrough or unlocked via a GODLY+ drop) that flavors *how* you cultivate: e.g., *Glass-Bone Physique* (SAND — +armor, −dodge), *Drowned-Lung Physique* (TIDE — water immunity, mana regen), *Ashen-Heart* (ASH — ignite-on-hit, fire immunity), *Rimebound* (FROST — slow immunity, freeze aura), *Hollow-Veined* (MIRE — void resist, cheaper blink). Physiques map cleanly onto the five Echo Families' elements and the existing Elemental Infusion types — **no new element math**, just a per-player tag and a few pipeline hooks.

#### 6.4 Sects & Family Ties (the Echo Families as cultivation factions)
The five Echo Families *are* the cultivation sects. **Attunement** (the plan's family token sink) becomes your **sect standing**, and your realm + sect determines: which family altars give you Stable Breakthroughs, which keyed-armor line you can wear at full power, and which Family Wars / Outpost sieges you fight in. Switching sects keeps the plan's deliberate cost (token sink + cooldown). A high-realm player of a sect can act as a **patriarch/master** — a social/diegetic role (can light altars for lower-realm sect-mates, granting them a temporary suppression-resist buff in that Band), giving endgame players a *reason to mentor* rather than just farm.

---

### 7. Persistence, Folia-Safety & Build Notes

| Concern | Approach |
|---|---|
| **Storage** | New `player_cultivation` table (auto-create like the existing five): `uuid, realm (int 1–10), minor_stage (int 0–3), qi (BigInteger as TEXT), dao_points, physique_id, sect_id, attunement_json`. Spirit Stones/tokens ride the planned currency wallet tables. Follow the snapshot-on-entity-thread → async I/O → reschedule pattern via `AsyncSaveManager`. |
| **Schema versioning** | Add the `schema_version` table the Open Decisions flag recommends; cultivation is the first feature to ship a versioned migration (and a clean **v1 wipe** is fine, since the HP/XP rewrite already invalidates old saves). |
| **Numbers** | Live combat math in `double`; persistent accumulators (lifetime Qi, stone bank) in `BigInteger` stored as TEXT; all display via `CultivationFormat`. Unit-test the formatter and the `RP(R)` curve for monotonicity + no overflow (the same test discipline the Open Decisions demand for the XP curve). |
| **Folia** | Suppression-aura field application, tribulation strikes, meditation ticks all use `entity.getScheduler()` / region-local effects; tribulation bosses spawn and leash within one region; `teleportAsync` for any cross-region realm-gate travel. |
| **Open-ended gear** | The realm `requiredRealm` field lives on the **gear-TYPE registry entry** (data-driven), so the ~250 weapons and ~250 gear items — including unforeseen Equipment & Tools types — inherit realm gating and the seal rule automatically, exactly as mobs inherit scaling from the mob registry. |

---

### 8. Resolved & Open Realm Decisions

**Resolved by this section (defaults chosen, all tunable in config):**
- Ten realms, 1:1 with the tier ladder, Bands, and the five families' theming.
- `RP(R)` = `6^(R-1)` with ×4/×8/×16 kinks at R8/R9/R10, yielding the K→M→B→T cultivation scale.
- Four minor stages + Lesser/Greater bottlenecks; Mastery Level gates the per-stage Qi ceiling.
- Tribulations reuse the boss system; heavenly strikes ignore armor/bar but are softened by dao/pills; failure stakes scale by realm.
- Realm suppression as a multiplicative pipeline modifier on the `attacker−defender` realm delta, with Overwhelm at Δ≥3 — the realm-suppression aura.
- Old anti-creep caps retired as ceilings; only literal-bug rails (overflow, accidental `×level`) kept.

**Open (need a creator call):**
- **Exact `baseStep` and kink values** — is `6.0`/×16 the right "absurd," or push to `8.0`/×32 for an even harder cultivation novel slope? (Sets the whole economy's slope.)
- **Tribulation failure severity** — is "drop a full minor stage + entropy spike" too punishing for a small server, or correct xianxia stakes? Consider a per-server difficulty toggle.
- **Suppression hard-floor in PvP** — does Δ≥+3 Overwhelm apply in same-faction sect PvP, or is intra-sect dueling normalized to fair-fight to keep mentoring fun?
- **Can you cultivate past R10?** — recommend **no realm past Primordial Echo**; route infinite scaling into the plan's Threat-tier Depth Spiral (cosmetics/leaderboard past the power cap), so R10 is the terminal *identity* but not the terminal *challenge*.
- **Dao Comprehension vs. Primordial Ascension prestige** — confirm merging them into one bounded-infinite axis (recommended) rather than shipping two overlapping prestige systems.
- **Realm-boost consumables in PvP** — allow the Realm-Boost Elixir's temporary +1-realm-offense in PvP, or PvE-only to prevent a pay-to-burst meta?

**Files relevant to implementing this section** (all absolute): `C:\Users\Gabri\IdeaProjects\Cerb\src\main\java\Manager\MobHealthScaling.java` (promote to virtual-HP pool + re-target caps upward), `C:\Users\Gabri\IdeaProjects\Cerb\src\main\java\Manager\Tier.java` (add `Realm.forTier`/`tier()` bridge), `C:\Users\Gabri\IdeaProjects\Cerb\src\main\resources\combat.yml` (realm curve + suppression config section), and the new design lives in `C:\Users\Gabri\IdeaProjects\Cerb\docs\design\CERBERUS_DESIGN_PLAN.md`.
