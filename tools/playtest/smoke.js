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
// The HUD comes as the dedicated action-bar packet (Paper's sendActionBar), which this mineflayer
// version doesn't turn into a message event: read it directly.
bot._client.on('action_bar', p => { try { actionBar = require('prismarine-chat')(bot.registry).fromNotch(p.text).toString(); } catch (e) { /* keep last */ } });
bot.on('kicked', r => { console.log('KICKED', JSON.stringify(r)); process.exit(2); });
bot.on('error', e => { console.log('ERROR', e.message); process.exit(2); });

async function cmd(c, wait = 1200) { const at = chat.length; bot.chat(c); await sleep(wait); return at; }
/** Blade Mastery's level and XP as the server has them (/cerb skill ... xp 0 adds nothing). */
async function bladeXp() {
  const at = await cmd(`/cerb skill ${NAME} Blade_Mastery xp 0`, 900);
  const m = (saw(/is now level \d+ \(\d+ XP\)/, at) || '').match(/level (\d+) \((\d+) XP\)/);
  return m ? { level: Number(m[1]), xp: Number(m[2]) } : { level: NaN, xp: NaN };
}
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

// Every phase starts at world spawn: other phases teleport the bot, and test mobs spawned
// inside a hillside suffocate mid-check.
async function goHome() {
  const s = bot.spawnPoint;
  if (s) { bot.chat(`/tp @s ${s.x + 0.5} ${s.y} ${s.z + 0.5}`); await new Promise(r => setTimeout(r, 1200)); }
}

bot.once('spawn', async () => {
  try {
    await sleep(3000);
    await goHome();
    // Mobs left over from other runs would hit us and block the /kill check (hit invulnerability).
    bot.chat('/kill @e[type=!player,type=!text_display,distance=..48]'); await sleep(800);
    // Leftovers from other test runs (weapons, mob drops) fill the hotbar; the horse check needs an empty hand.
    for (const junk of ['rotten_flesh', 'iron_ingot', 'leather', 'iron_sword', 'iron_axe', 'netherite_sword', 'netherite_axe', 'blaze_rod', 'carrot', 'potato'])
      { bot.chat('/clear @s ' + junk); await sleep(250); }
    // The world spawn can sit above the ground, so arriving there may cost a little fall damage: start full.
    bot.chat('/cerb heal ' + NAME); await sleep(1500);
    check('HUD action bar shows health and mana', /(\d+)\/\1♥/.test(actionBar) && /✎/.test(actionBar), actionBar);
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
    const bmBefore = await bladeXp();
    at = await cmd('/summon minecraft:zombie ~2 ~ ~ {NoAI:1b}', 2500);
    const zLabels = labelsNear();
    check('Spawned zombie gets an HP label', zLabels.some(l => /Zombie.*❤.*Lv\d+/.test(l)), zLabels.join(' | '));
    const killed = await killNearest(['zombie']);
    await sleep(1000);
    const hud = actionBar; // kill XP shows on the HUD (the real-time XP display), not in chat
    const bmAfter = await bladeXp();
    const gained = bmAfter.level > bmBefore.level || bmAfter.xp > bmBefore.xp;
    check('Sword kill grants Blade Mastery XP, shown on the HUD', killed && gained && /\+\d+ Blade Mastery/.test(hud),
        `L${bmBefore.level} ${bmBefore.xp} XP -> L${bmAfter.level} ${bmAfter.xp} XP; HUD ${hud}`);

    at = await cmd('/cerb spawn elite_zombie', 2500);
    const eLabels = labelsNear();
    check('/cerb spawn elite_zombie uses its RARE pool', !!saw(/Spawned elite_zombie \(Rare\)/, at) && eLabels.some(l => /K❤/.test(l)), eLabels.join(' | '));
    await cmd('/kill @e[type=zombie,distance=..16]', 800);

    at = await cmd('/spawncustomzombie 10', 2500);
    const bLabels = labelsNear();
    check('High Health Zombie spawns with a named BOSS-tier label', bLabels.some(l => /High Health Zombie.*K❤/.test(l)), bLabels.join(' | '));
    await cmd('/kill @e[type=zombie,distance=..16]', 800);

    at = await cmd('/cerb skill ' + NAME + ' Blade_Mastery level 25', 1500);
    check('/cerb skill sets a skill level', !!saw(/Blade Mastery is now level 25/, at), chat.slice(at).join(' / '));
    await cmd('/cerb skill ' + NAME + ' Blade_Mastery level 1', 1000);

    // On a cleared pad (sandbox only): next to spawn the ground two blocks over may be a step up,
    // and a horse summoned inside a block can't be mounted.
    const sp = bot.spawnPoint || bot.entity.position;
    const PX = Math.floor(sp.x) + 8, PY = Math.floor(sp.y) + 1, PZ = Math.floor(sp.z);
    await cmd(`/fill ${PX - 2} ${PY} ${PZ - 2} ${PX + 3} ${PY + 4} ${PZ + 6} air`, 600);
    await cmd(`/fill ${PX - 2} ${PY - 1} ${PZ - 2} ${PX + 3} ${PY - 1} ${PZ + 6} stone`, 600);
    await cmd(`/tp @s ${PX + 0.5} ${PY} ${PZ + 0.5} 0 0`, 1200);
    await cmd('/summon minecraft:horse ~ ~ ~2 {Tame:1b,NoAI:1b}', 2500);
    const horse = mobsNear(['horse'])[0];
    const horseLabel = () => labelsNear().some(l => /Horse/.test(l));
    check('A horse shows its HP label like any mob', horseLabel(), labelsNear().join(' | ') || 'no labels');
    bot.setQuickBarSlot(emptyHotbar());
    if (horse) { await bot.lookAt(horse.position.offset(0, 1, 0), true); bot.mount(horse); await sleep(2000); }
    check('Tamed horse can be mounted (its label steps out of the seat)', !!bot.vehicle && !horseLabel(), bot.vehicle ? bot.vehicle.name : 'not mounted');
    // Getting off is Sneak since 1.21.2 (this mineflayer's dismount() sends Jump, which a horse ignores or rears to).
    // This mineflayer also never clears bot.vehicle on 1.21.11, so the label is the evidence: it only comes back
    // once no player rides the horse.
    if (bot.vehicle) { bot.setControlState('sneak', true); await sleep(600); bot.setControlState('sneak', false); await sleep(1000); }
    check('...and its label is back once you get off', horseLabel(), labelsNear().join(' | ') || 'no labels');
    await cmd('/summon minecraft:pig ~ ~ ~-2 {NoAI:1b}', 2500);
    check('A pig shows its HP label too', labelsNear().some(l => /Pig/.test(l)), labelsNear().join(' | ') || 'no labels');
    await cmd('/kill @e[type=horse,distance=..16]', 800);
    await cmd('/kill @e[type=pig,distance=..16]', 800);

    await cmd('/gamemode survival');
    at = await cmd('/kill @s', 3500);
    at = await cmd('/cerb healthstate');
    // Max HP is 100 x Vitality (102 for a fresh character), so check "full", not a fixed number.
    check('/kill -> instant respawn at full custom HP', !!saw(/Custom HP: ([\d.]+)\/\1\b/, at) && !!saw(/Lifecycle: ALIVE/, at), chat.slice(at).filter(m => /Custom HP|Lifecycle/.test(m)).join(' / '));
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
