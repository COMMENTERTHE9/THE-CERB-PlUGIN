# Attributes & Progression 2.0 — Technical Approach

*2026-10-24. Implements `docs/design/SYSTEMS_BASE_LAYER.md` §4 within the limits of `POWER_SCALING.md` §2 and §4. Owner decisions already made: attributes are **derived from skills and realm** (no manual points yet, but the architecture allows them later), and **kill XP is based on mob level and tier**, not HP.*

## Status (2026-10-24)

Increments 1–7 are built, unit-tested (81 tests) and verified on a Folia 1.21.11 sandbox with `tools/playtest/progression.js` and `admin.js attrs`. Increment 8 (persistence on the owner's server, load test) is open. Where the build differs from the plan below:

- **Fortitude adds on top** of the existing gear, Heavy Armor Training and Survival bonuses instead of replacing them, so no existing character loses defense. The limits still apply once (armor 40%, resist 50%).
- **No RealmSource yet.** Every player is Awakened and realms grant nothing until advancement exists; the AttributeSource interface is ready for it.
- **Total mitigation (75%) is reserved, not enforced.** Today's pipeline allows ~82% (40% armor, then a 70% bar); enforcing it is an owner decision.
- **Fixes found on the way:** QualityBuffs reset max health to 100 on every held-item change (would have wiped Vitality); an attributes.yml from an older version loaded zero skill weights; lifesteal could be farmed on targets without an HP pool; a late arrow could heal a dead shooter.
- **Behaviour notes:** Critical Strike hit XP follows real crits, which Agility now drives (more crits, more XP). Spells hitting mobs scale with Essence ×(1 + 0.02·Essence). Damage numbers now float over mobs (`combat.yml display.mob-damage-numbers`).

## The problem it solves

Skill levels barely change PvE combat. A zombie took 10 sword swings at Blade Mastery 1 and still 10 at level 100, because the combat-skill bonus is computed and then thrown away on the mob-damage path. The stat code that does exist is scattered across managers that each stack their own bonus: `PlayerDefenseManager`, Strength, Luck, Magic Find, and every skill's `applyEffect`. Most of them read one level shared by the whole server instead of the player's own.

## Architecture

```
 Sources (each pushes contributions when ITS inputs change)        Engine (pure, unit-tested)          Readers
 ─────────────────────────────────────────────────────────         ──────────────────────────          ───────────────────
 SkillSource    on level change / load      ─┐                     sum per attribute                   combat damage path
 RealmSource    on realm change (v1: all     │   per-player         + conditional (weapon class)        max HP / defense / mana
                players are Awakened)        ├─► contribution ──►   → apply caps once               ──► Stats page
 GearSource     on equipment change (later)  │   table              → derived combat stats              Admin inspector
 BuffSource     on apply / expire (dev buffs)│   (dirty flag)       → immutable AttributeSnapshot       kill-XP anti-grind
 Talisman/…     (future, same interface)    ─┘                     (cached until dirty)
```

- **`Attribute`**: VITALITY, ESSENCE, POWER, FORTITUDE, AGILITY, FORTUNE, as defined in the design.
- **`Contribution`**: (source id, label, attribute, amount, optional weapon class), for example *Blade Mastery → Power +50 (swords)*. The label drives the per-source breakdown on the Stats page and in the admin inspector.
- **`AttributeSource`** is the extension point. Gear, talismans, realms, buffs and a future manual-allocation layer each implement it. Combat code never changes when a new source arrives.
- **`AttributeCalculator`** (pure) turns contributions plus the config into an **`AttributeSnapshot`**: the six totals (base, plus a bonus per weapon class), the derived combat stats, and the breakdown. **All caps are applied here and only here.**
- **`AttributeService`** (runtime) keeps one contribution table and cached snapshot per player in concurrent maps. A source marks the table dirty, and the snapshot is rebuilt on the next read. Rebuilding only combines stored numbers and never touches Bukkit objects, so it is safe from any region thread. Each source gathers its inputs on the player's own thread, for example the equipped armor.
- **Config:** a new `attributes.yml` holds skill-to-attribute weights, realm base values, the derived-stat formulas, the caps, kill-XP v2, and kill switches for each integration. It is data-driven like `mobs.yml`.

## Skill → attribute mapping (v1 defaults, all in `attributes.yml`)

The weights follow the design's "drives / existing system" table. The tuning goal is that **a weapon skill at level L matches a level-L mob's HP growth**. Mob HP climbs ×(1 + 0.05·(L−1)) inside a realm, and the weapon's Power multiplier climbs the same way, so an equal-level fight keeps the same hit count. That is the design's within-realm TTK invariant.

| Attribute | Fed by (per level) | Drives (derived stat) |
|---|---|---|
| Power | Blade Mastery 1.0 (swords), Weapon Mastery 1.0 (axes, mace, trident) + 0.25 (all weapons), Martial Expertise 1.0 (unarmed), Ranged Precision 1.0 (bows) | damage multiplier ×(1 + 0.05·Power) for the matching weapon class |
| Agility | Critical Strike 1.0, Dual Wielding 0.5, Navigation 0.25, Stealth 0.25 | crit chance and crit multiplier (existing `critical-strike` curves, keyed by Agility) |
| Vitality | Survival 1.0, Heavy Armor Training 0.5, First Aid 0.5 | max HP = base 100 × (1 + 0.01·Vitality), so level 100 Survival gives ×2 |
| Fortitude | Heavy Armor Training 1.0, Survival 0.5, Defensive Magic 0.5 | armor reduction bonus, defense-bar size, fire/cold/drowning resistance |
| Essence | Intelligence 1.0, Mana Regeneration 1.0, Spell Weaving 0.5, Elemental Mastery 0.5, Arcane Knowledge 0.5 | max mana, mana regen, spell damage |
| Fortune | Scavenging, Lockpicking, Trading, Fishing 0.5 each | luck and magic find. **Shown, but nothing reads it until loot exists**; marked as such in the UI. |

## Limits (applied once, in the calculator)

| Limit | Value | Source |
|---|---|---|
| Total offense multiplier (Power × crit × future gear and buffs) | ≤ 6.0 × the level baseline | POWER_SCALING §4.2 `OFFENSE_BUDGET_CAP` |
| Crit chance / crit multiplier | ≤ 75% / ≤ 3.5× | POWER_SCALING §4.2, `combat.yml` |
| Armor reduction | ≤ 40% | design plan, `combat.yml armor.max-reduction` |
| Total mitigation (resist + armor + bar) | ≤ 75% | design plan §"global budget" |
| Lifesteal | ≤ 40% | `combat.yml weapon-mastery.lifesteal.hard-cap` |
| Every value | finite, ≥ 0, NaN-safe | engine invariant (unit-tested) |

## Combat integration (each piece behind its own switch)

1. **PvE offense.** In the player → mob path, the attacker's Power multiplier for the weapon class, the crit roll and lifesteal are applied **before** the hit drains the mob's HP pool. Today they are applied after, and thrown away. The weapon class comes from the held item for melee and from the projectile type for arrows.
2. **Vitality** sets max HP through `PlayerVirtualHealthManager` (the current HP fraction is kept). **Fortitude** extends `PlayerDefenseManager.updateDefense`: it adds to gear armor and the existing Heavy Armor Training and Survival bonuses (built that way so nobody's defense drops).
3. **Essence** sets max mana and regen through `PlayerManaManager`. Spell damage (Fireball) scales with it.
4. **PvP stays as it is today** (the `CombatSkillProcessor` path). Changing PvP is a separate design decision. One side effect: Vitality and Fortitude also apply when a player is hit by a player.

## Kill XP v2 (approved)

`xp = base × (1 + slope·(level − 1)) × tierMultiplier`, with defaults base 40 and slope 0.15, read from `attributes.yml`:

| Mob | XP |
|---|---|
| Common, level 1 | 40, the same as today, so early game is no slower |
| Common, level 50 | 334 |
| Rare, level 50 | 668, which matches the design's "~300–600" |
| Primordial, level 100 | 9,510 |

- **Anti-grind** (design plan §1.2): a mob 20 or more levels below the weapon skill's level pays ×0.25.
- **Ceiling:** at most 1,000,000 per kill, and the result is always finite. Awards go through the existing saturating `SkillProgression.award`.

## Stats page and admin tools

- **Player Menu → General becomes Stats.** It shows the six attributes, each with its value, what it does, and its top sources. It also shows derived stats as readable outcomes: "Swords: ×3.45 damage", "Crit 28% for ×2.1", "Max HP 160". A progression tile shows kill XP at your level and the next milestones.
- **Admin → Attributes becomes Live.** It adds per-player attribute inspection with the source breakdown, and a **simulator**: pick a skill and a level, and see the before/after attributes and derived stats without changing anything. Timed dev buffs on any attribute (BuffSource) support controlled tests.

## Performance

- **Per hit:** one map lookup, plus a few multiplications on an immutable snapshot. No recomputation, no allocation beyond the existing `CombatContext`.
- **Rebuilds** happen only when something changed (level-up, armor change, realm, buff). A rebuild costs about 35 skills × 1–2 contributions, which is microseconds.
- No timers. Buff expiry is checked lazily when the snapshot is read, plus one entity-scheduler task per active buff.
- **Memory:** one table and one snapshot per online player, removed on quit.

## Increments (each is built, unit-tested, and verified on the Folia sandbox)

1. ✅ **Kill XP v2:** pure formula and tests, wired into `MobKillListener`. Played: L1 40, L50 334, anti-grind ×0.25.
2. ✅ **Engine core:** Attribute, Contribution, Calculator (caps, derived stats) and `attributes.yml`, with unit tests.
3. ✅ **Service plus Skill source** (Realm source deferred, see Status), dirty-tracking on level change, join and quit. **Stats page** (`/stats`, Player Menu → Stats).
4. ✅ **PvE offense integration.** Played: swings to kill an L50 zombie 19.5 / 7.5 / 5 at Blade Mastery 1 / 50 / 100; equal-level fights 7 vs 7.5.
5. ✅ **Vitality and Fortitude integration.** Played: defensive skills 1 → 100: max health 102 → 300, a level-50 zombie hit costs 1.1–1.4% → 0.33% of health.
6. ✅ **Essence integration.** Played: max mana 1,040 → 3,020, regen 51.7 → 150.8/s, Fireball ×1.08 → ×5.04 vs mobs.
7. ✅ **Admin Attributes inspector, simulator and dev buffs** (Played: `admin.js attrs` 14/14).
8. ⬜ **Restart-persistence check on the owner's server, a performance pass under load.** An independent review of increments 2–4 is done; its findings are fixed.
