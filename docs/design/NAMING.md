# Cerberus — Naming & Terminology (canonical)

*Authoritative source of truth for player-facing names. Generated 2026-06-08.*
*This file SUPERSEDES the realm-name columns in [POWER_SCALING.md](POWER_SCALING.md), [WEAPONS.md](WEAPONS.md), and [CULTIVATION_REALMS.md](CULTIVATION_REALMS.md) wherever they disagree. It resolves open decision **N13** in [CULTIVATION_INTEGRATION.md](CULTIVATION_INTEGRATION.md).*

---

## Principle: cultivation skeleton, Western skin

Cerberus keeps the **cultivation/progression *systems*** — realm step-changes, realm suppression, breakthroughs, spirit weapons — because that's the genre's appeal. But the **player-facing *language* is Western-forward**, so a Western/RPG audience reads everything instantly with no xianxia background. This is purely a **display layer over the unchanged `Tier` enum** (`Realm.forTier(Tier)`), so naming costs nothing mechanically and can change anytime.

> "cultivation" is used in these design docs as the internal *genre reference* only. The player never sees the word — the player-facing system is **Ascension** (a.k.a. **the Path**).

---

## Canonical Realm ladder

Ten Realms, 1:1 with the ten tiers (and the ten Bands and the 1–100 level curve).

| # | Tier (enum, unchanged) | **Realm name (canonical)** | Epithet (optional flavor) |
|---|---|---|---|
| R1 | WEAK | **Mortal** | *Stirring* |
| R2 | COMMON | **Awakened** | *Vesselborn* |
| R3 | UNCOMMON | **Adept** | *Wellspring* |
| R4 | RARE | **Champion** | *Tidecaller* |
| R5 | EPIC | **Ascendant** | *Embersoul* |
| R6 | LEGENDARY | **Paragon** | *Rimeheart* |
| R7 | PERFECT | **Transcendent** | *Mirebound* |
| R8 | MYTHIC | **Demigod** | *Ashen Lord* |
| R9 | GODLY | **Empyrean** | *Maw-Eater* |
| R10 | PRIMORDIAL | **Primordial** | *Primordial Echo* |

- **Realm name** is the canonical, player-facing term used in UI, lore, and all docs.
- **Epithet** is optional world-flavor (tied to the Echo myth / families) usable as a subtitle or title — it is *not* cultivation jargon and may be kept or dropped per surface.

---

## Terminology glossary (swaps & keeps)

| Cultivation term | → Player-facing (canonical) | Note |
|---|---|---|
| cultivation / cultivating | **Ascension / ascending / the Path** | the overall progression system |
| cultivator | **ascendant** (lowercase) or just "player/character" | |
| realm (cultivation) | **Realm** | kept — universally readable |
| breakthrough | **Breakthrough** | kept — clear on its own |
| tribulation / heavenly tribulation | **Ascension Trial** (or just **Trial**) | the realm-up gate event |
| qi | **Essence** | the power/cast resource (the Mana pool's flavor name) |
| dao / dao comprehension / dao insight | **Mastery** / **Insight** | |
| sect | **Order** (or an **Echo Family** faction) | social/faction unit |
| spirit weapon | **Spirit Weapon** | kept — reads as Western fantasy too |
| spirit stones | **Spirit Stones** or **Essence Shards** | currency flavor |
| cultivation units (Qi/Marrow/Meridian/Pillar/Dao/Heaven/Primordium) | **`K / M / B / T / Qa`** short-scale (default) | the themed unit skin is retired as default; may return as an optional toggle |
| soulbound / soulbind | **Soulbound** | kept |
| Ascend / Ascension | **Ascend / Ascension** | kept — this is now the core verb |

**Keep (read to everyone, no change):** Realm, Breakthrough, Spirit Weapon, Soulbound, Ascend/Ascension, Essence Shards, Echo / Echo Family.

---

## Skill display names (Player Menu 2.0, 2026-10-24)

A light polish, not a renaming pass: four skills read better under a new name, the other 31 keep
theirs (Blade Mastery, Critical Strike... are already good names).

| Internal name (database, config, commands) | Shown to players |
|---|---|
| Heavy Armor Training | **Heavy Armor Mastery** |
| First Aid | **Field Medicine** |
| Mana Regeneration | **Essence Regeneration** (Essence = the Mana pool's name, see glossary) |
| Navigation | **Pathfinding** |

- Only text changes. The internal name stays the database key, the skills.yml / attributes.yml
  key and the XP route, so no level or XP moves. Code: `Skills/SkillNames.java`.
- Commands accept both names (`/cerb skill Steve Pathfinding level 5` = `... Navigation ...`);
  admin screens show "Pathfinding (Navigation)"; skill pages say "Formerly Navigation".
- A server can rename any skill in skills.yml: `skill-gui.<internal name>.display-name`.
- Descriptions are short identity lines; what a skill actually does is listed on its page with an
  honest status (live / against players only / not active yet). Catalog: `GUIs/SkillDefinitions.java`.

---

## Apply-going-forward

- New docs and code use the canonical Realm names and the right-hand glossary terms.
- Numbers display short-scale (`1.2B`, `3.4T`) by default — see [POWER_SCALING.md](POWER_SCALING.md) §1.5.
- The realm-name tables in the other design docs were reconciled to this set on 2026-06-08; any remaining cultivation jargon in *prose* (qi/dao/sect/tribulation) is legacy working-text and should be swapped per the glossary on the next editing pass.
