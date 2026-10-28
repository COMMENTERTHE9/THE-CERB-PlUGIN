# Lifesteal review (decision 6), 2026-10-28

*Milestone "Combat Improvements, Replant Enchantment, Herbalism & Alchemy", increment 3. **Analysis and a recommendation only: nothing about lifesteal was changed.** The owner's rule for this milestone: "Do not rebalance, remove, or reduce lifesteal in this milestone. Any change to lifesteal requires my explicit approval." Decision 6 (2026-10-27) kept lifesteal "for now" and asked to reconsider once healing exists; Cooking's meals heal now, and Alchemy's draughts are next.*

## 1. What lifesteal is today (read from the code)

- **Where it comes from:** Weapon Mastery's level, through the lifesteal curve in `combat.yml` (`skills.weapon-mastery.lifesteal`: preset `HARD_CAPPED`, base 0, hard cap 0.40, late-game bonus 0.30 from level 50) into the attribute engine's `LIFESTEAL` stat, capped again by `attributes.yml` → `limits.lifesteal: 0.40`. Nothing else gives it: the old crafted lifesteal items (`Manager/CraftingManager.addLifesteal`) are not wired, and no meal, ability or gear affix adds any.
- **When it heals:** on every hit a player lands on a creature with a melee weapon **or an arrow**, never a spell (`VirtualHealthListener.afterPveHit` → `PveOffense.dealt`): the heal is the damage the creature's health pool really took times the lifesteal share, given to the Cerberus health pool on the player's own thread, never past their max health, never to a player who died meanwhile. **With any weapon**: the stat comes from Weapon Mastery's level, not from the weapon in hand (fists and bows included).
- **PvP:** unchanged from before Attributes 2.0 (`CombatSkillProcessor.weaponMasteryLifesteal`, the same curve, scaled to the target's health as every PvP number is).
- **The curve** (`ScalingFormula`: 0.03 × ln(1 + level) + 0.30 / (1 + e^(−0.08 × (level − 50))), at most 0.40):

| Weapon Mastery | 1 | 5 | 10 | 25 | 40 | 50 | 60 | 75 | 90 | 100 |
|---|---|---|---|---|---|---|---|---|---|---|
| Lifesteal | 2.7% | 6.2% | 8.4% | 13.4% | 20.4% | 26.8% | 33.0% | 39.4% | 40% | 40% |

- **Other healing now:** natural regeneration of the Cerberus pool, First Aid, Cooking's meals (the Hearty Stew restores 25% of max health over 20 s, paused in PvP), and from the next increment Alchemy's draughts.

## 2. Measured in real fights (`tools/playtest/lifesteal.js`, the sandbox)

A fighter with Blade Mastery, Weapon Mastery, Critical Strike, Survival and Heavy Armor Mastery at the level, every other skill at 1, full iron, a netherite sword, in survival, swinging at the sword's pace for 40 s; then the same fight with lifesteal off (`limits.lifesteal: 0` written into the **sandbox's** `attributes.yml` for that run and put back). Numbers from the combat traces and the HUD.

**One zombie of the same level at a time:**

| Level | Lifesteal | Hits | Damage dealt | Healed by lifesteal | Damage taken | Lowest health | Deaths |
|---|---|---|---|---|---|---|---|
| 1 | 2.7% | 31 | 313 | 8 | 5 | 98.1% | 0 |
| 1, lifesteal off | | 32 | 302 | 0 | 7 | 93.2% | 0 |
| 25 | 13.4% | 29 | 665 | 80 | 5 | 99.3% | 0 |
| 25, off | | 28 | 701 | 0 | 5 | 96.4% | 0 |
| 50 | 26.8% | 26 | 1,229 | 295 | 6 | 99.4% | 0 |
| 50, off | | 29 | 1,180 | 0 | 6 | 97.7% | 0 |
| 100 | 40% | 17 | 2,618 | 833 | 11 | 98.4% | 0 |
| 100, off | | 22 | 2,908 | 0 | 4 | 99.2% | 0 |

One zombie of your own level barely scratches a fighter in full iron (4–11 health in 40 s), so lifesteal changes nothing that matters there. What it shows is the size of the heal: **a quarter to a third of the damage dealt** from Weapon Mastery 50, against a few points taken.

**Under pressure: four zombies at once, each ten levels above the fighter** (`lifesteal.js … crowd`):

| Level | Lifesteal | Hits | Damage dealt | Healed by lifesteal | Healed, in max healths | Damage taken | Lowest health | Last health | Deaths |
|---|---|---|---|---|---|---|---|---|---|
| 25 | 13.4% | 76 | 1,408 | 173 | 1.2× (max 139) | 66 | 97.8% | 136/139 | 0 |
| 25, off | | 76 | 1,448 | 0 | | 57 | **59%** | 82/139 | 0 |
| 50 | 26.8% | 69 | 2,495 | 610 | 3.5× (max 176) | 51 | 97.7% | 175/176 | 0 |
| 50, off | | 64 | 2,500 | 0 | | 72 | **59.1%** | 104/176 | 0 |
| 100 | 40% | 60 | 7,001 | 2,225 | 8.9× (max 251) | 44 | 99.2% | 251/251 | 0 |
| 100, off | | 63 | 7,066 | 0 | | 46 | **82.1%** | 206/251 | 0 |

(The heal is capped at max health, so the "healed" column is what lifesteal offered; most of it overflowed.)

## 3. What it means

- **Lifesteal scales with damage dealt, health doesn't keep up.** From Weapon Mastery 25 to 100 the damage a fighter dealt in 40 s grew about five times (1,408 → 7,001) while max health grew 1.8 times (139 → 251), and the lifesteal share tripled (13% → 40%). So what lifesteal offers per fight grows from about one max health (at 25) to nine (at 100). Under Cerberus's hyperscaling, where damage numbers climb into the billions faster than health, that gap keeps widening.
- **Under pressure it removes the pressure.** The same crowd that drives a fighter without lifesteal down to 59% (at 25 and 50) or 82% (at 100) never takes one with lifesteal below 97.7%. Nobody died either way in these fights: the creatures were too weak to kill, so lifesteal decided whether the fight felt dangerous, not whether it was won.
- **It outclasses the new healing for melee fighters.** A Hearty Stew restores 25% of max health over 20 s, a Draught of Mending 25% over 5 s (one at a time each). At Weapon Mastery 50, lifesteal offered 350% of max health in 40 s of fighting. For a melee or bow fighter, meals and draughts matter only outside fights or early on; for a mage (spells never lifesteal) they are the only healing.
- **The early game is untouched by it either way.** At Weapon Mastery 1 lifesteal is 2.7%: 8 health in a 40 s fight. Decision 6 worried that early survival must not become frustrating; lifesteal neither causes nor fixes that. Early healing now comes from meals.
- **It comes from Weapon Mastery but works with every weapon** (swords, fists, bows), though Weapon Mastery's Power and attack speed favour axes, maces, tridents and spears (other weapons get a quarter of its Power).
- **Not measured here:** bosses and creatures that hit hard enough to kill (where lifesteal decides survival, not comfort); PvP (unchanged, off for now); realm suppression at higher realms.

## 4. Options (none applied; each needs the owner's approval)

- **A. Keep it as it is** (decision 6 stands). Melee fighters stay almost unkillable against crowds from Weapon Mastery 25 on, and meals and draughts stay minor for them.
- **B. Cap one hit's heal at a share of max health** (a new number, for example at most 2% of max health a hit, in `attributes.yml limits`). Early heals are below the cap and unchanged (at 25 the average hit healed 1.6% of max health); late heals stop growing with damage. In the crowd at 50, a 2% cap would still offer about 1.4 max healths in 40 s against 51 health taken: strong sustain, no longer ninefold.
- **C. Lower the curve** (`combat.yml skills.weapon-mastery.lifesteal`, for example a hard cap of 0.15 instead of 0.40). Simple, but the heal still grows with damage dealt, so the gap returns with higher realms.
- **D. Only with Weapon Mastery's own weapons** (axes, maces, tridents, spears), as its full Power and attack speed are. Swords would get their sustain elsewhere (Blade Mastery has none today).
- **E. Not together with other healing:** lifesteal halved while a meal's or draught's restoration runs. Keeps both meaningful, adds a rule players must learn.

Each would be a config number or a small rule in `PveOffense.dealt`, PvE only (PvP plays as before), with a unit test and a before/after run of this harness.

## 5. Recommendation

**Recommendation: B, with D as a second step, after your playtest; no change until you say so.**
- **B** fixes the one thing the measurement shows growing without bound (the heal follows damage dealt, which outgrows health) while leaving the early and mid game where they are. Its number should be set with harder content than these zombies: creatures that can kill, and a boss.
- **D** would make lifesteal what Weapon Mastery's page says it is, and give Blade Mastery or Dual Wielding room for their own sustain later.
- Not recommended now: **C** (it postpones the same gap to higher realms) and **E** (a hidden rule for a problem B already solves).
- **To judge in your playtest:** a melee fighter at Weapon Mastery 50+ in a crowd or against a boss, once as it is and once with `limits.lifesteal` lowered in a copy of `attributes.yml` on a test server (never your live world): does it feel too safe?

Nothing in the code changed for this review: the harness's control switches lifesteal off only in the sandbox's own `attributes.yml`, for one run, and puts the file back.
