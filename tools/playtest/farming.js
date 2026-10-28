// Farming in game (docs/design/FARMING_COOKING.md §3). LOCAL SANDBOX servers only; the bot must be op.
// Usage: node farming.js [phase[,phase...]] [botName]
//   harvest  - a ripe crop pays its XP (wheat 3, carrots, beetroots' fractions carried), an unripe one nothing (and a
//              hint); the first ripe harvest of a crop is a discovery, once; crops the bot planted and grew with bone
//              meal pay in full; the weak-crop rule
//   placed   - a melon the world grew (setblock) pays, one the bot placed by hand pays nothing (and a hint); sugar cane:
//              every grown segment that comes down counts, a placed bottom segment doesn't; a melon grown from a stem pays
//   pick     - sweet berries picked with a right-click pay (the bush stays), an unripe bush pays nothing; glow berries
//   creative - creative pays nothing, and the XP display says why
//   machines - water and a piston harvest ripe crops: vanilla drops, nobody paid
//   replant  - Farming 10: a ripe crop broken by hand is planted again at its first stage, its seed from the drops (a ripe
//              crop always drops one: the inventory is used only when something else took the drops), never a second
//              harvest; cocoa on the same side of its log; not below 10; /farming replant off
//   wide     - Farming 25: Sneak + a hoe takes every ripe crop of that kind in 3x3 (5x5 at 50), each paid once with 1 hoe
//              durability; not without Sneak, a hoe, or below 25; unripe crops and other kinds stay; a hoe about to break
//              stops it; switching away from the hoe stops it
//   protect  - a protection plugin's claim stops a Wide Harvest at its edge (needs the sandbox-only CerbTestProtect plugin)
//   fortune  - Fortune (crops) at Farming 100: about a quarter of wheat harvests drop a second wheat; none at Farming 1
//   quality  - Farming 100: about a third of the produce carries a quality (lore and data), never below 50
//   menu     - Player Menu → Skills → Farming → Crops, and Collections → Crops, count the discoveries
//   relog    - discoveries, Replant's switch and quality items survive a relog
//   all      - every phase above but protect (the default)
const mineflayer = require('mineflayer');
const { Vec3 } = require('vec3');
const phaseArg = process.argv[2] || 'all';
const NAME = process.argv[3] || 'CerbFarm';
const sleep = ms => new Promise(r => setTimeout(r, ms));
const chat = [];
const bars = [];
const results = [];
const failed = [];
const check = (name, ok, detail = '') => {
  results.push(ok);
  if (!ok) failed.push(name);
  console.log(`${ok ? 'PASS' : 'FAIL'}  ${name}${detail ? '  (' + detail + ')' : ''}`);
};
const note = s => console.log('      ' + s);
let bot, pchat;
function connect() {
  const b = mineflayer.createBot({ host: '127.0.0.1', port: 25565, username: NAME, version: '1.21.11', auth: 'offline' });
  b.on('messagestr', (m, pos) => { if (pos !== 'game_info') chat.push(m); });
  b._client.on('action_bar', p => {
    try { pchat = pchat || require('prismarine-chat')(b.registry); bars.push({ t: Date.now(), m: pchat.fromNotch(p.text).toString() }); } catch (e) { /* ignore */ }
  });
  b.on('kicked', r => { console.log('KICKED', r); process.exit(2); });
  bot = b;
  return new Promise(r => b.once('spawn', () => r(b)));
}
async function cmd(c, wait = 600) { const at = chat.length; bot.chat(c); await sleep(wait); return at; }
const saw = (re, since) => chat.slice(since).find(m => re.test(m));
const hudSince = t => [...new Set(bars.filter(b => b.t >= t).map(b => b.m))];

// ── skills ────────────────────────────────────────────────────────
const xpForLevel = l => Math.round(75 * Math.pow(Math.max(1, l), 1.85) + 40 * Math.max(1, l));
const totalXp = s => { let t = s.xp; for (let l = 1; l < s.level; l++) t += xpForLevel(l); return t; };
async function skill(name) {
  const at = await cmd(`/cerb skill ${NAME} ${name} xp 0`, 600);
  const m = (saw(/is now level \d+ \(\d+ XP\)/, at) || '').match(/level (\d+) \((\d+) XP\)/);
  return m ? { level: Number(m[1]), xp: Number(m[2]) } : { level: NaN, xp: NaN };
}
const farmXp = async () => totalXp(await skill('Farming'));
async function setLevel(level) { await cmd(`/cerb skill ${NAME} Farming level ${level}`, 500); }

// ── the arena: a fresh farm far from spawn each call (placed-block marks live in chunk data and outlive /fill) ──
let X = 0, Y = 0, Z = 0;
async function arena() {
  const sp = bot.spawnPoint || bot.entity.position;
  X = Math.floor(sp.x) - 2000 - (Date.now() % 20000) * 96; Y = 110; Z = Math.floor(sp.z) + 400;
  await cmd('/gamemode creative', 300);
  await cmd(`/tp @s ${X + 0.5} ${Y + 1} ${Z + 0.5}`, 1000);
  try { await bot.waitForChunksToLoad(); } catch (e) { /* the fill retries */ }
  await cmd('/gamerule spawn_mobs false', 300);
  await cmd('/gamerule random_tick_speed 0', 300); // nothing grows unless the test grows it
  await cmd('/time set day', 300);
  for (let i = 0; i < 20; i++) {
    const a = await cmd(`/fill ${X - 12} ${Y - 2} ${Z - 12} ${X + 24} ${Y - 2} ${Z + 12} stone`, 600);
    if (!saw(/not loaded/i, a)) break;
    await sleep(1000);
  }
  await cmd(`/fill ${X - 12} ${Y - 1} ${Z - 12} ${X + 24} ${Y - 1} ${Z + 12} farmland[moisture=7]`, 600);
  await cmd(`/fill ${X - 12} ${Y} ${Z - 12} ${X + 24} ${Y + 8} ${Z + 12} air`, 900);
  await cmd('/kill @e[type=item,distance=..60]', 300);
  await cmd('/clear @s', 400);
  await cmd('/gamemode survival', 300);
  await cmd('/effect clear @s', 300);
  await cmd('/effect give @s resistance infinite 4 true', 300);
  await cmd('/effect give @s saturation infinite 1 true', 300);
  await cmd('/xpdisplay compact', 300);
  await stand(0, -3);
}
async function stand(dx, dz, yaw = 0, pitch = 30) { await cmd(`/tp @s ${X + dx + 0.5} ${Y} ${Z + dz + 0.5} ${yaw} ${pitch}`, 500); }
const at = (dx, dy, dz) => new Vec3(X + dx, Y + dy, Z + dz);
const block = (dx, dy, dz) => bot.blockAt(at(dx, dy, dz));
const blockName = (dx, dy, dz) => { const b = block(dx, dy, dz); return b ? b.name : '?'; };
const age = (dx, dy, dz) => { const b = block(dx, dy, dz); return b ? Number((b.getProperties() || {}).age) : NaN; };
async function fill(x1, y1, z1, x2, y2, z2, b, wait = 300) { await cmd(`/fill ${X + x1} ${Y + y1} ${Z + z1} ${X + x2} ${Y + y2} ${Z + z2} ${b}`, wait); }
async function setblock(dx, dy, dz, b, wait = 300) { await cmd(`/setblock ${X + dx} ${Y + dy} ${Z + dz} ${b}`, wait); }

// ── items ─────────────────────────────────────────────────────────
async function give(item, n = 1, wait = 500) { await cmd(`/give @s ${item} ${n}`, wait); }
async function hold(name) {
  const idx = bot.inventory.slots.findIndex((it, i) => it && it.name === name && i >= 36 && i <= 44);
  if (idx >= 0) { bot.setQuickBarSlot(idx - 36); await sleep(250); return true; }
  const any = bot.inventory.slots.findIndex((it, i) => it && it.name === name && i >= 9 && i <= 35);
  if (any < 0) return false;
  try { await bot.clickWindow(any, 0, 2); } catch (e) { /* the server decides */ }
  await sleep(350);
  return true;
}
async function holdNothing() {
  const free = [...Array(9).keys()].find(i => !bot.inventory.slots[36 + i]);
  if (free != null) bot.setQuickBarSlot(free);
  await sleep(250);
}
const invCount = name => bot.inventory.slots.filter(it => it && it.name === name).reduce((a, it) => a + it.count, 0);
const loreOf = it => (it && it.customLore ? it.customLore.map(l => {
  try { return require('prismarine-chat')(bot.registry).fromNotch(l).toString(); } catch (e) { return String(l); }
}).join(' | ') : '');
const nameOf = it => {
  if (!it || !it.customName) return '';
  try { return require('prismarine-chat')(bot.registry).fromNotch(it.customName).toString(); } catch (e) { return String(it.customName); }
};
/** Item entities within `r` of a spot: [{name, count, lore}]. */
async function dropsNear(dx, dy, dz, r = 3, clear = true) {
  await sleep(300);
  const p = at(dx, dy, dz).offset(0.5, 0.5, 0.5);
  const out = [];
  for (const e of Object.values(bot.entities)) {
    if (e.name !== 'item' || e.position.distanceTo(p) > r) continue;
    try { const d = e.getDroppedItem(); if (d) out.push({ name: d.name, count: d.count, lore: loreOf(d) }); } catch (err) { /* skip */ }
  }
  if (clear) await cmd(`/kill @e[type=item,x=${X + dx},y=${Y + dy},z=${Z + dz},distance=..${r + 3}]`, 300);
  return out;
}
const count = (list, name) => list.filter(d => d.name === name).reduce((a, d) => a + d.count, 0);
/** Fill every free inventory slot with dirt, so drops stay on the ground where they can be counted. */
async function stuff() {
  const free = bot.inventory.emptySlotCount();
  if (free > 0) await cmd(`/give @s dirt ${free * 64}`, 800);
}

// ── harvesting ────────────────────────────────────────────────────
let seq = 9000;
/** Break the block at (dx, dy, dz) by raw dig packets (crops break at once). `after`: right after the finish packet. */
async function brk(dx, dy, dz, { sneak = false, ms = 80, after = null } = {}) {
  let b = block(dx, dy, dz);
  for (let i = 0; i < 10 && (!b || b.name === 'air'); i++) { await sleep(150); b = block(dx, dy, dz); } // a fresh arena's updates
  if (!b || b.name === 'air') return false;
  await bot.lookAt(b.position.offset(0.5, 0.5, 0.5), true);
  if (sneak) { bot.setControlState('sneak', true); await sleep(200); }
  bot._client.write('block_dig', { status: 0, location: b.position, face: 1, sequence: seq++ });
  bot.swingArm();
  await sleep(ms);
  bot._client.write('block_dig', { status: 2, location: b.position, face: 1, sequence: seq++ });
  if (after) after();
  await sleep(400);
  if (sneak) { bot.setControlState('sneak', false); await sleep(150); }
  return true;
}
async function brkXp(dx, dy, dz, opts = {}) {
  const before = await farmXp();
  await brk(dx, dy, dz, opts);
  await sleep(250);
  return (await farmXp()) - before;
}
async function use(dx, dy, dz) {
  const b = block(dx, dy, dz);
  await bot.lookAt(b.position.offset(0.5, 0.5, 0.5), true);
  try { await bot.activateBlock(b); } catch (e) { /* the server decides */ }
  await sleep(450);
}

// ════════════════════════════════════════════════════════════════
async function harvestPhase() {
  await arena();
  await cmd(`/cerb farm crops ${NAME} reset`, 600);
  await setLevel(1);
  await give('iron_hoe'); await hold('iron_hoe');
  await stand(0, -2, 0);
  let t = Date.now();
  await setblock(0, 0, 0, 'wheat[age=7]');
  let g = await brkXp(0, 0, 0);
  check('The first ripe wheat: 3 XP and its 20 XP discovery', g === 23 && chat.some(m => /Discovered: Wheat/.test(m)), `+${g}`);
  await setblock(0, 0, 0, 'wheat[age=7]');
  check('The next ripe wheat: 3 XP, no second discovery', (await brkXp(0, 0, 0)) === 3);
  await setblock(0, 0, 0, 'wheat[age=3]');
  t = Date.now();
  g = await brkXp(0, 0, 0);
  check('Unripe wheat: no XP, and the HUD says when it pays', g === 0 && hudSince(t).some(h => /Wheat pays Farming XP when fully grown/.test(h)),
    `+${g}; ${JSON.stringify(hudSince(t).slice(-2))}`);
  await cmd('/kill @e[type=item,distance=..20]', 300);
  // Beetroots pay 3.5: two harvests make 7 (the half carries over), after the 30 XP discovery.
  await setblock(1, 0, 0, 'beetroots[age=3]');
  await brk(1, 0, 0);
  await setblock(1, 0, 0, 'beetroots[age=3]'); await setblock(2, 0, 0, 'beetroots[age=3]');
  const b0 = await farmXp();
  await brk(1, 0, 0); await brk(2, 0, 0);
  g = (await farmXp()) - b0;
  check('Beetroots: 3.5 each, the half carried (two make 7)', g === 7, `+${g}`);
  // Planted by the bot and grown with bone meal: pays in full (crops planted by players are farming).
  await give('wheat_seeds', 4); await give('bone_meal', 16);
  await hold('wheat_seeds');
  const soil = block(3, -1, 0);
  try { await bot.placeBlock(soil, new Vec3(0, 1, 0)); } catch (e) { /* checked below */ }
  await sleep(500);
  await hold('bone_meal');
  for (let i = 0; i < 8 && age(3, 0, 0) !== 7; i++) await use(3, 0, 0);
  check('The bot planted wheat and grew it with bone meal', blockName(3, 0, 0) === 'wheat' && age(3, 0, 0) === 7, `${blockName(3, 0, 0)} age ${age(3, 0, 0)}`);
  await hold('iron_hoe');
  check('...and it pays in full: 3 XP', (await brkXp(3, 0, 0)) === 3);
  // The weak-crop rule: wheat is level 1, a quarter from Farming 21.
  await setLevel(21);
  await setblock(0, 0, 0, 'wheat[age=7]'); await setblock(1, 0, 0, 'wheat[age=7]'); await setblock(2, 0, 0, 'wheat[age=7]'); await setblock(3, 0, 0, 'wheat[age=7]');
  const w0 = await farmXp();
  for (let x = 0; x < 4; x++) await brk(x, 0, 0);
  g = (await farmXp()) - w0;
  check('At Farming 21, wheat pays a quarter (4 harvests: 3 XP)', g === 3, `+${g}`);
  await setLevel(1);
  await cmd('/kill @e[type=item,distance=..20]', 300);
}

async function placedPhase() {
  await arena();
  await setLevel(1);
  await stand(0, -2, 0);
  await fill(-2, -1, 0, 6, -1, 4, 'dirt');
  await setblock(0, 0, 0, 'melon');
  let g = await brkXp(0, 0, 0, { ms: 1200 });
  check('A melon the world made: 5 XP and its 40 XP discovery (first)', g === 45 || g === 5, `+${g}`);
  await setblock(0, 0, 0, 'melon');
  check('...another: 5 XP', (await brkXp(0, 0, 0, { ms: 1200 })) === 5);
  // Placed by hand: nothing.
  await give('melon', 2);
  await hold('melon');
  try { await bot.placeBlock(block(0, -1, 0), new Vec3(0, 1, 0)); } catch (e) { /* checked below */ }
  await sleep(400);
  await holdNothing();
  let t = Date.now();
  g = await brkXp(0, 0, 0, { ms: 1200 });
  check('A melon placed by hand: no XP, and the HUD says why', blockName(0, 0, 0) === 'air' && g === 0
    && hudSince(t).some(h => /placed by hand pays no Farming XP/.test(h)), `+${g}; ${JSON.stringify(hudSince(t).slice(-2))}`);
  await cmd('/kill @e[type=item,distance=..20]', 300);
  // Sugar cane: water next to it; three grown segments come down from the bottom one.
  await setblock(4, -1, 1, 'water');
  await fill(4, -1, 0, 4, -1, 0, 'sand');
  await fill(4, 0, 0, 4, 2, 0, 'sugar_cane');
  await stand(4, -2, 0);
  const c0 = await farmXp();
  await brk(4, 0, 0);
  await sleep(500);
  g = (await farmXp()) - c0;
  check('Sugar cane: the bottom segment and the two above that fall pay 1 each (and the 20 XP discovery)', g === 23 || g === 3, `+${g}`);
  // A placed bottom segment with two grown above it: 2.
  await give('sugar_cane', 2); await hold('sugar_cane');
  try { await bot.placeBlock(block(4, -1, 0), new Vec3(0, 1, 0)); } catch (e) { /* checked below */ }
  await sleep(400);
  await fill(4, 1, 0, 4, 2, 0, 'sugar_cane');
  await holdNothing();
  const c1 = await farmXp();
  await brk(4, 0, 0);
  await sleep(500);
  g = (await farmXp()) - c1;
  check('A cane planted by hand with two grown above: the two grown pay, the placed one doesn\'t', g === 2, `+${g}`);
  await cmd('/kill @e[type=item,distance=..20]', 300);
  // A melon grown from a stem pays.
  await setblock(8, -1, 0, 'farmland'); await setblock(8, 0, 0, 'melon_stem[age=7]');
  await fill(7, -1, 0, 9, -1, 1, 'dirt replace air');
  await setblock(8, -1, 0, 'farmland');
  await cmd('/gamerule random_tick_speed 400', 300);
  let grown = null;
  for (let i = 0; i < 40 && !grown; i++) {
    await sleep(500);
    for (const [dx, dz] of [[7, 0], [9, 0], [8, 1], [8, -1]]) if (blockName(dx, 0, dz) === 'melon') grown = [dx, dz];
  }
  await cmd('/gamerule random_tick_speed 0', 300);
  if (!grown) { check('A melon grew from its stem', false); return; }
  await stand(grown[0], grown[1] - 2, 0);
  g = await brkXp(grown[0], 0, grown[1], { ms: 1200 });
  check('A melon grown from a stem: 5 XP', g === 5, `+${g}`);
  await cmd('/kill @e[type=item,distance=..20]', 300);
}

async function pickPhase() {
  await arena();
  await setLevel(1);
  await fill(-2, -1, 0, 6, -1, 4, 'grass_block');
  await stand(0, -2, 0);
  await holdNothing();
  await setblock(0, 0, 0, 'sweet_berry_bush[age=3]');
  let g0 = await farmXp();
  await use(0, 0, 0);
  let g = (await farmXp()) - g0;
  check('Sweet berries picked: 3 XP (and the 40 XP discovery), the bush stays', (g === 43 || g === 3) && blockName(0, 0, 0) === 'sweet_berry_bush'
    && age(0, 0, 0) === 1, `+${g}, ${blockName(0, 0, 0)} age ${age(0, 0, 0)}`);
  await setblock(0, 0, 0, 'sweet_berry_bush[age=1]');
  g0 = await farmXp();
  await use(0, 0, 0);
  check('An unripe bush: nothing', (await farmXp()) - g0 === 0);
  await setblock(0, 0, 0, 'sweet_berry_bush[age=2]');
  g0 = await farmXp();
  await use(0, 0, 0);
  check('A bush at stage 2 has berries: 3 XP', (await farmXp()) - g0 === 3);
  // Glow berries: a vine under a ceiling.
  await setblock(3, 3, 0, 'stone');
  await setblock(3, 2, 0, 'cave_vines[berries=true]');
  g0 = await farmXp();
  await use(3, 2, 0);
  g = (await farmXp()) - g0;
  check('Glow berries picked: 4 XP (and the 80 XP discovery); the weak-crop rule needs no level', g === 84 || g === 4, `+${g}`);
  await cmd('/kill @e[type=item,distance=..20]', 300);
}

async function creativePhase() {
  await arena();
  await setLevel(1);
  await cmd('/gamemode creative', 400);
  await stand(0, -2, 0);
  await setblock(0, 0, 0, 'wheat[age=7]');
  const t = Date.now();
  const g = await brkXp(0, 0, 0);
  await sleep(300);
  check('Creative: ripe wheat pays nothing, and the XP display says why', g === 0 && hudSince(t).some(h => /creative/i.test(h)),
    `+${g}; ${JSON.stringify(hudSince(t).slice(-3))}`);
  await cmd('/gamemode survival', 400);
}

async function machinesPhase() {
  await arena();
  await setLevel(1);
  await stand(0, -4, 0);
  // Water over ripe wheat.
  await fill(0, 0, 0, 2, 0, 0, 'wheat[age=7]');
  const w0 = await farmXp();
  await setblock(-1, 0, 0, 'water');
  await sleep(4000);
  const drops = await dropsNear(1, 0, 0, 12);
  const gone = [0, 1, 2].filter(x => blockName(x, 0, 0) !== 'wheat').length;
  check('Water washes ripe wheat away: vanilla drops, no XP', gone === 3 && count(drops, 'wheat') >= 1 && (await farmXp()) - w0 === 0,
    `${gone} of 3 washed away, ${count(drops, 'wheat')} wheat on the ground`);
  await setblock(-1, 0, 0, 'air');
  // A piston breaks a melon the world made.
  await setblock(5, 0, 0, 'melon');
  await setblock(5, 0, -1, 'piston[facing=south]');
  const m0 = await farmXp();
  await setblock(5, 0, -2, 'redstone_block');
  await sleep(1200);
  const md = await dropsNear(5, 0, 1, 4);
  check('A piston breaks a melon: its slices drop, no XP', blockName(5, 0, 0) !== 'melon' && count(md, 'melon_slice') >= 1 && (await farmXp()) - m0 === 0,
    `${blockName(5, 0, 0)}; ${count(md, 'melon_slice')} slices`);
}

async function replantPhase() {
  await arena();
  await give('iron_hoe'); await hold('iron_hoe');
  await stand(0, -2, 0);
  await setLevel(9);
  await setblock(0, 0, 0, 'wheat[age=7]');
  await brk(0, 0, 0);
  check('Farming 9: no Replant (the spot is empty)', blockName(0, 0, 0) === 'air', blockName(0, 0, 0));
  await setLevel(10);
  await cmd('/farming replant on', 400);
  await setblock(0, 0, 0, 'carrots[age=7]');
  await cmd('/clear @s carrot', 300);
  await stuff(); // no room: the harvest's carrots stay on the ground
  await brk(0, 0, 0);
  const d = await dropsNear(0, 0, 0);
  check('Farming 10: ripe carrots come back as a carrot at stage 0', blockName(0, 0, 0) === 'carrots' && age(0, 0, 0) === 0, `${blockName(0, 0, 0)} age ${age(0, 0, 0)}`);
  check('...its seed from the harvest\'s own drops (no carrot in the inventory, a vanilla 1-4 less one on the ground)',
    invCount('carrot') === 0 && count(d, 'carrot') <= 3, `inventory ${invCount('carrot')}; ${count(d, 'carrot')} on the ground`);
  const x0 = await farmXp();
  await brk(0, 0, 0);
  check('...and the replanted crop is no second harvest (unripe: nothing)', (await farmXp()) - x0 === 0 && blockName(0, 0, 0) === 'air');
  await cmd('/clear @s dirt', 400);
  // Cocoa goes back on the same side of the same log.
  await setblock(2, 0, 2, 'jungle_log');
  await setblock(2, 0, 1, 'cocoa[age=2,facing=south]');
  await brk(2, 0, 1);
  const cocoa = block(2, 0, 1);
  check('Cocoa: replanted on the same side of the same log', cocoa && cocoa.name === 'cocoa' && Number(cocoa.getProperties().age) === 0
    && cocoa.getProperties().facing === 'south', cocoa ? JSON.stringify(cocoa.getProperties()) : 'none');
  await cmd('/kill @e[type=item,distance=..20]', 300);
  // Wheat with no seeds in the inventory: a ripe crop always drops its seed (vanilla), so it is planted again from them.
  await cmd('/clear @s wheat_seeds', 300);
  let replanted = 0;
  for (let i = 0; i < 5; i++) {
    await setblock(0, 0, 0, 'wheat[age=7]');
    await brk(0, 0, 0);
    if (blockName(0, 0, 0) === 'wheat' && age(0, 0, 0) === 0) replanted++;
    await cmd('/kill @e[type=item,distance=..20]', 300);
  }
  check('No seeds in the inventory: ripe wheat is still planted again, from its own drops (5 of 5)', replanted === 5, `${replanted} of 5`);
  await cmd('/clear @s dirt', 300);
  await cmd('/farming replant off', 400);
  await give('wheat_seeds', 5);
  await setblock(0, 0, 0, 'wheat[age=7]');
  await brk(0, 0, 0);
  check('/farming replant off: nothing is replanted', blockName(0, 0, 0) === 'air');
  await cmd('/farming replant on', 400);
  await cmd('/kill @e[type=item,distance=..20]', 300);
}

/** A field of ripe wheat, (2r+1)² around (0, z0). */
async function field(r, z0 = 5, crop = 'wheat[age=7]') { await fill(-r, 0, z0 - r, r, 0, z0 + r, crop, 500); }
function ripeLeft(r, z0 = 5, name = 'wheat', ripeAge = 7) {
  let n = 0;
  for (let dx = -r; dx <= r; dx++) for (let dz = z0 - r; dz <= z0 + r; dz++) if (blockName(dx, 0, dz) === name && age(dx, 0, dz) === ripeAge) n++;
  return n;
}
const heldDamage = () => { const h = bot.heldItem; return h ? (h.durabilityUsed ?? -1) : -1; };

async function widePhase() {
  await arena();
  await give('iron_hoe'); await hold('iron_hoe');
  await cmd('/farming replant off', 400);
  await stand(0, 2, 0);
  await setLevel(24);
  await field(3);
  await brk(0, 0, 5, { sneak: true });
  check('Farming 24: Sneak + hoe takes one crop', ripeLeft(3) === 48, `${ripeLeft(3)} of 49 left`);
  await setLevel(25);
  await field(3);
  await cmd('/kill @e[type=item,distance=..20]', 300);
  const d0 = heldDamage();
  const x0 = await farmXp();
  await brk(0, 0, 5, { sneak: true });
  await sleep(800);
  let g = (await farmXp()) - x0;
  let drops = await dropsNear(0, 0, 5, 6);
  check('Farming 25: Sneak + hoe takes the 3x3 (9 crops), the rest stays', ripeLeft(3) === 40, `${ripeLeft(3)} of 49 left`);
  check('...each pays once: 9 x 0.75 (wheat is weak at Farming 25: a quarter of 3)', g === 6 || g === 7, `+${g}`);
  check('...9 wheat dropped (and a Fortune extra now and then), merged', count(drops, 'wheat') >= 9 && count(drops, 'wheat') <= 11, `${count(drops, 'wheat')} wheat`);
  check('...8 points of hoe wear (the own break costs none, as in vanilla)', heldDamage() - d0 === 8, `${d0} → ${heldDamage()}`);
  await setLevel(50);
  await field(3);
  await brk(0, 0, 5, { sneak: true });
  await sleep(800);
  check('Farming 50: the 5x5 (25 crops)', ripeLeft(3) === 24, `${ripeLeft(3)} of 49 left`);
  await field(3);
  await brk(0, 0, 5);
  check('Without Sneak: one crop', ripeLeft(3) === 48, `${ripeLeft(3)} left`);
  await field(3);
  await holdNothing();
  let t = Date.now();
  await brk(0, 0, 5, { sneak: true });
  check('Without a hoe: one crop, and the HUD says Wide Harvest wants a hoe', ripeLeft(3) === 48 && hudSince(t).some(h => /Wide Harvest.*hoe/.test(h)),
    `${ripeLeft(3)} left; ${JSON.stringify(hudSince(t).slice(-2))}`);
  await hold('iron_hoe');
  // Unripe crops and other kinds stay.
  await field(3);
  await fill(-1, 0, 4, 1, 0, 4, 'wheat[age=5]');
  await fill(-1, 0, 6, 1, 0, 6, 'carrots[age=7]');
  await brk(0, 0, 5, { sneak: true });
  await sleep(800);
  let unripe = 0, carrots = 0;
  for (let dx = -1; dx <= 1; dx++) { if (age(dx, 0, 4) === 5) unripe++; if (blockName(dx, 0, 6) === 'carrots') carrots++; }
  check('Unripe wheat and ripe carrots in reach stay', unripe === 3 && carrots === 3, `unripe ${unripe}, carrots ${carrots}`);
  await cmd('/kill @e[type=item,distance=..20]', 300);
  // A hoe about to break: the sweep stops before it would.
  await cmd('/clear @s iron_hoe', 300);
  await give('iron_hoe[damage=247]'); await hold('iron_hoe');
  await field(3);
  await brk(0, 0, 5, { sneak: true });
  await sleep(800);
  const left = ripeLeft(3);
  check('A hoe with 3 uses left: the sweep stops before it breaks (2 more crops), the hoe stays', left === 46 && bot.heldItem && bot.heldItem.name === 'iron_hoe',
    `${left} left; holding ${bot.heldItem ? bot.heldItem.name : 'nothing'} damage ${heldDamage()}`);
  await cmd('/clear @s iron_hoe', 300);
  await give('iron_hoe'); await hold('iron_hoe');
  // Switching away from the hoe stops it (the first step runs a tick after the break).
  await setLevel(100);
  await field(4);
  await brk(0, 0, 5, { sneak: true, after: () => bot.setQuickBarSlot((bot.quickBarSlot + 1) % 9) });
  await sleep(800);
  check('Switching away from the hoe at once: the sweep stops early (it would take all 81)', ripeLeft(4) > 0 && ripeLeft(4) <= 80, `${ripeLeft(4)} of 81 left`);
  await hold('iron_hoe');
  await field(4);
  await cmd('/farming replant on', 400);
  await give('wheat_seeds', 64);
  await brk(0, 0, 5, { sneak: true });
  await sleep(1500);
  let seedlings = 0;
  for (let dx = -4; dx <= 4; dx++) for (let dz = 1; dz <= 9; dz++) if (blockName(dx, 0, dz) === 'wheat' && age(dx, 0, dz) === 0) seedlings++;
  check('Farming 100 with Replant: the 9x9 comes down and is planted again', ripeLeft(4) === 0 && seedlings >= 80, `${ripeLeft(4)} ripe left, ${seedlings} seedlings`);
  await cmd('/kill @e[type=item,distance=..30]', 300);
  await setLevel(1);
}

async function protectPhase() {
  await arena();
  const a = await cmd('/testprotect off', 600);
  if (!saw(/testprotect/, a)) { check('protect needs the sandbox-only CerbTestProtect plugin (tools/playtest/protect/build.sh)', false); return; }
  await give('iron_hoe'); await hold('iron_hoe');
  await cmd('/farming replant off', 400);
  await setLevel(50);
  await stand(0, 2, 0);
  await field(2);
  // The far two rows of the 5x5 are someone's claim.
  await cmd(`/testprotect ${X - 3} ${Y - 1} ${Z + 6} ${X + 3} ${Y + 2} ${Z + 8}`, 500);
  await brk(0, 0, 5, { sneak: true });
  await sleep(800);
  let claimed = 0;
  for (let dx = -2; dx <= 2; dx++) for (let dz = 6; dz <= 7; dz++) if (blockName(dx, 0, dz) === 'wheat' && age(dx, 0, dz) === 7) claimed++;
  check('A claim over part of the reach: those crops stay', claimed === 10, `${claimed} of 10 in the claim left`);
  await cmd('/testprotect off', 500);
  await field(2);
  await brk(0, 0, 5, { sneak: true });
  await sleep(800);
  check('Control: with no claim, the whole 5x5 comes down', ripeLeft(2) === 0, `${ripeLeft(2)} left`);
  await cmd('/kill @e[type=item,distance=..20]', 300);
  await cmd('/farming replant on', 400);
  await setLevel(1);
}

async function harvestMany(n, level) {
  await setLevel(level);
  await cmd('/farming replant off', 300);
  const st = await cmd('/cerb farm stats', 500);
  note('server before: ' + (saw(/quality fine/, st) || '').replace(/.*quality /, ''));
  let wheat = 0, extra = 0, quality = 0;
  const lore = [];
  for (let i = 0; i < n; i += 7) {
    await fill(-3, 0, 2, 3, 0, 2, 'wheat[age=7]', 400);
    for (let dx = -3; dx <= 3; dx++) await brk(dx, 0, 2, { ms: 40 });
    const d = await dropsNear(0, 0, 2, 5);
    for (const it of d.filter(x => x.name === 'wheat')) {
      wheat += it.count;
      if (it.lore) { quality += it.count; lore.push(it.lore); }
    }
  }
  extra = wheat - Math.ceil(n / 7) * 7;
  const st2 = await cmd('/cerb farm stats', 500);
  note('server after: ' + (saw(/quality fine/, st2) || '').replace(/.*quality /, '') + '; ground: ' + wheat + ' wheat, ' + quality + ' with a quality');
  return { wheat, extra, quality, lore };
}

async function fortunePhase() {
  await arena();
  await give('iron_hoe'); await hold('iron_hoe');
  await stand(0, -1, 0);
  const low = await harvestMany(42, 1);
  check('Farming 1: a ripe wheat drops one wheat (Fortune 0.5: a 0.25% chance)', low.extra <= 1, `${low.extra} extra of 42`);
  const high = await harvestMany(84, 100);
  check('Farming 100: Fortune (crops) 50, a 25% chance of a second wheat (84 harvests: 9-35 extra)', high.extra >= 9 && high.extra <= 35, `${high.extra} extra of 84`);
  await setLevel(1);
}

async function qualityPhase() {
  await arena();
  await give('iron_hoe'); await hold('iron_hoe');
  await stand(0, -1, 0);
  const low = await harvestMany(42, 49);
  check('Farming 49: no quality', low.quality === 0, `${low.quality} of ${low.wheat}`);
  const high = await harvestMany(84, 100);
  const kinds = [...new Set(high.lore)];
  note(`qualities seen: ${JSON.stringify(kinds)}`);
  check('Farming 100: about a third of the wheat has a quality (84 harvests: 15-45)', high.quality >= 15 && high.quality <= 45, `${high.quality} of ${high.wheat}`);
  check('...shown on the item ("✦ Fine quality"...)', kinds.length > 0 && kinds.every(k => /✦ (Fine|Superior|Masterwork) quality/.test(k)), JSON.stringify(kinds));
  // One kept for the relog phase: the bot walks over the drops to pick them up.
  for (let tries = 0; tries < 6; tries++) {
    await fill(-3, 0, 2, 3, 0, 2, 'wheat[age=7]', 400);
    for (let dx = -3; dx <= 3; dx++) await brk(dx, 0, 2, { ms: 40 });
    for (let dx = -3; dx <= 3; dx += 2) await cmd(`/tp @s ${X + dx + 0.5} ${Y} ${Z + 2.5}`, 400);
    await sleep(800);
    await stand(0, -1, 0);
    if (bot.inventory.slots.some(it => it && it.name === 'wheat' && loreOf(it))) break;
  }
  check('A quality wheat picked up keeps its quality', bot.inventory.slots.some(it => it && it.name === 'wheat' && /quality/.test(loreOf(it))));
  await setLevel(1);
}

async function menuPhase() {
  await cmd('/menu', 1500);
  const find = re => { for (let i = 0; i < 54; i++) if (re.test(nameOf(bot.currentWindow && bot.currentWindow.slots[i]))) return i; return -1; };
  let tile = '(none)', screen = '(none)', coll = '(none)';
  try {
    await bot.clickWindow(19, 0, 0); await sleep(1000); // Skills
    await bot.clickWindow(24, 0, 0); await sleep(1000); // Utility Skills
    const f = find(/^Farming/);
    if (f >= 0) {
      await bot.clickWindow(f, 0, 0); await sleep(1200);
      const ti = find(/^Crops: /);
      if (ti >= 0) {
        tile = nameOf(bot.currentWindow.slots[ti]);
        await bot.clickWindow(ti, 0, 0); await sleep(1200);
        const h = bot.currentWindow && bot.currentWindow.slots[4];
        screen = h ? nameOf(h) + ' | ' + loreOf(h) : '(no header)';
      }
    }
  } catch (e) { tile = '(' + e.message + ')'; }
  if (bot.currentWindow) bot.closeWindow(bot.currentWindow);
  await sleep(500);
  const at0 = await cmd('/farming', 800);
  const summary = saw(/crops discovered \d+ of \d+/, at0) || '';
  const n = (summary.match(/discovered (\d+) of/) || [])[1];
  check('The Farming page has a Crops tile with the count /farming gives', n != null && new RegExp(`^Crops: ${n} of 12 discovered`).test(tile), `${tile} / ${summary}`);
  check('...which opens the crop list with the techniques at your level', /Crops/.test(screen) && /Replant/.test(screen) && /Wide Harvest/.test(screen), screen.slice(0, 200));
  await cmd('/menu', 1500);
  try {
    const c = find(/^Collections/);
    if (c >= 0) {
      await bot.clickWindow(c, 0, 0); await sleep(1200);
      const e = find(/^Crops/);
      if (e >= 0) coll = nameOf(bot.currentWindow.slots[e]) + ' | ' + loreOf(bot.currentWindow.slots[e]);
    }
  } catch (e) { coll = '(' + e.message + ')'; }
  if (bot.currentWindow) bot.closeWindow(bot.currentWindow);
  await sleep(400);
  check('Collections has a Crops entry with the same count', n != null && new RegExp(`${n}\\s*/\\s*12|${n} of 12`).test(coll), coll.slice(0, 160));
}

async function relogPhase() {
  await setLevel(10); // Replant's line shows its switch from the level it opens at
  const at0 = await cmd('/farming', 800);
  const before = saw(/crops discovered \d+ of \d+/, at0) || '';
  await cmd('/farming replant off', 400);
  const q = bot.inventory.slots.filter(it => it && /quality/.test(loreOf(it))).length;
  bot.quit();
  await sleep(4000);
  await connect();
  await sleep(3000);
  const at1 = await cmd('/farming', 800);
  const after = saw(/crops discovered \d+ of \d+/, at1) || '';
  check('Discoveries survive a relog', before !== '' && before === after, `${before} / ${after}`);
  check('Replant\'s switch survives a relog', chat.slice(at1).some(m => /Replant .*off/.test(m)));
  const q2 = bot.inventory.slots.filter(it => it && /quality/.test(loreOf(it))).length;
  check('Quality items survive a relog', q2 === q, `${q} → ${q2}`);
  await cmd('/farming replant on', 400);
}

(async () => {
  await connect();
  await sleep(3000);
  const phases = { harvest: harvestPhase, placed: placedPhase, pick: pickPhase, creative: creativePhase, machines: machinesPhase,
    replant: replantPhase, wide: widePhase, protect: protectPhase, fortune: fortunePhase, quality: qualityPhase, menu: menuPhase, relog: relogPhase };
  const run = phaseArg === 'all' ? ['harvest', 'placed', 'pick', 'creative', 'machines', 'replant', 'wide', 'fortune', 'quality', 'menu', 'relog'] : phaseArg.split(',');
  for (const p of run) {
    if (!phases[p]) { console.log('unknown phase ' + p); continue; }
    console.log(`\n── ${p} ──`);
    try { await phases[p](); } catch (e) { check(`${p} ran to the end`, false, e.stack.split('\n').slice(0, 2).join(' ')); }
  }
  bot.setControlState('sneak', false);
  await cmd(`/cerb skill ${NAME} Farming level 1`, 250);
  await cmd('/clear @s', 300);
  await cmd('/effect clear @s', 300);
  await cmd('/gamerule random_tick_speed 3', 300);
  await cmd('/gamerule spawn_mobs true', 300);
  const passed = results.filter(Boolean).length;
  console.log(`\n${passed}/${results.length} passed`);
  if (failed.length) console.log('FAILED: ' + failed.join(' | '));
  bot.quit();
  setTimeout(() => process.exit(failed.length ? 1 : 0), 500);
})();
