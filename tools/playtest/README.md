# Playtest checks

Headless in-game checks for Cerberus, for a **local sandbox server only** (they join with
`online-mode=false` and change the test character's skills). Used by cloud/Linux sessions to
verify gameplay after a change.

```
cd tools/playtest && npm install          # once
# start a sandbox server (tools/cerb.sh run with online-mode=false), then in its console:  op CerbSmoke
node smoke.js                             # PASS/FAIL per check, non-zero exit on failure
```

| Script and phase | What it checks |
|---|---|
| `smoke.js` | Join, HUD, Player Menu, kill XP, elite spawns, horse riding, instant respawn |
| `admin.js main` | Every Control Center section, edits, spawns, mob inspector |
| `admin.js attrs` / `buffexpire` | Attributes section: inspector, simulator (changes nothing), dev buffs, buff expiry (5 min) |
| `admin.js staleclick` | A lagging admin's double-click: a click aimed at the skill list that arrives after the skill editor opened must not press the editor's button in that slot (+10 levels); with a control click on the editor itself |
| `admin.js nonop` / `deopmid` / `dupe` / `cross` / `owners` | Security: non-admins refused (also with a forged panel item), deop mid-session, no item can be taken, editing another player, cleanup removes only your own test mobs. `nonop` needs the bot de-opped and a forged item given from the console; `cross`/`owners` need `CerbTarget` (op for `owners`). |
| `menu.js nav` | Player Menu 2.0: head and `/menu` open the hub, every tile's state, Skills → category → skill pages (training, attributes, effects, next levels), browsing, Back/Home/Close, renamed skills; Ascension, Equipment (with a worn iron chestplate when op), Bestiary, World and Guide screens. Run it non-op too: `OP=0 node menu.js nav CerbPlayer` |
| `menu.js names` | Renamed skills in chat: level-up message, `/cerb skill` with either name |
| `menu.js safety` | With the menu open no click type moves anything (checked by rejoining); the head can't be dropped, swapped, placed, framed or put on an armor stand (with controls proving the bot's interaction reaches the server); a powered shelf swaps hotbar slots 7–8 but not the head; one head after rejoin and respawn |
| `menu.js motion` | Player Menu 2.1: opening reveal (centre column first, buttons work at once), header sweep between screens, page-turn sounds (told apart by volume and pitch: this client's 1.21.11 sound ids are outdated), category tabs and the last opened category, the skill bar filling, progress since the last close (count badges, lines, one chime, one level-up flash), an interrupted animation writing nothing into the next screen, no animation left running on an idle open menu |
| `menu.js shelves` | Decision B: three chained powered shelves swap the whole hotbar except the head and the admin chest; the shelf items meant for those two slots stay on the shelves; server view after rejoin and the shelves' contents (counted after breaking them) show nothing lost or duplicated |
| `menu.js perf` | Browsing every screen three times opens no database connection and rebuilds no attribute snapshot (`/cerb showqueues` counters) |
| `progression.js killxp` | Kill XP v2 by mob level, anti-grind |
| `progression.js swings` | Swings to kill a level 1 / 50 zombie at Blade Mastery 1 / 50 / 100 |
| `progression.js survive` / `magic` | Vitality + Fortitude (max health, armor, per-hit loss), Essence (mana, Fireball scaling) |
| `progression.js stats` | Player Menu → Stats page values, live update after a level change |
| `progression.js persist` | `PERSIST=set`, restart the server, `PERSIST=check`: attributes identical after restart |
| `progression.js perf` | Hits don't rebuild attribute snapshots |
| `progression.js speed` | Speed stats measured in game: server attack-speed value by Blade/Weapon Mastery level, hits at a fixed 0.55 s rhythm (charged vs not), bow power and critical arrows by draw time and Ranged Precision, movement speed by Pathfinding, Fireball cooldown by Spell Weaving, and the PvP pause (spawns `CerbTarget`) |
| `progression.js defense` | How a hit on a player is reduced, from exact `/damage` hits and the combat trace: armor follows what is worn, resistance × armor (70% today, under the 75% cap), the defense bar's 70% share |
| `progression.js defense-cap` | The 75% cap binding. Needs a temporarily raised `attributes.limits.resist` (e.g. 0.75) in the sandbox's `plugins/CerberusPlugin/attributes.yml` + `/cerb reload`; restore it afterwards |
| `progression.js pvp` | PvP as before Attributes 2.0 (spawns `CerbTarget`): pre-attribute armor and resistance (Fireball), hits scaled to the defender's health so the share lost matches a 100-health defender, lifesteal restoring the same share, burn ticks during the PvP tag (and not after it); PvE unchanged |
| `progression.js mastery` | Weapon Mastery with the specialized skills: Power multiplier and swings to kill a level-100 zombie (sword BM 100 + WM 1 / 100, axe WM 100) |
| `progression.js fire` | Decision C: fire resistance on every kind of fire, from the combat trace: `/damage` in_fire, on_fire and lava, plus standing on a real campfire and magma block (the server names those only when a block caused them). Lava at half; a non-fire hit gets none; under the 75% cap |
| `progression.js projectile` | Decision D: a skeleton's arrows train Survival and Heavy Armor Mastery; a dispenser's arrows, your own harming potion, the second arrow of a volley (invulnerability window) and a 0-damage hit train nothing. Exact arrow hits come from `/damage … by <arrow> from <skeleton>` with the arrow's owner set to the skeleton |
| `progression.js spear` | Decision E: a spear kill pays Weapon Mastery XP, once (no Heavy Armor Mastery XP on top); spear hits use Weapon Mastery's Power (x7.25 at level 100, as axes). The bot jabs like the vanilla client: a "stab" action (player action 7) at a target 3 blocks away |
| `classes.js learn` | Step 5 classes: skills open Warrior, Mage and Duelist (and only when the entry is met); `/class` shows ranks or what is missing; learning is never taken back. Use a **new bot name** (op it first): classes stay learned |
| `classes.js keys` | The cast keys (Sneak + right-click, left-click, F, Q, scroll) cast actives 1–5 with a sword, and each key is consumed: nothing dropped, swapped or moved. Without Sneak, with a block, with a bow (right-click) or with an empty slot every key stays vanilla |
| `classes.js trees` / `mixed` | Progress in two trees at once, kept when switching loadouts; one loadout from three classes (actives, passives, keystone) works together, the shared movement lockout applies across classes, one attribute rebuild per switch |
| `classes.js combat` | The three classes play differently, measured from the combat trace: Cleave hits a group, Execute spends Momentum, Arcane Bolt at 7 blocks, Frost Nova damages and slows a group, Riposte negates a hit and answers at 2x, Flurry strikes three times, Feint's guaranteed crit is used up by one hit |
| `classes.js exploits` | Rapid presses cast once; cooldowns survive loadout switches and a relog; War Cry never stacks and ends with its loadout; combat lock, switch gap, cast lock; loadout rules; abilities never hit players and a Sneak-attack on a player stays a PvP hit (spawns `CerbTarget`, op it first) |
| `classes.js menu` | Player Menu → Classes & Abilities: class tiles, a class's abilities, a loadout, the ability picker (equip, empty, refused in combat), switching from the menu, all matching `/class` |
| `classes.js persist` | `PERSIST=set`, restart, `PERSIST=check`: ranks, loadouts, the active loadout and a running 30 s cooldown survive. `PERSIST=online` edits a loadout twice and waits for a server stop while still online (the shutdown save), then `LNAME=... PERSIST=check` |
| `load.js <bots> <seconds> <mode>` | Reproducible load for performance baselines (`combat`, `abilities`, `hurt`, `menu`, `menuidle`, `mixed`, `idle`): bots in one region next to spawn; profile meanwhile with `tools/perf/profile.sh` (`docs/PERFORMANCE.md`). `abilities` = `combat` with Warrior/Duelist bots pressing their cast keys on a rhythm. Ops the load bots for the run and deops them after. |
| `progression.js offswitch` | With `attributes.integration.pve-offense: false` + `/cerb reload`, old swing counts return |

Never point these at a server with real players or online-mode=true.
