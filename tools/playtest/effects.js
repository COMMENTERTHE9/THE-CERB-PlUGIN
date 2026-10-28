// Active effects in game (docs/design/ACTIVE_EFFECTS.md). LOCAL SANDBOX servers only; the bot must be op.
// Usage: node effects.js [phase[,phase...]] [botName]
//   none     - nothing running: the hub's head says "No active effects", its screen says so too, and no countdown runs
//   meal     - a Hearty Stew eaten: first on the head with its time, its tile with its source, what it does (Vitality +6 and
//              max health before and after, matching the HUD) and its restoration apart; the times count down while the
//              screen stays open; the restoration shows as not running once its 20 s are over
//   refresh  - the same meal again: its time back to the full ten minutes
//   replace  - a Trail Ration after the stew: only the ration shows, its speed raw and after the cap (the attribute's value)
//   death    - a meal and a potion, then death: neither shows
//   relog    - a meal across a relog: still there with its time (counting on in real time); restoration not running
//   class    - War Cry (a Warrior's +15% damage, 8 s): on the screen with its source and bonus, gone when it ends (the open
//              screen redraws by itself); an equipped passive on its own "Always on" tile, never among the timed effects
//   buff     - an admin dev buff (+100 Vitality, through the Control Center): its tile, max health +100, gone when cleared
//   potion   - vanilla effects: Speed II with its time and walking-speed text, Regeneration said to do nothing to Cerberus
//              health, an infinite Luck "until removed"; one cleared while the screen is open disappears from it
//   vanilla  - the descriptions checked against the game: Fire Resistance stops lava damage, Resistance changes nothing
//   combo    - a meal, War Cry, a dev buff and two potions together: meal first, then ability, buff, potions; the head lists four
//              and "+1 more"
//   nav      - the head opens Active Effects; Back returns to the hub; its Stats button opens Stats and Back returns;
//              Stats & Attributes is still on its hub tile
//   warn     - a two-minute stew written into the sandbox's resources.yml (put back after; needs SERVER_DIR): the heads-up
//              about a minute before the end, once; the stew ending while the screen is open; a stew replaced before its
//              heads-up never warns; a heads-up said before a relog isn't said again, one not yet said comes after the join
//   restart-set / restart-check - a meal and a potion across a clean stop and start with the bot online
//   all      - none, meal, refresh, replace, death, relog, class, buff, potion, vanilla, combo, nav (warn and the restart pair
//              apart: they take minutes or a restart)
const mineflayer = require('mineflayer');
const { Vec3 } = require('vec3');
const fs = require('fs');
const path = require('path');
const phaseArg = process.argv[2] || 'all';
let NAME = process.argv[3] || 'CerbEffects';
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
let bot, pchat;
function connect() {
  const b = mineflayer.createBot({ host: '127.0.0.1', port: 25565, username: NAME, version: '1.21.11', auth: 'offline' });
  b.on('messagestr', (m, pos) => { if (pos !== 'game_info') chat.push(m); });
  b._client.on('action_bar', p => {
    try { pchat = pchat || require('prismarine-chat')(b.registry); bars.push({ t: Date.now(), m: pchat.fromNotch(p.text).toString() }); } catch (e) { /* ignore */ }
  });
  b.on('kicked', r => { console.log('KICKED', r); process.exit(2); });
  bot = b;
  return new Promise(r => b.once('spawn', () => r(b)));
}
async function cmd(c, wait = 600) { const at = chat.length; bot.chat(c); await sleep(wait); return at; }
const saw = (re, since) => chat.slice(since).find(m => re.test(m));
const hudSince = t => [...new Set(bars.filter(b => b.t >= t).map(b => b.m))];
const plain = v => {
  if (v == null) return '';
  if (typeof v === 'string') { try { return plain(JSON.parse(v)); } catch { return v; } }
  try { return require('prismarine-chat')(bot.registry).fromNotch(v).toString(); } catch (e) { return String(v); }
};
const title = () => bot.currentWindow ? plain(bot.currentWindow.title) : '';
const slotName = i => { const it = bot.currentWindow && bot.currentWindow.slots[i]; return it ? plain(it.customName) : ''; };
const slotLore = i => { const it = bot.currentWindow && bot.currentWindow.slots[i]; return it && it.customLore ? it.customLore.map(plain).join(' | ') : ''; };
const findSlot = re => { const w = bot.currentWindow; if (!w) return -1; for (let i = 0; i < w.inventoryStart; i++) if (w.slots[i] && re.test(plain(w.slots[i].customName))) return i; return -1; };
async function waitTitle(re, ms = 4000) { const t0 = Date.now(); while (Date.now() - t0 < ms) { if (bot.currentWindow && re.test(title())) return true; await sleep(100); } return false; }
async function click(i, wait = 700) { try { await bot.clickWindow(i, 0, 0); } catch (e) { /* the server decides */ } await sleep(wait); }
function closeAny() { if (bot.currentWindow) bot.closeWindow(bot.currentWindow); }

/** Health and max health from the HUD: "103/103♥". */
function hud() {
  for (let i = bars.length - 1; i >= 0; i--) {
    const m = bars[i].m.match(/([\d,]+)\/([\d,]+)♥/);
    if (m) return { hp: Number(m[1].replace(/,/g, '')), max: Number(m[2].replace(/,/g, '')) };
  }
  return { hp: NaN, max: NaN };
}
async function attr(name) {
  const at = await cmd(`/attribute @s minecraft:${name} get`, 800);
  const m = chat.slice(at).map(x => x.match(/The value of attribute .+ for entity \S+ is ([-\d.E]+)/)).find(Boolean);
  return m ? Number(m[1]) : NaN;
}
/** "8m 42s" / "42s" / "1h 05m" -> seconds. */
function secs(t) {
  const m = String(t).match(/(?:(\d+)h )?(?:(\d+)m ?)?(?:(\d+)s)?/);
  if (!m) return NaN;
  return (Number(m[1] || 0) * 3600) + (Number(m[2] || 0) * 60) + Number(m[3] || 0);
}
/** The time a tile's name shows: "Hearty Stew — 9m 58s remaining" -> 598. */
const timeOf = name => { const m = String(name).match(/— ((?:\d+h )?(?:\d+m ?)?(?:\d+s)?) remaining/); return m ? secs(m[1]) : NaN; };

// ── the character ─────────────────────────────────────────────────
async function clean() {
  closeAny();
  await cmd('/gamemode survival', 300);
  await cmd(`/cerb cook clear ${NAME}`, 400);
  await cmd('/effect clear @s', 400); // no potion of our own: the screens list every effect (meals are edible at full hunger anyway)
  await cmd('/clear @s', 400);
  await sleep(600);
}
/** Eat `meal` (given by the admin command) with a held right-click, as a player does. True when one was eaten. */
async function eat(meal, tier = 'common') {
  await cmd(`/cerb cook give ${NAME} ${meal} ${tier} 1`, 800);
  const it = bot.inventory.items().find(i => /stew|potato|rabbit|salmon|pie|soup/.test(i.name));
  if (!it) return false;
  await bot.equip(it, 'hand');
  const before = bot.inventory.items().filter(i => i.name === it.name).reduce((a, i) => a + i.count, 0);
  bot.activateItem();
  await sleep(2300);
  bot.deactivateItem();
  await sleep(600);
  const after = bot.inventory.items().filter(i => i.name === it.name).reduce((a, i) => a + i.count, 0);
  return after === before - 1;
}
/** Open the hub; returns its head's lore. */
async function hub() {
  closeAny(); await sleep(200);
  bot.chat('/menu');
  await waitTitle(/Player Menu/);
  await sleep(400);
  return slotLore(4);
}
/** Open Active Effects through the head. */
async function effectsScreen() {
  await hub();
  await click(4, 800);
  return waitTitle(/Active Effects/);
}
/** The effect tiles on the open Active Effects screen: [{slot, name, lore}] in order. */
function tiles() {
  const out = [];
  for (const s of [10, 11, 12, 13, 14, 15, 16, 19, 20, 21, 22, 23, 24, 25, 28, 29, 30, 31, 32, 33, 34, 37, 38, 39, 40, 41, 42, 43]) {
    const n = slotName(s);
    if (n && /—/.test(n)) out.push({ slot: s, name: n, lore: slotLore(s) });
  }
  return out;
}
const tileOf = re => tiles().find(t => re.test(t.name));
async function liveCountdowns() {
  const at = await cmd('/cerb showqueues', 800);
  const m = (saw(/Live menu countdowns running/, at) || '').match(/running: (\d+)/);
  return m ? Number(m[1]) : NaN;
}

// ── class: War Cry on Q (quick keys) ───────────────────────────────
function keyQ() { bot._client.write('block_dig', { status: 4, location: { x: 0, y: 0, z: 0 }, face: 0, sequence: 0 }); }
async function warrior() {
  for (const [s, l] of [['Blade_Mastery', 10], ['Critical_Strike', 5]]) await cmd(`/cerb skill ${NAME} ${s} level ${l}`, 350);
  await cmd(`/cerb class ${NAME} warrior rank 12`, 500);
  await cmd('/class controls quick', 500);
  for (const s of ['a1', 'a2', 'a3', 'a4', 'a5', 'p1', 'p2', 'p3', 'k']) await cmd(`/class equip 1 ${s} ${s === 'a3' ? 'war_cry' : 'none'}`, 250);
  await cmd('/class loadout 1', 900);
  await cmd('/give @s iron_sword 1', 400);
  const sword = bot.inventory.items().find(i => i.name === 'iron_sword');
  if (sword) await bot.equip(sword, 'hand');
  await sleep(300);
}
async function warCry() {
  const sword = bot.inventory.items().find(i => i.name === 'iron_sword');
  if (sword) { await bot.equip(sword, 'hand'); await sleep(250); } // eating leaves a bowl in hand: War Cry wants a weapon
  const t = Date.now();
  keyQ();
  await sleep(500);
  return hudSince(t).some(h => /War Cry/.test(h));
}
async function neutralClass() {
  for (const s of ['a1', 'a2', 'a3', 'a4', 'a5', 'p1', 'p2', 'p3', 'k']) await cmd(`/class equip 1 ${s} none`, 250);
  await cmd('/class loadout 1', 600);
}

// ── an admin dev buff (Control Center → Attributes → you → Dev buffs) ──
async function devBuff(name = /^\+100 Vitality$/) {
  closeAny();
  await cmd('/cerb panel', 1500);
  await click(findSlot(/^Attributes$/), 900);
  await click(10, 900);
  await click(31, 900);
  if (!(await waitTitle(/Dev buffs/))) return false;
  await click(findSlot(name), 1200);
  closeAny();
  return true;
}
async function clearDevBuffs() {
  closeAny();
  await cmd('/cerb panel', 1500);
  await click(findSlot(/^Attributes$/), 900); await click(10, 900); await click(31, 900);
  if (await waitTitle(/Dev buffs/)) await click(40, 1200);
  closeAny();
}

// ── phases ────────────────────────────────────────────────────────
async function nonePhase() {
  await clean();
  const lore = await hub();
  check('The hub\'s head lists effects after the attributes: "No active effects"', /Active effects:.*No active effects/.test(lore) && /Click for your active effects/.test(lore), lore.slice(-120));
  await click(4, 800);
  check('Clicking the head opens Active Effects', await waitTitle(/Active Effects/), title());
  check('...which says there are none, and where the permanent numbers are', /^No active effects$/.test(slotName(22)) && /Stats & Attributes/.test(slotLore(22)), slotName(22) + ' | ' + slotLore(22));
  check('...and runs no countdown (nothing timed on it)', (await liveCountdowns()) === 0);
  closeAny();
}

async function mealPhase() {
  await clean();
  await sleep(1500);
  const base = hud();
  const ate = await eat('hearty_stew');
  await sleep(1200);
  const fed = hud();
  check('A Hearty Stew eaten (max health from the HUD before and after)', ate && fed.max > base.max, `${base.max} -> ${fed.max}`);
  const lore = await hub();
  const line = (lore.match(/Hearty Stew — [^|]+/) || [''])[0];
  check('The hub\'s head: "Hearty Stew — 9m 5Xs remaining", first after "Active effects:"', /Active effects: \| +Hearty Stew — 9m 5\ds remaining/.test(lore), line);
  await click(4, 800);
  await waitTitle(/Active Effects/);
  const t0 = tileOf(/Hearty Stew/);
  check('Its tile: name and time left', !!t0 && timeOf(t0.name) >= 590 && timeOf(t0.name) <= 600, t0 ? t0.name : 'no tile');
  check('...its source: a meal, its tier and role', !!t0 && /Meal · Common · Survival/.test(t0.lore), t0 ? t0.lore.slice(0, 80) : '');
  const mh = t0 && t0.lore.match(/• Vitality \+6 \|[\s\S]*?Now: \|\s+max health ([\d,]+) → ([\d,]+)/);
  check('...what it does: Vitality +6, and now max health before → after, as the HUD shows', !!mh && Number(mh[1].replace(/,/g, '')) === base.max && Number(mh[2].replace(/,/g, '')) === fed.max,
    mh ? `${mh[1]} → ${mh[2]} (HUD ${base.max} → ${fed.max})` : (t0 ? t0.lore : ''));
  const rs = t0 && t0.lore.match(/Restoration: (\d+)s left \|\s+25% of max health over 20 s, a heal a second/);
  check('...and its restoration apart, with its own short time (never the meal\'s ten minutes)', !!rs && Number(rs[1]) <= 20, t0 ? (t0.lore.match(/Restoration[^|]*\|[^|]*/) || [''])[0] : '');
  await sleep(3200);
  const t1 = tileOf(/Hearty Stew/);
  check('The times count down while the screen stays open (no reopening)', !!t1 && timeOf(t0.name) - timeOf(t1.name) >= 2, `${t0 && t0.name} → ${t1 && t1.name}`);
  check('...one live countdown running for the open screen', (await liveCountdowns()) === 1);
  await sleep(19000);
  const t2 = tileOf(/Hearty Stew/);
  check('After its 20 s the restoration shows as not running, the meal still on', !!t2 && /Restoration: 25% of max health over 20 s after eating; not running now/.test(t2.lore),
    t2 ? (t2.lore.match(/Restoration[^|]*/) || [''])[0] : 'no tile');
  closeAny();
  await sleep(500);
  check('Closing the screen stops its countdown', (await liveCountdowns()) === 0);
}

async function refreshPhase() {
  // The stew from the meal phase has run about half a minute: eaten again, its time is full once more.
  closeAny();
  if (!(await effectsScreen()) || !tileOf(/Hearty Stew/)) { await clean(); await eat('hearty_stew'); await sleep(8000); await effectsScreen(); }
  const before = timeOf((tileOf(/Hearty Stew/) || {}).name);
  closeAny();
  await eat('hearty_stew');
  await effectsScreen();
  const after = timeOf((tileOf(/Hearty Stew/) || {}).name);
  check('The same meal again refreshes its time (back to the full ten minutes)', after >= 590 && after > before, `${before}s → ${after}s`);
  check('...and it is still one meal (one tile)', tiles().filter(t => /Hearty Stew/.test(t.name)).length === 1, tiles().map(t => t.name).join(' / '));
  closeAny();
}

/** The Stats page's "Movement speed: +X%" (the engine's value, one decimal). */
async function statsMoveSpeed() {
  closeAny(); await sleep(200);
  bot.chat('/stats');
  await waitTitle(/Stats & Attributes/);
  await sleep(300);
  let v = NaN;
  for (let i = 0; bot.currentWindow && i < bot.currentWindow.inventoryStart; i++) {
    const m = slotLore(i).match(/Movement speed: ([+-][\d.]+)%/);
    if (m) { v = Number(m[1]); break; }
  }
  closeAny();
  return v;
}

async function replacePhase() {
  await clean();
  await eat('hearty_stew');
  await sleep(800);
  const plainSpeed = await attr('movement_speed'); // the character's own speed bonus (Navigation, Agility) without the ration
  const plainStat = await statsMoveSpeed();
  await eat('trail_ration');
  await sleep(800);
  const speed = await attr('movement_speed');
  const fedStat = await statsMoveSpeed();
  await effectsScreen();
  const names = tiles().map(t => t.name);
  check('A Trail Ration replaces the stew: only the ration shows', names.length === 1 && /Trail Ration/.test(names[0]), names.join(' / '));
  const t = tileOf(/Trail Ration/);
  const m = t && t.lore.match(/• Movement speed \+8% \|[\s\S]*?Now: \|\s+movement speed ([+-][\d.]+)% → ([+-][\d.]+)%/);
  // What the ration adds and what the total becomes after the curve: the Stats page's movement speed before and after (one
  // decimal each), and the server's walking speed (/attribute prints three decimals of 0.1: a coarse check, within one percent).
  const real = (speed - plainSpeed) / 0.1 * 100;
  check('...its +8% shown, and the movement speed total before → after the curve: the Stats page\'s numbers and the server\'s walking speed',
    !!m && Math.abs(Number(m[1]) - plainStat) <= 0.15 && Math.abs(Number(m[2]) - fedStat) <= 0.15 && Math.abs(Number(m[2]) - Number(m[1]) - real) <= 1.0,
    m ? `${m[1]}% → ${m[2]}% shown; Stats ${plainStat}% → ${fedStat}%; attribute ${plainSpeed} → ${speed}` : (t ? t.lore : 'no tile'));
  check('...no restoration line for a meal without one', !!t && !/Restoration/.test(t.lore));
  closeAny();
}

async function deathPhase() {
  await clean();
  await eat('hearty_stew');
  await cmd('/effect give @s speed 120 0', 400);
  await cmd('/kill @s', 1500);
  try { bot.respawn && bot.respawn(); } catch (e) { /* auto */ }
  await sleep(2500);
  await effectsScreen();
  const names = tiles().map(t => t.name);
  check('Death ends the meal and the potion: nothing shows', names.length === 0 && /^No active effects$/.test(slotName(22)), names.join(' / ') || slotName(22));
  closeAny();
}

async function relogPhase() {
  await clean();
  await eat('hearty_stew');
  await effectsScreen();
  const before = timeOf((tileOf(/Hearty Stew/) || {}).name);
  closeAny();
  bot.quit();
  await sleep(6000);
  await connect();
  await sleep(3500);
  await effectsScreen();
  const t = tileOf(/Hearty Stew/);
  const after = timeOf(t ? t.name : '');
  check('After a relog the meal is still there, its time having run on in real time', !!t && after < before - 5 && after > before - 30, `${before}s → ${after}s`);
  check('...and its restoration is not running (it doesn\'t survive a relog)', !!t && /not running now/.test(t.lore), t ? (t.lore.match(/Restoration[^|]*/) || [''])[0] : '');
  closeAny();
}

async function classPhase() {
  await clean();
  await warrior();
  const cast = await warCry();
  await effectsScreen();
  const t = tileOf(/War Cry/);
  check('War Cry cast: its tile with its time (8 s at most)', cast && !!t && timeOf(t.name) >= 1 && timeOf(t.name) <= 8, t ? t.name : `cast ${cast}; ${tiles().map(x => x.name).join(' / ')}`);
  const dm = t && t.lore.match(/• Damage \+15% \|[\s\S]*?Now: \|\s+(\w+) damage x([\d.]+) → x([\d.]+)/);
  check('...its source and what it does: +15% damage, and now the weapon\'s damage multiplier before → after (x1.15 of it)',
    !!t && /Warrior ability/.test(t.lore) && !!dm && Math.abs(Number(dm[3]) / Number(dm[2]) - 1.15) <= 0.01, t ? t.lore : '');
  await sleep(Math.max(0, (timeOf(t ? t.name : '8s') + 1.5)) * 1000);
  check('When it ends, the open screen redraws itself without it', !tileOf(/War Cry/) && /^No active effects$/.test(slotName(22)), tiles().map(x => x.name).join(' / ') || slotName(22));
  closeAny();
  // A passive in the loadout: always on, listed apart from the timed effects (and not on the head's list).
  await cmd('/class equip 1 p1 battle_hardened', 600);
  const lore = await hub();
  await click(4, 800);
  await waitTitle(/Active Effects/);
  const ao = slotName(47), aoLore = slotLore(47);
  check('An equipped passive: an "Always on" tile apart, with what it adds', /^Always on \(1\)$/.test(ao) && /Battle-Hardened/.test(aoLore) && /• Vitality \+8 \|\s+max health [\d,]+ → [\d,]+/.test(aoLore),
    ao + ' | ' + aoLore.slice(0, 200));
  check('...never among the timed effects or on the head\'s list', tiles().length === 0 && !/Battle-Hardened/.test(lore), tiles().map(x => x.name).join(' / '));
  closeAny();
  await neutralClass();
}

async function buffPhase() {
  await clean();
  await sleep(1200);
  const base = hud();
  check('Dev buff +100 Vitality given through the Control Center', await devBuff());
  await sleep(1200);
  const buffed = hud();
  await effectsScreen();
  const t = tileOf(/Dev buff \+100 Vitality/);
  check('Its tile: name and about five minutes left', !!t && timeOf(t.name) >= 280 && timeOf(t.name) <= 300, t ? t.name : tiles().map(x => x.name).join(' / '));
  const mh = t && t.lore.match(/• Vitality \+100 \|[\s\S]*?Now: \|\s+max health ([\d,]+) → ([\d,]+)/);
  check('...source "Admin test buff", and max health +100 as the HUD shows',
    !!t && /Admin test buff/.test(t.lore) && !!mh && Number(mh[1].replace(/,/g, '')) === base.max && Number(mh[2].replace(/,/g, '')) === buffed.max && buffed.max === base.max + 100,
    mh ? `${mh[1]} → ${mh[2]} (HUD ${base.max} → ${buffed.max})` : (t ? t.lore : ''));
  closeAny();
  await clearDevBuffs();
  await effectsScreen();
  check('Cleared by the admin: gone', !tileOf(/Dev buff/), tiles().map(x => x.name).join(' / ') || slotName(22));
  closeAny();
}

async function potionPhase() {
  await clean();
  await cmd('/effect give @s speed 30 1', 400);
  await cmd('/effect give @s regeneration 40 0', 400);
  await cmd('/effect give @s luck infinite 0', 400);
  await effectsScreen();
  const sp = tileOf(/^Speed II/), rg = tileOf(/^Regeneration/), lk = tileOf(/^Luck/);
  check('Speed II: its time and what it does (walking speed +40%)', !!sp && timeOf(sp.name) <= 30 && timeOf(sp.name) >= 25 && /Walking speed \+40%/.test(sp.lore) && /Potion effect \(vanilla\)/.test(sp.lore),
    sp ? sp.name + ' | ' + sp.lore : 'no tile');
  check('Regeneration: said to do nothing to Cerberus health', !!rg && /No effect on Cerberus health/.test(rg.lore), rg ? rg.lore : 'no tile');
  const names = tiles().map(t => t.name);
  check('An infinite Luck: "until removed", listed after the timed ones', !!lk && /until removed/.test(lk.name) && names.indexOf(lk.name) === names.length - 1, names.join(' / '));
  check('Order among potions: the soonest to end first', tiles().map(t => t.name.split(' — ')[0]).join(',') === 'Speed II,Regeneration,Luck', tiles().map(t => t.name).join(' / '));
  await cmd('/effect clear @s speed', 400);
  await sleep(1800);
  check('Speed cleared while the screen is open: it disappears by itself', !tileOf(/^Speed II/) && !!tileOf(/^Regeneration/), tiles().map(t => t.name).join(' / '));
  closeAny();
  await cmd('/effect clear @s', 400);
}

async function vanillaPhase() {
  // The descriptions are about Cerberus's health: checked against what the game does.
  await clean();
  await cmd('/gamerule natural_regeneration false', 300);
  const p = bot.entity.position.floored();
  await cmd(`/setblock ${p.x} ${p.y - 1} ${p.z} stone`, 300);
  await cmd('/effect give @s fire_resistance 60 0', 400);
  await sleep(1500);
  const h0 = hud().hp;
  await cmd(`/setblock ${p.x} ${p.y} ${p.z} lava`, 300);
  await sleep(3000);
  await cmd(`/setblock ${p.x} ${p.y} ${p.z} air`, 300);
  await sleep(1200);
  const h1 = hud().hp;
  await cmd('/effect clear @s fire_resistance', 300);
  await cmd(`/cerb heal ${NAME}`, 800);
  await sleep(800);
  const h2 = hud().hp;
  await cmd(`/setblock ${p.x} ${p.y} ${p.z} lava`, 300);
  await sleep(1500);
  await cmd(`/setblock ${p.x} ${p.y} ${p.z} air`, 300);
  await sleep(800);
  const h3 = hud().hp;
  check('Fire Resistance ("No damage from fire and lava"): lava costs no health with it, and does without it (control)', h1 === h0 && h3 < h2,
    `with ${h0} → ${h1}; without ${h2} → ${h3}`);
  await cmd('/effect give @s fire_resistance 10 0 true', 300); // put out the burning from the control
  await cmd(`/cerb heal ${NAME}`, 600);
  await sleep(1000);
  await cmd('/effect clear @s fire_resistance', 300);
  // Resistance ("No effect: Cerberus applies its own defense"): the same hit takes the same health with and without it
  // (vanilla's Resistance IV would take 80% off). Each hit lands on full health and a full defense bar.
  const full = async () => {
    await cmd(`/cerb heal ${NAME}`, 600);
    for (let i = 0; i < 40; i++) {
      const m = (bars.length ? bars[bars.length - 1].m : '').match(/([\d,]+)\/([\d,]+)⛨/);
      if (m && m[1] === m[2] && hud().hp === hud().max) return true;
      await sleep(500);
    }
    return false;
  };
  const hit = async () => { await full(); const a = hud().hp; await cmd('/damage @s 40 minecraft:generic', 1500); return a - hud().hp; };
  const plainHit = await hit();
  await cmd('/effect give @s resistance 60 3', 400);
  const resisted = await hit();
  check('Resistance IV ("No effect" here): a 40-damage hit takes the same health as without it (vanilla would take 80% off)',
    plainHit >= 5 && Math.abs(resisted - plainHit) <= Math.max(1, plainHit * 0.05), `${plainHit} vs ${resisted}`);
  await cmd('/effect clear @s', 300);
  await cmd('/gamerule natural_regeneration true', 300);
  await cmd(`/cerb heal ${NAME}`, 600);
}

async function comboPhase() {
  await clean();
  await warrior();
  await eat('hearty_stew');
  await devBuff();
  await cmd('/effect give @s speed 300 0', 400);
  await cmd('/effect give @s luck infinite 0', 400);
  const cast = await warCry();
  const lore = await hub();
  const listed = (lore.split('Active effects:')[1] || '').split('|').map(s => s.trim()).filter(s => /—|more/.test(s));
  check('The head lists four, meal first, then "+1 more"', listed.length === 5 && /^Hearty Stew/.test(listed[0]) && /^\+1 more$/.test(listed[4]), listed.join(' / '));
  await click(4, 800);
  await waitTitle(/Active Effects/);
  const order = tiles().map(t => t.name.split(' — ')[0]);
  check('The screen: meal, ability, buff, then potions (soonest to end first)', cast && order.join(',') === 'Hearty Stew,War Cry,Dev buff +100 Vitality,Speed,Luck', order.join(','));
  closeAny();
  await clearDevBuffs();
  await neutralClass();
  await cmd('/effect clear @s', 400);
  await cmd(`/cerb cook clear ${NAME}`, 400);
}

async function navPhase() {
  await clean();
  await hub();
  await click(21, 900);
  check('Stats & Attributes is still on its hub tile', await waitTitle(/Stats & Attributes/), title());
  await click(45, 900);
  check('...and Back returns to the hub', await waitTitle(/Player Menu/), title());
  await click(4, 900);
  check('The head opens Active Effects', await waitTitle(/Active Effects/), title());
  await click(51, 900);
  check('Its Stats & Attributes button opens Stats', await waitTitle(/Stats & Attributes/), title());
  await click(45, 900);
  check('...and Back returns to Active Effects', await waitTitle(/Active Effects/), title());
  await click(45, 900);
  check('Back from Active Effects returns to the hub', await waitTitle(/Player Menu/), title());
  await click(4, 900);
  await click(49, 900);
  check('Home (the compass) returns to the hub too', await waitTitle(/Player Menu/), title());
  await click(53, 900);
  check('Close closes', !bot.currentWindow);
}

async function withResources(edit, body) {
  const dir = process.env.SERVER_DIR;
  if (!dir) { check('this phase needs SERVER_DIR (the sandbox server folder)', false); return; }
  const file = path.join(dir, 'plugins', 'CerberusPlugin', 'resources.yml');
  const original = fs.readFileSync(file, 'utf8');
  const edited = edit(original);
  if (edited === original) { check('the phase found what it edits in the sandbox resources.yml', false); return; }
  try {
    fs.writeFileSync(file, edited);
    await cmd('/cerb reload', 2500);
    await body();
  } finally {
    fs.writeFileSync(file, original);
    await cmd('/cerb reload', 2500);
  }
}

async function warnPhase() {
  await withResources(s => s.replace(/(hearty_stew:[\s\S]*?minutes: )10/, '$12'), async () => {
    await clean();
    await eat('hearty_stew');
    const eaten = Date.now();
    // The heads-up comes a minute before the end: about 60 s after eating a two-minute stew.
    let seen = null;
    while (Date.now() - eaten < 75000) {
      await sleep(500);
      const h = hudSince(eaten).find(m => /Hearty Stew wears off in/.test(m));
      if (h && !seen) seen = { at: Date.now() - eaten, m: h };
    }
    check('About a minute before the end: "Hearty Stew wears off in 1 min" on the HUD', !!seen && seen.at > 55000 && seen.at < 66000 && /wears off in (1 min|5\d s)/.test(seen.m),
      seen ? `${(seen.at / 1000).toFixed(1)} s after eating: ${seen.m}` : 'never');
    // Open the screen before the end and keep it open: it redraws without the meal when it ends.
    await effectsScreen();
    const open = !!tileOf(/Hearty Stew/);
    while (Date.now() - eaten < 124000) await sleep(500);
    const repeats = bars.filter(b => b.t >= eaten && /Hearty Stew wears off in/.test(b.m)).map(b => Math.round((b.t - eaten) / 1000));
    // One notice stays about 4 s (the HUD resends its bar each second): one stretch of seconds, never a second one later.
    const sec = [...new Set(repeats)];
    check('...said once: not again before the end', sec.length > 0 && Math.max(...sec) - Math.min(...sec) <= 5, JSON.stringify(sec));
    check('The stew ends while the screen is open: the screen redraws without it', open && !tileOf(/Hearty Stew/) && /^No active effects$/.test(slotName(22)), tiles().map(t => t.name).join(' / ') || slotName(22));
    closeAny();
    // A stew replaced before its heads-up never warns.
    await eat('hearty_stew');
    const second = Date.now();
    await sleep(15000);
    await eat('trail_ration');
    while (Date.now() - second < 75000) await sleep(500);
    check('A stew replaced (by a Trail Ration) before its heads-up: no stale warning', !hudSince(second).some(m => /Hearty Stew wears off/.test(m)), hudSince(second).filter(m => /wears off/.test(m)).join(' / '));
    // A heads-up already said is not said again after a relog: the warned meal is recorded on the player.
    await cmd(`/cerb cook clear ${NAME}`, 400);
    await eat('hearty_stew');
    const third = Date.now();
    let said = null;
    while (Date.now() - third < 75000 && !said) { await sleep(500); said = hudSince(third).find(m => /Hearty Stew wears off in/.test(m)); }
    closeAny();
    bot.quit();
    await sleep(4000);
    await connect();
    const back = Date.now();
    await sleep(8000);
    check('A heads-up said before a relog is not said again after it', !!said && !hudSince(back).some(m => /Hearty Stew wears off/.test(m)),
      said ? hudSince(back).filter(m => /wears off/.test(m)).join(' / ') || 'not again' : 'no heads-up before the relog');
    // The control: a relog before the heads-up, back with under a minute left: said a moment after the join.
    await cmd(`/cerb cook clear ${NAME}`, 400);
    await eat('hearty_stew');
    const fourth = Date.now();
    while (Date.now() - fourth < 52000) await sleep(500);
    const early = hudSince(fourth).some(m => /Hearty Stew wears off/.test(m));
    bot.quit();
    await sleep(12000);
    await connect();
    const rejoined = Date.now();
    await sleep(6000);
    const joinSaid = hudSince(rejoined).find(m => /Hearty Stew wears off in/.test(m));
    check('...while a relog before the heads-up, back with under a minute left, says it a moment after the join', !early && !!joinSaid,
      `before the relog: ${early ? 'said (too early)' : 'not yet'}; after: ${joinSaid || 'nothing'}`);
    await cmd(`/cerb cook clear ${NAME}`, 400);
  });
}

async function restartSet() {
  await clean();
  await eat('hearty_stew');
  await cmd('/effect give @s speed 600 0', 400);
  await effectsScreen();
  const t = tileOf(/Hearty Stew/), s = tileOf(/^Speed/);
  closeAny();
  fs.writeFileSync(path.join(__dirname, '.effects-state.json'), JSON.stringify({ stew: timeOf(t ? t.name : ''), speed: timeOf(s ? s.name : ''), at: Date.now() }));
  check('Before the stop: a Hearty Stew and Speed running (stop and start the server, then run restart-check)', !!t && !!s, tiles().map(x => x.name).join(' / '));
  console.log('READY');
  await new Promise(res => { bot.once('end', res); setTimeout(res, 240000); });
}
async function restartCheck() {
  const st = JSON.parse(fs.readFileSync(path.join(__dirname, '.effects-state.json'), 'utf8'));
  const away = (Date.now() - st.at) / 1000;
  await sleep(1500);
  await effectsScreen();
  const t = tileOf(/Hearty Stew/), s = tileOf(/^Speed/);
  check('After a clean stop and start the meal is there, its time having run on in real time', !!t && timeOf(t.name) <= st.stew - away + 3 && timeOf(t.name) >= st.stew - away - 6,
    `${st.stew}s before, ${away.toFixed(0)} s away, now ${t ? timeOf(t.name) : 'none'}s`);
  check('...and the potion too (vanilla keeps it; its time paused while you were offline)', !!s && timeOf(s.name) > st.speed - 20 && timeOf(s.name) <= st.speed, `${st.speed}s before, now ${s ? timeOf(s.name) : 'none'}s`);
  closeAny();
  await cmd(`/cerb cook clear ${NAME}`, 400);
  await cmd('/effect clear @s', 400);
}

(async () => {
  await connect();
  await sleep(3000);
  const phases = { none: nonePhase, meal: mealPhase, refresh: refreshPhase, replace: replacePhase, death: deathPhase, relog: relogPhase,
    class: classPhase, buff: buffPhase, potion: potionPhase, vanilla: vanillaPhase, combo: comboPhase, nav: navPhase, warn: warnPhase,
    'restart-set': restartSet, 'restart-check': restartCheck };
  const run = phaseArg === 'all' ? ['none', 'meal', 'refresh', 'replace', 'death', 'relog', 'class', 'buff', 'potion', 'vanilla', 'combo', 'nav'] : phaseArg.split(',');
  for (const p of run) {
    if (!phases[p]) { console.log('unknown phase ' + p); continue; }
    console.log(`\n── ${p} ──`);
    try { await phases[p](); } catch (e) { check(`${p} ran to the end`, false, e.stack.split('\n').slice(0, 2).join(' ')); }
  }
  if (phaseArg === 'restart-set') {
    console.log(`\n${results.filter(Boolean).length}/${results.length} passed`);
    process.exit(failed.length ? 1 : 0);
  }
  closeAny();
  await cmd(`/cerb cook clear ${NAME}`, 300);
  await cmd('/effect clear @s', 300);
  await cmd('/gamemode creative', 300);
  const passed = results.filter(Boolean).length;
  console.log(`\n${passed}/${results.length} passed`);
  if (failed.length) console.log('FAILED: ' + failed.join(' | '));
  bot.quit();
  setTimeout(() => process.exit(failed.length ? 1 : 0), 500);
})();
