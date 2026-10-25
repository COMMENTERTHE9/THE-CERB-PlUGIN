// Reproducible load for performance baselines. LOCAL SANDBOX servers only (it ops test bots).
// Usage: node load.js [bots=6] [seconds=60] [mode=mixed]
//   combat - each bot hits its own no-AI level-50 zombie with a netherite sword (player -> mob path:
//            attributes, Power, crits, mob HP pool, labels, damage numbers, kill XP, saves)
//   hurt   - each bot stands in survival next to an AI level-30 zombie (mob -> player path: defense,
//            bar, HUD, defender XP); healed every 8 s so nobody stays dead
//   menu   - each bot browses the Player Menu as fast as a hurried player: hub -> Skills -> Combat ->
//            Blade Mastery -> next -> back -> home -> Stats -> back -> close
//   mixed  - the bots split across the three
//   idle   - the bots just stand there (per-player background cost: HUD, regen, saves)
//   menuidle - each bot opens the Player Menu once and leaves it open (an open menu must cost nothing extra)
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

async function hurt(b, deadline, controller) {
  b.on('entityHurt', e => { if (e === b.entity) bump(b.username, 'hurt'); });
  b.chat('/gamemode survival'); await sleep(400);
  controller.chat(`/execute at ${b.username} run summon zombie ~1 ~ ~ {PersistenceRequired:1b}`); await sleep(600);
  let nextHeal = Date.now() + 8000;
  while (Date.now() < deadline) {
    if (Date.now() > nextHeal) { b.chat(`/cerb heal ${b.username}`); bump(b.username, 'heals'); nextHeal = Date.now() + 8000; }
    const near = Object.values(b.entities).some(e => e.name === 'zombie' && e.position.distanceTo(b.entity.position) < 6);
    if (!near) { controller.chat(`/execute at ${b.username} run summon zombie ~1 ~ ~ {PersistenceRequired:1b}`); bump(b.username, 'summons'); }
    await sleep(1000);
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

async function menuidle(b, deadline) {
  b.chat('/gamemode creative'); await sleep(400);
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
      // gather: one lane each, 7 blocks apart (an ore block reaches 6 ahead); else a 4-block grid
      const x = MODE === 'gather' ? s.x + 3 + i * 7 : s.x + 3 + (i % 4) * 4, z = MODE === 'gather' ? s.z + 3 : s.z + 3 + Math.floor(i / 4) * 4;
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
        : m === 'gather' ? gather(b, deadline, controller) : m === 'craft' ? craft(b, deadline, controller)
        : m === 'menu' ? menu(b, deadline) : m === 'menuidle' ? menuidle(b, deadline) : sleep(Math.max(0, deadline - Date.now()));
    return run.catch(e => console.log(b.username, m, 'error', e.message));
  }));
  console.log(`LOAD END ${new Date().toISOString()}`);
  for (const [name, c] of Object.entries(counts)) console.log(`  ${name}: ${JSON.stringify(c)}`);
  controller.chat('/kill @e[type=zombie,distance=..60]'); await sleep(600);
  for (const b of bots) { controller.chat('/deop ' + b.username); b.chat('/gamemode creative'); }
  await sleep(800);
  for (const b of bots) b.quit();
  controller.quit();
  await sleep(500);
  process.exit(0);
})().catch(e => { console.log('LOAD ERROR', e.stack); process.exit(1); });
setTimeout(() => { console.log('TIMEOUT'); process.exit(3); }, (SECONDS + 240) * 1000);
