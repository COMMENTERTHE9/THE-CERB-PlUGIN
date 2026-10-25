# Ability Controls

*Phase 1 of `docs/ROADMAP.md` (owner direction, 2026-10-25). It covers how a player triggers abilities, what they see when they do, and why. Built and tested on the sandbox. **Usability is not verified until the owner has played it** (`docs/PLAYTEST_CHECKLIST.md`).*

## 1. Why the Step 5 controls had to change

The Step 5 prototype cast with **Sneak + right-click / left-click / F / Q / scroll**. The owner's playtest found three problems. Each was reproduced on the sandbox before anything was changed (survival mode, the checklist's own loadouts; `tools/playtest/abilities.js` keeps the checks):

| What the owner saw | What was really happening |
|---|---|
| "Not sure what Sneak + left-click does; no clear feedback" | The HUD rewrote the action bar every second, and at once after any health or Essence change. A cast message stayed on screen for **1–950 ms, often 1 ms**. Execute with no Momentum dealt exactly a normal hit (9.2 vs 9.2). In the air it went off anyway and spent its 8 s cooldown on nothing. |
| "Sneak + F puts things in my off hand" | Two causes. (1) F's slot was empty in the loadout in use, so the key stayed vanilla. (2) Sneak and F pressed together: a vanilla client sends the F packet **before** the Sneak state in the same tick, so the server saw "F without Sneak" and swapped. |
| "Arcane Bolt / the focus doesn't work reliably" | Two causes. (1) The test **Fireball Wand** (`/givetestitem fireball`, a blaze rod carrying its own spell) caught every right-click first and cast the old Fireball, so abilities never saw it. (2) A plain blaze rod worked only with a precise aim. A miss by about one block, or a target past 8 blocks, still "went off": it spent Essence and its cooldown, drew nothing, and its message vanished. Essence was not the issue (1,095 at Intelligence 5). |

So the input problems were real, but most of the "doesn't work" feeling came from **invisible feedback** and **silent misses that still cost**.

## 2. What a vanilla 1.21.11 client tells the server

Abilities can only use what the server can see and tell apart. Verified on the sandbox where marked (✔), otherwise from the client's code:

| Input | Sent? | Notes |
|---|---|---|
| Right-click on air | Only with an item in a hand | An empty main hand **and** an empty off hand send nothing. The packet carries the look direction, which the server uses ✔. Holding the button repeats every 4 ticks. |
| Right-click on a block or mob | Always | A door or chest uses the click (vanilla). A hostile mob passes it on, and the client then also sends an air click in the same tick ✔. A villager trade or a mount consumes it (no air click). |
| Left-click on a mob | Always (attack) | |
| Left-click on air or a block | An arm swing | The server reports it as a left-click. A Q drop and an off-hand item use **also** swing the arm ✔, so those swings must not count. |
| **F** (swap hands) | Always, even with empty hands ✔ | Cancellable with no visible flicker: the client never predicts the swap. |
| **Q** (drop) | Only when the held slot has an item | The client predicts the drop; when the server cancels it, the item comes back. |
| Number keys / scroll | Only when the slot changes | The key of the slot already held sends nothing; it can't be a key. |
| Sneak, Sprint, Jump, movement | State changes (PlayerInputEvent) | Sneak's new state arrives **after** a click or F pressed in the same tick ✔. |
| Inventory (E), chat, other keys | Nothing usable in combat | R, C, V, X, Z and Tab never reach the server. |

Two server facts matter for the tests too:
- **Mob effects:** the client is never told a mob's effects. A slow or stun on a mob can only be read on the server (`/cerb mobs`).
- **Motion packet:** since 1.21.9 the server sends motion in a new format. A dash or knockback moves a real client. The test bot's library misreads the format, and the harness corrects it.

## 3. What other Minecraft RPGs do

Learn, don't copy: what established servers and plugins do on a vanilla client, from their wikis, configs and source (research of 2026-10-25). Where a point could not be confirmed from a source, it is left out.

**Wynncraft 2.x** (the click-combo reference):
- **Four spells, four 3-click patterns**, the same for every class except Archer:
  - Spell 1: R-L-R. Spell 2, the movement spell: R-R-R. Spell 3: R-L-L. Spell 4: R-R-L.
  - **Archer uses the mirror image** (L-R-L, L-L-L, L-R-R, L-L-R), "since it attacks using Right-Click" ([Bows](https://wynncraft.wiki.gg/wiki/Bows), [Wands](https://wynncraft.wiki.gg/wiki/Wands)).
  - Some newer spells add Sneak variants on top ([2.1.1](https://wynncraft.wiki.gg/wiki/Version_2.1.1)).
- **Feedback:**
  - Combo progress is an action-bar segment of three slots.
  - A cast shows "<Spell> Cast! -N" there.
  - **Failures go to chat** in dark red ("You don't have enough mana to cast that spell!"). Players complained that these flood chat ([forum](https://forums.wynncraft.com/threads/dont-spam-the-out-of-mana-text.295417/); parsers in the [Wynntils](https://github.com/Wynntils/Wynntils) mod).
- **Spell spam:** repeating a spell quickly costs more mana each time. It replaced a delay between spells in 2014 ([Mana](https://wynncraft.wiki.gg/wiki/Mana), [version history](https://wynncraft.wiki.gg/wiki/Version_history)).
- **Combos are not enough on their own:**
  - Players use client mods for one key per spell. The server rules allow a macro for "a single 3-click spell command at a time" ([rules](https://forums.wynncraft.com/threads/game-forum-rules.111874/)).
  - A macro mod notes that blocking the first click against accidental casts also stops right-clicking doors and NPCs.

**Hypixel SkyBlock** (the item-ability reference):
- **Abilities sit on the item.** The tooltip reads "Ability: <Name> RIGHT CLICK", with mana cost and cooldown lines. One item can have several triggers: RIGHT CLICK, SNEAK RIGHT CLICK, LEFT CLICK ([Builder's Ruler](https://hypixel-skyblock.fandom.com/wiki/Builder%27s_Ruler)).
- **Action bar:** the mana spent ("-N Mana (<ability>)") replaces the Defense figure ([Player](https://hypixelskyblock.minecraft.wiki/w/Player)), much as our HUD message slot replaces the middle figure.
- **Too little mana or on cooldown:** the ability doesn't work and chat says so ("This ability is on cooldown for Ns"). Hypixel later added settings to turn the cooldown chat messages and sounds off ([changelog](https://hypixelskyblock.minecraft.wiki/w/Changelog/2022/August_1)).
- **Dungeon classes:**
  - **Drop (Q) uses the Ultimate**, and Q drops nothing inside dungeons. Ctrl + Q uses the class ability, and the Ultimate's cooldown shows on the XP bar ([changelog](https://hypixel-skyblock.fandom.com/wiki/Changelog/2020/July_7)).
  - The off hand (F) is disabled in SkyBlock.

**MythicRPG and Fabled** (plugin frameworks):
- **MythicRPG** opens casting with F or Q, then a hotbar number or a click combo (3 inputs, 800 ms between them). Vanilla actions are cancelled while a combo is open, and it can show hints for the next input ([Casting](https://wiki.mythiccraft.io/mythicrpg/Spells/Casting)). Its documented limits:
  - the opening key can't be part of a combo;
  - without a global cooldown, "a player with several off-cooldown spells can fire all of them in the same tick".
- **Fabled** (formerly SkillAPI) has several modes ([config](https://github.com/magemonkeystudio/fabled/blob/dev/src/main/resources/config.yml)):
  - F, then a number key, is the default;
  - a scroll wheel;
  - a skill bar on hotbar slots 1–5, which cancels the slot change;
  - click combos (4 inputs, 1.0 s), off by default. Using Q or F in them "disables dropping items via Q" or swapping hands.
- **MMOCore:** key combos with the progress on the action bar, item interaction blocked during a combo, and a skill bar entered with F, then keys 1–6 ([docs](https://docs.phoenixdevt.fr/mmocore/skills/casting.html)).

**Platform facts behind the options:**
- **The server can't tell a scroll step from a number key:** the packet carries only the slot ([protocol](https://minecraft.wiki/w/Java_Edition_protocol/Packets)).
- **Switching the held item resets the attack charge** ([Damage](https://minecraft.wiki/w/Damage)).
- **There is no packet for a left-click in the air.** The server guesses it from the arm swing, so dropping with Q also looks like a left-click ([SPIGOT-5435](https://hub.spigotmc.org/jira/browse/SPIGOT-5435), [SPIGOT-5632](https://hub.spigotmc.org/jira/browse/SPIGOT-5632)).

**What we took, and what we didn't:**
- **Took:**
  - Wynncraft's four patterns and the mirrored bow (the combo option).
  - SkyBlock's item-ability keys: right-click and Sneak + right-click, and Q as an ability key, as in its dungeons (quick keys).
  - Feedback on the action bar rather than in chat. Both servers drew complaints, or added opt-outs, for chat-based refusals.
  - A global gap between casts (MythicRPG's stated limit).
  - Doors and NPCs keep their right-click (the macro-mod lesson).
  - The swing after a drop is not a left-click.
- **Didn't take:**
  - Hotbar or skill-bar modes: number keys and scroll change the held weapon, reset the attack charge, and re-selecting the held slot sends nothing.
  - Opening a casting mode that blocks vanilla actions.
  - Escalating spell costs. Our cooldowns do that job; it stays an option for Essence-heavy classes later.

## 4. The options, compared

Requirements: several abilities fluently in a fight, no fighting Minecraft's own controls, no wrong ability by accident, nothing hard to remember, a vanilla client, mixed-class loadouts with **5 actives** (CLASSES.md §10 item 4), every weapon family (melee, bow, focus, empty hand), and clear feedback.

| Scheme | Inputs per cast | While running | Conflicts with vanilla | Wrong-ability risk | To learn | Notes |
|---|---|---|---|---|---|---|
| **A. Sneak + 5 keys** (Step 5) | 1 + held Sneak | Slowed (Sneak walks) | F swap / Q drop leaked when a slot was empty | Same-tick Sneak race; L both attack and cast | 5 keys + Sneak | Retired |
| **B. Quick keys** (built, default) | 1 (2 slots need Sneak) | Full speed for 1–3 | F and Q are taken over **only** with a weapon or focus in hand | Same-tick Sneak race fixed by deciding one tick later | R, F, Q, Sneak R, Sneak F | SkyBlock's "RIGHT CLICK / SNEAK RIGHT CLICK" plus MMO keys |
| **C. Click combos** (built, option) | 3 clicks (F = 1) | Full speed (mouse only) | None (Q stays a drop) | A misclick changes the combo; a stray right-click starts one | 4 patterns, shown live | Wynncraft's idea; ours maps R R R = active 1 so holding right-click casts it |
| D. F, then a number key (MMOCore's skill bar) | 2 | OK | Number keys taken while in mode | The held slot's number key is dead | Easy (keys on screen) | Dead key and hotbar flicker; not built |
| E. Ability items in the hotbar (Fabled's skill bar) | 1 | OK | Takes 3–4 hotbar slots for good | Scrolling into a slot casts it | Very easy (icons, cooldown sweeps) | Costs inventory space; item-safety burden; not built |
| F. Select with scroll, cast with a click | 2+ | Slowed | Scroll taken | Low | Easy | Too slow for 5 actives; not built |
| G. Gestures (double-tap Sneak, double jump) | 1 | Good | None | Low | Hidden | Good for one dodge or dash; not for 5 general slots |

## 5. Recommendation

**Quick keys as the default, click combos as a per-player option.** Both are built, so the owner can compare them in the same playtest (`/class controls quick|combo`, or click the Controls tile). This is a player-controls decision, so it stays open until the owner has tried both.

- Quick keys are the closest to "natural during combat". The three most-used slots are one press each with no Sneak, so you keep sprinting. They read like any MMO's ability keys and like SkyBlock's right-click abilities. Players arrange their loadout so that the frequent abilities sit on right-click, F and Q.
- The research (§3) points the same way. Wynncraft players install client mods to get one key per spell, which quick keys give without mods, and SkyBlock's dungeons already put an ability on Q.
- Click combos suit players who want everything on the mouse and their fingers on WASD. They cost three clicks per cast.
- Both keep the fixes that matter more than the scheme: the visible message slot, refusals that cost nothing, aim assist, no swap or drop of a weapon, the one-tick Sneak decision, and the wand fix.

Reversible: the scheme lives in one class (`Classes/Controls`), separate from the abilities. The server default is one line (`classes.yml controls.default-scheme`). Each player's choice is saved in their class document; a choice this build doesn't know falls back to the default and never touches progress.

## 6. The rules as built

**When keys are ability keys.** Only with a weapon, a focus (blaze rod, breeze rod, stick) or an empty hand, **and** with at least one active equipped in the loadout in use. With a tool, a block, food or any other item, every key is vanilla. A player without equipped actives keeps vanilla F, Q and right-click.

**Quick keys.**

| Active | Key | With a bow or crossbow |
|---|---|---|
| 1 | Right-click | Left-click (the bow shoots with right-click) |
| 2 | F | F |
| 3 | Q | Q |
| 4 | Sneak + right-click | Sneak + left-click |
| 5 | Sneak + F | Sneak + F |

- F and Q never swap or drop a weapon or focus while armed. An empty F or Q says so ("Nothing on F"). A weapon can still be dropped from the inventory screen. With an empty main hand and an item in the off hand, F stays a swap: that is how you take the item into your hand.
- **Right-click stays vanilla:**
  - On a usable block (door, chest, lever...).
  - On a mob that isn't an enemy (trade, mount, feed).
  - With an axe on a block it strips, scrapes or unwaxes (logs, wood, copper).
  - When nothing is on right-click (actives 1 and 4 empty).
  - With an item that has a right-click of its own, when no right-click ability fits it: a spear or trident (its charge or throw), a spell item such as the Fireball Wand (its spell).

  Sneaking turns the first three into casts, as vanilla does with an item in hand.
- **The off hand's own use keeps right-click**, with or without Sneak:
  - A shield blocks.
  - Food, potions, throwables, a bow or armor are used.
  - A block or torch is placed when you click a block. In the air, right-click is still an ability key.

  The right-click abilities show dark on the ability bar meanwhile. A totem, arrows or a map in the off hand change nothing. (`Classes/VanillaUse`, unit-tested.)
- **Sneak + key:** the decision waits one tick. Sneak held at the press, or pressed in the same tick, counts.
- **Q casts a tick later.** While the drop event runs, the item is out of the hand. The ability waits until the weapon is back, so its strikes use that weapon's Power and a kill pays that weapon's skill.

**Click combos.** The combos are R R R, R L R, R L L and R R L for actives 1–4. F is active 5 and Q stays a drop. Each click may follow the previous one by at most 0.7 s (`combo-gap-ms`). The HUD shows the clicks so far and what the next click casts. Clicks inside a combo don't attack. A lone right-click expires and casts nothing. A bow's combos start with left-click, since R and L swap. A left-click on a player is always a plain hit and ends the combo.

**What a cast checks, before spending anything.** In order:
1. The ability is unlocked.
2. No loadout switch happened in the last second.
3. It fits the item in hand.
4. It is off cooldown.
5. A finisher has the Momentum it spends. Execute needs 1: at 0 it would be a plain hit that wastes its cooldown (Phase 3; `needs` on the resource effect, which a spend-all finisher gets by default).
6. A single-target ability has a target. The aim assist forgives 0.75 blocks around the target's hitbox, within range and in sight; walls block it.
7. A Blink has room.
8. There is enough Essence.

A refused cast spends nothing: no Essence, cooldown or Momentum. Area abilities (Cleave, Frost Nova) may still swing at nothing, as a weapon does.

## 7. Feedback

| Outcome | HUD message (middle of the bar, about 1.5 s) | Sound (refusals: only you hear it) |
|---|---|---|
| Cast | `✦ Cleave · 3 hit`, `✦ Execute · x2.2 · 3 Momentum`, `✦ War Cry · +15% damage · 8 s` | The ability's own sound, heard by everyone nearby |
| Cooldown | `Cleave 2.4 s` | A soft tick |
| Too little Essence | `Arcane Bolt needs 15 Essence` | A fizzle and a puff of smoke |
| No Momentum to spend | `Execute needs 1 Momentum` | A low note |
| Wrong item | `Arcane Bolt needs a focus (blaze rod, breeze rod or stick)`, `Execute needs a blade, an axe, a polearm or a blunt weapon` | A low note |
| No target, no room | `Arcane Bolt: no target within 8 blocks`, `Blink: no room` | A whiff |
| Not unlocked | `Execute unlocks at Warrior rank 6` | A low note |
| Nothing on the key | `Nothing on Sneak + F · set it in Classes & Abilities` | A low note |
| Switching | `Switching loadouts...` | A low note |

- The message replaces the HUD's middle figure. Health and Essence stay visible around it, and the HUD's own refreshes keep it until it ends. Nothing goes to chat.
- Refusal sounds play at most every 0.35 s, because a held right-click repeats five times a second.
- **What a cast looks like:** Arcane Bolt draws a coloured beam to its target. Execute's impact grows with the Momentum spent. Each Flurry strike sweeps higher. War Cry rings the caster, Riposte shows a guard and Feint a shimmer, and Blink leaves at both ends. Everything is one-off: nothing keeps running.

**The ability bar.** A boss bar shows your loadout's keys, names and state, for example `[R] Cleave  [F] War Cry 12  [Q] Charge  [⇧R] Execute  ⚔ ●●●○○`:
- **Colours:** green when ready, grey with seconds left, blue when Essence is short, gold when a finisher has no Momentum, dark when the ability doesn't fit what you hold.
- **Momentum:** shown when the loadout uses it.
- **When it shows:** in **auto** (the default) during a fight and for 5 s after a press or after taking a weapon in hand. **on** shows it always, **off** never (`/class bar`, or right-click the Controls tile).
- **Cost:** while shown it refreshes twice a second; hidden, it runs no task.

## 8. Where it lives

- **Controls and casting:**
  - `Classes/Controls`: the schemes, combo patterns, key labels and settings (pure, unit-tested).
  - `Classes/AbilityListener`: raw key events → presses, by scheme.
  - `Classes/AbilityCaster`: the checks, the cast, aim assist and visuals; returns a `CastResult`.
- **Feedback and bar:**
  - `Classes/CastFeedback`: messages and sounds.
  - `Manager/PlayerHUDManager.flash`: the HUD message slot.
  - `Classes/AbilityBar`: the boss bar.
- **Player choices:**
  - `ClassProfile` saves `controls:` and `bar:`. They are optional keys, so older documents load unchanged.
  - Commands: `/class controls`, `/class bar`.
  - The Controls tile in Player Menu → Classes & Abilities: click switches the scheme, right-click changes the bar.
- **Admin and test tools:** `/cerb mana <player> [n|full|empty]`, `/cerb class <player> cooldowns`, `/cerb mobs [radius]`.

## 9. Known limits

- **Empty hand:** a vanilla client sends nothing for right-click in the air or for Q. Unarmed builds cast with F and Sneak + F, and with right-click on a mob or block. Gauntlets as a held item would fix this when weapons are built.
- **Q briefly hides the held item** (the client predicts the drop until the server puts it back). F and right-click don't flicker.
- **Spear or trident:** right-click casts only when the right-click abilities fit a polearm. Otherwise the vanilla charge or throw stays.
- **A shield in the off hand takes right-click** (it blocks), so actives 1 and 4 can't be cast while it is there, nor click combos, which all start with right-click. Shield fighters put their abilities on F, Q and Sneak + F. This keeps vanilla blocking, which most players rely on. It is open for the owner's playtest: the alternative would trade blocking for the right-click abilities.
- **Presentation:** a resource pack could later add ability icons and cooldown sweeps. Nothing depends on one.
