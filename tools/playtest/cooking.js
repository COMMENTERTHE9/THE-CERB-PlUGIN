// Cooking in game (docs/design/FARMING_COOKING.md §4). LOCAL SANDBOX servers only; the bot must be op.
// Usage: node cooking.js [phase[,phase...]] [botName]
//   furnace  - food taken out of a smoker pays Cooking XP (cooked beef 3 each, a shift-click every item); a hopper's take
//              pays nobody; creative takes nothing (and the XP display says why); Fine potatoes in a smoker all bake, plain
//   campfire - food the bot put on a campfire pays it when the campfire finishes it (cooked beef 3 each); a Fine potato
//              roasts into a Fine baked potato; food put on in creative pays nothing
//   craft    - bread at a crafting table pays 4, a shift-click batch every loaf; dried kelp from a dried kelp block nothing
//              (reversible storage); creative nothing
//   meals    - a meal's recipe makes nothing below its level, the meal from it (Cooking 10: Hearty Stew, Common), its
//              ingredients used once, its own XP (not a vanilla stew's); Cooking 25 makes it Fine, Fine crops lift it a tier;
//              a crafter never makes one
//   eat      - eating at full hunger works; the effect is on the character (max health from Vitality, movement speed from a
//              Trail Ration, Fortune on crops from a Farmer's Breakfast); a new meal replaces the running one (and says so),
//              the same one refreshes its time; restoration heals a hurt character over its seconds, never past max health
//   persist  - the running meal survives a relog with its time; death ends it
//   expire   - a meal wears off at its time and its effect leaves (a one-minute Hearty Stew written into the sandbox's
//              resources.yml for the phase and put back; needs SERVER_DIR)
//   menu     - Player Menu → Skills → Cooking → Cookbook and Collections → Meals agree with /cooking
//   restart-set / restart-check - a running Superior Hearty Stew and Replant switched off, the bot online when the server
//              stops cleanly; after the start the meal runs on with its time and the switch is still off (run restart-set,
//              stop and start the server, then restart-check with the same name)
//   all      - furnace, campfire, craft, meals, eat, persist, menu (the default)
const mineflayer = require('mineflayer');
const { Vec3 } = require('vec3');
const fs = require('fs');
const path = require('path');
const phaseArg = process.argv[2] || 'all';
const NAME = process.argv[3] || 'CerbCook';
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
const cookXp = async () => totalXp(await skill('Cooking'));
async function setLevel(level, s = 'Cooking') { await cmd(`/cerb skill ${NAME} ${s} level ${level}`, 500); }

/** Health and max health from the HUD: "103/103♥". */
function hud() {
  for (let i = bars.length - 1; i >= 0; i--) {
    const m = bars[i].m.match(/([\d,]+)\/([\d,]+)♥/);
    if (m) return { hp: Number(m[1].replace(/,/g, '')), max: Number(m[2].replace(/,/g, '')) };
  }
  return { hp: NaN, max: NaN };
}
/** Server-side attribute value (/attribute works on Folia). */
async function attr(name) {
  const at = await cmd(`/attribute @s minecraft:${name} get`, 800);
  const m = chat.slice(at).map(x => x.match(/The value of attribute .+ for entity \S+ is ([-\d.E]+)/)).find(Boolean);
  return m ? Number(m[1]) : NaN;
}

// ── the kitchen: a platform in the sky far from spawn ─────────────
let X = 0, Y = 0, Z = 0;
async function kitchen() {
  const sp = bot.spawnPoint || bot.entity.position;
  X = Math.floor(sp.x) + 3000 + (Date.now() % 20000) * 64; Y = 120; Z = Math.floor(sp.z) - 600;
  await cmd('/gamemode creative', 300);
  await cmd(`/tp @s ${X + 0.5} ${Y + 1} ${Z + 0.5}`, 1000);
  try { await bot.waitForChunksToLoad(); } catch (e) { /* the fill retries */ }
  await cmd('/gamerule spawn_mobs false', 300);
  for (let i = 0; i < 20; i++) {
    const a = await cmd(`/fill ${X - 6} ${Y - 1} ${Z - 6} ${X + 6} ${Y - 1} ${Z + 6} stone`, 600);
    if (!saw(/not loaded/i, a)) break;
    await sleep(1000);
  }
  await cmd(`/fill ${X - 6} ${Y} ${Z - 6} ${X + 6} ${Y + 4} ${Z + 6} air`, 600);
  await cmd('/kill @e[type=item,distance=..30]', 300);
  await cmd('/clear @s', 400);
  await cmd('/gamemode survival', 300);
  await cmd('/effect clear @s', 300);
  await cmd('/effect give @s resistance infinite 4 true', 300);
  await cmd('/effect give @s saturation infinite 1 true', 300); // full hunger: meals must be edible anyway
  await cmd(`/cerb cook clear ${NAME}`, 400);
  await cmd(`/tp @s ${X + 0.5} ${Y} ${Z + 0.5} 0 20`, 500);
}
const at = (dx, dy, dz) => new Vec3(X + dx, Y + dy, Z + dz);
async function setblock(dx, dy, dz, b, wait = 400) { await cmd(`/setblock ${X + dx} ${Y + dy} ${Z + dz} ${b}`, wait); }
async function give(item, n = 1, wait = 500) { await cmd(`/give @s ${item} ${n}`, wait); }
const invCount = name => bot.inventory.slots.filter(it => it && it.name === name).reduce((a, it) => a + it.count, 0);
const loreOf = it => (it && it.customLore ? it.customLore.map(l => {
  try { return require('prismarine-chat')(bot.registry).fromNotch(l).toString(); } catch (e) { return String(l); }
}).join(' | ') : '');
const nameOf = it => {
  if (!it || !it.customName) return '';
  try { return require('prismarine-chat')(bot.registry).fromNotch(it.customName).toString(); } catch (e) { return String(it.customName); }
};
const fineData = '[custom_data={PublicBukkitValues:{"cerberus:crop_quality":"fine"}}]';
async function click(slot, button = 0, mode = 0) { try { await bot.clickWindow(slot, button, mode); } catch (e) { /* the server decides */ } await sleep(220); }

// ── a smoker ──────────────────────────────────────────────────────
/** Put `n` of `item` in the smoker's input and coal in its fuel; wait until `want` cooked items are in its output. */
async function smoke(item, n, want, { take = 'shift' } = {}) {
  await setblock(2, 0, 2, 'smoker[facing=north]', 600);
  const w = await bot.openFurnace(bot.blockAt(at(2, 0, 2)));
  await sleep(300);
  const find = pred => { for (let i = w.inventoryStart; i < w.inventoryEnd; i++) if (w.slots[i] && pred(w.slots[i])) return i; return -1; };
  const s = find(it => it.name === item);
  if (s >= 0) { await click(s); for (let i = 0; i < n; i++) await click(0, 1); await click(s); }
  const c = find(it => it.name === 'coal');
  if (c >= 0) { await click(c); await click(1, 1); await click(c); }
  let out = 0;
  for (let i = 0; i < n * 6 + 6 && out < want; i++) { await sleep(1000); out = w.slots[2] ? w.slots[2].count : 0; }
  const output = w.slots[2];
  if (take === 'shift') await click(2, 0, 1);
  else if (take === 'click') { await click(2, 0, 0); const free = find(it => false); void free; }
  await sleep(400);
  return { w, out, output };
}

// ════════════════════════════════════════════════════════════════
async function furnacePhase() {
  await kitchen();
  await setLevel(1);
  await give('beef', 2); await give('coal', 2);
  const x0 = await cookXp();
  const { w, out } = await smoke('beef', 2, 2);
  bot.closeWindow(w);
  await sleep(500);
  const g = (await cookXp()) - x0;
  check('Two cooked beef taken out of a smoker (a shift-click): 3 XP each', out === 2 && g === 6 && invCount('cooked_beef') === 2, `${out} cooked, +${g}`);
  // A hopper under the smoker takes the output: nobody is paid.
  await cmd('/clear @s', 300);
  await setblock(2, -1, 2, 'hopper', 400);
  await setblock(2, -2, 2, 'chest', 400);
  await give('beef', 2); await give('coal', 2);
  const h0 = await cookXp();
  const s2 = await smoke('beef', 2, 99, { take: 'none' });
  bot.closeWindow(s2.w);
  await sleep(1500);
  check('A hopper takes the smoker\'s output: no Cooking XP', (await cookXp()) - h0 === 0);
  await setblock(2, -1, 2, 'stone', 300); await setblock(2, -2, 2, 'stone', 300);
  // Creative: nothing, and the XP display says why.
  await cmd('/clear @s', 300);
  await give('beef', 1); await give('coal', 1);
  const s3 = await smoke('beef', 1, 1, { take: 'none' });
  await cmd('/gamemode creative', 400);
  const c0 = await cookXp();
  const t = Date.now();
  await click(2, 0, 1);
  bot.closeWindow(s3.w);
  await sleep(600);
  check('Creative takes the cooked beef: no XP, and the XP display says why', (await cookXp()) - c0 === 0 && hudSince(t).some(h => /creative/i.test(h)),
    JSON.stringify(hudSince(t).slice(-2)));
  await cmd('/gamemode survival', 400);
  // Fine potatoes in a smoker all bake, plain: a quality result would stop it at the first (its output takes only the
  // recipe's plain result); a campfire carries the quality (campfire phase).
  await cmd('/clear @s', 300);
  await give('potato' + fineData, 3); await give('coal', 1);
  const s4 = await smoke('potato', 3, 3, { take: 'none' });
  const lore = loreOf(s4.output);
  await click(2, 0, 1);
  bot.closeWindow(s4.w);
  check('Three Fine potatoes in a smoker: all three bake, plain (the smoker never stalls)', s4.out === 3 && s4.output && s4.output.name === 'baked_potato' && !/quality/.test(lore),
    `${s4.out} baked; ${lore || 'plain'}`);
}

async function campfirePhase() {
  await kitchen();
  await setLevel(1);
  await setblock(0, 0, 2, 'campfire[lit=true]', 500);
  await give('beef', 2); await give('potato' + fineData, 1);
  const holdItem = async name => {
    const k = bot.inventory.slots.findIndex((it, i) => it && it.name === name && i >= 36 && i <= 44);
    if (k >= 0) bot.setQuickBarSlot(k - 36);
    await sleep(300);
  };
  await holdItem('beef');
  const x0 = await cookXp();
  try { await bot.activateBlock(bot.blockAt(at(0, 0, 2))); } catch (e) { /* the server decides */ }
  await sleep(300);
  try { await bot.activateBlock(bot.blockAt(at(0, 0, 2))); } catch (e) { /* the server decides */ }
  await sleep(300);
  await holdItem('potato');
  try { await bot.activateBlock(bot.blockAt(at(0, 0, 2))); } catch (e) { /* the server decides */ }
  note('two beef and a Fine potato on the campfire; it takes 30 s');
  await sleep(33_000);
  const g = (await cookXp()) - x0;
  check('Two beef and a potato the bot put on a campfire: cooked, 3 + 3 + 2 XP to the bot', g === 8, `+${g}`);
  const roasted = Object.values(bot.entities).filter(e => e.name === 'item' && e.position.distanceTo(at(0, 0, 2)) < 3)
    .map(e => { try { const d = e.getDroppedItem(); return d.name + (loreOf(d) ? ' (' + loreOf(d) + ')' : ''); } catch (er) { return '?'; } });
  check('...and the Fine potato roasts into a Fine baked potato (a campfire carries the quality)', roasted.some(r => /baked_potato \(.*Fine quality/.test(r)), roasted.join(', '));
  // Food put on in creative pays nothing, even when the campfire finishes it in survival.
  await cmd('/kill @e[type=item,distance=..20]', 300);
  await setblock(3, 0, 2, 'campfire[lit=true]', 400);
  await cmd('/gamemode creative', 400);
  await give('beef', 1);
  const j = bot.inventory.slots.findIndex((it, i) => it && it.name === 'beef' && i >= 36 && i <= 44);
  if (j >= 0) bot.setQuickBarSlot(j - 36);
  await sleep(300);
  try { await bot.activateBlock(bot.blockAt(at(3, 0, 2))); } catch (e) { /* the server decides */ }
  await sleep(500);
  await cmd('/gamemode survival', 400);
  const d0 = await cookXp();
  await sleep(33_000);
  const near = Object.values(bot.entities).filter(e => e.name === 'item' && e.position.distanceTo(at(3, 0, 2)) < 3)
    .map(e => { try { return e.getDroppedItem().name; } catch (er) { return '?'; } });
  check('Beef put on a campfire in creative: cooked, no XP (even back in survival)', near.includes('cooked_beef') && (await cookXp()) - d0 === 0, near.join(','));
}

/** Open the crafting table next to the bot; null when it can't. */
async function table() {
  await setblock(-2, 0, 2, 'crafting_table', 500);
  try { const w = await bot.openBlock(bot.blockAt(at(-2, 0, 2))); await sleep(300); return w; } catch (e) { return null; }
}
/** One of each of `items` ({name, pred?}) into grid slots 1.. of the open table. */
async function fillGrid(w, items) {
  let slot = 1;
  for (const it of items) {
    const name = typeof it === 'string' ? it : it.name;
    let src = -1;
    for (let i = w.inventoryStart; i < w.inventoryEnd; i++) if (w.slots[i] && w.slots[i].name === name) { src = i; break; }
    if (src < 0) { note('no ' + name + ' to put in the grid'); slot++; continue; }
    await click(src); await click(slot, 1); await click(src);
    slot++;
  }
  await sleep(400);
}
/** Items of `name` in the open window's player-inventory section (the bot's own inventory view lags while a window is open). */
const winCount = (w, name) => { let n = 0; for (let i = w.inventoryStart; i < w.inventoryEnd; i++) if (w.slots[i] && w.slots[i].name === name) n += w.slots[i].count; return n; };
async function clearGrid(w) { for (let i = 1; i <= 9; i++) if (w.slots[i]) await click(i, 0, 1); }

async function craftPhase() {
  await kitchen();
  await setLevel(1);
  await give('wheat', 15);
  const w = await table();
  if (!w) { check('a crafting table opens', false); return; }
  await fillGrid(w, ['wheat', 'wheat', 'wheat']);
  const x0 = await cookXp();
  await click(0, 0, 1);
  await sleep(400);
  check('Bread at a crafting table: 4 Cooking XP', (await cookXp()) - x0 === 4 && winCount(w, 'bread') === 1);
  // A shift-click batch: three rows of wheat make four more loaves.
  for (let slot = 1; slot <= 3; slot++) {
    let src = -1;
    for (let i = w.inventoryStart; i < w.inventoryEnd; i++) if (w.slots[i] && w.slots[i].name === 'wheat') { src = i; break; }
    if (src >= 0) { await click(src); for (let k = 0; k < 4; k++) await click(slot, 1); await click(src); }
  }
  await sleep(300);
  const b0 = await cookXp();
  await click(0, 0, 1);
  await sleep(500);
  const g = (await cookXp()) - b0;
  check('A shift-click batch of bread: every loaf pays (4 loaves: 16)', winCount(w, 'bread') === 5 && g === 16, `${winCount(w, 'bread')} bread, +${g}`);
  await clearGrid(w);
  // Dried kelp from a dried kelp block: storage, nothing.
  await give('dried_kelp_block', 1);
  await sleep(300);
  await fillGrid(w, ['dried_kelp_block']);
  const k0 = await cookXp();
  await click(0, 0, 1);
  await sleep(400);
  check('Dried kelp from a dried kelp block (reversible storage): no XP', winCount(w, 'dried_kelp') === 9 && (await cookXp()) - k0 === 0, `${winCount(w, 'dried_kelp')} kelp`);
  // Creative: nothing.
  await give('wheat', 3);
  await fillGrid(w, ['wheat', 'wheat', 'wheat']);
  await cmd('/gamemode creative', 400);
  const c0 = await cookXp();
  await click(0, 0, 1);
  await sleep(400);
  check('Bread in creative: no XP', (await cookXp()) - c0 === 0);
  await cmd('/gamemode survival', 400);
  bot.closeWindow(w);
}

const STEW = ['bowl', 'cooked_beef', 'carrot', 'potato', 'beetroot'];
async function stewIngredients(n = 1, fine = []) {
  for (const i of STEW) await give(fine.includes(i) ? i + fineData : i, n, 300);
}

async function mealsPhase() {
  await kitchen();
  await setLevel(9);
  await stewIngredients(3);
  let w = await table();
  if (!w) { check('a crafting table opens', false); return; }
  await fillGrid(w, STEW);
  check('Cooking 9: the Hearty Stew recipe makes nothing', !w.slots[0], w.slots[0] ? w.slots[0].name : 'empty');
  await clearGrid(w);
  bot.closeWindow(w);
  await setLevel(10);
  w = await table();
  await fillGrid(w, STEW);
  const res = w.slots[0];
  check('Cooking 10: a Hearty Stew (Common), its effect on the item', res && nameOf(res) === 'Hearty Stew' && /Common/.test(loreOf(res)) && /Vitality \+6/.test(loreOf(res)),
    res ? nameOf(res) + ': ' + loreOf(res) : 'empty');
  const x0 = await cookXp();
  const carrots0 = winCount(w, 'carrot') + 1; // one is in the grid
  await click(0, 0, 1);
  await sleep(500);
  const g = (await cookXp()) - x0;
  check('...made once: its own 20 XP (not a vanilla rabbit stew\'s 12), one of each ingredient used', g === 20 && winCount(w, 'rabbit_stew') === 1
    && !w.slots[3] && winCount(w, 'carrot') === carrots0 - 1, `+${g}; stews ${winCount(w, 'rabbit_stew')}; grid carrot ${w.slots[3] ? w.slots[3].count : 0}; carrots ${carrots0} → ${winCount(w, 'carrot')}`);
  await clearGrid(w);
  bot.closeWindow(w);
  // Cooking 25 cooks Fine; two Fine crops of three lift a Cooking 10 stew to Fine.
  await setLevel(25);
  w = await table();
  await fillGrid(w, STEW);
  check('Cooking 25: the stew is Fine (effects x1.2: Vitality +7.2)', w.slots[0] && /Fine/.test(loreOf(w.slots[0])) && /Vitality \+7\.2/.test(loreOf(w.slots[0])),
    w.slots[0] ? loreOf(w.slots[0]) : 'empty');
  await clearGrid(w);
  bot.closeWindow(w);
  await cmd('/clear @s', 300);
  await setLevel(10);
  await stewIngredients(1, ['carrot', 'potato']);
  w = await table();
  await fillGrid(w, STEW);
  check('Cooking 10 with a Fine carrot and potato (2 of its 3 crops): the stew is lifted to Fine', w.slots[0] && /Fine/.test(loreOf(w.slots[0])),
    w.slots[0] ? loreOf(w.slots[0]) : 'empty');
  await clearGrid(w);
  bot.closeWindow(w);
  await cmd('/clear @s', 300);
  await stewIngredients(1, ['carrot']);
  w = await table();
  await fillGrid(w, STEW);
  check('...one Fine crop of three: still Common', w.slots[0] && /Common/.test(loreOf(w.slots[0])), w.slots[0] ? loreOf(w.slots[0]) : 'empty');
  await clearGrid(w);
  bot.closeWindow(w);
  await cmd('/clear @s', 300);
  // A crafter never makes a meal: fill one through its window and power it.
  await setblock(0, 0, 3, 'crafter[orientation=up_north]', 500);
  await stewIngredients(1);
  let made = 'none';
  try {
    const cw = await bot.openBlock(bot.blockAt(at(0, 0, 3)));
    await sleep(300);
    let slot = 0;
    for (const i of STEW) {
      let src = -1;
      for (let k = cw.inventoryStart; k < cw.inventoryEnd; k++) if (cw.slots[k] && cw.slots[k].name === i) { src = k; break; }
      if (src >= 0) { await click(src); await click(slot, 1); await click(src); }
      slot++;
    }
    bot.closeWindow(cw);
    await setblock(0, 0, 4, 'redstone_block', 800);
    await sleep(1000);
    const items = Object.values(bot.entities).filter(e => e.name === 'item' && e.position.distanceTo(at(0, 1, 3)) < 3);
    made = items.map(e => { try { const d = e.getDroppedItem(); return d.name + (nameOf(d) ? '(' + nameOf(d) + ')' : ''); } catch (er) { return '?'; } }).join(',') || 'none';
  } catch (e) { made = 'error ' + e.message; }
  check('A crafter given a Hearty Stew\'s ingredients makes nothing (no cook, no meal)', !/rabbit_stew/.test(made), made);
  await cmd('/kill @e[type=item,distance=..20]', 300);
}

async function eatOne(name) {
  const it = bot.inventory.items().find(i => nameOf(i) === name);
  if (!it) return false;
  // Held right-click for the eating time, as a player does (mineflayer's consume() refuses at full hunger on its own side).
  try { await bot.equip(it, 'hand'); } catch (e) { note('eat ' + name + ': ' + e.message); return false; }
  const before = bot.inventory.items().filter(i => nameOf(i) === name).reduce((a, i) => a + i.count, 0);
  bot.activateItem();
  await sleep(2300);
  bot.deactivateItem();
  await sleep(500);
  const after = bot.inventory.items().filter(i => nameOf(i) === name).reduce((a, i) => a + i.count, 0);
  return after === before - 1;
}

async function eatPhase() {
  await kitchen();
  for (const s of ['Cooking', 'Farming', 'Survival', 'Heavy_Armor_Training', 'First_Aid', 'Woodcutting', 'Navigation']) await setLevel(1, s);
  await sleep(1200);
  const base = hud();
  await cmd(`/cerb cook give ${NAME} hearty_stew common 2`, 600);
  await cmd(`/cerb cook give ${NAME} trail_ration common 1`, 600);
  await cmd(`/cerb cook give ${NAME} farmers_breakfast fine 1`, 600);
  const at0 = chat.length;
  check('Eaten at full hunger (a meal can always be eaten)', await eatOne('Hearty Stew'));
  await sleep(1200);
  const fed = hud();
  check('Hearty Stew (Common): max health x1.06 (Vitality +6)', Math.abs(fed.max - Math.round(base.max * 1.06)) <= 1, `${base.max} → ${fed.max}`);
  check('...and chat says what it does', chat.slice(at0).some(m => /Hearty Stew.*Vitality \+6.*Restores 25% health over 20 s/.test(m)));
  const sp0 = await attr('movement_speed');
  // Fortune on crops: /cerb farm info shows the chance where the bot looks (before any breakfast).
  await setblock(0, 0, 2, 'farmland', 300); await setblock(0, 1, 2, 'wheat[age=7]', 300);
  const fortune = async () => {
    await bot.lookAt(at(0, 1, 2).offset(0.5, 0.5, 0.5), true);
    const fi = await cmd('/cerb farm info', 800);
    return Number(((saw(/Fortune chance/, fi) || '').match(/Fortune chance ([\d.]+)%/) || [])[1]);
  };
  const f0 = await fortune();
  let a = chat.length;
  await eatOne('Trail Ration');
  await sleep(1200);
  const sp1 = await attr('movement_speed');
  const after = hud();
  check('A Trail Ration replaces it (and says so): max health back, movement speed up (8% raw, about 7% after the cap\'s diminishing returns)',
    chat.slice(a).some(m => /replaces Hearty Stew/.test(m)) && Math.abs(after.max - base.max) <= 1 && sp1 / sp0 - 1 >= 0.06 && sp1 / sp0 - 1 <= 0.0801,
    `max ${after.max}; speed ${sp0} → ${sp1}`);
  await eatOne('Farmer\'s Breakfast');
  await sleep(800);
  const sp2 = await attr('movement_speed');
  check('A Farmer\'s Breakfast replaces the ration: the speed is gone', Math.abs(sp2 - sp0) < 1e-6, `${sp0} → ${sp2}`);
  const f1 = await fortune();
  check('...its Fortune (crops) +12 (Fine): 6% more chance of an extra crop', Math.abs(f1 - f0 - 6) < 0.15, `${f0}% → ${f1}%`);
  let t = Date.now();
  // The same meal again refreshes its time.
  await cmd(`/cerb cook give ${NAME} farmers_breakfast fine 1`, 600);
  t = Date.now();
  await eatOne('Farmer\'s Breakfast');
  check('The same meal again refreshes its time (and says so)', hudSince(t).some(h => /time refreshed/.test(h)), JSON.stringify(hudSince(t).slice(-1)));
  // Restoration: hurt the bot, eat a stew, its health climbs over the seconds and stops at max.
  await cmd('/effect clear @s resistance', 300);
  await cmd('/damage @s 8 minecraft:generic', 800);
  await cmd('/effect give @s resistance infinite 4 true', 300);
  await sleep(600);
  const hurt = hud();
  await eatOne('Hearty Stew');
  await sleep(5200);
  const mid = hud();
  await sleep(17000);
  const end = hud();
  check('Restoration: a hurt character heals over the seconds after a Hearty Stew', hurt.hp < hurt.max && mid.hp > hurt.hp, `${hurt.hp}/${hurt.max} → ${mid.hp} after 5 s`);
  check('...and never past max health', end.hp <= end.max, `${end.hp}/${end.max}`);
}

async function persistPhase() {
  await kitchen();
  await cmd(`/cerb cook give ${NAME} hearty_stew superior 1`, 600);
  await eatOne('Hearty Stew');
  const a0 = await cmd('/cooking', 800);
  const before = saw(/Hearty Stew/, a0) || '';
  bot.quit();
  await sleep(4000);
  await connect();
  await sleep(3000);
  const a1 = await cmd('/cooking', 800);
  const line = saw(/Superior.*Hearty Stew.*left|No meal running/, a1) || '';
  check('The running meal survives a relog with its time', /Superior.*Hearty Stew.*min.*left/.test(line), `${before.slice(-40)} | ${line.slice(-60)}`);
  await cmd('/kill @s', 1500);
  try { bot.respawn && bot.respawn(); } catch (e) { /* auto */ }
  await sleep(3000);
  const a2 = await cmd('/cooking', 800);
  check('Death ends it', !!saw(/No meal running/, a2), saw(/meal|Meal/, a2) || '');
}

/** A running meal and Farming's Replant switch, with the bot online when the server stops cleanly. */
async function restartSetPhase() {
  await kitchen();
  await setLevel(10, 'Farming');
  await cmd('/farming replant off', 500);
  await cmd(`/cerb cook give ${NAME} hearty_stew superior 1`, 600);
  const ate = await eatOne('Hearty Stew');
  const line = saw(/Superior.*Hearty Stew.*left/, await cmd('/cooking', 800)) || '';
  const f0 = await cmd('/farming', 800);
  check('Before the stop: a Superior Hearty Stew running and Replant off (stop and start the server, then run restart-check)',
    ate && !!line && !!saw(/Replant off/, f0), `${line.slice(-50)} | ${saw(/Replant/, f0) || ''}`);
  console.log('READY');
  await new Promise(res => { bot.once('end', res); setTimeout(res, 240000); });
}
async function restartCheckPhase() {
  await sleep(1500);
  const line = saw(/Superior.*Hearty Stew.*left|No meal running/, await cmd('/cooking', 800)) || '';
  check('After a clean stop and start: the meal runs on with its time', /Superior.*Hearty Stew.*min.*left/.test(line), line.slice(-60));
  const f1 = await cmd('/farming', 800);
  check('...and Replant is still off', !!saw(/Replant off/, f1), saw(/Replant/, f1) || '');
  await cmd('/farming replant on', 400);
}

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

async function expirePhase() {
  await kitchen();
  await withResources(s => s.replace(/(hearty_stew:[\s\S]*?minutes: )10/, '$11'), async () => {
    await setLevel(1, 'Survival');
    await sleep(800);
    const base = hud();
    await cmd(`/cerb cook give ${NAME} hearty_stew common 1`, 600);
    await eatOne('Hearty Stew');
    await sleep(1500);
    const fed = hud();
    const t = Date.now();
    await sleep(60_500);
    const done = hud();
    check('A one-minute stew: max health up while it runs, back when it wears off (and the HUD says so)', fed.max > base.max && done.max === base.max
      && hudSince(t).some(h => /wore off/.test(h)), `${base.max} → ${fed.max} → ${done.max}`);
  });
}

async function menuPhase() {
  const a = await cmd('/cooking', 800);
  const line = saw(/meals cooked \d+ of \d+/, a) || '';
  const n = (line.match(/cooked (\d+) of/) || [])[1];
  await cmd('/menu', 1500);
  const find = re => { for (let i = 0; i < 54; i++) if (re.test(nameOf(bot.currentWindow && bot.currentWindow.slots[i]))) return i; return -1; };
  let tile = '(none)', header = '(none)', coll = '(none)';
  try {
    await bot.clickWindow(19, 0, 0); await sleep(1000);
    await bot.clickWindow(24, 0, 0); await sleep(1000);
    const c = find(/^Cooking/);
    if (c >= 0) {
      await bot.clickWindow(c, 0, 0); await sleep(1200);
      const ti = find(/^Cookbook: /);
      if (ti >= 0) {
        tile = nameOf(bot.currentWindow.slots[ti]);
        await bot.clickWindow(ti, 0, 0); await sleep(1200);
        const h = bot.currentWindow && bot.currentWindow.slots[4];
        header = h ? nameOf(h) + ' | ' + loreOf(h) : '(none)';
      }
    }
  } catch (e) { tile = '(' + e.message + ')'; }
  if (bot.currentWindow) bot.closeWindow(bot.currentWindow);
  await sleep(400);
  check('The Cooking page\'s Cookbook tile counts what /cooking does', n != null && new RegExp(`^Cookbook: ${n} of 6`).test(tile), `${tile} / ${line}`);
  check('...and opens the cookbook with the tier you cook at', /Cookbook/.test(header) && /You cook/.test(header), header.slice(0, 160));
  await cmd('/menu', 1500);
  try {
    const c = find(/^Collections/);
    if (c >= 0) { await bot.clickWindow(c, 0, 0); await sleep(1200); const e = find(/^Meals/); if (e >= 0) coll = nameOf(bot.currentWindow.slots[e]) + ' | ' + loreOf(bot.currentWindow.slots[e]); }
  } catch (e) { coll = '(' + e.message + ')'; }
  if (bot.currentWindow) bot.closeWindow(bot.currentWindow);
  await sleep(400);
  check('Collections has Meals with the same count', n != null && new RegExp(`${n} of 6`).test(coll), coll.slice(0, 140));
}

(async () => {
  await connect();
  await sleep(3000);
  const phases = { furnace: furnacePhase, campfire: campfirePhase, craft: craftPhase, meals: mealsPhase, eat: eatPhase, persist: persistPhase, expire: expirePhase, menu: menuPhase,
    'restart-set': restartSetPhase, 'restart-check': restartCheckPhase };
  const run = phaseArg === 'all' ? ['furnace', 'campfire', 'craft', 'meals', 'eat', 'persist', 'menu'] : phaseArg.split(',');
  for (const p of run) {
    if (!phases[p]) { console.log('unknown phase ' + p); continue; }
    console.log(`\n── ${p} ──`);
    try { await phases[p](); } catch (e) { check(`${p} ran to the end`, false, e.stack.split('\n').slice(0, 2).join(' ')); }
  }
  if (phaseArg === 'restart-set') { // the server stopped with the bot on it: nothing to tidy
    console.log(`\n${results.filter(Boolean).length}/${results.length} passed`);
    process.exit(failed.length ? 1 : 0);
  }
  if (bot.currentWindow) bot.closeWindow(bot.currentWindow);
  for (const s of ['Cooking', 'Farming']) await cmd(`/cerb skill ${NAME} ${s} level 1`, 250);
  await cmd(`/cerb cook clear ${NAME}`, 300);
  await cmd('/clear @s', 300);
  await cmd('/effect clear @s', 300);
  await cmd('/gamerule spawn_mobs true', 300);
  const passed = results.filter(Boolean).length;
  console.log(`\n${passed}/${results.length} passed`);
  if (failed.length) console.log('FAILED: ' + failed.join(' | '));
  bot.quit();
  setTimeout(() => process.exit(failed.length ? 1 : 0), 500);
})();
