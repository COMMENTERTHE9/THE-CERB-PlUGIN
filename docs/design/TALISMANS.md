# Talisman System

*Generated 2026-06-08 (cultivation/xianxia design pass). Companion to the [master design plan](CERBERUS_DESIGN_PLAN.md). A **separate domain** from Gear — see [GEAR.md](GEAR.md) §1, which reserves an unread `talisman-sockets` field that this system owns and reads. Reflects the **controlled-hyperscaling** doctrine ([CULTIVATION_INTEGRATION.md](CULTIVATION_INTEGRATION.md)) and the central realm power-curve ([POWER_SCALING.md](POWER_SCALING.md) §2).*

---

> **Scope & doctrine.** Talismans are **borrowed external power** — paper-and-cinnabar charms, spirit-paper, jade tags, and bound-spirit seals that a cultivator *slots into* gear or *carries* to gain conditional, often-triggered effects. Where **Gear** is the always-on physical apparatus (passive stat slabs) and **Skills** are the cultivator's refined internal essence (use-leveled), **Talismans are a third axis: a modular, swappable, mostly-conditional effect layer that the player tunes per encounter.** This section designs ~**260 talismans** generatively (archetype × realm × variant), a `TalismanRegistry` mirroring `MobRegistry`/`GearRegistry` exactly, a socketing model that reads Gear's reserved `talisman-sockets` field, and a **hard balance budget** so talismans never breach the capped offense/defense budgets the realm curve already enforces.

---

### 1. Boundary — What a Talisman IS (and is NOT)

A talisman is a **socketable or carried effect-module** with three defining properties that separate it from every adjacent system:

1. **Conditional / triggered, not flat-passive.** A gear stat is "+X armor, always." A talisman is "*when* you fall below 30% HP, *proc* a barrier" or "*on* crit, *trigger* a chain-lightning." Even "passive" talismans (Sustain/Movement) are framed as effect-modules, not raw stat slabs.
2. **Swappable per-encounter, charge-gated.** Talismans **socket and unsocket freely** (with an entropy/cooldown cost, §4) and most carry **charges** that deplete — they are a *consumable-adjacent* layer, not permanent itemization.
3. **Owned by its own registry & table.** Talismans never appear in `gear-items.yml`, never roll as gear affixes, and never occupy an equip slot. They live in `talismans.yml` and persist in `player_talismans`.

**The four-way distinction (the load-bearing boundary table):**

| Axis | **Gear** (Wearable) | **Gear Affix** | **Ring** (Gear, `ring_1..10`) | **Talisman** (this system) |
|---|---|---|---|---|
| What it is | Worn apparatus | Random modifier *on* a gear piece | A worn gear piece in a finger slot | Socketed/carried effect-module |
| Occupies | An equip slot | Nothing (rider on host item) | One of 10 ring slots | A `talisman-socket` *inside* a gear piece, or a carry-pouch slot |
| Effect shape | Flat, always-on stat | Flat/small proc, fixed at craft | Small flat affix budget (flex) | **Conditional / triggered / proc**, swappable |
| Mutability | Re-roll = reforge (destructive) | Locked once rolled | Locked once rolled | **Freely swapped**; charge-gated, entropy on re-socket |
| Budget pool | Armor (L2) / HP / resist (L1) | Folded into gear's tier budget | Ring flex pool | **Separate Talisman Budget** (§4), capped *inside* the global caps |
| Registry / table | `gear-items.yml` / `player_gear` | rolled into gear `affix_json` | `gear-items.yml` / `player_gear` | **`talismans.yml` / `player_talismans`** |
| Acquisition | Craft / drop | Reforge / craft roll | Drop / craft | **Scribed** (Inscription skill) / boss drop / ritual |

**Why separate.** Three reasons, all structural:
- **Build velocity.** Gear is a slow, destructive grind (reforge ruins, temper is +0.08/step). Talismans are the **fast-twitch build layer** — swap your proc loadout for a frost boss vs a fire boss without re-grinding armor. Two different cadences need two different systems.
- **Effect class.** The proc/trigger/set-key effect-space is fundamentally a *script-hook* space (like Defensive Magic), not a *stat-slab* space. Cramming it into gear affixes would force every gear roll to carry script state.
- **Budget isolation.** Talismans draw from their **own clamped budget pool** that sits *under* the global offense/defense caps. Keeping it a separate pool means a talisman loadout can be balanced (and nerfed) independently of gear without re-tuning the entire itemization surface.

---

### 2. Talisman Archetypes (the generative rows by effect-class)

Archetypes are the generative **rows**; realm × variant are the **columns** (§5), exactly as Weapons (archetype × realm × variant) and Gear (archetype × band × variant) do it. Each archetype maps to a single `effect.type` behavior handler (mirrors Gear's `GearBehavior` map), so a new archetype = one handler class; a new talisman *in* an existing archetype = pure config.

| # | Archetype (effect-class) | Effect shape | `effect.type` | Draws from budget | Signature example |
|---|---|---|---|---|---|
| 1 | **Offensive** | Flat/% added damage or armor-pen, gated by a condition (vs-realm, vs-element, vs-low-HP) | `OFFENSE` | Offense | *Cinnabar Wrath Seal* — +X% damage vs lower-realm foes |
| 2 | **Defensive** | Flat/% mitigation, resist, or defense-bar size while a condition holds | `DEFENSE` | Defense | *Jade Carapace Tag* — +ICE resist while bar > 50% |
| 3 | **Sustain** | Lifesteal, HP/Essence regen, defense-bar regen, overheal-to-bar | `SUSTAIN` | Sustain (sub-cap of Defense) | *Marrow-Drinking Charm* — heal X% of damage dealt |
| 4 | **Proc-Trigger** | On-event (crit / kill / hit-taken / low-HP / cast) → fire a scripted effect | `PROC` | Offense **or** Defense (by proc payload) | *Stormcall Talisman* — on-crit: chain-lightning, N charges |
| 5 | **Set-Key** | Inert alone; completes/empowers a keyed gear SET or an Echo-Family attunement | `SET_KEY` | none (gates a *gear* bonus, no own budget) | *Frost-Sovereign Keystone* — unlocks Frost-Keyed 6-pc at full effect |
| 6 | **Summon / Companion** | Spawns a temporary bound spirit/construct that fights/buffs; TTL + leash | `SUMMON` | Offense (companion DPS counts against your cap) | *Paper Crane Effigy* — summons a scaling spirit-ally |
| 7 | **Movement** | Dash, blink-extend, glide, slow-immunity, mount-summon, Echo-Step empower | `MOVEMENT` | Utility (off-budget for combat) | *Cloud-Tread Slip* — short blink, M charges |
| 8 | **Economy / Luck** | Drop-rate, gathering-yield, currency-find, pity-progress, Qi-gain modifiers | `ECONOMY` | Utility (off-budget for combat) | *Fortune Cat Tag* — +X% Echo-Shard find |
| 9 | **Suppression** | Modifies the §3 realm-gap you *suffer* or *project* (counter-suppress, or amplify) | `SUPPRESSION` | Suppression (own micro-cap, §4) | *Defiant Junior Seal* — −1 to the realm-gap Δ you suffer |
| 10 | **Ward / Status** | Cleanse, status-immunity windows, reaction-priority shift, tribulation-resist | `WARD` | Defense | *Heaven-Defying Ward* — −X% tribulation-strike damage (R6+) |

**Notes on the cross-system rows:**
- **Set-Key (5)** is the explicit, sanctioned bridge to Gear: it is the *only* talisman archetype that touches gear's set-bonus math, and it does so by **gating** (flip a keyed-set from 50%-inert → full effect, per the Open-Decisions keyed-set default), never by adding its own stats. This is how the two domains interoperate without coupling budgets.
- **Suppression (9)** is the cultivation-novel "defiant junior with a treasure that punches above their realm" trope (referenced in GEAR.md §6) made into a *swappable* module rather than a fixed relic. It has its own micro-cap so a stack of them can't fully cancel realm suppression.
- **Ward (10)** is the only system besides Pills (CULTIVATION_REALMS.md §4) that can soften heavenly-tribulation strikes — talismans are explicitly listed there as a *consumed* resource the realm system never owns.

---

### 3. Socketing — How Talismans Enter Gear

Talismans bind to gear through the **`talisman-sockets` integer** already reserved on each `GearType` (GEAR.md §3.2, currently `talisman-sockets: 0`, unread). This system is the first and only reader of that field.

#### 3.1 Two attach modes

| Mode | Where it lives | Capacity | Use case |
|---|---|---|---|
| **Socketed** | Inside a gear piece's `talisman-sockets` | Per-piece socket count (§3.2) | The build's *committed* talismans (entropy cost to swap) |
| **Carried** | A `talisman-pouch` (granted by the `belt` slot, like tool-belt slots) | `base 0`, `max 4`, granted by equipped belt | *Fast-swap* talismans (free swap, but reduced effect — 70%) |

Socketed talismans are stronger (full effect) but entropy-gated on swap; carried talismans are weaker (×0.70) but swap freely in combat. This mirrors the Gear `belt`→tool-belt grant pattern (GEAR.md §2.2) exactly, so the pouch needs no new mechanism.

#### 3.2 Sockets-per-gear-piece by realm

Socket count is a property of the **gear piece's realm**, not the talisman. Higher-realm gear has more sockets — the same "higher realm = bigger canvas" rule the ring system uses. A piece's socket count is `min(realmSockets[gearRealm], typeSocketCap)` where `typeSocketCap` is the per-`GearType` ceiling in `gear-types.yml` (so a ring might cap at 1 socket, a chestplate at 3).

| Realm band (gear piece) | Tiers | Sockets per piece (`realmSockets`) | Notes |
|---|---|---|---|
| Band I — Body Tempering | WEAK, COMMON | 0 | talismans not yet socketable; carry-only |
| Band II — Qi Condensation | UNCOMMON, RARE | 1 | first socket appears |
| Band III — Foundation/Core | EPIC, LEGENDARY | 2 | |
| Band IV — Nascent Soul | PERFECT, MYTHIC | 2 | |
| Band V — Realm Sovereign | GODLY, PRIMORDIAL | 3 | endgame canvas |

**Total socket budget at endgame:** a fully PRIMORDIAL-geared player has up to `~12 weapon/armor pieces × 3 = ` *theoretical* 36 sockets, but `typeSocketCap` clamps most slots (rings = 1, accessories = 1, weapons = 2, armor quad = 3), landing real endgame capacity at **~18–22 socketed + 4 carried**. This is intentionally large — and §4's *budget* clamp, not the *socket count*, is what prevents runaway power.

#### 3.3 Stacking & diminishing rules

The socket count is generous; the **effect stacking** is where balance lives. Three rules:

1. **Same-archetype diminishing.** The Nth talisman of the same `effect.type` contributes at `dimFactor^(N-1)` with `dimFactor = 0.5` (1st = 100%, 2nd = 50%, 3rd = 25%, …). Stacking five Offensive seals yields `1 + .5 + .25 + .125 + .0625 = 1.9375×` one seal — never linear. This makes **diverse loadouts strictly better than mono-stacking**, the same anti-mono-stack philosophy as the crit-chance cap.
2. **Unique-keyed talismans** (`unique: true`, mostly curated/boss talismans) **ignore diminishing** but only one of each may be equipped account-wide (PDC `cerb_talisman_unique` guard).
3. **Set-Key and Suppression do not diminish** against *each other* but each is independently micro-capped (§4) — you cannot stack three Suppression seals to fully negate realm suppression.

---

### 4. The Balance Budget (talismans never breach the capped budgets)

This is the structural heart of the section: **talismans add a fourth contribution source to offense/defense, so they must consume budget *inside* the existing caps, not on top of them.**

#### 4.1 The Talisman Budget Pool — a sub-allocation of the realm baseline

Talismans collectively draw from a **Talisman Budget** expressed as a fraction of the player's realm-anchored baseline (POWER_SCALING.md §2.2 — `realmMult × levelMult`). The whole talisman loadout is clamped to:

```
talismanOffenseBudget(realm) = OFFENSE_BUDGET_CAP_FRACTION_TALISMAN × realmBaseline(realm)
   TALISMAN_OFFENSE_FRACTION = 1.0      // talismans may add up to +1.0× the realm baseline to offense
   TALISMAN_DEFENSE_FRACTION = 0.8      // ...and up to +0.8× to the defensive budget
```

These fractions live **inside** the global `OFFENSE_BUDGET_CAP = 6.0` (POWER_SCALING.md §4.2). The accounting:

| Contribution source | Share of the 6.0× offense cap (illustrative split) |
|---|---|
| Base gear (realm-matched) | up to 4.0× |
| Skills + crit + infusion | up to 1.0× |
| **Talismans** (this system) | **up to 1.0× — and the clamp is enforced at resolve time** |
| **Sum, hard-clamped** | **6.0× of realm baseline** |

So if a player's gear + skills already sit at 5.5×, the talisman layer can only contribute 0.5× before the global clamp in `CombatSkillProcessor` (POWER_SCALING.md §4.2) eats the rest. **Talismans are squeezed by the same wall everything else is** — they cannot be the system that breaks the cap, because the cap is checked *after* their contribution is summed.

#### 4.2 The five sub-budgets and their micro-caps

| Talisman budget | Feeds | Micro-cap (fraction of realm baseline) | Hard rail it lives under |
|---|---|---|---|
| **Offense** | raw dmg %, armor-pen %, companion DPS | +1.0× | Global 6.0× offense clamp |
| **Defense** | mitigation, resist %, bar size, Ward | +0.8× | L2 armor ≤ 40% (55% only via gear set, not talismans); per-element resist ≤ 75% |
| **Sustain** | lifesteal, regen (sub-cap of Defense) | +0.4× (lifesteal ≤ 15% of damage dealt) | Inversion/backlash regen-freeze rules still override |
| **Suppression** | counter-suppress the realm-gap Δ | **−2 max to suffered Δ, +0 to projected Δ** | `SUPP_MAX_GAP = 4`; talismans may reduce *suffered* gap, never raise *projected* gap (no ganking amplifier) |
| **Utility** | movement, economy, luck | off the combat budget; capped per-axis | Gathering yield +50% cap; crit-chance 0.75 cap unaffected |

**Critical clamps talismans may NOT exceed (restating the global rails so the budget is unambiguous):**
- **Armor mitigation cap stays 40%** — talismans contribute toward it but cannot raise the *cap* (only the Heavy Sovereign 6-pc gear set does, to 55%).
- **Per-element resist stays ≤ 75%** even fully stacked.
- **Crit chance hard-caps at 0.75, crit mult at 3.5×** — dimensionless, talismans included.
- **Suppression counter is capped at −2 Δ.** A junior with the best talismans still suffers a 3-realm gap as a 1-realm fight at *best* — never a fair fight. This preserves "distance is difficulty" mechanically.
- **Summon/companion DPS counts against the player's offense cap** — a summon loadout is not a cap-bypass.

#### 4.3 Realm scaling of talisman magnitude

A talisman's *flat* contributions scale on the **same `gearRealmMultiplier`** table Gear uses (GEAR.md §5.1), so a PRIMORDIAL talisman's flat numbers are billions while a COMMON one's are tens — but the *fractional* caps in §4.2 are realm-invariant, so the **ratio** stays balanced across realms (the POWER_SCALING.md §4.1 "magnitude inflates, gameplay ratio conserved" invariant). A talisman whose realm is *below* the wearer's realm is **sealed to the wearer's realm cap fraction** (same seal rule as under-realm gear/weapons, CULTIVATION_REALMS.md §5.1) — you cannot get free endgame value from a low-realm talisman, but it isn't dead weight either; it scales *down* to your realm's slice.

---

### 5. Count Target & Generative Model

Target: **~260 talismans**, matching the Weapons (~263) and Gear (~265) surfaces, almost entirely generated.

**Generative math:** `count = Σ over archetypes ( realm-bands-it-appears-in × variants )`, identical to the Gear/Weapons approach (archetype × band × variant), plus a curated unique layer.

Using the **5 realm bands** (GEAR.md §4.1) for authoring sanity:

| Archetype | Bands present | Variants (condition/payload) | Generated | Notes |
|---|---|---|---|---|
| Offensive | 5 | 3 (vs-realm / vs-element / execute-low-HP) | **15** | per-element handled by infusion tag, not new variants |
| Defensive | 5 | 3 (flat-mit / element-resist / bar-size) | **15** | |
| Sustain | 5 | 2 (lifesteal / regen) | **10** | |
| Proc-Trigger | 5 | 5 (on-crit / on-kill / on-hit-taken / on-low-HP / on-cast) | **25** | the largest archetype — proc trigger is the build identity space |
| Set-Key | 5 | 5 (one per Echo Family: SAND/TIDE/ASH/FROST/MIRE) | **25** | inert without family-keyed gear |
| Summon/Companion | 5 | 3 (melee-spirit / caster-spirit / buff-totem) | **15** | TTL + leash; DPS counts vs offense cap |
| Movement | 5 | 3 (dash / glide / slow-immunity) | **15** | utility budget |
| Economy/Luck | 5 | 3 (drop-find / gather-yield / Qi-gain) | **15** | utility budget |
| Suppression | 4 (RARE+) | 2 (counter-suffer / aura-resist) | **8** | only appears once realm-gaps matter |
| Ward/Status | 5 | 3 (cleanse / status-immune / tribulation-resist) | **15** | tribulation-resist is R6+ only |
| **Generated subtotal** | | | **158** | |
| **Charge/quality variants** (each generated talisman ships a `lesser`/`standard`/`greater` charge-count tier) | | ×~1.6 effective on the high-traffic archetypes | **~+80** | charge-tier is a cheap config multiplier, not a new entry per se |
| **Curated unique/keyed** (boss/quest/ritual talismans — one signature per Echo Family per high band, + named treasures) | — | — | **~22** | e.g. *Cerberus's Severed Sigil*, *The First Charm*, family GODLY/PRIMORDIAL seals; bound-on-pickup |
| **Grand total** | | | **~260** | |

Same low-authoring-cost pattern as the rest of Cerberus: ~10 archetype rows × 5 bands × a few variants, plus ~22 curated uniques.

---

### 6. Acquisition, Realm Scaling & the TalismanRegistry

#### 6.1 Acquisition

Talismans are **scribed, not forged** — a deliberate divergence from gear's smithing loop, reusing an existing utility skill as the talisman spine (mirrors how Pills hang off Alchemy, CULTIVATION_REALMS.md §4):

| Source | Governing system | Yields |
|---|---|---|
| **Inscription** (utility skill — the talisman-crafting spine) | skill level + spirit-paper/cinnabar reagents (tier-gated, inputs-gate-outputs) | generated talismans at-or-below your realm |
| **Boss / elite drops** | mob tier drop tables (pity-timer applies) | curated unique talismans, higher-band generated |
| **Echo-Family rituals** | family token sink + altar | Set-Key talismans for that family |
| **Tribulation rewards** | breakthrough completion | Ward/tribulation-resist talismans |

Charges refill via **re-inscription** (Inscription skill + reagent) or slow passive regen at an attuned altar — talismans are durable but not infinite, matching their "borrowed power" identity.

#### 6.2 The TalismanRegistry (data-driven, mirrors MobRegistry/GearRegistry)

```
plugins/Cerberus/
  talismans.yml        # archetypes, schemas, generative rules, curated uniques — the registry spine
```

A new `TalismanRegistry` (sibling to `GearRegistry`) loads `talismans.yml`, builds:
- `archetypesById: Map<String, TalismanArchetype>` — the effect-class rows.
- `talismansById: Map<String, TalismanDef>` — concrete entries.
- Secondary indexes: `byEffectType`, `byBand`, `byFamily`, and a `patternEntries` list for display-name resolution.
- Resolution is identical to `MobRegistry.resolveInternal`: **PDC `cerb_talisman_id` (O(1)) → display-name exact → pattern → UNIDENTIFIED**. PDC is authoritative; crafted/dropped talismans carry `cerb_talisman_id`, `cerb_talisman_realm`, `cerb_talisman_charges`, `cerb_talisman_unique`.

Behaviors are a `Map<String, TalismanBehavior>` keyed by `effect.type` (`OFFENSE`, `DEFENSE`, `PROC`, `SET_KEY`, `SUMMON`, `MOVEMENT`, `ECONOMY`, `SUPPRESSION`, `WARD`, `SUSTAIN`) — adding a new effect-class needs one handler class; adding a talisman using an existing class needs only config. Same generic-resolution / specific-hook split as Gear and Mobs.

**`talismans.yml` schema (mirrors `gear-types.yml`):**

```yaml
# talismans.yml
talisman-budget:                 # the single source of truth for §4
  offense-fraction: 1.0          # of realm baseline
  defense-fraction: 0.8
  sustain-fraction: 0.4
  suppression-max-delta: 2       # max Δ a talisman loadout can counter
  lifesteal-cap: 0.15
  carried-effect-mult: 0.70      # pouch-carried talismans at 70%
  same-archetype-dim-factor: 0.5 # Nth same-type = 0.5^(N-1)
realm-sockets:                   # sockets a gear piece grants, by gear realm (read into gear's talisman-sockets)
  WEAK: 0
  COMMON: 0
  UNCOMMON: 1
  RARE: 1
  EPIC: 2
  LEGENDARY: 2
  PERFECT: 2
  MYTHIC: 2
  GODLY: 3
  PRIMORDIAL: 3
realm-power-ref: gear-realm-multiplier   # reuse GEAR.md §5.1 table; do not invent a new curve

archetypes:
  proc-trigger:
    effect-type: PROC
    budget: OFFENSE              # which §4 sub-budget this draws from
    diminishes: true
    schema:
      trigger: { type: ON_CRIT, role: REQUIRED }   # ON_CRIT|ON_KILL|ON_HIT_TAKEN|ON_LOW_HP|ON_CAST
      payload: { type: CHAIN_LIGHTNING, base: 1.0, role: PRIMARY }
      charges:  { base: 8, regen-per-min: 1 }
      internal-cooldown-ticks: 40

  set-key:
    effect-type: SET_KEY
    budget: NONE                 # gates a GEAR set bonus; carries no own stat budget
    diminishes: false
    unique-per-family: true
    schema:
      family: { type: FROST, role: REQUIRED }
      gates:  { gear-set: frost-keyed, from: 0.5, to: 1.0 }   # flips keyed-set 50%-inert → full

talismans:                       # concrete entries (or generative rules)
  cinnabar-wrath-seal:
    archetype: offensive
    bands: [III, IV, V]
    effect: { type: OFFENSE, condition: VS_LOWER_REALM, magnitude-base: 0.12 }
    charges: { base: 0 }         # passive-while-socketed (no charges)
  stormcall-talisman:
    archetype: proc-trigger
    bands: [II, III, IV, V]
    effect: { type: PROC, trigger: ON_CRIT, payload: CHAIN_LIGHTNING, magnitude-base: 0.8 }
    charges: { base: 8, regen-per-min: 1 }
  # curated unique — works with zero code:
  cerberus-severed-sigil:
    archetype: suppression
    unique: true
    bands: [V]
    bound-on-pickup: true
    effect: { type: SUPPRESSION, counter-suffered-delta: 2 }
```

Adding `realm-sockets` here is what *activates* Gear's reserved field: the gear stat-aggregator asks `TalismanRegistry.socketsFor(gearRealm, gearType)` at equip time and exposes that many sockets — **Gear still never reads its own `talisman-sockets` int; the Talisman domain reads gear's realm and supplies the count.** Clean one-directional dependency (Talisman → Gear data, never the reverse).

#### 6.3 Persistence — `player_talismans` table

A new `player_talismans` table (auto-created like the existing five + `player_gear` + `player_cultivation`), following the snapshot-on-region-thread → async-write pattern via `AsyncSaveManager`:

```
player_talismans (
  uuid           TEXT,     -- owner
  talisman_id    TEXT,     -- cerb_talisman_id → TalismanRegistry
  realm          INTEGER,  -- 1..10, the talisman's own realm (for the seal rule)
  charges        INTEGER,  -- current charges (BigInteger-safe via long; charges are small)
  attach_mode    TEXT,     -- SOCKETED | CARRIED
  host_slot_id   TEXT,     -- gear slot the socket belongs to (NULL if CARRIED)
  socket_index   INTEGER,  -- which socket within the host piece (NULL if CARRIED)
  quality_json   TEXT,     -- charge-tier / craft roll / unique flag
  PRIMARY KEY (uuid, host_slot_id, socket_index)   -- one talisman per socket; carried use a synthetic pouch slot
)
```

- **Charges** persist (unlike transient gear entropy) because they are the talisman's depletion currency.
- **Folia-safety:** socket/unsocket and proc-firing happen on `PlayerInteractEvent` / `EntityDamageEvent` (region thread, inline); summons spawn owner-region-scheduled with TTL + leash (reuse the elite-despawn pattern); the budget clamp is a pure function evaluated at damage-resolve time (no scheduling).
- **Schema versioning:** ships under the `schema_version` table the Open Decisions flag mandates; a clean v1 wipe is acceptable since the HP/XP rewrite already invalidates old saves.

---

### 7. Architecture Summary (how new talismans ship without code)

1. **New talisman item using an existing effect-class** → add a `TalismanDef` (or generative rule) to `talismans.yml`. Zero code.
2. **New archetype / effect-class** → one `TalismanBehavior` handler registered into the `Map<String, TalismanBehavior>`, then config. Single small class — exactly like a new mob ability or `GearBehavior`.
3. **Re-tune the whole talisman power layer** → edit the `talisman-budget` block (fractions, dim-factor, suppression-delta). One config section; the resolve-time clamp reads it live on `/cerb reload`.
4. **Change sockets-per-realm** → edit `realm-sockets`; the gear equip GUI exposes the new count on reload (Gear's reserved field, supplied by this registry).
5. **Resolution & persistence** reuse the proven `MobRegistry`/`GearRegistry` shape: PDC O(1) lookup, atomic reload-with-fallback, async snapshot saves, Folia region-thread-safe proc firing. **Gear stays decoupled** — the dependency is one-directional (Talisman reads gear realm/socket field; Gear never reads talisman state).

---

### 8. New Open Decisions

| Priority | Decision | Why it matters | Recommended default |
|---|---|---|---|
| P1 | **Talisman budget fractions** — confirm offense `+1.0×` / defense `+0.8×` / sustain `+0.4×` of realm baseline *inside* the global 6.0× offense and 40%/75% defense caps. Is +1.0× offense too much head-room next to gear's 4.0×? | Talismans are a fourth contribution source; if the fractions are too generous a talisman loadout becomes mandatory BiS and gear/skills stop mattering. If too small, the system is cosmetic. | Ship `1.0 / 0.8 / 0.4`; the global clamp catches over-stacking. Tune down to `0.6` offense if playtest shows talismans dominating gear. |
| P1 | **Socket-swap friction** — socketed talismans swap at an *entropy* cost vs a *cooldown* vs a *currency* cost. Carried-pouch talismans swap free at ×0.70. Confirm the friction model so per-encounter swapping is deliberate, not frictionless. | Frictionless socketed swapping collapses the carried/socketed distinction and removes the "commit your build" tension. Too much friction kills the fast-twitch identity the system exists for. | Socketed swap costs gear entropy (drains the host piece); carried swaps free at 0.70. Revisit if entropy-drain feels punishing on a small server. |
| P1 | **Suppression-counter cap** — confirm talismans may reduce *suffered* realm-gap Δ by **at most 2** and may **never** raise *projected* Δ (no ganking amplifier). Does a Δ−2 counter trivialize the "distance is difficulty" wall in any band? | This is the talisman that most directly fights the signature realm-suppression mechanic. Uncapped, it breaks the "higher realm dominates" covenant; banned entirely, the cultivation-novel "defiant junior" fantasy is lost. | Cap at −2 suffered, +0 projected. A 3-realm gap stays at least a 1-realm fight — punishing but survivable with full commitment. |
| P2 | **Inscription as the crafting spine** — confirm talismans hang off a dedicated **Inscription** utility skill (vs folding into Alchemy/Enchanting). Does adding a 36th skill conflict with the "35 skills" count, or does Inscription replace/rename an existing utility skill? | The talisman economy needs a governing skill for the inputs-gate-outputs rule. A new skill changes the Mastery-Level (sum/10) math and the badge-gating count. | Reuse/rename an existing dormant utility skill rather than adding a 36th, to keep Mastery-Level math stable; if a new skill is added, update the skill-count constant and XP-curve table everywhere. |
| P2 | **Charge model** — confirm talismans are charge-gated (deplete, refill via re-inscription/altar) vs always-on. Charges make proc talismans a managed resource; always-on makes them set-and-forget. | Charges are the "borrowed power is finite" identity lever and a pacing tool, but add inventory bookkeeping on a likely-small server. | Charges on Proc/Summon/Movement/Ward archetypes only; passive-while-socketed (no charges) on Offensive/Defensive/Sustain/Economy. Refill at attuned altars + re-inscription. |
| P2 | **Set-Key vs Echo-Family attunement overlap** — Set-Key talismans flip keyed-gear sets from 50%-inert → full. Does this *replace* the family-attunement requirement, *stack* with it, or *substitute* for players of the wrong family (letting a SAND-attuned player wear Frost-keyed at full via a Frost Keystone)? | This decides whether talismans let players sidestep the deliberate Echo-Family commitment cost (token sink + cooldown) that the Families domain is built around. | Set-Key talismans *substitute* attunement at a steep token cost (the cultivation-novel "borrowed sect technique"), but only one family Keystone may be active — preserving single-family identity while allowing a costly flex. |
| P3 | **Curated-unique cap** — should account-unique talismans (`unique: true`) be limited to a small named set (~22), or can generated talismans also roll `unique`? Affects the bound-on-pickup economy and the trade surface. | Too many uniques dilutes the chase; too few makes the curated layer thin. Ties to the bind-on-pickup band decision in the main Open Decisions. | Keep `unique` curated-only (~22, bound-on-pickup); generated talismans are freely tradeable below GODLY, matching the gear bind-band default. |

---

*Files relevant to implementing this section (all absolute):* `C:\Users\Gabri\IdeaProjects\Cerb\src\main\resources\talismans.yml` (new registry config), a new `TalismanRegistry` + `TalismanBehavior` map (sibling to the planned `GearRegistry`), the `player_talismans` table in `DatabaseManager.java`, and the gear equip/stat-aggregator hook that calls `TalismanRegistry.socketsFor(gearRealm, gearType)` to read Gear's reserved `talisman-sockets` field. Balance constants belong in the `talisman-budget` block, referenced by the canonical balance-constants doc the Open Decisions recommend.
