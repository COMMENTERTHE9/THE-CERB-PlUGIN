# Playtest checklist: Admin Control Panel 2.0, Attributes 2.0, decisions A–E, Player Menu 2.1, classes and ability controls

*2026-10-25, branch `claude/quirky-mccarthy-nhaz58`. About 50 minutes. Everything below was checked on the cloud sandbox server; this list is for your own world.*

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
| 6 | Look at open ground. **Q**, then **Sneak + F**. | Charge dashes you forward (the sword flickers for a moment, but is never dropped). Blink right after it waits: `Blink 1.2 s`. Later, Sneak + F blinks you 6 blocks. |
| 7 | Swap the sword for a **pickaxe, a block or food**. Press R, F, Q. | All vanilla: you mine, place, eat, swap and drop as usual. |
| 8 | Sword in hand: right-click a **door, a chest, a villager, a horse**. | They open, trade or mount as usual. With Sneak, right-click casts instead (vanilla also skips them while sneaking). |
| 9 | Empty a slot (A2 → none). Press **F**. | `Nothing on F · set it in Classes & Abilities`. No swap. |

### 6b. The Mage and the focus (your earlier report)

| # | Do | Expected |
|---|---|---|
| 10 | Loadout 2: Arcane Bolt (A1), Frost Nova (A2), Fireball (A3), Blink (A4). **Blaze rod** in hand. | A blaze rod, breeze rod or stick is a focus. |
| 11 | Right-click at a zombie 6 blocks away. | `✦ Arcane Bolt`, a purple beam to it, 15 Essence spent. Aim half a block beside it: still hits (aim assist). |
| 12 | Aim well beside it, at one 10 blocks away, or behind a wall. | `Arcane Bolt: no target within 8 blocks`, a whiff. **No Essence and no cooldown spent.** |
| 13 | **F** with zombies around you; **Q** at a zombie. | Frost Nova damages and slows those within 4 blocks; Fireball bursts on the first zombie (20 Essence). |
| 14 | Spend your Essence (`/cerb mana YourName 0`), then right-click. | `Arcane Bolt needs 15 Essence`, a fizzle. |
| 15 | A sword instead of the rod. Right-click. | `Arcane Bolt needs a focus`, a low note. |
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

**What only you can judge, please tell me:**
- Which scheme feels better in a real fight: quick keys or click combos? Should the default change?
- Is 1.5 s right for the messages? Does the boss bar help or get in the way?
- Can you tell the refusal sounds apart (cooldown tick, Essence fizzle, low note, whiff)?
- Do the three classes feel different, and does each ability feel worth pressing?

## 7. Afterwards

Set your skill levels back, then tell me "works and feels right" or what feels off: too strong, too weak, too slow, or confusing. Include any red error lines from the server window. Once you confirm, I'll prepare a reviewed pull request into `master`. It won't be merged automatically.
