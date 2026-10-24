// Progression in-game checks (kill XP, and later damage/survivability by skill level).
// LOCAL SANDBOX servers only; the bot must be op.  Usage: node progression.js [phase]
//   killxp  - kill XP v2 by mob level, anti-grind
//   swings  - swings-to-kill table: Blade Mastery 1/50/100 vs zombie level 1/50 (prints numbers)
const mineflayer = require('mineflayer');
const phase = process.argv[2] || 'killxp';
const NAME = process.argv[3] || 'CerbSmoke';
const sleep = ms => new Promise(r => setTimeout(r, ms));
const chat = [];
const results = [];
const check = (name, ok, detail = '') => { results.push(ok); console.log(`${ok ? 'PASS' : 'FAIL'}  ${name}${detail ? '  (' + detail + ')' : ''}`); };
const bot = mineflayer.createBot({ host: '127.0.0.1', port: 25565, username: NAME, version: '1.21.11', auth: 'offline' });
bot.on('messagestr', (m, pos) => { if (pos !== 'game_info') chat.push(m); });
bot.on('kicked', r => { console.log('KICKED', r); process.exit(2); });

async function cmd(c, wait = 1000) { const at = chat.length; bot.chat(c); await sleep(wait); return at; }
const saw = (re, since) => chat.slice(since).find(m => re.test(m));
const mobsNear = (name, r = 6) => Object.values(bot.entities).filter(e => e.name === name && e.position.distanceTo(bot.entity.position) < r);

async function equip(item) {
  await cmd('/clear @s ' + item, 600);
  await cmd('/give @s ' + item, 900);
  const slot = bot.inventory.slots.findIndex((it, i) => it && it.name === item && i >= 36 && i <= 44);
  if (slot >= 0) bot.setQuickBarSlot(slot - 36);
  await sleep(300);
  return slot >= 0;
}

/** Spawn a no-AI mob at `level`, hit it until it dies (vanilla attack cooldown respected). */
async function killOne(type, level) {
  await cmd(`/kill @e[type=${type},distance=..10]`, 600);
  const at = await cmd(`/cerb spawn ${type} ${level} noai`, 1500);
  const target = mobsNear(type, 5)[0];
  if (!target) return { swings: 0, xp: NaN, msg: 'mob did not spawn' };
  let swings = 0;
  for (; swings < 400; swings++) {
    const m = bot.entities[target.id];
    if (!m || !m.isValid) break;              // died (entity removed)
    if (m.position.distanceTo(bot.entity.position) > 4.5) { console.log('  target out of reach'); break; }
    await bot.lookAt(m.position.offset(0, m.height * 0.6, 0), true);
    bot.attack(m);
    await sleep(650); // full sword cooldown (~0.625 s) so every hit is a full-strength hit
  }
  await sleep(800);
  const msg = saw(/^\+(\d+) \S.* XP$/, at);
  return { swings, xp: msg ? Number(msg.match(/^\+(\d+)/)[1]) : NaN, msg, grey: !!saw(/Low-level mob/, at) };
}

async function killxp() {
  await cmd('/gamemode creative');
  check('Got a netherite sword', await equip('netherite_sword'));
  await cmd(`/cerb skill ${NAME} Blade_Mastery level 1`);
  let r = await killOne('zombie', 1);
  check('Common L1 kill pays 40 Blade Mastery XP', r.xp === 40, r.msg + ', ' + r.swings + ' swings');
  r = await killOne('zombie', 50);
  check('Common L50 kill pays 334 Blade Mastery XP', r.xp === 334, r.msg + ', ' + r.swings + ' swings');
  await cmd(`/cerb skill ${NAME} Blade_Mastery level 30`);
  r = await killOne('zombie', 1);
  check('Anti-grind: L1 mob at Blade Mastery 30 pays x0.25 and says so', r.xp === 10 && r.grey, r.msg + ' | ' + chat.slice(-6).join(' / '));
  await cmd(`/cerb skill ${NAME} Blade_Mastery level 1`);
}

async function swings() {
  await cmd('/gamemode creative');
  await equip('netherite_sword');
  const rows = [];
  for (const bm of [1, 50, 100]) {
    await cmd(`/cerb skill ${NAME} Blade_Mastery level ${bm}`);
    for (const lvl of [1, 50]) {
      const r = await killOne('zombie', lvl);
      rows.push({ bm, lvl, swings: r.swings });
      console.log(`Blade Mastery ${bm} vs zombie L${lvl}: ${r.swings} swings`);
    }
  }
  await cmd(`/cerb skill ${NAME} Blade_Mastery level 1`);
  return rows;
}

bot.once('spawn', async () => {
  await sleep(2500);
  try {
    if (phase === 'killxp') await killxp();
    else if (phase === 'swings') {
      const rows = await swings();
      const at = (bm, lvl) => rows.find(r => r.bm === bm && r.lvl === lvl).swings;
      check('Higher Blade Mastery kills a L1 zombie in fewer swings', at(100, 1) < at(1, 1), `BM1 ${at(1, 1)} vs BM100 ${at(100, 1)}`);
      check('Equal-level fights take about the same swings (L1/BM1 vs L50/BM50)', Math.abs(at(50, 50) - at(1, 1)) <= 2, `${at(1, 1)} vs ${at(50, 50)}`);
    }
  } catch (e) { console.log('SCRIPT ERROR', e.stack); results.push(false); }
  const failed = results.filter(r => !r).length;
  console.log(`\n${results.length - failed}/${results.length} checks passed`);
  bot.quit(); await sleep(400); process.exit(failed ? 1 : 0);
});
setTimeout(() => { console.log('TIMEOUT'); process.exit(3); }, 600000);
