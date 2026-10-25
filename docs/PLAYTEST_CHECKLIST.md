# Playtest checklist: Admin Control Panel 2.0 + Attributes 2.0

*2026-10-25, branch `claude/quirky-mccarthy-nhaz58`. About 20 minutes. Everything below was checked on the cloud sandbox server; this list is for your own world.*

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
| 3 | `/cerb combatdebug` (run it again to turn it off), then hit a mob at Blade Mastery 50 | `[Combat] Power(Swords, Power 50.0 -> x3.50)` plus crit and lifesteal steps. |
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

## 4. Afterwards

Set your skill levels back, then tell me "works and feels right" or what feels off: too strong, too weak, too slow, or confusing. Include any red error lines from the server window. Once you confirm, I'll prepare a reviewed pull request into `master`. It won't be merged automatically.
