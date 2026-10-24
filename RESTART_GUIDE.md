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
6. **Make yourself operator (once).** With the server running, type in its window: `op YourMinecraftName`. The test commands below need it.
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
| Join | Action bar: `300/300⛨  100/100♥  0.00%★  1000.00/1000.00✎` (defense, health, luck, mana). A **Player Menu** head in hotbar slot 9. Operators also get an **Admin Control Panel** ender chest. |
| Right-click the head → **Skills → Combat Skills** | Each skill with its level and XP bar. Click one for its detail page. Other main-menu buttons say *Coming Soon* (not built yet). |
| `/cerb healthstate` | `Custom HP: 100.00/100.00`, `Lifecycle: ALIVE`, `Session: ACTIVE` |
| `/givetestitem kit` | Cerberus Blade, Styx Cleaver, Hydra's Reach, Fireball Wand, and Cerberus armor (fills your hotbar). |
| Right-click the **Fireball Wand** | `You cast Fireball!`; mana drops by 20 and refills. |
| `/summon minecraft:zombie`, then kill it with the Cerberus Blade | A floating label like `Zombie 50❤ Lv1` that drops as you hit. On the kill: `+40 Blade Mastery XP`. |
| `/cerb spawn elite_zombie` | `Spawned elite_zombie (Rare)`; its label shows about `3.2K❤`. |
| `/cerb combatdebug`, then let a zombie hit you | A chat trace `MobScaling → Incoming → DefenseBar → Final`. Run it again to turn it off. |
| `/testregen 50` | Health drops by 50, then regenerates about 4 per second. |
| `/kill` | Instant respawn at full 100 HP. No death screen; this is intentional. |

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
