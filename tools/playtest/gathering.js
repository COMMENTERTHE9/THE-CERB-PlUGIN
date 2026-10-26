// Gathering, forging and forged gear in game (docs/design/GATHERING.md). LOCAL SANDBOX servers only; the bot must be op.
// Usage: node gathering.js [phase]
//   mine      - Mining XP per resource, the weak-resource rule, vanilla's tool rule (iron ore and a wooden pickaxe: no drop,
//               no XP), bare hands; gathering speed from Mining (the block_break_speed attribute)
//   farm      - every "can't be farmed" row, each with a positive control: place and break, Silk Touch (XP, no bonus; the
//               block placed again pays nothing), a lava-and-water generator, a piston, TNT, creative mode
//   fortune   - the bonus at the 50% cap: one extra item at most, about half the time, and never on a torch standing on the
//               ore; never with Silk Touch; rare at low Fortune; on top of the Fortune enchantment, never multiplied by it
//   smelt     - smelting XP when a player takes the metal: once per take (a furnace that smelted two kinds of input), a
//               shift-click take, a right-click split (both halves), a number key onto an empty hotbar slot, and a hopper
//               pays nobody; each ingot taken covers one unit of forging
//   forge     - forging XP: one click, a shift-click batch (every item paid, one quality for the batch), a batch cut short by a
//               full inventory, Ctrl+Q; a number key onto an occupied hotbar slot and a click with a full cursor make
//               nothing and pay nothing; storage blocks both ways, nuggets and the repair recipe pay nothing; iron the
//               player didn't smelt (a farm's) makes plain gear and pays nothing, and a batch stops paying when it runs out
//   upgrade   - the netherite upgrade at a smithing table (50 XP, the quality kept; a shift-click too), an armor trim (nothing),
//               a shift-click with no room while a netherite sword is swapped in (nothing)
//   gear      - forged quality in the attribute engine: Power with that weapon in hand only (not in the off hand), Fortitude
//               per armor piece worn, Fortune with the pickaxe in hand (the bonus chance follows); plain items rebuild nothing
//   custom    - the synthetic custom resource, drop and drill (written into the sandbox's resources.yml for the phase, then
//               put back; needs SERVER_DIR): refused with a pickaxe, gathered with the drill, a machine's extraction, a placed
//               one; the refusal message takes priority over the XP line
//   xpdisplay - the real-time XP display: compact (merged gains), detailed, off
//   persist-set / persist-check - a placed ore, Mining XP, a forged item and the XP display setting survive a restart
//               (STATE=<file> keeps what persist-set recorded)
const mineflayer = require('mineflayer');
const { Vec3 } = require('vec3');
const fs = require('fs');
const phase = process.argv[2] || 'mine';
const NAME = process.argv[3] || 'CerbSmoke';
const sleep = ms => new Promise(r => setTimeout(r, ms));
const chat = [];
let actionBar = '';
const results = [];
const check = (name, ok, detail = '') => { results.push(ok); console.log(`${ok ? 'PASS' : 'FAIL'}  ${name}${detail ? '  (' + detail + ')' : ''}`); };
const bot = mineflayer.createBot({ host: '127.0.0.1', port: 25565, username: NAME, version: '1.21.11', auth: 'offline' });
bot.on('messagestr', (m, pos) => { if (pos === 'game_info') actionBar = m; else chat.push(m); });
bot._client.on('action_bar', p => { try { actionBar = require('prismarine-chat')(bot.registry).fromNotch(p.text).toString(); } catch (e) { /* keep last */ } });
bot.on('kicked', r => { console.log('KICKED', r); process.exit(2); });

async function cmd(c, wait = 700) { const at = chat.length; bot.chat(c); await sleep(wait); return at; }
const saw = (re, since) => chat.slice(since).find(m => re.test(m));

// ── Skill XP, read back from the server ──────────────────────────
const xpForLevel = l => Math.round(75 * Math.pow(Math.max(1, l), 1.85) + 40 * Math.max(1, l));
const totalXp = s => { let t = s.xp; for (let l = 1; l < s.level; l++) t += xpForLevel(l); return t; };
async function skill(name) {
  const at = await cmd(`/cerb skill ${NAME} ${name} xp 0`, 700);
  const m = (saw(/is now level \d+ \(\d+ XP\)/, at) || '').match(/level (\d+) \((\d+) XP\)/);
  return m ? { level: Number(m[1]), xp: Number(m[2]) } : { level: NaN, xp: NaN };
}
const mining = async () => totalXp(await skill('Mining'));
const smithing = async () => totalXp(await skill('Smithing'));
/** Turn /cerb combatdebug on or off (it toggles; its answer says which). */
async function debugOn(on) {
  for (let i = 0; i < 2; i++) {
    const a = await cmd('/cerb combatdebug', 700);
    const m = saw(/Combat debug: (ON|OFF)/, a);
    if (m && /ON/.test(m) === on) return true;
  }
  return false;
}
async function setLevel(name, level) { await cmd(`/cerb skill ${NAME} ${name} level ${level}`, 600); }

// ── The test area: a glass floor far from spawn, fresh each run (placed-block marks live in chunk data) ──
let X = 0, Y = 0, Z = 0;
async function arena(fixed = null) {
  const sp = bot.spawnPoint || bot.entity.position;
  const off = fixed != null ? fixed : 300 + (Date.now() % 40) * 48;
  X = Math.floor(sp.x) + off; Y = 120; Z = Math.floor(sp.z) + 300;
  await cmd('/gamemode creative', 300);
  await cmd(`/tp @s ${X + 0.5} ${Y + 1} ${Z + 0.5}`, 1000);
  try { await bot.waitForChunksToLoad(); } catch (e) { /* the fill below retries */ }
  await cmd('/gamerule spawn_mobs false', 300);
  await cmd('/time set day', 300);
  await cmd('/gamerule advance_time false', 300);
  for (let i = 0; i < 20; i++) { // fresh terrain: wait until the server has the chunks loaded
    const a = await cmd(`/fill ${X - 6} ${Y - 1} ${Z - 6} ${X + 18} ${Y - 1} ${Z + 6} glass`, 600);
    if (!saw(/not loaded/i, a)) break;
    await sleep(1000);
  }
  await cmd(`/fill ${X - 6} ${Y} ${Z - 6} ${X + 18} ${Y + 5} ${Z + 6} air`, 500);
  await stand(0, 0);
  await cmd('/kill @e[type=item,distance=..30]', 300);
  await cmd('/clear @s', 400);
  await cmd('/gamemode survival', 300);
  await cmd('/effect clear @s', 300);
  await cmd('/effect give @s resistance infinite 4 true', 300);
  await cmd('/effect give @s saturation infinite 1 true', 300);
  await cmd('/xpdisplay compact', 300);
}
async function stand(dx, dz, yaw = -90) { await cmd(`/tp @s ${X + dx + 0.5} ${Y} ${Z + dz + 0.5} ${yaw} 0`, 600); }
const at = (dx, dy, dz) => new Vec3(X + dx, Y + dy, Z + dz);
async function setblock(dx, dy, dz, block, wait = 350) { await cmd(`/setblock ${X + dx} ${Y + dy} ${Z + dz} ${block}`, wait); }
const blockName = (dx, dy, dz) => { const b = bot.blockAt(at(dx, dy, dz)); return b ? b.name : '?'; };
async function look(dx, dy, dz) { await bot.lookAt(at(dx, dy, dz).offset(0.5, 0.5, 0.5), true); await sleep(150); }

// ── Items ─────────────────────────────────────────────────────────
async function give(item, n = 1, wait = 600) { await cmd(`/give @s ${item} ${n}`, wait); }
/** Put an item with this name in hand (hotbar first; else swap it into the selected hotbar slot). */
async function hold(name) {
  const idx = bot.inventory.slots.findIndex((it, i) => it && it.name === name && i >= 36 && i <= 44);
  if (idx >= 0) { bot.setQuickBarSlot(idx - 36); await sleep(300); return true; }
  const any = bot.inventory.slots.findIndex((it, i) => it && it.name === name && i >= 9 && i <= 35);
  if (any < 0) return false;
  try { await bot.clickWindow(any, 0, 2); } catch (e) { /* the server's answer is what counts */ }
  await sleep(400);
  return true;
}
async function holdNothing() {
  const free = [...Array(9).keys()].find(i => !bot.inventory.slots[36 + i]);
  if (free != null) bot.setQuickBarSlot(free);
  await sleep(300);
  return free != null;
}
const loreOf = it => (it && it.customLore ? it.customLore.map(l => {
  try { return require('prismarine-chat')(bot.registry).fromNotch(l).toString(); } catch (e) { return String(l); }
}).join(' | ') : '');
const qualityOf = it => (loreOf(it).match(/(Common|Fine|Superior|Masterwork) quality/) || [])[1] || null;
const invItems = name => bot.inventory.slots.filter(it => it && it.name === name);

// ── Breaking blocks ───────────────────────────────────────────────
/** Dig with the held item. Enchanted tools go through raw dig packets (this mineflayer can't read 1.21.11 enchantments). */
async function dig(dx, dy, dz, raw = false, ms = 900) {
  const b = bot.blockAt(at(dx, dy, dz));
  if (!b || b.name === 'air') return false;
  await bot.lookAt(b.position.offset(0.5, 0.5, 0.5), true);
  if (!raw) {
    try { await bot.dig(b, true); } catch (e) { return false; }
    await sleep(350);
    return true;
  }
  const seq = () => (bot._cerbSeq = (bot._cerbSeq || 5000) + 1);
  bot._client.write('block_dig', { status: 0, location: b.position, face: 1, sequence: seq() });
  bot.swingArm();
  await sleep(ms);
  bot._client.write('block_dig', { status: 2, location: b.position, face: 1, sequence: seq() });
  await sleep(450);
  return true;
}
/** Dropped items near a spot: [{name, count}], then cleared. */
async function drops(dx, dy, dz, r = 3, clear = true) {
  await sleep(250);
  const p = at(dx, dy, dz).offset(0.5, 0.5, 0.5);
  const out = [];
  for (const e of Object.values(bot.entities)) {
    if (e.name !== 'item' || e.position.distanceTo(p) > r) continue;
    try { const d = e.getDroppedItem(); if (d) out.push({ name: d.name, count: d.count, lore: loreOf(d), nameText: d.customName }); } catch (err) { out.push({ name: '?', count: 0 }); }
  }
  if (clear) await cmd(`/kill @e[type=item,x=${X + dx},y=${Y + dy},z=${Z + dz},distance=..${r + 3}]`, 300);
  return out;
}
const count = (list, name) => list.filter(d => d.name === name).reduce((a, d) => a + d.count, 0);
/** Fill every free slot with dirt, so the bot can't pick drops up: they are counted where they fall. */
async function fill() {
  const free = bot.inventory.emptySlotCount();
  if (free > 0) await cmd(`/give @s dirt ${free * 64}`, 900);
}
const invCounts = () => { const m = {}; for (const it of bot.inventory.items()) m[it.name] = (m[it.name] || 0) + it.count; return m; };
/** Break a block and collect what it dropped: item entities around it plus whatever the bot already picked up. */
async function mineOne(dx, dy, dz, raw = false, ms = 900) {
  const inv0 = invCounts();
  await dig(dx, dy, dz, raw, ms);
  const d = await drops(dx, dy, dz);
  const inv1 = invCounts();
  for (const k of Object.keys(inv1)) { const diff = inv1[k] - (inv0[k] || 0); if (diff > 0) d.push({ name: k, count: diff, picked: true }); }
  return d;
}

// ── Phases ────────────────────────────────────────────────────────

async function mine() {
  await arena();
  await setLevel('Mining', 1);
  await give('iron_pickaxe'); await hold('iron_pickaxe');
  await stand(0, 0);
  const cases = [['stone', 1], ['iron_ore', 8], ['deepslate_diamond_ore', 40], ['coal_ore', 4], ['deepslate', 1]];
  for (const [block, xp] of cases) {
    await setblock(2, 0, 0, block);
    const before = await mining();
    await dig(2, 0, 0);
    const after = await mining();
    check(`Natural ${block} pays ${xp} Mining XP`, after - before === xp, `+${after - before}`);
  }
  check('...and the HUD shows the gains (real-time XP display)', /\+\d+ Mining/.test(actionBar), actionBar);
  await cmd('/kill @e[type=item,distance=..10]', 300);

  // The weak-resource rule: 20+ levels above a resource, a quarter of its XP (fractions carry over).
  await setLevel('Mining', 40);
  let before = await mining();
  for (let i = 0; i < 4; i++) { await setblock(2, 0, 0, 'stone'); await dig(2, 0, 0); }
  check('Mining 40: four stones (level 1) pay a quarter each, 1 XP in all', (await mining()) - before === 1, `+${(await mining()) - before}`);
  before = await mining();
  await setblock(2, 0, 0, 'iron_ore'); await dig(2, 0, 0);
  check('...an iron ore (level 10) pays 2 of its 8', (await mining()) - before === 2);
  before = await mining();
  await setblock(2, 0, 0, 'deepslate_diamond_ore'); await dig(2, 0, 0);
  check('...a diamond ore (level 30) still pays all 40', (await mining()) - before === 40);
  await setLevel('Mining', 1);
  await cmd('/kill @e[type=item,distance=..10]', 300);

  // Vanilla's tool rule decides vanilla drops; no drop, no XP.
  await give('wooden_pickaxe'); await hold('wooden_pickaxe');
  await setblock(2, 0, 0, 'iron_ore');
  before = await mining();
  let d = await mineOne(2, 0, 0);
  check('Iron ore with a wooden pickaxe: no drop and no XP', d.length === 0 && (await mining()) === before, JSON.stringify(d));
  await setblock(2, 0, 0, 'stone');
  before = await mining();
  d = await mineOne(2, 0, 0);
  check('Control: stone with the wooden pickaxe drops and pays 1', count(d, 'cobblestone') === 1 && (await mining()) - before === 1, JSON.stringify(d));
  await holdNothing();
  await cmd('/effect give @s haste 30 4 true', 300);
  await setblock(2, 0, 0, 'stone');
  before = await mining();
  d = await mineOne(2, 0, 0);
  check('Stone by hand: no drop and no XP', d.length === 0 && (await mining()) === before, JSON.stringify(d));
  await cmd('/effect clear @s haste', 300);

  // Gathering speed: Mining raises the block_break_speed attribute (diminishing returns, at most +25%).
  const speed = async () => { const a = await cmd('/attribute @s minecraft:block_break_speed get', 700); const m = (saw(/block_break_speed|Block Break Speed/i, a) || '').match(/is ([\d.]+)/); return m ? Number(m[1]) : NaN; };
  const s1 = await speed();
  await setLevel('Mining', 100);
  await sleep(500);
  const s100 = await speed();
  const want = 1 + 0.25 * (1 - Math.exp(-0.2 / 0.25));
  check('Gathering speed: Mining 1 about x1.002, Mining 100 x1.138 (cap 1.25)', Math.abs(s1 - 1.002) < 0.002 && Math.abs(s100 - want) < 0.002, `${s1} -> ${s100}`);
  await setLevel('Mining', 1);
}

async function farm() {
  await arena();
  await setLevel('Mining', 1);
  await give('iron_pickaxe'); await hold('iron_pickaxe');
  await stand(0, 0);

  // 1. Place and break
  await give('iron_ore', 2); await give('stone', 1);
  for (const block of ['iron_ore', 'stone']) {
    await hold(block);
    try { await bot.placeBlock(bot.blockAt(at(2, -1, 0)), new Vec3(0, 1, 0)); } catch (e) { /* checked below */ }
    await sleep(500);
    const placed = blockName(2, 0, 0) === block;
    await hold('iron_pickaxe');
    const before = await mining();
    await dig(2, 0, 0);
    check(`A placed ${block}, broken: no XP`, placed && (await mining()) === before, `placed=${placed}`);
  }
  await setblock(3, 0, 0, 'iron_ore');
  let before = await mining();
  await dig(3, 0, 0);
  check('Control: the natural one next to it pays 8', (await mining()) - before === 8);
  await cmd('/kill @e[type=item,distance=..10]', 300);

  // 2. Silk Touch: XP, no bonus; the block placed again pays nothing
  await cmd('/clear @s', 300);
  await give('iron_pickaxe[enchantments={silk_touch:1}]'); await hold('iron_pickaxe');
  await setblock(2, 0, 0, 'iron_ore');
  before = await mining();
  const silk = await mineOne(2, 0, 0, true);
  const silkXp = (await mining()) - before;
  check('Silk Touch on a natural ore: the ore and its XP', count(silk, 'iron_ore') === 1 && silkXp === 8, `+${silkXp}, ${JSON.stringify(silk)}`);
  await cmd('/kill @e[type=item,distance=..10]', 300);
  if (!invItems('iron_ore').length) await give('iron_ore', 1);
  await hold('iron_ore');
  try { await bot.placeBlock(bot.blockAt(at(2, -1, 0)), new Vec3(0, 1, 0)); } catch (e) { /* checked below */ }
  await sleep(500);
  await hold('iron_pickaxe');
  before = await mining();
  await dig(2, 0, 0, true);
  check('...placed again and silk-touched again: nothing', (await mining()) === before, blockName(2, 0, 0));
  await cmd('/kill @e[type=item,distance=..10]', 300);
  await cmd('/clear @s', 300);
  await give('iron_pickaxe'); await hold('iron_pickaxe');

  // 3. A generator: lava flowing down into water makes stone
  await cmd(`/fill ${X + 5} ${Y} ${Z - 1} ${X + 7} ${Y + 3} ${Z + 1} glass`, 400);
  await cmd(`/fill ${X + 6} ${Y} ${Z} ${X + 6} ${Y + 3} ${Z} air`, 400);
  await setblock(6, 0, 0, 'water', 400);
  await setblock(6, 2, 0, 'lava', 400);
  await sleep(3500);
  const formed = blockName(6, 0, 0);
  await cmd(`/fill ${X + 6} ${Y + 1} ${Z} ${X + 6} ${Y + 3} ${Z} air`, 400);
  await cmd(`/fill ${X + 5} ${Y} ${Z - 1} ${X + 7} ${Y + 3} ${Z + 1} air replace glass`, 400);
  await sleep(500);
  await stand(4, 0);
  before = await mining();
  await dig(6, 0, 0);
  check('Stone a generator made (lava onto water): no XP', formed === 'stone' && (await mining()) === before, `formed ${formed}`);
  await setblock(6, 0, 0, 'stone');
  before = await mining();
  await dig(6, 0, 0);
  check('Control: natural stone in the same spot pays 1', (await mining()) - before === 1);
  await cmd('/kill @e[type=item,distance=..12]', 300);

  // 4. A piston: the ore it pushed counts as placed where it lands
  await stand(7, -2, 0);
  await setblock(9, 0, 1, 'iron_ore');
  await setblock(9, 0, 0, 'piston[facing=south]');
  await setblock(9, 0, -1, 'redstone_block', 1500);
  const pushed = blockName(9, 0, 2);
  await cmd(`/fill ${X + 9} ${Y} ${Z - 1} ${X + 9} ${Y} ${Z + 1} air`, 400);
  await stand(9, 0, 0);
  before = await mining();
  await dig(9, 0, 2);
  check('An ore a piston pushed: no XP', pushed === 'iron_ore' && (await mining()) === before, `pushed: ${pushed}`);
  await setblock(10, 0, 2, 'iron_ore');
  before = await mining();
  await dig(10, 0, 2);
  check('Control: an ore it didn\'t push pays 8', (await mining()) - before === 8);
  await cmd('/kill @e[type=item,distance=..12]', 300);

  // 5. TNT: drops, no XP for anybody
  await stand(0, 0);
  await cmd(`/fill ${X + 14} ${Y} ${Z - 1} ${X + 14} ${Y} ${Z + 1} iron_ore`, 400);
  before = await mining();
  await cmd(`/summon tnt ${X + 14.5} ${Y + 1} ${Z + 0.5} {fuse:10}`, 2500);
  check('Ores blown up by TNT: no XP', (await mining()) === before && blockName(14, 0, 0) !== 'iron_ore');
  await cmd(`/fill ${X - 6} ${Y - 1} ${Z - 6} ${X + 18} ${Y - 1} ${Z + 6} glass`, 400);
  await cmd('/kill @e[type=item,distance=..30]', 300);

  // 6. Creative mode
  await cmd('/gamemode creative', 400);
  await setblock(2, 0, 0, 'iron_ore');
  before = await mining();
  await dig(2, 0, 0);
  const cd = await drops(2, 0, 0);
  check('Creative mode: no XP and no drops', (await mining()) === before && cd.length === 0 && blockName(2, 0, 0) === 'air', JSON.stringify(cd));
  await cmd('/gamemode survival', 400);
}

/** Mine `n` natural blocks of `block` at one spot, returning the drops of each. */
async function mineMany(n, block, raw = false) {
  const per = [];
  for (let i = 0; i < n; i++) {
    await setblock(2, 0, 0, block, 250);
    per.push(await mineOne(2, 0, 0, raw, 700));
  }
  return per;
}

async function fortune() {
  await arena();
  // Fortune 250 (Mining, Fishing, Trading, Scavenging, Lockpicking at 100: 0.5 each per level): the chance is capped at 50%.
  for (const s of ['Mining', 'Fishing', 'Trading', 'Scavenging', 'Lockpicking']) await setLevel(s, 100);
  await give('iron_pickaxe'); await hold('iron_pickaxe');
  await fill();
  await stand(0, 0);
  await setblock(2, 0, 0, 'iron_ore');
  await look(2, 0, 0);
  let a = await cmd('/cerb gather info', 900);
  const info = chat.slice(a).join(' ');
  check('Fortune 250: the bonus chance is capped at 50%', /50\.0% bonus chance/.test(info), (info.match(/[\d.]+% bonus chance/) || [''])[0]);
  const per = await mineMany(40, 'iron_ore');
  const counts = per.map(d => count(d, 'raw_iron'));
  const total = counts.reduce((x, y) => x + y, 0);
  check('One extra raw iron at most per ore (40 ores)', counts.every(c => c === 1 || c === 2), counts.join(''));
  check('...about half the time at the cap (50-70 of 40 ores)', total >= 50 && total <= 70, `${total} raw iron from 40 ores`);
  // A torch on the ore drops with it, in the same drop event: the bonus goes to the raw iron, never the torch.
  const torched = [];
  for (let i = 0; i < 20; i++) {
    await setblock(2, 0, 0, 'iron_ore', 200);
    await setblock(2, 1, 0, 'torch', 200);
    torched.push(await mineOne(2, 0, 0));
  }
  const torches = torched.map(d => count(d, 'torch')), irons = torched.map(d => count(d, 'raw_iron'));
  check('A torch on the ore: always 1 torch back, the bonus on the raw iron (20 ores)', torches.every(t => t === 1) && irons.every(c => c === 1 || c === 2)
      && irons.some(c => c === 2), `torches ${torches.join('')}, raw iron ${irons.join('')}`);

  await cmd('/clear @s', 300);
  await give('iron_pickaxe[enchantments={silk_touch:1}]'); await hold('iron_pickaxe');
  await fill();
  const silk = await mineMany(12, 'iron_ore', true);
  check('Silk Touch never gets the bonus (12 ores: 12 ore blocks)', silk.every(d => count(d, 'iron_ore') === 1 && d.length === 1), silk.map(d => count(d, 'iron_ore')).join(''));

  await cmd('/clear @s', 300);
  await give('iron_pickaxe[enchantments={fortune:3}]'); await hold('iron_pickaxe');
  await fill();
  const ench = await mineMany(15, 'iron_ore', true);
  const ec = ench.map(d => count(d, 'raw_iron'));
  check('With the Fortune III enchantment: never more than vanilla\'s 4 plus one', ec.every(c => c >= 1 && c <= 5), ec.join(' '));

  for (const s of ['Mining', 'Fishing', 'Trading', 'Scavenging', 'Lockpicking']) await setLevel(s, 1);
  await cmd('/clear @s', 300);
  await give('iron_pickaxe'); await hold('iron_pickaxe');
  await fill();
  const low = await mineMany(20, 'iron_ore');
  await cmd('/clear @s', 300);
  const extra = low.map(d => count(d, 'raw_iron')).reduce((x, y) => x + y, 0) - 20;
  check('Fortune 2.5 (all at level 1): a bonus is rare (at most 2 in 20 ores)', extra >= 0 && extra <= 2, `${extra} extra`);
}

// ── Containers ────────────────────────────────────────────────────
async function openAt(dx, dy, dz) {
  const b = bot.blockAt(at(dx, dy, dz));
  await bot.lookAt(b.position.offset(0.5, 0.5, 0.5), true);
  const w = await bot.openBlock(b);
  await sleep(500);
  return w;
}
/** Window slot index of the first stack of `name` in the player's part of window `w`. */
const findIn = (w, name) => { for (let i = w.inventoryStart; i < w.inventoryEnd; i++) if (w.slots[i] && w.slots[i].name === name) return i; return -1; };
const freeIn = w => { for (let i = w.inventoryStart; i < w.inventoryEnd; i++) if (!w.slots[i]) return i; return -1; };
async function clickW(slot, button = 0, mode = 0, wait = 250) { try { await bot.clickWindow(slot, button, mode); } catch (e) { /* the server decides */ } await sleep(wait); }
/** Move the whole stack of `name` into window slot `to`. */
async function put(w, name, to) { const from = findIn(w, name); if (from < 0) return false; await clickW(from); await clickW(to); return true; }
/** Put `n` of `name` (a stack of exactly 2n) half into `a`, half into `b`. */
async function split(w, name, a, b) { await put(w, name, a); await clickW(a, 1); await clickW(b); }
/** Drop whatever is on the cursor into a free inventory slot. */
async function stash(w) { const f = freeIn(w); if (f >= 0) await clickW(f); }

async function smelt() {
  await arena();
  await setLevel('Smithing', 1);
  await cmd('/cerb gather credit iron 0', 400);
  await stand(0, 0);
  await setblock(2, 0, 0, 'blast_furnace[facing=west]');
  await give('raw_iron', 3); await give('coal', 8); await give('iron_ore', 1);
  let w = await openAt(2, 0, 0);
  await put(w, 'coal', 1);
  await put(w, 'raw_iron', 0); // three raw iron
  bot.closeWindow(w);
  await sleep(16500); // 5 s each in a blast furnace
  w = await openAt(2, 0, 0);
  let before = await smithing();
  await clickW(2, 0, 1, 600); // shift-click the output into the inventory
  check('A shift-click take of 3 iron ingots pays 3 x 2 Smithing XP', (await smithing()) - before === 6, `+${(await smithing()) - before}`);
  // Two kinds of input in one furnace (raw iron and iron ore): the take fires two extract events; it still pays once.
  await put(w, 'iron_ore', 0);
  bot.closeWindow(w);
  await sleep(5800);
  await give('raw_iron', 1);
  w = await openAt(2, 0, 0);
  await put(w, 'raw_iron', 0);
  bot.closeWindow(w);
  await sleep(5800);
  w = await openAt(2, 0, 0);
  before = await smithing();
  await clickW(2, 0, 0, 500); // pick the 2 ingots up
  await stash(w);
  check('Two kinds of input, one take of 2 ingots: 4 XP, not 8', (await smithing()) - before === 4, `+${(await smithing()) - before}`);
  bot.closeWindow(w);

  // A right-click split takes half; the second half pays too.
  await give('raw_iron', 4);
  w = await openAt(2, 0, 0);
  await put(w, 'raw_iron', 0);
  bot.closeWindow(w);
  await sleep(21000);
  w = await openAt(2, 0, 0);
  before = await smithing();
  await clickW(2, 1, 0, 500); await stash(w); // right-click: half (2)
  const half = (await smithing()) - before;
  await clickW(2, 0, 0, 500); await stash(w); // the rest (2)
  check('A right-click split: both halves pay (2 + 2 ingots, 4 + 4 XP)', half === 4 && (await smithing()) - before === 8, `+${half}, +${(await smithing()) - before}`);
  // A number key onto an empty hotbar slot takes the whole stack: paid from the click, once.
  await give('raw_iron', 2);
  await put(w, 'raw_iron', 0);
  bot.closeWindow(w);
  await sleep(11000);
  await cmd('/clear @s', 300);
  w = await openAt(2, 0, 0);
  before = await smithing();
  await clickW(2, 4, 2, 700); // number key 5: hotbar slot 5 is empty
  check('A number key onto an empty hotbar slot: 2 ingots, 4 XP, once', (await smithing()) - before === 4, `+${(await smithing()) - before}`);
  bot.closeWindow(w);
  await look(2, 0, 0);
  const a2 = await cmd('/cerb gather info', 900);
  const units = Number(((chat.slice(a2).find(m => /smelted metal for forging/.test(m)) || '').match(/iron (\d+)/) || [])[1]);
  check('Every iron ingot taken covers one unit of forging (3 + 2 + 4 + 2 = 11)', units === 11, `iron ${units}`);

  // A hopper under the furnace takes the output: nobody is paid.
  await setblock(2, -1, 0, 'hopper');
  await give('raw_iron', 2);
  w = await openAt(2, 0, 0);
  await put(w, 'raw_iron', 0);
  bot.closeWindow(w);
  before = await smithing();
  await sleep(11500);
  const hopper = await openAt(2, -1, 0);
  const pulled = hopper.slots.slice(0, 5).filter(Boolean).reduce((s, it) => s + (it.name === 'iron_ingot' ? it.count : 0), 0);
  await clickW(hopper.slots.findIndex(it => it && it.name === 'iron_ingot'), 0, 1, 500);
  bot.closeWindow(hopper);
  check('A hopper takes 2 ingots out: no Smithing XP (taking them from the hopper pays nothing either)', pulled === 2 && (await smithing()) === before, `hopper had ${pulled}`);
  await setblock(2, -1, 0, 'glass');
}

async function forge() {
  await arena();
  await setLevel('Smithing', 1);
  await stand(0, 0);
  await setblock(2, 0, 0, 'crafting_table');
  await cmd('/cerb gather credit iron 0', 400);
  const sword = async (n, how) => { // the sword pattern (slots 2, 5, 8), n sets
    await give('iron_ingot', 2 * n, 300); await give('stick', n, 300);
    const w = await openAt(2, 0, 0);
    await split(w, 'iron_ingot', 2, 5);
    await put(w, 'stick', 8);
    await sleep(300);
    const before = await smithing();
    await how(w);
    await sleep(600);
    const gained = (await smithing()) - before;
    for (const s of [1, 2, 3, 4, 5, 6, 7, 8, 9]) if (w.slots[s]) { await clickW(s, 0, 1, 150); }
    bot.closeWindow(w);
    await sleep(300);
    return gained;
  };

  // Iron the player never smelted (a farm's): a plain sword, no XP, no quality.
  let g = await sword(1, async w => { await clickW(0, 0, 0, 400); await stash(w); });
  const farmed = invItems('iron_sword');
  check('A sword from iron you didn\'t smelt: plain, no XP', g === 0 && farmed.length === 1 && !qualityOf(farmed[0]), `+${g}, ${loreOf(farmed[0])}`);
  await cmd('/clear @s', 300);
  // Smelted iron for 3 swords (6 units), then a batch of 5: 3 paid and given a quality, 2 plain.
  await cmd('/cerb gather credit iron 6', 400);
  g = await sword(5, async () => { await clickW(0, 0, 1, 900); });
  const mixed = invItems('iron_sword');
  check('Smelted iron for 3 of a batch of 5: 30 XP, 3 with a quality, 2 plain', g === 30 && mixed.length === 5
      && mixed.filter(qualityOf).length === 3, `+${g}, ${mixed.map(i => qualityOf(i) || 'plain').join(',')}`);
  await cmd('/clear @s', 300);
  await cmd('/cerb gather credit iron 1000', 400); // plenty for the checks below

  g = await sword(1, async w => { await clickW(0, 0, 0, 400); await stash(w); });
  const one = invItems('iron_sword');
  check('One iron sword: 10 Smithing XP, and it carries a quality', g === 10 && one.length === 1 && !!qualityOf(one[0]), `+${g}, ${loreOf(one[0])}`);
  await cmd('/clear @s', 300);

  g = await sword(10, async () => { await clickW(0, 0, 1, 900); });
  const ten = invItems('iron_sword');
  const qs = new Set(ten.map(qualityOf));
  check('A shift-click batch of 10 swords: 100 XP, every one paid', g === 100 && ten.length === 10, `+${g}, ${ten.length} swords`);
  check('...one quality roll for the whole batch', qs.size === 1 && !qs.has(null), [...qs].join(','));
  await cmd('/clear @s', 300);

  // A batch cut short by a full inventory: only what was made is paid.
  await give('dirt', 64 * 33, 900); // 33 of 36 slots full (3 free)
  g = await sword(6, async () => { await clickW(0, 0, 1, 900); });
  check('A shift-click with room for 3 of 6: 3 swords, 30 XP', g === 30 && invItems('iron_sword').length === 3, `+${g}, ${invItems('iron_sword').length} swords`);
  await cmd('/clear @s', 300);

  // Ctrl+Q drops every sword it makes: each one pays.
  g = await sword(4, async () => { await clickW(0, 1, 4, 900); });
  const dropped = count(await drops(0, 0, 0, 8), 'iron_sword') + invItems('iron_sword').length; // thrown, maybe picked up again
  check('Ctrl+Q on the result: 4 swords thrown, 40 XP', g === 40 && dropped === 4, `+${g}, ${dropped} swords`);
  await cmd('/clear @s', 300);

  // A number key onto an occupied hotbar slot makes nothing: nothing paid. Then onto an empty one: one sword.
  await give('dirt', 1, 300); // hotbar slot 1 (index 0) holds dirt
  g = await sword(1, async () => { await clickW(0, 0, 2, 600); });
  check('Number key 1 onto an occupied slot: no sword, no XP', g === 0 && invItems('iron_sword').length === 0, `+${g}`);
  await cmd('/clear @s', 300);
  g = await sword(1, async () => { await clickW(0, 0, 2, 600); });
  check('Control: number key 1 onto an empty slot makes one sword, 10 XP', g === 10 && invItems('iron_sword').length === 1, `+${g}`);
  await cmd('/clear @s', 300);

  // A full cursor: clicking the result does nothing (swords don't stack).
  g = await sword(1, async w => { await give('dirt', 1, 300); const d = findIn(w, 'dirt'); await clickW(d); await clickW(0, 0, 0, 500); await clickW(d); });
  check('A click on the result with dirt on the cursor: nothing made, nothing paid', g === 0, `+${g}`);
  await cmd('/clear @s', 300);

  // Reversible recipes pay nothing.
  const craftOne = async (lay) => { const w = await openAt(2, 0, 0); await lay(w); await sleep(300); const b = await smithing(); await clickW(0, 0, 1, 600); const gain = (await smithing()) - b; bot.closeWindow(w); await sleep(300); return gain; };
  await give('iron_ingot', 9, 300);
  g = await craftOne(async w => { for (let s = 1; s <= 9; s++) { const f = findIn(w, 'iron_ingot'); await clickW(f); await clickW(s, 1); await clickW(f); } });
  check('Nine ingots into an iron block: no XP', g === 0 && invItems('iron_block').length === 1, `+${g}`);
  g = await craftOne(async w => { await put(w, 'iron_block', 5); });
  check('...and back into nine ingots: no XP', g === 0 && invItems('iron_ingot').reduce((s, i) => s + i.count, 0) === 9, `+${g}`);
  await cmd('/clear @s', 300);
  await give('iron_nugget', 9, 300);
  g = await craftOne(async w => { for (let s = 1; s <= 9; s++) { const f = findIn(w, 'iron_nugget'); await clickW(f); await clickW(s, 1); await clickW(f); } });
  check('Nine nuggets into an ingot: no XP', g === 0, `+${g}`);
  await cmd('/clear @s', 300);
  // The repair recipe: two worn swords into one.
  await give('iron_sword[damage=100]', 1, 300); await give('iron_sword[damage=100]', 1, 300);
  g = await craftOne(async w => { const a = findIn(w, 'iron_sword'); await clickW(a); await clickW(1); const b = findIn(w, 'iron_sword'); await clickW(b); await clickW(2); });
  check('Two worn swords repaired into one: no XP', g === 0 && invItems('iron_sword').length === 1, `+${g}, ${invItems('iron_sword').length} sword(s)`);
  await cmd('/clear @s', 300);
}

/** /cerb gather info's "in hand" line: the held item's name and quality as the server has them. */
async function inHand() {
  const a = await cmd('/cerb gather info', 900);
  const t = chat.slice(a).find(m => /in hand:/.test(m)) || '';
  const m = t.match(/in hand: (\w+)(?:, quality (\w+))?/);
  return m ? { name: m[1], quality: m[2] || null } : { name: null, quality: null };
}
/** How many of an item the player has, as the server counts them. */
async function serverCount(item) {
  const a = await cmd(`/clear @s ${item} 0`, 700);
  const m = (saw(/Found (\d+) matching item/, a) || '').match(/(\d+)/);
  return m ? Number(m[1]) : 0;
}

async function upgrade() {
  await arena();
  await setLevel('Smithing', 1);
  await stand(0, 0);
  await setblock(2, 0, 0, 'smithing_table');
  await give('diamond_sword'); await hold('diamond_sword');
  await cmd('/cerb gather quality fine', 700);
  await give('netherite_upgrade_smithing_template'); await give('netherite_ingot');
  // This mineflayer maps the 1.21 smithing table as the old 3-slot one: its slot numbers match the server's, but its
  // own idea of where the inventory starts and its shift-click don't. So: plain clicks, and the result straight
  // into hotbar slot 1 (server slot 31), checked through the server.
  let w = await openAt(2, 0, 0);
  await put(w, 'netherite_upgrade_smithing_template', 0);
  await put(w, 'diamond_sword', 1);
  await put(w, 'netherite_ingot', 2);
  await sleep(500);
  if (process.env.DEBUG) console.log('  smithing table:', w.slots.map((x, i) => x ? `${i}:${x.name}` : null).filter(Boolean).join(' '));
  let before = await smithing();
  await clickW(3, 0, 0, 500);
  await clickW(31, 0, 0, 500);
  bot.closeWindow(w);
  await sleep(500);
  bot.setQuickBarSlot(0);
  await sleep(400);
  const gained = (await smithing()) - before;
  const held = await inHand();
  check('The netherite upgrade: 50 Smithing XP', gained === 50, `+${gained}`);
  check('...and the sword keeps its Fine quality', held.name === 'netherite_sword' && held.quality === 'Fine', JSON.stringify(held));
  await cmd('/clear @s', 300);

  await give('sentry_armor_trim_smithing_template'); await give('iron_chestplate'); await give('iron_ingot');
  w = await openAt(2, 0, 0);
  await put(w, 'sentry_armor_trim_smithing_template', 0);
  await put(w, 'iron_chestplate', 1);
  await put(w, 'iron_ingot', 2);
  await sleep(500);
  before = await smithing();
  await clickW(3, 0, 0, 500);
  await clickW(31, 0, 0, 500);
  bot.closeWindow(w);
  await sleep(500);
  check('An armor trim: no Smithing XP', (await smithing()) === before && (await serverCount('iron_chestplate')) === 1, `+${(await smithing()) - before}`);
  await cmd('/clear @s', 300);

  // A shift-click on the result: vanilla's statistic counts nothing for it; the upgrade still pays once.
  await give('diamond_pickaxe'); await give('netherite_upgrade_smithing_template'); await give('netherite_ingot');
  w = await openAt(2, 0, 0);
  await put(w, 'netherite_upgrade_smithing_template', 0);
  await put(w, 'diamond_pickaxe', 1);
  await put(w, 'netherite_ingot', 2);
  await sleep(500);
  before = await smithing();
  await clickW(3, 0, 1, 900);
  bot.closeWindow(w);
  await sleep(500);
  check('A shift-click upgrade: 50 Smithing XP, once', (await smithing()) - before === 50 && (await serverCount('netherite_pickaxe')) === 1,
      `+${(await smithing()) - before}`);
  await cmd('/clear @s', 300);

  // Negative control: a shift-click that fails for want of room makes nothing, even when a netherite sword lands in
  // the inventory in that same tick (swapped in from the cursor). Counting the inventory a tick later paid 50 for it
  // (this check failed with +50 on that build, 2026-10-26). The two clicks are sent back to back and the server
  // normally takes them in one tick; nothing here proves it did, but the payment now never depends on the timing.
  await give('netherite_upgrade_smithing_template'); await give('diamond_sword'); await give('netherite_ingot'); await give('netherite_sword');
  await give('dirt', 32 * 64, 900); // every other slot full
  w = await openAt(2, 0, 0);
  await put(w, 'netherite_upgrade_smithing_template', 0);
  await put(w, 'diamond_sword', 1);
  await put(w, 'netherite_ingot', 2);
  let own = -1; // the inventory's own netherite sword (slot 3 is the result, a netherite sword too)
  for (let i = 4; i < w.slots.length && own < 0; i++) if (w.slots[i] && w.slots[i].name === 'netherite_sword') own = i;
  await clickW(own, 0, 0, 400); // onto the cursor
  await give('dirt', 4 * 64, 900); // the four slots just emptied: nothing has room now
  await debugOn(true);
  before = await smithing();
  const at0 = chat.length;
  // Both clicks in one go (one server tick): the shift-click on the result, then the cursor's sword onto a dirt stack.
  bot.clickWindow(3, 0, 1).catch(() => {});
  bot.clickWindow(4, 0, 0).catch(() => {});
  await sleep(1200);
  const name = i => (w.slots[i] ? w.slots[i].name : null);
  const shiftSeen = !!chat.slice(at0).find(m => /\[Forge\] smithing click \(SHIFT_LEFT\)/.test(m));
  const swapped = name(4) === 'netherite_sword' && w.selectedItem && w.selectedItem.name === 'dirt';
  const notMade = name(3) === 'netherite_sword' && name(1) === 'diamond_sword';
  const gainedNo = (await smithing()) - before;
  await debugOn(false);
  check('A shift-click with no room, a netherite sword swapped in that tick: no Smithing XP', gainedNo === 0 && shiftSeen && swapped && notMade,
      `+${gainedNo}; shift-click seen ${shiftSeen}, sword swapped in ${swapped}, upgrade not made ${notMade}`);
  bot.closeWindow(w);
  await sleep(500);
  await cmd('/clear @s', 300);
  await cmd('/kill @e[type=item,distance=..12]', 300);
}

async function gear() {
  await arena();
  for (const s of ['Blade_Mastery', 'Weapon_Mastery', 'Mining', 'Survival', 'Heavy_Armor_Training', 'Defensive_Magic', 'Fishing', 'Trading', 'Scavenging', 'Lockpicking']) await setLevel(s, 1);
  await stand(0, 0);
  const info = async () => {
    const a = await cmd('/cerb gather info', 900);
    const t = chat.slice(a).join(' ');
    const m = t.match(/Power ([\d.]+) \(([^)]+)\), Fortitude ([\d.]+), Fortune ([\d.]+)/);
    const c = t.match(/your bonus chance: ([\d.]+)%/);
    return m ? { power: Number(m[1]), weapon: m[2], fort: Number(m[3]), luck: Number(m[4]), chance: c ? Number(c[1]) : NaN } : {};
  };
  await setblock(2, 0, 0, 'stone'); // something to look at
  await give('iron_sword'); await hold('iron_sword');
  const plain = await info();
  await cmd('/cerb gather quality masterwork', 900);
  const mw = await info();
  check('A Masterwork sword in hand: +10 Power with swords', Math.abs(mw.power - plain.power - 10) < 1e-6 && mw.weapon === 'sword', `${plain.power} -> ${mw.power}`);
  // In the off hand it adds nothing: swap it there (the inventory's off-hand key), then hold a plain sword.
  try { await bot.equip(bot.heldItem, 'off-hand'); } catch (e) { console.log('  off-hand equip: ' + e.message); }
  await sleep(500);
  await give('iron_sword');
  await hold('iron_sword');
  const off = await info();
  const offHand = await inHand();
  check('...in the off hand, with a plain sword in hand: nothing', Math.abs(off.power - plain.power) < 1e-6 && offHand.quality === null, `${off.power}, ${JSON.stringify(offHand)}`);
  await cmd('/clear @s', 300);

  // Armor: Fortitude per piece worn (held, it adds none).
  await give('iron_chestplate'); await hold('iron_chestplate');
  await cmd('/cerb gather quality masterwork', 700);
  const bare = await info();
  bot.activateItem(); // right-click: put it on
  await sleep(900);
  const worn = await info();
  check('A Masterwork chestplate worn: +4 Fortitude (held, it adds none)', Math.abs(worn.fort - bare.fort - 4) < 1e-6, `${bare.fort} -> ${worn.fort}`);
  await give('iron_leggings'); await hold('iron_leggings');
  await cmd('/cerb gather quality fine', 400);
  bot.activateItem();
  await sleep(900);
  const two = await info();
  check('...plus Fine leggings: +1 more', Math.abs(two.fort - worn.fort - 1) < 1e-6, `${worn.fort} -> ${two.fort}`);
  await cmd('/clear @s', 600);
  const none = await info();
  check('...and taken off: back where it was', Math.abs(none.fort - bare.fort) < 1e-6, `${none.fort}`);

  // A pickaxe's quality: Fortune while held, and the bonus chance follows (0.5% per point).
  await give('iron_pickaxe'); await hold('iron_pickaxe');
  const p0 = await info();
  await cmd('/cerb gather quality superior', 900);
  const p1 = await info();
  check('A Superior pickaxe in hand: +6 Fortune, +3% bonus chance', Math.abs(p1.luck - p0.luck - 6) < 1e-6 && Math.abs(p1.chance - p0.chance - 3) < 0.11, `${p0.luck} -> ${p1.luck}, ${p0.chance}% -> ${p1.chance}%`);

  // Rebuilds: switching between plain items rebuilds nothing; to and from a forged one rebuilds.
  await cmd('/clear @s', 300);
  await give('stone', 1, 200); await give('dirt', 1, 200); await give('iron_shovel', 1, 300);
  const built = async () => { const a = await cmd('/cerb gather stats', 900); const m = (saw(/Attribute snapshots built: \d+/, a) || '').match(/(\d+)$/); return m ? Number(m[1]) : NaN; };
  const r0 = await built();
  for (let i = 0; i < 10; i++) { bot.setQuickBarSlot(i % 3); await sleep(250); }
  const r1 = await built();
  check('Scrolling between plain items 10 times rebuilds no attributes', r1 === r0, `${r0} -> ${r1}`);
  await hold('iron_shovel');
  await cmd('/cerb gather quality fine', 600);
  const r2 = await built();
  for (let i = 0; i < 4; i++) { bot.setQuickBarSlot(i % 2 === 0 ? 0 : 2); await sleep(300); }
  const r3 = await built();
  check('...to and from a Fine shovel 4 times: a rebuild per change', r3 - r2 >= 3 && r3 - r2 <= 5, `${r2} -> ${r3}`);
  await cmd('/clear @s', 300);
}

const TEST_RESOURCES = {
  items: '  test_crystal: { material: AMETHYST_SHARD, name: "Test Crystal", lore: ["A synthetic drop (gathering test)"] }',
  tools: '  test_drill: { material: DIAMOND_PICKAXE, name: "Test Drill", kinds: [drill, pickaxe], tier: 5 }',
  resource: '  test_vein:      { skill: Mining, blocks: [AMETHYST_BLOCK], tool: drill, tier: 5, level: 50, xp: 75, fortune: true, drops: [ { item: test_crystal, min: 1, max: 2 } ] }',
};

async function custom() {
  const dir = process.env.SERVER_DIR;
  if (!dir) { check('SERVER_DIR names the sandbox server (its resources.yml gets the test entries for this phase)', false); return; }
  const file = `${dir}/plugins/CerberusPlugin/resources.yml`;
  const original = fs.readFileSync(file, 'utf8');
  const test = original.replace(/^tools: \{\}$/m, 'tools:\n' + TEST_RESOURCES.tools).replace(/^items: \{\}$/m, 'items:\n' + TEST_RESOURCES.items)
      .replace(/^resources:$/m, 'resources:\n' + TEST_RESOURCES.resource);
  fs.writeFileSync(file, test);
  try {
    let a = await cmd('/cerb reload', 2500);
    await arena();
    await setLevel('Mining', 1);
    a = await cmd('/cerb gather info', 300);
    await give('netherite_pickaxe'); await hold('netherite_pickaxe');
    await stand(0, 0);
    // A pickaxe can't gather it: the block stays, no XP, a message saying what it needs.
    await setblock(2, 0, 0, 'amethyst_block');
    let before = await mining();
    await dig(2, 0, 0);
    await sleep(300);
    const msg = actionBar;
    check('The test vein with a netherite pickaxe: refused, the block stays, no XP', blockName(2, 0, 0) === 'amethyst_block' && (await mining()) === before, blockName(2, 0, 0));
    check('...with a message naming what it needs', /needs a drill \(tier 5\)/.test(msg), msg);
    // The drill gathers it: 75 XP and the custom drop.
    a = await cmd('/cerb gather tool test_drill', 700);
    await hold('diamond_pickaxe');
    before = await mining();
    const d = await mineOne(2, 0, 0);
    const shards = d.filter(x => x.name === 'amethyst_shard');
    check('The drill gathers it: 75 Mining XP', (await mining()) - before === 75, `+${(await mining()) - before}`);
    check('...and drops 1-3 Test Crystals (a custom item), not the block', shards.length > 0 && count(d, 'amethyst_shard') >= 1 && count(d, 'amethyst_shard') <= 3
        && count(d, 'amethyst_block') === 0, JSON.stringify(d.map(x => [x.name, x.count])));
    // A machine's extraction through the same definitions: the drop, no XP; the wrong tool can't.
    await setblock(2, 0, 0, 'amethyst_block');
    before = await mining();
    const inv0 = invCounts();
    a = await cmd('/cerb gather extract', 900);
    const ex = saw(/Extract amethyst_block/, a) || '';
    const ed = await drops(2, 0, 0);
    const got = count(ed, 'amethyst_shard') + Math.max(0, (invCounts().amethyst_shard || 0) - (inv0.amethyst_shard || 0)); // fallen or picked up
    check('A machine with the drill extracts it: Test Crystals, no XP', /done/.test(ex) && /test_crystal/.test(ex) && got >= 1 && (await mining()) === before, `${ex}; ${got} found`);
    await setblock(2, 0, 0, 'amethyst_block');
    await give('netherite_pickaxe'); await hold('netherite_pickaxe');
    a = await cmd('/cerb gather extract', 900);
    const ex2 = saw(/Extract amethyst_block/, a) || '';
    check('...a machine with a pickaxe can\'t', /not done, needs a drill/.test(ex2) && blockName(2, 0, 0) === 'amethyst_block', ex2);
    // A placed one: its vanilla drop back, nothing else.
    await cmd(`/setblock ${X + 2} ${Y} ${Z} air`, 300);
    await give('amethyst_block');
    await hold('amethyst_block');
    try { await bot.placeBlock(bot.blockAt(at(2, -1, 0)), new Vec3(0, 1, 0)); } catch (e) { /* checked below */ }
    await sleep(500);
    await hold('diamond_pickaxe');
    before = await mining();
    const pd = await mineOne(2, 0, 0);
    check('A placed test vein broken with the drill: its own block back, no XP, no crystals', count(pd, 'amethyst_block') === 1 && count(pd, 'amethyst_shard') === 0
        && (await mining()) === before, JSON.stringify(pd.map(x => [x.name, x.count])));
    // A message takes priority over the XP line; the XP line comes back after it.
    await setblock(3, 0, 0, 'stone');
    await setblock(2, 0, 0, 'amethyst_block');
    await hold('netherite_pickaxe');
    await dig(3, 0, 0);
    const xpLine = actionBar;
    await dig(2, 0, 0, true, 400); // one attempt (mineflayer's dig() keeps retrying a refused break, which re-shows the message)
    await sleep(100);
    const during = actionBar;
    // The message lasts 1.5 s; the HUD redraws once a second, so the XP line is back within about 2.5 s.
    let afterMsg = actionBar;
    for (let t = 0; t < 3000 && !/Mining/.test(afterMsg); t += 100) { await sleep(100); afterMsg = actionBar; }
    check('The XP line shows; a refusal message covers it; then the XP line is back', /\+1 Mining/.test(xpLine) && /needs a drill/.test(during) && /Mining/.test(afterMsg),
        `${xpLine} | ${during} | ${afterMsg}`);
  } finally {
    fs.writeFileSync(file, original);
    await cmd('/cerb reload', 2500);
  }
  const a = await cmd('/cerb gather tool test_drill', 700);
  check('resources.yml put back: the test drill is gone', !!saw(/Tools in resources.yml: \[\]/, a), saw(/Tools in resources.yml/, a) || '');
}

async function xpdisplay() {
  await arena();
  await setLevel('Mining', 1);
  await give('iron_pickaxe'); await hold('iron_pickaxe');
  await stand(0, 0);
  await cmd('/xpdisplay compact', 400);
  for (let i = 0; i < 5; i++) { await setblock(2, 0, 0, 'stone', 150); await dig(2, 0, 0); }
  check('Compact: five stones in a row make one line, +5 Mining', /\+5 Mining/.test(actionBar) && !/XP ·/.test(actionBar), actionBar);
  await sleep(4500);
  await cmd('/xpdisplay detailed', 400);
  await setblock(2, 0, 0, 'iron_ore', 150); await dig(2, 0, 0);
  check('Detailed: +8 Mining XP · progress · level', /\+8 Mining XP · [\d,]+\/[\d,]+ · Lv \d+/.test(actionBar), actionBar);
  await sleep(3000);
  await cmd('/xpdisplay off', 400);
  await setblock(2, 0, 0, 'stone', 150); await dig(2, 0, 0);
  check('Off: no XP line', !/Mining/.test(actionBar), actionBar);
  await cmd('/xpdisplay compact', 400);
  await cmd('/kill @e[type=item,distance=..10]', 300);
}

const STATE = process.env.STATE || require('os').tmpdir() + '/cerb-gathering-persist.json';
async function persistSet() {
  await arena(2000);
  await setLevel('Mining', 5);
  await give('iron_pickaxe'); await hold('iron_pickaxe');
  await stand(0, 0);
  await setblock(3, 0, 0, 'stone'); await dig(3, 0, 0); // some XP into the level
  await give('iron_ore'); await hold('iron_ore');
  try { await bot.placeBlock(bot.blockAt(at(2, -1, 0)), new Vec3(0, 1, 0)); } catch (e) {}
  await sleep(500);
  await give('iron_sword'); await hold('iron_sword');
  await cmd('/cerb gather quality masterwork', 700);
  await cmd('/xpdisplay detailed', 400);
  const state = { mining: await mining(), placed: blockName(2, 0, 0), quality: qualityOf(bot.heldItem) };
  fs.writeFileSync(STATE, JSON.stringify(state));
  check('Recorded for after the restart', state.placed === 'iron_ore' && state.quality === 'Masterwork', JSON.stringify(state));
  await cmd('/save-all', 1500);
}

async function persistCheck() {
  const state = JSON.parse(fs.readFileSync(STATE, 'utf8'));
  const sp = bot.spawnPoint || bot.entity.position;
  X = Math.floor(sp.x) + 2000; Y = 120; Z = Math.floor(sp.z) + 300;
  await cmd(`/tp @s ${X + 0.5} ${Y} ${Z + 0.5} -90 0`, 2500);
  check('Mining XP survived the restart', (await mining()) === state.mining, `${state.mining} -> ${await mining()}`);
  const sword = invItems('iron_sword')[0];
  check('The Masterwork sword is still Masterwork', qualityOf(sword) === 'Masterwork', loreOf(sword));
  await hold('iron_pickaxe');
  const before = await mining();
  await dig(2, 0, 0);
  check('The ore placed before the restart still pays nothing', blockName(2, 0, 0) === 'air' && (await mining()) === before, blockName(2, 0, 0));
  await setblock(3, 0, 0, 'stone');
  await dig(3, 0, 0);
  check('The XP display is still Detailed', /Mining XP · /.test(actionBar), actionBar);
  await cmd('/xpdisplay compact', 400);
  await cmd('/clear @s', 300);
}

const PHASES = { mine, farm, fortune, smelt, forge, upgrade, gear, custom, xpdisplay, 'persist-set': persistSet, 'persist-check': persistCheck };

bot.once('spawn', async () => {
  try {
    await sleep(2500);
    const run = phase === 'all' ? ['mine', 'farm', 'fortune', 'smelt', 'forge', 'upgrade', 'gear', 'xpdisplay'] : [phase];
    for (const p of run) {
      if (!PHASES[p]) { console.log('Unknown phase ' + p); process.exit(1); }
      console.log(`== ${p}`);
      await PHASES[p]();
    }
    await cmd('/gamerule spawn_mobs true', 300);
    await cmd('/gamerule advance_time true', 300);
  } catch (e) {
    console.log('ERROR', e && e.stack || e);
    results.push(false);
  }
  const failed = results.filter(r => !r).length;
  console.log(`\n${results.length - failed}/${results.length} passed`);
  bot.quit();
  setTimeout(() => process.exit(failed ? 1 : 0), 500);
});
