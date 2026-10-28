// Lifesteal in real fights: the measured part of the lifesteal review (docs/design/LIFESTEAL_REVIEW.md, the owner's decision
// 6). Nothing here changes lifesteal: the control run switches it off on the SANDBOX only (attributes.yml limits.lifesteal: 0,
// written for the run and put back). LOCAL SANDBOX servers only; op the bot first; SERVER_DIR for the control.
// Usage: node lifesteal.js [levels=1,25,50,100] [seconds=40] [name] [crowd]
//   crowd: under pressure: four zombies at once, each ten levels above the fighter (one same-level zombie at a time
//   barely scratches a fighter in full iron, so it says little about sustain).
// For each level L a fighter (Blade Mastery, Weapon Mastery, Critical Strike, Survival and Heavy Armor Mastery at L, every
// other skill at 1, full iron, a netherite sword, survival) fights level-L zombies one after another for the given seconds,
// swinging at the sword's pace, healed to full at the start only. Measured from the combat traces and the HUD: zombies
// killed, damage dealt, lifesteal healed, damage taken, the lowest and last health, deaths; then the same fight with
// lifesteal off.
const fs = require('fs');
const path = require('path');
const mineflayer = require('mineflayer');
const LEVELS = (process.argv[2] || '1,25,50,100').split(',').map(Number);
const SECONDS = Number(process.argv[3] || 40);
const NAME = process.argv[4] || 'CerbLifesteal';
const CROWD = process.argv[5] === 'crowd';
const sleep = ms => new Promise(r => setTimeout(r, ms));
const chat = [];
const bars = [];
let bot, pchat;

function connect() {
  const b = mineflayer.createBot({ host: '127.0.0.1', port: Number(process.env.MC_PORT || 25565), username: NAME, version: '1.21.11', auth: 'offline' });
  b.on('messagestr', (m, pos) => { if (pos !== 'game_info') chat.push({ t: Date.now(), m }); });
  b._client.on('action_bar', p => { try { pchat = pchat || require('prismarine-chat')(b.registry); bars.push({ t: Date.now(), m: pchat.fromNotch(p.text).toString() }); } catch (e) { /* skip */ } });
  b.on('kicked', r => { console.log('KICKED', r); process.exit(2); });
  return new Promise(r => b.once('spawn', () => r(b)));
}
async function cmd(c, wait = 500) { bot.chat(c); await sleep(wait); }
/** The HUD's health "103/109♥", or null. */
function health(m) { const x = m.replace(/,/g, '').match(/([\d.]+)\/([\d.]+)♥/); return x ? { hp: Number(x[1]), max: Number(x[2]) } : null; }

async function arena() {
  await cmd('/tp @s 2000.5 -60 300.5', 2000);
  await cmd('/fill 1986 -60 286 2014 -40 314 air', 1200);
  await cmd('/fill 1986 -61 286 2014 -61 314 grass_block', 900);
  await cmd('/kill @e[type=!player,distance=..64]', 700);
  await cmd('/tp @s 2000.5 -60 300.5 180 0', 600);
}

async function character(level) {
  for (const s of ['Blade_Mastery', 'Weapon_Mastery', 'Critical_Strike', 'Survival', 'Heavy_Armor_Training']) await cmd(`/cerb skill ${NAME} ${s} level ${level}`, 250);
  await cmd('/clear @s', 300);
  // No /item command on the sandbox's Folia build: each piece is given and put on by a click into its slot.
  for (const [dest, piece] of [['head', 'helmet'], ['torso', 'chestplate'], ['legs', 'leggings'], ['feet', 'boots']]) {
    await cmd(`/give @s iron_${piece}`, 400);
    const it = bot.inventory.items().find(x => x.name === `iron_${piece}`);
    if (it) { try { await bot.equip(it, dest); } catch (e) { console.log('      equip ' + piece + ': ' + e.message); } }
  }
  await cmd('/give @s netherite_sword', 400);
  const sword = bot.inventory.items().find(x => x.name === 'netherite_sword');
  if (sword) { try { await bot.equip(sword, 'hand'); } catch (e) { console.log('      equip sword: ' + e.message); } }
  await sleep(1500); // the attribute snapshot and the health pool settle
  await cmd(`/cerb heal ${NAME}`, 600);
}

/** One fight: level-{@code level} zombies one after another, the bot swinging at the sword's pace. */
async function fight(level) {
  await cmd('/kill @e[type=!player,distance=..64]', 500);
  await cmd(`/cerb heal ${NAME}`, 600);
  const t0 = Date.now(), from = chat.length;
  let deaths = 0, low = Infinity, last = null, spawned = 0;
  const onDeath = () => { deaths++; };
  bot.on('death', onDeath);
  let seen = 0;
  while (Date.now() - t0 < SECONDS * 1000) {
    if (deaths > seen) { // back to the arena after a respawn, healed: the fight goes on and the deaths are counted
      seen = deaths; await sleep(1500);
      await cmd('/tp @s 2000.5 -60 300.5 180 0', 800); await cmd(`/cerb heal ${NAME}`, 400);
    }
    const near = Object.values(bot.entities).filter(e => e.name === 'zombie' && e.position.distanceTo(bot.entity.position) < 8)
      .sort((a, b) => a.position.distanceTo(bot.entity.position) - b.position.distanceTo(bot.entity.position));
    if (CROWD && near.length < 4) { await cmd(`/cerb spawn zombie ${level + 10}`, 250); spawned++; if (near.length === 0) continue; }
    let z = near[0];
    if (!z) { await cmd(`/cerb spawn zombie ${level}`, 700); spawned++; continue; }
    await bot.lookAt(z.position.offset(0, 1.5, 0), true);
    if (z.position.distanceTo(bot.entity.position) < 3.2) bot.attack(z);
    const h = bars.length ? health(bars[bars.length - 1].m) : null;
    if (h) { low = Math.min(low, h.hp / h.max); last = h; }
    await sleep(650);
  }
  bot.removeListener('death', onDeath);
  await cmd('/kill @e[type=zombie,distance=..64]', 500);
  const traces = chat.slice(from).map(x => x.m).filter(m => m.startsWith('[Combat]'));
  // A hit on a zombie: "[Combat] ... Power(...): a → Crit(...): b → Lifesteal(p%): h"; a hit taken: "[Combat] Incoming: x →
  // ... → Final: y" (y reached the health pool, after armor and the defense bar).
  let healed = 0, dealt = 0, hits = 0, taken = 0, struck = 0, pct = NaN;
  for (const t of traces) {
    if (/Incoming: /.test(t)) { const f = t.match(/Final: ([\d.]+)/); if (f) { taken += Number(f[1]); struck++; } continue; }
    if (!/Power\(/.test(t)) continue;
    hits++;
    const ls = t.match(/Lifesteal\(([\d.]+)%\): ([\d.]+)/);
    if (ls) { healed += Number(ls[2]); pct = Number(ls[1]); }
    const step = t.replace(/^\[Combat\] /, '').split(' → ').filter(x => /^(Power|Crit|Bonus)\(/.test(x)).pop();
    const v = step && step.match(/: ([\d.]+)$/);
    if (v) dealt += Number(v[1]);
  }
  return { level, lifesteal: pct, zombies: spawned, hits, dealt: Math.round(dealt), healed: Math.round(healed), struck, taken: Math.round(taken),
    lowest: Math.round(low * 1000) / 10 + '%', last: last ? `${last.hp}/${last.max}` : '?', deaths };
}

async function withLifestealOff(body) {
  const dir = process.env.SERVER_DIR;
  if (!dir) { console.log('NO SERVER_DIR: the control run is skipped'); return null; }
  const file = path.join(dir, 'plugins', 'CerberusPlugin', 'attributes.yml');
  const original = fs.readFileSync(file, 'utf8');
  const edited = original.replace(/^(\s*lifesteal:\s*)[\d.]+/m, '$10.0');
  if (edited === original) { console.log('limits.lifesteal not found in the sandbox attributes.yml: the control run is skipped'); return null; }
  try {
    fs.writeFileSync(file, edited);
    await cmd('/cerb reload', 2500);
    return await body();
  } finally {
    fs.writeFileSync(file, original);
    await cmd('/cerb reload', 2500);
  }
}

(async () => {
  bot = await connect();
  await sleep(2500);
  await cmd('/gamerule spawn_mobs false', 300);
  await cmd('/gamemode survival', 300);
  await cmd('/time set midnight', 300);
  await cmd('/difficulty normal', 300);
  await arena();
  if (!chat.slice(-20).some(x => /Combat debug: ON/.test(x.m))) await cmd('/cerb combatdebug', 600);
  const rows = [];
  for (const level of LEVELS) {
    await character(level);
    const on = await fight(level);
    const off = await withLifestealOff(async () => { await character(level); return fight(level); });
    rows.push({ on, off });
    console.log(`LEVEL ${level} ON  ${JSON.stringify(on)}`);
    console.log(`LEVEL ${level} OFF ${JSON.stringify(off)}`);
  }
  await cmd('/cerb combatdebug', 500);
  for (const s of ['Blade_Mastery', 'Weapon_Mastery', 'Critical_Strike', 'Survival', 'Heavy_Armor_Training']) await cmd(`/cerb skill ${NAME} ${s} level 1`, 200);
  console.log('\nRESULT ' + JSON.stringify(rows));
  bot.quit();
  setTimeout(() => process.exit(0), 500);
})();
