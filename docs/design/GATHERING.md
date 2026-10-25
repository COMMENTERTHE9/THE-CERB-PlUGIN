# Gathering → processing → equipment: the first complete loop

*Gameplay Depth & Systems Completion, item 3 (`docs/ROADMAP.md`). Built on the skill audit's findings (`docs/SKILL_AUDIT.md`): no gathering, crafting or furnace event is handled today, and the old yield and quality code is unsafe to wire. Status: being built; each part is marked as it lands in `docs/STATUS.md`.*

## The loop

Find stone and ore → mine it (**Mining XP**) → smelt it (**Smithing XP**) → forge tools, weapons and armor (**Smithing XP**, a quality roll) → the quality feeds the attribute engine → a better pickaxe finds more ore, a better sword hits harder.

Vanilla resources first. Everything a later custom ore, tool, drill or machine needs is a definition in a config file, proven by a synthetic resource and tool that exist only in a test config.

## What a level changes

| Skill | Today | After |
|---|---|---|
| Mining | nothing | XP per natural block; **gathering speed** (+0.2% per level, toward a 25% cap); **Fortune** (+0.5 per level): a chance of one extra drop from ore |
| Smithing | nothing | XP for smelting metal and forging gear; a better **quality roll** on forged gear |
| Fortune (attribute) | display-only | the extra-ore chance (and later fishing, scavenging and loot) |

## Resources (`resources.yml`, shipped, refreshed when unedited like `abilities.yml`)

```yaml
resources:
  iron_ore:                  # stable id: saved data and other configs refer to it; never rename
    skill: Mining
    blocks: [IRON_ORE, DEEPSLATE_IRON_ORE]
    tool: pickaxe            # the tool kind that gathers it (vanilla tiers decide vanilla drops)
    level: 10                # the resource's level for the weak-resource rule
    xp: 8
    fortune: true            # the Fortune attribute can add one extra drop
```

- **Ids are stable.** Blocks, tools and drops refer to materials or to Cerberus item ids, never to display names.
- **Bad entries are refused one by one** and logged, like abilities: an unknown material, a negative XP, a duplicate block. A broken file changes nothing; the old definitions stay.
- **Stone and deepslate pay a little, ore pays most:** stone 1, deepslate 1, coal 4, copper 4, iron 8, gold 12, redstone 6, lapis 10, diamond 40, emerald 50, nether quartz 5, nether gold 4, ancient debris 100. Cobblestone pays nothing, because generators make it.
- **Weak resources pay a quarter,** like weak mobs: at 20 or more levels above a resource's level (stone is level 1, diamond 30). Progress means mining deeper.
- **Fractions carry over** per player, so a stone's 1 XP is never lost to rounding.
- **XP pace:** a strip miner breaking 2 blocks a second, about 1.5% of them ore, earns about 8,000 XP an hour at level 1. That is the same order as a fighter: a level-1 kill every 10 seconds is about 14,000. Level 10 then takes about 2 hours of mining (`SkillManager.xpForLevel`).

## Tools

- **Vanilla tools:** kind and tier from the material: a pickaxe, axe, shovel or hoe, and wood, stone, iron, gold, diamond or netherite. Vanilla still decides vanilla drops: an iron ore broken with a wooden pickaxe drops nothing and pays nothing.
- **Custom tools:** an item carrying a Cerberus tool id (`tools:` in `resources.yml`), with its own kind and tier. A resource can require a kind and tier vanilla doesn't know (a `drill`, tier 5).
- **Custom drops:** a resource can replace its drops with Cerberus items (`items:`), for custom ores.

## Extraction methods

| Method | XP | Fortune bonus |
|---|---|---|
| **Manual:** a player breaks it in survival or adventure | yes | yes |
| **Creative mode** | no | no |
| **Explosion, piston, fire, other plugins** | no | no |
| **Automated:** a future drill or machine, through the same definitions (`GatheringService.extract`) | no (configurable later) | configurable |

## What can't be farmed

| Way to farm | What stops it |
|---|---|
| Place and break | Placed resource blocks are remembered per chunk (chunk data, saved with the world), and breaking them pays nothing. |
| Silk Touch, then place again | Silk Touch on a natural ore pays XP and no bonus. The placed block is remembered. |
| Generators (lava + water make stone, basalt, obsidian) | Formed blocks are remembered as placed. |
| Pistons | A pushed or pulled resource block counts as placed at its new spot. |
| Fortune duplicating rewards | The bonus is at most one extra item per block, never multiplied by the Fortune enchantment, never on Silk Touch, and its chance is capped at 50%. |
| Creative mode | No XP and no bonus. |
| Craft and uncraft loops (ingots ↔ blocks, nuggets ↔ ingots) | Only forward recipes pay (gear); storage and reversible recipes pay nothing. |
| Furnace automation | Smelting XP is paid when a player takes the output (`FurnaceExtractEvent`); hoppers pay nobody. One ingot always needs one mined raw metal. |
| Invalid definitions | Refused entry by entry, with a log line. |
| Item duplication | The bonus changes the amount of an item vanilla drops; it never spawns extra entities or copies a block. |

## Smithing and crafting

- **Smelting:** XP when a player takes metal from a furnace or blast furnace: iron 2, gold 3, copper 1, netherite scrap 25 per item.
- **Forging:** XP for crafting metal and diamond gear (tools, weapons, armor), by material and size (an iron sword 10, an iron chestplate 30); for the netherite upgrade at a smithing table, 50. A shift-click pays for every item it made.
- **Quality:** forged gear rolls a quality when taken from the crafting grid: Common, Fine, Superior or Masterwork. Better Smithing moves the odds toward Masterwork, and a shift-click rolls once for the whole batch, so re-crafting can't fish for a roll. The quality is stored in the item (a Cerberus id, not its name) and shown in its lore.

## Equipment in the attribute engine (no second item-stat engine)

An **equipment source** for the existing engine reads the quality of the item in hand and the armor worn:

| Quality | Weapon in hand (Power, that weapon type) | Each armor piece (Fortitude) | Tool in hand (Fortune) |
|---|---|---|---|
| Fine | +3 | +1 | +3 |
| Superior | +6 | +2 | +6 |
| Masterwork | +10 | +4 | +10 |

- **It goes through the existing caps** (the 6× offense budget, 40% armor, 75% total) and the PvP rule (PvE only for now).
- **It never adds item attribute modifiers.** Those would enter the hit before Power, outside the caps (`PveOffense`).
- **The snapshot is rebuilt only when what's equipped changes its quality signature.** Scrolling between plain items rebuilds nothing.

## The real-time XP display (owner request)

- **Every skill's XP gain shows briefly on the HUD:** kills, hits taken, spells, new chunks, mining, smelting, forging.
- **Gains of the same skill within a few seconds merge** into one line ("+86 Mining"), never dozens of lines.
- **Ability and combat messages always come first.** The XP line uses a second, lower-priority slot that waits while they show.
- **A per-player setting:** Compact ("+86 Mining"), Detailed ("+86 Mining XP · 1,240 / 2,000 · Lv 12") or Off. It is stored on the player: no database write.
- **Kill XP's chat line moves to the HUD.** Level-ups stay in chat.

## Proving the extension points

A test-only config adds a custom resource (a block the shipped file doesn't use), a custom drop item and a custom drill tool of tier 5. With no code change:
- a pickaxe can't gather it (no XP, no custom drop, a clear message);
- the drill can (XP, the custom drop);
- an automated extraction call yields the drop and no XP.

Unit tests cover parsing and the rules; the sandbox covers the same in game.

## Performance

- Definitions are cached in enum maps.
- A block break costs one map lookup, plus a chunk-cache lookup for resource blocks.
- No database work per block: XP rides the existing coalesced skill saves, and placed blocks live in chunk data.
- Folia-safe: every check runs on the region thread that owns the block or player.
- **Measured before and after** with `load.js gather` and `load.js craft` (9 bots, 60 s, two runs and a warm-up, JFR), by the method in `docs/PERFORMANCE.md`.

## Built in order (each verified in game, then committed)

1. Baseline measurements (gather, craft).
2. Resources, tools and placed-block tracking; Mining XP, gathering speed and Fortune.
3. The XP display.
4. Smelting and forging XP; quality; the equipment source.
5. The synthetic resource and tool.
6. Exploit checks in game (every row of the table above, each with a positive control), performance after, an independent review, docs.
