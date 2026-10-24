// Headless in-game smoke test for Cerberus. For a LOCAL SANDBOX server only:
// it needs online-mode=false and the bot made operator from the server console:
//   op CerbSmoke
// Usage: node smoke.js [host] [port]      (defaults 127.0.0.1 25565)
// Prints PASS/FAIL per check and exits non-zero if any check fails.
const mineflayer = require('mineflayer');
const [host = '127.0.0.1', port = '25565'] = process.argv.slice(2);
const NAME = 'CerbSmoke';
const sleep = ms => new Promise(r => setTimeout(r, ms));
const text = v => v ? [...JSON.stringify(v).matchAll(/"text":\{"type":"string","value":"([^"]*)"/g)].map(m => m[1]).join('') : '';

const chat = [];
let actionBar = '';
const results = [];
const check = (name, ok, detail = '') => { results.push(ok); console.log(`${ok ? 'PASS' : 'FAIL'}  ${name}${detail ? '  (' + detail + ')' : ''}`); };
const saw = (re, since) => chat.slice(since).find(m => re.test(m));

const bot = mineflayer.createBot({ host, port: Number(port), username: NAME, version: '1.21.11', auth: 'offline' });
bot.on('messagestr', (m, pos) => { if (pos === 'game_info') actionBar = m; else chat.push(m); });
bot.on('kicked', r => { console.log('KICKED', JSON.stringify(r)); process.exit(2); });
bot.on('error', e => { console.log('ERROR', e.message); process.exit(2); });

async function cmd(c, wait = 1200) { const at = chat.length; bot.chat(c); await sleep(wait); return at; }
const mobsNear = (names, r = 8) => Object.values(bot.entities).filter(e => names.includes(e.name) && e.position.distanceTo(bot.entity.position) < r);
const labelsNear = (r = 8) => Object.values(bot.entities).filter(e => e.name === 'text_display' && e.position.distanceTo(bot.entity.position) < r).map(e => text(e.metadata));
const emptyHotbar = () => { for (let i = 0; i < 8; i++) if (!bot.inventory.slots[36 + i]) return i; return 0; };

async function killNearest(names, maxSwings = 80) {
  for (let i = 0; i < maxSwings; i++) {
    const m = mobsNear(names, 5)[0];
    if (!m) return true;
    await bot.lookAt(m.position.offset(0, m.height * 0.6, 0), true);
    bot.attack(m);
    await sleep(650);
  }
  return mobsNear(names, 5).length === 0;
}

bot.once('spawn', async () => {
  try {
    await sleep(3000);
    check('HUD action bar shows health and mana', /\/100♥/.test(actionBar) && /✎/.test(actionBar), actionBar);
    const head = bot.inventory.slots[44];
    check('Player Menu head in hotbar slot 9', !!head && head.name === 'player_head' && /Player Menu/.test(text(head.customName)));

    let at = await cmd('/cerb healthstate');
    check('/cerb healthstate reports ALIVE + ACTIVE', !!saw(/Lifecycle: ALIVE/, at) && !!saw(/Session: ACTIVE/, at));
    if (!saw(/Lifecycle/, at)) console.log('      (is the bot op? run "op ' + NAME + '" in the server console)');

    bot.setQuickBarSlot(8); await sleep(300);
    const opened = new Promise(res => bot.once('windowOpen', w => res(text(w.title))));
    bot.activateItem();
    const title = await Promise.race([opened, sleep(3000).then(() => '')]);
    check('Right-click head opens Player Menu', /Player Menu/.test(title), title);
    if (bot.currentWindow) bot.closeWindow(bot.currentWindow);

    await cmd('/gamemode creative');
    await cmd('/kill @e[type=!player,distance=..32]', 1000);
    await cmd('/clear @s minecraft:iron_sword');
    await cmd('/give @s minecraft:iron_sword', 1200);
    const sw = bot.inventory.slots.findIndex((it, i) => it && it.name === 'iron_sword' && i >= 36 && i <= 44);
    if (sw >= 0) bot.setQuickBarSlot(sw - 36);
    await sleep(300);
    at = await cmd('/summon minecraft:zombie ~2 ~ ~ {NoAI:1b}', 2500);
    const zLabels = labelsNear();
    check('Spawned zombie gets an HP label', zLabels.some(l => /Zombie.*❤.*Lv\d+/.test(l)), zLabels.join(' | '));
    const killed = await killNearest(['zombie']);
    await sleep(1000);
    const xpMsg = saw(/^\+(\d+) Blade Mastery XP$/, at);
    const xp = xpMsg ? Number(xpMsg.match(/\d+/)[0]) : NaN;
    check('Sword kill grants bounded Blade Mastery XP', killed && xp > 0 && xp < 10000, xpMsg || 'no XP message');

    at = await cmd('/cerb spawn elite_zombie', 2500);
    const eLabels = labelsNear();
    check('/cerb spawn elite_zombie uses its RARE pool', !!saw(/Spawned elite_zombie \(Rare\)/, at) && eLabels.some(l => /K❤/.test(l)), eLabels.join(' | '));
    await cmd('/kill @e[type=zombie,distance=..16]', 800);

    await cmd('/summon minecraft:horse ~2 ~ ~ {Tame:1b,NoAI:1b}', 2500);
    const horse = mobsNear(['horse'])[0];
    bot.setQuickBarSlot(emptyHotbar());
    if (horse) { await bot.lookAt(horse.position.offset(0, 1, 0), true); bot.mount(horse); await sleep(2000); }
    check('Tamed horse can be mounted (no label in the seat)', !!bot.vehicle, bot.vehicle ? bot.vehicle.name : 'not mounted');
    if (bot.vehicle) { bot.dismount(); await sleep(500); }
    await cmd('/kill @e[type=horse,distance=..16]', 800);

    await cmd('/gamemode survival');
    at = await cmd('/kill @s', 3500);
    at = await cmd('/cerb healthstate');
    check('/kill -> instant respawn at full custom HP', !!saw(/Custom HP: 100\.00\/100\.00/, at) && !!saw(/Lifecycle: ALIVE/, at));
  } catch (e) {
    console.log('SCRIPT ERROR', e.stack);
    results.push(false);
  }
  const failed = results.filter(r => !r).length;
  console.log(`\n${results.length - failed}/${results.length} checks passed`);
  bot.quit();
  await sleep(500);
  process.exit(failed ? 1 : 0);
});
setTimeout(() => { console.log('TIMEOUT'); process.exit(3); }, 180000);
