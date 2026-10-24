# Cerberus — Owner's Vision (authoritative direction)

*Stated by the project owner on 2026-10-24. This is the top-level direction every design doc and
implementation decision serves. Where a design doc conflicts with it, the conflict is raised with
the owner (see [VISION_ALIGNMENT.md](VISION_ALIGNMENT.md)); it is not silently resolved either way.*

## What Cerberus is

A massive, **original** Minecraft cultivation RPG: "an enormous cultivation RPG that happens to
exist inside Minecraft — not just Minecraft with extra RPG statistics." Its depth must come from
systems that interact intelligently, not from overwhelming players with numbers and menus.

Three inspirations, none of them to be copied:

| Inspiration | What Cerberus takes from it | What it must not become |
|---|---|---|
| **Chinese cultivation fantasy** (the foundation) | Players start weak and grow to extraordinary power through training, exploration, combat, knowledge, equipment, rare discoveries and **breakthroughs**. Higher realms are genuinely different levels of existence and capability, not just a bigger number. | Not an adaptation of any novel or game; Cerberus keeps its own terminology, mythology, world and identity. |
| **Hypixel SkyBlock** (system depth) | Deep, interconnected progression: skills, large equipment/weapon ecosystems, crafting, gathering, resources, rare loot with meaningful RNG, collections and unlock paths, enhancements and specialization, bosses, long-term challenges, player-driven goals, possibly an economy. Study *why* the systems reinforce each other. | Not a recreation of SkyBlock mechanics; original Cerberus equivalents that serve a cultivation RPG. |
| **Wynncraft** (world and adventure) | A world worth exploring: regions with distinct identities, dangers, enemies, discoveries and rewards; difficulty that rises with capability; quests, encounters, lore, progression tied to the world. | Not a clone; the architecture must support this even though it comes later. |

## Principles

1. **Cultivation is the central progression framework.** Skills, combat, gathering, equipment and
   discoveries all feed the path toward the next breakthrough.
2. **Controlled hyperscaling.** Power reaches enormous magnitudes, but the math stays consistent,
   balanced within the intended progression, and cheap to compute. Keep the ten-realm framework
   and the Western-forward naming ([NAMING.md](NAMING.md)).
3. **One stat foundation.** The Attributes engine combines progression, equipment, cultivation,
   buffs and future content under the agreed balance rules. **No independent stat system per
   feature.**
4. **RNG is exciting, not meaningless.** Rare treasures and exceptional drops exist, but ordinary
   progression never depends on unreasonable luck; effort, preparation, exploration and decisions
   improve outcomes. Avoid hundreds of items that differ only in numerical rarity — prefer distinct
   mechanics, useful combinations and interesting choices.
5. **Scale means quality and depth, not feature count.** Power, content, world, progression, system
   and technical scale (huge numbers without performance problems, math errors or data
   corruption). **50 excellent interconnected features over 500 disconnected ones.**
6. **Originality.** No copied assets, text, branding or unique content from any game. Original
   ideas are welcome but are labelled as proposals until the owner approves them.
7. **Design docs are the starting authority.** Conflicts and missing decisions are identified and
   brought to the owner, not silently replaced.

## Priorities (in order)

1. **Admin Control Panel** — a powerful, secure, extensible development interface; working in-game
   before it is expanded.
2. **Attributes & Progression foundation** — substantially more capable than a basic stat plugin,
   following the cultivation design, controlled hyperscaling and the established boundaries.
3. **A complete, enjoyable initial progression experience** — meaningful advancement through
   combat, skills, attributes, rewards and eventually cultivation breakthroughs, as one coherent loop.
4. Only then: the much larger content ecosystem.

## How the lead developer works

- Keep this vision in mind for architecture and implementation; plan for scale, but don't use
  future complexity as an excuse to overengineer the present.
- Small, functional, independently testable milestones; stable gameplay, meaningful progression,
  maintainable systems, **real in-game verification**.
- Routine engineering decisions are the lead developer's. Changes to the game's identity,
  progression rules, balance philosophy or scope go to the owner with a recommendation.
