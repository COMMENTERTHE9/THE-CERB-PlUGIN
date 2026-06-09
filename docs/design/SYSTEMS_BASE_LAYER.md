# Cerberus — Base-Layer Systems

*Generated 2026-06-08. **Base layer only** — the structural frame for four more systems (Enchanting, Artifacts, Bags, Attributes), NOT full content specs. Each defines what it is, its boundary vs adjacent systems, the core shape, and the key decisions to lock. Flesh out later. Companion to the [index](README.md). Western naming per [NAMING.md](NAMING.md); magnitudes per the realm `power-curve` ([POWER_SCALING.md](POWER_SCALING.md)).*

---

## 1. Enchanting

**What it is:** Player-*chosen* magical enhancements applied to existing gear/weapons after crafting, via the existing **Enchanting** skill + an enchant altar. Realm-scaled vanilla-style enchanting.

**Boundary (so it doesn't overlap):**

| System | Effect shape |
|---|---|
| **Crafting affix** | *Random*, rolled at craft, locked |
| **Elemental Infusion** | The element + on-hit status layer (Fire/Ice/…) |
| **Talisman** | *Swappable* socketed conditional procs |
| **Enchanting (this)** | *Chosen*, applied-after-craft, semi-permanent stat/effect; costs Essence + materials; tier capped by Enchanting skill + realm |

**Core shape (base):**
- Each gear/weapon piece has **N enchant slots**, realm-gated (e.g. 1 at low realm → 4 at high).
- Enchant categories: **Weapon** (damage / crit / element-affinity), **Armor** (defense / resist / HP), **Tool** (efficiency / yield), **Utility** (movement / Essence).
- Enchant magnitude reads the central `power-curve`; **draws from the same capped budgets** (6× offense / 40% armor / 75% resist) — enchanting can't breach the caps.
- Data-driven `enchants.yml` registry (mirrors the others).

**Key decisions:** enchant slots per piece by realm · destructive reroll vs additive stacking · enchant budget vs affix budget separation · can high-realm enchants exceed a piece's craft tier.

---

## 2. Artifacts

**What it is:** Unique, **named**, soulbound items with a signature power — the top rarity class. Boss / quest / ritual-only.

**Boundary — Artifact is a cross-cutting CLASS, not a single slot:**

| Where an Artifact can live | Example |
|---|---|
| **Weapon** | Divine Artifacts ([WEAPONS.md](WEAPONS.md) §6) — already designed |
| **Gear** | Keyed / named-treasure pieces (GEAR §4 uniques) |
| **Mount** | Legendary mounts (MOUNTS §legendaries) |
| **Talisman** | Named seals (TALISMANS curated) |
| **Standalone accessory** | Passive Artifacts buff you from inside the **Artifact Bag** (Bags §3, SkyBlock accessory-bag model — stats apply while stored). The rare **active**-power ones bind to a quick-use hotkey. |

**Core shape (base):**
- An Artifact = unique item + a **signature power (active or passive)** + **soulbound** (`cerb_bound`, EULA-safe earned) + an **acquisition gate** + usually a **growth/awaken track**.
- Standalone passive Artifacts/accessories live in the **Artifact Bag** (passive while stored, SkyBlock-style — Bags §3); the rare active ones bind to a quick-use hotkey. No per-artifact equip slot.
- All other Artifacts ride their normal slot; "Artifact" just tags them as the apex tier with an active power + awaken track.

**Key decisions:** Artifact = cross-cutting rarity class (recommended) vs its own system · Artifact Bag start size + expansion curve + no-dup-supersede rule · passive-in-bag vs active-hotkey split · awaken/empower track shape · single-instance-per-server for the apex few.

---

## 3. Bags

**What it is:** Inventory / storage expansion — bags, pouches, packs that hold items beyond the base inventory.

**Boundary:** vs Gear **Equipment & Tools** (tools are *used*; bags *store*). Bags are their own utility category, not a gear branch.

**Core shape (base):**

| Bag type | Function |
|---|---|
| **Storage bag** | +N rows of general inventory |
| **Material pouch** | Auto-collects one category (ore / herbs / loot / drops) on pickup |
| **Ammo bag (Quiver)** | Holds **Spirit Rounds** (feeds the Spirit-Gun) + arrows |
| **Realm-bound bag** | Ender-style, accessible anywhere |
| **Artifact Bag** | Holds Artifacts/accessories — their passive effects apply ***while stored*** (Hypixel SkyBlock accessory-bag model); slots expand with progression |
| **Mount cargo** | Already covered by Pack-Beast mounts (MOUNTS) |

- Bag **capacity scales with realm/tier**; bags open in a dedicated bag panel.
- Open-ended `bags.yml` type registry (same pattern); ties to **Scavenging** / gathering skills.
- Persistence: `player_bags` table (auto-create, snapshot→async).

**Artifact Bag (the SkyBlock accessory-bag model — headline mechanic):** passive Artifacts/accessories buff you *just by being in the bag*, no per-item equipping. Rules: **no duplicate stacking** (a higher-tier copy supersedes the lower — chase upgrades, don't hoard); **slots expand** with realm/upgrades (~3 → grows); **all passive stats sum into the Attribute layer (§4) and clamp to the caps**, so a full bag can't breach the budget. The rare **active**-power artifacts bind to a quick-use hotkey instead.

**Key decisions:** how bags are accessed (bag panel vs equip slot) · auto-collect pouch scope/filters · bag capacity curve · do bags persist on death · Artifact Bag start size + expansion curve + the no-dup-supersede rule.

---

## 4. Attributes

**What it is:** The unified **stat-aggregation layer** — a small set of player attributes that *every* system feeds and that the combat engine *reads*. The "stat bus" between content and combat. (Architecturally the most important of the four.)

**Core attribute set (base — 6, each maps to an existing manager):**

| Attribute | Drives | Existing system |
|---|---|---|
| **Vitality** | max HP | Virtual Health pool |
| **Essence** | mana pool + regen, magic power | Mana manager |
| **Power** | offensive damage budget | CombatSkillProcessor |
| **Fortitude** | armor + resist + defense-bar | 3-layer defense |
| **Agility** | move speed, attack speed, crit, dodge | speed/crit |
| **Fortune** | luck, magic-find, drop rate | Luck / MagicFind |

**Aggregation model (the key structural point):**
```
finalAttr = base(realm) + Σ skills + Σ gear + Σ enchants + Σ talismans + Σ buffs
          → clamped to the per-realm capped budget (6× / 40% / 75% / realm-HP-ceiling)
```
Attributes are the **single point** where all sources sum and all caps apply — combat never reads a raw gear/skill number, only the final clamped attribute. This is what keeps "controlled hyperscaling" enforceable in one place.

**Key decisions:**
- **Manual allocation or pure-derived?** Do players spend points to customize attributes (Diablo-style build layer on top of use-based skills), or are attributes 100% derived from skills+gear+realm? *(Biggest decision — defines whether there's a manual stat-build layer.)*
- The exact attribute set (6 vs more) · do attributes have their own per-realm soft caps · HUD exposure of attributes.

---

## Where these slot into the build order

All four are **base-layer** and fold into the existing plan, not new phases:
- **Attributes** → **Step 1–2** (the aggregation layer is foundational; it *is* where the caps live — build it alongside the constants treaty so every later system contributes through it).
- **Enchanting** → **Step 5** (with Weapons & Gear — it's a gear-enhancement path).
- **Bags** → **Step 5–6** (utility; ammo bag pairs with the Spirit-Gun).
- **Artifacts** → **Step 5–6 + 8** (apex tier of each system; the dedicated `artifact` slot ships with gear, the awaken track with signature systems, the chase with endgame).
