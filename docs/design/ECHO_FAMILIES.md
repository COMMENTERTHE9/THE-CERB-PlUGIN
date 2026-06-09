# Echo Families & Orders

*Generated 2026-06-08. Consolidation pass — folds the scattered family references (bestiary [CERBERUS_DESIGN_PLAN.md §Mobs.2](CERBERUS_DESIGN_PLAN.md), boss/ritual themes §Bosses.3/§Bosses.5, realm-band themes [CULTIVATION_REALMS.md §1](CULTIVATION_REALMS.md), keyed armor §Gear, mount lines [MOUNTS_AND_VEHICLES.md](MOUNTS_AND_VEHICLES.md)) into ONE player-facing **faction system**. Western-forward naming per [NAMING.md](NAMING.md); realm/suppression per [POWER_SCALING.md](POWER_SCALING.md). Data-driven registry per the `MobRegistry`/`GearRegistry` pattern. This is the player-facing wrapper around the cultivation **Sect** layer already sketched in [CULTIVATION_REALMS.md §6.4](CULTIVATION_REALMS.md).*

---

## 0. Doctrine & Scope

The five **Echo Families** (SAND, TIDE, ASH, FROST, MIRE) currently exist as four disconnected slices: mob factions (bestiary), boss/ritual themes (bosses), realm-band themes (realms), and mount lines (camel→SAND, strider→ASH). This section makes them **one system with one persistence row, one attunement track, one ritual verb, and one cross-reference matrix** — the player's primary *commitment axis* (which-am-I), orthogonal to Realm (how-strong-am-I) and Skills (what-can-I-do).

- **Family ≡ faction ≡ cultivation Sect.** [CULTIVATION_REALMS.md §6.4](CULTIVATION_REALMS.md)'s "Sects" and the bestiary's "Echo Families" are **the same object** — `sect_id` in `player_cultivation` IS the family id. No second system.
- **Open-ended registry.** Families are a `family.yml` registry (`EchoFamilyRegistry`, sibling to `MobRegistry`/`GearRegistry`); five ship by default but a server can author a sixth. Every cross-referenced object (mob, boss, armor set, mount line, ritual, talisman, technique) carries a `family:` field; the family entry is the **join key**, not hard-coded.
- **One behavior map.** Family-specific verbs (aura buffs, ritual triggers, war scoring) route through a `Map<String, FamilyBehavior>` keyed by `effect.type` — new behavior = one handler + config, exactly the GearBehavior/MountAbility separation.

---

## 1. The Five Families — Canonical Identity Matrix

The single structural source of truth. Every column already exists *somewhere* in the docs; this consolidates them into one row per family.

| Field | **SAND** | **TIDE** | **ASH** | **FROST** | **MIRE** |
|---|---|---|---|---|---|
| **Element(s)** | EARTH (+WIND elites) | ICE-as-water + LIGHTNING | FIRE (+DARK elites) | ICE | DARK (+BINDING elites) |
| **Realm-band theme** | R2–R3 (Awakened/Adept), Bands 1–2 | R4 (Champion), Band 3 | R5 + R8 (Ascendant/Ashen Lord), Bands 4 & 7 | R6 (Paragon), Band 5 | R7 + R9 (Mirebound/Maw-Eater), Bands 6 & 8 |
| **Biomes / Zone** | Deserts, badlands, savanna, mesas | Oceans, rivers, beaches, swamp-edge | Nether-adjacent, volcanic, deep caves | Snowy peaks, frozen ocean, tundra | Swamps, dark forest, deep dark, mushroom |
| **Signature mechanic** | Burrow-ambush, sandstorm slow, **armor-shred** | Pull/displace, **Wet** (+30% LIGHTNING/−25% FIRE), drown pressure | Ignite DoT, explode-on-death, **regen-suppress heat aura** | Freeze-stack→root, **Brittle** (+dmg vs frozen), shatter | Poison/wither DoT, **root/bind**, spore-heal, life-leech |
| **Family token** (canonical — see §11 D1) | **Glass Tear** | **Brine Pearl** | **Cinder Mote** | **Rime Crystal** | **Spore Husk** |
| **Signature mobs** (rep.) | Dust Mite, Husk Drifter, Glasswing Reaver, Sand Wraith | Tide Nipper, Brine Lurker, Maelstrom Caller, Drowned Echo | Cinder Hound, Ashen Brute, Pyre Colossus, Emberling Swarmcaller | Frost Wisp, Rime Sentinel, Glacial Warden | Mire Spitter, Spore Warden, Bog Tyrant, Voidtouched Stalker |
| **Herald** (farmable, no-lockout, token source) | Grit-Herald Khamûn (EPIC) | *(Tide herald)* | *(Cinder herald)* | *(Frost herald)* | *(Mire herald)* |
| **Family boss** (ritual-summoned) | Dune-Matriarch Sa'irah (LEGENDARY) | Tide-Caller Nessuvar (LEGENDARY) | Cinder-Patriarch Vol'Khar (PERFECT) | Frost-Empress Lysithea (MYTHIC) | Bog-Mother ULGRA (MYTHIC) |
| **Keyed armor set** | Sand-Keyed (Glasshide) | Tide-Keyed (Drownward) | Ash-Keyed (Emberplate) | Frost-Keyed (Rimeguard) | Mire-Keyed (Rotweave) |
| **Mount line** | Camel→Sand-Strider→Glass-Qilin | Hippocamp→Tide-Serpent→Maw-Tide Leviathan | Strider→Ember Wyrmling→Embersoul Drake | Rimeheart Stag→Frost Roc | Mire-Stalker→Bog-Drake |
| **Ritual** | Rite of the Glassing | Rite of the Drowned Tide | Rite of the Cinder-Pyre | Rite of Absolute Cold | Rite of the Sump |
| **Family talisman line** | Glass-Tear talismans (armor/dodge) | Brine talismans (water-walk/mana) | Cinder talismans (ignite/fire-immune) | Rime talismans (slow-immune/freeze) | Hollow talismans (void-resist/blink) |
| **Physique** ([CULTIVATION_REALMS.md §6.3](CULTIVATION_REALMS.md)) | Glass-Bone | Drowned-Lung | Ashen-Heart | Rimebound | Hollow-Veined |
| **Color / particle** | Tan/gold dust | Aqua bubbles | Red/orange ember | Aqua/white snow | Dark-gray/green murk |

> **Multi-band families (intentional):** ASH and MIRE each theme **two** realm bands (ASH = R5 Embersoul + R8 Ashen Lord; MIRE = R7 Mirebound + R9 Maw-Eater) per [CULTIVATION_REALMS.md §1](CULTIVATION_REALMS.md). This is not a conflict — it means those families have a **low-band and a high-band roster/boss tier**, giving them the longest progression spine. The Convergence (R10) is **all five**.

---

## 2. Membership & Attunement

### 2.1 Joining a family

| Step | Mechanic | Gate |
|---|---|---|
| **Discover** | Kill any family mob / enter its biome → family becomes *known* (codex entry). | none |
| **Pledge** | At a **family altar** (a DEPLOYABLE/world structure), spend a small token offering to set `sect_id`. First pledge is **free** at R2 (Awakened — the realm you can first cultivate). | R2 Awakened |
| **Attune** | Thereafter, accrue **Attunement** (the family rep track) by same-family kills, rituals, and altar meditation. | being pledged |

- **Default policy: single-family membership** (`sect_id` is one value). This is the *commitment* the design plan calls "the meta-progression decision" ([CERBERUS_DESIGN_PLAN.md §Overview.24](CERBERUS_DESIGN_PLAN.md)). See §11 D2 for the multi-membership open decision.
- Pledging does **not** lock you out of fighting other families — it gates *full-power keyed armor*, *which altar gives you Stable Breakthroughs*, and *which Family War side you fight on*.

### 2.2 The Attunement / Reputation track

A per-family `0–100` attunement level on a tier ladder (data-driven thresholds in `family.yml`). Only your **pledged** family's attunement is "active"; other families decay toward a floor.

| Attunement tier | Name | Unlocks |
|---|---|---|
| 0–9 | **Stranger** | codex only |
| 10–24 | **Initiate** | wear UNCOMMON keyed pieces at full power; light Lesser-Bottleneck cleanse with 1 token |
| 25–44 | **Adherent** | RARE keyed set bonus; family Technique tier 1 (Manual comprehension, [CULTIVATION_REALMS.md §6.1](CULTIVATION_REALMS.md)) |
| 45–64 | **Sworn** | EPIC keyed set bonus; family talisman crafting; family mount line tameable |
| 65–84 | **Vanguard** | LEGENDARY keyed; Stable Breakthrough at this family's altar; outpost-capture eligible |
| 85–99 | **Exarch** | GODLY keyed; family Technique tier 2; can light altars for lower-realm sect-mates (mentor buff) |
| 100 | **Patriarch / Matriarch** | full set + named talisman; seasonal-standings title; projects a **family-aura mentor field** ([CULTIVATION_REALMS.md §6.4](CULTIVATION_REALMS.md)) |

- **Attunement is realm-independent** — it is a *loyalty* axis, not a power axis. A R10 player can be an Initiate of a family they just switched to (and their keyed gear is sealed until they re-earn rep — the anti-flip-flop teeth).
- Attunement gain rate scales with **same-band kills** (killing your family's mobs *in their band* gives full rep; off-band gives a fraction), tying the rep grind back to distance-is-difficulty.

### 2.3 Switching cost (consolidated)

Resolves the [OPEN_DECISIONS.md L138](OPEN_DECISIONS.md) flag with a concrete default:

| Lever | Default | Rationale |
|---|---|---|
| **Token sink** | Forfeit 100% of *current* family tokens + pay a flat **Spirit-Stone re-attunement fee** (Band-scaled) | makes switching a real sacrifice, not free re-rolling |
| **Cooldown** | **7-day** real-time cooldown between switches | per [OPEN_DECISIONS.md L138](OPEN_DECISIONS.md) suggestion |
| **Attunement loss** | New family starts at **Stranger (0)**; old family attunement **frozen at half** (not wiped — return is cheaper than first-pledge) | preserves some investment, discourages churn |
| **Keyed gear** | Old family's keyed set bonuses **deactivate** (the `cerb_keyed_family` set-bonus gate, [CERBERUS_DESIGN_PLAN.md §Gear](CERBERUS_DESIGN_PLAN.md)); pieces keep their +20% base budget but lose the bonus until re-attuned | the structural enforcement — no code change, the existing keyed-attunement check already does this |
| **Partial refund** | 25% of the Spirit-Stone fee refunded if returning to a previously-pledged family within 30 days | "prodigal son" softener (tunable; see §11 D2) |

---

## 3. Cross-Reference Matrix — Family → Everything

The clean structural join table (the consolidation deliverable). Every row is one family; every column is a system that keys off it.

| Family | Element(s) | Band(s) / Realm | Signature mob (boss-tier) | Family Boss | Keyed Set | Mount line apex | Ritual | Token |
|---|---|---|---|---|---|---|---|---|
| **SAND** | EARTH/WIND | B1–2 / R2–R3 | Dune Leviathan (PERFECT) | **Dune-Matriarch Sa'irah** | Glasshide | Glass-Qilin | **Rite of the Glassing** | Glass Tear |
| **TIDE** | ICE-water/LIGHTNING | B3 / R4 | Abyssal Sovereign (MYTHIC) | **Tide-Caller Nessuvar** | Drownward | Maw-Tide Leviathan | **Rite of the Drowned Tide** | Brine Pearl |
| **ASH** | FIRE/DARK | B4 & B7 / R5 & R8 | Infernal Archon (MYTHIC) | **Cinder-Patriarch Vol'Khar** | Emberplate | Embersoul Drake | **Rite of the Cinder-Pyre** | Cinder Mote |
| **FROST** | ICE | B5 / R6 | Hoarfrost Monarch (GODLY) | **Frost-Empress Lysithea** | Rimeguard | Frost Roc | **Rite of Absolute Cold** | Rime Crystal |
| **MIRE** | DARK/BINDING | B6 & B8 / R7 & R9 | Rotcrown Hierophant (GODLY) | **Bog-Mother ULGRA** | Rotweave | Bog-Drake | **Rite of the Sump** | Spore Husk |
| **(all 5)** | cycles all 6 | B9 / R10 | — | **The Convergence** (GODLY) → **Warden of Cerberus** (PRIMORDIAL) | Mantle of the Nine (counts-as-4 any set) | — | **Rite of Convergence** (5 Nomicons) | all five |

---

## 4. Family Rewards

### 4.1 Keyed armor (the marquee reward)

Already specified in [CERBERUS_DESIGN_PLAN.md §Gear](CERBERUS_DESIGN_PLAN.md) — this consolidates the rule:

- `cerb_keyed_family` PDC binds a piece to one family; keyed pieces get **+20% stat budget** but the **set bonus only activates for players attuned to that family** (`sect_id == cerb_keyed_family`).
- Acquired **only** via that family's ritual boss + Echo Family ritual drops (no crafting the set from scratch — the boss is the gate).
- One set per family across 6 armor slots; the 5 sets together are the hard requirement for **The Convergence** (each family's Convergence phase DoT is only survivable in its matching keyed set — [CERBERUS_DESIGN_PLAN.md §Bosses.6](CERBERUS_DESIGN_PLAN.md)).
- **Mantle of the Nine** (PRIMORDIAL chest, [CERBERUS_DESIGN_PLAN.md L898](CERBERUS_DESIGN_PLAN.md)) counts as 4-piece of whatever family set you wear the rest of — the cross-family capstone.

### 4.2 Family Techniques (built on Manuals/Nomicons)

A **family-flavored Manual** drops from that family's boss; comprehending it ([CULTIVATION_REALMS.md §6.1](CULTIVATION_REALMS.md), requires realm ≥ manual realm + Qi cost) grants a Technique. Gated by attunement tier (§2.2).

| Family | Technique T1 (Adherent) | Technique T2 (Exarch) |
|---|---|---|
| SAND | *Glass-Step* (dash leaves shatter-tiles) | *Vitrify* (root + armor-shred a target) |
| TIDE | *Tide-Pull* (yank a foe, apply Wet) | *Maelstrom* (AoE pull + drown-pressure zone) |
| ASH | *Cinder-Brand* (ignite-on-hit, fire immune) | *Pyre-Collapse* (delayed explode-on-self-dmg nova) |
| FROST | *Rime-Lock* (freeze-stack accelerator) | *Absolute Zero* (freeze own defense-bar = invuln window) |
| MIRE | *Spore-Veil* (brief invis + cleanse) | *Entropy Touch* (live-corrode a target's gear) |

Techniques hook the combat pipeline at defined insertion points (like Defensive Magic). They are **family-keyed** — you can only comprehend your pledged family's line at full effect.

### 4.3 Family talismans & mounts

- **Talismans** are a **separate system** that realm/family only *consumes* (the reserved-but-unread `talisman-sockets` field in GEAR is NOT this — see [GEAR.md](GEAR.md)). Each family has a talisman *line* (§1 row) crafted at Sworn+ attunement; family talismans grant that family's signature defensive verb (Glass-Tear → +armor/−dodge; Brine → water-walk; etc.). Maps 1:1 onto the Physique flavors so there's no new element math.
- **Mounts**: each family owns a **mount evolution line** ([MOUNTS_AND_VEHICLES.md §4.2](MOUNTS_AND_VEHICLES.md) `ascends-to:` chains). Family mounts are tameable at **Sworn** attunement and bond faster for attuned riders. The vanilla-OG seeds (camel→SAND, strider→ASH) are the **bottom rung** of these lines; the apex (Glass-Qilin, Maw-Tide Leviathan) drops from the family boss. `MountSpecies.echoFamily` (the existing `byEchoFamily` index) is the join.

---

## 5. Rituals — the Consolidated Summon Verb

**One ritual mechanic, five reagent recipes.** This consolidates the scattered summon hooks ([CERBERUS_DESIGN_PLAN.md §Bosses.5](CERBERUS_DESIGN_PLAN.md), §Mobs.8) into a single `RitualManager` driving a single `family.yml` ritual schema.

### 5.1 The ritual loop (identical for all families)

```
1. Build an ALTAR (family DEPLOYABLE) in/near the family's biome-band.
2. CHARGE it via any of:
     - tokens + family reagent (the canonical path)
     - the family's "echo charge" auto-fill (kill N same-family mobs nearby)
     - a Cerberus Trap baited with a family mob (Trap-Mastery shortcut)
3. IGNITE → spawns the family BOSS at the altar (level/tier set by altar's distance-band).
4. KILL → boss drops keyed-armor pieces (20h per-player lockout) + tokens (no lockout) + family Nomicon.
```

### 5.2 Ritual schema (data-driven)

```yaml
# family.yml
families:
  ASH:
    element: [FIRE, DARK]
    bands: [4, 7]                       # multi-band family
    token: cinder-mote
    boss: cinder-patriarch-volkhar
    keyed-set: emberplate
    mount-line: ember-drake-line
    ritual:
      id: rite-of-the-cinder-pyre
      altar-block: MAGMA_BLOCK
      charge:
        token-cost: 12                  # Cinder Motes
        reagent: { item: MAGMA_CORE, qty: 1 }
        echo-charge-kills: 40           # alt: kill 40 ASH mobs nearby
        trap-bait: true
      summon: { boss: cinder-patriarch-volkhar, scale: ALTAR_DISTANCE_BAND }
      lockout: { keyed-drop-hours: 20, token-drop: NONE }
    attunement:
      gain-per-same-band-kill: 1.0
      gain-per-ritual: 25
      decay-floor: 10                   # when un-pledged
    behavior-refs: [ ASH_AURA_BUFF, ASH_WAR_SCORE ]
```

- **Convergence** is the same verb scaled up: consume one completed Nomicon from **each** family at the Echo Nexus (no per-family altar). **Warden** requires the Convergence-drop Warden Key + 8 synced players.
- **Folia-safe**: altar charge ticks on the altar's region scheduler; the boss spawns and leashes within one region; boss holds only UUIDs and re-resolves on its own thread ([CERBERUS_DESIGN_PLAN.md §Bosses-Folia](CERBERUS_DESIGN_PLAN.md)).

---

## 6. Family Wars & PvP

The endgame social layer — turns family membership into a **persistent inter-family conflict** with territory, scoring, and seasons. Built on the realm-bracketed PvP rail ([CULTIVATION_REALMS.md §5.2](CULTIVATION_REALMS.md)) so it never becomes realm-gank-fest.

### 6.1 Outpost control

| Concept | Mechanic |
|---|---|
| **Outpost** | A capturable world structure in contested bands (one per band-zone, neutral by default). |
| **Capture** | A Vanguard+ player of family X channels at the outpost; contesting players of other families interrupt. Capture flips ownership to family X. |
| **Hold benefit** | Owning family gets: a band-local **attunement-gain buff**, an altar that gives **Stable Breakthroughs** to owners, and a **token-yield bonus** on same-band kills. |
| **Suppression rule** | Capture channel is **realm-bracketed** — only ±1 realm can meaningfully contest, so an outpost is fought by peers, not flattened by a passing Primordial (the suppression-DR-cap + bracket protection from [CULTIVATION_REALMS.md §5.2](CULTIVATION_REALMS.md)). |

### 6.2 Inter-family PvP & seasonal standings

| System | Default |
|---|---|
| **Family-vs-family flag** | Attacking another *pledged* player of a rival family in a contested zone is **always full-PvP**; same-family is duel-only (opt-in). |
| **War score** | Outpost holds, rival-family kills (realm-bracketed), and ritual-boss races feed a per-family **War Score** (the `*_WAR_SCORE` behavior handler). |
| **Season** | War Score accumulates over a **season** (e.g. monthly); end-of-season **family standings leaderboard**; top family gets a cosmetic banner + a server-wide minor buff for its members next season. |
| **Persistence** | War Score is a per-family server-global counter (`echo_war` table, §7); individual contribution tracked per player for personal-standings titles. |
| **Anti-grief** | Intra-band brackets + the no-loot-3-realms-down rule ([CULTIVATION_REALMS.md §5.2](CULTIVATION_REALMS.md)) carry over verbatim; outpost-flip has a cooldown to prevent flip-spam. |

---

## 7. Persistence & Config Schema

### 7.1 Tables (reuse the cultivation persistence pattern, [CULTIVATION_REALMS.md §7](CULTIVATION_REALMS.md))

Family membership is **not a new table** — it folds into the existing `player_cultivation.sect_id` + `attunement_json`. Two small global tables are added for Wars.

```sql
-- player_cultivation (EXISTING — sect_id IS the family id; attunement_json holds per-family rep)
--   sect_id          TEXT     -- pledged family id ('ASH'), or NULL
--   attunement_json  TEXT     -- {"ASH": 72, "FROST": 18, ...} per-family rep (active + decaying)
--   physique_id      TEXT     -- family-flavored physique (CULTIVATION_REALMS §6.3)

-- player_echo_meta (NEW — switch bookkeeping)
--   uuid, last_switch_at (epoch), prev_family, switch_count, season_war_contribution

-- echo_war (NEW — server-global, per-family-per-season)
--   family_id, season_id, war_score, outposts_held, PRIMARY KEY (family_id, season_id)

-- echo_outpost (NEW — territory state)
--   outpost_id, band, owner_family, captured_at, region_key
```

- Follow the **snapshot-on-entity-thread → async I/O → reschedule** pattern via `AsyncSaveManager`.
- Ships in the **schema_version** versioned-migration batch alongside `player_cultivation` (Step 6.5).
- `attunement_json` keyed by family id keeps it open-ended — a 6th family adds a JSON key, no migration.

### 7.2 Config files

```
plugins/Cerberus/
  family.yml          # the EchoFamilyRegistry spine: element, bands, token, boss,
                      #   keyed-set, mount-line, ritual schema, attunement curve, behavior-refs
  family-rituals.yml  # (optional split) per-family altar/charge/summon recipes
  family-wars.yml     # outpost definitions, season length, war-score weights, standings rewards
```

`EchoFamilyRegistry` builds: `byId`, `byElement`, `byBand`, `byToken`, `byBossId`, `byKeyedSet`, `byMountLine` — the same multi-index resolution as `MobRegistry`. Family-specific verbs dispatch through `Map<String, FamilyBehavior>` (`*_AURA_BUFF`, `*_WAR_SCORE`, `*_RITUAL_TRIGGER`, `*_MENTOR_FIELD`).

---

## 8. Architecture Summary (how a new family ships without code)

1. **Add a family** → one `family.yml` entry referencing existing element(s), an existing boss id, keyed-set id, mount-line id, and behavior-refs. **Zero code.**
2. **Add a ritual** → a `ritual:` block on the family entry (altar block, charge recipe, summon target). Reuses `RitualManager`. **Zero code.**
3. **Add a keyed set / mount line / talisman line** → author in the respective registry with `family:` set to the family id; the `byKeyedSet`/`byMountLine` join indexes pick it up. **Zero code.**
4. **Add a genuinely new family verb** (e.g. a unique War objective) → one `FamilyBehavior` handler + config. **Only** case needing code.
5. **Persistence** is `sect_id` + `attunement_json` (existing row) + three small Wars tables — no per-family schema.

---

## 9. Build-Order Placement (existing 9-step plan)

| Step | Echo Families work |
|---|---|
| **0 — Hygiene** | Reserve `family.yml`; reconcile the token-name inconsistency (§11 D1). |
| **4 — ZoneManager / BandResolver** | Family→biome→band mapping ([OPEN_DECISIONS.md L128](OPEN_DECISIONS.md)) lands here; altar zones flagged. |
| **5 — Weapons & Gear** | Keyed-armor `cerb_keyed_family` attunement gate (already specced) wires to `sect_id`. |
| **6 — Signature systems** | `EchoFamilyRegistry`, `RitualManager`, family Techniques, `FamilyBehavior` handlers. |
| **6.5 — Cultivation** | `sect_id`/`attunement_json` ship with `player_cultivation`; pledge/switch flow; mentor field. |
| **7 — Blink Gates + economy** | Token sinks (re-attunement fee), altar Spirit-Stone offerings plug into the economy. |
| **8 — Endgame / PvP** | Family Wars: outposts, war score, seasonal standings; Convergence/Warden gates. |

---

## 10. Flagship Differentiator

Most server "factions" are a name tag and a chat color. Cerberus's Echo Families are a **single object that simultaneously themes the bestiary, gates an armor set, summons a boss, owns a mount evolution line, defines a Physique, hosts a talisman line, and fields a seasonal war** — all off **one `sect_id` and one attunement track**, all data-driven. Pledging a family re-skins the *entire game* around one element without the engine knowing five families from six.

---

## 11. New Open Decisions

| # | Decision | Options | Recommended default |
|---|---|---|---|
| **D1** | **Token-name canon.** Three docs disagree: bestiary says *Glass Shard / Brine Pearl / Cinder Mote / Rime Crystal / Spore Sac*; [CULTIVATION_REALMS.md §3.3](CULTIVATION_REALMS.md) says *Glass Tear / Brine Pearl / Cinder Mote / Frost Shard / Mire Husk*; boss loot says *Grit Token / Ember Token*. | Pick one set; alias the rest. | **Glass Tear / Brine Pearl / Cinder Mote / Rime Crystal / Spore Husk** (used in §1); update bestiary + boss-loot strings. |
| **D2** | **Single vs. multi-family membership.** One `sect_id`, or allow a primary + secondary attunement? | (a) single (commitment); (b) primary+secondary with split rep; (c) free multi with diluted keyed bonuses. | **(a) single** — preserves the commitment axis; revisit if servers want hybrid builds. |
| **D3** | **TIDE/FROST ICE element-collision** ([OPEN_DECISIONS.md L137](OPEN_DECISIONS.md)). Both map to `ICE`; gear keying and reactions block on this. | (a) split ICE into two specialEffect variants; (b) TIDE = water/LIGHTNING blend, FROST = pure ICE; (c) add a 9th WATER element. | **(b)** — TIDE's identity is already LIGHTNING+Wet-water (§1), FROST keeps pure ICE; no 9th element, minimal reaction-table churn. Lock before keyed-armor keying. |
| **D4** | **Switch cost magnitude.** §2.3 proposes 100% token forfeit + 7-day cooldown + half-rep-freeze. | tune fee/cooldown/refund per server population. | ship the §2.3 defaults; expose all as config. |
| **D5** | **Multi-band families (ASH, MIRE).** They theme two bands each. Two herald/boss tiers per family, or one boss reused across both bands? | (a) two boss tiers (low-band + high-band); (b) one boss, scaled by altar distance. | **(b) one boss, distance-scaled** (the ritual already scales to altar band) — fewer assets; revisit if a distinct high-band boss is wanted. |
| **D6** | **Family War season length & reward power.** | weekly / monthly; cosmetic-only vs. minor server buff. | **monthly**, **cosmetic banner + a small (non-power-creep) buff** for the winning family; keep it flavor, not a snowball. |
| **D7** | **Intra-family PvP normalization** ([CULTIVATION_REALMS.md §8](CULTIVATION_REALMS.md) open). Does Δ≥+3 Overwhelm apply between same-family sect-mates? | normalize to fair-fight for mentoring, or full suppression. | **normalize same-family duels to fair-fight** (preserve mentoring); rival-family PvP keeps suppression+brackets. |
| **D8** | **Herald roster gap.** Only SAND's herald (Grit-Herald Khamûn) is named; TIDE/ASH/FROST/MIRE heralds are unnamed. | author 4 herald names + EPIC stat blocks. | author in the bestiary pass; herald = farmable no-lockout token source per family. |
