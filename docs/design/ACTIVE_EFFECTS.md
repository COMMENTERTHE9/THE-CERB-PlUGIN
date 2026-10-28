# Active effects: what is on you, what it does, how long it lasts

*The owner's request of 2026-10-28, after the Farming and Cooking playtest: "it would help to see all the effects that you have on you… I can't see how much longer I have it." Approved as the next feature the same day. An interface and visibility change only: no meal, ability, buff, attribute, Cooking or PvP number changes.*

## 1. What the player sees

- **The Player Menu's head** (the hub's top centre) lists the effects running on you after your attributes, meal first, each as `Hearty Stew — 8m 42s remaining`. At most four lines, then "+N more". With nothing running: "No active effects". Clicking the head opens **Active Effects**.
- **Stats & Attributes** stays where it was: its own tile in the hub (slot 21), and the Health and Essence tiles. The Active Effects screen has a shortcut to it too.
- **The Active Effects screen** has one tile per effect, in the same order. Each tile shows:
  - the effect's **name** and **time left**;
  - its **source**: a meal and its tier, a class ability, an admin test buff, a potion or a beacon;
  - **what it does:** what it adds ("• Vitality +6", "• Movement speed +8%"), then under **Now:** every number that moves because of it, worked out by the attribute engine with and without the effect, so caps and diminishing returns are included: "max health 103 → 109", "movement speed +2.0% → +8.6%". The numbers compared: max health, damage with your best weapon kind, crit chance and damage, lifesteal, armor (through the game's own rule: gear, Heavy Armor and both caps included), the defense bar, fire/cold/drowning resistance, max Essence and its regeneration, spell damage, each speed and handling stat (attack speed for the weapon in hand), and Fortune's extra-drop chances. A number that doesn't move isn't listed; one the server switches off or PvP pauses says so.
  - Where a source has a shorter inner part, that part is shown on its own line with its own time. A meal's **restoration** heals for its first seconds only ("Restoration: 25% of max health over 20 s · 12 s left"), never for the meal's whole time.
- **Always on is kept apart:**
  - A separate tile lists the equipped class passives and keystone, labelled *Always on: not timed*.
  - Permanent skill attributes are not effects; they stay on Stats & Attributes.
- **PvP:** while you are PvP-tagged, the header and each tile with combat numbers say that combat bonuses count against creatures only for now (PvP plays as before Attributes 2.0: player hits don't use them, and a PvP hit is worked out on the pre-attribute baseline), that a meal's healing waits for the fight to end, and, where the server pauses them, that speed and handling bonuses are paused. Nothing here changes those rules.
- **No effects:** a clear message instead of an empty screen. It says what would show here and where the permanent numbers are.

## 2. Where the information comes from

Every effect is read from the system that runs it, at the moment it is shown. Nothing is copied or stored a second time.

| Source | Runtime state read | Ends |
|---|---|---|
| A meal (`Gathering/Cooking`) | the running meal: id, tier, end time (saved on the player) and its restoration (heals done of heals due, paused or not) | its time; a new meal replaces it; death; it continues across a relog or restart |
| A class ability (`Classes/ClassService`) | running buffs (War Cry: +15% damage, 8 s), an open Riposte guard, a Feint (next hit crits) | its time; leaving the loadout in use; death; leaving the server |
| A temporary attribute buff (`Attributes/BuffSource`) | the admin's dev buffs (+25 or +100 of an attribute, 5 min) | its time; the admin clears it; leaving the server |
| An admin test realm (`Attributes/PlayerRealms`) | the realm an admin set for testing | when the admin clears it or you leave |
| A vanilla effect (the server's own) | the player's potion effects, with level and remaining ticks | vanilla's rules |

Vanilla effects are described **as they work under Cerberus**, which runs its own health and damage:
- **Regeneration, Instant Health, Absorption, Health Boost and Resistance** do nothing to Cerberus health or damage. Vanilla healing is cancelled for players, the Cerberus pool has its own maximum, and hits are taken before vanilla's reductions. Their tiles say so.
- **Strength and Weakness** change a melee hit's base damage (+3 and −4 per level); on a creature, Cerberus's Power and realm multiply it.
- **Infested** may release silverfish when you are hit; **Wind Charged, Weaving and Oozing** act when you die.
- **Poison and Wither** hurt the Cerberus pool.
- **Fire Resistance, Speed, Slowness, Haste and the rest** work as in vanilla.

Planned systems (Alchemy, Herbalism, later magic) are not shown until they exist.

**The interface for later systems.** `Effects/ActiveEffect` is the read-only snapshot of one effect:
- name, source, icon and end time;
- the attribute-engine contributions it adds;
- fixed notes;
- an optional inner phase.

A system offers its effects through `Effects/EffectProvider`, registered once on `Effects/ActiveEffects`. A later Alchemy potion registers a provider; the menus, the head summary and the countdown need no change.

## 3. Countdowns without a scanner

- **The times on screen count down live while you look at them.** A screen showing a timer asks the menu framework (`GUIs/menu/MenuTicker`) for one update a second. The update runs:
  - for that viewer only, on their own region thread;
  - only while that exact screen is open;
  - only while something on it is timed (a countdown or a running restoration);
  - and stops by itself when the screen changes, refreshes or closes, the player leaves, or nothing timed is left.
- **Each update rewrites only the tiles that show a time.** The engine's with-and-without numbers are worked out once, when the screen is drawn. When an effect ends or a new one starts, or a PvP fight starts or ends, the screen is drawn again, so an ended effect never shows as active.
- **Limits:** the first 28 effects get a tile (the content area); the last tile counts the rest ("+N more effects not shown"), and a change among the hidden ones redraws the screen too. A countdown's tiles are built fresh, never put in the menus' shared item cache (which they would fill with one entry a second).
- **An open menu with no timed effect runs nothing**, as before. A screen of untimed effects only (an infinite potion) is a snapshot: what was on you when it was drawn.
- **There is no server-wide effect scanner and no per-tick work for players who aren't looking.**

## 4. The one-minute heads-up for a meal

- **About a minute before a meal ends**, the HUD says so once: "Hearty Stew wears off in about a minute", with a soft chime.
- **It is one delayed task per meal**, set when the meal is eaten or read back at a join. When it runs it checks that the meal is still the one it was set for, so a replaced, refreshed or ended meal never warns.
- **It waits its turn on the HUD:**
  - it shows only when no ability or combat message and no level-up notice is on the bar, trying again every 2 s, eight times;
  - after that it is queued behind the notice showing (`PlayerHUDManager.queueNotice`): it never replaces a level-up;
  - an XP line waits behind it, as behind a level-up.
- **When it doesn't show:**
  - a meal eaten with about a minute or less to run gets no heads-up;
  - after a join with under a minute left, it comes a moment after the join if at least 15 s remain and it wasn't said yet;
  - it is said once per meal: the meal it was said for is recorded on the player (`cerberus:meal_warned`, its end time), so a relog or restart doesn't say it again; a new meal or the meal's end clears the record.

## 5. Performance

- **Before the feature** (the build before), measured on the sandbox:
  - the hub and skill pages (`menu`);
  - an open menu (`menuidle`);
  - idle players;
  - an open hub with a meal and a potion running (`mealidle`, new);
  - the head click and back (`effectsmenu`, new; Stats before, Active Effects after);
  - the head's screen left open with a meal running (`effectsidle`, new).
- **After the feature**, the same loads, plus the cost of a live countdown per open screen.
- Results: `docs/PERFORMANCE.md` → "Active effects".
