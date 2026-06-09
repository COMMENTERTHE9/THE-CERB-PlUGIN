# Cerberus — Mounts & Vehicles

*Generated 2026-06-08. Companion to [GEAR.md](GEAR.md) and the [master design plan](CERBERUS_DESIGN_PLAN.md). Western-forward naming per [NAMING.md](NAMING.md); controlled-hyperscaling per [POWER_SCALING.md](POWER_SCALING.md). Uses the new `mount` summon slot from the 20-slot gear layout.*

---

# Mounts & Vehicles — Integration

## Summary

Mounts & Vehicles adds a deep, **realm-scaled, open-ended progression spine** for traversal and aerial power, built entirely on the live engine rather than a new one. Two parallel concepts — **bonded Mounts** (living beasts, flying swords, realm-spirits that gain level/loyalty and *grow* with you via Beast Ascension) and **constructed Vehicles** (skiffs, mechs, airships, sky-fortresses, and the signature **personal-flight Skywing line**) — resolve through a single data-driven `RideableRegistry` that mirrors `MobRegistry`/`GearRegistry`: types, stats, realm bands, and abilities are config (`mounts.yml`/`vehicles.yml`), special powers are a `MountBehavior` handler map keyed by `ability.type`, so new mount/vehicle types ship as config and only genuinely new behaviors need code. Every rideable reads the **same central curve** (a `mount-curve` sibling of `power-curve`): HP and flight-stamina ride the ×8/realm slope into the billions, while raw **speed stays additive and hard-capped** so flight never shreds the chunk loader — realm expresses itself through *capability* (glide → flight → cloud-stride → cross-region `teleportAsync`), stamina, ability magnitude, and suppression, not velocity. Mount HP rides the existing `MobVirtualHealthManager` `double` pool through the 3-layer defense pipeline; growth rides the Spirit-Weapon level/loyalty/breakthrough track; everything is Folia-safe (region-thread summon/tick, UUID re-resolution, `teleportAsync` hand-off) and **ProtocolLib-free** (real Elytra glide + server-side velocity).

## Ties Into Existing Systems

| System | How Mounts & Vehicles ties in |
|---|---|
| **Gear (20 equip slots incl. 10 rings)** | One new **`mount` summon slot** added to `gear-slots.yml` (`summon-slot: true`, position reserved by the 20-slot layout expansion). It holds a **bound-rideable token** (PDC `cerb_rideable_id` + instance UUID), not passive stats — the Gear stat aggregator **ignores it**. A Skywing may co-occupy the `back` slot via a `gear-link` field (Gear contributes only its passive stats; flight logic stays owned by this system). `RideableRegistry` ships alongside `GearRegistry` (same mirrored resolution: PDC → name → pattern → fallback). |
| **Riding / Animal Taming / Navigation** | The three utility skills become the **primary XP sink + bonus delivery**. **Riding** = mounted control/combat (turn rate, no-buck, mounted-attack share, mounted-action ceiling). **Animal Taming** = bonding, loyalty cap/gain, tame-success vs realm gap, gates `boss-rideable` taming. **Navigation** = the *flight skill*: stamina (the GLIDE→FLIGHT→CLOUD_STRIDE gate), flight speed within cap, ceilings, cross-region hops, fast-travel. XP routes through the existing `long`-safe `SkillManager.addXp`. |
| **Distance-difficulty zones** | A mount carries its own `realm`; zone realm comes from `BandResolver` (distance-from-spawn). A bonded Skywing/beast read zone flags on the region-thread move/flight tick for no-fly, forced-dismount, and speed gating — flight obeys the same distance-is-difficulty covenant as everything else, enforced *vertically* too. |
| **Realm suppression** | When `zoneRealmIndex − mountRealmIndex > 0`, the **same POWER_SCALING §3 curve crushes the mount**: stamina bleed, speed weakened toward `auraWeaken`, and at `gap ≥ 4` **forced grounding** — "you flew too deep" is a mechanical wall. Inversely, a high-realm rider/legendary mount projects the rider's **Suppression Aura** (`REALM_AURA_PROJECT`) — the "god descends on a cloud" fantasy. Suppression wires into the **same `applyDamageMultiplierToEntity` / `applyResolvedDamage` insertion points** gear and mobs use; a mounted player projects the *higher* of (rider realm, mount realm) but defends at their own realm. |

## New Open Decisions

These are introduced *by this system* (beyond the per-section decisions already surfaced):

1. **One registry vs. two** — ship a single `RideableRegistry` (`class: MOUNT|VEHICLE`) as designed, or split `MountRegistry` + `VehicleRegistry`? The Systems section assumes unified; the standalone Mounts/Vehicles sections each assume their own manager. **Ratify one shape** before Step 5.
2. **Curve home** — does `mount-curve` live in `combat.yml` (Systems §5) or as its own `mount-curve.yml`/inside the `power-curve` treaty? Affects the Step-1 constants lock-down.
3. **Flight-Essence vs. shared Mana** — should flight/airship boost draw from a **separate flight-Essence reserve** or the shared Essence/Mana pool? Shared starves casting builds at the realm flight unlocks; separate adds a second resource bar.
4. **`max-active` rideables** — confirmed at **1** (one summoned rideable; second auto-stows). Ratify whether a towed beast-drawn vehicle (tamed beast pulling a barge) counts as one active or two.
5. **Loyalty decay severity** — how fast does an unsummoned/neglected bonded mount decay, and how much does Release refund? Tunes Stable-hoarding pressure.
6. **Boss-rideable HP factor** — confirm the `boss-hp-factor` (×20–×50) applied atop the curve for sky-fortresses/Maw-Leviathans, and that it stays `double`-safe (≤ ~840B « 2⁵³).
7. **Speed-cap value** — ratify `flight-speed-cap` (proposed 4.0 blocks/tick) against Folia chunk-streaming headroom; this is the single anti-thrash lever.
8. **Per-region active-rideable cap** — set the display-rig/entity budget per region (mirror the elite-mob cap) so airship fleets and summoned beasts don't stall a region thread.
9. **Ferry anti-exploit scope** — confirm passenger eligibility is checked **per-passenger on the destination** (reusing Blink-Gate USE-gating), and whether *same-realm* group ferry is fully unrestricted.

## Build-Order Placement (existing 9-step plan)

| Step | Mount/Vehicle work folded in |
|---|---|
| **0 — Hygiene + `power-curve`** | Reserve the `mount-curve` block as a `power-curve` sibling; no new code. |
| **1 — Number lock-down** | Ratify `mount-curve` constants (MOUNT_HP_ANCHOR, speed-per-realm, flight-speed-cap, mount-hp-ceiling, boss-hp-factor) into the one constants treaty. |
| **2 — Number formatter** | Reuse `CultivationNumberFormat` for Stable/Garage/Hangar GUI tiles (`840M HP` / `0.84 Heaven`). |
| **4 — ZoneManager / BandResolver** | Mounts read `zoneRealm` for suppression/zone rules; add mount zone-flags (no-fly, dismount radius) to zone config. |
| **5 — Weapons & Gear (extend)** | **`RideableRegistry` ships alongside `GearRegistry`**; `mount` summon slot added to `gear-slots.yml`; suppression wires into the same 3-layer insertion points; flying-sword mount reuses the existing yujian weapon link. **Natural home.** |
| **6 — Signature systems** | `MountBehavior` handlers (SUSTAINED_FLIGHT, dive attacks, `REALM_AURA_PROJECT`) land with the other signature verbs; the **Skywing personal-flight ladder is a marquee Step-6 feature**. |
| **6.5 — Cultivation/Ascension** | `player_mounts` / `player_vehicles` tables ship in the versioned-migration batch (`schema_version` bump, auto-create); loyalty/realm-attunement tie to the cultivation realm layer. |
| **7 — Blink Gates + economy** | **Ferry anti-exploit reuses the gate USE-gating**; realm-attunement Spirit-Stone sink plugs into the same economy. |
| **8 — Endgame / PvP** | `boss-rideable` apex mounts/sky-fortresses as endgame chases; `cerb_depths` per-floor no-fly flags; mount HP stays `double` (never `BigInteger` in the overworld). |

**One-line placement:** *A Step-5 registry sibling (`RideableRegistry` + `mount` summon slot) with a Step-6 signature-flight layer, Step-6.5 persistence, and Step-7 economy/anti-ferry hooks — built entirely on the `power-curve`, suppression curve, zone resolver, and gate-gating rail earlier steps already stand up.*

## Flagship Differentiator

The **Elytra-forward personal-flight progression** — a five-rung realm-gated climb (Glider → Wingsuit → Spirit-Wings → Flying-Sword → Cloud-Treading) powered by Essence instead of fireworks, with a real skill ceiling via Navigation — turns Minecraft's most under-used item into a signature, deep, never-"solved" progression that most servers leave as a one-time firework-spam novelty.

---

# Mounts

> *A Spirit Weapon grows in your hand; a **bonded mount grows at your side.** Where Gear is the apparatus that armors a soul and Weapons are the edge a cultivator turns on the world, a Mount is a **second life you raise** — a creature that begins as a skittish frontier horse and, fifty realms of shared blood later, becomes a **cloud-striding qilin or a sky-fortress leviathan** whose shadow darkens a province. Mounts are the signature delivery vehicle for the Ascension power fantasy in **motion**: a Mortal trots; a Primordial **rides the firmament**, suppression-aura rolling off their realm-beast like weather. This section makes mounts a deep, soulbound, realm-scaled progression — and makes **personal flight** (the badly-underused Elytra, see the companion Vehicles section) the through-line that ties it all together.*

This section is built **on the live engine** — the unified 10-tier (`Tier`) ladder reinterpreted as Realms ([NAMING.md](NAMING.md)), the central `power-curve` ([POWER_SCALING.md](POWER_SCALING.md) §2), the 3-layer defense pipeline, per-entity **Virtual Health** pools, **realm suppression** (POWER_SCALING §3), and the data-driven registry pattern ([GEAR.md](GEAR.md) §3). A mount is, mechanically, **a `MobVirtualHealthManager`-backed entity that is also a `SpiritWeapon`-style growth vessel** — it reuses both proven systems rather than inventing engines. It is **open-ended** (a `MountRegistry` mirroring `MobRegistry`/`GearRegistry`), ties to the **Animal Taming**, **Riding**, and **Navigation** utility skills, and is **Folia-safe** (summon on the owning region thread, hold UUID refs, re-resolve on thread, `teleportAsync` across regions). No ProtocolLib.

---

### 1. Design Goals & Boundaries

| Goal | How Mounts deliver it |
|---|---|
| **Mounts that *grow*, not get replaced** | A bonded mount has its **own level (1–100), its own Realm, its own Breakthroughs, and a loyalty/bond track** — mirroring the Spirit Weapon growth model (WEAPONS §5) exactly. Your starter beast can ascend with you across realms via **Beast Ascension**. |
| **Open-ended, never a fixed enum** | A **data-driven `MountRegistry`** (`mounts.yml`), mirroring `MobRegistry`. Mount species, stats, abilities, roles, and acquisition are config. New mounts — even unforeseen types like a "tide-kraken barge-beast" — ship as config + (at most) one behavior handler. |
| **Realm-scaled power fantasy** | A mount's speed, its own Virtual-HP pool, and its signature ability all read the central `power-curve`. A Primordial realm-beast is not "a faster horse" — it is **categorically a different order of being**, and realm suppression makes that a hard mechanical wall (§9). |
| **Skill-gated depth** | **Animal Taming** gates *what* you can bond and *how strong* the bond; **Riding** gates *what you can do while mounted* (mounted combat, speed, control) and scales mount stats; **Navigation** unlocks flight ceilings, cloud-striding, and cross-region travel. |
| **One active mount; a stable for the rest** | Exactly **one summoned mount at a time** via the new `mount` summon slot (the 20-slot equip layout reserved room — GEAR §2.1). The rest live in a persistent **Stable**, off-world, costing nothing. |
| **Mounted combat that feels cavalry, not a chairlift** | You can **swing and cast while mounted** within rules (§8); the mount has its **own HP pool** distinct from the rider; charge/trample mechanics make a war-mount a weapon. |
| **Folia-safe and ProtocolLib-free** | Summon/dismiss/ability all run on the owning region thread; the manager holds **UUIDs only** and re-resolves; cross-region follow uses `teleportAsync`. |

---

### 2. The Mount Registry (open-ended architecture)

Mirrors `MobRegistry`/`GearRegistry`: config-defined entries, O(1) resolution, atomic reload-with-fallback, PDC tagging, a small behavior-handler map for abilities.

#### 2.1 Files & manager

```
plugins/Cerberus/
  mounts.yml          # mount SPECIES (the registry spine: stats, role, realm band, ability ref)
  mount-roles.yml     # ROLE definitions (ground / war / flight / utility) — data, not code
  mount-abilities.yml  # named ability presets keyed to behavior handlers
```

A new `MountRegistry` (sibling to `MobRegistry`) loads all three and builds:
- `speciesById: Map<String, MountSpecies>` — the species/archetype table.
- `rolesById: Map<String, MountRole>` — roles are data (so a server can add a `siege` role).
- `byRealmBand`, `byRole`, `byEchoFamily` secondary indexes.
- `abilityHandlers: Map<String, MountAbility>` — the **only** code that knows specific behaviors (`CHARGE_TRAMPLE`, `FLIGHT_GLIDE`, `CLOUD_STRIDE`, `BREATH_CONE`, `GATHER_SCENT`, `LEVIATHAN_DIVE`, …). Adding a *new behavior* = one handler class + a config row; adding a *new mount using an existing behavior* = config only. Same separation as GEAR §3.2.

#### 2.2 `MountSpecies` schema (the extensibility unit)

```yaml
# mounts.yml
mount-species:
  frontier-courser:                 # a mundane starter (Mortal/Awakened band)
    display: "Frontier Courser"
    realm-band: I                   # Body Tempering (WEAK,COMMON) — see GEAR §4.1 bands
    base-entity: HORSE              # vanilla carrier entity used for visuals/physics
    role: GROUND
    growable: true                  # can be bonded & ascended (a Spirit-Mount candidate)
    stats:                          # per-realm-anchored; scaled by power-curve at spawn (§5)
      speed-mult: 1.6               # × vanilla ride speed baseline
      hp-anchor-factor: 2.0         # × the realm HP anchor for its OWN virtual pool
      jump: 1.1
    ability: { ref: dash-burst }     # mount-abilities.yml preset
    acquire: { tame: { item: GOLDEN_CARROT, taming-skill-min: 1 }, breed: true }

  cloud-qilin:                       # a spirit/flight beast (Realm Sovereign band)
    display: "Cloud Qilin"
    realm-band: V                   # GODLY,PRIMORDIAL
    base-entity: HORSE              # ridden as a horse-class entity; model via CustomModelData
    role: FLIGHT
    growable: true
    flight: { mode: CLOUD_STRIDE, ceiling: NONE, hover: true }
    stats: { speed-mult: 4.5, hp-anchor-factor: 6.0, jump: 3.0 }
    ability: { ref: nine-heavens-gale }
    acquire: { hatch: { egg: cloud-qilin-egg }, boss-drop: { from: empyrean-sky-warden } }

  # A future, unforeseen mount — ZERO code changes (existing FLIGHT role + BREATH_CONE handler):
  ember-wyrmling:
    display: "Ember Wyrmling"
    realm-band: III
    base-entity: HORSE
    role: FLIGHT
    growable: true
    flight: { mode: FLAP, ceiling: 256, hover: false }
    stats: { speed-mult: 3.0, hp-anchor-factor: 3.5, jump: 2.2 }
    ability: { ref: cinder-breath }   # uses the generic BREATH_CONE handler
    acquire: { boss-drop: { from: ascendant-ash-drake } }
```

#### 2.3 Resolution & persistence

A summoned mount entity carries PDC tags exactly like gear/mobs: `cerb_mount_id` (species, O(1) lookup), `cerb_mount_owner` (player UUID), `cerb_mount_instance` (the **bonded-instance** UUID — the *individual* beast, which carries its level/bond), `cerb_mount_realm`, `cerb_mount_level`. Resolution order: **PDC `cerb_mount_id` (O(1)) → species fallback → UNIDENTIFIED**. The *individual* bonded beast's growable state (level, XP, bond, name, breakthroughs, ability unlocks) lives in a new auto-created `player_mounts` table (snapshot-on-region-thread → async write, the proven pattern), keyed by `(owner_uuid, instance_uuid, species_id, realm, level, bond, custom_name, ability_flags, lineage_json)`. The transient *entity* is disposable; the **row is the mount's soul** — this is what lets it persist across summon/dismiss, server restarts, and chunk unloads.

---

### 3. The Roster — across the 10 Realms

The roster is authored by **Echo-flavored species × realm band × role**, with a curated **named/legendary** layer (the "famous beasts"). Every mount has its **own Virtual-HP pool** (its `hp-anchor-factor` × the realm HP anchor from POWER_SCALING §2.3) and a **signature ability** routed through a behavior handler. Numbers below use the central curve (`HP_ANCHOR=50`, `realmMult = 8^realmIndex`); a mount's own HP = `realmAnchorHP × hp-anchor-factor × levelMult` — and because a **bonded** mount ascends with you (§4), its realm tracks yours, so its pool is always *of your band*.

Speed is shown as a multiplier over the vanilla ride baseline (≈ a sprinting player); flight speed is the in-air cruise.

#### 3.1 Mundane → trained (Mortal · Awakened · Adept)

| Realm | Mount (example) | Role | Speed | Own Virtual-HP (rep.) | Signature ability |
|---|---|---|---|---|---|
| Mortal (WEAK) | **Scrub Pony**, **Bristleback Boar**, **Camp Wolf** | GROUND | ×1.3–1.5 | ~12–30 | Passive: +5% out-of-combat stamina regen |
| Awakened (COMMON) | **Frontier Courser** (horse), **War-Boar**, **Pack-Wolf** | GROUND / light-war | ×1.6–1.9 | ~100–600 | **Dash-Burst** (3 s sprint, short CD) |
| Awakened (COMMON) | **Draft Ox**, **Mule-of-Burden** | UTILITY | ×1.2 | ~300–600 | **Pack-Hauler:** +9 mounted carry slots; +scent-ping on ore/herb nodes |
| Adept (UNCOMMON) | **Trained Destrier**, **Marsh Strider**, **Dire-Wolf** | WAR | ×2.0–2.3 | ~2.4K–6K | **Trample Charge:** sprint into a foe = knockback + trample dmg (CHARGE_TRAMPLE) |

#### 3.2 Warbeasts → spirit beasts (Champion · Ascendant · Paragon)

| Realm | Mount (example) | Role | Speed | Own Virtual-HP (rep.) | Signature ability |
|---|---|---|---|---|---|
| Champion (RARE) | **Tidecaller Hippocamp** (water-strider), **Iron Boar**, **Bonereaver Lion** | WAR / aquatic | ×2.4–2.8 | ~19K–60K | **Surge Charge:** trample + a forward shockwave; Hippocamp walks on water |
| Champion (RARE) | **Ash-Mastiff Pack** (rideable alpha) | WAR | ×2.6 | ~40K | **Pack-Howl:** summons 2 ephemeral wolf adds (region-thread spawn, leashed, TTL) |
| Ascendant (EPIC) | **Embersoul Drake** (low-flight wyrmling) | FLIGHT (flap, ceiling 256) | ×3.0 air | ~205K–600K | **Cinder-Breath** (BREATH_CONE): forward fire cone, applies Burn |
| Ascendant (EPIC) | **Mire-Stalker** (spirit beast, phases through reeds) | WAR / stealth | ×2.8 | ~205K | **Veil-Step:** brief mounted invisibility + ambush-dmg on exit |
| Paragon (LEGENDARY) | **Rimeheart Stag**, **Spirit Tiger** | WAR / ground-burst | ×3.2 | ~1.6M–6M | **Frost-Trample:** trample chills (slows) foes in the lane |
| Paragon (LEGENDARY) | **Sky-Serpent** (true flight, no ceiling) | FLIGHT (glide+hover) | ×3.8 air | ~1.6M | **Coil-Dive:** dive-bomb attack scaling with fall distance |

#### 3.3 Cloud-beasts → realm-beasts (Transcendent · Demigod · Empyrean)

| Realm | Mount (example) | Role | Speed | Own Virtual-HP (rep.) | Signature ability |
|---|---|---|---|---|---|
| Transcendent (PERFECT) | **Thunder-Roc** | FLIGHT (cloud-stride) | ×4.0 air | ~13M–52M | **Storm-Talon:** dive-strike calls a lightning bolt at the impact point; shock-stun |
| Transcendent (PERFECT) | **Ashen Behemoth** (siege ground-beast) | WAR / siege | ×3.0 | ~13M | **Quake-Stomp:** AoE ground-slam, staggers a crowd; ignores knockback |
| Demigod (MYTHIC) | **Cloud Qilin** | FLIGHT (cloud-stride, hover, **no ceiling**) | ×4.5 air | ~105M–420M | **Nine-Heavens Gale:** sustained hover + a wind-domain that buffs rider cast & slows nearby foes |
| Demigod (MYTHIC) | **Maw-Tide Leviathan** (aquatic/sky-swim juvenile) | FLIGHT-AQUATIC | ×4.2 | ~105M | **Leviathan-Dive:** submerge/phase-dash, surfacing as a wave that knocks back a cone |
| Empyrean (GODLY) | **Empyrean Sky-Warden** (winged colossus) | FLIGHT / war-platform | ×5.0 air | ~840M–3.4B | **Sovereign Wingbeat:** a downdraft that grounds lower-realm flyers (anti-air) + suppression pulse |
| Empyrean (GODLY) | **Abyssal Wyrm** | FLIGHT (cloud-stride) | ×4.8 air | ~840M | **Void-Breath:** cone that applies **Soulpierce** (chips Virtual Health directly) |

#### 3.4 Primordial realm-beasts & leviathans (Primordial)

| Realm | Mount (example) | Role | Speed | Own Virtual-HP (rep.) | Signature ability |
|---|---|---|---|---|---|
| Primordial (PRIMORDIAL) | **Firmament Qilin** (apex cloud-strider) | FLIGHT (cloud-stride, no ceiling, hover) | ×5.5 air | ~6.7B–27B | **Tread the Firmament:** rider + mount become near-untargetable by lower-realm foes while cloud-striding; leaves a damaging star-road trail |
| Primordial (PRIMORDIAL) | **World-Tide Leviathan** (the sky-fortress beast) | WAR-PLATFORM / siege | ×4.5 | ~13B–33B (×bossHpFactor on legendary individuals → **~330B**) | **Maw of the World-Tide:** a frontal devour-cone dealing % of a lower-realm target's max HP; carries a multi-rider howdah (group mount) |
| Primordial (PRIMORDIAL) | **Echo-Roc, the Storm-Crowned** | FLIGHT | ×6.0 air | ~6.7B–27B | **Tempest Sovereign:** a moving storm-domain — continuous lightning on lower-realm foes below the flight path |
| Primordial (PRIMORDIAL) | **Cerberus-Scion Hound** (curated, from the Echo-Eater) | WAR | ×5.0 | ~33B+ | **Three-Throated Roar:** AoE realm-suppression burst (adds +1 effective gap to the rider's suppression for 6 s) |

> **Named / Legendary mounts** (the famous beasts, soulbound on bond — the cultivation "this is *my* beast" trope): curated single-or-rare individuals, e.g. *Báiyún the First Qilin*, *Wǎnglǎng the Maw-Tide*, the *Cerberus-Scion Hound*. These are the mount analogue of Divine Artifacts (WEAPONS §6): boss/quest-only, `cerb_bound`, carry a unique pre-named ability, and apply a **Suppression Aura** while ridden (the "everyone is instantly outclassed" effect, §9).

---

### 4. Bonding, Taming & Beast Ascension (mounts that grow with you)

A bonded mount is a **living Spirit Weapon** — it reuses the Spirit Weapon growth track (WEAPONS §5) but with its own flavor: **level, breakthroughs, loyalty, lineage.**

#### 4.1 Bonding (gated by Animal Taming + Riding)

| Mechanic | Driver | Effect |
|---|---|---|
| **Tame** | **Animal Taming** skill | The skill level gates *which realm band* of wild beast you may bond and the **bond-success chance** (a Mortal can't bond a Sky-Serpent). Taming a wild beast rolls against `tameChance = base + tamingSkill·k − realmGapPenalty`. |
| **Bond / Resonance** | **Animal Taming** | A `0.0–1.0` **bond** value (the mount analogue of weapon *resonance*). Grows by riding, feeding, fighting *together*; **decays slowly if stabled and ignored**. High bond unlocks the mount's **active ability** and raises its stat budget (`×(1 + 0.5·bond)`, identical to weapon resonance). |
| **Loyalty** | derived from bond | At low loyalty a wild-tempered beast may **buck** (brief control loss) under heavy damage; a fully-bonded beast never bucks and **will not abandon you on death** (returns to the stable, not lost — the soulbound rule). |
| **Ride control** | **Riding** skill | Riding level scales the mount's **effective speed, turn handling, jump, and flight stability**, and raises the **mounted-action ceiling** (§8: what weapons/casts you can use mounted). A low-Riding player on a Qilin flies sluggishly; a maxed rider makes the same beast dance. |
| **Navigation** | **Navigation** skill | Gates **flight ceiling**, **cloud-striding** unlock, and **cross-region auto-follow** range; widens the mount's "safe-path" auto-pathing and unlocks Blink-Gate co-travel (the mount comes through the gate with you). |

#### 4.2 Beast Ascension (the mount's own Realm progression)

A **growable** mount has its **own level (1–100)** and its **own Realm**, and **ascends with you** — this is the heart of "the beast that grew with me." It mirrors the player's Ascension (Breakthroughs and an Ascension-Trial gate), so a starter Frontier Courser can, across the whole journey, become a Firmament Qilin's equal in *function* without ever being replaced.

```
mountPower = realmAnchor(mountRealm)               # its band's order-of-magnitude (power-curve)
           × (1 + 0.012 · mountLevel)              # +1.2%/level → +120% at L100 (mirrors Spirit Weapon)
           × (1 + 0.5 · bond)                       # bond strength with THIS rider (up to ×1.5)
           × speciesStatMult                        # from mounts.yml
           × (1 + 0.08 · breakthroughStars)         # per Breakthrough within a realm
```

- **Mount XP:** a bonded mount earns a small share of the XP its rider earns in **Riding** and **Animal Taming** (and a sliver of combat XP when it lands hits / takes hits in mounted combat) — so *using* the mount levels it, exactly like a Spirit Weapon levels from its class skill.
- **Breakthroughs:** at level milestones a mount hits a **Breakthrough** (within-realm power stars); at the realm-cap it faces a **Beast Ascension Trial** — a short challenge (feed it a realm-shard / win a fight while mounted) that lifts it a full Realm, re-anchoring its HP pool, speed, and ability damage onto the next band. This is the mechanical "my old horse became a sky-beast" payoff.
- **Evolution lines:** a species can declare an `ascends-to:` chain in config (Frontier Courser → Trained Destrier → Spirit Tiger → … → Firmament Qilin), so the *model and abilities* visibly transform across realms while the **same bonded soul (instance UUID, name, bond, lineage) persists**. Open-ended: a server adds an evolution branch in `mounts.yml` with zero code.

| Growth axis | Bounded by | Power contribution |
|---|---|---|
| Mount level 1–100 | L100 | up to ×2.2 |
| Bond / loyalty | 1.0 cap | up to ×1.5 |
| Breakthrough stars | per-realm cap | up to ~×1.5 within a realm |
| Beast Ascension (realm) | PRIMORDIAL | the ×8/realm magnitude jump (the big lever) |

---

### 5. Realm Scaling of mount power (one curve)

Mount stats read the **same central `power-curve`** as everything else (POWER_SCALING §2.4) — nothing invents its own slope:

```
mountMaxHP(realm, level)  = HP_ANCHOR · realmMult(realm) · levelMult(level) · hpAnchorFactor · (1 + 0.5·bond)
mountAbilityDmg(realm, …) = DMG_ANCHOR · realmMult(realm) · levelMult(level) · abilityCoeff · suppressionMult(§9)
mountRideSpeed            = baseRideSpeed · speedMult · (1 + 0.10·ridingTierBonus)   # NOT realm-scaled to absurd values — see note
```

> **Speed is deliberately *not* hyperscaled.** HP and ability damage climb into the billions by realm; **movement speed does not** (a Firmament Qilin caps around ×5–6, not ×16M) — uncapped mount speed would shred chunk-loading and Folia region hand-off. Realm expresses itself in mounts through **flight access, HP pool, ability magnitude, and suppression**, not raw m/s. The flight-mode ladder (walk → flap → glide → cloud-stride → firmament-tread) is the *traversal* power fantasy; the numbers are the *combat* one.

All mount HP lives in the **same `MobVirtualHealthManager` pool** mobs use (POWER_SCALING §1.2): a fixed cosmetic vanilla `MAX_HEALTH` (e.g. 40.0) drives the rendered bar; the **`double` pool is the source of truth** and stays well under `2^53` (a `330B`-HP World-Tide Leviathan = 3.3×10¹¹ « 9.0×10¹⁵). Mount HP is persisted as a `PersistentDataType.DOUBLE` PDC tag + the `player_mounts` row, recomputed cheaply from the curve on summon. **No `BigInteger` on the mount path** — mounts never enter instanced Threat content as scaling vessels.

---

### 6. Mount Roles

Roles are **data** (`mount-roles.yml`), so a server can add a `siege` or `caravan` role without code. The four shipped roles:

| Role | Identity | Combat? | Flight? | Key stat bias | Example |
|---|---|---|---|---|---|
| **GROUND** | Pure ground-speed traversal; the everyday horse | rider attacks only | no | speed, stamina | Frontier Courser, Spirit Tiger |
| **WAR** | Mounted combat platform; charge/trample is a weapon | **yes — mount deals trample/charge dmg + rider attacks** | some (war-flyers) | HP pool, charge dmg, knockback-immunity | Trained Destrier, Ashen Behemoth |
| **FLIGHT** | Flight-capable beasts; the Elytra-replacement signature | rider attacks; dive-strikes | **yes** (flap → glide → cloud-stride) | air speed, hover, flight stability | Sky-Serpent, Cloud Qilin, Thunder-Roc |
| **UTILITY** | Gathering / hauling / scouting support | minimal | some | carry slots, node-scent, gather bonus | Draft Ox, Pack-Mule, scout-hawk |

- **GROUND** mounts maximize the speed multiplier and stamina; their ability is mobility (Dash-Burst, water-walk).
- **WAR** mounts are the only ones whose **mount itself deals damage** as a primary mode (CHARGE_TRAMPLE): a sprinting charge into a foe applies the mount's `mountAbilityDmg` + knockback; the mount has knockback-immunity and the largest HP pool, because it's meant to be in the fray.
- **FLIGHT** mounts are the headline — personal flight as deep progression. The flight-mode ladder (§7) *is* the role's depth. Dive-strikes (Coil-Dive, Storm-Talon, Leviathan-Dive) let a flyer deal burst on a pass.
- **UTILITY** mounts feed the gather→craft loop: a Pack-Mule grants mounted carry slots and pings ore/herb nodes (a GATHER_SCENT behavior reading the rider's gathering skills, like Branch-B tools in GEAR §2.2); a scout-hawk widens the map-reveal radius (Navigation synergy).

A mount may hold a **secondary role** (a war-flyer is `FLIGHT` primary + `WAR` secondary) — these are just two role refs in config.

---

### 7. Flight & the Elytra-as-progression hook

Flight is the signature. The flight ladder is a **mode progression** keyed to the mount's realm + the rider's **Navigation** skill — turning the underused Elytra fantasy into a deep climb (and dovetailing with the **Vehicles** section's personal-Elytra wing-suit line):

| Flight mode | Realm band | Feel | Mechanics |
|---|---|---|---|
| **None** | I | grounded | ground-only mounts |
| **Flap** | II–III | ascend-then-glide, has a ceiling (e.g. 256) | limited altitude; periodic flap-cost; the "young drake" feel |
| **Glide** | III–IV | true gliding, hover-capable | no hard ceiling but no free hover-climb; dive-strikes |
| **Cloud-Stride** | IV–V | walk on air, full hover, no ceiling | the cultivation "step on clouds" fantasy — stable hover, vertical freedom; **Navigation-gated** |
| **Firmament-Tread** | V (Primordial) | ride the sky like ground, leaves trails | apex: near-untargetable by lower realms while striding; the World-Tide/Firmament Qilin tier |

Flight is implemented Folia-safely on the **rider's region thread**: mount velocity/levitation is applied inline on the owning thread; when a flying mount crosses a region boundary it uses **`teleportAsync`** for the hand-off (never a sync cross-region move), and the controller re-resolves the entity by UUID on the new thread. No client-side packet work, **no ProtocolLib** — flight uses server-side velocity + `Levitation`/glide physics on the vanilla carrier entity.

---

### 8. Mounted Combat

The rider and the mount are **two entities with two separate Virtual-HP pools** — this is the core of cavalry feel.

#### 8.1 Can you act while mounted?

Gated by the **Riding** skill (the mounted-action ceiling rises with Riding level):

| Riding tier | Melee while mounted | Cast/spell while mounted | Ranged while mounted |
|---|---|---|---|
| Low | light 1H melee only (Jian/Dao/Fists) | basic spells, reduced cast | thrown/short only |
| Mid | most 1H + the **Flying Sword** (yujian auto-orbits perfectly from a mount — a natural pairing) | full casting | bows at a small accuracy penalty |
| High | 1H freely; **lances/Qiang gain a mounted charge bonus** | full casting, no penalty | full ranged incl. Spirit-Gun |
| Max | dual-wield enabled mounted; charge-attacks combo with the mount's trample | spell + mount-ability chaining | hitscan Spirit-Gun (WEAPONS §2) shines from a flyer |

- **2H heavy weapons (Greatsword, Hammer, Cauldron)** are **disabled or heavily penalized** while mounted at all but the highest Riding tiers — you can't swing a Zhanmadao from a galloping horse without mastery. This gives Riding a real combat trade-off and keeps the **Lance/Qiang** identity as the *premier mounted weapon* (it gains a charge-damage bonus that stacks with the mount's trample).
- The **Flying Sword (yujian)** and **Spirit-Gun** are the standout mounted weapons: a detached auto-orbiting blade ignores the awkwardness of swinging from a saddle, and a hitscan qi-bolt is point-and-click from a diving Roc.
- **Mount abilities** are bound to a sneak-modifier input (reusing the tool-belt sneak-scroll/interact pattern, no ProtocolLib): the rider triggers Trample-Charge, Cinder-Breath, etc. The ability fires on the region thread.

#### 8.2 Mount takes damage vs rider

| Hit source | Hits the… | Notes |
|---|---|---|
| Frontal melee / projectiles aimed at the moving target | **mount pool first** (it's the bigger silhouette) | a fraction (config `rider-splash`, e.g. 20%) bleeds to the rider — you're not perfectly safe |
| AoE / cleave / splash | **both pools** (mount + rider, each via their own 3-layer defense) | |
| Targeted rider attacks (e.g. a sniper Longbow line-pierce) | **rider pool**, mount shields a share if loyalty is high | high-bond mounts "take the hit for you" |
| Trample / charge dealt **by** the mount | uses **`mountAbilityDmg`** through the victim's 3-layer defense + suppression | mount is a damage *source* in WAR role |

- **If the mount's pool hits 0:** the mount is **downed** (dismounts the rider, enters a recovery state), **not permanently killed** — a bonded mount returns to the stable to heal over time (loyalty-gated recovery speed). Soulbound mounts are *never* lost on death (mirrors the bound-weapon-returns-as-shard rule, WEAPONS §5).
- **If the rider dies while mounted:** the mount auto-stables (it does not despawn into the world as a free entity — avoids orphaned high-realm beasts on Folia).
- Both pools route through the **same `EntityDamageEvent` → 3-layer defense → suppression** pipeline already built; the mount is just another `MobVirtualHealthManager`-tracked entity with an owner tag.

---

### 9. Realm Suppression — applied to and against a mounted player

Suppression (POWER_SCALING §3) is what makes a high-realm mount **monstrous** rather than just fast. The rule: **a mounted player projects the *higher* of (rider realm, mount realm) as their effective suppression realm**, but their *defenses* use their own realm (you can't borrow a beast's body to tank — only its dominance).

| Situation | Suppression behavior |
|---|---|
| **High-realm rider on any mount, vs lower-realm foes** | Full rider suppression (amplified outgoing, incoming DR, the 12-block **Suppression Aura**) applies normally; the mount's trample also benefits from `suppressionDamageMult`. A Primordial cavalier flattens a Champion zone — automatically. |
| **A *legendary/named* mount (Cloud Qilin, World-Tide, Cerberus-Scion)** | The **mount itself projects a Suppression Aura** while ridden (like a Divine Artifact, WEAPONS §6) — even a lower-skilled rider radiates the beast's realm dominance to lower foes. The Cerberus-Scion's *Three-Throated Roar* literally **adds +1 effective gap** to the rider's suppression for 6 s. |
| **Lower-realm rider venturing into a higher-realm zone on a mount** | Suppression flows **against** them: the *mount* is suppressed too — its speed, HP-effective-value, and ability damage are reduced by the same `auraWeaken`/DR curve (a Champion mount in a Primordial Maw is sluggish and barely scratches). This enforces "you rode too deep" mechanically, not just via HP sponging — the distance-is-difficulty covenant. |
| **Anti-air dominance** | Higher-realm flyers' abilities (Empyrean Sky-Warden's *Sovereign Wingbeat*) **ground lower-realm flyers** — suppression expressed as a hard control effect, the "you don't get to fly in my sky" beat. |
| **Within-realm (gap = 0)** | All suppression is **inert** (identity) — same-realm mounted duels are governed by the readable level/bond/Riding math only, preserving fair PvP/PvE pacing (POWER_SCALING §3.4). |

Suppression slots into the **existing insertion points** (the rewritten `applyDamageMultiplierToEntity` and `applyResolvedDamage`) — a mount is just another entity feeding `effectiveRealm` into the same gap math. PvP suppression of a mounted player obeys the same `suppression-dr-cap` (≤99%) and the level-bracketed-PvP edge-protection from the World domain, so ganking-down stays flavorful, not infinitely abusive.

---

### 10. Acquisition, Summon/Dismiss & the Stable

#### 10.1 Acquisition (config-declared per species)

| Path | How | Skill / gate |
|---|---|---|
| **Tame in the wild** | Approach a wild beast of a band you qualify for, feed its `tame.item`, roll `tameChance`. | **Animal Taming** level + bond item |
| **Hatch / raise eggs** | Boss/loot **eggs** (`cloud-qilin-egg`) incubate over real time (offline-friendly timer); hatchling starts at L1 of its species and bonds to the hatcher. | Egg item + Animal Taming to incubate high bands |
| **Boss drops** | Apex bosses drop **bonded mount tokens** or live tameable scions (Empyrean Sky-Warden → Sky-Warden, the Echo-Eater → Cerberus-Scion Hound). | Beat the content |
| **Breeding** | Two bonded same-species (or compatible-line) mounts produce an **egg** whose stats/ability inherit a **lineage roll** (the `lineage_json` — best-of-parents + a small mutation chance for a rare color/ability variant). | **Animal Taming**; breeding pen (a DEPLOYABLE) |

Breeding + lineage gives mounts the same "deep chase" the rings-of-power and affix systems give gear — a perfect-lineage Spirit Tiger is an endgame breeding project, not a vendor purchase.

#### 10.2 Summon / dismiss / storage (Folia-safe)

- **One active mount.** Summoning from the **`mount` equip slot** (the slot GEAR §2.1 reserved) spawns the bonded instance; summoning another auto-stables the first. Dismiss returns it to the stable instantly.
- **The Stable** is unlimited, off-world, free — a GUI list of `player_mounts` rows. Mounts heal and slowly regain any lost bond-decay while stabled (loyalty-gated). This keeps "collect them all" from cluttering the world with high-realm entities.
- **Folia handling (the critical bit):**
  - **Summon** is requested on the **owning region thread** of the player's current location (the player's region) — the spawn, PDC tagging, and pool initialization happen inline there, exactly like `CreatureSpawnEvent` mob scaling. The manager then stores **only the entity UUID** (+ the instance UUID) in a `ConcurrentHashMap<UUID, MountState>`.
  - **Every subsequent mount operation re-resolves the entity by UUID on its owning region thread** (`Bukkit.getEntity(uuid)` / region scheduler), never caching a cross-thread `Entity` reference — the same discipline bosses use (CERBERUS_DESIGN_PLAN §boss-Folia: "holds only UUID references, re-resolving entities on its own thread").
  - **Cross-region follow** (a galloping/flying mount, or a player who teleports): the mount follows via **`entity.teleportAsync(target)`**, and the controller re-binds on the destination region thread. A mount that loses its rider across a region boundary auto-stables rather than risking an orphaned cross-thread entity.
  - **Mount AI/ability ticks** run on the mount's owning region scheduler at a low frequency (ability checks, aura scans on legendary mounts only — perf-bounded exactly like the §3.3 suppression-aura scan); nothing touches Bukkit on async threads. PDC/`player_mounts` writes follow snapshot-on-region-thread → async-I/O.

---

### 11. Architecture summary (how new mounts ship without code)

1. **Add a new mount species** → one `MountSpecies` entry in `mounts.yml` referencing an existing role + an existing ability ref. **Zero code.**
2. **Add a new role** (e.g. `siege`, `caravan`) → one entry in `mount-roles.yml`; the equip/summon flow and stat aggregator read `rolesById`. **Zero code.**
3. **Add a new evolution line** → an `ascends-to:` chain in config; the bonded soul persists across the transform. **Zero code.**
4. **Add a genuinely new ability** (a brand-new mount behavior) → one `MountAbility` handler class registered into `abilityHandlers`, then config — the *only* case needing code, a single small class, exactly like a new mob ability or GearBehavior.
5. **Resolution & persistence** reuse the proven `MobRegistry`/`GearRegistry` shape: PDC `cerb_mount_*` tags → O(1) `speciesById` lookup, atomic reload-with-fallback, async snapshot saves to the auto-created `player_mounts` table, Folia region-thread-safe summon/ability with UUID re-resolution and `teleportAsync` hand-off. Mount HP rides the **existing** `MobVirtualHealthManager` pool and the **central `power-curve`**; mount growth rides the **existing** Spirit-Weapon-style level/bond/breakthrough track — Mounts add **content and two managers** (`MountRegistry`, `MountManager`), not a new engine.

---

## Vehicles & Personal Flight

> **Scope & doctrine.** This section designs **non-living Vehicles** (boats/ships, rail/carts, ground constructs/mechs, and multiplayer **airships/sky-skiffs/cloud-barges**) and the centerpiece — **Personal Flight**, a deep Elytra-based progression from a wobbly first glider to near-free Primordial sky-striding. It is built **on the live engine**: the 10-Realm ladder (`Tier`, `realmIndex`, the `realm-base 8.0` power curve from [POWER_SCALING.md](POWER_SCALING.md)), the data-driven **registry pattern** that Gear and Mobs already use ([GEAR.md](GEAR.md), `MobRegistry`), the **Essence** pool (Mana, held as a `double`), the three utility skills **Riding / Animal Taming / Navigation**, and realm **suppression**. It reuses the existing **Flying Sword (yujian)** weapon archetype from [WEAPONS.md](WEAPONS.md) §2 #15 for its top flight mode. Everything ships **without ProtocolLib** — pure Paper/Folia APIs (`elytra` flag, `setVelocity`, `teleportAsync`, `World.rayTrace*`, region schedulers). Like all Cerberus systems: **open-ended (config registry), realm-scaled (numbers climb ×8/realm), and Folia-safe (entity ops on the owning region thread).**

The two halves answer two different fantasies. **Vehicles** are *shared, owned, world-spanning* — the galleon, the war-mech, the sky-fortress a crew lives on. **Personal Flight** is *intimate, skill-expressive, always-on* — the Elytra made into a signature progression instead of the throwaway most servers leave it as.

---

### 1. One Registry, Two Branches (open-ended architecture)

Vehicles and flight gear are **not** a fixed enum. A new `VehicleRegistry` (sibling to `MobRegistry`/`GearRegistry`) loads config, resolves O(1) by PDC tag, and dispatches behavior through a handler map — *identical in shape* to the Gear registry so a new vehicle type ships as **config, not code**.

```
plugins/Cerberus/
  vehicle-types.yml     # VEHICLE archetypes/schemas — the registry spine (boats, mechs, airships, gliders, wings…)
  vehicle-items.yml     # concrete authored vehicles + generative realm-band rules
  flight.yml            # the personal-flight curve: modes, Essence costs, altitude caps, zone rules (single source of truth)
```

`VehicleRegistry` builds `typesById`, `byBranch`, `byMaterial`, and a `patternEntries` list (display-name regex match, like `customNamePattern`). Resolution mirrors `MobRegistry.resolveInternal`: **PDC `cerb_vehicle_id` (O(1)) → display-name exact → pattern → material fallback → UNIDENTIFIED.** Crafted/spawned vehicles carry `cerb_vehicle_id`, `cerb_vehicle_realm`, `cerb_vehicle_quality`, `cerb_vehicle_owner`.

| Branch | What it is | Backed by | Governing skill |
|---|---|---|---|
| **V — Vehicles** (this section §2–§5) | Non-living rideable constructs: boats, carts, mechs, airships | A real entity (boat/minecart/armor-stand/display-entity *seat rig*) + a virtual-stat pool | **Riding** (pilot bonuses), **Navigation** (speed/range), **Animal Taming** (realm-beast-drawn variants) |
| **F — Flight** (§6–§9) | Personal flight worn in the `back` gear slot | The vanilla **Elytra** flag + an Essence-driven boost engine | **Navigation** (the flight skill), **Riding** (shared mount-air bonuses) |

The **only** code that knows specific behaviors is a small `Map<String, VehicleBehavior>` keyed by `function.type` (`WATER_HULL`, `RAIL_CART`, `WALKER_MECH`, `AIRSHIP_PLATFORM`, `GLIDER`, `WING`, `SWORD_FLIGHT`…). New behavior = one handler class + config; new vehicle using an existing behavior = config only. Exactly the `GearBehavior` separation.

```yaml
# vehicle-types.yml — a VehicleType is the extensibility unit
vehicle-types:
  war-junk:
    branch: VEHICLE
    function: { type: AIRSHIP_PLATFORM, hull-shape: junk }
    required-realm: LEGENDARY            # seal rule: below this it flies at the PILOT's realm cap, not the hull's
    material-base: OAK_BOAT              # cosmetic carrier; real geometry is a block-display rig
    governing-skill: NAVIGATION
    seats: { pilot: 1, gunner: 4, passenger: 12 }
    stat-schema:
      hull-hp:    { weight: 0.45, role: PRIMARY }   # vehicle Virtual Health (a double, realm-scaled)
      cruise-spd: { weight: 0.25, role: PRIMARY }
      essence-burn:{ weight: 0.15, role: SECONDARY } # Essence/sec to stay aloft
      cargo:      { weight: 0.15, role: SECONDARY }
    weapon-hardpoints: 4                 # mountable Spirit-Gun / ballista emplacements (reuse WEAPONS §2 #21)
    affix-slots: { prefix: 1, suffix: 1 }

  # An unforeseen future type — ZERO code:
  cloud-barge:
    branch: VEHICLE
    function: { type: AIRSHIP_PLATFORM, hull-shape: cloud }
    required-realm: GODLY
    material-base: WHITE_WOOL
    governing-skill: NAVIGATION
    seats: { pilot: 1, passenger: 30 }
    stat-schema: { hull-hp: {weight:0.5,role:PRIMARY}, cruise-spd:{weight:0.3,role:PRIMARY}, essence-burn:{weight:0.2,role:SECONDARY} }
```

---

### 2. Non-Living Vehicles — the four families & their realm tiers

Every vehicle's stats follow the same realm engine as Gear: `finalStat = archetypeBase × realmMult(tier) × variantMod × qualityRoll`, where `realmMult = REALM_BASE^realmIndex` (`REALM_BASE = 8.0`). A vehicle's **hull-HP is a Virtual Health pool** (a `double`, billions-capable, drained by vehicle-combat — §5) managed exactly like `MobVirtualHealthManager`. Vehicles obey the **seal rule** (`required-realm`, per [CULTIVATION_REALMS.md](CULTIVATION_REALMS.md)): pilot a hull above your realm and it runs at *your* realm cap, never the hull's — no "ferry a god-ship to a mortal" exploit.

| Family | `function.type` | Domain | Governing skill | Entity backing (Folia-safe) |
|---|---|---|---|---|
| **Boats & Ships** | `WATER_HULL` | seas, rivers, the world edge | Navigation + Fishing | `Boat` entity for small; **block-display seat-rig** for ships |
| **Rail & Carts** | `RAIL_CART` | overland/underground transit nets | Navigation | `Minecart` family + custom rail behavior |
| **Ground Constructs / Mechs** | `WALKER_MECH` | combat & heavy hauling | Riding + Engineering | armor-stand/display rig with a server-driven walk cycle |
| **Airships / Sky-Skiffs / Cloud-Barges** | `AIRSHIP_PLATFORM` | the sky — the marquee family | Navigation | block-display **moving platform** (passengers ride mounted seats) |

#### 2.1 Boats & Ships (sail the seas, the rivers, and the world edge)

The water family scales from a one-seat skiff to a Primordial leviathan-hunter that sails *past* the world border into the open ocean where the **Sea Monsters** live (ties directly to the existing Sea Monster spawner and the **Tide-Caller Nessuvar** raid in `_parts/bosses.md`).

| Realm tier | Vessel (named example) | Seats (pilot/crew/pax) | Hull-HP (realm-scaled) | Cruise speed | Signature |
|---|---|---|---|---|---|
| Awakened | **Reed Skiff** | 1 / 0 / 1 | ~400 | 1.0× walk | Cheap, paper-thin; a starter river boat |
| Adept | **Coastal Junk** | 1 / 0 / 4 | ~3.2K | 1.4× | First real cargo + a harpoon mount |
| Champion | **Trawler "Deepnet"** | 1 / 2 / 6 | ~26K | 1.6× | Fishing-rig deck; Sea-Monster *bait* well |
| Ascendant | **War-Galley** | 1 / 4 / 10 | ~205K | 1.8× | 2 ballista hardpoints; ram prow |
| Paragon | **Leviathan-Hunter "Maelstrom"** | 1 / 6 / 12 | ~1.6M | 2.0× | Resists Nessuvar's maelstrom pull; 4 harpoon hardpoints |
| Transcendent | **Trench-Diver** (submersible) | 1 / 4 / 8 | ~13M | 1.7× | Dives — accesses the Drowned Trench floor; oxygen-shared hull |
| Demigod | **Tide-Sovereign Dreadnought** | 1 / 8 / 20 | ~105M | 2.2× | 6 Spirit-Cannon hardpoints; sails *over* the world border |
| Empyrean | **Abyssal Ark** | 1 / 10 / 30 | ~840M | 2.2× | A mobile sea-base; deployable Qi-Forge deck |
| Primordial | **"World-Edge Leviathan"** | 1 / 12 / 40 | ~6.7B | 2.5× | A *living-metal* ship that itself fights Sea Monsters; cloud-sails let it briefly skim airborne |

**Folia handling for ships:** a real `Boat` works for 1–2 seaters (vanilla passenger + region thread). Multi-seat ships are a **block-display hull rig** with discrete seat entities (invisible interaction/armor-stand seats); the hull entity owns movement, seats are passengers, so the whole rig moves as one parent on the **owning region thread**. Crossing a region boundary uses **`teleportAsync`** on the hull (which carries passengers), then re-resolves seat UUIDs on the destination thread — never touching another region's entities directly.

#### 2.2 Rail & Carts

A logistics/transit spine, gated by **Navigation**. Carts give the fast-travel backbone between player towns and into dungeon depths; high realms add powered, self-propelling, and combat carts.

| Realm tier | Cart (example) | Capacity | Function |
|---|---|---|---|
| Adept | **Push-Cart** | 1 | Manual, vanilla-ish; slow |
| Champion | **Essence-Cart** | 1 / 1 cargo | Self-propels on rail, **burns Essence** instead of needing a powered-rail furnace |
| Paragon | **Caravan-Train** (linked carts) | 1 pilot / 4 linked cargo cars | A chained convoy; one pilot drags N cars |
| Demigod | **War-Rail Battery** | 1 / 2 gunner | A rolling weapon platform — Spirit-Cannon hardpoints, used in siege/escort events |
| Empyrean+ | **Phase-Rail Shuttle** | 1 / 8 pax | Rides a *ghost rail* that auto-lays ahead and dissolves behind — fast inter-region transit; `teleportAsync` hops between rail segments across region borders |

#### 2.3 Ground Constructs / Mechs

The "pilot a body bigger than yourself" fantasy, gated by **Riding + Engineering**. A mech is a `WALKER_MECH` rig (display-entity body, server-driven walk cycle, player mounted at a hidden seat). The pilot's inputs drive the rig's `setVelocity`/`teleportAsync` stepping; the mech has its **own hull-HP pool** that absorbs hits before the pilot, **its own armor-pen and suppression realm** (a Demigod mech *suppresses* lower-realm foes even if a lower-realm pilot is inside, capped by the seal rule), and weapon hardpoints.

| Realm tier | Mech (example) | Hull-HP | Role |
|---|---|---|---|
| Champion | **Labor-Frame "Mule"** | ~26K | Hauling/mining utility; +Mining yield while piloted |
| Ascendant | **Skirmish-Walker** | ~205K | Light combat; dash-stomp AoE |
| Paragon | **Bastion-Mech** | ~1.6M | Tank; deployable shield-wall (raises a wall of `defense-bar` for nearby allies) |
| Demigod | **Siege-Colossus** | ~105M | Anti-boss/anti-fortress; 4 hardpoints; ground-slam shockwave scales with target *max* HP |
| Empyrean+ | **Realm-Titan** | ~840M+ | A walking fortress; projects a **suppression aura** as it walks (the "a god strides into the field" effect) |

#### 2.4 Airships / Sky-Skiffs / Cloud-Barges (the marquee multiplayer platform)

The headline vehicle: **moving rideable platforms** a whole crew lives and fights on. An `AIRSHIP_PLATFORM` is a **block-display hull** (the deck, masts, cloud-stuff) with mounted seats; it flies by the same Essence engine as personal flight (§7) but pays a **per-hull `essence-burn`/sec** drawn from the **pilot's** Essence pool (high realms add a shared crew **Essence-condenser** that lets gunners feed the hull). It floats (hover) for free and **burns Essence only to climb/cruise**, so a parked airship is a free sky-base.

| Realm tier | Sky-vehicle (example) | Seats (pilot/gun/pax) | Hull-HP | Ceiling (Y) | Cruise | Signature |
|---|---|---|---|---|---|---|
| Champion | **Sky-Skiff** | 1 / 0 / 2 | ~26K | build-limit −40 | 1.4× | Two-seat personal flyer; the entry airship |
| Ascendant | **Cloud-Cutter** | 1 / 1 / 4 | ~205K | build-limit −20 | 1.7× | First gunner hardpoint; nimble |
| Paragon | **War-Junk** | 1 / 4 / 12 | ~1.6M | build-limit | 1.8× | 4 hardpoints; the classic combat airship |
| Transcendent | **Cloud-Barge** | 1 / 2 / 30 | ~13M | build-limit | 1.5× | The *people-mover* — a flying town square; deployable market stalls |
| Demigod | **Storm-Dreadnought** | 1 / 8 / 24 | ~105M | +64 over limit | 2.0× | 8 hardpoints; weather-aura (lightning storm follows it) |
| Empyrean | **Sky-Fortress "Empyrean Hold"** | 1 / 12 / 50 | ~840M | +128 | 1.8× | A flying guild-base: Qi-Forge, anchor-tether, respawn point |
| Primordial | **"Heaven-Vault"** | 1 / 16 / 80 | ~6.7B | unbounded | 2.4× | A *city*-scale barge; projects realm suppression over everything below it — the literal "a Primordial's sky-fortress blots out the sun" |

**Who pilots:** one **pilot seat** (steers, pays climb-Essence); **gunner seats** operate hardpoints; **passenger seats** are free riders. Pilot bonuses read **Navigation** (speed/ceiling/fuel-efficiency); gunner accuracy reads the gunner's **Ranged Precision**. Ownership (`cerb_vehicle_owner` PDC) gates who can pilot vs. ride vs. modify; a guild-shared flag allows crew control.

---

### 3. Crafting & Acquisition

Vehicles close the **gather → craft → ascend** loop. Most are **crafted** at realm-appropriate stations from realm-gated materials; the apex few are **boss-keyed drops** (soulbound, un-craftable), exactly like Divine Artifacts.

| Path | Applies to | How |
|---|---|---|
| **Crafted (Engineering/Smithing)** | the generative bulk of all four families | A `Shipwright's Berth` / `Mech Forge` / `Sky-Dock` station + realm-tier hull frames + Essence-cores. Recipe in `vehicle-items.yml`; output carries `cerb_vehicle_id` + realm + quality roll. |
| **Tamed-beast-drawn variants** | hybrid living/non-living | **Animal Taming** lets a tamed realm-beast *draw* a cart/barge (a tamed sky-serpent towing a cloud-barge). The beast is the living mount system; the vehicle is the platform it pulls — the two systems compose. |
| **Boss-keyed apex hulls** | Primordial named vehicles | **"World-Edge Leviathan"** drops from a deep-ocean Sea-Monster apex; **"Heaven-Vault"** from a Maw apex. Soulbound, single-rare per server. |
| **Salvaged / repaired wrecks** | mid-tier shortcut | Derelict hulls spawn in the world (shipwrecks, crashed sky-skiffs); Engineering + parts restores them — a Navigation/Scavenging treasure loop. |

Vehicles use **entropy durability** like all gear: hull-HP regenerates out of combat, but **entropy** (repair fuel) drains with use and from taking hull damage; at full entropy a vehicle floors at ~50% performance until repaired at its station. This paces vehicle power and gives shipwrights a permanent service economy.

---

### 4. Passenger Capacity, Piloting & Folia Seat-Rigs (the multiplayer engine)

Multi-seat vehicles are the hardest Folia problem here, so the design is explicit:

- **Seat model:** each seat is a lightweight `interaction`/`armor_stand` entity parented to the **hull entity**. The hull is the single moving body; seats and their riders are passengers of the hull. **One entity moves; everyone rides.** This guarantees all occupants are on the **hull's owning region thread**.
- **Cross-region travel:** when the hull's next move crosses a region boundary, the pilot tick issues **`hull.teleportAsync(dest)`** — passengers travel with the parent. On completion the behavior re-resolves seat UUIDs on the destination thread. The hull **never reads or writes another region's entities directly**; it holds only UUIDs and re-resolves on its own scheduler (the exact pattern `_parts/bosses.md` uses for cross-region boss adds).
- **Pilot input → motion:** captured from the pilot's look-vector + movement keys (`PlayerInputEvent` / sneak-toggle / item-use, **no ProtocolLib**). The behavior translates to `hull.setVelocity` (for cruise) or stepped `teleportAsync` (for precise platform movement). Climb consumes the pilot's **Essence** at the hull's `essence-burn` rate; at 0 Essence the hull **gently auto-descends** (sinks/lands) rather than dropping like a stone — never a death sentence.
- **Capacity guard:** seat count is config (`seats:` map). A per-region **active-vehicle cap** and per-player **owned-vehicle cap** prevent a region thread from drowning in display rigs (same perf budget as elite-mob caps).

---

### 5. Durability & Vehicle-Combat

Vehicles fight as **realm entities with their own pool**, so airship battles and ship-vs-Sea-Monster duels feel like clashes of war-machines, not HP bags.

- **Hull-HP** is a Virtual Health `double` pool, drained through the **same 3-layer pipeline** (elemental resist → armor → defense-bar → hull-HP) via the same `applyDamageMultiplierToEntity` hook mob HP uses. Hull armor/resist come from the vehicle's realm + affixes.
- **Weapon hardpoints** mount **Spirit-Cannons** (reuse WEAPONS.md §2 #21 — Folia-safe **hitscan** via `World.rayTraceEntities` on the firing region thread, particle tracer + crack SFX, runs on Spirit Rounds or Essence) and **ballistae/harpoons**. Gunner damage = `weaponWP × gunnerRangedSkill × suppression`.
- **Realm suppression applies to vehicles:** a Demigod airship vs. an Ascendant one suffers the §3 POWER_SCALING suppression math — the higher-realm hull both ignores most incoming and amplifies outgoing. A Primordial sky-fortress over a low-realm zone is *effectively unassailable* from below, which is the intended power fantasy (and is gated so same-realm vehicle PvP stays a real fight, `gap=0 ⇒ identity`).
- **Destruction:** at 0 hull-HP the vehicle is *wrecked* — it drops a **recoverable hull-shard** (soulbound vehicles) or a salvage wreck (craftable ones), ejects passengers (who then fall — bring your Elytra!), and applies a repair/cooldown. No permanent loss of soulbound vehicles, matching the weapon-shard rule.

---

### 6. Personal Flight — the centerpiece (make the Elytra matter)

Most servers hand out an Elytra + a stack of rockets and call it done. Cerberus turns flight into a **five-stage realm-gated progression** worn in the `back` gear slot, powered by **Essence** instead of fireworks, expressive enough to have a skill ceiling, and culminating in true sustained flight that *feels* like a Primordial sky-strider. **No ProtocolLib** — every mode is the vanilla `elytra` glide flag plus server-side `setVelocity` boosts and a per-tick Essence ledger.

**The core loop:** equip a flight item → it sets the player's `elytra` flag while worn (gliding) → instead of firework rockets, **Essence-powered maneuvers** (boost / hover / dash / cloud-step / dive-bomb) consume Essence per use or per tick → flight depth, altitude ceiling, and Essence efficiency all **gate and scale by realm** and **Navigation** skill. Early realms = brief, capped, costly glides; top realms = sustained, near-free, ceiling-less flight.

#### 6.1 The five flight modes (the progression spine)

Each mode is a `back`-slot gear type (`function.type` in the §1 registry); higher modes unlock at higher realms and *include* the lower modes' maneuvers.

| Stage | Mode (item) | `function.type` | Unlock realm | Flight character |
|---|---|---|---|---|
| 1 | **Glider** (kite-cloak, paper-wing) | `GLIDER` | Awakened | **Descent-only glide.** No boost. Slows your fall, lets you cross gaps. Hard **altitude cap** (can't rise above launch Y). Tiny Essence drip just to stay gliding. |
| 2 | **Wingsuit** (gale-cloak) | `WINGSUIT` | Adept | Glide + a **forward Essence-dash** and a short **hop-boost** (replaces rockets). Still net-descending over time; brief altitude *gains* on boost. Stamina/Essence cost per boost. |
| 3 | **Spirit-Wings** (manifested qi wings) | `WING` | Champion | True **powered glide** — sustained **hover**, repeated **boosts**, **cloud-step** (a mid-air double-jump that refunds a little height). Net-level flight possible while you have Essence. Altitude cap raised high. |
| 4 | **Flying-Sword flight (yujian)** | `SWORD_FLIGHT` | Paragon | **Ride your weapon.** Reuses the Flying Sword archetype (WEAPONS §2 #15) — the sword detaches into a flight platform you stand on. **Fast, agile, ceiling-less**, can fight *while* flying (the sword still strikes). The signature cultivation flight. |
| 5 | **Sky-Striding / Cloud-Treading** | `FREEFLIGHT` | Empyrean → Primordial | **Near-free true flight.** Hover and cruise cost a *trickle* of Essence (Empyrean) → effectively **free, creative-like flight bounded only by a vast Essence reserve** (Primordial). No altitude cap; cross-region `teleportAsync` cruise. This is "the immortal who simply *stands* on the air." |

#### 6.2 How flight unlocks & gates by realm (the central `flight.yml` curve)

Flight is gated on **three axes**, all in one config block so the whole feel tunes from one place — mirroring the `power-curve` single-source-of-truth doctrine:

```yaml
# flight.yml — single source of truth for personal flight
flight:
  # Essence cost per boost and per second of powered flight, by mode (a flat Essence number;
  # Essence pool scales ×8/realm via power-curve, so the SAME cost is trivial at high realm).
  boost-cost:   { GLIDER: 0, WINGSUIT: 18, WING: 12, SWORD_FLIGHT: 9, FREEFLIGHT: 4 }
  hover-cost-per-sec: { WINGSUIT: 0, WING: 8, SWORD_FLIGHT: 5, FREEFLIGHT: 1.5 }
  # Altitude ceiling RELATIVE to launch Y (blocks). -1 = unbounded.
  altitude-cap: { GLIDER: 0, WINGSUIT: 24, WING: 96, SWORD_FLIGHT: -1, FREEFLIGHT: -1 }
  # Realm gates which modes are wearable at all (seal rule applies below this).
  unlock-realm: { GLIDER: COMMON, WINGSUIT: UNCOMMON, WING: RARE, SWORD_FLIGHT: LEGENDARY, FREEFLIGHT: GODLY }
  # Navigation skill (1-100) reduces costs and raises ceilings within a mode.
  navigation-efficiency: 0.004   # each Nav level cuts Essence cost by 0.4% → -40% at L100
  navigation-ceiling-bonus: 1.0  # each Nav level +1 block to a capped mode's ceiling
```

**The gating logic — why early flight is limited and late flight is godlike, using the *same numbers*:**

- **Essence is the universal fuel, and the Essence pool scales ×8/realm** (POWER_SCALING §2). A Wingsuit boost costs a flat **18 Essence**. At **Adept** (pool ~hundreds) that's a meaningful, rationed cost — a handful of boosts then you glide down. At **Empyrean** (pool in the *billions*) the same 18 is a rounding error, so flight is effectively free. **The cost number never changes; the realm makes it cheap.** This is the same "magnitude inflates, gameplay ratio conserved" trick the whole game uses — no special-casing.
- **Altitude cap** hard-walls early flight (`GLIDER 0` = descend only; `WINGSUIT 24`; `WING 96`) and is **lifted entirely at Flying-Sword and above** (`-1`). So a low realm *cannot* simply fly over content — they glide and hop; a high realm owns the sky vertically.
- **Mode unlock** is realm-gated and **sealed**: equip a Spirit-Wings at Adept and the seal rule runs it at your realm cap (i.e. like a Wingsuit) until you reach Champion. No buying your way into the sky.
- **Navigation** is the *skill-expression* axis: it cuts Essence cost (up to −40% at L100) and raises capped ceilings, so a flight-main who grinds Navigation flies meaningfully longer/higher than a same-realm player who didn't — flight has a skill ceiling, not just a realm gate.

#### 6.3 The Essence-powered maneuver kit (what replaces fireworks, and feels great)

All maneuvers are **server-side velocity impulses on the player's region thread** triggered by inputs (swap-hands key, sneak, look-vector) — **no ProtocolLib, no client mods.** Each consumes Essence from the per-tick ledger.

| Maneuver | Input | Effect | Cost | Available from |
|---|---|---|---|---|
| **Boost** | swap-hands / use-item | Forward impulse along look-vector (the rocket replacement) — `setVelocity(look × boostPower)` | `boost-cost` | Wingsuit |
| **Hover** | sneak (hold) | Cancels descent, holds Y — gentle station-keeping | `hover-cost-per-sec` | Spirit-Wings |
| **Dash** | double-tap direction | Sharp lateral burst (dodge in the air) | 1.5× boost | Spirit-Wings |
| **Cloud-Step** | jump while airborne | Mid-air "double jump" — small upward impulse, refunds a little height (the cultivation cloud-step) | boost | Spirit-Wings |
| **Dive-Bomb** | look down + boost | Converts a steep dive into a ground-slam AoE on landing (damage scales with fall speed × realm) | 2× boost | Spirit-Wings |
| **Sword-Mount** | use Flying Sword item | Detach the yujian into a flight platform; cruise + fight | hover trickle | Flying-Sword flight |
| **Sky-Stride** | toggle | Sustained free cruise, optionally `teleportAsync` for long hauls | minimal | Sky-Striding |

**Feel-good details (all server-side):** boosts emit a particle trail + whoosh SFX; hover plays a soft hum and a ring particle; a **low-Essence warning** (HUD flash + audio) fires before you run dry; running out of Essence **converts to a safe controlled glide-down**, never an instant drop. Momentum/banking comes from the vanilla elytra physics we're riding on top of, so it already *feels* like flight — we're adding the powered layer Mojang left out.

#### 6.4 Realm-by-realm flight summary (the felt progression)

| Realm | Best mode available | What flight feels like |
|---|---|---|
| **Mortal** | none | Grounded. (Flight is an earned privilege of Ascension.) |
| **Awakened** | Glider | A controlled fall. Cross a ravine, descend a cliff. The first taste. |
| **Adept** | Wingsuit | Glide + a few boosts. You can *gain* a little height, briefly. Rationed and exciting. |
| **Champion** | Spirit-Wings | Real flight while Essence lasts — hover, dash, cloud-step. You can *patrol* the sky. |
| **Ascendant–Paragon** | Spirit-Wings → Flying-Sword | Sustained agile flight; at Paragon you **ride your sword**, fight airborne, no ceiling. |
| **Transcendent–Demigod** | Flying-Sword (mastered) | Fast, ceiling-less, near-costless aerial combat. The sky is yours. |
| **Empyrean** | Sky-Striding | True flight on a trickle of Essence. You simply *stand on the air* and go where you will. |
| **Primordial** | Cloud-Treading | **Effectively free creative-like flight**, bounded only by a vast reserve — the immortal who treads clouds across the world, sky-fortress in tow. |

---

### 7. No-Fly / Speed Zones (tied to safe & PvP zones)

Flight power must be reined in around safe hubs and weighted in PvP, done as **region rules**, not client hacks.

| Zone type | Flight rule | Implementation (no ProtocolLib) |
|---|---|---|
| **Spawn / safe hubs** | **No-Fly** — flight disabled, elytra flag forced off on entry, gentle auto-land | Region flag (reuse the existing `/region` system); on `PlayerMoveEvent` into a no-fly region, clear elytra + zero upward velocity on the region thread |
| **Town / market interiors** | **Speed-Capped** — gliding allowed, boosts disabled or weakened | Region flag caps `boostPower` and `hover` so you can't strafe through a crowded plaza at mach speed |
| **PvP / war zones** | **Full flight, suppression-weighted** | Flight fully enabled; realm suppression + the PvP damage scalar apply to air combat exactly as on the ground — a higher-realm flyer dominates, level-bracketed PvP keeps it from pure misery |
| **Dungeon / boss arenas** | **Per-arena ceiling** (often capped/disabled) | Arenas declare an `altitude-cap` override so you can't trivialize a telegraph boss by floating above its mechanics; some (Nessuvar's maelstrom) *require* mounts/flight to resist pull |
| **The Maw / deep zones** | **Flight allowed but suppressed** | Open sky, but the realm gap means an under-realm intruder who flies in is suppressed to near-helplessness — distance-is-difficulty enforced vertically too |

Zone rules live in the region config and are read on movement/boost events on the **owning region thread** — Folia-safe, instant on `/cerb reload`.

---

### 8. Skills, XP & the gather/loop integration

Vehicles and flight feed the existing **utility skills** so they're not a side-system but part of the progression spine:

| Skill | What flight/vehicles give it | Bonus it grants back |
|---|---|---|
| **Navigation** | XP for distance flown, distance sailed, new biomes reached by vehicle | The **flight skill**: −Essence cost, +altitude ceiling, +cruise speed, +airship fuel-efficiency |
| **Riding** | XP for piloting mechs/airships and time mounted | Pilot bonuses: vehicle handling, +hull control, faster mount/dismount, mounted-combat accuracy |
| **Animal Taming** | XP for taming realm-beasts that draw carts/barges | Bigger/stronger beast-drawn vehicles; tamed-beast tow capacity |
| **Engineering** (if present) / Smithing | Crafting hulls, mechs, hardpoints | Higher-realm vehicle recipes; +hull quality roll |
| **Fishing** | Ship-deck fishing rigs, Sea-Monster hunts | Deck rigs, Leviathan-harpoon synergy |

This makes **a flight-main / a ship-captain / a mech-pilot** distinct, viable playstyles, each with its own grind and ceiling.

---

### 9. Implementation map (build ON the existing engine)

| Need | Reuse / minimal add |
|---|---|
| O(1) vehicle resolution | **`VehicleRegistry`** mirroring `MobRegistry`/`GearRegistry`; `vehicle-types.yml` + PDC `cerb_vehicle_id` |
| Open-ended vehicle **types** | Data-driven **type registry** + `Map<String, VehicleBehavior>` keyed by `function.type` — new types = config, new behaviors = one handler class |
| Realm tiers / numbers | Existing `Tier` + `realmMult = REALM_BASE^realmIndex` (`power-curve`); hull-HP is a `double` pool, billions-capable; short-scale `K/M/B/T` display |
| Realm gating / seal rule | `required-realm` field per type (like Gear/Weapons) + the seal rule from [CULTIVATION_REALMS.md](CULTIVATION_REALMS.md) |
| Realm suppression | Reuse the §3 POWER_SCALING suppression math on vehicle and flight combat (hull realm vs. target realm) |
| Hull / vehicle combat | Route hull damage through the existing 3-layer pipeline + `applyDamageMultiplierToEntity`; hardpoints reuse the **Spirit-Gun hitscan** (`World.rayTraceEntities`) |
| Personal flight | Vanilla **elytra flag** (set while a `back`-slot flight item is worn) + server-side `setVelocity` boosts + a per-tick **Essence** ledger; **no ProtocolLib** |
| Flight curve | Single **`flight.yml`** block (costs, caps, unlocks, Navigation scaling) — one source of truth, hot-reloadable |
| Multiplayer seat-rigs | Block-display hull + parented seat entities; everyone is a passenger of one moving hull → all on its region thread; **`teleportAsync`** the hull for cross-region (passengers carried; UUIDs re-resolved on arrival) |
| Persistence | `player_flight` (mode, unlocks) + `player_vehicle` (owned hulls: realm, quality, entropy, affix-json) tables, auto-created like the existing ones; snapshot-on-region-thread → async write |
| No-fly / speed zones | Existing `/region` flags read on move/boost events on the owning region thread |
| Skills loop | XP hooks into **Navigation / Riding / Animal Taming**; bonuses read those skill levels |

---

### 10. Open decisions this section surfaces

- **Flight unlock realms** — ratify the `unlock-realm` map (Glider@Awakened … FREEFLIGHT@Empyrean). Pushing FREEFLIGHT later preserves the "earned sky" feel longer; earlier makes high-tier flight more available.
- **Essence economy pressure** — confirm flight costs (`boost-cost`/`hover-cost-per-sec`) don't starve casting builds at the realm where flight unlocks (a Champion who flies a lot has less Essence to cast). Consider a separate **flight-Essence reserve** vs. shared with Mana.
- **Airship crew-Essence model** — does the hull burn only the pilot's Essence, or can gunners/passengers feed a shared condenser? Affects how solo-pilotable big airships are.
- **No-fly default for spawn vs. flight-as-fast-travel** — how aggressively safe hubs disable flight (immersion/anti-grief) vs. letting flight be the fast-travel network players expect.
- **Vehicle PvP destruction stakes** — soulbound apex hulls drop a recoverable shard (proposed); craftable hulls leave a salvage wreck. Confirm no permanent loss of earned vehicles, matching the weapon-shard rule, and the repair/cooldown severity.
- **Per-region vehicle cap** — set the active-rideable-display-rig budget per region so airship fleets don't stall a region thread (mirror the elite-mob cap).
- **Flying-Sword flight vs. Flying-Sword weapon** — confirm the yujian can be *both* a flight platform and an active weapon simultaneously, and how its WEAPONS.md damage budget interacts when it's doubling as transport.

---

Source files referenced for grounding (all absolute):
- `C:\Users\Gabri\IdeaProjects\Cerb\docs\design\POWER_SCALING.md` (`realm-base 8.0` curve, `realmIndex`, suppression math, number formatting, double/long/BigInteger regimes)
- `C:\Users\Gabri\IdeaProjects\Cerb\docs\design\GEAR.md` (data-driven type registry + `GearBehavior` map, `back` slot, entropy durability, seal/`required-realm`)
- `C:\Users\Gabri\IdeaProjects\Cerb\docs\design\WEAPONS.md` (Flying Sword #15, Spirit-Gun #21 Folia-safe hitscan, realm-WP)
- `C:\Users\Gabri\IdeaProjects\Cerb\docs\design\NAMING.md` (canonical Realm names, Essence, Western-forward glossary)
- `C:\Users\Gabri\IdeaProjects\Cerb\docs\design\CULTIVATION_REALMS.md` (seal rule, Riding/Navigation mounts resist maelstrom pull, gear-type `requiredRealm`)
- `C:\Users\Gabri\IdeaProjects\Cerb\docs\design\_parts\bosses.md` (Tide-Caller Nessuvar, Sea Monster spawner reuse, cross-region scheduler/`teleportAsync` boss-add pattern)
- `C:\Users\Gabri\IdeaProjects\Cerb\docs\design\CULTIVATION_INTEGRATION.md` (Folia-safe + no-ProtocolLib doctrine, `teleportAsync`/`entity.getScheduler()` cross-region rule)

---

## Mounts & Vehicles — Systems & Integration

> *Generated 2026-06-08 (cultivation/xianxia design pass). Companion to the [master design plan](CERBERUS_DESIGN_PLAN.md), [GEAR.md](GEAR.md), and [POWER_SCALING.md](POWER_SCALING.md). This section designs the **shared plumbing** for Mounts & Vehicles — the open-ended registry, the summon slot + management GUI, persistence, realm-scaling math, skill/suppression ties, zone rules, economy, Folia threading, and where it slots into the build order. Per the live engine: it mirrors `MobRegistry` / `GearRegistry`, reuses the `power-curve` (POWER_SCALING §2), the 3-layer defense pipeline, the realm-suppression curve (POWER_SCALING §3), and the five auto-created DB tables (`player_custom_ids`, `player_skills`, `skill_effects`, `player_magic_find`, `visited_chunks`).*

The thesis: **the Elytra is the most under-used item on the average Minecraft server — a one-time firework-spam novelty with a flat power ceiling.** Cerberus inverts that. Personal flight, mounts, and vehicles become a **deep, realm-scaled progression spine** that rides the same single power curve as everything else: a Mortal stumbles on a clumsy glider; a Primordial *cloud-strides* across continents on a sky-fortress with billions of HP. Crucially, this is **the same data-driven pattern as Gear** — types, stats, and abilities live in config; new mount/vehicle types ship without code; special powers are a behavior-handler map keyed by ability type.

---

### 1. Design Goals & Boundaries

| Goal | How Mounts & Vehicles delivers it |
|---|---|
| **Personal flight is signature, not a toy** | An `Elytra`-class **Skywing** archetype is a *progression line*, not a single item: glide → assisted flight → sustained flight → realm cloud-striding, gated by **Navigation** skill and realm. Power, speed, and stamina all climb the realm curve. |
| **Open-ended, never a fixed enum** | A **data-driven Mount/Vehicle Registry** (`mounts.yml` / `vehicles.yml`), mirroring `MobRegistry` + `GearRegistry`. New types ship as config; special powers are a `MountBehavior` handler map keyed by `ability.type`. |
| **Two parallel concepts, one registry** | **Mounts** (living/bonded — beasts, flying swords, realm-spirits; level/loyalty; ridden) and **Vehicles** (constructed/owned — skiffs, gliders, sky-barges, sky-fortresses; no loyalty; piloted). Both resolve through one `RideableRegistry`. |
| **Cultivation scaling** | Mount/vehicle speed, HP, and flight derive from **one central curve** (`mount-curve` in `combat.yml`, a sibling of `power-curve`) — astronomical by realm, controlled and representable. |
| **Skill-tied** | **Riding** (combat/control), **Animal Taming** (bonding, loyalty), **Navigation** (flight stamina, speed, fast-travel) gain XP from and grant bonuses to mounts/vehicles. |
| **Realm-suppression aware** | A low-realm mount in a high-realm zone is **crushed** (POWER_SCALING §3) — speed-gated, stamina-drained, forced to ground. You cannot ferry a sub-realm friend deep on a flying beast (tied to the Blink-Gate USE-gating rail). |
| **Zone-safe** | No-fly / forced-dismount in safe zones and near PvP outposts; speed gating by zone realm; anti-exploit on cross-realm ferrying. |
| **Folia-safe** | Rideable entities are real entities → all ops on the **owning region thread**; cross-region travel via **`teleportAsync`**; snapshot-on-region-thread → async DB write. **No ProtocolLib.** |

**Boundary with Gear:** a Skywing *can* occupy the `back` gear slot (it is also a wearable Elytra), but its **flight behavior, stamina, and realm-flight rules are owned by this system**, not the gear stat aggregator. The registry reserves the link via a `gear-link` field; Gear only contributes its passive stats, never the flight logic.

---

### 2. The Rideable Registry (open-ended architecture)

This is the heart of the "no fixed list" requirement, and it mirrors `MobRegistry.resolveInternal` (PDC → name → pattern → fallback) and `GearRegistry` exactly.

#### 2.1 Two config files, one manager

```
plugins/Cerberus/
  mounts.yml      # MOUNT types (living/bonded): beasts, flying swords, realm-spirits
  vehicles.yml    # VEHICLE types (constructed): skiffs, gliders, barges, fortresses, skywings
```

A new `RideableRegistry` (sibling to `MobRegistry` / `GearRegistry`) loads both, builds:
- `typesById: Map<String, RideableType>` — the archetype/schema spine (O(1) resolution).
- Secondary indexes: `byClass` (MOUNT/VEHICLE), `byArchetype`, `byEntityBase` (which vanilla entity carries it), and a `patternEntries` list for matching display-name regex.
- A `Map<String, MountBehavior>` — the **behavior-handler map keyed by `ability.type`** (the only code that knows specific powers).

Resolution at summon/mount time is identical in spirit to `MobRegistry`: **PDC `cerb_rideable_id` tag (O(1)) → display-name exact → display-name pattern → entity-base fallback → UNREGISTERED**. PDC is authoritative — summoned rideables carry `cerb_rideable_id`, `cerb_rideable_realm`, `cerb_rideable_level`, `cerb_rideable_owner`.

#### 2.2 `RideableType` schema (the extensibility unit)

A **RideableType** is the equivalent of a `MobEntry` / `GearType`: it declares a class, a locomotion model, a stat schema, the realm band it appears in, and which abilities it carries. New mount/vehicle concepts = new entries.

```yaml
# mounts.yml
mounts:
  # --- A living bonded beast (Animal Taming) ---
  ashmane-courser:
    class: MOUNT
    locomotion: GROUND          # GROUND | AQUATIC | GLIDE | FLIGHT | CLOUD_STRIDE | PHASE
    entity-base: HORSE          # vanilla carrier entity (region-thread real entity)
    bonded: true                # has level + loyalty; tameable
    governing-skill: ANIMAL_TAMING
    realm-band: II              # appears in Qi Condensation band (UNCOMMON/RARE)
    stat-schema:
      speed:    { weight: 0.5, role: PRIMARY }
      hp:       { weight: 0.3, role: PRIMARY }
      stamina:  { weight: 0.2, role: SECONDARY }
    abilities:
      - { type: SPRINT_BURST, cooldown: 8.0, magnitude: 1.6 }
      - { type: GAP_CLOSE, trigger: SNEAK_JUMP }

  # --- A FLYING SWORD mount (ties to the existing yujian weapon archetype) ---
  azure-flying-sword:
    class: MOUNT
    locomotion: FLIGHT
    entity-base: ARMOR_STAND    # invisible carrier; sword model rides under feet
    bonded: true
    governing-skill: NAVIGATION
    realm-band: III
    weapon-link: yujian         # detaches as the Flying-Sword weapon when dismounted
    stat-schema:
      flight-speed:  { weight: 0.5, role: PRIMARY }
      flight-stamina:{ weight: 0.3, role: PRIMARY }
      hp:            { weight: 0.2, role: SECONDARY }
    abilities:
      - { type: SUSTAINED_FLIGHT }
      - { type: SWORD_DIVE, cooldown: 6.0, magnitude: 3.0 }   # dive-bomb attack

  # --- An apex realm-beast (PRIMORDIAL) ---
  maw-leviathan:
    class: MOUNT
    locomotion: CLOUD_STRIDE
    entity-base: ENDER_DRAGON
    bonded: true
    governing-skill: ANIMAL_TAMING
    realm-band: V
    boss-rideable: true         # uses boss-hp-factor; monstrous HP
    abilities:
      - { type: SUSTAINED_FLIGHT }
      - { type: REALM_AURA_PROJECT }   # projects the rider's suppression aura while mounted
      - { type: WORLD_BREAKER_BREATH, cooldown: 30.0, magnitude: 50.0 }
```

```yaml
# vehicles.yml
vehicles:
  # --- The signature personal-flight line (Elytra reborn) ---
  drifters-skywing:
    class: VEHICLE
    locomotion: GLIDE
    entity-base: NONE           # worn item, not an entity; uses real Elytra glide
    gear-link: back             # also occupies the `back` gear slot
    governing-skill: NAVIGATION
    realm-band: I
    stat-schema:
      glide-ratio:   { weight: 0.5, role: PRIMARY }
      flight-stamina:{ weight: 0.5, role: PRIMARY }
    abilities:
      - { type: GLIDE_BOOST, cooldown: 5.0, magnitude: 1.4 }

  cloudskiff:
    class: VEHICLE
    locomotion: FLIGHT
    entity-base: BOAT           # reskinned boat as a flying skiff
    seats: 2
    governing-skill: NAVIGATION
    realm-band: III
    abilities:
      - { type: SUSTAINED_FLIGHT }
      - { type: PASSENGER_TETHER }   # seats — but see §7 ferry anti-exploit

  # --- A future, unforeseen type — works with ZERO code changes ---
  sovereign-sky-fortress:
    class: VEHICLE
    locomotion: CLOUD_STRIDE
    entity-base: ARMOR_STAND    # multi-block model via passenger stack / display entities
    seats: 8
    realm-band: V
    boss-rideable: true
    abilities:
      - { type: SUSTAINED_FLIGHT }
      - { type: MOBILE_SPAWN_ANCHOR }   # a flying respawn point + storage
      - { type: BROADSIDE_BARRAGE, cooldown: 20.0, magnitude: 25.0 }
```

The **only code that knows about specific powers** is the `MountBehavior` handler set keyed by `ability.type` (`SUSTAINED_FLIGHT`, `SPRINT_BURST`, `SWORD_DIVE`, `REALM_AURA_PROJECT`, `MOBILE_SPAWN_ANCHOR`, …), registered in a `Map<String, MountBehavior>`. Adding a *new ability* = one handler class + a config entry; adding a new *mount/vehicle using an existing ability* = config only. This is exactly the `GearRegistry`/`MobRegistry` split between generic resolution and per-entry ability hooks.

#### 2.3 Locomotion models (the extensibility axis for movement)

| `locomotion` | Behavior | Realm gate (default) | Folia handling |
|---|---|---|---|
| `GROUND` | Walk/sprint on terrain; speed-scaled | none | entity move on owning region thread |
| `AQUATIC` | Water/lava traversal; no fall | none | inline |
| `GLIDE` | Elytra-style descending glide (the Skywing entry); stamina drains, no sustained altitude gain | **Mortal+** (R1) | worn Elytra; velocity tick on region thread |
| `FLIGHT` | Sustained powered flight; stamina-limited hover/climb | **Adept+** (R3) | velocity/teleport on region thread |
| `CLOUD_STRIDE` | High-speed sustained flight, near-infinite stamina, cross-region | **Empyrean+** (R9) | `teleportAsync` for cross-region hops |
| `PHASE` | Short blink/teleport-dash (flying-sword "sword-flight" feel) | **Champion+** (R4) | `teleportAsync`, cooldown-gated |

`GLIDE → FLIGHT → CLOUD_STRIDE` is the **personal-flight progression ladder** — the deliberate fix for the wasted Elytra: a player earns each rung through Navigation level + realm, so flight *deepens* across the whole game instead of peaking at one item.

---

### 3. The Mount Summon Slot + Stable/Garage/Hangar GUI

#### 3.1 The dedicated summon slot

The gear equip layout just expanded to **20 slots incl. 10 ring slots** (GEAR §2.1); there is room for a dedicated **mount slot**. It is added like any other slot — **one entry in `gear-slots.yml`** — but flagged so the Gear stat aggregator ignores it (it carries a *summon binding*, not passive stats):

```yaml
# gear-slots.yml  (addition)
slots:
  mount:  { branch: RIDEABLE, gui-position: 26, mandatory: false, summon-slot: true }
```

The `mount` slot holds a **bound-rideable token** (an item whose PDC carries `cerb_rideable_id` + the owned-instance UUID). The keybind/command **summons the bound rideable** at the player's feet on the owning region thread, and **stows** it (despawns the entity, persists state) on dismount or re-press. Only **one active rideable per player** at a time (config `max-active: 1`); summoning a second auto-stows the first.

#### 3.2 The Stable / Garage / Hangar GUI

A single management GUI (built with the existing `GuiBuilder` / `GuiIdentity` system from the GUI rewrite) with **three tabs** filtered by `class` + `locomotion`:

| Tab | Shows | Actions |
|---|---|---|
| **Stable** (`class: MOUNT`, bonded beasts) | Owned beasts: icon, realm, **level**, **loyalty bar**, HP | Bind to summon slot, rename, feed (loyalty), release (refund sink, §8) |
| **Garage** (`class: VEHICLE`, ground/aquatic) | Skiffs, ground craft | Bind, repair (entropy sink), upgrade-realm (§8) |
| **Hangar** (`locomotion ∈ {GLIDE, FLIGHT, CLOUD_STRIDE, PHASE}`) | Skywings, cloudskiffs, sky-fortresses, flying swords | Bind, repair, **flight-stamina upgrade**, attune-realm |

Each item tile renders via `CultivationNumberFormat` (POWER_SCALING §1.5) so a sky-fortress shows `840M HP` / `0.84 Heaven`, not a raw digit string. The GUI iterates the **owned-instances list** (loaded from `player_mounts` / `player_vehicles`), so adding a new mount type or even a new locomotion is reflected live on `/cerb reload` with no GUI code change — the tab filter is data-driven on `class`/`locomotion`.

Commands: `/mount summon [id]`, `/mount stow`, `/stable` (opens GUI), `/garage`, `/hangar` (all open the same GUI on the matching tab).

---

### 4. Persistence

Two new tables, **auto-created exactly like the existing five** (`player_custom_ids`, `player_skills`, `skill_effects`, `player_magic_find`, `visited_chunks` — see `DatabaseManager` lines 64–109) and added to the same idempotent `CREATE TABLE IF NOT EXISTS` startup block and the integrity-check whitelist:

```sql
CREATE TABLE IF NOT EXISTS player_mounts (
    custom_id     TEXT NOT NULL,
    instance_uuid TEXT NOT NULL,        -- the owned mount instance
    rideable_id   TEXT NOT NULL,        -- registry type id (FK-by-convention to mounts.yml)
    realm         INTEGER NOT NULL,     -- Tier ordinal (0..9); drives mount-curve + suppression
    level         INTEGER NOT NULL DEFAULT 1,   -- bonded mount level (1..100)
    loyalty       REAL    NOT NULL DEFAULT 0.0, -- 0.0..1.0 bond strength
    xp            INTEGER NOT NULL DEFAULT 0,    -- long-backed; never wraps
    nickname      TEXT,
    bound_slot    INTEGER NOT NULL DEFAULT 0,    -- 1 if in the summon slot
    entropy       REAL    NOT NULL DEFAULT 0.0,  -- durability/wear
    PRIMARY KEY (custom_id, instance_uuid)
);

CREATE TABLE IF NOT EXISTS player_vehicles (
    custom_id     TEXT NOT NULL,
    instance_uuid TEXT NOT NULL,
    rideable_id   TEXT NOT NULL,
    realm         INTEGER NOT NULL,     -- vehicles have a crafted/attuned realm, no loyalty
    flight_tier   INTEGER NOT NULL DEFAULT 0,   -- stamina-upgrade rung
    nickname      TEXT,
    bound_slot    INTEGER NOT NULL DEFAULT 0,
    entropy       REAL    NOT NULL DEFAULT 0.0,
    PRIMARY KEY (custom_id, instance_uuid)
);
```

Notes mirroring the engine's proven patterns:
- **`realm` stored as the `Tier` ordinal `INTEGER`** (0..9), exactly as `MobRegistry.tagLevel` stores level as `PersistentDataType.INTEGER`. Reconstituted via `Tier.values()[ordinal]`.
- **`xp`/`loyalty` are accumulators → `long`/`REAL`**, per POWER_SCALING §1.1 ("monotonically increasing counter must never wrap"). Loyalty is a bounded `0.0–1.0` `REAL`.
- **Snapshot-on-region-thread → async write** (CLAUDE.md "Use `AsyncSaveManager` for all file/DB saves"): on dismount/stow, read the live entity's transient state (current HP fraction, position is discarded) **inline on the owning region thread**, build an immutable snapshot record, hand it to `AsyncSaveManager` for the off-thread `INSERT … ON CONFLICT … UPDATE`. **Never touch a Bukkit entity off-thread.**
- **No per-tick persistence.** Loyalty/XP mutate in memory and flush on stow, logout, and the periodic save tick — same cadence as `player_skills`.
- **Versioned migration**: these are among the "first features to ship a versioned migration" (CULTIVATION_INTEGRATION N18) — added under a `schema_version` bump, clean v1 wipe acceptable.

---

### 5. Realm-Scaling Math (one central curve)

Mounts/vehicles derive **speed, HP, and flight stamina** from a single `mount-curve` config block in `combat.yml` — a sibling of `power-curve` (POWER_SCALING §2.2) that **reuses the same `realmMult` and `levelMult`** so nothing invents its own slope.

```
realmMult(realm)   = REALM_BASE ^ realmIndex(realm)        # REALM_BASE = 8.0 (shared with power-curve)
levelMult(level)   = 1 + LEVEL_SLOPE * (level - 1)         # LEVEL_SLOPE = 0.05 (bonded mounts only)

mountHP(realm,L)   = MOUNT_HP_ANCHOR * realmMult(realm) * levelMult(L)         # MOUNT_HP_ANCHOR = 120
mountSpeed(realm)  = BASE_SPEED * (1 + SPEED_PER_REALM * realmIndex(realm))    # additive, NOT geometric — see note
flightStam(realm)  = STAM_ANCHOR * realmMult(realm)^STAM_EXP                   # STAM_ANCHOR = 100, STAM_EXP = 0.5
```

**Critical design rule — speed is NOT geometric.** HP and stamina ride the ×8/realm curve (so a realm-beast can *survive* in a realm-appropriate zone and a sky-fortress legitimately has billions of HP), **but raw travel speed is additive and gently bounded** — an ×8/realm *speed* would teleport a Primordial across the map and shred the chunk loader. Instead, realm expresses itself in flight through **capability** (glide → sustained → cloud-stride → cross-region `teleportAsync` hops), **stamina** (low realms drop out of the sky; high realms fly indefinitely), and **suppression immunity**, not through a raw velocity that breaks the server.

```yaml
# combat.yml  (new block, sibling of power-curve)
mount-curve:
  # HP & stamina reuse the realm/level multipliers from power-curve
  mount-hp-anchor: 120.0       # COMMON L1 bonded-mount HP
  flight-stam-anchor: 100.0    # COMMON flight stamina units
  flight-stam-exp: 0.5         # sub-linear: stamina climbs but stays sane
  # SPEED is additive + capped (anti-chunk-thrash)
  base-speed: 0.30             # vanilla-ish ground speed
  speed-per-realm: 0.18        # +0.18 BASE_SPEED per realm step (Mortal..Primordial ≈ 1.0..2.6x)
  flight-base-speed: 0.9       # blocks/tick at GLIDE
  flight-speed-per-realm: 0.25
  flight-speed-cap: 4.0        # hard ceiling blocks/tick — protects chunk streaming
  # per-realm sanity ceiling on the mount HP pool (overflow rail, like realm-health-ceiling)
  mount-hp-ceiling:
    WEAK: 2.0e3
    PRIMORDIAL: 5.0e14         # well under double-exact 2^53; boss-rideable uses boss-hp-factor on top
```

**Representative numbers** (REALM_BASE=8, MOUNT_HP_ANCHOR=120, LEVEL_SLOPE=0.05; bonded mount at its band's rep level):

| Realm (Tier) | realmMult | Mount HP (HUD) | Ground speed | Flight capability | Flight stamina |
|---|---|---|---|---|---|
| Mortal (WEAK) | 0.125 | ~15 | 1.0× | none / borrowed glider | ~35 |
| Awakened (COMMON) | 1 | ~120–700 | 1.18× | GLIDE (Skywing) | ~100 |
| Adept (UNCOMMON) | 8 | ~5.8K (`5.8K`) | 1.36× | FLIGHT unlocks | ~280 |
| Champion (RARE) | 64 | ~46K (`46K`) | 1.54× | FLIGHT + PHASE-dash | ~800 |
| Ascendant (EPIC) | 512 | ~490K (`490K`) | 1.72× | FLIGHT, dive attacks | ~2.3K |
| Paragon (LEGENDARY) | 4,096 | ~3.9M (`3.9M`) | 1.90× | sustained FLIGHT | ~6.4K |
| Transcendent (PERFECT) | 32,768 | ~31M (`31M`) | 2.08× | high-altitude FLIGHT | ~18K |
| Demigod (MYTHIC) | 262,144 | ~250M (`250M`) | 2.26× | near-CLOUD_STRIDE | ~52K |
| Empyrean (GODLY) | 2,097,152 | ~2.0B (`2.0B`) | 2.44× | **CLOUD_STRIDE** (cross-region) | ~145K |
| Primordial (PRIMORDIAL) | 16,777,216 | ~16B (`16B`) | 2.6× | CLOUD_STRIDE, world-breaker | ~410K |

A **boss-rideable** (sky-fortress, Maw-Leviathan) applies `boss-hp-factor` (×20–×50) on top: a Primordial sky-fortress sits at **~330B–840B HP** (`330B`/`0.33 Heaven`), comfortably inside `double` precision (10¹¹ « 2⁵³). All of this reads from the **one `mount-curve` block**, tunable from a single place, and a `/cerb mount preview <realm> <level>` debug command (mirroring the `power preview` command in POWER_SCALING §4.4) prints the derived table with overflow-safety assertions (no value > 9.0×10¹⁵).

---

### 6. Skill Ties (Riding / Animal Taming / Navigation)

Mounts/vehicles are the **primary XP sink and bonus delivery** for three of the 35 skills. XP routes through the existing `SkillManager.addXp` (the rewritten `long`-safe path, POWER_SCALING §1.4).

| Skill | XP source (mount/vehicle actions) | Bonuses granted (per level) |
|---|---|---|
| **Riding** | Time mounted in combat, mounted kills, surviving falls/dives | Mounted **control** (turn rate, no-buck on hit), mounted-attack damage share, reduced dismount-on-damage chance |
| **Animal Taming** | Bonding a beast, feeding/loyalty gain, taming higher-realm beasts | **Loyalty cap** + loyalty gain rate, bonded-mount **HP/level cap**, tame-success vs realm gap, unlocks `boss-rideable` taming at high levels |
| **Navigation** | Distance flown, cross-region `teleportAsync` hops, discovering new chunks while mounted (ties to `visited_chunks`) | **Flight stamina** (the gate from GLIDE→FLIGHT→CLOUD_STRIDE), flight speed within the cap, fast-travel/recall, reduced no-fly penalties |

**Loyalty** (bonded mounts only) is the Animal-Taming flavor axis: it scales mount effectiveness (`effectiveStat = baseStat × (0.6 + 0.4 × loyalty)`), gates the apex abilities (`REALM_AURA_PROJECT` requires loyalty ≥ 0.8), and **decays** if a mount is left unsummoned or repeatedly dismounted by damage — feeding the feed/repair economy (§8). Loyalty/level/realm are persisted per-instance (§4).

The progression interlock: **Navigation level is the stamina gate, realm is the capability gate.** You cannot CLOUD_STRIDE until you are Empyrean (R9, capability) *and* have the Navigation stamina to sustain it — exactly mirroring the gear "body must hold the realm tool" rule.

---

### 7. Zone Rules, Realm Suppression & Anti-Exploit

#### 7.1 Realm suppression on mounts (low-realm mount in a high-realm zone gets crushed)

A mount/vehicle carries its own `realm`. When it operates in a zone whose **realm** (from `BandResolver`, the distance-as-difficulty curve) exceeds the mount's realm, the **same suppression curve from POWER_SCALING §3** applies to the *mount*:

```
gap = zoneRealmIndex - mountRealmIndex          # +ve = zone out-realms the mount
suppression on mount when gap > 0:
  - flight stamina drain ×(1 + SUPP_DR_PER_GAP-style ramp)   # high zones bleed a low mount out of the sky
  - speed multiplier reduced toward the §3 aura-weaken curve (auraWeaken = min(0.60, 0.15·g))
  - at gap ≥ SUPP_MAX_GAP (4): FORCED GROUNDING — flight/cloud-stride disabled, beast cowed
```

This makes "you flew too deep" a **mechanical wall**, not a suggestion: a Champion's flying sword carried into a Primordial zone is dragged out of the sky and crawls — exactly the cultivation "a junior's treasure can't carry them past their realm" trope, and the clean alternative to invisible walls. Conversely, a Primordial cloud-striding through a Mortal zone projects the **rider's** suppression aura (`REALM_AURA_PROJECT`), the "god descends on a cloud" power fantasy.

#### 7.2 Zone rules (no-fly / forced-dismount / speed gating)

Zone flags come from the existing zone system (safe zones, PvP outposts), checked on the region thread on a move/flight tick:

| Zone type | Mount rule |
|---|---|
| **Safe zone / spawn / town** | **No-fly + forced dismount**: flight disabled on entry, bonded mounts auto-stowed (snapshot → DB), gliders drop to walk. Prevents safe-zone griefing/clutter. |
| **Near PvP outpost (configurable radius)** | **Forced dismount** on the ground, **no-fly** — you must approach a contested outpost on foot, no fly-by capture. |
| **Open-world by realm** | **Speed/flight gating** via §7.1 suppression — capability is gated by the zone's realm vs the mount's realm. |
| **`cerb_depths` instances** | Per-floor flags (often no-fly to preserve floor design); never carries `BigInteger`-scale mount HP into the overworld. |

#### 7.3 Anti-exploit — no ferrying a low-realm player deep (Blink-Gate USE-gating reuse)

The exploit: a Primordial summons a flying mount/sky-fortress with passenger seats and **ferries a level-5 friend deep into a Primordial zone**, bypassing distance-as-difficulty (the exact thing realm-gated Blink Gates prevent, CULTIVATION_INTEGRATION Step 7). The fix **reuses the Blink-Gate USE-gating logic** directly:

- **Passenger realm check on the destination, not the vehicle.** A `PASSENGER_TETHER` / multi-seat mount evaluates **each passenger's own realm-eligibility for the entered zone** (the same `realmEligible(player, zoneRealm)` predicate the Maw Gate uses), not the pilot's.
- If a passenger is **realm-ineligible** for the zone they are being carried into → **forced ejection at the zone boundary** (teleported back to the last eligible position via `teleportAsync`), identical to a Blink Gate refusing a sub-realm USE.
- **Cross-region `teleportAsync` hops** (`CLOUD_STRIDE`, `PHASE`) run the same passenger eligibility gate per-hop, so you cannot async-teleport a passenger past their realm wall.
- Seats remain useful for **same-realm** group travel (a sect flying together), which is the intended fantasy; only the *cross-realm ferry* is blocked.

This makes ferrying obey the exact same covenant as gates: **geography + realm = access, and no vehicle is a backdoor around it.**

---

### 8. Acquisition Economy & Sinks

Acquisition mirrors the Gear generative+curated split and is **strictly earned** for the apex (EULA-safe, soulbound `cerb_bound` PDC; never purchasable — CULTIVATION_INTEGRATION §3 monetization).

| Source | What it yields | Sink it feeds |
|---|---|---|
| **Taming (Animal Taming)** | Bonded beasts — capture in the wild, success vs realm gap; apex `boss-rideable` beasts require high Taming + defeating them first | Bait/lure items (gathering loop), loyalty feed |
| **Crafting (Engineering/Crafting)** | Vehicles — skiffs, gliders, Skywings, cloudskiffs built from realm-banded mats | Material + Spirit-Stone (Marrow alias) cost per realm band |
| **Boss/quest drops (curated)** | The ~5 keyed uniques: *Maw-Leviathan*, *Sovereign Sky-Fortress*, the *First Flying Sword* | Soulbound on pickup; single-instance-per-server flex candidates (N15) |
| **Realm-attunement upgrade** | Raising a vehicle's `realm` to keep pace as you ascend | **Major Spirit-Stone sink** — geometric cost per realm step (mirrors gear temper cost) |

**Sinks** (the absurd-number economy needs them, POWER_SCALING demands intentional drains):
- **Entropy/repair**: mounts/vehicles accrue `entropy` from use and combat (the Gear durability model, GEAR §7); at full entropy they floor at ~50% power until repaired (Spirit-Stone + mats sink).
- **Loyalty feed**: bonded mounts decay loyalty when neglected; feeding consumes gathered food/treats (gathering loop sink).
- **Realm-attunement**: the dominant late-game sink — keeping a flying sword or sky-fortress realm-current costs geometrically more Spirit Stones per realm, so flight power is a *continuous* spend, not a one-time unlock (the explicit Elytra fix — flight stays a chase, never "solved").
- **Release/refund**: releasing a bonded mount returns a fraction of its attunement cost (a soft sink that keeps the Stable from becoming hoarding clutter).

---

### 9. Folia Threading & Cross-Region Travel

All rideables are **real entities** (or a worn Elytra), so every operation obeys Folia's region-threading (Folia 1.21.11, entity ops on the owning region thread, `teleportAsync` for cross-region):

| Operation | Thread / mechanism |
|---|---|
| **Summon** (spawn the carrier entity, tag PDC, mount player) | Owning region thread of the player's location; `entity.getScheduler()` for any deferred follow-up |
| **Mount tick** (velocity, stamina drain, zone/suppression checks) | Owning region thread of the *mount* entity; inline, no cross-thread reads of player state — snapshot player realm at summon |
| **Stow/dismount** | Region thread: snapshot transient state inline → hand to `AsyncSaveManager` → async DB write (§4) |
| **Cross-region flight** (CLOUD_STRIDE / PHASE crossing a region boundary) | **`teleportAsync`** for the carrier + passengers; passenger eligibility gate (§7.3) runs *before* the async hop resolves; re-acquire the new owning region thread on completion |
| **Aura projection** (`REALM_AURA_PROJECT`) | The §3 1 Hz region-thread radius scan, restricted to mounted elites/high-realm players (perf-bounded) |
| **Despawn safety** | Owner-logout / TTL / leash despawn reuses the elite-despawn pattern; orphaned carrier entities are swept on chunk load |

**No ProtocolLib** — flight is driven by real Elytra glide + server-side `Vector` velocity and `teleportAsync`, not client packet spoofing. The flying-sword/sky-fortress visuals use display/passenger-stacked entities and CustomModelData, consistent with the resourcepack approach in GEAR §3.2.

---

### 10. Build-Order Placement (where it slots into the existing 9-step plan)

Mounts & Vehicles is **additive content that depends on the curve, the registry pattern, suppression, zones, and the gate-gating rail** — so it threads into the existing plan (CULTIVATION_INTEGRATION §5) without reordering it:

| Step | Mount/Vehicle work folded in |
|---|---|
| **Step 0 (Hygiene + `power-curve`)** | Nothing new — but Mounts depend on `power-curve` existing. Confirm the **`mount-curve`** block is reserved here as a sibling. |
| **Step 1 (Number lock-down)** | Ratify the `mount-curve` constants alongside `power-curve` (MOUNT_HP_ANCHOR, speed-per-realm, flight-speed-cap, mount-hp-ceiling) so mount numbers are part of the one constants treaty. |
| **Step 2 (Number formatter)** | Reuse `CultivationNumberFormat` for the Stable/Hangar GUI tiles — no new util. |
| **Step 4 (ZoneManager/BandResolver)** | Mounts read `zoneRealm` from `BandResolver` for §7 suppression/zone rules. Mount zone-flags (no-fly, dismount radius) are added to the zone config here. |
| **Step 5 (Weapons & Gear) → extend** | **`RideableRegistry` ships alongside `GearRegistry`** (same mirrored pattern), the `mount` summon slot is added to `gear-slots.yml`, and mount/vehicle suppression wires into the **same 3-layer pipeline insertion points** as gear. The flying-sword mount reuses the existing yujian weapon link. This is the natural home — same registry build, same authoring surface. |
| **Step 6 (Signature systems)** | Mount **abilities** (`MountBehavior` handlers — SUSTAINED_FLIGHT, dive attacks, aura projection) land with the other signature-verb behaviors. The Skywing personal-flight ladder is a Step-6 signature feature (the marquee Elytra fix). |
| **Step 6.5 (Cultivation/Ascension)** | **`player_mounts` / `player_vehicles` tables** ship with the versioned-migration batch (`player_cultivation`, `player_spirit_weapon`, `player_gear`) — same auto-create + `schema_version` bump. Loyalty/realm-attunement tie to the cultivation realm layer. |
| **Step 7 (Blink Gates + economy)** | **Ferry anti-exploit reuses the gate USE-gating** built here (§7.3); the realm-attunement Spirit-Stone sink plugs into the same economy. |
| **Step 8 (Endgame/PvP)** | `boss-rideable` apex mounts/sky-fortresses as endgame chases; `cerb_depths` per-floor no-fly flags; mount HP stays `double` (never `BigInteger` — mounts are overworld entities, POWER_SCALING §1.1 forbids `BigInteger` outside instances). |

**One-line placement summary:** *Mounts & Vehicles is a Step-5 registry sibling (`RideableRegistry` + summon slot) with a Step-6 signature-flight layer, Step-6.5 persistence, and Step-7 economy/anti-ferry hooks — built entirely on the `power-curve`, the suppression curve, the zone resolver, and the gate-gating rail that earlier steps already stand up.*
