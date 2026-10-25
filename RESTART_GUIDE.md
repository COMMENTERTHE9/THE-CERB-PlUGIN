# Cerberus — Restart Guide

How to build the plugin, run the test server, join it, test, and stop it.
**First-time setup** is done once per computer; the **daily loop** is what you do every session.

| Fact | Value |
|---|---|
| Server | **Folia 1.21.11, build 14** (`folia-1.21.11-14.jar`). PaperMC ended support for 1.21.11 on 2026-10-14; staying on it is fine for development. |
| Minecraft client | **Java Edition 1.21.11** exactly. No mods, no resource pack. |
| Java | **JDK 21 or newer** for both building and running (21 and 25 both verified). |
| Server folder | `test-server\` inside this repo (git ignores it, so switching branches never touches your worlds). |
| Plugin data | `test-server\plugins\CerberusPlugin\` (`cerberus.db` holds skills; `*.yml` are configs). |

All scripts live in the repo root and are double-clickable. They call `tools\cerb.ps1`, which checks prerequisites and stops with a one-line reason when something is wrong.

---

## First-time setup (once per computer)

1. **Get the current code.** In the repo folder:
   ```
   git fetch
   git checkout claude/quirky-mccarthy-nhaz58
   git pull
   ```
2. **Check your setup.** Double-click **`status.bat`**. It shows which Java it found, whether `test-server\` and the Folia jar exist, your worlds and their sizes, the port, and whether a server is already running. Anything marked `[FAIL]` or `[warn]` tells you what to fix.
3. **Java missing?** Install Eclipse Temurin JDK 21 from https://adoptium.net. If you keep Java somewhere unusual, set `BUILD_JAVA_HOME` (and optionally `RUN_JAVA_HOME`) to its folder.
4. **No server yet?** Double-click **`setup.bat`**. It creates `test-server\` and downloads the Folia jar, checking its SHA-256. It never touches an existing jar, world, or config.
5. **EULA (only for a brand-new server folder).** The first start creates `test-server\eula.txt` and stops. Read https://aka.ms/MinecraftEULA, change `eula=false` to `eula=true` yourself, and start again.
6. **Make yourself operator (once).** With the server running, type in its window: `op YourMinecraftName`. The test commands below need it. (The server window itself accepts `cerb status`, `cerb reload`, `cerb skill …`, `cerb heal …`.)
7. **Client.** In the Minecraft Launcher: *Installations → New installation → Version: release 1.21.11*.

## Daily loop

1. **Start:** double-click **`run.bat`**. It builds the plugin (unit tests included), backs up plugin data, copies `Cerberus.jar` into `test-server\plugins\`, and starts the server in that window.
   - Ready when the window shows: `[CerberusPlugin] Cerberus Plugin has been enabled!` (about 30–60 s).
   - `start.bat` starts without rebuilding. `deploy.bat` only builds and copies.
2. **Join:** Minecraft → *Multiplayer → Direct Connection* →
   - **`localhost`** when Minecraft runs on the same PC as the server (add `:port` if `status.bat` shows a port other than 25565).
   - From another PC on your home network, use the LAN address `status.bat` prints (for example `192.168.1.20`). Don't port-forward or expose the server to the internet.
3. **Where to test:** anywhere near world spawn. Mob level rises by 1 for every 150 blocks you are from spawn, so spawn is the easy zone; walk out for tougher mobs.
4. **Stop:** type **`stop`** in the server window and wait for `[ok] server stopped cleanly`. Don't close the window with X; that can skip saving.
5. **Changed code?** Stop the server, then `run.bat` again. Never use `/reload`: it duplicates listeners and tasks.

## Five-minute check that Cerberus works

| Do | You should see |
|---|---|
| Join | Action bar like `305/305⛨  102/102♥  0.00%★  1035.00/1035.00✎` (defense, health, luck, mana; they grow with your skills). A **Player Menu** head in hotbar slot 9. Operators also get an **Admin Control Panel** ender chest. |
| Right-click the head (or type `/menu`) | The **Player Menu**: your realm, total skill level, health, Essence and attributes at the top; Skills, Stats & Attributes, Ascension, Equipment, Bestiary, World & Exploration and Guide open; Collections, Recipes, Quests and Unlocks say *In development* when clicked. Every screen has Back, Player Menu (home) and Close. |
| Player Menu → **Skills → Combat Skills → Blade Mastery** | The skill page: level and XP strip, how to train it (kills with any sword, XP per mob level), what its levels give (Power, sword attack speed) and what that does now and next level, its own effects marked *Live* / *Against players only* / *Not active yet*, and the next milestones. The arrows at the bottom flip through the category. |
| `/cerb healthstate` | `Custom HP: 102.00/102.00` (full), `Lifecycle: ALIVE`, `Session: ACTIVE` |
| `/givetestitem kit` | Cerberus Blade, Styx Cleaver, Hydra's Reach, Fireball Wand, and Cerberus armor (fills your hotbar). |
| Right-click the **Fireball Wand** | `You cast Fireball!`; mana drops by 20 and refills. |
| `/summon minecraft:zombie`, then kill it with the Cerberus Blade | A floating label like `Zombie 50❤ Lv1` that drops as you hit. On the kill: `+40 Blade Mastery XP`. |
| `/cerb spawn elite_zombie` | `Spawned elite_zombie (Rare)`; its label shows about `3.2K❤`. |
| `/cerb combatdebug`, then let a zombie hit you | A chat trace `MobScaling → Incoming → DefenseBar → Final`. Run it again to turn it off. |
| `/cerb skill YourName Blade_Mastery level 25` | `Blade Mastery is now level 25 (0 XP)`; the Skills menu shows it. Set it back the same way. Renamed skills accept both names: `Pathfinding` or `Navigation`. |
| `/testregen 50` | Health drops by 50, then regenerates about 4 per second. |
| `/kill` | Instant respawn at full health. No death screen; this is intentional. |
| Player Menu → **Stats & Attributes**, or `/stats` | Your six attributes, what each does and which skills feed it; your weapon's damage and crits (click the weapon tile to see other weapon types); **Speed & handling** (attacks per second, bow draw time, movement speed, Fireball cooldown); health, armor, mana; what the next level gives; kill XP per mob level. |

### Feel the progression (attributes)

| Try this | You should see |
|---|---|
| `/cerb spawn zombie 50 noai`, hit it with a netherite sword at Blade Mastery 1, then `/cerb skill YourName Blade_Mastery level 100` and spawn another | About 20 swings at level 1, about 5 at level 100. Damage numbers float over the mob (crits marked `✯`). |
| `/cerb combatdebug`, then hit a mob | `[Combat] Power(Swords, Power 50.0 -> x3.50): 28.0`, plus crit and lifesteal steps. |
| Set Survival, Heavy Armor Training (Heavy Armor Mastery) and First Aid (Field Medicine) to 100 | `/stats` → Survivability: max health about 300, armor bonus, bigger defense bar, 50% resistances. |
| Set Intelligence and Mana Regeneration to 100 | Max mana about 3,000, regeneration about 150/s; Fireball does about x5 damage to mobs. |
| Kill a mob far below your weapon skill (20+ levels) | Reduced XP and the message `(Low-level mob: reduced XP...)`. |
| Hold a sword: Blade Mastery 1, then 100 | `/stats` → Speed & handling: about 1.61 → 1.88 attacks per second (a full swing every 0.62 s → 0.53 s). The attack-cooldown indicator under your crosshair refills visibly faster. |
| Bow: Ranged Precision 1, then 100 | A bow released after about 0.8 s already shoots a full-power critical arrow (sparkle trail); at level 1 it needs the full second. The pull animation itself stays vanilla. |
| Pathfinding (Navigation) 100 | You walk and sprint about 9% faster. |

Turn any part off in `plugins/CerberusPlugin/attributes.yml` (`attributes.integration`, and `attributes.secondary.stats` for the speed stats), then `/cerb reload`. PvP is unchanged: the speed bonuses pause for 15 seconds whenever you hit or are hit by a player (`attributes.secondary.in-pvp`). An `attributes.yml` from before 2026-10-25 has no `secondary` section; the built-in defaults then apply. To change them, copy that section from the new file in the jar (or rename your file and `/cerb reload` to get a fresh one, then copy your own changes back).

## Admin Control Panel (Cerberus Control Center)

Operators open it with **`/cerb panel`** or by right-clicking the **Admin Control Panel** ender chest (operators get one on join). Non-operators can't open it; the item does nothing for them and is removed on join.

| Section | What you can do |
|---|---|
| Players | Inspect anyone online (health, mana, defense, skills). Full restore, refill mana, teleport to them, set skill levels and grant XP. Lowering a level asks first; the player is told. |
| Combat Lab | Spawn any `mobs.yml` entry or vanilla test mob at an exact level (1–100), count and AI mode. Inspect nearby mobs' tier, level and HP pool; set their HP, freeze, highlight, remove. Clean up test mobs. Combat stats, debug traces, tier table. |
| Progression Lab | XP curve, kill-XP v2 table (by mob level and tier), XP sources, which skills affect play today. |
| Attributes | Pick a player: every attribute with its sources (the same numbers as their Stats page), the rules and limits in force, a **simulator** ("what would Blade Mastery 80 give?", changes nothing) and **dev buffs** (+25/+100 to an attribute for 5 minutes, never saved). |
| My Test Character | Full restore, test kit, menu head, game mode, teleport to spawn, skills, combat stats. |
| Server & Plugin | Build time, versions, memory, worlds, config and registry state. |
| Maintenance | Reload configs, save all players (both ask first where it matters). |

Test mobs spawned from the panel are never saved into the world, and cleanup removes only them. Every change made through the panel is written to `latest.log` as `[Admin] …`.

## Where things are

| What | Path |
|---|---|
| Server log | `test-server\logs\latest.log` (older logs zipped beside it) |
| Per-player combat logs | `test-server\plugins\CerberusPlugin\logs\sessions\` (`/cerb log <player>` dumps the live one) |
| Automatic plugin-data backups | `test-server\backups\plugin-data\<date-time>\`, made before every deploy/start; the last 10 are kept |
| Full backup (worlds + data + configs) | `backup.bat` (server stopped) → `test-server\backups\full-<date-time>\` |
| Built plugin | `target\Cerberus.jar` |

## When something goes wrong

| Message | Fix |
|---|---|
| `No JDK 21+ found` | Install Temurin JDK 21, or set `BUILD_JAVA_HOME` to your JDK folder. |
| `The server is running on port 25565` | Use the window that is already open, or type `stop` there first. |
| Server stops right after starting and mentions the EULA | First-time setup, step 5. |
| `Outdated client` / `Outdated server` | Your launcher is not on release 1.21.11. |
| `Connection refused` | The server window isn't open or hasn't finished starting. `localhost` only works on the same PC. |
| Kicked: `Cerberus could not load your saved data` | The database couldn't be read. Your data was not changed. Rejoin; if it repeats, send `latest.log`. |
| Build fails | The error is just above `[FAIL] Build failed`. A failed test means real code broke. |

## Linux / macOS / cloud sessions

Same commands through bash: `./setup.sh`, `./run.sh`, `./deploy.sh`, or `tools/cerb.sh status|build|start|backup`.
Settings for either version: `CERB_SERVER_DIR` (use a server folder elsewhere), `CERB_SERVER_JAR`, `JAVA_XMS` / `JAVA_XMX` (heap, default 1G / 4G).
