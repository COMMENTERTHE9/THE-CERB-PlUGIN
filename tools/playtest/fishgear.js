// Fishing Equipment, Guided Discovery and Collections in game (docs/design/FISHING_EQUIPMENT.md). LOCAL SANDBOX servers
// only; the bot must be op (it sets levels, time and biomes). Usage: node fishgear.js [phase[,phase...]] [botName]
//   pages    - the skill pages' layout and sections: Fishing (Journal, Encounters; Equipment from increment B) and
//              Woodcutting (Woods) open their screens and Back returns to the page; the hub's Collections index lists
//              fish species, fishing treasure, sea creatures defeated and woods with the same counts the skill pages show,
//              opens the same screens, and Back returns to it; the journal's tabs and filters, kept across a redraw
//   track    - tracking a species: each requirement checked where you fish (a labelled preview of the water you look at,
//              your bobber while a cast is out, "not known" otherwise, never where you stand), "Available here" lists
//              exactly what /cerb fish info (the bite's own eligibility) lists, the tracked species is saved (/fishing
//              track), and the cast hint says whether it can bite there, once per change
//   summary  - "since your last visit": real catches and the XP really awarded (measured), a discovery and its weight
//              among the notable catches, redraws never clear it, marking it read takes off what was shown; creative
//              fishing adds nothing
//   terms    - Encounters: a banked sea creature says "encounter", /fishing encounters (and the old /fishing lures) list them
//   all      - pages, track, summary, terms
const mineflayer = require('mineflayer');
const { Vec3 } = require('vec3');
const phaseArg = process.argv[2] || 'all';
const NAME = process.argv[3] || 'CerbGear';
const sleep = ms => new Promise(r => setTimeout(r, ms));
const chat = [];
const bars = [];
const results = [];
const failed = [];
const check = (name, ok, detail = '') => {
  results.push(ok);
  if (!ok) failed.push(name);
  console.log(`${ok ? 'PASS' : 'FAIL'}  ${name}${detail ? '  (' + String(detail).slice(0, 400) + ')' : ''}`);
};
const note = s => console.log('      ' + s);
let bot, pchat;
function connect(name = NAME) {
  const b = mineflayer.createBot({ host: '127.0.0.1', port: 25565, username: name, version: '1.21.11', auth: 'offline' });
  b.on('messagestr', (m, pos) => { if (pos !== 'game_info') chat.push(m); });
  b._client.on('action_bar', p => {
    try { pchat = pchat || require('prismarine-chat')(b.registry); bars.push({ t: Date.now(), m: pchat.fromNotch(p.text).toString() }); } catch (e) { /* ignore */ }
  });
  b.on('kicked', r => { console.log('KICKED', name, r); process.exit(2); });
  bot = b;
  return new Promise(r => b.once('spawn', () => r(b)));
}
async function cmd(c, wait = 600) { const at = chat.length; bot.chat(c); await sleep(wait); return at; }
const saw = (re, since) => chat.slice(since).find(m => re.test(m));
const sawAll = (re, since) => chat.slice(since).filter(m => re.test(m));
const hudSince = t => [...new Set(bars.filter(b => b.t >= t).map(b => b.m))];

// ── menus ────────────────────────────────────────────────────────
const plain = v => {
  if (v == null) return '';
  if (typeof v === 'string') { try { return plain(JSON.parse(v)); } catch { return v; } }
  const s = JSON.stringify(v);
  const nbt = [...s.matchAll(/"text":\{"type":"string","value":"([^"]*)"/g)].map(m => m[1]);
  if (nbt.length) return nbt.join('');
  return [...s.matchAll(/"text":"([^"]*)"/g)].map(m => m[1]).join('');
};
const title = () => bot.currentWindow ? plain(bot.currentWindow.title) : '';
const slot = i => bot.currentWindow && bot.currentWindow.slots[i];
const slotName = i => { const it = slot(i); return it ? plain(it.customName) : ''; };
const flat = i => { const it = slot(i); return it && it.customLore ? it.customLore.map(plain).join(' ').replace(/\s+/g, ' ').trim() : ''; };
const findSlot = (re, from = 0, to = 53) => { if (!bot.currentWindow) return -1; for (let i = from; i <= to; i++) if (re.test(slotName(i))) return i; return -1; };
const SCREEN = name => new RegExp('^✦ ' + name + '$');
async function waitTitle(re, ms = 4000) { const t0 = Date.now(); while (Date.now() - t0 < ms) { if (bot.currentWindow && re.test(title())) return true; await sleep(100); } return false; }
async function click(i, wait = 750, button = 0, mode = 0) { await bot.clickWindow(i, button, mode); await sleep(wait); }
async function closeAny() { if (bot.currentWindow) { bot.closeWindow(bot.currentWindow); await sleep(400); } }
async function openMenu() { await closeAny(); await cmd('/menu', 1200); await sleep(300); return waitTitle(SCREEN('Player Menu')); }
async function openSkill(name) {
  if (!(await openMenu())) return false;
  await click(19); if (!(await waitTitle(SCREEN('Skills')))) return false;
  await click(24); if (!(await waitTitle(SCREEN('Utility Skills')))) return false;
  const s = findSlot(new RegExp('^' + name + '\\b'));
  if (s < 0) return false;
  await click(s);
  return waitTitle(SCREEN(name));
}

// ── skills, the world ────────────────────────────────────────────
const xpForLevel = l => Math.round(75 * Math.pow(Math.max(1, l), 1.85) + 40 * Math.max(1, l));
const totalXp = s => { let t = s.xp; for (let l = 1; l < s.level; l++) t += xpForLevel(l); return t; };
async function skill(name, who = NAME) {
  const at = await cmd(`/cerb skill ${who} ${name} xp 0`, 600);
  const m = (saw(/is now level \d+ \(\d+ XP\)/, at) || '').match(/level (\d+) \((\d+) XP\)/);
  return m ? { level: Number(m[1]), xp: Number(m[2]) } : { level: NaN, xp: NaN };
}
async function setLevel(name, level, who = NAME) { await cmd(`/cerb skill ${who} ${name} level ${level}`, 500); }
async function hold(item) {
  await cmd('/clear @s ' + item.replace(/\[.*$/, ''), 400); await cmd('/give @s ' + item, 900);
  const base = item.replace(/\[.*$/, '');
  const i = bot.inventory.slots.findIndex((it, s) => it && it.name === base && s >= 36 && s <= 44);
  if (i >= 0) { bot.setQuickBarSlot(i - 36); await sleep(300); }
  return i;
}
const ROD = 'fishing_rod[enchantments={lure:3}]';
let X = 0, Y = 120, Z = 0, SP = null;
/** A pond in the sky (7x7, 3 deep, open water), its own biome, far from spawn. */
async function arena(dx = 0, biome = 'river') {
  SP = SP || bot.spawnPoint || bot.entity.position;
  X = Math.floor(SP.x) - 900 + dx; Z = Math.floor(SP.z) + 900;
  // Folia moves a player to a far region asynchronously, and a command sent meanwhile can be lost: wait until the bot is
  // there, then run each change until the server confirms it.
  for (let i = 0; i < 3; i++) {
    await cmd(`/tp @s ${X + 0.5} ${Y + 2} ${Z + 0.5}`, 2500);
    if (bot.entity.position.distanceTo(new Vec3(X + 0.5, Y + 2, Z + 0.5)) < 3) break;
  }
  await sleep(1500);
  await sure(`/fill ${X - 6} ${Y - 5} ${Z - 3} ${X + 6} ${Y - 1} ${Z + 12} stone`, /filled|No blocks were filled/);
  await sure(`/fill ${X - 6} ${Y} ${Z - 3} ${X + 6} ${Y + 6} ${Z + 12} air`, /filled|No blocks were filled/);
  await sure(`/fill ${X - 3} ${Y - 3} ${Z + 2} ${X + 3} ${Y - 1} ${Z + 8} water`, /filled|No blocks were filled/);
  await sure(`/fillbiome ${X - 6} ${Y - 5} ${Z - 3} ${X + 6} ${Y + 6} ${Z + 12} minecraft:${biome}`, /biome|filled/i);
  await cmd('/time set day', 300);
  await cmd('/weather clear', 300);
  await stand();
}
/** Run a command until the server answers with {@code ok} (up to three tries). */
async function sure(c, ok) {
  for (let i = 0; i < 3; i++) {
    const at = await cmd(c, 900);
    if (saw(ok, at)) return true;
  }
  note('no confirmation for ' + c);
  return false;
}
async function stand() {
  await cmd(`/tp @s ${X + 0.5} ${Y} ${Z + 0.5} 0 20`, 800);
  try { await bot.lookAt(new Vec3(X + 0.5, Y - 1, Z + 5.5), true); } catch (e) { /* best effort */ }
}
async function survival(level) {
  await cmd('/gamemode survival', 400);
  await cmd('/effect give @s resistance infinite 4 true', 300);
  await cmd('/effect give @s saturation infinite 1 true', 300);
  await cmd('/clear @s', 400);
  if (level !== undefined) await setLevel('Fishing', level);
  await hold(ROD);
}
async function catchOne(timeoutMs = 45000) {
  try {
    await Promise.race([bot.fish(), new Promise((_, rej) => setTimeout(() => rej(new Error('timeout')), timeoutMs))]);
    await sleep(900);
    return true;
  } catch (e) {
    note('no catch: ' + (e.message || e));
    try { bot.activateItem(); await sleep(300); } catch (x) { /* reel in */ }
    return false;
  }
}
/** Cast and leave the bobber in the water (no reel). */
async function castOnly() { bot.activateItem(); await sleep(150); bot.deactivateItem(); await sleep(2500); }
async function reelIn() { bot.activateItem(); await sleep(150); bot.deactivateItem(); await sleep(500); }
/** Species ids the admin journal shows as not caught (✘). */
async function uncaught() {
  const at = await cmd(`/cerb fish journal ${NAME}`, 1400);
  return sawAll(/✘/, at).map(l => l.replace(/^.*✘\s*/, '').split(' · ')[0].trim());
}
const idOf = name => name.toLowerCase().replace(/ /g, '_');
/** The summary counters from the admin dump. */
async function summaryDump() {
  const at = await cmd(`/cerb fish journal ${NAME}`, 1400);
  const l = saw(/unread summary: /, at) || '';
  const m = l.match(/unread summary: (\d+) catches, (\d+) XP, (\d+) species, (\d+) bests, (\d+) encounters · notable: (\d+) \((\d+) unread\)/);
  return m ? { catches: +m[1], xp: +m[2], species: +m[3], bests: +m[4], encounters: +m[5], notable: +m[6], unread: +m[7] } : null;
}

// ════════════════════════════════════════════════════════════════════

async function pages() {
  await cmd('/gamemode creative', 300);
  check('Fishing page opens', await openSkill('Fishing'), title());
  const row2 = [19, 21, 23, 25].map(slotName);
  check('Row 2 keeps the page readable: How to train, Attributes & speed, What it does, Next levels',
    /How to train/.test(row2[0]) && /Attributes & speed/.test(row2[1]) && /What it does/.test(row2[2]) && /Next levels/.test(row2[3]), row2.join(' | '));
  const journalTile = findSlot(/^Journal: \d+ of \d+ species$/, 27, 35), encTile = findSlot(/^Encounters: \d+$/, 27, 35);
  check('Section tiles in row 3: Journal: N of M species, Encounters: N', journalTile >= 0 && encTile >= 0,
    [27, 28, 29, 30, 31, 32, 33, 34, 35].map(slotName).filter(Boolean).join(' | '));
  const jn = (slotName(journalTile).match(/Journal: (\d+) of (\d+)/) || []).slice(1).map(Number);
  check('No giant tooltip: the Journal tile is a few lines', flat(journalTile).length < 220, flat(journalTile).length + ' chars');
  check('The summary tile sits between the skill arrows', /Since your last visit/.test(slotName(40)) && /◀/.test(slotName(37)) && /▶/.test(slotName(43)), slotName(40));
  await click(journalTile);
  check('Journal opens from the Fishing page', await waitTitle(SCREEN('Fishing Journal')), title());
  check('Journal header: species and treasure counts', new RegExp(`Species: ${jn[0]} of ${jn[1]}`).test(flat(4)) && /Treasure: \d+ of \d+/.test(flat(4)), flat(4));
  check('Tabs: All waters (shown), each water, Treasure', /▶ All waters/.test(slotName(9)) && /Rivers/.test([10, 11, 12, 13, 14, 15, 16].map(slotName).join(' ')) && /Treasure/.test(slotName(17)),
    [9, 10, 11, 12, 13, 14, 15, 16, 17].map(slotName).join(' | '));
  const entries = () => [19, 20, 21, 22, 23, 24, 25, 28, 29, 30, 31, 32, 33, 34, 37, 38, 39, 40, 41, 42, 43].filter(i => slotName(i) && !/Nothing to show/.test(slotName(i)));
  check('A page of species entries with rarity, water and requirements', entries().length === 21 && /Fishing \d+/.test(flat(entries()[0])) && /(Common|Uncommon|Rare|Epic|Legendary) · /.test(flat(entries()[0])),
    entries().length + ' entries; ' + flat(entries()[0]));
  await click(47);
  check('Filter: Undiscovered', /Filter: Undiscovered/.test(slotName(47)) && entries().every(i => /Not caught yet/.test(flat(i))), slotName(47) + ' ' + entries().length);
  const riverTab = findSlot(/Rivers/, 9, 17);
  await click(riverTab);
  check('A water tab keeps the filter', /▶ Rivers/.test(slotName(riverTab)) && /Filter: Undiscovered/.test(slotName(47)) && entries().every(i => /Rivers/.test(flat(i))),
    slotName(riverTab) + ' / ' + slotName(47));
  await click(52);
  check('A redraw keeps the tab and the filter', /▶ Rivers/.test(slotName(riverTab)) && /Filter: Undiscovered/.test(slotName(47)), slotName(riverTab) + ' / ' + slotName(47));
  await click(45);
  check('Back from the journal returns to the Fishing page', await waitTitle(SCREEN('Fishing')), title());
  await click(encTile);
  check('Encounters opens from the Fishing page', await waitTitle(SCREEN('Encounters')) && /^Encounters: \d+$/.test(slotName(4)), title() + ' / ' + slotName(4));
  const creatures = [19, 20, 21, 22, 23, 24, 25, 28].map(slotName).filter(Boolean);
  check('Every sea creature is listed with its waters and drops', creatures.length === 8 && /Waters: /.test(flat(19)) && /Drops:/.test(flat(19)), creatures.join(' | '));
  await click(45);
  check('Back from Encounters returns to the Fishing page', await waitTitle(SCREEN('Fishing')), title());

  // Woodcutting: one section.
  check('Woodcutting page opens', await openSkill('Woodcutting'), title());
  const woods = findSlot(/^Woods: \d+ of \d+ discovered$/, 27, 35);
  check('Woodcutting has one section: Woods: N of M discovered', woods === 31, slotName(woods) + ' at ' + woods);
  const wn = (slotName(woods).match(/Woods: (\d+) of (\d+)/) || []).slice(1).map(Number);
  await click(woods);
  check('Woods opens with every wood', await waitTitle(SCREEN('Woods')) && [19, 20, 21, 22, 23, 24, 25, 28, 29, 30, 31].every(i => slotName(i)), title());
  await click(45);
  check('Back from Woods returns to the Woodcutting page', await waitTitle(SCREEN('Woodcutting')), title());
  // A skill without functionality has no sections.
  await click(45); await waitTitle(SCREEN('Utility Skills'));
  const mining = findSlot(/^Mining\b/);
  await click(mining); await waitTitle(SCREEN('Mining'));
  check('A skill without sections shows none', [28, 29, 30, 31, 32, 33, 34].every(i => /^ ?$/.test(slotName(i))), [28, 29, 30, 31, 32, 33, 34].map(slotName).join('|'));

  // Collections: the same data and the same screens.
  await openMenu();
  check('The hub\'s Collections tile is available', /Collections/.test(slotName(32)) && /Available/.test(flat(32)), flat(32));
  await click(32);
  check('Collections opens', await waitTitle(SCREEN('Collections')), title());
  const names = [20, 22, 24, 30].map(slotName);
  check('Four collections: fish species, fishing treasure, sea creatures defeated, woods', names.join('|') === 'Fish species|Fishing treasure|Sea creatures defeated|Woods', names.join(' | '));
  const fishC = (flat(20).match(/Collected: (\d+) of (\d+)/) || []).slice(1).map(Number);
  const woodC = (flat(30).match(/Collected: (\d+) of (\d+)/) || []).slice(1).map(Number);
  check('The same counts as the skill pages (from the definitions)', fishC.join('/') === jn.join('/') && woodC.join('/') === wn.join('/'),
    `fish ${fishC} vs ${jn}, woods ${woodC} vs ${wn}`);
  await click(20);
  check('Fish species opens the Fishing Journal', await waitTitle(SCREEN('Fishing Journal')) && /▶ All waters/.test(slotName(9)), title());
  await click(45);
  check('Back returns to Collections', await waitTitle(SCREEN('Collections')), title());
  await click(22);
  check('Fishing treasure opens the journal on its Treasure tab', await waitTitle(SCREEN('Fishing Journal')) && /▶ Treasure/.test(slotName(17)) && /treasure/.test(flat(19)), slotName(17) + ' / ' + flat(19));
  await click(45); await waitTitle(SCREEN('Collections'));
  await click(24);
  check('Sea creatures defeated opens Encounters', await waitTitle(SCREEN('Encounters')), title());
  await click(45); await waitTitle(SCREEN('Collections'));
  await click(30);
  check('Woods opens the Woods screen', await waitTitle(SCREEN('Woods')), title());
  await closeAny();
}

async function track() {
  await arena(0, 'river');
  await survival(30);
  await cmd('/fishing track off', 500);
  // Looking at the river pond, then opening the journal: a labelled preview of that water.
  await stand();
  await openSkill('Fishing');
  await click(findSlot(/^Journal: /, 27, 35));
  await waitTitle(SCREEN('Fishing Journal'));
  check('Before a cast: the water you look at, labelled a preview', /Where you fish: Rivers/.test(slotName(2)) && /preview/.test(flat(2)), slotName(2) + ' / ' + flat(2));
  const trout = findSlot(/Goldfin Trout/);
  await click(trout);
  check('Clicking a species tracks it', /★ Tracked: Goldfin Trout/.test(slotName(6)), slotName(6));
  check('Each requirement on its own line: level met, water met (preview)', /✓ Fishing 25 \(you: 30\)/.test(flat(6)) && /✓ Rivers \(preview: Rivers\)/.test(flat(6)) && /It can bite here now/.test(flat(6)), flat(6));
  const brook = findSlot(/Brook Char/);
  await click(brook);
  check('Tracking another species replaces it; by day it says what is missing', /Tracked: Brook Char/.test(slotName(6)) && /✕ Night only \(it is day\)/.test(flat(6)) && /can't bite here now/.test(flat(6)), flat(6));
  await closeAny();
  await cmd('/time set midnight', 500);
  await openSkill('Fishing');
  await click(findSlot(/^Journal: /, 27, 35));
  await waitTitle(SCREEN('Fishing Journal'));
  check('At night the time requirement is met', /✓ Night only \(it is night\)/.test(flat(6)) && /It can bite here now/.test(flat(6)), flat(6));
  // Available here = the bite's own eligibility (/cerb fish info lists the eligible species at this level).
  await click(47); await click(47); // All -> Undiscovered -> Available here
  const here = [19, 20, 21, 22, 23, 24, 25, 28, 29, 30, 31, 32, 33, 34].map(slotName).filter(n => n && !/Nothing to show/.test(n)).map(n => n.trim()).sort();
  await closeAny();
  const at = await cmd('/cerb fish info', 1200);
  const info = sawAll(/^\s+\S.* [\d.]+%$/, at).filter(l => !/Lure|Treasure|Encounter/.test(l)).map(l => l.trim().replace(/ [\d.]+%$/, '')).map(s => s.split(' ').map(w => w[0].toUpperCase() + w.slice(1)).join(' ')).sort();
  check('"Available here" lists exactly what the bite allows (/cerb fish info)', here.length > 0 && JSON.stringify(here) === JSON.stringify(info), `journal ${here.join(', ')} | info ${info.join(', ')}`);
  // The tracked species is saved: a rejoin keeps it.
  bot.quit(); await sleep(3500); await connect(); await sleep(3000);
  const at2 = await cmd('/fishing track', 800);
  check('The tracked species is saved across a rejoin', !!saw(/Tracking .*Brook Char/, at2), sawAll(/Track/, at2).join(' / '));
  // With a cast out, "here" is the bobber; the cast also brings the tracked species' hint (night: it can bite here).
  await survival(30); await stand();
  const tCast = Date.now();
  await castOnly();
  const hint1 = hudSince(tCast).filter(m => /★ Brook Char/.test(m));
  await openSkill('Fishing');
  await click(findSlot(/^Journal: /, 27, 35));
  await waitTitle(SCREEN('Fishing Journal'));
  check('With a cast out: where your bobber floats', /Where you fish: Rivers/.test(slotName(2)) && /where your bobber floats/.test(flat(2)) && /your bobber: Rivers/.test(flat(6)), flat(2) + ' / ' + flat(6));
  await closeAny();
  await reelIn();
  // Cast hint: once per verdict.
  check('The cast hint said the tracked species can bite here', hint1.some(m => /can bite here/.test(m)), hint1.join(' / ') || 'no hint');
  let t = Date.now();
  await castOnly(); await sleep(2500);
  check('The same verdict is not repeated on the next cast', hudSince(t).filter(m => /★ Brook Char/.test(m)).length === 0, hudSince(t).join(' / '));
  await reelIn();
  await cmd('/time set day', 400);
  t = Date.now();
  await castOnly(); await sleep(2500);
  check('A change (day) shows what is missing', hudSince(t).some(m => /★ Brook Char bites only at night/.test(m)), hudSince(t).join(' / '));
  await reelIn();
  // Unknown place: no bobber, looking at the sky.
  try { await bot.look(0, Math.PI / 2, true); } catch (e) { /* best effort */ }
  await openSkill('Fishing');
  await click(findSlot(/^Journal: /, 27, 35));
  await waitTitle(SCREEN('Fishing Journal'));
  check('No cast and no water in sight: the place is not known (not where you stand)', /not known yet/.test(slotName(2)) && /\? Rivers \(cast to check\)/.test(flat(6)), slotName(2) + ' / ' + flat(6));
  await click(47); await click(47);
  check('"Available here" with no known place explains itself', /Nothing to show/.test(slotName(31)) && /cast your line/.test(flat(31)), flat(31));
  await closeAny();
  await cmd('/fishing track off', 400);
}

async function summary() {
  await arena(60, 'plains');
  await survival(30);
  // Start from a read summary.
  await openSkill('Fishing');
  if (/Right-click: mark as read/.test(flat(40))) { await click(40, 900, 1); }
  await closeAny();
  const s0 = await summaryDump();
  check('A read summary has no unread counters', s0 && s0.catches === 0 && s0.xp === 0 && s0.unread === 0, JSON.stringify(s0));
  const fresh = (await uncaught()).map(idOf).find(id => !/abyssal|crystal|riverking|sturgeon/.test(id)) || 'emerald_tench';
  await cmd(`/cerb fish force ${NAME} species ${fresh}`, 400);
  const xp0 = totalXp(await skill('Fishing'));
  let caught = 0;
  for (let i = 0; i < 4; i++) if (await catchOne()) caught++;
  const xp1 = totalXp(await skill('Fishing'));
  const s1 = await summaryDump();
  check('Catches and the XP really awarded are counted', s1 && s1.catches === caught && s1.xp === xp1 - xp0, `caught ${caught}, xp +${xp1 - xp0}; ${JSON.stringify(s1)}`);
  check('The forced first catch counts as a new species among the notable catches', s1 && s1.species >= 1 && s1.unread >= 1, JSON.stringify(s1));
  await openSkill('Fishing');
  const tile = flat(40);
  check('The Fishing page shows it: catches, XP, new species', new RegExp(`${caught} catch(es)? · \\+[\\d,]+ Fishing XP`).test(tile) && /new species/.test(tile), tile);
  await closeAny();
  await openSkill('Fishing');
  check('Reopening the page does not clear it', flat(40) === tile, flat(40));
  await click(40);
  check('Click: view new discoveries', await waitTitle(SCREEN('New discoveries')) && /NEW/.test(slotName(19)) && /\d+\.\d+ kg/.test(flat(19)), slotName(19) + ' / ' + flat(19));
  // A catch while the screen is open is not lost when it is marked read.
  let extra = false;
  try {
    await Promise.race([bot.fish(), new Promise((_, rej) => setTimeout(() => rej(new Error('timeout')), 45000))]);
    await sleep(900);
    const d = await summaryDump();
    extra = !!d && d.catches > s1.catches;
  } catch (e) { note('the bot could not catch with the window open (' + e.message + '): FishingJournalTest covers that case'); }
  check('The screen stayed open during that catch', SCREEN('New discoveries').test(title()), title());
  const before = await summaryDump();
  await click(51, 900);
  const after = await summaryDump();
  if (extra) check('Mark as read takes off what the screen showed: the catch made while it was open stays unread', after && after.catches === before.catches - s1.catches && after.catches >= 1,
    `shown ${s1.catches}, before ${before && before.catches}, after ${after && after.catches}`);
  else check('Mark as read clears what the screen showed', after && after.catches === 0 && after.species === 0, JSON.stringify(after));
  await closeAny();
  // Creative: nothing is counted.
  await openSkill('Fishing'); if (/Right-click: mark as read/.test(flat(40))) await click(40, 900, 1); await closeAny();
  const c0 = await summaryDump();
  await cmd('/gamemode creative', 400);
  await hold(ROD);
  for (let i = 0; i < 2; i++) await catchOne();
  const c1 = await summaryDump();
  check('Creative fishing adds nothing to the summary', c1 && c0 && c1.catches === c0.catches && c1.xp === c0.xp, `${JSON.stringify(c0)} -> ${JSON.stringify(c1)}`);
  await cmd('/gamemode survival', 300);
}

async function terms() {
  await arena(120, 'river');
  await survival(30);
  await cmd(`/cerb fish force ${NAME} lure river_lurker`, 400);
  const at = chat.length;
  await catchOne();
  await sleep(800);
  check('A banked sea creature says "encounter banked"', !!saw(/River Lurker .*took your bait.*Encounter banked/, at), sawAll(/took your bait/, at).join(' / '));
  const at2 = await cmd('/fishing encounters', 900);
  check('/fishing encounters lists them', !!saw(/^Encounters: .*River Lurker/, at2), chat.slice(at2).join(' / '));
  const at3 = await cmd('/fishing lures', 900);
  check('The old /fishing lures still works', !!saw(/^Encounters: .*River Lurker/, at3), chat.slice(at3).join(' / '));
  await openSkill('Fishing');
  const enc = findSlot(/^Encounters: \d+$/, 27, 35);
  check('The Fishing page\'s tile is "Encounters: N"', enc >= 0 && Number(slotName(enc).split(': ')[1]) >= 1, slotName(enc));
  await closeAny();
}

const PHASES = { pages, track, summary, terms };
const ALL = ['pages', 'track', 'summary', 'terms'];

connect().then(async () => {
  await sleep(3500);
  const list = phaseArg === 'all' ? ALL : phaseArg.split(',');
  for (const ph of list) {
    if (!PHASES[ph]) { console.log('Unknown phase ' + ph); results.push(false); continue; }
    console.log(`── ${ph} ──`);
    try { await PHASES[ph](); } catch (e) { console.log('SCRIPT ERROR in ' + ph, e.stack); results.push(false); failed.push(ph + ' (script error)'); }
  }
  try { await closeAny(); await cmd('/gamemode creative', 300); } catch (e) { /* gone */ }
  const bad = results.filter(r => !r).length;
  console.log(`\n${results.length - bad}/${results.length} passed`);
  if (failed.length) console.log('FAILED: ' + failed.join(' | '));
  bot.quit();
  await sleep(500); process.exit(bad ? 1 : 0);
});
setTimeout(() => { console.log('TIMEOUT'); process.exit(3); }, 3600000);
