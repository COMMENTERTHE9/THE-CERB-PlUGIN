// Ranged mob attacks scale with mob level and tier like melee, within their own caps (owner decision 10, 2026-10-27;
// combat.yml mob-damage-scaling.ranged). LOCAL SANDBOX servers only; the bot must be op.
// Usage: node ranged.js <phase> [name]
//   levels  - a fresh player (100 health) and a trained one (Survival, Heavy Armor Training, First Aid 100: about
//             300 health and more Fortitude) each take arrows from COMMON skeletons of level 1, 50 and 100; the
//             combat trace (/cerb combatdebug) shows each shot's multiplier, and health lost per shot is measured
//   fangs   - an evoker's fangs (its ranged spell) scale like its arrows would
//   cap     - with max-hit-share lowered in the sandbox's combat.yml (SERVER_DIR=...; restored afterwards), a level-100
//             shot is held to that share of max health, never below its vanilla damage
//   control - on a build without the change: a level-100 skeleton's arrow costs what a level-1 one's does
const mineflayer = require('mineflayer');
const fs = require('fs');
const path = require('path');
const phase = process.argv[2] || 'levels';
const NAME = process.argv[3] || 'CerbRanged';
const sleep = ms => new Promise(r => setTimeout(r, ms));
const chat = [];
const results = [];
const check = (name, ok, detail = '') => { results.push(ok); console.log(`${ok ? 'PASS' : 'FAIL'}  ${name}${detail ? '  (' + detail + ')' : ''}`); };
const bot = mineflayer.createBot({ host: '127.0.0.1', port: Number(process.env.MC_PORT || 25565), username: NAME, version: '1.21.11', auth: 'offline' });
bot.on('messagestr', (m, pos) => { if (pos !== 'game_info') chat.push(m); });
bot.on('kicked', r => { console.log('KICKED', r); process.exit(2); });
async function cmd(c, wait = 700) { const at = chat.length; bot.chat(c); await sleep(wait); return at; }
const saw = (re, since) => chat.slice(since).find(m => re.test(m));

/** The melee curve: tierBase x (1 + 0.55 ln(1 + L/25)); COMMON's tier base is 1. */
const commonMult = L => 1 + 0.55 * Math.log1p(L / 25);

let X = 0, Y = 200, Z = 0;
async function arena() {
  const sp = bot.spawnPoint || bot.entity.position;
  X = Math.floor(sp.x) - 60; Z = Math.floor(sp.z) + 30;
  await cmd('/time set midnight', 300);
  await cmd('/weather clear', 300);
  await cmd(`/fill ${X - 14} ${Y - 1} ${Z - 14} ${X + 14} ${Y - 1} ${Z + 14} stone`, 500);
  await cmd(`/fill ${X - 14} ${Y} ${Z - 14} ${X + 14} ${Y + 4} ${Z + 14} air`, 500);
  await clear();
}
async function clear() {
  for (const t of ['skeleton', 'evoker', 'vex', 'arrow', 'evoker_fangs', 'item']) await cmd(`/kill @e[type=${t},distance=..80]`, 250);
}
async function stand(dx, dz, yaw = 0) { await cmd(`/tp @s ${X + dx + 0.5} ${Y} ${Z + dz + 0.5} ${yaw} 0`, 600); }

async function health() {
  const at = await cmd(`/cerb healthstate ${NAME}`, 600);
  const m = (saw(/Custom HP:/, at) || '').match(/Custom HP:\s*([\d.]+)\s*\/\s*([\d.]+)/);
  return m ? { cur: +m[1], max: +m[2] } : { cur: NaN, max: NaN };
}
async function heal() { await cmd(`/cerb heal ${NAME}`, 500); }
async function skills(level) {
  for (const s of ['Survival', 'Heavy_Armor_Training', 'First_Aid']) await cmd(`/cerb skill ${NAME} ${s} level ${level}`, 400);
  await sleep(800);
}

/**
 * Spawn `mob` (with AI) 9 blocks in front, wait for the first hit that reaches us; returns the trace's MobScaling
 * step ("ranged x1.88") if any, and the health that hit cost (polled, so the first drop only).
 */
async function takeOneShot(mob, level, { wantTrace = true, timeoutMs = 30000 } = {}) {
  await clear();
  await heal();
  await sleep(1500); // the defense bar refills between shots (regen after its delay); health is full
  await stand(0, 9, 180);
  await cmd(`/cerb spawn ${mob} ${level}`, 900);
  await stand(0, 0, 0);
  const before = await health();
  const at = chat.length;
  const t0 = Date.now();
  let after = before, trace = null;
  while (Date.now() - t0 < timeoutMs) {
    await sleep(250);
    trace = chat.slice(at).find(m => /^\[Combat\]/.test(m) && /MobScaling\([^)]*ranged/.test(m)) || null;
    const h = await health();
    if (h.cur < before.cur - 0.01) { after = h; if (trace || !wantTrace) break; }
    if (trace && after !== before) break;
  }
  await clear();
  const step = trace ? trace.match(/MobScaling\(([A-Z]+) Lv(\d+) ranged x([\d.]+)( \(hit capped: ([\d.]+), vanilla ([\d.]+)\))?\): ([\d.]+)/) : null;
  return {
    trace, lost: before.cur - after.cur, max: before.max,
    tier: step ? step[1] : null, level: step ? +step[2] : null, mult: step ? +step[3] : null, capped: !!(step && step[4]),
    uncapped: step && step[5] ? +step[5] : null, vanilla: step && step[6] ? +step[6] : null, scaled: step ? +step[7] : null,
  };
}

async function levels() {
  await arena();
  await cmd('/gamemode survival', 400);
  await cmd('/clear @s', 300);
  await debug(true);
  const rows = [];
  for (const [who, lvl] of [['fresh', 1], ['trained', 100]]) {
    await skills(lvl);
    for (const L of [1, 50, 100]) {
      const r = await takeOneShot('skeleton', L);
      rows.push({ who, L, ...r });
      console.log(`  ${who} player (max ${r.max.toFixed(0)}), skeleton level ${L}: x${r.mult} -> ${r.scaled} damage, health lost ${r.lost.toFixed(1)} (${(100 * r.lost / r.max).toFixed(1)}% of max)`);
      check(`${who} player, level-${L} skeleton: the shot carries the ranged multiplier x${commonMult(L).toFixed(2)}`,
        r.tier === 'COMMON' && r.level === L && Math.abs(r.mult - commonMult(L)) < 0.011, r.trace || 'no ranged trace');
      check(`${who} player, level-${L} skeleton: the shot cost health`, r.lost > 0, `lost ${r.lost}`);
    }
  }
  const share = (who, L) => { const r = rows.find(x => x.who === who && x.L === L); return r ? r.lost / r.max : NaN; };
  const lost = (who, L) => { const r = rows.find(x => x.who === who && x.L === L); return r ? r.lost : NaN; };
  check('Level 100 shots cost clearly more than level 1 shots (fresh player)', lost('fresh', 100) > 1.4 * lost('fresh', 1),
    `${lost('fresh', 1).toFixed(1)} -> ${lost('fresh', 100).toFixed(1)}`);
  check('A trained player loses a smaller share of health to the same level-100 shot', share('trained', 100) < share('fresh', 100),
    `${(100 * share('fresh', 100)).toFixed(1)}% vs ${(100 * share('trained', 100)).toFixed(1)}%`);
  check('No level-100 COMMON shot comes near the one-shot cap (35% of max health)', rows.every(r => r.lost < 0.35 * r.max),
    rows.map(r => (100 * r.lost / r.max).toFixed(1) + '%').join(' '));
  await skills(1);
  await debug(false);
}

async function fangs() {
  await arena();
  await cmd('/gamemode survival', 400);
  await debug(true);
  await skills(1);
  // Fangs come out when the evoker has a target in range; vexes it summons hit in melee (traced without "ranged").
  const r = await takeOneShot('evoker', 100, { timeoutMs: 40000 });
  console.log(`  evoker level 100: ${r.trace || 'no ranged trace'}; health lost ${r.lost.toFixed(1)}`);
  check('Evoker fangs are scaled as its ranged attack', r.mult !== null && r.level === 100 && r.mult > 1.5, r.trace || 'none');
  await debug(false);
}

async function cap() {
  const dir = process.env.SERVER_DIR;
  if (!dir) { check('SERVER_DIR is set (the sandbox server folder)', false); return; }
  const file = path.join(dir, 'plugins', 'CerberusPlugin', 'combat.yml');
  const original = fs.readFileSync(file, 'utf8');
  try {
    // A combat.yml from before this change has no ranged: section (its defaults apply): add one for the test.
    const edited = /max-hit-share: [\d.]+/.test(original) ? original.replace(/max-hit-share: [\d.]+/, 'max-hit-share: 0.03')
      : original.replace(/^mob-damage-scaling:\n/m, 'mob-damage-scaling:\n  ranged:\n    max-hit-share: 0.03\n');
    if (edited === original) { check('combat.yml has a mob-damage-scaling section to edit', false); return; }
    fs.writeFileSync(file, edited);
    await cmd('/cerb reload', 2500);
    await arena();
    await cmd('/gamemode survival', 400);
    await debug(true);
    await skills(1);
    const r = await takeOneShot('skeleton', 100);
    console.log(`  share 0.03 (3 health of 100): ${r.trace}`);
    check('With max-hit-share 0.03 the level-100 shot is capped', r.capped, r.trace || 'none');
    const limit = Math.max(0.03 * r.max, r.vanilla);
    check('...to 3% of max health, or the vanilla damage when that is higher (never weaker than vanilla)',
      r.scaled !== null && Math.abs(r.scaled - limit) <= 0.06 && r.uncapped > r.scaled,
      `${r.scaled} (3% of ${r.max} = ${(0.03 * r.max).toFixed(2)}, vanilla ${r.vanilla}, uncapped ${r.uncapped})`);
  } finally {
    fs.writeFileSync(file, original);
    await cmd('/cerb reload', 2500);
    await debug(false);
  }
  check('combat.yml restored', fs.readFileSync(file, 'utf8') === original);
}

async function control() {
  await arena();
  await cmd('/gamemode survival', 400);
  await skills(1);
  const a = await takeOneShot('skeleton', 1, { wantTrace: false });
  const b = await takeOneShot('skeleton', 100, { wantTrace: false });
  console.log(`  level 1 shot cost ${a.lost.toFixed(1)}, level 100 shot cost ${b.lost.toFixed(1)}`);
  check('CONTROL: a level-100 skeleton shoots like a level-1 one (expected to FAIL on the new build)', b.lost < 1.3 * a.lost && b.lost > 0,
    `${a.lost.toFixed(1)} vs ${b.lost.toFixed(1)}`);
}

async function debug(on) {
  for (let k = 0; k < 2; k++) {
    const at = await cmd('/cerb combatdebug', 600);
    const m = (saw(/Combat debug: (ON|OFF)/, at) || '').match(/(ON|OFF)/);
    if (m && (m[1] === 'ON') === on) return;
  }
}

bot.once('spawn', async () => {
  await sleep(3000);
  try {
    if (phase === 'levels') await levels();
    else if (phase === 'fangs') await fangs();
    else if (phase === 'cap') await cap();
    else if (phase === 'control') await control();
    else { console.log('Unknown phase ' + phase); results.push(false); }
  } catch (e) { console.log('SCRIPT ERROR', e.stack); results.push(false); }
  await cmd('/gamemode creative', 300);
  const failed = results.filter(r => !r).length;
  console.log(`\n${results.length - failed}/${results.length} passed`);
  bot.quit(); await sleep(400); process.exit(failed ? 1 : 0);
});
setTimeout(() => { console.log('TIMEOUT'); process.exit(3); }, 900000);
