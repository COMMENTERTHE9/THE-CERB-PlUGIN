# Playtest checklist: Admin Control Panel 2.0, Attributes 2.0, decisions A–E, Player Menu 2.1, classes and ability controls, mining and forging, level-ups, Woodcutting, Fishing, your XP reports, Farming and Cooking, Active effects, combat improvements, Replant on the hoe, lifesteal, Herbalism, Alchemy

*2026-10-28, branch `claude/quirky-mccarthy-nhaz58`. Everything below was checked on the cloud sandbox server; this list is for your own world. **New this time: section 17, Active effects (about 20 minutes), section 18, Martial Expertise's Agility and Iron Reprisal (about 20 minutes), section 19, Replant on the hoe (about 10 minutes), section 20, lifesteal: nothing changed, one fight to judge it (about 10 minutes), section 21, Herbalism (about 20 minutes), and section 22, Alchemy (about 25 minutes).** You played section 16 (Farming and Cooking) on 2026-10-28; section 15 (Fishing equipment, with the bite cue, step 14b) and the sections before it are from earlier builds and still wait for your playtest where you haven't done them.*

**Before you start.** Start with `run.bat`, which backs up the plugin data before deploying. Test as an operator. Skill levels you set are saved, so **note your levels first** (Player Menu → Skills) and set them back at the end. The simulator and dev buffs change nothing permanent, so prefer them. Never use `/reload`.

**Classes (section 6) are saved too.** A class opens by itself once your skills meet its entry and then stays learned (there is no "unlearn", by design). Ranks you set with `/cerb class` are kept; set them back with the same command (rank 1 is the lowest).

## 1. Admin Control Panel

| # | Do | Expected |
|---|---|---|
| 1 | `/cerb panel`, or right-click the **Admin Control Panel** ender chest | Home with 7 sections (Players, Combat Lab, Progression Lab, Attributes, My Test Character, Server & Plugin, Maintenance); the header shows the plugin build. |
| 2 | Players → you → Skills → any skill → **+10 levels**, then lower it | The level rises and chat confirms. Lowering asks first; **Cancel** keeps the level. |
| 3 | Combat Lab → spawn a zombie at level 50 | A zombie labelled `Lv50` appears. The Wither is listed but refused (it would wreck terrain). |
| 4 | Combat Lab → nearby mobs → that zombie → **HP 10%**, **Highlight**, **Remove** | The label drops to 10%, the mob glows, then it disappears. |
| 5 | Spawn 2–3 test mobs, then **Cleanup** | Asks to confirm, then removes only the test mobs you spawned: not world mobs, not another admin's. |
| 6 | Attributes → you | The same Power numbers as your Stats page. The rules tile lists the integrations, including **Defense attributes in PvP: off**, and the limits, including **Resist x armor together: max 75%**. |
| 7 | Attributes → Simulator → Blade Mastery 100 | Shows about +99 sword Power (+4.95 damage multiplier). Your real level does not change. |
| 8 | Attributes → Dev buffs → **+100 Vitality**, then **Clear all** | Max health roughly doubles (for example 102 → 202), then returns. Buffs end after 5 minutes on their own and are never saved. |
| 9 | Server & Plugin, then Maintenance → Reload | Folia and Java versions are shown. Reload asks to confirm, then reports success. |
| 10 | As a non-operator (a second account, or `deop YourName` in the server window), try `/cerb panel` | Refused. A panel left open refuses the next click and closes. `op YourName` afterwards. |
| 11 | Open `test-server\logs\latest.log` | Each panel change is logged as `[Admin] …`. |

## 2. Attributes

| # | Do | Expected |
|---|---|---|
| 1 | `/stats` (or Player Menu → Stats & Attributes) | Six attributes with their sources. A weapon tile (click it to cycle weapon types), Speed & handling, and Survivability. |
| 2 | `/cerb spawn zombie 50 noai`, then hit it with a netherite sword at Blade Mastery 1. Repeat at Blade Mastery 100 | About 20 swings at level 1, about 5 at level 100. Damage numbers float over the mob. |
| 3 | `/cerb combatdebug` (run it again to turn it off), then hit a mob at Blade Mastery 50 | `[Combat] Power(Swords, Power 50.0 -> x3.50)` plus crit and lifesteal steps. Weapon Mastery adds 0.25 Power per level to every weapon, so at Weapon Mastery 1 it reads `Power 50.3 -> x3.51`. |
| 4 | Survival, Heavy Armor Training and First Aid at 100 | Stats → Survivability: max health about 300, armor bonus about +15%, a bigger defense bar, fire, cold and drowning resistance 50%. |
| 5 | Intelligence and Mana Regeneration at 100 | Max mana about 3,000, regeneration about 150/s. Fireball does about ×5 damage to mobs. |
| 6 | Hold a sword at Blade Mastery 1, then 100 | Speed & handling: about 1.61 → 1.88 attacks per second. The attack indicator refills visibly faster. |
| 7 | A bow at Ranged Precision 100 | A release after about 0.8 s already fires a full-power critical arrow. |
| 8 | Pathfinding (Navigation) at 100 | You walk and sprint about 9% faster. |
| 9 | Kill a Common zombie at level 1, then at level 50 | `+40` XP, then `+334`. A mob 20+ levels below your weapon skill gives reduced XP, with a message saying so. |

## 3. The changes from your 2026-10-25 decisions

| # | Do | Expected |
|---|---|---|
| 1 | **75% cap.** Survival mode, full netherite, Survival / Heavy Armor Training / Defensive Magic at 100, `/cerb combatdebug`, then `/damage YourName 100 minecraft:in_fire` | `ElemResist(FIRE 50%): 50.0 → Armor(40% reduction): 30.0 → DefenseBar(70% absorb, -21.0): 9.0`. There is no `ReductionCap` step: today's strongest defense (70%) is under the cap, so normal play does not change. Survivability says resistance and armor together block at most 75%. |
| 2 | **PvP as before** (needs a second player) | Survivability says *PvP: these bonuses don't count yet*. With combat debug on, a hit from a player shows `PvP(as before attributes)` and, if your max health is above 100, `HealthShare(x…)`. You lose the same share of health per hit as before Attributes 2.0, and the same goes for burning or poison during the fight and for lifesteal. Speed bonuses still pause for 15 s after PvP. |
| 3 | **Weapon Mastery.** At Blade Mastery 100, set Weapon Mastery 1 and then 100, and hit a level-100 zombie | Power 100 → 125 (×6.0 → ×7.25 in the trace): up to one swing fewer (about 7–7.7 → 6.7–7). An axe at Weapon Mastery 100 alone also shows ×7.25. |

## 4. Decisions A–E (Step 2)

| # | Do | Expected |
|---|---|---|
| 1 | **Shelves.** Put items on one to three shelves side by side, power them (a redstone block behind), hold anything but the head, right-click a shelf | Your hotbar swaps with the shelves (one shelf: slots 7–9; three: the whole row), except the Player Menu head in slot 9 and, if it's in your hotbar, the Admin Control Panel chest: they stay put, and the shelf items meant for their slots stay on the shelf. No message, nothing dropped. |
| 2 | **Fire.** Survival, Heavy Armor Training and Defensive Magic at 100, `/cerb combatdebug`, survival mode. Stand in fire, then keep burning after stepping out; stand on a magma block (not sneaking) and on a lit campfire; touch lava for a moment | The trace starts with `ElemResist(FIRE 50%)`, `ElemResist(FIRE_TICK 50%)`, `(HOT_FLOOR 50%)`, `(CAMPFIRE 50%)` and, for lava, `(LAVA 25%)`. Damage still gets through: there is no immunity. |
| 3 | **Arrows train defense.** Survival mode, Survival at a low level, read its XP with `/cerb skill YourName Survival xp 0` (it adds nothing). Let a skeleton hit you a few times, then read it again | Survival gains 1 XP per arrow that hurts you; with an iron, diamond or netherite piece on, Heavy Armor Training gains XP too. Arrows from a dispenser give nothing. |
| 4 | **Spears.** Kill a zombie with a spear. A spear jabs targets 2 to 4.5 blocks away, so stand back a little | Chat says `+N Weapon Mastery XP`, and nothing else for that kill (no Heavy Armor Training XP, even in heavy armor). With `/cerb combatdebug`, hits show `Power(Spears, …)`. |
| 5 | **Speed in PvP** (needs a second player) | As before: after a player hits you, your attack and movement speed bonuses pause for 15 seconds. |
| 6 | **Admin double-click.** Players → you → Skills, then double-click a skill quickly | The skill editor opens; the level does not change. Only a click on a button in the editor changes it. |

## 5. Player Menu 2.1 (Step 3)

| # | Do | Expected |
|---|---|---|
| 1 | Right-click the head | The frame appears and the tiles come in from the middle outwards in a blink (0.2 s), with a page-turn sound. You can click a tile straight away. |
| 2 | Skills, then Back, then Home | Each move to another screen: a band of light sweeps across the top row. Going in plays a higher page turn, going back a lower one. Refresh just clicks. |
| 3 | Skills → Combat | Combat glows in the middle of the top row, with Magic and Utility as tabs on either side; click one to switch. Back on Skills, Combat says "◆ Last opened". |
| 4 | Open a skill with some XP into its level | The bar under the title fills up from the left as the page appears. |
| 5 | Close the menu, kill a few mobs (or `/cerb skill YourName Blade_Mastery level <current + 2>`), open it again | The Skills tile shows a **2** in its corner and "▲ +2 levels since you last looked", with one soft chime. Skills → Combat shows the same on the category and on Blade Mastery. Opening Blade Mastery flashes its bar gold once, with a quiet level-up sound. Open it again: it just fills. |
| 6 | Leave the menu open for a while, then close it | Nothing flickers or keeps moving. Closing is silent. |

## 6. Classes & Abilities: the new controls (Phases 1–3)

Only Warrior, Mage and Duelist exist; the rest of the roster is design only. **This section matters most:** the sandbox checks prove the keys reach the right ability, but only you can say whether they feel right. Nothing here is called "usable" until you have played it. Details: `docs/design/ABILITY_CONTROLS.md`, results per ability: `docs/ABILITY_AUDIT.md`.

**Setup** (skip what you already have): `/cerb skill YourName Blade_Mastery level 10`, `… Critical_Strike level 5`, `… Intelligence level 5`, then `/cerb class YourName warrior rank 12`, `… mage rank 12`, `… duelist rank 12`. `/cerb spawn zombie 1 noai` puts a zombie that stands still where you stand.

**Where things show.** Cast messages appear in the **middle of the HUD bar** for about 1.5 s; health and Essence stay visible around them. Nothing goes to chat. In a fight, or for 5 s after a key press, a **boss bar** at the top lists your keys and what is ready.

### 6a. Quick keys (the default)

| # | Do | Expected |
|---|---|---|
| 1 | Player Menu → **Classes & Abilities**. Look at the **Controls** tile. | "Quick keys", your five keys with their abilities, and the bar setting. |
| 2 | Loadout 1: Cleave (A1), War Cry (A2), Charge (A3), Execute (A4), Blink (A5). "Use this loadout". | Each pick says where it went. |
| 3 | Sword in hand, three zombies close in front. **Right-click.** | `✦ Cleave · 3 hit`. Right-click again at once: `Cleave 3.8 s` and a soft tick. |
| 4 | **Sneak + right-click** at a zombie straight after the Cleave. | `✦ Execute · x2.2 · 3 Momentum`, a heavier impact. Again with no Momentum: `Execute needs 1 Momentum`, nothing spent. *(New: Execute is a finisher, so it no longer wastes its cooldown on a plain hit.)* |
| 5 | **F**. | `✦ War Cry · +15% damage · 8 s` and a ring around you. **The sword stays in your hand.** |
| 6 | Look at open ground. **Q**, then **Sneak + F**. | Charge dashes you forward (the sword flickers for a moment, but is never dropped). Blink right after it waits for the movement lockout, 1.2 s at most (`Blink 0.9 s`). Then Sneak + F blinks you where you look: up to 6 blocks ahead, or onto the spot on the ground you look at. |
| 6a | *(Your movement rule.)* Press **Q** at a zombie, then Q again and again. Then hold Sneak and press F repeatedly. | The first Charge stuns and strikes; the next ones, every 1.2 s, only move you and say `strike in 8.2 s` until the strike is back (every 10 s). Blink goes off about every 0.4 s, 20 Essence each, until the Essence runs out. Do they feel fast enough? |
| 7 | Swap the sword for a **pickaxe, a block or food**. Press R, F, Q. | All vanilla: you mine, place, eat, swap and drop as usual. |
| 8 | Sword in hand: right-click a **door, a chest, a villager, a horse**. | They open, trade or mount as usual. With Sneak, right-click casts instead (vanilla also skips them while sneaking). |
| 8b | A **shield in the off hand**, then right-click; also try bread or a torch there. With an axe, right-click a log. | The shield blocks, the bread is eaten, the torch is placed: the off hand's own use wins, and the right-click abilities show dark on the bar. F and Q still cast. The axe strips the log. *(Your call: should shield fighters lose right-click abilities, or the shield's block?)* |
| 9 | Empty a slot (A2 → none). Press **F**. | `Nothing on F · set it in Classes & Abilities`. No swap. |

### 6b. The Mage and the focus (your earlier report)

| # | Do | Expected |
|---|---|---|
| 10 | Loadout 2: Arcane Bolt (A1), Frost Nova (A2), Fireball (A3), Blink (A4). **Blaze rod** in hand. | A blaze rod, breeze rod or stick is a focus. |
| 11 | Right-click at a zombie 6 blocks away. | `✦ Arcane Bolt`, a purple beam to it, 15 Essence spent. Aim half a block beside it: still hits (aim assist). |
| 12 | Aim well beside it, at one 10 blocks away, or behind a wall. | `Arcane Bolt: no target within 8 blocks`, a whiff. **No Essence and no cooldown spent.** |
| 13 | **F** with zombies around you; **Q** at a zombie. | Frost Nova damages and slows those within 4 blocks; Fireball bursts on the first zombie (20 Essence). |
| 14 | Spend your Essence (`/cerb mana YourName 0`), then right-click. | `Arcane Bolt needs 15 Essence`, a fizzle. |
| 15 | A sword instead of the rod. Right-click. | `Arcane Bolt needs a focus (blaze rod, breeze rod or stick)`, a low note. |
| 16 | `/givetestitem fireball` (the Fireball Wand) with Arcane Bolt on right-click. | The wand now casts **Arcane Bolt**; before, it always cast its own Fireball. When no right-click ability fits a focus (say Cleave on A1, nothing on A4), it casts its Fireball as before. |

### 6c. Click combos (the option, Wynncraft-like)

| # | Do | Expected |
|---|---|---|
| 17 | Click the **Controls** tile (or `/class controls combo`). | "Click combos": R R R, R L R, R L L, R R L for actives 1–4, F for 5, and Q is a normal drop. |
| 18 | With loadout 1: **R R R** (or hold right-click) at zombies; **R R L** right after it. | Cleave, then Execute (active 4). The HUD shows the clicks so far and what the next one casts. The left-clicks inside a combo don't hit. |
| 19 | One right-click and wait 1 s. | Nothing is cast; the combo expires. |
| 20 | Switch back: click the tile again (or `/class controls quick`). Right-click the tile to cycle the bar: auto, on, off. | Your choice is saved; it is still there after a rejoin. |

### 6d. Duelist, mixing, saving, players

| # | Do | Expected |
|---|---|---|
| 21 | Loadout 3: Riposte (A1), Flurry (A2), Feint (A3), Lunge (A4), Precision (P1). Right-click just before a zombie hits you. | No damage, it is struck back (`✦ Riposte! · countered`) and Riposte is ready again in 1.5 s. |
| 22 | **F** facing a zombie; then **Q**, then a normal hit. | Flurry: three rising sweeps. Feint: `next hit crits`, and the hit is a critical one. |
| 23 | Mix: Cleave (Warrior), Feint (Duelist), Blink (Mage) in one loadout. | All three work. |
| 24 | Hit a zombie, then try to switch loadouts at once. | `Not during a fight: wait until 8 s after the last hit.` Cooldowns follow you into the other loadout. |
| 25 | Cast War Cry, rejoin within 30 s, `/class`. | Loadouts, controls and War Cry's cooldown as you left them. |
| 26 | Another player in front of you while you Cleave or Fireball; aim Execute at them. | Abilities never hit players (PvP for abilities is off for now). |
| 27 | War Cry (F), then kill a cow with normal hits; `/class`. Then Cleave a zombie until it dies; `/class`. | **No class XP from the cow**, from casts or kills. The zombie pays 6 per Cleave and a share of its kill XP. Zombies 20 or more levels below your class's skills pay a quarter. |
| 28 | Trap a zombie (or stand in a corner and let one hit you) and keep casting at it without killing it; watch `/class`. | Class XP stops after about 40 (a level-1 zombie: what its kill pays). Killing it still pays the usual share. |
| 29 | Find a horse and a pig (your report: no health bar). Ride the horse (tame it first), get off; saddle the pig and ride it. | Both show the `Lv… Horse  …❤` bar like a cow. The bar steps aside while you ride and is back when you get off. |

**What only you can judge, please tell me:**
- Movement: are Blink (0.4 s), Charge and Lunge (1.2 s) quick enough? Should Charge and Lunge strike on every cast instead of every 10 s and 7 s (delete the two `strike-cooldown:` lines in `abilities.yml`)?
- Which scheme feels better in a real fight: quick keys or click combos? Should the default change?
- Is 1.5 s right for the messages? Does the boss bar help or get in the way?
- Can you tell the refusal sounds apart (cooldown tick, Essence fizzle, low note, whiff)?
- Do the three classes feel different, and does each ability feel worth pressing?

## 7. Mining, smelting, forging and the XP display

Use a survival world spot with stone and ore (or make some with `/fill`: blocks made by commands count as natural). Note your Mining and Smithing levels first; set them back at the end.

| # | Do | Expected |
|---|---|---|
| 1 | In survival, mine stone, coal, iron and a diamond ore with an iron pickaxe. | The HUD's middle shows `+1 Mining`, then the gains adding up into one line (`+53 Mining`) for a few seconds. Stone 1, coal 4, iron 8, diamond 40. No chat lines. |
| 2 | `/xpdisplay detailed`, mine an ore; then `/xpdisplay off`, mine one; then `/xpdisplay compact`. | Detailed: `+8 Mining XP · 120/115... · Lv 2`. Off: no XP line (level-ups still in chat). Your choice is still there after a rejoin. |
| 3 | Place a stone or an ore and break it again. Make stone with lava and water and break it. | No XP for either. |
| 4 | Mine an ore with a wooden pickaxe where vanilla drops nothing (iron ore). | No drop and no XP. |
| 5 | `/cerb skill YourName Mining level 100`, `/stats` → Speed & handling. Mine stone. | Gathering speed +13.8%: stone breaks a little faster. Mining's page shows Fortune +50 and an extra-ore chance of 25%. |
| 6 | Mine 20 iron ores at Mining 100. | About one ore in four drops 2 raw iron instead of 1, never more than 2 (a soft chime when it happens). With Silk Touch: never. |
| 7 | Smelt raw iron in a furnace and take the ingots. | `+2 Smithing` per ingot. A hopper taking them out pays nobody. |
| 8 | With iron you smelted (step 7), craft an iron sword, then shift-click a batch of iron pickaxes. | `+10 Smithing` for the sword; `+15` for each pickaxe. Each item's lore shows a quality (Common, Fine, Superior or Masterwork); a shift-click batch shares one. `/cerb gather info` shows how much smelted iron is left. |
| 8b | Craft a sword from ingots you didn't smelt (`/give YourName iron_ingot 2`, with no smelted iron left). | A plain sword: no XP, no quality, and a hint that Smithing pays for metal you smelted yourself. **Note:** this includes ingots you smelted before this update (only ingots taken out of a furnace from now on count), so an old stockpile forges plain gear. |
| 9 | Hold a Fine or better sword; `/stats` → the weapon tile. Put on a Fine or better armor piece. | Power for swords +3 (Fine), +6, +10; Fortitude +1, +2, +4 per piece. `/cerb gather quality masterwork` sets the held item's quality for a quick test. |
| 10 | 9 iron ingots into an iron block and back; repair two worn swords in the crafting grid. | No Smithing XP for any of these. |
| 11 | Upgrade a diamond sword with a quality at a smithing table; then upgrade another with a shift-click. | `+50 Smithing` each time; the netherite sword keeps its quality. With a full inventory the shift-click does nothing and pays nothing. |
| 12 | Kill a zombie, then use an ability that is on cooldown right after. | Kill XP shows on the HUD (`+40 Blade Mastery`), not in chat. The cooldown message covers it, and the XP line comes back afterwards. |

**What only you can judge, please tell me:**
- Does mining feel rewarding at level 1, and does the extra ore feel noticeable but not silly at Mining 100?
- Are the XP numbers right (strip mining about 8,000 Mining XP an hour at level 1)?
- Is the XP line easy to read without being in the way? Compact or Detailed by default?
- Do the quality bonuses feel worth forging for?

## 8. Mob labels (the ghost labels you found)

| # | Do | Expected |
|---|---|---|
| 1 | Find wild horses (or llamas) and watch them for a minute. | Each keeps its "Lv Horse" label; they graze and wander instead of galloping about, and no label is left floating behind. |
| 2 | Kill a horse, a pig and a zombie. | Each label disappears with its mob. |
| 3 | Mount an untamed horse with an empty hand, a few times. | It still bucks you off now and then, and tames as usual. Its label steps aside while you ride and comes back when you get off. |
| 4 | Feed two tamed horses golden carrots. | They breed; all three wear labels afterwards. |
| 5 | (Admin) `/cerb labels 48` | "orphans 0". `/cerb labels 48 clean` removes any stray, for example ones left from before this update (they also vanish at restart). |

## 9. XP that can't be farmed (the live exploits)

The XP display (`/xpdisplay compact`) shows every gain, so each row is quick to see.

| # | Do | Expected |
|---|---|---|
| 1 | As a Mage (or with the admin Fireball Wand, `/givetestitem fireball`), cast into the sky a few times. Then cast at a mob, and at a group of mobs. | The sky pays nothing. A cast that hits pays Spell Weaving +15, Intelligence +5, and Elemental Mastery +10 (Arcane Bolt: Arcane Knowledge +10), once per cast however many it hits. |
| 2 | Kill the zombies a monster spawner makes (a dungeon, or a spawner farm). | Each pays a quarter of a wild zombie's XP. |
| 3 | Breed two cows and kill the calf; then kill a wild cow. | The calf pays nothing; the wild cow pays as usual. |
| 4 | Name a zombie "Legendary Bob" with a name tag and kill it. | It pays as a zombie (it used to pay four times as much). |
| 5 | Kill a mob in creative mode. | No XP. |
| 6 | Shoot a mob with a bow and switch to a sword before the arrow lands. Throw a trident at a mob. | The arrow's kill trains Ranged Precision, the trident's Weapon Mastery (they used to train Blade Mastery and Martial Expertise). |
| 7 | Punch one zombie over and over without killing it. | Critical Strike stops rising after a while (at what the zombie's kill would pay); a new zombie pays again, and the first one's kill still pays in full. |
| 8 | Stand where a mob can hit you and touch nothing for a minute. | Survival and Heavy Armor Mastery stop rising. Move or click and they train again. |
| 9 | (Two players) Hit each other; kill each other. | No skill XP from either, for now (PvP: `attributes.yml xp-integrity.pvp-xp`). |

## 10. Level-ups and class ranks

What a level-up looks and sounds like now (`docs/design/LEVEL_UP.md`). Admin commands change levels quietly, so set a skill just below a level with `/cerb skill <you> <skill> level <n>` and then earn the last bit by playing.

| # | Do | Expected |
|---|---|---|
| 1 | Kill a mob that gives you a level, in any skill. | A short chime and a small sparkle around you. For 3 s the middle of the HUD bar says which skill rose and what it gave ("▲ Blade Mastery 12 · +1 Power (swords)"); health and Essence stay where they are. One chat line has the full gain ("Power (swords) 11.8 → 12.8, Attack speed (swords) 3.4% → 3.6%"). |
| 2 | Reach level 10 of a skill (then 25 and 50 if you like) by playing. | A title ("Apprentice" over "Blade Mastery 10"; Journeyman, Expert, Master and Grandmaster later), a fanfare and a ring of particles. Bigger than a level, but short. |
| 3 | With Spell Weaving, Intelligence and Elemental Mastery each just below a level, hit a mob with a Fireball. | One sound and one HUD line naming all three; three chat lines. |
| 4 | Rank up a class by fighting: `/cerb class <you> warrior rank 2`, put Cleave on a key, and fight until rank 3. | Its own sound (a beacon tone and an enchanting sound), a ring in the Warrior's red, "✦ Warrior rank 3" and "New ability: Charge - equip it in /class" in chat, and a title "Warrior · Rank 3" over "New: Charge". A rank that opens nothing has no title. |
| 5 | Level up several times quickly (a strong mob at a low skill). | Every level gets its chat line; sounds and particles play at most every 1.5 s, never a storm. |
| 6 | `/levelup reduced`, then level something. `/levelup off`, then level something. `/levelup` alone. Rejoin. | Reduced: the HUD line, chat and a soft note, no particles or title. Off: chat only. `/levelup` alone cycles through the three. Your choice stays after a rejoin. |
| 7 | Use the admin tools: `/cerb skill <you> <skill> level 30`, the admin panel's skill editor, `/cerb class <you> warrior rank 9`. Then rejoin. | Only the admin's confirmation, no celebration. Rejoining replays nothing. |
| 8 | Overall feel. | Tell me if anything is too loud, too long, too often or too small. Volumes, particles and title times are easy to tune. |

## 11. Woodcutting

The new loop (`docs/design/WOODCUTTING.md`). The XP display (`/xpdisplay compact`) shows every gain. `/cerb skill <you> Woodcutting level <n>` sets a level quietly; `/cerb gather tree` (looking at a log) shows what Felling sees.

| # | Do | Expected |
|---|---|---|
| 1 | Cut an oak or birch log with any axe, in survival. Then cut one by hand. | +4 Woodcutting XP with the axe. By hand the log drops, with no XP, and the HUD says logs pay with an axe. The first log of each wood is also a **discovery**: a chat line, a chime and its bonus XP, once. |
| 2 | Find a harder wood (cherry, dark oak, mangrove, a pale garden's pale oak, a Nether stem) and cut it with a wooden axe, then with a better one. | A too-weak axe pays a quarter, and the HUD names the axe that pays in full (cherry, dark oak and mangrove: stone; pale oak and Nether stems: iron). |
| 3 | Place a log and cut it. | The wood drops, with no XP, and the HUD says logs placed by hand pay nothing (grown trees: §12). |
| 4 | At Woodcutting 10, **Sneak** while cutting the bottom log of a small oak or birch. | The whole tree comes down at once; its logs land at the stump in one or two stacks; the axe loses a point per log. Without Sneak, one log as always. |
| 5 | Fell another tree right after. | "Felling is ready again in N s": a short rest, longer after a bigger tree. |
| 6 | At 10 try a tall tree; at 25 the same; at 25 a dark oak (2x2), then at 50. | Too big for your level: one log, and the HUD gives the limit (6 logs at 10, 24 at 25). Giant trunks fall from 50. |
| 7 | Sneak + cut a log of one of your own log builds. Then put a plank or a torch against a tree's trunk and Sneak + cut that tree; then a short fence of logs against another tree's base. | One log only, each time: Felling brings down only trees with their own leaves, never a tree a build touches, and never one with logs built against its base (the HUD says which). Your old builds and villages stay standing. |
| 8 | At 75, fell a tree. | Half a second later its leaves come down too, with their saplings, sticks and apples. |
| 9 | Get a **heartwood** (rare from natural logs; for testing, `/cerb gather item heartwood`). Craft it with an axe, then with a bow. | The axe gets a **heartwood grip**: the Stats page's Chopping speed rises while you hold it. The bow gets **heartwood limbs** (draw speed). Quality, enchantments and name stay; a second grip on the same axe isn't offered. Heartwood can't be placed, burnt, smelted or made into planks, and a crafter block won't use it. |
| 10 | At Woodcutting 50 or more, watch how many logs you get. | Fortune for wood: about one extra log in eight at 50, one in four at 100. Mining's Fortune doesn't add logs, and Woodcutting's doesn't add ore. |
| 11 | The Player Menu → Skills → Utility → Woodcutting, and → Stats. | What a level gives (Vitality, Fortune on wood, chopping speed), what Felling takes at your level, the heartwood chance, and the woods you have discovered and the ones still to find. On the Stats page, Fortune by where it counts and Chopping speed. |
| 12 | Overall feel. | Too slow, too fast, Felling too strong, the rest too long, heartwood too rare? Every number is in `resources.yml` and `attributes.yml`. |

## 12. Your feedback: grown trees and Smithing's growth

The two changes from your Woodcutting playtest (`docs/STATUS.md` → "Done: grown trees and Smithing's growth"). **Plant new saplings for these:** a tree that grew before this build was recorded as placed and still pays nothing.

| # | Do | Expected |
|---|---|---|
| 1 | Cut a few logs of a wild tree with an axe (Woodcutting 15 or so; birch or oak). | 4 XP a log, as before. |
| 2 | Plant a sapling, let it grow on its own (or wait near it), and cut or fell it. | 80% of a wild tree's XP (3.2 a birch log; the XP display shows the whole points). Once every few minutes the HUD says "Trees grown from saplings pay 80% of a wild tree's Woodcutting XP". At Woodcutting 10, Sneak + cut fells it. |
| 3 | Plant a sapling, bone-meal it (by hand, or with a dispenser and a button), and cut or fell it. | Half (2 XP a birch log), no heartwood; the HUD says so now and then. Felling works. |
| 4 | Place a log and cut it with an axe. If a tree you grew before this build is still standing, cut it too. | No XP; the HUD says "Logs placed by hand pay no Woodcutting XP; trees grown from saplings do", and for the old tree "This tree grew before grown trees paid…". |
| 5 | Put a plank against a grown tree's trunk and Sneak + cut it. | One log only, as for a wild tree: Felling never takes a tree a build touches. |
| 6 | Player Menu → Skills → Utility → **Smithing**. | "Each level gives: +0.25 Fortitude, +0.3% wear resistance", what they give you now and at the next level (armor, defense bar, resistances, wear resistance), both live; Next levels still shows the forged-quality odds. No more "This skill gives no attribute points or speed". |
| 7 | Player Menu → Stats → Speed & handling. | A Wear resistance line (20% at Smithing 100). The Fortitude tile lists Smithing among its skills. |
| 8 | Level Smithing (smelt or forge something) and read the level-up line. | It names Fortitude and Wear resistance with their before → after values. |
| 9 | At a high Smithing level, use a pickaxe or sword for a while and watch its durability bar. | It wears more slowly: at Smithing 100 about one point in five is never taken (Unbreaking still counts first). During a PvP fight it wears as usual, like the other secondary stats. |
| 10 | Overall feel. | Too much, too little? `attributes.yml`: `Smithing:` under `skills` (Fortitude) and under `secondary.skills` (wear resistance), the cap under `secondary.stats.wear-resistance`; `resources.yml`: `woodcutting.grown` and `woodcutting.bone-meal`. |

## 13. Fishing

The third gathering loop (`docs/design/FISHING.md`), built on your decisions 1–3: real fights with sea creatures you bank and call when ready, and AFK fishing with full rewards. Fish in survival with any rod; the XP display (`/xpdisplay compact`) shows every gain. `/cerb skill <you> Fishing level <n>` sets a level quietly. Admin helpers: `/cerb fish info` (the water, odds and bite speed where you look), `/cerb fish lure <you> <creature> [n]` (bank lures), `/cerb fish force <you> species <id>|treasure [id]|lure [creature]|apex [creature]|clear` (the next catch), `/cerb fish journal <you>`, `/cerb fish stats`.

| # | Do | Expected |
|---|---|---|
| 1 | At Fishing 1, fish a pond or lake for a few minutes. | Every catch pays: junk 2, a vanilla fish 10, vanilla treasure 30. About a third of fish and junk bites are the water's own species at level 1 (Pond Bream in a lake; nearly two thirds at 100), with a weight on the HUD ("◆ Pond Bream 1.07 kg"). The first of each species is a **discovery** (a chat line, a chime, bonus XP, "1 of 31 species"); a heavier one later is a personal best. |
| 2 | Fish a river, a swamp, an ocean, a warm ocean, a frozen ocean, and a pool in a dark cave below sea level. `/fishing journal` (or `/fishing journal river`). | Each water has its own species (Silver Dace in rivers, Mud Loach in swamps, Sea Bass at sea, Reef Wrasse in warm seas, Ice Cod in cold seas, Blind Cavefish in caves from Fishing 5). The journal lists every species by water: caught ones with your best weight, missing ones with their level and when they bite (night, rain). |
| 3 | Fish at night and in rain (`/time set night`, `/weather rain`) at Fishing 30 or so. | Night species (Moonlit Carp in lakes, Brook Char in rivers) and rain bites start appearing; rain also makes bites come sooner, as in vanilla. |
| 4 | Set Fishing 5 and catch anything in a lake or river (other waters' creatures start at 10–35). | **Something Stirs:** the first catch from level 5 brings a sea creature's lure: a celebration, "Something stirs: River Lurker", "…took your bait and slipped away. Its lure is yours (1): Sneak and cast into water to call it and fight it." No mob appears. From then on, a catch now and then banks another (about 1 catch in 55 at level 50, 1 in 30 at 100). |
| 5 | **Sneak and cast** into the water with a lure banked. | The HUD says "Calling: River Lurker…"; on the bite the creature surfaces at the bobber with a splash and a title, at your Fishing level give or take 3 (never below its own level or the world's level there), with twice a normal mob's health. One lure is spent only now. `/fishing` shows "Fighting now". |
| 6 | Fight it with your class and abilities. Let a friend hit it, if one is online. | It fights only you; nobody else can hurt it. Defeating it pays your weapon's skill as any kill does, plus Fishing XP worked out the same way; the first defeat of each creature is a discovery. Its drops (Lurker Scale, cod…) are yours alone: another player standing on them can't pick them up. |
| 7 | Call another, then walk 50+ blocks away for half a minute. Call another and log out. Call another and die. | Each time the creature slips back into the water and its lure comes back (one lure, never two): "Its lure is yours again". A creature never stays in the world. Sneak + cast while one is out says "Finish your fight first", and that cast simply fishes at its usual pace (calling never makes fishing faster). |
| 8 | Bank encounters for two creatures (`/cerb fish lure <you> tidecaller 2`), open Player Menu → Skills → Utility → **Fishing**, click the **Encounters** tile. | (Since §15: "lures" are called **encounters**.) The Encounters screen lists every creature with the encounters you hold and which one is chosen (▶); a click chooses one. `/fishing call tidecaller` chooses too. Sneak + cast calls the chosen one. |
| 9 | Set Fishing 25 and fish open water (nothing but water and air within two blocks of the bobber: vanilla's own treasure rule). | Now and then **Cerberus treasure**: Sunken Coins, Sea Glass, Drowned Locket, Pearl of the Depths, each a discovery the first time. Fortune never doubles treasure. In a 1-block-wide AFK channel there is no treasure (vanilla's rule), but fish, species and lures come as usual. |
| 10 | (Since §15 rod upgrades are **parts of your fishing setup**, fitted in Fishing → Equipment; see §15 step 5.) Put a rod and a Lurker Scale in a crafting table. | Nothing is made, and chat says rod parts are fitted in Player Menu → Skills → Fishing → Equipment now. Creature drops and treasure can't be placed, burnt, or used in anvils and brewing stands. |
| 11 | Run your usual AFK fish farm for 10 minutes or more (a held right-click on a pressure plate or trapdoor setup, an auto-clicker, anything you'd normally use). | Full XP and catches the whole time, no "idle" or "AFK" message, no mob spawning on you. Lures are banked by the farm as by hand. With a full inventory the catch stays on the ground, its XP is paid and a lure is still banked. (A farm run while **holding Sneak** would call a creature with each cast: Sneak + cast is the call. Tell me if your setup needs Sneak held.) |
| 12 | At Fishing 50, call a creature. At 75 and 100, watch your catches (`/cerb fish force <you> apex` to see an apex). | From 50, called creatures come as **Elite** (half again the health and XP, one more roll of their drops). From 75, Fortune's extra fish can come twice. At 100 each water's apex can take the bait (very rare, 1 catch in 2,000): the Drowned Monarch in rivers, lakes, swamps and caves, the Leviathan at sea. |
| 13 | Fish in creative. | No Fishing XP, no species and no lure: nothing really happened. The HUD says so ("No Fishing XP in creative mode"). Survival pays again. |
| 14 | **Decision 10:** fight a high-level skeleton or blaze (far from spawn, or `/cerb spawn` a level-60 one) at your normal levels. | Its arrows and fireballs hit like its level now, as melee does (a level-1 skeleton is unchanged). One shot takes at most 35% of your maximum health and never less than vanilla's own damage. |
| 15 | Overall feel. | Too slow, too generous, lures too rare, fights too hard or too easy? Every number is data: `resources.yml` → `fishing:` (waters, species, creatures, techniques, XP), `upgrades:` and `items:`; `attributes.yml` → `Fishing:` under `skills` (Fortune) and under `secondary.skills` (lure speed, treasure chance, sea-creature chance). `fishing: off` in `resources.yml` turns all of it off. |

## 14. Your XP reports: Fishing XP and bow kills

What you saw (Fishing level 0, XP 0 / 115, nothing on the HUD; bow kills with no Ranged Precision) is exactly what **creative mode** gives: creative earns no skill XP, by the XP-integrity rule (unchanged). It used to give it silently; now the HUD says so. Four real defects were found on the way and fixed (`docs/STATUS.md` → "Done: your XP reports"). **Set your game mode first:** `/gamemode survival` (the HUD looks the same in both modes).

| # | Do | Expected |
|---|---|---|
| 1 | Join, then Player Menu → Skills → Utility. | **No skill shows level 0 any more.** Every skill that was at level 0 is level 1 now, with its XP kept (a "Fishing 0, 40 / 115" becomes "Fishing 1, 40 / 115"). The server log shows one line for you: "…N skill(s) stored at level 0 start at level 1 now (their XP kept)". Your Stats are slightly higher (a level in every skill); a brand-new character starts at health 102, Essence 1,035. PvP hits are exactly as before. |
| 2 | In **survival**, fish your superflat pond for a few minutes (`/xpdisplay compact`). | Every catch pays and the HUD shows it ("+10 Fishing"); about a third of fish and junk bites are Pond Bream from the first cast (at level 0 none could bite). The Fishing tile's XP moves with each catch. |
| 3 | Leave and rejoin, then restart the server and rejoin. | Fishing keeps the exact level and XP it had. |
| 4 | In survival, kill a zombie with a bow (`/summon zombie`, or a natural one). Then shoot one and switch to a sword before the arrow lands. | Ranged Precision XP both times, no Blade Mastery. Same with a crossbow's arrows. |
| 5 | Load a crossbow with a firework rocket (rocket in your off hand, then charge), shoot a zombie and switch to a sword before it hits. | **Ranged Precision**, no Blade Mastery (it trained Blade Mastery before this fix). |
| 6 | Kill a zombie from a **spawn egg**, with a bow and with a sword. | **No XP**, and the HUD says "No Ranged Precision XP from mobs out of spawn eggs" (or Blade Mastery). That's the rule in `attributes.yml` (`xp-integrity.by-origin.SPAWNER_EGG: 0`), which the server never applied before: spawn-egg mobs paid in full. **For testing kill XP, use `/summon`, `/cerb spawn`, or natural mobs, not spawn eggs.** |
| 7 | Do steps 2 and 4 in **creative**. | No XP, and the HUD says why, once per skill every 5 minutes: "No Fishing XP in creative mode · survival and adventure train skills". The same in survival pays. |
| 8 | Chop a log and mine an ore in creative with the right tool, then fly into land you haven't visited. | No XP, and the HUD says why (Woodcutting, Mining, Pathfinding), as in step 7. Pathfinding used to pay in creative and spectator; the chunks you fly through still pay once you walk them in survival. |

## 15. Fishing equipment, goals and collections

The fishing loop's goals and equipment (`docs/design/FISHING_EQUIPMENT.md`): find a missing species, track it, choose a setup, fish, discover or beat a record, unlock the next option. Treasure and sea-creature encounters are the other two goals. Fish in survival. Admin helpers: `/cerb skill <you> Fishing level <n>`; `/cerb gather item <id> [n]` (lurker_scale, sea_glass, bog_resin, auto_hook_reel, grub_bait, chum, glowworm_bait, wayfarers_bait); `/cerb fish part <you> <id> [remove]`; `/cerb fish bait <you> <id> [n]`; `/cerb fish legacyrod <you> scaled_grip,resin_line` (holding a rod: adds the upgrades the old way); `/cerb fish gear <you>`; `/cerb fish journal <you>` (species, bests, milestones, trophies); `/cerb fish force <you> species <id>`; `/cerb fish info` (looking at water: what can bite, with your setup).

| # | Do | Expected |
|---|---|---|
| 1 | Player Menu → Skills → Utility → **Fishing**. | The page stays readable: progress, how to train, attributes, next levels. Below them three small tiles, **Equipment**, **Journal: N of 31 species**, **Encounters: N**, and between the arrows a clock, **Since your last visit**. No giant tooltip. Woodcutting has one tile, **Woods: N of 11 discovered**; skills without real content have none. |
| 2 | Click **Journal**. Try the water tabs and the filter (All species / Undiscovered / Available here). Click a species. | Every species by water, with rarity, your best weight and what it needs. **Available here** lists what can bite where you fish right now (the same rules a bite uses). Clicking a species **tracks** it: each requirement on its own line with ✓ or ✕ (level, water, night/day, rain, deep ocean), where you fish: your bobber if a cast is out, else the water you were looking at, marked "preview", else "not known yet" (never where you stand). The tracked species is saved. On the **Treasure** tab, "Available here" needs a cast: open water is decided around your bobber (the place tile says "Open water: yes/no"). In creative it lists nothing and says only vanilla fish bite there. |
| 3 | Cast with a tracked species. Cast again, then somewhere it can't bite. | One short HUD line when the bobber settles: "★ Goldfin Trout can bite here" or what's missing ("needs Rivers", "bites only at night"). Not repeated while nothing changes; never over combat or ability messages. Tracking doesn't change your odds. |
| 4 | Player Menu → **Collections**. Open each collection, then Back. | Fish species, fishing treasure, sea creatures defeated and woods, with the same counts as the skill pages, opening the same screens; Back returns to Collections. A **Fishing milestones** tile shows each milestone's progress (step 10). |
| 5 | Fish a few minutes, then open the Fishing page. Click the clock, then right-click it. | "Since your last visit": catches, the Fishing XP really awarded, new species, personal bests, encounters banked. Click: **New discoveries**, the last notable catches with their real weights. Reopening doesn't clear it; right-click (or "Mark as read") clears what it showed; catches made meanwhile stay unread. Creative adds nothing. |
| 6 | Fishing → **Equipment**. `/cerb gather item lurker_scale`, then Rod → **Scaled grip** → Fit (it asks first). | Three presets (Discoveries, Treasure, Sea Creatures), four categories: **Rod** from Fishing 1, **Line** 10, **Lure** 25, **Reel** 50. Each part's tile says what it does, what you carry, where its item drops; owned parts show what putting them in or taking them out changes, as the engine computes it after caps ("Lure speed: 5.6% → 0.2% (−6.0% raw)", "no meaningful change (cap)"), and when it applies. Fitting uses one scale, keeps the part for good, and any normal rod uses it (enchantments and durability are the rod's own). |
| 7 | Click another preset; shift-click a preset and type a name in chat. | Switching is free and counts from your next cast (a cast already out keeps what it was cast with). Renaming accepts 1–20 letters, digits and spaces; "cancel" cancels. Treasure parts (floats, the lantern bob) go into Treasure and Sea Creatures, speed and encounter parts into all three. |
| 8 | Hold one of your **old upgraded rods** (or `/cerb fish legacyrod <you> scaled_grip,resin_line` holding a named, enchanted rod). Fish with it, then Equipment → **Upgraded rod in your hand** → Convert. | It keeps working as before (its upgrades count in hand; one you also own as a part counts once). The preview says what happens to each upgrade: becomes your part; "you already own it: 1 Lurker Scale comes back"; or it stays on the rod. Converting keeps the rod's name, enchantments and durability; it refuses (nothing changed) if your inventory is full or the rod changed since the preview. |
| 9 | Craft bait: **Grub Bait** (rotten flesh + wheat seeds), **Chum** (cod or salmon + bone meal), **Glowworm Bait** (glow berries + wheat seeds); 4 per craft. Equipment → **Bait Bag** → Put in. Fish, reel in empty, fish by day with Glowworm Bait. | The bag has 1 slot (2 at Fishing 20, 3 at 40), one bait type per slot, up to 64; one bait works at a time, the first slot your preset uses that can apply. **One bait per completed catch** it applied to; a missed bite keeps it on the line, and reeling in empty, logging out or dying puts it back in the bag. Night bait by day stays in the bag ("Waits for the night"). When the last runs out: "Bait depleted. Normal fishing continues." once. **Restock** (off by default) refills an empty slot with the same bait from your inventory, nothing else. |
| 10 | Catch species in 3 different waters; then 8 species; then set 10 personal bests (`/cerb fish force <you> species <id>` speeds it up). | Each milestone once, with a celebration: **Wayfarer** (3 waters) opens the **Wayfarer's Bait** recipe (dried kelp + sweet berries: undiscovered species ×1.5); **Surveyor** (8 species) gives **Surveyor's Float**; **Record Keeper** (10 bests) gives the **Specimen Hook**; **Master Angler** (every species) puts an emblem on the journal, the Fishing page and Collections. Rejoining or a reload grants nothing again; one you already met is granted at your next join, once. |
| 11 | Equipment → Lure: put **Surveyor's Float** in a preset; `/cerb fish info` looking at water, with and without it. | Species you haven't caught come ×1.5 as often (×1.50 beside them in the info); every requirement still applies, and the share of fish bites that are species stays the same. Where you have every species that can bite, the Equipment tile and one cast line say "normal odds". |
| 12 | Put the **Specimen Hook** in that preset instead. | It replaces the Float (one specialist per preset; the tile says what it removes). Fish come heavier within each species' own range (average 33% of the range instead of 29%); species, rarity, XP and encounters unchanged. |
| 13 | At Fishing 50: craft the **Auto-hook Reel** (3 iron ingots, a tripwire hook, redstone), Equipment → Reel → fit. Cast and wait; open a menu meanwhile; switch it off. | A bite is reeled for you a moment after it starts: the normal catch, XP, discoveries, Fortune, bait and encounters, the rod's normal wear and sound. It never recasts and never answers a Sneak-cast call (encounters are only spent by your own reel). Off: `/fishing reel off` or the Equipment tile. |
| 14 | New discoveries → click a catch while carrying that fish → confirm. | **Keep as trophy:** one fish becomes "Goldfin Trout · 3.21 kg" with who caught it, the date and the water; once per notable catch; trophies don't stack. Ordinary fish stay stackable. |
| 14b | Cast and wait for a bite (owner's request, 2026-10-28). Try `/fishing cue off`, then `/fishing cue on`. | A bell in your ear and a big **"!"** over your bobber, only for you: green, then yellow, then red as the bite runs out; it goes the moment you reel or the fish gets away. Off: nothing shows, fishing works as usual. |
| 15 | Overall. | Is the loop clear (a goal, a setup, a result)? Values to judge (all in `resources.yml` → `tackle:`): Surveyor's Float ×1.5, Wayfarer's Bait ×1.5 (together at most ×2), Specimen Hook strength 0.25, Grub Bait lure speed +8%, Chum sea-creature chance +1.5%, Glowworm Bait night species ×2, the reel's 0.4 s delay, the milestones (3 waters, 8 species, 10 bests), bag slots at 20 and 40. |

## 16. Farming and Cooking

Farming grows ingredients; Cooking turns them into meals that make you stronger for a while (`docs/design/FARMING_COOKING.md`). Play in survival. Admin helpers: `/cerb skill <you> Farming level <n>` (and `Cooking`); `/cerb farm info` (looking at a crop: ripe or not, placed or not, the XP it pays you, your Fortune chance); `/cerb farm crops <you> [reset]`; `/cerb cook give <you> <meal> [common|fine|superior|masterwork] [n]`; `/cerb cook clear <you>`; `/cerb farm stats`, `/cerb cook stats`.

| # | Do | Expected |
|---|---|---|
| 1 | Plant wheat, carrots and potatoes on farmland; bone meal some, let others grow. Break them ripe and unripe. | A ripe crop pays Farming XP (wheat 3, carrots and potatoes 3, beetroots 3.5), planted or wild, grown with bone meal or not; an unripe one pays nothing and the HUD says when it pays. The first ripe harvest of each crop is a discovery (chat and a sound, once). |
| 2 | Place a melon or pumpkin by hand and break it; break one that grew from a stem. Plant sugar cane, let it grow two segments, break the bottom. | Placed by hand: nothing (with a HUD line). Grown: 5 XP. Cane: the grown segments that fall pay 1 each; the one you planted doesn't. |
| 3 | Pick sweet berries and glow berries with a right-click. | Each pick pays; the bush stays. |
| 4 | Harvest next to water channels, with a piston farm, or let a villager farm. | The machines' drops are vanilla and pay nobody. Your own harvests in the same field pay as usual. |
| 5 | Replant now comes from a hoe (section 19): give yourself one with `/cerb farm hoe <you>`, break ripe wheat with it, then cocoa on a jungle log. Try `/farming replant off`. | Each ripe crop broken with the Replanting hoe is planted again at its first stage, with a seed from its own drops; cocoa on the same side of the same log. Off: nothing is replanted. `/farming` shows your techniques, the switch and whether the hoe in your hand replants. |
| 6 | Farming 25 with a hoe: hold Sneak and break a ripe crop in a field. Then at 50, 75, 100. | **Wide Harvest:** every ripe crop of that kind in a 3×3 (5×5 at 50, 7×7 at 75, 9×9 at 100), one level up or down, comes down in quick steps, each paid once, its drops in one pile at your crop, one point of hoe wear each (the hoe never breaks: the sweep stops first). Unripe crops and other kinds stay. With a Replanting hoe (section 19) the field is planted again; a plain hoe leaves it bare. Without Sneak, without a hoe or below 25: one crop. A claim protected by your protection plugin stops it at its edge. |
| 7 | Farming 50, then 100: harvest a lot of wheat, carrots, potatoes. | Some produce carries **✦ Fine / Superior / Masterwork quality** (about 12% at 50, a third at 100). It stacks only with its own quality, stays normal food otherwise. A campfire roasts a Fine potato into a Fine baked potato; a furnace or smoker makes plain food (and never stalls on a stack of Fine potatoes). |
| 8 | Player Menu → Skills → **Farming**, then **Crops**; Player Menu → Collections → Crops. | The page shows Vitality, Fortune (crops) as "Extra crop chance", Wide Harvest's square and the quality chance by level. **Crops: N of 12** opens each crop: when it is ripe, how it is harvested, its level and XP, which techniques work on it, discovered or not. Collections shows the same count. |
| 9 | Smoke or bake food in a furnace or smoker and take it out (shift-click too); put raw food on a campfire. Bake bread at a crafting table, one and a batch. | Cooking XP for food really made: cooked meats 3 each, salmon 4, baked potato 2; bread 4, a cake 20. A shift-click pays every item. Campfire food pays you when it is done. Hoppers and crafters pay nobody; dried kelp from a block, nothing; creative nothing (the XP display says why). |
| 10 | Cooking 10: put a bowl, any cooked meat, a carrot, a potato and a beetroot in a crafting table (four-ingredient meals also fit your own 2×2 grid). At 9, then at 10. | At 9 nothing; at 10 a **Hearty Stew** (Common), 20 Cooking XP, one of each ingredient used. Cooking 25 makes it Fine, 50 Superior, 75 Masterwork. Two of its three crops with a quality lift it one tier. The recipe book shows a meal once you have its level. |
| 11 | Eat a Hearty Stew when hurt, at full hunger too. Then eat a Trail Ration, then a Farmer's Breakfast (`/cerb cook give`). Eat the same meal twice. | Meals can always be eaten. Hearty Stew: max health up (Vitality +6 at Common) and 25% of max health back over 20 s, never past full. A new meal **replaces** the running one and says so; the same meal **refreshes** its time. Trail Ration: walking speed +8%; Farmer's Breakfast: more extra crops. `/cooking` shows your tier, the running meal and its time left. |
| 12 | Relog with a meal running, and once restart the server with one; then die with one. | It survives both with its time, which keeps running in real time while you're away (a heal still in progress doesn't carry over); death ends it. |
| 13 | Player Menu → Skills → **Cooking** → **Cookbook**; Collections → Meals. | Each meal with its level, ingredients, effect and duration at the tier you cook at, and whether you have cooked it. |
| 14 | Overall. | Values to judge (all in `resources.yml` → `farming:` and `cooking:`): crop XP and levels, Wide Harvest's squares, the quality odds (fine 10/22, superior 2/8, masterwork 0/3 at 50/100), Cooking XP per food, each meal's level, recipe, effect and duration, the tier steps (×1.2/1.4/1.6 potency, ×1.25/1.5/2 duration). |

## 17. Active effects

What is on your character, what it does, and how long it lasts (`docs/design/ACTIVE_EFFECTS.md`). Helpers: `/cerb cook give <you> <meal> [tier] [n]` and `/cerb cook clear <you>`; `/effect give @s <effect> <seconds> <level>`; Control Center → Attributes → you → Dev buffs.

| # | Do | Expected |
|---|---|---|
| 1 | Open the Player Menu with nothing running. Hover your head, then click it. | The head lists your attributes, then "Active effects: No active effects". Clicking opens **Active Effects**, which says there are none and that your permanent numbers are on Stats & Attributes. |
| 2 | Eat a Hearty Stew (`/cerb cook give <you> hearty_stew common 1`). Open the menu and hover your head. | "Hearty Stew — 9m 5Xs remaining" comes first under Active effects. The time counts down while the menu stays open. |
| 3 | Click your head and watch the stew's tile for half a minute. | The tile shows its name and time left, "Meal · Common · Survival", then **What it does:** "• Vitality +6", and under **Now:** your max health before → after (103 → 109 on a new character). Below: "Restoration: Ns left" for the first 20 s, then "not running now". The times count down without reopening. |
| 4 | Eat a Trail Ration, then the same again. | The ration replaces the stew: only the ration shows, "• Movement speed +8%" and under Now: your movement speed total before → after (the same numbers as the Stats page). Eating it again resets its time. |
| 5 | As a Warrior with War Cry in your loadout: cast it, then open the menu quickly and click your head. | War Cry with its seconds left, "Warrior ability", "• Damage +15%" and under Now: your weapon's damage multiplier before → after (x1.15 of it). When it ends, the screen redraws without it. |
| 6 | Hold a sword, then eat a meal or get a buff that changes several numbers (a dev buff of +100 Agility or Fortitude). | Every number it moves is listed under Now: crit chance and damage, attack speed for the weapon in your hand, armor (with your gear and the caps), the defense bar, resistances... A number that doesn't move isn't listed. |
| 7 | `/effect give` yourself Speed II, Regeneration and an infinite Luck. | Each shows its level and time ("until removed" for the infinite one), described as it works here: Speed "Walking speed +40%", Regeneration "No effect on Cerberus health…". Clear one while the screen is open: its tile goes. |
| 8 | Give yourself an admin dev buff: Control Center → Attributes → you → Dev buffs → +100 Vitality. | "Dev buff +100 Vitality — 4m 5Xs remaining", "Admin test buff", "• Vitality +100" and max health before → after. Clearing it removes the tile. |
| 9 | Put a passive or a keystone in the loadout you use. | An **Always on** tile (bottom row, next to Back) lists it apart from the timed effects, with what it adds and changes. |
| 10 | Let a meal run down to its last minute. | About a minute before it ends, the HUD says "Hearty Stew wears off in 1 min" once, with a soft chime. It never covers an ability message or a level-up (it waits for them), and never comes for a meal you replaced. Relogging after it doesn't say it again. |
| 11 | With a meal running: relog; restart the server; die. | After a relog or restart the meal is there, its time having run on in real time. After death it is gone. |
| 12 | If you can, with a friend: be in a PvP fight with a meal running and open the screen. | The header says combat bonuses count against creatures only for now and a meal's healing waits for the fight to end; the restoration line says "paused: PvP". |
| 13 | Look for Stats & Attributes. | Still on its hub tile (blaze powder) and behind the Health and Essence tiles. The Active Effects screen has a shortcut to it too. |
| 14 | Overall. | Is it clear what you have, what it does and when it ends? Note wording, order, or anything missing. |

## 18. Combat improvements: Martial Expertise's Agility and Iron Reprisal

Martial Expertise now gives Agility too, and Heavy Armor Mastery throws melee attackers back (`docs/design/COMBAT_IMPROVEMENTS.md`). Play in survival, at night or in a dark area. Helpers: `/cerb skill <you> Martial_Expertise level <n>` (and `Heavy_Armor_Training`); `/cerb reprisal <you>` (your level, heavy pieces and push); `/cerb reprisal` (how many attackers were thrown and why others weren't).

| # | Do | Expected |
|---|---|---|
| 1 | Note your Agility on the Stats page, then set Martial Expertise to 50 and look again. | Agility rises 1.25 per level gained (from Martial Expertise 1 to 50: +61.25), and the Agility tile lists "Martial Expertise +1.25". Crit chance and speeds rise with it, within their caps. Your fist's Power still grows 1 per level. |
| 2 | Martial Expertise 11, then punch a zombie to death bare-handed. | The level-up line names the Agility it added (with the Power). |
| 3 | Pick up a sword with Martial Expertise at 50. | Crit chance is the same as with fists: Agility counts with every weapon. Judge whether that is what you want. |
| 4 | Heavy Armor Mastery 10, full iron armor: let a zombie hit you. | The zombie is thrown back a few blocks with a soft clang; no damage to it. The level-up from 9 to 10 announces "New technique: Iron Reprisal". |
| 5 | Heavy Armor Mastery 100: let zombies hit you in one, two and four iron pieces. | The more heavy pieces, the farther they fly (a quarter of the push per piece); at 100 in full iron, much farther than at 10. |
| 6 | In full iron at 100: let a skeleton shoot you; raise a shield against a zombie, facing it; wear leather instead. | The skeleton is never thrown (arrows never trigger it). Behind the raised shield the zombie's blows take **no health** from you (until this build they still did) and the shield wears a little, as in the vanilla game (which pushes nobody on a block); Iron Reprisal throws nothing. Leather throws nothing. |
| 7 | A ravager or a hoglin hits you in full iron at 100. | They move much less (their knockback resistance counts); an iron golem or a warden doesn't move. |
| 8 | If you can, with a friend: have them hit you in melee while you wear full iron at 100. | Nothing happens to them (PvP is off for now: `combat.yml` → `iron-reprisal.pvp: false`). |
| 9 | Overall. | Values to judge (all in `combat.yml` → `skills.heavy-armor-training.iron-reprisal` and `attributes.yml` → Martial Expertise): the push at 10 and 100, the opening level, the cooldown; whether Martial Expertise's Agility makes unarmed and other weapons too strong. |

## 19. Replant on the hoe

Your note after the Farming build: Replant now comes from the hoe, not from Farming 10 (`docs/design/FARMING_COOKING.md` §9). It is a Cerberus hoe upgrade, like the heartwood grips: a real registered enchantment would have meant rebuilding the plugin as a different kind of plugin and writing into your world, so it was left out. Play in survival.

| # | Do | Expected |
|---|---|---|
| 1 | At a crafting table: wheat seeds, beetroot seeds, a melon or pumpkin seed, bone meal, string and a gold nugget. | A **Sower's Charm** (a rabbit's foot with its own name and lore). It can't be placed, brewed or used in a vanilla recipe. |
| 2 | Put the charm and a hoe in a crafting grid (try an enchanted, renamed, worn hoe) and take the result. | The same hoe, its enchantments, name and wear kept, with "✦ Replanting: ripe crops it harvests are planted again". No enchantment glint and no "Replant I" line: it never pretends to be a vanilla enchantment. The charm is used up. A hoe that already has it can't take a second. |
| 3 | At Farming 1, break ripe wheat, carrots, potatoes, beetroots and nether wart with that hoe, then cocoa on a jungle log. | Each is planted again at its first stage, with a seed from its own drops; cocoa on the same side of the same log. Farming XP as before. |
| 4 | Set Farming to 10 or more and break ripe crops with a plain hoe, then by hand. | Nothing is replanted, at any level: the hoe is the source now. |
| 5 | Farming 25 with the Replanting hoe: Sneak and break a ripe crop in a field (Wide Harvest). Then with a plain hoe. | With it, the field comes down and is planted again crop by crop; with a plain hoe it is left bare. |
| 6 | `/farming` with each hoe in hand; the Farming skill page; the Crops screen. | `/farming` says whether the hoe in your hand replants, and how to make one if it doesn't. Nothing says "Replant at 10" any more. |
| 7 | If you had Farming 10 or more before this build: join. | One chat message, once: Replant now comes from a hoe, with the recipe. Nothing is taken or given. (`/cerb farm notice <you> reset` makes it say itself again at the next join.) |
| 8 | Put the charm and a hoe in a crafter (the block) and power it. | It makes nothing: a crafter never makes a Cerberus upgrade. |
| 8b | Offer two Sower's Charms to a cleric that buys rabbit's feet. | The emerald can't be taken ("Cerberus items can't be traded to villagers"); plain rabbit's feet trade as usual. |
| 9 | Overall. | Is the charm's recipe fair, and does Replant on the hoe feel right? Its recipe and the upgrade are data in `resources.yml` (`items: sowers_charm`, `upgrades: replanting`, `recipes:`). |

## 20. Lifesteal (decision 6: nothing changed, yours to judge)

Weapon Mastery's lifesteal is unchanged, as you ruled for this milestone. The review measured it and recommends a per-hit cap, after your playtest (`docs/design/LIFESTEAL_REVIEW.md`). This section is only for your judgement. Use a test copy of the server, never your live world.

| # | Do | Expected |
|---|---|---|
| 1 | At Weapon Mastery 50 or more, in iron with a good sword, fight a crowd (four or five zombies or skeletons a few levels above you), then a boss or a mob that hits hard. | Note how low your health gets. The review's crowds never pushed a fighter below 97.7% with lifesteal, and down to 59–82% without it. |
| 2 | On the test copy only: set `limits: lifesteal: 0.05` in `attributes.yml`, `/cerb reload`, and fight the same crowd. Then put the file back. | The same fight with almost no lifesteal: is it closer to how dangerous it should feel? |
| 3 | Overall. | Too safe, right, or too harsh? Tell me which option of the review you want (B, a per-hit cap, is the recommendation), or to leave it as it is. Nothing changes until you say so. |

## 21. Herbalism

Gathering wild plants (`docs/design/HERBALISM.md`): 35 herbs as data, Essence and Fortune on herbs from the level, rare reagents from 25, reagent quality from 50, and Wildcrafting from 10. Its reagents are Alchemy's.

| # | Do | Expected |
|---|---|---|
| 1 | In survival, pick flowers in the wild (a poppy, a dandelion, a cornflower), then the same kind again. | Herbalism XP for each (3 for a poppy), and a discovery bonus the first time only; the XP display shows it. |
| 2 | Break a fern by hand, then with shears. | By hand: nothing of the fern drops, no XP, and the HUD says it wants shears. With shears: the fern drops and pays. |
| 3 | Place a poppy from your inventory and break it. Then bone-meal grass and pick a flower it grew. | The placed poppy pays nothing (the HUD says why); the bone-meal flower pays in full. |
| 4 | Place a pink petal, bone-meal it to two petals, break it. Place a small dripleaf on clay and bone-meal its top half, then break the big dripleaf. | Nothing for either: bone meal grew a plant you placed, so it stays placed. A wild pink petal or small dripleaf bone-mealed the same way pays. |
| 5 | Set Herbalism to 25, then 100, and gather a few dozen poppies (`/herbalism` shows your chances). | At 100 about a quarter drop a second poppy, and now and then a **Bloodroot** (a rare reagent, a discovery the first time). Rare reagents can't be placed, burned or used in vanilla recipes. |
| 6 | At 50 or more, look at gathered herbs and rare reagents. | Some carry a quality ("✦ Fine quality"…): Alchemy reads it. |
| 7 | At 10 or more, hold shears, Sneak and gather one wild poppy in a patch (also try underwater seagrass). | Every wild plant of that kind in a 3×3 is gathered (5×5 at 40, 7×7 at 70), each paid, the drops in one pile, one point of shears wear each; placed plants and other herbs stay. Underwater, water stays where they stood. |
| 8 | Player Menu → Skills → Herbalism → Herbarium; Collections → Herbs and Rare reagents; `/herbalism`. | The same counts everywhere ("Herbarium: N of 35", "Rare reagents: N of 4"). |
| 9 | Overall. | Do the herbs' XP, levels and rare odds feel worth gathering? Every number is data in `resources.yml` (`herbalism:`). |

## 22. Alchemy

Brewing (`docs/design/ALCHEMY.md`): vanilla brews pay Alchemy XP; eight Cerberus brews, draughts that heal and elixirs that last, are made from Herbalism's herbs and rare reagents; your level sets their tier; Alchemy gives Essence. Play in survival. Helpers: `/cerb skill <you> Alchemy level <n>`; `/cerb alchemy give <you> <brew> [common|fine|superior|masterwork] [count]` (brews: mending, swiftness, vigor, stoneskin, insight, might, bloodroot, moonlit); `/cerb alchemy clear <you>` (ends your elixir and draught); `/cerb alchemy brews <you> [reset]`; `/cerb alchemy stats`.

| # | Do | Expected |
|---|---|---|
| 1 | At a brewing stand with blaze powder: brew three water bottles with nether wart, then the Awkward Potions with sugar. | When each brew completes, 2 Alchemy XP a bottle (6 for three), on the XP display. Nothing while it brews. |
| 2 | Feed a stand from hoppers (bottles and blaze powder from the side, nether wart from above). Stand at it with its window open and click an empty slot now and then. | It brews as in vanilla and pays nobody, however often you click. A bottle or reagent you put in yourself makes that brew yours. |
| 3 | Brew in creative. | It brews; no XP (the XP display says why). |
| 4 | At Alchemy 9, brew Awkward Potions with a poppy; then at 10. | At 9: three **Mundane Potions**, and the HUD says the Draught of Mending needs Alchemy 10. At 10: three **Draughts of Mending** (Common), 18 XP, and the first is a discovery (chat and a sound, once). |
| 5 | At Alchemy 25, 50 and 75, brew a Draught of Mending. At 50, brew one with a **✦ Fine** poppy (Herbalism 50 rolls a quality), then an Elixir of Vigor with Sunpetal instead of a dandelion (Alchemy 20 or more). | Fine at 25, Superior at 50, Masterwork at 75, on the bottle with its effect and time. A Fine reagent lifts the brew one tier (Superior to Masterwork at 50), and so does Sunpetal in place of the dandelion (Emberbloom in place of crimson fungus for the Elixir of Might). |
| 6 | On a test copy: start a brew, stop the server before it finishes, start it again and let the brew finish. | The brew finishes as yours: the right brew, paid to you. |
| 7 | Hurt yourself and drink a Draught of Mending; drink a second at once. | 25% of max health back over 5 s, never past full. The second is refused while the first heals ("A draught is still working"), and you keep the bottle. |
| 7b | If you can, with a friend: drink a draught while they keep hitting you for half a minute. | It heals nothing during the fight, then its full 25% once the fight ends (15 s after the last hit). The same holds for a meal's restoration. |
| 8 | Drink an Elixir of Vigor; eat a Hearty Stew; drink a Tonic of Swiftness; drink the Tonic again. | Vigor: Vitality +8 (max health up on the Stats page). The stew and the elixir both count. The Tonic **replaces** the Elixir of Vigor and says so; the same Tonic again **refreshes** its time. |
| 9 | Open the Player Menu: hover your head, then click it. | The elixir with its time left, after the meal. Active Effects shows it ("Elixir · Common · Alchemy"), what it adds and every number it moves; a draught shows its seconds while it heals. |
| 10 | Relog with an elixir running; restart the server; die. Let one run to its last minute. | It survives a relog and a restart, its time running on in real time; death ends it (and a draught). A minute before it ends, the HUD says "… wears off in 1 min" once. |
| 11 | Player Menu → Skills → **Alchemy**, then **Brews**; Collections → Brews; `/alchemy`. Set Alchemy to 50 and look at the Stats page. | The tier you brew at, **Brews: N of 8**, each brew with its level, reagent, effect and time at your tier, made or not. `/alchemy` shows your tier and your running elixir and draught. Essence rises 0.5 per level (+24.5 from 1 to 50). |
| 12 | Overall. | Values to judge (all in `resources.yml` → `alchemy:`): each brew's level, reagent, effect, time and XP; the tiers (25/50/75) and their steps (×1.2/1.4/1.6 strength, ×1.25/1.5/2 time); 2 XP a vanilla bottle. Are draughts and elixirs worth making beside meals? |

## 23. Recipes

Player Menu → **Recipes** (the crafting table in the hub): how to make every Cerberus item, from the same data the game uses.

| # | Do | Expected |
|---|---|---|
| 1 | Open the Player Menu and hover **Recipes**. | Available, with the counts: crafting, meals and brews. |
| 2 | Click it. Hover the **Sower's Charm**, **Replanting**, **Heartwood grip** and **Wayfarer's Bait**. | Each shows its ingredients and where it is made. The charm needs a crafting table and is in your recipe book. Replanting takes the charm and any hoe in any grid and says what it gives; it is not in the recipe book. Wayfarer's Bait is grey and locked until the Wayfarer milestone (species found in 3 waters), then shows as unlocked. No rod part is listed. |
| 3 | Make a Sower's Charm and a Replanting hoe exactly as the screen says. | Both work as shown. |
| 4 | Click **Meals**, then Back; **Brews**, then Back; **Rod parts**, then Back. | The Cookbook, the Brews list and Fishing Equipment open; Back returns to Recipes each time. |
| 5 | Overall. | Can you find how to make anything new without asking? |

## 24. Afterwards

Set your skill levels back, then tell me "works and feels right" or what feels off: too strong, too weak, too slow, or confusing. Include any red error lines from the server window. Once you confirm, I'll prepare a reviewed pull request into `master`. It won't be merged automatically.
