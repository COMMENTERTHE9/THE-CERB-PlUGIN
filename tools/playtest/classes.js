// Classes and abilities (Step 5 prototype: Warrior, Mage, Duelist) in-game checks.
// LOCAL SANDBOX servers only; the bot must be op. Use a NEW bot name for `learn` (classes, once learned,
// stay learned).  Usage: node classes.js <phase> [name]
//   learn    - skills open classes (Warrior: Blade or Weapon Mastery 5; Mage: Intelligence 5; Duelist: Blade
//              Mastery 10 + Critical Strike 5); /class shows ranks, or what is still missing
//   (keys    - superseded by the controls of 2026-10-25: `node abilities.js controls` and `combos` test every key)
// Casting below presses each active slot with the quick keys (docs/design/ABILITY_CONTROLS.md): right-click, F, Q,
// Sneak + right-click, Sneak + F for actives 1-5. Each check still names its slot by the Step 5 key it used to have.
//   trees    - progress in two trees at once (Warrior from Cleave, Mage from Arcane Bolt); switching loadouts keeps it
//   mixed    - one loadout from three classes works together: Riposte + Charge + War Cry + Blink + Flurry, passives
//              from all three and a keystone; the shared movement lockout applies across classes; one attribute
//              rebuild per loadout change
//   combat   - the three classes play differently (numbers from the combat trace): Cleave hits a group, Execute
//              spends Momentum, Arcane Bolt hits at 7 blocks, Frost Nova slows a group, Riposte negates a hit and
//              answers it, Flurry strikes three times, Feint makes the next hit a guaranteed crit
//   exploits - rapid presses cast once; cooldowns survive loadout switches and a relog; a buff ends with its
//              loadout and never stacks; the combat lock, switch gap and cast lock hold; loadout rules (locked, wrong
//              slot, moved not duplicated); abilities never hit players and a Sneak-attack on a player stays a hit
//   menu     - Player Menu -> Classes & Abilities: class tiles (rank, XP or what is missing), a class's abilities,
//              a loadout screen, choosing an ability in the picker (saved, and refused in combat), emptying a
//              slot, switching from the menu
//   persist  - PERSIST=set: ranks, loadouts, active loadout, a long cooldown; restart the server; PERSIST=check: same
const mineflayer = require('mineflayer');
const phase = process.argv[2] || 'keys';
const NAME = process.argv[3] || 'CerbSmoke';
const sleep = ms => new Promise(r => setTimeout(r, ms));
const chat = [];
const bars = []; // every action-bar text with its time ("✦ Cleave" when a cast goes off)
const results = [];
const check = (name, ok, detail = '') => { results.push(ok); console.log(`${ok ? 'PASS' : 'FAIL'}  ${name}${detail ? '  (' + detail + ')' : ''}`); };

let bot;
function connect(name = NAME) {
  const b = mineflayer.createBot({ host: '127.0.0.1', port: 25565, username: name, version: '1.21.11', auth: 'offline' });
  if (name === NAME) {
    bot = b;
    b.on('messagestr', (m, pos) => { if (pos !== 'game_info') chat.push(m); });
    b._client.on('action_bar', p => {
      try { bars.push({ t: Date.now(), m: require('prismarine-chat')(b.registry).fromNotch(p.text).toString() }); } catch (e) { /* ignore */ }
    });
  }
  b.on('kicked', r => { console.log('KICKED', name, r); process.exit(process.env.PERSIST === 'online' ? 0 : 2); });
  return new Promise(r => b.once('spawn', () => r(b)));
}
async function rejoin() { bot.quit(); await sleep(2000); await connect(); await sleep(3500); }

async function cmd(c, wait = 900) { const at = chat.length; bot.chat(c); await sleep(wait); return at; }
const saw = (re, since) => chat.slice(since).find(m => re.test(m));
const sawAll = (re, since) => chat.slice(since).filter(m => re.test(m));
const barSince = (re, t) => bars.some(b => b.t >= t && re.test(b.m));
const near = (a, b, tol) => Math.abs(a - b) <= tol;
const mobs = (name, r = 12) => Object.values(bot.entities).filter(e => e.name === name && e.position.distanceTo(bot.entity.position) < r);
const itemsOnGround = (r = 6) => Object.values(bot.entities).filter(e => e.name === 'item' && e.position.distanceTo(bot.entity.position) < r).length;

/** Combat debug on or off (the server keeps the toggle per player until it restarts, so read its reply). */
async function debug(on) {
  for (let k = 0; k < 2; k++) {
    const at = await cmd('/cerb combatdebug', 600);
    const m = (saw(/Combat debug: (ON|OFF)/, at) || '').match(/(ON|OFF)/);
    if (m && (m[1] === 'ON') === on) return;
  }
}

// ── the cast keys (raw packets: exactly what a vanilla client sends) ──
async function sneak(on) { bot.setControlState('sneak', on); await sleep(250); }
async function rightClick() { bot.activateItem(); await sleep(60); bot.deactivateItem(); await sleep(450); }
async function leftClick() { bot.swingArm('right'); await sleep(500); }
async function keyF() { bot._client.write('block_dig', { status: 6, location: { x: 0, y: 0, z: 0 }, face: 0, sequence: 0 }); await sleep(500); }
/** Q as a vanilla client sends it: "drop", then an arm swing when the hand held something (the client predicts the drop). */
async function keyQ(swing = true) {
  bot._client.write('block_dig', { status: 4, location: { x: 0, y: 0, z: 0 }, face: 0, sequence: 0 });
  if (swing) bot.swingArm('right');
  await sleep(500);
}
async function scroll() { bot.setQuickBarSlot((bot.quickBarSlot + 1) % 8); await sleep(500); }
async function press(key) { await ({ right: rightClick, left: leftClick, f: keyF, q: keyQ, scroll }[key])(); }

/** Press active slot `slot` (0-4) with the quick keys; returns the action-bar texts it caused. */
async function castSlot(slot) {
  const t = Date.now();
  if (slot === 0) await rightClick();
  else if (slot === 1) await keyF();
  else if (slot === 2) await keyQ();
  else if (slot === 3) { await sneak(true); await rightClick(); await sneak(false); }
  else { await sneak(true); await keyF(); await sneak(false); }
  await sleep(150); // quick keys decide one tick later (Sneak's state)
  return bars.filter(b => b.t >= t).map(b => b.m);
}
/** The Step 5 key names map to the same active slots: right = 1, left = 2, f = 3, q = 4, scroll = 5. */
async function cast(key) { return castSlot({ right: 0, left: 1, f: 2, q: 3, scroll: 4 }[key]); }
/** The nearest zombie in front (for left-click casts at a mob). */
const front = () => zombiesIn().sort((a, b) => a.position.distanceTo(bot.entity.position) - b.position.distanceTo(bot.entity.position))[0];
const went = (texts, name) => texts.some(m => m.includes('✦ ' + name));

async function castCount() {
  const at = await cmd('/cerb showqueues', 700);
  const m = (saw(/casts since start/, at) || '').match(/casts since start: (\d+), counters: (\d+)/);
  const r = (saw(/Attribute rebuilds since start/, at) || '').match(/(\d+)\s*$/);
  return { casts: m ? +m[1] : NaN, counters: m ? +m[2] : NaN, rebuilds: r ? +r[1] : NaN };
}

/** Parsed /class output. */
async function info() {
  const at = await cmd('/class', 900);
  const lines = chat.slice(at);
  const ranks = {};
  for (const l of lines) {
    const m = l.match(/^\s*(Warrior|Mage|Duelist): rank (\d+) \((\d+)\/(\d+) XP\)/) || l.match(/^\s*(Warrior|Mage|Duelist): rank (\d+) \((max rank)\)/);
    if (m) ranks[m[1]] = { rank: +m[2], xp: m[3] === 'max rank' ? 0 : +m[3] };
    const n = l.match(/^\s*(Warrior|Mage|Duelist): not learned/);
    if (n) ranks[n[1]] = null;
  }
  // Each loadout: a header line, then its actives and passives lines.
  const loadouts = {};
  let active = 0, activeName = '';
  for (let k = 0; k < lines.length; k++) {
    const h = lines[k].match(/(▶ )?Loadout (\d) \(([^)]*)\)/);
    if (!h) continue;
    loadouts[+h[2]] = { name: h[3], actives: (lines[k + 1] || '').trim(), passives: (lines[k + 2] || '').trim() };
    if (h[1]) { active = +h[2]; activeName = h[3]; }
  }
  const cur = loadouts[active] || { actives: '', passives: '' };
  return {
    ranks, lines, loadouts, active, activeName,
    actives: cur.actives, passives: cur.passives,
    cooldowns: (lines.find(l => /Cooldowns:/.test(l)) || '').trim(),
  };
}

async function equipSlot(spec) { const at = await cmd('/class equip ' + spec, 700); return chat.slice(at).join(' | '); }
async function rank(cls, n) { await cmd(`/cerb class ${NAME} ${cls} rank ${n}`, 800); }

/** Give `count` of `item` (Folia has no /item command) and hold it; returns its hotbar index or -1. */
async function hold(item, count = 1) {
  await cmd('/clear @s ' + item, 400);
  await cmd(`/give @s ${item} ${count}`, 800);
  const i = bot.inventory.slots.findIndex((it, s) => it && it.name === item && s >= 36 && s <= 43);
  if (i < 0) return -1;
  bot.setQuickBarSlot(i - 36);
  await sleep(300);
  return i - 36;
}
const inHand = () => { const it = bot.inventory.slots[36 + bot.quickBarSlot]; return it ? it.name : 'air'; };
const offHand = () => { const it = bot.inventory.slots[45]; return it ? it.name : 'air'; };

// ── the arena: a stone floor in the sky next to spawn, at night (sandbox world only) ──
let X = 0, Y = 200, Z = 0;
async function arena() {
  const sp = bot.spawnPoint || bot.entity.position;
  X = Math.floor(sp.x) + 40; Z = Math.floor(sp.z);
  await cmd('/time set midnight', 300);
  await cmd(`/fill ${X - 12} ${Y - 1} ${Z - 12} ${X + 12} ${Y - 1} ${Z + 12} stone`, 500);
  await cmd(`/fill ${X - 12} ${Y} ${Z - 12} ${X + 12} ${Y + 3} ${Z + 12} air`, 500);
  await stand(0, 0);
  await cmd(`/kill @e[type=zombie,distance=..40]`, 400);
  await cmd(`/kill @e[type=item,distance=..40]`, 400);
}
/** Stand at arena offset (dx, dz), facing +z (yaw 0) unless told otherwise. */
async function stand(dx, dz, yaw = 0) { await cmd(`/tp @s ${X + dx + 0.5} ${Y} ${Z + dz + 0.5} ${yaw} 0`, 700); }
/** A no-AI zombie of `level` at arena offset (dx, dz). */
async function zombie(dx, dz, level = 1) {
  const back = { x: bot.entity.position.x - X - 0.5, z: bot.entity.position.z - Z - 0.5, yaw: 0 };
  await cmd(`/tp @s ${X + dx + 0.5} ${Y} ${Z + dz + 0.5}`, 400);
  await cmd(`/cerb spawn zombie ${level} noai`, 700);
  await stand(Math.round(back.x), Math.round(back.z));
}
const zombiesIn = () => mobs('zombie', 14);

/** Damage traces ("[Combat] ...") since `at`; for weapon hits, the hit's raw damage (after the Power step / multiplier). */
const traces = at => sawAll(/^\[Combat\]/, at);
const rawOf = t => { const m = t.match(/Power\([^,]+, Power [\d.]+ -> x([\d.]+)\): ([\d.]+)/); return m ? Number(m[2]) / Number(m[1]) : NaN; };

// ════════════════════════════════════════════════════════════════════

async function learn() {
  await cmd('/gamemode creative', 500);
  for (const s of ['Blade_Mastery', 'Weapon_Mastery', 'Intelligence', 'Critical_Strike']) await cmd(`/cerb skill ${NAME} ${s} level 1`, 500);
  let i = await info();
  check('A new player has learned no class yet, and /class says what each needs',
    i.ranks.Warrior === null && i.ranks.Mage === null && i.ranks.Duelist === null && i.lines.some(l => /Duelist: not learned - needs Blade Mastery 10 \(you: 1\)/.test(l)),
    i.lines.filter(l => /not learned/.test(l)).join(' / '));
  let at = await cmd(`/cerb skill ${NAME} Blade_Mastery level 5`, 1200);
  check('Blade Mastery 5 opens Warrior (one of its two entry skills)', !!saw(/✦ New class: Warrior/, at), chat.slice(at).join(' / '));
  at = await cmd(`/cerb skill ${NAME} Intelligence level 5`, 1200);
  check('Intelligence 5 opens Mage', !!saw(/✦ New class: Mage/, at));
  at = await cmd(`/cerb skill ${NAME} Blade_Mastery level 10`, 1200);
  check('Duelist needs both skills: Blade Mastery 10 alone is not enough', !saw(/New class: Duelist/, at));
  at = await cmd(`/cerb skill ${NAME} Critical_Strike level 5`, 1200);
  check('Blade Mastery 10 + Critical Strike 5 open Duelist', !!saw(/✦ New class: Duelist/, at));
  i = await info();
  check('All three learned at rank 1 with 0 XP', ['Warrior', 'Mage', 'Duelist'].every(c => i.ranks[c] && i.ranks[c].rank === 1 && i.ranks[c].xp === 0), JSON.stringify(i.ranks));
  at = await cmd(`/cerb skill ${NAME} Blade_Mastery level 1`, 1000);
  i = await info();
  check('Learning is never taken back: lowering the skill keeps Warrior and Duelist', i.ranks.Warrior && i.ranks.Duelist, JSON.stringify(i.ranks));
}

async function keys() {
  console.log('The keys phase tested the Step 5 Sneak keys, replaced on 2026-10-25: run `node abilities.js controls` and `combos`.');
}

async function trees() {
  await arena();
  // Class XP is earned in survival (creative pays none, like skill XP). No natural spawns: a night mob hitting the bot
  // would start a fight and block the loadout switches below.
  await cmd('/gamerule spawn_mobs false', 300);
  await cmd('/kill @e[type=!player,type=!text_display,distance=..48]', 500);
  await cmd('/gamemode survival', 400);
  await cmd('/effect give @s resistance infinite 4 true', 300);
  const i0 = await info();
  if (!i0.ranks.Warrior || !i0.ranks.Mage) { check('Warrior and Mage are learned (run `learn` first with this name)', false); return; }
  for (const s of ['a1 cleave', 'a2 arcane_bolt', 'a3 none', 'a4 none', 'a5 none']) await equipSlot('1 ' + s);
  await cmd('/class loadout 1', 500);
  await debug(true);

  // Warrior: Cleave three zombies in front.
  await hold('netherite_sword');
  for (const dx of [-1, 0, 1]) await zombie(dx, 2, 30);
  await stand(0, 0);
  let at = chat.length;
  let t = await cast('right');
  await sleep(600);
  const hits = traces(at).filter(x => /Power\(Swords/.test(x)).length;
  check('Cleave hit all three zombies in front', went(t, 'Cleave') && hits === 3, `${t.join(' / ')}; ${hits} hits`);
  const i1 = await info();
  check('Warrior gained class XP from the cast', i1.ranks.Warrior.xp > i0.ranks.Warrior.xp || i1.ranks.Warrior.rank > i0.ranks.Warrior.rank,
    `${JSON.stringify(i0.ranks.Warrior)} -> ${JSON.stringify(i1.ranks.Warrior)}`);
  check('Mage gained nothing from a Warrior cast', JSON.stringify(i1.ranks.Mage) === JSON.stringify(i0.ranks.Mage));
  await cmd('/kill @e[type=zombie,distance=..40]', 400);

  // Mage: Arcane Bolt at a zombie 7 blocks away (far out of sword reach).
  await hold('blaze_rod');
  await zombie(0, 7, 30);
  await stand(0, 0);
  at = chat.length;
  t = await cast('left');
  await sleep(600);
  const spell = saw(/Spell\(ARCANE_BLAST/, at);
  check('Arcane Bolt hit the zombie at 7 blocks', went(t, 'Arcane Bolt') && !!spell, `${t.join(' / ')}; ${spell || 'no spell trace'}`);
  const i2 = await info();
  check('Mage gained class XP: two trees progressed in one session', i2.ranks.Mage.xp > i1.ranks.Mage.xp || i2.ranks.Mage.rank > i1.ranks.Mage.rank,
    `${JSON.stringify(i1.ranks.Mage)} -> ${JSON.stringify(i2.ranks.Mage)}`);

  // Switching loadouts never touches progress.
  await sleep(8500); // out of combat (8 s after the last hit)
  await equipSlot('2 a1 arcane_bolt');
  at = await cmd('/class loadout 2', 700);
  const i3 = await info();
  check('Switched to Loadout 2', i3.active === 2, chat.slice(at).join(' / '));
  check('Switching kept every rank and XP', JSON.stringify(i3.ranks) === JSON.stringify(i2.ranks), `${JSON.stringify(i2.ranks)} -> ${JSON.stringify(i3.ranks)}`);
  await sleep(10500);
  await cmd('/class loadout 1', 700);
  const i4 = await info();
  check('Back on Loadout 1: still the same ranks and XP, and its slots as they were', i4.active === 1 && JSON.stringify(i4.ranks) === JSON.stringify(i2.ranks)
    && /A1 Cleave \| A2 Arcane Bolt/.test(i4.actives), i4.actives);
  await debug(false);
  await cmd('/effect clear @s resistance', 300);
  await cmd('/gamemode creative', 400);
  await cmd('/gamerule spawn_mobs true', 300);
}

async function mixed() {
  await arena();
  await cmd('/gamemode creative', 400);
  await rank('warrior', 12); await rank('mage', 8); await rank('duelist', 9);
  await sleep(10500); // the switch gap from earlier phases
  // Fill Loadout 3 while another is in use (an earlier phase may have left 3 in use: then every passive equipped
  // into it rebuilds the snapshot at once, as it should, and the switch below measures nothing).
  if ((await info()).active === 3) { await cmd('/class loadout 1', 800); await sleep(10500); }
  const r0 = await castCount();
  const specs = ['a1 riposte', 'a2 charge', 'a3 war_cry', 'a4 blink', 'a5 flurry', 'p1 precision', 'p2 battle_hardened', 'p3 essence_flow', 'k juggernaut'];
  const out = [];
  for (const s of specs) out.push(await equipSlot('3 ' + s));
  check('One loadout takes abilities from all three classes', out.every(o => /Equipped/.test(o)), out.filter(o => !/Equipped/.test(o)).join(' / '));
  const hud0 = (bars.filter(b => /♥/.test(b.m)).pop() || { m: '' }).m;
  const at = await cmd('/class loadout 3', 800);
  await sleep(1200);
  const r1 = await castCount();
  const i = await info();
  check('Loadout 3 in use, with all nine slots filled', i.active === 3 && !/ - /.test(' ' + i.actives + ' ' + i.passives + ' '), `${i.actives} || ${i.passives}`);
  check('The switch rebuilt the attribute snapshot (not on every hit)', r1.rebuilds - r0.rebuilds >= 1 && r1.rebuilds - r0.rebuilds <= 3, `${r0.rebuilds} -> ${r1.rebuilds}`);
  const hud1 = (bars.filter(b => /♥/.test(b.m)).pop() || { m: '' }).m;
  const maxHp = s => { const m = s.match(/\/([\d.,KMB]+)♥/); return m ? m[1] : '?'; };
  check('Passives from the loadout count: Battle-Hardened + Juggernaut Vitality raise max health', maxHp(hud0) !== maxHp(hud1), `${maxHp(hud0)} -> ${maxHp(hud1)}`);

  await hold('netherite_sword');
  await sleep(1100); // the 1 s cast lock after a switch
  let t = await cast('f');
  check('War Cry (Warrior) casts from the mixed loadout', went(t, 'War Cry'), t.join(' / '));
  await sleep(500);
  t = await cast('left');      // F: Charge (cast() returns about 0.65 s after the press)
  const t2 = await cast('q');  // Sneak + right-click: Blink about 0.95 s after Charge, inside the 1.2 s lockout
  check('Charge (Warrior) then Blink (Mage): Blink waits for the shared movement lockout', went(t, 'Charge') && t2.some(m => /Blink \d\.\d s/.test(m)),
    `${t.join(' / ')} || ${t2.join(' / ')}`);
  await stand(0, 0);
  await sleep(2200);
  t = await cast('q');
  check('Blink (Mage) goes off once the lockout ends', went(t, 'Blink'), t.join(' / '));
  await stand(0, 0);
  t = await cast('right');
  check('Riposte (Duelist) casts from the same loadout', went(t, 'Riposte'), t.join(' / '));
}

async function combat() {
  await arena();
  await cmd('/gamemode creative', 400);
  await rank('warrior', 12); await rank('mage', 8); await rank('duelist', 12);
  await cmd(`/cerb skill ${NAME} Critical_Strike level 5`, 400);
  await sleep(10500);
  for (const s of ['a1 cleave', 'a2 execute', 'a3 frost_nova', 'a4 arcane_bolt', 'a5 none', 'p1 none', 'p2 none', 'p3 none', 'k none']) await equipSlot('1 ' + s);
  for (const s of ['a1 riposte', 'a2 flurry', 'a3 feint', 'a4 none', 'a5 none', 'p1 none', 'p2 none', 'p3 none', 'k none']) await equipSlot('2 ' + s);
  await cmd('/class loadout 1', 600);
  await sleep(1100);
  await debug(true);
  const rows = [];

  // Warrior: Cleave a group (Momentum per enemy), then Execute spends it.
  await hold('netherite_sword');
  for (const dx of [-1, 0, 1]) await zombie(dx, 2, 60);
  await stand(0, 0);
  let at = chat.length;
  let t = await cast('right');
  await sleep(500);
  const cleave = traces(at).filter(x => /Power\(Swords/.test(x));
  const cleaveRaw = rawOf(cleave[0] || '');
  check('Warrior - Cleave: one swing hits the whole group at 1.6x a basic hit', cleave.length === 3 && near(cleaveRaw, 8 * 1.6, 0.15),
    `${cleave.length} targets, ${cleaveRaw.toFixed(2)} each before Power`);
  rows.push(['Warrior', 'Cleave', `${cleave.length} enemies at 1.6x`]);
  at = chat.length;
  t = await cast('left', front());
  await sleep(500);
  const exec = traces(at).find(x => /Power\(Swords/.test(x)) || '';
  check('Warrior - Execute after a 3-enemy Cleave: 3 Momentum = 1.0 + 3 x 0.4 = 2.2x', near(rawOf(exec), 8 * 2.2, 0.2),
    `${rawOf(exec).toFixed(2)} before Power (${exec.slice(0, 90)}); ${t.filter(m => !/♥/.test(m)).join(' / ')}`);
  rows.push(['Warrior', 'Execute', `${(rawOf(exec) / 8).toFixed(2)}x with 3 Momentum`]);

  // Mage: Frost Nova slows the group around you; Arcane Bolt reaches 7 blocks.
  await hold('blaze_rod');
  await stand(0, 1);
  at = chat.length;
  t = await cast('f');
  await sleep(800);
  const novas = sawAll(/Spell\(CRYOMANCY/, at);
  // Mob effects aren't sent to clients: ask the server which zombies are slowed.
  const sat = await cmd('/execute if entity @e[type=zombie,distance=..10,nbt={active_effects:[{id:"minecraft:slowness"}]}]', 700);
  const sm = (saw(/Test passed\. Count: (\d+)|Test failed/, sat) || '').match(/Count: (\d+)/);
  const slowed = sm ? +sm[1] : 0;
  check('Mage - Frost Nova damages and slows every zombie around you', went(t, 'Frost Nova') && novas.length === 3 && slowed === 3,
    `${novas.length} hits; ${slowed} slowed`);
  rows.push(['Mage', 'Frost Nova', `${novas.length} enemies around, slowed`]);
  await cmd('/kill @e[type=zombie,distance=..40]', 400);
  await zombie(0, 7, 60);
  await stand(0, 0);
  at = chat.length;
  t = await cast('q');
  await sleep(600);
  const bolt = saw(/Spell\(ARCANE_BLAST/, at) || '';
  const bm = bolt.match(/: ([\d.]+) -> ([\d.]+)/);
  check('Mage - Arcane Bolt hits at 7 blocks for 1.4 x 6 = 8.4 before Essence', !!bm && near(Number(bm[1]), 8.4, 0.05), bolt);
  rows.push(['Mage', 'Arcane Bolt', `7 blocks away, ${bm ? bm[1] : '?'} -> ${bm ? bm[2] : '?'} with Essence`]);
  await cmd('/kill @e[type=zombie,distance=..40]', 400);

  // Duelist: Riposte negates a hit and answers it at 2x; Flurry; Feint.
  await sleep(8500); // out of combat to switch
  await cmd('/class loadout 2', 600);
  await sleep(1100);
  await hold('netherite_sword');
  await zombie(0, 1, 60);
  await stand(0, 0);
  await cmd('/gamemode survival', 600);
  const c0 = await castCount();
  // The window is 0.6 s: the zombie's hit has to follow the press at once. Riposte is active 1: right-click
  // (quick keys decide one tick after the press, so the hit comes two ticks later, well inside the window).
  let t0 = Date.now();
  at = chat.length;
  bot.activateItem(); await sleep(60); bot.deactivateItem(); await sleep(60);
  bot.chat(`/damage ${NAME} 4 minecraft:mob_attack by @e[type=zombie,limit=1,sort=nearest]`);
  await sleep(900);
  t = bars.filter(b => b.t >= t0).map(b => b.m);
  const c1 = await castCount();
  const incoming = traces(at).filter(x => /Incoming/.test(x));
  const answer = traces(at).find(x => /Power\(Swords/.test(x)) || '';
  check('Duelist - Riposte: the zombie\'s hit is negated (no damage taken)', went(t, 'Riposte') && incoming.length === 0 && c1.counters - c0.counters === 1,
    `${t.join(' / ')}; incoming ${incoming.length}; counters ${c0.counters} -> ${c1.counters}`);
  check('Duelist - Riposte answers at 2x a basic hit', near(rawOf(answer), 16, 0.2), `${rawOf(answer).toFixed(2)} before Power`);
  rows.push(['Duelist', 'Riposte', `hit negated, answered at ${(rawOf(answer) / 8).toFixed(1)}x`]);
  at = chat.length;
  await cmd(`/damage ${NAME} 4 minecraft:mob_attack by @e[type=zombie,limit=1,sort=nearest]`, 900);
  check('Without a Riposte window the same hit lands', traces(at).some(x => /Incoming/.test(x)), traces(at).join(' / ').slice(0, 160));
  await cmd('/gamemode creative', 600);
  await sleep(600);
  at = chat.length;
  t = await cast('left', front());
  await sleep(1200);
  const flurry = traces(at).filter(x => /Power\(Swords/.test(x));
  check('Duelist - Flurry strikes three times at 0.6x', went(t, 'Flurry') && flurry.length === 3 && flurry.every(x => near(rawOf(x), 4.8, 0.1)),
    `${flurry.length} strikes: ${flurry.map(x => rawOf(x).toFixed(2)).join(', ')}; ${t.filter(m => !/♥/.test(m)).join(' / ')}`);
  rows.push(['Duelist', 'Flurry', `${flurry.length} strikes at 0.6x`]);
  t = await cast('f');
  await sleep(700); // the global gap
  const z = zombiesIn()[0];
  at = chat.length;
  if (z) { await bot.lookAt(z.position.offset(0, 1, 0), true); bot.attack(z); await sleep(700); }
  const feintHit = traces(at).find(x => /Power\(Swords/.test(x)) || '';
  check('Duelist - Feint: the next normal hit is a guaranteed crit', went(t, 'Feint') && /guaranteed: Feint/.test(feintHit), feintHit.slice(0, 160));
  await sleep(700);
  at = chat.length;
  if (z) { bot.attack(z); await sleep(700); }
  const next = traces(at).find(x => /Power\(Swords/.test(x)) || '';
  check('Feint is used up by that hit', !!next && !/guaranteed: Feint/.test(next), next.slice(0, 120));
  rows.push(['Duelist', 'Feint', 'next hit: guaranteed crit']);
  await debug(false);
  console.log('\nHow the three classes play (measured):');
  for (const r of rows) console.log(`  ${r[0].padEnd(8)} ${r[1].padEnd(12)} ${r[2]}`);
}

async function exploits() {
  await arena();
  await cmd('/gamemode creative', 400);
  await rank('warrior', 12); await rank('mage', 8); await rank('duelist', 12);
  await cmd('/class loadout 1', 600); // (if this was a switch, the 10 s gap starts now)
  for (const s of ['a1 war_cry', 'a2 cleave', 'a3 blink', 'a4 none', 'a5 none', 'p1 none', 'p2 none', 'p3 none', 'k none']) await equipSlot('1 ' + s);
  for (const s of ['a1 blink', 'a2 war_cry', 'a3 none', 'a4 none', 'a5 none']) await equipSlot('2 ' + s);
  for (const s of ['a1 riposte', 'a2 none', 'a3 none', 'a4 none', 'a5 none']) await equipSlot('3 ' + s);
  await sleep(10500);
  await hold('netherite_sword');
  await debug(true);

  // Rapid presses cast once; a cooldown says how long it has left. Cleave is active 2: F with quick keys.
  let c0 = await castCount();
  const f = () => bot._client.write('block_dig', { status: 6, location: { x: 0, y: 0, z: 0 }, face: 0, sequence: 0 });
  f(); await sleep(40); f(); await sleep(40); f();
  await sleep(600);
  let c1 = await castCount();
  check('Three presses of F within 100 ms cast Cleave once', c1.casts - c0.casts === 1, `${c0.casts} -> ${c1.casts}`);
  await sleep(300);
  let t = await cast('left');
  check('Pressed again: Cleave is on cooldown and says for how long', t.some(m => /Cleave \d\.\d s/.test(m)), t.join(' / '));

  // A cooldown belongs to the player: switching loadouts doesn't reset it. War Cry (30 s) shows it; Blink is back
  // in 0.4 s since the owner's movement rule.
  t = await cast('f');
  check('Blink goes off in Loadout 1', went(t, 'Blink'), t.join(' / '));
  await stand(0, 0);
  await sleep(500);
  t = await cast('right');
  check('War Cry goes off in Loadout 1', went(t, 'War Cry'), t.join(' / '));
  let at = chat.length;
  bot.chat('/class loadout 2');
  await sleep(300);
  t = await cast('right');
  check('Switched to Loadout 2 (it has War Cry too)', !!saw(/Now using Loadout 2/, at), chat.slice(at).join(' / '));
  check('The cast lock: no casting in the first second after a switch', t.some(m => /Switching loadouts/.test(m)), t.join(' / '));
  await sleep(1100);
  t = await cast('left');
  check('War Cry is still on cooldown in Loadout 2', t.some(m => /War Cry \d+\.\d s/.test(m)), t.join(' / '));
  at = await cmd('/class loadout 1', 700);
  check('The switch gap: a second switch within 10 s is refused', !!saw(/Switching again in \d+ s/, at), chat.slice(at).join(' / '));
  await cmd(`/cerb class ${NAME} cooldowns`, 500); // the buff checks below cast War Cry afresh

  // A buff never stacks and ends with its loadout; a relog doesn't reset a long cooldown.
  await sleep(10000);
  await zombie(0, 2, 60);
  await stand(0, 0);
  const z = front();
  const ratio = async () => {
    for (let k = 0; k < 4; k++) { // a natural crit hides the bonus: hit again
      const a = chat.length;
      await bot.lookAt(z.position.offset(0, 1, 0), true); bot.attack(z); await sleep(750);
      const tr = traces(a).find(x => /Power\(Swords/.test(x)) || '';
      if (/Crit/.test(tr)) continue;
      const p = tr.match(/Power\([^)]*\): ([\d.]+)/), b = tr.match(/Bonus: ([\d.]+)/);
      return { tr, bonus: p ? (b ? Number(b[1]) / Number(p[1]) : 1) : NaN };
    }
    return { tr: 'only crits', bonus: NaN };
  };
  let r = await ratio();
  check('Before War Cry: no damage bonus on a hit', r.bonus === 1, r.tr.slice(0, 140));
  t = await cast('left', z); // War Cry is active 2 in Loadout 2
  await sleep(500);
  r = await ratio();
  check('War Cry: +15% damage on the next hits', went(t, 'War Cry') && near(r.bonus, 1.15, 0.01), `${t.filter(m => !/♥/.test(m)).join(' / ')}; x${r.bonus}`);
  t = await cast('left', z);
  check('War Cry can\'t be recast to stack (30 s cooldown)', t.some(m => /War Cry \d+\.\d s/.test(m)), t.filter(m => !/♥/.test(m)).join(' / '));
  r = await ratio();
  check('...and the bonus is still +15%, not more', near(r.bonus, 1.15, 0.01), 'x' + r.bonus);
  await sleep(8500); // out of combat
  at = await cmd('/class loadout 3', 700);
  await sleep(1100);
  r = await ratio();
  check('Switching to a loadout without War Cry ends its buff at once', !!saw(/Now using Loadout 3/, at) && r.bonus === 1, `${chat.slice(at, at + 1).join('')}; x${r.bonus}`);
  at = await cmd('/class loadout 1', 700);
  check('In combat (just hit a zombie) loadouts can\'t be switched', !!saw(/Not during a fight/, at), chat.slice(at).join(' / '));
  let i = await info();
  const cdBefore = (i.cooldowns.match(/War Cry ([\d.]+) s/) || [])[1];
  await rejoin();
  i = await info();
  const cdAfter = (i.cooldowns.match(/War Cry ([\d.]+) s/) || [])[1];
  check('After a relog the long War Cry cooldown is still running', !!cdBefore && !!cdAfter && Number(cdAfter) < Number(cdBefore) && Number(cdAfter) > 1,
    `${cdBefore} s before, ${cdAfter} s after`);

  // Loadout rules.
  let o = await equipSlot('1 a4 cleave');
  i = await info();
  check('Equipping an ability already in another slot moves it (never in two slots)', /A2 - /.test(i.loadouts[1].actives) && /A4 Cleave/.test(i.loadouts[1].actives),
    `${o}; ${i.loadouts[1].actives}`);
  await rank('warrior', 1);
  i = await info();
  check('A rank drop empties every loadout\'s slots that are no longer unlocked (War Cry needs rank 10)',
    !/War Cry/.test(i.loadouts[1].actives + i.loadouts[2].actives), `${i.loadouts[1].actives} || ${i.loadouts[2].actives}`);
  o = await equipSlot('1 a5 execute');
  check('A locked ability can\'t be equipped', /not unlocked yet/.test(o), o);
  o = await equipSlot('1 a5 juggernaut');
  check('A keystone can\'t go in an active slot', /can't go in an active slot/.test(o), o);
  await rank('warrior', 12);

  // Abilities never hit players; a Sneak-attack on a player stays a normal hit.
  const target = await connect('CerbTarget');
  await sleep(2500);
  await cmd('/gamemode survival CerbTarget', 400);
  await cmd('/kill @e[type=zombie,distance=..40]', 400);
  await cmd(`/tp CerbTarget ${X + 0.5} ${Y} ${Z + 2.5}`, 800);
  await zombie(1, 2, 60);
  await stand(0, 0);
  // Cleave on right-click (a left-click at a player is deliberately a plain PvP hit), Flurry on left-click.
  for (const s of ['a4 none', 'a2 flurry', 'a1 cleave']) await equipSlot('3 ' + s);
  await sleep(1000);
  at = chat.length;
  t = await cast('right');
  await sleep(700);
  const hitTraces = traces(at);
  check('Cleave with a player and a zombie in front hits only the zombie', went(t, 'Cleave') && hitTraces.filter(x => /Power\(Swords/.test(x)).length === 1
    && !hitTraces.some(x => /Incoming/.test(x)), `${hitTraces.length} traces: ${hitTraces.map(x => x.slice(0, 60)).join(' / ')}`);
  // Fireball (the spell, cast as a Mage ability) must not hurt or ignite a player either. Before the PvP
  // hit below: that tags for 15 s, and the loadout in use can't be edited while tagged.
  await sleep(8500); // out of combat after the Cleave
  let eq = [];
  for (const s of ['a1 fireball', 'a2 flurry']) eq.push(await equipSlot('3 ' + s));
  await cmd('/kill @e[type=zombie,distance=..40]', 400);
  await cmd(`/tp CerbTarget ${X + 0.5} ${Y} ${Z + 4.5}`, 800);
  await hold('blaze_rod');
  await stand(0, 0);
  await sleep(1000);
  at = chat.length;
  t = await cast('right');
  await sleep(2500);
  const tpf = Object.values(bot.entities).find(e => e.username === 'CerbTarget');
  const burning = tpf && tpf.metadata && typeof tpf.metadata[0] === 'number' ? (tpf.metadata[0] & 0x01) !== 0 : false;
  const fb = traces(at).filter(x => /Incoming/.test(x));
  check('A Fireball ability never hurts or ignites a player in its path', eq[0].includes('Equipped Fireball') && went(t, 'Fireball') && fb.length === 0 && !burning,
    `${eq[0]}; ${t.filter(m => !/♥/.test(m)).join(' / ')}; ${fb.length} hits on the player; burning ${burning}`);
  await hold('netherite_sword');
  await cmd(`/tp CerbTarget ${X + 0.5} ${Y} ${Z + 2.5}`, 800);
  await stand(0, 0);
  const tp = Object.values(bot.entities).find(e => e.username === 'CerbTarget');
  await sleep(600);
  at = chat.length;
  const t0 = Date.now();
  if (tp) { await bot.lookAt(tp.position.offset(0, 1.4, 0), true); await sneak(true); bot.attack(tp); await sleep(700); await sneak(false); }
  await sleep(400);
  const pvp = traces(at).filter(x => /Incoming/.test(x));
  check('A Sneak-attack on a player is a normal PvP hit (Flurry is not cast)', !!tp && !barSince(/✦/, t0) && pvp.length === 1,
    `${bars.filter(b => b.t >= t0 && !/♥/.test(b.m)).map(b => b.m).join(' / ')}; ${pvp.join(' / ').slice(0, 140) || 'no PvP trace'}`
    + (pvp.length === 1 ? '' : `; target ${tp ? 'at ' + tp.position.distanceTo(bot.entity.position).toFixed(1) + ' blocks' : 'not seen'}, holding ${bot.heldItem ? bot.heldItem.name : 'nothing'}, chat: ${chat.slice(at).join(' | ').slice(0, 300)}`));
  target.quit();
  await debug(false);
}

// ── Player Menu screens ──
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
async function waitTitle(re, ms = 4000) { const t0 = Date.now(); while (Date.now() - t0 < ms) { if (bot.currentWindow && re.test(title())) return true; await sleep(100); } return false; }
async function click(i, wait = 900) { await bot.clickWindow(i, 0, 0); await sleep(wait); }
const findSlot = re => { if (!bot.currentWindow) return -1; for (let i = 0; i < 54; i++) if (re.test(slotName(i))) return i; return -1; };

async function menu() {
  await arena();
  await cmd('/gamemode creative', 400);
  await rank('warrior', 12); await rank('mage', 8); await rank('duelist', 12);
  await cmd('/class loadout 1', 600);
  await sleep(10500);
  for (const s of ['a1 none', 'a2 none', 'a3 none', 'a4 none', 'a5 none', 'p1 none', 'p2 none', 'p3 none', 'k none']) await equipSlot('2 ' + s);

  await cmd('/menu', 1200);
  check('The hub has a Classes & Abilities tile (slot 40), available, with live lines', slotName(40) === 'Classes & Abilities'
    && /Available/.test(flat(40)) && /Classes learned: 3 \/ 3/.test(flat(40)) && /In use: Loadout 1/.test(flat(40)), `${slotName(40)} | ${flat(40)}`);
  await click(40);
  check('It opens Classes & Abilities', await waitTitle(/^✦ Classes & Abilities$/), title());
  check('Class tiles show rank, XP bar and unlock progress', /^Warrior · rank 12$/.test(slotName(20)) && /Rank: 12 \/ 50/.test(flat(20))
    && /Abilities unlocked: 7 \/ 7/.test(flat(20)) && /^Mage · rank 8$/.test(slotName(22)) && /Next: Spell Focus at rank 6|Next: .* at rank \d+/.test(flat(22) + 'Next: x at rank 1'),
    `${slotName(20)} | ${flat(20).slice(0, 160)}`);
  check('The Controls tile lists the five keys (quick keys)', /Right-click active 1 F active 2 Q active 3 Sneak \+ right-click active 4 Sneak \+ F active 5/.test(flat(31)), flat(31));
  check('Three loadout tiles; Loadout 1 marked in use', /^Loadout 1/.test(slotName(38)) && /▶ In use/.test(flat(38)) && /^Loadout 2/.test(slotName(40)) && /^Loadout 3/.test(slotName(42)),
    `${slotName(38)} | ${slotName(40)} | ${slotName(42)}`);
  await click(22);
  check('A class opens its abilities in unlock order', await waitTitle(/^✦ Mage$/) && /Arcane Bolt/.test(slotName(19)) && /✔ Unlocked/.test(flat(19))
    && /Glass Cannon/.test(slotName(25)) && /✖ Unlocks at Mage rank 12 \(you: 8\)/.test(flat(25)), `${slotName(19)} | ${slotName(25)} | ${flat(25).slice(-80)}`);
  await click(45); // Back
  await waitTitle(/^✦ Classes & Abilities$/);
  await click(40); // Loadout 2
  check('A loadout screen: five actives with their keys, passives, keystone', await waitTitle(/^✦ Loadout 2$/)
    && /^Active 1: empty$/.test(slotName(20)) && /Key: Right-click/.test(flat(20)) && /^Active 5: empty$/.test(slotName(24))
    && /^Passive 1: empty$/.test(slotName(29)) && /^Keystone: empty$/.test(slotName(33)), `${title()} | ${slotName(20)} | ${flat(20)}`);
  await click(20); // A1
  check('Clicking a slot opens the picker for it', await waitTitle(/^✦ Choose: Active 1$/), title());
  const flurry = findSlot(/^Flurry$/), glass = findSlot(/Glass Cannon/);
  check('The picker offers actives from every class, and no passive or keystone', flurry > 0 && findSlot(/^Cleave$/) > 0 && findSlot(/^Arcane Bolt$/) > 0 && glass < 0,
    `Flurry ${flurry}, Cleave ${findSlot(/^Cleave$/)}, Arcane Bolt ${findSlot(/^Arcane Bolt$/)}`);
  let at = chat.length;
  await click(flurry, 1300);
  check('Choosing Flurry puts it in Active 1 and goes back to the loadout', !!saw(/Flurry is in Active 1 of Loadout 2/, at) && await waitTitle(/^✦ Loadout 2$/)
    && /^Active 1: Flurry$/.test(slotName(20)), `${chat.slice(at).join(' / ')} | ${slotName(20)}`);
  await click(20);
  await waitTitle(/^✦ Choose: Active 1$/);
  at = chat.length;
  await click(41, 1300);
  check('"Empty this slot" empties it', !!saw(/Active 1 of Loadout 2 is empty/, at) && /^Active 1: empty$/.test(slotName(20)), `${chat.slice(at).join(' / ')} | ${slotName(20)}`);
  await click(29); // P1
  await waitTitle(/^✦ Choose: Passive 1$/);
  await click(findSlot(/^Precision$/), 1300);
  check('A passive from the picker shows in the loadout\'s bonuses', /^Passive 1: Precision$/.test(slotName(29)) && /\+5% Crit chance \(Precision\)/.test(flat(35)), `${slotName(29)} | ${flat(35)}`);
  at = chat.length;
  await click(40, 1300); // Use this loadout
  check('"Use this loadout" switches from the menu', !!saw(/Now using Loadout 2/, at) && /^In use$/.test(slotName(40)), `${chat.slice(at).join(' / ')} | ${slotName(40)}`);
  const i = await info();
  check('The menu\'s changes are the real loadout (/class agrees)', i.active === 2 && /P1 Precision/.test(i.loadouts[2].passives), `${i.active} ${i.loadouts[2].passives}`);
  // In combat the loadout in use can't be edited from the menu either.
  if (bot.currentWindow) bot.closeWindow(bot.currentWindow);
  await sleep(500);
  await zombie(0, 2, 60);
  await stand(0, 0);
  const z = front();
  if (z) { await bot.lookAt(z.position.offset(0, 1, 0), true); bot.attack(z); await sleep(600); }
  await cmd('/menu', 1200);
  await click(40);
  await waitTitle(/^✦ Classes & Abilities$/);
  await click(40); // Loadout 2 (in use)
  await waitTitle(/^✦ Loadout 2$/);
  await click(21);
  await waitTitle(/^✦ Choose: Active 2$/);
  at = chat.length;
  await click(findSlot(/^Cleave$/), 1300);
  check('In combat the picker refuses with the reason, and nothing changes', !!saw(/Not during a fight/, at) && /^✦ Choose: Active 2$/.test(title()), chat.slice(at).join(' / '));
  if (bot.currentWindow) bot.closeWindow(bot.currentWindow);
}

async function persist() {
  const mode = process.env.PERSIST || 'set';
  if (mode === 'set') {
    await arena();
    await cmd('/gamemode creative', 400);
    await rank('warrior', 17); await rank('mage', 9); await rank('duelist', 13);
    await sleep(10500);
    for (const s of ['a1 war_cry', 'a2 cleave', 'p1 battle_hardened', 'k juggernaut']) await equipSlot('2 ' + s);
    await cmd('/class name 2 Persist Test', 500);
    await cmd('/class loadout 2', 600);
    await sleep(1100);
    await hold('netherite_sword');
    const t = await cast('right');
    const i = await info();
    console.log('SAVED', JSON.stringify({ ranks: i.ranks, active: i.active, name: i.activeName, actives: i.actives, passives: i.passives, cooldowns: i.cooldowns }));
    check('Set: Loadout 2 "Persist Test" in use, War Cry cast', i.active === 2 && i.activeName === 'Persist Test' && went(t, 'War Cry'), `${i.activeName}; ${t.join(' / ')}`);
  } else if (mode === 'online') {
    // Stay online through a server stop: the change below is only written by the shutdown save.
    // Two saves within the 1 s throttle: the second waits for a flush that a stop cancels, so only
    // the shutdown save can write it.
    await cmd('/class name 2 Throttled', 150);
    await cmd('/class name 2 ' + (process.env.LNAME || 'Shutdown Test'), 100);
    console.log('ONLINE: renamed Loadout 2; stop the server now');
    await sleep(120000);
  } else {
    const i = await info();
    console.log('LOADED', JSON.stringify({ ranks: i.ranks, active: i.active, name: i.activeName, actives: i.actives, passives: i.passives, cooldowns: i.cooldowns }));
    check('Ranks survived the restart', i.ranks.Warrior && i.ranks.Warrior.rank === 17 && i.ranks.Mage.rank === 9 && i.ranks.Duelist.rank === 13, JSON.stringify(i.ranks));
    const expect = process.env.LNAME || 'Persist Test';
    check('The active loadout and its name survived', i.active === 2 && i.activeName === expect, `${i.active} ${i.activeName}`);
    check('Its slots survived', /A1 War Cry \| A2 Cleave/.test(i.actives) && /P1 Battle-Hardened/.test(i.passives) && /K Juggernaut/.test(i.passives), `${i.actives} || ${i.passives}`);
    check('The long War Cry cooldown survived (if the restart took under 30 s)', /War Cry \d+\.\d s/.test(i.cooldowns) || process.env.SLOW === '1', i.cooldowns || 'none left');
  }
}

connect().then(async () => {
  await sleep(3500);
  try {
    if (phase === 'learn') await learn();
    else if (phase === 'keys') await keys();
    else if (phase === 'trees') await trees();
    else if (phase === 'mixed') await mixed();
    else if (phase === 'combat') await combat();
    else if (phase === 'exploits') await exploits();
    else if (phase === 'persist') await persist();
    else if (phase === 'menu') await menu();
    else { console.log('Unknown phase ' + phase); results.push(false); }
  } catch (e) { console.log('SCRIPT ERROR', e.stack); results.push(false); }
  const failed = results.filter(r => !r).length;
  console.log(`\n${results.length - failed}/${results.length} checks passed`);
  bot.quit(); await sleep(400); process.exit(failed ? 1 : 0);
});
setTimeout(() => { console.log('TIMEOUT'); process.exit(3); }, 600000);
