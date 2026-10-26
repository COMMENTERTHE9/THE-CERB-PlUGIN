// Floating mob labels never outlive their mob (the "ghost label" bug the owner found, 2026-10-26).
// LOCAL SANDBOX servers only; the bot must be op (op CerbLabel).  Usage: node labels.js [phase|all] [botName]
//   idle   - untamed horses and llamas keep their labels for 20 s (they used to buck them off as a rider being tamed,
//            and the label stayed floating where it fell; killing the horse later left it there for good)
//   kills  - horses, llamas, pigs, cows and zombies killed by /kill, by lethal hits through the health pool, and right
//            after a right-click (the label steps aside): no label left behind, by the server's count and the client's
//   ride   - an untamed horse wearing its label has its bucking paused; it can still be mounted, and while ridden its
//            bucking goal is back (so taming works as in vanilla); its label and the pause return when the rider gets
//            off. (This bot is never bucked off, labelled or not: a name-tagged horse that never had a label doesn't
//            buck it either, so the check is the goal itself, read from the server.)
//   breed  - two tamed horses fed golden carrots breed (a label used to block it: vanilla won't breed a horse that
//            carries anything), and all three wear labels afterwards
//   unload - labelled horses in chunks that unload and load again: one label each, no orphans
// The server's count comes from /cerb labels (labels within a radius, how many ride a live mob, orphans).
const mineflayer = require('mineflayer');
const phase = process.argv[2] || 'all';
const NAME = process.argv[3] || 'CerbLabel';
const sleep = ms => new Promise(r => setTimeout(r, ms));
const chat = [];
const results = [];
const check = (name, ok, detail = '') => { results.push(ok); console.log(`${ok ? 'PASS' : 'FAIL'}  ${name}${detail ? '  (' + detail + ')' : ''}`); };
const saw = (re, since) => chat.slice(since).find(m => re.test(m));
const bot = mineflayer.createBot({ host: '127.0.0.1', port: 25565, username: NAME, version: '1.21.11', auth: 'offline' });
bot.on('messagestr', (m, pos) => { if (pos !== 'game_info') chat.push(m); });
bot.on('kicked', r => { console.log('KICKED', JSON.stringify(r)); process.exit(2); });
bot.on('error', e => { console.log('ERROR', e.message); process.exit(2); });

async function cmd(c, wait = 700) { const at = chat.length; bot.chat(c); await sleep(wait); return at; }

// ── A glass pen far from spawn, fresh for each phase (or an open field: an untamed horse needs room to run) ──
let X = 0, Y = 0, Z = 0;
async function arena(off, open = false) {
  const sp = bot.spawnPoint || bot.entity.position;
  X = Math.floor(sp.x) + off; Y = 120; Z = Math.floor(sp.z) - 400;
  await cmd('/gamemode creative', 300);
  await cmd(`/tp @s ${X + 0.5} ${Y + 1} ${Z + 0.5}`, 1000);
  try { await bot.waitForChunksToLoad(); } catch (e) { /* the fill below retries */ }
  for (let i = 0; i < 20; i++) { // fresh terrain: wait until the server has the chunks loaded
    const a = await cmd(open ? `/fill ${X - 20} ${Y - 1} ${Z - 20} ${X + 20} ${Y - 1} ${Z + 20} grass_block`
        : `/fill ${X - 8} ${Y - 1} ${Z - 8} ${X + 8} ${Y + 4} ${Z + 8} glass hollow`, 700);
    if (!saw(/not loaded/i, a)) break;
    await sleep(1000);
  }
  if (open) await cmd(`/fill ${X - 20} ${Y} ${Z - 20} ${X + 20} ${Y + 6} ${Z + 20} air`, 600);
  await cmd('/gamerule spawn_mobs false', 300);
  await cmd(`/tp @s ${X + 0.5} ${Y} ${Z + 0.5}`, 600);
  await cmd('/kill @e[type=!player,distance=..30]', 800);
  await cmd('/clear @s', 300);
}
const summon = async (type, dx, dz, nbt = '') => cmd(`/summon ${type} ${X + 0.5 + dx} ${Y} ${Z + 0.5 + dz}${nbt ? ' ' + nbt : ''}`, 250);
/** A level-5 mob at the bot (far from spawn a summoned mob's level, and health, would outgrow a test hit). */
const spawn5 = async type => cmd(`/cerb spawn ${type} 5`, 250);
const near = (name, r = 12) => Object.values(bot.entities).filter(e => e.name === name && e.position.distanceTo(bot.entity.position) < r);

/** The server's count: labels within r blocks, how many ride a live mob, and orphans. */
async function census(r = 24) {
  const a = await cmd(`/cerb labels ${r}`, 900);
  const line = saw(/labels \d+, on a live mob \d+, orphans \d+/, a) || '';
  const m = line.match(/labels (\d+), on a live mob (\d+), orphans (\d+)/);
  const h = line.match(/horse-likes (\d+), untamed (\d+), bucking paused (\d+)/);
  return m ? { total: +m[1], live: +m[2], orphans: +m[3], untamed: h ? +h[2] : NaN, paused: h ? +h[3] : NaN }
      : { total: NaN, live: NaN, orphans: NaN, untamed: NaN, paused: NaN };
}
/** What the client sees: labels that ride nothing alive (a vanilla client draws those where they were left). */
function clientGhosts(r = 24) {
  return Object.values(bot.entities).filter(e => e.name === 'text_display' && e.position.distanceTo(bot.entity.position) < r
      && !(e.vehicle && bot.entities[e.vehicle.id] === e.vehicle)).length;
}
const show = c => `server: ${c.total} labels, ${c.live} on a live mob, ${c.orphans} orphans`;

async function idle() {
  await arena(700, true); // in a closed pen an untamed horse has nowhere to run, and never bucks
  for (let i = 0; i < 5; i++) await summon('horse', (i - 2) * 2.5, -3);
  for (let i = 0; i < 2; i++) await summon('llama', (i - 0.5) * 3, 3);
  await sleep(2500);
  const c0 = await census(40);
  await sleep(20000);
  const c1 = await census(40);
  check('Untamed horses and llamas keep their labels for 20 s', c1.live === 7 && c1.orphans === 0, `start ${show(c0)}; after 20 s ${show(c1)}`);
}

async function killRound(label, kill) {
  const kinds = [['horse', -4, -3], ['horse', -1, -3], ['horse', 2, -3], ['llama', 5, -3], ['pig', -4, 3], ['pig', -1, 3], ['cow', 2, 3], ['zombie', 5, 3], ['zombie', 0, 5]];
  await cmd('/gamemode creative', 300);
  for (const [t] of kinds) await spawn5(t);
  await sleep(2500);
  const c0 = await census();
  await kill();
  await sleep(3000); // the death animation, then removal
  const c1 = await census();
  const g = clientGhosts();
  check(`${label}: no label left behind`, c0.live === kinds.length && c1.total === 0 && g === 0,
      `before: ${show(c0)}; after: ${show(c1)}, client ghosts ${g}`);
  await cmd('/kill @e[type=!player,distance=..30]', 600);
}

async function kills() {
  await arena(760);
  await killRound('Killed by /kill', async () => { await cmd('/kill @e[type=!player,type=!text_display,distance=..20]', 600); });
  await killRound('Killed by lethal hits through the health pool', async () => {
    // A dying mob stays targetable for a second: one at a time, each after the last one's death animation.
  for (let i = 0; i < 9; i++) await cmd('/damage @e[type=!player,type=!text_display,limit=1,sort=nearest,distance=..20] 100000 minecraft:player_attack by @s', 1200);
  });
  await killRound('Killed right after a right-click (the label is stepping aside)', async () => {
    await cmd('/gamemode survival', 300);
    for (let i = 0; i < 9; i++) {
      const m = Object.values(bot.entities).filter(e => ['horse', 'llama', 'pig', 'cow', 'zombie'].includes(e.name) && e.position.distanceTo(bot.entity.position) < 20)
          .sort((a, b) => a.position.distanceTo(bot.entity.position) - b.position.distanceTo(bot.entity.position))[0];
      if (!m) break;
      try { bot.activateEntity(m); } catch (e) { /* the server decides */ }
      await sleep(30);
      await cmd('/damage @e[type=!player,type=!text_display,limit=1,sort=nearest,distance=..20] 100000 minecraft:player_attack by @s', 1200);
    }
  });
}

async function ride() {
  await arena(820);
  await summon('horse', 0, 2.5);
  await sleep(2500);
  const c0 = await census();
  check('An untamed horse wearing its label has its bucking paused', c0.live === 1 && c0.untamed === 1 && c0.paused === 1, JSON.stringify(c0));
  await cmd('/gamemode survival', 300);
  const h = near('horse')[0];
  let mounted = false;
  bot.on('mount', () => { mounted = true; });
  if (h) { await bot.lookAt(h.position.offset(0, 1, 0), true); try { bot.activateEntity(h); } catch (e) { /* the server decides */ } }
  for (let i = 0; i < 30 && !mounted; i++) await sleep(100);
  check('An untamed horse can still be mounted (its label steps aside)', mounted);
  const cr = await census();
  check('...and while it is ridden its bucking is back', cr.untamed === 1 && cr.paused === 0, JSON.stringify(cr));
  await sleep(2000);
  bot.setControlState('sneak', true); // since 1.21.2 you get off with Sneak (this mineflayer's dismount() jumps)
  await sleep(1500);
  bot.setControlState('sneak', false);
  await sleep(1500);
  const c = await census();
  // Read from the server: a label only comes back once nobody rides (this mineflayer never reports its own dismount).
  check('...and its label and the pause are back once the rider is off', c.live === 1 && c.orphans === 0 && c.paused === 1, JSON.stringify(c));
}

async function breed() {
  await arena(880);
  await summon('horse', -1, 2, '{Tame:1b}');
  await summon('horse', 1, 2, '{Tame:1b}');
  await sleep(2500);
  const c0 = await census();
  await cmd('/gamemode survival', 300);
  await cmd('/give @s golden_carrot 8', 700);
  const slot = bot.inventory.slots.findIndex((it, i) => it && it.name === 'golden_carrot' && i >= 36 && i <= 44);
  if (slot >= 0) bot.setQuickBarSlot(slot - 36);
  await sleep(300);
  for (const h of near('horse')) { await bot.lookAt(h.position.offset(0, 1, 0), true); try { bot.activateEntity(h); } catch (e) { /* the server decides */ } await sleep(700); }
  let n = 0;
  for (let i = 0; i < 40 && n < 3; i++) { await sleep(500); n = near('horse').length; }
  check('Two tamed horses fed golden carrots breed (a label used to block it)', n === 3, `horses ${n}; at the start ${show(c0)}`);
  await sleep(4000); // love ends with the birth; the parents' labels come back within 2 s
  const c1 = await census();
  check('...and all three wear their labels afterwards', c1.live === 3 && c1.orphans === 0, show(c1));
}

async function unload() {
  await arena(940);
  for (let i = 0; i < 3; i++) await summon('horse', (i - 1) * 3, 2);
  await sleep(2500);
  const c0 = await census();
  await cmd('/gamemode creative', 300);
  await cmd(`/tp @s ${X + 3000} ${Y} ${Z}`, 1000); // far enough that the pen's chunks unload
  await sleep(25000);
  await cmd(`/tp @s ${X + 0.5} ${Y} ${Z + 0.5}`, 1500);
  try { await bot.waitForChunksToLoad(); } catch (e) { /* counted below */ }
  await sleep(4000);
  const c1 = await census();
  check('Horses in chunks that unload and load again: one label each, no orphans', c1.total === 3 && c1.live === 3 && c1.orphans === 0,
      `before: ${show(c0)}; after: ${show(c1)}`);
}

bot.once('spawn', async () => {
  try {
    await sleep(1500);
    const phases = { idle, kills, ride, breed, unload };
    for (const [name, run] of Object.entries(phases)) {
      if (phase !== 'all' && phase !== name) continue;
      console.log('== ' + name);
      await run();
    }
    await cmd('/kill @e[type=!player,distance=..30]', 500);
    await cmd('/gamemode creative', 300);
  } catch (e) { console.log('ERROR', e.stack || e.message); results.push(false); }
  const ok = results.filter(Boolean).length;
  console.log(`\n${ok}/${results.length} passed`);
  bot.quit();
  setTimeout(() => process.exit(ok === results.length ? 0 : 1), 500);
});
