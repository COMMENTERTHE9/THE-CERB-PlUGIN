// XP integrity in game (docs/STATUS.md, "XP integrity"; attributes.yml xp-integrity). LOCAL SANDBOX servers only; the bot
// must be op. Each exploit has a reproduction, which FAILS on a build without the fix (the exploit pays), and a positive
// control showing that legitimate XP still pays (it passes on both builds).
// Usage: node xp.js [phase]
//   magic    - the Fireball Wand cast into the sky pays nothing; at a zombie it pays Spell Weaving 15, Intelligence 5 and
//              Elemental Mastery 10, once even when the blast reaches three zombies. The Mage's Arcane Bolt (Arcane
//              Knowledge) and Frost Nova (Elemental Mastery) pay when they reach a zombie and nothing otherwise; the Mage's
//              Fireball into the sky pays nothing
//   origin   - a monster spawner's zombie pays a quarter of an ordinary one (a real spawner in a dark room); a calf bred
//              from two cows pays nothing, and its parent pays in full
//   named    - a zombie a player named "Legendary Bob" with a name tag pays as a zombie, not as mobs.yml's Legendary (x4);
//              one an admin summoned with that name still pays x4
//   creative - a kill in creative mode pays nothing; the same kill in survival pays
//   pvp      - killing a player pays no skill XP, and a player's hit trains no defensive skill; a zombie's hit does
//              (brings in a second player, CerbTarget)
//   credit   - a kill trains the skill behind the last hit: an arrow that kills after the archer switched to a sword
//              trains Ranged Precision (a real shot, and an exact /damage one), a thrown trident Weapon Mastery; a sword kill
//              still trains Blade Mastery
//   crit     - Critical Strike from punching one zombie stops at what its kill pays; a fresh zombie pays again, and the
//              first one's kill still pays in full
//   idle     - Survival from a zombie's hits stops after a minute without doing anything and comes back on the next action;
//              one zombie's hits pay Survival at most what its kill pays (CerbTarget is the defender)
//   all      - every phase (the default)
const mineflayer = require('mineflayer');
const { toNotchianYaw, toNotchianPitch } = require('mineflayer/lib/conversions');
const phase = process.argv[2] || 'all';
const NAME = process.argv[3] || 'CerbSmoke';
const OTHER = 'CerbTarget';
const sleep = ms => new Promise(r => setTimeout(r, ms));
const chat = [];
const results = [];
const failed = [];
const check = (name, ok, detail = '') => {
  results.push(ok);
  if (!ok) failed.push(name);
  console.log(`${ok ? 'PASS' : 'FAIL'}  ${name}${detail ? '  (' + detail + ')' : ''}`);
};
const note = s => console.log('      ' + s);

let bot, other = null;
function connect(name) {
  const b = mineflayer.createBot({ host: '127.0.0.1', port: Number(process.env.MC_PORT || 25565), username: name, version: '1.21.11', auth: 'offline' });
  if (name === NAME) b.on('messagestr', (m, pos) => { if (pos !== 'game_info') chat.push(m); });
  b.on('kicked', r => { console.log('KICKED', name, r); process.exit(2); });
  return new Promise(r => b.once('spawn', () => r(b)));
}
async function cmd(c, wait = 700) { const at = chat.length; bot.chat(c); await sleep(wait); return at; }
const saw = (re, at) => chat.slice(at).find(m => re.test(m));

// ── Skill XP, read back from the server (/cerb skill <player> <skill> xp 0 adds nothing and says the total) ──
const xpForLevel = l => Math.round(75 * Math.pow(Math.max(1, l), 1.85) + 40 * Math.max(1, l));
async function skillXp(skill, who = NAME) {
  const at = await cmd(`/cerb skill ${who} ${skill} xp 0`, 650);
  const m = (saw(/is now level \d+ \(\d+ XP\)/, at) || '').match(/level (\d+) \((\d+) XP\)/);
  if (!m) return NaN;
  let t = Number(m[2]);
  for (let l = 1; l < Number(m[1]); l++) t += xpForLevel(l);
  return t;
}
/** What `fn` paid each of `skills` (the skills are put back to level 1 first, so the weak-mob rule never applies). */
async function gains(skills, fn, who = NAME) {
  for (const s of skills) await cmd(`/cerb skill ${who} ${s} level 1`, 350);
  const before = {};
  for (const s of skills) before[s] = await skillXp(s, who);
  await fn();
  const out = {};
  for (const s of skills) out[s] = (await skillXp(s, who)) - before[s];
  return out;
}
const show = g => Object.entries(g).map(([k, v]) => `${k.replace(/_/g, ' ')} +${v}`).join(', ');

// ── The arena: a stone floor in the sky near spawn (mobs here are level 1), night, no natural mobs ──
let X = 0, Y = 200, Z = 0;
async function arena() {
  const sp = bot.spawnPoint || bot.entity.position;
  X = Math.floor(sp.x) + 60; Z = Math.floor(sp.z) - 20;
  await cmd('/gamerule spawn_mobs false', 300);
  await cmd('/time set midnight', 300); // zombies don't burn
  await cmd('/gamerule advance_time false', 300);
  await cmd('/gamerule spawn_phantoms false', 300);
  await cmd(`/fill ${X - 14} ${Y - 1} ${Z - 14} ${X + 20} ${Y - 1} ${Z + 14} stone`, 500);
  await cmd(`/fill ${X - 14} ${Y} ${Z - 14} ${X + 20} ${Y + 5} ${Z + 14} air`, 500);
  await stand(0, 0);
  await clearMobs();
  await cmd('/gamemode survival', 300);
  await cmd('/effect clear @s', 300);
  await cmd('/effect give @s resistance infinite 4 true', 300);
  await cmd('/effect give @s saturation infinite 1 true', 300);
}
async function clearMobs() { await cmd('/kill @e[type=!player,distance=..60]', 500); await sleep(300); }
async function stand(dx, dz, yaw = 0, pitch = 0) { await cmd(`/tp @s ${X + dx + 0.5} ${Y} ${Z + dz + 0.5} ${yaw} ${pitch}`, 600); }
/** `/cerb spawn <spec>` at arena offset (dx, dz); the bot goes back to where it stood. */
async function spawnAt(dx, dz, spec) {
  const back = bot.entity.position.clone(), yaw = bot.entity.yaw, pitch = bot.entity.pitch;
  await cmd(`/tp @s ${X + dx + 0.5} ${Y} ${Z + dz + 0.5}`, 350);
  await cmd(`/cerb spawn ${spec}`, 700);
  await cmd(`/tp @s ${back.x} ${back.y} ${back.z}`, 450);
  await bot.look(yaw, pitch, true);
  await sleep(200);
}
const mobs = (name, r = 16) => Object.values(bot.entities).filter(e => e.name === name && e.position.distanceTo(bot.entity.position) < r)
  .sort((a, b) => a.position.distanceTo(bot.entity.position) - b.position.distanceTo(bot.entity.position));
async function lookAt(e) { await bot.lookAt(e.position.offset(0, e.height * 0.6, 0), true); await sleep(150); }

// ── Items and hands ─────────────────────────────────────────────
/** A clean inventory with `item` in hand; returns its hotbar index or -1. */
async function hold(item, n = 1) {
  await cmd('/clear @s', 300);
  await cmd(`/give @s ${item} ${n}`, 700);
  const name = item.replace(/\[.*$/, '');
  const i = bot.inventory.slots.findIndex((it, s) => it && it.name === name && s >= 36 && s <= 44);
  if (i < 0) return -1;
  bot.setQuickBarSlot(i - 36);
  await sleep(350);
  return i - 36;
}
/** Add `item` to the inventory without selecting it; returns its hotbar index or -1. */
async function add(item, n = 1) {
  await cmd(`/give @s ${item} ${n}`, 700);
  return bot.inventory.slots.findIndex((it, s) => it && it.name === item && s >= 36 && s <= 44) - 36;
}
const inHand = () => { const it = bot.inventory.slots[36 + bot.quickBarSlot]; return it ? it.name : 'air'; };

/** Hit `e` until it is gone (a full swing each time, stepping next to it when it is out of reach); returns the swings, or -1 if it outlived `max`. */
async function swingKill(e, max = 80, delay = 650) {
  for (let s = 0; s < max; s++) {
    const m = bot.entities[e.id];
    if (!m || !m.isValid) return s;
    if (m.position.distanceTo(bot.entity.position) > 2.6) await cmd(`/tp @s ${m.position.x.toFixed(2)} ${m.position.y.toFixed(2)} ${(m.position.z - 1.6).toFixed(2)} 0 0`, 500);
    await lookAt(m);
    bot.attack(m);
    await sleep(delay);
  }
  return -1;
}
const gone = e => !bot.entities[e.id] || !bot.entities[e.id].isValid;
/** A level-1 zombie that stands still, `dz` blocks ahead; returns it. */
async function zombie(dz = 2, dx = 0) {
  await spawnAt(dx, dz, 'zombie 1 noai');
  return mobs('zombie', 12).find(e => Math.abs(e.position.z - (Z + dz + 0.5)) < 1.2 && Math.abs(e.position.x - (X + dx + 0.5)) < 1.2) || mobs('zombie', 12)[0];
}
/** Kill a mob with a netherite sword; returns its Blade Mastery XP, or NaN when it didn't die. */
async function swordKill(z) {
  if (!z) return NaN;
  const g = await gains(['Blade_Mastery'], async () => { await swingKill(z); await sleep(900); });
  if (!gone(z)) { note(`the ${z.name} didn't die`); return NaN; }
  return g.Blade_Mastery;
}

// ── Keys as a 1.21.11 client sends them (the Mage abilities) ────
let sequence = 1000;
function rclick() {
  const rotation = { x: toNotchianYaw(bot.entity.yaw), y: toNotchianPitch(bot.entity.pitch) };
  bot._client.write('use_item', { hand: 0, sequence: sequence++, rotation });
}
function keyF() { bot._client.write('block_dig', { status: 6, location: { x: 0, y: 0, z: 0 }, face: 0, sequence: 0 }); }
function keyQ() { bot._client.write('block_dig', { status: 4, location: { x: 0, y: 0, z: 0 }, face: 0, sequence: 0 }); bot.swingArm('right'); }
/** The Mage's rank and XP as /class shows them ("rank 12 (34/1234 XP)"), '?' when unreadable. */
async function mageXp() {
  const at = await cmd('/class', 800);
  const m = (saw(/Mage: rank \d+ \(\d+\/\d+/, at) || '').match(/rank \d+ \(\d+\/\d+/);
  return m ? m[0] : '?';
}
async function fresh() { await cmd(`/cerb class ${NAME} cooldowns`, 350); await cmd(`/cerb mana ${NAME} full`, 400); await sleep(500); }
/** /class equip, waiting out the fight lock (the loadout in use can't change within 8 s of a hit). */
async function equip(spec) {
  for (let k = 0; k < 2; k++) {
    const at = await cmd('/class equip ' + spec, 260);
    await sleep(150);
    if (!saw(/Not during a fight/, at)) return;
    await sleep(8600);
  }
}
/** Switch to loadout n, waiting out the fight lock and the 10 s gap between switches; true once it is in use. */
async function use(n) {
  for (let k = 0; k < 3; k++) {
    const at = await cmd('/class loadout ' + n, 700);
    if (saw(/Now using Loadout/, at) || saw(/already/i, at)) { await sleep(1100); return true; }
    if (saw(/Not during a fight/, at)) { await sleep(8600); continue; }
    const w = (saw(/(\d+(?:\.\d+)?) ?s/, at) || '').match(/(\d+(?:\.\d+)?) ?s/);
    await sleep(w ? Number(w[1]) * 1000 + 500 : 10500);
  }
  return false;
}

// ════════════════════════════════════════════════════════════════
// magic: the spell has to reach an enemy
// ════════════════════════════════════════════════════════════════
const MAGIC = ['Spell_Weaving', 'Intelligence', 'Elemental_Mastery', 'Arcane_Knowledge'];
async function magic() {
  await clearMobs(); await stand(0, 0);
  await cmd(`/cerb skill ${NAME} Intelligence level 5`, 400); // the Mage's entry (a class once open stays open)
  await cmd(`/cerb class ${NAME} mage rank 12`, 450);
  await cmd('/class controls quick', 450);
  for (const s of ['a1', 'a2', 'a3', 'a4', 'a5', 'p1', 'p2', 'p3', 'k']) await equip(`1 ${s} none`);
  await use(1); // nothing on right-click: the wand casts its own Fireball
  await cmd('/clear @s', 300);
  await cmd('/givetestitem fireball', 800);
  const wand = bot.inventory.slots.findIndex((it, s) => it && it.name === 'blaze_rod' && s >= 36 && s <= 44);
  if (wand >= 0) bot.setQuickBarSlot(wand - 36);
  await sleep(400);
  check('Holding the Fireball Wand', inHand() === 'blaze_rod', inHand());

  // 1. Into the sky (the exploit: it paid every magic skill per cast, and mana comes back faster than it is spent).
  await fresh();
  let at = chat.length;
  let g = await gains(MAGIC, async () => { await bot.look(0, Math.PI / 2 * 0.95, true); await sleep(200); rclick(); await sleep(3500); });
  check('Fireball Wand into the sky: no magic XP', !!saw(/You cast Fireball/, at) && Object.values(g).every(v => v === 0), `${saw(/You cast Fireball/, at) ? 'cast' : 'NOT cast'}; ${show(g)}`);

  // 2. At a zombie: the old per-cast amounts.
  await fresh();
  let z = await zombie(6);
  at = chat.length;
  g = await gains(MAGIC, async () => { await lookAt(z); rclick(); await sleep(2500); });
  check('Fireball Wand at a zombie: Spell Weaving 15, Intelligence 5, Elemental Mastery 10',
    g.Spell_Weaving === 15 && g.Intelligence === 5 && g.Elemental_Mastery === 10 && g.Arcane_Knowledge === 0, show(g));

  // 3. One blast, three zombies: paid once.
  await clearMobs(); await fresh();
  for (const dx of [-1, 0, 1]) await spawnAt(dx, 6, 'zombie 1 noai');
  z = mobs('zombie').find(e => Math.abs(e.position.x - (X + 0.5)) < 0.6) || mobs('zombie')[0];
  g = await gains(MAGIC, async () => { await lookAt(z); rclick(); await sleep(2500); });
  check('A blast that reaches three zombies pays once', g.Spell_Weaving === 15 && g.Intelligence === 5 && g.Elemental_Mastery === 10, show(g));

  // 4. The Mage's spells (right-click: Arcane Bolt, F: Frost Nova, Q: Fireball), with the wand as the focus.
  await clearMobs();
  for (const [s, a] of [['a1', 'arcane_bolt'], ['a2', 'frost_nova'], ['a3', 'fireball'], ['a4', 'none'], ['a5', 'none']]) await equip(`2 ${s} ${a}`);
  for (const s of ['p1', 'p2', 'p3', 'k']) await equip(`2 ${s} none`);
  check('Loadout 2 (Arcane Bolt, Frost Nova, Fireball) in use', await use(2));

  await fresh();
  z = await zombie(5);
  let m0 = await mageXp();
  g = await gains(MAGIC, async () => { await lookAt(z); rclick(); await sleep(1200); });
  let m1 = await mageXp();
  check('Arcane Bolt at a zombie: Spell Weaving 15, Intelligence 5, Arcane Knowledge 10', g.Spell_Weaving === 15 && g.Intelligence === 5 && g.Arcane_Knowledge === 10 && g.Elemental_Mastery === 0, show(g));
  check('Control: ...and Mage class XP', m1 !== m0 && m0 !== '?', `Mage ${m0} -> ${m1}`);
  // Creative mode: a spell that hits pays nothing, skill or class XP (the same rule as kills).
  await clearMobs(); await fresh();
  z = await zombie(5);
  await cmd('/gamemode creative', 400);
  m0 = await mageXp();
  g = await gains(MAGIC, async () => { await lookAt(z); rclick(); await sleep(1200); });
  m1 = await mageXp();
  await cmd('/gamemode survival', 400);
  check('In creative mode a spell that hits pays no magic or class XP', Object.values(g).every(v => v === 0) && m1 === m0 && m0 !== '?', `${show(g)}; Mage ${m0} -> ${m1}`);
  await clearMobs(); await fresh();
  g = await gains(MAGIC, async () => { await bot.look(0, Math.PI / 2 * 0.95, true); await sleep(200); rclick(); await sleep(1200); });
  check('Arcane Bolt at nothing: no magic XP', Object.values(g).every(v => v === 0), show(g));

  await fresh();
  z = await zombie(2);
  g = await gains(MAGIC, async () => { await bot.look(0, 0, true); await sleep(200); keyF(); await sleep(1200); });
  check('Frost Nova around a zombie: Spell Weaving 15, Intelligence 5, Elemental Mastery 10', g.Spell_Weaving === 15 && g.Intelligence === 5 && g.Elemental_Mastery === 10, show(g));
  await clearMobs(); await fresh();
  g = await gains(MAGIC, async () => { keyF(); await sleep(1200); });
  check('Frost Nova with no one near: no magic XP', Object.values(g).every(v => v === 0), show(g));

  await fresh();
  g = await gains(MAGIC, async () => { await bot.look(0, Math.PI / 2 * 0.95, true); await sleep(200); keyQ(); await sleep(3500); });
  check('The Mage\'s Fireball into the sky: no magic XP', Object.values(g).every(v => v === 0), show(g));
  await fresh();
  z = await zombie(6);
  g = await gains(MAGIC, async () => { await lookAt(z); keyQ(); await sleep(2500); });
  check('The Mage\'s Fireball at a zombie: Spell Weaving 15, Intelligence 5, Elemental Mastery 10', g.Spell_Weaving === 15 && g.Intelligence === 5 && g.Elemental_Mastery === 10, show(g));

  // Back to the neutral character the other scripts expect (loadout 1, empty).
  await clearMobs();
  await use(1);
  for (const s of ['a1', 'a2', 'a3']) await equip(`2 ${s} none`);
}

// ════════════════════════════════════════════════════════════════
// origin: farms' mobs
// ════════════════════════════════════════════════════════════════
async function origin() {
  await clearMobs(); await stand(0, 0);
  await hold('netherite_sword');
  const ordinary = await swordKill(await zombie(2));
  check('Control: an ordinary zombie pays its kill XP', ordinary === 40, `Blade Mastery +${ordinary}`);

  // A real monster spawner in a dark stone room (spawners work with mob spawning off; the room keeps every light rule happy).
  const R = { x1: X + 8, x2: X + 16, z1: Z - 4, z2: Z + 4 }, SX = X + 12;
  await cmd(`/fill ${R.x1} ${Y - 1} ${R.z1} ${R.x2} ${Y + 3} ${R.z2} stone hollow`, 600);
  await cmd(`/tp @s ${X + 10.5} ${Y} ${Z + 0.5} -90 0`, 700);
  await cmd(`/setblock ${SX} ${Y} ${Z} spawner{SpawnData:{entity:{id:"minecraft:zombie"}},Delay:0s,MinSpawnDelay:200s,MaxSpawnDelay:400s,SpawnCount:1s,MaxNearbyEntities:1s,RequiredPlayerRange:16s,SpawnRange:2s}`, 600);
  let sz = null;
  for (let i = 0; i < 40 && !sz; i++) { await sleep(500); sz = mobs('zombie', 10)[0]; }
  await cmd(`/setblock ${SX} ${Y} ${Z} air`, 500);
  if (sz) {
    await cmd('/effect give @e[type=zombie,distance=..10] slowness infinite 255 true', 400);
    const fromSpawner = await swordKill(sz);
    check('A monster spawner\'s zombie pays a quarter of an ordinary one', fromSpawner === ordinary / 4, `Blade Mastery +${fromSpawner} (ordinary +${ordinary})`);
  } else check('A monster spawner\'s zombie pays a quarter of an ordinary one', false, 'the spawner made no zombie in 20 s');
  await cmd(`/fill ${R.x1} ${Y - 1} ${R.z1} ${R.x2} ${Y + 3} ${R.z2} air`, 600);
  await cmd(`/fill ${R.x1} ${Y - 1} ${R.z1} ${R.x2} ${Y - 1} ${R.z2} stone`, 500);
  await clearMobs(); await stand(0, 0);

  // Breeding: two cows fed wheat make a calf. The calf pays nothing; a parent (summoned) pays in full.
  await cmd(`/summon cow ${X + 0.5} ${Y} ${Z + 2.5}`, 400);
  await cmd(`/summon cow ${X + 1.5} ${Y} ${Z + 2.5}`, 900);
  const parents = mobs('cow', 8).map(e => e.id);
  await hold('wheat', 4);
  for (const id of parents) { const c = bot.entities[id]; if (c) { await lookAt(c); bot.activateEntity(c); await sleep(500); } }
  let calf = null;
  for (let i = 0; i < 40 && !calf; i++) { await sleep(500); calf = mobs('cow', 12).find(e => !parents.includes(e.id)); }
  await cmd('/effect give @e[type=cow,distance=..12] slowness infinite 255 true', 400);
  await add('netherite_sword');
  const sw = bot.inventory.slots.findIndex((it, s) => it && it.name === 'netherite_sword' && s >= 36 && s <= 44);
  if (sw >= 0) bot.setQuickBarSlot(sw - 36);
  await sleep(300);
  if (calf) {
    const xp = await swordKill(calf);
    check('A calf bred from two cows pays nothing', xp === 0, Number.isNaN(xp) ? 'the calf did not die' : `Blade Mastery +${xp}`);
  } else check('A calf bred from two cows pays nothing', false, 'no calf in 20 s');
  const parent = parents.map(id => bot.entities[id]).find(Boolean);
  if (parent) {
    const xp = await swordKill(parent);
    check('Control: its parent pays in full', xp === 40, Number.isNaN(xp) ? 'the cow did not die' : `Blade Mastery +${xp}`);
  } else check('Control: its parent pays in full', false, 'no parent left');
  await clearMobs(); await stand(0, 0);
}

// ════════════════════════════════════════════════════════════════
// named: a name tag doesn't make a mob a registry mob
// ════════════════════════════════════════════════════════════════
async function named() {
  await clearMobs(); await stand(0, 0);
  await hold('name_tag[custom_name="Legendary Bob"]');
  const z = await zombie(2);
  await lookAt(z);
  bot.activateEntity(z);
  await sleep(800);
  const lines = chat.slice(await cmd('/cerb mobs 6', 700)).filter(m => /^zombie/.test(m));
  note('server sees: ' + (lines[0] || 'no line'));
  await add('netherite_sword');
  const sw = bot.inventory.slots.findIndex((it, s) => it && it.name === 'netherite_sword' && s >= 36 && s <= 44);
  if (sw >= 0) bot.setQuickBarSlot(sw - 36);
  await sleep(300);
  const tagged = await swordKill(z);
  check('A zombie a player named "Legendary Bob" pays as a zombie (not x4)', tagged === 40, `Blade Mastery +${tagged}`);

  // An admin's "Legendary" mob is still one (mobs.yml matches what admins and plugins name).
  await clearMobs();
  await cmd(`/summon zombie ${X + 0.5} ${Y} ${Z + 2.5} {CustomName:"Legendary Bob",NoAI:1b}`, 900);
  const g = await gains(['Blade_Mastery'], async () => {
    await cmd('/damage @e[type=zombie,limit=1,sort=nearest] 1000000000 minecraft:player_attack by @s', 1200);
  });
  check('Control: one an admin summoned with that name pays x4 (160)', g.Blade_Mastery === 160, `Blade Mastery +${g.Blade_Mastery}`);
  await clearMobs();
}

// ════════════════════════════════════════════════════════════════
// creative
// ════════════════════════════════════════════════════════════════
async function creative() {
  await clearMobs(); await stand(0, 0);
  await hold('netherite_sword');
  await cmd('/gamemode creative', 400);
  const c = await swordKill(await zombie(2));
  await cmd('/gamemode survival', 400);
  check('A kill in creative mode pays nothing', c === 0, Number.isNaN(c) ? 'it did not die' : `Blade Mastery +${c}`);
  const s = await swordKill(await zombie(2));
  check('Control: the same kill in survival pays', s === 40, `Blade Mastery +${s}`);
}

// ════════════════════════════════════════════════════════════════
// pvp: players pay no skill XP (for now: attributes.yml xp-integrity.pvp-xp)
// ════════════════════════════════════════════════════════════════
async function bringOther() {
  if (!other) {
    other = await connect(OTHER);
    await sleep(2500);
  }
  await cmd(`/gamemode survival ${OTHER}`, 400);
  await cmd(`/tp ${OTHER} ${X + 4.5} ${Y} ${Z + 0.5}`, 800);
  await cmd(`/effect clear ${OTHER}`, 300);
  await cmd(`/cerb heal ${OTHER}`, 500);
}
async function pvp() {
  await clearMobs(); await stand(0, 0);
  await hold('netherite_sword');
  await bringOther();
  // A kill: the other player dies to this player's hit.
  let died = false;
  other.once('death', () => { died = true; }); // listening before the hit: the respawn comes at once
  let g = await gains(['Blade_Mastery'], async () => {
    await cmd(`/damage ${OTHER} 1000000000 minecraft:player_attack by ${NAME}`, 2000);
  });
  check('Killing a player pays no skill XP', died && g.Blade_Mastery === 0, `${died ? 'killed' : 'did not die'}; Blade Mastery +${g.Blade_Mastery}`);
  await sleep(1500);
  await bringOther();

  // A hit taken from a player trains no defensive skill; a zombie's does.
  other.swingArm('right'); // an active player
  g = await gains(['Survival'], async () => { await cmd(`/damage ${OTHER} 1 minecraft:player_attack by ${NAME}`, 1000); }, OTHER);
  check('A player\'s hit trains no Survival', g.Survival === 0, `Survival +${g.Survival}`);
  await spawnAt(4, 2, 'zombie 1 noai');
  other.swingArm('right');
  g = await gains(['Survival'], async () => { await cmd(`/damage ${OTHER} 1 minecraft:mob_attack by @e[type=zombie,limit=1,sort=nearest]`, 1000); }, OTHER);
  check('Control: a zombie\'s hit trains Survival', g.Survival === 1, `Survival +${g.Survival}`);
  await clearMobs();
}

// ════════════════════════════════════════════════════════════════
// credit: the kill's skill is the last hit's
// ════════════════════════════════════════════════════════════════
const WEAPON_SKILLS = ['Blade_Mastery', 'Ranged_Precision', 'Weapon_Mastery', 'Martial_Expertise'];
const uuidInts = u => u.replace(/-/g, '').match(/.{8}/g).map(h => parseInt(h, 16) | 0);
async function credit() {
  await clearMobs(); await stand(0, 0);
  // Control: a sword kill trains Blade Mastery.
  await hold('netherite_sword');
  let z = await zombie(2);
  let g = await gains(WEAPON_SKILLS, async () => { await swingKill(z); await sleep(900); });
  check('Control: a sword kill trains Blade Mastery', gone(z) && g.Blade_Mastery === 40 && g.Ranged_Precision === 0, `${gone(z) ? 'killed' : 'NOT killed'}; ${show(g)}`);

  // An exact arrow hit (/damage by an arrow this player owns) while a sword is in hand.
  await clearMobs();
  z = await zombie(4);
  await cmd(`/summon arrow ${X + 0.5} ${Y + 3} ${Z + 1.5} {NoGravity:1b,Owner:[I;${uuidInts(bot.player.uuid).join(',')}]}`, 800);
  g = await gains(WEAPON_SKILLS, async () => {
    await cmd(`/damage @e[type=zombie,limit=1,sort=nearest] 1000000000 minecraft:arrow by @e[type=arrow,limit=1,sort=nearest] from @s`, 1200);
  });
  check('An arrow\'s kill with a sword in hand trains Ranged Precision (exact hit)', gone(z) && inHand() === 'netherite_sword' && g.Ranged_Precision === 40 && g.Blade_Mastery === 0, `${gone(z) ? 'killed' : 'NOT killed'}; ${show(g)}; in hand: ${inHand()}`);

  // A real shot: bow drawn, released, and the sword taken out while the arrow flies (the zombie is weakened first by
  // damage no player made, so one arrow kills).
  await clearMobs();
  await hold('bow');
  await add('arrow', 16);
  await add('netherite_sword');
  const bowSlot = bot.inventory.slots.findIndex((it, s) => it && it.name === 'bow' && s >= 36 && s <= 44) - 36;
  const swordSlot = bot.inventory.slots.findIndex((it, s) => it && it.name === 'netherite_sword' && s >= 36 && s <= 44) - 36;
  bot.setQuickBarSlot(bowSlot); await sleep(300);
  z = await zombie(8);
  await cmd('/damage @e[type=zombie,limit=1,sort=nearest] 48 minecraft:generic', 900);
  g = await gains(WEAPON_SKILLS, async () => {
    await lookAt(z);
    bot.activateItem(); await sleep(1200);
    bot.deactivateItem();
    bot.setQuickBarSlot(swordSlot); // at once: the arrow is still in the air
    await sleep(1800);
  });
  check('A real arrow kill after switching to a sword trains Ranged Precision, not Blade Mastery',
    gone(z) && g.Ranged_Precision === 40 && g.Blade_Mastery === 0, `${gone(z) ? 'killed' : 'NOT killed'}; ${show(g)}; in hand: ${inHand()}`);

  // A thrown trident: the hand is empty when it lands.
  await clearMobs();
  await hold('trident');
  z = await zombie(6);
  await cmd('/damage @e[type=zombie,limit=1,sort=nearest] 48 minecraft:generic', 900);
  g = await gains(WEAPON_SKILLS, async () => {
    await lookAt(z);
    bot.activateItem(); await sleep(900);
    bot.deactivateItem();
    await sleep(1800);
  });
  check('A thrown trident\'s kill trains Weapon Mastery, not the empty hand (Martial Expertise)',
    gone(z) && g.Weapon_Mastery === 40 && g.Martial_Expertise === 0, `${gone(z) ? 'killed' : 'NOT killed'}; ${show(g)}; in hand: ${inHand()}`);
  await clearMobs();
  await cmd('/kill @e[type=trident,distance=..30]', 300);
}

// ════════════════════════════════════════════════════════════════
// crit: one mob, a bounded amount
// ════════════════════════════════════════════════════════════════
async function crit() {
  await clearMobs(); await stand(0, 0);
  await cmd('/clear @s', 300);
  const free = [...Array(9).keys()].find(i => !bot.inventory.slots[36 + i]);
  bot.setQuickBarSlot(free == null ? 0 : free);
  await sleep(300);
  const z = await zombie(2);
  // 35 punches of about 1 damage: a level-1 zombie (50 health) stays alive; its kill pays 40.
  let g = await gains(['Critical_Strike'], async () => {
    for (let i = 0; i < 35; i++) { const m = bot.entities[z.id]; if (!m) break; await lookAt(m); bot.attack(m); await sleep(560); }
    await sleep(600);
  });
  const alive = !!bot.entities[z.id];
  check('35 punches on one zombie pay Critical Strike at most its kill XP (40)', alive && g.Critical_Strike === 40, `${alive ? 'alive' : 'died'}; Critical Strike +${g.Critical_Strike} (without the limit: 2 per hit, 8 per critical one, 70 or more)`);
  const z2 = await zombie(2, 2);
  g = await gains(['Critical_Strike'], async () => {
    for (let i = 0; i < 5; i++) { await lookAt(z2); bot.attack(z2); await sleep(560); }
    await sleep(600);
  });
  check('Control: a fresh zombie pays again (2 per hit, 8 for a critical one)', g.Critical_Strike >= 10 && g.Critical_Strike <= 40, `Critical Strike +${g.Critical_Strike}`);
  await hold('netherite_sword');
  const k = await swordKill(z);
  check('Control: the first zombie\'s kill still pays in full', k === 40, Number.isNaN(k) ? 'it did not die' : `Blade Mastery +${k}`);
  await clearMobs();
}

// ════════════════════════════════════════════════════════════════
// idle: defensive XP needs a player who is doing something
// ════════════════════════════════════════════════════════════════
async function idle() {
  await clearMobs(); await stand(0, 0);
  await bringOther();
  await spawnAt(4, 2, 'zombie 1 noai');
  const hit = () => cmd(`/damage ${OTHER} 1 minecraft:mob_attack by @e[type=zombie,limit=1,sort=nearest]`, 900);
  other.swingArm('right');
  let g = await gains(['Survival'], hit, OTHER);
  check('Control: an active player\'s hit by a zombie trains Survival', g.Survival === 1, `Survival +${g.Survival}`);
  note('the defender does nothing for 65 s...');
  await sleep(65000);
  g = await gains(['Survival'], hit, OTHER);
  check('After a minute doing nothing: no Survival XP', g.Survival === 0, `Survival +${g.Survival}`);
  other.swingArm('right');
  await sleep(300);
  g = await gains(['Survival'], hit, OTHER);
  check('Control: one action later it trains again', g.Survival === 1, `Survival +${g.Survival}`);

  // One zombie's hits: at most its kill XP (40). A fresh zombie, 45 hits of 1, the defender active and healed.
  await clearMobs();
  await spawnAt(4, 2, 'zombie 1 noai');
  g = await gains(['Survival'], async () => {
    for (let i = 0; i < 45; i++) {
      other.swingArm('right');
      await hit();
      if (i % 10 === 9) await cmd(`/cerb heal ${OTHER}`, 300);
    }
  }, OTHER);
  check('45 hits from one zombie pay Survival at most its kill XP (40)', g.Survival === 40, `Survival +${g.Survival} (1 per hit without the limit: 45)`);
  await clearMobs();
}

(async () => {
  bot = await connect(NAME);
  await sleep(3000);
  await arena();
  const phases = { magic, origin, named, creative, pvp, credit, crit, idle };
  const run = phase === 'all' ? Object.keys(phases) : phase.split(',');
  for (const p of run) {
    if (!phases[p]) { console.log('unknown phase ' + p); continue; }
    console.log(`\n── ${p} ──`);
    try { await phases[p](); } catch (e) { check(`${p} ran to the end`, false, e.message); }
  }
  // Leave the sandbox as the other scripts expect it.
  await clearMobs();
  for (const s of ['Blade_Mastery', 'Ranged_Precision', 'Weapon_Mastery', 'Martial_Expertise', 'Critical_Strike', ...MAGIC]) await cmd(`/cerb skill ${NAME} ${s} level 1`, 250);
  await cmd('/clear @s', 300);
  await cmd('/gamerule spawn_mobs true', 300);
  await cmd('/gamerule advance_time true', 300);
  await cmd('/gamerule spawn_phantoms true', 300);
  const passed = results.filter(Boolean).length;
  console.log(`\n${passed}/${results.length} passed`);
  if (failed.length) console.log('FAILED: ' + failed.join(' | '));
  if (other) other.quit();
  bot.quit();
  setTimeout(() => process.exit(failed.length ? 1 : 0), 500);
})();
