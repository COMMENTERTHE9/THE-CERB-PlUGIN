# Farming, Cooking and meaningful food

The owner's milestone of 2026-10-28: Farming and Cooking as disciplines worth mastering, at the standard of Woodcutting's Felling and Fishing's discoveries. Farming grows better ingredients; Cooking turns them into meals that make the character stronger. Each also stands on its own. Built on what exists: the gathering pipeline, the attribute engine, the menu framework and the shipped-config rules. No second skill system, attribute engine, inventory framework or crafting platform.

## 1. The loop

- **A farmer:**
  - grows and harvests crops, and earns Farming XP for every ripe harvest, by hand or with macros;
  - earns more crops through Fortune (crops), and replants at 10;
  - sweeps whole fields with Wide Harvest at 25;
  - grows quality crops at 50: Fine, Superior and Masterwork.
- **A cook:**
  - earns Cooking XP for food really made: cooked in a furnace or smoker, baked at a crafting table;
  - unlocks meals from Cooking 10;
  - makes meals better the higher their Cooking (the meal's tier);
  - cooks with quality ingredients, which lift a meal one tier.
- **Meals** give one timed effect at a time through the attribute engine. Each has its own role: survival, harvesting, exploration, fishing, gathering, a hard fight.
- **Independent paths:**
  - Cooking needs no Farming level: every meal is made from things a level-1 player can get.
  - Farming needs no Cooking: crops, Vitality and Fortune pay on their own.
  - Fishing connects through fish dishes.

## 2. What exists today (inspected 2026-10-28)

- **Farming and Cooking pay no XP and do nothing** (`docs/SKILL_AUDIT.md`). `FarmingSkill`, `CookingSkill`, `ResourceYieldManager`, `CraftingManager` and `QualityBuffs` are unreachable `applyEffect` code. The audit found they would duplicate items and grow yields without limit, so none of it is wired. This milestone replaces them in the gathering runtime and leaves the old classes untouched.
- **Vanilla healing is off for players** (`VirtualHealthListener.onEntityRegainHealth` cancels it). Cerberus health regenerates on its own timer (`PlayerVirtualHealthManager.startHealthRegenTask`). Food fills hunger and restores no Cerberus health. Meals here are the first consumables that act on the character, and they do it through the engine and the health pool, never vanilla health.
- **The attribute engine** has a timed source, `BuffSource` (admin dev buffs: attributes only, in memory). Meals get their own source, which can carry speed stats and survive a relog.
- **Fortune is scoped by yield** (ore, wood, fish). Farming adds **crops**.
- **XP integrity:** creative and spectator earn nothing, and the XP display says why. XP is counted from what really happened: the final break event, the game's crafted-items statistic.

## 3. Farming (increment A)

### 3.1 Crops in this milestone

Data in `resources.yml` → `farming.crops` (stable ids). Table columns:
- **Ripe**: when a harvest pays.
- **Method**: how it is harvested.
- **Replant**: the seed a replant takes.
- **Level**: for the weak-crop rule and the journal, never a lock.
- **XP**: per ripe harvest.
- **Discovery**: XP the first time.

| id | Block | Ripe | Method | Produce | Replant | Level | XP | Discovery | Quality |
|---|---|---|---|---|---|---|---|---|---|
| wheat | WHEAT | age 7 | break | wheat | wheat seeds | 1 | 3 | 20 | yes |
| carrots | CARROTS | age 7 | break | carrot | carrot | 5 | 3 | 20 | yes |
| potatoes | POTATOES | age 7 | break | potato | potato | 5 | 3 | 20 | yes |
| sugar_cane | SUGAR_CANE | a grown segment | break (the column above falls too) | sugar cane | none | 1 | 1 per segment | 20 | no |
| beetroots | BEETROOTS | age 3 | break | beetroot | beetroot seeds | 10 | 3.5 | 30 | yes |
| cactus | CACTUS | a grown segment | break | cactus | none | 10 | 1 per segment | 30 | no |
| sweet_berries | SWEET_BERRY_BUSH | age 2 or 3 | pick (right-click) or break | sweet berries | none (picking keeps the bush) | 15 | 3 | 40 | yes |
| melon | MELON | a melon that grew (or wild) | break | melon slice | none (the stem stays) | 20 | 5 | 40 | no |
| pumpkin | PUMPKIN | a pumpkin that grew (or wild) | break | pumpkin | none (the stem stays) | 20 | 5 | 40 | no |
| cocoa | COCOA | age 2 | break | cocoa beans | cocoa beans (same log face) | 30 | 4 | 60 | yes |
| glow_berries | CAVE_VINES(_PLANT) | with berries | pick or break | glow berries | none | 35 | 4 | 80 | no |
| nether_wart | NETHER_WART | age 3 | break | nether wart | nether wart | 40 | 4 | 100 | yes |

The levels spread like the woods' (1 to 40), so the weak-crop rule (a quarter, 20 levels above a crop) reaches every crop only past Farming 60, as it reaches every wood past Woodcutting 60.

Not in this milestone: bamboo and kelp (building and underwater materials, not food crops), torchflower and pitcher pod (sniffer seeds whose ripe stage is a block that can also be placed; later), mushrooms and wild plants (Herbalism's scope, not decided).

### 3.2 What pays

- **A ripe harvest by a player** in survival or adventure: by hand, any tool, an auto-clicker or a macro. One harvest pays once: at MONITOR, once nothing can cancel it.
- **Crops are always planted by players: that is farming.** Mining's placed-block rule does not apply to them, and bone meal (a player's or a dispenser's) is full growth.
- The exceptions are blocks a player can place as items: melon, pumpkin, and sugar cane and cactus segments. Those placed by hand pay nothing, because nothing grew. The existing placed-block memory (`PlacedBlocks`) tracks them. Fruit and segments that grew, and wild ones, pay.
- **Nothing pays for:**
  - an unripe crop;
  - trampling;
  - creative or spectator (the XP display says why);
  - a crop another plugin's protection refused.
- **No player, no XP.** Water, pistons, villagers, dispensers and observer farms keep their vanilla outputs. With no player in the event, nobody earns Farming XP; see §6.
- **Sugar cane and cactus:** breaking a segment pays for it and for each grown segment above it that falls with it, counted at the break. Those segments fall a tick later; one broken again before it falls (a fast macro) pays nothing, since it was already counted.
- **The weak-crop rule** (Mining's and Woodcutting's `weak-resources`) applies.

### 3.3 What Farming gives the character (`attributes.yml`, the one engine)

- **Vitality 0.25 per level:** max health +0.25% a level, +25% at 100.
- **Fortune (crops) 0.5 per level,** a new yield scope `crops`.
  - Each ripe harvest has a chance (the shared Fortune rule: 0.5% a point, at most 50%) of one more of its produce, never of its seeds.
  - Mining's Fortune stays on ore, Woodcutting's on wood, Fishing's on fish.
- **Harvesting efficiency is a mechanic, not a number.** Crops break instantly, so a "harvest speed" stat would do nothing. Farming's efficiency is how much one action harvests: Replant at 10 and Wide Harvest's reach (3×3 at 25, 5×5 at 50, 7×7 at 75, 9×9 at 100).

### 3.4 Techniques

- **10, Replant.** A ripe crop of a replanting kind, broken by hand, is planted again at once (age 0).
  - The seed comes from that harvest's own drops first, else from the player's inventory.
  - With neither, the spot stays empty, and the HUD says so once a minute.
  - Never a free seed, never a second harvest.
  - Cocoa goes back on the same side of the same log.
  - Off with `/farming replant off`.
- **25, Wide Harvest** (Farming's Felling). **Sneak + break a ripe crop with a hoe:** every ripe crop of the same kind within the reach is harvested (wheat, carrots, potatoes, beetroots, nether wart).
  - **Reach:** 3×3 at 25, 5×5 at 50, 7×7 at 75, 9×9 at 100, on the same level ±1, nearest first. Every ripe crop of that kind in the square is taken, also across a water channel.
  - **Bounded steps:** 16 crops a tick on the region thread that owns them, one sweep per player at a time. The reach is checked on the region thread before it starts.
  - **Every crop, at its step:**
    - ripeness, type, ownership and loaded chunk are checked again;
    - it must be inside the world border and outside spawn protection, as vanilla checks for the player's own break, and it is offered to protection plugins as the player's own break (`Farming.HarvestBreak`); the sweep stops at the first refusal;
    - it costs 1 hoe durability (Unbreaking and Smithing's wear resistance apply), and the sweep stops before the hoe would break;
    - it pays its XP, Fortune roll and quality roll, and is replanted when Replant is on and a seed is there.
  - It also stops when the hoe leaves the hand, the player leaves or dies, or the sweep reaches another region or an unloaded chunk.
  - Drops are merged at the first crop.
- **50, Crop quality.** Each ripe harvest of a cooking crop rolls once for its produce's quality.
  - **Odds** (`farming.quality`):

    | Farming level | Fine | Superior | Masterwork |
    |---|---|---|---|
    | 50 | 10% | 2% | 0% |
    | 100 | 22% | 8% | 3% |

  - The quality is written on the produce items (`cerberus:crop_quality`, a lore line; Common produce stays a plain item). Those items stay vanilla: edible, craftable, compostable, tradeable. They stack only with the same quality. The key is not forged gear's `cerberus:quality`, so produce never counts as equipment.
  - Only the crops meals use roll one (the table's last column): cane, cactus, melons, pumpkins and glow berries roll none.
  - A campfire passes the quality on: a Fine potato roasts into a Fine baked potato. A furnace or smoker makes plain food: its output slot takes only items like the recipe's plain result, so a quality result would stop it until emptied (or burn fuel for nothing beside plain food). The quality is rolled once at the harvest, can't be rerolled and has no upgrade path.
- **75 and 100:** the reach grows (7×7, then 9×9) and the quality odds keep rising.
  - **Proposed, not built:** scythes (a Farming tool with its own reach), irrigation and harvesting machines (with the attribution in §6), custom crops for Herbalism and Alchemy, and seasonal or regional crops.

### 3.5 Discoveries and the pages

- **Discoveries:** the first ripe harvest of each crop is a discovery. It pays its XP once, with a celebration, and is remembered on the player (`cerberus:crops_discovered`).
- **The Farming page:**
  - a **Crops: N of 12** tile opens the crop list: each crop's ripe rule, method, level and XP, discovered or not, and which techniques work on it;
  - the page's "What it does" lists Replant, Wide Harvest's reach and the quality odds at the player's level, from the same rules the game uses.
- **Collections** gets a Crops entry on the same screen.

## 4. Cooking (increment B)

### 4.1 What pays Cooking XP (exactly)

- **Food taken out of a furnace or smoker by a player** (the game's crafted-items statistic, the same path as Smithing's smelting; a shift-click pays every item):

  | Food | XP each |
  |---|---|
  | cooked beef, porkchop, mutton, chicken, rabbit, cod | 3 |
  | cooked salmon | 4 |
  | baked potato | 2 |
  | dried kelp | 0.25 |

  Hoppers pay nobody (no player).
- **Food crafted at a crafting grid** (an allowlist; reversible storage recipes such as dried kelp blocks and honey never pay):

  | Food | XP |
  |---|---|
  | bread | 4 |
  | cookie | 1 each (8 a craft) |
  | pumpkin pie | 6 |
  | mushroom stew | 6 |
  | beetroot soup | 6 |
  | rabbit stew | 12 |
  | cake | 20 |
  | golden carrot | 5 |

- **Cerberus meals** (§4.2) pay their own XP per meal made.
- **Food a player puts on a campfire** pays that player, at the furnace table's rate, when the campfire finishes it: the slot they filled is remembered (in memory, from the campfire's own start event) and paid when that slot's food is done. Food put on in creative, food whose player left before it was done, and food another plugin put there pay nobody. (In Java Edition only a player puts food on a campfire: hoppers and dispensers can't.)
- **Never:** creative or spectator (the XP display says why), a crafter block (no player), or a click that made nothing.
- **Cooking gives Vitality 0.25 per level** (`attributes.yml`).

### 4.2 Meals

Each meal is data in `resources.yml` → `cooking.meals`: a stable id, name, edible base item, shapeless crafting recipe, the Cooking level that unlocks it, XP, and effect. The base item's own food points and eating time are vanilla. Recipes are crafted in a crafting grid (the four-ingredient meals fit a player's own 2×2 grid) and stay locked until the level: the grid shows no result, and the recipe book shows them once open.

| Meal | Level | Role | Recipe (shapeless) | Made on | Effect (Common tier) | Duration | XP |
|---|---|---|---|---|---|---|---|
| **Hearty Stew** | 10 | Survival | bowl, any cooked meat, carrot, potato, beetroot | rabbit stew | Vitality +6; restores 25% of max health over 20 s | 10 min | 20 |
| **Farmer's Breakfast** | 15 | Harvesting | bread, egg, cooked porkchop, sweet berries | baked potato | Fortune (crops) +10 | 15 min | 30 |
| **Trail Ration** | 20 | Exploration | bread, dried kelp, sweet berries, cocoa beans | cooked rabbit | Movement speed +8% | 15 min | 40 |
| **Angler's Platter** | 25 | Fishing | cooked salmon, cooked cod, beetroot, wheat | cooked salmon | Lure speed +6%, sea-creature chance +1% | 15 min | 50 |
| **Miner's Pasty** | 35 | Gathering | pumpkin pie, cooked beef, carrot, wheat | pumpkin pie | Gathering speed +8%, chopping speed +8% | 15 min | 60 |
| **Warrior's Feast** | 50 | A hard fight | bowl, cooked beef, golden carrot, baked potato, nether wart | beetroot soup | Power +3 (all weapons), Fortitude +4; restores 15% over 10 s | 10 min | 80 |

- **Made on** an edible vanilla item: its food points and eating time stay vanilla, the meal can be eaten at full hunger, and a bowl meal gives its bowl back. Meal bases are items vanilla makes in a furnace or rarely crafts, so the grid looks up a recipe only when one of them shows.

- **Tiers:** a meal's tier is fixed when it is made, never rolled.
  - **The cook's Cooking level:** Common below 25, Fine 25–49, Superior 50–74, Masterwork 75+.
  - **Quality ingredients:** when at least half of the meal's crop ingredients are Fine or better, the meal is one tier higher (Masterwork at most).
  - **Potency** (every number of the effect): ×1.0, ×1.2, ×1.4, ×1.6. **Duration:** ×1.0, ×1.25, ×1.5, ×2.0.
  - Meals of one tier stack. The meal's lore shows its tier, effect and duration.
- **Eating** (the consume event, once it stands):
  - **One meal effect at a time.** A new meal replaces the running one and says so; the same meal refreshes its time. Nothing stacks.
  - **Effects:** attributes and speed stats go through the engine. Caps and the offense budget apply. Attributes don't act in PvP and speed stats pause there, as for every source while PvP is off.
  - **Restoration** heals the Cerberus health pool over its seconds, starting when eaten. It pauses while PvP-tagged, never goes past max health, and a new meal ends the old restoration. Healing is therefore bounded by one meal at a time.
  - **Persistence:** the effect runs in real time and is saved on the player with its end time. It survives a relog or restart if still running. Death ends it. Restoration does not survive a relog.
- **Weapon Mastery's lifesteal** (decision 6) is unchanged. Meals make healing exist; whether lifesteal changes is still the owner's call.

### 4.3 The Cooking page

- A **Cookbook: N of 6** tile opens the meals: level, ingredients, effect and duration at the player's current tier, and whether they have cooked it.
- The page's "What it does" shows the tier the player cooks at and its potency and duration.
- **Collections** gets a Meals entry.

## 5. Persistence and migration

- **No database changes.** The data lives on the player (PDC):
  - crops discovered and the Replant switch (`cerberus:crops_discovered`, `cerberus:farming_flags`);
  - the running meal (`cerberus:meal` = `1|id|tier|endMillis`);
  - the meals cooked (`cerberus:meals_cooked`).
- **On items:** a crop's quality (`cerberus:crop_quality`) and a meal's id and tier (`cerberus:meal_item` = `id|tier`), so they travel with the item.
- **Config:**
  - `resources.yml` gets whole new sections, `farming:` and `cooking:`, and its meal items. An edited older file keeps its numbers, gets those sections and items from the plugin's own file, and the log says so (`GatheringCatalog.addUpdates`, `ADDED_BY_UPDATES`). `farming: off` / `cooking: off` turn them off.
  - `attributes.yml` gets Farming's and Cooking's skill lines through `AttributesConfig`'s update list.
  - `ShippedConfig` records the old hashes, so unedited files refresh.
- Skills, XP, classes, fishing journals, gear and old saves are untouched.

## 6. Automation and attribution

- **Same rewards for the same actions:** a player harvesting or cooking by hand, with an auto-clicker or a macro, gets the same XP and drops.
- **World automation keeps its vanilla output and pays nobody:** water and piston harvesters, villagers, observer farms, hoppers and crafters.
  - Nothing in the codebase records who owns a machine.
  - Inventing an owner, such as the nearest player, would be wrong.
- **Proposal for the owner (not built):** a *Farm Ledger*.
  - A craftable block a player places to claim a field or a kitchen line (bounded size, protection-plugin aware).
  - Machine harvests or furnace output inside the claim credit its owner a fixed share of the XP (a first candidate: 50%), once per real output.
  - It would be stored in chunk data like the placed-block marks.
  - It needs the owner's decision on the share, claim size and machine types.

## 7. Performance

**Added work:**
- **A harvest:** a crop lookup by material (a map), a ripeness read, one roll each for Fortune and quality, and the XP fraction carry.
- **A wide harvest:** 16 crops a tick at most, one sweep per player.
- **Cooking:** the statistic path that already exists. The crafting grid gets a recipe lookup only when its shown result is a meal's base item (the lesson of `docs/PERFORMANCE.md`).
- **A meal:** one source entry per player, one snapshot rebuild when eaten and when it ends, and a restoration task of a few seconds.
- **Nothing runs per tick,** and nothing is written to the database for any of this.

**Measured before and after** (`tools/playtest/load.js`):
- `farm`: 9 players harvesting wheat by hand;
- `widefarm`: wide harvests (after only);
- `cook`: baking bread;
- `menu`, `menuidle`, `idle`.

**The baseline** was taken on 69e772d before any code (9 bots, two 60 s JFR runs per load after a warm-up, the method of `docs/PERFORMANCE.md`). Cerberus's share of the region thread, in milliseconds per tick:

| Load | Run 1 | Run 2 | Work per bot |
|---|---|---|---|
| `farm` | 0.013 | 0.017 | 267-268 harvests |
| `cook` | 0.072 | 0.048 | 130 bread |
| `menu` | 0.222 | 0.191 | 25 opens, 225 clicks |
| `menuidle` | 0.012 | 0.013 | one open menu |
| `idle` | 0.013 | 0.008 | nothing |

`widefarm` has no baseline: Wide Harvest doesn't exist before this milestone.

**After** (the final build, `docs/PERFORMANCE.md` → "Farming and Cooking"): `farm` 0.032 / 0.028, `widefarm` 0.028 / 0.038, `cook` 0.074 / 0.084, `menu` 0.182 / 0.179, `menuidle` 0.006 / 0.007, `idle` 0.008 / 0.008, TPS 20.0 throughout. A paid harvest costs about 10 µs and a paid craft about 20 µs, mostly the XP path every skill shares; the region's own time per tick is unchanged.

## 8. Testing

Two new harnesses, `tools/playtest/farming.js` and `cooking.js`, with positive and negative controls. Unit tests cover the crop rules, ripeness, the quality odds, meal tiers, the meal codec and the cooking allowlist. The existing regression slices also run.

**Farming:**
- ripe and unripe crops of every method;
- picking versus breaking;
- placed versus grown melons and cane;
- bone meal growth paying in full;
- water harvesting paying nothing;
- Fortune and its cap;
- Replant with seeds from the drops, from the inventory, and with none;
- Wide Harvest with a protection plugin, cancellation, a breaking hoe, a hoe switch and a region edge;
- quality items surviving a relog and a restart;
- no duplicated drops or XP.

**Cooking:**
- valid and locked recipes;
- ingredient use;
- shift-click batches;
- furnace and smoker takes versus hoppers;
- the allowlist versus reversible loops;
- meal tiers from level and ingredients;
- meal effects on the real character (max health, the speed stats, Fortune, Power);
- replace and refresh rules;
- restoration bounds;
- death, logout and restart;
- creative earning nothing.

**As tested** (`docs/STATUS.md` → "Done: Farming and Cooking"): all of the above in game on the sandbox, except four things checked in the code and unit tests only:
- Wide Harvest across a Folia region edge: each crop is checked for its region before it is broken, and one sandbox player can't straddle two regions on purpose;
- Replant falling back to the inventory when another plugin removed the drops: vanilla never does, so a ripe crop always replanted from its own drops in game;
- quality items across a restart: they are ordinary items carrying their data; the restart check (`cooking.js restart-set`/`restart-check`) covers a running meal and Replant's switch;
- Fortune's cap: the engine's, covered by its unit tests; Farming 100's 25.8% is below it.

Spawn protection and the world border on Wide Harvest (added by the review) were checked in the code too: the test bots must be operators, who are exempt.
