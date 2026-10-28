# Combat improvements: Martial Expertise's Agility and Iron Reprisal (2026-10-28)

*Milestone "Combat Improvements, Replant Enchantment, Herbalism & Alchemy", increment 1 (the owner's spec, 2026-10-28). Both go through the systems that already exist: the attribute engine for Martial Expertise, the damage pipeline and the server's own knockback for Iron Reprisal. No reflected damage, no chance rolls, no PvP change.*

## 1. Martial Expertise: +1.25 Agility per level

**What changes.** Each Martial Expertise level now adds **1.25 Agility**, on top of **Power (unarmed) 1.0**, which stays as it was.
- `attributes.yml`: `Martial Expertise: [ power 1.0 (unarmed), agility 1.25 ]`; the code default (`AttributeRules`) says the same.
- Agility is a general attribute: it raises crit chance and crit damage, attack, draw and movement speed **with every weapon**, not only bare hands. That is how Agility works for Critical Strike, Dual Wielding, Navigation and Stealth too.

**Through the shared engine, nothing new:**
- the caps and curves: crit chance at most 75%, crit damage at most x3.5, the offense budget (crit and damage bonuses multiply the Power baseline by at most 6), each speed stat's diminishing returns to its cap, the Agility safety cap of 300;
- the PvP policy, unchanged: attributes count against creatures only (`PveOffense` handles player → creature hits only; PvP hits play as before Attributes 2.0), and speed and handling bonuses are paused in a PvP fight (`secondary.in-pvp: false`).

**What it means, on a new character** (every other skill at level 1, which already gives Agility 2.0; `MartialExpertiseTest` checks these numbers). "Unarmed damage" is the expected multiplier with crits counted:

| Level | Agility | Crit chance | Crit damage | Attack speed | Draw speed | Movement speed | Unarmed damage |
|---|---|---|---|---|---|---|---|
| 1 | 2.00 → 3.25 | 13.8% → 14.7% | x1.55 → x1.56 | +0.2% → +0.3% | +0.6% → +0.7% | +0.2% → +0.3% | x1.14 → x1.15 |
| 10 | 2.00 → 14.50 | 13.8% → 19.4% | x1.55 → x1.62 | +0.2% → +1.4% | +0.6% → +1.8% | +0.2% → +0.8% | x1.63 → x1.69 |
| 25 | 2.00 → 33.25 | 13.8% → 25.7% | x1.55 → x1.74 | +0.2% → +3.1% | +0.6% → +3.6% | +0.2% → +1.7% | x2.43 → x2.69 |
| 50 | 2.00 → 64.50 | 13.8% → 51.5% | x1.55 → x2.57 | +0.2% → +5.7% | +0.6% → +6.4% | +0.2% → +3.1% | x3.78 → x6.34 |
| 75 | 2.00 → 95.75 | 13.8% → 70.8% | x1.55 → x3.14 | +0.2% → +8.0% | +0.6% → +9.0% | +0.2% → +4.3% | x5.12 → x11.98 |
| 100 | 2.00 → 127.00 | 13.8% → 74.3% | x1.55 → x3.19 | +0.2% → +10.0% | +0.6% → +11.5% | +0.2% → +5.5% | x6.47 → x15.81 |

**For the owner to judge in the playtest.** This is a large step for unarmed fighters: from about level 40 most of a fist fighter's damage comes from crits, and at 100 they reach what Blade Mastery 100 *and* Critical Strike 100 give a swordsman. Because Agility is general, a Martial Expertise 100 player also crits 74% of the time with a sword. The caps hold everywhere. If it plays too strong, the lever is the one number in `attributes.yml`; nothing else needs to change.

**Servers with an older `attributes.yml`:**
- **Never edited:** refreshed at startup as before (`ShippedConfig`; the old copy kept as `.old`).
- **Edited, but Martial Expertise still the old line word for word** (`power 1.0, weapon: unarmed` only): the new entry is used in memory and the log says so, with how to keep the old one (`{ attribute: agility, per-level: 0 }`). The file on disk is not rewritten. (`AttributesConfig.CHANGED_BY_UPDATES`.)
- **Edited with the owner's own Martial Expertise line:** it is kept; the log says what this version ships.
- **`Martial Expertise: []`:** still off.

**Where players see it:**
- the Stats page and the skill page ("Next levels"), both read from the engine;
- level-up messages: the real gain, Agility first when it moves most ("▲ Martial Expertise 12 · +2.5 Agility");
- the admin inspector and simulator.

## 2. Iron Reprisal (Heavy Armor Mastery)

**What it does.** When a creature's melee hit lands on you while you wear heavy armor (iron, diamond or netherite helmet, chestplate, leggings or boots) and your Heavy Armor Mastery is at least **10**, the creature is **thrown back**. There is no chance roll and no damage. Internally the skill is still "Heavy Armor Training".

**How hard.** The push is the server's own knockback strength (a plain unenchanted hit pushes with 0.4):
- **0.4 at level 10**, growing evenly to **1.0 at level 100** (a Knockback I sword hit is about 0.9), then staying;
- **a quarter per heavy piece**: one piece gives a quarter of the push, a full set all of it;
- the attacker's **knockback resistance** counts, as for any knockback: iron golems and wardens don't move, ravagers and hoglins move less.

Measured in game (`tools/playtest/reprisal.js`, a zombie on flat ground; how far it lands from where it stood): see `docs/STATUS.md` → "Done: Combat improvements".

**When it triggers, and when never:**
- **Only a melee hit that landed:** an `ENTITY_ATTACK` or `ENTITY_SWEEP_ATTACK` from a living attacker, read at MONITOR after every plugin had its say, not cancelled, and resolved by the damage pipeline into real damage (`VirtualHealthListener.landed`). A hit during the invulnerability window, a hit Riposte countered, and a lethal hit throw nobody.
- **Never from projectiles:** arrows, tridents, fireballs, shulker bullets, llama spit, wind charges. The shooter isn't in reach, and sending the projectile back would be reflection. Never from explosions, thorns, magic, fire, falls or anything environmental, and never from yourself or your own tamed animals.
- **Not when your shield takes the blow:** the blow never landed, so there is nothing to answer (the game itself pushes nobody on a block). A blow from behind, past a raised shield, lands and is answered as any other. This holds for a player's blow too once `pvp: true`: the damage pipeline still resolves a player's blow as before PvP's pass (and so zeroes the hit), but it remembers that the shield took it, and Iron Reprisal reads that (until the review of 2026-10-28, a player's blocked blow was answered).
- **PvP: off for now** (`pvp: false`): players, and animals a player owns, are not thrown. Like every PvP limit it is temporary and switchable, listed in `docs/ROADMAP.md` → PvP.
- **Cooldown:** an attacker is thrown at most every 0.5 s, by anyone; it can't be juggled. Exempt: the ender dragon and the wither.
- A creature riding another (a spider jockey) is thrown with its mount.

**How it runs (Folia):**
- The check runs on the player's region thread at MONITOR: a few comparisons and the armor count.
- The push runs on the **attacker's own region thread, one tick later** (`attacker.getScheduler().runDelayed(…, 1)`), after the server's own knockback and movement for that hit. It re-checks there that the attacker is still valid, in the same world and within 8 blocks. It uses the server's knockback (`LivingEntity.knockback`), so knockback resistance and other plugins' knockback handling apply.
- Nothing it does deals damage, so it can't start another hit: no recursion. One short delayed task per throw; the cooldown map is swept of ended entries when it reaches 512, with no timer.
- A thrown **player** (only with `pvp: true`) is moved by their own game client, which never hears of the server's knockback alone: the push is sent to them as their velocity (found in the sandbox: with only the server's knockback, the server sent the attacking player no push at all; with it, the full 1.0 blocks per tick at Heavy Armor Mastery 100 in full iron).

**Settings** (`combat.yml` → `skills.heavy-armor-training.iron-reprisal`). Every key has a code default, so an older `combat.yml` without the section plays exactly like the shipped one (`IronReprisalRulesTest`):

| Key | Default | What |
|---|---|---|
| `enabled` | true | the switch |
| `unlock-level` | 10 | the Heavy Armor Mastery level it opens at |
| `strength-at-unlock` | 0.4 | the push at that level, in full heavy armor |
| `max-strength-level` | 100 | the level it reaches its maximum |
| `max-strength` | 1.0 | the maximum push |
| `cooldown-ms` | 500 | how often one attacker can be thrown |
| `pvp` | false | throw players and their animals too |
| `sound` | true | a soft clang where the attacker is thrown |
| `exempt` | ender_dragon, wither | never thrown |

**Where players see it:**
- the Heavy Armor Mastery skill page: "Iron Reprisal: melee attackers thrown back, in full heavy armor", as a multiple of a plain hit's push (x1.00 at 10, x2.50 at 100);
- the level-up to 10 announces the technique ("✦ New technique: Iron Reprisal").

**Admin:** `/cerb reprisal [player|reset]`: the settings, how many attackers were thrown and why others weren't (hit not landed, PvP off, cooldown, exempt, the shield took it, gone by its turn), and a player's level, heavy pieces and push.

### Shields and the Cerberus health pool (found while checking Iron Reprisal, 2026-10-28)

**What was wrong (from before this milestone):** behind a raised shield, a creature's blow still came off the Cerberus health pool in full. The game showed the block (no hurt, the block sound), but the damage pipeline read the blow's size before the shield's share and took it from the pool; resolving the hit then erased the shield's share, so the shield didn't wear either. In the sandbox, a husk striking a player with a raised shield took the pool from 102.75 to 97.10 over a few blows, more than one blow takes without the shield (101.66). Shields were useless against creatures.

**Now:** when the server reports that a raised shield took the whole blow (its own rules: the shield up long enough, the blow from in front), a creature's blow, arrow, fangs or blast leaves the pool alone, and the game's own blocking runs after the event: the block sound and the shield's wear (`VirtualHealthListener.shieldTookAll`, `ShieldBlockTest`). Checked in the sandbox: five husk blows on a raised shield left the pool full and wore the shield by 20, as in the vanilla game; the game pushes nobody on a block (neither the husk nor the player moved). A blocked blow pays no Survival or Heavy Armor Mastery XP: nothing reached you.

**Not changed:**
- a blow from behind, past the shield, lands as before;
- an item that blocks only part of a blow (none in the vanilla game) still lets the whole blow through;
- **players' blows**: PvP plays as before, so a player's blow still reaches the pool behind a shield. Listed in `docs/ROADMAP.md` → PvP for the PvP pass.
