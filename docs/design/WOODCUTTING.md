# Woodcutting: the second complete gathering loop

*Item 4 of the Progression Depth & Woodcutting milestone (`docs/ROADMAP.md`). Built on the gathering infrastructure (`docs/design/GATHERING.md`: resources as data, placed-block memory, Fortune, the weak-resource rule, the XP display), the attribute engine and the level-up presentation (`docs/design/LEVEL_UP.md`). Status: **built and checked in game, 2026-10-26** (`docs/STATUS.md` → "Done: Woodcutting"; `tools/playtest/woodcutting.js`).*

## 1. What the owner asked for

- **The loop:** find a natural tree → cut it with a fitting tool → earn XP → feel the skill's effect → better or specialized tools → new harvesting capabilities → resources that feed other systems → harder, more rewarding trees.
- **Trees that matter:** different properties, tool requirements, techniques, multi-log methods, discoveries, beginner and advanced tools, milestones that unlock capabilities. Basic trees stay easy. At least one higher-level technique visibly changes how Woodcutting plays.
- **Connections:** wood feeds a few genuinely useful recipes, upgrades or mechanics. No separate economy or crafting engine.
- **Protect the world:**
  - nothing rewrites or regenerates the owner's world;
  - natural and placed logs are told apart;
  - no infinite XP from placing and breaking, or from regrowing trees;
  - large effects respect protected areas, nearby builds, chunk and region boundaries and the player's intent;
  - no unsafe cross-region block changes under Folia.
- **Extension points:** data registries, proven by a synthetic custom tree. No enormous forest framework.
- **Studied for principles only:** Hypixel SkyBlock's foraging updates (0.23 and 0.27). No names, assets, maps, menus, formulas or implementations are copied.

## 2. Principles we keep (studied, not copied)

Studied: Hypixel SkyBlock's foraging updates 0.23 (June 2025) and 0.27 (August 2026), and for contrast RuneScape's woodcutting rework, Wynncraft's gathering, and two Minecraft plugins (mcMMO's tree felling, AuraSkills' foraging). Sources are in §14.
- **Trees are not all alike.** Harder woods ask for better tools and pay more. A weak tool is a soft gate (it pays less), not a wall.
- **Milestones open capabilities, not only numbers:** new ways to fell, new woods worth the trip.
- **A bonus belongs to its activity.** Fortune for wood counts on wood, not on ore.
- **Wood feeds other systems.** Here heartwood upgrades axes and bows.
- **Feedback at every scale:** each log (a sound, the XP line), each tree (Felling), each milestone (a technique), each new wood (a discovery).
- **Expect retuning.** Every number is data in `resources.yml` and `attributes.yml`.
- **The known pitfalls,** each answered in §10:
  - farming placed logs;
  - regrowth farms (one of the plugins pays full XP for trees grown from saplings);
  - felling that bridges the gaps between trees or bites into builds;
  - felling lag (one plugin fells a whole tree in one tick, and other plugins' handlers then run for every log at once);
  - protection bypass (another checks only the first log);
  - Folia regions (a tree is never assumed to lie in one region).

## 3. The loop in Cerberus

1. **Find a natural tree.** Oak and birch grow everywhere and pay a beginner well. Better wood grows in harder places: cherry groves on mountains, dark forests, mangrove swamps, pale gardens (with their creakings), and the Nether's crimson and warped forests.
2. **Cut it with an axe.** Logs pay Woodcutting XP only with an axe. Harder woods want a better one: with too weak an axe a log pays a quarter of its XP and gives no Felling and no heartwood, and the HUD says which axe pays in full. By hand the wood still drops, with no XP.
3. **Feel the skill.** Every level adds Vitality, Fortune for wood (a chance of an extra log) and chopping speed with an axe. The level-up says exactly how much.
4. **Unlock techniques at milestones.** Felling (Sneak while cutting, and the tree comes down) grows from small trees at 10 to giant trunks at 50; at 75 the canopy comes down too.
5. **Discover woods.** The first natural log of each wood pays a discovery bonus, once.
6. **Bring back heartwood.** A rare core from natural logs, more often from harder trees and at higher levels. It goes into an axe's grip (faster chopping) or a bow's limbs (faster draw).
7. **Move on.** A tree 20 or more levels below the player pays a quarter (as weak mobs and weak ore do), so progress means harder trees in harder places.

## 4. Woods (`resources.yml`, data)

| Wood | Level | XP per log | Axe | Heartwood per log | Discovery | Grows in |
|---|---|---|---|---|---|---|
| Oak (and azalea trees) | 1 | 4 | any | 0.2% | 20 | plains, forests, swamps |
| Birch | 1 | 4 | any | 0.2% | 20 | birch and mixed forests |
| Spruce | 5 | 5 | any | 0.3% | 40 | taiga, snowy slopes (giant spruce: 2×2) |
| Jungle | 10 | 5 | any | 0.3% | 60 | jungles (giant trees: 2×2) |
| Acacia | 10 | 6 | any | 0.4% | 60 | savannas |
| Cherry | 15 | 6 | stone | 0.5% | 80 | cherry groves |
| Dark oak | 20 | 7 | stone | 0.6% | 100 | dark forests (2×2) |
| Mangrove | 25 | 8 | stone | 0.8% | 125 | mangrove swamps |
| Pale oak | 35 | 10 | iron | 1.2% | 175 | pale gardens (2×2) |
| Crimson stem | 40 | 10 | iron | 1.5% | 200 | crimson forests (Nether) |
| Warped stem | 40 | 10 | iron | 1.5% | 200 | warped forests (Nether) |

- **"Axe"** is the lowest axe that pays in full: any (wood or gold), stone (or copper), iron. Vanilla tiers as Mining uses them. A weaker axe pays a quarter.
- **XP pace:** a beginner cutting oak and birch by hand-picked trees earns about 6,000 XP an hour, like a beginner miner (`GATHERING.md`). Level 10 takes about 3 hours, and Felling makes the next stretch faster.
- **Only logs (and Nether stems).** Stripped logs, wood blocks and planks are things players make, so they pay nothing.

## 5. Natural and placed wood

- **A log pays unless it was placed, moved or grown since this update,** the same rule as ore (`GATHERING.md`). The placed-block memory marks every log a player places or a piston moves, and every log a sapling grows into:
  - saplings, fungi, propagules and azaleas that a player plants or bone-meals grow trees that give wood, but no XP, no Felling, no heartwood and no discovery;
  - so no amount of replanting pays.
- **Felling also needs a tree the world grew.** Its connected logs of that wood must touch at least two of the tree's own **natural leaves** (by a side, an edge or a corner, as the logs themselves connect, so a mushroom-like cap sitting on its stem counts): leaves the world grew, which vanilla marks as decaying. Leaves a player placed never count, and Nether stems count their wart blocks. Looking up to 7 blocks sideways, 32 up and 1 down from the cut, a felling never takes a placed log or goes past one.
- **Old builds.** A log built into a house before this update is unmarked, so cutting it by hand pays once, as old ore blocks do; it can't be repeated, because a log placed again is remembered. Felling never takes such a build:
  - a build has no natural leaves of its own;
  - a natural tree that a build touches is left standing. When planks, stairs, slabs, a fence or wall, a door or trapdoor, glass, a bed, wool or carpet, a sign or banner, a torch or lantern, bricks, concrete, a ladder, a crafting table, chest, barrel, furnace or bookshelf touches any of its logs, the HUD says so and the tree is cut log by log. An old log house or a village built against a tree never comes down with it. Trees the world grows never touch these;
  - a tree whose lowest layer of logs is wider than a trunk is left standing too. A trunk is 1 or 2 logs wide (3 for the Nether's thick fungi: `trunk:` per wood), and a log fence, wall or palisade built against it, even of logs alone, widens its base. Two trees grown into each other are left standing for the same reason, and are cut by hand.
- **What can still come down:** a structure made only of logs of the tree's own wood, attached to the tree above its base, with no other building block touching any of its logs, and only on land no protection plugin claims. Claims stop every felling at their edge.
- **Why not demand leaves for every log:** a trunk whose crown was cut first, or whose leaves already decayed, would stop paying halfway down. One check per felling costs little; one per log would cost more and frustrate players.

## 6. Techniques (milestones, data)

| Level | Technique | What changes |
|---|---|---|
| 10 | **Felling I** | Sneak while cutting a natural tree's log: the rest of the tree, up to 6 logs, comes down with it. Small oaks and birches in one cut. |
| 25 | **Felling II** | Up to 24 logs: most single-trunk trees. |
| 50 | **Felling III** | Up to 64 logs, and giant 2×2 trunks: dark oak, pale oak, giant spruce and jungle trees. |
| 75 | **Canopy** | Felling brings the tree's natural leaves down too, with their own drops (saplings, sticks, apples). |
| 100 | **Felling IV** | Up to 128 logs, and the rest between fellings is halved. |

**How Felling plays:**
- **Intent:** only while Sneaking, with an axe that fits the wood. Without Sneak, an axe cuts one log as always.
- **Rest:** after a felling, the next one waits 0.3 s per log it took (a 20-log tree: 6 s; a felling a claim or the axe stopped early rests only for what came down). Single logs can be cut meanwhile, and a relog doesn't end the rest. The HUD says when it is ready.
- **The tree comes down in steps,** 8 logs a tick from the cut upward: a 64-log tree takes about 0.4 s. It stops if the axe leaves the player's hand or the player leaves.
- **The axe pays:** one point of durability per log (Unbreaking counts). A felling stops when the axe would break.
- **The wood lands at the stump,** merged into a few stacks rather than one item per log.
- **XP, Fortune and heartwood** count for every log felled, exactly as if each was cut by hand.
- **Too big:** a tree with more logs than the player's Felling can take isn't felled; the cut takes one log and the HUD says how many the tree has.

## 7. What Woodcutting gives (attribute engine, `attributes.yml`)

| | Per level | At 100 | Cap |
|---|---|---|---|
| **Vitality** | +0.25 | +25 | the engine's |
| **Fortune (wood)** | +0.5 | +50: a 25% chance of one extra log | 50% chance |
| **Chopping speed** (an axe in hand) | +0.2 points | about +14% (measured: x1.138) | 25% |

- **Fortune is scoped by resource.** Woodcutting's counts on wood, Mining's on ore (same numbers as before), and neither gets the other's. Unscoped Fortune (luck skills, later talismans) counts on everything.
- **Chopping speed** is its own stat: with an axe in hand it replaces gathering speed (Mining's), with anything else gathering speed applies as before. Like every speed stat, its points add up and the real speed approaches the cap with diminishing returns (`docs/ATTRIBUTES_ENGINE.md`): 20 points at level 100 give about +14%.
- **Forged axes** (Smithing quality) add Fortune for wood; forged pickaxes Fortune for ore.

## 8. Heartwood

- **A rare drop** from natural logs, by wood (the table above) and level: the wood's chance × (1 + Woodcutting level / 50), so ×3 at level 100. Fortune doesn't apply to it.
- **Two upgrades, one per item,** made in a crafting grid (the item and one heartwood, shapeless; the item keeps its enchantments, damage, quality and name):
  - **Heartwood grip** on any axe: +8 points of chopping speed while it is in hand, about +7% for a beginner (measured: x1.07) and +3% at Woodcutting 100, where the 25% cap is near;
  - **Heartwood limbs** on a bow: +8 points of draw speed while it is in hand (the bow's own cap is 50%).
- Both go through the attribute engine and its caps (and the PvP pause, like every speed stat). The item's lore says what it has.
- **Only a player at a crafting table** makes an upgrade. A crafter block never does, and never crafts with heartwood; a furnace never smelts it into charcoal (it stays in the furnace).

## 9. Discoveries

- The first natural log of each wood a player cuts is a **discovery**: the bonus XP in the table above, a line in chat and on the HUD, and a soft chime. Once per wood per player, saved on the player (no database).
- The Woodcutting page (Player Menu → Skills → Utility → Woodcutting) lists the woods discovered and the ones still to find, with the level and axe each asks for.

## 10. Protecting the world

| Risk | What stops it |
|---|---|
| Placing and breaking logs | Placed logs are remembered and pay nothing (as ore). |
| Tree farms (saplings, bone meal, fungi, propagules) | Logs a sapling grows are remembered as grown: wood drops, nothing else. |
| Old log builds and villages (logs nothing remembers as placed) | Cut by hand they pay once, like old ore. Felling never takes them: they have no natural leaves, and a natural tree they touch is left standing (§5). |
| Felling a build | Felling takes only logs of the cut log's wood that belong to its natural tree, never a placed log and never past one, within 7 blocks sideways, 32 up and 1 down of the cut. A tree a build touches, or whose base is wider than a trunk, isn't felled at all (§5). |
| Machines | A crafter never makes an upgrade or uses heartwood; a furnace never smelts heartwood. An upgrade's recipe on its own gives nothing back. |
| Protected areas | Each felled log is offered to protection plugins as the player's own break; when one refuses a log, the felling stops there. |
| Chunk and region edges | A felling reads and changes only loaded blocks the current region thread owns (Folia), checked again at every step; past that the tree is cut by hand. |
| Lag | The felling runs in steps of 8 logs a tick, so other plugins' handlers never run for a whole tree in one tick. |
| Player intent | Felling only while Sneaking, with a fitting axe, on a natural tree. |
| Fortune duplicating wood | At most one extra log per log, never from placed or grown logs; a placed extra log pays nothing. |
| Creative mode | Nothing (no XP, no Felling bonus, no heartwood, no discovery). |

## 11. Data and extension points

```yaml
# resources.yml: a wood is a resource with a tree
resources:
  oak:
    skill: Woodcutting
    blocks: [OAK_LOG]
    tool: axe
    level: 1
    xp: 4
    fortune: true            # which Fortune counts: true (the skill's own: wood here), ore, wood, or general
    tree:
      leaves: [OAK_LEAVES, AZALEA_LEAVES, FLOWERING_AZALEA_LEAVES]
      heartwood: 0.002       # chance per natural log at level 0
      discovery-xp: 20
      trunk: 2               # how wide a trunk is at its base (2 unless written; the Nether stems say 3)

woodcutting:
  felling: { at: { 10: 6, 25: 24, 50: 64, 100: 128 }, giant-trunks-at: 50, rest-per-log: 0.3, rest-halved-at: 100 }
  canopy-at: 75
  reach: { sideways: 7, up: 32, down: 1 }
  min-natural-leaves: 2
  weak-axe: 0.25             # what too weak an axe pays
  heartwood: { item: heartwood, level-factor: 50 }

upgrades:                    # "for:", not "on:" (YAML reads a bare on as true)
  heartwood_grip:  { item: heartwood, for: [axe], stat: chop-speed, amount: 0.08, name: Heartwood grip }
  heartwood_limbs: { item: heartwood, for: [bow], stat: draw-speed, amount: 0.08, name: Heartwood limbs }
```

- **A new wood** is one entry: its logs, its leaves, its numbers. A custom tree from another plugin or a datapack is the same.
- **The synthetic custom tree** (a test config only): a huge red mushroom as a "tree" (mushroom stems as logs, mushroom blocks as leaves), with its own XP, a 100% heartwood chance and a discovery. In game, with no code change: XP per stem, Felling, heartwood and the discovery work.

## 12. Performance

- **One tree check per felling,** only when a sneaking cut asks for one: a walk from the cut over at most the logs Felling may take plus one, reading each log's neighbours once and counting natural leaves up to 16. A cut without Sneak walks nothing.
- **Felling in steps:** 8 logs a tick, at most 128 logs; each log is one protection event and one block change. The wood is merged into a few item stacks, so a felling makes fewer item entities than cutting by hand. A few break effects, not one per log.
- **The canopy** looks at no more than 2,000 blocks and takes at most 400 leaves, 50 a tick, walking only through leaves about to decay (a neighbouring tree's crown is never walked).
- **Crafting grids:** the upgrade listener looks only at grids holding a custom item's material, so ordinary crafting never pays for the recipe lookup.
- **No database writes:** XP rides the coalesced skill saves; discoveries and upgrades live on the player and the item.
- **Measured before and after** (`docs/PERFORMANCE.md`): cutting wood costs what it did, and 9 players felling non-stop (about 5 trees a second) cost 0.05–0.08 ms per tick, about what cutting the same logs by hand costs (`load.js wood`, `felling`).

## 13. Admin and test tools

- `/cerb gather tree`: the tree you look at: its wood, natural or not and why, logs, natural leaves, trunk size, whether a build touches it, whether you can fell it now, and the XP and heartwood chance.
- `/cerb gather info` shows Fortune (ore) and Fortune (wood).
- `tools/playtest/woodcutting.js`: phases for natural and placed logs, builds, tree farms, tools, Felling and its limits, protection, Fortune, heartwood and upgrades, crafters and furnaces, discoveries, the synthetic tree and the canopy. The protection phase needs the sandbox-only test plugin in `tools/playtest/protect/`.

## 14. Sources studied (principles only)

- Hypixel SkyBlock: the official patch notes for 0.23, 0.23.3 and 0.27 and a later August 2026 change (hypixel.net threads 5921261, 5962379, 6132090, 6142558), and the community wiki's foraging pages.
- Old School RuneScape wiki: woodcutting, trees, and the 2023 woodcutting rework.
- Wynncraft wiki: woodcutting, gathering tools, professions.
- mcMMO source (its woodcutting and block listeners, configuration, two issue reports) and AuraSkills (its documentation and its tree-felling source).
