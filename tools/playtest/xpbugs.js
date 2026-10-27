// The owner's XP reports (2026-10-27): Fishing paid no XP in a superflat pond (the menu showed level 0, 0/115), and bow
// kills paid no Ranged Precision. Run on a superflat sandbox server (op the bot first); a name that has never played
// starts every skill as a new player's would.
// Usage: node xpbugs.js <phase[,phase...]> [name]
//   levels   - a character's starting levels: every skill starts at level 1 (it used to be 0), in the command and the menu
//   repro    - catches in a two-deep pond dug into the superflat ground with a plain rod (XP, HUD line, menu tile), a
//              full-health zombie killed by real bow shots, and Woodcutting as the working control; the Fishing level and
//              XP it ends with are written to xpbugs-<name>.json for the rejoin phase
//   rejoin   - after a quit (and a database check by the caller): the same Fishing level and XP, in the command and the menu
//   bow      - Ranged Precision from real shots: a bow kill; a switch to a sword before the arrow lands; a crossbow's
//              arrows, with and without a switch; a crossbow's firework rockets, with and without a switch; no Blade or
//              Weapon Mastery from any of them. Spawn-egg mobs pay nothing (xp-integrity by-origin, positive control:
//              the summoned zombies above); a naturally spawned zombie pays in full
//   creative - creative mode pays no skill XP (XP integrity, unchanged): a catch, a bow kill and a log cut pay nothing
//              and the HUD says why; the same steps back in survival pay
const fs = require('fs');
const mineflayer = require('mineflayer');
const { Vec3 } = require('vec3');
const phaseArg = process.argv[2] || 'repro';
const NAME = process.argv[3] || 'CerbNewcomer';
const STATE = `${process.env.XPBUGS_DIR || '.'}/xpbugs-${NAME}.json`;
const sleep = ms => new Promise(r => setTimeout(r, ms));
const chat = [];
const bars = [];
const results = [];
const failed = [];
const check = (name, ok, detail = '') => {
  results.push(!!ok);
  if (!ok) failed.push(name);
  console.log(`${ok ? 'PASS' : 'FAIL'}  ${name}${detail !== '' ? '  (' + detail + ')' : ''}`);
};
const note = s => console.log('      ' + s);
let bot;
const dead = new Set();

function connect(name = NAME) {
  const b = mineflayer.createBot({ host: '127.0.0.1', port: 25565, username: name, version: '1.21.11', auth: 'offline' });
  b.on('messagestr', (m, pos) => { if (pos !== 'game_info') chat.push(m); else bars.push({ t: Date.now(), m }); });
  b.on('kicked', r => { console.log('KICKED', name, r); process.exit(2); });
  b.on('entityDead', e => dead.add(e.id));
  b._client.on('action_bar', p => { try { bars.push({ t: Date.now(), m: require('prismarine-chat')(b.registry).fromNotch(p.text).toString() }); } catch (e) { /* keep the rest */ } });
  b.on('death', () => { console.log('      THE BOT DIED: the rest of this phase is not valid'); results.push(false); failed.push('the bot died'); });
  bot = b;
  return new Promise(r => b.once('spawn', () => r(b)));
}
async function cmd(c, wait = 600) { const at = chat.length; bot.chat(c); await sleep(wait); return at; }
const saw = (re, since) => chat.slice(since).find(m => re.test(m));
const sawAll = (re, since) => chat.slice(since).filter(m => re.test(m));

/** A skill's level and in-level XP, read without changing them (an admin's 0 XP). */
async function skill(name) {
  const at = await cmd(`/cerb skill ${NAME} ${name} xp 0`, 700);
  const m = (saw(/is now level \d+ \(\d+ XP\)/, at) || '').match(/level (\d+) \((\d+) XP\)/);
  return m ? { level: Number(m[1]), xp: Number(m[2]) } : { level: NaN, xp: NaN };
}
const show = s => `level ${s.level}, ${s.xp} XP`;
const need = l => Math.round(75 * Math.pow(Math.max(1, l), 1.85) + 40 * Math.max(1, l));
/** Total XP behind a level and in-level XP, counted from level 0 (level 0 and level 1 both need 115 to go on). */
const total = s => { let t = 0; for (let i = 0; i < s.level; i++) t += need(i); return t + s.xp; };
const gain = (a, b) => total(b) - total(a);
const inv = () => bot.inventory.slots.filter(Boolean);
async function hold(item) {
  await cmd(`/give @s ${item}`, 700);
  const name = item.replace(/\[.*$/, '');
  const i = bot.inventory.slots.findIndex((it, s) => it && it.name === name && s >= 36 && s <= 44);
  if (i >= 0) bot.setQuickBarSlot(i - 36);
  await sleep(300);
  return i;
}
const hudSince = t => [...new Set(bars.filter(b => b.t >= t).map(b => b.m.replace(/\s+/g, ' ').trim()))];

// ── menu ──────────────────────────────────────────────────────────

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
const flat = i => { const it = bot.currentWindow && bot.currentWindow.slots[i]; return it && it.customLore ? it.customLore.map(plain).join(' ').replace(/\s+/g, ' ').trim() : ''; };
const findSlot = re => { if (!bot.currentWindow) return -1; for (let i = 0; i < 54; i++) if (re.test(slotName(i))) return i; return -1; };
async function waitTitle(re, ms = 4000) { const t0 = Date.now(); while (Date.now() - t0 < ms) { if (bot.currentWindow && re.test(title())) return true; await sleep(100); } return false; }
async function click(i, wait = 700) { await bot.clickWindow(i, 0, 0); await sleep(wait); }

/** The tile the Utility Skills screen shows for {@code name}: its title and lore, read from the real menu. */
async function utilityTile(name) {
  if (bot.currentWindow) { bot.closeWindow(bot.currentWindow); await sleep(400); }
  await cmd('/menu', 900);
  if (!await waitTitle(/Player Menu/)) return { name: '', lore: 'menu did not open' };
  await sleep(300);
  await click(19); await waitTitle(/Skills$/);
  await click(24);
  if (!await waitTitle(/Utility Skills/)) return { name: '', lore: 'Utility Skills did not open: ' + title() };
  await sleep(300);
  const i = findSlot(new RegExp('^' + name));
  const tile = i >= 0 ? { name: slotName(i), lore: flat(i) } : { name: '', lore: name + ' tile missing' };
  bot.closeWindow(bot.currentWindow); await sleep(400);
  return tile;
}

// ── fishing ───────────────────────────────────────────────────────

async function catchOne(timeoutMs = 60000) {
  try {
    await Promise.race([bot.fish(), new Promise((_, rej) => setTimeout(() => rej(new Error('timeout')), timeoutMs))]);
    await sleep(1200);
    return true;
  } catch (e) {
    note('no catch: ' + (e.message || e));
    try { bot.activateItem(); await sleep(300); } catch (x) { /* reel in */ }
    return false;
  }
}

/**
 * A clean patch of superflat ground for one phase (grass at y -61, so a player stands at -60): earlier runs leave
 * trees, ponds and mobs behind, so each phase clears its own patch and stands in the middle of it.
 */
async function arena(cx, cz) {
  await cmd(`/tp @s ${cx + 0.5} -60 ${cz + 0.5}`, 2000); // load the chunks first
  await cmd(`/fill ${cx - 12} -60 ${cz - 16} ${cx + 12} -40 ${cz + 12} air`, 1200);
  await cmd(`/fill ${cx - 12} -61 ${cz - 16} ${cx + 12} -61 ${cz + 12} grass_block`, 900);
  await cmd(`/fill ${cx - 12} -63 ${cz - 16} ${cx + 12} -62 ${cz + 12} dirt`, 900);
  await cmd('/kill @e[type=!player,distance=..48]', 700);
  await cmd(`/tp @s ${cx + 0.5} -60 ${cz + 0.5} 0 0`, 800);
  return { X: cx, Y: -60, Z: cz };
}

/** A pond two deep dug into the superflat ground south of the spot, open to the sky; the bot looks at it. */
async function pond(o) {
  const { X, Y, Z } = o;
  await cmd(`/fill ${X - 2} ${Y - 2} ${Z + 2} ${X + 2} ${Y - 1} ${Z + 6} water`, 900);
  await cmd(`/tp @s ${X + 0.5} ${Y} ${Z + 0.5} 0 30`, 900);
  await bot.lookAt(new Vec3(X + 0.5, Y - 1, Z + 4.5), true);
  return o;
}

/** {@code n} catches with a plain rod; the Fishing level before and after, and what the HUD and chat showed. */
async function fishSome(n) {
  await cmd('/clear @s', 400);
  await hold('fishing_rod');
  const before = await skill('Fishing');
  const at = chat.length, t0 = Date.now();
  let caught = 0;
  for (let i = 0; i < n; i++) {
    if (await catchOne()) caught++;
    note(`after catch ${i + 1}: Fishing ${show(await skill('Fishing'))}`);
  }
  const after = await skill('Fishing');
  const hud = hudSince(t0);
  return { before, after, caught, hud, chat: chat.slice(at).filter(m => !/is now level/.test(m)) };
}

// ── combat ────────────────────────────────────────────────────────

const WEAPON = { bow: 'bow', crossbow: 'crossbow', sword: 'iron_sword' };
/** The hotbar slot (0-8) holding an item, or -1. */
const hotbarOf = name => { const i = bot.inventory.slots.findIndex((it, s) => it && it.name === name && s >= 36 && s <= 44); return i >= 0 ? i - 36 : -1; };
function select(weapon) { const i = hotbarOf(WEAPON[weapon] || weapon); if (i >= 0) bot.setQuickBarSlot(i); return i; }

/** A bow, a crossbow and an iron sword in the hotbar, arrows, nothing in the offhand (/item doesn't run on this server: /give). */
async function kit() {
  await cmd('/clear @s', 500);
  for (const it of ['bow', 'crossbow', 'iron_sword', 'arrow 64']) await cmd('/give @s ' + it, 400);
  await sleep(300);
  const missing = Object.values(WEAPON).filter(n => hotbarOf(n) < 0);
  if (missing.length) note('kit: missing from the hotbar: ' + missing.join(', '));
}

/** Firework rockets in the offhand and no arrows: a crossbow loads them. */
async function rocketsInOffhand(rocket) {
  await cmd('/clear @s minecraft:arrow', 400);
  await cmd(`/give @s ${rocket} 64`, 700);
  const it = bot.inventory.items().find(i => i.name === 'firework_rocket');
  if (it) { try { await bot.equip(it, 'off-hand'); } catch (e) { note('offhand: ' + e.message); } }
  await sleep(500);
  const off = bot.inventory.slots[45];
  if (!off || off.name !== 'firework_rocket') note('rockets are not in the offhand: ' + (off ? off.name : 'empty'));
}

async function combatSkills() {
  return { rp: await skill('Ranged_Precision'), bm: await skill('Blade_Mastery'), wm: await skill('Weapon_Mastery') };
}

/**
 * A zombie that stands still {@code dist} blocks north of the spot and can't burn (a zombie that burns to death in daylight
 * more than 5 s after the last hit has no killer, in vanilla too); a summoned mob (spawn reason COMMAND) pays in full.
 */
async function summonZombie(o, dist = 9) {
  await cmd('/kill @e[type=zombie]', 800);
  await cmd(`/summon zombie ${o.X + 0.5} ${o.Y} ${o.Z - dist + 0.5} {NoAI:1b,PersistenceRequired:1b,Tags:["xpbugs"]}`, 1000);
  await cmd('/effect give @e[type=zombie,tag=xpbugs] fire_resistance infinite 0 true', 600);
  return nearestZombie();
}
function nearestZombie() {
  return Object.values(bot.entities).filter(e => e.name === 'zombie' && !dead.has(e.id))
    .sort((a, b) => a.position.distanceTo(bot.entity.position) - b.position.distanceTo(bot.entity.position))[0];
}
async function aim(z, dy = 1.4) { await bot.lookAt(z.position.offset(0, dy, 0), true); }
const alive = z => z && bot.entities[z.id] && !dead.has(z.id);

/** The nearest zombie held still and harmless, moved {@code dist} blocks north of the spot, unburnable. */
async function hold_(o, dist) {
  await cmd('/effect give @e[type=zombie,limit=1,sort=nearest] slowness infinite 255 true', 400);
  await cmd('/effect give @e[type=zombie,limit=1,sort=nearest] weakness infinite 255 true', 400);
  await cmd(`/tp @e[type=zombie,limit=1,sort=nearest] ${o.X + 0.5} ${o.Y} ${o.Z - dist + 0.5}`, 600);
  await cmd('/effect give @e[type=zombie,limit=1,sort=nearest] fire_resistance infinite 0 true', 500);
}

/** Shoot {@code z} with {@code weapon} until it dies; {@code switchTo}: the slot taken right after each shot is fired. */
async function shootDead(z, weapon, switchTo = null, max = 30) {
  let shots = 0;
  while (alive(z) && shots < max) {
    select(weapon); await sleep(200);
    if (weapon === 'bow') {
      await aim(z); bot.activateItem(); await sleep(1100); await aim(z); bot.deactivateItem();
    } else { // charge, then fire
      bot.activateItem(); await sleep(1450); bot.deactivateItem(); await sleep(200);
      await aim(z, process.env.FIREWORK_DY ? Number(process.env.FIREWORK_DY) : 1.2); bot.activateItem(); await sleep(60); bot.deactivateItem();
    }
    if (switchTo != null) select(switchTo); // in the same tick as the release: the shot is in the air
    shots++;
    await sleep(1100);
  }
  await sleep(900);
  return { dead: !alive(z), shots };
}

/** Swing the sword at {@code z} until it dies (it must stand within reach). */
async function swordDead(z, max = 40) {
  select('sword'); await sleep(200);
  let swings = 0;
  while (alive(z) && swings < max) {
    // Knockback carries it off (slowness doesn't hold against a hit): step back into reach.
    if (z.position.distanceTo(bot.entity.position) > 2.6) await cmd(`/tp @s ${z.position.x.toFixed(2)} ${z.position.y.toFixed(2)} ${(z.position.z + 1.6).toFixed(2)} 180 0`, 500);
    await aim(z, 1.0); bot.attack(z); swings++; await sleep(700);
  }
  await sleep(900);
  return { dead: !alive(z), swings };
}

/** One kill scenario: what it paid Ranged Precision, Blade Mastery and Weapon Mastery, and the kill's debug line. */
async function scenario(label, z, kill) {
  if (!z) { check(label + ': a zombie to shoot', false, 'none found'); return null; }
  const b = await combatSkills();
  const at = chat.length;
  const r = await kill(z);
  const a = await combatSkills();
  const out = { dead: r.dead, rp: gain(b.rp, a.rp), bm: gain(b.bm, a.bm), wm: gain(b.wm, a.wm), debug: sawAll(/\[KillXp\]/, at).join(' / ') };
  if (process.env.XPBUGS_TRACE) note('chat during ' + label + ':\n        ' + chat.slice(at).filter(m => !/is now level/.test(m)).join('\n        '));
  note(`${label}: ${r.dead ? 'dead' : 'ALIVE'} after ${r.shots ?? r.swings}; Ranged Precision +${out.rp}, Blade Mastery +${out.bm}, Weapon Mastery +${out.wm}; ${out.debug || 'no [KillXp] line'}`);
  return out;
}

// ── phases ────────────────────────────────────────────────────────

async function levels() {
  const names = ['Fishing', 'Ranged_Precision', 'Blade_Mastery', 'Weapon_Mastery', 'Woodcutting', 'Mining', 'Smithing', 'Survival'];
  const got = {};
  for (const n of names) got[n] = await skill(n);
  note('starting levels: ' + names.map(n => `${n} ${show(got[n])}`).join('; '));
  check('Every skill of a character starts at level 1 (none at 0)', names.every(n => got[n].level >= 1), names.filter(n => !(got[n].level >= 1)).join(', '));
  const tile = await utilityTile('Fishing');
  note('Fishing tile: ' + tile.name + ' | ' + tile.lore.slice(0, 160));
  check('The menu shows Fishing at the level the command reads (Lv ' + got.Fishing.level + ', XP ' + got.Fishing.xp + ' / ' + need(got.Fishing.level) + ')',
    new RegExp('Lv ' + got.Fishing.level + '$').test(tile.name) && new RegExp('Level ' + got.Fishing.level + ' / 100').test(tile.lore)
      && new RegExp('XP ' + got.Fishing.xp + ' / ' + need(got.Fishing.level).toLocaleString('en-US')).test(tile.lore), tile.name + ' | ' + tile.lore.slice(0, 120));
}

async function repro() {
  note(`gamemode ${bot.game.gameMode}, world ${bot.game.levelType || '?'} ${bot.game.dimension}`);
  const o = await pond(await arena(300, 300));
  let at = await cmd('/cerb fish info', 1000);
  note('fish info: ' + chat.slice(at).join(' | ').slice(0, 300));

  const f = await fishSome(4);
  note('HUD while fishing: ' + f.hud.slice(0, 6).join(' | ').slice(0, 400));
  note('chat while fishing: ' + f.chat.slice(0, 6).join(' | ').slice(0, 400));
  check('Catches in a player-dug superflat pond pay Fishing XP', f.caught > 0 && gain(f.before, f.after) > 0, `${f.caught} catches; ${show(f.before)} -> ${show(f.after)}`);
  check('The XP display shows the Fishing XP on the HUD', f.hud.some(m => /\+\d+ Fishing/.test(m)), f.hud.filter(m => /Fishing/.test(m)).join(' | ').slice(0, 200) || 'no Fishing line');
  const tile = await utilityTile('Fishing');
  check('The menu tile shows the new Fishing level and XP', new RegExp('Lv ' + f.after.level + '$').test(tile.name)
    && new RegExp('XP ' + f.after.xp.toLocaleString('en-US') + ' / ').test(tile.lore), tile.name + ' | ' + tile.lore.slice(0, 120));
  try { fs.writeFileSync(STATE, JSON.stringify({ fishing: f.after })); } catch (e) { note('state not written: ' + e.message); }

  // A full-health zombie killed by real bow shots.
  await kit();
  const z = await summonZombie(o, 7);
  const k = await scenario('bow kill', z, zz => shootDead(zz, 'bow'));
  check('A bow kill pays Ranged Precision XP', k && k.dead && k.rp > 0, k ? `+${k.rp}` : '');

  // The working control: a log cut with an axe.
  await cmd('/clear @s', 400);
  await cmd(`/place feature minecraft:birch ${o.X + 5} ${o.Y} ${o.Z - 2}`, 1500);
  await hold('iron_axe');
  const wcBefore = await skill('Woodcutting');
  const log = bot.findBlock({ matching: b => b && b.name === 'birch_log', maxDistance: 10 });
  if (log) { try { await bot.lookAt(log.position.offset(0.5, 0.5, 0.5), true); await bot.dig(log, true); } catch (e) { note('dig: ' + e.message); } }
  await sleep(1200);
  const wcAfter = await skill('Woodcutting');
  check('Control: a log cut pays Woodcutting XP', gain(wcBefore, wcAfter) > 0, `${show(wcBefore)} -> ${show(wcAfter)}`);
}

async function rejoin() {
  let saved;
  try { saved = JSON.parse(fs.readFileSync(STATE, 'utf8')).fishing; } catch (e) { check('The repro phase saved its Fishing state first', false, e.message); return; }
  const now = await skill('Fishing');
  check('After rejoining, Fishing has the level and XP it had (' + show(saved) + ')', now.level === saved.level && now.xp === saved.xp, show(now));
  const tile = await utilityTile('Fishing');
  check('...and the menu tile shows them', new RegExp('Lv ' + saved.level + '$').test(tile.name) && new RegExp('XP ' + saved.xp.toLocaleString('en-US') + ' / ').test(tile.lore),
    tile.name + ' | ' + tile.lore.slice(0, 120));
}

async function bow() {
  const o = await arena(400, 300);
  await cmd(`/tp @s ${o.X + 0.5} ${o.Y} ${o.Z + 0.5} 180 0`, 800);
  await kit();

  const s1 = await scenario('bow, held', await summonZombie(o), z => shootDead(z, 'bow'));
  check('Bow kill: Ranged Precision, no Blade or Weapon Mastery', s1 && s1.dead && s1.rp > 0 && s1.bm === 0 && s1.wm === 0, s1 && `RP +${s1.rp}, BM +${s1.bm}, WM +${s1.wm}`);

  const s2 = await scenario('bow, switched to a sword before the arrow lands', await summonZombie(o), z => shootDead(z, 'bow', 'sword'));
  check('Bow kill after a switch to a sword: still Ranged Precision, no Blade Mastery', s2 && s2.dead && s2.rp > 0 && s2.bm === 0 && s2.wm === 0
    && /in hand: Blade Mastery/.test(s2.debug), s2 && `RP +${s2.rp}, BM +${s2.bm}; ${s2.debug}`);

  const s3 = await scenario('crossbow arrows, held', await summonZombie(o), z => shootDead(z, 'crossbow'));
  check('Crossbow kill: Ranged Precision, no Blade or Weapon Mastery', s3 && s3.dead && s3.rp > 0 && s3.bm === 0 && s3.wm === 0, s3 && `RP +${s3.rp}, BM +${s3.bm}, WM +${s3.wm}`);

  const s4 = await scenario('crossbow arrows, switched to a sword', await summonZombie(o), z => shootDead(z, 'crossbow', 'sword'));
  check('Crossbow kill after a switch to a sword: still Ranged Precision', s4 && s4.dead && s4.rp > 0 && s4.bm === 0, s4 && `RP +${s4.rp}, BM +${s4.bm}; ${s4.debug}`);

  // Firework rockets loaded from the offhand: their blast is the crossbow's.
  const rocket = 'firework_rocket[fireworks={flight_duration:1,explosions:[{shape:"small_ball",colors:[I;16711680]},{shape:"small_ball",colors:[I;255]},{shape:"small_ball",colors:[I;65280]}]}]';
  await rocketsInOffhand(rocket);
  const s5 = await scenario('crossbow fireworks, held', await summonZombie(o), z => shootDead(z, 'crossbow'));
  check('Crossbow firework kill: Ranged Precision', s5 && s5.dead && s5.rp > 0 && s5.bm === 0 && s5.wm === 0, s5 && `RP +${s5.rp}, BM +${s5.bm}, WM +${s5.wm}; ${s5.debug}`);
  await rocketsInOffhand(rocket);
  const s6 = await scenario('crossbow fireworks, switched to a sword', await summonZombie(o), z => shootDead(z, 'crossbow', 'sword'));
  check('Crossbow firework kill after a switch to a sword: still Ranged Precision, no Blade Mastery', s6 && s6.dead && s6.rp > 0 && s6.bm === 0,
    s6 && `RP +${s6.rp}, BM +${s6.bm}; ${s6.debug}`);
  await kit();

  // Spawn eggs: XP integrity's origin rule (SPAWNER_EGG: 0) pays nothing, for any weapon; the zombies above are the
  // positive control (summoned by a command: paid in full). A plain egg used on the ground within reach, as a player
  // does; slowness and weakness hold the zombie still and harmless and /tp moves it, all keeping its spawn reason
  // (/data doesn't run on this server).
  const egg = async dist => {
    await cmd('/kill @e[type=zombie]', 800);
    await cmd('/give @s zombie_spawn_egg 4', 600);
    select('zombie_spawn_egg'); await sleep(300);
    const ground = bot.blockAt(new Vec3(o.X, o.Y - 1, o.Z - 2));
    try { await bot.lookAt(ground.position.offset(0.5, 1, 0.5), true); await bot.activateBlock(ground, new Vec3(0, 1, 0)); } catch (e) { note('egg: ' + e.message); }
    await sleep(400);
    await hold_(o, dist);
    return nearestZombie();
  };
  let t0 = Date.now();
  const s7 = await scenario('spawn-egg zombie, bow', await egg(9), z => shootDead(z, 'bow'));
  check('A spawn-egg zombie killed by a bow pays no Ranged Precision (by-origin SPAWNER_EGG: 0, intended)', s7 && s7.dead && s7.rp === 0 && /origin SPAWNER_EGG x0/.test(s7.debug),
    s7 && `RP +${s7.rp}; ${s7.debug}`);
  const bowEggHud = hudSince(t0);
  note('HUD after the spawn-egg bow kill: ' + bowEggHud.slice(0, 6).join(' | ').slice(0, 300));
  check('The HUD says why the spawn-egg bow kill paid nothing', bowEggHud.some(m => /No Ranged Precision XP from mobs out of spawn eggs/.test(m)),
    bowEggHud.filter(m => /No |egg/i.test(m)).join(' | ').slice(0, 200) || 'nothing about it on the HUD');
  t0 = Date.now();
  const s8 = await scenario('spawn-egg zombie, sword', await egg(2), z => swordDead(z));
  check('...and a sword kill of one pays no Blade Mastery either (the same rule for every weapon)', s8 && s8.dead && s8.bm === 0 && /origin SPAWNER_EGG x0/.test(s8.debug),
    s8 && `BM +${s8.bm}; ${s8.debug}`);
  const eggHud = hudSince(t0);
  note('HUD after the spawn-egg sword kill: ' + eggHud.slice(0, 6).join(' | ').slice(0, 300));
  check('The HUD says why the spawn-egg sword kill paid nothing', eggHud.some(m => /No Blade Mastery XP from mobs out of spawn eggs/.test(m)),
    eggHud.filter(m => /No |egg/i.test(m)).join(' | ').slice(0, 200) || 'nothing about it on the HUD');

  // A natural mob's origin: natural spawning produced no mobs at all on the superflat sandbox (none in 60 s, creative or
  // survival), so the admin spawn tool makes a zombie whose spawn event says NATURAL, as a natural spawn's does.
  await cmd('/kill @e[type=zombie]', 600);
  await cmd('/cerb spawn zombie noai origin=NATURAL', 1200);
  await hold_(o, 8.5);
  let nat = nearestZombie();
  const s9 = await scenario('zombie of natural origin, bow', nat, z => shootDead(z, 'bow'));
  check('A zombie of natural origin killed by a bow pays Ranged Precision in full (origin NATURAL)', s9 && s9.dead && s9.rp > 0 && /origin NATURAL x1/.test(s9.debug),
    s9 && `RP +${s9.rp}; ${s9.debug}`);
}

async function creative() {
  await cmd('/gamemode creative', 800);
  note('gamemode now ' + bot.game.gameMode);
  const o = await pond(await arena(500, 300));
  const f = await fishSome(2);
  note('HUD while fishing in creative: ' + f.hud.slice(0, 6).join(' | ').slice(0, 300));
  check('Creative: catches pay no Fishing XP (XP integrity: creative earns nothing, unchanged)', f.caught > 0 && gain(f.before, f.after) === 0, `${f.caught} catches; ${show(f.before)} -> ${show(f.after)}`);
  check('Creative: the HUD says why the catch paid nothing', f.hud.some(m => /creative/i.test(m)), f.hud.join(' | ').slice(0, 200) || 'nothing on the HUD');

  await kit();
  let t0 = Date.now();
  const k = await scenario('creative bow kill', await summonZombie(o, 7), z => shootDead(z, 'bow'));
  const kh = hudSince(t0);
  check('Creative: a bow kill pays no Ranged Precision', k && k.dead && k.rp === 0, k ? `+${k.rp}` : '');
  check('Creative: the HUD says why the kill paid nothing', kh.some(m => /creative/i.test(m)), kh.join(' | ').slice(0, 200) || 'nothing on the HUD');

  await cmd('/clear @s', 400);
  await cmd(`/place feature minecraft:birch ${o.X + 5} ${o.Y} ${o.Z - 2}`, 1500);
  await hold('iron_axe');
  const wcBefore = await skill('Woodcutting');
  t0 = Date.now();
  const log = bot.findBlock({ matching: b => b && b.name === 'birch_log', maxDistance: 10 });
  if (log) { try { await bot.lookAt(log.position.offset(0.5, 0.5, 0.5), true); await bot.dig(log, true); } catch (e) { note('dig: ' + e.message); } }
  await sleep(1200);
  const wcAfter = await skill('Woodcutting');
  check('Creative: a log cut pays no Woodcutting XP', gain(wcBefore, wcAfter) === 0, `${show(wcBefore)} -> ${show(wcAfter)}`);
  note('HUD after the creative log cut: ' + hudSince(t0).slice(0, 4).join(' | ').slice(0, 200));

  // Positive control: the same in survival.
  await cmd('/gamemode survival', 800);
  await cmd(`/tp @s ${o.X + 0.5} ${o.Y} ${o.Z + 0.5} 0 30`, 900);
  await bot.lookAt(new Vec3(o.X + 0.5, o.Y - 1, o.Z + 4.5), true);
  const s = await fishSome(2);
  check('Control, survival: the same pond pays Fishing XP', s.caught > 0 && gain(s.before, s.after) > 0, `${show(s.before)} -> ${show(s.after)}`);
  await kit();
  const ks = await scenario('survival bow kill', await summonZombie(o, 7), z => shootDead(z, 'bow'));
  check('Control, survival: the same bow kill pays Ranged Precision', ks && ks.dead && ks.rp > 0, ks ? `+${ks.rp}` : '');
}

const PHASES = { levels, repro, rejoin, bow, creative };
/** A quiet sandbox: no natural spawning (superflat slimes killed the bot mid-test), nothing hostile around, daytime. */
async function quiet() {
  await cmd('/gamerule spawn_mobs false', 400);
  await cmd('/kill @e[type=!player]', 800);
  await cmd('/time set midnight', 400); // zombies don't burn at night
  await cmd('/cerb combatdebug', 600);
  if (!saw(/Combat debug: ON/, chat.length - 3)) await cmd('/cerb combatdebug', 600);
}

connect().then(async () => {
  await sleep(4000);
  await quiet();
  for (const ph of phaseArg.split(',')) {
    if (!PHASES[ph]) { console.log('Unknown phase ' + ph); results.push(false); continue; }
    console.log(`── ${ph} ──`);
    try { await PHASES[ph](); } catch (e) { console.log('SCRIPT ERROR in ' + ph, e.stack); results.push(false); failed.push(ph + ' (script error)'); }
  }
  const bad = results.filter(r => !r).length;
  console.log(`\n${results.length - bad}/${results.length} passed`);
  if (failed.length) console.log('FAILED: ' + failed.join(' | '));
  bot.quit(); await sleep(500); process.exit(bad ? 1 : 0);
});
setTimeout(() => { console.log('TIMEOUT'); process.exit(3); }, 1500000);
