// Skill progress persistence (docs/STATUS.md → reliability audit): what the database holds after XP gains, quits,
// rejoins, contention and failures, read straight from the database file (dblock.py), never trusted from the plugin.
// Sandbox only. MC_PORT; SERVER_DIR (default the main sandbox) for the database. The bots must be op (/cerb skill).
//
//   node persist.js contention [trials] [name]  another program holds the database's write lock while a bot gains XP and
//                                               quits: every save waits, then they run in some order. The stored XP
//                                               must be the last value the game showed, every trial.
//   node persist.js failure [name]              the lock is held past every retry (20 s) across the quit: nothing can be
//                                               written. After it lets go and the bot rejoins, it has its progress.
//   node persist.js rapid [bots] [name]         several bots gain XP quickly, quit at once and rejoin at once.
//   node persist.js backup [trials] [bots] [name]  /cerb backup (every online player's skills, one write after another) while
//                                               the last bot to join gains XP: its newer progress must survive the backup.
//   node persist.js joinact [name]              the database holds every read for 2.5 s, so the bot's load waits while it mines
//                                               coal, gathers a poppy and harvests wheat: that XP must arrive once loaded.
//   node persist.js joinquit [name]             the same, quitting before the load completes: nothing changes, the next
//                                               join loads the stored values.
//   node persist.js joinfail [name]             the database holds every read past the busy timeout: the load fails, the
//                                               bot is told and disconnected, nothing stored changes, a rejoin works.
//   node persist.js shutdown-set [name]         XP gains, then the caller stops the server right away (writes the expected
//                                               value to $OUT); shutdown-check [name] after the restart compares.
const mineflayer = require('mineflayer');
const { spawn, execFileSync } = require('child_process');
const fs = require('fs');
const path = require('path');

const phase = process.argv[2] || 'contention';
const PORT = Number(process.env.MC_PORT || 25565);
const SP = '/tmp/claude-0/-home-user-THE-CERB-PlUGIN/0abccdf5-ab39-5f77-aec2-ee72d6d35cbc/scratchpad';
const SERVER_DIR = process.env.SERVER_DIR || path.join(SP, 'server');
const DB = path.join(SERVER_DIR, 'plugins/CerberusPlugin/cerberus.db');
const TOOL = path.join(__dirname, 'dblock.py');
const SKILL = 'Mining';
const sleep = ms => new Promise(r => setTimeout(r, ms));
const results = [];
const check = (name, ok, detail = '') => { results.push(ok); console.log(`${ok ? 'PASS' : 'FAIL'}  ${name}${detail ? '  (' + detail + ')' : ''}`); };

function connect(name) {
  return new Promise((resolve, reject) => {
    const bot = mineflayer.createBot({ host: '127.0.0.1', port: PORT, username: name, version: '1.21.11', auth: 'offline' });
    bot.chatLog = [];
    bot.on('messagestr', (m, pos) => { if (pos !== 'game_info') bot.chatLog.push(m); });
    bot.once('spawn', () => resolve(bot));
    bot.once('kicked', r => reject(new Error('kicked: ' + JSON.stringify(r))));
    bot.once('error', reject);
    setTimeout(() => reject(new Error('no spawn for ' + name)), 30000);
  });
}
function quit(bot) { return new Promise(r => { bot.once('end', r); bot.quit(); setTimeout(r, 3000); }); }

/** "/cerb skill <name> Mining xp <n>": the game's value after it, or null (not loaded, no answer). */
async function award(bot, name, amount, wait = 1500) {
  const at = bot.chatLog.length;
  bot.chat(`/cerb skill ${name} ${SKILL} xp ${amount}`);
  const t0 = Date.now();
  while (Date.now() - t0 < wait) {
    for (const m of bot.chatLog.slice(at)) {
      const r = /is now level (\d+) \((\d+) XP\)/.exec(m);
      if (r) return [Number(r[1]), Number(r[2])];
      if (/not loaded yet/.test(m)) return null;
    }
    await sleep(25);
  }
  return null;
}
/** Fire-and-forget award (several inside one save window). */
function awardNow(bot, name, amount) { bot.chat(`/cerb skill ${name} ${SKILL} xp ${amount}`); }
async function setLevel(bot, name, level) {
  const at = bot.chatLog.length;
  bot.chat(`/cerb skill ${name} ${SKILL} level ${level}`);
  for (let i = 0; i < 40; i++) {
    if (bot.chatLog.slice(at).some(m => /is now level/.test(m))) return true;
    await sleep(50);
  }
  return false;
}
/** Wait until the bot's skills are loaded (the command answers with a value). */
async function loaded(bot, name) {
  for (let i = 0; i < 60; i++) {
    const v = await award(bot, name, 0, 800);
    if (v) return v;
    await sleep(250);
  }
  return null;
}
function stored(name) {
  try { return JSON.parse(execFileSync('python3', [TOOL, 'read', DB, name, SKILL]).toString()); } catch (e) { return 'error: ' + e.message.split('\n')[0]; }
}
/**
 * Hold the database's write lock for {@code seconds} (dblock.py); resolves once it is held with {released}, a promise for
 * its release. (Resolving with the bare promise would make the caller wait for the release itself: a promise adopts a
 * promise it is resolved with.)
 */
function holdLock(seconds) {
  return new Promise((resolve, reject) => {
    const p = spawn('python3', [TOOL, 'lock', DB, String(seconds)]);
    let out = '';
    const released = new Promise(r => p.on('exit', r));
    p.stdout.on('data', d => { out += d; if (/LOCKED/.test(out)) resolve({ released }); });
    setTimeout(() => reject(new Error('the lock was not taken within 5 s')), 5000);
  });
}
const same = (a, b) => Array.isArray(a) && Array.isArray(b) && a[0] === b[0] && a[1] === b[1];

async function contention() {
  const trials = Number(process.argv[3] || 12), name = process.argv[4] || 'CerbSave';
  let rolledBack = 0, lost = 0;
  for (let t = 1; t <= trials; t++) {
    let bot = await connect(name);
    await loaded(bot, name);
    await setLevel(bot, name, 10);
    await sleep(1500); // its save lands and the save window passes
    const base = stored(name);
    const { released } = await holdLock(2.5);
    awardNow(bot, name, 40);              // a save starts at once and waits for the lock
    await sleep(300);
    const shown = await award(bot, name, 40, 800); // the next waits for the save window: kept for later
    await sleep(100);
    await quit(bot);                      // the quit's own save of the same progress
    await released;
    await sleep(3000);
    const after = stored(name);
    const ok = same(after, shown);
    if (!ok && Array.isArray(after) && Array.isArray(shown)) (after[0] < shown[0] || after[1] < shown[1]) ? rolledBack++ : lost++;
    console.log(`  trial ${t}: before ${JSON.stringify(base)}, the game showed ${JSON.stringify(shown)}, stored ${JSON.stringify(after)}${ok ? '' : '  <<<'}`);
  }
  check(`Contention: the stored XP is the last value the game showed in every trial (${trials})`, rolledBack === 0 && lost === 0,
    `${rolledBack} rolled back, ${lost} other`);
}

async function failure() {
  const name = process.argv[3] || 'CerbFail';
  let bot = await connect(name);
  await loaded(bot, name);
  await setLevel(bot, name, 10);
  await sleep(1500);
  const base = stored(name);
  const { released } = await holdLock(20);
  awardNow(bot, name, 40);
  await sleep(300);
  const shown = await award(bot, name, 40, 800);
  await sleep(100);
  await quit(bot);
  const during = stored(name);
  await released;
  await sleep(2000);
  bot = await connect(name);
  const back = await loaded(bot, name);
  await sleep(2500);
  const after = stored(name);
  await quit(bot);
  console.log(`  before ${JSON.stringify(base)}, the game showed ${JSON.stringify(shown)}, stored while locked ${JSON.stringify(during)}, after the rejoin the game shows ${JSON.stringify(back)}, stored ${JSON.stringify(after)}`);
  check('Failure: after the database lets go and the bot rejoins, it has the progress the game showed', same(back, shown), `${JSON.stringify(back)} vs ${JSON.stringify(shown)}`);
  check('...and the database holds it', same(after, shown), JSON.stringify(after));
}

async function rapid() {
  const n = Number(process.argv[3] || 6), base = process.argv[4] || 'CerbRap';
  const names = Array.from({ length: n }, (_, i) => base + i);
  const bots = [];
  for (const nm of names) { bots.push(await connect(nm)); await sleep(300); }
  await Promise.all(bots.map((b, i) => loaded(b, names[i])));
  await Promise.all(bots.map((b, i) => setLevel(b, names[i], 10)));
  await sleep(1500);
  // 20 gains each over about 2 s, the last one read back; then all quit at once and rejoin at once
  const shown = await Promise.all(bots.map(async (b, i) => {
    for (let k = 0; k < 19; k++) { awardNow(b, names[i], 7); await sleep(100); }
    return award(b, names[i], 7, 1500);
  }));
  await Promise.all(bots.map(quit));
  const again = [];
  for (const nm of names) { again.push(await connect(nm)); await sleep(150); }
  const back = await Promise.all(again.map((b, i) => loaded(b, names[i])));
  await sleep(2500);
  const after = names.map(stored);
  await Promise.all(again.map(quit));
  const bad = names.filter((nm, i) => !same(back[i], shown[i]) || !same(after[i], shown[i]));
  names.forEach((nm, i) => console.log(`  ${nm}: showed ${JSON.stringify(shown[i])}, after rejoin ${JSON.stringify(back[i])}, stored ${JSON.stringify(after[i])}`));
  check(`Rapid: ${n} bots gain XP fast, quit and rejoin at once: each has exactly what it showed, in game and stored`, bad.length === 0, bad.join(', ') || 'all equal');
}

async function shutdownSet() {
  const name = process.argv[3] || 'CerbDown';
  const bot = await connect(name);
  await loaded(bot, name);
  await setLevel(bot, name, 10);
  await sleep(1500);
  awardNow(bot, name, 30);
  await sleep(200);
  const shown = await award(bot, name, 30, 800); // inside the save window: its save is pending when the server stops
  // Class progress too: a Warrior rank that differs from run to run (a stale stored value can't pass)
  const rank = 2 + (Date.now() % 7);
  const at = bot.chatLog.length;
  bot.chat(`/cerb class ${name} warrior rank ${rank}`);
  let ranked = false;
  for (let i = 0; i < 40 && !ranked; i++) { await sleep(50); ranked = bot.chatLog.slice(at).some(m => new RegExp('Warrior is now rank ' + rank).test(m)); }
  fs.writeFileSync(process.env.OUT || path.join(SP, 'persist-shutdown.json'), JSON.stringify({ skills: shown, warrior: rank }));
  console.log(`  the game showed ${JSON.stringify(shown)} and Warrior rank ${rank}; stop the server now`);
  check('shutdown-set: the game showed the skill and the class rank', Array.isArray(shown) && ranked);
  // Stay online until the server stops (a quit first would be the quit's save, not shutdown's).
  await new Promise(r => { bot.once('end', r); setTimeout(r, 180000); });
}

async function shutdownCheck() {
  const name = process.argv[3] || 'CerbDown';
  const want = JSON.parse(fs.readFileSync(process.env.OUT || path.join(SP, 'persist-shutdown.json')).toString());
  const shown = want.skills;
  const after = stored(name);
  const bot = await connect(name);
  const back = await loaded(bot, name);
  let rank = null;
  for (let tries = 0; tries < 20 && rank === null; tries++) { // classes load a moment after skills
    const at = bot.chatLog.length;
    bot.chat(`/cerb class ${name} info`);
    await sleep(500);
    const line = bot.chatLog.slice(at).find(m => /Warrior: rank \d+/.test(m));
    if (line) rank = Number(/Warrior: rank (\d+)/.exec(line)[1]);
  }
  await quit(bot);
  console.log(`  before the stop: skills ${JSON.stringify(shown)}, Warrior rank ${want.warrior}; stored ${JSON.stringify(after)}; after the restart the game shows ${JSON.stringify(back)}, Warrior rank ${rank}`);
  check('Shutdown: the skill progress the game showed before the stop is stored and loads after the restart', same(after, shown) && same(back, shown));
  check('Shutdown: and the class rank', rank === want.warrior, `rank ${rank}, expected ${want.warrior}`);
}

async function backup() {
  const trials = Number(process.argv[3] || 8), n = Number(process.argv[4] || 6), base = process.argv[5] || 'CerbBak';
  const names = Array.from({ length: n }, (_, i) => base + i);
  const bots = [];
  for (const nm of names) { bots.push(await connect(nm)); await sleep(400); } // the target joins last: last in the backup
  await Promise.all(bots.map((b, i) => loaded(b, names[i])));
  const target = names[n - 1], tb = bots[n - 1];
  await setLevel(tb, target, 10);
  await sleep(1500);
  let rolledBack = 0;
  for (let t = 1; t <= trials; t++) {
    const before = stored(target);
    const { released } = await holdLock(1.0);  // the backup's first write waits, and so does everything after it
    console_('cerb backup');
    await sleep(150);
    const shown = await award(tb, target, 25, 1500); // newer than the backup's copy of the target's skills
    await released;
    await sleep(3000);
    const after = stored(target);
    const ok = same(after, shown);
    if (!ok) rolledBack++;
    console.log(`  trial ${t}: before ${JSON.stringify(before)}, the game showed ${JSON.stringify(shown)}, stored ${JSON.stringify(after)}${ok ? '' : '  <<<'}`);
    await sleep(1200);
  }
  await Promise.all(bots.map(quit));
  check(`Backup: the progress the game showed after a /cerb backup is what is stored, every trial (${trials})`, rolledBack === 0, `${rolledBack} rolled back`);
}
function console_(command) { execFileSync('tmux', ['send-keys', '-t', `=${process.env.MC_TMUX || 'mc'}:`, command, 'Enter']); }

// ── joining: XP earned while the skills load ─────────────────────
const { Vec3 } = require('vec3');
const AX = 600, AY = 100, AZ = 600;
function storedOf(name, skill) {
  try { return JSON.parse(execFileSync('python3', [TOOL, 'read', DB, name, skill]).toString()); } catch (e) { return null; }
}
/** The game's value for a skill right now ([level, xp]), or null while loading. */
async function valueOf(bot, name, skill) {
  const at = bot.chatLog.length;
  bot.chat(`/cerb skill ${name} ${skill} xp 0`);
  for (let i = 0; i < 40; i++) {
    for (const m of bot.chatLog.slice(at)) {
      const r = /is now level (\d+) \((\d+) XP\)/.exec(m);
      if (r) return [Number(r[1]), Number(r[2])];
      if (/not loaded yet/.test(m)) return null;
    }
    await sleep(50);
  }
  return null;
}
async function untilLoaded(bot, name, skill) {
  for (let i = 0; i < 80; i++) { const v = await valueOf(bot, name, skill); if (v) return v; await sleep(250); }
  return null;
}
function holdXLock(seconds) {
  return new Promise((resolve, reject) => {
    const p = spawn('python3', [TOOL, 'xlock', DB, String(seconds)]);
    let out = '';
    const released = new Promise(r => p.on('exit', r));
    p.stdout.on('data', d => { out += d; if (/LOCKED/.test(out)) resolve({ released }); });
    setTimeout(() => reject(new Error('the lock was not taken within 5 s')), 5000);
  });
}
/** A first session: the bot logs out on a stone floor at the arena, with a diamond pickaxe; then the blocks it will break. */
async function arenaSession(name) {
  const bot = await connect(name);
  await untilLoaded(bot, name, 'Mining');
  console_(`fill ${AX - 3} ${AY - 1} ${AZ - 3} ${AX + 3} ${AY + 4} ${AZ + 3} air`); await sleep(400);
  console_(`fill ${AX - 3} ${AY - 1} ${AZ - 3} ${AX + 3} ${AY - 1} ${AZ + 3} stone`); await sleep(400);
  console_(`tp ${name} ${AX + 0.5} ${AY} ${AZ + 0.5} 0 0`); await sleep(600);
  console_(`gamemode survival ${name}`); console_(`clear ${name}`); await sleep(300);
  console_(`give ${name} diamond_pickaxe`); await sleep(800);
  await quit(bot);
  console_(`setblock ${AX + 1} ${AY} ${AZ} coal_ore`); console_(`setblock ${AX - 1} ${AY} ${AZ} coal_ore`);
  console_(`setblock ${AX} ${AY - 1} ${AZ + 2} grass_block`); console_(`setblock ${AX} ${AY} ${AZ + 2} poppy`);
  console_(`setblock ${AX} ${AY - 1} ${AZ - 2} farmland`); console_(`setblock ${AX} ${AY} ${AZ - 2} wheat[age=7]`);
  await sleep(800);
}
/** Break the four blocks (2 coal with the pickaxe, a poppy, ripe wheat) as fast as the bot can, once its world arrived. */
async function breakAll(bot) {
  const spots = [[AX + 1, AY, AZ], [AX - 1, AY, AZ], [AX, AY, AZ + 2], [AX, AY, AZ - 2]].map(v => new Vec3(...v));
  for (let i = 0; i < 60 && spots.some(v => !bot.blockAt(v)); i++) await sleep(50); // the chunk comes after the spawn
  for (let i = 0; i < 40 && !bot.inventory.items().some(it => it.name === 'diamond_pickaxe'); i++) await sleep(50);
  const pick = bot.inventory.items().find(i => i.name === 'diamond_pickaxe');
  if (pick) await bot.equip(pick, 'hand');
  let broke = 0;
  for (const v of spots) {
    const b = bot.blockAt(v);
    if (!b || b.name === 'air') continue;
    try { await bot.dig(b, true); broke++; } catch (e) { /* counted below */ }
  }
  return broke;
}
/**
 * Make the next load wait, as a busy server's would: two other bots quit while the database refuses writes, so their quit
 * saves hold the thread loads run on (Cerb-DB-Session before 2026-11, the skill store's writer since) for several
 * seconds. Reads still work, so the waiting load itself never fails. Resolves {released} once they have quit.
 */
async function busyDatabase(name, seconds) {
  const helpers = [name + 'B', name + 'C'];
  const hb = [];
  for (const h of helpers) { const b = await connect(h); await untilLoaded(b, h, 'Mining'); hb.push(b); }
  const lock = await holdLock(seconds);
  for (let i = 0; i < hb.length; i++) { awardNow(hb[i], helpers[i], 1); }
  await sleep(300);
  for (const b of hb) await quit(b);
  return lock;
}
const SKILLS3 = ['Mining', 'Herbalism', 'Farming'];

async function joinact() {
  const name = process.argv[3] || 'CerbJoin';
  await arenaSession(name);
  const before = Object.fromEntries(SKILLS3.map(s => [s, storedOf(name, s)]));
  const { released } = await busyDatabase(name, 7);
  const bot = await connect(name);
  const broke = await breakAll(bot);
  const during = await valueOf(bot, name, 'Mining'); // null: still loading when the blocks broke
  await released;
  await untilLoaded(bot, name, 'Mining');
  await sleep(500);
  const after = {};
  for (const s of SKILLS3) after[s] = await valueOf(bot, name, s);
  await quit(bot);
  const gain = s => Array.isArray(after[s]) && Array.isArray(before[s]) ? (after[s][0] - before[s][0]) * 100000 + after[s][1] - before[s][1] : NaN;
  console.log(`  broke ${broke} blocks while ${during ? 'ALREADY LOADED (inconclusive)' : 'loading'}; before ${JSON.stringify(before)}, after ${JSON.stringify(after)}`);
  check('Joining: the bot broke the four blocks while its skills were still loading', broke === 4 && during === null, `broke ${broke}, loaded then: ${JSON.stringify(during)}`);
  check('...and the XP it earned meanwhile arrived once they loaded (Mining, Herbalism, Farming)', SKILLS3.every(s => gain(s) > 0),
    SKILLS3.map(s => `${s} +${gain(s)}`).join(', '));
}

async function joinquit() {
  const name = process.argv[3] || 'CerbJoinQ';
  await arenaSession(name);
  const before = Object.fromEntries(SKILLS3.map(s => [s, storedOf(name, s)]));
  const { released } = await busyDatabase(name, 7);
  let bot = await connect(name);
  const broke = await breakAll(bot);
  const during = await valueOf(bot, name, 'Mining');
  if (during) console.log('  (inconclusive: loaded before it quit)');
  await quit(bot); // before the load completes
  await released;
  await sleep(2500);
  const storedAfter = Object.fromEntries(SKILLS3.map(s => [s, storedOf(name, s)]));
  bot = await connect(name);
  const back = {};
  await untilLoaded(bot, name, 'Mining');
  for (const s of SKILLS3) back[s] = await valueOf(bot, name, s);
  await quit(bot);
  console.log(`  broke ${broke} while loading, then quit; stored before ${JSON.stringify(before)}, after ${JSON.stringify(storedAfter)}, loaded at the rejoin ${JSON.stringify(back)}`);
  check('Leaving before the load completes changes nothing stored, and the rejoin loads it', SKILLS3.every(s => same(storedAfter[s], before[s]) && same(back[s], before[s])));
}

async function joinfail() {
  const name = process.argv[3] || 'CerbJoinF';
  const pre = await connect(name);
  await untilLoaded(pre, name, 'Mining');
  await quit(pre);
  const before = storedOf(name, 'Mining');
  const { released } = await holdXLock(15);
  const bot = mineflayer.createBot({ host: '127.0.0.1', port: PORT, username: name, version: '1.21.11', auth: 'offline' });
  const kicked = await new Promise(r => { bot.once('kicked', reason => r(JSON.stringify(reason))); bot.once('end', () => r('ended')); setTimeout(() => r(null), 30000); });
  await released;
  await sleep(1500);
  const after = storedOf(name, 'Mining');
  const again = await connect(name);
  const back = await untilLoaded(again, name, 'Mining');
  await quit(again);
  console.log(`  kicked: ${kicked}; stored before ${JSON.stringify(before)}, after ${JSON.stringify(after)}; the rejoin loads ${JSON.stringify(back)}`);
  check('A load that fails disconnects the player and says why', /could not load your saved data/i.test(kicked || ''), kicked || 'not kicked');
  check('...nothing stored changes, and a rejoin once the database answers loads it', same(after, before) && same(back, before));
}

const PHASES = { contention, failure, rapid, backup, joinact, joinquit, joinfail, 'shutdown-set': shutdownSet, 'shutdown-check': shutdownCheck };
(async () => {
  try { await PHASES[phase](); } catch (e) { check('phase ran without errors', false, e.stack); }
  const failed = results.filter(r => !r).length;
  console.log(`\n${results.length - failed}/${results.length} passed`);
  process.exit(failed ? 1 : 0);
})();
