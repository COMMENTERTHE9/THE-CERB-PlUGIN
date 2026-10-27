# Fishing: the third complete gathering loop

*The current milestone (`docs/ROADMAP.md` → "Fishing, and respecting the player's time"). Built on the gathering infrastructure (`docs/design/GATHERING.md`: data in `resources.yml`, custom items, upgrades, Fortune, the XP display), the attribute engine, the mob registry and combat pipeline, and the level-up presentation (`docs/design/LEVEL_UP.md`). Status: **designed and built 2026-10-27** (`docs/STATUS.md` → "Done: Fishing"; checked in game with `tools/playtest/fishing.js`; your checklist is `docs/PLAYTEST_CHECKLIST.md` §13).*

## 1. What the owner asked for (2026-10-27, `docs/DECISIONS.md` 1–3)

- **The same quality as Woodcutting:** satisfying from the first cast; meaningful XP and attributes; increasingly valuable discoveries and rewards; milestones that unlock new capabilities; different fish, different waters, rare encounters; rod upgrades and useful resources.
- **Real sea-creature fights:** levelled creatures through the mob levels, the combat engine, classes and abilities, with rewards; encounters grow more exciting with Fishing.
- **Banked encounters:** any legitimate catch can earn a stored encounter; the player starts the fight when ready. Manual and automated catches earn them equally. Stored encounters persist safely, never duplicated or lost; spawned creatures have entity limits.
- **AFK fishing is allowed with full XP and catches:** no reduced fraction, no inactivity timers, no movement or interaction challenges. Compact setups are not invalidated; the environment may change which catches come and how rare they are.
- **Respect the player's time** (the guiding philosophy, `docs/DECISIONS.md`): active play earns *more* (fights, discoveries, travel), passive play is never made worthless. Protect the server and prevent broken reward loops without treating automation as cheating.
- **Replace the old Sea Monster:** today every catch has a flat 5% chance to spawn a Rare guardian on the player, with no level, no cap and no limit (`CerberusPlugin.onPlayerFish`); an AFK farm keeps spawning them.

## 2. Principles (studied, not copied)

Studied for principles only: Hypixel SkyBlock's fishing (sea creatures, trophy fish), Stardew Valley and Animal Crossing (species by place, time and weather; a collection), Terraria (biome fish, quests), RuneScape and Wynncraft (fishing professions). No names, numbers, menus or mechanics are copied.
- **Where you fish matters.** Each kind of water has its own species; time and weather change what bites.
- **The catch is the moment.** A rare bite shows itself before you reel; a new species, a record weight and a creature on the line are each celebrated.
- **Fights are the active part.** Every catch can earn a creature, but meeting it is your choice and your fight.
- **Fishing feeds Fishing first.** Creature drops and treasure upgrade rods; later systems (Cooking, Trading) get the fish and coins.
- **Every number is data** in `resources.yml` and `attributes.yml`, retuned after playtests.

## 3. The loop

Cast into water → the bobber settles (lure speed, Quick Bite, rain) → a bite (its splash hints at the rarity) → reel in → the catch: a vanilla fish, a species of that water, junk, or treasure → Fishing XP by what it is → a new species is a **discovery**, a heavier one a **personal best** → sometimes a **lure** is banked → later, Sneak and cast to **call** the creature and fight it → its drops upgrade your rod → better rods, higher levels, new waters, rarer species, stronger creatures, the apex.

## 4. Waters and species (`resources.yml` → `fishing:`)

The water is read where the bobber floats:
1. **Cave pools:** no sky light at the bobber and below sea level (any biome).
2. **The biome's water:** rivers (frozen rivers too); swamps; temperate, warm and cold seas.
3. **Lakes and ponds:** anywhere else.

| Water | Common | Uncommon | Rare | Epic | Legendary |
|---|---|---|---|---|---|
| Rivers | Silver Dace (1) | River Perch (5), Brook Char (15, night) | Goldfin Trout (25) | Old Whiskers (45, night) | Riverking Sturgeon (75, rain) |
| Lakes and ponds | Pond Bream (1) | Reed Pike (10) | Moonlit Carp (20, night) | Emerald Tench (50) | |
| Swamps | Mud Loach (1) | Bog Eel (10) | Mire Gar (30) | Witchlight Bowfin (55, night) | |
| Temperate seas | Sea Bass (1) | Striped Mackerel (10) | Silver Tarpon (35) | Bluefin Tuna (55) | Abyssal Angler (85, night, deep ocean) |
| Warm seas | Reef Wrasse (1) | Coral Snapper (15) | Sunburst Parrotfish (30) | Sailfin Marlin (60) | |
| Cold seas | Ice Cod (1) | Arctic Char (15) | Frostfin Halibut (35) | Glacier Wolffish (60) | |
| Cave pools | Blind Cavefish (5) | Glowgill (20) | Dripstone Eel (40) | Crystal-eye (70) | |

*(n) = the Fishing level it needs; conditions after it.* 31 species, each a custom item (a fish material with its name and lore), edible and cookable like any fish.

**What a bite is.** Vanilla still decides junk, fish or treasure (Luck of the Sea and open water work as always). A fish or junk bite may become a species of that water: the share that stays vanilla falls from 70% at level 1 to 35% at 100, and the rest goes to the species your level and the moment allow, by their weights. Vanilla treasure is always kept.

**Weights and records.** Every species catch has a weight (most are small, a few are big). The heaviest of each species is your personal best; beating it is announced.

**XP per catch** (data): junk 2, a vanilla fish 10, vanilla treasure 30; species and Cerberus treasure by rarity: common 12, uncommon 25, rare 60, epic 150, legendary 400. With a Lure III rod (about 360 catches an hour) that is about 5,000 XP an hour at the start, like mining and woodcutting by hand; rarer species, faster bites and creature fights raise it as you level. **The same for every catch, by hand or by a farm.**

## 5. Techniques (milestones, data)

| Level | Technique | What it does |
|---|---|---|
| 5 | **Something Stirs** | Sea creatures start taking the bait (lures, §7) |
| 10 | **Quick Bite** | Bites come 15% sooner (with lure speed, never below 40% of the vanilla wait) |
| 25 | **Treasure Sense** | In open water, a catch can be Cerberus treasure (§6) |
| 50 | **Deep Waters** | Creatures you call come as elites: half again the health, the XP and one more roll of their drops |
| 75 | **Twin Catch** | Fortune's extra fish can come twice |
| 100 | **Apex** | The apex of each water can take the bait |

## 6. What Fishing gives (attribute engine, `attributes.yml`)

- **Fortune (fish)** 0.5 per level: a chance of one extra fish per catch (the shared Fortune rule: 0.5% per point, at most 50%). Never on treasure, lures or junk. Fishing's Fortune now counts on fish only, as `SKILL_PROGRESSION.md` planned.
- **Lure speed** (new secondary stat, a rod in hand): bites come sooner. 0.2% per level, capped at 30% with diminishing returns; rod upgrades add to it.
- **Treasure chance** (new): from Treasure Sense, the chance a catch in open water is Cerberus treasure. 0.05% per level, capped at 15%.
- **Sea-creature chance** (new): the chance a catch banks a lure. 0.04% per level (about 2% at 50, 3.3% at 100 after its curve), capped at 10%; rod upgrades add to it.

All three show on the Stats page, the skill page and in level-up messages, and pause in PvP like the other speed and handling stats (`docs/ROADMAP.md` → PvP).

**Cerberus treasure** (open water, from 25): Sunken Coins (common), Sea Glass (uncommon), Drowned Locket (rare), Pearl of the Depths (epic). Sea Glass and the Pearl upgrade rods; coins and lockets are kept for Trading and Enchanting later.

## 7. Sea creatures: lures you bank, fights you choose

**Earning.** After each catch that really happened, the sea-creature chance rolls. On a hit, one creature of that water that your level allows is chosen by weight, and a **lure** for it is banked: *"✦ A River Lurker took your bait and slipped away. Lure kept (3). Sneak and cast to call it."* Manual and automated catches roll the same. AFK farms bank lures; they never spawn a mob.

**Banking.** Lures are stored on the player (saved with the player like their inventory, no item to lose in a full AFK inventory, nothing to duplicate). They show in the Fishing page and `/fishing`.

**Calling.** Sneak and cast with a lure banked. The bobber settles, and on the next bite the creature surfaces at the bobber instead of a fish: a splash, a sound, a title. The lure is spent only when the creature is really there. Which one: the lure you chose in the Fishing page or with `/fishing call <creature>`, else one of the water you cast into, else your first.

**The fight.**
- The creature's level follows your Fishing level (a little spread), and never below the world's own level there. Its health, damage, armor penetration and ranged attacks scale through the normal mob pipeline (`combat.yml`), so classes, abilities and attributes all apply.
- It fights only you: nobody else can hurt it or steal its drops, and it hurts nobody else.
- One creature out per player, at most 32 on the server (both data). Beyond that a call is refused and nothing is spent.
- **Rewards (yours):** the kill pays your weapon's skill as any kill does, plus the same again as Fishing XP; its drops fall for you alone; the first defeat of each kind is a discovery.

**Nothing is lost.** The creature is never saved into the world. If it leaves without dying (you log out, die, go too far for 30 s, change worlds, or 5 minutes pass, or the server restarts), its lure comes back. A fight in progress is remembered on the player, so a crash or restart returns the lure at the next join, and a win at the moment you disconnect is recorded as a win at your next join (never as a lure back).

**The creatures (data; health is a multiple of a normal mob of the same level and tier):**

| Creature | Mob | Waters | From | Tier | Health | Drops (rod upgrades) |
|---|---|---|---|---|---|---|
| River Lurker | Drowned with a rod | Rivers, lakes | 5 | Common | ×2 | Lurker Scale |
| Bog Stalker | Bogged (poison arrows) | Swamps | 10 | Common | ×2 | Bog Resin |
| Tidecaller | Drowned with a trident | Temperate and warm seas | 20 | Common | ×2.5 | Tide Pearl |
| Frostbound Mariner | Stray (slowing arrows) | Cold seas | 20 | Common | ×2.5 | Rime Shard |
| Hollow Angler | Drowned, lantern-lit | Cave pools | 30 | Common | ×3 | Angler's Lantern |
| Reef Guardian | Guardian (laser) | Warm and temperate seas | 35 | Common | ×4 | Reef Eye |
| Drowned Monarch (apex) | Giant drowned | Fresh water, swamps, caves | 100 | Uncommon | ×1 | Monarch's Seal |
| Leviathan (apex) | Giant guardian | Every sea | 100 | Uncommon | ×1 | Leviathan Scale |

## 8. Rods (`upgrades:`, the heartwood mechanism)

A rod and one of these in a crafting grid make the same rod with the upgrade, once each, keeping its enchantments and name. Upgrades count on the rod in your main hand.

| Upgrade | Made with | Adds |
|---|---|---|
| Scaled grip | Lurker Scale | Lure speed +6% |
| Rime hook | Rime Shard | Lure speed +4% |
| Heartwood handle | Heartwood (Woodcutting) | Lure speed +4% |
| Pearl float | Tide Pearl | Treasure chance +2% |
| Sea-glass float | Sea Glass | Treasure chance +1.5% |
| Lantern bob | Angler's Lantern | Treasure chance +1.5% |
| Resin line | Bog Resin | Sea-creature chance +1% |
| Reef-eye lens | Reef Eye | Sea-creature chance +1.5% |
| Pearl inlay | Pearl of the Depths | Sea-creature chance +1% |
| Monarch's blessing | Monarch's Seal | Lure speed +8% |
| Leviathan line | Leviathan Scale | Sea-creature chance +2% |

Every stat keeps its cap. Custom items are never placed, burnt or used in vanilla recipes; species fish are ordinary food.

## 9. Respecting time, protecting the server

- **Full rewards for automation:** every legitimate catch pays the same XP, items and lure chance, whoever or whatever reels it in. No inactivity timer, no movement check, no penalty for compact farms (treasure needs open water, as in vanilla).
- **What pays nothing (nothing really happened):** creative and spectator, a cancelled catch, hooking an entity or an item (not a catch).
- **No uncontrolled spawning:** catches never spawn mobs. A creature appears only when its owner calls it, at most one per player and 32 on the server, and it is never saved into the world.
- **No duplication:** a lure is spent once, on the thread that owns both the player and the bobber, at the moment the creature exists; a refund happens once (the active fight is one record). Fortune never copies treasure or a lure.
- **Bounded work:** a catch is a few table lookups; a bite a roll and one sound; a fight one check a second per creature.

## 10. Data and extension points

Waters, species, treasure, creatures, techniques and XP are `resources.yml` data; a custom species or creature is an entry, proven by synthetic test entries (a test water and a test creature). Creatures register in the mob registry by id, so `mobs.yml` can override a creature's tier or XP. An older edited `resources.yml` gets the `fishing:` section and the new items and upgrades from the plugin's own file, in memory (the file on disk is never rewritten).

## 11. Admin and test tools

`/cerb fish info` (the water and odds at your bobber or where you look), `/cerb fish lure <player> <creature> [n]`, `/cerb fish force <player> species <id>|treasure [id]|lure [creature]|apex [creature]|clear` (the next catch, for tests), `/cerb fish journal <player>`, `/cerb fish stats`. Players: `/fishing` (journal and lures), `/fishing call <creature>`. Harness: `tools/playtest/fishing.js`; load: `tools/playtest/load.js fishing`.

## 12. Not in this version

Lava fishing in the Nether, fishing spots that move, fish quests, a Cooking use for species, trading lures, more apexes. Each is data or a small system on top of this one.
