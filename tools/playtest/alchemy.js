// Alchemy in game (docs/design/ALCHEMY.md). LOCAL SANDBOX servers only; the bot must be op.
// Usage: node alchemy.js [phase[,phase...]] [botName]
//   vanilla  - water bottles and nether wart brewed by the bot: 2 XP a bottle to the one who started it; a stand that
//              brews by itself (bottles and fuel set into it, nether wart fed by a hopper) pays nobody; creative pays
//              nothing, and the XP display says why
//   brews    - Awkward Potions and a poppy at Alchemy 9 make Mundane Potions (and the HUD says the level); at 10, three
//              Draughts of Mending (6 XP each, the first a discovery); a Fine poppy brews them Fine; at 20 a Sunpetal
//              brews the Elixir of Vigor a tier up; the bottles carry their name, tier and effect
//   drink    - an elixir adds what it says (Vitality: max health), replaces another elixir and refreshes itself; a meal
//              and an elixir count together; a draught heals the Cerberus health pool over its seconds, and a second is
//              refused while it heals (the bottle kept); Active Effects shows the elixir with its time
//   claim    - a click that puts nothing into a stand claims nothing: the window of a stand a hopper feeds kept open, an
//              empty slot clicked again and again while the hopper starts the brew: nobody started it, nobody is paid
//   restart1, restart2 - a brew a restart interrupts (the caller restarts the server between the two; RESTART_FILE names
//              a file for the stand's place): started by the bot, it completes after the restart as the bot's brew, paid
//   persist  - the elixir survives a relog with its time, and ends at death
//   pvp      - a draught drunk in a fight (PvP-tagged: CerbTarget, op, hits the bot for 24 s) waits, paused, not lost: its
//              healing comes once the tag ends (it used to give up within about one tag's length, its healing lost)
//   reload   - /cerb reload with the same brews leaves the server's brewing mixes alone; a brew still brews after it
//   menu     - Player Menu → Skills → Alchemy → Brews, and Collections → Brews, count what /alchemy counts
//   all      - every phase above but restart1 and restart2 (the default)
const mineflayer = require('mineflayer');
const { Vec3 } = require('vec3');
const phaseArg = process.argv[2] || 'all';
const NAME = process.argv[3] || 'CerbBrew';
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

const xpForLevel = l => Math.round(75 * Math.pow(Math.max(1, l), 1.85) + 40 * Math.max(1, l));
const totalXp = s => { let t = s.xp; for (let l = 1; l < s.level; l++) t += xpForLevel(l); return t; };
async function skill(name) {
  const at = await cmd(`/cerb skill ${NAME} ${name} xp 0`, 600);
  const m = (saw(/is now level \d+ \(\d+ XP\)/, at) || '').match(/level (\d+) \((\d+) XP\)/);
  return m ? { level: Number(m[1]), xp: Number(m[2]) } : { level: NaN, xp: NaN };
}
const alchXp = async () => totalXp(await skill('Alchemy'));
async function setLevel(level, s = 'Alchemy') { await cmd(`/cerb skill ${NAME} ${s} level ${level}`, 500); }
function hud() {
  for (let i = bars.length - 1; i >= 0; i--) {
    const m = bars[i].m.match(/([\d,]+)\/([\d,]+)♥/);
    if (m) return { hp: Number(m[1].replace(/,/g, '')), max: Number(m[2].replace(/,/g, '')) };
  }
  return { hp: NaN, max: NaN };
}

// ── the lab: a platform in the sky far from spawn ────────────────
let X = 0, Y = 0, Z = 0;
async function lab() {
  const sp = bot.spawnPoint || bot.entity.position;
  X = Math.floor(sp.x) - 3000 - (Date.now() % 20000) * 64; Y = 120; Z = Math.floor(sp.z) + 600;
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
  await cmd('/effect give @s saturation infinite 1 true', 300);
  await cmd(`/cerb alchemy clear ${NAME}`, 400);
  await cmd(`/cerb cook clear ${NAME}`, 400);
  await cmd(`/tp @s ${X + 0.5} ${Y} ${Z + 0.5} 0 20`, 500);
}
const at = (dx, dy, dz) => new Vec3(X + dx, Y + dy, Z + dz);
async function setblock(dx, dy, dz, b, wait = 400) { await cmd(`/setblock ${X + dx} ${Y + dy} ${Z + dz} ${b}`, wait); }
async function give(item, n = 1, wait = 500) { await cmd(`/give @s ${item} ${n}`, wait); }
const loreOf = it => (it && it.customLore ? it.customLore.map(l => {
  try { return require('prismarine-chat')(bot.registry).fromNotch(l).toString(); } catch (e) { return String(l); }
}).join(' | ') : '');
const nameOf = it => {
  if (!it || !it.customName) return '';
  try { return require('prismarine-chat')(bot.registry).fromNotch(it.customName).toString(); } catch (e) { return String(it.customName); }
};
async function click(slot, button = 0, mode = 0) { try { await bot.clickWindow(slot, button, mode); } catch (e) { /* the server decides */ } await sleep(220); }
const findIn = (w, pred) => { for (let i = w.inventoryStart; i < w.inventoryEnd; i++) if (w.slots[i] && pred(w.slots[i])) return i; return -1; };
const AWKWARD = 'potion[potion_contents={potion:"minecraft:awkward"}]';
const WATER = 'potion[potion_contents={potion:"minecraft:water"}]';
const fine = '[custom_data={PublicBukkitValues:{"cerberus:crop_quality":"fine"}}]';

/** Open the stand at (2, 0, 2) once the bot's own copy of the world has it (a fresh lab's chunks can lag), with one retry. */
async function openStand() {
  for (let i = 0; i < 40 && (bot.blockAt(at(2, 0, 2)) || {}).name !== 'brewing_stand'; i++) await sleep(150);
  await sleep(300);
  await bot.lookAt(at(2, 0, 2).offset(0.5, 0.5, 0.5), true);
  try { return await bot.openBlock(bot.blockAt(at(2, 0, 2))); } catch (e) {
    note('opening the stand again: ' + e.message);
    await sleep(1000);
    return await bot.openBlock(bot.blockAt(at(2, 0, 2)));
  }
}

/**
 * Brew at a fresh stand: three bottles of {@code bottle}, blaze powder, then {@code reagent} (a /give or /cerb command's
 * argument); all shift-clicked in by the bot, as a player does. Returns the three bottles as they came out.
 */
async function brew(bottle, reagentCmd, { wait = 23000 } = {}) {
  const bs = at(2, 0, 2);
  await cmd('/clear @s', 300);
  await setblock(2, 0, 2, 'air', 300);
  await setblock(2, 0, 2, 'brewing_stand', 600);
  for (let i = 0; i < 3; i++) await give(bottle, 1, 300);
  await give('blaze_powder', 1, 400);
  await cmd(reagentCmd, 600);
  const out = [];
  try {
    const w = await openStand();
    await sleep(500);
    for (let k = 0; k < 3; k++) { const i = findIn(w, it => it.name === 'potion'); if (i >= 0) await click(i, 0, 1); }
    const bp = findIn(w, it => it.name === 'blaze_powder'); if (bp >= 0) await click(bp, 0, 1);
    const rg = findIn(w, it => it.name !== 'potion' && it.name !== 'blaze_powder'); if (rg >= 0) await click(rg, 0, 1);
    await sleep(wait);
    for (let i = 0; i < 3; i++) { const it = w.slots[i]; out.push(it ? { name: it.name, custom: nameOf(it), lore: loreOf(it) } : null); }
    bot.closeWindow(w);
  } catch (e) { note('brewing: ' + e.message); }
  await sleep(500);
  return out;
}

async function drink(name) {
  const it = bot.inventory.items().find(i => nameOf(i) === name || nameOf(i).endsWith(name));
  if (!it) { note('no ' + name + ' to drink'); return false; }
  try { await bot.equip(it, 'hand'); } catch (e) { note('drink ' + name + ': ' + e.message); return false; }
  const before = bot.inventory.items().filter(i => nameOf(i) === nameOf(it)).length;
  bot.activateItem();
  await sleep(2100);
  bot.deactivateItem();
  await sleep(500);
  const after = bot.inventory.items().filter(i => nameOf(i) === nameOf(it)).length;
  return after === before - 1;
}

// ════════════════════════════════════════════════════════════════
async function vanillaPhase() {
  await lab();
  await setLevel(1);
  let x0 = await alchXp();
  let out = await brew(WATER, '/give @s nether_wart 1');
  let g = (await alchXp()) - x0;
  check('Three water bottles and nether wart, started by the bot: 2 XP a bottle (6)', g === 6 && out.filter(o => o && o.name === 'potion').length === 3, `+${g}`);
  // A stand that brews by itself: the bot puts the bottles and fuel in and walks away; the wart comes from a hopper
  // above after the click's moment has passed, so nobody started it. (This Folia build has no /data or /item command.)
  await cmd('/clear @s', 300);
  await setblock(2, 1, 2, 'air', 300);
  await setblock(2, 0, 2, 'air', 300);
  await setblock(2, 0, 2, 'brewing_stand', 600);
  for (let i = 0; i < 2; i++) await give(WATER, 1, 300);
  await give('blaze_powder', 1, 400);
  try {
    const w = await openStand();
    await sleep(500);
    for (let k = 0; k < 2; k++) { const i = findIn(w, it => it.name === 'potion'); if (i >= 0) await click(i, 0, 1); }
    const bp = findIn(w, it => it.name === 'blaze_powder'); if (bp >= 0) await click(bp, 0, 1);
    bot.closeWindow(w);
  } catch (e) { note('filling the stand: ' + e.message); }
  x0 = await alchXp();
  await sleep(3000); // longer than a click arms a stand for (1.5 s)
  await setblock(2, 1, 2, 'hopper[facing=down]{Items:[{Slot:0b,id:"minecraft:nether_wart",count:1}]}', 600);
  await sleep(23000);
  g = (await alchXp()) - x0;
  const st = await cmd('/cerb alchemy stats', 600);
  check('A stand a hopper fed: it brews, and pays nobody', g === 0 && /brews nobody started [1-9]/.test(saw(/\[Alchemy\]/, st) || ''), `+${g}; ${saw(/\[Alchemy\]/, st)}`);
  await setblock(2, 1, 2, 'air', 300);
  // Creative: brews, pays nothing.
  await cmd('/gamemode creative', 400);
  x0 = await alchXp();
  const t = Date.now();
  out = await brew(WATER, '/give @s nether_wart 1');
  g = (await alchXp()) - x0;
  check('Creative: the brew completes, no XP, and the XP display says why', g === 0 && hudSince(t).some(h => /creative/i.test(h)),
    `+${g}; ${JSON.stringify(hudSince(t).slice(-3))}`);
  await cmd('/gamemode survival', 400);
}

async function brewsPhase() {
  await lab();
  await cmd(`/cerb alchemy brews ${NAME} reset`, 500);
  await setLevel(9);
  let t = Date.now();
  let out = await brew(AWKWARD, '/give @s poppy 1');
  check('Alchemy 9, Awkward Potions and a poppy: Mundane Potions, and the HUD says the level', out.every(o => o && o.name === 'potion' && !o.custom)
    && hudSince(t).some(h => /needs Alchemy 10/.test(h)), `${JSON.stringify(out.map(o => o && o.custom))}; ${JSON.stringify(hudSince(t).slice(-2))}`);
  await setLevel(10);
  const x0 = await alchXp();
  const c0 = chat.length;
  out = await brew(AWKWARD, '/give @s poppy 1');
  const g = (await alchXp()) - x0;
  check('Alchemy 10: three Draughts of Mending (Common)', out.every(o => o && /Draught of Mending/.test(o.custom)) && out.every(o => /Draught · Common/.test(o.lore)),
    JSON.stringify(out.map(o => o && (o.custom + ' / ' + o.lore))));
  check('...6 XP a bottle (18), and the first brew is announced', g === 18 && chat.slice(c0).some(m => /First brew: Draught of Mending/.test(m)), `+${g}`);
  check('...its lore says what it does', out.every(o => /Heals 25% of max health over 5 s/.test(o.lore)), out[0] && out[0].lore);
  out = await brew(AWKWARD, `/give @s poppy${fine} 1`);
  check('A Fine poppy at Alchemy 10: Fine draughts (a tier up)', out.every(o => o && /Draught · Fine/.test(o.lore) && /Heals 30%/.test(o.lore)),
    JSON.stringify(out.map(o => o && o.lore)));
  await setLevel(20);
  out = await brew(AWKWARD, `/cerb herb rare ${NAME} sunpetal 1`);
  check('Alchemy 20 and a Sunpetal: Elixirs of Vigor a tier up (Fine, Vitality +9.6)', out.every(o => o && /Elixir of Vigor/.test(o.custom) && /Elixir · Fine/.test(o.lore)
    && /Vitality \+9\.6/.test(o.lore)), JSON.stringify(out.map(o => o && (o.custom + ' / ' + o.lore))));
  out = await brew(AWKWARD, '/give @s dandelion 1');
  check('...with a plain dandelion: Common', out.every(o => o && /Elixir · Common/.test(o.lore) && /Vitality \+8/.test(o.lore)), JSON.stringify(out.map(o => o && o.lore)));
  // A rare reagent's own quality lifts the brew it makes, as a herb's does (Herbalism rolls theirs the same way).
  await setLevel(50);
  out = await brew(AWKWARD, `/cerb herb rare ${NAME} bloodroot 1 fine`);
  check('Alchemy 50 (Superior) and a Fine Bloodroot: Bloodroot Draughts a tier up (Masterwork)', out.every(o => o && /Bloodroot Draught/.test(o.custom)
    && /Draught · Masterwork/.test(o.lore)), JSON.stringify(out.map(o => o && (o.custom + ' / ' + o.lore))));
  out = await brew(AWKWARD, `/cerb herb rare ${NAME} bloodroot 1`);
  check('...a Common Bloodroot: Superior, the level\'s own tier', out.every(o => o && /Draught · Superior/.test(o.lore)), JSON.stringify(out.map(o => o && o.lore)));
  await setLevel(1);
}

/** A stand at (2, 0, 2) the bot fills by clicks: {@code n} bottles of {@code bottle} and blaze powder (and a reagent, if given). */
async function fillStand(bottle, n, reagentCmd) {
  await cmd('/clear @s', 300);
  await setblock(2, 1, 2, 'air', 300);
  await setblock(2, 0, 2, 'air', 300);
  await setblock(2, 0, 2, 'brewing_stand', 600);
  for (let i = 0; i < n; i++) await give(bottle, 1, 300);
  await give('blaze_powder', 1, 400);
  if (reagentCmd) await cmd(reagentCmd, 600);
  const w = await openStand();
  await sleep(500);
  for (let k = 0; k < n; k++) { const i = findIn(w, it => it.name === 'potion'); if (i >= 0) await click(i, 0, 1); }
  const bp = findIn(w, it => it.name === 'blaze_powder'); if (bp >= 0) await click(bp, 0, 1);
  if (reagentCmd) { const rg = findIn(w, it => it.name !== 'potion' && it.name !== 'blaze_powder'); if (rg >= 0) await click(rg, 0, 1); }
  return w;
}
const statOf = (re, line) => +(((line || '').match(re) || [0, 0])[1]);

async function claimPhase() {
  // Until 2026-10-28 any click in a stand's window armed it, so a hopper's brew became whoever clicked there last.
  await lab();
  await setLevel(1);
  const st0 = saw(/\[Alchemy\]/, await cmd('/cerb alchemy stats', 600));
  const x0 = await alchXp();
  let clicks = 0, out = [];
  try {
    const w = await fillStand(WATER, 3);
    await sleep(3000); // the bottles' and the fuel's own clicks have passed
    let empty = -1;
    for (let i = w.inventoryStart; i < w.inventoryEnd; i++) if (!w.slots[i]) { empty = i; break; }
    bot.chat(`/setblock ${X + 2} ${Y + 1} ${Z + 2} hopper[facing=down]{Items:[{Slot:0b,id:"minecraft:nether_wart",count:1}]}`);
    const t0 = Date.now();
    while (Date.now() - t0 < 24000) { if (empty >= 0) { await click(empty, 0, 0); clicks++; } await sleep(300); }
    for (let i = 0; i < 3; i++) { const it = w.slots[i]; out.push(it ? it.name + (nameOf(it) ? ':' + nameOf(it) : '') : null); }
    bot.closeWindow(w);
  } catch (e) { note('claim: ' + e.message); }
  const g = (await alchXp()) - x0;
  const st = saw(/\[Alchemy\]/, await cmd('/cerb alchemy stats', 600));
  const nobody = statOf(/brews nobody started (\d+)/, st) - statOf(/brews nobody started (\d+)/, st0);
  const vanilla = statOf(/vanilla bottles (\d+)/, st) - statOf(/vanilla bottles (\d+)/, st0);
  check('A stand a hopper feeds, its window kept open and an empty slot clicked every half second: the brew is nobody\'s (no XP)',
    clicks >= 20 && vanilla === 3 && nobody === 1 && g === 0, `${clicks} clicks; brewed ${vanilla}, nobody's ${nobody}; +${g}; ${JSON.stringify(out)}`);
  await setblock(2, 1, 2, 'air', 300);
}

async function reloadPhase() {
  // A reload that adds or removes no brew leaves the server's list of brewing mixes alone (stands read it on their own
  // threads); a brew still brews after it.
  const before = statOf(/brewing mixes registered (\d+)x/, saw(/\[Alchemy\]/, await cmd('/cerb alchemy stats', 600)));
  await cmd('/cerb reload', 3000);
  const after = statOf(/brewing mixes registered (\d+)x/, saw(/\[Alchemy\]/, await cmd('/cerb alchemy stats', 600)));
  check('/cerb reload with the same brews: the brewing mixes are not registered again', before >= 1 && after === before, `${before}x -> ${after}x`);
  await lab();
  await setLevel(10);
  const out = await brew(AWKWARD, '/give @s poppy 1');
  check('...and a brew after the reload: Draughts of Mending', out.every(o => o && /Draught of Mending/.test(o.custom)), JSON.stringify(out.map(o => o && o.custom)));
  await setLevel(1);
}

async function restart1Phase() {
  const file = process.env.RESTART_FILE;
  if (!file) { check('restart1 needs RESTART_FILE', false); return; }
  await lab();
  await setLevel(10);
  await cmd(`/cerb alchemy brews ${NAME} reset`, 500);
  const x0 = await alchXp();
  try {
    const w = await fillStand(AWKWARD, 3, '/give @s poppy 1');
    await sleep(2500);
    bot.closeWindow(w);
  } catch (e) { note('restart1: ' + e.message); }
  require('fs').writeFileSync(file, JSON.stringify({ X, Y, Z, x0, at: Date.now() }));
  note(`a Draught of Mending brewing at ${X + 2} ${Y} ${Z + 2}; the server restarts now`);
}

async function restart2Phase() {
  const file = process.env.RESTART_FILE;
  const saved = file && require('fs').existsSync(file) ? JSON.parse(require('fs').readFileSync(file, 'utf8')) : null;
  if (!saved) { check('restart2 needs restart1\'s RESTART_FILE', false); return; }
  ({ X, Y, Z } = saved);
  // The XP before the brew completes, read at once: the bot logs back in beside the stand, so it resumes right away with
  // what was left of its 20 s (restart1's own clean-up set Alchemy back to 1; the stand kept the level it started at).
  const x0 = await alchXp();
  await cmd(`/tp @s ${X + 0.5} ${Y} ${Z + 0.5} 0 20`, 2000);
  try { await bot.waitForChunksToLoad(); } catch (e) { /* the stand is near */ }
  await sleep(25000); // what was left of the brew's 20 s, the stand ticking again once its chunk is loaded
  const out = [];
  try {
    await cmd('/clear @s', 400);
    const w = await openStand();
    await sleep(1000);
    note('the stand: ' + JSON.stringify(w.slots.slice(0, 5).map(it => it && { name: it.name, count: it.count, custom: nameOf(it) })));
    for (let i = 0; i < 3; i++) if (w.slots[i]) await click(i, 0, 1); // the bottles into the inventory, read there
    bot.closeWindow(w);
    await sleep(500);
    for (const it of bot.inventory.items().filter(i => i.name === 'potion')) out.push({ name: it.name, custom: nameOf(it), lore: loreOf(it) });
  } catch (e) { note('restart2: ' + e.message); }
  const g = (await alchXp()) - x0;
  const st = saw(/\[Alchemy\]/, await cmd('/cerb alchemy stats', 600));
  check('A brew the restart interrupted completes as the starter\'s: three Draughts of Mending (not Mundane Potions)',
    out.length === 3 && out.every(o => o && /Draught of Mending/.test(o.custom)), JSON.stringify(out.map(o => o && (o.custom || o.name))));
  check('...and its starter is paid (6 XP a bottle), the stand having kept who started it', g === 18 && statOf(/resumed after a restart (\d+)/, st) === 1,
    `+${g}; ${st}`);
  await setLevel(1);
}

async function drinkPhase() {
  await lab();
  for (const s of ['Survival', 'Heavy_Armor_Training', 'First_Aid', 'Woodcutting', 'Farming', 'Cooking', 'Herbalism']) await setLevel(1, s);
  await sleep(1200);
  const base = hud();
  await cmd(`/cerb alchemy give ${NAME} vigor common 2`, 600);
  await cmd(`/cerb alchemy give ${NAME} stoneskin common 1`, 600);
  await cmd(`/cerb alchemy give ${NAME} mending common 2`, 600);
  let c0 = chat.length;
  check('An Elixir of Vigor is drunk', await drink('Elixir of Vigor'));
  await sleep(1200);
  const up = hud();
  check('...Vitality +8: max health x1.08', Math.abs(up.max - Math.round(base.max * 1.08)) <= 1, `${base.max} → ${up.max}`);
  check('...and chat says what it does and how long', chat.slice(c0).some(m => /Elixir of Vigor.*Vitality \+8.*8 min/.test(m)));
  // A meal and an elixir together.
  await cmd(`/cerb cook give ${NAME} hearty_stew common 1`, 600);
  const it = bot.inventory.items().find(i => nameOf(i) === 'Hearty Stew');
  if (it) { await bot.equip(it, 'hand'); bot.activateItem(); await sleep(2300); bot.deactivateItem(); await sleep(1200); }
  const both = hud();
  check('A Hearty Stew beside it: both count (Vitality +14: max health x1.14)', Math.abs(both.max - Math.round(base.max * 1.14)) <= 1, `${base.max} → ${both.max}`);
  // Replace and refresh.
  let t = Date.now();
  await drink('Elixir of Stoneskin');
  await sleep(800);
  check('An Elixir of Stoneskin replaces it (and says so): the elixir\'s Vitality gone, the meal\'s stays', hudSince(t).some(h => /replaces Elixir of Vigor/.test(h))
    && Math.abs(hud().max - Math.round(base.max * 1.06)) <= 1, `${JSON.stringify(hudSince(t).slice(-1))}; max ${hud().max}`);
  await cmd(`/cerb alchemy give ${NAME} stoneskin common 1`, 600);
  t = Date.now();
  await drink('Elixir of Stoneskin');
  check('The same elixir again refreshes its time', hudSince(t).some(h => /time refreshed/.test(h)), JSON.stringify(hudSince(t).slice(-1)));
  // Active Effects: the elixir with its time, after the meal.
  await cmd('/menu', 1500);
  let head = '';
  try { const h = bot.currentWindow && bot.currentWindow.slots[4]; head = h ? loreOf(h) : ''; } catch (e) { /* none */ }
  if (bot.currentWindow) bot.closeWindow(bot.currentWindow);
  check('The hub\'s head lists the meal, then the elixir with its time left', /Hearty Stew.*Elixir of Stoneskin — \d+m \d+s remaining/.test(head), head.slice(0, 200));
  // A draught heals the pool; a second is refused while it heals.
  await cmd('/effect clear @s resistance', 300);
  await cmd('/damage @s 10 minecraft:generic', 800);
  await cmd('/effect give @s resistance infinite 4 true', 300);
  await sleep(600);
  const hurt = hud();
  await drink('Draught of Mending');
  await sleep(400);
  t = Date.now();
  const refused = !(await drink('Draught of Mending'));
  check('A second draught while the first heals: refused, the bottle kept, and the HUD says how long', refused
    && bot.inventory.items().some(i => nameOf(i) === 'Draught of Mending') && hudSince(t).some(h => /draught is still working/.test(h)),
    JSON.stringify(hudSince(t).slice(-1)));
  await sleep(5000);
  const healed = hud();
  check('The draught healed the Cerberus health pool over its seconds', hurt.hp < hurt.max && healed.hp > hurt.hp && healed.hp <= healed.max,
    `${hurt.hp}/${hurt.max} → ${healed.hp}`);
  check('...and the bottle kept then can be drunk now', await drink('Draught of Mending'));
}

async function pvpPhase() {
  await lab();
  await cmd(`/cerb alchemy give ${NAME} mending common 1`, 600);
  const other = mineflayer.createBot({ host: '127.0.0.1', port: Number(process.env.MC_PORT || 25565), username: 'CerbTarget', version: '1.21.11', auth: 'offline' });
  await new Promise((res, rej) => { other.once('spawn', res); setTimeout(() => rej(new Error('CerbTarget did not spawn')), 30000); });
  await sleep(1500);
  await cmd(`/tp CerbTarget ${X + 0.5} ${Y} ${Z + 2.5} 180 0`, 800);
  await cmd('/gamemode survival CerbTarget', 400);
  await cmd(`/tp @s ${X + 0.5} ${Y} ${Z + 0.5} 0 0`, 600);
  await cmd('/effect clear @s resistance', 300); // the lab's Resistance would take the hits
  await cmd('/damage @s 30 minecraft:generic', 1000);
  const punch = async () => {
    const e = other.players[NAME] && other.players[NAME].entity;
    if (!e) return false;
    await other.lookAt(e.position.offset(0, 1.5, 0), true);
    other.attack(e);
    await sleep(300);
    return true;
  };
  const tags = [];
  const t0 = Date.now();
  tags.push(await punch());
  const drank = await drink('Draught of Mending');
  const start = hud();
  for (let i = 0; i < 6; i++) { await sleep(4000); tags.push(await punch()); }
  const lastHit = Date.now();
  const endFight = hud();
  check('The draught is drunk in a fight (a player keeps hitting the bot for 24 s)', drank && tags.every(Boolean), `drank ${drank}, hits ${tags.filter(Boolean).length}/7`);
  check('...while the fight lasts it heals nothing (paused)', endFight.hp <= start.hp + 2, `${start.hp} → ${endFight.hp} of ${endFight.max} over ${Math.round((lastHit - t0) / 1000)} s`);
  // Room for the heal to show: natural regeneration refills part of the pool meanwhile, so 40 more damage just before the
  // tag ends (not from a player: the tag isn't renewed). Then the draught's own state and the pool over the next seconds.
  await sleep(Math.max(0, lastHit + 13000 - Date.now()));
  await cmd('/damage @s 40 minecraft:generic', 300);
  await sleep(Math.max(0, lastHit + 15500 - Date.now())); // the tag ends 15 s after the last hit
  const tagEnd = hud();
  const at = await cmd('/alchemy', 900);
  const healing = chat.slice(at).some(m => /A draught is healing/.test(m));
  await sleep(5600);
  const after = hud();
  check('...once the tag ends, it heals: "A draught is healing", and its quarter of max health on top of regeneration',
    healing && after.hp - tagEnd.hp >= 0.3 * after.max,
    `${healing ? 'healing' : 'no draught running'}; ${tagEnd.hp} → ${after.hp} of ${after.max} (+${Math.round((after.hp - tagEnd.hp) / after.max * 100)}%; regeneration alone gave +12% in the control)`);
  other.quit();
  await cmd('/effect give @s resistance infinite 4 true', 300);
  await cmd(`/cerb heal ${NAME}`, 400);
}

async function persistPhase() {
  await lab();
  await cmd(`/cerb alchemy give ${NAME} insight superior 1`, 600);
  await drink('Philter of Insight');
  const a0 = await cmd('/alchemy', 800);
  const before = saw(/Philter of Insight/, a0) || '';
  bot.quit();
  await sleep(4000);
  await connect();
  await sleep(3000);
  const a1 = await cmd('/alchemy', 800);
  const line = saw(/Philter of Insight|No elixir running/, a1) || '';
  check('The running elixir survives a relog with its time', /Philter of Insight.*Superior.*left/.test(line), `${before.slice(-50)} | ${line.slice(-60)}`);
  await cmd('/kill @s', 1500);
  try { bot.respawn && bot.respawn(); } catch (e) { /* auto */ }
  await sleep(3000);
  const a2 = await cmd('/alchemy', 800);
  check('Death ends it', !!saw(/No elixir running/, a2), saw(/elixir|Elixir|Philter/, a2) || '');
}

async function menuPhase() {
  const find = re => { for (let i = 0; i < 54; i++) if (re.test(nameOf(bot.currentWindow && bot.currentWindow.slots[i]))) return i; return -1; };
  let tile = '(none)', coll = '(none)';
  await cmd('/menu', 1500);
  try {
    await bot.clickWindow(19, 0, 0); await sleep(1000); // Skills
    await bot.clickWindow(24, 0, 0); await sleep(1000); // Utility Skills
    let f = find(/^Alchemy/);
    for (let page = 0; f < 0 && page < 3; page++) {
      const next = find(/^Next page/);
      if (next < 0) break;
      await bot.clickWindow(next, 0, 0); await sleep(900);
      f = find(/^Alchemy/);
    }
    if (f >= 0) {
      await bot.clickWindow(f, 0, 0); await sleep(1200);
      const ti = find(/^Brews: /);
      if (ti >= 0) tile = nameOf(bot.currentWindow.slots[ti]);
    }
  } catch (e) { tile = '(' + e.message + ')'; }
  if (bot.currentWindow) bot.closeWindow(bot.currentWindow);
  await sleep(500);
  const at0 = await cmd('/alchemy', 800);
  const summary = saw(/brews made \d+ of \d+/, at0) || '';
  const n = (summary.match(/brews made (\d+) of/) || [])[1];
  check('The Alchemy page has a Brews tile with the count /alchemy gives', n != null && new RegExp(`^Brews: ${n} of 8 brewed`).test(tile), `${tile} / ${summary}`);
  await cmd('/menu', 1500);
  try {
    const c = find(/^Collections/);
    if (c >= 0) {
      await bot.clickWindow(c, 0, 0); await sleep(1200);
      const e = find(/^Brews/);
      if (e >= 0) coll = nameOf(bot.currentWindow.slots[e]) + ' | ' + loreOf(bot.currentWindow.slots[e]);
    }
  } catch (e) { coll = '(' + e.message + ')'; }
  if (bot.currentWindow) bot.closeWindow(bot.currentWindow);
  await sleep(400);
  check('Collections has a Brews entry with the same count', n != null && new RegExp(`${n} of 8`).test(coll), coll.slice(0, 160));
}

(async () => {
  await connect();
  await sleep(3000);
  const phases = { vanilla: vanillaPhase, brews: brewsPhase, drink: drinkPhase, persist: persistPhase, menu: menuPhase, claim: claimPhase, pvp: pvpPhase,
    restart1: restart1Phase, restart2: restart2Phase, reload: reloadPhase };
  const run = phaseArg === 'all' ? ['vanilla', 'claim', 'brews', 'drink', 'pvp', 'persist', 'menu', 'reload'] : phaseArg.split(',');
  for (const p of run) {
    if (!phases[p]) { console.log('unknown phase ' + p); continue; }
    console.log(`\n── ${p} ──`);
    try { await phases[p](); } catch (e) { check(`${p} ran to the end`, false, e.stack.split('\n').slice(0, 2).join(' ')); }
  }
  await cmd(`/cerb skill ${NAME} Alchemy level 1`, 250);
  await cmd(`/cerb alchemy clear ${NAME}`, 300);
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
