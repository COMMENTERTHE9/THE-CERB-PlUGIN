# Attribute Expansion: speed and handling stats (proposal, 2026-10-24)

*Status: proposal. Increment 1 (four stats) is built and measured, behind switches in
attributes.yml. Everything else here is a recommendation for the owner to accept or change.*

## Recommendation in one paragraph

Keep the six attributes (Power, Agility, Vitality, Fortitude, Essence, Fortune) as the only
primaries and add **secondary stats** computed by the same engine (`AttributeCalculator`). Each
secondary stat has two kinds of source: a primary attribute that raises it a little everywhere,
and the skill that owns the action (Blade Mastery → sword attack speed, Ranged Precision → bow
draw). Both are summed, then one diminishing-returns rule and one cap per stat apply. Gear and
buffs later add to the same sums as `Contribution`s, so no feature ever gets its own stat system.

**Why not new primaries.** A seventh attribute such as "Dexterity" or "Haste" would split
Agility's role, need its own feeder table and its own UI, and every primary multiplies balance
work. The owner's goal is that *training a skill makes the action itself better*; secondary stats
owned by that skill say exactly that, and the primary part keeps attributes meaningful.

## One formula

```
raw       = driver attribute x per-point  +  sum of skill (and later gear/buff) contributions
effective = cap x (1 - e^(-raw / cap))
```

Near-linear while small (slope 1 at zero), smooth, never above the cap: raw = cap gives 63% of the
cap, 2 x cap gives 86%. The cap is the hard ceiling; diminishing returns stop the last few
percent from being worth stacking. All numbers live in `attributes.yml` → `secondary`.

## The stats

| Stat | Raised by | What changes in the game | How | Cap | Status |
|---|---|---|---|---|---|
| **Attack speed** | Agility (all weapons) + Blade Mastery (swords), Weapon Mastery (axes, maces, tridents, spears) | Attack cooldown: full-strength hits come sooner | Vanilla attack-speed attribute, a temporary (never saved) modifier, updated when the main hand changes | **+25%**: a sword then reaches 2 hits/s, the 10-tick window in which a mob ignores further hits; faster would waste swings | **Built (increment 1)** |
| **Draw speed** (bows) | Agility + Ranged Precision | Time to full power: an earlier release flies as if drawn longer (speed, damage, critical arrow at full power) | Recomputed when the bow is released (`EntityShootBowEvent`) | **+50%** (full power at 0.67 s instead of 1.0 s) | **Built (increment 1)**. The pull animation is client-side and stays vanilla. |
| **Movement speed** | Agility + Pathfinding | Walking and sprinting speed | Vanilla movement-speed attribute, temporary modifier | **+20%** (= Speed I) | **Built (increment 1)**; replaces the old Navigation bonus, which ignored the player's level, ran on every step and was saved into player data |
| **Spell cooldown recovery** | Essence + Spell Weaving | Time between casts (Fireball: 1.0 s base) | cooldown / (1 + recovery) | **+50%** (0.67 s minimum) | **Built (increment 1)** |
| Crossbow reload speed | Agility + Ranged Precision | Charge time | Needs server control of charge time; Paper has no clean hook. Candidate: a per-player Quick Charge equivalent. | +40% | Proposed |
| Knockback resistance | Fortitude + Martial Expertise, Heavy Armor | Being knocked back less | Vanilla knockback-resistance attribute | 50% | Proposed: makes Martial Expertise's "not active" promise real |
| Gathering speed | Mining, Woodcutting, Farming, Herbalism | Block-breaking speed for that kind of block | Vanilla block-break-speed attribute, set per tool type | +50% | Proposed, after gathering skills get XP sources (VISION_ALIGNMENT gap) |
| Casting speed | Essence + Spell Weaving | Channel time | Spells are instant today | — | Later, when channelled spells exist |

Rejected: **reach** (PvP-sensitive and exploit-prone), **raw dodge/miss chance** (luck-gated
outcomes the vision avoids).

### Increment 1 default weights (attributes.yml → `secondary`)

| Stat | Per Agility / Essence point | Per skill level | Blade Mastery 1 → 50 → 100 (no other sources) |
|---|---|---|---|
| Attack speed | +0.1% (Agility) | +0.3% (Blade Mastery: swords; Weapon Mastery: axes, maces, tridents, spears) | sword hit every 12.5 → 11.2 → 10.6 ticks |
| Draw speed | +0.1% (Agility) | +0.4% (Ranged Precision) | Ranged Precision 1 → 50 → 100: full draw 1.00 → 0.86 → 0.78 s |
| Movement speed | +0.05% (Agility) | +0.1% (Pathfinding) | Pathfinding 100: +7.9% |
| Cooldown recovery | +0.2% (Essence) | +0.3% (Spell Weaving) | Spell Weaving 100 + Essence 150: Fireball every 0.74 s |

## PvP

These four stats are vanilla attributes or shared mechanics, so unlike Power (which only touches
player → mob hits) they would also change PvP. The established rule is that PvP changes are a
separate owner decision, so by default **secondary bonuses pause while a player is in PvP
combat**: hitting or being hit by a player tags both for 15 seconds; during the tag the attack and
movement modifiers are removed, bows draw at vanilla speed, spells use the base cooldown, and an
arrow that carries a draw bonus deals its vanilla damage if it hits a player.
`secondary.in-pvp: true` applies them in PvP too. **Decision for the owner:** keep PvP untouched
(default) or let speed progression count in PvP, possibly with lower PvP caps.

## Cost and threading (Folia)

- Modifiers are temporary attribute modifiers set on the player's own region thread when the
  attribute snapshot changes (join, level-up, buff, reload), when the main hand changes (Paper's
  equipment-change event) and when a PvP tag starts or ends. No per-tick work, nothing saved.
- Draw speed: one calculation per arrow. Cooldown: one multiplication per cast.

## Shown in the game

- Stats page: a "Speed & handling" tile with each stat's value, what raises it, raw value and cap,
  and its status (active / turned off / paused in PvP); proposed stats are listed as not built.
- Skill pages: the owning skill shows its per-level speed bonus and what the next level gives.
- Admin attribute inspector: the four values next to the other combat outcomes.

## Measured in game (sandbox Folia server, bot client, 2026-10-25)

From `tools/playtest/progression.js speed` (13/13 checks). Attribute values are read on the
server with `/attribute`, so they are what the game actually uses.

| What | Level 1 | Level 50 | Level 100 |
|---|---|---|---|
| Netherite sword, attacks per second (Blade Mastery) | 1.607 (a full swing every 12.45 ticks, 0.62 s) | 1.782 (11.22 ticks, 0.56 s) | 1.880 (10.64 ticks, 0.53 s) |
| Iron axe, attacks per second (Weapon Mastery; Blade Mastery 100 does not apply) | 0.904 | — | 1.058 |
| Swinging every 0.55 s: vanilla damage per hit (8 = fully charged) | 7.1 (hits land before the cooldown ends) | — | 8.0 (every hit fully charged) |
| Bow released after 0.60 s: power (vanilla 0.52) (Ranged Precision) | 0.52 | 0.63 | 0.71 |
| Bow released after 0.85 s: power, critical arrow | 0.81, no | 0.99, no | 1.00, **critical** |
| Movement speed (Pathfinding; base 0.100) | 0.100 | — | 0.109 (+9.0%) |
| Fireball while clicking constantly (Spell Weaving) | every 1,037 ms | — | every 810 ms |

PvP: a hit from another player removed the attack and movement modifiers at once; they came back
when the 15-second tag ended.
