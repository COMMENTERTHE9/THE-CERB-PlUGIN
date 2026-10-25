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
      const x = s.x + 3 + (i % 4) * 4, z = s.z + 3 + Math.floor(i / 4) * 4;
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
    const run = m === 'combat' ? combat(b, deadline) : m === 'hurt' ? hurt(b, deadline, controller)
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
