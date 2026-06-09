# Cerberus — Gear System

*Generated 2026-06-08 (cultivation/xianxia design pass). Companion to the [master design plan](CERBERUS_DESIGN_PLAN.md). Reflects the **controlled-hyperscaling** doctrine — see [CULTIVATION_INTEGRATION.md](CULTIVATION_INTEGRATION.md) for what this overturns from the original plan.*

---

## Gear System

> *Gear is what makes a Threshold Walker's body into a weapon. Where skills are the cultivator's refined essence and talismans are borrowed external power (a **separate system** — see the Talisman domain; nothing in this section touches talisman slots, talisman charges, or talisman crafting), **Gear is the physical apparatus of cultivation**: the robes and bone-plate that armor a soul, the rings that store a realm's worth of qi, and the brutally practical tools — chainsaws, marrow-drills, leviathan harpoons — that a cultivator uses to bend the world's resources to their ascent. Gear is the single largest authored item surface in Cerberus (~250 items) and the primary delivery vehicle for the cultivation power fantasy: a PRIMORDIAL-realm body wrapped in PRIMORDIAL gear is not "20% stronger" than a mortal — it is **categorically a different kind of being**, and realm suppression makes that difference into a hard mechanical wall.*

This section assumes the upward-retargeted power doctrine: the old ~1,500 HP player cap and ~6x offense clamp are replaced by realm-banded budgets that climb into the millions, billions, and trillions, all carried by the existing **double/long Virtual Health pool** and a new **BigInteger-backed display layer**. Gear is the lever that moves a character along that curve.

---

### 1. Design Goals & Boundaries

| Goal | How Gear delivers it |
|---|---|
| **Power fantasy via apparatus** | Equipping a realm-matched set is the single biggest power jump available outside of breaking through a realm. A full GODLY set should roughly **10–40×** a player's bare-body combat budget. |
| **Open-ended, never a fixed enum** | A **data-driven Gear-Type Registry** (`gear.yml`), mirroring `MobRegistry`, defines slots, branches, archetypes, and stat schemas in config. New gear types and new equip slots ship as config, not code. |
| **Two distinct branches** | **Wearables** (worn, passive, always-on stats) and **Equipment & Tools** (wielded/activated, skill-gated, mostly utility/gathering). |
| **Cultivation scaling** | Every gear stat is `archetypeBase × realmMultiplier × variantMod × qualityRoll`, where `realmMultiplier` reuses and extends `Tier.craftStatMultiplier` into the cultivation range. |
| **Integrates with the 3-layer defense** | Wearables feed **Layer 1** (elemental resist), **Layer 2** (armor ≤ a realm-scaled cap), and **Layer 3** (defense-bar size/regen) — never a fourth bypass layer. |
| **Talisman boundary respected** | Gear has **zero** talisman slots, talisman affixes, or talisman crafting hooks. Talismans may *socket into* gear later via a clearly separated `talisman-sockets` field owned by the Talisman domain — Gear only reserves the field name; it never reads it. |

---

### 2. The Two Branches

#### 2.1 Branch A — Wearables (worn, passive)

Wearables occupy **equip slots** and contribute stats continuously while worn. Crucially, **slots are config-defined, not hardcoded** — the registry ships with the slot set below but a server can add `slot: halo` or `slot: tail` in `gear.yml` and the equip GUI renders it automatically.

**Default equip-slot layout (20 slots):**

| Slot ID | Worn item examples | Primary stat contribution | Defense layer |
|---|---|---|---|
| `head` | Helms, masks, crowns, conical hats | Armor + crit-related | L2 armor |
| `chest` | Breastplates, robes, mantles | Armor (largest share ~30%) + Max HP | L2 + HP budget |
| `legs` | Greaves, skirts, trousers | Armor + move speed | L2 |
| `feet` | Boots, sandals, sabatons | Armor + move speed + fall mitigation | L2 |
| `back` | Cloaks, wings, mantle-of-realm | Defense-bar size + glide/mobility | L3 bar |
| `hands` | Gauntlets, wraps, bracers | Attack speed + armor-pen + flat melee | offense |
| `belt` | Sashes, qi-belts, tool-belts | Mana pool + tool-belt slots | mana/utility |
| `neck` | Amulets, pendants, soul-lockets | Elemental resist % + Max HP | L1 resist |
| `ring_1` … `ring_10` | Rings — **one per finger, 10 slots** | Free-floating affix budget (offense / crit / utility / resist / mobility) — the build's flex canvas | flexible |
| `relic` | Realm-relic, dantian-anchor | Defense-bar regen + realm-suppression strength | L3 + suppression |
| `aura` | Cosmetic-functional cultivation aura | Set-bonus carrier / passive proc | varies |

Design intent: **chest/legs/feet/head** are the classic armor quad (own ~70% of the L2 armor budget combined), while **back/neck/relic** own the L1 resist and L3 defense-bar budgets, and **hands/rings/belt/aura** own offense and utility/flex budgets. This spreads the budget so no single slot is mandatory and build identity emerges from slot priorities. The **10 ring slots** (one per finger) are a deliberate flex — each ring carries a *small* per-slot budget and is individually rare, so filling all ten is an endgame-long "rings of power" chase rather than an early freebie; the total ring budget is one large flex pool split across ten chase items.

#### 2.2 Branch B — Equipment & Tools (wielded / activated)

Equipment & Tools are **functional items** — they are *not* worn for passive stats; they are **held in the main/off hand or assigned to a tool-belt slot and activated**. They are the gathering/utility spine and tie directly to the utility skills.

**Activation models (config field `activation`):**

| Activation | How it works | Folia handling |
|---|---|---|
| `HELD_USE` | Right-click while held (drills, chainsaws, harpoons). Fires on `PlayerInteractEvent` / block-break events on the region thread. | Inline on region thread; no scheduling. |
| `HELD_PASSIVE` | Held in offhand, grants a passive while held but no equip slot (e.g. a divining rod that pings nodes). | Inline. |
| `TOOLBELT` | Assigned to one of the `belt`-granted tool-belt slots; quick-swap-activated via sneak-scroll. No inventory slot consumed in combat. | `PlayerInteractEvent`, no ProtocolLib. |
| `DEPLOYABLE` | Placed in the world as a temporary entity/block (qi-forge, harvest-totem, ore-magnet). | Owner-region scheduled; TTL + leash despawn (reuse the elite-despawn pattern). |
| `CONSUMED_ON_USE` | One-shot deployables (cultivation explosives, marrow-charges). | Inline. |

**Branch B is gated by utility & gathering skills, not combat realm.** A chainsaw's harvest speed/yield scales with **Woodcutting**; a marrow-drill with **Mining**; a leviathan harpoon with **Fishing** + **Trap Mastery**. Their *durability/entropy* and *realm-tier ceiling* still follow the realm ladder (you cannot wield a PRIMORDIAL drill without the body to hold it — see §6 realm suppression on tools), but their *effectiveness* curve reads the skill, closing the gather→craft→gear loop the design plan already defines.

---

### 3. The Gear-Type Registry (open-ended architecture)

This is the heart of the "no fixed list" requirement. It mirrors `MobRegistry` exactly: config-defined entries, O(1) resolution, atomic reload-with-fallback, PDC tagging.

#### 3.1 Three config files, one manager

```
plugins/Cerberus/
  gear-slots.yml       # defines equip SLOTS (Branch A) and tool-belt capacity
  gear-types.yml       # defines gear TYPES (archetypes/schemas) — the registry spine
  gear-items.yml       # defines concrete authored ITEMS (uniques) + generative rules
```

A new `GearRegistry` (sibling to `MobRegistry`) loads all three, builds:
- `slotsById: Map<String, GearSlot>` — slots come from config; the equip GUI iterates this map, so **adding a slot is pure config**.
- `typesById: Map<String, GearType>` — the archetype/schema table.
- `itemsById: Map<String, GearItemDef>` — concrete items.
- Secondary indexes: `byMaterial`, `byBranch`, `byArchetype`, and a `patternEntries` list (for matching player-held items by display-name regex, exactly like `customNamePattern`).

Resolution at equip/use time is identical in spirit to `MobRegistry.resolveInternal`: **PDC `cerb_gear_id` tag (O(1)) → display-name exact → display-name pattern → material fallback → UNIDENTIFIED**. PDC is authoritative (crafted/dropped items carry `cerb_gear_id`, `cerb_gear_realm`, `cerb_gear_quality`), so resolution is O(1) for everything the plugin made.

#### 3.2 `GearType` schema (the extensibility unit)

A **GearType** is the equivalent of a `MobEntry`: it declares a branch, a slot (or activation), a stat schema, and which budgets it draws from. New gear concepts = new GearType entries.

```yaml
# gear-types.yml
gear-types:
  heavy-chestplate:
    branch: WEARABLE
    slot: chest
    material-base: NETHERITE_CHESTPLATE      # vanilla item used as the visual carrier
    model-data-base: 4100                    # CustomModelData range for resourcepack
    stat-schema:                             # which stat keys this type may roll
      armor:        { weight: 0.30, role: PRIMARY }
      max-health:   { weight: 0.20, role: PRIMARY }
      defense-bar:  { weight: 0.10, role: SECONDARY }
    affix-slots: { prefix: 1, suffix: 1 }    # how many random affixes
    set-eligible: true
    talisman-sockets: 0                      # reserved for Talisman domain; Gear never reads it

  marrow-drill:
    branch: EQUIPMENT_TOOL
    activation: HELD_USE
    material-base: DIAMOND_PICKAXE
    governing-skill: MINING                  # effectiveness scales with this skill
    function: { type: HARVEST, target: ORE, base-speed: 1.0, base-yield: 1 }
    stat-schema:
      harvest-speed: { weight: 0.5, role: PRIMARY }
      harvest-yield: { weight: 0.3, role: PRIMARY }
      entropy-rate:  { weight: 0.2, role: SECONDARY }
    affix-slots: { prefix: 1, suffix: 0 }

  # A future, unforeseen type — works with ZERO code changes:
  grav-tether:
    branch: EQUIPMENT_TOOL
    activation: DEPLOYABLE
    material-base: LEAD
    governing-skill: SCAVENGING
    function: { type: ITEM_MAGNET, radius-base: 8 }
    stat-schema:
      pull-radius: { weight: 0.6, role: PRIMARY }
      duration:    { weight: 0.4, role: SECONDARY }
```

The **only code that knows about specific behaviors** is a small set of `GearBehavior` handlers keyed by `function.type` (`HARVEST`, `ITEM_MAGNET`, `EXPLOSIVE`, `BUFF_AURA`, …), registered in a `Map<String, GearBehavior>`. Adding a *new behavior* needs one handler class + a config entry; adding a new *item using an existing behavior* needs only config. This is the same separation `MobRegistry` uses between resolution (generic) and the per-mob ability hooks.

#### 3.3 `GearSlot` schema (slots are data too)

```yaml
# gear-slots.yml
slots:
  chest:   { branch: WEARABLE, gui-position: 12, mandatory: false, armor-share: 0.30 }
  relic:   { branch: WEARABLE, gui-position: 25, mandatory: false, grants-suppression: true }
  halo:    { branch: WEARABLE, gui-position: 8,  mandatory: false }   # add a slot with no code
toolbelt:
  base-slots: 0                # tool-belt slots are granted by equipped `belt` items
  max-slots: 6
```

Because the equip GUI, the stat aggregator, and the persistence layer all iterate `slotsById`, **adding `halo` above immediately gives every player a halo slot** with no recompile. Player equipped gear persists in a new `player_gear` table (auto-created like the existing five) keyed by `(uuid, slot_id, gear_id, realm, quality, affix_json)`, following the snapshot-on-region-thread → async-write pattern.

---

### 4. The ~250-Item Breakdown

Items are generated by **archetype × realm-band × variant**, plus a curated **unique/keyed** layer (the cultivation-novel "named treasures"). This is the same generative approach the bestiary uses (family × archetype × tier) and keeps authoring to config.

#### 4.1 Generative math

For a generative family: `count = archetypes × realm-bands-it-appears-in × variants`. Realm-bands group the 10 tiers into **5 cultivation bands** for authoring sanity (each band = 2 tiers), so most generative gear spans the band it drops in plus naturally upgrades into the next.

**Cultivation realm bands (gear authoring grouping):**

| Band | Tiers | Cultivation name | HP-budget order of magnitude (geared, endgame) |
|---|---|---|---|
| Band I | WEAK, COMMON | Body Tempering | hundreds → low thousands |
| Band II | UNCOMMON, RARE | Qi Condensation | tens of thousands |
| Band III | EPIC, LEGENDARY | Foundation / Core | hundreds of thousands → millions |
| Band IV | PERFECT, MYTHIC | Nascent Soul | tens of millions → hundreds of millions |
| Band V | GODLY, PRIMORDIAL | Realm Sovereign | billions → trillions+ |

#### 4.2 Branch A — Wearables (~150 items)

| Group | Archetypes | Bands | Variants | Items | Notes |
|---|---|---|---|---|---|
| **Armor quad sets** (head/chest/legs/feet) | 4 pieces × 3 weight-classes (Light/Medium/Heavy) | — | 5 realm bands | **60** | A "set" = 4 pieces; 3 weights × 5 bands = 15 sets = 60 pieces. Light→defense-bar/speed, Heavy→armor/HP. |
| **Backs / cloaks** | 3 (Cloak, Wing, Mantle) | 5 | — | **15** | Wing variants grant glide; Mantle carries set bonuses. |
| **Hands / gauntlets** | 2 (Gauntlet=armor, Wrap=attack-speed) | 5 | — | **10** | Hands own the armor-pen + flat-melee budget. |
| **Necks / amulets** | 2 (Resist-amulet, HP-locket) | 5 | — | **10** | The elemental-resist (L1) carriers. |
| **Rings** | 6 (Offense, Crit, Utility, Resist, Mobility, Suppression) | 5 | — | **30** | **10 ring slots (one per finger)** — small per-ring budget, individually rare; filling all 10 is an endgame chase. |
| **Belts / sashes** | 2 (Qi-belt=mana, Tool-belt=slots) | 5 | — | **10** | Tool-belt grants Branch-B activation slots. |
| **Relics** | 2 (Aegis-relic=bar regen, Sovereign-relic=suppression) | 5 | — | **10** | The realm-suppression carrier (see §6). |
| **Curated keyed/unique wearables** | — | — | — | **~20** | One per Echo Family per high band + named treasures (e.g. *Hoarfrost Monarch's Carapace*, *Mantle of the Sand Sovereign*). Bound-on-pickup. |
| **Wearables subtotal** | | | | **~165** | |

#### 4.3 Branch B — Equipment & Tools (~100 items)

| Group | Examples | Governing skill | Bands | Items | Notes |
|---|---|---|---|---|---|
| **Gathering power-tools** | Chainsaw, Marrow-Drill, Hewing-Maul, Reaper-Scythe | Woodcutting / Mining / — / Farming | 5 | **40** | 8 tool-types × 5 bands. `HELD_USE` harvest behavior; speed/yield scale with the governing skill. |
| **Hunting / fishing equipment** | Leviathan Harpoon, Snare-Launcher, Beast-Lure, Deep-Net | Trap Mastery / Fishing | 5 | **20** | 4 types × 5 bands. Tie into the trap-as-hunting pillar and Sea Monster apexes. |
| **Deployables** | Qi-Forge, Harvest-Totem, Ore-Magnet (Grav-Tether), Ward-Stake | Scavenging / Survival / Engineering | 5 | **20** | `DEPLOYABLE`; TTL + leash despawn, per-player + per-chunk cap. |
| **Utility / mobility tools** | Cloud-Stepper (dash), Earth-Render (tunnel bore), Divining Rod, Marrow-Charge (explosive) | Navigation / Survival | 5 | **20** | Mix of `TOOLBELT` and `CONSUMED_ON_USE`. |
| **Curated unique tools** | *Cerberus's Own Fang* (harvests any node at PRIMORDIAL speed), *The First Drill* | — | — | **~5** (folded into above counts via uniques) | Boss/quest only. |
| **Equipment & Tools subtotal** | | | | **~100** | |

**Grand total: ~165 + ~100 = ~265 gear items** (plus the **Mounts & Vehicles** line — see [MOUNTS_AND_VEHICLES.md](MOUNTS_AND_VEHICLES.md)), almost entirely generated from a handful of archetype rows × 5 bands, plus ~20 curated uniques — the same low-authoring-cost pattern the rest of Cerberus uses.

---

### 5. Cultivation Scaling (the absurd-numbers engine)

#### 5.1 The realm multiplier (extends `Tier.craftStatMultiplier`)

The current `Tier.craftStatMultiplier` runs ~0.5×→4.0×. For the cultivation power fantasy this is **far too compressed** — a 4× spread across 10 tiers cannot express "a higher realm hard-dominates a lower one." Gear introduces a separate, dramatically steeper **`gearRealmMultiplier`** read from `gear.yml` (so the mob/skill ladders are untouched). The recommended curve is **geometric with a per-band acceleration**, hitting the cultivation orders of magnitude:

| Tier | gearRealmMultiplier (recommended) | Order of magnitude vs COMMON |
|---|---|---|
| WEAK | 0.4 | — |
| COMMON | 1 | 1× (anchor) |
| UNCOMMON | 4 | 4× |
| RARE | 18 | ~10¹ |
| EPIC | 90 | ~10² |
| LEGENDARY | 500 | ~10² |
| PERFECT | 3,200 | ~10³ |
| MYTHIC | 24,000 | ~10⁴ |
| GODLY | 220,000 | ~10⁵ |
| PRIMORDIAL | 2,600,000 | ~10⁶ |

So a PRIMORDIAL chestplate carries **~2.6 million×** the stat budget of a COMMON one. Stacked across 20 slots + set bonuses + temper + affixes, a fully-equipped PRIMORDIAL cultivator's HP/defense/offense budget lands in the **billions-to-trillions**, exactly the target. This is **intentional, tunable (one config table), and representable** — see §5.3.

#### 5.2 The full stat formula

```
finalStat = archetypeBase
          × gearRealmMultiplier[tier]
          × variantMod                     # weight-class / archetype variant (0.7–1.4)
          × qualityRoll                     # 0.85–1.15 craft roll (luck-gated)
          × (1 + temperBonus)              # temper: +0.08 per step, cap +3 tiers worth
          + affixFlat                       # additive affix contributions
```

All arithmetic is done in **`double`** for intermediate math and stored as **`long`** where the result is an integer-like pool (HP, defense-bar). Because PRIMORDIAL endgame can exceed `long` when totals are multiplied by Threat-tier in instanced content, the **display + comparison layer uses `BigInteger`** (the engine already proves Virtual Health can hold gigantic doubles; Gear formalizes the long/BigInteger boundary so nothing silently overflows — this is the *intentional* analogue of killing the accidental `100·2^level` XP overflow).

#### 5.3 Number representation & display

| Magnitude | Storage | Display format |
|---|---|---|
| < 2⁵³ (HP, bar, most stats) | `double`/`long` | grouped digits then suffix at ≥10,000 |
| ≥ millions | `long` | `1.2M`, `3.4B`, `7.8T`, `9.1Qa` (short-scale) |
| Beyond `long` (Threat-scaled raids, theoretical PRIMORDIAL stacks) | `BigInteger` | xianxia units: **Tempered (10³) → Condensed (10⁶) → Cored (10⁹) → Nascent (10¹²) → Sovereign (10¹⁵) → Primordial (10¹⁸+)** |

A `CultivationNumberFormat` util (pure function, unit-tested like `MobHealthScaling`) is the single formatter used by the HUD, item lore, and floaty combat numbers, so "2,600,000,000" always renders as `2.6B` (or `2.6 Cored`) everywhere.

#### 5.4 Set bonuses

Sets are config (`gear-items.yml` `set:` blocks). Bonuses are **multiplicative on the realm budget** so they scale with the absurd curve rather than adding trivial flat amounts:

| Set | 2-piece | 4-piece | 6-piece (full + relic + aura) |
|---|---|---|---|
| **Heavy Sovereign (GODLY)** | +15% armor cap | +25% Max HP | **Realm Bulwark:** defense-bar overflow that *would* spill to HP is reduced 40%, and armor cap raised from 40%→55% |
| **Frost-Keyed (FROST family)** | +20% ICE resist | reflect 10% chill | **Absolute Hoar:** attackers below your realm are *suppressed* (see §6) an extra band |
| **Drifter (Light, any band)** | +12% move speed | +20% defense-bar regen | **Windwalk:** out-of-combat bar regen window drops 7s→4s |

Keyed-set bonuses are **partially inert for non-attuned players** (recommend 50% effect) so family commitment matters without being a brick wall — matching the Open Decisions default.

---

### 6. Realm Suppression — gear's signature cultivation mechanic

This is the mechanical expression of "a higher realm hard-dominates a lower one," and **gear is its primary carrier** (via the `relic` slot, `grants-suppression` slots, and keyed-set 6-piece bonuses).

**Rule:** combat between two entities computes a **realm gap** `Δ = attackerEffectiveRealm − defenderEffectiveRealm` (effective realm = body cultivation realm blended with worn-gear average realm). Suppression applies a multiplier to *both* the lower party's outgoing damage and incoming mitigation:

| Realm gap Δ (tiers) | Effect on the lower-realm party |
|---|---|
| 0–1 | none (normal fight) |
| 2 | outgoing damage ×0.5, their mitigation vs the higher party ×0.7 |
| 3 | outgoing ×0.2, mitigation ×0.4 |
| 4 | outgoing ×0.05, mitigation ×0.15 |
| 5+ | **Total Suppression** — outgoing ≈0 (chip only), defense bar cannot regen in the higher party's presence; the lower being is "outclassed the moment they walk into the room." |

- **PvE:** a level-100 PRIMORDIAL-banded mob deep in The Maw suppresses an under-geared intruder, enforcing the distance-is-difficulty covenant *mechanically*, not just via raw HP. This is the cleaner alternative to pure HP-sponging the design plan worried about.
- **PvP:** suppression is what makes "ganking down" feel like a god swatting a fly — and is governed by the same realm-gap so the existing PvP damage scalar still applies on top. (Edge-protection / level-bracketed PvP from the World domain keeps this from being pure misery.)
- **Suppression is gear-counterable:** a `Sovereign-relic` or the Frost-Keyed 6-piece *reduces the Δ you suffer* by 1–2, the cultivation-novel trope of "a defiant junior with a treasure that lets them punch above their realm." This gives the `relic` slot a clear identity and a reason to chase.

---

### 7. Integration with the 3-Layer Defense & power curve

Gear feeds the **existing** pipeline — it never adds a bypass layer:

| Layer | Gear contribution | Cap behavior |
|---|---|---|
| **L1 Elemental Resist** | `neck`/`back` resist %, keyed-set resist | Per-element resist still clamps (recommend ≤75% even at PRIMORDIAL); realm raises the *floor* toward the cap faster, never past it. |
| **L2 Armor** | `head/chest/legs/feet/hands` armor pts | Armor mitigation cap stays a % (default 40%, raised to **55% only via the Heavy Sovereign 6-piece**). Mob `armorPenetration` (already on `Tier`) is wired into the mob→player path so high-realm mobs ignore most of this — keeping elites lethal without uncapped damage. |
| **L3 Defense Bar** | `back`/`relic` grant **bar size + regen**; bar size scales on the full realm multiplier (so endgame bars are in the millions/billions and *can* absorb a sustained cultivation fight) | The bar remains the TTK heartbeat. Bigger realm gear = bigger bar, not a new layer. Overflow still spills to Virtual Health. |
| **Virtual Health** | `chest`/`neck`/HP-locket Max-HP additions | The capped-budget rule is retargeted upward per realm band (Body Tempering → hundreds; Realm Sovereign → trillions). No `× level` term; HP comes from gear realm multiplier × archetype base, hard-clamped per band so a bug can't exceed the band ceiling. |

**Entropy durability** applies to all gear: using a tool or taking hits drains entropy; at full entropy a piece floors at ~50% power (Nomicons/curated uniques may be entropy-immune per Open Decisions). This makes the gather→craft→repair loop permanent and paces the absurd-number climb.

---

### 8. Architecture summary (how new types/slots ship without code)

1. **Add a new equip slot** → add one entry to `gear-slots.yml`. The equip GUI, stat aggregator, and `player_gear` persistence all iterate `slotsById`, so the slot appears live on `/cerb reload`.
2. **Add a new gear *type*** → add a `GearType` entry to `gear-types.yml` referencing an existing `function.type` behavior and an existing slot/activation. Zero code.
3. **Add a new gear *item*** → add a `GearItemDef` (or a generative rule) to `gear-items.yml`; it inherits its type's schema and the realm multiplier table.
4. **Add a genuinely new *behavior*** (e.g. a brand-new tool action) → one `GearBehavior` handler class registered into the `Map<String, GearBehavior>`, then config as above. This is the *only* case needing code, and it's a single small class — exactly like adding a new mob ability hook.
5. **Resolution & persistence** reuse the proven `MobRegistry` shape: PDC `cerb_gear_id`/`cerb_gear_realm`/`cerb_gear_quality` tags → O(1) `itemsById` lookup, atomic reload-with-fallback, async snapshot saves, Folia region-thread-safe activation. The **Talisman system stays entirely separate**; Gear only reserves an unread `talisman-sockets` field so the two domains can later interoperate without coupling now.
