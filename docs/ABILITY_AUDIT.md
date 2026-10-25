# Ability Audit: Warrior, Mage, Duelist

*Phase 2 of `docs/ROADMAP.md` (owner direction, 2026-10-25). Each of the 21 abilities was tested on its own, through real key presses from a bot that sends what a vanilla 1.21.11 client sends. Effects were measured in the world: damage traces, positions, mob effects, Essence, cooldowns and attributes. Harness: `tools/playtest/abilities.js` (phases `controls`, `combos`, `warrior`, `mage`, `duelist`, `mixed`, `persist`). Controls: `docs/design/ABILITY_CONTROLS.md`. **Feel is not verified here**: whether a key or an ability feels right needs the owner's playtest (`docs/PLAYTEST_CHECKLIST.md`).*

## How it was tested

**The bot.** It sends the exact packets of a vanilla client:
- right-click with the look direction in degrees;
- the mob interaction and the air click in the same tick;
- attack + swing for a left-click on a mob, a swing for the air;
- F and Q as their own actions;
- Sneak as a key state.

**Its limits are handled, not hidden:**
- **Motion packet:** the 1.21.9+ motion packet is applied as the client applies it. Unpatched, the bot never moves from a dash.
- **Drops:** the client predicts a Q drop and the bot doesn't, so drops are counted on the ground.
- **Mob effects:** the client never learns them, so they are read from the server with `/cerb mobs`.

**Positive controls.** Every "nothing happened" check has one that shows the same input doing something when it should: F swaps and Q drops a pickaxe, a hit without Riposte lands, a Cleave on a zombie pays XP. A check can't pass just because the bot's input never reached the server.

**The arena.** A clean stone floor at y 200: natural mob spawning off, every mob removed between checks, the hotbar cleared before each item. Cooldowns and Essence are reset by admin test commands (`/cerb class <p> cooldowns`, `/cerb mana <p> full`). The bot has Blade Mastery 10, Critical Strike 5 and Intelligence 5 (the owner's checklist levels), and every class at rank 12.

**What the numbers mean:**
- **A plain sword hit:** 9.2 damage after Power.
- **Ability damage:** listed as the multiplier the trace shows over that hit.
- **Essence costs:** read from the HUD at the moment of the cast. Regeneration (about 70 Essence a second at these levels) would hide them between two separate readings.

## Results

Key = the default control scheme (quick keys) in the loadout each phase uses. With a bow, right and left swap.

**In numbers**, on the final build: `controls` 26, `combos` 13, `warrior` 20, `mage` 23, `duelist` 14, `mixed` 17, `fixes` 20 (the review's findings), `persist` 4. That makes 137 checks, all passing.

### Warrior

| Ability | Intended (abilities.yml) | Verified in game | Result |
|---|---|---|---|
| **Cleave** (active, rank 1) | Cone 3.5 blocks, 100°, 1.6x Power, up to 6; +1 Momentum per enemy; 4 s; blade, axe, polearm, blunt | Hits the 3 zombies in front (14.8 each = 1.6x 9.2), not the one behind nor the one 5 blocks away; message "3 hit"; on cooldown: "Cleave 3.0 s", nothing hit; works running and jumping; a whiff spends its cooldown ("no one hit"), as a swing does; a player in front is never hit | ✅ |
| **Charge** (active, rank 3) | Dash 8 blocks; the first enemy in its path stunned 1 s; then a 1.0x strike; 1.2 s, the stun and strike every 10 s (the owner's movement rule, 2026-10-25); movement lockout | Dashed 6.2 blocks; the zombie in the path got Slowness 10 for 1 s (`/cerb mobs`) and was struck. A moment later it rushes again, 6.2 blocks, without stun or strike ("✦ Charge · strike in 6.9 s", no damage trace, no effect on the zombie); half a second after that: "Charge 0.7 s". Blink right after it waits for the shared lockout | ✅ |
| **Execute** (active, rank 6) | Strike 3.5 blocks, 1.0x + 0.4x per Momentum, spends all Momentum, **needs 1** (Phase 3); 8 s | After a 3-zombie Cleave: "x2.2 · 3 Momentum", 20.3 damage (2.2x). With no Momentum: "Execute needs 1 Momentum", no strike, no cooldown (before Phase 3 it was a plain 1.0x hit that spent its 8 s). Nothing in reach: "no target within 3.5 blocks", no cooldown, Momentum kept. Aim assist finds a zombie half a block off the crosshair, not one 2 blocks off. With an empty hand: "needs a blade, an axe, a polearm or a blunt weapon", nothing spent | ✅ |
| **War Cry** (active, rank 10) | +15% damage for 8 s; 30 s; any hand | Next hit x1.152 (crits divided out); recast refused ("War Cry 28.4 s", no stacking); ends after 8 s. **Bug found and fixed:** the buff could outlive its 8 s when a region's catch-up ticks ran its end task a few ms early | ✅ after fix |
| **Battle-Hardened** (passive, rank 2) | +8 Vitality | Max health 102 → 110 | ✅ |
| **Weapon Discipline** (passive, rank 8) | +5% attack speed (inside the speed caps) | Attack speed 1.653 → 1.716 | ✅ |
| **Juggernaut** (keystone, rank 12) | +12 Vitality, +6 Fortitude, 6% slower attacks | Max health 110 → 122, attack speed 1.716 → 1.639; all back on unequip (102, 1.653) | ✅ |

### Mage

| Ability | Intended | Verified in game | Result |
|---|---|---|---|
| **Arcane Bolt** (active, rank 1) | Instant bolt at the enemy you look at, 8 blocks, 1.4x spell hit (Essence scaling), 15 Essence, 1.5 s; focus | Hits a zombie 6 blocks away once (8.4 → 10.0 with Essence), costs exactly 15, draws a beam; 0.7 blocks off: still hits; 2.5 blocks off, 10 blocks away, or behind a wall: "no target within 8 blocks", no Essence, no cooldown; blaze rod, breeze rod and stick all work; sword: "needs a focus"; almost no Essence: "needs 15 Essence", no cooldown. **The owner's report:** the Fireball Wand now casts it (it cast its own spell before), and still casts its own Fireball when no right-click ability fits a focus (Cleave on right-click used to make it do nothing) | ✅ after fixes |
| **Fireball** (active, rank 3) | The Fireball spell, mobs only, flies through players; 20 Essence, 2 s; focus | Q: flies and bursts on a zombie (1 hit), costs exactly 20, the rod stays in hand | ✅ |
| **Frost Nova** (active, rank 5) | Circle 4 blocks, 0.8x spell hit, Slowness II 3 s, up to 8; 30 Essence, 8 s | Hits the 3 zombies within 4 blocks, not the one 6 away; all 3 slowed (`/cerb mobs`); costs exactly 30 | ✅ |
| **Blink** (active, rank 8) | Teleport up to 6 blocks where you look, never into blocks; 20 Essence, 0.4 s (the owner's movement rule; was 6 s); movement lockout | Moved 6.00 blocks, costs 20. Two Blinks half a second apart both go off, 12 blocks. Wall 3 blocks ahead: stops at 2.00 (never inside). Facing a wall, or looking at your own feet: "Blink: no room", nothing spent. **Looking down at open ground** (the owner's playtest: it said "no room" there): lands on the floor where you look, 2.2 blocks at 30° down, the full 5.9 at 10° down | ✅ after fix |
| **Essence Flow** (passive, rank 2) | +15% Essence regeneration | 3 s from empty: 226 vs 201 without | ✅ |
| **Spell Focus** (passive, rank 6) | +8 Essence | Max mana 1,100 → 1,180 | ✅ |
| **Glass Cannon** (keystone, rank 12) | +25% spell damage, 15% less max health | Arcane Bolt's spell multiplier x2.32 → x2.57 (+0.25, read from the trace); max health 102 → 87 | ✅ |

### Duelist

| Ability | Intended | Verified in game | Result |
|---|---|---|---|
| **Riposte** (active, rank 1) | For 0.6 s the next melee hit taken is negated and answered at 2x; 6 s, 1.5 s after a success; blade | A zombie's hit inside the window: no damage step at all, answered ("✦ Riposte! · countered"), back in 1.5 s; after the window: the hit lands normally; positive control: a hit with no Riposte shows its damage step; a player's hit is never countered | ✅ |
| **Lunge** (active, rank 3) | Dash 5 + strike 2.5 blocks at 1.5x; 1.2 s, the strike every 7 s (the owner's movement rule); movement; blade, polearm | Moved 3.9 blocks and struck the zombie; again 1.2 s later it moves without the strike ("strike in N s", no damage trace) | ✅ |
| **Flurry** (active, rank 6) | Three strikes at 0.6x, 4 ticks apart; 8 s; blade | 3 strikes on the zombie; facing away: "no target", no cooldown; an axe: "needs a blade" | ✅ |
| **Feint** (active, rank 9) | The next weapon hit within 3 s is a critical hit; 10 s; blade | "next hit crits"; the next hit shows "guaranteed: Feint"; the one after it doesn't; the sword stays in hand (Q) | ✅ |
| **Precision** (passive, rank 2) | +5% critical hit chance | Crit chance 16.0% → 21.0% | ✅ |
| **Footwork** (passive, rank 5) | +4% movement speed | Movement speed 0.1 → 0.104 | ✅ |
| **Blade Dancer** (keystone, rank 12) | +15% critical damage, 6 less Fortitude | Feint's guaranteed crit x1.58 → x1.73 (+0.15, read from the trace); the Stats page reloads with it | ✅ |

## Across abilities

**Mixed classes** (`mixed`):
- Warrior Cleave, Warrior War Cry and Duelist Feint each cast from one loadout with one key.
- Feint's crit and War Cry's +15% land on the same hit: 8.9 x 1.58 x 1.15 = 16.1.
- Charge then Blink: Blink waits for the shared movement lockout, 1.2 s at most, then goes off.
- Eight right-clicks in a quarter second cast once.
- War Cry from another loadout is still on its cooldown: switching resets nothing.

**Class XP:**
- A self-buff cast out of combat earns none.
- Cleave on a farm animal earns none, and **killing one no longer pays class XP either**. It used to: +20 for a cow. Phase 3 fix.
  - Measured: 4 Cleaves kill a level-1 cow (a 50 HP pool, like any mob) for +0.
  - The positive control: the same on a zombie pays every Cleave that lands, plus a 20 XP kill share.
- A self cast right after hitting a farm animal earns none. **Any hit used to count as a fight**: a cow, an armor stand or a PvP tag. So Blink or War Cry right after one paid the full 6. Now only a fight with an enemy pays, for that enemy's level. Phase 3 fix.
  - The positive control: the same War Cry after hitting a zombie pays 6.
- At Blade Mastery 40, a Cleave on a grey mob (level 15) pays a quarter, as kill XP does (1.5, banked as 1). A level-30 one pays the full 6.
- A Riposte counter is paid by the attacker's level the same way.
- **A mob kept alive pays cast XP only up to its kill XP** (wrap-up, 2026-10-25). Nine War Cries in a fight with one caged level-1 zombie paid 6 6 6 6 6 6 4 0 0, 40 in all, its kill XP. Killing it still paid the 20 XP kill share and nothing more. The first run found the killing cast paying again (the mob's death had cleared its record): fixed.

**Items:** after every press in the phase, exactly one sword; nothing dropped, nothing duplicated.

**PvP safety:**
- Cleave with only a player in front hits nobody.
- Execute aimed at a player finds no target.
- Riposte never counters a player's hit.
- A left-click on a player inside a combo is a plain hit.

**Persistence** (`persist`, around a server restart): the control scheme, the ability-bar setting, the loadout in use and a running War Cry cooldown all survive. The cooldown is saved as an end time: 29.4 s left at the save, 6.4 s left 23.0 s later, after the restart.

## Found and fixed during the audit

1. **HUD erased cast messages** (1–950 ms on screen): the HUD now has a message slot.
2. **F swapped the weapon** (same-tick Sneak, empty F slot): F and Q never swap or drop while armed; Sneak combinations are decided a tick later.
3. **The Fireball Wand took every right-click.** Its listener now runs after the ability keys.
4. **Misses still cost Essence and the cooldown.** Single-target abilities and Blink now check before spending, with aim assist and a visible bolt.
5. **A buff could outlive its time** (War Cry): its end task now removes exactly that buff.
6. **Execute at 0 Momentum** was a plain hit that spent its 8 s cooldown. It is now refused ("Execute needs 1 Momentum") and spends nothing.
7. **The Fireball Wand did nothing with a melee ability on right-click** (found by the regression suite). It now keeps its own spell unless a right-click ability fits a focus.
8. **Class XP could be farmed:**
   - Farm animals paid it on a kill.
   - Weak mobs paid full cast XP.
   - Hitting anything (a cow, an armor stand) made self casts pay.

   Now only enemies count, with the kill-XP grey-mob share (`ClassRanks.castXp`, unit-tested).
9. **A negative bonus read "+-4.6%"**: a keystone's slower attacks on the Skill, Stats and admin pages (found by the regression suite). Signed bonuses now show one sign.
10. **Blink said "no room" looking down at open ground** (the owner's playtest). It walked the feet along the look direction, straight into the floor. Now it follows the sight line and stands you on the ground under it, checking the whole body (0.6 by 1.8 blocks) at the landing spot.
11. **"Arcane Bolt needs a focus" didn't say what a focus is** (the owner's playtest). It now reads "needs a focus (blaze rod, breeze rod or stick)", and the menus say the same.

## Independent review

A reviewer read the code from scratch, without my conclusions, and reported 3 medium and 5 low findings. Each was checked against the code (and the server's own drop handling) before fixing. All are fixed and checked in game by `abilities.js fixes`:

| # | Finding | Fix |
|---|---|---|
| 1 | **Q cast while the weapon was out of the hand** (medium). The server takes the item out before the drop event and puts it back after, so a damage ability on Q struck bare-handed and a kill paid Martial Expertise. | Q casts a tick later, with the weapon back. Flurry on Q: all three strikes use sword Power. |
| 2 | **Class XP for hits that can't land** (medium). A heart-bound Creaking or an invulnerable mob counted as an enemy reached: endless free XP. Riposte paid for any attacker. | Such mobs are not targets. Riposte pays for enemies only. |
| 3 | **Right-click took the off hand's own use** (medium): a shield never blocked, an off-hand torch or food did nothing, an axe couldn't strip a log. | The off hand's use and the axe's block uses stay vanilla (`VanillaUse`, unit-tested). The bar shows the right-click abilities dark. |
| 4 | Sneak let go together with the key cast the plain slot (low). | Sneak held at the press counts. |
| 5 | `ability-bar: off` unquoted in `classes.yml` read as a YAML boolean and was ignored (low). | on/off (true/false) are understood; unit-tested by loading the YAML. |
| 6 | Abilities could hit another player's pet or a mount a player rides (low; PvP is off). | Never targets; Riposte doesn't counter them. |
| 7 | Q within 100 ms of closing an inventory dropped the weapon (low). | The hand is compared with how it was at the close. |
| 8 | Fireball paid class XP as a self cast, even fired into the sky (low). | It pays when it bursts on an enemy. |

## Not verifiable by a bot (needs the owner)

How the keys feel under pressure, whether 1.6 s is the right message length, whether the ability bar helps or distracts, whether the sounds tell the reasons apart, how the effects look, and whether each ability's identity comes across. Those are in `docs/PLAYTEST_CHECKLIST.md` §6.
