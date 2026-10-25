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
| `admin.js nonop` / `deopmid` / `dupe` / `cross` / `owners` | Security: non-admins refused (also with a forged panel item), deop mid-session, no item can be taken, editing another player, cleanup removes only your own test mobs. `nonop` needs the bot de-opped and a forged item given from the console; `cross`/`owners` need `CerbTarget` (op for `owners`). |
| `menu.js nav` | Player Menu 2.0: head and `/menu` open the hub, every tile's state, Skills → category → skill pages (training, attributes, effects, next levels), browsing, Back/Home/Close, renamed skills; Ascension, Equipment (with a worn iron chestplate when op), Bestiary, World and Guide screens. Run it non-op too: `OP=0 node menu.js nav CerbPlayer` |
| `menu.js names` | Renamed skills in chat: level-up message, `/cerb skill` with either name |
| `menu.js safety` | With the menu open no click type moves anything (checked by rejoining); the head can't be dropped, swapped, placed, framed or put on an armor stand (with controls proving the bot's interaction reaches the server); one head after rejoin and respawn |
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
| `progression.js offswitch` | With `attributes.integration.pve-offense: false` + `/cerb reload`, old swing counts return |

Never point these at a server with real players or online-mode=true.
