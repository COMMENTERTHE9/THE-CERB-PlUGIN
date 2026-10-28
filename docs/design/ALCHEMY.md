# Alchemy: brews that act on the character (design, 2026-10-28)

*Milestone "Combat Improvements, Replant Enchantment, Herbalism & Alchemy", increment 5 (the owner's spec, 2026-10-28). Built on what exists: the vanilla brewing stand (with Paper's custom brewing mixes), the attribute engine, the Cerberus health pool, Active Effects, the menu framework and the shipped-config rules. The old `Skills/AlchemySkill` stays unwired (its `applyEffect` and crafting bonuses are the unsafe code the audit found).*

## 1. The loop

- **An alchemist** brews at a brewing stand, as in vanilla: water bottles, nether wart into Awkward Potions, then a reagent. Alchemy XP is paid **only for brews that complete**, to the player who started them.
- **Cerberus brews** (from Alchemy 10): an Awkward Potion and a herb from Herbalism (a poppy, a cornflower, a dandelion...) or a rare reagent brew a **draught** (healing on the Cerberus health pool) or an **elixir** (a timed effect through the attribute engine). They have Western names: Draught of Mending, Elixir of Vigor...
- **Better brews with mastery:** the alchemist's level sets the tier (Common, Fine, Superior, Masterwork), which multiplies the brew's numbers and its time; a quality reagent (Herbalism 50) or a rare one lifts it one tier.
- **The character:** Essence 0.5 per level (the owner set it), through the engine.
- **Shown everywhere effects are shown:** a running elixir or draught is on the Player Menu's head and on Active Effects, with what it adds and its time left, beside a meal.

## 2. What pays (exactly)

- **A completed brew** (the game's brew event, not cancelled) pays the player who **started** it: the one whose click put something into the stand (a bottle, the reagent, its fuel) that made it brewable, in that same moment. A click that puts nothing in (an empty slot, taking something out, a click outside the window) starts nothing. Per bottle brewed: a vanilla brew 2 XP, a Cerberus brew its own XP (data).
- **Pays nobody:** a stand that hoppers feed (no player started it: automation keeps vanilla's output and pays nobody, the Farm Ledger rule), however often someone clicks in its open window; a brew whose starter has left (the brew still finishes).
- **A restart mid-brew** changes nothing: the stand keeps who started its brew (in its own block data, saved with the chunk), so the brew completes as theirs after the restart and pays them.
- **Creative:** brews, pays nothing (the XP display says why).
- **Cerberus brews and their level:** a brew needs its Alchemy level. A stand started by a player below it, or by no player, brews a **Mundane Potion** instead (vanilla's potion with no effect) and the HUD says which level the brew needs. The level gate means something: no machine makes Cerberus brews (as no crafter makes a meal).

## 3. The brews (data: `resources.yml alchemy.brews`)

| Brew | Level | Reagent | Kind | Effect (Common) | Time (Common) | XP |
|---|---|---|---|---|---|---|
| Draught of Mending | 10 | Poppy | draught | 25% of max health back over 5 s | — | 6 |
| Tonic of Swiftness | 15 | Cornflower | elixir | Movement speed +8% | 8 min | 6 |
| Elixir of Vigor | 20 | Dandelion (Sunpetal: a tier up) | elixir | Vitality +8 | 8 min | 8 |
| Elixir of Stoneskin | 25 | Allium | elixir | Fortitude +8 | 8 min | 8 |
| Philter of Insight | 30 | Lily of the Valley | elixir | Essence +10 | 8 min | 10 |
| Elixir of Might | 40 | Crimson Fungus (Emberbloom: a tier up) | elixir | Power +4 (every weapon) | 6 min | 12 |
| Bloodroot Draught | 50 | Bloodroot (rare) | draught | 40% of max health back over 4 s | — | 16 |
| Moonlit Elixir | 60 | Moonpetal (rare) | elixir | Vitality +6, Fortitude +6, Essence +6 | 10 min | 20 |

- **Tiers** (the alchemist's level when the brew starts): Common from 1, Fine 25, Superior 50, Masterwork 75. Each multiplies the effect ×1.2 / ×1.4 / ×1.6 and the time ×1.25 / ×1.5 / ×2 (Cooking's steps). A reagent of Fine quality or better (a herb, or a rare reagent: Herbalism rolls theirs the same way), or the rare reagent named in the table, lifts the brew one tier (at most one, at most Masterwork). The tier is fixed when brewed and shown on the bottle.
- **A lift that can't be used** (Herbalism off, the rare reagent turned off) is left out with a line in the log: the brew stays, made from its own reagent. A brew made from a rare reagent goes with it.
- **The bottle:** a drinkable potion with the brew's name, colour, tier, effect and time in its lore, no vanilla effect, and its id on the item (`cerberus:brew`). It stacks like potions do (not at all).
- Every brew, level, reagent, number and colour is data; a brew can be turned off and new ones added.

## 4. Drinking: stacking and replacement with meals

- **The drink counts only when it happens:** the effect starts after every plugin had its say (a drink another plugin cancels keeps its bottle and gives nothing); the "one draught at a time" refusal comes before.
- **Elixirs** are timed effects through the attribute engine (an attribute source, as meals are), with every cap and diminishing return:
  - **one elixir at a time:** a new elixir replaces the running one (and says so); the same elixir refreshes its time;
  - **an elixir and a meal run together** (different kinds of food for the body): both count, and the engine's caps hold over both;
  - saved on the player with its real end time (`cerberus:elixir`): a relog or restart keeps it, its time running on; death ends it.
- **Draughts** heal the Cerberus health pool, a share each second for their seconds, never past max:
  - **one draught at a time:** while one is still healing, another can't be drunk (the HUD says how long; the bottle is kept);
  - a draught heals beside a meal's restoration (both are short: 4–5 s and the meal's first 20 s);
  - paused while PvP-tagged, as a meal's restoration is (the rest comes after the fight); death ends it; not saved across a relog (it lasts seconds).
- **Vanilla potions** keep working as they do (Active Effects describes them as they act under Cerberus); a Cerberus brew never adds a vanilla effect.
- **PvP:** as for every attribute: elixirs count against creatures only for now (PvP plays as before Attributes 2.0); listed in `docs/ROADMAP.md` → PvP.

## 5. Shown

- **Active Effects** (`Effects/EffectProvider`, no change to the menus): the running elixir (category Elixir, after the meal) with its source "Elixir · Fine · Alchemy", what it adds and every number it moves, and its time left; a running draught with its restoration seconds.
- **The Alchemy skill page:** the tier you brew at as multipliers, and **Brews: N of 8** (each brew: its level, reagent, kind, effect and time at your tier, brewed or not). Collections → Brews.
- **`/alchemy`:** your tier, the running elixir and draught, brews made; `/alchemy brews` opens the list.
- **The one-minute heads-up** before an elixir ends, the meal's (queued behind other notices, once per elixir).

## 6. How it is built (Folia, performance)

- **Paper's brewing mixes** (`PotionMix`): one per brew, an Awkward Potion and its reagent (a material, or a Cerberus rare reagent matched by its id). The stand brews it as vanilla brews (its fuel, its 20 s). The result is set at the brew event on the stand's region thread: the tier from the starter's level and the reagent, or a Mundane Potion. Each mix asks the catalog of the moment whether its brew still exists and what it takes, so a reload changes the server's list of mixes (which stands read on their own threads) only when it adds or removes a brew.
- **Who started it:** a click that puts something into a brewing stand is remembered for that stand for a moment (1.5 s; a map by stand, bounded, emptied as brews start); the stand's start event binds the brew to that player with their level, and writes the binding on the stand itself (`cerberus:brew_started_by`), read back if a restart came between. A start with no such click (a hopper) binds nobody.
- **Cerberus items in brewing:** the guard that keeps Cerberus items out of vanilla brewing lets Alchemy's rare reagents into Alchemy's own mixes, nothing else.
- **Nothing runs per tick:** one task per running elixir's end and one per draught's seconds; brewing is the stand's own timer.

## 7. Testing

- Unit: brew rules (levels, tiers, the lift, potency and time, the bottle codec), the elixir codec, the stacking rules.
- In game (`tools/playtest/alchemy.js`): vanilla brews pay their starter per bottle, a hopper-fed stand pays nobody (also with its window open and an empty slot clicked again and again), creative nothing; a brew a server restart interrupts completes as its starter's, paid; a Fine Bloodroot lifts its draught a tier; a Cerberus brew below its level makes a Mundane Potion (and says why), at its level the brew at its tier, lifted by a Fine poppy and by Sunpetal; drinking: Vitality, Fortitude, Essence, speed as the Stats page shows them; the elixir replacing and refreshing; a meal and an elixir together; a draught healing the pool, a second refused while it heals; Active Effects showing both; relog, restart and death.
