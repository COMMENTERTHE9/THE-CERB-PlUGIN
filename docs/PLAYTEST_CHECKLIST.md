# Playtest checklist: Admin Control Panel 2.0, Attributes 2.0, decisions A–E, Player Menu 2.1

*2026-10-25, branch `claude/quirky-mccarthy-nhaz58`. About 35 minutes. Everything below was checked on the cloud sandbox server; this list is for your own world.*

**Before you start.** Start with `run.bat`, which backs up the plugin data before deploying. Test as an operator. Skill levels you set are saved, so **note your levels first** (Player Menu → Skills) and set them back at the end. The simulator and dev buffs change nothing permanent, so prefer them. Never use `/reload`.

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

## 6. Afterwards

Set your skill levels back, then tell me "works and feels right" or what feels off: too strong, too weak, too slow, or confusing. Include any red error lines from the server window. Once you confirm, I'll prepare a reviewed pull request into `master`. It won't be merged automatically.
