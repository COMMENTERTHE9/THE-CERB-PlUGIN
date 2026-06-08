## Bosses & Encounter Design

### 1. Encounter Design Philosophy

A Cerberus boss is not a vanilla mob with a fat HP bar. It is a **stateful, multi-system puzzle** that interrogates whether a player has actually engaged with Cerberus's mechanics: the 3-Layer Defense, Mana economy, Elemental Infusion, and the 44 combat/magic/utility skills. The core design rule:

> **No single stat solves a Cerberus boss.** A pure-tank with 5000 HP, a pure-mage with a 1000 mana pool, and a glass-cannon crit build should each *fail differently* against the same boss, and each must adapt rather than out-stat it.

**How a Cerberus boss exercises each existing system:**

| System | How bosses use it |
|---|---|
| **3-Layer Defense** (elem resist → armor ≤40% → defense bar) | Bosses deal **mixed damage packets** in one attack (e.g. 60% Fire + 40% physical) so a single resistance never zeroes a hit. "Bar-breaker" attacks deal flat **defense-bar-only** damage that ignores armor cap, punishing players who never refill the bar. "Pierce" attacks bypass the bar entirely and hit Virtual Health, punishing players who hide behind the bar and never dodge. |
| **Mana** | Some bosses apply **Mana Burn** (drain MP on hit) and **Silence fields** (no spellcasting in zone), forcing martial players to carry the fight. Reservoir Inversion bosses (see ASH) literally swap your HP and MP mid-fight, weaponizing your build against you. Mana shields are explicitly counterable by **shield-shatter** attacks. |
| **Elemental Infusion** | Every boss has an **affinity** (resist its own element ~75%) and a **vulnerability** (take +50% from the opposing element per the Infusion wheel: Fire↔Water, Ice↔Fire, Lightning↔Earth, Earth↔Lightning, Water↔Void, Void↔Water). Players who infuse the *wrong* element waste a phase; the fight teaches the wheel. |
| **Combat skills** | Telegraph windows are tuned so **Ranged Precision** can punish from safe range, **Critical Strike**/**Blade Mastery** reward burst inside short openings, **Heavy Armor Training** gates whether bar-breakers one-shot you, and **Martial Expertise** dodge/parry timing maps to telegraph cadence. **Dual Wielding** vs two-handed is a real choice on tight DPS checks. |
| **Utility skills** | **First Aid** out-of-combat ticks gate sustain on long fights; **Alchemy** potions counter status (Antidote vs Mire poison, Warmth vs Frost slow); **Survival** reduces environmental arena damage; **Navigation** unlocks Blink-Gate fast access to remote arenas; **Trap Mastery** lets players pre-place Cerberus Traps in the arena (see Summoning). |

**Distinctiveness pillars (every boss must hit at least 3):**

1. **Telegraph-and-window** — every lethal attack is readable (particle/sound/block cue) with a fixed wind-up; reaction, not RNG, decides outcome.
2. **Resource conversation** — the fight forces an MP/bar/HP trade-off the player must actively manage, not autopilot.
3. **Phase identity** — each phase changes the *question being asked* (DPS check → survival check → add-management → elemental swap), not just the number.
4. **Counterplay over gear-check** — there is always a *skill* answer, not only a *stat* answer.
5. **Arena as a character** — the floor, hazards, and adds are part of the boss, not set dressing.

---

### 2. Tier & Difficulty Ladder

Bosses occupy **EPIC and above** on the unified tier system. Each tier band defines the boss's base HP multiplier (feeding the existing `(base + level*hpPerLevel)*tierMult` formula, but **bosses ignore the 5000 cap** — see Folia tuning), its level band, and the *intended* gear tier of the player.

| Boss Tier | Mob Level Band | Boss HP (solo baseline) | Enrage Timer | Player Gear Expected | Zone Band |
|---|---|---|---|---|---|
| **EPIC** | 30–50 | 25,000 | 6:00 | RARE–EPIC | Inner zones (mini-bosses, family entry) |
| **LEGENDARY** | 50–70 | 70,000 | 5:30 | EPIC–LEGENDARY | Family heartlands |
| **PERFECT** | 65–80 | 150,000 | 5:00 | LEGENDARY–PERFECT | Family apex |
| **MYTHIC** | 80–90 | 320,000 | 4:30 | PERFECT–MYTHIC | World-tier raids |
| **GODLY** | 90–100 | 650,000 | 4:00 | MYTHIC–GODLY | Convergence raids |
| **PRIMORDIAL** | 100 (capped) | 1,200,000 | hard 3:30 | GODLY–PRIMORDIAL | Endgame singletons |

**Intended progression order** (gates explained in §6):

```
SAND (EPIC) → TIDE (LEGENDARY) → ASH (PERFECT) → FROST (MYTHIC) → MIRE (MYTHIC)
        ↓ (all 5 family-keyed armor sets collected)
   CONVERGENCE (GODLY) → THE WARDEN OF CERBERUS (PRIMORDIAL)
```

Each family has a **3-rung internal ladder**: Herald (EPIC entry mini-boss) → Matriarch/Patriarch (the family's main LEGENDARY+ boss) → the family contributes one component to the GODLY Convergence raid. The Spaceman Armor meme reward drops *only* from the PRIMORDIAL Warden.

---

### 3. Boss Roster Overview

| Boss | Family | Tier | Zone | Element (affinity / vuln) | Signature Mechanic |
|---|---|---|---|---|---|
| **Grit-Herald Khamûn** | SAND | EPIC | Sunken Dunes | Earth / Lightning | Sand-shroud (sight-blocking sandstorm + burrow ambush) |
| **Dune-Matriarch Sa'irah** | SAND | LEGENDARY | Glasspike Wastes | Earth / Lightning | Glassing — vitrifies the floor into shatter-tiles |
| **Tide-Caller Nessuvar** | TIDE | LEGENDARY | Drowned Trench | Water / Void | Whirlpool arena + summons Sea Monsters |
| **Cinder-Patriarch Vol'Khar** | ASH | PERFECT | Emberfall Caldera | Fire / Ice | Reservoir Inversion — swaps your HP/MP mid-fight |
| **Frost-Empress Lysithea** | FROST | MYTHIC | The Rime Vault | Ice / Fire | Absolute Zero — freezes the defense bar solid |
| **Bog-Mother ULGRA** | MIRE | MYTHIC | The Sump | Void / Water | Entropy plague — corrodes gear durability live |
| **The Convergence** | All 5 | GODLY | Echo Nexus | Cycles all 6 | Rotating family phases; demands all 5 keyed sets |
| **The Warden of Cerberus** | — | PRIMORDIAL | The Last Gate | None (adaptive) | Learns/locks the last skill you used |

---

### 4. Detailed Boss Concepts

#### 4.1 Grit-Herald Khamûn — SAND, EPIC (Tutorial-Boss of the boss system)

- **Zone:** Sunken Dunes. **Level:** 40. **HP (solo):** 25,000. **Element:** Earth affinity (75% Earth resist), Lightning vulnerability (+50%). **Enrage:** 6:00 → permanent +100% damage and stacking Sand-Lung (1% max-HP DoT/sec).
- **Arena:** Circular 30-block sand bowl ringed by glass pillars (line-of-sight breakers / LOS lightning rods — Lightning infusions arc off them for bonus AoE, teaching the vuln).
- **Role:** Teaches telegraph-reading, the elemental wheel, and bar management at low stakes.

**Phases:**
- **P1 (100–60% HP) — The Reading.** Khamûn does telegraphed **Sand Lances** (1.0s wind-up, line AoE, mixed 70% physical / 30% Earth, hits defense bar). Every 15s, **Burrow** (vanishes 2s, re-emerges under the nearest player — a 0.6s shadow-on-ground telegraph). Soft check: out-DPS the Sand-Lung stacks.
- **P2 (60–25%) — The Storm.** Spawns a sandstorm: vision cut to ~6 blocks, **Survival** skill reduces the storm's 0.5%/s chip. Summons 4 **Grit-Wraiths** (level 30 adds). Players must kill adds *or* LOS-break behind pillars; ranged builds shine.
- **P3 (25–0%) — Collapse.** Floor sinks into a funnel (Whirlpool-of-sand pulling players center). Khamûn channels **Sandblast** (3.0s, full-arena, **bar-breaker** — drains the entire defense bar then overflows to HP). Counter: stand behind a pillar (LOS) or pop a Mana shield. This is the player's first "the bar alone won't save you" lesson.

- **Loot:** SAND Herald Token x1 (ritual currency), Earthbreaker Mace (EPIC, Earth infusion), 30% Sand-Keyed Helm (entry piece of SAND armor set), Grit -Nomicon page (1 of 5).

---

#### 4.2 Dune-Matriarch Sa'irah — SAND, LEGENDARY (family apex)

- **Zone:** Glasspike Wastes. **Level:** 60. **HP (solo):** 70,000. **Element:** Earth / Lightning vuln. **Enrage:** 5:30.
- **Arena:** 40-block glass plateau. The defining gimmick: Sa'irah **vitrifies floor tiles** — stepped-on tiles after 1.5s become **shatter-glass** that breaks on the next dash, dropping players into a damaging sand pit (escape via climb, costs a phase of DPS).

**Phases:**
- **P1 — Glassing.** Telegraphed cone breath vitrifies a 90° arc; arena slowly becomes a minefield. Heavy Armor Training reduces shatter fall damage.
- **P2 (66%) — Mirror Dunes.** Spawns 2 **Glass-Doubles** (illusory clones at 1 HP that mirror her attacks; killing the wrong target wastes the DPS window — a *target-priority* check). Lightning infusion reveals the real one (it doesn't reflect light).
- **P3 (33%) — Hourglass.** Hard soft-enrage: the arena fills with rising sand from the edges inward (shrinking safe zone). Sa'irah gains **Sandstorm Veil** (60% damage reduction unless hit by Lightning — forces correct infusion). DPS race against the shrinking floor.

- **Loot:** Guaranteed **3 Sand-Keyed armor pieces**, Glasspike Bow (LEGENDARY, Lightning), Sa'irah's Hourglass (consumable: brief time-slow trinket), 2 SAND Matriarch Tokens, completes Grit -Nomicon. **Gate:** full Sand-Keyed set required to survive Convergence's SAND phase environmental DoT.

---

#### 4.3 Tide-Caller Nessuvar — TIDE, LEGENDARY

- **Zone:** Drowned Trench (underwater/flooded arena). **Level:** 65. **HP (solo):** 80,000. **Element:** Water affinity, **Void** vulnerability. **Enrage:** 5:30.
- **Arena:** Submerged amphitheater with a central **maelstrom** that pulls players inward (Navigation/Riding mounts resist pull). Ties directly into the existing **Sea Monster spawning** system.

**Phases:**
- **P1 — Undertow.** Periodic **Tidal Surge** telegraph (water rises 3s warning) — players must reach raised coral platforms or take a bar-breaker wave. Mana shields hold one surge.
- **P2 (70%) — The Drowning.** Arena fully floods; oxygen becomes a resource (Survival/Alchemy water-breathing). Nessuvar summons **3 Sea Monsters** (reuses existing spawner) that must be tanked off the raid.
- **P3 (40%) — Maelstrom.** Central whirlpool intensifies; Nessuvar becomes **untargetable except when struck by Void infusion** (which "stills" the water for a 4s window). **Mana Burn** aura drains 20 MP/s — martial builds carry. Soft-enrage: maelstrom radius grows.
- **P4 (15%) — Last Tide.** Single hard DPS check: **Abyssal Geyser** channel (5s) one-shots via pierce damage unless interrupted; interrupt requires a Void-infused heavy hit (Weapon Mastery/Blade Mastery burst).

- **Loot:** Guaranteed **Tide-Keyed armor pieces**, Maelstrom Trident (LEGENDARY, Water+Void hybrid), Drowned -Nomicon, TIDE Tokens, rare **Leviathan Scale** (Smithing mat for keyed-armor upgrades).

---

#### 4.4 Cinder-Patriarch Vol'Khar — ASH, PERFECT (the build-breaker)

- **Zone:** Emberfall Caldera (lava arena). **Level:** 75. **HP (solo):** 150,000. **Element:** Fire affinity, **Ice** vulnerability. **Enrage:** 5:00.
- **Signature:** First boss to weaponize the **Reservoir Inversion** roadmap mechanic — mid-fight he **swaps each player's HP and MP pools**, so a 5000-HP/1000-MP tank becomes 1000-HP/5000-MP and must survive on a glass-cannon body.
- **Arena:** Caldera with rising/falling lava floor; safe basalt platforms shift each phase (Navigation reads the pattern).

**Phases:**
- **P1 — Forge-Heat.** Stacking arena heat: every 10s, +5% Fire damage taken (Survival + Ice infusion of self counters). Telegraphed **Magma Lance** lines.
- **P2 (75%) — Inversion.** Vol'Khar channels **Reservoir Inversion** (4s telegraph, unavoidable). HP↔MP swap for 45s. Now your mana pool is your life total — Mana Regen skill literally heals you; Mana Burn attacks become *lethal*. The fight inverts every assumption. Reapplies each phase.
- **P3 (45%) — Cinderstorm.** Summons **Ash-Echoes** (adds that explode on death for Fire AoE — corpse positioning matters). Vol'Khar gains a **Mana shield** that must be shield-shattered (heavy burst) before he's damageable.
- **P4 (20%) — Pyre Collapse.** Lava floor rises to near-total; tiny safe platforms. Hard DPS check under Inversion. **Ice infusion doubles damage** this phase — wrong element = enrage wipe.

- **Loot:** Guaranteed **Ash-Keyed armor**, Cinderheart Greatsword (PERFECT, Fire), **Vol'Khar's Reservoir** (trinket: voluntary HP/MP swap on demand — a build-defining item), Ember -Nomicon, ASH Tokens. **Gate:** Ash-Keyed set's Inversion-resist stat is near-mandatory for Convergence's ASH phase.

---

#### 4.5 Frost-Empress Lysithea — FROST, MYTHIC

- **Zone:** The Rime Vault. **Level:** 85. **HP (solo):** 320,000. **Element:** Ice affinity, **Fire** vulnerability. **Enrage:** 4:30.
- **Signature:** **Absolute Zero** — periodically *freezes the defense bar solid*, so it can neither absorb nor regenerate for 8s; players must avoid all damage or eat it raw on Virtual Health.
- **Arena:** Mirror-ice floor (low friction — momentum carries dashes; Navigation/Riding control it) ringed by braziers (Fire-infused hits relight braziers, which thaw a safe zone).

**Phases:**
- **P1 — Frostbite.** Stacking slow (Alchemy Warmth potion / Survival counter). Telegraphed **Ice Spear** volleys (Ranged Precision punishes from range).
- **P2 (75%) — Absolute Zero.** Bar-freeze cycle begins (8s frozen / 12s thawed). During freeze, only dodging (Martial Expertise) or Mana shields save you. Spawns **Rime-Wraiths** that must be melted (Fire).
- **P3 (50%) — Blizzard.** Whiteout vision + arena-wide DoT; players cluster in brazier-thawed zones. Lysithea **shatters frozen players** (if you took a hit while a Freeze debuff is active, you take massive bonus damage — a hard "don't get hit while frozen" check).
- **P4 (25%) — Heart of Winter.** She encases herself in an ice shell (damage-immune) that *only Fire infusion cracks*; a non-Fire raid simply cannot kill her — hard elemental gate. Soft-enrage stacks frostbite to a 1%/s max-HP DoT.

- **Loot:** Guaranteed **Frost-Keyed armor**, Rimepiercer (MYTHIC, Ice), **Heart of Winter** (trinket: on-demand defense-bar freeze immunity, 1/min), Rime -Nomicon, FROST Tokens, **Permafrost Core** (Enchanting reagent).

---

#### 4.6 Bog-Mother ULGRA — MIRE, MYTHIC

- **Zone:** The Sump. **Level:** 88. **HP (solo):** 340,000. **Element:** Void affinity, **Water** vulnerability. **Enrage:** 4:30.
- **Signature:** **Entropy Plague** — live, accelerating **gear durability corrosion** (ties to roadmap Entropy Durability). The longer the fight, the worse your gear performs; broken pieces drop your defense layers. This makes ULGRA a *speed/efficiency* boss, not an attrition one.
- **Arena:** Sinking bog of poison pools; **Repairing** skill mid-fight (field-repair anvil mechanic) becomes relevant; Antidote (Alchemy) counters poison stacks.

**Phases:**
- **P1 — Rot.** Poison pools spread; Entropy aura ticks 1 durability/3s on all worn gear. Telegraphed **Spore Burst** (poison cloud, Antidote/Survival mitigates).
- **P2 (70%) — Swarm.** Summons **Mire-Spawn** waves (uses Mob Registry); each spawn that reaches ULGRA heals her 2% — an add-management/positioning phase. **Trap Mastery** shines: pre-placed Spike/Flame traps thin the swarm.
- **P3 (40%) — Decay Acceleration.** Entropy ticks double (1 dura/1.5s). Players who over-relied on gear now feel naked; **First Aid**/skill-based sustain carries. ULGRA gains **Void-Lash** pierce attacks (bypass bar).
- **P4 (15%) — Dissolution.** Arena floor dissolves to islands; ULGRA **regurgitates** corroded gear stats as projectiles. Water infusion strips her Void shell for the kill window.

- **Loot:** Guaranteed **Mire-Keyed armor**, Entropy Scythe (MYTHIC, Void), **Bog-Mother's Carapace** (trinket: entropy-corrosion immunity aura), Sump -Nomicon, MIRE Tokens, **Incorruptible Ingot** (Smithing — crafts entropy-proof gear).

---

#### 4.7 The Convergence — All Families, GODLY (raid)

- **Zone:** Echo Nexus (where all 5 family echoes bleed together). **Level:** 95. **HP (solo, soloing discouraged):** 650,000; tuned for **5–10 players**. **Element:** cycles all 6. **Enrage:** 4:00.
- **Access gate:** Requires **all 5 family-keyed armor sets** present in the raid (the boss's per-phase environmental DoT can only be survived by wearing the matching keyed set during that family's phase — so a raid must field at least one player per family, or rotate).

**Phases (rotating family wheel):**
- **P1 SAND** — Glassing floor + sandstorm; Sand-Keyed set negates DoT.
- **P2 TIDE** — Flood + Sea Monsters; Tide-Keyed set grants water-breathing.
- **P3 ASH** — Mass Reservoir Inversion (whole raid swaps HP/MP); Ash-Keyed set softens it.
- **P4 FROST** — Raid-wide Absolute Zero; Frost-Keyed set keeps the bar thawed.
- **P5 MIRE** — Entropy storm; Mire-Keyed set halts corrosion.
- **P6 CONVERGENCE (sub-25%)** — all five mechanics fire simultaneously at low intensity; pure coordination check. Hard soft-enrage.

- **Loot:** **Convergence -Nomicon** (capstone, combines all 5 family powers), one guaranteed **GODLY armor token** (upgrades any keyed set to GODLY), Echo Crown trinket, large family-token payout. **Gate:** drops the **Warden Key** needed to open The Last Gate.

---

#### 4.8 The Warden of Cerberus — PRIMORDIAL (endgame singleton)

- **Zone:** The Last Gate. **Level:** 100. **HP (solo):** 1,200,000 (tuned 8–10). **Element:** none innate — **adaptive** (mirrors the element you use most, becoming resistant to it). **Enrage:** hard 3:30 (full wipe — no soft-enrage; the Warden is a *check*, not a war of attrition).
- **Signature — Skill-Lock:** every 20s the Warden **locks the last combat/magic skill you used** (disables it for 30s), forcing players to rotate their entire kit. A one-trick build is physically incapable of clearing.

**Phases:**
- **P1 — Mirror.** Warden adapts to the raid's dominant element; raid must diversify infusions (no mono-element zerg).
- **P2 (66%) — The Lock.** Skill-Lock begins. Mana Burn + intermittent Silence fields force martial/magic hybridization.
- **P3 (33%) — Gauntlet.** Sequentially recreates a signature mechanic from each family boss (a Glassing tile, a Maelstrom pull, an Inversion, an Absolute Zero, an Entropy tick) — a "final exam" of every prior boss. Hard 3:30 enrage looms.
- **P4 (10%) — The Gate Opens.** Warden removes all its own resistances but gains a **pierce-only** rapid combo (pure dodge check, Martial Expertise / no defense layer helps) — the player's reflexes decide it.

- **Loot:** **Spaceman Armor** (the meme endgame reward — full PRIMORDIAL set, jetpack hop + space-cosmetic), Warden's Gavel (PRIMORDIAL, adaptive-element weapon), the only source of **PRIMORDIAL upgrade cores**, capstone -Nomicon, Blink-Gate master node unlock.

---

### 5. Summoning, Rituals & Access

Bosses are **not** wandering spawns — they are **summoned**, tying boss access to the Echo Families ritual system, Traps, and the Sea Monster spawner.

| Access method | Mechanic |
|---|---|
| **Family Ritual** | Combine **family Tokens** (from heralds) + a **family reagent** at the family's ritual altar to summon that family's main boss. SAND needs Grit Tokens + glass; ASH needs Ember Tokens + magma core, etc. (ties to roadmap Echo Families rituals). |
| **Mob-kill triggers** | Killing N family-aligned mobs (Mob Registry tag) in a zone builds a hidden "echo charge"; at threshold, the family Herald auto-spawns (ambient gateway into the boss ladder). |
| **Trap-baited summons** | Placing a **Cerberus Trap** (existing system) at a ritual site and letting a family mob trigger it "feeds" the altar — a Trap Mastery shortcut to charge rituals faster. |
| **Sea Monster escalation** | Killing enough Sea Monsters (existing spawner) in the Drowned Trench raises the **Tide-Caller** without tokens (alt access for fishing/sailing players). |
| **Convergence** | Requires one **completed -Nomicon from each of the 5 families** consumed at the Echo Nexus. |
| **Warden** | Requires the **Warden Key** (Convergence drop) + a synchronized 8-player presence at The Last Gate. |

**Ritual cadence / lockouts:** main-boss rituals have a per-player **20-hour lockout** on guaranteed keyed-armor drops (tokens still drop on repeat kills, so farming is allowed but loot-protected). Heralds have **no lockout** (farmable for tokens). Convergence and Warden are **weekly** lockouts.

---

### 6. Progression Gating

Bosses are the spine of mid-to-endgame progression. Each gate is a **mechanic wall**, not just a stat wall:

- **Keyed armor chain:** Each family boss drops its **keyed armor set** (per-family, roadmap Gear Progression). You *need* the matching keyed set to survive that family's Convergence phase DoT → so you must clear all 5 families before the GODLY raid.
- **-Nomicons:** Each family yields one **-Nomicon** (unique item, roadmap). All 5 → Convergence access. Convergence -Nomicon + Warden → capstone. -Nomicons are the explicit "you have mastered this family" key.
- **Element-gate phases:** Lysithea's ice shell (Fire-only), ULGRA's Void shell (Water-only), Vol'Khar's Inversion phase (Ice-only DPS) all **hard-require correct elemental infusion** — gating Elemental Mastery skill investment.
- **Inventory Badge tie-in:** boss clears award **Badge tier progress** (roadmap 20-tier badge), giving cosmetic/prestige proof of clears.
- **Blink Gates:** boss-zone Blink nodes unlock as you clear each family (Navigation), making remote arenas reachable — progression doubles as map unlock.

---

### 7. Folia Tuning — Solo vs Group Scaling & Threading

**Threading model (hard constraint compliance):**

- A boss is **owned by the region thread** of its arena. **All** boss entity ops (movement, target switch, ability casts, summon spawns, damage application) are scheduled on the **owning entity/region scheduler** (`entity.getScheduler()` / region scheduler), never the global/main thread.
- Ability **timers** use `RegionScheduler.runAtFixedRate` anchored to the arena's region, not Bukkit's global scheduler. Phase-transition state machines live in a per-boss object whose mutations only occur inside region-thread tasks.
- **Cross-region reads** (e.g. a player who dashed into an adjacent region) are resolved by re-scheduling onto the *player's* region for that player's damage/effect application, then handing results back — never blocking-read across threads.
- Add summons (Sea Monsters, wraiths, spawn waves) are spawned via the **region scheduler at the spawn location's region**; the boss holds only UUID references, re-resolving entities on its own thread to avoid cross-thread entity access.
- Arena is **anchored within a single Folia region** wherever possible (one-region arenas avoid cross-thread handoff entirely) — a stated arena-design rule.

**Player-count scaling (computed at the encounter's *start*, re-checked at phase transitions):**

| Players | HP multiplier | Boss damage multiplier | Add count | Notes |
|---|---|---|---|---|
| 1 (solo) | ×1.00 (baseline above) | ×1.00 | ×1 | Solo allowed up to MYTHIC; GODLY/PRIMORDIAL strongly group-tuned |
| 2 | ×1.8 | ×1.05 | ×1 | Sub-linear HP so duos aren't punished |
| 3 | ×2.5 | ×1.10 | ×2 | |
| 4 | ×3.2 | ×1.12 | ×2 | |
| 5 | ×4.0 | ×1.15 | ×3 | Convergence design point |
| 6–8 | ×4.0 + 0.7/player | ×1.15 | ×3–4 | |
| 9–10 | ×4.0 + 0.7/player | ×1.18 (cap) | ×4 | Warden design point |

- **HP scales sub-linearly** with players (so each added player still speeds the kill — no "more players = slower" anti-social math), while **damage scales gently** (so adding bodies doesn't trivialize survival but also never one-shots the squishiest). Damage multiplier is **capped at ×1.18** to keep telegraph dodging the real defense.
- **Bosses bypass the 5000 HP cap** of the standard Mob Health Scaling (a `bossUncapped` flag in the boss definition) — boss HP comes from the tier table × player-count multiplier, computed once and stored, not from the per-mob distance formula.
- **Damage packets still flow through the 3-Layer Defense pipeline** unchanged (elem resist → armor ≤40% → defense bar → Virtual Health), so bosses reuse the existing combat math; only the *magnitudes* and *mixed-packet composition* are boss-specific.
- **Enrage/soft-enrage timers** are region-scheduler countdowns started on first damage; on enrage they flip a boss-state flag read by every ability tick.

---

### Open Decisions (Bosses)

- **HP cap bypass scope:** Confirm `bossUncapped` is the right approach vs. a separate boss HP field — and decide the absolute ceiling for PRIMORDIAL (1.2M solo → up to ~6M at 10 players: is that DPS-feasible with GODLY gear, or does the enrage timer need lengthening?).
- **Solo accessibility cutoff:** Is MYTHIC the right solo ceiling, or should GODLY/PRIMORDIAL be *hard* group-locked (refuse to start with <N players)?
- **Reservoir Inversion as a boss mechanic** before it ships as a player system — do we build Inversion for Vol'Khar first (boss-driven) or wait for the roadmap feature? (Dependency call.)
- **Defense-bar freeze (Absolute Zero):** does freezing the bar require new state in the 3-Layer Defense engine, and is an 8s freeze too punishing at MYTHIC?
- **Entropy durability dependency:** ULGRA's live corrosion needs the roadmap Entropy Durability system — gate ULGRA behind that feature, or ship a boss-local stand-in?
- **Keyed-armor hard gate vs. soft gate:** should Convergence *refuse entry* without all 5 keyed sets, or just make those phases brutally hard (soft gate)?
- **Skill-Lock fairness:** does locking the *last-used* skill feel punishing-but-fair, or should it lock a *random* skill / a *category*? Needs playtest.
- **Lockout lengths:** 20h main-boss keyed-drop lockout and weekly Convergence/Warden — too grindy or too generous for the server's population?
- **Sea Monster reuse:** can the existing Sea Monster spawner be parameterized for boss-add duty (level/tier override), or does it need a boss-add variant?
- **Element vulnerability values:** is +50% from the opposing element and 75% self-resist the right spread, or too swingy (mono-element wipe risk on Lysithea/ULGRA)?
- **Add-heal mechanic (ULGRA/Convergence):** does "adds heal the boss if they reach it" create unfun leash/pathing dependence on Folia's region threading?
- **Telegraph delivery without ProtocolLib:** confirm all telegraphs (particles, block displays, sounds, BossBar) are achievable with vanilla Paper/Folia APIs only — no packet lib.
