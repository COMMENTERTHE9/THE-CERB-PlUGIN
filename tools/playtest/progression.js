// Progression in-game checks (kill XP, and later damage/survivability by skill level).
// LOCAL SANDBOX servers only; the bot must be op.  Usage: node progression.js [phase]
//   killxp  - kill XP v2 by mob level, anti-grind
//   swings  - swings-to-kill table: Blade Mastery 1/50/100 vs zombie level 1/50 (prints numbers)
//   stats   - Player Menu -> Stats page shows attributes, and updates after a level change
//   survive - same level-50 zombie hits at Survival/Heavy Armor Training/First Aid 1 vs 100 (Vitality, Fortitude)
//   offswitch - with attributes.yml integration.pve-offense: false, Blade Mastery 100 kills like Blade Mastery 1
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

// Text of a chat component as mineflayer exposes it (NBT compound, JSON string, or JSON object).
const plain = v => {
  if (v == null) return '';
  if (typeof v === 'string') { try { return plain(JSON.parse(v)); } catch { return v; } }
  const s = JSON.stringify(v);
  const nbt = [...s.matchAll(/"text":\{"type":"string","value":"([^"]*)"/g)].map(m => m[1]);
  if (nbt.length) return nbt.join('');
  return [...s.matchAll(/"text":"([^"]*)"/g)].map(m => m[1]).join('');
};
const title = () => bot.currentWindow ? plain(bot.currentWindow.title) : '';
const slotName = i => { const it = bot.currentWindow && bot.currentWindow.slots[i]; return it ? plain(it.customName) : ''; };
const slotLore = i => { const it = bot.currentWindow && bot.currentWindow.slots[i]; return it && it.customLore ? it.customLore.map(plain).join(' | ') : ''; };
async function waitTitle(re, ms = 4000) { const t0 = Date.now(); while (Date.now() - t0 < ms) { if (bot.currentWindow && re.test(title())) return true; await sleep(100); } return false; }
async function click(i, wait = 800) { await bot.clickWindow(i, 0, 0); await sleep(wait); }

async function cmd(c, wait = 1000) { const at = chat.length; bot.chat(c); await sleep(wait); return at; }
const saw = (re, since) => chat.slice(since).find(m => re.test(m));
const mobsNear = (name, r = 6) => Object.values(bot.entities).filter(e => e.name === name && e.position.distanceTo(bot.entity.position) < r);

let heldSlot = 0;
async function equip(item) {
  await cmd('/clear @s ' + item, 600);
  await cmd('/give @s ' + item, 900);
  const slot = bot.inventory.slots.findIndex((it, i) => it && it.name === item && i >= 36 && i <= 44);
  if (slot >= 0) bot.setQuickBarSlot(slot - 36);
  await sleep(300);
  heldSlot = slot - 36;
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
  await cmd(`/cerb skill ${NAME} Critical_Strike level 1`);
  const rows = [];
  for (const bm of [1, 50, 100]) {
    await cmd(`/cerb skill ${NAME} Blade_Mastery level ${bm}`);
    for (const lvl of [1, 50]) {
      // Average two kills: crits (12.5% at Critical Strike 1) make single kills vary by a swing.
      const a = await killOne('zombie', lvl), b = await killOne('zombie', lvl);
      const avg = (a.swings + b.swings) / 2;
      rows.push({ bm, lvl, swings: avg });
      console.log(`Blade Mastery ${bm} vs zombie L${lvl}: ${a.swings} and ${b.swings} swings`);
    }
  }
  // One traced hit, to confirm the multiplier the server applied.
  await cmd(`/cerb skill ${NAME} Blade_Mastery level 50`);
  await cmd('/cerb combatdebug', 600);
  const at = chat.length;
  await cmd('/kill @e[type=zombie,distance=..10]', 600);
  await cmd('/cerb spawn zombie 50 noai', 1500);
  const z = mobsNear('zombie', 5)[0];
  if (z) { await bot.lookAt(z.position.offset(0, z.height * 0.6, 0), true); bot.attack(z); await sleep(900); }
  const trace = chat.slice(at).find(m => /\[Combat\] Power/.test(m)) || '';
  await cmd('/cerb combatdebug', 600);
  await cmd('/kill @e[type=zombie,distance=..10]', 600);
  await cmd(`/cerb skill ${NAME} Blade_Mastery level 1`);
  return { rows, trace };
}

async function openStats() {
  if (bot.currentWindow) { bot.closeWindow(bot.currentWindow); await sleep(400); }
  const head = bot.inventory.slots[44];
  if (!head || head.name !== 'player_head') return false;
  bot.setQuickBarSlot(8); await sleep(300);
  bot.activateItem(); await sleep(1200);
  if (!(await waitTitle(/Player Menu/))) return false;
  await click(29, 1200);
  return waitTitle(/Your Stats/);
}

async function stats() {
  await cmd('/gamemode creative');
  await equip('netherite_sword');
  await cmd(`/cerb skill ${NAME} Blade_Mastery level 1`);
  await cmd(`/cerb skill ${NAME} Critical_Strike level 1`);
  check('Player Menu -> Stats opens', await openStats(), title());
  check('Power tile shows sword Power from Blade Mastery 1', /Swords: 1\.0 +-> +x1\.05 damage/.test(slotLore(10)) && /Blade Mastery 1: \+1\.0 \(swords\)/.test(slotLore(10)), slotLore(10));
  check('Power tile says which skills feed it', /Gained from.*Blade Mastery \+1 \(swords\).*Weapon Mastery \+0\.25/.test(slotLore(10)), slotLore(10));
  check('Agility tile shows crit from Critical Strike', /Crit chance: 1[0-9]\.[0-9]%/.test(slotLore(11)), slotLore(11));
  check('Power is marked active against mobs', /Active \(fighting mobs\)/.test(slotLore(10)), slotLore(10));
  check('Vitality is marked active', /● Active/.test(slotLore(12)), slotLore(12));
  check('Essence is honest about not being used yet', /not used in combat yet/.test(slotLore(15)), slotLore(15));
  // Stats is opened from the Player Menu, so the held item there is the head; check with the sword in hand.
  await cmd(`/cerb skill ${NAME} Blade_Mastery level 50`);
  if (bot.currentWindow) { bot.closeWindow(bot.currentWindow); await sleep(300); }
  bot.setQuickBarSlot(heldSlot); await sleep(300);
  await cmd('/stats', 1500);
  const viaCommand = /Your Stats/.test(title());
  check('/stats opens the page', viaCommand, title());
  if (!viaCommand) return;
  check('A level change shows immediately (Blade Mastery 50 -> Power 50, x3.50)', /Swords: 50\.0 +-> +x3\.50 damage/.test(slotLore(10)), slotLore(10));
  check('Weapon tile reads the held sword', /Holding: Netherite Sword.*Power: 50\.0 +-> +x3\.50 damage.*Keeps pace with level-51 mobs/.test(slotLore(29)), slotLore(29));
  check('Next-level tile previews Blade Mastery 51', /Blade Mastery 50 -> 51:.*x3\.50 -> x3\.55/.test(slotLore(38)), slotLore(38));
  check('Kill XP tile lists XP per mob level', /Trains: Blade Mastery \(level 50\).*Level 1: 10 .*reduced.*Level 50: 334/.test(slotLore(40)), slotLore(40));
  await click(45, 1200);
  check('Back returns to the Player Menu', await waitTitle(/Player Menu/), title());
  if (bot.currentWindow) bot.closeWindow(bot.currentWindow);
  await cmd(`/cerb skill ${NAME} Blade_Mastery level 1`);
}

async function survive() {
  await cmd('/gamemode survival');
  await cmd('/difficulty normal');
  await cmd('/time set day');
  const out = {};
  for (const lv of [1, 100]) {
    for (const sk of ['Survival', 'Heavy_Armor_Training', 'First_Aid']) await cmd(`/cerb skill ${NAME} ${sk} level ${lv}`, 500);
    await sleep(800);
    await cmd(`/cerb heal ${NAME}`, 800);
    await cmd('/stats', 1500);
    const lore = slotLore(31);
    if (bot.currentWindow) bot.closeWindow(bot.currentWindow);
    const maxHp = Number((lore.match(/Max health: ([\d.]+)/) || [])[1]);
    await cmd('/kill @e[type=zombie,distance=..16]', 600);
    await cmd('/cerb combatdebug', 500);
    const at = chat.length;
    await cmd('/cerb spawn zombie 50', 500);
    const t0 = Date.now();
    while (Date.now() - t0 < 25000 && chat.slice(at).filter(m => /\[Combat\].*Final/.test(m)).length < 3) await sleep(250);
    await cmd('/cerb combatdebug', 500);
    await cmd('/kill @e[type=zombie,distance=..16]', 600);
    const traces = chat.slice(at).filter(m => /\[Combat\].*Final/.test(m)).slice(0, 3);
    const finals = traces.map(t => Number((t.match(/Final: ([\d.]+)/) || [])[1]));
    const avg = finals.reduce((a, b) => a + b, 0) / Math.max(1, finals.length);
    // Switching away from an item with metadata (the Player Menu head) used to reset max health to 100.
    bot.setQuickBarSlot(8); await sleep(400); bot.setQuickBarSlot(0); await sleep(400);
    await cmd('/stats', 1500);
    const afterSwap = Number(((slotLore(31).match(/Max health: ([\d.]+)/)) || [])[1]);
    if (bot.currentWindow) bot.closeWindow(bot.currentWindow);
    out[lv] = { maxHp, avg, pct: avg / maxHp, traces, lore, afterSwap };
    console.log(`Defensive skills ${lv}: max HP ${maxHp}, HP lost per hit ${avg.toFixed(2)} (${(100 * avg / maxHp).toFixed(2)}% of max)`);
    console.log('   ' + (traces[0] || 'no trace'));
  }
  for (const sk of ['Survival', 'Heavy_Armor_Training', 'First_Aid']) await cmd(`/cerb skill ${NAME} ${sk} level 1`, 500);
  await cmd(`/cerb heal ${NAME}`, 500);
  await cmd('/gamemode creative');
  return out;
}

bot.once('spawn', async () => {
  await sleep(2500);
  try {
    if (phase === 'killxp') await killxp();
    else if (phase === 'stats') await stats();
    else if (phase === 'survive') {
      const r = await survive();
      check('Vitality: defensive skills 1 -> 100 take max health from 102 to 300', r[1].maxHp === 102 && r[100].maxHp === 300, `${r[1].maxHp} -> ${r[100].maxHp}`);
      check('Max health survives switching held items', r[100].afterSwap === 300, String(r[100].afterSwap));
      check('Fortitude shows in the totals (armor and resistances rose)', /Armor bonus: \+15\.[0-9]%/.test(r[100].lore), r[100].lore);
      check('Each zombie hit costs a much smaller share of health at level 100', r[100].pct < r[1].pct / 2.5,
          `${(100 * r[1].pct).toFixed(2)}% -> ${(100 * r[100].pct).toFixed(2)}% per hit`);
    }
    else if (phase === 'offswitch') {
      await cmd('/gamemode creative');
      await equip('netherite_sword');
      await cmd(`/cerb skill ${NAME} Blade_Mastery level 100`);
      const r = await killOne('zombie', 1);
      await cmd(`/cerb skill ${NAME} Blade_Mastery level 1`);
      check('PvE offense switched off: Blade Mastery 100 needs the old 6+ swings on a level-1 zombie', r.swings >= 6, r.swings + ' swings');
    }
    else if (phase === 'swings') {
      const { rows, trace } = await swings();
      const at = (bm, lvl) => rows.find(r => r.bm === bm && r.lvl === lvl).swings;
      console.log('\nSwings to kill (avg of 2)   zombie L1   zombie L50');
      for (const bm of [1, 50, 100]) console.log(`  Blade Mastery ${String(bm).padEnd(3)}            ${String(at(bm, 1)).padEnd(10)}  ${at(bm, 50)}`);
      check('The server applied Power to the hit (trace: Swords, Power 50 -> x3.50)', /Power\(Swords, Power 50\.0 -> x3\.50\)/.test(trace), trace);
      check('Blade Mastery 100 kills a level-1 zombie in far fewer swings than Blade Mastery 1', at(100, 1) <= at(1, 1) / 2, `BM1 ${at(1, 1)} vs BM100 ${at(100, 1)}`);
      check('Blade Mastery 100 kills a level-50 zombie in far fewer swings than Blade Mastery 1', at(100, 50) <= at(1, 50) / 3, `BM1 ${at(1, 50)} vs BM100 ${at(100, 50)}`);
      check('Equal-level fights take about the same swings (BM1 vs L1, BM50 vs L50)', Math.abs(at(50, 50) - at(1, 1)) <= 2, `${at(1, 1)} vs ${at(50, 50)}`);
    }
  } catch (e) { console.log('SCRIPT ERROR', e.stack); results.push(false); }
  const failed = results.filter(r => !r).length;
  console.log(`\n${results.length - failed}/${results.length} checks passed`);
  bot.quit(); await sleep(400); process.exit(failed ? 1 : 0);
});
setTimeout(() => { console.log('TIMEOUT'); process.exit(3); }, 600000);
