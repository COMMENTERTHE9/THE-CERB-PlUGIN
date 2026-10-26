# Cerberus Design — Index & Status

*Generated 2026-06-08. The navigation + build-readiness layer over the full `docs/design/` set. Two new sections (TALISMANS, ECHO_FAMILIES) are folded in. Doctrine: controlled hyperscaling, Western-forward naming, data-driven registries. **Headline: design breadth now exceeds build depth — the bottleneck is decision-lock + Step 0, not more design.***

---

## 1. Doc Index

| Doc | Status | One-line purpose |
|---|---|---|
| [CERBERUS_DESIGN_PLAN.md](CERBERUS_DESIGN_PLAN.md) | on disk | Master plan — pitch, 8 pillars, the 9-step build order, exec summary; the spine all other docs hang off. |
| [OPEN_DECISIONS.md](OPEN_DECISIONS.md) | on disk | The "what's not yet locked" checklist — P0/P1/P2 decisions, 8 unflagged gaps, per-domain open questions. |
| [NAMING.md](NAMING.md) | on disk | Canonical player-facing names — resolves N13: Western realm ladder (Mortal…Primordial), "Ascension/the Path", qi→Essence. Display layer over `Tier`. |
| [CULTIVATION_INTEGRATION.md](CULTIVATION_INTEGRATION.md) | on disk | The doctrine pivot — retires anti-power-creep for controlled hyperscaling; enumerates 16 overrides (O1–O16), 20 new decisions (N1–N20), survivals, re-scoped build order. |
| [POWER_SCALING.md](POWER_SCALING.md) | on disk | The math spine — `power-curve` (realm-base, level-slope), realm/HP/damage anchors, suppression curve, BigInteger threshold, per-realm sanity ceilings. |
| [CULTIVATION_REALMS.md](CULTIVATION_REALMS.md) | on disk | The 10-realm progression layer — minor stages, bottlenecks, tribulations, Pills/Alchemy, Manuals/Techniques, Physiques, Sects; `player_cultivation` schema. |
| [WEAPONS.md](WEAPONS.md) | on disk | ~263 weapons, 21 archetypes (incl. 3 ranged) — WP realm anchors, refine/temper/affix/spirit/divine paths, `WeaponRegistry`. |
| [GEAR.md](GEAR.md) | on disk | ~265 items, 20 equip slots (10 rings) — two branches (Wearables + Equipment/Tools), `GearRegistry`, set bonuses, the reserved-unread `talisman-sockets` field. |
| [MOUNTS_AND_VEHICLES.md](MOUNTS_AND_VEHICLES.md) | on disk | ~40–60 forms (12 archetypes, ~10–14 evolution lines) — bonded mounts + constructed vehicles, 5-rung flight ladder, `RideableRegistry`, `mount` summon slot. |
| [TALISMANS.md](TALISMANS.md) | **NEW (not yet on disk)** | ~260 talismans, 10 effect-class archetypes — socketable/carried conditional effect-modules, separate Talisman Budget (inside global caps), `TalismanRegistry`, `player_talismans`. Owns Gear's reserved socket field. |
| [ECHO_FAMILIES.md](ECHO_FAMILIES.md) | on disk | Consolidates SAND/TIDE/ASH/FROST/MIRE into one faction system — family≡sect, attunement track, rituals, keyed armor, Family Wars; `family.yml`, folds into `player_cultivation.sect_id`. |
| [SYSTEMS_BASE_LAYER.md](SYSTEMS_BASE_LAYER.md) | on disk | **Base-layer frames** (not full specs) for Enchanting, Artifacts (cross-cutting rarity class + `artifact` slot), Bags, and Attributes (the 6-attribute stat-aggregation layer where all sources sum and all caps apply). |
| [../ROADMAP.md](../ROADMAP.md) | **owner direction 2026-10-25** | Current priorities and build order: performance first, decisions A–E, Player Menu animations, and the **flexible multiclass / ability-tree system** (≈25 proposed classes as combinable specialization trees, cross-class loadouts, the 21 weapon archetypes not locked to classes). |
| [CLASSES.md](CLASSES.md) | **proposal, 2026-10-25 (awaiting owner decisions, §10); Warrior, Mage and Duelist built as the Step 5 prototype (§11)** | Classes & abilities design (ROADMAP Step 4): classes as specialization trees, unlock-by-rank, 5 active / 3 passive / 1 keystone loadouts with exploit-proof switching, ~15 effect verbs, abilities = the designed Techniques, the 25-class roster review, Western-forward names for the 21 weapon archetypes, phased plan. |
| [LEVEL_UP.md](LEVEL_UP.md) | **built, 2026-10-26** | Level-up, milestone and class rank-up presentation: real gains, combined moments, no replay on login or admin corrections, `/levelup full|reduced|off`, abilities with requirements besides their rank. |
| [GATHERING.md](GATHERING.md) | **built, 2026-10-25** | The first gathering loop, Mining → Smithing → Equipment: resources, tools and custom drops as data (`resources.yml`), what can't be farmed (placed, generated, piston-moved blocks), Fortune, smelting and forging XP, forged quality in the attribute engine, the real-time XP display. |
| [WOODCUTTING.md](WOODCUTTING.md) | **built, 2026-10-26** | The second gathering loop: woods as data with a tree (level, axe tier, XP, heartwood, discovery), natural versus placed and grown wood, Felling I–IV and Canopy as milestone techniques (Sneak + cut), rest between fellings, protection (placed logs, builds, protection plugins, Folia regions), Fortune scoped to wood, chopping speed, heartwood grip and limbs, a synthetic custom tree. |
| [SKILL_PROGRESSION.md](SKILL_PROGRESSION.md) | **design, 2026-10-26 (owner's permanent rule)** | What every one of the 35 skills grows: primary attributes, profession stats (scoped Fortune and gathering speed), milestone capabilities at 10/25/50/75/100, needs, and what works today versus what is planned. |
| `_parts/bosses.md`, `_parts/loops.md` | on disk (fragments) | Source fragments feeding the master plan (boss roster, core loops) — not standalone deliverables. |

---

## 2. Completeness Map

> **Honest assessment: the plan is ~90% *designed* and ~0% *built*.** Eleven design surfaces are written; the codebase still carries the three P0 math landmines. Breadth has outrun build. Every "DESIGNED" row below is **blocked on a "pending DECISION" row, which is blocked on Step 0.**

### DESIGNED — content systems specified (config schema + registry + persistence)
- **Power/math doctrine** — controlled hyperscaling, the `power-curve` spine, suppression math, BigInteger boundary.
- **Realm ladder** — 10 realms, Western names canonical, 1:1 with Tier/Band/level.
- **Weapons** (~263), **Gear** (~265), **Mounts/Vehicles** (~40–60), **Talismans** (~260) — all generative archetype × realm × variant + curated uniques, each a registry mirroring `MobRegistry`.
- **Cultivation realm layer** — stages, bottlenecks, tribulations, Pills, Techniques, Physiques.
- **Echo Families** — consolidated faction system: attunement, rituals, keyed armor, Techniques, mount lines, Family Wars.
- **3-layer defense pipeline, elemental grammar, Reservoir Inversion** — specified as insertion points, not new engines.
- **Registry/persistence/Folia patterns** — proven shapes (PDC O(1) → name → pattern → fallback; snapshot→async; auto-create tables) reused across every new system.

### pending DECISION — numbers/choices to lock before authoring
- **N1 master `realm-base`** (8.0 vs 6.0-with-kinks vs accelerating-WP) — *three docs not yet mutually consistent.* Highest leverage.
- Per-realm HP/WP/damage **anchors + sanity ceilings** (N4); level-slope (N2); BigInteger threshold (N3).
- **Suppression bounds** — `SUPP_MAX_GAP` (+4 vs +3 vs +5), DR cap 0.99, PvP/intra-sect application (N5–N8).
- **Talisman budget fractions** (offense +1.0× / defense +0.8× / sustain +0.4×), socket-swap friction, suppression-counter −2 cap, Inscription-as-spine (36th skill?), charge model.
- **Echo Families** — token-name canon (D1), single-vs-multi membership (D2), **TIDE/FROST ICE collision** (D3 — blocks keyed-armor keying), switch cost, herald roster.
- **Mount count** (~40–60), one-vs-two registry, flight-speed-cap, flight-Essence-vs-shared-Mana.
- Carryover P0/P1 itemization: weapon base anchor (superseded by WP anchors — confirm), per-item ceiling, temper +3 vs uncapped, bind-on-pickup band, quality-enum unification.
- Cross-cutting **gaps**: monetization stance (EULA), schema-migration/wipe policy, concurrent-player target, MVP cut line, accessibility/HUD-density at trillions-magnitude.

### UNBUILT — no code yet (design only)
- **All three P0 math landmines still live in code**: XP `int` overflow (`100·2^(L-1)`), First-Aid `level×1500` runaway, mob `MAX_HEALTH` 1024 / flat-5000-cap collapse.
- `power-curve` config block — does not exist in `combat.yml`.
- `MobVirtualHealthManager` + the empty `applyDamageMultiplierToEntity` hook — unimplemented.
- Every new registry: `WeaponRegistry`, `GearRegistry`, `RideableRegistry`, `TalismanRegistry`, `EchoFamilyRegistry`, `RitualManager` — none exist.
- Every new table: `player_cultivation`, `player_gear`, `player_talismans`, `player_mounts`, `player_echo_meta`, `echo_war`, `echo_outpost`, `schema_version` — none created.
- `CultivationNumberFormat`, `BandResolver`/`ZoneManager`, `CombatSkillProcessor` realm-relative clamp — unbuilt.
- Repo hygiene: Creb/ nested repo not deleted, `target/` not un-cached, stale CLAUDE.md/AI_HANDOFF.md not updated.

---

## 3. Top Open Decisions to Lock BEFORE Building

The consolidated must-decide set, ordered by leverage. Pulled from OPEN_DECISIONS.md (P0 block) and CULTIVATION_INTEGRATION.md §4.

| # | Decision | Source | Recommended lock |
|---|---|---|---|
| 1 | **N1 — the single master `realm-base` (×8/realm).** Pick ONE curve; derive WP/HP/gear/mount/talisman from it. Three docs currently disagree. | INTEGRATION N1 | **Ratify Power-Scaling's 8.0** as the spine; re-express the others as functions of it. *Everything hangs on this.* |
| 2 | **Three P0 math landmines** — XP curve rewrite (hybrid polynomial, `long`, ~19M to 100), First-Aid kill, mob virtual-HP pool (replaces 5000/1024). | OPEN P0 ×3 | Adopt all three recommended defaults; these are anti-*bug* fixes that survive the doctrine pivot. **This is Step 0 itself.** |
| 3 | **Suppression bounds** — `SUPP_MAX_GAP=+4`, DR cap 0.99, PvP application, aura scope/radius. | INTEGRATION N5–N8 | Saturate +4 with a Δ≥+5 %-max-HP floor; 0.99 DR; realm-bracketed full-loot only ±1 realm. |
| 4 | **Talisman socket budget** — offense +1.0× / defense +0.8× / sustain +0.4× inside the global 6.0×/40%/75% caps; suppression-counter capped −2. | TALISMANS §4, §8 | Ship 1.0/0.8/0.4; global clamp catches over-stack; −2 suppression cap is non-negotiable (preserves "distance is difficulty"). |
| 5 | **TIDE/FROST ICE collision** — two families, one element breaks keyed-armor keying + reactions. | OPEN P1, ECHO D3 | **TIDE = water/LIGHTNING+Wet, FROST = pure ICE.** No 9th element. *Lock before keyed-armor keying (Step 5).* |
| 6 | **Single-vs-multi family membership** + token-name canon. | ECHO D1, D2 | Single `sect_id` (the commitment axis); canon = Glass Tear / Brine Pearl / Cinder Mote / Rime Crystal / Spore Husk. |
| 7 | **Mount count + registry shape** — ~40–60 forms; one `RideableRegistry` vs two; flight-speed-cap. | MOUNTS §Open | ~40–60 (deep-bond, not a 250-collectible); one `RideableRegistry`; flight-speed-cap 4.0 blocks/tick. |
| 8 | **Inscription as 36th skill?** — talisman crafting spine vs renaming a dormant skill (affects Mastery-Level math). | TALISMANS §8 P2 | Rename a dormant utility skill, not add a 36th, to keep Mastery-Level (sum/10) stable. |
| 9 | **Cross-cutting gates** — monetization (EULA-safe cosmetic-only), schema-migration + clean v1 wipe, concurrent-player target, MVP cut line. | OPEN "Gaps" | Cosmetic-only; `schema_version` + idempotent migrations + clean wipe; pin ~20–40 concurrent; draw the v1 cut line. |

---

## 4. Recommended Build Order (condensed 9-step)

The existing Step 0–8 skeleton survives; cultivation/talismans/mounts/families thread in rather than replace.

| Step | Deliverable | New systems folding in |
|---|---|---|
| **0 — Hygiene + virtual-HP + `power-curve` (UNBLOCK EVERYTHING)** | Commit staged work, kill Creb/ nested repo, `git rm --cached target/`, fix stale docs, add `schema_version`. Then: `MobVirtualHealthManager` + implement empty hook; author `power-curve`; delete `health-cap:5000` / `max-damage:10`. | Reserve `mount-curve`, `talisman-budget`, `family.yml`, `realm-sockets` blocks (no code). |
| **1 — Number lock-down (constants treaty)** | Ratify N1–N7 in one place: realm-base, level-slope, suppression bounds, BigInteger threshold, per-realm ceilings, WP anchors. | Mount-curve, talisman-budget fractions, family attunement curve enter the treaty. |
| **2 — Progression spine** | XP-curve rewrite, 35-skill audit, First-Aid kill, gathering cap +50%. **Add `CultivationNumberFormat`** (pure, unit-tested). | Inscription skill decision (talisman spine); number-formatter reused by all GUIs. |
| **3 — Bestiary + Families as data** | `mobs.yml` authoring; mobs read `power-curve`, carry their Band's realm (drives suppression aura). | Echo Families bestiary slices land here; token-name reconcile (D1). |
| **4 — ZoneManager / BandResolver** | Pure-function location → Band+Family+realm+risk; floor-tier (now floor-*realm*) rule; feeds suppression gap. | Family→biome→band map; mount zone-flags (no-fly/dismount); altar zones. |
| **5 — Weapons & Gear (the big build)** | `WeaponRegistry` (~263), `GearRegistry` (~265), affix/reforge/temper/spirit/divine, set bonuses, suppression wired into 3-layer pipeline. | **`RideableRegistry` + `mount` slot, `TalismanRegistry` + socket reader, keyed-armor `cerb_keyed_family` gate all ship here.** |
| **6 — Signature systems** | Reservoir Inversion (swap-by-fill-%), 8-element reactions, Defensive Magic (offhand+sneak, no ProtocolLib), traps, Sea Monsters. | **Talisman proc/summon behaviors, family Techniques + `FamilyBehavior`, the Skywing flight ladder (marquee).** |
| **6.5 — Cultivation / Ascension layer** | Stages, bottlenecks, tribulations (reuse boss system), Pills, Manuals, Physiques; `player_cultivation` + versioned migration. | `sect_id`/`attunement_json` pledge-switch flow; `player_talismans`/`player_mounts` tables; mentor field. |
| **7 — Blink Gates + economy** | Realm-gated gate USE; Spirit-Stone sinks; ferry anti-exploit. | Talisman/family re-attunement Spirit-Stone sinks; mount ferry reuses gate USE-gating. |
| **8 — Endgame / PvP / seasons** | Depth Spiral / Abyssal Ladder (`BigInteger`, instanced-only), power caps at PRIMORDIAL, realm-bracketed full-loot PvP. | **Family Wars** (outposts, war score, monthly seasons); boss-rideable apex chases; Convergence/Warden gates. |

*Dependency one-liner: Hygiene+virtual-HP+`power-curve` (0) → constants treaty (1) → sane XP + formatter (2) → mobs/families (3) → zones feed realm-gap (4) → weapons/gear/talismans/mounts/suppression, the big build (5) → signature verbs + flight (6) → cultivation/families persistence (6.5) → realm-gated economy (7) → BigInteger endgame + Family Wars (8).*

---

## 5. Strategic Note — Pivot to Build

**The plan has enough breadth.** Eleven design surfaces, ~1,050+ catalogued items across four registries, a coherent doctrine, and a consistent data-driven pattern (every new system mirrors `MobRegistry`). Adding a twelfth design doc has diminishing returns. The risk now is *permanent planning* — the OPEN_DECISIONS "scope-cut" gap and the integration brief both call this out explicitly.

**The bottleneck is two concrete things, in order:**

1. **Lock N1 (the master `realm-base`).** It is the single highest-leverage decision and it is *still inconsistent across three docs* (Power-Scaling 8.0, Cultivation-Realms 6.0-with-kinks, Weapons accelerating-WP). Until one master curve is ratified, no WP/HP/gear/mount/talisman number can be authored without risking a contradictory tuning. This is a one-sitting creator call, not a research task.

2. **Build Step 0.** Everything downstream reads `power-curve`, and `power-curve` does not exist in code. Step 0 is narrow and high-certainty: repo hygiene (commit + delete Creb/ + un-cache target/ + fix stale docs) → stand up `MobVirtualHealthManager` + implement the empty `applyDamageMultiplierToEntity` hook → author the `power-curve` config block → delete the dead `health-cap:5000` / `max-damage:10` keys. The three P0 landmines (XP overflow, First-Aid runaway, mob HP collapse) are *anti-bug* fixes that survive the doctrine pivot unchanged — they are owed regardless of any open decision, so they carry zero decision risk.

**Recommendation: freeze design, lock the ~9 decisions in §3 (a single creator session), and start Step 0.** The proven registry/persistence/Folia patterns mean the authoring surface (Steps 3–6) is config-heavy and low-risk *once the curve is real* — but only once. Write the canonical balance-constants doc (the treaty `combat.yml` + code comments point to) as the first artifact of Step 1, so the eleven design docs stop being eleven potential sources of drift and collapse onto one numeric source of truth.
