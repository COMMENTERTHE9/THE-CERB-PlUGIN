# Player & Mob Stat Ecosystem Report
**Date:** 2026-04-06
**Purpose:** Map current stats and establish combat-feel targets before designing the shared power score.

---

## Section 1: Player Max HP

**Storage:** In-memory only. `HealthLifecycleEngine.MutableState.currentHp` and `maxHp` fields, stored in a `ConcurrentHashMap<UUID, MutableState>` inside `PlayerVirtualHealthManager`. Default `maxHp = 100.0` (HealthLifecycleEngine.java line 6). NOT stored in the database — recalculated from skill levels at runtime.

**Starting HP:** 100.0 for a fresh player (HealthLifecycleEngine.java line 6, line 134).

**Every source that modifies max HP:**

| Source | Formula | At Lv 1 | At Lv 50 | At Lv 100 | File:Line |
|--------|---------|---------|----------|-----------|-----------|
| Base | — | 100.0 | 100.0 | 100.0 | HealthLifecycleEngine:6 |
| FirstAidSkill | `level * 1500.0` | +1,500 | +75,000 | +150,000 | FirstAidSkill:28 |
| FirstAidSkill (>100) | `(level - 100) * 3000.0` | — | — | +0 (starts at 101) | FirstAidSkill:37 |
| MiningSkill | `level * 2.0` | +2 | +100 | +200 | MiningSkill:32 |
| FarmingSkill | `level * 1.5` | +1.5 | +75 | +150 | FarmingSkill:34 |
| FishingSkill | `level * 1.0` | +1 | +50 | +100 | FishingSkill:44 |
| WoodcuttingSkill | `level * 1.5` | +1.5 | +75 | +150 | WoodcuttingSkill:31 |
| CookingSkill | `level * 1.0` | +1 | +50 | +100 | CookingSkill:29 |
| CraftingSkill | `level * 5.0` | +5 | +250 | +500 | CraftingSkill:46 |

**CRITICAL:** FirstAidSkill at `level * 1500.0` completely dominates. At level 100: 150,000 HP. All other skills combined at level 100 contribute ~1,200 HP. The FirstAid formula is catastrophically out of scale.

**Theoretical max HP at all skills level 100:** 100 + 150,000 + 200 + 150 + 100 + 150 + 100 + 500 = **151,300 HP**. Effectively all from FirstAid.

**Realistic max HP (ignoring FirstAid's broken scaling):** With all utility skills at level 50 and no FirstAid: 100 + 100 + 75 + 50 + 75 + 50 + 250 = **700 HP**. This is a more reasonable progression.

**Three HP checkpoints:**
- **Fresh player:** 100 HP
- **Mid-progression (skills ~30, no FirstAid):** ~250-350 HP (estimated)
- **Endgame (skills ~80, ignoring FirstAid):** ~700-1,000 HP (estimated)

---

## Section 2: Player Defense

**Defense Bar:**
- Base max points: **300** (DefenseBarManager:29, combat.yml:31)
- Max absorption: **70%** at full bar (DefenseBarManager:30, combat.yml:32)
- Formula: `absorptionRate = 0.70 * (barCurrent / barMax)` (DefenseBarManager:66)
- Regen: 15.0 points/sec after 7-second delay (DefenseBarManager:31-32)
- Absorption is NOT configurable per-player — it's always 70% max
- Bar maximum IS per-player: Heavy Armor Training adds bonus max points

**Heavy Armor Training defense bar bonus:**
- Scaling: STEADY preset, base 0.0, hard-cap 200.0, late-game-bonus 100.0
- At level 50: ~85 bonus points (estimated from STEADY curve)
- At level 100: ~150 bonus points (estimated)
- Max bar: 300 + 200 = **500 points** at cap

**Armor reduction:**
- Per-piece values (additive sum, combat.yml lines 20-28):
  - Leather: 2%, Chainmail: 3%, Gold: 2%, Iron: 4%, Diamond: 5%, Netherite: 6%
- Full netherite (4 pieces): 4 × 6% = **24%**
- Heavy Armor Training armor bonus: FLAT_DEFENSIVE preset, hard-cap **15%**
- Total max armor: min(24% + 15%, 40%) = **39%** (cap is 40%)

**Other defensive layers:**
- Elemental resistances (Survival skill): Fire cap 30%, Cold cap 20%, Drowning cap 20%
- Martial Expertise KB resist: cap 50% (doesn't reduce damage, only knockback)

**Realistic max total damage reduction from all sources:**
- Armor: 39% (with max HAT skill)
- Defense bar at full: 70% of remaining
- Combined: incoming 100 → after armor 61 → defense bar absorbs 42.7, passes 18.3 → **81.7% total reduction**
- After defense bar empties: armor alone → **39% reduction**

---

## Section 3: Mob HP

**How mob HP is set:** Mobs spawned via `/cerb spawn` get **vanilla default HP**. The spawn command (CerbCommand.java lines 370-425) calls `world.spawnEntity()` and sets PDC tags + custom name, but does NOT modify `MAX_HEALTH`. mobs.yml has no health field — only classification, xp-multiplier, and match rules.

**Actual HP values for the 7 registered mobs:**

| Mob ID | Entity Type | Vanilla HP | Custom HP | Source |
|--------|------------|-----------|-----------|--------|
| ender_dragon | ENDER_DRAGON | 200.0 | — | Vanilla default |
| wither | WITHER | 300.0 | — | Vanilla default |
| wither_skeleton | WITHER_SKELETON | 20.0 | — | Vanilla default |
| high_health_zombie | ZOMBIE | 20.0 | 1024.0 (only via dedicated spawner) | CustomHighHealthZombie:19 |
| sea_monster | GUARDIAN | 30.0 | 100.0 (only via SeaMonster class) | SeaMonster:21 |
| elite_zombie | ZOMBIE | 20.0 | — | Vanilla default |
| legendary_mob | (varies) | varies | — | Vanilla default |

**Tier-based HP scaling: DOES NOT EXIST.** No code anywhere modifies mob HP based on tier. The only tier-based mob scaling is damage output (Phase 3e).

---

## Section 4: Armor Reduction

**Values per piece (combat.yml lines 20-28):**

| Material | Per Piece | Full Set (4 pieces) |
|----------|----------|-------------------|
| Leather | 2% | 8% |
| Gold | 2% | 8% |
| Chainmail | 3% | 12% |
| Iron | 4% | 16% |
| Diamond | 5% | 20% |
| Netherite | 6% | 24% |

**Combination:** Additive. Sum of pieces + skill bonus, capped. (PlayerDefenseManager:58: `Math.min(cap, armorSum + skillBonus)`)

**Cap:** 40% hard limit (combat.yml:28: `max-reduction: 0.40`)

**Maximum total reduction:** 24% (full netherite) + 15% (HAT skill cap) = 39% (under the 40% cap)

**NOTE:** The `armor:` section applies per-piece reduction as a FLAT PERCENTAGE per piece, not per-slot. A netherite helmet gives the same 6% as netherite boots. There's no slot weighting (e.g., chestplate giving more than boots).

---

## Section 5: Player Damage Output

**Skills that modify outgoing damage (from CombatSkillProcessor):**

| Skill | Stat | Base | Hard Cap | At Lv 50 (est) | At Lv 100 (est) | Source |
|-------|------|------|----------|-----------------|------------------|--------|
| Blade Mastery | damage-bonus | 0.0 | 1.5 (150% bonus) | ~0.6 | ~1.2 | combat.yml:136-140, STEADY preset |
| Weapon Mastery | damage-multiplier | 1.15 | 2.5 (2.5x) | ~1.6 | ~2.1 | combat.yml:123-127, LATE_SURGE |
| Critical Strike | crit-chance | 0.10 | 0.75 (75%) | ~0.35 | ~0.60 | combat.yml:111-115, HARD_CAPPED |
| Critical Strike | crit-multiplier | 1.5 | 3.5 (3.5x) | ~2.2 | ~3.0 | combat.yml:116-120, LATE_SURGE |
| Weapon Mastery | lifesteal | 0.0 | 0.40 (40%) | ~0.15 | ~0.30 | combat.yml:128-133, HARD_CAPPED |

**Scaling formula:** `value = base + logScale * ln(1 + level) + lateBonus * sigmoid(level)`, clamped to [0, hardCap]. (ScalingFormula.java lines 89-112)

**Runaway scaling check:**
- Blade Mastery damage-bonus: caps at 1.5 (150% bonus). Reasonable.
- Weapon Mastery damage-multiplier: caps at 2.5x. Reasonable.
- Critical Strike crit-multiplier: caps at 3.5x. Reasonable.
- **No runaway scaling in damage skills.** All use ScalingFormula with hard caps. The FirstAid/ManaRegen runaway scaling is in utility skills, not combat skills.

**Realistic max DPS estimate:**

Netherite sword base damage: 8.0 (vanilla). Attack speed: 1.6/sec.

Fresh player (no skills): `8.0 × 1.6 = 12.8 DPS`

Endgame player (Blade Mastery 100 + Weapon Mastery 100 + Crit Strike 100):
- Base: 8.0
- Blade Mastery bonus: +1.2 (additive) → 8.0 × (1 + 1.2) = 17.6
- Weapon Mastery multiplier: ×2.1 → 17.6 × 2.1 = 36.96 (note: these stack multiplicatively in CombatSkillProcessor)

Wait — checking CombatSkillProcessor lines 51-55:
```java
baseMod += getBladeMasteryBonus(ctx);    // additive
baseMod += getWeaponMasteryDamageBonus(ctx);  // additive
double modified = ctx.getRawDamage() * baseMod;
```
They're BOTH additive to the same baseMod, not multiplicative. So:
- baseMod = 1.0 + 1.2 (blade) + (2.1 - 1.0) (weapon mastery returns the full multiplier, subtract 1 for the bonus portion) = 1.0 + 1.2 + 1.1 = 3.3
- modified = 8.0 × 3.3 = 26.4

With crits (60% chance, 3.0x multiplier):
- Average: 26.4 × (0.40 × 1.0 + 0.60 × 3.0) = 26.4 × 2.2 = 58.1
- DPS: 58.1 × 1.6 = **93.0 DPS** (endgame estimate)

**Three DPS checkpoints:**
- **Fresh player:** ~12.8 DPS (8.0 × 1.6, no skills)
- **Mid-progression (skills ~30):** ~25-35 DPS (estimated)
- **Endgame (skills ~80-100):** ~70-93 DPS (estimated)

---

## Section 6: Survivability and Time-to-Kill Targets

**NOTE:** The codebase has NO explicit time-to-kill rules, progression tier definitions, or combat duration targets. Everything below is a **(design proposal)** based on current values and intended RPG feel.

### Hits-to-die (how many hits before the player dies)

Assumptions for calculation: mob deals vanilla damage, player has Cerberus defense systems active.

| Scenario | Player HP | Armor | Defense Bar | Mob Base Dmg | Effective Dmg/Hit | Hits to Die | **(design proposal)** |
|----------|-----------|-------|-------------|-------------|-------------------|-------------|----------------------|
| Fresh vs COMMON | 100 | 0% | 300 bar, 70% abs | 3.0 | ~0.9 (bar full) → ~3.0 (bar empty) | ~45 (bar phase) + ~33 (no bar) = **~78** | (design proposal) Target: 15-25 |
| Fresh vs RARE (×1.38) | 100 | 0% | 300 bar | 4.1 | ~1.2 → ~4.1 | ~55 | (design proposal) Target: 10-15 |
| Fresh vs PRIMORDIAL (×4.6) | 100 | 0% | 300 bar | 13.8 | ~4.1 → ~13.8 | ~15 | (design proposal) Target: 2-4 |
| Mid vs RARE | 300 | 16% (iron) | 350 bar | 3.5 | ~0.9 → ~2.9 | ~150+ | (design proposal) Target: 20-30 |
| Mid vs EPIC (×1.79) | 300 | 16% | 350 bar | 5.4 | ~1.4 → ~4.5 | ~100 | (design proposal) Target: 12-20 |
| Endgame vs MYTHIC (×3.07) | 700 | 39% | 500 bar | 5.5 | ~1.0 → ~3.4 | ~350+ | (design proposal) Target: 15-25 |
| Endgame vs PRIMORDIAL (×4.6) | 700 | 39% | 500 bar | 8.3 | ~1.5 → ~5.1 | ~200+ | (design proposal) Target: 8-15 |

**Problem identified:** Current numbers give WAY too many hits-to-die. A fresh player survives ~78 hits from a COMMON mob. That's nearly two minutes of standing still getting hit. Combat has no urgency.

The defense bar (300 points, 70% absorption) combined with the low base mob damage (3.0 for a zombie) makes early-game combat trivially safe. The bar absorbs most of the damage, and by the time it depletes, the player has killed the mob.

### Hits-to-kill (player hits to kill a mob)

| Scenario | Mob HP | Player DPS | TTK (seconds) | Hits (at 1.6/sec) | **(design proposal)** |
|----------|--------|-----------|---------------|--------------------|-----------------------|
| Fresh killing COMMON (zombie 20 HP) | 20 | 12.8 | 1.6s | ~3 hits | (design proposal) Target: 3-5 |
| Fresh killing RARE (wither skel 20 HP) | 20 | 12.8 | 1.6s | ~3 hits | (design proposal) Same HP as zombie! Should be harder |
| Mid killing EPIC | 20-200 (vanilla varies) | 30 | 0.7-6.7s | 1-11 | (design proposal) Target: 15-25 hits |
| Endgame killing MYTHIC | ??? (no HP scaling) | 80 | ??? | ??? | (design proposal) Target: 30-60 hits |
| Endgame killing PRIMORDIAL | ??? (no HP scaling) | 80 | ??? | ??? | (design proposal) Target: 120+ hits (boss fight) |

**Problem identified:** Mob HP does not scale with tier AT ALL. A RARE wither_skeleton has 20 HP — same as a COMMON zombie. An EPIC mob could be a zombie with 20 HP. There's no differentiation. This is the single biggest gap in the combat system.

### Combat duration targets (design proposals)

| Encounter Type | Target Duration | Current Duration |
|---------------|----------------|-----------------|
| COMMON (trash mob) | 2-4 seconds | ~1.5s (too fast — mob dies in 3 hits) |
| RARE (minor elite) | 5-10 seconds | ~1.5s (same HP as COMMON) |
| EPIC (mini-boss) | 15-30 seconds | Undefined (no HP scaling) |
| LEGENDARY (named boss) | 30-60 seconds | Undefined |
| MYTHIC (dungeon boss) | 60-120 seconds | Undefined |
| PRIMORDIAL (raid boss) | 3-5 minutes | Undefined |

### Progression checkpoint definitions (design proposals)

**Fresh Player:**
- Level: 0-5 across all skills
- HP: 100
- Gear: Leather or no armor
- Weapons: Wood/stone tools
- Defense bar: 300 base, no HAT bonus
- Expected to fight: COMMON and UNCOMMON mobs only

**Mid-Progression Player:**
- Level: 20-40 across combat skills, 10-20 utility
- HP: 250-400 (from utility skill HP bonuses, excluding FirstAid)
- Gear: Iron or diamond armor
- Weapons: Iron/diamond tools
- Defense bar: 350-400 (with some HAT)
- Expected to fight: RARE and EPIC mobs

**Endgame Player:**
- Level: 70-100 across combat skills, 50+ utility
- HP: 700-1000 (excluding FirstAid)
- Gear: Full netherite
- Weapons: Netherite with custom enchants
- Defense bar: 450-500 (max HAT)
- Expected to fight: LEGENDARY through PRIMORDIAL mobs

---

## What's Missing

1. **Mob HP scaling** — The most critical gap. Mobs have no tier-based health. A PRIMORDIAL mob has the same HP as a COMMON mob if they're the same entity type. The shared power score MUST include mob HP.

2. **Mob defense/armor** — Mobs have no custom defense system. A PRIMORDIAL mob takes the same damage per hit as a COMMON mob. There's no armor, damage reduction, or toughness on mobs.

3. **Player progression tier** — No concept exists in the codebase. There's no "player level" that summarizes overall progression. Individual skill levels exist but there's no aggregate. The power score needs either a composite player level or a gear-score system to determine what tier of content a player should face.

4. **FirstAidSkill HP scaling is broken** — `level * 1500.0` HP per level completely breaks any HP-based balance. At level 10 it already adds 15,000 HP. This must be fixed (probably to use ScalingFormula with a hard cap around 200-500) before any balanced mob damage/HP design is possible.

5. **Mob damage base values** — mobs.yml has no `base-damage` field. The Phase 3e scaling multiplies vanilla damage, but vanilla damage varies by difficulty setting and mob type. A zombie on Hard hits for 6, on Normal for 4, on Easy for 2. The power score should probably define explicit base damage per tier rather than relying on vanilla defaults.

6. **Level system for mobs** — `resolveMobLevel()` always returns 1. The power score formula uses level as an input but there's no way to assign levels to mobs yet. This was documented as a future feature.

7. **Equipment system for mobs** — No way to equip Cerberus mobs with custom gear. Vanilla mobs can pick up items but there's no Cerberus-controlled equipment system tied to tier.

8. **Mob special abilities** — No tier-based special abilities (boss mechanics, attack patterns, status effects). Currently all mobs just do vanilla melee.
