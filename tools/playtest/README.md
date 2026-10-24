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
| `progression.js killxp` | Kill XP v2 by mob level, anti-grind |
| `progression.js swings` | Swings to kill a level 1 / 50 zombie at Blade Mastery 1 / 50 / 100 |
| `progression.js survive` / `magic` | Vitality + Fortitude (max health, armor, per-hit loss), Essence (mana, Fireball scaling) |
| `progression.js stats` | Player Menu → Stats page values, live update after a level change |
| `progression.js persist` | `PERSIST=set`, restart the server, `PERSIST=check`: attributes identical after restart |
| `progression.js perf` | Hits don't rebuild attribute snapshots |
| `progression.js offswitch` | With `attributes.integration.pve-offense: false` + `/cerb reload`, old swing counts return |

Never point these at a server with real players or online-mode=true.
