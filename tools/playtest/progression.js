// Progression in-game checks (kill XP, and later damage/survivability by skill level).
// LOCAL SANDBOX servers only; the bot must be op.  Usage: node progression.js [phase]
//   killxp  - kill XP v2 by mob level, anti-grind
//   swings  - swings-to-kill table: Blade Mastery 1/50/100 vs zombie level 1/50 (prints numbers)
//   stats   - Player Menu -> Stats page shows attributes, and updates after a level change
//   survive - same level-50 zombie hits at Survival/Heavy Armor Training/First Aid 1 vs 100 (Vitality, Fortitude)
//   magic   - Essence: Intelligence/Mana Regeneration 1 vs 100 -> max mana, regen, Fireball damage vs a mob
//   persist - PERSIST=set: set test levels and record Stats; restart the server; PERSIST=check: same Stats after restart
//   realm   - admin test realm: Champion vs a Champion (RARE) elite takes the same swings as Awakened vs a common
//             zombie; Primordial magnitudes (billions) display and compute cleanly; back to default
//   perf    - 20 hits on a mob cause no attribute rebuilds (snapshot stays cached; only level-ups rebuild)
//   offswitch - with attributes.yml integration.pve-offense: false, Blade Mastery 100 kills like Blade Mastery 1
//   speed   - speed stats measured in game: attack interval by Blade/Weapon Mastery (server attribute value and
//             damage at a fixed swing rhythm), bow draw (power and critical arrows by draw time), movement speed,
//             spell cooldown (Fireball intervals), and the PvP pause (spawns CerbTarget as a second player)
//   defense - how a hit on a player is reduced (exact /damage hits, read from the combat trace): armor counts
//             as soon as it is put on or taken off, resistance x armor (70% at most today, under the 75% cap),
//             then the defense bar takes its share of what is left
//   mastery - Weapon Mastery with the specialized skills: swings to kill a level-100 zombie and the server's Power
//             multiplier, netherite sword at Blade Mastery 100 + Weapon Mastery 1 vs 100, netherite axe at Weapon Mastery 100
//   pvp     - PvP plays as before Attributes 2.0 (attributes.yml integration.pvp-defense: false): exact /damage hits
//             by this bot on a second player (CerbTarget) with defensive skills 100 use the old armor (gear + Heavy
//             Armor), Survival resistance, and take the share of health a 100-health defender lost
//   defense-cap - the 75% cap binding: needs a TEMPORARILY raised limits.resist (e.g. 0.75) in the sandbox
//             server's plugins/CerberusPlugin/attributes.yml (then /cerb reload); fire resist 70% + armor 40% = 82% -> 75%
//   fire    - decision C: Survival's fire resistance on every kind of fire (/damage in_fire, on_fire, lava; standing
//             on a real campfire and magma block), lava at half; a non-fire hit gets none; still under the 75% cap
//   projectile - decision D: a skeleton's arrows train Survival and Heavy Armor Mastery; a dispenser's arrows, your
//             own harming potion, a second arrow inside the invulnerability window and a 0-damage hit train nothing
//   spear   - decision E: a spear kill pays Weapon Mastery XP (once: no Heavy Armor Mastery XP on top), and spear
//             hits use Weapon Mastery's Power (x7.25 at Weapon Mastery 100, as axes)
const mineflayer = require('mineflayer');
const phase = process.argv[2] || 'killxp';
const NAME = process.argv[3] || 'CerbSmoke';
const sleep = ms => new Promise(r => setTimeout(r, ms));
const chat = [];
let actionBar = '';
const results = [];
const check = (name, ok, detail = '') => { results.push(ok); console.log(`${ok ? 'PASS' : 'FAIL'}  ${name}${detail ? '  (' + detail + ')' : ''}`); };
const bot = mineflayer.createBot({ host: '127.0.0.1', port: 25565, username: NAME, version: '1.21.11', auth: 'offline' });
bot.on('messagestr', (m, pos) => { if (pos === 'game_info') actionBar = m; else chat.push(m); });
// The HUD comes as the dedicated action-bar packet (Paper's sendActionBar), which this mineflayer
// version doesn't turn into a message event: read it directly.
bot._client.on('action_bar', p => { try { actionBar = require('prismarine-chat')(bot.registry).fromNotch(p.text).toString(); } catch (e) { /* keep last */ } });
bot.on('kicked', r => { console.log('KICKED', r); process.exit(2); });

// Text of a chat component as mineflayer exposes it (NBT compound, JSON string, or JSON object).
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
const slotLore = i => { const it = bot.currentWindow && bot.currentWindow.slots[i]; return it && it.customLore ? it.customLore.map(plain).join(' | ') : ''; };
async function waitTitle(re, ms = 4000) { const t0 = Date.now(); while (Date.now() - t0 < ms) { if (bot.currentWindow && re.test(title())) return true; await sleep(100); } return false; }
async function click(i, wait = 800) { await bot.clickWindow(i, 0, 0); await sleep(wait); }

/** "Swords: P -> xM damage" from a Power tile; M must equal 1 + 0.05 * P (both as displayed). */
const swordPower = lore => { const m = (lore || '').match(/Swords: ([\d.]+) +-> +x([\d.]+) damage/); return m ? { p: Number(m[1]), m: Number(m[2]) } : null; };
const consistent = sp => !!sp && Math.abs(sp.m - (1 + 0.05 * sp.p)) < 0.011;

async function cmd(c, wait = 1000) { const at = chat.length; bot.chat(c); await sleep(wait); return at; }
const saw = (re, since) => chat.slice(since).find(m => re.test(m));
const mobsNear = (name, r = 6) => Object.values(bot.entities).filter(e => e.name === name && e.position.distanceTo(bot.entity.position) < r);

let heldSlot = 0;
async function equip(item) {
  await cmd('/clear @s ' + item, 600);
  await cmd('/give @s ' + item, 900);
  const slot = bot.inventory.slots.findIndex((it, i) => it && it.name === item && i >= 36 && i <= 44);
  if (slot >= 0) bot.setQuickBarSlot(slot - 36);
  await sleep(300);
  heldSlot = slot - 36;
  return slot >= 0;
}

/** Total XP a skill holds: every level's cost below its level (SkillManager.xpForLevel) plus the XP into it. */
const xpForLevel = l => Math.round(75 * Math.pow(Math.max(1, l), 1.85) + 40 * Math.max(1, l));
const totalXp = s => { let t = s.xp; for (let l = 1; l < s.level; l++) t += xpForLevel(l); return t; };

/**
 * Spawn a no-AI mob at `level`, hit it until it dies (vanilla attack cooldown respected: `delay` ms per swing).
 * With `skill`, the kill's XP is read back from the server (kill XP shows on the HUD now, not in chat).
 */
async function killOne(type, level, entity = type, delay = 650, skill = null) {
  const before = skill ? await skillXp(skill) : null;
  await cmd(`/kill @e[type=${entity},distance=..10]`, 600);
  const at = await cmd(`/cerb spawn ${type} ${level} noai`, 1500);
  const target = mobsNear(entity, 5)[0];
  if (!target) return { swings: 0, xp: NaN, msg: 'mob did not spawn' };
  let swings = 0;
  for (; swings < 400; swings++) {
    const m = bot.entities[target.id];
    if (!m || !m.isValid) break;              // died (entity removed)
    if (m.position.distanceTo(bot.entity.position) > 4.5) { console.log('  target out of reach'); break; }
    await bot.lookAt(m.position.offset(0, m.height * 0.6, 0), true);
    bot.attack(m);
    await sleep(delay); // a full cooldown (sword ~0.625 s) so every hit is a full-strength hit
  }
  await sleep(800);
  const hud = actionBar;
  const after = skill ? await skillXp(skill) : null;
  const xp = skill ? totalXp(after) - totalXp(before) : NaN;
  return { swings, xp, msg: skill ? `${skill} +${xp} (HUD: ${hud})` : '', hud, grey: !!saw(/Low-level mob/, at) };
}

async function killxp() {
  await cmd('/gamemode creative');
  check('Got a netherite sword', await equip('netherite_sword'));
  await cmd(`/cerb skill ${NAME} Blade_Mastery level 1`);
  let r = await killOne('zombie', 1, 'zombie', 650, 'Blade_Mastery');
  check('Common L1 kill pays 40 Blade Mastery XP', r.xp === 40, r.msg + ', ' + r.swings + ' swings');
  check('...and the HUD shows it (real-time XP display)', /\+40 Blade Mastery/.test(r.hud), r.hud);
  await sleep(4500); // let the XP line's merge window pass
  r = await killOne('zombie', 50, 'zombie', 650, 'Blade_Mastery');
  check('Common L50 kill pays 334 Blade Mastery XP', r.xp === 334, r.msg + ', ' + r.swings + ' swings');
  await cmd(`/cerb skill ${NAME} Blade_Mastery level 30`);
  r = await killOne('zombie', 1, 'zombie', 650, 'Blade_Mastery');
  check('Anti-grind: L1 mob at Blade Mastery 30 pays x0.25 and says so', r.xp === 10 && r.grey, r.msg + ' | ' + chat.slice(-6).join(' / '));
  await cmd(`/cerb skill ${NAME} Blade_Mastery level 1`);
}

async function swings() {
  await cmd('/gamemode creative');
  await equip('netherite_sword');
  await cmd(`/cerb skill ${NAME} Critical_Strike level 1`);
  // Weapon Mastery adds 0.25 Power per level to every weapon; pin it (level 1, the lowest settable) so the
  // traced multiplier is exact whatever earlier phases left behind.
  await cmd(`/cerb skill ${NAME} Weapon_Mastery level 1`);
  const rows = [];
  for (const bm of [1, 50, 100]) {
    await cmd(`/cerb skill ${NAME} Blade_Mastery level ${bm}`);
    for (const lvl of [1, 50]) {
      // Average two kills: crits (12.5% at Critical Strike 1) make single kills vary by a swing.
      const a = await killOne('zombie', lvl), b = await killOne('zombie', lvl);
      const avg = (a.swings + b.swings) / 2;
      rows.push({ bm, lvl, swings: avg });
      console.log(`Blade Mastery ${bm} vs zombie L${lvl}: ${a.swings} and ${b.swings} swings`);
    }
  }
  // One traced hit, to confirm the multiplier the server applied.
  await cmd(`/cerb skill ${NAME} Blade_Mastery level 50`);
  await cmd('/cerb combatdebug', 600);
  const at = chat.length;
  await cmd('/kill @e[type=zombie,distance=..10]', 600);
  await cmd('/cerb spawn zombie 50 noai', 1500);
  const z = mobsNear('zombie', 5)[0];
  if (z) { await bot.lookAt(z.position.offset(0, z.height * 0.6, 0), true); bot.attack(z); await sleep(900); }
  const trace = chat.slice(at).find(m => /\[Combat\] Power/.test(m)) || '';
  await cmd('/cerb combatdebug', 600);
  await cmd('/kill @e[type=zombie,distance=..10]', 600);
  await cmd(`/cerb skill ${NAME} Blade_Mastery level 1`);
  return { rows, trace };
}

async function openStats() {
  if (bot.currentWindow) { bot.closeWindow(bot.currentWindow); await sleep(400); }
  const head = bot.inventory.slots[44];
  if (!head || head.name !== 'player_head') return false;
  bot.setQuickBarSlot(8); await sleep(300);
  bot.activateItem(); await sleep(1200);
  if (!(await waitTitle(/Player Menu/))) return false;
  await click(21, 1200); // hub: Stats & Attributes
  return waitTitle(/Stats & Attributes/);
}

async function stats() {
  await cmd('/gamemode creative');
  await equip('netherite_sword');
  await cmd(`/cerb skill ${NAME} Blade_Mastery level 1`);
  await cmd(`/cerb skill ${NAME} Critical_Strike level 1`);
  check('Player Menu -> Stats opens', await openStats(), title());
  // Other phases leave Weapon Mastery (+0.25 Power for all weapons) at level 1 or more, and levels can't go back
  // to 0: check that the displayed numbers agree with each other instead of hard-coding a total.
  const sp1 = swordPower(slotLore(10));
  check('Power tile shows sword Power from Blade Mastery 1', consistent(sp1) && sp1.p >= 1 && /Blade Mastery 1: \+1\.0 \(swords\)/.test(slotLore(10)), slotLore(10));
  check('Power tile says which skills feed it', /Gained from.*Blade Mastery \+1 \(swords\).*Weapon Mastery \+0\.25/.test(slotLore(10)), slotLore(10));
  check('Agility tile shows crit from Critical Strike', /Crit chance: 1[0-9]\.[0-9]%/.test(slotLore(11)), slotLore(11));
  check('Power is marked active against mobs', /Active \(fighting mobs\)/.test(slotLore(10)), slotLore(10));
  check('Vitality is marked active', /● Active/.test(slotLore(12)), slotLore(12));
  check('Essence is marked active', /● Active/.test(slotLore(15)), slotLore(15));
  // Since the gathering loop (2026-10-25) Fortune is live: the extra-ore chance, shown with its cap.
  check('Fortune shows its real effect, the extra-ore chance', /Extra ore when mining: [\d.]+% \(max 50\.0%\)/.test(slotLore(16)) && /Active/.test(slotLore(16)), slotLore(16));
  // Stats is opened from the Player Menu, so the held item there is the head; check with the sword in hand.
  await cmd(`/cerb skill ${NAME} Blade_Mastery level 50`);
  if (bot.currentWindow) { bot.closeWindow(bot.currentWindow); await sleep(300); }
  bot.setQuickBarSlot(heldSlot); await sleep(300);
  await cmd('/stats', 1500);
  const viaCommand = /Stats & Attributes/.test(title());
  check('/stats opens the page', viaCommand, title());
  if (!viaCommand) return;
  const sp50 = swordPower(slotLore(10));
  check('A level change shows immediately (Blade Mastery 50: +49 Power, multiplier follows)', consistent(sp50) && sp1 && Math.abs(sp50.p - sp1.p - 49) < 0.11, slotLore(10));
  check('Weapon tile reads the held sword', /Holding: Netherite Sword.*Power: 5\d\.\d +-> +x3\.\d\d damage.*Keeps pace with level-5\d mobs/.test(slotLore(29)), slotLore(29));
  const nl = slotLore(38).match(/Blade Mastery 50 -> 51:.*Sword damage x([\d.]+) -> x([\d.]+)/);
  check('Next-level tile previews Blade Mastery 51 (+0.05)', !!nl && Math.abs(Number(nl[2]) - Number(nl[1]) - 0.05) < 0.011, slotLore(38));
  check('Kill XP tile lists XP per mob level', /Trains: Blade Mastery \(level 50\).*Level 1: 10 .*reduced.*Level 50: 334/.test(slotLore(40)), slotLore(40));
  await click(45, 1200);
  check('Back returns to the Player Menu', await waitTitle(/Player Menu/), title());
  if (bot.currentWindow) bot.closeWindow(bot.currentWindow);
  await cmd(`/cerb skill ${NAME} Blade_Mastery level 1`);
}

async function survive() {
  await cmd('/gamemode survival');
  await cmd('/difficulty normal');
  await cmd('/time set day');
  const out = {};
  for (const lv of [1, 100]) {
    for (const sk of ['Survival', 'Heavy_Armor_Training', 'First_Aid']) await cmd(`/cerb skill ${NAME} ${sk} level ${lv}`, 500);
    await sleep(800);
    await cmd(`/cerb heal ${NAME}`, 800);
    await cmd('/stats', 1500);
    const lore = slotLore(31);
    if (bot.currentWindow) bot.closeWindow(bot.currentWindow);
    const maxHp = Number((lore.match(/Max health: ([\d.]+)/) || [])[1]);
    await cmd('/kill @e[type=zombie,distance=..16]', 600);
    await cmd('/cerb combatdebug', 500);
    const at = chat.length;
    await cmd('/cerb spawn zombie 50', 500);
    const t0 = Date.now();
    while (Date.now() - t0 < 25000 && chat.slice(at).filter(m => /\[Combat\].*Final/.test(m)).length < 3) await sleep(250);
    await cmd('/cerb combatdebug', 500);
    await cmd('/kill @e[type=zombie,distance=..16]', 600);
    const traces = chat.slice(at).filter(m => /\[Combat\].*Final/.test(m)).slice(0, 3);
    const finals = traces.map(t => Number((t.match(/Final: ([\d.]+)/) || [])[1]));
    const avg = finals.reduce((a, b) => a + b, 0) / Math.max(1, finals.length);
    // Switching away from an item with metadata (the Player Menu head) used to reset max health to 100.
    bot.setQuickBarSlot(8); await sleep(400); bot.setQuickBarSlot(0); await sleep(400);
    await cmd('/stats', 1500);
    const afterSwap = Number(((slotLore(31).match(/Max health: ([\d.]+)/)) || [])[1]);
    if (bot.currentWindow) bot.closeWindow(bot.currentWindow);
    out[lv] = { maxHp, avg, pct: avg / maxHp, traces, lore, afterSwap };
    console.log(`Defensive skills ${lv}: max HP ${maxHp}, HP lost per hit ${avg.toFixed(2)} (${(100 * avg / maxHp).toFixed(2)}% of max)`);
    console.log('   ' + (traces[0] || 'no trace'));
  }
  for (const sk of ['Survival', 'Heavy_Armor_Training', 'First_Aid']) await cmd(`/cerb skill ${NAME} ${sk} level 1`, 500);
  await cmd(`/cerb heal ${NAME}`, 500);
  await cmd('/gamemode creative');
  return out;
}

async function magic() {
  await cmd('/gamemode creative');
  await cmd('/time set day');
  await cmd('/clear @s blaze_rod', 400);
  await cmd('/givetestitem fireball', 900);
  const wandItem = bot.inventory.items().find(it => it.name === 'blaze_rod');
  if (wandItem) await bot.equip(wandItem, 'hand'); // works even when the hotbar is full
  await sleep(400);
  const out = {};
  // Three more skills feed Essence (attributes.yml): hold them at 1, so this measures Intelligence and Mana Regeneration
  // alone (ability tests on the same character raise Spell Weaving and Elemental Mastery with every Fireball).
  for (const sk of ['Spell_Weaving', 'Elemental_Mastery', 'Arcane_Knowledge']) await cmd(`/cerb skill ${NAME} ${sk} level 1`, 400);
  for (const lv of [1, 100]) {
    for (const sk of ['Intelligence', 'Mana_Regeneration']) await cmd(`/cerb skill ${NAME} ${sk} level ${lv}`, 500);
    await sleep(800);
    await cmd('/stats', 1500);
    const lore = slotLore(33);
    if (bot.currentWindow) bot.closeWindow(bot.currentWindow);
    await cmd('/kill @e[type=zombie,distance=..16]', 600);
    await cmd('/cerb spawn zombie 50 noai', 1200);
    await cmd('/tp @s ~2.5 ~ ~', 800);
    const z = mobsNear('zombie', 8)[0];
    await cmd('/cerb combatdebug', 500);
    const at = chat.length;
    const held = bot.heldItem;
    if (!held || held.name !== 'blaze_rod') { const w = bot.inventory.items().find(it => it.name === 'blaze_rod'); if (w) await bot.equip(w, 'hand'); }
    if (z && bot.heldItem && bot.heldItem.name === 'blaze_rod') {
      await bot.lookAt(z.position.offset(0, z.height * 0.5, 0), true);
      await sleep(300);
      bot.activateItem(); await sleep(150); bot.deactivateItem();
      const t0 = Date.now();
      while (Date.now() - t0 < 5000 && !chat.slice(at).some(m => /\[Combat\] Spell/.test(m))) await sleep(200);
    }
    await cmd('/cerb combatdebug', 500);
    await cmd('/kill @e[type=zombie,distance=..16]', 600);
    const trace = chat.slice(at).find(m => /\[Combat\] Spell/.test(m)) || '';
    out[lv] = { lore, trace, mult: Number((trace.match(/Essence x([\d.]+)/) || [])[1]),
      maxMana: Number((lore.match(/Max mana: ([\d,]+)/) || [])[1]?.replace(/,/g, "")) };
    console.log(`Intelligence/Mana Regeneration ${lv}: ${lore.split(' | ').slice(1, 4).join(' / ')}`);
    console.log('   ' + (trace || 'no spell trace'));
  }
  for (const sk of ['Intelligence', 'Mana_Regeneration']) await cmd(`/cerb skill ${NAME} ${sk} level 1`, 500);
  return out;
}

async function statsValues() {
  await equip('netherite_sword');
  await cmd('/stats', 1500);
  const v = { power: (slotLore(10).match(/Swords: ([\d.]+)/) || [])[1], health: (slotLore(31).match(/Max health: ([\d,]+)/) || [])[1],
              mana: (slotLore(33).match(/Max mana: ([\d,]+)/) || [])[1], crit: (slotLore(11).match(/Crit chance: ([\d.]+%)/) || [])[1] };
  if (bot.currentWindow) bot.closeWindow(bot.currentWindow);
  return v;
}

async function persist() {
  const file = require('path').join(__dirname, '.persist.json');
  const fs = require('fs');
  if (process.env.PERSIST === 'set') {
    await cmd('/gamemode creative');
    for (const [sk, lv] of [['Blade_Mastery', 50], ['Survival', 100], ['Intelligence', 100], ['Critical_Strike', 40]]) await cmd(`/cerb skill ${NAME} ${sk} level ${lv}`, 500);
    await sleep(1000);
    const v = await statsValues();
    fs.writeFileSync(file, JSON.stringify(v));
    console.log('recorded ' + JSON.stringify(v));
    check('Recorded values after setting levels', !!v.power && !!v.health && !!v.mana, JSON.stringify(v));
  } else {
    const before = JSON.parse(fs.readFileSync(file, 'utf8'));
    const after = await statsValues();
    console.log('after restart ' + JSON.stringify(after));
    check('Attributes and derived stats are the same after a server restart', JSON.stringify(before) === JSON.stringify(after),
        JSON.stringify(before) + ' vs ' + JSON.stringify(after));
    for (const sk of ['Blade_Mastery', 'Survival', 'Intelligence', 'Critical_Strike']) await cmd(`/cerb skill ${NAME} ${sk} level 1`, 500);
    fs.unlinkSync(file);
  }
}

async function rebuilds() {
  await cmd('/cerb panel', 1500);
  const tile = Object.keys(bot.currentWindow ? bot.currentWindow.slots : {}).find(i => /^Attributes$/.test(slotName(+i)));
  await click(+tile, 900); await click(10, 900);
  const n = Number(((slotLore(40).match(/Rebuilds since start: ([\d,]+)/) || [])[1] || 'NaN').replace(/,/g, ''));
  if (bot.currentWindow) bot.closeWindow(bot.currentWindow);
  await sleep(300);
  return n;
}

async function perf() {
  await cmd('/gamemode creative');
  await equip('netherite_sword');
  await cmd(`/cerb skill ${NAME} Blade_Mastery level 1`);
  await cmd(`/cerb skill ${NAME} Critical_Strike level 99`); // max-ish: per-hit XP can't level it during the test
  await sleep(500);
  const before = await rebuilds();
  bot.setQuickBarSlot(heldSlot);
  const r = await killOne('zombie', 50);
  const after = await rebuilds();
  console.log(`hits ${r.swings}, rebuilds ${before} -> ${after}`);
  check("Hitting mobs does not rebuild attributes (only the kill's level-up may)", r.swings >= 8 && after - before <= 1, `${r.swings} hits, ${after - before} rebuilds`);
  await cmd(`/cerb skill ${NAME} Critical_Strike level 1`);
}

async function setTestRealm(slot) {
  await cmd('/cerb panel', 1500);
  const tile = Object.keys(bot.currentWindow ? bot.currentWindow.slots : {}).find(i => /^Attributes$/.test(slotName(+i)));
  await click(+tile, 900); await click(10, 900); await click(35, 900);
  const ok = /Test realm/.test(title());
  await click(slot, 1200);
  if (bot.currentWindow) bot.closeWindow(bot.currentWindow);
  await sleep(1200); // effects re-apply on the player's thread
  return ok;
}

async function statsHeaderAndHealth() {
  await cmd('/stats', 1500);
  const v = { header: slotLore(4), health: (slotLore(31).match(/Max health: ([\d,.]+[KMBT]?)/) || [])[1] };
  if (bot.currentWindow) bot.closeWindow(bot.currentWindow);
  return v;
}

async function realm() {
  await cmd('/gamemode creative');
  await cmd('/time set night');
  await equip('netherite_sword');
  for (const sk of ['Blade_Mastery', 'Critical_Strike', 'Survival', 'Heavy_Armor_Training', 'First_Aid']) await cmd(`/cerb skill ${NAME} ${sk} level 1`, 400);
  await sleep(800);
  let v = await statsHeaderAndHealth();
  check('Default realm is Awakened at x1', /R2 Awakened/.test(v.header) && /Realm magnitude: x1\.00/.test(v.header), v.header);
  bot.setQuickBarSlot(heldSlot);
  const base = (await killOne('zombie', 1)).swings, base2 = (await killOne('zombie', 1)).swings;

  check('Admin can open Test realm and pick Champion', await setTestRealm(22));
  v = await statsHeaderAndHealth();
  check('Champion: realm x64 shown, max health 102 -> 6,528', /R4 Champion/.test(v.header) && /x64\.00/.test(v.header) && v.health === '6,528',
      v.header.slice(0, 120) + ' | ' + v.health);
  bot.setQuickBarSlot(heldSlot);
  const elite = (await killOne('elite_zombie', 1, 'zombie')).swings, elite2 = (await killOne('elite_zombie', 1, 'zombie')).swings;
  console.log(`Awakened vs common zombie L1: ${base}, ${base2} swings; Champion vs Champion elite L1 (x64 HP): ${elite}, ${elite2} swings`);
  check('Same-realm fights stay even across realms (Champion vs Rare elite ~ Awakened vs common)',
      Math.abs((elite + elite2) / 2 - (base + base2) / 2) <= 2, `${(base + base2) / 2} vs ${(elite + elite2) / 2} swings`);

  check('Primordial picked', await setTestRealm(31));
  v = await statsHeaderAndHealth();
  check('Primordial: x16.8M magnitude, max health in billions, shown compactly', /R10 Primordial/.test(v.header) && v.health === '1.7B', v.health);
  await sleep(2500);
  check('HUD shows billions compactly, no NaN; the defense bar kept its fill', /1\.7B\/1\.7B♥/.test(actionBar) && /5\.1B\/5\.1B⛨/.test(actionBar) && !/NaN|\?/.test(actionBar), actionBar);
  await cmd('/cerb combatdebug', 500);
  bot.setQuickBarSlot(heldSlot);
  const at = chat.length;
  const one = await killOne('zombie', 50, 'zombie', 650, 'Blade_Mastery');
  await cmd('/cerb combatdebug', 500);
  const trace = chat.slice(at).find(m => /\[Combat\] Realm/.test(m)) || '';
  // The harness's first swing often doesn't register (same +1 seen in every swing count), so allow 2.
  check('A Primordial hit kills a level-50 zombie at once; the trace shows the realm step', one.swings <= 2 && /Realm\(Primordial x16\.8M\)/.test(trace), one.swings + ' | ' + trace);
  check('Kill XP is unchanged by realm (Common L50 = 334)', one.xp === 334, String(one.msg));

  check('Back to default', await setTestRealm(40));
  v = await statsHeaderAndHealth();
  check('Default restored: Awakened, max health 102', /R2 Awakened/.test(v.header) && v.health === '102', v.health);
}

/** Server-side attribute value (/attribute works on Folia; mineflayer mislabels 1.21.11 attributes). */
async function attr(name) {
  const at = await cmd(`/attribute @s minecraft:${name} get`, 800);
  const m = chat.slice(at).map(x => x.match(/The value of attribute .+ for entity \S+ is ([-\d.E]+)/)).find(Boolean);
  return m ? Number(m[1]) : NaN;
}
async function hasModifier(name, key) {
  const at = await cmd(`/attribute @s minecraft:${name} modifier value get ${key}`, 800);
  return chat.slice(at).some(x => /The value of modifier/.test(x));
}

async function speed() {
  const out = {};
  await cmd('/gamemode survival', 500);
  await cmd('/time set day', 300);
  await cmd('/kill @e[type=zombie,distance=..30]', 500);
  for (const s of ['Blade_Mastery', 'Weapon_Mastery', 'Ranged_Precision', 'Navigation', 'Spell_Weaving']) await cmd(`/cerb skill ${NAME} ${s} level 1`, 300);

  // 1. Attack speed: the server's attack-speed value with a sword in hand, by Blade Mastery level
  check('Got a netherite sword', await equip('netherite_sword'));
  const swords = [];
  for (const bm of [1, 50, 100]) {
    await cmd(`/cerb skill ${NAME} Blade_Mastery level ${bm}`, 1200);
    const v = await attr('attack_speed');
    swords.push({ bm, v, ticks: 20 / v });
    console.log(`  Blade Mastery ${bm}: ${v.toFixed(3)} attacks/s = a full-strength swing every ${(20 / v).toFixed(2)} ticks (${(1 / v).toFixed(3)} s)`);
  }
  out.swords = swords;
  check('Sword interval shortens with Blade Mastery (12.5 -> 11.2 -> 10.6 ticks)', Math.abs(swords[0].ticks - 12.5) < 0.1
      && Math.abs(swords[1].ticks - 11.2) < 0.1 && Math.abs(swords[2].ticks - 10.6) < 0.1, swords.map(r => r.ticks.toFixed(2)).join(' / '));
  check('Never faster than the 10-tick hit window', swords.every(r => r.ticks >= 10));

  check('Got an iron axe', await equip('iron_axe'));
  await sleep(500);
  const axe1 = await attr('attack_speed');
  await cmd(`/cerb skill ${NAME} Weapon_Mastery level 100`, 1200);
  const axe100 = await attr('attack_speed');
  console.log(`  Iron axe (Blade Mastery 100): Weapon Mastery 1 ${axe1.toFixed(3)}/s, Weapon Mastery 100 ${axe100.toFixed(3)}/s`);
  check('Blade Mastery does not speed up axes; Weapon Mastery does', axe1 < 0.92 && axe100 > axe1 * 1.15, axe1.toFixed(3) + ' -> ' + axe100.toFixed(3));
  await cmd(`/cerb skill ${NAME} Weapon_Mastery level 1`, 400);
  out.axe = { axe1, axe100 };

  // 2. The same thing as damage: swing every 0.55 s at a sturdy mob; vanilla scales a hit by how charged it is
  await equip('netherite_sword');
  const rhythm = {};
  for (const bm of [1, 100]) {
    await cmd(`/cerb skill ${NAME} Blade_Mastery level ${bm}`, 1000);
    await cmd('/kill @e[type=zombie,distance=..30]', 500);
    await cmd('/cerb spawn elite_zombie 1 noai', 1500); // a RARE pool (3.2K): survives ten hits at Blade Mastery 100
    const z = mobsNear('zombie', 6)[0];
    await cmd('/cerb combatdebug', 400);
    const at = chat.length;
    if (z) {
      await bot.lookAt(z.position.offset(0, z.height * 0.6, 0), true);
      for (let i = 0; i < 10; i++) { bot.attack(bot.entities[z.id] || z); await sleep(550); }
    }
    await sleep(600);
    await cmd('/cerb combatdebug', 400);
    const raws = chat.slice(at).map(m => m.match(/Power\([^,]+, Power ([\d.]+) -> x([\d.]+)\): ([\d.]+)/)).filter(Boolean)
        .map(m => Number(m[3]) / Number(m[2])).slice(1); // the first swing starts fully charged
    const avg = raws.reduce((a, b) => a + b, 0) / Math.max(1, raws.length);
    rhythm[bm] = { avg, n: raws.length, raws };
    console.log(`  Blade Mastery ${bm}, a swing every 0.55 s: ${raws.length} hits, vanilla damage per hit ${raws.map(x => x.toFixed(1)).join(' ')} (avg ${avg.toFixed(2)} of 8)`);
  }
  out.rhythm = rhythm;
  check('At a 0.55 s rhythm Blade Mastery 100 hits are fully charged, Blade Mastery 1 hits are not', rhythm[1].n >= 5 && rhythm[100].n >= 5
      && rhythm[100].avg > rhythm[1].avg + 0.4 && rhythm[100].avg > 7.6, `${rhythm[1].avg.toFixed(2)} vs ${rhythm[100].avg.toFixed(2)}`);
  await cmd('/kill @e[type=zombie,distance=..30]', 500);
  await cmd(`/cerb skill ${NAME} Blade_Mastery level 1`, 400);

  // 3. Bow draw: power and critical arrows by draw time, Ranged Precision 1 / 50 / 100
  await cmd('/clear @s bow', 300); await cmd('/clear @s arrow', 300);
  await cmd('/give @s bow', 500); await cmd('/give @s arrow 32', 700);
  const bow = bot.inventory.items().find(it => it.name === 'bow');
  if (bow) await bot.equip(bow, 'hand');
  await bot.look(bot.entity.yaw, 0.15, true);
  await cmd('/cerb combatdebug', 400);
  const draws = [];
  for (const rp of [1, 50, 100]) {
    await cmd(`/cerb skill ${NAME} Ranged_Precision level ${rp}`, 1000);
    for (const hold of [600, 850]) {
      const at = chat.length;
      // The critical flag (entity data 8, bit 1) is cleared when the arrow lands: read the first packets.
      let crit = false;
      const onPacket = (data, meta) => {
        if (meta.name !== 'entity_metadata' || !bot.entities[data.entityId] || bot.entities[data.entityId].name !== 'arrow') return;
        for (const m of data.metadata || []) if (m.key === 8 && (m.value & 1)) crit = true;
      };
      bot._client.on('packet', onPacket);
      bot.activateItem(); await sleep(hold); bot.deactivateItem();
      await sleep(900);
      bot._client.removeListener('packet', onPacket);
      const line = chat.slice(at).find(m => /^\[Draw\]/.test(m)) || '';
      const m = line.match(/drew ([\d.]+) s: power ([\d.]+) -> ([\d.]+)/);
      draws.push({ rp, hold, drew: m ? Number(m[1]) : NaN, before: m ? Number(m[2]) : NaN, after: m ? Number(m[3]) : NaN, crit });
      console.log(`  Ranged Precision ${rp}, held ${hold} ms: ${line || 'no [Draw] line'}  | arrow critical: ${crit}`);
    }
  }
  await cmd('/cerb combatdebug', 400);
  await cmd('/kill @e[type=arrow,distance=..60]', 400);
  out.draws = draws;
  const d = (rp, hold) => draws.find(x => x.rp === rp && x.hold === hold) || {};
  check('Ranged Precision 100 turns a ~0.6 s draw into ~0.7 power (vanilla ~0.5)', d(100, 600).after > d(100, 600).before + 0.12 && d(1, 600).after - d(1, 600).before < 0.02,
      `RP1 ${d(1, 600).before}->${d(1, 600).after}, RP100 ${d(100, 600).before}->${d(100, 600).after}`);
  check('Ranged Precision 100 shoots a critical arrow from a ~0.85 s draw; Ranged Precision 1 does not', d(100, 850).crit === true && d(1, 850).crit === false,
      `RP1 ${d(1, 850).crit}, RP100 ${d(100, 850).crit}`);
  await cmd(`/cerb skill ${NAME} Ranged_Precision level 1`, 400);
  await cmd('/clear @s bow', 300); await cmd('/clear @s arrow', 300);

  // 4. Movement speed (server attribute; base 0.1)
  const mv = {};
  for (const pf of [1, 100]) {
    await cmd(`/cerb skill ${NAME} Navigation level ${pf}`, 1200);
    mv[pf] = await attr('movement_speed');
    console.log(`  Pathfinding ${pf}: movement speed ${mv[pf].toFixed(5)} (+${((mv[pf] / 0.1 - 1) * 100).toFixed(1)}%)`);
  }
  out.move = mv;
  check('Pathfinding raises movement speed, within the +20% cap', mv[100] > mv[1] * 1.05 && mv[100] <= 0.1 * 1.2 + 1e-9, `${mv[1]} -> ${mv[100]}`);
  await cmd(`/cerb skill ${NAME} Navigation level 1`, 400);

  // 5. Spell cooldown: Fireball every N ms while clicking constantly, Spell Weaving 1 / 100
  await cmd('/clear @s blaze_rod', 300);
  await cmd('/givetestitem fireball', 800);
  const wand = bot.inventory.items().find(it => it.name === 'blaze_rod');
  if (wand) await bot.equip(wand, 'hand');
  await bot.lookAt(bot.entity.position.offset(0.2, 60, 0.2), true);
  const cd = {};
  for (const sw of [1, 100]) {
    await cmd(`/cerb skill ${NAME} Spell_Weaving level ${sw}`, 1200);
    await cmd(`/cerb mana ${NAME}`, 600);
    // The Fireball is drawn with particles (no entity); each successful cast says so in chat.
    const times = [];
    const onChat = (m, pos) => { if (pos !== 'game_info' && /You cast Fireball!/.test(m)) times.push(Date.now()); };
    bot.on('messagestr', onChat);
    const t0 = Date.now();
    while (Date.now() - t0 < 4200) { bot.activateItem(); await sleep(40); bot.deactivateItem(); await sleep(40); }
    await sleep(500);
    bot.removeListener('messagestr', onChat);
    const gaps = times.slice(1).map((t, i) => t - times[i]);
    const avg = gaps.reduce((a, b) => a + b, 0) / Math.max(1, gaps.length);
    cd[sw] = { casts: times.length, avg };
    console.log(`  Spell Weaving ${sw}: ${times.length} Fireballs in 4.2 s, every ${avg.toFixed(0)} ms on average`);
  }
  out.cooldown = cd;
  check('Spell Weaving shortens the Fireball cooldown', cd[1].casts >= 3 && cd[100].avg < cd[1].avg - 120, `${cd[1].avg.toFixed(0)} ms -> ${cd[100].avg.toFixed(0)} ms`);
  await cmd(`/cerb skill ${NAME} Spell_Weaving level 1`, 400);
  await cmd('/clear @s blaze_rod', 300);

  // 6. PvP: a hit from another player pauses the bonuses for the tag, then they come back
  await equip('netherite_sword');
  await cmd(`/cerb skill ${NAME} Blade_Mastery level 100`, 1200);
  check('Before PvP: the attack-speed bonus is on', await hasModifier('attack_speed', 'cerberus:attack_speed'));
  const other = mineflayer.createBot({ host: '127.0.0.1', port: 25565, username: 'CerbTarget', version: '1.21.11', auth: 'offline' });
  other.on('messagestr', () => {});
  await new Promise(r => other.once('spawn', r));
  await sleep(2500);
  other.chat('/gamemode survival'); await sleep(400);
  await cmd('/tp CerbTarget ' + NAME, 1000);
  const me = Object.values(other.entities).find(e => e.type === 'player' && e.username === NAME);
  if (me) { await other.lookAt(me.position.offset(0, 1.5, 0), true); other.attack(me); }
  await sleep(1200);
  const paused = !(await hasModifier('attack_speed', 'cerberus:attack_speed')) && !(await hasModifier('movement_speed', 'cerberus:movement_speed'));
  check('Hit by a player: attack and movement bonuses pause (PvP stays vanilla)', paused);
  await sleep(15500);
  check('After the 15 s tag: the bonuses are back', await hasModifier('attack_speed', 'cerberus:attack_speed'));
  other.quit();
  await cmd(`/cerb skill ${NAME} Blade_Mastery level 1`, 400);
  await cmd(`/cerb heal ${NAME}`, 400);
  await cmd('/gamemode creative', 400);
  return out;
}

// ── Defense (resistance, armor, the 75% cap, the defense bar) ─────
const DEF_SKILLS = ['Survival', 'Heavy_Armor_Training', 'Defensive_Magic'];
const PIECES = ['helmet', 'chestplate', 'leggings', 'boots'];
const near = (a, b, tol = 0.006) => Math.abs(a - b) <= tol;
const pct = v => (100 * v).toFixed(1) + '%';

async function debugOn(on) {
  for (let i = 0; i < 2; i++) {
    const at = await cmd('/cerb combatdebug', 700);
    const m = saw(/Combat debug: (ON|OFF)/, at);
    if (m && /ON/.test(m) === on) return true;
  }
  return false;
}

/**
 * One exact hit (/damage) with full health and a full bar; the numbers from its trace. `by` names the
 * attacking player (a PvP hit); the trace goes to whichever of the two has combat debug on.
 */
async function hitTrace(type, amount = 100, target = NAME, by = '') {
  await cmd(`/cerb heal ${target}`, 900);
  const at = chat.length;
  await cmd(`/damage ${target} ${amount} ${type}${by ? ' by ' + by : ''}`, 1300);
  return parseTrace(chat.slice(at).find(m => /\[Combat\].*Final/.test(m)) || '');
}

/** Steps of a "[Combat] Incoming: 100.0 -> Armor(40% reduction): 60.0 -> ..." trace line. */
function parseTrace(t) {
  const steps = [...t.matchAll(/(\w+)(?:\(([^)]*)\))?: (-?[\d.]+)/g)].map(m => ({ step: m[1], note: m[2] || '', v: Number(m[3]) }));
  const at_ = name => steps.findIndex(x => x.step === name);
  const value = name => { const i = at_(name); return i >= 0 ? steps[i].v : NaN; };
  const iBar = at_('DefenseBar'), iArmor = at_('Armor'), iResist = at_('ElemResist'), iShare = at_('HealthShare');
  const r = {
    trace: t, incoming: value('Incoming'), final: value('Final'), capped: at_('ReductionCap') >= 0,
    pvpAsBefore: at_('PvP') >= 0,
    beforeBar: iBar > 0 ? steps[iBar - 1].v : NaN,
    afterBar: iBar > 0 ? steps[iBar].v : NaN,
    armor: iArmor > 0 ? 1 - steps[iArmor].v / steps[iArmor - 1].v : 0,
    resist: iResist > 0 ? 1 - steps[iResist].v / steps[iResist - 1].v : 0,
    healthShare: iShare > 0 ? Number((steps[iShare].note.match(/x([\d.]+)/) || [])[1]) : 1,
  };
  r.through = r.beforeBar / r.incoming;              // share of the hit left after resistance and armor
  r.barShare = (r.beforeBar - r.afterBar) / r.beforeBar; // share of that the bar took
  return r;
}

/** Put a netherite piece on the everyday way: hold it and right-click (no inventory screen). */
async function rightClickOn(piece) {
  await equip('netherite_' + piece);
  await bot.look(bot.entity.yaw, Math.PI / 2, true);
  bot.activateItem(); await sleep(250); bot.deactivateItem(); await sleep(700);
}

async function defenseSetup(full) {
  await cmd('/gamemode survival'); await cmd('/difficulty normal'); await cmd('/time set day');
  await cmd('/kill @e[type=!player,type=!text_display,distance=..48]', 800); // a stray mob hit would be read as the test hit
  for (const p of PIECES) await cmd('/clear @s netherite_' + p, 300);
  for (const sk of DEF_SKILLS) await cmd(`/cerb skill ${NAME} ${sk} level ${full ? 100 : 1}`, 500);
  if (full) for (const p of PIECES) await rightClickOn(p);
  await sleep(800);
  if (!(await debugOn(true))) console.log('  (could not turn combat debug on)');
}

async function defenseCleanup() {
  await debugOn(false);
  for (const p of PIECES) await cmd('/clear @s netherite_' + p, 300);
  for (const sk of DEF_SKILLS) await cmd(`/cerb skill ${NAME} ${sk} level 1`, 500);
  await cmd(`/cerb heal ${NAME}`, 500);
  await cmd('/gamemode creative');
}

async function defense() {
  await defenseSetup(false);
  const bare = await hitTrace('minecraft:generic');
  await rightClickOn('chestplate');
  const chest = await hitTrace('minecraft:generic');
  check('Armor put on by right-click counts at once', chest.armor > bare.armor + 0.05, `${pct(bare.armor)} -> ${pct(chest.armor)}`);
  await cmd('/clear @s netherite_chestplate', 900);
  const off = await hitTrace('minecraft:generic');
  check('Armor taken off stops counting at once', near(off.armor, bare.armor), `${pct(chest.armor)} -> ${pct(off.armor)}`);

  await defenseSetup(true); // full netherite, Survival / Heavy Armor / Defensive Magic 100: the strongest defense today
  const fire = await hitTrace('minecraft:in_fire');
  const plain = await hitTrace('minecraft:generic');
  console.log('  fire:  ' + fire.trace);
  console.log('  plain: ' + plain.trace);
  check('Armor tops out at 40%', near(plain.armor, 0.40), pct(plain.armor));
  check('Non-elemental hit: armor alone lets 60% through', near(plain.through, 0.60), pct(plain.through));
  check('Fire hit: 50% resistance x 40% armor lets 30% through (70% removed, under the 75% cap)', near(fire.through, 0.30), pct(fire.through));
  check('No cap step in today\'s strongest defense', !fire.capped && !plain.capped);
  check('A full defense bar takes 70% of what gets through', near(fire.barShare, 0.70, 0.01) && near(plain.barShare, 0.70, 0.01),
      `${pct(fire.barShare)} / ${pct(plain.barShare)}`);
  await defenseCleanup();
}

async function pvp() {
  const T = 'CerbTarget';
  const SK = ['Survival', 'Heavy_Armor_Training', 'First_Aid', 'Defensive_Magic'];
  const other = mineflayer.createBot({ host: '127.0.0.1', port: 25565, username: T, version: '1.21.11', auth: 'offline' });
  other.on('messagestr', () => {});
  await new Promise(r => other.once('spawn', r));
  await sleep(2500);
  await cmd(`/gamemode survival ${T}`, 500); await cmd('/gamemode survival', 500); await cmd('/difficulty normal', 400);
  // Daylight and no mobs around: a zombie's hits would skew the numbers, and Fireball explodes on the first mob it meets.
  const clearMobs = () => cmd('/kill @e[type=!player,type=!text_display,distance=..48]', 800);
  await cmd('/time set day', 400); await clearMobs();
  // Both on a cleared pad: spawn points vary, and a bot that lands in a hillside suffocates (its hits get skipped).
  const sp = bot.spawnPoint || bot.entity.position;
  const PX = Math.floor(sp.x) + 8, PY = Math.floor(sp.y) + 1, PZ = Math.floor(sp.z);
  await cmd(`/fill ${PX - 2} ${PY} ${PZ - 2} ${PX + 3} ${PY + 4} ${PZ + 6} air`, 600);
  await cmd(`/fill ${PX - 2} ${PY - 1} ${PZ - 2} ${PX + 3} ${PY - 1} ${PZ + 6} stone`, 600);
  await cmd(`/tp @s ${PX + 0.5} ${PY} ${PZ + 0.5} 0 0`, 1000);
  await cmd(`/tp ${T} ${PX + 0.5} ${PY} ${PZ + 3.5}`, 1000);
  // No armor on either: what Fortitude adds to armor (+20% at 200 Fortitude) is then plain to see.
  for (const p of PIECES) { await cmd(`/clear ${T} netherite_${p}`, 250); await cmd(`/clear @s netherite_${p}`, 250); }
  for (const who of [T, NAME]) for (const sk of SK) await cmd(`/cerb skill ${who} ${sk} level 100`, 450);
  await sleep(1000);
  if (!(await debugOn(true))) console.log('  (could not turn combat debug on)');
  const at = await cmd(`/cerb healthstate ${T}`, 900);
  const hpLine = saw(/Custom HP: [\d.]+\/([\d.]+)/, at);
  const maxHp = hpLine ? Number(hpLine.match(/\/([\d.]+)/)[1]) : NaN;

  // The same defender build hit by the world (this bot, PvE) and by a player (CerbTarget, PvP).
  const pveHit = await hitTrace('minecraft:generic', 100);
  const pveFire = await hitTrace('minecraft:in_fire', 100);
  // This bot (the attacker, same build: 300 health) is hurt first, so its lifesteal from the PvP hit shows.
  const hp = async who => { const a = await cmd(`/cerb healthstate ${who}`, 900); const m = saw(/Custom HP: ([\d.]+)\/([\d.]+)/, a); return m ? Number(m.match(/Custom HP: ([\d.]+)/)[1]) : NaN; };
  await cmd(`/damage ${NAME} 60 minecraft:generic`, 1200);
  const hp0 = await hp(NAME);
  const pvpHit = await hitTrace('minecraft:player_attack', 20, T, NAME);
  const hp1 = await hp(NAME);
  const lifesteal = Number((pvpHit.trace.match(/Lifesteal: ([\d.]+)/) || [])[1]);
  // A fire spell from a player (/damage "by" a player arrives as a plain melee hit): Fireball at the defender.
  await cmd(`/tp @s ${PX + 0.5} ${PY} ${PZ + 0.5} 0 0`, 1000);
  await cmd(`/tp ${T} ${PX + 0.5} ${PY} ${PZ + 3.5}`, 1000);
  await clearMobs();
  await cmd('/clear @s blaze_rod', 300);
  await cmd('/givetestitem fireball', 900);
  const wand = bot.inventory.items().find(it => it.name === 'blaze_rod');
  if (wand) await bot.equip(wand, 'hand');
  await cmd(`/cerb heal ${T}`, 900);
  let pvpFire = parseTrace('');
  const tgt = Object.values(bot.entities).find(e => e.type === 'player' && e.username === T);
  if (tgt && bot.heldItem && bot.heldItem.name === 'blaze_rod') {
    const at2 = chat.length;
    await bot.lookAt(tgt.position.offset(0, 1.0, 0), true); await sleep(300);
    bot.activateItem(); await sleep(150); bot.deactivateItem();
    const t0 = Date.now();
    while (Date.now() - t0 < 5000 && !chat.slice(at2).some(m => /\[Combat\].*Final/.test(m))) await sleep(200);
    pvpFire = parseTrace(chat.slice(at2).find(m => /\[Combat\].*Final/.test(m)) || '');
  }
  await cmd('/clear @s blaze_rod', 300);

  // A burn tick carries no attacker: during a PvP fight (tagged) it is PvP too, afterwards it isn't.
  await cmd(`/tp ${T} ${NAME}`, 900);
  const me = Object.values(other.entities).find(e => e.type === 'player' && e.username === NAME);
  if (me) { await other.lookAt(me.position.offset(0, 1.5, 0), true); other.attack(me); }
  await sleep(1200);
  await cmd(`/cerb heal ${NAME}`, 900);
  let at3 = chat.length;
  await cmd(`/damage ${NAME} 4 minecraft:on_fire`, 1300);
  const burnTagged = parseTrace(chat.slice(at3).find(m => /\[Combat\].*Final/.test(m)) || '');
  await sleep(16000); // the 15 s PvP tag runs out
  await cmd(`/cerb heal ${NAME}`, 900);
  at3 = chat.length;
  await cmd(`/damage ${NAME} 4 minecraft:on_fire`, 1300);
  const burnLater = parseTrace(chat.slice(at3).find(m => /\[Combat\].*Final/.test(m)) || '');
  console.log(`  defender max health ${maxHp}`);
  console.log('  PvE hit:  ' + pveHit.trace);
  console.log('  PvP hit:  ' + pvpHit.trace);
  console.log('  PvE fire: ' + pveFire.trace);
  console.log('  PvP fire (Fireball): ' + pvpFire.trace);
  console.log('  burn tick during PvP: ' + burnTagged.trace);
  console.log('  burn tick 16 s later: ' + burnLater.trace);
  check('A PvP hit is worked out as before attributes (trace marker)', pvpHit.pvpAsBefore && pvpFire.pvpAsBefore);
  check('PvE: Fortitude adds to armor (Heavy Armor 15% + Fortitude 20%)', near(pveHit.armor, 0.35, 0.011), pct(pveHit.armor));
  check('PvP: armor without Fortitude (Heavy Armor only, as before)', near(pvpHit.armor, 0.15, 0.011), pct(pvpHit.armor));
  check('PvE: fire resistance 50% (Survival 30% + Fortitude, capped)', near(pveFire.resist, 0.50, 0.011), pct(pveFire.resist));
  check('PvP: fire resistance 30% (Survival only, as before)', near(pvpFire.resist, 0.30, 0.011), pct(pvpFire.resist));
  check('Vitality tripled max health (300)', near(maxHp, 300, 0.5), String(maxHp));
  check('PvP: the hit is scaled to that health (x3.00)', near(pvpHit.healthShare, maxHp / 100, 0.011), 'x' + pvpHit.healthShare);
  // Before attributes: 100 health, Heavy Armor 100's 15% armor, a full bar taking 70%.
  const share = pvpHit.final / maxHp, before = pvpHit.incoming * (1 - 0.15) * (1 - 0.70) / 100;
  check('PvP: the defender loses the share of health a 100-health defender lost before', near(share, before, 0.001),
      `${pct(share)} of ${maxHp} vs ${pct(before)} of 100`);
  const healed = hp1 - hp0;
  check('PvP lifesteal restores the same share of the attacker\'s 300 health as before (x3)', lifesteal > 0 && near(healed, lifesteal * 3, 0.35),
      `healed ${healed.toFixed(2)} for lifesteal ${lifesteal}`);
  check('A burn tick during a PvP fight is PvP (old armor)', burnTagged.pvpAsBefore && near(burnTagged.armor, 0.15, 0.03), burnTagged.trace);
  check('Once the PvP tag ends, burning uses your attributes again', !burnLater.pvpAsBefore && near(burnLater.armor, 0.35, 0.03), burnLater.trace);
  check('PvP: the bar still takes 70% (full bar)', near(pvpHit.barShare, 0.70, 0.01), pct(pvpHit.barShare));

  await debugOn(false);
  for (const who of [T, NAME]) for (const sk of SK) await cmd(`/cerb skill ${who} ${sk} level 1`, 450);
  await cmd(`/cerb heal ${T}`, 400); await cmd(`/cerb heal ${NAME}`, 400);
  other.quit();
  await cmd('/gamemode creative', 400);
}

async function mastery() {
  await cmd('/gamemode creative'); await cmd('/time set day');
  await cmd(`/cerb skill ${NAME} Critical_Strike level 1`, 400);
  const runs = [
    { label: 'sword, Blade Mastery 100 + Weapon Mastery 1  ', item: 'netherite_sword', bm: 100, wm: 1, delay: 700, cls: 'Swords' },
    { label: 'sword, Blade Mastery 100 + Weapon Mastery 100', item: 'netherite_sword', bm: 100, wm: 100, delay: 700, cls: 'Swords' },
    { label: 'axe,   Weapon Mastery 100 alone             ', item: 'netherite_axe', bm: 1, wm: 100, delay: 1150, cls: 'Axes' },
  ];
  for (const r of runs) {
    await equip(r.item);
    await cmd(`/cerb skill ${NAME} Blade_Mastery level ${r.bm}`, 400);
    await cmd(`/cerb skill ${NAME} Weapon_Mastery level ${r.wm}`, 900);
    await debugOn(true);
    const at = chat.length;
    const first = await killOne('zombie', 100, 'zombie', r.delay);
    await debugOn(false);
    const t = chat.slice(at).find(m => new RegExp('Power\\(' + r.cls).test(m)) || '';
    const pm = t.match(/Power\((\w+), Power ([\d.]+) -> x([\d.]+)\)/);
    r.power = pm ? Number(pm[2]) : NaN; r.mult = pm ? Number(pm[3]) : NaN;
    const kills = [first.swings];
    // Five kills each: crits make one kill take a swing more or less, and three samples were too few to
    // show the 21% reliably (7.0 -> 7.0 once; 8.0 -> 5.7 and 7.3 -> 7.0 in the next two runs).
    for (let i = 0; i < 4; i++) kills.push((await killOne('zombie', 100, 'zombie', r.delay)).swings);
    r.swings = kills; r.avg = kills.reduce((a, b) => a + b, 0) / kills.length;
    console.log(`  ${r.label}: Power ${r.power} -> x${r.mult}, swings to kill a level-100 zombie ${kills.join(' / ')} (avg ${r.avg.toFixed(1)})`);
  }
  const [bm, both, axe] = runs;
  check('Weapon Mastery 100 adds 25 Power to a sword at Blade Mastery 100 (x6.01 -> x7.25)', near(bm.mult, 6.01, 0.011) && near(both.mult, 7.25, 0.011), `x${bm.mult} -> x${both.mult}`);
  check('An axe at Weapon Mastery 100 alone has the same x7.25', near(axe.mult, 7.25, 0.011), `x${axe.mult}`);
  check('The extra 21% shows up as fewer swings, not a one-shot', both.avg < bm.avg && Math.min(...both.swings, ...axe.swings) >= 4,
      `${bm.avg.toFixed(1)} -> ${both.avg.toFixed(1)} swings; axe ${axe.avg.toFixed(1)}`);
  await cmd(`/cerb skill ${NAME} Blade_Mastery level 1`, 400);
  await cmd(`/cerb skill ${NAME} Weapon_Mastery level 1`, 400);
  await cmd('/kill @e[type=zombie,distance=..10]', 500);
  for (const w of ['netherite_sword', 'netherite_axe', 'rotten_flesh', 'iron_ingot']) await cmd('/clear @s ' + w, 250);
}

async function defenseCap() {
  await defenseSetup(true);
  const fire = await hitTrace('minecraft:in_fire');
  const plain = await hitTrace('minecraft:generic');
  console.log('  fire:  ' + fire.trace);
  check('Fire resistance x armor would remove 82%; the cap leaves 25% (75% removed)', near(fire.through, 0.25), pct(fire.through));
  check('The trace shows the cap step', fire.capped, fire.trace);
  check('Armor alone (40%) is under the cap: unchanged', near(plain.through, 0.60) && !plain.capped, pct(plain.through));
  check('The bar still takes 70% of what gets through (it is not part of the cap)', near(fire.barShare, 0.70, 0.01), pct(fire.barShare));
  await defenseCleanup();
}

// ── Step 2 decisions (docs/ROADMAP.md) ───────────────────────────

/** A skill's level and XP into it, as the server has them (/cerb skill ... xp 0 adds nothing). */
async function skillXp(skill) {
  const at = await cmd(`/cerb skill ${NAME} ${skill} xp 0`, 900);
  const m = (saw(/is now level \d+ \(\d+ XP\)/, at) || '').match(/level (\d+) \((\d+) XP\)/);
  return m ? { level: Number(m[1]), xp: Number(m[2]) } : { level: NaN, xp: NaN };
}
const grew = (a, b) => b.level > a.level || (b.level === a.level && b.xp > a.xp);
const same = (a, b) => a.level === b.level && a.xp === b.xp;
const xpText = (a, b) => `L${a.level} ${a.xp} XP -> L${b.level} ${b.xp} XP`;

/** The resistance named in a trace's ElemResist step ("ElemResist(HOT_FLOOR 50%)" -> 0.5), 0 if none. */
const resistNote = t => { const m = (t.trace || '').match(/ElemResist\(\w+ ([\d.]+)%\)/); return m ? Number(m[1]) / 100 : 0; };

/**
 * The first combat trace while standing on a real block. The server names campfire and magma damage
 * only when a block caused it: /damage minecraft:campfire or hot_floor has no block and arrives as CUSTOM.
 */
async function blockTrace(kind) {
  const p = bot.entity.position.floored();
  await cmd(`/cerb heal ${NAME}`, 900);
  const at = chat.length;
  if (kind === 'magma') await cmd(`/setblock ${p.x} ${p.y - 1} ${p.z} magma_block`, 200);
  else await cmd(`/setblock ${p.x} ${p.y} ${p.z} campfire[lit=true]`, 200);
  let line = '';
  for (let i = 0; i < 30 && !line; i++) { await sleep(100); line = chat.slice(at).find(m => /\[Combat\].*Final/.test(m)) || ''; }
  if (kind === 'magma') await cmd(`/setblock ${p.x} ${p.y - 1} ${p.z} stone`, 300);
  else await cmd(`/setblock ${p.x} ${p.y} ${p.z} air`, 300);
  await cmd(`/tp @s ${p.x + 0.5} ${p.y} ${p.z + 0.5}`, 600);
  return parseTrace(line);
}

async function fire() {
  await defenseSetup(true); // full netherite + Survival / Heavy Armor / Defensive Magic 100: fire resistance 50%, armor 40%
  // A stone pad at spawn height: the magma check replaces the block underfoot, the campfire check the feet block.
  const sp = bot.spawnPoint || bot.entity.position;
  const X = Math.floor(sp.x) - 8, Y = Math.floor(sp.y) + 1, Z = Math.floor(sp.z) - 8;
  await cmd(`/fill ${X - 2} ${Y} ${Z - 2} ${X + 2} ${Y + 3} ${Z + 2} air`, 500);
  await cmd(`/fill ${X - 2} ${Y - 1} ${Z - 2} ${X + 2} ${Y - 1} ${Z + 2} stone`, 500);
  await cmd(`/tp @s ${X + 0.5} ${Y} ${Z + 0.5}`, 1500);
  const FULL = 0.5;
  const kinds = [
    ['Standing in fire', 1, () => hitTrace('minecraft:in_fire')],
    ['Burning afterwards', 1, () => hitTrace('minecraft:on_fire')],
    ['Campfire (real block)', 1, () => blockTrace('campfire')],
    ['Magma block (real block)', 1, () => blockTrace('magma')],
    ['Lava', 0.5, () => hitTrace('minecraft:lava')],
    ['Not fire (control)', 0, () => hitTrace('minecraft:generic')],
  ];
  const got = [];
  console.log('  Kind of damage            resistance   trace');
  for (const [label, share, run] of kinds) {
    const t = await run();
    got.push(t);
    console.log(`  ${label.padEnd(26)}${pct(resistNote(t)).padEnd(13)}${t.trace.replace(/^.*?Incoming/, 'Incoming') || '(no trace)'}`);
  }
  kinds.forEach(([label, share], i) => check(`${label}: ${pct(FULL * share)} resistance`, !!got[i].trace && near(resistNote(got[i]), FULL * share), pct(resistNote(got[i]))));
  check('Bounded: resistance x armor stays under the 75% cap (no cap step), damage still gets through', got.every(t => !t.capped && t.final > 0));
  await defenseCleanup();
}

async function projectile() {
  let hurts = 0;
  bot.on('entityHurt', e => { if (e === bot.entity) hurts++; });
  await cmd('/difficulty normal', 400); await cmd('/time set day', 400);
  await cmd('/kill @e[type=!player,type=!text_display,distance=..48]', 800);
  // A floating stone pad at spawn height (no fall damage: falling trains Survival too).
  const sp = bot.spawnPoint || bot.entity.position;
  const X = Math.floor(sp.x) - 8, Y = Math.floor(sp.y) + 1, Z = Math.floor(sp.z);
  await cmd(`/fill ${X - 3} ${Y} ${Z - 3} ${X + 3} ${Y + 4} ${Z + 9} air`, 600);
  await cmd(`/fill ${X - 3} ${Y - 1} ${Z - 3} ${X + 3} ${Y - 1} ${Z + 9} stone`, 600);
  await cmd('/gamemode creative', 300);
  await cmd(`/tp @s ${X + 0.5} ${Y} ${Z + 0.5} 0 0`, 1500); // yaw 0 = facing +z
  await rightClickOn('chestplate'); // one heavy piece: Heavy Armor Mastery trains from hits
  await cmd('/gamemode survival', 400);
  await cmd(`/cerb heal ${NAME}`, 600);
  const reset = async () => { for (const s of ['Survival', 'Heavy_Armor_Training']) await cmd(`/cerb skill ${NAME} ${s} level 1`, 400); };
  const read = async () => ({ sv: await skillXp('Survival'), ham: await skillXp('Heavy_Armor_Training') });

  // 1. A skeleton (Power V bow, so each arrow does real damage through the bar) shoots the bot.
  await reset();
  let before = await read();
  hurts = 0;
  await cmd(`/summon skeleton ${X + 0.5} ${Y} ${Z + 7.5} {Rotation:[180f,0f],equipment:{mainhand:{id:"minecraft:bow",count:1,components:{"minecraft:enchantments":{"minecraft:power":5}}}}}`, 500);
  for (let t = 0; t < 30 && hurts < 3; t++) await sleep(500);
  const arrows = hurts;
  await cmd('/kill @e[type=skeleton,distance=..30]', 600);
  await cmd('/kill @e[type=arrow,distance=..30]', 400);
  let after = await read();
  console.log(`  skeleton: ${arrows} arrow hits; Survival ${xpText(before.sv, after.sv)}; Heavy Armor ${xpText(before.ham, after.ham)}`);
  check('A skeleton\'s arrows train Survival', arrows > 0 && grew(before.sv, after.sv), `${arrows} hits, ${xpText(before.sv, after.sv)}`);
  check('... and Heavy Armor Mastery (wearing a netherite chestplate)', arrows > 0 && grew(before.ham, after.ham), xpText(before.ham, after.ham));

  // 2. A dispenser's arrows (no living shooter): nothing.
  await cmd(`/cerb heal ${NAME}`, 600);
  await reset();
  before = await read();
  hurts = 0;
  await cmd(`/setblock ${X} ${Y} ${Z + 3} dispenser[facing=north]{Items:[{Slot:0b,id:"minecraft:arrow",count:16}]}`, 500);
  for (let i = 0; i < 3; i++) {
    await cmd(`/setblock ${X} ${Y + 1} ${Z + 3} redstone_block`, 600);
    await cmd(`/setblock ${X} ${Y + 1} ${Z + 3} air`, 900);
  }
  await sleep(800);
  const dispensed = hurts;
  await cmd(`/setblock ${X} ${Y} ${Z + 3} air`, 300);
  await cmd('/kill @e[type=arrow,distance=..30]', 400);
  after = await read();
  check('A dispenser\'s arrows train nothing', dispensed > 0 && same(before.sv, after.sv) && same(before.ham, after.ham),
      `${dispensed} hits; Survival ${xpText(before.sv, after.sv)}; Heavy Armor ${xpText(before.ham, after.ham)}`);

  // 3. Your own splash potion of harming: nothing.
  await cmd(`/cerb heal ${NAME}`, 600);
  await reset();
  before = await read();
  hurts = 0;
  await equip('splash_potion[potion_contents={potion:"minecraft:harming"}]');
  const potion = bot.inventory.slots.findIndex((it, i) => it && it.name === 'splash_potion' && i >= 36 && i <= 44);
  if (potion >= 0) bot.setQuickBarSlot(potion - 36);
  await bot.look(bot.entity.yaw, -Math.PI / 2, true); await sleep(300);
  bot.activateItem(); await sleep(250); bot.deactivateItem();
  await sleep(1800);
  const own = hurts;
  after = await read();
  check('Your own harming potion trains nothing', own > 0 && same(before.sv, after.sv) && same(before.ham, after.ham),
      `${own} hits; Survival ${xpText(before.sv, after.sv)}; Heavy Armor ${xpText(before.ham, after.ham)}`);
  await cmd('/clear @s splash_potion', 300);

  // 4. Exactly timed arrows: /damage by a summoned arrow whose owner is a no-AI skeleton makes the same event as a
  //    real arrow. Two at once (a volley): the second lands in the invulnerability window and trains nothing. A hit
  //    that does no damage trains nothing either (snowballs, eggs and fishing hooks never even reach plugins:
  //    the server drops 0-damage hits on players).
  await cmd(`/summon skeleton ${X + 0.5} ${Y} ${Z + 6.5} {NoAI:1b}`, 1200);
  const sk = Object.values(bot.entities).find(e => e.name === 'skeleton' && e.uuid);
  const ints = sk ? sk.uuid.replace(/-/g, '').match(/.{8}/g).map(h => parseInt(h, 16) | 0) : [];
  await cmd(`/summon arrow ${X + 0.5} ${Y + 3} ${Z + 2.5} {NoGravity:1b,Owner:[I;${ints.join(',')}]}`, 900);
  const shot = amount => `/damage ${NAME} ${amount} minecraft:arrow by @e[type=arrow,limit=1,sort=nearest] from @e[type=skeleton,limit=1,sort=nearest]`;
  await cmd(`/cerb heal ${NAME}`, 1200);
  await reset();
  before = await read();
  hurts = 0;
  bot.chat(shot(20)); bot.chat(shot(20)); // big enough that Heavy Armor's XP (half the damage taken, whole points) shows
  await sleep(1500);
  const volley = hurts;
  after = await read();
  check('Two arrows at once: only the first trains (the second hits during the invulnerability window)',
      !!sk && after.sv.level === 1 && after.sv.xp - before.sv.xp === 1 && after.ham.xp > before.ham.xp,
      `${volley} hit(s) shown; Survival ${xpText(before.sv, after.sv)}; Heavy Armor ${xpText(before.ham, after.ham)}`);
  before = after;
  await cmd(shot(0), 1500);
  after = await read();
  check('A hit that does no damage trains nothing', same(before.sv, after.sv) && same(before.ham, after.ham),
      `Survival ${xpText(before.sv, after.sv)}; Heavy Armor ${xpText(before.ham, after.ham)}`);
  before = after;
  await cmd(shot(20), 1500);
  after = await read();
  check('Control: the next arrow after the window trains again', after.sv.xp - before.sv.xp === 1, `Survival ${xpText(before.sv, after.sv)}`);

  await cmd('/kill @e[type=!player,type=!text_display,distance=..48]', 600);
  await cmd('/clear @s netherite_chestplate', 400);
  await reset();
  await cmd(`/cerb heal ${NAME}`, 500);
  await cmd('/gamemode creative', 400);
}

/**
 * Jab a no-AI zombie with a spear until it dies (or `max` jabs). A spear doesn't attack like a sword: the client
 * sends a "stab" action (player action 7), then the arm swing, and the server hits whatever is 2 to 4.5 blocks
 * away along the aim line, so the zombie is placed 3 blocks ahead. (A normal attack with a spear does nothing.)
 */
async function stabOne(level, max = 200, delay = 1000) {
  await cmd('/kill @e[type=zombie,distance=..12]', 600);
  await bot.look(bot.entity.yaw, 0, true);
  const at = await cmd(`/cerb spawn zombie ${level} noai`, 1500);
  await cmd('/tp @e[type=zombie,limit=1,sort=nearest] ^ ^ ^3', 900);
  const target = mobsNear('zombie', 6)[0];
  if (!target) return { stabs: 0, xp: NaN, msg: 'mob did not spawn' };
  let stabs = 0;
  for (; stabs < max; stabs++) {
    const m = bot.entities[target.id];
    if (!m || !m.isValid) break;
    await bot.lookAt(m.position.offset(0, m.height * 0.6, 0), true);
    bot._client.write('block_dig', { status: 7, location: { x: 0, y: 0, z: 0 }, face: 0, sequence: 0 });
    bot.swingArm(); // after the stab: a swing first would reset the attack timer and the stab would be refused
    await sleep(delay);
  }
  await sleep(800);
  return { stabs, hud: actionBar };
}

async function spear() {
  await cmd('/gamemode creative'); await cmd('/time set night'); // a zombie in daylight burns: the kill must be the spear's (creative: night mobs ignore the bot)
  await cmd('/kill @e[type=!player,type=!text_display,distance=..20]', 600);
  for (const s of ['Weapon_Mastery', 'Heavy_Armor_Training', 'Blade_Mastery', 'Critical_Strike']) await cmd(`/cerb skill ${NAME} ${s} level 1`, 400);
  // Heavy armor on: a kill with a weapon no skill claimed used to fall back to Heavy Armor Mastery XP.
  await rightClickOn('chestplate');
  check('Got an iron spear', await equip('iron_spear'));
  const wm0 = await skillXp('Weapon_Mastery'), ham0 = await skillXp('Heavy_Armor_Training');
  await debugOn(true);
  const at = chat.length;
  const r = await stabOne(1);
  await debugOn(false);
  const wm1 = await skillXp('Weapon_Mastery'), ham1 = await skillXp('Heavy_Armor_Training');
  console.log(`  spear kill: ${r.stabs} jabs; HUD ${r.hud}; Weapon Mastery ${xpText(wm0, wm1)}; Heavy Armor ${xpText(ham0, ham1)}`);
  check('A spear kill pays Weapon Mastery XP', grew(wm0, wm1), `HUD ${r.hud}; ${xpText(wm0, wm1)}`);
  check('One reward per kill: no Heavy Armor Mastery XP on top', same(ham0, ham1), `Heavy Armor ${xpText(ham0, ham1)}`);
  const t1 = chat.slice(at).find(m => /Power\(Spears/.test(m)) || '';
  check('Spear hits use Weapon Mastery\'s Power', /Power\(Spears, Power [\d.]+ -> x[\d.]+\)/.test(t1), t1.slice(0, 160) || 'no Power(Spears) step');
  // The same classification for the combat bonus: Weapon Mastery 100 gives spears x7.25, as axes.
  await cmd(`/cerb skill ${NAME} Weapon_Mastery level 100`, 900);
  await debugOn(true);
  const at2 = chat.length;
  await stabOne(100, 2);
  await debugOn(false);
  const pm = (chat.slice(at2).find(m => /Power\(Spears/.test(m)) || '').match(/Power\(Spears, Power ([\d.]+) -> x([\d.]+)\)/);
  check('Weapon Mastery 100: spears get Power 125 (x7.25), as axes', !!pm && near(Number(pm[2]), 7.25, 0.011), pm ? `Power ${pm[1]} -> x${pm[2]}` : 'no trace');
  for (const s of ['Weapon_Mastery', 'Heavy_Armor_Training', 'Critical_Strike']) await cmd(`/cerb skill ${NAME} ${s} level 1`, 400);
  await cmd('/kill @e[type=zombie,distance=..12]', 500);
  await cmd('/time set day', 300);
  for (const w of ['iron_spear', 'netherite_chestplate', 'rotten_flesh', 'iron_ingot', 'carrot', 'potato']) await cmd('/clear @s ' + w, 250);
}

// Every phase starts at world spawn: other phases teleport the bot, and test mobs spawned
// inside a hillside suffocate mid-check.
async function goHome() {
  const s = bot.spawnPoint;
  if (s) { bot.chat(`/tp @s ${s.x + 0.5} ${s.y} ${s.z + 0.5}`); await new Promise(r => setTimeout(r, 1200)); }
}

bot.once('spawn', async () => {
  await sleep(2500);
  try {
    await goHome();
    if (phase === 'killxp') await killxp();
    else if (phase === 'stats') await stats();
    else if (phase === 'survive') {
      const r = await survive();
      check('Vitality: defensive skills 1 -> 100 take max health from 102 to 300', r[1].maxHp === 102 && r[100].maxHp === 300, `${r[1].maxHp} -> ${r[100].maxHp}`);
      check('Max health survives switching held items', r[100].afterSwap === 300, String(r[100].afterSwap));
      check('Fortitude shows in the totals (armor and resistances rose)', /Armor bonus: \+15\.[0-9]%/.test(r[100].lore), r[100].lore);
      check('Each zombie hit costs a much smaller share of health at level 100', r[100].pct < r[1].pct / 2.5,
          `${(100 * r[1].pct).toFixed(2)}% -> ${(100 * r[100].pct).toFixed(2)}% per hit`);
    }
    else if (phase === 'magic') {
      const r = await magic();
      check('Essence raises max mana (about x3 at level 100)', r[100].maxMana > 2.5 * r[1].maxMana, `${r[1].maxMana} -> ${r[100].maxMana}`);
      check('Fireball damage on a mob is scaled by Essence', r[1].mult > 1 && r[1].mult < 1.2 && r[100].mult > 4.5, `x${r[1].mult} -> x${r[100].mult}`);
    }
    else if (phase === 'persist') await persist();
    else if (phase === 'perf') await perf();
    else if (phase === 'speed') await speed();
    else if (phase === 'realm') await realm();
    else if (phase === 'defense') await defense();
    else if (phase === 'defense-cap') await defenseCap();
    else if (phase === 'pvp') await pvp();
    else if (phase === 'mastery') await mastery();
    else if (phase === 'fire') await fire();
    else if (phase === 'projectile') await projectile();
    else if (phase === 'spear') await spear();
    else if (phase === 'offswitch') {
      await cmd('/gamemode creative');
      await equip('netherite_sword');
      await cmd(`/cerb skill ${NAME} Blade_Mastery level 100`);
      const r = await killOne('zombie', 1);
      await cmd(`/cerb skill ${NAME} Blade_Mastery level 1`);
      check('PvE offense switched off: Blade Mastery 100 needs the old 6+ swings on a level-1 zombie', r.swings >= 6, r.swings + ' swings');
    }
    else if (phase === 'swings') {
      const { rows, trace } = await swings();
      const at = (bm, lvl) => rows.find(r => r.bm === bm && r.lvl === lvl).swings;
      console.log('\nSwings to kill (avg of 2)   zombie L1   zombie L50');
      for (const bm of [1, 50, 100]) console.log(`  Blade Mastery ${String(bm).padEnd(3)}            ${String(at(bm, 1)).padEnd(10)}  ${at(bm, 50)}`);
      check('The server applied Power to the hit (trace: Swords, Power 50.3 -> x3.51: Blade Mastery 50 + Weapon Mastery 1 x 0.25)', /Power\(Swords, Power 50\.3 -> x3\.51\)/.test(trace), trace);
      check('Blade Mastery 100 kills a level-1 zombie in far fewer swings than Blade Mastery 1', at(100, 1) <= at(1, 1) / 2, `BM1 ${at(1, 1)} vs BM100 ${at(100, 1)}`);
      check('Blade Mastery 100 kills a level-50 zombie in far fewer swings than Blade Mastery 1', at(100, 50) <= at(1, 50) / 3, `BM1 ${at(1, 50)} vs BM100 ${at(100, 50)}`);
      check('Equal-level fights take about the same swings (BM1 vs L1, BM50 vs L50)', Math.abs(at(50, 50) - at(1, 1)) <= 2, `${at(1, 1)} vs ${at(50, 50)}`);
    }
  } catch (e) { console.log('SCRIPT ERROR', e.stack); results.push(false); }
  const failed = results.filter(r => !r).length;
  console.log(`\n${results.length - failed}/${results.length} checks passed`);
  bot.quit(); await sleep(400); process.exit(failed ? 1 : 0);
});
setTimeout(() => { console.log('TIMEOUT'); process.exit(3); }, 600000);
