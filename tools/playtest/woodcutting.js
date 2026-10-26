// Woodcutting in game (docs/design/WOODCUTTING.md). LOCAL SANDBOX servers only; the bot must be op.
// Usage: node woodcutting.js [phase[,phase...]] [botName]
//   cut       - a natural log pays its wood's XP with an axe; by hand nothing (and a hint); too weak an axe a quarter
//               (and a hint), a fitting one in full; the weak-resource rule
//   sources   - every source of a log on its own: a tree the world grew pays in full, a tree a player planted that grew
//               on its own 80%, one grown with bone meal half (with a HUD line), one grown by a dispenser's bone meal half,
//               a log placed by hand nothing (with a HUD line), and nothing either after a wind charge hit it; Felling
//               takes grown trees, and leaves a grown tree a build touches standing
//   wild      - trees the world generator made, in chunks generated for the test (/locate a forest far away): each log
//               pays its wood's XP
//   felling   - Felling I-III: Sneak + cut brings the tree down (logs at the stump, XP and durability for every log); not
//               without Sneak, not below level 10, not past the level's size, not a giant trunk below 50; the rest between
//               fellings; never a build (no natural leaves, or leaves a player placed); a placed log touching the tree
//               stays; a natural tree a build touches stays (with the same tree alone as the control), and so does one
//               with a log palisade against its base; the axe never breaks; a real vanilla tree (/place feature)
//   protect   - a protection plugin's area stops a felling (needs the sandbox-only CerbTestProtect plugin, built by
//               tools/playtest/protect/build.sh), with the same tree felled whole as the control
//   fortune   - Fortune for wood at Woodcutting 100 (about a quarter more logs, at most one per log) and none from
//               Mining 100; Mining's ore bonus doesn't come from Woodcutting
//   speed     - chopping speed with an axe in hand, gathering speed with anything else (the block_break_speed attribute)
//   heartwood - a heartwood drop (chance raised to 100% in the sandbox's resources.yml for the phase; needs SERVER_DIR);
//               it can't be placed, burnt or crafted into planks; the grip on an axe (faster chopping, once per axe) and
//               the limbs on a bow
//   discover  - the first log of each wood pays its discovery once, and the Woodcutting page counts it (use a fresh
//               bot name: discoveries are saved)
//   custom    - the synthetic custom tree (mushroom stems as logs; written into the sandbox's resources.yml for the
//               phase and put back; needs SERVER_DIR): XP, discovery, heartwood and Felling with no code change
//   canopy    - Woodcutting 75: a felled vanilla tree's leaves that would decay come down at once; at 74 they stay
//   machines  - a crafter never makes an upgrade (any axe + stripped dark oak wood once gave the recipe's own copper axe)
//               or crafts with heartwood; a furnace never makes heartwood into charcoal; each with a working control
//   oldtree-set / oldtree-check - a birch grown with bone meal on the build before grown trees paid (oldtree-set, run on
//               that build; STATE=<file> keeps where), then, on this build, it still pays nothing and the HUD says it grew
//               before grown trees paid, while a tree grown now pays its share
//   all       - cut, sources, wild, felling, fortune, speed (the default)
const mineflayer = require('mineflayer');
const { Vec3 } = require('vec3');
const fs = require('fs');
const path = require('path');
const phaseArg = process.argv[2] || 'all';
const NAME = process.argv[3] || 'CerbSmoke';
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
const wood = async () => totalXp(await skill('Woodcutting'));
async function setLevel(name, level) { await cmd(`/cerb skill ${NAME} ${name} level ${level}`, 500); }

// ── the arena: grass far from spawn, a fresh area each call; leaves never decay while it runs ──
// Placed-block marks live in chunk data and outlive /fill, so an area is never reused: one left by an earlier run would
// carry its marks (a bone-mealed tree's logs cut down to one, a placed log) into this run's trees.
let X = 0, Y = 0, Z = 0;
async function arena() {
  const sp = bot.spawnPoint || bot.entity.position;
  X = Math.floor(sp.x) + 2000 + (Date.now() % 20000) * 96; Y = 110; Z = Math.floor(sp.z) - 400;
  await cmd('/gamemode creative', 300);
  await cmd(`/tp @s ${X + 0.5} ${Y + 1} ${Z + 0.5}`, 1000);
  try { await bot.waitForChunksToLoad(); } catch (e) { /* the fill retries */ }
  await cmd('/gamerule spawn_mobs false', 300);
  await cmd('/gamerule random_tick_speed 0', 300); // no leaf decay, no sapling growth unless bone meal
  await cmd('/time set day', 300);
  for (let i = 0; i < 20; i++) {
    const a = await cmd(`/fill ${X - 8} ${Y - 1} ${Z - 8} ${X + 60} ${Y - 1} ${Z + 8} grass_block`, 600);
    if (!saw(/not loaded/i, a)) break;
    await sleep(1000);
  }
  // two fills: one /fill changes at most 32,768 blocks
  await cmd(`/fill ${X - 8} ${Y} ${Z - 8} ${X + 26} ${Y + 30} ${Z + 8} air`, 900);
  await cmd(`/fill ${X + 27} ${Y} ${Z - 8} ${X + 60} ${Y + 30} ${Z + 8} air`, 900);
  await cmd('/kill @e[type=item,distance=..60]', 300);
  await cmd('/clear @s', 400);
  await cmd('/gamemode survival', 300);
  await cmd('/effect clear @s', 300);
  await cmd('/effect give @s resistance infinite 4 true', 300);
  await cmd('/effect give @s saturation infinite 1 true', 300);
  await cmd('/xpdisplay compact', 300);
  await stand(0, -3);
}
async function stand(dx, dz, yaw = 0, pitch = 0) { await cmd(`/tp @s ${X + dx + 0.5} ${Y} ${Z + dz + 0.5} ${yaw} ${pitch}`, 500); }
const at = (dx, dy, dz) => new Vec3(X + dx, Y + dy, Z + dz);
const blockName = (dx, dy, dz) => { const b = bot.blockAt(at(dx, dy, dz)); return b ? b.name : '?'; };
async function fill(x1, y1, z1, x2, y2, z2, block, wait = 250) { await cmd(`/fill ${X + x1} ${Y + y1} ${Z + z1} ${X + x2} ${Y + y2} ${Z + z2} ${block}`, wait); }
async function setblock(dx, dy, dz, block, wait = 300) { await cmd(`/setblock ${X + dx} ${Y + dy} ${Z + dz} ${block}`, wait); }

/**
 * A tree the world could have grown: a trunk of `height` logs (2x2 when `giant`) and a crown of leaves around its top,
 * placed with /fill (no player placed it, so nothing remembers it as placed; /fill's leaves are the decaying kind).
 */
async function tree(dx, dz, height, woodName = 'oak', { giant = false, leaves = null } = {}) {
  const w = giant ? 1 : 0;
  const leaf = leaves || `${woodName}_leaves`;
  await fill(dx, 0, dz, dx + w, height - 1, dz + w, `${woodName}_log`);
  await fill(dx - 2, height - 2, dz - 2, dx + 2 + w, height - 1, dz + 2 + w, `${leaf} replace air`);
  await fill(dx - 1, height, dz - 1, dx + 1 + w, height, dz + 1 + w, `${leaf} replace air`);
  await sleep(300);
}
/** Logs of `woodName` left in the column(s) of a tree at (dx, dz). */
function logsLeft(dx, dz, height, woodName = 'oak', giant = false) {
  let n = 0;
  for (let y = 0; y < height + 1; y++) for (let a = 0; a <= (giant ? 1 : 0); a++) for (let b = 0; b <= (giant ? 1 : 0); b++) {
    if (blockName(dx + a, y, dz + b) === `${woodName}_log`) n++;
  }
  return n;
}

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
/** Fill every free inventory slot with dirt, so drops stay on the ground where they can be counted. */
async function stuff() {
  const free = bot.inventory.emptySlotCount();
  if (free > 0) await cmd(`/give @s dirt ${free * 64}`, 800);
}
const loreOf = it => (it && it.customLore ? it.customLore.map(l => {
  try { return require('prismarine-chat')(bot.registry).fromNotch(l).toString(); } catch (e) { return String(l); }
}).join(' | ') : '');
const nameOf = it => {
  if (!it || !it.customName) return '';
  try { return require('prismarine-chat')(bot.registry).fromNotch(it.customName).toString(); } catch (e) { return String(it.customName); }
};
/** The Woodcutting page's woods tile (Player Menu → Skills → Utility → Woodcutting, slot 33): "name | lore". */
async function woodsTile() {
  await cmd('/menu', 1500);
  if (!bot.currentWindow) return '(no menu)';
  const find = re => { for (let i = 0; i < 54; i++) if (re.test(nameOf(bot.currentWindow && bot.currentWindow.slots[i]))) return i; return -1; };
  let out = '(no Woodcutting tile)';
  try {
    await bot.clickWindow(19, 0, 0); await sleep(1000); // Skills
    await bot.clickWindow(24, 0, 0); await sleep(1000); // Utility Skills
    const w = find(/^Woodcutting/);
    if (w >= 0) {
      await bot.clickWindow(w, 0, 0); await sleep(1200);
      const it = bot.currentWindow && bot.currentWindow.slots[33];
      out = it ? nameOf(it) + ' | ' + loreOf(it) : '(slot 33 empty)';
    }
  } catch (e) { out = '(' + e.message + ')'; }
  if (bot.currentWindow) bot.closeWindow(bot.currentWindow);
  await sleep(400);
  return out;
}
/** Item entities within `r` of a spot: [{name, count, lore}]. */
async function dropsNear(dx, dy, dz, r = 4, clear = true) {
  await sleep(300);
  const p = at(dx, dy, dz).offset(0.5, 0.5, 0.5);
  const out = [];
  for (const e of Object.values(bot.entities)) {
    if (e.name !== 'item' || e.position.distanceTo(p) > r) continue;
    try { const d = e.getDroppedItem(); if (d) out.push({ name: d.name, count: d.count, lore: loreOf(d) }); } catch (err) { /* skip */ }
  }
  if (clear) await cmd(`/kill @e[type=item,x=${X + dx},y=${Y + dy},z=${Z + dz},distance=..${r + 4}]`, 300);
  return out;
}
const count = (list, name) => list.filter(d => d.name === name).reduce((a, d) => a + d.count, 0);
const entityCount = (list, name) => list.filter(d => d.name === name).length;
const heldDamage = () => { const h = bot.heldItem; return h ? (h.durabilityUsed ?? -1) : -1; };

// ── cutting ───────────────────────────────────────────────────────
let seq = 9000;
/**
 * Cut the block at (dx, dy, dz) with what is in hand, by raw dig packets (start, wait, finish) so the timing is the
 * server's. `sneak`: hold Sneak for the cut. `after`: run right after the finish packet (same tick).
 */
async function cut(dx, dy, dz, { sneak = false, ms = 700, after = null } = {}) {
  const b = bot.blockAt(at(dx, dy, dz));
  if (!b || b.name === 'air') return false;
  await bot.lookAt(b.position.offset(0.5, 0.5, 0.5), true);
  if (sneak) { bot.setControlState('sneak', true); await sleep(200); }
  bot._client.write('block_dig', { status: 0, location: b.position, face: 1, sequence: seq++ });
  bot.swingArm();
  await sleep(ms);
  bot._client.write('block_dig', { status: 2, location: b.position, face: 1, sequence: seq++ });
  if (after) after();
  await sleep(450);
  if (sneak) { bot.setControlState('sneak', false); await sleep(150); }
  return true;
}
/** A cut's XP: Woodcutting total before and after. */
async function cutXp(dx, dy, dz, opts = {}) {
  const before = await wood();
  await cut(dx, dy, dz, opts);
  await sleep(300);
  return (await wood()) - before;
}

// ════════════════════════════════════════════════════════════════
async function cutPhase() {
  await arena();
  await setLevel('Woodcutting', 1);
  await give('iron_axe'); await give('wooden_axe');
  await hold('iron_axe');
  // The first log of a wood also pays its discovery: cut one of each wood used here first.
  for (const [i, w] of [['oak', 0], ['pale_oak', 2]].map(([w, i]) => [i, w])) await setblock(i, 0, 3, `${w}_log`);
  await stand(1, 1, 0);
  await cut(0, 0, 3); await cut(2, 0, 3);
  await cmd('/kill @e[type=item,distance=..20]', 300);

  await setblock(0, 0, 3, 'oak_log');
  check('A natural oak log with an iron axe: 4 Woodcutting XP', (await cutXp(0, 0, 3)) === 4);
  await setblock(0, 0, 3, 'oak_log');
  await holdNothing();
  let t = Date.now();
  let g = await cutXp(0, 0, 3, { ms: 3200 });
  check('By hand: no XP, and the HUD says to use an axe', g === 0 && hudSince(t).some(h => /with an axe/.test(h)), `+${g}; ${JSON.stringify(hudSince(t).slice(-2))}`);
  await cmd('/kill @e[type=item,distance=..20]', 300);

  // Pale oak wants an iron axe: a wooden one pays a quarter (10 x 0.25 = 2.5, fractions carry over two logs).
  await hold('wooden_axe');
  t = Date.now();
  await setblock(2, 0, 3, 'pale_oak_log'); await setblock(2, 1, 3, 'pale_oak_log');
  const before = await wood();
  await cut(2, 0, 3, { ms: 1600 }); await cut(2, 1, 3, { ms: 1600 });
  g = (await wood()) - before;
  check('Pale oak with a wooden axe: a quarter (2 logs: 5 XP), and the HUD names the axe that pays in full', g === 5
    && hudSince(t).some(h => /Pale oak.*iron axe/.test(h)), `+${g}; ${JSON.stringify(hudSince(t).slice(-2))}`);
  await hold('iron_axe');
  await setblock(2, 0, 3, 'pale_oak_log');
  check('...with an iron axe: 10 XP', (await cutXp(2, 0, 3)) === 10);
  // The weak-resource rule: 20+ levels above oak (level 1), a quarter.
  await setLevel('Woodcutting', 21);
  await setblock(0, 0, 3, 'oak_log');
  check('At Woodcutting 21 an oak log pays a quarter (1 XP)', (await cutXp(0, 0, 3)) === 1);
  await setLevel('Woodcutting', 1);
  await cmd('/kill @e[type=item,distance=..20]', 300);
}

/** Birch logs in the column at (dx, dz), from the ground up (a birch is a single trunk, 5-7 logs). */
const birchLogs = (dx, dz) => { let n = 0; for (let y = 0; y < 12; y++) if (blockName(dx, y, dz) === 'birch_log') n++; return n; };
/** Plant a birch sapling on the arena floor at (dx, dz), as a player does (right-click on the grass). */
async function plant(dx, dz) {
  await stand(dx, dz - 2);
  await hold('birch_sapling');
  try { await bot.placeBlock(bot.blockAt(at(dx, -1, dz)), new Vec3(0, 1, 0)); } catch (e) { /* checked by the caller */ }
  await sleep(500);
  return blockName(dx, 0, dz) === 'birch_sapling';
}
/** Fell the tree at (dx, dz) with Sneak + cut from (dx, dz - 3): its logs before, the XP it paid, the HUD lines. */
async function fellXp(dx, dz) {
  const logs = birchLogs(dx, dz);
  await hold('iron_axe');
  await stand(dx, dz - 3);
  const t = Date.now();
  const before = await wood();
  await cut(dx, 0, dz, { sneak: true });
  await sleep(900);
  const xp = (await wood()) - before;
  return { logs, left: birchLogs(dx, dz), xp, hud: hudSince(t).filter(h => !/^\S+⛨\s+\S+♥\s+[+\d]/.test(h)).slice(-4) };
}

/** Bone-meal the sapling at (dx, dz) until it is a tree (the bot holds bone meal and right-clicks it). */
async function boneMealGrow(dx, dz) {
  await hold('bone_meal');
  for (let i = 0; i < 16 && blockName(dx, 0, dz) !== 'birch_log'; i++) {
    try { await bot.activateBlock(bot.blockAt(at(dx, 0, dz)), new Vec3(0, 1, 0)); } catch (e) { /* retry */ }
    await sleep(350);
  }
  return blockName(dx, 0, dz) === 'birch_log';
}

const OLD_TREE_STATE = process.env.STATE || path.join(require('os').tmpdir(), 'cerb-oldtree.json');
async function oldTreeSet() {
  await arena();
  await give('birch_sapling', 2); await give('bone_meal', 32);
  const ok = (await plant(0, 3)) && (await boneMealGrow(0, 3));
  fs.writeFileSync(OLD_TREE_STATE, JSON.stringify({ X, Y, Z, logs: birchLogs(0, 3) }));
  check('A birch grown with bone meal on this build, recorded for oldtree-check', ok && birchLogs(0, 3) >= 4, `${birchLogs(0, 3)} logs at ${X} ${Y} ${Z + 3}`);
}
async function oldTreeCheck() {
  const st = JSON.parse(fs.readFileSync(OLD_TREE_STATE, 'utf8'));
  X = st.X; Y = st.Y; Z = st.Z;
  await cmd('/gamemode creative', 300);
  await cmd(`/tp @s ${X + 0.5} ${Y + 1} ${Z + 0.5}`, 1500);
  try { await bot.waitForChunksToLoad(); } catch (e) { /* best effort */ }
  await cmd('/gamerule random_tick_speed 0', 300);
  await cmd('/clear @s', 300);
  await cmd('/gamemode survival', 300);
  await cmd('/effect give @s resistance infinite 4 true', 300);
  await setLevel('Woodcutting', 15);
  await give('iron_axe'); await give('birch_sapling', 2); await give('bone_meal', 32);
  await hold('iron_axe');
  await setblock(-3, 0, 3, 'birch_log'); // birch discovered first, so its one-time bonus doesn't count below
  await stand(-3, 0);
  await cut(-3, 0, 3);
  const logs = birchLogs(0, 3);
  await stand(0, 0);
  const t = Date.now();
  const xp = await cutXp(0, 0, 3);
  check('A tree grown before grown trees paid: still remembered as placed, no XP, and the HUD says it grew before',
    logs >= 4 && xp === 0 && hudSince(t).some(h => /grew before grown trees paid/.test(h)), `${logs} logs, +${xp}; ${JSON.stringify(hudSince(t).slice(-1))}`);
  const ok = (await plant(6, 3)) && (await boneMealGrow(6, 3));
  await stand(6, 0);
  await hold('iron_axe');
  const xp2 = await cutXp(6, 0, 3);
  check('...while a tree grown with bone meal now pays half (2 XP a log)', ok && xp2 === 2, `grew=${ok}, +${xp2}`);
  await cmd('/kill @e[type=item,distance=..30]', 300);
}

async function sourcesPhase() {
  // Every source of a log, measured on its own (docs/design/WOODCUTTING.md §5): a tree the world grew, a tree a player
  // planted that grew on its own, one grown with bone meal, a log placed by hand. Birch (level 1, 4 XP a log) at
  // Woodcutting 15: in full, and Felling I takes the whole trunk (birches grow 5-7 logs).
  await arena();
  await setLevel('Woodcutting', 15);
  await give('iron_axe'); await give('birch_log', 2); await give('birch_sapling', 6); await give('bone_meal', 64);
  await stuff();
  // Birch discovered first, so its one-time bonus doesn't count below.
  await hold('iron_axe');
  await setblock(-3, 0, 3, 'birch_log');
  await stand(-3, 0);
  await cut(-3, 0, 3);
  // A tree the world grew (/place feature makes it the way world generation does): every log in full.
  await cmd(`/place feature minecraft:birch ${X} ${Y} ${Z + 3}`, 900);
  const nat = await fellXp(0, 3);
  check('A tree the world grew: Felling takes it, every log pays in full (4 XP)', nat.logs >= 4 && nat.left === 0 && nat.xp === nat.logs * 4,
    JSON.stringify(nat));
  await cmd('/kill @e[type=item,distance=..40]', 300);
  await sleep(2500); // the rest after a felling
  // A tree grown from a planted sapling with bone meal.
  const bm = await plant(6, 3);
  await hold('bone_meal');
  for (let i = 0; i < 16 && blockName(6, 0, 3) !== 'birch_log'; i++) {
    try { await bot.activateBlock(bot.blockAt(at(6, 0, 3)), new Vec3(0, 1, 0)); } catch (e) { /* retry */ }
    await sleep(350);
  }
  const bone = await fellXp(6, 3);
  check('A tree grown with bone meal: Felling takes it, and it pays half (2 XP a log)', bm && bone.logs >= 4 && bone.left === 0
    && Math.abs(bone.xp - bone.logs * 2) <= 1, JSON.stringify(bone));
  check('...and the HUD says why it pays less', bone.hud.some(h => /bone meal/i.test(h)), JSON.stringify(bone.hud));
  await cmd('/kill @e[type=item,distance=..40]', 300);
  await sleep(2500);
  // A tree grown from a planted sapling on its own: random ticks sped up, no bone meal.
  const gp = await plant(12, 3);
  await cmd('/gamerule random_tick_speed 1000', 300);
  for (let i = 0; i < 120 && blockName(12, 0, 3) !== 'birch_log'; i++) await sleep(250);
  await cmd('/gamerule random_tick_speed 0', 300);
  const grown = await fellXp(12, 3);
  check('A tree a player planted that grew on its own: Felling takes it, and it pays 80% (3.2 XP a log)', gp && grown.logs >= 4
    && grown.left === 0 && Math.abs(grown.xp - grown.logs * 3.2) <= 1, JSON.stringify(grown));
  await cmd('/kill @e[type=item,distance=..40]', 300);
  await sleep(2500);
  // A log placed by hand: nothing, and the HUD says so.
  await hold('birch_log');
  await stand(18, 0);
  try { await bot.placeBlock(bot.blockAt(at(18, -1, 2)), new Vec3(0, 1, 0)); } catch (e) { /* checked below */ }
  await sleep(500);
  const placedOk = blockName(18, 0, 2) === 'birch_log';
  await hold('iron_axe');
  let t = Date.now();
  const p0 = await cutXp(18, 0, 2);
  check('A log placed by hand: no XP, and the HUD says so', placedOk && p0 === 0 && hudSince(t).some(h => /placed by hand/i.test(h)),
    `placed=${placedOk}, +${p0}; ${JSON.stringify(hudSince(t).slice(-1))}`);
  // A wind charge "explodes" without destroying anything: the placed log beside it stays placed (it once became natural).
  await hold('birch_log');
  await stand(20, 0);
  try { await bot.placeBlock(bot.blockAt(at(20, -1, 2)), new Vec3(0, 1, 0)); } catch (e) { /* checked below */ }
  await sleep(500);
  const placed2 = blockName(20, 0, 2) === 'birch_log';
  await setblock(20, 1, 2, 'oak_trapdoor[open=false]'); // the burst opens it: proof that it reached the log
  await give('wind_charge', 2);
  await hold('wind_charge');
  await bot.lookAt(at(20, 0, 2).offset(0.5, 0.5, 0.5), true);
  bot.activateItem(); await sleep(150); bot.deactivateItem();
  await sleep(1200);
  const reached = !!(bot.blockAt(at(20, 1, 2)) && bot.blockAt(at(20, 1, 2)).getProperties().open);
  await setblock(20, 1, 2, 'air');
  await stand(20, 0);
  await hold('iron_axe');
  const p1 = await cutXp(20, 0, 2);
  check('A log placed by hand, hit by a wind charge first (the trapdoor on it opened): still no XP', placed2 && reached
    && blockName(20, 0, 2) === 'air' && p1 === 0, `placed=${placed2}, burst reached=${reached}, +${p1}`);
  // A grown tree a build touches (a plank against its trunk): Felling leaves it standing, the cut log still pays.
  await plant(24, 3);
  await hold('bone_meal');
  for (let i = 0; i < 16 && blockName(24, 0, 3) !== 'birch_log'; i++) {
    try { await bot.activateBlock(bot.blockAt(at(24, 0, 3)), new Vec3(0, 1, 0)); } catch (e) { /* retry */ }
    await sleep(350);
  }
  await setblock(25, 1, 3, 'birch_planks');
  const built = await fellXp(24, 3);
  check('A grown tree a build touches: one log (2 XP), the rest stands, and the HUD says why', built.logs >= 4 && built.left === built.logs - 1
    && built.xp === 2 && built.hud.some(h => /touches a build/.test(h)), JSON.stringify(built));
  await cmd('/kill @e[type=item,distance=..40]', 300);
  await sleep(2500);
  // A dispenser's bone meal (a tree farm's): bone meal too, half the XP, although the game's grow event says otherwise.
  const dp = await plant(30, 3);
  await setblock(30, 0, 2, 'dispenser[facing=south]{Items:[{Slot:0b,id:"minecraft:bone_meal",count:64}]}');
  for (let i = 0; i < 24 && blockName(30, 0, 3) !== 'birch_log'; i++) {
    await setblock(31, 0, 2, 'redstone_block', 150); await setblock(31, 0, 2, 'air', 250);
  }
  const dgrew = blockName(30, 0, 3) === 'birch_log';
  await setblock(30, 0, 2, 'air'); // the dispenser out of the way (no build next to the tree)
  const disp = await fellXp(30, 3);
  check('A tree grown by a dispenser\'s bone meal: Felling takes it, and it pays half (2 XP a log)', dp && dgrew && disp.logs >= 4
    && disp.left === 0 && Math.abs(disp.xp - disp.logs * 2) <= 1, `grew=${dgrew}; ${JSON.stringify(disp)}`);
  await cmd('/kill @e[type=item,distance=..40]', 300);
}

/** The Woodcutting XP a cut of the block at absolute position `pos` pays (survival, the axe in hand). */
async function cutXpAt(pos) {
  const before = await wood();
  const b = bot.blockAt(pos);
  await bot.lookAt(pos.offset(0.5, 0.5, 0.5), true);
  bot._client.write('block_dig', { status: 0, location: pos, face: 1, sequence: seq++ });
  bot.swingArm();
  await sleep(700);
  bot._client.write('block_dig', { status: 2, location: pos, face: 1, sequence: seq++ });
  await sleep(700);
  const gone = (bot.blockAt(pos) || {}).name !== (b || {}).name;
  return { name: b ? b.name : '?', gone, xp: (await wood()) - before };
}

async function wildPhase() {
  // Trees the world generator made, in chunks the server generates for this test (nothing was ever placed or grown
  // there): every log pays like any natural tree. The owner's playtest question was a tree that paid nothing.
  await cmd('/gamemode creative', 300);
  await cmd('/gamerule spawn_mobs false', 300);
  await cmd('/time set day', 300);
  await cmd('/effect give @s resistance infinite 4 true', 300);
  await setLevel('Woodcutting', 1);
  // Oak, birch and spruce discovered first (logs set in an arena), so each wild log must pay exactly its wood's XP.
  await arena();
  await give('iron_axe'); await hold('iron_axe');
  for (const [i, w] of ['oak', 'birch', 'spruce'].entries()) await setblock(i * 2, 0, 3, `${w}_log`);
  await stand(2, 1, 0);
  for (const i of [0, 1, 2]) await cut(i * 2, 0, 3);
  await cmd('/gamemode creative', 300);
  const far = 30000 + (Date.now() % 97) * 160;
  await cmd(`/tp @s ${far} 200 ${far}`, 2500);
  let found = null;
  for (const biome of ['birch_forest', 'forest', 'taiga']) {
    const a = await cmd(`/locate biome minecraft:${biome}`, 4000);
    const m = (saw(/is at \[/, a) || '').match(/\[(-?\d+), (?:-?\d+|~), (-?\d+)\]/);
    if (m) { found = { biome, x: Number(m[1]), z: Number(m[2]) }; break; }
  }
  if (!found) { check('A forest could be located for the test', false); return; }
  await cmd(`/tp @s ${found.x} 200 ${found.z}`, 3000);
  try { await bot.waitForChunksToLoad(); } catch (e) { /* best effort */ }
  await sleep(1500);
  const names = ['oak_log', 'birch_log', 'spruce_log'];
  const ids = names.map(n => bot.registry.blocksByName[n].id);
  const soil = new Set(['grass_block', 'dirt', 'podzol', 'coarse_dirt', 'moss_block', 'rooted_dirt']);
  const solid = p => { const b = bot.blockAt(p); return b && b.boundingBox === 'block' && !names.includes(b.name) && !/leaves/.test(b.name); };
  const air = p => { const b = bot.blockAt(p); return b && (b.name === 'air' || b.name === 'short_grass' || b.name === 'fern'); };
  const bases = bot.findBlocks({ matching: ids, maxDistance: 64, count: 600 })
    .filter(p => soil.has((bot.blockAt(p.offset(0, -1, 0)) || {}).name))
    .filter(p => { for (let y = 2; y < 12; y++) for (let a = -2; a <= 2; a++) for (let c = -2; c <= 2; c++) {
      const b = bot.blockAt(p.offset(a, y, c)); if (b && /leaves/.test(b.name)) return true; } return false; });
  const results = [];
  for (const p of bases) {
    if (results.length >= 3) break;
    const spot = [[1, 0], [-1, 0], [0, 1], [0, -1]].map(([a, c]) => p.offset(a, 0, c))
      .find(q => air(q) && air(q.offset(0, 1, 0)) && solid(q.offset(0, -1, 0)));
    if (!spot) continue;
    await cmd('/gamemode creative', 250);
    await cmd(`/tp @s ${spot.x + 0.5} ${spot.y} ${spot.z + 0.5}`, 900);
    await cmd('/gamemode survival', 300);
    if (!bot.inventory.items().some(i => i.name === 'iron_axe')) await give('iron_axe');
    await hold('iron_axe');
    results.push({ at: `${p.x},${p.y},${p.z}`, ...(await cutXpAt(p)) });
  }
  await cmd('/gamemode creative', 300);
  await cmd('/kill @e[type=item,distance=..30]', 300);
  check(`Trees the world generated (a ${found.biome} at ${found.x}, ${found.z}, chunks made for the test): each log pays its wood's XP`,
    results.length >= 2 && results.every(r => r.gone && r.xp === (r.name === 'spruce_log' ? 5 : 4)), JSON.stringify(results));
}

async function fellingPhase() {
  await arena();
  await give('iron_axe'); await hold('iron_axe');
  await stuff();
  const axeInHand = async () => { if (!bot.heldItem || bot.heldItem.name !== 'iron_axe') await hold('iron_axe'); };
  // Level 9: no Felling yet.
  await setLevel('Woodcutting', 9);
  await tree(0, 3, 5);
  await stand(0, 1, 0);
  await cut(0, 0, 3, { sneak: true });
  check('Woodcutting 9, Sneak + cut: one log (Felling opens at 10)', logsLeft(0, 3, 5) === 4, `${logsLeft(0, 3, 5)} left`);
  // Level 10, no Sneak: one log.
  await setLevel('Woodcutting', 10);
  await cut(0, 1, 3);
  check('Woodcutting 10 without Sneak: one log', logsLeft(0, 3, 5) === 3, `${logsLeft(0, 3, 5)} left`);
  await cmd('/kill @e[type=item,distance=..30]', 300);
  // Level 10, Sneak: the whole 5-log tree, at the stump, XP and durability for every log.
  await tree(4, 3, 5);
  await stand(4, 1, 0);
  let before = await wood();
  const dmg0 = heldDamage();
  await cut(4, 0, 3, { sneak: true });
  await sleep(800);
  const left = logsLeft(4, 3, 5), d = await dropsNear(4, 0, 3, 3), g = (await wood()) - before, dmg = heldDamage() - dmg0;
  check('Woodcutting 10, Sneak + cut on a 5-log oak: the whole tree comes down', left === 0, `${left} left`);
  check('...its logs land at the stump, merged (at least 5 logs in at most 2 stacks)', count(d, 'oak_log') >= 5 && entityCount(d, 'oak_log') <= 2,
    `${count(d, 'oak_log')} logs in ${entityCount(d, 'oak_log')} stacks`);
  check('...20 XP (4 per log) and 5 points of durability', g === 20 && (dmg === 5 || dmg0 < 0), `+${g}, durability ${dmg} (${dmg0})`);
  await sleep(2000);
  // Too big for the level: an 8-log oak at 10 (Felling I takes 6).
  await tree(12, 3, 8);
  await stand(12, 1, 0);
  let t = Date.now();
  await cut(12, 0, 3, { sneak: true });
  check('An 8-log tree at Woodcutting 10: one log, and the HUD gives the limit', logsLeft(12, 3, 8) === 7
    && hudSince(t).some(h => /more than 6 logs/.test(h)), `${logsLeft(12, 3, 8)} left; ${JSON.stringify(hudSince(t).slice(-1))}`);
  await setLevel('Woodcutting', 25);
  await sleep(500);
  // At 25 (Felling II) the 7 logs left come down.
  await cut(12, 1, 3, { sneak: true });
  await sleep(300);
  check('...at 25 (Felling II, 24) the rest of it comes down', logsLeft(12, 3, 8) === 0, `${logsLeft(12, 3, 8)} left`);
  await cmd('/kill @e[type=item,distance=..40]', 300);
  await sleep(2500);
  // A giant trunk (2x2) at 25: refused; at 50: felled.
  await tree(16, 3, 5, 'dark_oak', { giant: true });
  await tree(8, 3, 4); // for the rest, below
  await stand(16, 1, 0);
  t = Date.now();
  await cut(16, 0, 3, { sneak: true });
  check('A 2x2 dark oak at Woodcutting 25: one log, "giant trunks fall from Woodcutting 50"', logsLeft(16, 3, 5, 'dark_oak', true) === 19
    && hudSince(t).some(h => /Giant trunks fall from Woodcutting 50/.test(h)), `${logsLeft(16, 3, 5, 'dark_oak', true)} left`);
  await setLevel('Woodcutting', 50);
  await cut(17, 0, 3, { sneak: true });
  await sleep(800);
  check('...at 50 (Felling III) the giant comes down', logsLeft(16, 3, 5, 'dark_oak', true) === 0, `${logsLeft(16, 3, 5, 'dark_oak', true)} left`);
  // The rest after those 20 logs (6 s): a tree cut right after gets one log, and the HUD says when.
  await stand(9, 1, 0);
  t = Date.now();
  await cut(8, 0, 3, { sneak: true, ms: 500 });
  check('Right after a felling the next waits: one log, and the HUD says when', logsLeft(8, 3, 4) === 3
    && hudSince(t).some(h => /ready again in \d+ s/.test(h)), `${logsLeft(8, 3, 4)} left; ${JSON.stringify(hudSince(t).slice(-1))}`);
  await cmd('/kill @e[type=item,distance=..40]', 300);
  await sleep(6500);

  // Never a build: logs with no natural leaves, or with leaves a player placed.
  await fill(20, 0, 3, 20, 4, 3, 'oak_log');
  await stand(20, 1, 0);
  t = Date.now();
  await cut(20, 0, 3, { sneak: true });
  check('A log tower with no leaves (a build): one log, and the HUD says it is no natural tree', logsLeft(20, 3, 5) === 4
    && hudSince(t).some(h => /only trees with their own leaves/.test(h)), `${logsLeft(20, 3, 5)} left`);
  await fill(19, 4, 2, 21, 5, 4, 'oak_leaves[persistent=true] replace air');
  await cut(20, 1, 3, { sneak: true });
  check('...with leaves a player placed: still one log', logsLeft(20, 3, 5) === 3, `${logsLeft(20, 3, 5)} left`);
  // A placed log touching a natural tree stays.
  await tree(24, 3, 5);
  await cmd('/clear @s dirt 128', 400); // room for the log
  await give('oak_log', 1);
  await hold('oak_log');
  await stand(27, 3, 90);
  let err = '';
  try { await bot.placeBlock(bot.blockAt(at(24, 1, 3)), new Vec3(1, 0, 0)); } catch (e) { err = e.message; }
  await sleep(400);
  const fence = blockName(25, 1, 3) === 'oak_log';
  await stuff();
  await axeInHand();
  await stand(24, 1, 0);
  await cut(24, 0, 3, { sneak: true });
  await sleep(700);
  check('A log a player placed against a natural tree: the tree falls, the placed log stays', fence && logsLeft(24, 3, 5) === 0
    && blockName(25, 1, 3) === 'oak_log', `placed=${fence}${err ? ' (' + err + ')' : ''}, tree ${logsLeft(24, 3, 5)} left, placed log now ${blockName(25, 1, 3)}`);
  await cmd('/kill @e[type=item,distance=..40]', 300);
  await sleep(2500);

  // The axe never breaks: an iron axe 3 uses from breaking fells only what it can take.
  await cmd('/clear @s iron_axe', 300);
  await cmd('/clear @s dirt 64', 300);
  await give('iron_axe[damage=247]'); // 250 uses: 3 left
  await hold('iron_axe');
  await stuff();
  await tree(28, 3, 6);
  await stand(28, 1, 0);
  await cut(28, 0, 3, { sneak: true });
  await sleep(700);
  const kept = !!bot.heldItem && bot.heldItem.name === 'iron_axe';
  check('An axe 3 uses from breaking: the felling stops before it breaks', kept && logsLeft(28, 3, 6) >= 3, `axe kept=${kept}, ${logsLeft(28, 3, 6)} left`);
  await cmd('/clear @s iron_axe', 300);
  await cmd('/clear @s dirt 64', 300);
  await give('iron_axe'); await hold('iron_axe');
  await stuff();
  await sleep(2500);

  // The axe leaves the hand while a 128-log giant comes down (8 logs a tick): the felling stops part-way.
  await setLevel('Woodcutting', 100);
  await tree(32, 3, 32, 'oak', { giant: true });
  await stand(32, 1, 0);
  const slot = bot.quickBarSlot;
  await cut(32, 0, 3, { sneak: true, ms: 700, after: () => setTimeout(() => bot.setQuickBarSlot((slot + 1) % 8), 120) });
  await sleep(1500);
  bot.setQuickBarSlot(slot);
  const rest = logsLeft(32, 3, 32, 'oak', true);
  check('Switching away from the axe while a giant comes down: the felling stops part-way', rest > 0 && rest < 127, `${rest} of 128 left`);
  await cmd('/kill @e[type=item,distance=..60]', 300);
  await sleep(20000); // the rest after a 128-log felling at 100 (19 s)

  // A natural tree a build touches (a plank against its trunk, as an old log house or a village built against a tree,
  // whose logs nothing remembers as placed): left standing, and the HUD says why. Control: the same tree alone falls.
  await axeInHand();
  await tree(44, 3, 5);
  await setblock(45, 1, 3, 'oak_planks');
  await stand(44, 1, 0);
  t = Date.now();
  await cut(44, 0, 3, { sneak: true });
  await sleep(500);
  check('A natural tree a build touches (a plank against its trunk): one log, and the HUD says why', logsLeft(44, 3, 5) === 4
    && hudSince(t).some(h => /touches a build/.test(h)), `${logsLeft(44, 3, 5)} left; ${JSON.stringify(hudSince(t).slice(-1))}`);
  await tree(50, 3, 5);
  await stand(50, 1, 0);
  await cut(50, 0, 3, { sneak: true });
  await sleep(500);
  check('...control: the same tree with no build against it comes down', logsLeft(50, 3, 5) === 0, `${logsLeft(50, 3, 5)} left`);
  await cmd('/kill @e[type=item,distance=..60]', 300);
  await sleep(1500);
  // A palisade of logs only (no planks, no torch), two high and three long, against a natural tree: nothing remembers its
  // logs (an old build, or /fill here), and no build block touches it. The tree's base is then wider than a trunk.
  await tree(55, 3, 5);
  await fill(56, 0, 3, 58, 1, 3, 'oak_log');
  await stand(55, 1, 0);
  t = Date.now();
  await cut(55, 0, 3, { sneak: true });
  await sleep(500);
  let pal = 0;
  for (let x = 56; x <= 58; x++) for (let y = 0; y <= 1; y++) if (blockName(x, y, 3) === 'oak_log') pal++;
  check('A log palisade against a natural tree: one log, the palisade stands, and the HUD says why', logsLeft(55, 3, 5) === 4 && pal === 6
    && hudSince(t).some(h => /wider than a trunk/.test(h)), `${logsLeft(55, 3, 5)} left, palisade ${pal}/6; ${JSON.stringify(hudSince(t).slice(-1))}`);
  await cmd('/kill @e[type=item,distance=..60]', 300);
  await sleep(1500);

  // A real vanilla oak (the world's generator, not /fill), if this server allows /place.
  await axeInHand();
  const pa = await cmd(`/place feature minecraft:oak ${X + 38} ${Y} ${Z + 3}`, 900);
  if (saw(/Unknown|error|Failed|not/i, pa)) { note('/place feature is not available here: skipped'); return; }
  await stand(38, 1, 0);
  let real = 0;
  for (let y = 0; y < 8; y++) if (blockName(38, y, 3) === 'oak_log') real++;
  t = Date.now();
  await cut(38, 0, 3, { sneak: true });
  await sleep(800);
  let after = 0;
  for (let y = 0; y < 12; y++) for (let a = -3; a <= 3; a++) for (let b = 0; b <= 6; b++) if (blockName(38 + a, y, b) === 'oak_log') after++;
  check('A vanilla oak from the world generator: felled whole', real >= 4 && after === 0, `${real} trunk logs before, ${after} logs after; ${JSON.stringify(hudSince(t).slice(-1))}`);
  await cmd('/kill @e[type=item,distance=..60]', 300);
}

async function protectPhase() {
  await arena();
  await give('iron_axe'); await hold('iron_axe');
  await setLevel('Woodcutting', 25);
  const a = await cmd('/testprotect off', 600);
  if (!saw(/testprotect/, a)) { check('protect needs the sandbox-only CerbTestProtect plugin (tools/playtest/protect/build.sh)', false); return; }
  // The upper half of an 8-log tree is someone's claim.
  await tree(0, 3, 8);
  await cmd(`/testprotect ${X - 3} ${Y + 4} ${Z} ${X + 3} ${Y + 12} ${Z + 6}`, 500);
  await stand(0, 1, 0);
  await cut(0, 0, 3, { sneak: true });
  await sleep(700);
  const kept = [];
  for (let y = 0; y < 8; y++) kept.push(blockName(0, y, 3) === 'oak_log' ? 1 : 0);
  check('A claim over the top half: the felling stops at its edge (logs 0-3 down, 4-7 still up)', kept.join('') === '00001111', kept.join(''));
  await cmd('/testprotect off', 500);
  await cmd('/kill @e[type=item,distance=..30]', 300);
  await sleep(3000);
  await tree(4, 3, 8);
  await stand(4, 1, 0);
  await cut(4, 0, 3, { sneak: true });
  await sleep(700);
  check('Control: the same tree with no claim comes down whole', logsLeft(4, 3, 8) === 0, `${logsLeft(4, 3, 8)} left`);
  await cmd('/kill @e[type=item,distance=..30]', 300);
}

async function fortunePhase() {
  await arena();
  await give('iron_axe'); await hold('iron_axe');
  await stuff();
  // Woodcutting 100: Fortune 50 on wood, a 25% chance of one more log. One felling of a 64-log tree.
  for (const s of ['Mining', 'Fishing', 'Trading', 'Scavenging', 'Lockpicking']) await setLevel(s, 1);
  await setLevel('Woodcutting', 100);
  await tree(0, 3, 32, 'oak', { giant: true }); // 128 logs
  await stand(0, 1, 0);
  await cut(0, 0, 3, { sneak: true });
  await sleep(2500);
  const d = await dropsNear(0, 0, 3, 4);
  const logs = count(d, 'oak_log');
  check('Woodcutting 100, a felled 128-log tree: about a quarter more logs (148-172), never two per log', logs >= 148 && logs <= 172 && logs <= 256,
    `${logs} logs from 128`);
  // Mining 100 and Woodcutting 1: no wood bonus (cut by hand, 30 logs).
  await setLevel('Woodcutting', 1);
  await setLevel('Mining', 100);
  await sleep(6000);
  let extra = 0;
  for (let i = 0; i < 30; i++) {
    await setblock(0, 0, 3, 'oak_log', 150);
    await cut(0, 0, 3, { ms: 450 });
    extra += count(await dropsNear(0, 0, 3, 3), 'oak_log') - 1;
  }
  // The bot's own baseline Fortune (level-1 skills) gives about 1.5% a log; Mining 100 counting on wood would give ~26%,
  // about 8 extra in 30. At most 3 tells them apart without failing on chance.
  check('Mining 100 gives no extra logs (30 logs cut: at most 3 extra, from the baseline Fortune)', extra <= 3, `${extra} extra`);
  // ...and Woodcutting gives no extra ore: Woodcutting 100, Mining 1, 30 iron ores.
  await setLevel('Mining', 1);
  await setLevel('Woodcutting', 100);
  await cmd('/clear @s iron_axe', 300);
  await give('iron_pickaxe'); await hold('iron_pickaxe');
  extra = 0;
  for (let i = 0; i < 30; i++) {
    await setblock(0, 0, 3, 'iron_ore', 150);
    await cut(0, 0, 3, { ms: 700 });
    extra += count(await dropsNear(0, 0, 3, 3), 'raw_iron') - 1;
  }
  check('Woodcutting 100 gives no extra ore (30 iron ores at Mining 1: at most 3 extra, from the baseline Fortune)', extra <= 3, `${extra} extra`);
  await setLevel('Woodcutting', 1);
}

async function attributeValue() {
  const a = await cmd('/attribute @s minecraft:block_break_speed get', 600);
  const m = (saw(/block[_ ]break[_ ]speed.* is ([\d.]+)/i, a) || saw(/is ([\d.]+)/, a) || '').match(/([\d.]+)\s*$/);
  return m ? Number(m[1]) : NaN;
}
async function speedPhase() {
  await arena();
  await give('iron_axe'); await give('iron_pickaxe');
  await setLevel('Mining', 1);
  await setLevel('Woodcutting', 100);
  await sleep(500);
  await hold('iron_axe');
  const axeW = await attributeValue();
  await hold('iron_pickaxe');
  const pickW = await attributeValue();
  check('Woodcutting 100: chopping speed with an axe in hand (about x1.14: 20 points toward the 25% cap)', axeW > 1.13 && axeW < 1.15, `axe ${axeW}`);
  check('...not with a pickaxe in hand', pickW < 1.01, `pickaxe ${pickW}`);
  await setLevel('Woodcutting', 1);
  await setLevel('Mining', 100);
  await sleep(500);
  const pickM = await attributeValue();
  await hold('iron_axe');
  const axeM = await attributeValue();
  check('Mining 100: gathering speed with the pickaxe, not with an axe', pickM > 1.13 && axeM < 1.01, `pickaxe ${pickM}, axe ${axeM}`);
  await setLevel('Mining', 1);
}

/** Write `edit(text)` into the sandbox's resources.yml for the phase, /cerb reload; put it back after. */
async function withResources(edit, body) {
  const dir = process.env.SERVER_DIR;
  if (!dir) { check('this phase needs SERVER_DIR (the sandbox server folder)', false); return; }
  const file = path.join(dir, 'plugins', 'CerberusPlugin', 'resources.yml');
  const original = fs.readFileSync(file, 'utf8');
  const edited = edit(original);
  if (edited === original) { check('the phase found what it edits in the sandbox resources.yml', false); return; }
  try {
    fs.writeFileSync(file, edited);
    await cmd('/cerb reload', 2500);
    await body();
  } finally {
    fs.writeFileSync(file, original);
    await cmd('/cerb reload', 2500);
  }
}

async function heartwoodPhase() {
  await arena();
  await withResources(s => s.replace(/(  oak:[^\n]*\n[^\n]*heartwood: )0\.002/, '$11.0'), async () => {
    await setLevel('Woodcutting', 1);
    await give('iron_axe'); await hold('iron_axe');
    await stuff();
    await setblock(0, 0, 3, 'oak_log');
    await stand(0, 1, 0);
    await cut(0, 0, 3);
    const d = await dropsNear(0, 0, 3, 3);
    const hw = d.filter(x => x.name === 'stripped_dark_oak_wood');
    check('Heartwood drops from a natural log (chance raised to 100% here), with its lore', hw.length === 1 && /craft it with an axe/i.test(hw[0].lore || ''),
      JSON.stringify(d));
    await cmd('/clear @s dirt', 300);
    await cmd('/cerb gather item heartwood 4', 600);
    // It can't be placed.
    await hold('stripped_dark_oak_wood');
    try { await bot.placeBlock(bot.blockAt(at(2, -1, 0)), new Vec3(0, 1, 0)); } catch (e) { /* checked below */ }
    await sleep(500);
    check('Heartwood can\'t be placed as a block', blockName(2, 0, 0) === 'air', blockName(2, 0, 0));
    // A crafting table: heartwood alone makes no planks; with an iron axe (a Masterwork one), the grip.
    await setblock(2, 0, 0, 'crafting_table');
    await give('iron_axe');
    {
      const idx = bot.inventory.slots.findIndex((it, i) => it && it.name === 'iron_axe' && !loreOf(it) && i >= 9 && i !== bot.quickBarSlot + 36);
      if (idx >= 36) bot.setQuickBarSlot(idx - 36); else if (idx >= 0) { try { await bot.clickWindow(idx, 0, 2); } catch (e) {} }
      await sleep(400);
      await cmd('/cerb gather quality masterwork', 600);
    }
    const smith0 = totalXp(await skill('Smithing'));
    const tbl = bot.blockAt(at(2, 0, 0));
    await bot.lookAt(tbl.position.offset(0.5, 0.5, 0.5), true);
    let w = await bot.openBlock(tbl);
    await sleep(500);
    const findIn = name => { for (let i = w.inventoryStart; i < w.inventoryEnd; i++) if (w.slots[i] && w.slots[i].name === name) return i; return -1; };
    const click = async (slot, button = 0, mode = 0) => { try { await bot.clickWindow(slot, button, mode); } catch (e) { /* the server decides */ } await sleep(250); };
    /** One of `name` into window slot `to`: pick the stack up, right-click one down, put the rest back. */
    const putOne = async (name, to) => { const from = findIn(name); if (from < 0) return false; await click(from); await click(to, 1); await click(from); return true; };
    await putOne('stripped_dark_oak_wood', 1);
    await sleep(400);
    check('Heartwood alone in a crafting grid makes no planks', !w.slots[0], w.slots[0] ? w.slots[0].name : 'nothing');
    const axeSlot = (() => { for (let i = w.inventoryStart; i < w.inventoryEnd; i++) if (w.slots[i] && w.slots[i].name === 'iron_axe' && /Masterwork/.test(loreOf(w.slots[i]))) return i; return -1; })();
    await click(axeSlot); await click(2);
    await sleep(500);
    const result = w.slots[0];
    check('A Masterwork iron axe and a heartwood: the same axe, still Masterwork, with a heartwood grip', !!result && result.name === 'iron_axe'
      && /Heartwood grip/.test(loreOf(result)) && /Masterwork quality/.test(loreOf(result)), result ? `${result.name}: ${loreOf(result)}` : 'nothing');
    await click(0, 0, 1); // shift-click the result into the inventory
    await sleep(400);
    bot.closeWindow(w);
    await sleep(400);
    const gripped = bot.inventory.items().find(it => it.name === 'iron_axe' && /Heartwood grip/.test(loreOf(it)));
    check('...taken: the grip is on the axe in the inventory, one heartwood used', !!gripped && bot.inventory.items().filter(it => it.name === 'stripped_dark_oak_wood').reduce((a, it) => a + it.count, 0) === 3,
      gripped ? loreOf(gripped) : 'no gripped axe');
    const smith1 = totalXp(await skill('Smithing'));
    check('...an upgrade is no forging: no Smithing XP, and the quality is kept (never rerolled)', smith1 === smith0 && /Masterwork quality/.test(loreOf(gripped)),
      `Smithing +${smith1 - smith0}`);
    // The grip is felt: chopping speed with that axe beats a plain one.
    const plainIdx = bot.inventory.slots.findIndex(it => it && it.name === 'iron_axe' && !/Heartwood grip/.test(loreOf(it)));
    const grippedIdx = bot.inventory.slots.findIndex(it => it && it.name === 'iron_axe' && /Heartwood grip/.test(loreOf(it)));
    const holdSlot = async idx => { if (idx >= 36) { bot.setQuickBarSlot(idx - 36); } else { try { await bot.clickWindow(idx, 0, 2); } catch (e) {} } await sleep(500); };
    await holdSlot(plainIdx);
    const plain = await attributeValue();
    await holdSlot(bot.inventory.slots.findIndex(it => it && it.name === 'iron_axe' && /Heartwood grip/.test(loreOf(it))));
    const grip = await attributeValue();
    check('The grip in hand: faster chopping than a plain axe (about +7%)', grip > plain + 0.05, `plain ${plain}, grip ${grip}`);
    // A second grip on the same axe: nothing.
    w = await bot.openBlock(tbl);
    await sleep(500);
    const g2 = (() => { for (let i = w.inventoryStart; i < w.inventoryEnd; i++) if (w.slots[i] && w.slots[i].name === 'iron_axe' && /Heartwood grip/.test(loreOf(w.slots[i]))) return i; return -1; })();
    await click(g2); await click(1);
    await putOne('stripped_dark_oak_wood', 2);
    await sleep(500);
    check('An axe that has a grip gets no second one', !w.slots[0], w.slots[0] ? loreOf(w.slots[0]) : 'nothing');
    for (const s of [1, 2]) if (w.slots[s]) await click(s, 0, 1);
    // A bow: heartwood limbs.
    bot.closeWindow(w);
    await give('bow');
    w = await bot.openBlock(tbl);
    await sleep(500);
    await click(findIn('bow')); await click(1);
    await putOne('stripped_dark_oak_wood', 2);
    await sleep(500);
    check('A bow and a heartwood: heartwood limbs', !!w.slots[0] && w.slots[0].name === 'bow' && /Heartwood limbs/.test(loreOf(w.slots[0])),
      w.slots[0] ? loreOf(w.slots[0]) : 'nothing');
    for (const s of [1, 2]) if (w.slots[s]) await click(s, 0, 1);
    bot.closeWindow(w);
    // Not fuel: a furnace with heartwood as its only fuel never lights.
    await setblock(4, 0, 0, 'furnace[facing=west]');
    await give('raw_iron', 1);
    const f = bot.blockAt(at(4, 0, 0));
    await bot.lookAt(f.position.offset(0.5, 0.5, 0.5), true);
    w = await bot.openBlock(f);
    await sleep(500);
    await click(findIn('raw_iron')); await click(0);
    await putOne('stripped_dark_oak_wood', 1);
    await sleep(2500);
    check('Heartwood is never burnt as fuel', !!w.slots[1] && w.slots[1].name === 'stripped_dark_oak_wood' && w.slots[1].count === 1,
      w.slots[1] ? `${w.slots[1].name} x${w.slots[1].count}` : 'fuel gone');
    for (const s of [0, 1]) if (w.slots[s]) await click(s, 0, 1);
    bot.closeWindow(w);
  });
}

async function discoverPhase() {
  await arena();
  await setLevel('Woodcutting', 1);
  const before = await woodsTile();
  check('The Woodcutting page lists the woods to find, with their level and axe', /^Woods: 0 of \d+ discovered/.test(before)
    && /✘ Birch · level 1 · any axe/.test(before) && /✘ Pale oak · level 35 · iron axe/.test(before), before);
  await give('iron_axe'); await hold('iron_axe');
  await stand(0, 1, 0);
  let at0 = chat.length;
  await setblock(0, 0, 3, 'birch_log');
  const first = await cutXp(0, 0, 3);
  await setblock(0, 0, 3, 'birch_log');
  const second = await cutXp(0, 0, 3);
  const lines = chat.slice(at0).filter(m => /[Dd]iscover/.test(m));
  check('The first birch log: 4 XP and its discovery (20); the next one 4', first === 24 && second === 4, `first +${first}, second +${second}`);
  check('...one discovery line in chat', lines.length === 1 && /Birch/.test(lines[0]), JSON.stringify(lines));
  const after = await woodsTile();
  check('...and the page counts it: 1 discovered, Birch ticked', /^Woods: 1 of \d+ discovered/.test(after) && /✔ Birch/.test(after)
    && /✘ Oak/.test(after), after);
  await cmd('/kill @e[type=item,distance=..20]', 300);
}

async function customPhase() {
  await arena();
  // A new id each run: a discovery is saved on the player, and this check pays the discovery.
  const id = 'test_mushroom_' + Date.now().toString(36);
  const entry = `  ${id}: { skill: Woodcutting, blocks: [MUSHROOM_STEM], tool: axe, tier: 0, level: 1, xp: 7, fortune: wood,\n`
    + '                   tree: { leaves: [RED_MUSHROOM_BLOCK, BROWN_MUSHROOM_BLOCK], heartwood: 1.0, discovery-xp: 33 } }\n';
  await withResources(s => s.replace(/\n# A resource pays a quarter/, '\n' + entry + '\n# A resource pays a quarter'), async () => {
    await setLevel('Woodcutting', 10);
    await give('iron_axe'); await hold('iron_axe');
    await stuff();
    // A synthetic tree: four stems under a cap of red mushroom blocks.
    await fill(0, 0, 3, 0, 3, 3, 'mushroom_stem');
    await fill(-2, 4, 1, 2, 4, 5, 'red_mushroom_block');
    await stand(0, 1, 0);
    let before = await wood();
    await cut(0, 0, 3);
    let d = await dropsNear(0, 0, 3, 3);
    let g = (await wood()) - before;
    check('A custom tree from resources.yml alone: 7 XP a stem plus its discovery (33)', g === 40, `+${g}`);
    check('...its heartwood (100% here)', d.some(x => x.name === 'stripped_dark_oak_wood'), JSON.stringify(d));
    before = await wood();
    await cut(0, 1, 3, { sneak: true });
    await sleep(700);
    let left = 0;
    for (let y = 0; y < 4; y++) if (blockName(0, y, 3) === 'mushroom_stem') left++;
    g = (await wood()) - before;
    check('...and Felling: Sneak + cut brings the rest of the stems down, 7 XP each', left === 0 && g === 21, `${left} left, +${g}`);
    await cmd('/kill @e[type=item,distance=..20]', 300);
  });
}

// Machines: a crafter fires none of the crafting-grid events, and a furnace takes any log as charcoal's input. (Folia has
// no /item or /data, so the bot fills them through their windows.)
const GIVE = { axe: ['/give @s wooden_axe', 'wooden_axe'], wood: ['/give @s stripped_dark_oak_wood', 'stripped_dark_oak_wood'],
  heartwood: ['/cerb gather item heartwood', 'stripped_dark_oak_wood'], log: ['/give @s oak_log', 'oak_log'] };
/** A crafter facing south at (dx, 0, dz), one of each item in its first slots, pulsed once: what it puts out. */
async function crafter(dx, dz, items) {
  await cmd('/clear @s', 300);
  for (const k of items) await cmd(GIVE[k][0], 500);
  await setblock(dx, 0, dz, 'air', 150);
  await setblock(dx, 0, dz, 'crafter[orientation=south_up]', 500);
  await stand(dx, dz - 2);
  const block = bot.blockAt(at(dx, 0, dz));
  await bot.lookAt(block.position.offset(0.5, 0.5, 0.5), true);
  const want = items.map(k => GIVE[k][1]);
  let filled = [];
  for (let attempt = 0; attempt < 3 && filled.join() !== want.join(); attempt++) {
    const win = await bot.openBlock(block);
    await sleep(600); // the window's contents first, or the server refuses clicks made on a stale view
    let g = 0;
    for (const name of want) {
      if (win.slots[g] && win.slots[g].name === name) { g++; continue; }
      const idx = win.slots.findIndex((it, i) => it && it.name === name && i >= win.inventoryStart);
      if (idx < 0) break;
      await bot.clickWindow(idx, 0, 0); await sleep(350);  // pick it up
      await bot.clickWindow(g++, 1, 0); await sleep(350);  // one into the grid
      if (win.selectedItem) { await bot.clickWindow(idx, 0, 0); await sleep(350); }
    }
    bot.closeWindow(win); await sleep(500);
    const check2 = await bot.openBlock(block); // what the server has, read back
    await sleep(600);
    filled = check2.slots.slice(0, 9).filter(Boolean).map(it => it.name);
    bot.closeWindow(check2); await sleep(400);
  }
  const before = new Set(Object.keys(bot.entities));
  await setblock(dx, 0, dz - 1, 'redstone_block', 2500); // a rising edge: one craft; the crafter throws what it makes
  const c = at(dx, 0, dz).offset(0.5, 0.5, 0.5);
  const d = [];
  for (const [id, e] of Object.entries(bot.entities)) {
    if (before.has(id) || e.name !== 'item' || e.position.distanceTo(c) > 8) continue;
    try { const it = e.getDroppedItem(); if (it) d.push({ name: it.name, count: it.count }); } catch (err) { /* skip */ }
  }
  await setblock(dx, 0, dz - 1, 'air', 200);
  await cmd(`/kill @e[type=item,x=${X + dx},y=${Y},z=${Z + dz},distance=..10]`, 300);
  return { filled, d };
}
/** A furnace at (dx, 0, dz) given the item and a coal, then 11 s: what is in its output and input. */
async function furnace(dx, dz, item) {
  await cmd('/clear @s', 300);
  await cmd(GIVE[item][0], 500); await cmd('/give @s coal', 500);
  await setblock(dx, 0, dz, 'furnace', 500);
  await stand(dx, dz - 2);
  const block = bot.blockAt(at(dx, 0, dz));
  await bot.lookAt(block.position.offset(0.5, 0.5, 0.5), true);
  const f = await bot.openFurnace(block);
  await f.putInput(bot.inventory.items().find(i => i.name === GIVE[item][1]).type, null, 1);
  await f.putFuel(bot.inventory.items().find(i => i.name === 'coal').type, null, 1);
  await sleep(11000);
  const out = f.outputItem(), inp = f.inputItem();
  f.close(); await sleep(300);
  return { out: out ? out.name : null, input: inp ? inp.name : null };
}

async function machinesPhase() {
  await arena();
  const show = r => `grid [${r.filled}] -> ${JSON.stringify(r.d.map(x => [x.name, x.count]))}`;
  // Control: the crafter works in this setup (an oak log makes planks).
  let r = await crafter(0, 0, ['log']);
  check('Control: a crafter with an oak log makes 4 oak planks', count(r.d, 'oak_planks') === 4, show(r));
  // The upgrade recipe in a crafter: it handed out the recipe's own result (a copper axe) for any axe and any stripped
  // dark oak wood, until crafters were handled.
  r = await crafter(3, 0, ['axe', 'wood']);
  check('A crafter with a wooden axe and stripped dark oak wood makes nothing (no free copper axe)', r.filled.length === 2 && r.d.length === 0, show(r));
  r = await crafter(6, 0, ['axe', 'heartwood']);
  check('...with a real heartwood: still nothing (an upgrade is made by a player at a crafting table)', r.filled.length === 2 && r.d.length === 0, show(r));
  r = await crafter(9, 0, ['heartwood']);
  check('A crafter never makes heartwood into planks', r.filled.length === 1 && count(r.d, 'dark_oak_planks') === 0, show(r));
  // A furnace: an oak log makes charcoal (control); heartwood doesn't, and stays in the furnace.
  const ctl = await furnace(12, 0, 'log');
  check('Control: a furnace makes charcoal from an oak log', ctl.out === 'charcoal', JSON.stringify(ctl));
  const hw = await furnace(15, 0, 'heartwood');
  check('A furnace never makes heartwood into charcoal; it stays in the furnace', hw.out === null && hw.input === 'stripped_dark_oak_wood', JSON.stringify(hw));
  for (const x of [0, 3, 6, 9, 12, 15]) await setblock(x, 0, 0, 'air', 120);
  await cmd('/kill @e[type=item,distance=..30]', 300);
  await cmd('/clear @s', 300);
}

async function canopyPhase() {
  await arena();
  await give('iron_axe'); await hold('iron_axe');
  await stuff();
  const leavesAround = (cx, cz) => {
    let n = 0;
    for (let y = 0; y < 12; y++) for (let a = -4; a <= 4; a++) for (let b = -4; b <= 4; b++) if (/leaves/.test(blockName(cx + a, y, cz + b))) n++;
    return n;
  };
  for (const [level, cx, canopy] of [[74, 4, false], [75, 16, true]]) {
    await setLevel('Woodcutting', level);
    const pa = await cmd(`/place feature minecraft:oak ${X + cx} ${Y} ${Z + 3}`, 900);
    if (saw(/Unknown|error|Failed/i, pa)) { check('canopy needs /place feature', false, chat.slice(pa).join(' / ')); return; }
    await sleep(500);
    const before = leavesAround(cx, 3);
    await stand(cx, 1, 0);
    await cut(cx, 0, 3, { sneak: true });
    await sleep(2500);
    const after = leavesAround(cx, 3);
    if (canopy) check('Woodcutting 75: the felled tree\'s leaves come down with it', before > 20 && after <= 2, `${before} leaves before, ${after} after`);
    else check('Woodcutting 74: the leaves stay (they decay on their own later)', before > 20 && after >= before - 2, `${before} leaves before, ${after} after`);
    await cmd('/kill @e[type=item,distance=..40]', 300);
    await sleep(4000);
  }
}

(async () => {
  await connect();
  await sleep(3000);
  const phases = { cut: cutPhase, sources: sourcesPhase, wild: wildPhase, felling: fellingPhase, protect: protectPhase, fortune: fortunePhase, speed: speedPhase,
    heartwood: heartwoodPhase, discover: discoverPhase, custom: customPhase, canopy: canopyPhase, machines: machinesPhase,
    'oldtree-set': oldTreeSet, 'oldtree-check': oldTreeCheck };
  const run = phaseArg === 'all' ? ['cut', 'sources', 'wild', 'felling', 'fortune', 'speed'] : phaseArg.split(',');
  for (const p of run) {
    if (!phases[p]) { console.log('unknown phase ' + p); continue; }
    console.log(`\n── ${p} ──`);
    try { await phases[p](); } catch (e) { check(`${p} ran to the end`, false, e.stack.split('\n').slice(0, 2).join(' ')); }
  }
  bot.setControlState('sneak', false);
  for (const s of ['Woodcutting', 'Mining']) await cmd(`/cerb skill ${NAME} ${s} level 1`, 250);
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
