// Herbalism in game (docs/design/HERBALISM.md). LOCAL SANDBOX servers only; the bot must be op.
// Usage: node herbalism.js [phase[,phase...]] [botName]
//   gather   - a wild poppy pays 3 XP and its 15 XP discovery once; a dandelion; far above a herb's level it pays a quarter
//   placed   - a poppy the bot placed by hand pays nothing (and a hint); a sunflower placed by hand pays nothing from its top
//              half either, a wild one does; flowers bone meal grew on grass pay in full
//   shears   - a fern broken by hand drops nothing of itself: no XP and a hint; with shears it pays
//   creative - creative pays nothing, and the XP display says why
//   machines - water and a piston break wild flowers: vanilla drops, nobody paid
//   fortune  - Fortune (herbs) at Herbalism 100: about a quarter of poppies drop a second poppy; about none at Herbalism 1
//   rare     - no rare reagent below 25; at 100 about 6% of poppies give a Bloodroot (a Cerberus item, its first a
//              discovery); a rare reagent is never placed or used in a vanilla recipe
//   quality  - Herbalism 100: about a third of the poppies carry a quality (lore); none at 49
//   bonemeal - bone meal on a pink petal placed by hand grows a second petal: still placed, it pays nothing; on a wild one
//              it pays
//   water    - Wildcrafting tall seagrass in a pool: both halves of each plant become water again, never an air pocket
//   wild     - Wildcrafting: Herbalism 10, shears and Sneak take every wild poppy of a 3x3 (5x5 at 40), each paid once with
//              one point of shears wear, the drops in one pile; a placed poppy and other herbs stay; not without Sneak,
//              shears, or below 10; shears about to break stop it
//   protect  - a protection plugin's claim stops a sweep at its edge (needs the sandbox-only CerbTestProtect plugin)
//   menu     - Player Menu → Skills → Herbalism → Herbarium and Rare reagents, and Collections → Herbs, count what
//              /herbalism counts
//   relog    - discoveries and rare finds survive a relog
//   technique - Herbalism 9 → 10 by a real gather: the level-up names "Wildcrafting 3×3"
//   all      - every phase above but protect (the default)
const mineflayer = require('mineflayer');
const { Vec3 } = require('vec3');
const phaseArg = process.argv[2] || 'all';
const NAME = process.argv[3] || 'CerbHerb';
const sleep = ms => new Promise(r => setTimeout(r, ms));
const chat = [];
const bars = [];
const results = [];
const failed = [];
const check = (name, ok, detail = '') => {
  results.push(!!ok);
  if (!ok) failed.push(name);
  console.log(`${ok ? 'PASS' : 'FAIL'}  ${name}${detail ? '  (' + detail + ')' : ''}`);
};
const note = s => console.log('      ' + s);
let bot, pchat;
function connect() {
  const b = mineflayer.createBot({ host: '127.0.0.1', port: Number(process.env.MC_PORT || 25565), username: NAME, version: '1.21.11', auth: 'offline' });
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
const herbXp = async () => totalXp(await skill('Herbalism'));
async function setLevel(level) { await cmd(`/cerb skill ${NAME} Herbalism level ${level}`, 500); }

// ── the arena: a fresh meadow far from spawn each call (placed-block marks live in chunk data and outlive /fill) ──
let X = 0, Y = 0, Z = 0;
async function arena() {
  const sp = bot.spawnPoint || bot.entity.position;
  X = Math.floor(sp.x) + 2000 + (Date.now() % 20000) * 96; Y = 110; Z = Math.floor(sp.z) - 400;
  await cmd('/gamemode creative', 300);
  // Never under a mountain: the arena clears only 9 blocks above its floor (farming.js found crops popping off in the dark).
  for (let tries = 0; ; tries++) {
    await cmd(`/tp @s ${X + 0.5} ${Y + 1} ${Z + 0.5}`, 1000);
    try { await bot.waitForChunksToLoad(); } catch (e) { /* the fill retries */ }
    const roof = roofOver();
    if (!roof || tries >= 9) { if (roof) note(`arena under terrain after ${tries + 1} tries (${roof})`); break; }
    note(`arena skipped: terrain above it (${roof})`);
    X += 5000 + Math.floor(Math.random() * 20000);
  }
  await cmd('/gamerule spawn_mobs false', 300);
  await cmd('/gamerule random_tick_speed 0', 300); // nothing spreads unless the test grows it
  await cmd('/time set day', 300);
  for (let i = 0; i < 20; i++) {
    const a = await cmd(`/fill ${X - 12} ${Y - 2} ${Z - 12} ${X + 24} ${Y - 2} ${Z + 12} stone`, 600);
    if (!saw(/not loaded/i, a)) break;
    await sleep(1000);
  }
  await cmd(`/fill ${X - 12} ${Y - 1} ${Z - 12} ${X + 24} ${Y - 1} ${Z + 12} grass_block`, 600);
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
/** The first block above the arena's cleared space (Y+9 up) that is not air, or null: the bot's own copy of the chunks. */
function roofOver() {
  for (let x = X - 12; x <= X + 24; x++) for (let z = Z - 12; z <= Z + 12; z++) for (let y = Y + 9; y < 320; y++) {
    const b = bot.blockAt(new Vec3(x, y, z));
    if (b && b.name !== 'air' && b.name !== 'cave_air' && b.name !== 'void_air') return `${b.name} at ${x} ${y} ${z}`;
  }
  return null;
}
async function stand(dx, dz, yaw = 0, pitch = 30) { await cmd(`/tp @s ${X + dx + 0.5} ${Y} ${Z + dz + 0.5} ${yaw} ${pitch}`, 500); }
const at = (dx, dy, dz) => new Vec3(X + dx, Y + dy, Z + dz);
const block = (dx, dy, dz) => bot.blockAt(at(dx, dy, dz));
const blockName = (dx, dy, dz) => { const b = block(dx, dy, dz); return b ? b.name : '?'; };
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
const loreOf = it => (it && it.customLore ? it.customLore.map(l => {
  try { return require('prismarine-chat')(bot.registry).fromNotch(l).toString(); } catch (e) { return String(l); }
}).join(' | ') : '');
const nameOf = it => {
  if (!it || !it.customName) return '';
  try { return require('prismarine-chat')(bot.registry).fromNotch(it.customName).toString(); } catch (e) { return String(it.customName); }
};
/** Item entities within `r` of a spot: [{name, count, lore, custom}]. */
async function dropsNear(dx, dy, dz, r = 3, clear = true) {
  await sleep(300);
  const p = at(dx, dy, dz).offset(0.5, 0.5, 0.5);
  const out = [];
  for (const e of Object.values(bot.entities)) {
    if (e.name !== 'item' || e.position.distanceTo(p) > r) continue;
    try { const d = e.getDroppedItem(); if (d) out.push({ name: d.name, count: d.count, lore: loreOf(d), custom: nameOf(d) }); } catch (err) { /* skip */ }
  }
  if (clear) await cmd(`/kill @e[type=item,x=${X + dx},y=${Y + dy},z=${Z + dz},distance=..${r + 3}]`, 300);
  return out;
}
const count = (list, name) => list.filter(d => d.name === name).reduce((a, d) => a + d.count, 0);
const heldDamage = () => { const h = bot.heldItem; return h ? (h.durabilityUsed ?? -1) : -1; };

// ── gathering ─────────────────────────────────────────────────────
let seq = 9000;
/** Break the block at (dx, dy, dz) by raw dig packets (plants break at once). */
async function brk(dx, dy, dz, { sneak = false, ms = 80 } = {}) {
  let b = block(dx, dy, dz);
  for (let i = 0; i < 10 && (!b || b.name === 'air'); i++) { await sleep(150); b = block(dx, dy, dz); } // a fresh arena's updates
  if (!b || b.name === 'air') return false;
  await bot.lookAt(b.position.offset(0.5, 0.5, 0.5), true);
  if (sneak) { bot.setControlState('sneak', true); await sleep(200); }
  bot._client.write('block_dig', { status: 0, location: b.position, face: 1, sequence: seq++ });
  bot.swingArm();
  await sleep(ms);
  bot._client.write('block_dig', { status: 2, location: b.position, face: 1, sequence: seq++ });
  await sleep(400);
  if (sneak) { bot.setControlState('sneak', false); await sleep(150); }
  return true;
}
async function brkXp(dx, dy, dz, opts = {}) {
  const before = await herbXp();
  await brk(dx, dy, dz, opts);
  await sleep(250);
  return (await herbXp()) - before;
}
async function use(dx, dy, dz) {
  const b = block(dx, dy, dz);
  await bot.lookAt(b.position.offset(0.5, 1, 0.5), true);
  try { await bot.activateBlock(b, new Vec3(0, 1, 0)); } catch (e) { /* the server decides */ }
  await sleep(450);
}
/** Place the held block on top of (dx, dy-1, dz) by hand. */
async function placeOn(dx, dy, dz) {
  try { await bot.placeBlock(block(dx, dy - 1, dz), new Vec3(0, 1, 0)); } catch (e) { /* checked by the caller */ }
  await sleep(400);
}
const HERBS = /^(poppy|dandelion|azure_bluet|oxeye_daisy|cornflower|red_tulip|orange_tulip|white_tulip|pink_tulip|allium|lily_of_the_valley|blue_orchid)$/;

// ════════════════════════════════════════════════════════════════
async function gatherPhase() {
  await arena();
  await cmd(`/cerb herb herbs ${NAME} reset`, 600);
  await setLevel(1);
  await holdNothing();
  await stand(0, -2, 0);
  await setblock(0, 0, 0, 'poppy');
  if (process.env.HERB_DIAG) { // what the server says about the poppy, and the HUD after its break
    await bot.lookAt(at(0, 0, 0).offset(0.5, 0.3, 0.5), true);
    const i = await cmd('/cerb herb info', 700);
    note('info: ' + chat.slice(i).join(' / '));
  }
  const t0 = Date.now();
  let g = await brkXp(0, 0, 0);
  if (process.env.HERB_DIAG) note('HUD after the break: ' + JSON.stringify(hudSince(t0)));
  check('The first wild poppy: 3 XP and its 15 XP discovery', g === 18 && chat.some(m => /Discovered: Poppy/.test(m)), `+${g}`);
  await setblock(0, 0, 0, 'poppy');
  check('The next poppy: 3 XP, no second discovery', (await brkXp(0, 0, 0)) === 3);
  await setblock(1, 0, 0, 'dandelion');
  check('A dandelion: 3 XP and its own discovery', (await brkXp(1, 0, 0)) === 18);
  // The weak-plant rule: a poppy is level 1, a quarter from Herbalism 21.
  await setLevel(21);
  await fill(0, 0, 0, 3, 0, 0, 'poppy');
  const w0 = await herbXp();
  for (let x = 0; x < 4; x++) await brk(x, 0, 0);
  g = (await herbXp()) - w0;
  check('At Herbalism 21 a poppy pays a quarter (4 gathers: 3 XP)', g === 3, `+${g}`);
  await setLevel(1);
  await cmd('/kill @e[type=item,distance=..20]', 300);
}

async function placedPhase() {
  await arena();
  await setLevel(1);
  await stand(0, -2, 0);
  await give('poppy', 2);
  await hold('poppy');
  await placeOn(0, 0, 0);
  check('The bot placed a poppy by hand', blockName(0, 0, 0) === 'poppy', blockName(0, 0, 0));
  await holdNothing();
  let t = Date.now();
  let g = await brkXp(0, 0, 0);
  check('A poppy placed by hand: no XP, and the HUD says why', blockName(0, 0, 0) === 'air' && g === 0
    && hudSince(t).some(h => /placed by hand pays no Herbalism XP/.test(h)), `+${g}; ${JSON.stringify(hudSince(t).slice(-2))}`);
  await setblock(0, 0, 0, 'poppy');
  check('...and a wild poppy at the same spot pays again (the mark went with the placed one)', (await brkXp(0, 0, 0)) === 3);
  // A tall flower: placed by hand, its top half pays nothing either; a wild one pays from its top.
  await give('sunflower', 1); await hold('sunflower');
  await placeOn(2, 0, 0);
  await holdNothing();
  check('The bot placed a sunflower by hand (two blocks tall)', blockName(2, 0, 0) === 'sunflower' && blockName(2, 1, 0) === 'sunflower',
    `${blockName(2, 0, 0)} / ${blockName(2, 1, 0)}`);
  await setLevel(15);
  g = await brkXp(2, 1, 0);
  check('...breaking its top half pays nothing (judged by its lower half)', g === 0 && blockName(2, 0, 0) === 'air', `+${g}, ${blockName(2, 0, 0)}`);
  await setblock(4, 0, 0, 'sunflower[half=lower]'); await setblock(4, 1, 0, 'sunflower[half=upper]');
  g = await brkXp(4, 1, 0);
  check('A wild sunflower broken at its top: 6 XP (and its 30 XP discovery the first time)', g === 6 || g === 36, `+${g}`);
  // Bone meal on grass: the flowers it grows pay in full.
  await setLevel(1);
  await cmd('/kill @e[type=item,distance=..20]', 300);
  await give('bone_meal', 8); await hold('bone_meal');
  await stand(8, -2, 0);
  for (let i = 0; i < 4; i++) await use(8, -1, 2);
  await holdNothing();
  let grown = null;
  for (let dx = 4; dx <= 12 && !grown; dx++) for (let dz = -2; dz <= 6 && !grown; dz++) {
    const n = blockName(dx, 0, dz);
    if (HERBS.test(n)) grown = [dx, dz, n];
  }
  if (!grown) { check('Bone meal on grass grew a flower', false, 'none grew in four uses'); return; }
  await stand(grown[0], grown[1] - 2, 0);
  g = await brkXp(grown[0], 0, grown[1]);
  check(`A ${grown[2]} that bone meal grew on grass pays in full`, g >= 3, `+${g}`);
  await cmd('/kill @e[type=item,distance=..30]', 300);
}

async function shearsPhase() {
  await arena();
  await setLevel(1);
  await holdNothing();
  await stand(0, -2, 0);
  await setblock(0, 0, 0, 'fern');
  const t = Date.now();
  let g = await brkXp(0, 0, 0);
  const d = await dropsNear(0, 0, 0);
  check('A fern broken by hand drops nothing of itself: no XP, and the HUD says shears gather it', g === 0 && count(d, 'fern') === 0
    && hudSince(t).some(h => /gathered with shears/.test(h)), `+${g}; ${JSON.stringify(hudSince(t).slice(-2))}`);
  await give('shears'); await hold('shears');
  await setblock(0, 0, 0, 'fern');
  const ferns0 = bot.inventory.items().filter(i => i.name === 'fern').reduce((a, i) => a + i.count, 0);
  g = await brkXp(0, 0, 0);
  const d2 = await dropsNear(0, 0, 0);
  const picked = bot.inventory.items().filter(i => i.name === 'fern').reduce((a, i) => a + i.count, 0) - ferns0; // the bot may pick it up
  check('With shears: the fern drops itself and pays 2 XP (and its 15 XP discovery the first time)', (g === 2 || g === 17) && count(d2, 'fern') + picked >= 1,
    `+${g}, ${count(d2, 'fern')} fern`);
}

async function creativePhase() {
  await arena();
  await setLevel(1);
  await cmd('/gamemode creative', 400);
  await stand(0, -2, 0);
  await setblock(0, 0, 0, 'poppy');
  const t = Date.now();
  const g = await brkXp(0, 0, 0);
  await sleep(300);
  check('Creative: a wild poppy pays nothing, and the XP display says why', g === 0 && hudSince(t).some(h => /creative/i.test(h)),
    `+${g}; ${JSON.stringify(hudSince(t).slice(-3))}`);
  await cmd('/gamemode survival', 400);
}

async function machinesPhase() {
  await arena();
  await setLevel(1);
  await stand(0, -4, 0);
  await fill(0, 0, 0, 2, 0, 0, 'poppy');
  const w0 = await herbXp();
  await setblock(-1, 0, 0, 'water');
  await sleep(4000);
  const drops = await dropsNear(1, 0, 0, 12);
  const gone = [0, 1, 2].filter(x => blockName(x, 0, 0) !== 'poppy').length;
  check('Water washes wild poppies away: vanilla drops, no XP', gone === 3 && count(drops, 'poppy') >= 1 && (await herbXp()) - w0 === 0,
    `${gone} of 3 washed away, ${count(drops, 'poppy')} poppies on the ground`);
  await setblock(-1, 0, 0, 'air');
  await setblock(5, 0, 0, 'dandelion');
  await setblock(5, 0, -1, 'piston[facing=south]');
  const m0 = await herbXp();
  await setblock(5, 0, -2, 'redstone_block');
  await sleep(1200);
  const md = await dropsNear(5, 0, 1, 4);
  check('A piston breaks a dandelion: it drops, no XP', blockName(5, 0, 0) !== 'dandelion' && count(md, 'dandelion') >= 1 && (await herbXp()) - m0 === 0,
    `${blockName(5, 0, 0)}; ${count(md, 'dandelion')} dropped`);
}

/** Gather {@code n} wild poppies at {@code level} in rows of 7; what landed on the ground, and the server's own counts. */
async function gatherMany(n, level, hand = null) {
  await setLevel(level);
  if (hand) await hold(hand); else await holdNothing();
  const st = await cmd('/cerb herb stats', 500);
  const num = (out, re) => +(((saw(/\[Herbalism\]/, out) || '').match(re) || [0, 0])[1]);
  const f0 = num(st, /Fortune extra (\d+)/), r0 = num(st, /rare reagents (\d+)/);
  let poppies = 0, quality = 0, rare = 0;
  const lore = [], rareNames = [];
  for (let i = 0; i < n; i += 7) {
    await fill(-3, 0, 2, 3, 0, 2, 'poppy', 400);
    for (let dx = -3; dx <= 3; dx++) await brk(dx, 0, 2, { ms: 40 });
    const d = await dropsNear(0, 0, 2, 5);
    for (const it of d) {
      if (it.name === 'poppy') { poppies += it.count; if (it.lore && /quality/.test(it.lore)) { quality += it.count; lore.push(it.lore); } }
      else if (it.custom) { rare += it.count; rareNames.push(it.custom); }
    }
  }
  const st2 = await cmd('/cerb herb stats', 500);
  const gathered = Math.ceil(n / 7) * 7;
  const out = { poppies, extra: poppies - gathered, quality, lore, rare, rareNames, rolled: num(st2, /Fortune extra (\d+)/) - f0, rolledRare: num(st2, /rare reagents (\d+)/) - r0 };
  note(`Herbalism ${level}: ${poppies} poppies (${out.extra} extra, server ${out.rolled}), ${quality} with a quality, ${rare} rare (server ${out.rolledRare}) ${JSON.stringify([...new Set(rareNames)])}`);
  return out;
}

async function fortunePhase() {
  await arena();
  await stand(0, -1, 0);
  const low = await gatherMany(42, 1);
  check('Herbalism 1: a poppy drops one poppy (Fortune 2: a 1% chance; at most 3 extra of 42)', low.extra <= 3, `${low.extra} extra`);
  const high = await gatherMany(84, 100);
  check('Herbalism 100: Fortune (herbs) 50, a 25% chance of a second poppy (84 gathers: 9-35 extra)', high.extra >= 9 && high.extra <= 35,
    `${high.extra} extra, server ${high.rolled}`);
  check('...never a second rare reagent from Fortune (the extras are poppies)', high.rolled === high.extra || Math.abs(high.rolled - high.extra) <= 1,
    `ground ${high.extra}, server ${high.rolled}`);
  await setLevel(1);
}

async function rarePhase() {
  await arena();
  await cmd(`/cerb herb herbs ${NAME} reset`, 600);
  await stand(0, -1, 0);
  const low = await gatherMany(56, 24);
  check('Herbalism 24: no rare reagent (they start at 25)', low.rare === 0 && low.rolledRare === 0, `${low.rare} on the ground, server ${low.rolledRare}`);
  const high = await gatherMany(140, 100);
  check('Herbalism 100: about 6% of poppies give a Bloodroot (140 gathers: 1-20)', high.rare >= 1 && high.rare <= 20 && high.rare === high.rolledRare,
    `${high.rare} on the ground, server ${high.rolledRare}`);
  check('...a Cerberus item named Bloodroot', high.rareNames.length > 0 && high.rareNames.every(n => /Bloodroot/.test(n)), JSON.stringify([...new Set(high.rareNames)]));
  check('...the first one is a discovery ("Rare reagent: Bloodroot")', chat.some(m => /Rare reagent: Bloodroot/.test(m)));
  // A rare reagent never goes into a vanilla use: Emberbloom is a blaze rod that makes no blaze powder; Sunpetal no resin.
  await cmd(`/cerb herb rare ${NAME} emberbloom 1`, 600);
  await cmd(`/cerb herb rare ${NAME} sunpetal 1`, 600);
  await sleep(400);
  const ember = bot.inventory.items().find(it => it.name === 'blaze_rod' && /Emberbloom/.test(nameOf(it)));
  const sun = bot.inventory.items().find(it => it.name === 'resin_clump' && /Sunpetal/.test(nameOf(it)));
  check('/cerb herb rare gives the reagents (Emberbloom, Sunpetal)', !!ember && !!sun);
  let made = 0;
  try {
    const mcData = require('minecraft-data')(bot.version);
    const recipe = bot.recipesAll(mcData.itemsByName.blaze_powder.id, null, null)[0];
    if (recipe) { await bot.craft(recipe, 1, null); await sleep(600); }
  } catch (e) { note('craft: ' + e.message); }
  // A refused craft leaves the rod in the 2x2 grid: closing the inventory gives it back (mineflayer doesn't count grid slots).
  try { bot._client.write('close_window', { windowId: 0 }); } catch (e) { /* none open */ }
  await sleep(600);
  made = bot.inventory.slots.filter(it => it && it.name === 'blaze_powder').reduce((a, it) => a + it.count, 0);
  const rods = bot.inventory.slots.filter(it => it && it.name === 'blaze_rod' && /Emberbloom/.test(nameOf(it))).length;
  check('An Emberbloom makes no blaze powder (a Cerberus item is never a vanilla ingredient), and is not lost', made === 0 && rods >= 1,
    `${made} blaze powder, ${rods} Emberbloom`);
  if (sun) {
    await hold('resin_clump');
    await stand(0, -2, 0);
    await setblock(0, 0, 0, 'stone');
    try { await bot.placeBlock(block(0, 0, 0), new Vec3(0, 0, -1)); } catch (e) { /* refused */ }
    await sleep(500);
    check('A Sunpetal can\'t be placed', blockName(0, 0, -1) !== 'resin_clump', blockName(0, 0, -1));
    await setblock(0, 0, 0, 'air');
  }
  await cmd('/clear @s', 400);
  await setLevel(1);
}

async function qualityPhase() {
  await arena();
  await stand(0, -1, 0);
  const low = await gatherMany(42, 49);
  check('Herbalism 49: no quality', low.quality === 0, `${low.quality} of ${low.poppies}`);
  const high = await gatherMany(84, 100);
  const kinds = [...new Set(high.lore)];
  note(`qualities seen: ${JSON.stringify(kinds)}`);
  check('Herbalism 100: about a third of the poppies have a quality (84 gathers: 15-45)', high.quality >= 15 && high.quality <= 50, `${high.quality} of ${high.poppies}`);
  check('...shown on the item ("✦ Fine quality"...)', kinds.length > 0 && kinds.every(k => /✦ (Fine|Superior|Masterwork) quality/.test(k)), JSON.stringify(kinds));
  await setLevel(1);
}

const poppiesLeft = (r, z0) => { let n = 0; for (let dx = -r; dx <= r; dx++) for (let dz = z0 - r; dz <= z0 + r; dz++) if (blockName(dx, 0, dz) === 'poppy') n++; return n; };

async function bonemealPhase() {
  // Bone meal on a herb placed by hand grows it where it stands (one more pink petal): still placed, so it pays nothing.
  // The control: the same on a wild one pays.
  await arena();
  await setLevel(30);
  await stand(0, -2, 0);
  await setblock(2, 0, 0, 'pink_petals[flower_amount=1]');
  await give('bone_meal', 4); await hold('bone_meal');
  await use(2, 0, 0);
  await holdNothing();
  check('Bone meal on a wild pink petal: two petals now', blockName(2, 0, 0) === 'pink_petals' && Number((block(2, 0, 0).getProperties() || {}).flower_amount) === 2,
    `${blockName(2, 0, 0)} ${JSON.stringify(block(2, 0, 0) && block(2, 0, 0).getProperties())}`);
  let g = await brkXp(2, 0, 0);
  check('...broken: it pays (a wild plant bone meal grew)', g >= 4, `+${g}`);
  await give('pink_petals', 1); await hold('pink_petals');
  await placeOn(0, 0, 0);
  check('The bot placed a pink petal by hand', blockName(0, 0, 0) === 'pink_petals', blockName(0, 0, 0));
  await hold('bone_meal');
  await use(0, 0, 0);
  await holdNothing();
  check('...bone meal on it: two petals now', Number((block(0, 0, 0).getProperties() || {}).flower_amount) === 2,
    JSON.stringify(block(0, 0, 0) && block(0, 0, 0).getProperties()));
  g = await brkXp(0, 0, 0); // (the "placed by hand" hint shows once a minute or two: the placed phase checks it)
  check('...broken: no XP (bone meal grew a placed plant: still placed)', g === 0 && blockName(0, 0, 0) === 'air', `+${g}`);
  await cmd('/kill @e[type=item,distance=..20]', 300);
  await setLevel(1);
}

async function bonemealTopPhase() {
  // Bone meal on the TOP half of a small dripleaf grows a big dripleaf from its lower half, the half that carries the
  // "placed" mark: one placed by hand stays placed and pays nothing. The control: the same on a wild one pays.
  await arena();
  await setLevel(30);
  await stand(0, -2, 0);
  await give('bone_meal', 8);
  const head = dx => { for (let dy = 10; dy >= 0; dy--) if (blockName(dx, dy, 0) === 'big_dripleaf') return dy; return -1; };
  const column = dx => [0, 1, 2, 3, 4, 5, 6, 7].map(dy => blockName(dx, dy, 0)).join(',');
  // Bone meal on the top half until the lower half has grown: a use the server missed is tried again, but never one more
  // on a plant that grew (bone meal makes a big dripleaf a block taller each time). Then the head, wherever it ended up.
  const grow = async dx => {
    for (let i = 0; i < 3 && blockName(dx, 0, 0) === 'small_dripleaf'; i++) {
      await use(dx, 1, 0);
      for (let w = 0; w < 20 && blockName(dx, 0, 0) === 'small_dripleaf'; w++) await sleep(250);
    }
    for (let w = 0; w < 20 && head(dx) < 0; w++) await sleep(250);
    return head(dx);
  };
  // A small dripleaf that bone meal would grow two blocks tall becomes a lone stem on this server, with or without
  // Cerberus (16 bone-mealed without the plugin: 7 lone stems, every whole one 3-5 tall): there is no leaf to break, so
  // that plant is set up again (at most three tries) and the count is shown.
  let lone = 0;
  await setblock(2, -1, 0, 'clay');
  let top = -1;
  for (let a = 0; a < 3 && top < 0; a++) {
    await setblock(2, 1, 0, 'air');
    await setblock(2, 0, 0, 'small_dripleaf[half=lower]');
    await setblock(2, 1, 0, 'small_dripleaf[half=upper]');
    for (let i = 0; i < 20 && blockName(2, 1, 0) !== 'small_dripleaf'; i++) await sleep(250);
    await hold('bone_meal');
    top = await grow(2);
    if (top < 0 && blockName(2, 0, 0) === 'big_dripleaf_stem') lone++;
  }
  check('Bone meal on the top half of a wild small dripleaf: a big dripleaf', top > 0, `${blockName(2, 0, 0)} … head at ${top}` + (top > 0 ? '' : ` [${column(2)}]`) + (lone ? `; ${lone} lone stem(s) set up again` : ''));
  let g = top > 0 ? await brkXp(2, top, 0) : 0;
  check('...its leaf broken: it pays (a wild plant bone meal grew)', g >= 5, `+${g}`);
  await setblock(0, -1, 0, 'clay');
  let placed = false, placedSeen = '';
  top = -1; lone = 0;
  for (let a = 0; a < 3 && top < 0; a++) {
    await setblock(0, 1, 0, 'air'); await setblock(0, 0, 0, 'air');
    await give('small_dripleaf', 1); await hold('small_dripleaf');
    await placeOn(0, 0, 0);
    placed = blockName(0, 0, 0) === 'small_dripleaf' && blockName(0, 1, 0) === 'small_dripleaf';
    placedSeen = `${blockName(0, 0, 0)} / ${blockName(0, 1, 0)}`;
    if (!placed) continue;
    await hold('bone_meal');
    top = await grow(0);
    if (top < 0 && blockName(0, 0, 0) === 'big_dripleaf_stem') lone++;
  }
  check('The bot placed a small dripleaf by hand', placed, placedSeen);
  check('...bone meal on its top half: a big dripleaf', top > 0, `${blockName(0, 0, 0)} … head at ${top}` + (top > 0 ? '' : ` [${column(0)}]`) + (lone ? `; ${lone} lone stem(s) set up again` : ''));
  g = top > 0 ? await brkXp(0, top, 0) : -1;
  check('...its leaf broken: no XP (bone meal grew a plant placed by hand: still placed)', g === 0, `+${g}`);
  await holdNothing();
  await cmd('/kill @e[type=item,distance=..20]', 300);
  await setLevel(1);
}

async function nodropsPhase() {
  // Another plugin cancels a gather's drops after Herbalism has seen them (a "no drops" rule; the sandbox's
  // /testnodrops): nothing dropped, so nothing pays. The control: the same with the rule off pays.
  await arena();
  await setLevel(1);
  await stand(0, -2, 0);
  await setblock(1, 0, 0, 'poppy');
  await setblock(-1, 0, 0, 'poppy');
  for (let i = 0; i < 20 && (blockName(1, 0, 0) !== 'poppy' || blockName(-1, 0, 0) !== 'poppy'); i++) await sleep(250);
  const at = chat.length;
  await cmd(`/testnodrops ${NAME}`, 600);
  const on = chat.slice(at).some(m => /testnodrops: cancelling/.test(m));
  let g = await brkXp(1, 0, 0);
  await sleep(400);
  check('A plugin cancels the drops of a wild poppy: no Herbalism XP', on && g === 0, on ? `+${g}` : 'the sandbox plugin has no /testnodrops');
  await cmd('/testnodrops off', 500);
  g = await brkXp(-1, 0, 0);
  check('...the rule off: the next poppy pays', g > 0, `+${g}`);
  await cmd('/kill @e[type=item,distance=..20]', 300);
}

async function waterPhase() {
  // Wildcrafting tall seagrass under water: both halves of every plant it takes become water again, never an air pocket
  // (an air pocket under water stays: the water above it is never told to fall in). A stone basin three blocks deep under
  // the arena, the plants at its bottom with water over them; the bot stands on its rim and cuts a plant's top half.
  // WATER_PLANT=small_dripleaf: the same with waterlogged small dripleaf on clay (Herbalism 20, its level).
  const dripleaf = process.env.WATER_PLANT === 'small_dripleaf';
  const plant = dripleaf ? 'small_dripleaf' : 'tall_seagrass';
  await arena();
  await setLevel(dripleaf ? 20 : 10);
  await give('shears'); await hold('shears');
  await fill(-3, -4, 0, 3, -1, 6, 'stone');
  if (dripleaf) await fill(-1, -4, 2, 1, -4, 4, 'clay');
  await fill(-2, -3, 1, 2, -1, 5, 'water');
  await fill(-1, -3, 2, 1, -3, 4, dripleaf ? 'small_dripleaf[half=lower,waterlogged=true]' : 'tall_seagrass[half=lower]');
  await fill(-1, -2, 2, 1, -2, 4, dripleaf ? 'small_dripleaf[half=upper,waterlogged=true]' : 'tall_seagrass[half=upper]');
  await stand(0, 0, 0, 60);
  // The bot's own copy of the world first (a fresh arena's chunks can lag behind the server's fills).
  const plantedNow = () => { let n = 0; for (let dx = -1; dx <= 1; dx++) for (let dz = 2; dz <= 4; dz++) if (blockName(dx, -3, dz) === plant && blockName(dx, -2, dz) === plant) n++; return n; };
  for (let i = 0; i < 40 && plantedNow() < 9; i++) await sleep(250);
  await sleep(500);
  const planted = plantedNow();
  check(`Nine ${plant.replace('_', ' ')} under water`, planted === 9 && blockName(0, -1, 3) === 'water', `${planted}; above: ${blockName(0, -1, 3)}`);
  const x0 = await herbXp();
  await brk(0, -2, 3, { sneak: true });
  await sleep(1500);
  const g = (await herbXp()) - x0;
  const left = [], dry = [], seen = {};
  let wet = 0;
  for (let dx = -1; dx <= 1; dx++) for (let dz = 2; dz <= 4; dz++) for (let dy = -3; dy <= -2; dy++) {
    const n = blockName(dx, dy, dz);
    seen[n] = (seen[n] || 0) + 1;
    if (n === plant || n === 'seagrass') left.push(`${dx},${dy},${dz}`);
    if (n === 'air') dry.push(`${dx},${dy},${dz}`);
    if (n === 'water' || n === 'bubble_column') wet++;
  }
  check(`Herbalism ${dripleaf ? 20 : 10}: Sneak + shears take the 3x3 of ${plant.replace('_', ' ')}, each paid`, left.length === 0 && g >= 9 * 2, `${left.length} left, +${g}; ${JSON.stringify(seen)}`);
  check('...and water stands where both halves of each plant were: no air pocket', wet === 18,
    dry.length ? `air at ${dry.join(' ')}` : `${wet} of 18 water; ${JSON.stringify(seen)}`);
  await cmd('/kill @e[type=item,distance=..20]', 300);
  await setLevel(1);
}

async function wildPhase() {
  await arena();
  await give('shears'); await hold('shears');
  await stand(0, 2, 0);
  // Below 10: one plant (and what a poppy broken by hand costs the shears, vanilla's own wear).
  await setLevel(9);
  await fill(-1, 0, 4, 1, 0, 6, 'poppy');
  const h0 = heldDamage();
  await brk(0, 0, 5, { sneak: true });
  await sleep(800);
  const handCost = heldDamage() - h0;
  check('Herbalism 9: Sneak + shears gathers one poppy (Wildcrafting opens at 10)', poppiesLeft(1, 5) === 8, `${poppiesLeft(1, 5)} left`);
  // At 10: the 3x3.
  await setLevel(10);
  await fill(-1, 0, 4, 1, 0, 6, 'poppy');
  await cmd('/kill @e[type=item,distance=..20]', 300);
  const d0 = heldDamage();
  const x0 = await herbXp();
  const fortuneSoFar = async () => +(((saw(/\[Herbalism\]/, await cmd('/cerb herb stats', 500)) || '').match(/Fortune extra (\d+)/) || [0, 0])[1]);
  const fo0 = await fortuneSoFar();
  await brk(0, 0, 5, { sneak: true });
  await sleep(1200);
  const g = (await herbXp()) - x0;
  const d = await dropsNear(0, 0, 5, 3);
  const fo = (await fortuneSoFar()) - fo0; // Fortune on herbs (3% a plant here) adds to the pile
  check('Herbalism 10: Sneak + shears take the whole 3x3', poppiesLeft(1, 5) === 0, `${poppiesLeft(1, 5)} left`);
  check('...each pays once: 9 x 3 = 27 XP', g === 27, `+${g}`);
  check('...the drops land together (9 poppies by the first plant, and any Fortune extra the server counted)', count(d, 'poppy') === 9 + fo,
    `${count(d, 'poppy')} poppies near it; the server: Fortune +${fo}`);
  check('...one point of shears wear per swept plant (8, beside the hand\'s own)', heldDamage() - d0 === 8 + handCost, `${heldDamage() - d0} (the hand's own ${handCost})`);
  // Not without Sneak.
  await fill(-1, 0, 4, 1, 0, 6, 'poppy');
  await brk(0, 0, 5);
  await sleep(800);
  check('Without Sneak: one poppy', poppiesLeft(1, 5) === 8, `${poppiesLeft(1, 5)} left`);
  // Not without shears: and a hint.
  await fill(-1, 0, 4, 1, 0, 6, 'poppy');
  await holdNothing();
  const t = Date.now();
  await brk(0, 0, 5, { sneak: true });
  await sleep(800);
  check('Sneak with an empty hand: one poppy, and the HUD says Wildcrafting needs shears', poppiesLeft(1, 5) === 8
    && hudSince(t).some(h => /Wildcrafting/.test(h) && /shears/.test(h)), `${poppiesLeft(1, 5)} left; ${JSON.stringify(hudSince(t).slice(-2))}`);
  // A placed poppy and other herbs stay.
  await hold('shears');
  await fill(-1, 0, 4, 1, 0, 6, 'poppy');
  await setblock(1, 0, 6, 'dandelion');
  await setblock(-1, 0, 6, 'air');
  await give('poppy'); await hold('poppy');
  await placeOn(-1, 0, 6);
  await hold('shears');
  await brk(0, 0, 5, { sneak: true });
  await sleep(1000);
  check('A poppy placed by hand inside the square stays, and so does a dandelion', blockName(-1, 0, 6) === 'poppy' && blockName(1, 0, 6) === 'dandelion',
    `${blockName(-1, 0, 6)} / ${blockName(1, 0, 6)}`);
  check('...the wild poppies around them are gathered', poppiesLeft(1, 5) === 1, `${poppiesLeft(1, 5)} left`);
  // The placed poppy broken by hand (its mark goes with it), so the 5x5 below starts from wild ground only: a /fill
  // doesn't tell the plugin a placed block was replaced.
  await brk(-1, 0, 6);
  await sleep(400);
  await fill(-3, 0, 2, 3, 0, 8, 'air');
  // At 40: the 5x5.
  await setLevel(40);
  await fill(-2, 0, 3, 2, 0, 7, 'poppy');
  await brk(0, 0, 5, { sneak: true });
  await sleep(1500);
  check('Herbalism 40: the 5x5', poppiesLeft(2, 5) === 0, `${poppiesLeft(2, 5)} left`);
  // Shears about to break stop it.
  await cmd('/kill @e[type=item,distance=..20]', 300);
  await cmd('/clear @s shears', 300);
  await give('shears[damage=234]'); await hold('shears'); // shears last 238: 3 points left
  await fill(-2, 0, 3, 2, 0, 7, 'poppy');
  await brk(0, 0, 5, { sneak: true });
  await sleep(1500);
  const left = poppiesLeft(2, 5);
  check('Shears 3 points from breaking: the sweep stops before they break', left >= 20 && bot.heldItem && bot.heldItem.name === 'shears',
    `${left} of 25 left, holding ${bot.heldItem ? bot.heldItem.name : 'nothing'}`);
  await cmd('/kill @e[type=item,distance=..20]', 300);
  await cmd('/clear @s', 300);
  await setLevel(1);
}

async function protectPhase() {
  await arena();
  const a = await cmd('/testprotect off', 600);
  if (!saw(/testprotect/, a)) { check('protect needs the sandbox-only CerbTestProtect plugin (tools/playtest/protect/build.sh)', false); return; }
  await give('shears'); await hold('shears');
  await setLevel(40);
  await stand(0, 2, 0);
  await fill(-2, 0, 3, 2, 0, 7, 'poppy');
  // The far two rows of the 5x5 are someone's claim.
  await cmd(`/testprotect ${X - 3} ${Y - 1} ${Z + 6} ${X + 3} ${Y + 2} ${Z + 8}`, 500);
  await brk(0, 0, 5, { sneak: true });
  await sleep(1200);
  let claimed = 0;
  for (let dx = -2; dx <= 2; dx++) for (let dz = 6; dz <= 7; dz++) if (blockName(dx, 0, dz) === 'poppy') claimed++;
  check('A claim over part of the reach: those poppies stay', claimed === 10, `${claimed} of 10 in the claim left`);
  await cmd('/testprotect off', 500);
  await fill(-2, 0, 3, 2, 0, 7, 'poppy');
  await brk(0, 0, 5, { sneak: true });
  await sleep(1200);
  check('Control: with no claim, the whole 5x5 comes down', poppiesLeft(2, 5) === 0, `${poppiesLeft(2, 5)} left`);
  await cmd('/kill @e[type=item,distance=..20]', 300);
  await setLevel(1);
}

async function menuPhase() {
  const find = re => { for (let i = 0; i < 54; i++) if (re.test(nameOf(bot.currentWindow && bot.currentWindow.slots[i]))) return i; return -1; };
  let tile = '(none)', rareTile = '(none)', screen = '(none)', coll = '(none)';
  await cmd('/menu', 1500);
  try {
    await bot.clickWindow(19, 0, 0); await sleep(1000); // Skills
    await bot.clickWindow(24, 0, 0); await sleep(1000); // Utility Skills
    let f = find(/^Herbalism/);
    for (let page = 0; f < 0 && page < 3; page++) { // a later page
      const next = find(/^Next page/);
      if (next < 0) break;
      await bot.clickWindow(next, 0, 0); await sleep(900);
      f = find(/^Herbalism/);
    }
    if (f >= 0) {
      await bot.clickWindow(f, 0, 0); await sleep(1200);
      const ti = find(/^Herbarium: /), rt = find(/^Rare reagents: /);
      if (rt >= 0) rareTile = nameOf(bot.currentWindow.slots[rt]);
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
  const at0 = await cmd('/herbalism', 800);
  const summary = saw(/herbs discovered \d+ of \d+/, at0) || '';
  const n = (summary.match(/discovered (\d+) of/) || [])[1];
  const r = (summary.match(/rare reagents (\d+) of/) || [])[1];
  check('The Herbalism page has a Herbarium tile with the count /herbalism gives', n != null && new RegExp(`^Herbarium: ${n} of 35 discovered`).test(tile), `${tile} / ${summary}`);
  check('...and a Rare reagents tile with its count', r != null && new RegExp(`^Rare reagents: ${r} of 4 found`).test(rareTile), rareTile);
  check('...the Herbarium lists the techniques at your level', /Herbarium/.test(screen) && /Wildcrafting/.test(screen) && /Rare reagents/.test(screen), screen.slice(0, 220));
  await cmd('/menu', 1500);
  try {
    const c = find(/^Collections/);
    if (c >= 0) {
      await bot.clickWindow(c, 0, 0); await sleep(1200);
      const e = find(/^Herbs/);
      if (e >= 0) coll = nameOf(bot.currentWindow.slots[e]) + ' | ' + loreOf(bot.currentWindow.slots[e]);
    }
  } catch (e) { coll = '(' + e.message + ')'; }
  if (bot.currentWindow) bot.closeWindow(bot.currentWindow);
  await sleep(400);
  check('Collections has a Herbs entry with the same count', n != null && new RegExp(`${n} of 35`).test(coll), coll.slice(0, 160));
}

async function relogPhase() {
  const at0 = await cmd('/herbalism', 800);
  const before = saw(/herbs discovered \d+ of \d+/, at0) || '';
  bot.quit();
  await sleep(4000);
  await connect();
  await sleep(3000);
  const at1 = await cmd('/herbalism', 800);
  const after = saw(/herbs discovered \d+ of \d+/, at1) || '';
  check('Discoveries and rare finds survive a relog', before !== '' && before === after, `${before} / ${after}`);
}

async function techniquePhase() {
  await arena();
  await setLevel(9);
  await cmd(`/cerb skill ${NAME} Herbalism xp ${xpForLevel(9) - 1}`, 500);
  await holdNothing();
  await stand(0, -2, 0);
  await setblock(0, 0, 0, 'poppy');
  const from = chat.length;
  await brk(0, 0, 0);
  await sleep(1500);
  const lines = chat.slice(from).join(' | ');
  check('Herbalism 9 → 10 by a real gather: the level-up names "Wildcrafting 3×3"', /Wildcrafting 3×3/.test(lines), lines.slice(0, 300));
  await setLevel(1);
}

(async () => {
  await connect();
  await sleep(3000);
  const phases = { gather: gatherPhase, placed: placedPhase, shears: shearsPhase, creative: creativePhase, machines: machinesPhase,
    bonemealtop: bonemealTopPhase, nodrops: nodropsPhase,
    fortune: fortunePhase, rare: rarePhase, quality: qualityPhase, wild: wildPhase, protect: protectPhase, menu: menuPhase,
    relog: relogPhase, technique: techniquePhase, bonemeal: bonemealPhase, water: waterPhase };
  const run = phaseArg === 'all' ? ['gather', 'placed', 'bonemeal', 'bonemealtop', 'nodrops', 'shears', 'creative', 'machines', 'fortune', 'rare', 'quality', 'wild', 'water', 'menu', 'relog', 'technique']
    : phaseArg.split(',');
  for (const p of run) {
    if (!phases[p]) { console.log('unknown phase ' + p); continue; }
    console.log(`\n── ${p} ──`);
    try { await phases[p](); } catch (e) { check(`${p} ran to the end`, false, e.stack.split('\n').slice(0, 2).join(' ')); }
  }
  bot.setControlState('sneak', false);
  await cmd(`/cerb skill ${NAME} Herbalism level 1`, 250);
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
