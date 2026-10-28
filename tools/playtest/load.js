// Reproducible load for performance baselines. LOCAL SANDBOX servers only (it ops test bots).
// Usage: node load.js [bots=6] [seconds=60] [mode=mixed]
//   combat - each bot hits its own no-AI level-50 zombie with a netherite sword (player -> mob path:
//            attributes, Power, crits, mob HP pool, labels, damage numbers, kill XP, saves)
//   hurt   - each bot stands in survival next to an AI level-30 zombie (mob -> player path: defense,
//            bar, HUD, defender XP); healed every 8 s so nobody stays dead
//   hurtheavy - like hurt, but each bot wears full iron at Heavy Armor Mastery 100: on builds with Iron Reprisal
//            (2026-10-28) each landed hit throws its zombie back, and it walks back to strike again. Compare with the
//            build before (the same armor, no throw); armor and level are put back at the end
//   menu   - each bot browses the Player Menu as fast as a hurried player: hub -> Skills -> Combat ->
//            Blade Mastery -> next -> back -> home -> Stats -> back -> close
//   mixed  - the bots split across the three
//   idle   - the bots just stand there (per-player background cost: HUD, regen, saves)
//   menuidle - each bot opens the Player Menu once and leaves it open (an open menu must cost nothing extra)
//   fishmenu - each bot browses to the Fishing skill page as fast as a hurried player: hub -> Skills -> Utility -> Fishing,
//            redraws it twice (Refresh), then back -> back -> close. The same clicks on every build since Fishing
//            (2026-10-27), so the Fishing page's cost compares before and after (docs/design/FISHING_EQUIPMENT.md).
//   abilities - like combat, but each bot is a Warrior/Duelist (Step 5 classes; the controller sets its skills and
//            ranks) and presses its quick keys on a rhythm while fighting (docs/design/ABILITY_CONTROLS.md):
//            right-click (Execute), F (Cleave), Q (Flurry), Sneak + right-click (Feint), with plain hits in between.
//            Many presses land on a cooldown, as in play. (Before 2026-10-25: the same four abilities on Sneak keys.)
//   gather - each bot mines in survival, as a fast miner does (a netherite pickaxe and Haste II, 2-3 blocks a second;
//            no enchantment: this mineflayer can't read 1.21.11 enchantment data and its dig() throws): a 5x3x5 block of
//            stone with coal, iron, copper and gold ore in front of it, refilled when mined out. Drops are picked up as
//            in play. The Gameplay Depth baseline (docs/PERFORMANCE.md).
//   craft  - each bot crafts at its own crafting table as fast as a hurried player: iron ingots and sticks into iron
//            pickaxes, about two a second; finished pickaxes are cleared so the inventory never fills.
//   wood   - each bot chops a small grove in survival (a netherite axe and Haste II): three oak trees, four logs tall
//            under a leaf canopy, refilled when cut down; drops are picked up. The Woodcutting baseline (2026-10-26).
//   felling - like wood, but each bot is at Woodcutting 25 and Sneaks while it cuts, and its trees are six logs tall: a
//            cut fells the trunk when Felling is ready, and single logs fall meanwhile (the rest between fellings). The
//            tree scan, the felling's steps, its merged drops and one protection event per log are the workload.
//   fishing - each bot fishes in survival from the edge of its own pond (7x7, 3 deep, open to the sky), as an AFK farm or a
//            macro does with a Lure III rod: cast, wait for the bite, reel, again, nothing else in between. Fishing is set to 25 at the
//            start (species, treasure and lure rolls all run). The Fishing baseline (2026-10-27).
//   gearmenu - each bot browses the Fishing page's new screens as fast as a hurried player (Fishing 50, four parts owned,
//            so every category is open and its tiles compute their previews from the engine): Equipment -> Rod -> back
//            -> Lure -> back -> Bait Bag -> back -> back -> Journal -> two filters -> the Treasure tab -> back -> close.
//            Only on builds with Fishing equipment (2026-10-27); compare its per-click cost with fishmenu's.
//   autofish - like fishing, but each bot is at Fishing 50 with the Auto-hook Reel in its setup
//            (docs/design/FISHING_EQUIPMENT.md §9): it only casts, and casts again when the reel has caught (its bobber
//            gone) or after 45 s without a bite. One delayed task per bite is the reel's whole cost; compare with
//            fishing (the same ponds, a bot reeling by hand) on the build before and after (2026-10-27).
//   farm   - each bot harvests a 5x5 field of ripe wheat on farmland in survival with an iron hoe, the nearest ripe crop
//            first, about three a second; the field is refilled ripe when bare. Farming is set to 1 every run, so
//            nothing replants and every build does the same work (the Farming baseline, 2026-10-28).
//   widefarm - like farm, at Farming 50 with Sneak held: each cut is a wide harvest (builds with Farming only). The field
//            is planted again after each sweep on every build: from Farming 10 before Replant moved to the hoe, from the
//            hoe after it (the bot gets a Replanting hoe through /cerb farm hoe where that exists, 2026-10-28).
//   cook   - each bot bakes bread at its own crafting table, about two a second (Cooking's crafting path).
//   mealidle - like menuidle, but each bot first eats a Hearty Stew and has Speed I for 10 minutes: an open hub while
//            timed effects run (the Active Effects milestone, 2026-10-28: the head's summary counts down on later builds).
//   effectsmenu - each bot, with the same meal and potion, opens the menu and clicks its head, then Back, then closes, as
//            fast as a hurried player: the head's destination (Stats before the Active Effects milestone, the Active
//            Effects screen after) and the way back.
//   effectsidle - each bot, with the same meal and potion, opens the menu, clicks its head and leaves that screen open
//            (on later builds the Active Effects screen, whose timers count down while it is open).
//   levels - each bot mines diamond ore with its Mining set back to level 1 at the start, so it levels up again and
//            again from real XP: many level-ups at once across the server (the progression feedback's workload).
// All bots stand in a small grid next to world spawn, so they share one Folia region (the worst
// case: one region thread does all the work). A controller bot (CerbSmoke, already op) ops the
// load bots, places them, and cleans everything up at the end (test zombies killed, bots deopped).
// Measure while it runs: Folia `tps` in the console, and/or tools/perf/profile.sh.
const mineflayer = require('mineflayer');
const BOTS = Number(process.argv[2] || 6);
const SECONDS = Number(process.argv[3] || 60);
const MODE = process.argv[4] || 'mixed';
const HOST = { host: '127.0.0.1', port: 25565, version: '1.21.11', auth: 'offline' };
const sleep = ms => new Promise(r => setTimeout(r, ms));
const plain = v => {
  if (v == null) return '';
  if (typeof v === 'string') { try { return plain(JSON.parse(v)); } catch { return v; } }
  const s = JSON.stringify(v);
  const nbt = [...s.matchAll(/"text":\{"type":"string","value":"([^"]*)"/g)].map(m => m[1]);
  if (nbt.length) return nbt.join('');
  return [...s.matchAll(/"text":"([^"]*)"/g)].map(m => m[1]).join('');
};

function join(name) {
  return new Promise((resolve, reject) => {
    const b = mineflayer.createBot({ ...HOST, username: name });
    b.on('kicked', r => console.log(`${name} KICKED`, plain(r)));
    b.on('error', e => console.log(`${name} error`, e.message));
    b.once('spawn', () => resolve(b));
    setTimeout(() => reject(new Error(name + ' did not spawn')), 30000);
  });
}

const counts = {};
const bump = (name, k) => { counts[name] = counts[name] || {}; counts[name][k] = (counts[name][k] || 0) + 1; };

async function combat(b, deadline) {
  b.chat('/gamemode creative'); await sleep(400);
  b.chat('/clear @s netherite_sword'); await sleep(300);
  b.chat('/give @s netherite_sword'); await sleep(800);
  const slot = b.inventory.slots.findIndex((it, i) => it && it.name === 'netherite_sword' && i >= 36 && i <= 44);
  if (slot >= 0) b.setQuickBarSlot(slot - 36);
  while (Date.now() < deadline) {
    const z = Object.values(b.entities).find(e => e.name === 'zombie' && e.position.distanceTo(b.entity.position) < 3.5);
    if (!z) { b.chat('/cerb spawn zombie 50 noai'); bump(b.username, 'spawns'); await sleep(900); continue; }
    await b.lookAt(z.position.offset(0, z.height * 0.6, 0), true);
    b.attack(z); bump(b.username, 'hits');
    await sleep(650);
  }
}

async function abilities(b, deadline, controller) {
  const n = b.username;
  controller.chat(`/cerb skill ${n} Blade_Mastery level 10`); await sleep(500);
  controller.chat(`/cerb skill ${n} Critical_Strike level 5`); await sleep(1500);
  controller.chat(`/cerb class ${n} warrior rank 12`); await sleep(500);
  controller.chat(`/cerb class ${n} duelist rank 12`); await sleep(800);
  for (const e of ['a1 execute', 'a2 cleave', 'a3 flurry', 'a4 feint', 'a5 none', 'p1 battle_hardened', 'p2 precision', 'k juggernaut']) {
    b.chat('/class equip 1 ' + e); await sleep(300);
  }
  b.chat('/class loadout 1'); await sleep(300);
  b.chat('/gamemode creative'); await sleep(400);
  b.chat('/clear @s netherite_sword'); await sleep(300);
  b.chat('/give @s netherite_sword'); await sleep(800);
  const slot = b.inventory.slots.findIndex((it, i) => it && it.name === 'netherite_sword' && i >= 36 && i <= 44);
  if (slot >= 0) b.setQuickBarSlot(slot - 36);
  const key = status => b._client.write('block_dig', { status, location: { x: 0, y: 0, z: 0 }, face: 0, sequence: 0 });
  let k = 0, f = 0;
  while (Date.now() < deadline) {
    const z = Object.values(b.entities).find(e => e.name === 'zombie' && e.position.distanceTo(b.entity.position) < 3.5);
    if (!z) { b.chat('/cerb spawn zombie 50 noai'); bump(n, 'spawns'); await sleep(900); continue; }
    await b.lookAt(z.position.offset(0, z.height * 0.6, 0), true);
    const step = k++ % 6;
    if (step === 1 || step === 3 || step === 5) { b.attack(z); bump(n, 'hits'); }
    else {
      if (step === 0) key(6);                                                  // F: Cleave (A2)
      else if (step === 2) { b.activateItem(); await sleep(50); b.deactivateItem(); } // right-click: Execute (A1)
      else if (f++ % 2 === 0) { key(4); b.swingArm('right'); }                // Q: Flurry (A3)
      else { b.setControlState('sneak', true); await sleep(60); b.activateItem(); await sleep(50); b.deactivateItem(); await sleep(60); b.setControlState('sneak', false); } // Sneak + right-click: Feint (A4)
      bump(n, 'presses');
    }
    await sleep(650);
  }
}

/** A 5x3x5 block in front of bot i (+z side), stone with ore mixed in: what a strip-mining player cuts through. */
function oreCube(controller, x, y, z) {
  controller.chat(`/fill ${x - 2} ${y - 1} ${z - 1} ${x + 2} ${y - 1} ${z + 7} stone`); // a floor to stand on
  controller.chat(`/fill ${x - 2} ${y} ${z - 1} ${x + 2} ${y + 3} ${z + 1} air`);
  controller.chat(`/fill ${x - 2} ${y} ${z + 2} ${x + 2} ${y + 2} ${z + 6} stone`);
  for (const [ore, dx, dy, dz] of [['coal_ore', -1, 0, 3], ['iron_ore', 1, 1, 4], ['copper_ore', 0, 2, 5], ['gold_ore', 2, 0, 6], ['iron_ore', -2, 1, 6], ['coal_ore', 0, 1, 3]]) {
    controller.chat(`/setblock ${x + dx} ${y + dy} ${z + dz} ${ore}`);
  }
}

/** Three oak trees in front of bot i (+z side), `height` logs tall under a leaf canopy, on a floor to stand on. */
function grove(controller, x, y, z, height = 4) {
  controller.chat(`/fill ${x - 3} ${y - 1} ${z - 1} ${x + 3} ${y - 1} ${z + 6} grass_block`);
  controller.chat(`/fill ${x - 3} ${y} ${z - 1} ${x + 3} ${y + height + 2} ${z + 6} air`);
  controller.chat(`/fill ${x - 3} ${y + height - 1} ${z + 2} ${x + 3} ${y + height + 1} ${z + 5} oak_leaves`);
  for (const [dx, dz] of [[-2, 3], [0, 4], [2, 3]]) controller.chat(`/fill ${x + dx} ${y} ${z + dz} ${x + dx} ${y + height - 1} ${z + dz} oak_log`);
}

async function wood(b, deadline, controller, felling = false) {
  const n = b.username;
  const height = felling ? 6 : 4;
  // Every run starts from the same level, so the XP, level-ups and HUD lines it causes are the same from run to run.
  controller.chat(`/cerb skill ${n} Woodcutting level ${felling ? 25 : 1}`); await sleep(400);
  b.chat('/gamemode survival'); await sleep(400);
  b.chat('/clear @s'); await sleep(300);
  b.chat('/give @s netherite_axe'); await sleep(800);
  b.chat('/effect give @s haste infinite 1 true'); b.chat('/effect give @s saturation infinite 1 true'); b.chat('/effect give @s resistance infinite 4 true');
  await sleep(500);
  const slot = b.inventory.slots.findIndex((it, i) => it && it.name === 'netherite_axe' && i >= 36 && i <= 44);
  if (slot >= 0) b.setQuickBarSlot(slot - 36);
  const home = b.entity.position.floored();
  const fill = async () => { grove(controller, home.x, home.y, home.z, height); bump(n, 'groves'); await sleep(900); };
  await fill();
  if (felling) b.setControlState('sneak', true);
  const vec3 = require('vec3');
  while (Date.now() < deadline) {
    let target = null; // the lowest log still standing, nearest trunk first
    for (const [dx, dz] of [[0, 4], [-2, 3], [2, 3]]) for (let dy = 0; dy < height && !target; dy++) {
      const bl = b.blockAt(new vec3(home.x + dx, home.y + dy, home.z + dz));
      if (bl && bl.name === 'oak_log') target = bl;
    }
    if (!target) { b.chat(`/tp @s ${home.x + 0.5} ${home.y} ${home.z + 0.5} 0 0`); await sleep(300); await fill(); continue; }
    try {
      await b.dig(target, true); bump(n, 'logs');
      if (felling && target.position.y < home.y + height - 1) { // the cut log had another above it: felled if that one fell too
        await sleep(150);
        const up = b.blockAt(target.position.offset(0, 1, 0));
        if (up && up.name !== 'oak_log') bump(n, 'fellings');
      }
    } catch (e) { bump(n, 'digfail'); if (!counts.firstError) counts.firstError = { [n]: String(e.message || e).slice(0, 120) }; await sleep(200); }
    if (b.inventory.emptySlotCount() < 3) { b.chat('/clear @s oak_log'); b.chat('/clear @s oak_sapling'); b.chat('/clear @s stick'); b.chat('/clear @s apple'); }
    await sleep(200);
  }
  if (felling) { b.setControlState('sneak', false); controller.chat(`/cerb skill ${n} Woodcutting level 1`); }
}

/** A 7x7 pond, 3 deep, in front of bot i (+z side), with a stone edge to stand on. */
/** A 5x5 field of ripe wheat on farmland in front of the bot, 2 to 6 blocks ahead (refilled when harvested). */
function field(controller, x, y, z) {
  controller.chat(`/fill ${x - 2} ${y - 1} ${z + 2} ${x + 2} ${y - 1} ${z + 6} farmland[moisture=7]`);
  controller.chat(`/fill ${x - 2} ${y} ${z + 2} ${x + 2} ${y} ${z + 6} wheat[age=7]`);
}

/**
 * Harvesting ripe wheat in survival with an iron hoe, the nearest ripe crop first, the field refilled ripe when it is bare
 * and loose items cleared now and then. Farming is set to 1 every run (no replanting, no wide harvest), so every build
 * does the same work. {@code wide}: Farming 50 and Sneak held, so each cut is a wide harvest (builds with Farming only).
 */
async function farm(b, deadline, controller, wide = false) {
  const n = b.username;
  controller.chat(`/cerb skill ${n} Farming level ${wide ? 50 : 1}`); await sleep(400);
  b.chat('/gamemode survival'); await sleep(400);
  b.chat('/clear @s'); await sleep(300);
  if (wide) { controller.chat(`/cerb farm hoe ${n} iron_hoe`); await sleep(900); } // a Replanting hoe (builds with Replant on the hoe)
  if (!b.inventory.items().some(it => it.name === 'iron_hoe')) { b.chat('/give @s iron_hoe'); await sleep(700); }
  b.chat('/effect give @s saturation infinite 1 true'); b.chat('/effect give @s resistance infinite 4 true');
  await sleep(400);
  const slot = b.inventory.slots.findIndex((it, i) => it && it.name === 'iron_hoe' && i >= 36 && i <= 44);
  if (slot >= 0) b.setQuickBarSlot(slot - 36);
  const home = b.entity.position.floored();
  field(controller, home.x, home.y, home.z); await sleep(1000);
  const vec3 = require('vec3');
  if (wide) b.setControlState('sneak', true);
  let lastClear = Date.now();
  while (Date.now() < deadline) {
    let target = null;
    for (let dz = 2; dz <= 6 && !target; dz++) for (let dx = -2; dx <= 2 && !target; dx++) {
      const bl = b.blockAt(new vec3(home.x + dx, home.y, home.z + dz));
      if (bl && bl.name === 'wheat' && Number((bl.getProperties() || {}).age) === 7) target = bl;
    }
    if (!target) { field(controller, home.x, home.y, home.z); bump(n, 'refills'); await sleep(700); continue; }
    try { await b.dig(target, true); bump(n, 'harvested'); } catch (e) { bump(n, 'digfail'); await sleep(200); }
    if (Date.now() - lastClear > 10000) { controller.chat(`/execute at ${n} run kill @e[type=item,distance=..10]`); lastClear = Date.now(); }
    if (b.inventory.emptySlotCount() < 4) { b.chat('/clear @s wheat'); b.chat('/clear @s wheat_seeds'); }
    await sleep(wide ? 600 : 250);
  }
  if (wide) b.setControlState('sneak', false);
  controller.chat(`/cerb skill ${n} Farming level 1`);
}

/** Baking bread at a crafting table as fast as a hurried player (3 wheat each), about two a second (Cooking's crafting path). */
async function cook(b, deadline, controller) {
  const n = b.username;
  b.chat('/gamemode survival'); await sleep(400);
  b.chat('/clear @s'); await sleep(300);
  const p = b.entity.position.floored();
  controller.chat(`/setblock ${p.x + 1} ${p.y} ${p.z} crafting_table`); await sleep(600);
  const vec3 = require('vec3');
  const table = b.blockAt(new vec3(p.x + 1, p.y, p.z));
  const mcData = require('minecraft-data')(b.version);
  const recipe = b.recipesAll(mcData.itemsByName.bread.id, null, table)[0];
  if (!recipe) { console.log(n, 'cook: no recipe'); return; }
  while (Date.now() < deadline) {
    if (b.inventory.count(mcData.itemsByName.wheat.id) < 3) { b.chat('/give @s wheat 63'); await sleep(700); continue; }
    try { await b.craft(recipe, 1, table); bump(n, 'baked'); } catch (e) { bump(n, 'craftfail'); await sleep(300); }
    if (b.inventory.count(mcData.itemsByName.bread.id) > 32) { b.chat('/clear @s bread'); await sleep(200); }
    await sleep(350);
  }
}

function pond(controller, x, y, z) {
  controller.chat(`/fill ${x - 3} ${y - 4} ${z - 1} ${x + 3} ${y - 1} ${z + 9} stone`);
  controller.chat(`/fill ${x - 3} ${y} ${z - 1} ${x + 3} ${y + 3} ${z + 9} air`);
  controller.chat(`/fill ${x - 3} ${y - 3} ${z + 2} ${x + 3} ${y - 1} ${z + 8} water`);
}

async function fishing(b, deadline, controller) {
  const n = b.username;
  controller.chat(`/cerb skill ${n} Fishing level 25`); await sleep(400);
  b.chat('/gamemode survival'); await sleep(400);
  b.chat('/clear @s'); await sleep(300);
  b.chat('/give @s fishing_rod[enchantments={lure:3}]'); await sleep(800); // what an AFK farm uses
  b.chat('/effect give @s saturation infinite 1 true'); b.chat('/effect give @s resistance infinite 4 true');
  await sleep(500);
  const slot = b.inventory.slots.findIndex((it, i) => it && it.name === 'fishing_rod' && i >= 36 && i <= 44);
  if (slot >= 0) b.setQuickBarSlot(slot - 36);
  const home = b.entity.position.floored();
  pond(controller, home.x, home.y, home.z); await sleep(1200);
  const vec3 = require('vec3');
  await b.lookAt(new vec3(home.x + 0.5, home.y - 1, home.z + 5.5), true);
  while (Date.now() < deadline) {
    try {
      await Promise.race([b.fish(), new Promise((_, rej) => setTimeout(() => rej(new Error('no bite in 45 s')), 45000))]);
      bump(n, 'catches');
    } catch (e) { bump(n, 'missed'); if (!counts.firstError) counts.firstError = { [n]: String(e.message || e).slice(0, 120) }; }
    await sleep(400);
    if (b.inventory.emptySlotCount() < 4) { b.chat('/clear @s'); b.chat('/give @s fishing_rod[enchantments={lure:3}]'); await sleep(600);
      const s2 = b.inventory.slots.findIndex((it, i) => it && it.name === 'fishing_rod' && i >= 36 && i <= 44); if (s2 >= 0) b.setQuickBarSlot(s2 - 36); }
  }
  controller.chat(`/cerb skill ${n} Fishing level 1`);
}

async function autofish(b, deadline, controller) {
  const n = b.username;
  controller.chat(`/cerb skill ${n} Fishing level 50`); await sleep(400);
  controller.chat(`/cerb fish part ${n} auto_reel`); await sleep(400);
  b.chat('/gamemode survival'); await sleep(400);
  b.chat('/clear @s'); await sleep(300);
  b.chat('/give @s fishing_rod[enchantments={lure:3}]'); await sleep(800);
  b.chat('/effect give @s saturation infinite 1 true'); b.chat('/effect give @s resistance infinite 4 true');
  await sleep(500);
  const slot = b.inventory.slots.findIndex((it, i) => it && it.name === 'fishing_rod' && i >= 36 && i <= 44);
  if (slot >= 0) b.setQuickBarSlot(slot - 36);
  const home = b.entity.position.floored();
  pond(controller, home.x, home.y, home.z); await sleep(1200);
  const vec3 = require('vec3');
  await b.lookAt(new vec3(home.x + 0.5, home.y - 1, home.z + 5.5), true);
  const bobberType = b.registry.entitiesByName.fishing_bobber.id;
  while (Date.now() < deadline) {
    let id = null, gone = false;
    const onSpawn = p => { if (p.type === bobberType && id === null) id = p.entityId; };
    const onDestroy = p => { if (id !== null && p.entityIds.includes(id)) gone = true; };
    b._client.on('spawn_entity', onSpawn);
    b._client.on('entity_destroy', onDestroy);
    b.activateItem(); await sleep(150); b.deactivateItem(); // cast; the reel does the rest
    const t0 = Date.now();
    while (!gone && Date.now() - t0 < 45000 && Date.now() < deadline) await sleep(100);
    b._client.removeListener('spawn_entity', onSpawn);
    b._client.removeListener('entity_destroy', onDestroy);
    if (gone) bump(n, 'reeled');
    else { bump(n, 'missed'); b.activateItem(); await sleep(150); b.deactivateItem(); }
    await sleep(400);
    if (b.inventory.emptySlotCount() < 4) { b.chat('/clear @s'); b.chat('/give @s fishing_rod[enchantments={lure:3}]'); await sleep(600);
      const s2 = b.inventory.slots.findIndex((it, i) => it && it.name === 'fishing_rod' && i >= 36 && i <= 44); if (s2 >= 0) b.setQuickBarSlot(s2 - 36); }
  }
  controller.chat(`/cerb fish part ${n} auto_reel remove`);
  controller.chat(`/cerb skill ${n} Fishing level 1`);
}

async function levels(b, deadline, controller) {
  const n = b.username;
  controller.chat(`/cerb skill ${n} Mining level 1`); await sleep(400);
  b.chat('/gamemode survival'); await sleep(400);
  b.chat('/clear @s'); await sleep(300);
  b.chat('/give @s netherite_pickaxe'); await sleep(800);
  b.chat('/effect give @s haste infinite 1 true'); b.chat('/effect give @s saturation infinite 1 true'); b.chat('/effect give @s resistance infinite 4 true');
  await sleep(500);
  const slot = b.inventory.slots.findIndex((it, i) => it && it.name === 'netherite_pickaxe' && i >= 36 && i <= 44);
  if (slot >= 0) b.setQuickBarSlot(slot - 36);
  const home = b.entity.position.floored();
  const fill = async () => {
    controller.chat(`/fill ${home.x - 2} ${home.y - 1} ${home.z - 1} ${home.x + 2} ${home.y - 1} ${home.z + 7} stone`);
    controller.chat(`/fill ${home.x - 2} ${home.y} ${home.z - 1} ${home.x + 2} ${home.y + 3} ${home.z + 1} air`);
    controller.chat(`/fill ${home.x - 2} ${home.y} ${home.z + 2} ${home.x + 2} ${home.y + 2} ${home.z + 6} diamond_ore`);
    bump(n, 'fills'); await sleep(900);
  };
  await fill();
  const vec3 = require('vec3');
  let nextReset = Date.now() + 12000;
  while (Date.now() < deadline) {
    // back to level 1 every 12 s, so it keeps levelling up from real XP (the reset itself is an admin correction)
    if (Date.now() > nextReset) { controller.chat(`/cerb skill ${n} Mining level 1`); bump(n, 'resets'); nextReset = Date.now() + 12000; }
    let target = null;
    for (let dz = 2; dz <= 6 && !target; dz++) for (let dy = 0; dy <= 2 && !target; dy++) for (let dx = -2; dx <= 2 && !target; dx++) {
      const bl = b.blockAt(new vec3(home.x + dx, home.y + dy, home.z + dz));
      if (bl && bl.name === 'diamond_ore') target = bl;
    }
    if (!target) { b.chat(`/tp @s ${home.x + 0.5} ${home.y} ${home.z + 0.5} 0 0`); await sleep(300); await fill(); continue; }
    if (target.position.distanceTo(b.entity.position.offset(0, 1.6, 0)) > 4.2) {
      b.chat(`/tp @s ${target.position.x + 0.5} ${home.y} ${target.position.z - 1.5} 0 0`); await sleep(250);
    }
    // Raw dig packets: this mineflayer times diamond ore as if the pickaxe didn't fit (3.25 s a block). A netherite
    // pickaxe with Haste II breaks it in 8 ticks; the server checks the progress, so the finish comes after 0.45 s.
    await b.lookAt(target.position.offset(0.5, 0.5, 0.5), true);
    b._client.write('block_dig', { status: 0, location: target.position, face: 1, sequence: (b._seq = (b._seq || 7000) + 1) });
    b.swingArm();
    await sleep(450);
    b._client.write('block_dig', { status: 2, location: target.position, face: 1, sequence: (b._seq = b._seq + 1) });
    await sleep(150);
    const after = b.blockAt(target.position);
    bump(n, after && after.name === 'diamond_ore' ? 'digfail' : 'ores');
    if (b.inventory.emptySlotCount() < 3) { b.chat('/clear @s diamond'); }
    await sleep(200);
  }
}

async function gather(b, deadline, controller) {
  const n = b.username;
  b.chat('/gamemode survival'); await sleep(400);
  b.chat('/clear @s'); await sleep(300);
  b.chat('/give @s netherite_pickaxe'); await sleep(800);
  b.chat('/effect give @s haste infinite 1 true'); b.chat('/effect give @s saturation infinite 1 true'); b.chat('/effect give @s resistance infinite 4 true');
  await sleep(500);
  const slot = b.inventory.slots.findIndex((it, i) => it && it.name === 'netherite_pickaxe' && i >= 36 && i <= 44);
  if (slot >= 0) b.setQuickBarSlot(slot - 36);
  const home = b.entity.position.floored();
  const fill = async () => { oreCube(controller, home.x, home.y, home.z); bump(n, 'fills'); await sleep(900); };
  await fill();
  const vec3 = require('vec3');
  while (Date.now() < deadline) {
    // the nearest stone or ore of its cube still standing, front to back (the bot walks into the space it cuts)
    let target = null;
    for (let dz = 2; dz <= 6 && !target; dz++) for (let dy = 0; dy <= 2 && !target; dy++) for (let dx = -2; dx <= 2 && !target; dx++) {
      const bl = b.blockAt(new vec3(home.x + dx, home.y + dy, home.z + dz));
      if (bl && bl.name !== 'air' && bl.name !== 'cave_air') target = bl;
    }
    if (!target) { b.chat(`/tp @s ${home.x + 0.5} ${home.y} ${home.z + 0.5} 0 0`); await sleep(300); await fill(); continue; }
    if (target.position.distanceTo(b.entity.position.offset(0, 1.6, 0)) > 4.2) {
      b.chat(`/tp @s ${target.position.x + 0.5} ${home.y} ${target.position.z - 1.5} 0 0`); await sleep(250); // step up to it
    }
    try { await b.dig(target, true); bump(n, target.name.endsWith('_ore') ? 'ores' : 'stone'); } catch (e) { bump(n, 'digfail'); if (!counts.firstError) counts.firstError = { [n]: String(e.message || e).slice(0, 120) }; await sleep(200); }
    if (b.inventory.emptySlotCount() < 3) { b.chat('/clear @s cobblestone'); b.chat('/clear @s raw_iron'); b.chat('/clear @s coal'); b.chat('/clear @s raw_copper'); b.chat('/clear @s raw_gold'); }
    await sleep(200);
  }
}

async function craft(b, deadline, controller) {
  const n = b.username;
  b.chat('/gamemode survival'); await sleep(400);
  b.chat('/clear @s'); await sleep(300);
  const p = b.entity.position.floored();
  controller.chat(`/setblock ${p.x + 1} ${p.y} ${p.z} crafting_table`); await sleep(600);
  const vec3 = require('vec3');
  const table = b.blockAt(new vec3(p.x + 1, p.y, p.z));
  const mcData = require('minecraft-data')(b.version);
  const recipe = b.recipesAll(mcData.itemsByName.iron_pickaxe.id, null, table)[0];
  if (!recipe) { console.log(n, 'craft: no recipe'); return; }
  while (Date.now() < deadline) {
    const ingots = b.inventory.count(mcData.itemsByName.iron_ingot.id), sticks = b.inventory.count(mcData.itemsByName.stick.id);
    if (ingots < 3 || sticks < 2) { b.chat('/give @s iron_ingot 63'); b.chat('/give @s stick 42'); await sleep(700); continue; }
    try { await b.craft(recipe, 1, table); bump(n, 'crafted'); } catch (e) { bump(n, 'craftfail'); await sleep(300); }
    if (b.inventory.count(mcData.itemsByName.iron_pickaxe.id) > 8) { b.chat('/clear @s iron_pickaxe'); await sleep(200); }
    await sleep(350);
  }
}

const IRON = [['head', 'helmet'], ['torso', 'chestplate'], ['legs', 'leggings'], ['feet', 'boots']];

async function hurt(b, deadline, controller, heavy = false) {
  b.on('entityHurt', e => { if (e === b.entity) bump(b.username, 'hurt'); });
  b.chat('/gamemode survival'); await sleep(400);
  if (heavy) { // no /item command on the sandbox's Folia build: each piece is given and put on by a click into its slot
    controller.chat(`/cerb skill ${b.username} Heavy_Armor_Training level 100`); await sleep(250);
    for (const [dest, piece] of IRON) {
      b.chat(`/give @s iron_${piece}`); await sleep(400);
      const it = b.inventory.items().find(x => x.name === `iron_${piece}`);
      if (it) { try { await b.equip(it, dest); bump(b.username, 'armor'); } catch (e) { bump(b.username, 'armorfail'); } }
    }
  }
  controller.chat(`/execute at ${b.username} run summon zombie ~1 ~ ~ {PersistenceRequired:1b}`); await sleep(600);
  let nextHeal = Date.now() + 8000;
  while (Date.now() < deadline) {
    if (Date.now() > nextHeal) { b.chat(`/cerb heal ${b.username}`); bump(b.username, 'heals'); nextHeal = Date.now() + 8000; }
    const near = Object.values(b.entities).some(e => e.name === 'zombie' && e.position.distanceTo(b.entity.position) < 6);
    if (!near) { controller.chat(`/execute at ${b.username} run summon zombie ~1 ~ ~ {PersistenceRequired:1b}`); bump(b.username, 'summons'); }
    await sleep(1000);
  }
  if (heavy) { // the same bots run other modes later: no armor, Heavy Armor Mastery back to 1
    for (const [, piece] of IRON) { b.chat(`/clear @s iron_${piece}`); await sleep(100); }
    controller.chat(`/cerb skill ${b.username} Heavy_Armor_Training level 1`);
  }
}

async function menu(b, deadline) {
  b.chat('/gamemode creative'); await sleep(400);
  const step = async slot => { if (!b.currentWindow) return false; await b.clickWindow(slot, 0, 0); bump(b.username, 'clicks'); await sleep(300); return true; };
  while (Date.now() < deadline) {
    b.chat('/menu'); await sleep(600);
    if (!b.currentWindow) { await sleep(500); continue; }
    bump(b.username, 'opens');
    for (const s of [19, 20, 10, 43, 45, 49, 21, 45]) if (!(await step(s))) break;
    if (b.currentWindow) { await b.clickWindow(53, 0, 0); bump(b.username, 'clicks'); }
    await sleep(300);
  }
}

async function fishmenu(b, deadline) {
  b.chat('/gamemode creative'); await sleep(400);
  const step = async slot => { if (!b.currentWindow) return false; await b.clickWindow(slot, 0, 0); bump(b.username, 'clicks'); await sleep(300); return true; };
  while (Date.now() < deadline) {
    b.chat('/menu'); await sleep(600);
    if (!b.currentWindow) { await sleep(500); continue; }
    bump(b.username, 'opens');
    for (const s of [19, 24, 13, 52, 52, 45, 45]) if (!(await step(s))) break;
    if (b.currentWindow) { await b.clickWindow(53, 0, 0); bump(b.username, 'clicks'); }
    await sleep(300);
  }
}

async function gearmenu(b, deadline, controller) {
  b.chat('/gamemode creative'); await sleep(400);
  // Fishing 50 (every category open) and four owned parts, so the screens compute their engine previews as for a player.
  controller.chat(`/cerb skill ${b.username} Fishing level 50`); await sleep(300);
  for (const part of ['scaled_grip', 'resin_line', 'rime_hook', 'sea_glass_float']) { controller.chat(`/cerb fish part ${b.username} ${part}`); await sleep(250); }
  const step = async slot => { if (!b.currentWindow) return false; await b.clickWindow(slot, 0, 0); bump(b.username, 'clicks'); await sleep(300); return true; };
  while (Date.now() < deadline) {
    b.chat('/menu'); await sleep(600);
    if (!b.currentWindow) { await sleep(500); continue; }
    bump(b.username, 'opens');
    // Skills -> Utility -> Fishing -> Equipment -> Rod -> back -> Lure -> back -> Bait Bag -> back -> back -> Journal ->
    // filter, filter -> Treasure tab -> back
    for (const s of [19, 24, 13, 29, 19, 45, 23, 45, 29, 45, 45, 31, 47, 47, 17, 45]) if (!(await step(s))) break;
    if (b.currentWindow) { await b.clickWindow(53, 0, 0); bump(b.username, 'clicks'); }
    await sleep(300);
  }
  // Leave the bot as it came (the fishing loads that follow compare builds without parts).
  for (const part of ['scaled_grip', 'resin_line', 'rime_hook', 'sea_glass_float']) controller.chat(`/cerb fish part ${b.username} ${part} remove`);
  controller.chat(`/cerb skill ${b.username} Fishing level 1`);
}

/** A Hearty Stew eaten and Speed I for 10 minutes: the timed effects the mealidle/effects loads run with. */
async function mealAndPotion(b, controller) {
  b.chat('/gamemode creative'); await sleep(400);
  controller.chat(`/cerb cook give ${b.username} hearty_stew common 1`); await sleep(800);
  controller.chat(`/effect give ${b.username} speed 600 0`); await sleep(300);
  const stew = b.inventory.items().find(i => i.name === 'rabbit_stew');
  if (stew) {
    try { await b.equip(stew, 'hand'); b.activateItem(); await sleep(2300); b.deactivateItem(); bump(b.username, 'ate'); } catch (e) { bump(b.username, 'eatfail'); }
  }
  await sleep(500);
}

/** The meal and potion end with the load, so the next load on this server starts from the same state. */
function clearMealAndPotion(b, controller) {
  controller.chat(`/cerb cook clear ${b.username}`);
  controller.chat(`/effect clear ${b.username}`);
}

async function mealidle(b, deadline, controller) {
  await mealAndPotion(b, controller);
  await menuidle(b, deadline, true);
  clearMealAndPotion(b, controller);
}

async function effectsmenu(b, deadline, controller) {
  await mealAndPotion(b, controller);
  const step = async slot => { if (!b.currentWindow) return false; await b.clickWindow(slot, 0, 0); bump(b.username, 'clicks'); await sleep(300); return true; };
  while (Date.now() < deadline) {
    b.chat('/menu'); await sleep(600);
    if (!b.currentWindow) { await sleep(500); continue; }
    bump(b.username, 'opens');
    for (const s of [4, 45]) if (!(await step(s))) break;
    if (b.currentWindow) { await b.clickWindow(53, 0, 0); bump(b.username, 'clicks'); }
    await sleep(300);
  }
  clearMealAndPotion(b, controller);
}

async function effectsidle(b, deadline, controller) {
  await mealAndPotion(b, controller);
  b.chat('/menu'); await sleep(1200);
  if (b.currentWindow) { await b.clickWindow(4, 0, 0); await sleep(800); bump(b.username, 'open'); }
  while (Date.now() < deadline) {
    if (!b.currentWindow) { b.chat('/menu'); await sleep(1000); if (b.currentWindow) await b.clickWindow(4, 0, 0); bump(b.username, 'reopened'); }
    await sleep(1000);
  }
  if (b.currentWindow) b.closeWindow(b.currentWindow);
  clearMealAndPotion(b, controller);
}

async function menuidle(b, deadline, keepMode = false) {
  if (!keepMode) { b.chat('/gamemode creative'); await sleep(400); }
  b.chat('/menu'); await sleep(1500);
  if (b.currentWindow) bump(b.username, 'open');
  while (Date.now() < deadline) {
    if (!b.currentWindow) { b.chat('/menu'); bump(b.username, 'reopened'); }
    await sleep(1000);
  }
  if (b.currentWindow) b.closeWindow(b.currentWindow);
}

(async () => {
  const controller = await join('CerbSmoke');
  await sleep(2000);
  const s = controller.spawnPoint || controller.entity.position;
  controller.chat(`/tp @s ${s.x + 0.5} ${s.y} ${s.z + 0.5}`); await sleep(800);
  controller.chat('/gamemode creative'); controller.chat('/time set day'); controller.chat('/difficulty normal');
  controller.chat('/kill @e[type=zombie,distance=..40]'); await sleep(500);
  const bots = [];
  for (let i = 1; i <= BOTS; i++) {
    const name = 'LoadBot' + i;
    controller.chat('/op ' + name);
    await sleep(4300); // bukkit.yml connection-throttle: 4 s between joins from one address
    try {
      const b = await join(name);
      bots.push(b);
      // gather, levels, wood, felling: one lane each, 7 blocks apart (an ore block or a grove reaches 6 ahead); else a 4-block grid
      const lanes = ['gather', 'levels', 'wood', 'felling', 'fishing', 'autofish', 'farm', 'widefarm'].includes(MODE);
      const x = lanes ? s.x + 3 + i * 7 : s.x + 3 + (i % 4) * 4, z = lanes ? s.z + 3 : s.z + 3 + Math.floor(i / 4) * 4;
      controller.chat(`/tp ${name} ${x + 0.5} ${s.y} ${z + 0.5}`);
    } catch (e) { console.log(e.message); }
  }
  await sleep(2500);
  const modes = ['combat', 'hurt', 'menu'];
  const pick = i => MODE === 'mixed' ? modes[i % 3] : MODE;
  const deadline = Date.now() + SECONDS * 1000;
  console.log(`LOAD START ${new Date().toISOString()} bots=${bots.length} seconds=${SECONDS} mode=${MODE}`);
  await Promise.all(bots.map((b, i) => {
    const m = pick(i);
    const run = m === 'combat' ? combat(b, deadline) : m === 'abilities' ? abilities(b, deadline, controller) : m === 'hurt' ? hurt(b, deadline, controller)
        : m === 'hurtheavy' ? hurt(b, deadline, controller, true)
        : m === 'gather' ? gather(b, deadline, controller) : m === 'craft' ? craft(b, deadline, controller)
        : m === 'wood' ? wood(b, deadline, controller) : m === 'felling' ? wood(b, deadline, controller, true)
        : m === 'levels' ? levels(b, deadline, controller) : m === 'fishing' ? fishing(b, deadline, controller)
        : m === 'autofish' ? autofish(b, deadline, controller)
        : m === 'farm' ? farm(b, deadline, controller) : m === 'widefarm' ? farm(b, deadline, controller, true)
        : m === 'cook' ? cook(b, deadline, controller)
        : m === 'menu' ? menu(b, deadline) : m === 'fishmenu' ? fishmenu(b, deadline) : m === 'menuidle' ? menuidle(b, deadline)
        : m === 'gearmenu' ? gearmenu(b, deadline, controller)
        : m === 'mealidle' ? mealidle(b, deadline, controller) : m === 'effectsmenu' ? effectsmenu(b, deadline, controller)
        : m === 'effectsidle' ? effectsidle(b, deadline, controller)
        : sleep(Math.max(0, deadline - Date.now()));
    return run.catch(e => console.log(b.username, m, 'error', e.message));
  }));
  console.log(`LOAD END ${new Date().toISOString()}`);
  for (const [name, c] of Object.entries(counts)) console.log(`  ${name}: ${JSON.stringify(c)}`);
  controller.chat('/kill @e[type=zombie,distance=..60]'); if (MODE === 'fishing' || MODE === 'autofish') controller.chat('/kill @e[type=guardian,distance=..80]'); await sleep(600);
  for (const b of bots) { controller.chat('/deop ' + b.username); b.chat('/gamemode creative'); }
  await sleep(800);
  for (const b of bots) b.quit();
  controller.quit();
  await sleep(500);
  process.exit(0);
})().catch(e => { console.log('LOAD ERROR', e.stack); process.exit(1); });
setTimeout(() => { console.log('TIMEOUT'); process.exit(3); }, (SECONDS + 240) * 1000);
