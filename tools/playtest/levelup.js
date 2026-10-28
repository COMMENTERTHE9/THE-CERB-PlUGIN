// Level-up and rank-up presentation in game (docs/design/LEVEL_UP.md). LOCAL SANDBOX servers only; the bot must be op.
// Usage: node levelup.js [phase]
//   level     - a kill that raises Blade Mastery: one chat line with the real gain, the HUD notice (health and Essence
//               still in the bar), a sound and a few particles; no title
//   milestone - Blade Mastery 9 -> 10 by a kill: the Apprentice title, the fanfare, more particles
//   together  - one Fireball that levels Spell Weaving, Intelligence and Elemental Mastery at once: one notice naming
//               them, one sound, three chat lines; the next level-up within 1.5 s shows text without effects
//   quiet     - admin corrections (/cerb skill ... xp / level, /cerb class ... rank) and a rejoin celebrate nothing
//   settings  - /levelup reduced: text and a soft sound, no particles or title; /levelup off: the chat line only
//   rank      - a Warrior rank gained by fighting: its own sound and colour, "New ability: Charge" and a title
//   requires  - an ability that needs a skill besides its rank (written into the sandbox's abilities.yml for the phase,
//               then put back; needs SERVER_DIR): the rank-up names what it still needs and equipping it says so; a
//               slot kept while the requirement is unmet stays, inert, and never blocks other edits; the skill's
//               level-up then makes it available and F casts it
//   all       - every phase but requires (the default)
const mineflayer = require('mineflayer');
const fs = require('fs');
const path = require('path');
const { toNotchianYaw, toNotchianPitch } = require('mineflayer/lib/conversions');
const phase = process.argv[2] || 'all';
const NAME = process.argv[3] || 'CerbSmoke';
const sleep = ms => new Promise(r => setTimeout(r, ms));
const chat = [];
const bars = [];      // action bar texts with their time
const sounds = [];    // { t, name }
const particles = []; // { t, id, count }
const titles = [];    // { t, kind, text }
const results = [];
const failed = [];
const check = (name, ok, detail = '') => {
  results.push(ok);
  if (!ok) failed.push(name);
  console.log(`${ok ? 'PASS' : 'FAIL'}  ${name}${detail ? '  (' + detail + ')' : ''}`);
};
const note = s => console.log('      ' + s);

let bot, pchat;
function connect() {
  const b = mineflayer.createBot({ host: '127.0.0.1', port: Number(process.env.MC_PORT || 25565), username: NAME, version: '1.21.11', auth: 'offline' });
  b.on('messagestr', (m, pos) => { if (pos !== 'game_info') chat.push({ t: Date.now(), m }); });
  b._client.on('action_bar', p => {
    try { pchat = pchat || require('prismarine-chat')(b.registry); bars.push({ t: Date.now(), m: pchat.fromNotch(p.text).toString() }); } catch (e) { /* ignore */ }
  });
  // A sound played at a player that follows them arrives as an entity sound, the others as a positioned one.
  b._client.on('sound_effect', p => sounds.push({ t: Date.now(), name: soundOf(p) }));
  b._client.on('entity_sound_effect', p => sounds.push({ t: Date.now(), name: soundOf(p) }));
  // Only particles centred on the player: a hit's own particles (damage hearts, a sweep) are centred on the mob.
  b._client.on('world_particles', p => {
    const me = b.entity && b.entity.position;
    if (!me || Math.hypot(p.x - me.x, p.z - me.z) > 1.4) return;
    particles.push({ t: Date.now(), count: Math.max(1, Number(p.amount) || 1) });
  });
  const title = kind => p => titles.push({ t: Date.now(), kind, text: plain(p.text) });
  b._client.on('set_title_text', title('title'));
  b._client.on('set_title_subtitle', title('subtitle'));
  b.on('kicked', r => { console.log('KICKED', r); process.exit(2); });
  bot = b;
  return new Promise(r => b.once('spawn', () => r(b)));
}
/**
 * Which presentation sound a packet is. This test client's 1.21.11 sound-id table is out of date (it names sounds
 * wrongly, see menu.js), so each sound is told apart by its own volume and pitch (Skills.Celebrations).
 */
const SIG = {
  'player.levelup': [0.5, 1.4],          // a skill level
  'milestone.levelup': [0.5, 1.1],       // a milestone's chime...
  'challenge_complete': [0.55, 1.0],     // ...and its fanfare
  'beacon.power_select': [0.6, 1.25],    // a class rank
  'enchantment_table.use': [0.8, 1.0],   // a class rank that opens an ability
  'note_block.pling': [0.35, 1.6],       // reduced mode
};
function soundOf(p) {
  const key = Object.keys(SIG).find(k => Math.abs(p.volume - SIG[k][0]) < 0.02 && Math.abs(p.pitch - SIG[k][1]) < 0.02);
  return key || `?(${Number(p.volume).toFixed(2)}/${Number(p.pitch).toFixed(2)})`;
}

// Text of a chat component as this client hands it over (NBT compound, JSON string, or JSON object).
const plain = v => {
  if (v == null) return '';
  if (typeof v === 'string') { try { return plain(JSON.parse(v)); } catch { return v; } }
  const s = JSON.stringify(v);
  const nbt = [...s.matchAll(/"text":\{"type":"string","value":"([^"]*)"/g)].map(m => m[1]);
  if (nbt.length) return nbt.join('');
  const str = [...s.matchAll(/"value":"([^"]*)"/g)].map(m => m[1]);
  if (str.length) return str.join('');
  return [...s.matchAll(/"text":"([^"]*)"/g)].map(m => m[1]).join('');
};

async function cmd(c, wait = 700) { const at = chat.length; bot.chat(c); await sleep(wait); return at; }
const saw = (re, at) => chat.slice(at).map(x => x.m).find(m => re.test(m));
const sawAll = (re, at) => chat.slice(at).map(x => x.m).filter(m => re.test(m));
const since = (list, t) => list.filter(x => x.t >= t);

// ── arena, mobs, skills ─────────────────────────────────────────
let X = 0, Y = 200, Z = 0;
async function arena() {
  const sp = bot.spawnPoint || bot.entity.position;
  X = Math.floor(sp.x) + 60; Z = Math.floor(sp.z) + 20;
  await cmd('/gamerule spawn_mobs false', 300);
  await cmd('/time set midnight', 300);
  await cmd('/gamerule advance_time false', 300);
  await cmd(`/fill ${X - 10} ${Y - 1} ${Z - 10} ${X + 10} ${Y - 1} ${Z + 10} stone`, 500);
  await cmd(`/fill ${X - 10} ${Y} ${Z - 10} ${X + 10} ${Y + 4} ${Z + 10} air`, 500);
  await stand(0, 0);
  await clearMobs();
  await cmd('/gamemode survival', 300);
  await cmd('/effect clear @s', 300);
  await cmd('/effect give @s resistance infinite 4 true', 300);
  await cmd('/effect give @s saturation infinite 1 true', 300);
  await cmd('/levelup full', 400);
}
async function clearMobs() { await cmd('/kill @e[type=!player,distance=..40]', 500); await sleep(300); }
async function stand(dx, dz, yaw = 0, pitch = 0) { await cmd(`/tp @s ${X + dx + 0.5} ${Y} ${Z + dz + 0.5} ${yaw} ${pitch}`, 600); }
async function zombie(dz = 2, dx = 0, level = 1) {
  const back = bot.entity.position.clone(), yaw = bot.entity.yaw, pitch = bot.entity.pitch;
  await cmd(`/tp @s ${X + dx + 0.5} ${Y} ${Z + dz + 0.5}`, 350);
  await cmd(`/cerb spawn zombie ${level} noai`, 700);
  await cmd(`/tp @s ${back.x} ${back.y} ${back.z}`, 450);
  await bot.look(yaw, pitch, true);
  await sleep(200);
  return Object.values(bot.entities).filter(e => e.name === 'zombie').sort((a, b) => a.position.distanceTo(bot.entity.position) - b.position.distanceTo(bot.entity.position))[0];
}
async function lookAt(e) { await bot.lookAt(e.position.offset(0, e.height * 0.6, 0), true); await sleep(150); }
async function hold(item) {
  await cmd('/clear @s', 300);
  await cmd(`/give @s ${item} 1`, 700);
  const i = bot.inventory.slots.findIndex((it, s) => it && it.name === item.replace(/\[.*$/, '') && s >= 36 && s <= 44);
  if (i >= 0) { bot.setQuickBarSlot(i - 36); await sleep(300); }
}
/** Kill the nearest zombie with an exact hit by this player (no swing: no sweep particles near the bot). */
async function hitKill(wait = 1200) {
  await cmd('/damage @e[type=zombie,limit=1,sort=nearest] 1000000000 minecraft:player_attack by @s', wait);
}
async function swingKill(e, max = 60) {
  for (let s = 0; s < max; s++) {
    const m = bot.entities[e.id];
    if (!m || !m.isValid) return true;
    await lookAt(m);
    bot.attack(m);
    await sleep(650);
  }
  return false;
}
const xpForLevel = l => Math.round(75 * Math.pow(Math.max(1, l), 1.85) + 40 * Math.max(1, l));
/** Put `skill` at `level`, `short` XP below the next level (both admin corrections: quiet). */
async function nearLevelUp(skill, level, short = 5) {
  await cmd(`/cerb skill ${NAME} ${skill} level ${level}`, 400);
  await cmd(`/cerb skill ${NAME} ${skill} xp ${xpForLevel(level) - short}`, 500);
}
async function skillLevel(skill) {
  const at = await cmd(`/cerb skill ${NAME} ${skill} xp 0`, 600);
  const m = (saw(/is now level \d+/, at) || '').match(/level (\d+)/);
  return m ? Number(m[1]) : NaN;
}
let sequence = 2000;
function rclick() {
  bot._client.write('use_item', { hand: 0, sequence: sequence++, rotation: { x: toNotchianYaw(bot.entity.yaw), y: toNotchianPitch(bot.entity.pitch) } });
}
/** What a moment of play showed: chat lines, HUD texts, sounds, particle packets and titles from `t` on. */
function shown(t, at) {
  return {
    chat: chat.slice(at).map(x => x.m),
    hud: since(bars, t).map(b => b.m),
    sounds: since(sounds, t).map(s => s.name),
    particles: since(particles, t).reduce((n, p) => n + p.count, 0),
    titles: since(titles, t).map(x => x.kind + ':' + x.text),
  };
}
const hasSound = (s, re) => s.sounds.some(n => re.test(n));
// A presentation's text: "▲ Blade Mastery 12", "★ Blade Mastery reached...", "✦ Warrior rank 3" (the HUD's own
// "4.36%★" effectiveness and "✦ Arcane Bolt" cast messages are not one).
const NOTICE = /▲ |★ [A-Za-z]|✦ (New|[A-Za-z]+ rank)/;
const brief = s => `chat ${JSON.stringify(s.chat.filter(m => /▲|★|✦|New ability|needs/.test(m)))}; hud ${JSON.stringify([...new Set(s.hud.filter(h => NOTICE.test(h)))].slice(0, 2))}; sounds ${JSON.stringify([...new Set(s.sounds)].slice(0, 6))}; particles ${s.particles}; titles ${JSON.stringify(s.titles)}`;
/** /class equip on loadout 1, waiting out the fight lock (8 s after the last hit) up to `tries` times. The reply. */
async function equip(slot, ability, tries = 14) {
  for (let i = 0; i < tries; i++) {
    const at = await cmd(`/class equip 1 ${slot} ${ability}`, 450);
    const r = chat.slice(at).map(x => x.m).find(m => /Equipped|Cleared|not unlocked|Not during a fight|Unknown|can't go|twice|Only one|still loading/.test(m)) || '(no reply)';
    if (!/Not during a fight/.test(r) || i === tries - 1) return r;
    await sleep(1000);
  }
}
const keyF = () => bot._client.write('block_dig', { status: 6, location: { x: 0, y: 0, z: 0 }, face: 0, sequence: 0 });
const keyQ = () => { bot._client.write('block_dig', { status: 4, location: { x: 0, y: 0, z: 0 }, face: 0, sequence: 0 }); bot.swingArm('right'); };
/** The HUD texts of the last `ms`. */
const recentHud = ms => [...new Set(bars.filter(b => b.t >= Date.now() - ms).map(b => b.m))];

// ════════════════════════════════════════════════════════════════
async function level() {
  await clearMobs(); await stand(0, 0);
  await hold('netherite_sword');
  await nearLevelUp('Blade_Mastery', 11);
  const z = await zombie(2);
  await sleep(1600); // past the effect gap of anything earlier
  const t = Date.now(), at = chat.length;
  await hitKill();
  const s = shown(t, at);
  const line = s.chat.find(m => /▲ Blade Mastery reached level 12/.test(m));
  check('A real level-up: one chat line naming the skill, its level and the real gain', !!line && /Power \(swords\) [\d.]+ → [\d.]+/.test(line), line || brief(s));
  const hud = s.hud.find(h => /▲ Blade Mastery 12/.test(h));
  check('...the HUD notice, with health and Essence still in the bar', !!hud && /♥/.test(hud) && /✎/.test(hud) && /\+[\d.]+ Power/.test(hud), hud || JSON.stringify(s.hud.slice(-3)));
  check('...a sound and a small particle effect, no title', hasSound(s, /player\.levelup/) && s.particles > 0 && s.particles <= 20 && s.titles.length === 0, brief(s));
  check('Exactly one level-up line (no old "Congratulations" message as well)', s.chat.filter(m => /reached level|Congratulations/.test(m)).length === 1, JSON.stringify(s.chat.slice(0, 6)));
}

async function milestone() {
  await clearMobs(); await stand(0, 0);
  await hold('netherite_sword');
  await nearLevelUp('Blade_Mastery', 9);
  const z = await zombie(2);
  await sleep(1600);
  const t = Date.now(), at = chat.length;
  await hitKill(1500);
  const s = shown(t, at);
  check('Blade Mastery 10: the Apprentice title', s.titles.some(x => /title:Apprentice/.test(x)) && s.titles.some(x => /subtitle:Blade Mastery 10/.test(x)), brief(s));
  check('...the fanfare and a bigger effect (bounded)', hasSound(s, /challenge_complete/) && s.particles > 20 && s.particles <= 60, brief(s));
  check('...and the chat line names the milestone', s.chat.some(m => /★ Blade Mastery reached level 10 · Apprentice/.test(m)), JSON.stringify(s.chat.slice(0, 4)));
}

async function together() {
  await clearMobs(); await stand(0, 0);
  // Nothing on the ability keys: right-click is the wand's own Fireball (an ability's message would hold the notice back).
  for (const sl of ['a1', 'a2', 'a3', 'a4', 'a5']) await equip(sl, 'none');
  for (const sk of ['Spell_Weaving', 'Intelligence', 'Elemental_Mastery']) await nearLevelUp(sk, 6, 3);
  await cmd('/clear @s', 300);
  await cmd('/givetestitem fireball', 800);
  const w = bot.inventory.slots.findIndex((it, s) => it && it.name === 'blaze_rod' && s >= 36 && s <= 44);
  if (w >= 0) bot.setQuickBarSlot(w - 36);
  await cmd(`/cerb mana ${NAME} full`, 400);
  const z = await zombie(6);
  await sleep(1600);
  let t = Date.now(), at = chat.length;
  await lookAt(z); rclick();
  await sleep(3000);
  let s = shown(t, at);
  const lines = s.chat.filter(m => /▲ (Spell Weaving|Intelligence|Elemental Mastery) reached level 7/.test(m));
  const hud = s.hud.find(h => /▲/.test(h)) || '';
  check('Three skills in one moment: three chat lines', lines.length === 3, JSON.stringify(lines));
  check('...one HUD notice naming each briefly', /▲ Spell Weaving 7 · ▲ Intelligence 7 · ▲ Elemental Mastery 7/.test(hud) && !/Essence/.test(hud),
    hud || JSON.stringify([...new Set(s.hud)].slice(-5)));
  check('...one sound', s.sounds.filter(n => /player\.levelup/.test(n)).length === 1, JSON.stringify(s.sounds));
  // A burst: two Blade Mastery level-ups about 0.7 s apart. Both lines show; the effects play once.
  await clearMobs();
  await hold('netherite_sword');
  await nearLevelUp('Blade_Mastery', 20, 1);
  await zombie(2, -1); await zombie(2, 1);
  await sleep(1600);
  t = Date.now(); at = chat.length;
  bot.chat('/damage @e[type=zombie,limit=1,sort=nearest] 1000000000 minecraft:player_attack by @s'); // 20 -> 21 (39 XP into it)
  await sleep(350);
  bot.chat(`/cerb skill ${NAME} Blade_Mastery xp ${xpForLevel(21) - 39 - 1}`);                      // quietly 1 short again
  await sleep(350);
  bot.chat('/damage @e[type=zombie,limit=1,sort=nearest] 1000000000 minecraft:player_attack by @s'); // 21 -> 22
  await sleep(2000);
  s = shown(t, at);
  check('A burst of level-ups: every line shows, the effects play once', s.chat.some(m => /▲ Blade Mastery reached level 21/.test(m))
    && s.chat.some(m => /▲ Blade Mastery reached level 22/.test(m)) && s.sounds.filter(n => /player\.levelup/.test(n)).length === 1 && s.particles <= 20, brief(s));
  await clearMobs();
}

async function quiet() {
  await clearMobs(); await stand(0, 0);
  await sleep(1600);
  let t = Date.now(), at = chat.length;
  await cmd(`/cerb skill ${NAME} Blade_Mastery level 1`, 500);
  await cmd(`/cerb skill ${NAME} Blade_Mastery xp 200000`, 900);
  await cmd(`/cerb skill ${NAME} Survival level 30`, 600);
  await cmd(`/cerb class ${NAME} warrior rank 9`, 600);
  await cmd(`/cerb class ${NAME} warrior rank 11`, 900);
  let s = shown(t, at);
  check('Admin corrections celebrate nothing (skill XP, skill level, class rank)',
    !s.chat.some(m => /reached level|▲|★|✦ Warrior rank/.test(m)) && !s.hud.some(h => NOTICE.test(h)) && !hasSound(s, /levelup|challenge|beacon/) && s.titles.length === 0, brief(s));
  check('...the admin still gets the confirmation', s.chat.some(m => /is now level/.test(m)) && s.chat.some(m => /Warrior is now rank 11/.test(m)), JSON.stringify(s.chat.slice(-3)));
  // Rejoin: loading saved data replays nothing.
  at = chat.length; t = Date.now();
  bot.quit();
  await sleep(2500);
  await connect();
  await sleep(7000);
  s = shown(t, at);
  check('A rejoin replays nothing', !s.chat.some(m => /reached level|▲|★|✦/.test(m)) && s.titles.length === 0, brief(s));
  for (const sk of ['Blade_Mastery', 'Survival']) await cmd(`/cerb skill ${NAME} ${sk} level 1`, 300);
}

async function settings() {
  await clearMobs(); await stand(0, 0);
  await hold('netherite_sword');
  await cmd('/levelup reduced', 500);
  await nearLevelUp('Blade_Mastery', 13);
  let z = await zombie(2);
  await sleep(1600);
  let t = Date.now(), at = chat.length;
  await hitKill();
  let s = shown(t, at);
  check('Reduced: the chat line, the HUD notice and a soft sound; no particles, no title',
    s.chat.some(m => /▲ Blade Mastery reached level 14/.test(m)) && s.hud.some(h => /▲ Blade Mastery 14/.test(h))
    && hasSound(s, /note_block\.pling/) && !hasSound(s, /player\.levelup/) && s.particles === 0 && s.titles.length === 0, brief(s));
  await cmd('/levelup off', 500);
  await nearLevelUp('Blade_Mastery', 15);
  z = await zombie(2);
  await sleep(1600);
  t = Date.now(); at = chat.length;
  await hitKill();
  s = shown(t, at);
  check('Off: only the chat line', s.chat.some(m => /▲ Blade Mastery reached level 16/.test(m)) && !s.hud.some(h => /▲/.test(h))
    && !hasSound(s, /levelup|pling|challenge/) && s.particles === 0 && s.titles.length === 0, brief(s));
  const at2 = await cmd('/levelup', 600);
  check('/levelup with no choice cycles back to Full', !!saw(/Level-ups: Full/, at2), chat.slice(at2).map(x => x.m).join(' / '));
  await cmd(`/cerb skill ${NAME} Blade_Mastery level 1`, 300);
}

/** "rank 2 (147/151" from /class. */
async function warrior() {
  const at = await cmd('/class', 800);
  const m = (saw(/Warrior: rank \d+ \(\d+\/\d+/, at) || '').match(/rank \d+ \(\d+\/\d+/);
  return m ? m[0] : '?';
}

async function rank() {
  await clearMobs(); await stand(0, 0);
  await hold('netherite_sword');
  await cmd(`/cerb skill ${NAME} Blade_Mastery level 50`, 400);
  await cmd(`/cerb class ${NAME} warrior rank 2`, 500);
  await cmd(`/cerb class ${NAME} warrior xp ${151 - 4}`, 600); // 4 short of rank 3 (151 XP from rank 2), quietly
  for (const s of ['a1', 'a2', 'a3', 'a4', 'a5']) await equip(s, s === 'a1' ? 'cleave' : 'none');
  await cmd('/class loadout 1', 1000);
  const z = await zombie(2, 0, 10);
  const w0 = await warrior();
  await sleep(1600);
  const t = Date.now(), at = chat.length;
  await lookAt(z); rclick(); // Cleave: class XP for the cast
  await sleep(600);
  await hitKill(1500);
  const s = shown(t, at);
  const w1 = await warrior();
  check('Warrior rank 3 by fighting: "✦ Warrior rank 3" and "New ability: Charge"', s.chat.some(m => /✦ Warrior rank 3/.test(m)) && s.chat.some(m => /New ability: Charge/.test(m)),
    `Warrior ${w0} -> ${w1}; ${brief(s)}`);
  check('...its own sound and colour, and a title naming the ability', hasSound(s, /beacon\.power_select/) && s.titles.some(x => /Charge/.test(x)) && s.particles > 0 && s.particles <= 40, brief(s));
  await equip('a1', 'none');
  await cmd(`/cerb skill ${NAME} Blade_Mastery level 1`, 300);
}

// ── requires: a synthetic requirement written into the sandbox's abilities.yml for the phase ──
async function requires() {
  const dir = process.env.SERVER_DIR;
  if (!dir) { check('requires needs SERVER_DIR (the sandbox server folder)', false); return; }
  const file = path.join(dir, 'plugins', 'CerberusPlugin', 'abilities.yml');
  const original = fs.readFileSync(file, 'utf8');
  const edited = original.replace(/(\n  frost_nova:\n    class: mage\n)/, '$1    requires: { Elemental Mastery: 25 }\n');
  if (edited === original) { check('requires: Frost Nova found in the sandbox abilities.yml', false); return; }
  try {
    fs.writeFileSync(file, edited);
    await cmd('/cerb reload', 2500);
    await clearMobs(); await stand(0, 0);
    await cmd(`/cerb skill ${NAME} Intelligence level 5`, 400);
    await cmd(`/cerb skill ${NAME} Elemental_Mastery level 1`, 400);
    await cmd(`/cerb class ${NAME} mage rank 4`, 500);
    await cmd(`/cerb class ${NAME} mage xp ${Math.round(80 + 25 * Math.pow(4, 1.5)) - 4}`, 600); // 4 short of rank 5
    // Arcane Bolt on Q and nothing on right-click, so the Fireball Wand keeps its own spell.
    for (const [sl, a] of [['a1', 'none'], ['a2', 'none'], ['a3', 'arcane_bolt'], ['a4', 'none'], ['a5', 'none'], ['p1', 'none']]) await equip(sl, a);
    await cmd('/class loadout 1', 1000);
    await cmd('/clear @s', 300);
    await cmd('/givetestitem fireball', 800);
    const w = bot.inventory.slots.findIndex((it, s) => it && it.name === 'blaze_rod' && s >= 36 && s <= 44);
    if (w >= 0) bot.setQuickBarSlot(w - 36);
    let z = await zombie(5, 0, 10);
    await sleep(1600);
    let t = Date.now(), at = chat.length;
    await cmd(`/cerb mana ${NAME} full`, 300);
    await lookAt(z); keyQ(); // Arcane Bolt: class XP
    await sleep(1500);
    let s = shown(t, at);
    check('Mage rank 5 by fighting: the rank-up says Frost Nova still needs Elemental Mastery 25', s.chat.some(m => /✦ Mage rank 5/.test(m)) && s.chat.some(m => /Frost Nova.*needs Elemental Mastery 25/.test(m))
      && !s.chat.some(m => /New ability: Frost Nova/.test(m)), brief(s));
    // Locked: it can't be equipped, and the refusal says what it still needs.
    let r = await equip('a2', 'frost_nova', 1);
    check('...it stays locked: equipping it says what it still needs', /Frost Nova is not unlocked yet: it also needs Elemental Mastery 25/.test(r), r);
    // A slot kept while a requirement is unmet: open it (an admin's level), equip it, take the level away again.
    await cmd(`/cerb skill ${NAME} Elemental_Mastery level 25`, 400);
    const put = await equip('a2', 'frost_nova');
    await cmd(`/cerb skill ${NAME} Elemental_Mastery level 1`, 400);
    await cmd(`/cerb class ${NAME} cooldowns`, 300); await cmd(`/cerb mana ${NAME} full`, 300);
    keyF(); await sleep(900);
    let hud = recentHud(1000);
    check('A kept slot whose requirement is unmet stays, inert: F says what it needs', /Equipped Frost Nova/.test(put) && hud.some(b => /Frost Nova.*Elemental Mastery 25/.test(b)),
      `${put}; ${JSON.stringify(hud.slice(-3))}`);
    r = await equip('p1', 'essence_flow');
    check('...and never blocks an unrelated edit of that loadout', /Equipped Essence Flow/.test(r), r);
    // Elemental Mastery 24 -> 25 by a fire spell that hits: the Journeyman milestone and the ability, together.
    await nearLevelUp('Elemental_Mastery', 24, 3);
    await clearMobs();
    z = await zombie(6, 0, 25); // a mob of the skill's level: a weak one pays a skill this high a quarter (Kill XP v2)
    await sleep(1600);
    t = Date.now(); at = chat.length;
    await cmd(`/cerb mana ${NAME} full`, 300);
    await lookAt(z); rclick();
    await sleep(2200);
    s = shown(t, at);
    check('Elemental Mastery 25 by a spell: Journeyman, and "New ability: Frost Nova"', s.chat.some(m => /★ Elemental Mastery reached level 25 · Journeyman/.test(m)) && s.chat.some(m => /New ability: Frost Nova/.test(m)), brief(s));
    await cmd(`/cerb class ${NAME} cooldowns`, 300); await cmd(`/cerb mana ${NAME} full`, 300);
    keyF(); await sleep(900);
    hud = recentHud(1000);
    check('...and F now casts it', hud.some(b => /✦ Frost Nova/.test(b)) && !hud.some(b => /Elemental Mastery 25/.test(b)), JSON.stringify(hud.slice(-3)));
  } finally {
    fs.writeFileSync(file, original);
    await cmd('/cerb reload', 2500);
    for (const sk of ['Elemental_Mastery', 'Spell_Weaving', 'Intelligence']) await cmd(`/cerb skill ${NAME} ${sk} level 1`, 300);
    for (const sl of ['a1', 'a2', 'a3', 'a4', 'a5', 'p1']) await equip(sl, 'none');
  }
}

(async () => {
  await connect();
  await sleep(3000);
  await arena();
  const phases = { level, milestone, together, quiet, settings, rank, requires };
  const run = phase === 'all' ? ['level', 'milestone', 'together', 'quiet', 'settings', 'rank'] : phase.split(',');
  for (const p of run) {
    if (!phases[p]) { console.log('unknown phase ' + p); continue; }
    console.log(`\n── ${p} ──`);
    try { await phases[p](); } catch (e) { check(`${p} ran to the end`, false, e.stack.split('\n').slice(0, 2).join(' ')); }
  }
  await clearMobs();
  for (const s of ['Blade_Mastery', 'Spell_Weaving', 'Intelligence', 'Elemental_Mastery']) await cmd(`/cerb skill ${NAME} ${s} level 1`, 250);
  await cmd('/levelup full', 300);
  await cmd('/clear @s', 300);
  await cmd('/effect clear @s', 300);
  await cmd('/gamerule spawn_mobs true', 300);
  await cmd('/gamerule advance_time true', 300);
  const passed = results.filter(Boolean).length;
  console.log(`\n${passed}/${results.length} passed`);
  if (failed.length) console.log('FAILED: ' + failed.join(' | '));
  bot.quit();
  setTimeout(() => process.exit(failed.length ? 1 : 0), 500);
})();
