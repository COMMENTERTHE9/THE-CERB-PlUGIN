# Vision ↔ Design Alignment (2026-10-24)

*Compares the owner's vision ([VISION.md](VISION.md)) with the design docs in this folder. Nothing
here changes an established rule: conflicts are listed with a **recommendation** for the owner to
accept or reject. "Proposal" marks ideas that are not approved design. Sources: a full read of every
doc (two independent passes, spot-checked).*

## Where the docs already match the vision

- **Controlled hyperscaling** with overflow safety, a short-scale formatter and `BigInteger` only in
  instanced content (POWER_SCALING §1; CULTIVATION_INTEGRATION O1–O9).
- **Ten Western-named realms** over the tier ladder (NAMING.md), realms that differ in kind:
  suppression, one Ascension Trial per realm, flight unlocked by realm.
- **One stat bus**: "the single point where all sources sum and all caps apply" (SYSTEMS_BASE_LAYER §4)
  — built (`Attributes/*`), with skills, buffs and now the realm magnitude feeding it.
- **Quality over count** (DESIGN_PLAN Pillar 5), bosses no single stat solves (_parts/bosses.md §1),
  deterministic paths beside RNG: temper, refinement, rituals, breeding, Stable Breakthrough.

## Conflicts (decisions for the owner)

| # | Conflict | Where | Recommendation |
|---|---|---|---|
| C1 | **Four realm curves.** ×8 is locked (POWER_SCALING §2.2, CLAUDE.md) but CULTIVATION_REALMS §1.1 still uses ×6 with kinks, WEAPONS §1 has its own weapon-power anchors (10 → 1.5B), GEAR §5.1 a `gearRealmMultiplier` (18 at Champion → 2.6M), reused by TALISMANS §4.3. | as listed | One curve. Items take their magnitude from the ×8 curve through an item realm (capped at the wielder's realm); retire the separate tables. |
| C2 | **In-realm power stacks past the realm step.** Minor stages ×1→×10 (CULTIVATION_REALMS §2), the level curve ×5.95, a ×6 gear/offense budget (GEAR §1 wants ×10–40), spirit weapons ~×7 (WEAPONS §5). Multiplied together a realm spans far more than ×8, so a higher realm stops being "a different level of existence" and same-realm fights stop being even. | as listed | One in-realm budget: realm magnitude × level curve (skills/stages, ≤ ×6) × offense budget (gear, crit, bonuses, ≤ ×6). Minor stages *are* the level curve, not an extra multiplier. |
| C3 | **Three suppression models.** | POWER_SCALING §3, CULTIVATION_REALMS §5.2, WEAPONS §7 / GEAR §6 | POWER_SCALING §3 (smooth, saturates at +4 realms, never full immunity). Lock N5–N8. |
| C4 | **Stat systems outside the engine.** Talisman budget sub-caps, gear flat values and a set bonus that raises the armor cap to 55%, weapon refine/spirit multipliers, mount stats, a keyed HP map, badge perks, a Ward damage-reduction layer, trap damage formula; in code, the orphaned Strength/Luck/Magic Find managers. | TALISMANS §4, GEAR §5, WEAPONS §8, MOUNTS §5, DESIGN_PLAN §2.5/§6 | Every source becomes an `AttributeSource`; the engine grows scoped contributions (weapon, element, condition) and secondary stats rather than each system keeping its own caps. |
| C5 | **Items differ mostly by numbers.** ~263 weapons / ~265 gear / ~260 talismans are archetype × realm × variant; affixes that differ only in size (+8/15/30%); drops roll a rarity tier; PRIMORDIAL crafts succeed ~1 in 15; the only pity is a tier floor. | WEAPONS §3–4, GEAR §4–5, TALISMANS §5, DESIGN_PLAN loot | Realm becomes an item field, not catalogue entries. First slice: ~8 weapon archetypes with distinct actions, affixes that change behaviour, targeted drop tables with per-item pity, known recipes craft deterministically, "pay to remove variance" (like Stable Breakthrough). |
| C6 | **A survival world with stronger mobs.** "Distance is destiny … no region editor"; vanilla world generation "interpreted"; points of interest are overlays plus a few fixed coordinates. | DESIGN_PLAN Pillar 1, World §2/§5 | Keep distance-as-difficulty as the base, add a data-driven **Region registry** (authored bounds, identity, landmarks, hubs, later quests) over the band resolver. |
| C7 | **Cultivation is not the spine in the docs.** Families are "the spine", gear "the balance lever", cultivation is build Step 6.5 after the big gear build. | DESIGN_PLAN Exec Summary/Pillar 3, README §4 | Make realm the root; families become Orders that feed it; bring the first breakthroughs forward (see "First playable loop"). |
| C8 | **One ladder for realms, item rarity and PvP ranks.** Items use Common…Legendary names and colours. | DESIGN_PLAN Cross-Domain, Bestiary §1 | Give item grades their own vocabulary so realm names keep their meaning. |
| C9 | **Superseded rules still in the master plan** (anti-power-creep Pillars 3/7, "5,000 cap sacred"). | DESIGN_PLAN | Add "superseded by CULTIVATION_INTEGRATION" banners (no rule change). |
| C10 | **Originality.** The Artifact Bag is described as "the Hypixel SkyBlock accessory-bag model", with a Talisman/Ring/Artifact/Relic chain; mounts and spirit weapons level from an XP share like SkyBlock pets; "Reforge"; xianxia band names (Body Tempering, Qi Condensation, Nascent Soul) and pinyin names (Jian, Tian'e, Báiyún, Wǎnglǎng) against NAMING.md. Term collisions: "Qi" (cultivation XP) vs Essence (qi→Essence, and the mana attribute); "Mastery" (dao→Mastery) vs Mastery Level. | SYSTEMS_BASE_LAYER §2–3, GEAR §4, TALISMANS §3, WEAPONS §2/§6, MOUNTS §3.4 | Redesign the artifact storage idea into something Cerberus-specific; rename per NAMING.md; pick a distinct name for cultivation progress. |
| C11 | **Docs contradict each other.** Family token names (two sets); currency Marrow vs Spirit Stones; Convergence needs all five keyed sets while single-family membership is recommended; MOUNTS has two bond formulas and depends on the removed `/region` system. | as listed | Resolve in the constants/canon pass. |

## Gaps (the vision needs, the docs barely cover)

Lore (what the Echo and Cerberus are) · quests and NPCs (only "quest-only drop" mentions) ·
**collections** (not designed anywhere) · exploration rewards and secrets · targeted RNG mitigation ·
economy and currency · skill specialization choices · the starting realm (players start "weak", but
every mob near spawn is Awakened-tier) · player health anchor (100 built vs 200 designed) · mob
damage still on the old formula (not realm-scaled) · environmental damage at high realms (absolute,
should be relative) · Fortune feeding loot · gathering XP sources (19 skills have none) · the v1 cut line.

## First playable loop (proposal)

The docs have onboarding beats (_parts/loops.md §5, §8) but no buildable cut line. Proposal for the
"complete initial progression experience": **Mortal → Awakened → Adept** in the first two bands. Kill
and gather → skills feed attributes (built) → earn cultivation progress → ~10 deterministic recipes →
**First Stirring** (the tutorial trial, cannot fail) and the **Sand Trial** → the realm step (the realm
magnitude layer is built and verified, default off-path until breakthroughs exist).

## Recommended decision order

1. C1 + C2: one curve, one in-realm budget (unblocks every number).
2. Starting realm and the player health / mob damage anchors (constants treaty).
3. C3 suppression model.
4. The v1 cut line (first playable loop above).
5. C5 item philosophy before any weapon/gear authoring; C6 regions before world content.
