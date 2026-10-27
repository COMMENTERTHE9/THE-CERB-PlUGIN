# Fishing Equipment, Guided Discovery and Collections

*Approved by the owner on 2026-10-27 ("Build Fishing Equipment, Guided Discovery, and Collections"); built in three increments on the development branch. This file holds the approved design and the choices made while building it: numbers, unlock levels, recipes, data and migration. Fishing itself is `docs/design/FISHING.md`.*

## 1. The loop

> Find an interesting missing species → track it → understand its requirements → choose a suitable setup → fish → discover something or beat a record → unlock another useful option.

Treasure hunting and sea-creature encounters are alternative goals inside the same skill: no new professions, no new XP bars. Manual fishing, AFK fishing and macros keep their full normal rewards (`docs/DECISIONS.md`, *respect the player's time*). Active play gets interesting opportunities; nothing checks attendance.

**Words players see (one meaning each):**
- **Lure**: reusable fishing equipment (the Lure category of the setup).
- **Bait**: an optional consumable, one used per catch.
- **Encounter**: a banked sea-creature summon (what used to be called a banked lure). Saved balances, the chosen creature, calling and refunds are unchanged; internal ids and commands stay (`/fishing lures` still works, `/fishing encounters` is new).

## 2. Skill pages, sections and Collections (increment A)

**The skill page stays readable.** Row 2 holds How to train, Attributes & speed, What it does and Next levels (slots 19, 21, 23, 25). Row 3 holds compact **section tiles**, one line of progress each, that open their own screens. Row 4 keeps the previous/next skill arrows; Fishing's "Since your last visit" summary sits between them (slot 40). Details live in the section screens, not in tooltips.

| Skill | Sections |
|---|---|
| Fishing | **Equipment** · **Journal: N of M species** · **Encounters: N** |
| Woodcutting | **Woods: N of M discovered** |
| Other skills | none until they have real functionality and progress data |

Sections are registered per skill (`GUIs/player/SkillSections`), so a later skill adds a tile without touching the page.

**Collections** (Player Menu → Collections, the hub tile that said "In development") is an index of every collection, read from the definitions:

| Collection | Data | Screen (the same one the skill page opens) |
|---|---|---|
| Fish species | the journal's species | Fishing Journal |
| Fishing treasure | the journal's treasure | Fishing Journal, Treasure tab |
| Sea creatures defeated | the journal's defeats | Encounters |
| Woods | Woodcutting's discoveries | Woods |

Totals come from `resources.yml` (nothing says "31" or "11" in code). Browsing reads what is already in memory: no rewards, no progress changes, no database work per redraw. Back returns to where the player came from, with the tab, filter and page they had (each screen keeps its state on the navigation stack).

## 3. The Fishing Journal (increment A)

- **Tabs:** All waters, each water (from `resources.yml`), Treasure.
- **Filters:** All species · Undiscovered · Available here.
- **Entries:** one item per species: found or not, rarity, personal-best weight, and its requirements (Fishing level, water, time, weather, biomes).
- **"Available here"** means the species meets its real eligibility rules where you fish now. It uses `FishingRules.checks` — the same checks `FishingRules.eligible` uses when a fish bites — never a second interpretation. It does not promise the next catch.
- **Where "here" is:** your bobber when a cast is out. Otherwise the water you were looking at when you opened the journal (within 30 blocks), labelled as a preview. Otherwise "not known yet: cast your line". Never your standing position.
- `/fishing journal [water]` keeps working in chat.

**Tracking one species.** Click a species to track it (saved on the player); click again to stop. The tracked view checks each requirement on its own line:

```
★ Tracked: Goldfin Trout
  ✓ Fishing 25 (you: 31)
  ✓ Rivers (your bobber)
  ✕ Night only (it is day)
```

When the bobber settles, a short HUD line says "Tracked Goldfin Trout can bite here" or what is missing ("needs Rivers"). It shows only when the verdict changes (a new water, nightfall), only when no other message is on the bar, never in combat, and can be turned off in the journal. **Tracking never changes the odds.**

## 4. "Since your last visit" (increment A)

The Fishing page shows, from real completed actions only:

```
42 catches · +1,260 Fishing XP
3 new species · 1 personal best
2 encounters banked
View new discoveries →
```

- **XP is what was really awarded** (the skill's total before and after), so creative mode and the level cap count nothing.
- **Bounded:** five counters and the last 12 notable catches (discoveries and personal bests with their weights), saved with the player, so a restart keeps them.
- **Acknowledged only by clicking the summary** ("mark as read"). Reopening or refreshing never clears it. The click subtracts exactly what that tile showed, so catches made while the screen was open stay unread.
- Fish stay stackable: weights live in the journal, not on items.

## 5. The setup (increment B)

Your **setup** is on your character. Any normal fishing rod you fish with uses it, in either hand; the rod keeps its own enchantments, durability and behaviour.

| Category | Unlocks | Parts (each owned once, kept for good) |
|---|---|---|
| **Rod** | Fishing 1 | Scaled grip, Heartwood handle, Pearl inlay, Monarch's blessing |
| **Line** | Fishing 10 | Resin line, Leviathan line |
| **Lure** | Fishing 25 | Rime hook, Pearl float, Sea-glass float, Lantern bob, Reef-eye lens; the specialists (§7) |
| **Reel** | Fishing 50 | Auto-hook Reel (§9) |

- **Each category is an assembly:** it holds every owned part of its kind, so the stacking rods had keeps working. Amounts are the old upgrades' (`resources.yml upgrades:`).
- **Getting a part:** Equipment → category → the part → **Fit**. It uses one of the item that made the upgrade (a Lurker Scale makes the Scaled grip). A confirmation screen shows what it uses and what it changes. A part can be owned before its category unlocks; it waits, shown as locked.
- **Crafting upgrades onto rods is retired.** The axe and bow upgrades (heartwood grip and limbs) are unchanged.
- **One engine.** The setup's parts are contributions to the same attribute engine (`Gathering/TackleSource`), so they go through the caps, diminishing returns and the PvP pause like every other stat. There is no separate fishing-stat engine.
- **Active part:** owned, configured on this server, its category unlocked at your Fishing level, and installed in the active preset. Anything else shows clearly and adds nothing.

**Previews before installing.** Each part shows the effective change, worked out by the engine with and without it:

```
Lure speed: 9.0% → 12.7%   (+6% raw; cap 30%)
Treasure chance: no change yet — Treasure Sense opens at Fishing 25
Removes: Specimen Hook (one specialist at a time)
```

- When the cap leaves almost nothing (under 0.1 percentage points), it says so instead of the raw number.
- Each line says when it applies:
  - always, with a rod;
  - in open water from Fishing 25 (treasure);
  - from Fishing 5 where a creature lives (encounters);
  - where an undiscovered species can bite (discovery).

**Three presets**, renameable (shift-click a preset and type the name in chat, or `/fishing setup rename <1-3> <name>`), initially **Discoveries**, **Treasure** and **Sea Creatures**.
- A preset is a list of owned parts plus which bait slots it uses. It never makes or copies parts.
- One preset is active. Switching is free and applies from the **next cast**. Each cast takes a snapshot of the stats, the specialist, the bait and the reel, so swapping during a cast changes nothing for that cast.
- **A part you get goes into the presets that suit it:**
  - bite-speed and encounter parts go into all three;
  - treasure parts go into Treasure and Sea Creatures (a treasure catch replaces a fish, so Discoveries leaves them out);
  - the reel goes into all three;
  - each specialist goes where its goal is.
  You can change any of this.

## 6. Old upgraded rods (increment B)

- **They keep working.** A rod with upgrades still counts them in your main hand, at any level, as before.
- **No double counting.** An upgrade on the rod and the same part in your setup count once (by id).
- **Converting is voluntary:** Equipment → "Upgraded rod in your hand". The preview lists each upgrade:
  - it becomes your part;
  - you already own it: its item (for example 1 Lurker Scale) comes back to you;
  - its category is still locked (for example a line at Fishing 8): owned, active from Fishing 10;
  - this server no longer has it: it stays on the rod.
- **The rod keeps** its enchantments, durability, name and any other data; only the upgrade data and upgrade lore lines go.
- **The conversion is one step on your thread.** It is refused, with nothing changed, if:
  - the rod in hand changed since the preview;
  - returned items don't fit your inventory.
- **An upgrade is never returned twice:** a converted rod has none left, and parts are owned once.
- **A part whose entry disappears** from `resources.yml` stays owned, shows "unavailable on this server" and does nothing until it returns.

## 7. Specialists (increment C)

Two Lure parts; **one specialist at a time** per preset (installing one removes the other, and the preview says so). Legacy upgrades never become exclusive.

| Specialist | Effect | Starting value (`resources.yml tackle.specialists`) |
|---|---|---|
| **Surveyor's Float** | Eligible species you haven't caught come more often: their weight ×1.5. Every gate stays (level, water, time, weather, biomes). Rarity order stays: each weight is multiplied, so an undiscovered legendary stays rare. The vanilla share is unchanged, so there are not more species bites, only a different pick among them. Where nothing undiscovered can bite, the odds are normal and the Equipment tile and a cast hint say so. | factor 1.5 |
| **Specimen Hook** | Heavier fish within each species' own range. The weight roll's curve flattens from u^2.5 to u^2.0: the average catch goes from 28.6% to 33.3% of the range, and the top tenth of the range from 4.1% to 5.1% of catches. Species, rarity, XP and encounters are untouched; no fish is ever outside its range. | strength 0.25 (exponent 2.5/(1+0.25)) |

With Wayfarer's Bait (§8) the discovery factors multiply, at most ×2.0. Both specialists come from collection milestones (§10).

## 8. Bait (increment B)

**The Bait Bag:** one slot at Fishing 1, a second at **Fishing 20**, a third at **Fishing 40**. A slot holds one bait type, up to 64.

| Bait (4 per craft, crafting table) | Recipe | Purpose | When it applies |
|---|---|---|---|
| **Grub Bait** | Rotten Flesh + Wheat Seeds | Faster bites: lure speed +8% (raw, through the engine) | always |
| **Chum** | Cod or Salmon + Bone Meal | Encounters: sea-creature chance +1.5% (raw) | from Fishing 5, where a creature lives at your level |
| **Glowworm Bait** | Glow Berries + Wheat Seeds | Night species: species that bite only at night come twice as often | at night, where such a species can bite |
| **Wayfarer's Bait** | Dried Kelp + Sweet Berries (recipe unlocked by a collection milestone, §10) | Discovery: undiscovered species that can bite here come 1.5× as often | where an undiscovered species can bite |

- **One bait at a time.** Extra slots are storage and fallbacks in slot order. Each preset can switch each slot on or off: Discoveries can use Glowworm Bait and fall back to Grub Bait while Sea Creatures uses Chum.
- **Situational bait is never wasted:** by day the Glowworm Bait stays in the bag, shown inactive, and the next slot (or none) is used. Ordinary fishing goes on.
- **One per completed catch.** At the cast, the first bait that can apply is taken out of the bag and held on the line.
  - **Consumed** when a catch really completes and the bait applied to it (Fortune's extra fish, missed bites and duplicate events take nothing more).
  - **Put back** when the cast ends any other way: reeling in nothing, hooking a mob, the bobber lost, a cancelled catch, logging out, dying, a restart (the hold is saved with the player).
- **No free effects.** Taking bait out, switching presets or disconnecting during a cast can't keep an effect: the held bait belongs to that cast.
- **Sneak-cast calls** never use bait.
- **Restocking is opt-in** (off by default): when a slot runs out, the same bait is moved in from your inventory, up to 64. It never buys, never searches chests, never substitutes.
- **When it runs out**, one line: *"Bait depleted. Normal fishing continues."* An empty bag never lowers the normal catch rate or XP.

## 9. The Auto-hook Reel (increment B)

- **Getting it:** craft the item (3 Iron Ingots + 1 Tripwire Hook + 1 Redstone, crafting table), then fit it in Equipment → Reel (Fishing 50).
- **On/off:** the Reel tile, or `/fishing reel on|off`.
- **What it does:** on a bite, 0.4 s later (`tackle.reel.delay-ticks: 8`), if the same bobber still has its bite, it reels in through the game's own retrieve. That is the same catch event as a click: catch, XP, discoveries, Fortune, bait and encounter banking all happen the normal way. The rod takes its normal durability (Unbreaking and wear resistance apply) and the reel sound plays.
- **It never recasts.**
- **It never reels a Sneak-cast call:** the call's bite is the creature, and a call cast is always reeled by hand. It never spends an encounter.
- **Races are safe:**
  - reeling by hand first, switching items, a broken rod, dying or logging out: the delayed reel finds no bite and does nothing;
  - an open menu doesn't matter;
  - a cancelled catch keeps the cast.
- **Cost:** one delayed task per bite. Nothing scans players.

## 10. Collection rewards (increment C)

| Milestone (recorded automatically) | Unlocks |
|---|---|
| Species found in **3 different waters** | **Wayfarer's Bait** recipe (in the recipe book) |
| **8 species** discovered | **Surveyor's Float** |
| **10 personal bests** set | **Specimen Hook** |
| **Every species** discovered | **Master Angler**: an emblem on the journal, the Fishing page and Collections, with a celebration |

- Each is granted exactly once and remembered on the player. Opening menus, reloads, rejoining and migration never grant one again; a lost record re-grants the same unlock, which is a set, not a stack.
- Players who already meet a milestone get it at their next join, with one message.
- Totals come from the definitions ("every species" is however many `resources.yml` lists).

## 11. Trophies (increment C)

The "new discoveries" screen lists the last 12 notable catches (discoveries and personal bests) with their real weights. **Keep as trophy** takes one fish of that species from your inventory and gives back a trophy:
- the same fish, named "Goldfin Trout · 3.21 kg";
- caught by, date and water in the lore;
- it doesn't stack.

Each notable catch can become one trophy, and the weight always comes from that catch. Rename it at an anvil like any item. There is no display system.

## 12. Data and migration

- **Journal** (`cerberus:fishing`, saved with the player as before) gains: the tracked species, the personal-best count, the rewards granted, the summary counters and the notable catches. An older journal reads as before, with all of these empty. Parts it doesn't know are kept when it is written back (forward compatible).
- **Equipment** (`cerberus:fishing_gear`, new, saved with the player in the same file as the inventory): owned parts, the three presets, the active one, the bait bag, the held bait, and the reel and restock switches.
  - A player without it starts with an empty setup.
  - A string it can't read is kept as it is and never overwritten.
- Because parts, bait and the inventory are saved together in the player's file, a crash can't separate a removed upgrade from its part or a held bait from the bag.
- **Config:** a new top-level `tackle:` section in `resources.yml` (categories, part categories, specialists, bait, the bag, the reel, rewards). A file without it gets the plugin's own (like `fishing:`). New items are listed in `GatheringCatalog.ADDED_BY_UPDATES`.

## 13. Performance

- Nothing runs per tick.
- **Menus** read in-memory state.
- **A cast** adds:
  - one engine preview when bait or a legacy rod changes the numbers;
  - one short check while the bobber settles, when tracking hints are on (at most six, 0.5 s apart).
- **A bite with the reel** adds one delayed task.
- **A catch** adds a few counters to the journal.
- Measured before and after for browsing, an idle open menu, ordinary fishing and automated reeling (`docs/PERFORMANCE.md`).

## 14. Commands

| Command | Who | What |
|---|---|---|
| `/fishing` | players | the summary in chat, as before |
| `/fishing journal [water]` | players | the journal in chat |
| `/fishing encounters` (also `lures`) | players | banked encounters |
| `/fishing call <creature>` | players | choose which encounter a Sneak-cast calls |
| `/fishing track <species\|off>` | players | track a species |
| `/fishing gear` | players | open Equipment |
| `/fishing setup <1-3>`, `/fishing setup rename <1-3> <name>` | players | switch or rename a preset |
| `/fishing reel on\|off`, `/fishing restock on\|off` | players | switches |
| `/cerb fish part <player> <id> [remove]` | admins | give or take a part |
| `/cerb fish bait <player> <id> [n]` | admins | put bait into a player's bag |
| `/cerb fish legacyrod <player> <upgrade,...>` | admins | make an old-style upgraded rod (migration tests) |
| `/cerb fish gear <player>`, `/cerb fish summary <player>` | admins | dump a player's equipment or summary |
| `/cerb fish info\|lure\|force\|journal\|stats` | admins | as before |
