# Level-up and rank-up presentation

*Item 3 of the Progression Depth & Woodcutting milestone (`docs/ROADMAP.md`). What a player sees and hears when a skill levels up, reaches a milestone, or a class ranks up. Vanilla sounds and particles only. Built: `Skills/Celebrations`, `Skills/Celebration`, `Skills/LevelUps`. Checks: `tools/playtest/levelup.js`; status in `docs/STATUS.md`.*

## 1. What the owner asked for

- **Ordinary level-ups:** a brief sound, a small particle effect, which skill rose, its new level and its real attribute or stat gain, in a short notice that doesn't overwhelm the HUD.
- **Milestones** (10, 25, 50, 75, 100: `docs/design/SKILL_PROGRESSION.md`): something more impressive.
- **Class rank-ups:** their own identity, naming the abilities that became available. A rank alone doesn't unlock an ability that has other requirements; it stays locked on its own, and ranking goes on.
- **Simultaneous gains** combined or prioritized; **nothing replayed** on login, data loading or admin corrections; **a player setting** to reduce or disable it.
- Local, bounded, Folia-safe; the action bar's health, Essence, combat messages and XP display preserved. Built so realm breakthroughs, mastery trials and Awakenings can use it later (not built now).

## 2. What a player sees

| | Sound (the player only) | Particles (players nearby see them) | Screen | Chat |
|---|---|---|---|---|
| **Skill level** | The level-up chime, bright | A small burst at chest height (12) | The middle of the HUD bar for 3 s: "▲ Blade Mastery 12 · +1 Power (swords)" | "▲ Blade Mastery reached level 12: Power (swords) 11.8 → 12.8, Attack speed (swords) 3.3% → 3.5%" |
| **Skill milestone** | The chime, lower, and a fanfare | A ring at the feet and a burst (36) | A title: "Apprentice" over "Blade Mastery 10", about 2.5 s | "★ Blade Mastery reached level 10 · Apprentice: ..." |
| **Class rank** | A beacon tone, and an enchanting sound when an ability opens | A ring in the class's colour and a few glyphs (30) | A title only when an ability opens ("Warrior · Rank 3" over "New: Charge"); otherwise the HUD bar | "✦ Warrior rank 3", then "New ability: Charge (active) - equip it in /class", or "Meteor: rank reached; it also needs Elemental Mastery 25" |
| **Ability available** (its last requirement met by a skill) | as a milestone | as a milestone | A title: "New ability" over "Frost Nova (Mage)" | "✦ New ability: Frost Nova (Mage, active) - equip it in /class" |

**The HUD bar keeps its parts.** Health and Essence stay where they are. The notice takes the middle slot. It ranks below an ability's result or refusal and above the XP line. A notice that arrives during an ability message waits for it to end, and the XP line waits for the notice.

**The gain is the real one.** It is the difference the level made to the player's attributes after diminishing returns, computed by the attribute engine itself (`AttributeService.withSkillLevel`, also used by the skill page's "next level" preview). A skill that feeds nothing yet says only its new level.

**Class colours** are data: `color: "#RRGGBB"` per class in `classes.yml` (Warrior red, Mage violet, Duelist gold).

## 3. Combining and limits

- Everything a player earns in the same moment is shown once, one tick later. One sound and one effect play: the strongest of them (a milestone or rank over a level).
  - The HUD line names each item briefly ("▲ Spell Weaving 7 · ▲ Intelligence 7 · ▲ Elemental Mastery 7"), three at most, then "+N more".
  - Every item keeps its own chat line.
- Several levels at once (a big reward) read "Blade Mastery 10 → 13". A milestone crossed on the way gets the milestone presentation.
- Sound, particles and titles play at most once every 1.5 s per player; text always shows. A burst of level-ups can't become a particle storm.
- Nothing runs every tick: one task one tick after a gain, and one-off effects.

## 4. Never celebrated

- **Loading:** joining the server or loading saved data never goes through the award path.
- **Admin corrections** change the numbers quietly; the admin still gets their confirmation:
  - `/cerb skill <player> <skill> level|xp`;
  - the admin panel's skill editor;
  - `/cerb class <player> <class> rank|xp`.

## 5. The player setting

`/levelup full|reduced|off` (alias `/levelups`; with no choice it cycles). It is saved on the player, like `/xpdisplay`:
- **full** (default): everything above;
- **reduced:** the HUD notice, the chat lines and a quiet note; no particles, no titles;
- **off:** the chat lines only, so progress is never invisible.

## 6. Abilities with requirements besides their rank

An ability in `abilities.yml` may list skill levels it also needs, for example `requires: { Elemental Mastery: 25 }` (no shipped ability uses this yet).
- Reaching its rank doesn't open it: the rank-up names what it still needs and its tile shows each requirement. Equipping it is refused with the reason ("Frost Nova is not unlocked yet: it also needs Elemental Mastery 25"). The class keeps ranking.
- When the skill reaches the level, the ability opens and the skill's level-up says so. A passive or keystone already in the loadout starts counting at that moment.
- An ability already in a loadout is never removed because a requirement isn't met (skills may still be loading, or an admin lowered one). It stays in its slot, inert, until the requirement is met again: its key says what it needs ("Frost Nova unlocks at Elemental Mastery 25"). It never blocks another change to that loadout.

## 7. Later systems

A celebration is data: a kind, a tier, a HUD line and its brief part, a title and subtitle, chat lines and a colour. `Celebrations.celebrate` takes any of them.
- Skill levels, skill milestones, class ranks and new abilities use the ORDINARY and MAJOR tiers.
- Realm breakthroughs, mastery trials and Awakenings are meant for a GRAND tier, through the same path with a stronger effect. The tier exists; nothing uses it yet.
