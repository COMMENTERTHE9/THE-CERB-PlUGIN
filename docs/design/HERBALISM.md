# Herbalism: wild plants as reagents (design, 2026-10-28)

*Milestone "Combat Improvements, Replant Enchantment, Herbalism & Alchemy", increment 4 (the owner's spec, 2026-10-28). Built the way Farming was: the gathering pipeline, the placed-block memory, the attribute engine, the menu framework, the shipped-config rules. No second skill system. The old `Skills/HerbalismSkill` stays unwired (its `applyEffect` is the unsafe code the audit found).*

## 1. The loop

- **A herbalist** gathers wild plants (flowers, ferns, mushrooms, cave and Nether plants, sea plants) and earns Herbalism XP for each one gathered, by hand or with macros. Farming keeps the crops a player sows; Herbalism is the wild side: what grows on its own, or from bone meal on grass.
- **Each plant is a reagent** for Alchemy (increment 5): the herb item itself, at a quality from Herbalism 50.
- **Rare reagents** from Herbalism 25: a gathered herb sometimes yields one more, rare plant part (Bloodroot, Moonpetal, Sunpetal, Emberbloom) that Alchemy's strongest brews need.
- **The character:** Essence 0.25 per level (mind and spirit, as the owner set) and Fortune on herbs 0.5 per level (a chance of one more of the herb).
- **Wildcrafting** (the active technique, 10): Sneak and gather a plant with shears, and every wild plant of that kind around it is gathered too, in short bounded steps, each one offered to protection plugins.
- **The Herbarium**, the journal: every herb and rare reagent, found or not, where it grows, what it needs, through the skill page and Collections.

## 2. What pays (and what doesn't)

Rules the code keeps whatever the data says, the same as every gathering skill (*respect the player's time*: anything that really happened pays, by hand or with a macro):
- **Pays:** a herb gathered by a player in survival, when the break drops the herb itself (the game's drops, read at the drop event: what really happened; the drops are read and changed at HIGH, and the gather paid at MONITOR once no plugin has cancelled them, so a plugin that cancels the drops pays nothing). A plant that grew on its own pays in full; so does one that bone meal grew on grass (it cost bone meal, like a crop).
- **Pays nothing:**
  - a plant **placed by hand** (the placed-block memory keeps herb blocks, as it keeps melons and cane; a tall flower is judged by its lower half, so breaking the top of a placed sunflower pays nothing either). Bone meal that grows a placed plant where it stands (one more pink petal, a fern into a large fern, small dripleaf into big) or spreads it leaves what grows placed too: nothing wild came of it. A two-block plant is judged by its lower half here as well, wherever the bone meal lands (until the integration review, bone meal on a placed small dripleaf's top half grew a big dripleaf that paid). Bone meal on grass grows wild flowers;
  - a break that **drops nothing** of the herb: ferns, seagrass, glow lichen, hanging roots, small dripleaf and Nether sprouts drop themselves only to **shears**; the HUD says so (once a minute);
  - **creative** (the XP display says why), and **machines**: water, pistons and sheep keep vanilla's drops and pay nobody;
  - the plants a break carries away with it (the kelp above a cut segment, the second half of a tall flower): one gather is the block broken.
- **Weak plants:** each herb has a level; 20 levels above it a herb pays a quarter (the rule ore, wood and crops follow).

## 3. The herbs (data: `resources.yml herbalism.herbs`)

| Herb | Blocks | Level | XP | Discovery | Grows | Shears | Rare reagent (25+) |
|---|---|---|---|---|---|---|---|
| Dandelion | dandelion | 1 | 3 | 15 | plains, forests, meadows | | Sunpetal |
| Poppy | poppy | 1 | 3 | 15 | plains, forests | | Bloodroot |
| Oxeye Daisy | oxeye daisy | 1 | 3 | 15 | plains, meadows | | |
| Cornflower | cornflower | 1 | 3 | 15 | plains, meadows, flower forests | | |
| Azure Bluet | azure bluet | 1 | 3 | 15 | plains, flower forests | | Moonpetal |
| Fern | fern, large fern | 1 | 2 | 15 | taigas, jungles | yes | |
| Tulip | red, orange, white, pink tulip | 5 | 4 | 20 | plains, flower forests | | |
| Allium | allium | 5 | 4 | 20 | flower forests, meadows | | |
| Lily Pad | lily pad | 5 | 3 | 20 | swamps | | |
| Mushroom | red, brown mushroom | 10 | 4 | 25 | dark places: caves, swamps, dark forests | | |
| Blue Orchid | blue orchid | 10 | 5 | 25 | swamps | | |
| Lily of the Valley | lily of the valley | 10 | 5 | 25 | flower forests | | Moonpetal |
| Seagrass | seagrass, tall seagrass | 10 | 2 | 25 | oceans, rivers | yes | |
| Kelp | kelp, kelp plant | 10 | 2 | 25 | oceans | | |
| Sunflower | sunflower | 15 | 6 | 30 | sunflower plains | | Sunpetal |
| Lilac | lilac | 15 | 6 | 30 | forests, flower forests | | |
| Rose Bush | rose bush | 15 | 6 | 30 | forests, flower forests | | Bloodroot |
| Peony | peony | 15 | 6 | 30 | forests, flower forests | | |
| Pink Petals | pink petals | 15 | 4 | 30 | cherry groves | | |
| Wildflowers | wildflowers | 15 | 4 | 30 | birch forests, meadows | | |
| Glow Lichen | glow lichen | 20 | 4 | 40 | caves | yes | |
| Hanging Roots | hanging roots | 20 | 4 | 40 | lush caves | yes | |
| Small Dripleaf | small dripleaf | 20 | 5 | 40 | lush caves | yes | |
| Big Dripleaf | big dripleaf (its stem too) | 20 | 5 | 40 | lush caves | | |
| Cactus Flower | cactus flower | 20 | 6 | 40 | deserts, badlands | | Sunpetal |
| Firefly Bush | firefly bush | 20 | 5 | 40 | swamps, rivers, badlands | | |
| Spore Blossom | spore blossom | 25 | 10 | 60 | lush caves | | |
| Sea Pickle | sea pickle | 25 | 6 | 60 | warm oceans | | |
| Eyeblossom | open, closed eyeblossom | 25 | 8 | 60 | pale gardens | | Moonpetal |
| Crimson Fungus | crimson fungus, crimson roots | 30 | 6 | 80 | the Nether: crimson forests | | Emberbloom |
| Warped Fungus | warped fungus, warped roots | 30 | 6 | 80 | the Nether: warped forests | | |
| Nether Sprouts | nether sprouts | 30 | 4 | 80 | the Nether: warped forests | yes | |
| Nether Vines | weeping, twisting vines | 35 | 5 | 80 | the Nether | | Emberbloom |
| Wither Rose | wither rose | 40 | 12 | 100 | where the wither or its victims fell | | |
| Chorus Flower | chorus flower | 40 | 10 | 100 | the End | | |

**35 herbs.** Small and big dripleaf are two herbs, as are warped fungus and Nether sprouts: one herb's gathering rule (shears or not) has to hold for all of its blocks. A herb whose block breaks into something else names what it gives (`produce:`: tall seagrass gives seagrass, kelp's stem gives kelp, a vine's stem gives the vine). Every number, name, block, hint and rare reagent is data; a herb can be turned off (`<id>: off`) and new ones added the way crops are. A herb never claims a block that is already a resource (ore, wood) or a crop (`HerbalismRulesTest`).

## 4. What Herbalism grows

**Through the attribute engine** (`attributes.yml`, the one engine; caps, curves and the PvP policy as for every skill):
- **Essence 0.25 per level:** max mana, its regeneration, spell damage and spell cooldown (Herbalism 100: Essence +25).
- **Fortune on herbs 0.5 per level:** a new yield, **herbs**, like ore, wood, fish and crops: each gathered herb has a chance (the shared rule: 0.5% a point, at most 50%) of one more of itself. Never of a rare reagent.

**Techniques and milestones** (by level, data in `herbalism:`):
- **10, Wildcrafting** (Herbalism's Felling and Wide Harvest): **Sneak and gather a wild plant with shears:** every wild plant of the same herb within the reach is gathered too: 3×3 at 10, 5×5 at 40, 7×7 at 70, one block up or down.
  - **Bounded steps:** at most 16 plants a tick on the region thread that owns them; one at a time per player; the reach is worked out on that thread before it starts.
  - **Every plant, at its step:** still that herb and wild (never placed by hand), in a loaded chunk the thread owns, allowed by spawn protection and the world border, and **offered to protection plugins as the player's own break** (`Gathering.Herbalism.GatherBreak`, as Felling and Wide Harvest do); the sweep stops at the first refusal.
  - Each plant pays as if gathered by hand (XP, Fortune, quality, the rare roll); the drops land in one pile at the first plant.
  - A water plant leaves water behind, both halves of a tall one (tall seagrass, a waterlogged dripleaf): never an air pocket under water.
  - **One point of shears wear per plant** (Unbreaking and wear resistance apply); it stops before the shears would break, or when they leave the hand.
- **25, rare reagents:** each gathered herb that has one rolls for its rare reagent: 2% at 25, rising evenly to 6% at 100 (data). It drops beside the herb: a Cerberus item that can't be placed, burned, brewed by vanilla or used in vanilla recipes (Alchemy's own brews use it, increment 5). Fortune never multiplies it. The first find of each is a discovery.
  - **Bloodroot** (poppy, rose bush), **Moonpetal** (azure bluet, lily of the valley, eyeblossom), **Sunpetal** (dandelion, sunflower, cactus flower), **Emberbloom** (crimson fungus, Nether vines).
- **50, reagent quality:** gathered herbs (and rare reagents) roll a quality: Fine 10%, Superior 2% at 50, rising to Fine 22%, Superior 8%, Masterwork 3% at 100 (the crop odds). It is the produce quality crops already carry (`cerberus:crop_quality`, the same lore line): one rule for Alchemy and Cooking to read. Common stays plain.
- **75 and 100:** the reach and the odds keep rising (Wildcrafting's 7×7 at 70; the rare and quality odds to 100).

## 5. The Herbarium (journal)

- **The Herbalism skill page** gets two section tiles (`GUIs/player/SkillSections`, as Fishing and Farming have): **Herbarium: N of 35** and **Rare reagents: N of 4**.
- **Herbarium:** one tile per herb: found or not (an unfound herb shows its name and where it grows, so it is a goal, not a secret), its level, XP, whether it needs shears, the rare reagent it can give, and its discovery bonus. A header with the techniques at your level.
- **Rare reagents:** each with the herbs that give it, found or not, how many you've gathered.
- **Collections → Herbs** opens the same screens (`CollectionIndex`).
- **`/herbalism`:** your level, herbs found, techniques; `/herbalism herbs` opens the Herbarium.

## 6. Persistence and migration

- On the player (no database, as Farming's): herbs discovered (`cerberus:herbs_discovered`), rare reagents found with counts (`cerberus:rare_reagents`).
- Herbalism levels were already saved (it was one of the 35 skills, earning nothing); nobody loses anything.
- `resources.yml`: an unedited file is refreshed; an edited one gets the `herbalism:` section and the rare reagent items from the plugin's own file (logged; `herbalism: off` turns it off). `attributes.yml`: Herbalism's line comes the same way.

## 7. Performance

- **A gathered herb:** a lookup by material, the placed-block memory for that block, one roll each for Fortune, quality and the rare reagent from the cached attribute snapshot, and the XP payment: the cost of a Farming harvest. Nothing runs per tick while nobody gathers.
- **Wildcrafting:** 16 plants a tick at most, one sweep per player.
- Measured before and after with a `herb` load (`load.js`: nine bots gathering flowers in survival) and a `wildcraft` load, beside `menu`, `menuidle` and `idle`.

## 8. Testing

- Unit: the herb rules (blocks, levels, XP, weak plants, reach, rare odds, quality odds, the config's checks), the Yield, the attribute weights at 1/25/50/100.
- In game (`tools/playtest/herbalism.js`): every rule in §2 with a negative and a positive control (wild vs placed vs bone-mealed, a placed plant bone-mealed in place included, on a dripleaf's top half too (`bonemealtop`); a gather whose drops another plugin cancels (`nodrops`, with the sandbox's test plugin); shears vs hand; creative; a piston); Wildcrafting in water (both halves of each plant water again); discoveries once; Fortune's extra herb against the server's count; rare reagents from 25 (none at 24) and their guard (can't be placed or brewed by vanilla); quality from 50; Wildcrafting's reach, wear, its stop before the shears break, a protection plugin's refusal (the sandbox-only test plugin) and the control without it; the Herbarium and Collections against `/herbalism`; a relog.
