# Skill audit, combat group (8 skills): code-grounded

Scope: Blade Mastery, Martial Expertise, Weapon Mastery, Ranged Precision, Heavy Armor Training (shown as Heavy Armor Mastery), Dual Wielding, Critical Strike, Survival. Repo state: `1d2423f`. Read-only audit; nothing in the repository was changed.

**Evidence key.**
- **Read**: plugin source.
- **API/bytecode**: checked in `paper-api-1.21.11` or the sandbox Folia jar with `javap`.
- **Computed**: the code's own formulas, evaluated with a throwaway script (`xpForLevel`, `KillXp`, `ScalingFormula`).
- **Played**: a result already recorded in `docs/STATUS.md` or the playtest scripts. This audit ran nothing in game.

Paths are under `src/main/java/` unless they start with `docs/`, `tools/` or `src/main/resources/`.

## Shared paths (referenced below)

- **K1, kill XP.** Registered `Listener/MobKillListener.onEntityDeath` (:45-95; registered in `CerberusPlugin`:531).
  - It runs for any LivingEntity whose `getKiller()` is a player (:48-49).
  - The skill is chosen by the item in the main hand **at death time**, through `skills.yml` (:57-61, :120-142). With no rule match it falls back to an elemental tag, then to Heavy Armor Training when heavy armor is worn (:135-138).
  - XP = `40 × (1 + 0.15 × (mobLevel−1)) × tierMult`, times 0.25 when mobLevel ≤ skill − 20 (`Attributes/KillXp`:31-40; `attributes.yml`:10-17). Common L1 40, L25 184, L50 334, L100 634.
  - Mob level = distance from spawn / 150 (`Listener/MobScalingListener`:98-101).
  - Award path: `SkillManager.addXpAndCheckLevelUp` (:343-355) → `SkillProgression.award`. XP is truncated to whole points per award (`toWholeXp`, :88-92).
- **K2, per-hit attacker XP.** Critical Strike only (`Manager/CombatSkillProcessor.awardAttackerHitXp`:260-266), called from `Listener/VirtualHealthListener`:271 and :362.
- **K3, defender XP.** Heavy Armor Training and Survival (`CombatSkillProcessor.awardDefenderHitXp`:272-283).
  - Called for every non-skipped hit on a player on the main path, any cause (`VirtualHealthListener`:161-242, call at :230).
  - Also for projectiles from another living shooter (:421-429).
  - Skipped hits pay nothing: the 250 ms invulnerability window (`HealthLifecycleEngine`:40-45; `combat.yml`:52-54).
- **A1, attributes.** `Attributes/SkillSource` → `AttributeCalculator` → three consumers:
  - `Manager/PveOffense` for player→mob hits (`VirtualHealthListener`:245-254, :441-452);
  - `PlayerDefenseManager.updateDefense` and `AttributeEffects` for defense and health;
  - `SecondaryEffects` for speed stats.

  Unlearned skills count as level 1 (`SkillSource`:197-205; `SkillManager.getSkillLevel`:465-468), so every player starts with Agility 2.0 and Weapon Mastery's level-1 lifesteal. PvP offense still uses the old per-skill bonuses in `CombatSkillProcessor.processAttackerSkills` (:55-79), on the player-target paths only (`VirtualHealthListener`:172-174, :388-390).
- **D1, dead per-skill code.** No caller of `.applyEffect(` exists for any of the 8 skill classes. `EffectManager.applyCombatSkillEffect` (:66-79) has no caller. The only EffectManager dispatch in use is `applyMagicSkillEffect("Elemental Mastery")` from `Spells/FireballSpell`:65 and :92, which lands on an empty placeholder (:163-167). The `Skills/Skills` enum and its descriptions are unused.

**Shared protection gaps in K1** (affect BM, WM, RP, ME and HAT's fallback):
- **X1, any source of mobs pays full XP.** There is no spawn-reason, spawner, breeding or AI check (grep: no `SpawnReason`/`GameMode` in any XP path). Passive animals, villagers and golems pay full kill XP; every `Mob` gets a level and pool (`MobScalingListener`:79). Class XP already excludes non-`Enemy` kills (`MobKillListener`:94); skill XP does not.
- **X2, creative mode.** No game-mode check in K1 or K2. A creative player can fly out, spawn-egg L100 mobs and level any weapon skill.
- **X3, name-tag multipliers.** `mobs.yml` matches by custom name at death: `legendary_mob` "^Legendary .*", any entity, ×4 (:57-61); "High Health Zombie" ×3 (:35-39); "Sea Monster" ×2.5. The pool was sized at spawn, so a name-tagged cow keeps cow HP and pays ×4 within the session (`Manager/MobRegistry.resolveInternal`:162-196; `MobScalingListener`:117-119).
- **X4, routing by the item held at death, not by the killing hit.**
  - A thrown-trident kill finds the hand empty → Martial Expertise.
  - An arrow kill after switching to a sword → Blade Mastery.
  - A bow held in the off hand → Martial Expertise.

  The damage path already classifies by projectile (`VirtualHealthListener`:444-445); the kill path doesn't.
- **X5, PvP kills pay weapon-skill kill XP.** `PlayerDeathEvent` has no handler list of its own, so EntityDeathEvent listeners receive it (API: `javap`). A player resolves as COMMON ×1.0 at level 1 (`MobContext`:14-15): 40 XP, or 10 from skill level 21 up. The class share is excluded.

**Shared protection gaps in K3:**
- **X6, no idle or self check.** Defensive XP has no idle check (`docs/STATUS.md`:409, open decision 8). The main path has no self check, so falls, cactus, your own TNT or lingering cloud count. Only the projectile path excludes self and dispensers (:424).

**Pacing (computed).** Cumulative XP to reach a level: L5 2,292 · L10 17,857 · L20 132,501 · L30 423,937 · L50 1,826,471 · L100 13,199,772. `SkillManager`:414 says "≈ 19M"; the formula gives 13.2M. Equal-level Common kills to reach level 50: about 7,800 (about 30,800 to 100).

---

### Blade Mastery
**Status:** functional (PvE). Mixed with:
- the PvP bonus, live on the old path;
- legacy effect code, disconnected (D1);
- kill-XP protection gaps X1–X5.

1. **XP source:** K1 only. A kill with `.*_SWORD` in the main hand (`skills.yml`:26-30, priority 10); amounts as in K1. There is no per-hit XP. Admins can set it with `/cerb skill` (`CerberusPlugin`:763-765) or `GUIs/admin/SkillEditMenu`:140/175.
2. **Balance and protection:** grey rule ×0.25 at mobLevel ≤ BM − 20, with a chat hint (`MobKillListener`:79-85). Distance-based levels force travel. Open gaps: X1–X5. Pace: about 7,800 equal-level kills to reach BM 50.
3. **Reachable effects:**
   - **Sword Power, +1/level** (`attributes.yml`:25) → ×(1+0.05·Power) before the pool drains (`PveOffense`:67-93): ×3.51 at L50, ×6.01 at L100. Played: an L50 zombie takes 19.5 → 7.5 → 5 swings at BM 1 → 50 → 100.
   - **Sword attack speed, +0.003/level**, diminished, cap 25% (`attributes.yml`:112; `SecondaryEffects`:71-77): +11.4% at L50, +17.5% at L100 (Played: 1.61 → 1.88 attacks/s). Paused while PvP-tagged.
   - **PvP only:** sword damage vs players +3.5% at L1, +40% at L50, +95% at L100 (`CombatSkillProcessor`:133-152).
   - **Class entry:** Warrior (BM 5 or WM 5, `classes.yml`:31) and Duelist (BM 10 + CS 5, :43).
4. **Incomplete or disconnected:**
   - `BladeMasterySkill.applyEffect` (:17-40) is never called. It would compound `setBaseValue` every call (+10%/lvl damage, +5% speed, +1% move).
   - `getDamagePerLevel` and `getSpeedMultiplier` (:43-50) are unused.
   - `EffectManager.applyBladeMasteryEffect` (:107-114) is unreachable.
   - `combat.yml` blade-mastery.speed-bonus is loaded into `bmSpeedScaling` (`CombatConfig`:209) and never read.
   - In PvP, BM's sword check reads the shooter's main hand when an arrow lands, so an arrow can get the sword bonus (:141-142 via :388-390).
5. **Attributes:** Power (sword) 1.0/level and attack speed 0.003/level (sword). PvE only; PvP uses item 3's PvP bonus.
6. **Progression rewards:** numbers plus two class gates. No technique, sword mechanic or milestone at any level. The skill page's "Next levels" (`GUIs/player/SkillDetailMenu`:198-229) only previews numbers.
7. **Dependencies:**
   - Built: kill XP v2, mob levels and pools, the attribute engine, `SecondaryEffects`, the class prototype.
   - Not built: sword archetypes (`docs/design/WEAPONS.md`:58-59, 72, 74), realms (everyone is Awakened), the PvP pass, level-150 mastery.
8. **Missing gameplay:** any sword identity beyond the multiplier, such as the Jian combo-extender or the Dao cleave arc (WEAPONS.md).
9. **Future interactions:**
   - Spellblade (BM 15 + Spell Weaving 15) and Spiritbinder (BM 10) (`docs/design/CLASSES.md`:103, 115).
   - Smithing-made swords should feed the same Power path.
   - Realm magnitude is already in `hitMultiplier`.
10. **Now vs later:**
    - Now: the K1 integrity fixes (X1–X5), and routing by the weapon of the last player hit.
    - Later: archetype mechanics and mastery techniques.

### Weapon Mastery
**Status:** functional (PvE). Mixed with:
- the PvP bonus, live on the old path;
- dead legacy code (D1);
- the double-role imbalance still open (`docs/STATUS.md`:403);
- X1–X5.

1. **XP source:** K1 with MACE, TRIDENT, `.*_AXE` or `.*_SPEAR` in the main hand (`skills.yml`:46-54). An older `skills.yml` gets the spear rule added in memory (`ConfigSkillMapper`:178-189). A thrown-trident kill trains whatever the emptied slot holds, usually Martial Expertise (X4).
2. **Balance and protection:** same as Blade Mastery.
3. **Reachable effects:**
   - **Power:** +1/level for axe, mace, trident and spear, and +0.25/level for every weapon class (`attributes.yml`:26-31). Its own weapons reach ×7.25 at L100 with this one skill (`docs/ATTRIBUTES_ENGINE.md`:143-155; Played `progression.js mastery`: axe 4.3 swings vs an L100 zombie).
   - **Attack speed:** +0.003/level on its 4 weapons (:113-117).
   - **Lifesteal on every PvE hit, melee and arrows** (`combat.yml`:168-173 via `CerberusPlugin.attributeCurves`:319-323, `SkillAttributes`:161-168, `PveOffense.dealt`:96-105, heal at `VirtualHealthListener`:349-361): 2.7% at L1 for every player, 26.8% at L50, 39.4% at L75, 40% cap. It is the only in-combat healing, since combat regen is 0 (`combat.yml`:47-50).
   - **PvP only:** +18% on every PvP hit at L1, rising to +132% at L100, for any weapon, spells and arrows included (`CombatSkillProcessor`:62, :155-157). PvP lifesteal is restored at the old share (:74-78, :109-120).
   - **Class entry:** Warrior (WM 5).
4. **Incomplete or disconnected:** `WeaponMasterySkill.applyEffect` (:17-34, marked "Example") is never called; `getLifestealPercent` and `getDamageMultiplier` (:37-44) are unused.
5. **Attributes:**
   - Power, both per weapon class and general.
   - Attack speed.
   - `Stat.LIFESTEAL`, the engine's only lifesteal source.

   All PvE only.
6. **Progression rewards:** numbers plus the Warrior gate. Lifesteal is the most felt change.
7. **Dependencies:** same as Blade Mastery, plus the Weapon Mastery split (decision 2) and the blunt-weapons question N12 (`docs/design/CULTIVATION_INTEGRATION.md`:107), which may move maces to Heavy Armor Training.
8. **Missing gameplay:**
   - Per-weapon identity: axe "Sundered", spear reach, hammer stagger (WEAPONS.md:60-61, 63-64).
   - Correct thrown-trident routing.
   - A decision on whether lifesteal for everyone from level 1 is intended.
9. **Future interactions:** Berserker (WM 10 + Survival 10); greatsword, spear and battleaxe archetypes; the specialized/general split.
10. **Now vs later:**
    - Now: route projectile kills by projectile type; start the lifesteal curve at 0 if L1 lifesteal is unwanted.
    - Later: the split, together with the weapon archetypes.

### Ranged Precision
**Status:** functional (PvE). Mixed with:
- accuracy, placeholder only;
- crossbows, which get no draw speed;
- no PvP role;
- X1–X5.

1. **XP source:** K1 with BOW or CROSSBOW in the main hand (`skills.yml`:56-61). There is no per-hit XP (`VirtualHealthListener`:452, `hitXp=false`). Kills are routed by the held item (X4).
2. **Balance and protection:** same as Blade Mastery. A dispenser's arrows get neither Power nor credit.
3. **Reachable effects:**
   - **Bow Power, +1/level** (`attributes.yml`:33). It applies to bow and crossbow arrows hitting mobs, classified by projectile type (`VirtualHealthListener`:444-452): ×3.51 at L50.
   - **Draw speed, +0.004/level** (plus +0.001 per Agility), diminished, cap 50% (`attributes.yml`:108, :118; `SecondaryEffects.onShoot`:122-139). An early release flies as if drawn longer, and full power makes a critical arrow. +16.6% at L50, +27.6% at L100 (full draw 1.00 → 0.78 s; Played: a 0.85 s release went from power 0.81 to full power).
   - Draw speed is for bows only: crossbows are skipped (:125). It is paused while PvP-tagged, and the extra damage is removed against players (`VirtualHealthListener`:372-377).
   - Arrows can crit against mobs through the Agility roll (`PveOffense`:70-72), but they never train Critical Strike.
4. **Incomplete or disconnected:**
   - `RangedPrecisionSkill.applyEffect` (:20-56) is never called, and would be inert anyway: it calls `setDirection` on a Location copy (:52-54, :58-64). Its draw speed is a TODO (:47-49); its "crit" is a chat message.
   - The skill page honestly marks "Better arrow accuracy" as Not active (`GUIs/SkillDefinitions`:217-219).
5. **Attributes:** Power (bow) and draw speed, PvE only. There is no PvP per-skill bonus for this skill at all.
6. **Progression rewards:** draw speed is the most tactile reward in this group. Otherwise numbers; there is no class gate yet.
7. **Dependencies:**
   - Crossbow reload speed: proposed in `docs/design/ATTRIBUTE_EXPANSION.md`:39 (no clean Paper hook).
   - Accuracy and falloff: design only.
   - Mob projectiles stay vanilla at any level (`docs/STATUS.md`:408, decision 7).
   - Bow archetypes: not built.
8. **Missing gameplay:**
   - Crossbow parity.
   - Accuracy, falloff or headshots (`docs/design/CERBERUS_DESIGN_PLAN.md`:810).
   - Thrown weapons.
   - Any crit training for archers.
9. **Future interactions:** Ranger (RP 10 + Survival 10) and Marksman (RP 15 + CS 10) (CLASSES.md:96-97); Recurve, Longbow, Shortbow, Crossbow, Throwing Arms and Essence Repeater (CLASSES.md:142-155).
10. **Now vs later:**
    - Now: route kills by projectile; let arrow hits on pooled mobs pay Critical Strike hit XP.
    - Later: crossbow reload, the archetypes, Ranger and Marksman.

### Martial Expertise
**Status:** partial.
- Kill XP and unarmed Power are reachable.
- Knockback resistance and disarm compute values that nothing uses.
- Unarmed stays far weaker than any weapon.

1. **XP source:** K1 with an empty main hand ("AIR", `skills.yml`:69-76, priority 100). It also catches, wrongly, every kill whose weapon has left the hand: thrown tridents and off-hand bows (X4).
2. **Balance and protection:** same as Blade Mastery.
3. **Reachable effects:** unarmed Power, +1/level (`attributes.yml`:32).
   - The fist's base damage is 1, so ME 100 (×6.01) hits for about 6.0. That roughly equals an iron sword at BM 1 (6 × 1.06) and is 1/8 of a netherite sword at BM 100 (48) (computed).
   - Vanilla's 10-tick hurt immunity stops fists turning their fast swing into more hits.
   - There is no unarmed attack-speed stat (`attributes.yml`:111-120).
4. **Incomplete or disconnected:**
   - **Knockback resistance.** `processDefenderSkills` only appends a trace: "knockback is handled by vanilla — this trace is for debug visibility only" (`CombatSkillProcessor`:86-95). Curve: 1.7% → 26% (Computed).
   - **Disarm.** It sets `disarmTriggered` on PvP hits (:122-125, :239-249), but nothing reads `isDisarmTriggered` (`CombatContext`:174 has no caller). `disarm-duration-ticks` is loaded and never read (`CombatConfig`:217).
   - **`MartialExpertiseSkill.applyEffect`** is never called. Its disarm would remove the caster's own weapon (:46-58), and its knockback resistance adds to the base value on every call (:24-28).
   - The skill page marks both effects as Not active (`SkillDefinitions`:209-212), which is honest.
5. **Attributes:** Power (unarmed) only. Knockback resistance from Fortitude + ME + HAT is proposed, not built (ATTRIBUTE_EXPANSION.md:40).
6. **Progression rewards:** one multiplier on a 1-damage base. No unlock, technique or class gate (Brawler is design only).
7. **Dependencies:** martial weapons (Gauntlets, Quarterstaff, War Fan, Chain Whip, Soul Lash: WEAPONS.md:62, 68-70, 73; CLASSES.md:139-150), the Brawler class (CLASSES.md:95), stagger and combo systems, and the PvP pass for disarm.
8. **Missing gameplay:** a viable unarmed build, working knockback resistance, and a PvE version of disarm (stagger or interrupt).
9. **Future interactions:** Brawler (ME 10); disarm synergy with Spear/Halberd (CERBERUS_DESIGN_PLAN:807).
10. **Now vs later:**
    - Now:
      - Knockback resistance as a transient vanilla attribute modifier, using the `SecondaryEffects.set` pattern (:80-90).
      - An unarmed damage term through `Stat.DAMAGE` for UNARMED, which the engine already supports (`AttributeCalculator`:37-38).
      - Fix routing, so only real fist kills train it.
    - Later: gauntlets and martial weapons, Brawler, disarm in the PvP pass.

### Heavy Armor Training (shown as "Heavy Armor Mastery")
**Status:** partial.
- Its effects are reachable.
- Its designed XP source pays 0 for most real hits.
- Its practical XP source is killing mobs with tools.
- Its bonuses apply whether or not heavy armor is worn.

1. **XP source:**
   - **(a) Hits taken in heavy armor (K3).** Any counted hit while wearing at least one iron, diamond or netherite piece (`PlayerDefenseManager`:140-151) pays min(0.5 × damage taken, 10) (`CombatSkillProcessor`:34-36, :276-279).
     - "Damage taken" is the final damage, after resistance, armor and the defense bar (÷ health scale in PvP) (`VirtualHealthListener`:227-231, :426-428).
     - If nothing gets through, 10% of the incoming hit counts instead.
     - Every cause counts: mob melee, PvP, fall, fire, lava, drowning, cactus.
   - **(b) Kill fallback.** A kill holding an item no rule matches (pickaxe, shovel, hoe, block, shield, food...) while wearing heavy armor pays full K1 XP (`MobKillListener`:135-138).
2. **Balance and protection:** whole-point truncation (`SkillProgression`:88-92) means a hit under 2 final damage pays nothing.
   - Worked example (computed, Normal difficulty): a Common zombie at L1 or L100 hits for 3 × 1.02 or 3 × 1.885. In an iron set at HAT 1 (17.7% armor) with a full bar (the bar takes 70%), 0.76 or 1.40 gets through → **0 XP**. That rises to 1 XP below half a bar and 2 with an empty bar.
   - The harness had to use a Power V bow and `/damage 20`, which it describes as "big enough that Heavy Armor's XP (half the damage taken, whole points) shows" (`tools/playtest/progression.js`:894, :956).
   - No idle or self check (X6). One iron boot qualifies.
   - The fallback pays more for fighting with a pickaxe than for wearing armor.
   - HAT 20 needs 13,250 maximum-XP hits.
3. **Reachable effects:** `updateDefense` recalculates on join, armor change and level change (`CustomArmorListener`:30-51; `AttributeEffects`:40-46).
   - **(a) Armor bonus.** +1.5% at L1, 5.1% at L10, 10.2% at L50, 15% (cap) at L100 (`combat.yml`:205-209). It is added whatever is worn (`PlayerDefenseManager`:72, :81), under the 40% armor limit.
   - **(b) Defense-bar points.** 0.9 at L1, 1.9 at L10, 5.9 at L25, 31 at L50, 96 at L100, × realm (:190-198; `combat.yml`:210-214). The STEADY preset's log-scale (0.04) is sized for fractions, but this stat is in points, so it is about 0 below L40.
   - **(c) Fortitude +1/level** (armor +0.1%, bar ×(1+0.01·F), resist +0.2% per point) **and Vitality +0.5/level** (`attributes.yml`:39, :57-61).
   - In PvP, (a) and (b) count through the pre-attribute baseline (`pushPvpBaseline`:105-111); (c) does not.
4. **Incomplete or disconnected:**
   - `HeavyArmorTrainingSkill.applyEffect` (:21-27) is never called; `calculateArmorMultiplier` and `isArmor` (:29-69) are unused.
   - `EffectManager.applyHeavyArmorTrainingEffect` (+20 max HP, :131-141) is unreachable.
   - kb-reduction is loaded into `hatKbReductionScaling` (`CombatConfig`:212) and never read.
   - `PlayerManaManager.applyCrossSkillBenefits` (HAT → Defensive Magic mana discount, :382-392) has no caller.
   - The skill page says "XP equal to half the damage, up to 10 per hit" (`SkillDefinitions`:221-224). It omits that the damage is measured after the bar and that fractions are dropped.
5. **Attributes:** Fortitude 1.0/level and Vitality 0.5/level.
6. **Progression rewards:** numbers only. The armor bonus hits the 40% cap around HAT 60-75 in diamond or netherite (20-24% gear + 15% + 10%). The owner already flagged the "weak-feeling Heavy Armor rewards" (`docs/ROADMAP.md`:67, :130).
7. **Dependencies:**
   - Built: the defense pipeline, the bar, Fortitude and Vitality.
   - Not built:
     - an armor weight or mobility trade-off (only the unused `Skills` enum mentions "mobility penalties", `Skills/Skills.java`:9);
     - heavy weapons (Warhammer and Furnace Hammer → HAT, WEAPONS.md:63, :75);
     - Knight and Paladin (HAM 15);
     - the gear system;
     - level-150 mastery, where Heavy Armor Mastery comes first.
8. **Missing gameplay:** a tanking XP loop that rewards absorbing damage, a reason to wear heavy armor, sane bar-bonus units, and knockback reduction.
9. **Future interactions:** Knight and Paladin need HAM 15 (about 58k XP), reachable today mainly through tool kills; Smithing-made armor as an attribute source; the realm-scaled bar.
10. **Now vs later:**
    - Now:
      - Give XP for damage absorbed, or as a share of max HP, with fractions carried over.
      - Gate (a) and (b) to the heavy pieces worn.
      - Fix the bar-bonus log-scale.
      - Retire the tool fallback.
      - Add the idle rule.
    - Later: weight, heavy weapons, Knight and Paladin, mastery to 150.

### Dual Wielding
**Status:** placeholder.
- There is no XP source.
- The skill class is placeholder logic.
- No off-hand combat path exists.
- Its attribute weight is live but frozen at level 1.

1. **XP source:** none from play. There is no award call site and no `skills.yml` rule, only the admin `/cerb skill` and `SkillEditMenu`. The page says "Nothing in the game trains this skill yet" (`GUIs/player/SkillView`:125-126), which is honest.
2. **Balance and protection:** not applicable.
3. **Reachable effects:** +0.5 Agility per level (`attributes.yml`:35). The level never moves, so every player has the same +0.5 Agility.
4. **Incomplete or disconnected:**
   - `DualWieldingSkill.applyEffect` (:16-41) is never called. It holds ×1.2 damage, ×1.1 speed (compounding) and an "additional strike" that is a chat message ("This is placeholder logic", :56-60).
   - `EffectManager.applyDualWieldingEffect` (:117-128) is unreachable.
   - Combat reads only the main hand (`PveOffense.meleeWeapon`:108-110; "Dual Wielding and offhand have no pipeline today", CERBERUS_DESIGN_PLAN:938).
   - The "Next levels" tile previews Agility at levels that cannot be reached.
5. **Attributes:** Agility 0.5/level, inert.
6. **Progression rewards:** none.
7. **Dependencies:** an off-hand hit path (net-new; vanilla has no off-hand attack), the Twin Blades archetype (WEAPONS.md:71; CLASSES.md:148), and an input for off-hand strikes (the class key layer could host one).
8. **Missing gameplay:** everything.
9. **Future interactions:** Twin Blades for Duelist and Assassin.
10. **Now vs later:**
    - Now: don't build it. Keep the "in development" label, and either give the skill an XP source or drop its Agility weight so the stat means something.
    - Later: the off-hand pipeline, with the weapons slice.

### Critical Strike
**Status:** partial.
- XP and effect both reach play.
- Hit XP is unprotected: an armor stand is an endless target, and every target pays the same.
- It levels about 8× slower than the weapon skills.
- Arrows never train it.

1. **XP source:** K2: 2 XP per hit, 8 if the hit was a crit (`CombatSkillProcessor`:31-33, :260-266). It comes from:
   - **(a) Melee and sweep hits** that take HP from any `Mob`'s pool, hostile or passive (`VirtualHealthListener`:245-254, :362).
   - **(b) Any other non-projectile hit by a player** on a non-player LivingEntity (:255-273): spells, ability strikes (`Classes/AbilityCaster`:372-385, `t.damage(amount, p)`), thorns, and non-Mob targets.
     - On this path the "crit" comes from the old PvP roll (:270 → `applyCriticalStrike`:185-201). It changes no damage.
   - Nothing from arrows (:452) or from PvP hits.
2. **Balance and protection:** nothing beyond the attack cooldown and hurt immunity. No grey rule, no per-mob budget (class casts got `CastXpLedger`; hits didn't), and animals count.
   - **Armor stand farm.** An armor stand hit more than 5 ticks after the previous hit fires the Bukkit damage event, then only wobbles (bytecode: `ArmorStand.hurtServer` calls `handleNonLivingEntityDamageEvent`, then the `lastHit > 5` branch).
     - It is a LivingEntity, not a Mob, so it takes the unpooled branch and pays 2 (or 8) XP per hit, indefinitely (`VirtualHealthListener`:304-306, :319, :267-272).
     - Class code excludes armor stands (`AbilityCaster`:632; `FireballSpell`:106); this path doesn't.
   - **Pace (computed):** about 23-32 Critical Strike XP per equal-level kill (7.5 swings) against 184-334 kill XP for the weapon skill. Critical Strike 50 takes about 65k kills; Blade Mastery 50 takes about 7.8k.
3. **Reachable effects:**
   - **PvE crit chance and multiplier.** Agility +1/level (`attributes.yml`:34) drives both from floor(Agility) (`AttributeCalculator`:61-70), applied per mob hit, arrows included (`PveOffense`:70-78), inside the ×6 offense budget (`AttributeSnapshot`:112-115). Computed:
     - baseline Agility 2: 13.8% ×1.55;
     - CS 50: 38% ×2.09;
     - CS 100: 72% ×3.16.
   - **Speed stats from Agility:** attack +0.1%, draw +0.1% and movement +0.05% per point. At CS 100: +8.3%, +9.1% and +4.5%.
   - **PvP:** the old roll uses the Critical Strike level: 12.5% ×1.53 at L1 → 72% ×3.16 at L100.
   - **Class entry:** Duelist (CS 5).
4. **Incomplete or disconnected:**
   - `CriticalStrikeSkill.applyEffect` (:14-30, marked "Example") is never called.
   - The page's "8 for a critical hit" also counts spell "crits" that deal no extra damage (`SkillDefinitions`:231-236).
5. **Attributes:** Agility 1.0/level, the main Agility source (Dual Wielding 0.5 frozen; Navigation and Stealth 0.25 each).
6. **Progression rewards:** numbers plus the Duelist gate. Crits are visible as marked damage numbers; there are no unlocks.
7. **Dependencies:**
   - Built: the engine, damage numbers, classes (Feint's forced crit is the class's, not the skill's).
   - Not built: Keen and Savage affixes and their stacking rule (CERBERUS_DESIGN_PLAN:829; `docs/design/OPEN_DECISIONS.md`:98); Assassin and Marksman.
8. **Missing gameplay:** crit training for ranged players, XP that depends on the target (level and tier), and anti-farm rules.
9. **Future interactions:** Assassin (Stealth 10 + CS 10) and Marksman (RP 15 + CS 10). Today a pure archer must melee to qualify for Marksman.
10. **Now vs later:**
    - Now:
      - Pay hit XP only for hits that take HP from a pooled Mob's pool.
      - Scale it by the mob's level, like kill XP.
      - Cap it per mob, reusing `CastXpLedger`.
      - Count projectile hits.
    - Later: affixes, Assassin and Marksman.

### Survival
**Status:** partial.
- XP and effects are reachable.
- At 1 XP per hit its level barely moves in real play.
- Its resistances face vanilla-sized environmental damage.

1. **XP source:** K3: +1 per counted hit taken, any cause (`CombatSkillProcessor`:37-38, :281-282). Nothing else pays it.
2. **Balance and protection:** one XP per hit, whatever the hit's size; at most 4 counted hits per second.
   - Survival 5 takes 2,292 hits, 10 takes 17,857, 20 takes 132,501, 50 about 1.83 M (computed).
   - No idle or self check (X6), so mob traps, cactus, falls and friends hitting each other all count. The rate is so low that farming is pointless, and so is honest play.
3. **Reachable effects:** pushed by `updateDefense` (:172-184), under the 50% resist limit and the 75% total cap. In PvP the resistances count through the pre-attribute baseline.
   - **Fire resistance:** 1%/level, cap 30%, times each fire type's share; lava gets half (`PlayerDefenseManager`:154-156; `combat.yml`:33-39; `PlayerVirtualHealthManager`:462-471). Played: fire 7/7.
   - **Cold resistance:** 1%/level, cap 20%; only powder-snow FREEZE reaches players.
   - **Drowning resistance:** 1%/level, cap 20%.
   - **Vitality +1/level** (max HP +1%) **and Fortitude +0.5/level** (`attributes.yml`:38).
4. **Incomplete or disconnected:**
   - `SurvivalSkill.applyEffect` is never called; `applyEnvironmentalResistances` computes values and discards them ("Placeholder logic", :32-43).
   - `enhanceSurvivalConsumables` → `CraftingManager.applyFoodQualityBonus` (:46-49) is never called.
   - The page claims cold resistance covers "frost spells" (`SkillDefinitions`:310-311), but no frost damage reaches players today: CRYOMANCY comes only from class abilities, which hit only mobs (`AbilityCaster`:644-648).
5. **Attributes:** Vitality 1.0 and Fortitude 0.5 per level.
6. **Progression rewards:** numbers only. The resistances barely matter, because falls, fire and lava deal vanilla amounts against a 100–300 health pool (`docs/STATUS.md`:410).
7. **Dependencies:**
   - Hazards (cold, heat, storms; design only: CERBERUS_DESIGN_PLAN:497, 559, 602, 618).
   - Food and hunger (not built).
   - Deployables (`docs/design/GEAR.md`:192-193).
8. **Missing gameplay:** an actual survival loop (exposure, temperature, foraging, camps, food) and XP that scales with danger.
9. **Future interactions:** Ranger, Berserker and Druid need Survival 10, about 17,857 hits (CLASSES.md:93, 96, 109). Ranger is one of Step 6's next six classes.
10. **Now vs later:**
    - Now:
      - XP from damage as a share of max HP, with fractions carried over (realm-safe).
      - XP for enduring hazards alive.
      - The idle rule.
    - Later: hazards, temperature and food.

---

## Group table

| Skill | Status | XP source (reachable) | What its level does (reachable) |
|---|---|---|---|
| Blade Mastery | functional (X1–X5 open) | sword kills | sword Power, sword attack speed; PvP +dmg; Warrior/Duelist gate |
| Weapon Mastery | functional (X1–X5 open) | axe, mace, trident, spear kills | Power on its 4 weapons +0.25 all; attack speed; PvE lifesteal on all hits; PvP +dmg; Warrior gate |
| Ranged Precision | functional (X1–X5 open) | bow, crossbow kills | bow Power; bow draw speed (not crossbow) |
| Martial Expertise | partial | empty-hand kills (plus misrouted thrown-trident kills) | unarmed Power on a 1-damage fist; knockback resistance and disarm inert |
| Heavy Armor Training | partial | hits in heavy armor (whole points, usually 0) + tool-kill fallback | armor %, bar points (≈0 below L40), Fortitude, Vitality; not gated to heavy armor |
| Dual Wielding | placeholder | none | +0.5 Agility, frozen at level 1 |
| Critical Strike | partial | 2/8 XP per melee/spell hit, unprotected | Agility → PvE crit chance/multiplier, small speeds; PvP crit; Duelist gate |
| Survival | partial | +1 per hit taken | fire/cold/drowning resistance, Vitality, Fortitude (level barely moves) |

## Highest-value missing loops, by value vs effort

1. **The defensive loop: Heavy Armor + Survival.** High value, small effort.
   - Both skills' designed XP almost never pays: HAT gets 0 from typical hits, Survival needs 17,857 hits for level 10.
   - Together they are 2 of the 3 Vitality/Fortitude skills.
   - They gate five planned classes (Knight, Paladin, Ranger, Berserker, Druid), two of them in Step 6.
   - The owner has already flagged the Heavy Armor rewards.
   - **Fix:**
     - XP for damage taken or absorbed, as a share of max HP, with fractions carried over;
     - Heavy Armor bonuses gated to the heavy pieces worn;
     - the bar-bonus units;
     - retire the tool-kill fallback;
     - one idle rule (open decision 8).
2. **Kill-XP integrity for the four weapon skills (X1–X5).** High value, small effort.
   - This is the main progression loop, and it feeds the class kill share.
   - **Fix:**
     - route by the weapon class of the last player hit (tag it on the mob);
     - no or reduced XP for spawner, bred, no-AI, creative and PvP kills (reuse the classes' `Enemy` rule);
     - fix the name-pattern XP multipliers.
3. **Critical Strike hit XP.** Medium-high value, small effort.
   - Pay only for hits that take HP from a pooled Mob's pool, which closes the armor-stand farm.
   - Scale by mob level, cap per mob (`CastXpLedger`), count arrows.
   - This fixes the ~8× lag behind weapon skills and makes Marksman and Assassin reachable for archers.
4. **Martial Expertise viability.** Medium value, small-medium effort: vanilla knockback resistance (proposed in ATTRIBUTE_EXPANSION) and an unarmed damage term.
5. **Dual Wielding.** Low value now, large effort: wait for the weapons slice (off-hand pipeline and Twin Blades).

## Skill-page claims the code doesn't fully back

- **Heavy Armor:** "XP equal to half the damage, up to 10 per hit". The damage is measured after armor and the bar, and fractions are dropped, so most hits pay 0. "Fighting in iron, diamond and netherite armor": the bonuses apply without such armor.
- **Critical Strike:** "8 for a critical hit" includes spell rolls that crit nothing.
- **Survival:** cold resistance "and frost spells": nothing frost hits players.

The "Not active yet" and "Against players only" markers for Blade Mastery, Weapon Mastery, Martial Expertise, Ranged Precision and Dual Wielding match the code.

## Unconfirmed items, and how each was checked

| Item | How checked | Not done, and how to confirm |
|---|---|---|
| Armor-stand Critical Strike farm | plugin source + server bytecode (`ArmorStand.hurtServer`) | Not played. Punch a stand every 0.4 s, 20 times, and read Critical Strike XP. |
| Heavy Armor 0 XP for typical hits | computed from the pipeline; consistent with the harness's own comment (`progression.js`:956) | Not measured with natural zombie hits. |
| Thrown-trident or swapped-item kill routing (X4) | read | Not played. |
| PvP kills pay skill XP (X5) | read + `javap` (`PlayerDeathEvent` shares EntityDeathEvent's handler list) | Not played. |
| Name-tag ×4 XP (X3) | read (`MobRegistry`, `mobs.yml`) | Not played. |
| Creative-mode XP (X2) | grep: no game-mode checks in any XP path | Not played. |
| Self-inflicted non-projectile hits pay defensive XP (X6) | read | Not played. |
| Pace numbers | computed from the code's formulas; swings per equal-level kill (7.5) taken from `docs/STATUS.md` | — |

**Housekeeping.** These invite false readings of what a skill does; retire them:
- the 8 dead `applyEffect` bodies;
- `EffectManager`'s combat branch;
- `bmSpeedScaling`, `hatKbReductionScaling` and `meDisarmDurationTicks`;
- the `Skills` enum.

**Doc drift.**
- `docs/design/CLASSES.md`:91 says Warrior needs BM or WM 10; `classes.yml`:31 says 5.
- The `SkillManager`:414 comment says 19M cumulative XP to 100; the formula gives 13.2M.
