// Skill progress persistence (docs/STATUS.md → reliability audit): what the database holds after XP gains, quits,
// rejoins, contention and failures, read straight from the database file (dblock.py), never trusted from the plugin.
// Sandbox only. MC_PORT; SERVER_DIR (required: the server on MC_PORT) for the database. The bots must be op (/cerb skill).
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
//   node persist.js recovery-check [name]       after shutdown-set, a stop and a start both under the database's write
//                                               lock: the join continues from the recovery file; stored once the lock goes.
//   Exact XP while loading (item 10): what a bot does while its load waits is paid once loaded, in order, at its restored
//   level: exactly what a loaded session gets for the same from the same start (each phase runs that control too).
//   node persist.js exact-new [name]            a new player (level 1): coal, a poppy, wheat (passes on every build)
//   node persist.js exact-veteran [name]        level 60: twelve blocks, each paying a quarter (weak-resources)
//   node persist.js exact-levelup [name]        Mining 20 two XP short of 21: two coal, the second paid at 21
//   node persist.js exact-rapid [name]          a session breaks blocks and quits before its load; the rejoin does the same
//                                               and loads: only the rejoin's are paid, once
//   node persist.js exact-fail [name]           the load fails while the bot breaks blocks: disconnected, nothing paid
//   node persist.js exact-kill [name]           Blade Mastery 60 kills a level-6 husk while loading: the anti-grind quarter
const mineflayer = require('mineflayer');
const { spawn, execFileSync } = require('child_process');
const fs = require('fs');
const path = require('path');

const phase = process.argv[2] || 'contention';
const PORT = Number(process.env.MC_PORT || 25565);
// The sandbox server's folder, always given: a default could point at another server's database than the one on MC_PORT,
// and every check would then read the wrong file (2026-11 review).
const SERVER_DIR = process.env.SERVER_DIR;
if (!SERVER_DIR) { console.log('Set SERVER_DIR to the sandbox server\'s folder (the one listening on MC_PORT).'); process.exit(2); }
const DB = path.join(SERVER_DIR, 'plugins/CerberusPlugin/cerberus.db');
if (!fs.existsSync(DB)) { console.log('No database at ' + DB + ': is SERVER_DIR the sandbox server\'s folder?'); process.exit(2); }
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
  let rolledBack = 0, lost = 0, invalid = 0;
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
    // a trial that couldn't read the game's value or the stored one proves nothing: it counts against the check
    if (!Array.isArray(after) || !Array.isArray(shown) || !Array.isArray(base)) invalid++;
    else if (!ok) (after[0] < shown[0] || after[1] < shown[1]) ? rolledBack++ : lost++;
    console.log(`  trial ${t}: before ${JSON.stringify(base)}, the game showed ${JSON.stringify(shown)}, stored ${JSON.stringify(after)}${ok ? '' : '  <<<'}`);
  }
  check(`Contention: the stored XP is the last value the game showed in every trial (${trials})`, rolledBack === 0 && lost === 0 && invalid === 0,
    `${rolledBack} rolled back, ${lost} other, ${invalid} without a value to compare`);
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
  // LOCK_SECONDS: the database's write lock is taken here, before the gains, and held that long (past the stop and the
  // next start, for recovery-check): nothing of them can reach the database before the stop.
  if (process.env.LOCK_SECONDS) await holdLock(Number(process.env.LOCK_SECONDS));
  const a1 = 10 + Date.now() % 37, a2 = 10 + Math.floor(Date.now() / 13) % 41; // differs from run to run
  awardNow(bot, name, a1);
  await sleep(200);
  const shown = await award(bot, name, a2, 2500); // inside the save window: its save is pending when the server stops
  // Class progress too: a Warrior rank that differs from run to run (a stale stored value can't pass)
  let storedRank = null;
  try { storedRank = JSON.parse(execFileSync('python3', [TOOL, 'rank', DB, name, 'warrior']).toString()); } catch (e) { /* none yet */ }
  let rank = 2 + (Date.now() % 7);
  if (rank === storedRank) rank = rank === 8 ? 2 : rank + 1; // never the stored one: a stale value can't pass
  const at = bot.chatLog.length;
  bot.chat(`/cerb class ${name} warrior rank ${rank}`);
  let ranked = false;
  for (let i = 0; i < 40 && !ranked; i++) { await sleep(50); ranked = bot.chatLog.slice(at).some(m => new RegExp('Warrior is now rank ' + rank).test(m)); }
  fs.writeFileSync(process.env.OUT || path.join(SERVER_DIR, 'persist-shutdown.json'), JSON.stringify({ skills: shown, warrior: rank }));
  console.log(`  the game showed ${JSON.stringify(shown)} and Warrior rank ${rank}; stop the server now`);
  check('shutdown-set: the game showed the skill and the class rank', Array.isArray(shown) && ranked);
  // Stay online until the server stops (a quit first would be the quit's save, not shutdown's).
  await new Promise(r => { bot.once('end', r); setTimeout(r, 180000); });
}

async function shutdownCheck() {
  const name = process.argv[3] || 'CerbDown';
  const want = JSON.parse(fs.readFileSync(process.env.OUT || path.join(SERVER_DIR, 'persist-shutdown.json')).toString());
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

/**
 * After shutdown-set, a stop with the database's write lock held, and a start with it still held (so the recovery file
 * can't be written at startup): the join continues from the file's progress, not the older stored rows, and once the
 * lock is let go the progress is stored and the file removed (2026-11 review, finding 1).
 */
async function recoveryCheck() {
  const name = process.argv[3] || 'CerbDown';
  const want = JSON.parse(fs.readFileSync(process.env.OUT || path.join(SERVER_DIR, 'persist-shutdown.json')).toString());
  const shown = want.skills;
  const dir = path.join(SERVER_DIR, 'plugins/CerberusPlugin/skills-recovery');
  // this player's recovery file only (another player's left over from elsewhere proves nothing here)
  const files = () => (fs.existsSync(dir) ? fs.readdirSync(dir) : []).filter(f => f.endsWith('.yml')
    && new RegExp(`^name: ${name}$`, 'm').test(fs.readFileSync(path.join(dir, f), 'utf8')));
  const storedBefore = stored(name), filesBefore = files().length;
  let rankBefore = null;
  try { rankBefore = JSON.parse(execFileSync('python3', [TOOL, 'rank', DB, name, 'warrior']).toString()); } catch (e) { /* null */ }
  const bot = await connect(name);
  const back = await loaded(bot, name);
  console.log(`  before the stop the game showed ${JSON.stringify(shown)}; stored now ${JSON.stringify(storedBefore)}; recovery files ${filesBefore}; the join shows ${JSON.stringify(back)}`);
  check('The stop could not write the progress (the stored rows are older) and kept it in a recovery file', !same(storedBefore, shown) && filesBefore > 0,
    `stored ${JSON.stringify(storedBefore)}, files ${filesBefore}`);
  check('...the start could not write that file either (the lock still held): the join continues from it, not the older stored rows',
    same(back, shown), `the game shows ${JSON.stringify(back)}, expected ${JSON.stringify(shown)}`);
  let rank = null;
  for (let tries = 0; tries < 20 && rank === null; tries++) { // classes load a moment after skills
    const at = bot.chatLog.length;
    bot.chat(`/cerb class ${name} info`);
    await sleep(500);
    const line = bot.chatLog.slice(at).find(m => /Warrior: rank \d+/.test(m));
    if (line) rank = Number(/Warrior: rank (\d+)/.exec(line)[1]);
  }
  check('...and the class rank comes from its recovery file too (the stored document has another)', rank === want.warrior && rankBefore !== want.warrior,
    `rank ${rank}, expected ${want.warrior}; stored before the join ${rankBefore}`);
  let after = null;
  for (let i = 0; i < 150 && !(same(after, shown) && files().length === 0); i++) { await sleep(1000); after = stored(name); }
  await quit(bot);
  console.log(`  once the lock was let go: stored ${JSON.stringify(after)}, recovery files ${files().length}`);
  check('...once the database answers it is stored, and its recovery file removed', same(after, shown) && files().length === 0,
    `stored ${JSON.stringify(after)}, files ${files().length}`);
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
async function busyDatabase(name, seconds, n = 2) {
  const helpers = ['B', 'C', 'D', 'E'].slice(0, n).map(s => name + s);
  const hb = [];
  for (const h of helpers) { const b = await connect(h); await untilLoaded(b, h, 'Mining'); hb.push(b); }
  const lock = await holdLock(seconds);
  for (let i = 0; i < hb.length; i++) { awardNow(hb[i], helpers[i], 1); }
  await sleep(300);
  for (const b of hb) await quit(b);
  return lock;
}
const SKILLS3 = ['Mining', 'Herbalism', 'Farming'];

/** XP from level 1 to [level, xp] on the plugin's curve (Skills.SkillManager.xpForLevel), so gains compare across level-ups. */
const xpForLevel = l => Math.round(75 * Math.pow(Math.max(1, l), 1.85) + 40 * Math.max(1, l));
function total(v) { if (!Array.isArray(v)) return NaN; let t = v[1]; for (let l = 1; l < v[0]; l++) t += xpForLevel(l); return t; }

/** The four blocks broken by a bot whose skills are loaded the normal way: the XP each skill gained. */
async function loadedBreak(name) {
  await arenaSession(name);
  const bot = await connect(name);
  await untilLoaded(bot, name, 'Mining');
  const b0 = {}, a0 = {};
  for (const s of SKILLS3) b0[s] = await valueOf(bot, name, s);
  const broke = await breakAll(bot);
  await sleep(800);
  for (const s of SKILLS3) a0[s] = await valueOf(bot, name, s);
  await quit(bot);
  return { broke, gain: Object.fromEntries(SKILLS3.map(s => [s, total(a0[s]) - total(b0[s])])) };
}

async function joinact() {
  const name = process.argv[3] || 'CerbJoin';
  // First the same four blocks once loaded: a herb or crop this bot never found is discovered here, so the runs compared
  // below pay alike (a discovery waits for a loaded session; 2026-11 review).
  await loadedBreak(name);
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
  const gain = s => total(after[s]) - total(before[s]);
  console.log(`  broke ${broke} blocks while ${during ? 'ALREADY LOADED (inconclusive)' : 'loading'}; before ${JSON.stringify(before)}, after ${JSON.stringify(after)}`);
  // The control: the same blocks broken once loaded, the normal way
  const control = await loadedBreak(name);
  console.log(`  control (loaded): broke ${control.broke}, ${SKILLS3.map(s => `${s} +${control.gain[s]}`).join(', ')}`);
  check('Joining: the bot broke the four blocks while its skills were still loading', broke === 4 && during === null, `broke ${broke}, loaded then: ${JSON.stringify(during)}`);
  check('...and the XP it earned meanwhile arrived once they loaded, once: exactly what the same blocks pay a loaded player',
    control.broke === 4 && SKILLS3.every(s => gain(s) > 0 && gain(s) === control.gain[s]),
    SKILLS3.map(s => `${s} +${gain(s)} (loaded: +${control.gain[s]})`).join(', '));
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


// ── Exact XP while loading (the owner's decision on item 10, 2026-11-08) ─────────────────────
// What a player does while their skills load is paid once they have loaded, in order, at their restored level, seeing the
// level-ups of what came before it. Each phase compares the loading window with a loaded session doing the same from the
// same start (the control). On a build before the fix (amounts worked out at level 1 while loading, then added) the veteran,
// levelup, rapid and kill phases FAIL; new and fail pass on both (level 1 was always right; a failed load pays nothing).
const LAYOUTS = {
  four: [['coal_ore', 1, 0, 0], ['coal_ore', -1, 0, 0], ['poppy', 0, 0, 2], ['wheat[age=7]', 0, 0, -2]],
  coal2: [['coal_ore', 1, 0, 0], ['coal_ore', -1, 0, 0]],
  twelve: [['coal_ore', 1, 0, 0], ['coal_ore', -1, 0, 0], ['coal_ore', 1, 1, 0], ['coal_ore', -1, 1, 0],
    ['poppy', -1, 0, 2], ['poppy', 0, 0, 2], ['poppy', 1, 0, 2], ['poppy', 2, 0, 2],
    ['wheat[age=7]', -1, 0, -2], ['wheat[age=7]', 0, 0, -2], ['wheat[age=7]', 1, 0, -2], ['wheat[age=7]', 2, 0, -2]],
};
/** Place a layout's blocks at the arena (a poppy on grass, wheat on wet farmland), while the bot is away. */
async function place(layout) {
  for (const [block, dx, dy, dz] of layout) {
    const x = AX + dx, y = AY + dy, z = AZ + dz;
    if (block === 'poppy') console_(`setblock ${x} ${AY - 1} ${z} grass_block`);
    if (block.startsWith('wheat')) console_(`setblock ${x} ${AY - 1} ${z} farmland[moisture=7]`);
    console_(`setblock ${x} ${y} ${z} ${block}`);
  }
  await sleep(900);
}
/** Break a layout's blocks in its order as fast as the bot can (after its world arrived); returns how many broke. */
async function breakLayout(bot, layout) {
  const spots = layout.map(([, dx, dy, dz]) => new Vec3(AX + dx, AY + dy, AZ + dz));
  for (let i = 0; i < 60 && spots.some(v => !bot.blockAt(v)); i++) await sleep(50);
  for (let i = 0; i < 40 && !bot.inventory.items().some(it => it.name === 'diamond_pickaxe'); i++) await sleep(50);
  const pick = bot.inventory.items().find(i => i.name === 'diamond_pickaxe');
  if (pick) await bot.equip(pick, 'hand');
  let broke = 0;
  for (const v of spots) {
    const b = bot.blockAt(v);
    if (!b || b.name === 'air') continue;
    try { await bot.dig(b, true); broke++; } catch (e) { /* counted */ }
  }
  return broke;
}
/** "/cerb skill <name> <skill> level <l>", then "xp <n>": the game's value after it. */
async function setSkill(bot, name, skill, level, xp = 0) {
  for (const c of [`level ${level}`].concat(xp > 0 ? [`xp ${xp}`] : [])) {
    const at = bot.chatLog.length;
    bot.chat(`/cerb skill ${name} ${skill} ${c}`);
    for (let i = 0; i < 40 && !bot.chatLog.slice(at).some(m => /is now level/.test(m)); i++) await sleep(50);
  }
  return valueOf(bot, name, skill);
}
/**
 * A loaded session that sets the arena and the start up, then logs out on the arena: `start` is skill -> [level, xp];
 * `give` the items. Returns the game's values after it (stored by the quit).
 */
async function startOn(name, start, give = ['diamond_pickaxe'], extra = []) {
  const bot = await connect(name);
  await untilLoaded(bot, name, 'Mining');
  // At the arena first: a fill where no player is (a new bot joins at the world's spawn) does nothing, "That position is
  // not loaded" (seen on a fresh sandbox world).
  console_(`tp ${name} ${AX + 0.5} ${AY + 1} ${AZ + 0.5} 0 0`);
  const at = new Vec3(AX + 0.5, AY + 1, AZ + 0.5);
  for (let i = 0; i < 80 && !(bot.entity.position.distanceTo(at) < 3 && bot.blockAt(new Vec3(AX, AY - 1, AZ))); i++) await sleep(100);
  await sleep(800);
  console_(`fill ${AX - 3} ${AY - 1} ${AZ - 3} ${AX + 3} ${AY + 4} ${AZ + 3} air`); await sleep(400);
  console_(`fill ${AX - 3} ${AY - 1} ${AZ - 3} ${AX + 3} ${AY - 1} ${AZ + 3} stone`); await sleep(400);
  bot.chat('/kill @e[type=!player,distance=..8]'); await sleep(300); // the bot's own command: Folia's console can't select a region's entities
  console_(`tp ${name} ${AX + 0.5} ${AY} ${AZ + 0.5} 0 0`); await sleep(600);
  console_(`gamemode survival ${name}`); console_(`clear ${name}`); await sleep(300);
  for (const g of give) console_(`give ${name} ${g}`);
  for (const c of extra) console_(c);
  const got = {};
  for (const [skill, [level, xp]] of Object.entries(start)) got[skill] = await setSkill(bot, name, skill, level, xp);
  await sleep(1500);
  await quit(bot);
  await sleep(500);
  return got;
}
const valuesOf = async (bot, name, skills) => { const v = {}; for (const s of skills) v[s] = await valueOf(bot, name, s); return v; };
/** Stored values by the skill's database key ("Blade Mastery"; commands take "Blade_Mastery"). */
const storedAll = (name, skills) => Object.fromEntries(skills.map(s => [s, storedOf(name, s.replace(/_/g, ' '))]));
/** The layout broken while the skills load (the database busy, `n` saves queued in front of the load). */
async function duringLoad(name, layout, skills, seconds, n) {
  await place(layout);
  const before = storedAll(name, skills);
  const { released } = await busyDatabase(name, seconds, n);
  const bot = await connect(name);
  const broke = await breakLayout(bot, layout);
  const during = await valueOf(bot, name, 'Mining'); // null: still loading when the blocks broke
  await released;
  await untilLoaded(bot, name, 'Mining');
  await sleep(500);
  const after = await valuesOf(bot, name, skills);
  await quit(bot);
  return { broke, during, before, after };
}
/** The control: the same layout broken by a session whose skills are loaded. */
async function loadedRun(name, layout, skills) {
  await place(layout);
  const bot = await connect(name);
  await untilLoaded(bot, name, 'Mining');
  const before = await valuesOf(bot, name, skills);
  const broke = await breakLayout(bot, layout);
  await sleep(800);
  const after = await valuesOf(bot, name, skills);
  await quit(bot);
  return { broke, before, after };
}
const show = v => Object.entries(v).map(([s, x]) => `${s} ${JSON.stringify(x)}`).join(', ');
const gainsOf = (r, skills) => skills.map(s => `${s} +${total(r.after[s]) - total(r.before[s])}`).join(', ');
function opAll(names) { for (const n of names) console_(`op ${n}`); return sleep(600); }

/** One comparison: from `start`, the layout during a load and once loaded; the same values after both, and `expect`. */
async function exactCase(label, name, start, layout, { seconds = 7, n = 2, expect = null } = {}) {
  const skills = Object.keys(start);
  await opAll([name, ...['B', 'C', 'D', 'E'].slice(0, n).map(s => name + s)]);
  await startOn(name, start);
  const first = await loadedRun(name, layout, skills); // a herb or crop this bot never found is discovered here, not in the runs compared
  check(`${label}: setup, the first loaded run (discoveries) broke every block`, first.broke === layout.length, `broke ${first.broke}`);
  const from = await startOn(name, start);
  const run = await duringLoad(name, layout, skills, seconds, n);
  await startOn(name, start);
  const control = await loadedRun(name, layout, skills);
  console.log(`  start ${show(from)}`);
  console.log(`  while loading: broke ${run.broke}/${layout.length} ${run.during ? 'ALREADY LOADED (inconclusive)' : 'while loading'}; after ${show(run.after)} (${gainsOf(run, skills)})`);
  console.log(`  loaded control: broke ${control.broke}/${layout.length}; after ${show(control.after)} (${gainsOf(control, skills)})`);
  check(`${label}: every block broke while the skills were still loading`, run.broke === layout.length && run.during === null,
    `broke ${run.broke}, loaded then: ${JSON.stringify(run.during)}`);
  check(`${label}: the start was set`, skills.every(s => same(from[s], start[s])), show(from));
  check(`${label}: once loaded, the XP is exactly what a loaded player gets for the same blocks from the same start`,
    control.broke === layout.length && skills.every(s => same(run.after[s], control.after[s])),
    skills.map(s => `${s} ${JSON.stringify(run.after[s])} vs loaded ${JSON.stringify(control.after[s])}`).join(', '));
  if (expect) check(`${label}: ${expect.what}`, skills.every(s => !expect[s] || same(run.after[s], expect[s])),
    skills.filter(s => expect[s]).map(s => `${s} ${JSON.stringify(run.after[s])}, expected ${JSON.stringify(expect[s])}`).join(', '));
}

async function exactNew() {
  const name = process.argv[3] || 'CerbExactN';
  await exactCase('A new player (level 1)', name, { Mining: [1, 0], Herbalism: [1, 0], Farming: [1, 0] }, LAYOUTS.four,
    { expect: { what: 'full XP at level 1 (coal 4 each, poppy 3, wheat 3; a level-1 skill holds its XP)', Mining: [1, 8], Herbalism: [1, 3], Farming: [1, 3] } });
}
async function exactVeteran() {
  const name = process.argv[3] || 'CerbExactV';
  await exactCase('An experienced player (level 60), twelve actions', name, { Mining: [60, 0], Herbalism: [60, 0], Farming: [60, 0] }, LAYOUTS.twelve,
    { seconds: 14, n: 3, expect: { what: 'level-1 resources pay a quarter at level 60 (weak-resources): coal 1, poppy and wheat 0.75', Mining: [60, 4], Herbalism: [60, 3], Farming: [60, 3] } });
}
async function exactLevelup() {
  const name = process.argv[3] || 'CerbExactL';
  const need = xpForLevel(20);
  await exactCase('Buffered actions that level up', name, { Mining: [20, need - 2] }, LAYOUTS.coal2,
    { expect: { what: 'the first coal pays 4 at Mining 20 and levels up; the second pays a quarter at 21 (Mining 21, 3 XP)', Mining: [21, 3] } });
}

/** Two sessions in a row while the database is busy: the first quits before its load, the second loads. */
async function exactRapid() {
  const name = process.argv[3] || 'CerbExactR';
  const start = { Mining: [60, 0], Herbalism: [60, 0], Farming: [60, 0] }, skills = Object.keys(start), layout = LAYOUTS.four;
  await opAll([name, name + 'B', name + 'C', name + 'D', name + 'E']);
  await startOn(name, start);
  const first = await loadedRun(name, layout, skills); // discoveries
  check('Rapid reconnect: setup, the first loaded run (discoveries) broke every block', first.broke === layout.length, `broke ${first.broke}`);
  const from = await startOn(name, start);
  await place(layout);
  const before = storedAll(name, skills);
  const { released } = await busyDatabase(name, 16, 4);
  const a = await connect(name);
  const brokeA = await breakLayout(a, layout);
  const duringA = await valueOf(a, name, 'Mining');
  await quit(a); // before its load completes
  await place(layout);
  const b = await connect(name); // the rejoin, while the first session's load is still waiting
  const brokeB = await breakLayout(b, layout);
  const duringB = await valueOf(b, name, 'Mining');
  await released;
  await untilLoaded(b, name, 'Mining');
  await sleep(500);
  const after = await valuesOf(b, name, skills);
  await quit(b);
  await sleep(2500);
  const storedAfter = storedAll(name, skills);
  await startOn(name, start);
  const control = await loadedRun(name, layout, skills);
  console.log(`  start ${show(from)}; stored before ${show(before)}`);
  console.log(`  first session broke ${brokeA} ${duringA ? '(ALREADY LOADED)' : 'while loading'}, then quit; the rejoin broke ${brokeB} ${duringB ? '(ALREADY LOADED)' : 'while loading'}; after ${show(after)}; stored ${show(storedAfter)}`);
  console.log(`  loaded control: ${show(control.after)} (${gainsOf(control, skills)})`);
  check('Rapid reconnect: both sessions broke their blocks while loading', brokeA === 4 && brokeB === 4 && duringA === null && duringB === null,
    `broke ${brokeA} and ${brokeB}; loaded then: ${JSON.stringify(duringA)}, ${JSON.stringify(duringB)}`);
  check('Rapid reconnect: only the rejoined session\'s actions are paid, once, at the restored level (the first session\'s are dropped)',
    skills.every(s => same(after[s], control.after[s])), skills.map(s => `${s} ${JSON.stringify(after[s])} vs one loaded session ${JSON.stringify(control.after[s])}`).join(', '));
  check('Rapid reconnect: what is stored after the quit is what the game showed', skills.every(s => same(storedAfter[s], after[s])));
}

/** The database refuses every read past the busy timeout: the load fails while the bot breaks blocks. */
async function exactFail() {
  const name = process.argv[3] || 'CerbExactF';
  const start = { Mining: [60, 0], Herbalism: [60, 0], Farming: [60, 0] }, skills = Object.keys(start), layout = LAYOUTS.four;
  await opAll([name]);
  await startOn(name, start);
  await place(layout);
  const before = storedAll(name, skills);
  const { released } = await holdXLock(15);
  const bot = await connect(name);
  const kickedP = new Promise(r => { bot.once('kicked', reason => r(JSON.stringify(reason))); bot.once('end', () => r('ended')); setTimeout(() => r(null), 30000); });
  const broke = await breakLayout(bot, layout);
  const kicked = await kickedP;
  await released;
  await sleep(1500);
  const storedAfter = storedAll(name, skills);
  const again = await connect(name);
  await untilLoaded(again, name, 'Mining');
  await sleep(1000);
  const back = await valuesOf(again, name, skills);
  await quit(again);
  console.log(`  broke ${broke} while the load waited; kicked: ${kicked}; stored before ${show(before)}, after ${show(storedAfter)}; the rejoin loads ${show(back)}`);
  check('A failed load: the bot broke blocks while it waited, then was disconnected with the reason', broke > 0 && /could not load your saved data/i.test(kicked || ''), `broke ${broke}; ${kicked}`);
  check('...and none of that XP is stored or paid at the rejoin', skills.every(s => same(storedAfter[s], before[s]) && same(back[s], before[s])));
}

/** A kill while loading: Blade Mastery 60 and a level-6 husk (persist.js's arena): the anti-grind quarter, as once loaded. */
async function exactKill() {
  const name = process.argv[3] || 'CerbExactK';
  const skills = ['Blade_Mastery'], start = { Blade_Mastery: [60, 0] };
  const give = ['diamond_sword'];
  await opAll([name, name + 'B', name + 'C', name + 'D']);
  const husk = () => { console_(`summon husk ${AX + 0.5} ${AY} ${AZ + 2.0} {NoAI:1b,PersistenceRequired:1b}`); return sleep(900); };
  const strong = [`effect give ${name} strength infinite 20 true`]; // one swing kills (+63 damage): the kill lands inside the window
  async function swingKill(bot) {
    for (let i = 0; i < 60 && !bot.inventory.items().some(it => it.name === 'diamond_sword'); i++) await sleep(50);
    const sword = bot.inventory.items().find(i => i.name === 'diamond_sword');
    if (sword) await bot.equip(sword, 'hand');
    let target = null;
    for (let i = 0; i < 60 && !target; i++) { target = bot.nearestEntity(e => e.name === 'husk' && e.position.distanceTo(bot.entity.position) < 5); if (!target) await sleep(50); }
    if (!target) return -1;
    for (let s = 1; s <= 20; s++) {
      await bot.lookAt(target.position.offset(0, target.height * 0.6, 0), true);
      bot.attack(target);
      await sleep(650);
      if (!bot.entities[target.id] || !bot.entities[target.id].isValid) return s;
    }
    return -1;
  }
  async function session(during) {
    await startOn(name, start, give, strong);
    await husk();
    const before = storedAll(name, skills);
    let released = null;
    if (during) released = (await busyDatabase(name, 14, 3)).released;
    const bot = await connect(name);
    if (!during) await untilLoaded(bot, name, 'Mining');
    const b0 = during ? before : await valuesOf(bot, name, skills);
    const swings = await swingKill(bot);
    const loadedAtKill = await valueOf(bot, name, 'Mining');
    if (released) await released;
    await untilLoaded(bot, name, 'Mining');
    await sleep(800);
    const after = await valuesOf(bot, name, skills);
    await quit(bot);
    return { swings, loadedAtKill, before: b0, after };
  }
  const run = await session(true);
  const control = await session(false);
  console.log(`  while loading: killed in ${run.swings} swing(s), ${run.loadedAtKill ? 'ALREADY LOADED (inconclusive)' : 'still loading'}; ${show(run.before)} -> ${show(run.after)}`);
  console.log(`  loaded control: killed in ${control.swings} swing(s); ${show(control.before)} -> ${show(control.after)}`);
  const gain = r => total(r.after.Blade_Mastery) - total(r.before.Blade_Mastery);
  check('A kill while loading: the husk died before the skills loaded', run.swings > 0 && run.loadedAtKill === null, `swings ${run.swings}, loaded then: ${JSON.stringify(run.loadedAtKill)}`);
  check('...and its kill XP is what a loaded Blade Mastery 60 gets (the anti-grind quarter), not level 1\'s',
    control.swings > 0 && gain(run) === gain(control) && gain(control) > 0, `while loading +${gain(run)}, loaded +${gain(control)}`);
}

const PHASES = { contention, failure, rapid, backup, joinact, joinquit, joinfail, 'shutdown-set': shutdownSet, 'shutdown-check': shutdownCheck,
  'recovery-check': recoveryCheck, 'exact-new': exactNew, 'exact-veteran': exactVeteran, 'exact-levelup': exactLevelup,
  'exact-rapid': exactRapid, 'exact-fail': exactFail, 'exact-kill': exactKill };
(async () => {
  try { await PHASES[phase](); } catch (e) { check('phase ran without errors', false, e.stack); }
  const failed = results.filter(r => !r).length;
  console.log(`\n${results.length - failed}/${results.length} passed`);
  process.exit(failed ? 1 : 0);
})();
