// Fishing in game (docs/design/FISHING.md). LOCAL SANDBOX servers only; the bot (and CerbTarget, for the phases that need a
// second player) must be op. Usage: node fishing.js [phase[,phase...]] [botName]
//   basic     - ordinary fishing in survival with a Lure III rod: every catch pays Fishing XP of a configured amount; species
//               of the water replace some fish (the first is a discovery with its bonus); /fishing shows the journal
//   waters    - the water at the bobber: a lake (any other biome), a river, a swamp, temperate, warm and cold seas
//               (/fillbiome), and an underground pool (no sky, below sea level); a river's own species bite there
//   force     - admin forced catches: a rare species (its XP and discovery), Cerberus treasure, a lure for a creature
//   call      - Sneak + cast with a lure: the creature surfaces at the bobber and the lure is spent only then; nobody else
//               can hurt it (CerbTarget's hits do nothing) and it fights only its owner; a second call during the fight
//               spends nothing; the owner's kill pays Fishing XP, its drops are its owner's alone (CerbTarget can't pick
//               them up), and its first defeat is recorded
//   refund    - a creature that leaves without dying gives its lure back: its owner logs out, goes too far, or dies
//   limits    - the server cap (calls.server set to 1 in the sandbox's resources.yml for the phase; SERVER_DIR=...):
//               CerbTarget's call is refused and spends nothing while the bot's creature is out
//   afk       - automation: three minutes of cast, wait, reel, again with nothing else in between pay the same XP per
//               catch at the end as at the start, with no inactivity notice
//   inventory - a full inventory: the catch falls to the ground, but its XP is paid and a lure is still banked
//   economy   - Fortune (fish) at Fishing 100 adds extra fish, never extra treasure; a species cooks like any fish; a
//               creature's drop can't be placed; a rod upgrade (Scaled grip) makes bites sooner, once per rod
//   creative  - creative mode: vanilla catches, no Fishing XP, no species, no lures
//   persist-set / persist-check - the journal (species, lures) survives a restart (the caller restarts in between)
//   restart-set / restart-check - a creature out when the server stops: its lure is back at the next join
//   control   - on a build without Fishing (expected to show the old behaviour): catches pay no Fishing XP and spawn
//               "Sea Monster" guardians
//   all       - basic, waters, force, call, refund, inventory, economy, creative
const mineflayer = require('mineflayer');
const { Vec3 } = require('vec3');
const fs = require('fs');
const path = require('path');
const phaseArg = process.argv[2] || 'all';
const NAME = process.argv[3] || 'CerbFisher';
const sleep = ms => new Promise(r => setTimeout(r, ms));
const chat = [];
const bars = [];
const results = [];
const failed = [];
const check = (name, ok, detail = '') => {
  results.push(ok);
  if (!ok) failed.push(name);
  console.log(`${ok ? 'PASS' : 'FAIL'}  ${name}${detail ? '  (' + detail + ')' : ''}`);
};
const note = s => console.log('      ' + s);
let bot, pchat, target;
function connect(name = NAME) {
  const b = mineflayer.createBot({ host: '127.0.0.1', port: 25565, username: name, version: '1.21.11', auth: 'offline' });
  if (name === NAME) {
    b.on('messagestr', (m, pos) => { if (pos !== 'game_info') chat.push(m); });
    b._client.on('action_bar', p => {
      try { pchat = pchat || require('prismarine-chat')(b.registry); bars.push({ t: Date.now(), m: pchat.fromNotch(p.text).toString() }); } catch (e) { /* ignore */ }
    });
    bot = b;
  }
  b.on('kicked', r => { console.log('KICKED', name, r); if (!serverGoing) process.exit(2); });
  return new Promise(r => b.once('spawn', () => r(b)));
}
async function cmd(c, wait = 600) { const at = chat.length; bot.chat(c); await sleep(wait); return at; }
const saw = (re, since) => chat.slice(since).find(m => re.test(m));
const sawAll = (re, since) => chat.slice(since).filter(m => re.test(m));
const hudSince = t => [...new Set(bars.filter(b => b.t >= t).map(b => b.m))];

// ── skills and the journal ───────────────────────────────────────
const xpForLevel = l => Math.round(75 * Math.pow(Math.max(1, l), 1.85) + 40 * Math.max(1, l));
const totalXp = s => { let t = s.xp; for (let l = 1; l < s.level; l++) t += xpForLevel(l); return t; };
async function skill(name, who = NAME) {
  const at = await cmd(`/cerb skill ${who} ${name} xp 0`, 600);
  const m = (saw(/is now level \d+ \(\d+ XP\)/, at) || '').match(/level (\d+) \((\d+) XP\)/);
  return m ? { level: Number(m[1]), xp: Number(m[2]) } : { level: NaN, xp: NaN };
}
const fishXp = async () => totalXp(await skill('Fishing'));
async function setLevel(name, level, who = NAME) { await cmd(`/cerb skill ${who} ${name} level ${level}`, 500); }
/** The admin view of a journal: banked encounters (lures), defeated creatures, the fight in progress, species lines. */
async function journal(who = NAME) {
  const at = await cmd(`/cerb fish journal ${who}`, 1200);
  const lines = chat.slice(at);
  const l = (lines.find(m => /^(Lures|Encounters): /.test(m)) || ''); // "Encounters" since docs/design/FISHING_EQUIPMENT.md
  const lures = {};
  for (const m of l.matchAll(/(\w+)=(\d+)/g)) lures[m[1]] = Number(m[2]);
  const defeated = ((l.match(/defeated: \[([^\]]*)\]/) || [])[1] || '').split(',').map(s => s.trim()).filter(Boolean);
  const fight = (l.match(/fight: (.*)$/) || [])[1] || '';
  const found = lines.filter(m => /✔/.test(m));
  return { lures, defeated, fight: fight === 'none' ? null : fight, found, lines };
}
async function stats() {
  const at = await cmd('/cerb fish stats', 800);
  const lines = chat.slice(at).join(' | ');
  const out = (lines.match(/creatures out now (\d+)/) || [])[1];
  return { text: lines, out: out === undefined ? NaN : Number(out) };
}

// ── items ────────────────────────────────────────────────────────
function itemName(it) {
  if (!it || !it.customName) return null;
  try { return require('prismarine-chat')(bot.registry).fromNotch(it.customName).toString(); } catch (e) { return String(it.customName); }
}
const loreOf = it => (it && it.customLore ? it.customLore.map(l => {
  try { return require('prismarine-chat')(bot.registry).fromNotch(l).toString(); } catch (e) { return String(l); }
}).join(' | ') : '');
const inv = () => bot.inventory.slots.filter(Boolean);
const count = (pred) => inv().filter(pred).reduce((n, it) => n + it.count, 0);
const named = name => count(it => (itemName(it) || '').includes(name));
async function hold(item, give = true) {
  if (give) { await cmd('/clear @s ' + item.replace(/\[.*$/, ''), 400); await cmd('/give @s ' + item, 900); }
  const base = item.replace(/\[.*$/, '');
  const i = bot.inventory.slots.findIndex((it, s) => it && it.name === base && s >= 36 && s <= 44);
  if (i < 0) return -1;
  bot.setQuickBarSlot(i - 36);
  await sleep(300);
  return i - 36;
}
const ROD = 'fishing_rod[enchantments={lure:3}]';

// ── the arena: a pond in the sky, 7x7 and 3 deep, open to the sky, with a stone edge (open water) ──
let X = 0, Y = 120, Z = 0, SP = null;
async function arena(dx = 0, depth = 3) {
  SP = SP || bot.spawnPoint || bot.entity.position;
  X = Math.floor(SP.x) - 600 + dx; Z = Math.floor(SP.z) + 600;
  // Go there first: Folia changes blocks only in the region the player is in (a /fill from spawn does nothing there).
  await cmd(`/tp @s ${X + 0.5} ${Y + 2} ${Z + 0.5}`, 2500);
  await cmd(`/fill ${X - 6} ${Y - 5} ${Z - 3} ${X + 6} ${Y - 1} ${Z + 12} stone`, 500);
  await cmd(`/fill ${X - 6} ${Y} ${Z - 3} ${X + 6} ${Y + 6} ${Z + 12} air`, 500);
  await cmd(`/fill ${X - 3} ${Y - depth} ${Z + 2} ${X + 3} ${Y - 1} ${Z + 8} water`, 500);
  await cmd('/time set day', 300);
  await cmd('/weather clear', 300);
  await stand();
  await clearMobs();
}
async function stand(dx = 0, dz = 0) {
  await cmd(`/tp @s ${X + dx + 0.5} ${Y} ${Z + dz + 0.5} 0 20`, 800);
  try { await bot.lookAt(new Vec3(X + 0.5, Y - 1, Z + 5.5), true); } catch (e) { /* best effort */ }
}
async function clearMobs() {
  for (const t of ['drowned', 'bogged', 'stray', 'guardian', 'item']) await cmd(`/kill @e[type=${t},distance=..60]`, 250);
}
async function biome(b) { return cmd(`/fillbiome ${X - 6} ${Y - 5} ${Z - 3} ${X + 6} ${Y + 6} ${Z + 12} minecraft:${b}`, 900); }
async function survivalSetup(level) {
  await cmd('/gamemode survival', 400);
  await cmd('/effect give @s resistance infinite 4 true', 300);
  await cmd('/effect give @s saturation infinite 1 true', 300);
  await cmd('/clear @s', 400);
  if (level !== undefined) await setLevel('Fishing', level);
  await hold(ROD);
}

/** One natural catch (cast, wait for the bite, reel): true when something was reeled in. */
async function catchOne(timeoutMs = 45000) {
  try {
    await Promise.race([bot.fish(), new Promise((_, rej) => setTimeout(() => rej(new Error('timeout')), timeoutMs))]);
    await sleep(900); // the catch flies to the bot
    return true;
  } catch (e) {
    note('no catch: ' + (e.message || e));
    try { bot.activateItem(); await sleep(300); } catch (x) { /* reel in whatever is out */ }
    return false;
  }
}

/** Sneak and cast (a call), then wait for the creature's answer in chat. */
async function sneakCast(waitMs = 12000) {
  const at = chat.length, t = Date.now();
  bot.setControlState('sneak', true);
  await sleep(250);
  bot.activateItem();
  await sleep(120);
  bot.deactivateItem();
  await sleep(300);
  bot.setControlState('sneak', false);
  while (Date.now() - t < waitMs) {
    if (saw(/answers your call/, at)) return { at, t, answered: true };
    await sleep(300);
  }
  return { at, t, answered: false };
}
const creatureNear = (r = 24) => Object.values(bot.entities).filter(e => ['drowned', 'bogged', 'stray', 'guardian'].includes(e.name)
  && e.position.distanceTo(bot.entity.position) < r);
async function mobsHp(r = 24) {
  const at = await cmd(`/cerb mobs ${r}`, 800);
  return sawAll(/^(drowned|bogged|stray|guardian) .*hp /, at).map(l => { const m = l.match(/hp ([\d.]+)\/([\d.]+)/); return { line: l, cur: +m[1], max: +m[2] }; });
}
/** The owner attacks the nearest creature until it is gone (or the time runs out). */
async function fight(seconds = 60, stepAway = false) {
  await hold('netherite_sword');
  // stepAway: the moment the creature falls, the owner leaves its drops (before their half-second pickup delay ends).
  let fell = null;
  const onDead = e => {
    if (!stepAway || fell || !['drowned', 'bogged', 'stray', 'guardian'].includes(e.name)) return;
    fell = e.position.clone(); // to the arena's corner farthest from it
    bot.chat(`/tp @s ${fell.x > X + 0.5 ? X - 5.5 : X + 6.5} ${Y} ${fell.z > Z + 5 ? Z - 2.5 : Z + 12.5}`);
  };
  bot.on('entityDead', onDead);
  try { return await fightLoop(seconds, () => fell !== null); } finally { bot.removeListener('entityDead', onDead); fight.fell = fell; }
}
async function fightLoop(seconds, won) {
  const end = Date.now() + seconds * 1000;
  while (Date.now() < end) {
    if (won()) return true; // stepped away from it as it fell
    const c = creatureNear(30).sort((a, b) => a.position.distanceTo(bot.entity.position) - b.position.distanceTo(bot.entity.position))[0];
    if (!c) return true;
    const d = c.position.distanceTo(bot.entity.position);
    if (d > 3.2) { await cmd(`/tp @s ${c.position.x.toFixed(1)} ${(c.position.y + 0.2).toFixed(1)} ${(c.position.z - 1.5).toFixed(1)}`, 350); }
    try { await bot.lookAt(c.position.offset(0, 1.2, 0), true); bot.attack(c); } catch (e) { /* gone */ }
    await sleep(650);
  }
  return creatureNear(30).length === 0;
}

// ════════════════════════════════════════════════════════════════════

async function basic() {
  await arena();
  await biome('plains');
  await survivalSetup(1);
  await cmd('/cerb fish force ' + NAME + ' clear', 300); // no forced catch left from an earlier phase
  // A character that found Pond Bream in an earlier run checks the other side: no second discovery.
  const knewBream = (await journal()).found.some(l => /Pond Bream/.test(l));
  const before = await fishXp();
  const deltas = [];
  let last = before, at0 = chat.length, t0 = Date.now();
  for (let i = 0; i < 16; i++) {
    if (!(await catchOne())) continue;
    const now = await fishXp();
    deltas.push(now - last);
    last = now;
  }
  note('XP per catch: ' + deltas.join(', '));
  check('Every catch paid Fishing XP (16 casts)', deltas.length >= 12 && deltas.every(d => d >= 2), deltas.join(','));
  const allowed = new Set([2, 10, 12, 30, 12 + 50, 2 + 0, 25, 25 + 120]);
  check('Each catch paid a configured amount: junk 2, fish 10, treasure 30, a species 12 (+50 the first time)',
    deltas.every(d => allowed.has(d)), deltas.join(','));
  const bream = named('Pond Bream');
  check('Some fish came up as the water\'s own species (Pond Bream in a lake)', bream > 0, `Pond Bream ${bream}, other fish ${count(it => /cod|salmon|tropical|pufferfish/.test(it.name)) - bream}`);
  if (knewBream) check('Pond Bream was found in an earlier run: no discovery again, just its 12 XP', !saw(/New species: .*Pond Bream/, at0) && !deltas.includes(62),
    sawAll(/New species/, at0).join(' / ') || 'no discovery');
  else check('The first one was a discovery with its bonus', !!saw(/New species: .*Pond Bream/, at0) && deltas.includes(62), sawAll(/New species/, at0).join(' / '));
  const hud = hudSince(t0).filter(m => /Pond Bream|Fishing/.test(m));
  check('The HUD named the catch and its weight', hud.some(m => /Pond Bream .*\d+\.\d+ kg/.test(m)), hud.slice(0, 3).join(' / '));
  const at = await cmd('/fishing', 1200);
  check('/fishing shows the journal by water', !!saw(/Lakes and ponds: [1-4] of 4/, at) && !!saw(/species \d+ of 31/, at), chat.slice(at).join(' / ').slice(0, 240));
}

async function waters() {
  await arena(40);
  await survivalSetup(30);
  const cases = [['plains', 'Lakes and ponds'], ['river', 'Rivers'], ['swamp', 'Swamps'], ['ocean', 'Temperate seas'], ['warm_ocean', 'Warm seas'], ['frozen_ocean', 'Cold seas']];
  for (const [b, name] of cases) {
    const at = await biome(b);
    const ok = !saw(/Unknown|Incorrect|error|cannot/i, at);
    await stand();
    const at2 = await cmd('/cerb fish info', 900);
    const w = (saw(/^Water: /, at2) || '');
    check(`A ${b} pond is ${name}`, ok && w.includes(name) && w.includes(b), w);
  }
  // Underground: a covered pool below sea level.
  const u = { x: X + 30, y: 40, z: Z };
  await cmd(`/fill ${u.x - 4} ${u.y - 3} ${u.z - 4} ${u.x + 4} ${u.y + 4} ${u.z + 8} stone`, 600);
  await cmd(`/fill ${u.x - 3} ${u.y} ${u.z - 3} ${u.x + 3} ${u.y + 2} ${u.z + 7} air`, 600);
  await cmd(`/fill ${u.x - 2} ${u.y - 2} ${u.z + 2} ${u.x + 2} ${u.y - 1} ${u.z + 6} water`, 600);
  await cmd(`/tp @s ${u.x + 0.5} ${u.y} ${u.z + 0.5} 0 30`, 900);
  try { await bot.lookAt(new Vec3(u.x + 0.5, u.y - 1, u.z + 4.5), true); } catch (e) { /* best effort */ }
  const at3 = await cmd('/cerb fish info', 900);
  check('A covered pool below sea level is a cave pool', (saw(/^Water: /, at3) || '').includes('Cave pools'), saw(/^Water: /, at3) || 'none');
  // A river's own species bite in a river (level 30, by day: Silver Dace, River Perch, Goldfin Trout).
  await arena(40);
  await biome('river');
  await survivalSetup(30);
  const before = inv().length;
  let caught = 0;
  for (let i = 0; i < 12 && caught < 3; i++) {
    if (!(await catchOne())) continue;
    caught = named('Silver Dace') + named('River Perch') + named('Goldfin Trout');
  }
  check('River species bite in a river', caught > 0 && named('Pond Bream') === 0, `dace ${named('Silver Dace')}, perch ${named('River Perch')}, trout ${named('Goldfin Trout')}`);
}

async function force() {
  await arena(80);
  await biome('river');
  await survivalSetup(30);
  let before = await fishXp();
  let at = await cmd(`/cerb fish force ${NAME} species goldfin_trout`, 400);
  await catchOne();
  let gain = (await fishXp()) - before;
  const first = !!saw(/New species: .*Goldfin Trout/, at);
  check('A forced rare species: Goldfin Trout in hand', named('Goldfin Trout') >= 1, `${named('Goldfin Trout')}`);
  check('...paying a rare catch\'s 60 XP (+300 the first time)', first ? gain === 360 : gain === 60, `gain ${gain}, first ${first}`);
  before = await fishXp();
  at = await cmd(`/cerb fish force ${NAME} treasure sea_glass`, 400);
  await catchOne();
  gain = (await fishXp()) - before;
  check('Forced treasure: Sea Glass in hand, 25 XP (+120 the first time)', named('Sea Glass') >= 1 && [25, 145].includes(gain), `glass ${named('Sea Glass')}, gain ${gain}`);
  const j0 = await journal();
  at = await cmd(`/cerb fish force ${NAME} lure river_lurker`, 400);
  await catchOne();
  const j1 = await journal();
  check('A forced lure: "took your bait", and the journal banks it', !!saw(/River Lurker .*took your bait/, at) && (j1.lures.river_lurker || 0) === (j0.lures.river_lurker || 0) + 1,
    `${JSON.stringify(j0.lures)} -> ${JSON.stringify(j1.lures)}`);
}

async function call() {
  await arena(120, 1); // a shallow pond: the creature walks out to fight
  await biome('river');
  await survivalSetup(30);
  await setLevel('Blade_Mastery', 80);
  await cmd(`/cerb fish lure ${NAME} river_lurker 2`, 900);
  const j0 = await journal();
  const r = await sneakCast();
  await sleep(800);
  const j1 = await journal();
  const st = await stats();
  check('Sneak + cast with a lure: the River Lurker answers the call', r.answered, sawAll(/Calling|answers|crowded|Finish/, r.at).join(' / ') + ' | ' + hudSince(r.t).filter(m => /Calling/.test(m)).join(' / '));
  check('...one lure spent when it surfaced, and the fight is on the journal', (j1.lures.river_lurker || 0) === (j0.lures.river_lurker || 0) - 1 && !!j1.fight,
    `${JSON.stringify(j0.lures)} -> ${JSON.stringify(j1.lures)}, fight ${j1.fight}`);
  check('...one creature out on the server', st.out === 1, st.text);
  const mob = (await mobsHp(30)).find(m => /drowned/.test(m.line));
  check('...a real levelled mob with its health pool (twice a normal drowned of its level)', !!mob && mob.max > 100, mob ? mob.line : 'none');
  // A second call during the fight spends nothing.
  const r2 = await sneakCast(6000);
  const j2 = await journal();
  check('A second call during the fight is refused and spends nothing', !r2.answered && (j2.lures.river_lurker || 0) === (j1.lures.river_lurker || 0),
    hudSince(r2.t).filter(m => /Finish|crowded/.test(m)).join(' / ') + ` lures ${JSON.stringify(j2.lures)}`);
  // Nobody else can hurt it.
  target = await connect('CerbTarget');
  await sleep(1500);
  bot.chat('/op CerbTarget'); await sleep(400);
  target.chat('/gamemode survival'); target.chat('/effect give @s resistance infinite 4 true'); target.chat('/give @s netherite_sword'); await sleep(900);
  const c0 = creatureNear(30)[0];
  if (c0) {
    target.chat(`/tp @s ${c0.position.x.toFixed(1)} ${(c0.position.y + 0.3).toFixed(1)} ${(c0.position.z - 1.5).toFixed(1)}`); await sleep(1200);
    const s = target.inventory.slots.findIndex((it, i) => it && it.name === 'netherite_sword' && i >= 36 && i <= 44);
    if (s >= 0) target.setQuickBarSlot(s - 36);
    const h0 = (await mobsHp(30)).find(m => /drowned/.test(m.line));
    for (let i = 0; i < 6; i++) {
      const e = Object.values(target.entities).find(x => x.name === 'drowned');
      if (e) { try { await target.lookAt(e.position.offset(0, 1.2, 0), true); target.attack(e); } catch (x) { /* ignore */ } }
      await sleep(700);
    }
    const h1 = (await mobsHp(30)).find(m => /drowned/.test(m.line));
    check('Another player\'s hits do nothing to it', h0 && h1 && h1.cur === h0.cur, `${h0 && h0.line} -> ${h1 && h1.line}`);
    target.chat('/tp @s ~ ~ ~20'); await sleep(800);
  } else check('Another player\'s hits do nothing to it', false, 'no creature to hit');
  // The owner fights it and steps away as it falls, so the drops stay on the ground for the ownership check.
  const before = await fishXp();
  const atK = chat.length;
  const done = await fight(90, true);
  await sleep(1500);
  const gain = (await fishXp()) - before;
  const j3 = await journal();
  check('The owner defeated it', done && !!saw(/Defeated|First defeat|defeated/, atK), sawAll(/Defeated|defeat/, atK).join(' / ') || 'no message');
  check('...Fishing XP for the fight (its kill XP again, plus the first defeat\'s 120)', gain >= 120, `+${gain}`);
  check('...recorded as defeated, the fight closed, the other lures still banked', j3.defeated.includes('river_lurker') && !j3.fight && (j3.lures.river_lurker || 0) === (j1.lures.river_lurker || 0),
    `defeated ${j3.defeated}, fight ${j3.fight}, lures ${JSON.stringify(j3.lures)}`);
  const st2 = await stats();
  check('...no creature out any more', st2.out === 0, st2.text);
  // Its drops are its owner's alone: CerbTarget stands on them first, then the owner picks them up. The cod and the scale
  // are chances (no drop at all about one fight in eight), so call again until one drops.
  const itemsNear = (pos, r) => Object.values(bot.entities).filter(e => e.name === 'item' && e.position.distanceTo(pos) < r);
  let loot = fight.fell ? itemsNear(fight.fell, 6) : [];
  for (let tries = 1; !loot.length && tries <= 3; tries++) {
    note(`no drops from that one: calling another (${tries})`);
    await stand();
    await cmd(`/cerb fish lure ${NAME} river_lurker 1`, 900);
    await hold(ROD); // the fight left the sword in hand
    if (!(await sneakCast()).answered) { note('the call was not answered'); break; }
    await fight(90, true);
    await sleep(800);
    loot = fight.fell ? itemsNear(fight.fell, 6) : [];
  }
  if (loot.length) {
    const p = loot[0].position;
    const all = who => who.inventory.slots.filter(Boolean).reduce((n, it) => n + it.count, 0);
    const tBefore = all(target);
    target.chat(`/tp @s ${p.x.toFixed(1)} ${(p.y + 0.3).toFixed(1)} ${p.z.toFixed(1)}`); await sleep(2500);
    const tAfter = all(target);
    const left = itemsNear(p, 3).length;
    target.chat(`/tp @s ${X + 0.5} ${Y} ${Z - 2.5}`); await sleep(800);
    const mine0 = all(bot);
    await cmd(`/tp @s ${p.x.toFixed(1)} ${(p.y + 0.3).toFixed(1)} ${p.z.toFixed(1)}`, 2500);
    const mine1 = all(bot);
    check('Its drops are its owner\'s alone: CerbTarget standing on them picks up nothing, the owner does', tAfter === tBefore && left > 0 && mine1 > mine0,
      `target ${tBefore}->${tAfter} with ${left} on the ground, owner ${mine0}->${mine1}, ${named('Lurker Scale')} scale(s)`);
  } else check('Its drops are its owner\'s alone', false, 'no drops in four fights');
  target.quit(); target = null;
}

async function refund() {
  await arena(160, 1);
  await biome('river');
  await survivalSetup(30);
  // 1. The owner logs out mid-fight.
  await cmd(`/cerb fish lure ${NAME} river_lurker 1`, 900);
  let j0 = await journal();
  let r = await sneakCast();
  check('Called (logout case)', r.answered);
  bot.quit(); await sleep(3000);
  await connect(); await sleep(3500);
  let j1 = await journal();
  let st = await stats();
  check('Logging out mid-fight gives the lure back, and the creature is gone', (j1.lures.river_lurker || 0) === (j0.lures.river_lurker || 0) && !j1.fight && st.out === 0,
    `${JSON.stringify(j0.lures)} -> ${JSON.stringify(j1.lures)}, out ${st.out}`);
  // 2. Too far for too long (the leash: 48 blocks for 30 s).
  await arena(160, 1);
  await survivalSetup(30);
  j0 = await journal();
  r = await sneakCast();
  check('Called (leash case)', r.answered);
  const at = await cmd(`/tp @s ${X + 0.5} ${Y + 1} ${Z + 120.5}`, 1000);
  for (let i = 0; i < 45 && !saw(/slipped back into the water/, at); i++) await sleep(1000);
  j1 = await journal();
  check('Too far for 30 s: the creature leaves and its lure comes back', !!saw(/slipped back into the water \(you left the water\)/, at) && (j1.lures.river_lurker || 0) === (j0.lures.river_lurker || 0),
    sawAll(/slipped/, at).join(' / ') + ` ${JSON.stringify(j0.lures)} -> ${JSON.stringify(j1.lures)}`);
  // 3. The owner dies.
  await stand();
  j0 = await journal();
  r = await sneakCast();
  check('Called (death case)', r.answered);
  const at2 = await cmd('/effect clear @s resistance', 300);
  await cmd('/kill @s', 2500);
  try { bot.respawn && bot.respawn(); } catch (e) { /* auto */ }
  await sleep(3000);
  j1 = await journal();
  st = await stats();
  check('Dying ends the fight: the lure comes back and the creature leaves', (j1.lures.river_lurker || 0) === (j0.lures.river_lurker || 0) && st.out === 0,
    sawAll(/slipped/, at2).join(' / ') + ` ${JSON.stringify(j0.lures)} -> ${JSON.stringify(j1.lures)}, out ${st.out}`);
}

async function limits() {
  const dir = process.env.SERVER_DIR;
  if (!dir) { check('SERVER_DIR is set (the sandbox server folder)', false); return; }
  const file = path.join(dir, 'plugins', 'CerberusPlugin', 'resources.yml');
  const original = fs.readFileSync(file, 'utf8');
  try {
    const edited = original.replace(/calls: \{ server: \d+,/, 'calls: { server: 1,');
    if (edited === original) { check('resources.yml has a calls section to edit', false); return; }
    fs.writeFileSync(file, edited);
    await cmd('/cerb reload', 2500);
    await arena(200, 1);
    await biome('river');
    await survivalSetup(30);
    await cmd(`/cerb fish lure ${NAME} river_lurker 1`, 600);
    await cmd(`/cerb fish lure CerbTarget river_lurker 1`, 600);
    const r = await sneakCast();
    check('The bot\'s call answers (1 of 1 on the server)', r.answered);
    target = await connect('CerbTarget');
    await sleep(1500);
    bot.chat('/op CerbTarget'); await sleep(400);
    await cmd(`/cerb fish lure CerbTarget river_lurker 1`, 600);
    target.chat('/gamemode survival'); target.chat('/clear @s'); target.chat(`/give @s ${ROD}`); await sleep(900);
    target.chat(`/tp @s ${X + 2.5} ${Y} ${Z + 0.5} 0 20`); await sleep(900);
    const s = target.inventory.slots.findIndex((it, i) => it && it.name === 'fishing_rod' && i >= 36 && i <= 44);
    if (s >= 0) target.setQuickBarSlot(s - 36);
    const tj0 = await journal('CerbTarget');
    target.setControlState('sneak', true); await sleep(250); target.activateItem(); await sleep(120); target.deactivateItem(); await sleep(300); target.setControlState('sneak', false);
    await sleep(9000);
    const tj1 = await journal('CerbTarget');
    const st = await stats();
    check('CerbTarget\'s call is refused while the server is at its cap, and spends nothing', st.out === 1 && (tj1.lures.river_lurker || 0) === (tj0.lures.river_lurker || 0),
      `out ${st.out}, CerbTarget lures ${JSON.stringify(tj0.lures)} -> ${JSON.stringify(tj1.lures)}; ${st.text}`);
    target.quit(); target = null;
    await fight(60);
  } finally {
    fs.writeFileSync(file, original);
    await cmd('/cerb reload', 2500);
  }
  check('resources.yml restored', fs.readFileSync(file, 'utf8') === original);
}

async function afk() {
  await arena(240);
  await biome('plains');
  await survivalSetup(20);
  const start = Date.now(), at = chat.length;
  const per = [];
  let last = await fishXp(), catches = 0;
  while (Date.now() - start < 180000) {
    if (await catchOne()) {
      catches++;
      const now = await fishXp();
      per.push({ t: (Date.now() - start) / 1000, d: now - last });
      last = now;
    }
  }
  const first = per.filter(p => p.t < 60).map(p => p.d), lastMin = per.filter(p => p.t >= 120).map(p => p.d);
  const avg = a => a.length ? a.reduce((x, y) => x + y, 0) / a.length : 0;
  note(`catches ${catches}; XP per catch, first minute: ${first.join(',')}; last minute: ${lastMin.join(',')}`);
  check('Automation keeps catching for three minutes', catches >= 12 && lastMin.length >= 3, `${catches} catches`);
  check('...and the last minute pays like the first (no idle reduction)', lastMin.every(d => d >= 2) && avg(lastMin) >= 0.6 * avg(first),
    `avg ${avg(first).toFixed(1)} -> ${avg(lastMin).toFixed(1)}`);
  check('...with no inactivity notice', !saw(/idle|inactiv|AFK|afk/, at), sawAll(/idle|inactiv|afk/i, at).join(' / '));
}

async function inventory() {
  await arena(280);
  await biome('plains');
  await survivalSetup(20);
  for (let i = 0; i < 35; i++) bot.chat('/give @s stone 64');
  await sleep(2500);
  await hold(ROD, false);
  const free = bot.inventory.emptySlotCount();
  const before = await fishXp();
  const j0 = await journal();
  await cmd(`/cerb fish force ${NAME} lure river_lurker`, 300);
  await catchOne();
  await sleep(1500);
  const gain = (await fishXp()) - before;
  const j1 = await journal();
  const ground = Object.values(bot.entities).filter(e => e.name === 'item' && e.position.distanceTo(bot.entity.position) < 6).length;
  check('A full inventory: the catch stays on the ground', free === 0 && ground >= 1, `free slots ${free}, items near ${ground}`);
  check('...its XP is paid', gain >= 2, `+${gain}`);
  check('...and the lure is banked all the same', (j1.lures.river_lurker || 0) === (j0.lures.river_lurker || 0) + 1, `${JSON.stringify(j0.lures)} -> ${JSON.stringify(j1.lures)}`);
  await cmd('/clear @s', 400);
  await clearMobs();
}

async function economy() {
  await arena(320);
  await biome('plains');
  await survivalSetup(100);
  // Fortune (fish) at Fishing 100: Fortune 50, a quarter of fish catches bring one more (from Twin Catch, sometimes two).
  // The server counts the extra fish it drops; every one of them must reach the player.
  const extrasNow = async () => Number(((await stats()).text.match(/extra fish (\d+)/) || [])[1]);
  const isFish = it => /^(cod|salmon|tropical_fish|pufferfish)$/.test(it.name);
  const fishCount = () => count(isFish);
  const otherCount = () => count(it => !isFish(it) && it.name !== 'fishing_rod');
  const e0 = await extrasNow();
  let catches = 0, other = 0, fishCatches = 0, arrived = 0, lost = 0;
  for (let i = 0; i < 30; i++) {
    const f0 = fishCount(), o0 = otherCount();
    if (!(await catchOne())) continue;
    // Wait until whatever flew in has settled (an extra fish flies with the catch).
    let last = fishCount() + otherCount(), still = 0;
    for (let t = 0; t < 15 && still < 3; t++) { await sleep(200); const n = fishCount() + otherCount(); if (n === last) still++; else { still = 0; last = n; } }
    catches++;
    const df = fishCount() - f0, dO = otherCount() - o0;
    if (dO > 0) other++;                                // junk or treasure
    else if (df > 0) { fishCatches++; arrived += df - 1; } // a fish, and its extra ones
    else lost++;                                       // landed short of the player
  }
  const extras = (await extrasNow()) - e0;
  check('Fortune (fish) at Fishing 100 brings extra fish', extras >= 2 && extras <= 2 * (fishCatches + lost), `${extras} extra fish (server count) from ${fishCatches} fish catches (${catches} catches, ${other} junk or treasure, ${lost} landed short)`);
  check('...and they reach the player with the catch', arrived <= extras && arrived >= extras - 2 * lost, `${arrived} extra fish arrived with their catch, ${extras} dropped`);
  // Treasure never comes twice.
  const glass0 = named('Sea Glass');
  for (let i = 0; i < 6; i++) { await cmd(`/cerb fish force ${NAME} treasure sea_glass`, 250); await catchOne(); }
  check('Six forced treasures: exactly six Sea Glass (Fortune never copies treasure)', named('Sea Glass') - glass0 === 6, `${named('Sea Glass') - glass0}`);
  // A species cooks like any fish.
  await cookSpecies();
  // A creature's drop can't be placed.
  await cmd(`/cerb gather item anglers_lantern 1`, 800);
  await hold('lantern', false);
  const below = new Vec3(X - 2, Y - 1, Z - 2);
  try { await bot.placeBlock(bot.blockAt(below), new Vec3(0, 1, 0)); } catch (e) { /* refused */ }
  await sleep(800);
  const placed = bot.blockAt(below.offset(0, 1, 0));
  check('Angler\'s Lantern can\'t be placed', !placed || placed.name !== 'lantern', placed ? placed.name : 'none');
  // A rod upgrade: Scaled grip, once per rod.
  await cmd('/clear @s', 300);
  await cmd(`/give @s fishing_rod`, 500);
  await cmd(`/cerb gather item lurker_scale 2`, 800);
  await hold('fishing_rod', false);
  const lureBefore = await lureInfo();
  const ct = new Vec3(X - 2, Y, Z - 2);
  await cmd(`/setblock ${ct.x} ${ct.y} ${ct.z} crafting_table`, 600);
  const tbl = bot.blockAt(ct);
  let upgraded = '', again = '';
  try {
    await bot.lookAt(tbl.position.offset(0.5, 0.5, 0.5), true);
    let w = await bot.openBlock(tbl);
    await sleep(500);
    const findIn = (name, re) => { for (let i = w.inventoryStart; i < w.inventoryEnd; i++) if (w.slots[i] && w.slots[i].name === name && (!re || re.test(loreOf(w.slots[i])))) return i; return -1; };
    const click = async (slot, button = 0, mode = 0) => { try { await bot.clickWindow(slot, button, mode); } catch (e) { /* the server decides */ } await sleep(250); };
    const putOne = async (name, to) => { const from = findIn(name); if (from < 0) return false; await click(from); await click(to, 1); await click(from); return true; };
    await click(findIn('fishing_rod')); await click(1);
    await putOne('turtle_scute', 2);
    await sleep(500);
    upgraded = w.slots[0] ? `${w.slots[0].name}: ${loreOf(w.slots[0])}` : '';
    await click(0, 0, 1); // shift-click the result into the inventory
    await sleep(400);
    bot.closeWindow(w);
    await sleep(500);
    w = await bot.openBlock(tbl);
    await sleep(500);
    await click(findIn('fishing_rod', /Scaled grip/)); await click(1);
    await putOne('turtle_scute', 2);
    await sleep(500);
    again = w.slots[0] ? `${w.slots[0].name}: ${loreOf(w.slots[0])}` : '';
    await click(1, 0, 1); await click(2, 0, 1); // everything back into the inventory
    bot.closeWindow(w);
  } catch (e) { note('crafting: ' + e.message); }
  await sleep(800);
  const gripped = bot.inventory.slots.findIndex((it, i) => it && it.name === 'fishing_rod' && /Scaled grip/.test(loreOf(it)));
  if (gripped >= 36) bot.setQuickBarSlot(gripped - 36); else if (gripped >= 0) { try { await bot.clickWindow(gripped, 0, 2); } catch (e) { /* ignore */ } }
  await sleep(600);
  const lureAfter = await lureInfo();
  check('Rod + Lurker Scale makes the same rod with a Scaled grip', /fishing_rod.*Scaled grip/.test(upgraded), upgraded || 'nothing');
  check('...once per rod (the upgraded rod and another scale make nothing)', again === '', again);
  check('...and bites come sooner with it in hand', lureAfter > lureBefore, `${lureBefore}% -> ${lureAfter}% sooner`);
}
/** A species cooks like any fish: a forced Pond Bream (a cod with its name) smelted in a furnace. */
async function cookSpecies() {
  await cmd(`/cerb fish force ${NAME} species pond_bream`, 250);
  await catchOne();
  const fb = new Vec3(X + 2, Y, Z - 2);
  await cmd(`/setblock ${fb.x} ${fb.y} ${fb.z} furnace`, 600);
  await cmd('/give @s coal 2', 800);
  let cooked = 0, detail = '';
  try {
    const bream = bot.inventory.items().find(it => (itemName(it) || '').includes('Pond Bream'));
    const coal = bot.inventory.items().find(it => it.name === 'coal');
    detail = `bream ${bream ? bream.name + ' slot ' + bream.slot : 'none'}, coal ${coal ? coal.count : 0}`;
    const furnace = await bot.openFurnace(bot.blockAt(fb));
    await sleep(400);
    const w = furnace;
    const click = async (slot, button = 0, mode = 0) => { try { await bot.clickWindow(slot, button, mode); } catch (e) { /* the server decides */ } await sleep(250); };
    // Move one bream into the input (slot 0) and one coal into the fuel (slot 1) with plain clicks, as a player would.
    const find = pred => { for (let i = w.inventoryStart; i < w.inventoryEnd; i++) if (w.slots[i] && pred(w.slots[i])) return i; return -1; };
    const b = find(it => (itemName(it) || '').includes('Pond Bream'));
    if (b >= 0) { await click(b); await click(0, 1); await click(b); }
    const c = find(it => it.name === 'coal');
    if (c >= 0) { await click(c); await click(1, 1); await click(c); }
    await sleep(500);
    detail += `; input ${w.slots[0] ? itemName(w.slots[0]) || w.slots[0].name : 'empty'}, fuel ${w.slots[1] ? w.slots[1].name : 'empty'}`;
    for (let i = 0; i < 16 && !cooked; i++) { await sleep(1000); const o = w.slots[2]; cooked = o && /cooked/.test(o.name) ? o.count : 0; }
    detail += `; output ${w.slots[2] ? w.slots[2].name + ' x' + w.slots[2].count : 'empty'}`;
    bot.closeWindow(w);
  } catch (e) { detail += '; furnace: ' + e.message; }
  check('A species cooks like any fish (Pond Bream -> cooked cod)', cooked >= 1, `cooked ${cooked}; ${detail}`);
}
async function lureInfo() {
  await cmd(`/tp @s ${X + 0.5} ${Y} ${Z + 0.5} 0 20`, 600);
  try { await bot.lookAt(new Vec3(X + 0.5, Y - 1, Z + 5.5), true); } catch (e) { /* best effort */ }
  const at = await cmd('/cerb fish info', 900);
  const m = (saw(/bites [\d.]+% sooner/, at) || '').match(/bites ([\d.]+)% sooner/);
  return m ? Number(m[1]) : NaN;
}

async function creative() {
  await arena(360);
  await biome('plains');
  await cmd('/gamemode creative', 400);
  await cmd('/clear @s', 300);
  await setLevel('Fishing', 30);
  await hold(ROD);
  const before = await fishXp();
  const j0 = await journal();
  await cmd(`/cerb fish force ${NAME} lure river_lurker`, 300);
  let n = 0;
  for (let i = 0; i < 4; i++) if (await catchOne()) n++;
  const gain = (await fishXp()) - before;
  const j1 = await journal();
  check('Creative: catches pay no Fishing XP', n >= 2 && gain === 0, `${n} catches, +${gain}`);
  check('...give no species', named('Silver Dace') + named('Pond Bream') === 0);
  check('...and bank no lure', JSON.stringify(j1.lures) === JSON.stringify(j0.lures), `${JSON.stringify(j0.lures)} -> ${JSON.stringify(j1.lures)}`);
  await cmd(`/cerb fish force ${NAME} clear`, 200); // a force waits for a survival catch: never leave one behind
  await cmd('/gamemode survival', 300);
}

const STATE = process.env.STATE || path.join(__dirname, '.fishing-state.json');
async function persistSet() {
  await arena(400);
  await biome('river');
  await survivalSetup(30);
  await cmd(`/cerb fish force ${NAME} species old_whiskers`, 300);
  await catchOne();
  await cmd(`/cerb fish lure ${NAME} tidecaller 3`, 800);
  const j = await journal();
  fs.writeFileSync(STATE, JSON.stringify({ lures: j.lures, found: j.found.length }));
  check('Before the restart: Old Whiskers in the journal and 3 Tidecaller lures', j.found.some(l => /Old Whiskers/.test(l)) && j.lures.tidecaller >= 3, JSON.stringify(j.lures));
}
async function persistCheck() {
  const s = JSON.parse(fs.readFileSync(STATE, 'utf8'));
  const j = await journal();
  check('After the restart: the same journal and lures', j.found.length === s.found && JSON.stringify(j.lures) === JSON.stringify(s.lures), `${JSON.stringify(s)} vs ${JSON.stringify(j.lures)} ${j.found.length}`);
}
/** The bot stays online until the server goes (the outer script stops or kills it on "READY"). */
let serverGoing = false;
async function holdUntilServerGoes() {
  serverGoing = true; // the kick that follows is expected
  console.log('READY');
  await new Promise(res => { bot.once('end', res); setTimeout(res, 240000); });
}
// The server stops cleanly while a creature is out and its owner online: the stop's quit gives the lure back, saved with the player.
async function restartSet() {
  await arena(440, 1);
  await biome('river');
  await survivalSetup(30);
  await cmd(`/cerb fish lure ${NAME} river_lurker 1`, 800);
  const j0 = await journal();
  const r = await sneakCast();
  const j1 = await journal();
  fs.writeFileSync(STATE, JSON.stringify({ before: j0.lures.river_lurker || 0 }));
  check('A creature is out when the server stops', r.answered && (j1.lures.river_lurker || 0) === (j0.lures.river_lurker || 0) - 1 && !!j1.fight,
    `${JSON.stringify(j0.lures)} -> ${JSON.stringify(j1.lures)} fight ${j1.fight}`);
  await holdUntilServerGoes();
}
async function restartCheck() {
  const s = JSON.parse(fs.readFileSync(STATE, 'utf8'));
  const j = await journal();
  const st = await stats();
  check('After the restart its lure is back (once), no fight is open and no creature is out', (j.lures.river_lurker || 0) === s.before && !j.fight && st.out === 0,
    `before ${s.before}, now ${JSON.stringify(j.lures)}, fight ${j.fight}, out ${st.out}`);
}
// A crash mid-fight: the player's data was saved with the fight open (the sandbox-only test plugin's /testsave, as an
// autosave would), the server dies without stopping, and the next join gives the lure back once.
async function crashSet() {
  await arena(520, 1);
  await biome('river');
  await survivalSetup(30);
  await cmd(`/cerb fish lure ${NAME} river_lurker 1`, 800);
  const j0 = await journal();
  const r = await sneakCast();
  const j1 = await journal();
  fs.writeFileSync(STATE, JSON.stringify({ before: j0.lures.river_lurker || 0 }));
  check('A creature is out, its fight on the journal', r.answered && (j1.lures.river_lurker || 0) === (j0.lures.river_lurker || 0) - 1 && !!j1.fight,
    `${JSON.stringify(j0.lures)} -> ${JSON.stringify(j1.lures)} fight ${j1.fight}`);
  const at = await cmd(`/testsave ${NAME}`, 1500); // the player's data on disk now holds the open fight
  check('...and the player\'s data was written with it (sandbox test plugin)', !!saw(/testsave: saved/, at), sawAll(/testsave|Unknown/, at).join(' / ') || 'no answer');
  await holdUntilServerGoes();
}
async function crashCheck() {
  const s = JSON.parse(fs.readFileSync(STATE, 'utf8'));
  const j = await journal();
  const st = await stats();
  check('After a crash mid-fight: the lure is back once, no fight is open, no creature is out', (j.lures.river_lurker || 0) === s.before && !j.fight && st.out === 0,
    `before ${s.before}, now ${JSON.stringify(j.lures)}, fight ${j.fight}, out ${st.out}`);
  check('...and the join says why', !!saw(/slipped back into the water \(the server restarted\)/, 0), sawAll(/slipped|restarted/, 0).join(' / ') || 'no message');
}

async function control() {
  await arena(480);
  await survivalSetup(1);
  const before = await fishXp();
  let n = 0, guardians = 0;
  const seen = new Set();
  for (let i = 0; i < 60; i++) {
    if (await catchOne()) n++;
    for (const e of Object.values(bot.entities)) if (e.name === 'guardian' && !seen.has(e.id)) { seen.add(e.id); guardians++; }
    if (guardians >= 2) break;
  }
  const gain = (await fishXp()) - before;
  note(`${n} catches, +${gain} Fishing XP, ${guardians} guardians`);
  check('CONTROL: catches pay Fishing XP (expected to FAIL on a build without Fishing)', gain > 0, `+${gain}`);
  check('CONTROL: no Sea Monster spawns from catches (expected to FAIL on a build without Fishing)', guardians === 0, `${guardians} guardians in ${n} catches`);
  await clearMobs();
}

const PHASES = { basic, waters, force, call, refund, limits, afk, inventory, economy, creative, cook: async () => { await arena(330); await biome('plains'); await survivalSetup(20); await cookSpecies(); },
  'persist-set': persistSet, 'persist-check': persistCheck, 'restart-set': restartSet, 'restart-check': restartCheck,
  'crash-set': crashSet, 'crash-check': crashCheck, control };
const ALL = ['basic', 'waters', 'force', 'call', 'refund', 'inventory', 'economy', 'creative'];

connect().then(async () => {
  await sleep(3500);
  const list = phaseArg === 'all' ? ALL : phaseArg.split(',');
  for (const ph of list) {
    if (!PHASES[ph]) { console.log('Unknown phase ' + ph); results.push(false); continue; }
    console.log(`── ${ph} ──`);
    try { await PHASES[ph](); } catch (e) { console.log('SCRIPT ERROR in ' + ph, e.stack); results.push(false); failed.push(ph + ' (script error)'); }
  }
  try { await cmd('/gamemode creative', 300); } catch (e) { /* gone */ }
  const bad = results.filter(r => !r).length;
  console.log(`\n${results.length - bad}/${results.length} passed`);
  if (failed.length) console.log('FAILED: ' + failed.join(' | '));
  bot.quit(); if (target) target.quit();
  await sleep(500); process.exit(bad ? 1 : 0);
});
setTimeout(() => { console.log('TIMEOUT'); process.exit(3); }, 3600000);
