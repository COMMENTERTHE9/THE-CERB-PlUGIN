// Ability controls (Phase 1) and the per-ability audit (Phase 2): docs/design/ABILITY_CONTROLS.md, docs/ABILITY_AUDIT.md.
// LOCAL SANDBOX servers only; the bot must be op. Usage: node abilities.js <phase> [name]
//   controls - quick keys: each key reaches its slot (right-click, F, Q, Sneak + right-click, Sneak + F); Sneak pressed in the
//              same tick as the key still counts; F and Q never swap or drop a weapon while armed, but do with a tool (positive
//              control) and with nothing equipped; vanilla right-click stays on doors/chests, mobs that aren't enemies, bows,
//              tools and blocks; a bow's abilities move to left-click; an empty key says so; one press casts once even when
//              the client sends it for both hands; the message stays readable while the HUD refreshes (taking hits); the old
//              Fireball Wand no longer hides Arcane Bolt but still casts its own spell when no right-click ability fits a focus
//   combos   - click combos: R R R / R L R / R L L / R R L and F reach slots 1-5; a lone click expires and casts nothing; clicks
//              inside a combo don't attack; Q drops again; a bow starts with left-click; a left-click on a player is a plain
//              hit and ends the combo; the scheme choice is saved
//   warrior | mage | duelist - every ability of the class individually: its key, its weapons, targeting and range, effects
//              measured in the world (damage traces, movement, statuses, buffs, attribute contributions), costs, cooldowns,
//              refusals that spend nothing, moving / jumping / sneaking (see AUDIT notes per check)
//   mixed    - abilities from three classes in one loadout, rapid presses, shared lockouts, no duplicate buffs, PvP safety
//   persist  - PERSIST=set: scheme, bar, loadout, a long cooldown; restart; PERSIST=check: the same
//   fixes    - the independent review's findings: Q strikes with the weapon back in hand; Q right after closing the
//              inventory; the off hand's own use (shield, food, torch) and an axe on a log keep right-click; Sneak let go
//              with the key; another player's wolf and invulnerable mobs are never targets; Fireball pays on its hit
//   neutral  - only the clean-up every phase ends with: loadout 1 in use and empty (other scripts such as menu.js,
//              admin.js and progression.js expect the character's plain attributes and keys)
// Bot limits handled here (each told apart from plugin behaviour): the 1.21.9+ motion packet (applied as the client does),
// item-use rotation in degrees (the server aims with it), no client-side prediction of a Q drop (counted on the ground).
// Every "nothing happens" check has a positive control (the same input doing something when it should), so a check can't
// pass because the bot's input never reached the server.
const mineflayer = require('mineflayer');
const { toNotchianYaw, toNotchianPitch } = require('mineflayer/lib/conversions');
const phase = process.argv[2] || 'controls';
const NAME = process.argv[3] || 'CerbSmoke';
const sleep = ms => new Promise(r => setTimeout(r, ms));
const chat = [];
const bars = []; // action-bar texts with their time (the HUD, and cast messages in its middle)
const results = [];
const failed = [];
const check = (name, ok, detail = '') => {
  results.push(ok);
  if (!ok) failed.push(name);
  console.log(`${ok ? 'PASS' : 'FAIL'}  ${name}${detail ? '  (' + detail + ')' : ''}`);
};
const note = s => console.log('      ' + s);

let bot, pchat;
function connect(name = NAME) {
  const b = mineflayer.createBot({ host: '127.0.0.1', port: 25565, username: name, version: '1.21.11', auth: 'offline' });
  if (name === NAME) {
    bot = b;
    b.on('messagestr', (m, pos) => { if (pos !== 'game_info') chat.push(m); });
    b._client.on('action_bar', p => {
      try { pchat = pchat || require('prismarine-chat')(b.registry); bars.push({ t: Date.now(), m: pchat.fromNotch(p.text).toString() }); } catch (e) { /* ignore */ }
    });
    // BOT LIMITATION: since 1.21.9 the server sends motion as {velocity: {x, y, z}} (blocks per tick); this mineflayer
    // still reads the old velocityX/Y/Z fields, so a dash (Charge, Lunge) or knockback never moves the bot. A vanilla
    // client applies the packet; do the same here so movement abilities are measured as a player would feel them.
    // Registered at spawn, after mineflayer's own handler (which scales the new values as old 1/8000 units): runs last.
    b.once('spawn', () => b._client.on('entity_velocity', p => {
      if (!b.entity || p.entityId !== b.entity.id || !p.velocity) return;
      b.entity.velocity.set(p.velocity.x, p.velocity.y, p.velocity.z);
    }));
  }
  b.on('kicked', r => { console.log('KICKED', name, r); process.exit(2); });
  return new Promise(r => b.once('spawn', () => r(b)));
}
async function rejoin() { bot.quit(); await sleep(2000); await connect(); await sleep(3500); }

async function cmd(c, wait = 700) { const at = chat.length; bot.chat(c); await sleep(wait); return at; }
const saw = (re, at) => chat.slice(at).find(m => re.test(m));
const sawAll = (re, at) => chat.slice(at).filter(m => re.test(m));
const near = (a, b, tol) => Math.abs(a - b) <= tol;

// ── keys, as a vanilla 1.21.11 client sends them ────────────────
let sequence = 1000;
/** Right-click in the air: use_item for the main hand (and, with `both`, the off-hand use the client sends when the main hand passes). */
async function rclick(both = false) {
  // With a mob under the crosshair in reach the client first sends the interaction (interact-at, then interact; both
  // pass for a hostile mob), then the air use, all in the same tick. (mineflayer's activateEntity waits for a smooth
  // look first, which a real client doesn't.)
  const e = underCursor();
  if (e) {
    const sneaking = bot.getControlState('sneak');
    bot._client.write('use_entity', { target: e.id, mouse: 2, x: 0, y: e.height * 0.6, z: 0, hand: 0, sneaking });
    bot._client.write('use_entity', { target: e.id, mouse: 0, hand: 0, sneaking });
  }
  // 1.21.2+ servers take the look direction from this packet: degrees, as the client sends it.
  const rotation = { x: toNotchianYaw(bot.entity.yaw), y: toNotchianPitch(bot.entity.pitch) };
  bot._client.write('use_item', { hand: 0, sequence: sequence++, rotation });
  if (both) bot._client.write('use_item', { hand: 1, sequence: sequence++, rotation });
}
/** The living mob under the crosshair within survival reach (3 blocks), as the client picks it. */
function underCursor() { const e = bot.entityAtCursor(3); return e && e.type !== 'object' && e.name !== 'item' && e.name !== 'arrow' ? e : null; }
/** Right-click a mob: interact, then the air use that follows when the interaction passes. */
async function rclickEntity(e) { bot.activateEntity(e); await rclick(); }
/** Right-click a block (door, chest), as the client does (it looks at it first). */
async function rclickBlock(p) { const b = bot.blockAt(new (require('vec3'))(p.x, p.y, p.z)); if (b) await bot.activateBlock(b); }
/** Left-click as the client sends it: an attack on the mob under the crosshair in reach, else an arm swing (air). */
function lclick() { const e = underCursor(); if (e) bot.attack(e); else bot.swingArm('right'); }
/** Left-click a mob: attack + swing. */
function attack(e) { bot.attack(e); }
function keyF() { bot._client.write('block_dig', { status: 6, location: { x: 0, y: 0, z: 0 }, face: 0, sequence: 0 }); }
/** Q: drop, then the arm swing the client plays. */
function keyQ() { bot._client.write('block_dig', { status: 4, location: { x: 0, y: 0, z: 0 }, face: 0, sequence: 0 }); bot.swingArm('right'); }
async function sneak(on, lead = 200) { bot.setControlState('sneak', on); await sleep(lead); }

/** Press `fn` with Sneak held (pressed `lead` ms before), then release. */
async function sneaking(fn, lead = 200) { await sneak(true, lead); await fn(); await sleep(300); await sneak(false); }
/** Press `fn` and Sneak in the same tick, the key's packet first (as a real client sends them). */
async function sameTick(fn) { await fn(); bot.setControlState('sneak', true); await sleep(400); await sneak(false); }

// ── what the player sees ────────────────────────────────────────
/** The middle of the HUD bar (a cast message), or '' when it shows the usual effectiveness figure. */
const middle = m => { const s = m.replace(/^.*?♥\s+/, '').replace(/\s+\S+✎\s*$/, '').trim(); return /%★$/.test(s) ? '' : s; };
/** Cast messages shown since time t, each with how long it stayed before something else replaced it. */
function flashes(t) {
  const out = [];
  for (let k = 0; k < bars.length; k++) {
    if (bars[k].t < t) continue;
    const m = middle(bars[k].m);
    if (!m || (out.length && out[out.length - 1].m === m)) continue;
    const next = bars.slice(k + 1).find(b => middle(b.m) !== m);
    out.push({ m, ms: next ? next.t - bars[k].t : Date.now() - bars[k].t });
  }
  return out;
}
const flashed = (t, re) => flashes(t).find(f => re.test(f.m));
/** Essence now (exact, from the server). */
async function essence() {
  const at = await cmd(`/cerb mana ${NAME}`, 600);
  const m = (saw(/Essence: [\d,]+ \/ [\d,]+/, at) || '').match(/Essence: ([\d,]+) \/ ([\d,]+)/);
  return m ? Number(m[1].replace(/,/g, '')) : NaN;
}
/**
 * The Essence a cast took, read from the HUD: the bar just before time t, and the first bar after it that shows the
 * cast's message (sent the moment it went off). Start at full Essence so "before" is exact; regeneration (about 70/s
 * at low levels) can't creep in between, as it does between two /cerb mana readings.
 */
function paid(t, re) {
  const mana = b => { const m = b && b.m.match(/([\d.,]+)\/[\d.,]+✎/); return m ? Number(m[1].replace(/,/g, '')) : NaN; };
  const before = [...bars].reverse().find(b => b.t < t && /✎/.test(b.m));
  const after = bars.find(b => b.t >= t && re.test(middle(b.m)));
  return mana(before) - mana(after);
}
/** Health on the HUD ("123/456♥"). */
function hud(kind) {
  for (let k = bars.length - 1; k >= 0; k--) {
    const m = bars[k].m.match(kind === 'hp' ? /([\d.,KMB]+)\/([\d.,KMB]+)♥/ : /([\d.,KMB]+)\/([\d.,KMB]+)✎/);
    if (m) return { now: Number(m[1].replace(/,/g, '')), max: Number(m[2].replace(/,/g, '')) };
  }
  return { now: NaN, max: NaN };
}
async function casts() {
  const at = await cmd('/cerb showqueues', 600);
  const m = (saw(/casts since start/, at) || '').match(/casts since start: (\d+), counters: (\d+)/);
  return m ? { casts: +m[1], counters: +m[2] } : { casts: NaN, counters: NaN };
}
/** Nearby mobs as the server sees them, nearest first ("zombie 2.0m hp 20.0/20.0 effects: slowness 10 0.9s"). The client is never told a mob's effects. */
async function mobLines(r = 16) { const at = await cmd(`/cerb mobs ${r}`, 700); return sawAll(/^[a-z_]+ [\d.]+m/, at); }
/** A class's XP into its rank, from /class ("Warrior: rank 12 (34/1234 XP)"). */
async function classXp(name) {
  const at = await cmd('/class', 800);
  const m = (saw(new RegExp(name + ': rank \\d+ \\((\\d+)\\/'), at) || '').match(/\((\d+)\//);
  return m ? Number(m[1]) : NaN;
}
async function cooldownLine() { const at = await cmd('/class', 800); return (saw(/Cooldowns:/, at) || 'Cooldowns: none').trim(); }
/** Server-side attribute value (/attribute works on Folia). */
async function attr(name) {
  const at = await cmd(`/attribute @s minecraft:${name} get`, 700);
  const m = chat.slice(at).map(x => x.match(/The value of attribute .+ for entity \S+ is ([-\d.E]+)/)).find(Boolean);
  return m ? Number(m[1]) : NaN;
}
/** "[Combat] ..." lines since `at`, and the hit's final damage (the number before the first arrow's last step). */
const traces = at => sawAll(/^\[Combat\]/, at);
const finalDamage = t => { const all = [...t.matchAll(/: ([\d.]+)(?: →|$)/g)].map(m => Number(m[1])); return all.length ? all[all.length - 1] : NaN; };
const powerDamage = t => { const m = t.match(/Power\([^)]*\): ([\d.]+)/); return m ? Number(m[1]) : NaN; };
/** A trace's steps: "Power(Swords, ...): 9.2 → Crit(x1.58 (16.0% chance)): 16.7 → Lifesteal(4.8%): 0.8". */
const steps = t => t.replace(/^\[Combat\]\s*/, '').split(' → ').map(part => {
  const v = part.match(/: ([\d.]+)\s*$/), c = part.match(/^Crit\(x([\d.]+)/);
  return { name: (part.match(/^(\w+)/) || [])[1], value: v ? Number(v[1]) : NaN, mult: c ? Number(c[1]) : 1 };
});
/** The damage a traced hit dealt: the last step before Lifesteal. */
const dealt = t => { const st = steps(t).filter(x => x.name !== 'Lifesteal'); return st.length ? st[st.length - 1].value : NaN; };
/** A weapon hit's bonus beyond Power (War Cry), with a random crit divided out: 1.15 = +15%. */
const bonusFactor = t => {
  const st = steps(t).filter(x => x.name !== 'Lifesteal'), power = st.find(x => x.name === 'Power'), crit = st.find(x => x.name === 'Crit');
  return power && st.length ? st[st.length - 1].value / power.value / (crit ? crit.mult : 1) : NaN;
};
async function debug(on) {
  for (let k = 0; k < 2; k++) {
    const at = await cmd('/cerb combatdebug', 600);
    const m = (saw(/Combat debug: (ON|OFF)/, at) || '').match(/(ON|OFF)/);
    if (m && (m[1] === 'ON') === on) return;
  }
}

// ── items and the arena ─────────────────────────────────────────
/** Give `item` (Folia has no /item) and hold it; returns its hotbar index or -1. */
async function hold(item, extra = '') {
  await cmd('/clear @s', 300); // a clean hotbar: the item lands in it
  await cmd(`/give @s ${item}${extra} 1`, 700);
  const i = bot.inventory.slots.findIndex((it, s) => it && it.name === item && s >= 36 && s <= 43);
  if (i < 0) return -1;
  bot.setQuickBarSlot(i - 36);
  await sleep(350);
  return i - 36;
}
/** Hold nothing: select an empty hotbar slot (clearing slot 8 if needed). */
async function emptyHand() {
  let i = [36, 37, 38, 39, 40, 41, 42, 43].find(s => !bot.inventory.slots[s]);
  if (i === undefined) { await cmd('/clear @s', 400); i = 36; }
  bot.setQuickBarSlot(i - 36);
  await sleep(350);
}
const inHand = () => { const it = bot.inventory.slots[36 + bot.quickBarSlot]; return it ? it.name : 'air'; };
const offHand = () => { const it = bot.inventory.slots[45]; return it ? it.name : 'air'; };
const itemsAround = (r = 8) => Object.values(bot.entities).filter(e => e.name === 'item' && e.position.distanceTo(bot.entity.position) < r).length;

let X = 0, Y = 200, Z = 0;
/** A clean, lit stone floor in the sky next to spawn: no natural mobs, nothing left from earlier checks. */
async function arena() {
  const sp = bot.spawnPoint || bot.entity.position;
  X = Math.floor(sp.x) + 40; Z = Math.floor(sp.z);
  await cmd('/gamerule doMobSpawning false', 300);
  await cmd('/time set midnight', 300); // test zombies don't burn
  await cmd(`/fill ${X - 14} ${Y - 1} ${Z - 14} ${X + 14} ${Y - 1} ${Z + 14} stone`, 500);
  await cmd(`/fill ${X - 14} ${Y} ${Z - 14} ${X + 14} ${Y + 4} ${Z + 14} air`, 500);
  await stand(0, 0);
  await clearMobs();
  await cmd('/gamemode survival', 300);
  await cmd('/effect clear @s', 300);
  await cmd('/effect give @s resistance infinite 4 true', 300); // test hits never kill the bot
  await cmd('/effect give @s saturation infinite 1 true', 300);
}
async function clearMobs() {
  await cmd('/kill @e[type=!player,distance=..60]', 500);
  await sleep(300);
}
/** Stand at arena offset (dx, dz), facing +z (yaw 0) unless told otherwise. */
async function stand(dx, dz, yaw = 0, pitch = 0) { await cmd(`/tp @s ${X + dx + 0.5} ${Y} ${Z + dz + 0.5} ${yaw} ${pitch}`, 600); }
/** A zombie of `level` at arena offset (dx, dz); `ai` false = it stands still. */
async function zombie(dx, dz, level = 1, ai = false) {
  const back = bot.entity.position.clone(), yaw = bot.entity.yaw, pitch = bot.entity.pitch;
  await cmd(`/tp @s ${X + dx + 0.5} ${Y} ${Z + dz + 0.5}`, 350);
  await cmd(`/cerb spawn zombie ${level}${ai ? '' : ' noai'}`, 700);
  await cmd(`/tp @s ${back.x} ${back.y} ${back.z}`, 450);
  await face(yaw, pitch);
  await sleep(150);
}
const zombies = (r = 16) => Object.values(bot.entities).filter(e => e.name === 'zombie' && e.position.distanceTo(bot.entity.position) < r)
  .sort((a, b) => a.position.distanceTo(bot.entity.position) - b.position.distanceTo(bot.entity.position));
const SOUTH = Math.PI, NORTH = 0; // mineflayer yaw (the arena's zombies are mostly south, +z)
/** Turn, then give the look a moment to reach the server (the bot sends it on its next physics tick). */
async function face(yaw, pitch = 0) { await bot.look(yaw, pitch, true); await sleep(150); }
async function lookAt(e, dx = 0, dy = null) { await bot.lookAt(e.position.offset(dx, dy == null ? e.height * 0.6 : dy, 0), true); await sleep(120); }
const pos = () => bot.entity.position.clone();

// ── the class setup ─────────────────────────────────────────────
async function setup(scheme = 'quick') {
  for (const [s, l] of [['Blade_Mastery', 10], ['Critical_Strike', 5], ['Intelligence', 5]]) await cmd(`/cerb skill ${NAME} ${s} level ${l}`, 350);
  for (const c of ['warrior', 'mage', 'duelist']) await cmd(`/cerb class ${NAME} ${c} rank 12`, 450);
  await cmd('/class controls ' + scheme, 500);
  await cmd('/class bar auto', 400);
}
/** Put `spec` = { a1, a2, a3, a4, a5, p1, p2, p3, k } into loadout `n` (missing slots emptied) and use it. */
async function loadout(n, spec) {
  for (const s of ['a1', 'a2', 'a3', 'a4', 'a5', 'p1', 'p2', 'p3', 'k']) await equip(`${n} ${s} ${spec[s] || 'none'}`);
  await use(n);
}
/** /class equip, waiting out the fight lock (the loadout in use can't change within 8 s of a hit). */
async function equip(spec, wait = 260) {
  for (let k = 0; k < 2; k++) {
    const at = await cmd('/class equip ' + spec, wait);
    await sleep(150);
    if (!saw(/Not during a fight/, at)) return;
    await sleep(8600);
  }
}
/** Switch to loadout n even right after an earlier switch (waits out the 10 s gap if the server says so). */
async function use(n) {
  for (let k = 0; k < 3; k++) {
    const at = await cmd('/class loadout ' + n, 700);
    if (saw(/Now using Loadout/, at) || saw(/already/i, at)) { await sleep(1100); return; }
    if (saw(/Not during a fight/, at)) { await sleep(8600); continue; }
    const w = (saw(/(\d+(?:\.\d+)?) ?s/, at) || '').match(/(\d+(?:\.\d+)?) ?s/);
    await sleep(w ? Number(w[1]) * 1000 + 500 : 10500);
  }
}
/**
 * Leave the character as the other scripts expect it: loadout 1 in use and empty. A class passive or
 * keystone would change max health, attack speed and crits in menu.js and admin.js, and an ability
 * on right-click would take the test wand's click in progression.js.
 */
async function neutral() {
  for (const s of ['a1', 'a2', 'a3', 'a4', 'a5', 'p1', 'p2', 'p3', 'k']) await equip(`1 ${s} none`);
  await use(1);
}
async function fresh() {
  await cmd(`/cerb class ${NAME} cooldowns`, 400);
  await cmd(`/cerb mana ${NAME} full`, 400);
  await sleep(450); // the global gap and any pending decision
}

// ════════════════════════════════════════════════════════════════
// controls: quick keys
// ════════════════════════════════════════════════════════════════
async function controls() {
  await arena();
  await setup('quick');
  await loadout(1, { a1: 'cleave', a2: 'war_cry', a3: 'charge', a4: 'execute', a5: 'blink' });
  await hold('iron_sword');
  await fresh();

  // 1. Each quick key reaches its slot (sword in hand, one zombie in front; Execute right after the Cleave
  //    that gives it Momentum to spend).
  await zombie(0, 2);
  const z = zombies()[0];
  const keys = [
    ['Right-click = active 1 (Cleave)', async () => { await lookAt(z); await rclick(); }, /✦ Cleave/],
    ['Sneak + right-click = active 4 (Execute)', async () => { await lookAt(z); await sneaking(rclick); }, /✦ Execute/],
    ['F = active 2 (War Cry)', async () => keyF(), /✦ War Cry/],
    ['Q = active 3 (Charge); the sword stays in hand', async () => { await face(NORTH, 0); keyQ(); }, /✦ Charge/],
    ['Sneak + F = active 5 (Blink)', async () => { await stand(0, -4, 180); await sneaking(async () => keyF()); }, /✦ Blink/],
  ];
  for (const [name, press, re] of keys) {
    const before = (await casts()).casts;
    const t = Date.now();
    await press();
    await sleep(700);
    const f = flashed(t, re);
    const after = (await casts()).casts;
    check(name, !!f && after === before + 1 && inHand() === 'iron_sword', `${f ? f.m : JSON.stringify(flashes(t))}; casts +${after - before}; hand ${inHand()}`);
  }
  check('Nothing was dropped or swapped by F and Q', itemsAround() === 0 && offHand() === 'air', `items ${itemsAround()}, off hand ${offHand()}`);

  // 2. Sneak pressed in the same tick as the key: still the Sneak slot (the key's packet arrives first).
  await fresh(); await stand(0, 0);
  let t = Date.now();
  await lookAt(z);
  await sameTick(rclick);
  await sleep(500);
  check('Right-click and Sneak in the same tick cast active 4 (Execute), not active 1', !!flashed(t, /Execute/) && !flashed(t, /Cleave/), JSON.stringify(flashes(t)));
  await fresh(); await stand(0, -4, 180);
  t = Date.now();
  await sameTick(async () => keyF());
  await sleep(500);
  check('F and Sneak in the same tick cast active 5 (Blink), not active 2', !!flashed(t, /Blink/) && !flashed(t, /War Cry/), JSON.stringify(flashes(t)));
  check('...and the sword never moved to the off hand', inHand() === 'iron_sword' && offHand() === 'air', `${inHand()} / ${offHand()}`);

  // 3. An empty key says so and still never swaps or drops.
  await equip('1 a5 none'); await sleep(300);
  t = Date.now();
  await sneaking(async () => keyF());
  await sleep(500);
  check('Sneak + F with nothing on it: "Nothing on Sneak + F", no swap', !!flashed(t, /Nothing on Sneak \+ F/) && inHand() === 'iron_sword' && offHand() === 'air',
    `${JSON.stringify(flashes(t))}; ${inHand()} / ${offHand()}`);
  await equip('1 a5 blink');

  // 4. Positive controls: with a tool F swaps and Q drops (vanilla), so the checks above saw the server act.
  await hold('stone_pickaxe');
  keyF(); await sleep(700);
  check('Positive control: with a pickaxe, F swaps it to the off hand (vanilla)', offHand() === 'stone_pickaxe', `${inHand()} / ${offHand()}`);
  keyF(); await sleep(700);
  const beforeItems = itemsAround();
  keyQ(); await sleep(900);
  check('Positive control: with a pickaxe, Q drops it (vanilla)', itemsAround() === beforeItems + 1, `items ${beforeItems} -> ${itemsAround()}`);
  await clearMobs(); await zombie(0, 2);

  // 5. Right-click stays vanilla where it means something else.
  await hold('iron_sword');
  await fresh();
  await stand(0, 0);
  await cmd(`/setblock ${X + 2} ${Y} ${Z + 1} chest`, 400);
  t = Date.now();
  await rclickBlock({ x: X + 2, y: Y, z: Z + 1 });
  await sleep(900);
  const opened = !!bot.currentWindow;
  check('Right-click on a chest with a sword opens it (no cast)', opened && !flashed(t, /✦/), `window ${opened ? 'open' : 'none'}; ${JSON.stringify(flashes(t))}`);
  if (bot.currentWindow) bot.closeWindow(bot.currentWindow);
  await sleep(500);
  await cmd(`/setblock ${X + 2} ${Y} ${Z + 1} air`, 300);
  await cmd(`/summon villager ${X + 1.5} ${Y} ${Z + 2.5} {NoAI:1b}`, 600);
  const v = Object.values(bot.entities).find(e => e.name === 'villager');
  await fresh();
  t = Date.now();
  if (v) { await lookAt(v); bot.activateEntity(v); } // the client sends no air click after a trade opens
  await sleep(900);
  check('Right-click on a villager opens trading, not an ability', !!v && !flashed(t, /✦ Cleave/), `${v ? 'villager' : 'no villager'}; ${JSON.stringify(flashes(t))}`);
  if (bot.currentWindow) bot.closeWindow(bot.currentWindow);
  await clearMobs(); await zombie(0, 2);
  // A tool, a block, food: every key vanilla (no message at all).
  for (const item of ['stone_pickaxe', 'cobblestone', 'bread']) {
    await hold(item);
    t = Date.now();
    await rclick(); keyF(); await sleep(700); keyF(); await sleep(600);
    check(`With ${item.replace('_', ' ')} in hand the keys are vanilla (no ability message)`, !flashed(t, /✦|Nothing on|needs/), JSON.stringify(flashes(t)));
  }

  // 6. A bow shoots with right-click; its slot-1 and slot-4 abilities move to left-click. Nothing in the way:
  // an arrow that hits a mob disappears, and a swing at a mob is an attack, not an air click.
  await clearMobs(); await stand(0, 0, 0, -30);
  await hold('bow'); await cmd('/give @s arrow 16', 400);
  await fresh();
  t = Date.now();
  bot.activateItem(); await sleep(1100); bot.deactivateItem(); await sleep(500);
  const arrows = Object.values(bot.entities).filter(e => e.name === 'arrow').length;
  check('A bow still shoots with right-click (an arrow flies, no cast)', arrows >= 1 && !flashed(t, /✦/), `arrows ${arrows}; ${JSON.stringify(flashes(t))}`);
  t = Date.now();
  lclick(); await sleep(700);
  check('With a bow, left-click is active 1 (Cleave needs a blade: told so, nothing spent)', !!flashed(t, /Cleave needs/), JSON.stringify(flashes(t)));
  await clearMobs(); await zombie(0, 2);

  // 7. One press, one cast, even when the client sends it for both hands; holding right-click recasts only after the cooldown.
  await hold('iron_sword'); await fresh();
  const c0 = (await casts()).casts;
  await lookAt(zombies()[0]);
  await rclick(true); await sleep(700);
  const c1 = (await casts()).casts;
  check('A right-click sent for both hands casts once', c1 === c0 + 1, `casts +${c1 - c0}`);
  t = Date.now();
  for (let k = 0; k < 6; k++) { await rclick(); await sleep(200); } // held right-click repeats every 4 ticks
  await sleep(300);
  const c2 = (await casts()).casts;
  check('Holding right-click during the cooldown casts nothing more and shows the time left', c2 === c1 && !!flashed(t, /Cleave \d\.\d s/), `casts +${c2 - c1}; ${JSON.stringify(flashes(t)).slice(0, 160)}`);

  // 8. The message stays readable while the HUD refreshes (hits taken change health several times a second).
  await fresh();
  await clearMobs(); await zombie(0, 2);
  await lookAt(zombies()[0]);
  t = Date.now();
  await rclick();
  for (let k = 0; k < 4; k++) { await sleep(250); await cmd(`/damage ${NAME} 1 minecraft:generic`, 0); }
  await sleep(900);
  const f = flashed(t, /✦ Cleave/);
  check('The cast message stays up about 1.6 s while hits refresh the HUD', !!f && f.ms >= 1200, f ? `${f.m}: ${f.ms} ms` : JSON.stringify(flashes(t)));

  // 9. The Fireball Wand (a blaze rod carrying its own spell) no longer hides Arcane Bolt...
  await loadout(2, { a1: 'arcane_bolt', a2: 'frost_nova', a3: 'fireball', a4: 'blink' });
  await cmd('/clear @s blaze_rod', 300); await cmd('/givetestitem fireball', 700);
  const wi = bot.inventory.slots.findIndex((it, s) => it && it.name === 'blaze_rod' && s >= 36 && s <= 43);
  if (wi >= 0) { bot.setQuickBarSlot(wi - 36); await sleep(300); }
  await clearMobs(); await stand(0, 0); await zombie(0, 5);
  await fresh();
  let at = chat.length;
  t = Date.now();
  await lookAt(zombies()[0]); await rclick(); await sleep(900);
  check('The Fireball Wand casts Arcane Bolt on right-click (not its old Fireball)', !!flashed(t, /✦ Arcane Bolt/) && !saw(/You cast Fireball/, at),
    `${JSON.stringify(flashes(t))}; old spell ${saw(/You cast Fireball/, at) ? 'cast' : 'not cast'}`);
  // ...and with nothing on right-click it still casts its own spell (vanilla behaviour of that item kept).
  await equip('2 a1 none'); await equip('2 a4 none'); await sleep(300);
  at = chat.length;
  t = Date.now();
  await rclick(); await sleep(900);
  check('With nothing on right-click, the wand casts its own Fireball as before', !!saw(/You cast Fireball/, at) && !flashed(t, /Nothing on/), chat.slice(at).join(' | ').slice(0, 120));
  // ...and with only a blade ability on right-click (Cleave can't use a focus), the same: its own Fireball.
  await equip('2 a1 cleave'); await sleep(1300); // the wand's own 1 s cooldown
  at = chat.length;
  t = Date.now();
  await rclick(); await sleep(900);
  check('With only Cleave on right-click (it needs a blade), the wand casts its own Fireball, not "Cleave needs..."', !!saw(/You cast Fireball/, at) && !flashed(t, /Cleave/),
    `${chat.slice(at).join(' | ').slice(0, 100)}; ${JSON.stringify(flashes(t))}`);
  await equip('2 a1 arcane_bolt'); await equip('2 a4 blink');

  // 10. With no ability equipped at all, every key is vanilla (a sword's F swaps).
  await use(3); // loadout 3 is empty in this phase
  await hold('iron_sword');
  for (const sl of ['a1', 'a2', 'a3', 'a4', 'a5']) await equip(`3 ${sl} none`);
  await sleep(400);
  keyF(); await sleep(700);
  check('No actives equipped: F with a sword swaps as in vanilla', offHand() === 'iron_sword', `${inHand()} / ${offHand()}`);
  keyF(); await sleep(500);
  await use(1);
  await clearMobs();
}

// ════════════════════════════════════════════════════════════════
// combos
// ════════════════════════════════════════════════════════════════
async function combos() {
  await arena();
  await setup('combo');
  await loadout(1, { a1: 'cleave', a2: 'war_cry', a3: 'execute', a4: 'charge', a5: 'blink' });
  await hold('iron_sword');
  await debug(true);
  await zombie(0, 2);
  const z = zombies()[0];
  const gap = 150;
  const seqs = [
    ['R R R (hold right-click) = active 1, Cleave', ['r', 'r', 'r'], /✦ Cleave/],
    ['R L L = active 3, Execute', ['r', 'l', 'l'], /✦ Execute/], // right after the Cleave: Momentum to spend
    ['R L R = active 2, War Cry', ['r', 'l', 'r'], /✦ War Cry/],
    ['R R L = active 4, Charge', ['r', 'r', 'l'], /✦ Charge/],
  ];
  for (const [name, keys, re] of seqs) {
    await fresh(); await stand(0, 0); await lookAt(z);
    const at = chat.length;
    const t = Date.now();
    for (const k of keys) { if (k === 'r') await rclick(); else attack(z); await sleep(gap); }
    await sleep(700);
    const plainHits = traces(at).filter(l => /Power\(/.test(l) && !/Ability/.test(l)).length;
    check(name, !!flashed(t, re), `${JSON.stringify(flashes(t)).slice(0, 140)}`);
    if (keys.includes('l') && !/Execute|Cleave/.test(name)) check(`...its left-clicks did not hit the zombie as normal attacks`, plainHits === 0, `${plainHits} hit traces`);
  }
  await fresh(); await stand(0, -4, 180);
  let t = Date.now();
  keyF(); await sleep(600);
  check('F = active 5, Blink', !!flashed(t, /✦ Blink/), JSON.stringify(flashes(t)));
  // A lone right-click expires: nothing is cast and the next attack is a normal hit.
  await fresh(); await stand(0, 0); await lookAt(z);
  const c0 = (await casts()).casts;
  await rclick(); await sleep(1100);
  let at = chat.length;
  attack(z); await sleep(700);
  const c1 = (await casts()).casts;
  check('A single right-click casts nothing and the combo expires (0.7 s)', c1 === c0, `casts +${c1 - c0}`);
  check('...the next left-click is a normal attack again', traces(at).length >= 1, traces(at).join(' | ').slice(0, 120));
  // Q drops again in combo mode (positive: the scheme changed the meaning of Q).
  const before = itemsAround();
  keyQ(); await sleep(900);
  check('With click combos, Q drops the sword as in vanilla', itemsAround() === before + 1, `items ${before} -> ${itemsAround()}`);
  await clearMobs(); await hold('iron_sword'); await zombie(0, 2);
  // A bow starts its combos with left-click and still shoots with right-click.
  await hold('bow'); await cmd('/give @s arrow 16', 300); await fresh();
  t = Date.now();
  lclick(); await sleep(gap); lclick(); await sleep(gap); lclick(); await sleep(700);
  check('With a bow, L L L is active 1 (Cleave needs a blade: told so)', !!flashed(t, /Cleave needs/), JSON.stringify(flashes(t)));
  // A left-click on a player is a plain hit and ends a combo.
  const target = await connect('CerbTarget');
  await sleep(1500);
  await cmd('/tp CerbTarget ' + (X + 0.5) + ' ' + Y + ' ' + (Z + 2.5), 600);
  await hold('iron_sword'); await fresh();
  const tp = bot.players.CerbTarget && bot.players.CerbTarget.entity;
  if (tp) {
    await bot.lookAt(tp.position.offset(0, 1.4, 0), true);
    await rclick(); await sleep(gap);
    at = chat.length; t = Date.now();
    bot.attack(tp); await sleep(gap); await rclick(); await sleep(700);
    check('A left-click on a player inside a combo is a plain hit and the combo ends (no cast)', !flashed(t, /✦/), JSON.stringify(flashes(t)));
  } else check('CerbTarget visible', false);
  target.quit();
  // The choice is saved.
  await rejoin();
  at = await cmd('/class controls', 800);
  check('The control scheme is kept after a relog', !!saw(/Controls: Click combos/, at), chat.slice(at).join(' | ').slice(0, 100));
  await cmd('/class controls quick', 500);
  await debug(false);
  await clearMobs();
}

// ════════════════════════════════════════════════════════════════
// per class
// ════════════════════════════════════════════════════════════════
async function warrior() {
  await arena();
  await setup('quick');
  await debug(true);
  await loadout(1, { a1: 'cleave', a2: 'execute', a3: 'war_cry', a4: 'charge' });
  await hold('iron_sword');

  // Cleave
  await clearMobs(); await fresh();
  for (const [dx, dz] of [[0, 2], [1, 2], [-1, 2], [0, -2], [0, 5]]) await zombie(dx, dz);
  await stand(0, 0); await face(SOUTH, 0);
  let at = chat.length, t = Date.now();
  await rclick(); await sleep(800);
  let tr = traces(at);
  const cf = flashed(t, /✦ Cleave/);
  check('Cleave hits the three zombies in front (cone, 3.5 blocks), not the one behind or the one 5 blocks away', !!cf && /3 hit/.test(cf.m) && tr.length === 3,
    `${cf ? cf.m : 'no cast'}; ${tr.length} traces`);
  note('Cleave trace: ' + (tr[0] || '').slice(0, 140));
  await sleep(200);
  at = chat.length; t = Date.now();
  await rclick(); await sleep(600);
  check('Cleave again at once: refused with the time left, nothing hit', !!flashed(t, /Cleave \d\.\d s/) && traces(at).length === 0, JSON.stringify(flashes(t)));
  // Execute spends the 3 Momentum Cleave built.
  await lookAt(zombies()[0]);
  at = chat.length; t = Date.now();
  keyF(); await sleep(700);
  const ef = flashed(t, /✦ Execute/);
  check('Execute after a Cleave on three: x2.2 with 3 Momentum, one strike', !!ef && /x2\.2 · 3 Momentum/.test(ef.m) && traces(at).length === 1, ef ? ef.m : JSON.stringify(flashes(t)));
  note('Execute trace: ' + (traces(at)[0] || '').slice(0, 140));
  // Execute is a finisher: with no Momentum (the Execute above spent it all) it is refused, nothing spent.
  await fresh(); await lookAt(zombies()[0]);
  at = chat.length; t = Date.now();
  keyF(); await sleep(700);
  let cd = await cooldownLine();
  check('Execute with no Momentum: "Execute needs 1 Momentum", no strike, no cooldown spent', !!flashed(t, /Execute needs 1 Momentum/) && traces(at).length === 0 && !/Execute/.test(cd),
    `${JSON.stringify(flashes(t))}; ${traces(at).length} traces; ${cd}`);
  /** A Cleave at the zombie in front: one Momentum for the next Execute (it fades after 6 s). */
  const momentumUp = async () => { await face(SOUTH, 0); t = Date.now(); await rclick(); await sleep(600); return !!flashed(t, /✦ Cleave · 1 hit/); };
  // Execute with nothing in reach: refused, nothing spent, Momentum kept.
  await clearMobs(); await zombie(0, 2); await stand(0, 0); await fresh();
  let built = await momentumUp();
  await clearMobs(); await fresh();
  t = Date.now();
  keyF(); await sleep(700);
  cd = await cooldownLine();
  check('Execute with no enemy in reach: "no target within 3.5 blocks", no cooldown spent', built && !!flashed(t, /Execute: no target within 3\.5 blocks/) && !/Execute/.test(cd),
    `Momentum built: ${built}; ${JSON.stringify(flashes(t))}; ${cd}`);
  // Aim assist: a zombie half a block off the crosshair is still the target; two blocks off is not.
  await zombie(0, 3); await stand(0, 0); await fresh();
  built = await momentumUp(); await fresh();
  await lookAt(zombies()[0], 0.55);
  t = Date.now(); keyF(); await sleep(700);
  check('Execute with the crosshair half a block beside the zombie still hits it (aim assist)', built && !!flashed(t, /✦ Execute/), JSON.stringify(flashes(t)));
  await fresh();
  built = await momentumUp(); await fresh();
  await lookAt(zombies()[0], 2.2);
  t = Date.now(); keyF(); await sleep(700);
  check('...but 2 blocks beside it is a miss: refused, nothing spent', built && !!flashed(t, /no target/), JSON.stringify(flashes(t)));
  // War Cry: +15% damage for 8 s.
  await fresh(); await clearMobs(); await zombie(0, 2, 50); await stand(0, 0);
  const zz = zombies()[0];
  await lookAt(zz);
  at = chat.length; attack(zz); await sleep(900);
  const plain = bonusFactor(traces(at)[0] || '');
  t = Date.now(); keyQ(); await sleep(700);
  check('War Cry goes off with its effect in the message', !!flashed(t, /✦ War Cry · \+15% damage · 8 s/), JSON.stringify(flashes(t)));
  at = chat.length; attack(zz); await sleep(900);
  const buffed = bonusFactor(traces(at)[0] || '');
  check('A hit under War Cry deals about 15% more (crits divided out)', near(plain, 1.0, 0.03) && near(buffed, 1.15, 0.03), `bonus x${plain.toFixed(3)} -> x${buffed.toFixed(3)}`);
  note('War Cry trace: ' + (traces(at)[0] || '').slice(0, 160));
  t = Date.now(); keyQ(); await sleep(500);
  check('War Cry again during it: refused (no stacking), time left shown', !!flashed(t, /War Cry \d+\.\d s/), JSON.stringify(flashes(t)));
  await sleep(8200);
  at = chat.length; attack(zz); await sleep(900);
  const after = bonusFactor(traces(at)[0] || '');
  check('After 8 s War Cry has ended', near(after, 1.0, 0.03), `bonus x${after.toFixed(3)}`);
  // War Cry works with an empty hand (any hand).
  await fresh(); await emptyHand();
  t = Date.now(); keyF(); await sleep(400); // F = Execute needs a blade: told so
  check('Empty hand + F (Execute): "needs a blade..." and nothing spent', !!flashed(t, /Execute needs/), JSON.stringify(flashes(t)));
  // Charge: dash 8, the first enemy is stunned, then a strike. A zombie standing in the path (one that walks into the
  // dash knocks the charger back, as in play).
  await hold('iron_sword'); await fresh(); await clearMobs();
  await zombie(0, 7);
  await stand(0, 0); await face(SOUTH, 0);
  const p0 = pos();
  at = chat.length; t = Date.now();
  await sneaking(rclick, 200); await sleep(500);
  const ml = await mobLines();
  await sleep(300);
  const moved = pos().distanceTo(p0);
  const stunned = ml.length > 0 && /slowness 10/.test(ml[0]);
  check('Charge (Sneak + right-click) rushes forward', !!flashed(t, /✦ Charge/) && moved >= 4, `moved ${moved.toFixed(1)} blocks`);
  check('...stuns the first zombie in its path (Slowness 10, 1 s) and strikes it', stunned && traces(at).length >= 1, `${ml[0] || 'no mob'}; ${traces(at).length} traces`);
  t = Date.now();
  await sneaking(rclick, 200); await sleep(400);
  check('Charge again: on cooldown (10 s)', !!flashed(t, /Charge \d+\.\d s/), JSON.stringify(flashes(t)));
  // Moving and jumping don't stop a cast.
  await fresh(); await clearMobs(); await zombie(0, 3); await stand(0, 0); await lookAt(zombies()[0]);
  bot.setControlState('forward', true); bot.setControlState('jump', true);
  t = Date.now(); await sleep(250); await rclick(); await sleep(500);
  bot.setControlState('forward', false); bot.setControlState('jump', false);
  check('Cleave while running and jumping', !!flashed(t, /✦ Cleave/), JSON.stringify(flashes(t)));
  // Passives and keystone: through the attribute engine.
  await fresh();
  await stand(0, 0);
  const hp0 = (await statsValues()).health, as0 = await attr('attack_speed');
  await equip('1 p1 battle_hardened'); await sleep(600);
  const hp1 = (await statsValues()).health;
  check('Battle-Hardened (+8 Vitality) raises max health', hp1 > hp0, `${hp0} -> ${hp1}`);
  await equip('1 p2 weapon_discipline'); await sleep(600);
  const as1 = await attr('attack_speed');
  check('Weapon Discipline (+5% attack speed) raises attack speed', as1 > as0, `${as0} -> ${as1}`);
  await equip('1 k juggernaut'); await sleep(600);
  const hp2 = (await statsValues()).health, as2 = await attr('attack_speed');
  check('Juggernaut (keystone) raises max health further and slows attacks', hp2 > hp1 && as2 < as1, `hp ${hp1} -> ${hp2}, attack speed ${as1} -> ${as2}`);
  for (const s of ['p1', 'p2', 'k']) await equip(`1 ${s} none`);
  await sleep(600);
  const hp3 = (await statsValues()).health, as3 = await attr('attack_speed');
  check('Unequipped: max health and attack speed are back', hp3 === hp0 && near(as3, as0, 1e-6), `hp ${hp3}, attack speed ${as3}`);
  await debug(false);
  await clearMobs();
}

async function mage() {
  await arena();
  await setup('quick');
  await debug(true);
  await loadout(2, { a1: 'arcane_bolt', a2: 'frost_nova', a3: 'fireball', a4: 'blink' });
  await hold('blaze_rod');

  // Arcane Bolt: aimed, near miss, clear miss, out of range, through a wall.
  await clearMobs(); await zombie(0, 6); await stand(0, 0); await fresh();
  const z = zombies()[0];
  let e0, e1;
  await lookAt(z); await sleep(1100); // a fresh HUD bar at full Essence
  let at = chat.length, t = Date.now();
  await rclick(); await sleep(800);
  let cost = paid(t, /✦ Arcane Bolt/);
  check('Arcane Bolt at a zombie 6 blocks away: hits it once, costs 15 Essence', !!flashed(t, /✦ Arcane Bolt/) && traces(at).length === 1 && near(cost, 15, 1.5),
    `${traces(at).length} traces, paid ${cost}`);
  note('Arcane Bolt trace: ' + (traces(at)[0] || '').slice(0, 160));
  await fresh();
  await lookAt(z, 0.7);
  at = chat.length; t = Date.now();
  await rclick(); await sleep(800);
  check('Aimed 0.7 blocks beside it: still hits (aim assist)', !!flashed(t, /✦ Arcane Bolt/) && traces(at).length === 1, JSON.stringify(flashes(t)));
  await fresh();
  e0 = await essence();
  await lookAt(z, 2.5);
  at = chat.length; t = Date.now();
  await rclick(); await sleep(800);
  e1 = await essence();
  let cd = await cooldownLine();
  check('Aimed 2.5 blocks beside it: "no target within 8 blocks", no Essence, no cooldown', !!flashed(t, /no target within 8 blocks/) && traces(at).length === 0 && near(e0, e1, 3) && !/Arcane/.test(cd),
    `Essence ${e0} -> ${e1}; ${cd}`);
  await clearMobs(); await zombie(0, 10); await stand(0, 0); await fresh();
  await lookAt(zombies()[0]);
  t = Date.now(); await rclick(); await sleep(700);
  check('A zombie 10 blocks away is out of range (8): refused', !!flashed(t, /no target/), JSON.stringify(flashes(t)));
  await clearMobs(); await zombie(0, 5); await stand(0, 0); await fresh();
  await cmd(`/fill ${X - 2} ${Y} ${Z + 3} ${X + 2} ${Y + 3} ${Z + 3} stone`, 500);
  await lookAt(zombies()[0]);
  t = Date.now(); await rclick(); await sleep(700);
  check('A wall between: refused (no hitting through walls)', !!flashed(t, /no target/), JSON.stringify(flashes(t)));
  await cmd(`/fill ${X - 2} ${Y} ${Z + 3} ${X + 2} ${Y + 3} ${Z + 3} air`, 400);
  // Other foci, wrong items, no Essence.
  for (const item of ['breeze_rod', 'stick']) {
    await hold(item); await fresh(); await lookAt(zombies()[0]);
    t = Date.now(); await rclick(); await sleep(700);
    check(`A ${item.replace('_', ' ')} is a focus too`, !!flashed(t, /✦ Arcane Bolt/), JSON.stringify(flashes(t)));
  }
  await hold('iron_sword'); await fresh();
  t = Date.now(); await rclick(); await sleep(600);
  check('With a sword: "Arcane Bolt needs a focus", nothing spent', !!flashed(t, /Arcane Bolt needs a focus/), JSON.stringify(flashes(t)));
  await hold('blaze_rod'); await fresh();
  await lookAt(zombies()[0]);
  bot.chat(`/cerb mana ${NAME} 0`); await sleep(60); // press at once: Essence regenerates about 70 per second
  t = Date.now(); await rclick(); await sleep(700);
  cd = await cooldownLine();
  check('With almost no Essence: "needs 15 Essence", no cooldown started', !!flashed(t, /needs 15 Essence/) && !/Arcane/.test(cd), `${JSON.stringify(flashes(t))}; ${cd}`);
  // Fireball (Q).
  await fresh(); await clearMobs(); await zombie(0, 6); await stand(0, 0);
  await lookAt(zombies()[0]); await sleep(1100);
  at = chat.length; t = Date.now();
  keyQ(); await sleep(1500);
  cost = paid(t, /✦ Fireball/);
  check('Fireball (Q) flies and bursts on the zombie, 20 Essence, the rod stays in hand', !!flashed(t, /✦ Fireball/) && traces(at).length >= 1 && near(cost, 20, 1.5) && inHand() === 'blaze_rod',
    `${traces(at).length} traces, paid ${cost}, hand ${inHand()}`);
  // Frost Nova (F): around you, 4 blocks, slows.
  await fresh(); await clearMobs();
  for (const [dx, dz] of [[2, 0], [-2, 1], [0, -3], [0, 6]]) await zombie(dx, dz);
  await stand(0, 0);
  at = chat.length; t = Date.now();
  keyF(); await sleep(500);
  const slowed = (await mobLines()).filter(l => /slowness 2/.test(l)).length;
  const nf = flashed(t, /✦ Frost Nova/);
  check('Frost Nova hits the three zombies within 4 blocks, not the one 6 away', !!nf && /3 hit/.test(nf.m) && traces(at).length === 3, `${nf ? nf.m : '-'}; ${traces(at).length} traces`);
  check('...and slows them', slowed === 3, `${slowed} with an effect`);
  check('...for 30 Essence', near(paid(t, /✦ Frost Nova/), 30, 1.5), `paid ${paid(t, /✦ Frost Nova/)}`);
  // Blink (Sneak + right-click): 6 blocks, never into a wall, refused with no room.
  await fresh(); await clearMobs(); await stand(0, -6); await face(SOUTH, 0);
  let p0 = pos();
  t = Date.now();
  await sneaking(rclick); await sleep(800);
  let d = pos().distanceTo(p0);
  check('Blink moves you about 6 blocks where you look, for 20 Essence', !!flashed(t, /✦ Blink/) && d >= 5 && d <= 6.6 && near(paid(t, /✦ Blink/), 20, 1.5),
    `moved ${d.toFixed(2)}, paid ${paid(t, /✦ Blink/)}`);
  await fresh();
  await cmd(`/fill ${X - 2} ${Y} ${Z + 3} ${X + 2} ${Y + 3} ${Z + 3} stone`, 400);
  await stand(0, 0); await face(SOUTH, 0);
  p0 = pos(); t = Date.now();
  await sneaking(rclick); await sleep(800);
  d = pos().distanceTo(p0);
  check('With a wall 3 blocks ahead, Blink stops before it (never inside)', !!flashed(t, /✦ Blink/) && d >= 1.5 && d < 3, `moved ${d.toFixed(2)}`);
  await fresh(); await stand(0, 2); await face(SOUTH, 0);
  e0 = await essence();
  t = Date.now();
  await sneaking(rclick); await sleep(700);
  e1 = await essence();
  check('Facing the wall: "Blink: no room", no Essence spent', !!flashed(t, /Blink: no room/) && near(e0, e1, 3), `${JSON.stringify(flashes(t))}; Essence ${e0} -> ${e1}`);
  await cmd(`/fill ${X - 2} ${Y} ${Z + 3} ${X + 2} ${Y + 3} ${Z + 3} air`, 400);
  // Passives and keystone.
  await stand(0, 0);
  const v0 = await statsValues();
  await equip('2 p1 spell_focus'); await sleep(600);
  const v1 = await statsValues();
  check('Spell Focus (+8 Essence attribute) raises max mana', Number(String(v1.mana).replace(/,/g, '')) > Number(String(v0.mana).replace(/,/g, '')), `${v0.mana} -> ${v1.mana}`);
  await equip('2 p2 essence_flow'); await sleep(600);
  await cmd(`/cerb mana ${NAME} 0`, 400);
  await sleep(3000);
  const regenWith = await essence();
  await equip('2 p2 none'); await sleep(400);
  await cmd(`/cerb mana ${NAME} 0`, 400);
  await sleep(3000);
  const regenWithout = await essence();
  check('Essence Flow (+15% Essence regeneration): more Essence back in 3 s', regenWith > regenWithout, `${regenWith} vs ${regenWithout} without`);
  /** Arcane Bolt at a zombie 5 blocks away: the spell multiplier its trace shows (realm x Essence x spell bonuses). */
  const boltMult = async () => {
    await hold('blaze_rod'); await clearMobs(); await zombie(0, 5, 50); await stand(0, 0); await fresh();
    await lookAt(zombies()[0]);
    const at = chat.length; await rclick(); await sleep(800);
    const m = (traces(at).find(l => /Spell\(/.test(l)) || '').match(/realm and Essence x([\d.]+)/);
    return m ? Number(m[1]) : NaN;
  };
  const sm0 = await boltMult();
  await equip('2 k glass_cannon'); await sleep(600);
  const sm1 = await boltMult();
  check('Glass Cannon (keystone): +25% spell damage (+0.25 on the spell multiplier)', near(sm1 - sm0, 0.25, 0.015), `x${sm0} -> x${sm1}`);
  const v2 = await statsValues();
  check('...and 15% less max health', Number(String(v2.health).replace(/,/g, '')) < Number(String(v1.health).replace(/,/g, '')), `${v1.health} -> ${v2.health}`);
  for (const s of ['p1', 'k']) await equip(`2 ${s} none`);
  await debug(false);
  await clearMobs();
}

async function duelist() {
  await arena();
  await setup('quick');
  await debug(true);
  await loadout(3, { a1: 'riposte', a2: 'flurry', a3: 'feint', a4: 'lunge' });
  await hold('iron_sword');

  // Riposte (right-click): the next melee hit within 0.6 s is negated and answered. The hit comes from a still
  // zombie (/damage ... by it, a real mob attack): a zombie with AI would hit on its own schedule. Damage taken is
  // read from the combat trace (it lands on the defense bar first, so health alone can't show it).
  await clearMobs(); await zombie(0, 2); await stand(0, 0); await fresh();
  const z = zombies()[0];
  await lookAt(z);
  const taken = at => traces(at).filter(l => /Armor\(|DefenseBar\(|ElemResist\(/.test(l));
  let c0 = await casts();
  let at = chat.length, t = Date.now();
  await rclick(); await sleep(150);
  await cmd(`/damage ${NAME} 4 minecraft:mob_attack by @e[type=zombie,limit=1,sort=nearest]`, 800);
  let c1 = await casts();
  check('Riposte, then a zombie hit within 0.6 s: the hit is negated (no damage step) and answered',
    c1.counters === c0.counters + 1 && taken(at).length === 0 && traces(at).some(l => /Power\(/.test(l)) && !!flashed(t, /Riposte! · countered/),
    `counters +${c1.counters - c0.counters}, ${taken(at).length} damage steps, ${traces(at).length} traces; ${flashes(t).map(f => f.m).join(' / ')}`);
  let cd = await cooldownLine();
  check('...and a success brings Riposte back in 1.5 s, not 6', !/Riposte [2-6]\./.test(cd), cd);
  await sleep(1600); await fresh();
  c0 = await casts();
  at = chat.length;
  await rclick(); await sleep(900);
  await cmd(`/damage ${NAME} 4 minecraft:mob_attack by @e[type=zombie,limit=1,sort=nearest]`, 800);
  c1 = await casts();
  check('A hit after the 0.6 s window lands normally (the window closed)', c1.counters === c0.counters && taken(at).length >= 1,
    `counters +${c1.counters - c0.counters}, ${taken(at).length} damage steps: ${(taken(at)[0] || '').slice(0, 120)}`);
  // Positive control for the trace reading: a plain hit with no Riposte shows its damage step.
  at = chat.length;
  await cmd(`/damage ${NAME} 4 minecraft:mob_attack by @e[type=zombie,limit=1,sort=nearest]`, 800);
  check('Positive control: a mob hit with no Riposte shows a damage step', taken(at).length >= 1, (taken(at)[0] || 'none').slice(0, 120));
  // Flurry (F): three quick strikes at the enemy in front; none in reach: refused.
  await clearMobs(); await zombie(0, 2, 50); await stand(0, 0); await fresh();
  await lookAt(zombies()[0]);
  at = chat.length; t = Date.now();
  keyF(); await sleep(1000);
  check('Flurry strikes the zombie three times', !!flashed(t, /✦ Flurry/) && traces(at).length === 3, `${traces(at).length} traces`);
  await fresh(); await face(NORTH, 0);
  t = Date.now(); keyF(); await sleep(600);
  cd = await cooldownLine();
  check('Flurry facing away from it: "no target", no cooldown spent', !!flashed(t, /Flurry: no target/) && !/Flurry/.test(cd), cd);
  // Feint (Q): the next weapon hit within 3 s is a critical hit.
  await fresh(); await lookAt(zombies()[0]);
  t = Date.now(); keyQ(); await sleep(400);
  check('Feint goes off ("next hit crits"), the sword stays in hand', !!flashed(t, /✦ Feint · next hit crits/) && inHand() === 'iron_sword', JSON.stringify(flashes(t)));
  at = chat.length; attack(zombies()[0]); await sleep(800);
  check('...the next hit is a critical hit', traces(at).some(l => /Crit/i.test(l)), (traces(at)[0] || '').slice(0, 160));
  // Lunge (Sneak + right-click): dash 5 and strike.
  await clearMobs(); await zombie(0, 5); await stand(0, 0); await fresh();
  await lookAt(zombies()[0]);
  const p0 = pos();
  at = chat.length; t = Date.now();
  await sneaking(rclick); await sleep(1000);
  const moved = pos().distanceTo(p0);
  check('Lunge closes the distance and strikes', !!flashed(t, /✦ Lunge/) && moved >= 2 && traces(at).length >= 1, `moved ${moved.toFixed(1)}, ${traces(at).length} traces`);
  t = Date.now(); await stand(0, -6); await face(SOUTH, 0); await equip(`3 a5 none`);
  // Lunge and Blink share the movement lockout: covered in "mixed".
  // Wrong weapon: an axe can't Flurry (blade only).
  await hold('iron_axe'); await fresh();
  t = Date.now(); keyF(); await sleep(500);
  check('With an axe: "Flurry needs a blade", nothing spent', !!flashed(t, /Flurry needs a blade/), JSON.stringify(flashes(t)));
  // Passives and keystone.
  await hold('iron_sword');
  const v0 = await statsValues(), ms0 = await attr('movement_speed');
  await equip('3 p1 precision'); await sleep(600);
  const v1 = await statsValues();
  check('Precision (+5% crit chance) raises crit chance', parseFloat(v1.crit) > parseFloat(v0.crit), `${v0.crit} -> ${v1.crit}`);
  await equip('3 p2 footwork'); await sleep(600);
  const ms1 = await attr('movement_speed');
  check('Footwork (+4% movement speed) raises movement speed', ms1 > ms0, `${ms0} -> ${ms1}`);
  /** Feint, then a hit: the guaranteed crit's multiplier from the trace. */
  const feintCrit = async () => {
    await clearMobs(); await zombie(0, 2, 50); await stand(0, 0); await fresh();
    await lookAt(zombies()[0]);
    keyQ(); await sleep(500);
    const at = chat.length; attack(zombies()[0]); await sleep(800);
    const m = (traces(at).find(l => /guaranteed: Feint/.test(l)) || '').match(/Crit\(x([\d.]+)/);
    return m ? Number(m[1]) : NaN;
  };
  const cm0 = await feintCrit();
  await equip('3 k blade_dancer'); await sleep(600);
  const cm1 = await feintCrit();
  check('Blade Dancer (keystone): +15% critical damage (+0.15 on the crit multiplier)', near(cm1 - cm0, 0.15, 0.011), `x${cm0} -> x${cm1}`);
  const v2 = await statsValues();
  check('...and the Stats page reloads with it', !!v2.crit, JSON.stringify(v2));
  for (const s of ['p1', 'p2', 'k']) await equip(`3 ${s} none`);
  await debug(false);
  await clearMobs();
}

// ════════════════════════════════════════════════════════════════
// fixes: the independent review's findings (2026-10-25), each with its positive control
// ════════════════════════════════════════════════════════════════
/** A player's offline-mode UUID as the [I; ...] int array NBT takes (to make a pet theirs). */
function offlineUuidInts(name) {
  const h = require('crypto').createHash('md5').update('OfflinePlayer:' + name).digest();
  h[6] = (h[6] & 0x0f) | 0x30; h[8] = (h[8] & 0x3f) | 0x80;
  return [0, 4, 8, 12].map(i => h.readInt32BE(i));
}
async function fixes() {
  await arena();
  await setup('quick');
  await debug(true);
  await loadout(1, { a1: 'cleave', a2: 'war_cry', a3: 'flurry', a4: 'feint' });
  await hold('iron_sword');
  // Folia has no /item command: give the item and move it to the off hand through the inventory, as a player would.
  const offhand = async item => {
    if (bot.inventory.slots[45]) { await bot.unequip('off-hand').catch(() => {}); await sleep(300); }
    if (item === 'air') return;
    await cmd(`/give @s ${item} 1`, 600);
    const it = bot.inventory.items().find(i => i.name === item);
    if (it) await bot.equip(it, 'off-hand').catch(() => {});
    await sleep(400);
  };
  const inOff = () => (bot.inventory.slots[45] ? bot.inventory.slots[45].name : 'air');
  const press = async fn => { const c0 = (await casts()).casts; const t = Date.now(); await fn(); await sleep(700); return { n: (await casts()).casts - c0, f: flashes(t) }; };

  // 1. Q: the item is out of the hand while its drop event runs. The cast now waits a tick, so Flurry's strikes
  //    use the sword's Power (before: the first strike was bare-handed, and a kill paid Martial Expertise).
  await clearMobs(); await zombie(0, 2, 50); await stand(0, 0); await fresh(); await lookAt(zombies()[0]);
  let at = chat.length;
  keyQ(); await sleep(1300);
  const tr = traces(at).filter(l => /Power\(/.test(l));
  check('Q (Flurry): all three strikes use the sword\'s Power, none bare-handed', tr.length === 3 && tr.every(l => /Power\(Swords/.test(l)),
    tr.map(l => (l.match(/Power\([A-Za-z]+/) || [''])[0]).join(' | '));
  check('...and the sword is back in hand, nothing on the ground', inHand() === 'iron_sword' && itemsAround() === 0, `${inHand()}, ${itemsAround()} items`);
  // 2. Q right after closing the inventory is still the key (the cursor-item grace only covers a drop that left the hand alone).
  await fresh(); await lookAt(zombies()[0]);
  let t = Date.now();
  bot._client.write('close_window', { windowId: 0 }); keyQ(); await sleep(900);
  check('Q within 100 ms of closing the inventory casts (Flurry) and keeps the sword', !!flashed(t, /Flurry/) && inHand() === 'iron_sword' && itemsAround() === 0,
    `${JSON.stringify(flashes(t))}; ${inHand()}, ${itemsAround()} items`);

  // 3. The off hand's own use keeps right-click: a shield or food always, a torch on a block.
  await clearMobs(); await stand(0, 0); await face(SOUTH, 0); await fresh();
  let r = await press(rclick);
  check('Positive control: with a free off hand, right-click casts Cleave', r.n === 1 && r.f.some(f => /✦ Cleave/.test(f.m)), JSON.stringify(r.f));
  await offhand('shield'); await fresh();
  r = await press(rclick);
  check('A shield in the off hand: right-click is the shield\'s (no cast, no message)', inOff() === 'shield' && r.n === 0 && !r.f.some(f => /Cleave/.test(f.m)), `off hand ${inOff()}; ${JSON.stringify(r.f)}`);
  r = await press(async () => sneaking(rclick));
  check('...Sneak + right-click too (Feint not cast)', r.n === 0, JSON.stringify(r.f));
  r = await press(async () => keyF());
  check('...while F still casts (War Cry)', r.n === 1 && r.f.some(f => /✦ War Cry/.test(f.m)), JSON.stringify(r.f));
  await offhand('bread'); await fresh();
  r = await press(rclick);
  check('Bread in the off hand: right-click eats, no cast', inOff() === 'bread' && r.n === 0, `off hand ${inOff()}; ${JSON.stringify(r.f)}`);
  await offhand('totem_of_undying'); await fresh();
  r = await press(rclick);
  check('A totem in the off hand (no use of its own): right-click casts Cleave', inOff() === 'totem_of_undying' && r.n === 1, `off hand ${inOff()}; ${JSON.stringify(r.f)}`);
  await offhand('torch'); await fresh();
  r = await press(rclick); // in the air
  check('A torch in the off hand, right-click in the air: Cleave', inOff() === 'torch' && r.n === 1, `off hand ${inOff()}; ${JSON.stringify(r.f)}`);
  await fresh();
  r = await press(() => rclickBlock({ x: X, y: Y - 1, z: Z + 2 })); // on the floor: the torch's place
  check('...right-click on a block: vanilla (the torch is placed), no cast', r.n === 0, JSON.stringify(r.f));
  await offhand('air');
  await cmd(`/fill ${X - 2} ${Y} ${Z + 1} ${X + 2} ${Y + 2} ${Z + 3} air`, 400); // anything the torch left

  // 4. An axe keeps its own use on a block: stripping a log.
  await hold('iron_axe'); await fresh();
  await cmd(`/setblock ${X} ${Y} ${Z + 2} oak_log`, 400);
  await cmd(`/setblock ${X + 1} ${Y} ${Z + 2} stone`, 400);
  r = await press(() => rclickBlock({ x: X, y: Y, z: Z + 2 }));
  const log = bot.blockAt(new (require('vec3'))(X, Y, Z + 2));
  check('An axe on an oak log strips it (vanilla), no cast', r.n === 0 && log && log.name === 'stripped_oak_log', `${log && log.name}; ${JSON.stringify(r.f)}`);
  await fresh();
  r = await press(() => rclickBlock({ x: X + 1, y: Y, z: Z + 2 }));
  check('Positive control: the same axe on stone casts Cleave', r.n === 1 && r.f.some(f => /✦ Cleave/.test(f.m)), JSON.stringify(r.f));
  await cmd(`/fill ${X - 2} ${Y} ${Z + 1} ${X + 2} ${Y + 2} ${Z + 3} air`, 400);

  // 5. Sneak let go together with the key: still the Sneak slot (Feint, not Cleave). Timing-dependent: when the
  //    release arrives after the decision, the old code passed too.
  await hold('iron_sword'); await fresh(); await stand(0, 0); await face(SOUTH, 0);
  bot.setControlState('sneak', true); await sleep(300);
  t = Date.now();
  await rclick(); bot.setControlState('sneak', false);
  await sleep(700);
  check('Sneak released with the right-click: Sneak + right-click (Feint), not right-click (Cleave)', !!flashed(t, /Feint/) && !flashed(t, /Cleave/), JSON.stringify(flashes(t)));

  // 6. Nothing that belongs to a player: another player's wolf. And nothing that can't be hurt (no XP for it).
  await clearMobs(); await fresh(); await face(SOUTH, 0);
  await cmd(`/summon wolf ${X + 0.5} ${Y} ${Z + 2.5} {NoAI:1b,Owner:[I;${offlineUuidInts('CerbTarget').join(',')}]}`, 600);
  t = Date.now(); await rclick(); await sleep(600);
  check('Cleave with only another player\'s wolf in front: "no one hit"', !!flashed(t, /✦ Cleave · no one hit/), JSON.stringify(flashes(t)));
  await clearMobs(); await fresh(); await face(SOUTH, 0);
  await cmd(`/summon wolf ${X + 0.5} ${Y} ${Z + 2.5} {NoAI:1b}`, 600);
  t = Date.now(); await rclick(); await sleep(600);
  check('Positive control: a wild wolf in front is hit', !!flashed(t, /✦ Cleave · 1 hit/), JSON.stringify(flashes(t)));
  await clearMobs(); await sleep(8600); await fresh(); await face(SOUTH, 0); // out of the fight
  await cmd(`/summon zombie ${X + 0.5} ${Y} ${Z + 2.5} {NoAI:1b,Invulnerable:1b}`, 600);
  let xp0 = await classXp('Warrior');
  t = Date.now(); await rclick(); await sleep(600);
  let xp1 = await classXp('Warrior');
  check('An invulnerable zombie isn\'t a target: "no one hit", no class XP', !!flashed(t, /no one hit/) && xp1 === xp0, `${JSON.stringify(flashes(t))}; Warrior XP ${xp0} -> ${xp1}`);
  await clearMobs(); await zombie(0, 2); await fresh(); await face(SOUTH, 0);
  xp0 = await classXp('Warrior');
  t = Date.now(); await rclick(); await sleep(600);
  xp1 = await classXp('Warrior');
  check('Positive control: a normal zombie is hit and pays 6', !!flashed(t, /1 hit/) && xp1 - xp0 === 6, `Warrior XP ${xp0} -> ${xp1}`);

  // 7. Fireball pays class XP when it bursts on an enemy, not as a self cast (before: 6 even into the sky).
  await loadout(2, { a1: 'arcane_bolt', a3: 'fireball' });
  await hold('blaze_rod');
  await clearMobs(); await zombie(0, 4, 50); await stand(0, 0); await fresh();
  await lookAt(zombies()[0]); lclick(); await sleep(400);             // a fight with an enemy
  xp0 = await classXp('Mage');
  await face(SOUTH, Math.PI / 2); keyQ(); await sleep(1500);          // straight up
  xp1 = await classXp('Mage');
  check('Fireball into the sky during a fight: no class XP', xp1 === xp0, `Mage XP ${xp0} -> ${xp1}`);
  await fresh(); await lookAt(zombies()[0]);
  xp0 = await classXp('Mage');
  keyQ(); await sleep(1800);                                          // it flies, then bursts on the zombie
  xp1 = await classXp('Mage');
  check('Fireball bursting on the zombie: +6 class XP', xp1 - xp0 === 6, `Mage XP ${xp0} -> ${xp1}`);
  await use(1);
  await debug(false);
  await clearMobs();
}

/** Values from Player Menu -> Stats (max health, max mana, crit chance), as the player sees them. */
const plain = v => {
  if (v == null) return '';
  if (typeof v === 'string') { try { return plain(JSON.parse(v)); } catch { return v; } }
  const s = JSON.stringify(v);
  const nbt = [...s.matchAll(/"text":\{"type":"string","value":"([^"]*)"/g)].map(m => m[1]);
  if (nbt.length) return nbt.join('');
  return [...s.matchAll(/"text":"([^"]*)"/g)].map(m => m[1]).join('');
};
const slotLore = i => { const it = bot.currentWindow && bot.currentWindow.slots[i]; return it && it.customLore ? it.customLore.map(plain).join(' | ') : ''; };
async function statsValues() {
  await cmd('/stats', 1300);
  const v = { health: (slotLore(31).match(/Max health: ([\d,]+)/) || [])[1], mana: (slotLore(33).match(/Max mana: ([\d,]+)/) || [])[1],
              crit: (slotLore(11).match(/Crit chance: ([\d.]+%)/) || [])[1] };
  if (bot.currentWindow) bot.closeWindow(bot.currentWindow);
  await sleep(300);
  return v;
}

// ════════════════════════════════════════════════════════════════
// mixed classes, rapid presses, PvP safety
// ════════════════════════════════════════════════════════════════
async function mixed() {
  await arena();
  await setup('quick');
  await debug(true);
  await loadout(1, { a1: 'cleave', a2: 'war_cry', a3: 'feint', a4: 'charge', a5: 'blink', p1: 'battle_hardened', p2: 'precision', p3: 'spell_focus', k: 'juggernaut' });
  await hold('iron_sword');
  await clearMobs(); await zombie(0, 2, 50); await zombie(1, 2, 50); await stand(0, 0); await fresh();
  await lookAt(zombies()[0]);
  // Three classes' abilities in one loadout, each through its key.
  let t = Date.now();
  await rclick(); await sleep(600); keyF(); await sleep(600); keyQ(); await sleep(600);
  check('Warrior Cleave, Warrior War Cry and Duelist Feint from one loadout, one key each', ['Cleave', 'War Cry', 'Feint'].every(n => flashed(t, new RegExp('✦ ' + n))),
    flashes(t).map(f => f.m).join(' / '));
  const at = chat.length; attack(zombies()[0]); await sleep(800);
  check('Feint\'s crit and War Cry\'s bonus land on the same hit', traces(at).some(l => /Crit/i.test(l)), (traces(at)[0] || '').slice(0, 160));
  // Movement lockout across classes: Charge (Warrior) then Blink (Mage).
  await fresh(); await clearMobs(); await stand(0, -8); await face(SOUTH, 0);
  t = Date.now();
  await sneaking(rclick); await sleep(500);
  await sneaking(async () => keyF()); await sleep(500);
  check('Blink right after Charge waits for the shared movement lockout', !!flashed(t, /✦ Charge/) && !!flashed(t, /Blink \d\.\d s/), flashes(t).map(f => f.m).join(' / '));
  // Rapid presses: one cast per press, never two from one key mash.
  await fresh(); await clearMobs(); await zombie(0, 2, 50); await stand(0, 0); await lookAt(zombies()[0]);
  const c0 = (await casts()).casts;
  for (let k = 0; k < 8; k++) { await rclick(); await sleep(30); }
  await sleep(700);
  const c1 = (await casts()).casts;
  check('Eight right-clicks in a quarter second cast Cleave once', c1 === c0 + 1, `casts +${c1 - c0}`);
  // Cooldowns follow the ability, not the loadout: switching can't reset one.
  await fresh(); await stand(0, 0); await lookAt(zombies()[0]);
  t = Date.now(); keyF(); await sleep(500);                       // War Cry in loadout 1 (30 s)
  await sleep(8600);                                              // the fight lock (8 s after the last hit)
  await loadout(2, { a1: 'war_cry', a2: 'arcane_bolt' });         // War Cry again, on another key, in loadout 2
  t = Date.now(); await rclick(); await sleep(500);
  check('War Cry from another loadout is still on its cooldown (no reset by switching)', !!flashed(t, /War Cry \d+\.\d s/), JSON.stringify(flashes(t)));
  await sleep(10500);
  await use(1);
  // Class XP is for real fights: a buff cast into the air out of combat earns none.
  await clearMobs(); await sleep(8600); await fresh();
  let xp0 = await classXp('Warrior');
  keyF(); await sleep(600);                                       // War Cry, nobody around, not in a fight
  let xp1 = await classXp('Warrior');
  check('A self-buff cast out of combat earns no class XP', xp1 === xp0, `Warrior XP ${xp0} -> ${xp1}`);
  const cowNear = () => Object.values(bot.entities).find(e => e.name === 'cow' && e.position.distanceTo(bot.entity.position) < 6);
  const cow = async () => { await cmd(`/summon cow ${X + 0.5} ${Y} ${Z + 2.5} {NoAI:1b}`, 500); await stand(0, 0); await face(SOUTH, 0); };
  await cow(); await fresh();
  xp0 = await classXp('Warrior');
  t = Date.now(); await rclick(); await sleep(600);               // Cleave hits the cow
  xp1 = await classXp('Warrior');
  check('Cleave on a farm animal earns no class XP', !!flashed(t, /✦ Cleave · 1 hit/) && xp1 === xp0, `${(flashed(t, /Cleave/) || {}).m}; Warrior XP ${xp0} -> ${xp1}`);
  // A "fight" with no enemy in it (a cow hit just before) pays a self cast nothing; one with a zombie pays it.
  await fresh(); await lookAt(cowNear());
  xp0 = await classXp('Warrior');
  let at3 = chat.length; lclick(); await sleep(300); keyF(); await sleep(600); // hit the cow, then War Cry
  xp1 = await classXp('Warrior');
  check('A self cast right after hitting a farm animal earns no class XP', traces(at3).length > 0 && xp1 === xp0, `hit traced: ${traces(at3).length > 0}; Warrior XP ${xp0} -> ${xp1}`);
  await clearMobs(); await zombie(0, 2); await fresh(); await lookAt(zombies()[0]);
  xp0 = await classXp('Warrior');
  at3 = chat.length; lclick(); await sleep(300); keyF(); await sleep(600); // hit the zombie, then War Cry
  xp1 = await classXp('Warrior');
  check('Positive control: the same self cast in a fight with a zombie earns class XP (6)', traces(at3).length > 0 && xp1 - xp0 === 6, `Warrior XP ${xp0} -> ${xp1}`);
  await fresh(); await face(SOUTH, 0);
  xp0 = await classXp('Warrior');
  await rclick(); await sleep(600);
  xp1 = await classXp('Warrior');
  check('Positive control: Cleave on a zombie earns class XP', xp1 > xp0, `Warrior XP ${xp0} -> ${xp1}`);
  // Killing farm animals pays no class XP either (only enemies do), so they can't be bred into ranks.
  // A cow has a health pool like any mob (50 at level 1): Cleave until it falls.
  await clearMobs(); await sleep(600); await cow();
  xp0 = await classXp('Warrior');
  let presses = 0;
  while (cowNear() && presses < 8) { await fresh(); await face(SOUTH, 0); await rclick(); presses++; await sleep(700); }
  await sleep(1200);
  xp1 = await classXp('Warrior');
  check('Killing a cow with an ability pays no class XP (no cast XP, no kill share)', !cowNear() && xp1 === xp0, `cow ${cowNear() ? 'alive' : 'killed'} after ${presses} Cleaves; Warrior XP ${xp0} -> ${xp1}`);
  // Positive control: a zombie killed the same way pays every Cleave and a kill share on top.
  await clearMobs(); await zombie(0, 2);
  xp0 = await classXp('Warrior');
  presses = 0;
  while (zombies(6).length && presses < 8) { await fresh(); await face(SOUTH, 0); await rclick(); presses++; await sleep(700); }
  await sleep(1200);
  xp1 = await classXp('Warrior');
  check('Positive control: killing a zombie with Cleave pays its casts and a kill share', !zombies(6).length && xp1 - xp0 > 6 * presses,
    `zombie ${zombies(6).length ? 'alive' : 'killed'} after ${presses} Cleaves; Warrior XP ${xp0} -> ${xp1}`);
  // Weak mobs pay a quarter of the cast XP: the kill-XP rule, measured against the class's own skills.
  await cmd(`/cerb skill ${NAME} Blade_Mastery level 40`, 600);
  await clearMobs(); await zombie(0, 2, 15); await fresh(); await face(SOUTH, 0); // level 15 <= 40 - 20: grey, and survives the Cleave
  xp0 = await classXp('Warrior');
  await rclick(); await sleep(700);
  xp1 = await classXp('Warrior');
  await clearMobs(); await zombie(0, 2, 30); await fresh(); await face(SOUTH, 0);
  const xp2 = await classXp('Warrior');
  await rclick(); await sleep(700);
  const xp3 = await classXp('Warrior');
  check('At Blade Mastery 40 a Cleave on a level-15 zombie pays a quarter (1 XP), on a level-30 one the full 6', xp1 - xp0 === 1 && xp3 - xp2 === 6,
    `level 15: +${xp1 - xp0}, level 30: +${xp3 - xp2}`);
  await cmd(`/cerb skill ${NAME} Blade_Mastery level 10`, 600);
  // Items: all the key presses above neither dropped nor duplicated anything.
  const swords = bot.inventory.items().filter(i => i.name === 'iron_sword').reduce((n, i) => n + i.count, 0);
  check('After every press in this phase exactly one sword, nothing dropped (the cow\'s beef aside)', swords === 1 && itemsAround(12) === 0,
    `${bot.inventory.items().map(i => i.name + 'x' + i.count).join(',')}; ${itemsAround(12)} on the ground`);
  // PvP safety: abilities never hit players.
  const target = await connect('CerbTarget');
  await sleep(1500);
  await cmd(`/tp CerbTarget ${X + 0.5} ${Y} ${Z + 2.5}`, 600);
  await fresh(); await clearMobs(); await stand(0, 0); await face(SOUTH, 0);
  const at2 = chat.length;
  t = Date.now();
  await rclick(); await sleep(800);
  const cf = flashed(t, /✦ Cleave/);
  check('Cleave with only a player in front: nobody hit ("no one hit")', !!cf && /no one hit/.test(cf.m) && traces(at2).length === 0, cf ? cf.m : JSON.stringify(flashes(t)));
  // Execute (a single-target strike) finds no target in a player: refused, nothing spent.
  await equip('1 a4 execute'); await sleep(300); await fresh();
  await zombie(3, 0); await lookAt(zombies()[0]); await rclick(); await sleep(500); // a Cleave to the side: Momentum to spend
  await clearMobs(); await fresh(); await face(SOUTH, 0);
  t = Date.now(); await sneaking(rclick); await sleep(500);
  check('Execute aimed at a player: "no target" (players are never targets)', !!flashed(t, /Execute: no target/), JSON.stringify(flashes(t)));
  // Riposte doesn't counter a player's hit.
  await loadout(3, { a1: 'riposte' }); await fresh();
  const c0r = await casts();
  await rclick(); await sleep(100);
  target.attack(bot.entity); await sleep(700);
  const c1r = await casts();
  check('Riposte never counters a player\'s hit', c1r.counters === c0r.counters, `counters +${c1r.counters - c0r.counters}`);
  target.quit();
  await use(1);
  await debug(false);
  await clearMobs();
}

async function persist() {
  // Run as: PERSIST=set, restart the server at once, PERSIST=check. War Cry's 30 s cooldown is the
  // longest there is, so the cast comes last in "set" and the cooldown is read first in "check".
  const fs = require('fs'), file = require('path').join(__dirname, '.abilities-persist.json');
  if (process.env.PERSIST === 'set') {
    await setup('combo');
    await cmd('/class bar off', 400);
    await loadout(2, { a1: 'riposte', a2: 'blink', a5: 'war_cry' });
    await emptyHand();
    const at = await cmd('/class', 800);
    await fresh();
    keyF(); await sleep(600); // combos: F = active 5 = War Cry (30 s)
    const cd = await cooldownLine();
    fs.writeFileSync(file, JSON.stringify({ cd, at: Date.now() }));
    check('Set: combo scheme, bar off, loadout 2 in use, War Cry cooling down', /War Cry/.test(cd) && !!saw(/▶ Loadout 2/, at), cd);
  } else {
    const before = JSON.parse(fs.readFileSync(file, 'utf8'));
    const cd = await cooldownLine();
    const gone = ((Date.now() - before.at) / 1000).toFixed(1);
    check('After the restart War Cry is still cooling down (saved as an end time)', /War Cry/.test(cd), `${before.cd} -> ${cd}, ${gone} s later`);
    let at = await cmd('/class controls', 800);
    check('...the scheme is still Click combos', !!saw(/Controls: Click combos/, at), chat.slice(at).join(' | ').slice(0, 100));
    at = await cmd('/class bar', 600);
    check('...the ability bar is still off', !!saw(/Ability bar: off/, at));
    at = await cmd('/class', 800);
    check('...loadout 2 is still the one in use', !!saw(/▶ Loadout 2/, at));
    await cmd('/class controls quick', 400); await cmd('/class bar auto', 400); await use(1);
  }
}

(async () => {
  await connect();
  await sleep(3000);
  try {
    if (phase === 'controls') await controls();
    else if (phase === 'combos') await combos();
    else if (phase === 'warrior') await warrior();
    else if (phase === 'mage') await mage();
    else if (phase === 'duelist') await duelist();
    else if (phase === 'mixed') await mixed();
    else if (phase === 'persist') await persist();
    else if (phase === 'fixes') await fixes();
    else if (phase !== 'neutral') console.log('unknown phase ' + phase);
    if (!(phase === 'persist' && process.env.PERSIST === 'set')) await neutral(); // the restart check needs loadout 2
  } catch (e) {
    console.log('ERROR ' + (e && e.stack || e));
    results.push(false);
  }
  const passed = results.filter(Boolean).length;
  console.log(`\n${phase}: ${passed}/${results.length} passed${failed.length ? '; failed: ' + failed.join(' | ') : ''}`);
  bot.quit();
  process.exit(passed === results.length ? 0 : 1);
})();
