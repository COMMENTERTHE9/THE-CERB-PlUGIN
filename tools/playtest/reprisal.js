// Iron Reprisal and Martial Expertise's Agility in game (docs/design/COMBAT_IMPROVEMENTS.md). LOCAL SANDBOX servers only
// (a superflat world); op the bots first.
// Usage: node reprisal.js <phase[,phase...]> [name] [second bot]
//   throw     - a husk whose melee hit lands on the bot (full iron, Heavy Armor Mastery 100) is thrown back once: the
//               counter, and how far it flies from where it struck
//   controls  - nobody thrown: Heavy Armor Mastery 9 (below its level); leather armor; a raised shield (it takes the
//               blows: nothing off the Cerberus health pool; lowered, the control, the blow lands); a skeleton's arrows
//               (never projectiles); knockback resistance counts (a ravager lands much nearer than a husk)
//   distances - how far a husk lands: Heavy Armor Mastery 10 / 55 / 100 with 1 / 2 / 4 iron pieces (the table in STATUS)
//   pvp       - a second player hitting: not thrown with pvp off (the counter says why); thrown with pvp: true (written into
//               the sandbox's combat.yml for the phase, then put back; needs SERVER_DIR): the server sends the attacker's
//               client the push and they fly (the bots apply 1.21.9+'s velocity packets themselves: see connect())
//   cooldown  - cooldown-ms 4000 (written for the phase): the husk's next hits within it throw nothing (needs SERVER_DIR)
//   technique - Heavy Armor Mastery 9 -> 10 by a real hit: "New technique: Iron Reprisal" with the level-up
//   agility   - Martial Expertise: the Stats page's Agility counts 1.25 per level; a real level-up by a fist kill names Agility
//   all       - throw, controls, distances, technique, agility (pvp and cooldown apart: they edit combat.yml)
const fs = require('fs');
const path = require('path');
const mineflayer = require('mineflayer');
const { Vec3 } = require('vec3');
const phaseArg = process.argv[2] || 'all';
const NAME = process.argv[3] || 'CerbArmor';
const SECOND = process.argv[4] || 'CerbTarget';
const sleep = ms => new Promise(r => setTimeout(r, ms));
const chat = [];
const results = [];
const failed = [];
const measured = [];
const check = (name, ok, detail = '') => {
  results.push(!!ok);
  if (!ok) failed.push(name);
  console.log(`${ok ? 'PASS' : 'FAIL'}  ${name}${detail !== '' ? '  (' + detail + ')' : ''}`);
};
const note = s => console.log('      ' + s);
let bot;

function connect(name = NAME) {
  const b = mineflayer.createBot({ host: '127.0.0.1', port: 25565, username: name, version: '1.21.11', auth: 'offline' });
  b.on('messagestr', (m, pos) => { if (pos !== 'game_info') chat.push({ t: Date.now(), m }); });
  b.on('kicked', r => { console.log('KICKED', name, r); process.exit(2); });
  // mineflayer 4.39 reads 1.21.9+'s velocity packet (already in blocks per tick) as the old 1/8000 units, so a bot the
  // server pushes barely moves. A real client takes the push in full: the bot is given the packet's own value, and the
  // strongest push it was sent is kept (selfPush) as proof the server sent one at all. Registered at spawn, after
  // mineflayer's own handler (its plugins load once the connection is up), so this one has the last word.
  b.selfPush = 0;
  b.once('spawn', () => b._client.on('entity_velocity', p => {
    if (!b.entity || p.entityId !== b.entity.id) return;
    b.entity.velocity.set(p.velocity.x, p.velocity.y, p.velocity.z);
    b.selfPush = Math.max(b.selfPush, Math.hypot(p.velocity.x, p.velocity.z));
  }));
  // The bot's own hand state (living entity data 8: 1 = using an item, 2 = the off hand): is the shield really raised?
  b.handState = 0;
  b._client.on('entity_metadata', p => {
    if (!b.entity || p.entityId !== b.entity.id) return;
    for (const m of p.metadata || []) if (m.key === 8) b.handState = m.value;
  });
  return new Promise(r => b.once('spawn', () => r(b)));
}
async function cmd(c, wait = 600) { const at = chat.length; bot.chat(c); await sleep(wait); return at; }
const saw = (re, since) => (chat.slice(since).find(x => re.test(x.m)) || {}).m;

// ── the arena ─────────────────────────────────────────────────────

/** A clean patch of superflat ground (grass at y -61, a player stands at -60); the bot in its middle, facing north. */
async function arena(cx, cz) {
  await cmd(`/tp @s ${cx + 0.5} -60 ${cz + 0.5}`, 2000);
  await cmd(`/fill ${cx - 14} -60 ${cz - 18} ${cx + 14} -40 ${cz + 14} air`, 1200);
  await cmd(`/fill ${cx - 14} -61 ${cz - 18} ${cx + 14} -61 ${cz + 14} grass_block`, 900);
  await cmd(`/fill ${cx - 14} -63 ${cz - 18} ${cx + 14} -62 ${cz + 14} dirt`, 900);
  await clear();
  await cmd(`/tp @s ${cx + 0.5} -60 ${cz + 0.5} 180 0`, 800);
  return { X: cx, Y: -60, Z: cz };
}
async function clear() { await cmd('/kill @e[type=!player,distance=..64]', 700); }

const SLOTS = ['chest', 'legs', 'head', 'feet'];
const PIECE = { chest: 'chestplate', legs: 'leggings', head: 'helmet', feet: 'boots' };
/** The player inventory window's armor slots, and where mineflayer's equip puts each piece. */
const ARMOR_SLOT = { head: 5, chest: 6, legs: 7, feet: 8 };
const DEST = { head: 'head', chest: 'torso', legs: 'legs', feet: 'feet' };

/**
 * {@code pieces} of {@code metal} armor (chest first, then legs, head, feet); the rest empty. The sandbox's Folia build has
 * no /item command, so each piece is given and put on by a click into its slot, as a player does.
 */
async function wear(metal, pieces) {
  for (const s of SLOTS) {
    const it = bot.inventory.slots[ARMOR_SLOT[s]];
    if (it) await cmd(`/clear @s ${it.name}`, 300);
  }
  for (let i = 0; i < pieces; i++) {
    const name = `${metal}_${PIECE[SLOTS[i]]}`;
    await cmd(`/give @s ${name}`, 500);
    const item = bot.inventory.items().find(it => it.name === name);
    if (!item) { note(`wear: no ${name} to put on`); continue; }
    try { await bot.equip(item, DEST[SLOTS[i]]); } catch (e) { note(`wear ${name}: ${e.message}`); }
    await sleep(250);
  }
  const on = SLOTS.filter(s => (bot.inventory.slots[ARMOR_SLOT[s]] || {}).name === `${metal}_${PIECE[s]}`).length;
  if (on !== pieces) note(`wear: ${on} of ${pieces} ${metal} pieces on`);
}

/** /cerb reprisal's counters: { thrown, notLanded, pvpOff, cooldown, exempt, shield, gone }. */
async function counters() {
  const at = await cmd('/cerb reprisal', 700);
  const line = saw(/^Thrown \d+/, at) || '';
  const n = re => Number((line.match(re) || [0, NaN])[1]);
  return { thrown: n(/^Thrown (\d+)/), notLanded: n(/not landed (\d+)/), pvpOff: n(/PvP off (\d+)/), cooldown: n(/cooldown (\d+)/),
    exempt: n(/exempt (\d+)/), shield: n(/the shield took it (\d+)/), gone: n(/its turn (\d+)/), line };
}
const diff = (a, b) => Object.fromEntries(Object.keys(a).filter(k => k !== 'line').map(k => [k, b[k] - a[k]]));

const nearest = type => Object.values(bot.entities).filter(e => e.name === type)
  .sort((a, b) => a.position.distanceTo(bot.entity.position) - b.position.distanceTo(bot.entity.position))[0];
const flat = (a, b) => Math.hypot(a.x - b.x, a.z - b.z);

/**
 * One attack: {@code type} summoned {@code dist} blocks north of the bot walks up and strikes; we follow it for the next
 * {@code watchMs}: how far it gets from the bot after its first strike (its distance then subtracted), the counters'
 * change, and whether the bot was hurt. The bot doesn't move (knockback resistance 1), so only the attacker's flight counts.
 */
async function attack(o, type = 'husk', { dist = 3, watchMs = 1300, extra = '', shield = false, pool: readPool = false } = {}) {
  await clear();
  await cmd(`/cerb heal ${NAME}`, 300);
  await cmd(`/tp @s ${o.X + 0.5} ${o.Y} ${o.Z + 0.5} 180 0`, 500);
  const before = await counters();
  const healthBefore = readPool ? await pool() : null;
  let raised = null;
  if (shield) { bot.activateItem(true); await sleep(500); raised = bot.handState; } // raised (off hand) after the teleport, facing the attacker
  let hurtAt = 0, blockSounds = 0;
  const onHurt = (e) => { if (e === bot.entity && !hurtAt) hurtAt = Date.now(); };
  const onSound = (name, pos, volume) => { if (/shield\.block/.test(name) && volume >= 0.9) blockSounds++; }; // vanilla's block (Iron Reprisal's own clang is at 0.5)
  bot.on('entityHurt', onHurt);
  bot.on('soundEffectHeard', onSound);
  await cmd(`/summon ${type} ${o.X + 0.5} ${o.Y} ${o.Z - dist + 0.5} {PersistenceRequired:1b,Tags:["reprisal"]${extra}}`, 200);
  const t0 = Date.now();
  let atHit = null, far = 0, mob = null, nearest_ = Infinity, back = 0;
  while (Date.now() - t0 < 8000) {
    await sleep(50);
    mob = mob && bot.entities[mob.id] ? mob : nearest(type);
    if (!mob) continue;
    const d = flat(mob.position, bot.entity.position);
    nearest_ = Math.min(nearest_, d);
    back = Math.max(back, d - nearest_); // how far it was put back from its closest approach (the game pushes nobody on a block)
    if (hurtAt && atHit === null) atHit = d;
    if (atHit !== null) far = Math.max(far, d - atHit);
    if (hurtAt && Date.now() - hurtAt > watchMs) break;
  }
  bot.removeListener('entityHurt', onHurt);
  bot.removeListener('soundEffectHeard', onSound);
  const healthAfter = readPool ? await pool() : null; // read with the shield still up, and the mob gone before it comes down
  await clear();
  if (shield) bot.deactivateItem();
  const after = await counters();
  return { hit: !!hurtAt, flew: far, back, atHit, blockSounds, raised, healthBefore, healthAfter, c: diff(before, after) };
}

/** The bot's Cerberus health pool, from /cerb healthstate ("Custom HP: 102.75/102.75"). */
async function pool() {
  const at = await cmd(`/cerb healthstate ${NAME}`, 900);
  const m = (saw(/Custom HP/, at) || '').replace(/,/g, '').match(/Custom HP: ([\d.]+)\/([\d.]+)/);
  return m ? Number(m[1]) : NaN;
}

async function setup(o, level, metal, pieces) {
  await cmd(`/cerb skill ${NAME} Heavy_Armor_Training level ${level}`, 400);
  await wear(metal, pieces);
  await sleep(300);
}

// ── phases ────────────────────────────────────────────────────────

async function throwPhase() {
  const o = await arena(1200, 300);
  await setup(o, 100, 'iron', 4);
  const at = await cmd(`/cerb reprisal ${NAME}`, 900);
  check('/cerb reprisal reads the player: Heavy Armor Mastery 100, four heavy pieces, push 1.000',
    /Heavy Armor Mastery 100, heavy pieces 4, push 1\.000/.test(saw(/Heavy Armor Mastery \d+, heavy pieces/, at) || ''), saw(/heavy pieces/, at) || 'nothing');
  const r = await attack(o);
  check('A husk\'s melee hit lands on the bot in full iron at Heavy Armor Mastery 100: the husk is thrown, once',
    r.hit && r.c.thrown === 1, `hit ${r.hit}, counters ${JSON.stringify(r.c)}`);
  check('...it flies well back from where it struck (more than 3 blocks)', r.flew > 3, `${r.flew.toFixed(2)} blocks`);
  measured.push(['throw', 100, 4, r.flew]);
}

async function controls() {
  const o = await arena(1300, 300);
  await shieldChecks(o);
  // Below its level.
  await setup(o, 9, 'iron', 4);
  let r = await attack(o);
  check('Heavy Armor Mastery 9 (it opens at 10): the husk\'s hit lands, nobody is thrown', r.hit && r.c.thrown === 0 && r.flew < 1.2,
    `hit ${r.hit}, flew ${r.flew.toFixed(2)}, ${JSON.stringify(r.c)}`);
  // No heavy armor.
  await setup(o, 100, 'leather', 4);
  r = await attack(o);
  check('Leather armor at Heavy Armor Mastery 100: not thrown (heavy armor only)', r.hit && r.c.thrown === 0 && r.flew < 1.2,
    `hit ${r.hit}, flew ${r.flew.toFixed(2)}, ${JSON.stringify(r.c)}`);
  // Projectiles never: back in full iron at 100, where a melee blow would throw.
  await setup(o, 100, 'iron', 4);
  // A summon with NBT skips the spawn's own equipment: the skeleton gets its bow here (without one it punches).
  r = await attack(o, 'skeleton', { dist: 8, watchMs: 2500, extra: ',equipment:{mainhand:{id:"minecraft:bow",count:1}}' });
  check('A skeleton\'s arrows hit the bot in full iron at 100: never thrown (projectiles never trigger it)',
    r.hit && r.c.thrown === 0 && r.c.notLanded === 0, `hit ${r.hit}, ${JSON.stringify(r.c)}`);
  // Knockback resistance counts, as for any knockback: a ravager (0.75) is thrown, but much less far than a husk.
  const husk = await attack(o);
  r = await attack(o, 'ravager', { dist: 4 });
  check('Knockback resistance counts: a ravager (0.75) is thrown, a quarter as hard, and lands much nearer than a husk',
    r.hit && r.c.thrown === 1 && r.flew < husk.flew * 0.6, `ravager ${r.flew.toFixed(2)} blocks, husk ${husk.flew.toFixed(2)}`);
  measured.push(['ravager', 100, 4, r.flew]);
}

/**
 * A raised shield, first, on an emptied inventory: put on after several armor swaps, mineflayer's own view of the off hand
 * could hold a shield the server never placed there (the shield then never rose: hand state 0).
 */
async function shieldChecks(o) {
  await cmd('/clear @s', 500);
  await cmd('/give @s shield', 500);
  const shield = bot.inventory.items().find(it => it.name === 'shield');
  if (shield) { try { await bot.equip(shield, 'off-hand'); } catch (e) { note('shield: ' + e.message); } }
  await sleep(300);
  await setup(o, 100, 'iron', 4);
  note(`off hand: ${(bot.inventory.slots[45] || {}).name || 'empty'}`);
  let r;
  // Raised, it takes the husk's blows: no hurt and nothing off the Cerberus health pool (until 2026-10-28 the pool lost
  // health behind a raised shield), and Iron Reprisal throws nothing (a blocked blow never landed).
  r = await attack(o, 'husk', { dist: 3, shield: true, pool: true });
  check('A raised shield takes the husk\'s blows: no hurt, nothing off the Cerberus health pool, nobody thrown by Iron Reprisal (its counter says the shield took them)',
    !r.hit && r.c.thrown === 0 && r.c.shield >= 1 && r.healthAfter === r.healthBefore,
    `hit ${r.hit}, health ${r.healthBefore} -> ${r.healthAfter}, hand state ${r.raised} (3 = the off hand's shield in use), block sounds ${r.blockSounds}, put back ${r.back.toFixed(2)} blocks, ${JSON.stringify(r.c)}`);
  // The control: the same shield held but lowered. The blow lands, the pool loses health, the husk is thrown.
  r = await attack(o, 'husk', { dist: 3, pool: true });
  check('...the shield lowered (the control): the blow lands, the pool loses health, and the husk is thrown',
    r.hit && r.c.thrown === 1 && r.healthAfter < r.healthBefore, `health ${r.healthBefore} -> ${r.healthAfter}, ${JSON.stringify(r.c)}`);
  await cmd('/clear @s shield', 300);
}

async function distances() {
  const o = await arena(1400, 300);
  const rows = [];
  for (const level of [10, 55, 100]) {
    for (const pieces of [1, 2, 4]) {
      await setup(o, level, 'iron', pieces);
      const r = await attack(o);
      rows.push({ level, pieces, ...r });
      measured.push(['distance', level, pieces, r.flew]);
      note(`Heavy Armor Mastery ${level}, ${pieces} iron piece${pieces > 1 ? 's' : ''}: thrown ${r.c.thrown}, flew ${r.flew.toFixed(2)} blocks`);
    }
  }
  // A weak push leaves the husk in reach: it strikes again after the cooldown and is thrown again (two in the window).
  check('Every trial: the hit landed and the husk was thrown', rows.every(r => r.hit && r.c.thrown >= 1), JSON.stringify(rows.map(r => r.c.thrown)));
  const at = (l, p) => rows.find(r => r.level === l && r.pieces === p).flew;
  check('More heavy pieces throw farther (1 < 2 < 4 at level 100)', at(100, 1) < at(100, 2) && at(100, 2) < at(100, 4),
    `${at(100, 1).toFixed(2)} / ${at(100, 2).toFixed(2)} / ${at(100, 4).toFixed(2)}`);
  check('A higher level throws farther (10 < 55 < 100 in full iron)', at(10, 4) < at(55, 4) && at(55, 4) < at(100, 4),
    `${at(10, 4).toFixed(2)} / ${at(55, 4).toFixed(2)} / ${at(100, 4).toFixed(2)}`);
}

/** The sandbox's combat.yml with {@code lines} added under heavy-armor-training.iron-reprisal for the body, then put back. */
async function withCombat(lines, body) {
  const dir = process.env.SERVER_DIR;
  if (!dir) { check('this phase needs SERVER_DIR (the sandbox server folder)', false); return; }
  const file = path.join(dir, 'plugins', 'CerberusPlugin', 'combat.yml');
  const original = fs.readFileSync(file, 'utf8');
  if (/iron-reprisal:/.test(original)) { check('the sandbox combat.yml has no iron-reprisal section of its own (the phase adds one)', false); return; }
  const edited = original.replace(/^(  heavy-armor-training:\n)/m, `$1    iron-reprisal:\n${lines.map(l => '      ' + l + '\n').join('')}`);
  if (edited === original) { check('the phase found heavy-armor-training in the sandbox combat.yml', false); return; }
  try {
    fs.writeFileSync(file, edited);
    await cmd('/cerb reload', 2500);
    await body();
  } finally {
    fs.writeFileSync(file, original);
    await cmd('/cerb reload', 2500);
  }
}

async function pvp() {
  const o = await arena(1500, 300);
  await setup(o, 100, 'iron', 4);
  const t = await connect(SECOND);
  await sleep(1500);
  const strike = async () => {
    await clear();
    t.chat(`/tp @s ${o.X + 0.5} ${o.Y} ${o.Z - 1.5} 0 0`); await sleep(1200);
    await cmd(`/tp @s ${o.X + 0.5} ${o.Y} ${o.Z + 0.5} 180 0`, 600);
    const before = await counters();
    const start = t.entity.position.clone();
    t.selfPush = 0;
    const me = t.nearestEntity(e => e.type === 'player' && e.username === NAME);
    if (me) t.attack(me);
    let far = 0;
    const t0 = Date.now();
    while (Date.now() - t0 < 1500) { await sleep(50); far = Math.max(far, flat(t.entity.position, start)); }
    return { far, push: t.selfPush, c: diff(before, await counters()) };
  };
  let r = await strike();
  check('PvP off (the default): a player who hits you is not thrown, and the counter says "PvP off"', r.c.thrown === 0 && r.c.pvpOff === 1,
    `sent ${r.push.toFixed(2)} blocks/tick, moved ${r.far.toFixed(2)}, ${JSON.stringify(r.c)}`);
  await withCombat(['pvp: true'], async () => {
    await sleep(1200); // the next swing: past the attacker's cooldown
    r = await strike();
    check('pvp: true: the player who hits you is thrown back (the server sends their client the push, and they fly)',
      r.c.thrown === 1 && r.push > 0.3 && r.far > 1.5, `sent ${r.push.toFixed(2)} blocks/tick, moved ${r.far.toFixed(2)}, ${JSON.stringify(r.c)}`);
  });
  t.quit(); await sleep(500);
}

async function cooldown() {
  const o = await arena(1600, 300);
  await setup(o, 100, 'iron', 4);
  await withCombat(['cooldown-ms: 4000', 'strength-at-unlock: 0.05', 'max-strength: 0.05'], async () => {
    // A push too weak to send the husk out of reach: it strikes again within the cooldown.
    const r = await attack(o, 'husk', { watchMs: 3500 });
    check('With cooldown-ms 4000: the first hit throws, the next ones within 4 s don\'t (the counter says "cooldown")',
      r.c.thrown === 1 && r.c.cooldown >= 1, JSON.stringify(r.c));
  });
}

async function technique() {
  const o = await arena(1700, 300);
  const xpForLevel = l => Math.round(75 * Math.pow(Math.max(1, l), 1.85) + 40 * Math.max(1, l));
  await setup(o, 9, 'iron', 4);
  await cmd(`/cerb skill ${NAME} Heavy_Armor_Training xp ${xpForLevel(9) - 0.1}`, 600); // quietly, a tenth of an XP short
  const since = chat.length;
  const r = await attack(o, 'husk', { watchMs: 2500 });
  await sleep(1500);
  const line = saw(/New technique: Iron Reprisal/, since);
  check('A real hit lifts Heavy Armor Mastery 9 -> 10: the level-up announces "New technique: Iron Reprisal"', r.hit && !!line, line || chat.slice(since).map(x => x.m).join(' / '));
  const at = await cmd(`/cerb reprisal ${NAME}`, 900);
  const mine = saw(/Heavy Armor Mastery \d+, heavy pieces/, at) || '';
  check('...and it is on at 10: push 0.400 in full iron', /Heavy Armor Mastery 10, heavy pieces 4, push 0\.400/.test(mine), mine);
}

// ── Martial Expertise ─────────────────────────────────────────────

const plain = v => {
  if (v == null) return '';
  if (typeof v === 'string') { try { return plain(JSON.parse(v)); } catch { return v; } }
  if (Array.isArray(v)) return v.map(plain).join('');
  if (typeof v === 'object') {
    if (v.type === 'compound' || v.type === 'list') return plain(v.value);
    let s = v.text !== undefined ? plain(v.text) : (v.value !== undefined ? plain(v.value) : '');
    if (v.extra) s += plain(v.extra);
    if (v[''] !== undefined) s += plain(v['']);
    return s;
  }
  return String(v);
};
const title = () => bot.currentWindow ? plain(bot.currentWindow.title).replace(/§./g, '') : '';
async function waitTitle(re, ms = 4000) { const t0 = Date.now(); while (Date.now() - t0 < ms) { if (bot.currentWindow && re.test(title())) return true; await sleep(100); } return false; }
const slotLore = i => { const it = bot.currentWindow && bot.currentWindow.slots[i]; return it && it.customLore ? it.customLore.map(plain).join(' | ').replace(/§./g, '') : ''; };
const slotName = i => { const it = bot.currentWindow && bot.currentWindow.slots[i]; return it ? (it.customName ? plain(it.customName) : it.name).replace(/§./g, '') : ''; };

async function agility() {
  const o = await arena(1800, 300);
  for (const s of ['Critical_Strike', 'Dual_Wielding', 'Navigation', 'Stealth']) await cmd(`/cerb skill ${NAME} ${s} level 1`, 300);
  await cmd(`/cerb skill ${NAME} Martial_Expertise level 50`, 600);
  await cmd('/stats', 1500);
  const ok = await waitTitle(/Stats & Attributes/);
  const tile = slotName(11) + ' | ' + slotLore(11);
  check('Stats page: Martial Expertise feeds Agility, +1.25 per level', ok && /Martial Expertise \+1\.25/.test(tile), tile.slice(0, 400));
  check('...and Agility is 64.5 at Martial Expertise 50 (the other Agility skills at 1 give 2.0)', ok && /64\.5/.test(tile), tile.slice(0, 200));
  if (bot.currentWindow) bot.closeWindow(bot.currentWindow);
  await sleep(400);
  // A real level-up by a fist kill names Agility.
  const xpForLevel = l => Math.round(75 * Math.pow(Math.max(1, l), 1.85) + 40 * Math.max(1, l));
  await cmd(`/cerb skill ${NAME} Martial_Expertise level 11`, 400);
  await cmd(`/cerb skill ${NAME} Martial_Expertise xp ${xpForLevel(11) - 0.1}`, 500);
  await cmd('/clear @s', 400);   // bare hands: the kill trains Martial Expertise
  await wear('iron', 4);
  await cmd('/effect give @s strength 60 4 true', 400); // +15 base damage a punch: a few punches, not a minute of them
  await clear();
  await cmd(`/summon husk ${o.X + 0.5} ${o.Y} ${o.Z - 1.5} {NoAI:1b,PersistenceRequired:1b,Tags:["reprisal"]}`, 1000);
  const since = chat.length;
  const t0 = Date.now();
  let h = nearest('husk');
  while (h && bot.entities[h.id] && Date.now() - t0 < 60000) {
    await bot.lookAt(h.position.offset(0, 1.4, 0), true);
    bot.attack(h);
    await sleep(650);
    h = bot.entities[h.id] ? h : null;
  }
  await sleep(1500);
  await cmd('/effect clear @s strength', 300);
  const line = saw(/Martial Expertise reached level 12/, since);
  check('A fist kill lifts Martial Expertise 11 -> 12: the level-up names the Agility it added', !!line && /Agility/.test(line), line || chat.slice(since).map(x => x.m).join(' / ').slice(0, 300));
  await cmd(`/cerb skill ${NAME} Martial_Expertise level 1`, 300);
}

const PHASES = { throw: throwPhase, controls, distances, pvp, cooldown, technique, agility };

(async () => {
  bot = await connect();
  await sleep(2500);
  await cmd('/gamerule spawn_mobs false', 400);
  await cmd('/gamemode survival', 400);
  await cmd('/time set midnight', 400);
  await cmd('/attribute @s minecraft:knockback_resistance base set 1', 400); // the bot stays put: only the attacker's flight counts
  await cmd('/cerb reprisal reset', 500);
  const run = phaseArg === 'all' ? ['throw', 'controls', 'distances', 'technique', 'agility'] : phaseArg.split(',');
  for (const p of run) {
    if (!PHASES[p]) { console.log('unknown phase ' + p); continue; }
    console.log(`\n── ${p} ──`);
    try { await PHASES[p](); } catch (e) { check(`${p} ran without an error`, false, e.stack); }
  }
  await cmd('/attribute @s minecraft:knockback_resistance base set 0', 400);
  await wear('iron', 0);
  await cmd(`/cerb skill ${NAME} Heavy_Armor_Training level 1`, 300);
  if (measured.length) console.log('\nMEASURED ' + JSON.stringify(measured));
  console.log(`\n${results.filter(Boolean).length}/${results.length} passed`);
  if (failed.length) console.log('FAILED: ' + failed.join(' | '));
  bot.quit();
  setTimeout(() => process.exit(failed.length ? 1 : 0), 500);
})();
